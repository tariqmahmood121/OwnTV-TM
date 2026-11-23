# MovioXP v1.0.73 tested checkpoint — 23 November 2026

## Protected source baselines
- Stable 1.0.70: `stable/movioxp-v1.0.70` (older, tested login/add-on sources)
- Stable 1.0.73: `stable/movioxp-v1.0.73` from commit `a59e02b5ab6e3b2ed7b31ee78df5e71db38ab189`
- Future work: `feature/movioxp-v74-next`
- Successful Actions APK: https://github.com/tariqmahmood121/OwnTV-TM/actions/runs/45165602101

## User-confirmed tests — HONOR MBH-N49, Android 17
- MovioXP v1.0.73 launches without the previous ART `VerifyError`.
- Movie resumes at its last saved position on the same phone; the fix is verified locally.
- v1.0.70 had already been confirmed working for Central login and add-on sources.

## Not yet verified in v1.0.73
- Central cloud transfer of resume state to another device/profile.
- Central upload and re-install of manually added add-ons across devices.
- Central transfer of source, audio and subtitle choices.
- Appearance settings and large login button on multiple screen sizes.

Do not report Central cross-device sync as verified until the user tests it. Keep the small Compose helper/extraction structure that solved the repeated ART VerifyError. Never modify the stable branches directly; work on `feature/movioxp-v74-next` or children and promote only after test.

## Next build P0: preserve last manually selected movie source (reported on 1.0.73)

**Repro:** Start a movie, change its stream source in the player, press Back, and play the same movie again. The saved playback position is restored correctly, but the stream resets to the original/default first source. **Not fixed in 1.0.73.**

**Required behavior:** Resume the movie from the saved position **and** prefer the previously user-selected source for this exact title/profile when available. Remember the actual stream/source choice (differentiate multiple streams from the same add-on); preserve profile isolation. If the saved source is expired/unavailable, gracefully fall back without losing the resume timeline. Central source preference may be synchronized by stable nonsecret identifiers; do **not** upload expiring signed stream URLs or credentials.

**Code locations and likely mechanism (to investigate, not yet established as definitive cause):**
- `app/src/main/java/tv/own/owntv/features/home/StremioCatalogScreen.kt`: auto-play sorts candidates using `last_source_${profileId}_${contentKey.hashCode()}` by **provider name**, rather than unique stream identity.
- `app/src/main/java/tv/own/owntv/features/shell/OwnTVShell.kt`: candidate handler plays `candidates.first()` and stores its provider immediately, potentially overwriting a previously chosen preference.
- `OwnTVShell.kt` player HUD source picker saves the selected provider name only.
- `app/src/main/java/tv/own/owntv/features/shell/MovioXpPlaybackSupport.kt`: `storeMovioXpSource` persists provider and marks it for profile Central sync.

**Test acceptance:** (1) Reopen same movie after choosing source B; source B plays at the saved position, including when A and B belong to the same add-on. (2) Fallback works if B is no longer available. (3) Source preference remains profile-scoped. (4) Test Central propagation between devices separately. (5) Preserve the 1.0.73 Android 17 verifier fix: avoid expanding `OwnTVShell` substantially.

**Development branch:** `feature/movioxp-v74-next`. **Protected baseline:** `stable/movioxp-v1.0.73` (untouched).
