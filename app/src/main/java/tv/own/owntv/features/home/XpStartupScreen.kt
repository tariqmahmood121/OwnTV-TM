package tv.own.owntv.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.own.owntv.BuildConfig
import tv.own.owntv.R

@Composable
internal fun XpStartupScreen(visible: Boolean) {
    AnimatedVisibility(visible, enter = EnterTransition.None, exit = fadeOut(tween(180))) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                XpApprovedWordmark(Modifier.size(width = 320.dp, height = 92.dp))
                Text(BuildConfig.VERSION_NAME, color = Color.White, fontSize = 14.sp)
                val creator = stringResource(R.string.movioxp_splash_created_by)
                val ziggs = stringResource(R.string.movioxp_splash_ziggs)
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append(creator) }
                    withStyle(SpanStyle(color = Color(0xFFAE7AFF))) { append(ziggs) }
                }, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun XpApprovedWordmark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.movioxp_approved_wordmark),
        stringResource(R.string.movioxp_wordmark_description), modifier)
}
