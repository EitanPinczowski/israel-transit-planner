#!/usr/bin/env python
"""Record a real Transitous response as a test fixture.

    python tools/record_fixture.py plan_bgu_telaviv "/api/v6/plan?fromPlace=31.262,34.801&toPlace=32.0839,34.7983&language=he"

Saves core/src/test/resources/fixtures/<name>.json (pretty-printed). Sends the project
User-Agent, as the Transitous usage policy requires. One request per run — this is for
capturing fixtures, not for load.
"""
from __future__ import annotations

import json
import pathlib
import sys
import urllib.request

BASE = "https://api.transitous.org"
UA = "IsraelTransitPlanner/0.1 (+https://github.com/EitanPinczowski/israel-transit-planner)"
OUT = pathlib.Path(__file__).resolve().parent.parent / "core/src/test/resources/fixtures"


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    name, path = sys.argv[1], sys.argv[2]
    req = urllib.request.Request(BASE + path, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        data = json.load(r)
    target = OUT / f"{name}.json"
    target.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"saved {target.relative_to(OUT.parent.parent.parent.parent.parent)} ({target.stat().st_size // 1024} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
