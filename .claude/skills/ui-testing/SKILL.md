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
on screen above it, at least its top 96 dp (D11). The row never shows with the keyboard up: the sheet
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
- Popups and buttons that appear after a search (the ⋮ menu items, the results' ★ Save trip)
  are composed a frame later: go through `AppDriver.tapMenuItem` / `tapSaveTrip`, which wait.
- A new fixture: record it (skill `transitous-api`), add its name to `ReplayTransitApi.FIXTURES`.
- The fixed clock means fixture dates matter: alarms/rides use 2026-10-05.

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
