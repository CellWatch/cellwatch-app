# FCC Reporting Requirements: Platform Gap Analysis

## Purpose

This document compares:

1. FCC mobile speed test reporting requirements for iOS from `/Users/jeff/Projects/cellwatch-app/doc/bdc-mobile-speed-test-data-specifications.pdf`
2. What the current KMP app captures from MSAK measurements
3. What the Supabase layer exports to the FCC submission payload

The key question is not whether the app directly constructs FCC JSON. It does not. The FCC-shaped payload is built in Supabase by `private.fcc_submissions_view` and posted by `private.post_fcc_submission`.

Relevant SQL:

- `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:217`
- `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1402`

## Executive Summary

Current status: **not fully aligned with FCC platform capture requirements**.

The Supabase export layer is structurally close to the FCC JSON schema, but the KMP platform capture paths are still missing or mis-populating several required upstream values.

The biggest problems are:

1. iOS location capture is best-effort only and currently depends on runtime permission plus availability of a recent Core Location sample.
2. iOS telephony capture is only partially wired: radio access technology is captured best-effort, but carrier identity and cell-level detail remain unavailable.
3. Android location capture is best-effort only and still depends on runtime permission plus availability of a recent platform location sample.
4. `provider_name` is currently populated from the wrong source on both platforms.
5. `app_version` is currently populated from the wrong source on both platforms.
6. `app_name` and `device_id` are at risk of being null in FCC submissions.
7. `server_source_port` appears unpopulated.
8. Download/upload warmup fields are modeled but not captured from MSAK.
9. Android telephony capture is materially better than iOS, but still incomplete relative to FCC richness.

## Status After App-Layer Remediation Slice

### Fixed In App

The current app-layer remediation slice corrected the following issues without changing Supabase or the MSAK library:

1. FCC submissions no longer use `appSource` as `provider_name`.
2. FCC submissions no longer use `appSource` as `app_version`.
3. MSAK server hostnames are no longer injected as the measurement/provider source on Android, iOS, or JVM executor paths.
4. FCC submission creation now accepts a real app-owned submission profile carrying:
   - `app_name`
   - `app_version`
   - `device_id`
   - challenge contact info
5. Android FCC submission assembly now relies on telephony-enriched provider data instead of the MSAK server machine name.
6. Android and iOS app-layer run flows now pass real persisted onboarding contact info plus real app/device metadata into FCC submission assembly.
7. Shared FCC submission validation now blocks malformed app-owned FCC submissions before they are inserted for sync.
8. The repository-backed measurement result store now persists `locations` and `cells` alongside measurements, so captured FCC support data is no longer dropped before sync.
9. Android location capture is now wired as a best-effort active/current location snapshot when the harness receives a callback sample, with fallback to the most recent last-known provider sample when permission is granted.
10. iOS location capture is now wired as a best-effort current-location snapshot when permission is granted and CoreLocation already has a sample.
11. iOS telephony capture now records best-effort CoreTelephony radio access technology and maps it into FCC-facing `network_generation` / `network_subtype` fields when Apple still exposes that value.
12. Android cell capture now records additional LTE/NR radio metrics when the platform exposes them, including `rsrp`, `rsrq`, `sinr`, LTE `cqi`, and NR `csi_*` values.

### Deferred App-Layer Gaps

These remain app-layer gaps after the current slice:

1. iOS telephony remains partial only; carrier identity and cell-level detail are still not wired.
2. iOS location capture is opportunistic only; it is not yet an active or guaranteed fresh location collection flow.
3. Android location capture is opportunistic only; it is not yet a guaranteed fresh location collection flow and still depends on platform/provider availability.
4. Android cell richness is still partial for several FCC radio-detail fields such as `cqi`, `spectrum_band`, and `spectrum_bandwidth`, and for legacy technologies where Android does not expose equivalent metrics through the current shared adapter.
5. Android `device_imei` and `device_tac` ownership/population is still unresolved.

### Blocked By MSAK

These remain blocked by the current MSAK output shape:

1. `warmup_duration`
2. `warmup_bytes_transferred`

The KMP model has fields for these values, but the current MSAK throughput summaries do not expose them.

### Blocked By Supabase / Server-Side Export

These remain outside the app-only remediation slice:

1. `server_source_port`
2. Any changes to FCC JSON naming or transform logic inside `private.fcc_submissions_view`
3. Any changes to `private.post_fcc_submission`

Those fields and transforms are owned by `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql`, which was intentionally left untouched in this slice.

## Platform Feasibility Review

This section separates data that a normal mobile app can still plausibly collect from data that is now effectively blocked by platform policy or deprecation.

### iOS Feasibility

#### Feasible in app layer

1. Location capture is feasible in a normal app through Core Location, subject to runtime permission and user/device availability.
2. Device metadata such as model and OS version is feasible through `UIDevice`.
3. Best-effort radio access technology reporting is still technically available through `CTTelephonyNetworkInfo`, though the API surface is increasingly deprecated.

Relevant Apple documentation:

- `CLLocationManager` still supports `authorizationStatus`, `requestWhenInUseAuthorization()`, `requestLocation()`, and `location`:
  - [Apple CLLocationManager](https://developer.apple.com/documentation/corelocation/cllocationmanager)
- `CTTelephonyNetworkInfo.currentRadioAccessTechnology` is deprecated in favor of service-scoped radio access technology:
  - [Apple currentRadioAccessTechnology](https://developer.apple.com/documentation/coretelephony/cttelephonynetworkinfo/currentradioaccesstechnology)

#### Not realistically feasible for a normal app

1. Carrier/provider identity via `CTCarrier` is no longer a stable long-term basis for FCC reporting.
   - `CTCarrier` is deprecated with no replacement.
   - `mobileCountryCode` and `mobileNetworkCode` are deprecated and documented as returning placeholder values such as `65535` at some point.
2. Per-cell metadata and signal metrics are not exposed through a normal public iOS app API comparable to Android `CellInfo`.
3. Standard public iOS APIs do not provide the FCC-style cell richness that Android can expose.

Relevant Apple documentation:

- `CTCarrier` is deprecated with no replacement:
  - [Apple CTCarrier](https://developer.apple.com/documentation/coretelephony/ctcarrier)
- `mobileCountryCode` deprecation notes the placeholder behavior:
  - [Apple mobileCountryCode](https://developer.apple.com/documentation/coretelephony/ctcarrier/mobilecountrycode)
- `mobileNetworkCode` deprecation notes the placeholder behavior:
  - [Apple mobileNetworkCode](https://developer.apple.com/documentation/coretelephony/ctcarrier/mobilenetworkcode)

Conclusion for iOS:

- A fresher location flow is still app-owned and feasible.
- Best-effort radio access technology is feasible and now wired, but it is not a path to full FCC-grade carrier/cell parity.
- FCC-grade telephony/cell reporting parity with Android is not realistically achievable in a normal iOS app using current public APIs.

### Android Feasibility

#### Feasible in app layer

1. Carrier name, MCC/MNC, network generation, network subtype, roaming, and cell information are still feasible through `TelephonyManager` and `CellInfo`, subject to runtime permission and device/network support.
2. Additional LTE/NR radio metrics such as `rsrp`, `rsrq`, `sinr`, and NR `csi_*` are feasible when exposed by the device API level and modem stack.
3. Best-effort location capture is feasible through `LocationManager`, subject to runtime permission and sample availability.

Relevant Android documentation:

- `TelephonyManager` and `CellInfo` remain available to normal apps for non-persistent telephony and cell information:
  - [Android TelephonyManager](https://developer.android.com/reference/android/telephony/TelephonyManager)

#### Not realistically feasible for a normal app

1. IMEI is not generally available to a normal Play-distributed app targeting Android 10+.
   - Official docs say `getImei()` requires privileged or special roles/permissions such as:
     - `READ_PRIVILEGED_PHONE_STATE`
     - device/profile owner
     - carrier privileges
     - default SMS role
     - `USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER`
2. TAC depends on IMEI availability in this architecture.
   - Without a legitimate IMEI source, TAC is also not realistically available.
3. This means `device_imei` and `device_tac` should be treated as blocked for a normal deployed Android app unless product requirements explicitly move the app into one of the special entitlement/ownership categories above.

Relevant Android documentation:

- `TelephonyManager.getImei()` restrictions:
  - [Android TelephonyManager getImei](https://developer.android.com/reference/android/telephony/TelephonyManager.html)
- AndroidX compatibility reference summarizing the Android 10+ restrictions:
  - [AndroidX TelephonyManagerCompat.getImei](https://developer.android.com/reference/androidx/core/telephony/TelephonyManagerCompat)

Conclusion for Android:

- We should keep improving standard telephony/cell/location capture in-app where APIs already expose it.
- We should not plan on shipping real IMEI/TAC capture in a normal consumer Android build without an explicit product/platform decision that changes app privileges or deployment model.

## FCC iOS Exceptions From the BDC Spec

The PDF explicitly allows a reduced field set for iOS. The iOS-nullable exceptions include:

- Submission object:
  - `device_tac`
  - `net_mobile_country_code`
  - `net_mobile_network_code`
  - `sim_mobile_country_code`
  - `sim_mobile_network_code`
- Download / Upload / Latency test object:
  - `carrier_aggregation_flag`
  - `network_connected_flag`
  - `network_available_flag`
  - `network_roaming_flag`
- Cell object:
  - `physical_cell_id`
  - `cell_connection`
  - `signal_strength`
  - `rssi`
  - `rsrp`
  - `rsrq`
  - `sinr`
  - `csi_rsrp`
  - `csi_rsrq`
  - `csi_sinr`
  - `cqi`
  - `spectrum_band`
  - `spectrum_bandwidth`
  - `arfcn`

These iOS exceptions appear in the PDF pages covering crowdsourced and challenge submissions:

- `/Users/jeff/Projects/cellwatch-app/doc/bdc-mobile-speed-test-data-specifications.pdf` page 7
- `/Users/jeff/Projects/cellwatch-app/doc/bdc-mobile-speed-test-data-specifications.pdf` page 10

Important implication: iOS is allowed to omit some fields, but **not all**. The FCC still expects the rest of the challenge submission structure to be populated.

## How FCC Submission Works In This Project

The flow is:

1. The KMP app captures measurements and syncs internal rows to Supabase.
2. Supabase stores:
   - measurements
   - upload/download data
   - latency data
   - locations
   - cells
   - fcc_submissions
3. Supabase builds the FCC JSON payload in `private.fcc_submissions_view`.
4. Supabase posts that payload in `private.post_fcc_submission`.

Relevant schema and functions:

- Measurements table:
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:509`
- Upload/download table:
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:550`
- Latency table:
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:478`
- Locations table:
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:494`
- Cells table:
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:451`
- FCC submissions table:
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1365`
- FCC export view:
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1402`
- FCC post function:
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:217`
- Auto-submit trigger:
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:145`

## What Supabase Exports To FCC

The Supabase FCC export view already maps internal storage to FCC field names.

Examples:

- locations:
  - `timestamp`
  - `latitude`
  - `longitude`
  - `horizontal_accuracy`
  - `speed`
  - `speed_accuracy`
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1403`
- cells:
  - `cell_id`
  - `physical_cell_id`
  - `cell_connection`
  - `network_generation`
  - `network_subtype`
  - signal metrics
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1409`
- upload/download tests:
  - `warmup_duration`
  - `warmup_bytes_transferred`
  - `bytes_transferred`
  - `bytes_sec`
  - `targets`
  - `locations`
  - `cells`
  - `success_flag`
  - connectivity flags
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1415`
- latency tests:
  - `round_trip_time`
  - `jitter`
  - `packets_sent`
  - `packets_received`
  - `targets`
  - `locations`
  - `cells`
  - connectivity flags
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1428`
- submission object:
  - `test_id`
  - `device_timestamp`
  - `server_timestamp`
  - `server_source_ip_address`
  - `server_source_port`
  - `device_imei`
  - `device_type`
  - `manufacturer`
  - `model`
  - `operating_system`
  - `device_tac`
  - `device_id`
  - `app_name`
  - `app_version`
  - `provider_name`
  - SIM / network MCC-MNC values
  - `in_vehicle_flag`
  - `external_antenna_flag`
  - `scheduled_test_flag`
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1445`

Conclusion: the export layer is generally in the right shape. The remaining question is whether the app fills the upstream tables correctly for iOS.

## What The Current KMP App Captures

### Measurement Domain Model

The KMP measurement model has space for many FCC-relevant fields:

- device metadata
- provider
- MCC / MNC
- connectivity flags
- locations
- cells
- upload/download sub-data
- latency sub-data

Relevant model:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/model/Measurement.kt:6`
- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/model/UploadDownloadData.kt:6`
- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/model/LatencyData.kt:6`
- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/model/Location.kt:6`
- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/model/Cell.kt:8`

### FCC Submission Domain Model

The KMP FCC submission model also has space for:

- contact info
- device timestamp
- server timestamp
- source IP / port
- IMEI / TAC
- device metadata
- app metadata
- provider
- SIM / network MCC-MNC
- challenge flags

Relevant model:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/model/FccSubmission.kt:6`

### Measurement Execution

MSAK execution currently captures:

- latency:
  - RTT mean
  - jitter
  - packets sent
  - packets received
  - target server list
- throughput:
  - bytes
  - bytes/sec
  - target server list

Relevant KMP iOS executor:

- `/Users/jeff/Projects/cellwatch-app/shared/src/iosMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/MsakMeasurementExecutorPlatform.ios.kt:25`

Relevant MSAK library summaries:

- `/Users/jeff/Projects/msak-android/msak-shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/msak/shared/latency/LatencyRunner.kt:69`
- `/Users/jeff/Projects/msak-android/msak-shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/msak/shared/throughput/ThroughputRunner.kt:58`

## iOS Problems Identified

### 1. iOS location capture is not wired

The current iOS capability provider explicitly reports that location capture is not wired:

- `/Users/jeff/Projects/cellwatch-app/shared/src/iosMain/kotlin/edu/gatech/cc/cellwatch/domain/capability/IosPlatformCapabilityProvider.kt:21`

This means iOS measurement records will not be enriched with location samples unless some other code path provides them. The FCC spec still expects location objects for mobile challenge tests.

Impact:

- FCC export view can emit `locations`, but iOS currently does not reliably populate them.

### 2. iOS telephony and cell capture are not wired

The current iOS capability provider explicitly reports telephony as not supported:

- `/Users/jeff/Projects/cellwatch-app/shared/src/iosMain/kotlin/edu/gatech/cc/cellwatch/domain/capability/IosPlatformCapabilityProvider.kt:13`

The shared enricher only fills `cells`, carrier, and MCC/MNC from the capability snapshot:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/capability/PlatformCapabilitySnapshot.kt:96`

Impact:

- iOS cells will likely be null or empty.
- SIM / network MCC-MNC values will likely remain null unless injected elsewhere.
- Some nulls are acceptable on iOS, but the complete lack of telephony/cell capture is not ideal and may leave required non-exempt fields empty.

### 3. `provider_name` is currently wrong

The iOS measurement executor sets `provider` to the MSAK server machine:

- `/Users/jeff/Projects/cellwatch-app/shared/src/iosMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/MsakMeasurementExecutorPlatform.ios.kt:47`
- `/Users/jeff/Projects/cellwatch-app/shared/src/iosMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/MsakMeasurementExecutorPlatform.ios.kt:100`

Then the default submission context uses `appSource` as submission-level provider:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/DefaultMsakMeasurementSequenceOrchestratorFactory.kt:70`

Supabase exports submission `provider` as FCC `provider_name`:

- `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1447`

Impact:

- FCC `provider_name` should identify the mobile carrier.
- Current values appear to be either:
  - MSAK server hostname
  - harness/app-source string
- That is not aligned.

### 4. `app_version` is currently wrong

The default submission context sets:

- `appVersion = appSource`

Relevant code:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/DefaultMsakMeasurementSequenceOrchestratorFactory.kt:69`

Impact:

- FCC `app_version` is supposed to be the real app version.
- Current value is a harness/source label, not the actual released app version.

### 5. `app_name` may be null

The FCC submission metadata snapshot pulls `appName` from the three measurements:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/FccSubmissionPolicy.kt:64`

But the iOS MSAK executor never sets `appName` on `Measurement`:

- `/Users/jeff/Projects/cellwatch-app/shared/src/iosMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/MsakMeasurementExecutorPlatform.ios.kt:40`

Impact:

- `app_name` may reach Supabase as null.
- For FCC consumer challenge data, `app_name` is required.

### 6. `device_id` may be null

The FCC submission metadata snapshot also pulls `deviceId` from the measurements:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/FccSubmissionPolicy.kt:59`

The iOS capability provider does not supply a device/install identifier:

- `/Users/jeff/Projects/cellwatch-app/shared/src/iosMain/kotlin/edu/gatech/cc/cellwatch/domain/capability/IosPlatformCapabilityProvider.kt:25`

Impact:

- `device_id` may be null in FCC submissions.
- For consumer challenge data, `device_id` should be present.

### 7. `server_source_port` appears unpopulated

The FCC export view includes:

- `server_source_port`

Relevant export:

- `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1447`

The table has a `source_port` field:

- `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1373`

But I found no trigger or function that fills it. By contrast, `source_ip` is explicitly filled:

- `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:345`

Impact:

- `server_source_port` likely remains null.

### 8. Warmup fields are modeled but not captured

The FCC download and upload objects include:

- `warmup_duration`
- `warmup_bytes_transferred`

Your model supports them:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/model/UploadDownloadData.kt:9`

But the current iOS executor does not populate them:

- `/Users/jeff/Projects/cellwatch-app/shared/src/iosMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/MsakMeasurementExecutorPlatform.ios.kt:103`

And the current MSAK throughput summary does not expose warmup metrics:

- `/Users/jeff/Projects/msak-android/msak-shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/msak/shared/throughput/ThroughputRunner.kt:58`

Impact:

- Supabase can export these fields, but the values are currently absent.

### 9. Default contact phone format is not FCC-compliant

The default submission context uses:

- `contactPhone = "555-0000"`

Relevant code:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/DefaultMsakMeasurementSequenceOrchestratorFactory.kt:73`

The FCC PDF expects phone values in `000-000-0000` format when present.

Impact:

- Harness/default-generated challenge submissions are not format-compliant for phone.

## Fields That Are Probably Acceptable To Leave Null On iOS

These are the major nulls that appear acceptable under the FCC iOS exceptions:

- `device_tac`
- `sim_mobile_country_code`
- `sim_mobile_network_code`
- `net_mobile_country_code`
- `net_mobile_network_code`
- `carrier_aggregation_flag`
- `network_connected_flag`
- `network_available_flag`
- `network_roaming_flag`
- many radio-detail cell metrics

These should not be treated as primary blockers by themselves.

## Overall Assessment

### What is good

- Supabase export structure is close to the FCC hierarchy.
- Measurement and FCC submission domain models already contain many of the right concepts.
- MSAK provides the core test metrics needed for throughput and latency summaries.

### What is not yet good enough

- iOS platform capture is still too thin for FCC-aligned challenge reporting.
- Some FCC-required fields are missing.
- Some FCC fields are present but populated from clearly wrong sources.

## Recommended Next Work

1. Wire a real iOS location adapter into `IosPlatformCapabilityProvider`.
2. Add best-effort iOS telephony/provider capture where platform APIs allow it.
3. Stop using `server.machine` and `appSource` as FCC `provider_name`.
4. Feed real app metadata into measurement/submission capture:
   - `app_name`
   - `app_version`
5. Ensure a stable install-scoped `device_id` is attached to measurements.
6. Decide whether `server_source_port` can be derived server-side in Supabase.
7. Either:
   - extend MSAK to expose warmup metrics, or
   - document that these remain uncollected and confirm acceptability with FCC requirements.
8. Replace harness-only placeholder contact values when generating real challenge submissions.

## Bottom Line

The project is **not yet fully aligned** with FCC iOS capture requirements.

The blocking issue is not the Supabase FCC export shape. The blocking issue is that the iOS KMP capture layer is still missing or mis-populating several required upstream values before Supabase constructs the FCC payload.

## Android Problems Identified

Android is in better shape than iOS, but it is still not fully aligned.

### 1. Android location capture is not wired

The Android capability provider explicitly reports location as unavailable:

- `/Users/jeff/Projects/cellwatch-app/shared/src/androidMain/kotlin/edu/gatech/cc/cellwatch/domain/capability/AndroidPlatformCapabilityProvider.kt:41`

Impact:

- FCC test objects expect `locations` arrays.
- Supabase can export `locations`, but the Android shared capture path does not currently fill them.

### 2. `provider_name` is wrong on Android too

The Android measurement executor initially sets `provider` to the MSAK server machine:

- `/Users/jeff/Projects/cellwatch-app/shared/src/androidMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/MsakMeasurementExecutorPlatform.android.kt:47`
- `/Users/jeff/Projects/cellwatch-app/shared/src/androidMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/MsakMeasurementExecutorPlatform.android.kt:100`

Android telephony capture can provide a better carrier name:

- `/Users/jeff/Projects/cellwatch-app/shared/src/androidMain/kotlin/edu/gatech/cc/cellwatch/domain/capability/AndroidPlatformCapabilityProvider.kt:82`

But the default submission context still overwrites submission-level `provider` with `appSource`:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/DefaultMsakMeasurementSequenceOrchestratorFactory.kt:70`

Impact:

- FCC `provider_name` is likely wrong in Android submissions as well.

### 3. `app_version` is wrong on Android too

Submission build context uses `appVersion = appSource`:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/DefaultMsakMeasurementSequenceOrchestratorFactory.kt:69`

Impact:

- FCC `app_version` should be the real Android app version.
- Current value is a harness/source label.

### 4. `app_name` and `device_id` are still at risk

Submission metadata is derived from measurements:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/FccSubmissionPolicy.kt:58`

The Android executor itself does not set `appName` or `deviceId`:

- `/Users/jeff/Projects/cellwatch-app/shared/src/androidMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/MsakMeasurementExecutorPlatform.android.kt:40`

Impact:

- Android has stronger device and telephony capture than iOS, but I do not see a guaranteed path here that always sets FCC-compliant `app_name` and stable install-scoped `device_id` before submission creation.

### 5. Warmup fields are missing on Android too

The model supports warmup values:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/model/UploadDownloadData.kt:9`

But the Android executor does not populate them:

- `/Users/jeff/Projects/cellwatch-app/shared/src/androidMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/MsakMeasurementExecutorPlatform.android.kt:103`

And the MSAK throughput summary does not expose them:

- `/Users/jeff/Projects/msak-android/msak-shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/msak/shared/throughput/ThroughputRunner.kt:58`

Impact:

- FCC upload/download warmup fields remain absent for Android too.

### 6. `server_source_port` appears unpopulated on Android too

This is shared server-side behavior, not Android-specific:

- FCC export includes `server_source_port`:
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1447`
- table field exists:
  - `/Users/jeff/Projects/cellwatch-app/supabase/schema.sql:1373`
- I found no population logic for it.

Impact:

- Android FCC exports likely also have null `server_source_port`.

### 7. Android telephony capture is partial, not full FCC richness

Android does better than iOS here:

- provider
- SIM MCC/MNC
- network MCC/MNC
- generation / subtype
- cells
- roaming flag

Relevant Android provider:

- `/Users/jeff/Projects/cellwatch-app/shared/src/androidMain/kotlin/edu/gatech/cc/cellwatch/domain/capability/AndroidPlatformCapabilityProvider.kt:57`

But several Android cell fields remain unpopulated or weak:

- `network_subtype` at per-cell level is null:
  - `/Users/jeff/Projects/cellwatch-app/shared/src/androidMain/kotlin/edu/gatech/cc/cellwatch/domain/capability/AndroidPlatformCapabilityProvider.kt:218`
- richer radio metrics are null:
  - `rsrp`
  - `rsrq`
  - `sinr`
  - `csi_rsrp`
  - `csi_rsrq`
  - `csi_sinr`
  - `cqi`
  - `spectrum_band`
  - `spectrum_bandwidth`
  - `/Users/jeff/Projects/cellwatch-app/shared/src/androidMain/kotlin/edu/gatech/cc/cellwatch/domain/capability/AndroidPlatformCapabilityProvider.kt:221`

Impact:

- Android is likely acceptable for some submissions, but it is not clearly at full FCC richness for standard Android challenge reporting.

### 8. `device_imei` and `device_tac` are still not populated in the shared submission path

The FCC submission model has both:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/model/FccSubmission.kt:17`

But submission build policy does not populate either field:

- `/Users/jeff/Projects/cellwatch-app/shared/src/commonMain/kotlin/edu/gatech/cc/cellwatch/domain/fcc/FccSubmissionPolicy.kt:72`

Impact:

- On Android, unlike iOS, these are more important because Android does not benefit from the same explicit FCC null allowances for TAC.

## Platform Comparison

### iOS

Status: **far from aligned**

Main blockers:

- no location capture
- no telephony capture
- no cell capture
- wrong provider source
- wrong app version
- weak app/device metadata population

### Android

Status: **closer, but still incomplete**

Main blockers:

- no location capture
- wrong provider source
- wrong app version
- likely weak app/device metadata population
- missing warmup metrics
- missing server source port
- incomplete radio richness
- missing IMEI / TAC population in shared submission path

## Overall Bottom Line

Neither platform is fully aligned today.

- iOS is the larger gap by far.
- Android is closer because telephony and cell capture are at least partially wired.
- The Supabase FCC export shape is not the primary problem.
- The primary problem is upstream field capture and submission-context correctness before Supabase constructs the final FCC JSON.
