# Android release checklist

Written for #122, the first release since versionCode 13 (Oct 2025). It ships the upload fixes (#111) and the
carrier fallback (c51980e). Steps marked **(decision)** need a person; the rest are mechanical. Nobody has run
this procedure yet; it's based on what the repo shows.

## 1. Before building

### Server first

- [ ] **Server fixes are live.** Deploy #115 (the RLS check that slows uploads, plus the
  `measurements(device_id)` index) before the app goes out. The new build makes phones with a backlog upload
  it: at most 3 requests in flight per phone, but every phone at once. Under the current policies, a device
  with ~400 measurements takes ~12 s per upload, and supabase-kt gives up after 10 s. The fix also helps builds
  already on phones. #116 (payload view) and #117 (rolled-back posts) control how fast the FCC queue drains
  after the release, so do them before or soon after.
- [ ] **Migrations are live before the app that needs them.** supabase-kt 1.0.3 omits null fields and ignores
  unknown response keys, but a request with a field the database doesn't have fails the insert. Compare the
  request models since the last release:
  ```bash
  git diff 2eab6c4 HEAD -- app/src/main/java/edu/gatech/cc/cellwatch/data/network/model app/schemas
  ```
  2eab6c4 is the commit that set versionCode 13. The repo doesn't record which commit was built for it, so
  confirm with whoever uploaded it. For this release the diff is empty: no new request fields and no Room
  schema change. If a later release adds a field, apply its migration first, following supabase/README.md.
- [ ] **No drift.** `supabase/check_drift.sh` passes, so the server is in the state the repo describes.

### FCC

- [ ] **(decision) The version string.** Spec §5.1.2 requires `app_version` to "match an approved App Version
  string"; the Demo allow list was `1.*` (#97). Ask the FCC whether a new `versionName` needs approval (see
  README.md, "Versioning", and #95). **Until they answer, bump only `versionCode` (13 → 14) and keep
  `versionName "1.0.0"`.**
- [ ] **(decision) Anything else the FCC should hear about.** The release changes upload timing, not the
  measurement method. Anything that changes measurement or submission semantics is a MAJOR change under the
  versioning policy and needs FCC review first.

### Play requirements (check in Play Console; these can block the upload)

- [ ] **16 KB page size.** Google Play requires updates that target Android 15+ (we target 35) to support 16 KB
  memory pages on 64-bit devices. As far as we know this has applied to updates since Nov 1, 2025. Every
  `arm64-v8a` native library in our dependencies has 4 KB `LOAD` alignment (`objdump -p` on the cached AARs,
  Oct 21, 2026):
  - `libmapbox-maps.so` and `libmapbox-common.so` (Mapbox 11.3);
  - `libSearchCore.so` (Mapbox Search native 2.1.0);
  - `libconscrypt_jni.so` (Conscrypt 2.5.2);
  - `libh3-java.so` (H3 3.7.2);
  - `libc++_shared.so`.

  If Play Console or Android Studio's APK Analyzer flags it, those dependencies need upgrading to 16 KB-aligned
  versions first. That's a code change with its own testing.
- [ ] **Target API level.** Google raises the minimum `targetSdk` for updates every August. Check whether 35 is
  still accepted. If 36 is required, that change (and the AGP upgrade it probably needs; AGP is 8.1.2) comes
  before this release.

### Tests

- [ ] `ANDROID_HOME=~/Library/Android/sdk ./gradlew --offline :app:testDebugUnitTest` passes.
- [ ] `supabase/db_tests/run.sh` passes.
- [ ] **Manual test on a phone against non-production servers** (doc/environments.md): a full test in Testing
  mode, then in FCC Challenge mode **only against local or staging Supabase**. Against production, a challenge
  test is filed with the FCC.
- [ ] **Upgrade test.** Install the current Play build (versionCode 13), record a few tests in airplane mode,
  install the new build over it, and go online. Check that:
  - the Room database opens (schema 18, unchanged);
  - the backlog uploads with no more than 3 requests at a time;
  - submissions upload after their measurements;
  - no duplicates appear on the server.

## 2. Version, build, sign

1. Bump `versionCode` (and `versionName` only if decided above) in `app/build.gradle`, in its own commit:
   `Release v1.0.0 (versionCode 14)`.
2. Tag it and push the tag (format in CONTRIBUTING.md, "Release & tagging"):
   ```bash
   git tag -a v1.0.0+14 -m "versionCode 14: upload and carrier fixes"
   ```
3. Build from a clean checkout of the tag, with release values in the root `cellwatch.properties`:
   - `SUPABASE_URL` / `SUPABASE_API_KEY` for the production project;
   - `MSAK_LATENCY_PORT`;
   - `TCP_TUPLE_URL`;
   - `MAPBOX_DOWNLOADS_TOKEN`.

   Also check that `app/google-services.json` is the production Firebase app. The release build reads
   `SUPABASE_URL`/`SUPABASE_API_KEY`, not the `_LOCAL_` keys.
4. **Signing.** `app/build.gradle` has no `signingConfigs`, and `.gitignore` excludes `/keystore/` and
   `/app/release/`. That points to signing locally in Android Studio (**Build → Generate Signed App Bundle**,
   release) with a keystore kept out of Git. The repo doesn't say who holds the upload key or whether Play App
   Signing is on (Play Console → App integrity), so write that down somewhere private. Don't commit the
   keystore or its passwords.
   `./gradlew :app:bundleRelease` gives an unsigned bundle at `app/build/outputs/bundle/release/`.
5. `minifyEnabled` is `false`, so there's no R8 mapping file to upload to Crashlytics.

## 3. Play Console rollout

1. Upload the AAB to the **internal testing** track. Install it from Play on at least one phone that has a
   backlog. Check that Play Console shows no blocking warnings (16 KB, target API, permissions declarations).
2. Promote to **production** as a **staged rollout**, for example 10% for 2-3 days, then 50%, then 100%. Write
   "What's new" (upload reliability, carrier name fix).
3. **Halt the rollout** (Play Console → Release → Halt) if any of these happen:
   - the crash-free rate for versionCode 14 drops below versionCode 13's;
   - API errors or statement timeouts rise on Supabase;
   - FCC posts start failing in new ways (section 4).
4. Create the GitHub Release for the tag, with the notes and any server steps taken.

## 4. After release

Watch daily during the staged rollout and for a week after.

- **Crashlytics:** crash-free users and new issues for `1.0.0 (14)`. `versionName` didn't change, so filter by
  version code. Check that #112's redaction holds: no secrets in the logs.
- **Play Console:** ANR rate, install adoption by versionCode.
- **Supabase dashboard:** API 5xx rate, `statement timeout` and `lock timeout` errors in the Postgres logs, and
  database CPU while backlogs flush.
- **Uploads reaching the server.** Run these read-only, through the session pooler with
  `set default_transaction_read_only = on` first. `app_version` can't tell builds apart, so compare before and
  after the rollout date:
  ```sql
  -- New submissions per day: how many have all three measurements, and how many have a blank carrier.
  -- Limited to 14 days because the readiness check scans measurements (no group_id index yet; #116).
  select date_trunc('day', s.created_on) as day,
         count(*) as submissions,
         count(*) filter (where private.fcc_submission_is_ready(s.id)) as complete,
         count(*) filter (where nullif(btrim(s.provider), '') is null) as blank_carrier
  from public.fcc_submissions s
  where s.created_on > now() - interval '14 days'
  group by 1 order by 1;

  -- FCC queue: the waiting and "Missing provider" counts should stop growing for new submissions.
  select state, last_error, count(*) from private.fcc_submission_retry_queue
  group by 1, 2 order by 3 desc;

  -- FCC posting outcomes per day.
  select date_trunc('day', occurred_on) as day, succeeded, count(*)
  from private.fcc_submission_failures
  where occurred_on > now() - interval '14 days'
  group by 1, 2 order by 1, 2;
  ```
  What to expect:
  - more `complete` submissions as phones on the new build flush their backlogs;
  - `blank_carrier` near zero for submissions from updated phones;
  - fewer queue entries stuck "Waiting for complete latency/download/upload data". Entries from phones that
    never update stay stuck; that's #119.
- **Afterwards:** close #122 and note the versionCode in #95.
