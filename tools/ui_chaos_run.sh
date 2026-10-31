#!/usr/bin/env bash
# Phase 11 T7, the nightly chaos steps (nightly-ui.yml): the ones that need root or are slow.
#   tools/ui_chaos_run.sh chaos  <api> <apk dir> <out dir>   standby buckets, time zone + DST, clock skew,
#                                                            low storage, freeform windows, pseudolocales
#   tools/ui_chaos_run.sh monkey <api> <apk dir> <out dir>   the long monkey: 3 seeds x 20000 events
# Each step prints what it saw; only a broken promise fails the run. Findings go to findings.txt.
set -u
MODE=$1
API=$2
APKS=$3
OUT=$4
PKG=il.transit.planner.uitest
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
ACT=$PKG/il.transit.planner.MainActivity
SYS=il.transit.planner.uitest.SystemJourneysTest
CHAOS=il.transit.planner.uitest.ChaosJourneysTest
mkdir -p "$OUT"
: > "$OUT/crash.txt"
failed=0
# shellcheck source=ui_instrument.sh
source "$(dirname "$0")/ui_instrument.sh"

lc() { adb logcat -d -b crash >> "$OUT/crash.txt"; adb logcat -c; }
fail() { echo "::error::$*"; failed=1; }
note() { echo "::notice::finding: $*"; echo "- $*" >> "$OUT/findings.txt"; }
instrument() { # $1 = class#method(s), $2 = output name, rest = extra -e pairs
  local classes=$1 name=$2; shift 2
  echo "::group::$classes"
  ui_instrument "$classes" "$OUT/instrument-$name.txt" -e profile "api$API" -e locale device -e theme light "$@"
  local status=$?
  echo "::endgroup::"
  if [[ $status != 0 ]]; then fail "$classes failed on API $API"; fi
  return $status
}
say() { adb logcat -d -s SystemJourney:I ChaosJourney:I | tr -d '\r'; }
device_now() { adb shell date +%s | tr -d '\r'; }
posted() { adb shell dumpsys notification --noredact | grep -qE "\|$PKG\|$1\|"; }
# How many times the app posted notification $1: one `notification_enqueue` event per post. (Counting the
# key in `dumpsys notification` counted the lines of ONE record's dump: "rang 4 times" every night.)
enqueued() { adb logcat -d -b events -s notification_enqueue | grep -cE "[[,]$PKG,$1,"; }

adb install -r -g "$APKS"/app-uitest.apk
adb install -r -g "$APKS"/app-uitest-androidTest.apk
ui_prep_emulator
adb shell locksettings set-disabled true >/dev/null 2>&1 || true
adb shell svc power stayon true >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb emu geo fix 34.8013 31.2622 >/dev/null
sleep 3
lc

if [[ $MODE == monkey ]]; then
  adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
  for seed in 4242 777 20261019; do
    echo "::group::monkey seed $seed"
    adb shell monkey -p "$PKG" --pct-syskeys 0 --throttle 40 -s "$seed" -v 20000 > "$OUT/monkey-$seed.txt" 2>&1
    tail -5 "$OUT/monkey-$seed.txt"
    grep -q "// CRASH\|// NOT RESPONDING" "$OUT/monkey-$seed.txt" && { fail "monkey found a crash/ANR (seed $seed)"; ui_monkey_evidence "$OUT/monkey-$seed.txt"; }
    adb logcat -d -b crash >> "$OUT/crash.txt"; adb logcat -c # each seed's evidence on its own
    echo "::endgroup::"
    echo "- monkey seed $seed: $(grep -c . "$OUT/monkey-$seed.txt") lines" >> "$OUT/findings.txt"
  done
  echo "monkey seeds: 4242 777 20261019" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
  adb logcat -d -b crash >> "$OUT/crash.txt"
  grep -q "Process: $PKG" "$OUT/crash.txt" && fail "the app crashed under the monkey (crash.txt)"
  exit $failed
fi

adb root >/dev/null 2>&1; adb wait-for-device
# Time moves by hand from here on.
adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0

# --- App standby: `rare` must ring, `restricted` is recorded and E1's check must say so ---------
for bucket in rare restricted; do
  echo "::group::standby $bucket"
  adb shell am set-standby-bucket "$PKG" "$bucket"
  echo "bucket now: $(adb shell am get-standby-bucket "$PKG" | tr -d '\r')"
  adb shell cmd notification cancel "$PKG" 1005 >/dev/null 2>&1 || true
  lc
  if instrument "$SYS#arm_test_reminder" "arm-$bucket"; then
    due=$(( $(say | grep -o "test-reminder due=[0-9]*" | tail -1 | cut -d= -f2) / 1000 ))
    ok=0
    while [[ $(device_now) -lt $((due + 150)) ]]; do posted 1005 && { ok=1; break; }; sleep 5; done
    if [[ $ok == 1 ]]; then echo "standby $bucket: rang"
    elif [[ $bucket == rare ]]; then fail "standby rare: the test reminder did not ring"
    else note "standby restricted: the test reminder did not ring within 2.5 min"; fi
    adb shell am set-standby-bucket "$PKG" "$bucket"
    [[ $bucket == restricted ]] && instrument "$CHAOS#check_reports_the_restriction" check-restriction
  fi
  adb shell am set-standby-bucket "$PKG" active
  echo "::endgroup::"
done

# --- Time zone + DST ------------------------------------------------------------------------------
set_zone() { adb shell cmd alarm set-timezone "$1" >/dev/null 2>&1 || adb shell service call alarm 3 s16 "$1" >/dev/null 2>&1; }
set_time() { adb shell cmd alarm set-time "$1" >/dev/null 2>&1 || adb shell date "@$(( $1 / 1000 ))" >/dev/null 2>&1; }
echo "::group::time zone and DST"
set_zone Asia/Jerusalem; lc
if instrument "$SYS#arm_trip_state" arm-trip-tz; then
  leave=$(say | grep -o "trip-state leaveAt=[0-9]*" | tail -1 | cut -d= -f2)
  count() { adb shell dumpsys alarm | tr -d '\r' | grep -c "il.transit.planner.action.LEAVE"; }
  before=$(count)
  set_zone Europe/London; sleep 8
  mid=$(count)
  set_zone Asia/Jerusalem; sleep 8
  after=$(count)
  echo "LEAVE alarms: before=$before, in London=$mid, back=$after"
  [[ $mid -le $before && $after -le $before && $after -ge 1 ]] || fail "time zone change: the trip alarm was lost or armed twice ($before/$mid/$after)"
  instrument "$SYS#trip_state_unchanged" tz-unchanged -e leaveAt "$leave"
fi
# Sun 25 Oct 2026 01:59 Israel summer time (UTC+3 = 22:59 UTC on Sat 24th): the clocks go back at 02:00.
set_time "$(date -u -d '2026-10-24 22:59:00' +%s)000"; sleep 3
adb shell cmd notification cancel "$PKG" 1005 >/dev/null 2>&1 || true
lc
enq0=$(enqueued 1005)
if instrument "$SYS#arm_test_reminder" arm-dst; then
  due=$(( $(say | grep -o "test-reminder due=[0-9]*" | tail -1 | cut -d= -f2) / 1000 ))
  ok=0; n=0
  while [[ $(device_now) -lt $((due + 150)) ]]; do posted 1005 && { ok=1; break; }; sleep 5; done
  sleep 30 # a second ring would come right after the first
  n=$(( $(enqueued 1005) - enq0 ))
  echo "across the DST change: rang=$ok, posts=$n"
  adb logcat -d -b events -s notification_enqueue | grep -E "[[,]$PKG,1005," | cut -c1-200
  [[ $ok == 1 ]] || fail "DST: the test reminder armed across the 25 Oct change did not ring"
  [[ $n -le 1 ]] || fail "DST: the test reminder rang $n times"
fi
echo "::endgroup::"

# --- Clock skew ----------------------------------------------------------------------------------------
now_ms=$(( $(date -u +%s) * 1000 ))
for skew in "+3h:$((now_ms + 3 * 3600 * 1000))" "-1d:$((now_ms - 86400 * 1000))"; do
  echo "::group::clock skew ${skew%%:*}"
  set_time "${skew#*:}"; sleep 3
  instrument "$CHAOS#clock_skew" "skew-${skew%%:*}"
  echo "::endgroup::"
done
set_time "$now_ms"

# --- Low storage ------------------------------------------------------------------------------------------
echo "::group::low storage"
avail_kb=$(adb shell df -k /data | tr -d '\r' | awk 'NR==2 {print $4}')
fill_mb=$(( avail_kb / 1024 - 40 ))
if [[ $fill_mb -gt 0 ]]; then
  adb shell "fallocate -l ${fill_mb}M /data/local/tmp/fill.bin" || adb shell "dd if=/dev/zero of=/data/local/tmp/fill.bin bs=1M count=$fill_mb" >/dev/null 2>&1
  echo "free now: $(adb shell df -k /data | tr -d '\r' | awk 'NR==2 {print int($4/1024)}') MB"
fi
instrument "$CHAOS#low_storage" low-storage
adb shell rm -f /data/local/tmp/fill.bin
echo "::endgroup::"

# --- Freeform window, resized while results show ----------------------------------------------------
echo "::group::freeform window"
adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
adb shell wm size 2208x1840; adb shell wm density 420
lc
adb shell am start -S -W -n "$ACT" --windowingMode 5 >/dev/null 2>&1
sleep 5
task=$(adb shell am stack list 2>/dev/null | tr -d '\r' | grep -m1 -oE "taskId=[0-9]+" | cut -d= -f2)
if [[ -n $task ]]; then
  for box in "0 0 1100 1500" "100 100 1900 1700" "0 0 700 900"; do
    adb shell am task resize "$task" $box >/dev/null 2>&1; sleep 3
    echo "resized to $box: $(adb shell pidof "$PKG" | tr -d '\r' || true)"
  done
else
  note "freeform: no task id found, the window was not resized"
fi
[[ -n $(adb shell pidof "$PKG" | tr -d '\r') ]] || fail "freeform: the app is gone after resizing"
adb shell am force-stop "$PKG"
adb shell wm size reset; adb shell wm density reset
echo "::endgroup::"

# --- Pseudolocales on P1 and P5: the screens + layout audit under en-XA / ar-XB ---------------------
for prof in "P1 720x1280 320 1.0" "P5 1080x2340 450 2.0"; do
  read -r id size dpi font <<<"$prof"
  adb shell wm size "$size"; adb shell wm density "$dpi"; adb shell settings put system font_scale "$font"
  sleep 3
  for loc in en-XA ar-XB; do
    echo "::group::pseudolocale $loc on $id"
    adb shell am force-stop "$PKG"
    adb shell cmd locale set-app-locales "$PKG" --locales "$loc" >/dev/null 2>&1
    ui_instrument il.transit.planner.uitest.ScreensTest "$OUT/instrument-$id-$loc.txt" -e profile "$id" -e locale "$loc" -e theme light
    status=$?
    echo "::endgroup::"
    if [[ $status != 0 ]]; then
      fail "ScreensTest failed under $loc on $id"
      grep -o "blocking layout findings on .*\|Process crashed.*" "$OUT/instrument-$id-$loc.txt" | sort -u | sed "s/^/  $id $loc: /"
    fi
  done
done
adb shell cmd locale set-app-locales "$PKG" --locales "" >/dev/null 2>&1 || true
adb shell wm size reset; adb shell wm density reset; adb shell settings put system font_scale 1.0

adb pull "/sdcard/Android/data/$PKG/files/ui" "$OUT/" >/dev/null 2>&1 || true
adb logcat -d > "$OUT/logcat.txt" || true
adb logcat -d -b crash >> "$OUT/crash.txt" || true
grep -q "Process: $PKG" "$OUT/crash.txt" && fail "the app crashed (crash.txt)"
[[ -f $OUT/findings.txt ]] && { echo "### Chaos findings (not failures)"; cat "$OUT/findings.txt"; } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
exit $failed
