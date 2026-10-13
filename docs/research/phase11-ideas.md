# Phase 11 ideas — research

Asked by the boss on 2026-10-13, after Phase 10 (v0.9.0): propose 5–10 ranked ideas that make
the app more useful for a **daily Israeli rider** (bus, Israel Railways, light rail, plus the
car + transit features). Areas named: trust in the answers, accessibility, family use and
sharing, commute routines, onboarding a friend who just installed the APK, battery and data
use, and gaps in the current app.

**How this was made.** Read `CLAUDE.md`, `team.md` (Researcher), `dead-ends`, `transitous-api`,
`ROADMAP.md` (Phases 0–10), `phase9-ideas.md` and `phase10-ideas.md`, then looked in the code
(`ui/screens/` names, `strings.xml`, `AndroidManifest.xml`, `ReminderScheduler`) to see what
exists. No web research and no other app was opened from this session; "Done by" lines for
other apps are not repeated from memory except where `phase10-ideas.md` already sourced them.

**Live checks: 4 of 5 allowed** (2026-10-13, `MotisClient.USER_AGENT` sent, ≥ 1 s apart, n=1 each):
1. `plan` Tel Aviv Savidor → Haifa, Wed 14 Oct 08:00: 4 itineraries (train + bus). Legs carry
   `agencyName` and `bikesAllowed`. Itineraries carry **no fare data** (`fareTransfers` absent).
2. `plan` Petah Tikva → Bat Yam, same time, `transitModes=TRAM`: 4 itineraries, all on the
   **Red Line** (`mode` TRAM, `routeShortName` "1", operator תבל). Light rail is in the data.
3. `plan` Tel Aviv → Haifa, Wed **16 Dec 2026** 08:00 → **0 itineraries**.
4. `plan` Tel Aviv → Haifa, Wed 17 Feb 2027 08:00 → **0 itineraries**.
   So the loaded timetable ends somewhere before mid-December (about 9 weeks out). Two points
   do not give the exact end date. Phase 10 D3 already covers the "may not reach this date"
   message; this only confirms the horizon is **short**, which matters for idea 7.

Across the 15 transit legs in checks 1–2 `bikesAllowed` is `false` on every one, **including
Israel Railways**, where bikes are in fact carried. The field means "no information" here, so
the app must not turn it into a "no bikes" warning (added to Checked and dropped).

Ideas are ranked by daily value for the effort. **Returning ideas** (earlier picks or earlier
research that the owner did not pick) are marked and say what is new.

---

## 1. "Will my reminder ring?" — a reliability check — S
**User gets:** in Settings, one row, "Check reminders". It tests what a leave reminder or a
get-off alert depends on and says in plain words what is wrong: notifications off for the app
or for a channel; battery restriction on this phone ("Sleeping apps" on Samsung, "Battery saver
→ No restrictions" on Xiaomi/Huawei, and the stock "Unrestricted" setting); location denied for
the ride alert. Each problem has one button that opens exactly that system page. A **Test
reminder in 1 minute** button fires a real alarm through the same path so the user hears it
once, at a calm moment, not on the morning that matters. After the first reminder is set, a
one-time hint offers the check. Example: a friend on Xiaomi sets "leave at 07:40", the phone
kills the app overnight, and nothing rings. The check would have shown "Battery: restricted".
**What exists:** `USE_EXACT_ALARM` is declared (granted on API 33+; `SCHEDULE_EXACT_ALARM` on
≤ 32), so exact alarms are not the gap. `ReminderScheduler` quietly falls back to inexact when
exact is not allowed. Only `LastTripSettings` checks `areNotificationsEnabled()`. Nothing tells
the user that reminders are at risk, and nothing in `strings.xml` mentions battery.
**Data:** none. `NotificationManager`, `PowerManager.isIgnoringBatteryOptimizations`,
`AlarmManager`, runtime permission state; settings pages via `ACTION_APP_NOTIFICATION_SETTINGS`
and `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (opens the list, so no extra permission and no
Play-policy question for a sideloaded APK).
**Requests:** 0.
**Effort:** S. A tested pure `ReminderHealth` in `remind/` (inputs → list of problems, Hebrew +
English text), one Settings row, one test-alarm action, strings.
**Risks / open:** OEM battery pages differ and change; the app can only open the generic page
and print the vendor's name, and **I could not test any vendor's behaviour from the cloud**. The
"killed overnight" failure is the usual reason such alarms fail in the field, but it needs one
real report from a friend's phone before the wording is final. No privacy cost.
**New outside service:** no.

## 2. Share this trip — and "I arrive at 18:42" — S  *(returns; not picked in Phases 9 or 10)*
**User gets:** one tap on the selected option sends text through WhatsApp/SMS: legs, times,
stops, arrival. A second button sends a short line for whoever is waiting: "I arrive 18:42 at
Rager stop, line 47 · Waze: …" with the Waze/Maps link to that stop, so a parent knows when to
leave to pick up. A third adds the trip to the phone's calendar (`ACTION_INSERT`, no calendar
permission). This is the family-use gap: today only drop-off and pick-up have a share text
(`share_dropoff`, `share_pickup`), and a plain trip has none.
**What is new since Phase 9/10:** the ETA line with a stop link, and an aim at the family case
(the owner's own three car features already end with "send to driver"). Leaving out of the
text anything the user did not choose is a rule: no Home address unless the destination *is*
Home and the user taps share.
**Data:** none; the `Itinerary` is on the phone. Pattern: `MainActivity.shareCrashLog`.
**Requests:** 0.
**Effort:** S. A tested `ItineraryText` in `present/` (he/en, reuse `NavLinks`), buttons in
`ActionRow`/`TripPanel`, strings, one release-note fragment.
**Risks / open:** none on data. Open: should the text include fares? (Suggest no.)
**New outside service:** no.

## 3. "I missed it" / "Running late" — re-plan from where I am — S–M
**User gets:** a button in the ride notification, in the "trip changed" notification and in the
trip sheet: **Missed it? Plan from here.** It takes the phone's GPS fix now, plans to the same
destination departing now, and opens the result. Example: the 07:42 bus is gone; one tap shows
"next: 07:58, arrives 08:41", with the same settings and sort. For a train commuter the
alternative is to re-type the destination while standing on a platform.
**What exists:** C1's "If I miss this: next at 08:35 (+30 min)" is a static line from loaded
options on the *planned* start; the leave reminder only says the trip changed; `RideTracker`
has the GPS fixes. I found no "re-plan from here" string or action.
**Data:** `plan` from `lat,lon` to the original destination. **Requests:** 1 per tap (guard
cache and retry as always). Foreground tap only, never automatic.
**Effort:** S–M. `MainActions` (re-plan with a start override), `remind/Notifications` and
`ride/RideService` action buttons (shared files, so sequence this after the notification work
of D10), `present/` text. Core is a thin wrapper over `TripPlanner`.
**Risks / open:** location is read only on tap (foreground permission is already asked for the
ride alert; without it, the button asks for the start). MOT times are scheduled, so "missed"
is the rider's own judgement. No new service.
**New outside service:** no.

## 4. Backup, restore and "set up Dad's phone" — S–M
**User gets:** Settings → **Back up my data**: one JSON file (saved places, Home/Work, saved
trips, routines, My lines, fare profile, settings) through the Android share sheet or the file
picker. **Restore** on a new phone reads it back. Same file lets the owner set up a parent's
phone: export once, send over WhatsApp, open with the app. Smaller form: share a *single saved
place* as text; D6 already accepts `geo:` and Waze/Maps links, so a place shared this way
arrives pre-filled on the other phone.
Example: a friend gets a new phone and loses six saved places and the "University: Sun–Thu"
routine; with a file it takes 20 seconds.
**What exists:** `UserJson` already encodes the user data (`UserJsonTest`) for DataStore, and
D8 added a CSV/text report. There is no backup, export or restore string in `strings.xml`.
**Data:** none. The file is made on the phone and goes only where the user sends it.
**Requests:** 0.
**Effort:** S–M. `UserJson` versioning + a tested merge rule ("replace" vs "add to"),
`ActivityResultContracts.CreateDocument/OpenDocument`, Settings rows, strings. Android's own
auto-backup (`allowBackup`) is a free, zero-code alternative for Google-account phones; check
what the manifest says before building anything.
**Risks / open:** the file holds Home and Work, so the share sheet must show a one-line
warning, and nothing is uploaded by the app. Versioning of the JSON must be tested both ways
(an older app reading a newer file). Owner decides: "replace" vs "merge" on restore.
**New outside service:** no.

## 5. Departures near me — the start screen for someone with no destination — M  *(returns: Phase 9 #6, Phase 10 #5, not picked)*
**User gets:** a **Nearby** button (and a card in the first-run tour) listing the next buses and
trains from every stop within 300 m, grouped by line and direction, "scheduled" labelled. Tap a
row to open the stop sheet or plan from there. Example: a friend steps out of a building in a
city they do not know, and sees "47 → Savidor · 4 min, 18 → Azrieli · 9 min".
**What is new:** the framing. For someone who just installed the APK it answers "does this
thing work here" before they type anything, and it is also the data for idea 6. Phase 10's
"Departures near me" notes on dedupe (Saturday-night trips appear twice) still apply.
**Data:** `GET /api/v6/stoptimes?center=lat,lon&radius=300&n=…`, verified live in Phase 9
(not re-checked this round).
**Requests:** 1 per open, cached 30 s by the guard.
**Effort:** M. `api/` (`stopTimesAround`), `present/` grouping, a sheet, strings.
**Risks / open:** location permission on first use; the board says times are scheduled
(idea 7). No new service.
**New outside service:** no.

## 6. Accessibility pass with a TalkBack journey — M  *(returns: Phase 10 "areas to improve" #1, not picked)*
**User gets:** the app usable with TalkBack in Hebrew and at 200 % text, for a parent or a
visually impaired rider. Examples: the sort chips say "Fastest, selected"; a leg chip reads "Bus
47, 08:10 to 08:34" not "47 08:10 08:34 ▶"; the map is skipped as decoration while the list
is the main path; touch targets ≥ 48 dp; colour is never the only signal (leg colours also get a
mode word or icon).
**What exists:** `contentDescription` appears about 17 times in 8 files under `ui/screens/` and
`MainScreen.kt`, a small number for a Compose app with 30+ screen files. The Phase 10 note
said "11 in 6 files"; it has grown with Phase 10, but chips, the stop sheet, trip details and the
sort row were not individually checked.
**Data:** none. **Requests:** 0.
**Effort:** M. Semantics (`contentDescription`, `stateDescription`, `Role`, `mergeDescendants`)
across screens, a tested `present/` speech formatter for time ranges, and one emulator journey
that turns on Espresso accessibility checks (`espresso-accessibility`, free) plus a 200 % font
profile in `ui-testing`'s layout audit.
**Risks / open:** touches many UI files, so it is **sequenced after** other UI packages, or done
screen by screen. I cannot run TalkBack from the cloud: the test is in CI, and a real device is
needed for tone and reading order.
**New outside service:** no.

## 7. Trust label and "Something looks wrong" — S
**User gets:** two small things so answers can be trusted and bad ones become fixes.
(a) One calm label, once per session and on the stop sheet: **"Scheduled times — Israeli bus
and train lines have no live data here"**, with a "why?" line that links to
`https://transitous.org/sources/`. Friends who come from Moovit expect live arrivals; today
the app says it only in the vehicle sheet. (b) A **Report a wrong answer** row on an option or
a stop: it builds a text with the query (from, to, time, settings), the answers' summary, the
app version and the data horizon, and opens the share sheet. The user sends it to the owner by
hand, the same pattern as "Share crash log". The owner turns it into a golden trip or a fixture.
**What exists:** no "scheduled" string in `strings.xml`; Phase 10 listed this as an area but did
not pick it. Live checks 3–4 show the timetable horizon is short, so (b)'s text records which
date was asked.
**Data:** none. **Requests:** 0.
**Effort:** S. A tested `ReportText` in `present/`, a label in `TripPanel`/`StopSheet`, strings.
**Risks / open:** the report may contain place names the user asked for; it is only shared by
the user's tap. Open: who triages reports? (The owner, from WhatsApp; no server.)
**New outside service:** no.

## 8. Home-screen widget "My lines" — tap-to-refresh only — M  *(returns: Phase 9 #5, Phase 10 #4, not picked)*
**User gets:** a widget with the next three departures of the starred lines at the home stop,
readable without opening the app. Tap opens the stop sheet; a small ↻ refreshes.
**What is new:** the open owner question from Phase 10 (a background refresh cap) is answered
by **never refreshing on a timer**. The widget draws from the My lines cache and the night
refresh (D7 already saves starred-stop timetables), shows "as of HH:MM", and asks Transitous
only on the ↻ tap: at most 1 request per starred stop, `BudgetedTransitApi`, 6 a day per widget.
**Data:** `stoptimes` for starred stops; the offline timetable store from D7.
**Requests:** 0 to draw; ≤ 1 per starred stop per ↻ tap.
**Effort:** M. `android/` only (Glance or RemoteViews), `data/*Store` read, a tested "next three
from a saved board" function in `present/`.
**Risks / open:** an out-of-date widget looks like a wrong answer, so "as of" and "scheduled"
must be on the face. Owner decides the daily cap. Not worth building before ideas 1–4.
**New outside service:** no.

## 9. Data and battery card — S
**User gets:** Settings → **Data & battery**: "Today: 14 requests to Transitous (6 from cache),
map tiles 3.2 MB, background: night refresh ran at 02:14 (4 requests), ride GPS 12 min." One
switch, **Low-data mode**, that turns off the stops-layer prefetch, shrinks the tile cache
write and keeps the night refresh on Wi-Fi only. It lets a friend on a 5 GB plan, or the owner
checking the Transitous policy, *see* that traffic is light.
**What exists:** `TileSettings`, `NightRefreshSettings`, `GuardedTransitApi`'s cache counters
(not shown to users). No usage surface.
**Data:** none. Counters in memory and a small persisted day total.
**Requests:** 0.
**Effort:** S. A tested `UsageLog` in `core/api` (counts per feature per day, local only), a
Settings card, strings.
**Risks / open:** none on privacy: counts only, no places. Lowest value of the nine; do it if
the owner wants to prove light traffic before asking Transitous about routine use again.
**New outside service:** no.

---

## Checked and dropped this round
- **"No bikes on this train" warnings from `bikesAllowed`** — live check: `false` on all 15
  transit legs, Israel Railways included. The field is "no information" here. Do not use it.
- **Fares from the data** (`fareTransfers`) — absent in the Israel answers; `FareTable` stays.
- **Bike start (Phase 10 #10)** — not revived. Nothing new, and `bikesAllowed` can't tell a
  rider whether a bike fits on a train (a bike-and-ride feature would need a rules table with
  an owner-checked source).
- **Any live location sharing ("follow my trip")** — needs a server (`dead-ends`: own server).
  Idea 2's text covers the family need without one.
- **Service alerts / crowd reports / live delays** — `dead-ends`: no MOT real-time on
  Transitous; unchanged.
- **Light-rail-specific work** — checked: the Red Line is in the data and the app already has
  `LegKind.LIGHT_RAIL`. Nothing missing was found; I could not check Jerusalem's line.

## Areas looked at with nothing new to propose
- **Onboarding.** `WelcomeTour` exists (C6) and is skippable. Ideas 5 and 1 improve its first
  minute (a result before typing, a reminder test); no separate wizard is proposed.
- **Commute routines.** Routines (Phase 6), C2/C3/C4/C5 cover leave time, insights, tile,
  last-trip alert. Idea 3 is the one open hole (what to do once the commute goes wrong).

## Recommended first wave
Build **1, 2 and 3**, in that order, and take **4** next if the owner wants it before a
release.

- **1 — Reminder check.** The leave reminder and get-off alert are the app's promise, and the
  likeliest silent failure for a new friend is a phone that kills the app. S, 0 requests, no
  shared UI files beyond Settings.
- **2 — Share this trip.** The cheapest family feature, asked for in two research rounds, 0
  requests, S. It also gives Phase 11 an easy first release note.
- **3 — "I missed it".** Fixes the worst moment of a daily commute with 1 request on tap. It
  shares `remind/` and `ride/` files with D10, so it starts after D10 merges.
- **4 — Backup and restore** after those, because it protects everything the other features
  save (places, routines, My lines), and makes setting up a parent's phone a one-file job.

Ideas 5 and 8 stay available if the owner wants the app to be useful before a trip is planned.
Idea 6 is large and cross-cutting: schedule it once the UI work has quietened, not in parallel.
Idea 7 is small; the "Scheduled times" label can ride with any package that touches `StopSheet`.
Idea 9 last.

## Owner decisions this list needs
1. Idea 1: is a friend's real-phone report (Samsung/Xiaomi) available to confirm the wording?
2. Idea 4: "replace" or "merge" on restore; accept the one-line warning about Home in the file.
3. Idea 8 (if picked): the daily cap per widget (suggest 6 and tap-to-refresh only).
4. Idea 6 (if picked): which phone and TalkBack language the owner can test on.
5. No idea here needs a new outside service. All of them stay inside CLAUDE.md's hard rules.
