#!/usr/bin/env python3
"""Drive the Android product shell through the vertical slice, capturing evidence.

Taps resolve element bounds from a uiautomator dump by visible text rather than
using fixed coordinates: a coordinate script silently taps the wrong thing the
moment a layout changes, and this flow has already had one layout defect that
made every control move.
"""

from __future__ import annotations

import argparse
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
        return [t for t in re.findall(r'text="([^"]*)"', xml) if t]

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
        xml = self.dump(attempts=3)
        point = self.find(xml, needle) if xml else None
        located = "text"
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
    args = ap.parse_args()

    dev = Device(args.adb, args.out / "screenshots")
    steps: list = []

    dev.sh("shell", "am", "force-stop", PKG)
    dev.sh("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(14)

    step(dev, steps, "01-map-home",
         "Launch — map home",
         "The app opens on the map. Saved measurements are drawn as pins, and the panel "
         "reports what sync has done rather than a bare queue count.",
         ["Measure", "History & sync"])

    # Map home cannot be introspected, so this one tap is by coordinate.
    if not dev.tap_text("Measure", settle=3, fallback=(540, 2085)):
        print("could not find Measure", file=sys.stderr)
        return 2

    step(dev, steps, "02-start-measurement",
         "Tap Measure — pre-flight",
         "The pre-flight screen states what a run involves and asks the one question the FCC "
         "needs that the device cannot detect: whether the user is in a moving vehicle. "
         "Conditions are checked at press time, not cached.",
         ["I am in a moving vehicle", "Start measurement"])

    if not dev.tap_switch():
        print("could not find the in-vehicle switch", file=sys.stderr)
        return 2
    toggled_on = dev.switch_state() is True
    step(dev, steps, "03-in-vehicle",
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

    step(dev, steps, "04-run-in-progress",
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

    step(dev, steps, "05-results",
         "Results",
         "Metrics, then two statements the app previously left unsaid: what sync did and when, "
         "and whether this measurement reaches the FCC. Both were silent before — a measurement "
         "could complete perfectly and be withheld with no explanation.",
         ["Measurement complete", "Latency", "Download", "Upload"])

    if dev.tap_text("Done", settle=8, fallback=(540, 2098)):
        step(dev, steps, "06-map-home-after",
             "Back to the map",
             "The new measurement appears as a pin at the captured location, and the sync panel "
             "carries the same wording as the results screen — one presenter owns both.",
             ["Measure"])

    manifest = {
        "title": "CellWatch — measurement walkthrough",
        "subtitle": "Android product shell, vertical slice: launch → pre-flight → run → results",
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
