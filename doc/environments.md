# Production vs development servers

For the board item "Production vs development server?". It describes which servers each build talks to today,
and proposes a staging setup. The proposal needs a team decision.

## Current state

### What each build type uses

From `app/build.gradle` (`buildTypes`) and the keys in `cellwatch.properties`:

| Service | Debug build | Release build |
|---|---|---|
| Supabase | `SUPABASE_LOCAL_URL` / `SUPABASE_LOCAL_API_KEY` | `SUPABASE_URL` / `SUPABASE_API_KEY` |
| M-Lab locate and test servers | `MSAK_SERVER_ENV`: `local` (`MSAK_LOCAL_SERVER_HOST`, `MSAK_LOCAL_SERVER_SECURE`), `staging` (`locate-dot-mlab-staging.appspot.com`) or `prod` (`locate.measurementlab.net`) | always `prod`, secure |
| M-Lab latency port | `MSAK_LOCAL_LATENCY_PORT` | `MSAK_LATENCY_PORT` |
| TCP tuple service | `TCP_TUPLE_URL` (the EC2 box) | same |
| Firebase Crashlytics | `app/google-services.json` | same project. Reports carry a `debug` custom key. |
| Mapbox | `mapbox_access_token` in `strings.xml` | same token |
| Application id, `versionName` | `edu.gatech.cc.cellwatch`, `1.0.0` | same. The only difference is the User-Agent (`CellWatch/1.0.0-debug`), which the server doesn't store. |

Notes:
- **"Local" Supabase on a phone.** The README template sets `SUPABASE_LOCAL_URL="http://127.0.0.1:54321"`. On
  an emulator or a phone, `127.0.0.1` is the device itself. It only reaches the Mac's stack with
  `adb reverse tcp:54321 tcp:54321`, `10.0.2.2` on an emulator, or the Mac's LAN address on a phone.
- **A tracked config that points debug at production.** `app/cellwatch.properties` is tracked but not read by
  the build (the root file is). It sets `SUPABASE_LOCAL_URL` to the **production** project, so anyone who copies
  it to the root runs debug builds against production.

### Supabase projects

The repo knows of one remote project: **CellWatch Server** (`xepxxvpbexkyxrwtrgqv`), in `sync_from_remote.sh`.
There's no staging project in the repo. Whether one exists outside it is unknown.

### FCC (BDC) environments

- **Where the URLs and tokens live.** `private.fcc_submission_info` holds one row per environment: `env`, the
  BDC URL, and a Vault secret holding the token. The setup comment in `supabase/post_fcc_submission_func.sql`
  shows a `demo` row for `https://bdc-demo.fcc.gov/api/mobiletest/create`. The live table's contents aren't in
  the snapshot, so whether `demo` and `prod` rows exist there now is unknown.
- **Everything goes to production.** `private.auto_post_fcc_submission()` (the trigger on every
  `fcc_submissions` insert) queues with `fcc_env = 'prod'` hard-coded, and the queue's default is `'prod'`. So
  every submission that reaches the live project is posted to the FCC's **production** BDC, whichever build
  sent it.
- **Locally, posting usually fails harmlessly.** A fresh local stack (`supabase start`, empty `seed.sql`) has
  no `fcc_submission_info` rows. A submission against it is queued, and posting it would fail with "missing FCC
  submission info".
- **Except after a sync.** A local stack rebuilt by `sync_from_remote.sh` loads production data and the
  production extensions. It may hold the production `fcc_submission_info` row, and running migration
  202610070001 there schedules the cron job. Whether the Vault secret survives the copy hasn't been checked.
  Treat such a stack as able to post to the FCC: delete those rows, or unschedule the job, after a sync.

### What this means

Developer testing in FCC Challenge mode is safe only when the debug build really points at local Supabase.
Testing with a release build, an internal Play test track, or a debug build pointed at production files
Consumer Challenge data with the FCC under the tester's contact details. Nothing on the server tells test
devices from real ones.

## Proposal

1. **A staging Supabase project** (for example "CellWatch Staging"):
   - build its schema from the live snapshot plus `supabase/migrations` (see supabase/README.md), and keep it
     in step by applying every migration to staging first;
   - put only a `demo` row in its `private.fcc_submission_info`, with the BDC demo token, if the FCC still
     issues one (#98 used it);
   - make the queue environment configurable instead of hard-coding `'prod'`, for example a one-row
     `private.fcc_settings(env)` that the trigger reads: `prod` on production, `demo` on staging. Ship that
     as a migration.

   Cost: check the current Supabase plan. Free-tier projects pause when idle, and paid projects are billed
   per project (unverified).
2. **Point debug builds at staging by default:**
   - `SUPABASE_LOCAL_*` becomes `SUPABASE_STAGING_*` in the template; local Supabase stays possible by
     overriding it;
   - add `applicationIdSuffix ".debug"` so debug and release can be installed side by side. That needs a
     matching Android app in Firebase (`google-services.json` must list `edu.gatech.cc.cellwatch.debug`), or a
     separate Firebase project for debug;
   - consider `versionNameSuffix "-debug"` so test data is labelled in `app_version`. Only do this once debug
     builds can't reach production, because the FCC checks `app_version` (README.md, "Versioning").

   If staging, debug and release need more differences later, switch to product flavors (`dev` / `prod`).
3. **A guard on production that works regardless of the app.** Keep a `private.test_devices` list of developer
   device ids (shown in Settings). The production trigger holds their submissions, or sends them to `demo`.
   It's cheap, needs no app release, and covers release builds on testers' phones.
4. **M-Lab:** use `MSAK_SERVER_ENV="staging"` for routine debug testing, so development traffic stays off M-Lab
   production, which publishes its test data. Use `prod` only when checking real-world behaviour.
5. **Delete the tracked `app/cellwatch.properties`** (also flagged in doc/open-source-dependency-review.md).
6. **Mapbox:** a separate development token, so debug use and forks don't count against the production token.

**Decisions for the team:**
- Do we pay for a staging project?
- Does the FCC still issue a demo-environment token?
- Should production hold test devices, or send them to `demo`?
