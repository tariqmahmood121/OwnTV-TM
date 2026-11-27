package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme

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
    selected: Int,
    focus: FocusRequester,
    onSelect: (Int) -> Unit,
    onProfile: () -> Unit,
    onMenu: () -> Unit,
    onExit: () -> Unit,
) {
    Column(Modifier.width(68.dp).fillMaxHeight().background(OwnTVTheme.colors.surfaceContainerLow).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        XpCinemaRailButton(stringResource(R.string.movioxp_account_menu), OwnTVIcon.PERSON, false, onClick = onProfile) {
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(8.dp).background(profileStatus, CircleShape))
        }
        Spacer(Modifier.weight(1f))
        val labels = listOf(R.string.moviotv_xp_nav_search, R.string.moviotv_nav_home, R.string.moviotv_xp_nav_discover,
            R.string.moviotv_nav_series, R.string.moviotv_xp_nav_library, R.string.moviotv_xp_nav_settings)
        val icons = listOf(OwnTVIcon.SEARCH, OwnTVIcon.HOME, OwnTVIcon.TREND, OwnTVIcon.SERIES, OwnTVIcon.FAVORITE, OwnTVIcon.GEAR)
        labels.forEachIndexed { index, label ->
            XpCinemaRailButton(stringResource(label), icons[index], selected == index,
                modifier = if (selected == index) Modifier.focusRequester(focus) else Modifier,
                onClick = { onSelect(index) })
        }
        Spacer(Modifier.weight(1f))
        val logo = stringResource(R.string.movioxp_logo_menu_xp)
        val brand = stringResource(R.string.moviotv_xp_title)
        XpCinemaRailButton(brand, OwnTVIcon.PLAY_CIRCLE, false, onClick = onMenu) {
            Text(logo, color = XpRemotePink, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 5.dp))
        }
        XpCinemaRailButton(stringResource(R.string.movioxp_exit), OwnTVIcon.POWER, false, onClick = onExit)
    }
}

@Composable
private fun XpCinemaRailButton(
    label: String,
    icon: OwnTVIcon,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    decoration: @Composable BoxScope.() -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    Box(modifier.size(55.dp, 37.dp).clip(shape)
        .background(if (focused) OwnTVTheme.colors.surfaceContainerHighest else Color.Transparent)
        .border(if (focused) 1.dp else 0.dp, if (focused) XpRemotePink else Color.Transparent, shape)
        .semantics { contentDescription = label }.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        OwnTVIcon(icon, if (selected) XpRemotePink else OwnTVTheme.colors.onSurface,
            Modifier.size(if (focused) 16.dp else 21.dp).align(if (focused) Alignment.TopCenter else Alignment.Center)
                .padding(top = if (focused) 2.dp else 0.dp))
        if (selected) Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(18.dp).background(XpRemotePink, CircleShape))
        if (focused) Text(label, color = OwnTVTheme.colors.onSurface, fontSize = 8.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomCenter))
        decoration()
    }
}

@Composable
internal fun XpCinemaKeyboard(value: TextFieldValue, onEdit: (TextFieldValue) -> Unit, onSubmit: () -> Unit, focus: FocusRequester) {
    val colors = OwnTVTheme.colors
    BasicTextField(value, onEdit, singleLine = true,
        keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
        textStyle = TextStyle(color = colors.onSurface, fontSize = 18.sp),
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
    val metadata = listOfNotNull(kind, details?.genres?.firstOrNull(),
        item.releaseInfo?.takeIf { showYear }, details?.runtime).joinToString(stringResource(R.string.moviotv_xp_meta_separator))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(metadata, color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
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
internal fun XpCinemaFeature(item: XpMeta, showYear: Boolean, onPlay: () -> Unit, onInfo: () -> Unit) {
    val loadDetails = LocalXpCinemaDetails.current
    val details by produceState<XpDetailMetadata?>(null, item.type, item.id, loadDetails) { value = loadDetails(item) }
    val colors = OwnTVTheme.colors
    val shape = RoundedCornerShape(20.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp)) {
        val artHeight = (maxWidth * .31f).coerceIn(188.dp, 325.dp)
        Box(Modifier.fillMaxWidth().heightIn(min = artHeight).clip(shape).background(colors.surfaceContainerLow)) {
            val backdrop = details?.backdrop ?: item.backdrop
            AsyncImage(backdrop ?: item.poster, null, Modifier.matchParentSize(),
                contentScale = if (backdrop == null) ContentScale.Fit else ContentScale.Crop, alignment = Alignment.CenterEnd)
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                listOf(colors.background, colors.background.copy(alpha = .85f), Color.Transparent))))
            Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.58f).padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name, color = colors.onSurface, fontSize = 27.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                XpCinemaMetadata(item, details, showYear, 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XpDialogButton(stringResource(if (item.positionMs > 0L) R.string.moviotv_xp_continue_watching else R.string.movioxp_play_content),
                        OwnTVIcon.PLAY, primary = true, onClick = onPlay)
                    XpDialogButton(stringResource(R.string.movioxp_cinema_more_info), OwnTVIcon.INFO, onClick = onInfo)
                }
            }
        }
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
