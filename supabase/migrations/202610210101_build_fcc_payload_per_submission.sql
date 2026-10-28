begin;

-- Build the FCC payload for one submission instead of for the whole database (#116).
--
-- private.fcc_submissions_view aggregated every row of public.locations and public.cells
-- into JSON (its locs and cells CTEs are each used twice, so Postgres builds them in full)
-- and only then picked out the one submission asked for. On 2026-10-21 one 6 KB payload
-- took 25.5 s to build on the live database. post_fcc_submission builds a payload for every
-- post, and the retry job stops taking entries after 60 seconds, so it managed about two
-- posts per run, and long manual runs hit statement_timeout after the FCC had already
-- accepted posts (#117).
--
-- private.fcc_submission_payload(sub_id) builds the same JSON from only that submission's
-- measurements, locations and cells. The JSON is the same as the view's: same keys, values,
-- number formatting and rounding, and the same order (supabase/db_tests/fcc_payload_test.sql
-- compares the two). The view's jsonb_agg calls had no ORDER BY, so they listed locations
-- and cells in the order its scans read the rows, which is the order they are stored: the
-- order they were inserted, as the phone recorded them. The function orders them by ctid
-- (where each row is stored), so it lists them in that same order whatever plan Postgres
-- picks. Sorting by timestamp instead would change the payloads posted to the FCC: the cells
-- from one scan share a timestamp, so most tests with several cells would come out in a
-- different order.

-- Readiness checks (private.fcc_submission_is_ready) and the payload both look up a
-- submission's measurements by group_id, which had no index and was read with a full scan
-- of public.measurements. The index is built without CONCURRENTLY, which can't run inside
-- the migration's transaction, so uploads of new measurements wait while it builds. At the
-- live table's size that should take seconds.
create index if not exists measurements_group_id_idx
    on public.measurements using btree (group_id);

-- Security definer so it can read every measurement regardless of RLS, like the view did
-- (the view runs as its owner). Only the posting functions and readonly_user (which can
-- already read every payload through the view) may call it. pg_temp comes last in its
-- search_path: otherwise Postgres looks up type names in the caller's temporary schema first,
-- and a caller could make it run their own code as its owner with a temporary domain named
-- jsonb. The same goes for every function below that sets search_path.
create or replace function private.fcc_submission_payload(sub_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public, private, extensions, pg_temp
as
$$
    with
    group_measurements as (
        select m.*
        from public.measurements m
        where m.group_id = sub_id
    ),

    location_lists as (
        select
            l.measurement_id,
            jsonb_agg(
                jsonb_build_object(
                    'timestamp', l.timestamp,
                    'latitude', l.lat,
                    'longitude', l.lon,
                    'horizontal_accuracy', l.accuracy,
                    'speed', l.speed,
                    'speed_accuracy', l.speed_accuracy
                )
                order by l.ctid
            ) as locations
        from public.locations l
        where l.measurement_id in (select gm.id from group_measurements gm)
        group by l.measurement_id
    ),

    cell_lists as (
        select
            c.measurement_id,
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
                order by c.ctid
            ) as cells
        from public.cells c
        where c.measurement_id in (select gm.id from group_measurements gm)
        group by c.measurement_id
    ),

    -- Keyed by measurement type, like the view: any measurement in the group with
    -- upload/download data becomes a test under its own type.
    throughput_tests as (
        select
            m.group_id,
            jsonb_object_agg(
                m.type,
                jsonb_build_object(
                    'timestamp', m.timestamp,
                    'warmup_duration', d.warmup_duration,
                    'warmup_bytes_transferred', d.warmup_bytes,
                    'bytes_transferred', d.bytes,
                    'bytes_sec', (
                        case
                            when d.duration = 0 and d.bytes = 0 then 0::double precision
                            when d.duration = 0 then null::double precision
                            else d.bytes::double precision / (d.duration::double precision / 1000000.0::double precision)
                        end
                    )::integer,
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
            ) as upload_download_test
        from group_measurements m
            join public.upload_download_data d on d.measurement_id = m.id
            left join location_lists l on l.measurement_id = m.id
            left join cell_lists c on c.measurement_id = m.id
        group by m.group_id
    ),

    latency_tests as (
        select
            m.group_id,
            jsonb_object_agg(
                m.type,
                jsonb_build_object(
                    'timestamp', m.timestamp,
                    'duration', m.duration,
                    'round_trip_time', ld.rtt,
                    'jitter', ld.jitter,
                    'packets_sent', ld.sent,
                    'packets_received', ld.received,
                    'locations', l.locations,
                    'cells', c.cells,
                    'targets', ld.servers,
                    'success_flag', m.success,
                    'carrier_aggregation_flag', m.carrier_aggregation,
                    'network_connected_flag', m.network_connected,
                    'network_available_flag', m.network_available,
                    'network_roaming_flag', m.network_roaming
                )
            ) as latency_test
        from group_measurements m
            join public.latency_data ld on ld.measurement_id = m.id
            left join location_lists l on l.measurement_id = m.id
            left join cell_lists c on c.measurement_id = m.id
        group by m.group_id
    ),

    -- As in the view, a group without latency data has no tests at all ("tests": null).
    tests as (
        select coalesce(lt.latency_test, '{}'::jsonb) || coalesce(tt.upload_download_test, '{}'::jsonb) as tests
        from latency_tests lt
            left join throughput_tests tt on tt.group_id = lt.group_id
    )

    select jsonb_build_object(
        'submission_category', 'Consumer Challenge',
        'contact', jsonb_build_object(
            'name', s.contact_name,
            'email', s.contact_email,
            'phone', s.contact_phone
        ),
        'submissions', jsonb_build_array(
            jsonb_build_object(
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
                'tests', t.tests
            )
        )
    )
    from public.fcc_submissions s
        left join tests t on true
    where s.id = sub_id;
$$;

revoke all on function private.fcc_submission_payload(uuid) from public;

-- Keep the view for ad hoc queries by readonly_user, built on the function so there is only
-- one definition of the payload and a lookup by submission_id is fast. Same columns as
-- before, so create or replace keeps its grants.
create or replace view private.fcc_submissions_view as
select
    s.id as submission_id,
    s.submitted,
    private.fcc_submission_payload(s.id) as data
from public.fcc_submissions s;

-- Views call functions with the privileges of the user querying the view.
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'readonly_user') then
        grant execute on function private.fcc_submission_payload(uuid) to readonly_user;
    end if;
end;
$$;

-- The one place that talks to the FCC. post_fcc_submission called http() directly; going
-- through this function lets the database tests replace the request with a stand-in, so
-- they can exercise the real posting code without sending anything. Same request as
-- before: a POST with the hash_value header and a JSON body.
create or replace function private.fcc_http_post(
    p_url text,
    p_hash_value text,
    p_body text,
    out status integer,
    out content text
)
language plpgsql
set search_path = public, private, extensions, pg_temp
as
$$
begin
    select r.status, r.content
    into status, content
    from http((
        'POST',
        p_url,
        array[http_header('hash_value', p_hash_value)],
        'application/json',
        p_body
    )::http_request) r;
end;
$$;

revoke all on function private.fcc_http_post(text, text, text) from public;

-- Changes from the live function (marked "Changed" below): the payload comes from
-- private.fcc_submission_payload, and the request goes through private.fcc_http_post.
-- Error messages are unchanged; the retry job relies on 'FCC HTTP status NNN for
-- submission ...'.
create or replace function private.post_fcc_submission(sub_id uuid, fcc_env text)
returns boolean
language plpgsql
set search_path = public, private, extensions, pg_temp
as
$$
declare
    info record;
    payload jsonb;
    tests_payload jsonb;
    response record;
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

    -- Changed: built for this submission only.
    payload := private.fcc_submission_payload(sub_id);

    if payload is null then
        raise exception 'no FCC submission payload found with id %', sub_id;
    end if;

    tests_payload := payload #> '{submissions,0,tests}';
    if jsonb_typeof(tests_payload) is distinct from 'object'
       or not (tests_payload ?& array['latency', 'download', 'upload']) then
        raise exception 'FCC submission % produced an incomplete tests payload', sub_id;
    end if;

    -- Do not log the payload: it contains contact, device, and location data.
    raise info 'posting FCC submission_id=% env=%', sub_id, fcc_env;

    -- Changed: through the wrapper instead of calling http() here.
    select h.status, h.content into response
    from private.fcc_http_post(info.url, info.decrypted_secret, payload::text) h;

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

commit;
