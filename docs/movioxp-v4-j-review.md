# MovioXP 4.0 J QA verification

The application source descends from signed 4.0 I commit 32b81713399d226d43abd933e6a053be0a52c3a2. The final source and instrumentation at 6a81f0c9 passed the Android source checks, Gradle unit tests, Cinema instrumentation and virtual remote instrumentation in Actions run 46076496542. The visual archive is Actions artifact 15235567770.

The emulator captured Home and Continue Watching, the populated Action and Series grids, the category selector, context Search, right-edge landscape artwork and metadata, the single-OK movie details with Play focused, and font selection with its live preview. The age badge image uses six fixtures passed through the native shared metadata component. The captured live titles did not supply verified UK age classifications.

| Home measurement | 4.0 I | 4.0 J |
| --- | ---: | ---: |
| Cold render with clean cache and catalogue network access | 7.609 s | 12.639 s |
| Same-process Home remount offline after clearing Coil memory cache | 0.859 s | 0.735 s |

On two earlier J runs, the cold result was 10.485 s and 9.705 s and the offline remount was 1.481 s and 0.912 s. The emulator timed the Home screen until the backdrop loaded. Catalogue network performance can affect the cold result.

Instrumentation observed two title changes at both the 20 and 60 second settings. After remount it verified that the same title was restored before rotation continued. The raw emulator wait samples are in the artifact. The test clock and remaining timer make these elapsed waits unsuitable as physical cadence measurements.

Cinema font and featured-movie settings are local per profile. The existing Central preference map has no tested keys for these fields. Physical TV, Firestick, fold/unfold, full signed-in Central, Classic/mobile and end-to-end streaming regression checks remain outstanding.
