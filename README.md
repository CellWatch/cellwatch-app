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
