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

Direct Xcode invocation:

```bash
xcodebuild \
  -project iosTestApp/iosTestApp.xcodeproj \
  -scheme iosTestApp \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2" \
  test
```
