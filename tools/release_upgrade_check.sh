#!/usr/bin/env bash
# Phase 11 T8, release.yml: the newly signed APK must install OVER the previous release's published one (`adb install -r`), which is what
# fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE when the key changed, or INSTALL_FAILED_VERSION_DOWNGRADE when the version code went down.
#   tools/release_upgrade_check.sh <previous apk> <new apk>
set -eu
OLD=$1
NEW=$2
PKG=il.transit.planner
adb install -r "$OLD"
echo "previous: $(adb shell dumpsys package $PKG | tr -d '\r' | grep -m1 versionName)"
adb install -r "$NEW"
echo "now:      $(adb shell dumpsys package $PKG | tr -d '\r' | grep -m1 versionName)"
adb shell pm list packages | grep -q "package:$PKG$"
echo "upgrade install ok"
