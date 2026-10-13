#!/usr/bin/env python3
"""ktlint gate (Phase 11 T2): fail on style findings that sit on lines a change added or edited.

ktlint itself runs with ignoreFailures (`./gradlew -p core ktlintCheck`, `-p android :app:ktlintCheck`)
and writes checkstyle XML. This reads those reports, asks git which lines changed against the
merge-base with origin/main, and fails on findings on those lines. Untouched code is never
judged, so nobody reformats old files. `tools/ktlint-baseline.json` freezes today's counts
(per module and rule); the totals are printed so a drift is visible.

  python3 tools/ktlint_gate.py [--base REF] [--write-baseline]
"""
import argparse, json, re, subprocess, sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASELINE = ROOT / "tools" / "ktlint-baseline.json"


def findings():
    out = []
    for xml in sorted(ROOT.glob("*/**/build/reports/ktlint/*/*.xml")):
        module = xml.relative_to(ROOT).parts[0]
        for f in ET.parse(xml).getroot().iter("file"):
            path = Path(f.get("name")).resolve()
            try:
                rel = str(path.relative_to(ROOT))
            except ValueError:
                continue
            for e in f.iter("error"):
                out.append((module, rel, int(e.get("line", "0")), e.get("source", "").split(":")[-1], e.get("message", "")))
    return sorted(set(out))


def changed_lines(base):
    mb = subprocess.run(["git", "merge-base", "HEAD", base], cwd=ROOT, capture_output=True, text=True)
    ref = mb.stdout.strip() or base
    diff = subprocess.run(["git", "diff", "-U0", "--no-color", ref, "--", "*.kt", "*.kts"],
                          cwd=ROOT, capture_output=True, text=True, check=True).stdout
    lines, cur = {}, None
    for l in diff.splitlines():
        if l.startswith("+++ "):
            cur = l[6:] if l.startswith("+++ b/") else None
            if cur:
                lines.setdefault(cur, set())
        elif l.startswith("@@") and cur:
            m = re.match(r"@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@", l)
            start, n = int(m.group(1)), int(m.group(2) or "1")
            lines[cur].update(range(start, start + n))
    return lines


def counts(fs):
    c = {}
    for module, _, _, rule, _ in fs:
        c.setdefault(module, {})
        c[module][rule] = c[module].get(rule, 0) + 1
    return c


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="origin/main")
    ap.add_argument("--write-baseline", action="store_true")
    a = ap.parse_args()
    fs = findings()
    if not fs and not list(ROOT.glob("*/**/build/reports/ktlint/*/*.xml")):
        sys.exit("no ktlint reports found: run ktlintCheck first")
    c = counts(fs)
    if a.write_baseline:
        old = json.loads(BASELINE.read_text()) if BASELINE.exists() else {}
        old.update(c)  # a run that only saw one module keeps the others
        BASELINE.write_text(json.dumps(old, indent=1, sort_keys=True) + "\n")
        print(f"baseline written: {sum(sum(r.values()) for r in old.values())} findings")
        return
    changed = changed_lines(a.base)
    bad = [f for f in fs if f[2] in changed.get(f[1], ())]
    total = sum(sum(r.values()) for r in c.values())
    base = json.loads(BASELINE.read_text()) if BASELINE.exists() else {}
    btotal = sum(sum(r.values()) for r in base.values())
    print(f"ktlint: {total} findings in all (baseline {btotal}); {len(bad)} on changed lines")
    for m in sorted(c):
        print(f"  {m}: {sum(c[m].values())} (baseline {sum(base.get(m, {}).values())})")
    for module, rel, line, rule, msg in bad:
        print(f"::error file={rel},line={line}::ktlint {rule}: {msg}")
    if bad:
        print("Fix the lines above (./gradlew -p core ktlintFormat, or by hand); old code is not judged.")
        sys.exit(1)


if __name__ == "__main__":
    main()
