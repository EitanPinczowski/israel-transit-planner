#!/usr/bin/env python
"""Record a real Transitous response as a test fixture.

    python tools/record_fixture.py plan_bgu_telaviv "/api/v6/plan?fromPlace=31.262,34.801&toPlace=32.0839,34.7983&language=he"

    python tools/record_fixture.py --photon photon_tabenkin "/api?q=...&lang=default&bbox=..."

    python tools/record_fixture.py --open-meteo openmeteo_dry "/v1/forecast?latitude=31.3&longitude=34.8&..."

`--photon` asks photon.komoot.io (the backup geocoder) instead of Transitous; `--open-meteo`
asks api.open-meteo.com (rain on the walks, Phase 10 D9). An HTTP error's JSON body is saved
too (an error fixture), and the status is printed.
Saves core/src/test/resources/fixtures/<name>.json (pretty-printed). Sends the project
User-Agent, as the Transitous usage policy requires. One request per run — this is for
capturing fixtures, not for load.
"""
from __future__ import annotations

import json
import pathlib
import sys
import urllib.error
import urllib.request

BASE = "https://api.transitous.org"
PHOTON = "https://photon.komoot.io"
OPEN_METEO = "https://api.open-meteo.com"
UA = "IsraelTransitPlanner/0.1 (+https://github.com/EitanPinczowski/israel-transit-planner)"
OUT = pathlib.Path(__file__).resolve().parent.parent / "core/src/test/resources/fixtures"


def main() -> int:
    args = sys.argv[1:]
    base = BASE
    if args[:1] == ["--photon"]:
        base, args = PHOTON, args[1:]
    elif args[:1] == ["--open-meteo"]:
        base, args = OPEN_METEO, args[1:]
    if len(args) != 2:
        print(__doc__)
        return 2
    name, path = args
    req = urllib.request.Request(base + path, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            data = json.load(r)
    except urllib.error.HTTPError as e:
        data = json.load(e)
        print(f"HTTP {e.code}: saving the error body")
    target = OUT / f"{name}.json"
    target.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"saved {target.relative_to(OUT.parent.parent.parent.parent.parent)} ({target.stat().st_size // 1024} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
