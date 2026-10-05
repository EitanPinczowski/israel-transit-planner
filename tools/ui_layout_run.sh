#!/usr/bin/env bash
# Runs ScreensTest (every screen + the layout audit) once per phone profile, on one
# emulator that is reshaped between runs. Called by .github/workflows/ui-tests.yml:
#   tools/ui_layout_run.sh <shard 1|2|3> <apk dir> <out dir>
# Profiles: see .claude/skills/ui-testing. Exit 1 if a test crashed (audit findings never fail).
# The emulator boots with a 2400×2400 screen (ui-tests.yml): `wm size` can shrink the screen
# but not grow it past the physical one (a 1080×1920 AVD clamped every taller profile).
# Landscape is a wide size, not a rotation.
set -u
SHARD=$1
APKS=$2
OUT=$3
PKG=il.transit.planner.uitest
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p "$OUT"

#        id  size       dpi font nav     rotation cutout dark
PROFILES=(
  "P1 720x1280  320 1.0 threebutton 0 none  yes"
  "P2 720x1280  320 1.3 threebutton 0 none  no"
  "P3 1080x2340 540 1.0 gestural    0 none  no"
  "P4 1080x2340 450 1.0 gestural    0 hole  yes"
  "P5 1080x2340 450 2.0 gestural    0 hole  no"
  "P6 1080x2400 420 1.0 gestural    0 none  no"
  "P7 904x2316  420 1.0 gestural    0 none  no"
  "P8 2400x1080 420 1.0 gestural    0 hole  yes"
  "P9 2208x1840 420 1.0 threebutton 0 none  no"
)
case $SHARD in
  1) MINE="P1 P2 P3" ;;
  2) MINE="P4 P5 P6" ;;
  3) MINE="P7 P8 P9" ;;
  *) MINE="P1 P2 P3 P4 P5 P6 P7 P8 P9" ;;
esac

adb install -r -g "$APKS"/app-uitest.apk
adb install -r "$APKS"/app-uitest-androidTest.apk
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell settings put system accelerometer_rotation 0
adb emu geo fix 34.8013 31.2622 >/dev/null
adb logcat -c

failed=0
for line in "${PROFILES[@]}"; do
  read -r id size dpi font nav rot cutout dark <<<"$line"
  [[ " $MINE " == *" $id "* ]] || continue
  adb shell wm size "$size"
  adb shell wm density "$dpi"
  adb shell settings put system font_scale "$font"
  adb shell cmd overlay enable-exclusive --category "com.android.internal.systemui.navbar.$nav" >/dev/null 2>&1 \
    || adb shell cmd overlay enable "com.android.internal.systemui.navbar.$nav" >/dev/null 2>&1
  if [[ $cutout == none ]]; then
    for c in hole corner double tall; do adb shell cmd overlay disable "com.android.internal.display.cutout.emulation.$c" >/dev/null 2>&1; done
  else
    adb shell cmd overlay enable-exclusive --category "com.android.internal.display.cutout.emulation.$cutout" >/dev/null 2>&1
  fi
  adb shell settings put system user_rotation "$rot"
  # API 33+: user_rotation alone no longer rotates; lock the window manager's rotation too.
  adb shell cmd window user-rotation lock "$rot" >/dev/null 2>&1 || adb shell wm user-rotation lock "$rot" >/dev/null 2>&1 || true
  sleep 3
  # A size change can crash the launcher ("keeps stopping" dialog over the app): close it.
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  # What the emulator really is now, so a profile that did not apply is visible.
  applied="$(adb shell wm size | tr -d '\r' | tail -1) / $(adb shell wm density | tr -d '\r' | tail -1) / font $(adb shell settings get system font_scale | tr -d '\r') / rotation $(adb shell settings get system user_rotation | tr -d '\r')"
  echo "$id applied: $applied" | tee -a "$OUT/profiles.txt"
  themes=light
  [[ $dark == yes ]] && themes="light dark"
  for locale in he en; do
    for theme in $themes; do
      run="$id-$locale-$theme"
      adb shell am force-stop $PKG
      adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
      adb shell cmd locale set-app-locales $PKG --locales "$locale" >/dev/null 2>&1
      adb shell cmd uimode night "$([[ $theme == dark ]] && echo yes || echo no)" >/dev/null
      sleep 2
      echo "::group::ScreensTest $run ($size @${dpi}dpi, font $font, $nav, rotation $rot, cutout $cutout)"
      adb shell am instrument -w -r -e class il.transit.planner.uitest.ScreensTest \
        -e profile "$id" -e locale "$locale" -e theme "$theme" "$RUNNER" | tee "$OUT/instrument-$run.txt"
      echo "::endgroup::"
      if grep -q "INSTRUMENTATION_STATUS_CODE: -2\|INSTRUMENTATION_FAILED\|Process crashed" "$OUT/instrument-$run.txt"; then
        echo "::error::ScreensTest failed on $run"
        # Outside the folded group, so the rule findings show without opening it.
        grep -o "blocking layout findings on .*\|Process crashed.*" "$OUT/instrument-$run.txt" | sort -u | sed "s/^/  $run: /"
        failed=1
      fi
    done
  done
done

adb pull "/sdcard/Android/data/$PKG/files/ui" "$OUT/" >/dev/null || echo "::warning::nothing to pull"
adb logcat -d -b crash > "$OUT/crash.txt" || true
adb shell wm size reset; adb shell wm density reset; adb shell settings put system font_scale 1.0; adb shell settings put system user_rotation 0
adb shell cmd window user-rotation free >/dev/null 2>&1 || true
exit $failed
