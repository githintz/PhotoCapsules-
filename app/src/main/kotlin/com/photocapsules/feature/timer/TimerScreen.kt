package com.photocapsules.feature.timer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.core.photo.Format
import com.photocapsules.data.AppState
import com.photocapsules.feature.calculators.NumberRow
import com.photocapsules.feature.calculators.rememberPersisted
import com.photocapsules.ui.components.AppScreen
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.InfoText
import com.photocapsules.ui.components.ScrollColumn
import com.photocapsules.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun TimerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var seconds by rememberPersisted("timer_s", 30.0)
    AppState.timerPresetSeconds?.let { if (it.isFinite() && it > 0) seconds = Math.round(it * 10) / 10.0; AppState.timerPresetSeconds = null }
    val st by TimerState.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.running) { while (st.running) { now = System.currentTimeMillis(); delay(100) } }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var askedPerm by remember { mutableStateOf(false) }
    val remaining = if (st.running) (st.endMillis - now).coerceAtLeast(0) / 1000.0 else if (st.finished) 0.0 else seconds
    val fraction = if (st.running && st.totalMillis > 0) (st.endMillis - now).coerceAtLeast(0).toFloat() / st.totalMillis else 1f

    AppScreen("Timer", onBack) { p ->
        ScrollColumn(p) {
            Card {
                Canvas(Modifier.fillMaxWidth().height(240.dp).padding(16.dp)) {
                    val d = size.minDimension
                    val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                    drawArc(Palette.SurfaceHigh, 0f, 360f, false, tl, Size(d, d), style = Stroke(18f))
                    drawArc(if (st.finished) Palette.Green else Palette.Accent, -90f, 360f * fraction, false, tl, Size(d, d), style = Stroke(18f))
                }
                Text(if (st.finished) "Done!" else Format.duration(remaining), color = Palette.Text, fontSize = 40.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp))
            }
            if (!st.running) Card {
                NumberRow("Duration", seconds, "s", display = Format.duration(seconds), min = 1.0, max = 7.0 * 86400) { seconds = it }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (!st.running) Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && !askedPerm) { askedPerm = true; notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    TimerService.start(ctx, (seconds * 1000).toLong())
                }, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black)) { Text("Start") }
                else Button(onClick = { TimerService.stop(ctx) }, colors = ButtonDefaults.buttonColors(containerColor = Palette.Red)) { Text("Stop") }
                if (st.finished) Button(onClick = { TimerState.state.value = TimerState.State() }) { Text("Reset") }
            }
            InfoText("The timer keeps running in the background with a notification and vibrates when time is up.")
        }
    }
}
