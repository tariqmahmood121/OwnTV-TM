# MovioXP 3.1 updater deployment — staged QA

MovioXP uses a **separate** update channel and Android package, com.oakstudios.movioxp.
The existing /downloads/moviotv.apk belongs to the original MovioTV and must never be offered to MovioXP.

## Current state

- The GitHub Actions signed StandardRelease job produces **two files** in its artifact:
  the exact signed MovioXP APK and a matching movioxp-version.json (SHA-256, size,
  Android versionCode, package, channel and notes).
- GitHub Actions does not have permission to publish to Plesk. Do not claim the updater
  is active until both files have been manually deployed and verified.
- Candidate 3.1.0-qa1 is a QA release; do **not** overwrite the current stable 3.0.0 APK.
- Normal stable users check only /downloads/movioxp-version.json. QA builds
  check only /downloads/qa/movioxp-version.json, at most once every 24 hours
  automatically (or immediately when Check for updates is pressed).

## Staging the QA release on the downloads website

1. Download the new successful GitHub Actions artifact; confirm its run is complete
   and the APK's signing verification step passed.
2. In Plesk, create a QA-specific folder beneath the downloads document root:
   /downloads/qa/. Configure HTTPS and Content-Type application/json for .json.
3. Upload the signed QA APK under the filename movioxp.apk into that QA directory.
4. Upload the generated movioxp-version.json from the **same CI artifact** alongside it.
   Its apkUrl points to https://moviotv.opticalzoo.com/downloads/qa/movioxp.apk.
   If your actual downloads host or path differs, update both the app's feed URLs
   and CI manifest generator and rebuild, rather than silently redirecting.
5. Open the manifest URL and inspect packageName, versionCode, channel and sha256.
   Download the hosted APK and independently verify its size and SHA-256 match the
   manifest exactly (e.g. sha256sum, or PowerShell Get-FileHash).
6. Test 3.0.0 -> 3.1.0-qa1 upgrade on one device via a controlled QA setup.
   QA checks intentionally do not appear to stable 3.0.0 users; a stable 3.1
   candidate will require a dedicated stable release and manifest.
7. Test app data preservation, Central profile/account switch, source, history,
   resume, search, the four Central LED states, phone/tablet/TV navigation, and
   the Android unknown-apps permission prompt.

## Promoting to stable (ONLY after user approval)

1. Create a new stable tag/branch based on approved QA code with a **greater**
   Android versionCode and versionName 3.1.0 (no -qa).
2. Build and verify that signed stable APK in GitHub Actions.
3. Upload its exact APK to /downloads/movioxp.apk and its matching manifest to
   /downloads/movioxp-version.json. Publish the files safely (prefer upload to
   temporary names, verify, then atomically rename), preserving the last good
   stable files for rollback.
4. Verify HTTPS, package, SHA and versionCode again before announcing the update.
5. Do not publish QA manifests on the stable route.

## Security and traffic limits

- Fail closed on missing/malformed/404 manifests. Do not equate an unreachable
  server with "up to date".
- HTTPS only, no HTTP redirects followed automatically.
- APK verifies exact bytes (size and SHA-256), package name, increasing
  Android versionCode and matching existing signing certificate before
  opening Android's system installation prompt.
- Android confirmation is mandatory. No silent installs. Never clear local data.
- Auto checks occur at most once per 24 hours after successful checks; no
  background polling or video proxying, and no Central API dependency.
- The download server will receive ordinary HTTP logs and download traffic.
