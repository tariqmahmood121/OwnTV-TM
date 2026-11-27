#!/usr/bin/env bash
set -euo pipefail

adb logcat -b main -G 16M
test_status=0
bash gradlew :app:connectedX86_64DebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.package=tv.own.owntv.features.simulation \
    --stacktrace || test_status=$?

mkdir -p cinema-visual
adb logcat -b main -d -v raw -s CinemaVisual:I | python3 -c '
from collections import defaultdict
from pathlib import Path
import base64
import re
import sys

shots = defaultdict(dict)
for line in sys.stdin:
    match = re.fullmatch(r"([0-9]{2}-[a-z-]+) (\d+) ([A-Za-z0-9+/=]+)\n", line)
    if match:
        name, position, chunk = match.groups()
        shots[name][int(position)] = chunk
for name, chunks in shots.items():
    encoded = "".join(chunks[index] for index in range(len(chunks)))
    image = base64.b64decode(encoded, validate=True)
    if image.startswith(b"\xff\xd8") and image.endswith(b"\xff\xd9"):
        Path(f"cinema-visual/{name}.jpg").write_bytes(image)
'
if [[ $test_status == 0 ]]; then
    for expected in 01-cinema-home 02-explore-more 03-action-category 04-exit-confirmation 05-simulator-remote; do
        test -s "cinema-visual/$expected.jpg"
    done
fi
exit "$test_status"
