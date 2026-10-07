# Israel Transit Planner — project context

Android trip planner for Israeli public transport (bus, Israel Railways, light rail) with
four car + transit features. For the owner, friends and family; shipped as an APK.

**Keep this file under 120 lines.** It is loaded into every session. Anything a session
only sometimes needs belongs in a skill (table below). No status, no history, no dated
measurements here — live state is printed by `.claude/hooks/session_start.py`, and the
phase checklist is `ROADMAP.md`.

**Times for the owner are Israel time** (Asia/Jerusalem: UTC+3 in summer, UTC+2 in winter).
Convert CI and tool timestamps (UTC) before writing them in messages, PRs or docs.

## Hard rules (do not silently reverse)

- **Free, no card, no API key in the app — ever.** No Google Maps SDK / Directions / Places,
  no Moovit, no paid tier "just for testing". A proposal that needs a card is rejected.
  CI-only test logins are allowed when keyless and on a free plan (Test Lab Spark via
  Workload Identity, owner 2026-10-03).
- **No server of our own.** The phone talks directly to free public services:
  - **Transitous** (`https://api.transitous.org`, runs MOTIS) — routing, geocoding, stops,
    departures. Israel MOT GTFS is loaded there; real-time only for busofash (Tel Aviv
    night buses), so MOT times are scheduled.
  - **OpenFreeMap** (`tiles.openfreemap.org`) — map tiles for MapLibre.
  - **Android's built-in Geocoder** (`android.location.Geocoder`; owner approved 2026-10-07) —
    keyless, part of the OS; on Play-services phones Google answers it. NOT the Maps SDK/Places
    (no key, no billing). Only on a Transitous street miss and for pin names; 3 s timeout.
  - **Photon** (`photon.komoot.io`, OSM search; owner approved 2026-10-07) — only when the phone
    has no Geocoder or it can't answer: ≤ 1 request per typing pause, `GuardedGeocoder` (day
    cache, ≤ 2 at once, one retry), our User-Agent, Israel bbox, credited under its answers.
    Never Nominatim's public server (its policy forbids autocomplete). See `transitous-api`.
- **Transitous usage policy is a project rule**: repo stays **public + open source**,
  non-commercial, every request sends `MotisClient.USER_AGENT` (repo URL = contact),
  a visible link to `https://transitous.org/sources/`, and traffic stays light.
  All traffic goes through `GuardedTransitApi` (cache, ≤2 concurrent, one retry on
  429/503); every special feature runs under a `BudgetedTransitApi` with a tested cap.
- **Fallback if Transitous says no or disappears:** self-host MOTIS on the owner's PC
  behind a free Cloudflare Tunnel. That must stay a base-URL change — never call MOTIS
  except through `TransitApi`.
- **Unit tests never touch the network.** Fixtures in `core/src/test/resources/fixtures/`.

## Architecture

```
core/     plain Kotlin/JVM — builds and tests WITHOUT the Android SDK
  api/        TransitApi · MotisClient (HTTP) · GuardedTransitApi · BudgetedTransitApi · Models
  geo/        LatLon, haversine, polyline decode (MOTIS precision 6), corridor bbox, RailStations (generated)
  features/   Pareto engine + BetterStart · DropOff · PickUp · ParkRide, TrafficProfile
  plan/       TripPlanner (A→B), LastRideFinder, ChainPlanner (errands), CarCompare, caches
  present/    summaries, leg chips, colours, departure rows — all UI text logic, tested
  user/       UserSettings → MOTIS Preferences, saved places/trips, UserJson codec
  remind/     Reminder + ReminderLogic: leave time, re-check matching (tested)
  ride/       RideTracker: "get off at the next stop" from GPS fixes (tested)
  history/    TripRecord + History.stats (tested)
  fare/       FareTable (Rav-Kav bands, agorot) + FareEstimator: "≈ ₪8" per itinerary (tested)
  search/     PlaceSearch (Transitous → Photon on a miss), TypingSearch, LocalFirst + Recents (tested)
android/  the app (Compose + MapLibre); includeBuild("../core"). Needs the SDK → CI builds it.
  ui/MainViewModel (state) · ui/MainScreen (scaffold) · ui/screens/* (Compose panels) · ui/Theme (palette) · ui/MapController (layers) · data/UserStore
  remind/ (alarms, receivers, notifications) · ride/RideService · ui/OfflineMap · data/*Store
tools/    check_docs.py · check_strings.py · plan_summary.py · record_fixture.py · gen_rail_stations.py · gen_towns.py · gen_icons.py · pull_goldens.py · ui_*
```

### The four special features (all return a Pareto front: driver cost × arrival × transfers)

| Feature | Idea | Requests |
|---|---|---|
| Better start | `preTransitModes=CAR` (Transitous's `CAR_DROPOFF` barely drives), cap ladder + walk baseline | ≤ 5 |
| Let me off on the way (A→B, reach C) | car route → stations spread along it (bundled list on long drives) → 2 one-to-many → ≤ 4 plans + 2 baselines | ≤ 10 (`DropOffPlanner.BUDGET`) |
| Best pick-up point | `postTransitModes=CAR`, cap ladder + transit-only baseline (`PickUpPlanner.BUDGET`) | ≤ 4 |
| Park & ride (own car → train) | bundled `RailStations` (not `CAR_PARKING`) → 1 one-to-many CAR → plans from ≤ 3 station ids + baseline | ≤ 5 (`ParkRidePlanner.BUDGET`) |

Car times from free routers assume empty roads → `TrafficProfile` (×1.3 Sun–Thu peaks).

## Commands

- Core tests: `./gradlew -p core test -q` — **never pipe it** (a pipe hides the exit code;
  the guard hook blocks it). Only failures print.
- Android APK: `./gradlew -p android :app:assembleDebug` (needs the SDK; CI does it and
  uploads `app-debug` as an artifact).
- Docs integrity: `python tools/check_docs.py` (CI runs it; it also runs `check_strings.py`).

## Where the rest lives

`tools/check_docs.py` fails CI if a skill is missing from this table or the table names a
skill that does not exist — a note nobody can find is a note nobody has.

| skill | load when |
|---|---|
| `android-build` | building, running, or debugging the app; Gradle or CI failures |
| `transitous-api` | any call to MOTIS/Transitous, new endpoints, recording fixtures |
| `special-features` | editing BetterStart / DropOff / PickUp / ParkRide / Pareto / traffic |
| `add-feature` | adding any user-visible feature end to end |
| `i18n-rtl` | any UI text, layout, colour or icon (Hebrew RTL + English, dark mode, insets) |
| `golden-trips` | checking that routing results are still sane |
| `ui-testing` | UI/UX on many phones: emulator profiles, layout audit, journeys, ci-screens, Test Lab |
| `reminders-offline` | leave reminder, get-off alert, history, notifications, offline cache and map |
| `release-apk` | shipping a signed APK to friends and family |
| `dead-ends` | **before proposing an approach** — what was rejected and why |
| `parallel-work` | working as one of several agents (Phase 8+ packages, `docs/agents/`) |
