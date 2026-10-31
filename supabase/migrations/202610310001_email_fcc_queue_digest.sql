begin;

-- Email the FCC queue digest (#119), only when something is new.
--
-- 202610210104 built private.fcc_queue_digest() but left sending it for later. This emails it
-- with Resend (https://resend.com) from a daily job at 13:01 UTC (9:01 in Atlanta during
-- daylight saving time, 8:01 otherwise), but only when, since the job's last run,
--   * an entry was blocked or held,
--   * a post to the FCC failed, or
--   * an entry passed 3 days pending (waiting for its measurements).
-- A quiet queue sends nothing, except a short email after 7 days without one, so a broken
-- sender shows up as no more than a week of silence. The first run always sends, to show the
-- setup works; to send one now: select private.send_fcc_queue_digest(p_force => true);
-- Blocked entries that haven't changed since the last run are one line in the email: the 89
-- entries left blocked on 2026-10-29 would otherwise fill every email.
--
-- Set up on the live project by hand, because one is secret and the other personal: two
-- Vault secrets,
--   fcc_digest_resend_api_key  a Resend API key (sending access is enough)
--   fcc_digest_email_to        the address that gets the digest
-- Without a domain verified in Resend the sender is onboarding@resend.dev, and Resend only
-- delivers to the address the Resend account was created with.
--
-- Each run is logged in private.fcc_digest_runs. A failed send (Resend unreachable, or
-- refusing the request) raises instead, so the job's run shows as failed in
-- cron.job_run_details, nothing is logged, and the next run covers the missed period too.

create table if not exists private.fcc_digest_runs (
    id bigint generated always as identity primary key,
    ran_at timestamptz not null default now(),
    sent boolean not null,
    detail text
);

-- The digest as in 202610210104, plus p_new_since: when it's given, the blocked entries that
-- haven't changed since then are counted in one line instead of listed. Without it (as
-- readonly_user calls it) the text is the same as before. The old no-argument function is
-- dropped, or a call without arguments would match both.
drop function if exists private.fcc_queue_digest();

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
    select count(*) into total from private.fcc_submission_retry_queue q where q.state = 'held';
    if total > 0 then
        lines := lines || ''::text || format('Held until there is a way to send them (%s):', total);
        for entry in
            select q.last_error as reason, count(*) as n
            from private.fcc_submission_retry_queue q
            where q.state = 'held'
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

revoke all on function private.fcc_queue_digest(timestamptz) from public;

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'readonly_user') then
        grant execute on function private.fcc_queue_digest(timestamptz) to readonly_user;
    end if;
end;
$$;

-- The request to Resend, apart so that tests can replace it (like private.fcc_http_post).
-- Resend refuses requests without a User-Agent header.
create or replace function private.resend_http_post(
    p_api_key text, p_body text, out status integer, out content text)
language plpgsql
set search_path = public, private, extensions, pg_temp
as
$$
begin
    select r.status, r.content
    into status, content
    from http((
        'POST',
        'https://api.resend.com/emails',
        array[
            http_header('Authorization', 'Bearer ' || p_api_key),
            http_header('User-Agent', 'cellwatch-fcc-digest/1.0')
        ],
        'application/json',
        p_body
    )::http_request) r;
end;
$$;

revoke all on function private.resend_http_post(text, text) from public;

-- Sends the digest if something is new since the last run (see the top of this file), or
-- always with p_force. Returns 'sent' or 'nothing new'.
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
    where q.state = 'blocked' and q.updated_on > since;
    if n > 0 then
        news := news || format('%s newly blocked', n);
    end if;

    -- The daily re-check of held entries changes updated_on only when the reason changes.
    select count(*) into n from private.fcc_submission_retry_queue q
    where q.state = 'held' and q.updated_on > since;
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
      and q.created_on >= since - interval '3 days';
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

revoke all on function private.send_fcc_queue_digest(boolean) from public;

-- Daily at 13:01 UTC, a minute after the retry job's 13:00 run (it runs every 2 minutes), so
-- the changes that run makes are committed before the digest reads the queue. A change
-- stamped before a digest run but committed after it read the queue would be missed by that
-- run and the next. cron.schedule updates the existing job with this name. Skipped where
-- pg_cron isn't installed, such as a fresh local database.
do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        perform cron.schedule(
            'fcc-queue-digest',
            '1 13 * * *',
            'select private.send_fcc_queue_digest();'
        );
    end if;
end;
$$;

commit;
