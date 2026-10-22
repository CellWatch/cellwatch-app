# Android app: build and configuration

How the app is built: Gradle/AGP/SDK versions, build types, BuildConfig fields fed from `cellwatch.properties`, local AARs, key dependencies, signing and minification.

Related: [layout](layout.md) · [sync-and-upload](sync-and-upload.md) · [local setup](../dev/local-setup.md) · [testing](../dev/testing.md) · [release checklist](../ops/release-checklist.md)

Files: [build.gradle](../../build.gradle), [settings.gradle](../../settings.gradle), [gradle.properties](../../gradle.properties), [app/build.gradle](../../app/build.gradle), [gradle-wrapper.properties](../../gradle/wrapper/gradle-wrapper.properties), [app/proguard-rules.pro](../../app/proguard-rules.pro).

## Toolchain

| Item | Version / value | Where |
|---|---|---|
| Gradle wrapper | 8.9 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.1.2 (`com.android.application`, `com.android.library`) | root `build.gradle` |
| Kotlin Android plugin | 1.8.10 | root `build.gradle` |
| Kotlin serialization plugin | 1.8.20 (differs from the Kotlin plugin version) | root `build.gradle` |
| Annotation processing | `kotlin-kapt` (Room compiler) | `app/build.gradle` |
| Other plugins | `com.google.gms.google-services` 4.4.0, `com.google.firebase.crashlytics` 2.9.9, `com.google.android.gms.oss-licenses-plugin` 0.10.6 (buildscript classpath), `com.google.relay` 0.3.09 (no Relay UI packages exist in the repo) | |
| Java / JVM target | 17 | `compileOptions`, `kotlinOptions` |

## Android settings (`app/build.gradle`)

| Setting | Value |
|---|---|
| `namespace` / `applicationId` | `edu.gatech.cc.cellwatch` |
| `compileSdk` | 35 |
| `minSdk` | 29 (Android 10) |
| `targetSdk` | 35 |
| `versionCode` | 13 |
| `versionName` | `"1.0.0"` |
| ABI filters | `armeabi`, `armeabi-v7a`, `arm64-v8a` (no x86/x86_64) |
| Build features | `viewBinding true`; BuildConfig enabled globally by `android.defaults.buildfeatures.buildconfig=true` in `gradle.properties` |
| Room schema export | `room.schemaLocation = app/schemas` |
| Lint | `abortOnError false` |
| Product flavors | none |
| Build types | `debug`, `release` (below) |

`versionName` has stayed `1.0.0`; it is what the app reports as `app_version` on measurements and submissions, so server data cannot tell builds apart by that field. Bump it (and `versionCode`) for each release; see [release-checklist.md](../ops/release-checklist.md).

> Pending upgrade: Google Play now requires `targetSdk 36` for app updates, so `compileSdk`/`targetSdk` must move to 36. Related: with AGP 8.1.x, connected (instrumented) tests abort on API 36 emulators; AGP 8.2 or later fixes this. See [../dev/testing.md](../dev/testing.md).

## Build types and backend selection

Both build types have `minifyEnabled false` and list `proguard-android-optimize.txt` + `proguard-rules.pro` (the rules are therefore not applied today).

| BuildConfig field | Type | `debug` source key | `release` source |
|---|---|---|---|
| `SUPABASE_URL` | String | `SUPABASE_LOCAL_URL` | `SUPABASE_URL` |
| `SUPABASE_API_KEY` | String | `SUPABASE_LOCAL_API_KEY` | `SUPABASE_API_KEY` |
| `TCP_TUPLE_URL` | String | `TCP_TUPLE_URL` | `TCP_TUPLE_URL` |
| `MSAK_SERVER_ENV` | String | `MSAK_SERVER_ENV` (`local`, `staging` or `prod`) | literal `"prod"` |
| `MSAK_LOCAL_SERVER_HOST` | String | `MSAK_LOCAL_SERVER_HOST` | literal `""` |
| `MSAK_LOCAL_SERVER_SECURE` | Boolean | `MSAK_LOCAL_SERVER_SECURE` | literal `true` |
| `MSAK_LATENCY_PORT` | Integer | `MSAK_LOCAL_LATENCY_PORT` | `MSAK_LATENCY_PORT` |

So a **debug** build talks to the Supabase instance named by the `SUPABASE_LOCAL_*` keys (normally a local `supabase start`) and to whichever MSAK environment the properties choose; a **release** build always uses the cloud Supabase keys and M-Lab production. `TCP_TUPLE_URL` is the same for both. `MSAK_LATENCY_PORT` is generated but not read by app code. Where each field is used: [measurement-engine.md](measurement-engine.md#choosing-msak--m-lab-servers), [sync-and-upload.md](sync-and-upload.md).

Standard fields used by code: `BuildConfig.VERSION_NAME` (user agent, `appVersion`, Settings screen) and `BuildConfig.DEBUG` (user agent suffix `-debug`, logcat output, Crashlytics key `debug`).

## `cellwatch.properties`

The build reads **`cellwatch.properties` at the repository root** (`rootProject.file(...)` in `app/build.gradle`; `file(...)` in `settings.gradle`). It is git-ignored (`/cellwatch.properties` in the root `.gitignore`). Values are pasted verbatim into `buildConfigField`, so String values must include their double quotes and Boolean/Integer values must not. Every key referenced by a build type should be present.

Key names (never commit values):

| Key | Used for |
|---|---|
| `SUPABASE_LOCAL_URL`, `SUPABASE_LOCAL_API_KEY` | debug Supabase |
| `SUPABASE_URL`, `SUPABASE_API_KEY` | release Supabase |
| `TCP_TUPLE_URL` | tuple service (both builds) |
| `MSAK_SERVER_ENV`, `MSAK_LOCAL_SERVER_HOST`, `MSAK_LOCAL_SERVER_SECURE`, `MSAK_LOCAL_LATENCY_PORT` | debug MSAK settings |
| `MSAK_LATENCY_PORT` | release `MSAK_LATENCY_PORT` |
| `MAPBOX_DOWNLOADS_TOKEN` | Maven credentials (user `mapbox`) for the Mapbox repository in `settings.gradle` |

Other config inputs:

| File | Purpose |
|---|---|
| `app/google-services.json` | Firebase project config for the google-services plugin (Crashlytics). Required to build. Tracked in git. |
| `res/values/strings.xml` key `mapbox_access_token` | Mapbox runtime access token read by the Mapbox SDK. |
| `local.properties` | Android SDK path (standard, git-ignored). |
| `app/cellwatch.properties` | An old copy the build never read. Removed from git in #136; `cellwatch.properties` is now gitignored in every directory. |

`gradle.properties` also defines `ktor_version` and `supabase_version`, but `app/build.gradle` hard-codes its versions and does not read them.

## Local binaries

| File | What | Used by |
|---|---|---|
| [lib/AndroidPing.aar](../../lib/AndroidPing.aar) | `com.spectrum.android.ping.Ping` (ICMP ping) | [ping.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/domain/fcc/ping.kt) for M-Lab server selection |
| [lib/DeviceInformation-release.aar](../../lib/DeviceInformation-release.aar) | `com.oseamiya.deviceinformation.*` | nothing in app code imports it |
| `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libh3-java.so` | Native library for `com.uber:h3` 3.7.2 | `H3Manager` (map hexes). No x86 builds, matching the ABI filters. |

## Key dependencies

| Area | Artifact | Version | Notes |
|---|---|---|---|
| Backend client | `io.github.jan-tennert.supabase:postgrest-kt` | 1.0.3 | JSON: `ignoreUnknownKeys = true`, `encodeDefaults = false`; default request timeout 10 s. See [sync-and-upload.md](sync-and-upload.md#serialization-rule-important-when-adding-fields). |
| Ktor engine | `io.ktor:ktor-client-okhttp` | 2.3.0 | Pending (not yet merged/deployed): replaces `ktor-client-cio` 2.3.0 so `LocalPortHeaders` can see the socket. |
| Serialization | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.5.0 | `kotlinx-datetime` comes in transitively. |
| Speed tests | `com.github.CellWatch:msak-android` (JitPack) | 0.1.3 | M-Lab MSAK throughput1/latency1 client and locate. |
| TLS | `org.conscrypt:conscrypt-android` | 2.5.2 | Installed as provider #1 in `CellWatchApp` for msak byte counting. |
| HTTP | `com.squareup.okhttp3:okhttp` | 4.10.0 | Tuple request, msak locate. |
| Database | `androidx.room:room-runtime` / `room-ktx` / `room-compiler` (kapt) | 2.5.2 | Also declares `room-rxjava2`, `room-rxjava3`, `room-guava`, `room-paging`, which app code does not use. |
| Settings | `androidx.datastore:datastore-preferences` | 1.0.0 | |
| Background work | `androidx.work:work-runtime-ktx` | 2.9.1 | `UploadWorker`. |
| Location | `com.google.android.gms:play-services-location` | 21.3.0 | Fused location. |
| Map | `com.mapbox.maps:android` | 11.3.1 | From the Mapbox Maven repo (needs `MAPBOX_DOWNLOADS_TOKEN`). |
| Map search | `com.mapbox.search:mapbox-search-android`, `-ui` | 2.2.0 | |
| Hexes | `com.uber:h3` | 3.7.2 | Plus bundled `.so` files. |
| Crash reporting | Firebase BoM 32.7.0, `firebase-crashlytics` | | Via [Log.kt](../../app/src/main/java/edu/gatech/cc/cellwatch/core/util/Log.kt). |
| Device info | `com.github.nisrulz:easydeviceinfo-base` | 2.4.1 | Manufacturer, OS version, app name/version. |
| Validation | `commons-validator:commons-validator` | 1.7 | Email. |
| Licenses | `play-services-oss-licenses` | 17.0.1 | About screen. |
| UI | appcompat 1.4.1, material 1.5.0, constraintlayout 2.1.3, flexbox 3.0.0, lifecycle 2.6.1, core-ktx 1.7.0 and 1.13.1 (declared twice) | | |
| JSON (legacy) | `com.google.code.gson:gson` | 2.10.1 | Room list converter, tuple response, map annotation data. |

Declared but not referenced by app code or layouts: `play-services-maps`, `navigation-fragment-ktx` / `navigation-ui-ktx`, `commons-io`, `android-device-names`, `speedviewlib`, the extra Room artifacts above, and `androidx.test.ext:junit-ktx` (declared as `implementation`, not a test dependency).

Test dependencies: JUnit 4.13.2, `room-testing`; instrumented: AndroidX JUnit 1.1.3, Espresso 3.4.0, MockK 1.13.8, MockWebServer 4.11.0. See [../dev/testing.md](../dev/testing.md).

## Signing

There is no `signingConfigs` block in any Gradle file, and no keystore or key-alias property names are referenced by the build. Release artifacts are therefore signed outside the Gradle config. TODO: verify and document the actual release signing process (keystore location, who holds it, Play App Signing) in [release-checklist.md](../ops/release-checklist.md).

## ProGuard / R8

`minifyEnabled false` for both build types, so R8 does not shrink or obfuscate. [proguard-rules.pro](../../app/proguard-rules.pro) only contains the standard kotlinx.serialization keep rules (companion `serializer()` and `INSTANCE`) and `-keepattributes RuntimeVisibleAnnotations,AnnotationDefault`. If minification is enabled later, Room, msak, Mapbox and Gson reflection (for `TcpTuple` and the list converter) will need checking.

## Manifest-level build facts

- `usesCleartextTraffic="true"` (plain-HTTP tuple service and local dev servers).
- `allowBackup="false"`, `fullBackupContent="false"`, `dataExtractionRules=@xml/data_extraction_rules`.
- `tools:targetApi="31"` on `<application>`.

Details: [ui-and-screens.md](ui-and-screens.md#permissions).
