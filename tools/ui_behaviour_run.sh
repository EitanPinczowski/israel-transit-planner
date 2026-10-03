#!/usr/bin/env bash
# Behaviour on one Android version: first launch with location denied, the user journeys,
# process death, and (API 35 only) a seeded monkey run. Called by ui-tests.yml:
#   tools/ui_behaviour_run.sh <api level> <apk dir> <out dir>
# Exit 1 on a failed test or any crash/ANR of the app.
set -u
API=$1
APKS=$2
OUT=$3
PKG=il.transit.planner.uitest
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
ACT=$PKG/il.transit.planner.MainActivity
mkdir -p "$OUT"
failed=0

check() { # $1 = name of the step, $2 = output file
  if grep -q "INSTRUMENTATION_STATUS_CODE: -2\|INSTRUMENTATION_FAILED\|Process crashed" "$2"; then
    echo "::error::$1 failed on API $API"
    failed=1
  fi
}

instrument() { # $1 = class(es), $2 = output name
  echo "::group::$1"
  adb shell am instrument -w -r -e class "$1" -e profile "api$API" -e locale device -e theme light "$RUNNER" | tee "$OUT/instrument-$2.txt"
  echo "::endgroup::"
  check "$1" "$OUT/instrument-$2.txt"
}

# Fresh install, no permissions granted: J1 first.
adb install -r "$APKS"/app-uitest.apk
adb install -r "$APKS"/app-uitest-androidTest.apk
adb shell settings put secure show_ime_with_hard_keyboard 1
adb emu geo fix 34.8013 31.2622 >/dev/null
# Old images boot with the keyguard up and SystemUI still settling; a permission prompt
# shown then can vanish (seen on API 26). Unlock and give it a moment first.
adb shell input keyevent 82
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
sleep 10
adb logcat -c
instrument il.transit.planner.uitest.FirstLaunchTest first-launch
instrument il.transit.planner.uitest.SmokeTest,il.transit.planner.uitest.JourneysTest journeys

# Process death: leave the app, let the system kill it, come back.
echo "::group::process death"
adb shell am start -W -n "$ACT" >/dev/null
sleep 4
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell am kill "$PKG"
adb shell am start -W -n "$ACT" >/dev/null
sleep 4
adb shell pidof "$PKG" >/dev/null || { echo "::error::app not running after process death on API $API"; failed=1; }
echo "::endgroup::"

if [[ $API == 35 ]]; then
  echo "::group::monkey"
  adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
  # Seeded, so a crash it finds reproduces with the same seed.
  adb shell monkey -p "$PKG" --pct-syskeys 0 --throttle 60 -s 4242 -v 6000 > "$OUT/monkey.txt" 2>&1
  tail -5 "$OUT/monkey.txt"
  grep -q "// CRASH\|// NOT RESPONDING" "$OUT/monkey.txt" && { echo "::error::monkey found a crash/ANR (seed 4242)"; failed=1; }
  echo "::endgroup::"
fi

adb pull "/sdcard/Android/data/$PKG/files/ui" "$OUT/" >/dev/null 2>&1 || true
adb logcat -d -b crash > "$OUT/crash.txt" || true
if grep -q "Process: $PKG" "$OUT/crash.txt"; then
  echo "::error::the app crashed on API $API (see crash.txt)"
  grep -A20 "Process: $PKG" "$OUT/crash.txt" | head -60
  failed=1
fi
exit $failed
