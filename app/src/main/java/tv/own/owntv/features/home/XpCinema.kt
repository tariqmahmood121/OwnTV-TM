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
internal val XpCinemaInter = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.inter_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

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
    onSelect: (Int) -> Unit,
    onProfile: () -> Unit,
    onSwitchProfile: (() -> Unit)?,
    onExit: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.width(if (expanded) 196.dp else 56.dp).fillMaxHeight()
        .background(XpCinemaBackground.copy(alpha = if (expanded) .97f else 1f))
        .onFocusChanged { expanded = it.hasFocus }.focusGroup().padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.Start) {
        XpCinemaRailButton(profileName, OwnTVIcon.PERSON, false, expanded, onClick = onProfile, indicator = profileStatus)
        if (expanded && onSwitchProfile != null) XpCinemaRailButton(stringResource(R.string.movioxp_switch_profile),
            OwnTVIcon.SWAP, false, expanded, onClick = onSwitchProfile)
        Spacer(Modifier.weight(1f))
        val labels = listOf(R.string.moviotv_xp_nav_search, R.string.moviotv_nav_home, R.string.moviotv_xp_nav_discover,
            R.string.moviotv_nav_series, R.string.movioxp_my_library)
        val icons = listOf(OwnTVIcon.SEARCH, OwnTVIcon.HOME, OwnTVIcon.TREND, OwnTVIcon.SERIES, OwnTVIcon.FAVORITE)
        labels.forEachIndexed { index, label ->
            XpCinemaRailButton(stringResource(label), icons[index], selected == index, expanded,
                modifier = if (selected == index) Modifier.focusRequester(focus) else Modifier,
                onClick = { onSelect(index) })
        }
        Spacer(Modifier.weight(1f))
        if (expanded) {
            val movio = stringResource(R.string.movioxp_logo_menu_movio)
            val xp = stringResource(R.string.movioxp_logo_menu_xp)
            Text(androidx.compose.ui.text.buildAnnotatedString {
                append(movio)
                pushStyle(androidx.compose.ui.text.SpanStyle(color = XpRemotePink))
                append(xp)
                pop()
            }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(9.dp, 4.dp))
        }
        XpCinemaRailButton(stringResource(R.string.movioxp_exit), OwnTVIcon.POWER, false, expanded, onClick = onExit)
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
    Row(modifier.fillMaxWidth().height(35.dp).clip(shape)
        .border(if (focused) 1.dp else 0.dp, if (focused) Color.White.copy(alpha = .8f) else Color.Transparent, shape)
        .semantics { contentDescription = label }.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp, 35.dp), contentAlignment = Alignment.Center) {
            OwnTVIcon(icon, if (selected) XpRemotePink else Color.White, Modifier.size(20.dp))
            if (selected) Box(Modifier.align(Alignment.CenterStart).width(2.dp).height(16.dp).background(XpRemotePink, CircleShape))
            if (indicator != null) Box(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 6.dp)
                .size(6.dp).background(indicator, CircleShape))
        }
        if (expanded) Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun XpCinemaKeyboard(value: TextFieldValue, onEdit: (TextFieldValue) -> Unit, onSubmit: () -> Unit, focus: FocusRequester) {
    val colors = OwnTVTheme.colors
    BasicTextField(value, onEdit, singleLine = true,
        keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
        textStyle = TextStyle(color = colors.onSurface, fontSize = 18.sp, fontFamily = XpCinemaInter),
        modifier = Modifier.fillMaxWidth().background(colors.surfaceContainerLow, RoundedCornerShape(8.dp)).padding(8.dp),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) Text(stringResource(R.string.moviotv_xp_search_hint), color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                inner()
            }
        })
    val rows = listOf("ABCDEF", "GHIJKL", "MNOPQR", "STUVWX", "YZ1234", "567890", ".,'&-:")
    rows.forEachIndexed { row, letters ->
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            letters.forEachIndexed { column, letter ->
                XpCinemaKey(letter.toString(), Modifier.weight(1f).then(
                    if (row == 0 && column == 0) Modifier.focusRequester(focus) else Modifier)) {
                    onEdit(xpCinemaEdit(value, letter.toString()))
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        XpCinemaKey(stringResource(R.string.movioxp_cinema_space), Modifier.weight(1f)) { onEdit(xpCinemaEdit(value, " ")) }
        XpCinemaKey(stringResource(R.string.movioxp_cinema_backspace), Modifier.weight(1f)) { onEdit(xpCinemaEdit(value, null)) }
        XpCinemaKey(stringResource(R.string.movioxp_search_clear), Modifier.weight(1f)) { onEdit(TextFieldValue()) }
    }
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
        Text(label, color = colors.onSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun XpCinemaMetadata(item: XpMeta, details: XpDetailMetadata?, showYear: Boolean, synopsisLines: Int) {
    val muted = OwnTVTheme.colors.onSurfaceVariant
    val kind = stringResource(if (item.type == "series") R.string.moviotv_nav_series else R.string.movioxp_cinema_film)
    val metadata = listOfNotNull(kind, (details?.genres?.firstOrNull() ?: item.genre),
        xpReleaseYear(item)?.takeIf { showYear }, details?.runtime ?: item.runtime).joinToString(stringResource(R.string.moviotv_xp_meta_separator))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(metadata, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            xpCinemaCertificate(details?.ukCertificate ?: item.ukCertificate)?.let { code ->
                val color = when (code) {
                    "U" -> Color(0xFF23964C)
                    "PG" -> Color(0xFF65A844)
                    "12", "12A" -> Color(0xFFDFA326)
                    "15" -> Color(0xFFDC414B)
                    else -> Color(0xFFA91E32)
                }
                Box(Modifier.size(25.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                    Text(code, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            item.rating?.takeIf { item.ratingSource == null || item.ratingSource.equals("IMDb", true) }
                ?.takeIf { it.toDoubleOrNull()?.let { number -> number in 0.0..10.0 } == true }?.let { rating ->
                    Text(stringResource(R.string.movioxp_cinema_rating, rating), color = Color(0xFFFFD775), fontSize = 12.sp, maxLines = 1)
                }
        }
        (details?.synopsis ?: item.description)?.takeIf(String::isNotBlank)?.let {
            Text(it, color = muted, fontSize = 12.sp, maxLines = synopsisLines, overflow = TextOverflow.Ellipsis)
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
    BoxWithConstraints(Modifier.fillMaxWidth().onFocusChanged { onFocus(it.hasFocus) }.focusGroup()) {
        Box(Modifier.fillMaxWidth().heightIn(min = height)) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(.64f).widthIn(max = 560.dp)
                .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val logo = details?.titleLogo ?: item.titleLogo
                if (logo != null) AsyncImage(logo, item.name, Modifier.width(205.dp).height(63.dp),
                    contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                else Text(item.name, color = Color.White, fontSize = 39.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                XpCinemaMetadata(item, details, showYear, 3)
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
private fun XpCinemaFeatureButton(label: String, icon: OwnTVIcon, primary: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val foreground = if (primary) XpCinemaBackground else Color.White
    Row(Modifier.heightIn(min = 36.dp).clip(shape)
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
    val details by produceState<XpDetailMetadata?>(null, item.id, item.type, load) {
        value = null
        delayFocus()
        value = load(item)
    }
    val showYear = LocalXpPosterDisplay.current.second
    val artwork = details?.backdrop ?: item.backdrop
    Row(Modifier.fillMaxWidth().heightIn(max = 120.dp).clip(RoundedCornerShape(11.dp))
        .background(OwnTVTheme.colors.surfaceContainerLow).padding(8.dp),
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

private suspend fun delayFocus() = kotlinx.coroutines.delay(400)
