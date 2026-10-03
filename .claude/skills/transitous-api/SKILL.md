---
name: transitous-api
description: Everything about calling Transitous / MOTIS - endpoints, parameters, the usage policy, the request budget, and recording test fixtures. Load before adding or changing any call in core/api, or when a request fails or returns something unexpected.
---

# Transitous (MOTIS v6) — how we call it

Base URL `https://api.transitous.org` (staging: `https://staging.api.transitous.org`).
Schema: `https://github.com/motis-project/motis/blob/master/openapi.yaml`. Only the fields
we use are modelled in `core/api/Models.kt`, with `ignoreUnknownKeys`.

## The policy (https://transitous.org/api/) — these are project rules
1. Project must be open source and non-commercial → the repo is public, MIT.
2. Send a real User-Agent with contact info → `MotisClient.USER_AGENT` (repo URL).
3. Visible link to https://transitous.org/sources/ → the attribution chip on the map.
4. Keep traffic light; **contact them before routine use of routing endpoints** — their
   API page points to their **Matrix channel** for this. The owner sent
   `docs/transitous-contact.md` there; until they answer, keep usage to the owner's own
   testing. Their reply (a rate, caps, `CAR_DROPOFF`) belongs in this skill.
5. Don't scrape — bulk data is downloadable from them instead.

## Endpoints we use

| call | endpoint | notes |
|---|---|---|
| `plan` | `GET /api/v6/plan` | `fromPlace`/`toPlace` = `lat,lon` or a stop id. `preTransitModes`/`postTransitModes` e.g. `CAR_DROPOFF`, capped by `maxPreTransitTime`/`maxPostTransitTime` (s). `directModes=CAR` gives the car route in `direct[]`. |
| `oneToMany` | `GET /api/v1/one-to-many` | `one`/`many` use **`lat;lon`** (semicolon!), many comma-joined. `arriveBy=true` = many→one. `{}` entry = no path. `max` capped by server config. |
| `stops` | `GET /api/v6/map/stops` | `min`/`max` bbox. Transitous ignores `modes` — filter client-side. Long drop-off drives use the bundled `RailStations` instead. |
| `geocode` | `GET /api/v1/geocode` | `text`, `language=he`, `place` bias. |
| `stopTimes` | `GET /api/v6/stoptimes` | departures, `realTime` flag per entry. |

Times are ISO-8601 with offset; parse with `parseTime()` (OffsetDateTime), never assume `Z`.

## Verified against the live server (phase-0 spike, fixtures in `core/src/test/resources/fixtures/`)
- **`CAR_DROPOFF` is accepted but useless as a default.** Answers open with a 0-s car stub
  and drive only a few minutes (Meitar → Tel Aviv, 20-min cap: 4-min drive, arrive 07:35;
  `CAR` drives 15 min to Be'er Sheva North, arrive 06:46). Better start uses `CAR`;
  `leadingCarLegs()` still parses the CAR_DROPOFF shape (CAR 0′, WALK, CAR, WALK).
- **Post-transit `CAR_DROPOFF`** is accepted too, but answers end `…CAR, WALK` — no trailing
  car leg. Pick-up keeps `CAR`, which ends with the car leg from the station (`plan_car_post_pickup`).
- **`direct[]` is capped by `maxDirectTime`, default 30 min — silently.** A 73-min car route
  came back empty without it. Send `PlanRequest.maxDirectSec` for any long direct trip
  (DropOff sends 4 h; accepted).
- Empty `transitModes=` works: `itineraries` empty, `direct` filled.
- `maxPreTransitTime` / `maxPostTransitTime` of 1200 s are honoured.
- `one-to-many` with 4 points and `max=5400`: `{}` for an unreachable point, as modelled.
- `map/stops` with `min`=SW, `max`=NE returns the stops inside. **The `modes` filter is
  ignored**: Be'er Sheva → Tel Aviv's corridor returned 6,417 stops, mostly buses. Stop ids look like
  `il-Israel-MOT_37314`.
- **Geocode:** without `placeBias` the `place` bias is weak ("רגר" near Be'er Sheva → Agra,
  Zagreb, Riga). `placeBias=10` keeps every answer in Israel; `MotisClient` sends it.
- **Geocode, very short words:** matching is loose. Even with the bias, "רגר" → Hagar (הגר),
  Rigba…; Rager Blvd is not in the top 30, while "שדרות רגר" puts it first (2026-10-03, n=1).
  Re-ranking cannot fix a missing answer, so the app shows a "type the full name" hint
  (`needsFullNameHint` in `present/Format.kt`).
- **Last trip of the day (2026-10-03, live):** `arriveBy=true` at 03:00 the next morning
  returns the evening's latest trips — Fri 9 Oct Be'er Sheva → Tel Aviv: 15:29. Two traps,
  handled in `LastRideFinder`: a late trip that arrives after 03:00 (night line 469, hourly
  all night) is invisible to that search; and a depart-at search after the last Friday trip
  offers a 16:20 bus that **waits out Shabbat** (arrives Saturday night) — not a "next trip".
- **`pedestrianProfile=WHEELCHAIR`** is accepted and changes the answer (BGU → Tel Aviv: a bus
  to the station instead of the 836 m walk). `map/stops` carries no wheelchair field, so
  vehicle/stop accessibility is unknown — the app says so.
- **Walking `steps[]`:** only `CONTINUE` or `STAIRS` (2,612 recorded steps), `streetName` on
  ~16% of steps overall but most of a street walk, and a polyline per step. Turns are computed
  from the polylines in `present/WalkDirections.kt`; `plan_walk_beersheva_streets.json` pins
  a real 1.4 km walk (Bialik → Basel → Ussishkin → Weizmann → Wolfson → HaTikva).
- **Israel Railways:** `routeShortName` is empty, `displayName` is "A-city<->B-city",
  `headsign` is the train number ("406"); the terminus is `tripTo.name`. See
  `lineLabel()` / `headsignText()`.
- Still open: real-time coverage (the spike ran on a Friday evening — Shabbat, no service).
  Record a `stoptimes` for "now" on a weekday and check for `realTime: true`.

## Budget
`GuardedTransitApi` wraps the client everywhere: cache (plan 60 s, stops/geocode 1 day,
departures 30 s), ≤ 2 concurrent, one retry on 429/503. Each special feature runs in a
`BudgetedTransitApi`; `DropOffPlanner.BUDGET = 10` is pinned by a test. Raising a budget
is a policy decision, not a code tweak — say so in the PR.

## Fixtures
Tests never hit the network. `python tools/record_fixture.py <name> "<url path+query>"`
saves a real response into `core/src/test/resources/fixtures/` (needs network access to
api.transitous.org). All fixtures are real recordings; record future-dated (a weekday,
not a holiday) so the plans make sense, and add a test that pins what you learned.
Inspect any response cheaply with `python tools/plan_summary.py <file.json>`.
