# Parity and FCC-correctness plan

Work items from comparing the KMP app against `frozenApp/`, in the order I would do them.
Each entry records what is wrong, the evidence, what to build on each platform, and — where
the two platforms cannot do the same thing — the decision to be made and my recommendation.

Recorded 2026-09-18. Companion to `PRE_DEPLOYMENT_CHECKLIST.md`, which covers deployment
blockers rather than correctness gaps.

---

## The rule for platform asymmetry

**Standing strategy: capture the absolute best we can on iOS, record every discrepancy, report
the list to the FCC and ask for guidance — and do not hold up development or testing waiting
for the answer.** Build the best-effort implementation now; adjust if the FCC answers
differently. Discrepancies accumulate in `FCC_IOS_DISCREPANCIES.md`, which is the artefact to
send them.

That strategy decides the platform questions below, so they are recommendations to implement
rather than decisions to wait on. Applied throughout:

1. **Never fabricate.** If a platform cannot observe something, the value is null. A plausible
   guess is worse than a null, because a null is visible and a guess is not.
2. **Prefer a truthful partial over an empty whole.** Where the spec exempts a *field* on iOS
   but not the *structure* containing it, emit the structure with the fields we do have.
3. **The FCC's iOS exemption list is the authority, not our convenience.** BDC v2.2 section
   3.1.1 enumerates exactly what may be null on iOS. Anything outside that list is required on
   both platforms, and "iOS cannot do it" is a finding to escalate, not a licence to omit.
4. **Record the limitation where the data lives**, via the existing `capabilityNotes`, so an
   analyst can tell "not measurable here" from "measured as absent".

### What the spec actually exempts on iOS

Verified in BDC *Data Specifications for Mobile Speed Test Data* v2.2 (2025-07-28) section
3.1.1:

- **Submission Object**: `device_tac`, `net_mobile_country_code`, `net_mobile_network_code`,
  `sim_mobile_country_code`, `sim_mobile_network_code`
- **Download / Upload / Latency Test Objects**: `carrier_aggregation_flag`,
  `network_connected_flag`, `network_available_flag`, `network_roaming_flag`
- **Cell Object**: `physical_cell_id`, `cell_connection`, `signal_strength`, `rssi`, `rsrp`,
  `rsrq`, `sinr`, `csi_rsrp`, `csi_rsrq`, `csi_sinr`, `cqi`, `spectrum_band`,
  `spectrum_bandwidth`, `arfcn` (and `cell_id`, exempted inline at its own definition)

**The `cells` array itself is not exempt** — only fields within each element. Nor are
`network_generation` or `network_subtype`, both of which CoreTelephony can supply. This single
fact decides item 2 below.

---

## 1. Enforce the FCC generation-stability rule  ·  highest priority

### What is wrong

`success_flag` is defined by the spec as *"whether the test completed successfully **and
without a change in state or connectivity**."* We currently implement only the first half. A
measurement that hands off 5G→LTE mid-run is submitted as successful.

frozenApp enforced it explicitly:

```kotlin
// The FCC requires the test to stay on the same technology generation to be successful.
val success = when {
    !generations.all { it == generations.first() } -> false
    else -> resultSuccess
}
```
`frozenApp/.../domain/fcc/MeasurementTest.kt:63`

The KMP equivalent — `MeasurementResultPolicy.finalizeMeasurementSuccess(resultSuccess,
observedGenerations)` — **exists and is never called.** Only its own unit tests reference it.

### Build

- Collect generation samples across each measurement (delivered by item 2's observation
  plumbing — do 2a first, then return here).
- Call `finalizeMeasurementSuccess` in the three executors where `success` is computed
  (`MsakMeasurementExecutorPlatform.{ios,android}.kt`), after the coverage check added
  previously.
- Record the observed generations in `capabilityNotes` so a `false` is explainable.

### Platform asymmetry — resolvable

| | Android | iOS |
|---|---|---|
| Source | `TelephonyCallback.CellInfoListener` | `CTTelephonyNetworkInfo` + `ServiceSubscriberCellularProvidersDidChange` notification |
| Fidelity | per-cell, precise | coarse: current radio access technology only |

Both can detect a generation change. iOS's signal is coarser and can lag, so it will catch
fewer changes — under-reporting failures rather than inventing them, which is the safe
direction. **No decision needed; implement on both.**

### Verification

Unit tests over synthetic generation sequences (already written, currently unreachable), plus
a device run with a forced network-type change (Settings → Mobile Data → Voice & Data, switch
5G↔LTE mid-test) asserting `success_flag=false`.

**Effort:** small once item 2a exists. This is the cheapest FCC-correctness win available.

---

## 2. Observe cells for the duration of the test

### What is wrong

The spec asks for *"List of cellular telephony information measured **during** the speed
test."* We take one `captureSnapshot()` per measurement and submit that single sample.
frozenApp registered a `TelephonyCallback` for the length of each test and accumulated every
cell observed (`TelephonyInfoManager.kt:417`). KMP has no continuous monitoring anywhere — no
`registerTelephonyCallback`, no `PhoneStateListener`.

This also blocks item 1: one sample cannot show a change.

### Build

**2a — observation window (shared contract).** Introduce a `TelephonyObservationSession`:
`start()` before the measurement, `stop()` after, returning accumulated cells and generation
samples. `expect`/`actual`, mirroring `PlatformCapabilityProvider`. Executors open the window
around `runLatency`/`runThroughput` and merge the result into the measurement.

**2b — Android.** `TelephonyManager.registerTelephonyCallback` with `CellInfoListener`;
de-duplicate by cell identity + timestamp. frozenApp's implementation is a good reference,
including its `PhoneStateListener` fallback for older APIs.

**2c — iOS.** Cannot enumerate cells at all. See the decision below.

### Platform asymmetry — needs your decision

iOS gives third-party apps **no cell identity, no signal strength, nothing** beyond the current
radio access technology string. Three options:

| | Approach | Consequence |
|---|---|---|
| **A (recommended)** | Emit one Cell Object per observed RAT change, carrying `timestamp`, `network_generation` and `network_subtype`, with every iOS-exempt field null | Structurally valid, truthful, and the non-exempt fields are populated |
| B | Emit an empty `cells` array on iOS | Likely non-compliant: the array is not on the iOS exemption list, and the "may be empty" allowance is conditioned on `success_flag` being false |
| C | Do not submit from iOS at all | Throws away the platform; not warranted by anything in the spec |

**Recommendation: A.** It is the only option that satisfies the spec as written. It also
follows the asymmetry rule — the exemption list tells us precisely which fields may be null,
and `network_generation` is not among them, so an empty array is the one clearly wrong answer.

`CTTelephonyNetworkInfo.serviceCurrentRadioAccessTechnology` maps cleanly onto the spec's
enumerations (`LTE`→4G/LTE, `NRNSA`→5G/NRNSA, `NR`→5G/NRSA, `WCDMA`/`HSDPA`→3G/…), so this is
a lookup table, not guesswork.

**Report, do not wait.** Log the sparse-Cell-Object shape in `FCC_IOS_DISCREPANCIES.md` and
raise it with the FCC, but ship A now. If they come back wanting something else, the mapping is
the only part that changes.

**Effort:** medium. 2a+2b is the bulk; 2c is a small mapping once 2a exists.

---

## 3. Capture location at the start and end of each test

### What is wrong

frozenApp took `beginLocation` before and `endLocation` after every test. Android's KMP
provider emits `samples = listOf(Location(...))` — exactly one, at capture time. The spec's
`locations` is an array, and a test taken while walking or driving covers ground —
`in_vehicle_flag` is itself a submitted field.

### Build

Extend the item 2a observation window to bracket location as well: sample at open and at
close, append both. No new permissions on either platform.

### Platform asymmetry — none

CoreLocation and `FusedLocationProvider`/`LocationManager` both do this. Implement identically.

**Effort:** small, once 2a exists. Worth folding into the same change.

---

## 4. Make a measurement survive backgrounding and screen lock

### What is wrong

frozenApp ran every measurement inside a foreground `Service`, held a
`PowerManager.WakeLock`, and declared `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` and
`WAKE_LOCK`. `androidTestApp` declares **none of those three** and has no service. A ~30s
measurement can therefore be throttled or killed if the screen locks or the user switches away,
and background location is restricted without a foreground service.

This is a field-testing app; users walk around and pocket the phone.

### Build

**Android:** port the foreground service — notification channel, `startForeground`, wake lock
acquired for the run and released in `finally`, plus the three manifest permissions. frozenApp's
`MeasurementService.kt` is a direct reference.

**iOS:** no true equivalent. Options, best-effort:
- `UIApplication.beginBackgroundTask` buys ~30s of grace — enough for one measurement phase,
  not a full three-phase sequence.
- `CLLocationManager.allowsBackgroundLocationUpdates` with Always authorisation keeps the app
  alive while location updates flow, but requires a stronger permission prompt and App Store
  justification.

### Platform asymmetry — needs your decision

| | Approach | Consequence |
|---|---|---|
| **A (recommended)** | Android foreground service; iOS `beginBackgroundTask` plus explicit UI telling the user to keep the app open | Honest, no new iOS permission, no store-review risk |
| B | Also adopt background location on iOS | Better survival, but Always-location is a heavier ask and needs App Store justification for a research app |

**Recommendation: A** — it is the best iOS can do without a heavier permission, which matches
the standing strategy. Log the shortfall as a discrepancy and revisit B only if field use shows
real attrition. Pair it with
detecting the interruption and marking the measurement rather than submitting a truncated one —
the duration-coverage check added earlier already fails those, so they will be recorded with
`success_flag=false` rather than silently wrong.

**Effort:** medium on Android, small on iOS.

---

## 5. Restore error and crash reporting

### What is wrong

frozenApp routed its logger into Firebase Crashlytics, recording exceptions and tagging
`device_id`. In KMP, `shared/.../core/util/Log.kt` is **entirely commented out** and there is
no logging in `shared/` at all.

The cost is not hypothetical: twice in one session, facts that the code had already computed —
the detected network interface, and the sync report explaining why nothing uploaded — were
discarded rather than surfaced, and had to be added before anything could be diagnosed.

### Build

- A minimal `expect`/`actual` logger in `shared/` (`Log.d/i/w/e`), `NSLog`/`os_log` on iOS,
  `android.util.Log` on Android — enough that shared code can say something.
- Decide separately whether to reinstate Crashlytics. It is a third-party SDK collecting from a
  research app, so it is a data-governance question as much as a technical one.

### Platform asymmetry — none

Both platforms support both halves.

**Effort:** small for the logger; Crashlytics depends on the governance answer.

---

## 6. Encrypt the stored device secret

### What is wrong

frozenApp encrypted the device secret before writing it to DataStore. `PersistentDeviceAuthStore`
writes plaintext JSON into app-private storage (`NSUserDefaults` / `SharedPreferences`).

Defensible — sandboxed storage, an anonymous credential scoped to one device — but frozenApp set
a higher bar and `SecureEncryptor` already exists in `shared/`.

### Build

Wrap the stored blob in `SecureEncryptor.encrypt/decrypt`. A decryption failure needs no special
handling: the existing pairing rule treats an unreadable record as absent and mints a fresh
credential, which is already the correct recovery.

### Platform asymmetry — none

`SecureKeyStore` has actuals on both.

**Effort:** small.

---

## Do not port

frozenApp selects its throughput server with `throughputServers.maxBy { ping(it.machine) }`
(`MeasurementManager.kt:230`), and `ping()` returns mean RTT in milliseconds — so it picks the
**slowest** server. Almost certainly an inverted comparator. KMP does no ping-based selection,
which is better than copying this. If latency-based selection is ever wanted, write it fresh
with `minBy` and a timeout.

---

## Suggested sequencing

1. **2a** observation window (unblocks 1, 2b, 2c, 3)
2. **1** generation-stability enforcement — the cheapest FCC-correctness win
3. **2b / 2c** cells, Android then iOS
4. **3** begin/end location — fold into the same change
5. **5** shared logger — pull earlier if diagnosing gets painful again
6. **4** run survival
7. **6** secret encryption

Items 1–3 are one coherent workstream and should land together behind the same device
verification. Items 4–6 are independent and can be done in any order.

## To raise with the FCC (in parallel, not as a gate)

Tracked in `FCC_IOS_DISCREPANCIES.md`; none of these block the work above.

- **iOS Cell Objects** — is timestamp + `network_generation` + `network_subtype`, with all
  exempt fields null, acceptable? (item 2)
- **iOS interruption exposure** — a measurement cannot be guaranteed to survive backgrounding;
  affected runs are marked `success_flag=false` rather than submitted truncated (item 4)

## Still ours to decide

- **Crashlytics** — acceptable for a research app collecting location traces? A data-governance
  question, not an FCC one (item 5)
