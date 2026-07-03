package com.daybreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daybreak.data.HomeData
import com.daybreak.data.InsightsData
import com.daybreak.scoring.ScoreResult
import com.daybreak.ui.theme.ScoreRed

/**
 * Insights — the app's main screen (PRD §8.6): readable, card-based trends with
 * scrubbable charts (tap or drag a chart to read the time and value at any point),
 * plus the on-demand heart-rate measurement and wearer notes.
 */
@Composable
fun InsightsScreen(
    data: InsightsData?,
    home: HomeData?,
    selectedRange: Int,
    ranges: List<Pair<String, Int>>,
    onRangeChange: (Int) -> Unit,
    liveHeartRate: Int?,
    measuring: Boolean,
    onMeasureHeartRate: () -> Unit,
    aiSummaryEnabled: Boolean,
    onAiSummaryToggle: (Boolean) -> Unit,
    onExportCsv: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Daybreak", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            home?.let { BatteryLine(it.batteryPct, it.charging) }
        }
        home?.lastUpdated?.let {
            Text("Updated $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(12.dp))
        RangeSelector(selectedRange, ranges, onRangeChange)
        Spacer(Modifier.height(16.dp))

        ChartCard("Heart rate now") {
            Button(onClick = onMeasureHeartRate, enabled = !measuring, modifier = Modifier.fillMaxWidth()) {
                Text(if (measuring) "Measuring…" else "Measure heart rate")
            }
            if (liveHeartRate != null) {
                Spacer(Modifier.height(10.dp))
                Text("❤️ $liveHeartRate bpm", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ScoreRed)
                Text(
                    if (measuring) "Keep the ring snug on the finger…" else "Latest reading",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (measuring) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Keep the ring snug on the finger…",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (data == null) {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        data.today?.let { ScoreCards(it) }

        val isToday = selectedRange <= 1

        if (!isToday && data.trend.size >= 2) {
            ChartCard("Score trends") {
                val series = listOf(
                    LineSeries("Sleep", data.trend.map { it.sleep.toFloat() }, ChartColors.sleep),
                    LineSeries("Recovery", data.trend.map { it.recovery.toFloat() }, ChartColors.recovery),
                    LineSeries("Activity", data.trend.map { it.activity.toFloat() }, ChartColors.activity),
                )
                LineChart(
                    series,
                    yMin = 0f,
                    yMax = 100f,
                    markedIndices = data.taggedTrendIndices,
                    xLabels = data.trend.map { it.label },
                )
                Spacer(Modifier.height(10.dp))
                ChartLegend(series)
                ScrubHint()
                if (data.taggedTrendIndices.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("◆ marks days with a note", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        ChartCard("Resting heart rate") {
            val rhr = data.restingHr
            if (rhr.isEmpty()) {
                Empty("No data yet.")
            } else if (isToday || rhr.size < 2) {
                BigStat("${rhr.last().toInt()}", "bpm last night")
            } else {
                val lo = rhr.minOrNull() ?: 0f
                val hi = rhr.maxOrNull() ?: 0f
                StatRow("Latest" to "${rhr.last().toInt()} bpm", "Range" to "${lo.toInt()}–${hi.toInt()} bpm")
                Spacer(Modifier.height(8.dp))
                LineChart(
                    listOf(LineSeries("RHR", rhr, ChartColors.recovery)),
                    yMin = lo - 3f,
                    yMax = hi + 3f,
                    labelEveryPoint = true,
                    xLabels = data.restingHrDates,
                    readoutSuffix = " bpm",
                )
                ScrubHint()
            }
        }

        ChartCard("Daily steps") {
            val steps = data.steps
            if (steps.isEmpty()) {
                Empty("No data yet.")
            } else if (isToday) {
                BigStat("${steps.last()}", "steps today")
            } else {
                StatRow("Latest" to "${steps.last()}", "Average" to "${steps.average().toInt()}")
                Spacer(Modifier.height(8.dp))
                BarChart(
                    steps,
                    ChartColors.activity,
                    xLabels = data.stepsDates,
                    readoutSuffix = " steps",
                )
                ScrubHint()
            }
        }

        ChartCard("Last night's sleep") {
            val s = data.lastNightStages
            if (s == null || s.total == 0) {
                Empty("No sleep recorded.")
            } else {
                BigStat("${s.total / 60}h ${s.total % 60}m", "total sleep")
                Spacer(Modifier.height(12.dp))
                StageCompositionBar(s.deepMin, s.lightMin, s.remMin, s.awakeMin)
            }
        }

        ChartCard("Last night's heart rate") {
            val hr = data.overnightHr
            if (hr.size < 2) {
                Empty("No overnight heart rate.")
            } else {
                val lo = hr.minOrNull() ?: 0
                val hi = hr.maxOrNull() ?: 0
                StatRow("Low" to "$lo bpm", "High" to "$hi bpm")
                Spacer(Modifier.height(8.dp))
                LineChart(
                    listOf(LineSeries("HR", hr.map { it.toFloat() }, ChartColors.sleep)),
                    yMin = (lo - 3).toFloat(),
                    yMax = (hi + 3).toFloat(),
                    xLabels = data.overnightHrTimes,
                    readoutSuffix = " bpm",
                )
                ScrubHint()
            }
        }

        ChartCard("Blood oxygen (SpO2)") {
            val spo2 = data.spo2
            when {
                data.lastNightSpo2Avg == null && spo2.isEmpty() -> Empty("No SpO2 recorded.")
                isToday || spo2.size < 2 -> BigStat("${data.lastNightSpo2Avg ?: spo2.lastOrNull() ?: 0}%", "average last night")
                else -> {
                    StatRow(
                        "Latest" to "${spo2.last()}%",
                        "Lowest" to "${data.lastNightSpo2Min ?: spo2.minOrNull() ?: 0}%",
                    )
                    Spacer(Modifier.height(8.dp))
                    LineChart(
                        listOf(LineSeries("SpO2", spo2.map { it.toFloat() }, ChartColors.spo2)),
                        yMin = 88f,
                        yMax = 100f,
                        labelEveryPoint = true,
                        xLabels = data.spo2Dates,
                        readoutSuffix = "%",
                    )
                    ScrubHint()
                }
            }
        }

        data.today?.let { today ->
            ChartCard("Today's contributors") {
                ContributorBars("Recovery", today.recovery, ChartColors.recovery)
                Spacer(Modifier.height(14.dp))
                ContributorBars("Sleep", today.sleep, ChartColors.sleep)
                Spacer(Modifier.height(14.dp))
                ContributorBars("Activity", today.activity, ChartColors.activity)
            }
        }

        ChartCard("Settings") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI daily summary", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Experimental. Uses an on-device model (sideloaded); falls back to the standard summary if unavailable.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = aiSummaryEnabled, onCheckedChange = onAiSummaryToggle)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onExportCsv) { Text("Export data (CSV)") }
            Text(
                "Saves a local CSV and opens the share sheet. Stays on your device.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BatteryLine(pct: Int?, charging: Boolean) {
    val text = when {
        pct == null -> "🔋 —"
        charging -> "🔋 $pct% (charging)"
        pct <= 20 -> "🔋 $pct% — charge soon"
        else -> "🔋 $pct%"
    }
    val color = when {
        pct != null && pct <= 20 && !charging -> ScoreRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
}

@Composable
private fun ScrubHint() {
    Text(
        "Tap or drag the chart to see any point",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ScoreCards(today: com.daybreak.scoring.ScoringState.Ready) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScoreCard("Recovery", today.recovery.value, ChartColors.recovery, Modifier.weight(1f))
        ScoreCard("Sleep", today.sleep.value, ChartColors.sleep, Modifier.weight(1f))
        ScoreCard("Activity", today.activity.value, ChartColors.activity, Modifier.weight(1f))
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ScoreCard(label: String, value: Int, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RangeSelector(selected: Int, ranges: List<Pair<String, Int>>, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ranges.forEach { (label, days) ->
            val active = days == selected
            Box(
                Modifier
                    .weight(1f)
                    .clickable { onChange(days) }
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun BigStat(value: String, caption: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(caption, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun StatRow(vararg stats: Pair<String, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        stats.forEach { (label, value) ->
            Column {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ContributorBars(title: String, score: ScoreResult, accent: Color) {
    Text("$title — ${score.value}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accent)
    Spacer(Modifier.height(8.dp))
    score.contributors.forEach { c ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
            Text(c.name, fontSize = 12.sp, modifier = Modifier.weight(0.46f))
            Box(
                Modifier.weight(0.42f).height(12.dp).background(ChartColors.grid, RoundedCornerShape(6.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction = (c.normalized / 100.0).toFloat().coerceIn(0f, 1f))
                        .height(12.dp)
                        .background(accent, RoundedCornerShape(6.dp)),
                )
            }
            Text(
                "${c.normalized.toInt()}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.12f).padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun Empty(message: String) {
    Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
