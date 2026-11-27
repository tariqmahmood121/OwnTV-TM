# MovioXP 4.0 A — Experimental Edition

Android version name 4.0 A, version code 100098. The development baseline is the signed QA6 commit 37910a59. The package and signing configuration are carried forward.

## Changes

- TV navigation adds Series and places Settings to the right. The menu can sit above the header, below it, or at the bottom. Phones retain their existing tab layout.
- The TV header uses a compact explicit Search button and the white and pink MovioXP wordmark. Profile status remains visible. Back returns focus to the TV menu when browsing.
- Home calculates the focused row’s scroll adjustment from the complete measured row, including its heading, and the available viewport. Fully visible rows do not scroll. Rows taller than the viewport use normal focused-card handling.
- Search opens a single field with persistent text and selection state. The existing search engine powers results and up to five ranked suggestions after a 300 ms debounce. The five most recent submitted searches are scoped to the account and profile.
- Pageable Home rows can link to a full collection using a stable provider/catalogue ID. Series reuses the catalogue grid and paging, while sports remains accessible.
- TV card size defaults to Medium when there is no saved setting. Existing saved card sizes are migrated on read. Navigation, card size, poster scales and labels, zoom, and main font size participate in local rollback. Zoom and main font size previews require confirmation within 15 seconds.
- The splash, About and updater show the embedded version. Update eligibility already compares numeric version codes. This experimental build uses the existing QA channel.

## Central compatibility

Central V2.3.1 and the stable download are unchanged. The existing setting records use profile-level legacy keys such as poster:year. Device-type display sync needs independent keys for TV, mobile and tablet, confirmation that unknown namespaced fields round-trip, and revision or equivalent conflict handling for offline writes. Version 4 keeps the new navigation and card size records and rollback snapshots local. Legacy settings continue using their existing mechanism. There is no activation enforcement.

## QA and limitations

Automated tests cover measured row offsets, ranking, and bounded unique search history. Device testing remains open for the reported TV setup at 75% zoom and Medium cards, 720p/1080p/4K rendering, row transitions, scroll and focus restoration, IME editing, More navigation, reset and crash recovery, playback, account and add-on sync, and upgrade over QA6 using the same signing key. Theme and accent settings still use the existing appearance controls, outside the new layout snapshot. The existing catalogue engine retains its paging window and cache limits.
