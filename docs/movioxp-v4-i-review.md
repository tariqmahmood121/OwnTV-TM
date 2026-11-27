# 4.0 I QA verification

Source commit 2297857529e1bc2ba8ea0627aab01178b3153e19 passed Gradle unit tests and Android emulator instrumentation in Actions run 46055899872. The emulator used the TV simulation view and guest catalogue data with a seeded Continue Watching item.

| Measurement | 4.0 H | 4.0 I first | 4.0 I second |
| --- | ---: | ---: | ---: |
| Cold Home, clean cache | 9.814 s | 9.448 s | 8.398 s |
| Offline Home remount | 0.759 s | 1.217 s | 0.709 s |

The offline measurement uses a same-process remount with Android network access disabled and Coil's memory cache cleared. It does not represent a cold process start.

Visual artifact 15226453783 shows the Home feature, Search, Discover selector and Action poster grid. It does not demonstrate a populated Series grid or the expanded focused shelf card. The tests verified navigation to the Series grid shell. Real-time rotation, lifecycle restoration on a foldable, rapid physical D-pad use, and signed-in Central and playback regression testing remain outstanding. The existing Central payload has no verified fields for featured-movie settings, which are stored locally per profile.
