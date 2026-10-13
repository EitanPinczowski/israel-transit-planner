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
| `stopTimes` | `GET /api/v6/stoptimes` | departures, `realTime` flag per entry. Answers carry `previousPageCursor` / `nextPageCursor`; the same query plus `pageCursor` gives the next window (day timetable, below). |
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
### What the guards do when the network misbehaves (pinned by `NetworkFaultTest`, Phase 10 D4)
| fault | `GuardedTransitApi` / `GuardedGeocoder` | against the budget |
|---|---|---|
| 429 / 503, then 200 | waits `Retry-After` (else 2 s), one retry inside the same slot, answer cached | counted once |
| 429 / 503 twice | the second refusal propagates; never a third call; nothing cached | counted once |
| `Retry-After` > 30 s (`MAX_RETRY_WAIT`) | no retry, the refusal propagates at once (no slot asleep for an hour) | counted once |
| 400 / 404 / 500 / 502 | no retry | counted once |
| slow past the caller's deadline, or cancelled (also while waiting out a 429) | `CancellationException`; slot freed, nothing cached | counted |
| OkHttp timeout / `IOException` | propagates as is, not retried, not cached | counted |
| malformed, empty, truncated or HTML body | `SerializationException` (an `IllegalArgumentException`); not cached; one-to-many with the wrong count is an `IllegalStateException` | counted |
| 3 at once, limit 2 | the third waits for a slot (retries stay inside their slot) | each counted |

Tests inject these at the `TransitApi` / `BackupGeocoder` level (`FaultyTransitApi`, `FaultyGeocoder`) or in an OkHttp
interceptor (`ScriptedHttp`): no socket, not even localhost. `RoutingInvariantsTest` checks times, ride details, fares,
traffic and Pareto fronts over every `plan_*` fixture and each feature's output; a new fixture is covered automatically.

The "Keep transfers at least" setting (Phase 10 D1) sends no request of its own: it adds
`additionalTransferTime` (minutes) to every `plan`, so it is part of the cache key (`TransfersTest`).

Holidays + dates (Phase 10 D3) build every notice from the bundled calendar, 0 requests
(`DayNoticeTest`); "Plan for <day>" re-plans once, on tap (`MainActions.planOn`).

**Big-stop changes (Phase 10 D5): 2 `trip` per opened option**, under `BudgetedTransitApi(2)`
(`BigStopChanges.BUDGET`), only when the user opens (selects) a Trip option whose tightest change is at
a small stop; 0 for a direct option, a change already at a big stop, errands or an offline copy. The
two are the vehicles around that change, cached by the guard like the trip sheet's own (opening the
sheet within 30 s costs 0). An option opened again sends 0 for as long as its results last,
found or not (`BigStopSession`; a failed check is not remembered). Pinned in `BigStopChangeTest`. **Trip ids expire with each feed update**
(recorded 2026-10-12: ids from a plan of 2026-10-05, `…_051026`, answered 404 for the same service
day; the current feed was `…_121026`), so a `trip` fixture needs a fresh `plan` first.

**Day timetable (Phase 10 D7): 2 `stoptimes` per open, +1 per "More".** `present/DayTimetable.kt`
`TimetableLoader`: from the service day's 04:00, `n = 60`, at most 2 pages under `BudgetedTransitApi(2)`,
then "More" sends 1 with the last `nextPageCursor`. Every page repeats the first query (`time` included) +
`pageCursor`, which is in the guard's cache key, so a re-open within 30 s costs 0. Paging stops when a page
reaches past the day's end (04:00 next day), is empty, or has no cursor. The line + headsign filter and the
Saturday-night dedupe (line + minute) run on the phone. Recorded 2026-10-12 (4 calls): Be'er Sheva central
intercity (`48114`) Wed 14 Oct from 04:00 = 62 departures 04:45–06:45, page 2 continues at 06:50 with no
overlap (`stoptimes_day_beersheva_central_p1/p2`): a busy hub needs More through the day (a parent station
lists all its platforms). A quiet Omer stop (`26760`) fits a whole day in one page and spills into the next
(`stoptimes_day_omer_quiet`); on Friday from 12:00 its last bus is 15:43, the next Saturday 20:15
(`stoptimes_day_omer_friday`). Pinned in `DayTimetableTest`.

**Timetable horizon** (`DayNotice.HORIZON_DAYS = 8`): measured 2026-10-12 with 3 live `plan`
calls, Be'er Sheva → Tel Aviv 08:00 (`plan_horizon_20261111/20261210/20270110`): +30, +60 and
+90 days answer no itinerary (`n_routes_visited: 0`), while +8 days answered (`plan_pages_*`,
recorded 2026-10-05 for 13 Oct). So the MOT timetable on Transitous reaches at least 8 and under
30 days ahead; "may not reach" shows only past 8. Owner, 2026-10-12: keep 8, no extra calls.

| background job | requests | worst case per day | constraints |
|---|---|---|---|
| Night refresh (`NightRefreshWorker`, Phase 9 C2) | `plan` only | 6 (`NightRefresh.MAX_TRIPS`, `BudgetedTransitApi`) + 1 per 429/503 retry | Wi-Fi, charging, battery not low; 22:00–06:00; once per service day; never retried |
| Night boards (same `NightRefreshWorker`, Phase 10 D7) | `stoptimes` only, after the trips | 3 (`NightRefresh.MAX_STOPS`, its own `BudgetedTransitApi(3)`, one page of `n = 100` from tomorrow's 04:00 per stop of My lines) + 1 per 429/503 retry: night total ≤ 6 + 3 = 9 | same run, same constraints; also enqueued when My lines has a stop; once per service day |
| Last trip home (`LastTripReceiver`, Phase 9 C5) | `plan` only (`LastRideFinder`) | 3 (`LastRideFinder.BUDGET`, `BudgetedTransitApi`) + 1 per 429/503 retry; 0 with the setting off (default), no Home, no origin, or within 1 km of Home | one inexact alarm an evening (19:00 Sun–Thu, 12:00 Fri, 20:00 Sat) → WorkManager, network connected; not 02:00–03:59; a check with no signal retried every 15 min while the day's 3 last (spent requests stored before each try) |

## Backup geocoders: the phone's Geocoder, then Photon (search fix, owner approved 2026-10-07)
Asked only after a Transitous miss: no answer's name/street contains every typed word that is
not a number, a town the answers lie in, or "רחוב/street" (`PlaceSearch.needsBackup`), text ≥ 3
characters. Chain (`core/search/Photon.kt` `FallbackGeocoder`, wired in `TransitApp.backupGeocoder`):
**saved/recent → Transitous → Android Geocoder → Photon** (Photon only when `!Geocoder.isPresent()`
or the Geocoder errs, times out or finds nothing). Answers go below Transitous's; duplicates
(same name ≤ 150 m) dropped. Each answer shows its town.
**Typed town** (`search/Towns.kt`): the bundled table (`TownsData`, ~130 towns incl. Arab and Druze towns, Hebrew + English
names, OSM centre, radius; `python tools/gen_towns.py` from `tools/towns_seed.tsv`, 1 Photon
request per town, or a hand-checked centre in the seed) finds the town the text ends with ("טבנקין 15 רעננה", "…, Tel Aviv", "ב"ש"), or
starts with before a comma; not after a street word ("שדרות ירושלים") nor before only a number
("ירושלים 5"). It biases every request and **every source's answers outside it are dropped**
(`Towns.holds`: by the answer's own town name when the table knows it, else by radius), so a
typed town is never silently replaced; nothing left = "Not found… long-press the map".
**Never a wrong place as the answer** (owner, 2026-10-07; tightened after review 2026-10-11):
`PlaceSearch.exact` — the typed house (letter required: 12א ≠ 12/12ב; "12-14" = any in range;
"12/3", "דירה 4" = 12); every word that names the place (not the typed town, numbers, street or
kind words like בית חולים/תחנה) is a **whole word** of the answer's name/street (one letter off for
4+ letters, a ה/ב/ל/מ prefix aside, a Latin word against its Hebrew consonants via `Translit`:
"Rothschild" = רוטשילד); an address's street has no untyped word ("יהודה" ≠ בן יהודה), first
names/titles aside (חיים ויצמן); a place/stop is **named by a typed word first** ("הטכניון" ≠
"אובליסק הטכניון"); kind words only ("קניון רעננה") = a place of that kind named by it; the town
only ("רעננה") = the town itself. Never true on an empty word list (that was F1). Only exact
answers are offered as answers; the rest are under "Closest matches", with "Not found exactly"
+ a "Pick on the map" button on top; a failed search (offline) says so instead.
`Aliases` rewrites ~20 everyday names to their map name + town (איכילוב → "איכילוב תל אביב",
תל השומר hospital → שיבא, נתב"ג…) — only alone or with the alias's own town ("ברזילי ירושלים"
is a street). `QueryText.expand` first spells out acronyms (`Aliases.acronym`, below), "שד'" → שדרות, drops
רחוב/רח', and reads "ברעננה"/"בתל אביב" after the street as the town. **Timing:** Transitous's
answers show at once (`pending`) when the backup is asked; the whole backup stage (phone, then
Photon) is capped at `BACKUP_DEADLINE_MS` = 4 s, and the Photon call is cancelled with it (its own
timeouts: connect 3 s, read 4 s).
**Budget:** one chain call per search, a
search only after typing pauses 350 ms (`TypingSearch`): ≤ 1 Geocoder + ≤ 1 Photon request,
pinned in `PlaceSearchTest`. Each service under its own `GuardedGeocoder` (day cache — "nothing"
only 2 min, since the phone's Geocoder answers nothing when offline —, ≤ 2 at once, one retry on
429/503). A backup failure never fails the search. Every backup answer outside Israel's box is
dropped (the phone's Geocoder is also asked inside the box).

**Search hardening (Phase 10 D2), 0 new requests:**
- **Stale searches:** `MotisClient` (like `PhotonClient`) enqueues and cancels the OkHttp call with
  the coroutine, so new typing frees the connection and the guard's slot at once; a cancelled call
  caches nothing. `TypingSearch` numbers each search: an answer reaches the screen only while its
  number is the latest (the phone's blocking Geocoder may still return), and `cancel()` takes a
  number too. Pinned in `TypingSearchTest` (5 keys in 300 ms → 1 geocode; a slow old answer is dropped).
- **Acronyms** (`Aliases.acronym`): ת"א ר"ג פ"ת ב"ש ק"ש ק"ג ר"ל ראשל"צ נס"צ כ"ס הוד"ש ב"ב י-ם, את"א
  (Tel Aviv University), בי"ח (hospital), with `"` `״` `'` `׳` between the letters, also after ב/ל/מ/ו
  ("בת"א", "בי-ם"; the letters as they are first: ב"ב is בני ברק); with no mark
  only ראשלצ/נסצ/הודש/ביח (the others are words). `Aliases.AMBIGUOUS` (ק"א, ק"ב, ק"מ, ר"ה…) is never
  spelled out. In English text the English name ("Herzl 5, ת"א" → "…, Tel Aviv").
- **Neighbourhoods** (`Neighbourhoods`, `NeighbourhoodsData` from `tools/gen_neighbourhoods.py` and
  the hand-checked `tools/neighbourhoods_seed.tsv`, ~160): a name and no town ("רמת אביב", not "גילה 5"
  nor "רחוב רמות") biases the one geocode to its town's `Towns` centre (no coordinates of our own);
  answers in that town rank first, nothing is dropped (it may be a street name too). **In two towns**
  ("רמות", "נווה שאנן"; owner, 2026-10-12): no request while typing; one row per town from the table
  ("רמות · באר שבע", nearest the user first, `SearchAnswer.choices`), and tapping one searches
  "רמות, באר שבע" (1 geocode, that town's centre). Pinned in `NeighbourhoodsTest`. A neighbourhood
  typed with a street is "where", not part of the street name. The detail line names the town (`geocodeDetail`).
- **Match levels** (`MatchLevel`, `PlaceSearch.level`): HOUSE (the address as typed), STREET (the
  street, not the typed number), TOWN (only the town), PLACE (a place, stop, or the town typed
  alone), NONE. Only HOUSE/PLACE are `exact` (the old rule, unchanged); `closest` is ordered STREET,
  TOWN, NONE, and the row says "Street only: no number 15" / "Town only" (`SearchAnswer.note`).
  An answer named exactly as typed comes first among the exact ones ("רמות" before "רמות אשכול").
  Apartment words ("דירה 4", "קומה 2") are dropped before the request.
- **JVM corpus** (`AddressCorpusJvmTest`, every `core test`): `fixtures/corpus/corpus.tsv` (267 rows:
  the emulator corpus's queries, acronym/mark/comma/"ב"-town/apartment variants, neighbourhoods,
  English) through `PlaceSearch` with Transitous and Photon replayed from `fixtures/corpus/`. Replay
  key = the text sent, normalized, + language (variants share one recording); each was recorded with
  the bias the app sends (2026-10-12: 60 geocodes, 1 s apart). A row: query, language, level,
  expected name, `lat,lon` (150 m), known gap. **Adding a row:** append it and run the test; a new
  search text fails it and writes the `record_fixture.py` commands to `core/build/corpus-missing.sh`
  (`core/build/corpus-observed.tsv` shows what every row got). Photon (`corpus-missing-photon.tsv`)
  was blocked from the cloud session: only `טבנקין 15 רעננה` is replayed; rows the phone's Geocoder
  answers in the app are known gaps here (the emulator `AddressCorpusTest` covers that chain).

**Ways in (Phase 10 D6), ≤ 1 request per use:**
- **Stop numbers are not in Transitous's geocoder** (2026-10-12, 2 live calls, `geocode_stopcode_13907`,
  `…_stop` with `type=STOP`): "13907" (Be'er Sheva central's intercity platforms) near Be'er Sheva answers
  names containing "1390" in Iraq, Spain and Australia, nothing in Israel. So `search/StopCode` reads 3–6
  digits (or "תחנה"/"stop" + digits; never "15" or "הרצל 15") and looks the code up in the stops the phone
  already holds (`StopsStore.known()`, the map's stops tile cache, which keeps `stopCode`): **0 requests**,
  routed by one branch at the top of `PlaceSearch.search`. My lines and the departure cache store no stop
  code (`FavoriteLine` has the id and name only), so their stops count once the map has shown them. No hit:
  "Stop 47899 is not in this area yet: open the map near it". A hit opens the stop sheet (its usual
  `stoptimes`). Pinned in `StopCodeTest` and `PlaceSearchTest`.
- **Shared places** (`present/SharedPlace`): `geo:` and Google Maps / Waze links with coordinates (also
  Waze's `/ul/h<geohash>`) → the destination, **0 requests**, named by the link's label or "Pin · lat, lon";
  address text (a WhatsApp message: the line with a street word, a town or "כתובת:") → the search box,
  one geocode as if typed. Short links (`maps.app.goo.gl`, `goo.gl/maps`) are never resolved (that would
  ask Google), outside Israel's box is refused. Pinned in `SharedPlaceTest`.
- **Voice search**: the phone's own speech service (`RecognizerIntent`), 0 requests of ours.

**Android's built-in Geocoder** (`android/.../data/AndroidGeocoder.kt`). An OS API: keyless, no
account, no billing. On Play-services phones Google's address data answers it. **It is NOT the
Google Maps SDK / Places** (`dead-ends`): don't remove it under the "no Google Maps" rule.
`Locale("iw")` or English; API 33+ listener form, `runInterruptible(IO)` below; **3 s timeout**
(`TIMEOUT_MS`). Also names pins and "save my current location" first ("טבנקין 15, רעננה"), then
Transitous `reverse-geocode`, then coordinates offline — a name only when its point is within
75 m (`PinName.NEAR_M`; a field or the sea is "Pin · lat, lon"). Privacy: the typed text or the
point goes to the phone's geocoder (Google on most phones); Photon also gets the approximate area
(the bias, rounded to 0.1° ≈ 10 km) and the IP (release notes say so). Its answers carry no
credit line (Google's licence asks none of an OS API); only Photon's do. Not unit-testable (Android class): the chain is
tested with a fake in core; UI tests have no backup (`UiTestApp.backupGeocoder = null`).
- **Step 0 check (2026-10-07, scratch run 37567840566, google_apis emulators API 34 + 35):**
  `isPresent()` true. "טבנקין 15 רעננה", "טבנקין 15, רעננה" and "Tabenkin 15, Ra'anana" → 1 answer
  each, every locale: "טבנקין 15, רעננה, ישראל" (en "Tabenkin St 15, Ra'anana, Israel"), locality
  רעננה, **32.1881065, 34.860527**. Reverse there: טבנקין 15, השלום 51, טבנקין 12. Timing: cold
  first call 2.5 s, then forward 130–750 ms (typically 200–450), reverse 1–530 ms.

**Photon** (`PhotonClient`): `GET https://photon.komoot.io/api?q=…&lang=…&lat=…&lon=…&bbox=34.2,29.45,35.95,33.35&limit=5`,
GeoJSON (`features[].properties.{name,street,housenumber,city,type,osm_value}`, `geometry.coordinates`
= **[lon, lat]**) → `GeocodeMatch` (`parsePhoton`).
- **Policy:** free, keyless, no card; fair use, search-as-you-type tolerated (komoot's public
  instance). Our User-Agent; "Search © OpenStreetMap / Photon" under its answers (ODbL). Not in
  the map chip: on the largest font the longer chip wrapped and failed R7. Nominatim's public
  server forbids autocomplete → `dead-ends`.
- **Language:** the public instance knows default/en/de/fr; Hebrew sends `lang=default` (OSM's
  local name = Hebrew in Israel), English `en`.
- **Recorded 2026-10-07:** OSM has no טבנקין in Ra'anana; Photon's top answer for "טבנקין 15
  רעננה" is טבנקין יצחק 15 in **Tel Aviv**, then Kiryat Arba (the bbox includes the West Bank)
  → dropped by the typed-town rule (`photon_tabenkin_raanana`). "רגר" → שדרות יצחק רגר, Be'er
  Sheva (`photon_rager`).
- **Fixtures:** `python tools/record_fixture.py --photon <name> "/api?q=…"` (`photon_*`).
- **Swappable:** `BackupGeocoder` interface; another Photon (or self-hosted) is a base-URL change.

**Address corpus** (owner, 2026-10-07: "no address left undefined or wrong"):
`androidTest/assets/address_corpus.tsv` (~60 queries: cities and villages, abbreviations, geresh
forms, English, typos, landmarks; expected town/street/house; a known-gap column) runs through
the real chain against the LIVE services in `AddressCorpusTest`, on demand only
(`.github/workflows/address-corpus.yml`: Run, or push `corpus-run/<name>`), ~1 query/s. The
table (source, ms, first answer, pass/fail) is in the run summary. A miss not listed as a known
gap fails it.

## Open-Meteo: rain on the walks (Phase 10 D9, owner approved 2026-10-07, threshold 2026-10-13)
`core/weather/OpenMeteo.kt`: `GET https://api.open-meteo.com/v1/forecast?latitude=32.1&longitude=34.8&hourly=precipitation_probability,precipitation&timeformat=unixtime&forecast_hours=54`.
- **Terms** (open-meteo.com/en/terms, read from its website repo 2026-10-13; the site itself is blocked
  from cloud sessions, the API host is not): free, no key, non-commercial; "Less than 10'000 API calls per
  day, 5'000 per hour and 600 per minute"; data CC BY 4.0 → "Weather: Open-Meteo" under the note.
- **Budget: 1 request per Trip search**, only when the earliest option starts within 48 h
  (`GuardedWeather.HORIZON`), after the plan answered (never for errands, other tabs or an offline copy);
  cached **1 h per 0.1° cell** (`GuardedWeather.cell`, ~10 km: all it is told), ≤ 2 at once, one retry on
  429/503, 3 s call timeout; any failure → no note, nothing cached. 0 Transitous requests; the plan request
  is never changed. Pinned in `RainAdviceTest` (a second search in the same hour and cell sends 0) and
  `OpenMeteoTest`. Off: Settings → Rain on the walks (`UserSettings.rainAware`, on by default).
- **Hours:** each hourly value is for the **preceding** hour (the docs: "sum of the preceding hour",
  probability "of the preceding hour"), so `parseOpenMeteo` moves it back one hour. `unixtime` = UTC seconds:
  the clock change never shifts an hour; the note formats in `ISRAEL`.
- **Rule** (`weather/RainAdvice`, owner decision 7): an hour is rainy at ≥ 60% or ≥ 0.5 mm, and counts when
  it overlaps ≥ 5 min of walking (`WALK` legs, summed within that hour) in at least one option. The note
  names the first rainy walk of any option and the option with the least walking (`walkSec`, ties → earlier).
- **Recorded 2026-10-13** (1 look at the 16-day outlook + 3 fixtures): Tel Aviv's cell 16–17 Oct
  (`openmeteo_rain_telaviv`: 0.5 mm 09:00–12:00 on the 16th, 60–63% early on the 17th), Be'er Sheva now
  (`openmeteo_dry_beersheva`, 54 h, ≤ 3%), an error (`openmeteo_error`, HTTP 400 `{"error":true,"reason":…}`).
  The API answers the snapped grid point (32.125, 34.8125), not the cell we sent.
- **Fixtures:** `python tools/record_fixture.py --open-meteo <name> "/v1/forecast?…"` (`openmeteo_*`).
- **Swappable:** `WeatherSource` interface; another Open-Meteo (it is open source) is a base-URL change.

## Fixtures
Tests never hit the network. `python tools/record_fixture.py <name> "<url path+query>"`
saves a real response into `core/src/test/resources/fixtures/` (needs network access to
api.transitous.org). All fixtures are real recordings; record future-dated (a weekday,
not a holiday) so the plans make sense, and add a test that pins what you learned.
Inspect any response cheaply with `python tools/plan_summary.py <file.json>`.
