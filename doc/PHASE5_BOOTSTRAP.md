# Phase 5 Bootstrap (Agent/Human)

Use this file as a quick start when opening a new conversation thread.

## Read First

1. `README.md`
2. `doc/PHASE5_USER_STORY_PLAN.md`
3. `doc/APP_LAYER_ARCHITECTURE_CONTRACT.md`
4. `doc/IOS_APP_BOOTSTRAP_CHECKLIST.md` (for iOS app/simulator setup gotchas)

## Current State

- Active migration surfaces:
  - `shared/`
  - `androidTestApp/`
  - `iosTestApp/`
- Legacy reference-only:
  - `frozenApp/`
- Shared onboarding validation exists:
  - `shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/onboarding/OnboardingValidationUseCase.kt`
- Runtime onboarding draft contract exists:
  - `shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/runtime/RuntimeOnboardingContract.kt`
- Stepwise onboarding and phase3 simulator UI evidence exists for both platforms.

## Verify Current Health

Run onboarding UI evidence:

```bash
./scripts/generate-ui-flow-report.sh
```

Inspect:

- `build/reports/ui-flow/UI_FLOW_REPORT.md`
- `build/reports/ui-flow/logs/*.status`
- `build/reports/ui-flow/logs/*.log`

Expected flow keys:

- `android-onboarding-profile-entry`
- `android-measurement-start-preflight`
- `android-pending-sync-retry`
- `android-measurement-run-flow`
- `ios-onboarding-profile-entry-hosted`
- `ios-onboarding-profile-entry-xcuitest`
- `ios-measurement-start-preflight-xcuitest`
- `ios-pending-sync-retry-xcuitest`
- `ios-measurement-run-flow-xcuitest`

iOS test-runner note:
- `2A` hosted invariant smoke remains in `iosTestAppTests` (in-process).
- `2B` user-facing UI-flow evidence runs in `iosTestAppUITests` (XCUITest, out-of-process) for trustworthy simulator-frame UI interactions/snapshots.
- This split is intentional to avoid overloading hosted XCTest with UI-automation duties it is not designed for.
- `2A` hosted smoke is assertion-first and does not publish screenshot evidence by default.
- UI-flow pass/fail comes from deterministic assertions (state/control checks); screenshots are human-review evidence and are not auto-diffed.

iOS fullscreen + interaction guardrails:
- Keep `UILaunchScreen` declared in `iosTestApp/App/Info.plist` to prevent legacy `320x480` compatibility launch mode.
- In XCUITest, use explicit `waitForExistence` + targeted element taps/typing and dismiss keyboard before lower-screen actions.

Mapbox Android emulator guardrail:
- A black map panel with Mapbox attribution visible is often emulator DNS/host-network failure, not app-render logic.
- Validate emulator hostname resolution (`adb shell ping api.mapbox.com`) before debugging map UI code.
- Preferred runtime key is `MAPBOX_ACCESS_TOKEN` (`pk...`); current migration keeps a temporary fallback to `MAPBOX_DOWNLOADS_TOKEN` (`sk...`) when access token is unavailable.
- References:
  - https://developer.android.com/studio/run/emulator-networking
  - https://developer.android.com/studio/run/emulator-commandline
  - https://docs.mapbox.com/help/dive-deeper/access-tokens/

iOS Mapbox token distribution guardrail:
- Xcode prebuild writes bundle resource `cellwatch.runtime.properties` from local property sources.
- Runtime reads token from bundled resource (not generated Swift source).
- Hosted XCTest gate:
  - `HarnessUiSmokeTests.testMapboxTokenDistribution_matchesCellwatchProperties`
  - `HarnessUiSmokeTests.testMapHomeBuildPath_doesNotRenderTokenMissingState`

iOS hosted-testing Release defaults:
- `iosTestApp` `Debug` defaults to `MSAK=LOCAL` and `Supabase=LOCAL`.
- `iosTestApp` `Release` defaults to `MSAK=PUBLIC` and `Supabase=TESTING`.
- `Release` also enables `CELLWATCH_ALLOW_REMOTE_SUPABASE=YES`.
- Provide hosted testing credentials in local property files:
  - `SUPABASE_TESTING_URL`
  - `SUPABASE_TESTING_API_KEY`
- Current TestFlight intent is hosted testing Supabase, not `SUPABASE_URL` / `SUPABASE_API_KEY`.

Run Phase 3 simulator smoke evidence:

```bash
./scripts/generate-simulator-smoke-report.sh
```

Inspect:

- `build/reports/simulator-smoke/SIMULATOR_SMOKE_REPORT.md`
- `build/reports/simulator-smoke/logs/*.status`
- `build/reports/simulator-smoke/logs/*.log`

Expected flow keys:

- `android-phase3-sequence-button`
- `ios-phase3-sequence-button`

## Next Recommended Work

Phase 5 continuation:
- Move from the temporary MVP menu shell to map-home app entry:
  - default post-onboarding route should land on map-home (not debug/harness menu)
  - map-home should route to measurement-start, settings, and history
  - keep map-home headline/status/sync copy driven by shared `MapHomeViewController`
  - preserve explicit `CELLWATCH_UI_MODE=*` flow targeting used by UI-flow automation
- Keep expanding product-facing integration while retaining shared-first contracts:
  - history/status/retry UX polish on top of the now-stable shared controllers
  - keep map + export sequencing: Story 12 before Story 11
  - keep Mapbox SDK calls behind thin platform adapters; keep zoom/overlay/selection policy in shared map controllers
  - token hygiene follow-up: runtime map init should move to `MAPBOX_ACCESS_TOKEN` (public/scoped), with `MAPBOX_DOWNLOADS_TOKEN` retained for dependency download access only
- Keep shared-first use-case ownership in `shared/`; keep platform modules as thin adapters + viewmodels.
- Expand simulator UI evidence as new user-facing cards/screens are added (without reintroducing debug-panel UI into `ui-flow` captures).

Keep constraints:
- Do not add feature logic to `frozenApp/`.
- Keep shared-first business rules in `shared/`.
- Keep simulator UI report artifacts and sections updated when adding flows.
