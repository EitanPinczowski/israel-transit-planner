#!/usr/bin/env python3
"""Line coverage per package, from Kover's XML report, as a markdown table (Phase 11 T1; T3 for the app).

    python3 tools/kover_summary.py [core/build/reports/kover/report.xml [title [package-prefix-to-strip]]]

CI appends the output to the job summary ($GITHUB_STEP_SUMMARY); locally it prints. Exit 0 always:
the gate is `koverVerify`, this is only the picture.
"""
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1] if len(sys.argv) > 1 else "core/build/reports/kover/report.xml"
title = sys.argv[2] if len(sys.argv) > 2 else "core line coverage (Kover)"
prefix = sys.argv[3] if len(sys.argv) > 3 else "il/transit/core/"
root = ET.parse(path).getroot()


def line(counters):
    for c in counters:
        if c.get("type") == "LINE":
            return int(c.get("missed")), int(c.get("covered"))
    return 0, 0


rows = []
for p in root.findall("package"):
    m, c = line(p.findall("counter"))
    rows.append((p.get("name").replace(prefix, "").replace(prefix.rstrip("/"), "(root)"), m, c))
tm, tc = line(root.findall("counter"))
out = [f"### {title}", "", "| package | lines | covered | missed | % |", "|---|--:|--:|--:|--:|"]
for name, m, c in sorted(rows):
    out.append(f"| {name} | {m + c} | {c} | {m} | {100 * c / max(m + c, 1):.1f} |")
out.append(f"| **total** | **{tm + tc}** | **{tc}** | **{tm}** | **{100 * tc / max(tm + tc, 1):.2f}** |")
print("\n".join(out))
