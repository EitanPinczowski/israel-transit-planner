#!/usr/bin/env python
"""Fail if the English and Hebrew string resources disagree in a way that crashes or misleads.

Android merges every XML file in `res/values*/`, so this reads all of them (Phase 8 adds
`strings_<feature>.xml` files). For each language it checks:
- the same `<string>` and `<plurals>` names in `values/` and `values-iw/`, and the same
  plural quantities (a missing `other` is a crash on some counts);
- the same format placeholders per key (`%1$s`, `%d`, ...): a Hebrew string with a different
  set makes `getString(id, args)` throw or print the wrong value in one language only;
- no unescaped `'` (aapt rejects it, but only at build time, which a cloud session can't run);
- a literal `%` that is not a placeholder only in a `formatted="false"` string.
"""
from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "android" / "app" / "src" / "main" / "res"
LANGS = {"en": "values", "he": "values-iw"}
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([sdfxXc%])")


def raw_text(el: ET.Element) -> str:
    return "".join(el.itertext())


def placeholders(el: ET.Element) -> list[str]:
    if el.get("formatted") == "false":
        return []  # never passed through String.format, so "50% d" is text, not "% d"
    found = []
    for m in PLACEHOLDER.finditer(raw_text(el)):
        if m.group(2) == "%":
            continue
        found.append(f"{m.group(1) or '?'}${m.group(2)}")
    return sorted(found)


def load(folder: str) -> tuple[dict[str, ET.Element], dict[str, dict[str, ET.Element]], list[str]]:
    strings: dict[str, ET.Element] = {}
    plurals: dict[str, dict[str, ET.Element]] = {}
    errors: list[str] = []
    for path in sorted((RES / folder).glob("*.xml")):
        root = ET.parse(path).getroot()
        for el in root:
            name = el.get("name", "")
            where = f"{folder}/{path.name}: {name}"
            if el.tag == "string":
                if name in strings:
                    errors.append(f"{where} is defined twice")
                strings[name] = el
            elif el.tag == "plurals":
                if name in plurals:
                    errors.append(f"{where} is defined twice")
                plurals[name] = {item.get("quantity", ""): item for item in el.findall("item")}
    return strings, plurals, errors


def check_text(where: str, el: ET.Element, errors: list[str]) -> None:
    text = raw_text(el)
    if re.search(r"(?<!\\)'", text) and not (text.startswith('"') and text.endswith('"')):
        errors.append(f"{where}: unescaped ' (write \\')")
    formatted = el.get("formatted", "true") != "false"
    stripped = PLACEHOLDER.sub("", text)
    if formatted and "%" in stripped:
        errors.append(f'{where}: a literal % needs %% or formatted="false"')


def main() -> int:
    loaded = {lang: load(folder) for lang, folder in LANGS.items()}
    errors = [e for _, _, errs in loaded.values() for e in errs]
    (en_s, en_p, _), (he_s, he_p, _) = loaded["en"], loaded["he"]

    for kind, en, he in (("string", en_s, he_s), ("plurals", en_p, he_p)):
        for name in sorted(en.keys() - he.keys()):
            errors.append(f"{kind} `{name}` has no Hebrew (values-iw)")
        for name in sorted(he.keys() - en.keys()):
            errors.append(f"{kind} `{name}` is only in Hebrew (values-iw)")

    for lang, (strings, plurals, _) in loaded.items():
        folder = LANGS[lang]
        for name, el in strings.items():
            check_text(f"{folder}: {name}", el, errors)
        for name, items in plurals.items():
            if "other" not in items:
                errors.append(f"{folder}: plurals `{name}` has no `other` quantity")
            for q, el in items.items():
                check_text(f"{folder}: {name}[{q}]", el, errors)

    for name in sorted(en_s.keys() & he_s.keys()):
        a, b = placeholders(en_s[name]), placeholders(he_s[name])
        if a != b:
            errors.append(f"string `{name}`: placeholders differ (en {a}, he {b})")
    for name in sorted(en_p.keys() & he_p.keys()):
        # "one" may drop the count ("a minute"), so only the `other` forms must agree.
        if "other" in en_p[name] and "other" in he_p[name]:
            a, b = placeholders(en_p[name]["other"]), placeholders(he_p[name]["other"])
            if a != b:
                errors.append(f"plurals `{name}`: `other` placeholders differ (en {a}, he {b})")

    for e in errors:
        print("strings:", e)
    if not errors:
        print(f"strings ok: {len(en_s)} strings, {len(en_p)} plurals, en = he")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
