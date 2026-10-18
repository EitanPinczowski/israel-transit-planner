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
rule in `CLAUDE.md` is not used by any package. The weekly live checks (T5) call only the three
services the app already uses. Every emulator mock server (T4, T6) is a local `MockWebServer`
inside the test process: nothing reaches the internet.

**Testing track (owner, 2026-10-13: "plan more rigorous tests of all aspects", then "more
testing").** Eight test packages (T1–T8) harden the whole app. They are sequenced so a test lands
before or with the feature it guards: the gates and core foundation first, ViewModel tests with
E1, system and chaos journeys with E2, routing with E3, the network-fault matrix with E4.

| package | items | wave | requests | review | cost |
|---|---|---|---|---|---|
| T1 Core test foundation | Kover gate on core; property tests; parser fuzz; request-budget inventory; saved-data samples | 1 | 0 | none (boss checks CI) | $8–12 (Sonnet) |
| T2 Static gates + bug intake | Android Lint gate (baseline); ktlint (baseline); APK-size gate; OSV-scanner + licence check; GitHub bug-report issue form | 1 | 0 | none | $8–12 (Sonnet) |
| E1 Reminder check + pass expiry | 1 "will my reminder ring?" + test alarm; 7 Rav-Kav pass-expiry reminder | 2 | 0 | Sonnet logic + tester | $12–18 |
| E7 Pick a day (owner, 2026-10-18) | choose the day of a search (Today, Tomorrow, a date up to the timetable horizon and beyond with the existing notice) in every tab; the day shown on the time chip | 2 | 0 | Sonnet designer + tester | $8–12 |
| T3 ViewModel + state tests | every UI state transition, cancel / retry / error per tab, on the JVM with a fake `TransitApi`; Kover on the Android JVM tests, ratcheted | 2 | 0 | none | $12–18 (Sonnet) |
| E2 I missed it | 2 "Missed it? Plan from here" in the ride, countdown and trip-changed notifications and the trip sheet | 3 | 1 `plan` per tap | Sonnet logic + tester | $12–16 |
| T7 System + chaos journeys | Doze, standby, reboot, TZ/DST, airplane, process death in every tab; rotation / theme / language mid-flow, permission revoked mid-ride, low storage, clock skew, split screen, pseudolocales; long monkey; flake log | 3 | 0 | none | $15–20 (Sonnet; Opus if stuck) |
| E3 Leg labels | 5 train number + "fewer stops"; 8 accessible-bus badge (after a ≤ 10-plan sample) | 4 | 0 (≤ 10 recordings) | Sonnet logic + designer + tester | $12–18 |
| T5 Routing correctness | ~25 golden trips (every special feature, north, south, Jerusalem, night, Friday/Shabbat, holiday) + invariants, checked weekly live; contract check; GPS-replay `RideTracker` tests; mutation testing (weekly report, then a ratcheting PR gate) | 4 | weekly ≤ 60 goldens + ≤ 7 contract | none | $12–16 (Sonnet) |
| E4 Pay + data card | 6 "Pay with…"; 4 data & battery card + low-data switch | 5 | 0 | Sonnet designer + tester | $12–18 |
| T4 Network-fault matrix | emulator + local mock server: 429, 503, timeout, garbage, empty, offline on every screen; request counts asserted end to end against each budget | 5 | 0 | none | $15–20 (Opus: app seam) |
| T6 Security + in-app update | intent fuzzing of exported components; FileProvider checks; update download vs a fake release server (wrong host, bad digest, redirect, cut); end-to-end update banner → install | 6 | 0 | none | $12–18 (Opus) |
| T8 Release, upgrade + performance | release-APK smoke; upgrade test (old release → new); startup-time gate; LeakCanary in the uitest build; map jank report; 30-min simulated ride battery; journey inventory + 5 gap journeys; bug-bash checklist | 6 | 0 | none | $15–20 (Sonnet) |
| E5 Accessibility | 3 semantics on every screen; spoken leg text; accessibility checks in **every** journey; font 2.0 goldens | 7 | 0 | Sonnet designer + tester | $15–20 |
| E6 NFC spike (optional) | 9 read your own Rav-Kav: a debug-only prototype, then go/no-go | 8 | 0 (no network) | none (owner tests on a real card) | $6–10 (+ $8–12 if go) |

Total about **$165–235** of implementer work (features $69–100, tests $107–136), plus about
**$13** of Sonnet passes (7 logic/designer reviews and 6 tester passes at ~$1 each): **about
$180–250** in all (another $8–12 if the NFC spike is a go). If that is too much, the cheapest
cuts are T8's battery and jank reports and T7's nightly chaos (about −$8), never the gates.

## Order

**One Opus implementer at a time while the 7-day limit reads `allowed_warning`** (until about
Sat 18 Oct, 18:00 Israel time), plus at most one Sonnet session (a review, a tester pass or a
Sonnet test package). After that, two per wave as usual. The second package of a wave starts
when the first one's PR is green.

- **Wave 1:** T1 and T2 (both Sonnet, no app behaviour). Every later PR runs under their gates.
- **Wave 2:** E1 and E7 (owner, 2026-10-18; both Opus, the 7-day limit has reset), then T3 once one of them is green. T3's ViewModel tests cover the hook E1 adds and every tab before E2
  touches the ViewModel.
- **Wave 3:** E2 and T7. T7's Doze, reboot and standby steps drive E1's test reminder; its
  "permission revoked mid-ride" step covers the ride path E2 changes.
- **Wave 4:** E3 and T5. T5's contract check then watches E3's two new fields, and its golden
  trips include the train cases E3 labels. **Release v0.9.1 after Wave 4** (proposal, owner
  decision 15): reminder check, pass expiry, missed it and train labels.
- **Wave 5:** E4 and T4. T4's mock server counts every request, so E4's usage card is checked
  against the server's own count.
- **Wave 6:** T6 and T8. T6 reuses T4's mock server; T8's upgrade and release checks run before
  v0.10.0. They edit different CI files (shared-file table).
- **Wave 7:** E5 alone (every screen, every journey).
- **Wave 8:** E6 alone, optional, only after the owner's yes (owner decision 14). Then **v0.10.0**.

## Shared files

These would conflict if two packages edited them at once, so each has one editor per wave.
"Hook" means at most one call or one field, placed where the brief says.

| shared file | w1 | w2 | w3 | w4 | w5 | w6 | w7 | w8 |
|---|---|---|---|---|---|---|---|---|
| `core/build.gradle.kts`, `gradle/libs.versions.toml` | T1 (Kover, kotest-property), T2 (ktlint) | | | T5 (PIT, shared invariants dir) | | | | |
| `android/app/build.gradle.kts` | T2 (Lint baseline, ktlint) | T3 (Kover, test deps) | | | T4 (mock-server test deps; base-URL seam in `uitest`) | T8 (LeakCanary for `uitest`) | | |
| `.github/workflows/ci.yml` | T1 (`core`: coverage), T2 (`android`: Lint, APK size; `core`: ktlint) | T3 (`screenshots`: Android coverage) | | T5 (incremental mutation, path-filtered) | | T8 (throwaway signing for `assembleRelease`) | | |
| `.github/workflows/ui-tests.yml` | | | T7 (`system` job) | | T4 (`faults` job) | T8 (`release-smoke` job) | E5 (checks in every journey) | |
| `.github/workflows/nightly-ui.yml` (new) | | | T7 (creates it) | | | T8 (perf jobs) | | |
| `tools/ui_behaviour_run.sh`, `tools/ui_instrument.sh` | | | T7 (flake log) | | T4 (`faults` script beside it) | T6 (one line in the faults script), T8 (LeakCanary rule) | E5 | |
| `tools/check_docs.py` | T1 (budget rule) | | | | | T8 (journey-inventory rule) | | |
| `api/Models.kt` | | | | E3 (two fields, appended) | | | | |
| `api/Guards.kt`, `GuardedWeather`, `GuardedGeocoder` | | | | | E4 (one counter call each) | | | |
| `user/User.kt` (`UserSettings`, `UserJson`) | | E1 (`passEndsOn`, `reminderCheckHintShown`) | | | E4 (`paymentApp`, `lowData`) | | | |
| `remind/*` (android), `remind/Reminder.kt` (core) | | E1 | E2 (action buttons only) | | | | | |
| `ride/RideService.kt` | | | E2 (one action) | | E4 (one GPS-minutes call) | | | |
| `ui/screens/SettingsDialog.kt` | | E1 (two rows) | | | E4 (two sections) | | E5 (semantics) | E6 (one debug-only row) |
| `ui/screens/TripDetailsSheet.kt` | | | E2 (one button) | E3 (labels) | E4 (one button) | | E5 | |
| `ui/screens/TripPanel.kt`, `ui/screens/RideDetails.kt`, `present/RideDetails.kt` | | E7 (`TimeRow` only) | | E3 | | | E5 | |
| other `ui/screens/*`, `ui/MainScreen.kt`, `ui/MapController.kt` | | | | | E4 (stops-layer zoom, one line) | | E5 | |
| `AndroidManifest.xml` | | E1 (nothing expected) | | | E4 (`<queries>` packages) | | | E6 (NFC, not required) |
| `MainActivity.kt` | | | E2 (the missed-it extra) | | | | | |
| `ui/MainViewModel.kt`, `ui/MainActions.kt` | | E1: one hook; E7: none expected (`setTime` already takes an `Instant`) | E2: one entry point | E3: none expected | E4: one hook | | E5: none expected | |
| `android/.../uitest/JourneysTest.kt`, `AppDriver.kt` | | E1 (one journey) | E2 (one journey) | | | | E5 (checks after every step) | |
| `UiTestApp.kt`, `ProcessDeathProbe.kt` | | | T7 (tabs, chaos probes) | | T4 (mock-server mode) | T8 (`UpgradeProbe`) | | |
| `.claude/skills/transitous-api` | T1 (budget note) | | E2 (budget row) | E3, T5 | E4, T4 | | | |
| `.claude/skills/ui-testing` | | | T7 | | T4 | T6, T8 (separate sections) | E5 | |
| `.claude/skills/android-build` | T1, T2 | T3 | | T5 | | T8 | | |
| `ROADMAP.md` | each package ticks only its own `###` lines; E3 also corrects the "vehicle accessibility is not in the data" line in Phase 6 | | | | | | | |

The owner merges every PR; agents never merge.

## Request budgets

Every new call goes through `TransitApi`. Each cap is a constant pinned by a test, and each
row goes into the `transitous-api` budget table and T1's `BudgetInventoryTest` in the package
that adds it. From Wave 5, T4's emulator matrix also asserts every feature's count end to end.

| package | call | when | cap | pinned by |
|---|---|---|---|---|
| E1 | none: the test reminder never re-checks the trip | Settings tap | 0 | `ReminderHealthTest`: the test alarm sends 0 |
| E2 | `plan` from the current fix to the same destination, leaving now | a tap on "Missed it" | 1 per tap (a second tap within 60 s hits the guard cache: 0) | `MissedItTest` + a `BudgetInventoryTest` row |
| E3 | `plan` recordings for the `wheelchairAccessible` sample | recording only | ≤ 10, once | the sample fixtures |
| T5 | weekly live goldens: ~25 trips through the app's own planners | Tuesdays 03:47 Israel, scheduled CI | ≤ 60 a week, ≥ 1 s apart | the runner counts and stops at the cap |
| T5 | weekly contract: `plan`, `trip`, `stoptimes`, `geocode`, `reverse-geocode` on Transitous; 1 Open-Meteo; 1 Photon | same night | ≤ 5 + 1 + 1 | `tools/contract_check.py` stops at the cap |
| E7 | none new: a chosen day changes the existing `plan`'s time only | search | 0 extra | `DayPickTest`: picking a day sends nothing; the search sends its usual count |
| E4 | none; low-data mode only removes requests | | 0 | `LowDataTest` |
| T1–T4, T6–T8, E5, E6 | none to the internet (T4, T6: a local mock server only) | | 0 | |

**Transitous traffic from CI: ≤ 65 requests a week** (60 goldens + 5 contract), about what one
user's busy day of planning costs. It is routine use, so the owner mentions it in the existing
Transitous Matrix thread (owner decision 9).

---

## CI time (GitHub's free runners)

The repo is public, so standard-runner minutes cost $0. What matters is how long a PR waits and
the free plan's **20 jobs running at once** across the repo. Measured today (run 38428022729,
`main`): `UI tests` takes ~20 min wall clock (the three screen shards, 14–17 min, are the long
pole) and ~70 runner-minutes; `CI` (core, android, screenshots) ~15 runner-minutes.

**PR time** (every push that touches the app; slow suites go to nightly or weekly):

| added by | where | runner-min per PR | wall clock |
|---|---|---|---|
| T1 Kover + property + fuzz; T2 ktlint | `core` job | +3 | +3 (the core job gates the rest) |
| T2 Android Lint + APK size | `android` job | +4 | 0 (parallel to screenshots) |
| T3 ViewModel tests + Android Kover; E5 font 2.0 shots | `screenshots` job | +4 | +1 |
| T5 incremental mutation (changed core classes only) | own job, only when `core/src/main` changes | +5 | 0 |
| T7 `system` job (API 34): Doze, reboot, airplane, rotation / theme / language mid-flow, permission revoked mid-ride, process death in every tab | UI tests | +15 | 0 (parallel, ≤ a shard) |
| T4 `faults` job (API 35): the fault matrix; T6's intent fuzz, FileProvider and update tests ride in it | UI tests | +16 | 0 |
| T8 `release-smoke` job (API 35): release APK, startup-time gate | UI tests | +9 | 0 |
| T8 gap journeys (API 34 + 35); LeakCanary in every journey | journey jobs | +8 | 0 |
| E5 accessibility checks after every journey step | journey jobs | +4 | 0 |
| T8 upgrade test | own workflow, only when saved data, the manifest or the version change | +15 when it runs | 0 |
| T2 OSV-scanner + licence check | own workflow, only when Gradle files change | +3 when it runs | 0 |

**Per app PR: about +68 runner-minutes (from ~85 to ~150) and about +4 min wall clock**
(~24 min in all). **A core-only PR: about +8; a docs PR: +3.** Jobs per app PR: CI 3 + UI tests
14 (build, 3 shards, 6 journey levels, `system`, `faults`, `release-smoke`, report) = **17**, plus
the mutation, upgrade or dependency job when their paths change: **at most 20**. Two app PRs
pushed at the same minute will queue behind each other; the `concurrency` groups already cancel
a branch's older run.

**Nightly** (`nightly-ui.yml`, 01:47 Israel, only if `main` changed since the last nightly):
chaos steps that need root or are slow (low storage, clock skew, TZ/DST, split screen, app
standby buckets, pseudolocales en-XA / ar-XB on 2 profiles) ~25 min; long monkey (3 seeds ×
20 000) ~20 min; map jank report ~10 min; 30-min simulated ride with battery stats ~40 min:
**~95 runner-minutes a night**, at most 4 jobs at once.

**Weekly:** full mutation run ~45 min; live goldens + contract ~15 min; upgrade test ~15 min;
OSV + licence ~3 min; Test Lab (if set up) on Google's quota: **~80 runner-minutes a week.**
In all about **550 runner-minutes a week** of scheduled work, free on a public repo.

## Flake policy (all packages)

Quarantine-free, as today: nothing is skipped, disabled or quarantined. `tools/ui_instrument.sh`
keeps its one retry per test for a process killed with an empty crash buffer. T7 adds a record:
every retry, and every job re-run, is posted as one comment on a single open issue "CI flake
log" (the workflow's `GITHUB_TOKEN`, `issues: write`), with the run link, API level, test and
kind. Nightly and weekly failures comment on their own issues ("nightly", "golden-trips",
"contract-drift"). The boss reads them at each close-out; **a test that flakes twice in 14 days
gets a fix package**, never a skip. Gates with a threshold (startup time, APK size, coverage,
mutation) only ratchet: a PR that must cross one says why in its body and updates the stored
value in the same PR, for the owner to accept.

---

## Reviews

- **No Gemini** (owner, 2026-10-12). Reviews are a **targeted Sonnet pass** only where the
  table says:
  - *designer* (packages that change screens): from the CI `screenshots` artifact, light and
    dark, Hebrew and English, small and large phones; loads `i18n-rtl`;
  - *logic* (alarms, dates, routing, data): the brief's "Reviewer checks" list and the
    `parallel-work` definition of done, nothing wider;
  - *tester* (every E package, the `team.md` tester role, ~$1): reads the brief's "Tests" list
    and the PR's tests, and reports each missing case, weak assertion or untested branch with how
    to reproduce it. It writes no code. It runs once the PR is green, in the same round as the
    other pass.
  The boss starts them once the PR is green. The implementer fixes what they find on its own
  branch, in one round. Then the owner merges.
- Test packages (T1–T8) get no review: the boss checks that the new jobs ran on the PR head, what
  they reported, and that nothing was skipped.

**Models.** E1–E6, T4 and T6 run on **Opus** (T4 adds an app seam; T6 judges security). T1, T2,
T3, T5, T7 and T8 run on **Sonnet**; if an emulator step fails for a reason a Sonnet package
cannot find in two tries, it stops and tells the boss, who may re-start it on Opus. Reviews and
tester passes run on Sonnet. Every session is created with the tag
`remote-agents-project:israel-transit`.

---

## Common preamble (sent first to every session)

> You are one of several agents working on Israel Transit Planner, each on its own branch and
> PR. You run on Opus (T1, T2, T3, T5, T7, T8: Sonnet).
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
> - **Review:** if your package has a Sonnet pass or a tester pass, the boss starts it once your PR is green;
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

## T2: Static gates + bug intake
**Branch:** `claude/p11-static-gates`
**Skills:** `android-build`, `release-apk`
**Needs:** nothing (starts from `main`; runs after or beside T1)
**Owns:** `android/app/lint.xml` + `android/app/lint-baseline.xml` (new),
`.editorconfig` + ktlint baselines (new), `tools/apk_size.py` + `apk-size.json` (new),
`.github/workflows/deps.yml` (new), `tools/licence_allowlist.txt` (new),
`.github/ISSUE_TEMPLATE/bug.yml` + `config.yml` (new); the ktlint/Lint lines of both
`build.gradle.kts` files and of `ci.yml` (`core` and `android` jobs)
**Shared-file hooks:** none in app code (formatting is never mass-reformatted: the baseline
freezes today's code)

> Runs on **Sonnet**. **0 requests.** Each gate starts from a baseline of today's state, so this
> PR changes no app code; from then on new problems fail.
>
> **1. Android Lint as a gate.** `:app:lintDebug` in the `android` job with `lint-baseline.xml`
> of today's issues. Errors fail; and these warning ids fail when new (owner decision 18):
> `NewApi`, `InlinedApi`, `MissingPermission`, `UnspecifiedImmutableFlag`,
> `ExportedReceiver`, `ExportedService`, `UnsafeIntentLaunch`, `HardcodedText`, `RtlHardcoded`,
> `RtlEnabled`, `MissingTranslation`, `UnusedResources` (report only). The report is an
> artifact and its counts go in the job summary.
>
> **2. ktlint** (owner decision 17: ktlint, not detekt) on `core` and `android`, standard rules
> with `.editorconfig` tuned to the code's current style (Compose function names allowed), and
> a baseline of today's findings. `core` job: `ktlintCheck`. A PR's new or changed lines must
> pass; nobody reformats untouched files.
>
> **3. APK-size gate.** `tools/apk_size.py` reads the release APK from the `android` job and
> `apk-size.json` (bytes, the commit it was measured on). It fails at **> 2 % or > 1 MB** over
> the stored size (owner decision 19), and prints the biggest changed entries (`unzip -lv`
> diff by folder: `lib/`, `res/`, `classes*.dex`, `assets/`). A PR that grows on purpose updates
> `apk-size.json` with a reason in its body.
>
> **4. Dependencies: OSV-scanner + licences.** `deps.yml`: weekly, `workflow_dispatch`, and PRs
> touching `gradle/**` or `*.gradle.kts`. OSV-scanner (the free Google tool, no account) over the
> Gradle lockfile or the resolved dependency list; a HIGH or CRITICAL with a fixed version fails,
> the rest are listed. Licences of every runtime dependency of the release APK checked against
> `tools/licence_allowlist.txt` (Apache-2.0, MIT, BSD-2/3, ISC, EPL-1.0/2.0 for test-only
> libraries; anything GPL/AGPL/unknown in the APK fails; owner decision 27).
>
> **5. Bug intake from friends.** `.github/ISSUE_TEMPLATE/bug.yml`: app version, Android
> version and phone, what you did, what you expected, what happened, a screenshot, "share crash
> log" text (optional); a checkbox "no home address or personal places in this report". The
> in-app "Share crash log" stays the route for friends with no GitHub account (owner decision 28).
> `config.yml` disables blank issues.
>
> **Tests:** each gate shown failing once in a throwaway commit on a scratch branch (a Lint
> `NewApi`, a ktlint violation, a 2 MB asset, a known-vulnerable test dependency) and the run
> links in the PR body; the PR itself green.
>
> **Docs:** `android-build` (each gate: what fails, how to read it, how to update a baseline
> or `apk-size.json` honestly); `release-apk` (the issue form); no release fragment; tick
> ROADMAP T2.

## E1: Reminder check + pass expiry
**Branch:** `claude/p11-reminder-check`
**Skills:** `reminders-offline`, `i18n-rtl`, `add-feature`, `android-build`
**Needs:** Wave 1 merged
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
>   the most likely problem first. It also gives T7 its Doze and reboot seam.
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

## E7: Pick a day (owner, 2026-10-18)
**Branch:** `claude/p11-pick-day`
**Skills:** `i18n-rtl`, `ui-testing`, `add-feature`
**Needs:** nothing (starts from `main`; runs beside E1)
**Owns:** `TimeRow` / `timeLabel` in `ui/screens/TripPanel.kt` and the time rows of the car panels
(`ui/screens/Common.kt` and the four car panels, time row only), `present/DayPick.kt` (core, new),
`strings_day.xml` (new), one journey in `JourneysTest.kt`
**Shared-file hooks:** none in `MainViewModel` expected (`setTime(mode, Instant)` already takes any
instant; `SavedUi` already saves it)

> **0 new requests.** Owner, 2026-10-18: "when searching you need to be able to search a ride for
> next day (or more)". Today the time chips open a clock only; a time more than an hour in the past
> silently becomes tomorrow, and the chip shows "08:15" with no day.
> - **Choose the day** in every tab that has a time row (Trip, Better start, Drop-off, Pick-up,
>   Park & ride): after Depart at / Arrive by, a day step: chips **Today**, **Tomorrow**, and the next
>   5 weekdays by name ("Sun 20"), plus **Other date…** (Material date picker, from today to 60 days
>   ahead). Then the clock. Dates past `DayNotice.HORIZON_DAYS` are allowed; the existing D3 notice
>   ("The timetable may not reach …" + the nearest day with service) handles them.
> - **The chip shows the day** whenever it is not today: "Depart tomorrow 08:15", "Arrive by Thu 23
>   Oct 17:30" (Hebrew: "יציאה מחר 08:15", "הגעה עד יום ה׳ 23 באוק׳ 17:30"). Remove the silent
>   "an hour in the past means tomorrow" rule: a past time today asks "Tomorrow at 06:10?" with Yes /
>   Change, never guesses.
> - **Day logic in core** (`present/DayPick.kt`, pure, tested): the chip label, the list of days,
>   the 04:00 service-day edge (a 01:30 departure picked "Tomorrow" late at night is the calendar
>   date the user means, labelled clearly), both DST changes, Friday/Shabbat/holiday days marked with
>   the D3 holiday names in the day list.
> - Reminders, "save trip", Earlier/Later, the rain note (only within 48 h) and the process-death
>   restore keep working with a future day.
>
> **Tests:** `DayPickTest` (core: labels in he/en, the 04:00 edge, DST, holiday marks, 60-day cap,
> 0 requests); a property test in T1's style (any picked day + time round-trips through `SavedUi`);
> Paparazzi states for the day step and the chips (he/en, light/dark, small phone, font ×1.3); one
> journey: pick Tomorrow 08:00 → search → the result times are tomorrow → Earlier/Later stay on that day.
>
> **Reviewer checks (Sonnet designer + tester):** RTL order of the day chips; the small phone with
> the longest Hebrew label; the date picker in dark mode; the journey asserts the searched date, not
> just that results appeared.
>
> **Docs:** `i18n-rtl` (day labels and formats); `ui-testing` (the journey); a release fragment
> `docs/releases/next/e7-pick-day.md`; tick ROADMAP E7.

## T3: ViewModel + state tests
**Branch:** `claude/p11-viewmodel-tests`
**Skills:** `android-build`, `special-features`, `reminders-offline`
**Needs:** Wave 1 merged
**Owns:** new files in `android/app/src/test/.../ui/vm/` (`*StateTest.kt`, `FakeTransitApi`
helpers built on core's `Fakes`/`ReplayTransitApi`), Kover for the app's JVM tests in
`android/app/build.gradle.kts`, one step in the `screenshots` job of `ci.yml`; small fixes
(≤ ~20 lines) in `MainViewModel` / `MainActions` / `*ViewModel.kt` when a test finds a real bug
**Shared-file hooks:** none

> Runs on **Sonnet**. **0 requests.** JVM only (the `screenshots` job already runs the app's unit
> tests); `kotlinx-coroutines-test` with a `TestDispatcher` and a fixed `TransitApp.clock`, a fake
> `TransitApi` that can answer, wait, fail with each fault of `transitous-api`'s fault table, or
> be cancelled.
>
> **1. Every UI state transition, per tab** (Trip, Better start, Drop-off, Pick-up, Park & ride,
> errands): idle → searching → results / no options / error → retry → results; edit From or To
> while searching (the old search is cancelled and its answer never shows); Earlier / Later; sort
> change keeps the selected option; select → trip sheet → back; switching tabs mid-search; the
> offline copy (`PlanCache`) when the fake fails; the day notice; the rain note; the big-stop
> offer. Assert the `UiState` sequence, not just the end.
> **2. Cancel, retry and error paths:** 429 then 200 (one retry, one answer); 503 twice (the
> error text, Retry works); a timeout; malformed JSON; cancelled by a new query; the budgeted
> features stop at their cap and say so.
> **3. Other state holders:** `CalendarViewModel`, `TripDetailsViewModel`, `SavedUi` restore
> (with T1's property test), the update banner (`UpdateController`), settings changes that
> re-plan (buffer, accessible routes, fare profile) and those that don't.
> **4. Coverage on the Android JVM tests:** Kover for the app's `testDebugUnitTest`, line
> coverage of `ui/` (Compose screens excluded: Paparazzi covers them) in the job summary, gated
> at today's value rounded down, ratcheting (owner decision 21).
>
> **Docs:** `android-build` (the VM test pattern, the fake API, the Android coverage gate);
> `add-feature` (a new feature adds its state test); no release fragment; tick ROADMAP T3.

## E2: I missed it
**Branch:** `claude/p11-missed-it`
**Skills:** `reminders-offline`, `transitous-api`, `i18n-rtl`, `add-feature`
**Needs:** Wave 2 merged
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

## T7: System + chaos journeys
**Branch:** `claude/p11-system-chaos`
**Skills:** `ui-testing`, `reminders-offline`, `i18n-rtl`, `android-build`
**Needs:** Wave 2 merged (E1's test reminder); E2 may be in review (rebase-free: merge `main`
when E2 merges and add its notification step then)
**Owns:** `.github/workflows/ui-tests.yml` (the `system` job), `.github/workflows/nightly-ui.yml`
(new), `tools/ui_system_run.sh` + `tools/ui_chaos_run.sh` (new), `tools/ui_instrument.sh` (the
flake log), `ProcessDeathProbe.kt` (every tab), `android/.../uitest/SystemJourneysTest.kt` and
`ChaosJourneysTest.kt` (new), `pseudoLocalesEnabled` in the `uitest` build type; `devices.yml`
only if owner decision 13 says Test Lab is set up
**Shared-file hooks:** none in app code (a real bug found: report it with the repro, don't fix
app code)

> Runs on **Sonnet**; two failed tries at an emulator step you can't explain → stop and tell
> the boss. **0 requests.** Everything runs on the `uitest` build (replayed fixtures). Each step
> prints what it saw and fails only on a broken promise; anything else is a finding in the
> report.
>
> **1. PR time: a `system` job** on API 34 (`google_apis`, `adb root`), ≤ 17 min, parallel to the
> shards:
> - **Doze:** arm E1's test reminder, `dumpsys deviceidle force-idle`: posted within 2 min of its
>   time.
> - **Reboot re-arm:** a trip reminder and a pass-expiry date armed, `adb reboot`: `dumpsys alarm`
>   lists both again; no ride service came back by itself.
> - **Airplane mode:** `cmd connectivity airplane-mode enable`: offline banner, saved results, no
>   crash; off again: Retry works.
> - **Mid-flow switches:** rotation, dark ↔ light (`cmd uimode night`), and the in-app language
>   switch, each while a search is loading, with the trip sheet open, and during a leave
>   countdown: the same results, the same selected option, no second request
>   (`app.replay.calls`), no crash.
> - **Permission revoked mid-ride:** start a ride on the mock location, `pm revoke` fine
>   location: Android kills the process; on return no crash, the ride notification is gone or
>   says the alert stopped, and E2's "Missed it" asks for the start.
> - **Process death in every tab:** extend `ProcessDeathProbe` to Better start, Drop-off,
>   Pick-up, Park & ride and errands (selected option, the car path, the open sheet). The Trip
>   check stays on every API level.
> **2. Nightly** (`nightly-ui.yml`, 01:47 Israel, only if `main` changed; failures comment on the
> "nightly" issue):
> - **App standby:** `am set-standby-bucket` `rare` (the test reminder rings: blocking) and
>   `restricted` (whether it rings is recorded; E1's check must report the restriction).
> - **Time zone + DST:** switch to `Europe/London` and back with a reminder armed (absolute time
>   unchanged, nothing armed twice; pin what screens show abroad from `present/Format.kt`); set
>   the clock to Sat 24 Oct 2026 23:58 Israel and arm across the 25 Oct change: rings once.
> - **Clock skew:** device clock +3 h and −1 day against the app's fixed-clock fixtures: no
>   negative countdown, no stale cached plan shown as current, no crash.
> - **Low storage:** fill `/data` to < 50 MB free (`fallocate`): saving a place, the plan cache
>   and the crash log fail politely (a message, no crash, nothing half-written: the old file
>   still decodes). Free it again.
> - **Split screen / multi-window:** the app in split screen and freeform on the tablet profile
>   (`am start --windowingMode`), resized while results show: layout audit R1/R7/R8 hold.
> - **Pseudolocales:** en-XA and ar-XB (`cmd locale set-app-locales`, API 33+) on profiles P1
>   and P5: the layout audit runs; R3 (clipped text) findings are listed and, for the main
>   screen, blocking once T7's PR has fixed or listed them all.
> - **Long monkey:** API 35, 3 seeds × 20 000 events, crash/ANR gate, seeds in the summary. The
>   PR run keeps today's one seed.
> **3. Flake log.** When `ui_instrument.sh` retries a test, or a job is a re-run
> (`github.run_attempt > 1`), a final step comments on the open issue "CI flake log" (creates it
> once) with the run link, API, test and kind (`issues: write` for that step only).
> **4. Test Lab (only if owner decision 13 says the Spark project exists):** the no-root steps
> join the weekly `devices.yml` run on one real phone.
>
> **Docs:** `ui-testing` (the `system` job, the nightly chaos list, what is blocking, the flake
> log and the "two in 14 days" rule, pseudolocales); no release fragment; tick ROADMAP T7.

## E3: Leg labels (train number + accessible bus)
**Branch:** `claude/p11-leg-labels`
**Skills:** `transitous-api`, `i18n-rtl`, `add-feature`, `golden-trips`
**Needs:** Wave 3 merged (T5, in the same wave, adds the contract check that then watches the new fields)
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

## T5: Routing correctness (goldens, contract, GPS replay, mutation)
**Branch:** `claude/p11-routing-tests`
**Skills:** `golden-trips`, `special-features`, `transitous-api`, `android-build`
**Needs:** Wave 3 merged
**Owns:** `core/build.gradle.kts` (PIT, the shared `src/invariants` source dir),
`core/src/invariants/` (new: `RoutingInvariantsTest`'s checks, moved so `test` and `golden`
share them), `core/src/golden/` (new trips, the weekly mode), `.github/workflows/mutation.yml`
and `.github/workflows/weekly-live.yml` (new), the path-filtered mutation job in `ci.yml`,
`tools/contract_check.py` + `core/src/test/resources/contract/` (new), `tools/gpx_synth.py` +
`core/src/test/resources/tracks/` (new), `ride/RideReplayTest.kt` (new),
`core/mutation-baseline.json` (new)
**Shared-file hooks:** `RoutingInvariantsTest.kt`: calls the moved checks

> Runs on **Sonnet**.
>
> **1. Golden trips widened to ~25** (`golden-trips` table + `core/src/golden/`): today's 12 plus
> about 13, so that each of these has at least one: every special feature twice (Better start,
> Drop-off, Pick-up, Park & ride), errands, last trip home; north (Kiryat Shmona or Nahariya,
> Tiberias, the Golan), south (Eilat → Be'er Sheva, Dimona or Mitzpe Ramon), Jerusalem (light
> rail Mount Herzl → Central Station; a bus to a neighbourhood), Nazareth → Haifa, a **night**
> trip (Tel Aviv night buses: busofash, the one real-time feed), **Friday afternoon** and
> **Saturday night after Shabbat**, and a **holiday or holiday eve** when one falls within
> `DayNotice.HORIZON_DAYS` of the run (else the runner says "no holiday in range" and skips only
> that trip). Dates are computed from the run date (next Tuesday, that week's Friday…).
> - The runner applies every shared invariant (times in order, legs chain in time and space,
>   Pareto fronts, budgets, fares ≥ 0, ride details add up, no unreasonable chains) plus each
>   trip's own expectation.
> - **Weekly live** (`weekly-live.yml`, Tuesdays 03:47 Israel, `workflow_dispatch`): ≤ **60
>   requests**, `MotisClient.USER_AGENT`, ≥ 1 s apart; the runner stops at the cap. A failure
>   comments on the "golden-trips" issue with `report.md`; it never blocks a PR (owner decision 22).
>   One live run in this PR (from the session if the host is allowed, else `workflow_dispatch`).
> **2. Weekly contract check** in the same workflow: `tools/contract_check.py` (stdlib only),
> ≤ 5 Transitous (`plan` Be'er Sheva → Tel Aviv next Tuesday 08:00; `trip` for its first transit
> leg; `stoptimes`; `geocode` "דיזנגוף 50 תל אביב"; `reverse-geocode`), 1 Open-Meteo, 1 Photon.
> It compares the shape (keys and JSON types) of the fields `Models.kt`, `OpenMeteo` and `Photon`
> read with shapes generated from the fixtures (`--update-shapes`, never by hand), reports a
> shrunken timetable horizon and any new `realTime: true`, and comments on the
> "contract-drift" issue on drift.
> **3. GPS replay of `RideTracker`.** `RideReplayTest` feeds a GPX track fix by fix to
> `RideTracker` against the leg it rides and asserts the "get off at the next stop" alert fires
> once, before the stop, never at a wrong stop. Tracks:
> - synthetic, now: `tools/gpx_synth.py` builds tracks from recorded `plan`/`trip` polylines with
>   seeded noise: urban-canyon jitter, a 60 s tunnel gap (the Carmel and Ayalon rail), stop-and-go
>   traffic, a GPS jump of 300 m, a skipped stop, a loop line passing the stop twice;
> - real, when the owner records them (owner decision 23): 3 rides (bus, train, light rail) with
>   a free GPX logger, trimmed 300 m from home and work, committed under `tracks/real/`.
> **4. Mutation testing.** `mutation.yml`: weekly full run (Sunday night, ~45 min) of plain PIT
> (Apache-2.0, JUnit 4) on the pure packages `present/`, `fare/`, `features/`, `plan/`
> (`TripPages`, sorting), `remind/`, `ride/`, `calendar/`, with Kotlin's generated null checks
> filtered. Its first run (in this PR, by `workflow_dispatch`) writes `mutation-baseline.json`
> (score per class). Then **the gate is on** (owner decision 20): a PR that changes
> `core/src/main` runs PIT on the changed classes only (incremental history cached), and fails
> when a changed class scores more than 2 points under its baseline; the weekly run raises the
> baseline, never lowers it. Today's scores go in `android-build`.
>
> **Docs:** `golden-trips` (~25 trips, the weekly run, its request count); `transitous-api`
> (weekly CI traffic: ≤ 60 + 5 Transitous, 1 Open-Meteo, 1 Photon; the contract check; how to
> update shapes); `reminders-offline` (GPS replay, how to add a real track); `android-build`
> (mutation gate); no release fragment; tick ROADMAP T5.

## E4: Pay with… + data and battery card
**Branch:** `claude/p11-pay-data`
**Skills:** `i18n-rtl`, `add-feature`, `transitous-api`, `android-build`
**Needs:** Wave 4 merged
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

## T4: Network-fault matrix on the emulator
**Branch:** `claude/p11-fault-matrix`
**Skills:** `ui-testing`, `transitous-api`, `android-build`
**Needs:** Wave 4 merged; E4 may run beside it (T4 owns no E4 file)
**Owns:** a mock-server mode for the `uitest` build (`UiTestApp`: base URLs of Transitous,
Open-Meteo and Photon taken from an instrumentation argument), `android/.../uitest/mock/`
(new: `MockServerRule` on OkHttp's `MockWebServer`, scripted dispatchers),
`android/.../uitest/FaultMatrixTest.kt` (new), `tools/ui_faults_run.sh` (new), the `faults` job
in `ui-tests.yml`, the mock-server test dependency in `android/app/build.gradle.kts`
**Shared-file hooks:** none in `main` app code beyond the `uitest` seam

> Runs on **Opus** (it adds the seam). **0 requests to the internet:** the server runs inside the
> instrumentation process on the emulator's localhost; the replay mode of every other journey is
> untouched.
>
> **1. The matrix.** For each screen that calls the network: Trip search, Earlier / Later,
> Better start, Drop-off, Pick-up, Park & ride, errands, stop sheet departures, day timetable,
> trip sheet (`trip`), map stops, typing search (Transitous geocode, then Photon through its own
> base URL), pin name, rain note (Open-Meteo), E2's "Missed it" — and for each fault: **429
> then 200**, **429 twice**, **503 twice**, **timeout** (the server never answers), **garbage**
> (HTML), **empty body**, **`{}`**, **cut mid-body**, **offline** (the server shut down and
> airplane mode on). Assert what the user sees: the right message, no endless spinner, Retry
> works, the offline copy where one exists, never a crash.
> **2. Request counts end to end.** The server counts every request per path. Each case asserts
> the count is ≤ the feature's budget constant (+1 per allowed retry), read from the same
> constants as T1's `BudgetInventoryTest`; a second identical action within the cache time sends
> 0. Once E4 is merged, its data card's numbers must equal the server's counts.
> **3. CI:** a `faults` job on API 35, ≤ 17 min, parallel to the shards. T6's tests join it.
>
> **Docs:** `ui-testing` (the mock-server mode, how to add a screen to the matrix);
> `transitous-api` (the fault table now says "pinned in core and on the emulator"); no release
> fragment; tick ROADMAP T4.

## T6: Security + in-app update tests
**Branch:** `claude/p11-security-update`
**Skills:** `ui-testing`, `release-apk`, `android-build`
**Needs:** T4 merged (its mock server)
**Owns:** `android/.../uitest/IntentFuzzTest.kt`, `FileProviderTest.kt`, `UpdateFlowTest.kt`
(new), `android/.../uitest/mock/FakeReleaseServer.kt` (new), `tools/ui_intent_fuzz.sh` (new),
a second test APK build (`uitest` with `versionCode + 1`) in the `faults` job; small fixes
(≤ ~30 lines) in `MainActivity` intent handling, `UpdateCheck`/`UpdateFlow`/`ApkInstaller` or
`res/xml` provider paths when a test finds a real hole (security fixes are in scope)
**Shared-file hooks:** `tools/ui_faults_run.sh`: one line that runs these classes

> Runs on **Opus**. **0 requests to the internet.**
>
> **1. Intent fuzzing** of every exported component (`MainActivity`'s `SEND` / `VIEW` filters,
> the quick-settings tile, `BootReceiver`), from `adb shell am` and from the test: null and
> missing extras, 1 MB of text, the wrong MIME type, `geo:NaN,NaN`, `geo:999,999`, `geo:` with
> SQL / HTML / RTL-override characters, `javascript:` and `file://` schemes, a `content://` URI
> of another app's private file with a grant flag, a map link on a look-alike host
> (`maps.google.com.evil.example`), a spoofed `BOOT_COMPLETED` from the shell. Expected: no
> crash, no request for refused input, no file read outside what was granted, the spoofed
> broadcast does nothing a real boot wouldn't (re-arming is harmless; nothing else).
> **2. FileProvider:** only the `updates` and `reports` cache paths are served; `getUriForFile`
> refuses anything else (`filesDir`, DataStore, the crash log); another app (the shell) can't
> read a URI without a grant; the report file is gone after a day.
> **3. Update download against a fake release server** (`FakeReleaseServer` answers the
> `releases/latest` JSON and serves an APK): an asset on a host not in the allowlist → refused;
> a digest that doesn't match → refused and the file deleted; a redirect to a host off the
> allowlist → refused; the download cut halfway → a clear error, no partial file kept, the next
> try starts clean; HTTP instead of HTTPS → refused; a version not newer → no banner.
> **4. End to end (item 9):** the `uitest` APK running as the "previous version" shows the banner
> for the fake release; Download → digest OK → the system installer opens with our provider URI;
> on API 35 UiAutomator taps Install; `dumpsys package` shows `versionCode + 1`; the app restarts
> with its data (owner decision 30). Gate on API 35; other levels stop at the installer screen.
>
> **A hole found:** fix it if it is within the ~30 lines above, with a test; otherwise write it
> in the PR body without details that would help an attacker in a public repo, and tell the boss.
>
> **Docs:** `ui-testing` (security tests); `release-apk` (what the update flow refuses, pinned by
> tests); no release fragment unless a fix changes behaviour; tick ROADMAP T6.

## T8: Release, upgrade, performance + journey inventory
**Branch:** `claude/p11-release-perf`
**Skills:** `ui-testing`, `release-apk`, `android-build`, `reminders-offline`
**Needs:** Wave 5 merged
**Owns:** `.github/workflows/ui-tests.yml` (the `release-smoke` job), `ci.yml` (a throwaway
keystore for `assembleRelease`), `.github/workflows/upgrade.yml` (new), `release.yml` (one upgrade
step before publishing), the perf jobs in `nightly-ui.yml`, `tools/ui_release_run.sh`,
`tools/ui_upgrade_run.sh`, `tools/ui_perf_run.sh` (new), `startup-baseline.json` (new),
`UpgradeProbe.kt` (new, uitest only), LeakCanary for the `uitest` build only,
`android/.../uitest/MoreJourneysTest.kt` (new), `docs/testing/inventory.md` and
`docs/testing/bug-bash.md` (new), one rule in `tools/check_docs.py`
**Shared-file hooks:** `UiTestApp.kt`: register `UpgradeProbe`; `AppDriver.kt`: the LeakCanary
rule

> Runs on **Sonnet**. **0 requests**: the release APK runs with the emulator's network off.
>
> **1. Release-APK smoke** (`release-smoke`, API 35, ≤ 10 min). CI signs `assembleRelease` with a
> keystore made in the job by `keytool` (never committed, never a secret). `aapt2 dump badging`:
> app id `il.transit.planner`, the version, not debuggable, no `uitest` classes; airplane mode on,
> launch, skip the tour, open Settings, a 500-event monkey: no crash, no ANR, no request leaving.
> The release build is not minified today (`isMinifyEnabled = false`); this job is where a minify
> change will show.
> **2. Startup-time gate** in the same job: cold start (`am force-stop`, `am start -W`,
> `TotalTime`) 10 times, median against `startup-baseline.json`; **fail at > baseline + 25 %**
> (emulator noise; owner decision 24). Macrobenchmark is not used (a separate module and a
> profileable build for little extra on an emulator).
> **3. Upgrade test** (`upgrade.yml`: `workflow_dispatch`, weekly, and PRs touching
> `core/src/main/**/user/**`, `core/src/main/**/history/**`, `android/**/data/**`,
> `AndroidManifest.xml` or `android/app/build.gradle.kts`): build the latest release tag's
> `uitest` APK and HEAD's with the job's debug key; install the old one, write T1's user-data
> sample for that version into its DataStore with `run-as`, launch so it arms its reminder and
> night refresh; `adb install -r` the new one; `UpgradeProbe` logs places, Home/Work, saved
> trips, routines, My lines, history, settings, the reminder re-armed (`MY_PACKAGE_REPLACED`),
> the night refresh enqueued, the channels intact; any loss fails. `release.yml` (owner decision
> 12): before publishing, install the previous release's published APK, then the newly signed
> one with `adb install -r`: it must install over it (+~8 min per release).
> **4. Leaks:** LeakCanary (`leakcanary-android-instrumentation`) in the `uitest` build only
> (owner decision 25), `DetectLeaksAfterTestSuccess` in every journey: a retained Activity,
> ViewModel, MapView or service fails the journey with the leak trace.
> **5. Nightly performance reports** (no gate yet; numbers in the nightly summary, a trend table
> in `docs/testing/perf.md` updated by the boss at close-out):
> - **map jank:** a scripted pan/zoom over Tel Aviv with results drawn, `dumpsys gfxinfo
>   framestats`: janky-frame % and p90 frame time;
> - **30-min simulated ride:** a GPX of a recorded trip fed to the emulator (`adb emu geo fix`
>   every second), `dumpsys batterystats --reset` before and `--checkin` after: wakeups, wake
>   locks, GPS-on time, alarms of the app; a loose gate only after 2 weeks of numbers.
> **6. Journey inventory + gaps.** `docs/testing/inventory.md`: every user-visible feature in
> `ROADMAP.md` × unit test × ViewModel test (T3) × Paparazzi × journey × fault matrix (T4) × real
> phone, gaps marked. A `check_docs.py` rule: every journey named there exists and every
> journey test is listed. Journeys for the **5 biggest gaps** in `MoreJourneysTest.kt` (API 34
> and 35), proposed: Drop-off pick and send, Park & ride, stop sheet → day timetable, share an
> address in (`ACTION_SEND`), monthly report export.
> **7. Bug bash.** `docs/testing/bug-bash.md`: the release checklist for the owner and testers
> (install over the old version, every feature once on a real phone, Hebrew and English, dark
> mode, TalkBack basics, a reminder that really rings, one real ride with the get-off alert,
> offline, report with the issue form). The boss links it in each release's close-out.
>
> **Docs:** `ui-testing` (release smoke, startup gate, upgrade test, LeakCanary, the nightly perf
> reports, the inventory); `release-apk` (the upgrade step, the bug bash); no release fragment;
> tick ROADMAP T8.

## E5: Accessibility pass + TalkBack journey
**Branch:** `claude/p11-accessibility`
**Skills:** `i18n-rtl`, `ui-testing`, `add-feature`, `android-build`
**Needs:** Wave 6 merged (it touches every screen and every journey)
**Owns:** `present/Speech.kt` (core, new), semantics in every `ui/screens/*` file,
`ui/MainScreen.kt`, `ui/MapController.kt` (the map's semantics only),
`android/.../uitest/A11yJourneysTest.kt` (new), `AppDriver.kt` (the check after every step),
`LayoutAudit.kt` / `Atf.kt` (rules made blocking), `PanelsTest.kt` (a font 2.0 variant), `strings_a11y.xml`
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
> - **Every journey** (owner, 2026-10-13): `AppDriver` runs the same ATF check after each step of
>   every journey (`JourneysTest`, `MoreJourneysTest`, T7's and T4's), on every API level, errors
>   blocking. Today's findings are fixed in this package or listed in the PR with the reason.
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
**Needs:** Wave 7 merged and owner decision 14 = yes
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

Proposals in bold. The boss records the answers here, under a "Decided" line. Decisions 11, 17–19
and 27 block Wave 1 (T1, T2); 3–4 block E1 (Wave 2); the rest can be answered before their wave.

**Decided (owner, 2026-10-13, about 23:10 Israel time): all as proposed** (1–31, the proposals
in bold below, including the full testing track at about $180–250).
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
9. **Weekly live checks (T5), changed:** **Tuesdays 03:47 Israel time: ~25 golden trips (≤ 60
   requests) + the contract check (≤ 5 Transitous + 1 Open-Meteo + 1 Photon), so ≤ 65 Transitous
   requests a week from CI; failures open an issue and never block a PR; the owner mentions it in
   the existing Transitous Matrix thread** (`docs/transitous-contact.md`), since it is routine use.
10. **Workflows that write issues (T5, T7), changed:** **allow `issues: write` only in the steps
    that post to four issues: "contract-drift", "golden-trips", "nightly", "CI flake log".**
11. **Coverage gate (T1):** **core line coverage ≥ today's value rounded down; it only ratchets
    up**; generated data excluded.
12. **Upgrade test (T8):** **a throwaway key in PR runs (old tag and HEAD built in the job), and
    in `release.yml` a real install of the newly signed APK over the previous published one**
    (+~8 min per release).
13. **Test Lab (T7):** is the Spark project from `ui-testing` set up (repo variables
    `GCP_PROJECT`, `WIF_PROVIDER`, `SA_EMAIL`)? **If yes, T7 adds its no-root steps to the
    weekly real-phone run; if not, nothing.**
14. **NFC spike (E6):** OK to read **your own card on your own phone, nothing stored or sent**,
    and will you lend the card for two scans? **Proposal: yes, after Wave 7; go/no-go as in the
    brief.** No means E6 is dropped.
15. **Releases, changed:** **v0.9.1 after Wave 4** (E1, E2, E3, with T1–T3, T5, T7 already
    guarding them), **v0.10.0 at the end** (E4, E5, and E6 if it goes), each after a bug bash
    (T8's checklist from v0.10.0).
16. **Mutation testing (T5):** *changed: see 20.*
17. **Style checker (T2), new:** **ktlint with a baseline of today's code; no detekt** (its
    complexity rules are noisy on Compose and would need a long tuning round).
18. **Android Lint gate (T2), new:** **errors fail; a fixed list of warning ids (API level,
    permissions, exported components, pending-intent flags, hard-coded text, RTL, missing
    translation) fails when new; everything else is frozen in `lint-baseline.xml`.**
19. **APK-size gate (T2), new:** **fail at > 2 % or > 1 MB over `apk-size.json`; a PR that grows
    on purpose updates the file and says why.**
20. **Mutation gate (T5), changed from 16 ("report, no gate"):** **a weekly full report, and from
    its first run a PR gate on the changed core classes (≤ 2 points under their baseline); the
    baseline only rises.**
21. **Android JVM coverage (T3), new:** **line coverage of `ui/` (Compose screens excluded) ≥
    today's value rounded down, ratcheting.**
22. **Golden failures (T5), new:** **open or comment on the "golden-trips" issue; the boss turns a
    real regression into a fix package; never a PR gate** (live data changes every week).
23. **GPS tracks (T5), new:** **the owner records 3 real rides (bus, train, light rail) with a free
    GPX logger, trimmed 300 m from home and work, committed as fixtures; synthetic noisy tracks
    until then.**
24. **Startup gate (T8), new:** **`am start -W` cold start, median of 10 on the API 35 emulator,
    fail at > baseline + 25 %; no Macrobenchmark.**
25. **LeakCanary (T8), new:** **in the `uitest` build only, a retained leak fails the journey;
    never in debug or release.**
26. **Nightly runs (T7, T8), new:** **01:47 Israel time, only if `main` changed since the last
    nightly; failures comment on the "nightly" issue; perf numbers are reports until 2 weeks of
    data exist.**
27. **Dependency scan + licences (T2), new:** **OSV-scanner weekly and on Gradle changes; a
    HIGH/CRITICAL with a fixed version fails; runtime licences allowed: Apache-2.0, MIT, BSD, ISC
    (EPL only for test libraries); GPL/AGPL/unknown in the APK fails.**
28. **Bug reports from friends (T2), new:** **a GitHub issue form (needs a GitHub account); "Share
    crash log" over WhatsApp stays the no-account route.**
29. **Tester pass, new:** **a Sonnet tester (~$1) checks every E package's tests against its
    brief before merge, in the same round as its review.**
30. **End-to-end update (T6), new:** **a gate on API 35: the fake release installs through the
    system installer (UiAutomator taps Install); other API levels stop at the installer screen.**
31. **Cost, new:** the wider testing track raises Phase 11 from about $110–160 to **about
    $180–250**. **Proposal: run it all; if not, drop T8's battery and jank reports and T7's nightly
    chaos first (about −$8), never the gates.**

---

## Close-out (boss)
- After Wave 4 and a bug bash (`release-apk`'s check, T8's `docs/testing/bug-bash.md` from
  v0.10.0): join the E1–E3 fragments into `docs/releases/v0.9.1.md` (if the owner wants the
  release), add its user-data sample (T1's tool), delete the fragments, release with
  `release-apk`.
- Once all packages are merged, join the remaining `docs/releases/next/*.md` into
  `docs/releases/v0.10.0.md` (English block, then Hebrew block, then the usual update and
  attribution lines). Then delete the fragments.
- Read the "CI flake log", "nightly", "golden-trips" and "contract-drift" issues: any test that
  flaked twice in 14 days, and any real regression, gets a fix package before the release.
- Check every Phase 11 ROADMAP line and every skill row (`python3 tools/check_docs.py`).
- Ask the owner to check each feature on the Pixel (the reminder check with a test reminder,
  missed it on a real ride, pass expiry with a date 3 days out, the TalkBack checklist), then
  release with the `release-apk` skill.
