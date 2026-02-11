# androidTestApp

Isolated Android harness app for KMP/shared sync parity work.

What it provides:
- Runnable UI harness (`MainActivity`) with three actions:
  - `Seed + Run Map-Start Sync`
  - `Run Measurement-Complete Sync`
  - `Run Map-Start Sync (No Seed)`
- Shared upload-trigger slice wiring via `UploadTriggerUseCase` (extracted to `shared/`)
- Shared sync driver wiring via `AndroidTestSyncDriverFactory`
- Local Supabase default target enforcement via `SupabaseTarget.LOCAL`
- Robolectric/unit test coverage for driver behavior and environment guardrails

Run tests:

```bash
./gradlew :androidTestApp:testDebugUnitTest
```

Run app build:

```bash
./gradlew :androidTestApp:assembleDebug
```
