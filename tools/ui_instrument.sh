#!/usr/bin/env bash
# Shared by ui_layout_run.sh and ui_behaviour_run.sh (source it).
#
#   ui_prep_emulator            quiet the emulator: no ANR/crash dialogs, no package verifier
#   ui_instrument CLASSES OUT_FILE [-e k v ...]
#                               runs `am instrument` and returns 0 only if every test passed
#
# Why: the CI emulator sometimes kills the instrumentation process ("Process crashed") with no
# app crash in logcat (see .claude/skills/ui-testing, "Flaky instrumentation deaths"). A death
# like that is retried ONCE PER TEST, only when the crash buffer holds no sign of the app
# itself dying (FATAL EXCEPTION / native signal / ANR of the app). The retry runs only the
# test that was open and the tests that never started; a test that dies twice, any failed
# assertion, and any real app crash stay red. Every retry is a ::warning:: and a block in the
# job summary, and the evidence (events/system log, memory) is printed before the retry.

ui_prep_emulator() {
  adb shell settings put global verifier_verify_adb_installs 0 >/dev/null 2>&1 || true
  adb shell settings put global package_verifier_enable 0 >/dev/null 2>&1 || true
  adb shell settings put global hidden_api_policy 1 >/dev/null 2>&1 || true
  adb shell settings put global show_first_crash_dialog_dev_option 0 >/dev/null 2>&1 || true
  adb shell settings put secure anr_show_background 0 >/dev/null 2>&1 || true
  adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
  adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
  adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
  echo "::group::emulator baseline"
  adb shell getprop ro.build.fingerprint | tr -d '\r'
  adb shell 'grep -E "MemTotal|MemAvailable|SwapTotal" /proc/meminfo' | tr -d '\r'
  adb shell df /data | tr -d '\r'
  adb shell nproc | tr -d '\r'
  echo "::endgroup::"
}

# Prints "DONE class#test ..." and "OPEN class#test" (started, never finished) for an
# instrumentation output file.
_ui_parse() {
  tr -d '\r' < "$1" | awk '
    /^INSTRUMENTATION_STATUS: class=/ { cls = substr($0, index($0, "=") + 1) }
    /^INSTRUMENTATION_STATUS: test=/  { tst = substr($0, index($0, "=") + 1) }
    /^INSTRUMENTATION_STATUS_CODE:/ {
      code = $2
      if (code == 1) { open = cls "#" tst }
      else if (tst != "") { done = done (done == "" ? "" : ",") cls "#" tst; open = "" }
      tst = ""
    }
    END { print "DONE " done; print "OPEN " open }'
}

_ui_dump_evidence() { # $1 = label
  echo "::group::evidence: $1"
  echo "--- crash buffer"; adb logcat -d -b crash | tail -80
  echo "--- events: process deaths and kills"
  adb logcat -d -b events | grep -E "am_proc_died|am_kill|am_crash|am_anr|am_low_memory|am_drop_process" | tail -40
  echo "--- main/system: kills, memory, ANR, native"
  adb logcat -d -b main -b system | grep -iE "lowmemorykiller|lmkd|Killing|ANR in|has died|Fatal signal|tombstone|watchdog|OutOfMemory|Low on memory|crash" | tail -60
  echo "--- memory"
  adb shell 'grep -E "MemTotal|MemFree|MemAvailable|SwapFree" /proc/meminfo' | tr -d '\r'
  adb shell dumpsys meminfo 2>/dev/null | head -25 | tr -d '\r'
  adb shell df /data | tr -d '\r'
  adb shell dmesg 2>/dev/null | tail -20 | tr -d '\r'
  echo "::endgroup::"
}

_ui_real_app_crash() { # the app (same process as the test) died for its own reasons
  adb logcat -d -b crash | grep -qE "FATAL EXCEPTION|Process: $PKG|Fatal signal|ANR in $PKG"
}

ui_instrument() {
  local classes=$1 outfile=$2; shift 2
  local extras=("$@")
  local notclass="" retried="" round=0 rc=0 attempt parsed done_now open_now
  : > "$outfile"
  while :; do
    attempt="$(dirname "$outfile")/attempt-$round-$(basename "$outfile" .txt).log"
    adb logcat -b crash -c >/dev/null 2>&1 || true
    local args=(-w -r -e class "$classes")
    [[ -n $notclass ]] && args+=(-e notClass "$notclass")
    adb shell am instrument "${args[@]}" "${extras[@]}" "$RUNNER" | tee "$attempt"
    tr -d '\r' < "$attempt" >> "$outfile"
    # Real failures of this attempt: a failed assertion, an error, a refused start.
    if tr -d '\r' < "$attempt" | grep -qE "^INSTRUMENTATION_STATUS_CODE: -[12]$|^INSTRUMENTATION_FAILED"; then rc=1; fi
    if ! tr -d '\r' < "$attempt" | grep -q "Process crashed"; then break; fi

    parsed=$(_ui_parse "$attempt")
    done_now=$(sed -n 's/^DONE //p' <<<"$parsed")
    open_now=$(sed -n 's/^OPEN //p' <<<"$parsed")
    [[ -z $open_now ]] && open_now="(before the first test)"
    _ui_dump_evidence "process died during $open_now"
    if _ui_real_app_crash; then
      echo "::error::the app itself crashed during $open_now (see the crash buffer above): no retry"
      rc=1; break
    fi
    if [[ " $retried " == *" $open_now "* ]]; then
      echo "::error::$open_now died twice with no app crash: red"
      rc=1; break
    fi
    retried="$retried $open_now"
    echo "::warning::INFRASTRUCTURE RETRY: the emulator killed the instrumentation process during $open_now (no app crash in logcat). Re-running it once, with the tests that had not started."
    {
      echo "### ⚠️ Infrastructure retry — API ${API:-?} — $(basename "$outfile")"
      echo "The instrumentation process died during \`$open_now\` and the app did not crash."
      echo "That test and the ones after it were run once more; a second death or a failed assertion is red."
      echo
    } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
    sed -i 's/Process crashed/died-and-retried (infrastructure, see job summary)/' "$outfile"
    [[ -n $done_now ]] && notclass="${notclass:+$notclass,}$done_now"
    round=$((round + 1))
    sleep 5
  done
  # Retried tests that then passed stay green, but are named in the summary above.
  return $rc
}
