# Data flow: one measurement end to end

How one test group travels from the phone to the FCC and to the public `published` schema.

**Related:** [overview](overview.md) · [identity-and-security](identity-and-security.md) · [app/measurement-engine](../app/measurement-engine.md) · [app/sync-and-upload](../app/sync-and-upload.md) · [supabase/triggers-and-cron](../supabase/triggers-and-cron.md) · [supabase/fcc-submission-pipeline](../supabase/fcc-submission-pipeline.md)

A **group** is one run of the three tests (latency, download, upload). Each test is one `measurements` row. All three share a `group_id`, and in FCC challenge mode the FCC submission's `id` is that same group ID. Items marked **pending** are uncommitted changes on branch `client-port-header`.

## Sequence

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant VM as MeasureViewModel
    participant S as MeasurementService<br/>+ MeasurementManager
    participant M as M-Lab (Locate + MSAK)
    participant R as Room DB
    participant W as UploadWorker<br/>MeasurementUploader
    participant T as TCP tuple service
    participant API as Supabase PostgREST (anon)
    participant DB as Postgres
    participant C as pg_cron job
    participant F as FCC BDC API

    U->>VM: Tap Measure
    VM->>S: start foreground service (mode, inVehicle)
    S->>M: locate servers, ping-rank throughput servers
    loop latency, then download, then upload
        S->>M: run test (MeasurementTest: location fix before and after, cell watch)
        S->>R: insertMeasurement (+ data, locations, cells)
    end
    opt FCC_CHALLENGE mode and no test on Wi-Fi and cellular data on
        S->>R: insertFccSubmission (id = groupId, contact info from settings)
    end
    S-->>VM: group done
    VM->>W: UploadWorker.enqueue (unique work, APPEND_OR_REPLACE, needs network)

    Note over W,API: first use: register_device RPC if no stored secret
    loop each unsynced measurement (max 3 in flight)
        W->>API: rpc insert_measurement(measurement, data, latency, locations[], cells[])
        API->>DB: one transaction under RLS, BEFORE INSERT triggers
        DB-->>W: row, or duplicate key (treated as already uploaded)
        W->>R: set uploadTime
    end
    opt submissions whose 3 measurements are uploaded
        W->>T: GET (once per run), pending: record local port
        T-->>W: remoteAddress, remotePort, timestamp
        W->>API: insert fcc_submissions (source_ip/port, server_timestamp, pending: tuple_local_port, X-Local-Port, X-Transport)
        API->>DB: BEFORE triggers _200, _300, pending _310, then AFTER trg_auto_post_fcc_submission
        DB->>DB: upsert private.fcc_submission_retry_queue (env prod, pending, now)
        W->>R: set uploadTime
    end

    loop every 2 minutes
        C->>DB: private.process_fcc_submission_retries(batch)
        DB->>DB: readiness check, build payload from private.fcc_submissions_view
        DB->>F: POST JSON with hash_value header (http extension)
        F-->>DB: status and body
        DB->>DB: mark submitted or schedule retry, log in fcc_submission_failures
    end

    Note over DB: publish_data(...) copies rows into published.* (caller not in this repo)
```

## Steps in detail

### 1. Test run (phone)

- [MeasureViewModel.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/ui/main/MeasureViewModel.kt) starts [MeasurementService.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/domain/fcc/MeasurementService.kt), a foreground service of type location that holds a 10-minute partial wake lock. The service creates a random group UUID.
- [MeasurementManager.runTestSequence](../../app/src/main/java/edu/gatech/cc/cellwatch/domain/fcc/MeasurementManager.kt):
  1. Asks the M-Lab Locate API for throughput servers through msak `LocateManager`. It pings each candidate with [ping.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/domain/fcc/ping.kt) (AndroidPing.aar) and picks a latency server on the same machine when one exists. If Locate is unreachable, it falls back to `UnreachableServer`, so the tests still run and record their failure.
  2. Runs the latency test, then download, then upload (3 streams each). Each result is written to Room straight away (`MeasurementRepository.insertMeasurement`).
  3. Each test ([MeasurementTest.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/domain/fcc/MeasurementTest.kt)) takes a fused location fix before and after the test, giving 0–2 locations. It also records serving and neighbouring cells, MCC/MNC, carrier aggregation, and roaming and connection flags.
  4. If the collection mode is `FCC_CHALLENGE`, no test ran on Wi-Fi, and cellular data was enabled, the manager builds an `FccSubmission` with `id = groupId` and stores it in Room. The submission includes contact name, email, and phone from settings, the device model and OS, the provider, MCC/MNC, and `inVehicle`. `deviceImei` and `deviceTac` exist on the model but are never set.

### 2. Upload (phone)

- Triggers: `MeasureViewModel.handleMeasurementComplete` and `MapActivity.onCreate` both call [UploadWorker.enqueue](../../app/src/main/java/edu/gatech/cc/cellwatch/data/core/sync/UploadWorker.kt). The work is unique (`upload-pending`, `APPEND_OR_REPLACE`) and requires a connected network. It retries while `failed > 0`, up to 5 attempts.
- [MeasurementUploader.uploadPending](../../app/src/main/java/edu/gatech/cc/cellwatch/data/core/sync/MeasurementUploader.kt) runs under a mutex:
  1. **Measurements first.** Each unsynced measurement goes to `rpc("insert_measurement", …)`, with at most 3 requests in flight. A `DuplicateKeyError` counts as success, because `insert_measurement` is a single transaction: if the ID is on the server, everything for it is. Each success sets `uploadTime` in Room.
  2. **Submissions second.** A submission is sent only when all three of its group's measurements have an `uploadTime`; the rest are counted as `waiting`. One TCP tuple is fetched per run and copied onto every ready submission: `sourceIp`, `sourcePort`, `serverTimestamp`, and (**pending**) `tupleLocalPort`. If the tuple request fails, no submission uploads in that run.
  3. Each submission is inserted into `fcc_submissions` through PostgREST. Duplicate key counts as success.
- Every request carries `apikey`, `X-Device-ID`, and `X-Device-Secret` ([NetworkMeasurementDatasource.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/data/network/NetworkMeasurementDatasource.kt)). **Pending:** `LocalPortHeaders.kt` adds `X-Local-Port` and `X-Transport`.

### 3. Server writes and triggers

`public.insert_measurement` is `SECURITY INVOKER`, so the `anon` INSERT policies apply to every row it writes. It inserts the measurement, then its `upload_download_data` or `latency_data`, `locations[]`, and `cells[]`, each linked to the new measurement ID.

| Trigger | Table | When | Effect |
|---|---|---|---|
| `_200_*_force_created_on` → `force_created_on()` | measurements, upload_download_data, latency_data, locations, cells, fcc_submissions | BEFORE INSERT, only if `created_on IS NULL` | Sets `created_on` and `updated_on` to server `now()`. |
| `fix_jitter_pre_100alpha5` → `fix_jitter()` | latency_data | BEFORE INSERT | Replaces `jitter` with `sqrt(jitter)` when the parent measurement's `app_version` is null or `1.0.0-alpha.2`–`.4`. Those versions reported variance (see commit 5cdeba6). |
| `_100_*_set_updated_on` | most tables | BEFORE UPDATE | Bumps `updated_on`. |
| `_300_fcc_submission_update_source_ip` | fcc_submissions | BEFORE INSERT, only if `source_ip IS NULL` | Fallback that takes the first `x-forwarded-for` entry. A client can spoof that value; see [identity-and-security](identity-and-security.md#header-trust). |
| `_310_fcc_submission_record_local_ports` (**pending**, `202610130001`) | fcc_submissions | BEFORE INSERT | Stores `upload_local_port`, `upload_transport`, and `upload_ip` (from `cf-connecting-ip`), and range-checks `tuple_local_port`. Malformed values become null. |
| `trg_auto_post_fcc_submission` → `private.auto_post_fcc_submission()` | fcc_submissions | AFTER INSERT | If `submitted` is not true, upserts the row into `private.fcc_submission_retry_queue` (`fcc_env='prod'`, `state='pending'`, `next_attempt_at=now()`). The function is `SECURITY DEFINER`. |

The queue entry is created as soon as the submission row arrives. Normally the measurements are already there, because the uploader waits for them. Older app versions sent submissions first, and the retry job waits for those groups to fill in.

### 4. FCC post and retry queue

Cron job `fcc-submission-retries` runs every 2 minutes ([cron_jobs.sql](../../supabase/cron_jobs.sql)) and calls `private.process_fcc_submission_retries(n)`. The snapshot has `n = 1`; migration [202610070001](../../supabase/migrations/202610070001_unclog_fcc_retry_queue.sql) changes it to 10. The migration's function is live on prod (hash match, 2026-10-22); the job's batch size there was not checked.

1. Deletes queue rows whose submission is already `submitted`.
2. Locks up to `n` (capped at 10) due `pending` rows with `FOR UPDATE SKIP LOCKED`. With 202610070001, it stops taking new rows once the run has lasted 60 s.
3. Checks each row:
   - **Missing provider:** the row is set to `blocked` with error "Missing provider". This needs a manual repair.
   - **Not ready** (`private.fcc_submission_is_ready` needs exactly one latency row with `latency_data` and one download and one upload row with `upload_download_data`): the row is re-checked in 5 minutes. With 202610070001, rows that have waited more than a day are re-checked hourly. A not-ready row does not count as an attempt.
4. `private.post_fcc_submission(id, env)`:
   - Reads the URL and Vault secret for `env` from `private.fcc_submission_info`.
   - Builds the payload from `private.fcc_submissions_view` (`submission_category: "Consumer Challenge"`, `contact`, one `submissions[0]` with `tests.latency`, `tests.download`, `tests.upload`).
   - POSTs it with `http()` and a `hash_value` header.
   - On 2xx, sets `submitted=true`, `submission` (the payload), `submitted_on`, and `submission_response`.
5. Every attempt, successful or not, adds a row to `private.fcc_submission_failures` (`succeeded` flag, error, 500-character response snippet).
6. On failure, the next attempt waits `60 × 2^(attempts−1)` s, capped at 1 h. The row becomes `blocked` after 6 attempts, or immediately on a 4xx other than 408 or 429.

Full details: [supabase/fcc-submission-pipeline](../supabase/fcc-submission-pipeline.md).

### 5. Publishing (`published.*`)

`public.publish_data(publishable_measurement[], publishable_cell[])` copies selected rows into `published.measurements`, `published.cells`, `published.latency_data`, and `published.throughput_data`. Rows that already exist are skipped (`ON CONFLICT DO NOTHING`).

- The caller supplies the derived fields: `group_offset`, `cell_tech`, H3 res-9 `center/start/end_hex9`, and PostGIS points.
- For rows where the stored rate is null, the function computes `bytes_per_sec` from bytes over duration (µs).
- `published.*` is readable by everyone (`GRANT SELECT … TO PUBLIC`, RLS `USING (true)`). Helpers `published.measurements_in_hex(...)`, `published.measurement_groups`, and `published.providers` query it.
- `publish_data` is not `SECURITY DEFINER`, and `INSERT` on `published.*` is granted only to `service_role`. So although `EXECUTE` is granted to `anon`, only a service-role caller can actually publish.

> TODO: verify what calls `publish_data` and computes the hex and location fields. Nothing in this repo or in `cron_jobs.sql` does.

## Where a group can get stuck

| Symptom | Where | Cause in code |
|---|---|---|
| Group never leaves the phone | `MeasurementUploader` | Network or registration failure; after 5 failed attempts the work ends until the next enqueue (new test or opening the map). |
| Submission held on phone (`waiting`) | `measurementsUploaded()` | One of the 3 measurements has not uploaded. |
| No submission uploads at all | `getTcpTuple()` | The tuple service is unreachable or returned a non-200 response. |
| Queue row `pending` forever | `fcc_submission_is_ready` | Server never received all 3 measurements (e.g., from older app versions). |
| Queue row `blocked` | `process_fcc_submission_retries` | Missing provider, FCC 4xx validation error, or 6 failed attempts. |
