begin;

-- The retry job handled one queue entry every 2 minutes, and entries waiting for their
-- measurements were re-checked every 5 minutes forever. About 100 waiting entries kept new
-- submissions behind them for roughly 3.5 hours.
--
-- Changes from the live function (marked "Changed" below):
--   * entries that have waited more than a day are re-checked hourly instead of every
--     5 minutes; they become ready once the phone uploads their measurements
--   * a run stops taking new entries after 60 seconds, so a slow FCC API can't stretch out a
--     larger batch
-- The job itself now asks for 10 entries per run instead of 1.
create or replace function private.process_fcc_submission_retries(p_batch_size integer default 1)
returns integer
language plpgsql
security definer
set search_path to 'public', 'private', 'extensions'
as $$
declare
    item record;
    processed integer := 0;
    error_text text;
    response_text text;
    new_attempt_count integer;
    delay_seconds integer;
    permanent_failure boolean;
    -- Changed: when this run started, for the time limit below.
    started_at timestamptz := clock_timestamp();
begin
    -- Remove queue entries that were submitted manually or elsewhere.
    delete from private.fcc_submission_retry_queue q
    using public.fcc_submissions s
    where s.id = q.submission_id
      and coalesce(s.submitted, false) = true;

    for item in
        select q.*
        from private.fcc_submission_retry_queue q
        join public.fcc_submissions s
          on s.id = q.submission_id
        where q.state = 'pending'
          and q.next_attempt_at <= clock_timestamp()
          and coalesce(s.submitted, false) = false
        order by q.next_attempt_at
        limit greatest(1, least(coalesce(p_batch_size, 1), 10))
        for update of q skip locked
    loop
        -- Changed: leave the rest of the batch for the next run once this one has taken a
        -- minute, since each FCC request can take up to two minutes to fail.
        exit when clock_timestamp() - started_at > interval '60 seconds';

        -- A missing provider requires repair, not repeated FCC requests.
        if not exists (
            select 1
            from public.fcc_submissions s
            where s.id = item.submission_id
              and nullif(btrim(s.provider), '') is not null
        ) then
            update private.fcc_submission_retry_queue
            set
                state = 'blocked',
                last_error = 'Missing provider',
                updated_on = now()
            where submission_id = item.submission_id;

            continue;
        end if;

        -- Wait for complete latency, download, and upload payloads.
        -- This does not count as a failed FCC attempt.
        if not private.fcc_submission_is_ready(item.submission_id) then
            update private.fcc_submission_retry_queue
            set
                -- Changed: entries waiting more than a day are re-checked hourly so they
                -- don't crowd out new submissions.
                next_attempt_at = now() + case
                    when item.created_on < now() - interval '1 day' then interval '1 hour'
                    else interval '5 minutes'
                end,
                last_error =
                    'Waiting for complete latency/download/upload data',
                updated_on = now()
            where submission_id = item.submission_id;

            continue;
        end if;

        begin
            if private.post_fcc_submission(
                item.submission_id,
                item.fcc_env
            ) is distinct from true then
                raise exception 'FCC posting function returned false';
            end if;

            select s.submission_response
            into response_text
            from public.fcc_submissions s
            where s.id = item.submission_id;

            insert into private.fcc_submission_failures (
                submission_id,
                measurement_group_id,
                error_message,
                succeeded,
                fcc_env,
                response_snippet
            )
            values (
                item.submission_id,
                item.submission_id,
                null,
                true,
                item.fcc_env,
                left(coalesce(response_text, ''), 500)
            );

            delete from private.fcc_submission_retry_queue
            where submission_id = item.submission_id;

            processed := processed + 1;

        exception when others then
            error_text := sqlerrm;
            new_attempt_count := item.attempt_count + 1;

            -- Retry 408, 429, 5xx, and transport errors.
            -- Block other 4xx validation errors.
            permanent_failure :=
                error_text ~ 'FCC HTTP status 4[0-9][0-9]'
                and error_text !~ 'FCC HTTP status (408|429)';

            insert into private.fcc_submission_failures (
                submission_id,
                measurement_group_id,
                error_message,
                succeeded,
                fcc_env,
                response_snippet
            )
            values (
                item.submission_id,
                item.submission_id,
                error_text,
                false,
                item.fcc_env,
                left(error_text, 500)
            );

            delay_seconds := least(
                3600,
                (
                    60 * power(
                        2,
                        least(new_attempt_count - 1, 6)
                    )
                )::integer
            );

            update private.fcc_submission_retry_queue
            set
                state = case
                    when permanent_failure
                      or new_attempt_count >= 6
                        then 'blocked'
                    else 'pending'
                end,
                attempt_count = new_attempt_count,
                last_attempt_at = now(),
                next_attempt_at =
                    now() + make_interval(secs => delay_seconds),
                last_error = error_text,
                updated_on = now()
            where submission_id = item.submission_id;

            processed := processed + 1;
        end;
    end loop;

    return processed;
end;
$$;

-- Ask for 10 entries per run (the function caps the batch at 10). cron.schedule updates the
-- existing job with this name. Skipped where pg_cron isn't installed, such as a fresh local
-- database.
do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        perform cron.schedule(
            'fcc-submission-retries',
            '*/2 * * * *',
            'select private.process_fcc_submission_retries(10);'
        );
    end if;
end;
$$;

commit;
