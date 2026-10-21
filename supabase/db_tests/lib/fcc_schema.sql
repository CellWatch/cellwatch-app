-- Shared setup for the FCC pipeline tests: copies of the live tables, triggers, view and
-- functions involved in posting to the FCC, as in supabase/full_schema.sql (the snapshot of
-- the live schema), plus the newest live retry function from 202610070001. Tests include it
-- with \i /db_tests/lib/fcc_schema.sql and then load the migrations they test. run.sh only
-- runs files named *_test.sql, so this file is never run on its own.
--
-- Differences from the live schema:
--   * uuid_generate_v4() defaults are gen_random_uuid(), so uuid-ossp isn't needed
--   * vault.decrypted_secrets is a plain stand-in view over a stand-in vault.secrets table
--   * no RLS policies, grants or published.* tables

create schema if not exists private;
create schema if not exists extensions;

-- The live project has the http extension in schema extensions. Load it when the image has
-- it, so the http() calls compile as they do on the live project. Nothing reaches the
-- network: the tests replace the HTTP call, and the container has no network anyway.
do $$
begin
    if exists (select 1 from pg_available_extensions where name = 'http') then
        create extension if not exists http with schema extensions;
    end if;
end;
$$;

create schema if not exists vault;
create table vault.secrets (
    id uuid primary key default gen_random_uuid(),
    name text,
    secret text not null
);
create view vault.decrypted_secrets as
    select id, name, secret as decrypted_secret from vault.secrets;

create type public.connection_type as enum ('NONE', 'WIFI', 'CELLULAR');

create table public.cells (
    id uuid default gen_random_uuid() not null primary key,
    "timestamp" timestamptz default now() not null,
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
    measurement_id uuid,
    created_on timestamptz default now() not null,
    updated_on timestamptz default now() not null
);
create index cells_measurement_id_idx on public.cells using btree (measurement_id);

create table public.latency_data (
    id uuid default gen_random_uuid() not null primary key,
    measurement_id uuid unique,
    rtt numeric,
    jitter numeric,
    sent numeric,
    received numeric,
    servers text[],
    created_on timestamptz default now() not null,
    updated_on timestamptz default now() not null
);

create table public.locations (
    id uuid default gen_random_uuid() not null primary key,
    "timestamp" timestamptz default now() not null,
    lat double precision,
    lon double precision,
    accuracy double precision,
    speed double precision,
    speed_accuracy double precision,
    heading double precision,
    measurement_id uuid,
    created_on timestamptz default now() not null,
    updated_on timestamptz default now() not null
);
create index locations_measurement_id_idx on public.locations using btree (measurement_id);

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
    "timestamp" timestamptz default now() not null,
    duration numeric,
    scheduled boolean,
    success boolean,
    carrier_aggregation boolean,
    network_connected boolean,
    network_available boolean,
    network_roaming boolean,
    extra_data jsonb,
    created_on timestamptz default now() not null,
    updated_on timestamptz default now() not null,
    sim_mobile_country_code text,
    sim_mobile_network_code text,
    net_mobile_country_code text,
    net_mobile_network_code text,
    connection_type public.connection_type,
    cellular_data_enabled boolean,
    app_version text,
    constraint type_in_scope check ((type)::text = any (array['download', 'upload', 'latency']))
);

create table public.upload_download_data (
    id uuid default gen_random_uuid() not null primary key,
    measurement_id uuid unique,
    warmup_duration numeric,
    warmup_bytes numeric,
    duration numeric,
    bytes numeric,
    servers text[],
    created_on timestamptz default now() not null,
    updated_on timestamptz default now() not null,
    application_bytes numeric,
    bytes_per_sec numeric,
    application_bytes_per_sec numeric
);

create table public.fcc_submissions (
    id uuid default gen_random_uuid() not null primary key,
    contact_name text,
    contact_email text,
    contact_phone text,
    device_timestamp timestamptz,
    server_timestamp timestamptz default now(),
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
    created_on timestamptz default now() not null,
    updated_on timestamptz default now() not null,
    submission jsonb,
    submitted boolean default false,
    submitted_on timestamptz,
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

create table private.fcc_submission_failures (
    id uuid default gen_random_uuid() not null primary key,
    submission_id uuid not null,
    measurement_group_id uuid,
    error_message text,
    occurred_on timestamptz default now() not null,
    succeeded boolean default false not null,
    fcc_env text,
    response_status integer,
    response_snippet text
);

create table private.fcc_submission_info (
    env text not null primary key,
    url text not null,
    hash_secret_id uuid not null references vault.secrets (id)
);

create table private.fcc_submission_retry_queue (
    submission_id uuid not null primary key
        references public.fcc_submissions (id) on delete cascade,
    fcc_env text default 'prod' not null,
    state text default 'pending' not null,
    attempt_count integer default 0 not null,
    next_attempt_at timestamptz default now() not null,
    last_attempt_at timestamptz,
    last_error text,
    created_on timestamptz default now() not null,
    updated_on timestamptz default now() not null,
    constraint fcc_submission_retry_queue_state_check check (state = any (array['pending', 'blocked']))
);

-- Live trigger functions and triggers on fcc_submissions.
create function public.set_updated_on() returns trigger
language plpgsql as $$
begin
  new.updated_on = now();
  return new;
end
$$;

create function public.force_created_on() returns trigger
language plpgsql as $$
begin
    NEW.created_on = now();
    NEW.updated_on = now();
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

create trigger _100_fcc_submission_set_updated_on before update on public.fcc_submissions
    for each row execute function public.set_updated_on();
create trigger _200_fcc_submission_force_created_on before insert on public.fcc_submissions
    for each row when (new.created_on is null) execute function public.force_created_on();
create trigger _300_fcc_submission_update_source_ip before insert on public.fcc_submissions
    for each row when (new.source_ip is null) execute function public.fcc_submission_update_source_ip();
create trigger trg_auto_post_fcc_submission after insert on public.fcc_submissions
    for each row execute function private.auto_post_fcc_submission();

-- Live readiness check, unchanged by the 2026-10-21 migrations.
create function private.fcc_submission_is_ready(sub_id uuid) returns boolean
language sql stable
set search_path to 'public', 'private', 'extensions'
as $$
    select count(*) = 3
       and count(distinct lower(m.type::text)) = 3
    from public.measurements m
    where m.group_id = sub_id
      and (
        (
          lower(m.type::text) = 'latency'
          and exists (
            select 1
            from public.latency_data ld
            where ld.measurement_id = m.id
          )
        )
        or
        (
          lower(m.type::text) in ('download', 'upload')
          and exists (
            select 1
            from public.upload_download_data ud
            where ud.measurement_id = m.id
          )
        )
      );
$$;

-- Live payload view, as in full_schema.sql (formatting aside).
create view private.fcc_submissions_view as
 WITH locs AS (
         SELECT l.measurement_id,
            jsonb_agg(jsonb_build_object('timestamp', l."timestamp", 'latitude', l.lat, 'longitude', l.lon, 'horizontal_accuracy', l.accuracy, 'speed', l.speed, 'speed_accuracy', l.speed_accuracy)) AS locations
           FROM public.locations l
          GROUP BY l.measurement_id
         HAVING (l.measurement_id IS NOT NULL)
        ), cells AS (
         SELECT c.measurement_id,
            jsonb_agg(jsonb_build_object('timestamp', c."timestamp", 'cell_id', c.cell_id, 'physical_cell_id', c.physical_cell_id, 'cell_connection', c.cell_connection, 'network_generation', c.network_generation, 'network_subtype', c.network_subtype, 'signal_strength', c.signal_strength, 'rssi', c.rssi, 'rsrp', c.rsrp, 'rsrq', c.rsrq, 'sinr', c.sinr, 'csi_rsrp', c.csi_rsrp, 'csi_rsrq', c.csi_rsrq, 'csi_sinr', c.csi_sinr, 'cqi', c.cqi, 'spectrum_band', c.spectrum_band, 'spectrum_bandwidth', c.spectrum_bandwidth, 'arfcn', c.arfcn)) AS cells
           FROM public.cells c
          GROUP BY c.measurement_id
         HAVING (c.measurement_id IS NOT NULL)
        ), upload_download_data AS (
         SELECT m.group_id,
            jsonb_object_agg(m.type, jsonb_build_object('timestamp', m."timestamp", 'warmup_duration', d.warmup_duration, 'warmup_bytes_transferred', d.warmup_bytes, 'bytes_transferred', d.bytes, 'bytes_sec', (
                CASE
                    WHEN ((d.duration = (0)::numeric) AND (d.bytes = (0)::numeric)) THEN (0)::double precision
                    WHEN (d.duration = (0)::numeric) THEN NULL::double precision
                    ELSE ((d.bytes)::double precision / ((d.duration)::double precision / (1000000.0)::double precision))
                END)::integer, 'success', m.success, 'duration', d.duration, 'targets', d.servers, 'locations', l.locations, 'cells', c.cells, 'success_flag', m.success, 'carrier_aggregation_flag', m.carrier_aggregation, 'network_connected_flag', m.network_connected, 'network_available_flag', m.network_available, 'network_roaming_flag', m.network_roaming)) AS upload_download_test
           FROM (((public.measurements m
             JOIN public.upload_download_data d ON ((d.measurement_id = m.id)))
             LEFT JOIN locs l ON ((l.measurement_id = m.id)))
             LEFT JOIN cells c ON ((c.measurement_id = m.id)))
          GROUP BY m.group_id
        ), latency_data AS (
         SELECT m.group_id,
            jsonb_object_agg(m.type, jsonb_build_object('timestamp', m."timestamp", 'duration', m.duration, 'round_trip_time', l.rtt, 'jitter', l.jitter, 'packets_sent', l.sent, 'packets_received', l.received, 'locations', loc.locations, 'cells', c.cells, 'targets', l.servers, 'success_flag', m.success, 'carrier_aggregation_flag', m.carrier_aggregation, 'network_connected_flag', m.network_connected, 'network_available_flag', m.network_available, 'network_roaming_flag', m.network_roaming)) AS latency_test
           FROM (((public.measurements m
             JOIN public.latency_data l ON ((l.measurement_id = m.id)))
             LEFT JOIN locs loc ON ((loc.measurement_id = m.id)))
             LEFT JOIN cells c ON ((c.measurement_id = m.id)))
          GROUP BY m.group_id
        ), tests AS (
         SELECT test_data.group_id,
            (COALESCE(test_data.latency_test, '{}'::jsonb) || COALESCE(test_data.upload_download_test, '{}'::jsonb)) AS tests
           FROM ( SELECT COALESCE(upload_download_data.group_id, latency_data.group_id) AS group_id,
                    latency_data.latency_test,
                    upload_download_data.upload_download_test
                   FROM (latency_data
                     LEFT JOIN upload_download_data ON ((latency_data.group_id = upload_download_data.group_id)))) test_data
        )
 SELECT s.id AS submission_id,
    s.submitted,
    jsonb_build_object('submission_category', 'Consumer Challenge', 'contact', jsonb_build_object('name', s.contact_name, 'email', s.contact_email, 'phone', s.contact_phone), 'submissions', jsonb_build_array(jsonb_build_object('test_id', s.id, 'device_timestamp', s.device_timestamp, 'server_timestamp', s.server_timestamp, 'server_source_ip_address', s.source_ip, 'server_source_port', s.source_port, 'device_imei', s.device_imei, 'device_type', s.device_type, 'manufacturer', s.device_manufacturer, 'model', s.device_model, 'operating_system', s.device_os, 'device_tac', s.device_tac, 'device_id', s.device_id, 'app_name', s.app_name, 'app_version', s.app_version, 'provider_name', s.provider, 'sim_mobile_country_code', s.sim_country_code, 'sim_mobile_network_code', s.sim_network_code, 'net_mobile_country_code', s.net_country_code, 'net_mobile_network_code', s.net_network_code, 'in_vehicle_flag', s.in_vehicle, 'external_antenna_flag', s.external_antenna, 'scheduled_test_flag', false, 'tests', tests.tests))) AS data
   FROM (public.fcc_submissions s
     LEFT JOIN tests ON ((s.id = tests.group_id)));

-- Live posting function. Its body is only compiled when it runs, so it loads even where the
-- http extension (and its http_response type) is missing.
set check_function_bodies = off;
create function private.post_fcc_submission(sub_id uuid, fcc_env text) returns boolean
language plpgsql
set search_path to 'public', 'private', 'extensions'
as $$
declare
    info record;
    payload jsonb;
    tests_payload jsonb;
    response http_response;
begin
    if not exists (
        select 1
        from public.fcc_submissions s
        where s.id = sub_id
    ) then
        raise exception 'no FCC submission found with id %', sub_id;
    end if;

    if not private.fcc_submission_is_ready(sub_id) then
        raise exception 'FCC submission % is incomplete; latency, download, and upload data are required', sub_id;
    end if;

    select i.url, s.decrypted_secret into info
    from private.fcc_submission_info i
        join vault.decrypted_secrets s on i.hash_secret_id = s.id
    where i.env = fcc_env;

    if not found then
        raise exception 'missing FCC submission info for env %', fcc_env;
    end if;

    select v.data into payload
    from private.fcc_submissions_view v
    where v.submission_id = sub_id;

    if not found then
        raise exception 'no FCC submission payload found with id %', sub_id;
    end if;

    tests_payload := payload #> '{submissions,0,tests}';
    if jsonb_typeof(tests_payload) is distinct from 'object'
       or not (tests_payload ?& array['latency', 'download', 'upload']) then
        raise exception 'FCC submission % produced an incomplete tests payload', sub_id;
    end if;

    -- Do not log the payload: it contains contact, device, and location data.
    raise info 'posting FCC submission_id=% env=%', sub_id, fcc_env;

    select * into response from http((
        'POST',
        info.url,
        array[http_header('hash_value', info.decrypted_secret)],
        'application/json',
        payload::text
    )::http_request);

    if response.status is null then
        raise exception 'FCC submission % returned no HTTP status', sub_id;
    end if;

    if response.status < 200 or response.status >= 300 then
        raise exception 'FCC HTTP status % for submission %: %',
            response.status,
            sub_id,
            left(coalesce(response.content, ''), 500);
    end if;

    update public.fcc_submissions set
        submitted = true,
        submission = payload,
        submitted_on = now(),
        submission_response = response.content
    where id = sub_id;

    if not found then
        raise exception 'FCC submission % disappeared before it could be marked submitted', sub_id;
    end if;

    return true;
end;
$$;
reset check_function_bodies;

-- The newest live retry function (not in the snapshot yet).
\i /migrations/202610070001_unclog_fcc_retry_queue.sql

-- Pass/fail helper used by all FCC tests.
create function expect(ok boolean, description text) returns void
language plpgsql as $$
begin
    if ok is distinct from true then
        raise exception 'FAILED: %', description;
    end if;
    raise notice 'ok - %', description;
end;
$$;
