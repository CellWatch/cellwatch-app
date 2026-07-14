drop view if exists private.fcc_submissions_view;
alter table fcc_submissions alter column source_port type int using source_port::int;

create view private.fcc_submissions_view as
with

    locs as (
        select
            measurement_id,
            jsonb_agg(
                jsonb_build_object(
                    'timestamp', l.timestamp,
                    'latitude', l.lat,
                    'longitude', l.lon,
                    'horizontal_accuracy', l.accuracy,
                    'speed', l.speed,
                    'speed_accuracy', l.speed_accuracy
                )
            ) locations
        from locations l
        group by measurement_id
        having measurement_id is not null
    ),

    cells as (
        select
            measurement_id,
            jsonb_agg(
                jsonb_build_object(
                    'timestamp', c.timestamp,
                    'cell_id', c.cell_id,
                    'physical_cell_id', c.physical_cell_id,
                    'cell_connection', c.cell_connection,
                    'network_generation', c.network_generation,
                    'network_subtype', c.network_subtype,
                    'signal_strength', c.signal_strength,
                    'rssi', c.rssi,
                    'rsrp', c.rsrp,
                    'rsrq', c.rsrq,
                    'sinr', c.sinr,
                    'csi_rsrp', c.csi_rsrp,
                    'csi_rsrq', c.csi_rsrq,
                    'csi_sinr', c.csi_sinr,
                    'cqi', c.cqi,
                    'spectrum_band', c.spectrum_band,
                    'spectrum_bandwidth', c.spectrum_bandwidth,
                    'arfcn', c.arfcn
                )
            ) as cells
        from cells c
        group by measurement_id
        having measurement_id is not null
    ),

    upload_download_data as (
        select
            group_id,
            jsonb_object_agg(
                m.type,
                jsonb_build_object(
                        'timestamp', m.timestamp,
                        'warmup_duration', d.warmup_duration,
                        'warmup_bytes_transferred', d.warmup_bytes,
                        'bytes_transferred', d.bytes,
                        'bytes_sec', (case when d.duration = 0 and d.bytes = 0 then 0
                                            when d.duration = 0 then null
                                            else d.bytes::float / (d.duration::float / 1000000.0)
                                        end)::int,
                        'success', m.success,
                        'duration', d.duration,
                        'targets', d.servers,
                        'locations', l.locations,
                        'cells', c.cells,
                        'success_flag', m.success,
                        'carrier_aggregation_flag', m.carrier_aggregation,
                        'network_connected_flag', m.network_connected,
                        'network_available_flag', m.network_available,
                        'network_roaming_flag', m.network_roaming
                    )
            ) upload_download_test
        from measurements m
            inner join upload_download_data d on d.measurement_id = m.id
            left join locs l on l.measurement_id = m.id
            left join cells c on c.measurement_id = m.id
        group by group_id
    ),

    latency_data as (
        select
            group_id,
            jsonb_object_agg(
                m.type,
                jsonb_build_object(
                    'timestamp', m.timestamp,
                    'duration', m.duration,
                    'round_trip_time', l.rtt,
                    'jitter', l.jitter,
                    'packets_sent', l.sent,
                    'packets_received', l.received,
                    'locations', loc.locations,
                    'cells', c.cells,
                    'targets', l.servers,
                    'success_flag', m.success,
                    'carrier_aggregation_flag', m.carrier_aggregation,
                    'network_connected_flag', m.network_connected,
                    'network_available_flag', m.network_available,
                    'network_roaming_flag', m.network_roaming
                )
            ) latency_test
         from measurements m
              inner join latency_data l on l.measurement_id = m.id
              left join locs loc on loc.measurement_id = m.id
              left join cells c on c.measurement_id = m.id
         group by group_id
    ),

    tests as (
        select
            group_id,
            (coalesce(test_data.latency_test, '{}'::jsonb) || coalesce(test_data.upload_download_test, '{}'::jsonb)) tests
        from (
            select
                coalesce(upload_download_data.group_id, latency_data.group_id) group_id,
                latency_test,
                upload_download_test
            from latency_data
                left join upload_download_data on latency_data.group_id = upload_download_data.group_id) test_data
    )

select
    s.id as submission_id,
    s.submitted,
    jsonb_build_object(
        'submission_category', 'Consumer Challenge',
        'contact', jsonb_build_object(
            'name', s.contact_name,
            'email', s.contact_email,
            'phone', s.contact_phone
        ),
        'submissions', jsonb_build_array(jsonb_build_object(
            'test_id', s.id,
            'device_timestamp', s.device_timestamp,
            'server_timestamp', s.server_timestamp,
            'server_source_ip_address', s.source_ip,
            'server_source_port', s.source_port,
            'device_imei', s.device_imei,
            'device_type', s.device_type,
            'manufacturer', s.device_manufacturer,
            'model', s.device_model,
            'operating_system', s.device_os,
            'device_tac', s.device_tac,
            'device_id', s.device_id,
            'app_name', s.app_name,
            'app_version', s.app_version,
            'provider_name', s.provider,
            'sim_mobile_country_code', s.sim_country_code,
            'sim_mobile_network_code', s.sim_network_code,
            'net_mobile_country_code', s.net_country_code,
            'net_mobile_network_code', s.net_network_code,
            'in_vehicle_flag', s.in_vehicle,
            'external_antenna_flag', s.external_antenna,
            'scheduled_test_flag', false,
            'tests', tests.tests
        ))
    ) data
from fcc_submissions s
left join tests on s.id = tests.group_id;

create extension if not exists http with schema extensions;

alter table fcc_submissions add column if not exists submission jsonb;
alter table fcc_submissions add column if not exists submission_response text;

create table if not exists private.fcc_submission_info (
    env text primary key,
    url text not null,
    hash_secret_id uuid not null references vault.secrets
);

-- Need to run something like the following to setup:
--
-- insert into private.fcc_submission_info (env, url, hash_secret_id)
-- select 'demo', 'https://bdc-demo.fcc.gov/api/mobiletest/create', create_secret
-- from vault.create_secret('SECRET HASH VALUE');

create or replace function private.fcc_submission_is_ready(sub_id uuid)
    returns boolean
    language sql
    stable
    set search_path = public, private, extensions
as
$$
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

create or replace function private.post_fcc_submission(sub_id uuid, fcc_env text)
    returns bool
    language plpgsql
    set search_path = public, private, extensions
as
$$
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

create or replace function private.auto_post_fcc_submission()
returns trigger
language plpgsql
security definer
set search_path = public, private, extensions
as
$$
declare
    env text := 'prod';
    post_succeeded boolean;
    response_text text;
begin
    if coalesce(new.submitted, false) = false then
        begin
            post_succeeded := private.post_fcc_submission(new.id, env);

            if post_succeeded is distinct from true then
                raise exception 'FCC submission function returned false for submission_id=%', new.id;
            end if;

            select s.submission_response
            into response_text
            from public.fcc_submissions s
            where s.id = new.id;

            insert into private.fcc_submission_failures (
                submission_id,
                measurement_group_id,
                error_message,
                succeeded,
                fcc_env,
                response_snippet
            )
            values (
                new.id,
                new.id,
                null,
                true,
                env,
                left(coalesce(response_text, ''), 500)
            );

            raise info 'FCC auto-submit succeeded for submission_id=%', new.id;

        exception when others then
            insert into private.fcc_submission_failures (
                submission_id,
                measurement_group_id,
                error_message,
                succeeded,
                fcc_env,
                response_snippet
            )
            values (
                new.id,
                new.id,
                sqlerrm,
                false,
                env,
                left(sqlerrm, 500)
            );

            raise warning 'FCC auto-submit failed for submission_id=%; see private.fcc_submission_failures', new.id;
        end;
    end if;

    return new;
end;
$$;
