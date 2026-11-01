# Local setup

Purpose: get the Android app and a local Supabase backend running on a development machine. This page consolidates README.md and CONTRIBUTING.md, checked against the code.

Related: [Testing](testing.md) · [Build and config](../app/build-and-config.md) · [Schema](../supabase/schema.md) · [Migrations and deploy](../supabase/migrations-and-deploy.md) · [Runbooks](../ops/runbooks.md)

## Prerequisites

| Tool | Version on main | Notes |
|---|---|---|
| JDK | 17 | `compileOptions` / `jvmTarget` are 17 ([app/build.gradle](../../app/build.gradle)). |
| Android Studio + SDK | compileSdk 36 | AGP 8.9.3 and Kotlin 1.8.10 ([build.gradle](../../build.gradle)); Gradle wrapper 8.11.1 ([gradle-wrapper.properties](../../gradle/wrapper/gradle-wrapper.properties)). Since #148 (merged 2026-10-27); README asks for Android Studio Meerkat (2024.3.1 Patch 1) or newer. |
| Docker | Docker Desktop | Needed by `supabase start` and [db_tests/run.sh](../../supabase/db_tests/run.sh). |
| Supabase CLI | README says ≥ 1.180 | Local Postgres major version is 17 in [config.toml](../../supabase/config.toml), matching prod (since 7b27d19, merged in #152 on 2026-10-31). |
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
- `app/cellwatch.properties` is an old copy the build never read. #136 (merged 2026-10-27) removed it from git and gitignores `cellwatch.properties` in every directory, so an existing checkout keeps it on disk, ignored.
- `app/google-services.json` is tracked. Crashlytics is wired into **both** debug and release builds (see [Testing → cautions](testing.md#cautions)).

## Local Supabase

```bash
cd supabase
supabase start          # API 54321, DB 54322, Studio 54323, Inbucket 54324
```

Ports come from [config.toml](../../supabase/config.toml): API 54321, DB 54322, Studio 54323, Inbucket web/SMTP/POP3 54324/54325/54326, analytics 54327/54328 (disabled).

**Loading the schema.** `supabase db reset` applies only `supabase/migrations/*` and `seed.sql`. It does **not** read `full_schema.sql`. On main the migrations folder holds ten incremental migrations (2026-07-14 to 2026-10-22), and they assume the live schema already exists. So on a fresh stack, load the snapshot first:

```bash
# from the repo root, after `supabase start`
psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" -f supabase/full_schema.sql
```

The snapshot was pulled from prod on 2026-11-01, and every migration on `main` is on prod, so it already includes all of them: there is nothing to apply on top. Role timeouts aren't in a schema dump; [role_settings.sql](../../supabase/role_settings.sql) has prod's.

> TODO: verify. A fresh `db reset` probably fails on `202607140001` (schema `private` doesn't exist yet). #134's `202609300001` (merged to `main` 2026-10-27) doesn't fix this: it records only the hand-made queue table, trigger and role settings, and sorts after `202607140001`. [supabase/README.md](../../supabase/README.md) ("Local stack") describes the same gap. The local stack's default superuser credentials are Supabase's well-known local defaults, not a secret.

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

| Claim | Reality on main |
|---|---|
| `SUPABASE_LOCAL_URL="http://127.0.0.1:54321"` | Unreachable from the emulator; use `10.0.2.2`. |
| `cd ../app` before `./gradlew …` | `gradlew` is at the repo root. |
| `./gradlew ktlintCheck`, `detekt` | Neither plugin is configured. |
| "Supply a `google-services.json`" | One is already tracked in `app/`. |
| Edge Functions, golden-file export tests, "Backend SQL/Edge-function tests using Supabase CLI runners" | No `supabase/functions/` and no golden-file tests. DB tests are plain SQL run by [db_tests/run.sh](../../supabase/db_tests/run.sh). |
| `TCP_TUPLE_URL` example value | A literal service address is checked into README/CONTRIBUTING. Treat it as configuration and don't copy it elsewhere. |
| `supabase/migrations/dump-migrations.sql` safety grep | No such file. |

#134 and #148 (both merged 2026-10-27) fixed two earlier entries: README's `supabase db reset` comment now says it applies `migrations/*` and `seed.sql` (`seed.sql` is empty), and README and CONTRIBUTING now say `targetSdk 36`, as on main.
