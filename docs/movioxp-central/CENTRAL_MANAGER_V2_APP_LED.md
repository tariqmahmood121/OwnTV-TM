# MovioXP Central Manager v2 — app-led integration checkpoint

**23 November 2026.** The uploaded ZIP `MovioXP_Central_Addon_Manager_v1(1).zip` was standalone, with `mxam_addons` plus free-text account/profile labels. It did **not** use Central's real `accounts`, `profiles`, `account_addons`, or `profile_addons` records.

## Agreed source of truth

MovioXP Android installs/configures add-ons, which must be uploaded to the logged-in Central account and assigned to the active Central profile. A Central web manager must read/write those **same account/profile add-on records**, not create a second add-on library. Multiple add-ons must be supported for each account/profile. Authenticated Plesk Central web administration must also list and **create real Central profiles**, as the old Central could.

## v2 prototype produced in conversation

A new, test-only PHP 8.0+ archive `MovioXP_Central_Manager_v2_APP_LED_TEST.zip` was created and verified locally with `php -l` and two offline test scripts. It has not been uploaded to Plesk or tested against live Central. It is not yet checked into this GitHub branch as source files.

The new account-scoped web manager uses:
- `POST /login` with a real Central user; the account ID comes from the API, not free text.
- `GET /profiles` to display profiles and `POST /profiles` to create an adult/child profile.
- `GET /addons` and `POST /addons` to display/insert multiple account add-ons.
- `GET /profiles/{id}/addons` and `PUT /profiles/{id}/addons` to enable add-ons on each account-owned profile.
- An optional, encrypted legacy-compatible `mxam_keys` TorBox vault; the old duplicate `mxam_addons` table is **not used**.

The existing Android `XpCentralAddonSync.syncProfile` already calls the same add-on endpoints, with a sync trigger after manual add-on installation if Central is connected. This still requires live verification across devices.

## Remaining work (must not claim complete)

1. **Deploy to a parallel URL first**, e.g. `/movio/central-manager-v2/`, after backing up Central API/DB and checking that the documented route includes are installed and enabled. Do not overwrite `/rumple/movioadmin/` or `/movio/api/` without confirming their current code.
2. **Full Central profile discovery in Android**: a new profile created on Central must be imported into the phone/TV local picker with its stable Central ID on every login/profile refresh. The existing app mainly maps currently local profiles by ID/name; creating a profile on the web alone does not establish this.
3. **Central-authoritative add-on edits**: the existing Android sync merges all local URLs with Central and can resurrect add-ons removed/re-keyed on Central. Track an encrypted, account/profile-scoped last-synced baseline and upload only explicit local deltas; treat remote changes as authoritative otherwise.
4. **TorBox key rotation**: saving a new key in the v2 vault does not automatically replace already-installed add-ons. Implement a per-account, authenticated stable-ID add-on update route and verified provider-specific adapters (Torrentio/Comet/MediaFusion have different URL shapes), then verify old device URLs cannot roll back the new key.
5. **Multi-profile testing**: app install -> Central library -> assigned profile -> second device; website creates profile -> app picks it up; key rotates -> both devices update after reconnect; account and child-profile isolation.
6. **Security**: preserve Central's ownership/auth checks, never log bearer tokens or tokenised manifest URLs, keep TorBox AES keys separate from published code, test with a non-critical account.

**Working Android fallback:** `stable/movioxp-v1.0.73`. Source persistence fixes on `fix/movioxp-v78-source-persistence` should not be conflated with verified server sync.
