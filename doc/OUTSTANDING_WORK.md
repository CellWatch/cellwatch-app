# Outstanding work

Carried forward so it is not lost between sessions. Updated 2026-09-19.

## Verification owed

1. **Device run — telephony observation.** Cells and radio-generation change detection have
   never fired on a real radio, on either platform. A simulator has no radio, so the observer
   only ever exercised its empty path, and the sparse iOS Cell Object has never been produced.
   Covers parity plan items 1 and 2.
2. **Android on hardware — nothing, ever.** Every Android change from 2026-09-18 (persistent
   device credential, telephony observer, wake guard, cancel-on-stop) is verified by compile
   and unit tests only. iOS at least has real cellular runs behind it.

## Deferred by decision

3. **Mapbox `sk.` secret token** ships in the app bundle. High severity, extractable from any
   build. Deferred until nearer deployment; full detail in `PRE_DEPLOYMENT_CHECKLIST.md` item 1.
4. **`server_source_port`** — blocked on the AWS tuple service decision and external
   stakeholders. `PRE_DEPLOYMENT_CHECKLIST.md` item 5.
5. **Crashlytics** — low priority. The shared logger landed without it; reinstating it is a
   data-governance question, not a technical one.

## External action

6. **Send `FCC_IOS_DISCREPANCIES.md`** to the FCC and ask for guidance. Ready and pushed. The
   standing strategy is not to block development on the reply, but the clock only starts once
   it goes.

## Build fragility

7. **msak-client-kmp 0.6.0 exists only in this machine's `~/.m2`.** cellwatch pins it and
   resolves via `mavenLocal()`, so a fresh clone or another machine cannot build until someone
   runs `publishToMavenLocal` in msak — and nothing says so. Either set
   `cellwatch.useLocalMsak=true` to resolve from source through the existing `includeBuild`, or
   document the publish step.
