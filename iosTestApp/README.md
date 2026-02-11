# iosTestApp

Isolated iOS host test app for KMP parity work, mirroring the purpose of `androidTestApp`.

Covers:
- `sharedKit` Keychain-backed secure storage integration
- local/remote Supabase environment guardrail behavior (unit tests)
- shared upload-trigger parity harness behavior from `shared/` (`UploadTriggerParityHarness`)
- runnable UI harness (`HarnessViewController`) with:
  - local environment resolve action
  - map-start shared-slice action
  - measurement-complete shared-slice action

Run hosted tests:

```bash
./gradlew :shared:verifyIosTestAppHosted
```

Xcode prebuild now uses `/Users/jeff/Projects/cellwatch-app/scripts/compile-shared-framework-for-xcode.sh`,
which keeps `sharedKit.framework` current at:
- `/Users/jeff/Projects/cellwatch-app/shared/build/bin/iosSimulatorArm64/Current/sharedKit.framework`

To force composite-source local `msak` during Xcode builds:

```bash
CELLWATCH_USE_LOCAL_MSAK_COMPOSITE=true \
CELLWATCH_LOCAL_MSAK_DIR=/Users/jeff/Projects/msak-android \
xcodebuild \
  -project iosTestApp/iosTestApp.xcodeproj \
  -scheme iosTestApp \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2" \
  test
```

Direct Xcode invocation:

```bash
xcodebuild \
  -project iosTestApp/iosTestApp.xcodeproj \
  -scheme iosTestApp \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2" \
  test
```
