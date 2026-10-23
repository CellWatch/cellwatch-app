# Database performance notes

Known server-side costs in the CellWatch database, why they happen (traced to the policy and view definitions), and how to profile them.

**Related:** [schema](schema.md) · [rpc-reference](rpc-reference.md) · [fcc-submission-pipeline](fcc-submission-pipeline.md) · [migrations-and-deploy](migrations-and-deploy.md) · [sync-and-upload](../app/sync-and-upload.md) · [identity-and-security](../architecture/identity-and-security.md)

All definitions referenced here are in [full_schema.sql](../../supabase/full_schema.sql) (live snapshot). Timings were measured in October 2026.

| # | Cost | Effect | Status |
|---|---|---|---|
| 1 | Upload RLS runs a bcrypt check per row the device owns, for every child row inserted | Uploads get slower as a device's history grows; phones with history time out | Fix in progress, not deployed |
| 2 | FCC payload view aggregates every location and cell in the database | About 25 s per FCC post | Fix in progress, not deployed |
| 3 | Missing indexes on `public.measurements(device_id)` and `(group_id)` | Seq scans in the RLS subqueries and the readiness checks | Fix in progress, not deployed |
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

The shipped app sends every unsynced row on each upload run, and its supabase-kt client gives up after about 10 s. So a phone with a backlog stops getting measurements through even though the server may still commit them. The next attempt then hits a duplicate key, which the uploader treats as success. See [sync-and-upload](../app/sync-and-upload.md).

The same pattern makes reads slow. `GET /measurements?select=*,cells(*),locations(*),…` evaluates the `Allow viewing own …` policies, which wrap `verified_device_id()` in `(SELECT …)`, but their subquery still goes through the unwrapped `measurements` policy. The `… (dashboard or own)` policies call `verified_device_id_or_null()` unwrapped, inside `EXISTS` (once per child row). Postgres ORs all permissive policies on a table, so these costs add up.

### Fix approach (in progress, not deployed)

Wrap every policy function call in `(SELECT …)` so it becomes a once-per-statement InitPlan, and add an index on `measurements(device_id)`. In the local reproduction this brought the 400-measurement case to 78 ms (insert) and 30 ms (read), and the isolation checks between devices still passed. Deploy this before asking users to re-sync.

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
- `private.fcc_submission_is_ready` runs twice per post (once in the retry job, once in `post_fcc_submission`). It filters `measurements` by `group_id`, which has no index in the snapshot.

Fix approach (in progress, not deployed): build the payload for one group only, by aggregating locations and cells just for that group's measurement ids, and add an index on `measurements(group_id)`.

## 3. Other costs visible in the schema

- **Dashboard RPCs** (`rpc_map_points`, `rpc_group_summaries`): `DISTINCT ON (measurement_id)` over the **entire** `locations` and `cells` tables, then group all measurements, then apply the bounding box and filters. Each call costs time in proportion to the whole database. `rpc_group_detail` probably aggregates all groups too, because of a parameter-name clash ([rpc-reference](rpc-reference.md#rpc_group_detail)).
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

Expect `CTE locs` / `CTE cells` nodes that read every row of `locations` and `cells`.

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
