#!/usr/bin/env bash
# Phase 11 T7: the flake log. Posts one comment on the open issue "CI flake log" (created once)
# for every test retry that tools/ui_instrument.sh recorded (flake.log files), and for a job
# that is a re-run (GITHUB_RUN_ATTEMPT > 1). A test that flakes twice in 14 days gets a fix
# package, never a skip (docs/agents/phase11.md). Called as the last step of a UI job:
#   tools/ui_flake_log.sh <dir with the job's output> <job name>
# Needs GH_TOKEN and `issues: write`; never fails the job.
set -u
DIR=$1
JOB=${2:-job}
RUN="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-0}"
lines=""
while IFS= read -r f; do
  while IFS=$'\t' read -r word api test kind; do
    lines+="- $word: \`$test\` on API $api. $kind"$'\n'
  done < "$f"
done < <(find "$DIR" -name flake.log 2>/dev/null)
if [[ ${GITHUB_RUN_ATTEMPT:-1} -gt 1 ]]; then
  lines+="- job re-run: attempt ${GITHUB_RUN_ATTEMPT} of \`$JOB\`"$'\n'
fi
[[ -n $lines ]] || exit 0
body="**$JOB**, [run ${GITHUB_RUN_ID:-?}]($RUN), $(date -u +%Y-%m-%dT%H:%MZ)"$'\n\n'"$lines"
issue=$(gh issue list --state open --search '"CI flake log" in:title' --json number,title --jq '[.[] | select(.title == "CI flake log")][0].number' 2>/dev/null)
if [[ -z $issue || $issue == null ]]; then
  gh issue create --title "CI flake log" --body "Every test retry and job re-run in CI is commented here by tools/ui_flake_log.sh. A test that flakes twice in 14 days gets a fix package, never a skip." >/dev/null 2>&1 || true
  issue=$(gh issue list --state open --search '"CI flake log" in:title' --json number,title --jq '[.[] | select(.title == "CI flake log")][0].number' 2>/dev/null)
fi
if [[ -n $issue && $issue != null ]]; then
  gh issue comment "$issue" --body "$body" >/dev/null 2>&1 || echo "::warning::could not post to the flake log"
else
  echo "::warning::no flake log issue"
fi
echo "$body"
exit 0
