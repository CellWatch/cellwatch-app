# Kotlin Multiplatform: what can be shared and what must be rewritten

Inventory for #88, taken from `main` at 9a7f0e5 (88 Kotlin files, about 8,000 lines, all under
`app/src/main/java/edu/gatech/cc/cellwatch/`). `lib/` has no Kotlin. It holds two prebuilt AARs, covered under
"Libraries that block sharing".

Jeff's `origin/feature-ios-support` branch already covers much of this. That branch was read with `git log` and
`git ls-tree` only, so the "Counterpart" column matches by file name, not by reading the code. On that branch:
- `shared/` is a KMP module with `commonMain`, `androidMain`, `iosMain` and `jvmMain`, and SQLDelight in place
  of Room;
- `androidTestApp/` and `iosTestApp/` are the new app shells (Kotlin screens and Swift `*ScreenViewController`s);
- the current app is frozen in `frozenApp/` and isn't built.

The branch forked from `main` in Aug 2025, before #111, #112 and c51980e. Those fixes still need porting (see
doc/branching.md).

## Categories

| Category | Meaning |
|---|---|
| **Shared** | Pure Kotlin. Moves to `commonMain` with mechanical edits only (list below). |
| **Shared + platform** | The logic is shared. A platform API sits behind an `expect`/`actual` or an injected interface, with one implementation per platform. |
| **Storage** | Room code. Replace with SQLDelight (the branch's choice) or Room's KMP support (Room 2.7+; check before relying on it). |
| **Platform UI** | Android Views, Fragments and Activities. Rewrite per platform (SwiftUI/UIKit on iOS; Compose or Views on Android), keeping screen state in shared view models. |
| **Android-only** | A platform service with no shared logic. iOS needs its own design. |
| **Delete** | Unused. |

The mechanical edits for **Shared** files are:
- `java.util.UUID` (26 files): use `kotlin.uuid.Uuid` (Kotlin 2.0.20+, opt-in) or keep ids as `String`;
- `java.io.IOException`: use `kotlinx.io.IOException`;
- Gson: use kotlinx.serialization;
- `BuildConfig.*`: inject a config object;
- `Context`/`R.string`: move out to the UI layer.

## Inventory

Paths are relative to `app/src/main/java/edu/gatech/cc/cellwatch/`.

### App entry and utilities

| File | Lines | Category | Why | iOS / shared counterpart |
|---|---|---|---|---|
| `CellWatchApp.kt` | 66 | Android-only | `Application` subclass. Installs Conscrypt, builds the lazy object graph, creates the notification channel, builds the User-Agent. | Graph: `shared/.../domain/app/ProductContainer.kt`, `IosProductContainer.kt`, `androidTestApp/.../AndroidProductContainer.kt` |
| `core/util/ContactInfoValidator.kt` | 22 | Shared | Pure logic, but uses Apache commons-validator (`EmailValidator`, JVM-only). Replace it with a regex or a KMP validator. | `commonMain/.../core/util/ContactInfoValidator.kt` |
| `core/util/Encryptor.kt` | 67 | Shared + platform | AES-GCM with a key in Android Keystore; protects the device secret. iOS: Keychain. | `commonMain/.../Encryptor.kt` + `SecureKeyStore.{android,ios,jvm}.kt` |
| `core/util/HttpUtil.kt` | 12 | Shared | Regex over an exception string. | `commonMain/.../HttpUtil.kt` |
| `core/util/Log.kt` | 84 | Shared + platform | `android.util.Log` and Crashlytics behind a logger. Secret redaction (#112) is pure Kotlin and should be shared. Also wires msak's logger. | `SharedLog.{android,ios,jvm}.kt` + `commonMain/.../Log.kt` (check that #112's redaction is ported) |
| `core/util/PermissionManager.kt` | 29 | Shared + platform | `ContextCompat.checkSelfPermission`. iOS: `CLLocationManager` authorisation. `READ_PHONE_STATE` has no iOS equivalent. | `commonMain/.../PermissionManager.kt` |
| `core/util/SingletonHolder.kt` | 27 | Shared | Generic lazy holder. Only `CellWatchDatabase` uses it, so it can go with Room. | `commonMain/.../SingletonHolder.kt` |
| `core/util/setCopyOnClick.kt` | 25 | Platform UI | `View` extension using `ClipboardManager`. iOS: `UIPasteboard`. | `commonMain/.../setCopyOnClick.kt` exists on the branch; check what it contains |

### Data: repositories, sync, settings

| File | Lines | Category | Why | iOS / shared counterpart |
|---|---|---|---|---|
| `data/core/repositories/MeasurementRepository.kt` | 227 | Shared + platform | Wires the Room DAOs, the Supabase datasource and the uploader. Fetches the TCP tuple with OkHttp and Gson from `BuildConfig.TCP_TUPLE_URL`. Replace those with Ktor, kotlinx.serialization and injected config. | `data/repo/MeasurementRepositoryImpl.kt`, `domain/sync/HttpTcpTupleProvider.kt` + `HttpGetText.{android,ios,jvm}.kt` |
| `data/core/repositories/SettingsRepository.kt` | 105 | Shared | Logic over `LocalDataStore` (device id, contact info, collection mode). | `domain/runtime/RuntimeProfileStore.kt`, `domain/onboarding/OnboardingPersistenceUseCase.kt` |
| `data/core/sync/MeasurementUploader.kt` | 162 | Shared | Already pure Kotlin behind `Local`/`Remote`/`Log` interfaces (#111), and unit-tested on the JVM. Needs the `IOException` swap only. | `domain/sync/MeasurementSyncService.kt`, `MeasurementSyncUseCase.kt`, `PendingSyncUseCases.kt`. The branch predates #111: port its rules (duplicate means uploaded; at most 3 in flight; one run at a time; submissions wait for all three measurements). |
| `data/core/sync/UploadWorker.kt` | 38 | Android-only | WorkManager. iOS: `BGTaskScheduler`, or upload when the app comes to the foreground and after each test. | `domain/sync/UploadTriggerUseCase.kt` (shared trigger policy) |
| `data/datastore/LocalDataStore.kt` | 123 | Shared + platform | Jetpack DataStore Preferences (1.0.0, Android-only; DataStore 1.1+ is KMP). The device secret goes through `Encryptor`. | `data/remote/PersistentDeviceAuthStore.kt` + `Android/IosDeviceCredentialStorage.kt`, `RuntimeProfileStore.kt` |

### Data: Room (local database)

| File | Lines | Category | Why | iOS / shared counterpart |
|---|---|---|---|---|
| `data/local/CellWatchDatabase.kt` | 89 | Storage | Room database, schema version 18, with auto and manual migrations (schemas 15-18 in `app/schemas`). | SQLDelight `.sq` files in `shared/src/commonMain/sqldelight/` |
| `data/local/dao/CellDao.kt` | 13 | Storage | Room DAO | `Cell.sq`, `data/repo/CellRepositoryImpl.kt` |
| `data/local/dao/FccSubmissionDao.kt` | 42 | Storage | Room DAO, Flow queries | `FccSubmission.sq`, `FccSubmissionRepositoryImpl.kt` |
| `data/local/dao/LatencyDataDao.kt` | 23 | Storage | Room DAO | `LatencyData.sq`, `LatencyDataRepositoryImpl.kt` |
| `data/local/dao/LocationDao.kt` | 15 | Storage | Room DAO | `Location.sq`, `LocationRepositoryImpl.kt` |
| `data/local/dao/MeasurementDao.kt` | 91 | Storage | Room DAO with `@Transaction` relations | `Measurement.sq`, `MeasurementRepositoryImpl.kt` |
| `data/local/dao/UploadDownloadDataDao.kt` | 23 | Storage | Room DAO | `UploadDownloadData.sq`, `UploadDownloadDataRepositoryImpl.kt` |
| `data/local/model/CellEntity.kt` | 75 | Storage | `@Entity` | generated by SQLDelight + `data/mappers/CellMappers.kt` |
| `data/local/model/ChallengeDataEntity.kt` | 27 | Storage | `@Entity` | `ChallengeData.sq`, `ChallengeDataMappers.kt` |
| `data/local/model/DeviceEntity.kt` | 23 | Storage | `@Entity` | `Device.sq`, `DeviceMappers.kt` |
| `data/local/model/FccSubmissionEntity.kt` | 83 | Storage | `@Entity` | `FccSubmissionMappers.kt` |
| `data/local/model/FccSubmissionWithMeasurements.kt` | 43 | Storage | `@Relation` holder | SQL join + mapper |
| `data/local/model/LatencyDataEntity.kt` | 45 | Storage | `@Entity` | `LatencyDataMappers.kt` |
| `data/local/model/LocationEntity.kt` | 59 | Storage | `@Entity` | `LocationMappers.kt` |
| `data/local/model/MeasurementEntity.kt` | 113 | Storage | `@Entity` | `MeasurementMappers.kt` |
| `data/local/model/MeasurementWithData.kt` | 75 | Storage | `@Relation` holder | SQL join + mapper |
| `data/local/model/UploadDownloadDataEntity.kt` | 61 | Storage | `@Entity` | `UploadDownloadDataMappers.kt` |
| `data/local/util/Converters.kt` | 36 | Storage | Room type converters, with Gson for string lists | SQLDelight column adapters, `data/mappers/SqlEncoding.kt` |

**Migrating existing installs:** phones in the field keep unsynced measurements in the Room database (version
18). If the multiplatform Android app switches to SQLDelight, it must open or import that database on first
launch. Otherwise unsynced tests, including FCC submissions, are lost. Test the upgrade from a real v18 database.

### Data: domain models and network DTOs

| File | Lines | Category | Why | iOS / shared counterpart |
|---|---|---|---|---|
| `data/model/Cell.kt` | 84 | Shared | Data class + mappers to the entity and DTO. The entity mapper moves to the storage layer. | `domain/model/Cell.kt` |
| `data/model/ChallengeData.kt` | 28 | Shared | Data class | `domain/model/ChallengeData.kt` |
| `data/model/CollectionMode.kt` | 5 | Shared | Enum | `domain/model/CollectionMode.kt` |
| `data/model/Device.kt` | 18 | Shared | Data class | `domain/model/Device.kt` |
| `data/model/FccExportExtensions.kt` | 267 | Shared | Builds the BDC JSON export, using kotlinx.serialization only. | `domain/export/FccSubmissionExport.kt`, `ExtendedExport.kt` |
| `data/model/FccSubmission.kt` | 115 | Shared | Data class + mappers | `domain/model/FccSubmission.kt` |
| `data/model/LatencyData.kt` | 31 | Shared | Data class | `domain/model/LatencyData.kt` |
| `data/model/Location.kt` | 50 | Shared | Data class | `domain/model/Location.kt` |
| `data/model/Measurement.kt` | 230 | Shared | Data class + mappers. `displayValue(Context)` uses `R.string`: move it to the UI. | `domain/model/Measurement.kt` |
| `data/model/MeasurementGroup.kt` | 66 | Shared | Data class | `domain/model/MeasurementGroup.kt` |
| `data/model/TcpTuple.kt` | 7 | Shared | Data class | `domain/model/TcpTuple.kt` |
| `data/model/UploadDownloadData.kt` | 56 | Shared | Data class | `domain/model/UploadDownloadData.kt` |
| `data/network/NetworkMeasurementDatasource.kt` | 160 | Shared + platform | supabase-kt (multiplatform) and a Ktor client plugin for the device headers. Needs injected config instead of `BuildConfig`, a supabase-kt upgrade (see below), and the Darwin Ktor engine on iOS. | `data/remote/SupabaseMeasurementSyncRemoteDataSource.kt` |
| `data/network/NetworkRestResult.kt` | 13 | Delete | No references. | n/a |
| `data/network/model/NetworkCell.kt` | 89 | Shared | `@Serializable` DTO | `data/transport/NetworkCell.kt` |
| `data/network/model/NetworkChallengeData.kt` | 30 | Shared | DTO | `data/transport/NetworkChallengeData.kt` |
| `data/network/model/NetworkFccSubmission.kt` | 135 | Shared | DTO | `data/transport/NetworkFccSubmission.kt` |
| `data/network/model/NetworkFccSubmissionWithMeasurements.kt` | 49 | Shared | DTO | n/a (not seen) |
| `data/network/model/NetworkLatencyData.kt` | 35 | Shared | DTO | `data/transport/NetworkLatencyData.kt` |
| `data/network/model/NetworkLocation.kt` | 50 | Shared | DTO | `data/transport/NetworkLocation.kt` |
| `data/network/model/NetworkMeasurement.kt` | 144 | Shared | DTO | `data/transport/NetworkMeasurement.kt` |
| `data/network/model/NetworkMeasurementWithData.kt` | 56 | Shared | DTO for `insert_measurement` | `data/transport/NetworkMeasurementWithData.kt` |
| `data/network/model/NetworkUploadDownloadData.kt` | 57 | Shared | DTO | `data/transport/NetworkUploadDownloadData.kt` |

### Domain: measurement engine and platform capabilities

| File | Lines | Category | Why | iOS / shared counterpart |
|---|---|---|---|---|
| `domain/fcc/LatencyResult.kt` | 14 | Shared | Data class | n/a |
| `domain/fcc/ThroughputMetrics.kt` | 21 | Shared | Data class | n/a |
| `domain/fcc/ThroughputResult.kt` | 13 | Shared | Data class | n/a |
| `domain/fcc/UnreachableServer.kt` | 13 | Shared + platform | Subclass of msak's `Server`, so it depends on the msak types. | Server types in `MsakServerSelector.kt` |
| `domain/fcc/LatencyTest.kt` | 91 | Shared + platform | Drives msak-android's latency test (Android library). iOS needs its own msak latency client. | `MsakMeasurementExecutorPlatform.{android,ios,jvm}.kt` |
| `domain/fcc/ThroughputTest.kt` | 278 | Shared + platform | Drives msak-android's WebSocket throughput streams (OkHttp + Conscrypt byte counting) and uses `android.os.Handler`. The metric arithmetic can be shared; the transport can't. | same as above |
| `domain/fcc/MeasurementManager.kt` | 250 | Shared + platform | Runs the sequence (locate servers, latency, download, upload) and builds the `FccSubmission`. Uses msak `LocateManager`, `ping`, `EasyAppMod`, `TelephonyInfoManager`, `BuildConfig`. | `MeasurementSequenceOrchestrator.kt`, `MsakServerSelector(+Platform).kt`, `FccSubmissionPolicy.kt` |
| `domain/fcc/MeasurementTest.kt` | 145 | Shared + platform | Base class. Gets location (Fused Location Provider), device info (`EasyDeviceMod`, `Build.MODEL`) and telephony snapshots around each test. iOS: CoreLocation, `UIDevice`, `utsname`. | `domain/capability/*LocationSampleBridge.kt`, `PlatformCapabilitySnapshot.kt` |
| `domain/fcc/MeasurementService.kt` | 186 | Android-only | Foreground service with a wake lock and notification, so a test survives the screen turning off. iOS has no equivalent: run tests in the foreground or under the location background mode, and decide this explicitly. | none seen |
| `domain/fcc/ping.kt` | 42 | Shared + platform | ICMP through `lib/AndroidPing.aar`. Used only to pick a throughput server. **Note:** `chooseMsakServers` uses `maxBy { ping(...) }`, which picks the server with the highest RTT. That looks like a bug; check it before porting. | `MsakServerSelectorPlatform.{android,ios,jvm}.kt` |
| `domain/map/managers/H3Manager.kt` | 93 | Shared + platform | Uber H3 (Java with JNI) plus Mapbox `Point` conversion. Share the H3 indexing behind expect/actual (H3 C library on iOS); keep the Mapbox conversion in the map UI. | `domain/maphome/H3Grid.{android,ios,jvm}.kt`, `H3Index.kt` |
| `domain/telephony/managers/TelephonyInfoManager.kt` | 467 | Shared + platform | `TelephonyManager`, `CellInfo*`, `ConnectivityManager`. iOS exposes much less (`CTTelephonyNetworkInfo` radio technology, `NWPathMonitor`). Spec §3.1.1 lets iOS challenge data leave most cell and network fields null, but `provider_name` stays required. Apple deprecated `CTCarrier` in iOS 16, so verify how iOS gets a carrier name. | `domain/capability/AndroidPlatformCapabilityProvider.kt`, `IosPlatformCapabilityProvider.kt`, `IosNetworkPath.kt` |

### UI

All of these are **Platform UI**. The view models' state logic can move to shared classes, as the branch does.

| File | Lines | Why | iOS / shared counterpart |
|---|---|---|---|
| `ui/main/MapActivity.kt` | 803 | Mapbox Maps and Search, H3 overlay, drawer. The largest file. Mapbox has separate Android and iOS SDKs. | `MapHomeScreen.kt` / `MapHomeScreenViewController.swift`; shared `domain/maphome/*` |
| `ui/main/MapViewModel.kt` | 56 | `androidx.lifecycle.ViewModel` | `domain/maphome/MapHomeViewModel.kt` |
| `ui/main/MeasureActivity.kt` | 123 | Binds to `MeasurementService` | `MeasurementRunScreen*` |
| `ui/main/MeasureFragment.kt` | 90 | Progress UI | `MeasurementRunScreen*`; `domain/measurementrun/*` |
| `ui/main/PreMeasureFragment.kt` | 152 | Permission and settings checks before a test | `MeasurementStartScreen*`; `domain/measurementstart/*Preflight*` |
| `ui/main/MeasureViewModel.kt` | 161 | Service state, upload status | `MeasurementRunViewModel.kt`, `MeasurementStartViewModel.kt` |
| `ui/main/MeasureHistoryActivity.kt` | 106 | List and export (Storage Access Framework) | `HistoryScreen*`, `ExportScreen*` |
| `ui/main/MeasureHistoryViewModel.kt` | 67 | Loads groups; writes the export to a `FileDescriptor`. The export building is already shared (`FccExportExtensions.kt`). | `domain/measurementhistory/*`, `domain/export/*` |
| `ui/main/MeasurementAdapter.kt` | 50 | RecyclerView adapter | platform list |
| `ui/main/MeasurementGroupItem.kt` | 187 | Custom view (Flexbox) | platform list cell |
| `ui/main/MeasurementGroupItemViewModel.kt` | 6 | Trivial | n/a |
| `ui/main/MeasurementItem.kt` | 87 | Custom view | platform list cell |
| `ui/main/NavigationDrawer.kt` | 58 | Drawer menu | `domain/navigation/Navigator.kt`, `Destination.kt` |
| `ui/main/OnboardingActivity.kt` | 218 | Onboarding flow host | `OnboardingScreen*`; `domain/onboarding/*`, `domain/consent/*` |
| `ui/main/SettingsActivity.kt` | 176 | Contact info, collection mode, device id, app version | `SettingsScreen*`; `domain/settings/SettingsProfileViewModel.kt` |
| `ui/main/AboutActivity.kt` | 35 | Opens Google's OSS licenses screen (`play-services-oss-licenses`, Android-only). iOS needs its own acknowledgements screen. | none seen |
| `ui/onboarding/CollectionModeFragment.kt` | 64 | Onboarding step | `CollectionChoiceScreen*` |
| `ui/onboarding/DataUseFragment.kt` | 26 | Onboarding step | `DataUseScreen*` |
| `ui/onboarding/FCCInfoFragment.kt` | 94 | Contact form, validation | `OnboardingScreen*`, `OnboardingValidationUseCase.kt` |
| `ui/onboarding/HomeFragment.kt` | 28 | Onboarding step | `OnboardingScreen*` |
| `ui/onboarding/ReadMoreFragment.kt` | 33 | Onboarding step | `OnboardingScreen*` |
| `ui/onboarding/SettingsSetupFragment.kt` | 76 | Permission requests | `OnboardingScreen*` |

Totals (88 files): Shared 29, Shared + platform 14, Storage 18, Platform UI 23, Android-only 3, Delete 1.

## Libraries that block sharing

| Library (version on `main`) | Used for | KMP status | Plan |
|---|---|---|---|
| Room 2.5.2 | Local database | Android-only at this version. Room 2.7+ supports KMP (verify before relying on it). | SQLDelight (branch) or Room KMP, plus the install-upgrade path above |
| DataStore Preferences 1.0.0 | Settings, device secret | Android-only at 1.0; 1.1+ is KMP (verify) | Upgrade, or a KMP settings library; secrets in Keystore/Keychain |
| WorkManager 2.9.1 | Background upload | Android-only | Keep on Android; iOS uses `BGTaskScheduler` or foreground triggers |
| supabase-kt `postgrest-kt` 1.0.3 + Ktor 2.3 CIO | Upload, device registration | supabase-kt is multiplatform, but 1.0.3 is old. Use the Darwin engine on iOS. | Upgrade supabase-kt and Ktor together. Re-check the JSON settings (`encodeDefaults = false`, `ignoreUnknownKeys = true` in 1.0.3), because inserts depend on omitted nulls. |
| OkHttp 4.10 | TCP tuple GET; msak transport | JVM/Android only | Ktor for the tuple; msak stays platform-specific |
| Gson 2.10 | Tuple JSON, Room converter | JVM-only | kotlinx.serialization |
| msak-android 0.1.3 (CellWatch fork, JitPack) | Throughput and latency tests | Android library (OkHttp WebSockets, `android.*` logging). On TLS, its byte counting relies on the app installing Conscrypt. | Keep on Android; write an iOS msak client (WebSocket throughput + latency protocol). The biggest single piece of new iOS work. |
| Conscrypt 2.5.2 | Lets msak count bytes on TLS sockets | Android-only | Android `actual` only |
| `lib/AndroidPing.aar` (`com.spectrum.android.ping`) | Server selection | Android-only | Replace with an RTT probe both platforms can run (e.g. the msak latency endpoint), and fix `maxBy` |
| Uber H3 3.7.2 | Map hexagons | Java with JNI | H3 C library via cinterop on iOS (branch: `H3Grid.ios.kt`) |
| Mapbox Maps 11.3 / Search 2.2 | Map, place search | Separate proprietary Android and iOS SDKs | Platform UI on each side; check the token and licence terms for iOS |
| Play services location 21.3 | Location fixes | Android-only | CoreLocation on iOS |
| easydeviceinfo 2.4.1 | Manufacturer, OS version, app version | Android-only | `Build.*` / `PackageManager` on Android; `UIDevice` / `Bundle` on iOS |
| commons-validator 1.7 | Email validation | JVM-only | Regex in common code |
| Firebase Crashlytics (BoM 32.7.0) | Crash and log reporting | Separate Android and iOS SDKs; no first-party KMP API | expect/actual logger |
| `play-services-oss-licenses` | About screen | Android-only | iOS acknowledgements screen |
| Kotlin 1.8.10 / AGP 8.1.2 | Build | KMP with current iOS targets needs a newer Kotlin | The branch uses its own `.kts` build; not inspected |

`com.jaredrummler:android-device-names`, `com.github.anastr:speedviewlib`, `commons-io`, `play-services-maps`,
the Room RxJava2/RxJava3/Guava/Paging artifacts and `lib/DeviceInformation-release.aar` are declared but not
referenced in `app/src/main`. Don't port them. doc/open-source-dependency-review.md covers removing them.

## Suggested order

1. **Port the FCC-relevant fixes into the branch first:** #111 upload rules, c51980e carrier fallback, #112 log
   redaction. They're small, shared-logic changes, and doing them first keeps the branch from regressing what
   `main` fixed.
2. **Models, DTOs and export** (Shared). Mostly done on the branch. Diff the branch's DTOs against `main`'s
   `data/network/model/*` field by field, since the server schema is shared.
3. **Storage**, including the Room v18 upgrade path. This blocks a release of the multiplatform Android app,
   because it decides whether existing installs keep their unsynced tests.
4. **Sync and credentials:** upgrade supabase-kt, keep the device id and secret in Keystore/Keychain, and
   carry the existing device id and secret over on Android so devices don't re-register.
5. **Measurement engine:** an msak executor per platform and a server-selection fix. This needs FCC-facing
   review, since it changes the measurement methodology (see README.md, "Versioning").
6. **Platform capabilities:** telephony, location and connectivity, checked against the iOS-optional list in
   spec §3.1.1. `provider_name` has to have a real value on both platforms.
7. **Background execution:** keep the Android foreground service and WorkManager; decide and document the iOS
   behaviour.
8. **UI** per platform, last, on top of shared view models.
