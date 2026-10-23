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

What's covered (18 tests in this checkout):

| Test class | Covers |
|---|---|
| [MeasurementUploaderTest](../../app/src/test/java/edu/gatech/cc/cellwatch/data/core/sync/MeasurementUploaderTest.kt) (11) | Upload ordering and idempotency against fakes: duplicate-key responses mark items uploaded, at most 3 requests in flight, overlapping runs don't double-send, a network error leaves only that item pending, a submission waits for all three measurements, tuple failure leaves submissions pending, and cancellation isn't logged as an error. *Pending:* also asserts `tupleLocalPort` is copied from the tuple. |
| `LocalPortHeadersTest` (2) | *Pending (untracked):* the `X-Local-Port` header equals the client port a local server saw; no `X-Transport` header when the network is unknown. |
| [RedactSecretsTest](../../app/src/test/java/edu/gatech/cc/cellwatch/core/util/RedactSecretsTest.kt) (5) | `redactSecrets` strips device secrets, JWTs and auth headers from exception messages and cause chains before they reach Crashlytics. |

Constraints:
- Unit tests compile against `android.jar`, so `com.sun.net.httpserver` isn't available. **MockWebServer isn't available offline either**: it's only an `androidTestImplementation` dependency, and its okhttp version isn't in the local Gradle cache. For an HTTP peer, use a bare `java.net.ServerSocket` the way `LocalPortHeadersTest` does. A socket that never calls `accept()` simulates a server that never answers.
- supabase-kt runs fine in JVM unit tests.
- There are no tests for the payload view, the in-app export or the telephony mapping.

## Database tests

[`supabase/db_tests/run.sh`](../../supabase/db_tests/run.sh) runs every `db_tests/*_test.sql`:

1. Starts a throwaway `public.ecr.aws/supabase/postgres:17.6.1.084` container (override with `POSTGRES_IMAGE`) with **`--network none`**. It can't reach prod or the FCC, and the local Supabase stack is never touched.
2. Waits until Postgres stays up (the image restarts once after init).
3. Copies `supabase/migrations/` and `supabase/db_tests/` into the container.
4. Creates one database per test file and runs it with `psql -v ON_ERROR_STOP=1`. Test output is `NOTICE` lines (`ok - …`); any failure aborts the script.
5. Removes the container on exit.

Each test builds **minimal stand-in tables** (columns copied from the live schema), stubs what it doesn't test, then `\i`s the migration under test. It does not load `full_schema.sql`.

| Test | Migration under test | Covers |
|---|---|---|
| [fcc_retry_queue_test.sql](../../supabase/db_tests/fcc_retry_queue_test.sql) | [202610070001_unclog_fcc_retry_queue.sql](../../supabase/migrations/202610070001_unclog_fcc_retry_queue.sql) | Waiting entries are re-checked in 5 min, or 1 h once older than a day, and don't count as attempts. A run takes at most 10 entries. A 400 blocks the entry and is logged. A 503 is retried after 60 s. A run stops taking entries after 60 s. The FCC POST and readiness check are stubbed. |
| `local_ports_test.sql` | `202610130001_record_local_ports.sql` (*pending*) | Header parsing into `upload_local_port` / `upload_transport` / `upload_ip`, rejection of malformed values, and the `source_port_comparison` view. |

Prerequisite: a working Docker daemon. If Docker misbehaves, restart Docker Desktop, or use the PG14 fallback in [Local setup](local-setup.md#when-docker-is-unavailable) for ad-hoc SQL. `run.sh` itself needs Docker.

`supabase test db` (pgTAP under `supabase/tests/`) is deliberately **not** used.

## Instrumented tests

Location: [app/src/androidTest](../../app/src/androidTest/java/edu/gatech/cc/cellwatch/). Room DAOs (`MeasurementDaoTest`, `FccSubmissionDaoTest`), serialization (`SerializationTest`, `NetworkMeasurementWithDataTest`, `CellTest`), and MSAK-backed `LatencyTestTest` / `ThroughputTestTest` (using MockWebServer and mockk).

**This checkout's androidTest source set does not compile.** `LatencyTestTest` and `ThroughputTestTest` still import `edu.gatech.cc.cellwatch.domain.msak.*`, but MSAK moved to the `msak-android` library. Commit `b6d440c` fixes the imports and stale assertions and removes the obsolete `ThroughputMetricsTest`; it is not merged here.

### AGP 8.1.x vs API 36 emulators

On AGP 8.1.x (this repo uses 8.1.2), `./gradlew :app:connectedDebugAndroidTest` **aborts with 0 tests** on API 36 images (`test_status: ABORTED`; the cause shows only with `--info`).

- **Cause:** UTP's ddmlib device provider parses adb `track-app` messages. API 36's adbd adds new process fields that this ddmlib/protobuf combination can't parse. The fields come from the device, so changing the host adb doesn't help.
- **Fix:** AGP ≥ 8.2 (the provider no longer tracks apps). The target-API-36 work (#135) moves to AGP 8.7.3.
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
