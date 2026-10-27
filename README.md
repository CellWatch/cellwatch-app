# CellWatch

Network measurement for Android. It collects, visualizes, and exports cellular performance data with FCC BDC compliant bulk submissions.

- Android client for active measurements (download, upload, latency)
- Supabase backend for storage, APIs, and server-side processing
- One-click JSON export aligned with FCC Broadband Data Collection (BDC) mobile speed test spec

---

## Project status
- Active development; public API and data schema may change between releases
- FCC BDC export supports hierarchical JSON with download/upload/latency tests nested per submission

## Architecture
```
/app        Android app (Kotlin, Gradle, minSdk 29, targetSdk 36)
/supabase   Local Supabase project (config, schema, functions, seed)
```

**High level**
- Android client collects location + throughput/latency metrics
- Supabase (Postgres + Auth + Storage + Edge Functions) stores measurement groups (one group is one submeasurement: one measurement is made of one latency, upload and download speed) and serves export endpoints
- Export builder groups valid FCC submissions and emits JSON per spec

---

## Quickstart

### Prereqs
- Android Studio Meerkat (2024.3.1 Patch 1) or newer (JDK 17)
- Node 18+ (for Supabase CLI), Docker (for local Postgres)
- Supabase CLI `supabase >= 1.180`
- Git LFS (if storing large sample datasets)

### Clone
```bash
git clone https://github.com/CellWatch/cellwatch-app.git
cd cellwatch-app
```

### Start backend (local Supabase)
```bash
cd supabase
supabase start
supabase db reset   # applies supabase/migrations and seed.sql; see supabase/README.md, "Local stack"
```

### Build & install Android app
```bash
cd ../app
./gradlew assembleDebug
./gradlew :app:installDebug
```

> The debug build points to your **local** Supabase instance when configured via `cellwatch.properties` (see below). Release builds use the **cloud** Supabase settings.

---

## Configuration

### `cellwatch.properties` (required)
Create a `cellwatch.properties` file at the repository root with the following keys. Debug vs. release behavior is determined in the app’s build config.

```properties
# your local development machine's IP address - used for debug build
SUPABASE_LOCAL_URL="http://127.0.0.1:54321"
SUPABASE_LOCAL_API_KEY=
#SUPABASE_LOCAL_URL=
#SUPABASE_LOCAL_API_KEY=

# Supabase cloud URL - used for release build
SUPABASE_URL=
SUPABASE_API_KEY=

# Changing MSAK_SERVER_ENV from "local" to "prod" or "staging" will use M-Lab's
# production or staging servers during local development. The production
# servers are always used in release builds.
MSAK_SERVER_ENV="local"
MSAK_LOCAL_SERVER_HOST="10.0.2.2:8080"
MSAK_LOCAL_SERVER_SECURE=false
MSAK_LATENCY_PORT=1053
MSAK_LOCAL_LATENCY_PORT=1053

MAPBOX_DOWNLOADS_TOKEN=


TCP_TUPLE_URL="http://34.201.124.67/"
```

**Notes**
- `SUPABASE_LOCAL_URL`/`SUPABASE_LOCAL_API_KEY` are used by **debug** builds to talk to your local Supabase started with `supabase start`.
- `SUPABASE_URL`/`SUPABASE_API_KEY` are used by **release** builds to talk to your hosted Supabase project.
- `MSAK_*` control the measurement servers (M-Lab). Use `MSAK_SERVER_ENV="local"` for local testing; change to `staging` or `prod` to point debug builds at those environments.
- `MAPBOX_DOWNLOADS_TOKEN` is required for map downloads if mapping features are enabled.
- `TCP_TUPLE_URL` is used for tuple reporting in the measurement pipeline.

### Firebase Crashlytics (required)
Crash reporting is **required**. Supply a valid `google-services.json` in `/app` and ensure Gradle is configured for Crashlytics uploads in both debug and release as per your org’s policy.

---

## Backend (Supabase)

Folder contents:
```
/supabase
  config.toml
  full_schema.sql
  post_fcc_submission_func.sql
  seed.sql
```

Local dev commands:
```bash
cd supabase
supabase start
supabase db reset
```

> `full_schema.sql` is a snapshot of the live schema, and `supabase db reset` doesn't load it. Changes to the
> live database are migrations in `supabase/migrations`, tested with `supabase/db_tests/run.sh`.
> `supabase/check_drift.sh` compares the live project with the repo. See supabase/README.md.

**Safety tip:** If you generate or update migration dumps, inspect for unintended data:
```bash
grep -E "INSERT INTO|COPY " supabase/migrations/dump-migrations.sql | head
```

### FCC submission queue

Each new row in `fcc_submissions` is queued in `private.fcc_submission_retry_queue`. Every 2 minutes the pg_cron job `fcc-submission-retries` runs `call private.run_fcc_submission_retries(10);`, which posts up to 10 complete submissions to the FCC and commits after each one.

- **Don't post big batches from the Supabase SQL editor**, or from anything else that runs the call inside one transaction. If such a run fails or times out part way, the FCC keeps the tests it already accepted, but our record of them is rolled back. To work the queue by hand, use psql and a small batch: `call private.run_fcc_submission_retries(1);` (inside a transaction block it refuses to start).
- Queue states: `pending` (waiting for the phone's measurements, or retrying after an error), `held` (the FCC is known to reject it, or it falls short of the spec, e.g. no network codes or fewer than two locations in a test; waiting for a decision), `blocked` (a person has to fix something, e.g. a blank carrier name, which is never filled in automatically), `expired` (measurements still incomplete 30 days after queueing). Posted entries leave the queue.
- To see what is stuck: `select * from private.fcc_queue_health;` (counts by state, reason and device), `select * from private.fcc_queue_attention;` (entries a person should look at), or `select private.fcc_queue_digest();` (a text summary; how to send it daily is described in `supabase/migrations/202610210104_fcc_queue_health.sql`).
- The database tests in `supabase/db_tests` cover the queue: `supabase/db_tests/run.sh` (needs Docker).

---

## Data & exports

### Measurement model (simplified)
- `MeasurementGroup` = top-level run with timestamps, device/network metadata
- `Measurement` = individual samples (download/upload/latency)
- `Cell` and `Location` entities attached where available
- Valid FCC submissions flagged via associated `FccSubmission` metadata

### FCC bulk JSON export
- One JSON object with:
  - `contact` and `submission_category`
  - `submissions[]` where each entry = one `MeasurementGroup`
  - Top-level device/app/network metadata
  - `tests` → `download` / `upload` / `latency` sections
- Aligns with FCC BDC mobile speed test hierarchical format
- [FCC Bulk JSON Export Process Overview](https://help.bdc.fcc.gov/hc/en-us/articles/10482866475547-Bulk-Mobile-Challenge-Process-Overview)
- Export timestamp and server IP/port reuse the same logic used during upload

---

## Development

### Common tasks
```bash
# Kotlin style / lint
./gradlew ktlintCheck
./gradlew detekt

# Unit tests (Android)
./gradlew testDebugUnitTest

# Run Supabase migrations (reset local db)
cd supabase && supabase db reset
```

### Git & PR workflow
- Conventional Commits
- `main` protected; feature branches via `feat/<topic>` or `fix/<topic>`
- PRs must include:
  - Screenshots for UI changes
  - Sample export JSON diffs for data model changes
  - **GitGuardian scan passing** (see below)

### GitGuardian (required)
We enforce secret scanning on every commit and PR.

Suggested local setup:
```bash
# install
pip install ggshield

# authenticate (one-time; follow prompts)
ggshield auth login

# scan staged changes before commit
ggshield secret scan pre-commit

# or scan the whole repo
ggshield secret scan repo .

# CI will run: ggshield secret scan ci
```

---

## Versioning

The app has two version fields in `app/build.gradle`:

| Field | Who sees it | Rule |
|---|---|---|
| `versionCode` | Google Play | Integer. Increase by 1 for every build uploaded to Play; Play rejects a code it has already seen. |
| `versionName` | The FCC, users, our server | Sent as `app_version` in every measurement and FCC submission, shown in Settings, and used in the User-Agent (`CellWatch/<versionName>`). |

`app_version` is read from `BuildConfig.VERSION_NAME` in `MeasurementTest.kt` and from the installed package's
`versionName` (via `EasyAppMod`) in `MeasurementManager.kt`. Both resolve to `versionName`.

### Why `versionName` is constrained

BDC spec §5.1.2 (Data Specifications for Mobile Speed Test Data, rev. 2.2, July 2025) says `app_version` is
required and "must match an approved App Version string" when `submission_category` is Consumer Challenge or
Consumer Crowdsourced. CellWatch submits Consumer Challenge. A `versionName` the FCC doesn't recognise can get
every submission rejected.

What we know about the approved strings:
- During approval (#97) the FCC set the **Demo** allow list to `1.*`.
- The repo has no record of the **Prod** allow list, or of whether a new `1.x` string needs approval.

### History

- `1.0.0-alpha.0` to `1.0.0-beta.2` (2024): `versionName` bumped and tagged (`v1.0.0-alpha.1` ... `v1.0.0-beta.2`)
  with every release.
- `1.0.0` (versionCode 8, Nov 2024, tag `v1.0.0`).
- versionCodes 9-13 (the last one in Oct 2025) all kept `1.0.0` and have no tags. As a result, server rows since
  Nov 2024 can't be traced to a build.
- `public.fix_jitter()` (supabase/full_schema.sql) treats `app_version` null or `1.0.0-alpha.2`/`.3`/`.4` as old
  builds and corrects their jitter. Never reuse those strings.

### Policy (proposal, pending team sign-off and the FCC's answer below)

`versionName` follows `MAJOR.MINOR.PATCH`. The bump depends on whether the change touches what the FCC approved:

- **MAJOR**: changes the measurement methodology or the submitted data. Examples: the test protocol or servers,
  test durations, how throughput, latency or `success_flag` are computed, which fields are collected, or how a
  submission is formed. The FCC/OET must review it before release, and it will need a new approved string,
  because `2.*` doesn't match `1.*`.
- **MINOR**: user-visible features that don't change measurement or submission semantics, such as UI, history,
  export or translations.
- **PATCH**: fixes that leave measurement semantics alone, such as upload reliability, crashes, or filling a
  required field the spec already defines (for example, the carrier-name fallback).
- `versionCode` increases by 1 with every Play upload, including uploads where `versionName` doesn't change.
- Pre-release suffixes (`-beta.N`) are only for builds that never submit to the FCC's production system. We
  don't know how the FCC matches `1.*` against suffixed strings.

**Open question for the FCC** (blocks #122): does a new `versionName` inside `1.x` (for example `1.0.1` or
`1.1.0`) need approval, and does the Prod allow list match `1.*` like Demo? **Until they answer, bump only
`versionCode` and keep `versionName` at `1.0.0`.** That release then can't be told apart from earlier builds
in the server data. Record its `versionCode` in the Git tag instead (see below).

### Tagging a release

Make one commit that bumps the version, with the subject `Release v<versionName> (versionCode <N>)`, and add an
annotated tag:

```bash
git tag -a v1.0.0+14 -m "versionCode 14: upload and carrier fixes"   # versionName unchanged: add +<versionCode>
git tag -a v1.0.1 -m "..."                                            # versionName changed
```

See CONTRIBUTING.md, "Release & tagging".

---

## Testing
- Android unit tests for data processing and export assembly
- Instrumented tests for permission flows and long-running services
- Backend SQL/Edge-function tests using Supabase CLI runners
- Golden-file tests for FCC export: compare JSON against fixtures

---

## Contributing
We welcome contributions!

1. Fork and create a feature branch  
2. Make changes with tests  
3. Run **ggshield** locally  
4. Submit a PR with context, steps to reproduce, and screenshots/JSON diffs

---

## Security
- Report vulnerabilities privately to `cellwatch@groups.gatech.edu` or via GitHub Security Advisory
- Keep all API keys in `cellwatch.properties` locally and in CI secret stores for builds
- Enable pre-commit scans to prevent key leaks

---

## License

### TODO

---

## Acknowledgments
Thanks to contributors and upstream projects (Android, Kotlin, Supabase, Mapbox, M-Lab).

---

Maintainers: https://cellwatch.cc.gatech.edu/. For roadmap and discussions, see GitHub Issues/Discussions.
