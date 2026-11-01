# Android app: sync and upload

How locally stored measurements and FCC submissions get to Supabase: what triggers an upload, how retries work, which RPCs and tables are called, and how rows are marked as uploaded.

Related: [layout](layout.md) · [local-storage](local-storage.md) · [measurement-engine](measurement-engine.md) · [data flow](../architecture/data-flow.md) · [identity and security](../architecture/identity-and-security.md) · [RPC reference](../supabase/rpc-reference.md) · [FCC submission pipeline](../supabase/fcc-submission-pipeline.md) · [performance notes](../supabase/performance-notes.md)

Code: [`data/core/sync/`](../../app/src/main/java/edu/gatech/cc/cellwatch/data/core/sync/), [`MeasurementRepository.kt`](../../app/src/main/java/edu/gatech/cc/cellwatch/data/core/repositories/MeasurementRepository.kt), [`data/network/`](../../app/src/main/java/edu/gatech/cc/cellwatch/data/network/).

Status: the `UploadWorker` / `MeasurementUploader` path described here was merged to main in #111 (2026-10-07). #148 (merged 2026-10-27) added the hourly and foreground triggers, 60 s request timeouts and retry-safe device registration. `versionCode` is still 13: #111 is not known to be in a release (check Play Console), and #148 is not released. Phones upload with whatever code their build shipped.

## Overview

```mermaid
flowchart LR
    A[App comes to the foreground<br/>CellWatchApp, KEEP] --> E
    B[MeasureViewModel<br/>test finished] --> E
    E[UploadWorker.enqueue<br/>unique 'upload-pending'<br/>APPEND_OR_REPLACE by default<br/>needs CONNECTED] --> W[UploadWorker.doWork]
    P[UploadWorker.schedulePeriodic<br/>unique 'upload-pending-periodic'<br/>every hour, KEEP<br/>needs CONNECTED] --> W
    W --> R[MeasurementRepository.uploadPending]
    R --> U[MeasurementUploader]
    U -->|1. each unsynced measurement| M[RPC insert_measurement]
    U -->|2. once per run, if a submission is ready| T[GET TCP_TUPLE_URL<br/>tuple service]
    U -->|3. each ready submission| F[POST fcc_submissions]
    M --> DB[(Room: set uploadTime)]
    F --> DB
```

## When uploads trigger

| Trigger | Code |
|---|---|
| A test sequence finishes successfully | `UploadWorker.enqueue(...)` in [MeasureViewModel.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/ui/main/MeasureViewModel.kt) `handleMeasurementComplete` |
| The app comes to the foreground (any activity starts after none were started) | A `ProcessLifecycleOwner` observer in [CellWatchApp.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/CellWatchApp.kt) calls `UploadWorker.enqueue(this, ExistingWorkPolicy.KEEP)` in `onStart`, so an upload that's already queued or running is enough |
| Every hour while the phone has a connection | `UploadWorker.schedulePeriodic(this)` in `CellWatchApp.onCreate`: unique periodic work `upload-pending-periodic`, `ExistingPeriodicWorkPolicy.KEEP`, so a launch doesn't push the next run back |

The foreground and hourly triggers were merged to `main` in #148 (2026-10-27) and are not released. There is no connectivity-change trigger. Rows recorded while offline upload at the next hourly run once the phone has a connection, or sooner if the app comes to the foreground or another test completes (the enqueued work itself waits for a network).

The released build (versionCode 13) has no periodic or foreground trigger: it uploads only after a test, or when `MapActivity` is created (usually at a cold start). #148 removed that `MapActivity` trigger, since the foreground trigger covers it.

Phones that already have the periodic work keep its old settings, because of `KEEP`. Changing the periodic request later needs `ExistingPeriodicWorkPolicy.UPDATE`.

## `UploadWorker` (WorkManager)

[UploadWorker.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/data/core/sync/UploadWorker.kt)

| Setting | Value |
|---|---|
| Request type | `CoroutineWorker`; `OneTimeWorkRequest` from `enqueue`, `PeriodicWorkRequest` (1 h) from `schedulePeriodic` |
| Unique name | `upload-pending` (one-time), `upload-pending-periodic` (hourly) |
| Existing-work policy | One-time: `APPEND_OR_REPLACE` by default, so a new request queues behind a running one instead of cancelling it (replaces the chain only if the previous one failed or was cancelled); the foreground trigger passes `KEEP`. Hourly: `ExistingPeriodicWorkPolicy.KEEP` |
| Constraints | `NetworkType.CONNECTED` |
| Backoff | not set, so WorkManager's default applies (exponential, 30 s initial) |
| Retry rule | `Result.retry()` if `summary.failed > 0 && runAttemptCount < 5`, else `Result.success()` (so at most 6 runs per request) |

Submissions that are only *waiting* for their measurements do not cause a retry. The worker always ends in `success` once attempts are exhausted; the rows simply stay unsynced until the next trigger.

## `MeasurementUploader` (the algorithm)

[MeasurementUploader.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/data/core/sync/MeasurementUploader.kt) is plain Kotlin with `Local` / `Remote` / `Log` interfaces (implemented inside `MeasurementRepository`), which makes it unit-testable ([MeasurementUploaderTest.kt](../../app/src/test/java/edu/gatech/cc/cellwatch/data/core/sync/MeasurementUploaderTest.kt)).

`uploadPending()` holds a `Mutex`, so only one upload runs per process. It returns `Summary(uploaded, failed, waiting)`.

1. **Measurements.** `local.unsyncedMeasurements()` (all rows with `uploadTime IS NULL`, with children). Each is sent with `remote.insertMeasurement`; at most `maxParallelUploads = 3` requests in flight (`Semaphore`).
2. **Submissions.** `local.unsyncedSubmissions()` are split into:
   - *ready*: the group has a `latency`, `download` and `upload` measurement that all have `uploadTime` set;
   - *waiting*: everything else (counted in `Summary.waiting`, logged).
3. If any are ready, **one** tuple request (`remote.getTcpTuple()`) is made for the whole batch. On failure every ready submission counts as failed for this run.
4. Each ready submission gets `sourceIp = tuple.remoteAddress`, `sourcePort = tuple.remotePort`, `serverTimestamp = tuple.timestamp` (epoch ms) (uncommitted work on the `client-port-header` branch also sets `tupleLocalPort = tuple.localPort`); then `remote.insertFccSubmission` (again max 3 in parallel).

Consequences worth knowing:

- `sourceIp` / `sourcePort` / `serverTimestamp` describe the phone's connection **at upload time**, not at test time, and all submissions in one run share the same tuple.
- A submission never reaches the server before all three of its measurements, so server-side processing of a submission can rely on them existing.

### Error handling

| Exception | Where | Effect |
|---|---|---|
| `CancellationException` | any | rethrown (worker stopped) |
| `DuplicateKeyError` | insert measurement / submission | treated as success: the id is already on the server (an earlier attempt succeeded but the response was lost); the local copy is marked uploaded |
| `IOException` (incl. `NetworkError`) | any request | logged at INFO, item counted as failed -> worker retries |
| any other `Exception` | any request | logged at ERROR (Crashlytics non-fatal), counted as failed -> worker retries |

## Marking rows uploaded

`MeasurementRepository` implements `Local`:

- `markMeasurementUploaded(measurement, now)`: `measurement.asEntity()` with `uploadTime = now`, then `MeasurementDao.updateMeasurement` (`@Update` of the whole `MeasurementEntity` row; child tables are untouched).
- `markSubmissionUploaded(submission, now)`: same pattern with `FccSubmissionDao.updateFccSubmission`.

The object passed in is the **server's returned copy** (decoded response) on success, or the local copy after a `DuplicateKeyError`. So after a normal upload, the local parent row reflects what the server returned (for example server-filled `createdOn`), and any field the server returns as null overwrites the local value.

`uploadTime` is the only sync flag. `MeasurementRepository.uploadTimeFlow(group)` re-evaluates on every change to either table and emits `null` until every record in the group is uploaded; the measure screen and history items show "Pending" until then. Since #148 (merged 2026-10-27, #127) that means each of the group's measurements and its submission, if it has one ([MeasurementGroup.uploadTime](../../app/src/main/java/edu/gatech/cc/cellwatch/data/model/MeasurementGroup.kt)). The released build checks only the group's first measurement and its submission, so it can show a test as uploaded while its download or upload is still pending.

## Network layer (`NetworkMeasurementDatasource`)

[NetworkMeasurementDatasource.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/data/network/NetworkMeasurementDatasource.kt)

### Client and identity

- One `SupabaseClient` (supabase-kt `postgrest-kt` 1.0.3), created on first use under a `Mutex` so concurrent uploads don't double-register.
- URL and key: `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_API_KEY` (local Supabase in debug, cloud in release; see [build-and-config.md](build-and-config.md)).
- Device identity: `device_id` from DataStore. The secret comes from `registeredDeviceSecret` in [DeviceRegistration.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/data/network/DeviceRegistration.kt):
  1. If DataStore has no `device_secret`, the app makes one (32 random bytes as 64 hex digits) and saves it encrypted **before** registering.
  2. Unless DataStore's `device_registered` is true, it calls RPC **`register_device`** `{ "device_id": <id>, "device_secret": <secret> }` on a separate throwaway client, then sets `device_registered`.
  3. A lost response is retried with the same secret, which the server accepts. A phone registered by an older version already has a secret and confirms it once.
- A Ktor client plugin adds `X-Device-ID` and `X-Device-Secret` to every request of the main client. The server-side checks are described in [identity-and-security.md](../architecture/identity-and-security.md).
- The device id and secret are read once when the client is created and cached for the process lifetime.

> This registration was merged to `main` in #148 (2026-10-27) and is not released. The released build calls `register_device(device_id)`, which makes the secret on the server and returns it once. Until 2026-10-31 a second call for the same id raised `device id … already registered`, so a phone that lost the first response could never register or upload. Migration `202610220001` (#148), pushed to prod on 2026-10-31, adds the two-argument overload and lets a device that never uploaded register again through either version, which also rescues phones on the released build. PostgREST picks an overload by argument names, so that push had to come before any app release built from main; it has.

### Calls

| App method | Supabase call | Body / result |
|---|---|---|
| `registerDevice(id, secret)` | `rpc("register_device", {device_id, device_secret})` via `requestDeviceRegistration` | returns nothing. Raises `device id … already registered` if the id has uploaded data under a different secret, and `device secret is too short` under 32 characters |
| `insertMeasurement(m)` | `rpc("insert_measurement", NetworkMeasurementWithData)` | body keys `in_measurement`, `in_measurement_data`, `in_latency_data`, `in_locations`, `in_cells`; decoded as `NetworkMeasurement` |
| `insertFccSubmission(s)` | `postgrest["fcc_submissions"].insert(NetworkFccSubmission)` | `decodeSingle<NetworkFccSubmission>()` |
| `getMeasurementById(id)` | `select` on `measurements` with embedded `upload_download_data, latency_data, locations, cells` | not called anywhere |

DTOs live in [`data/network/model/`](../../app/src/main/java/edu/gatech/cc/cellwatch/data/network/model/) and map camelCase fields to snake_case with `@SerialName` (for example `simMcc` -> `sim_mobile_country_code` on measurements, but `simCountryCode` -> `sim_country_code` on submissions; `deviceOsName` -> `device_os` on submissions).

### Error mapping (`withErrorHandling`)

| Caught | Rethrown as |
|---|---|
| `IOException`, supabase `HttpRequestException` (includes Ktor request timeouts) | `NetworkError` (an `IOException`) |
| `RestException` whose `error` starts with `duplicate key value violates unique constraint` | `DuplicateKeyError` |
| anything else | unchanged |

`NotFoundError` is only thrown by the unused `getMeasurementById`. [`NetworkRestResult`](../../app/src/main/java/edu/gatech/cc/cellwatch/data/network/NetworkRestResult.kt) is an unused leftover data class.

### Timeouts

| Request | Timeout |
|---|---|
| Supabase calls | 60 s. `allowSlowRequests()` in `NetworkMeasurementDatasource.kt` sets supabase-kt's `requestTimeout` and Ktor's `HttpTimeout` connect timeout to 60 s, on both the main and the registration client. The engine is Ktor CIO (`ktor-client-cio` 2.3.0), which otherwise gives up connecting after 5 s. The released build uses supabase-kt 1.0.3's default of 10 s. Uncommitted work on the `client-port-header` branch switches to the OkHttp engine, where OkHttp's own defaults (10 s connect/read/write) also apply. |
| Tuple request | 60 s: one shared `tcpTupleClient` built with `OkHttpClient.Builder.allowSlowRequests()` (connect, read, write and whole-call timeouts). The released build uses a default `OkHttpClient` (10 s connect/read/write). |
| Whole upload run | no explicit limit; WorkManager stops a worker after its 10-minute execution window. |

### Serialization rule (important when adding fields)

supabase-kt's JSON is configured with `ignoreUnknownKeys = true` and `encodeDefaults = false`:

- **Responses**: extra columns from the server are ignored, so the server can add columns without breaking old apps.
- **Requests**: a property equal to its default (`null` for nearly every DTO field) is **omitted** from the body. Fields without a default (`type`, `connection_type`, `cellular_data_enabled` on `NetworkMeasurement`) are always sent.
- Therefore a new request-body field is invisible to the server while it is null, but as soon as the app sends a non-null value the server must already know the column/argument. The same goes for a new RPC overload: PostgREST picks an overload by its argument names. **Push the server migration to Supabase prod before releasing an app version that sends a new field or calls a new overload.** Merging the app change to main is not the deadline; the Play Store release is. See [migrations-and-deploy.md](../supabase/migrations-and-deploy.md).

## Tuple request (`getPubicTCPTuple`)

In `MeasurementRepository`. A `GET` to `BuildConfig.TCP_TUPLE_URL` (an HTTP service outside Supabase, the "tuple service") with the app's `User-Agent`, on the shared `tcpTupleClient` (60 s limits; see [Timeouts](#timeouts)). A 200 response is parsed with Gson into [`TcpTuple`](../../app/src/main/java/edu/gatech/cc/cellwatch/data/model/TcpTuple.kt) `{remoteAddress, remotePort, timestamp}`: the public IP and port the service saw, and its clock. Non-200 or a parse error fails the batch (logged as an unexpected error); transport failures are `IOException`s.

## Not on main: local port and transport reporting (`client-port-header`)

Uncommitted work on the local `client-port-header` branch: not in a PR, not merged to main, not released, and its migration is not pushed to Supabase prod. None of these files or changes are on main. Goal: find out whether the phone's own source port can replace the tuple service's view (the service sees the port after carrier NAT).

| Change | File |
|---|---|
| New OkHttp network interceptor `LocalPortHeaders` adds `X-Local-Port` (this phone's socket port for the request) and `X-Transport` (`vpn`, `cellular`, `wifi`, `ethernet`, `other`; VPN checked first) to every Supabase request. | `LocalPortHeaders.kt` |
| Supabase client uses `httpEngine = OkHttp.create { addNetworkInterceptor(LocalPortHeaders { currentTransport() }) }`; dependency switched from `ktor-client-cio` to `ktor-client-okhttp` 2.3.0. | `NetworkMeasurementDatasource.kt`, [app/build.gradle](../../app/build.gradle) |
| Tuple request records its own local port via a network interceptor: `TcpTuple.localPort`. | `MeasurementRepository.kt`, `TcpTuple.kt` |
| `FccSubmission.tupleLocalPort` is set from it before upload and sent as `tuple_local_port` (not stored in Room). | `FccSubmission.kt`, `NetworkFccSubmission.kt`, `MeasurementUploader.kt` |
| Tests: `LocalPortHeadersTest`, updated `MeasurementUploaderTest`. | `app/src/test/...` |
| Server side: `202610130001_record_local_ports.sql` adds `tuple_local_port`, `upload_local_port`, `upload_transport`, `upload_ip` to `fcc_submissions` plus a before-insert trigger reading the headers. | see [triggers-and-cron.md](../supabase/triggers-and-cron.md) |

Per the serialization rule, `tuple_local_port` is sent whenever the tuple request saw a port, so **migration `202610130001` must be pushed to Supabase prod before an app release that includes this change**. The headers themselves are harmless to an older server.

## Other paths that touch Supabase data

- History export (`MeasureHistoryViewModel.exportData`) writes a local JSON file only; it does not call Supabase. See [ui-and-screens.md](ui-and-screens.md#history).
- The map reads only the local Room database; it does not call any RPC.
