#!/usr/bin/env python3
"""APK-size gate (Phase 11 T2, owner decision 19).

Compares an APK with `apk-size.json` (bytes, the commit it was measured on, and the compressed
size per folder). Fails at more than 2 % or more than 1 MB over the stored size, and prints the
folders that grew most. The numbers are the zip's compressed entry sizes (what the download costs),
so a re-signed copy of the same APK measures the same.

  python3 tools/apk_size.py APK                  # check (CI)
  python3 tools/apk_size.py APK --write [--commit SHA]   # store the measurement (honest update)

A PR that grows the APK on purpose runs --write in the same PR and says why in its body.
The gate only ratchets: shrinking is fine, but store the smaller value too so the room does not pile up.
"""
import argparse, json, os, sys, zipfile
from pathlib import Path

STORE = Path(__file__).resolve().parent.parent / "apk-size.json"
PCT, ABS = 2.0, 1_000_000
FOLDERS = ("lib/", "res/", "assets/", "META-INF/", "kotlin/")


def folder(name):
    if name.startswith("classes") and name.endswith(".dex"):
        return "classes*.dex"
    for f in FOLDERS:
        if name.startswith(f):
            return f
    return "other"


def measure(apk):
    sizes = {}
    total = 0
    with zipfile.ZipFile(apk) as z:
        for i in z.infolist():
            total += i.compress_size
            k = folder(i.filename)
            sizes[k] = sizes.get(k, 0) + i.compress_size
    return {"bytes": os.path.getsize(apk), "entries_bytes": total, "folders": dict(sorted(sizes.items()))}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("apk")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--commit", default=os.environ.get("GITHUB_SHA", "unknown"))
    a = ap.parse_args()
    m = measure(a.apk)
    if a.write:
        m["commit"] = a.commit
        STORE.write_text(json.dumps(m, indent=1) + "\n")
        print(f"stored {m['bytes']} bytes ({m['bytes'] / 1e6:.2f} MB) for {a.commit}")
        return 0
    old = json.loads(STORE.read_text())
    grew = m["bytes"] - old["bytes"]
    pct = 100.0 * grew / old["bytes"]
    lines = [f"APK {m['bytes']:,} bytes ({m['bytes'] / 1e6:.2f} MB); stored {old['bytes']:,} (commit {old['commit'][:7]}); "
             f"change {grew:+,} bytes ({pct:+.2f} %). Limit: +{PCT:.0f} % and +{ABS / 1e6:.0f} MB."]
    diffs = sorted(((m["folders"].get(k, 0) - old["folders"].get(k, 0), k) for k in set(m["folders"]) | set(old["folders"])), reverse=True)
    lines.append("| folder | now | stored | change |\n|---|---|---|---|")
    for d, k in diffs:
        lines.append(f"| {k} | {m['folders'].get(k, 0):,} | {old['folders'].get(k, 0):,} | {d:+,} |")
    out = "\n".join(lines)
    print(out)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        Path(summary).open("a").write("### APK size\n" + out + "\n")
    if grew > ABS or pct > PCT:
        print(f"::error::APK grew by {grew:+,} bytes ({pct:+.2f} %), over the limit. If this is intended, run "
              "`python3 tools/apk_size.py <apk> --write` in this PR and say why in the PR body.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
