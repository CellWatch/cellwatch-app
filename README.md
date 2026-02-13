# CellWatch

CellWatch is a cellular signal quality measurement app used to collect data for FCC cellular quality challenge workflows.

This repository currently contains:
- A frozen legacy Android app (`frozenApp/`) kept for reference only
- A Kotlin Multiplatform shared module (`shared/`) under active migration
- An isolated Android KMP test harness module (`androidTestApp/`) for shared-sync integration work
- An isolated iOS host integration app (`iosTestApp/`) for parity testing of keychain and sync-harness behaviors
- A legacy iOS host integration app (`iosSharedIntegrationHost/`) retained for backward compatibility

## Current Project Status

The project is mid-migration from Android-only Kotlin to KMP:
- Shared domain/data logic and SQLDelight testing are active in `shared/`
- Shared encryption and secure key storage abstractions are implemented and tested
- iOS product app UI is not yet implemented here; iOS currently uses a host test app for realistic integration testing

## Current UI Slice (Harness Apps)

First cross-platform UI slice (intentionally minimal):
- One button to run shared map-start slice
- One button to run shared Phase 3 sequence
- One text results panel
- No map visualization yet
- Location details remain best-effort; platform capability notes explain missing fields

Shared result text contract:
- Android (`androidTestApp`) and iOS (`iosTestApp`) now render Phase 3 output via `Phase3UiSliceFormatter`
- Stable field order is tested in `Phase3UiSliceFormatterTest`

## Repo Structure

- `frozenApp/`: legacy Android-only codebase (frozen reference; not part of active migration/build)
- `shared/`: KMP shared module (common/domain/data/util, SQLDelight schema and tests)
- `androidTestApp/`: isolated Android module for shared/KMP sync wiring and local Supabase smoke tests
- `iosTestApp/`: isolated iOS host app + XCTest target for parity testing (`sharedKit.framework` integration)
- `iosSharedIntegrationHost/`: Minimal iOS app + XCTest target for hosted integration tests against `sharedKit.framework`
- `doc/`: Supporting documentation (including architecture notes)

Porting rule:
- New migration work should go to `shared/`, `androidTestApp/`, and `iosTestApp/`.
- Do not treat `frozenApp/` as the destination for the KMP replacement application.

## User Constraints

- User-requested policy: do not modify legacy Android app code as part of KMP migration work.
- Legacy Android code has been moved from `app/` to `frozenApp/` and is excluded from active module wiring.

## Prerequisites

- Java 17 (project has been standardized on JDK 17)
- Android SDK + emulator tooling (`adb`, `emulator`)
- Xcode + iOS Simulator (for hosted iOS integration tests)
- Docker Desktop (required for local Supabase stack)
- Supabase CLI (`supabase`)

## Local Supabase Safety

Local development and test harness work is now guarded to use local Supabase by default.

- `androidTestApp` resolves Supabase through `CellwatchPropertiesSupabaseEnvironmentProvider`
- Shared sync factory now accepts a transport resolver boundary (`SyncSupabaseConfigResolver`) plus target (`LOCAL`/`REMOTE`) so selection is explicit at construction time
- Default target is `LOCAL`
- `REMOTE` target is hard-blocked unless explicitly enabled with:
  - `CELLWATCH_ALLOW_REMOTE_SUPABASE=true`
- Current local defaults in `cellwatch.properties`:
  - `SUPABASE_LOCAL_URL="http://10.0.2.2:54321"`
  - local Supabase anon key (CLI default)
  - optional local-only override: `SUPABASE_LOCAL_SERVICE_KEY` (use for local RLS-constrained write testing; keep untracked/local)

### Runtime Mode Model (MSAK + Supabase)

Shared runtime profile wiring now supports explicit mode selection:
- MSAK modes: `LOCAL`, `STAGING`, `PUBLIC`
- Supabase modes: `LOCAL`, `TESTING`, `LIVE`

Implementation entrypoints:
- `RuntimeSyncMsakProfiles.fromModes(...)`
- `RuntimeSyncMsakProfileBridge.resolveFromModes(...)`
- `RuntimeProfileConfig` + `RuntimeProfileResolver` + `RuntimeProfileStore` (shared canonical contract)
- `RuntimeProfileContract` (shared validation + canonical config keys)

Shared runtime profile store contract:
- `RuntimeProfileConfig` is the single cross-platform payload for persisted runtime profile values.
- `RuntimeProfileStore` abstracts read/write/clear for app-owned profile persistence.
- `RuntimeProfileResolver` converts stored config to resolved runtime snapshot/profile used by sync + MSAK.
- Platform harnesses now build this shared config and resolve through the shared resolver bridge (instead of ad-hoc mode/url/key wiring).
- Shared resolver now enforces one contract before resolution (`RuntimeProfileContract.requireValid(...)`), so all entrypoints fail with the same deterministic errors.

Runtime source precedence (harnesses/tests):
- Environment variables override file values.
- File values are read from `cellwatch.local.properties` first (if present), then `cellwatch.properties`.
- iOS loopback normalization: `10.0.2.2`/`10.0.3.2` -> `127.0.0.1`.
- Android keeps emulator-safe local defaults for local mode (`10.0.2.2:*`).

Supabase mode mapping:
- `LOCAL` -> shared sync target `LOCAL` (uses `SUPABASE_LOCAL_URL` + `SUPABASE_LOCAL_API_KEY`)
- `TESTING` -> shared sync target `REMOTE` (uses `SUPABASE_TESTING_URL` + `SUPABASE_TESTING_API_KEY`)
- `LIVE` -> shared sync target `REMOTE` (uses `SUPABASE_URL` + `SUPABASE_API_KEY`)

Strict runtime config hardening (current):
- Shared runtime config now supports strict resolution mode (no silent local fallback defaults).
- `iosTestApp` runtime mode resolution runs in strict mode; missing Supabase URL/key fails fast as `Runtime profile unavailable` instead of silently defaulting.
- `androidTestApp` runtime profile resolution defaults to strict mode (`CELLWATCH_STRICT_RUNTIME_CONFIG=true` unless explicitly overridden).
- iOS hosted Tier 2 Gradle tasks now inject `SUPABASE_LOCAL_URL` and `SUPABASE_LOCAL_SERVICE_KEY` directly into `xcodebuild` test settings to avoid environment drift.
- iOS app and iOS hosted tests now consume one shared source in `iosTestApp/App/AppDelegate.swift` (`RuntimeConfigSource`) instead of duplicating env/property parsing per test.
- Android runtime resolver now uses shared runtime keys (`RuntimeProfileContract`) and explicit env > properties precedence.

Deployed-build intent:
- Production/staged app entrypoints should use strict runtime config and require explicit values from persisted app/user profile state.
- Local fallback defaults are only for dev-focused helper paths/tests where explicitly enabled.
- Future final app flow should persist a user-selected `RuntimeProfileConfig` via `RuntimeProfileStore` and resolve at startup through `RuntimeProfileResolver` only.

MSAK local host defaults in harness apps:
- Android harness (`androidTestApp`): when `MSAK` mode is `LOCAL` and `MSAK_LOCAL_SERVER_HOST` is unset, host defaults to `10.0.2.2:8080`.
- iOS harness (`iosTestApp`): when `MSAK` mode is `LOCAL` and `MSAK_LOCAL_SERVER_HOST` is unset, host defaults to `127.0.0.1:8080` (and normalizes `10.0.2.2`/`10.0.3.2` to simulator loopback).

MSAK URL resolution details (current harness behavior):
- Runtime mode `LOCAL` passes `MSAK_LOCAL_SERVER_HOST` into `LocateManager(msakLocalServerHost=..., msakLocalServerSecure=...)`.
- Runtime mode `PUBLIC`/`STAGING` ignores local host and uses locate-discovered endpoints.
- Local default host by harness:
  - Android emulator: `10.0.2.2:8080`
  - iOS simulator: `127.0.0.1:8080` (with normalization from emulator aliases)
- Local MSAK currently runs over HTTP/WS (not TLS):
  - set `MSAK_LOCAL_SERVER_SECURE=false`
  - Android harness enables cleartext in manifest for this local path.

### MSAK Endpoint Contract (Shared + `msak-client-kmp`)

Expected endpoint URL keys from locate/local server selection:
- `ws:///throughput/v1/download`
- `ws:///throughput/v1/upload`
- `http:///latency/v1/authorize`
- `http:///latency/v1/result`

Resolved URL examples in local mode (`MSAK_LOCAL_SERVER_HOST=10.0.2.2:8080`):
- Throughput download: `ws://10.0.2.2:8080/throughput/v1/download`
- Throughput upload: `ws://10.0.2.2:8080/throughput/v1/upload`
- Latency authorize: `http://10.0.2.2:8080/latency/v1/authorize`
- Latency result: `http://10.0.2.2:8080/latency/v1/result`

Notes:
- `msak-client-kmp` appends request query parameters required by each API (for example `mid` in latency calls).
- If local server is up but Phase3 fails with `authorize`/`result` decode errors, first verify protocol compatibility between current `msak-client-kmp` artifact and your local `msak-server`.

Current project policy:
- We only operate in `Supabase LOCAL` mode for active development and test workflows.
- `TESTING`/`LIVE` paths exist for future staged rollout, but remain guard-railed by `allowRemoteSupabase` and are not part of normal day-to-day usage.

### Harness App Permissions (Current)

Android harness (`androidTestApp`) currently declares:
- `android.permission.INTERNET` (required for MSAK + Supabase HTTP traffic)
- `android.permission.ACCESS_NETWORK_STATE` (required for connectivity capability capture and network-state checks)
- `android.permission.ACCESS_WIFI_STATE` (best-effort network context capture; avoids permission-denied paths in some runtime checks)
- `android.permission.READ_PHONE_STATE` (telephony/network state fields used by capability capture and some device-side network APIs)
- `android.permission.ACCESS_COARSE_LOCATION` and `android.permission.ACCESS_FINE_LOCATION` (cell/network capability context on newer Android runtimes)

iOS harness (`iosTestApp`) currently sets:
- `NSAppTransportSecurity` with `NSAllowsArbitraryLoads=true` and `NSAllowsLocalNetworking=true` (local/dev HTTP endpoints used by harness flows)
- `NSLocalNetworkUsageDescription` (local MSAK/Supabase testing on LAN/host paths)

Notes:
- These are harness/demo app permissions only, not final production policy for eventual platform apps.
- Android harness requests `READ_PHONE_STATE` + location permissions at runtime on launch (for best-effort capability capture and telephony/network paths).
- Phase3 interactive runs now execute a preflight gate first (MSAK mode/host + Supabase URL/key sanity) and fail fast with a rendered status message instead of hard-crashing on missing config.
- Both harness apps also print runtime/preflight details to debug logs for investigation:
  - Android: logcat tag `AndroidTestHarness`
  - iOS: `NSLog` lines prefixed with `[iosTestApp]`
- Local MSAK runs require protocol compatibility between `msak-client-kmp` and the local `msak-server`.  
  If you see `MissingFieldException` for `Application.BytesSent`, the server/client schema versions are mismatched.

### Sync Diagnostics (Failure Explainability)

Shared sync reports now carry structured error samples (category, context id, exception type/message, root cause, optional cause chain), not just counts.

Android harness diagnostics knobs (read from `cellwatch.properties` or env at build time):
- `CELLWATCH_SYNC_DIAGNOSTICS_LEVEL=OFF|BASIC|VERBOSE` (default `BASIC`)
- `CELLWATCH_SYNC_DIAGNOSTICS_MAX_SAMPLES=<int>` (default `6`)
- `CELLWATCH_SYNC_DIAGNOSTICS_INCLUDE_CAUSE_CHAIN=true|false` (default `false`)

iOS harness diagnostics knobs (read from env first, then `cellwatch.properties`, at app startup):
- `CELLWATCH_SYNC_DIAGNOSTICS_LEVEL=OFF|BASIC|VERBOSE` (default `VERBOSE`)
- `CELLWATCH_SYNC_DIAGNOSTICS_MAX_SAMPLES=<int>` (default `12`)
- `CELLWATCH_SYNC_DIAGNOSTICS_INCLUDE_CAUSE_CHAIN=true|false` (default `true`)

At `VERBOSE` (or with `INCLUDE_CAUSE_CHAIN=true`), upstream cause chains are included in status rendering to reduce adb/logcat dependence during triage.

### Harness Bootstrap (Before Simulator Taps)

Use this sequence to bootstrap both harnesses before running interactive buttons:

```bash
./gradlew :androidTestApp:assembleDebug
./gradlew :shared:refreshIosSimulatorCurrentFramework
./gradlew :shared:verifyIosTestAppHosted
```

### Start/Stop Local Supabase

Use helper script:

```bash
./scripts/supabase-local.sh start
./scripts/supabase-local.sh status
./scripts/supabase-local.sh stop
```

Or direct CLI:

```bash
supabase start
supabase status
supabase stop
```

### macOS Setup (Docker + Supabase CLI)

1. Install Docker Desktop for Mac
2. Open Docker Desktop once and complete initial setup
3. Verify Docker engine is running:

```bash
docker --version
docker info
```

4. Install Supabase CLI (Homebrew):

```bash
brew install supabase
supabase --version
```

5. Start local Supabase stack from repo root:

```bash
./scripts/supabase-local.sh start
./scripts/supabase-local.sh status
```

6. Confirm local API endpoint responds:

```bash
curl http://127.0.0.1:54321
```

If Android emulator is used, debug builds should continue using `http://10.0.2.2:54321` as configured in `cellwatch.properties`.

Expected local endpoints after start:
- API: `http://127.0.0.1:54321`
- DB: `postgresql://postgres:postgres@127.0.0.1:54322/postgres`

### About Local PostgreSQL Service

Supabase local does not require your Homebrew PostgreSQL service to run; Supabase starts its own Postgres in Docker.

If you still need Homebrew PostgreSQL for other tasks, your local notes map to:

```bash
brew services start postgresql@14
```

or foreground:

```bash
/opt/homebrew/opt/postgresql@14/bin/postgres -D /opt/homebrew/var/postgresql@14
```

## Local KMP `msak` Client Override

`shared/` currently depends on the published artifact:
- `edu.gatech.cc.cellwatch:msak-client-kmp:0.2.2`

Reference repositories used during CellWatch development:
- KMP client library source: [CellWatch/msak-client-kmp](https://github.com/CellWatch/msak-client-kmp)
  - Local inspection path used on this machine: `/Users/jeff/Projects/msak-android` (branch `codex/kotlin-1.9.24-rollback`)
  - Use this repo to inspect current client APIs and demo-app usage patterns when wiring shared measurement flows.
- Upstream server implementation: [m-lab/msak](https://github.com/m-lab/msak)
  - Use this repo for running a local `msak-server` during Phase 3 measurement testing.

Standard endpoint paths expected by current shared selector/executor wiring:
- `throughput/v1/download`
- `throughput/v1/upload`
- `latency/v1/authorize`
- `latency/v1/result`

Preferred local-dev path is Maven Local publication from `msak-client-kmp`:
- publish in producer: `:msak-shared:publishToMavenLocal`
- this repo already has `mavenLocal()` before `mavenCentral()`, so no extra flags are needed

Advanced option (source-composite substitution):
- Use this only when you explicitly want to substitute from a local checkout source tree.
- Note: this can fail Android builds if AGP versions differ between repos.
- Current state (February 11, 2026): AGP has been aligned to `8.9.2` in this repo; composite mode is optional and no longer required for normal development.

Composite mode examples:

```bash
# from this repo root, default local path is ../msak-android
./gradlew -Pcellwatch.useLocalMsak=true :shared:jvmTest
```

```bash
# custom local path
./gradlew \
  -Pcellwatch.useLocalMsak=true \
  -Pcellwatch.local.msak.dir=/absolute/path/to/msak-android \
  :shared:jvmTest
```

Behavior:
- Default (no flags): Maven resolution order applies (`mavenLocal()` first, then remote repos)
- With `cellwatch.useLocalMsak=true`: Gradle uses a composite build and substitutes
  `edu.gatech.cc.cellwatch:msak-client-kmp` with project `:msak-shared` from your local `msak-android` checkout
- If the local path is missing, settings evaluation fails fast with a clear error

For Xcode-hosted iOS builds in this repo (`iosTestApp` and `iosSharedIntegrationHost`), the prebuild step now calls:
- `/Users/jeff/Projects/cellwatch-app/scripts/compile-shared-framework-for-xcode.sh`

That script supports composite-source override via environment variables:

```bash
CELLWATCH_USE_LOCAL_MSAK_COMPOSITE=true \
CELLWATCH_LOCAL_MSAK_DIR=/Users/jeff/Projects/msak-android \
xcodebuild \
  -project iosTestApp/iosTestApp.xcodeproj \
  -scheme iosTestApp \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2" \
  test
```

### Consumer Snippets (Maven Local + Version Catalog)

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenLocal()
        mavenCentral()
    }
}
```

`gradle/libs.versions.toml`:

```toml
[versions]
msakClientKmp = "0.2.2"

[libraries]
msak-client-kmp = { module = "edu.gatech.cc.cellwatch:msak-client-kmp", version.ref = "msakClientKmp" }
```

KMP consumer module `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.msak.client.kmp)
            }
        }
    }
}
```

If you do not use the version catalog:

```kotlin
implementation("edu.gatech.cc.cellwatch:msak-client-kmp:0.2.2")
```

### Local iOS XCFramework Consumption

Producer output:
- zip: `/Users/jeff/Projects/msak-android/msak-shared/build/local-dist/apple/msak-client-kmp/0.2.2/MsakShared.xcframework.zip`
- sha256: `/Users/jeff/Projects/msak-android/msak-shared/build/local-dist/apple/msak-client-kmp/0.2.2/MsakShared.xcframework.sha256`

Steps:
1. Unzip into a stable local path, for example:
   - `/Users/jeff/Projects/cellwatch-app/iosTestApp/Frameworks/MsakShared.xcframework`
2. In Xcode, open the iOS app project and select the app target.
3. Under `General` -> `Frameworks, Libraries, and Embedded Content`, add `MsakShared.xcframework`.
4. Set embed mode:
   - app target: `Embed & Sign` (recommended for Kotlin/Native dynamic frameworks)
   - test target(s): typically `Do Not Embed` (link only)
5. Usually no extra search paths are needed if you added the framework directly in Xcode.
   - If needed, set `FRAMEWORK_SEARCH_PATHS` to include: `$(PROJECT_DIR)/Frameworks`

### Verification Checklist

Android/KMP:
1. Confirm artifact exists in Maven local:
   - `~/.m2/repository/edu/gatech/cc/cellwatch/msak-client-kmp/0.2.2/`
2. Run dependency insight:
   - `./gradlew -q :shared:dependencies --configuration jvmCompileClasspath | grep msak-client-kmp`
3. Run a fast compile/test task:
   - `./gradlew :shared:jvmTest`

iOS:
1. Verify installed framework slices:
   - `x86_64` and/or `arm64` simulator slice present for your simulator
2. Build app target in Xcode for iOS Simulator
3. Run hosted tests that touch the shared path
4. If launch fails with missing framework, re-check `Embed & Sign` on app target

### Deterministic Local Refresh Script

Use:

```bash
scripts/refresh-local-msak-xcframework.sh
```

Optional args/env:

```bash
# version argument
scripts/refresh-local-msak-xcframework.sh 0.2.2

# custom producer/dist root and destination
MSAK_DIST_ROOT=/Users/jeff/Projects/msak-android/msak-shared/build/local-dist/apple \
MSAK_FRAMEWORK_DEST=/Users/jeff/Projects/cellwatch-app/iosTestApp/Frameworks/MsakShared.xcframework \
scripts/refresh-local-msak-xcframework.sh 0.2.2
```

The script verifies SHA-256 against the sidecar file and atomically replaces the destination framework.

## Testing

The project now uses a practical two-tier test strategy.

### Tier 1: Lightweight (fast, default)

Runs shared/unit-style checks:
- Android unit tests (Robolectric)
- JVM tests
- Kotlin/Native iOS simulator tests

Command:

```bash
./gradlew :shared:verifyLightweightPlatforms
```

### Tier 2: Realistic platform integration

Runs runtime-dependent tests:
- Android instrumentation tests on connected emulator/device (real framework/Keystore path)
- iOS hosted XCTest integration tests (real app host + Keychain path)

Command:

```bash
./gradlew :shared:verifyRealisticPlatforms
```

If run separately:
- Android realistic only: `./gradlew :shared:verifyAndroidEmulator`
- iOS realistic (new parity host) only: `./gradlew :shared:verifyIosTestAppHosted`
- iOS local-MSAK hosted smoke only: `./gradlew :shared:verifyIosTestAppHostedLocalMsakSmoke`
  - Runs only `LocalMsakPhase3HostedTests` and creates a temporary marker file for explicit opt-in.
  - Strict by default: local `msak-server` runtime issues fail the test.
  - Optional transient bypass: set `CELLWATCH_ALLOW_LOCAL_MSAK_TRANSIENT_SKIP=1` to allow skip on known local infra/runtime transients.
- Android public-MSAK/local-Supabase smoke only: `./gradlew :shared:verifyAndroidPublicMsakLocalSupabaseSmoke`
  - Runs only `PublicMsakLocalSupabaseSmokeTest` with explicit env gating.
- iOS public-MSAK/local-Supabase hosted smoke only: `./gradlew :shared:verifyIosTestAppHostedPublicMsakLocalSupabaseSmoke`
  - Runs only `PublicMsakLocalSupabaseHostedTests` and creates a temporary marker file for explicit opt-in.
- iOS hosted Tier 2 sequential bundle (recommended): `./gradlew :shared:verifyIosHostedTier2Sequential`
  - Serializes hosted iOS checks (including local/public MSAK smoke paths) to reduce simulator/keychain/local-service concurrency flake.
- Simulator UI regression gate (strict success on both platforms):
  - Gradle task: `./gradlew :shared:verifySimulatorUiRegressionSequence`
  - Script wrapper: `./scripts/verify-simulator-regression.sh`
  - Runs Android emulator + iOS simulator Phase3 button smoke tests and fails if either reports `status=FAILURE`.
- iOS realistic (legacy host) only: `./gradlew :shared:verifyIosHostedKeychain`
- Local Supabase JVM integration only: `./gradlew :shared:verifyLocalSupabaseJvmIntegration`
  - Includes remote adapter RPC/table checks and end-to-end `MeasurementSyncUseCase` store-and-forward validation against local Docker Supabase
- Android isolated harness smoke test: `./gradlew :androidTestApp:testDebugUnitTest --tests "edu.gatech.cc.cellwatch.androidtestapp.LocalSupabaseSharedSyncSmokeTest"`
- Android isolated harness driver + environment tests: `./gradlew :androidTestApp:testDebugUnitTest --tests "edu.gatech.cc.cellwatch.androidtestapp.AndroidTestSyncDriverTest" --tests "edu.gatech.cc.cellwatch.androidtestapp.SupabaseEnvironmentProviderTest"`

### Phase 3 Operator Runbook

Use this sequence for day-to-day migration work and pre-checkin confidence.

Prerequisites:
- Local Supabase stack running (`./scripts/supabase-local.sh start`)
- Docker Desktop running
- iOS simulator available (for hosted iOS tasks)
- Optional local `msak-server` running if you want local-MSAK Tier 2 smoke coverage

Recommended command flow:
1. Fast regression pass (Tier 1):
   - `./gradlew :shared:verifyLightweightPlatforms`
2. Local Supabase integration confidence:
   - `./gradlew :shared:verifyLocalSupabaseJvmIntegration`
3. Full Phase 3 Tier 2 matrix (Android+iOS, local/public MSAK, local Supabase, failure-surface smokes):
   - `./gradlew :shared:verifyPhase3Tier2FailureMatrix`
4. Simulator-perspective regression gate (strict UI success contract on both platforms):
   - `./scripts/verify-simulator-regression.sh`

Recommended pre-check-in gate (single command):
- `./scripts/verify-dev-gates.sh`
  - Runs parity gates (`:shared:verifyParityPipelines`) and Tier 1 (`:shared:verifyLightweightPlatforms`)
  - Exits nonzero if any gate fails, with a compact pass/fail summary

Optional narrower commands:
- iOS hosted sequential suite only:
  - `./gradlew :shared:verifyIosHostedTier2Sequential`
- Android-only Phase 3 smokes:
  - `./gradlew :shared:verifyAndroidLocalMsakPhase3Smoke`
  - `./gradlew :shared:verifyAndroidPublicMsakLocalSupabaseSmoke`
  - `./gradlew :shared:verifyAndroidFailureStatusSmoke`

Expected skip/fail behavior:
- Local-MSAK smoke tests are fail-by-default for runtime issues. Use `CELLWATCH_ALLOW_LOCAL_MSAK_TRANSIENT_SKIP=1` only when explicitly bypassing known local transients.
- Local Supabase smoke tests are fail-by-default for local stack unavailability. Use `CELLWATCH_ALLOW_LOCAL_SUPABASE_UNAVAILABLE_SKIP=1` for explicit developer bypass.
- For nested Gradle/Xcode smoke tasks (especially with long-lived Gradle daemons), you can pass bypass flags via project properties for reliable propagation:
  - `-Pcellwatch.allowLocalMsakTransientSkip=1`
  - `-Pcellwatch.allowLocalSupabaseUnavailableSkip=1`
- Failure-status smoke tests must pass only when failure is surfaced cleanly (error returned, no crash).
- Simulator Phase3 button smokes are strict-success gates:
  - both Android and iOS tests now require `status=SUCCESS` and fail on any `status=FAILURE` envelope.
- Any hard test failure (assertion/process exit) should block check-in.

Supabase safety policy:
- Keep runtime on local Supabase for all normal dev and tests.
- Remote Supabase is blocked by default and requires explicit opt-in (`CELLWATCH_ALLOW_REMOTE_SUPABASE=true`).

### Harness Parity Matrix

- `map-start sync` action:
  - Android: `androidTestApp` UI (`MainActivity`) + `AndroidTestSyncDriverTest`
  - iOS: `iosTestApp` UI (`HarnessViewController`) + `SyncHarnessParityTests`
- `measurement-complete sync` action:
  - Android: `androidTestApp` UI (`MainActivity`) + `LegacySharedSyncFlowTest`
  - iOS: `iosTestApp` UI simulation (`HarnessViewController`) + `SyncHarnessParityTests`
- secure storage/encryption host validation:
  - Android: shared Android unit + instrumentation suites
  - iOS: `iosTestApp` hosted `KeychainIntegrationTests` and legacy `iosSharedIntegrationHost` hosted tests
- local Supabase guardrails:
  - Android: `SupabaseEnvironmentProviderTest` and local-only `LocalSupabaseSharedSyncSmokeTest`
  - iOS: `SyncHarnessParityTests` local/remote environment provider checks and hosted local Supabase sync end-to-end
  - Both harnesses now resolve runtime Supabase config through shared `SyncRuntimeConfig` (`allowRemote=false` by default)
  - Both harnesses validate shared upload-trigger entrypoints (`onMapStart` + `onMeasurementComplete`) against local Supabase
- machine-readable parity artifact:
  - Shared contract: `UploadTriggerParityArtifact` (`shared/src/commonMain/.../UploadTriggerParityArtifact.kt`)
  - Android and iOS parity tests emit `uploadTriggerParityArtifact=<json>` for the default upload-trigger scenario.
  - Shared checker test `UploadTriggerParityArtifactTest` validates schema version and key-field parity across Android/iOS artifacts.
  - Consolidated report task:
    - `./gradlew :shared:collectUploadTriggerParityArtifacts`
    - writes `build/reports/parity/upload-trigger-parity-artifacts.txt` with Android + iOS artifact JSON lines.
  - Parity gate task:
    - `./gradlew :shared:verifyUploadTriggerParityArtifacts`
    - runs artifact collection, then fails if Android/iOS key parity fields diverge.
  - Phase 3 sequence parity artifact:
    - `./gradlew :shared:collectPhase3SequenceParityArtifacts`
    - writes `build/reports/parity/phase3-sequence-parity-artifacts.txt` from Android+iOS local-MSAK smoke paths.
  - Phase 3 sequence parity gate:
    - `./gradlew :shared:verifyPhase3SequenceParityArtifacts`
    - runs phase3 artifact collection, then fails if persisted/capability key fields diverge or machine IDs are blank.
  - Recommended umbrella parity pre-check-in command:
    - `./gradlew :shared:verifyParityPipelines`
    - runs both upload-trigger and phase3-sequence parity gates.
- shared runtime profile contract:
  - `RuntimeSyncMsakProfiles.fromModes(...)` defines the shared MSAK+Supabase mode contract.
  - `RuntimeSyncMsakProfiles.publicMsakLocalSupabase(...)` remains as the convenience profile for:
    - MSAK target = public/prod
    - Supabase target = local-only
  - Used by Android/iOS opt-in Tier 2 smoke tests to prevent accidental remote Supabase writes while exercising public MSAK paths.
  - Also used by harness app runtime wiring:
    - `/Users/jeff/Projects/cellwatch-app/androidTestApp/src/main/java/edu/gatech/cc/cellwatch/androidtestapp/MainActivity.kt`
    - `/Users/jeff/Projects/cellwatch-app/iosTestApp/App/AppDelegate.swift`
    - both now resolve MSAK + Supabase runtime selection via shared profile bridge/config, not ad-hoc per-app constants.
- shared Phase 3 + sync canonical path:
  - `MeasurementSequenceSyncOrchestrator` now defines the single shared flow for harness apps:
    - map-start sync
    - full MSAK sequence orchestration
    - measurement-complete sync on resulting group
  - `RepositoryBackedMeasurementResultStore` persists sequence artifacts to repositories so sync uploads the same records generated by Phase 3 execution.
  - `androidTestApp` and `iosTestApp` Phase 3 actions now use this shared combined path.

## Recent KMP Porting Work

### Encryption and secure storage

- Shared encryptor implemented in `shared/src/commonMain/.../Encryptor.kt`
  - AES-256-GCM
  - Versioned envelope format (`cw1:`)
  - Optional associated data support
- Platform secure key abstraction implemented in `SecureKeyStore` (`expect/actual`)
  - Android actual: Keystore-backed wrapping/unwrapping of shared key material
  - iOS actual: Keychain-backed storage
  - JVM actual: in-memory implementation for host/JVM tests
- Integration wrapper (`SecureEncryptor`) uses `SecureKeyStore` + shared `Encryptor`

### SQLDelight and cross-platform test contracts

- Shared SQLDelight contracts continue to run across Android/JVM/iOS test perspectives
- Verification tasks were tightened so iOS simulator test tasks fail when no tests are actually executed

## Key Design Decisions (Keep These)

- Shared crypto algorithm and envelope are platform-consistent (same contract everywhere)
- Secure key material lifecycle is platform-specific behind a shared interface (`SecureKeyStore`)
- Tiered testing is intentional:
  - Tier 1 optimizes speed and broad regression coverage
  - Tier 2 validates runtime-specific behavior where unit tests are not representative
- iOS Keychain validation requires a hosted app test context; K/N-only simulator tests are insufficient for Keychain confidence
- Shared sync runtime configuration is local-only by default and blocks remote targets unless explicitly enabled
- `:shared:verifyIosTestAppHosted` now refreshes `shared/build/bin/Current/sharedKit.framework` before running Xcode-hosted tests

## Logging

The app uses a custom logging wrapper that delegates to Firebase Crashlytics and Android local logs.

Use log levels intentionally:
- `Log.e` / `Log.w`: unexpected behavior likely indicating bugs (appears prominently in Crashlytics)
- `Log.i`: contextual breadcrumbs for later debugging
- `Log.d` / `Log.v`: local debugging only

## Local MSAK Server (Phase 3 Testing)

CellWatch shared Phase 3 harness can target `MsakLocateEnvironment.LOCAL` and `MSAK_LOCAL_SERVER_HOST` for local end-to-end measurement testing.

### Preferred: shared helper script (`scripts/msak-local.sh`)

Use the repo helper so all developers run the same Dockerized local server path:

```bash
./scripts/msak-local.sh start
./scripts/msak-local.sh status
./scripts/msak-local.sh smoke
./scripts/msak-local.sh logs
./scripts/msak-local.sh stop
```

Defaults:
- Expects a local `msak` checkout at `../msak` relative to this repo root
- Runs Docker container `msak-local` from image `msak-local:dev`
- Maps `8080/tcp` and `1053/udp`
- Mounts `<msak checkout>/data` into the container for persisted local output

Override paths/ports as needed:

```bash
CELLWATCH_MSAK_REPO_DIR=/absolute/path/to/msak \
CELLWATCH_MSAK_WS_PORT=8080 \
CELLWATCH_MSAK_UDP_PORT=1053 \
./scripts/msak-local.sh rebuild
```

### Manual run from local source build

```bash
git clone https://github.com/m-lab/msak.git
cd msak
go build ./cmd/msak-server
./msak-server
```

### Run server from Docker (repo-provided Dockerfile)

```bash
git clone https://github.com/m-lab/msak.git
cd msak
docker build -t mlab-msak-local .
docker run --rm -p 8080:8080 -p 1053:1053/udp mlab-msak-local
```

Then set local target in `/Users/jeff/Projects/cellwatch-app/cellwatch.properties`:

```properties
MSAK_LOCAL_SERVER_HOST="10.0.2.2:8080"
MSAK_LOCAL_SERVER_SECURE=false
```

And run the optional Android Tier 2 smoke:

```bash
CELLWATCH_RUN_LOCAL_MSAK_SMOKE=1 ./gradlew :androidTestApp:testDebugUnitTest --tests "edu.gatech.cc.cellwatch.androidtestapp.LocalMsakPhase3SequenceSmokeTest"
```

## Architecture Notes

Historical Clean Architecture notes were moved out of the root README:
- `doc/CLEAN_ARCHITECTURE_NOTES.md`

## Porting Gap Snapshot (`frozenApp/` -> `shared/`)

Already ported in `shared/`:
- Core models and mappers (`Cell`, `Device`, `ChallengeData`, `Measurement`, `Location`, `LatencyData`, `UploadDownloadData`, `FccSubmission`)
- SQLDelight schema + repository implementations for those entities
- Shared encryption + secure key storage abstraction (`SecureKeyStore`) with Android/iOS/JVM actuals

Not yet ported (still Android-only in `frozenApp/`):
- Measurement pipeline and FCC flow (`domain/fcc/*`)
- Network datasource stack for measurements/submissions
- Telephony and map managers
- Android UI/viewmodels/navigation

## Progressive KMP Porting Plan

### Phase 1: Data model and storage parity
- Status: completed on February 11, 2026
- Delivered:
  - Shared domain model parity for `Measurement`, `Location`, `LatencyData`, `UploadDownloadData`, `FccSubmission`, `ChallengeData` and supporting types
  - SQLDelight schema + repository interfaces/implementations for these entities
  - Cross-platform query contracts and mapper round-trip tests (Android unit, JVM, iOS simulator via Tier 1)
  - Cross-platform Phase 1 integration-style data-flow contract (`Phase1DataFlowContract`) validating:
    - persistence and hydration of a full measurement group (latency/download/upload + child tables + FCC submission)
    - `MeasurementGroup` reconstruction invariants
    - foreign-key cascade behavior for measurement child records
- Tier 1 tests:
  - `./gradlew :shared:verifyLightweightPlatforms`
- Tier 2 tests:
  - Not required yet unless secure-storage/encryption behavior changes

### Phase 2: Shared network and submission contracts
- Status: in progress
- Delivered (February 11, 2026):
  - Shared sync contracts and orchestration use case in `shared/domain/sync` (`MeasurementSyncUseCase`, `SyncReport`, remote/local/TCP tuple interfaces)
  - Shared remote transport profile boundary in `shared/data/sync`:
    - `SyncRemoteProfile` (currently `Supabase`)
    - `SyncRemoteDataSourceFactory` + `DefaultSyncRemoteDataSourceFactory`
    - generic `MeasurementSyncServiceFactory.createUploadTriggerUseCase(...)` that consumes profile + factory
  - Shared network transport models in `shared/data/transport` for measurement and FCC submission payloads:
    - `NetworkMeasurement`, `NetworkMeasurementWithData`, `NetworkLatencyData`, `NetworkUploadDownloadData`, `NetworkLocation`, `NetworkCell`, `NetworkFccSubmission`
  - Shared repository-backed sync local-store adapter in `shared/data/sync/RepositoryBackedMeasurementSyncLocalStore.kt`
  - SQLDelight/repository support for upload synchronization state:
    - `Measurement`: `selectUnsyncedMeasurements`, `markMeasurementUploaded`
    - `FccSubmission`: `selectUnsyncedFccSubmissions`, `markFccSubmissionUploaded`
  - Supabase-backed shared remote datasource implementation in `shared/data/remote/SupabaseMeasurementSyncRemoteDataSource.kt`
  - Local Supabase shared integration tests in `shared/jvmTest` (RPC + store-and-forward coverage)
  - Isolated Android bridge and driver in `androidTestApp` for legacy trigger modeling:
    - `UploadTriggerUseCase` extracted in `shared/domain/sync/UploadTriggerUseCase.kt`
    - `LegacySharedSyncFlow` now delegates to shared `UploadTriggerUseCase` for compatibility
    - `AndroidTestSyncDriver` stateful wrapper for action/report/error flow
    - `AndroidTestSyncDriverFactory` with environment-target wiring
    - shared transport-boundary wiring via `SyncSupabaseConfigResolver` + `SyncTransportTarget` for explicit local/remote transport selection
  - Shared parity scenario harness in `shared/domain/sync/UploadTriggerParityHarness.kt`
    - single default scenario used by Android and iOS hosted tests to assert equal report/upload-time contract
  - Shared `UploadTriggerUseCase` contract tests in `shared/src/commonTest/.../UploadTriggerUseCaseTest.kt` covering:
    - map-start sync delegation
    - measurement-complete sync + upload-time resolution semantics
    - upload-time edge cases for pending records and measurement-id fallback ordering
  - Shared FCC submission policy extraction in `shared/domain/fcc/FccSubmissionPolicy.kt`:
    - eligibility contract ported from legacy `MeasurementManager` (`FCC_CHALLENGE` mode + non-WIFI + cellular-data-not-false)
    - deterministic metadata fallback aggregation from latency/download/upload measurements for submission construction
    - shared FCC submission builder (`buildSubmission`) for deterministic field assembly from policy metadata + runtime context
    - cross-platform contract tests in `shared/src/commonTest/.../FccSubmissionPolicyTest.kt`
  - Shared server-selection contract extraction in `shared/domain/fcc/MsakServerSelector.kt`:
    - ports legacy `chooseMsakServers` behavior (throughput selection by host probe, latency same-machine preference/fallback-first, network-unavailable fallback pair)
    - keeps shared logic adapter-agnostic via `MsakServerLocator` and `MsakHostPinger` interfaces
    - includes unreachable endpoint synthesis equivalent to legacy `UnreachableServer` URL mapping
    - cross-platform contract tests in `shared/src/commonTest/.../MsakServerSelectorTest.kt`
  - Shared measurement-result policy extraction in `shared/domain/fcc/MeasurementResultPolicy.kt`:
    - ports legacy FCC success rules for latency/throughput result acceptance
    - ports generation-stability override (network generation changes force unsuccessful result)
    - cross-platform contract tests in `shared/src/commonTest/.../MeasurementResultPolicyTest.kt`
  - `androidTestApp` failure-path tests for:
    - network-down style sync failure handling
    - tuple-blocked submission report handling
    - duplicate-key/partial-success report propagation
  - `androidTestApp` Supabase environment guard tests for local defaults + remote blocking
  - Isolated iOS parity harness app in `iosTestApp`:
    - UI harness actions for local env resolve + map-start/measurement-complete shared-slice calls
    - hosted tests for Keychain integration and shared upload-trigger parity behavior
- Remaining:
  - Continue porting legacy behavior from `frozenApp/` into `shared/` and harness modules only
- Tier 1 tests:
  - Contract tests for network mapping + error handling in `commonTest`
  - Existing lightweight platform suite
  - `androidTestApp` Robolectric driver/environment tests
- Tier 2 tests:
  - Local Supabase smoke integration via `androidTestApp` and `shared` local integration tasks
  - iOS hosted parity harness tests via `:shared:verifyIosTestAppHosted`

### Immediate Next Slice
- Phase 4 hardening:
  - Add hosted verification (Android + iOS harness paths) that capability support states and notes persist through full measurement runs.
  - Extend local Supabase inspection/report checks to surface persisted capability fields so "platform-expected missing data" is distinguishable from execution failures.
- Migration strategy:
  - Keep shared-first logic and validate parity in `androidTestApp` and `iosTestApp` before wiring into future product app modules.

### Deferred Coordination TODOs
- Coordinate Supabase migration-history reconciliation with main branch before tracking live-compatible migrations in-repo

### Phase 3: Measurement engine extraction
- Status: started
- Delivered (initial slice, February 12, 2026):
  - Shared orchestration contract in `shared/domain/fcc/MeasurementSequenceOrchestrator.kt`:
    - deterministic sequence skeleton (server-pair select -> latency -> download -> upload -> optional FCC submission build/persist)
    - platform-facing seams via interfaces (`MsakServerPairProvider`, `MeasurementExecutor`, `MeasurementResultStore`, `FccSubmissionContextFactory`)
  - Cross-platform contract tests in `shared/src/commonTest/.../MeasurementSequenceOrchestratorTest.kt` validating:
    - call order and persistence sequencing
    - conditional FCC submission creation via shared policy
    - measurement-id pass-through to execution adapters
  - Shared harness runner `shared/domain/fcc/MeasurementSequenceHarness.kt` and app wiring:
    - `androidTestApp` action: "Run Phase3 Sequence (Shared Orchestrator)"
    - `iosTestApp` action: "Run Phase3 Sequence (Shared Orchestrator)"
    - executes shared orchestrator with platform server selection + real `msak-client-kmp` measurement execution adapters
  - Tier 2 local-MSAK smoke scaffold in `androidTestApp`:
    - `LocalMsakPhase3SequenceSmokeTest` (gated by `CELLWATCH_RUN_LOCAL_MSAK_SMOKE=1`)
    - uses `MsakLocateEnvironment.LOCAL` + `MSAK_LOCAL_SERVER_HOST` from `cellwatch.properties`
- Split `domain/fcc` into:
  - Pure shared logic (metrics aggregation, challenge orchestration, payload assembly)
  - Platform adapters for network sockets/timing/device signals
- Define expect/actual seams for platform-specific primitives
- Tier 1 tests:
  - Deterministic shared engine tests in `commonTest`
- Tier 2 tests:
  - Android instrumented smoke test for real runtime behavior
  - iOS hosted integration smoke test via `iosTestApp`
  - Optional local MSAK smoke (Android Robolectric):
    - `CELLWATCH_RUN_LOCAL_MSAK_SMOKE=1 ./gradlew :androidTestApp:testDebugUnitTest --tests "edu.gatech.cc.cellwatch.androidtestapp.LocalMsakPhase3SequenceSmokeTest"`

### Phase 4: Platform capability adapters
- Introduce shared interfaces for telephony/map/device capability reads
- Keep implementations in app layers:
  - Android actuals in `shared/androidMain` or Android app module
  - iOS actuals in iOS app target
- iOS capability policy is best-effort by design:
  - Some telephony/network/location fields available on Android will be unavailable, permission-gated, or unsupported on iOS.
  - Shared contracts must represent that explicitly (support state + nullable fields) instead of treating missing values as hard failures.
  - Reporting/upload payload assembly should proceed with partial capability snapshots when required fields are unavailable on iOS.
- Current delivered slice (February 12, 2026):
  - Shared capability contract models + provider seam in `shared/domain/capability/PlatformCapabilitySnapshot.kt`
  - Android best-effort provider in `shared/src/androidMain/.../AndroidPlatformCapabilityProvider.kt` (permission-aware telephony + connectivity + device snapshot)
  - iOS best-effort provider in `shared/src/iosMain/.../IosPlatformCapabilityProvider.kt` (device metadata + explicit partial/unsupported signaling)
  - Contract tests in `shared/src/commonTest`, plus Android/iOS platform tests in `shared/src/androidUnitTest` and `shared/src/iosTest`
  - MSAK measurement executors now enrich every produced measurement via `MeasurementCapabilityEnricher`, and Android/iOS test harnesses inject concrete platform providers.
  - Android/iOS Phase 3 harness outputs now include a shared formatted capability capture summary (support states + notes) so best-effort gaps are explicit in smoke runs.
  - Capability support states and notes are now persisted in local measurement storage (`telephonySupport`, `networkSupport`, `locationSupport`, `deviceSupport`, `capabilityNotes`) so downstream analysis can distinguish "not available by platform/policy" from "missing due to runtime failure."
  - Parity guard: `PlatformCapabilityParityReportTest` compares normalized enriched measurement fields for Android-like vs iOS-like harness snapshots. Contract:
    - both platforms must persist support-state fields and device identity
    - iOS is explicitly allowed to have missing provider/SIM identifiers/network connection type due to best-effort platform limits
    - both platforms must persist capability notes for operator visibility

### Android vs iPhone measurement data (current shared contract)
- Android (typically richer):
  - Telephony identity and radio fields are often available when permissions are granted (`provider`, `simMcc/simMnc`, `netMcc/netMnc`, cell list).
  - Connectivity and device metadata are usually available in normal app/runtime contexts.
- iPhone (best-effort/partial by design):
  - Telephony/radio details are frequently unavailable or restricted; capability is recorded as `PARTIAL`, `PERMISSION_DENIED`, or `NOT_SUPPORTED` with notes.
  - Device metadata is available; network/location may be partial depending on host/runtime permissions and APIs.
- Cross-platform interpretation rule:
  - Absence of a raw field should not be treated as a hard failure by itself.
  - Use persisted capability support fields + `capabilityNotes` to interpret whether missing measurement values are expected platform behavior.
- Tier 1 tests:
  - Contract tests with fakes in `commonTest`
- Tier 2 tests:
  - Android instrumentation for telephony-backed paths
  - iOS hosted tests for best-effort capability signaling and persisted support-state coverage

### Phase 5: App-layer convergence
- Planning tracker: `doc/PHASE5_USER_STORY_PLAN.md`
- Architecture contract: `doc/APP_LAYER_ARCHITECTURE_CONTRACT.md`
- Build replacement Android/iOS product app layers that consume shared repositories/use-cases
- Stand up iOS app UI against the same shared APIs
- Retire frozen Android-only logic as each slice is migrated into shared
- Tier 1 tests:
  - Shared regression suite + Android unit/UI unit tests
- Tier 2 tests:
  - Android instrumentation + iOS hosted integration checks in CI/release gates
