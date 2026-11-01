#!/usr/bin/env bash
# Phase 11 T6, in the `faults` job after the fault matrix (one line in ui_faults_run.sh):
#   1. IntentFuzzTest, FileProviderTest, UpdateFlowTest (FakeReleaseServer on the emulator's loopback);
#   2. the same hostile intents from `adb shell am`, the spoofed boot broadcast, the tile, the provider;
#   3. on API 35, the in-app update end to end: this uitest APK as the previous version, the next one
#      (versionCode + 1, built and signed with the same key by the job) installed through the banner,
#      Android's installer and a tap on its button; the new version must run with the old data.
# Nothing reaches the internet. Usage (ui_faults_run.sh): tools/ui_intent_fuzz.sh <api> <apk dir> <out dir>
set -u
API=$1
APKS=$2
OUT=$3
PKG=il.transit.planner.uitest
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
ACT=$PKG/il.transit.planner.MainActivity
T=il.transit.planner.uitest
NEXT=$APKS/app-uitest-next.apk
mkdir -p "$OUT"
failed=0
# shellcheck source=ui_instrument.sh
source "$(dirname "$0")/ui_instrument.sh"
fail() { echo "::error::$*"; failed=1; }
note() { echo "$*" | tee -a "$OUT/security.txt"; }
: > "$OUT/security.txt"
alive() { adb shell pidof "$PKG" >/dev/null 2>&1; }
# The crash buffer is the job's evidence (ui_faults_run.sh reads it at the end): count, never clear.
crashes() { adb logcat -d -b crash | grep -cE "Process: $PKG|ANR in $PKG"; }
base=$(crashes)
crashed() { [[ $(crashes) -gt $base ]]; }

echo "::group::security + update tests"
ui_instrument "$T.IntentFuzzTest,$T.FileProviderTest,$T.UpdateFlowTest" "$OUT/instrument-security.txt" \
  -e profile "api$API" -e locale device -e theme light || fail "the security / update tests failed on API $API"
adb logcat -d -s IntentFuzz:I | tr -d '\r' | grep -- " -> " | sed 's/^.*IntentFuzz: //' >> "$OUT/security.txt"
echo "::endgroup::"
base=$(crashes)

# --- 2. From the shell: what any other app could send. -------------------------------------------
echo "::group::intents from adb shell"
adb shell am force-stop "$PKG"
shell_case() { # $1 = label, rest = am arguments
  local label=$1; shift
  local out
  out=$(adb shell am "$@" 2>&1 | tr -d '\r' | tr '\n' ' ')
  sleep 2
  if crashed; then fail "shell: $label crashed the app"; adb logcat -d -b crash | tail -40; base=$(crashes); fi
  note "shell: $label -> ${out:0:160}"
}
big=$(head -c 60000 /dev/zero | tr '\0' 'a') # the shell's argument limit is ~128 KB
shell_case "SEND no extras" start -W -n "$ACT" -a android.intent.action.SEND -t text/plain
shell_case "SEND 60 KB" start -W -n "$ACT" -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "$big"
shell_case "SEND wrong type" start -W -n "$ACT" -a android.intent.action.SEND -t image/png --ei android.intent.extra.TEXT 7
for uri in 'geo:NaN,NaN' 'geo:999,999' "geo:0,0?q=%27%3B%20DROP%20TABLE%20places%3B--" 'geo:0,0?q=%3Cscript%3Ealert(1)%3C/script%3E' \
  'geo:0,0?q=32.08,34.79(%E2%80%AEevil)' 'javascript:alert(1)' "file:///data/data/$PKG/files/crash_log.txt" \
  'https://maps.google.com.evil.example/maps?q=32.0839,34.7983'; do
  # Quoted for the phone's shell too: "(" and ";" are its syntax.
  shell_case "VIEW $uri" start -W -n "$ACT" -a android.intent.action.VIEW -d "'$uri'"
done
# Implicit: the look-alike host must not even resolve to us (our filters name the exact hosts).
res=$(adb shell cmd package query-activities -a android.intent.action.VIEW -c android.intent.category.BROWSABLE \
  -d 'https://maps.google.com.evil.example/maps' 2>&1 | tr -d '\r')
if grep -q "$PKG" <<< "$res"; then fail "a look-alike map host resolves to the app"; else note "shell: look-alike host, implicit -> not ours"; fi
shell_case "VIEW content:// + grant" start -W -n "$ACT" -a android.intent.action.VIEW --grant-read-uri-permission \
  -d content://com.android.externalstorage.documents/document/primary%3ADownload%2Fsecret.txt
shell_case "missed it at 999,999" start -W -n "$ACT" -a missed_it --ez missed_it true --ed missed_it_lat 999 --ed missed_it_lon 999
shell_case "missed it, NaN" start -W -n "$ACT" -a missed_it --ez missed_it true --es missed_it_lat NaN
alive || fail "the app is not running after the shell intents"

# The spoofed boot: BOOT_COMPLETED is a protected broadcast. The shell is refused; if it ever got through,
# the receiver may only re-arm (no Activity, no ride, no notification of its own).
notes_before=$(adb shell dumpsys notification --noredact 2>/dev/null | grep -c "pkg=$PKG")
for action in android.intent.action.BOOT_COMPLETED android.intent.action.MY_PACKAGE_REPLACED android.intent.action.TIMEZONE_CHANGED il.example.EVIL; do
  shell_case "broadcast $action" broadcast -a "$action" -n "$PKG/il.transit.planner.remind.BootReceiver"
done
sleep 3
notes_after=$(adb shell dumpsys notification --noredact 2>/dev/null | grep -c "pkg=$PKG")
[[ $notes_after -le $notes_before ]] || fail "a spoofed boot broadcast posted a notification ($notes_before -> $notes_after)"
adb shell dumpsys activity services "$PKG" | grep -q "RideService" && fail "a spoofed boot broadcast started the ride service"
top=$(adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | tr -d '\r')
note "shell: after the broadcasts, on top: ${top:0:160}"

# The tile is bound by System UI only (BIND_QUICK_SETTINGS_TILE); the provider is not exported.
shell_case "start the tile service" startservice -n "$PKG/il.transit.planner.tile.NextTripTile"
out=$(adb shell content read --uri "content://$PKG.updates/updates/update.apk" 2>&1 | tr -d '\r')
if grep -qiE "Permission Denial|SecurityException|not exported|No such file|FileNotFound" <<< "$out"; then
  note "shell: provider read without a grant -> refused"
else
  fail "the shell read the update provider without a grant: ${out:0:200}"
fi
crashed && fail "the app crashed during the shell intents"
base=$(crashes)
echo "::endgroup::"

# --- 3. End to end: banner -> download -> Android's installer -> the new version, data kept. -------
if [[ $API == 35 ]]; then
  echo "::group::in-app update end to end"
  if [[ ! -f $NEXT ]]; then
    fail "no next APK at $NEXT (the job builds it)"
  else
    adb push "$NEXT" /data/local/tmp/t6-next.apk >/dev/null
    adb shell chmod 644 /data/local/tmp/t6-next.apk
    before=$(adb shell dumpsys package "$PKG" | grep -m1 -o "versionCode=[0-9]*" | tr -d '\r')
    note "e2e: installed $before"
    if ui_instrument "$T.UpdateInstallSteps#step1_banner_to_installer" "$OUT/instrument-e2e-1.txt" -e profile "api$API"; then
      # Android's "Update this app?" prompt: tap its button (UiAutomator's dump of the screen gives where).
      tapped=0
      for _ in $(seq 1 20); do
        adb shell uiautomator dump /sdcard/t6-ui.xml >/dev/null 2>&1
        xy=$(adb shell cat /sdcard/t6-ui.xml 2>/dev/null | python3 -c '
import re, sys
xml = sys.stdin.read()
for node in re.findall(r"<node [^>]*>", xml):
    pkg = re.search(r"package=\"([^\"]*)\"", node).group(1)
    text = re.search(r"text=\"([^\"]*)\"", node).group(1)
    rid = re.search(r"resource-id=\"([^\"]*)\"", node).group(1)
    if "packageinstaller" in pkg and (rid.endswith("button1") or text.lower() in ("update", "install")):
        x1, y1, x2, y2 = map(int, re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node).groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break')
        if [[ -n $xy ]]; then
          # shellcheck disable=SC2086
          adb shell input tap $xy
          tapped=1
          note "e2e: tapped the installer's button at $xy"
          break
        fi
        sleep 2
      done
      [[ $tapped == 1 ]] || { fail "Android's installer prompt never showed its button"; adb shell cat /sdcard/t6-ui.xml | head -c 4000; }
      after=""
      for _ in $(seq 1 45); do
        after=$(adb shell dumpsys package "$PKG" | grep -m1 -o "versionCode=[0-9]*" | tr -d '\r')
        [[ $after == "versionCode=2" ]] && break
        sleep 2
      done
      note "e2e: after the tap $after"
      if [[ $after == "versionCode=2" ]]; then
        adb shell am start -W -n "$ACT" >/dev/null
        sleep 3
        crashed && fail "the new version crashed on its first start"
        ui_instrument "$T.UpdateInstallSteps#step2_after_install" "$OUT/instrument-e2e-2.txt" -e profile "api$API" \
          || fail "the new version lost data or did not start"
      else
        fail "the update did not install (still $after)"
      fi
    else
      fail "the update never reached Android's installer"
    fi
  fi
  echo "::endgroup::"
fi

{
  echo "### Security + in-app update (API $API)"
  echo '```'
  cat "$OUT/security.txt"
  echo '```'
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
exit $failed
