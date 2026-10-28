begin;

-- Fixes for the map dashboard's RPCs.
--
-- rpc_group_detail(group_id): its parameter has the same name as the measurements.group_id
-- column. Inside a SQL function the column wins, so `where group_id = group_id::uuid` compared
-- the column with itself and the function summarised every measurement in the database instead
-- of the requested group. The references are now qualified. The parameter keeps its name
-- because PostgREST callers pass it by name ({"group_id": ...}).
--
-- All three are SECURITY DEFINER (they bypass row-level security) and are granted to anon, so
-- anyone holding the anon key that ships in the app could read every device's locations and
-- results. They now refuse to run unless the request carries the dashboard's shared secret
-- (x-dashboard-secret), the same check the dashboard RLS policies use.
--
-- latency_data.rtt and .jitter are in microseconds (the app stores MSAK's values, and the FCC
-- payload uses the same unit). rpc_map_points already converted them to the milliseconds its
-- ping_ms/jitter_ms names promise; rpc_group_detail and rpc_group_summaries now do too, so
-- their lat_min/lat_max filters are also in milliseconds.
--
-- Speeds: when a row has neither bytes_per_sec nor application_bytes_per_sec, all three fell back
-- to bytes / duration. duration is in microseconds (as in the FCC payload view, which divides by
-- duration / 1e6), so that fallback came out a million times too small. It now converts to
-- seconds first.

create or replace function private.require_dashboard_request() returns void
    language plpgsql stable security definer
    set search_path = private, pg_temp
    as $$
begin
    if not private.is_dashboard_request() then
        raise exception 'dashboard secret required'
            using errcode = '42501', hint = 'Send the x-dashboard-secret header.';
    end if;
end
$$;

revoke all on function private.require_dashboard_request() from public;

CREATE OR REPLACE FUNCTION "public"."rpc_group_detail"("group_id" "text") RETURNS "jsonb"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
select private.require_dashboard_request();
with m as (
  select *
  from measurements
  where measurements.group_id = rpc_group_detail.group_id::uuid
),
latest_loc as (
  select distinct on (measurement_id)
    measurement_id, lat, lon, accuracy, timestamp
  from locations
  where measurement_id in (select id from m)
  order by measurement_id, timestamp desc
),
latest_cell as (
  select distinct on (measurement_id)
    measurement_id, network_generation, rsrp, sinr, rsrq, rssi, signal_strength, timestamp
  from cells
  where measurement_id in (select id from m)
  order by measurement_id, timestamp desc
),
ud as (
  select *
  from upload_download_data
  where measurement_id in (select id from m)
),
ld as (
  select *
  from latency_data
  where measurement_id in (select id from m)
),
parts as (
  select
    m.id::text as measurement_id,
    m.type,
    m.timestamp as ts,
    m.provider,
    ll.lat,
    ll.lon,
    ll.accuracy as accuracy_m,
    lc.network_generation,
    lc.rsrp,
    lc.sinr,
    case
      when m.type in ('download','upload') then
        (coalesce(ud.bytes_per_sec, ud.application_bytes_per_sec, (ud.bytes / (nullif(ud.duration, 0) / 1e6))) * 8.0) / 1e6
      else null
    end as mbps,
    case when m.type = 'latency' then (ld.rtt / 1000.0) end as ping_ms,
    case when m.type = 'latency' then (ld.jitter / 1000.0) end as jitter_ms,
    case
      when m.type = 'latency' and ld.sent is not null and ld.received is not null and ld.sent > 0
        then (100.0 * greatest(0, ld.sent - ld.received)) / ld.sent
      else null
    end as loss_pct
  from m
  left join latest_loc ll on ll.measurement_id = m.id
  left join latest_cell lc on lc.measurement_id = m.id
  left join ud on ud.measurement_id = m.id
  left join ld on ld.measurement_id = m.id
),
pick as (
  select
    max(ts) as ts,
    coalesce(max(provider) filter (where provider is not null), 'Unknown') as provider,
    avg(lon) filter (where lon is not null) as lon,
    avg(lat) filter (where lat is not null) as lat,
    min(accuracy_m) filter (where accuracy_m is not null) as accuracy_m,
    max(network_generation) as network_generation,
    max(rsrp) as rsrp,
    max(sinr) as sinr
  from parts
),
dl as (
  select mbps from parts where type='download' order by ts desc nulls last limit 1
),
ul as (
  select mbps from parts where type='upload' order by ts desc nulls last limit 1
),
la as (
  select ping_ms, jitter_ms, loss_pct from parts where type='latency' order by ts desc nulls last limit 1
)
select jsonb_build_object(
  'group_id', rpc_group_detail.group_id,
  'timestamp', (select ts from pick),
  'provider', (select provider from pick),
  'location', jsonb_build_object(
    'lat', (select lat from pick),
    'lon', (select lon from pick),
    'accuracy_m', (select accuracy_m from pick)
  ),
  'cell', jsonb_build_object(
    'network_generation', (select network_generation from pick),
    'rsrp', (select rsrp from pick),
    'sinr', (select sinr from pick)
  ),
  'tests', jsonb_build_object(
    'download', jsonb_build_object('mbps', (select mbps from dl)),
    'upload', jsonb_build_object('mbps', (select mbps from ul)),
    'latency', jsonb_build_object(
      'rtt_ms', (select ping_ms from la),
      'jitter_ms', (select jitter_ms from la),
      'loss_pct', (select loss_pct from la)
    )
  )
);
$$;


CREATE OR REPLACE FUNCTION "public"."rpc_group_summaries"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone DEFAULT NULL::timestamp with time zone, "t_to" timestamp with time zone DEFAULT NULL::timestamp with time zone, "providers" "text"[] DEFAULT NULL::"text"[], "conn" "text"[] DEFAULT NULL::"text"[], "dl_min" double precision DEFAULT NULL::double precision, "dl_max" double precision DEFAULT NULL::double precision, "ul_min" double precision DEFAULT NULL::double precision, "ul_max" double precision DEFAULT NULL::double precision, "lat_min" double precision DEFAULT NULL::double precision, "lat_max" double precision DEFAULT NULL::double precision, "lim" integer DEFAULT 50000) RETURNS SETOF "jsonb"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
select private.require_dashboard_request();
with latest_loc as (
  select distinct on (measurement_id)
    measurement_id, lat, lon, accuracy, timestamp
  from locations
  order by measurement_id, timestamp desc
),
latest_cell as (
  select distinct on (measurement_id)
    measurement_id, network_generation, rsrp, sinr, rsrq, rssi, signal_strength, timestamp
  from cells
  order by measurement_id, timestamp desc
),
meas as (
  select
    m.id,
    m.group_id,
    m.provider,
    m.type,
    m.timestamp as ts,
    ll.lat,
    ll.lon,
    ll.accuracy as accuracy_m,
    lc.network_generation,
    case
      when m.type = 'download' then
        (coalesce(ud.bytes_per_sec, ud.application_bytes_per_sec, (ud.bytes / (nullif(ud.duration, 0) / 1e6))) * 8.0) / 1e6
      else null
    end as dl_mbps,
    case
      when m.type = 'upload' then
        (coalesce(ud.bytes_per_sec, ud.application_bytes_per_sec, (ud.bytes / (nullif(ud.duration, 0) / 1e6))) * 8.0) / 1e6
      else null
    end as ul_mbps,
    case when m.type = 'latency' then (ld.rtt / 1000.0) end as ping_ms,
    case when m.type = 'latency' then (ld.jitter / 1000.0) end as jitter_ms,
    case
      when m.type = 'latency' and ld.sent is not null and ld.received is not null and ld.sent > 0
        then (100.0 * greatest(0, ld.sent - ld.received)) / ld.sent
      else null
    end as loss_pct
  from measurements m
  left join latest_loc ll on ll.measurement_id = m.id
  left join latest_cell lc on lc.measurement_id = m.id
  left join upload_download_data ud on ud.measurement_id = m.id
  left join latency_data ld on ld.measurement_id = m.id
  where m.group_id is not null
),
gs as (
  select
    group_id,
    max(ts) as ts,
    coalesce(max(provider) filter (where provider is not null), 'Unknown') as provider,
    case
      when max(network_generation) ilike '%NR%' or max(network_generation) ilike '%5%' then '5G'
      when max(network_generation) ilike '%LTE%' or max(network_generation) ilike '%4%' then '4G'
      else 'Other'
    end as conn_tag,
    avg(lon) filter (where lon is not null) as lon,
    avg(lat) filter (where lat is not null) as lat,
    min(accuracy_m) filter (where accuracy_m is not null) as accuracy_m,
    max(dl_mbps) as dl_mbps,
    max(ul_mbps) as ul_mbps,
    max(ping_ms) as ping_ms,
    max(jitter_ms) as jitter_ms,
    max(loss_pct) as loss_pct
  from meas
  group by group_id
),
f as (
  select *
  from gs
  where
    lon between min_lng and max_lng
    and lat between min_lat and max_lat
    and (t_from is null or ts >= t_from)
    and (t_to is null or ts <= t_to)
    and (providers is null or cardinality(providers) = 0 or provider = any(providers))
    and (conn is null or cardinality(conn) = 0 or conn_tag = any(conn))
    and (dl_min is null or (dl_mbps is not null and dl_mbps >= dl_min))
    and (dl_max is null or (dl_mbps is not null and dl_mbps <= dl_max))
    and (ul_min is null or (ul_mbps is not null and ul_mbps >= ul_min))
    and (ul_max is null or (ul_mbps is not null and ul_mbps <= ul_max))
    and (lat_min is null or (ping_ms is not null and ping_ms >= lat_min))
    and (lat_max is null or (ping_ms is not null and ping_ms <= lat_max))
    and lon is not null and lat is not null
  order by ts desc nulls last
  limit greatest(1, least(coalesce(lim, 50000), 200000))
)
select jsonb_build_object(
  'group_id', f.group_id::text,
  'provider', f.provider,
  'conn_tag', f.conn_tag,
  'timestamp', f.ts,
  'lon', f.lon,
  'lat', f.lat,
  'accuracy_m', f.accuracy_m,
  'dl_mbps', f.dl_mbps,
  'ul_mbps', f.ul_mbps,
  'ping_ms', f.ping_ms,
  'jitter_ms', f.jitter_ms,
  'loss_pct', f.loss_pct
)
from f;
$$;


CREATE OR REPLACE FUNCTION "public"."rpc_map_points"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone DEFAULT NULL::timestamp with time zone, "t_to" timestamp with time zone DEFAULT NULL::timestamp with time zone, "providers" "text"[] DEFAULT NULL::"text"[], "conn" "text"[] DEFAULT NULL::"text"[], "dl_min" double precision DEFAULT NULL::double precision, "dl_max" double precision DEFAULT NULL::double precision, "ul_min" double precision DEFAULT NULL::double precision, "ul_max" double precision DEFAULT NULL::double precision, "lat_min" double precision DEFAULT NULL::double precision, "lat_max" double precision DEFAULT NULL::double precision, "lim" integer DEFAULT 2000) RETURNS SETOF "jsonb"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
select private.require_dashboard_request();
with latest_loc as (
  select distinct on (measurement_id)
    measurement_id, lat, lon, accuracy, timestamp
  from locations
  order by measurement_id, timestamp desc
),
latest_cell as (
  select distinct on (measurement_id)
    measurement_id, network_generation, rsrp, sinr, rsrq, rssi, signal_strength, timestamp
  from cells
  order by measurement_id, timestamp desc
),
meas as (
  select
    m.id,
    m.group_id,
    m.provider,
    m.type,
    m.timestamp as ts,
    ll.lat,
    ll.lon,
    ll.accuracy as accuracy_m,
    lc.network_generation,
    case
      when m.type = 'download' then
        (coalesce(ud.bytes_per_sec, ud.application_bytes_per_sec, (ud.bytes / (nullif(ud.duration, 0) / 1e6))) * 8.0) / 1e6
      else null
    end as dl_mbps,
    case
      when m.type = 'upload' then
        (coalesce(ud.bytes_per_sec, ud.application_bytes_per_sec, (ud.bytes / (nullif(ud.duration, 0) / 1e6))) * 8.0) / 1e6
      else null
    end as ul_mbps,
    case when m.type = 'latency' then (ld.rtt / 1000.0) end as ping_ms,
    case when m.type = 'latency' then (ld.jitter / 1000.0) end as jitter_ms,
    case
      when m.type = 'latency' and ld.sent is not null and ld.received is not null and ld.sent > 0
        then (100.0 * greatest(0, ld.sent - ld.received)) / ld.sent
      else null
    end as loss_pct
  from measurements m
  left join latest_loc ll on ll.measurement_id = m.id
  left join latest_cell lc on lc.measurement_id = m.id
  left join upload_download_data ud on ud.measurement_id = m.id
  left join latency_data ld on ld.measurement_id = m.id
  where m.group_id is not null
),
gs as (
  select
    group_id,
    max(ts) as ts,
    coalesce(max(provider) filter (where provider is not null), 'Unknown') as provider,
    case
      when max(network_generation) ilike '%NR%' or max(network_generation) ilike '%5%' then '5G'
      when max(network_generation) ilike '%LTE%' or max(network_generation) ilike '%4%' then '4G'
      else 'Other'
    end as conn_tag,
    avg(lon) filter (where lon is not null) as lon,
    avg(lat) filter (where lat is not null) as lat,
    min(accuracy_m) filter (where accuracy_m is not null) as accuracy_m,
    max(dl_mbps) as dl_mbps,
    max(ul_mbps) as ul_mbps,
    max(ping_ms) as ping_ms,
    max(jitter_ms) as jitter_ms,
    max(loss_pct) as loss_pct
  from meas
  group by group_id
),
f as (
  select *
  from gs
  where
    lon between min_lng and max_lng
    and lat between min_lat and max_lat
    and (t_from is null or ts >= t_from)
    and (t_to is null or ts <= t_to)
    and (providers is null or cardinality(providers) = 0 or provider = any(providers))
    and (conn is null or cardinality(conn) = 0 or conn_tag = any(conn))
    and (dl_min is null or (dl_mbps is not null and dl_mbps >= dl_min))
    and (dl_max is null or (dl_mbps is not null and dl_mbps <= dl_max))
    and (ul_min is null or (ul_mbps is not null and ul_mbps >= ul_min))
    and (ul_max is null or (ul_mbps is not null and ul_mbps <= ul_max))
    and (lat_min is null or (ping_ms is not null and ping_ms >= lat_min))
    and (lat_max is null or (ping_ms is not null and ping_ms <= lat_max))
    and lon is not null and lat is not null
  order by ts desc nulls last
  limit greatest(1, least(coalesce(lim, 2000), 20000))
)
select jsonb_build_object(
  'group_id', f.group_id::text,
  'provider', f.provider,
  'conn_tag', f.conn_tag,
  'timestamp', f.ts,
  'center', jsonb_build_array(f.lon, f.lat),
  'stats', jsonb_build_object(
    'down_mbps', f.dl_mbps,
    'up_mbps', f.ul_mbps,
    'ping_ms', f.ping_ms,
    'jitter_ms', f.jitter_ms,
    'loss_pct', f.loss_pct
  )
)
from f;
$$;

commit;
