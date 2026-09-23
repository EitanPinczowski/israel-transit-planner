#!/usr/bin/env python
"""PostToolUse (Edit|Write): lint the edited Kotlin file with ktlint, if installed.

Silent when ktlint is absent or the file is not Kotlin — a missing linter must never
block editing. Prints only the violations, so the context gets errors, not noise.
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return 0
    path = (payload.get("tool_input") or {}).get("file_path") or ""
    if not path.endswith((".kt", ".kts")):
        return 0
    ktlint = shutil.which("ktlint")
    if not ktlint:
        return 0
    try:
        out = subprocess.run([ktlint, "--relative", path], capture_output=True, text=True, timeout=25)
    except (OSError, subprocess.SubprocessError):
        return 0
    if out.returncode != 0 and out.stdout.strip():
        lines = out.stdout.strip().splitlines()
        print("ktlint:\n" + "\n".join(lines[:20]) + ("\n…" if len(lines) > 20 else ""), file=sys.stderr)
        return 2  # feeds the violations back to Claude without undoing the edit
    return 0


if __name__ == "__main__":
    sys.exit(main())
