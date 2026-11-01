#!/usr/bin/env bash
# Behaviour on one Android version: first launch with location denied, the user journeys
# (API 36: the Live Update ones only), process death, and (API 35 only) a seeded monkey run. Called by ui-tests.yml:
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
# shellcheck source=ui_instrument.sh
source "$(dirname "$0")/ui_instrument.sh"

instrument() { # $1 = class(es), $2 = output name
  echo "::group::$1"
  ui_instrument "$1" "$OUT/instrument-$2.txt" -e profile "api$API" -e locale device -e theme light
  local status=$?
  echo "::endgroup::"
  if [[ $status != 0 ]]; then echo "::error::$1 failed on API $API"; failed=1; fi
}

# Fresh install, no permissions granted: J1 first.
adb install -r "$APKS"/app-uitest.apk
adb install -r "$APKS"/app-uitest-androidTest.apk
ui_prep_emulator
adb shell settings put secure show_ime_with_hard_keyboard 1
adb emu geo fix 34.8013 31.2622 >/dev/null
# Android 8's SystemUI crashes ("System UI has stopped", NPE in
# KeyguardViewMediator.handleSetOccluded) when an app opens over a lock screen that is still
# up, and takes the permission prompt with it. Turn the lock screen off, unlock, and wait
# until it is really gone before any test starts.
adb shell locksettings set-disabled true >/dev/null 2>&1 || true
adb shell svc power stayon true >/dev/null 2>&1 || true
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell input keyevent 82
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
for _ in $(seq 1 30); do
  adb shell dumpsys window | grep -q "mShowingLockscreen=true\|isKeyguardShowing=true\|mDreamingLockscreen=true" || break
  adb shell input keyevent 82; sleep 1
done
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
sleep 5
adb logcat -c
if [[ $API == 36 ]]; then
  # Android 16 (Phase 10 D10): only the Live Update journeys, the ride and the leave countdown.
  J=il.transit.planner.uitest.JourneysTest
  instrument "$J#j12_ride_alert_fires_near_the_stop,$J#j14_leave_countdown_is_a_live_update" live-updates
else
  instrument il.transit.planner.uitest.FirstLaunchTest first-launch
  instrument il.transit.planner.uitest.SmokeTest,il.transit.planner.uitest.JourneysTest journeys
  # Phase 11 T8: the five gap journeys (docs/testing/inventory.md) on the two newest phones.
  if [[ $API == 34 || $API == 35 ]]; then instrument il.transit.planner.uitest.MoreJourneysTest more-journeys; fi
fi

# Process death: leave the app, let the system kill it, come back. The uitest build's
# ProcessDeathProbe searches, picks the second option and opens its trip sheet ("ready …"),
# then reports what the recreated Activity shows ("restored …"): the same option, the same
# sheet, and no new search (Phase 10 D11).
echo "::group::process death"
adb logcat -c
adb shell am start -S -W -n "$ACT" --ez uitest_process_death_setup true >/dev/null
ready=""
for _ in $(seq 1 30); do
  ready=$(adb logcat -d -s ProcessDeath:I | grep -o "ready .*" | tail -1)
  [[ -n $ready ]] && break
  sleep 1
done
# Android 8's SystemUI crash dialog can hold the key focus, and Home would go to it: close it first.
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
adb shell input keyevent KEYCODE_HOME
sleep 2
pid_before=$(adb shell pidof "$PKG" | tr -d '\r')
adb shell am kill "$PKG"
sleep 1
# am kill only kills a process Android considers safe to kill; the uitest build is debuggable,
# so its own uid can SIGKILL it (what the low-memory killer does), keeping the saved state.
if [[ -n $pid_before ]] && adb shell pidof "$PKG" >/dev/null; then
  echo "am kill left pid $pid_before running; SIGKILL through run-as"
  adb shell run-as "$PKG" kill -9 "$pid_before" || true
  sleep 1
fi
adb logcat -c
adb shell am start -W -n "$ACT" >/dev/null
restored=""
for _ in $(seq 1 30); do
  restored=$(adb logcat -d -s ProcessDeath:I | grep -o "restored .*" | tail -1)
  [[ -n $restored ]] && break
  sleep 1
done
pid_after=$(adb shell pidof "$PKG" | tr -d '\r')
echo "before: $ready (pid $pid_before)"
echo "after:  $restored (pid $pid_after)"
echo "focus:  $(adb shell dumpsys window | grep -m1 mCurrentFocus)"
[[ -n $pid_after ]] || { echo "::error::app not running after process death on API $API"; failed=1; }
if [[ -z $ready ]]; then
  echo "::error::process death: the probe never got the search ready on API $API"; failed=1
elif [[ $pid_before == "$pid_after" ]]; then
  echo "::error::process death on API $API: the process was never killed (pid $pid_before)"; failed=1
elif [[ $restored != "restored saved=true "* ]]; then
  echo "::error::process death on API $API: the Activity did not come back from its saved state: '$restored'"; failed=1
else
  got=${restored#restored saved=true }
  if [[ ${got%% requests=*} != "${ready#ready }" ]]; then
    echo "::error::process death on API $API: '${ready#ready }' came back as '$got'"; failed=1
  fi
fi
echo "::endgroup::"

if [[ $API == 35 ]]; then
  echo "::group::monkey"
  adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
  # Seeded, so a crash it finds reproduces with the same seed.
  adb shell monkey -p "$PKG" --pct-syskeys 0 --ignore-crashes --ignore-timeouts --throttle 60 -s 4242 -v 6000 > "$OUT/monkey.txt" 2>&1
  tail -5 "$OUT/monkey.txt"
  ui_monkey_verdict "$OUT/monkey.txt" || { echo "::error::monkey found an app crash/ANR (seed 4242)"; failed=1; ui_monkey_evidence "$OUT/monkey.txt"; }
  grep -q "Events injected: 6000" "$OUT/monkey.txt" || { echo "::error::monkey stopped early (seed 4242): $(tail -1 "$OUT/monkey.txt")"; failed=1; }
  echo "::endgroup::"
fi

adb pull "/sdcard/Android/data/$PKG/files/ui" "$OUT/" >/dev/null 2>&1 || true
adb logcat -d > "$OUT/logcat.txt" || true # always: the app's own warnings (RideService, …)
adb logcat -d -b crash > "$OUT/crash.txt" || true
if [[ $failed == 1 ]]; then
  echo "::group::logcat tail (a step failed)"; tail -300 "$OUT/logcat.txt"; echo "::endgroup::"
fi
if grep -q "Process: $PKG" "$OUT/crash.txt"; then
  echo "::error::the app crashed on API $API (see crash.txt)"
  # The artifact store is out of reach from some sessions: put the evidence in the job log.
  echo "::group::crash.txt"; cat "$OUT/crash.txt"; echo "::endgroup::"
  echo "::group::logcat (threads, fatal, compose)"
  grep -n -B2 -A30 -E 'FATAL|SnapshotStateObserver|Thread-|AndroidRuntime' "$OUT/logcat.txt" | head -300
  echo "::endgroup::"
  grep -A20 "Process: $PKG" "$OUT/crash.txt" | head -60
  failed=1
fi
exit $failed
