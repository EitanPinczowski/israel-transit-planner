#!/usr/bin/env python
"""Fail if the English and Hebrew string resources disagree or break the RTL rules.

Checks (rules from .claude/skills/i18n-rtl):
  - every <string>/<plurals> exists in both values/ and values-iw/
  - the same format placeholders (%d, %1$s, ...) in both languages
  - plurals: `one` + `other` in both; Hebrew plurals of a %d also need `two`
  - no "→" anywhere (it points backwards in RTL; use an en dash)
  - Hebrew: every %d is wrapped in FSI/PDI (U+2068 ... U+2069) so digits don't reorder
  - Hebrew delay strings (`late*`): an LRM (U+200E) or RLM (U+200F) directly before "+"

A mismatch here shows up on a phone as a crash (missing format argument) or as text in
the wrong order, and neither is caught by the build. CI runs this next to check_docs.py.
"""
from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "android" / "app" / "src" / "main" / "res"
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sd]")
FSI, PDI, LRM, RLM = "⁨", "⁩", "‎", "‏"


def unescape(s: str) -> str:
    """Android's \\uXXXX escapes, so a rule sees the same characters the phone does."""
    return re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), s)


def load(path: pathlib.Path) -> dict[str, dict[str, str]]:
    """name -> {quantity or "": text}."""
    out: dict[str, dict[str, str]] = {}
    for el in ET.parse(path).getroot():
        name = el.get("name")
        if el.tag == "string":
            out[name] = {"": unescape("".join(el.itertext()))}
        elif el.tag == "plurals":
            out[name] = {i.get("quantity"): unescape("".join(i.itertext())) for i in el.findall("item")}
    return out


def placeholders(text: str) -> list[str]:
    return sorted(PLACEHOLDER.findall(text))


def check(en: dict[str, dict[str, str]], iw: dict[str, dict[str, str]]) -> list[str]:
    errors = []
    for name in sorted(en.keys() - iw.keys()):
        errors.append(f"`{name}` is in values/ but not values-iw/")
    for name in sorted(iw.keys() - en.keys()):
        errors.append(f"`{name}` is in values-iw/ but not values/")

    for name in sorted(en.keys() & iw.keys()):
        e, h = en[name], iw[name]
        plural = "" not in e
        if plural != ("" not in h):
            errors.append(f"`{name}` is a plural in one language only")
            continue
        if plural:
            for lang, forms in (("en", e), ("iw", h)):
                for q in ("one", "other"):
                    if q not in forms:
                        errors.append(f"`{name}` ({lang}) has no `{q}` form")
            if "%d" in h.get("other", "") and "two" not in h:
                errors.append(f"`{name}` (iw) counts with %d but has no `two` form")
            # `one` may drop the number ("החלפה אחת"); `other` must carry the same ones.
            if placeholders(e.get("other", "")) != placeholders(h.get("other", "")):
                errors.append(f"`{name}`: placeholders differ: en {placeholders(e.get('other', ''))} iw {placeholders(h.get('other', ''))}")
        elif placeholders(e[""]) != placeholders(h[""]):
            errors.append(f"`{name}`: placeholders differ: en {placeholders(e[''])} iw {placeholders(h[''])}")

    for lang, table in (("en", en), ("iw", iw)):
        for name, forms in table.items():
            for text in forms.values():
                if "→" in text:
                    errors.append(f"`{name}` ({lang}) uses →; use an en dash (–)")

    for name, forms in iw.items():
        for q, text in forms.items():
            where = f"`{name}`" + (f" [{q}]" if q else "") + " (iw)"
            for m in re.finditer(r"%(?:\d+\$)?d", text):
                before, after = text[: m.start()], text[m.end():].removeprefix("%%")  # "42%" is one unit
                if not (before.endswith(FSI) and after.startswith(PDI)):
                    errors.append(f"{where}: {m.group(0)} is not wrapped in FSI/PDI")
            if name.startswith("late"):
                for m in re.finditer(r"\+", text):
                    if m.start() == 0 or text[m.start() - 1] not in (LRM, RLM):
                        errors.append(f"{where}: '+' needs an LRM/RLM right before it")
    return errors


def main() -> int:
    en = load(RES / "values" / "strings.xml")
    iw = load(RES / "values-iw" / "strings.xml")
    errors = check(en, iw)
    for e in errors:
        print("strings:", e)
    if not errors:
        print(f"strings ok: {len(en)} names in both languages")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
