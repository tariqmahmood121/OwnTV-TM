# MovioXP 4.0 C — Cinema Polish

Android version name 4.0 C, version code 100100, based on 4.0 B (9e7aa11c). Package name and signing configuration carry forward.

- Cinema on TV uses locally bundled Inter with the SIL Open Font License. Classic and touch retain their existing font and layout paths.
- New TV installations and Cinema factory reset use 100% screen zoom and font scaling. Medium remains the default card size. Existing explicit values and display rollback are retained.
- Home features from the existing Trending Movies results. Local rotation is every 60 seconds while the feature is visible and idle. It pauses for focus, overlays, playback, lower rows, and backgrounding. Child profiles do not receive the additional feature.
- Cinema movie and series posters omit separate titles. Sustained shelf focus anchors the card using its existing list state and expands genuine landscape art. The white outline encloses the artwork, while metadata and synopsis sit below it. A missing landscape image leaves the portrait undistorted. Available source logos and UK classifications are reused.
- The near-black rail has fixed icon sizes and expands over the browsing layout. Settings and About are available from Profile and the rail has one Exit button.
- Picking an available category under Cinema Search opens Discover with a fixed heading and a cinematic shelf. More opens the existing pageable catalogue. My MOVIOXP uses the existing personal collections with recent Watch History first.
- Standard Discover and Series grids retain their established paging, filters and focus preview. Category shelves show real source collections rather than generated subcategories.

## Publishing and validation

The prerelease includes the signed APK, the existing QA manifest format and a ZIP containing both. Central publishing was not exercised. Central, production downloads and 4.0 B are untouched.

CI covers compilation, unit tests, Android lint, signing and version verification. Physical testing remains open for launch proportions, 100% defaults, 60-second rotation and pausing, rail and shelf focus, category navigation, My MOVIOXP ordering, mixed-height row snapping, TV and Firestick resolutions, Classic/touch regression, playback restoration and upgrade data retention.
