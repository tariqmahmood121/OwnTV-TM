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

## Next build P0: port MovioTV event-triggered Central playback sync

The original MovioTV agreement is to update the Central profile **when playback is paused, stopped, completed, or when a seek settles**, rather than waiting for the 90-second periodic fallback. A seek is considered settled **3 seconds after the most recent scrub**; another scrub restarts that timer. Sync movie ID/title, resume position, duration, and existing per-title source/audio/subtitle preferences, all isolated to the authenticated Central account and active profile.

**Current 1.0.73 limitation:** `MovioXpPlaybackEffects` persists the movie position locally every 5 seconds, and `XpContinueStore.updateProgress` only marks Central records dirty. `StremioCatalogScreen` runs `XpCentralDataSync.sync` approximately every 90 seconds while its catalogue composition is active. Leaving the app before that may mean Central has not received the latest title/position, even though local resume works.

**Port requirements:** (1) immediate local write and queued cloud upload on pause, Back/stop, completion, and stabilized seek; (2) initial seek after resume must not send a spurious zero position; (3) async network I/O off the UI thread, serialization and coalescing, with dirty records retained for retry if offline; (4) sync source, audio, subtitle choices on change; (5) 90-second sync remains a safety net, not the primary mechanism; (6) 3-second seek debounce is independent from the normal 1-second playback ticks, no flood of requests; (7) verify Central profile ownership/permissions and avoid cross-account leakage; (8) ensure no regression to the Android 17 `OwnTVShell` ART `VerifyError`.

**Validation:** Play movie on phone, pause and seek several times, stop; verify `GET /sync?profile_id=<id>` returns the latest `resume` record before 90 seconds. Restart same movie on the same device and on TV with same Central profile. Also test offline/reconnect replay of dirty data and finished movie deletion. Central server and multi-device behavior remains unverified until tested.

**Protected branch:** `stable/movioxp-v1.0.73`. Target: `feature/movioxp-v74-next`.
