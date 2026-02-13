# Phase 5 Bootstrap (Agent/Human)

Use this file as a quick start when opening a new conversation thread.

## Read First

1. `README.md`
2. `doc/PHASE5_USER_STORY_PLAN.md`
3. `doc/APP_LAYER_ARCHITECTURE_CONTRACT.md`

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

Run:

```bash
./scripts/generate-ui-flow-report.sh
```

Inspect:

- `build/reports/ui-flow/UI_FLOW_REPORT.md`
- `build/reports/ui-flow/logs/*.status`
- `build/reports/ui-flow/logs/*.log`

Expected flow keys:

- `android-onboarding-profile-entry`
- `android-phase3-sequence-button`
- `ios-onboarding-profile-entry`
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
