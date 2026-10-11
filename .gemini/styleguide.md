# Review guide for Israel Transit Planner

An Android trip planner for Israeli public transport, for the owner, friends and family. Read
`CLAUDE.md` first: its hard rules decide what is a bug here.

## Flag these as high severity
- **A paid or keyed service, or a server of our own.** The app talks only to free, keyless public
  services named in `CLAUDE.md` (Transitous/MOTIS, OpenFreeMap, Photon, Android's built-in
  `Geocoder`). No Google Maps SDK, Directions or Places, no Moovit, no API keys in the app.
- **A Transitous call that bypasses `TransitApi` / `GuardedTransitApi`**, or a new request without
  a budget pinned by a test (`BudgetedTransitApi`). Traffic to public services must stay light.
- **A unit test that touches the network.** Tests use fixtures in `core/src/test/resources/fixtures/`.
- **A wrong place or a wrong time shown as certain.** Search must never show a different street or
  town as the answer; times for MOT lines are scheduled, not live.
- **Hebrew RTL breakage:** mixed Hebrew/English or times without bidi isolation (FSI/PDI),
  hard-coded left/right instead of start/end, untranslated strings (Hebrew and English must match).

## Medium
- Logic in Compose screens or the ViewModel that belongs in `core` (pure, tested).
- Missing tests for edge cases: Shabbat and holidays, the midnight rollover and the 04:00
  service-day edge, offline, permission denied.
- Main-thread network or disk work; blocking calls without a timeout.

## Don't comment on
- Formatting and naming nits, the goldens (`snapshots/`), recorded fixtures, generated files
  (`RailStations`, the towns table), and the wording of docs and release notes.
- Suggestions to add a dependency, a server, analytics or crash reporting to a backend
  (see `.claude/skills/dead-ends/SKILL.md` for what was rejected and why).

Keep comments short, name the file and line, and say how to reproduce.
