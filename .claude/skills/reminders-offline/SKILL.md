---
name: reminders-offline
description: The "time to leave" reminder (exact alarms, real-time re-check, boot re-arm, notifications) and the offline fallback (cached Trip results, the offline map area). Load before editing core/remind, core/plan/PlanCache, android remind/, data/PlanCacheStore, or ui/OfflineMap.
---

# "Time to leave" reminders

- **Logic is in `core/remind/Reminder.kt`** and tested: `Reminder.from(itinerary, …)` sets
  `leaveAt` = itinerary start (when to start walking); `recheckAt` = 15 min earlier.
  `ReminderLogic.update` finds the same vehicle in a fresh plan — by `tripId` when both sides
  have one, else line + boarding stop + scheduled time ±1 min — and returns `Updated`
  (new leave time) or `Gone` (cancelled / no longer offered).
- **One active reminder**, stored as JSON in `UserStore` (`UserJson.encodeReminder`).
- **Alarms** (`remind/ReminderScheduler.kt`): two exact alarms, re-check and leave.
  `USE_EXACT_ALARM` (API 33+) is fine because the app is sideloaded — it is a Play-policy
  restriction, not a platform one; if the app ever goes to the Play Store this must change.
  `SCHEDULE_EXACT_ALARM` is declared only up to API 32. If exact alarms are refused, an
  inexact `setAndAllowWhileIdle` is used rather than nothing.
- **The receiver never loses a reminder on a bad signal**: a failed re-check keeps the
  original time. `Gone` posts "your trip changed" and clears it.
- **Reboot clears alarms** → `BootReceiver` re-arms (or drops a reminder already past).
- `POST_NOTIFICATIONS` is asked the first time a reminder is set (API 33+). A refusal still
  arms the alarm; only the banner is blocked.
- **Trip tab only.** Car-feature legs have different "leave" semantics (the driver leaves).

# "Get off at the next stop" (`core/ride/RideTracker.kt`, `android/.../ride/RideService.kt`)

- Tracker is pure and tested with synthetic tracks: per transit leg it fires `Approaching`
  ONCE — within 120 m of the stop before yours (if MOTIS listed intermediate stops) or 400 m
  of your stop — then `Finished` at the destination or 30 min past planned arrival.
- The service is a `location` foreground service that exists only during a ride; it uses
  the platform `LocationManager` (GPS + network, 5 s / 15 m), not Play services.
- **The `LocationListener` is an explicit object, never a lambda**: below API 29 its other
  methods are abstract and a SAM lambda crashes with AbstractMethodError.
- Starting a ride also writes a `TripRecord` to the history (`data/HistoryStore`,
  `filesDir/history.json`, newest first, capped at 500 — `core/history/History.kt`).

# Offline

- **Trip results**: `core/plan/PlanCache` (LRU, 10) + `TripCacheJson`, persisted by
  `data/PlanCacheStore` in `filesDir/trip_cache.json`. Key = tab + time mode + places rounded
  to 3 decimals (~110 m), so "my location" a few steps away still hits. On a NETWORK error
  the Trip tab shows the cached entry with an "Offline — saved at HH:MM" banner. Car tabs
  are not cached: their whole point is live combinations.
- **Background refresh**: Trip tab re-plans every 2 min while the app is in front
  (`MainViewModel.onVisible`), quietly — failures are ignored, the selection is kept.
  Car tabs refresh only on ↻ (up to 10 requests each).
- **Offline map** (`ui/OfflineMap.kt`): one MapLibre offline region, the current view,
  zoom 10–14 (OpenFreeMap vector tiles overzoom past 14), refused above 0.5° span (~55 km).
  Keep it city-sized: OpenFreeMap is free and donation-run.
- Stops are cached in memory only (GuardedTransitApi, 1 day). A disk cache is an open
  roadmap item, not a bug.
