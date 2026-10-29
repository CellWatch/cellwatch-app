-- Tests for private.fcc_submission_payload and the reworked private.post_fcc_submission
-- (#116), run by supabase/db_tests/run.sh in a throwaway container. A stand-in replaces the
-- HTTP request, so nothing is sent anywhere.
\set ON_ERROR_STOP on
set client_min_messages = notice;

\i /db_tests/lib/fcc_schema.sql

-- Keep a copy of the live view to compare against, since the migration replaces it. With
-- search_path set to pg_catalog, pg_get_viewdef qualifies every table name.
create schema test_ref;
do $$
begin
    perform set_config('search_path', 'pg_catalog', true);
    execute 'create view test_ref.fcc_submissions_view as '
        || pg_get_viewdef('private.fcc_submissions_view'::regclass);
end;
$$;

\i /migrations/202610210101_build_fcc_payload_per_submission.sql

-- Timestamps in the payload are written in the session time zone; use one with an offset.
set timezone = 'America/Los_Angeles';

-- The fixture submissions, by name.
create table test_case (name text primary key, sub_id uuid not null unique);

create function test_submission(case_name text, with_details boolean default true) returns uuid
language plpgsql as $$
declare
    sub uuid := gen_random_uuid();
begin
    insert into test_case values (case_name, sub);
    if with_details then
        insert into public.fcc_submissions (
            id, contact_name, contact_email, contact_phone, device_timestamp, server_timestamp,
            source_ip, source_port, device_imei, device_tac, sim_country_code, sim_network_code,
            net_country_code, net_network_code, in_vehicle, external_antenna,
            device_manufacturer, device_model, device_os, app_name, app_version, provider,
            device_type, device_id)
        values (
            sub, 'Test "Person"', 'person@example.com', '+1 555 0100',
            '2026-07-09 10:15:30.123456-07', '2026-07-09 10:15:31.5-07',
            '203.0.113.7', 40123, '350000000000000', '35000000', '310', '410',
            '310', '410', false, false,
            'samsung', 'SM-A125U1', 'Android 13', 'CellWatch', '1.0.0', 'at&t',
            'phone', gen_random_uuid());
    else
        -- Only what the app always sends; everything else stays null.
        insert into public.fcc_submissions (id, source_ip) values (sub, '198.51.100.1');
    end if;
    return sub;
end;
$$;

create function test_measurement(sub uuid, measurement_type text, at timestamptz) returns uuid
language sql as $$
    insert into public.measurements (
        group_id, type, "timestamp", duration, success, carrier_aggregation,
        network_connected, network_available, network_roaming)
    values (sub, measurement_type, at, 2490000, true, null, true, true, false)
    returning id;
$$;

create function test_latency(measurement uuid) returns void
language sql as $$
    insert into public.latency_data (measurement_id, rtt, jitter, sent, received, servers)
    values (measurement, 30000.5, 8000, 200, 198, array['s1.example.net', 's2.example.net']);
$$;

create function test_throughput(measurement uuid, duration numeric, bytes numeric) returns void
language sql as $$
    insert into public.upload_download_data (
        measurement_id, warmup_duration, warmup_bytes, duration, bytes, servers)
    values (measurement, 2997000, 22970368, duration, bytes, array['s1.example.net']);
$$;

create function test_locations(measurement uuid, how_many integer) returns void
language sql as $$
    insert into public.locations (measurement_id, "timestamp", lat, lon, accuracy, speed, speed_accuracy)
    select
        measurement,
        '2026-07-09 10:15:00-07'::timestamptz + make_interval(secs => i * 1.25),
        38.8860976 + i / 1000000.0,
        -76.9962846 - i / 1000000.0,
        case when i % 2 = 0 then 4.5 end,
        case when i % 3 = 0 then null else 1.0 / 3 end,
        0.1
    from generate_series(1, how_many) i;
$$;

create function test_cells(measurement uuid, how_many integer) returns void
language sql as $$
    insert into public.cells (
        measurement_id, "timestamp", cell_id, physical_cell_id, cell_connection,
        network_generation, network_subtype, signal_strength, rssi, rsrp, rsrq, sinr,
        csi_rsrp, csi_rsrq, csi_sinr, cqi, spectrum_band, spectrum_bandwidth, arfcn)
    select
        measurement,
        '2026-07-09 10:15:00-07'::timestamptz + make_interval(secs => i),
        12345678 + i, 101 + i, i % 2 + 1,
        '4G', case when i = 1 then 'LTE' end, -95, null, -101.25, -10.5, 12.3,
        null, null, null, 9, 'B66', 20000.5, 66786
    from generate_series(1, how_many) i;
$$;

-- A complete group with several locations and cells per test, and some null fields.
do $$
declare
    sub uuid := test_submission('complete group, several locations and cells');
    m uuid;
begin
    m := test_measurement(sub, 'latency', '2026-07-09 10:15:00.25-07');
    perform test_latency(m);
    perform test_locations(m, 3);
    perform test_cells(m, 3);
    m := test_measurement(sub, 'download', '2026-07-09 10:15:10.5-07');
    perform test_throughput(m, 5005000, 107995136);
    perform test_locations(m, 2);
    perform test_cells(m, 2);
    m := test_measurement(sub, 'upload', '2026-07-09 10:15:20-07');
    perform test_throughput(m, 5343000, 14157824);
    perform test_locations(m, 1);
end;
$$;

-- A complete group without any locations or cells, and a bare submission row.
do $$
declare
    sub uuid := test_submission('no locations or cells, bare submission row', false);
begin
    perform test_latency(test_measurement(sub, 'latency', '2026-07-10 08:00:00-07'));
    perform test_throughput(test_measurement(sub, 'download', '2026-07-10 08:00:10-07'), 5000000, 1000000);
    perform test_throughput(test_measurement(sub, 'upload', '2026-07-10 08:00:20-07'), 5000000, 333333);
end;
$$;

-- Only latency and download so far.
do $$
declare
    sub uuid := test_submission('latency and download only');
    m uuid;
begin
    m := test_measurement(sub, 'latency', '2026-07-11 08:00:00-07');
    perform test_latency(m);
    perform test_cells(m, 1);
    m := test_measurement(sub, 'download', '2026-07-11 08:00:10-07');
    perform test_throughput(m, 5000000, 2500000);
    perform test_locations(m, 2);
end;
$$;

-- No latency data: the view gives "tests": null.
do $$
declare
    sub uuid := test_submission('download and upload, no latency');
begin
    perform test_throughput(test_measurement(sub, 'download', '2026-07-12 08:00:10-07'), 5000000, 2500000);
    perform test_throughput(test_measurement(sub, 'upload', '2026-07-12 08:00:20-07'), 5000000, 1500000);
    -- A latency measurement whose latency data never arrived.
    perform test_measurement(sub, 'latency', '2026-07-12 08:00:00-07');
end;
$$;

-- A submission whose measurements never arrived.
do $$ begin perform test_submission('no measurements'); end; $$;

-- Zero durations: bytes_sec is 0 when nothing was sent in no time, null otherwise.
do $$
declare
    sub uuid := test_submission('zero durations');
begin
    perform test_latency(test_measurement(sub, 'latency', '2026-07-13 08:00:00-07'));
    perform test_throughput(test_measurement(sub, 'download', '2026-07-13 08:00:10-07'), 0, 0);
    perform test_throughput(test_measurement(sub, 'upload', '2026-07-13 08:00:20-07'), 0, 4096);
end;
$$;

-- Rows from other groups, and orphaned locations and cells, must not leak in.
do $$
declare
    other uuid := gen_random_uuid();
    m uuid;
begin
    m := test_measurement(other, 'latency', '2026-07-09 10:15:00-07');
    perform test_latency(m);
    perform test_locations(m, 4);
    perform test_cells(m, 4);
    perform test_locations(null, 2);
    perform test_cells(null, 2);
end;
$$;

do $$
declare
    c record;
begin
    for c in select * from test_case order by name loop
        perform expect(
            private.fcc_submission_payload(c.sub_id)::text
                = (select v.data::text from test_ref.fcc_submissions_view v where v.submission_id = c.sub_id),
            format('%s: payload is byte for byte the same as the old view''s', c.name));
    end loop;

    perform expect(
        (select count(*) from test_ref.fcc_submissions_view) = (select count(*) from test_case)
        and not exists (
            select 1
            from test_ref.fcc_submissions_view old
                full join private.fcc_submissions_view new on new.submission_id = old.submission_id
            where new.data::text is distinct from old.data::text
               or new.submitted is distinct from old.submitted),
        'the redefined view gives the same rows as the old one');

    perform expect(private.fcc_submission_payload(gen_random_uuid()) is null,
        'there is no payload for an unknown submission');
end;
$$;

-- Locations and cells are listed in the order they were inserted, as the old view listed
-- them, even when that isn't time order.
do $$
declare
    sub uuid := test_submission('locations inserted out of order');
    m uuid := test_measurement(sub, 'latency', '2026-07-14 08:00:00-07');
    stamps text[];
begin
    perform test_latency(m);
    insert into public.locations (measurement_id, "timestamp", lat, lon)
    values (m, '2026-07-14 08:00:03-07', 1, 1),
           (m, '2026-07-14 08:00:01-07', 2, 2),
           (m, '2026-07-14 08:00:02-07', 3, 3);

    select array_agg(e ->> 'timestamp' order by n) into stamps
    from jsonb_array_elements(private.fcc_submission_payload(sub) #> '{submissions,0,tests,latency,locations}')
        with ordinality as x(e, n);

    perform expect(stamps = array['2026-07-14T08:00:03-07:00', '2026-07-14T08:00:01-07:00', '2026-07-14T08:00:02-07:00'],
        'locations are listed in the order they were inserted, not sorted by time');
    perform expect(
        private.fcc_submission_payload(sub)::text
            = (select v.data::text from test_ref.fcc_submissions_view v where v.submission_id = sub),
        'locations inserted out of order: payload is byte for byte the same as the old view''s');
end;
$$;

-- The cells from one scan share a timestamp. Sorting by (timestamp, id) would put these in
-- reverse; the payload keeps the order they were inserted in.
do $$
declare
    sub uuid := test_submission('cells from one scan');
    m uuid := test_measurement(sub, 'latency', '2026-07-15 08:00:00-07');
    ids numeric[];
begin
    perform test_latency(m);
    insert into public.cells (id, measurement_id, "timestamp", cell_id)
    values ('30000000-0000-0000-0000-000000000000', m, '2026-07-15 08:00:01-07', 1),
           ('20000000-0000-0000-0000-000000000000', m, '2026-07-15 08:00:01-07', 2),
           ('10000000-0000-0000-0000-000000000000', m, '2026-07-15 08:00:01-07', 3);

    select array_agg((e ->> 'cell_id')::numeric order by n) into ids
    from jsonb_array_elements(private.fcc_submission_payload(sub) #> '{submissions,0,tests,latency,cells}')
        with ordinality as x(e, n);

    perform expect(ids = array[1, 2, 3]::numeric[],
        'cells with the same timestamp are listed in the order they were inserted');
    perform expect(
        private.fcc_submission_payload(sub)::text
            = (select v.data::text from test_ref.fcc_submissions_view v where v.submission_id = sub),
        'cells from one scan: payload is byte for byte the same as the old view''s');
end;
$$;

-- Speed: add a few thousand other measurements with locations and cells, then compare one
-- lookup through the old view with the new function.
insert into public.measurements (group_id, type, app_name)
select gen_random_uuid(), 'latency', 'bulk' from generate_series(1, 5000);
insert into public.locations (measurement_id, lat, lon)
select m.id, 1, 1 from public.measurements m, generate_series(1, 4) where m.app_name = 'bulk';
insert into public.cells (measurement_id, cell_id)
select m.id, 1 from public.measurements m, generate_series(1, 10) where m.app_name = 'bulk';
analyze public.measurements, public.locations, public.cells;

do $$
declare
    sub uuid := (select sub_id from test_case where name = 'complete group, several locations and cells');
    started timestamptz;
    old_time interval;
    new_time interval;
    plan text := '';
    plan_line text;
begin
    started := clock_timestamp();
    perform v.data from test_ref.fcc_submissions_view v where v.submission_id = sub;
    old_time := clock_timestamp() - started;

    started := clock_timestamp();
    perform private.fcc_submission_payload(sub);
    new_time := clock_timestamp() - started;

    raise notice 'one payload with % measurements, % locations, % cells in the database: old view %, new function %',
        (select count(*) from public.measurements), (select count(*) from public.locations),
        (select count(*) from public.cells), old_time, new_time;
    perform expect(new_time * 10 < old_time, 'the new function is at least 10 times faster than the old view');

    for plan_line in execute format('explain select * from public.measurements where group_id = %L', sub) loop
        plan := plan || plan_line || E'\n';
    end loop;
    perform expect(plan like '%measurements_group_id_idx%', 'looking up a group''s measurements uses the new index');
end;
$$;

-- Posting. First check the real request function fails cleanly when nothing answers
-- (127.0.0.1 port 9, inside a container with no network).
select exists (select 1 from pg_extension where extname = 'http') as have_http \gset
\if :have_http
do $$
declare
    reply record;
begin
    select * into reply from private.fcc_http_post('http://127.0.0.1:9/', 'test-hash', '{}');
    perform expect(false, 'a request to a closed port fails');
exception when others then
    perform expect(sqlerrm ilike '%connect%', 'the real request function reaches http() (a closed port fails to connect)');
end;
$$;
\else
\echo 'Skipping the real http() check: the http extension is not installed in this image.'
\endif

-- From here on, a stand-in answers instead of the FCC.
create table test_http_log (url text, hash_value text, body text, at timestamptz default clock_timestamp());
create table test_http_reply (status integer, content text);
insert into test_http_reply values (200, '{"status":"ok","refId":"test-1"}');

-- Counts the requests sent. A sequence, because nextval() isn't undone when the caller's
-- transaction rolls back: test_http_log is, so it can't show that nothing was sent by a call
-- that then failed.
create sequence test_post_counter;
create function test_posts_sent() returns bigint
language sql as $$ select case when is_called then last_value else 0 end from test_post_counter $$;

create or replace function private.fcc_http_post(
    p_url text, p_hash_value text, p_body text, out status integer, out content text)
language plpgsql as $$
begin
    perform nextval('test_post_counter');
    insert into test_http_log (url, hash_value, body) values (p_url, p_hash_value, p_body);
    select r.status, r.content into status, content from test_http_reply r;
end;
$$;

insert into vault.secrets (id, name, secret)
values ('00000000-0000-0000-0000-00000000f0cc', 'fcc_hash', 'test-hash-value');
insert into private.fcc_submission_info (env, url, hash_secret_id)
values ('prod', 'https://bdc.example.invalid/api/challenge', '00000000-0000-0000-0000-00000000f0cc');

do $$
declare
    sub uuid := (select sub_id from test_case where name = 'complete group, several locations and cells');
    expected text := private.fcc_submission_payload(sub)::text;
    request record;
    s record;
begin
    perform expect(private.post_fcc_submission(sub, 'prod'), 'post_fcc_submission posts a complete group');

    select * into request from test_http_log;
    perform expect((select count(*) from test_http_log) = 1, 'it sends one request');
    perform expect(request.url = 'https://bdc.example.invalid/api/challenge'
                   and request.hash_value = 'test-hash-value',
        'the request goes to the configured URL with the hash from the vault');
    perform expect(request.body = expected, 'the request body is the payload, byte for byte');

    select * into s from public.fcc_submissions where id = sub;
    perform expect(s.submitted and s.submitted_on is not null
                   and s.submission::text = expected
                   and s.submission_response = '{"status":"ok","refId":"test-1"}',
        'the submission is marked sent with the payload and the FCC response');
end;
$$;

do $$
declare
    sub uuid := (select sub_id from test_case where name = 'no locations or cells, bare submission row');
begin
    update test_http_reply set status = 400, content = '{"errors":[{"code":"some_error"}]}';
    begin
        perform private.post_fcc_submission(sub, 'prod');
        perform expect(false, 'a 400 response raises an error');
    exception when others then
        perform expect(sqlerrm = format('FCC HTTP status 400 for submission %s: {"errors":[{"code":"some_error"}]}', sub),
            'a 400 response raises the same error message as before');
    end;
    perform expect(not (select submitted from public.fcc_submissions where id = sub),
        'a rejected submission is not marked sent');

    update test_http_reply set status = null, content = null;
    begin
        perform private.post_fcc_submission(sub, 'prod');
        perform expect(false, 'a missing status raises an error');
    exception when others then
        perform expect(sqlerrm = format('FCC submission %s returned no HTTP status', sub),
            'a response without a status raises the same error message as before');
    end;
end;
$$;

do $$
declare
    sub uuid := (select sub_id from test_case where name = 'latency and download only');
    sent_before bigint := test_posts_sent();
    refusal text;
begin
    update test_http_reply set status = 200, content = 'ok';
    begin
        perform private.post_fcc_submission(sub, 'prod');
        refusal := 'posted';
    exception when others then
        refusal := sqlerrm;
    end;
    perform expect(refusal like '%is incomplete%' and test_posts_sent() = sent_before,
        'an incomplete group is refused before anything is sent');
end;
$$;

-- Roles that could read the view's payloads before (pg_read_all_data members, such as
-- Supabase's supabase_read_only_user) still can.
do $$ begin create role test_reader login; exception when duplicate_object then null; end $$;
grant pg_read_all_data to test_reader;
grant test_reader to postgres;   -- postgres isn't a superuser in this image, so it needs this to set role
do $$
declare
    sub uuid := (select sub_id from test_case where name = 'complete group, several locations and cells');
    expected text := private.fcc_submission_payload(sub)::text;
    seen text;
begin
    set local role test_reader;
    select v.data::text into seen from private.fcc_submissions_view v where v.submission_id = sub;
    reset role;
    perform expect(seen = expected, 'a pg_read_all_data member can still read payloads through the view');
end;
$$;

-- An unprivileged role that may build payloads (as readonly_user may) must not be able to
-- run its own code as the function's owner by shadowing a type with a temporary object.
do $$ begin create role test_caller login; exception when duplicate_object then null; end $$;
grant test_caller to postgres;   -- postgres isn't a superuser in this image, so it needs this to set role
grant usage on schema private to test_caller;
grant execute on function private.fcc_submission_payload(uuid) to test_caller;
-- Latency only, so the payload falls back to an empty object for the throughput tests, the
-- one place it names a type ('{}'::jsonb).
do $$
begin
    perform test_latency(test_measurement(test_submission('latency only'), 'latency', '2026-07-11 08:00:00-07'));
end;
$$;
select current_database() as test_db,
       (select sub_id from test_case where name = 'latency only') as shadow_sub \gset
\c :test_db
set role test_caller;
\i /db_tests/lib/shadow_types.sql
select private.fcc_submission_payload(:'shadow_sub') is not null as built \gset
select pg_temp.test_shadow_ran_as_owner() as escalated \gset
reset role;
\c :test_db
select expect(:'built' and not :'escalated',
    'a caller''s temporary types do not run as the owner of fcc_submission_payload') as ok \gset
