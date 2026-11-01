#!/usr/bin/env bash
# Phase 11 T8, the upgrade test (upgrade.yml): the latest release's uitest APK is installed and given its saved data (T1's sample for
# that version, written into its DataStore with run-as), launched so it arms its night refresh and leave reminder, then HEAD's uitest APK
# goes over it with `adb install -r`. UpgradeProbe logs what the new build reads; tools/upgrade_data.py compares it with what was written.
# Any loss fails. The alarm and the channels are read here. 0 requests (the uitest build answers from the replay).
#   tools/ui_upgrade_run.sh <old apk> <new apk> <sample json> <out dir>
set -u
OLD=$1
NEW=$2
SAMPLE=$3
OUT=$4
PKG=il.transit.planner.uitest
ACT=$PKG/il.transit.planner.MainActivity
mkdir -p "$OUT/data"
failed=0
# shellcheck source=ui_instrument.sh
source "$(dirname "$0")/ui_instrument.sh"
fail() { echo "::error::$*"; failed=1; }
alarms() { adb shell dumpsys alarm | tr -d '\r' | grep -c "il.transit.planner.action.LEAVE"; }

adb root >/dev/null 2>&1; adb wait-for-device
adb install -r -g "$OLD" || { fail "the old APK did not install"; exit 1; }
ui_prep_emulator
adb shell locksettings set-disabled true >/dev/null 2>&1 || true
adb shell svc power stayon true >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
echo "old: $(adb shell dumpsys package $PKG | tr -d '\r' | grep -m1 versionName), codes: $(adb shell dumpsys package $PKG | tr -d '\r' | grep -m1 versionCode)"

# 1. The old build creates its files, then stops; we write its saved data while it is not running.
adb shell am start -W -n "$ACT" >/dev/null; sleep 6
adb shell am force-stop "$PKG"
now=$(adb shell date +%s | tr -d '\r')
python3 "$(dirname "$0")/upgrade_data.py" write "$SAMPLE" "$OUT/data" "$now" || { fail "could not build the saved data"; exit 1; }
adb push "$OUT/data" /data/local/tmp/upgrade-data >/dev/null
adb shell "run-as $PKG sh -c 'mkdir -p files/datastore'"
adb shell "cat /data/local/tmp/upgrade-data/datastore/user.preferences_pb | run-as $PKG sh -c 'cat > files/datastore/user.preferences_pb'"
adb shell "cat /data/local/tmp/upgrade-data/history.json | run-as $PKG sh -c 'cat > files/history.json'"
adb shell "run-as $PKG ls -l files files/datastore" | tr -d '\r'

# 2. The old build reads it: the night refresh is enqueued at start; the boot receiver arms the reminder (a root shell may send the protected action).
adb shell am start -W -n "$ACT" >/dev/null; sleep 8
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n "$PKG/il.transit.planner.remind.BootReceiver" >/dev/null
sleep 5
before=$(alarms)
jobs_before=$(adb shell dumpsys jobscheduler | tr -d '\r' | grep -c "$PKG")
echo "old build: LEAVE alarms=$before, scheduler entries=$jobs_before"
adb shell dumpsys notification --noredact | tr -d '\r' | grep -o "NotificationChannel{mId='[^']*'" | sort -u | sed 's/^/  channel before: /' | tee "$OUT/channels-before.txt"
[[ $before -ge 1 ]] || fail "the old build did not arm the leave reminder (setup problem, not an upgrade loss)"
adb shell am force-stop "$PKG"

# 3. The upgrade: the same install a friend does.
adb install -r -g "$NEW" || fail "adb install -r of the new APK failed"
echo "new: $(adb shell dumpsys package $PKG | tr -d '\r' | grep -m1 versionName)"
# MY_PACKAGE_REPLACED re-arms the reminder without anyone opening the app.
after=0
for _ in $(seq 1 15); do
  after=$(alarms); [[ $after -ge 1 ]] && break; sleep 2
done
echo "after the upgrade, before opening the app: LEAVE alarms=$after"
[[ $after -ge 1 ]] || fail "the leave reminder was not re-armed after the upgrade (MY_PACKAGE_REPLACED)"

# 4. The new build reads the old build's files.
adb logcat -c
adb shell am start -W -n "$ACT" --ez uitest_upgrade_probe true >/dev/null
for _ in $(seq 1 30); do
  adb logcat -d -s UpgradeProbe:I | grep -q "done=1" && break; sleep 1
done
adb logcat -d -s UpgradeProbe:I | tr -d '\r' > "$OUT/probe.log"
python3 "$(dirname "$0")/upgrade_data.py" compare "$OUT/data/expected.json" "$OUT/probe.log" || failed=1
# Channels: every channel the old build had must still exist.
adb shell dumpsys notification --noredact | tr -d '\r' | grep -o "NotificationChannel{mId='[^']*'" | sort -u > "$OUT/channels-after.txt"
lost=$(comm -23 "$OUT/channels-before.txt" <(sed 's/^/  channel before: /' "$OUT/channels-after.txt") | wc -l)
[[ $lost -eq 0 ]] || fail "$lost notification channel(s) gone after the upgrade"
{
  echo "### Upgrade test: $(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['version'])" "$OUT/data/expected.json") -> HEAD"
  echo "LEAVE alarms: before the upgrade $before, after $after; channels lost: $lost"
  echo '```'; cat "$OUT/probe.log" | cut -c1-200; echo '```'
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

adb logcat -d > "$OUT/logcat.txt" || true
adb logcat -d -b crash > "$OUT/crash.txt" || true
grep -q "Process: $PKG" "$OUT/crash.txt" && fail "the app crashed after the upgrade (crash.txt)"
if [[ $failed == 1 ]]; then echo "::group::logcat tail"; tail -200 "$OUT/logcat.txt"; echo "::endgroup::"; fi
exit $failed
