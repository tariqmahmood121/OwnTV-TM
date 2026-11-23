# MovioXP checkpoint — 23 November 2026

## Working baseline

- **Stable branch:** `stable/movioxp-v1.0.70`
- **Source commit:** `6d1fefe11971037542a4170018796367e60b3303`
- **GitHub APK build:** https://github.com/tariqmahmood121/OwnTV-TM/actions/runs/45144183042
- **Status:** GitHub Actions build succeeded. User confirmed MovioXP 1.0.70 works on HONOR Android 17, with Central login and sources working.
- Preserve the Compose `OwnTVShell` split that fixed the ART `VerifyError`; never reintroduce a single oversized shell composable.

## Next build: requested refinements (not in stable version)

1. Make the Central login button visibly larger and easier to tap/focus on phone, without clipping on small viewports.
2. Restore original MovioTV advanced appearance controls inside an organized **More Settings** area: themes/colours, GUI layout/navigation, fonts/font sizes, and other profile-level visual customizations. Inspect the original UI and current settings repository before deciding what to expose; reuse existing state and per-profile persistence, rather than duplicating controls.
3. Keep the clean single MovioXP navigation bar: Home, Discover, Library, Add-ons, Settings. Never restore the duplicate legacy phone bar.
4. Maintain the easy **Add add-on URL** action and existing working add-on/login/source flows.
5. Preserve D-pad focus, keyboard behavior, responsive phone sizing, Back navigation, existing Central data, playback and profiles.
6. Test builds and a physical device before promoting any changes to the stable branch.

## Backup convention

Keep `stable/movioxp-v1.0.70` untouched. Implement new work on `feature/movioxp-next` or a child feature branch. Before promoting a future build, create another versioned stable checkpoint. Note that GitHub Actions APK artifacts may expire; the stable Git branch preserves source, not necessarily an APK indefinitely.

## Next build implementation (23 November 2026)

- Development branch: `feature/movioxp-v71-resume-central`, forked from proven `stable/movioxp-v1.0.70`.
- GitHub Actions run 71 failed at the repository's i18n technical-literal inventory, **not** due to any network issue. Inventory corrected; next attempt is run 72.
- Resume: pass the saved playhead to `OwnTVPlayer.play(startPositionMs=...)` instead of opening at 0; relative-seek fallback only if actual progress remains behind the saved position. Correct the 5-second progress capture to consult live player state. Keep end-of-film cleanup behavior.
- Audio/subtitles: save the selected language as well as engine track IDs for each profile/title, and sync both through existing Central record APIs. Restore by language when a new provider changes track IDs.
- Source: save initial manual/automatic provider selection, and track source failovers, to profile-scoped Central settings. Preserve source preference upon the next lookup.
- Add-ons: on manual installation, retain the encrypted local URL and invoke the existing Central account-library plus profile-assignment API. Queue another sync if a new installation arrives during the first sync; show completion/error on the Add-ons tab. Live server deployment and full multi-device restore require device testing.
- Appearance: XP Settings now offers shortcuts to existing original MovioTV `SettingsGroup.APPEARANCE` and `SettingsGroup.LAYOUT` with fonts, themes, colours and advanced controls; the original implementations remain the source of truth. Enlarged Central sign-in action on small phones.
- Protected stable 1.0.70 branch remains untouched. Do not promote this new build until it both compiles and passes device playback / Central sync testing.

### Device test checklist

1. Sign in to Central with an existing profile; confirm the new login button is usable.
2. On Add-ons, install an HTTPS manifest manually; check the Central profile confirmation and verify it appears after signing into the same profile on another device.
3. Play a VOD item for at least 60 seconds. Back out. Confirm its purple resume stripe, reopen the item and verify playback starts near the previous position (not 0).
4. Switch audio and subtitle language. Stop and reopen; check the same languages are restored, including when the provider add-on changes.
5. Confirm source preference and resume data appear on the second Central-connected device. Do not confuse a local saved state with a proven server sync.
6. Try Appearance (colours, backgrounds, fonts) and GUI Layout shortcuts, and verify focus and Back navigation on both phone and TV.
7. If resume/source sync fails, capture device logs, which source/add-on was selected and the Central HTTP response (redact tokens and stream URLs).
