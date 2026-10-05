# Phase 9 ideas — research

Researcher pass, 2026-10-05. Nine ideas, ranked by value ÷ effort (best first). Every idea is
free, keyless, server-less, uses only Transitous/MOTIS or on-device Android APIs, and is not in
`ROADMAP.md` or `dead-ends`. No MOT real-time is assumed anywhere: every time is scheduled.

**Live checks made: 5 of 6 allowed** (User-Agent `IsraelTransitPlanner/0.1 (+repo URL)`,
Tue 2026-10-06 06:00 local as the query time). The rest is checked against the recorded
fixtures in `core/src/test/resources/fixtures/` and MOTIS's `openapi.yaml`; each idea says which.

| # | Idea | Effort | Requests per use | Verified |
|---|---|---|---|---|
| 1 | Share this trip (WhatsApp text) | S | 0 | n/a, on-device |
| 2 | Earlier / Later + "if I miss it" | S | 1 per tap | cursors in fixtures; no live call |
| 3 | Safe-transfer margin setting | S | 0 extra | parameter already in `TransitApi` |
| 4 | Boarding platform + stop code | S | 0 extra | fixtures (real data) |
| 5 | Home-screen widget (next departures) | M | 0–1 per refresh | stoptimes live-recorded already |
| 6 | "What leaves near me" board | M | 1 | **live** |
| 7 | Bike to the station | M | ≤ 4 | **live** |
| 8 | Reach map + "meet halfway" | M–L | 1 (2 for meet) | **live** |
| 9 | Line explorer (route map of a line) | L | 1 | **live**, noisy |

---

## 1. Share this trip — S
**User gets:** one tap on a selected option sends a plain-text itinerary to WhatsApp or SMS:
"Sun 08:05 Be'er Sheva Central → 470 → Tel Aviv HaHagana, arrive 09:40 (≈ ₪16)", with the
walking and transfer steps. Family can see when to expect them; friends get the plan.

**Data:** none. The selected `Itinerary` is already on the phone; text goes through
`Intent.ACTION_SEND` (same as the car features' "Send to driver"). Requests: 0. Verified: not
applicable. `TripPanel.kt` has no share action today (only the three car panels do).

**Effort:** S. `present/` (a tested `ItineraryText` formatter, he + en), `TripPanel`, strings.

**Risks:** none on data. Open: include a Waze/Maps link to the first stop? Reuse `NavLinks`.
No conflict.

## 2. Earlier / Later results + "if I miss it" — S
**User gets:** "Earlier" and "Later" buttons under the options list, and on the selected option
"If I miss this: next is 08:35 (+30 min)". With scheduled-only data and sparse lines (a missed
bus can mean 30–60 min), this is the most useful safety net.

**Data:** `plan` already returns `nextPageCursor` / `previousPageCursor`; passing it back as
`pageCursor` returns the adjacent window. Verified in recorded fixtures
(`plan_now_bs_hahagana.json`: `LATER|1791060420`, `EARLIER|1791059100`); I did not spend a live
call on the follow-up page. `grep pageCursor` finds nothing in `core/src/main` or `android`, so
the app does not use it. Requests: 1 per tap, under the existing `plan` cache.

**Effort:** S–M. `api/` (`PlanRequest.pageCursor`, `PlanResult` cursors), `plan/TripPlanner`,
`TripPanel`. Sort chips must apply to the merged list.

**Risks:** merging pages must dedupe; the later page may repeat an option that crosses Shabbat
(see the `LastRideFinder` trap in `transitous-api`). Interacts with Routines/leave reminder:
the reminder must follow the option the user selected, not the first one.

## 3. Safe-transfer margin setting — S
**User gets:** a Settings choice "Transfer buffer: normal / +5 / +10 min". With no real-time, a
tight bus-to-train transfer is the commonest way a trip fails.

**Data:** `plan` takes `additionalTransferTime` (minutes); `TransitApi.kt:103` already sends it
when `Preferences.additionalTransferSec` is set. I found no UserSettings field feeding it
(grep), so the owner cannot set it today; the planner should confirm before scoping.
Requests: 0 extra. Not verified live: the parameter is in the schema and already coded.

**Effort:** S. `user/UserSettings` → Preferences, `SettingsDialog`, strings; a golden trip
to check the answers still look sane. **Risks:** a bigger buffer can drop the only option in a
sparse corridor; the UI should say "no plan with this buffer" and offer the normal one.

## 4. Boarding platform and stop code — S
**User gets:** in trip details and on the stop sheet: "Platform 12 · stop 47899" for Be'er Sheva
Central and Tel Aviv Central bus stations, where the right rotzef is half the trip.

**Data:** the stop `description` string, already in every `plan`/`stoptimes`/`trip` answer, has
a `רציף: N` field, and `קומה:` (floor) for Tel Aviv Central. In all recorded fixtures: 70 of 391
distinct stop descriptions carry a platform number (e.g. `רציף: 15`, `רציף: 626 קומה: 6`);
the rest are blank, so it is only a bonus when present. `Models.kt` does not model
`description`/`stopCode` yet. Requests: 0 extra. **Not for trains:** the `track` field is
empty on every Israel Railways leg I looked at (fixtures + live plan of 2026-10-06, 4 itineraries).

**Effort:** S. `api/Models` (+2 fields), `present/` (a tested parser for the Hebrew key/value
text), `TripDetailsSheet`, strings. **Risks:** the text format is MOT's, not MOTIS's; parse
defensively and show nothing when blank.

## 5. Home-screen widget — M
**User gets:** a widget "My lines": next 3 departures of the starred lines at the home stop,
and optionally "next trip to Tel Aviv". Glance at the phone instead of opening the app.

**Data:** `stoptimes` (verified live earlier: `stoptimes_weekday_*`) for starred lines; the app
already stores "last saved board per stop" for My lines offline, and the timetable is scheduled
anyway, so the widget can draw from that cache and recompute "upcoming only" with no network.
Requests: 0 per redraw; ≤ 1 per stop per refresh (WorkManager, ≥ 30 min, only if the cache is
older than a day). **Verified live:** not needed beyond the existing fixtures.

**Effort:** M. `android/` only (new Glance/RemoteViews widget, `data/*Store` read), no core
change except maybe a `present/` row formatter. **Risks:** battery and exact times at the edge
of a cache day; Doze delays. Overlaps with My lines and the app-icon shortcuts, but does not
change them.

## 6. "What leaves near me" board — M
**User gets:** a button "Departures near me": the next buses and trains from every stop within
300 m, merged by line and direction, with walk time. Good for local buses when you just step
outside.

**Data:** `GET /api/v6/stoptimes?center=lat,lon&radius=300&n=…`. **Verified live** (centre at
Be'er Sheva Central, 300 m, Tue 06:00): HTTP 200, 37 KB, **17 distinct stop ids** (many
platforms of the same station) and 18 stop times in one call, each with `mode`, `headsign`. It
is not necessarily sorted per stop, so the client must group by `routeShortName` + headsign
and drop duplicates. Requests: 1 (guard caches stoptimes 30 s).

**Effort:** M. `api/` (`stopTimesAround`), `plan/` or `present/` (group/dedupe, tested),
a new panel or a mode of the stop sheet. **Risks:** `n` did not cap the count as I expected
(18 for `n=12`), so cap on the client; at big stations the list is dominated by one hub. All
times scheduled. Overlaps the existing stop sheet; this is the same board with a radius.

## 7. Bike to the station — M
**User gets:** "Bike start": cycle up to 10/15/20 min to a station, then train/bus on, shown as
a Pareto list like Better start (no driver, so cost = bike minutes). Cheaper than asking for a
lift, and works from Be'er Sheva neighbourhoods to the north station.

**Data:** `plan` with `preTransitModes=BIKE&maxPreTransitTime=…`. **Verified live** (Neve Noy
area → Tel Aviv HaHagana, 06:00, 15-min cap): 4 itineraries; the second is **BIKE 11 min →
REGIONAL_RAIL 78 min → WALK → BUS**, total 101 min; the others were BIKE 1 min then bus (88 min).
So the router does choose bike-to-train when it helps. Requests: ≤ 4 (cap ladder + transit-only
baseline), a `BudgetedTransitApi` budget like the other features.

**Effort:** M. `features/` (a `BikeStart` using the shared Pareto engine), `plan/`, a tab or a
mode of Better start, strings, a golden trip. **Risks:** MOTIS knows nothing about bike
carriage rules or where to lock a bike; bikes on Israel Railways may be restricted at peak, and
the answer cannot say, so the text must say "check bike rules". Summer heat and hills not
modelled. Real-world value depends on cycling habits: ask the owner.

## 8. Reach map and "meet halfway" — M–L
**User gets:** "Where can I get in 45/60/90 min?" — a map of coloured stops from my location
or a saved place, with "max 1 transfer". Tap a stop to plan there. Extension: enter two
starts (me + a friend) and get stops reachable by both, best by the later arrival, to
choose a meeting point halfway between Be'er Sheva and Tel Aviv.

**Data:** `GET /api/v6/one-to-all?one=<stopId or lat,lon>&maxTravelTime=…&maxTransfers=…` (not
used by the app). **Verified live** (Be'er Sheva Central stop, 90 min, Tue 06:00): HTTP 200, but
**1.86 MB**, 4,855 stops, each `{place, duration (min), k (transfers)}`, 0–90 min. `arriveBy=true`
reverses it ("who can reach here by 09:00"). Requests: 1 (2 for meet halfway), but heavy:
default `maxTravelTime` 45–60 and `maxTransfers` set to keep answers smaller, cache an hour,
never auto-refresh.

**Effort:** M for the map, L with meet-halfway. `api/` (parse + stream the large JSON on a
background thread), `geo/`, `present/` (bands), `MapController` (new layer). **Risks:** the
payload size is the main policy risk ("keep traffic light"), so ask Transitous' Matrix channel
or start with the one-request minimum. Stops only, not streets, so the map is dots, not an
isochrone. Do not call it a travel guarantee: scheduled times only.

## 9. Line explorer — L (lowest value)
**User gets:** type a line number, see its route and stops on the map, plus its terminus names.
Helps when someone says "take the 470" without knowing where it goes.

**Data:** `GET /api/experimental/map/routes?zoom=&min=&max=`. **Verified live** (Be'er Sheva box,
zoom 14): 564 routes, **618 KB**, 462 BUS with `shortName` ("555") and `longName` of
Hebrew termini, plus REGIONAL_RAIL; but the answer was **not limited to the box** and
**included Italian Trenitalia routes** (`it-trenitalia…` stops in Rome/Naples), and the endpoint
is marked *experimental*. Requests: 1, heavy. A cheaper source of "where does line N go" is
the `trip` call the app already makes from a leg, so this idea adds little.

**Effort:** L (filtering, client-side search, new layer). **Risks:** experimental API may change
or go away; payload size; low added value over trip details. Recommend leaving it out.

---

## Checked and rejected (candidates for `dead-ends`)
| idea | why not | evidence |
|---|---|---|
| Bike / scooter rentals (Tel-O-Fun, Waze scooters) | Transitous carries no Israeli rental feeds | `GET /api/v1/rentals?point=32.0853,34.7818&radius=3000` (Tel Aviv centre, 3 km), 2026-10-06: HTTP 200, **0 providers, 0 stations, 0 vehicles, 0 zones** (n=1) |
| Rail platform numbers from `track` | empty on every Israel Railways leg | live plan 2026-10-06 + all fixtures: no `track` on train legs. (Bus-station platforms in stop `description` do exist, see idea 4) |
| `refresh-itinerary` | refreshes with real-time data, and MOT has none | same reason as the real-time row in `dead-ends` |

## Top 3 recommendation
1. **Earlier / Later + "if I miss it" (#2).** Best value for effort: one request, data already
   returned and unused, and it directly fixes the biggest weakness of scheduled-only data.
2. **Share this trip (#1).** No data, no requests, an afternoon's work, and it is what family
   and friends ask for. Do it in the same package as #4 and #3, all S and all touching the trip
   panel and settings.
3. **Home-screen widget (#5).** The one idea that changes daily use: next departures without
   opening the app, built on the My lines cache that already exists, with almost no network.

If the owner wants one "feature" rather than polish, take **Bike to the station (#7)** instead of
the widget: live-verified and it reuses the Better start machinery.

Conflicts to schedule: #1, #2, #3 and #4 all edit `TripPanel`/trip details, so they are one
package or run in sequence. #6 and #5 both touch the stop board and My lines.
