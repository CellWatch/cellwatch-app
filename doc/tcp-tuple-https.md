# Serving the TCP tuple over HTTPS

For #68. The proposals here need a team decision; none of it has been built.

## What the tuple service does today

- **The request.** Before uploading FCC submissions, the app does a `GET` to `TCP_TUPLE_URL`
  (`http://34.201.124.67/`, plain HTTP, set in `cellwatch.properties` and compiled into `BuildConfig`). See
  `MeasurementRepository.getPubicTCPTuple()`.
- **The response.** The box is an Express app on EC2 with no proxy in front. It answers JSON with the socket's
  remote address and port and its own clock (`remoteAddress`, `remotePort`, `timestamp` in epoch ms).
- **How it's used.** `MeasurementUploader` copies the response into `source_ip`, `source_port` and
  `server_timestamp` of **every** submission in that upload run. These become the BDC fields
  `server_source_ip_address`, `server_source_port` and `server_timestamp`.
- **If it fails.** If the request fails, no submission uploads in that run.
- **Cleartext.** The app allows cleartext for everything: `android:usesCleartextTraffic="true"` in
  `AndroidManifest.xml`, and there's no `network_security_config`. In release builds the tuple request is the
  only plain-HTTP URL in our own configuration: Supabase is HTTPS and the M-Lab throughput servers are `wss`.
  The M-Lab latency authorize/result URLs come from M-Lab's locate response; check their scheme on a release
  build before turning cleartext off.

Spec §5.1.2 defines all three fields as describing the **transmission of the submission to the app's servers,
as measured by the server**, and requires them to correspond to each other. Today they describe a different
connection: a separate GET to a different server, made before the upload.

## Why plain HTTP matters

- **Transparent proxies.** Some mobile carriers intercept port-80 traffic with transparent HTTP proxies (for
  "optimisation" or header enrichment). The proxy ends the phone's TCP connection and opens its own, so the
  server sees the proxy's address and port, not the phone's NAT mapping. The tuple is then wrong in a way
  nobody can see. HTTPS on 443 is generally passed through untouched, because a proxy can't end TLS without a
  certificate the phone trusts.
- **Tampering.** Anyone on the path (a captive portal, hostile Wi-Fi, the carrier) can rewrite the JSON. A user
  can also do it with a local proxy, which is easier over HTTP. With HTTPS, Android apps targeting API 24+
  don't trust user-installed CAs by default, so a casual MITM fails.
- **Privacy.** The response is the phone's public IP, sent in clear text.

HTTPS fixes the transport. It doesn't fix that the fields describe the wrong connection. Only measuring them
on the upload itself (option C) does that.

## Options

| | A. Domain + Let's Encrypt on the EC2 box | B. CloudFront in front of the EC2 box | C. CloudFront in front of the Supabase upload (replaces EC2) |
|---|---|---|---|
| HTTPS | Yes, with our own domain | Yes, with the default `*.cloudfront.net` certificate; no domain needed | Yes, with `*.cloudfront.net` |
| What it needs | A domain or GT subdomain, DNS, certbot, renewals, and 443 open on the instance | A distribution with the EC2 box as HTTP origin; an origin request policy that forwards `CloudFront-Viewer-Address`; the Express code reads that header, not the socket; the security group limited to CloudFront | A distribution with origin `xepxxvpbexkyxrwtrgqv.supabase.co`, caching off, POST allowed; `CloudFront-Viewer-Address` forwarded; a secret origin custom header; a trigger that trusts the header only when the secret matches and sets `server_timestamp = now()` |
| Matches the spec | No: still a separate GET | No: still a separate GET | **Yes**: IP, port and timestamp all describe the upload itself |
| Source port | Correct, but only if Node terminates TLS. If nginx or Caddy terminates TLS, Express sees the proxy's socket, and the proxy must pass `$remote_port` in a header. | From `CloudFront-Viewer-Address` (AWS documents it as `IP:port` of the viewer connection) | Same |
| Moving parts left | EC2 box + certificate | EC2 box + distribution | Distribution only. EC2 can be switched off once old builds stop calling it. |
| App change | New `TCP_TUPLE_URL` | New `TCP_TUPLE_URL` | Submission uploads go to the CloudFront URL; the tuple request goes away |
| Cost | Instance as now, plus a domain if we buy one | Instance as now; CloudFront's free tier covers our volume | CloudFront's free tier covers our volume; EC2 removed |

To check before relying on B or C (unverified):
- that CloudFront overwrites a client-sent `CloudFront-Viewer-Address`;
- the header's format for IPv6 viewers (split at the last colon);
- HTTP/3 must stay off on the distribution so the port is a TCP port.

Test with the public `get_raw_header` RPC through the distribution and `curl --local-port`.

## Recommendation

**Go straight to C** in the same app release that changes the URL. Every option needs a new app build, because
`TCP_TUPLE_URL` is compiled in. C is the only one that also makes the three fields describe the upload, as the
spec requires. That fixes the bigger problem behind #68 and removes the EC2 box. As side effects:
- the submission upload gets an HTTPS endpoint that we control, where the Phase 2 attestation check in
  doc/upload-authenticity.md could go;
- `source_ip` can come from a trusted header instead of the spoofable `x-forwarded-for` the current trigger
  uses.

If C is delayed, B is a smaller step that removes the cleartext request without a domain.

The EC2 box has to keep answering on plain HTTP until builds that call it are rare. Old builds keep using the
compiled-in URL, and submissions from them fail without it.

## Changes the app needs (either way)

1. **The URL.** Set `TCP_TUPLE_URL` (or, for C, the new upload base URL) to the `https://` endpoint in
   `cellwatch.properties` and the README/CONTRIBUTING examples. It's a build-time value, so it ships with the
   next release.
2. **Cleartext.** Once release builds make no plain-HTTP requests (including M-Lab's latency URLs; see above),
   turn cleartext off for release only. Debug builds
   still need it for local Supabase (`http://127.0.0.1:54321`) and a local M-Lab server
   (`MSAK_LOCAL_SERVER_SECURE=false`). For example:
   - remove `android:usesCleartextTraffic="true"` from `src/main/AndroidManifest.xml`; and
   - add `src/debug/res/xml/network_security_config.xml` permitting cleartext for `127.0.0.1`, `10.0.2.2` and
     `localhost` only, referenced from a `src/debug/AndroidManifest.xml` overlay.

   With targetSdk 35, release then blocks cleartext by default.
3. **For C:**
   - a migration for the trigger that parses `CloudFront-Viewer-Address` and sets `server_timestamp`;
   - the uploader stops calling the tuple service. One tuple copied onto every submission in a run goes away
     too.

   Apply the migration before the release (doc/release-checklist.md).
