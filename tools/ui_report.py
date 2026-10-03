#!/usr/bin/env python
"""Merge the UI-test runs into one report with screenshots, for the `ci-screens` branch.

Input: a folder holding the downloaded artifacts of .github/workflows/ui-tests.yml (each
run's `ui/<run id>/<state>.json|png`, `instrument-*.txt`, `crash.txt`, `monkey.txt`).
Output folder:
  README.md   findings by rule (layout audit R1–R7, journey findings J*), test failures,
              crashes — what GitHub shows when the branch is opened
  screens.md  every state on every phone profile, side by side
  shots/      screenshots as JPEG, at most 540 px wide

  python tools/ui_report.py <artifacts dir> <out dir> [--title TEXT]
Needs Pillow (pip install pillow). Prints the summary; exit 0 (the workflow decides).
"""
from __future__ import annotations

import argparse
import collections
import json
import pathlib
import re
import sys

RULES = {
    "R1": "search card and bottom panel overlap",
    "R2": "tappable thing outside the usable screen",
    "R3": "text clipped, squeezed or a time/price cut off",
    "R4": "touch target under 48×48 dp",
    "R5": "Accessibility Test Framework",
    "R6": "RTL order",
    "R7": "route not in the visible map / map squeezed",
    "J1": "first launch without location",
    "J3": "reminder alarm",
    "J4": "Back closes what is open before leaving",
}
PROFILE_ORDER = ["P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8", "P9"]


def run_key(run: str) -> tuple:
    head = run.split("-")[0]
    return (PROFILE_ORDER.index(head) if head in PROFILE_ORDER else 99, run)


def parse_instrument(text: str) -> tuple[int, list[tuple[str, str]]]:
    """(tests passed, [(test, first lines of the stack)]) from `am instrument -r` output."""
    passed, failures, cur = 0, [], {}
    for block in re.split(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", text):
        if re.fullmatch(r"-?\d+", block.strip() or "x"):
            code = int(block)
            name = f"{cur.get('class', '?').split('.')[-1]}.{cur.get('test', '?')}"
            if code == 0:
                passed += 1
            elif code in (-1, -2):
                failures.append((name, "\n".join(cur.get("stack", "").strip().splitlines()[:6])))
            cur = {}
            continue
        for m in re.finditer(r"INSTRUMENTATION_STATUS: (\w+)=(.*?)(?=\nINSTRUMENTATION_|\Z)", block, re.S):
            cur[m.group(1)] = m.group(2).strip()
    if "INSTRUMENTATION_FAILED" in text or "Process crashed" in text:
        failures.append(("instrumentation", "\n".join(l for l in text.splitlines() if "crash" in l.lower() or "FAILED" in l)[:600]))
    return passed, failures


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("artifacts")
    ap.add_argument("out")
    ap.add_argument("--title", default="UI test report")
    a = ap.parse_args()
    src, out = pathlib.Path(a.artifacts), pathlib.Path(a.out)
    (out / "shots").mkdir(parents=True, exist_ok=True)

    try:
        from PIL import Image
    except ImportError:
        Image = None

    states: dict[str, dict[str, str]] = collections.defaultdict(dict)  # state -> run -> jpg path
    found: dict[tuple, list[str]] = collections.defaultdict(list)       # (rule, state, where, detail) -> runs
    per_rule_run: dict[str, collections.Counter] = collections.defaultdict(collections.Counter)
    runs: set[str] = set()

    for js in sorted(src.rglob("ui/*/*.json")):
        run = js.parent.name
        runs.add(run)
        data = json.loads(js.read_text(encoding="utf-8"))
        state = data.get("state", js.stem)
        for v in data.get("violations", []):
            detail = re.sub(r"\d+(\.\d+)?", "#", v["detail"]) if v["rule"] in ("R2", "R7") else v["detail"]
            found[(v["rule"], state, v["where"], detail)].append(run)
            per_rule_run[v["rule"]][run] += 1
    for png in sorted(src.rglob("ui/*/*.png")):
        run, state = png.parent.name, png.stem
        runs.add(run)
        dest = out / "shots" / run / f"{state}.jpg"
        dest.parent.mkdir(parents=True, exist_ok=True)
        if Image:
            im = Image.open(png).convert("RGB")
            if im.width > 540:
                im = im.resize((540, round(im.height * 540 / im.width)))
            im.save(dest, "JPEG", quality=80, optimize=True)
        states[state][run] = f"shots/{run}/{state}.jpg"

    passed, failures = 0, []
    for f in sorted(src.rglob("instrument-*.txt")):
        p, fl = parse_instrument(f.read_text(encoding="utf-8", errors="replace"))
        passed += p
        failures += [(f"{f.parent.name}/{f.stem.removeprefix('instrument-')}", n, s) for n, s in fl]
    crashes = []
    for f in sorted(src.rglob("crash.txt")):
        t = f.read_text(encoding="utf-8", errors="replace")
        if "il.transit.planner" in t:
            crashes.append((f.parent.name, "\n".join(t.splitlines()[:30])))
    monkey = [f for f in src.rglob("monkey.txt")]
    monkey_line = ""
    for f in monkey:
        t = f.read_text(encoding="utf-8", errors="replace")
        bad = "// CRASH" in t or "// NOT RESPONDING" in t
        monkey_line = f"Monkey (6000 events, seed 4242): **{'crash/ANR found' if bad else 'no crash'}**"

    run_list = sorted(runs, key=run_key)
    by_rule = collections.defaultdict(list)
    for (rule, state, where, detail), rs in found.items():
        by_rule[rule].append((state, where, detail, sorted(set(rs), key=run_key)))

    md = [f"# {a.title}", ""]
    md.append(f"{len(run_list)} runs · {sum(len(v) for v in states.values())} screenshots · "
              f"{passed} tests passed · **{len(failures)} failed** · **{len(crashes)} crash logs** · "
              f"{sum(len(v) for v in by_rule.values())} distinct findings")
    if monkey_line:
        md += ["", monkey_line]
    md += ["", "All screens side by side: [screens.md](screens.md). Rules: `.claude/skills/ui-testing`.", ""]

    if by_rule:
        md += ["## Findings per rule and run", "", "| rule | " + " | ".join(run_list) + " |", "|---|" + "---|" * len(run_list)]
        for rule in sorted(by_rule):
            md.append(f"| {rule} | " + " | ".join(str(per_rule_run[rule][r] or "") for r in run_list) + " |")
        md.append("")
    for rule in sorted(by_rule):
        items = sorted(by_rule[rule], key=lambda x: (-len(x[3]), x[0]))
        md += [f"## {rule} — {RULES.get(rule, '')} ({len(items)})", "", "| state | where | detail | runs (n) |", "|---|---|---|---|"]
        for state, where, detail, rs in items[:60]:
            first = rs[0]
            link = f"[{state}]({states[state][first]})" if first in states.get(state, {}) else state
            shown = ", ".join(rs[:6]) + (" …" if len(rs) > 6 else "")
            md.append(f"| {link} | {where.replace('|', '/')} | {detail.replace('|', '/')} | {shown} ({len(rs)}) |")
        if len(items) > 60:
            md.append(f"| … | | {len(items) - 60} more | |")
        md.append("")
    if failures:
        md += ["## Test failures", ""]
        for run, name, stack in failures:
            md += [f"**{run}** `{name}`", "", "```", stack, "```", ""]
    if crashes:
        md += ["## Crash logs", ""]
        for run, text in crashes:
            md += [f"**{run}**", "", "```", text, "```", ""]
    (out / "README.md").write_text("\n".join(md) + "\n", encoding="utf-8")

    sheet = [f"# Screens — {a.title}", "", "Every state on every run. Click an image for full size.", ""]
    for state in sorted(states):
        sheet += [f"## {state}", ""]
        rs = sorted(states[state], key=run_key)
        for i in range(0, len(rs), 6):
            chunk = rs[i:i + 6]
            sheet.append("| " + " | ".join(chunk) + " |")
            sheet.append("|" + "---|" * len(chunk))
            sheet.append("| " + " | ".join(f'<a href="{states[state][r]}"><img src="{states[state][r]}" width="150"></a>' for r in chunk) + " |")
            sheet.append("")
    (out / "screens.md").write_text("\n".join(sheet) + "\n", encoding="utf-8")

    print("\n".join(md[:8]))
    for rule in sorted(by_rule):
        print(f"  {rule}: {len(by_rule[rule])} distinct findings")
    for run, name, _ in failures:
        print(f"  FAILED {run}: {name}")
    if not Image:
        print("warning: Pillow missing, screenshots not converted", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
