#!/usr/bin/env bash
set -euo pipefail

bash gradlew :app:assembleX86_64Debug :app:assembleX86_64DebugAndroidTest --stacktrace
app_apk=$(find app/build/outputs/apk/x86_64/debug -name '*.apk' -print -quit)
test_apk=$(find app/build/outputs/apk/androidTest/x86_64/debug -name '*.apk' -print -quit)
test -n "$app_apk" && test -n "$test_apk"
adb install -r -t "$app_apk"
adb install -r -t "$test_apk"
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell am instrument -w -e package tv.own.owntv.features.simulation \
    com.oakstudios.movioxp.test/androidx.test.runner.AndroidJUnitRunner | tee cinema-instrumentation.txt

mkdir -p cinema-visual
for shot in $(adb shell run-as com.oakstudios.movioxp ls files/cinema-visual 2>/dev/null || true); do
    adb exec-out run-as com.oakstudios.movioxp cat "files/cinema-visual/$shot" > "cinema-visual/$shot"
done
grep -F 'OK (2 tests)' cinema-instrumentation.txt
adb logcat -d -v raw | rg "HOME_TIMING" || true
for expected in 01-cinema-home 02-explore-more 03-action-category 04-exit-confirmation 05-simulator-remote; do
    test -s "cinema-visual/$expected.jpg"
done
