begin;

-- Precompute the dashboard's per-group summary instead of rebuilding it on every call.
--
-- rpc_map_points and rpc_group_summaries (202610220101) built a summary of every group in the
-- database on each call: the latest location and cell reading of every measurement (sorting all
-- 117k locations and 558k cell readings), joined to every measurement's results, aggregated
-- per group, and only then filtered by the map area. On prod that took about 950 ms with a
-- warm cache, for a city or a continent alike, and up to 7.5 s seen. The dashboard backend
-- calls them with the service-role key, which has no statement timeout of its own, so the
-- 8 s limit of the authenticator role applies: slower calls were cancelled and the map
-- returned HTTP 500. The backend also pages through results (offset/limit), and each page
-- rebuilt the whole summary again.
--
-- Now the same summary is a materialized view, private.dashboard_group_points, refreshed
-- every 5 minutes by the pg_cron job dashboard-group-points-refresh (concurrently, so the
-- functions can read it during a refresh). Both functions read it with the same filters,
-- limits and output as before, so each call is a lookup over about 23k rows. Groups with the
-- same timestamp are now ordered by group_id, so the backend's offset paging sees one order
-- (before, their order was arbitrary and could differ from one page's call to the next). The
-- trade-off: a new upload appears on the map after the next refresh, within 5 minutes,
-- instead of at once. rpc_group_detail is unchanged; it already filters by group.
--
-- The view depends on columns of measurements, locations, cells, latency_data and
-- upload_download_data. Changing the type of one of those columns needs the view dropped and
-- created again in the same migration.

-- The per-group summary as in 202610220101, with the tables schema-qualified.
create materialized view if not exists private.dashboard_group_points as
with latest_loc as (
  select distinct on (measurement_id)
    measurement_id, lat, lon, accuracy, timestamp
  from public.locations
  order by measurement_id, timestamp desc
),
latest_cell as (
  select distinct on (measurement_id)
    measurement_id, network_generation, rsrp, sinr, rsrq, rssi, signal_strength, timestamp
  from public.cells
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
  from public.measurements m
  left join latest_loc ll on ll.measurement_id = m.id
  left join latest_cell lc on lc.measurement_id = m.id
  left join public.upload_download_data ud on ud.measurement_id = m.id
  left join public.latency_data ld on ld.measurement_id = m.id
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
)
select * from gs
with data;

-- The unique index lets the view be refreshed concurrently.
create unique index if not exists dashboard_group_points_group_id_idx
    on private.dashboard_group_points (group_id);
create index if not exists dashboard_group_points_ts_idx
    on private.dashboard_group_points (ts desc nulls last, group_id);

revoke all on private.dashboard_group_points from public, anon, authenticated;

-- As in 202610220101, reading the view, with ties ordered by group_id.
CREATE OR REPLACE FUNCTION "public"."rpc_map_points"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone DEFAULT NULL::timestamp with time zone, "t_to" timestamp with time zone DEFAULT NULL::timestamp with time zone, "providers" "text"[] DEFAULT NULL::"text"[], "conn" "text"[] DEFAULT NULL::"text"[], "dl_min" double precision DEFAULT NULL::double precision, "dl_max" double precision DEFAULT NULL::double precision, "ul_min" double precision DEFAULT NULL::double precision, "ul_max" double precision DEFAULT NULL::double precision, "lat_min" double precision DEFAULT NULL::double precision, "lat_max" double precision DEFAULT NULL::double precision, "lim" integer DEFAULT 2000) RETURNS SETOF "jsonb"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
select private.require_dashboard_request();
with f as (
  select *
  from private.dashboard_group_points
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
  order by ts desc nulls last, group_id
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

-- As in 202610220101, reading the view, with ties ordered by group_id.
CREATE OR REPLACE FUNCTION "public"."rpc_group_summaries"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone DEFAULT NULL::timestamp with time zone, "t_to" timestamp with time zone DEFAULT NULL::timestamp with time zone, "providers" "text"[] DEFAULT NULL::"text"[], "conn" "text"[] DEFAULT NULL::"text"[], "dl_min" double precision DEFAULT NULL::double precision, "dl_max" double precision DEFAULT NULL::double precision, "ul_min" double precision DEFAULT NULL::double precision, "ul_max" double precision DEFAULT NULL::double precision, "lat_min" double precision DEFAULT NULL::double precision, "lat_max" double precision DEFAULT NULL::double precision, "lim" integer DEFAULT 50000) RETURNS SETOF "jsonb"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
select private.require_dashboard_request();
with f as (
  select *
  from private.dashboard_group_points
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
  order by ts desc nulls last, group_id
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

-- Every 5 minutes. cron.schedule updates the existing job with this name. Skipped where pg_cron
-- isn't installed, such as a fresh local database.
do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        perform cron.schedule(
            'dashboard-group-points-refresh',
            '*/5 * * * *',
            'refresh materialized view concurrently private.dashboard_group_points;'
        );
    end if;
end;
$$;

commit;
