# Phase 10: the brief each agent session gets

The boss starts one cloud session per package. The prompt it sends is the **Common preamble**
followed by that package's brief, word for word as written below. If you re-run a package,
use the same text. When the plan changes, update this file in the same PR.

The scope is the owner's picks in `docs/research/phase10-ideas.md`: the sections "Owner's picks
(2026-10-07)" and "Owner's additions (2026-10-07 and 2026-10-11)". Item 20 (recent and saved
places first) shipped in v0.8.5, and S1–S3 shipped with the search fix (#51). The rest is
grouped into eleven packages:

| package | items | wave | requests | review | cost |
|---|---|---|---|---|---|
| D1 Ride details + transfers | 26 ride details; 6 tight-transfer warning + buffer; collapse long stop lists | 1 | 0 | Sonnet designer | $12–18 |
| D2 Search hardening | cancel stale searches; acronyms; neighbourhood → town; match levels; fast JVM address corpus | 1 | 0 new (≤ 60 geocodes to record) | Sonnet logic | $15–20 |
| D3 Holidays + dates | 3 holiday heads-up; 25 "the timetable doesn't reach this date" + nearest day with service; holiday eve in the last-trip alert | 2 | 0, 1 per tap | Sonnet logic + designer | $15–20 |
| D4 Test hardening | network-fault tests (429/503/slow/cancel); routing invariants | 2 | 0 | none (core tests) | $5–8 (Sonnet) |
| D5 Big-stop changes | 27 prefer a big stop for a change of line, same two vehicles | 3 | ≤ 2 per opened option | Sonnet logic | $12–18 |
| D6 Ways in | 2 open places shared from other apps; 9 voice search; 11 search by stop number | 3 | 0–1 per use | Sonnet logic | $12–18 |
| D7 Stop timetables | 7 day timetable for a line at a stop; 18 offline timetables for starred stops (night refresh) | 4 | 1–2 per open; ≤ 3 a night | Sonnet designer | $15–20 |
| D8 Fares + report | 12 fare profiles (senior / student / youth / soldier); 13 monthly travel report file | 4 | 0 | Sonnet logic | $12–16 |
| D9 Rain-aware walking | 19 rain from Open-Meteo on the walking parts (new outside service) | 5 | 1 Open-Meteo per search, cached 1 h | Sonnet designer | $10–15 |
| D10 Live Updates | 8 ride + leave countdown as an Android 16 Live Update | 5 | 0 | Sonnet logic | $15–20 |
| D11 App polish | keep state across process death; map ↔ list highlighting; thumb-zone actions + haptics | 6 | 0 | Sonnet designer | $15–20 |
| D12 Spare minute + sort everywhere | owner, 2026-10-12: ≥ 1 min to spare at every change; Fastest / Fewest transfers / Least walking in every tab; no unreasonable chains | after D6, before D7 | 0 (1 fixture recording) | Sonnet logic | $10–15 |

Total about **$150–190** of implementer work, plus about $1 per Sonnet review.

## Order

**Usually one Opus implementer at a time** (the owner's usage limits). Each wave allows two,
but when only one can run, start the first package of the wave; the second one starts when the
first one's PR is green. Waves are ordered by daily value: what the owner uses every day comes
first.

- **Wave 1:** D1, then D2 (or both in parallel). They share no file, except `TripPanel.kt` in
  different screens: D1 edits the result cards, D2 adds one line to the search suggestions.
- **Wave 2:** D3 and D4, after Wave 1 is merged. D3 adds its banner to `TripPanel.kt` after D1.
  D4 edits only test files, plus small fixes in `api/Guards.kt` after D2.
  **Release v0.8.6** after Wave 2 (proposal): ride details, transfer warning, holidays and the
  search hardening are the daily-use part of the phase.
- **Wave 3:** D5 and D6, after Wave 2 is merged. D5 uses D1's transfer gaps and D4's invariants.
  D6 builds on D2's search code.
- **Wave 4:** D7 and D8, after Wave 3 is merged. D7 is the only API-shape change of the phase
  (`stopTimes` paging). D8 owns `fare/` and the history section.
- **Wave 5:** D9 and D10, after Wave 4 is merged. D10 bumps `compileSdk`; nothing else in the
  wave touches the build.
- **Wave 6:** D11 alone, last: it moves code in `MainScreen`, `MainViewModel` and `TripPanel`,
  which every other package hooks into.

## Shared files

These would conflict if two packages edited them at once, so each has one editor per wave.
"Hook" means at most one call or one field, placed where the brief says.

| shared file | w1 | w2 | w3 | w4 | w5 | w6 |
|---|---|---|---|---|---|---|
| `api/Models.kt`, `api/TransitApi.kt`, test `Fakes.kt` | | | | D7 | | |
| `api/Guards.kt`, `api/MotisClient.kt` | D2 (cancellation only) | D4 (fixes its tests expose, ≤ ~20 lines) | | D7 (paging pass-through) | | |
| `search/*.kt` | D2 | | D6 (`StopCode.kt` new + one branch in `PlaceSearch`) | | | |
| `ui/screens/TripPanel.kt` | D1 (cards) + D2 (one line in `SuggestionList`) | D3 (one banner line) | D5 (one line, selected card) | | D9 (one line) | D11 |
| `ui/screens/TripDetailsSheet.kt` | D1 | | | | | |
| `ui/screens/SearchEditing.kt` | | | D6 | | | |
| `ui/screens/StopSheet.kt` | | | | D7 | | |
| `ui/screens/SettingsDialog.kt` | D1 (one row) | | | D8 (fare section + report button) | D9 (one toggle) | |
| `user/User.kt` (`UserSettings`) | D1 | | | D8 | D9 | |
| `plan/LastTripHome.kt`, `remind/LastTripReceiver.kt` | | D3 | | | | |
| `plan/NightRefresh.kt`, `work/NightRefreshWorker.kt` | | | | D7 | | |
| `fare/*.kt`, `history/*.kt` | | | | D8 | | |
| `remind/Notifications.kt`, `remind/ReminderReceiver.kt`, `ride/RideService.kt` | | | | | D10 | |
| `AndroidManifest.xml` | | | D6 (filters on `MainActivity`, `<queries>`) | D8 (FileProvider path only) | D10 (one permission) | |
| `MainActivity.kt` | | | D6 | | | D11 |
| `android/app/build.gradle.kts`, `gradle/libs.versions.toml`, `.github/workflows/ui-tests.yml` | | | | | D10 | |
| `CLAUDE.md` | | | | | D9 (one hard-rule line) | |
| `ui/MapController.kt`, `ui/MainScreen.kt`, `ui/PlacePick.kt` | | | | | | D11 |
| `ui/MainViewModel.kt`, `ui/MainActions.kt` | D1, D2: one hook each | D3: one hook | D5, D6: one hook each | D7, D8: one hook each | D9, D10: one hook each | D11 (state saving) |

The owner merges every PR; agents never merge.

## Request budgets

Every new call goes through `TransitApi` (Transitous) or its own guarded client (Open-Meteo,
like `GuardedGeocoder`). Each cap is a constant pinned by a test, and each row goes into the
`transitous-api` budget table in the package that adds it.

| package | call | when | cap | pinned by |
|---|---|---|---|---|
| D1 | none (the buffer setting changes the existing `plan`'s `additionalTransferTime`) | | 0 | `TransfersTest`: the setting adds the parameter, no extra call |
| D2 | none new; a cancelled search sends nothing more | typing | 0 extra | `TypingSearchTest`: 5 fast keystrokes → 1 geocode; a stale answer never shows |
| D3 | `plan` for the offered nearest day | tap only | 1 per tap | `DayNoticeTest`: building the notice sends 0 |
| D5 | `trip(tripId)` for the two lines around one change | the user opens an option with a change at a small stop | 2 per opened option, `BudgetedTransitApi(2)` | `BigStopChangeTest`: 2 for an opened option, 0 again (guard cache), 0 for a direct trip or a big-stop change |
| D6 | `geocode` for shared address text; stop-code lookup | a share / a typed stop number | ≤ 1 per use | `SharedPlaceTest`: `geo:` and map links send 0; `StopCodeTest` |
| D7 | `stopTimes` with `pageCursor`, `n = 60` | open a day timetable | 2 pages per open, +1 per "More" tap | `DayTimetableTest` |
| D7 | `stopTimes` for starred stops | night refresh (Wi-Fi + charging) | 3 a night, its own `BudgetedTransitApi(3)` (night worst case 6 + 3 = 9) | `NightRefreshTest` |
| D9 | Open-Meteo `forecast` (not Transitous) | a Trip search for the next 48 h | 1 per search, cached 1 h per 0.1° cell, ≤ 2 at once, one retry on 429/503 | `RainAdviceTest`: second search in the same hour and cell sends 0 |
| D4, D8, D10, D11 | none | | 0 | |

---

## Reviews (owner, 2026-10-11)

- **No Gemini** (owner, 2026-10-12: its GitHub reviewer is not free). Reviews are Sonnet only.
- **A Sonnet review** runs only where the table above says, and only as a **targeted pass**:
  - *designer* (packages that change screens): from the CI `screenshots` artifact, light and
    dark, Hebrew and English, small and large phones; loads `i18n-rtl`;
  - *logic* (search, routing, alarms, fares, dates): the brief's "Reviewer checks" list and the
    `parallel-work` definition of done, nothing wider.
  The boss starts it once the PR is green. The implementer
  fixes what it finds on its own branch. Then the owner merges.

**Models.** Implementers run on **Opus**, except D4 (Sonnet). Reviews run on Sonnet.

---

## Common preamble (sent first to every session)

> You are one of several agents working on Israel Transit Planner, each on its own branch and
> PR. You run on Opus (D4: Sonnet).
>
> Before writing any code:
> 1. Read `CLAUDE.md`.
> 2. Load the `parallel-work` skill: file ownership, conflict rules, definition of done, token
>    rules.
> 3. Load `dead-ends`.
> 4. Load the skills your package names.
> 5. Read the top of `docs/agents/phase10.md`: the shared-file table says which shared files
>    you may touch and where, and the budget table pins your requests.
>
> Your package is the `###` subsection of `ROADMAP.md` Phase 10 named below. Tick only those
> lines.
>
> Work on the branch given. Merge `origin/main` before opening the PR (never rebase). Open a PR
> to `main`, subscribe to its activity, and drive it to green CI. **Do not merge it.**
>
> - **Budget:** your package should cost about the amount in the table. Keep it there: no long
>   live testing, no emulator runs beyond the one the definition of done needs, read only the
>   files your brief names plus what they call.
> - **Screens:** every screen you add or change gets a Paparazzi state in
>   `android/app/src/test/.../ui/` (Hebrew and English, light and dark), re-recorded in your PR.
> - **Release notes:** write a fragment at `docs/releases/next/<package>.md`, 1–3 bullets in
>   English, then the same in Hebrew.
> - **Requests:** every new request goes through `TransitApi` (or the guarded client your brief
>   names), and its cap is pinned by a test. A background job states its constraints and its
>   worst case per day in the PR body.
> - **Transitous traffic:** live calls are for recording fixtures only (`tools/record_fixture.py`),
>   within the number your brief gives.
> - **Review:** if your package has a Sonnet pass, the boss starts it once your PR is green;
>   fix what it finds in one round.
> - **Blocked?** If you are blocked on another package or need an owner decision, write it in
>   the PR body, tell the boss in one message, and stop. Don't work around it.
> - **Messages:** one message to the boss when the PR is green and clean against `main`, or
>   one when blocked. No progress messages.
> - **After each push:** schedule exactly one `send_later` ~20 min out that re-reads the PR
>   checks and acts on them. CI events may not reach your session; don't wait idle for them.
> - **Emulator before goldens:** get the `UI tests` workflow green first, then pull goldens
>   from that same head's CI run in a goldens-only commit (`parallel-work`).

---

## D1: Ride details + transfers
**Branch:** `claude/p10-ride-details`
**Skills:** `i18n-rtl`, `add-feature`
**Needs:** nothing (starts from `main`)
**Owns:** `present/RideDetails.kt` (new), `present/Transfers.kt` (new),
`ui/screens/RideDetails.kt` (new), `ui/screens/TripPanel.kt` (`ItineraryCard` and the selected
card only), `ui/screens/TripDetailsSheet.kt`, `strings_ride_details.xml`
**Shared-file hooks:** `user/User.kt`: `UserSettings.transferBufferMin`; `SettingsDialog`: one
"Keep transfers at least" row; `MainViewModel`: none expected (the setting flows through
`preferences()`)

> Everything here is computed from the `plan` answer on the phone. **0 requests.**
>
> **1. Ride details on every option (item 26).**
> - Each transit leg gets: stops on board (`intermediateStops.size + 1`), time on board
>   (leg end − leg start), and the get-off stop with its stop number from C1's
>   `present/StopPlatform.kt` / `Place.stopCode`: "Line 5 · 12 stops · 23 min · get off at
>   Rager/Oren (stop 47899)".
> - **Unselected cards:** one compact line for the whole option: total stops, total time on
>   board, and the final get-off stop ("17 stops · 38 min on board · off at Rager/Oren").
> - **Selected card:** one line per transit leg (at most 4; more than 4 legs shows the first 3
>   and "+N").
> - No stop number in the data: the stop name alone, no empty brackets. A train leg: no
>   "stop" number (trains have no `stopCode`), "get off at Tel Aviv Savidor".
> - `present/RideDetails.kt` (pure) builds the parts; the composable only joins strings, so
>   Hebrew and English word order live in `strings_ride_details.xml`.
>
> **2. Collapse long stop lists** (owner's additions). In the trip details sheet, a ride with
> more than 6 intermediate stops shows the boarding stop, the first stop, a row "12 stops, tap
> to expand", the last stop before yours, and the get-off stop. Tap expands, tap again
> collapses. Never collapse the stop the live vehicle is at or heading to, a cancelled stop, or
> a stop with an alert. `present/TripStops.kt` gets a pure `collapse(rows, keep)` tested in
> `RideDetailsTest`.
>
> **3. Tight-transfer warning (item 6).** `present/Transfers.kt` (pure):
> `transfers(itin): List<Transfer>` with the change stop, the slack (next transit leg's
> departure − previous transit leg's arrival − the walk between), the walk minutes and whether
> it is the same stop. Use the live time when a leg has one, else scheduled.
> - A transfer whose slack is under the warning threshold gets an amber chip on the card:
>   "Change at Savidor in 3 min" (same stop) or "Walk 4 min, 2 min to spare" (a walk between).
>   Slack ≤ 0 says "Tight: may miss the change".
> - Threshold (owner decision 1): **under 5 min**, or **under 8 min when either side is a
>   train**; never lower than the user's buffer.
> - The chip colour passes the WCAG check in `ContrastTest` in both themes; the text says
>   the risk, so colour is never the only signal.
>
> **4. Buffer setting.** Settings → "Keep transfers at least": Off · 3 · 5 · 8 · 10 min.
> `UserSettings.transferBufferMin: Int? = null` (Off = MOTIS's own minimum), passed by
> `preferences()` as `Preferences.additionalTransferSec` (already sent as
> `additionalTransferTime` by `PlanRequest.toQuery()`). It is part of the query, so the cache
> key changes with it. JSON round-trip test, and old files still decode.
>
> **Tests:** `RideDetailsTest.kt` (a leg with no intermediate stops, a train leg, no stop
> code, 5 legs, collapse rules), `TransfersTest.kt` (same stop, a walk between, a delay that
> eats the slack, slack ≤ 0, a train change at 6 min, a change past midnight, the buffer
> raising the threshold, the query carries `additionalTransferTime`).
>
> **Reviewer checks (Sonnet designer):** cards on the smallest phone profile with 3 legs in
> Hebrew at the largest font; the amber chip in dark mode; RTL order of "12 stops · 23 min".
>
> **Docs:** the `i18n-rtl` skill (the amber warning chip); a release fragment
> `docs/releases/next/d1-ride-details.md`; tick ROADMAP D1.

## D2: Search hardening
**Branch:** `claude/p10-search-hardening`
**Skills:** `transitous-api`, `i18n-rtl`
**Needs:** nothing (starts from `main`)
**Owns:** `search/*.kt` (`PlaceSearch`, `Aliases`, `Towns`, `LocalFirst`), `search/Neighbourhoods.kt`
+ `search/NeighbourhoodsData.kt` (new, generated), `search/MatchLevel.kt` (new),
`tools/gen_neighbourhoods.py` + `tools/neighbourhoods_seed.tsv` (new),
`core/src/test/.../AddressCorpusJvmTest.kt` (new) + `fixtures/corpus/` (new),
`ui/screens/SearchLevel.kt` (new), `strings_search_level.xml`; `api/Guards.kt` and
`api/MotisClient.kt` for cancellation only
**Shared-file hooks:** `TripPanel.kt`: one line in `SuggestionList`/`SuggestionRow` that shows the
match level (D1 does not touch those functions); `MainViewModel`: one hook if the stale-answer
check needs the current query

> The owner's report that started the search fix was "my home address is not found". This
> package makes the search predictable and tests it fast. **0 new requests.**
>
> **1. Cancel stale searches.** `TypingSearch` already cancels its coroutine on each keystroke.
> Make sure that really stops the work:
> - the HTTP call in `MotisClient` is cancelled with the coroutine (not left running);
> - a cancelled call leaves nothing in `GuardedTransitApi`'s cache and frees its concurrency
>   slot;
> - the phone `Geocoder` (blocking) and Photon answers for an old query are dropped: an answer
>   is shown only when its query is still the text in the box (a sequence number, tested).
>
> **2. Acronyms.** `Aliases` learns the common ones, with `"`, `״`, `'` or no mark at all:
> ת"א, ר"ג, פ"ת, ב"ש, ק"ש, ק"ג, ר"ל, ראשל"צ, נס"צ, כ"ס, הוד"ש, ב"ב, את"א (= Tel Aviv
> University), בי"ח (= hospital). An ambiguous acronym is not expanded. Each one has a row in
> the corpus.
>
> **3. Neighbourhood → town.** A bundled table of about 150 well-known neighbourhoods and the
> town they are in (רמת אביב → תל אביב-יפו, נווה שאנן → חיפה, גילה → ירושלים, רמות → ירושלים /
> באר שבע…). Generated by `tools/gen_neighbourhoods.py` from a hand-checked seed TSV, like
> `gen_towns.py`. A query that names a neighbourhood and no town searches near that town's
> centre (`Towns`), and the answer's detail line names the town. A name in two towns asks
> nearby first (my location), then lists both. No coordinates of our own: the town comes from
> `Towns`.
>
> **4. Match levels.** `SearchAnswer` hits get an explicit `MatchLevel`: `HOUSE` (the typed
> house number), `STREET` (street, not the number), `TOWN` (only the town), `PLACE` (a named
> place or a stop), `NONE`. It replaces the yes/no exact flag, with the same rule: only `HOUSE`
> or `PLACE` matching the text is "the answer"; the rest stays under "Closest matches". The
> suggestion row says the level when it is not exact: "Street only: no number 15", "Town only".
>
> **5. Fast JVM address corpus.** `AddressCorpusJvmTest` runs the whole chain (`PlaceSearch` with
> a replayed Transitous and a replayed backup geocoder) in `./gradlew -p core test`, every push.
> - At least **200 rows**: the ~63 rows of the emulator `AddressCorpusTest`, plus variants
>   (acronyms, quote marks, neighbourhoods, house numbers, English spellings). Variants share a
>   recorded answer where the normalized query is the same.
> - Each row: query, expected place (name + within 150 m) and expected `MatchLevel`, or a
>   known gap with its reason. A wrong answer fails a known-gap row too.
> - **Recording:** at most **60 new geocode answers** from Transitous, ≥ 1 s apart, and the
>   Photon answers the misses need (the hosts must be allowed in the environment; if Photon
>   is blocked, record its rows on the owner's PC or leave them out and say so). No live
>   `Address corpus` emulator run; the owner can run it.
>
> **Tests:** `TypingSearchTest` (five keystrokes in 300 ms send 1 geocode; a slow first answer
> arriving after the second is dropped; cancel frees the slot), `AliasesTest`,
> `NeighbourhoodsTest`, `MatchLevelTest`, the corpus.
>
> **Reviewer checks (Sonnet logic):** a Hebrew street with a house number that Transitous has
> without the number; a neighbourhood in two towns; an acronym in English text; the
> stale-answer race under slow network.
>
> **Docs:** the `transitous-api` skill (match levels, the corpus, how to add a row); a release
> fragment `docs/releases/next/d2-search-hardening.md`; tick ROADMAP D2.

## D3: Holidays + dates
**Branch:** `claude/p10-holidays`
**Skills:** `reminders-offline`, `transitous-api`, `i18n-rtl`, `add-feature`
**Needs:** D1 merged (`TripPanel.kt`)
**Owns:** `calendar/Holidays.kt` + `calendar/HolidaysData.kt` (core, new, generated),
`tools/gen_holidays.py` (new), `present/DayNotice.kt` (new), `ui/screens/DayNotice.kt` (new),
`plan/LastTripHome.kt`, `remind/LastTripReceiver.kt`, `strings_holidays.xml`
**Shared-file hooks:** `TripPanel.kt`: one banner line above the options; `MainViewModel`: one
hook that offers the nearest day (re-plans with the new date)

> **0 requests** to build any notice; **1 per tap** on "Plan for <day>".
>
> **1. Holiday heads-up (item 3).**
> - `tools/gen_holidays.py` writes `HolidaysData.kt` for **2026–2031** from the Hebrew calendar
>   (compute it: the arithmetic rules fit in the tool; or a pip package if the environment
>   allows it). It includes Israel's moving rules for Yom HaZikaron / Yom HaAtzmaut and Yom
>   HaShoah. `HolidaysTest` pins at least 8 dates against a cited source (hebcal.com or
>   gov.il), for example Yom Kippur 2026 = Mon 21 Sep 2026 and Pesach 2027 = Thu 22 Apr 2027.
> - From 2031 on (the table's last year), Settings shows one line "Holiday table out of date:
>   update the app", tested with a fixed clock.
> - Each day has a kind: `NO_SERVICE` (Rosh Hashana 1–2, Yom Kippur, Sukkot, Shmini Atzeret,
>   Pesach first and seventh day, Shavuot), `EVE` (the day before each), `YOM_KIPPUR_EVE`,
>   `REDUCED` (Yom HaAtzmaut, Chol HaMoed: owner decision 2), with a one-line source for each
>   rule.
> - On the Trip tab, a search for such a day shows one banner: "Thu 22 Apr is Pesach: no buses
>   or trains until the evening after" / "Erev Rosh Hashana: buses run a Friday timetable,
>   trains stop in the afternoon". The banner never claims a time: the last trip comes from
>   the timetable (`LastRideFinder`), as today.
> - **Last trip home alert** (the follow-up in `docs/agents/boss-handoff.md`): on an `EVE` day
>   the check runs at the Friday time (12:00), not 19:00, and the text says why.
>
> **2. "The timetable doesn't reach this date" (item 25).**
> - Measure once how far ahead the Transitous MOT timetable reaches: at most **3 live `plan`
>   calls** (for example +30, +60, +90 days), recorded as fixtures; put the result in
>   `DayNotice.HORIZON_DAYS` with the date checked.
> - A search that returns 0 options:
>   - on a `NO_SERVICE` day or Shabbat: "No service that day" and a button "Plan for <the next
>     day with service>", computed from the calendar (Shabbat ends Saturday evening: offer
>     Saturday 20:00);
>   - beyond the horizon: "The timetable may not reach Thu 22 Apr yet" and "Plan for <the same
>     weekday within the horizon>";
>   - otherwise the existing "no options" text.
> - The button re-plans once (1 request). Nothing is planned on its own.
>
> **Tests:** `HolidaysTest.kt` (the pinned dates, the moving rules, every year has every
> holiday once, sunset days in the right civil day), `DayNoticeTest.kt` (each case above,
> Friday, Saturday night after service, the 04:00 service-day edge, a date in the horizon
> that still has 0 options), `LastTripHomeTest` additions for the eve.
>
> **Reviewer checks (Sonnet logic + designer):** the pinned dates against the source; the
> banner on the smallest phone in Hebrew; Yom Kippur eve afternoon; a weekday holiday eve.
>
> **Docs:** the `reminders-offline` skill (the holiday table, who updates it after 2031, the
> eve rule in the last-trip alert); `transitous-api` (the measured horizon); a release fragment
> `docs/releases/next/d3-holidays.md`; tick ROADMAP D3.

## D4: Test hardening (network faults + routing invariants)
**Branch:** `claude/p10-test-hardening`
**Skills:** `transitous-api`, `special-features`, `golden-trips`
**Needs:** D2 merged (its cancellation code in `Guards.kt`)
**Owns:** new test files only: `NetworkFaultTest.kt`, `RoutingInvariantsTest.kt`, and a
`FaultyTransitApi.kt` test helper (a new file, not `Fakes.kt`); small fixes (≤ ~20 lines) in
`api/Guards.kt`, `api/MotisClient.kt` or `search/Photon.kt` when a new test exposes a real bug
**Shared-file hooks:** none

> Runs on **Sonnet**. **0 requests.** No sockets, not even localhost: faults are injected at
> the `TransitApi` / `BackupGeocoder` level and through `MotisClient`'s parsing.
>
> **1. Network faults.** For `GuardedTransitApi`, `BudgetedTransitApi` and `GuardedGeocoder`:
> - 429 then 200: one retry, one answer, counted once against the budget;
> - 503 twice: fails after one retry, never a third call;
> - a slow answer past the timeout: an error, nothing cached, the slot freed;
> - cancelled mid-call: nothing cached, the slot freed, the next call works;
> - malformed or empty JSON: a clear error, never a crash or a cached empty answer;
> - 3 calls at once with a limit of 2: the third waits.
>
> **2. Routing invariants.** Over every recorded plan fixture and every special feature's
> output on its fixtures: arrival ≥ departure; legs in time order; a leg's stops in time order;
> drive time and detour ≥ 0; traffic factor ≥ 1; the Pareto front has no dominated option;
> every budget is respected; fares ≥ 0; ride details (D1) add up to the leg.
>
> **A test that finds a real bug:** fix it if it is small and in the owned files; otherwise
> write the failing case in the PR body, keep the test out of the PR, and tell the boss.
> Never push a skipped or disabled test.
>
> **Docs:** the `transitous-api` skill (the fault behaviour, one table); a release fragment is
> not needed (no user change); tick ROADMAP D4.

## D5: Big-stop changes
**Branch:** `claude/p10-big-stops`
**Skills:** `transitous-api`, `golden-trips`, `i18n-rtl`, `add-feature`
**Needs:** D1 (`present/Transfers.kt`) and D4 merged
**Owns:** `plan/BigStopChange.kt` (core, new), `present/BigStopText.kt` (new),
`ui/screens/BigStopRow.kt` (new), `strings_big_stops.xml`
**Shared-file hooks:** `TripPanel.kt`: one line on the selected card; `MainViewModel`: one hook
that runs the check when an option is opened, and one that applies the change

> **Idea (item 27).** When an option changes lines at a small street stop, and both lines also
> pass a big stop, change there instead: **the same two vehicles**, so the arrival is the
> same and the trip is never longer. "Safer change: stay on line 5 two more stops to Be'er
> Sheva Central, take line 18 there. Same arrival; 7 min to change instead of 2."
>
> **When:** only when the user opens (selects) an option that has a change at a stop that is
> not big. With several changes, only the one with the least slack. Never for the other
> options, never in the background.
>
> **Requests:** `trip(tripId)` for the leg before the change (the stops after you get off) and
> the leg after it (the stops before you get on): **2 per opened option**, under
> `BudgetedTransitApi(2)`, cached by the guard. These are the same calls the trip details sheet
> makes, so opening the sheet afterwards costs 0.
>
> **Core (`plan/BigStopChange.kt`, pure apart from the two calls):**
> - Candidates: stops that the first vehicle reaches after the original change and the second
>   vehicle leaves from before it (or the other way round), matched by stop id, or the same
>   parent station, or within 150 m with the same name.
> - **Big stop** (owner decision 3): a rail or light-rail station (`RailStations`, mode),
>   a name with תחנה מרכזית / מסוף / Central Station / Terminal, or a stop `description` with
>   a platform (C1's `StopPlatform`).
> - Keep a candidate only if: the first vehicle arrives there before the second leaves, with
>   slack ≥ the original's + 3 min and ≥ the user's buffer; the extra walk is ≤ the original's;
>   the final arrival is unchanged. Pick the one with the most slack.
> - "Use this change" re-cuts the two legs on the phone (pure, tested), so the trip details,
>   the get-off alert and the reminder follow it. The original stays one tap away.
>
> **Tests:** `BigStopChangeTest.kt` on recorded `trip` fixtures (record at most **6 `trip`
> calls** for 2–3 real cases, for example a Be'er Sheva or Haifa street change that both lines
> also serve at the central station): a candidate found, none found, a direct trip (0
> requests), a change already at a big stop (0 requests), less slack (rejected), the budget,
> the re-cut legs pass D4's invariants. One golden trip if a case fits (`golden-trips`).
>
> **Reviewer checks (Sonnet logic):** arrival really unchanged; the get-off alert stops at the
> new stop after "Use this change"; a loop line that passes the same stop twice.
>
> **Docs:** the `transitous-api` skill (the 2-call budget); `golden-trips` if a trip is
> added; a release fragment `docs/releases/next/d5-big-stops.md`; tick ROADMAP D5.

## D6: Ways in (shared places, voice, stop number)
**Branch:** `claude/p10-ways-in`
**Skills:** `transitous-api`, `i18n-rtl`, `add-feature`, `android-build`
**Needs:** D2 merged (search code)
**Owns:** `present/SharedPlace.kt` (new), `search/StopCode.kt` (new), `MainActivity.kt` (intent
handling), `ui/screens/SearchEditing.kt` (the mic icon), `ui/VoiceSearch.kt` (new),
`strings_ways_in.xml`
**Shared-file hooks:** `AndroidManifest.xml`: intent filters on `MainActivity` and one
`<queries>` entry for the speech service; `search/PlaceSearch.kt`: one branch that routes a
stop number to `StopCode`; `MainViewModel`: one `openShared(place)` entry point

> **1. Open places from other apps (item 2).**
> - Filters on `MainActivity` (it is `singleTop`; handle `onNewIntent` too): `ACTION_SEND`
>   `text/plain`; `ACTION_VIEW` for `geo:` and for `https` links of `maps.google.com`,
>   `www.google.com/maps`, `waze.com/ul`.
> - `present/SharedPlace.kt` (pure) parses: `geo:lat,lon`, `geo:0,0?q=…`, `?q=lat,lon`,
>   `@lat,lon`, `ll=lat,lon`, Waze `ll=`; a WhatsApp text with an address and other words;
>   Hebrew and English.
> - Coordinates → the destination at once (0 requests; named by the pin-name code from the
>   search fix). Address text → one `geocode` through `PlaceSearch` (≤ 1 request), the result
>   list opens with the text in the box.
> - Short links (`maps.app.goo.gl`, `goo.gl/maps`) are **not** resolved (that would call
>   Google): "This link can't be opened here. Paste the address instead." (owner decision 4).
> - A coordinate outside Israel's bbox is refused politely.
>
> **2. Voice search (item 9).** A mic icon at the end of the search field (start in RTL):
> `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` with the app language (`he-IL` / `en-US`). The text
> goes into the box and searches as if typed. Hidden when no app handles the intent (API 30+
> needs the `<queries>` entry). The first use shows one line: "Speech goes to your phone's
> speech service, not to this app". 0 extra requests.
>
> **3. Search by stop number (item 11).** A query of 3–6 digits (optionally "תחנה" / "stop"
> before it) is a stop code.
> - First check with at most **3 live calls** whether Transitous `geocode` finds a stop by its
>   code (record what it does). If it does, use it (1 request, `StopCode` filters to the stop
>   with that `stopCode`).
> - If not: look up the code on the phone in the stops tile cache, My lines and the departure
>   cache (0 requests); with no hit, "Stop 47899 is not in this area yet: open the map near it".
>   Write the finding in `transitous-api` and, if negative, in `dead-ends`.
> - A digit query that is a house number with a street ("הרצל 15") is not a stop code.
> - A hit opens the stop sheet.
>
> **Tests:** `SharedPlaceTest.kt` (every link form, a long WhatsApp text, a short link,
> outside Israel, 0 requests for coordinates), `StopCodeTest.kt`, the routing rule in
> `PlaceSearchTest`.
>
> **Reviewer checks (Sonnet logic):** share from WhatsApp while the app is open and while it is
> killed; a Waze link; voice with no speech service; "15" alone.
>
> **Docs:** the `transitous-api` skill (stop-code search); `add-feature` if the intent pattern is
> new; a release fragment `docs/releases/next/d6-ways-in.md`; tick ROADMAP D6.

## D7: Stop timetables (day timetable + offline)
**Branch:** `claude/p10-timetables`
**Skills:** `transitous-api`, `reminders-offline`, `i18n-rtl`, `add-feature`
**Needs:** Wave 3 merged
**Owns:** `api/Models.kt`, `api/TransitApi.kt`, `api/MotisClient.kt`, `api/Guards.kt`,
`api/ReplayTransitApi.kt` (the `stopTimes` page cursor, fields only), test `Fakes.kt`,
`present/DayTimetable.kt` (new), `ui/screens/DayTimetable.kt` (new),
`data/TimetableStore.kt` (new), `plan/NightRefresh.kt`, `work/NightRefreshWorker.kt`,
`ui/screens/StopSheet.kt`, `strings_timetable.xml`
**Shared-file hooks:** `MainViewModel`: one `openTimetable(stop, line)` entry point

> **1. Day timetable for a line at a stop (item 7).**
> - In the stop sheet's departures, tap a line → all its departures from that stop today,
>   grouped by hour ("06 │ 10 40", "07 │ 05 25 45"), the next one marked; "Tomorrow ›" shows
>   the next service day.
> - API: `stopTimes(..., pageCursor: String? = null)` and `StopTimesResponse.nextPageCursor`
>   / `previousPageCursor`, null defaults. Start from the service day's 04:00
>   (`LastRideFinder.DAY_STARTS`), `n = 60`, at most **2 pages per open**, then a "More" button
>   (1 request per tap). Filter to the line + headsign on the phone; dedupe Saturday-night
>   doubles by line + time (`transitous-api`).
> - Times are scheduled; say so once in the sheet's footer.
>
> **2. Offline timetables for starred stops (item 18).**
> - The night refresh (C2's worker, same constraints: Wi-Fi + charging + battery not low)
>   also saves tomorrow's board for the stops in My lines: at most **3 stops**, one `stopTimes`
>   page each (`n` ≤ 100), under **its own `BudgetedTransitApi(3)`**, after the trip jobs.
>   Worst case per night: 6 trips + 3 stops = 9 requests (owner decision 5).
> - `data/TimetableStore.kt` keeps the last saved board per stop. Offline, the stop sheet and
>   the day timetable show it: "Saved last night at 02:14", departures already gone hidden.
>
> **Fixtures:** record at most **4 calls**: a busy stop with two pages, its second page, a
> quiet stop, and a stop on Friday afternoon.
>
> **Tests:** `DayTimetableTest.kt` (grouping, the line filter, dedupe, 2 pages then More,
> the 04:00 edge, Friday ending early), `NightRefreshTest` additions (3 stops cap, its own
> budget, a second run sends 0), the paging fields parse and old caches decode.
>
> **Reviewer checks (Sonnet designer):** the hour grid in Hebrew RTL (hours on the right,
> minutes reading right to left), a hub line with 150 departures, offline banner.
>
> **Docs:** `transitous-api` (stoptimes paging, the night budget row); `reminders-offline`
> (offline timetables); a release fragment `docs/releases/next/d7-timetables.md`; tick ROADMAP D7.

## D8: Fares + report
**Branch:** `claude/p10-fares-report`
**Skills:** `i18n-rtl`, `add-feature`, `reminders-offline` (history section)
**Needs:** Wave 3 merged
**Owns:** `fare/Fares.kt`, `fare/PassAdvisor.kt`, `history/History.kt`, `history/Report.kt` (new),
`ui/screens/FareSettings.kt` (new), `ui/screens/ReportExport.kt` (new), `ui/screens/Insights.kt`,
`strings_fares.xml`
**Shared-file hooks:** `user/User.kt`: the profile field; `SettingsDialog`: the fare section and
one "Monthly report" button in the history section; `AndroidManifest.xml` /
`res/xml/` FileProvider: one cache path for the report (the provider already exists for the
APK installer); `MainViewModel`: one hook

> **0 requests.**
>
> **1. Fare profiles (item 12).** Settings → Fare: Regular · Senior · Student · Youth ·
> Soldier · 50% (other) · Free (other).
> - Each profile has its discount per mode and for monthly passes, from an **official source**
>   (owner decision 6): MOT's Rav-Kav profiles page on gov.il or the operators' page that
>   `FareTable.SOURCE_URL` already cites. Write the URL and the date checked next to each
>   number, and pin them in `FareTest`. **If a number has no reachable source, stop and ask the
>   owner; never invent it.**
> - Soldier = conscripts in mandatory service (free on buses and trains, proposal); the profile
>   says "in mandatory service".
> - Migration: today's `FareProfile` values (`REGULAR`, `HALF`, `FREE`) keep decoding to the
>   same prices. Test it with a stored old settings string.
> - The estimate on every option and C3's pass advisor use the profile.
>
> **2. Monthly travel report (item 13).** History section → "Monthly report" → pick a finished
> month → the share sheet with a CSV file (UTF-8 with BOM, so Excel shows Hebrew), named
> `transit-2026-09.csv`, and the same summary as text in the message body.
> - Columns: date, start, end, from, to, lines, minutes, fare estimate (₪), profile.
> - The last rows: total trips, total estimate, and "estimate from trips started in the app;
>   not a receipt".
> - The file goes into `cacheDir/reports/` through the existing FileProvider (one path entry),
>   and is deleted after a day. Nothing leaves the phone without the tap.
> - `history/Report.kt` (pure) builds the CSV and text; tested.
>
> **Tests:** `FareProfileTest.kt` (each profile, old values decode, passes), `ReportTest.kt`
> (a month with 0 trips, a trip across midnight at the month's end, Hebrew names with commas
> and quotes escaped, the BOM, totals).
>
> **Reviewer checks (Sonnet logic):** every number against its source; a FREE profile shows no
> pass advice; the CSV opens in Google Sheets with Hebrew intact.
>
> **Docs:** `reminders-offline` (the report); the fare source note in `fare/Fares.kt`; a release
> fragment `docs/releases/next/d8-fares-report.md`; tick ROADMAP D8.

## D9: Rain-aware walking
**Branch:** `claude/p10-rain`
**Skills:** `transitous-api`, `i18n-rtl`, `add-feature`, `dead-ends`
**Needs:** Wave 4 merged
**Owns:** `weather/OpenMeteo.kt` (core, new: client + `GuardedWeather`), `weather/RainAdvice.kt`
(core, new), `ui/screens/RainNote.kt` (new), `strings_rain.xml`
**Shared-file hooks:** `CLAUDE.md`: one hard-rule line for Open-Meteo, next to Photon (stay
under 120 lines); `user/User.kt`: `UserSettings.rainAware: Boolean = true`; `SettingsDialog`:
one toggle; `TripPanel.kt`: one line above the options; `MainViewModel`: one hook after a
Trip search

> **New outside service (owner-approved, 2026-10-07): Open-Meteo**
> (`https://api.open-meteo.com/v1/forecast`), free, no key, no card.
> - Rules, as for Photon: our User-Agent; ≤ 1 request per Trip search, only for a trip
>   starting within the next 48 h; cached 1 h per 0.1° cell (~10 km, which is also all it is
>   told: the rounded start); ≤ 2 at once; one retry on 429/503; 3 s timeout, and a failure is
>   silent (no note). Credit "Weather: Open-Meteo" under the note. Swappable base URL. Add it
>   to `CLAUDE.md`'s hard rules and to `transitous-api`'s outside-services section.
> - Its terms: free for non-commercial use with attribution, < 10 000 calls a day per app
>   (check the current page and quote it in the PR).
>
> **What the user gets:** when rain is likely during the walking parts of the options, one note
> on the Trip tab: "Rain likely around 08:10 (70%). Least walking: 4 min (the 08:05 option)",
> with a tap that switches the sort to Least walking. The plan request is never changed, and
> no extra Transitous request is sent.
>
> **Rule (owner decision 7):** hourly `precipitation_probability ≥ 60%` or `precipitation ≥
> 0.5 mm` in an hour that overlaps ≥ 5 min of walking in at least one option. `RainAdvice.kt`
> (pure) decides and picks the option.
>
> **Fixtures:** record at most **3 calls** (a rainy winter forecast, a dry one, an error). If the
> host is blocked in the environment, read the `environment.network` documentation and ask the
> boss for it to be allowed; don't hand-write fixtures.
>
> **Tests:** `OpenMeteoTest.kt` (parse, rounding of the cell, cache within the hour, the 48 h
> limit, failure is silent), `RainAdviceTest.kt` (threshold edges, walking overlap, no walking,
> Asia/Jerusalem hours across the DST change).
>
> **Reviewer checks (Sonnet designer):** the note on the smallest phone, dark mode, Hebrew.
>
> **Docs:** `CLAUDE.md` hard rule; `transitous-api` (Open-Meteo section and the budget row);
> a release fragment `docs/releases/next/d9-rain.md` (say what is sent); tick ROADMAP D9.

## D10: Live Updates notification (Android 16)
**Branch:** `claude/p10-live-updates`
**Skills:** `android-build`, `reminders-offline`, `ui-testing`
**Needs:** Wave 4 merged
**Owns:** `android/app/build.gradle.kts` and `gradle/libs.versions.toml` (SDK 36 and the
`androidx.core` version only), `remind/Notifications.kt`, `remind/ReminderReceiver.kt`,
`ride/RideService.kt`, `remind/LiveProgress.kt` (core, new), `.github/workflows/ui-tests.yml`
(one API 36 row)
**Shared-file hooks:** `AndroidManifest.xml`: `POST_PROMOTED_NOTIFICATIONS`; `MainViewModel`:
none expected

> **0 requests.**
> - `compileSdk = 36`. **`targetSdk` stays 35** (owner decision 8): no Android 16 behaviour
>   changes ride along. Update `androidx.core` to a version with `NotificationCompat.ProgressStyle`.
> - **"On the bus"** (RideService) and the **leave-now countdown** (C5) become Live Updates on
>   Android 16+: `ProgressStyle` with one segment per leg (walk / bus / train in the leg
>   colours of `present/LegPalette`), a point per change, the tracker at the current progress,
>   `setRequestPromotedOngoing(true)`, and a short status-bar text ("3 stops", "8 min").
> - Below Android 16: today's notification, plus the compat progress bar where `NotificationCompat`
>   draws one. No second notification ever (same ids).
> - `remind/LiveProgress.kt` (core, pure): segments and points from an itinerary, progress
>   from `RideTracker`'s state, the short text. Tested.
> - CI: add one API 36 row to the `UI tests` journeys (ride + countdown journeys only); if no
>   API 36 emulator image works on GitHub's runners, say so in the PR and keep the row out.
>
> **Tests:** `LiveProgressTest.kt` (a 3-leg trip, a walk-only start, progress at the change,
> after the last stop, a delay).
>
> **Reviewer checks (Sonnet logic):** the same notification id on < 16 and 16+; the countdown
> ends 2 min after boarding as before; the ride notification goes away at the end; a reboot
> during a countdown.
>
> **Docs:** `android-build` (SDK 36, target 35 and why); `reminders-offline` (Live Updates);
> a release fragment `docs/releases/next/d10-live-updates.md`; tick ROADMAP D10.

## D11: App polish (process death, map ↔ list, thumb zone)
**Branch:** `claude/p10-polish`
**Skills:** `i18n-rtl`, `ui-testing`, `android-build`
**Needs:** Wave 5 merged (it touches the files every package hooks into)
**Owns:** `ui/MainViewModel.kt` (state saving), `ui/MainScreen.kt`, `ui/MapController.kt`,
`ui/PlacePick.kt`, `ui/screens/TripPanel.kt`, `ui/screens/PlacePick.kt`, `MainActivity.kt`,
`ui/SavedUi.kt` (new), `strings_polish.xml`
**Shared-file hooks:** none (it is the only package in its wave)

> **0 requests** (restoring the state reads the Trip cache; it never re-plans by itself).
>
> **1. Keep the search and the chosen trip across process death.** From, To, the time mode and
> time, the mode tab, the selected option and the open sheet survive Android killing the app
> in the background: `SavedStateHandle` for the small values, and the Trip cache entry for the
> options (no request). Extend the existing process-death journey to check the selected
> option and the open sheet come back.
>
> **2. Map ↔ list highlighting.** Selecting a card draws its route on top at full colour and the
> others thin and faded; tapping a route on the map selects its card and scrolls the list to
> it. Long-pressing still drops a pin. Colours from `present/LegPalette`.
>
> **3. Thumb-zone actions + haptics.** The main actions of the selected option (Start trip,
> Remind me, Send to driver) sit in a bottom row within thumb reach, each ≥ 48 dp; the layout
> audit (`ui-testing` R-rules) checks the targets on every profile. A haptic tick on pin drop
> (long-press), on "Start trip" and when a transfer warning appears on the selected option;
> none when the phone's touch feedback is off.
>
> **Tests:** a pure test for the saved-state round-trip (`SavedUi`), the process-death
> journey, Paparazzi states for the highlighted map list and the bottom action row.
>
> **Reviewer checks (Sonnet designer):** the bottom row on the smallest phone with the keyboard
> open; landscape; dark mode; Hebrew RTL order of the actions.
>
> **Docs:** `ui-testing` (the extended journey, the target rule); `i18n-rtl` (the action row);
> a release fragment `docs/releases/next/d11-polish.md`; tick ROADMAP D11.

---

## D12: Spare minute, sort in every tab, no unreasonable chains (owner, 2026-10-12)
**Branch:** `claude/p10-spare-sort`
**Skills:** `special-features`, `i18n-rtl`, `add-feature`, `android-build`
**Needs:** D6 merged. Runs alone, before D7.
**Owns:** `plan/TripPlanner.kt` (`TripSort`, `sortOptions`), `plan/TripPages.kt` (`sane`), `user/User.kt` (the buffer field and
its choices), the transfer-buffer row in `SettingsDialog.kt`, `SortChips` in `TripPanel.kt`
**Shared-file hooks:** `MainViewModel`: the `options` / `selectedCarPath` getters and the
reselect calls, so the order applies in every tab; `TripPanel.kt`: the one line that shows
`SortChips` only for `AppMode.TRIP`

> **1. At least 1 minute to spare at every change (owner, 2026-10-12).**
> - Every plan request in every tab (Trip, errands, Better start, Drop-off, Pick-up, Park & ride,
>   last trip home) sends `additionalTransferTime` of **at least 1 min**. It is sent on top of
>   the change walk, which already includes MOTIS's own minimum.
> - The buffer setting loses "Off". Its choices become **1 (default), 3, 5, 8, 10**.
> - A saved `transferBufferMin = null` reads as 1. Keep the JSON field name, so old settings
>   still load. Pin this in `UserJsonTest`.
> - The D1 transfer chip thresholds stay as decided (never below the buffer).
> - The cache key changes, so old cached plans are simply missed. That is fine.
> - No extra requests.
>
> **2. Fastest / Fewest transfers / Least walking in every tab (owner, 2026-10-12).**
> - Show `SortChips` in all five tabs whenever there are 2 or more options. Keep one shared
>   `settings.tripSort`.
> - Sort the **payload lists** (Better start, Drop-off, Pick-up and Park & ride options), not
>   only the itineraries. The selected card, its car path, the map highlight and the D5
>   "safer change" must keep pointing at the same option after a re-sort.
> - Fastest = earliest arrival at the destination (latest departure for arrive-by), car part
>   included. Fewest transfers = transit transfers. Least walking = `walkSec` of the whole option.
> - Ties keep the planner's order.
> - The walk-only and baseline options sort like any other option.
> - Errands (chain) results stay in leg order. No chips there.
>
> **3. No unreasonable chains (owner, 2026-10-12, screenshot `docs/agents/d12-golan-tlv.png`).**
> The case: Golan to Tabenkin St 15, Tel Aviv. It came out as 07:12–11:01, 7 buses, 6 changes,
> four of them with 0–1 min to spare, listed first under Fastest.
> - In `TripPages.sane` (every tab's transit options go through it), drop option X when another
>   option Y has **at least 2 fewer changes and arrives at most 10 min after X**. For arrive-by,
>   use Y leaving at most 10 min before X. (As built: the window bounds both ends in both modes,
>   Y leaving at most 10 min before X and arriving at most 10 min after it.)
> - Drop any option with **5 or more changes** when an option with 3 or fewer is within 10 min
>   (leaves at most 10 min before X and arrives at most 10 min after X; owner 2026-10-12).
> - In the car tabs, Y also costs the driver no more than X (`driverCostSec`; review 2026-10-12).
> - An option is never dropped when it is the only one left. Put the constants in
>   `TripPlanner` and pin them with tests.
> - Record the Golan → Tel Aviv case as a fixture: one `plan` call through `record_fixture.py`,
>   from near Kela Alon to Tabenkin 15, Tuesday 07:00.
>   - Assert the 6-change chain is gone, or say in the PR what the feed really offers.
>   - Add it to `golden-trips` if it holds.
> - Fewest transfers / Least walking sort only what survives.
>
> **Tests:** `SortOptionsTest`: each sort in each mode's payload type, with ties and arrive-by.
> A test that every mode's request carries `additionalTransferTime ≥ 1` (use the fake API's
> recorded queries). The settings migration in `UserJsonTest`. Paparazzi: the chips on a
> Drop-off result. The emulator journeys stay green, then refresh the goldens from that head.
>
> **Reviewer checks (Sonnet logic):**
> - The chain rule: never drops the last option; arrive-by direction; a 2-change gap at exactly 10 min.
> - Re-sort in Drop-off and Park & ride with option 2 selected: the map car path and the
>   card still match.
> - An old settings file with the buffer Off: the plan sends 1.
> - Arrive-by + Fastest.
>
> **Docs:** the `special-features` skill (sorting the front), a release fragment
> `docs/releases/next/d12-spare-sort.md` (en + he), and a ROADMAP line for D12.

## Owner decisions this plan needs (ask before Wave 1)

Proposals in bold. The boss records the answers here, under a "Decided" line, before starting
Wave 1 (only 1–2 block Wave 1; the rest can be answered before their wave).
1. **Transfer warning and buffer (D1):** warn **under 5 min, or under 8 min when a train is on
   either side**; buffer setting **Off by default** (MOTIS's own minimum), choices 3/5/8/10.
   **Decided (owner, 2026-10-12, after D1 measured the recorded plans):** the chip shows
   **under 2 min, or under 4 min with a train on either side** (never below the buffer);
   "Tight: may miss the change" only when the slack is **below 0** (a live delay). MOTIS's
   transfer walks already include its minimum change time, so 5/8 min put a chip on 52 of 61
   recorded options with a change. Buffer unchanged: Off by default, 3/5/8/10.
   **Superseded (owner, 2026-10-12, D12):** at least 1 min spare at every change in every tab;
   the buffer choices become 1 (default)/3/5/8/10, with no Off.
2. **Holidays listed (D3):** **all days with no service (Rosh Hashana, Yom Kippur, Sukkot,
   Shmini Atzeret, Pesach 1 and 7, Shavuot) and their eves, plus Yom HaAtzmaut and Chol HaMoed
   as "reduced service, check the times"**. Muslim, Christian and Druze holidays are not covered.
   Who keeps the table: the generator covers 2026–2031; **the app shows a "holiday table
   out of date" line in Settings from 2031, and the boss re-runs the tool then**.
   **Timetable horizon (D3), decided (owner, 2026-10-12):** no extra calls; keep
   `DayNotice.HORIZON_DAYS = 8` (measured: +8 days answered, +30/+60/+90 empty).
3. **"Big stop" (D5):** **a rail or light-rail station, a stop named תחנה מרכזית / מסוף /
   Central Station / Terminal, or a stop with platforms in its description**; offered only when
   it gives **≥ 3 min more slack** and the same arrival.
   **Matching, decided (owner, 2026-10-12): B.** Besides the same stop id, parent station or
   name (within 150 m), a big stop on the other line also matches within **300 m**
   (`BigStopChanges.NEAR_BIG_M`), whatever its name (Be'er Sheva central's city and intercity
   platforms). The walk is counted at a fixed 1 m/s (`WALK_MPS`) against the slack, the offer
   still needs ≥ 3 min more slack after it, and the row says the walk ("Walk 4 min to …").
   After the logic review: slack ranks only up to 10 min (`RANK_CAP_SEC`), and a re-opened
   option sends 0 requests for as long as its results last (`BigStopSession`).
4. **Short map links (D6):** **"paste the address instead"**, no call to Google to expand them.
5. **Offline timetables (D7):** **3 starred stops a night**, so the night worst case is 6 + 3 =
   **9 requests**, Wi-Fi + charging only.
6. **Fare profiles (D8):** **from MOT's Rav-Kav profile page (gov.il) or the operator page
   `FareTable` already cites; any number without a reachable source stops the package**.
   Soldier = **conscripts, free on bus and train**.
7. **Rain threshold (D9):** **≥ 60% probability or ≥ 0.5 mm in an hour that overlaps ≥ 5 min of
   walking**; on by default; the note only re-sorts on tap, never changes the plan.
8. **Live Updates (D10):** **`compileSdk` 36, `targetSdk` stays 35**; on Android < 16 **today's
   notification with the compat progress bar**, no new UI.
9. **Recording for the corpus (D2):** **≤ 60 new Transitous geocodes, ≥ 1 s apart**, once.
   **Neighbourhood in two towns (D2), decided (owner, 2026-10-12): B.** One suggestion row per
   town from the bundled table ("רמות, ירושלים", "רמות, באר שבע"), nearest town first; 0
   requests while typing, 1 geocode (that town's centre) when a row is tapped.
10. **Releases:** **v0.8.6** after Wave 2 (D1–D4), then **v0.9.0** at the end of Phase 10.
    A v0.8.7 after Wave 4 is optional, if the owner wants the timetables sooner.

---

## Close-out (boss)
- After Wave 2: join the D1–D3 fragments into `docs/releases/v0.8.6.md` (if the owner wants
  the release), delete them, release with `release-apk`.
- Once all packages are merged, join the remaining `docs/releases/next/*.md` into
  `docs/releases/v0.9.0.md` (English block, then Hebrew block, then the usual update and
  attribution lines). Then delete the fragments.
- Check every Phase 10 ROADMAP line and every skill row (`python3 tools/check_docs.py`).
- Ask the owner to check each feature on the Pixel (the offline timetables need one night on
  Wi-Fi and charging; Live Updates need an Android 16 phone or the API 36 emulator), then
  release with the `release-apk` skill.
