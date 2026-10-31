# Database performance notes

Known server-side costs in the CellWatch database, why they happen (traced to the policy and view definitions), and how to profile them.

**Related:** [schema](schema.md) · [rpc-reference](rpc-reference.md) · [fcc-submission-pipeline](fcc-submission-pipeline.md) · [migrations-and-deploy](migrations-and-deploy.md) · [sync-and-upload](../app/sync-and-upload.md) · [identity-and-security](../architecture/identity-and-security.md)

Costs 1–3 were fixed on prod by migrations pushed on 2026-10-29. Their sections describe the definitions prod had before that push (the 2026-10-07 snapshot of [full_schema.sql](../../supabase/full_schema.sql)), because they explain what happened to uploads and posts until then; the current `full_schema.sql`, pulled on 2026-11-01, has the fixed versions. Timings were measured in October 2026.

| # | Cost | Effect | Status |
|---|---|---|---|
| 1 | Upload RLS runs a bcrypt check per row the device owns, for every child row inserted | Uploads get slower as a device's history grows; phones with history time out | Fixed: `202610210001` (#132), pushed to prod 2026-10-29 |
| 2 | FCC payload view aggregates every location and cell in the database | About 25 s per FCC post | Fixed: `202610210101` (#133), pushed to prod 2026-10-29 |
| 3 | Missing indexes on `public.measurements(device_id)` and `(group_id)` | Seq scans in the RLS subqueries and the readiness checks | Fixed: `device_id` index in #132's `202610210001`, `group_id` index in #133's `202610210101`, both pushed to prod 2026-10-29 |
| 4 | Dashboard RPCs aggregate the whole DB before filtering | Cost grows with total data per call | Not measured |

## 1. Upload cost grows with device history

### The pieces

- `public.insert_measurement` is **SECURITY INVOKER**, so the anon RLS policies apply. It inserts each location and cell with **its own `INSERT` statement** inside a loop.
- `private.verified_device_id()` and `private.verified_device_id_or_null()` are PL/pgSQL functions with no volatility marker, so they are **VOLATILE**. Each call runs `crypt(secret, hashed_secret)`, a bcrypt check of about 5 ms on prod.
- Child-table insert policies (`cells`, `locations`, `latency_data`, `upload_download_data`) look like this:

  ```sql
  WITH CHECK (measurement_id IN (
      SELECT measurements.id FROM public.measurements
      WHERE measurements.device_id = (SELECT private.verified_device_id())))
  ```

  The inner `(SELECT private.verified_device_id())` is an InitPlan: it runs **once per statement**. Because `insert_measurement` uses one statement per child row, that is already one bcrypt per child row.
- The subquery reads `public.measurements`, which has its own RLS. Its `SELECT` policy is **not** wrapped in `(SELECT …)`:

  ```sql
  USING (private.is_dashboard_request() OR private.verified_device_id_or_null() = device_id)
  ```

  So `verified_device_id_or_null()` (another bcrypt) runs **per `measurements` row** the subquery scans. That is every measurement the device has already uploaded.
- There is **no index on `measurements.device_id`**, so each check also seq-scans `measurements`.

### The result

Inserting one child row costs about (1 + *N*) bcrypt calls, where *N* is the number of measurements the device already owns, plus a scan of `measurements`. One `insert_measurement` call with *c* cells and *l* locations inserts 1 + *c* + *l* child rows (the data row, the cells, the locations). It also does one bcrypt check for the measurement row itself. In total that is roughly 1 + (1 + *c* + *l*)(1 + *N*) bcrypt calls.

Measured on a local copy of the prod policies, for one small upload (3 cells, 2 locations):

| Prior measurements on the device | Time |
|---|---|
| 0 | 0.1 s |
| 200 | 6.1 s |
| 400 | 12.3 s |

The released app sends every unsynced row on each upload run, and its supabase-kt client gives up after 10 s (the library's default request timeout). So a phone with a backlog stops getting measurements through even though the server may still commit them. The next attempt then hits a duplicate key, which the uploader treats as success. Main limits uploads to 3 in flight (#111) and, since #148 (merged 2026-10-27; not released), waits up to 60 s per request and also uploads hourly. That makes phones with a backlog upload more, which is why the RLS fix had to be on prod first; it is since 2026-10-29. See [sync-and-upload](../app/sync-and-upload.md).

The same pattern makes reads slow. `GET /measurements?select=*,cells(*),locations(*),…` evaluates the `Allow viewing own …` policies, which wrap `verified_device_id()` in `(SELECT …)`, but their subquery still goes through the unwrapped `measurements` policy. The `… (dashboard or own)` policies call `verified_device_id_or_null()` unwrapped, inside `EXISTS` (once per child row). Postgres ORs all permissive policies on a table, so these costs add up.

### Fix (#132, pushed to prod 2026-10-29)

Migration [202610210001_fast_upload_rls.sql](../../supabase/migrations/202610210001_fast_upload_rls.sql) wraps the helper calls in seven policies on `measurements`, `latency_data`, `locations`, `upload_download_data` and `cells` in `(SELECT …)`, so each becomes a once-per-statement InitPlan, and adds an index on `measurements(device_id)`. The policies allow and refuse the same rows as before. In the local reproduction an upload with 401 measurements on the device took 66–114 ms and the duplicate-check read 35–62 ms, and the isolation checks between devices still passed (`upload_rls_test.sql`; see [testing](../dev/testing.md#database-tests)). It was pushed to prod on 2026-10-29, and backlogged uploads from phones with long histories started arriving right after.

- A caller without a valid device secret that reads or writes the child tables directly may now get no rows or an RLS error, instead of "missing x-device-id header" or "incorrect device secret". Such callers are refused either way.
- The index is built inside the migration's transaction (0.3 s for 1M rows locally). If the table is much larger, build it first with `create index concurrently`, as the migration's comment shows.

## 2. FCC payload view scans the whole database

`private.post_fcc_submission` reads one row from `private.fcc_submissions_view` (`where submission_id = <id>`). The view's first two CTEs are:

```sql
locs  AS (SELECT measurement_id, jsonb_agg(...) FROM public.locations GROUP BY measurement_id ...),
cells AS (SELECT measurement_id, jsonb_agg(...) FROM public.cells     GROUP BY measurement_id ...)
```

Each is referenced **twice** (by the `upload_download_data` and `latency_data` CTEs). Since PostgreSQL 12, a CTE referenced more than once is **materialized**, so Postgres builds the JSON for every location and every cell in the database before it joins to the single submission. The outer `submission_id` filter cannot reach inside. The `upload_download_data` and `latency_data` CTEs also group all measurements by `group_id`.

- On prod (2026-10-21; about 421k cells and 97k locations), building one ~6 KB payload took **25.5 s**. It grows with total data, not with the size of the submission.
- Knock-on effects:
  - With the 60 s per-run cap from `202610070001`, a cron run can post only two or three submissions.
  - Slow runs have hit `statement_timeout` and rolled back after the FCC had already accepted the POST ([fcc-submission-pipeline](fcc-submission-pipeline.md#5-http-call-and-response-handling)).
- `private.fcc_submission_is_ready` runs twice per post (once in the retry job, once in `post_fcc_submission`). It filters `measurements` by `group_id`, which had no index on prod before 2026-10-29.

Fix (#133, pushed to prod 2026-10-29): [202610210101_build_fcc_payload_per_submission.sql](../../supabase/migrations/202610210101_build_fcc_payload_per_submission.sql) adds `private.fcc_submission_payload(sub_id)`, which aggregates locations and cells only for that group's measurements, makes `post_fcc_submission` use it, rebuilds `fcc_submissions_view` on top of it, and adds an index on `measurements(group_id)`. The payload is byte for byte the old view's on the test fixtures, with locations and cells in the same order. With 50k cells in a local database, one payload took 0.5 s through the old view and 1.5 ms through the function. On prod, whole retry-job runs have taken under a second since the push.

## 3. Other costs visible in the schema

- **Dashboard RPCs** (`rpc_map_points`, `rpc_group_summaries`): `DISTINCT ON (measurement_id)` over the **entire** `locations` and `cells` tables, then group all measurements, then apply the bounding box and filters. Each call costs time in proportion to the whole database. Until 2026-11-01, `rpc_group_detail` aggregated every group too, because of a parameter-name clash ([rpc-reference](rpc-reference.md#rpc_group_detail)); #136's `202610220101`, pushed that day, fixed it. It does not change the whole-table scans in the other two.
- **`published.measurements_in_hex`**: filters on `hex_ancestor(center_hex9, …) = hex`. That expression can't use the btree indexes on the `*_hex9` columns, so it scans the table.
- **`fix_jitter` trigger**: runs two subqueries on `measurements` per `latency_data` insert (cheap, because they use the primary key).

## How to profile

Profile RLS and functions **locally**, against a copy of the schema with realistic data (see [migrations-and-deploy](migrations-and-deploy.md#local-stack) and [local-setup](../dev/local-setup.md)). Do not impersonate `anon` or read device secrets on prod.

### Reproduce an app request locally

PostgREST sets the role and `request.headers` per request. You can do the same in `psql` with a device you registered locally:

```sql
begin;
select public.register_device('<local test uuid>');          -- returns a test secret
select set_config('request.headers',
  '{"x-device-id":"<local test uuid>","x-device-secret":"<returned secret>"}', true);
set local role anon;
explain (analyze, buffers)
  insert into public.locations (id, measurement_id, created_on, updated_on)
  values (gen_random_uuid(), '<a measurement of that device>', now(), now());
rollback;
```

Look for `SubPlan` / `InitPlan` nodes and for loop counts (`loops=`) on scans of `measurements`. Seed the device with a few hundred measurements to see the growth.

`EXPLAIN ANALYZE` on an RPC (`select public.insert_measurement(...)`) shows only the function call. To see the statements inside it, use `auto_explain` in the local session (superuser):

```sql
load 'auto_explain';
set auto_explain.log_min_duration = 0;
set auto_explain.log_analyze = on;
set auto_explain.log_nested_statements = on;
-- run the RPC, then read the plans from the Postgres log
```

### Profile the FCC view

```sql
explain (analyze, buffers)
select data from private.fcc_submissions_view where submission_id = '<id>';
```

Before 2026-10-29, prod showed `CTE locs` / `CTE cells` nodes that read every row of `locations` and `cells`. Since `202610210101` was pushed, the view is built on `private.fcc_submission_payload`, so profile `select private.fcc_submission_payload('<id>')` instead.

### On prod (read-only)

- `pg_stat_statements` is installed in the `extensions` schema:

  ```sql
  set default_transaction_read_only = on;
  select calls, round(total_exec_time) as total_ms, round(mean_exec_time) as mean_ms, left(query, 120)
  from extensions.pg_stat_statements
  order by total_exec_time desc limit 20;
  ```

  Statements run inside functions and policies only appear separately if `pg_stat_statements.track = all` (`show pg_stat_statements.track;`). > TODO: verify the live setting.
- Retry-job run times: `cron.job_run_details` (`end_time - start_time`), as in [triggers-and-cron](triggers-and-cron.md#pg_cron-jobs).
- To time bcrypt without touching device secrets, run `select crypt('x', gen_salt('bf'));` with `\timing on`.
