# UI delivery plan

Getting from "test harness with UI" to a coherent application, under the rules in
`APP_LAYER_ARCHITECTURE_CONTRACT.md` (see the 2026-09-19 addendum).

Working doc: the status tables are updated as work lands. Started 2026-09-19.

---

## Where we actually are

Measured, not estimated:

- iOS: UIKit, fully programmatic, **4,330 lines** in `AppDelegate.swift`.
- Android: Android Views, fully programmatic, **3,322 lines** in `MainActivity.kt`, **0 XML layouts**.
- Navigation exists in embryo — `pushMvpScreen(mode:)` and `returnToMapHome()` push another copy
  of the same object in a different mode.
- The shared domain layer underneath is solid and well tested. **The problem is confined to
  presentation.**

These modules are named `iosTestApp` and `androidTestApp` and have a `FULL_HARNESS` mode. They
were built as harnesses, and they succeeded as harnesses. They stay, unchanged, for
instrumentation and CI; the product UI grows alongside them.

## Story reconciliation

Against the 12-story inventory in `PHASE5_USER_STORY_PLAN.md`. "Wired" = referenced by app code
on that platform; iOS reaches stories 4 and 6 through `IosPhase3SequenceSyncHarness` rather than
directly.

| # | Story | Shared code | Wired | Verdict |
|---|---|---|---|---|
| 1 | Onboarding + profile | `OnboardingProfileViewModel` | both | **adopt** — verify layout only |
| 2 | App shell + home routing | `AppLaunchRoutingUseCase` | both, partial | **extend** — two destinations today; needs the full graph |
| 3 | Start measurement | `MeasurementStartPreflight*` (5 classes) | both | **collapse** to one ViewModel |
| 4 | Run sequence + persist | `MeasurementSequenceSyncOrchestrator` | both (iOS indirect) | **adopt** |
| 5 | Results UX | `MeasurementRunViewController` | both | **rename** to ViewModel; verify layout |
| 6 | Sync after measurement | `UploadTriggerUseCase` | both (iOS indirect) | **adopt** |
| 7 | History + status | `MeasurementHistoryViewController` | **neither** | **de-duplicate** — shared class orphaned, screen reimplemented inline as `renderMeasurementHistoryUi()` |
| 8 | Retry unsynced | `PendingSync*` | both (13/16 refs) | **adopt** |
| 9 | Observe sync state | diagnostics only | partial | **design** — status text exists, not actionable |
| 10 | Settings | `SettingsProfileViewModel` | both | **adopt** |
| 11 | Export history | **none** | none | **build** — frozenApp has `dialog_export_confirmation` |
| 12 | GIS map | `MapHome*` (3 classes) | both | **collapse** to one ViewModel |

Only one story is genuinely missing. One is duplicated. The rest need adoption, collapsing, or
layout work — which is why this is a coherence exercise, not a rewrite.

---

## Phase 0 — Foundations

No screen work until these land; they are what makes screen work reusable.

| Task | Detail | Status |
|---|---|---|
| 0.1 | Record the reconciliation above | done |
| 0.2 | Shared `Destination` + router, extending `AppLaunchRoutingUseCase` to the full graph (Rule 2) | todo |
| 0.3 | Component inventory, iOS: 9 baseline components + screen template + spacing scale (Rule 3) | todo |
| 0.4 | Component inventory, Android: same 9, same template | todo |
| 0.5 | Collapse presentation roles to `ViewModel` + `UiState` (Rule 1): `measurementstart` 5→1, `maphome` 3→1, rename `*ViewController` | todo |

## Phase 1 — Vertical slice

The priority slice already agreed in `PHASE5_USER_STORY_PLAN.md`: **profile setup → home →
start measurement → run → results.** Each screen is finished, reachable and simulator-verified
before the next begins. No parallel screens — that is how duplication got in.

| Task | Screen | Story | Status |
|---|---|---|---|
| 1.1 | Onboarding / profile | 1 | todo |
| 1.2 | Map home (shell entry) | 2, 12 | todo |
| 1.3 | Start measurement | 3 | todo |
| 1.4 | Measurement run progress | 4 | todo |
| 1.5 | Results | 5, 6 | todo |

## Phase 2 — Remaining stories

| Task | Screen | Story | Status |
|---|---|---|---|
| 2.1 | History + sync status — delete the inline duplicate, adopt the shared class | 7, 9 | todo |
| 2.2 | Retry unsynced | 8 | todo |
| 2.3 | Settings | 10 | todo |
| 2.4 | Export | 11 | todo |

---

## Per-screen working method

Every screen in Phases 1 and 2 follows the same loop. This is the part that was missing.

1. **Compare with frozenApp first.** Open the corresponding layout and Activity/Fragment. Note
   what it does, what it shows, and which behaviours are deliberate. Record any intended
   divergence (Rule 4.8).
2. **Shared ViewModel + UiState**, unit-tested, no platform calls (Rules 1, 4.1, 4.2).
3. **Compose the view from inventory components** on each platform; extend the inventory rather
   than inlining (Rule 3).
4. **Wire into the navigation graph** so it is reachable from launch (Rule 4.5).
5. **Run on the iOS 17.5 simulator** (iPhone 15 Pro, `B2875856-6CE2-40C6-896D-134FAA277283`):
   screenshot, review against the screen template, drive at least one interaction end to end
   (Rule 4.7).
6. **Run the suites** — `:shared:verifyLightweightPlatforms` and `:androidTestApp:testDebugUnitTest`.
7. **Update the status table** in this document and commit.

Android has no simulator loop here by choice: it has never run on hardware either
(`OUTSTANDING_WORK.md` item 2), so Android verification is batched into a device session rather
than pretended at.

## Layout decisions — how they get made

Visual incoherence came from every screen inventing its own arrangement. Decisions are made once
and recorded here as they are taken:

- **Screen template**: header, scrollable content, pinned actions.
- **Spacing scale**: to be fixed in task 0.3, then used everywhere.
- **Reference**: frozenApp's layouts are the product intent. Where its arrangement is good, copy
  it; where it is not, record why.

Open layout questions are listed here as they arise rather than decided ad hoc mid-screen.

## What this plan will not do

- Refactor the two harness god-objects. They stay as harnesses (`OUTSTANDING_WORK.md`).
- Adopt Compose Multiplatform. Blocked on Kotlin 1.9.24; see the decision gate in
  `SHARED_UI_FEASIBILITY_ANALYSIS.md`.
- Touch `frozenApp/`, which is reference-only per contract guardrail 1.
