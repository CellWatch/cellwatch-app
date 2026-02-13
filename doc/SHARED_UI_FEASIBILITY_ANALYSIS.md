# Shared UI Feasibility Analysis (Android + iOS)

Date: 2026-02-13  
Scope: Preliminary technical feasibility of adopting a shared UI layer for both Android and iOS in CellWatch, given current project constraints.

## Executive Summary

Short answer:
- Shared UI across Android+iOS is technically possible with Compose Multiplatform.
- Under the current project constraints, it is **not a near-term drop-in**.
- The primary blockers are:
  1. Kotlin baseline mismatch (project is on Kotlin `1.9.24`; modern Compose Multiplatform requires Kotlin `2.1+`).
  2. GIS/map stack asymmetry (current map implementation is Android Mapbox-heavy and not portable as-is).

Recommendation:
- Near term: keep shared domain/use-cases, build platform-native UI (Android + iOS) for map-heavy flows.
- Medium term: evaluate a staged Compose Multiplatform UI adoption for non-map screens first.

## Current Project Constraints (Observed)

From repository configuration:
- Kotlin: `1.9.24`
- AGP: `8.9.2`
- SQLDelight: `2.0.2`
- Shared module targets: Android, JVM, iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`)
- `msak-client-kmp` dependency currently consumed from Maven in shared module

Legacy Android app (`frozenApp/`) dependencies/behavior relevant to UI migration:
- Android View/XML UI stack (not Compose)
- Heavy Mapbox Android SDK usage (`com.mapbox.maps:android`, `com.mapbox.search:*`)
- Android-specific map/search UI widgets and flows (`MapView`, `SearchResultsView`, bottom sheets, annotations)
- Android-specific runtime/service behavior (foreground service + location permission interactions)

## Dependency Impact Snapshot (Legacy Android -> Shared UI)

| Area | Legacy dependency/pattern | Shared UI impact |
|---|---|---|
| Core UI framework | Android XML + Fragments/Activities | Requires full UI rewrite for Compose Multiplatform path |
| Map rendering | `com.mapbox.maps:android` | Android-only API surface; not directly reusable as shared Compose iOS map |
| Map search UI | `com.mapbox.search:mapbox-search-android-ui` | Android-specific UI widgets; no direct cross-platform shared wrapper in this repo |
| Material/AndroidX UI | `androidx.appcompat`, `material`, `constraintlayout`, `navigation` | Platform-specific, replaced if moving to shared Compose UI |
| Foreground measurement runtime | Android `MeasurementService` + service binding | Needs platform-specific execution wrappers regardless of UI strategy |
| Data layer | SQLDelight + shared repositories already in `shared/` | Good fit for both strategies; no blocker |
| Sync layer | Shared sync contracts/use-cases already in `shared/` | Good fit for both strategies; no blocker |
| Crypto/key storage | Shared abstraction with platform actuals already present | Good fit for both strategies; no blocker |

## Ecosystem Reality Check (External)

1. Compose Multiplatform status:
- Stable for Android, iOS, desktop (JetBrains docs)

2. Version compatibility:
- Current Compose Multiplatform line expects Kotlin `2.1+` for latest releases
- Therefore, project Kotlin `1.9.24` is a hard constraint against adopting latest shared UI stack directly

3. Mapping:
- Mapbox Compose extension is Android-focused (Jetpack Compose wrapper around Android MapView)
- Mapbox iOS supports SwiftUI directly (separate SDK path)
- Result: Mapbox does not provide one first-party shared Compose map API spanning Android+iOS for this codebase today

4. Third-party shared map option:
- MapLibre Compose exists for Android+iOS, but explicitly reports incomplete feature coverage and API instability
- This is promising but should be treated as exploratory/high-risk for production map parity

## SQLDelight + Kotlin 2.1+ Compatibility Check

Historical context:
- There was a real incompatibility report: SQLDelight `2.0.0` with Kotlin `2.1.0`/K2 (`Task :sqldelightGenerate` failure).

Follow-up from SQLDelight maintainers:
- SQLDelight `2.0.2` release notes include: "Update kotlin and KGP to supports kotlin 2.1.0" (PR `#5394`), which addresses that compatibility line.

Current project impact:
- This repo already uses SQLDelight `2.0.2`, so the specific "SQLDelight cannot work with Kotlin 2.1+" claim is outdated as a blanket statement.
- It remains a migration risk area (compiler/Gradle/plugin interactions), but no longer a hard blocker by itself based on upstream fix history.
- Therefore, Kotlin 2.1+ upgrade feasibility should be evaluated against the full dependency graph (not SQLDelight alone), with full Tier1+Tier2 regression gates.

## Feasibility Matrix

### Option A: Shared UI now (Compose Multiplatform for most/all screens)

Feasibility: **Low (now), Medium (after baseline upgrades)**

Pros:
- Maximum UI code sharing
- Unified UI state patterns

Cons:
- Requires Kotlin major upgrade path first
- GIS/map feature parity risk is high due to Mapbox asymmetry
- Introduces a second major migration (UI framework + platform behavior) while port is still ongoing

### Option B: Shared logic + native platform UI (recommended now)

Feasibility: **High**

Pros:
- Compatible with current version baseline
- Keeps map stack native where it is strongest (Mapbox Android + Mapbox iOS SwiftUI/UIKit)
- Reduces risk while preserving shared core logic benefits

Cons:
- Less UI code sharing
- Requires dual UI implementation discipline

### Option C: Hybrid staged approach

Feasibility: **High**

Approach:
- Keep map/search and measurement-run screens native first
- Optionally introduce shared UI later for non-map screens:
  - onboarding/profile
  - settings
  - sync status/history list shells

Pros:
- Captures some shared-UI benefits with contained risk
- Avoids map-first coupling

Cons:
- Mixed UI strategy adds architecture complexity

## GIS/Map-Specific Risk Analysis

This is likely the hardest blocker for fully shared UI.

Current map flow depends on:
- Rich Android Mapbox APIs and Compose/View integrations
- Mapbox Search Android UI widgets and adapters
- Annotation/bottom-sheet/search result interactions tightly coupled to Android SDK behavior

For iOS:
- Mapbox’s first-class story is SwiftUI wrapper APIs, not shared Compose APIs

Implication:
- If product parity on map UX is critical, platform-native map UI is currently the lowest-risk path.

## Other Notable Constraints

1. Runtime/service model differences:
- Android foreground-service patterns do not map 1:1 to iOS app lifecycle constraints.

2. Permission UX differences:
- Telephony/network/location capture semantics differ significantly between Android and iOS.

3. Existing test harness structure:
- Strong shared logic and hosted parity tests already exist; this favors “shared core + platform UI” progression now.

## Recommended Path

1. Keep Phase 5 architecture contract as shared-core + MVVM platform layers.
2. Implement product UI natively per platform for map-heavy and measurement-execution surfaces.
3. Ensure all business logic continues to flow through shared use-cases/contracts.
4. Re-evaluate shared UI after:
  - explicit Kotlin 2.x upgrade plan
  - compatibility verification for key dependencies
  - spike/prototype proving map story viability

## Decision Record

Decision date: 2026-02-13

Committed direction:
- **Option B** (shared logic + native platform UI) is the active implementation strategy.

Rationale:
- Lowest delivery risk under current constraints.
- Better leverage of existing Android UI flows.
- Avoids coupling core migration progress to immediate Kotlin 2.x + shared-map stack upgrades.

## Decision Gate Checklist (Before Any Shared UI Commitment)

All must be green:
1. Kotlin baseline migration plan approved and tested (`1.9.24` -> `2.1+`).
2. `msak-client-kmp` and critical dependencies validated on new baseline.
3. SQLDelight generation/build/test path validated on upgraded Kotlin baseline in this repo (not inferred from upstream only).
4. Map strategy chosen and prototyped:
  - native per platform, or
  - third-party shared map wrapper with acceptable risk
5. Tier1 + Tier2 regression suites still pass with no reduction in diagnostics.

## External References

- Compose Multiplatform compatibility and versions:
  - https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- KMP FAQ (Compose stability statement):
  - https://kotlinlang.org/docs/multiplatform/faq.html
- Compose compiler/Kotlin 2.x alignment:
  - https://kotlinlang.org/docs/multiplatform/compose-compiler.html
- SQLDelight Kotlin 2.1 incompatibility report (`2.0.0`):
  - https://github.com/sqldelight/sqldelight/issues/5370
- SQLDelight `2.0.2` release note including Kotlin/KGP 2.1 support update:
  - https://github.com/sqldelight/sqldelight/releases/tag/2.0.2
- Mapbox Android Jetpack Compose extension:
  - https://docs.mapbox.com/android/maps/guides/using-jetpack-compose/
- Mapbox iOS SwiftUI support:
  - https://docs.mapbox.com/ios/maps/guides/swift-ui/
- MapLibre Compose status/feature matrix:
  - https://maplibre.org/maplibre-compose/
