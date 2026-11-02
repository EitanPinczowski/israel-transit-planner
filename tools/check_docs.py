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


# A request cap in core/src/main: BUDGET, MAX_TRIPS/STOPS/PLANS/PAGES/REQUESTS/CALLS, or *_PER_OPEN/SEARCH/TAP.
REQUEST_CAP = re.compile(r"^\s*(?:private |internal )?const val (BUDGET|MAX_(?:TRIPS|STOPS|PLANS|PAGES|REQUESTS|CALLS)|[A-Z_]+_PER_(?:OPEN|SEARCH|TAP))\b")
OWNER = re.compile(r"^\s*(?:[a-z]+ )*(?:class|object) (\w+)")


def request_caps() -> list[str]:
    """`Owner.NAME` of every request-cap constant in core/src/main (Phase 11 T1); owner = enclosing class/object."""
    found = []
    for path in sorted((ROOT / "core" / "src" / "main").rglob("*.kt")):
        stack: list[tuple[int, str]] = []  # (indent, name) of the classes/objects around the current line
        for line in path.read_text(encoding="utf-8").splitlines():
            indent = len(line) - len(line.lstrip())
            if line.lstrip().startswith("}"):
                stack = [e for e in stack if e[0] < indent]  # the closing brace of a class
            m = OWNER.match(line)
            if m:
                stack = [e for e in stack if e[0] < indent] + [(indent, m.group(1))]
            c = REQUEST_CAP.match(line)
            if c:
                outer = [e for e in stack if e[0] < indent]
                if outer:
                    found.append(f"{outer[-1][1]}.{c.group(1)}")
    return found


def check_budgets() -> list[str]:
    """Every request cap must be a row of BudgetInventoryTest and be named in the transitous-api skill."""
    test = ROOT / "core" / "src" / "test" / "kotlin" / "il" / "transit" / "core" / "BudgetInventoryTest.kt"
    skill = ROOT / ".claude" / "skills" / "transitous-api" / "SKILL.md"
    if not test.exists() or not skill.exists():
        return ["budget inventory: BudgetInventoryTest.kt or the transitous-api skill is missing"]
    test_text, skill_text = test.read_text(encoding="utf-8"), skill.read_text(encoding="utf-8")
    errors = []
    for key in request_caps():
        if f'"{key}"' not in test_text:
            errors.append(f"request cap `{key}` has no row in BudgetInventoryTest (constant column)")
        if f"`{key}`" not in skill_text:
            errors.append(f"request cap `{key}` is not in the transitous-api skill's budget inventory")
    return errors


def check_inventory() -> list[str]:
    """Phase 11 T8: docs/testing/inventory.md names every journey test, and only journeys that exist."""
    inv = ROOT / "docs" / "testing" / "inventory.md"
    if not inv.exists():
        return ["docs/testing/inventory.md is missing"]
    text = inv.read_text(encoding="utf-8")
    src = ROOT / "android" / "app" / "src" / "androidTest" / "java" / "il" / "transit" / "planner" / "uitest"
    existing: set[str] = set()
    for f in sorted(src.glob("*JourneysTest.kt")) + [src / "FirstLaunchTest.kt"]:
        if f.exists():
            existing |= {f"{f.stem}#{m}" for m in re.findall(r"@Test fun ([A-Za-z0-9_]+)", f.read_text(encoding="utf-8"))}
    named = set(re.findall(r"`([A-Za-z0-9]*(?:JourneysTest|FirstLaunchTest)#[A-Za-z0-9_]+)`", text))
    errors = [f"inventory.md names journey `{n}` but no such test exists" for n in sorted(named - existing)]
    errors += [f"journey test `{n}` is not listed in docs/testing/inventory.md" for n in sorted(existing - named)]
    return errors


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
    errors += check_budgets()
    errors += check_inventory()
    lines = text.count("\n") + 1
    if lines > MAX_CLAUDE_MD_LINES:
        errors.append(f"CLAUDE.md is {lines} lines (max {MAX_CLAUDE_MD_LINES}); move detail into a skill")
    for e in errors:
        print("docs:", e)
    if not errors:
        print(f"docs ok: {len(on_disk)} skills, CLAUDE.md {lines} lines")
    return 1 if errors else 0


if __name__ == "__main__":
    # CI runs only this script, so it also runs the string-resource check.
    sys.path.insert(0, str(ROOT / "tools"))
    import check_strings

    sys.exit(main() | check_strings.main())
