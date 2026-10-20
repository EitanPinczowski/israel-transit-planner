#!/usr/bin/env python
"""Per-class mutation score from PIT's XML report, and the gate on it (Phase 11 T5, owner decision 20).

    python tools/mutation_score.py changed BASE              comma list of PIT class patterns for the core/src/main files changed since BASE
    python tools/mutation_score.py write XML                 first run: write core/mutation-baseline.json from the report
    python tools/mutation_score.py raise XML                 weekly run: raise the baseline (a class only goes up; new classes are added)
    python tools/mutation_score.py gate XML [--only PATTERNS] fail when a class scores more than 2 points under its baseline

Score = detected mutations / all mutations of the class (inner classes count with their outer class),
in percent. The baseline only ratchets: `raise` never lowers a class, and a PR that has to lower one
says why in its body and edits the file by hand, for the owner to accept. Stdlib only.
"""
from __future__ import annotations

import json
import pathlib
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
BASELINE = ROOT / "core/mutation-baseline.json"
MAIN = "core/src/main/kotlin/il/transit/core/"
PACKAGES = ("present", "fare", "features", "remind", "ride", "calendar")
TOLERANCE = 2.0  # points a class may score under its baseline


def scores(xml_path: str) -> dict[str, dict]:
    out: dict[str, dict] = {}
    for m in ET.parse(xml_path).getroot().iter("mutation"):
        cls = (m.findtext("mutatedClass") or "").split("$")[0]
        e = out.setdefault(cls, {"mutations": 0, "detected": 0})
        e["mutations"] += 1
        e["detected"] += m.get("detected") == "true"
    for e in out.values():
        e["score"] = round(100.0 * e["detected"] / e["mutations"], 1)
    return dict(sorted(out.items()))


def load_baseline() -> dict[str, dict]:
    return json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.exists() else {}


def save(data: dict[str, dict]) -> None:
    BASELINE.write_text(json.dumps(data, indent=1, sort_keys=True, ensure_ascii=False) + "\n", encoding="utf-8")


def changed(base: str) -> list[str]:
    files = subprocess.run(["git", "diff", "--name-only", f"{base}...HEAD", "--", MAIN], cwd=ROOT, capture_output=True, text=True, check=True).stdout.split()
    patterns = []
    for f in files:
        rel = f[len(MAIN):]
        parts = rel.split("/")
        if len(parts) != 2 or not rel.endswith(".kt") or rel.endswith("Data.kt"):
            continue
        pkg, name = parts[0], parts[1][:-3]
        if pkg in PACKAGES or (pkg == "plan" and name == "TripPages"):
            patterns.append(f"il.transit.core.{pkg}.{name}*")
    return sorted(set(patterns))


def matches(cls: str, patterns: list[str]) -> bool:
    return any(cls.startswith(p.rstrip("*")) for p in patterns)


def summary(rows: list[tuple[str, float, float | None, int]]) -> str:
    lines = ["| class | score | baseline | mutations |", "|---|---|---|---|"]
    for cls, now, was, n in rows:
        lines.append(f"| `{cls.removeprefix('il.transit.core.')}` | {now}% | {'—' if was is None else f'{was}%'} | {n} |")
    return "\n".join(lines)


def main() -> int:
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 2
    cmd = args[0]
    if cmd == "changed":
        print(",".join(changed(args[1])))
        return 0
    got = scores(args[1])
    if cmd == "write":
        save({c: {"score": e["score"], "mutations": e["mutations"]} for c, e in got.items()})
        print(f"baseline written for {len(got)} classes")
        return 0
    base = load_baseline()
    if cmd == "raise":
        up = 0
        for c, e in got.items():
            old = base.get(c)
            if old is None or e["score"] > old["score"]:
                base[c] = {"score": e["score"], "mutations": e["mutations"]}
                up += 1
        save(base)
        print(f"{up} classes raised or added")
        return 0
    if cmd == "gate":
        only = args[args.index("--only") + 1].split(",") if "--only" in args else None
        rows, bad = [], []
        for c, e in got.items():
            if only is not None and not matches(c, only):
                continue
            was = base.get(c, {}).get("score")
            rows.append((c, e["score"], was, e["mutations"]))
            if was is not None and e["score"] < was - TOLERANCE:
                bad.append(f"{c}: {e['score']}% is more than {TOLERANCE:g} points under its baseline {was}%")
        print(summary(rows) if rows else "no mutated class in the changed files")
        if not base:
            print("\nNo baseline yet (core/mutation-baseline.json): nothing to gate against.")
        if bad:
            print("\n**Mutation gate failed:**\n" + "\n".join(f"- {b}" for b in bad))
            print("\nKill the surviving mutants (core/build/reports/pitest/index.html), or, if the score has to drop, say why in the PR body and edit the baseline by hand.")
            return 1
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())
