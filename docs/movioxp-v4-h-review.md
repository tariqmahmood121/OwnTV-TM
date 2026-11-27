# MovioXP 4.0 H Home timing and acceptance

The Android 15 emulator loaded the cinematic backdrop in 9,814 ms from mounting the test Home with a clean feature cache. A second Home mount took 759 ms with no active network and the Coil memory cache cleared. These are single emulator measurements, with a live provider for the cold run. The warm measurement is a Home remount in the same process.

The emulator passed the Cinema catalogue, Explore More and Back checks as well as the dedicated simulator remote test. The captured offline Home shows the decoded backdrop, title, compact controls and Continue Watching. Physical TV and signed-in Central performance, a process-level restart, and the full rotation interval remain unmeasured.

[Emulator run](https://github.com/tariqmahmood121/OwnTV-TM/actions/runs/46028530191) · [Screenshots and timings](https://github.com/tariqmahmood121/OwnTV-TM/actions/runs/46028530191/artifacts/15212784622)
