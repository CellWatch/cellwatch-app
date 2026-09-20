# Outstanding work

Carried forward so it is not lost between sessions. Updated 2026-09-20.

## Verification owed

1. **Device run — telephony observation.** Cells and radio-generation change detection have
   never fired on a real radio, on either platform. A simulator has no radio, so the observer
   only ever exercised its empty path, and the sparse iOS Cell Object has never been produced.
   Covers parity plan items 1 and 2.
2. **Android on hardware — nothing, ever.** Every Android change from 2026-09-18 (persistent
   device credential, telephony observer, wake guard, cancel-on-stop) is verified by compile
   and unit tests only. iOS at least has real cellular runs behind it.

## Known defects, not yet fixed

3. **A permanently-rejected record retries forever.** Sync has no notion of a row the server
   will never accept. When iOS was writing null location timestamps, twelve measurements failed
   on every attempt indefinitely; the rows were purged on 2026-09-19, but nothing stops it
   recurring. Needs a failure count or a `rejected` state so the queue can drain.
4. **Per-test results only appear when a run finishes.** `MeasurementSequenceProgressListener`
   emits stage transitions, not results, so latency sits at `--` on the run screen until the
   whole sequence completes even though it finished seconds earlier.
5. **Most of the Spanish is unreviewed.** The whole product app now renders in Spanish - Map
   home, the run screen, History, Settings, Export, consent, and every sync status line - but
   only about thirty of the strings are frozenApp's reviewed `values-es` wording. The rest,
   roughly 140, were translated by Claude and have had no native-speaker or subject-matter
   review. frozenApp's translator worked from the FCC's own bilingual challenge-process pages;
   the FCC vocabulary here follows that glossary ("impugnación", "medición", "proveedor"), but
   the sentences around it do not carry the same authority. Each copy object says in its KDoc
   which of the two it is. Worth a review pass before anything ships publicly, starting with
   `FccSubmissionOutcomeMessage`, which tells a user why their measurement will not reach
   the FCC.

   There is also no translator-facing resource format: the two languages sit adjacent in Kotlin
   source under `domain/**/*Copy.kt`. That is deliberate - it keeps one source of truth for both
   platforms and puts the two languages where a reviewer sees them together - but a real
   translator would want an export.

6. **The privacy policy is not versioned or recorded.** The app links to
   `sites.gatech.edu/cellwatch/android-app/app-privacy-policy/` but does not record which
   version a user agreed to, or when. frozenApp did not either, but for a study that publishes
   location data it is worth knowing.

## Deferred by decision

7. **Mapbox `sk.` secret token** ships in the app bundle. High severity, extractable from any
   build. Deferred until nearer deployment; full detail in `PRE_DEPLOYMENT_CHECKLIST.md` item 1.
8. **`server_source_port`** — blocked on the AWS tuple service decision and external
   stakeholders. `PRE_DEPLOYMENT_CHECKLIST.md` item 5.
9. **Crashlytics** — low priority. The shared logger landed without it; reinstating it is a
   data-governance question, not a technical one.

## External action

10. **Send `FCC_IOS_DISCREPANCIES.md`** to the FCC and ask for guidance. Ready and pushed. The
   standing strategy is not to block development on the reply, but the clock only starts once
   it goes.

## Build fragility

11. **msak-client-kmp 0.6.0 exists only in this machine's `~/.m2`.** cellwatch pins it and
   resolves via `mavenLocal()`, so a fresh clone or another machine cannot build until someone
   runs `publishToMavenLocal` in msak — and nothing says so. Either set
   `cellwatch.useLocalMsak=true` to resolve from source through the existing `includeBuild`, or
   document the publish step.
