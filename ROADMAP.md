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
- [ ] Pick-up tab, driver leave-time, send point to Waze

## Phase 5 — Real-time + offline
- [ ] Real-time badges on legs and departures
- [ ] Leave reminder (alarm + real-time re-check 15 min before)
- [ ] Offline: cached plans/stops/places, offline map region download

## Phase 6 — On the trip
- [ ] "Get off next stop" alert (foreground GPS only during a trip)
- [ ] Trip history + stats (incl. minutes saved by the special features)
- [ ] Fare estimate — only after checking against the official Rav-Kav calculator

## Phase 7 — Release
- [ ] Signed APK on GitHub Releases for friends and family
