# Phase 10 ideas — research

Asked by the owner on 2026-10-06: "find more areas to improve and features to add; you can take
inspiration from similar apps."

**Live checks made: 4 of 5 allowed** (2026-10-06, `MotisClient.USER_AGENT` sent, n=1 each):
- `geocode` "קניון עזריאלי" (he) → the mall comes first (PLACE), then two of its bus stops.
  `geocode` "Azrieli Mall Tel Aviv" (en) → "Azrieli Mall", "Azrieli Center", "Azrieli Sarona"…
  So big landmarks are found in both languages. A bundled landmark list is not needed.
- `stoptimes` for stop `il-Israel-MOT_37314`, `n=60`, from Thu 8 Oct 08:00 → **61 departures
  from 08:10 to 17:48**, plus `nextPageCursor`. Ten hours of one stop fit in one request; a whole day takes 1–2.
- `plan` on Thu 22 Apr 2027 (Pesach day 1), Tel Aviv → Haifa → **0 itineraries**. Either the
  holiday has no service or the timetable does not reach that far. The answer does not say
  which. Nothing that needs holiday dates months ahead can lean on the server alone.

**Apps looked at:** Moovit, Google Maps, Citymapper, Transit, Öffi, KDE Itinerary, OneBusAway,
Rav-Kav Online, Israel Railways. Sources: their feature pages and public reviews (Moovit's
"get off" reminder and offline maps are what reviewers praise most; Citymapper's cost
comparison and Transit's "nearby departures on open" and GO reminders; Google Maps' status-bar
live trip). Moovit's own holiday notices
([Rosh Hashana 2025](https://updates.moovit.com/rosh-hashana-2025-eng/)) show how much Israeli
users rely on holiday timetable news.

Ideas 1–10 are ranked. Nine are 0 requests or 1 request per tap. Ideas 4, 5 and 10 were
already in `phase9-ideas.md` and were not picked then. They are kept only where the notes say
what is new.

---

## 1. Share this trip, and "send my arrival time" — S
**User gets:** one tap on the selected option sends plain text to WhatsApp or SMS: the legs,
times, stops and the arrival time. A second button sends a short "I arrive at 18:42, Rager
stop" line for the person waiting. A third adds the trip to the phone's calendar.
*Done by:* Google Maps, Moovit, Citymapper (share trip); KDE Itinerary (calendar export).
*New since phase 9 (idea 1 there):* the ETA line and the calendar button.
**Data:** none. The `Itinerary` is on the phone. Text goes through `ACTION_SEND` (the pattern
in `MainActivity.shareCrashLog`). Calendar uses an `ACTION_INSERT` intent to the Calendar
app, so no calendar permission is needed.
**Requests:** 0.
**Effort:** S. A tested `ItineraryText` formatter in `present/` (Hebrew + English), buttons in
`TripPanel`, strings.
**Risks / open:** none on data. Open: add a Waze link to the first stop? (reuse `NavLinks`).
A text with the user's location in it is shared only when they tap.

## 2. Open places from other apps (share an address or pin into the planner) — S–M
**User gets:** a friend sends an address in WhatsApp, or a pin from Waze or Google Maps. The
user taps Share → "Transit Planner" and the destination is filled in. A `geo:` link works
the same way.
*Done by:* Citymapper, Transit and Google Maps (they all accept shared places).
**Data:** the shared text. A `geo:lat,lon` link or `lat,lon` in a Waze/Maps URL needs no request.
Plain address text needs 1 `geocode` (cached a day). Short links (`maps.app.goo.gl`) would
need a request to Google, so they are **not** resolved: the app says "paste the address".
**Requests:** 0–1 per share.
**Effort:** S–M. Manifest `intent-filter` for `ACTION_SEND text/plain` and `geo:`/`waze.com`
URLs on `MainActivity` (it has only the MAIN filter today), a tested `SharedPlace` parser in
`present/`, one ViewModel hook.
**Risks / open:** the manifest and `MainActivity` are shared files, so schedule it away from
other packages that touch them. Open: is the "short link, paste the address" message
acceptable? It is the price of "no Google calls".

## 3. Holiday heads-up — M
**User gets:** pick a day that is a holiday or a holiday eve and the app says it plainly:
"Tue 21 Oct is Yom Kippur: no buses or trains until about 20:00 that evening", or "Erev Rosh
Hashana: buses run the Friday timetable, trains stop about 16:00". It also covers the open
item in `docs/agents/boss-handoff.md` (the 19:00 last-trip check on a weekday holiday eve).
*Done by:* Moovit (holiday notices every year); Israel Railways and MOT publish the same rules.
**Data:** a bundled table of holiday dates for about 5 years, made by a tool in `tools/`
(like `gen_rail_stations.py`) and tested in `core/`. Why bundled: the live check shows the
timetable can answer "0 options" for a holiday months ahead with no reason given.
Public rules (from MOT and the operators, via news pages): on a holiday eve buses follow a
Friday timetable and trains run a short day (about 06:00–13:00 or 16:00); on the holiday
itself buses and light rail do not run, and on Yom Kippur nothing runs until the evening.
**Requests:** 0.
**Effort:** M. `core/` (`HolidayCalendar`, `present/` text, tested), one banner on the Trip
tab and the reminder text, strings.
**Risks / open:** accuracy is the whole risk. Owner decides: which holidays are listed (all
Jewish holidays, or only the ones with no service), and who checks the table each year.
Needs a one-line source for each rule so the tester can verify it. Muslim, Christian and
Druze holidays change local service, and are not covered.

## 4. Home-screen widget "My lines" — M (from phase 9, not built)
**User gets:** a widget with the next 3 departures of the starred lines at the home stop.
No need to open the app. Tap opens the stop sheet.
*Done by:* Transit and Google Maps (widgets); Öffi (favourite stops widget).
**Data:** `stoptimes` for the starred stops; the My lines cache already holds the last board.
**Requests:** at most 1 per starred stop per refresh. Refresh only on tap and when the
widget updates (not more than every 30 min, only on Wi-Fi or mobile data, never at night).
Budgeted in a `BudgetedTransitApi` with a tested cap, like the night refresh.
**Effort:** M. `android/` only (RemoteViews or Glance, a `data/*Store` read).
**Risks / open:** a widget that refreshes on a timer is the first background traffic that
runs all day. Owner decides the cap (suggest 4 requests a day per widget) and whether it is
tap-to-refresh only, which is safer for the Transitous policy.

## 5. "Departures near me" board — M (from phase 9, not built)
**User gets:** a button that lists the next buses and trains from every stop within 300 m,
grouped by line and direction. The first screen of Transit, and the one thing the app does
not have for someone who just walked out of a building and has no destination yet.
*Done by:* Transit (its home screen), Moovit (nearby), Öffi.
**Data:** `GET /api/v6/stoptimes?center=lat,lon&radius=300&n=…`; verified live in phase 9.
**Requests:** 1 per open, cached 30 s by the guard.
**Effort:** M. `api/` (`stopTimesAround`), `present/` grouping (tested), a new sheet.
**Risks / open:** MOT times are scheduled, so the board says "scheduled". Saturday-night
trips appear twice (dedupe by line + time, see `transitous-api`).

## 6. Tight-transfer warning — S
**User gets:** an option with a short change gets a visible warning: "Change at Savidor in 3
min". The user can set "keep transfers at least 5 / 8 / 10 min" in Settings. Today the
user can only see the numbers by reading the leg times.
*Done by:* Google Maps and Moovit warn about short or risky connections; Citymapper filters.
*New since phase 9 (idea 3, the buffer setting):* the warning badge itself.
**Data:** none for the badge (the gap between one leg's arrival and the next leg's departure
is already in the plan). The setting uses `additionalTransferTime` (already sent from
`TransitApi.kt`).
**Requests:** 0 (the setting only changes the existing plan call).
**Effort:** S. A `present/` function (tested), a chip style, a Settings row, strings.
**Risks / open:** with scheduled-only times a 3-minute change at a big station is a real
risk, so the default threshold matters. Owner decides: warn under 5 min or under 8?

## 7. Day timetable for a line at a stop — S–M
**User gets:** in a stop's departures, tap a line and see **all its departures today**
("06:10 · 06:40 · 07:05 …"), and tomorrow with an arrow. This is how many Israelis still think
about a bus: "when is the first one, when is the last one?".
*Done by:* Moovit ("lines" tab), Öffi, OneBusAway (stop schedule).
**Data:** `GET /api/v6/stoptimes` with `n=60` and `pageCursor`. **Verified live:** one stop,
61 departures over ten hours (08:10–17:48) in one request; the answer carries
`nextPageCursor` for more. Filter to one line on the phone.
**Requests:** 1–2 per open, cached 30 s.
**Effort:** S–M. `api/` (page cursor on `stopTimes`; it exists on `plan`), `present/` (group
by line and hour), `StopSheet`.
**Risks / open:** a big hub has several hundred departures a day, so paging may take 3–4
requests. Cap at 2 pages and say "more" with a button. Times are scheduled.

## 8. Live Updates notification for the ride and the leave countdown — M
**User gets:** on Android 16 and later, "On the bus" and the leave-now countdown show in the
status bar and on the lock screen as a progress bar with the stops left. Older phones keep
today's notification.
*Done by:* Google Maps (navigation), Citymapper.
**Data:** none beyond what `RideTracker` already knows. Android's own `ProgressStyle` template.
**Requests:** 0.
**Effort:** M. `android/remind/` and `ride/` only. Needs `compileSdk`/`targetSdk` 36 (now 35),
which changes the build, so it is a toolchain decision.
**Risks / open:** only Android 16 phones benefit, and Samsung rolls it out later. Owner
decides if a build bump is worth it for the few friends on Android 16. Test on an emulator
image for API 36 in CI first.

## 9. Voice search for the destination — S
**User gets:** a microphone icon in the search field: say "תחנה מרכזית תל אביב", the text is
filled and searched. Useful while walking or with a hand full.
*Done by:* Google Maps, Waze, Moovit.
**Data:** Android's `RecognizerIntent` (the phone's own speech service; no key in our app).
The recognised text goes to `geocode` as typed text does.
**Requests:** 0 extra (the same single geocode).
**Effort:** S. `SearchEditing.kt` (one icon, one activity result), strings; hide the icon if
no speech service exists.
**Risks / open:** the speech service belongs to the phone (often Google) and may send audio
to its own server. That is the phone's setting, not our traffic, but the first-run text
should say so. Hebrew recognition quality varies by phone.

## 10. Bike start — M (from phase 9, not built)
**User gets:** "Bike start": cycle up to 10/15/20 min to a station, then ride transit,
ranked on the same Pareto list as the car features.
*Done by:* Citymapper (bike + transit), Google Maps.
**Data:** `plan` with `preTransitModes=BIKE&maxPreTransitTime=…`; verified live in phase 9
(Be'er Sheva). Rentals are **not** possible (Tel-O-Fun has no feed, see `dead-ends`), so it is
the user's own bike.
**Requests:** ≤ 4 per search (same ladder as Better start).
**Effort:** M. A `BikeStart` in `features/` on the shared Pareto engine, one tab or mode.
**Risks / open:** the mode row is already tight on small phones. Folding it into Better
start as a "car or bike" switch avoids a sixth icon. Bike lanes and hills are not known.

---

## Areas to improve
1. **Accessibility.** The main app code has only about 11 `contentDescription` uses in 6 files.
   Leg chips, the map, the sort chips and the stop sheet have not been checked with TalkBack
   in Hebrew. Do one pass, add a TalkBack journey to the emulator tests, and check touch
   targets of 48 dp.
2. **Say "scheduled" everywhere.** Users who come from Moovit expect live times. For MOT lines
   there are none, so show one small "Scheduled times" label on the option list and the stop
   sheet, once per session, instead of only in the vehicle sheet.
3. **Far-future dates.** The live check showed a plan 6 months ahead gives 0 options with no
   reason. Show "the timetable may not reach this date" and offer the nearest day that has
   service. 0 extra requests.
4. **Search ranking.** Short Hebrew words and English street names find the wrong place (see
   `transitous-api`). Re-rank on the phone: saved places, Home and recent trips first. 0
   requests, and it works with the existing geocode answer.
5. **UI findings still open.** `ROADMAP.md` still has the owner item "pick the order of the
   UI fixes" and the Pixel checks for Phases 8 and 9. F1 (results cover the From/To rows)
   and F3 (almost no map visible) hit most screens; check which are still true on v0.8.0.
6. **Owner phone checks.** Phase 8 and 9 features are not ticked as "checked on the Pixel
   against live Transitous". Do that before adding more.

## Considered and dropped
- **Crowd reports and community edits (Moovit, Transit GO)** — needs a server and accounts.
- **Live delays and service alerts for MOT lines** — `dead-ends`: Transitous has no MOT feed.
- **Ticket payment inside the app (Moovit)** — needs an operator or MOT agreement.
- **Rav-Kav balance by NFC** — not verified that a third-party app may read the card; the
  official Rav-Kav Online app already does it.
- **Bundled landmark list for search** — live check: big landmarks are already found in
  Hebrew and English.
- **Reach map / meet halfway and Line explorer (phase 9 #8, #9)** — still parked: large
  downloads (1.86 MB one-to-all) or an experimental endpoint, low daily value.
- **Taxi / ride-hail price (Citymapper)** — no free price source (`dead-ends`: no taxi price).
- **Augmented-reality walking (Google Live View)** — needs Google's services and keys.

## Recommendation
Build **1, 2 and 6** first (all S, 0 to 1 request, they touch the Trip tab and the manifest,
so one package). Then **3** (the holiday heads-up), because it is the only idea that fixes
a daily Israeli pain point with no network. Take **4 and 5** together next if the owner
wants the app to be useful before a trip is planned.
