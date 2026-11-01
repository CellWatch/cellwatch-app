begin;

-- Leave the 89 FCC queue entries blocked before the 2026-10-29 pipeline push out of the digest
-- email (decided 2026-10-31).
--
-- They were blocked before 202610210103 went live: 40 "Missing provider", 8 on the 2026-08-17
-- hold, and 41 the FCC rejected with HTTP 400. On 2026-10-29 the team decided to leave old
-- entries as they are (#120), and on 2026-10-31 to stop reporting them. This lists them in
-- private.fcc_queue_ignored, and what reports to a person skips the entries listed there:
--   * private.fcc_queue_attention, and so the digest's "needs a person" sections and its
--     "Nothing needs attention" line,
--   * the digest's queue and held counts,
--   * send_fcc_queue_digest's check for something new.
-- The queue rows are unchanged, and private.fcc_queue_health still counts them. To report an
-- entry again, delete its row from private.fcc_queue_ignored; to ignore another, insert one.

create table if not exists private.fcc_queue_ignored (
    submission_id uuid primary key
        references private.fcc_submission_retry_queue (submission_id) on delete cascade,
    ignored_on timestamptz not null default now(),
    note text
);

-- The retry job was paused for that push at 19:18 UTC: every entry blocked before then.
insert into private.fcc_queue_ignored (submission_id, note)
select q.submission_id,
       'Blocked before the 2026-10-29 pipeline push: left as is (2026-10-29), not reported (2026-10-31)'
from private.fcc_submission_retry_queue q
where q.state = 'blocked'
  and q.updated_on < '2026-10-29 19:18:00+00'
on conflict (submission_id) do nothing;

-- As in 202610210104, without the ignored entries.
create or replace view private.fcc_queue_attention as
select
    case
        when q.state = 'blocked' then 'blocked: needs a person'
        when q.state = 'pending' then 'pending for more than 3 days'
        else 'expired, but its measurements are complete now'
    end as attention,
    q.submission_id,
    s.device_id,
    q.state,
    q.last_error,
    q.attempt_count,
    q.created_on as queued_on,
    date_trunc('second', now() - q.created_on) as queued_for,
    q.last_attempt_at,
    q.next_attempt_at,
    s.provider,
    s.sim_country_code,
    s.sim_network_code,
    q.updated_on as last_changed_on
from private.fcc_submission_retry_queue q
    left join public.fcc_submissions s on s.id = q.submission_id
where (q.state = 'blocked'
       or (q.state = 'pending' and q.created_on < now() - interval '3 days')
       or (q.state = 'expired' and private.fcc_queue_entry_is_ready(q.submission_id)))
  and not exists (select 1 from private.fcc_queue_ignored i where i.submission_id = q.submission_id);

-- As in 202610310001, without the ignored entries in its counts.
create or replace function private.fcc_queue_digest(p_new_since timestamptz default null)
returns text
language plpgsql
stable
security definer
set search_path = public, private, pg_temp
as
$$
declare
    max_lines constant integer := 20;
    lines text[];
    entry record;
    total integer;
    shown integer;
    groups integer;
    older integer;
begin
    lines := array[
        format('FCC submission queue, %s UTC',
               to_char(now() at time zone 'UTC', 'YYYY-MM-DD HH24:MI'))
    ];

    lines := lines || coalesce(
        (select 'In the queue: ' || string_agg(format('%s %s', n, state), ', ' order by sort_order)
         from (
             select q.state, count(*) as n,
                    array_position(array['pending', 'held', 'blocked', 'expired'], q.state) as sort_order
             from private.fcc_submission_retry_queue q
             where not exists (select 1 from private.fcc_queue_ignored i where i.submission_id = q.submission_id)
             group by q.state
         ) counts),
        'The queue is empty.'
    );

    -- A duplicate_test_id answer is logged as a success whose response is our error message
    -- ('FCC HTTP status 4xx for submission ...', see 202610210103); a real post's response is
    -- the FCC's body. Those weren't posted in this period, so they're counted apart.
    lines := lines || (
        select format('Last 24 hours: %s posted, %s already at the FCC (duplicate_test_id), %s failed attempts',
                      count(*) filter (where f.succeeded and coalesce(f.response_snippet, '') not like 'FCC HTTP status %'),
                      count(*) filter (where f.succeeded and f.response_snippet like 'FCC HTTP status %'),
                      count(*) filter (where not f.succeeded))
        from private.fcc_submission_failures f
        where f.occurred_on > now() - interval '1 day'
    );

    -- Blocked: one line per entry, newest first, so a new failure isn't hidden behind
    -- entries blocked long ago. With p_new_since, only the entries changed since then.
    select count(*) into total from private.fcc_queue_attention a where a.state = 'blocked';
    if total > 0 then
        lines := lines || ''::text || format('Blocked, needs a person (%s, newest first):', total);
        shown := 0;
        for entry in
            select a.*
            from private.fcc_queue_attention a
            where a.state = 'blocked'
              and (p_new_since is null or a.last_changed_on >= p_new_since)
            order by a.last_changed_on desc, a.queued_on desc
            limit max_lines
        loop
            lines := lines || format('- %s (device %s, queued %s): %s',
                entry.submission_id,
                coalesce(left(entry.device_id::text, 8), 'unknown'),
                to_char(entry.queued_on at time zone 'UTC', 'YYYY-MM-DD'),
                left(coalesce(entry.last_error, ''), 300));
            shown := shown + 1;
        end loop;
        older := 0;
        if p_new_since is not null then
            select count(*) into older
            from private.fcc_queue_attention a
            where a.state = 'blocked' and a.last_changed_on < p_new_since;
        end if;
        if total - older > shown then
            lines := lines || format('- and %s more', total - older - shown);
        end if;
        if older > 0 then
            lines := lines || format('- %s not listed: unchanged since before %s UTC (for the full list: '
                                     'select private.fcc_queue_digest();)',
                                     older, to_char(p_new_since at time zone 'UTC', 'YYYY-MM-DD HH24:MI'));
        end if;
    end if;

    -- Pending for more than 3 days: one line per device and reason. The reason has submission
    -- ids taken out (as in fcc_queue_health), or every failed post would be a group of its own.
    select count(*) into total from private.fcc_queue_attention a where a.state = 'pending';
    if total > 0 then
        lines := lines || ''::text || format('Pending for more than 3 days (%s):', total);
        shown := 0;
        for entry in
            select g.*, count(*) over () as groups
            from (
                select a.device_id,
                       regexp_replace(coalesce(a.last_error, 'New'),
                           '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', '<id>', 'gi') as reason,
                       count(*) as n,
                       min(a.queued_on) as oldest
                from private.fcc_queue_attention a
                where a.state = 'pending'
                group by 1, 2
            ) g
            order by g.oldest
            limit max_lines
        loop
            lines := lines || format('- device %s: %s, oldest queued %s: %s',
                coalesce(left(entry.device_id::text, 8), 'unknown'), entry.n,
                to_char(entry.oldest at time zone 'UTC', 'YYYY-MM-DD'), left(entry.reason, 300));
            shown := shown + 1;
            groups := entry.groups;
        end loop;
        if groups > shown then
            lines := lines || format('- and %s more devices or reasons', groups - shown);
        end if;
    end if;

    -- Expired, but complete by now.
    select count(*) into total from private.fcc_queue_attention a where a.state = 'expired';
    if total > 0 then
        lines := lines || ''::text
            || format('Expired, but their measurements are complete now (%s). The retry job puts '
                      'these back in line on its next run, so check that it is running. To put them '
                      'back in line by hand:', total)
            || '  select private.revive_expired_fcc_submission_retries();'::text;
        shown := 0;
        for entry in
            select a.*
            from private.fcc_queue_attention a
            where a.state = 'expired'
            order by a.queued_on
            limit max_lines
        loop
            lines := lines || format('- %s (device %s, queued %s)',
                entry.submission_id,
                coalesce(left(entry.device_id::text, 8), 'unknown'),
                to_char(entry.queued_on at time zone 'UTC', 'YYYY-MM-DD'));
            shown := shown + 1;
        end loop;
        if total > shown then
            lines := lines || format('- and %s more', total - shown);
        end if;
    end if;

    -- Held: counts by reason.
    select count(*) into total from private.fcc_submission_retry_queue q
    where q.state = 'held' and not exists (select 1 from private.fcc_queue_ignored i where i.submission_id = q.submission_id);
    if total > 0 then
        lines := lines || ''::text || format('Held until there is a way to send them (%s):', total);
        for entry in
            select q.last_error as reason, count(*) as n
            from private.fcc_submission_retry_queue q
            where q.state = 'held' and not exists (select 1 from private.fcc_queue_ignored i where i.submission_id = q.submission_id)
            group by 1
            order by 2 desc, 1
        loop
            lines := lines || format('- %s: %s', entry.reason, entry.n);
        end loop;
    end if;

    if not exists (select 1 from private.fcc_queue_attention) then
        lines := lines || ''::text || 'Nothing needs attention.'::text;
    end if;

    return array_to_string(lines, E'\n');
end;
$$;

-- As in 202610310001, without the ignored entries.
create or replace function private.send_fcc_queue_digest(p_force boolean default false)
returns text
language plpgsql
set search_path = public, private, pg_temp
as
$$
declare
    since timestamptz;
    last_sent timestamptz;
    n integer;
    news text[] := '{}';
    api_key text;
    email_to text;
    subject text;
    intro text;
    reply record;
begin
    -- One run at a time, so a run by hand and the job's can't both send.
    perform pg_advisory_xact_lock(hashtextextended('cellwatch fcc digest', 0));

    select coalesce(max(r.ran_at), now() - interval '1 day') into since from private.fcc_digest_runs r;
    select max(r.ran_at) into last_sent from private.fcc_digest_runs r where r.sent;

    select count(*) into n from private.fcc_submission_retry_queue q
    where q.state = 'blocked' and q.updated_on > since and not exists (select 1 from private.fcc_queue_ignored i where i.submission_id = q.submission_id);
    if n > 0 then
        news := news || format('%s newly blocked', n);
    end if;

    -- The daily re-check of held entries changes updated_on only when the reason changes.
    select count(*) into n from private.fcc_submission_retry_queue q
    where q.state = 'held' and q.updated_on > since and not exists (select 1 from private.fcc_queue_ignored i where i.submission_id = q.submission_id);
    if n > 0 then
        news := news || format('%s newly held', n);
    end if;

    -- A duplicate_test_id answer counts as a success here, as in the digest.
    select count(*) into n from private.fcc_submission_failures f
    where not f.succeeded and f.occurred_on > since;
    if n > 0 then
        news := news || format('%s failed FCC post%s', n, case when n = 1 then '' else 's' end);
    end if;

    -- Entries that passed 3 days pending since the last run. The digest lists all of them.
    select count(*) into n from private.fcc_submission_retry_queue q
    where q.state = 'pending'
      and q.created_on < now() - interval '3 days'
      and q.created_on >= since - interval '3 days'
      and not exists (select 1 from private.fcc_queue_ignored i where i.submission_id = q.submission_id);
    if n > 0 then
        news := news || format('%s now pending over 3 days', n);
    end if;

    if cardinality(news) = 0 and not coalesce(p_force, false)
       and last_sent > now() - interval '7 days' then
        insert into private.fcc_digest_runs (sent, detail) values (false, 'Nothing new');
        return 'nothing new';
    end if;

    select s.decrypted_secret into api_key
    from vault.decrypted_secrets s where s.name = 'fcc_digest_resend_api_key';
    select s.decrypted_secret into email_to
    from vault.decrypted_secrets s where s.name = 'fcc_digest_email_to';
    if coalesce(btrim(api_key), '') = '' or coalesce(btrim(email_to), '') = '' then
        raise exception 'The FCC digest email is not set up: add the Vault secrets '
                        'fcc_digest_resend_api_key and fcc_digest_email_to';
    end if;

    if cardinality(news) > 0 then
        subject := 'CellWatch FCC queue: ' || array_to_string(news, ', ');
        intro := format('New since %s UTC: %s.',
                        to_char(since at time zone 'UTC', 'YYYY-MM-DD HH24:MI'), array_to_string(news, ', '));
    elsif last_sent is null then
        subject := 'CellWatch FCC queue: first email';
        intro := 'Nothing new needs attention. This email comes when something is new, and at '
                 'least once a week, so silence for longer than a week means it stopped working.';
    else
        subject := 'CellWatch FCC queue: nothing new this week';
        intro := format('Nothing new since the last email (%s UTC). This weekly email shows that '
                        'sending still works.', to_char(last_sent at time zone 'UTC', 'YYYY-MM-DD HH24:MI'));
    end if;

    select * into reply
    from private.resend_http_post(
        btrim(api_key),
        jsonb_build_object(
            'from', 'CellWatch FCC queue <onboarding@resend.dev>',
            'to', jsonb_build_array(btrim(email_to)),
            'subject', subject,
            'text', intro || E'\n\n' || private.fcc_queue_digest(since)
        )::text
    );

    if reply.status is null or reply.status not between 200 and 299 then
        raise exception 'Resend did not send the FCC digest: HTTP % %',
            reply.status, left(coalesce(reply.content, ''), 300);
    end if;

    insert into private.fcc_digest_runs (sent, detail) values (true, left(reply.content, 300));
    return 'sent';
end;
$$;

commit;
