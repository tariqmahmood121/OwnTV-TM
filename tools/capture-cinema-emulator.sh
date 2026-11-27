#!/usr/bin/env bash
set -euo pipefail

test_status=0
bash gradlew :app:connectedX86_64DebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=tv.own.owntv.features.simulation.XpSimulationRemoteTest,tv.own.owntv.features.simulation.XpCinemaVisualTest \
    --stacktrace || test_status=$?

mkdir -p cinema-visual
for shot in $(adb shell run-as com.oakstudios.movioxp ls files/cinema-visual 2>/dev/null || true); do
    adb exec-out run-as com.oakstudios.movioxp cat "files/cinema-visual/$shot" > "cinema-visual/$shot"
done
exit "$test_status"
