#!/usr/bin/env python
"""Pick up to 4 real phones from Firebase Test Lab's catalog, as a GitHub Actions matrix.

Input: `gcloud firebase test android models list --format=json` (a file). Output (stdout):
a JSON list of {"model", "version", "label"} — a Samsung Galaxy A, a Xiaomi/Redmi/POCO,
a Pixel, and the oldest Android available (≤ API 27 if any), all physical. 4 runs a day
fits the free Spark quota of 5 physical runs; re-picked each time because the catalog changes.

  python tools/testlab_pick.py models.json [--max 4]
"""
from __future__ import annotations

import argparse
import json
import re
import sys


def versions(m: dict) -> list[int]:
    return sorted(int(v) for v in m.get("supportedVersionIds", []) if str(v).isdigit())


def label(m: dict, v: int) -> str:
    name = re.sub(r"[^a-z0-9]+", "-", f"{m.get('brand', '')} {m.get('name', m['id'])}".lower()).strip("-")
    return f"{name[:28]}-api{v}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("models")
    ap.add_argument("--max", type=int, default=4)
    a = ap.parse_args()
    models = [m for m in json.load(open(a.models, encoding="utf-8"))
              if m.get("form") == "PHYSICAL" and versions(m) and "PHONE" in str(m.get("formFactor", "PHONE")).upper()]

    def newest(pred):
        c = [m for m in models if pred(m)]
        return max(c, key=lambda m: versions(m)[-1], default=None)

    picks = []
    for pred in (
        lambda m: m.get("brand", "").lower() == "samsung" and re.search(r"galaxy a|sm-a", (m.get("name", "") + m["id"]).lower()),
        lambda m: m.get("brand", "").lower() in ("xiaomi", "redmi", "poco"),
        lambda m: m.get("brand", "").lower() == "google",
    ):
        m = newest(pred)
        if m:
            picks.append((m, versions(m)[-1]))
    old = min(models, key=lambda m: versions(m)[0], default=None)
    if old:
        picks.append((old, versions(old)[0]))

    out, seen = [], set()
    for m, v in picks:
        if (m["id"], v) in seen:
            continue
        seen.add((m["id"], v))
        out.append({"model": m["id"], "version": str(v), "label": label(m, v)})
    json.dump(out[: a.max], sys.stdout)
    print()
    return 0 if out else 1


if __name__ == "__main__":
    sys.exit(main())
