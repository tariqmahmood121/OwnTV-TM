# MovioXP Central — integrated multi-add-on management (v2 design/checkpoint)
**Reviewed:** 23 November 2026. **Status:** Source audit and integration contract only; **not deployed**. Never claim the live Central server or multi-device sync has been verified.

## Sources inspected
- Uploaded `MovioXP_Central_Addon_Manager_v1(1).zip`: `README.md`, `sql/schema.sql`, `index.php`, `private/lib.php`.
- MovioXP Android: `features/home/XpCentralAddons.kt`, `StremioCatalogScreen.kt`, `XpCentralDataSync.kt` and `XpCentralEventSync.kt`.
- Prior in-repository Central extension: `docs/movioxp-central/004_stremio_addons.sql` and `addon_routes.php`.

## Confirmed current behaviour vs gap
1. **Android app install**: `installAddon()` saves the add-on locally. With an existing Central connection, it invokes `syncAddonsWithCentral()`, which invokes `XpCentralAddonSync.syncProfile()`. That calls authenticated `GET /addons`, `POST /addons`, `GET /profiles/{centralProfileId}/addons` and `PUT /profiles/{centralProfileId}/addons`. The app attempts to upload a manually installed add-on to the account library and assign it to the active profile. The same sync runs after restoring a Central login/profile. **Whether the corresponding authenticated routes have been installed and work on live Plesk remains unverified**.
2. **The uploaded PHP v1 manager** is completely separate: it makes `mxam_keys` and `mxam_addons`, requires its own admin password and accepts manually typed `account_ref`/`profile_ref`. It exports private JSON only; it does not write `account_addons`/`profile_addons` or use Central bearer-token authentication. Consequently, an add-on configured in the v1 manager is **not** automatically visible in MovioXP, and an app-installed add-on is **not** visible in that manager.
3. **Existing Central extension (if deployed)**: `004_stremio_addons.sql` and `addon_routes.php` define `account_addons` as the account-wide library and `profile_addons` as per-profile assignments. Client `XpCentralAddonSync` matches these endpoints, subject to actual deployed schema/auth/routes. These should remain the canonical tables and APIs; avoid a second permanent `mxam_addons` source of truth.
4. **Key vault**: Uploaded v1 manager stores a TorBox API key encrypted per manually entered account reference, and can derive a Torrentio manifest URL or store direct configured Comet/MediaFusion/custom URLs. This functionality is useful, but its keys must become bound to a **verified Central account ID**; its add-on/config records must be integrated with the existing Central account library, not merely exported.

## Required workflow (user decision)

### App -> Central -> other devices (mandatory)
1. Viewer signs into a Central **account** and picks a validated Central **profile**.
2. Viewer installs or manually pastes a Stremio-compatible add-on manifest URL in the MovioXP app.
3. App persists it locally immediately, then sends an authenticated HTTPS request that **upserts it into that account's add-on library**, assigns it to **the active profile**, and reports upload success or queued/offline status. Other accounts must never see it.
4. The same profile on another phone/TV retrieves the account's assigned add-ons on login/profile switch or refresh, without requiring the URL to be typed again.
5. Multiple add-ons are supported per account/profile. One account add-on can be enabled on several profiles, with different per-profile ordering/enabled flags. Default installation scope is **current profile**; an explicit, authorised option can share with all profiles.
6. When offline, keep a durable local pending-install/change queue; reconcile after reconnect. Do not replace a full profile list from stale data or silently restore a user-deleted add-on.
7. A change on Central must also flow down to devices on the next authenticated refresh. User should see pending/synced/error status.

### Central manager -> app (mandatory)
- Admin UI lists **real accounts and their real Central profiles**, never asks operators to type unverified free-form references.
- Add/edit/disable/remove add-ons from the Central account library and assign each to zero or more owned profiles.
- Key vault per verified account: TorBox and other supported providers, with **provider-specific adapters** that know how to transform the key into a supported configured add-on manifest. Never simply replace arbitrary tokens into untrusted host URLs.
- If a TorBox or other provider key changes: update the stored account credential, re-resolve only add-ons that depend on that credential, increment configuration version, and refresh the authorised profile's add-ons on clients. Preserve stable add-on identity/assignment across key rotation; do not create another library row just because an embedded token in the resolved manifest URL changed.
- An add-on can have multiple **configured instances** (distinct configuration ID, display name, credential reference and provider mode); key by stable internal `addon_id`/instance ID rather than solely the full personal URL.
- Supported initial modes: direct HTTPS manifest URL; Torrentio configured using the account's TorBox key; Comet/MediaFusion direct URLs from their configuration pages unless a verified adapter exists. Later configurable adapters may be added intentionally.
- Never display a key in normal lists or write keys/raw personal URLs to logs. All access uses existing Central role/account/profile authorization.

## Recommended architecture, without a second add-on database
- Reuse `account_addons` and `profile_addons`. Add a Central-owned `account_provider_credentials` key vault and stable `addon_instance_id`/configuration metadata migration **after inspecting the live schema**. Do not apply `mxam_*` schema directly into production as a parallel long-term store.
- Manager UI is a secured page under the existing Central administrator area. Reuse Central's admin auth/permissions, CSRF protection and real account/profile selectors. Do not expose a public export of personal configured URLs.
- Central service resolves a configured manifest for each permitted account/profile and returns the right URL over authenticated HTTPS only when the client needs it. Personal URLs may embed tokens; treat them as secrets in transit and at rest, and redact request/response logs. A credential rotation invalidates stale resolved URLs.
- Preserve current API contracts `GET/POST /addons`, `GET/PUT /profiles/{id}/addons` for older clients where possible, but **add a targeted idempotent assignment** operation for multi-device writes. Whole-list `PUT` based on a stale snapshot can overwrite changes from another device. Use version/ETag conflict handling or server-side atomic upsert/remove.
- Protect account/profile isolation at each endpoint via Central's server-side session/token checks. Never accept client-supplied `account_ref` to decide which user's secrets to read.
- Treat v1 `mxam_*` data (if actually deployed) as candidate import data only, validate each account/profile against Central before importing, then retire the standalone manager. No migration should delete/overwrite existing Central data without a backup.

## Suggested API operations (integration, not yet implemented)
- `GET /addons` — authenticated account library (IDs, safe metadata, config and status, no raw keys).
- `POST /addons` — idempotent create/upsert for account, either direct manifest or validated provider adapter/config.
- `PATCH /addons/{addonId}` — rename, configuration reference, enable/disable.
- `DELETE /addons/{addonId}` — remove an owned instance and its assignments (separate confirmation).
- `GET /profiles/{id}/addons` — active assignments, ordered, with authenticated resolved manifests.
- `POST /profiles/{id}/addons/{addonId}` — assign one owned instance idempotently.
- `DELETE /profiles/{id}/addons/{addonId}` — remove one assignment idempotently.
- `GET /addon-providers` — advertise enabled configuration modes/adapters and required fields.
- `PUT /account/provider-credentials/{provider}` — write/rotate an encrypted provider key, never return it in GET responses.
All new endpoints require proper role and profile authorization. These are a **proposal**, not confirmation that the current server supports them.

## P0 integration and regression checks
- Snapshot Central DB, identify schema version and deployed `/movio/api/index.php` route includes, verify `004_stremio_addons.sql` and `addon_routes.php` have been applied. Do not assume the uploaded ZIP replaced them.
- With test account A, profile A1, install two add-ons in Android. Verify both appear in `GET /addons` and are assigned only to A1; login on device 2 under A1 and confirm both appear.
- Log in as test account B and verify no A add-ons or keys are accessible. Under A2, confirm only explicitly assigned addons.
- Change TorBox key for account A; verify only dependent instances re-resolve, with same stable IDs and assignments, and device refresh gets new configuration. Other provider instances should remain unchanged.
- Test direct Comet/MediaFusion configured URLs, duplicates, two instances of same add-on, removal/disable/reenable, offline queue, simultaneous device installs, stale profile list updates, reconnect, and no leaked secrets in logs.
- Confirm APK 1.0.77/v78 application-level source preference and local resume are unaffected. Upload of add-ons must never silently change an active movie's source.
- Only mark deployed/working when API responses and cross-device tests confirm it.

## Required deployment inputs
The live Central `index.php` routing file, private bootstrap/auth helpers, deployed schema/migration history, admin UI's authentication and add-on routes are necessary to write an **installable integrated patch** rather than a standalone prototype. Do not invent how these production files behave.
