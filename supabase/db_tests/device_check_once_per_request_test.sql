-- Tests for 202611010004_device_check_once_per_request.sql, run by supabase/db_tests/run.sh in a
-- throwaway container. Loads copies of the live upload tables, row level security policies,
-- triggers and device checks, replays a test's upload the way PostgREST runs it and counts how
-- often bcrypt (crypt()) runs in each request, applies the migration, and checks that:
--   * each request runs bcrypt once, however many locations and cells it has;
--   * every caller can read and write exactly what it could before, with the same errors;
--   * a remembered check is never used for another device id, another secret, a replaced
--     secret or a later transaction, and the app's roles can't set or forge one.
\set ON_ERROR_STOP on
set client_min_messages = notice;
-- Count the calls of every function, crypt() included (pg_stat_get_xact_function_calls()).
set track_functions = 'all';
-- PostgREST requests run with this search_path (extra_search_path in supabase/config.toml).
set search_path = public, extensions;

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
create schema if not exists private;

do $$ begin create role anon nologin; exception when duplicate_object then null; end $$;
do $$ begin create role authenticated nologin; exception when duplicate_object then null; end $$;
do $$ begin create role service_role nologin; exception when duplicate_object then null; end $$;
do $$ begin create role authenticator noinherit login; exception when duplicate_object then null; end $$;
grant usage on schema public to anon, authenticated;

-- Stand-ins for readonly_user, which gets SELECT on every new table in private by default on
-- the live project, and for a role that reads every table (pg_read_all_data).
do $$ begin create role test_private_reader nologin; exception when duplicate_object then null; end $$;
do $$ begin create role test_all_reader nologin; exception when duplicate_object then null; end $$;
grant test_private_reader, test_all_reader to postgres;   -- postgres isn't a superuser in this image, so it needs this to set role
grant pg_read_all_data to test_all_reader;
grant usage on schema private to test_private_reader;
alter default privileges for role postgres in schema private grant select on tables to test_private_reader;

-- Copies of the upload tables, as in supabase/full_schema.sql.
create type public.connection_type as enum ('NONE', 'WIFI', 'CELLULAR');

create table public.measurements (
    id uuid default gen_random_uuid() not null primary key,
    group_id uuid,
    campaign_id uuid,
    session_id uuid,
    device_id uuid,
    device_manufacturer text,
    device_model text,
    device_os_name text,
    device_os_version text,
    app_name text,
    provider text,
    type character varying not null,
    "timestamp" timestamp with time zone default now() not null,
    duration numeric,
    scheduled boolean,
    success boolean,
    carrier_aggregation boolean,
    network_connected boolean,
    network_available boolean,
    network_roaming boolean,
    extra_data jsonb,
    created_on timestamp with time zone default now() not null,
    updated_on timestamp with time zone default now() not null,
    sim_mobile_country_code text,
    sim_mobile_network_code text,
    net_mobile_country_code text,
    net_mobile_network_code text,
    connection_type public.connection_type,
    cellular_data_enabled boolean,
    app_version text,
    constraint type_in_scope check (type::text = any (array['download', 'upload', 'latency']))
);

create table public.upload_download_data (
    id uuid default gen_random_uuid() not null primary key,
    measurement_id uuid unique references public.measurements (id) on delete cascade,
    warmup_duration numeric,
    warmup_bytes numeric,
    duration numeric,
    bytes numeric,
    servers text[],
    created_on timestamp with time zone default now() not null,
    updated_on timestamp with time zone default now() not null,
    application_bytes numeric,
    bytes_per_sec numeric,
    application_bytes_per_sec numeric
);

create table public.latency_data (
    id uuid default gen_random_uuid() not null primary key,
    measurement_id uuid unique references public.measurements (id) on delete cascade,
    rtt numeric,
    jitter numeric,
    sent numeric,
    received numeric,
    servers text[],
    created_on timestamp with time zone default now() not null,
    updated_on timestamp with time zone default now() not null
);

create table public.locations (
    id uuid default gen_random_uuid() not null primary key,
    "timestamp" timestamp with time zone default now() not null,
    lat double precision,
    lon double precision,
    accuracy double precision,
    speed double precision,
    speed_accuracy double precision,
    heading double precision,
    measurement_id uuid references public.measurements (id) on update cascade on delete cascade,
    created_on timestamp with time zone default now() not null,
    updated_on timestamp with time zone default now() not null
);

create table public.cells (
    id uuid default gen_random_uuid() not null primary key,
    "timestamp" timestamp with time zone default now() not null,
    cell_id numeric,
    physical_cell_id numeric,
    cell_connection numeric,
    network_generation character varying,
    network_subtype character varying,
    signal_strength numeric,
    rssi numeric,
    rsrp numeric,
    rsrq numeric,
    sinr numeric,
    csi_rsrp numeric,
    csi_rsrq numeric,
    csi_sinr numeric,
    cqi numeric,
    spectrum_band text,
    spectrum_bandwidth double precision,
    arfcn numeric,
    measurement_id uuid references public.measurements (id) on update cascade on delete cascade,
    created_on timestamp with time zone default now() not null,
    updated_on timestamp with time zone default now() not null
);

create table public.fcc_submissions (
    id uuid default gen_random_uuid() not null primary key,
    contact_name text,
    contact_email text,
    contact_phone text,
    device_timestamp timestamp with time zone,
    server_timestamp timestamp with time zone default now(),
    source_ip character varying,
    source_port integer,
    device_imei character varying,
    device_tac character varying,
    sim_country_code character varying,
    sim_network_code character varying,
    net_country_code character varying,
    net_network_code character varying,
    in_vehicle boolean,
    external_antenna boolean,
    created_on timestamp with time zone default now() not null,
    updated_on timestamp with time zone default now() not null,
    submission jsonb,
    submitted boolean default false,
    submitted_on timestamp with time zone,
    device_manufacturer text,
    device_model text,
    device_os text,
    app_name text,
    app_version text,
    provider text,
    device_type text,
    device_id uuid,
    submission_response text
);

create index cells_measurement_id_idx on public.cells using btree (measurement_id);
create index locations_measurement_id_idx on public.locations using btree (measurement_id);
create index measurements_device_id_idx on public.measurements (device_id);

create table private.dashboard_settings (
    id boolean default true not null primary key,
    shared_secret text not null
);

create table private.devices (
    id uuid not null primary key,
    hashed_secret text not null
);

-- Only the columns the trigger below fills.
create table private.fcc_submission_retry_queue (
    submission_id uuid not null primary key references public.fcc_submissions (id) on delete cascade,
    fcc_env text default 'prod' not null,
    state text default 'pending' not null,
    next_attempt_at timestamp with time zone default now() not null,
    updated_on timestamp with time zone default now() not null
);

grant select, insert, references, delete, trigger, update
    on public.measurements, public.upload_download_data, public.latency_data,
       public.locations, public.cells, public.fcc_submissions
    to anon, authenticated;

-- The live insert triggers (the update triggers don't matter here).
create function public.force_created_on() returns trigger
language plpgsql as $$
begin
    NEW.created_on = now();
    NEW.updated_on = now();
    return new;
end
$$;

create function public.fix_jitter() returns trigger
language plpgsql as $$
begin
    new.jitter = case
        when (select app_version from measurements where id = new.measurement_id) is null
            or (select app_version from measurements where id = new.measurement_id) in ('1.0.0-alpha.4', '1.0.0-alpha.3', '1.0.0-alpha.2')
            then sqrt(new.jitter)
        else new.jitter
    end;
    return new;
end
$$;

create function public.get_header(item text) returns text
language sql stable as $$
    SELECT (current_setting('request.headers', true)::json)->>item
$$;

create function public.fcc_submission_update_source_ip() returns trigger
language plpgsql as $$
    BEGIN
        NEW.source_ip = SPLIT_PART(get_header('x-forwarded-for') || ',', ',', 1);
        RETURN NEW;
    END;
$$;

create function private.auto_post_fcc_submission() returns trigger
language plpgsql security definer
set search_path to 'public', 'private', 'extensions'
as $$
begin
    if coalesce(new.submitted, false) = false then
        insert into private.fcc_submission_retry_queue (
            submission_id,
            fcc_env,
            state,
            next_attempt_at
        )
        values (
            new.id,
            'prod',
            'pending',
            now()
        )
        on conflict (submission_id) do update
        set
            state = 'pending',
            next_attempt_at = least(
                private.fcc_submission_retry_queue.next_attempt_at,
                now()
            ),
            updated_on = now();
    end if;

    return new;
end;
$$;

create trigger _200_cell_force_created_on before insert on public.cells
    for each row when (new.created_on is null) execute function public.force_created_on();
create trigger _200_fcc_submission_force_created_on before insert on public.fcc_submissions
    for each row when (new.created_on is null) execute function public.force_created_on();
create trigger _200_latency_data_force_created_on before insert on public.latency_data
    for each row when (new.created_on is null) execute function public.force_created_on();
create trigger _200_location_force_created_on before insert on public.locations
    for each row when (new.created_on is null) execute function public.force_created_on();
create trigger _200_measurement_force_created_on before insert on public.measurements
    for each row when (new.created_on is null) execute function public.force_created_on();
create trigger _200_upload_download_data_force_created_on before insert on public.upload_download_data
    for each row when (new.created_on is null) execute function public.force_created_on();
create trigger _300_fcc_submission_update_source_ip before insert on public.fcc_submissions
    for each row when (new.source_ip is null) execute function public.fcc_submission_update_source_ip();
create trigger fix_jitter_pre_100alpha5 before insert on public.latency_data
    for each row execute function public.fix_jitter();
create trigger trg_auto_post_fcc_submission after insert on public.fcc_submissions
    for each row execute function private.auto_post_fcc_submission();

-- The live helper functions, as in supabase/full_schema.sql.
create function private.is_dashboard_request() returns boolean
language sql stable security definer
set search_path to 'public', 'private', 'extensions', 'pg_temp'
as $$
  select coalesce(
    (current_setting('request.headers', true)::json->>'x-dashboard-secret')
      = (select shared_secret from private.dashboard_settings limit 1),
    false
  )
$$;

create function private.verified_device_id() returns uuid
language plpgsql security definer
set search_path to 'public', 'private', 'extensions', 'pg_temp'
as $$
declare
    device_id text;
    device_secret text;
    hashed text;
begin
    device_id := current_setting('request.headers', true)::json->>'x-device-id';
    device_secret := current_setting('request.headers', true)::json->>'x-device-secret';

    if device_id is null or device_id = '' then
        raise exception 'missing x-device-id header';
    end if;

    if device_secret is null or device_secret = '' then
        raise exception 'missing x-device-secret header';
    end if;

    -- Changed: wait for a secret replacement in progress, and hold one off until this ends.
    perform pg_advisory_xact_lock_shared(hashtextextended('cellwatch device ' || uuid(device_id)::text, 0));

    select hashed_secret into hashed from private.devices where id = uuid(device_id);
    if not found then
        raise exception 'device id % is not registered', device_id;
    end if;

    if crypt(device_secret, hashed) <> hashed then
        raise exception 'incorrect device secret';
    end if;

    return uuid(device_id);
end;
$$;

create function private.verified_device_id_or_null() returns uuid
language plpgsql security definer
set search_path to 'public', 'private', 'extensions', 'pg_temp'
as $$
declare
  device_id     text;
  device_secret text;
  hashed        text;
begin
  device_id     := current_setting('request.headers', true)::json->>'x-device-id';
  device_secret := current_setting('request.headers', true)::json->>'x-device-secret';

  if device_id is null or device_id = '' then return null; end if;
  if device_secret is null or device_secret = '' then return null; end if;

  select hashed_secret into hashed
  from private.devices
  where id = uuid(device_id);
  if not found then return null; end if;

  if crypt(device_secret, hashed) <> hashed then return null; end if;

  return uuid(device_id);
end;
$$;

create function private.device_has_uploaded(device uuid) returns boolean
language sql stable security definer
set search_path to 'public', 'private', 'pg_temp'
as $$
    select exists (select 1 from public.measurements m where m.device_id = device)
        or exists (select 1 from public.fcc_submissions s where s.device_id = device);
$$;

revoke all on function private.device_has_uploaded(uuid) from public;

create function public.register_device(device_id uuid, device_secret text) returns void
language plpgsql security definer
set search_path to 'public', 'private', 'extensions', 'pg_temp'
as $$
declare
    hashed text;
begin
    -- The app sends 32 random bytes as 64 hex digits. Anything much shorter could be guessed.
    if length(coalesce(device_secret, '')) < 32 then
        raise exception 'device secret is too short';
    end if;

    insert into private.devices (id, hashed_secret)
    values (device_id, crypt(device_secret, gen_salt('bf')))
    on conflict do nothing;
    if found then
        return;
    end if;

    -- The device's uploads in progress finish first (see above).
    perform pg_advisory_xact_lock(hashtextextended('cellwatch device ' || device_id::text, 0));

    -- Checked the same way as in private.verified_device_id_or_null().
    select d.hashed_secret into hashed from private.devices d where d.id = device_id;
    if crypt(device_secret, hashed) = hashed then
        return;
    end if;

    if private.device_has_uploaded(device_id) then
        raise exception 'device id % already registered', device_id;
    end if;

    update private.devices
    set hashed_secret = crypt(device_secret, gen_salt('bf'))
    where id = device_id;
end;
$$;

-- The live upload function, unchanged.
create function public.insert_measurement(in_measurement public.measurements, in_measurement_data public.upload_download_data, in_latency_data public.latency_data, in_locations public.locations[], in_cells public.cells[]) returns public.measurements
language plpgsql as $$
declare
    new_measurement measurements%ROWTYPE;
    location locations%ROWTYPE;
    cell cells%ROWTYPE;
begin
    insert into measurements select in_measurement.* returning * into new_measurement;

    -- insert related measurement data
    if in_measurement_data is null then
        -- do nothing
    else
        in_measurement_data.measurement_id = new_measurement.id;
        insert into upload_download_data select in_measurement_data.*;
    end if;

    -- insert related latency data
    if in_latency_data is null then
        -- do nothing
    else
        in_latency_data.measurement_id = new_measurement.id;
        insert into latency_data select in_latency_data.*;
    end if;

    -- Insert related location records
    if in_locations is null then
        -- do nothing
    else
        foreach location in array in_locations loop
                location.measurement_id = new_measurement.id;
                insert into locations select location.*;
            end loop;
    end if;

    -- Insert related cell records
    if in_cells is null then
        -- do nothing
    else
        foreach cell in array in_cells loop
                cell.measurement_id = new_measurement.id;
                insert into cells select cell.*;
            end loop;
    end if;

    return new_measurement;
end;
$$;

-- The live policies, copied from supabase/full_schema.sql.
alter table public.cells enable row level security;
alter table public.fcc_submissions enable row level security;
alter table public.latency_data enable row level security;
alter table public.locations enable row level security;
alter table public.measurements enable row level security;
alter table public.upload_download_data enable row level security;

CREATE POLICY "Allow inserting own FCC submissions" ON "public"."fcc_submissions" FOR INSERT TO "anon" WITH CHECK ((( SELECT "private"."verified_device_id"() AS "verified_device_id") = "device_id"));

CREATE POLICY "Allow inserting own cells" ON "public"."cells" FOR INSERT TO "anon" WITH CHECK (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));

CREATE POLICY "Allow inserting own latency data" ON "public"."latency_data" FOR INSERT TO "anon" WITH CHECK (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));

CREATE POLICY "Allow inserting own locations" ON "public"."locations" FOR INSERT TO "anon" WITH CHECK (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));

CREATE POLICY "Allow inserting own measurements" ON "public"."measurements" FOR INSERT TO "anon" WITH CHECK ((( SELECT "private"."verified_device_id"() AS "verified_device_id") = "device_id"));

CREATE POLICY "Allow inserting own upload/download data" ON "public"."upload_download_data" FOR INSERT TO "anon" WITH CHECK (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));

CREATE POLICY "Allow viewing latency (dashboard or own)" ON "public"."latency_data" FOR SELECT TO "anon" USING ((( SELECT "private"."is_dashboard_request"() AS "is_dashboard_request") OR (EXISTS ( SELECT 1
   FROM "public"."measurements" "m"
  WHERE (("m"."id" = "latency_data"."measurement_id") AND ("m"."device_id" = ( SELECT "private"."verified_device_id_or_null"() AS "verified_device_id_or_null")))))));

CREATE POLICY "Allow viewing locations (dashboard or own)" ON "public"."locations" FOR SELECT TO "anon" USING ((( SELECT "private"."is_dashboard_request"() AS "is_dashboard_request") OR (EXISTS ( SELECT 1
   FROM "public"."measurements" "m"
  WHERE (("m"."id" = "locations"."measurement_id") AND ("m"."device_id" = ( SELECT "private"."verified_device_id_or_null"() AS "verified_device_id_or_null")))))));

CREATE POLICY "Allow viewing own FCC submissions" ON "public"."fcc_submissions" FOR SELECT TO "anon" USING ((( SELECT "private"."verified_device_id"() AS "verified_device_id") = "device_id"));

CREATE POLICY "Allow viewing own cells" ON "public"."cells" FOR SELECT TO "anon" USING (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));

CREATE POLICY "Allow viewing own latency data" ON "public"."latency_data" FOR SELECT TO "anon" USING (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));

CREATE POLICY "Allow viewing own locations" ON "public"."locations" FOR SELECT TO "anon" USING (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));

CREATE POLICY "Allow viewing own measurements" ON "public"."measurements" FOR SELECT TO "anon" USING ((( SELECT "private"."is_dashboard_request"() AS "is_dashboard_request") OR (( SELECT "private"."verified_device_id_or_null"() AS "verified_device_id_or_null") = "device_id")));

CREATE POLICY "Allow viewing own upload/download data" ON "public"."upload_download_data" FOR SELECT TO "anon" USING (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));

CREATE POLICY "Allow viewing ud (dashboard or own)" ON "public"."upload_download_data" FOR SELECT TO "anon" USING ((( SELECT "private"."is_dashboard_request"() AS "is_dashboard_request") OR (EXISTS ( SELECT 1
   FROM "public"."measurements" "m"
  WHERE (("m"."id" = "upload_download_data"."measurement_id") AND ("m"."device_id" = ( SELECT "private"."verified_device_id_or_null"() AS "verified_device_id_or_null")))))));

CREATE POLICY "dashboard can select cells" ON "public"."cells" FOR SELECT TO "anon" USING (( SELECT "private"."is_dashboard_request"() AS "is_dashboard_request"));

CREATE POLICY "dashboard can select latency_data" ON "public"."latency_data" FOR SELECT TO "anon" USING (( SELECT "private"."is_dashboard_request"() AS "is_dashboard_request"));

CREATE POLICY "dashboard can select locations" ON "public"."locations" FOR SELECT TO "anon" USING (( SELECT "private"."is_dashboard_request"() AS "is_dashboard_request"));

-- Test data: devices A and B with secrets like the app's (32 random bytes as 64 hex digits),
-- hashed the way register_device does it, and a few measurements and FCC submissions each.
create table test_devices (name text primary key, id uuid not null, secret text not null);
insert into test_devices values
    ('A', gen_random_uuid(), encode(gen_random_bytes(32), 'hex')),
    ('B', gen_random_uuid(), encode(gen_random_bytes(32), 'hex'));
insert into private.devices select id, crypt(secret, gen_salt('bf')) from test_devices;
insert into private.dashboard_settings (shared_secret) values ('dashboard-secret');

create function test_device(device_name text) returns uuid
language sql stable as $$ select id from test_devices where name = device_name $$;

create function test_secret(device_name text) returns text
language sql stable as $$ select secret from test_devices where name = device_name $$;

-- Adds n measurements for a device, each with its throughput or latency row, a location and
-- two cells, and an FCC submission.
create function test_add_measurements(device uuid, n integer) returns void
language sql as $$
    with m as (
        insert into public.measurements (id, device_id, type, app_version)
        select gen_random_uuid(), device, (array['download', 'upload', 'latency'])[1 + i % 3], '1.0.0'
        from generate_series(1, n) i
        returning id, type
    ), ud as (
        insert into public.upload_download_data (measurement_id, bytes) select id, 1000000 from m where type <> 'latency'
    ), lat as (
        insert into public.latency_data (measurement_id, rtt, jitter) select id, 40, 4 from m where type = 'latency'
    ), loc as (
        insert into public.locations (measurement_id, lat, lon) select id, 33.77, -84.39 from m
    ), fcc as (
        insert into public.fcc_submissions (device_id, source_ip) values (device, '203.0.113.1')
    )
    insert into public.cells (measurement_id, rsrp) select id, -95 from m, generate_series(1, 2);
$$;

do $$ begin perform test_add_measurements(test_device('A'), 9); end $$;
do $$ begin perform test_add_measurements(test_device('B'), 6); end $$;

-- A measurement of device A with no data yet, for attempts to attach data to it.
insert into public.measurements (id, device_id, type) values ('00000000-0000-0000-0000-00000000000a', test_device('A'), 'download');

-- A throughput measurement of device A with its data, for the duplicate check.
create function test_measurement_with_data(device uuid) returns uuid
language sql stable as $$
    select m.id from public.measurements m join public.upload_download_data ud on ud.measurement_id = m.id
    where m.device_id = device order by m.id limit 1
$$;

-- Request headers as the app (or the dashboard) sends them.
create function test_headers_for(device text, secret text) returns text
language sql immutable as $$
    select json_build_object('x-device-id', device, 'x-device-secret', secret,
                             'content-type', 'application/json', 'prefer', 'return=representation')::text
$$;

create table test_callers (name text primary key, headers text not null);
insert into test_callers values
    ('owner', test_headers_for(test_device('A')::text, test_secret('A'))),
    ('other device', test_headers_for(test_device('B')::text, test_secret('B'))),
    ('wrong secret', test_headers_for(test_device('A')::text, test_secret('B'))),
    ('unregistered device', test_headers_for(gen_random_uuid()::text, test_secret('A'))),
    ('malformed device id', test_headers_for('not-a-uuid', test_secret('A'))),
    ('empty secret', test_headers_for(test_device('A')::text, '')),
    ('dashboard', json_build_object('x-dashboard-secret', 'dashboard-secret')::text),
    ('dashboard with device headers', json_build_object('x-dashboard-secret', 'dashboard-secret',
        'x-device-id', test_device('B'), 'x-device-secret', test_secret('B'))::text),
    ('wrong dashboard secret', json_build_object('x-dashboard-secret', 'not-the-secret')::text),
    ('no headers', '{}');

create function test_headers(caller text) returns text
language sql stable as $$ select headers from test_callers where name = caller $$;

-- POST /rpc/insert_measurement as PostgREST builds it, with the body the app sends: one
-- measurement of this kind with its throughput or latency data, two locations (start and end)
-- and n cells. Returns the device id of the measurement it inserted.
create function test_upload_statement(device uuid, kind text, n_cells integer, grp uuid default gen_random_uuid()) returns text
language sql volatile as $$
    select format($sql$
        with pgrst_source as (
            with pgrst_payload as (select %L::json as json_data),
            pgrst_body as (select case when json_typeof(json_data) = 'array' then json_data
                                       else json_build_array(json_data) end as val
                           from pgrst_payload),
            pgrst_args as (select * from json_to_recordset((select val from pgrst_body)) as _(
                "in_measurement" public.measurements, "in_measurement_data" public.upload_download_data,
                "in_latency_data" public.latency_data, "in_locations" public.locations[],
                "in_cells" public.cells[]))
            select "insert_measurement".*
            from "public"."insert_measurement"(
                "in_measurement" := (select "in_measurement" from pgrst_args limit 1),
                "in_measurement_data" := (select "in_measurement_data" from pgrst_args limit 1),
                "in_latency_data" := (select "in_latency_data" from pgrst_args limit 1),
                "in_locations" := (select "in_locations" from pgrst_args limit 1),
                "in_cells" := (select "in_cells" from pgrst_args limit 1)) "insert_measurement"
        )
        select json_agg(_postgrest_t) -> 0 ->> 'device_id' from (select * from pgrst_source) _postgrest_t
    $sql$, json_build_object(
        'in_measurement', json_build_object('id', gen_random_uuid(), 'group_id', grp, 'device_id', device,
            'type', kind, 'timestamp', now(), 'duration', 10000000, 'success', true, 'app_version', '1.0.0',
            'app_name', 'CellWatch', 'provider', 'T-Mobile', 'connection_type', 'CELLULAR', 'scheduled', false,
            'carrier_aggregation', false, 'network_connected', true, 'network_available', true,
            'network_roaming', false, 'cellular_data_enabled', true,
            'sim_mobile_country_code', '310', 'sim_mobile_network_code', '260',
            'net_mobile_country_code', '310', 'net_mobile_network_code', '260'),
        'in_measurement_data', case when kind <> 'latency' then json_build_object('id', gen_random_uuid(),
            'warmup_duration', 1000000, 'warmup_bytes', 100000, 'duration', 10000000, 'bytes', 25000000,
            'application_bytes', 24000000, 'servers', json_build_array('server-1')) end,
        'in_latency_data', case when kind = 'latency' then json_build_object('id', gen_random_uuid(),
            'rtt', 42.5, 'jitter', 3.1, 'sent', 200, 'received', 200, 'servers', json_build_array('server-1')) end,
        'in_locations', json_build_array(
            json_build_object('id', gen_random_uuid(), 'timestamp', now(), 'lat', 33.77, 'lon', -84.39, 'accuracy', 5),
            json_build_object('id', gen_random_uuid(), 'timestamp', now(), 'lat', 33.78, 'lon', -84.39, 'accuracy', 5)),
        'in_cells', (select coalesce(json_agg(json_build_object('id', gen_random_uuid(), 'timestamp', now(),
                         'rsrp', -95 - i, 'network_generation', '4G', 'cell_connection', case when i = 1 then 1 else 0 end)), '[]')
                     from generate_series(1, n_cells) i)))
$$;

-- POST /fcc_submissions with Prefer: return=representation, as PostgREST builds it, with the
-- submission the app sends after the test's three measurements. Returns its device id.
create function test_submission_statement(device uuid, grp uuid default gen_random_uuid()) returns text
language sql volatile as $$
    select format($sql$
        with pgrst_source as (
            insert into "public"."fcc_submissions" ("id", "device_id", "device_timestamp", "server_timestamp",
                "source_ip", "source_port", "sim_country_code", "sim_network_code", "net_country_code",
                "net_network_code", "device_manufacturer", "device_model", "device_os", "app_name",
                "app_version", "provider", "device_type", "in_vehicle", "external_antenna")
            select pgrst_body."id", pgrst_body."device_id", pgrst_body."device_timestamp", pgrst_body."server_timestamp",
                pgrst_body."source_ip", pgrst_body."source_port", pgrst_body."sim_country_code", pgrst_body."sim_network_code",
                pgrst_body."net_country_code", pgrst_body."net_network_code", pgrst_body."device_manufacturer",
                pgrst_body."device_model", pgrst_body."device_os", pgrst_body."app_name", pgrst_body."app_version",
                pgrst_body."provider", pgrst_body."device_type", pgrst_body."in_vehicle", pgrst_body."external_antenna"
            from (select %L::json as json_data) pgrst_payload,
                lateral (select case when json_typeof(pgrst_payload.json_data) = 'array' then pgrst_payload.json_data
                                     else json_build_array(pgrst_payload.json_data) end as val) pgrst_uniform_json,
                lateral (select * from json_to_recordset(pgrst_uniform_json.val) as _("id" uuid, "device_id" uuid,
                    "device_timestamp" timestamptz, "server_timestamp" timestamptz, "source_ip" varchar,
                    "source_port" integer, "sim_country_code" varchar, "sim_network_code" varchar,
                    "net_country_code" varchar, "net_network_code" varchar, "device_manufacturer" text,
                    "device_model" text, "device_os" text, "app_name" text, "app_version" text, "provider" text,
                    "device_type" text, "in_vehicle" boolean, "external_antenna" boolean)) pgrst_body
            returning "public"."fcc_submissions".*
        )
        select json_agg(_postgrest_t) -> 0 ->> 'device_id'
        from (select "fcc_submissions".* from "pgrst_source" as "fcc_submissions") _postgrest_t
    $sql$, json_build_object('id', grp, 'device_id', device, 'device_timestamp', now(), 'server_timestamp', now(),
        'source_ip', '203.0.113.7', 'source_port', 40000, 'sim_country_code', '310', 'sim_network_code', '260',
        'net_country_code', '310', 'net_network_code', '260', 'device_manufacturer', 'Google',
        'device_model', 'Pixel 8', 'device_os', 'Android', 'app_name', 'CellWatch', 'app_version', '1.0.0',
        'provider', 'T-Mobile', 'device_type', 'phone', 'in_vehicle', false, 'external_antenna', false))
$$;

-- The duplicate check of the released app, as PostgREST runs it:
-- measurements?id=eq.X&select=*,upload_download_data(*),latency_data(*),locations(*),cells(*)
create function test_confirm_statement(measurement uuid) returns text
language sql stable as $$
    select format($sql$
        select md5(row_to_json(r)::text) from (
            select m.*, ud.j as upload_download_data, lat.j as latency_data, loc.j as locations, c.j as cells
            from public.measurements m
            left join lateral (select row_to_json(x) j from public.upload_download_data x
                               where x.measurement_id = m.id) ud on true
            left join lateral (select row_to_json(x) j from public.latency_data x
                               where x.measurement_id = m.id) lat on true
            left join lateral (select coalesce(json_agg(x order by x.id), '[]') j from public.locations x
                               where x.measurement_id = m.id) loc on true
            left join lateral (select coalesce(json_agg(x order by x.id), '[]') j from public.cells x
                               where x.measurement_id = m.id) c on true
            where m.id = %L
        ) r
    $sql$, measurement)
$$;

-- Which rows a caller can see in a table, as a count and a hash of the ids.
create function test_rows_statement(table_name text) returns text
language sql immutable as $$
    select format('select count(*) || '' rows '' || md5(coalesce(string_agg(id::text, '','' order by id), '''')) from public.%I',
                  table_name)
$$;

-- crypt() calls so far in this transaction. The statistics are only flushed while the session
-- is idle, so differences within one transaction are exact.
create function test_crypt_calls() returns bigint
language sql as $$
    select coalesce(pg_stat_get_xact_function_calls('extensions.crypt(text,text)'::regprocedure), 0)
$$;

-- Runs a statement as PostgREST would for the caller: role anon, the caller's headers in
-- request.headers. Changes are always rolled back. Returns 'ok' and what the statement
-- returned, or the error.
create function test_as(caller text, statement text) returns text
language plpgsql as $$
declare
    result text;
begin
    perform set_config('request.headers', test_headers(caller), true);
    set role anon;
    execute statement into result;
    reset role;
    raise exception using errcode = 'TSTOK', message = coalesce(result, '');
exception
    when sqlstate 'TSTOK' then
        return 'ok ' || sqlerrm;
    when others then
        return sqlstate || ' ' || sqlerrm;
end;
$$;

-- Everything each caller can read and write, saved per phase so the two phases can be compared.
create table test_outcomes (phase text, caller text, action text, outcome text, primary key (phase, caller, action));

create function test_record_outcomes(phase text) returns void
language plpgsql as $$
declare
    caller text;
    table_name text;
    target uuid := '00000000-0000-0000-0000-00000000000a';
begin
    for caller in select name from test_callers loop
        foreach table_name in array array['measurements', 'upload_download_data', 'latency_data', 'locations', 'cells', 'fcc_submissions'] loop
            insert into test_outcomes values (phase, caller, 'read ' || table_name,
                test_as(caller, test_rows_statement(table_name)));
        end loop;
        insert into test_outcomes values
            (phase, caller, 'upload a measurement as device A', test_as(caller, test_upload_statement(test_device('A'), 'latency', 3))),
            (phase, caller, 'submit an FCC test as device A', test_as(caller, test_submission_statement(test_device('A')))),
            (phase, caller, 'duplicate check of device A''s measurement', test_as(caller,
                test_confirm_statement(test_measurement_with_data(test_device('A'))))),
            (phase, caller, 'attach upload_download_data to device A''s measurement', test_as(caller, format(
                'insert into public.upload_download_data (measurement_id, bytes) values (%L, 1) returning ''inserted''', target))),
            (phase, caller, 'attach latency_data to device A''s measurement', test_as(caller, format(
                'insert into public.latency_data (measurement_id, rtt) values (%L, 1) returning ''inserted''', target))),
            (phase, caller, 'attach a location to device A''s measurement', test_as(caller, format(
                'insert into public.locations (measurement_id, lat, lon) values (%L, 1, 1) returning ''inserted''', target))),
            (phase, caller, 'attach a cell to device A''s measurement', test_as(caller, format(
                'insert into public.cells (measurement_id, rsrp) values (%L, 1) returning ''inserted''', target)));
    end loop;
end;
$$;

-- How many times crypt() ran in each request of an upload, and how long the request took.
create table test_counts (
    phase text, cells integer, seq integer, request text, crypt_calls bigint, ms numeric,
    primary key (phase, cells, seq)
);

-- Runs one request the way PostgREST does, in the current transaction: request settings for
-- this transaction only, role anon, then the statement. Returns how many times crypt() ran.
create function test_request_calls(headers text, statement text) returns bigint
language plpgsql as $$
declare
    calls_before bigint := test_crypt_calls();
    result text;
begin
    perform set_config('search_path', '"public", "extensions"', true),
            set_config('request.method', 'POST', true),
            set_config('request.headers', headers, true);
    set local role anon;
    execute statement into result;
    reset role;
    return test_crypt_calls() - calls_before;
end;
$$;

-- One test's upload by device A, as MeasurementUploader sends it: the three measurements
-- (insert_measurement), then the FCC submission, each in a transaction of its own as PostgREST
-- runs it. Logs crypt() calls per request, then deletes what it uploaded.
create procedure test_upload(phase text, n_cells integer)
language plpgsql as $$
declare
    grp uuid := gen_random_uuid();
    kind text;
    seq integer := 0;
    started timestamptz;
    calls bigint;
begin
    foreach kind in array array['download', 'upload', 'latency'] loop
        seq := seq + 1;
        started := clock_timestamp();
        calls := test_request_calls(test_headers('owner'), test_upload_statement(test_device('A'), kind, n_cells, grp));
        insert into test_counts values (phase, n_cells, seq, format('insert_measurement (%s)', kind), calls,
                                        round(extract(epoch from clock_timestamp() - started)::numeric * 1000, 1));
        commit;
    end loop;

    started := clock_timestamp();
    calls := test_request_calls(test_headers('owner'), test_submission_statement(test_device('A'), grp));
    insert into test_counts values (phase, n_cells, 4, 'insert into fcc_submissions', calls,
                                    round(extract(epoch from clock_timestamp() - started)::numeric * 1000, 1));
    commit;

    delete from public.fcc_submissions where id = grp;
    delete from public.measurements where group_id = grp;
    commit;
end;
$$;

create function expect(ok boolean, description text) returns void
language plpgsql as $$
begin
    if ok is distinct from true then
        raise exception 'FAILED: %', description;
    end if;
    raise notice 'ok - %', description;
end;
$$;

-- Before the migration: what every caller can do, and what an upload costs.
do $$ begin perform test_record_outcomes('before'); end $$;
call test_upload('before', 3);
call test_upload('before', 10);
begin read only;
select test_request_calls(test_headers('owner'), test_confirm_statement(test_measurement_with_data(test_device('A'))))
    as confirm_calls_before \gset
commit;

do $$
begin
    perform expect(
        (select array_agg(crypt_calls order by seq) from test_counts where phase = 'before' and cells = 3)
            = array[14, 14, 16, 1]::bigint[],
        'live checks: with 2 locations and 3 cells, the requests of a test''s upload run bcrypt 14, 14, 16 and 1 times');
    perform expect(
        (select array_agg(crypt_calls order by seq) from test_counts where phase = 'before' and cells = 10)
            = array[28, 28, 30, 1]::bigint[],
        'live checks: with 10 cells, 28, 28, 30 and 1 times (2 more per cell)');
    perform expect(
        (select bool_and(outcome = 'ok ' || test_device('A')) from test_outcomes
         where phase = 'before' and caller = 'owner' and action in ('upload a measurement as device A', 'submit an FCC test as device A')),
        'setup: device A can upload measurements and FCC submissions');
    perform expect(test_as('wrong secret', test_upload_statement(test_device('A'), 'download', 3)) = 'P0001 incorrect device secret',
        'setup: a wrong secret cannot');
end;
$$;

\i /migrations/202611010004_device_check_once_per_request.sql

-- Running it again changes nothing. (It reports "already exists, skipping" for the table; hide
-- that.)
select encode(key, 'hex') as first_key from private.device_check_key \gset
set client_min_messages = warning;
\i /migrations/202611010004_device_check_once_per_request.sql
set client_min_messages = notice;
set test.first_key = :'first_key';

call test_upload('after', 3);
call test_upload('after', 10);
begin read only;
select test_request_calls(test_headers('owner'), test_confirm_statement(test_measurement_with_data(test_device('A'))))
    as confirm_calls_after \gset
commit;
set test.confirm_calls_before = :'confirm_calls_before';
set test.confirm_calls_after = :'confirm_calls_after';

do $$ begin perform test_record_outcomes('after'); end $$;

do $$
declare
    r record;
begin
    perform expect(
        (select count(*) = 1 and bool_and(encode(key, 'hex') = current_setting('test.first_key')) from private.device_check_key),
        'running the migration again keeps the one key');

    perform expect(
        (select array_agg(crypt_calls order by cells, seq) from test_counts where phase = 'after')
            = array[1, 1, 1, 1, 1, 1, 1, 1]::bigint[],
        'each request of a test''s upload runs bcrypt once, with 3 cells or 10');
    perform expect(current_setting('test.confirm_calls_after')::bigint = 1,
        format('so does the read-only duplicate check (was %s)', current_setting('test.confirm_calls_before')));

    raise notice 'bcrypt runs (and ms) per request of one test''s upload, live -> with this migration:';
    for r in
        select b.cells, b.seq, b.request, b.crypt_calls as before, a.crypt_calls as after, b.ms as before_ms, a.ms as after_ms
        from test_counts b join test_counts a using (cells, seq)
        where b.phase = 'before' and a.phase = 'after'
        order by b.cells, b.seq
    loop
        raise notice '  % cells, %: % -> % (% ms -> % ms)', r.cells, r.request, r.before, r.after, r.before_ms, r.after_ms;
    end loop;
    for r in
        select cells, sum(crypt_calls) filter (where phase = 'before') as before, sum(crypt_calls) filter (where phase = 'after') as after
        from test_counts group by cells order by cells
    loop
        raise notice '  whole upload with % cells per measurement: % -> %', r.cells, r.before, r.after;
    end loop;

    for r in
        select b.caller, b.action, b.outcome as before, a.outcome as after
        from test_outcomes b join test_outcomes a using (caller, action)
        where b.phase = 'before' and a.phase = 'after' and a.outcome is distinct from b.outcome
    loop
        raise notice 'changed: % / %: % -> %', r.caller, r.action, r.before, r.after;
    end loop;
    perform expect(
        (select count(*) from test_outcomes where phase = 'before') = 10 * 13
        and (select count(*) from test_outcomes where phase = 'after') = 10 * 13
        and not exists (
            select 1
            from test_outcomes b join test_outcomes a using (caller, action)
            where b.phase = 'before' and a.phase = 'after' and a.outcome is distinct from b.outcome),
        'every caller can read and write exactly what it could before, with the same results and errors');
end;
$$;

-- What a remembered check is used for. These call the check functions directly, as postgres
-- (they read the request headers whoever calls them); the forgery checks below set the setting
-- as anon. Each do block is one transaction, like one request.
create function test_set_headers(headers text) returns void
language plpgsql as $$
begin
    perform set_config('request.headers', headers, true);
end;
$$;

-- private.verified_device_id() for the request headers: the device id, or its error.
create function test_verified() returns text
language plpgsql as $$
begin
    return private.verified_device_id()::text;
exception when others then
    return sqlerrm;
end;
$$;

-- private.verified_device_id_or_null() for the request headers: the device id, or 'null'.
create function test_verified_or_null() returns text
language plpgsql as $$
begin
    return coalesce(private.verified_device_id_or_null()::text, 'null');
end;
$$;

-- The check this transaction keeps: a tag, or '' when there is none.
create function test_kept() returns text
language sql as $$ select coalesce(current_setting('cellwatch.device_check', true), '') $$;

-- The right secret.
do $$
declare
    calls bigint := test_crypt_calls();
    a text := test_device('A')::text;
begin
    perform test_set_headers(test_headers('owner'));
    perform expect(test_verified() = a, 'the right secret is accepted');
    perform expect(test_verified() = a and test_verified_or_null() = a and test_verified() = a,
        'and accepted again by both checks in the same transaction');
    perform expect(test_crypt_calls() - calls = 1, 'with one bcrypt run for all four checks');
    perform expect(test_kept() ~ '^[0-9a-f]{64}$' and strpos(test_kept(), test_secret('A')) = 0,
        'what is kept is a 256-bit tag, not the secret');
    perform expect(
        exists (select 1 from pg_locks l
                where l.locktype = 'advisory' and l.pid = pg_backend_pid() and l.mode = 'ShareLock' and l.granted),
        'verified_device_id() still holds the device''s advisory lock (202610220001)');
end;
$$;

-- A wrong secret.
do $$
declare
    calls bigint := test_crypt_calls();
begin
    perform test_set_headers(test_headers('wrong secret'));
    perform expect(test_verified() = 'incorrect device secret' and test_verified_or_null() = 'null',
        'a wrong secret is refused by both checks');
    perform expect(test_verified() = 'incorrect device secret', 'and again');
    perform expect(test_crypt_calls() - calls = 3, 'each refusal runs bcrypt: a refusal is not kept');
    perform expect(test_kept() = '', 'and nothing is kept');
end;
$$;

-- Another secret for the same device, after the right one in the same transaction.
do $$
declare
    calls bigint;
begin
    perform test_set_headers(test_headers('owner'));
    perform expect(test_verified() = test_device('A')::text, 'setup: device A is let in');
    calls := test_crypt_calls();
    perform test_set_headers(test_headers('wrong secret'));
    perform expect(test_verified() = 'incorrect device secret' and test_verified_or_null() = 'null',
        'later in the same transaction, device A with another secret is refused');
    perform test_set_headers(test_headers_for(test_device('A')::text, test_secret('A') || 'x'));
    perform expect(test_verified() = 'incorrect device secret',
        'and so is its secret with a character added');
    perform expect(test_crypt_calls() - calls = 3, 'each checked with bcrypt, not taken from the kept check');
end;
$$;

-- Another device id, after device A in the same transaction.
do $$
declare
    calls bigint;
begin
    perform test_set_headers(test_headers('owner'));
    perform expect(test_verified() = test_device('A')::text, 'setup: device A is let in');
    calls := test_crypt_calls();
    perform test_set_headers(test_headers_for(test_device('B')::text, test_secret('A')));
    perform expect(test_verified() = 'incorrect device secret' and test_verified_or_null() = 'null',
        'later in the same transaction, device B with A''s secret is refused');
    perform test_set_headers(test_headers('other device'));
    perform expect(test_verified() = test_device('B')::text, 'device B with its own secret is let in');
    perform expect(test_crypt_calls() - calls = 3, 'each checked with bcrypt, not taken from A''s kept check');
    perform test_set_headers(test_headers('unregistered device'));
    perform expect(test_verified() like 'device id % is not registered' and test_verified_or_null() = 'null',
        'an unregistered device id with A''s secret is still not registered');
end;
$$;

-- A secret replaced (by register_device) in the same transaction.
do $$
declare
    device uuid := gen_random_uuid();
    first_secret text := encode(gen_random_bytes(32), 'hex');
    new_secret text := encode(gen_random_bytes(32), 'hex');
    calls bigint;
begin
    perform public.register_device(device, first_secret);
    perform test_set_headers(test_headers_for(device::text, first_secret));
    perform expect(test_verified() = device::text, 'setup: a new device is let in with its secret');
    -- It has never uploaded, so registering it again replaces its secret.
    perform public.register_device(device, new_secret);
    calls := test_crypt_calls();
    perform expect(test_verified() = 'incorrect device secret' and test_verified_or_null() = 'null',
        'once its secret is replaced in the same transaction, the old secret is refused');
    perform test_set_headers(test_headers_for(device::text, new_secret));
    perform expect(test_verified() = device::text, 'and the new one is let in');
    perform expect(test_crypt_calls() - calls = 3, 'all checked with bcrypt against the new hash');
end;
$$;

-- A check made in a savepoint that is rolled back.
do $$
declare
    calls bigint;
begin
    perform test_set_headers(test_headers('owner'));
    begin
        perform private.verified_device_id();
        raise exception using errcode = 'TSTRB';
    exception when sqlstate 'TSTRB' then
        null;
    end;
    perform expect(test_kept() = '', 'a check made in a savepoint that was rolled back is forgotten with it');
    calls := test_crypt_calls();
    perform expect(test_verified() = test_device('A')::text and test_crypt_calls() - calls = 1,
        'so the next check runs bcrypt');
end;
$$;

-- A read-only transaction (a GET) keeps a check too.
begin read only;
do $$
declare
    calls bigint := test_crypt_calls();
    a text := test_device('A')::text;
begin
    perform test_set_headers(test_headers('owner'));
    perform expect(test_verified() = a and test_verified_or_null() = a and test_crypt_calls() - calls = 1,
        'in a read-only transaction too, bcrypt runs once');
end;
$$;
commit;

-- A later transaction. Keep a check, and its tag, in one transaction...
create function test_keep(headers text) returns text
language plpgsql as $$
begin
    perform test_set_headers(headers);
    perform private.verified_device_id();
    return test_kept();
end;
$$;
select test_keep(test_headers('owner')) as earlier_tag \gset
set test.earlier_tag = :'earlier_tag';

-- ...then in the next one:
do $$
declare
    calls bigint := test_crypt_calls();
begin
    perform expect(current_setting('test.earlier_tag') ~ '^[0-9a-f]{64}$', 'setup: a transaction kept a check');
    perform expect(test_kept() = '', 'a kept check is gone once its transaction ends');
    perform test_set_headers(test_headers('owner'));
    perform expect(test_verified() = test_device('A')::text and test_crypt_calls() - calls = 1,
        'so the next transaction checks the secret with bcrypt again');
end;
$$;

-- anon may set the setting for the whole session, as any role can (from a direct database
-- login; PostgREST offers no way to). Here it copies the earlier transaction's tag.
set role anon;
set cellwatch.device_check = :'earlier_tag';
reset role;

do $$
declare
    calls bigint := test_crypt_calls();
begin
    perform expect(test_kept() = current_setting('test.earlier_tag'),
        'setup: anon set the setting to the tag an earlier transaction kept');
    perform test_set_headers(test_headers('wrong secret'));
    perform expect(test_verified() = 'incorrect device secret' and test_verified_or_null() = 'null',
        'that does not let a wrong secret in');
    perform test_set_headers(test_headers('owner'));
    perform expect(test_verified() = test_device('A')::text and test_crypt_calls() - calls = 3,
        'and is not used for the right secret either: a check is never reused in a later transaction');
end;
$$;
reset cellwatch.device_check;

-- A forged tag. anon can't read the key, so it would have to guess it. Here even a tag made
-- from everything else, including the stored hash (which anon can't read either), and a
-- guessed key, does not let a wrong secret in. The same tag made with the real key would:
-- that shows the tag below was made the way the migration makes it, and that the key is what
-- keeps tags from being forged.
create function test_tag(device uuid, secret text, tag_key bytea) returns text
language sql as $$
    select encode(hmac(
        convert_to(json_build_array('cellwatch device check', device, secret,
                                    (select hashed_secret from private.devices where id = device),
                                    pg_backend_pid(), extract(epoch from transaction_timestamp()))::text,
                   'UTF8'),
        tag_key, 'sha256'), 'hex')
$$;

-- Sets the setting as anon, for the rest of this transaction.
create function test_set_as_anon(value text) returns void
language plpgsql as $$
begin
    set local role anon;
    perform set_config('cellwatch.device_check', value, true);
    reset role;
end;
$$;

do $$
declare
    a uuid := test_device('A');
    wrong text := test_secret('B');
    calls bigint := test_crypt_calls();
begin
    perform test_set_headers(test_headers('wrong secret'));
    perform test_set_as_anon(test_tag(a, wrong, decode(repeat('00', 32), 'hex')));
    perform expect(test_verified() = 'incorrect device secret' and test_verified_or_null() = 'null',
        'a tag anon sets for a wrong secret, made with a guessed key, does not let it in');
    perform test_set_as_anon(test_tag(a, wrong, gen_random_bytes(32)));
    perform expect(test_verified() = 'incorrect device secret', 'nor with another guessed key');
    perform expect(test_crypt_calls() - calls = 3, 'each checked with bcrypt');

    perform test_set_as_anon(test_tag(a, wrong, (select key from private.device_check_key)));
    perform expect(test_verified() = a::text,
        'control: made with the real key, the same tag would be taken (so only the owner may read the key)');
end;
$$;

-- Who can read the key, and call the function that keeps checks.
set role test_all_reader;
select count(*) as all_reader_rows from private.device_check_key \gset
reset role;
set test.all_reader_rows = :'all_reader_rows';

do $$
declare
    role_name text;
begin
    perform expect(has_table_privilege('test_private_reader', 'private.devices', 'select'),
        'setup: the stand-in for readonly_user may read the other tables in private, like readonly_user');
    foreach role_name in array array['anon', 'authenticated', 'service_role', 'authenticator', 'test_private_reader'] loop
        perform expect(not has_table_privilege(role_name, 'private.device_check_key',
                                               'select, insert, update, delete, truncate, references, trigger'),
            format('%s has no privileges on the key', role_name));
    end loop;
    foreach role_name in array array['anon', 'authenticated', 'service_role', 'authenticator', 'test_private_reader', 'test_all_reader'] loop
        perform expect(not has_function_privilege(role_name, 'private.device_secret_matches(uuid, text)', 'execute'),
            format('%s may not call private.device_secret_matches()', role_name));
    end loop;
    perform expect(current_setting('test.all_reader_rows')::bigint = 0,
        'a role that reads every table (pg_read_all_data) gets no key: row level security hides it');
    perform expect(not has_schema_privilege('anon', 'private', 'usage') and not has_schema_privilege('authenticated', 'private', 'usage'),
        'anon and authenticated can''t even name the private schema');
    perform expect(
        has_function_privilege('anon', 'private.verified_device_id()', 'execute')
        and has_function_privilege('anon', 'private.verified_device_id_or_null()', 'execute'),
        'anon may still run the checks the policies call');
    perform expect(
        (select bool_and(p.prosecdef and pg_get_userbyid(p.proowner) = 'postgres'
                         and p.proconfig = array['search_path=public, private, extensions, pg_temp'])
         from pg_proc p
         where p.oid in ('private.verified_device_id()'::regprocedure, 'private.verified_device_id_or_null()'::regprocedure)),
        'both checks are still security definer functions owned by postgres, with the same search_path');
    perform expect(
        (select not p.prosecdef and pg_get_userbyid(p.proowner) = 'postgres'
         from pg_proc p where p.oid = 'private.device_secret_matches(uuid, text)'::regprocedure)
        and (select pg_get_userbyid(c.relowner) = 'postgres' and c.relrowsecurity
             from pg_class c where c.oid = 'private.device_check_key'::regclass),
        'the new function and key table are owned by postgres, and the key table has row level security');
    perform expect((select bool_and(length(key) = 32) and count(*) = 1 from private.device_check_key),
        'the key is 32 random bytes');
end;
$$;
