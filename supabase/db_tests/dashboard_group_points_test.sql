-- Tests for precomputing the dashboard's per-group summary (202611010001): rpc_map_points and
-- rpc_group_summaries return exactly what their 202610220101 versions returned, in the same
-- order, for every filter; they read a view that only a refresh brings up to date; and they
-- still refuse requests without the dashboard secret. Run by supabase/db_tests/run.sh in a
-- throwaway container.
\set ON_ERROR_STOP on
set client_min_messages = notice;

-- Minimal copies of the tables the RPCs read (columns as in the live schema).
create schema if not exists private;

create table public.measurements (
    id uuid primary key,
    group_id uuid,
    provider text,
    type varchar not null,
    "timestamp" timestamptz not null default now()
);

create table public.locations (
    measurement_id uuid,
    "timestamp" timestamptz not null default now(),
    lat double precision,
    lon double precision,
    accuracy double precision
);

create table public.cells (
    measurement_id uuid,
    "timestamp" timestamptz not null default now(),
    network_generation varchar,
    signal_strength numeric,
    rssi numeric,
    rsrp numeric,
    rsrq numeric,
    sinr numeric
);

create table public.upload_download_data (
    measurement_id uuid,
    duration numeric,
    bytes numeric,
    bytes_per_sec numeric,
    application_bytes_per_sec numeric
);

create table public.latency_data (
    measurement_id uuid,
    rtt numeric,
    jitter numeric,
    sent numeric,
    received numeric
);

create table private.dashboard_settings (
    id boolean default true not null primary key,
    shared_secret text not null
);
insert into private.dashboard_settings (shared_secret) values ('test-dashboard-secret');

-- Copy of the live definition.
create function private.is_dashboard_request() returns boolean
language sql stable security definer as $$
  select coalesce(
    (current_setting('request.headers', true)::json->>'x-dashboard-secret')
      = (select shared_secret from private.dashboard_settings limit 1),
    false
  )
$$;

do $$ begin create role anon nologin; exception when duplicate_object then null; end $$;
do $$ begin create role authenticated nologin; exception when duplicate_object then null; end $$;

-- Pass/fail helper, as in db_tests/lib/fcc_schema.sql.
create function expect(ok boolean, description text) returns void
language plpgsql as $$
begin
    if ok is distinct from true then
        raise exception 'FAILED: %', description;
    end if;
    raise notice 'ok - %', description;
end;
$$;

-- 40 groups, alternating between Atlanta and Seattle, an hour apart. Each has a download, an
-- upload and a latency test; each test has two location fixes and two cell readings, of which
-- the later one counts. Providers, generations and speeds vary with the group number; every
-- fifth download only has bytes and duration (microseconds). One provider is missing, which
-- the summary reports as 'Unknown'. Group 41 has no location and is never listed.
create function test_uuid(kind text, g integer, t text default '') returns uuid
language sql immutable as $$ select md5(kind || g || t)::uuid $$;

insert into public.measurements (id, group_id, provider, type, "timestamp")
select test_uuid('m', g, t), test_uuid('g', g),
       case when g = 7 then null else (array['Carrier A', 'Carrier B', 'Carrier C'])[1 + g % 3] end,
       t, '2026-10-01 00:00+00'::timestamptz + make_interval(hours => g) + make_interval(secs => k)
from generate_series(1, 41) g, unnest(array['download', 'upload', 'latency']) with ordinality as x(t, k);

insert into public.locations (measurement_id, "timestamp", lat, lon, accuracy)
select test_uuid('m', g, t),
       '2026-10-01 00:00+00'::timestamptz + make_interval(hours => g) + make_interval(secs => k * 10 + fix),
       case when g % 2 = 0 then 33.75 else 47.60 end + g * 0.001 + fix * 0.0001,
       case when g % 2 = 0 then -84.39 else -122.33 end - g * 0.001 - fix * 0.0001,
       5 + fix + k
from generate_series(1, 40) g, unnest(array['download', 'upload', 'latency']) with ordinality as x(t, k), generate_series(1, 2) fix;

insert into public.cells (measurement_id, "timestamp", network_generation, rsrp)
select test_uuid('m', g, t),
       '2026-10-01 00:00+00'::timestamptz + make_interval(hours => g) + make_interval(secs => k * 10 + reading),
       case when reading = 1 then 'HSPA' else (array['NR', 'LTE', 'HSPA'])[1 + g % 3] end,
       -90 - reading
from generate_series(1, 41) g, unnest(array['download', 'upload', 'latency']) with ordinality as x(t, k), generate_series(1, 2) reading;

insert into public.upload_download_data (measurement_id, duration, bytes, bytes_per_sec)
select test_uuid('m', g, t),
       case when g % 5 = 0 then 10000000 end,
       case when g % 5 = 0 then 12500000 * g end,
       case when g % 5 = 0 then null else 125000 * g * (case when t = 'upload' then 1 else 4 end) end
from generate_series(1, 41) g, unnest(array['download', 'upload']) as t;

insert into public.latency_data (measurement_id, rtt, jitter, sent, received)
select test_uuid('m', g, 'latency'), 20000 + g * 1000, 1000 + g * 100, 10, 10 - g % 3
from generate_series(1, 41) g;

-- Every function call and filter compared, with the results in order.
create table test_results (phase text, name text, result jsonb, primary key (phase, name));

create function test_capture(p_phase text) returns void
language plpgsql as $$
begin
    insert into test_results
    select p_phase, c.name, (select jsonb_agg(r order by n) from unnest(c.rows) with ordinality as u(r, n))
    from (values
        ('map world',        array(select public.rpc_map_points(-180, -90, 180, 90))),
        ('map atlanta',      array(select public.rpc_map_points(-84.5, 33.6, -84.2, 33.9))),
        ('map seattle',      array(select public.rpc_map_points(-122.5, 47.5, -122.2, 47.8))),
        ('map time window',  array(select public.rpc_map_points(-180, -90, 180, 90, t_from => '2026-10-01 10:00+00', t_to => '2026-10-02 06:00+00'))),
        ('map providers',    array(select public.rpc_map_points(-180, -90, 180, 90, providers => array['Carrier A', 'Unknown']))),
        ('map 5G',           array(select public.rpc_map_points(-180, -90, 180, 90, conn => array['5G']))),
        ('map 4G or other',  array(select public.rpc_map_points(-180, -90, 180, 90, conn => array['4G', 'Other']))),
        ('map download',     array(select public.rpc_map_points(-180, -90, 180, 90, dl_min => 20, dl_max => 80))),
        ('map upload',       array(select public.rpc_map_points(-180, -90, 180, 90, ul_min => 10))),
        ('map latency',      array(select public.rpc_map_points(-180, -90, 180, 90, lat_min => 30, lat_max => 50))),
        ('map limit 5',      array(select public.rpc_map_points(-180, -90, 180, 90, lim => 5))),
        ('map limit 0',      array(select public.rpc_map_points(-180, -90, 180, 90, lim => 0))),
        ('map nothing',      array(select public.rpc_map_points(0, 0, 1, 1))),
        ('summaries world',  array(select public.rpc_group_summaries(-180, -90, 180, 90))),
        ('summaries atlanta', array(select public.rpc_group_summaries(-84.5, 33.6, -84.2, 33.9, providers => array['Carrier B']))),
        ('summaries limit 3', array(select public.rpc_group_summaries(-180, -90, 180, 90, lim => 3)))
    ) as c(name, rows);
end;
$$;

\i /migrations/202610220101_fix_dashboard_rpcs.sql

select set_config('request.headers', '{"x-dashboard-secret": "test-dashboard-secret"}', false) as ignored \gset
do $$ begin perform test_capture('before'); end $$;

\i /migrations/202611010001_precompute_dashboard_group_points.sql
-- Applying it again changes nothing.
\i /migrations/202611010001_precompute_dashboard_group_points.sql

do $$ begin perform test_capture('after'); end $$;

do $$
declare
    differing text;
begin
    perform expect(jsonb_array_length((select result from test_results where phase = 'before' and name = 'map world')) = 40,
                   'the fixtures put 40 groups on the map before the change (the one without a location is left out)');
    perform expect((select count(*) from test_results where phase = 'before' and result is not null
                        and jsonb_array_length(result) between 1 and 39) >= 10,
                   'most filters select a strict, non-empty subset');
    select string_agg(b.name, ', ') into differing
    from test_results b join test_results a on a.name = b.name and a.phase = 'after'
    where b.phase = 'before' and a.result is distinct from b.result;
    perform expect(differing is null, 'every call returns exactly what it did before, in the same order (differ: '
                                      || coalesce(differing, 'none') || ')');
end;
$$;

-- The view only changes on a refresh.
insert into public.measurements (id, group_id, provider, type, "timestamp")
values (test_uuid('m', 99, 'download'), test_uuid('g', 99), 'Carrier New', 'download', '2026-11-01 00:00+00');
insert into public.locations (measurement_id, "timestamp", lat, lon, accuracy)
values (test_uuid('m', 99, 'download'), '2026-11-01 00:00+00', 33.8, -84.4, 5);

do $$
begin
    perform expect(not exists (select 1 from public.rpc_map_points(-180, -90, 180, 90) r
                               where r ->> 'group_id' = test_uuid('g', 99)::text),
                   'a new group is not on the map before the refresh');
end;
$$;

refresh materialized view concurrently private.dashboard_group_points;

do $$
begin
    perform expect((select r ->> 'group_id' from public.rpc_map_points(-180, -90, 180, 90) r limit 1) = test_uuid('g', 99)::text,
                   'after the refresh it is, first as the newest');
    perform expect(not has_table_privilege('anon', 'private.dashboard_group_points', 'select')
                       and not has_table_privilege('authenticated', 'private.dashboard_group_points', 'select'),
                   'the API roles cannot read the view directly');
end;
$$;

-- Groups with the same timestamp come in group_id order, the same on every call.
insert into public.measurements (id, group_id, provider, type, "timestamp")
select test_uuid('m', g, 'download'), test_uuid('g', g), 'Carrier Tie', 'download', '2026-11-02 00:00+00'
from generate_series(100, 104) g;
insert into public.locations (measurement_id, "timestamp", lat, lon, accuracy)
select test_uuid('m', g, 'download'), '2026-11-02 00:00+00', 33.8, -84.4, 5
from generate_series(100, 104) g;
refresh materialized view concurrently private.dashboard_group_points;

do $$
begin
    perform expect((select array_agg(r ->> 'group_id') from public.rpc_map_points(-180, -90, 180, 90, lim => 5) r)
                       = (select array_agg(test_uuid('g', g)::text order by test_uuid('g', g)) from generate_series(100, 104) g),
                   'groups with the same timestamp are ordered by group_id');
end;
$$;

-- Without the dashboard secret, both still refuse.
select set_config('request.headers', '{}', false) as ignored \gset
do $$
declare
    refused integer := 0;
begin
    begin
        perform public.rpc_map_points(-180, -90, 180, 90);
    exception when insufficient_privilege then
        refused := refused + 1;
    end;
    begin
        perform public.rpc_group_summaries(-180, -90, 180, 90);
    exception when insufficient_privilege then
        refused := refused + 1;
    end;
    perform expect(refused = 2, 'both refuse a request without the dashboard secret');
end;
$$;

-- A caller's temporary types must not run as the RPCs' owner.
do $$ begin create role test_caller login; exception when duplicate_object then null; end $$;
grant test_caller to postgres;   -- postgres isn't a superuser in this image, so it needs this to set role
select current_database() as test_db \gset
\c :test_db
set role test_caller;
\i /db_tests/lib/shadow_types.sql
select set_config('request.headers', '{"x-dashboard-secret": "test-dashboard-secret"}', false) as ignored \gset
select (select count(*) from public.rpc_group_summaries(-180, -90, 180, 90)) > 0
       and (select count(*) from public.rpc_map_points(-180, -90, 180, 90)) > 0
       and not pg_temp.test_shadow_ran_as_owner() as shadow_ok \gset
reset role;
\c :test_db
set test.shadow_ok = :'shadow_ok';
do $$
begin
    perform expect(current_setting('test.shadow_ok') = 't',
                   'a caller''s temporary types do not run as the owner of the dashboard RPCs');
end;
$$;

\echo 'dashboard_group_points_test: all checks passed'
