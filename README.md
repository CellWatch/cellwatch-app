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
/app        Android app (Kotlin, Gradle, minSdk 29, targetSdk 34)
/supabase   Local Supabase project (config, schema, functions, seed)
```

**High level**
- Android client collects location + throughput/latency metrics
- Supabase (Postgres + Auth + Storage + Edge Functions) stores measurement groups (one group is one submeasurement: one measurement is made of one latency, upload and download speed) and serves export endpoints
- Export builder groups valid FCC submissions and emits JSON per spec

---

## Quickstart

### Prereqs
- Android Studio Giraffe+ (JDK 17)
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
supabase db reset   # loads full_schema.sql and seed.sql
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

> `full_schema.sql` creates tables/views/indexes; `post_fcc_submission_func.sql` registers server-side logic for grouping/exports; `seed.sql` adds minimal sample data.

**Safety tip:** If you generate or update migration dumps, inspect for unintended data:
```bash
grep -E "INSERT INTO|COPY " supabase/migrations/dump-migrations.sql | head
```

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
