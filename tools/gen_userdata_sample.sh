#!/usr/bin/env bash
# Writes core/src/test/resources/fixtures/userdata/<version>.json: the user data that release VERSION's own
# codec (UserJson, History) wrote, for UserDataMigrationTest. Never edit a sample by hand: regenerate it.
#
#   tools/gen_userdata_sample.sh v0.9.0 [v0.8.6 ...]
#
# It checks the tag out into a `git worktree`, copies tools/userdata_gen/UserDataSampleGen.kt in as a test (lines
# marked `//@since X` / `//@until X` are dropped for releases outside that range), runs it with that tag's Gradle
# build, and pretty-prints the result. If a tag does not build in a cloud session, run this script in a CI
# `workflow_dispatch` job instead and commit the output. Every release adds its sample (skill `release-apk`).
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
out_dir="$root/core/src/test/resources/fixtures/userdata"
mkdir -p "$out_dir"
[ $# -gt 0 ] || { echo "usage: $0 vX.Y.Z [...]" >&2; exit 2; }

ver_ge() { [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | head -n1)" = "$2" ]; }  # $1 >= $2

for tag in "$@"; do
  v="${tag#v}"
  tmp="$(mktemp -d)"
  git -C "$root" worktree add --detach "$tmp/wt" "$tag" >/dev/null
  trap 'git -C "$root" worktree remove --force "$tmp/wt" >/dev/null 2>&1 || true' EXIT
  gen="$tmp/wt/core/src/test/kotlin/il/transit/core/UserDataSampleGen.kt"
  : > "$gen"
  while IFS= read -r line; do
    since="$(sed -n 's|.*//@since \([0-9.]*\).*|\1|p' <<<"$line")"
    until_="$(sed -n 's|.*//@until \([0-9.]*\).*|\1|p' <<<"$line")"
    [ -n "$since" ] && ! ver_ge "$v" "$since" && continue
    [ -n "$until_" ] && ! ver_ge "$until_" "$v" && continue
    printf '%s\n' "$line" >> "$gen"
  done < "$root/tools/userdata_gen/UserDataSampleGen.kt"
  raw="$tmp/raw.json"
  USERDATA_VERSION="$v" USERDATA_OUT="$raw" "$tmp/wt/gradlew" -p "$tmp/wt/core" test -q --tests '*UserDataSampleGen*'
  python3 - "$raw" "$out_dir/$v.json" <<'PY'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
with open(sys.argv[2], "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=1)
    f.write("\n")
PY
  git -C "$root" worktree remove --force "$tmp/wt" >/dev/null
  trap - EXIT
  echo "wrote $out_dir/$v.json"
done
