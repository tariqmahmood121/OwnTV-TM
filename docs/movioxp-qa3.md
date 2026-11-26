# MovioXP 3.1.0-qa3

Android-only QA release, version code 100094, based on the successful 718430bd build.

- Refresh the active profile's add-ons on foreground entry and every 90 seconds while active. Manual refresh remains available and uses the same serialized refresh path.
- Manage installed add-on order and enablement per profile. Disabled assignments remain visible when the API includes them. Playback sources follow add-on priority, with last-played source selection preserved.
- Discover Live TV lists compatible catalogues and their advertised filters. Compact landscape cards fit the full artwork.
- Home uses uniform poster dimensions and spacing. The appearance settings offer Small, Medium and Large presets.
- Phone entry opens only on request. Secure mobile sign-in shows startup status, expiry and retry. Manual add-on entry reuses the local phone form. MovioXP setup and account management are for existing accounts.

## Central V2.3.1 compatibility

Local priority and enablement are saved on this device. The documented Central assignment API replaces the entire list and can omit disabled entries. Using it for moves or toggles could overwrite assignments. Existing explicit install and remove operations still use the previously supported API. A Central-disabled add-on that is omitted from the profile response cannot be distinguished from a removed add-on.

On November 26, 2026, both deployed HTTPS mobile sign-in URLs returned HTTP 404. The app requires the existing pairing service to return a session before it can display a login QR. Direct TV sign-in remains available. The separate local add-on phone form does not use the pairing service.

## Physical QA

- Install over the previous signed QA APK and confirm profiles, settings and viewing progress survive.
- On two profiles, test manual and foreground add-on refresh, local priority and enablement, Central add/remove, playback source order and last-played selection. Confirm playback continues through a background refresh.
- Test Discover with multiple Live TV add-ons, including Sport X. Check supplied filters, artwork, playback, focus and the empty state.
- Check Small, Medium and Large Home cards across rows on Android TV. Check touch layouts on a phone and tablet.
- Verify the add-on QR and connection code on the same network. On a deployment with the HTTPS pairing service, verify login QR, phone submission, expiry, retry and cancellation.
