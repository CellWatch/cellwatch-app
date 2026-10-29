-- Tests for the FCC retry queue after the 2026-10-21 migrations: per-entry commits and
-- duplicate_test_id (#117), blocking a blank provider (#118), holding submissions the FCC
-- rejects (#124, #125), and expiry, health views and the digest (#119). Run by
-- supabase/db_tests/run.sh in a throwaway container. The real posting code runs, but a
-- stand-in replaces the HTTP request, so nothing is sent anywhere.
\set ON_ERROR_STOP on
set client_min_messages = notice;

\i /db_tests/lib/fcc_schema.sql
\i /migrations/202610210101_build_fcc_payload_per_submission.sql
\i /migrations/202610210103_fcc_retry_queue_processing.sql
\i /migrations/202610210104_fcc_queue_health.sql

-- How the stand-in FCC answers each submission (200 unless listed), and what it was sent.
create table test_fcc_reply (
    submission_id uuid primary key,
    status integer,
    content text,
    delay_seconds numeric not null default 0
);
create table test_http_log (submission_id uuid, body text);
-- Counts the requests sent. A sequence, because nextval() isn't undone when the caller's
-- transaction rolls back: test_http_log is, so it can't show that nothing was sent by a run
-- that then failed.
create sequence test_post_counter;
create function test_posts_sent() returns bigint
language sql as $$ select case when is_called then last_value else 0 end from test_post_counter $$;

create or replace function private.fcc_http_post(
    p_url text, p_hash_value text, p_body text, out status integer, out content text)
language plpgsql as $$
declare
    sub uuid := (p_body::jsonb #>> '{submissions,0,test_id}')::uuid;
    reply record;
begin
    perform nextval('test_post_counter');
    select * into reply from test_fcc_reply r where r.submission_id = sub;
    if found then
        perform pg_sleep(reply.delay_seconds);
        status := reply.status;
        content := reply.content;
    else
        status := 200;
        content := '{"status":"ok"}';
    end if;
    insert into test_http_log values (sub, p_body);
end;
$$;

insert into vault.secrets (id, name, secret)
values ('00000000-0000-0000-0000-00000000f0cc', 'fcc_hash', 'test-hash-value');
insert into private.fcc_submission_info (env, url, hash_secret_id)
values ('prod', 'https://bdc.example.invalid/api/challenge', '00000000-0000-0000-0000-00000000f0cc');

-- Adds a submission the way the app does (the live trigger queues it), with all three
-- measurements unless ready is false. Each measurement gets `locations_per_test` location
-- fixes, a second apart; the spec asks for at least two. `due_ago` orders the entries.
create function test_submission(
    ready boolean default true,
    provider text default 'at&t',
    sim_mcc text default '310',
    sim_mnc text default '410',
    net_mcc text default '310',
    net_mnc text default '410',
    locations_per_test integer default 2,
    queued_ago interval default interval '1 hour',
    due_ago interval default interval '1 minute'
) returns uuid
language plpgsql as $$
declare
    sub uuid := gen_random_uuid();
    m uuid;
    t text;
begin
    insert into public.fcc_submissions (
        id, provider, sim_country_code, sim_network_code, net_country_code, net_network_code)
    values (sub, provider, sim_mcc, sim_mnc, net_mcc, net_mnc);

    update private.fcc_submission_retry_queue
    set created_on = now() - queued_ago, next_attempt_at = now() - due_ago
    where submission_id = sub;

    if ready then
        foreach t in array array['latency', 'download', 'upload'] loop
            insert into public.measurements (group_id, type) values (sub, t) returning id into m;
            if t = 'latency' then
                insert into public.latency_data (measurement_id, rtt) values (m, 30000);
            else
                insert into public.upload_download_data (measurement_id, duration, bytes)
                values (m, 5000000, 1000000);
            end if;
            insert into public.locations (measurement_id, "timestamp", lat, lon)
            select m, now() - n * interval '1 second', 33.7756, -84.3963
            from generate_series(1, locations_per_test) n;
        end loop;
    end if;

    return sub;
end;
$$;

create function test_reset() returns void
language sql as $$
    truncate public.fcc_submissions, private.fcc_submission_retry_queue,
        private.fcc_submission_failures, public.measurements, public.latency_data,
        public.upload_download_data, public.locations, public.cells,
        test_fcc_reply, test_http_log;
$$;

-- Submission ids are passed between the psql steps below as settings.
create function test_id(name text) returns uuid
language sql as $$ select current_setting('test.' || name)::uuid $$;

create function test_set_id(name text, sub uuid) returns void
language sql as $$ select set_config('test.' || name, sub::text, false) $$;

-- #117: a CALL from the top level commits after each entry.
do $$
begin
    perform test_reset();
    perform test_set_id('a', test_submission(due_ago => interval '3 minutes'));
    perform test_set_id('b', test_submission(due_ago => interval '2 minutes'));
    perform test_set_id('c', test_submission(due_ago => interval '1 minute'));
end;
$$;

call private.run_fcc_submission_retries(10) \gset
set test.processed = :'processed';

do $$
begin
    perform expect(current_setting('test.processed') = '3', 'a CALL from the top level posts every due entry');
    perform expect((select count(*) from public.fcc_submissions where submitted) = 3
                   and not exists (select 1 from private.fcc_submission_retry_queue)
                   and (select count(*) from private.fcc_submission_failures where succeeded) = 3,
        'each post is marked sent, logged and removed from the queue');
    perform expect(
        (select count(distinct xmin::text) from public.fcc_submissions where submitted) = 3,
        'each post was recorded in its own transaction');
end;
$$;

-- Inside a transaction block the procedure can't commit, and must stop before posting.
do $$
begin
    perform test_reset();
    perform test_set_id('a', test_submission());
end;
$$;

select test_posts_sent() as sent_before \gset
\set LAST_ERROR_SQLSTATE none
\echo 'Next: a CALL inside a transaction block (an "invalid transaction termination" error is expected).'
\set ON_ERROR_STOP off
begin;
call private.run_fcc_submission_retries(10);
rollback;
\set ON_ERROR_STOP on
set test.last_sqlstate = :'LAST_ERROR_SQLSTATE';
set test.sent_before = :'sent_before';

do $$
begin
    perform expect(current_setting('test.last_sqlstate') = '2D000',
        'a CALL inside a transaction block fails with invalid transaction termination');
    perform expect(test_posts_sent() = current_setting('test.sent_before')::bigint
                   and (select state = 'pending' and attempt_count = 0
                        from private.fcc_submission_retry_queue where submission_id = test_id('a')),
        'it fails before posting anything');
end;
$$;

-- The 2026-08-17 incident: a run posts two entries, then times out on the third.
do $$
begin
    perform test_reset();
    perform test_set_id('a', test_submission(due_ago => interval '3 minutes'));
    perform test_set_id('b', test_submission(due_ago => interval '2 minutes'));
    perform test_set_id('c', test_submission(due_ago => interval '1 minute'));
    insert into test_fcc_reply values (test_id('c'), 200, '{"status":"ok"}', 3);
end;
$$;

\set LAST_ERROR_SQLSTATE none
\echo 'Next: a run that hits statement_timeout on its third entry (a statement timeout error is expected).'
set statement_timeout = '1500ms';
\set ON_ERROR_STOP off
call private.run_fcc_submission_retries(10);
\set ON_ERROR_STOP on
reset statement_timeout;
set test.last_sqlstate = :'LAST_ERROR_SQLSTATE';

do $$
begin
    perform expect(current_setting('test.last_sqlstate') = '57014', 'the run was cancelled by statement_timeout');
    perform expect(
        (select count(*) from public.fcc_submissions where submitted and id in (test_id('a'), test_id('b'))) = 2
        and (select count(*) from private.fcc_submission_failures
             where succeeded and submission_id in (test_id('a'), test_id('b'))) = 2
        and not exists (select 1 from private.fcc_submission_retry_queue
                        where submission_id in (test_id('a'), test_id('b'))),
        'the two posts before the timeout stay recorded');
    perform expect(
        not (select submitted from public.fcc_submissions where id = test_id('c'))
        and (select state = 'pending' and attempt_count = 0
             from private.fcc_submission_retry_queue where submission_id = test_id('c')),
        'the entry that timed out is still pending');
end;
$$;

-- Its post may well have reached the FCC. The next run gets duplicate_test_id.
update test_fcc_reply
set status = 400,
    delay_seconds = 0,
    content = '{"status":"error","errors":[{"field":"test_id","code":"duplicate_test_id","message":"test_id already exists"}]}'
where submission_id = (select current_setting('test.c')::uuid);

call private.run_fcc_submission_retries(10) \gset
set test.processed = :'processed';

do $$
declare
    s record;
begin
    select * into s from public.fcc_submissions where id = test_id('c');
    perform expect(s.submitted, 'a duplicate_test_id answer marks the submission sent');
    perform expect(s.submission = private.fcc_submission_payload(test_id('c')),
        'with the payload, as for any post');
    perform expect(s.submitted_on is null, 'and no time, since the earlier post''s wasn''t recorded');
    perform expect(s.submission_response like 'Not posted again: the FCC answered duplicate_test_id%'
                   and s.submission_response like '%FCC HTTP status 400 for submission%duplicate_test_id%',
        'the stored response says why, with the FCC''s answer');
    perform expect(not exists (select 1 from private.fcc_submission_retry_queue where submission_id = test_id('c')),
        'and the entry leaves the queue instead of being blocked');
    perform expect(
        (select succeeded and error_message is null and response_snippet like 'FCC HTTP status 400%duplicate_test_id%'
         from private.fcc_submission_failures where submission_id = test_id('c')),
        'it is logged as a success, with the FCC''s answer as the response');
    perform expect(current_setting('test.processed') = '1', 'it counts as processed');
end;
$$;

do $$
begin
    perform expect(private.fcc_response_is_duplicate_test_id(400, '{"errors":[{"code":"duplicate_test_id"}]}'),
        'duplicate_test_id in a 400 answer is recognised');
    perform expect(private.fcc_response_is_duplicate_test_id(409, E'{"errors":[\n{"code":"other"},\n{"code":"duplicate_test_id"}]}'),
        'among other errors, and across lines');
    perform expect(private.fcc_response_is_duplicate_test_id(400, '{"errors":[{"code":"DUPLICATE_TEST_ID"}]}'),
        'in any case');
    perform expect(private.fcc_response_is_duplicate_test_id(400,
            '{"errors":[' || repeat('{"code":"other","message":"x"},', 20) || '{"code":"duplicate_test_id"}]}'),
        'past the first 500 characters');
    perform expect(not private.fcc_response_is_duplicate_test_id(503, 'duplicate_test_id'),
        'not in a 5xx answer');
    perform expect(not private.fcc_response_is_duplicate_test_id(400, '{"errors":[{"code":"null_properties"}]}'),
        'not for other validation errors');
    perform expect(not private.fcc_response_is_duplicate_test_id(400, '{"errors":[{"code":"duplicate_test_ids"}]}'),
        'not for a longer word');
    perform expect(not private.fcc_response_is_duplicate_test_id(400, null)
                   and not private.fcc_response_is_duplicate_test_id(null, 'duplicate_test_id'),
        'not without a body or a status');
end;
$$;

-- The same through a run: an answer whose duplicate_test_id comes after the 500 characters the
-- error message keeps, and one for a submission that someone posts by hand, in another
-- session, while the job's post of it is in flight (the job's post takes 3 seconds; the other
-- session records its answer after 1).
create table test_other_session (done_at timestamptz);

do $$
begin
    perform test_reset();
    perform test_set_id('long', test_submission(due_ago => interval '2 minutes'));
    perform test_set_id('meanwhile', test_submission(due_ago => interval '1 minute'));
    insert into test_fcc_reply (submission_id, status, content) values (
        test_id('long'), 400,
        '{"status":"error","errors":[' || repeat('{"field":"x","code":"other","message":"some other problem"},', 10)
            || '{"field":"test_id","code":"duplicate_test_id"}]}');
    insert into test_fcc_reply (submission_id, status, content, delay_seconds) values (
        test_id('meanwhile'), 400, '{"errors":[{"code":"duplicate_test_id"}]}', 3);
end;
$$;

select current_database() as test_db, current_setting('test.meanwhile') as meanwhile_id \gset
\setenv TEST_DB :test_db
\setenv MEANWHILE_ID :meanwhile_id
\! psql -U postgres -d "$TEST_DB" -X -q -c "select pg_sleep(1)" -c "update public.fcc_submissions set submitted = true, submitted_on = now(), submission_response = '{\"status\":\"ok\",\"refId\":\"REAL-REF-123\"}' where id = '$MEANWHILE_ID'" -c "insert into test_other_session values (clock_timestamp())" >/dev/null 2>&1 &
call private.run_fcc_submission_retries(10) \gset
select clock_timestamp() as run_done \gset
set test.run_done = :'run_done';

do $$
begin
    for i in 1 .. 100 loop
        exit when exists (select 1 from test_other_session);
        perform pg_sleep(0.05);
    end loop;
    perform expect((select done_at < current_setting('test.run_done')::timestamptz from test_other_session),
        'setup: the other session recorded its post while the job''s post was in flight');
    perform expect(
        (select submitted and submission_response like 'Not posted again%'
         from public.fcc_submissions where id = test_id('long'))
        and not exists (select 1 from private.fcc_submission_retry_queue where submission_id = test_id('long')),
        'duplicate_test_id past the first 500 characters of the answer is recognised');
    perform expect(
        (select submitted and submission_response = '{"status":"ok","refId":"REAL-REF-123"}'
         from public.fcc_submissions where id = test_id('meanwhile'))
        and not exists (select 1 from private.fcc_submission_retry_queue where submission_id = test_id('meanwhile')),
        'a submission recorded as sent meanwhile keeps that record');
end;
$$;

-- run_fcc_submission_retries has its own copy of the batch loop (process_fcc_submission_retries
-- below runs the same steps in one transaction, for manual use), so check its steps here: the
-- cap of 10, the cleanup of entries sent some other way, putting expired entries whose data has
-- arrived back in line, which outcomes count, and the time limit.
do $$
begin
    perform test_reset();
    perform test_submission() from generate_series(1, 12);
end;
$$;
call private.run_fcc_submission_retries(20) \gset
set test.processed = :'processed';
do $$
begin
    perform expect(current_setting('test.processed') = '10'
                   and (select count(*) from private.fcc_submission_retry_queue) = 2,
        'the procedure takes at most 10 entries, even when asked for more');
    perform test_reset();
    perform test_submission() from generate_series(1, 11);
end;
$$;
call private.run_fcc_submission_retries(null) \gset
set test.processed = :'processed';
do $$
begin
    perform expect(current_setting('test.processed') = '10', 'and 10 when the batch size is null');
end;
$$;

do $$
begin
    perform test_reset();
    perform test_set_id('sent_elsewhere', test_submission());
    update public.fcc_submissions set submitted = true, submission_response = 'sent by hand'
    where id = test_id('sent_elsewhere');
    perform test_set_id('expired_complete', test_submission(queued_ago => interval '40 days'));
    update private.fcc_submission_retry_queue set state = 'expired'
    where submission_id = test_id('expired_complete');
    -- Held for a reason that no longer applies, and due to be checked again.
    perform test_set_id('held_released', test_submission());
    update private.fcc_submission_retry_queue
    set state = 'held', last_error = 'One location: an older rule', next_attempt_at = now() - interval '1 minute'
    where submission_id = test_id('held_released');
    perform test_submission(ready => false);
    perform test_submission(locations_per_test => 0);
    perform test_submission(provider => '');
    perform test_set_id('rejected', test_submission());
    insert into test_fcc_reply (submission_id, status, content)
    values (test_id('rejected'), 400, '{"errors":[{"code":"null_properties"}]}');
    perform test_set_id('duplicate', test_submission());
    insert into test_fcc_reply (submission_id, status, content)
    values (test_id('duplicate'), 400, '{"errors":[{"code":"duplicate_test_id"}]}');
end;
$$;
select test_posts_sent() as sent_before \gset
call private.run_fcc_submission_retries(10) \gset
set test.processed = :'processed';
set test.sent_before = :'sent_before';
do $$
begin
    perform expect(
        not exists (select 1 from private.fcc_submission_retry_queue where submission_id = test_id('sent_elsewhere'))
        and (select submission_response = 'sent by hand' from public.fcc_submissions where id = test_id('sent_elsewhere')),
        'the procedure drops the entry of a submission sent some other way, and leaves its record alone');
    perform expect((select submitted from public.fcc_submissions where id = test_id('expired_complete')),
        'it puts an expired entry whose data has arrived back in line, and posts it');
    perform expect((select submitted from public.fcc_submissions where id = test_id('held_released')),
        'it checks due held entries again, and posts one that no reason applies to any more');
    perform expect(test_posts_sent() - current_setting('test.sent_before')::bigint = 4,
        'it posts only the entries that can be posted');
    perform expect(current_setting('test.processed') = '4',
        'and counts the posted, rejected and already-at-the-FCC entries, not the waiting or held ones');
    perform expect(
        (select state = 'blocked' and attempt_count = 1
         from private.fcc_submission_retry_queue where submission_id = test_id('rejected')),
        'a rejection blocks its entry, and the run goes on');
end;
$$;

-- #14: a failure that isn't the FCC rejecting the test (an outage, a rotated hash value, a
-- wrong URL) ends the run, so it costs one request per run, not one per ready entry.
do $$
begin
    perform test_reset();
    perform test_set_id('first', test_submission(due_ago => interval '5 minutes'));
    perform test_submission(due_ago => interval '4 minutes') from generate_series(1, 4);
    insert into test_fcc_reply (submission_id, status, content)
    select q.submission_id, 401, '{"message":"invalid hash_value"}' from private.fcc_submission_retry_queue q;
end;
$$;
select test_posts_sent() as sent_before \gset
call private.run_fcc_submission_retries(10) \gset
set test.processed = :'processed';
set test.sent_before = :'sent_before';
do $$
begin
    perform expect(test_posts_sent() - current_setting('test.sent_before')::bigint = 1
                   and current_setting('test.processed') = '1',
        'the procedure stops after a 401: one request, one entry processed');
    perform expect(
        (select state = 'pending' and attempt_count = 1
         from private.fcc_submission_retry_queue where submission_id = test_id('first')),
        'the 401 is retried, not blocked');
    perform expect(
        (select count(*) from private.fcc_submission_retry_queue where state = 'pending' and attempt_count = 0) = 4,
        'the other entries wait for the next run, with no attempt counted');

end;
$$;

-- The same in the function kept for manual use. (A failed post's test_http_log row is rolled
-- back with the error, so requests are counted with the sequence.)
do $$
declare
    sent_before bigint;
begin
    perform test_reset();
    perform test_submission() from generate_series(1, 3);
    insert into test_fcc_reply (submission_id, status, content)
    select q.submission_id, 503, 'try later' from private.fcc_submission_retry_queue q;
    sent_before := test_posts_sent();
    perform expect(private.process_fcc_submission_retries(10) = 1 and test_posts_sent() - sent_before = 1,
        'process_fcc_submission_retries stops after a 503 too');
end;
$$;

-- Which answers block an entry on its first attempt.
do $$
declare
    answer integer;
    sub uuid;
begin
    foreach answer in array array[401, 403, 404, 405, 408, 429, 500, 502, 503] loop
        perform test_reset();
        sub := test_submission();
        insert into test_fcc_reply (submission_id, status, content) values (sub, answer, 'not about the test');
        perform private.process_fcc_submission_retries(10);
        perform expect(
            (select state = 'pending' and attempt_count = 1
             from private.fcc_submission_retry_queue where submission_id = sub),
            format('a %s answer is retried', answer));
    end loop;

    foreach answer in array array[400, 409, 413, 422] loop
        perform test_reset();
        sub := test_submission();
        insert into test_fcc_reply (submission_id, status, content)
        values (sub, answer, '{"errors":[{"code":"null_properties"}]}');
        perform private.process_fcc_submission_retries(10);
        perform expect(
            (select state = 'blocked' and attempt_count = 1
             from private.fcc_submission_retry_queue where submission_id = sub),
            format('a %s answer rejects the test and blocks it', answer));
    end loop;

    -- Retried answers still block an entry at its 6th attempt.
    perform test_reset();
    sub := test_submission();
    insert into test_fcc_reply (submission_id, status, content) values (sub, 401, 'invalid hash_value');
    update private.fcc_submission_retry_queue set attempt_count = 5 where submission_id = sub;
    perform private.process_fcc_submission_retries(10);
    perform expect(
        (select state = 'blocked' and attempt_count = 6
         from private.fcc_submission_retry_queue where submission_id = sub),
        'a retried answer blocks the entry at the 6th attempt');
end;
$$;

do $$
begin
    perform test_reset();
    perform test_set_id('slow1', test_submission(due_ago => interval '3 minutes'));
    perform test_set_id('slow2', test_submission(due_ago => interval '2 minutes'));
    perform test_set_id('slow3', test_submission(due_ago => interval '1 minute'));
    insert into test_fcc_reply (submission_id, status, content, delay_seconds)
    select test_id(n), 200, '{"status":"ok"}', 1 from unnest(array['slow1', 'slow2', 'slow3']) n;
end;
$$;
call private.run_fcc_submission_retries(10, null, interval '1.5 seconds') \gset
set test.processed = :'processed';
do $$
begin
    perform expect(current_setting('test.processed') = '2'
                   and (select count(*) from private.fcc_submission_retry_queue) = 1,
        'it stops taking entries once the run has taken its time limit (1 s posts, 1.5 s limit)');
end;
$$;

-- The function kept for manual use still works, in one transaction.
do $$
begin
    perform test_reset();
    perform test_submission();
    perform test_submission();
    perform test_submission(ready => false);
    perform expect(private.process_fcc_submission_retries(10) = 2,
        'process_fcc_submission_retries still posts due entries and returns the count');
    perform expect((select count(*) from public.fcc_submissions where submitted) = 2
                   and (select count(*) from private.fcc_submission_retry_queue) = 1,
        'the entry still waiting for data stays queued');
end;
$$;

-- #118: provider checks happen once the data is complete, and a blank provider is never
-- filled in. Nobody can fix such an entry, so it is held rather than blocked: blocked entries
-- are the ones a person should look at.
do $$
declare
    waiting uuid;
    blank uuid;
    no_provider uuid;
begin
    perform test_reset();
    waiting := test_submission(ready => false, provider => '');
    blank := test_submission(provider => ' ');
    no_provider := test_submission(provider => null);

    perform private.process_fcc_submission_retries(10);

    perform expect(
        (select state = 'pending' and last_error like 'Waiting%'
         from private.fcc_submission_retry_queue where submission_id = waiting),
        'an entry without a provider that is still waiting for data keeps waiting');
    perform expect(
        (select state = 'held' and last_error = 'Missing provider: no carrier name (blank names are not filled in)'
         from private.fcc_submission_retry_queue where submission_id = blank),
        'a complete entry with a blank provider is held, with the reason');
    perform expect(
        (select state = 'held' and last_error like 'Missing provider:%'
         from private.fcc_submission_retry_queue where submission_id = no_provider),
        'a complete entry with a null provider is held');
    perform expect(
        (select provider = ' ' from public.fcc_submissions where id = blank)
        and (select provider is null from public.fcc_submissions where id = no_provider),
        'the job leaves a blank provider as the app sent it');
    perform expect(not exists (select 1 from test_http_log), 'nothing is posted without a provider');
    perform expect(not exists (select 1 from private.fcc_queue_attention)
                   and private.fcc_queue_digest() like '%Nothing needs attention.',
        'entries without a provider don''t count as needing a person');
end;
$$;

-- #124, #125: complete submissions the FCC is known to reject, or that fall short of the
-- spec, are held, not posted.
do $$
declare
    no_net uuid;
    blank_net uuid;
    no_locations uuid;
    one_location uuid;
    both_reasons uuid;
    incomplete uuid;
    held_and_no_provider uuid;
    fine uuid;
begin
    perform test_reset();
    no_net := test_submission(net_mcc => null, net_mnc => null);
    blank_net := test_submission(net_mnc => ' ');
    no_locations := test_submission(locations_per_test => 0);
    one_location := test_submission(locations_per_test => 1);
    both_reasons := test_submission(net_mcc => null, locations_per_test => 0);
    incomplete := test_submission(ready => false, net_mcc => null, locations_per_test => 0);
    held_and_no_provider := test_submission(provider => '', sim_mnc => '999', net_mcc => null);
    fine := test_submission();

    perform expect(private.process_fcc_submission_retries(10) = 1, 'only the submission without problems is posted');
    perform expect((select array_agg(submission_id) from test_http_log) = array[fine],
        'nothing is sent for the held ones');

    perform expect(
        (select state = 'held' and attempt_count = 0
                and last_error = 'No network registration: net MCC/MNC missing (the FCC rejects this as null_net_mobile_country_code)'
         from private.fcc_submission_retry_queue where submission_id = no_net),
        '#124: a submission without network codes is held, with the reason');
    perform expect(
        (select state = 'held' and last_error like 'No network registration%'
         from private.fcc_submission_retry_queue where submission_id = blank_net),
        '#124: a blank network code counts as missing');
    perform expect(
        (select state = 'held'
                and last_error = 'No locations: a test has no location fix (the FCC rejects this as null_properties)'
         from private.fcc_submission_retry_queue where submission_id = no_locations),
        '#125: a submission without locations is held, with the reason');
    perform expect(
        (select state = 'held'
                and last_error = 'One location: a test has only one location fix (the spec requires at least two)'
         from private.fcc_submission_retry_queue where submission_id = one_location),
        '#125: a submission with one location per test is held, with the reason');
    perform expect(
        (select state = 'held' and last_error like 'No network registration%; No locations%'
         from private.fcc_submission_retry_queue where submission_id = both_reasons),
        'both reasons are given when both apply');
    perform expect(
        (select state = 'pending' and last_error like 'Waiting%'
         from private.fcc_submission_retry_queue where submission_id = incomplete),
        '#125: an incomplete submission keeps waiting for its data instead of being held');
    perform expect(
        (select state = 'held' and last_error like 'No network registration%; Missing provider:%'
         from private.fcc_submission_retry_queue where submission_id = held_and_no_provider),
        'a missing provider is given alongside the other reasons');
    perform expect(not exists (select 1 from private.fcc_submission_failures where not succeeded),
        'holding is not logged as a failed attempt');

    -- Held entries are checked again once a day.
    perform expect(
        (select bool_and(next_attempt_at = now() + interval '1 day')
         from private.fcc_submission_retry_queue where state = 'held'),
        'a held entry is due to be checked again in a day');
    update private.fcc_submission_retry_queue set updated_on = now() - interval '1 day' where state = 'held';
    perform private.process_fcc_submission_retries(10);
    perform expect(
        (select count(*) from private.fcc_submission_retry_queue
         where state = 'held' and updated_on = now() - interval '1 day') = 6,
        'runs before then leave held entries alone');

    -- Due again, with the reasons still applying. One was stored in older wording.
    update private.fcc_submission_retry_queue set next_attempt_at = now() - interval '1 minute'
    where state = 'held';
    update private.fcc_submission_retry_queue set last_error = 'Only one location'
    where submission_id = one_location;
    perform private.process_fcc_submission_retries(10);
    perform expect(
        (select count(*) from private.fcc_submission_retry_queue
         where state = 'held' and next_attempt_at = now() + interval '1 day') = 6
        and (select count(*) from test_http_log) = 1,
        'a due held entry is checked again, and stays held while a reason applies');
    perform expect(
        (select last_error like 'One location: %' and updated_on = now()
         from private.fcc_submission_retry_queue where submission_id = one_location)
        and (select count(*) from private.fcc_submission_retry_queue
             where state = 'held' and updated_on = now() - interval '1 day') = 5,
        'a reason stored in older wording gets the current wording; the others are left as they were');

    -- Released by hand without a fix, an entry is checked again and held again.
    update private.fcc_submission_retry_queue set state = 'pending', next_attempt_at = now()
    where submission_id = no_net;
    perform private.process_fcc_submission_retries(10);
    perform expect(
        (select state = 'held' from private.fcc_submission_retry_queue where submission_id = no_net)
        and (select count(*) from test_http_log) = 1,
        'a released entry is checked again before posting');

    -- A reason stops applying: here the second fix turns up; a change to fcc_payload_hold_reason
    -- once the FCC answers works the same way. The next check releases the entry, and the run
    -- posts it.
    insert into public.locations (measurement_id, "timestamp", lat, lon)
    select m.id, now(), 33.7756, -84.3963 from public.measurements m where m.group_id = one_location;
    update private.fcc_submission_retry_queue set next_attempt_at = now() - interval '1 minute'
    where submission_id = one_location;
    perform private.process_fcc_submission_retries(10);
    perform expect(
        (select submitted from public.fcc_submissions where id = one_location)
        and not exists (select 1 from private.fcc_submission_retry_queue where submission_id = one_location),
        'a held entry that no reason applies to any more is posted at its next check');

    -- recheck_held_fcc_submission_retries(true) checks every held entry, due or not.
    insert into public.locations (measurement_id, "timestamp", lat, lon)
    select m.id, now() - n * interval '1 second', 33.7756, -84.3963
    from public.measurements m, generate_series(1, 2) n
    where m.group_id = no_locations;
    perform expect(private.recheck_held_fcc_submission_retries() = 0,
        'without p_all, entries not due are not checked');
    perform expect(private.recheck_held_fcc_submission_retries(true) = 1,
        'with p_all every held entry is checked, and the count released is returned');
    perform expect(
        (select state = 'pending' and next_attempt_at = now()
                and last_error = 'Released: no hold reason applies any more'
         from private.fcc_submission_retry_queue where submission_id = no_locations)
        and (select count(*) from private.fcc_submission_retry_queue where state = 'held') = 4,
        'the released entry is back in line, the rest stay held');
end;
$$;

do $$
declare
    with_net jsonb := '{"submissions":[{"provider_name":"at&t","net_mobile_country_code":"310","net_mobile_network_code":"410","tests":{}}]}';
    test_ok jsonb := '{"locations":[{"latitude":1},{"latitude":2}]}';
begin
    perform expect(private.fcc_payload_hold_reason(
            jsonb_set(with_net, '{submissions,0,tests}', jsonb_build_object('latency', test_ok, 'download', test_ok))) is null,
        'hold reason: none for network codes and locations');
    perform expect(private.fcc_payload_hold_reason(
            jsonb_set(jsonb_set(with_net, '{submissions,0,net_mobile_country_code}', 'null'),
                      '{submissions,0,tests}',
                      jsonb_build_object('latency', test_ok || '{"connection_type":"wifi"}',
                                         'download', test_ok || '{"connection_type":"WIFI"}'))) is null,
        'hold reason: missing network codes are fine when every test is over wifi');
    perform expect(private.fcc_payload_hold_reason(
            jsonb_set(jsonb_set(with_net, '{submissions,0,net_mobile_network_code}', 'null'),
                      '{submissions,0,tests}',
                      jsonb_build_object('latency', test_ok || '{"connection_type":"wifi"}',
                                         'download', test_ok || '{"connection_type":"cell"}'))) like 'No network registration%',
        'hold reason: but not when one test is over cell');
    perform expect(private.fcc_payload_hold_reason(
            jsonb_set(with_net, '{submissions,0,tests}',
                      jsonb_build_object('latency', test_ok, 'upload', '{"locations":[]}'::jsonb))) like 'No locations%',
        'hold reason: an empty locations list counts as missing');
    perform expect(private.fcc_payload_hold_reason(
            jsonb_set(with_net, '{submissions,0,tests}',
                      jsonb_build_object('latency', test_ok, 'upload', '{"locations":[{"latitude":1}]}'::jsonb))) like 'One location%',
        'hold reason: one location in a test is too few');
    perform expect(private.fcc_payload_hold_reason(
            jsonb_set(with_net, '{submissions,0,tests}',
                      jsonb_build_object('latency', '{"locations":[]}'::jsonb,
                                         'upload', '{"locations":[{"latitude":1}]}'::jsonb))) like 'No locations%; One location%',
        'hold reason: both location reasons when one test has none and another has one');
    perform expect(private.fcc_payload_hold_reason(
            jsonb_set(with_net, '{submissions,0,tests}',
                      jsonb_build_object('latency', '{"locations":[{"latitude":1},{"latitude":1}]}'::jsonb))) is null,
        'hold reason: the same fix listed twice counts as two');
    perform expect(private.fcc_payload_hold_reason(jsonb_set(with_net, '{submissions,0,provider_name}', '" "'))
                       = 'Missing provider: no carrier name (blank names are not filled in)'
                   and private.fcc_payload_hold_reason(with_net #- '{submissions,0,provider_name}') like 'Missing provider%',
        'hold reason: a blank or missing provider name');
    perform expect(private.fcc_payload_hold_reason(null) is null,
        'hold reason: none without a payload (posting raises its own error for that)');
end;
$$;

-- #119: entries still waiting for data 30 days after queueing expire.
do $$
declare
    expires uuid;
    keeps_waiting uuid;
    old_but_complete uuid;
    m uuid;
    t text;
begin
    perform test_reset();
    expires := test_submission(ready => false, queued_ago => interval '31 days');
    keeps_waiting := test_submission(ready => false, queued_ago => interval '29 days');
    old_but_complete := test_submission(queued_ago => interval '40 days');

    perform private.process_fcc_submission_retries(10);

    perform expect(
        (select state = 'expired' and last_error = 'Expired: measurements still incomplete 30 days after queueing'
         from private.fcc_submission_retry_queue where submission_id = expires),
        'an entry still incomplete 30 days after queueing expires');
    perform expect(
        (select state = 'pending' and next_attempt_at = now() + interval '1 hour'
         from private.fcc_submission_retry_queue where submission_id = keeps_waiting),
        'a younger one keeps waiting');
    perform expect((select submitted from public.fcc_submissions where id = old_but_complete),
        'an old entry whose data is complete is still posted');

    update private.fcc_submission_retry_queue
    set next_attempt_at = now() - interval '1 day', updated_on = now() - interval '1 day'
    where submission_id = expires;
    perform private.process_fcc_submission_retries(10);
    perform expect(
        (select updated_on = now() - interval '1 day' from private.fcc_submission_retry_queue
         where submission_id = expires),
        'expired entries are not checked again');

    -- The phone uploads the expired entry's measurements after all.
    foreach t in array array['latency', 'download', 'upload'] loop
        insert into public.measurements (group_id, type) values (expires, t) returning id into m;
        if t = 'latency' then
            insert into public.latency_data (measurement_id, rtt) values (m, 30000);
        else
            insert into public.upload_download_data (measurement_id, duration, bytes) values (m, 5000000, 1000000);
        end if;
        insert into public.locations (measurement_id, "timestamp", lat, lon)
        select m, now() - n * interval '1 second', 33.7756, -84.3963
        from generate_series(1, 2) n;
    end loop;

    perform private.process_fcc_submission_retries(10);
    perform expect((select submitted from public.fcc_submissions where id = expires),
        'an expired entry is posted once its measurements arrive');
    perform expect(
        not exists (select 1 from private.fcc_submission_retry_queue where submission_id = expires),
        'and leaves the queue');
end;
$$;

-- #119: health views and digest.
do $$
declare
    device_a uuid := gen_random_uuid();
    device_b uuid := gen_random_uuid();
    device_c uuid := gen_random_uuid();
    stuck_1 uuid;
    stuck_2 uuid;
    new_entry uuid;
    blocked uuid;
    held uuid;
    no_provider uuid;
    expired_complete uuid;
    expired_incomplete uuid;
    digest text;
    m uuid;
    t text;
begin
    perform test_reset();
    stuck_1 := test_submission(ready => false, queued_ago => interval '5 days');
    stuck_2 := test_submission(ready => false, queued_ago => interval '4 days');
    blocked := test_submission(queued_ago => interval '2 days');
    insert into test_fcc_reply (submission_id, status, content)
    values (blocked, 400, '{"errors":[{"code":"null_properties"}]}');
    held := test_submission(locations_per_test => 0);
    no_provider := test_submission(provider => '', sim_mnc => '999');
    expired_complete := test_submission(ready => false, queued_ago => interval '45 days');
    expired_incomplete := test_submission(ready => false, queued_ago => interval '40 days');
    update public.fcc_submissions set device_id = device_a where id in (stuck_1, stuck_2);
    update public.fcc_submissions set device_id = device_b where id in (blocked, held, no_provider);
    update public.fcc_submissions set device_id = device_c where id in (expired_complete, expired_incomplete);

    perform private.process_fcc_submission_retries(10);

    -- Queued after that run.
    new_entry := test_submission(ready => false);
    update public.fcc_submissions set device_id = device_a where id = new_entry;

    -- The phone finally uploads one expired submission's measurements.
    foreach t in array array['latency', 'download', 'upload'] loop
        insert into public.measurements (group_id, type) values (expired_complete, t) returning id into m;
        if t = 'latency' then
            insert into public.latency_data (measurement_id, rtt) values (m, 30000);
        else
            insert into public.upload_download_data (measurement_id, duration, bytes) values (m, 5000000, 1000000);
        end if;
    end loop;

    perform expect(
        (select entries = 2 and pending_over_3_days = 2
         from private.fcc_queue_health
         where state = 'pending' and device_id = device_a
           and reason = 'Waiting for complete latency/download/upload data'),
        'health: entries waiting more than 3 days are counted by device and reason');
    perform expect(
        (select entries = 1 and pending_over_3_days = 0 from private.fcc_queue_health
         where state = 'pending' and device_id = device_a and reason = 'New'),
        'health: an entry not checked yet is counted as New');
    perform expect(
        (select entries = 1 from private.fcc_queue_health
         where state = 'blocked' and device_id = device_b
           and reason = 'FCC HTTP status 400 for submission <id>: {"errors":[{"code":"null_properties"}]}'),
        'health: blocked entries are counted with their reason, without the submission id');
    perform expect(
        (select entries = 1 from private.fcc_queue_health
         where state = 'held' and device_id = device_b and reason like 'No locations%'),
        'health: held entries are counted with their reason');
    perform expect(
        (select entries = 1 from private.fcc_queue_health
         where state = 'held' and device_id = device_b and reason like 'Missing provider%'),
        'health: entries without a provider are counted as held');
    perform expect(
        (select entries = 2 from private.fcc_queue_health where state = 'expired' and device_id = device_c),
        'health: expired entries are counted');
    perform expect((select sum(entries) from private.fcc_queue_health) = 8, 'health: every entry is counted once');

    perform expect(
        (select array_agg(submission_id order by queued_on) from private.fcc_queue_attention)
            = array[expired_complete, stuck_1, stuck_2, blocked],
        'attention: blocked entries, entries pending more than 3 days, and expired entries that are complete now');

    digest := private.fcc_queue_digest();
    raise notice 'digest example:%', E'\n' || digest;
    perform expect(digest like 'FCC submission queue, %' and digest like '%In the queue: 3 pending, 2 held, 1 blocked, 2 expired%',
        'digest: counts by state');
    perform expect(digest like '%Last 24 hours: 0 posted, 0 already at the FCC (duplicate_test_id), 1 failed attempts%',
        'digest: counts the last day''s posts and failed attempts');
    perform expect(digest like format('%%Blocked, needs a person (1, newest first):%%- %s (device %s, queued %%): FCC HTTP status 400%%',
                                      blocked, left(device_b::text, 8)),
        'digest: lists each blocked entry with its reason');
    perform expect(digest like format('%%Pending for more than 3 days (2):%%- device %s: 2, oldest queued %%: Waiting for complete%%', left(device_a::text, 8)),
        'digest: groups long-pending entries by device');
    perform expect(digest like format('%%Expired, but their measurements are complete now (1).%%  select private.revive_expired_fcc_submission_retries();%%- %s (device%%', expired_complete),
        'digest: lists expired entries whose data has arrived, with how to release them');
    perform expect(digest like '%Held until there is a way to send them (2):%- Missing provider%: 1%- No locations%: 1%',
        'digest: counts held entries by reason');
    perform expect(digest not like '%Nothing needs attention%', 'digest: does not say all is well');

    -- What the digest says to run puts the entry back in line with a current reason.
    perform private.revive_expired_fcc_submission_retries();
    perform expect(
        (select state = 'pending' and last_error = 'Measurements arrived after the entry expired'
                and updated_on = now()
         from private.fcc_submission_retry_queue where submission_id = expired_complete),
        'digest: the statement it gives for expired entries leaves no stale Expired reason');

    perform test_reset();
    digest := private.fcc_queue_digest();
    perform expect(digest like '%The queue is empty.%Nothing needs attention.', 'digest: says so when nothing needs attention');
end;
$$;

-- #15: the digest groups long-pending entries without their submission ids, says how many
-- groups it left out, lists new blocked entries first, and counts duplicate_test_id answers
-- apart from posts.
do $$
declare
    device uuid := gen_random_uuid();
    old_block uuid;
    new_block uuid;
    sub uuid;
    digest text;
begin
    perform test_reset();

    -- 25 entries from one device, retrying for days after the same 503.
    for i in 1 .. 25 loop
        sub := test_submission(queued_ago => interval '5 days');
        update public.fcc_submissions set device_id = device where id = sub;
        update private.fcc_submission_retry_queue
        set attempt_count = 1, next_attempt_at = now() + interval '1 hour',
            last_error = format('FCC HTTP status 503 for submission %s: try later', sub)
        where submission_id = sub;
    end loop;
    -- 21 more devices with one waiting entry each: 22 device-and-reason groups in all.
    for i in 1 .. 21 loop
        sub := test_submission(ready => false, queued_ago => interval '4 days');
        update public.fcc_submissions set device_id = gen_random_uuid() where id = sub;
    end loop;
    -- Blocked long ago, and blocked just now (but queued earlier).
    old_block := test_submission(queued_ago => interval '10 days');
    update private.fcc_submission_retry_queue
    set state = 'blocked', last_error = 'blocked long ago', updated_on = now() - interval '9 days'
    where submission_id = old_block;
    new_block := test_submission(queued_ago => interval '20 days');
    update private.fcc_submission_retry_queue
    set state = 'blocked', last_error = 'blocked just now', updated_on = now()
    where submission_id = new_block;

    digest := private.fcc_queue_digest();

    perform expect(
        digest like format('%%- device %s: 25, oldest queued %%: FCC HTTP status 503 for submission <id>: try later%%',
                           left(device::text, 8))
        and digest not like '%FCC HTTP status 503%FCC HTTP status 503%',
        'digest: pending entries that failed the same way make one line');
    perform expect(digest like '%Pending for more than 3 days (46):%- and 2 more devices or reasons%',
        'digest: says how many device-and-reason groups it left out');
    perform expect(digest like '%Blocked, needs a person (2, newest first):%'
                   and position(new_block::text in digest) < position(old_block::text in digest),
        'digest: the entry blocked last comes first');

    perform test_reset();
    perform test_submission();
    sub := test_submission();
    insert into test_fcc_reply (submission_id, status, content)
    values (sub, 400, '{"errors":[{"code":"duplicate_test_id"}]}');
    perform private.process_fcc_submission_retries(10);
    perform expect(
        private.fcc_queue_digest() like '%Last 24 hours: 1 posted, 1 already at the FCC (duplicate_test_id), 0 failed attempts%',
        'digest: a duplicate_test_id answer is not counted as posted');
end;
$$;

-- #15: fcc_queue_attention lists expired entries that are complete for a role that row level
-- security keeps from reading measurements (readonly_user on prod; here a member of
-- pg_read_all_data, which 202610210104 grants the readiness check to).
do $$
begin
    perform test_reset();
    perform test_set_id('expired_complete', test_submission());
    update private.fcc_submission_retry_queue set state = 'expired'
    where submission_id = test_id('expired_complete');
end;
$$;
do $$ begin create role test_reader login; exception when duplicate_object then null; end $$;
grant pg_read_all_data to test_reader;
grant test_reader to postgres;   -- postgres isn't a superuser in this image, so it needs this to set role
alter table public.measurements enable row level security;
do $$
declare
    visible bigint;
    listed uuid[];
begin
    set local role test_reader;
    select count(*) into visible from public.measurements;
    select array_agg(submission_id) into listed from private.fcc_queue_attention;
    reset role;
    perform expect(visible = 0, 'setup: row level security hides every measurement from the reader');
    perform expect(listed = array[test_id('expired_complete')],
        'attention: a reader that can''t see measurements still gets expired entries that are complete');
end;
$$;
alter table public.measurements disable row level security;

-- A direct call to post_fcc_submission refuses, for prod, what the queue holds or blocks;
-- the demo environment takes anything.
insert into vault.secrets (id, name, secret)
values ('00000000-0000-0000-0000-0000000de300', 'fcc_demo_hash', 'test-demo-hash-value');
insert into private.fcc_submission_info (env, url, hash_secret_id)
values ('demo', 'https://bdc-demo.example.invalid/api/challenge', '00000000-0000-0000-0000-0000000de300');

do $$
declare
    one_location uuid;
    no_network uuid;
    no_provider uuid;
    refusal text;
    attempt text[];
begin
    perform test_reset();
    one_location := test_submission(locations_per_test => 1);
    no_network := test_submission(net_mcc => null, net_mnc => null);
    no_provider := test_submission(provider => ' ');

    -- Each: the submission, and how the refusal should read after 'FCC submission <id> '.
    foreach attempt slice 1 in array array[
        [one_location::text, 'is held: One location:%'],
        [no_network::text, 'is held: No network registration:%'],
        [no_provider::text, 'is held: Missing provider:%']
    ] loop
        begin
            perform private.post_fcc_submission(attempt[1]::uuid, 'prod');
            refusal := 'posted';
        exception when others then
            refusal := sqlerrm;
        end;
        perform expect(refusal like format('FCC submission %s %s', attempt[1], attempt[2]),
            format('a direct post to prod refuses it (%s)', refusal));
    end loop;
    perform expect(not exists (select 1 from public.fcc_submissions where submitted)
                   and not exists (select 1 from test_http_log),
        'and sends and marks nothing');

    perform expect(private.post_fcc_submission(one_location, 'demo'),
        'the demo environment takes a payload the queue would hold');
end;
$$;

-- readonly_user may run the digest, which runs as its owner. A caller's temporary types must
-- not run as that owner, in the digest or in fcc_submission_is_ready, which it calls through
-- fcc_queue_attention for expired entries.
do $$
declare
    sub uuid;
begin
    perform test_reset();
    sub := test_submission();
    update private.fcc_submission_retry_queue set state = 'expired' where submission_id = sub;
end;
$$;
do $$ begin create role test_caller login; exception when duplicate_object then null; end $$;
grant test_caller to postgres;   -- postgres isn't a superuser in this image, so it needs this to set role
grant usage on schema private to test_caller;
grant execute on function private.fcc_queue_digest() to test_caller;
select current_database() as test_db \gset
\c :test_db
set role test_caller;
\i /db_tests/lib/shadow_types.sql
select private.fcc_queue_digest() like '%Expired, but their measurements are complete now (1)%' as listed \gset
select pg_temp.test_shadow_ran_as_owner() as escalated \gset
reset role;
\c :test_db
select expect(:'listed' and not :'escalated',
    'a caller''s temporary types do not run as the owner of fcc_queue_digest or fcc_submission_is_ready') as ok \gset

-- pg_cron runs each job's command at the top level, so the procedure can commit there.
-- Checked with pg_cron itself where the image has it (it can only be installed in the
-- postgres database, and runs jobs in other databases with schedule_in_database).
select current_database() as test_db,
       exists (select 1 from pg_available_extensions where name = 'pg_cron')
           and current_setting('shared_preload_libraries') like '%pg_cron%' as have_cron \gset
\if :have_cron
do $$
begin
    perform test_reset();
    perform test_set_id('a', test_submission());
    perform test_set_id('b', test_submission());
end;
$$;
select current_setting('test.a') as cron_a, current_setting('test.b') as cron_b \gset

\c postgres
create extension if not exists pg_cron;
select cron.schedule_in_database('fcc-queue-test', '1 seconds',
    'call private.run_fcc_submission_retries(10);', :'test_db') as cron_job \gset
set test.cron_job = :'cron_job';
do $$
begin
    for i in 1 .. 60 loop
        exit when exists (
            select 1 from cron.job_run_details
            where jobid = current_setting('test.cron_job')::bigint and status in ('succeeded', 'failed'));
        perform pg_sleep(0.5);
    end loop;
end;
$$;
select coalesce((select status || ': ' || return_message from cron.job_run_details
                 where jobid = :cron_job and status in ('succeeded', 'failed')
                 order by runid limit 1), 'no finished run') as cron_result \gset
select cron.unschedule(:cron_job) as unscheduled \gset
\c :test_db
set test.cron_result = :'cron_result';
set test.a = :'cron_a';
set test.b = :'cron_b';

do $$
begin
    perform expect(current_setting('test.cron_result') like 'succeeded:%',
        format('pg_cron runs the CALL without errors (%s)', current_setting('test.cron_result')));
    perform expect(
        (select count(*) from public.fcc_submissions where submitted and id in (test_id('a'), test_id('b'))) = 2
        and (select count(distinct xmin::text) from public.fcc_submissions where id in (test_id('a'), test_id('b'))) = 2,
        'and the run commits each post separately');
end;
$$;
\else
\echo 'Skipping the pg_cron check: pg_cron is not preloaded in this image.'
\endif
