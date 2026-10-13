---
name: reminders-offline
description: The "time to leave" reminder (exact alarms, real-time re-check, boot re-arm, notifications) and the offline fallback (cached Trip results, the offline map area). Load before editing core/remind, core/plan/PlanCache, android remind/, data/PlanCacheStore, or ui/OfflineMap.
---

# "Time to leave" reminders

- **Logic is in `core/remind/Reminder.kt`** and tested: `Reminder.from(itinerary, …)` sets
  `leaveAt` = itinerary start (when to start walking). Re-checks run at leave − 30, 20, 12,
  6 and 2 min (`Reminder.RECHECKS`, `ReminderLogic.nextRecheck`), each re-arming the next:
  at most 5 plan requests per reminder.
  `ReminderLogic.update` finds the same vehicle in a fresh plan — by `tripId` when both sides
  have one, else line + boarding stop + scheduled time ±1 min — and returns `Updated`
  (new leave time) or `Gone` (cancelled / no longer offered).
- **Delay alerts need GTFS-RT, which Israel MOT lines do not have on Transitous** (only
  busofash does; see `transitous-api`). They stay quiet on most trips — expected, not a bug.
- **Delay alert** (`ReminderLogic.alertMinutes`): notify when the leave time moved ≥ 3 min,
  **earlier or later**, from `toldLeaveAt` — the time the user was last told, stored as
  `alertedLeaveAtEpoch` after each alert. Measuring from the told time (not the previous
  check) means a delay creeping 2 min at a time still alerts once, and never twice for the
  same shift. Text: "Line 3 is 4 min late — leave at 08:16" / "comes 3 min early".
- **One active reminder**, stored as JSON in `UserStore` (`UserJson.encodeReminder`).
- **Alarms** (`remind/ReminderScheduler.kt`): two exact alarms, re-check and leave.
  `USE_EXACT_ALARM` (API 33+) is fine because the app is sideloaded — it is a Play-policy
  restriction, not a platform one; if the app ever goes to the Play Store this must change.
  `SCHEDULE_EXACT_ALARM` is declared only up to API 32. If exact alarms are refused, an
  inexact `setAndAllowWhileIdle` is used rather than nothing.
- **The receiver never loses a reminder on a bad signal**: a failed re-check keeps the
  original time and arms the next re-check. `Gone` posts "your trip changed" and clears it.
- **Reboot clears alarms** → `BootReceiver` re-arms (or drops a reminder already past).
- `POST_NOTIFICATIONS` is asked the first time a reminder is set (API 33+). A refusal still
  arms the alarm; only the banner is blocked.
- **Trip tab only.** Car-feature legs have different "leave" semantics (the driver leaves).
- **Leave-now countdown** (Phase 9 C5, `core/remind/Countdown.kt`, 0 requests): the leave
  alarm posts the ongoing notification on the same id (`ID_LEAVE`), counting down to boarding
  (scheduled + known delay; `setUsesChronometer` + `setChronometerCountDown`, `setWhen`):
  "Line 5 · board at Rager/Oren · walk 6 min" (walk = WALK legs before the first vehicle;
  "Train 406" for rail). Actions "Start trip" (`Notifications.EXTRA_START_RIDE` →
  `MainActivity` → `MainViewModel.startReminderRide()`: the reminder's stored itinerary, same
  permission checks and history record as the in-app button) and "Dismiss". The reminder
  stays stored with `counting = true` until the countdown ends, so Cancel in the app takes it
  down (`ReminderScheduler.cancel`). It ends 2 min after boarding (`setTimeoutAfter`, plus the
  `ACTION_END` alarm that drops the reminder), when a ride starts (`RideService`), on Dismiss or
  Cancel. Re-checks all run before leaving, so the countdown shows the delay known at the
  leave alarm (0 requests); only a re-check that fires late re-posts it (`setOnlyAlertOnce`)
  and re-arms `ACTION_END`. Setting a new reminder cancels the old one's countdown and alarms
  first (`remindSelected` → `ReminderScheduler.cancel`). A leave alarm that
  fires past the end (Doze) posts nothing; a reboot before the end brings the countdown back.
  `Reminder` stores the itinerary and the planned names since C5 (null on older reminders:
  no "Start trip", no walk).

# "Get off at the next stop" (`core/ride/RideTracker.kt`, `android/.../ride/RideService.kt`)

- Tracker is pure and tested with synthetic tracks: per transit leg it fires `Approaching`
  ONCE — within 120 m of the stop before yours (if MOTIS listed intermediate stops) or 400 m
  of your stop — then `Finished` at the destination or 30 min past planned arrival.
- **On-the-bus progress** (`RideTracker.progress`): the nearest stop-to-stop segment of the
  current leg gives "N stops left" (never moves backwards on GPS jitter); the schedule
  interpolated to that point vs now gives the delay, which shifts the arrival estimates. The
  service publishes it (`RideService.progress`) and rewrites the ongoing notification
  ("Line 5 · 3 stops left" / "Get off at X ~08:47 (+2 min) · arrive 08:55"); the app shows
  the same in the ride row. 50 of 60 recorded transit legs list intermediate stops.
- The service is a `location` foreground service that exists only during a ride; it uses
  the platform `LocationManager` (GPS + network, 5 s / 15 m), not Play services.
- **The `LocationListener` is an explicit object, never a lambda**: below API 29 its other
  methods are abstract and a SAM lambda crashes with AbstractMethodError.
- Starting a ride also writes a `TripRecord` to the history (`data/HistoryStore`,
  `filesDir/history.json`, newest first, capped at 500 — `core/history/History.kt`).
- Since Phase 9 C3 a record also has `totalMin` (door to door), `fareAgorot` (full fare,
  before the profile's discount), `fareBand`, `withTrain`, and `fromCell` / `toCell` (start
  and end rounded to 3 decimals). All nullable: older records decode with them null; insights
  skip records with no `totalMin` (no waits known) and pass advice skips those with no fare.
- **Insights** read only this history, 0 requests. `history/UsualTrip`: "Usually 52 min ·
  this one +8" on the selected option (≥ 3 records in 90 days, both ends within 300 m of the
  cells, or the same saved-place names when a record has no coordinates) and the routine dialog's best leave
  time (median per 15-min slot on the routine's days, ≥ 2 records per slot). `fare/PassAdvisor`:
  the last finished month (service days, 04:00 boundary) priced per ride with daily caps
  against the cheapest monthly pass covering its bands; ≥ 10 trips with a fare, none for the
  free profiles (Senior, Soldier, Free), today's profile for the whole month, single and
  daily shares for rides, the monthly share for the pass. Medians, never means.
- **Fare profiles** (Phase 10 D8, `fare/Fares.kt` `FareProfile`): each has a share paid on
  single rides + daily cap and on the monthly pass, every number from the Rav-Pass profiles
  page (`FareProfile.PROFILES_URL`, pinned in `FareProfileTest`). Old settings (`REGULAR`,
  `HALF`, `FREE`) keep their names and prices; never rename an entry.
- **Monthly report** (Phase 10 D8): History → "Monthly report" (shown once a calendar month
  has finished) → a month → the share sheet with `transit-2026-09.csv` (UTF-8 with BOM, CRLF,
  RFC 4180 quoting, formula-looking cells prefixed with `'`) and the summary as text.
  `history/Report.kt` is pure: calendar month of the start in Israel time, one row per trip
  (date, start, end, from, to, lines, minutes, fare after the single-ride share, profile), then
  total trips, the column's sum (no caps, so it adds up) and "not a receipt". The file goes
  to `cacheDir/reports/` (`ui/screens/ReportExport.kt` `ReportFiles`), shared through the
  existing FileProvider (`res/xml/update_paths.xml`, authority `<package>.updates`); files
  older than a day are deleted at app start and before each new report. `TripRecord.lines`
  (since D8) fills the lines column; older records leave it empty.

# Offline
- **My lines offline**: each successful board is saved per stop (`core/present/DepartureCache`,
  `data/DepartureCacheStore`, `filesDir/departures_cache.json`, 20 stops). With no signal the
  saved board shows departures still ahead only, marked "Offline — timetable saved at HH:MM",
  never a delay or cancellation.

- **Offline timetables for starred stops** (Phase 10 D7): the night refresh, after the trips, saves
  tomorrow's board (one `stoptimes` page, `n = 100`, from 04:00) for at most 3 stops of My lines
  (`NightRefresh.stops`, pinning order) under its own `BudgetedTransitApi(3)`; a failed or empty stop
  keeps its old board. `data/TimetableStore` (`filesDir/timetables.json`, ≤ 6 stops, codec
  `present/SavedBoardsJson`) keeps the last board per stop. With no signal the stop sheet and the day
  timetable (`ui/DayTimetableUi`) show it, departures still ahead only, under "Offline · saved last
  night at 02:14" (`SavedBoard.isLastNight`, < 18 h; older: "saved 12/10 at 02:14"). The worker is
  enqueued when some night has a trip **or** My lines has a stop.

- **Trip results**: `core/plan/PlanCache` (LRU, 16) + `TripCacheJson`, persisted by
  `data/PlanCacheStore` in `filesDir/trip_cache.json`. Key = tab + time mode + places rounded
  to 3 decimals (~110 m), so "my location" a few steps away still hits. On a NETWORK error
  the Trip tab shows the cached entry with an "Offline — saved at HH:MM" banner. Car tabs
  are not cached: their whole point is live combinations.
  Every offline view drops options whose vehicle already left (`TripResult.stillAhead`,
  applied in `PlanCacheStore.get`); an entry with nothing left shows the network error.
- **Home** (`UserSettings.homePlace`, the saved place's name; `core/user/Home.kt`): "Set as
  Home" (🏠) on a place row in Settings. A place named "Home"/"בית" is offered once
  (`homeOffered`). Deleting the Home place clears it; `Home.of` ignores a dangling name.
  Night refresh, the Quick Settings tile (C4) and the evening alerts (C5) use it.
- **Night refresh** (`core/plan/NightRefresh.kt`, `work/NightRefreshWorker.kt`): plans
  tomorrow's usual trips into the Trip cache so the morning works offline.
  - Which: Home → each saved place whose routine has tomorrow (at its start); each saved trip
    from its `from` or Home, at the destination's routine start, else the time of day of its
    latest history record, else skipped. Earliest first, deduped by key, ≤ `MAX_TRIPS = 6`.
    Shabbat/holidays are planned anyway (an empty answer is not cached).
  - The day planned is the coming morning (`NightRefresh.tomorrow`): 22:00–03:59 → the service
    day starting at the next 04:00 (23:50 and 00:10 plan the same day); 04:00–05:59 → the service
    day that just started (a 04:30 run plans this morning; after a 00:10 run, `lastRunDay` keeps
    it at 0 requests). Boss decision on the tester review, 2026-10-05.
  - Key: `PlanCache.key("TRIP-NOW", from, to)`, the key of the Trip tab's NOW search, so the
    morning's search finds it. Entries carry `night = true` → banner "Offline · planned last
    night at 02:14" only while the entry is under 18 h old (`Entry.isLastNight`; a missed
    night shows the ordinary "saved at" banner). Capacity is 16 so 6 night entries never push out the user's last 10.
  - Job: one unique periodic WorkManager work (`KEEP`), 24 h with a 5-h flex window opening
    at 00:00 Israel time (`NightRefresh.initialDelay`); constraints Wi-Fi (`UNMETERED`),
    charging, battery not low. The worker returns without a request outside 22:00–06:00 or
    when the service day was already done (`filesDir/night_refresh_day.txt`), and never asks
    WorkManager to retry: a failed trip waits for the next night.
  - **Worst case per day: 6 `plan`** (`BudgetedTransitApi(6)`) **+ 3 `stoptimes`** (My lines' boards,
    own `BudgetedTransitApi(3)`), + 1 each on a 429/503 retry.
  - Enqueued only while the setting is on (default on, owner 2026-10-05) **and** some night
    of the coming week has a trip (`NightRefresh.anyJobs`); `TransitApp` re-checks on every
    change of settings, places, trips or history, and cancels the work otherwise.
  - Check on a phone: `adb shell cmd jobscheduler` / `adb shell dumpsys jobscheduler | grep -A5 night`;
    Doze can push the run later in the window; a run after 06:00 does nothing.
- **Quick Settings tile "Home"** (C4; `tile/NextTripTile.kt`, core `plan/CacheLookup.kt`,
  `present/TileText.kt`): drawn in `onStartListening` from the Trip cache only, **0 requests**:
  the newest `TRIP-*` entry whose key's destination is within 110 m of Home and still has an
  option ahead → "Home · 22:40–23:35"; none → "tap to plan"; no Home → "Set Home". It never
  refreshes itself (no worker, no alarm). A tap opens `MainActivity` with
  `NextTripTile.EXTRA_TRIP_HOME` (`tripHomeIntent`, which C5 reuses) → `MainViewModel.openTripHome()`:
  NOW from my location (waits ≤ 5 s for a fix, else the cached entry's origin; `CacheLookup.start`), **1 `plan`**,
  no last-ride hint; offline → the cached entry under the usual banner; no Home → Settings.
  Settings → "Add to Quick Settings" (`requestAddTileService`, API 33+; a hint below).
- **"Last trip home" alert** (Phase 9 C5; core `plan/LastTripHome.kt`, `remind/LastTripReceiver.kt`):
  opt-in (`UserSettings.lastTripAlert`, default off, owner 2026-10-05), own channel "Last trip
  home" (default importance). Turning it on asks `POST_NOTIFICATIONS` (API 33+) after a
  rationale; refused or blocked → stays off and says why (`ui/screens/LastTripSettings.kt`).
  - **When:** one inexact alarm (`setAndAllowWhileIdle`) per evening: 19:00 Sun–Thu, 12:00 Fri,
    20:00 Sat (`LastTripHome.checkTime`). `TransitApp` arms it on start and on every toggle;
    `BootReceiver` re-arms it. A check missed while the phone was off runs at once, inside the
    same service day (`nextCheck`), but never 02:00–03:59 (`quiet`: the last trip has gone).
    Each alarm re-arms the next day's and hands the check to `LastTripWorker` (one-time
    WorkManager work, network `CONNECTED`): a broadcast must finish in seconds, three `plan`
    calls may not.
  - **Where am I** (no background location): the latest of today's latest started trip's
    destination (at its arrival; history `toCell`) and the last fix the app saw in front (saved
    in `MainActivity.onPause`), if today and < 3 h old. None, or within 1 km of Home → 0 requests.
  - **Check:** `LastTripChecker` → `LastRideFinder(origin → Home, service day)` under a
    `BudgetedTransitApi` holding what is left of the day's 3. The day record (`LastTripDay`:
    requests spent, done) is written *before* the first request as if all 3 were spent (a killed
    process cannot exceed the cap), then with what was really spent. An answer finishes the day;
    no signal leaves it open and the worker retries 15 min later (linear backoff) while budget
    is left: offline all evening = 3 tries, 3 requests. `runsAllNight` → nothing. Else one exact alarm at
    leave − 30 min (inexact if exact alarms are refused); the alert is stored (`LastTripStore`,
    survives a reboot). The foreground fix is saved only while the setting is on, and cleared
    when it is turned off. It posts "Last trip home 23:10 from X: leave in 30 min", plus "(next:
    Saturday 19:30)" on `longGap` (Shabbat, a holiday eve: the timetable knows). Checked after the
    warning time → "leaves at 23:10: leave now"; past the leave time → nothing. At most one per
    service day (`notified_day`). A tap opens C4's `NextTripTile.tripHomeIntent` (one `plan`).
  - **Worst case per day: 3 `plan`** (+1 per 429/503 retry), only with the setting on.
  - **Holiday eve** (Phase 10 D3): a weekday eve of a no-service holiday checks at the Friday
    time, 12:00 (`LastTripHome.checkTime` / `eve`; an eve on a Saturday keeps 20:00), and the
    alert adds "(Erev Pesach: service ends early)" (`LastTripAlert.eve`, `LastTripReceiver.eveNote`).

# Holiday table (Phase 10 D3)
- `core/calendar/HolidaysData.kt` is generated by `python tools/gen_holidays.py` (pure Hebrew
  calendar arithmetic, no network; `--check` compares the festivals with pyluach if installed).
  Israel's dates: one day of yom tov, the moving rules of Yom HaShoah and Yom HaZikaron /
  Yom HaAtzmaut. Covers **2026–2031**; `HolidaysTest` pins dates against hebcal.com.
- Kinds (`calendar/Holidays.kt`, a source line on each): `NO_SERVICE` (Rosh Hashana 1–2, Yom
  Kippur, Sukkot, Shmini Atzeret, Pesach 1 and 7, Shavuot), `EVE` (the day before), `YOM_KIPPUR_EVE`,
  `REDUCED` (Yom HaAtzmaut, Chol HaMoed; owner decision 2). Muslim, Christian and Druze holidays
  are not covered.
- **Who updates it:** from 2031 (`Holidays.outOfDate`) Settings shows "Holiday table out of date:
  update the app"; the boss re-runs the tool with a later `LAST` and ships a release.
- Trip tab (`present/DayNotice.kt`, `ui/screens/DayNotice.kt`, 0 requests): a banner for the day
  searched (never a time: the timetable has the last trip); with 0 options, "No service that day"
  inside a rest window (from 16:00 of a Friday or eve to 20:00 of the last rest day) with "Plan
  for Sat 20:00", or past `HORIZON_DAYS` "The timetable may not reach…" with the same weekday
  inside it. The button re-plans once. Night refresh still plans holidays (an empty answer is not
  cached).
- **Background refresh**: Trip tab re-plans every 2 min while the app is in front
  (`MainViewModel.onVisible`), quietly — failures are ignored, the selection is kept.
  Car tabs refresh only on ↻ (up to 10 requests each).
- **Offline map** (`ui/OfflineMap.kt`): one MapLibre offline region, the current view,
  zoom 10–14 (OpenFreeMap vector tiles overzoom past 14), refused above 0.5° span (~55 km).
  Keep it city-sized: OpenFreeMap is free and donation-run.
- Stops are cached in memory only (GuardedTransitApi, 1 day). A disk cache is an open
  roadmap item, not a bug.
