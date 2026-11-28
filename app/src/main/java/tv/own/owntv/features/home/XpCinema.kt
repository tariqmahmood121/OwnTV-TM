package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.dissolveEdges
import tv.own.owntv.ui.stage.backdropLeftWash

internal val XpCinemaBackground = Color(0xFF0C0C11)
private fun cinemaVariableFont(resource: Int) = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
        .map { Font(resource, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight))) },
)

internal enum class XpCinemaFont(val label: Int, val family: FontFamily) {
    MONTSERRAT(R.string.movioxp_font_montserrat, cinemaVariableFont(R.font.montserrat_variable)),
    INTER(R.string.movioxp_font_inter, cinemaVariableFont(R.font.inter_variable)),
    POPPINS(R.string.movioxp_font_poppins, FontFamily(
        Font(R.font.poppins_regular), Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_semibold, FontWeight.SemiBold), Font(R.font.poppins_bold, FontWeight.Bold),
        Font(R.font.poppins_extrabold, FontWeight.ExtraBold),
    )),
    MANROPE(R.string.movioxp_font_manrope, cinemaVariableFont(R.font.manrope_variable)),
    DM_SANS(R.string.movioxp_font_dm_sans, cinemaVariableFont(R.font.dmsans_variable)),
    SYSTEM(R.string.movioxp_font_system, FontFamily.Default);

    companion object {
        fun restore(value: String?) = entries.firstOrNull { it.name == value } ?: MONTSERRAT
    }
}

internal fun xpNextFeatured(index: Int, count: Int): Int =
    if (count <= 1) 0 else ((index.coerceAtLeast(0) % count) + 1) % count

internal fun xpCinemaLandscape(backdrop: String?, poster: String?): String? =
    backdrop?.trim()?.takeIf { it.isNotEmpty() && it != poster?.trim() }

internal val LocalXpCinema = staticCompositionLocalOf { false }
internal val LocalXpCinemaDetails = staticCompositionLocalOf<suspend (XpMeta) -> XpDetailMetadata?> { { null } }

internal fun xpCinemaEdit(value: TextFieldValue, replacement: String?): TextFieldValue {
    val end = value.selection.max
    val start = if (replacement == null && end == value.selection.min && end > 0)
        value.text.offsetByCodePoints(end, -1) else value.selection.min
    val next = replacement.orEmpty()
    val candidate = value.text.replaceRange(start, end, next)
    if (candidate.length > 160) return value
    return TextFieldValue(candidate, TextRange(start + next.length))
}

internal fun xpCinemaCertificate(raw: String?): String? =
    raw?.trim()?.uppercase(java.util.Locale.UK)?.takeIf { it in setOf("U", "PG", "12", "12A", "15", "18") }

@Composable
internal fun XpCinemaRail(
    profileStatus: Color,
    profileName: String,
    selected: Int,
    focus: FocusRequester,
    onMenuFocus: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    onProfile: () -> Unit,
    onSwitchProfile: (() -> Unit)?,
    onExit: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val labels = listOf(R.string.moviotv_xp_nav_search, R.string.moviotv_nav_home, R.string.moviotv_xp_nav_discover,
        R.string.moviotv_nav_series, R.string.movioxp_my_library, R.string.moviotv_xp_nav_settings)
    val icons = listOf(OwnTVIcon.SEARCH, OwnTVIcon.HOME, OwnTVIcon.TREND, OwnTVIcon.SERIES, OwnTVIcon.FAVORITE, OwnTVIcon.GEAR)
    Box(Modifier.width(if (expanded) 210.dp else 56.dp).fillMaxHeight().padding(5.dp)) {
        val shape = RoundedCornerShape(16.dp)
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF25252B), Color(0xFF0B0B10))))
            .onFocusChanged {
                expanded = it.hasFocus
                onMenuFocus(it.hasFocus)
            }.focusRestorer().focusGroup()
            .testTag("cinema-navigation").padding(3.dp)) {
            if (expanded) {
                XpCinemaRailButton(profileName, OwnTVIcon.PERSON, false, true, modifier = Modifier.testTag("cinema-profile"),
                    onClick = onProfile, indicator = profileStatus)
                if (onSwitchProfile != null) XpCinemaRailButton(stringResource(R.string.movioxp_switch_profile),
                    OwnTVIcon.SWAP, false, true, onClick = onSwitchProfile)
            }
            labels.forEachIndexed { index, label ->
                if (expanded || index == 0 || index == 2 || index == 4) {
                    val active = selected == index || !expanded && index == 2 && selected in 1..3
                    XpCinemaRailButton(stringResource(label), icons[index], active, expanded,
                        modifier = if (active) Modifier.focusRequester(focus) else Modifier,
                        onClick = {
                            onSelect(index)
                            focusManager.moveFocus(FocusDirection.Right)
                            expanded = false
                        })
                }
            }
            if (expanded) XpCinemaRailButton(stringResource(R.string.movioxp_exit), OwnTVIcon.POWER, false, true, onClick = onExit)
        }
        androidx.activity.compose.BackHandler(enabled = expanded) { focusManager.moveFocus(FocusDirection.Right); expanded = false }
        if (!expanded) Box(Modifier.align(Alignment.TopStart).width(46.dp)) {
            XpCinemaRailButton(profileName, OwnTVIcon.PERSON, false, false,
                modifier = Modifier.testTag("cinema-profile"), onClick = onProfile, indicator = profileStatus)
        }
    }
}

@Composable
private fun XpCinemaRailButton(
    label: String,
    icon: OwnTVIcon,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    indicator: Color? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val active = selected && expanded
    val foreground = if (active) XpCinemaBackground else Color.White
    Row(modifier.fillMaxWidth().height(35.dp).clip(shape)
        .background(if (active) Color.White else Color.Transparent)
        .border(if (focused) 2.dp else 0.dp, if (focused) XpRemotePink else Color.Transparent, shape)
        .semantics { contentDescription = label }.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp, 35.dp), contentAlignment = Alignment.Center) {
            OwnTVIcon(icon, if (selected) XpRemotePink else foreground, Modifier.size(20.dp))
            if (selected) Box(Modifier.align(Alignment.CenterStart).width(2.dp).height(16.dp).background(XpRemotePink, CircleShape))
            if (indicator != null) Box(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 6.dp)
                .size(6.dp).background(indicator, CircleShape))
        }
        if (expanded) Text(label, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun XpCinemaKeyboard(value: TextFieldValue, onEdit: (TextFieldValue) -> Unit, onSubmit: () -> Unit, focus: FocusRequester) {
    val colors = OwnTVTheme.colors
    BasicTextField(value, onEdit, singleLine = true,
        keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
        textStyle = TextStyle(color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = tv.own.owntv.ui.theme.LocalMainFontFamily.current),
        modifier = Modifier.fillMaxWidth().background(colors.surfaceContainerLow, RoundedCornerShape(8.dp)).padding(8.dp),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) Text(stringResource(R.string.moviotv_xp_search_hint), color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                inner()
            }
        })
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        XpCinemaKey(stringResource(R.string.movioxp_cinema_space), Modifier.weight(1f)) { onEdit(xpCinemaEdit(value, " ")) }
        XpCinemaKey(stringResource(R.string.movioxp_cinema_delete), Modifier.weight(1f)) { onEdit(xpCinemaEdit(value, null)) }
    }
    listOf("abcdef", "ghijkl", "mnopqr", "stuvwx", "yz1234", "567890").forEachIndexed { row, letters ->
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            letters.forEachIndexed { column, letter ->
                XpCinemaKey(letter.toString(), Modifier.weight(1f).then(
                    if (row == 0 && column == 0) Modifier.focusRequester(focus) else Modifier)) {
                    onEdit(xpCinemaEdit(value, letter.toString()))
                }
            }
        }
    }
    XpCinemaKey(stringResource(R.string.movioxp_search_clear), Modifier.fillMaxWidth()) { onEdit(TextFieldValue()) }
    XpAction(stringResource(R.string.moviotv_xp_nav_search), value.text.isBlank(), compact = true, onClick = onSubmit)
}

@Composable
private fun XpCinemaKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = OwnTVTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.heightIn(min = 32.dp).clip(shape)
        .background(if (focused) colors.primaryContainer else colors.surfaceContainerLow)
        .border(if (focused) 1.dp else 0.dp, if (focused) XpRemotePink else Color.Transparent, shape)
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(3.dp), contentAlignment = Alignment.Center) {
        Text(label, color = colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun XpCinemaMetadata(item: XpMeta, details: XpDetailMetadata?, showYear: Boolean, synopsisLines: Int) {
    val muted = OwnTVTheme.colors.onSurfaceVariant
    val kind = stringResource(if (item.type == "series") R.string.moviotv_nav_series else R.string.movioxp_cinema_film)
    val metadata = listOfNotNull(kind, (details?.genres?.firstOrNull() ?: item.genre),
        xpReleaseYear(item)?.takeIf { showYear }, if (item.type == "series") details?.seasonCount?.let {
            androidx.compose.ui.res.pluralStringResource(R.plurals.movioxp_cinema_seasons, it, it)
        } else (details?.runtime ?: item.runtime)?.let { runtime ->
            runtime.trim().removeSuffix(" min").trim().toIntOrNull()?.let { minutes ->
                if (minutes >= 60) stringResource(R.string.movioxp_cinema_hours_minutes, minutes / 60, minutes % 60)
                else stringResource(R.string.movioxp_runtime_minutes, minutes)
            } ?: runtime
        }).joinToString(stringResource(R.string.movioxp_cinema_metadata_separator))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(metadata, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            xpCinemaCertificate(details?.ukCertificate ?: item.ukCertificate)?.let { code ->
                val color = when (code) {
                    "U" -> Color(0xFF23964C)
                    "PG" -> Color(0xFF65A844)
                    "12", "12A" -> Color(0xFFDFA326)
                    "15" -> Color(0xFFDC414B)
                    else -> Color(0xFFA91E32)
                }
                val diameter = with(androidx.compose.ui.platform.LocalDensity.current) { 21.sp.toDp() }
                Box(Modifier.padding(start = 12.dp).size(diameter).background(color, CircleShape), contentAlignment = Alignment.Center) {
                    Text(code, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            (details?.imdbRating ?: xpCinemaImdbRating(item))
                ?.takeIf { it.toDoubleOrNull()?.let { number -> number in 0.0..10.0 } == true }?.let { rating ->
                    Text(stringResource(R.string.movioxp_cinema_rating, rating), color = Color(0xFFFFD775), fontSize = 12.sp, maxLines = 1)
                }
        }
        (details?.synopsis ?: item.description)?.takeIf(String::isNotBlank)?.let {
            Text(it, color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = synopsisLines, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun XpCinemaHomeBackdrop(backdrop: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(modifier) {
        androidx.compose.animation.Crossfade(backdrop,
            modifier = Modifier.matchParentSize().dissolveEdges(start = .22f, bottom = .38f),
            animationSpec = androidx.compose.animation.core.tween(220)) { wide ->
            var loaded by remember(wide) { mutableStateOf(false) }
            val artwork = Modifier.fillMaxSize().then(
                if (loaded) Modifier.testTag("cinema-backdrop-loaded") else Modifier,
            )
            val request = remember(context, wide) { xpHeroRequest(context, wide) }
            AsyncImage(request, null, artwork, contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd, onSuccess = { loaded = true })
        }
        Box(Modifier.matchParentSize().backdropLeftWash(
            0f to .86f, .29f to .57f, .64f to .12f, .9f to 0f))
    }
}

@Composable
internal fun XpCinemaFeature(item: XpMeta, showYear: Boolean, onFocus: (Boolean) -> Unit, onPlay: () -> Unit, onInfo: () -> Unit, height: androidx.compose.ui.unit.Dp) {
    val loadDetails = LocalXpCinemaDetails.current
    val details by produceState<XpDetailMetadata?>(null, item.type, item.id, loadDetails) { value = null; value = loadDetails(item) }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("cinema-feature-content").onFocusChanged { onFocus(it.hasFocus) }.focusGroup()) {
        Box(Modifier.fillMaxWidth().heightIn(min = height)) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(.64f).widthIn(max = 560.dp)
                .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.animation.Crossfade(item to details,
                    animationSpec = androidx.compose.animation.core.tween(220)) { (featured, metadata) ->
                    Column(Modifier.semantics { contentDescription = featured.name },
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val logo = metadata?.titleLogo ?: featured.titleLogo
                        if (logo != null) AsyncImage(logo, featured.name, Modifier.width(205.dp).height(63.dp),
                            contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                        else Text(featured.name, color = Color.White, fontSize = 39.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        XpCinemaMetadata(featured, metadata, showYear, 3)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XpCinemaFeatureButton(stringResource(if (item.positionMs > 0L) R.string.movioxp_cinema_resume else R.string.movioxp_play_content),
                        OwnTVIcon.PLAY, primary = true, onClick = onPlay)
                    XpCinemaFeatureButton(stringResource(R.string.movioxp_cinema_more_info), OwnTVIcon.INFO, onClick = onInfo)
                }
            }
        }
    }
}

@Composable
private fun XpCinemaFeatureButton(label: String, icon: OwnTVIcon, primary: Boolean = false,
    minimumHeight: androidx.compose.ui.unit.Dp = 36.dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val foreground = if (primary) XpCinemaBackground else Color.White
    Row(Modifier.heightIn(min = minimumHeight).clip(shape)
        .background(if (primary) Color.White else Color.Black.copy(alpha = .56f))
        .border(1.dp, if (focused) if (primary) XpRemotePink else Color.White else Color.Transparent, shape)
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .padding(horizontal = 13.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        OwnTVIcon(icon, foreground, Modifier.size(15.dp))
        Text(label, color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
internal fun XpCinemaFocusedPreview(item: XpMeta) {
    val load = LocalXpCinemaDetails.current
    var settled by remember(item.id, item.type) { mutableStateOf(false) }
    LaunchedEffect(item.id, item.type) {
        delayFocus()
        settled = true
    }
    val details by produceState<XpDetailMetadata?>(null, item.id, item.type, load, settled) {
        value = if (settled) load(item) else null
    }
    if (!settled) return
    val showYear = LocalXpPosterDisplay.current.second
    val artwork = details?.backdrop ?: item.backdrop
    Row(Modifier.fillMaxWidth().heightIn(max = 120.dp).clip(RoundedCornerShape(11.dp))
        .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AsyncImage(artwork ?: item.poster, null, Modifier.width(130.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)),
            contentScale = if (artwork == null) ContentScale.Fit else ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(item.name, color = OwnTVTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            XpCinemaMetadata(item, details, showYear, 2)
        }
    }
}

internal const val XP_CINEMA_FOCUS_DELAY = 1_500L

private suspend fun delayFocus() = kotlinx.coroutines.delay(XP_CINEMA_FOCUS_DELAY)

@Composable
internal fun XpTouchHomeHero(item: XpMeta, compact: Boolean, onPlay: () -> Unit, onInfo: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(if (compact) 400.dp else 320.dp)
        .clip(RoundedCornerShape(14.dp)).background(XpCinemaBackground).testTag("touch-home-feature")) {
        val artwork = if (compact) item.poster ?: item.backdrop else item.backdrop ?: item.poster
        AsyncImage(artwork, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
            0.28f to Color.Transparent, 1f to XpCinemaBackground)))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.name, color = Color.White, fontSize = if (compact) 25.sp else 34.sp,
                fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            XpCinemaMetadata(item, null, true, if (compact) 1 else 2)
            val actions: @Composable () -> Unit = {
                XpCinemaFeatureButton(stringResource(R.string.movioxp_play_content), OwnTVIcon.PLAY,
                    primary = true, minimumHeight = 48.dp, onClick = onPlay)
                XpCinemaFeatureButton(stringResource(R.string.movioxp_cinema_more_info), OwnTVIcon.INFO,
                    minimumHeight = 48.dp, onClick = onInfo)
            }
            if (compact) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
}
