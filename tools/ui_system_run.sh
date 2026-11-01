#!/usr/bin/env bash
# Phase 11 T7, the `system` job: what the phone does to the app, on one API 34 emulator.
# Doze, a reboot, airplane mode, rotation / theme / language mid-flow, location revoked mid-ride,
# and process death in every car tab. Called by ui-tests.yml:
#   tools/ui_system_run.sh <api level> <apk dir> <out dir>
# Each step prints what it saw; only a broken promise fails the run (see .claude/skills/ui-testing).
set -u
API=$1
APKS=$2
OUT=$3
PKG=il.transit.planner.uitest
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
ACT=$PKG/il.transit.planner.MainActivity
S=il.transit.planner.uitest.SystemJourneysTest
mkdir -p "$OUT"
: > "$OUT/crash.txt"
failed=0
# shellcheck source=ui_instrument.sh
source "$(dirname "$0")/ui_instrument.sh"

# Clears logcat but keeps what the crash buffer held (a later step must not hide an earlier crash).
lc() { adb logcat -d -b crash >> "$OUT/crash.txt"; adb logcat -c; }
fail() { echo "::error::$*"; failed=1; }
note() { echo "::notice::finding: $*"; echo "- $*" >> "$OUT/findings.txt"; }
instrument() { # $1 = class#method(s), $2 = output name
  echo "::group::$1"
  ui_instrument "$1" "$OUT/instrument-$2.txt" -e profile "api$API" -e locale device -e theme light
  local status=$?
  echo "::endgroup::"
  if [[ $status != 0 ]]; then fail "$1 failed on API $API"; fi
  return $status
}
say() { adb logcat -d -s SystemJourney:I | tr -d '\r'; }
device_now() { adb shell date +%s | tr -d '\r'; }
posted() { # notification id $1 is posted by the app
  adb shell dumpsys notification --noredact | grep -qE "\|$PKG\|$1\|"
}
boot_wait() {
  adb wait-for-device
  for _ in $(seq 1 120); do
    [[ $(adb shell getprop sys.boot_completed | tr -d '\r') == 1 ]] && break
    sleep 2
  done
  sleep 10
  adb shell input keyevent 82
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

adb install -r -g "$APKS"/app-uitest.apk
adb install -r -g "$APKS"/app-uitest-androidTest.apk
ui_prep_emulator
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell locksettings set-disabled true >/dev/null 2>&1 || true
adb shell svc power stayon true >/dev/null 2>&1 || true
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb emu geo fix 34.8013 31.2622 >/dev/null
sleep 5
lc

# --- Doze: the test reminder (E1) rings within 2 min of its time ---------------------------
echo "::group::doze"
lc
if instrument "$S#arm_test_reminder" arm-test; then
  due_ms=$(say | grep -o "test-reminder due=[0-9]*" | tail -1 | cut -d= -f2)
  if [[ -z $due_ms ]]; then
    fail "doze: the test reminder was not armed"
  else
    due=$((due_ms / 1000))
    adb shell dumpsys battery unplug >/dev/null
    adb shell dumpsys deviceidle enable deep >/dev/null 2>&1 || true
    adb shell dumpsys deviceidle force-idle | tr -d '\r'
    echo "deep idle: $(adb shell dumpsys deviceidle get deep | tr -d '\r')"
    ok=0
    while [[ $(device_now) -lt $((due + 120)) ]]; do
      if posted 1005; then ok=1; break; fi
      sleep 5
    done
    late=$(($(device_now) - due))
    adb shell dumpsys deviceidle unforce >/dev/null 2>&1 || true
    adb shell dumpsys battery reset >/dev/null
    if [[ $ok == 1 ]]; then echo "doze: the test reminder was posted ${late}s after its time"; else fail "doze: the test reminder was not posted within 2 min of its time (deep idle)"; fi
  fi
fi
echo "::endgroup::"

# --- Reboot: the trip reminder and the pass notice come back, the ride does not -------------
echo "::group::reboot"
lc
if instrument "$S#arm_trip_state" arm-trip; then
  adb shell dumpsys alarm | grep -E "il.transit.planner.action.(LEAVE|PASS_EXPIRY)" | sed 's/^/  before reboot: /'
  adb reboot
  boot_wait
  leave=0 pass=0
  for _ in $(seq 1 45); do
    dump=$(adb shell dumpsys alarm | tr -d '\r')
    leave=$(grep -c "il.transit.planner.action.LEAVE" <<<"$dump")
    pass=$(grep -c "il.transit.planner.action.PASS_EXPIRY" <<<"$dump")
    [[ $leave -ge 1 && $pass -ge 1 ]] && break
    sleep 4
  done
  echo "after reboot: LEAVE alarms=$leave, PASS_EXPIRY alarms=$pass"
  [[ $leave -ge 1 ]] || fail "reboot: the trip reminder's alarm did not come back"
  [[ $pass -ge 1 ]] || fail "reboot: the pass-expiry alarm did not come back"
  rides=$(adb shell dumpsys activity services "$PKG" | grep -c "RideService")
  [[ $rides == 0 ]] || fail "reboot: a ride service came back by itself"
  lc
fi
echo "::endgroup::"

# --- Airplane mode and the three mid-flow switches ---------------------------------------------
instrument "$S#airplane_mode" airplane
adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true
instrument "$S#mid_flow_rotation,$S#mid_flow_theme,$S#mid_flow_language" mid-flow
adb shell cmd uimode night no >/dev/null 2>&1 || true
say | sed 's/^/  /' > "$OUT/system-journey.txt"

# --- Process death in every car tab and errands (blocking since Phase 11 R1) ----------------------
process_death() { # $1 = tab
  local tab=$1 ready restored pid_before pid_after key_before key_after
  echo "::group::process death: $tab"
  lc
  adb shell am start -S -W -n "$ACT" --es uitest_process_death_tab "$tab" >/dev/null
  ready=""
  for _ in $(seq 1 45); do
    ready=$(adb logcat -d -s ProcessDeath:I | tr -d '\r' | grep -o "ready .*" | tail -1)
    [[ -n $ready ]] && break
    sleep 1
  done
  adb shell input keyevent KEYCODE_HOME
  sleep 2
  pid_before=$(adb shell pidof "$PKG" | tr -d '\r')
  adb shell am kill "$PKG"
  sleep 1
  if [[ -n $pid_before ]] && adb shell pidof "$PKG" >/dev/null; then
    adb shell run-as "$PKG" kill -9 "$pid_before" || true
    sleep 1
  fi
  lc
  adb shell am start -W -n "$ACT" >/dev/null
  restored=""
  for _ in $(seq 1 45); do
    restored=$(adb logcat -d -s ProcessDeath:I | tr -d '\r' | grep -o "restored .*" | tail -1)
    [[ -n $restored ]] && break
    sleep 1
  done
  pid_after=$(adb shell pidof "$PKG" | tr -d '\r')
  echo "before: $ready (pid $pid_before)"
  echo "after:  $restored (pid $pid_after)"
  if [[ -z $ready ]]; then
    fail "process death $tab: the probe never got the search set up"
  elif [[ -z $pid_after || $pid_before == "$pid_after" ]]; then
    fail "process death $tab: the process was not killed and restarted (pid $pid_before -> $pid_after)"
  elif [[ $restored != "restored saved=true "* ]]; then
    fail "process death $tab: the Activity did not come back from its saved state: '$restored'"
  elif [[ ! $restored =~ \ requests=[0-9]+$ ]]; then
    # Checked before slicing, so a cut or changed log line fails readably instead of comparing garbage.
    fail "process death $tab: the restored line does not end in 'requests=<n>': '$restored'"
  else
    # Phase 11 R1: the whole line is a promise (tab, places, errand stops, options, the selected one,
    # its trip sheet, the car path), and the restore sends 0 plan requests (the reopened trip sheet's
    # `trip` and a reopened stop sheet's `stoptimes` are left out by the probe, by name).
    key_before=${ready#ready }
    key_after=${restored#restored saved=true }; asked=${key_after##* requests=}; key_after=${key_after% requests=*}
    if [[ $key_before != "$key_after" ]]; then
      fail "process death $tab: '$key_before' came back as '$key_after'"
    fi
    if [[ $asked != 0 ]]; then fail "process death $tab: the restore sent $asked request(s), expected 0"; fi
  fi
  adb shell am force-stop "$PKG"
  echo "::endgroup::"
}
for tab in BETTER_START DROP_OFF PICK_UP PARK_RIDE ERRANDS; do process_death "$tab"; done

# --- Location revoked in the middle of a ride ---------------------------------------------------
echo "::group::permission revoked mid-ride"
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION
adb shell appops set "$PKG" android:mock_location allow
lc
adb shell am start -S -W -n "$ACT" --ez uitest_ride_setup true >/dev/null
rready=""
for _ in $(seq 1 45); do
  rready=$(adb logcat -d -s SystemProbe:I | tr -d '\r' | grep -o "ride-ready .*" | tail -1)
  [[ -n $rready ]] && break
  sleep 1
done
echo "$rready"
pid_before=$(adb shell pidof "$PKG" | tr -d '\r')
if [[ $rready != *"active=true"* ]]; then
  fail "permission revoked: the ride did not start, so there was nothing to revoke under ($rready)"
else
  adb shell pm revoke "$PKG" android.permission.ACCESS_FINE_LOCATION
  adb shell pm revoke "$PKG" android.permission.ACCESS_COARSE_LOCATION
  sleep 4
  pid_mid=$(adb shell pidof "$PKG" | tr -d '\r')
  [[ -z $pid_mid || $pid_mid != "$pid_before" ]] && echo "Android ended the process (pid $pid_before -> '${pid_mid:-none}')" \
    || note "permission revoked: the process survived the revoke (pid $pid_before)"
  lc
  adb shell am start -W -n "$ACT" >/dev/null
  rafter=""
  for _ in $(seq 1 30); do
    rafter=$(adb logcat -d -s SystemProbe:I | tr -d '\r' | grep -o "ride-after .*" | tail -1)
    [[ -n $rafter ]] && break
    sleep 1
  done
  echo "$rafter"
  [[ -n $rafter ]] || fail "permission revoked: the app did not come back"
  [[ $rafter != *"active=true"* ]] || fail "permission revoked: the ride is still active without the location permission ($rafter)"
  [[ $rafter != *"ongoing=0"* && -n $rafter ]] && note "permission revoked: the ride notification is still there ($rafter)"
  # E2: "Missed it" tapped now has no location permission: it asks for the start, with no request.
  # The notification died with the process, and the action's door is not exported (F5), so the shell
  # can't send it: the probe sends the action's own PendingIntent, as the notification does.
  # A new task: Android keeps the old one after a force-stop, recreates its Activity from the saved state
  # and hands it our extra as a new intent ("delivered to top"), which the probe never sees.
  adb shell am force-stop "$PKG"
  lc
  adb shell am start -W -n "$ACT" --activity-clear-task --ez uitest_missed_it true >/dev/null
  missed=""
  for _ in $(seq 1 40); do
    missed=$(adb logcat -d -s SystemProbe:I | tr -d '\r' | grep -o "missed-it .*" | tail -1)
    [[ -n $missed ]] && break
    sleep 1
  done
  echo "$missed"
  [[ $missed == "missed-it kind=NEED_START editing=FROM requests=0" ]] || fail "permission revoked: Missed it did not ask for the start: '$missed'"
fi
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION
adb shell am force-stop "$PKG"
echo "::endgroup::"

# F5: the real Quick Settings tile, tapped through System UI (`cmd statusbar click-tile`, API 29+). Its
# trip home must reach the app through the non-exported alias and open (Settings with no Home, or the trip).
echo "::group::quick settings tile"
TILE=$PKG/il.transit.planner.tile.NextTripTile
lc
adb shell cmd statusbar add-tile "$TILE" >/dev/null 2>&1
sleep 3
adb shell cmd statusbar click-tile "$TILE"
tile=""
for _ in $(seq 1 30); do
  tile=$(adb logcat -d -s SystemProbe:I | tr -d '\r' | grep -o "tile-open .*" | tail -1)
  [[ -n $tile ]] && break
  sleep 1
done
echo "$tile"
[[ $tile == "tile-open alias=true opened=true" ]] || fail "tile: a tap did not open the trip home through the alias: '$tile'"
adb shell cmd statusbar remove-tile "$TILE" >/dev/null 2>&1 || true
adb shell cmd statusbar collapse >/dev/null 2>&1 || true
adb shell am force-stop "$PKG"
echo "::endgroup::"

adb pull "/sdcard/Android/data/$PKG/files/ui" "$OUT/" >/dev/null 2>&1 || true
adb logcat -d > "$OUT/logcat.txt" || true
adb logcat -d -b crash >> "$OUT/crash.txt" || true
if grep -q "Process: $PKG" "$OUT/crash.txt"; then
  fail "the app crashed on API $API (see crash.txt)"
  echo "::group::crash.txt"; cat "$OUT/crash.txt"; echo "::endgroup::"
fi
if [[ -f $OUT/findings.txt ]]; then
  { echo "### System job findings (not failures)"; cat "$OUT/findings.txt"; } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
fi
if [[ $failed == 1 ]]; then
  echo "::group::logcat tail (a step failed)"; tail -300 "$OUT/logcat.txt"; echo "::endgroup::"
fi
exit $failed
