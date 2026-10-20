#!/usr/bin/env python
"""Weekly contract check: do the services still answer the way the app reads them? (Phase 11 T5)

    python tools/contract_check.py                  live: <= 5 Transitous + 1 Open-Meteo + 1 Photon
    python tools/contract_check.py --update-shapes  regenerate the shapes from the fixtures (never by hand)
    python tools/contract_check.py --check-shapes   offline: the committed shapes still match the fixtures (CI)

A *shape* is every JSON path the app reads (a key that is a `val` of Models.kt, OpenMeteo.kt or
Photon.kt) with the JSON types it had in the recordings and whether it was in every object. The live
answer drifts when a path that was always there is missing, or has a type the recordings never had.
Also reported: a shrunken timetable horizon, any `realTime: true` (MOT has none on Transitous today),
and the two Leg fields Phase 11 E3 reads (`wheelchairAccessible`, `tripShortName`). An empty
`tripShortName` on a train is expected (the train number is in `headsign`), not drift.
Stdlib only. Exit 1 on drift, 2 on a transport error; the report goes to stdout and `--out`.
"""
from __future__ import annotations

import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

ROOT = pathlib.Path(__file__).resolve().parent.parent
CORE = ROOT / "core/src"
FIXTURES = CORE / "test/resources/fixtures"
SHAPES = CORE / "test/resources/contract"
KOTLIN = CORE / "main/kotlin/il/transit/core"
UA = "IsraelTransitPlanner/0.1 (+https://github.com/EitanPinczowski/israel-transit-planner)"
IL = ZoneInfo("Asia/Jerusalem")
TRANSITOUS, OPEN_METEO, PHOTON = "https://api.transitous.org", "https://api.open-meteo.com", "https://photon.komoot.io"
CAPS = {"transitous": 5, "open-meteo": 1, "photon": 1}
STREET = {"WALK", "BIKE", "CAR", "CAR_DROPOFF", "CAR_PARKING", "CAR_PICKUP", "RENTAL", "FLEX", "ODM"}
ACCESSIBLE = {"ACCESSIBLE", "NOT_ACCESSIBLE", "UNKNOWN"}

# endpoint -> (the Kotlin files naming what the app reads, the fixtures recorded from it)
ENDPOINTS: dict[str, tuple[list[str], list[str]]] = {
    "plan": (["api/Models.kt"], ["plan_*.json"]),
    "trip": (["api/Models.kt"], ["trip_*.json"]),
    "stoptimes": (["api/Models.kt"], ["stoptimes_*.json"]),
    "geocode": (["api/Models.kt"], ["geocode_*.json"]),
    "reverse-geocode": (["api/Models.kt"], ["reverse_geocode_*.json"]),
    "open-meteo": (["weather/OpenMeteo.kt"], ["openmeteo_dry_beersheva.json", "openmeteo_rain_telaviv.json"]),
    "photon": (["search/Photon.kt"], ["photon_rager.json", "photon_rager_en.json", "photon_tabenkin_raanana.json"]),
}


# ---- shapes -------------------------------------------------------------------------------------------

def read_names(files: list[str]) -> set[str]:
    names: set[str] = set()
    for f in files:
        text = (KOTLIN / f).read_text(encoding="utf-8")
        names |= set(re.findall(r"\bval\s+(\w+)", text))
        names |= set(re.findall(r'@SerialName\("(\w+)"\)', text))
        if f.startswith(("weather", "search")):
            names |= set(re.findall(r'"(\w+)"', text))
    return names


def kind(v) -> str:
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "boolean"
    if isinstance(v, (int, float)):
        return "number"
    if isinstance(v, str):
        return "string"
    return "array" if isinstance(v, list) else "object"


def walk(doc, names: set[str]) -> dict[str, dict]:
    """path -> {"types": set, "count": n}; only paths whose every key is one the app reads."""
    out: dict[str, dict] = {}

    def add(path: str, v) -> None:
        e = out.setdefault(path, {"types": set(), "count": 0})
        e["types"].add(kind(v))
        e["count"] += 1

    def rec(v, path: str) -> None:
        add(path, v)
        if isinstance(v, dict):
            for k, child in v.items():
                if k in names:
                    rec(child, f"{path}.{k}")
        elif isinstance(v, list):
            for child in v:
                rec(child, path + "[]")

    rec(doc, "$")
    return out


def parent(path: str) -> str | None:
    if path == "$":
        return None
    return path[:-2] if path.endswith("[]") else path.rsplit(".", 1)[0]


def build_shape(endpoint: str) -> dict:
    files, patterns = ENDPOINTS[endpoint]
    names = read_names(files)
    merged: dict[str, dict] = {}
    for pat in patterns:
        for fx in sorted(FIXTURES.glob(pat)):
            doc = json.loads(fx.read_text(encoding="utf-8"))
            if isinstance(doc, dict) and set(doc) <= {"error", "status", "message", "reason"}:
                continue  # an error-body fixture
            for p, e in walk(doc, names).items():
                m = merged.setdefault(p, {"types": set(), "count": 0})
                m["types"] |= e["types"]
                m["count"] += e["count"]
    counts = {p: m["count"] for p, m in merged.items()}
    shape = {}
    for p, m in sorted(merged.items()):
        par = parent(p)
        always = p == "$" or (not p.endswith("[]") and counts[p] == counts.get(par, -1))
        shape[p] = {"types": sorted(m["types"]), "always": bool(always)}
    return shape


def shape_file(endpoint: str) -> pathlib.Path:
    return SHAPES / f"{endpoint}.shape.json"


def dump(shape: dict) -> str:
    return json.dumps(shape, indent=1, sort_keys=True) + "\n"


def update_shapes() -> int:
    SHAPES.mkdir(parents=True, exist_ok=True)
    for ep in ENDPOINTS:
        shape_file(ep).write_text(dump(build_shape(ep)), encoding="utf-8")
        print(f"{ep}: {len(build_shape(ep))} paths")
    return 0


def check_shapes() -> int:
    stale = [ep for ep in ENDPOINTS if not shape_file(ep).exists() or shape_file(ep).read_text(encoding="utf-8") != dump(build_shape(ep))]
    if stale:
        print(f"contract shapes out of date for: {', '.join(stale)}. Run: python tools/contract_check.py --update-shapes")
        return 1
    print("contract shapes match the fixtures")
    return 0


# ---- live ---------------------------------------------------------------------------------------------

class Wire:
    def __init__(self) -> None:
        self.used = {k: 0 for k in CAPS}
        self.last = 0.0

    def get(self, service: str, base: str, path: str, params: dict[str, str]):
        if self.used[service] >= CAPS[service]:
            raise SystemExit(f"cap reached for {service} ({CAPS[service]})")
        wait = self.last + 1.0 - time.monotonic()
        if wait > 0:
            time.sleep(wait)
        self.used[service] += 1
        self.last = time.monotonic()
        url = base + path + "?" + urllib.parse.urlencode(params)
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=40) as r:
            return json.load(r)


def compare(endpoint: str, live) -> list[str]:
    shape = json.loads(shape_file(endpoint).read_text(encoding="utf-8"))
    got = walk(live, read_names(ENDPOINTS[endpoint][0]))
    bad = []
    for path, want in shape.items():
        have = got.get(path)
        par = parent(path)
        if have is None:
            if want["always"] and par is not None and got.get(par, {}).get("count", 0) > 0 and path.count(".") > 0:
                bad.append(f"{endpoint}: `{path}` is gone (it was in every recorded object)")
            continue
        new = have["types"] - set(want["types"]) - {"null"}
        if new and set(want["types"]) != {"null"}:
            bad.append(f"{endpoint}: `{path}` is now {'/'.join(sorted(new))} (recorded: {'/'.join(want['types'])})")
        if want["always"] and par in got and have["count"] < got[par]["count"] and "null" not in want["types"]:
            bad.append(f"{endpoint}: `{path}` is missing from {got[par]['count'] - have['count']} of {got[par]['count']} objects")
    return bad


def legs(doc) -> list[dict]:
    its = (doc.get("itineraries") or []) + (doc.get("direct") or []) if isinstance(doc, dict) and "legs" not in doc else [doc]
    return [leg for it in its for leg in it.get("legs", [])]


def e3_fields(where: str, doc) -> list[str]:
    transit = [l for l in legs(doc) if l.get("mode") not in STREET]
    bad = []
    for key in ("wheelchairAccessible", "tripShortName"):
        missing = [l for l in transit if key not in l]
        if transit and len(missing) == len(transit):
            bad.append(f"{where}: Leg field `{key}` is missing from every transit leg (Phase 11 E3 reads it)")
        elif any(not isinstance(l[key], str) for l in transit if key in l and l[key] is not None):
            bad.append(f"{where}: Leg field `{key}` is not a string")
    for l in transit:
        w = l.get("wheelchairAccessible")
        if w is not None and w not in ACCESSIBLE:
            bad.append(f"{where}: `wheelchairAccessible` = {w!r}, not one of {sorted(ACCESSIBLE)}")
            break
    return bad


def real_time(where: str, doc) -> list[str]:
    seen = {l.get("agencyName", "?") for l in legs(doc) if l.get("realTime") and l.get("mode") not in STREET}
    return [f"{where}: new `realTime: true` on {a} (MOT has no real-time on Transitous today)" for a in sorted(seen)]


def live(out: pathlib.Path | None) -> int:
    wire = Wire()
    drift: list[str] = []
    notes: list[str] = []
    today = datetime.now(IL).date()
    tuesday = today + timedelta(days=(1 - today.weekday()) % 7 or 7)
    at = datetime.combine(tuesday, datetime.min.time(), IL).replace(hour=8)
    iso = at.astimezone(ZoneInfo("UTC")).strftime("%Y-%m-%dT%H:%M:%SZ")
    try:
        plan = wire.get("transitous", TRANSITOUS, "/api/v6/plan", {
            "fromPlace": "31.2433,34.7976", "toPlace": "32.0839,34.7983", "time": iso, "arriveBy": "false",
            "preTransitModes": "WALK", "postTransitModes": "WALK", "directModes": "WALK", "language": "he",
            "withFares": "true",
        })
        drift += compare("plan", plan)
        drift += e3_fields("plan", plan)
        drift += real_time("plan", plan)
        first = next((l for l in legs(plan) if l.get("mode") not in STREET and l.get("tripId")), None)
        if first is None:
            drift.append("plan: no transit leg with a tripId for Be'er Sheva → Tel Aviv next Tuesday 08:00 (timetable horizon?)")
        else:
            trip = wire.get("transitous", TRANSITOUS, "/api/v6/trip", {"tripId": first["tripId"], "language": "he"})
            drift += compare("trip", trip) + e3_fields("trip", trip) + real_time("trip", trip)
            stop = first["from"].get("stopId")
            # Two weeks on: an empty answer means the timetable no longer reaches that far.
            later = (at + timedelta(days=14)).astimezone(ZoneInfo("UTC")).strftime("%Y-%m-%dT%H:%M:%SZ")
            st = wire.get("transitous", TRANSITOUS, "/api/v6/stoptimes", {"stopId": stop, "time": later, "n": "3", "language": "he"})
            drift += compare("stoptimes", st)
            if not st.get("stopTimes"):
                drift.append(f"stoptimes: no departures from {first['from'].get('name')} on {(at + timedelta(days=14)).date()} — the timetable horizon is under 14 days")
            if any(e.get("realTime") for e in st.get("stopTimes", [])):
                drift.append("stoptimes: new `realTime: true` departures")
        drift += compare("geocode", wire.get("transitous", TRANSITOUS, "/api/v1/geocode", {"text": "דיזנגוף 50 תל אביב", "language": "he", "numResults": "5"}))
        drift += compare("reverse-geocode", wire.get("transitous", TRANSITOUS, "/api/v1/reverse-geocode", {"place": "32.0775,34.7745", "language": "he", "numResults": "3"}))
        drift += compare("open-meteo", wire.get("open-meteo", OPEN_METEO, "/v1/forecast", {
            "latitude": "31.3", "longitude": "34.8", "hourly": "precipitation_probability,precipitation",
            "timeformat": "unixtime", "forecast_hours": "48"}))
        drift += compare("photon", wire.get("photon", PHOTON, "/api", {
            "q": "דיזנגוף 50 תל אביב", "lang": "default", "lat": "31.4", "lon": "35.0",
            "bbox": "34.2,29.4,35.9,33.4", "limit": "5"}))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as e:
        print(f"transport error: {e}", file=sys.stderr)
        return 2
    notes.append("requests: " + ", ".join(f"{k} {v}/{CAPS[k]}" for k, v in wire.used.items()))
    report = [f"# Contract check — {datetime.now(IL):%Y-%m-%d %H:%M} Israel time", ""] + notes + [""]
    report += (["## Drift", ""] + [f"- {d}" for d in drift]) if drift else ["No drift: every recorded shape still holds."]
    text = "\n".join(report) + "\n"
    print(text)
    if out:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text, encoding="utf-8")
    return 1 if drift else 0


def main() -> int:
    args = sys.argv[1:]
    if "--update-shapes" in args:
        return update_shapes()
    if "--check-shapes" in args:
        return check_shapes()
    out = pathlib.Path(args[args.index("--out") + 1]) if "--out" in args else None
    return live(out)


if __name__ == "__main__":
    sys.exit(main())
