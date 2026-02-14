# iOS App Bootstrap Checklist

Use this checklist when creating or refactoring iOS app targets in this repo.

## Launch + Fullscreen

- Define `UILaunchScreen` in `Info.plist`.
- Keep `UIApplicationSceneManifest` configured for `UIWindowScene` + `SceneDelegate`.
- If simulator UI appears letterboxed, first verify runtime geometry:
  - expected points should match target device class (not legacy `320x480`).

## Onboarding / Form UX

- Ensure onboarding mode is full-screen and does not embed harness debug panes.
- Keep keyboard dismissal available:
  - input accessory `Done` button
  - background tap dismissal fallback
- For text editing ergonomics:
  - select-all on focused prefilled fields for quick overwrite.

## Test Strategy Split

- Hosted XCTest (`iosTestAppTests`):
  - in-process invariant checks
  - shared/runtime/persistence contract assertions
- XCUITest (`iosTestAppUITests`):
  - real widget interaction (tap/type/toggle)
  - product-like simulator-frame screenshots/evidence

## XCUITest Interaction Pattern

- `waitForExistence` on each control before interaction.
- Use targeted `tap` + `typeText`.
- Before tapping lower controls, dismiss keyboard (`Done` first, fallback background tap).
- Assert field/value state after each critical action (not just final screen).

