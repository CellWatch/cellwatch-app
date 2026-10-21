-- Tests for 202610210001_fast_upload_rls.sql, run by supabase/db_tests/run.sh in a throwaway
-- container. Loads the live row level security policies first, records what every kind of
-- caller can read and write, applies the migration, and checks that every caller is allowed
-- and refused exactly as before, and that uploads no longer slow down as a device's
-- measurements pile up.
\set ON_ERROR_STOP on
set client_min_messages = notice;
-- PostgREST requests run with this search_path (extra_search_path in supabase/config.toml).
-- The helper functions call crypt() without a schema, so they rely on it.
set search_path = public, extensions;

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
create extension if not exists "uuid-ossp" with schema extensions;
create schema if not exists private;

-- Copies of the tables the upload uses, as in supabase/full_schema.sql.
create type public.connection_type as enum ('NONE', 'WIFI', 'CELLULAR');

create table public.measurements (
    id uuid default extensions.uuid_generate_v4() not null primary key,
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
    id uuid default extensions.uuid_generate_v4() not null primary key,
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
    id uuid default extensions.uuid_generate_v4() not null primary key,
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
    id uuid default extensions.uuid_generate_v4() not null primary key,
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
    id uuid default extensions.uuid_generate_v4() not null primary key,
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

create index cells_measurement_id_idx on public.cells using btree (measurement_id);
create index locations_measurement_id_idx on public.locations using btree (measurement_id);

create table private.dashboard_settings (
    id boolean default true not null primary key,
    shared_secret text not null
);

create table private.devices (
    id uuid not null primary key,
    hashed_secret text not null
);

grant select, insert, references, delete, trigger, truncate, update
    on public.measurements, public.upload_download_data, public.latency_data,
       public.locations, public.cells
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

create trigger _200_cell_force_created_on before insert on public.cells
    for each row when (new.created_on is null) execute function public.force_created_on();
create trigger _200_latency_data_force_created_on before insert on public.latency_data
    for each row when (new.created_on is null) execute function public.force_created_on();
create trigger _200_location_force_created_on before insert on public.locations
    for each row when (new.created_on is null) execute function public.force_created_on();
create trigger _200_measurement_force_created_on before insert on public.measurements
    for each row when (new.created_on is null) execute function public.force_created_on();
create trigger _200_upload_download_data_force_created_on before insert on public.upload_download_data
    for each row when (new.created_on is null) execute function public.force_created_on();
create trigger fix_jitter_pre_100alpha5 before insert on public.latency_data
    for each row execute function public.fix_jitter();

-- The live helper functions, unchanged.
create function private.is_dashboard_request() returns boolean
language sql stable security definer as $$
  select coalesce(
    (current_setting('request.headers', true)::json->>'x-dashboard-secret')
      = (select shared_secret from private.dashboard_settings limit 1),
    false
  )
$$;

create function private.verified_device_id() returns uuid
language plpgsql security definer as $$
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
language plpgsql security definer as $$
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
alter table public.latency_data enable row level security;
alter table public.locations enable row level security;
alter table public.measurements enable row level security;
alter table public.upload_download_data enable row level security;

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

CREATE POLICY "Allow viewing latency (dashboard or own)" ON "public"."latency_data" FOR SELECT TO "anon" USING (("private"."is_dashboard_request"() OR (EXISTS ( SELECT 1
   FROM "public"."measurements" "m"
  WHERE (("m"."id" = "latency_data"."measurement_id") AND ("m"."device_id" = "private"."verified_device_id_or_null"()))))));

CREATE POLICY "Allow viewing locations (dashboard or own)" ON "public"."locations" FOR SELECT TO "anon" USING (("private"."is_dashboard_request"() OR (EXISTS ( SELECT 1
   FROM "public"."measurements" "m"
  WHERE (("m"."id" = "locations"."measurement_id") AND ("m"."device_id" = "private"."verified_device_id_or_null"()))))));

CREATE POLICY "Allow viewing own cells" ON "public"."cells" FOR SELECT TO "anon" USING (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));

CREATE POLICY "Allow viewing own latency data" ON "public"."latency_data" FOR SELECT TO "anon" USING (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));

CREATE POLICY "Allow viewing own locations" ON "public"."locations" FOR SELECT TO "anon" USING (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));

CREATE POLICY "Allow viewing own measurements" ON "public"."measurements" FOR SELECT TO "anon" USING (("private"."is_dashboard_request"() OR ("private"."verified_device_id_or_null"() = "device_id")));

CREATE POLICY "Allow viewing own upload/download data" ON "public"."upload_download_data" FOR SELECT TO "anon" USING (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));

CREATE POLICY "Allow viewing ud (dashboard or own)" ON "public"."upload_download_data" FOR SELECT TO "anon" USING (("private"."is_dashboard_request"() OR (EXISTS ( SELECT 1
   FROM "public"."measurements" "m"
  WHERE (("m"."id" = "upload_download_data"."measurement_id") AND ("m"."device_id" = "private"."verified_device_id_or_null"()))))));

CREATE POLICY "dashboard can select cells" ON "public"."cells" FOR SELECT TO "anon" USING ("private"."is_dashboard_request"());

CREATE POLICY "dashboard can select latency_data" ON "public"."latency_data" FOR SELECT TO "anon" USING ("private"."is_dashboard_request"());

CREATE POLICY "dashboard can select locations" ON "public"."locations" FOR SELECT TO "anon" USING ("private"."is_dashboard_request"());

-- Test data: device A uploads a lot, device B a little. Secrets are hashed the way
-- register_device does it.
create table test_devices (name text primary key, id uuid not null, secret text not null);
insert into test_devices values
    ('A', gen_random_uuid(), 'secret-a'),
    ('B', gen_random_uuid(), 'secret-b');
insert into private.devices select id, crypt(secret, gen_salt('bf')) from test_devices;
insert into private.dashboard_settings (shared_secret) values ('dashboard-secret');

create function test_device(device_name text) returns uuid
language sql stable as $$ select id from test_devices where name = device_name $$;

-- Adds n measurements for a device, each with its throughput or latency row, a location and
-- two cells.
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
    )
    insert into public.cells (measurement_id, rsrp) select id, -95 from m, generate_series(1, 2);
$$;

do $$ begin perform test_add_measurements(test_device('A'), 50); end $$;
do $$ begin perform test_add_measurements(test_device('B'), 5); end $$;

-- A measurement of device A with no data yet, for attempts to attach data to it.
insert into public.measurements (id, device_id, type) values ('00000000-0000-0000-0000-00000000000a', test_device('A'), 'download');

-- The request headers of each kind of caller.
create table test_callers (name text primary key, headers jsonb not null);
insert into test_callers values
    ('owner', jsonb_build_object('x-device-id', test_device('A'), 'x-device-secret', 'secret-a')),
    ('other device', jsonb_build_object('x-device-id', test_device('B'), 'x-device-secret', 'secret-b')),
    ('wrong secret', jsonb_build_object('x-device-id', test_device('A'), 'x-device-secret', 'secret-b')),
    ('unregistered device', jsonb_build_object('x-device-id', gen_random_uuid(), 'x-device-secret', 'secret-a')),
    ('dashboard', jsonb_build_object('x-dashboard-secret', 'dashboard-secret')),
    ('dashboard with device headers', jsonb_build_object('x-dashboard-secret', 'dashboard-secret',
        'x-device-id', test_device('B'), 'x-device-secret', 'secret-b')),
    ('wrong dashboard secret', jsonb_build_object('x-dashboard-secret', 'not-the-secret')),
    ('no headers', '{}');

create function test_headers(caller text) returns text
language sql stable as $$ select headers::text from test_callers where name = caller $$;

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

-- Like test_as, but returns how long the statement took. Fails if it fails.
create function test_time(caller text, statement text) returns interval
language plpgsql as $$
declare
    started timestamptz;
    elapsed interval;
begin
    perform set_config('request.headers', test_headers(caller), true);
    set role anon;
    started := clock_timestamp();
    execute statement;
    elapsed := clock_timestamp() - started;
    reset role;
    raise exception using errcode = 'TSTOK', message = elapsed::text;
exception
    when sqlstate 'TSTOK' then
        return sqlerrm::interval;
end;
$$;

-- What the app sends: insert_measurement with a measurement, its throughput data, two
-- locations and three cells.
create function test_upload_statement(device uuid) returns text
language sql stable as $$
    select format($sql$
        select (public.insert_measurement(
            jsonb_populate_record(null::public.measurements, jsonb_build_object(
                'id', gen_random_uuid(), 'device_id', %L::uuid, 'type', 'download',
                'timestamp', now(), 'app_version', '1.0.0', 'connection_type', 'CELLULAR')),
            jsonb_populate_record(null::public.upload_download_data, jsonb_build_object(
                'id', gen_random_uuid(), 'bytes', 1000000, 'duration', 10)),
            null,
            array[jsonb_populate_record(null::public.locations, jsonb_build_object(
                      'id', gen_random_uuid(), 'timestamp', now(), 'lat', 33.77, 'lon', -84.39)),
                  jsonb_populate_record(null::public.locations, jsonb_build_object(
                      'id', gen_random_uuid(), 'timestamp', now(), 'lat', 33.78, 'lon', -84.39))],
            array[jsonb_populate_record(null::public.cells, jsonb_build_object(
                      'id', gen_random_uuid(), 'timestamp', now(), 'rsrp', -95)),
                  jsonb_populate_record(null::public.cells, jsonb_build_object(
                      'id', gen_random_uuid(), 'timestamp', now(), 'rsrp', -101)),
                  jsonb_populate_record(null::public.cells, jsonb_build_object(
                      'id', gen_random_uuid(), 'timestamp', now(), 'rsrp', -110))]
        )).device_id::text
    $sql$, device)
$$;

-- What PostgREST runs for the app's duplicate check,
-- measurements?id=eq.X&select=*,upload_download_data(*),latency_data(*),locations(*),cells(*)
create function test_confirm_statement(measurement uuid) returns text
language sql stable as $$
    select format($sql$
        select row_to_json(r)::text from (
            select m.*, ud.j as upload_download_data, lat.j as latency_data, loc.j as locations, c.j as cells
            from public.measurements m
            left join lateral (select row_to_json(x) j from public.upload_download_data x
                               where x.measurement_id = m.id) ud on true
            left join lateral (select row_to_json(x) j from public.latency_data x
                               where x.measurement_id = m.id) lat on true
            left join lateral (select coalesce(json_agg(x), '[]') j from public.locations x
                               where x.measurement_id = m.id) loc on true
            left join lateral (select coalesce(json_agg(x), '[]') j from public.cells x
                               where x.measurement_id = m.id) c on true
            where m.id = %L
        ) r
    $sql$, measurement)
$$;

-- Which rows a statement can see, as a count and a hash of the ids.
create function test_rows_statement(table_name text) returns text
language sql immutable as $$
    select format('select count(*) || '' rows '' || md5(coalesce(string_agg(id::text, '','' order by id), '''')) from public.%I',
                  table_name)
$$;

-- The same, computed without row level security, for the rows of the given devices.
create function test_expected_rows(table_name text, devices uuid[]) returns text
language plpgsql as $$
declare
    result text;
begin
    execute format(
        'select count(*) || '' rows '' || md5(coalesce(string_agg(t.id::text, '','' order by t.id), '''')) from public.%I t ', table_name)
        || case when table_name = 'measurements' then 'where t.device_id = any($1)'
                else 'join public.measurements m on m.id = t.measurement_id where m.device_id = any($1)' end
        into result using devices;
    return 'ok ' || result;
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
        foreach table_name in array array['measurements', 'upload_download_data', 'latency_data', 'locations', 'cells'] loop
            insert into test_outcomes values (phase, caller, 'read ' || table_name,
                test_as(caller, test_rows_statement(table_name)));
        end loop;
        insert into test_outcomes values
            (phase, caller, 'upload as device A', test_as(caller, test_upload_statement(test_device('A')))),
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

create function expect(ok boolean, description text) returns void
language plpgsql as $$
begin
    if ok is distinct from true then
        raise exception 'FAILED: %', description;
    end if;
    raise notice 'ok - %', description;
end;
$$;

-- Checks that callers can see and do what they should, under whichever policies are loaded.
create function test_check_access(phase text) returns void
language plpgsql as $$
declare
    table_name text;
    seen text;
    a uuid[] := array[test_device('A')];
    b uuid[] := array[test_device('B')];
    everyone uuid[] := array[test_device('A'), test_device('B')];
begin
    foreach table_name in array array['measurements', 'upload_download_data', 'latency_data', 'locations', 'cells'] loop
        perform expect(test_as('owner', test_rows_statement(table_name)) = test_expected_rows(table_name, a),
            format('%s: a device sees all of its own %s and nothing else', phase, table_name));
        perform expect(test_as('other device', test_rows_statement(table_name)) = test_expected_rows(table_name, b),
            format('%s: another device sees only its own %s', phase, table_name));
        perform expect(test_as('dashboard', test_rows_statement(table_name)) = test_expected_rows(table_name, everyone),
            format('%s: the dashboard sees all %s', phase, table_name));
        seen := test_as('wrong secret', test_rows_statement(table_name));
        perform expect(seen = 'ok 0 rows ' || md5('') or seen like 'P0001 incorrect device secret',
            format('%s: a wrong device secret reads no %s', phase, table_name));
    end loop;

    perform expect(test_as('owner', test_upload_statement(test_device('A'))) = 'ok ' || test_device('A'),
        phase || ': a device can upload its own measurement');
    perform expect(test_as('other device', test_upload_statement(test_device('A'))) like '42501 %',
        phase || ': another device cannot upload as device A');
    perform expect(test_as('wrong secret', test_upload_statement(test_device('A'))) = 'P0001 incorrect device secret',
        phase || ': a wrong device secret cannot upload');
    perform expect(test_as('unregistered device', test_upload_statement(test_device('A'))) like 'P0001 device id % is not registered',
        phase || ': an unregistered device cannot upload');

    perform expect(
        (select bool_and(outcome = 'ok inserted') from test_outcomes
         where test_outcomes.phase = test_check_access.phase and caller = 'owner' and action like 'attach %'),
        phase || ': a device can attach data to its own measurement');
    perform expect(
        (select count(*) = 4 and bool_and(outcome like '42501 %') from test_outcomes
         where test_outcomes.phase = test_check_access.phase and caller = 'other device' and action like 'attach %'),
        phase || ': another device cannot attach upload/download, latency, location or cell rows to device A''s measurement');
    perform expect(
        (select bool_and(outcome not like 'ok%') from test_outcomes
         where test_outcomes.phase = test_check_access.phase and caller <> 'owner'
           and (action like 'attach %' or action = 'upload as device A')),
        phase || ': nobody else can write device A''s data');
end;
$$;

-- How long device A's next upload and the duplicate check of one of its throughput
-- measurements take. Both are rolled back.
create function test_timings(label text, out upload interval, out confirm interval)
language plpgsql as $$
begin
    upload := test_time('owner', test_upload_statement(test_device('A')));
    confirm := test_time('owner', test_confirm_statement(
        (select m.id from public.measurements m join public.upload_download_data ud on ud.measurement_id = m.id
         where m.device_id = test_device('A') order by m.id limit 1)));
    raise notice '% (device with % measurements): upload took % ms, duplicate check took % ms', label,
        (select count(*) from public.measurements where device_id = test_device('A')),
        round(extract(epoch from upload) * 1000), round(extract(epoch from confirm) * 1000);
end;
$$;

-- Whether a caller was refused both times: an error, a row level security violation, or no
-- rows.
create function test_refused(outcome text) returns boolean
language sql immutable as $$
    select outcome not like 'ok %' or outcome = 'ok 0 rows ' || md5('')
$$;

-- Before the migration: record and check what everyone can do, and time an upload.
do $$ begin perform test_record_outcomes('before'); end $$;
do $$ begin perform test_check_access('before'); end $$;
do $$ begin perform test_timings('live policies'); end $$;

\i /migrations/202610210001_fast_upload_rls.sql

do $$ begin perform test_record_outcomes('after'); end $$;
do $$ begin perform test_check_access('after'); end $$;

-- Callers without a valid device secret used to get the helper's error ("missing x-device-id
-- header", "incorrect device secret") when they read or wrote cells, locations, latency_data
-- or upload_download_data directly. Now that the checks run in a different order they may get
-- no rows or a row level security violation instead. Which one they got already depended on
-- the data and the query plan (with no measurements to look at, the helper never ran), and
-- they are refused either way. Everything else must be exactly as before.
do $$
declare
    changed record;
begin
    for changed in
        select b.caller, b.action, b.outcome as before, a.outcome as after
        from test_outcomes b join test_outcomes a using (caller, action)
        where b.phase = 'before' and a.phase = 'after' and a.outcome <> b.outcome
          and not (test_refused(b.outcome) and test_refused(a.outcome))
    loop
        raise notice 'changed: % / %: % -> %', changed.caller, changed.action, changed.before, changed.after;
    end loop;

    perform expect(
        (select count(*) from test_outcomes where phase = 'before') = 8 * 10
        and (select count(*) from test_outcomes where phase = 'after') = 8 * 10
        and not exists (
            select 1
            from test_outcomes b join test_outcomes a using (caller, action)
            where b.phase = 'before' and a.phase = 'after' and a.outcome <> b.outcome
              and not (test_refused(b.outcome) and test_refused(a.outcome))),
        'every caller can read and write exactly what it could before the migration');
    perform expect(
        not exists (
            select 1
            from test_outcomes b join test_outcomes a using (caller, action)
            where b.phase = 'before' and a.phase = 'after' and a.outcome <> b.outcome
              and b.caller not in ('wrong secret', 'unregistered device', 'wrong dashboard secret', 'no headers')),
        'devices and the dashboard get exactly the same responses as before');
    raise notice 'refusals that changed from the helper''s error to no rows or a row level security violation: %',
        (select count(*)
         from test_outcomes b join test_outcomes a using (caller, action)
         where b.phase = 'before' and a.phase = 'after' and a.outcome <> b.outcome);
end;
$$;

do $$
begin
    perform expect(
        exists (select 1 from pg_indexes where schemaname = 'public' and indexname = 'measurements_device_id_idx'),
        'measurements are indexed by device');
end;
$$;

do $$ begin perform test_timings('new policies'); end $$;

-- Now give device A 400 measurements. With the live policies an upload took about 12 seconds
-- at that size (issue #115), so it isn't timed here.
do $$ begin perform test_add_measurements(test_device('A'), 350); end $$;

do $$
declare
    timings record := test_timings('new policies');
begin
    perform expect(timings.upload < interval '1 second',
        'an upload from a device with 400 measurements takes under a second');
    perform expect(timings.confirm < interval '1 second',
        'the duplicate check for a device with 400 measurements takes under a second');
end;
$$;
