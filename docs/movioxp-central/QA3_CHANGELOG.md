# MovioXP 1.0.83-qa3 changelog

Version code 100085. This release builds on the QA2 Central integration and the later QA2 persistent-cache fixes.

## Playback and navigation

- Central movie cards resolve IMDb identities through Central's verified TMDB external-ID lookup. Legacy Central installations can fall back to a unique Cinemeta title and full release-date match. Conflicting or insufficient matches are left unresolved.
- Stream requests use each add-on's supported identifiers. Playback reuses the stored identity for known resume records, looks up saved sources using verified aliases, and deduplicates compatible requests and returned URLs.
- Supported Cinemeta series expose season and episode selection. Episode streams use episode-specific identifiers, and the previous selection is remembered per profile.
- MovioXP keeps the originating browse destination composed behind fullscreen playback. Discover, Library, Search and Home retain their active controls and lists, and TV focus restoration uses stable poster keys.

## Catalogue and personal collections

- Discover has a vertical poster grid with the original Movies-style category sidebar, category and title search, category defaults, local sorting, supported pagination and independent zoom. Phones can collapse the category sidebar.
- Library shares the vertical browser and contains Favourites, History, Continue Watching and Watchlist. Discover and Library use the same personal collection stores. Favourites and History use the existing Central record contract. Watchlist uses compatible per-title Central setting records.
- Home retains horizontal configurable catalogue rows and independent zoom. Shared cached catalogues and posters survive restarts. Offline content remains visible during refresh. Disk eviction includes a shared size limit, and provider invalidation stays scoped.
- Poster badges show the star, numeric rating and release year. Details identify the actual rating source. The profile's rating and year visibility, catalogue sorting and zoom preferences sync through Central.

## Central update

Deploy the separate QA3 Plesk ZIP to enable the verified movie resolver, paginated shared catalogue responses and IMDb enrichment on Central. The ZIP includes installation and rollback instructions. The existing authenticated first-page route stays compatible. Central uses the existing shared cache, settings and quota, and keeps its credentials on the server.

## Verification scope

The Android suite covers identifier matching and add-on identifier compatibility, Central envelope parsing and pagination, legacy progress metadata, episode IDs, sorting and persistent cache behavior. PHP fixtures cover existing cache and fallback behavior, direct mapping and page separation. Real-device D-pad, pinch, fullscreen return, authenticated stream playback and multi-device profile sync require deployment and device testing.
