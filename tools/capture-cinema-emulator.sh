#!/usr/bin/env bash
set -euo pipefail

bash gradlew :app:assembleX86_64Debug :app:assembleX86_64DebugAndroidTest --stacktrace
app_apk=$(find app/build/outputs/apk/x86_64/debug -name '*.apk' -print -quit)
test_apk=$(find app/build/outputs/apk/androidTest/x86_64/debug -name '*.apk' -print -quit)
test -n "$app_apk" && test -n "$test_apk"
adb install -r -t "$app_apk"
adb install -r -t "$test_apk"
adb shell settings put secure immersive_mode_confirmations confirmed
# The emulator's launcher can report an unrelated ANR on a busy runner.
adb shell am force-stop com.google.android.apps.nexuslauncher
package=tv.own.owntv.features.simulation
visual="$package.XpCinemaVisualTest"
runner=com.oakstudios.movioxp.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class "$visual#captureCinemaHomeCategoryAndExit,$visual#touchHomeRespondsToAvailableWidthWithOneNavigationBar,$package.XpSimulationRemoteTest" \
    "$runner" | tee cinema-instrumentation.txt
if grep -Fq 'OK (3 tests)' cinema-instrumentation.txt; then
    adb shell am force-stop com.oakstudios.movioxp
    adb logcat -c
    adb shell svc wifi disable
    adb shell svc data disable
    trap 'adb shell svc wifi enable; adb shell svc data enable' EXIT
    adb shell am instrument -w -e class "$visual#forceStoppedProcessRestoresCataloguesAndLibraryOffline" \
        "$runner" | tee -a cinema-instrumentation.txt
fi

mkdir -p cinema-visual
for shot in $(adb shell run-as com.oakstudios.movioxp ls files/cinema-visual 2>/dev/null || true); do
    adb exec-out run-as com.oakstudios.movioxp cat "files/cinema-visual/$shot" > "cinema-visual/$shot"
done
if ! grep -Fq 'OK (1 test)' cinema-instrumentation.txt; then
    adb logcat -d -s MovioXP:D | grep -Ei 'catalogue cache|catalogue refresh' | tail -60 || true
    [ ! -f cinema-visual/force-stop.txt ] || cat cinema-visual/force-stop.txt
fi
grep -F 'OK (3 tests)' cinema-instrumentation.txt
grep -F 'OK (1 test)' cinema-instrumentation.txt
offline_log=$(adb logcat -d -s MovioXP:D)
for signal in 'Catalogue cache hit' 'Catalogue cache miss' 'Catalogue refresh completed' 'Catalogue refresh failed'; do
    printf '%s %s\n' "$signal" "$(grep -Fc "$signal" <<< "$offline_log" || true)"
done > cinema-visual/force-stop-request-counts.txt
cat cinema-visual/timings.txt
for expected in 01-cinema-home 02-explore-more 03-action-category 04-exit-confirmation 05-simulator-remote 06-warm-offline-home 07-cinema-search 08-discover-selector 09-discover-grid 10-series-grid 11-focused-shelf-card 12-single-ok-detail 13-context-search 14-font-preview 15-native-age-badge-fixtures 16-right-edge-focus 17-touch-home-compact 18-touch-home-expanded 19-storage-maintenance 20-offline-after-force-stop 21-compact-navigation 22-expanded-navigation; do
    test -s "cinema-visual/$expected.jpg"
done
