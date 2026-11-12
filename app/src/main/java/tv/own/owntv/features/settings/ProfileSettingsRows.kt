package tv.own.owntv.features.settings

import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.features.profiles.ProfileEditorDialog
import tv.own.owntv.features.profiles.ProfileGateSessionViewModel
import tv.own.owntv.features.profiles.ProfilesViewModel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent

private const val ADD_ROW = -1L

/** How many profiles Settings › Profile lists, for the page's count. */
@Composable
internal fun profileCount(): Int {
    val vm: ProfilesViewModel = koinViewModel()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    return profiles.size
}

/**
 * Settings › Profile: the profiles themselves, then Add a profile. OK edits a profile (the profile
 * editor), a held OK offers to delete it while more than one exists. The panel explains
 * profiles as a whole.
 */
@Composable
internal fun ProfileSettingsRows(
    onOpenSync: () -> Unit = {},
    onOpenSources: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenLayout: () -> Unit = {},
    onOpenAllSettings: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
) {
    val vm: ProfilesViewModel = koinViewModel()
    val gateSession: ProfileGateSessionViewModel = koinViewModel()
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val activeProfileId by vm.activeProfileId.collectAsStateWithLifecycle()
    val defaultProfileName = stringResource(R.string.profiles_default_name)
    val visualSettingsVm: SettingsViewModel = koinViewModel()
    val visualAccent by visualSettingsVm.accent.collectAsStateWithLifecycle()
    val visualCustom by visualSettingsVm.customAccent.collectAsStateWithLifecycle()
    val currentVisualPreset = if (visualAccent == AccentColor.VIOLET && visualCustom.isBlank()) "PURPLE" else "CLASSIC"
    val linkedSources by visualSettingsVm.sources.collectAsStateWithLifecycle()
    val defaultSourceId by visualSettingsVm.defaultSourceId.collectAsStateWithLifecycle()
    val currentSource = linkedSources.firstOrNull { it.id == defaultSourceId } ?: linkedSources.firstOrNull()
    val sourceSyncFlow = remember(currentSource?.id) {
        currentSource?.let { visualSettingsVm.syncState(it.id) }
            ?: kotlinx.coroutines.flow.flowOf<tv.own.owntv.core.sync.work.CatalogSyncState>(
                tv.own.owntv.core.sync.work.CatalogSyncState.Idle
            )
    }
    val sourceSync by sourceSyncFlow.collectAsStateWithLifecycle(
        tv.own.owntv.core.sync.work.CatalogSyncState.Idle
    )
    var showDiagnostics by remember { mutableStateOf(false) }
    val diagnosis by visualSettingsVm.sourceTest.collectAsStateWithLifecycle()
    var showVisualPicker by remember { mutableStateOf(false) }
    var showRatingPicker by remember { mutableStateOf(false) }
    val movioMovieOptions = tv.own.owntv.features.home.MovioMoviePrefs.get(context, activeProfileId)
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ProfileEntity?>(null) }
    // Editing or deleting a protected profile from another one asks one of its PINs first:
    // the profile and what opens once it is right.
    var pinFirst by remember { mutableStateOf<Pair<ProfileEntity, () -> Unit>?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val guarded: (ProfileEntity, () -> Unit) -> Unit = { p, open ->
        scope.launch { if (vm.changeNeedsPin(p, activeProfileId)) pinFirst = p to open else open() }
    }
    // A closing editor or confirmation hands focus back to the row it came from (Add a profile, or the
    // profile — its neighbour when it was deleted).
    val rowFocus = remember { HashMap<Long, FocusRequester>() }
    val addFocus = remember { FocusRequester() }
    var returnTo by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(editing, creating, confirmDelete, pinFirst, profiles) {
        if (editing != null || creating || confirmDelete != null || pinFirst != null) return@LaunchedEffect
        val id = returnTo ?: return@LaunchedEffect
        withFrameNanos { }
        val target = rowFocus[id].takeIf { profiles.any { it.id == id } } ?: profiles.lastOrNull()?.let { rowFocus[it.id] } ?: addFocus
        if (id == ADD_ROW) runCatching { addFocus.requestFocus() } else runCatching { target.requestFocus() }
        returnTo = null
    }

    val help = SettingHelp(
        title = stringResource(R.string.profiles_title),
        text = stringResource(R.string.settings_help_profiles),
        hints = listOfNotNull(
            stringResource(R.string.common_ok) to stringResource(R.string.common_edit),
            // The only way to delete a profile, so it has to be shown; never for the last one.
            if (profiles.size > 1) stringResource(R.string.content_key_hold_ok) to stringResource(R.string.common_delete) else null,
            stringResource(R.string.common_back) to stringResource(R.string.common_nav_settings),
        ),
    )
    val sep = stringResource(R.string.content_epg_bits_separator)
    // A simple two-choice MovioTV appearance picker under Settings > Profile.
    StageSettingRow(
        icon = OwnTVIcon.PALETTE,
        title = stringResource(R.string.moviotv_visual_theme),
        desc = stringResource(R.string.moviotv_visual_theme_desc),
        value = SettingValue.Choice(stringResource(
            if (currentVisualPreset == "PURPLE") R.string.moviotv_purple_theme
            else R.string.moviotv_classic_theme
        )),
        onClick = { showVisualPicker = true },
        help = SettingHelp(
            title = stringResource(R.string.moviotv_visual_theme),
            text = stringResource(R.string.moviotv_visual_theme_desc),
        ),
    )
    // V7.7: per-profile poster and movie-opening behaviour.
    StageSettingRow(
        icon = OwnTVIcon.PALETTE,
        title = stringResource(R.string.moviotv_rating_size_title),
        desc = stringResource(R.string.moviotv_rating_size_desc),
        value = SettingValue.Choice(stringResource(when (movioMovieOptions.ratingSize) {
            "MEDIUM" -> R.string.moviotv_rating_medium
            "LARGE" -> R.string.moviotv_rating_large
            else -> R.string.moviotv_rating_small
        })),
        onClick = { showRatingPicker = true },
    )
    StageSettingRow(
        icon = OwnTVIcon.MOVIES,
        title = stringResource(R.string.moviotv_show_movie_year),
        desc = stringResource(R.string.moviotv_show_movie_year_desc),
        value = SettingValue.Choice(stringResource(if (movioMovieOptions.showYear) R.string.common_on else R.string.common_off)),
        onClick = { tv.own.owntv.features.home.MovioMoviePrefs.save(context, activeProfileId,
            movioMovieOptions.copy(showYear = !movioMovieOptions.showYear)) },
    )
    StageSettingRow(
        icon = OwnTVIcon.PLAY,
        title = stringResource(R.string.moviotv_details_first),
        desc = stringResource(R.string.moviotv_details_first_desc),
        value = SettingValue.Choice(stringResource(if (movioMovieOptions.detailsFirst) R.string.common_on else R.string.common_off)),
        onClick = { tv.own.owntv.features.home.MovioMoviePrefs.save(context, activeProfileId,
            movioMovieOptions.copy(detailsFirst = !movioMovieOptions.detailsFirst)) },
    )
    // A visible account card and a real one-tap normal refresh for the current playlist.
    // Clean resync is deliberately NOT used here: keep provider-shrink safeguards intact.
    StageSettingRow(
        icon = OwnTVIcon.PLAYLIST,
        title = stringResource(R.string.moviotv_profile_source_title),
        desc = stringResource(R.string.moviotv_profile_source_desc),
        value = SettingValue.Choice(currentSource?.name ?: stringResource(R.string.moviotv_no_provider)),
        onClick = onOpenSources,
    )
    StageSettingRow(
        icon = OwnTVIcon.REFRESH,
        title = stringResource(R.string.moviotv_profile_sources_title),
        desc = if (sourceSync.isActive) stringResource(R.string.moviotv_profile_refresh_running)
            else stringResource(R.string.moviotv_profile_refresh_desc),
        value = null,
        onClick = {
            when {
                currentSource == null -> onOpenSources()
                sourceSync.isActive -> onOpenSources()
                else -> visualSettingsVm.resync(currentSource)
            }
        },
    )
    // A read-only provider/authentication check, plus device networking/VPN status in its report.
    StageSettingRow(
        icon = OwnTVIcon.INFO,
        title = stringResource(R.string.moviotv_diagnostics_title),
        desc = stringResource(R.string.moviotv_diagnostics_description),
        value = null,
        onClick = {
            if (currentSource == null) onOpenSources()
            else { showDiagnostics = true; visualSettingsVm.testSource(currentSource) }
        },
    )
    if (showDiagnostics && diagnosis != null) {
        SourceTestDialog(
            state = diagnosis!!,
            onDismiss = { showDiagnostics = false; visualSettingsVm.dismissSourceTest() },
        )
    }
    // Device pairing and display preferences sit together in the personal dashboard.
    StageSettingRow(
        icon = OwnTVIcon.BACKUP,
        title = stringResource(R.string.moviotv_profile_sync_title),
        desc = stringResource(R.string.moviotv_profile_sync_desc),
        value = null,
        onClick = onOpenSync,
    )
    StageSettingRow(
        icon = OwnTVIcon.PALETTE,
        title = stringResource(R.string.moviotv_profile_gui_title),
        desc = stringResource(R.string.moviotv_profile_gui_desc),
        value = null,
        onClick = onOpenAppearance,
    )
    StageSettingRow(
        icon = OwnTVIcon.LIST_GRID,
        title = stringResource(R.string.moviotv_profile_layout_title),
        desc = stringResource(R.string.moviotv_profile_layout_desc),
        value = null,
        onClick = onOpenLayout,
    )
    StageSettingRow(
        icon = OwnTVIcon.SETTINGS,
        title = stringResource(R.string.moviotv_profile_all_settings_title),
        desc = stringResource(R.string.moviotv_profile_all_settings_desc),
        value = null,
        onClick = onOpenAllSettings,
    )
    StageSettingRow(
        icon = OwnTVIcon.PERSON,
        title = stringResource(R.string.moviotv_profile_switch_title),
        desc = stringResource(R.string.moviotv_profile_switch_desc),
        value = null,
        onClick = onSwitchProfile,
    )
    profiles.forEach { p ->
        val inUse = p.id == activeProfileId
        val line = listOfNotNull(
            if (inUse) stringResource(R.string.settings_profile_in_use) else null,
            if (p.isKids) stringResource(R.string.settings_profile_kids) else null,
            stringResource(if (p.pinHash != null) R.string.settings_profile_pin_lock else R.string.settings_profile_no_lock),
        ).joinToString(sep)
        val inUseTag = stringResource(R.string.settings_profile_in_use).uppercase()
        val kidsTag = stringResource(R.string.profiles_kids_tag).uppercase()
        val pinTag = stringResource(R.string.profiles_pin).uppercase()
        val accent = stageAccent.accent
        StageSettingRow(
            icon = OwnTVIcon.PERSON,
            title = p.name,
            desc = line,
            value = SettingValue.Custom {
                Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
                    if (inUse) StageTag(inUseTag, tint = accent)
                    if (p.isKids) StageTag(kidsTag)
                    if (p.pinHash != null) StageTag(pinTag)
                    if (!inUse) OwnTVIcon(OwnTVIcon.CHEVRON, StageColors.Muted, Modifier.size(20.mpx))
                }
            },
            onClick = { returnTo = p.id; guarded(p) { editing = p } },
            onLongClick = if (profiles.size > 1) ({ returnTo = p.id; guarded(p) { confirmDelete = p } }) else null,
            help = help,
            modifier = Modifier.focusRequester(remember(p.id) { rowFocus.getOrPut(p.id) { FocusRequester() } }),
        )
    }
    StageSettingRow(
        icon = OwnTVIcon.SPARKLE,
        title = stringResource(R.string.settings_profile_add),
        desc = stringResource(R.string.settings_profile_add_line),
        value = null,
        onClick = { returnTo = ADD_ROW; creating = true },
        help = help,
        modifier = Modifier.focusRequester(addFocus),
    )

    if (showVisualPicker) {
        PickerDialog(
            title = stringResource(R.string.moviotv_visual_theme),
            options = listOf(
                "PURPLE" to stringResource(R.string.moviotv_purple_theme),
                "CLASSIC" to stringResource(R.string.moviotv_classic_theme),
            ),
            selected = currentVisualPreset,
            onSelect = { choice ->
                // Only visual tokens change; all player and SQLite state stays untouched.
                visualSettingsVm.setThemeMode(ThemeMode.DARK)
                visualSettingsVm.setCustomAccent("")
                visualSettingsVm.setFocusHighlight("")
                visualSettingsVm.setAccent(if (choice == "PURPLE") AccentColor.VIOLET else AccentColor.TEAL)
                // Preference scoped to the active profile. Only visual choices are stored here.
                activeProfileId?.takeIf { it >= 0L }?.let { profileId ->
                    context.getSharedPreferences("moviotv_profile_theme_v72", android.content.Context.MODE_PRIVATE)
                        .edit().putString("profile_$profileId", choice).apply()
                }
                showVisualPicker = false
            },
            onDismiss = { showVisualPicker = false },
            descriptions = mapOf(
                "PURPLE" to stringResource(R.string.moviotv_purple_desc),
                "CLASSIC" to stringResource(R.string.moviotv_classic_desc),
            ),
        )
    }

    if (showRatingPicker) {
        PickerDialog(
            title = stringResource(R.string.moviotv_rating_size_title),
            options = listOf(
                "SMALL" to stringResource(R.string.moviotv_rating_small),
                "MEDIUM" to stringResource(R.string.moviotv_rating_medium),
                "LARGE" to stringResource(R.string.moviotv_rating_large),
            ),
            selected = movioMovieOptions.ratingSize,
            onSelect = { selected ->
                tv.own.owntv.features.home.MovioMoviePrefs.save(context, activeProfileId,
                    movioMovieOptions.copy(ratingSize = selected))
                showRatingPicker = false
            },
            onDismiss = { showRatingPicker = false },
        )
    }

    if (creating) {
        ProfileEditorDialog(
            initial = null,
            onConfirm = { name, avatarId, isKids, pin, picture -> vm.create(name, avatarId, isKids, pin, defaultProfileName, picture); creating = false },
            onDismiss = { creating = false },
            // Names must stay unique (backup restore matches profiles by name).
            takenNames = profiles.map { it.name.trim().lowercase() }.toSet(),
        )
    }
    editing?.let { p ->
        val allowDownloads by remember(p.id) { vm.downloadsAllowed(p.id) }.collectAsStateWithLifecycle(null)
        ProfileEditorDialog(
            initial = p,
            onConfirm = { name, avatarId, isKids, pin, _ -> vm.edit(p, name, avatarId, isKids, pin); editing = null },
            onDismiss = { editing = null },
            takenNames = profiles.filter { it.id != p.id }.map { it.name.trim().lowercase() }.toSet(),
            // From the live list, not [p]: [p] is the row as it was when the dialog opened, and a
            // picture chosen here applies at once.
            customPath = profiles.firstOrNull { it.id == p.id }?.avatarPath.orEmpty(),
            onPickPicture = { file -> vm.setPicture(p.id, file) },
            onClearPicture = { vm.clearPicture(p.id) },
            allowDownloads = allowDownloads,
            onAllowDownloads = { vm.setDownloadsAllowed(p.id, it) },
        )
    }
    pinFirst?.let { (p, open) ->
        tv.own.owntv.features.profiles.ProtectedProfilePinDialog(
            profileName = p.name,
            body = stringResource(R.string.profiles_protected_edit),
            verify = { pin -> vm.verifyProtected(p, pin) },
            onUnlocked = { pinFirst = null; open() },
            onDismiss = { pinFirst = null },
        )
    }
    confirmDelete?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.profiles_delete_title, p.name),
            message = stringResource(R.string.profiles_delete_message),
            onConfirm = {
                // Only an active-profile deletion changes the identity this Activity authenticated.
                gateSession.invalidateIfDeletingActiveProfile(p.id, activeProfileId)
                vm.delete(p)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
            // Opened by a held OK: without the guard its repeats press Cancel, so the confirmation
            // closed the moment it appeared and a profile could never be deleted.
            modifier = Modifier.longPressMenuGuard(),
        )
    }
}
