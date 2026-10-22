-- Fixes for the map dashboard's RPCs.
--
-- rpc_group_detail(group_id): its parameter has the same name as the measurements.group_id
-- column. Inside a SQL function the column wins, so `where group_id = group_id::uuid` compared
-- the column with itself and the function summarised every measurement in the database instead
-- of the requested group. The references are now qualified. The parameter keeps its name
-- because PostgREST callers pass it by name ({"group_id": ...}).

CREATE OR REPLACE FUNCTION "public"."rpc_group_detail"("group_id" "text") RETURNS "jsonb"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
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
        (coalesce(ud.bytes_per_sec, ud.application_bytes_per_sec, (ud.bytes / nullif(ud.duration, 0))) * 8.0) / 1e6
      else null
    end as mbps,
    case when m.type = 'latency' then ld.rtt end as ping_ms,
    case when m.type = 'latency' then ld.jitter end as jitter_ms,
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
