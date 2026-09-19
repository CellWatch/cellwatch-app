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
| 0.2 | Shared `Destination` + `Navigator`, extending `AppLaunchRoutingUseCase` to the full graph (Rule 2) | **done** — 8 destinations, back stack, 7 tests; not yet adopted by either platform (that lands with each screen in Phase 1) |
| 0.3 | Component inventory, iOS: 9 baseline components + screen template + spacing scale (Rule 3) | **done** — reviewed on the simulator; see Layout decisions |
| 0.4 | Component inventory, Android: same 9, same template | **done** — reviewed on `Medium_Phone_API_36`; matches iOS |
| 0.5 | Collapse presentation roles to `ViewModel` + `UiState` (Rule 1) | **folded into Phase 1/2** — see below |

### Why 0.5 is not a standalone task

Collapsing the roles up front would touch every class twice: once to rename, once again when the
screen is rebuilt. The surface is not small — `MeasurementRunViewController` alone spans 11 files
and sits on the measurement path verified on real cellular hardware — and a pure rename produces
no user-visible change while destabilising something that works.

So each screen task below **collapses its own package as part of the rebuild**: one ViewModel and
one UiState, with `FlowController`/`UiPresenter`/`*ViewController` folded in, verified on both
emulators as part of that screen. Rule 1 still governs; it is applied per screen rather than in
one sweep.

Per-screen collapses owed:

| Package | Today | Becomes | Lands in |
|---|---|---|---|
| `measurementstart` | 5 classes | `MeasurementStartViewModel` + a correctly-named preflight use case (the current `MeasurementStartPreflightViewModel` holds domain types and policy, not presentation) | 1.3 |
| `maphome` | 3 classes | `MapHomeViewModel` | 1.2 |
| `measurementrun` | `MeasurementRunViewController` + `MeasurementRunUiPresenter` | `MeasurementRunViewModel` | 1.4 |
| `measurementhistory` | `MeasurementHistoryViewController` (orphaned) | `MeasurementHistoryViewModel`, adopted | 2.1 |

## Phase 1 — Vertical slice

The priority slice already agreed in `PHASE5_USER_STORY_PLAN.md`: **profile setup → home →
start measurement → run → results.** Each screen is finished, reachable and simulator-verified
before the next begins. No parallel screens — that is how duplication got in.

| Task | Screen | Story | Status |
|---|---|---|---|
| 1.1 | Onboarding / profile | 1 | **done both platforms** — shared VM adopted unchanged, composed from inventory, reachable at launch, interaction driven on iOS 17.5 sim and `Medium_Phone_API_36` |
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
5. **Run on both emulators**: iOS 17.5 simulator (iPhone 15 Pro,
   `B2875856-6CE2-40C6-896D-134FAA277283`) and the Android AVD `Medium_Phone_API_36`, chosen
   because API 36 exercises the `TelephonyCallback` path rather than the deprecated
   `PhoneStateListener` fallback. Screenshot each, review against the screen template, and drive
   at least one interaction end to end (Rule 4.7).
6. **Run the suites** — `:shared:verifyLightweightPlatforms` and `:androidTestApp:testDebugUnitTest`.
7. **Update the status table** in this document and commit.

Android runs the same loop on an emulator. There is no physical Android device available, so
the emulator is the verification surface rather than a stand-in for one. Step 5 therefore runs
twice per screen, once per platform.

What an emulator cannot show still stands unverified and is tracked in `OUTSTANDING_WORK.md`:
no radio means no cells, no generation change, and no cellular gate. That limits *measurement*
verification, not UI verification - layout, navigation and interaction are fully testable on
both.

## Layout decisions — how they get made

Visual incoherence came from every screen inventing its own arrangement. Decisions are made once
and recorded here as they are taken:

Taken in 0.3, reviewed on the simulator:

- **Spacing scale 4 / 8 / 12 / 16 / 24 / 32.** Derived from frozenApp's layouts rather than
  invented: across its 21 files, 16dp appears 58 times, 8dp 47, 4dp 17, 12dp 15, 24dp 7. Values
  off that scale (2, 5, 6, 10dp) were the drift. frozenApp's own `dimens.xml` was empty, so
  spacing had been decided per layout.
- **Palette** is frozenApp's `colors.xml` verbatim, exposed through roles (`primary`, `success`,
  `warning`, `surface`, `border`, `textPrimary`…) so screens never touch raw hues.
- **Screen template**: navigation bar, scrollable content, pinned actions. Actions are pinned
  because the primary action on a long form should not have to be scrolled to.
- **The navigation bar owns the title, not the scaffold.** The first build had the scaffold
  render its own title too, which produced two headers and about 150pt of dead space, with the
  first form field sliced by the translucent bar. Screens set `navigationItem.title`.
- **Dynamic Type everywhere**, and a 44pt minimum tap target; several harness buttons are
  smaller.
- **Stacks, not per-screen constraints.** `NSLayoutConstraint` blocks are what made harness
  layouts unrepeatable, so spacing flows from the scale through stack views.

Taken in 0.4:

- **Android components are built in code, not XML.** A deliberate divergence from frozenApp's 21
  layouts: androidTestApp already has zero XML so this adds no second paradigm, and mirroring the
  iOS factories keeps screen code structurally similar across platforms. Revisit if screens grow
  complex enough that layout previews pay for themselves.
- **A `Theme.CellWatch` applied per-activity**, not application-wide, so the harness keeps the
  stock theme it was built against. Without it the gallery showed Material's purple status bar
  and, being `NoActionBar`, had nowhere to put the title — the Android title would simply have
  vanished while iOS showed one.
- **Light parent, not DayNight.** The inventory hardcodes light surfaces and dark text, so
  DayNight would render white cards with unreadable text in dark mode. Dark mode is a deliberate
  not-yet.

Reference: frozenApp's layouts are the product intent. Where its arrangement is good, copy it;
where it is not, record why.

Taken in 1.1:

- **The action area is installed only when a screen has actions.** An empty `UIStackView` has no
  intrinsic content size, so keeping it always present left the layout under-constrained: on a
  screen with no actions the solver gave the empty stack all 818pt and crushed the scroll view to
  zero height, rendering a completely blank screen. Screens with buttons hid it. Content-hugging
  does not fix this — hugging needs an intrinsic size to hug.
- **Form-field capitalisation follows the keyboard type.** The default capitalised an address
  into `Jw199@gatech.edu`. Fixed in the component, so every screen inherits it.

## Open product questions

- **How much of frozenApp's onboarding should return?** frozenApp onboarded through six
  fragments: welcome, read more, FCC information, data use, collection mode, permissions. The KMP
  app captures only the contact details the FCC submission requires. Collection mode moving to
  settings was a deliberate, recorded decision; the informational steps - **particularly data
  use**, which is a consent disclosure for an app collecting location traces - were not decided,
  they simply were not carried over. The navigation graph takes extra destinations without
  rework, so this can be answered at any point.

## What this plan will not do

- Refactor the two harness god-objects. They stay as harnesses (`OUTSTANDING_WORK.md`).
- Adopt Compose Multiplatform. Blocked on Kotlin 1.9.24; see the decision gate in
  `SHARED_UI_FEASIBILITY_ANALYSIS.md`.
- Touch `frozenApp/`, which is reference-only per contract guardrail 1.
