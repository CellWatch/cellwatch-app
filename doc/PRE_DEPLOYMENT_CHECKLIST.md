# Pre-Deployment Checklist

Findings that must be resolved before a public release, deliberately **not** changed during
internal development. Each entry records what is wrong, where, and the fix, so it can be acted
on later without rediscovering it.

Recorded 2026-09-17.

---

## 1. A Mapbox **secret** token ships inside the app bundle

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

## 5. The public-address echo service needs standing up

`server_source_ip_address` no longer carries a placeholder (see the source-IP
commit), but it will stay empty on the client side until a reachable echo service
exists. Two things to do:

1. **Stand up a service and configure it by hostname.** `cellwatch.properties`
   has `TCP_TUPLE_URL="http://52.55.102.226/"`, which is (a) unreachable -
   `curl --max-time 10` times out - and (b) a bare IPv4 literal, so an IPv6-only
   carrier network (increasingly common via NAT64/464XLAT) either cannot reach it
   or traverses a translator, in which case the reported address is the
   translator's rather than the device's. Use a dual-stack hostname.
   `HttpTcpTupleProvider` already reads this key on all three platforms and is
   packaged into the iOS bundle by generate-ios-runtime-properties.sh; it just
   needs a working endpoint and the call sites switched from
   `UnavailableTcpTupleProvider`.
2. **Confirm what FCC expects.** The requirement is the device's source IP and
   port "as measured by the server". Until (1) is done, the value comes from
   Supabase's `fcc_submission_update_source_ip` trigger, i.e. the address
   observed at the *submission* endpoint. An echo service would be a dedicated
   lookup, still not the MSAK measurement server. Full fidelity would need msak
   changes: its latency `Summarize()` omits the `Client` ip:port field it records
   (it is archival-only), though throughput server-sent measurements do carry
   connection endpoints.

The authoritative specification
(`bdc-mobile-speed-test-data-specifications.pdf`) and the challenge-process
article both returned HTTP 403 when fetched, so nothing here about IPv6
acceptability in that field has been verified against FCC documentation.

### For reference: MSAK handles both families deliberately

Not a defect. `IosSocket.kt` prefers `AF_INET6` with `IPV6_V6ONLY = 0` for
dual-stack and switches to `AF_INET` for IPv4 literals to "avoid v4-on-v6
issues". m-lab hostnames resolve `AF_UNSPEC`, so a measurement may run over
either family and **nothing records which**. If a submission is expected to state
the family, that information is currently discarded.

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
