# Testing

Purpose: how to run CellWatch's unit, database and instrumented tests, what each suite covers, and the side effects to avoid.

Related: [Local setup](local-setup.md) · [Sync and upload](../app/sync-and-upload.md) · [FCC submission pipeline](../supabase/fcc-submission-pipeline.md) · [Release checklist](../ops/release-checklist.md)

| Suite | Location | Command | Needs |
|---|---|---|---|
| Unit (JVM) | [app/src/test](../../app/src/test/java/edu/gatech/cc/cellwatch/) | `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` | JDK 17, Android SDK, `cellwatch.properties` |
| Database | [supabase/db_tests](../../supabase/db_tests/) | `supabase/db_tests/run.sh` | Docker |
| Instrumented | [app/src/androidTest](../../app/src/androidTest/java/edu/gatech/cc/cellwatch/) | `./gradlew :app:connectedDebugAndroidTest` (see the caveats below) | Emulator or device |

## Unit tests

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
# offline / forced re-run:
./gradlew --offline :app:compileDebugKotlin :app:testDebugUnitTest --rerun
```

`compileDebugKotlin` also runs resource processing, so resource errors show up without building an APK. Prefer this over `assembleDebug`, because a debug APK is wired to Crashlytics.

What's covered (53 tests on main after #136, #148 and #150, all merged 2026-10-27):

| Test class | Covers |
|---|---|
| [MeasurementUploaderTest](../../app/src/test/java/edu/gatech/cc/cellwatch/data/core/sync/MeasurementUploaderTest.kt) (11) | Upload ordering and idempotency against fakes: duplicate-key responses mark items uploaded, at most 3 requests in flight, overlapping runs don't double-send, a network error leaves only that item pending, a submission waits for all three measurements, tuple failure leaves submissions pending, and cancellation isn't logged as an error. Uncommitted on the `client-port-header` branch: also asserts `tupleLocalPort` is copied from the tuple. |
| [RedactSecretsTest](../../app/src/test/java/edu/gatech/cc/cellwatch/core/util/RedactSecretsTest.kt) (5) | `redactSecrets` strips device secrets, JWTs and auth headers from exception messages and cause chains before they reach Crashlytics. |
| [OnboardingScreenTest](../../app/src/test/java/edu/gatech/cc/cellwatch/ui/main/OnboardingScreenTest.kt) (3) | Added in #136: FCC Challenge mode goes through the FCC info screen, Testing mode skips it, and `next()` returns null instead of throwing for screens with no next screen (`READ_MORE`, `PERMISSIONS`). |
| [UploadWorkerTest](../../app/src/test/java/edu/gatech/cc/cellwatch/data/core/sync/UploadWorkerTest.kt) (1) | Added in #148: the periodic upload runs hourly and needs a connection. |
| [AllowSlowRequestsTest](../../app/src/test/java/edu/gatech/cc/cellwatch/data/network/AllowSlowRequestsTest.kt) (3) | Added in #148: the supabase-kt and OkHttp clients give up on a request that outlasts the timeout, and the TCP tuple fetch waits as long as the Supabase requests. |
| [DeviceRegistrationTest](../../app/src/test/java/edu/gatech/cc/cellwatch/data/network/DeviceRegistrationTest.kt) (6) | Added in #148: a new phone saves its secret before registering, a lost response is retried with the same secret, a registered phone doesn't register again, a phone registered by an older version confirms its secret once, secrets are 64 hex digits, and the request sends the id and secret. |
| [MeasurementGroupUploadTimeTest](../../app/src/test/java/edu/gatech/cc/cellwatch/data/model/MeasurementGroupUploadTimeTest.kt) (8) | Added in #148: a group counts as uploaded only once each of its measurements and its submission are, and then takes the latest upload time. |
| [FccExportExtensionsTest](../../app/src/test/java/edu/gatech/cc/cellwatch/data/model/FccExportExtensionsTest.kt) (2) | Added in #148, changed in #150: the export's `test_id` is the latency measurement's ID, as in the released build; a group without a submission isn't exported. |
| [MeasureStepsTest](../../app/src/test/java/edu/gatech/cc/cellwatch/ui/main/MeasureStepsTest.kt) (14) | Added in #148: the measure screen's 1-2-3 step indicator: per-step time budgets from the configured test durations, countdown, failed and errored steps, and the end state. |
| `LocalPortHeadersTest` (2) | Uncommitted (untracked) on the `client-port-header` branch: the `X-Local-Port` header equals the client port a local server saw; no `X-Transport` header when the network is unknown. |

Constraints:
- Unit tests compile against `android.jar`, so `com.sun.net.httpserver` isn't available. **MockWebServer isn't available offline either**: it's only an `androidTestImplementation` dependency, and its okhttp version isn't in the local Gradle cache. For an HTTP peer, use a bare `java.net.ServerSocket` the way `AllowSlowRequestsTest` does. A socket that never calls `accept()` simulates a server that never answers.
- supabase-kt runs fine in JVM unit tests.
- On main there are no unit tests for writing the in-app export file or for the telephony mapping. `FccExportExtensionsTest` covers only the export's `test_id`. The FCC payload is covered by the database test `fcc_payload_test.sql`.

## Database tests

[`supabase/db_tests/run.sh`](../../supabase/db_tests/run.sh) runs every `db_tests/*_test.sql`:

1. Starts a throwaway `public.ecr.aws/supabase/postgres:17.6.1.084` container (override with `POSTGRES_IMAGE`) with **`--network none`**. It can't reach prod or the FCC, and the local Supabase stack is never touched.
2. Waits until Postgres stays up (the image restarts once after init).
3. Copies `supabase/migrations/` and `supabase/db_tests/` into the container.
4. Creates one database per test file and runs it with `psql -v ON_ERROR_STOP=1`. Test output is `NOTICE` lines (`ok - …` or `ok: …`); any failure aborts the script.
5. Removes the container on exit.

Each test builds **minimal stand-in tables** (columns copied from the live schema), stubs what it doesn't test, then `\i`s the migration under test. It does not load `full_schema.sql`. The FCC pipeline tests share [lib/fcc_schema.sql](../../supabase/db_tests/lib/fcc_schema.sql): copies of the live tables, triggers, view and functions involved in posting, plus `202610070001`. `run.sh` runs only `*_test.sql` files, so it never runs that file on its own.

| Test | Migration under test | Covers |
|---|---|---|
| [live_baseline_test.sql](../../supabase/db_tests/live_baseline_test.sql) (added in #134) | [202609300001_track_live_fcc_queue_and_role_settings.sql](../../supabase/migrations/202609300001_track_live_fcc_queue_and_role_settings.sql) | Creates the live-only queue table, queue-only trigger and role settings on a fresh database, and changes nothing when run again where they exist. |
| [fcc_retry_queue_test.sql](../../supabase/db_tests/fcc_retry_queue_test.sql) | [202610070001_unclog_fcc_retry_queue.sql](../../supabase/migrations/202610070001_unclog_fcc_retry_queue.sql) | Waiting entries are re-checked in 5 min, or 1 h once older than a day, and don't count as attempts. A run takes at most 10 entries. An entry without a carrier name is blocked, and one already sent some other way is removed. A 400 blocks the entry and is logged. A 503 is retried after 60 s. A run stops taking entries after 60 s. The FCC POST and readiness check are stubbed. |
| [dashboard_rpcs_test.sql](../../supabase/db_tests/dashboard_rpcs_test.sql) (added in #136) | [202610220101_fix_dashboard_rpcs.sql](../../supabase/migrations/202610220101_fix_dashboard_rpcs.sql) | `rpc_group_detail` returns only the requested group. All three dashboard RPCs refuse requests without the dashboard secret and work with it. They report latency, and apply the `lat_max` filter, in milliseconds. A speed computed from bytes and duration (microseconds) comes out right. A caller's temporary types can't make them run the caller's code as their owner. |
| [database_helpers_test.sql](../../supabase/db_tests/database_helpers_test.sql) (added in #136) | [202610220102_tighten_database_helpers.sql](../../supabase/migrations/202610220102_tighten_database_helpers.sql) | `log_user_data` is dropped, `get_raw_header(item)` returns just that header, the SECURITY DEFINER device and dashboard checks have a fixed `search_path`, and `anon`/`authenticated` lose TRUNCATE but keep INSERT on the app tables. |
| [upload_rls_test.sql](../../supabase/db_tests/upload_rls_test.sql) (added in #132) | [202610210001_fast_upload_rls.sql](../../supabase/migrations/202610210001_fast_upload_rls.sql) | Loads the live policies, records what 8 kinds of caller can read and write on 5 tables, applies the migration and checks every caller is allowed and refused exactly as before. Devices stay isolated, and an upload and the duplicate-check select each take under 1 s at 400 measurements. |
| [fcc_payload_test.sql](../../supabase/db_tests/fcc_payload_test.sql) (added in #133) | [202610210101_build_fcc_payload_per_submission.sql](../../supabase/migrations/202610210101_build_fcc_payload_per_submission.sql) | `fcc_submission_payload` gives byte for byte the old view's payload on each fixture, with locations and cells in the same (insertion) order, at least 10 times faster. `post_fcc_submission` posts it and refuses an incomplete group. The HTTP request is stubbed. |
| [fcc_queue_test.sql](../../supabase/db_tests/fcc_queue_test.sql) (added in #133) | `202610210101`, [202610210103_fcc_retry_queue_processing.sql](../../supabase/migrations/202610210103_fcc_retry_queue_processing.sql), [202610210104_fcc_queue_health.sql](../../supabase/migrations/202610210104_fcc_queue_health.sql) | Each post commits on its own: a timeout on the 3rd post keeps the first two recorded, also when pg_cron runs the `CALL`. A `duplicate_test_id` answer marks the entry sent. A blank provider is held as `Missing provider`. Held reasons and their daily re-check, 30-day expiry and revival, the health views and the digest. The real posting code runs; the HTTP request is stubbed. It prints two `ERROR` lines that it expects. |
| [device_registration_test.sql](../../supabase/db_tests/device_registration_test.sql) (added in #148) | [202610220001_retry_safe_device_registration.sql](../../supabase/migrations/202610220001_retry_safe_device_registration.sql) | Reproduces the lockout with the live `register_device`, then checks that a phone that lost its response registers again, a device that never uploaded can re-register, and one that has uploaded keeps its secret. |
| [migration_deploy_test.sql](../../supabase/db_tests/migration_deploy_test.sql) | [202610210001](../../supabase/migrations/202610210001_fast_upload_rls.sql), [202610210101](../../supabase/migrations/202610210101_build_fcc_payload_per_submission.sql), [202610210103](../../supabase/migrations/202610210103_fcc_retry_queue_processing.sql) | Each migration is applied while another session (started in the background with psql's `\!`) holds a table it needs, as a cron run or dashboard query would. An upload that arrives meanwhile must not wait for the migration, and the migration must apply once the table is free. Leftover invalid indexes from a failed `CREATE INDEX CONCURRENTLY` are rebuilt. |
| [fcc_digest_email_test.sql](../../supabase/db_tests/fcc_digest_email_test.sql) (added in #154) | [202610310001_email_fcc_queue_digest.sql](../../supabase/migrations/202610310001_email_fcc_queue_digest.sql) | The digest is emailed only when something is new since the last run, or after 7 days without one; blocked entries that haven't changed are one line; a run that Resend refuses fails without being logged, so the next run covers its period. The request to Resend is stubbed. |
| [fcc_digest_ignore_test.sql](../../supabase/db_tests/fcc_digest_ignore_test.sql) (added in #156) | [202610310002_ignore_old_blocked_fcc_entries.sql](../../supabase/migrations/202610310002_ignore_old_blocked_fcc_entries.sql) | Which entries go into `private.fcc_queue_ignored`, and that `fcc_queue_attention`, the digest and the check for something new skip every entry listed there. The request to Resend is stubbed. |
| `local_ports_test.sql` (uncommitted on the `client-port-header` branch) | `202610130001_record_local_ports.sql` (same branch) | Header parsing into `upload_local_port` / `upload_transport` / `upload_ip`, rejection of malformed values, and the `source_port_comparison` view. |

Since #133, `fcc_retry_queue_test.sql` loads `lib/fcc_schema.sql` plus `202610210101` and `0103`, and runs its scenarios through `process_fcc_submission_retries`, which `0103` keeps for manual runs. The blank-provider check now happens once the measurements are complete.

Prerequisite: a working Docker daemon. If Docker misbehaves, restart Docker Desktop, or use the PG14 fallback in [Local setup](local-setup.md#when-docker-is-unavailable) for ad-hoc SQL. `run.sh` itself needs Docker.

`supabase test db` (pgTAP under `supabase/tests/`) is deliberately **not** used.

## Instrumented tests

Location: [app/src/androidTest](../../app/src/androidTest/java/edu/gatech/cc/cellwatch/). Room DAOs (`MeasurementDaoTest`, `FccSubmissionDaoTest`), serialization (`SerializationTest`, `NetworkMeasurementWithDataTest`, `CellTest`), and MSAK-backed `LatencyTestTest` / `ThroughputTestTest` (using MockWebServer and mockk).

The androidTest source set compiles again on main since #148 (merged 2026-10-27). Before that, `LatencyTestTest` and `ThroughputTestTest` imported `edu.gatech.cc.cellwatch.domain.msak.*`, but MSAK had moved to the `msak-android` library. #148 fixes the imports and stale assertions, removes the obsolete `ThroughputMetricsTest`, updates the `SerializationTest` fixture to the current model, and adds `grantMeasurementPermissions()` (fine and coarse location, phone state), because the MSAK-backed tests throw without them.

### AGP 8.1.x vs API 36 emulators

On AGP 8.1.x, `./gradlew :app:connectedDebugAndroidTest` **aborts with 0 tests** on API 36 images (`test_status: ABORTED`; the cause shows only with `--info`). Main used AGP 8.1.2 until #148 moved it to 8.9.3 (merged 2026-10-27), so this still affects branches based on an older main.

- **Cause:** UTP's ddmlib device provider parses adb `track-app` messages. API 36's adbd adds new process fields that this ddmlib/protobuf combination can't parse. The fields come from the device, so changing the host adb doesn't help.
- **Fix:** AGP ≥ 8.2 (the provider no longer tracks apps), which main now has.
- **Workaround on 8.1.x:** use the legacy (non-UTP) runner:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.experimental.androidTest.useUnifiedTestPlatform=false
```

The legacy runner writes a single JUnit XML for the whole run, named after the first test class.

## Cautions

- **Crashlytics:** [Log.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/core/util/Log.kt) sends every `Log.w`/`Log.e` to Crashlytics as a non-fatal, in debug builds too, to the **real project**. Instrumented runs and manual debug runs therefore add noise to production crash data.
- **Data loss:** connected tests uninstall the app afterwards, which wipes its data on that emulator or device, including any CellWatch install with real contact details.
- **Network:** a debug build talks to whatever Supabase and MSAK servers `cellwatch.properties` names.

So run instrumented tests:
1. on a **disposable AVD** (or boot with `-read-only -no-snapshot`), never a personal phone;
2. with the emulator **offline** (`adb shell cmd connectivity airplane-mode enable`) before installing;
3. with `ANDROID_SERIAL` set, so a phone plugged in mid-run can't be targeted;
4. after the post-boot load settles. A freshly booted `-no-snapshot` AVD can ANR at app start and report "No tests found".

Other sessions may share the same AVD. Check `adb devices` before booting or killing an emulator.
