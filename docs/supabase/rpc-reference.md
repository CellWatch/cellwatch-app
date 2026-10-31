# Supabase function (RPC) reference

Every SQL/PL/pgSQL function in the CellWatch database: signature, security mode, caller, and behaviour.

**Related:** [schema](schema.md) · [triggers-and-cron](triggers-and-cron.md) · [fcc-submission-pipeline](fcc-submission-pipeline.md) · [performance-notes](performance-notes.md) · [sync-and-upload](../app/sync-and-upload.md) · [identity-and-security](../architecture/identity-and-security.md)

Definitions are in [full_schema.sql](../../supabase/full_schema.sql) unless a migration is named. `full_schema.sql` was pulled from prod on 2026-11-01, after that day's pushes (#158), so it includes every migration on `main`; all of them are on prod. Where a migration changed a function recently, this page gives the date. "Invoker" means `SECURITY INVOKER` (the default): RLS and grants of the calling role apply. "Definer" functions run as their owner, `postgres`, and bypass RLS.

## How the app calls Supabase

The app uses supabase-kt 1.0.3 (`postgrest-kt`) from [NetworkMeasurementDatasource.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/data/network/NetworkMeasurementDatasource.kt), always with the anon key (role `anon`).

| Call | HTTP | Device headers | Used by |
|---|---|---|---|
| `rpc("register_device", {device_id, device_secret})` on `main` (#148, merged 2026-10-27; not released). The Play build sends `{device_id}` | `POST /rest/v1/rpc/register_device` | No (separate client without the header plugin) | `getClient()`, through `registeredDeviceSecret` in [DeviceRegistration.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/data/network/DeviceRegistration.kt), until the server has confirmed the secret (DataStore `device_registered`). The Play build calls it the first time no secret is stored |
| `rpc("insert_measurement", {in_measurement, in_measurement_data, in_latency_data, in_locations, in_cells})` | `POST /rest/v1/rpc/insert_measurement` | Yes | `MeasurementUploader.uploadMeasurements` |
| `postgrest["fcc_submissions"].insert(...)` | `POST /rest/v1/fcc_submissions` (returns the row) | Yes | `MeasurementUploader.uploadSubmissions` |
| `postgrest["measurements"].select("*,upload_download_data(*),latency_data(*),locations(*),cells(*)")` | `GET /rest/v1/measurements?...` | Yes | `getMeasurementById`, which has no caller on main |

Headers on every request from the main client:
- `X-Device-ID`, `X-Device-Secret`: read by `private.verified_device_id()` and the RLS policies.
- **Not on main:** `X-Local-Port` and `X-Transport`, added by the OkHttp network interceptor `LocalPortHeaders.kt` and stored by the `_310` trigger. Both are uncommitted on the `client-port-header` branch (main's client uses the Ktor CIO engine), and the trigger's migration, `202610130001`, is not pushed to prod.

A unique-violation error (`duplicate key value violates unique constraint`) becomes `DuplicateKeyError`, which the uploader treats as "already on the server". The app also calls the EC2 tuple service for FCC source IP/port. That service is not Supabase; see [data-flow](../architecture/data-flow.md).

## App-facing functions

### `register_device`

`public.register_device(device_id uuid) RETURNS text`. **Definer**, `search_path = public, private, extensions, pg_temp` (since 2026-10-31). Grants: `anon`, `authenticated`, `service_role`. The released app (versionCode 13) calls this one.

- Makes a random 32-byte secret (`gen_random_bytes`, base64) and inserts `(device_id, crypt(secret, gen_salt('bf')))` into `private.devices`. Returns the plaintext secret **once**.
- No auth required: anyone with the anon key can register a new id.
- If the id already exists and the device has uploaded something (`private.device_has_uploaded`), it raises `device id % already registered`. If the device has never uploaded anything (no measurements, no FCC submissions), it replaces the stored secret and returns the new one, after waiting for that device's uploads in progress (see [identity-and-security](../architecture/identity-and-security.md)).
- That rescue came with [202610220001](../../supabase/migrations/202610220001_retry_safe_device_registration.sql) (#148), pushed to prod on 2026-10-31. Before it, a second call always raised, so a phone that lost the response could never register again. Such a phone never uploaded, so it now recovers on its next attempt, the released app included.

### `register_device(device_id, device_secret)`

`public.register_device(device_id uuid, device_secret text) RETURNS void`. **Definer**, `search_path = public, private, extensions, pg_temp`. Grants: `anon`, `authenticated`, `service_role`. Added by [202610220001](../../supabase/migrations/202610220001_retry_safe_device_registration.sql) (#148), pushed to prod on 2026-10-31.

- The app on `main` (not released) makes the secret itself (64 hex digits), saves it, then calls this. A retry after a lost response sends the same secret. A phone registered by an older build sends its stored secret once to confirm it.
- Raises `device secret is too short` for a secret under 32 characters.
- New id: stores `crypt(device_secret, gen_salt('bf'))`. Same secret as on file: succeeds without changes. Different secret: replaces it if the device has never uploaded, otherwise raises `device id % already registered`.
- Accepted risk (from the migration): someone who learns the id of a phone that registered but hasn't uploaded yet could take that id over.
- **Server first:** this had to be on prod before any app release built from `main`. PostgREST picks the overload by argument names, so without it the call fails and new installs can't register ([migrations-and-deploy](migrations-and-deploy.md#ordering-rule-server-first)).

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
| `private.verified_device_id()` | uuid | Definer, plpgsql, VOLATILE | RLS policies on all `public` tables | Reads `x-device-id` / `x-device-secret` from `request.headers`, looks up `private.devices`, checks `crypt(secret, hashed) = hashed`. **Raises** on a missing header, unknown device, or wrong secret |
| `private.verified_device_id_or_null()` | uuid | Definer, plpgsql, VOLATILE | `SELECT` policies that also allow the dashboard | Same check, but returns null instead of raising |
| `private.is_dashboard_request()` | boolean | Definer, sql, STABLE | `SELECT` policies | True when the `x-dashboard-secret` header equals `private.dashboard_settings.shared_secret` |
| `private.device_has_uploaded(device uuid)` | boolean | Definer, sql, STABLE, `search_path = public, private, pg_temp` | both `register_device` versions | From `202610220001` (#148, pushed 2026-10-31). True when `measurements` or `fcc_submissions` has a row with that `device_id`. Such a device's secret is never replaced |

Since 2026-10-31 the first three have `search_path = public, private, extensions, pg_temp`, set by #136's `202610220102` (and `202610220001` for `verified_device_id`). Before that none had a fixed `search_path`.

Each bcrypt check takes about 5 ms on prod. Policies that do not wrap these calls in `(SELECT ...)` run them once per row; see [performance-notes](performance-notes.md). Since 2026-10-29, when `202610210001` (#132) was pushed, every upload policy wraps them, so each runs once per statement.

## Map / dashboard functions

These serve the web dashboard. The dashboard's code is not in this repo: its backend is the Flask app in CellWatch/demo-dashboard, hosted on Render. It calls `rpc_map_points` and `rpc_group_detail` and sends `x-dashboard-secret` on both, from its `DASHBOARD_SECRET` setting; it does not call `rpc_group_summaries`. The Flask app's own API has no authentication, so anyone who can reach it gets what it returns.

> **Security note:** `rpc_map_points`, `rpc_group_summaries`, and `rpc_group_detail` are **Definer** functions granted to `anon`. Until 2026-11-01 none of them checked `is_dashboard_request()`, so anyone with the anon key (which ships in the app) could call them and read per-group location, carrier, and speed data for all devices, bypassing RLS. `202610220101` (#136), pushed to prod on 2026-11-01, makes all three require the dashboard secret: a call without it gets HTTP 401 (`42501`, "dashboard secret required"). The dashboard backend's `DASHBOARD_SECRET` was checked against prod's before the push.

### `rpc_map_points`

`public.rpc_map_points(min_lng, min_lat, max_lng, max_lat double precision, t_from timestamptz = null, t_to timestamptz = null, providers text[] = null, conn text[] = null, dl_min, dl_max, ul_min, ul_max, lat_min, lat_max double precision = null, lim int = 2000) RETURNS SETOF jsonb`. Definer, sql STABLE, `search_path = public, pg_temp` (`public` until `202610220101` was pushed on 2026-11-01).

- For each measurement, takes the latest location and latest cell (`DISTINCT ON` over the **whole** `locations` and `cells` tables), plus throughput and latency.
- Groups by `group_id`: averages lat/lon, takes the max of each speed, and sets `conn_tag` from `max(network_generation)` (`NR`/`5` → `5G`, `LTE`/`4` → `4G`, else `Other`).
- Then filters by bounding box, time, providers, conn tags, and min/max ranges (`lat_min` / `lat_max` bound latency, not latitude). `lim` is clamped to 1..20000.
- Row shape: `{group_id, provider, conn_tag, timestamp, center:[lon,lat], stats:{down_mbps, up_mbps, ping_ms, jitter_ms, loss_pct}}`.
- Mbps = `coalesce(bytes_per_sec, application_bytes_per_sec, bytes/duration) * 8 / 1e6`. `duration` is in microseconds, so the `bytes/duration` fallback (used only when both rates are null) came out 10⁶ too small until 2026-11-01. `202610220101` divides by `duration / 1e6` instead, as the FCC payload does. `ping_ms` and `jitter_ms` here are `rtt / 1000` and `jitter / 1000`.

### `rpc_group_summaries`

Same parameters as `rpc_map_points` (default `lim` 50000, clamped to 1..200000). Same computation. Until 2026-11-01 its `ping_ms` / `jitter_ms` were the raw `rtt` / `jitter`, which are microseconds, so those "ms" values were 1000× too large. `202610220101` (#136, pushed 2026-11-01) divides them by 1000 here and in `rpc_group_detail`, as `rpc_map_points` always did. Flat row: `{group_id, provider, conn_tag, timestamp, lon, lat, accuracy_m, dl_mbps, ul_mbps, ping_ms, jitter_ms, loss_pct}`.

### `rpc_group_detail`

`public.rpc_group_detail(group_id text) RETURNS jsonb`. Definer, sql STABLE, `search_path = public, pg_temp` (`public` until 2026-11-01).

Returns `{group_id, timestamp, provider, location:{lat, lon, accuracy_m}, cell:{network_generation, rsrp, sinr}, tests:{download:{mbps}, upload:{mbps}, latency:{rtt_ms, jitter_ms, loss_pct}}}`.

> **Fixed bug** (`202610220101`, pushed to prod 2026-11-01; `supabase/db_tests/dashboard_rpcs_test.sql` checks that the fixed version returns only the requested group): the filter was `where group_id = group_id::uuid`, with a parameter of the same name as the column. In SQL functions the column wins, so it compared the column with itself and matched every measurement with a non-null `group_id`; only the output `group_id` field used the parameter. The fix qualifies it as `rpc_group_detail.group_id`.

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
| `get_header(item text)` | text | `request.headers ->> item`. Used by `fcc_submission_update_source_ip` (and by `log_user_data` until it was dropped) |
| `get_raw_header()` | json | All request headers. Granted to `anon`, which makes it a handy way to see what headers reach Postgres |
| `get_raw_header(item text)` | text | The named header only. Until `202610220102` (#136) was pushed on 2026-10-31 it ignored `item` and returned all headers as text |
| `hex_res(hex bigint)` | bigint | H3 resolution (bits 52–55). IMMUTABLE |
| `hex_ancestor(hex bigint, res bigint)` | bigint | H3 parent at `res`: sets the resolution bits and fills the lower digits with 7 (unused). IMMUTABLE |
| `any_uuid_agg(uuid, uuid)` | uuid | `coalesce($1, $2)`. State function of the aggregate `any_uuid(uuid)`, which returns the first non-null value. Used by `published.measurement_groups` |
| `log_user_data()` | trigger | Dropped from prod on 2026-10-31 by `202610220102` (#136). It wrote to a non-existent `log_table`, and nothing used it |

Trigger functions (`set_updated_on`, `force_created_on`, `fix_jitter`, `fcc_submission_update_source_ip`, `fcc_submission_record_local_ports` (from `202610130001`, not on main), `private.auto_post_fcc_submission`) are covered in [triggers-and-cron](triggers-and-cron.md).

## FCC functions (`private`)

Full flow: [fcc-submission-pipeline](fcc-submission-pipeline.md).

| Function | Mode | Caller | Behaviour |
|---|---|---|---|
| `private.fcc_submission_is_ready(sub_id uuid) RETURNS boolean` | Invoker, sql STABLE, `search_path = public, private, extensions, pg_temp` (`pg_temp` added by `202610210104`, since `fcc_queue_digest()` and `fcc_queue_entry_is_ready()` call it as their owner) | `post_fcc_submission`, `process_next_fcc_submission_retry`, `fcc_queue_entry_is_ready`, `fcc_queue_digest` | True when exactly 3 of group `sub_id`'s measurements have their data row, one each of latency/download/upload (measurements without a data row are not counted). A failed test still counts (presence, not success). Defined in [202607140001](../../supabase/migrations/202607140001_repair_fcc_submission_functions.sql) |
| `private.post_fcc_submission(sub_id uuid, fcc_env text) RETURNS boolean` | Invoker (runs as `postgres` when called from the retry job), `search_path = public, private, extensions, pg_temp` | `process_next_fcc_submission_retry`; manual | Checks readiness, loads the URL and Vault secret for `fcc_env`, builds the payload with `fcc_submission_payload(sub_id)`, checks that `tests` has all three keys, and for the `prod` env refuses anything `fcc_payload_hold_reason` would hold. POSTs through `fcc_http_post`, raises on a non-2xx response, then sets `submitted`, `submission`, `submitted_on`, `submission_response`. This is [202610210103](../../supabase/migrations/202610210103_fcc_retry_queue_processing.sql)'s version, on prod since 2026-10-29; before that it read `fcc_submissions_view` and called `http()` itself |
| `private.process_fcc_submission_retries(p_batch_size int = 1) RETURNS integer` | Definer, `search_path = public, private, extensions, pg_temp`; `EXECUTE` revoked from `PUBLIC` | manual only since 2026-10-29 (the cron job called it before) | Runs `process_next_fcc_submission_retry` for up to 10 entries with a 60 s cap, **all in the caller's transaction**. `202610210103`'s version; prod ran [202610070001](../../supabase/migrations/202610070001_unclog_fcc_retry_queue.sql)'s until 2026-10-29. See the pipeline doc |
| `private.auto_post_fcc_submission() RETURNS trigger` | Definer, `search_path = public, private, extensions` | `AFTER INSERT` on `fcc_submissions` | Upserts a `pending` queue entry (`fcc_env = 'prod'`) when `submitted` is false. #134's [202609300001](../../supabase/migrations/202609300001_track_live_fcc_queue_and_role_settings.sql) records this hand-made version in a migration (pushed 2026-10-29). The copies in `202607140001` and `post_fcc_submission_func.sql` post synchronously instead (older design) |

Added by #133's migrations, on prod since 2026-10-29:

| Function | Mode | Caller | Behaviour |
|---|---|---|---|
| `private.fcc_submission_payload(sub_id uuid) RETURNS jsonb` | Definer, sql STABLE, `search_path = public, private, extensions, pg_temp`; `EXECUTE` revoked from `PUBLIC`, granted to `readonly_user` and `pg_read_all_data` | `post_fcc_submission`, `fcc_submissions_view`, `process_next_fcc_submission_retry` | Builds the BDC JSON for one submission from that group's rows only: the same JSON as the old view, with locations and cells in the same order. From `202610210101` |
| `private.fcc_http_post(p_url text, p_hash_value text, p_body text, OUT status int, OUT content text)` | Invoker, plpgsql, `search_path = public, private, extensions, pg_temp`; `EXECUTE` revoked from `PUBLIC` | `post_fcc_submission` | The one `http()` POST to the FCC, with the `hash_value` header. Database tests replace it with a stand-in. From `202610210101` |
| `private.fcc_payload_hold_reason(payload jsonb) RETURNS text` | sql IMMUTABLE | `process_next_fcc_submission_retry`, `recheck_held_fcc_submission_retries`, `post_fcc_submission` | Why the BDC would reject the payload, it falls short of the spec, or it waits on a decision: `No network registration` (net MCC/MNC missing), `No locations`, `One location`, `Missing provider`. Several reasons are joined with `; `; null if none. From `202610210103` |
| `private.fcc_response_is_duplicate_test_id(status int, body text) RETURNS boolean` | sql IMMUTABLE | `post_fcc_submission` | True for a 4xx answer whose body contains `duplicate_test_id`, in any case. From `202610210103` |
| `private.process_next_fcc_submission_retry() RETURNS text` | Definer, `search_path = public, private, extensions, pg_temp`; `EXECUTE` revoked from `PUBLIC` | `run_fcc_submission_retries`, `process_fcc_submission_retries` | Handles the one entry due longest: readiness (`waiting`, or `expired` after 30 days), hold reason (`held`), then posts (`posted`; `already_at_fcc` on `duplicate_test_id`, which marks the submission sent; `rejected` on 400/409/413/422, which blocks it; `failed` with backoff, blocked after 6 attempts). Returns null when nothing is due. From `202610210103` |
| `private.revive_expired_fcc_submission_retries() RETURNS integer` | Definer, `search_path = public, private, extensions, pg_temp`; `EXECUTE` revoked from `PUBLIC` | start of each run | Sets `expired` entries whose measurements are now complete back to `pending`, in one statement. From `202610210103` |
| `private.recheck_held_fcc_submission_retries(p_all boolean = false) RETURNS integer` | Definer, `search_path = public, private, extensions, pg_temp`; `EXECUTE` revoked from `PUBLIC` | start of each run; manual with `true` | Checks `held` entries again (each once a day, or all of them with `true`): those no reason applies to any more go back to `pending`, the rest get the current wording of their reason. From `202610210103` |
| `private.run_fcc_submission_retries(p_batch_size int = 10, INOUT processed int, p_time_limit interval = '1 minute')` (**procedure**) | plpgsql, not Definer and no `SET`, so it can commit; `EXECUTE` revoked from `PUBLIC` | pg_cron job `fcc-submission-retries`: `call private.run_fcc_submission_retries(10);` | Up to 10 entries, 60 s cap, **commits after each entry**, and ends the run at the first failure that isn't a rejection. Inside a transaction block it fails ("invalid transaction termination") before posting anything. From `202610210103` |
| `private.fcc_queue_entry_is_ready(sub_id uuid) RETURNS boolean` | Definer, sql STABLE, `search_path = public, private, extensions, pg_temp`; `EXECUTE` revoked from `PUBLIC`, granted to `readonly_user` and `pg_read_all_data` | `fcc_queue_attention` | `fcc_submission_is_ready` for callers that can't read the measurement tables. From `202610210104` |
| `private.fcc_queue_digest(p_new_since timestamptz = null) RETURNS text` | Definer, plpgsql STABLE, `search_path = public, private, pg_temp`; `EXECUTE` revoked from `PUBLIC`, granted to `readonly_user` | `send_fcc_queue_digest`; manual | Plain-text queue summary: counts by state, the last 24 hours' posts, `duplicate_test_id` answers and failed attempts, and the entries in `fcc_queue_attention`. With `p_new_since`, as the email passes it, blocked entries unchanged since then are one line instead of a list. Entries in `private.fcc_queue_ignored` are left out. From `202610210104`; `202610310001` added `p_new_since` and `202610310002` the ignore list |

Added by `202610310001` (#154, pushed 2026-10-31) and changed by `202610310002` (#156, pushed 2026-11-01):

| Function | Mode | Caller | Behaviour |
|---|---|---|---|
| `private.send_fcc_queue_digest(p_force boolean = false) RETURNS text` | plpgsql, not Definer, `search_path = public, private, pg_temp`; `EXECUTE` revoked from `PUBLIC` | pg_cron job `fcc-queue-digest` (daily, 13:01 UTC) | Emails `fcc_queue_digest` through Resend when, since the last run, an entry was blocked or held, a post failed, or an entry passed 3 days pending; entries in `private.fcc_queue_ignored` don't count. Otherwise it sends a short email only after 7 days without one. The first run, and a call with `p_force => true`, always send. The Resend key and the recipient come from the Vault secrets `fcc_digest_resend_api_key` and `fcc_digest_email_to`. Each run is logged in `private.fcc_digest_runs`; it returns `sent` or `nothing new`. A failed send raises, so nothing is logged and the next run covers the missed period |
| `private.resend_http_post(p_api_key text, p_body text, OUT status int, OUT content text)` | Invoker, plpgsql, `search_path = public, private, extensions, pg_temp`; `EXECUTE` revoked from `PUBLIC` | `send_fcc_queue_digest` | The one `http()` POST to Resend's email API. Database tests replace it with a stand-in |

The queue views `private.fcc_queue_health` and `private.fcc_queue_attention` (`202610210104`), and the tables `private.fcc_digest_runs` and `private.fcc_queue_ignored`, are in [schema](schema.md#private-schema).

## Overloads and PostgREST

PostgREST picks a function overload by the **names** of the JSON keys in the request body. Adding an overload, such as a second `register_device` signature, changes which function old app builds hit only if the key sets overlap. A new signature that a new app build needs must be pushed to prod before the app release that calls it. See [migrations-and-deploy](migrations-and-deploy.md#ordering-rule-server-first).

`202610220001` (pushed 2026-10-31) is such a case. The released app sends `{device_id}` and keeps reaching `register_device(uuid)`. Builds from `main` send `{device_id, device_secret}` and need `register_device(uuid, text)` on prod first.
