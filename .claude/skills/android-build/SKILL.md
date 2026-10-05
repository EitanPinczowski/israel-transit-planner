---
name: android-build
description: Build, run, or debug the Android app and the core module. Use for Gradle errors, CI failures, "build the APK", "run on the emulator", "the app crashes", or setting up Android Studio.
---

# Building

Two Gradle builds share one wrapper and one version catalog (`gradle/libs.versions.toml`):

| build | command | needs |
|---|---|---|
| `core/` (engine, API, tests) | `./gradlew -p core test -q` | any JDK ≥ 17 |
| `android/` (the app) | `./gradlew -p android :app:assembleDebug` | Android SDK (Studio or CI) |

`android/settings.gradle.kts` does `includeBuild("../core")`, and the app depends on
`il.transit:core`. Do not merge the two builds into one: `core` must stay buildable
without the SDK, because the cloud sessions cannot download it (`dl.google.com` is blocked
there) and that is where most of the logic is tested.

## Reading failures cheaply

- Run with `-q`: only failures print. Never pipe Gradle into `tail`/`grep` — the pipe
  swallows the exit code (the guard hook blocks it).
- Test failures: summarise `core/build/test-results/test/*.xml`, don't open the HTML report.
- A Gradle error: read the FIRST `What went wrong:` block only; the rest is cascade.
- `429 Too Many Requests` from Maven Central inside a cloud session is the sandbox proxy,
  not the build. Retry with `--max-workers=1` once or twice, a minute apart. Still 429:
  push and let CI run the tests (`get_job_logs` for failures). Never point Gradle at
  another repository (`~/.gradle/init.d/` mirror scripts): Auto mode blocks it as
  traffic redirection and the session stalls on a permission prompt.

## CI

`.github/workflows/ci.yml`: job `core` (tests + `tools/check_docs.py`), then job `android`
(assembleDebug, uploads the APK as artifact `app-debug`, kept 14 days). The APK from a
green run is how friends test a build before a release.

Job `screenshots` (Paparazzi, no emulator) renders `ui/ScreensTest.kt` (the whole main
screen in each state × 7 device variants) and `ui/PanelsTest.kt` (each `ui/screens/*` panel
alone, English light + Hebrew RTL dark, built from the recorded fixtures — the car features
run their real planners over them). The map is a flat placeholder and there are no system
bars, so inset handling still needs a phone. Test fixtures come from `core/src/test/resources`
(shared through `sourceSets["test"]`).

### Goldens: the job fails when a picture changes
The committed goldens are `android/app/src/test/snapshots/images/*.png`. The job first runs
`:app:verifyPaparazziDebug` against them (a changed picture, or a new shot with no golden,
fails), then records and publishes fresh pictures anyway, then fails if verify did. What
differed is published too, as `failures/delta-*.png` (golden | new | diff).

To re-record (needed whenever your PR changes how a screen looks, or adds a shot):
1. Push, and wait for the `screenshots` job of that commit (it goes red: expected).
2. `python3 tools/pull_goldens.py` — copies CI's pictures for your branch into the goldens
   folder (removing ones whose test is gone), after checking `INFO.txt` names your HEAD.
3. Look at the listed new/changed images (raw URLs below), then commit them **in the same
   PR** as the change. The next run is green.

Re-record in the PR that changes the screen, never in someone else's. Don't raise
`maxPercentDifference` to make a diff go away, and don't read a time from the clock in a
screen that has a shot (pass `now` in, like `HistoryContent`): the goldens would rot.
Local re-recording (`./gradlew -p android :app:recordPaparazziDebug`) also works on a machine
with the SDK, but fonts can differ from CI's runner; prefer CI's pictures.

On pushes the same job also writes the PNGs to the `screenshots` branch, one folder per
source branch (`/` becomes `_`, e.g. `claude_eager-curie-477jsw/`), plus `INFO.txt` with
the commit and run. The branch is one parentless commit, force-pushed each time, so it
never grows. A cloud session can't download artifacts (blob storage is blocked) but can
read `https://raw.githubusercontent.com/EitanPinczowski/israel-transit-planner/screenshots/<folder>/<file>.png`;
list a folder through `api.github.com/repos/.../contents/<folder>?ref=screenshots`.
Check that `INFO.txt` names your commit before judging a picture.

## Emulator / device

- The APK ships ARM only (`ndk.abiFilters` in `app/build.gradle.kts`): x86 MapLibre doubled
  the size (51 MB → ~half) for emulators nobody installs on. For an x86 emulator, add
  `"x86_64"` there locally — don't commit it.

- Mock GPS: emulator "…" → Location → set a point, or load a GPX route (used to test the
  "get off next stop" alert).
- Install on a phone: `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`,
  or download the CI artifact and open it on the phone (allow "install unknown apps").

## "The app crashed on my phone"
Ask for the crash log: Settings → "Share crash log" sends the last 5 crashes (time, app and
Android version, phone model, thread, stack trace) as plain text. `TransitApp.onCreate`
installs the handler (`data/CrashLogStore`, format in core `diag/CrashLog`); it writes
`filesDir/crash_log.txt` and then hands over to Android's own handler. Nothing is uploaded,
and that stays so: sending crashes anywhere automatically would need a server or a
third-party service (see `dead-ends`).
