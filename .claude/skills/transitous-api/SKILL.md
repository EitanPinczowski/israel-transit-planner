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
4. Keep traffic light; **contact them before routine use of routing endpoints**. Draft:
   `docs/transitous-contact.md`. Until they answer, keep usage to the owner's testing.
5. Don't scrape — bulk data is downloadable from them instead.

## Endpoints we use

| call | endpoint | notes |
|---|---|---|
| `plan` | `GET /api/v6/plan` | `fromPlace`/`toPlace` = `lat,lon` or a stop id. `preTransitModes`/`postTransitModes` e.g. `CAR_DROPOFF`, capped by `maxPreTransitTime`/`maxPostTransitTime` (s). `directModes=CAR` gives the car route in `direct[]`. |
| `oneToMany` | `GET /api/v1/one-to-many` | `one`/`many` use **`lat;lon`** (semicolon!), many comma-joined. `arriveBy=true` = many→one. `{}` entry = no path. `max` capped by server config. |
| `stops` | `GET /api/v6/map/stops` | `min`/`max` bbox, optional `modes` filter (we pass rail-like for long drives to keep responses small). |
| `geocode` | `GET /api/v1/geocode` | `text`, `language=he`, `place` bias. |
| `stopTimes` | `GET /api/v6/stoptimes` | departures, `realTime` flag per entry. |

Times are ISO-8601 with offset; parse with `parseTime()` (OffsetDateTime), never assume `Z`.

## Unverified until the phase-0 spike (record real answers, then update this list)
- `CAR_DROPOFF` is marked **Experimental** in MOTIS; Transitous may not enable it.
  Fallback: `CAR` (`BetterStartQuery.carMode`).
- Whether `CAR_DROPOFF` is allowed as a **post**-transit mode (pick-up). Default is `CAR`.
- Server caps: `street_routing_max_prepost_transit_seconds` may be < 30 min (the slider max).
- Real-time coverage for Israel beyond the busnear.by GTFS-RT feed.
- Empty `transitModes=` (our `directOnly`) really skips the transit search.
- `map/stops` bbox corner convention (the docs say "lower right"/"upper left"; we send
  min-lat/min-lon and max-lat/max-lon).

## Budget
`GuardedTransitApi` wraps the client everywhere: cache (plan 60 s, stops/geocode 1 day,
departures 30 s), ≤ 2 concurrent, one retry on 429/503. Each special feature runs in a
`BudgetedTransitApi`; `DropOffPlanner.BUDGET = 10` is pinned by a test. Raising a budget
is a policy decision, not a code tweak — say so in the PR.

## Fixtures
Tests never hit the network. `python tools/record_fixture.py <name> "<url path+query>"`
saves a real response into `core/src/test/resources/fixtures/` (needs network access to
api.transitous.org). `plan_synthetic.json` is hand-written — replace it with a recording.
Inspect any response cheaply with `python tools/plan_summary.py <file.json>`.
