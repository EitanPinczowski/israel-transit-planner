#!/usr/bin/env python
"""SessionStart: print ~5 lines of LIVE state, so CLAUDE.md never carries status.

Everything here is measured when the session opens. Each probe is short and fails soft:
a line saying "unknown" is better than a slow or crashing session start.
"""
from __future__ import annotations

import glob
import os
import re
import subprocess
import urllib.request
import xml.etree.ElementTree as ET

ROOT = os.environ.get("CLAUDE_PROJECT_DIR", os.getcwd())


def git(*args: str) -> str:
    try:
        return subprocess.run(["git", "-C", ROOT, *args], capture_output=True, text=True, timeout=5).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return ""


def transitous() -> str:
    req = urllib.request.Request(
        "https://api.transitous.org/api/v1/health",
        headers={"User-Agent": "IsraelTransitPlanner-session-check (+https://github.com/EitanPinczowski/israel-transit-planner)"},
    )
    try:
        with urllib.request.urlopen(req, timeout=4) as r:
            return f"reachable (HTTP {r.status})"
    except Exception as e:  # noqa: BLE001 — any failure is just "not reachable from here"
        return f"NOT reachable from this machine ({type(e).__name__}) — tests use fixtures; live checks need network"


def last_tests() -> str:
    files = glob.glob(os.path.join(ROOT, "core/build/test-results/test/*.xml"))
    if not files:
        return "no local run yet (./gradlew -p core test -q)"
    total = failed = 0
    for f in files:
        r = ET.parse(f).getroot()
        total += int(r.get("tests", 0))
        failed += int(r.get("failures", 0)) + int(r.get("errors", 0))
    return f"{total - failed}/{total} passing in the last local run"


def phase() -> str:
    try:
        text = open(os.path.join(ROOT, "ROADMAP.md"), encoding="utf-8").read()
    except OSError:
        return "unknown (no ROADMAP.md)"
    # "- [ ] ~~x~~" is a dropped item, and "- [ ] Owner: …" waits on the owner, not an agent:
    # neither should pin the report to an old phase.
    owner = 0
    for block in re.split(r"\n(?=## )", text):
        open_items = [l.strip() for l in block.splitlines()
                      if l.strip().startswith("- [ ]") and not l.strip().startswith("- [ ] ~~")]
        mine = [l for l in open_items if not l.startswith("- [ ] Owner:")]
        owner += len(open_items) - len(mine)
        if mine:
            current = block.splitlines()[0].lstrip("# ").strip()
            return f"{current} — {len(mine)} open item(s); {owner} owner check(s) earlier"
    return f"all agent items ticked; {owner} owner check(s) open"


def main() -> None:
    branch = git("rev-parse", "--abbrev-ref", "HEAD") or "?"
    last = git("log", "-1", "--format=%cs %s") or "no commits"
    dirty = git("status", "--porcelain")
    print("israel-transit-planner — live state at session start:")
    print(f"  git: {branch} · last commit {last}{' · UNCOMMITTED changes' if dirty else ''}")
    print(f"  phase: {phase()}")
    print(f"  core tests: {last_tests()}")
    print(f"  transitous: {transitous()}")


if __name__ == "__main__":
    main()
