# Phase 9: the brief each agent session gets

The boss starts one cloud session per package. The prompt it sends is the **Common preamble**
followed by that package's brief, word for word as written below. If you re-run a package,
use the same text. When the plan changes, update this file in the same PR.

The eight items the owner picked come from `docs/research/phase9-ideas.md` (PR #27) and the
owner's list of 2026-10-05. They are grouped into five packages:

| package | items | wave | requests |
|---|---|---|---|
| C1 Earlier / Later + platforms | Earlier / Later + "if I miss it"; boarding platform and stop code | 1 | 1 per tap |
| C2 Night refresh | offline next-day trips (WorkManager); sets **Home** | 1 | ≤ 6 per night, Wi-Fi + charging |
| C3 History insights | "your usual trip"; monthly pass advisor | 2 | 0 |
| C4 Quick Settings tile | "Next trip home" tile | 2 | 0 to draw, ≤ 1 per tap |
| C5 Alerts | leave-now countdown; "last trip home" alert | 3 | 0 + ≤ 3 per evening |

Order:
- **Before Wave 1:** Phase 8 is closed out (B4 merged, so the `screenshots` job fails on an
  image a PR did not re-record).
- **Wave 1:** C1 and C2, in parallel. They share no file.
- **Wave 2:** C3 and C4, in parallel, after C1 and C2 are merged. C3 edits `TripPanel.kt`
  after C1. C4 reads C2's Home setting and night cache.
- **Wave 3:** C5 alone, after Wave 2 is merged. It owns the reminder code, uses C2's Home,
  C3's history fields and C4's "open the trip home" intent.

Why this order. These shared files would conflict if two packages edited them at once, so
each has only one editor per wave:

| shared file | wave 1 | wave 2 | wave 3 |
|---|---|---|---|
| `api/Models.kt`, `api/TransitApi.kt`, test `Fakes.kt` | C1 | | |
| `ui/screens/TripPanel.kt` | C1 | C3 (one line) | |
| `ui/screens/SettingsDialog.kt` | C2 (Home, night refresh) | C3 (history section) + C4 (one button), different sections | C5 (one toggle) |
| `user/User.kt` (`UserSettings`) | C2 | | C5 |
| `history/History.kt`, `fare/Fares.kt` | | C3 | |
| `data/PlanCacheStore.kt`, `plan/PlanCache.kt` | C2 | C4 (reads; one helper in a new file) | |
| `android/app/build.gradle.kts`, `gradle/libs.versions.toml` (WorkManager) | C2 | | |
| `AndroidManifest.xml` | | C4 (tile service) | C5 (receiver) |
| `TransitApp.kt` | C2 | | C5 |
| `MainActivity.kt` (intent extras) | | C4 | |
| `remind/*` (Notifications, ReminderReceiver, ReminderScheduler) | | | C5 |
| `ui/MainViewModel.kt` | C1, C2: one hook each, different functions | C3, C4: one hook each | C5: one hook |

The owner merges every PR; agents never merge.

**Models.** Implementers run on **Opus**. The tester and designer reviews run on **Sonnet**
(the tester moves to Opus for a hard failure). See `docs/agents/team.md`.

**After each package's PR is green** the boss starts two reviews on it, one after the other,
never more than 2 agents at once:
1. **Tester review** (Sonnet). Checks the PR against its brief and the `parallel-work`
   definition of done, and tries the edge cases the brief lists, always including:
   Shabbat and holidays (Friday afternoon, Saturday night), the midnight rollover and the
   04:00 service-day edge, no history at all, offline, permission denied, and RTL.
   Output: a review on the PR with each finding and how to reproduce it, or a test PR.
2. **Designer review** (Sonnet). Works from the CI `screenshots` artifact: light and dark,
   Hebrew and English, the small and large phones in `ScreensTest`. Loads `i18n-rtl`.
   Output: review comments with specific fixes.

The implementer fixes what both reviews find, on its own branch. Then the owner merges.

---

## Common preamble (sent first to every session)

> You are one of several agents working on Israel Transit Planner, each on its own branch and
> PR. You run on Opus.
>
> Before writing any code:
> 1. Read `CLAUDE.md`.
> 2. Load the `parallel-work` skill: file ownership, conflict rules, definition of done, token
>    rules.
> 3. Load `dead-ends`.
> 4. Load the skills your package names.
> 5. Read the top of `docs/agents/phase9.md`: the shared-file table says which shared files
>    you may touch.
>
> Your package is the `###` subsection of `ROADMAP.md` Phase 9 named below. Tick only those
> lines.
>
> Work on the branch given. Merge `origin/main` before opening the PR (never rebase). Open a PR
> to `main`, subscribe to its activity, and drive it to green CI. **Do not merge it.**
>
> - **Screens:** every screen you add or change gets a Paparazzi state in
>   `android/app/src/test/.../ui/` (Hebrew and English, light and dark), re-recorded in your
>   PR. The tester and the designer review from the `screenshots` artifact.
> - **Release notes:** write a fragment at `docs/releases/next/<package>.md`, 1–3 bullets in
>   English, then the same in Hebrew.
> - **Requests:** every new request goes through `TransitApi`, and its budget is pinned by a
>   test. A background job states its constraints and its worst case per day in the PR body.
> - **Blocked?** If you are blocked on another package or need an owner decision, write it in
>   the PR body, tell the boss in one message, and stop. Don't work around it.
> - **Transitous traffic:** keep live calls to the few needed to record fixtures.
> - **Messages:** one message to the boss when the PR is green and clean against `main`, or
>   one when blocked. No progress messages, and no self-scheduled check-ins.

---

## C1: Earlier / Later + platforms
**Branch:** `claude/p9-trip-pages`
**Skills:** `transitous-api`, `i18n-rtl`, `add-feature`
**Needs:** Phase 8 closed out (B4 merged)
**Owns:** `api/Models.kt` and `api/TransitApi.kt` (fields only), test `Fakes.kt`,
`plan/TripPages.kt` (new), `present/StopPlatform.kt` (new), `ui/screens/TripPanel.kt`,
`ui/screens/TripDetailsSheet.kt`, `ui/screens/StopSheet.kt`, `strings_trip_pages.xml`
**Shared-file hooks:** `MainViewModel`: one `earlier()` / `later()` pair of actions, added
next to the existing search code; `MainActions`: the same two callbacks

> **What the user gets:**
> 1. **Earlier / Later.** Under the Trip options list, an "Earlier" and a "Later" button. Each
>    tap loads the adjacent page of options and merges it into the list. The sort chips apply
>    to the merged list.
> 2. **"If I miss it."** On the selected option, one line: "If I miss this: next at 08:35
>    (+30 min)". It is the first loaded option whose first transit leg leaves later than the
>    selected one's. When none is loaded, the line says "Show later" and does what the Later
>    button does. It never sends a request on its own.
> 3. **Platform and stop code.** Where you board and get off, show "Platform 12 · floor 6 ·
>    stop 47899" when the data has it:
>    - the boarding and alighting rows of the trip details sheet;
>    - the stop sheet header;
>    - the boarding line of the itinerary card.
>
>    Show nothing when the fields are blank.
>
> **Data (research #2 and #4, fixtures):**
> - `plan` answers carry `previousPageCursor` / `nextPageCursor` (for example `LATER|1791060420`
>   in `plan_now_bs_hahagana.json`). Passing one back as `pageCursor` returns the adjacent
>   window. The app does not use them yet.
> - Stop `description` text carries `רציף: N` (platform) and sometimes `קומה: F` (floor):
>   70 of 391 distinct descriptions in the fixtures. Model `stopCode` too.
> - **Trains have no platform.** `track` is empty on every Israel Railways leg (fixtures and a
>   live plan of 2026-10-06). Don't show a rail platform. This is in `dead-ends`.
>
> **Core:**
> - `PlanRequest.pageCursor: String? = null`, sent as `pageCursor` by `toQuery()`. It is part
>   of the query, so `GuardedTransitApi` caches each page like any `plan`.
> - The plan response's two cursors, and `TripResult.earlierCursor` / `laterCursor`, all with
>   null defaults, so the stored trip cache still decodes.
> - `Place.description: String? = null` and `Place.stopCode: String? = null`. Add fields only;
>   never reorder.
> - `plan/TripPages.kt` (pure): `merge(current, page, sort)`:
>   - dedupes by trip ids plus start time;
>   - applies `TripPlanner`'s "absurdly long" filter to the merged list, so a page that waits
>     out Shabbat adds nothing;
>   - caps the list at 20;
>   - finds "if I miss it".
> - `present/StopPlatform.kt` (pure): parses the Hebrew key/value text defensively. Unknown
>   keys, extra spaces, an English description or blank text give null, never a crash.
> - `FakeTransitApi` answers `pageCursor` requests.
>
> **Requests:** 1 per Earlier or Later tap, the same cursor again costs 0 (guard cache). No
> automatic paging. Pin it in a test: two Later taps send 2 requests, and repeating a cursor
> sends 0.
>
> **Fixtures:** record one `plan` with `pageCursor=LATER|…` and one with `EARLIER|…`, from a
> fresh `plan` (`tools/record_fixture.py`, 3 requests in total).
>
> **Reminders and routines:** a reminder must follow the option the user selected after
> paging, not the first one. Check `plan/Reselect.kt` and add a test.
>
> **Tests:** `TripPagesTest.kt`, `StopPlatformTest.kt`, and a parse test for the new fixtures.
>
> **Tester edge cases:** the Later page on Friday afternoon (crosses Shabbat); Earlier past
> 04:00 back into yesterday's service day; arrive-by mode; offline (the buttons show the
> network error, and the cached list stays); a description with only a floor; RTL order of
> "Platform 12 · stop 47899".
>
> **Docs:**
> - the `transitous-api` skill: `pageCursor` in the endpoint table, and the stop
>   `description` format;
> - a release fragment `docs/releases/next/c1-trip-pages.md`;
> - tick ROADMAP C1.

## C2: Night refresh (offline next-day trips) + Home
**Branch:** `claude/p9-night-refresh`
**Skills:** `reminders-offline`, `transitous-api`, `android-build`, `add-feature`, `i18n-rtl`
**Needs:** Phase 8 closed out (B4 merged)
**Owns:** `plan/NightRefresh.kt` (new), `work/NightRefreshWorker.kt` (new),
`data/PlanCacheStore.kt`, `plan/PlanCache.kt`, `user/User.kt` (`UserSettings` fields),
`android/app/build.gradle.kts` and `gradle/libs.versions.toml` (WorkManager only),
`TransitApp.kt`, `strings_night.xml`
**Shared-file hooks:** `SettingsDialog`: a "Set as Home" action on a saved place row, and one
"Refresh my trips at night" toggle; `MainViewModel`: one line that re-enqueues the work when
settings change

> **What the user gets:**
> - **Home.** Any saved place can be set as Home: "Set as Home" on its row in Settings, and
>   a 🏠 mark after that. When no Home is set, a saved place named "Home" or "בית" is offered
>   once as a suggestion. C4 and C5 use Home too.
> - **Night refresh.** Each night the app plans tomorrow's usual trips while the phone is on
>   Wi-Fi and charging. With no signal the next morning, the Trip tab shows them with
>   "Offline · planned last night at 02:14". Only options still ahead are shown, the same as
>   My lines offline.
>
> **Which trips (pure, `plan/NightRefresh.kt`, `jobs(places, trips, home, history, tomorrow)`):**
> - every saved place whose routine includes tomorrow: from Home to that place, at the
>   routine's start time. Skip it if no Home is set;
> - every saved trip: its `from`, or Home when `from` is null, at the destination's routine
>   start time tomorrow if it has one. Otherwise use the time of day of the latest history
>   record for that trip. With neither, skip it;
> - dedupe by cache key, earliest first, at most `NightRefresh.MAX_TRIPS = 6`;
> - a day with no service (Shabbat or a holiday): plan anyway. `TripPlanner`'s filter drops
>   the trips that wait out Shabbat, and an empty answer is not cached.
>
> **Cache:** each result goes into the Trip cache under the key the Trip tab builds for a
> NOW search of the same places (`PlanCache.key("TRIP-NOW", from, to)`), so tomorrow's
> offline search finds it. Mark the entry as a night entry (a nullable field, so old files
> decode), for the banner text. Night entries go through the same LRU: either raise
> `PlanCache` capacity to 16 or keep 10 and say why. Make sure the offline Trip view drops
> options that have already left, for every cached entry.
>
> **Background job (a Transitous policy matter, written in the PR body):**
> - WorkManager (`androidx.work:work-runtime-ktx`), one unique periodic work, every 24 h,
>   with a flex window that lands it between 00:00 and 05:00 Israel time. The worker returns
>   without a request when it wakes outside 22:00–06:00.
> - Constraints: `NetworkType.UNMETERED` (Wi-Fi), `requiresCharging`, `requiresBatteryNotLow`.
> - Requests: at most 6 `plan` per night, under `BudgetedTransitApi(6)`. That is the worst
>   case per day; the guard's single retry on 429/503 can add one HTTP call each. A failed
>   trip is skipped until the next night: the worker never asks WorkManager to retry.
> - It runs only when the setting is on **and** there is at least one job. If the owner
>   leaves the default off, the work is not enqueued at all.
> - Pin in tests: 8 eligible trips send 6 requests; a second run on the same night sends 0
>   (store the last run's service day); the time-window check.
>
> **Settings:**
> - `UserSettings.homePlace: String? = null` (the saved place's name);
> - `UserSettings.nightRefresh: Boolean = true` (owner, 2026-10-05).
>
> Both have a JSON round-trip test. Renaming or deleting the Home place clears or renames
> `homePlace`.
>
> **Tests:** `NightRefreshTest.kt`: picking trips (routine days, Shabbat, missing Home, a saved
> trip with no time, dedupe, the cap), the cache key, the budget, past options dropped from an
> offline entry.
>
> **"Tomorrow"** is the service day that starts at the next 04:00
> (`LastRideFinder.DAY_STARTS`), so a run at 23:50 and a run at 00:10 plan the same day.
>
> **Tester edge cases:** a routine on Sunday seen from Saturday night; runs at 23:50 and
> 00:10 plan the same day; Wi-Fi
> off or not charging (nothing runs); no Home; the Home place deleted; airplane mode in the
> morning; Doze on the owner's Pixel (check with `adb shell cmd jobscheduler`).
>
> **Docs:**
> - the `reminders-offline` skill: night refresh (constraints, the worst case per day, the
>   cache key, the Home setting);
> - the `transitous-api` skill: add the job to the budget table;
> - a release fragment `docs/releases/next/c2-night-refresh.md`;
> - tick ROADMAP C2.

## C3: History insights (usual trip + monthly pass)
**Branch:** `claude/p9-insights`
**Skills:** `add-feature`, `i18n-rtl`
**Needs:** C1 merged (`TripPanel.kt`), C2 merged (`SettingsDialog.kt`)
**Owns:** `history/History.kt` (`TripRecord` fields), `history/UsualTrip.kt` (new),
`fare/Fares.kt` (monthly pass prices), `fare/PassAdvisor.kt` (new),
`ui/screens/Insights.kt` (new), `strings_insights.xml`
**Shared-file hooks:** `TripPanel`: one line on the selected itinerary card; `SettingsDialog`:
the history section shows the pass advice; the routine dialog shows the best leave time;
`MainViewModel`: the line that builds the `TripRecord` (around `TripRecord.from`) fills the
new fields

> Everything here is computed on the phone from local history. **0 requests.**
>
> **What the user gets:**
> 1. **"Your usual trip."** On the selected option: "Usually 52 min · this one +8". It shows
>    when there are at least 3 records of the same trip in the last 90 days. "Same trip"
>    means start and end within about 300 m (rounded coordinates), or the same saved place
>    names for old records.
> 2. **Best leave time** for a routine, in the routine dialog: "Quickest when you leave around
>    07:15 (usually 41 min)". It uses the median per 15-minute bucket of departure time, on the
>    routine's days, with at least 2 records per bucket. Show nothing when the data is too thin.
> 3. **Monthly pass advisor**, in the history section: "A monthly pass would have saved ₪74 in
>    September", or "Paying per ride was ₪40 cheaper in September". It compares:
>    - the month's single fares, with the daily caps applied per day (the logic is already in
>      `FareEstimator`);
>    - the cheapest monthly pass that covers every band ridden.
>
>    It is shown only for a finished month with at least 10 trips. It always says "based on N
>    trips you started in the app", because history only knows trips the user started.
>
> **Core:**
> - `TripRecord` gets `totalMin`, `fareAgorot`, `fareBand`, `withTrain`, and rounded
>   `fromCell` / `toCell`. All are nullable with defaults, so the old `history.json` still
>   decodes. Test it with a stored old-format string.
> - `history/UsualTrip.kt` (pure): `usual(records, from, to, now)` and
>   `bestLeave(records, routine, place)`. Use medians, never means, because one 3-hour
>   Shabbat trip must not move them.
> - **Monthly pass prices** in `FareTable`, from the same HopOn source (`SOURCE_URL`), with
>   `CHECKED` updated and pinned in `FareTest`. If the source has no monthly pass prices,
>   **stop and ask the owner** for a source; don't invent numbers. The fare profile
>   (half / free) applies to passes as it does to single fares. With the FREE profile, show
>   no advice.
> - `fare/PassAdvisor.kt` (pure), with tests built on hand-made records.
>
> **Tests:** `UsualTripTest.kt`, `PassAdvisorTest.kt`, and the old-history decode test.
>
> **Tester edge cases:** no history; exactly 3 records; a 25-hour Shabbat record among them; a
> trip starting at 23:50 and ending at 00:30 (month and day rollover); a month split across
> two fare profiles; old records with no new fields; the FREE profile; RTL "₪74" and "+8".
>
> **Docs:**
> - the `reminders-offline` skill: the history section (new fields, what insights read);
> - a release fragment `docs/releases/next/c3-insights.md` (say it is an estimate from trips
>   started in the app);
> - tick ROADMAP C3.

## C4: Quick Settings tile "Next trip home"
**Branch:** `claude/p9-tile`
**Skills:** `android-build`, `reminders-offline`, `i18n-rtl`, `add-feature`
**Needs:** C2 merged (Home and the night cache)
**Owns:** `tile/NextTripTile.kt` (new), `present/TileText.kt` (new),
`plan/CacheLookup.kt` (new), `MainActivity.kt` (the intent extra), `strings_tile.xml`
**Shared-file hooks:** `AndroidManifest.xml`: one `<service>` element, placed right after the
`RideService` element (C5 later adds a receiver after `BootReceiver`); `SettingsDialog`: one
"Add to Quick Settings" button; `MainViewModel`: one `openTripHome()` entry point

> **What the user gets:** a Quick Settings tile "Home". When it is drawn it shows the next
> option home from the cache: "Home · 22:40 → 23:35". Tapping it opens the app on the
> planned trip home from where the user is.
>
> **Drawing the tile costs 0 requests.** In `onStartListening`, read `PlanCacheStore` only:
> - pick the newest cached Trip entry whose destination is within about 110 m of Home
>   (`plan/CacheLookup.kt` reads the destination from the cache key; test it);
> - take its first option still ahead.
>
> With no entry or no option ahead, the tile shows "Home" with "tap to plan". With no Home
> set, it shows "Set Home" and opens Settings. `present/TileText.kt` (pure, tested) makes the
> label and subtitle.
>
> **Tapping costs ≤ 1 request:**
> - `startActivityAndCollapse` with a `PendingIntent` (API 34+), and the old `Intent` form
>   below 34;
> - `MainActivity` handles `EXTRA_TRIP_HOME` the way it handles the shortcut extras;
> - `MainViewModel.openTripHome()` sets the destination to Home and runs the normal Trip
>   search from my location (1 `plan`, through the existing `TripPlanner`), then selects the
>   first option;
> - with no location fix, it uses the cached entry's origin;
> - offline, it shows the cached entry with the usual offline banner.
>
> Pin it in a test where you can: the extra leads to exactly one plan. C5 reuses this intent
> for its notification tap.
>
> **"Add to Quick Settings":** on API 33+, `StatusBarManager.requestAddTileService` from the
> Settings button; below 33, a one-line hint on how to add it by editing the panel.
>
> **No background work:** the tile never refreshes itself, and there is no worker or alarm.
>
> **Tests:** `CacheLookupTest.kt`, `TileTextTest.kt` (no cache, stale cache, an option that
> just left, the 04:00 service-day edge, no Home).
>
> **Tester edge cases:** location permission denied (uses the cache origin); app killed (the
> tap cold-starts on the trip); already at Home; Saturday afternoon (no option ahead, so "tap
> to plan"); the tile added before Home is set; RTL label.
>
> **Docs:**
> - the `reminders-offline` skill: the tile (reads the cache, 1 request per tap);
> - a release fragment `docs/releases/next/c4-tile.md` (say how to add the tile);
> - tick ROADMAP C4.

## C5: Alerts (leave-now countdown + last trip home)
**Branch:** `claude/p9-alerts`
**Skills:** `reminders-offline`, `transitous-api`, `i18n-rtl`, `add-feature`
**Needs:** C2 (Home), C3 (history fields) and C4 (the trip-home intent) merged
**Owns:** `remind/Notifications.kt`, `remind/ReminderReceiver.kt`,
`remind/ReminderScheduler.kt`, `remind/LastTripReceiver.kt` (new), `remind/Countdown.kt` (core,
new), `plan/LastTripHome.kt` (core, new), `strings_alerts.xml`
**Shared-file hooks:** `user/User.kt`: the new `UserSettings` fields; `SettingsDialog`: one
"Last trip home alert" toggle; `AndroidManifest.xml`: one `<receiver>` right after
`BootReceiver`; `TransitApp.kt`: arm the evening alarm on start; `BootReceiver`: re-arm it;
`RideService`: one line that removes the countdown when a ride starts

> **1. Leave-now countdown.** **0 extra requests.**
>
> When the leave alarm fires, the existing "time to leave" notification becomes an
> **ongoing** one: the same notification id, so there are never two.
> - It counts down to boarding: `setUsesChronometer(true)` + `setChronometerCountDown(true)`,
>   with `setWhen` = boarding time (scheduled + known delay).
> - Text: "Line 5 · board at Rager/Oren · walk 6 min".
> - Actions: "Start trip" (opens the app and starts the ride, the same as the in-app button)
>   and "Dismiss".
> - It goes away by itself 2 min after boarding (`setTimeoutAfter`), when the ride starts, or
>   when the reminder is cancelled.
> - A re-check that moves the time updates the countdown.
>
> `remind/Countdown.kt` (pure, tested) makes the text and the end time.
>
> **2. "Last trip home" alert.** Opt-in; the default is the owner's decision.
> - **When:** an inexact alarm (`setAndAllowWhileIdle`) once per evening. The check times are
>   an owner decision. The proposal: 19:00 Sun–Thu, 12:00 Friday (service ends in the
>   afternoon) and 20:00 Saturday.
> - **Am I away from Home?** No background location. The proposal, also an owner decision:
>   the origin is the latest of
>   - the destination of today's latest started trip (history), unless a trip to Home was
>     started after it;
>   - the last location the app saw while in front, today, within the last 3 h.
>
>   No origin, or an origin within 1 km of Home, means no request and no alert.
> - **Check:** `LastRideFinder.find(origin → Home, today)` under `BudgetedTransitApi(3)`
>   (= `LastRideFinder.BUDGET`). `runsAllNight` means no alert. Otherwise schedule one exact
>   alarm (the app already holds `USE_EXACT_ALARM`) at last trip's start − walk − 30 min. It
>   posts: "Last trip home 23:10 from Be'er Sheva Central: leave in 30 min". With
>   `longGap` it adds "(next: Saturday 19:30)". When that time has already passed: "Last trip
>   home leaves at 23:10, leave now", or nothing if it has gone.
> - Tapping the notification opens the trip home through C4's `EXTRA_TRIP_HOME`.
> - **At most once per service day:** store the day checked and the day notified.
>
> `plan/LastTripHome.kt` (pure) decides the origin, whether to ask, and the notification
> time and text.
>
> **Budget and policy (written in the PR body):** ≤ 3 `plan` per evening, so the worst case
> is 3 requests per day, and only when the setting is on. Pin in tests:
> - a check sends ≤ 3;
> - a second check on the same service day sends 0;
> - no origin sends 0.
>
> **Notifications:** a separate channel, "Last trip home" (default importance), so it can be
> silenced on its own. Turning the setting on asks `POST_NOTIFICATIONS` (API 33+) with a
> rationale, like reminders do. If it is refused, the toggle stays off and says why.
>
> **Settings:** `UserSettings.lastTripAlert: Boolean = false` (owner, 2026-10-05: opt-in), with a JSON
> round-trip test.
>
> **Tests:** `CountdownTest.kt`, `LastTripHomeTest.kt` (origin rules, near Home, `runsAllNight`,
> `longGap`, Friday, a time already passed, once per day, the 04:00 service-day edge).
>
> **Tester edge cases:** Friday 12:00 with the last bus at 15:40; Saturday night after
> service resumes; a holiday eve on a weekday (the timetable knows: check `longGap`); a phone
> rebooted in the evening (`BootReceiver` re-arms); notifications denied; exact alarms
> refused (falls back to inexact); a reminder cancelled during the countdown; a countdown
> past midnight; RTL countdown text.
>
> **Docs:**
> - the `reminders-offline` skill: the countdown, the last-trip alert, its schedule and its
>   worst case;
> - the `transitous-api` skill: the evening check in the budget table;
> - a release fragment `docs/releases/next/c5-alerts.md`;
> - tick ROADMAP C5.

---

## Owner decisions this plan needs (ask before Wave 1)

**Decided 2026-10-05:** the owner approved every proposal below as written. Night refresh
is on, with a cap of 6. Home is set by "Set as Home". The last-trip alert is off (opt-in), checked
at 19:00 Sun–Thu, 12:00 Fri and 20:00 Sat, with a 30-minute warning. "Away" comes from history plus
the last foreground location. The countdown is always on. Pass prices come from HopOn's page.
The release is v0.8.0.
1. **Night refresh default:** on or off? Proposal: **on**, with Wi-Fi + charging only and at
   most 6 requests a night. Friends will never find the toggle.
2. **Night refresh cap:** 6 trips a night? It costs at most 6 requests a day per phone.
3. **Home:** set by "Set as Home" on a saved place (proposal), or by a place named
   "Home" / "בית"?
4. **Last trip home default:** off (opt-in, as the owner asked)? Confirm.
5. **Last trip home check times:** 19:00 Sun–Thu, 12:00 Friday, 20:00 Saturday? And notify
   30 min before leaving?
6. **"Away from Home" without background location:** use today's history and the last
   foreground location (proposal), or ask for `ACCESS_BACKGROUND_LOCATION` (a scary prompt,
   and more accurate)?
7. **Countdown:** on for every reminder with no setting (proposal), or a setting?
8. **Monthly pass prices:** OK to take them from the HopOn price page that `FareTable`
   already cites? If it has none, which source?
9. **Release:** Phase 9 ships as **v0.8.0** after Phase 8's v0.7.0.

---

## Close-out (boss)
- Once all packages are merged, join `docs/releases/next/*.md` into `docs/releases/v0.8.0.md`
  (English block, then Hebrew block, then the usual update and attribution lines). Then
  delete the fragments.
- Check every Phase 9 ROADMAP line and every skill row.
- Ask the owner to check each feature on the Pixel (the night refresh needs one night on
  Wi-Fi and charging), then release with the `release-apk` skill.
