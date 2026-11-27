# MovioXP 4.0 B — Cinema Interface

Android version name 4.0 B, version code 100099. Based on MovioXP 4.0 A, commit cdf21697. The package and signing configuration carry forward.

## Changes

- Cinema is the experimental TV default with a persistent icon rail, profile connection indicator, and access to the account menu, About and Exit.
- TV Interface in Display & Layout switches between Cinema and Classic immediately. Classic retains its existing navigation placement. Mobile and tablet stay on the existing presentation.
- The scrolling Home feature uses enabled catalogue content and cached artwork and metadata. Its Play and More Info actions use the existing paths.
- Cinema Home shelves reveal landscape artwork and bounded metadata after 400 ms of focus. The poster is used without distortion if no backdrop is available. Dense grids retain stable poster slots and show a focused preview panel. Sports retain landscape proportions and progress indicators remain available.
- UK-labelled classifications display in coloured circles when a valid classification exists. Missing or unsupported values are hidden.
- The Cinema search panel provides an alphabetical keyboard with punctuation, direct editing and physical keyboard support. It shares the existing results, suggestions, recent searches and catalogue repositories. Supported catalogue categories can populate the results panel while the query is empty.
- Existing layout, browsing, playback, Central and activation mechanisms remain in place. The TV interface choice is stored locally by profile and device group.
- The software updater uses numeric version codes and recognises 4.0 B as a QA-channel build.

## Publishing

The release JSON uses the existing Central QA URL template and includes the exact signed APK size and digest. Central V2.3.1 publishing was not exercised, so acceptance of the alphanumeric version remains unverified. The production APK and the 4.0 A fallback are untouched.

## Physical testing still required

Install over 4.0 A and check data retention, Cinema/Classic switching, field entry and remote navigation, 400 ms expansion, mixed row heights and measured scrolling at 75% zoom and Medium cards, 720p/1080p/4K, Firestick, playback and return-to-focus, and mobile/tablet appearance. Check live Central sign-in, QR, add-on sync and release publishing using the normal QA path.
