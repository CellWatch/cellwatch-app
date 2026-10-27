# Architecture decisions (ADR log)

A short record of design decisions that can be seen in the code, commit messages, and migration headers. Newest decisions are at the bottom.

**Related:** [overview](overview.md) · [data-flow](data-flow.md) · [identity-and-security](identity-and-security.md) · [supabase/migrations-and-deploy](../supabase/migrations-and-deploy.md) · [fcc/bdc-spec-mapping](../fcc/bdc-spec-mapping.md)

Dates come from git (`git log --date=short`). Where a decision predates its first commit in this repo, the date reads "by" that commit. **Status** values:

| Status | Meaning |
|---|---|
| in prod | App parts are in the Play Store release; Supabase parts are pushed to the prod project |
| pushed | A Supabase change applied to the prod project, checked against prod's catalog |
| merged | On `main`; not known to be in prod. App code is not known to be in a release (versionCode is still 13; check Play Console) |
| pending | Uncommitted on the `client-port-header` branch; not merged, migration not pushed |
| decided, not merged | Agreed; built only as unpushed local commits |
| superseded | Replaced by a later decision |

Merged to `main` is not in prod: app changes need a Play Store release, and Supabase changes need their migration pushed to prod.

| ADR | Date | Title | Status |
|---|---|---|---|
| [001](#adr-001-device-id-and-secret-instead-of-user-accounts) | 2024-05-08 | Device ID and secret instead of user accounts | in prod |
| [002](#adr-002-tcp-tuple-service-for-the-fcc-server-fields) | 2023-12-18 | TCP tuple service for the FCC server fields | in prod (replacement under evaluation) |
| [003](#adr-003-fcc-submissions-only-for-cellular-tests-in-fcc-challenge-mode) | 2024-05-30 | FCC submissions only for cellular tests in FCC challenge mode | in prod |
| [004](#adr-004-m-lab-msak-for-measurements-in-a-separate-library) | 2024-05-29 / 2024-06-19 | M-Lab MSAK for measurements, in a separate library | in prod |
| [005](#adr-005-crashlytics-only-no-analytics) | 2024-06-26 | Crashlytics only, no Analytics | in prod |
| [006](#adr-006-post-to-the-fcc-from-postgres-not-from-the-phone) | by 2025-10-13 | Post to the FCC from Postgres, not from the phone | in prod |
| [007](#adr-007-retry-queue-processed-by-pg_cron) | by 2026-09-30 | Retry queue processed by pg_cron | in prod |
| [008](#adr-008-post-only-complete-groups) | 2026-07-14 | Post only complete groups | in prod (server side) |
| [009](#adr-009-live-schema-snapshot-in-the-repo) | 2026-10-07 | Live schema snapshot in the repo | merged (repo practice) |
| [010](#adr-010-background-uploads-in-workmanager-measurements-before-submissions) | 2026-10-07 | Background uploads in WorkManager, measurements before submissions | merged; not known to be released |
| [011](#adr-011-scrub-secrets-before-logging-to-crashlytics) | 2026-10-07 | Scrub secrets before logging to Crashlytics | merged; not known to be released |
| [012](#adr-012-unclog-the-retry-queue) | 2026-10-07 | Unclog the retry queue | function pushed (confirmed 2026-10-22); cron batch size not checked |
| [013](#adr-013-app-reports-its-own-tcp-local-port-pending) | 2026-10-13 | App reports its own TCP local port | pending |
| [014](#adr-014-two-locations-per-fcc-test) | 2026-10-22 | Two locations per FCC test | decided, not merged |

---

## ADR-001: Device ID and secret instead of user accounts

- **Date:** 2024-05-08 (commit 12c442c, "Use device ID and secret to secure DB access")
- **Context:** The app writes to Supabase with the anon key, which can be pulled out of the APK. Each device needed to be limited to its own data without asking users to sign up.
- **Decision:**
  - On first use the app calls `register_device(device_id)`. The server returns a random secret and stores only its bcrypt hash.
  - The app stores the secret encrypted with an Android Keystore key and sends `X-Device-ID` and `X-Device-Secret` on every request.
  - RLS policies compare `private.verified_device_id()` with each row's `device_id`.
- **Consequences:**
  - The anon key no longer needs to be treated as a secret.
  - Anyone can register new IDs and insert data under them, but only data tied to IDs they registered. The commit accepts this, since such data is easy to remove once identified.
  - The bcrypt check runs inside policies, which costs time for each row checked.
  - `register_device` fails on a second call for the same ID, so a lost response or lost secret locks the device out. A fix is on the unpushed local branch `register-device-retry` (no PR; its migration `202610220001` is not pushed).
  - Details: [identity-and-security](identity-and-security.md).
- **Status:** in prod: the app side is in the released build, and `register_device` and the `verified_device_id*` checks are on prod.

## ADR-002: TCP tuple service for the FCC server fields

- **Date:** 2023-12-18 (commit 5e077b9, "Include TCP tuple when uploading measurements". It replaced an IP-only lookup through `api.ipify.org`.)
- **Context:** The BDC payload needs `server_source_ip_address`, `server_source_port`, and `server_timestamp`. Supabase's edge does not pass the client's source port.
- **Decision:** Before uploading FCC submissions, the app does a GET to a small echo service on EC2 (`TCP_TUPLE_URL`). The service returns the remote address, remote port, and its own timestamp, and those values are copied onto the submissions.
- **Consequences:**
  - The connection is plain HTTP (`usesCleartextTraffic="true"`) and unauthenticated.
  - The port belongs to the GET connection, not the upload connection.
  - If the service is down, no FCC submission uploads.
  - One tuple is shared by every submission in an upload run.
  - The service is outside this repo.
- **Status:** in prod (released app). Replacement being evaluated in [ADR-013](#adr-013-app-reports-its-own-tcp-local-port-pending).

## ADR-003: FCC submissions only for cellular tests in FCC challenge mode

- **Date:** 2024-05-30 (commit 042f9c0, "Don't create FCC submission in testing mode")
- **Context:** Only consumer challenges taken on the mobile network are valid BDC data.
- **Decision:** [MeasurementManager.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/domain/fcc/MeasurementManager.kt) creates an `FccSubmission` only when the mode is `FCC_CHALLENGE`, none of the three tests ran on Wi-Fi, and cellular data was enabled. `SettingsRepository` picks `FCC_CHALLENGE` by default only when name, phone, email, and the FCC sharing acknowledgement are all set.
- **Consequences:**
  - Testing-mode and Wi-Fi measurements still upload to `public.measurements`, but never reach the FCC.
  - The submission ID is the group ID.
- **Status:** in prod (released app).

## ADR-004: M-Lab MSAK for measurements, in a separate library

- **Date:**
  - 2024-05-29: commit 1964281, "Use any M-Lab server, not just the US ones".
  - 2024-06-19: commit d0fc127, "Extract MSAK integration into separate library".
- **Context:** The app needs third-party measurement servers and a client for them.
- **Decision:**
  - Use M-Lab's Locate API and MSAK servers through `CellWatch/msak-android` (now 0.1.3 via JitPack).
  - Pick the throughput server by ping using `lib/AndroidPing.aar`.
  - Allow non-US servers. The commit's reasoning: M-Lab may move data between servers anyway, so US-only storage could not be guaranteed.
- **Consequences:**
  - Measurement protocol changes happen in the separate library.
  - Debug builds can target a local MSAK server (`MSAK_SERVER_ENV=local`).
  - Release builds always use `prod`.
- **Status:** in prod (released app).

## ADR-005: Crashlytics only, no Analytics

- **Date:** 2024-06-26 (commit 8021174, "Remove dependency on Firebase analytics")
- **Context:** Firebase Analytics was unused and pulled in the `AD_ID` permission.
- **Decision:** Keep only `firebase-crashlytics`.
- **Consequences:**
  - Logs and non-fatal errors still go to Google, with the device ID attached as a custom key.
  - [ADR-011](#adr-011-scrub-secrets-before-logging-to-crashlytics) handles the secrets that reached those logs.
- **Status:** in prod (released app).

## ADR-006: Post to the FCC from Postgres, not from the phone

- **Date:** by 2025-10-13. The server functions first appear in this repo in #109 ("migrate supabase functions", [post_fcc_submission_func.sql](../../supabase/post_fcc_submission_func.sql)). Before that, the server side lived in a separate repository (commit 12c442c mentions it).
- **Context:**
  - Posting to the BDC API needs an FCC-issued hash secret, which must not ship in the APK.
  - The payload combines rows from several tables.
- **Decision:**
  - The phone only uploads raw rows.
  - Postgres builds the BDC JSON (`private.fcc_submissions_view`) and POSTs it from `private.post_fcc_submission` using the **`http` (pgsql-http) extension**.
  - The secret is read from Supabase Vault through `private.fcc_submission_info`, one row per environment.
- **Consequences:**
  - The FCC secret stays in the database.
  - The posting logic can be fixed by pushing a migration, without an app release.
  - The `http` call is synchronous and holds a database connection for the whole FCC request. That is why [ADR-012](#adr-012-unclog-the-retry-queue) caps run time.
  - `pg_net` (async HTTP) is not used.
- **Status:** in prod (pushed): `post_fcc_submission`, `fcc_submissions_view` and `fcc_submission_info` are on prod.

## ADR-007: Retry queue processed by pg_cron

- **Date:** by 2026-09-30, when the cron job was pulled from the live project. Recorded in the repo on 2026-10-07 by #113, whose message says "no migration tracks" the queue, the trigger, or the retry function.
- **Context:** Posting directly from the insert trigger would tie the upload to the FCC API's availability, and a single failure would lose the post.
- **Decision:**
  - The `trg_auto_post_fcc_submission` AFTER INSERT trigger only queues the submission in `private.fcc_submission_retry_queue`.
  - The pg_cron job `fcc-submission-retries` (`*/2 * * * *`) calls `private.process_fcc_submission_retries`.
  - Exponential backoff (60 s doubling, capped at 1 h).
  - A row is blocked after 6 attempts, on a 4xx other than 408/429, or when the provider is missing.
  - Every attempt is logged in `private.fcc_submission_failures`.
- **Consequences:**
  - Phone uploads never wait on the FCC.
  - Blocked rows need manual repair and re-queueing.
  - Despite its name, the failures table logs successes as well (`succeeded=true`).
- **Status:** in prod (pushed). The queue, trigger and functions matched prod on 2026-10-22; the cron job itself was last read from prod on 2026-09-30.

## ADR-008: Post only complete groups

- **Date:** 2026-07-14 (commit c51980e, migration [202607140001](../../supabase/migrations/202607140001_repair_fcc_submission_functions.sql))
- **Context:** Submissions were reaching the FCC without all three tests.
- **Decision:**
  - `private.fcc_submission_is_ready` requires exactly one latency, one download, and one upload measurement, each with its data row. It checks that the tests are present, not that they succeeded; per the migration header, "A failed measurement is valid challenge data".
  - `post_fcc_submission` also checks that the built payload has all three `tests` keys.
- **Consequences:** Incomplete groups wait in the queue and are not counted as failed attempts.
- **Status:** in prod (server side). Prod's `fcc_submission_is_ready` and `post_fcc_submission` match the 2026-10-07 snapshot (catalog check, 2026-10-22), and the snapshot versions contain both checks. The migration file's own definitions were superseded: the live ones are the snapshot's. The app changes in the same commit (FCC validation, carrier fallback) are merged to `main` and not known to be released.

## ADR-009: Live schema snapshot in the repo

- **Date:** 2026-10-07 (#113, commit 54b0bce)
- **Context:** The live database had drifted from anything tracked in git.
- **Decision:**
  - Commit a `pg_dump`-style [full_schema.sql](../../supabase/full_schema.sql) of the live project.
  - Commit [cron_jobs.sql](../../supabase/cron_jobs.sql), since schema dumps leave out `cron.job` rows.
  - Fix [sync_from_remote.sh](../../supabase/sync_from_remote.sh) so it can refresh both.
  - Make later changes as dated migrations under `supabase/migrations/`.
- **Consequences:**
  - `full_schema.sql` is the reference for what is live; migrations newer than the snapshot may not be in it.
  - Cron job changes must be applied separately.
  - See [supabase/migrations-and-deploy](../supabase/migrations-and-deploy.md).
- **Status:** merged (a repo practice; nothing to release or push). On 2026-10-22 prod's app schemas matched `full_schema.sql` plus `202610070001`.

## ADR-010: Background uploads in WorkManager, measurements before submissions

- **Date:** 2026-10-07 (#111, commit fe8b05b)
- **Context:**
  - Phones with a backlog re-sent everything at once and timed out before marking rows uploaded.
  - Their FCC submissions went up without the measurements, leaving submissions the server could not complete.
- **Decision:**
  - `UploadWorker`: unique work, network constraint, up to 5 retries.
  - `MeasurementUploader`: one run at a time and at most 3 requests in flight.
  - A duplicate key counts as already uploaded, which is safe because `insert_measurement` is one transaction.
  - A submission is sent only after all three of its measurements are uploaded.
- **Consequences:**
  - Closing a screen no longer cancels uploads.
  - Submissions can sit on the phone in `waiting` until their measurements upload.
- **Status:** merged to `main`; not known to be in a release (versionCode is still 13; check Play Console).

## ADR-011: Scrub secrets before logging to Crashlytics

- **Date:** 2026-10-07 (#112, commit d795dad)
- **Context:** Supabase client error messages include every request header, which put `X-Device-Secret` and the API key into Crashlytics.
- **Decision:** `Log.redactSecrets` removes `x-device-secret`, `authorization`, and `apikey` values and JWT-shaped strings from every message and exception chain before anything is emitted.
- **Consequences:**
  - Stack traces are kept.
  - Secrets that were already sent to Crashlytics before this change are not removed by it.
- **Status:** merged to `main`; not known to be in a release (versionCode is still 13; check Play Console). Until a release includes it, the released build may still send these secrets.

## ADR-012: Unclog the retry queue

- **Date:** 2026-10-07 (commit d78b06f, migration [202610070001](../../supabase/migrations/202610070001_unclog_fcc_retry_queue.sql))
- **Context:** The job took one entry every 2 minutes, and about 100 never-ready entries were re-checked every 5 minutes forever. New submissions waited about 3.5 hours.
- **Decision:**
  - The job asks for 10 entries per run.
  - A run stops taking new entries after 60 s, because an FCC request can take up to 2 minutes to fail.
  - Entries that have waited more than a day are re-checked hourly.
- **Consequences:**
  - Throughput rises roughly 10×.
  - [cron_jobs.sql](../../supabase/cron_jobs.sql) (the 2026-09-30 snapshot) still shows batch size 1 until it is refreshed.
  - Tested in a throwaway, offline container ([db_tests/run.sh](../../supabase/db_tests/run.sh)).
- **Status:** function pushed: prod's `process_fcc_submission_retries` matches this migration's version (catalog hash match, 2026-10-22). The cron job's batch size of 10 was not checked (`cron.job` was not read). The Step 0 pre-checks in the prod deploy runbook (a local file, not in git) read the job and expect `process_fcc_submission_retries(10)`.

## ADR-013: App reports its own TCP local port (pending)

- **Date:** 2026-10-13 (uncommitted work on the `client-port-header` branch)
- **Context:**
  - The EC2 tuple service ([ADR-002](#adr-002-tcp-tuple-service-for-the-fcc-server-fields)) is a single point of failure that runs over plain HTTP, and the team wants to remove it.
  - Supabase can supply the IP (`cf-connecting-ip`) and a timestamp, but not the client port.
  - The phone knows only its pre-NAT port, which matches what a server sees only on networks that keep ports, and many carrier NATs don't.
- **Decision:** Before removing EC2, measure how often the phone's own port matches what EC2 sees.
  - App:
    - `LocalPortHeaders.kt`, an OkHttp network interceptor, sends `X-Local-Port` and `X-Transport` (`vpn` / `cellular` / `wifi` / `ethernet` / `other`).
    - The tuple request records its own local port as `tuple_local_port`.
    - The Ktor engine switches from CIO to OkHttp so the interceptor can see the socket.
  - Server: migration `202610130001`.
    - Adds `tuple_local_port`, `upload_local_port`, `upload_transport`, and `upload_ip` to `fcc_submissions`. None of these are sent to the FCC.
    - Adds trigger `_310_fcc_submission_record_local_ports`.
    - Adds view `private.source_port_comparison`, with per transport and provider counts of `port_kept` / `port_changed` and `percent_kept`.
  - DB test: `local_ports_test.sql`.
- **Consequences:**
  - The migration must be pushed to prod before an app that sends `tuple_local_port` is released, or PostgREST will reject the unknown column.
  - The uploader copies one tuple onto every submission in a run, so comparison counts should be de-duplicated by `(source_ip, source_port, server_timestamp)`.
  - A device-reported port cannot satisfy a field the spec defines as "measured by the server". `TODO.md` (local file, not in git) records a recommendation to shelve this comparison and evaluate an edge proxy (AWS CloudFront `CloudFront-Viewer-Address`) instead. That proposal is not decided.
  - The `supabase/config.toml` Postgres version change (15 → 17) in the same uncommitted work is unrelated housekeeping; `main` still says 15. Per `db_tests/run.sh`, the live project runs Postgres 17.
- **Status:** pending: uncommitted on `client-port-header`, not in a PR or merged; migration `202610130001` is not pushed to prod. EC2 stays until this is settled.

## ADR-014: Two locations per FCC test

- **Date:** 2026-10-22
- **Context:**
  - BDC Mobile Speed Test Data Specification §5.1 says each download, upload, and latency test object contains at least two Location objects (start and end).
  - The app records 0–2 fixes per test (`listOfNotNull(beginLocation, endLocation)` in [MeasurementTest.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/domain/fcc/MeasurementTest.kt)).
  - The BDC intake was observed to reject only empty location lists.
- **Decision:**
  - Require two locations per test for FCC submissions, following the spec rather than the intake's observed behaviour.
  - App side: a submission is FCC-submittable only with two locations per test, and the app retries for a fresh fix when the start and end fixes are the same.
  - Server side: a separate "one location" hold reason in the submission pipeline.
- **Consequences:**
  - Some tests that would have been accepted are held back.
  - If the FCC confirms one location is enough, the server hold can be reverted and the held entries released.
- **Status:** decided, not merged. The work is unpushed local commits on the branches of open PRs #130 (app) and #133 (server); neither PR contains it yet. Nothing on `main` or prod enforces it: `fcc_submission_is_ready` does not count locations.
