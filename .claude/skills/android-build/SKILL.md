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

**SDK levels** (Phase 10 D10): `compileSdk = 36` for the Android 16 Live Update APIs
(`androidx.core` 1.17's `NotificationCompat.ProgressStyle`, which needs compileSdk 36 and
AGP ≥ 8.9.1, so AGP is 8.9.1). **`targetSdk` stays 35** (owner decision 8): raising it opts
the app into Android 16's behaviour changes (edge-to-edge without opt-out, predictive back,
orientation/resizability ignored on large screens…), which want their own package and test
round. Code for 36 checks `Build.VERSION.SDK_INT >= BAKLAVA` (36).

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

## Static gates (Phase 11 T2)
Each gate starts from a baseline of the code as it was, so only NEW problems fail. Nobody
reformats or fixes old code to get green. Reports land in the job summary.

| gate | where | fails when | read it | update it honestly |
|---|---|---|---|---|
| Android Lint | `android` job, `:app:lintDebug` | any error, or a new `NewApi`, `InlinedApi`, `MissingPermission`, `UnspecifiedImmutableFlag`, `ExportedReceiver`, `ExportedService`, `UnsafeIntentLaunch`, `HardcodedText`, `RtlHardcoded`, `RtlEnabled`, `MissingTranslation` (raised to fatal in `android/app/lint.xml`) | job summary "Lint"; artifact `lint-report` (`lint-results-debug.html`) | fix the code. Only a finding that is truly intended: `./gradlew -p android :app:updateLintBaseline` on a machine with the SDK, commit `lint-baseline.xml`, say why in the PR body. Never loosen `lint.xml` |
| ktlint | `core` job (`ktlintCheck`) and `android` job (`:app:ktlintCheck`), then `python3 tools/ktlint_gate.py` | a finding sits on a line the PR added or changed (git diff against the merge-base with `main`) | the `::error file=…,line=…` lines; counts per module in the summary | fix the line (`./gradlew -p core ktlintFormat` also works on a file you are already editing). Rules are tuned in `.editorconfig`. `tools/ktlint-baseline.json` is only today's counts (for the drift line); refresh it with `tools/ktlint_gate.py --write-baseline` after `ktlintCheck`, never to hide a new finding |
| APK size | `android` job, `tools/apk_size.py` on the unsigned release APK | more than +2 % or +1 MB over `apk-size.json` | the folder table (`lib/`, `res/`, `classes*.dex`, `assets/`) in the summary | a PR that grows the app on purpose runs `python3 tools/apk_size.py <apk> --write` and says why in its body; the owner accepts it. Shrinks: store the smaller value too |
| Dependencies | `deps.yml`: weekly, on demand, and PRs/pushes touching Gradle files | a HIGH/CRITICAL (CVSS ≥ 7) advisory with a fixed version (OSV-scanner), or a release-APK dependency whose licence is not in `tools/licence_allowlist.txt` (Apache-2.0, MIT, BSD, ISC; GPL/AGPL/none never) | the run summary; artifact `deps-report` | upgrade the library. A licence exception is an owner decision: an `exception group:artifact reason` line in the allowlist |

`tools/gradle/deps-report.init.gradle` (`./gradlew -p android -I $PWD/tools/gradle/deps-report.init.gradle :app:depsReport`)
writes the resolved list `build/deps/gradle.lockfile` and each runtime dependency's POM, which the gate reads.
Cloud sessions have no Android SDK: the Lint and APK gates can only be proven by a CI run
(a scratch branch). `ktlintCheck` and the deps report for `core` run locally.

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
