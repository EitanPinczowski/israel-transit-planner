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
- [ ] Owner: allow `api.transitous.org` + `tiles.openfreemap.org` in the cloud environment's network settings
- [ ] Spike: record real Transitous answers for Israel (plan, CAR_DROPOFF pre + post, one-to-many CAR, map/stops, stoptimes, real-time) and replace `plan_synthetic.json`
- [ ] Owner: send `docs/transitous-contact.md` to Transitous in their Matrix channel

## Phase 1 — MVP
- [x] Search box (geocode, Hebrew + English), origin = my location, long-press = destination
- [x] Plan screen: itineraries list + legs drawn on the map
- [x] Arrive-by, preferences (max walk, max transfers, modes, walk speed) in DataStore
- [x] Stops layer from zoom 15, stop sheet with next departures (real-time delay when present)
- [x] Saved places + saved trips (DataStore JSON), one-tap re-plan
- [x] Dark map style, Hebrew RTL strings
- [x] Settings: traffic factor
- [ ] Verify on a real phone against live Transitous (blocked here: network policy)

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
- [ ] Optional: stops layer disk cache (today: in memory for a day)

## Phase 6 — On the trip
- [ ] "Get off next stop" alert (foreground GPS only during a trip)
- [ ] Trip history + stats (incl. minutes saved by the special features)
- [ ] Fare estimate — only after checking against the official Rav-Kav calculator

## Phase 7 — Release
- [ ] Signed APK on GitHub Releases for friends and family
