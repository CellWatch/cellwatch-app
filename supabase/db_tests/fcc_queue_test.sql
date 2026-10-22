-- Tests for the FCC retry queue after the 2026-10-21 migrations: per-entry commits and
-- duplicate_test_id (#117), the provider block reason (#118), holding submissions the FCC
-- rejects (#124, #125), and expiry, health views and the digest (#119). Run by
-- supabase/db_tests/run.sh in a throwaway container. The real posting code runs, but a
-- stand-in replaces the HTTP request, so nothing is sent anywhere.
\set ON_ERROR_STOP on
set client_min_messages = notice;

\i /db_tests/lib/fcc_schema.sql
\i /migrations/202610210101_build_fcc_payload_per_submission.sql
\i /migrations/202610210102_fill_blank_fcc_provider_from_sim.sql
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

create or replace function private.fcc_http_post(
    p_url text, p_hash_value text, p_body text, out status integer, out content text)
language plpgsql as $$
declare
    sub uuid := (p_body::jsonb #>> '{submissions,0,test_id}')::uuid;
    reply record;
begin
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
    delete from private.carrier_by_plmn where provider like 'test %';
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

\set LAST_ERROR_SQLSTATE none
\echo 'Next: a CALL inside a transaction block (an "invalid transaction termination" error is expected).'
\set ON_ERROR_STOP off
begin;
call private.run_fcc_submission_retries(10);
rollback;
\set ON_ERROR_STOP on
set test.last_sqlstate = :'LAST_ERROR_SQLSTATE';

do $$
begin
    perform expect(current_setting('test.last_sqlstate') = '2D000',
        'a CALL inside a transaction block fails with invalid transaction termination');
    perform expect(not exists (select 1 from test_http_log)
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
    perform expect(s.submitted and s.submitted_on is not null,
        'a duplicate_test_id answer marks the submission sent');
    perform expect(s.submission_response like 'Not posted again: the FCC answered duplicate_test_id%'
                   and s.submission_response like '%FCC HTTP status 400 for submission%duplicate_test_id%',
        'the stored response says why, with the FCC''s answer');
    perform expect(not exists (select 1 from private.fcc_submission_retry_queue where submission_id = test_id('c')),
        'and the entry leaves the queue instead of being blocked');
    perform expect(
        (select succeeded and error_message like 'FCC HTTP status 400%'
         from private.fcc_submission_failures where submission_id = test_id('c')),
        'it is logged as a success, with the FCC''s answer');
    perform expect(current_setting('test.processed') = '1', 'it counts as processed');
end;
$$;

do $$
declare
    sub text := '6f1c2d3e-0000-4000-8000-000000000001';
begin
    perform expect(private.fcc_error_is_duplicate_test_id(
            format('FCC HTTP status 400 for submission %s: {"errors":[{"code":"duplicate_test_id"}]}', sub)),
        'duplicate_test_id in a 400 answer is recognised');
    perform expect(private.fcc_error_is_duplicate_test_id(
            format(E'FCC HTTP status 409 for submission %s: {"errors":[\n{"code":"other"},\n{"code":"duplicate_test_id"}]}', sub)),
        'among other errors, and across lines');
    perform expect(not private.fcc_error_is_duplicate_test_id(
            format('FCC HTTP status 503 for submission %s: duplicate_test_id', sub)),
        'not in a 5xx answer');
    perform expect(not private.fcc_error_is_duplicate_test_id(
            format('FCC HTTP status 400 for submission %s: {"errors":[{"code":"null_properties"}]}', sub)),
        'not for other validation errors');
    perform expect(not private.fcc_error_is_duplicate_test_id(
            format('FCC HTTP status 400 for submission %s: {"errors":[{"code":"duplicate_test_ids"}]}', sub)),
        'not for a longer word');
    perform expect(not private.fcc_error_is_duplicate_test_id('duplicate_test_id')
                   and not private.fcc_error_is_duplicate_test_id(null),
        'not for errors that did not come from the FCC');
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

-- #118: provider checks happen once the data is complete, and say which code is unknown.
do $$
declare
    waiting uuid;
    unknown_code uuid;
    no_code uuid;
    sent text;
begin
    perform test_reset();
    waiting := test_submission(ready => false, provider => '', sim_mnc => '999');
    unknown_code := test_submission(provider => '', sim_mnc => '999');
    no_code := test_submission(provider => null, sim_mcc => null, sim_mnc => null);

    perform private.process_fcc_submission_retries(10);

    perform expect(
        (select state = 'pending' and last_error like 'Waiting%'
         from private.fcc_submission_retry_queue where submission_id = waiting),
        'an entry without a provider that is still waiting for data keeps waiting');
    perform expect(
        (select state = 'blocked' and last_error = 'Missing provider (SIM 310/999 not in private.carrier_by_plmn)'
         from private.fcc_submission_retry_queue where submission_id = unknown_code),
        'a complete entry with an unknown SIM code is blocked, naming the code');
    perform expect(
        (select state = 'blocked' and last_error = 'Missing provider (no SIM code)'
         from private.fcc_submission_retry_queue where submission_id = no_code),
        'a complete entry without a SIM code is blocked, saying so');
    perform expect(not exists (select 1 from test_http_log), 'nothing is posted without a provider');

    -- Someone adds the code and releases the entry.
    insert into private.carrier_by_plmn values ('310', '999', 'test wireless');
    update private.fcc_submission_retry_queue
    set state = 'pending', next_attempt_at = now() - interval '1 minute'
    where submission_id = unknown_code;

    perform private.process_fcc_submission_retries(10);

    select body into sent from test_http_log where submission_id = unknown_code;
    perform expect(sent::jsonb #>> '{submissions,0,provider_name}' = 'test wireless'
                   and (select provider = 'test wireless' from public.fcc_submissions where id = unknown_code),
        'once the code is known, the job fills the provider and posts it');
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
        (select state = 'held' from private.fcc_submission_retry_queue where submission_id = held_and_no_provider),
        'a held submission is not also reported as missing its provider');
    perform expect(not exists (select 1 from private.fcc_submission_failures where not succeeded),
        'holding is not logged as a failed attempt');

    -- Held entries are left alone by later runs.
    update private.fcc_submission_retry_queue set next_attempt_at = now() - interval '1 hour'
    where state = 'held';
    update private.fcc_submission_retry_queue set updated_on = now() - interval '1 day' where state = 'held';
    perform private.process_fcc_submission_retries(10);
    perform expect(
        (select count(*) from private.fcc_submission_retry_queue
         where state = 'held' and updated_on = now() - interval '1 day') = 6,
        'later runs don''t pick up held entries');

    -- Released without a fix, an entry is checked again and held again.
    update private.fcc_submission_retry_queue set state = 'pending' where submission_id = no_net;
    perform private.process_fcc_submission_retries(10);
    perform expect(
        (select state = 'held' from private.fcc_submission_retry_queue where submission_id = no_net)
        and (select count(*) from test_http_log) = 1,
        'a released entry is checked again before posting');
end;
$$;

do $$
declare
    with_net jsonb := '{"submissions":[{"net_mobile_country_code":"310","net_mobile_network_code":"410","tests":{}}]}';
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
    expired_complete uuid;
    expired_incomplete uuid;
    digest text;
    m uuid;
    t text;
begin
    perform test_reset();
    stuck_1 := test_submission(ready => false, queued_ago => interval '5 days');
    stuck_2 := test_submission(ready => false, queued_ago => interval '4 days');
    blocked := test_submission(provider => '', sim_mnc => '999', queued_ago => interval '2 days');
    held := test_submission(locations_per_test => 0);
    expired_complete := test_submission(ready => false, queued_ago => interval '45 days');
    expired_incomplete := test_submission(ready => false, queued_ago => interval '40 days');
    update public.fcc_submissions set device_id = device_a where id in (stuck_1, stuck_2);
    update public.fcc_submissions set device_id = device_b where id in (blocked, held);
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
           and reason = 'Missing provider (SIM 310/999 not in private.carrier_by_plmn)'),
        'health: blocked entries are counted with their reason');
    perform expect(
        (select entries = 1 from private.fcc_queue_health
         where state = 'held' and device_id = device_b and reason like 'No locations%'),
        'health: held entries are counted with their reason');
    perform expect(
        (select entries = 2 from private.fcc_queue_health where state = 'expired' and device_id = device_c),
        'health: expired entries are counted');
    perform expect((select sum(entries) from private.fcc_queue_health) = 7, 'health: every entry is counted once');

    perform expect(
        (select array_agg(submission_id order by queued_on) from private.fcc_queue_attention)
            = array[expired_complete, stuck_1, stuck_2, blocked],
        'attention: blocked entries, entries pending more than 3 days, and expired entries that are complete now');

    digest := private.fcc_queue_digest();
    raise notice 'digest example:%', E'\n' || digest;
    perform expect(digest like 'FCC submission queue, %' and digest like '%In the queue: 3 pending, 1 held, 1 blocked, 2 expired%',
        'digest: counts by state');
    perform expect(digest like format('%%Blocked, needs a person (1):%%- %s (device %s, queued %%): Missing provider (SIM 310/999 not in private.carrier_by_plmn)%%',
                                      blocked, left(device_b::text, 8)),
        'digest: lists each blocked entry with its reason');
    perform expect(digest like format('%%Pending for more than 3 days (2):%%- device %s: 2, oldest queued %%: Waiting for complete%%', left(device_a::text, 8)),
        'digest: groups long-pending entries by device');
    perform expect(digest like format('%%Expired, but their measurements are complete now (1). To post them:%%- %s (device%%', expired_complete),
        'digest: lists expired entries whose data has arrived, with how to release them');
    perform expect(digest like '%Held until there is a way to send them (1):%- No locations%: 1%',
        'digest: counts held entries by reason');
    perform expect(digest not like '%Nothing needs attention%', 'digest: does not say all is well');

    perform test_reset();
    digest := private.fcc_queue_digest();
    perform expect(digest like '%The queue is empty.%Nothing needs attention.', 'digest: says so when nothing needs attention');
end;
$$;

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
