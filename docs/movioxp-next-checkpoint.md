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
