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
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
CLASS=il.transit.planner.uitest.FaultMatrixTest
# Every matrix screen runs every fault: a run that saw fewer cases did not test them. Tests that are not a
# screen of the matrix (F6: rain after offline, data card vs server) don't call matrix() and add no 9 cases.
SRC="$(dirname "$0")/../android/app/src/androidTest/java/il/transit/planner/uitest/FaultMatrixTest.kt"
# Count the @Test functions that call matrix(): comments and strings are stripped, and the definition itself
# (`private fun matrix(`) is not a call, so neither a comment nor a line break can change the number.
SCREENS=$(python3 - "$SRC" <<'PY'
import re, sys
src = open(sys.argv[1], encoding="utf-8").read()
src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
src = re.sub(r"//[^\n]*", "", src)
src = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)
src = re.sub(r"\bfun\s+matrix\s*\(", "fun _def(", src)
tests = re.split(r"@Test\b", src)[1:]
print(sum(1 for t in tests if re.search(r"\bmatrix\s*\(", t.split("@Test")[0])))
PY
)
FAULTS=9
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
adb logcat -G 16M >/dev/null 2>&1 || true # the case lines must outlive a 10-min run
adb logcat -c

echo "::group::fault matrix"
ui_instrument "$CLASS" "$OUT/instrument-faults.txt" -e profile "api$API" -e locale device -e theme light \
  || fail "the fault matrix failed on API $API"
echo "::endgroup::"
# What each case saw: "<screen> <fault> -> <outcome> sent=<n> cap=<n> server=<counts per endpoint>".
echo "::group::cases"
adb logcat -d -s FaultMatrix:I | tr -d '\r' | grep -- " -> " | sed 's/^.*FaultMatrix: //' | tee "$OUT/cases.txt"
echo "::endgroup::"
cases=$(wc -l < "$OUT/cases.txt")
echo "$cases cases ($SCREENS screens x $FAULTS faults expected)"
[[ $cases -ge $((SCREENS * FAULTS)) ]] || fail "the fault matrix ran $cases of $((SCREENS * FAULTS)) cases"
TESTS=$(grep -cE "^[[:space:]]*@Test fun" "$SRC") # the screens plus the F6 tests beside them
grep -q "^OK ($TESTS tests)" <(tr -d '\r' < "$OUT/instrument-faults.txt") || fail "not all $TESTS fault-matrix tests passed"
{
  echo "### Network-fault matrix (API $API)"
  echo '```'
  cat "$OUT/cases.txt"
  echo '```'
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true
# Phase 11 T9: search and everyday-use journeys against the same mock server, in English and then in Hebrew (the
# app's own language for the package, API 33+). Typing must send one request per pause; the ATF check runs after every step.
SEARCH=il.transit.planner.uitest.SearchJourneysTest
SEARCH_TESTS=$(grep -cE "^[[:space:]]*@Test fun" "$(dirname "$0")/../android/app/src/androidTest/java/il/transit/planner/uitest/SearchJourneysTest.kt")
for lang in en he; do
  echo "::group::search journeys ($lang)"
  adb shell cmd locale set-app-locales "$PKG" --locales "$lang" >/dev/null 2>&1 || true
  ui_instrument "$SEARCH" "$OUT/instrument-search-$lang.txt" -e profile "api$API" -e locale "$lang" -e theme light \
    || fail "the search journeys failed on API $API ($lang)"
  grep -q "^OK ($SEARCH_TESTS tests)" <(tr -d '\r' < "$OUT/instrument-search-$lang.txt") || fail "not all $SEARCH_TESTS search journeys passed ($lang)"
  adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true
  echo "::endgroup::"
done
adb shell cmd locale set-app-locales "$PKG" --locales "" >/dev/null 2>&1 || true
adb logcat -d -s FaultMatrix:I | tr -d '\r' | grep -E "Typing|Task completion|IME search" | sed 's/^.*FaultMatrix: //' | tee "$OUT/search-journeys.txt" || true
bash "$(dirname "$0")/ui_intent_fuzz.sh" "$API" "$APKS" "$OUT/security" || fail "the security / update tests failed on API $API"

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
