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
| `plan` | `GET /api/v6/plan` | `fromPlace`/`toPlace` = `lat,lon` or a stop id. `preTransitModes`/`postTransitModes` e.g. `CAR_DROPOFF`, capped by `maxPreTransitTime`/`maxPostTransitTime` (s). `directModes=CAR` gives the car route in `direct[]`. Answers carry `previousPageCursor` / `nextPageCursor` (`EARLIER|1791867600`, `LATER|…`); the same query plus `pageCursor=<one>` returns the adjacent window (Earlier / Later, `TripPlanner.page`). |
| `oneToMany` | `GET /api/v1/one-to-many` | `one`/`many` use **`lat;lon`** (semicolon!), many comma-joined. `arriveBy=true` = many→one. `{}` entry = no path. `max` capped by server config. |
| `stops` | `GET /api/v6/map/stops` | `min`/`max` bbox. Transitous ignores `modes` — filter client-side. Long drop-off drives use the bundled `RailStations` instead. |
| `geocode` | `GET /api/v1/geocode` | `text`, `language=he`, `place` bias. Through `search/PlaceSearch` (Photon after a miss, below). |
| `reverseGeocode` | `GET /api/v1/reverse-geocode` | `place`, `numResults=1`: names a long-pressed pin or a "save my current location" fix (`PinName`); 1 request each, cached a day; none → "Pin · lat, lon". |
| `stopTimes` | `GET /api/v6/stoptimes` | departures, `realTime` flag per entry. |
| `trip` | `GET /api/v6/trip` | `tripId` (from a leg or a departure; contains `:` — let OkHttp encode it). Answers an `Itinerary`: one transit leg, first stop → last, every stop in `intermediateStops`. |
| `mapTrips` | `GET /api/v6/map/trips` | `min`/`max` bbox (SW/NE, like `map/stops`), `zoom`, `startTime`/`endTime`. A list of stop-to-stop hops (`TripSegment`); **polyline precision 5**, not 6. |

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
- **Geocode, missing streets (2026-10-07, `geocode_tabenkin_raanana`):** "טבנקין 15 רעננה" (the
  owner's home) near Ra'anana returns 8 other Ra'anana places and no טבנקין, with or without the
  number: the street is not in the index. Fixed by the map pin, "save my current location" and
  the Photon backup (below), not by re-ranking.
- **Geocode, calendar locations (2026-10-04, n=3):** "הרצל 12, באר שבע" → **הרצל 126**
  first (same street, house number matched loosely); English "Herzl St 12, Be'er Sheva,
  Israel" and "Herzl 12, Be'er Sheva" → only the city (PLACE "Be'er Sheva"): English street
  names are not in the index. So "From my calendar" shows the match next to the event's
  text before planning ("הרצל 126 (from: הרצל 12…)", with Change), and marks it approximate
  when a house number in the text is missing from the match or the match is a town
  (`category` `place_*`): `CalendarSuggest.isApproximate`, no extra request. Privacy: only the
  location text is sent (never the title); `lat,lon` in a location skips the request; the
  bias is rounded to 0.1° so the day cache hits (`CalendarSuggest`, fixtures `geocode_calendar_*`).
- **Real-time and alerts — almost none for Israel (checked 2026-10-03).** Transitous' Israel
  config (`public-transport/transitous` → `feeds/il.json`) loads the MOT GTFS timetable and
  ONE GTFS-RT feed: "busofash" (Tel Aviv night/Shabbat buses). MOT lines have no live delays
  and no service alerts there (3 live plans: 0 alerts). MOT's own SIRI feed needs a registered
  key → out under the "no key" rule. So delay badges/alerts only fire on busofash lines;
  "on the bus" delay is GPS vs timetable and does not depend on it. Service alerts: not built.
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
- **Real-time on MOT lines: confirmed none (closed 2026-10-04).** Sat 2026-10-03 23:20, late buses running: 0 of 36 departures
  at Be'er Sheva Central and 0 of 20 at Savidor (6 operators) had `realTime: true`; nor did
  the `trip`/`map/trips` answers. Departure = scheduled everywhere (`stoptimes_now_*`).
  **Sun 2026-10-04 10:16 (weekday, full service): still 0 of 49** at the same two stops
  (7 operators; `stoptimes_weekday_*`). Matches the feed config above (GTFS-RT only for
  busofash). The app stays tolerant: it shows live times whenever `realTime` is true.
- **No service alerts** (2026-10-03/04): no `alerts` on any leg, place or stop time in 9 answers.
  `Alert` is modelled from the MOTIS schema (header/description text, cause, effect,
  `impactPeriod` = validity) but has never been seen from Israel.
- **`cancelled` on WALK legs is noise:** some transfer walks between two stops come back
  `cancelled: true`, with both their places (both plans of 2026-10-03). Only trust it on
  transit legs and their stops.
- **Saturday-night trips appear twice**, one per service day (`…_031026` and `…_041026`, same
  line, same minute) in `stoptimes`. De-duplicate by line + time if it shows.
- **`trip`** (bus 470, train 7026, 2026-10-03): `transfers` 0, a single leg from the trip's
  first stop to its last, `intermediateStops` with arrival + departure each, precision-6
  geometry. Train: `routeShortName` empty, `headsign` = train number, as in `plan`.
- **`map/trips`** (Be'er Sheva box, 1-min window, zoom 14): 134 hops, each with one trip
  (`tripId` + `displayName`), from/to stop and their times, `realTime`, polyline at
  **precision 5**. A train's `displayName` is the long "A<->B" route name, not the number.
  `distance` is not the hop's length. One **Rome → Naples** hop (bad stop at 10.1,40.1)
  crossed the box — select by `tripId`, never trust the box alone.
- **`CAR_PARKING` (pre-transit) is unusable** — see `dead-ends` and `special-features`.

## Earlier / Later and stop platforms (Phase 9 C1)
- **Paging (recorded 2026-10-05, `plan_pages_first/later/earlier`):** a page request repeats
  the first search's query, `time` included (`TripResult.searchEpoch`), plus `pageCursor`, so
  the guard caches each page like any plan: 1 request per tap, a repeated cursor 0 (pinned in
  `TripPagesTest`). No automatic paging. Pages don't overlap in practice, but `TripPages.merge`
  dedupes by trip ids + first boarding's timetable time, re-runs the absurd-trip filter over
  the merged list (a Friday Later page that waits out Shabbat adds nothing) and keeps ≤ 20.
- **Stop `description`** (MOT `stop_desc`) is Hebrew key/value text:
  `רחוב: תחנה מרכזית קומה 6 עיר: תל אביב יפו רציף: 626 קומה: 6` — street, city, **platform
  (`רציף`)**, **floor (`קומה`)**, most values blank (70 of 391 distinct descriptions have
  something in them). `stopCode` is the number on the sign. Parsed defensively by
  `present/StopPlatform.kt`. Train stations: no description, and `track` is empty (`dead-ends`),
  so nothing is shown for rail. A parent station and its platforms may carry different
  `description`s under the same `stopCode` (Be'er Sheva central: 2 vs 10).

## How the app uses `trip` and `map/trips` (trip sheet, Phase 8 B1)
All in `present/TripStops.kt`; the request pattern is pinned in `TripDetailsTest`.
- **`trip`: one per tap** on a bus/train chip of the selected option (`TripDetailsSession.open`);
  the guard caches it 30 s, so close + re-open is free. No answer (offline, no `tripId`) → the
  sheet shows the plan leg's own stops (`legOnly`). Boarding/alighting are matched to the trip's
  stops by stop id, else the nearest stop within 150 m (sibling platforms), nearest in time.
- **`map/trips`: at most one per 30 s** (`TripDetailsSession.REFRESH`), only while the sheet is
  open **and the app is in front**, and only while the timetable has the vehicle on the road
  (from 2 min before its first stop to its last). Window `now … now+60 s`, zoom 14.
- **The box is not the user's leg** but the stretch of the trip between the last stop served and
  the next (`vehicleBox`, +1 km): the sheet is usually opened while waiting, when the vehicle is
  still before the boarding stop. It also keeps answers small (the leg box of a Be'er Sheva →
  Tel Aviv train would cover thousands of hops).
- The mark is matched **by `tripId`** and interpolated by time along the hop's precision-5
  polyline every 2 s between refreshes (`vehicleAt`); between hops it waits at the stop.
  `realTime: false` (every MOT line) → the sheet says "scheduled position".
- Live times, skipped stops and ⚠ alerts render only when present (`TripStopRow.live`,
  `cancelled`, `alertTexts`); alerts are kept only while `inEffectAt` and folded by text.

## Budget
`GuardedTransitApi` wraps the client everywhere: cache (plan 60 s, stops/geocode 1 day,
departures and `trip` 30 s, `mapTrips` 20 s), ≤ 2 concurrent, one retry on 429/503. Each special feature runs in a
`BudgetedTransitApi`; `DropOffPlanner.BUDGET = 10` and `ParkRidePlanner.BUDGET = 5` are pinned by tests. Raising a budget
is a policy decision, not a code tweak — say so in the PR.

| background job | requests | worst case per day | constraints |
|---|---|---|---|
| Night refresh (`NightRefreshWorker`, Phase 9 C2) | `plan` only | 6 (`NightRefresh.MAX_TRIPS`, `BudgetedTransitApi`) + 1 per 429/503 retry | Wi-Fi, charging, battery not low; 22:00–06:00; once per service day; never retried |
| Last trip home (`LastTripReceiver`, Phase 9 C5) | `plan` only (`LastRideFinder`) | 3 (`LastRideFinder.BUDGET`, `BudgetedTransitApi`) + 1 per 429/503 retry; 0 with the setting off (default), no Home, no origin, or within 1 km of Home | one inexact alarm an evening (19:00 Sun–Thu, 12:00 Fri, 20:00 Sat) → WorkManager, network connected; not 02:00–03:59; a check with no signal retried every 15 min while the day's 3 last (spent requests stored before each try) |

## Backup geocoder: Photon (search fix, owner approved 2026-10-07)
The one outside service besides Transitous and OpenFreeMap. `core/search/Photon.kt`:
- `GET https://photon.komoot.io/api?q=…&lang=…&lat=…&lon=…&bbox=34.2,29.45,35.95,33.35&limit=5`,
  answers GeoJSON (`features[].properties.{name,street,housenumber,city,type,osm_value}`,
  `geometry.coordinates` = **[lon, lat]**), mapped to `GeocodeMatch` (`parsePhoton`).
- **Policy:** free, keyless, no card; fair use, search-as-you-type tolerated (komoot's public
  instance). Our User-Agent (`MotisClient.USER_AGENT`); credit "Search: Photon" in the chip and
  "Search © OpenStreetMap / Photon" under its answers (ODbL). Nominatim's public server forbids
  autocomplete → `dead-ends`.
- **When:** only after a Transitous miss: no answer's name/street contains every typed word that
  is not a number, a town the answers lie in, or "רחוב/street" (`PlaceSearch.needsBackup`), and
  the text is ≥ 3 characters. Answers go below Transitous's, duplicates (same name ≤ 150 m) dropped.
- **Budget:** ≤ 1 Photon request per search, a search only after typing pauses 350 ms
  (`TypingSearch`): pinned in `PlaceSearchTest`. `GuardedGeocoder`: day cache (bias rounded to
  0.1°), ≤ 2 at once, one retry on 429/503. A Photon failure never fails the search.
- **Language:** Photon's public instance knows default/en/de/fr; Hebrew sends `lang=default`
  (OSM's local name = Hebrew in Israel), English `en`.
- **Swappable:** `BackupGeocoder` interface; another Photon (or self-hosted) is a base-URL change.
- **Typed town:** when the text names a town one of the answers lies in (whole words,
  `PlaceSearch.typedTowns`), Photon answers in another town are dropped; otherwise each shows its
  town. Recorded 2026-10-07 (`photon_tabenkin_raanana`): OSM has no טבנקין in Ra'anana either;
  Photon's top answer for "טבנקין 15 רעננה" is טבנקין יצחק 15 in **Tel Aviv**, then Kiryat Arba
  (the Israel bbox includes the West Bank). "רגר" → שדרות יצחק רגר, Be'er Sheva (`photon_rager`).
- **Fixtures:** `python tools/record_fixture.py --photon <name> "/api?q=…"` (`photon_*`).
- UI tests never call it (`UiTestApp.backupGeocoder = null`).

## Fixtures
Tests never hit the network. `python tools/record_fixture.py <name> "<url path+query>"`
saves a real response into `core/src/test/resources/fixtures/` (needs network access to
api.transitous.org). All fixtures are real recordings; record future-dated (a weekday,
not a holiday) so the plans make sense, and add a test that pins what you learned.
Inspect any response cheaply with `python tools/plan_summary.py <file.json>`.
