# App-Layer Architecture Contract

This contract defines how Phase 5 app-layer code must be structured across shared KMP logic and platform app modules.

Scope:
- Replacement product app layers only
- `shared/` + Android app layer + iOS app layer
- Does not apply to `frozenApp/` except as behavioral reference

## Goals

1. One shared business logic path across Android and iOS.
2. Platform UI parity at the story/behavior level, not pixel parity.
3. Deterministic runtime/profile/sync behavior with explicit failure states.
4. Testability: shared rules in Tier 1, platform integration in Tier 2.

## Module Boundaries

### Shared (`shared/`)

Owns:
- Domain models and policy rules
- Use cases and orchestration
- Repository interfaces + shared repository implementations
- Runtime profile validation/resolution contracts
- Sync status and error classification models

Must not own:
- Android/iOS UI toolkit code
- OS permission prompts
- Platform navigation concerns

### Platform app layers (Android/iOS)

Own:
- Screens, navigation, viewmodels
- UI state rendering and user interactions
- Permission request UX and platform lifecycle hooks
- Platform adapters implementing shared seams

Must not own:
- Business rules duplicated from shared use cases
- Ad-hoc runtime config parsing outside shared contract entrypoints

## Pattern Contract (MVVM)

Per screen:
1. `ViewModel` consumes shared use cases.
2. `UiState` is immutable and render-only.
3. One-off UI events (dialogs/toasts/navigation) emitted separately from `UiState`.

Rules:
- Viewmodels do not call platform APIs directly.
- Viewmodels do not parse config files/env directly.
- Viewmodels do not contain SQL/network transport specifics.

## Runtime/Profile Contract

1. All runtime configuration must flow through:
- `RuntimeProfileStore`
- `RuntimeProfileResolver`
- `RuntimeProfileContract`

2. Source precedence and strict validation behavior must be identical on both platforms.

3. App startup and measurement start both consume resolved runtime snapshot, not raw config keys.

4. `TESTING` mode must be persistently visible in app UI.

## User Story Contract Surface

Minimum shared use-case surface for Phase 5:
- `OnboardingValidationUseCase`
- `OnboardingPersistenceUseCase`
- `AppLaunchRoutingUseCase`
- `MeasurementPreflightUseCase`
- `RunMeasurementSequenceUseCase`
- `MeasurementResultReadModelUseCase`
- `GetMeasurementHistoryUseCase`
- `ExportMeasurementsUseCase`
- `RetryPendingSyncUseCase`
- `GetPendingSyncCountsUseCase`
- `SyncStatusReadModelUseCase`
- `SetCollectionModeUseCase` / settings profile use case

Note:
- Names above are contract targets; exact package/file names can vary if behavior is preserved.

## Sync Visibility Contract

Sync status must be user-visible and normalized across platforms:
- `IDLE`
- `PENDING`
- `IN_PROGRESS`
- `PARTIAL_FAILURE`
- `FAILED`
- `SUCCEEDED`

Each non-success status must include:
- short human-readable reason
- at least one next action hint (retry/check network/check config)

## Platform Adapter Contract

Adapters must be thin translation layers:
- Permission status provider
- Capability snapshot provider
- Foreground/background execution hooks
- File export destination bridge

Adapter constraints:
- No business policy branching that conflicts with shared rules
- No hidden fallback behavior; failures are explicit and surfaced

## Error Contract

All critical flows must produce structured error outputs:
- category (network/auth-config/validation/server/unknown)
- context id
- root message
- optional cause chain (debug level controlled)

User-facing UI:
- shows concise status
- can expose details in expanded view/log panel

## Testing Contract

### Tier 1 (required for merge)

- Shared use-case rule tests for each story
- Runtime/profile contract tests
- Sync status mapping tests
- Persistence invariants for measurement + submission artifacts

### Tier 2 (required smoke set)

- Android simulator story smoke:
  - onboarding
  - measurement run
  - pending sync retry
- iOS simulator story smoke:
  - onboarding
  - measurement run
  - pending sync retry

iOS runner split (required):
- Hosted XCTest (`iosTestAppTests`) is for in-process invariant/integration checks.
- XCUITest (`iosTestAppUITests`) is for product-like UI-flow evidence and simulator-frame screenshots.
- Do not duplicate full assertion sets across both runners.
  - Hosted owns deep business/runtime invariants.
  - XCUITest owns user-visible interaction and screenshot proof.
- iOS launch geometry requirement:
  - keep a launch-screen declaration (`UILaunchScreen`) so simulator/device runs use full-screen native geometry (not legacy `320x480` compatibility mode).
- XCUITest interaction requirement:
  - use explicit wait/tap/type/assert per control; avoid environment-only prefill/autosubmit shortcuts for full user-flow parity tests.

## Implementation Guardrails

1. No new feature logic in `frozenApp/`.
2. No platform-specific bypass flags enabled by default.
3. No silent fallbacks for missing required runtime config.
4. Any temporary developer bypass must be opt-in and visibly reported.

## Definition of Done (Phase 5 Baseline)

1. Stories 1-12 from `doc/PHASE5_USER_STORY_PLAN.md` implemented with shared-first logic in priority order.
2. Android and iOS app layers consume same shared use-case path for core measurement + sync.
3. User-visible sync state exists and is actionable on both platforms.
4. Tier 1 + required Tier 2 smoke set pass.
