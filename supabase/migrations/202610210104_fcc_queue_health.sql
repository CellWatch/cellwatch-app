begin;

-- Make stuck FCC submissions visible (#119).
--
-- Entries waiting for data, held, or blocked sat in private.fcc_submission_retry_queue
-- without anyone noticing: when the FCC asked about one consumer's tests in October 2026,
-- 75 of them had been waiting since July. This adds:
--   * private.fcc_queue_health: entry counts by state, reason and device
--   * private.fcc_queue_attention: the entries a person should look at
--   * private.fcc_queue_digest(): a plain-text summary, ready to send
-- Expiry of entries that wait more than 30 days is in 202610210103.
--
-- Queue states: 'pending' (waiting for its measurements, or retrying after an FCC or network
-- error), 'held' (the FCC is known to reject it, it falls short of the spec, or it has no
-- carrier name; waiting for a decision, and checked again daily), 'blocked' (a person has to
-- fix something), 'expired' (its measurements didn't arrive within 30 days). Posted entries
-- leave the queue.

-- Entry counts by state, reason and device. The reason is last_error with submission ids
-- taken out, so entries that failed the same way are counted together. 'New' means not
-- checked yet.
create or replace view private.fcc_queue_health as
select
    q.state,
    coalesce(
        left(
            regexp_replace(
                q.last_error,
                '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',
                '<id>',
                'gi'
            ),
            200
        ),
        'New'
    ) as reason,
    s.device_id,
    count(*) as entries,
    count(*) filter (
        where q.state = 'pending' and q.created_on < now() - interval '3 days'
    ) as pending_over_3_days,
    min(q.created_on) as oldest_queued_on,
    max(q.updated_on) as last_changed_on
from private.fcc_submission_retry_queue q
    left join public.fcc_submissions s on s.id = q.submission_id
group by 1, 2, 3;

-- Whether an entry's measurements are complete, as its owner sees them.
-- private.fcc_submission_is_ready reads measurements as whoever calls it, and a view calls
-- functions as the role querying it. Row level security shows a role such as readonly_user
-- no device's measurements, so to it every expired entry looked incomplete, and
-- fcc_queue_attention left out the ones that weren't. This runs as its owner instead.
create or replace function private.fcc_queue_entry_is_ready(sub_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public, private, extensions, pg_temp
as
$$
    select private.fcc_submission_is_ready(sub_id);
$$;

revoke all on function private.fcc_queue_entry_is_ready(uuid) from public;

-- The entries that need a person: every blocked entry, entries pending for more than 3 days,
-- and expired entries whose measurements are complete by now. The retry job puts that last
-- group back in line on its next run, so one that stays listed means the job isn't running.
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
where q.state = 'blocked'
   or (q.state = 'pending' and q.created_on < now() - interval '3 days')
   or (q.state = 'expired' and private.fcc_queue_entry_is_ready(q.submission_id));

-- A plain-text summary of the queue, for a daily message.
--
-- Nothing sends it yet: the team hasn't chosen where it should go. Once it has, keep the
-- webhook URL or API key in Vault rather than in the job command, and schedule a daily job.
-- For a Slack incoming webhook, with pg_net (create extension pg_net if it isn't installed):
--
--     select vault.create_secret('<webhook url>', 'fcc_digest_webhook_url');
--     select cron.schedule('fcc-queue-digest', '0 13 * * *', $job$
--         select net.http_post(
--             url := (select decrypted_secret from vault.decrypted_secrets
--                     where name = 'fcc_digest_webhook_url'),
--             body := jsonb_build_object('text', private.fcc_queue_digest())
--         )
--         where exists (select 1 from private.fcc_queue_attention);
--     $job$);
--
-- 13:00 UTC is 9:00 in Atlanta during daylight saving time. Without the where clause it
-- sends every day, even when nothing needs attention. For email, post the same text to
-- the chosen email API instead.
--
-- The digest runs as its owner for whoever calls it (readonly_user), so its search_path ends
-- in pg_temp: otherwise Postgres looks up type names in the caller's temporary schema first,
-- and a temporary domain named text would run the caller's code as the owner. Through
-- fcc_queue_attention it also calls private.fcc_submission_is_ready (so does
-- fcc_queue_entry_is_ready, as its owner, for anyone reading the view), whose own
-- search_path (from the live schema) has the same gap for its m.type::text, so that one gets
-- pg_temp last too.
alter function private.fcc_submission_is_ready(uuid)
    set search_path = public, private, extensions, pg_temp;

create or replace function private.fcc_queue_digest()
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
    -- entries blocked long ago.
    select count(*) into total from private.fcc_queue_attention a where a.state = 'blocked';
    if total > 0 then
        lines := lines || ''::text || format('Blocked, needs a person (%s, newest first):', total);
        shown := 0;
        for entry in
            select a.*
            from private.fcc_queue_attention a
            where a.state = 'blocked'
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
        if total > shown then
            lines := lines || format('- and %s more', total - shown);
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

revoke all on function private.fcc_queue_digest() from public;

-- Whoever can read fcc_queue_attention needs to be able to call the readiness check it
-- uses: readonly_user, and members of pg_read_all_data, which can read every view (as for
-- fcc_submission_payload in 202610210101).
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'readonly_user') then
        grant execute on function private.fcc_queue_digest() to readonly_user;
        grant execute on function private.fcc_queue_entry_is_ready(uuid) to readonly_user;
    end if;
end;
$$;

grant execute on function private.fcc_queue_entry_is_ready(uuid) to pg_read_all_data;

commit;
