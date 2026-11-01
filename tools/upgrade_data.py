#!/usr/bin/env python3
"""Phase 11 T8, the data half of the upgrade test (tools/ui_upgrade_run.sh).

  upgrade_data.py write   <sample.json> <out dir> <now epoch>   the OLD release's saved-data sample (T1) as the files its app keeps:
                                                                datastore/user.preferences_pb (Preferences DataStore, protobuf), history.json, expected.json
  upgrade_data.py compare <expected.json> <probe log>           what UpgradeProbe (the NEW build) read vs. what was written; exit 1 on any loss

The sample is what that release's own codec wrote (never edited by hand). Two things are added on top, because the sample has neither:
`nightRefresh` is switched on (so the old app enqueues the night refresh) and one leave reminder is stored (so it arms its alarm).
A field the new build added is fine; a value the old build had that is gone or different is a loss.
"""
import json
import re
import sys
from pathlib import Path


def varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def ld(field: int, payload: bytes) -> bytes:  # length-delimited field
    return varint(field << 3 | 2) + varint(len(payload)) + payload


def entry(key: str, value: bytes) -> bytes:  # one map<string, Value> entry of PreferenceMap.preferences (field 1)
    return ld(1, ld(1, key.encode()) + ld(2, value))


def string_value(s: str) -> bytes:  # Value.string = field 5
    return ld(5, s.encode("utf-8"))


def compact(o) -> str:
    return json.dumps(o, ensure_ascii=False, separators=(",", ":"))


def write(sample: Path, out: Path, now: int) -> None:
    data = json.loads(sample.read_text(encoding="utf-8"))
    settings = dict(data["settings"], nightRefresh=True, tourSeen=True)
    reminder = {
        "fromLat": 31.2437, "fromLon": 34.7976, "toLat": 31.2622, "toLon": 34.8013, "settings": settings,
        "tripId": None, "line": "470", "boardStop": "Beer Sheva North", "scheduledBoardingEpoch": now + 4 * 3600,
        "leaveAtEpoch": now + 3 * 3600, "fromName": "Home", "toName": "עבודה",
    }
    prefs = {
        "settings": compact(settings), "places": compact(data["places"]), "trips": compact(data["trips"]),
        "favorite_lines": compact(data["favorites"]), "reminder": compact(reminder),
    }
    (out / "datastore").mkdir(parents=True, exist_ok=True)
    (out / "datastore" / "user.preferences_pb").write_bytes(b"".join(entry(k, string_value(v)) for k, v in prefs.items()))
    (out / "history.json").write_text(compact(data["history"]), encoding="utf-8")
    expected = {"settings": settings, "places": data["places"], "trips": data["trips"], "favorites": data["favorites"],
                "reminder": reminder, "history": data["history"], "version": data["version"]}
    (out / "expected.json").write_text(json.dumps(expected, ensure_ascii=False, indent=1), encoding="utf-8")


def missing(old, new, path="") -> list[str]:
    """Every value of `old` that `new` does not have. Dicts: new keys are fine. Lists: same length, element by element."""
    if isinstance(old, dict):
        if not isinstance(new, dict):
            return [f"{path}: was an object, now {new!r}"]
        return [m for k, v in old.items() for m in missing(v, new.get(k), f"{path}.{k}")]
    if isinstance(old, list):
        if not isinstance(new, list) or len(new) != len(old):
            return [f"{path}: {len(old)} items before, now {new if not isinstance(new, list) else len(new)}"]
        return [m for i, (a, b) in enumerate(zip(old, new)) for m in missing(a, b, f"{path}[{i}]")]
    if isinstance(old, float) and isinstance(new, (int, float)) and abs(old - new) < 1e-9:
        return []
    return [] if old == new else [f"{path}: {old!r} -> {new!r}"]


def compare(expected: Path, log: Path) -> int:
    exp = json.loads(expected.read_text(encoding="utf-8"))
    seen: dict[str, str] = {}
    for line in log.read_text(encoding="utf-8", errors="replace").splitlines():
        m = re.search(r"UpgradeProbe: (\w+)=(.*)$", line)
        if m:
            seen[m.group(1)] = m.group(2).rstrip("\r")
    problems = []
    if "done" not in seen:
        problems.append("the probe never finished (no 'done' line)")
    for key in ("settings", "places", "trips", "favorites", "reminder", "history"):
        if key not in seen:
            problems.append(f"{key}: not read")
            continue
        try:
            new = json.loads(seen[key])
        except ValueError:
            problems.append(f"{key}: unreadable: {seen[key][:80]}")
            continue
        problems += missing(exp[key], new, key)
    if seen.get("nightRefresh") != "true":
        problems.append(f"nightRefresh: not enqueued after the upgrade ({seen.get('nightRefresh')})")
    print("channels after:", seen.get("channels", "?"))
    for p in problems:
        print("::error::upgrade lost data:", p)
    print(f"upgrade compare: {'LOSS' if problems else 'nothing lost'} ({len(problems)} problems, version {exp['version']})")
    return 1 if problems else 0


if __name__ == "__main__":
    if len(sys.argv) == 5 and sys.argv[1] == "write":
        write(Path(sys.argv[2]), Path(sys.argv[3]), int(sys.argv[4]))
    elif len(sys.argv) == 4 and sys.argv[1] == "compare":
        sys.exit(compare(Path(sys.argv[2]), Path(sys.argv[3])))
    else:
        sys.exit(__doc__)
