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
  not the build. Retry with `--max-workers=1`; a session-local init script in
  `~/.gradle/init.d/` may point at Google's Central mirror. Never commit that script.

## CI

`.github/workflows/ci.yml`: job `core` (tests + `tools/check_docs.py`), then job `android`
(assembleDebug, uploads the APK as artifact `app-debug`, kept 14 days). The APK from a
green run is how friends test a build before a release.

## Emulator / device

- Mock GPS: emulator "…" → Location → set a point, or load a GPX route (used to test the
  "get off next stop" alert).
- Install on a phone: `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`,
  or download the CI artifact and open it on the phone (allow "install unknown apps").
