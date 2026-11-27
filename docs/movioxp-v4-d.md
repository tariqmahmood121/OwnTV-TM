# MovioXP 4.0 D — TV Simulation

Experimental Android release, version code 100101. Built from the 4.0 C Cinema Polish baseline.

On a phone or tablet, open **Settings → Developer Tools → TV Simulation Mode**, then confirm. Exit Simulation returns to the mobile shell and restores the previous orientation. Simulation starts disabled on each application start.

- Uses the existing app shell and TV Cinema layout with the same account, profile, catalogue and playback paths.
- Fits a virtual 16:9 TV canvas into the screen. Select 720p, 1080p or 4K using the resolution control. The viewport is scaled by Compose and does not create a separate full-resolution video buffer.
- The remote supports directional presses and holds, OK, Back, Home and Menu. Select remote position to cycle right, left and hidden. The hidden state leaves a Show remote control.
- Simulation defaults to 1080p, Cinema, Medium cards and 100% zoom/font. Its layout preferences and rollback are local, separate from physical TV and mobile display settings. Existing playback and library changes still use normal account sync.

## Device tests still required

Verify landscape entry and exit, virtual remote presses and holds, modal dialogue handling, keyboard and Bluetooth D-pad input, all resolutions, folded and unfolded layouts, Cinema and Classic, playback return and mobile context restoration. Test simulator preferences, reset and rollback against both actual mobile and TV preferences. Check cold start after a simulator crash. Native Android dialogs use their own windows and may require dismissing the dialog before the simulator overlay is reachable.

Physical Android TV and Firestick testing is still required for decoder, remote hardware, performance, memory, HDMI, audio and actual display behaviour. Central and production downloads were left untouched.
