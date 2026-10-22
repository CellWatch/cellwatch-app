-- Tests for the map dashboard RPCs (rpc_group_detail, rpc_group_summaries, rpc_map_points),
-- run by supabase/db_tests/run.sh in a throwaway container.
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

-- Two groups in the same place. Group B is newer and slower, so any mix-up between the groups
-- shows up in the results. RTT and jitter are stored in microseconds, as the app sends them.
insert into public.measurements values
    ('a0000000-0000-0000-0000-00000000000d', 'aaaaaaaa-0000-0000-0000-000000000000', 'Carrier A', 'download', '2026-10-01 12:00+00'),
    ('a0000000-0000-0000-0000-00000000000e', 'aaaaaaaa-0000-0000-0000-000000000000', 'Carrier A', 'latency',  '2026-10-01 12:00+00'),
    ('b0000000-0000-0000-0000-00000000000d', 'bbbbbbbb-0000-0000-0000-000000000000', 'Carrier B', 'download', '2026-10-02 12:00+00'),
    ('b0000000-0000-0000-0000-00000000000e', 'bbbbbbbb-0000-0000-0000-000000000000', 'Carrier B', 'latency',  '2026-10-02 12:00+00');
insert into public.locations (measurement_id, lat, lon, accuracy) values
    ('a0000000-0000-0000-0000-00000000000d', 33.77, -84.39, 5),
    ('a0000000-0000-0000-0000-00000000000e', 33.77, -84.39, 5),
    ('b0000000-0000-0000-0000-00000000000d', 33.78, -84.40, 5),
    ('b0000000-0000-0000-0000-00000000000e', 33.78, -84.40, 5);
insert into public.upload_download_data (measurement_id, bytes_per_sec) values
    ('a0000000-0000-0000-0000-00000000000d', 12500000),   -- 100 Mbit/s
    ('b0000000-0000-0000-0000-00000000000d', 1250000);    -- 10 Mbit/s
insert into public.latency_data (measurement_id, rtt, jitter, sent, received) values
    ('a0000000-0000-0000-0000-00000000000e', 40000, 2000, 10, 10),   -- 40 ms, 2 ms
    ('b0000000-0000-0000-0000-00000000000e', 90000, 5000, 10, 10);   -- 90 ms, 5 ms

\i /migrations/202610220101_fix_dashboard_rpcs.sql

select set_config('request.headers', '{"x-dashboard-secret": "test-dashboard-secret"}', false);

-- rpc_group_detail returns only the requested group.
do $$
declare
    d jsonb := public.rpc_group_detail('aaaaaaaa-0000-0000-0000-000000000000');
begin
    if d->>'group_id' <> 'aaaaaaaa-0000-0000-0000-000000000000'
        or d->>'provider' <> 'Carrier A'
        or (d->'tests'->'download'->>'mbps')::numeric <> 100
        or (d->'location'->>'lat')::numeric <> 33.77
    then
        raise exception 'rpc_group_detail mixed in another group: %', d;
    end if;

    d := public.rpc_group_detail('cccccccc-0000-0000-0000-000000000000');
    if d->>'provider' <> 'Unknown' or d->'tests'->'download'->>'mbps' is not null then
        raise exception 'rpc_group_detail for an unknown group returned data: %', d;
    end if;
    raise notice 'ok: rpc_group_detail returns only the requested group';
end $$;

-- Without the dashboard secret, none of the RPCs return anything.
do $$
declare
    headers text;
    call text;
    refused boolean;
begin
    foreach headers in array array['{}', '{"x-dashboard-secret": "wrong"}', '{"x-dashboard-secret": ""}'] loop
        perform set_config('request.headers', headers, false);
        foreach call in array array[
            $c$select public.rpc_group_detail('aaaaaaaa-0000-0000-0000-000000000000')$c$,
            $c$select count(*) from public.rpc_group_summaries(-180, -90, 180, 90)$c$,
            $c$select count(*) from public.rpc_map_points(-180, -90, 180, 90)$c$
        ] loop
            refused := false;
            begin
                execute call;
            exception when insufficient_privilege then
                refused := true;
            end;
            if not refused then
                raise exception 'ran without the dashboard secret (headers %): %', headers, call;
            end if;
        end loop;
    end loop;

    raise notice 'ok: dashboard RPCs refuse requests without the dashboard secret';
end $$;

-- With the secret, the list RPCs return both groups.
select set_config('request.headers', '{"x-dashboard-secret": "test-dashboard-secret"}', false);
do $$
begin
    if (select count(*) from public.rpc_group_summaries(-180, -90, 180, 90)) <> 2
        or (select count(*) from public.rpc_map_points(-180, -90, 180, 90)) <> 2
    then
        raise exception 'dashboard RPCs did not return both groups with the dashboard secret';
    end if;
    raise notice 'ok: dashboard RPCs work with the dashboard secret';
end $$;

-- All three report latency in milliseconds (stored values are microseconds).
do $$
declare
    d jsonb := public.rpc_group_detail('aaaaaaaa-0000-0000-0000-000000000000');
    s jsonb;
    p jsonb;
begin
    select x into s from public.rpc_group_summaries(-180, -90, 180, 90) x
        where x->>'group_id' = 'aaaaaaaa-0000-0000-0000-000000000000';
    select x into p from public.rpc_map_points(-180, -90, 180, 90) x
        where x->>'group_id' = 'aaaaaaaa-0000-0000-0000-000000000000';

    if (d->'tests'->'latency'->>'rtt_ms')::numeric <> 40
        or (d->'tests'->'latency'->>'jitter_ms')::numeric <> 2
        or (s->>'ping_ms')::numeric <> 40
        or (s->>'jitter_ms')::numeric <> 2
        or (p->'stats'->>'ping_ms')::numeric <> 40
        or (p->'stats'->>'jitter_ms')::numeric <> 2
    then
        raise exception 'latency is not in milliseconds: detail %, summary %, point %', d, s, p;
    end if;

    -- The latency filter is in milliseconds too: only group A (40 ms) is at or under 50 ms.
    if (select count(*) from public.rpc_group_summaries(-180, -90, 180, 90, lat_max => 50)) <> 1
        or (select count(*) from public.rpc_map_points(-180, -90, 180, 90, lat_max => 50)) <> 1
    then
        raise exception 'lat_max is not applied in milliseconds';
    end if;
    raise notice 'ok: dashboard RPCs report latency in milliseconds';
end $$;
