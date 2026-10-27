# Knowing that an upload came from the CellWatch app

Investigation for #70. Nothing today stops another program from posing as CellWatch. This document covers the
current model, the threat, and the options, and recommends Play Integrity. The recommendation needs a team
decision; it hasn't been built.

## Current model

From `supabase/full_schema.sql` (snapshot of the live project) and
`app/src/main/java/edu/gatech/cc/cellwatch/data/network/NetworkMeasurementDatasource.kt`:

1. On first upload, the app picks a random UUID as its device id and calls the RPC `public.register_device(device_id)`
   with the project's **anon key**. The function is `SECURITY DEFINER` and `EXECUTE` is granted to `anon`. It
   creates a random 32-byte secret, stores `crypt(secret, gen_salt('bf'))` in `private.devices`, and returns
   the secret once. A second registration of the same id fails.
2. The app stores the secret in DataStore, encrypted with an Android Keystore key (`Encryptor.kt`). It sends
   the id and secret on every request as `X-Device-ID` / `X-Device-Secret`.
3. RLS checks the headers. `private.verified_device_id()` bcrypt-verifies the secret.
   - The INSERT policies on `measurements` and `fcc_submissions` require `device_id = verified_device_id()`.
   - The child tables (`cells`, `locations`, `latency_data`, `upload_download_data`) require the parent
     measurement to belong to that device.
4. Every inserted `fcc_submissions` row is queued for the FCC's production BDC by the
   `trg_auto_post_fcc_submission` trigger (`fcc_env = 'prod'`). The retry job posts it with
   `private.post_fcc_submission()`.

This proves that a request comes from **the same installation that registered the id**. It says nothing about
what that installation is.

## Threat model

- **The anon key is public.** It is compiled into every APK (`BuildConfig.SUPABASE_API_KEY`) and can be read
  with standard tools. Once the repo is open source (#107), the whole protocol is documented too.
- **So anyone can register a device and upload.** A script can call `register_device` with a fresh UUID, get a
  secret, and insert measurements and FCC submissions that pass every RLS check.
- **The FCC token then posts that data as CellWatch.** The trigger queues every submission for the BDC, so
  fabricated Consumer Challenge data goes to the FCC under our identity, with any contact details.
- **The "server-measured" fields are client-supplied.** `source_ip`, `source_port` and `server_timestamp` come
  from the app, which copies them from the EC2 tuple service (see doc/tcp-tuple-https.md). The source-IP
  trigger only fills `source_ip` when it's null, from `X-Forwarded-For`, which the client can spoof.
- Out of scope here: a real user running the real app on a real phone and lying about conditions (mock GPS,
  for example). Mitigations for that are listed at the end.

## What the FCC API token protects, and what it doesn't

The BDC token is held in Supabase Vault (`private.fcc_submission_info.hash_secret_id` →
`vault.decrypted_secrets`). Only `private.post_fcc_submission()`, running inside the database, sends it, as the
`hash_value` header. `anon` has no `USAGE` on schema `private`, so the API can't call that function or read the
table. The app never sees the token.

- **It protects:** nobody can post to the BDC as CellWatch without access to our database, and the FCC can
  attribute everything we send to CellWatch.
- **It doesn't protect:** where the data in our database came from. The trigger posts every row that passes
  RLS, and RLS only checks the device secret (see above). In effect, the token vouches for anything anyone
  inserts with the anon key.

## Options

| Option | What it proves | Spoofable by a script with the anon key? | Notes |
|---|---|---|---|
| App reads its own APK signature and sends it | Nothing | Yes. The signing certificate digest is public (it's in every APK), so a script sends the same value. | Only useful when a trusted third party reports the signature, which is what Play Integrity does. |
| Secret embedded in the app | Nothing durable | Yes. It's extractable from the APK, and from the source once the repo is public. | Same weakness as the anon key. |
| Certificate pinning | That the app's own connections reach our server | Not applicable: a script doesn't use the app's connections. | Protects traffic on hostile networks; doesn't authenticate the client. Also adds outage risk when Supabase or CloudFront rotate certificates. Not recommended for this goal. |
| SafetyNet Attestation | (was) device + app integrity | n/a | Deprecated in favour of Play Integrity. Google's turndown schedule ended in 2025 (check the exact date if it matters). Not an option. |
| **Google Play Integrity API** | The request comes from **our Play-distributed binary** (`appRecognitionVerdict = PLAY_RECOGNIZED`, signing cert digest, versionCode) on a device that passes Google's checks (`deviceRecognitionVerdict`), and is bound to a value we choose (`requestHash` / nonce) | No, not without a real device running our app. See limits below. | Free within quota; the default is 10,000 requests/day per app (verify in Play Console). Needs Google Play services on the device. |
| Apple App Attest (iOS) | The equivalent for iOS: a key pair attested by Apple to our App ID, then per-request assertions | No | Needed for the KMP iOS app. Server-side verification is more work (CBOR attestation object, Apple certificate chain). |
| Firebase App Check | Wraps Play Integrity (Android) and App Attest/DeviceCheck (iOS) and issues a short-lived JWT | No | One server check for both platforms: verify the App Check JWT against Firebase's public JWKS. We already ship Firebase (Crashlytics). Check its quota and pricing before choosing it. |
| Rate limits | Nothing about origin | Yes, but they bound the damage | Cheap and worth doing regardless (see below). |

## Recommendation: Play Integrity at registration, optionally per FCC submission

> **Proposal.** It needs a team decision on the device verdict to require, and on what happens to devices that
> registered before attestation.

### Phase 1: attested registration

1. **App.** Before registering, request a standard Play Integrity token, with `requestHash` set to the
   base64url SHA-256 of the device id. Then call an Edge Function instead of the RPC:
   `POST /functions/v1/register-device {device_id, integrity_token}`. This needs the
   `com.google.android.play:integrity` dependency and a Google Cloud project linked in Play Console.
2. **Edge Function `register-device`.** It holds a Google service-account key as a function secret and:
   - calls `https://playintegrity.googleapis.com/v1/edu.gatech.cc.cellwatch:decodeIntegrityToken`;
   - checks:
     - `requestDetails.requestPackageName = edu.gatech.cc.cellwatch`;
     - `requestDetails.requestHash` matches the device id;
     - `timestampMillis` is within 5 minutes;
     - `appIntegrity.appRecognitionVerdict = PLAY_RECOGNIZED`;
     - `certificateSha256Digest` is our Play app-signing certificate;
     - `deviceRecognitionVerdict` contains `MEETS_DEVICE_INTEGRITY` (proposal; `MEETS_BASIC_INTEGRITY` accepts
       more devices, rooted ones included);
   - on success, calls a new `private.register_attested_device(device_id, verdict jsonb)` using the service
     role. It inserts into `private.devices` and returns the secret, as `register_device` does today.
   - Rate-limit the function by `cf-connecting-ip`, which Cloudflare sets and clients can't spoof (unlike
     `x-forwarded-for`).
3. **Migration.**
   - Add `created_on timestamptz default now()`, `attested_at timestamptz` and `attestation jsonb` to
     `private.devices`. Today there's no timestamp, so we can't tell old devices from new.
   - Change `private.auto_post_fcc_submission()` to queue submissions only from attested devices, and to put
     the rest in a held state that a person reviews.
4. **Rollout order.**
   - Deploy the function and the migration.
   - Release the app.
   - After old builds have mostly updated, `revoke execute on function public.register_device(uuid) from anon,
     authenticated`.
   - Decide what to do with devices registered before attestation. Until the revoke, new installs of old
     builds can still register without attestation, which is why the trigger has to hold their submissions.

Phase 1 calls Play Integrity about once per install, far below the quota.

### Phase 2 (optional): attest each FCC submission

Phase 1 doesn't stop someone who extracts a device secret from an attested install (for example on a rooted
phone) from using it in a script. To bind the data itself:
- the app attaches a fresh token, with `requestHash` = SHA-256 of the canonical submission (id, device id,
  device timestamp, test ids), to each FCC submission upload;
- an Edge Function in the submission upload path verifies it before inserting.

This costs one Play Integrity call per submission. That's within quota at current volume, but measure it first.
If the CloudFront route in doc/tcp-tuple-https.md is built, the same upload path can carry this check.

### iOS

Use App Attest for the same flow (attest a key at registration, then sign assertions per submission), or use
Firebase App Check on both platforms. Then the Edge Function only has to verify one kind of JWT. Pick one
before building Phase 1, so the server isn't written twice.

### Effort and cost (estimates)

- Play Integrity is free within quota. Setup: a Google Cloud project, a service account, and the integrity
  dependency in the app.
- Edge Function, migration and DB tests: a few days. App change plus a staged rollout: a release cycle. The
  rollout depends on doc/environments.md (a staging project to test against) and doc/release-checklist.md.

## What attestation can't prevent

- **Spoofed verdicts on rooted devices.** Some root setups can pass `MEETS_BASIC_INTEGRITY`, and sometimes
  `MEETS_DEVICE_INTEGRITY`, by spoofing device properties or using leaked attestation keys.
  `MEETS_STRONG_INTEGRITY` (hardware-backed) is harder to fake, but excludes many legitimate phones. Google has
  been tightening these verdicts; check the current definitions before choosing.
- **A genuine app fed false inputs:** mock GPS, or a hooking framework on a rooted phone changing values before
  upload. Attestation proves which binary sent the request, not that its measurements are true.
- **Builds outside Play.** Sideloaded, self-built or F-Droid builds of the open-source app get
  `UNRECOGNIZED_VERSION`, so they can't register for FCC submission. That's intended, but say so in the README
  so contributors use a staging backend (doc/environments.md).

## Do these anyway

- **Rate limits in the database.** Each test group takes most of a minute, so more than about 60 groups per
  device per hour, or a few hundred FCC submissions per device per day, isn't plausible. A `BEFORE INSERT`
  trigger with an index on `(device_id, created_on)` can reject or hold the excess. Registrations per
  `cf-connecting-ip` per hour can be capped in the Edge Function.
- **Record mock locations.** The app doesn't check `Location.isMock()` (API 31+) or `isFromMockProvider()`
  today. Recording the flag lets the server hold those tests. It's a new request field, so apply the migration
  before releasing the app.
- **Plausibility checks before posting to the FCC:**
  - does the source IP's network match the SIM's carrier?
  - is the location consistent with the reported cells?
  - is the device timestamp close to server time?
- **Measure the source tuple on the server** instead of trusting the app (doc/tcp-tuple-https.md).
