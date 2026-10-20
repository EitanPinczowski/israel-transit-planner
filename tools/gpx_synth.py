#!/usr/bin/env python
"""Synthetic GPS tracks for the RideTracker replay test (Phase 11 T5).

    python tools/gpx_synth.py            writes core/src/test/resources/tracks/<name>.gpx + <name>.json
    python tools/gpx_synth.py --check    fails if the committed tracks differ from what this script makes

Each track rides a recorded leg's polyline (a `trip_*` or `plan_*` fixture, MOTIS precision 6) at its
scheduled speed, one fix every 10 s, then spoils it with seeded noise: urban-canyon jitter, a 60 s tunnel
gap (the Carmel and Ayalon rail), stop-and-go traffic, a 300 m GPS jump, a skipped stop (sparse fixes, no
dwell at the stop before the last), a loop line passing the alighting stop twice. The `.json` next to a
track names the fixture it rides (`fixture`, and `itinerary` for a plan). Real rides recorded with a GPX
logger go in `tracks/real/` with the same pair of files (skill reminders-offline). Stdlib only; a seed
makes a track reproducible.
"""
from __future__ import annotations

import json
import math
import pathlib
import random
import sys
from datetime import datetime, timedelta, timezone

ROOT = pathlib.Path(__file__).resolve().parent.parent
FIXTURES = ROOT / "core/src/test/resources/fixtures"
OUT = ROOT / "core/src/test/resources/tracks"
STREET = {"WALK", "BIKE", "CAR"}
INTERVAL = 10  # seconds between fixes


def decode(encoded: str, precision: int) -> list[tuple[float, float]]:
    pts, lat, lon, i, f = [], 0, 0, 0, 10 ** precision
    while i < len(encoded):
        for axis in (0, 1):
            shift = result = 0
            while True:
                b = ord(encoded[i]) - 63
                i += 1
                result |= (b & 0x1F) << shift
                shift += 5
                if b < 0x20:
                    break
            d = ~(result >> 1) if result & 1 else result >> 1
            if axis == 0:
                lat += d
            else:
                lon += d
        pts.append((lat / f, lon / f))
    return pts


def metres(a: tuple[float, float], b: tuple[float, float]) -> float:
    k = math.cos(math.radians((a[0] + b[0]) / 2))
    return math.hypot((b[0] - a[0]) * 111_320, (b[1] - a[1]) * 111_320 * k)


def when(s: str) -> datetime:
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def legs_of(fixture: str, itinerary: int | None) -> list[dict]:
    doc = json.loads((FIXTURES / f"{fixture}.json").read_text(encoding="utf-8"))
    if itinerary is not None:
        doc = doc["itineraries"][itinerary]
    return doc["legs"]


class Path:
    """A leg's polyline with its length in metres, sampled by fraction 0..1."""

    def __init__(self, leg: dict) -> None:
        g = leg["legGeometry"]
        self.pts = decode(g["points"], g.get("precision", 6))
        self.cum = [0.0]
        for a, b in zip(self.pts, self.pts[1:]):
            self.cum.append(self.cum[-1] + metres(a, b))
        self.length = self.cum[-1] or 1.0

    def at(self, frac: float) -> tuple[float, float]:
        target = min(max(frac, 0.0), 1.0) * self.length
        lo, hi = 0, len(self.cum) - 1
        while lo < hi - 1:
            mid = (lo + hi) // 2
            if self.cum[mid] <= target:
                lo = mid
            else:
                hi = mid
        seg = (self.cum[hi] - self.cum[lo]) or 1.0
        t = (target - self.cum[lo]) / seg
        a, b = self.pts[lo], self.pts[hi]
        return a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t


def offset(p: tuple[float, float], north: float, east: float) -> tuple[float, float]:
    return p[0] + north / 111_320, p[1] + east / (111_320 * math.cos(math.radians(p[0])))


def fixes(leg: dict, rnd: random.Random, *, sigma: float = 5.0, outliers: float = 0.0, interval: int = INTERVAL,
          dwell: bool = False, gaps: list[tuple[float, float]] = (), jumps: list[tuple[float, float]] = (),
          sparse_from: float | None = None, loop: bool = False) -> list[tuple[datetime, float, float]]:
    """One leg ridden start to end (+ 20 s). `gaps`: (from, to) fractions of the ride with no fixes;
    `jumps`: (fraction, metres) one fix thrown that far north; `sparse_from`: fixes 30 s apart after it."""
    path = Path(leg)
    t0, t1 = when(leg["startTime"]), when(leg["endTime"])
    total = (t1 - t0).total_seconds()
    # progress(t): constant speed, or moving/dwelling in turns (stop-and-go) rescaled to fill the leg
    if dwell:
        phases, clock = [], 0.0
        while clock < total:
            move, wait = rnd.uniform(40, 120), rnd.uniform(30, 90)
            phases.append((move, wait))
            clock += move + wait
        moving = sum(m for m, _ in phases)

        def progress(s: float) -> float:
            scale, done, t = total / clock, 0.0, s
            t /= scale
            for move, wait in phases:
                if t <= move:
                    return min((done + t) / moving, 1.0)
                done += move
                t -= move
                if t <= wait:
                    return min(done / moving, 1.0)
                t -= wait
            return 1.0
    else:
        def progress(s: float) -> float:
            return s / total

    def route(frac: float) -> tuple[float, float]:
        if not loop:
            return path.at(frac)
        # a straight detour from the 50% point to the alighting stop and back: the vehicle passes the stop at 65% and again at the end
        a, stop = path.at(0.5), path.at(1.0)
        if frac < 0.5:
            return path.at(frac)
        if frac < 0.65:
            u = (frac - 0.5) / 0.15
            return a[0] + (stop[0] - a[0]) * u, a[1] + (stop[1] - a[1]) * u
        if frac < 0.8:
            u = (frac - 0.65) / 0.15
            return stop[0] + (a[0] - stop[0]) * u, stop[1] + (a[1] - stop[1]) * u
        return path.at(0.5 + (frac - 0.8) / 0.2 * 0.5)

    out, s = [], 0.0
    while s <= total + 20:
        frac = progress(min(s, total))
        lat, lon = route(frac)
        sd = sigma * (rnd.uniform(2.0, 3.5) if outliers and rnd.random() < outliers else 1.0)
        lat, lon = offset((lat, lon), rnd.gauss(0, sd), rnd.gauss(0, sd))
        for at, metres_north in jumps:
            if abs(frac - at) <= interval / total / 2:
                lat, lon = offset((lat, lon), metres_north, 0)
        if not any(a <= frac <= b for a, b in gaps):
            out.append((t0 + timedelta(seconds=s), lat, lon))
        s += 30 if sparse_from is not None and frac >= sparse_from else interval
    return out


def gpx(name: str, track: list[tuple[datetime, float, float]]) -> str:
    rows = "\n".join(
        f'   <trkpt lat="{lat:.6f}" lon="{lon:.6f}"><time>{t.astimezone(timezone.utc):%Y-%m-%dT%H:%M:%SZ}</time></trkpt>'
        for t, lat, lon in track
    )
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="tools/gpx_synth.py" xmlns="http://www.topografix.com/GPX/1/1">\n'
            f' <trk><name>{name}</name><trkseg>\n{rows}\n </trkseg></trk>\n</gpx>\n')


# name -> (fixture, itinerary, seed, what to do to each transit leg's ride)
SCENARIOS: dict[str, tuple[str, int | None, int, dict]] = {
    "train_clean": ("trip_train_bs_telaviv", None, 1, dict(sigma=5)),
    "train_tunnel_mid": ("trip_train_bs_telaviv", None, 2, dict(sigma=8, gaps=[(0.45, 0.45 + 60 / 5460)])),
    "train_tunnel_before_stop": ("trip_train_bs_telaviv", None, 3, dict(sigma=8, gaps=[(0.97 - 60 / 5460, 0.97)])),
    "bus_urban_canyon": ("trip_bus_470", None, 4, dict(sigma=25, outliers=0.08)),
    "bus_stop_and_go": ("trip_bus_470", None, 5, dict(sigma=10, dwell=True)),
    "bus_gps_jump_300m": ("trip_bus_470", None, 6, dict(sigma=10, jumps=[(0.2, 300), (0.5, 300)])),
    "bus_skipped_stop": ("trip_bus_470", None, 7, dict(sigma=10, sparse_from=0.75)),
    "bus_loop_line": ("trip_bus_470", None, 8, dict(sigma=10, loop=True)),
    "plan_three_buses": ("plan_bgu_telaviv", 0, 9, dict(sigma=15)),
    "plan_train_walks": ("plan_bgu_telaviv", 1, 10, dict(sigma=12)),
}


def build() -> dict[str, tuple[str, str]]:
    made = {}
    for name, (fixture, itinerary, seed, how) in SCENARIOS.items():
        rnd = random.Random(seed)
        track: list = []
        for leg in legs_of(fixture, itinerary):
            if leg.get("legGeometry"):
                track += fixes(leg, rnd, **(how if leg["mode"] not in STREET else dict(sigma=how.get("sigma", 5))))
        meta = {"fixture": fixture, "itinerary": itinerary, "seed": seed, "kind": "synthetic", "scenario": how_text(how)}
        made[name] = (gpx(name, track), json.dumps(meta, indent=1) + "\n")
    return made


def how_text(how: dict) -> str:
    return ", ".join(f"{k}={v}" for k, v in how.items())


def main() -> int:
    made = build()
    if "--check" in sys.argv:
        stale = [n for n, (g, m) in made.items() if not (OUT / f"{n}.gpx").exists() or (OUT / f"{n}.gpx").read_text() != g or (OUT / f"{n}.json").read_text() != m]
        print("tracks match" if not stale else f"tracks out of date: {', '.join(stale)}")
        return 1 if stale else 0
    OUT.mkdir(parents=True, exist_ok=True)
    for n, (g, m) in made.items():
        (OUT / f"{n}.gpx").write_text(g, encoding="utf-8")
        (OUT / f"{n}.json").write_text(m, encoding="utf-8")
        print(f"{n}: {g.count('<trkpt')} fixes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
