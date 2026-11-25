package tv.own.owntv.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.features.home.XpCentralProfileScope
import tv.own.owntv.features.home.XpCentralConnectionStore
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * The "Who's watching?" launch gate. Shown when more than one profile exists. Picking a
 * profile makes it active (and prompts for a PIN if it's locked); [onEnter] records the selected
 * profile id and proceeds into the shell.
 */
@Composable
fun ProfileGate(onEnter: (Long) -> Unit, onAddProfile: () -> Unit, modifier: Modifier = Modifier) {
    val vm: ProfilesViewModel = koinViewModel()
    val allProfiles by vm.profiles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val centralAccountId by XpCentralProfileScope.accountId.collectAsStateWithLifecycle()
    val membershipRevision by XpCentralProfileScope.revision.collectAsStateWithLifecycle()
    val linkedLocalIds = remember(membershipRevision) { XpCentralProfileScope.linkedLocalIds(context) }
    val profiles = if (centralAccountId == null) allProfiles.filterNot { it.id in linkedLocalIds } else {
        val allowed = XpCentralProfileScope.allowedLocalIds(context, centralAccountId!!)
        allProfiles.filter { it.id in allowed }
    }

    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinError by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(profiles.isNotEmpty()) {
        if (profiles.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }

    fun choose(p: ProfileEntity) {
        if (p.pinHash != null) {
            pinError = false
            pinFor = p
        } else {
            vm.switchTo(p) { onEnter(p.id) }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(tv.own.owntv.ui.theme.LocalStagePalette.current.page),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.profiles_gate_title), style = tv.own.owntv.ui.theme.stageText(54, 700), color = tv.own.owntv.ui.theme.StageColors.Text)
            if (centralAccountId != null) {
                val username = XpCentralConnectionStore.read(context)?.username.orEmpty()
                Text(
                    stringResource(R.string.movioxp_account_profiles, username),
                    style = tv.own.owntv.ui.theme.stageText(23, 500),
                    color = tv.own.owntv.ui.theme.StageColors.Muted,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(36.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.focusGroup().padding(horizontal = 48.dp),
            ) {
                items(profiles, key = { it.id }) { p ->
                    ProfileTile(
                        profile = p,
                        modifier = if (p.id == profiles.first().id) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { choose(p) },
                    )
                }
                if (centralAccountId == null) item { AddTile(onClick = onAddProfile) }
            }
            if (centralAccountId != null && profiles.isEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.moviotv_xp_central_profile_required),
                    style = tv.own.owntv.ui.theme.stageText(21, 500),
                    color = tv.own.owntv.ui.theme.StageColors.Muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                tv.own.owntv.ui.stage.StageSurface(
                    onClick = { XpCentralConnectionStore.clear(context) },
                    radius = 16.dp,
                    modifier = Modifier.padding(12.dp),
                    focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
                    contentAlignment = Alignment.Center,
                ) { _ ->
                    Text(
                        stringResource(R.string.moviotv_xp_central_disconnect),
                        style = tv.own.owntv.ui.theme.stageText(22, 600),
                        color = tv.own.owntv.ui.theme.StageColors.Text,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    pinFor?.let { p ->
        PinDialog(
            title = if (pinError) stringResource(R.string.profiles_wrong_pin) else stringResource(R.string.profiles_enter_pin, p.name),
            onSubmit = { pin ->
                if (vm.verifyPin(p, pin)) {
                    vm.switchTo(p) { onEnter(p.id) }
                } else pinError = true
            },
            onDismiss = { pinFor = null },
        )
    }
}

@Composable
private fun ProfileTile(profile: ProfileEntity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        tv.own.owntv.ui.stage.StageSurface(
            onClick = onClick,
            radius = 24.dp,
            modifier = modifier.size(132.dp),
            focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
            contentAlignment = Alignment.Center,
        ) { _ ->
            Box(contentAlignment = Alignment.TopEnd) {
                OwnTVAvatar(avatarId = profile.avatarId, imagePath = profile.avatarPath.orEmpty(), modifier = Modifier.size(104.dp))
                if (profile.pinHash != null) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(tv.own.owntv.ui.theme.StageColors.Ink.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        OwnTVIcon(OwnTVIcon.SETTINGS, tint = tv.own.owntv.ui.theme.StageColors.Text, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(profile.name, style = tv.own.owntv.ui.theme.stageText(32, 600), color = tv.own.owntv.ui.theme.StageColors.Text, textAlign = TextAlign.Center)
        if (profile.isKids) {
            Text(stringResource(R.string.profiles_kids_badge), style = tv.own.owntv.ui.theme.stageText(20, 500), color = tv.own.owntv.ui.theme.stageAccent.accent)
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        tv.own.owntv.ui.stage.StageSurface(
            onClick = onClick,
            radius = 24.dp,
            modifier = Modifier.size(132.dp),
            focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
            contentAlignment = Alignment.Center,
        ) { _ ->
            Box(
                modifier = Modifier.size(104.dp).clip(CircleShape).background(tv.own.owntv.ui.theme.StageColors.Ink.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                OwnTVIcon(OwnTVIcon.ADD, tint = tv.own.owntv.ui.theme.StageColors.Muted, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.profiles_add), style = tv.own.owntv.ui.theme.stageText(32, 600), color = tv.own.owntv.ui.theme.StageColors.Muted)
    }
}
