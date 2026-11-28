# MovioXP 4.0 K QA verification

The source descends from signed 4.0 J commit 1a6fa4e7. Application and test source at 3991de7d passed source validation, Gradle unit tests, and Android emulator instrumentation in Actions run 46129549326. The reviewed visual archive is Actions artifact 15257243900.

The emulator passed the compact and expanded touch width checks with one navigation bar, the existing Cinema and virtual remote checks, and the maintenance test that retained a pending update while cleaning an obsolete fixture. Unit coverage included 0, 15, 16 and 300 viewing records, recommendation eligibility and alias suppression, disk cache migration with the original expiry, and fresh cache retention under maintenance.

A second Android instrumentation invocation followed a real adb force-stop with both Wi-Fi and mobile data disabled. It verified that the fresh process found the cached add-on manifest and 33 disk cache files, loaded the Home Action row, opened the cached Action grid, and restored Continue Watching in Library. Home Action cards became available in 4.885 s including the Compose startup and test scroll. The earlier timeout in the test used a label-based scroll, whereas the passing check explicitly brought the shelf into the viewport.

| Home backdrop measurement | 4.0 J | 4.0 K final |
| --- | ---: | ---: |
| Clean-cache emulator run online | 12.639 s | 14.195 s |
| Offline Home remount within the same process, Coil memory cleared | 0.735 s | 1.538 s |

An earlier K run before the relaunch fix measured 8.463 s cold and 0.778 s offline remount. These emulator measurements depend on network and fixture conditions. The final maintenance snapshot reported 2,593,193,984 bytes of emulator RAM, 22,444,576 bytes of Java heap in use, 4,958,330,880 bytes of free storage, 6,367,446 bytes of image cache and 2,201,871 bytes of catalogue cache.

Visual checks used an emulator with virtual compact and expanded widths. Featured artwork checks used the live catalogue with an instrumentation-only stream-availability fixture. For a real profile, the app checks available sources for shortlisted titles and resolves playback URLs for selection. The timing samples do not establish performance on 1 GB hardware. Physical folding and TV, a signed ARM upgrade install, device reboot, the affected account with a long list, real provider readiness/playback and signed-in Central regression checks remain outstanding. Logs and active player resources continue using the existing platform and player mechanisms.
