#!/usr/bin/env python
"""PreToolUse guard for Bash commands. Exit 2 blocks the call; stderr says why.

Blocks:
  - staging secrets: *.jks / *.keystore, keystore.properties, local.properties, .env
  - piping a test run (`gradlew ... test | tail`): the pipe returns tail's exit code,
    so a failing suite reads as green
  - force-pushing main

Escape hatch, typed on purpose: prefix the command with TRANSIT_SKIP_GUARD=1.
"""
from __future__ import annotations

import json
import re
import sys

BLOCK, ALLOW = 2, 0

SECRET = re.compile(r"(\.jks\b|\.keystore\b|keystore\.properties|local\.properties|(^|[\s/])\.env\b)")

RULES: list[tuple[str, re.Pattern[str], str]] = [
    (
        "piped test run",
        re.compile(r"gradlew[^|;&]*\btest\b[^|;&]*\|(?!\|)"),
        "Don't pipe the test run: the pipe hides Gradle's exit code. Run `./gradlew -p core test -q` "
        "(only failures print) and summarise core/build/test-results if needed.",
    ),
    (
        "force-push main",
        re.compile(r"git\s+push\b.*(--force\b|-f\b|--force-with-lease\b).*\bmain\b|git\s+push\b.*\bmain\b.*(--force\b|-f\b)"),
        "Force-pushing main rewrites shared history. Push a branch instead.",
    ),
]


def check(command: str) -> str | None:
    if "TRANSIT_SKIP_GUARD=1" in command:
        return None
    for segment in re.split(r"&&|;|\|\|", command):
        if re.search(r"\bgit\s+add\b", segment) and SECRET.search(segment):
            return "Refusing to stage a secret (keystore, keystore.properties, local.properties or .env)."
    for label, pattern, why in RULES:
        if pattern.search(command):
            return f"[{label}] {why}"
    return None


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return ALLOW
    command = (payload.get("tool_input") or {}).get("command") or ""
    reason = check(command)
    if reason:
        print(f"guard.py blocked this command. {reason}", file=sys.stderr)
        return BLOCK
    return ALLOW


if __name__ == "__main__":
    sys.exit(main())
