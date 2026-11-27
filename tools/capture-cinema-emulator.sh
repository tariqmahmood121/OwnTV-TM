#!/usr/bin/env bash
set -euo pipefail

test_status=0
bash gradlew :app:connectedX86_64DebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.package=tv.own.owntv.features.simulation \
    --stacktrace || test_status=$?

mkdir -p cinema-visual
for shot in $(adb shell 'ls /data/local/tmp/cinema-visual-*.jpg' 2>/dev/null || true); do
    adb pull "$shot" "cinema-visual/${shot##*/cinema-visual-}"
done
if [[ $test_status == 0 ]]; then
    for expected in 01-cinema-home 02-explore-more 03-action-category 04-exit-confirmation 05-simulator-remote; do
        test -s "cinema-visual/$expected.jpg"
    done
fi
exit "$test_status"
