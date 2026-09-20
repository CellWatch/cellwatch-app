#!/usr/bin/env python3
"""Turn the iOS XCUITest captures into a walkthrough manifest.

The test drives and screenshots; the narrative lives here. Keeping prose out of
the test means rewording the report does not mean recompiling a test bundle,
and the Android capture keeps its narrative in the same style.

Each step's expectations mirror what the XCUITest asserted before capturing, so
a step is only marked verified because the test would have failed otherwise.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import shutil
import sys

STEPS = [
    ("01-data-use", "First launch — data use",
     "A new install lands here, before anything is collected. The wording is frozenApp's, "
     "verbatim: measurements are shared with the public including general location and time, "
     "may be used for research, and the published privacy policy is linked with its address "
     "shown.",
     ["data-use disclosures presented"]),
    ("02-collection-mode", "Collection mode and FCC information",
     "Whether to work toward a formal FCC challenge, with what that means stated plainly - the "
     "FCC may make public the exact GPS location and provider. The acknowledgement is "
     "frozenApp's actual sentence about the carrier releasing customer information, and it "
     "gates Continue in challenge mode.",
     ["collection mode offered", "FCC acknowledgement required"]),
    ("03-profile-empty", "Contact details",
     "Only now are details asked for. They accompany every submission, which is why they come "
     "after the disclosures rather than before.",
     ["profile form presented"]),
    ("04-profile-complete", "Details entered",
     "Name, phone and email are validated as they are typed; Save is offered once all three "
     "are satisfied and consent has been given.",
     ["form accepted"]),
    ("05-map-home", "Map home",
     "Saving routes to the map and resets the back stack, so the back button cannot return to "
     "onboarding once a profile exists. Saved measurements are drawn as pins, and the panel "
     "reports what sync has done rather than a bare queue count.",
     ["Measure button present"]),
    ("06-start-measurement", "Tap Measure — pre-flight",
     "The pre-flight screen states what a run involves and asks the one question the FCC needs "
     "that the device cannot detect: whether the user is in a moving vehicle. Conditions are "
     "checked at press time, not cached.",
     ["Start measurement button present", "'I am in a moving vehicle' present"]),
    ("07-in-vehicle", "Toggle in-vehicle",
     "The answer travels with the measurement rather than living in shared state, so a later "
     "run cannot inherit it.",
     ["in-vehicle switch toggled"]),
    ("08-wifi-confirmation", "Wi-Fi confirmation",
     "The simulator is always on Wi-Fi, and the FCC only accepts cellular measurements, so the "
     "app asks before spending half a minute on a run it cannot submit. On a handset with "
     "Wi-Fi off this step does not appear.",
     ["'Measure anyway' offered"]),
    ("09-run-in-progress", "Run in progress",
     "Three tests run in sequence — latency, download, upload. The screen stays awake and "
     "offers Stop, because iOS has no foreground-service equivalent and a run the user leaves "
     "would yield partial data.",
     ["Stop measurement offered"]),
    ("10-results", "Results",
     "Metrics, then two statements the app previously left unsaid: what sync did and when, and "
     "whether this measurement reaches the FCC. Both were silent before — a measurement could "
     "complete perfectly and be withheld with no explanation.",
     ["run reached completion (Done offered)"]),
    ("11-map-home-after", "Back to the map",
     "The new measurement appears as a pin at the captured location, and the sync panel carries "
     "the same wording as the results screen — one presenter owns both.",
     ["returned to map home"]),
    ("12-history", "History and sync",
     "Saved runs, newest first, with the same sync wording as the map and the results screen. "
     "Selecting a run shows its detail. Retry appears only when something is actually queued.",
     ["run list rendered", "'Back to map' offered"]),
    ("13-export", "Export",
     "Two formats. The FCC file is the document the challenge accepts and contains only "
     "measurements that qualified, so it is empty here - the simulator has no carrier and is "
     "on Wi-Fi. The full export carries every run, why each was withheld, and what the device "
     "could not report, which the FCC format has no field for.",
     ["both export formats offered"]),
    ("14-settings", "Settings",
     "Contact details, an FCC-challenge opt-out that genuinely stops submissions being built, "
     "and a read-only account of what this install is talking to - app version, device id, "
     "measurement server, upload target.",
     ["settings form and diagnostics presented"]),
]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--captures", type=pathlib.Path,
                    default=pathlib.Path("/tmp/cellwatch-ui-flow/ios/product-walkthrough"))
    ap.add_argument("--out", type=pathlib.Path, required=True)
    ap.add_argument("--device-label", default="iOS Simulator")
    args = ap.parse_args()

    if not args.captures.is_dir():
        print(f"no captures at {args.captures}; run the XCUITest first", file=sys.stderr)
        return 2

    shots = args.out / "screenshots"
    shots.mkdir(parents=True, exist_ok=True)

    steps = []
    for key, title, narrative, expectations in STEPS:
        src = args.captures / f"{key}.png"
        if not src.exists():
            # A step the test skipped (the Wi-Fi prompt does not appear on
            # cellular) is omitted rather than shown as a hole in the story.
            continue
        shutil.copy2(src, shots / src.name)
        steps.append({
            "title": title,
            "narrative": narrative,
            "screenshot": f"screenshots/{src.name}",
            "observed": [{"text": e, "present": True} for e in expectations],
        })

    manifest = {
        "title": "CellWatch — measurement walkthrough",
        "subtitle": "iOS product shell: consent → profile → map → pre-flight → run → results → history → export → settings",
        "environment": {
            "Platform": args.device_label,
            "Bundle": "edu.gatech.cc.cellwatch",
            "Entry point": "ProductShell (-CellWatchProductShell)",
            "Evidence": "XCUITest ProductWalkthroughUiTests — every step below was asserted "
                        "before its screenshot was taken",
        },
        "steps": steps,
    }
    (args.out / "manifest.json").write_text(json.dumps(manifest, indent=2))
    print(f"manifest: {args.out / 'manifest.json'} ({len(steps)} steps)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
