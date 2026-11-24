# MovioXP v1.0.85-QA5 — UI Polish and Stability Release

## 1. Foundation and version control

- Repository: `tariqmahmood121/OwnTV-TM`
- Working baseline: `feature/movioxp-qa4`
- Last verified working version: v1.0.84-qa4.
- Baseline commit: `af1ad8c4dd48d25fe2c36b75cfdd96775f657ef8`
- Create a new branch: `feature/movioxp-qa5`.
- Target version: v1.0.85-qa5.
- Preserve QA4 as a stable rollback point.
- Produce a signed release APK using the existing signing configuration.

QA4 has been tested successfully on Android TV and mobile. Do not regress existing functionality.

## 2. New minimalist startup screen

Replace the existing splash screen with a modern, minimalist design.

Design:

- Pure black background.
- Existing Movio purple Play logo centred above the title.
- Large, bold MOVIOTV text.
- MOVIO in white.
- TV in bright pink.
- Smaller text underneath: Created by Ziggs.
- Created by in white.
- Ziggs in purple.
- All elements centred horizontally and vertically.
- Use clean, modern typography.
- No version number.
- No loading bar.
- No additional graphics or unnecessary information.
- Subtle fade transition into the application.

Reuse the actual existing Movio Play logo rather than creating a different logo.

Display the splash while the application initialises. Do not introduce unnecessary artificial loading delays or bypass the first-run wizard.

## 3. Redesign the top navigation header

Move the account/profile icon to the left-hand side of the header.

Remove:

- Yellow profile box.
- Profile initials such as V.
- Permanently displayed profile name.

Replace with:

- Recognisable universal person/profile outline icon.
- Clean white or theme-compatible colour.
- Bright pink focus border when selected using the TV remote.

Keep the search control between the profile icon and Movio logo.

Move the existing Movio Play logo to the right-hand side.

Apply consistent header styling across relevant application screens.

## 4. Profile menu

Clicking the profile icon should open a compact, modern menu displaying:

- Current profile name.
- Current account name where applicable.
- Central connection status.
- Switch profile.
- Add profile.
- Manage profile.
- Switch account.
- Log out.
- Close.

For local or guest users, display appropriate local profile information and an option to log in to Central.

Reuse existing account and profile functionality. Do not introduce duplicate profiles or change profile ownership.

The profile name should only appear when opening the menu, not permanently in the header.

## 5. Movio logo menu

Clicking the Movio logo on the right should open a small menu containing:

- About MovioXP.
- Exit MovioXP.
- Cancel.

About should open the existing About screen and preserve all current credits.

Exit should request confirmation before closing the application.

All menu options must be accessible using touch and TV remote navigation.

## 6. Fix TV search result focus

Currently, search results appear with similar blue backgrounds, making it difficult to identify the selected result when navigating using the TV remote.

Requirements:

- Bright pink border around the entire focused result.
- Focused result title displayed in pink.
- Clearly distinguish focused and unfocused results.
- Automatically scroll to keep the focused result visible.
- Predictable up/down D-pad navigation.
- Avoid unexpected keyboard activation.
- Preserve search query and results when opening movie details or playback.
- Restore the exact previous result, scroll position and focus when returning.
- Do not reset the search screen unnecessarily.

Apply consistent focus styling to buttons, dropdowns, menus and other interactive controls.

## 7. Hide unwanted TorBox catalogue rows

Hide the following three catalogue rows from Home and Discover:

- TorBox Torrents.
- TorBox Usenet.
- TorBox WebDL.

These currently appear at the bottom of Home and provide an undesirable browsing experience.

Important:

- Do not uninstall or disable the TorBox add-on.
- Do not interfere with stream resolution.
- Do not delete saved sources or add-on configuration.
- Separate catalogue visibility from add-on functionality.
- Identify catalogues using stable identifiers where possible.

Also:

- Hide empty external catalogue rows.
- Avoid displaying duplicate Cinemeta catalogue rows.
- Preserve Cinemeta metadata and search functionality.
- Keep Central catalogues as the primary default Home content.
- External catalogues should be optional.
- Reuse or improve existing Settings > Home Catalogues visibility controls.
- Allow individual catalogue rows to be enabled or disabled where supported.
- Preserve existing catalogue preferences.

## 8. Redesign movie details popup

The current movie details popup is oversized, narrow and poorly balanced on Android TV.

Current problems:

- Oversized vertically stacked buttons.
- Excessive popup height.
- Long descriptions push controls off-screen.
- Poor use of widescreen space.
- Inconsistent visual hierarchy.

Replace with a modern, balanced layout.

### Android TV and landscape layout

Use a wider, height-constrained modal.

Left-hand section:

- Movie poster.
- Appropriate poster aspect ratio.

Right-hand section:

- Movie title.
- Release year.
- Rating with star icon.
- Genre and runtime where available.
- Concise movie description.
- Expandable Read more option for longer descriptions.
- Prominent Play or Find Sources button.

Compact action buttons arranged horizontally where space permits:

- Add/remove favourite.
- Add/remove watchlist.
- Cast and crew.
- Remove from history, when applicable.

Requirements:

- Play button must remain visible.
- Close button must remain visible.
- Avoid oversized controls.
- Avoid unnecessary blank space.
- No content extending beyond the screen.
- Long content should scroll within the appropriate section.
- Use consistent spacing, rounded corners and existing Movio colours.
- Bright pink focus indicators.
- Restore focus to the originating poster after closing.
- Back button dismisses the popup.

### Mobile and tablet layout

- Responsive layout based on available screen width and height.
- Compact stacked layout where necessary.
- No clipped text or controls.
- Support portrait and landscape.
- Touch-friendly controls without excessive button sizes.

## 9. Modernise other related popups

Apply the same design principles to existing:

- Series details.
- Episode selection.
- Cast and credits.
- Playback/source selection.
- Profile menus.
- Confirmation dialogs.

Source selection should remain a separate, clearly organised interface.

Do not rewrite working playback or resolver logic merely to change its appearance.

Preserve existing actions, source selection, saved sources and playback functionality.

## 10. Fix incorrect application update notification

QA4 incorrectly displays an update notification for the original MovioTV application.

Example:

MovioTV v5.2.2 is ready (you have v1.0.84-qa4).

Requirements:

- Separate MovioXP and original MovioTV update channels.
- Only offer updates intended for MovioXP.
- Validate product identity, package compatibility and version code.
- Never offer the original MovioTV APK to MovioXP users.
- Handle unavailable update information gracefully.
- Prevent repeated incorrect update prompts.

Inspect the existing update mechanism before implementing the fix.

If a dedicated MovioXP update endpoint is unavailable, suppress incompatible notifications rather than offering an incorrect APK.

## 11. Preserve existing QA4 functionality

Do not regress:

- Central login and logout.
- Account switching.
- Profile isolation.
- Central synchronisation.
- Add-on management.
- Saved sources.
- Movie and series playback.
- Resume positions.
- Audio and subtitle preferences.
- Favourites.
- History.
- Watchlist.
- Continue Watching.
- Home catalogues.
- Discover categories.
- Catalogue sorting.
- IMDb/TMDB identity mapping.
- Search functionality.
- Persistent catalogue caching.
- Poster size and pinch-to-resize preferences.
- Existing themes.
- Show Year preferences.
- Existing 50-page / up to 1,000-movie catalogue browsing.
- Return-to-screen scroll and focus restoration.
- Existing first-run wizard.

Do not clear user data during an upgrade.

## 12. Explicitly excluded from QA5

The following features are reserved for a future release:

- Actor and actress search.
- Director search.
- Actor filmography browsing.
- Clickable cast members opening filmographies.
- Favourite actors.
- Personalised actor catalogues.
- Actor-related notifications.

Do not implement these features in QA5.

Existing cast and credits functionality should remain available, with visual improvements only.

## 13. Central server restrictions

QA5 is primarily an Android UI and stability release.

- No planned Central database changes.
- No new metadata endpoints.
- No changes to existing account/profile architecture.
- No changes to existing catalogue caching or TMDB request limits.
- No unnecessary increase in server traffic.
- Preserve existing API compatibility.

If a server-side dependency is discovered, report it rather than silently modifying Central.

## 14. Development and quality assurance

Before building:

- Inspect the current QA4 source.
- Identify reusable UI components.
- Implement shared components rather than duplicating layouts.
- Run source and precompile checks.
- Check Kotlin imports and Compose compatibility.
- Run existing automated tests.
- Add regression tests where practical.
- Review TV remote focus behaviour.
- Check responsive layout constraints.

Verify:

- Cold startup and normal startup.
- First-run wizard.
- Upgrade from QA4 without clearing data.
- Profile menu.
- Movio logo menu.
- Exit and About.
- Search result navigation.
- Search focus restoration.
- Movie details layout.
- Series and episode layouts.
- Cast and credits layout.
- Source selection.
- TorBox catalogue suppression without disabling the add-on.
- Correct application update handling.
- Central compatibility.

Test Android TV, mobile and landscape/portrait layouts wherever the available environment permits.

Do not claim physical-device testing unless actually performed.

## 15. Final build and delivery

Produce the signed MovioXP v1.0.85-qa5 release APK.

Use the existing GitHub Actions release signing workflow and secrets.

Complete the full implementation rather than delivering only a plan or partial placeholder features.

Provide:

- Final commit SHA.
- Branch name.
- GitHub Actions build link.
- Signed APK artifact download link.
- Summary of implemented changes.
- Automated test results.
- Any outstanding issues.
- Any features requiring physical-device verification.

Ensure the build succeeds and the signed APK artifact is available before reporting completion.

The objective is to retain the stability of QA4 while making MovioXP look and feel like a polished, premium streaming application.

### QA5 release summary

| Area                | Improvement                                          |
| ------------------- | ---------------------------------------------------- |
| Startup             | Black background, purple Play logo, MOVIOTV branding |
| Header              | Profile left, Movio logo right                       |
| Profile             | Modern account and profile menu                      |
| Search              | Clear pink remote focus                              |
| Home                | Hide unwanted TorBox rows                            |
| Movie details       | Modern widescreen popup                              |
| Credits and sources | Compact, balanced layouts                            |
| Updates             | Correct MovioXP update channel                       |
| Central             | No planned changes                                   |
| Build               | Signed release APK                                   |
