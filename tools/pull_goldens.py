#!/usr/bin/env python3
"""Re-record the Paparazzi goldens from CI's pictures, without an Android SDK.

CI's `screenshots` job renders every shot on each push and publishes the PNGs to the
`screenshots` branch, one folder per source branch. This copies that folder into
`android/app/src/test/snapshots/images/` (replacing what is there, so renamed or deleted
shots go too) after checking the pictures were rendered from the commit you have checked out.
Then look at what changed (`git status`), and commit the images with the change that caused
them.

    python3 tools/pull_goldens.py              # the current branch, must match HEAD
    python3 tools/pull_goldens.py --branch main --any-commit
"""
from __future__ import annotations

import argparse
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
GOLDENS = ROOT / "android" / "app" / "src" / "test" / "snapshots" / "images"


def git(*args: str, binary: bool = False):
    out = subprocess.run(["git", "-C", str(ROOT), *args], check=True, capture_output=True)
    return out.stdout if binary else out.stdout.decode().strip()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--branch", help="source branch whose pictures to take (default: the current one)")
    ap.add_argument("--any-commit", action="store_true", help="skip the check that CI rendered HEAD")
    a = ap.parse_args()

    branch = a.branch or git("rev-parse", "--abbrev-ref", "HEAD")
    folder = branch.replace("/", "_")
    git("fetch", "-q", "--depth=1", "origin", "screenshots")
    try:
        info = git("show", f"FETCH_HEAD:{folder}/INFO.txt")
    except subprocess.CalledProcessError:
        print(f"no pictures for {branch} on the screenshots branch yet: push, and wait for the screenshots job")
        return 1
    rendered = info.splitlines()[0].removeprefix("commit ").strip()
    head = git("rev-parse", "HEAD")
    if rendered != head and not a.any_commit:
        print(f"CI rendered {rendered[:7]}, you are on {head[:7]}.\n{info}\n"
              "Push and wait for that run's screenshots job (or pass --any-commit).")
        return 1

    names = [n for n in git("ls-tree", "--name-only", f"FETCH_HEAD:{folder}").splitlines() if n.endswith(".png")]
    if not names:
        print(f"{folder}/ has no pictures")
        return 1
    old = {p.name: p.read_bytes() for p in GOLDENS.glob("*.png")}
    new = {n: git("show", f"FETCH_HEAD:{folder}/{n}", binary=True) for n in names}

    GOLDENS.mkdir(parents=True, exist_ok=True)
    for name in old.keys() - new.keys():
        (GOLDENS / name).unlink()
    for name, data in new.items():
        (GOLDENS / name).write_bytes(data)

    added = sorted(new.keys() - old.keys())
    removed = sorted(old.keys() - new.keys())
    changed = sorted(n for n in new.keys() & old.keys() if new[n] != old[n])
    print(f"goldens from {branch} @ {rendered[:7]}: {len(added)} new, {len(changed)} changed, "
          f"{len(removed)} removed, {len(new)} total")
    for label, items in (("new", added), ("changed", changed), ("removed", removed)):
        for n in items:
            print(f"  {label:8} {n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
