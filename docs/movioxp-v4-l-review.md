# MovioXP 4.0 L QA verification

The baseline was signed 4.0 K at c9ae373e. Final application and test source 053f3b1b passed Gradle unit tests and Android emulator instrumentation in Actions run 46142638940. Visual evidence is artifact 15262553245.

The prior picker could exhaust the 30-day exposure window and stay unavailable. Refresh also requested a catalogue refresh for every Home row. The fix tries eligible Trending entries from enabled Discover catalogues and relaxes only the no-repeat window when needed. Featured Refresh scopes retry work to the active profile and its Trending request. The unit test uses 600 history records and a depleted Trending pool. Emulator coverage seeds 600 history records and verifies featured Home, exposure alias isolation and expiry. The actual affected profile was not available for testing.

The emulator showed populated Series and Action grids, a three-icon compact sidebar and the expanded destination panel. Existing Cinema controls, mobile width checks and the pending-update maintenance test passed. The two-column episode view is still implemented within the existing details dialog. The new build stores short-lived featured availability results in the catalogue repository, while playback obtains a live source listing when selected.

After adb force-stop and disabling both Wi-Fi and mobile data, the new process found its saved manifest and 43 cache files. Home Action loaded in 4.752 s including Compose startup and the test scroll. Discover and Library restored successfully. The diagnostic log counted 45 cache hits, zero misses, zero completed and zero failed catalogue refreshes during this offline interval.

| Home backdrop measurement | 4.0 K | 4.0 L |
| --- | ---: | ---: |
| Network-enabled clean-cache emulator run | 14.195 s | 8.182 s |
| Offline Home remount with Coil memory cleared | 1.538 s | 0.903 s |

The emulator reported 2,593,198,080 bytes RAM, 30,641,792 bytes Java heap in use, 5,754,759 bytes image cache and 2,873,722 bytes catalogue cache. Timings are fixture-dependent. The virtual-width tests do not demonstrate physical folding. Physical Xiaomi/Firestick testing, device reboot, signed upgrade installation, the affected profile, actual add-on playback and signed-in Central verification remain outstanding.
