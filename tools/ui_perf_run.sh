#!/usr/bin/env bash
# Phase 11 T8, the nightly performance REPORTS (nightly-ui.yml, job `perf`). Numbers in the job summary; no gate yet
# (a loose one after two weeks of numbers; the trend table is docs/testing/perf.md, updated by the boss at close-out).
#   tools/ui_perf_run.sh jank <api> <apk dir> <out dir>   a scripted pan / zoom over the route drawn on the map, `dumpsys gfxinfo`:
#                                                         janky-frame % and the 90th-percentile frame time
#   tools/ui_perf_run.sh ride <api> <apk dir> <out dir>   a 30-minute simulated ride: a recorded-style GPX (core/src/test/resources/tracks)
#                                                         fed every second with `adb emu geo fix`; `dumpsys batterystats --reset` before and
#                                                         `--checkin` after: wake locks, wakeups, GPS-on time, alarms of the app
# The emulator is software-rendered and shares a CI core: compare a number with its own earlier numbers, never with a phone's.
set -u
MODE=$1
API=$2
APKS=$3
OUT=$4
PKG=il.transit.planner.uitest
ACT=$PKG/il.transit.planner.MainActivity
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$OUT"
failed=0
# shellcheck source=ui_instrument.sh
source "$(dirname "$0")/ui_instrument.sh"
fail() { echo "::error::$*"; failed=1; }
summary() { echo "$*" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"; }

adb install -r -g "$APKS"/app-uitest.apk
ui_prep_emulator
adb shell locksettings set-disabled true >/dev/null 2>&1 || true
adb shell svc power stayon true >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb emu geo fix 34.8013 31.2622 >/dev/null
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb logcat -c

ready() { # $1 = kind: wait for the probe's "ready" line
  for _ in $(seq 1 40); do
    adb logcat -d -s PerfProbe:I PerfProbe:E | tr -d '\r' | grep -q "ready kind=$1" && return 0; sleep 1
  done
  adb logcat -d -s PerfProbe:I PerfProbe:E | tr -d '\r' | tail -3
  return 1
}

if [[ $MODE == jank ]]; then
  adb shell am start -S -W -n "$ACT" --es uitest_perf map >/dev/null
  ready map || { fail "the route was not drawn (PerfProbe never said ready)"; exit 1; }
  sleep 4
  size=$(adb shell wm size | tr -d '\r' | grep -o '[0-9]*x[0-9]*' | tail -1)
  w=${size%x*}; h=${size#*x}
  x1=$((w / 4)); x2=$((w * 3 / 4)); y1=$((h / 8)); y2=$((h / 3))
  adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
  # Pan in four directions, double-tap to zoom in then out, six rounds: ~1 minute of movement over Tel Aviv.
  for round in 1 2 3 4 5 6; do
    adb shell "input swipe $x1 $y2 $x2 $y1 350; input swipe $x2 $y1 $x1 $y2 350; input swipe $x1 $y1 $x2 $y2 350; input swipe $x2 $y2 $x1 $y1 350; \
      input tap $x2 $y1; input tap $x2 $y1; sleep 1; input swipe $x1 $y2 $x2 $y1 250; input swipe $x2 $y1 $x1 $y2 250; sleep 1"
  done
  adb shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt"
  adb shell dumpsys gfxinfo "$PKG" framestats > "$OUT/framestats.txt"
  grep -E "Total frames rendered|Janky frames|50th|90th|95th|99th|Number (Missed|High input|Slow)" "$OUT/gfxinfo.txt" | tee "$OUT/jank.txt"
  frames=$(sed -n 's/^Total frames rendered: *//p' "$OUT/gfxinfo.txt" | head -1)
  [[ ${frames:-0} -ge 100 ]] || fail "only ${frames:-0} frames were rendered: the pan / zoom did not move the map"
  summary "### Map jank (API $API, uitest build, software GL)"
  summary '```'; summary "$(cat "$OUT/jank.txt")"; summary '```'
  adb logcat -d -b crash | grep -q "Process: $PKG" && fail "the app crashed during the pan / zoom"
  exit $failed
fi

# --- ride --------------------------------------------------------------------------------------------------------------------
python3 -I - "$ROOT/core/src/test/resources/tracks/train_clean.gpx" "$OUT/fixes.txt" <<'PY'
import re, sys
pts = [tuple(map(float, m)) for m in re.findall(r'<trkpt lat="([\d.]+)" lon="([\d.]+)"', open(sys.argv[1]).read())]
out = []
for (la1, lo1), (la2, lo2) in zip(pts, pts[1:]):
    for k in range(10):  # the fixtures are one fix per 10 s; the phone gets one per second
        f = k / 10
        out.append(f"{lo1 + (lo2 - lo1) * f:.6f} {la1 + (la2 - la1) * f:.6f}")
open(sys.argv[2], "w").write("\n".join(out[:1800]) + "\n")
print(len(out[:1800]), "fixes")
PY
first=$(head -1 "$OUT/fixes.txt")
adb emu geo fix $first >/dev/null
adb shell am start -S -W -n "$ACT" --es uitest_perf ride >/dev/null
ready ride || { fail "the ride did not start (PerfProbe never said ready)"; exit 1; }
adb shell dumpsys batterystats --enable full-wake-history >/dev/null 2>&1 || true
adb shell dumpsys batterystats --reset >/dev/null
adb shell dumpsys alarm > "$OUT/alarm-before.txt"
start=$(date +%s)
while read -r fix; do
  adb emu geo fix $fix >/dev/null
  sleep 0.7
done < "$OUT/fixes.txt"
secs=$(( $(date +%s) - start ))
echo "fed $(wc -l < "$OUT/fixes.txt") fixes in ${secs}s"
adb shell dumpsys batterystats --checkin > "$OUT/batterystats-checkin.txt"
adb shell dumpsys batterystats "$PKG" > "$OUT/batterystats-app.txt"
adb shell dumpsys alarm > "$OUT/alarm-after.txt"
uid=$(adb shell dumpsys package "$PKG" | tr -d '\r' | sed -n 's/.*userId=\([0-9]*\).*/\1/p' | head -1)
echo "uid $uid"
grep -E "^9,$uid," "$OUT/batterystats-checkin.txt" | cut -c1-220 > "$OUT/app-checkin.txt"
{
  echo "### 30-minute simulated ride (API $API, ${secs}s of wall time for $(wc -l < "$OUT/fixes.txt") fixes)"
  echo "RideService alive at the end: $(adb shell dumpsys activity services "$PKG" | grep -c RideService) record(s)"
  echo '```'
  echo "# app uid $uid, batterystats --checkin lines (wl = wake locks, gps/gpsd = GPS, jb = jobs, sst/ssr = sensors)"
  head -40 "$OUT/app-checkin.txt"
  echo "# human-readable per-app block"
  grep -iE "wake ?lock|wakeup|gps|alarm|sensor|foreground|Computed drain|Total cpu" "$OUT/batterystats-app.txt" | head -30
  echo "# alarms of the app (dumpsys alarm)"
  grep -c "$PKG" "$OUT/alarm-after.txt" | sed 's/^/entries mentioning the app: /'
  echo '```'
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
adb logcat -d -b crash | grep -q "Process: $PKG" && fail "the app crashed during the simulated ride"
exit $failed
