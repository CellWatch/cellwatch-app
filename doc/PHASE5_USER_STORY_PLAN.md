# Phase 5 User Story Plan

This document tracks app-layer convergence work (Phase 5) by user story, starting from legacy behavior in `frozenApp/` and mapping to KMP + platform app implementations.

Companion contract:
- `doc/APP_LAYER_ARCHITECTURE_CONTRACT.md`

## Current Snapshot (2026-02-13)

Implemented now:
- Shared onboarding contracts:
  - `OnboardingProfile`
  - `OnboardingValidationUseCase`
  - validation tests in `shared` Tier 1
- Shared runtime onboarding draft contract:
  - `RuntimeOnboardingDraft` + mapping to `RuntimeProfileConfig`
- Harness onboarding UI on both platforms:
  - Android: profile-entry fields + submit
  - iOS: profile-entry fields + submit
- Cross-platform simulator UI flow evidence:
  - stepwise onboarding screenshots (per input action)
  - phase3 button screenshots
  - unified markdown report with per-flow pass/fail + log excerpt

Not implemented yet (Phase 5 core remaining):
- Story 1 product-app integration beyond harnesses (shared persistence + harness round-trip is implemented)
- Story 2+ product-facing app flow integration (measurement start/preflight through final app modules)
- Story 5/6/8 user-facing history/retry/sync-status product UX
- Story 7 settings edit UX bound to persisted shared profile state

Primary active execution surfaces:
- `androidTestApp/`
- `iosTestApp/`
- `shared/`

Reference-only (no feature work target):
- `frozenApp/`

## Source-of-Truth Inputs

Legacy onboarding and initial app entry behavior currently lives in:
- `frozenApp/src/main/java/edu/gatech/cc/cellwatch/ui/main/OnboardingActivity.kt`
- `frozenApp/src/main/java/edu/gatech/cc/cellwatch/ui/onboarding/CollectionModeFragment.kt`
- `frozenApp/src/main/java/edu/gatech/cc/cellwatch/ui/onboarding/FCCInfoFragment.kt`
- `frozenApp/src/main/java/edu/gatech/cc/cellwatch/ui/onboarding/SettingsSetupFragment.kt`

## Story Inventory (Initial)

1. First-run onboarding and profile setup
2. Start a measurement from main screen
3. Complete measurement sequence and persist results
4. Attempt sync after measurement (store-and-forward)
5. View measurement history and status
6. Retry unsynced uploads/submissions
7. Manage basic settings (collection mode, profile fields)
8. Observe sync state and actionable sync errors

## App-Layer Architecture (Phase 5)

Use MVVM in platform app modules:
- Shared module owns domain models, validation rules, orchestration use cases, repository interfaces.
- Android/iOS app modules own viewmodels, navigation, permission prompts, and platform-only UI concerns.
- Platform adapters implement shared seams (capability capture, permission state bridges, runtime config persistence IO details).

Guidance:
- Keep screen state in platform viewmodels (`UiState` + one-off events).
- Keep business decisions in shared use cases (collection-mode rules, sync trigger rules, measurement eligibility checks, submission policies).
- Keep `frozenApp/` as reference only; no new logic there.

## Story 1 Analysis: First-Run Onboarding And Profile Setup

### Legacy behavior

Flow in `OnboardingActivity`:
- Welcome -> Data Use -> Collection Mode -> (FCC Info if challenge mode) -> Permissions
- If collection mode is `TESTING`, FCC info is skipped in legacy flow.
- On completion of permissions step:
  - `onboardingComplete = true`
  - navigate to map activity

Data rules:
- Collection mode selected in onboarding is persisted.
- FCC challenge path requires:
  - non-blank name
  - valid phone
  - valid email
  - acknowledgment checkbox checked
- Required permission gate before exiting onboarding:
  - location
  - phone state (legacy Android-specific)

### KMP-era intent

Shared responsibilities:
- Define and validate profile payload (collection mode + optional/required contact fields by mode).
- Persist profile and onboarding completion through shared runtime/profile storage APIs.
- Expose deterministic validation errors for platform UI display.

Platform responsibilities:
- Render onboarding UI and navigation.
- Request platform permissions and report grant/deny results.
- Map shared validation errors to inline field errors and CTA state.

Product decision (updated):
- FCC contact info is collected in onboarding for both modes (`TESTING` and `FCC_CHALLENGE`).
- `TESTING` mode must remain explicitly visible in app chrome/status surfaces at all times.
- `TESTING` mode still controls challenge/submission semantics, but not whether profile/contact data is collected.

### Proposed shared contracts for Story 1

1. `OnboardingProfile` (shared model)
- `collectionMode`
- `name`, `phone`, `email`
- `fccAcknowledged`
- `onboardingComplete`

2. `OnboardingValidationUseCase` (shared)
- Input: `OnboardingProfile`
- Output: `ValidationResult(valid, fieldErrors, globalErrors)`
- Rules:
  - Both modes require validated name/phone/email/ack.
  - `FCC_CHALLENGE` and `TESTING` differ in downstream submission policy, not profile completeness.

Status:
- Implemented in `shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/onboarding/`
- Tier 1 tests implemented in `shared/src/commonTest/kotlin/edu/gatech/cc/cellwatch/domain/onboarding/`

3. `OnboardingPersistenceUseCase` (shared)
- Save profile
- Save onboarding completion
- Load existing profile for edit/re-entry

Status:
- Implemented in shared onboarding domain + wired in Android/iOS harnesses

### Acceptance criteria (Story 1)

1. New user in either mode cannot complete onboarding until FCC contact + acknowledgement validations pass.
2. Reopening onboarding/settings pre-fills previously saved profile values.
3. Onboarding completion flag is persisted and respected at app launch.
4. Validation behavior is identical on Android and iOS for shared rules.
5. Active mode (`TESTING` vs `FCC_CHALLENGE`) is clearly and persistently visible in app UI after onboarding.

### Test plan

Tier 1 (required):
- Shared unit tests for `OnboardingValidationUseCase`:
  - challenge mode invalid/valid cases
  - testing mode bypass behavior
- Shared persistence tests for profile round-trip

Tier 2 (recommended smoke):
- Android simulator: complete onboarding once in each mode
- iOS simulator: complete onboarding once in each mode
- Verify completion flag and profile values are visible via app state/log output
- Verify persistent mode indicator remains visible in both platforms

Current Tier 2 implementation status:
- Implemented now as stepwise profile-entry round-trip smoke in both harness apps.
- Product-like onboarding UI evidence report command:
  - `./scripts/generate-ui-flow-report.sh`
- Onboarding report path:
  - `build/reports/ui-flow/UI_FLOW_REPORT.md`
- iOS onboarding flow evidence is captured via XCUITest (`iosTestAppUITests`) with real widget interaction (no env-prefill shortcuts in full-flow test).
- Hosted tests (`iosTestAppTests`) remain for in-process invariants and status-text assertions.
- iOS fullscreen guardrail is now explicit: `UILaunchScreen` must remain in `iosTestApp/App/Info.plist` to avoid `320x480` compatibility-mode letterboxing.
- Phase 3 simulator smoke report command:
  - `./scripts/generate-simulator-smoke-report.sh`
- Phase 3 report path:
  - `build/reports/simulator-smoke/SIMULATOR_SMOKE_REPORT.md`

## Story 2 Analysis: Start A Measurement From Main Screen

### Legacy behavior

Entry points:
- Map screen measure CTA (`MapActivity` -> `MeasureActivity`)
- Pre-measure gate in `PreMeasureFragment`

Preflight gates:
- Foreground-service/location permission check before start
- Collection mode read (`FCC_CHALLENGE` vs `TESTING`)
- If challenge mode and not on eligible cellular path, user sees proceed/cancel warning
- User can set in-vehicle flag before run

Start action:
- Starts `MeasurementService` with:
  - `EXTRA_IN_VEHICLE`
  - `EXTRA_COLLECTION_MODE`
- Activity binds to service and transitions UI from pre screen to running screen

### KMP-era intent

Shared responsibilities:
- Centralize measurement preflight rules and explicit failure reasons
- Build start request payload (mode, in-vehicle, runtime profile snapshot id)

Platform responsibilities:
- Permission prompts and OS-specific checks
- UI warning dialogs and confirmation actions
- Start/stop foreground/background execution primitives

### Proposed shared contracts for Story 2

1. `MeasurementStartRequest` (shared model)
- `collectionMode`
- `inVehicle`
- `runtimeProfileSnapshot`

2. `MeasurementPreflightUseCase` (shared)
- Inputs: start request + capability snapshot + connectivity snapshot
- Output: `PreflightResult(allowed, reasonCode, warningTextKey, requiresUserConfirm)`

3. `MeasurementStartCoordinator` (shared boundary)
- Produces deterministic transition from `Idle` -> `Starting` with explicit context payload

Current implementation status:
- Initial shared Story 2 preflight contract is now in `shared`:
  - `MeasurementPreflightUseCase`
  - `MeasurementStartPreflightViewModel`
  - reason-code/result model for allowed/blocked/confirm-required outcomes
- Harness wiring now invokes this shared preflight before Phase 3 start actions as an intermediate migration step.

### Acceptance criteria (Story 2)

1. Both Android and iOS show equivalent preflight outcomes for the same shared inputs.
2. Challenge mode warns before non-cellular path execution.
3. User confirmation is required before proceeding on warned path.
4. Start payload includes mode + in-vehicle and is visible in debug diagnostics.

### Test plan

Tier 1:
- Shared preflight contract tests (allowed/blocked/warn-required matrix).

Tier 2:
- Android + iOS simulator smoke: start flow from UI CTA through preflight gate and transition to running state.

## Story 3 Analysis: Complete Measurement Sequence And Persist Results

### Legacy behavior

Execution path:
- `MeasurementService` drives locate -> latency -> download -> upload -> done/error.
- `MeasureViewModel` maps service state into screen progress updates.
- On success, results rendered as a grouped measurement record.

Persistence:
- Results persisted locally as measurement group records.
- Completion screen shows measurement details and upload status once resolved.

### KMP-era intent

Shared responsibilities:
- Run sequence via shared orchestrator and shared MSAK adapters.
- Persist canonical measurement + submission artifacts via shared repositories.
- Produce stable result envelope for UI rendering.

Platform responsibilities:
- Bind long-running execution to OS runtime model (foreground service/task/background allowances).
- Render progress state and completion/failure UI.

### Proposed shared contracts for Story 3

1. `RunMeasurementSequenceUseCase`
- Input: start request
- Output: flow of `MeasurementRunState` (`Starting`, `Locate`, `Latency`, `Download`, `Upload`, `Completed`, `Failed`)

2. `MeasurementRunResult`
- `measurementGroupId`
- `persistedCounts`
- `capabilityCaptureSummary`
- `errorSummary` (if failed)

3. `MeasurementResultReadModelUseCase`
- Builds UI-facing read model from persisted group id.

### Acceptance criteria (Story 3)

1. Progress stage order is deterministic and identical across platforms for successful runs.
2. Completed run persists latency/download/upload records (and submission when eligible).
3. Failure run preserves partial artifacts + structured error details.
4. Completion UI can reload from persisted data (not only in-memory state).

### Test plan

Tier 1:
- Shared sequence/orchestrator tests for success/failure/partial cases.
- Repository persistence invariants for completed groups.

Tier 2:
- Android+iOS simulator run completes against local/public MSAK profile and results remain queryable afterward.

## Story 4 Analysis: Attempt Sync After Measurement (Store-And-Forward)

### Legacy behavior

Legacy upload path:
- After measurement completion, app attempts upload of unsynced measurements/submissions.
- Network errors keep records unsynced for retry later.
- Duplicate-key handling can mark records uploaded when safe.

Current KMP harness behavior already follows this direction through shared sync contracts and reports.

### KMP-era intent

Shared responsibilities:
- Explicit sync trigger use cases (`map-start`, `measurement-complete`, periodic/manual retry).
- Deterministic sync report with counts and sampled structured errors.
- Local-only by default runtime profile safety.

Platform responsibilities:
- Trigger timing (screen transitions, app resume, user retry action).
- Surface sync status/errors in UI.

### Proposed shared contracts for Story 4

1. `SyncTriggerUseCase` family
- `runMapStartSync()`
- `runMeasurementCompleteSync(groupId)`
- `runPendingSync()`

2. `SyncRunSummary` (shared UI model)
- attempts/uploaded/markedUploaded/networkErrors/unexpectedErrors
- invariant flags (e.g., measurement-complete upload-time set)

### Acceptance criteria (Story 4)

1. Measurement-complete trigger attempts sync immediately after persistence.
2. Failed network uploads remain pending and retryable.
3. Duplicate-safe scenarios mark uploaded when identity rules match.
4. Shared summary is rendered consistently on Android and iOS.
5. User-facing status clearly indicates whether sync is pending, succeeded, failed, or partially failed.

### Test plan

Tier 1:
- Shared sync trigger contract tests and remote mapping tests.

Tier 2:
- Local Supabase simulator smoke confirming records appear remotely and local upload flags are set.

## Story 5 Analysis: View Measurement History And Status

### Legacy behavior

History screen:
- Loads grouped measurements from local DB.
- Displays status including upload indicators.
- Supports export action (JSON file) via document picker.

### KMP-era intent

Shared responsibilities:
- Provide grouped measurement history query + status derivation.
- Keep upload status and capability support fields available in read model.

Platform responsibilities:
- History list UI, filtering/sorting UX, export file picker and write destination.

### Proposed shared contracts for Story 5

1. `GetMeasurementHistoryUseCase`
- Returns grouped list with:
  - timestamp
  - type coverage (lat/dl/ul)
  - upload status summary
  - mode/capability summary

2. `ExportMeasurementsUseCase`
- Shared serialization to export payload format
- Platform handles destination URI/file descriptor

### Acceptance criteria (Story 5)

1. User sees local history even when offline.
2. Upload status is clearly visible per record/group.
3. Export operation writes non-empty valid JSON output.
4. History screen reload reflects newly completed runs without app restart.

### Test plan

Tier 1:
- Shared history read-model tests and export serialization tests.

Tier 2:
- Android+iOS manual smoke for history refresh and export path.

## Story 6 Analysis: Retry Unsynced Uploads/Submissions

### Legacy behavior

Retry behavior exists implicitly:
- Upload attempts on map start and measurement completion.
- Pending records remain local on failure and are retried on subsequent attempts.

### KMP-era intent

Shared responsibilities:
- Provide an explicit pending-sync retry use case and summary.
- Keep retry idempotent and safe for duplicate/partial conditions.

Platform responsibilities:
- Add explicit UI action ("Retry pending uploads") in history/settings/debug surfaces.
- Show retry result counts and sampled errors.

### Proposed shared contracts for Story 6

1. `RetryPendingSyncUseCase`
- Runs pending measurement + submission sync pass
- Returns `SyncRunSummary`

2. `GetPendingSyncCountsUseCase`
- Returns counts for unsynced measurements/submissions for badge/CTA display

### Acceptance criteria (Story 6)

1. User can manually trigger retry from UI.
2. Pending counts decrease after successful retry.
3. Failures remain visible with actionable diagnostics.
4. Retry is safe to run repeatedly.

## Story 8 Analysis: Observe Sync State And Actionable Sync Errors

### Why this is explicit now

Legacy behavior hid much of sync under background/repository calls. For KMP app convergence, sync observability is a user-visible requirement, not only a debug concern.

### KMP-era intent

Shared responsibilities:
- Emit normalized sync state transitions and summaries:
  - `IDLE`, `PENDING`, `IN_PROGRESS`, `PARTIAL_FAILURE`, `FAILED`, `SUCCEEDED`
- Include actionable error categories (network, auth/config, server validation, unknown).

Platform responsibilities:
- Persistently surface current sync state in user-facing UI.
- Provide drill-down error text suitable for user action (retry/check connection/contact support).

### Proposed shared contracts for Story 8

1. `SyncStatusReadModelUseCase`
- Aggregates pending counts + latest run summary + mode/context info.

2. `SyncErrorPresentationMapper`
- Maps low-level exception families to user-facing categories and short guidance text.

3. `SyncStatusStream`
- Observable stream for app-wide indicator surfaces.

### Acceptance criteria (Story 8)

1. User can always tell whether local data is synced or pending.
2. Sync failures are visible without opening debug logs.
3. Error states provide at least one clear next action (retry/check network/etc.).
4. Status representation is consistent across Android and iOS.

### Test plan

Tier 1:
- Shared mapping tests from raw sync outcomes to presentation statuses/categories.

Tier 2:
- Simulator smoke inducing network and auth/config failures, verifying expected user-visible status messages.

### Test plan

Tier 1:
- Shared pending-count and retry use-case tests with fake remote outcomes.

Tier 2:
- Local Supabase smoke: force pending records, run retry, verify remote + local status transitions.

## Story 7 Analysis: Manage Basic Settings (Collection Mode, Profile Fields)

### Legacy behavior

Settings screen supports:
- Edit name/phone/email
- Toggle collection mode (with FCC constraints + acknowledgement checks)
- Show/copy device id and app version

Validation and constraints:
- FCC challenge mode requires populated contact info + acknowledgement.
- Invalid phone/email rejected.

### KMP-era intent

Shared responsibilities:
- Single source of truth for profile/mode validation and mode transition constraints.
- Shared runtime profile + user profile persistence contracts.

Platform responsibilities:
- Form UI/UX and inline validation display.
- Clipboard copy interactions for device/app metadata.

### Proposed shared contracts for Story 7

1. `SettingsProfileUseCase`
- Load/save profile fields with normalized formats.

2. `SetCollectionModeUseCase`
- Enforce preconditions for `FCC_CHALLENGE` mode.
- Provide explicit reason codes for blocked transitions.

3. `GetAppIdentityReadModelUseCase`
- Returns device id/app version/runtime mode summary for display.

### Acceptance criteria (Story 7)

1. Invalid phone/email cannot be saved.
2. Enabling challenge mode without required FCC info is blocked with clear reason.
3. Editing settings updates persisted state used by onboarding/measurement flows.
4. Device/app identity values are visible and copyable.

### Test plan

Tier 1:
- Shared settings validation and mode-transition rule tests.

Tier 2:
- Android+iOS simulator smoke: edit settings, rerun pre-measure flow, verify new settings are applied.

## PR-Sized Implementation Checklist (Suggested)

1. Introduce shared onboarding/settings validation use cases and tests (Stories 1, 7 core rules).
2. Add shared measurement preflight use case and migrate harness preflight callers (Story 2).
3. Add shared measurement run-state UI model mapping and sequence result read model (Story 3).
4. Add shared history read model + export serializer (Story 5).
5. Add explicit pending-sync count + retry use cases and wire into harness UI actions (Story 6).
6. Stand up first product app-layer slice using MVVM on one platform, then mirror on second platform.
