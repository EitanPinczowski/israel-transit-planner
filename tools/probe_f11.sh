#!/usr/bin/env bash
# F11 scratch probe (bug bash A4): the real UI against the live services, under the Pixel's conditions.
set -x
PKG=il.transit.planner.uitest
RUN="il.transit.planner.uitest.test/androidx.test.runner.AndroidJUnitRunner"
probe() { # cond ime slowMs
  adb shell am instrument -w -r -e class il.transit.planner.uitest.SearchUiProbeTest -e cond "$1" -e ime "$2" -e slowMs "$3" $RUN | tail -5
}
adb shell am instrument -w -r -e class il.transit.planner.uitest.SearchProbeTest $RUN | tail -3
adb shell cmd location set-location-enabled false
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell cmd locale set-app-locales $PKG --locales en-US
probe "sysEN-appEN-locOff-permOK" wait 0
probe "sysEN-appEN-locOff-permOK" now 0
probe "sysEN-appEN-locOff-permOK-slow" wait 6000
probe "sysEN-appEN-locOff-permOK-slow" now 6000
adb shell cmd location set-location-enabled true
probe "sysEN-appEN-locOn-permOK" now 0
adb shell cmd location set-location-enabled false
adb shell cmd locale set-app-locales $PKG --locales he-IL
probe "sysEN-appHE-locOff-permOK" wait 0
adb shell cmd locale set-app-locales $PKG --locales en-US
adb shell pm revoke $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm revoke $PKG android.permission.ACCESS_COARSE_LOCATION
probe "sysEN-appEN-locOff-permNever" wait 0
adb shell pm set-permission-flags $PKG android.permission.ACCESS_FINE_LOCATION user-set user-fixed
adb shell pm set-permission-flags $PKG android.permission.ACCESS_COARSE_LOCATION user-set user-fixed
probe "sysEN-appEN-locOff-permDenied" now 0
adb shell pm clear-permission-flags $PKG android.permission.ACCESS_FINE_LOCATION user-set user-fixed
adb shell pm clear-permission-flags $PKG android.permission.ACCESS_COARSE_LOCATION user-set user-fixed
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
# The Pixel: the phone in Hebrew, the app in English.
adb root; sleep 3
adb shell setprop persist.sys.locale he-IL
adb shell "stop; sleep 2; start"
sleep 10; adb wait-for-device
for i in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 2; done
sleep 30
adb shell cmd location set-location-enabled false
adb shell cmd locale set-app-locales $PKG --locales en-US
probe "sysHE-appEN-locOff-permOK" wait 0
probe "sysHE-appEN-locOff-permOK" now 0
probe "sysHE-appEN-locOff-permOK-slow" now 6000
adb shell cmd locale set-app-locales $PKG --locales ""
probe "sysHE-appFollows-locOff-permOK" wait 0
adb pull /sdcard/Android/data/$PKG/files/address_corpus.md address_corpus.md || true
echo "===== address corpus =====" && cat address_corpus.md || true
