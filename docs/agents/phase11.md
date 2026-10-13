# Phase 11: the brief each agent session gets

The boss starts one cloud session per package. The prompt it sends is the **Common preamble**
followed by that package's brief, word for word as written below. If you re-run a package,
use the same text. When the plan changes, update this file in the same PR.

The scope is the owner's Phase 11 list (2026-10-13), taken from `docs/research/phase11-ideas.md`
(round 1) and `docs/research/phase11-ideas-2.md` (round 2), plus the owner's request to "plan
more rigorous tests of all aspects". Items:
1. "Will my reminder ring?" check (round 1 idea 1);
2. "I missed it": one-tap re-plan from GPS (round 1 idea 3);
3. accessibility pass + a TalkBack CI journey (round 1 idea 6);
4. data and battery card with a low-data switch (round 1 idea 9);
5. direct vs stopping train label from the train number in `headsign` (round 2 C(b));
6. "Pay with…" button that opens a payment app by package name, or its store page (round 2 D);
7. Rav-Kav pass-expiry reminder (typed date, 0 requests);
8. accessible-bus badge from leg `wheelchairAccessible`, buses only, after a sample of ≤ 10 plans;
9. **last, optional:** an NFC Rav-Kav read spike with a go/no-go.

**Out of scope** (no letters, no new outside services): live data, platform numbers, Stride, the
Israel Railways API. Phase 11 adds no API key and no new outside service; the 2026-10-13 key
rule in `CLAUDE.md` is not used by any package. The weekly contract check (T2) calls only the
three services the app already uses.

The work is grouped into six feature packages (E) and four testing packages (T):

| package | items | wave | requests | review | cost |
|---|---|---|---|---|---|
| T1 Core test foundation | coverage gate (Kover); property tests; parser fuzz; request-budget inventory; saved-data migration samples | 1 (first) | 0 | none (boss checks CI) | $8–12 (Sonnet) |
| E1 Reminder check + pass expiry | 1 "will my reminder ring?" + test alarm; 7 Rav-Kav pass-expiry reminder | 1 | 0 | Sonnet logic | $12–18 |
| E2 I missed it | 2 "Missed it? Plan from here" in the ride, countdown and trip-changed notifications and the trip sheet | 2 | 1 `plan` per tap | Sonnet logic | $12–16 |
| T2 Scheduled tests | weekly mutation report (PIT); weekly live contract check; golden trips widened + invariants | 2 | contract: ≤ 5 Transitous + 1 Open-Meteo + 1 Photon a week; goldens ≤ 30 once | none | $6–10 (Sonnet) |
| E3 Leg labels | 5 train number + "fewer stops"; 8 accessible-bus badge (after a ≤ 10-plan sample) | 3 | 0 (≤ 10 recordings) | Sonnet logic + designer | $12–18 |
| T3 System journeys | Doze, app standby, reboot re-arm, time zone + DST, airplane mode, process death in the car tabs, weekly long monkey, flake log | 3 | 0 | none | $10–15 (Sonnet; Opus for a hard failure) |
| E4 Pay + data card | 6 "Pay with…"; 4 data & battery card + low-data switch | 4 | 0 | Sonnet designer | $12–18 |
| T4 Release + upgrade + journey inventory | release-APK smoke; upgrade test (old release → new); feature × test inventory; 5 gap journeys | 4 | 0 | none | $10–15 (Sonnet) |
| E5 Accessibility | 3 semantics on every screen; spoken leg text; a11y journey with blocking checks; font 2.0 goldens | 5 | 0 | Sonnet designer | $15–20 |
| E6 NFC spike (optional) | 9 read your own Rav-Kav: a debug-only prototype, then go/no-go | 6 | 0 (no network) | none (owner tests on a real card) | $6–10 (+ $8–12 if go) |

Total about **$105–150** of implementer work, plus about $1 per Sonnet review (7 reviews), so
**about $110–160** (another $8–12 if the NFC spike is a go).

## Order

**One Opus implementer at a time while the 7-day limit reads `allowed_warning`** (until about
Sat 18 Oct, 18:00 Israel time), plus at most one Sonnet session (a review or a Sonnet test
package). After that, two per wave as usual. Each wave allows two packages; the second one
starts when the first one's PR is green. Tests come first where they guard new features.

- **Wave 1:** T1 first (Sonnet, core tests only, about a day's work), then E1. T1's property
  tests for time math, its budget inventory and its saved-data samples guard E1's alarm times
  and new settings fields from the first E1 commit. They share no file.
- **Wave 2:** E2 and T2, after Wave 1 is merged. E2 uses E1's notification code and T1's budget
  inventory (it adds its row). T2 owns `core/build.gradle.kts` after T1 and adds only scheduled
  workflows; it shares no app file with E2.
- **Wave 3:** E3 and T3, after Wave 2 is merged. T3 drives E1's test reminder and E2's notification
  action on the emulator. E3 adds two fields to `api/Models.kt`, which T2's contract check then
  watches. **Release v0.9.1 after Wave 3** (proposal, owner decision 15): reminder check, pass
  expiry, missed it and train labels are the daily-use part.
- **Wave 4:** E4 and T4, after Wave 3 is merged. T4's upgrade test covers E1's and E4's new
  settings fields before the release.
- **Wave 5:** E5 alone. It touches every screen, so it runs after all other UI work.
- **Wave 6:** E6 alone, optional, only after the owner's yes (owner decision 14). Then **v0.10.0**.

## Shared files

These would conflict if two packages edited them at once, so each has one editor per wave.
"Hook" means at most one call or one field, placed where the brief says.

| shared file | w1 | w2 | w3 | w4 | w5 | w6 |
|---|---|---|---|---|---|---|
| `core/build.gradle.kts`, `gradle/libs.versions.toml` | T1 (Kover, kotest-property) | T2 (PIT plugin, shared invariants source dir) | | | | |
| `.github/workflows/ci.yml` | T1 (coverage step in `core`) | | | T4 (throwaway signing for `assembleRelease`) | | |
| `.github/workflows/ui-tests.yml`, `tools/ui_behaviour_run.sh`, `tools/ui_instrument.sh` | | | T3 (`system` job, flake log) | T4 (`release-smoke` job; more journeys on API 34/35) | E5 (a11y journey on API 35) | |
| `tools/check_docs.py` | T1 (budget rule) | | | T4 (journey-inventory rule) | | |
| `api/Models.kt` | | | E3 (two fields, appended) | | | |
| `api/Guards.kt`, `weather/OpenMeteo.kt`, `search/Photon.kt` (`Guarded*`) | | | | E4 (one usage counter call each) | | |
| `user/User.kt` (`UserSettings`, `UserJson`) | E1 (`passEndsOn`, `reminderCheckHintShown`) | | | E4 (`paymentApp`, `lowData`) | | |
| `remind/*` (android), `remind/Reminder.kt` (core) | E1 | E2 (action buttons only) | | | | |
| `ride/RideService.kt` | | E2 (one action) | | E4 (one GPS-minutes call) | | |
| `ui/screens/SettingsDialog.kt` | E1 (two rows) | | | E4 (two sections) | E5 (semantics) | E6 (one debug-only row) |
| `ui/screens/TripDetailsSheet.kt` | | E2 (one button) | E3 (labels) | E4 (one button) | E5 | |
| `ui/screens/TripPanel.kt`, `ui/screens/RideDetails.kt`, `present/RideDetails.kt` | | | E3 | | E5 | |
| other `ui/screens/*`, `ui/MainScreen.kt`, `ui/MapController.kt` | | | | E4 (stops-layer zoom in `MapController`, one line) | E5 | |
| `AndroidManifest.xml` | E1 (nothing expected) | | | E4 (`<queries>` packages) | | E6 (NFC permission + feature, not required) |
| `MainActivity.kt` | | E2 (the missed-it intent extra) | | | | |
| `ui/MainViewModel.kt`, `ui/MainActions.kt` | E1: one hook | E2: one entry point | E3: none expected | E4: one hook | E5: none expected | |
| `android/.../uitest/JourneysTest.kt` | E1 (one journey) | E2 (one journey) | | | | |
| `ProcessDeathProbe.kt`, `UiTestApp.kt` | | | T3 (tabs) | T4 (`UpgradeProbe` beside it) | | |
| `.claude/skills/transitous-api` | T1 (budget table note) | T2 (contract section), E2 (budget row) | E3 (`wheelchairAccessible`) | E4 (usage counter) | | |
| `.claude/skills/ui-testing` | | | T3 | T4 | E5 | |
| `ROADMAP.md` | each package ticks only its own `###` lines; E3 also corrects the "vehicle accessibility is not in the data" line in Phase 6 | | | | | |

The owner merges every PR; agents never merge.

## Request budgets

Every new call goes through `TransitApi`. Each cap is a constant pinned by a test, and each
row goes into the `transitous-api` budget table and T1's `BudgetInventoryTest` in the package
that adds it.

| package | call | when | cap | pinned by |
|---|---|---|---|---|
| E1 | none: the test reminder never re-checks the trip | Settings tap | 0 | `ReminderHealthTest`: the test alarm sends 0 |
| E2 | `plan` from the current fix to the same destination, leaving now | a tap on "Missed it" | 1 per tap (a second tap within 60 s hits the guard cache: 0) | `MissedItTest` + a `BudgetInventoryTest` row |
| T2 | weekly contract: `plan`, `trip` (from that plan), `stoptimes`, `geocode`, `reverse-geocode` on Transitous; 1 Open-Meteo `forecast`; 1 Photon `api` | Mondays, scheduled CI | ≤ 5 + 1 + 1 a week, ≥ 1 s apart | `tools/contract_check.py` counts and stops at the cap |
| T2 | golden trips (3 added) | one run by the agent (or the owner's PC) | ≤ 30 once | `golden-trips` report |
| E3 | `plan` recordings for the `wheelchairAccessible` sample | recording only | ≤ 10, once | the sample fixtures |
| E4 | none; low-data mode only removes requests (stops layer, night boards) | | 0 | `LowDataTest`: low data sends ≤ the normal count in each case |
| T1, T3, T4, E5, E6 | none | | 0 | |

---

## CI time (GitHub's free runners)

The repo is public, so standard-runner minutes cost $0; what matters is how long a PR waits and
the free plan's 20 jobs running at once. Measured today (run 38428022729, `main`): `UI tests`
takes ~20 min wall clock (the three screen shards, 14–17 min, are the long pole) and ~70
runner-minutes; `CI` (core, android, screenshots) ~15 runner-minutes.

| added by | where | runner-min per PR | wall clock |
|---|---|---|---|
| T1 Kover + property + fuzz tests | `core` job, every push | +2 | +2 (the core job gates the others) |
| T3 `system` job (API 34) | UI tests | +12 | 0 (parallel, shorter than a shard) |
| T4 `release-smoke` job (API 35) | UI tests | +7 | 0 |
| T4 gap journeys (API 34 + 35 only) | UI tests | +4 | 0 |
| E5 a11y journey (API 35) + font 2.0 Paparazzi variant | UI tests + screenshots | +4 | +1 (screenshots job) |
| T4 upgrade test | own workflow, only when a PR touches saved data, the manifest or the version | +15 when it runs | 0 (parallel) |

**Per PR that touches the app: about +27 runner-minutes (≈ +30 %), +3 min wall clock; +15 more
when the upgrade test runs. A core-only or docs PR: +2 min.** The UI tests workflow grows from
11 to 13 jobs (16 with CI), still under 20.

**Weekly, scheduled:** mutation report ~45 min, contract check ~2 min, long monkey ~20 min,
upgrade test ~15 min: about 80 runner-minutes a week. Test Lab (if set up) runs on Google's
quota, not GitHub's.

## Flake policy (all packages)

Quarantine-free, as today: nothing is skipped, disabled or quarantined. `tools/ui_instrument.sh`
keeps its one retry per test for a process killed with an empty crash buffer. T3 adds a record:
every retry, and every job re-run, is posted as one comment on a single open issue "CI flake
log" (the workflow's `GITHUB_TOKEN`, `issues: write`), with the run link, API level, test and
kind. The boss reads it at each close-out; **a test that flakes twice in 14 days gets a fix
package**, never a skip.

---

## Reviews

- **No Gemini** (owner, 2026-10-12). Reviews are a **targeted Sonnet pass** only where the
  table says:
  - *designer* (packages that change screens): from the CI `screenshots` artifact, light and
    dark, Hebrew and English, small and large phones; loads `i18n-rtl`;
  - *logic* (alarms, dates, routing, data): the brief's "Reviewer checks" list and the
    `parallel-work` definition of done, nothing wider.
  The boss starts it once the PR is green. The implementer fixes what it finds on its own
  branch. Then the owner merges.
- Test packages (T1–T4) get no review: the boss checks that the new jobs ran on the PR head and
  what they reported.

**Models.** E1–E6 run on **Opus**. T1, T2 and T4 run on **Sonnet**. T3 runs on Sonnet; if an
emulator step fails for a reason it cannot find in two tries, it stops and tells the boss, who
may re-start it on Opus. Reviews run on Sonnet. Every session is created with the tag
`remote-agents-project:israel-transit`.

---

## Common preamble (sent first to every session)

> You are one of several agents working on Israel Transit Planner, each on its own branch and
> PR. You run on Opus (T1, T2, T3, T4: Sonnet).
>
> Before writing any code:
> 1. Read `CLAUDE.md`.
> 2. Load the `parallel-work` skill: file ownership, conflict rules, definition of done, token
>    rules.
> 3. Load `dead-ends`.
> 4. Load the skills your package names.
> 5. Read the top of `docs/agents/phase11.md`: the shared-file table says which shared files
>    you may touch and where, the budget table pins your requests, and the flake policy
>    applies to every package.
>
> Your package is the `###` subsection of `ROADMAP.md` Phase 11 named below. Tick only those
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
>   English, then the same in Hebrew (test-only packages: none).
> - **Requests:** every new request goes through `TransitApi`, and its cap is pinned by a test
>   and a row in `BudgetInventoryTest` (after T1). Phase 11 adds no outside service and no key.
> - **Transitous traffic:** live calls are for recording fixtures only (`tools/record_fixture.py`),
>   within the number your brief gives.
> - **Tests:** no skipped, disabled or quarantined test, ever (flake policy in `phase11.md`).
>   A test that finds a real bug outside your files: write the failing case in the PR body,
>   keep that test out, tell the boss.
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

## T1: Core test foundation
**Branch:** `claude/p11-core-tests`
**Skills:** `transitous-api`, `special-features`, `android-build`
**Needs:** nothing (starts from `main`)
**Owns:** `core/build.gradle.kts` and `gradle/libs.versions.toml` (Kover and kotest-property
only), the `core` job of `.github/workflows/ci.yml` (one coverage step), new test files only:
`core/src/test/.../prop/*PropertyTest.kt`, `ParserFuzzTest.kt` + `Fuzz.kt` (helper),
`BudgetInventoryTest.kt`, `UserDataMigrationTest.kt` + `fixtures/userdata/` (new),
`android/app/src/test/.../SavedUiPropertyTest.kt`, `tools/gen_userdata_sample.sh` (new),
one rule in `tools/check_docs.py`; small fixes (≤ ~20 lines) where a new test finds a real bug
**Shared-file hooks:** none in app code

> Runs on **Sonnet**. **0 requests.** No sockets. Every new test is deterministic: a fixed seed
> and a fixed number of cases in CI, and the seed printed on failure so it can be replayed.
> The whole `core test` may grow by at most ~2 min on CI.
>
> **1. Coverage, measured and gated (Kover).** Add the Kover Gradle plugin to `core` only.
> - `./gradlew -p core koverXmlReport koverVerify` in the CI `core` job, after the tests; line
>   coverage per package into the job summary (a small script, no upload service).
> - Exclude generated data (`RailStations`, `HolidaysData`, `Towns` / `NeighbourhoodsData` and
>   other `gen_*.py` output).
> - The gate: line coverage of `core` main ≥ **today's measured value rounded down to a whole
>   %** (owner decision 11). Write the measured value and the date in `android-build`. The
>   gate only ratchets up: a PR may raise it, never lower it.
>
> **2. Property-based tests** (kotest-property, used standalone from JUnit 4; no new runner).
> At least these, each with 300–1000 generated cases:
> - **time and date math:** the 04:00 service-day edge; `Asia/Jerusalem` across both DST
>   changes (Sun 25 Oct 2026 and Fri 26 Mar 2027); a countdown is never negative and never
>   jumps; holidays: every year 2026–2031 has each holiday once (`calendar/`);
> - **Pareto** (`features/`): the front has no dominated option, is a subset of the input, does
>   not depend on input order, and running it twice changes nothing;
> - **fares** (`fare/`): never negative; a longer band never costs less; every profile costs ≤
>   regular; a free profile costs 0;
> - **`LiveProgress`** (`remind/`): progress in [0, 1], never goes back as time moves on,
>   segments add up to the trip;
> - **`UserJson`**: decode(encode(x)) == x for generated settings, places, trips, routines,
>   My lines; unknown fields are ignored; a missing field gets its default;
> - **`SavedUi`** (android unit test, runs in the `screenshots` job): the saved-state round trip.
>
> **3. Parser robustness (fuzz).** `ParserFuzzTest` with a seeded mutator of our own (no new
> dependency): for every fixture of each kind (`plan`, `stoptimes`, `trip`, `mapTrips`,
> `oneToMany`, `geocode`, `reverse-geocode`, Open-Meteo, Photon), ~200 mutants each: cut at a
> random byte, drop a key, null a value, a number as a string, an array for an object, empty
> arrays, huge numbers, RTL marks and emoji in names, a bad time offset. Expected: it parses,
> or throws the documented error (`SerializationException` / `IllegalArgumentException` /
> `IllegalStateException`, see the fault table in `transitous-api`). Never a
> `NullPointerException`, `ClassCastException`, index error or stack overflow. Every mutant that
> parses then goes through the `present/` code (summaries, ride details, transfers, leg chips,
> fare estimate) without a crash. Under 60 s in all.
>
> **4. Request-budget inventory.** `BudgetInventoryTest`: one row per feature entry point (Trip
> plan, Earlier/Later, Better start, Drop-off, Pick-up, Park & ride, errands, last trip home,
> night refresh trips and boards, big-stop change, day timetable, stop code, typing search,
> pin name, rain), each run over its fixtures through a counting `TransitApi`, asserting count
> ≤ its cap and naming the constant. A new rule in `tools/check_docs.py`: every `BUDGET` /
> `MAX_*` request constant in `core/src/main` appears in this test's table and in the
> `transitous-api` budget section. Later packages add their row.
>
> **5. Saved-data migration samples.** `fixtures/userdata/<version>.json`: the user data that
> released versions wrote (at least v0.8.0, v0.8.6, v0.9.0, as tagged), with places, Home and
> Work, saved trips, a routine, My lines, history and non-default settings. Made by
> `tools/gen_userdata_sample.sh`, which runs that tag's `UserJson` in a `git worktree` (never
> written by hand; if a tag doesn't build in the session, run it in a CI `workflow_dispatch`
> job or say so in the PR). `UserDataMigrationTest`: each sample decodes with today's code to
> the expected values. Every later release adds its sample (`release-apk` note).
>
> **A test that finds a real bug:** fix it if small and in core; otherwise write the failing
> case in the PR body, keep that test out of the PR, and tell the boss.
>
> **Docs:** `android-build` (Kover: the command, the gate value and how it ratchets; the
> property-test seed rule); `transitous-api` (the budget inventory and the check); `release-apk`
> (add a user-data sample per release); no release fragment; tick ROADMAP T1.

## E1: Reminder check + pass expiry
**Branch:** `claude/p11-reminder-check`
**Skills:** `reminders-offline`, `i18n-rtl`, `add-feature`, `android-build`
**Needs:** T1 merged
**Owns:** `remind/ReminderHealth.kt` (core, new), `remind/PassExpiry.kt` (core, new),
android `remind/ReminderCheck.kt` (new: gathers the phone's state), `remind/Notifications.kt`,
`remind/ReminderReceiver.kt` (incl. `BootReceiver`), `remind/ReminderScheduler.kt`,
`ui/screens/ReminderCheck.kt` (new), `ui/screens/PassExpirySettings.kt` (new),
`strings_reminder_check.xml`
**Shared-file hooks:** `user/User.kt`: `UserSettings.passEndsOn: String? = null` (ISO date) and
`reminderCheckHintShown: Boolean = false`; `SettingsDialog`: two rows ("Check reminders",
"Rav-Kav pass ends on"); `MainViewModel`: one hook that shows the one-time hint;
`JourneysTest`: one journey

> **0 requests.**
>
> **1. "Will my reminder ring?" (item 1).** Settings → **Check reminders**.
> - `remind/ReminderHealth.kt` (core, pure): inputs are the phone's state as plain values
>   (notifications allowed for the app; each channel's importance; `POST_NOTIFICATIONS` on 33+;
>   exact alarms allowed; `isIgnoringBatteryOptimizations`; `isBackgroundRestricted` (28+);
>   the app-standby bucket (28+); location permission for the ride alert; the maker, from
>   `Build.MANUFACTURER`). Output: a list of problems, each with a kind, how bad it is, the
>   text key and the action. No problems → "All set: reminders should ring on this phone".
> - android `ReminderCheck.kt` reads the state; the screen lists the problems, each with one
>   button that opens exactly that page: `ACTION_APP_NOTIFICATION_SETTINGS`,
>   `ACTION_CHANNEL_NOTIFICATION_SETTINGS`, `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (31–32),
>   `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (the list: no new permission), the app's
>   details page for "Battery → Unrestricted". A maker line for Samsung, Xiaomi / Redmi / POCO,
>   Huawei / Honor, OnePlus / Oppo / Realme (owner decision 3: generic wording, the maker's
>   name, no promise about the maker's menus).
> - **Test reminder in 1 minute:** arms the same alarm type through `ReminderScheduler`,
>   flagged as a test, so the receiver posts "Test: your reminders work" on the
>   `trip_reminders` channel and never re-checks a trip (0 requests). The alarm uses the system
>   clock (`AlarmManager`), not `TransitApp.clock`. The screen then shows "Last test rang at
>   10:42" or, 3 min after the due time with no delivery stored, "The test did not ring" with
>   the most likely problem first. It also gives T3 its Doze and reboot seam.
> - After the first reminder is set, one hint, once: "Check that reminders will ring on this
>   phone" → the check.
>
> **2. Rav-Kav pass expiry (item 7).** Settings → **Rav-Kav pass ends on** (a date, or Off).
> - `remind/PassExpiry.kt` (core, pure): when to notify (owner decision 4: **3 days before and
>   on the last day, at 09:00 Israel time**); a past date is cleared; a date more than 400 days
>   ahead is refused. The text: "Your Rav-Kav pass ends on Sun 1 Nov". It says nothing about
>   prices.
> - An inexact alarm (`setAndAllowWhileIdle`) through `ReminderScheduler`, on a new channel
>   "Rav-Kav pass" (default importance); `BootReceiver` and a time-zone change re-arm it;
>   changing the date re-arms, Off cancels. The reminder check includes this channel. A tap
>   opens Settings for now (E4 points it at the payment app).
>
> **Tests:** `ReminderHealthTest.kt` (each problem alone; API 26, 31, 33, 34, 35; each maker
> line; all fine; the test alarm sends 0), `PassExpiryTest.kt` (3 days and last day, the DST
> change, 29 Feb 2028, a last day on Shabbat still notifies at 09:00, past date, changed date,
> Off), `UserJsonTest` (the two fields; old files decode) plus T1's migration samples. Journey
> `j15_test_reminder_rings`: Settings → Check reminders → Test → the notification is posted
> within 90 s. Paparazzi: the check with 0, 1 and 3 problems; the pass row.
>
> **Reviewer checks (Sonnet logic):** API 26 (no channels) and 33 (`POST_NOTIFICATIONS`
> denied): the test button says it can't ring rather than pretending; exact alarms denied on
> API 31/32; the pass date across DST; reboot re-arms both alarms; the test reminder never
> starts a trip re-check.
>
> **Docs:** `reminders-offline` (the check, the test alarm, pass expiry); a release fragment
> `docs/releases/next/e1-reminder-check.md`; tick ROADMAP E1.

## E2: I missed it
**Branch:** `claude/p11-missed-it`
**Skills:** `reminders-offline`, `transitous-api`, `i18n-rtl`, `add-feature`
**Needs:** Wave 1 merged
**Owns:** `plan/MissedIt.kt` (core, new), `ui/screens/MissedIt.kt` (new, the banner),
`strings_missed_it.xml`
**Shared-file hooks:** `remind/Notifications.kt` and `ride/RideService.kt`: one action button
each on the ride, leave-countdown and trip-changed notifications; `MainActivity.kt`: the
`missed_it` intent extra; `ui/screens/TripDetailsSheet.kt`: one button; `MainViewModel` /
`MainActions`: one `missedIt(destination)` entry point; `BudgetInventoryTest`: one row;
`JourneysTest`: one journey

> **1 `plan` per tap**, never automatic, never in the background.
>
> **What the user gets (item 2):** "Missed it? Plan from here" in the ride notification, the
> leave-countdown notification, the "trip changed" notification and the trip sheet. One tap
> opens the Trip tab with options from where the phone is now to the same destination, leaving
> now, with the same settings and sort, and a banner "From your location at 07:46".
>
> **Core (`plan/MissedIt.kt`):**
> - The start: the last fix if ≤ 2 min old and ≤ 100 m accuracy; otherwise wait ≤ 10 s for a
>   new one; otherwise open the Trip tab with From empty and focused ("Where are you?"). No
>   reverse geocode: the start is "My location" (0 extra requests).
> - The plan: the original destination, `TransitApp.clock` now, depart-at (an arrive-by trip
>   becomes depart-now and the banner says so), through `TripPlanner` (guarded, so a second
>   tap within 60 s costs 0). From a car tab (Better start, Drop-off, Pick-up, Park & ride) it
>   plans a plain Trip to the final destination.
> - Within 150 m of the destination: "You're already there", no request.
> - The old trip's ride alert and countdown stop (owner decision 5); the user starts the new
>   one with Start trip. The old reminder for a later day is untouched.
> - Without location permission: the button asks for the start, as above. Location is read only
>   on the tap.
>
> **Notification actions:** a `PendingIntent` to `MainActivity` with `missed_it` and the
> destination; works when the app was killed (the destination travels in the intent, not in
> memory). Same notification ids as today: no second notification.
>
> **Tests:** `MissedItTest.kt` (1 request; fresh, stale and no fix; at the destination: 0;
> arrive-by → depart-now; a car tab → Trip; the same settings and sort; a second tap: 0), a row
> in `BudgetInventoryTest`. Journey `j16_missed_it_from_ride_notification`: start a ride on the
> mock location, tap the notification action, results show and `app.replay.calls` grew by 1.
> Use a start point the replay already answers (record at most **2** `plan` calls if none
> does). Paparazzi: the banner, Hebrew and English.
>
> **Reviewer checks (Sonnet logic):** a tap from a killed app; two fast taps = 1 request; no
> location permission; arrive-by; the countdown really stops; RTL banner.
>
> **Docs:** `reminders-offline` (the action, what stops); `transitous-api` (the budget row);
> a release fragment `docs/releases/next/e2-missed-it.md`; tick ROADMAP E2.

## T2: Scheduled tests (mutation, contract, golden trips)
**Branch:** `claude/p11-scheduled-tests`
**Skills:** `transitous-api`, `golden-trips`, `android-build`
**Needs:** T1 merged
**Owns:** `core/build.gradle.kts` (the PIT plugin and a shared `src/invariants` source dir
only), `.github/workflows/mutation.yml` (new), `.github/workflows/contract.yml` (new),
`tools/contract_check.py` (new), `core/src/test/resources/contract/` (new, generated shapes),
`core/src/invariants/` (new: the checks of `RoutingInvariantsTest`, moved so `test` and
`golden` share them), `core/src/golden/` (new trips)
**Shared-file hooks:** `RoutingInvariantsTest.kt`: calls the moved checks

> Runs on **Sonnet**.
>
> **1. Mutation testing, report only (PIT).** `mutation.yml`: weekly (Sunday night) and
> `workflow_dispatch`, timeout 60 min. Plain PIT (Apache-2.0, its JUnit 4 support) on the pure
> packages: `present/`, `fare/`, `features/`, `plan/` (`TripPages`, `sortOptions`), `remind/`,
> `ride/`, `calendar/`. Filter Kotlin's generated null checks (`avoidCallsTo kotlin.jvm.internal`).
> The job summary lists the mutation score per package and the 20 surviving mutants that most
> look like a missing assertion; the HTML report is an artifact. **No gate** (owner decision
> 16). One run in the PR; write today's scores in `android-build`.
>
> **2. Weekly live contract check.** `contract.yml`: Mondays 04:47 Israel time (cron in UTC,
> written for winter time, with a comment) and `workflow_dispatch`. `tools/contract_check.py`
> (Python stdlib only):
> - sends `MotisClient.USER_AGENT`, ≥ 1 s apart, and **stops at the cap: ≤ 5 Transitous**
>   (`plan` Be'er Sheva → Tel Aviv next Tuesday 08:00; `trip` for that plan's first transit leg;
>   `stoptimes` at one stop; `geocode` "דיזנגוף 50 תל אביב"; `reverse-geocode` one point),
>   **1 Open-Meteo** `forecast` and **1 Photon** search (owner decision 9);
> - compares the shape (keys and JSON types) of the fields `Models.kt`, `OpenMeteo` and
>   `Photon` read with shapes generated from the recorded fixtures (`--update-shapes` rewrites
>   them; never by hand). Also reports: 0 itineraries for a date within
>   `DayNotice.HORIZON_DAYS` (the timetable horizon shrank), and any new `realTime: true` in
>   the `stoptimes` answer (good news worth a look);
> - on drift or an error, opens one issue labelled `contract-drift` (or comments on the open
>   one) with the diff, through the workflow's `GITHUB_TOKEN` (`issues: write`, nothing else).
>   A network failure is reported, never retried more than once.
> - The weekly calls are listed in `transitous-api` as routine use, with the count.
>
> **3. Golden trips widened.** Add 3 trips to `golden-trips` and `core/src/golden/`: Jerusalem
> light rail (Mount Herzl → Central Station), Nazareth → Haifa (a town the corpus hardly
> covers), Eilat → Be'er Sheva (a long intercity). The runner now also applies every check of
> the shared invariants (times in order, legs chain, Pareto, budgets, fares ≥ 0, ride details
> add up) to the live answers. One live run, **≤ 30 requests**, from the session if the host is
> allowed, else ask the owner to run `./gradlew -p core goldenTrips` and paste the report.
>
> **Docs:** `android-build` (mutation: how to run, today's scores, no gate); `transitous-api`
> (the contract check, its calls, how to update the shapes after a planned model change);
> `golden-trips` (the 3 trips, the invariants); no release fragment; tick ROADMAP T2.

## E3: Leg labels (train number + accessible bus)
**Branch:** `claude/p11-leg-labels`
**Skills:** `transitous-api`, `i18n-rtl`, `add-feature`, `golden-trips`
**Needs:** Wave 2 merged (T2's contract check then watches the new fields)
**Owns:** `present/TrainLabel.kt` (new), `present/AccessibleBadge.kt` (new),
`present/RideDetails.kt`, `ui/screens/RideDetails.kt`, `ui/screens/LegLabels.kt` (new),
`strings_leg_labels.xml`, the new sample fixtures
**Shared-file hooks:** `api/Models.kt`: `Leg.wheelchairAccessible: String? = null` and
`Leg.tripShortName: String? = null` (appended, null defaults); `ui/screens/TripPanel.kt`: one
line on the card (the leg chips); `ui/screens/TripDetailsSheet.kt`: the label in a rail leg's
header and the badge in a bus leg's header

> **0 requests at run time. ≤ 10 `plan` recordings.**
>
> **1. Sample first (item 8's gate).** Record **at most 10** weekday 08:00 `plan` calls, bus
> heavy, in different cities and operators (for example Tel Aviv, Jerusalem, Haifa, Be'er
> Sheva, Bnei Brak, Ashdod, Nazareth, Rahat, Eilat, a Galilee village). Count
> `wheelchairAccessible` per mode × value × agency and put the table in the PR and in
> `transitous-api`.
> - **Go for the badge** if ≥ 90 % of bus legs carry `ACCESSIBLE` or `NOT_ACCESSIBLE`, and the
>   same route and operator always carries the same value in the sample. Otherwise **no badge**:
>   a `dead-ends` row with the counts, and the package ships the train label only.
> - Rail is never labelled: all rail legs read `NOT_ACCESSIBLE`, which looks like a default.
>   Light rail too, unless the sample shows it consistently `ACCESSIBLE` (then say so in the
>   PR and ask the boss; don't decide).
>
> **2. Accessible-bus badge (item 8).** `present/AccessibleBadge.kt` (pure). Shown only with
> the Accessible-routes setting on (owner decision 7). A bus leg with `ACCESSIBLE`: a small
> wheelchair badge and the words "Accessible bus" (never an icon alone). `NOT_ACCESSIBLE`: an
> amber line "This bus may not be wheelchair accessible". Missing or `UNKNOWN`: nothing. Never
> on rail or another mode. The `ROADMAP.md` Phase 6 line "vehicle accessibility is not in the
> data" is corrected to say what the data has.
>
> **3. Train number and "fewer stops" (item 5).** `present/TrainLabel.kt` (pure):
> - a rail leg (not tram) whose `headsign` is 1–4 digits: "Train 24 to Nahariya · 3 stops",
>   the terminus from the route's long name (`Be'er Sheva ⇄ Nahariya`, the end in the direction
>   of travel) and the stops between boarding and getting off; a non-numeric `headsign` keeps
>   today's text;
> - between the options of one answer, rail legs between the same two stations (same id or
>   parent station) leaving within 30 min of each other: the one with **at least 2 fewer stops**
>   gets a "Fewer stops" chip (owner decision 6). Never the words "express" or "direct": the
>   operator's own label is not in the data.
> - Shown in the card's leg chips, the ride-details line and the trip sheet's rail header.
>
> **Tests:** `TrainLabelTest.kt` (24 and 404 from the recorded Tel Aviv → Haifa case if a fixture
> has it, else the new sample; a bus `headsign`; a tram; 0 stops between; two trains 25 min
> apart with 3 and 6 stops; 2 trains with equal stops: no chip; Hebrew word order),
> `AccessibleBadgeTest.kt` (each value, rail ignored, setting off), old fixtures without the
> fields still decode (T1's fuzz covers the rest). Paparazzi: a card with a train label and a
> "Fewer stops" chip; a bus leg with each badge, dark and light.
>
> **Reviewer checks (Sonnet logic + designer):** the go/no-go numbers against the fixtures;
> no badge on rail; the chip only between comparable trains; RTL order of "Train 24 · 3 stops";
> the amber line in dark mode; the smallest phone in Hebrew.
>
> **Docs:** `transitous-api` (the `wheelchairAccessible` sample and what it means, the train
> number in `headsign`); `i18n-rtl` (the badge and the chip); `dead-ends` if no-go; ROADMAP's
> Phase 6 line; a release fragment `docs/releases/next/e3-leg-labels.md`; tick ROADMAP E3.

## T3: System journeys (Doze, reboot, time zone, offline, flake log)
**Branch:** `claude/p11-system-journeys`
**Skills:** `ui-testing`, `reminders-offline`, `android-build`
**Needs:** Wave 2 merged (E1's test reminder, E2's notification action)
**Owns:** `.github/workflows/ui-tests.yml` (the `system` job), `tools/ui_system_run.sh` (new),
`tools/ui_instrument.sh` (the flake log), `.github/workflows/weekly-ui.yml` (new),
`ProcessDeathProbe.kt` (more tabs), `android/.../uitest/SystemJourneysTest.kt` (new);
`devices.yml` only if owner decision 13 says Test Lab is set up
**Shared-file hooks:** none in app code (a real bug found: report it, don't fix app code)

> Runs on **Sonnet**; two failed tries at an emulator step you can't explain → stop and tell
> the boss. **0 requests.** Everything runs on the `uitest` build (replayed fixtures).
>
> **1. A `system` job** on API 34 (`google_apis`, `adb root` allowed), ≤ 15 min, parallel to the
> shards. `tools/ui_system_run.sh` runs these steps; each prints what it saw and fails the job
> only on a broken promise:
> - **Doze:** arm E1's test reminder, `dumpsys deviceidle force-idle`, the notification is posted
>   within 2 min of its time. Then `unforce`.
> - **App standby:** `am set-standby-bucket <pkg> rare`: the test reminder still rings
>   (blocking). `restricted`: record whether it rings, and check E1's reminder check reports
>   the restriction (a finding, not a failure).
> - **Reboot re-arm:** arm a trip reminder and a pass-expiry date, `adb reboot`, wait for boot:
>   `dumpsys alarm` lists both again; no ride service came back by itself.
> - **Time zone and DST:** with a reminder armed, switch the phone to `Europe/London` and back:
>   the alarm's absolute time is unchanged and nothing is armed twice. Pin what the screens show
>   in a foreign time zone (find the rule in `present/Format.kt`; assert it, don't change it).
>   Set the clock to Sat 24 Oct 2026 23:58 Israel and arm the test reminder across the
>   25 Oct DST change: it rings once.
> - **Airplane mode:** `cmd connectivity airplane-mode enable` (API 30+): the offline banner,
>   saved results, no crash; disable: Retry works.
> - **Process death across tabs:** extend `ProcessDeathProbe` to Drop-off and Park & ride
>   (selected option, the car path on the map, the open sheet); the Trip-tab check stays on
>   every API level.
>
> **2. Weekly long monkey.** `weekly-ui.yml` (Saturday night, `workflow_dispatch`): API 35,
> 3 seeds × 20 000 events, crash/ANR gate, the seeds in the summary. The PR run keeps today's
> one seed.
>
> **3. Flake log.** When `ui_instrument.sh` retries a test, or a job is a re-run
> (`github.run_attempt > 1`), a final step comments on the open issue "CI flake log" (creates
> it once) with the run link, API, test and kind (`issues: write` for that step only).
>
> **4. Test Lab (only if owner decision 13 says the Spark project exists):** add the system
> steps that work without `adb root` to the weekly `devices.yml` run on one real phone.
>
> **Docs:** `ui-testing` (the system job: steps, what is blocking, how to read it; the weekly
> monkey; the flake log and the "two in 14 days" rule); no release fragment; tick ROADMAP T3.

## E4: Pay with… + data and battery card
**Branch:** `claude/p11-pay-data`
**Skills:** `i18n-rtl`, `add-feature`, `transitous-api`, `android-build`
**Needs:** Wave 3 merged
**Owns:** `present/PayApps.kt` (core, new), `api/UsageLog.kt` (core, new),
`user/LowData.kt` (core, new), `ui/PayWith.kt` (new), `ui/screens/PaySettings.kt` (new),
`ui/screens/DataCard.kt` (new), `data/UsageStore.kt` (new), `strings_pay_data.xml`
**Shared-file hooks:** `user/User.kt`: `paymentApp: String? = null`, `lowData: Boolean = false`;
`SettingsDialog`: a "Paying for rides" section and a "Data & battery" card; `TripDetailsSheet`:
one "Pay with…" button; `AndroidManifest.xml`: one `<queries><package/>` per listed app;
`api/Guards.kt`, `GuardedWeather`, `GuardedGeocoder`: one counter call each;
`ride/RideService.kt`: one GPS-minutes call; `work/NightRefreshWorker.kt` and `MapController`:
one low-data check each; E1's pass-expiry notification tap → the payment app;
`MainViewModel`: one hook

> **0 requests.** Low-data mode only removes requests.
>
> **1. Pay with… (item 6).** We never process a payment; we open another app.
> - `present/PayApps.kt` (pure): the owner-approved list (owner decision 1), each with Hebrew and
>   English names, the package name and the store URL. **Confirm each package name from its
>   Google Play page** (`play.google.com/store/apps/details?id=…`) and write the date checked;
>   a name you cannot confirm stops that row (ask the boss).
> - `ui/PayWith.kt`: `getLaunchIntentForPackage` (with a `<queries>` entry each); not installed →
>   `market://details?id=…`, falling back to the https store page.
> - Settings → "Paying for rides" → "My payment app": None (default) or one of the list.
>   The trip sheet's button says "Pay with Moovit" when one is chosen, else "Pay with…" opens a
>   short chooser, installed apps first. One line under it: "Opens the other app. This app
>   doesn't take payments."
> - The pass-expiry notification (E1) opens the chosen app, else the chooser.
>
> **2. Data and battery card (item 4).** Settings → **Data & battery**: "Today: 14 requests to
> Transitous (6 answered from the cache) · weather 1 · address backup 0 · map cache 38 MB ·
> night refresh 02:14 (4 requests) · ride GPS 12 min".
> - `api/UsageLog.kt` (core, pure): counts per day per service and per feature (network vs
>   cache, retries counted), the day ending at midnight Israel time, 7 days kept. Only counts and
>   times: no place, no query text. `data/UsageStore.kt` persists it (small JSON).
> - Map cache size: the size of MapLibre's ambient cache file, read, never changed by the card.
>
> **3. Low-data mode** (owner decision 2), one switch, off by default:
> - the stops layer loads from zoom 16 instead of 15 and fetches no neighbouring tiles;
> - the night refresh saves no stop boards (trips only, still Wi-Fi + charging);
> - the offline map area download asks first unless on Wi-Fi;
> - planning, search, reminders and the rain note are unchanged.
> `user/LowData.kt` (pure) says what each part does with the switch on; tested.
>
> **Tests:** `PayAppsTest.kt` (the list, the store URLs, names in both languages),
> `UsageLogTest.kt` (cache vs network, a retry, the Israel midnight rollover, 7 days, the JSON
> holds no text but keys and numbers), `LowDataTest.kt` (each part; low data never sends more
> than normal), `UserJsonTest` (the two fields) and T1's migration samples. Paparazzi: the
> card (empty day, busy day), the chooser, the trip-sheet button.
>
> **Reviewer checks (Sonnet designer):** the card on the smallest phone in Hebrew at font 1.3;
> the chooser with 0, 1 and 4 apps installed; the disclaimer visible; dark mode.
>
> **Docs:** `transitous-api` (the usage counter; low data in the budget table);
> `reminders-offline` (low data and the night refresh); a release fragment
> `docs/releases/next/e4-pay-data.md`; tick ROADMAP E4.

## T4: Release smoke, upgrade test, journey inventory
**Branch:** `claude/p11-release-upgrade`
**Skills:** `ui-testing`, `release-apk`, `android-build`
**Needs:** Wave 3 merged (runs beside E4; its upgrade test covers E4's fields once E4 merges)
**Owns:** `.github/workflows/ui-tests.yml` (the `release-smoke` job), `.github/workflows/ci.yml`
(a throwaway keystore for the `assembleRelease` step), `.github/workflows/upgrade.yml` (new),
`.github/workflows/release.yml` (one upgrade step before publishing), `tools/ui_release_run.sh`
and `tools/ui_upgrade_run.sh` (new), `UpgradeProbe.kt` (new, uitest only),
`android/.../uitest/MoreJourneysTest.kt` (new), `docs/testing/inventory.md` (new), one rule in
`tools/check_docs.py`
**Shared-file hooks:** `UiTestApp.kt`: register `UpgradeProbe`

> Runs on **Sonnet**. **0 requests**: the release APK runs with the emulator's network off.
>
> **1. Release-APK smoke.** CI signs `assembleRelease` with a keystore made in the job by
> `keytool` (never committed, never a secret). The `release-smoke` job (API 35, ≤ 8 min):
> `aapt2 dump badging` shows the app id `il.transit.planner`, the version, not debuggable, no
> `uitest` classes; with airplane mode on, launch, skip the tour, open Settings, then a 500-event
> monkey: no crash, no ANR, and `logcat` shows no request leaving. The release build is not
> minified today (`isMinifyEnabled = false`); say in the skill that this job is where a minify
> change will show.
>
> **2. Upgrade test (old release → new).** `upgrade.yml`: `workflow_dispatch`, weekly, and pull
> requests touching `core/src/main/**/user/**`, `core/src/main/**/history/**`,
> `android/**/data/**`, `AndroidManifest.xml` or `android/app/build.gradle.kts`.
> - Build the latest release tag's `uitest` APK and HEAD's, both with the job's debug key.
> - Install the old one, launch it, write T1's sample user data for that version into its
>   DataStore files with `run-as` (paths read from that tag's `UserStore`), launch again so it
>   arms its reminder and night refresh.
> - `adb install -r` the new one (no uninstall). `UpgradeProbe` logs: saved places, Home/Work,
>   saved trips, routines, My lines, history count, settings, the reminder re-armed
>   (`MY_PACKAGE_REPLACED`), the night refresh still enqueued, notification channels intact.
>   The job compares them with the sample: any loss fails.
> - `release.yml`: before publishing, install the previous release's published APK (its public
>   asset URL) on an emulator, then the newly signed one with `adb install -r`: it must install
>   over it (owner decision 12). About +8 min per release.
>
> **3. Journey inventory.** `docs/testing/inventory.md`: every user-visible feature in
> `ROADMAP.md` × its unit test × Paparazzi shot × emulator journey × real-phone check, with the
> gaps marked. A `check_docs.py` rule: every journey named there exists, and every `j*` journey
> is listed. Add journeys for the **5 biggest gaps** in `MoreJourneysTest.kt` (API 34 and 35
> only), proposed: Drop-off pick and send, Park & ride, stop sheet → day timetable, share an
> address in (`am start` with `ACTION_SEND`), monthly report export. Each asserts what the user
> sees and `app.replay.calls`.
>
> **Docs:** `ui-testing` (release smoke, upgrade test, the inventory); `release-apk` (the upgrade
> step and why); no release fragment; tick ROADMAP T4.

## E5: Accessibility pass + TalkBack journey
**Branch:** `claude/p11-accessibility`
**Skills:** `i18n-rtl`, `ui-testing`, `add-feature`, `android-build`
**Needs:** Wave 4 merged (it touches every screen)
**Owns:** `present/Speech.kt` (core, new), semantics in every `ui/screens/*` file,
`ui/MainScreen.kt`, `ui/MapController.kt` (the map's semantics only),
`android/.../uitest/A11yJourneysTest.kt` (new), `LayoutAudit.kt` / `Atf.kt` (rules made
blocking), `PanelsTest.kt` (a font 2.0 variant), `strings_a11y.xml`
**Shared-file hooks:** `tools/ui_behaviour_run.sh`: run `A11yJourneysTest` on API 35

> **0 requests.** No visible change except where a target was too small.
>
> **1. Semantics on every screen (item 3).**
> - `present/Speech.kt` (pure, he/en through string keys): what a leg, an option, a departure
>   row and a transfer say aloud: "Bus 47, 08:10 to 08:34, 12 stops, get off at Rager/Oren";
>   times as times, not digits; "Train 24 to Nahariya, 3 stops" (E3); the badge text (E3).
> - Compose: one merged node per card and leg chip (`mergeDescendants`), `Role` and
>   `stateDescription` on chips, tabs and toggles ("Fastest, selected"), `heading()` on section
>   titles, labels on every icon button, `liveRegion = Polite` on the countdown (at most once a
>   minute), the map marked "Map: the list has the same options" and skipped by the reading
>   order where the list holds the same content, reading order checked in RTL.
> - Every target ≥ 48 dp; colour never the only signal (leg colours keep a mode word or icon).
>
> **2. The CI journey (TalkBack).** GitHub's emulator images have no TalkBack (it ships with
> Google's Accessibility Suite, not in `google_apis`), so CI checks what TalkBack would read,
> and the owner checks the real thing (owner decision 8):
> - `A11yJourneysTest` (API 35, Hebrew and English): search → results → select → trip sheet →
>   stop sheet → Settings → reminder check. At each step: Google's Accessibility Test Framework
>   (`Atf`, today's R5) with errors **blocking** on these screens; every clickable node has a
>   label; no two neighbouring nodes read the same; each leg chip's description equals
>   `Speech`'s text; traversal order follows the layout direction.
> - Paparazzi: every panel in Hebrew, dark, **font scale 2.0** (one more variant in
>   `PanelsTest`); layout audit R3 and R4 become blocking for the main screen if they report 0
>   findings after this package (else list what's left in the PR).
>
> **3. Owner check:** a checklist in the PR for TalkBack on the Pixel in Hebrew: the search,
> one result, the trip sheet, Start trip, the reminder check.
>
> **Tests:** `SpeechTest.kt` (each leg kind, a walk, past midnight, Hebrew order, E3's labels),
> the journey, the font 2.0 goldens.
>
> **Reviewer checks (Sonnet designer):** font 2.0 Hebrew on the smallest profile; nothing
> visual changed beyond targets; the map skip still lets a sighted user tap routes.
>
> **Docs:** `i18n-rtl` (the semantics rules, `Speech`); `ui-testing` (the a11y journey, what is
> blocking, why no TalkBack in CI); a release fragment `docs/releases/next/e5-accessibility.md`;
> tick ROADMAP E5.

## E6: NFC Rav-Kav read spike (optional, go/no-go)
**Branch:** `claude/p11-nfc-spike`
**Skills:** `android-build`, `dead-ends`, `i18n-rtl`
**Needs:** Wave 5 merged and owner decision 14 = yes
**Owns:** `android/app/src/debug/.../labs/RavKavLab.kt` (new, debug builds only),
`core/src/main/.../card/` (new: APDU building and record parsing, pure)
**Shared-file hooks:** `AndroidManifest.xml`: `android.permission.NFC` and
`<uses-feature android:name="android.hardware.nfc" android:required="false"/>`;
`SettingsDialog`: one "Labs: read my Rav-Kav" row in debug builds only

> **0 network requests.** A spike: the goal is a yes or no, not a feature.
>
> **Clean room.** Do not open, read or copy Metrodroid or any other GPL source, or the
> closed-source apps. Work only from public standards documents (ISO/IEC 7816-4 commands,
> ISO 14443) and public descriptions of the Calypso card structure, and from what the owner's
> card answers. Cite every document you use in the PR.
>
> **1. The prototype** (debug APK only, never in a release): `NfcAdapter` reader mode, `IsoDep`;
> `SELECT` the application and read with plain `READ RECORD` the public files (environment,
> contracts list, contracts, event log, counters). **Skip the holder file** (name, ID number):
> never read it. The screen shows, per file: answered or not (status word), the record lengths,
> and any field decoded with confidence (dates, contract codes); raw bytes only on screen.
> Nothing is stored, logged, put in a crash log or shared, and the screen has no copy button.
> A card that answers anything unexpected shows "Can't read this card", never a crash.
> Tested in core on byte arrays from the standard's examples (not from a real card).
>
> **2. The owner's test** (the boss sends the steps): install the CI `app-debug` APK, scan the
> card twice, take screenshots of the Labs screen and of Rav-Kav Online's view of the same card.
>
> **3. Go / no-go (owner decides from the screenshots):**
> - **Go** if the pass's contract and its end date are readable without keys or a secure
>   session, match Rav-Kav Online on both scans, and the read takes < 2 s. Then a follow-up
>   package (brief written by the boss, $8–12): "Read from card" fills E1's pass end date;
>   nothing else.
> - **No-go** otherwise: a `dead-ends` row with what answered and what needed keys, and the Labs
>   code is removed in the same PR.
>
> **Docs:** the PR (findings, documents cited); `dead-ends` on no-go; no release fragment for
> the spike; tick ROADMAP E6.

---

## Owner decisions this plan needs (ask before Wave 1)

Proposals in bold. The boss records the answers here, under a "Decided" line. Only 11 blocks
Wave 1 (T1) and 3–4 block E1; the rest can be answered before their wave.
1. **Payment apps (E4):** list **Rav-Kav Online, Rav-Pass by HopOn, Moovit, ANY WAY**, in that
   order, with the user picking "my payment app" (default None). Package names: E4 confirms
   each from its Google Play page and writes the date checked (Moovit's is believed to be
   `com.tranzmate`; the others are unconfirmed); **the owner checks the four on the Pixel before
   E4 merges**. HaTachana, Pango and Cello only if the owner wants them.
2. **Low-data mode (E4):** **stops layer from zoom 16 and no neighbour tiles; no night stop
   boards; offline map download asks unless on Wi-Fi; planning, search, reminders and rain
   unchanged; off by default.**
3. **Reminder-check wording (E1):** **generic text plus the maker's name** for Samsung, Xiaomi,
   Huawei/Honor, OnePlus/Oppo/Realme, without promising a menu path; ask one friend with a
   Samsung or Xiaomi to run the check after v0.9.1 and fix the words then.
4. **Pass-expiry timing (E1):** **3 days before and on the last day, at 09:00 Israel time**, also
   on Shabbat (a silent default-importance notification).
5. **"I missed it" (E2):** **the old trip's ride alert and countdown stop** when the user taps
   it; the start is the phone's fix (≤ 2 min old), never a typed guess.
6. **Train label wording (E3):** **"Train 24 to Nahariya · 3 stops" and a "Fewer stops" chip**
   (≥ 2 fewer stops, same stations, within 30 min); never "express" or "direct", since the
   operator's label is not in the data.
7. **Accessible-bus badge (E3):** **only with the Accessible-routes setting on; buses only; no
   badge at all if the ≤ 10-plan sample fails the go rule.**
8. **TalkBack (E5):** CI can't run TalkBack (no TalkBack on the free emulator images); **CI
   checks the semantics, and the owner runs the PR's TalkBack checklist on the Pixel in Hebrew.**
9. **Weekly contract check (T2):** **Mondays 04:47 Israel time, ≤ 5 Transitous + 1 Open-Meteo +
   1 Photon requests a week, opens an issue on drift; the owner mentions it in the existing
   Transitous Matrix thread** (`docs/transitous-contact.md`), since it is routine use.
10. **Workflows that write issues (T2, T3):** **allow `issues: write` for the contract check and
    the flake log only**, on two issues ("contract-drift", "CI flake log").
11. **Coverage gate (T1):** **core line coverage ≥ today's value rounded down; it only ratchets
    up**; generated data excluded.
12. **Upgrade test (T4):** **a throwaway key in PR runs (old tag and HEAD built in the job), and
    in `release.yml` a real install of the newly signed APK over the previous published one**
    (+~8 min per release).
13. **Test Lab (T3):** is the Spark project from `ui-testing` set up (repo variables
    `GCP_PROJECT`, `WIF_PROVIDER`, `SA_EMAIL`)? **If yes, T3 adds its no-root steps to the
    weekly real-phone run; if not, nothing.**
14. **NFC spike (E6):** OK to read **your own card on your own phone, nothing stored or sent**,
    and will you lend the card for two scans? **Proposal: yes, after Wave 5; go/no-go as in the
    brief.** No means E6 is dropped.
15. **Releases:** **v0.9.1 after Wave 3** (E1, E2, E3), **v0.10.0 at the end** (E4, E5, and E6
    if it goes).
16. **Mutation testing (T2):** **a weekly report, no gate.** Revisit after a month of scores.

---

## Close-out (boss)
- After Wave 3: join the E1–E3 fragments into `docs/releases/v0.9.1.md` (if the owner wants the
  release), add its user-data sample (T1's tool), delete the fragments, release with
  `release-apk`.
- Once all packages are merged, join the remaining `docs/releases/next/*.md` into
  `docs/releases/v0.10.0.md` (English block, then Hebrew block, then the usual update and
  attribution lines). Then delete the fragments.
- Read the "CI flake log" and "contract-drift" issues: any test that flaked twice in 14 days
  gets a fix package before the release.
- Check every Phase 11 ROADMAP line and every skill row (`python3 tools/check_docs.py`).
- Ask the owner to check each feature on the Pixel (the reminder check with a test reminder,
  missed it on a real ride, pass expiry with a date 3 days out, the TalkBack checklist), then
  release with the `release-apk` skill.
