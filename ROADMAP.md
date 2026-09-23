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
- [ ] Owner: send `docs/transitous-contact.md` to Transitous

## Phase 1 — MVP
- [ ] Search box (geocode, Hebrew + English), origin = my location / long-press
- [ ] Plan screen: itineraries list + legs drawn on the map
- [ ] Arrive-by, preferences (max walk, max transfers, modes) in DataStore
- [ ] Stops layer from zoom ~15, stop sheet with next departures
- [ ] Saved places + saved trips (Room), one-tap re-plan
- [ ] Dark map style, Hebrew RTL pass
- [ ] Settings: traffic factor

## Phase 2 — Better start
- [ ] "Better start" tab with drive-limit slider (5–30 min), results on the map

## Phase 3 — Let me off on the way
- [ ] Drop-off tab (A→B, C), Pareto list, detour + arrival per option
- [ ] Send point to Waze / Google Maps via share sheet

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
