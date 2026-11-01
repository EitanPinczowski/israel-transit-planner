---
name: ui-testing
description: Testing the app's UI and UX on many phones - the uitest build, emulator profiles, the layout audit (R1-R8), journeys, the ci-screens report, Firebase Test Lab real phones. Use for "test the UI", "does it look right on small phones", a red UI tests run, reading screenshots, or adding a screen/journey to the tests.
---

# UI testing

Cloud sessions can't run Android (no SDK, no KVM) and can't download CI artifacts. So the
app runs on GitHub's free emulators and the results come back as git: **the `ci-screens`
branch** (single commit, force-pushed by each run). Read it with
`git clone -q --depth 1 -b ci-screens <repo url> <scratchpad>/screens`: `README.md` holds the
findings, `screens.md` every screenshot, `shots/<run>/<state>.jpg` the images (Read shows them).
CI logs: GitHub MCP `get_job_logs`.

## Layers (all free, none touch Transitous)

| layer | where | runs |
|---|---|---|
| core tests, contrast (`ContrastTest`), replay scenarios (`ReplayTransitApiTest`) | here | `./gradlew -p core test -q` |
| strings/RTL lint | here + CI | `python3 tools/check_strings.py` |
| golden trips (live, ~21 requests) | here, on demand | `./gradlew -p core goldenTrips -q` (skill `golden-trips`) |
| screens × 9 phone profiles + layout audit | `.github/workflows/ui-tests.yml` | push touching android/core main/gradle/tools ui_* |
| journeys on API 26/29/33/34/35, process death, monkey | same workflow | same |
| real phones + Robo crawler | `.github/workflows/devices.yml` (Test Lab) | weekly + manual, once set up |

## The uitest build

Build type `uitest` (= debug + x86_64, app id `il.transit.planner.uitest`) runs
`src/uitest/UiTestApp`: `ReplayTransitApi` over `core/src/test/resources/fixtures` (moved in
time to each request), clock fixed at **Mon 2026-10-05 07:55 Israel** (the fixtures' day),
blank local map style, scripted update banner. debug/release are unchanged. Seams in the app:
`TransitApp` is open (`clock`, `api`, `updates`, `mapStyle`), `UiTags` on the screen regions,
`MainActivity.routeOnScreen()`.

`core` main compiles against the Java 8 API (`-Xjdk-release=1.8`): Android 8 (minSdk 26) has
only Java 8 `java.time`. `Duration.truncatedTo` crashed API 26/29 once — now a compile error.

## Phone profiles (`tools/ui_layout_run.sh`, one API 35 emulator reshaped per run)

| id | stands for | size @ dpi | font | extra |
|---|---|---|---|---|
| P1 | old/budget 16:9 | 720×1280 @320 (360×640 dp) | 1.0 | 3-button nav, + dark |
| P2 | same, bigger text | 720×1280 @320 | 1.3 | 3-button |
| P3 | narrowest (Display size Large) | 1080×2340 @540 (320 dp wide) | 1.0 | |
| P4 | Samsung A-class | 1080×2340 @450 (384×832 dp) | 1.0 | punch-hole, + dark |
| P5 | same, largest font | 1080×2340 @450 | 2.0 | |
| P6 | Pixel-class | 1080×2400 @420 (411×914 dp) | 1.0 | |
| P7 | Fold cover screen | 904×2316 @420 | 1.0 | |
| P8 | landscape | 2400×1080 @420 (914×411 dp) | 1.0 | + dark |
| P9 | tablet / unfolded | 2208×1840 @420 | 1.0 | |

The emulator boots with a 2400×2400 screen because `wm size` can only shrink the physical
screen (a 1080×1920 AVD silently clamped every taller profile, seen 2026-10-03). Each profile in
Hebrew and English. Check the report's **Runs** table: it lists the screen each device
*reported*, so a profile that did not apply is visible. Don't trust a profile you haven't
checked there or by eye.

Journeys run on API 26 (AOSP `default` image), 29, 33, 34, 35 (`google_apis`). Android 8's
SystemUI crashes when an app opens over a lock screen that is still up, killing the permission
prompt; `ui_behaviour_run.sh` disables the lock screen and waits for it to go first. It still
crashed on every API 26 run (2026-10-03), so FirstLaunchTest ends there as "not verifiable on
this emulator" (finding J1, an assumption, not a pass) when it saw that crash; Test Lab's oldest
real phone covers Android 8. Without a SystemUI crash, a missing prompt still fails.
That crash leaves an "Application Error" dialog holding the **key focus**: a shell Back goes
to the dialog, not the app (J4's "two Backs on Android 8", 2026-10-06). Before a key journey,
`AppDriver.focusApp()` (clears it), and `focusedWindow()` (`mCurrentFocus`) in the message.

## Rules (`LayoutAudit.kt`, report-only until each is fixed, then make it blocking)

R1 search card and bottom panel overlap · R2 tappable outside the usable screen (keyboard-hidden
items = one finding per screen; behind an open dialog doesn't count) · R3 clipped/squeezed text, or a time/price/duration cut with …
· R4 a target under 40 dp whose grown 48 dp tap area reaches another · R5 Accessibility Test Framework
(contrast, labels) · R6 Hebrew row labels on the right · R7 route inside the visible map, map
not squeezed · R8 the selected option's action row (`UiTags.ACTIONS`: Start trip, Remind me, Send to
driver): every button ≥ 48 × 48 dp and inside the usable screen, and the selected card (`UiTags.SELECTED`)
on screen above it, at least its first line, 32 dp (D11; the list is 76 dp in landscape, so a whole card can't be required). The row never shows with the keyboard up: the sheet
is composed only while no field is edited. Blocking: R1, R7, R8. Journey findings: J1 first launch without location, J3 alarm armed, J4 Back
closes what is open (one Back, even with the keyboard up), J10 Settings reachable with results open. Broken journeys and crashes fail the run; findings never do.

**Process death** (`ui_behaviour_run.sh`, every API level, D11). An instrumented test dies with
its process, so the shell drives it and the uitest-only `ProcessDeathProbe` (an
`ActivityLifecycleCallbacks` in `UiTestApp`) does the taps: `am start -S --ez
uitest_process_death_setup true` → BGU → Tel Aviv, second option, its first ride's trip sheet,
log `ProcessDeath: ready mode=… selected=… option=… sheet=…`; Home, `am kill` (a SIGKILL through
`run-as` when Android won't), `am start` → the recreated Activity logs `restored saved=true …` plus
the requests since the restart. The step fails unless the pid changed, the Activity came back from
its saved state and both lines match (same tab, option and sheet). The options come from the Trip cache (`SavedUi`), never a
new `plan`; the reopened trip sheet asks its one `trip` again.

## Adding to it

- A new screen state: a `@Test` in `ScreensTest` (set up via the ViewModel, `AppDriver.place`).
- A new journey: `JourneysTest`, through taps; assert what the user sees and
  `app.replay.calls` (it doubles as the request-budget check).
- Setting a time in a journey (E7, `j16`): tap Depart / Arrive by, the day chip by
  `DayTags.day(offset)`, then `DayTags.CLOCK_TYPE` (the clock's "type the time") and replace the
  two text fields under `DayTags.CLOCK`, then `DayTags.CLOCK_OK`. The day step, clock and
  "Tomorrow at …?" are Compose dialogs; times are the app's clock (`TransitApp.clock`), so the
  uitest build's Mon 2026-10-05 07:55 makes "Tomorrow" Tue 6 Oct.
- Popups and buttons that appear after a search (the ⋮ menu items, the results' ★ Save trip)
  are composed a frame later: go through `AppDriver.tapMenuItem` / `tapSaveTrip`, which wait.
- A new fixture: record it (skill `transitous-api`), add its name to `ReplayTransitApi.FIXTURES`.
- The fixed clock means fixture dates matter: alarms/rides use 2026-10-05.
- The phone's own clock is today. Android 16 silently drops a notification whose `when` is over 14 days
  old: a notification dated by a replayed trip must first be moved to now (`ReplayTransitApi.moved`, J14).

## Real phones: Firebase Test Lab, Spark plan (free, no card)

`devices.yml` stays skipped until the repo variables exist. Owner, once:
1. console.firebase.google.com → new project, **Spark** plan (never upgrade to Blaze: with no
   billing account, over-quota runs are refused, never charged; quota 5 physical + 10 virtual/day).
2. Cloud Shell: `gcloud services enable testing.googleapis.com toolresults.googleapis.com
   iamcredentials.googleapis.com sts.googleapis.com`.
3. Service account `testlab-ci` with role **Editor** on the project (Firebase's documented CI role).
4. Keyless login (no key file): a Workload Identity pool + GitHub OIDC provider limited to
   `assertion.repository=='EitanPinczowski/israel-transit-planner'`; grant
   `roles/iam.workloadIdentityUser` on the service account to that repository's principalSet.
5. Repo → Settings → Variables: `GCP_PROJECT`, `WIF_PROVIDER`
   (`projects/<num>/locations/global/workloadIdentityPools/<pool>/providers/<provider>`), `SA_EMAIL`.
Then Actions → "Real phones (Test Lab)" → Run. Results: the `ci-devices` branch, same layout.

## Flaky instrumentation deaths (CI emulator stability, 2026-10-13)

**Symptom:** a screens shard (or journey) ends `Process crashed` on a different screen and profile
each time, with no `FATAL EXCEPTION` from `il.transit.planner` in logcat; a re-run passed (PRs
#62, #65, #67, #68). The test process is the app's process.

**Root cause (fixed, `claude/maplibre-crash-fix`):** not the emulator and not memory. A
**MapLibre native abort**: `Fatal signal 6` in a `Worker N` thread of `libmaplibre.so`,
`jni::PendingJavaException`, a tombstone, no Java stack. The pending exception (run 38361200196,
`W System.err` on the same tid) was a `ConcurrentModificationException`: MapLibre's default
location renderer (`SymbolLocationLayerRenderer`, still so in 12.3.1) passes ONE mutable `Feature`
to `GeoJsonSource.setGeoJson` on every compass tick (`RenderMode.COMPASS`) and keeps adding
properties to it on the main thread while a worker converts the last one through JNI. Location
is granted in the UI tests, so any screen could hit it, and so could a real phone.
Fix: `useSpecializedLocationLayer(true)` (native `LocationIndicatorLayer`, no GeoJSON), pinned by
`SmokeTest.location_dot_is_not_drawn_from_geojson`. Map callbacks and style access go through
`ui/MapGuard` (logged under tag `MapGuard`, never rethrown into native code).

**What CI does now** (`tools/ui_instrument.sh`, sourced by both run scripts):
- every death prints evidence first: crash buffer, `am_proc_died`/`am_crash` events, MapLibre and
  exception lines, lmkd/ANR lines, meminfo, df, dmesg;
- retried **once per test** (the open test + the tests that never started): only a process killed
  with an empty crash buffer. The retry is a `::warning::` naming its kind and a "Retry" block in
  the job summary, so it is never silent;
- red at once: any app crash (FATAL EXCEPTION, any native signal including a MapLibre abort, ANR),
  any failed assertion, and any test that dies twice. Nothing is skipped or quarantined;
- `-no-snapshot`, system image cached per API level, ANR dialogs / package verifier / animations off.
  The AVD keeps its default RAM: memory was not the cause, and a 4 GB AVD may have starved the
  runner ("lost communication", run 38355318297).
- A new native abort: read the `System.err` / exception lines of the evidence dump for the pending
  Java exception, then find which MapLibre callback (or MapLibre-internal Java) threw it.

## System and chaos journeys (Phase 11 T7)

What the phone does to the app, as opposed to what the user taps. Everything answers from the
replay (0 requests); a step prints what it saw and fails only on a broken promise, anything else
is a finding (`::notice::finding:` + the job summary).

**PR time: the `system` job** (`tools/ui_system_run.sh`, API 34 `google_apis`, `SystemJourneysTest`,
≤ 17 min). Steps that outlive the test process are driven from the shell, the rest are tests:
- *Doze*: `SystemJourneysTest#arm_test_reminder` arms E1's test alarm, the shell `force-idle`s the
  phone and polls `dumpsys notification` (key `|<pkg>|1005|`): posted within 2 min of its time.
- *Reboot*: `#arm_trip_state` (a leave reminder + a pass date), `adb reboot`, then `dumpsys alarm`
  lists the `LEAVE` and `PASS_EXPIRY` actions again and `dumpsys activity services` has no `RideService`.
- *Airplane*: `#airplane_mode` flips the real switch; the replay is not the network, so "no signal"
  is `replay.failing` as in J8. The switch itself must not hurt the app.
- *Mid-flow*: `#mid_flow_rotation|theme|language`, each (1) while a search loads (`UiTestApp.planDelayMs`
  holds `plan` 4 s), (2) with the trip sheet open, (3) during a leave countdown: same options, same
  selected one, no request beyond one plain search (`app.replay.calls`).
- *Process death in every tab*: `ProcessDeathProbe` with `--es uitest_process_death_tab BETTER_START|
  DROP_OFF|PICK_UP|PARK_RIDE|ERRANDS`: the second option, its first ride's trip sheet. Blocking since
  Phase 11 R1: tab, places, errand stops, option count, the selected option, its sheet and the car path
  must all come back, with `requests=0` (the reopened sheet's `trip` left out, `ReplayTransitApi.tripCalls`).
  The Trip check stays in `ui_behaviour_run.sh` on every API level.
- *Location revoked mid-ride*: `--ez uitest_ride_setup true` starts a ride on the mock GPS, `pm revoke`
  kills the process, the restart must not crash or leave the ride active. Then the shell sends the
  intent the "Missed it" action carries (the notification died with the process): without the location
  permission it must ask for the start (`kind=NEED_START editing=FROM`) with 0 requests.

**Nightly** (`nightly-ui.yml`, 01:47 Israel winter time, only when `main` changed since the last good
run; a failure comments on the open issue "nightly"; `ui_chaos_run.sh chaos|monkey`, API 35, root):
app standby (`rare` rings: blocking; `restricted`: recorded, and E1's check must report it), time zone
(`Europe/London` and back: LEAVE alarm not lost, not doubled, same absolute time) and the 25 Oct 2026 DST
change (test alarm across it rings once), clock skew (+3 h, −1 day), low storage (`fallocate` to < 50 MB
free: saving a place and a search must not crash, the places file still decodes), a freeform window
resized three times (alive; the layout audit does not run there: `ActivityScenario` can't pick a
windowing mode), pseudolocales en-XA and ar-XB on P1 and P5 (`ScreensTest`, whose blocking rules R1/R7/R8
apply; R3 stays a finding) and the long monkey (3 seeds × 20 000 events, seeds in the log).
`isPseudoLocalesEnabled` is on in the `uitest` build type only.

**Flake log.** `ui_instrument.sh` writes every retry **and every failed test** (assertion or error, with its
class#test) to `flake.log` next to its output; the last step of
each UI job (`tools/ui_flake_log.sh`) comments on the open issue "CI flake log" (created once) with the
run link, API, test and kind, and also for a job re-run (`run_attempt > 1`). **A test that flakes twice
in 14 days gets a fix package, never a skip.** `issues: write` is given to those jobs for that step only.

## Phase 11 F2: root causes of three CI flakes
- **"multithreaded access to SnapshotStateObserver"** (Settings dialog, ~1 run in 12): Compose 1.7 computed a
  node's semantics lazily, under the owner's snapshot observer, on whichever thread read it first. The test
  thread did (`fetchOneOrThrow` → `printToString` after every fetch) while the main thread's accessibility /
  content-capture checker (every 100 ms after a change, outside Compose's idle tracking) did too. **Still open.**
  Ruled out: Compose BOM 2025.08.00 (1.9; upstream computes semantics on the main thread) breaks Paparazzi 1.3.5
  (620 shots error even when recording) and grows the APK 2.8 %; an idling resource that walks every root's
  semantics on the main thread at each `waitForIdle` (the race still hit the first fetch after idle, run
  41234918862: the main thread composes/reads after idle, e.g. lazy prefetch or the a11y checker). Next: Compose
  1.8+ together with Paparazzi 2.x and re-recorded goldens, as its own package.
- **Nightly chaos**, red every night (not flakes): `low_storage` was a real app crash (DataStore ENOSPC out of
  `viewModelScope.launch`; `UserStore` now drops the write, returns false and the screen says "Not saved: phone storage is full"); en-XA on P5 squeezed the map (the credit chip wraps
  at font 2.0; the sheet cap uses its measured height); DST "rang 4 times" counted the lines of one record in
  `dumpsys notification` (now `notification_enqueue` events).
- **Monkey**: `ui_monkey_evidence` prints the monkey's CRASH/ANR block and the device evidence into the job
  log, because artifacts can't be read from a cloud session.

## Network-fault matrix (Phase 11 T4)

**PR time: the `faults` job** (`tools/ui_faults_run.sh`, API 35 `google_apis`, `FaultMatrixTest`). 0 requests to
the internet: `MockServerRule` (`uitest/mock/`) runs OkHttp's `MockWebServer` on 127.0.0.1 inside the test process.
- **The seam (uitest build only):** `UiTestApp.useMockServer(url)` before `launch()` sets `UiTestApp.network`
  (`MockNetwork`): `api` becomes `GuardedTransitApi(MotisClient("<url>/motis/<id>"))`, the backup geocoder Photon at
  `<url>/photon` (no phone Geocoder), the weather Open-Meteo at `<url>/meteo`; read timeouts 3 s. The server only decides
  *whether and how* to answer: a normal Transitous answer is the replay's answer to that same call, as MOTIS JSON, kept under
  `<id>`; Photon and Open-Meteo answer `photon_rager` / `openmeteo_rain_telaviv`. `reset()` clears the mode, so every other
  test keeps the replay. Cleartext HTTP is allowed to the loopback only (`src/uitest/res/xml/uitest_network.xml`).
- **Faults** (`Fault`): 429 twice, 503 twice (`Retry-After: 1`), no answer (timeout), HTML, empty body, `{}`, body cut
  halfway, offline (`goOffline`: server shut down + airplane mode; `goOnline`: a new server, the app pointed at it), and
  429 then 200 (per request: the guard's retry must get it).
- **Each case asserts:** the screen finishes within 30 s (no endless spinner), shows its failure (the message, or the offline
  copy; `{}` may read as "no results"; map stops and the rain note fail silently), Retry brings the answer, the server's count
  for the screen's endpoints ≤ the budget constant (×2 for 429/503), at least 2 for "429 then 200"; then the same action
  again sends 0. A crash fails the job (crash buffer). The job summary lists every case: `<screen> <fault> -> <outcome> sent cap`.
- **Adding a screen:** a `Screen` in `FaultMatrixTest`: endpoints (the path after `/api/<v>/`, or `photon` / `meteo`),
  the budget constant, `act`, `outcome` (null while loading), `message`, `retry`, `reset`, `repeat`. Vary the input per
  action with `seq` where a cache would otherwise answer (the fixed clock never expires one: `clockShift` moves "now").

## Security and in-app update tests (Phase 11 T6)

In the `faults` job after the matrix (`tools/ui_intent_fuzz.sh`, one line in `ui_faults_run.sh`). 0 requests to the internet.
- **Intent fuzzing** (`IntentFuzzTest`, explicit intents to the exported `MainActivity`, replay mode): SEND with no / wrong-type /
  240k-char text, VIEW with `geo:NaN`, `geo:999,999`, `javascript:`, `file://`, `content://` (+ grant flags), `intent:`, `data:`,
  look-alike map hosts, markup / SQL / RLO in `geo:` queries, and our own notification / tile / shortcut extras broken. Each case:
  the Activity stays up, saved data unchanged, refused input sends **0** (`replay.calls`), searched text ≤ 1 (a typed search's).
  The shell repeats the kinds with `adb shell am`, checks the look-alike host does not resolve to us, the spoofed
  `BOOT_COMPLETED` (refused to the shell; `BootReceiver` ignores other actions), the tile (System UI only), the provider.
- **FileProvider** (`FileProviderTest`): only `cache/updates` and `cache/reports` get URIs (not `filesDir`, DataStore, the
  crash log, a look-alike folder, `..`); crafted `..` URIs don't open; the shell can't read without a grant; a report older
  than a day is deleted at start.
- **Update** (`UpdateFlowTest`, `mock/FakeReleaseServer`): GitHub Releases on the loopback over TLS (a test-only
  self-signed cert, `androidTest/assets/fake_release_server.p12`). The app's own clients (`ApkInstaller.client()`,
  `UpdateChecker.client()`) get a DNS that answers 127.0.0.1 and records every name, sockets that land on the server's
  port and record the host:port asked for, and trust in that cert only, so the app's real URL checks decide.
  `UiTestApp.releaseChecker` puts a real checker + installer behind the banner. What it pins: `release-apk` skill.
- **End to end on API 35** (`UpdateInstallSteps`, run only by the script, in order): the job builds the next APK
  (`RELEASE_VERSION=0.0.2` → `versionCode` 2) and re-signs the downloaded pair with the same debug key. Step 1: banner →
  download through GitHub's redirect → digest OK → Android's installer opens on `content://<pkg>.updates/updates/update.apk`.
  The script taps the installer's button (from a `uiautomator dump`: the app's process dies with the install, so the
  test can't), waits for `versionCode=2`, starts the app; step 2: the new version, places and settings kept.
