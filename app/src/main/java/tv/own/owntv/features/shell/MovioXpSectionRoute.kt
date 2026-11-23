package tv.own.owntv.features.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.features.home.StremioCatalogScreen
import tv.own.owntv.features.home.XpPlaybackSource

/**
 * A separate Compose method for all MovioXP catalogue destinations.
 * Keep the oversized OwnTVShell composable from compiling three almost-identical UI call sites,
 * which can exhaust ART register verifier limits even when the Gradle build passes.
 */
@Composable
internal fun MovioXpSectionRoute(
    section: MainSection,
    onBack: () -> Unit,
    onPlayStream: (String, String, String, String?, String, String?, Long) -> Unit,
    onPlayStreamCandidates: (List<XpPlaybackSource>, String, String, String?, String, String?, Long) -> Unit,
    localLiveChannels: List<ChannelEntity>,
    onPlayLocalLiveChannel: (Long) -> Unit,
    showBottomNavigation: Boolean,
    onOpenAppSettings: () -> Unit,
    onOpenSettingsGroup: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialPage = when (section) {
        MainSection.LIVE_TV -> "live"
        MainSection.MOVIES -> "movies"
        MainSection.SERIES -> "series"
        else -> "home"
    }
    StremioCatalogScreen(
        onBack = onBack,
        onPlayStream = onPlayStream,
        onPlayStreamCandidates = onPlayStreamCandidates,
        localLiveChannels = if (section == MainSection.LIVE_TV) localLiveChannels else emptyList(),
        onPlayLocalLiveChannel = onPlayLocalLiveChannel,
        initialPage = initialPage,
        showBottomNavigation = showBottomNavigation,
        onOpenAppSettings = onOpenAppSettings,
        onOpenSettingsGroup = onOpenSettingsGroup,
        modifier = modifier,
    )
}
