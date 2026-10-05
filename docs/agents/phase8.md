# Phase 8: the brief each agent session gets

The planner session starts one cloud session per package. The prompt it sends is the
**Common preamble** followed by that package's brief, word for word as written below. If you
re-run a package, use the same text. When the plan changes, update this file in the same PR.

Order:
- **Wave 0:** A1 and A2, in parallel.
- **Wave 1:** B1–B4, in parallel. Wave 1 starts only after A1 and A2 are merged into `main`.

The owner merges every PR; agents never merge.

---

## Common preamble (sent first to every session)

> You are one of several agents working in parallel on Israel Transit Planner, each on its own
> branch and PR.
>
> Before writing any code:
> 1. Read `CLAUDE.md`.
> 2. Load the `parallel-work` skill: file ownership, conflict rules, definition of done.
> 3. Load `dead-ends`.
> 4. Load the skills your package names.
>
> Your package is the `###` subsection of `ROADMAP.md` Phase 8 named below. Tick only those
> lines.
>
> Work on the branch given. Merge `origin/main` before opening the PR (never rebase). Open a PR
> to `main` and drive it to green CI. **Do not merge it.**
>
> - **Release notes:** write a fragment at `docs/releases/next/<package>.md`, in English and
>   Hebrew.
> - **Blocked?** If you are blocked on another package or need an owner decision, write it in
>   the PR body and stop. Don't work around it.
> - **Transitous traffic:** keep live calls to the few needed to record fixtures.

---

## A1: API spike + plumbing
**Branch:** `claude/p8-api`
**Skills:** `transitous-api`, `special-features`

> Core only (`core/`). Nothing in `android/` changes.
>
> **Timing:** run this when Israeli buses are running (a weekday, or Saturday after about
> 20:00 Israel time), so real-time data exists.
>
> **1. Record real fixtures** with `python tools/record_fixture.py` (about 8 requests in total):
> - `stoptimes` for "now" at a busy stop (Be'er Sheva Central or Tel Aviv Savidor). Check for
>   `realTime: true`. This closes the open Phase 0 spike item; tick it.
> - `/api/v6/trip?tripId=…` for one bus and one Israel Railways train. Take the trip ids from a
>   fresh `plan`.
> - `/api/v6/map/trips` for a small box around one of those trips. Pass `zoom`, `min`, `max`,
>   `startTime` and `endTime`, about 1 minute apart.
> - `plan` from Meitar to Tel Aviv HaShagana with `preTransitModes=CAR_PARKING` and a
>   20-minute `maxPreTransitTime`. Look at where it parks and whether that is a station with
>   parking. Compare it with the existing `CAR` fixture.
> - Check whether any answer carries `alerts` (on legs, places or stop times).
>
> **2. Plumbing:**
> - `TransitApi.trip(tripId)` returns an `Itinerary`.
> - `TransitApi.mapTrips(bbox, start, end, zoom)` returns segments with trip id, route name,
>   from/to stop with times, real-time flag and polyline.
> - `MotisClient` implements both.
> - In `Guards.kt`:
>   - `GuardedTransitApi` caches `trip` for 30 s and `mapTrips` for 20 s;
>   - `BudgetedTransitApi` counts both.
> - `FakeTransitApi` in `core/src/test/.../Fakes.kt` supports both.
> - Add an `Alert` model (header, description, cause, effect, validity), plus `alerts` and
>   `cancelled` fields on `Leg`, `Place` and `StopTime`. Use defaults so existing fixtures still
>   parse.
>
> **3. Tests:** a new `ApiPhase8Test.kt` that parses every new fixture and pins what you learned.
>
> **4. Docs:**
> - Add `trip` and `mapTrips` to the endpoint table in the `transitous-api` skill.
> - Add the findings, with dates: real-time coverage, the trip and train shape, whether Israel
>   sends alerts.
> - Write the **`CAR_PARKING` verdict** in three places:
>   - under ROADMAP B2;
>   - in the `special-features` skill;
>   - in `dead-ends` if it is unusable (with the reason).
>
>   B2 picks its approach from this verdict.

## A2: mechanical UI split
**Branch:** `claude/p8-ui-split`
**Skills:** `android-build`, `i18n-rtl`, `add-feature`

> Split `android/app/src/main/java/il/transit/planner/ui/MainScreen.kt` (about 1,200 lines)
> into files under `ui/screens/`:
>
> | file | contents |
> |---|---|
> | `TripPanel.kt` | search card, mode row, time row, suggestions, saved chips, results panel, itinerary card, sort chips, walking directions, fare, last ride, reminder, ride and routine chips |
> | `BetterStartPanel.kt` | better start list and cards |
> | `DropOffPanel.kt` | drop-off list and cards |
> | `PickUpPanel.kt` | pick-up list and cards |
> | `StopSheet.kt` | stop sheet and departure rows |
> | `SettingsDialog.kt` | settings, saved place rows, routine dialog, history |
> | `Common.kt` | leg chip, minutes slider, place row, `parseColor`, shared helpers |
>
> `MainScreen.kt` keeps only the `MainScreen` scaffold and the update banner.
>
> **This is a pure move:**
> - no logic changes and no string changes;
> - keep the function names;
> - change `private` to `internal` only where a function is now used from another file.
>
> `MainViewModel.kt` is not touched. CI's `assembleDebug` and `assembleRelease` are the check,
> because a cloud session has no Android SDK. Say "pure move, no behaviour change" in the PR,
> with a table showing where each function went.
>
> Then update the `add-feature` skill: a new screen is a new file under `ui/screens/`, with its
> own `strings_<feature>.xml` in both `values/` and `values-iw/`, and its own ViewModel.
> Tick ROADMAP A2.

## B1: Trip details + live bus + alerts
**Branch:** `claude/p8-trip-details`
**Skills:** `transitous-api`, `i18n-rtl`, `add-feature`
**Needs:** A1 merged

> **What the user gets:**
> - Tap a bus or train leg on the selected itinerary. A sheet opens with every stop of that
>   vehicle:
>   - scheduled time, plus the live time when there is a delay;
>   - the boarding and alighting stops highlighted;
>   - cancelled or skipped stops struck through;
>   - the next stop marked.
> - ⚠ alerts (from A1's `Alert` model) show on:
>   - the sheet;
>   - leg chips;
>   - stop-sheet departures.
> - While the sheet is open and the app is in front, the vehicle's estimated position is drawn
>   on the map. It comes from `mapTrips` over the leg's bounding box every 30 s, matched by trip
>   id, and is interpolated along the segment polyline between refreshes.
>
> **What the data really has (A1, 2026-10-03, and the `transitous-api` skill):** Transitous
> carries GTFS-RT for Israel only for `busofash` (Tel Aviv night buses). MOT lines have no
> real-time and no alerts: A1 saw 0 `realTime: true` and 0 `alerts` in every answer. So:
> - the "live time" and ⚠ alerts appear **only when an answer carries them**. Build them, test
>   them on hand-built core models, but never show an empty alert area or a "live" label;
> - the vehicle position is the **timetable** position (`realTime: false` from `mapTrips`).
>   Label it "scheduled position" unless that segment's `realTime` is true;
> - say this in the release fragment, so friends don't expect live tracking on MOT lines.
>
> **Requests:**
> - `trip`: 1 per tap, cached 30 s.
> - `mapTrips`: 1 per 30 s, and only in that state.
>
> Pin both in a test with a fake clock.
>
> **Where the code goes:**
> - core: `present/TripStops.kt` (rows, delays, next stop, interpolated position), with tests.
> - android:
>   - `ui/screens/TripDetailsSheet.kt`;
>   - `ui/TripDetailsViewModel.kt`;
>   - a vehicle layer in `ui/MapController.kt`;
>   - `strings_trip_details.xml` in both languages.
>
> **Docs:**
> - the `transitous-api` skill: how the app uses `trip` and `mapTrips`;
> - a release fragment;
> - tick ROADMAP B1.

## B2: Park & Ride
**Branch:** `claude/p8-park-ride`
**Skills:** `special-features`, `transitous-api`, `golden-trips`, `i18n-rtl`
**Needs:** A1 merged (#20). A1's verdict: `CAR_PARKING` is unusable for Israel. It parks at
unnamed OSM lots by bus stops, never at stations. See `dead-ends`.

> A fourth car + transit feature: drive your own car to a train station with parking, park, and
> continue by train.
>
> **Engine:** `core/features/ParkRide.kt`, built like the other three. It returns a
> `paretoFront()` over:
> - drive minutes, traffic-adjusted with `TrafficProfile`;
> - arrival time;
> - transfers.
>
> **Approach (fixed by A1's verdict, don't use `CAR_PARKING`):**
> 1. Take the bundled `RailStations` within the drive limit, using a straight-line
>    pre-filter.
> 2. Make one `oneToMany` CAR call from the origin.
> 3. Run a `plan` from the best ≤ 3 stations (`fromPlace` = station stop id).
> 4. Add a transit-only baseline.
>
> **Budget:** `ParkRidePlanner.BUDGET = 5`, pinned by a test. Options must beat the baseline by
> 5 min or save a transfer, same as better start.
>
> **"Way back to my car":** a separate action, run on tap only, costing 1 request. It plans
> from the destination back to the *same* station. Reuse the existing "Way back after
> 1 / 2 / 3 h" code (PR #14: `MainViewModel.wayBack` and `WayBackRow` in the Trip panel);
> only the destination changes, to the parked station. Don't add a second way-back mechanism.
> Show "drive home from there" as a traffic-adjusted estimate taken from the outbound drive. No extra request.
>
> **App:**
> - `AppMode.PARK_RIDE`;
> - a drive-limit slider (5–40 min, default 20);
> - `ui/screens/ParkRidePanel.kt`;
> - `strings_park_ride.xml` in both languages;
> - the car part drawn the way drop-off draws it;
> - "Navigate to the station" through the existing `NavLinks`.
>
> **Docs:**
> - a Park & Ride section in the `special-features` skill;
> - a 4th row in CLAUDE.md's feature table;
> - a golden trip (Meitar or Omer → Tel Aviv at a Sunday 07:30 peak) in `golden-trips`;
> - a release fragment;
> - tick ROADMAP B2.

## B3: Calendar → arrive by
**Branch:** `claude/p8-calendar`
**Skills:** `add-feature`, `i18n-rtl`, `transitous-api`
**Needs:** A2 merged

> A "From my calendar" chip in the search card.
>
> **When it is tapped:**
> 1. The app asks for `READ_CALENDAR`, with a rationale first. Never at launch.
> 2. It reads the next events in the coming 24 h that have a location, through
>    `CalendarContract.Instances`, on the phone.
> 3. It lists them as "09:50 · Dentist · Herzl 12, Be'er Sheva".
> 4. Picking one geocodes the location (1 request, cached a day; coordinates in the location
>    skip the geocode). It then fills the destination and sets arrive-by = event start − buffer.
>
> **Buffer:** a new setting, default 10 min, stored in `UserSettings` with a JSON round-trip
> test.
>
> **Core:** `plan/CalendarSuggest.kt` (pure):
> - picks and orders events;
> - skips all-day events and events already started;
> - parses `lat,lon` locations;
> - works out arrive-by.
>
> Tests in `CalendarSuggestTest.kt`.
>
> **Android:**
> - `data/CalendarSource.kt`;
> - the permission in the manifest;
> - `strings_calendar.xml` in both languages.
>
> **Privacy, written in the rationale, the skill and the release note:** only the location text
> goes to Transitous's geocoder, the same as typing it. Titles never leave the phone.
>
> **Docs:**
> - a "permissions" note in the `add-feature` skill (ask on tap, rationale, a refusal still
>   leaves the feature usable by typing);
> - a release fragment;
> - tick ROADMAP B3.

## B4: UI tests + crash log
**Branch:** `claude/p8-qa`
**Skills:** `android-build`, `i18n-rtl`
**Needs:** A2 merged, and the design pass PR #18 merged (it adds Paparazzi screenshots and a
`screenshots` CI job)

> **1. Screenshot tests: extend #18's Paparazzi setup; don't add Roborazzi.** #18 renders the
> whole `MainScreen` from a plain `UiState` through its `MainActions` interface, in 7 states on
> 7 devices. B4 adds:
> - per-panel shots for every `ui/screens/*` file that #18's states don't already show
>   (better start, drop-off, pick-up, the stop sheet, settings, history), in Hebrew (RTL) and
>   English;
> - states built from the real fixtures in `core/src/test/resources/fixtures/`, or from
>   hand-built core models;
> - a regression check: the `screenshots` job fails when an image changes and the PR has not
>   re-recorded it. Use Paparazzi's verify task, and keep #18's artifact upload and
>   `screenshots` branch publishing.
>
> **2. Crash log:**
> - core: `diag/CrashLog.kt` keeps the last 5 entries and formats each with time, app version,
>   Android version and the stack trace. Tested.
> - android: `TransitApp` installs an uncaught-exception handler that appends to a file in
>   `filesDir`, then hands over to the previous handler.
> - Settings gets "Share crash log" (share sheet, plain text) and "Clear".
> - Nothing is sent anywhere automatically, and there is no network.
>
> **Docs:**
> - the `android-build` skill: how to record or verify goldens, and what to do when another PR
>   changes a screen (re-record in that PR);
> - `parallel-work`: Wave 1 PRs that change a screen re-record its golden;
> - a release fragment;
> - tick ROADMAP B4.
>
> Merge order: ask the owner to merge B4 first among Wave 1 if it is ready.

---

## Status board (coordinator, checked 2026-10-05)
| package | PR | state |
|---|---|---|
| A1 API, A2 UI split, #18 design pass | #20, #19, #18 | merged |
| B2 Park & ride, B3 Calendar | #23, #25 | merged |
| B1 Trip details | #24 | open; session active. Must compile after its main merge and answer #18's design review (shared sheet, readable badges, dark-safe highlights) |
| B4 UI tests + crash log | #26 (draft) | open; session active. **Merge first** among what is left |
| UI tests on devices (outside Phase 8) | branch `claude/gallant-mendel-4yps2i`, no PR | far behind `main`; overlaps B4 (`ci.yml`, `build.gradle.kts`, `TransitApp.kt`) and B1 (`MapController.kt`). Waits for #26 and #24, then merges `main` and yields to their files. Its CLAUDE.md change (CI-only keyless Test Lab login) is an owner decision |

Only one coordinator session runs at a time; it updates this table.

---

## Close-out (planner)
- Once all packages are merged, join `docs/releases/next/*.md` into `docs/releases/v0.7.0.md`
  (English block, then Hebrew block, then the usual update and attribution lines). Then delete
  the fragments.
- Check every Phase 8 ROADMAP line and every skill row.
  (v0.5.0 and v0.6.0 were released before Phase 8 merged, and v0.6.1 is the quality pass, so
  Phase 8 ships as v0.7.0.)
- Ask the owner to check each feature on the Pixel, then release with the `release-apk` skill.
