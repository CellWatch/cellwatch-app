#!/usr/bin/env python3
"""Render a captured walkthrough into a single PDF.

Input is a manifest produced by the capture scripts, so the same renderer serves
Android and iOS and any scenario, rather than each capture path growing its own
report writer - which is how the previous Markdown report ended up hard-coding
its six flows.

Deliberately HTML + wkhtmltopdf: both are already installed on this machine, and
an intermediate HTML file is inspectable when a page lays out wrong.
"""

from __future__ import annotations

import argparse
import base64
import html
import json
import pathlib
import subprocess
import sys
from datetime import datetime, timezone

CSS = """
@page { size: Letter; margin: 14mm 14mm 16mm 14mm; }
body { font-family: -apple-system, 'Helvetica Neue', Helvetica, Arial, sans-serif;
       color: #1A1A1A; font-size: 11pt; line-height: 1.45; }
h1 { font-size: 23pt; margin: 0 0 2mm 0; color: #0B3C5D; }
h2 { font-size: 14pt; margin: 0 0 1mm 0; color: #0B3C5D; }
.sub { color: #5A5A5A; font-size: 10pt; margin: 0 0 6mm 0; }
.meta { border: 1px solid #DFDFDF; border-radius: 3mm; padding: 4mm 5mm;
        background: #F7F7F9; margin-bottom: 7mm; }
.meta table { border-collapse: collapse; width: 100%; }
.meta td { padding: 0.8mm 0; font-size: 10pt; vertical-align: top; }
.meta td.k { color: #5A5A5A; width: 34mm; }
.step { page-break-inside: avoid; page-break-before: always; }
.step:first-of-type { page-break-before: avoid; }
.shot { border: 1px solid #DFDFDF; border-radius: 2mm; max-height: 165mm; }
.narrative { margin: 0 0 3mm 0; }
.n { display: inline-block; background: #0B3C5D; color: #FFF; border-radius: 50%;
     width: 7mm; height: 7mm; line-height: 7mm; text-align: center;
     font-size: 10pt; margin-right: 2.5mm; }
.assert { font-size: 9.5pt; color: #2E6B2E; margin: 2mm 0 0 0; }
.assert.miss { color: #B4451E; }
.cols { width: 100%; }
.cols td { vertical-align: top; }
.shotcell { width: 62mm; padding-right: 7mm; }
code { font-family: 'SF Mono', Menlo, monospace; font-size: 9.5pt; }
"""


def embed(path: pathlib.Path) -> str:
    """Base64 the image so the PDF does not depend on the capture directory."""
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode()


def build_html(manifest: dict, manifest_dir: pathlib.Path) -> str:
    steps = manifest["steps"]
    rows = "".join(
        f'<tr><td class="k">{html.escape(k)}</td><td>{html.escape(str(v))}</td></tr>'
        for k, v in manifest.get("environment", {}).items()
    )

    parts = [
        "<!DOCTYPE html><html><head><meta charset='utf-8'>",
        f"<style>{CSS}</style></head><body>",
        f"<h1>{html.escape(manifest['title'])}</h1>",
        f"<p class='sub'>{html.escape(manifest.get('subtitle', ''))}</p>",
        f"<div class='meta'><table>{rows}</table></div>",
    ]

    for i, step in enumerate(steps, start=1):
        shot = step.get("screenshot")
        img = ""
        if shot:
            p = (manifest_dir / shot) if not pathlib.Path(shot).is_absolute() else pathlib.Path(shot)
            if p.exists():
                img = f"<img class='shot' src='{embed(p)}' width='230'>"
            else:
                img = f"<p class='assert miss'>missing screenshot: {html.escape(str(p))}</p>"

        checks = ""
        for c in step.get("observed", []):
            ok = c.get("present", True)
            cls = "assert" if ok else "assert miss"
            mark = "verified on screen" if ok else "NOT FOUND on screen"
            checks += f"<p class='{cls}'>{'&#10003;' if ok else '&#10007;'} {mark}: <code>{html.escape(c['text'])}</code></p>"

        parts.append(
            "<div class='step'>"
            f"<h2><span class='n'>{i}</span>{html.escape(step['title'])}</h2>"
            "<table class='cols'><tr>"
            f"<td class='shotcell'>{img}</td>"
            f"<td><p class='narrative'>{html.escape(step.get('narrative', ''))}</p>{checks}</td>"
            "</tr></table></div>"
        )

    parts.append("</body></html>")
    return "".join(parts)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("manifest", type=pathlib.Path)
    ap.add_argument("-o", "--output", type=pathlib.Path, required=True)
    ap.add_argument("--keep-html", action="store_true")
    args = ap.parse_args()

    manifest = json.loads(args.manifest.read_text())
    manifest.setdefault("environment", {})["Generated"] = datetime.now(timezone.utc).strftime(
        "%Y-%m-%d %H:%M UTC"
    )

    html_path = args.output.with_suffix(".html")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    html_path.write_text(build_html(manifest, args.manifest.parent))

    result = subprocess.run(
        ["wkhtmltopdf", "--quiet", "--enable-local-file-access",
         "--print-media-type", str(html_path), str(args.output)],
        capture_output=True, text=True,
    )
    if result.returncode != 0 or not args.output.exists():
        print(result.stderr.strip() or "wkhtmltopdf failed", file=sys.stderr)
        return 1

    if not args.keep_html:
        html_path.unlink()

    missing = sum(
        1 for s in manifest["steps"] for c in s.get("observed", []) if not c.get("present", True)
    )
    no_shot = sum(
        1 for s in manifest["steps"]
        if not s.get("screenshot")
        or not (args.manifest.parent / s["screenshot"]).exists()
    )
    print(
        f"{args.output}  ({len(manifest['steps'])} steps, "
        f"{missing} unmet expectation(s), {no_shot} missing screenshot(s))"
    )
    # A walkthrough that documents a broken flow, or has no pictures in it,
    # should not exit as though it passed.
    return 1 if (missing or no_shot) else 0


if __name__ == "__main__":
    raise SystemExit(main())
