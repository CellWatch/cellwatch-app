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
- `ios-onboarding-profile-entry-hosted`
- `ios-onboarding-profile-entry-xcuitest`

iOS test-runner note:
- `2A` hosted invariant smoke remains in `iosTestAppTests` (in-process).
- `2B` onboarding UI evidence runs in `iosTestAppUITests` (XCUITest, out-of-process) for trustworthy simulator-frame UI interactions/snapshots.
- This split is intentional to avoid overloading hosted XCTest with UI-automation duties it is not designed for.
- `2A` hosted onboarding smoke is assertion-first and does not publish screenshot evidence by default.

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

Story 1 hardening:
- Implement onboarding persistence use case (save/load/edit profile).
- Wire persisted profile into both harness apps.
- Add simulator UI smoke proving:
  - submit persists
  - relaunch/reopen pre-fills fields
  - edit/save updates persisted values

Keep constraints:
- Do not add feature logic to `frozenApp/`.
- Keep shared-first business rules in `shared/`.
- Keep simulator UI report artifacts and sections updated when adding flows.
