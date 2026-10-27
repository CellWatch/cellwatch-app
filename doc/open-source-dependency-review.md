# Third-party dependency review for the open-source release

This is the "Review 3rd party dependencies" task of #107. It doesn't choose the project's license: that's
@jeremy303's task, and the GT software disclosure is filed separately. It lists what we depend on, under which
terms, and what each candidate license would imply.

## Method

- Direct dependencies: `build.gradle`, `settings.gradle`, `app/build.gradle`, `lib/*.aar` and
  `gradle/wrapper/gradle-wrapper.properties`, on `main` at 9a7f0e5.
- `package-lock.json` is empty (`"packages": {}`) and there is no `package.json`, so there are no npm
  dependencies. The file can be deleted.
- Transitive dependencies: `./gradlew --offline :app:dependencies --configuration releaseRuntimeClasspath`
  (Oct 21, 2026) resolved 255 distinct artifacts that ship in the release APK.
- Licenses: read from the `<licenses>` block of each artifact's POM in
  `~/.gradle/caches/modules-2/files-2.1/`, following `<parent>` POMs where needed. Anything not confirmed this
  way is marked **unverified**. A POM's license tag is the publisher's claim; it is not a legal review.

## Summary

Of the 255 artifacts in the release APK, by POM license:

| License | Artifacts | Notes |
|---|---|---|
| Apache-2.0 | 205 | AndroidX, Kotlin, kotlinx, Ktor, OkHttp, Gson, Room, Firebase SDK modules, Conscrypt, H3, msak-android, ... |
| Mapbox Terms of Service (proprietary) | 30 | Mapbox Maps 11.3, Search 2.2 and their common/native modules |
| Android Software Development Kit License (proprietary) | 7 | `play-services-base`, `-basement`, `-location`, `-maps`, `-oss-licenses`, `-tasks`, `firebase-measurement-connector` |
| MIT | 9 | supabase-kt (`supabase-kt`, `postgrest-kt`, `gotrue-kt` and their `-android` variants), `krypto` (2), `slf4j-api` |
| BSD | 2 | `mapbox-android-gestures` ("BSD"), `hamcrest-core` ("New BSD") |
| EPL-1.0 | 1 | `junit:junit`. It shouldn't be in the APK at all; see the flags below. |
| CC0 | 1 | `reactive-streams`, pulled in by the unused Room RxJava artifacts |

## Direct dependencies

"Used?" means referenced from `app/src/main` (code or layouts), as of 9a7f0e5.

### Shipped in the app

| Dependency (declared → resolved) | License (POM) | Used? | Flag |
|---|---|---|---|
| `com.google.firebase:firebase-bom` 32.7.0 → `firebase-crashlytics` 18.6.0 | Apache-2.0 | yes | Its transitive Play services modules are proprietary (next rows), and use of Firebase is governed by Google's service terms |
| `com.google.android.gms:play-services-location` 21.3.0 | Android SDK License | yes | **Proprietary** |
| `com.google.android.gms:play-services-oss-licenses` 17.0.1 | Android SDK License | yes (About screen) | **Proprietary** |
| `com.google.android.gms:play-services-maps` 18.2.0 | Android SDK License | **no** | Proprietary and unused: remove |
| `com.mapbox.maps:android` 11.3.1 | Mapbox Terms of Service | yes | **Proprietary**; see below |
| `com.mapbox.search:mapbox-search-android` / `-ui` 2.2.0 | Mapbox Terms of Service | yes | **Proprietary**; see below |
| `com.github.CellWatch:msak-android` 0.1.3 (JitPack) | Apache-2.0 | yes | The POM license comes from JitPack (GitHub's license API). The About screen credits upstream msak (robertodauria/msak) and M-Lab's go-memoryless as Apache-2.0. Unverified upstream. |
| `lib/AndroidPing.aar` (`com.spectrum.android.ping`) | **unverified**: no license file in the AAR | yes (`ping.kt`) | The About screen says it's github.com/dburckh/AndroidPing, Apache-2.0, "Copyright 2019 Charter Communications". Confirm upstream, then use the Maven artifact if there is one, or keep the AAR with its LICENSE/NOTICE next to it. |
| `lib/DeviceInformation-release.aar` (`com.oseamiya.deviceinformation`) | **unverified**: no license file in the AAR | **no** | Remove. We shouldn't redistribute a binary with unknown terms that we don't use. |
| `io.github.jan-tennert.supabase:postgrest-kt` 1.0.3 | MIT | yes | |
| `io.ktor:ktor-client-cio` 2.3.0 | Apache-2.0 | yes | |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` 1.5.0 → 1.5.1 | Apache-2.0 | yes | |
| `com.squareup.okhttp3:okhttp` 4.10.0 | Apache-2.0 | yes | |
| `com.google.code.gson:gson` 2.10.1 | Apache-2.0 | yes | |
| `org.conscrypt:conscrypt-android` 2.5.2 | Apache-2.0 | yes | |
| `com.github.nisrulz:easydeviceinfo-base` 2.4.1 | Apache-2.0 | yes | No license problem (POM says Apache-2.0) |
| `com.uber:h3` 3.7.2 | Apache-2.0 | yes | Bundles native `libh3` |
| `commons-validator:commons-validator` 1.7 | Apache-2.0 | yes | |
| `commons-io:commons-io` 2.13.0 | Apache-2.0 | **no** | Remove |
| `com.jaredrummler:android-device-names` 2.1.1 | Apache ("Apache License", links to the repo's LICENSE) | **no** | Remove |
| `com.github.anastr:speedviewlib` 1.6.0 | Apache-2.0 | **no** | Remove |
| `androidx.room:room-runtime`, `room-ktx` 2.5.2 | Apache-2.0 | yes | |
| `androidx.room:room-rxjava2`, `room-rxjava3`, `room-guava`, `room-paging` 2.5.2 | Apache-2.0 | **no** | Remove. They pull RxJava 2/3, Guava, paging and reactive-streams (CC0) into the APK. |
| `androidx.navigation:navigation-fragment-ktx`, `navigation-ui-ktx` 2.4.2 | Apache-2.0 | **no** (the drawer is Material's `NavigationView`) | Remove |
| `androidx.datastore:datastore-preferences` 1.0.0 | Apache-2.0 | yes | |
| `androidx.work:work-runtime-ktx` 2.9.1 | Apache-2.0 | yes | |
| `androidx.lifecycle:lifecycle-viewmodel-ktx`, `-livedata-ktx` 2.6.1 → 2.6.2 | Apache-2.0 | yes | |
| `androidx.core:core-ktx` 1.7.0 and 1.13.1 → 1.13.1 | Apache-2.0 | yes | Declared twice; drop the 1.7.0 line |
| `androidx.appcompat:appcompat` 1.4.1 → 1.7.0 | Apache-2.0 | yes | |
| `com.google.android.material:material` 1.5.0 → 1.12.0 | Apache-2.0 | yes | |
| `androidx.constraintlayout:constraintlayout` 2.1.3 | Apache-2.0 | yes | |
| `com.google.android.flexbox:flexbox` 3.0.0 | Apache-2.0 | yes | |
| `androidx.test.ext:junit-ktx` 1.1.5 | Apache-2.0 | test helper | **Declared as `implementation`**, so it ships `junit` 4.13.2 (EPL-1.0) and `hamcrest-core` 1.3 (BSD) in the release APK. Change it to `androidTestImplementation`. |
| FontAwesome icons (drawables) | Font Awesome Free: icons CC BY 4.0 (unverified) | yes | Attribution is on the About screen; keep it |

### Test-only (not shipped)

| Dependency | License (POM) |
|---|---|
| `junit:junit` 4.13.2 | EPL-1.0 |
| `androidx.test.ext:junit` 1.1.3 | **unverified** (not in the cache; AndroidX test libraries are normally Apache-2.0) |
| `androidx.test.espresso:espresso-core` 3.4.0 | Apache-2.0 |
| `com.squareup.okhttp3:mockwebserver` 4.11.0 | Apache-2.0 |
| `io.mockk:mockk-android`, `mockk-agent` 1.13.8 | Apache-2.0 |
| `androidx.room:room-testing` 2.5.2 | Apache-2.0 |

### Build-time only (not shipped)

| Dependency | License (POM) | Flag |
|---|---|---|
| Android Gradle Plugin 8.1.2 | Apache-2.0 | |
| Kotlin Gradle plugin 1.8.10, serialization plugin 1.8.20, kapt | Apache-2.0 | |
| `androidx.room:room-compiler` 2.5.2 | Apache-2.0 | |
| `com.google.gms:google-services` 4.4.0 | Apache-2.0 | |
| `com.google.firebase:firebase-crashlytics-gradle` 2.9.9 | Apache-2.0 | |
| `com.google.android.gms:oss-licenses-plugin` 0.10.6 | Apache-2.0 | |
| `com.google.relay` 0.3.09 (Gradle plugin) | Android Studio License Agreement (proprietary) | Applied in `app/build.gradle`, but the repo has no Relay UI packages. Remove. |
| Gradle wrapper 8.9 | Apache-2.0 (Gradle's published license; the wrapper jar has no POM) | |

## Flags

1. **Mapbox (30 artifacts, proprietary).** Mapbox Maps and Search for Android are under the Mapbox Terms of
   Service, not an open-source license.
   - Contributors need a Mapbox account and a secret downloads token (`MAPBOX_DOWNLOADS_TOKEN`, read in
     `settings.gradle`) just to build.
   - The runtime public token is committed in `app/src/main/res/values/strings.xml` (`mapbox_access_token`).
     In a public repo, every fork's builds will bill map loads to CellWatch's Mapbox account.

   Options: move the public token into `cellwatch.properties` and rotate it before the repo goes public; or
   replace Mapbox with MapLibre Native (BSD-2-Clause, unverified) and an open tile source. The latter is also
   what a GPL license would need (see below).
2. **Google Play services / Firebase (proprietary).** `play-services-location`, `-oss-licenses` and the
   transitive `-base`/`-basement`/`-tasks` are under the Android SDK License. Firebase Crashlytics' own code is
   Apache-2.0, but it needs those modules and a Firebase project. Builds without them aren't possible today:
   Crashlytics is "required" in README.md, and location uses the fused provider. Distributors that require a
   fully free build, such as F-Droid, would reject the app as it stands.
3. **Junk in the APK.** Fixing `junit-ktx`'s scope and removing the unused artifacts (`play-services-maps`,
   `commons-io`, `android-device-names`, `speedviewlib`, Room RxJava/Guava/Paging, navigation, the
   DeviceInformation AAR, the Relay plugin) leaves fewer licenses to comply with: no EPL-1.0, no CC0, and one
   fewer proprietary module. Removing them is a separate code change, not part of this review.
4. **Unverified binaries.** `lib/AndroidPing.aar` needs its upstream license confirmed and its LICENSE/NOTICE
   kept with it.
5. **M-Lab terms (not a code license).** Tests run against M-Lab's servers, and M-Lab publishes the data it
   collects. Check M-Lab's current acceptable-use and data policy and confirm the in-app disclosure matches it
   (unverified here).
6. **Notices.** Apache-2.0 requires passing on NOTICE files. The `oss-licenses-plugin` screen covers Maven
   dependencies. The About text covers msak, go-memoryless, AndroidPing and FontAwesome, which the plugin
   can't see. Keep both up to date.
7. **Tracked config files with keys.** Separate from licensing, but the release depends on it:
   - `app/cellwatch.properties` is tracked and not read by the build (the root `cellwatch.properties` is). It
     contains the production Supabase URL and anon key, and points `SUPABASE_LOCAL_URL` at production.
   - `app/google-services.json` is tracked and contains the Firebase API key.
   - Both keys are publishable by design, but confirm each one before publishing, and delete
     `app/cellwatch.properties`.
   - On `origin/feature-ios-support`, `frozenApp/cellwatch.properties` and `frozenApp/google-services.json` are
     tracked too.

## What each candidate license implies

| | Apache-2.0 | MIT | GPL-3.0 |
|---|---|---|---|
| Compatible with the Apache-2.0, MIT, BSD and CC0 dependencies | Yes | Yes | Yes. Apache-2.0 code can be combined into GPL-3.0 works. |
| EPL-1.0 (`junit`) in the APK | Fine | Fine | Incompatible, but it goes away once `junit-ktx`'s scope is fixed |
| Proprietary Mapbox and Play services SDKs in the distributed APK | Allowed. Our license covers our code; the SDKs keep their own terms. | Allowed, same as Apache-2.0 | **Conflict.** They aren't "System Libraries", so distributing the combined APK needs an additional permission (a GPL §7 linking exception for these SDKs), or the SDKs have to be replaced (MapLibre, platform `LocationManager`, no Crashlytics or an open alternative) |
| Patent grant from contributors | Explicit | None | Explicit |
| Obligations on redistributors | Keep notices, state changes | Keep the copyright notice | Release source under GPL-3.0 |
| Others can ship closed-source derivatives | Yes | Yes | No |

**Remaining human tasks for #107:**
- file the GT software disclosure;
- choose the license, @jeremy303 (with GT OTL);
- decide on the Mapbox token and Mapbox vs MapLibre;
- confirm AndroidPing's license;
- approve removing the unused dependencies and the tracked `app/cellwatch.properties`.
