# MovioXP Central add-on API

This is the first Central extension for syncing Stremio add-ons. Xtream/M3U provider provisioning remains on the existing `/sources`, `/assignments`, and `/profiles/{id}/sources` paths.

## Install

1. Back up the Central database and existing API files.
2. Apply `004_stremio_addons.sql` after migrations 001–003.
3. Copy `addon_routes.php` into the private directory beside `provisioning_routes.php`.
4. In the public API `index.php`, add this after the other authenticated private route includes and after `$accountId` is set:

   ```php
   require dirname(__DIR__,3).'/private/addon_routes.php';
   ```

5. Confirm `CentralBootstrap.php` is named exactly as referenced by `bootstrap_routes.php`.

All endpoints require HTTPS and the existing `Authorization: Bearer …` account token. Calls are paused when Central cloud sync is disabled. Add-on URLs are encrypted with the existing Central encryption key before storage; never log request bodies or decrypted responses.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/addons` | Read this account’s add-on library |
| POST | `/addons` | Add/update one add-on in the account library; body: `url`, `label`, optional `manifest` |
| DELETE | `/addons/{id}` | Remove an account add-on; profile links cascade |
| GET | `/profiles/{profileId}/addons` | Read enabled add-ons assigned to an owned profile |
| PUT | `/profiles/{profileId}/addons` | Replace a profile’s ordered list; body: `addon_ids: [..]` |

Only account-owned add-on IDs can be assigned to one of that account’s profiles. Add-ons are stored once at account scope and activated separately per profile.
