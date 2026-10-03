---
name: release-apk
description: Ship a signed APK to friends and family through GitHub Releases. Use for "make a release", "send the app to my friends", "bump the version", "the release workflow failed", or anything about the signing key.
---

# Releasing

**A release = a tag.** Two ways, both on `main` with CI green:
- push a tag: `git tag v0.2.0 && git push origin v0.2.0`, or
- **Actions → Release → Run workflow**, version `0.2.0` — the workflow creates the tag itself.
  **Claude cloud sessions must use this one**: their git proxy allows branch pushes but
  answers a tag push with HTTP 403 (seen on v0.1.0). Trigger it with the GitHub
  `actions_run_trigger` tool (workflow `release.yml`, ref `main`, input `version`).
It refuses a version that is not `x.y.z` or whose release already exists.
`.github/workflows/release.yml` then: core tests → decode keystore → `assembleRelease` →
`apksigner verify` → `gh release create` with `israel-transit-planner-v0.2.0.apk`.
Notes come from `docs/releases/v0.2.0.md` if it exists (write it: English + Hebrew, 5 lines),
else GitHub's generated notes.
Since Phase 8, parallel PRs don't edit that file. Each one writes a fragment in
`docs/releases/next/<package>.md`. Before triggering the release, join the fragments into
`docs/releases/vX.Y.Z.md`: all the English bullets, `---`, all the Hebrew bullets, then the
usual update and attribution lines (copy them from the previous version's notes). Delete the
fragments in the same commit.

## Versioning (android/app/build.gradle.kts)
- `versionName` = the tag without `v`; `versionCode` = major×10000 + minor×100 + patch.
- So tags must only go UP, and minor/patch stay below 100. A lower code will not install
  over a higher one.
- Local/CI builds are `0.0.0-dev` (code 1); `UpdateCheck` never nags a `-dev` build.
- Debug builds are `il.transit.planner.debug`. Before that, a sideloaded `app-debug` (debug
  key) blocked v0.1.0 on the owner's Pixel with a bare **"App not installed"**, Android's
  message for a signature mismatch. The APK was fine; uninstalling the old copy fixed it.

## The signing key — the one thing that must never be lost
- Lives in 4 **repository secrets**: `RELEASE_KEYSTORE_B64` (base64 of the .jks),
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
  Settings → Secrets and variables → Actions. Secrets are not visible to forks' PRs and are
  masked in logs; the workflow deletes the decoded file in an `always()` step.
- The owner keeps the original `.jks` + passwords backed up (password manager / cloud drive).
  **Losing it means every friend must uninstall (losing saved places/history) to update.**
- Never commit the keystore or passwords — the guard hook blocks `*.jks`,
  `keystore.properties`, `.env`. Never print the secrets in a workflow step.
- Local signed build (optional): `android/keystore.properties` (git-ignored) with
  `storeFile=/abs/path/transit-release.jks`, `storePassword=…`, `keyAlias=transit`,
  `keyPassword=…`, then `./gradlew -p android :app:assembleRelease`.

## Creating the key (owner, once)
```
keytool -genkeypair -v -keystore transit-release.jks -alias transit -keyalg RSA -keysize 4096 -validity 36500
[Convert]::ToBase64String([IO.File]::ReadAllBytes("transit-release.jks")) | Set-Clipboard   # PowerShell
```

## Checks
- `ci.yml` builds `assembleRelease` unsigned on every push, so release-only breaks show up
  before tagging.
- After a release: the Releases page has the APK; a phone on the previous version shows the
  "Update available" banner within a day (or on next launch after 24 h) and installs over it.
- Minify/R8 is OFF on purpose (MapLibre + kotlinx-serialization keep rules). Turning it on
  needs a phone test of every screen first.
- Fares are a copied table (`core/.../fare/Fares.kt`, `CHECKED` date). Prices are reported
  to rise in January 2027: before a release after that, re-read the source URL in its KDoc,
  update the columns + `CHECKED`, and fix the pinned test in `FareTest`.
- No Play Store: paid account, and USE_EXACT_ALARM would not pass Play review for this app.
