#!/usr/bin/env bash
# Phase 11 (follow-up to T8): tools/ui_flake_log.sh must log a failed test, and must NOT log anything for a cancelled job.
# Dry run (FLAKE_DRY_RUN=1: prints the comment instead of posting it). Run by ci.yml, no network, no emulator.
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
printf 'failed\t35\tclass#s17_update_banner_with_results\tfailed (assertion or error)\n' > "$tmp/flake.log"

export FLAKE_DRY_RUN=1 GITHUB_REPOSITORY=o/r GITHUB_RUN_ID=1
out=$(JOB_STATUS=failure bash "$here/ui_flake_log.sh" "$tmp" "screens shard 1")
grep -q 's17_update_banner_with_results' <<<"$out" || { echo "FAIL: a failed job's test was not logged: $out"; exit 1; }

out=$(JOB_STATUS=cancelled bash "$here/ui_flake_log.sh" "$tmp" "screens shard 1")
if grep -q 's17_update_banner_with_results' <<<"$out"; then echo "FAIL: a cancelled job was logged: $out"; exit 1; fi
grep -q 'cancelled' <<<"$out" || { echo "FAIL: no cancelled notice: $out"; exit 1; }

out=$(JOB_STATUS=success GITHUB_RUN_ATTEMPT=2 bash "$here/ui_flake_log.sh" "$tmp/none" "job")
grep -q 'job re-run: attempt 2' <<<"$out" || { echo "FAIL: a re-run was not logged: $out"; exit 1; }
echo "flake log: failed logged, cancelled skipped, re-run logged"
