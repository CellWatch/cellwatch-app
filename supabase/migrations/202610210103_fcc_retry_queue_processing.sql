begin;

-- Record each FCC post in its own transaction (#117), hold submissions the FCC is known to
-- reject (#124, #125), and stop re-checking submissions whose data never arrives (#119).
--
-- process_fcc_submission_retries posted a whole batch inside one transaction. A POST can't
-- be rolled back, but the record of it can: the submitted flag, the stored response and the
-- success row in fcc_submission_failures. On 2026-08-17 a manual run posted several
-- submissions and then hit statement_timeout. The FCC kept the tests, our records didn't,
-- the job posted them again, the FCC answered 400 duplicate_test_id, and the job treated
-- that as a permanent failure and blocked them.
--
-- Changes:
--   * private.process_next_fcc_submission_retry() handles one due queue entry: check it,
--     post it, record the outcome.
--   * The new procedure private.run_fcc_submission_retries(batch_size) calls it for up to
--     10 entries and commits after each one, so a timeout or error later in the run can't
--     undo an earlier success. The cron job now runs it with CALL. pg_cron sends each job's
--     command on its own, outside a transaction block, so the procedure may commit
--     (supabase/db_tests/fcc_queue_test.sql checks this with pg_cron itself).
--   * process_fcc_submission_retries(batch_size) still works for anyone who calls it by
--     hand, but runs the whole batch in one transaction, like before.
--   * A 4xx answer whose body says duplicate_test_id means the FCC already has the test.
--     The submission is marked submitted, with a note in submission_response, and leaves
--     the queue instead of being blocked.
--   * Readiness is checked before the provider, so an entry whose measurements never
--     arrive isn't blocked as needing a human (#119). A complete entry with a blank provider
--     is still blocked as 'Missing provider'; the provider is never filled in (#118).
--   * New queue state 'held': complete submissions the FCC is known to reject, or that fall
--     short of the spec, kept until there is a way to send them. They aren't posted or
--     checked again until someone sets them back to 'pending'. 'blocked' still means a person
--     has to fix something. Three reasons so far (private.fcc_payload_hold_reason):
--       - No network registration (#124). The FCC rejects a test whose
--         net_mobile_country_code or net_mobile_network_code is null "when at least one
--         connection_type is null or equals cell" (null_net_mobile_country_code /
--         null_net_mobile_network_code). The spec says a null connection_type is treated
--         as cell, and our payload never sends connection_type, so in practice every
--         submission without network codes is held. We are waiting for the FCC to say
--         what to send; the codes are not filled in from the SIM.
--       - No locations (#125). The FCC rejects a test without locations (null_properties,
--         "required: locations"). The app uploads a measurement's locations together with
--         the measurement (insert_measurement), so once a submission is complete, missing
--         locations won't turn up later. This is checked only after the readiness check,
--         so incomplete submissions keep waiting for their data first.
--       - One location (#125). The spec (section 5.1) says each test has at least two
--         locations, a fix at its start and one at its end. The BDC accepted the one such
--         test we posted (March 2026), but it is short of the spec, so it waits for the FCC
--         to say one is enough. The same fix listed twice counts as two: that meets the
--         spec's wording, and the released app lists one fix twice for about 6% of tests.
--   * New queue state 'expired' (#119): an entry still waiting for its measurements 30
--     days after it was queued stops being checked. Waiting entries are re-checked hourly
--     and each check takes one of the job's 10 places per run, so a few hundred entries
--     whose data will never come (75 from one phone had been waiting since July 2026)
--     would crowd out new submissions. 30 days leaves time for phones that upload late:
--     the released app uploads only after a finished test or a cold start. Expired entries
--     stay in the queue, and each run puts back in line any whose measurements have turned
--     up since, with one statement outside the batch. A phone that couldn't upload for weeks
--     (#115) still gets its tests to the FCC once it can. On the live database, entries
--     already waiting more than 30 days expire at their next hourly check after this
--     migration.
--
-- Don't post big batches from the Supabase SQL editor or any other client that wraps the
-- call in one transaction: if the run fails or times out part way, the posts already made
-- stay at the FCC but our record of them is lost. Use
--     call private.run_fcc_submission_retries(1);
-- from psql. Called inside a transaction block, the procedure stops with "invalid
-- transaction termination" before it does anything.

-- Deploying. Changing the state check needs an ACCESS EXCLUSIVE lock on the queue, which the
-- app's FCC submission uploads write to (the insert trigger queues each one), and a waiting
-- lock request makes those uploads wait behind it until they fail at the API's 8 second
-- lock_timeout. A running retry job holds the queue for its whole run, so pause the job and
-- let a running run end first. The lock is taken with NOWAIT and retries, as in
-- 202610210001, so this never makes an upload wait; it gives up after a minute.
set local lock_timeout = '3s';
do $$
declare
    give_up_at constant timestamptz := clock_timestamp() + interval '1 minute';
begin
    loop
        begin
            lock table private.fcc_submission_retry_queue in access exclusive mode nowait;
            exit;
        exception when lock_not_available then
            if clock_timestamp() > give_up_at then
                raise exception 'gave up after a minute waiting for a moment when nothing was using the FCC retry queue'
                    using hint = 'Pause the fcc-submission-retries cron job, then run this migration again.';
            end if;
            perform pg_sleep(0.1);
        end;
    end loop;
end;
$$;

alter table private.fcc_submission_retry_queue
    drop constraint if exists fcc_submission_retry_queue_state_check,
    add constraint fcc_submission_retry_queue_state_check
        check (state in ('pending', 'blocked', 'held', 'expired'));

-- Why the FCC would reject this payload or it falls short of the spec, or null if we know of
-- no reason. Held entries say this in last_error. Several reasons are joined with '; '.
--
-- When the FCC answers, change this function to match, then release the entries, e.g.
--     update private.fcc_submission_retry_queue
--     set state = 'pending', next_attempt_at = now(), updated_on = now()
--     where state = 'held' and last_error like '%No network registration%';
-- The job checks each one again before posting it.
create or replace function private.fcc_payload_hold_reason(payload jsonb)
returns text
language sql
immutable
as
$$
    with
    submission as (
        select payload #> '{submissions,0}' as s
    ),
    tests as (
        select t.value as test
        from submission,
            jsonb_each(case when jsonb_typeof(s -> 'tests') = 'object' then s -> 'tests' else '{}' end) t
    )
    select nullif(
        concat_ws(
            '; ',
            case
                when (
                    select nullif(btrim(s ->> 'net_mobile_country_code'), '') is null
                        or nullif(btrim(s ->> 'net_mobile_network_code'), '') is null
                    from submission
                )
                and exists (
                    select 1
                    from tests
                    where coalesce(lower(test ->> 'connection_type'), 'cell') = 'cell'
                )
                then 'No network registration: net MCC/MNC missing (the FCC rejects this as '
                     || 'null_net_mobile_country_code)'
            end,
            case
                when exists (
                    select 1
                    from tests
                    where case
                        when jsonb_typeof(test -> 'locations') = 'array'
                            then jsonb_array_length(test -> 'locations') = 0
                        else true
                    end
                )
                then 'No locations: a test has no location fix (the FCC rejects this as '
                     || 'null_properties)'
            end,
            case
                when exists (
                    select 1
                    from tests
                    where jsonb_typeof(test -> 'locations') = 'array'
                      and jsonb_array_length(test -> 'locations') = 1
                )
                then 'One location: a test has only one location fix (the spec requires at '
                     || 'least two)'
            end
        ),
        ''
    );
$$;

-- True when a posting error is the FCC saying it already has this test_id.
-- post_fcc_submission raises 'FCC HTTP status NNN for submission <id>: <response body>',
-- with the body cut to 500 characters.
create or replace function private.fcc_error_is_duplicate_test_id(error_text text)
returns boolean
language sql
immutable
as
$$
    select coalesce(
        error_text ~ '^FCC HTTP status 4[0-9]{2} for submission [0-9a-f-]+: .*\mduplicate_test_id\M',
        false
    );
$$;

-- Handles the queue entry that has been due longest and returns what happened to it:
-- 'waiting', 'expired', 'held', 'blocked', 'posted', 'already_at_fcc' or 'failed'; null when
-- no entry is due.
-- Changes from the loop body of the 202610070001 version are marked "Changed". Like every
-- security definer function in this file, its search_path ends in pg_temp (see
-- 202610210101_build_fcc_payload_per_submission.sql for why).
create or replace function private.process_next_fcc_submission_retry()
returns text
language plpgsql
security definer
set search_path = public, private, extensions, pg_temp
as
$$
declare
    item private.fcc_submission_retry_queue%rowtype;
    sub record;
    hold_reason text;
    error_text text;
    response_text text;
    new_attempt_count integer;
    delay_seconds integer;
    permanent_failure boolean;
begin
    -- skip locked: an overlapping run leaves alone the entry this one is posting.
    select q.*
    into item
    from private.fcc_submission_retry_queue q
        join public.fcc_submissions s
          on s.id = q.submission_id
    where q.state = 'pending'
      and q.next_attempt_at <= now()
      and coalesce(s.submitted, false) = false
    order by q.next_attempt_at
    limit 1
    for update of q skip locked;

    if not found then
        return null;
    end if;

    begin
        -- Wait for complete latency, download, and upload payloads.
        -- This does not count as a failed FCC attempt.
        -- Changed: checked before the provider.
        if not private.fcc_submission_is_ready(item.submission_id) then
            -- Changed: stop checking after 30 days.
            if item.created_on < now() - interval '30 days' then
                update private.fcc_submission_retry_queue
                set
                    state = 'expired',
                    last_error = 'Expired: measurements still incomplete 30 days after queueing',
                    updated_on = now()
                where submission_id = item.submission_id;

                return 'expired';
            end if;

            update private.fcc_submission_retry_queue
            set
                next_attempt_at = now() + case
                    when item.created_on < now() - interval '1 day' then interval '1 hour'
                    else interval '5 minutes'
                end,
                last_error =
                    'Waiting for complete latency/download/upload data',
                updated_on = now()
            where submission_id = item.submission_id;

            return 'waiting';
        end if;

        -- Changed: hold what the FCC is known to reject. Checked before the provider, since
        -- fixing the provider wouldn't get these through.
        hold_reason := private.fcc_payload_hold_reason(
            private.fcc_submission_payload(item.submission_id)
        );

        if hold_reason is not null then
            update private.fcc_submission_retry_queue
            set
                state = 'held',
                last_error = hold_reason,
                updated_on = now()
            where submission_id = item.submission_id;

            return 'held';
        end if;

        -- A missing provider requires repair, not repeated FCC requests.
        select s.provider
        into sub
        from public.fcc_submissions s
        where s.id = item.submission_id;

        if nullif(btrim(sub.provider), '') is null then
            update private.fcc_submission_retry_queue
            set
                state = 'blocked',
                last_error = 'Missing provider',
                updated_on = now()
            where submission_id = item.submission_id;

            return 'blocked';
        end if;

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

        return 'posted';

    exception when others then
        error_text := sqlerrm;

        -- Changed: the FCC already has this test, from an earlier post whose record was lost
        -- (a run that timed out after posting, or a request that timed out after the FCC
        -- had accepted it). Record it as submitted instead of blocking it. The success row
        -- keeps the FCC's answer in error_message.
        if private.fcc_error_is_duplicate_test_id(error_text) then
            update public.fcc_submissions
            set
                submitted = true,
                submitted_on = now(),
                submission_response =
                    'Not posted again: the FCC answered duplicate_test_id, so it already has '
                    || 'this test from an earlier post whose response was not recorded. '
                    || 'FCC answer: ' || error_text
            where id = item.submission_id;

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
                true,
                item.fcc_env,
                left(error_text, 500)
            );

            delete from private.fcc_submission_retry_queue
            where submission_id = item.submission_id;

            return 'already_at_fcc';
        end if;

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

        return 'failed';
    end;
end;
$$;

revoke all on function private.process_next_fcc_submission_retry() from public;

-- Puts expired entries whose measurements have arrived since back in line, and returns how
-- many. One statement, so they don't take places in the batch until they can be posted.
create or replace function private.revive_expired_fcc_submission_retries()
returns integer
language plpgsql
security definer
set search_path = public, private, extensions, pg_temp
as
$$
declare
    revived integer;
begin
    update private.fcc_submission_retry_queue q
    set
        state = 'pending',
        next_attempt_at = now(),
        last_error = 'Measurements arrived after the entry expired',
        updated_on = now()
    where q.state = 'expired'
      and private.fcc_submission_is_ready(q.submission_id);

    get diagnostics revived = row_count;
    return revived;
end;
$$;

revoke all on function private.revive_expired_fcc_submission_retries() from public;

-- For manual use; the cron job uses the procedure below. Same results as before, but
-- everything happens in the caller's transaction, so a run that fails or times out part way
-- loses the record of posts the FCC has already accepted. Returns the number of entries
-- posted or failed; entries that were only checked (waiting, held, blocked, expired) don't
-- count.
create or replace function private.process_fcc_submission_retries(p_batch_size integer default 1)
returns integer
language plpgsql
security definer
set search_path to 'public', 'private', 'extensions', 'pg_temp'
as
$$
declare
    batch_size integer := greatest(1, least(coalesce(p_batch_size, 1), 10));
    started_at timestamptz := clock_timestamp();
    outcome text;
    processed integer := 0;
begin
    -- Remove queue entries that were submitted manually or elsewhere.
    delete from private.fcc_submission_retry_queue q
    using public.fcc_submissions s
    where s.id = q.submission_id
      and coalesce(s.submitted, false) = true;

    perform private.revive_expired_fcc_submission_retries();

    for i in 1 .. batch_size loop
        -- Leave the rest of the batch for the next run once this one has taken a minute,
        -- since each FCC request can take up to two minutes to fail.
        exit when clock_timestamp() - started_at > interval '60 seconds';

        outcome := private.process_next_fcc_submission_retry();
        exit when outcome is null;

        if outcome in ('posted', 'already_at_fcc', 'failed') then
            processed := processed + 1;
        end if;
    end loop;

    return processed;
end;
$$;

-- What the cron job runs. Commits after each entry. A procedure can't commit if it is
-- security definer or has a SET clause, so this one has neither and names our tables and
-- functions with their schema; the work happens in the security definer function above.
-- Returns the same count as process_fcc_submission_retries, in `processed`.
create or replace procedure private.run_fcc_submission_retries(
    p_batch_size integer default 10,
    inout processed integer default null
)
language plpgsql
as
$$
declare
    batch_size integer := greatest(1, least(coalesce(p_batch_size, 10), 10));
    started_at timestamptz := clock_timestamp();
    outcome text;
begin
    -- Fails right here, before anything is posted, when the procedure can't commit: inside
    -- a transaction block (begin; call ...), when called from a function, or when sent
    -- together with other statements in one request.
    commit;

    processed := 0;

    -- Remove queue entries that were submitted manually or elsewhere.
    delete from private.fcc_submission_retry_queue q
    using public.fcc_submissions s
    where s.id = q.submission_id
      and coalesce(s.submitted, false) = true;

    perform private.revive_expired_fcc_submission_retries();

    commit;

    for i in 1 .. batch_size loop
        -- Leave the rest of the batch for the next run once this one has taken a minute,
        -- since each FCC request can take up to two minutes to fail.
        exit when clock_timestamp() - started_at > interval '60 seconds';

        outcome := private.process_next_fcc_submission_retry();
        exit when outcome is null;

        if outcome in ('posted', 'already_at_fcc', 'failed') then
            processed := processed + 1;
        end if;

        -- Record this entry's outcome before taking the next one.
        commit;
    end loop;
end;
$$;

revoke all on procedure private.run_fcc_submission_retries(integer, integer) from public;

-- Run the procedure from the cron job. cron.schedule updates the existing job with this
-- name. Skipped where pg_cron isn't installed, such as a fresh local database.
do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        perform cron.schedule(
            'fcc-submission-retries',
            '*/2 * * * *',
            'call private.run_fcc_submission_retries(10);'
        );
    end if;
end;
$$;

commit;
