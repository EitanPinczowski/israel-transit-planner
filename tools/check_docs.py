#!/usr/bin/env python
"""Fail if CLAUDE.md's skills table and .claude/skills/ disagree, or CLAUDE.md grows too long.

A skill missing from the table is never loaded; a table row with no skill is a dead
pointer. Both are silent, so CI checks them. CLAUDE.md is loaded into every session,
so its length is capped too.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAX_CLAUDE_MD_LINES = 120


def main() -> int:
    text = (ROOT / "CLAUDE.md").read_text(encoding="utf-8")
    listed = set(re.findall(r"^\|\s*`([a-z0-9-]+)`\s*\|", text, flags=re.M))
    on_disk = {p.parent.name for p in (ROOT / ".claude" / "skills").glob("*/SKILL.md")}
    errors = []
    for name in sorted(on_disk - listed):
        errors.append(f"skill `{name}` exists but CLAUDE.md's table does not name it")
    for name in sorted(listed - on_disk):
        errors.append(f"CLAUDE.md names skill `{name}` but .claude/skills/{name}/SKILL.md does not exist")
    for name in sorted(on_disk):
        head = (ROOT / ".claude" / "skills" / name / "SKILL.md").read_text(encoding="utf-8")
        m = re.match(r"---\nname: ([^\n]+)\ndescription: ([^\n]+)\n---\n", head)
        if not m or m.group(1).strip() != name:
            errors.append(f"skill `{name}`: frontmatter must start with name: {name} and a description")
    lines = text.count("\n") + 1
    if lines > MAX_CLAUDE_MD_LINES:
        errors.append(f"CLAUDE.md is {lines} lines (max {MAX_CLAUDE_MD_LINES}); move detail into a skill")
    for e in errors:
        print("docs:", e)
    if not errors:
        print(f"docs ok: {len(on_disk)} skills, CLAUDE.md {lines} lines")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
