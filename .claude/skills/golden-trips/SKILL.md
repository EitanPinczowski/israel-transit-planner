---
name: golden-trips
description: Check routing sanity against ~28 real reference trips, weekly in CI. Use after changing planning logic, the traffic factor, candidate pruning, or when results "look wrong".
---

# Golden trips

Unit tests prove the arithmetic; only real trips prove the answers are sensible. These
need network access to api.transitous.org (a cloud session may block it — then run on the
owner's machine) and they spend real requests, so run them deliberately. CI runs them once a
week (below); never on a PR.

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
| 12 | Near Kela Alon (Golan) → Tabenkin 15, Tel Aviv, Tuesday 07:00 | no unreasonable chain: 59א + 845 + 616 (2 changes, 07:56–11:50) is first; MOTIS's 6-bus chain (11:49) is dropped by the D12 chain rule (2026-10-12 for Tue 2026-10-13, n=1; `SortOptionsTest`). Checked by `Invariants.chains` with the 10-minute window on both ends (T5; it used to compare change counts only). |
| 13 | Better start #2: Meitar → Tel Aviv, 20′ | drives within the limit (traffic included), front not dominated |
| 14 | Drop-off #2: Haifa Hof HaCarmel → Tel Aviv by car, C = Netanya, 15′ | car route; a transit-from-start baseline; stations within the detour |
| 15 | Pick-up #2: Tel Aviv → Meitar, 20′ | pick-ups are named stops; the driver pays the round trip |
| 16 | Park & ride #2: Netivot → Tel Aviv, Sunday 07:30, 20′ | stations from the bundled list; drive ≥ free flow |
| 17 | Errands: Be'er Sheva home → Center (15′ stay) → BGU | both legs found; each leaves after the last arrives + the stay |
| 18 | Last trip home: Tel Aviv → Be'er Sheva Center, Tuesday | a last trip in the service day, the next one after it |
| 19 | Nahariya → Haifa Hof HaCarmel | train in the first 3 |
| 20 | Katzrin (Golan) → Tiberias | < 2 h |
| 21 | Eilat → Be'er Sheva Center | a bus; fastest < 9 h |
| 22 | Mitzpe Ramon → Be'er Sheva Center | a bus |
| 23 | Jerusalem light rail: Mount Herzl → Central | light rail in the first 3 |
| 24 | Jerusalem CBS → Gilo | a bus, < 60′ |
| 25 | Nazareth → Haifa Merkaz HaShmona | < 2 h |
| 26 | Night: Tel Aviv Allenby → Bnei Brak, 01:30 after Thursday | first option leaves 01:00–05:59 (busofash, the one real-time feed; the report counts options with real-time legs) |
| 27 | Saturday night: Be'er Sheva → Tel Aviv, 20:00 | nothing leaves in the Shabbat afternoon |
| 28 | The first holiday or eve within `DayNotice.HORIZON_DAYS` of the run, Be'er Sheva → Tel Aviv, 10:00 | on a no-service day nothing before 17:00; on an eve, service. No holiday in range: "no holiday in range", this trip only is skipped |

Automated pass: `./gradlew -p core goldenTrips -q` (optional `--args=YYYY-MM-DD` for the weekday;
default next Tuesday, trip 6 that week's Friday, trip 27 the Saturday after, trip 26 the Thursday night).
Source: `core/src/golden/`, a separate source set so `test`/CI never run it. Every answer meets the
**shared invariants** of `core/src/invariants/` (`Invariants`: times in order, legs chain in time ≤ 60 s
overlap and in space ≤ 400 m, ends within 1.5 km, Pareto fronts, ride details add up, fares ≥ 0 and
present, no unreasonable chain, feature budgets); `RoutingInvariantsTest` applies the same functions to the
recordings, so a rule is written once. Plus each trip's own expectation (the table). Writes
`core/build/golden/report.md`; exit 1 if any trip fails.

**Request cap:** the runner counts what leaves for the server (below the cache), keeps ≥ 1 s between
requests and **stops at 60**: the trips after the cap are reported `SKIP … not run`, never failed. The trips
are ordered so the least important come last. 2026-10-03: 10/10 pass, 21 requests (the original 10 trips).

**Weekly live** (`.github/workflows/weekly-live.yml`, Tuesdays 00:47 UTC = 03:47 Israel in summer time,
`workflow_dispatch` any time): the `golden` job runs `goldenTrips`, writes the report into the run summary and
an artifact, and on a failure comments the failing trips on the open "golden-trips" issue (created on the
first failure). It never runs on a PR and never blocks one (owner decision 22). The `contract` job beside
it is in `transitous-api`. Transitous traffic of both: ≤ 60 + 5 requests a week.

Then by hand: record each answer with `tools/record_fixture.py`, summarise with
`tools/plan_summary.py`, compare by hand with Moovit/Google on the same departure time.
Acceptable: arrival within ~10 min of Moovit. Write down any systematic gap (e.g. drives
always 20% short at peak → tune `TrafficProfile`) in the relevant skill, with the date and n.
