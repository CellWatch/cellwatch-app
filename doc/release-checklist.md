# Android release details

The release checklist is [docs/ops/release-checklist.md](../docs/ops/release-checklist.md); follow that one.
This page keeps the details it leaves out. It was first written for #122, before the 2026-10-27 merges, and was
brought up to date on 2026-11-01 for 1.1.0 (versionCode 14).

## Server before app

- **A migration goes live before the app that needs it.** supabase-kt 1.0.3 omits null fields and ignores
  unknown response keys, but a request with a field the database doesn't have fails the insert, and so does a
  call to an RPC overload it doesn't have. Compare the request models since the last release:
  ```bash
  git diff 2eab6c4 HEAD -- app/src/main/java/edu/gatech/cc/cellwatch/data/network/model app/schemas
  ```
  2eab6c4 is the commit that set versionCode 13; the repo doesn't record which commit was built for it. For
  1.1.0 the diff adds no request field and no Room schema change. Its one new server dependency is
  `register_device(device_id, device_secret)` (#148), pushed to prod on 2026-10-31.

## FCC

- `versionName` is sent to the BDC as `app_version`, which must match a version the FCC approved. The FCC
  approved `1.*` on 2026-11-01, so 1.1.0 needs no new approval.
- A change to how a test is measured or what it records needs FCC review before it ships (README.md,
  "Versioning"). Those changes are kept off `main` until then.

## Play requirements

- **16 KB memory pages.** Check Play Console and Android Studio's APK Analyzer before uploading. On
  2026-10-21, every `arm64-v8a` native library in the dependencies had 4 KB `LOAD` alignment (`objdump -p` on
  the cached AARs):
  - `libmapbox-maps.so` and `libmapbox-common.so` (Mapbox 11.3);
  - `libSearchCore.so` (Mapbox Search native 2.1.0);
  - `libconscrypt_jni.so` (Conscrypt 2.5.2);
  - `libh3-java.so` (H3 3.7.2);
  - `libc++_shared.so`.

  If Play requires 16 KB support for the update, those dependencies need upgrading first. That's a code change
  with its own testing.
- **Target API.** Main targets API 36 since #148. Google raises the minimum every August, so check Play
  Console for the current one.

## Testing

- **Manual test against non-production servers.** Run a full test in Testing mode, then one in FCC Challenge
  mode **only against local Supabase or the CellWatch Test Database project**, whose FCC auto-post trigger is
  disabled. Against production, a challenge test is filed with the FCC under the tester's contact details. See
  [environments.md](environments.md).
- **Upgrade test.** Install the current Play build (versionCode 13), record a few tests in airplane mode,
  install the new build over it, and go online without opening the app. Check that:
  - the Room database opens (schema 18, unchanged since versionCode 13);
  - the backlog uploads on its own, from the hourly upload or when the app comes to the front (#148);
  - submissions upload after their measurements;
  - no duplicates appear on the server.

## Build and sign

- Release builds read the root `cellwatch.properties`: `SUPABASE_URL`, `SUPABASE_API_KEY`,
  `MSAK_LATENCY_PORT`, `TCP_TUPLE_URL` and `MAPBOX_DOWNLOADS_TOKEN`. The `*_LOCAL_*` keys are for debug builds.
  Also check that `app/google-services.json` is the production Firebase app.
- Tag format: CONTRIBUTING.md, "Release & tagging".
- **Signing.** `app/build.gradle` has no `signingConfigs`, and `.gitignore` excludes `/keystore/` and
  `/app/release/`, so the bundle is signed locally (Android Studio: **Build → Generate Signed App Bundle**,
  release) with a keystore kept out of Git. Don't commit the keystore or its passwords.
  `./gradlew :app:bundleRelease` builds an unsigned bundle in `app/build/outputs/bundle/release/`.
- `minifyEnabled` is `false`, so there's no R8 mapping file to upload to Crashlytics.

## When to halt the rollout

Halt the staged rollout (Play Console → Release → Halt) if:
- the crash-free rate for versionCode 14 drops below versionCode 13's;
- API errors or statement timeouts rise on Supabase;
- submission inserts or FCC posts start failing in new ways.

## Watching after release

Daily during the staged rollout and for a week after, alongside the read-only SQL in the checklist's
post-release section:
- **Crashlytics:** crash-free users and new issues for 1.1.0 (14). Check that #112's redaction holds: no
  secrets in the logs.
- **Play Console:** ANR rate, and installs by versionCode.
- **Supabase dashboard:** API 5xx rate, `statement timeout` and `lock timeout` errors in the Postgres logs, and
  database CPU while phones flush their backlogs.
- **The FCC digest email,** which reports newly blocked or held submissions.

Afterwards, close #122.
