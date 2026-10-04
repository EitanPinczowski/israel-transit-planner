# Roadmap

Tick items in the same commit that completes them. `session_start.py` reports the first
phase with open items. Full design: the approved plan (summarised in CLAUDE.md).

## Phase 0 — setup
- [x] Public repo, MIT licence, attribution
- [x] Claude tooling: CLAUDE.md, 8 skills, guard / session-start / post-edit hooks, read deny-list
- [x] `core/` engine: MOTIS client, cache/concurrency/retry guard, per-search budget
- [x] Pareto engine + better start / drop-off / pick-up, with offline unit tests
- [x] Android shell: MapLibre + OpenFreeMap map, location, Transitous attribution
- [x] CI: core tests, docs check, debug APK artifact
- [x] Owner: allow `api.transitous.org` + `tiles.openfreemap.org` in the cloud environment's network settings
- [x] Spike: record real Transitous answers for Israel (plan, CAR_DROPOFF pre + post, one-to-many CAR, map/stops, stoptimes) and replace `plan_synthetic.json`
- [x] Spike, real-time part: record a weekday `stoptimes` for "now" and check `realTime: true` (the spike ran on Shabbat)
      — closed as **not available** (owner, 2026-10-04): Transitous has no real-time for Israeli
      MOT lines (0 `realTime` on Sat 2026-10-03 23:20 and Sun 2026-10-04 10:16; only busofash
      carries GTFS-RT).
- [x] Owner: send `docs/transitous-contact.md` to Transitous in their Matrix channel

## Phase 1 — MVP
- [x] Search box (geocode, Hebrew + English), origin = my location, long-press = destination
- [x] Plan screen: itineraries list + legs drawn on the map
- [x] Arrive-by, preferences (max walk, max transfers, modes, walk speed) in DataStore
- [x] Stops layer from zoom 15, stop sheet with next departures (real-time delay when present)
- [x] Saved places + saved trips (DataStore JSON), one-tap re-plan
- [x] Dark map style, Hebrew RTL strings
- [x] Settings: traffic factor
- [x] Verify on a real phone against live Transitous (owner's Pixel, v0.1.0–v0.2.0)

## Phase 2 — Better start
- [x] "Better start" tab with drive-limit slider (5–30 min), results on the map
- [x] Falls back to `CAR` when the server refuses `CAR_DROPOFF` (budget 5, tested)
- [x] "Send to driver": share sheet with Waze + Google Maps links to the drop-off stop

## Phase 3 — Let me off on the way
- [x] Drop-off tab (A = from, B = driver's destination, C = mine), detour slider, Pareto list
      with detour + arrival per option and both baselines (ride to the end / transit from the start)
- [x] Send point to Waze / Google Maps via share sheet (`NavLinks`)
- [x] Draw the car part of a drop-off option on the map (route up to the stop, purple)

## Phase 4 — Best pick-up point
- [x] "Pick me up" tab: From = me, "Driver at" = home; one-way drive slider (5–30, default 15)
- [x] Options show pick-up stop + time, driver's leave time and round trip, home arrival
      (traffic-adjusted), savings vs transit all the way; only offered if ≥ 5 min or a transfer saved
- [x] Send to driver: stop, pick-up time, leave time, Waze + Google Maps links

## Phase 5 — Real-time + offline
- [x] Real-time badges on legs and departures (+N min / ●), "Updated HH:MM" + ↻
- [x] Trip tab refreshes every 2 min while the app is in front (quiet, keeps selection)
- [x] Leave reminder: exact alarms, real-time re-check 15 min before, boot re-arm,
      "trip changed" notification when the bus is gone
- [x] Offline: last 10 Trip results cached on disk with an "offline" banner; saved places
      and trips are local; offline map download for the current area (z10–14, ≤ ~55 km)
- [x] Stops layer disk cache: 0.01° tiles, 7-day TTL, 400-tile LRU, one request per pan
      for the missing tiles only (`StopsTileCache`, `data/StopsStore`)

## Phase 6 — On the trip
- [x] "Get off next stop" alert: foreground GPS service only while a trip is ridden, buzzes once
      per leg (passing the stop before yours, or 400 m out), stops itself at the end
- [x] Trip history + stats (trips, this week, hours on transit, minutes saved by the car
      features, top destination); local only, capped at 500
- [x] Fare estimate ("≈ ₪8" on every option): straight-line distance bands, 90-min yellow
      transfer, train column, daily cap, Regular / 50% / Free. Table copied from HopOn's
      Rav-Pass price list (checked 2026-08-04, `fare/Fares.kt`); links to bus.gov.il
- [x] Last trip / Shabbat warning: "Last trip today 15:29 · next only Sat 20:14" from the
      timetable itself (no holiday calendar), automatic on evenings/Fri/Sat, "last trip back"
      on tap; ≤ 3 requests (`plan/LastRide.kt`)
- [x] Errands: up to 3 stops on the way with a stay each (0/15/30/60 min), chained so each leg
      leaves after the previous arrival + stay; one plan per leg (`plan/CarAndChain.kt`)
- [x] "🚗 By car?": the same trip by car with the traffic factor (1 request); no taxi price —
      the official tariff could not be confirmed from a reachable source, so it links to MOT's calculator
- [x] Spoken get-off alert (TextToSpeech, navigation audio, ducks music; Settings toggle, on)
- [x] App-icon shortcuts: My lines + 3 saved places (routine on now first)
- [x] My lines offline: last saved board per stop, upcoming departures only
- [ ] ~~Service alerts~~ — not possible free: Transitous has no MOT GTFS-RT (only busofash);
      MOT SIRI needs a registered key
- [x] "On the bus": live stops left, delay and arrival in the ride notification and the app
- [x] My lines: ☆ a line at a stop on its departure board; next 3 departures in one tap
- [x] Way back after 1 / 2 / 3 h: the return of the selected option, same settings
- [x] Routines on saved places: "University: Sun–Thu 07:00–10:00" opens the app on that
      trip with the time to leave; never over a destination the user chose (`Routines.active`)
- [x] Delay alert while waiting: with a leave reminder, re-checks 30/20/12/6/2 min before and
      notifies when the leave time moves ≥ 3 min, earlier or later, from what the user was told
- [x] Sort chips on the Trip tab: Fastest · Fewest transfers · Least walking (no new requests)
- [x] Walking directions for the selected trip: street by street, turns computed from the
      step geometry (Transitous sends only CONTINUE/STAIRS), stairs called out
- [x] Accessible routes setting: `pedestrianProfile=WHEELCHAIR` (step-free walking parts;
      vehicle accessibility is not in the data)

## Phase 7 — Release
- [x] Tag-triggered release workflow: signed APK (key from repo secrets), `apksigner verify`,
      GitHub Release with notes from `docs/releases/`
- [x] Version from the tag (versionCode = major·10000 + minor·100 + patch); CI builds release unsigned
- [x] Launcher icon; in-app "Update available" banner (GitHub Releases, once a day)
- [x] Install guide for friends (`docs/install.md`, Hebrew + English)
- [x] Owner: create the key, add the 4 secrets — v0.1.0 released 2026-09-28

## Phase 8 — parallel features
One cloud session per package, each on its own branch and PR; rules in the `parallel-work`
skill, the brief each session got in `docs/agents/phase8.md`. Wave 0 (A1, A2) merges before
Wave 1 (B1–B4) starts. Tick only your own package's lines.

### A1 — API spike + plumbing (`claude/p8-api`, core only, ~8 live requests to record)
- [x] Weekday `stoptimes` for "now" recorded; `realTime: true` checked (closes the Phase 0 item)
      — Transitous has no real-time for Israeli MOT lines (0 `realTime` on Sat 2026-10-03 23:20
      and Sun 2026-10-04 10:16; only busofash carries GTFS-RT). Closed as not available.
- [x] `trip(tripId)` and `mapTrips(bbox, start, end, zoom)` in `TransitApi`, both guards, fakes,
      fixtures (`trip` for a bus and a train, `map/trips` for a small box)
- [x] `Alert` model; `alerts` / `cancelled` on legs, places and stop times; does Israel send any?
      No: zero `alerts` in every answer of 2026-10-03.
- [x] `CAR_PARKING` verdict (Meitar → Tel Aviv, recorded) written here, in `special-features`,
      and in `dead-ends` if it fails

### A2 — mechanical UI split (`claude/p8-ui-split`, no behaviour change)
- [x] `MainScreen.kt` → `ui/screens/` (TripPanel, BetterStartPanel, DropOffPanel, PickUpPanel,
      StopSheet, SettingsDialog, Common); `MainScreen.kt` keeps the scaffold only

### B1 — Trip details + live bus (`claude/p8-trip-details`, 1 request per tap + 1 per 30 s)
- [ ] Tap a transit leg: every stop with scheduled + live time, cancelled stops struck through
- [ ] The vehicle on the map while the sheet is open (`map/trips`, leg bbox, app in front only)
- [ ] ⚠ service alerts on leg chips, the trip sheet and stop-sheet departures — **only when an
      answer carries them**; Israel sent none on 2026-10-03 (A1), so no empty alert UI

### B2 — Park & Ride (`claude/p8-park-ride`, `ParkRidePlanner.BUDGET = 5`)
> **A1 `CAR_PARKING` verdict (2026-10-03): unusable — use bundled `RailStations` + `CAR`.**
> Meitar → Tel Aviv HaHagana, Mon 08:00, 20-min cap: all 5 answers park at unnamed OSM lots
> near a bus stop (2–13′ drive, then up to 11′ walk), board a bus, and never park at a
> station; best arrives 10:06. `CAR` drives 15′ to Be'er Sheva North and reaches Tel Aviv
> Center (past HaHagana) at 09:46. Fixture `plan_car_parking_meitar`, test `ApiPhase8Test`.
- [ ] Drive your own car to a station with parking, continue by train; Pareto front like the
      other car features, traffic-adjusted (`CAR_PARKING` or bundled `RailStations`, per A1)
- [ ] "Way back to my car": plans back to the same station, on tap only (1 request)
- [ ] Golden trip + `special-features` section + 4th row in CLAUDE.md's feature table

### B3 — Calendar → arrive by (`claude/p8-calendar`, 1 geocode, cached a day)
- [ ] "From my calendar": next events with a location (on the phone, `READ_CALENDAR` asked on
      tap), "arrive by 09:50 at …" with a buffer setting (default 10 min)
- [ ] Only the location text leaves the phone (to the geocoder); said in the rationale + docs

### B4 — UI tests + crash log (`claude/p8-qa`, no requests)
- [ ] Roborazzi/Robolectric screenshots of every `ui/screens/*` panel, Hebrew + English, in CI
- [ ] Crash log: last 5 crashes in `filesDir`, "Share crash log" in Settings, nothing automatic

### Close-out (planner)
- [ ] `docs/releases/v0.7.0.md` assembled from `docs/releases/next/*`; fragments removed
- [ ] Owner: every Phase 8 feature checked on the Pixel against live Transitous
