# MovioXP 4.0 K — Adaptive Home QA

- Added compact and wide touch Home layouts, a scrollable category strip, and inset-aware bottom navigation.
- Limited the Home Continue Watching shelf to 15 unique resumable titles. Library views retain the full stored collections.
- Added profile-local featured recommendations and a 30-day exposure window. The candidate queue uses authorized catalogues, cached genres, recency, viewing metadata and bounded stream-list checks.
- Added Storage & Maintenance with device diagnostics, adaptive cache budgets, and idle cleanup of stale catalogue entries and obsolete update files.
- Reused the approved 4.0 J branding in welcome, setup and About.
- Restored durable catalogue reuse on relaunch. Existing cached disk entries migrate to stable, versioned request keys, cached manifests hydrate before requests, and maintenance prioritizes stale entries only when space is needed. Playback URLs are still resolved at selection time.
