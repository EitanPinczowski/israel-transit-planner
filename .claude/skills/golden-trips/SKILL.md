---
name: golden-trips
description: Check routing sanity against ten real reference trips. Use after changing planning logic, the traffic factor, candidate pruning, or when results "look wrong".
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
| 8 | Drop-off: Be'er Sheva → Tel Aviv by car, C = Rehovot | a rail station near Route 6/40, small detour, beats "ride to TA" |
| 9 | Pick-up: Tel Aviv → Be'er Sheva home | Be'er Sheva Center or North station |
| 10 | Tel Aviv Carlebach → Petah Tikva (Red Line light rail) | light rail |

Procedure: record each answer with `tools/record_fixture.py`, summarise with
`tools/plan_summary.py`, compare by hand with Moovit/Google on the same departure time.
Acceptable: arrival within ~10 min of Moovit. Write down any systematic gap (e.g. drives
always 20% short at peak → tune `TrafficProfile`) in the relevant skill, with the date and n.
