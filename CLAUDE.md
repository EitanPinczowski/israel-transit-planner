# Israel Transit Planner — project context

Android trip planner for Israeli public transport (bus, Israel Railways, light rail) with
three car + transit features. For the owner, friends and family; shipped as an APK.

**Keep this file under 120 lines.** It is loaded into every session. Anything a session
only sometimes needs belongs in a skill (table below). No status, no history, no dated
measurements here — live state is printed by `.claude/hooks/session_start.py`, and the
phase checklist is `ROADMAP.md`.

## Hard rules (do not silently reverse)

- **Free, no card, no API key — ever.** No Google Maps SDK / Directions / Places, no
  Moovit, no paid tier "just for testing". A proposal that needs a card is rejected.
- **No server of our own.** The phone talks directly to free public services:
  - **Transitous** (`https://api.transitous.org`, runs MOTIS) — routing, geocoding, stops,
    departures, real-time. Israel MOT GTFS + a GTFS-RT feed are already loaded there.
  - **OpenFreeMap** (`tiles.openfreemap.org`) — map tiles for MapLibre.
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
  geo/        LatLon, haversine, polyline decode (MOTIS precision 6), corridor bbox
  features/   Pareto engine + BetterStart · DropOff · PickUp, TrafficProfile
  plan/       TripPlanner — the ordinary A→B search (now / depart at / arrive by)
  present/    summaries, leg chips, colours, departure rows — all UI text logic, tested
  user/       UserSettings → MOTIS Preferences, saved places/trips, UserJson codec
  remind/     Reminder + ReminderLogic: leave time, re-check matching (tested)
android/  the app (Compose + MapLibre); includeBuild("../core"). Needs the SDK → CI builds it.
  ui/MainViewModel (state) · ui/MainScreen (Compose) · ui/MapController (layers) · data/UserStore
  remind/ (alarms, receivers, notifications) · ui/OfflineMap · data/PlanCacheStore
tools/    check_docs.py · plan_summary.py · record_fixture.py
```

### The three special features (all return a Pareto front: driver cost × arrival × transfers)

| Feature | Idea | Requests |
|---|---|---|
| Better start | `preTransitModes=CAR_DROPOFF` (falls back to `CAR` on HTTP 400), cap ladder + walk baseline | ≤ 5 |
| Let me off on the way (A→B, reach C) | car route → corridor stops → 2 one-to-many → plan per candidate + 2 baselines | ≤ 10 (`DropOffPlanner.BUDGET`) |
| Best pick-up point | `postTransitModes=CAR`, cap ladder + transit-only baseline (`PickUpPlanner.BUDGET`) | ≤ 4 |

Car times from free routers assume empty roads → `TrafficProfile` (×1.3 Sun–Thu peaks).

## Commands

- Core tests: `./gradlew -p core test -q` — **never pipe it** (a pipe hides the exit code;
  the guard hook blocks it). Only failures print.
- Android APK: `./gradlew -p android :app:assembleDebug` (needs the SDK; CI does it and
  uploads `app-debug` as an artifact).
- Docs integrity: `python tools/check_docs.py` (CI runs it).

## Where the rest lives

`tools/check_docs.py` fails CI if a skill is missing from this table or the table names a
skill that does not exist — a note nobody can find is a note nobody has.

| skill | load when |
|---|---|
| `android-build` | building, running, or debugging the app; Gradle or CI failures |
| `transitous-api` | any call to MOTIS/Transitous, new endpoints, recording fixtures |
| `special-features` | editing BetterStart / DropOff / PickUp / Pareto / traffic |
| `add-feature` | adding any user-visible feature end to end |
| `i18n-rtl` | any UI text or layout (Hebrew RTL + English) |
| `golden-trips` | checking that routing results are still sane |
| `reminders-offline` | the "time to leave" reminder, alarms, notifications, offline cache and map |
| `release-apk` | shipping a signed APK to friends and family |
| `dead-ends` | **before proposing an approach** — what was rejected and why |
