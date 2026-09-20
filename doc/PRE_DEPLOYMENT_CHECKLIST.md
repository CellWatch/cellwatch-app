# Pre-Deployment Checklist

Findings that must be resolved before a public release, deliberately **not** changed during
internal development. Each entry records what is wrong, where, and the fix, so it can be acted
on later without rediscovering it.

Recorded 2026-09-17.

---

## 1. A Mapbox **secret** token ships inside the app bundle

> **RESOLVED 2026-09-20.** `cellwatch.properties` now defines a `pk.` public
> token as `MAPBOX_ACCESS_TOKEN`, and
> `scripts/generate-ios-runtime-properties.sh` refuses the
> `MAPBOX_DOWNLOADS_TOKEN` fallback for any configuration other than Debug -
> a Release or AppStore build fails rather than packaging a secret. It also
> rejects a token without a `pk.` prefix. Verified: Release archive packages
> `<pk.… 89 chars>`.
>
> Two notes carried forward. The `sk.` downloads token was exposed in internal
> builds only and is still worth rotating - nothing shipped depends on it.
> The `pk.` token is the *same* one compiled into the publicly released
> frozenApp (`values/strings.xml`), so revoking it would break the map for
> existing Android users; add a second public token rather than replacing it.
>
> Android still has the same fallback at `androidTestApp/build.gradle.kts:81`.

**Severity: high.** Extractable from any distributed build (TestFlight or App Store).

### What is wrong

The built Release `.app` contains a `cellwatch.runtime.properties` entry:

```
MAPBOX_ACCESS_TOKEN=sk.…        # 89 chars, "sk." prefix
```

`sk.` is a Mapbox **secret** token. A client application must carry a `pk.` public token.
Anyone who downloads the app can read this value out of the bundle and use it against the
Mapbox account with whatever scopes it holds.

### Why it happens

`cellwatch.properties` defines `MAPBOX_DOWNLOADS_TOKEN` (the `sk.` credential used by
Gradle/SPM to *download* the Mapbox SDK) but does **not** define `MAPBOX_ACCESS_TOKEN`.
`scripts/generate-ios-runtime-properties.sh` falls back from the missing public token to the
downloads token and packages it:

```bash
MAPBOX_TOKEN="$(resolve_value "MAPBOX_ACCESS_TOKEN" || true)"
if [[ -z "$MAPBOX_TOKEN" ]]; then
  MAPBOX_TOKEN="$(resolve_value "MAPBOX_DOWNLOADS_TOKEN" || true)"   # <-- packages the secret
fi
```

The two tokens serve unrelated purposes and should never substitute for one another.

### Fix before deployment

1. **Rotate the leaked `sk.` token** in the Mapbox account. Treat it as compromised — it is
   present in local build artifacts and in anything already distributed.
2. Create a `pk.` public token and add it to `cellwatch.properties` as `MAPBOX_ACCESS_TOKEN`.
3. Delete the `MAPBOX_DOWNLOADS_TOKEN` fallback from
   `scripts/generate-ios-runtime-properties.sh` and hard-fail when `MAPBOX_ACCESS_TOKEN` is
   missing or does not begin with `pk.`.
4. Add an assertion to `iosTestApp/Tests/HarnessUiSmokeTests.swift` that the packaged token
   starts with `pk.`, alongside the existing
   `testBundledSupabaseConfig_matchesSelectedDeploymentMode`.
5. Check whether the Android path has the same substitution
   (`androidTestApp/build.gradle.kts` `resValue("string", "mapbox_access_token", …)`).

### Related: unactionable on-device error text

When the token is missing the app renders *"Mapbox token missing. Set `MAPBOX_ACCESS_TOKEN` in
`cellwatch.properties`."* (`iosTestApp/App/AppDelegate.swift:1335` and `:1633`). That advice
cannot be followed on a device — the app cannot read the developer Mac's property files. Reword
to reference the packaged runtime resource.

---

## 2. `CELLWATCH_SYNC_DIAGNOSTICS_LEVEL` defaults to `VERBOSE` in shipped builds

`iosTestApp/App/AppDelegate.swift:3365` defaults this to `"VERBOSE"`, and no build setting or
packaged value overrides it. TestFlight and App Store builds therefore run with verbose sync
diagnostics, including cause chains (`CELLWATCH_SYNC_DIAGNOSTICS_INCLUDE_CAUSE_CHAIN` defaults
`true`).

Useful during internal development — leave it. Before release, pin a quieter default for
device/Release builds in `scripts/generate-ios-runtime-properties.sh`.

---

## 3. No release automation exists

Stated so nobody assumes otherwise:

- No `exportOptions.plist` anywhere.
- No fastlane (`Fastfile`, `fastlane/`).
- No CI — `.github/` contains only issue templates; there is no `.github/workflows/`.
- No Android signing config — `assembleRelease` produces an unsigned artifact.
- `DEVELOPMENT_TEAM` is set in `iosTestApp/project.yml`, so signing works for interactive
  Xcode archiving only.

A store submission is a manual Xcode archive. Decide before release whether that is acceptable
or whether it should be automated.

---

## 4. Other hardcoded development defaults that reach shipped builds

Lower severity; review before release.

| Key | Location | Default in shipped builds |
|---|---|---|
| `CELLWATCH_MAP_SIM_LAT` / `_LON` | `AppDelegate.swift:2627`, `:2714`, `:2736` | Georgia Tech coordinates (`33.778462`, `-84.390123`), used as the fallback position written into persisted history when no location fix exists |
| local demo service-role JWT | `AppDelegate.swift:12–15`, returned `:101` | compiled into `#if DEBUG` builds only; not in Release |

The service-role JWT is Debug-only by construction, but a Debug build installed on a device
previously used it silently. See the device runtime-config work for the guard added there.

---

## 5. `server_source_port` is required and not currently obtainable

**Unresolved.** Verified against the BDC *Data Specifications for Mobile Speed Test
Data*, v2.2 (2025-07-28), section 5.1.2 Submission Object.

### What the spec asks for

> `server_source_ip_address` — "Source IP address of the device submitting test
> submission data, measured by the server. Value must be in valid **IPv4 or IPv6**
> format."
>
> `server_source_port` — "Source **TCP** port of the device submitting test
> submission data, measured by the server."
>
> Both "must correspond to transmission recorded in the `server_timestamp`" value,
> which is "the time at which the test submission data were transmitted to **the
> app's servers**".

Three things follow, each checked rather than assumed:

1. **These describe the device -> Supabase upload, not the measurement.** They live
   in the Submission Object; all 14 mentions in the spec are there, and *none* are
   in the Download, Upload or Latency Test Objects. If the measurement connection
   were meant, the field would be per-test and there would be three.
2. **Only Supabase can supply them.** Any address the client fetches from
   elsewhere - MSAK's `RemoteAddr`, or an echo service - describes a different TCP
   connection, so it cannot "correspond to" the submission. Deferred sync makes
   this worse: a measurement taken on cellular may be uploaded later over WiFi.
3. **IPv6 is explicitly acceptable**, so there is no IP-family concern here.

### What works and what does not

The **IP** is already correct: `fcc_submission_update_source_ip`
(`supabase/schema.sql:345`) records `x-forwarded-for` on insert. It fires only
`WHEN new.source_ip IS NULL`, so the client must send nothing - which it now does
(see `UnavailableTcpTupleProvider`).

The **port** is the gap. A client's source port is a TCP property; HTTP has no
field for it. When a reverse proxy terminates the device's connection and opens a
new one to the backend, the port is gone unless the proxy copies it into a header.
`X-Forwarded-For` carries only the IP; `X-Forwarded-Port` conventionally carries
the *destination* port. Postgres's own `inet_client_port()` sees PostgREST, not the
device. The one standard header that can carry it is RFC 7239
(`Forwarded: for="192.0.2.43:47011"`), which is rarely set.

This is not specific to Supabase - the same applies behind nginx, an ALB,
Cloudflare or Fastly.

### Why it matters

`server_source_port` may be null only when `submission_category` is Consumer
Crowdsourced, Entity Crowdsourced, Provider Response or Other, **or** when it is
Entity Challenge *and* `device_imei` is non-null. CellWatch submits as
**`Consumer Challenge`** (`supabase/schema.sql:1447`), which is in none of those
cases, so the field is required and is currently absent.

### To resolve, in order of preference

1. **Check whether the port is already available.** `log_user_data()` stores the
   full header JSON via `get_raw_header()`, so this is answerable from data already
   collected:
   ```sql
   select request_header from log_table
   where table_name = 'fcc_submissions' order by id desc limit 5;
   ```
   If any header carries a source port, read it in the trigger and the gap closes.
2. **Put an endpoint you control in the submission path.** Only the server that
   terminates the device's TCP connection can read the port. Note the project is on
   **hosted** Supabase (`*.supabase.co`), so the edge is Supabase-managed and cannot
   be modified; this repo contains the database schema only, no gateway config.
3. **Reconsider `submission_category`.** A university research app using its own
   software and hardware may be an **Entity Challenge** rather than a Consumer
   Challenge, which permits `device_imei` in lieu of all three server-measured
   fields. Note iOS cannot provide IMEI and this project's own gap analysis treats
   it as blocked on Android, so this may not help.
4. **Submit with the port null and see whether BDC rejects it**, and/or ask FCC -
   the challenge process already requires submitting a methodology description.

### The AWS tuple service: kept, currently unreachable

**Retained deliberately.** `TCP_TUPLE_URL="http://52.55.102.226/"` is an EC2
instance added by Jason Cox in Dec 2023 (`d7bb767`), replacing an `api.ipify.org`
call, and fetched immediately before upload in `frozenApp`'s
`tryUploadFccSubmissions()` - so the intent was an address contemporaneous with
the *submission*, which is the right instinct and the best option available while
the port cannot be read at the edge.

`HttpTcpTupleProvider` implements the lookup on all three platforms and is wired
in via `tcpTupleProviderFor`. The switch is the URL itself: blank or absent
disables the lookup. iOS resolves it from the packaged runtime resource, Android
from `BuildConfig.CELLWATCH_TCP_TUPLE_URL`.

It is **currently unreachable** (`curl --max-time 10` times out), which costs only
the request timeout: `MeasurementSyncUseCase` uploads without a tuple rather than
withholding the measurement, and the Supabase trigger then records the IP it
observed. To silence it while the service is down, comment out `TCP_TUPLE_URL` in
`cellwatch.properties`.

Known limitation once it is back up: the **IP** will be right in practice (same
network, same NAT, seconds before the upload), but the **port** describes the TCP
connection to the AWS box, not the one to Supabase. Every connection gets a
different ephemeral port, and carrier NAT assigns a different external port per
connection, so it cannot satisfy the spec's requirement that all three
server-measured fields correspond to one transmission. Whether that matters in
practice - whether BDC ever cross-checks the port against the timestamp - is
unknown.

Supabase has no option for this. Their documentation covers `X-Forwarded-For` for
the client IP and says nothing about a source port; it is not a hidden toggle, the
port simply is not carried through a managed edge.

### Also unrecorded: which IP family a measurement used

`IosSocket.kt` prefers `AF_INET6` with `IPV6_V6ONLY = 0` and switches to `AF_INET`
for IPv4 literals, and m-lab hostnames resolve `AF_UNSPEC` - so a measurement may
run over either family and nothing records which. Not required by the spec; noted
in case it is ever wanted.

### Related: `round_trip_time` statistic is unspecified

The spec says only "Round-trip latency in microseconds" - mean, median and minimum
are all format-legal. CellWatch reports the **mean**, which on real cellular is
pulled upward by burst arrivals (a phone run measured mean 57ms against median
45ms). Since the challenge process requires a methodology description, document the
choice.

## 6. FCC submission now requires a real cellular connection

Not a deferred item - a behaviour change to be aware of. The executors used to
hardcode `connectionType = CELLULAR`, so `FccSubmissionPolicy`'s cellular check
could never fail and WiFi or tethered measurements were submitted as cellular.
Connection type is now detected (`nw_path` on iOS, `NetworkCapabilities` on
Android) and the policy requires `CELLULAR`.

Consequence: measurements taken on a simulator, an emulator, over WiFi, or while
USB-tethered are stored but **not** submittable. iOS offers no way to force
traffic onto cellular for a URLSession or BSD socket, so field testing for
submittable data must be done on a device with WiFi off and no tethering.
