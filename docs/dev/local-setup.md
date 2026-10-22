# Local setup

Purpose: get the Android app and a local Supabase backend running on a development machine. This page consolidates README.md and CONTRIBUTING.md, checked against the code.

Related: [Testing](testing.md) · [Build and config](../app/build-and-config.md) · [Schema](../supabase/schema.md) · [Migrations and deploy](../supabase/migrations-and-deploy.md) · [Runbooks](../ops/runbooks.md)

## Prerequisites

| Tool | Version in this checkout | Notes |
|---|---|---|
| JDK | 17 | `compileOptions` / `jvmTarget` are 17 ([app/build.gradle](../../app/build.gradle)). |
| Android Studio + SDK | compileSdk 35 | AGP 8.1.2 and Kotlin 1.8.10 ([build.gradle](../../build.gradle)); Gradle wrapper 8.9 ([gradle-wrapper.properties](../../gradle/wrapper/gradle-wrapper.properties)). |
| Docker | Docker Desktop | Needed by `supabase start` and [db_tests/run.sh](../../supabase/db_tests/run.sh). |
| Supabase CLI | README says ≥ 1.180 | Local Postgres major version is 17 in [config.toml](../../supabase/config.toml) (*pending* change; committed value is 15). |
| Mapbox downloads token | | Gradle needs it to resolve the Mapbox Maven repo ([settings.gradle](../../settings.gradle)). |

## `cellwatch.properties`

Create `cellwatch.properties` at the **repo root**. It is gitignored. Both [settings.gradle](../../settings.gradle) and [app/build.gradle](../../app/build.gradle) read it, and the build fails without it. Key names and meanings (never commit values):

| Key | Used by | Meaning |
|---|---|---|
| `SUPABASE_LOCAL_URL` | debug `SUPABASE_URL` | Local Supabase API URL, for example `http://10.0.2.2:54321` from the emulator. |
| `SUPABASE_LOCAL_API_KEY` | debug `SUPABASE_API_KEY` | The local stack's anon key (printed by `supabase start`). |
| `SUPABASE_URL` | release `SUPABASE_URL` | Cloud project API URL. |
| `SUPABASE_API_KEY` | release `SUPABASE_API_KEY` | Cloud project anon key. |
| `MSAK_SERVER_ENV` | debug only | `local`, `staging` or `prod` (M-Lab locate environment). Release hard-codes `prod`. |
| `MSAK_LOCAL_SERVER_HOST` | debug only | `host:port` of a local MSAK server when `MSAK_SERVER_ENV=local`. Release uses `""`. |
| `MSAK_LOCAL_SERVER_SECURE` | debug only | `true` for TLS to the local MSAK server. Release hard-codes `true`. |
| `MSAK_LATENCY_PORT` | release `MSAK_LATENCY_PORT` | Latency port. The `BuildConfig` field is defined but no app code reads it. |
| `MSAK_LOCAL_LATENCY_PORT` | debug `MSAK_LATENCY_PORT` | As above, for debug builds. |
| `MAPBOX_DOWNLOADS_TOKEN` | settings.gradle | Secret token for the Mapbox Maven repo. The runtime map token is the `mapbox_access_token` string resource. |
| `TCP_TUPLE_URL` | both build types | The TCP tuple service that reports the phone's public IP, port and a timestamp for the FCC `server_*` fields. Debug and release use the same value. |

String values need surrounding quotes, because they are pasted into `buildConfigField` as Java literals.

Notes:
- `app/cellwatch.properties` was an old tracked copy the build never read. #136 removed it from git, and `cellwatch.properties` is now gitignored in every directory.
- `app/google-services.json` is tracked. Crashlytics is wired into **both** debug and release builds (see [Testing → cautions](testing.md#cautions)).

## Local Supabase

```bash
cd supabase
supabase start          # API 54321, DB 54322, Studio 54323, Inbucket 54324
```

Ports come from [config.toml](../../supabase/config.toml): API 54321, DB 54322, Studio 54323, Inbucket web/SMTP/POP3 54324/54325/54326, analytics 54327/54328 (disabled).

**Loading the schema.** `supabase db reset` applies only `supabase/migrations/*` and `seed.sql`. It does **not** read `full_schema.sql`. This checkout's migrations folder holds just three incremental migrations, and they assume the live schema already exists. So on a fresh stack, load the snapshot first:

```bash
# from the repo root, after `supabase start`
psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" -f supabase/full_schema.sql
```

> TODO: verify. A fresh `db reset` probably fails on `202607140001` (schema `private` doesn't exist yet). A baseline migration (`202609300001`) that fixes this is on the unmerged `board-docs` branch (#134). The local stack's default superuser credentials are Supabase's well-known local defaults, not a secret.

[sync_from_remote.sh](../../supabase/sync_from_remote.sh) builds the local stack from prod instead. It is destructive to the local DB and pulls prod data, which contains PII; see [Runbooks](../ops/runbooks.md#refreshing-the-local-schema-snapshot).

**FCC posting stays off locally** as long as `private.fcc_submission_info` is empty. The insert trigger still queues every submission with `fcc_env = 'prod'`, but posting fails with "missing FCC submission info" and nothing leaves the machine. Never put prod BDC credentials into a local database.

### When Docker is unavailable

If the Docker daemon is broken (for example, every API call returns 500), restarting Docker Desktop usually fixes `supabase start`:

```bash
osascript -e 'quit app "Docker"'; open -a Docker
```

For **SQL-only repros** (functions, policies, query plans) without Docker, use a throwaway Homebrew PostgreSQL 14 cluster on loopback:

```bash
PGBIN=/opt/homebrew/opt/postgresql@14/bin
D=$(mktemp -d)/data
$PGBIN/initdb -D "$D" -U postgres --auth=trust
$PGBIN/pg_ctl -D "$D" -l "$D.log" -o "-c listen_addresses=127.0.0.1 -c port=54329 -c unix_socket_directories=''" start
psql -h 127.0.0.1 -p 54329 -U postgres
# ... when done:
$PGBIN/pg_ctl -D "$D" -m fast stop
```

- Use TCP: long scratch paths exceed the 103-byte Unix socket limit.
- `full_schema.sql` expects Supabase roles and extensions. Load excerpts with `set check_function_bodies = false;` first, and create the `anon` role and the `extensions` and `private` schemas.
- Prod runs PG17. Plans can differ slightly from PG14, but RLS behaviour has matched.

## Emulator networking

From the Android emulator, the host machine is **`10.0.2.2`**; `127.0.0.1` is the emulator itself. Set `SUPABASE_LOCAL_URL` to `http://10.0.2.2:54321` and `MSAK_LOCAL_SERVER_HOST` to `10.0.2.2:<port>`. Cleartext HTTP is allowed app-wide (`android:usesCleartextTraffic="true"` in the manifest). On a physical phone, use the host's LAN IP.

## Measurement servers (MSAK)

- `MSAK_SERVER_ENV=prod` or `staging` uses M-Lab's servers through the locate service, which needs internet.
- `MSAK_SERVER_ENV=local` points at `MSAK_LOCAL_SERVER_HOST`. In local mode, the app also generates a measurement id up front ([MeasurementManager.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/domain/fcc/MeasurementManager.kt)). The client library is `com.github.CellWatch:msak-android:0.1.3`.

> TODO: verify how to run a local MSAK server (presumably M-Lab's `msak` server; README's example host is port 8080 for throughput and 1053 for latency). The repo doesn't document it.

## Running the app

1. Start local Supabase and load the schema (above).
2. Fill in `cellwatch.properties`.
3. Run the `app` configuration from Android Studio on an emulator or phone.

**Caution:** every debug build reports `Log.w`/`Log.e` to the **real Crashlytics project**. On first upload, the app registers its device id with whichever Supabase project the build points at. For automated validation, prefer `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` ([Testing](testing.md)) over installing a build.

## Where README / CONTRIBUTING are stale

| Claim | Reality in this checkout |
|---|---|
| `targetSdk 34` | `targetSdk 35`, `compileSdk 35`. Play now requires 36 for updates; that upgrade is pending (#135). |
| `supabase db reset` "loads full_schema.sql and seed.sql" | It applies `migrations/*` and `seed.sql` only. `seed.sql` is empty. |
| `SUPABASE_LOCAL_URL="http://127.0.0.1:54321"` | Unreachable from the emulator; use `10.0.2.2`. |
| `cd app && ./gradlew …` | `gradlew` is at the repo root. |
| `./gradlew ktlintCheck`, `detekt` | Neither plugin is configured. |
| "Supply a `google-services.json`" | One is already tracked in `app/`. |
| Edge Functions, golden-file export tests, "Backend SQL/Edge-function tests using Supabase CLI runners" | No `supabase/functions/` and no golden-file tests. DB tests are plain SQL run by [db_tests/run.sh](../../supabase/db_tests/run.sh). |
| `TCP_TUPLE_URL` example value | A literal service address is checked into README/CONTRIBUTING. Treat it as configuration and don't copy it elsewhere. |
| `supabase/migrations/dump-migrations.sql` safety grep | No such file. |
