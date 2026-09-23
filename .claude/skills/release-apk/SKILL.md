---
name: release-apk
description: Ship a signed APK to friends and family through GitHub Releases. Use for "make a release", "send the app to my friends", "bump the version".
---

# Releasing

1. CI green on `main`.
2. Bump `versionCode` (+1, always) and `versionName` in `android/app/build.gradle.kts`.
3. Signing: the release keystore lives **outside the repo** (e.g. `~/keys/transit.jks`),
   referenced from `android/keystore.properties` (git-ignored; the guard hook refuses to
   `git add` it or any `*.jks`). Losing the keystore means friends must uninstall to
   update — back it up.
4. `./gradlew -p android :app:assembleRelease` (on the owner's machine; CI does not hold
   the key).
5. GitHub Release tagged `vX.Y.Z` with the APK attached and 3–5 lines of Hebrew + English
   release notes. Friends install from the release page ("install unknown apps").
6. No Play Store: this is a private, sideloaded app. Publishing there would need a paid
   developer account and conflicts with the free-only rule.
