package tv.own.owntv.features.recovery

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.own.owntv.R

/** Diagnostic-build-only staged startup. Each cold launch returns to the minimal screen. */
@Composable
fun MovioXpStartupDiagnostic(onReady: () -> Unit, content: @Composable (Int) -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("movioxp_startup_diagnostic", Context.MODE_PRIVATE)
    }
    val previousStage = remember(prefs) { prefs.getInt("last_stage", -1) }
    var stage by remember { mutableIntStateOf(0) }
    val labels = listOf(
        stringResource(R.string.moviotv_xp_diag_stage_minimal),
        stringResource(R.string.moviotv_xp_diag_stage_shell),
        stringResource(R.string.moviotv_xp_diag_stage_catalogue),
        stringResource(R.string.moviotv_xp_diag_stage_full),
    )
    LaunchedEffect(Unit) { onReady() }

    Column(Modifier.fillMaxSize().background(Color(0xFF11101A))) {
        Text(
            stringResource(R.string.moviotv_xp_diag_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { index, label ->
                var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .border(
                            if (focused || stage == index) 3.dp else 1.dp,
                            if (focused || stage == index) Color(0xFFBB88FF) else Color(0xFF655A72),
                            RoundedCornerShape(10.dp),
                        )
                        .background(
                            if (stage == index || focused) Color(0xFF50356D) else Color(0xFF292334),
                            RoundedCornerShape(10.dp),
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .clickable {
                            prefs.edit().putInt("last_stage", index)
                                .putLong("last_change_ms", System.currentTimeMillis()).apply()
                            Log.i("MovioXPDiag", "STARTUP_STAGE=" + index)
                            stage = index
                        }
                        .focusable()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
        if (stage == 0) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Text(
                        stringResource(R.string.moviotv_xp_diag_minimal_ok),
                        color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.moviotv_xp_diag_instructions),
                        color = Color(0xFFC7BED4), fontSize = 17.sp,
                    )
                    if (previousStage >= 0) Text(
                        stringResource(
                            R.string.moviotv_xp_diag_previous,
                            labels.getOrNull(previousStage) ?: previousStage.toString(),
                        ),
                        color = Color(0xFFE4B881), fontSize = 15.sp,
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                content(stage)
            }
        }
    }
}
