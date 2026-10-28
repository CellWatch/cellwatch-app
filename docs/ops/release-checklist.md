# Release checklist

Purpose: the ordered steps for shipping a new Android build without breaking uploads or FCC posting.

Related: [Runbooks](runbooks.md) · Prod deploy runbook (`prod-deploy-runbook.md`, local only) · [Migrations and deploy](../supabase/migrations-and-deploy.md) · [Build and config](../app/build-and-config.md) · [Testing](../dev/testing.md) · [BDC spec mapping](../fcc/bdc-spec-mapping.md)

**Prod has two separate paths, and merging a PR to `main` is neither:**
- **Supabase:** a server change is in prod only once its migration has been **pushed** (applied) to the prod project, CellWatch Server. Pushing is done by hand with `psql`, one file at a time, per the prod deploy runbook (`prod-deploy-runbook.md`, a local file not in git). There is no CI deploy.
- **App:** an app change is in prod only once it is in a **Play Store release**: a `versionCode` bump, a signed release build, and a rollout in Play Console.

**Rule zero: server first, app second.** Every migration an app build depends on must be pushed to prod before that build is released.

## Where things stand (2026-10-26; prod schema compared 2026-10-22)

| Stage | Supabase | App |
|---|---|---|
| In prod | `full_schema.sql` plus `202610070001`: prod's `public`, `private` and `published` schemas matched them, apart from Postgres 17's MAINTAIN bit in table grants. The cron job's batch size wasn't checked ([Runbooks → cron](runbooks.md#recreating-cron-jobs)). | The Play build, versionCode 13. The repo doesn't record which commit it was built from. |
| Merged to `main`, not known to be in prod | none | c51980e (2026-07-14, FCC validation and carrier fallback), #111 (upload loop) and #112 (Crashlytics scrubbing), both 2026-10-07. `versionCode` is still 13, so check Play Console before assuming any of them is released. |
| Open PRs (unmerged) | #136 `202610220101`/`0102`, #132 `202610210001`, #133 `202610210101`–`0104`, #134 baseline `202609300001` | #128–#131, #135, #136 |
| Not in any PR | `202610130001` (uncommitted on the `client-port-header` branch); `202610220001` (unpushed local branch `register-device-retry`); the "One location" hold (unpushed local commit 75289ca on the #133 branch) | Local-port reporting (uncommitted on `client-port-header`); unpushed local branches `background-upload` and `register-device-retry`; the two-location rule (unpushed local commit 6ecfcaa on the #130 branch) |

## Why the order matters

- **supabase-kt 1.0.3** (the app's client) serializes with `ignoreUnknownKeys = true` and `encodeDefaults = false`:
  - New **response** columns are safe: old apps ignore them.
  - **Null fields are omitted** from request bodies, so column defaults and `WHEN (x IS NULL)` triggers still fire.
  - A **new non-null request field** fails the insert (PostgREST: unknown column) until the migration adding the column is pushed to prod. Example: the uncommitted app change on the `client-port-header` branch sends `tuple_local_port`, which needs `202610130001_record_local_ports.sql` pushed to prod first, or **every FCC submission upload fails**.
- **PostgREST picks an RPC overload by the argument names in the request body.** A new overload, such as `register_device(device_id, device_secret)` from `202610220001` (unpushed local branch `register-device-retry`), must exist on prod before a released app calls it. Otherwise the call fails, and new installs can't register or upload.

## Checklist

### 1. Server (push to prod)

- [ ] List every migration under [`supabase/migrations/`](../../supabase/migrations/) that isn't pushed to prod yet (see the table above), and every new request field or RPC the release's app code uses.
- [ ] Rehearse the push on the test project, **CellWatch Test Database** (same Supabase org). On 2026-10-22 its schema was brought up to match prod's, with deliberate differences: the FCC auto-post trigger is disabled and pg_cron/pg_graphql aren't installed, so nothing there posts to the FCC, and the cron steps can't be rehearsed there. Its `private.fcc_submission_info` still has a `prod` row, so don't re-enable the trigger and don't run `private.process_fcc_submission_retries` there by hand.
- [ ] Push the migrations to prod **in filename order**, pausing the retry job around FCC-pipeline changes. For the October 2026 set (baseline `202609300001` from #134, RLS `202610210001` from #132, pipeline `202610210101`–`0104` from #133, then the held-entry re-queue), follow **`prod-deploy-runbook.md` (local file, not in git)** step by step. In summary:
  1. Read-only pre-checks: is the unclog migration live, what is the cron job state, does a migration-history table exist, and what does the queue look like.
  2. Pause `fcc-submission-retries`.
  3. Apply the baseline (no-op), then the RLS fix, then the pipeline migrations in order. Each file is one transaction; run the check after each.
  4. Resume the job and confirm a `succeeded` run. If `CALL` can't commit under pg_cron, fall back to the `select …` command.
  5. Check queue health, then re-queue the held entries.
  6. Refresh and commit the schema snapshot, and record the versions in the migration history if that table exists.
- [ ] Migrations the runbook doesn't cover:
  - #136's `202610220101` (dashboard RPCs) and `202610220102` (database helpers). Before pushing `202610220101`, set `DASHBOARD_SECRET` on the dashboard backend (the CellWatch/demo-dashboard Flask service on Render) to the shared secret in `private.dashboard_settings`. Otherwise the dashboard's RPC calls are refused and its map breaks.
  - `202610270001` (dashboard speed fallback): push it after `202610220101`.
  - `202610130001` (uncommitted on the `client-port-header` branch): push it before releasing an app that sends `tuple_local_port`.
  - `202610220001` (unpushed local branch `register-device-retry`): push it before releasing an app built with that branch.
- [ ] Re-run [db tests](../dev/testing.md#database-tests) on the release commit.
- [ ] Confirm the schema snapshot has no drift (see [Runbooks → snapshot](runbooks.md#refreshing-the-local-schema-snapshot)).

### 2. FCC environment

- [ ] On prod, read-only: `select env, url from private.fcc_submission_info;`. There must be a `prod` row pointing at the BDC production endpoint. The insert trigger queues every submission with `fcc_env = 'prod'` ([full_schema.sql](../../supabase/full_schema.sql), `auto_post_fcc_submission`).
- [ ] Test or staging databases must **not** hold prod BDC credentials. The CellWatch Test Database project still has a `prod` row in `private.fcc_submission_info` (see the cautions in step 1). A `demo` env pointing at the BDC demo endpoint is sketched, commented out, in [post_fcc_submission_func.sql](../../supabase/post_fcc_submission_func.sql); test against it rather than prod.
- [ ] `cron.job` shows `fcc-submission-retries` active, with recent `succeeded` runs.

### 3. App build (for the Play release)

- [ ] Branch is up to date. Unit tests pass: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`.
- [ ] Bump **`versionCode`** in [app/build.gradle](../../app/build.gradle). It is `13` on main, unchanged since #109 (2025-10-13), and the Play Store build is also versionCode 13, so Play needs a higher one.
- [ ] Decide **`versionName`**. It is still `"1.0.0"` and is sent to the BDC as `app_version`, which the spec says must match an approved version string. > TODO: confirm with the FCC which versions are approved before changing it.
- [ ] **Release config** (`buildTypes.release`): `SUPABASE_URL` / `SUPABASE_API_KEY` from the cloud keys in `cellwatch.properties`; `MSAK_SERVER_ENV` hard-coded `"prod"`; `MSAK_LOCAL_SERVER_HOST` `""`; `MSAK_LOCAL_SERVER_SECURE` `true`; `TCP_TUPLE_URL` (same value as debug). Check that the root `cellwatch.properties` has the **cloud** values filled in. Debug builds use the `*_LOCAL_*` keys.
- [ ] **Target API:** Google Play requires app updates to target **API 36**. Main targets 35 on AGP 8.1.2, so open PR #135 (compileSdk/targetSdk 36, AGP 8.7.3, Gradle 8.9, edge-to-edge insets) must be merged and included in this release. Check the current deadline in Play Console, and check Play's 16 KB page-size requirement there too.
- [ ] **Signing:** `app/build.gradle` has no `signingConfigs`. Confirm who holds the upload key / Play App Signing access before release day.
- [ ] Build the signed release from a clean checkout of the release tag. `minifyEnabled` is false. Git release tags stop at `v1.0.0` (2024-11-21, versionCode 8), so no tag records the commit behind the current Play build.

### 4. Pre-release testing

- [ ] Manual test on a phone against non-production servers: run an FCC-mode test and confirm the measurements and submission upload.
- [ ] **Upgrade test** from the Play build (versionCode 13) with results recorded offline: after the upgrade, they must upload.
- [ ] Instrumented tests only on a disposable, offline AVD ([Testing → cautions](../dev/testing.md#cautions)).

### 5. Rollout

- [ ] In Play Console: internal testing track first, then a staged production rollout. The app changes are released only once that rollout reaches users. Halt if submission inserts start failing (see below).
- [ ] Tag the commit and create a GitHub Release with migration notes: which migrations had to be pushed to prod before it.

A fuller release checklist (`doc/release-checklist.md`, `doc/environments.md`) is in open PR #134 (`board-docs`). Its `environments.md` says it is unknown whether a staging project exists; the CellWatch Test Database project does (see step 1). > TODO: reconcile with this page once #134 merges.

## Post-release verification (read-only)

`app_version` can't tell builds apart, so use time windows and fields only the new build sends. Use [read-only prod SQL](runbooks.md#read-only-prod-sql) and `\set released_at '2026-…+00'`.

```sql
-- Uploads still arriving
select date_trunc('hour', created_on) as hour, count(*)
from public.measurements
where created_on > :'released_at'
group by 1 order by 1;

-- Submissions arriving
select count(*) as submissions
from public.fcc_submissions
where created_on > :'released_at';
-- Only once 202610130001 is pushed to prod and the release sends tuple_local_port
-- (the column doesn't exist on prod before that), count rows from the new build:
-- select count(tuple_local_port) as from_new_build
-- from public.fcc_submissions where created_on > :'released_at';

-- Posting outcomes since release
select succeeded, left(coalesce(error_message, response_snippet), 60) as what, count(*)
from private.fcc_submission_failures
where occurred_on > :'released_at'
group by 1, 2 order by 3 desc;

-- Queue not growing
select state, left(last_error, 60), count(*)
from private.fcc_submission_retry_queue
group by 1, 2 order by 3 desc;

-- Retry job healthy
select status, count(*) from cron.job_run_details
where jobid = (select jobid from cron.job where jobname = 'fcc-submission-retries')
  and start_time > :'released_at'
group by 1;
```

Also watch Crashlytics for new non-fatals from the release build (insert failures surface as `Log.e` from `MeasurementUploader`). If `202610130001` was pushed and the release includes the `client-port-header` app change, check `private.source_port_comparison` after a few days.
