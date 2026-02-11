# CellWatch

CellWatch is a cellular signal quality measurement app used to collect data for FCC cellular quality challenge workflows.

This repository currently contains:
- A production Android app (`app/`)
- A Kotlin Multiplatform shared module (`shared/`) under active migration
- An iOS host integration test app (`iosSharedIntegrationHost/`) used to validate platform-specific behavior such as Keychain access

## Current Project Status

The project is mid-migration from Android-only Kotlin to KMP:
- Shared domain/data logic and SQLDelight testing are active in `shared/`
- Shared encryption and secure key storage abstractions are implemented and tested
- iOS product app UI is not yet implemented here; iOS currently uses a host test app for realistic integration testing

## Repo Structure

- `app/`: Android application (legacy + active production app code)
- `shared/`: KMP shared module (common/domain/data/util, SQLDelight schema and tests)
- `iosSharedIntegrationHost/`: Minimal iOS app + XCTest target for hosted integration tests against `sharedKit.framework`
- `doc/`: Supporting documentation (including architecture notes)

## Prerequisites

- Java 17 (project has been standardized on JDK 17)
- Android SDK + emulator tooling (`adb`, `emulator`)
- Xcode + iOS Simulator (for hosted iOS integration tests)
- Docker Desktop (required for local Supabase stack)
- Supabase CLI (`supabase`)

## Local Supabase Safety

Local development and tests are now guarded to use local Supabase only for debug/test builds.

- `app/build.gradle.kts` enforces `SUPABASE_LOCAL_URL` host to be local (`localhost`, `127.0.0.1`, `::1`, `10.0.2.2`, `host.docker.internal`)
- If `SUPABASE_LOCAL_URL` points to a cloud host, debug/test builds fail fast
- Current local defaults in `cellwatch.properties`:
  - `SUPABASE_LOCAL_URL="http://10.0.2.2:54321"`
  - local Supabase anon key (CLI default)

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
- iOS realistic only: `./gradlew :shared:verifyIosHostedKeychain`

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

## Logging

The app uses a custom logging wrapper that delegates to Firebase Crashlytics and Android local logs.

Use log levels intentionally:
- `Log.e` / `Log.w`: unexpected behavior likely indicating bugs (appears prominently in Crashlytics)
- `Log.i`: contextual breadcrumbs for later debugging
- `Log.d` / `Log.v`: local debugging only

## Android msak Implementation

### Testing with a local server

By default, the app uses an M-Lab server. To run locally:

```bash
git clone https://github.com/m-lab/msak.git
git checkout sandbox-roberto-server
go build ./cmd/msak-server
./msak-server
```

Then in `MeasurementFragment.kt`, follow the existing comments for local-server toggle.

## Architecture Notes

Historical Clean Architecture notes were moved out of the root README:
- `doc/CLEAN_ARCHITECTURE_NOTES.md`

## Porting Gap Snapshot (`app/` -> `shared/`)

Already ported in `shared/`:
- Core models and mappers (`Cell`, `Device`, `ChallengeData`, `Measurement`, `Location`, `LatencyData`, `UploadDownloadData`, `FccSubmission`)
- SQLDelight schema + repository implementations for those entities
- Shared encryption + secure key storage abstraction (`SecureKeyStore`) with Android/iOS/JVM actuals

Not yet ported (still Android-only in `app/`):
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
  - Shared network transport models in `shared/data/transport` for measurement and FCC submission payloads:
    - `NetworkMeasurement`, `NetworkMeasurementWithData`, `NetworkLatencyData`, `NetworkUploadDownloadData`, `NetworkLocation`, `NetworkCell`, `NetworkFccSubmission`
  - Shared repository-backed sync local-store adapter in `shared/data/sync/RepositoryBackedMeasurementSyncLocalStore.kt`
  - SQLDelight/repository support for upload synchronization state:
    - `Measurement`: `selectUnsyncedMeasurements`, `markMeasurementUploaded`
    - `FccSubmission`: `selectUnsyncedFccSubmissions`, `markFccSubmissionUploaded`
- Remaining:
  - Implement concrete shared remote datasource adapter for Supabase in `shared/`
  - Add local-Supabase integration tests for remote adapter
- Current blocker:
  - Repository does not yet contain Supabase schema/migration files for measurements/submissions RPC contract, so local Supabase integration coverage cannot be made deterministic yet from this repo alone.
- Tier 1 tests:
  - Contract tests for network mapping + error handling in `commonTest`
  - Existing lightweight platform suite
- Tier 2 tests:
  - Optional smoke integration against test endpoint if available

### Phase 3: Measurement engine extraction
- Split `domain/fcc` into:
  - Pure shared logic (metrics aggregation, challenge orchestration, payload assembly)
  - Platform adapters for network sockets/timing/device signals
- Define expect/actual seams for platform-specific primitives
- Tier 1 tests:
  - Deterministic shared engine tests in `commonTest`
- Tier 2 tests:
  - Android instrumented smoke test for real runtime behavior
  - iOS hosted integration smoke test when iOS adapter is added

### Phase 4: Platform capability adapters
- Introduce shared interfaces for telephony/map/device capability reads
- Keep implementations in app layers:
  - Android actuals in `shared/androidMain` or Android app module
  - iOS actuals in iOS app target
- Tier 1 tests:
  - Contract tests with fakes in `commonTest`
- Tier 2 tests:
  - Android instrumentation for telephony-backed paths
  - iOS hosted tests for CoreTelephony-backed paths (when wired)

### Phase 5: App-layer convergence
- Keep Android UI in `app/` but consume shared repositories/use-cases
- Stand up iOS app UI against the same shared APIs
- Remove duplicated business logic from Android-only layer as each slice is migrated
- Tier 1 tests:
  - Shared regression suite + Android unit/UI unit tests
- Tier 2 tests:
  - Android instrumentation + iOS hosted integration checks in CI/release gates
