# MovioXP 4.0 L — TV Navigation QA

- Added a floating three-icon Cinema navigation panel and an expanded destination panel with an active-page highlight. Removed the wordmark above Exit.
- Added column-aware vertical movement on Cinema Home and delayed artwork and metadata updates until focus settles.
- Expanded Series catalogue discovery to include supported aliases, alternate enabled add-on catalogues, and explicit empty/retry states. Added landscape thumbnails, episode metadata and season counts.
- Added Bollywood add-on catalogue discovery to the cache-backed featured pool. Applied the approved wordmark with a transparent background.
- Retained the 4.0 K cache-first catalogue repository, profile isolation, adaptive touch Home and Storage & Maintenance.
- Added a profile-safe featured fallback for a depleted personalised queue. The picker tries eligible Trending titles and progressively relaxes only the exposure window. Featured Refresh rebuilds the profile's picker and requests Trending without clearing other catalogues or viewing records.
- Added verified UK certificates from country-tagged source metadata and cached short-lived featured availability checks without writing playback URLs to disk.
