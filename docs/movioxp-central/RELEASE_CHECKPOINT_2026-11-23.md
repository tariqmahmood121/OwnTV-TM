# MovioXP / NEW MovioTV Central — release checkpoint, 23 Nov 2026

## Requested outcome
Ship a **signed release APK**, not a debug APK, based on the protected latest MovioXP work. Do not modify the preserved stable v1.0.73 branch or the known-good v1.0.77 build.

## Incident: duplicate / cross-account V10
Central SQL showed these profiles:
- moviohomeXP account 13: V10 profile 10
- trinity account 17: V10 profile 19 AND V10 profile 20
- Profile 19 and 20 had two add-on links each, zero sync_records each at last inspection.
- Both the add-on sync path and the timeline/favourites sync path invoked ensureProfile() and could race: they matched local profiles by name or POSTed a new profile on a miss.
- The app's base Room profile chooser included local profiles from other cloud accounts.

## Required fix in the release
1. Use Central GET /profiles as the authority. Never POST/auto-create Central profiles during background sync, login, account switching or favourites sync.
2. Never match by profile name. Persist (account_id, Central profile_id) -> unique local profile_id, and filter the picker and shell by the authenticated account's verified profile membership. Show only profiles owned by that account.
3. When switching accounts, **keep all previous accounts' local profiles, add-ons, favourites, history, resume and settings stored**, but make them dormant. Reconnecting to that old account restores its same profile mappings and data. Logout is not deletion.
4. Do not re-upload stale add-on lists from a previous account or resurrect a disabled/removed Comet addon. The Central server is authoritative on refresh. Only an explicit install triggers an addon POST; a manual remove unlinks the active profile, not the other profiles.
5. Verify by logging Trinity and moviohomeXP alternately on phone and TV; no cross-account profiles and no profile POST on login.
6. Keep release signing secret-based via MOVIOXP_KEYSTORE_* GitHub secrets; don't output credentials.

## NEW Central preview status
Preview only: redesigned dashboard table with expandable accounts, profiles and add-ons; feature controls moved into Settings; five-step account wizard with Profile A/B, add-on default collection and assignments; Add-on Library with encrypted named collections and bulk paste up to 20; account/profile renaming and password reset; profile deletion impact and guarded delete; add-on enable/disable/remove.
Tests earlier tonight: XPWizardFlow24 account 16 with Profile A ID16, Profile B ID17, two add-ons and three links passed; editable manifest persisted; Trinity profile duplicate identified. Main/production admin has not been promoted from preview.

## Remaining blockers/tests
- Database cleanup of Trinity V10 profiles 19/20 **must not happen until signed Android fix installed and validated**; both IDs must remain distinct.
- Confirm profile membership restoration after logout/login on both personal devices.
- Confirm add-on removals or disables persist across app restarts and profile switches.
- Confirm subtitle/media provider configuration is kept private, and no TorBox URL leaked into logs or docs.
- Verify the signed APK installs as an update to existing apps (signer/package match) before uninstalling anything. Older debug-signed builds may not accept a release-signed upgrade.
- Confirm playback, source preference, resume, audio and subtitle persistence do not regress.
- Check Android CI release artifact before claiming successful build; this checkpoint is not a build-success statement.

## GitHub
Repository: tariqmahmood121/OwnTV-TM
Fix branch: fix/movioxp-central-profile-isolation-release
Reference branch: feature/movioxp-central-addon-integration
