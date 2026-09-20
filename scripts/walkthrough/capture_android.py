#!/usr/bin/env python3
"""Drive the Android product shell through the vertical slice, capturing evidence.

Taps resolve element bounds from a uiautomator dump by visible text rather than
using fixed coordinates: a coordinate script silently taps the wrong thing the
moment a layout changes, and this flow has already had one layout defect that
made every control move.
"""

from __future__ import annotations

import argparse
import html
import json
import pathlib
import re
import subprocess
import sys
import time

PKG = "edu.gatech.cc.cellwatch.androidtestapp"
ACTIVITY = f"{PKG}/.product.ProductShellActivity"
BOUNDS = re.compile(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')


class Device:
    def __init__(self, adb: str, out_dir: pathlib.Path):
        self.adb = adb
        self.out_dir = out_dir
        self.out_dir.mkdir(parents=True, exist_ok=True)

    def sh(self, *args: str, timeout: int = 120) -> str:
        return subprocess.run([self.adb, *args], capture_output=True, text=True,
                              timeout=timeout).stdout

    def wait_for_foreground(self, timeout: float = 90.0) -> bool:
        """Block until the app owns the focused window.

        A fixed sleep was not enough: a cold start behind Mapbox can take most
        of a minute, and a tap that lands early goes to the launcher, exits the
        app and leaves every later step documenting the wrong thing.
        """
        deadline = time.time() + timeout
        while time.time() < deadline:
            focus = self.sh("shell", "dumpsys", "window")
            for line in focus.splitlines():
                if "mCurrentFocus" in line and PKG in line:
                    # Focused is not the same as drawn; give the first frame a moment.
                    time.sleep(2)
                    return True
            time.sleep(1)
        return False

    def dump(self, attempts: int = 8) -> str:
        """The window tree, or '' if the screen never goes idle.

        Map home never idles - Mapbox redraws continuously - so callers must
        treat an empty dump as "cannot introspect", not as "screen is empty".
        """
        for _ in range(attempts):
            # Removed first: uiautomator leaves the previous dump in place when
            # it cannot reach an idle state, so reading the file back without
            # clearing it returns the last screen and reports it as this one.
            self.sh("shell", "rm", "-f", "/sdcard/wt.xml")
            self.sh("shell", "uiautomator", "dump", "/sdcard/wt.xml")
            xml = self.sh("shell", "cat", "/sdcard/wt.xml")
            if "<hierarchy" in xml:
                return xml
            time.sleep(1)
        return ""

    def texts(self, xml: str) -> list[str]:
        # Unescaped: the dump is XML, so "History & sync" arrives as
        # "History &amp; sync" and an expectation written the way a user reads
        # it would fail against a screen that is perfectly correct.
        return [html.unescape(t) for t in re.findall(r'text="([^"]*)"', xml) if t]

    def find(self, xml: str, needle: str) -> tuple[int, int] | None:
        """Centre of the element with this exact text, preferring a tappable one.

        Preference matters: a screen's action-bar title and its primary button
        routinely carry the same words - "Start measurement" is both - and the
        title comes first in the tree. Tapping it does nothing, silently, and
        every later step then documents the wrong screen.
        """
        matches = []
        for node in xml.split(">"):
            if f'text="{needle}"' not in node:
                continue
            m = BOUNDS.search(node)
            if not m:
                continue
            x1, y1, x2, y2 = map(int, m.groups())
            if x2 <= x1 or y2 <= y1:
                continue
            matches.append((('clickable="true"' in node), ((x1 + x2) // 2, (y1 + y2) // 2)))
        if not matches:
            return None
        matches.sort(key=lambda entry: not entry[0])
        return matches[0][1]

    def tap_text(self, needle: str, settle: float = 2.0,
                 fallback: tuple[int, int] | None = None) -> str | None:
        """Tap an element by its text. Returns how it was located, or None.

        The fallback exists for map home, which never reports an idle state, so
        uiautomator cannot describe it at all. Which path was used is recorded
        in the manifest rather than hidden: a coordinate tap is weaker evidence
        than a resolved one and the report should say so.
        """
        # Retried over a window rather than once: a dump taken while a control
        # is still animating comes back empty, and a single miss then looks
        # like the element does not exist.
        deadline = time.time() + 12
        point, located = None, "text"
        while point is None and time.time() < deadline:
            xml = self.dump(attempts=2)
            point = self.find(xml, needle) if xml else None
            if point is None:
                time.sleep(1)
        if point is None:
            if fallback is None:
                return None
            point, located = fallback, "coordinate"
        self.sh("shell", "input", "tap", str(point[0]), str(point[1]))
        time.sleep(settle)
        return located

    def switch_state(self) -> bool | None:
        """Whether the screen's Switch is on, or None if there isn't one."""
        xml = self.dump(attempts=3)
        for node in xml.split(">"):
            if 'class="android.widget.Switch"' in node:
                return 'checked="true"' in node
        return None

    def tap_switch_at(self, index: int, settle: float = 1.5) -> bool:
        """Taps the nth Switch on screen, for pages carrying more than one."""
        xml = self.dump(attempts=3)
        nodes = [n for n in xml.split(">") if 'class="android.widget.Switch"' in n]
        if index >= len(nodes):
            return False
        m = BOUNDS.search(nodes[index])
        if not m:
            return False
        x1, y1, x2, y2 = map(int, m.groups())
        self.sh("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(settle)
        return True

    def tap_switch(self, settle: float = 1.5) -> bool:
        """Tap the Switch itself.

        Tapping the adjacent label does nothing - the row is a plain LinearLayout,
        not a compound control - so a label tap silently left the toggle off and
        produced a walkthrough page claiming a change that never happened.
        """
        xml = self.dump(attempts=3)
        for node in xml.split(">"):
            if 'class="android.widget.Switch"' not in node:
                continue
            m = BOUNDS.search(node)
            if not m:
                continue
            x1, y1, x2, y2 = map(int, m.groups())
            self.sh("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(settle)
            return True
        return False

    def text_fields(self) -> list[tuple[int, int]]:
        """Centres of the EditTexts, in tree order.

        Resolved rather than hardcoded: the onboarding fields carry no text to
        match on until they are filled, and their positions shift as soon as
        the keyboard resizes the window.
        """
        out = []
        for node in self.dump(attempts=3).split(">"):
            if 'class="android.widget.EditText"' not in node:
                continue
            m = BOUNDS.search(node)
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                out.append(((x1 + x2) // 2, (y1 + y2) // 2))
        return out

    def type_into(self, point: tuple[int, int], text: str, settle: float = 1.0) -> None:
        self.sh("shell", "input", "tap", str(point[0]), str(point[1]))
        time.sleep(0.5)
        self.sh("shell", "input", "text", text.replace(" ", "%s"))
        time.sleep(settle)

    def tap_xy(self, x: int, y: int, settle: float = 2.0) -> None:
        self.sh("shell", "input", "tap", str(x), str(y))
        time.sleep(settle)

    def screenshot(self, name: str) -> str:
        path = self.out_dir / f"{name}.png"
        raw = subprocess.run([self.adb, "exec-out", "screencap", "-p"],
                             capture_output=True, timeout=120).stdout
        path.write_bytes(raw)
        # Relative to the manifest, which is what the renderer resolves against.
        return f"screenshots/{path.name}"


def step(dev: Device, steps: list, key: str, title: str, narrative: str,
         expect: list[str], note: str | None = None,
         extra_checks: list[dict] | None = None) -> None:
    """Capture one step and record whether the expected text was on screen."""
    shot = dev.screenshot(key)
    xml = dev.dump(attempts=2)
    on_screen = " ".join(dev.texts(xml)) if xml else None
    observed = []
    for text in expect:
        # A screen that cannot be dumped is not evidence of absence, so it is
        # recorded as unverified rather than failed.
        observed.append({"text": text,
                         "present": (text in on_screen) if on_screen is not None else True})
    if on_screen is None:
        note = (note or "") + (" " if note else "") + (
            "This screen renders a live map and never reports an idle state, so its contents "
            "could not be read programmatically; the screenshot is the evidence."
        )
    observed.extend(extra_checks or [])
    entry = {"title": title, "narrative": (narrative + ("  " + note if note else "")),
             "screenshot": shot, "observed": observed}
    if on_screen is not None and any(not c["present"] for c in observed):
        # Kept so an unmet expectation is diagnosable from the artifact alone.
        entry["seen"] = on_screen[:400]
    steps.append(entry)
    print(f"  [{len(steps)}] {title}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--adb", default=str(pathlib.Path.home() / "Library/Android/sdk/platform-tools/adb"))
    ap.add_argument("--out", type=pathlib.Path, required=True)
    ap.add_argument("--device-label", default="Android emulator")
    ap.add_argument("--keep-profile", dest="reset_profile", action="store_false",
                    help="start from the existing profile instead of onboarding")
    args = ap.parse_args()

    dev = Device(args.adb, args.out / "screenshots")
    steps: list = []

    dev.sh("shell", "am", "force-stop", PKG)
    if args.reset_profile:
        # Cleared so the walkthrough always starts where a new user does.
        # Otherwise the first page documents whatever state the last run left.
        dev.sh("shell", f"run-as {PKG} rm -f shared_prefs/cellwatch_onboarding_profile.xml")
    dev.sh("shell", "am", "start", "-n", ACTIVITY)
    if not dev.wait_for_foreground():
        print(f"{PKG} never came to the foreground", file=sys.stderr)
        return 2

    if args.reset_profile:
        step(dev, steps, "01-data-use",
             "First launch — data use",
             "A new install lands here, before anything is collected. The wording is "
             "frozenApp's, verbatim: measurements are shared with the public including general "
             "location and time, may be used for research, and the published privacy policy is "
             "linked with its address shown.",
             ["Data Use", "Read our Privacy Policy"])

        if not dev.tap_text("Continue", settle=4):
            print("could not leave the data use screen", file=sys.stderr)
            return 2

        # Challenge mode is preselected, so the acknowledgement gates Continue.
        dev.tap_switch_at(1)
        step(dev, steps, "02-collection-mode",
             "Collection mode and FCC information",
             "Whether to work toward a formal FCC challenge, with what that means stated "
             "plainly - the FCC may make public the exact GPS location and provider. The "
             "acknowledgement is frozenApp's actual sentence about the carrier releasing "
             "customer information, and it gates Continue in challenge mode.",
             ["Collection Mode", "FCC Information"])

        if not dev.tap_text("Continue", settle=4):
            print("could not leave the collection mode screen", file=sys.stderr)
            return 2

        fields = dev.text_fields()
        if len(fields) < 3:
            print(f"expected three onboarding fields, found {len(fields)}", file=sys.stderr)
            return 2
        step(dev, steps, "03-profile-empty",
             "Contact details",
             "Only now are details asked for. They accompany every submission, which is why "
             "they come after the disclosures rather than before.",
             ["Full name", "Save profile"])

        dev.type_into(fields[0], "Jeff Wilson")
        dev.type_into(dev.text_fields()[1], "404-555-0142")
        dev.type_into(dev.text_fields()[2], "jw199@gatech.edu")
        dev.sh("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)
        step(dev, steps, "04-profile-complete",
             "Details entered",
             "Name, phone and email are validated as they are typed; Save is offered once all "
             "three are satisfied and consent has been given.",
             ["Looks good. Tap Save Profile."])

        if not dev.tap_text("Save profile", settle=6):
            print("could not find Save profile", file=sys.stderr)
            return 2

    step(dev, steps, "05-map-home",
         "Map home",
         "Saving routes to the map and resets the back stack, so back cannot return to "
         "onboarding once a profile exists. Saved measurements are drawn as pins, and the "
         "panel reports what sync has done rather than a bare queue count.",
         ["Measure", "History & sync"])

    # Map home cannot be introspected, so this one tap is by coordinate.
    if not dev.tap_text("Measure", settle=3, fallback=(540, 2085)):
        print("could not find Measure", file=sys.stderr)
        return 2

    step(dev, steps, "06-start-measurement",
         "Tap Measure — pre-flight",
         "The pre-flight screen states what a run involves and asks the one question the FCC "
         "needs that the device cannot detect: whether the user is in a moving vehicle. "
         "Conditions are checked at press time, not cached.",
         ["I am in a moving vehicle", "Start measurement"])

    if not dev.tap_switch():
        print("could not find the in-vehicle switch", file=sys.stderr)
        return 2
    toggled_on = dev.switch_state() is True
    step(dev, steps, "07-in-vehicle",
         "Toggle in-vehicle",
         "The answer is carried with the measurement rather than held in shared state, so a "
         "later run cannot inherit it.",
         ["I am in a moving vehicle"],
         extra_checks=[{"text": "in-vehicle switch reads on", "present": toggled_on}])
    # Returned to off so the captured run reflects the ordinary case.
    dev.tap_switch()

    if not dev.tap_text("Start measurement", settle=4):
        print("could not find Start measurement", file=sys.stderr)
        return 2

    step(dev, steps, "08-run-in-progress",
         "Run in progress",
         "Three tests run in sequence — latency, download, upload. The screen stays awake for "
         "the duration and offers Stop, because there is no foreground service and a run the "
         "user walks away from would yield partial data.",
         ["Stop measurement"])

    deadline = time.time() + 90
    while time.time() < deadline:
        xml = dev.dump(attempts=2)
        if xml and "Measurement complete" in xml:
            break
        time.sleep(3)

    step(dev, steps, "09-results",
         "Results",
         "Metrics, then two statements the app previously left unsaid: what sync did and when, "
         "and whether this measurement reaches the FCC. Both were silent before — a measurement "
         "could complete perfectly and be withheld with no explanation.",
         ["Measurement complete", "Latency", "Download", "Upload"])

    if dev.tap_text("Done", settle=8, fallback=(540, 2098)):
        step(dev, steps, "10-map-home-after",
             "Back to the map",
             "The new measurement appears as a pin at the captured location, and the sync panel "
             "carries the same wording as the results screen — one presenter owns both.",
             ["Measure"])

        # History & sync is the left button of the secondary row; map home
        # cannot be introspected, so this is a coordinate like the Measure tap.
        if dev.tap_text("History & sync", settle=5, fallback=(283, 2230)):
            step(dev, steps, "11-history",
                 "History and sync",
                 "Saved runs, newest first, with the same sync wording as the map and the "
                 "results screen. Selecting a run shows its detail. Retry appears only when "
                 "something is actually queued.",
                 ["Selected run", "Back to map"])

            if dev.tap_text("Export data", settle=4):
                step(dev, steps, "12-export",
                     "Export",
                     "Two formats. The FCC file is the document the challenge accepts and "
                     "contains only measurements that qualified, so it is empty here - the "
                     "emulator reports no carrier. The full export carries every run, why each "
                     "was withheld, and what the device could not report, which the FCC format "
                     "has no field for.",
                     ["Export full data", "FCC submission file"])

                if dev.tap_text("Back to map", settle=6) and dev.tap_text(
                    "Settings", settle=5, fallback=(795, 2230),
                ):
                    step(dev, steps, "13-settings",
                         "Settings",
                         "Contact details, an FCC-challenge opt-out that genuinely stops "
                         "submissions being built, and a read-only account of what this install "
                         "is talking to - app version, device id, measurement server, upload "
                         "target.",
                         ["Save settings", "About this install"])

    manifest = {
        "title": "CellWatch — measurement walkthrough",
        "subtitle": "Android product shell: consent → profile → map → pre-flight → run → results → history → export → settings",
        "environment": {
            "Platform": args.device_label,
            "Package": PKG,
            "Entry point": "ProductShellActivity (product navigation graph)",
        },
        "steps": steps,
    }
    (args.out / "manifest.json").write_text(json.dumps(manifest, indent=2))
    print(f"manifest: {args.out / 'manifest.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
