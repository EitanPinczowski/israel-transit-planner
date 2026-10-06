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
  FREE profile, today's profile for the whole month. Medians, never means.

# Offline
- **My lines offline**: each successful board is saved per stop (`core/present/DepartureCache`,
  `data/DepartureCacheStore`, `filesDir/departures_cache.json`, 20 stops). With no signal the
  saved board shows departures still ahead only, marked "Offline — timetable saved at HH:MM",
  never a delay or cancellation.

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
  - **Worst case per day: 6 `plan`** (`BudgetedTransitApi(6)`), + 1 each on a 429/503 retry.
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
  NOW from my location (waits ≤ 3 s for a fix, else the cached entry's origin), **1 `plan`**,
  no last-ride hint; offline → the cached entry under the usual banner; no Home → Settings.
  Settings → "Add to Quick Settings" (`requestAddTileService`, API 33+; a hint below).
- **Background refresh**: Trip tab re-plans every 2 min while the app is in front
  (`MainViewModel.onVisible`), quietly — failures are ignored, the selection is kept.
  Car tabs refresh only on ↻ (up to 10 requests each).
- **Offline map** (`ui/OfflineMap.kt`): one MapLibre offline region, the current view,
  zoom 10–14 (OpenFreeMap vector tiles overzoom past 14), refused above 0.5° span (~55 km).
  Keep it city-sized: OpenFreeMap is free and donation-run.
- Stops are cached in memory only (GuardedTransitApi, 1 day). A disk cache is an open
  roadmap item, not a bug.
