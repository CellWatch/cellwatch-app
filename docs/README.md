# CellWatch documentation

CellWatch is an Android app that runs cellular speed tests (latency, download and upload) against M-Lab servers. It stores the results on the phone and uploads them to a Supabase (Postgres) backend. In FCC Challenge mode, the backend forwards each qualifying test to the FCC Broadband Data Collection (BDC) as a mobile speed test challenge.

These pages were added by #136 and describe the code on `main` as of 2026-11-01: after the 2026-10-27 merges of #132, #133, #134, #136 and #148, #150 (which took two measurement changes back off `main`), #151 (migration review fixes), #154 and #156 (the FCC queue digest email), and the schema snapshots taken after each prod push (#152, #153, #155, #157, #158). Work that is not on `main` is marked **pending**, and each mention says where it is (uncommitted on the `client-port-header` branch, or a local branch such as `measurement-process-changes`). Facts the authors could not confirm from the code are marked `> TODO: verify`.

**Merged to `main` is not in prod.** An app change reaches users only in a Play Store release. A Supabase change is live only once its migration has been pushed to the prod project ("CellWatch Server"); pushes are manual. Status lines use these stages: local branch → open PR → merged to `main` → released (app) or pushed (Supabase).

- **App:** the Play Store build is versionCode 13 (versionName 1.0.0). `versionCode` has been 13 since #109 (2025-10-13), and the repo does not record which commit that build came from, so older app changes merged after #109 (c51980e, #111, #112) are not known to be released (check Play Console). Nothing has been released since the 2026-10-27 merges, so #136's app fixes and #148 are merged to `main` but not released. The last git release tag is v1.0.0 (2024-11-21, versionCode 8).
- **Supabase:** [full_schema.sql](../supabase/full_schema.sql), [cron_jobs.sql](../supabase/cron_jobs.sql) and [role_settings.sql](../supabase/role_settings.sql) were pulled from prod on 2026-11-01, after that day's pushes (#158). **Every migration on `main` is on prod**: `202607140001` and `202610070001` were applied by hand earlier; `202609300001`, `202610210001`, `202610210101`, `0103` and `0104` were pushed on 2026-10-29; `202610220001`, `202610220102` and `202610310001` on 2026-10-31; and `202610310002` and `202610220101` on 2026-11-01. All are recorded in prod's `supabase_migrations.schema_migrations`. Only `202610130001`, uncommitted on `client-port-header`, is not on main or prod. See [migrations-and-deploy](supabase/migrations-and-deploy.md) and the "Where things stand" table in the [release checklist](ops/release-checklist.md).
- **Test project:** a second Supabase project, "CellWatch Test Database", was brought up to prod's schema on 2026-10-22 (before the 2026-10-29 push), with FCC posting disabled (auto-post trigger off, no pg_cron).

## Reading order for new contributors

1. [Architecture overview](architecture/overview.md): the parts of the system and how they connect.
2. [Data flow](architecture/data-flow.md): one test followed from the phone to the FCC.
3. [App layout](app/layout.md) and [Supabase schema](supabase/schema.md): where the code and data live.
4. [Local setup](dev/local-setup.md) and [Testing](dev/testing.md): how to get a working dev environment.
5. Read the rest of the pages as your work needs them.

## Contents

### Architecture
| Page | What it covers |
|---|---|
| [overview](architecture/overview.md) | System diagram, component table, trust boundaries |
| [data-flow](architecture/data-flow.md) | Sequence diagram of one test group end to end; where a group can get stuck |
| [identity-and-security](architecture/identity-and-security.md) | Device registration and secret, RLS, header trust, personal-data inventory, Crashlytics scrubbing |
| [decisions](architecture/decisions.md) | ADR log of design decisions visible in code and history |

### Android app
| Page | What it covers |
|---|---|
| [layout](app/layout.md) | Package map, layer dependencies, hand-wired singletons, per-file index |
| [measurement-engine](app/measurement-engine.md) | `MeasurementService`, latency/throughput tests, MSAK server choice, what each test captures, collection modes |
| [local-storage](app/local-storage.md) | Room schema (v18) and migrations, DataStore keys, `Encryptor` |
| [sync-and-upload](app/sync-and-upload.md) | Upload triggers, `UploadWorker`/`MeasurementUploader`, device registration, timeouts, RPCs and headers, serialization rules |
| [ui-and-screens](app/ui-and-screens.md) | Every screen, navigation, onboarding order, permissions, map hexes |
| [build-and-config](app/build-and-config.md) | Gradle/AGP/SDK versions, BuildConfig fields, AARs, dependencies, signing |

### Supabase backend
| Page | What it covers |
|---|---|
| [schema](supabase/schema.md) | Tables, views and types in `public`, `private` and `published`; keys, indexes, RLS, grants |
| [rpc-reference](supabase/rpc-reference.md) | Every function: signature, security mode, caller, behaviour |
| [triggers-and-cron](supabase/triggers-and-cron.md) | Every trigger and pg_cron job |
| [fcc-submission-pipeline](supabase/fcc-submission-pipeline.md) | Queueing, readiness, posting via the `http` extension, retry/backoff, lifecycle diagram |
| [migrations-and-deploy](supabase/migrations-and-deploy.md) | Migrations vs snapshot files, local reset, prod deploy, server-first ordering rule, read-only prod access |
| [performance-notes](supabase/performance-notes.md) | Per-row bcrypt cost of RLS checks, FCC payload view cost, how to profile |

### FCC BDC compliance
| Page | What it covers |
|---|---|
| [bdc-spec-mapping](fcc/bdc-spec-mapping.md) | Each BDC spec field mapped to the app model, DB column and payload; validation rules; known gaps |
| [inquiries-and-reconciliation](fcc/inquiries-and-reconciliation.md) | Playbook and SQL for "did this user's tests reach the BDC?" |

### Development and operations
| Page | What it covers |
|---|---|
| [dev/local-setup](dev/local-setup.md) | Prerequisites, `cellwatch.properties` keys, local Supabase, emulator networking, fallbacks |
| [dev/testing](dev/testing.md) | Unit, database and instrumented tests, plus their side effects |
| [ops/runbooks](ops/runbooks.md) | Read-only prod SQL, schema sync, cron recreation, retry queue, stuck devices, prod dumps |
| [ops/release-checklist](ops/release-checklist.md) | Ordered steps to release an app build safely |

Related files at the repo root: [README.md](../README.md) and [CONTRIBUTING.md](../CONTRIBUTING.md). The prod deploy runbook (`prod-deploy-runbook.md`) and the BDC spec PDF (`bdc-mobile-speed-test-data-specifications.pdf`) are local files kept out of git.

## Glossary

| Term | Meaning |
|---|---|
| **Measurement** | One test result: latency, download or upload. It is stored in `measurements` together with its data row (`latency_data` or `upload_download_data`), the locations and the cells seen during the test. |
| **Measurement group** | One press of "Go": the latency, download and upload measurements that share a `group_id`. See [measurement-engine](app/measurement-engine.md). |
| **FCC submission** | A row in `fcc_submissions` whose id equals the group id. It holds the challenge metadata (contact info, environment, etc.). It is created only in FCC Challenge mode when all three tests ran over cellular. |
| **BDC** | The FCC's Broadband Data Collection. Its mobile speed test API receives consumer challenge tests. See [bdc-spec-mapping](fcc/bdc-spec-mapping.md). |
| **Collection mode** | `FCC_CHALLENGE` or `TESTING`. It decides whether a group becomes an FCC submission. |
| **MSAK / M-Lab** | Measurement Lab's Multi-Stream Achievable Kernel throughput protocol and the servers that run it. The app picks prod, staging or local servers from BuildConfig. |
| **TCP tuple service** | An external echo service on EC2 that reports the public IP, port and clock it saw. It fills the FCC server-side source fields. A replacement is being evaluated: uncommitted work on the `client-port-header` branch has the app report its own local port for comparison (**pending**; see [ADR-013](architecture/decisions.md#adr-013-app-reports-its-own-tcp-local-port-pending)). |
| **Device secret** | A per-install secret, encrypted with an Android Keystore key and sent in `X-Device-*` headers. The server checks it with bcrypt in `private.verified_device_id()`. See [identity-and-security](architecture/identity-and-security.md). |
| **Retry queue** | `private.fcc_submission_retry_queue`. A pg_cron job drains it every 2 minutes, posting to the BDC with exponential backoff. |
| **`published` schema** | A world-readable copy of selected measurements, filled by `publish_data`. |
| **H3 hex** | Uber's hexagonal grid. The map draws resolution-8 and resolution-9 cells, and `hex_res`/`hex_ancestor` operate on H3 indexes in SQL. |
| **anon key** | The Supabase public API key built into the APK. Requests run as the `anon` role, and RLS plus device headers decide what they can do. |
| **Pooler** | Supabase's connection pooler. It is the route for read-only prod SQL, and it ignores `PGOPTIONS`. See [runbooks](ops/runbooks.md). |

## Keeping these docs current

- When you change a table, function, trigger or cron job, update the matching `supabase/` page in the same PR.
- When you add a request-body field or an RPC overload, follow the server-first rule in [migrations-and-deploy](supabase/migrations-and-deploy.md).
- When you write a status, name the stage (local branch, open PR, merged to `main`, released, pushed). When an app build is released or a migration is pushed to prod, update the status lines that mention it.
- Draw diagrams in Mermaid so they render on GitHub and stay diffable.
- Never paste keys, tokens, hosts with credentials, device IDs or contact details into these pages.
