#!/usr/bin/env bash
# Phase 11 T8, the `release-smoke` job: the RELEASE apk (signed in the job with a throwaway keystore, never a secret) on an API 35 emulator with the
# network OFF. 0 requests.
#   1. aapt2 badging: app id il.transit.planner, the version, not debuggable, no `uitest` classes.
#   2. airplane mode on, launch, skip the tour, open Settings, a 500-event monkey: no crash, no ANR, no connected network the app could use.
#   3. startup gate: cold start (am force-stop; am start -W; TotalTime) 10 times, median vs startup-baseline.json (> baseline + 25 % fails).
#   tools/ui_release_run.sh <release apk> <out dir> <expected versionName> <expected versionCode>
set -u
APK=$1
OUT=$2
WANT_NAME=$3
WANT_CODE=$4
PKG=il.transit.planner
ACT=$PKG/.MainActivity
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$OUT"
: > "$OUT/crash.txt"
failed=0
# shellcheck source=ui_instrument.sh
source "$(dirname "$0")/ui_instrument.sh"
fail() { echo "::error::$*"; failed=1; }

# --- 1. the APK itself -------------------------------------------------------------------------------------------------
aapt2="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)/aapt2"
"$aapt2" dump badging "$APK" > "$OUT/badging.txt"
grep -m1 "^package:" "$OUT/badging.txt"
grep -q "^package: name='$PKG' " "$OUT/badging.txt" || fail "app id is not $PKG"
grep -q "versionName='$WANT_NAME'" "$OUT/badging.txt" || fail "versionName is not $WANT_NAME"
grep -q "versionCode='$WANT_CODE'" "$OUT/badging.txt" || fail "versionCode is not $WANT_CODE"
grep -q "application-debuggable" "$OUT/badging.txt" && fail "the release APK is debuggable"
# No test build in a release: UiTestApp, the probes, the replay assets, LeakCanary.
unzip -p "$APK" 'classes*.dex' | strings > "$OUT/dex-strings.txt"
for needle in "UiTestApp" "ProcessDeathProbe" "UpgradeProbe" "PerfProbe" "leakcanary" "uitest_network"; do
  grep -q "$needle" "$OUT/dex-strings.txt" && fail "the release APK contains $needle"
done
unzip -l "$APK" | grep -qE "assets/(fixtures|uitest)/" && fail "the release APK carries the uitest assets"
echo "badging ok: $(grep -m1 -o "versionName='[^']*'" "$OUT/badging.txt"), minify check: $(grep -c . "$OUT/dex-strings.txt") dex strings"

# --- 2. on the emulator, network off ---------------------------------------------------------------------------------------
adb install -r -g "$APK" || { fail "the release APK did not install"; exit 1; }
ui_prep_emulator
adb shell locksettings set-disabled true >/dev/null 2>&1 || true
adb shell svc power stayon true >/dev/null 2>&1 || true
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb emu geo fix 34.8013 31.2622 >/dev/null
adb shell cmd connectivity airplane-mode enable >/dev/null 2>&1 || adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable >/dev/null 2>&1 || true
adb shell svc data disable >/dev/null 2>&1 || true
sleep 5
nets=$(adb shell dumpsys connectivity | tr -d '\r' | grep -cE "NetworkAgentInfo.*CONNECTED")
echo "networks connected while 'off': $nets"
[[ $nets -eq 0 ]] || fail "the emulator still has a connected network ($nets): the smoke would not prove 'no request'"
adb logcat -G 16M >/dev/null 2>&1 || true
adb logcat -c

# Taps by the text or description on screen (uiautomator), because the release APK has no test hooks.
tap_text() { # $1 = exact text or content-desc; returns 1 when it is not on screen
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local xy
  xy=$(adb exec-out cat /sdcard/ui.xml | python3 -I -c '
import re, sys
want = sys.argv[1]
xml = sys.stdin.read()
for m in re.finditer(r"<node [^>]*>", xml):
    n = m.group(0)
    t = re.search(r" text=\"([^\"]*)\"", n); d = re.search(r"content-desc=\"([^\"]*)\"", n)
    if want in ((t.group(1) if t else ""), (d.group(1) if d else "")):
        b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n)
        x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
' "$1")
  [[ -n $xy ]] || return 1
  adb shell input tap $xy
}
on_screen() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb exec-out cat /sdcard/ui.xml | grep -q "$1"; }

echo "::group::launch, tour, Settings"
adb shell am start -W -n "$ACT" > "$OUT/first-start.txt"
sleep 6
adb exec-out screencap -p > "$OUT/01-launch.png"
if tap_text "Skip"; then echo "tour skipped"; else echo "no tour on screen (already seen?)"; fi
sleep 2
tap_text "More options" || fail "no 'More options' button on the first screen"
sleep 1
tap_text "Settings" || fail "no 'Settings' item in the menu"
sleep 2
adb exec-out screencap -p > "$OUT/02-settings.png"
on_screen "Max transfers" && echo "Settings is open" || fail "Settings did not open (no 'Max transfers' on screen)"
adb shell input keyevent KEYCODE_BACK
echo "::endgroup::"

echo "::group::monkey (500 events, network off)"
adb shell monkey -p "$PKG" --pct-syskeys 0 --ignore-crashes --ignore-timeouts --throttle 150 -s 4242 -v 500 > "$OUT/monkey.txt" 2>&1
tail -4 "$OUT/monkey.txt"
ui_monkey_verdict "$OUT/monkey.txt" || { fail "the monkey crashed or hung the release app"; ui_monkey_evidence "$OUT/monkey.txt"; }
grep -q "Events injected: 500" "$OUT/monkey.txt" || fail "the monkey stopped early: $(tail -1 "$OUT/monkey.txt")"
echo "::endgroup::"
adb logcat -d -b crash >> "$OUT/crash.txt"
grep -q "Process: $PKG" "$OUT/crash.txt" && fail "the release app crashed (crash.txt)"
adb logcat -d | grep -q "ANR in $PKG" && fail "ANR in the release app"
nets=$(adb shell dumpsys connectivity | tr -d '\r' | grep -cE "NetworkAgentInfo.*CONNECTED")
[[ $nets -eq 0 ]] || fail "a network came up during the smoke ($nets); the monkey may have toggled it"

# --- 3. startup time ------------------------------------------------------------------------------------------------------
# The same code measured 631 ms on one CI runner and 930 ms on another (first two runs, T8): runner speed alone is a 47 % swing, more than
# the 25 % the gate allows. So every run also times the phone's own Settings app cold, ten times, and the gate compares the RATIO
# (app / Settings) with the baseline's ratio. Without `reference_ms` in the baseline it falls back to the absolute median.
cold_starts() { # $1 = component, $2 = out file
  : > "$2"
  for i in $(seq 1 10); do
    adb shell am force-stop "$PKG"; adb shell am force-stop com.android.settings; sleep 2
    adb shell am start -W -n "$1" | tr -d '\r' | sed -n 's/^TotalTime: *//p' | tee -a "$2"
    sleep 3
  done
}
echo "::group::cold start x10 (the app)"
cold_starts "$ACT" "$OUT/startup.txt"
echo "::endgroup::"
echo "::group::cold start x10 (reference: Settings)"
cold_starts com.android.settings/.Settings "$OUT/startup-reference.txt"
adb shell am force-stop com.android.settings
echo "::endgroup::"
verdict=$(python3 -I - "$OUT/startup.txt" "$OUT/startup-reference.txt" "$ROOT/startup-baseline.json" <<'PY'
import json, statistics, sys
def load(p): return [int(l) for l in open(p) if l.strip().isdigit()]
times, ref = load(sys.argv[1]), load(sys.argv[2])
base = json.load(open(sys.argv[3]))
if len(times) < 10:
    print(f"FAIL only {len(times)} of 10 cold starts were measured"); sys.exit()
med = int(statistics.median(times))
rmed = int(statistics.median(ref)) if len(ref) >= 5 else 0
tol = base.get("tolerance", 0.25)
b, bref = base.get("median_ms"), base.get("reference_ms")
line = f"cold start median {med} ms (min {min(times)}, max {max(times)}); Settings {rmed} ms; ratio {med / rmed:.3f}" if rmed else f"cold start median {med} ms"
if b and bref and rmed:
    limit = b / bref * (1 + tol)
    bad = med / rmed > limit
    line += f"; baseline ratio {b / bref:.3f} ({b}/{bref}), limit {limit:.3f}"
elif b:
    bad = med > b * (1 + tol)
    line += f"; baseline {b} ms, limit {int(b * (1 + tol))} ms (absolute: no reference_ms yet)"
else:
    bad = False
    line += "; no baseline yet"
print(("FAIL " if bad else "OK ") + line)
PY
)
echo "$verdict"
{ echo "### Release APK smoke"; echo "$(grep -m1 "^package:" "$OUT/badging.txt")"; echo; echo "Startup: \`$verdict\`"; echo "TotalTime x10: $(tr '\n' ' ' < "$OUT/startup.txt")"; echo "Settings x10: $(tr '\n' ' ' < "$OUT/startup-reference.txt")"; } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
[[ $verdict == OK* ]] || fail "$verdict"

adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true
adb logcat -d > "$OUT/logcat.txt" || true
if [[ $failed == 1 ]]; then echo "::group::logcat tail"; tail -300 "$OUT/logcat.txt"; echo "::endgroup::"; fi
exit $failed
