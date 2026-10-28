# Supabase function (RPC) reference

Every SQL/PL/pgSQL function in the CellWatch database: signature, security mode, caller, and behaviour.

**Related:** [schema](schema.md) · [triggers-and-cron](triggers-and-cron.md) · [fcc-submission-pipeline](fcc-submission-pipeline.md) · [performance-notes](performance-notes.md) · [sync-and-upload](../app/sync-and-upload.md) · [identity-and-security](../architecture/identity-and-security.md)

Definitions are in [full_schema.sql](../../supabase/full_schema.sql) unless a migration is named. Prod matches `full_schema.sql` plus migration `202610070001` (read-only catalog check, 2026-10-22); #136's migrations are not pushed to prod yet. "Invoker" means `SECURITY INVOKER` (the default): RLS and grants of the calling role apply. "Definer" functions run as their owner, `postgres`, and bypass RLS.

## How the app calls Supabase

The app uses supabase-kt 1.0.3 (`postgrest-kt`) from [NetworkMeasurementDatasource.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/data/network/NetworkMeasurementDatasource.kt), always with the anon key (role `anon`).

| Call | HTTP | Device headers | Used by |
|---|---|---|---|
| `rpc("register_device", {device_id})` | `POST /rest/v1/rpc/register_device` | No (separate client without the header plugin) | `getClient()`, the first time no secret is stored |
| `rpc("insert_measurement", {in_measurement, in_measurement_data, in_latency_data, in_locations, in_cells})` | `POST /rest/v1/rpc/insert_measurement` | Yes | `MeasurementUploader.uploadMeasurements` |
| `postgrest["fcc_submissions"].insert(...)` | `POST /rest/v1/fcc_submissions` (returns the row) | Yes | `MeasurementUploader.uploadSubmissions` |
| `postgrest["measurements"].select("*,upload_download_data(*),latency_data(*),locations(*),cells(*)")` | `GET /rest/v1/measurements?...` | Yes | `getMeasurementById`, which has no caller on main |

Headers on every request from the main client:
- `X-Device-ID`, `X-Device-Secret`: read by `private.verified_device_id()` and the RLS policies.
- **Not on main:** `X-Local-Port` and `X-Transport`, added by the OkHttp network interceptor `LocalPortHeaders.kt` and stored by the `_310` trigger. Both are uncommitted on the `client-port-header` branch (main's client uses the Ktor CIO engine), and the trigger's migration, `202610130001`, is not pushed to prod.

A unique-violation error (`duplicate key value violates unique constraint`) becomes `DuplicateKeyError`, which the uploader treats as "already on the server". The app also calls the EC2 tuple service for FCC source IP/port. That service is not Supabase; see [data-flow](../architecture/data-flow.md).

## App-facing functions

### `register_device`

`public.register_device(device_id uuid) RETURNS text`. **Definer.** No `SET search_path` (**fixed in #136**, which pins it; live once migration `202610220102` is pushed to prod). Grants: `anon`, `authenticated`.

- Makes a random 32-byte secret (`gen_random_bytes`, base64) and inserts `(device_id, crypt(secret, gen_salt('bf')))` into `private.devices` with `ON CONFLICT DO NOTHING`.
- Returns the plaintext secret **once**. If the id already exists, it raises `device id % already registered`.
- No auth required: anyone with the anon key can register a new id.
- If the phone loses the response, that device id can never be registered again. A fix is on an unpushed local branch, `register-device-retry` (migration `202610220001_retry_safe_device_registration.sql`, which adds a `register_device(device_id, device_secret)` overload; not on main, not pushed to prod). That migration must be pushed to prod before any app release that includes the fix ([migrations-and-deploy](migrations-and-deploy.md#ordering-rule-server-first)).

### `insert_measurement`

`public.insert_measurement(in_measurement measurements, in_measurement_data upload_download_data, in_latency_data latency_data, in_locations locations[], in_cells cells[]) RETURNS measurements`. **Invoker** (RLS applies). Grants: `anon`, `authenticated`.

- Inserts the measurement, then each non-null child: one `upload_download_data` row, one `latency_data` row, and every location and cell, one `INSERT` per row. It sets each child's `measurement_id` to the new measurement id.
- All of it runs in one transaction (one RPC call), so a measurement is either fully stored or not at all.
- Auth: each `INSERT` is checked by the anon insert policies, which call `private.verified_device_id()` (bcrypt). Cost grows with the device's history; see [performance-notes](performance-notes.md#1-upload-cost-grows-with-device-history).
- Whole composite rows are inserted, so column defaults do not apply. `created_on` / `updated_on` are filled by the `_200_*_force_created_on` triggers, and ids must come from the client (the app generates UUIDs).
- A repeated measurement id fails with a unique violation on `measurements_pkey`.

### FCC submission insert (table, not RPC)

`POST /rest/v1/fcc_submissions`. Checked by `Allow inserting own FCC submissions` and, for the returned row, `Allow viewing own FCC submissions`. After insert, the triggers queue it for posting; see [fcc-submission-pipeline](fcc-submission-pipeline.md).

## Auth helpers (`private`)

| Function | Returns | Mode | Called from | Behaviour |
|---|---|---|---|---|
| `private.verified_device_id()` | uuid | Definer, plpgsql, VOLATILE, no `search_path` | RLS policies on all `public` tables | Reads `x-device-id` / `x-device-secret` from `request.headers`, looks up `private.devices`, checks `crypt(secret, hashed) = hashed`. **Raises** on a missing header, unknown device, or wrong secret |
| `private.verified_device_id_or_null()` | uuid | Definer, plpgsql, VOLATILE | `SELECT` policies that also allow the dashboard | Same check, but returns null instead of raising |
| `private.is_dashboard_request()` | boolean | Definer, sql, STABLE | `SELECT` policies | True when the `x-dashboard-secret` header equals `private.dashboard_settings.shared_secret` |

None of the three sets `search_path` on prod; #136 pins it on all three (live once migration `202610220102` is pushed to prod).

Each bcrypt check takes about 5 ms on prod. Policies that do not wrap these calls in `(SELECT ...)` run them once per row; see [performance-notes](performance-notes.md).

## Map / dashboard functions

These serve the web dashboard. The dashboard's code is not in this repo: its backend is the Flask app in CellWatch/demo-dashboard, hosted on Render. It calls `rpc_map_points` and `rpc_group_detail` and sends `x-dashboard-secret` on both once its `DASHBOARD_SECRET` setting is set; it does not call `rpc_group_summaries`.

> **Security note:** `rpc_map_points`, `rpc_group_summaries`, and `rpc_group_detail` are **Definer** functions granted to `anon`, and on prod none of them checks `is_dashboard_request()`. Anyone with the anon key (which ships in the app) can call them and read per-group location, carrier, and speed data for all devices, bypassing RLS. Fixed in #136, which makes all three require the dashboard secret; live once migration `202610220101` is pushed to prod. `DASHBOARD_SECRET` must be set on the dashboard backend first, or its map stops loading.

### `rpc_map_points`

`public.rpc_map_points(min_lng, min_lat, max_lng, max_lat double precision, t_from timestamptz = null, t_to timestamptz = null, providers text[] = null, conn text[] = null, dl_min, dl_max, ul_min, ul_max, lat_min, lat_max double precision = null, lim int = 2000) RETURNS SETOF jsonb`. Definer, sql STABLE, `search_path = public` on prod; `202610220101` makes it `public, pg_temp`.

- For each measurement, takes the latest location and latest cell (`DISTINCT ON` over the **whole** `locations` and `cells` tables), plus throughput and latency.
- Groups by `group_id`: averages lat/lon, takes the max of each speed, and sets `conn_tag` from `max(network_generation)` (`NR`/`5` → `5G`, `LTE`/`4` → `4G`, else `Other`).
- Then filters by bounding box, time, providers, conn tags, and min/max ranges (`lat_min` / `lat_max` bound latency, not latitude). `lim` is clamped to 1..20000.
- Row shape: `{group_id, provider, conn_tag, timestamp, center:[lon,lat], stats:{down_mbps, up_mbps, ping_ms, jitter_ms, loss_pct}}`.
- Mbps = `coalesce(bytes_per_sec, application_bytes_per_sec, bytes/duration) * 8 / 1e6`. `duration` is in microseconds, so the `bytes/duration` fallback (used only when both rates are null) comes out 10⁶ too small; #136 leaves it as is. `ping_ms` and `jitter_ms` here are `rtt / 1000` and `jitter / 1000`.

### `rpc_group_summaries`

Same parameters as `rpc_map_points` (default `lim` 50000, clamped to 1..200000). Same computation, but `ping_ms` / `jitter_ms` are the raw `rtt` / `jitter` (**not** divided by 1000, unlike `rpc_map_points`). The stored values are microseconds, so `rpc_map_points` is right and these "ms" values are 1000× too large. **Fixed in #136**, which converts them in `rpc_group_summaries` and `rpc_group_detail`; live once migration `202610220101` is pushed to prod. Flat row: `{group_id, provider, conn_tag, timestamp, lon, lat, accuracy_m, dl_mbps, ul_mbps, ping_ms, jitter_ms, loss_pct}`.

### `rpc_group_detail`

`public.rpc_group_detail(group_id text) RETURNS jsonb`. Definer, sql STABLE, `search_path = public` on prod; `202610220101` makes it `public, pg_temp`.

Returns `{group_id, timestamp, provider, location:{lat, lon, accuracy_m}, cell:{network_generation, rsrp, sinr}, tests:{download:{mbps}, upload:{mbps}, latency:{rtt_ms, jitter_ms, loss_pct}}}`.

> **Confirmed bug** (**fixed in #136**; live once migration `202610220101` is pushed to prod. `supabase/db_tests/dashboard_rpcs_test.sql` checks that the fixed version returns only the requested group): the filter `where group_id = group_id::uuid` has a parameter with the same name as the column. In SQL functions the column wins, so this compares the column with itself and matches every measurement with a non-null `group_id`. Only the output `group_id` field uses the parameter. Fix by qualifying it as `rpc_group_detail.group_id`.

### `measurements_in_hex`

Two overloads in `published`, both Invoker, sql:
- `published.measurements_in_hex(hex bigint, match_center bool = true, match_start bool = false, match_end bool = false) RETURNS SETOF published.measurements`. Returns published measurements whose `center_hex9` / `start_hex9` / `end_hex9` (H3 res-9 cell) has `hex` as an ancestor, using `hex_ancestor(cell, hex_res(hex)) = hex`. No index can serve this expression, so it scans `published.measurements`.
- `published.measurements_in_hex(hex_string text, ...)`. Parses an H3 hex string (`'x' || lpad(hex_string, 16, '0')`) and calls the bigint version.

## Publishing

### `publish_data`

`public.publish_data(in_measurements publishable_measurement[], in_cells publishable_cell[], OUT measurement_ids uuid[], OUT cell_ids uuid[])`. Invoker, plpgsql. Grants: `anon`, `authenticated`. But `anon` has only `SELECT` on `published`, so it needs a privileged role in practice.

1. Inserts into `published.measurements`: the caller supplies `id`, `timestamp`, `group_offset`, H3 cells, PostGIS points, and `cell_tech`; every other column is copied from `public.measurements`. `ON CONFLICT DO NOTHING`, so already-published ids are skipped. Returns the inserted ids.
2. Inserts `published.cells` the same way, only for cells whose measurement was inserted in step 1.
3. Copies `latency_data` → `published.latency_data` and `upload_download_data` → `published.throughput_data` for those measurements. It fills missing `bytes_per_sec` / `application_bytes_per_sec` as `bytes / (duration / 1e6)`, or 0 when both are 0.

Caller: an offline publishing process. > TODO: verify what calls it and with which role.

## Small helpers (`public`)

| Function | Returns | Notes |
|---|---|---|
| `get_header(item text)` | text | `request.headers ->> item`. Used by `fcc_submission_update_source_ip` and `log_user_data` |
| `get_raw_header()` | json | All request headers. Granted to `anon`, which makes it a handy way to see what headers reach Postgres |
| `get_raw_header(item text)` | text | Ignores `item` and returns all headers as text (bug; **fixed in #136**; live once migration `202610220102` is pushed to prod) |
| `hex_res(hex bigint)` | bigint | H3 resolution (bits 52–55). IMMUTABLE |
| `hex_ancestor(hex bigint, res bigint)` | bigint | H3 parent at `res`: sets the resolution bits and fills the lower digits with 7 (unused). IMMUTABLE |
| `any_uuid_agg(uuid, uuid)` | uuid | `coalesce($1, $2)`. State function of the aggregate `any_uuid(uuid)`, which returns the first non-null value. Used by `published.measurement_groups` |
| `log_user_data()` | trigger | Writes to a non-existent `log_table`. Unused (dropped in #136; gone from prod once migration `202610220102` is pushed) |

Trigger functions (`set_updated_on`, `force_created_on`, `fix_jitter`, `fcc_submission_update_source_ip`, `fcc_submission_record_local_ports` (from `202610130001`, not on main), `private.auto_post_fcc_submission`) are covered in [triggers-and-cron](triggers-and-cron.md).

## FCC functions (`private`)

Full flow: [fcc-submission-pipeline](fcc-submission-pipeline.md).

| Function | Mode | Caller | Behaviour |
|---|---|---|---|
| `private.fcc_submission_is_ready(sub_id uuid) RETURNS boolean` | Invoker, sql STABLE, `search_path = public, private, extensions` (`202610210104` adds `pg_temp` last, since `fcc_queue_digest()` calls it as its owner) | `post_fcc_submission`, `process_fcc_submission_retries` | True when exactly 3 of group `sub_id`'s measurements have their data row, one each of latency/download/upload (measurements without a data row are not counted). A failed test still counts (presence, not success). Defined in [202607140001](../../supabase/migrations/202607140001_repair_fcc_submission_functions.sql) |
| `private.post_fcc_submission(sub_id uuid, fcc_env text) RETURNS boolean` | Invoker (runs as `postgres` when called from the retry job) | `process_fcc_submission_retries`; manual | Checks readiness, loads the URL and Vault secret for `fcc_env`, builds the payload from `fcc_submissions_view`, checks that `tests` has all three keys, POSTs with `http()`, raises on a non-2xx response, then sets `submitted`, `submission`, `submitted_on`, `submission_response` |
| `private.process_fcc_submission_retries(p_batch_size int = 1) RETURNS integer` | Definer; `EXECUTE` revoked from `PUBLIC` | pg_cron job `fcc-submission-retries` | Works through due `pending` queue entries (at most 10), with backoff and blocking. Prod runs the [202610070001](../../supabase/migrations/202610070001_unclog_fcc_retry_queue.sql) version (pushed); `full_schema.sql` still has the earlier one. See the pipeline doc |
| `private.auto_post_fcc_submission() RETURNS trigger` | Definer | `AFTER INSERT` on `fcc_submissions` | On prod (and in `full_schema.sql`): upserts a `pending` queue entry (`fcc_env = 'prod'`) when `submitted` is false. The copies in `202607140001` and `post_fcc_submission_func.sql` post synchronously instead (older design) |

## Overloads and PostgREST

PostgREST picks a function overload by the **names** of the JSON keys in the request body. Adding an overload, such as a second `register_device` signature, changes which function old app builds hit only if the key sets overlap. A new signature that a new app build needs must be pushed to prod before the app release that calls it. See [migrations-and-deploy](migrations-and-deploy.md#ordering-rule-server-first).
