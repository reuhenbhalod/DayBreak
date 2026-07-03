package com.daybreak.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daybreak.scoring.ScoreResult

/**
 * Maintainer detail (PRD §9): per-score contributor breakdown. Stub for Increment 1 —
 * raw nightly metrics, sync log, and settings come later.
 */
@Composable
fun DetailScreen(sleep: ScoreResult, recovery: ScoreResult, activity: ScoreResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Breakdown("Recovery", recovery)
        Breakdown("Sleep", sleep)
        Breakdown("Activity", activity)
    }
}

@Composable
private fun Breakdown(title: String, score: ScoreResult) {
    Text("$title — ${score.value}", fontWeight = FontWeight.Bold)
    score.contributors.forEach { c ->
        Text(
            "  ${c.name}: ${c.normalized.toInt()} (×${c.weightPct}%)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
