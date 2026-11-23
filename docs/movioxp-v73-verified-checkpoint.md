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
