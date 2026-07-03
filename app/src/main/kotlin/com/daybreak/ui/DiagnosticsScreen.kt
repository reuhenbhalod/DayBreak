package com.daybreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Stripped-down diagnostics view: read the ring and dump everything it returns. */
@Composable
fun DiagnosticsScreen(
    report: List<String>,
    bleLog: List<String>,
    running: Boolean,
    liveHeartRate: Int?,
    onRead: () -> Unit,
    onMeasureHeartRate: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Daybreak — Ring Diagnostics", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Button(onClick = onRead, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text(if (running) "Reading…" else "Read ring")
        }

        Button(onClick = onMeasureHeartRate, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text("Measure heart rate")
        }

        if (liveHeartRate != null) {
            Text("❤️ $liveHeartRate bpm", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        Text("Result", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        report.forEach { line ->
            Text(line, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        Divider(Modifier.padding(vertical = 8.dp))

        Text("Raw BLE log", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        bleLog.takeLast(120).forEach { line ->
            Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
