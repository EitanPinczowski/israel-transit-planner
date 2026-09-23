#!/usr/bin/env python
"""Summarise a MOTIS /plan JSON response in a few lines instead of reading megabytes.

    python tools/plan_summary.py response.json      (or pipe JSON on stdin)

One line per itinerary: depart → arrive, duration, transfers, then each leg as
MODE[route] from → to (minutes), with * marking legs that carry real-time data.
"""
from __future__ import annotations

import json
import sys
from datetime import datetime, timedelta, timezone

IL = timezone(timedelta(hours=3))  # display only; close enough for eyeballing


def hhmm(iso: str) -> str:
    return datetime.fromisoformat(iso.replace("Z", "+00:00")).astimezone(IL).strftime("%H:%M")


def leg_str(leg: dict) -> str:
    route = leg.get("routeShortName") or leg.get("displayName") or ""
    rt = "*" if leg.get("realTime") else ""
    mins = round(leg.get("duration", 0) / 60)
    return f"{leg['mode']}{'[' + route + ']' if route else ''}{rt} {leg['from']['name']} → {leg['to']['name']} ({mins}′)"


def main() -> int:
    data = json.load(open(sys.argv[1], encoding="utf-8") if len(sys.argv) > 1 else sys.stdin)
    for kind in ("itineraries", "direct"):
        for i, it in enumerate(data.get(kind, []), 1):
            print(f"{kind[:4]} {i}: {hhmm(it['startTime'])}→{hhmm(it['endTime'])} "
                  f"{round(it['duration'] / 60)}′ transfers={it.get('transfers', 0)}")
            for leg in it.get("legs", []):
                print("    " + leg_str(leg))
    if not data.get("itineraries") and not data.get("direct"):
        print("no itineraries")
    return 0


if __name__ == "__main__":
    sys.exit(main())
