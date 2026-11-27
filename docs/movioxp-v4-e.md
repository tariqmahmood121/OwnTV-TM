# MovioXP 4.0 E — Cinema and Simulator Corrections

- The touch remote sends Android D-pad events to the Compose view that owns TV focus. Direction keys use arrow icons, and the remote moves underneath the 16:9 viewport on near-square displays.
- Immersive touch mode gives the TV canvas the available screen space. Shared app dialogs are drawn and measured inside the simulated TV viewport.
- Cinema features Trending movies with backdrop artwork. The title and metadata sit near the lower left with compact Play or Resume and More Info buttons. Release dates display as years, and incomplete artwork no longer falls back to a portrait hero.
- Cinema shelves keep their artwork-only posters, left-anchored focused expansion, white artwork focus outline and landscape sports rows.

This QA build is gated by an Android emulator test for short D-pad presses, OK, Back, held input, and focus in the simulator overlay. Visual checks on an unfolded device and physical TV checks for navigation, playback and artwork remain outstanding.
