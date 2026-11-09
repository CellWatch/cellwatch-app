# Release checklist

Purpose: the ordered steps for shipping a new Android build without breaking uploads or FCC posting.

Related: [Runbooks](runbooks.md) · Prod deploy runbook (`prod-deploy-runbook.md`, local only) · [Migrations and deploy](../supabase/migrations-and-deploy.md) · [Build and config](../app/build-and-config.md) · [Testing](../dev/testing.md) · [BDC spec mapping](../fcc/bdc-spec-mapping.md)

**Prod has two separate paths, and merging a PR to `main` is neither:**
- **Supabase:** a server change is in prod only once its migration has been **pushed** (applied) to the prod project, CellWatch Server. Pushing is done by hand with `psql`, one file at a time, per the prod deploy runbook (`prod-deploy-runbook.md`, a local file not in git). There is no CI deploy.
- **App:** an app change is in prod only once it is in a **Play Store release**: a `versionCode` bump, a signed release build, and a rollout in Play Console.

**Rule zero: server first, app second.** Every migration an app build depends on must be pushed to prod before that build is released.

## Where things stand (2026-11-09)

| Stage | Supabase | App |
|---|---|---|
| In prod | [full_schema.sql](../../supabase/full_schema.sql), [cron_jobs.sql](../../supabase/cron_jobs.sql) and [role_settings.sql](../../supabase/role_settings.sql), pulled from prod on 2026-11-02 after that day's push (#177). That is **every migration on `main`**: `202607140001` and `202610070001`, applied by hand earlier; baseline `202609300001` (#134), RLS `202610210001` (#132), and pipeline `202610210101`, `0103` and `0104` (#133), pushed on 2026-10-29; registration `202610220001` (#148), database helpers `202610220102` (#136) and the digest email `202610310001` (#154), pushed on 2026-10-31; the digest ignore list `202610310002` (#156), dashboard RPCs `202610220101` (#136), the dashboard summary `202611010001` (#159), the digest's sent count `202611010002` (#162) and the `authenticated` timeout `202611010003` (#164), pushed on 2026-11-01; and the once-per-request device check `202611010004` (#176), pushed on 2026-11-02. All are recorded in prod's `supabase_migrations.schema_migrations`. Nothing under `supabase/` has changed on `main` since #177. Jobs: `fcc-submission-retries` runs `call private.run_fcc_submission_retries(10);`, `fcc-queue-digest` emails the queue digest daily when something is new, and `dashboard-group-points-refresh` refreshes the dashboard's summary every 5 minutes. | The Play build: versionCode 13 (1.0.0), from `2eab6c4`. No release tag records it. |
| Merged to `main`, not in prod | None: every migration on `main` is pushed. | **Not released.** `main` at `26891760` is the 1.1.0 candidate: versionCode 14 and versionName 1.1.0 since #166. Its app changes since the Play build are listed below the table. |
| Open PRs | none | none |
| Not in any PR | `202610130001` (uncommitted on the `client-port-header` branch) | Local-port reporting (uncommitted on `client-port-header`). Changes to how a test is measured or what it records, held back because the measurement process is to stay as approved: carrier aggregation, SIM-first carrier name, the generation check, the success-flag rework, the two-location rule, the closest-server pick and the export `test_id`, on the local branch `measurement-process-changes`. #128–#131 and #135 are closed, and #148 took their other commits; #149 is closed, and #151 took its fix. |

App changes on `main` since the Play build, none of them released:
- **2026-10-07:** #111 (upload loop) and #112 (Crashlytics scrubbing).
- **2026-10-27:** #136's app fixes, and #148 (API 36, hourly and foreground uploads, 60 s timeouts, registration retry, upload status, progress bar). #148 also reverted c51980e's carrier fallback (2026-07-14), so main's carrier name matches the released build again. #150 took #136's server choice and #148's export `test_id` back out.
- **2026-11-01 and 2026-11-02:** #168 (app data kept off new phones), #169 (onboarding names the missing permissions), #170 (a plain message when map search fails), #171 (the Spanish Cancel text), #172 (three bugs the unit tests found), #174 (notification permission asked before the first test), #178 (a second tap on Go is ignored) and #179 (a TODO comment removed from `ping.kt`). #167 added tests only.
- **2026-11-05:** #181 (uploads wait while a test runs) and #182 (16 KB-aligned Mapbox, H3 and Conscrypt).

Not done yet for 1.1.0: the signed build, the upgrade test with Play-signed builds, the FCC docket filing, the Play rollout and the `v1.1.0` tag.

## Why the order matters

- **supabase-kt 1.0.3** (the app's client) serializes with `ignoreUnknownKeys = true` and `encodeDefaults = false`:
  - New **response** columns are safe: old apps ignore them.
  - **Null fields are omitted** from request bodies, so column defaults and `WHEN (x IS NULL)` triggers still fire.
  - A **new non-null request field** fails the insert (PostgREST: unknown column) until the migration adding the column is pushed to prod. Example: the uncommitted app change on the `client-port-header` branch sends `tuple_local_port`, which needs `202610130001_record_local_ports.sql` pushed to prod first, or **every FCC submission upload fails**.
- **PostgREST picks an RPC overload by the argument names in the request body.** A new overload, such as `register_device(device_id, device_secret)` from `202610220001` (#148), must exist on prod before a released app calls it. Main's app calls it; without that migration, new installs of a release built from main couldn't register or upload. It was pushed on 2026-10-31.

## Checklist

### 1. Server (push to prod)

- [ ] List every migration under [`supabase/migrations/`](../../supabase/migrations/) that isn't pushed to prod yet (see the table above), and every new request field or RPC the release's app code uses.
- [ ] Rehearse the push on the test project, **CellWatch Test Database** (same Supabase org). On 2026-11-02 its schema was brought up to match prod's again (every migration on `main`, recorded in its `supabase_migrations.schema_migrations` as on prod), with deliberate differences: the FCC auto-post trigger is disabled and pg_cron, pg_net and pg_graphql aren't installed, so nothing there posts to the FCC, and the cron steps can't be rehearsed there. Since 2026-11-03 its `private.fcc_submission_info` has only the `demo` row, so a post to `prod` there fails ("missing FCC submission info for env prod"). Keep the trigger off, and don't run `private.process_fcc_submission_retries` there by hand.
- [ ] Push the migrations to prod **in filename order**, pausing the retry job around changes to the FCC pipeline or the upload tables. Follow **`prod-deploy-runbook.md` (local file, not in git)** step by step. In summary:
  1. Read-only pre-checks: is the previous migration live, what is the cron job state, and what does the queue look like.
  2. Pause `fcc-submission-retries` if the migration touches the FCC pipeline or the upload tables.
  3. Apply each file (one transaction each) and run its check.
  4. Resume the job and confirm a `succeeded` run.
  5. Check queue health.
  6. Refresh and commit the schema snapshot (`supabase/check_drift.sh --write`), and record each pushed version in `supabase_migrations.schema_migrations`, as was done for the October 2026 set.

  The pipeline set (`202609300001`, `202610210001`, `0101`, `0103`, `0104`) was pushed this way on 2026-10-29. That deploy applied to new submissions only (decided 2026-10-27): entries already in the queue were left as they were, and the re-queue of the held 2026-08-17 entries was skipped. `202610220001` (registration retry, which a release built from main needs), `202610220102` (database helpers) and `202610310001` (digest email) followed on 2026-10-31, and `202610310002` (digest ignore list), `202610220101` (dashboard RPCs, after the dashboard backend's `DASHBOARD_SECRET` was checked against prod's), `202611010001` (dashboard summary), `202611010002` (digest sent count) and `202611010003` (`authenticated` timeout) on 2026-11-01, and `202611010004` (device check once per request) on 2026-11-02. As of 2026-11-09 nothing merged to `main` is waiting to be pushed.
- [ ] Not in a PR: `202610130001` (uncommitted on the `client-port-header` branch). Push it before releasing an app that sends `tuple_local_port`.
- [ ] Re-run [db tests](../dev/testing.md#database-tests) on the release commit.
- [ ] Confirm the schema snapshot has no drift (see [Runbooks → snapshot](runbooks.md#refreshing-the-local-schema-snapshot)).

### 2. FCC environment

- [ ] On prod, read-only: `select env, url from private.fcc_submission_info;`. There must be a `prod` row pointing at the BDC production endpoint. The insert trigger queues every submission with `fcc_env = 'prod'` ([full_schema.sql](../../supabase/full_schema.sql), `auto_post_fcc_submission`).
- [ ] Test or staging databases must **not** hold prod BDC credentials. The CellWatch Test Database's `prod` row and its Vault secret were removed on 2026-11-03; its only env is `demo`, which points at the BDC demo endpoint (see the cautions in step 1). Test posting against `demo` rather than prod. A `demo` env is also sketched, commented out, in [post_fcc_submission_func.sql](../../supabase/post_fcc_submission_func.sql).
- [ ] `cron.job` shows `fcc-submission-retries` active, with recent `succeeded` runs.

### 3. App build (for the Play release)

- [ ] Branch is up to date. Unit tests pass: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`.
- [x] Bump **`versionCode`** in [app/build.gradle](../../app/build.gradle): `14` for the next release (the Play Store build is versionCode 13, and Play needs a higher one).
- [x] Decide **`versionName`**: `"1.1.0"`. It is sent to the BDC as `app_version`, which the spec says must match an approved version string; the FCC has approved `1.*` (confirmed 2026-11-01), so any `1.x` is accepted. `1.1.0` hasn't been used before (prod has seen `1.0`, `1.0.1` and `1.0.0`), so the new build's data can be told apart.
- [ ] **Release config** (`buildTypes.release`): `SUPABASE_URL` / `SUPABASE_API_KEY` from the cloud keys in `cellwatch.properties`; `MSAK_SERVER_ENV` hard-coded `"prod"`; `MSAK_LOCAL_SERVER_HOST` `""`; `MSAK_LOCAL_SERVER_SECURE` `true`; `TCP_TUPLE_URL` (same value as debug). Check that the root `cellwatch.properties` has the **cloud** values filled in. Debug builds use the `*_LOCAL_*` keys.
- [ ] **Target API and 16 KB pages:** Google Play requires app updates to target **API 36**, and from 2027-02-01 to support 16 KB memory pages. Main targets 36 since #148 (merged 2026-10-27: compileSdk/targetSdk 36, AGP 8.9.3, Gradle 8.11.1, edge-to-edge insets). Its native libraries support 16 KB pages since #182 (merged 2026-11-05: Mapbox, H3 and Conscrypt). So a release built from main meets both. Check the current deadlines in Play Console, and confirm both there for the uploaded bundle.
- [ ] **Signing:** `app/build.gradle` has no `signingConfigs`. Confirm who holds the upload key / Play App Signing access before release day.
- [ ] Build the signed release from a clean checkout of the release tag. `minifyEnabled` is false. Git release tags stop at `v1.0.0` (2024-11-21, versionCode 8), so no tag records the commit behind the current Play build.

### 4. Pre-release testing

- [ ] Manual test on a phone against non-production servers: run an FCC-mode test and confirm the measurements and submission upload.
- [ ] **Upgrade test** from the Play build (versionCode 13) with results recorded offline: after the upgrade, nothing uploads until the app is opened, and then they must upload within seconds. A debug-signed run on an emulator (`2eab6c4` to `26891760`, local Supabase) passed on 2026-11-09. The run with Play-signed builds is still to do.
- [ ] Instrumented tests only on a disposable, offline AVD ([Testing → cautions](../dev/testing.md#cautions)).

### 5. Rollout

- [ ] File a description of the changes on the FCC docket before submitting to the Play Store (the measurement method and data are unchanged from the approved build; see [decisions](../architecture/decisions.md)).
- [ ] In Play Console: internal testing track first, then a staged production rollout. The app changes are released only once that rollout reaches users. Halt if submission inserts start failing (see below).
- [ ] Tag the commit and create a GitHub Release with migration notes: which migrations had to be pushed to prod before it.

[doc/release-checklist.md](../../doc/release-checklist.md) keeps the details this page leaves out: the native libraries to check for 16 KB pages, what the upgrade test checks, the release keys and signing, and when to halt the rollout. [doc/environments.md](../../doc/environments.md) describes which servers each build talks to and proposes a staging setup.

## Post-release verification (read-only)

Tests recorded by the release send `app_version` `'1.1.0'`, which no earlier build used. A test keeps the version it was recorded with, so a 1.0.0 backlog still arrives as `'1.0.0'` after the update. Play-signed 1.1.0 builds on the internal testing track also send `'1.1.0'`, before the release. So count by `app_version` within a time window. Use [read-only prod SQL](runbooks.md#read-only-prod-sql) and `\set released_at '2026-…+00'`.

```sql
-- Uploads still arriving
select date_trunc('hour', created_on) as hour, count(*)
from public.measurements
where created_on > :'released_at'
group by 1 order by 1;

-- Uploads by build. '1.0.0' rows come from phones not updated yet,
-- or from tests recorded before the update.
select app_version, count(*) as measurements
from public.measurements
where created_on > :'released_at'
group by 1 order by 2 desc;

-- Submissions arriving, by build
select app_version, count(*) as submissions
from public.fcc_submissions
where created_on > :'released_at'
group by 1 order by 2 desc;
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
