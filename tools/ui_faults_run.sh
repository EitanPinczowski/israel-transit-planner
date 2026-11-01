#!/usr/bin/env bash
# Phase 11 T4, the `faults` job: the network-fault matrix (FaultMatrixTest) on one API 35 emulator.
# Every networked screen against a MockWebServer on the emulator's own loopback (inside the test
# process): 429, 503, timeout, HTML, empty, {}, cut body, offline, 429 then 200, and the request
# count per endpoint against each feature's budget. Nothing reaches the internet. Called by ui-tests.yml:
#   tools/ui_faults_run.sh <api level> <apk dir> <out dir>
# T6's security and update tests join this job (one line below the matrix).
set -u
API=$1
APKS=$2
OUT=$3
PKG=il.transit.planner.uitest
mkdir -p "$OUT"
: > "$OUT/crash.txt"
failed=0
# shellcheck source=ui_instrument.sh
source "$(dirname "$0")/ui_instrument.sh"

fail() { echo "::error::$*"; failed=1; }

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
adb logcat -c

echo "::group::fault matrix"
ui_instrument il.transit.planner.uitest.FaultMatrixTest "$OUT/instrument-faults.txt" -e profile "api$API" -e locale device -e theme light \
  || fail "the fault matrix failed on API $API"
echo "::endgroup::"
# What each case saw: "<screen> <fault> -> <outcome> sent=<n> cap=<n> server=<counts per endpoint>".
echo "::group::cases"
adb logcat -d -s FaultMatrix:I | tr -d '\r' | grep -- " -> " | sed 's/^.*FaultMatrix: //' | tee "$OUT/cases.txt"
echo "::endgroup::"
{
  echo "### Network-fault matrix (API $API)"
  echo '```'
  cat "$OUT/cases.txt"
  echo '```'
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true

adb logcat -d > "$OUT/logcat.txt" || true
adb logcat -d -b crash >> "$OUT/crash.txt" || true
if grep -q "Process: $PKG" "$OUT/crash.txt"; then
  fail "the app crashed on API $API (see crash.txt)"
  echo "::group::crash.txt"; cat "$OUT/crash.txt"; echo "::endgroup::"
fi
if [[ $failed == 1 ]]; then
  echo "::group::logcat tail (a step failed)"; tail -300 "$OUT/logcat.txt"; echo "::endgroup::"
fi
exit $failed
