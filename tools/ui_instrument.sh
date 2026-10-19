#!/usr/bin/env bash
# Shared by ui_layout_run.sh and ui_behaviour_run.sh (source it).
#
#   ui_prep_emulator            quiet the emulator: no ANR/crash dialogs, no package verifier
#   ui_instrument CLASSES OUT_FILE [-e k v ...]
#                               runs `am instrument` and returns 0 only if every test passed
#
# Why: the instrumentation process sometimes dies ("Process crashed") with no Java FATAL in
# logcat (see .claude/skills/ui-testing, "Flaky instrumentation deaths"). One kind is
# retried ONCE PER TEST: a process killed with nothing in the crash buffer. Any crash of the
# app (FATAL EXCEPTION, native signal such as a MapLibre abort, ANR) is red at once. The
# retry runs only the test that was open and the tests that never started; a test that dies
# twice and any failed assertion stay red. Every retry is a ::warning:: and a block in the job
# summary naming its kind, and the evidence is printed before the retry.
#
# Phase 11 T7: every retry is also a line "retry<TAB>API<TAB>test<TAB>kind" in flake.log next to
# the output file; the workflow's last step (tools/ui_flake_log.sh) posts them on the "CI flake log" issue.

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
  echo "--- main: the last MapLibre / exception lines (what a native abort was about)"
  # System.err carries the Java exception a native thread was holding when it aborted.
  adb logcat -d -b main | grep -iE "maplibre|mbgl|PendingJava|Exception|System\.err" | tail -60
  echo "--- main/system: kills, memory, ANR, native"
  adb logcat -d -b main -b system | grep -iE "lowmemorykiller|lmkd|Killing|ANR in|has died|Fatal signal|tombstone|watchdog|OutOfMemory|Low on memory|crash" | tail -60
  echo "--- memory"
  adb shell 'grep -E "MemTotal|MemFree|MemAvailable|SwapFree" /proc/meminfo' | tr -d '\r'
  adb shell dumpsys meminfo 2>/dev/null | head -25 | tr -d '\r'
  adb shell df /data | tr -d '\r'
  adb shell dmesg 2>/dev/null | tail -20 | tr -d '\r'
  echo "::endgroup::"
}

# The app (same process as the test) died for its own reasons: Java crash, ANR, or a native crash.
_ui_real_app_crash() {
  adb logcat -d -b crash | grep -qE "FATAL EXCEPTION|Process: $PKG|Fatal signal|ANR in $PKG"
}

_ui_flake() { # $1 = outfile, $2 = test, $3 = kind
  printf 'retry\t%s\t%s\t%s\n' "${API:-?}" "$2" "$3" >> "$(dirname "$1")/flake.log"
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
    local kind="the emulator killed the instrumentation process (no crash in logcat)"
    if _ui_real_app_crash; then
      echo "::error::the app itself crashed during $open_now (see the crash buffer above): no retry"
      rc=1; break
    fi
    if [[ " $retried " == *" $open_now "* ]]; then
      echo "::error::$open_now died twice with no app crash: red"
      rc=1; break
    fi
    retried="$retried $open_now"
    _ui_flake "$outfile" "$open_now" "$kind"
    echo "::warning::RETRY (once): $kind, during $open_now. Re-running it with the tests that had not started."
    {
      echo "### ⚠️ Retry — API ${API:-?} — $(basename "$outfile")"
      echo "During \`$open_now\`: $kind."
      echo "That test and the ones after it were run once more; a second death or a failed assertion is red."
      echo
    } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
    sed -i 's/Process crashed/died-and-retried (see job summary)/' "$outfile"
    [[ -n $done_now ]] && notclass="${notclass:+$notclass,}$done_now"
    round=$((round + 1))
    sleep 5
  done
  # Retried tests that then passed stay green, but are named in the summary above.
  return $rc
}
