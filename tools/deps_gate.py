#!/usr/bin/env python3
"""Dependency gate (Phase 11 T2, owner decision 27): vulnerabilities and licences.

  python3 tools/deps_gate.py osv  osv.json        # OSV-scanner --format json output
  python3 tools/deps_gate.py licences DIR [DIR..] # DIR = <project>/build/deps from deps-report.init.gradle

osv: a HIGH or CRITICAL (CVSS >= 7.0) advisory that has a fixed version fails; the rest are listed.
licences: every release-runtime dependency's POM must name a licence from tools/licence_allowlist.txt.
"""
import json, re, sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GH = __import__("os").environ.get("GITHUB_STEP_SUMMARY")


def say(text):
    print(text)
    if GH:
        with open(GH, "a") as f:
            f.write(text + "\n")


def severity(vuln, group):
    try:
        return float(group.get("max_severity") or 0)
    except ValueError:
        return 0.0


def fixed_versions(vuln):
    out = set()
    for aff in vuln.get("affected", []):
        for r in aff.get("ranges", []):
            for ev in r.get("events", []):
                if "fixed" in ev:
                    out.add(ev["fixed"])
    return sorted(out)


def osv(path):
    data = json.loads(Path(path).read_text())
    blocking, other = [], []
    for res in data.get("results", []):
        for pkg in res.get("packages", []):
            p = pkg["package"]
            vulns = {v["id"]: v for v in pkg.get("vulnerabilities", [])}
            for g in pkg.get("groups", []):
                sev = severity(None, g)
                ids = g.get("ids", [])
                fixes = sorted({f for i in ids if i in vulns for f in fixed_versions(vulns[i])})
                row = (p["name"], p["version"], ids[0] if ids else "?", sev, fixes)
                (blocking if sev >= 7.0 and fixes else other).append(row)
    say("### Dependency vulnerabilities (OSV)")
    for title, rows in (("Blocking (HIGH/CRITICAL, a fixed version exists)", blocking), ("Listed, not blocking", other)):
        say(f"**{title}: {len(rows)}**")
        for n, v, i, s, f in rows:
            say(f"- `{n}` {v}: {i}, CVSS {s}, fixed in {', '.join(f) or 'nothing yet'}")
    if blocking:
        print("::error::a HIGH/CRITICAL advisory with a fix applies to a dependency; upgrade it (see the list above)")
        return 1
    return 0


def load_allow():
    allow, exceptions = [], {}
    for line in (ROOT / "tools" / "licence_allowlist.txt").read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("exception "):
            coord, _, why = line[len("exception "):].partition(" ")
            exceptions[coord] = why
            continue
        spdx, _, aliases = line.partition(":")
        allow += [(a.strip().lower(), spdx.strip()) for a in aliases.split("|") if a.strip()]
    return allow, exceptions


NS = re.compile(r"^\{[^}]*\}")


def strip(root):
    for e in root.iter():
        e.tag = NS.sub("", e.tag)
    return root


def pom_licences(pom, seen=0):
    root = strip(ET.parse(pom).getroot())
    lic = [((l.findtext("name") or "") + " " + (l.findtext("url") or "")).strip() for l in root.iter("license")]
    if lic or seen > 4:
        return lic
    parent = root.find("parent")
    if parent is None:
        return []
    g, a, v = (parent.findtext(x) for x in ("groupId", "artifactId", "version"))
    for cache in (Path.home() / ".gradle/caches/modules-2/files-2.1" / g / a / v).glob("*/*.pom"):
        return pom_licences(cache, seen + 1)
    return []


def licences(dirs):
    allow, exceptions = load_allow()
    bad, ok = [], {}
    for d in dirs:
        tsv = Path(d) / "runtime.tsv"
        for line in tsv.read_text().splitlines():
            if not line.strip():
                continue
            coord, pom = line.split("\t")
            ga = ":".join(coord.split(":")[:2])
            if ga in exceptions or coord in exceptions:
                ok[coord] = "exception"
                continue
            lic = pom_licences(pom)
            hit = {spdx for text in lic for alias, spdx in allow if alias in text.lower()}
            if lic and hit:
                ok[coord] = "/".join(sorted(hit))
            else:
                bad.append((coord, lic))
    say("### Release dependency licences")
    by = {}
    for c, s in ok.items():
        by.setdefault(s, []).append(c)
    for s, cs in sorted(by.items()):
        say(f"- {s}: {len(cs)}")
    say(f"**Not allowed or unknown: {len(bad)}**")
    for c, l in bad:
        say(f"- `{c}`: {l or 'no licence in its POM'}")
    if bad:
        print("::error::a release dependency has a licence that is not in tools/licence_allowlist.txt (or none)")
        return 1
    return 0


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "osv":
        sys.exit(osv(sys.argv[2]))
    if len(sys.argv) >= 3 and sys.argv[1] == "licences":
        sys.exit(licences(sys.argv[2:]))
    sys.exit(__doc__)
