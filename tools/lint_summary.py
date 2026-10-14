#!/usr/bin/env python3
"""Android Lint counts for the job summary (Phase 11 T2): per severity and per issue id.

  python3 tools/lint_summary.py android/app/build/reports/lint-results-debug.xml
The report lists only what the baseline does not hide, so on a green run it is (nearly) empty;
`--baseline FILE` also counts what the baseline freezes.
"""
import sys
import xml.etree.ElementTree as ET
from collections import Counter

args = [a for a in sys.argv[1:] if not a.startswith("--")]
path = args[0]
base = sys.argv[sys.argv.index("--baseline") + 1] if "--baseline" in sys.argv else None
for title, f in (("new (not in the baseline)", path), ("frozen in the baseline", base)):
    if not f:
        continue
    try:
        root = ET.parse(f).getroot()
    except (OSError, ET.ParseError):
        print(f"### Lint {title}: no report")
        continue
    sev = Counter(i.get("severity", "?") for i in root.iter("issue"))
    ids = Counter(i.get("id") for i in root.iter("issue"))
    print(f"### Lint {title}: {sum(sev.values())} ({', '.join(f'{k} {v}' for k, v in sorted(sev.items())) or 'none'})")
    for k, v in ids.most_common(15):
        print(f"- {k}: {v}")
