---
name: golden-trips
description: Check routing sanity against eleven real reference trips. Use after changing planning logic, the traffic factor, candidate pruning, or when results "look wrong".
---

# Golden trips

Unit tests prove the arithmetic; only real trips prove the answers are sensible. These
need network access to api.transitous.org (a cloud session may block it — then run on the
owner's machine) and they spend real requests, so run them deliberately, not in CI.

| # | trip | expect |
|---|---|---|
| 1 | BGU campus → Tel Aviv Savidor Center | train from Be'er Sheva North/University |
| 2 | Be'er Sheva Center → Jerusalem Central Bus Station | direct intercity bus, or train |
| 3 | Be'er Sheva Ramot → BGU | local bus, < 30 min |
| 4 | Tel Aviv HaShalom → Haifa Hof HaCarmel | direct train |
| 5 | Jerusalem Central → Tel Aviv (arrive by 09:00, weekday) | train or direct intercity bus, leaving in time |
| 6 | Friday 15:00, Be'er Sheva → Tel Aviv | still service; flag last trips |
| 7 | Better start: Be'er Sheva Neve Ze'ev → Tel Aviv, 10 min | suggests a Be'er Sheva station |
| 8 | Drop-off: Be'er Sheva → Tel Aviv by car, C = Rehovot | car route ~93′ at peak; candidates spread along the drive (Lehavim … HaHagana). At the default 10′ limit no station survives — getting off the highway costs 14–21′ at peak; at 20′ Lehavim → train → bus 26 arrives 2′ before "ride to TA" (2026-10-05 08:00, n=1). Transit from the start (3 buses) is fastest. |
| 9 | Pick-up: Tel Aviv → Be'er Sheva home | Be'er Sheva Center or North station |
| 10 | Tel Aviv Carlebach → Petah Tikva (Red Line light rail) | light rail |
| 11 | Park & ride: Meitar → Tel Aviv, Sunday 07:30 peak, 20′ limit | in reach at ×1.3: Be'er Sheva North (877 s free flow) and Center (902 s); Lehavim is not. Park at North, leave 08:10, direct 08:34 train, arrive 09:46 — same arrival as the baseline (walk + bus 253 + train, leaves 07:30) but no transfer and 40′ later from home. Center's same train is dominated (longer drive). 4 requests (2026-10-04 for Sun 2026-10-11, n=1; `ParkRideRecordedTest`). |

Automated first pass: `./gradlew -p core goldenTrips -q` (optional `--args=YYYY-MM-DD` for the
weekday; default next Tuesday, trip 6 that week's Friday). Source: `core/src/golden/`, a
separate source set so `test`/CI never run it. It checks an answer exists, the expected mode,
legs that chain in time (≤ 60 s overlap) and space (≤ 400 m), ends within 1.5 km, arrive-by
kept, fares present, Pareto fronts, feature budgets — and writes `core/build/golden/report.md`.
Exit 1 if any trip fails. ~21 requests (2026-10-03: 10/10 pass, 21 requests).

Then by hand: record each answer with `tools/record_fixture.py`, summarise with
`tools/plan_summary.py`, compare by hand with Moovit/Google on the same departure time.
Acceptable: arrival within ~10 min of Moovit. Write down any systematic gap (e.g. drives
always 20% short at peak → tune `TrafficProfile`) in the relevant skill, with the date and n.
