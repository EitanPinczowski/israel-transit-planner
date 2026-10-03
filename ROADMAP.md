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
- [ ] Spike, real-time part: record a weekday `stoptimes` for "now" and check `realTime: true` (the spike ran on Shabbat)
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
