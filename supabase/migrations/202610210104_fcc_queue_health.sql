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
-- error), 'held' (the FCC is known to reject it; waiting for a decision), 'blocked' (a
-- person has to fix something), 'expired' (its measurements didn't arrive within 30 days).
-- Posted entries leave the queue.

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

-- The entries that need a person: every blocked entry, entries pending for more than 3 days,
-- and expired entries whose measurements are complete by now. The retry job puts that last
-- group back in line on its next run, so one that stays listed means the job isn't running.
-- private.fcc_submission_is_ready reads measurements as the querying role, so a role that
-- is subject to row level security won't see that last group; fcc_queue_digest() always
-- does.
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
    s.sim_network_code
from private.fcc_submission_retry_queue q
    left join public.fcc_submissions s on s.id = q.submission_id
where q.state = 'blocked'
   or (q.state = 'pending' and q.created_on < now() - interval '3 days')
   or (q.state = 'expired' and private.fcc_submission_is_ready(q.submission_id));

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
create or replace function private.fcc_queue_digest()
returns text
language plpgsql
stable
security definer
set search_path = public, private
as
$$
declare
    max_lines constant integer := 20;
    lines text[];
    entry record;
    total integer;
    shown integer;
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

    lines := lines || (
        select format('Last 24 hours: %s posted, %s failed attempts',
                      count(*) filter (where f.succeeded),
                      count(*) filter (where not f.succeeded))
        from private.fcc_submission_failures f
        where f.occurred_on > now() - interval '1 day'
    );

    -- Blocked: one line per entry.
    select count(*) into total from private.fcc_queue_attention a where a.state = 'blocked';
    if total > 0 then
        lines := lines || ''::text || format('Blocked, needs a person (%s):', total);
        shown := 0;
        for entry in
            select a.*
            from private.fcc_queue_attention a
            where a.state = 'blocked'
            order by a.queued_on
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

    -- Pending for more than 3 days: one line per device and reason.
    select count(*) into total from private.fcc_queue_attention a where a.state = 'pending';
    if total > 0 then
        lines := lines || ''::text || format('Pending for more than 3 days (%s):', total);
        for entry in
            select coalesce(left(a.device_id::text, 8), 'unknown') as device,
                   coalesce(a.last_error, 'New') as reason,
                   count(*) as n,
                   min(a.queued_on) as oldest
            from private.fcc_queue_attention a
            where a.state = 'pending'
            group by 1, 2
            order by min(a.queued_on)
            limit max_lines
        loop
            lines := lines || format('- device %s: %s, oldest queued %s: %s',
                entry.device, entry.n, to_char(entry.oldest at time zone 'UTC', 'YYYY-MM-DD'),
                left(regexp_replace(entry.reason,
                    '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', '<id>', 'gi'), 300));
        end loop;
    end if;

    -- Expired, but complete by now.
    select count(*) into total from private.fcc_queue_attention a where a.state = 'expired';
    if total > 0 then
        lines := lines || ''::text
            || format('Expired, but their measurements are complete now (%s). To post them:', total)
            || '  update private.fcc_submission_retry_queue set state = ''pending'', next_attempt_at = now()'::text
            || '  where state = ''expired'' and private.fcc_submission_is_ready(submission_id);'::text;
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

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'readonly_user') then
        grant execute on function private.fcc_queue_digest() to readonly_user;
    end if;
end;
$$;

commit;
