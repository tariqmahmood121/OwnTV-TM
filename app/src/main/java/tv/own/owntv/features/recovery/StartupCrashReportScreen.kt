package tv.own.owntv.features.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.own.owntv.R

/** Emergency report surface: works without profiles, the network, or the normal application shell. */
@Composable
internal fun StartupCrashReportScreen(report: String, onCopy: () -> Unit, onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Color(0xFF0E0C17))
            .padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.moviotv_xp_crash_report_title),
            style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            stringResource(R.string.moviotv_xp_crash_report_explanation),
            style = TextStyle(color = Color(0xFFCBC4D8), fontSize = 16.sp),
        )
        CrashReportAction(stringResource(R.string.moviotv_xp_crash_copy), onCopy)
        CrashReportAction(stringResource(R.string.moviotv_xp_crash_retry), onContinue)
        Text(
            stringResource(R.string.moviotv_xp_crash_privacy),
            style = TextStyle(color = Color(0xFFD6B5A8), fontSize = 13.sp),
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .border(1.dp, Color(0xFF544B63), RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState()).padding(14.dp),
        ) {
            Text(
                report.take(20000),
                style = TextStyle(color = Color(0xFFE2DCEF), fontSize = 12.sp, fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun CrashReportAction(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 3.dp else 1.dp, if (focused) Color(0xFFBB8AFF) else Color(0xFF55445F), RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFF533A72) else Color(0xFF2B213A), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 18.dp),
    ) {
        Text(label, style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
    }
}
