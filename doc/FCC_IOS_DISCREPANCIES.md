# iOS discrepancies against BDC mobile speed test requirements

Running record of places where iOS cannot supply what the BDC *Data Specifications for Mobile
Speed Test Data* v2.2 (2025-07-28) asks for, what CellWatch submits instead, and why.

**This document is the artefact to send the FCC when asking for guidance.** Development does
not wait on their answer: each row states what we ship today, and if the FCC directs otherwise
only the mapping changes.

Every entry follows the same discipline — capture the best truthful value iOS can give, null
only what the spec's iOS exemption list permits, never fabricate a plausible value, and note
the limitation in the measurement's own `capabilityNotes` so an analyst can distinguish "not
measurable on this platform" from "measured as absent".

Started 2026-09-18.

---

## What the spec already exempts on iOS

For context, so the entries below are limited to genuine gaps. BDC v2.2 section 3.1.1 permits
null on iOS for:

- **Submission Object**: `device_tac`, `net_mobile_country_code`, `net_mobile_network_code`,
  `sim_mobile_country_code`, `sim_mobile_network_code`
- **Download / Upload / Latency Test Objects**: `carrier_aggregation_flag`,
  `network_connected_flag`, `network_available_flag`, `network_roaming_flag`
- **Cell Object**: `cell_id`, `physical_cell_id`, `cell_connection`, `signal_strength`, `rssi`,
  `rsrp`, `rsrq`, `sinr`, `csi_rsrp`, `csi_rsrq`, `csi_sinr`, `cqi`, `spectrum_band`,
  `spectrum_bandwidth`, `arfcn`

CellWatch submits null for these on iOS, as permitted. They are not discrepancies.

---

## 1. `cells` array — structurally required, but iOS exposes no cells

**Spec:** the `cells` array on each Test Object is *"List of cellular telephony information
measured during the speed test."* The iOS exemption list covers fields **within** a Cell
Object; it does not exempt the array itself. `network_generation` and `network_subtype` carry
no iOS exemption.

**Platform limitation:** iOS provides third-party apps with no cell enumeration, no cell
identity and no signal measurements. `CTTelephonyNetworkInfo` exposes only the current radio
access technology per service.

**What we submit:** one Cell Object per observed radio-access-technology change during the
test, carrying:

| Field | Value on iOS |
|---|---|
| `timestamp` | time of observation |
| `network_generation` | derived from `CTTelephonyNetworkInfo` (`LTE`→4G, `NR`/`NRNSA`→5G, `WCDMA`/`HSDPA`→3G, …) |
| `network_subtype` | the mapped CoreTelephony value (`LTE`, `NRSA`, `NRNSA`, `WCDMA`, `HSPA`, …) |
| everything else | null, per the iOS exemption list |

**Question for the FCC:** is a Cell Object carrying only timestamp, generation and subtype
acceptable for iOS submissions? If an empty array is preferred instead, say so — we chose the
sparse object because the array is not on the exemption list.

---

## 2. Measurement interruption cannot be fully prevented on iOS

**Spec:** `success_flag` is *"whether the test completed successfully and without a change in
state or connectivity."*

**Platform limitation:** iOS offers no equivalent to an Android foreground service. If the user
backgrounds the app or the screen locks mid-test, the app can be suspended.
`UIApplication.beginBackgroundTask` buys roughly 30 seconds — enough for one phase, not a full
three-phase sequence. Keeping the app alive otherwise requires Always-authorised background
location, a heavier permission than a speed test warrants.

**What we submit:** interrupted runs are detected by duration coverage (measured window vs
requested) and marked `success_flag=false` with the measured values retained, rather than
submitted as complete. The user is told to keep the app open.

**Question for the FCC:** is that treatment acceptable, or is there a preferred way to signal an
interrupted test?

---

## 3. Radio-generation change detection is coarser on iOS

**Spec:** `success_flag` must reflect a change in state during the test.

**Platform limitation:** Android observes per-cell information continuously via
`TelephonyCallback`. iOS can only observe the current radio access technology, and the
notification can lag an actual handover.

**What we submit:** generation samples collected across the test on both platforms; any change
sets `success_flag=false`. iOS will detect fewer changes than Android — it under-reports
failures rather than inventing them, which we judged the safer direction.

**Question for the FCC:** none outstanding; recorded so the difference in `success_flag` rates
between platforms is not mistaken for a data-quality problem.

---

## 4. `device_imei` is unavailable on iOS

**Spec:** `server_source_port` may be null when `submission_category` is Entity Challenge *and*
`device_imei` is non-null. iOS provides no IMEI to third-party apps (nor does modern Android
without privileged access).

**What we submit:** null. Noted because it closes off one of the documented routes around the
`server_source_port` gap described in `PRE_DEPLOYMENT_CHECKLIST.md` section 5.

---

## Template for new entries

```
## N. <field or behaviour>

**Spec:** what is required, with the section reference.
**Platform limitation:** what iOS actually exposes.
**What we submit:** the best-effort value, and what is null.
**Question for the FCC:** the specific guidance wanted, or "none outstanding".
```
