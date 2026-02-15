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
- Move from harness-only story slices toward product app-layer integration for Story 5+ (history/status/retry UX).
- Keep shared-first use-case ownership in `shared/`; keep platform modules as thin adapters + viewmodels.
- Expand simulator UI evidence as new user-facing cards/screens are added (without reintroducing debug-panel UI into `ui-flow` captures).

Keep constraints:
- Do not add feature logic to `frozenApp/`.
- Keep shared-first business rules in `shared/`.
- Keep simulator UI report artifacts and sections updated when adding flows.
