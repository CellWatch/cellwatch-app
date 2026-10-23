# CellWatch documentation

CellWatch is an Android app that runs cellular speed tests (latency, download and upload) against M-Lab servers. It stores the results on the phone and uploads them to a Supabase (Postgres) backend. In FCC Challenge mode, the backend forwards each qualifying test to the FCC Broadband Data Collection (BDC) as a mobile speed test challenge.

These pages describe the code in this repository as it stands on the branch they were written from. Changes that are written but not merged or deployed are marked **pending**. Facts the authors could not confirm from the code are marked `> TODO: verify`.

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
| [sync-and-upload](app/sync-and-upload.md) | Upload triggers, `UploadWorker`/`MeasurementUploader`, RPCs and headers, serialization rules |
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
| [ops/release-checklist](ops/release-checklist.md) | Ordered steps to ship an app build safely |

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
| **TCP tuple service** | An external echo service on EC2 that reports the public IP, port and clock it saw. It fills the FCC server-side source fields. It is being replaced by the app reporting its own local port (**pending**). |
| **Device secret** | A per-install secret, encrypted with an Android Keystore key and sent in `X-Device-*` headers. The server checks it with bcrypt in `private.verified_device_id()`. See [identity-and-security](architecture/identity-and-security.md). |
| **Retry queue** | `private.fcc_submission_retry_queue`. A pg_cron job drains it every 2 minutes, posting to the BDC with exponential backoff. |
| **`published` schema** | A world-readable copy of selected measurements, filled by `publish_data`. |
| **H3 hex** | Uber's hexagonal grid. The map draws resolution-8 and resolution-9 cells, and `hex_res`/`hex_ancestor` operate on H3 indexes in SQL. |
| **anon key** | The Supabase public API key shipped in the app. Requests run as the `anon` role, and RLS plus device headers decide what they can do. |
| **Pooler** | Supabase's connection pooler. It is the route for read-only prod SQL, and it ignores `PGOPTIONS`. See [runbooks](ops/runbooks.md). |

## Keeping these docs current

- When you change a table, function, trigger or cron job, update the matching `supabase/` page in the same PR.
- When you add a request-body field or an RPC overload, follow the server-first rule in [migrations-and-deploy](supabase/migrations-and-deploy.md).
- Draw diagrams in Mermaid so they render on GitHub and stay diffable.
- Never paste keys, tokens, hosts with credentials, device IDs or contact details into these pages.
