# iosTestApp

Isolated iOS host test app for KMP parity work, mirroring the purpose of `androidTestApp`.

Covers:
- `sharedKit` Keychain-backed secure storage integration
- local/remote Supabase environment guardrail behavior (unit tests)
- hosted local Supabase sync end-to-end integration (`SyncHarnessParityTests.testHostedLocalSupabaseSync_endToEnd`)
- shared upload-trigger parity harness behavior from `shared/` (`UploadTriggerParityHarness`)
- runnable UI harness (`HarnessViewController`) with:
  - local environment resolve action
  - map-start shared-slice action
  - measurement-complete shared-slice action

Run hosted tests:

```bash
./gradlew :shared:verifyIosTestAppHosted
```

Run app build (same Gradle-triggered shared framework script path used by Xcode before simulator deploy):

```bash
xcodebuild \
  -project iosTestApp/iosTestApp.xcodeproj \
  -scheme iosTestApp \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2" \
  build
```

Current build defaults:
- `Debug`:
  - `CELLWATCH_DEFAULT_MSAK_MODE=LOCAL`
  - `CELLWATCH_DEFAULT_SUPABASE_MODE=LOCAL`
  - `CELLWATCH_ALLOW_REMOTE_SUPABASE=NO`
- `Release`:
  - `CELLWATCH_DEFAULT_MSAK_MODE=PUBLIC`
  - `CELLWATCH_DEFAULT_SUPABASE_MODE=TESTING`
  - `CELLWATCH_ALLOW_REMOTE_SUPABASE=YES`

For TestFlight/staged hosted testing, provide local property values for:

```properties
SUPABASE_TESTING_URL=...
SUPABASE_TESTING_API_KEY=...
```

Current staged deployment intent is to use hosted testing Supabase, not `SUPABASE_URL` / `SUPABASE_API_KEY`.

Xcode prebuild now uses `/Users/jeff/Projects/cellwatch-app/scripts/compile-shared-framework-for-xcode.sh`,
which keeps `sharedKit.framework` current at:
- `/Users/jeff/Projects/cellwatch-app/shared/build/bin/Current/sharedKit.framework`

To force composite-source local `msak` during Xcode builds:

```bash
CELLWATCH_USE_LOCAL_MSAK_COMPOSITE=true \
CELLWATCH_LOCAL_MSAK_DIR=/Users/jeff/Projects/msak-android \
xcodebuild \
  -project iosTestApp/iosTestApp.xcodeproj \
  -scheme iosTestApp \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2" \
  test
```

Direct Xcode invocation:

```bash
xcodebuild \
  -project iosTestApp/iosTestApp.xcodeproj \
  -scheme iosTestApp \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2" \
  test
```

## Sync Diagnostics Knobs (iOS Harness)

`iosTestApp` configures shared sync diagnostics at app startup. Values are read from environment first, then `cellwatch.properties`.

- `CELLWATCH_SYNC_DIAGNOSTICS_LEVEL=OFF|BASIC|VERBOSE` (default in iOS harness: `VERBOSE`)
- `CELLWATCH_SYNC_DIAGNOSTICS_MAX_SAMPLES=<int>` (default in iOS harness: `12`)
- `CELLWATCH_SYNC_DIAGNOSTICS_INCLUDE_CAUSE_CHAIN=true|false` (default in iOS harness: `true`)

The resolved diagnostics config is included in iOS status/log lines (prefix `[iosTestApp]`) for faster triage of simulator button-flow failures.

## iOS UI Harness Tips (Keep For Future Projects)

- Fullscreen requirement:
  - Keep `UILaunchScreen` declared in `App/Info.plist`.
  - Missing launch-screen declaration can force legacy `320x480` compatibility mode (letterboxed simulator/app UI).
- Onboarding UI mode:
  - `CELLWATCH_UI_MODE=onboarding-flow` for onboarding-only layout (no split harness/debug panel).
  - SwiftUI is the default app path; UIKit onboarding path is retained for hosted in-process invariants only.
- Keyboard handling for UI tests:
  - Prefer field `waitForExistence` then targeted `tap`/`typeText`.
  - Before tapping lower controls (switch/save), hide keyboard via `Done` button if present, else fallback tap on a safe background coordinate.
- Evidence strategy:
  - Use `iosTestAppUITests` (XCUITest) for product-like screenshots.
  - Keep `iosTestAppTests` hosted tests for in-process invariants and status-text assertions.
  - Phase3 button smoke in simulator reports now runs through XCUITest tap flow (`OnboardingFlowUiTests.testPhase3SequenceButton_flowRunsWithTrueUiTap`) and writes screenshots to `/tmp/cellwatch-ui-flow/ios/phase3-sequence-button`.
