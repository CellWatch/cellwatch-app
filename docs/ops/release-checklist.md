# Release checklist

Purpose: the ordered steps for shipping a new Android build without breaking uploads or FCC posting.

Related: [Runbooks](runbooks.md) · Prod deploy runbook (`prod-deploy-runbook.md`, local only) · [Migrations and deploy](../supabase/migrations-and-deploy.md) · [Build and config](../app/build-and-config.md) · [Testing](../dev/testing.md) · [BDC spec mapping](../fcc/bdc-spec-mapping.md)

**Rule zero: server first, app second.** Every migration an app build depends on must be live on prod before that build reaches any phone.

## Why the order matters

- **supabase-kt 1.0.3** (the app's client) serializes with `ignoreUnknownKeys = true` and `encodeDefaults = false`:
  - New **response** columns are safe: old apps ignore them.
  - **Null fields are omitted** from request bodies, so column defaults and `WHEN (x IS NULL)` triggers still fire.
  - A **new non-null request field** fails the insert (PostgREST: unknown column) until the migration adding the column is live. Example: this branch's app sends `tuple_local_port` (*pending*), which needs `202610130001_record_local_ports.sql` on prod first, or **every FCC submission upload fails**.
- **PostgREST picks an RPC overload by the argument names in the request body.** A new overload, such as `register_device(device_id, device_secret)` from the pending `202610220001`, must exist before an app calls it. Otherwise the call fails, and new installs can't register or upload.

## Checklist

### 1. Server

- [ ] List every migration the release branch adds under [`supabase/migrations/`](../../supabase/migrations/), and every new request field or RPC the app uses.
- [ ] Deploy them to prod **in filename order**, pausing the retry job around FCC-pipeline changes. For the October 2026 set (baseline `202609300001`, RLS `202610210001`, pipeline `202610210101`–`0104`, then the held-entry re-queue), follow **`prod-deploy-runbook.md` (local file, not in git)** step by step. In summary:
  1. Read-only pre-checks: is the unclog migration live, what is the cron job state, does a migration-history table exist, and what does the queue look like.
  2. Pause `fcc-submission-retries`.
  3. Apply the baseline (no-op), then the RLS fix, then the pipeline migrations in order. Each file is one transaction; run the check after each.
  4. Resume the job and confirm a `succeeded` run. If `CALL` can't commit under pg_cron, fall back to the `select …` command.
  5. Check queue health, then re-queue the held entries.
  6. Refresh and commit the schema snapshot, and record the versions in the migration history if that table exists.
- [ ] App-dependent migrations not covered there: `202610130001` (if this branch ships) and `202610220001` (if `register-device-retry` ships).
- [ ] Re-run [db tests](../dev/testing.md#database-tests) on the release commit.
- [ ] Confirm the schema snapshot has no drift (see [Runbooks → snapshot](runbooks.md#refreshing-the-local-schema-snapshot)).

### 2. FCC environment

- [ ] On prod, read-only: `select env, url from private.fcc_submission_info;`. There must be a `prod` row pointing at the BDC production endpoint. The insert trigger queues every submission with `fcc_env = 'prod'` ([full_schema.sql](../../supabase/full_schema.sql), `auto_post_fcc_submission`).
- [ ] Test or staging databases must **not** hold prod BDC credentials. A demo env (`bdc-demo`) is described in [post_fcc_submission_func.sql](../../supabase/post_fcc_submission_func.sql); test against it rather than prod.
- [ ] `cron.job` shows `fcc-submission-retries` active, with recent `succeeded` runs.

### 3. App build

- [ ] Branch is up to date. Unit tests pass: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`.
- [ ] Bump **`versionCode`** in [app/build.gradle](../../app/build.gradle) (currently `13`, which is on Play).
- [ ] Decide **`versionName`**. It is still `"1.0.0"` and is sent to the BDC as `app_version`, which the spec says must match an approved version string. > TODO: confirm with the FCC which versions are approved before changing it.
- [ ] **Release config** (`buildTypes.release`): `SUPABASE_URL` / `SUPABASE_API_KEY` from the cloud keys in `cellwatch.properties`; `MSAK_SERVER_ENV` hard-coded `"prod"`; `MSAK_LOCAL_SERVER_HOST` `""`; `MSAK_LOCAL_SERVER_SECURE` `true`; `TCP_TUPLE_URL` (same value as debug). Check that the root `cellwatch.properties` has the **cloud** values filled in. Debug builds use the `*_LOCAL_*` keys.
- [ ] **Target API:** Google Play requires app updates to target **API 36**. This checkout targets 35 on AGP 8.1.2, so the upgrade (#135: compileSdk/targetSdk 36, AGP 8.7.3, Gradle 8.9, edge-to-edge insets) must merge first. > TODO: verify the current Play deadline in Play Console; notes record an extension to 2026-11-01. Also note Play's 16 KB page-size requirement, reportedly from 2027-02-01 (unconfirmed).
- [ ] **Signing:** `app/build.gradle` has no `signingConfigs`. Confirm who holds the upload key / Play App Signing access before release day.
- [ ] Build from a clean checkout of the release tag. `minifyEnabled` is false.

### 4. Pre-release testing

- [ ] Manual test on a phone against non-production servers: run an FCC-mode test and confirm the measurements and submission upload.
- [ ] **Upgrade test** from the Play build (versionCode 13) with results recorded offline: after the upgrade, they must upload.
- [ ] Instrumented tests only on a disposable, offline AVD ([Testing → cautions](../dev/testing.md#cautions)).

### 5. Rollout

- [ ] Internal testing track first, then a staged production rollout. Halt if submission inserts start failing (see below).
- [ ] Tag the commit and create a GitHub Release with migration notes.

A fuller release checklist (`doc/release-checklist.md`, `doc/environments.md`) is on the unmerged `board-docs` branch (#134). > TODO: reconcile with this page once merged.

## Post-release verification (read-only)

`app_version` can't tell builds apart, so use time windows and fields only the new build sends. Use [read-only prod SQL](runbooks.md#read-only-prod-sql) and `\set released_at '2026-…+00'`.

```sql
-- Uploads still arriving
select date_trunc('hour', created_on) as hour, count(*)
from public.measurements
where created_on > :'released_at'
group by 1 order by 1;

-- Submissions arriving; rows with tuple_local_port come from builds that send it (pending field)
select count(*) as submissions,
       count(tuple_local_port) as from_new_build
from public.fcc_submissions
where created_on > :'released_at';

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

Also watch Crashlytics for new non-fatals from the release build (insert failures surface as `Log.e` from `MeasurementUploader`). If the build ships `202610130001`, check `private.source_port_comparison` after a few days.
