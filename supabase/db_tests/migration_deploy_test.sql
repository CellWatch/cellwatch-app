-- Tests how 202610210001, 202610210101 and 202610210103 behave while the live project is in
-- use, run by supabase/db_tests/run.sh in a throwaway container.
--
-- Each migration is applied while another session holds a lock the migration needs (as a
-- running cron job or dashboard query would) and an upload arrives meanwhile. The upload must
-- not have to wait for the migration, and the migration must apply once the lock is free.
-- The other sessions are psql processes started in the background with \!; they write what
-- they did, and when, to test_log.
--
-- Also: a leftover invalid index from a failed CREATE INDEX CONCURRENTLY (which
-- 202610210001 suggests running by hand on a big table) is rebuilt, not kept.
\set ON_ERROR_STOP on
set client_min_messages = notice;

\i /db_tests/lib/fcc_schema.sql

-- What 202610210001 changes: the live helpers (stand-ins; their bodies don't matter here)
-- and the policies it alters.
create function private.is_dashboard_request() returns boolean
language sql stable security definer as $$ select false $$;
create function private.verified_device_id_or_null() returns uuid
language sql security definer as $$ select null::uuid $$;
create policy "Allow viewing own measurements" on public.measurements for select using (true);
create policy "Allow viewing latency (dashboard or own)" on public.latency_data for select using (true);
create policy "Allow viewing locations (dashboard or own)" on public.locations for select using (true);
create policy "Allow viewing ud (dashboard or own)" on public.upload_download_data for select using (true);
create policy "dashboard can select cells" on public.cells for select using (true);
create policy "dashboard can select latency_data" on public.latency_data for select using (true);
create policy "dashboard can select locations" on public.locations for select using (true);

create table test_log (event text primary key, at timestamptz not null);

-- Waits until another session holds a lock on rel.
create function test_wait_for_lock(rel regclass) returns void
language plpgsql as $$
begin
    for i in 1 .. 200 loop
        if exists (
            select 1
            from pg_locks l
            where l.database = (select oid from pg_database where datname = current_database())
              and l.relation = rel
              and l.granted
              and l.pid <> pg_backend_pid()
        ) then
            return;
        end if;
        perform pg_sleep(0.05);
    end loop;
    raise exception 'the other session never took its lock on %', rel;
end;
$$;

-- Waits for another session to log an event, and returns when it happened.
create function test_wait_for(wanted text) returns timestamptz
language plpgsql as $$
declare
    happened timestamptz;
begin
    for i in 1 .. 400 loop
        select l.at into happened from test_log l where l.event = wanted;
        if found then
            return happened;
        end if;
        perform pg_sleep(0.05);
    end loop;
    raise exception 'the other session never logged "%"', wanted;
end;
$$;

select current_database() as test_db \gset
\setenv TEST_DB :test_db

-- Leftover invalid indexes with the names the migrations use. A unique concurrent build on
-- duplicate values fails and leaves one behind.
insert into public.measurements (device_id, group_id, type)
select '00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-0000000000a1', 'latency'
from generate_series(1, 2);
\echo 'Next: two failed concurrent index builds ("could not create unique index" is expected).'
\set ON_ERROR_STOP off
create unique index concurrently measurements_device_id_idx on public.measurements (device_id);
create unique index concurrently measurements_group_id_idx on public.measurements (group_id);
\set ON_ERROR_STOP on
select expect(
    (select count(*) from pg_index
     where indexrelid in (to_regclass('public.measurements_device_id_idx'), to_regclass('public.measurements_group_id_idx'))
       and not indisvalid) = 2,
    'setup: both index names are taken by invalid leftovers') as ok \gset

-- 202610210001 needs all five upload tables. A dashboard query holds cells for 3 seconds; an
-- upload arrives after 1.
\! psql -U postgres -d "$TEST_DB" -X -q -c "begin; lock table public.cells in access share mode; select pg_sleep(3); insert into test_log values ('0001 reader done', clock_timestamp()); commit;" >/dev/null 2>&1 &
select test_wait_for_lock('public.cells') as ignored \gset
\! psql -U postgres -d "$TEST_DB" -X -q -c "select pg_sleep(1)" -c "insert into public.measurements (group_id, type) values (gen_random_uuid(), 'latency')" -c "insert into test_log values ('0001 upload done', clock_timestamp())" >/dev/null 2>&1 &
\i /migrations/202610210001_fast_upload_rls.sql
select clock_timestamp() as migrated_at \gset
set test.migrated_at = :'migrated_at';

do $$
declare
    reader_done timestamptz := test_wait_for('0001 reader done');
    upload_done timestamptz := test_wait_for('0001 upload done');
begin
    perform expect(current_setting('test.migrated_at')::timestamptz > reader_done,
        '0001: the migration waited for the dashboard query');
    perform expect(upload_done < reader_done,
        '0001: an upload meanwhile did not wait for the migration');
    perform expect(
        (select qual like '%SELECT private.verified_device_id_or_null()%'
         from pg_policies where policyname = 'Allow viewing own measurements'),
        '0001: and the migration applied');
    perform expect(
        (select indisvalid and not indisunique from pg_index
         where indexrelid = to_regclass('public.measurements_device_id_idx')),
        '0001: the invalid leftover index was rebuilt');
end;
$$;

-- 202610210101 replaces private.fcc_submissions_view, which the retry job reads, and builds
-- an index on measurements. A retry run reads the view for 2 seconds; an upload arrives
-- after 1.
\! psql -U postgres -d "$TEST_DB" -X -q -c "begin; select count(*) from private.fcc_submissions_view; select pg_sleep(2); insert into test_log values ('0101 reader done', clock_timestamp()); commit;" >/dev/null 2>&1 &
select test_wait_for_lock('private.fcc_submissions_view') as ignored \gset
\! psql -U postgres -d "$TEST_DB" -X -q -c "select pg_sleep(1)" -c "insert into public.measurements (group_id, type) values (gen_random_uuid(), 'latency')" -c "insert into test_log values ('0101 upload done', clock_timestamp())" >/dev/null 2>&1 &
\i /migrations/202610210101_build_fcc_payload_per_submission.sql
select clock_timestamp() as migrated_at \gset
set test.migrated_at = :'migrated_at';

do $$
declare
    reader_done timestamptz := test_wait_for('0101 reader done');
    upload_done timestamptz := test_wait_for('0101 upload done');
begin
    perform expect(current_setting('test.migrated_at')::timestamptz > reader_done,
        '0101: the migration waited for the retry run');
    perform expect(upload_done < reader_done,
        '0101: an upload meanwhile did not wait for the migration');
    perform expect(to_regprocedure('private.fcc_submission_payload(uuid)') is not null,
        '0101: and the migration applied');
    perform expect(
        (select indisvalid and not indisunique from pg_index
         where indexrelid = to_regclass('public.measurements_group_id_idx')),
        '0101: the invalid leftover index was rebuilt');
end;
$$;

-- 202610210103 changes the queue's state check. A retry run (as the job ran before this
-- migration, in one transaction) holds the queue for 3 seconds; the app uploads an FCC
-- submission after 1, which the insert trigger queues.
\! psql -U postgres -d "$TEST_DB" -X -q -c "begin; lock table private.fcc_submission_retry_queue in row exclusive mode; select pg_sleep(3); insert into test_log values ('0103 reader done', clock_timestamp()); commit;" >/dev/null 2>&1 &
select test_wait_for_lock('private.fcc_submission_retry_queue') as ignored \gset
\! psql -U postgres -d "$TEST_DB" -X -q -c "select pg_sleep(1)" -c "insert into public.fcc_submissions (id) values (gen_random_uuid())" -c "insert into test_log values ('0103 upload done', clock_timestamp())" >/dev/null 2>&1 &
\i /migrations/202610210103_fcc_retry_queue_processing.sql
select clock_timestamp() as migrated_at \gset
set test.migrated_at = :'migrated_at';

do $$
declare
    reader_done timestamptz := test_wait_for('0103 reader done');
    upload_done timestamptz := test_wait_for('0103 upload done');
begin
    perform expect(current_setting('test.migrated_at')::timestamptz > reader_done,
        '0103: the migration waited for the retry run');
    perform expect(upload_done < reader_done,
        '0103: an FCC submission upload meanwhile did not wait for the migration');
    perform expect(
        (select count(*) from private.fcc_submission_retry_queue) = 1,
        '0103: and it was queued');
    perform expect(to_regprocedure('private.process_next_fcc_submission_retry()') is not null,
        '0103: and the migration applied');
end;
$$;
