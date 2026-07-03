package com.daybreak.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Chart palette — soft, high-contrast, with non-color cues via labels. */
object ChartColors {
    val sleep = Color(0xFF1E88E5)
    val recovery = Color(0xFF2E7D32)
    val activity = Color(0xFFEF6C00)
    val deep = Color(0xFF283593)
    val light = Color(0xFF64B5F6)
    val rem = Color(0xFF7E57C2)
    val awake = Color(0xFFFFB300)
    val grid = Color(0x22000000)
    val axisText = Color(0xFF757575)
    val note = Color(0xFF9E9E9E)
    val spo2 = Color(0xFF00897B)
    val scrub = Color(0xFF424242)
}

data class LineSeries(val name: String, val values: List<Float>, val color: Color)

private const val LEFT_PAD = 64f
private const val RIGHT_PAD = 56f

private fun DrawScope.label(text: String, x: Float, y: Float, color: Color, sizeSp: Int, align: Paint.Align) {
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        textSize = sizeSp.sp.toPx()
        textAlign = align
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

/** Which x indices get an axis label: all when few, otherwise first / middle / last. */
private fun axisLabelIndices(count: Int): List<Int> =
    if (count <= 8) (0 until count).toList() else listOf(0, count / 2, count - 1)

private fun Paint.Align.forEdge(index: Int, last: Int): Paint.Align = when (index) {
    0 -> Paint.Align.LEFT
    last -> Paint.Align.RIGHT
    else -> Paint.Align.CENTER
}

/** Nearest point index for a touch at [x], given the plot geometry. */
private fun scrubIndex(x: Float, widthPx: Float, pointCount: Int): Int? {
    if (pointCount < 2) return null
    val stepX = (widthPx - LEFT_PAD - RIGHT_PAD) / (pointCount - 1)
    return ((x - LEFT_PAD) / stepX).roundToInt().coerceIn(0, pointCount - 1)
}

/**
 * Line chart with a numbered y-axis (min/mid/max), dots at each point, and value
 * labels — latest value per series always, every point when [labelEveryPoint].
 *
 * When [xLabels] are provided (a time/date per point), the x-axis is labeled and the
 * chart is scrubbable: tap or drag anywhere to pin a point and read its time and value.
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    yMin: Float,
    yMax: Float,
    labelEveryPoint: Boolean = false,
    markedIndices: Set<Int> = emptySet(),
    xLabels: List<String> = emptyList(),
    readoutSuffix: String = "",
    valueFormat: (Float) -> String = { it.toInt().toString() },
    modifier: Modifier = Modifier,
) {
    val pointCount = series.firstOrNull()?.values?.size ?: 0
    val scrubbable = xLabels.isNotEmpty() && pointCount > 1
    var selected by remember(pointCount, xLabels) { mutableStateOf<Int?>(null) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(if (scrubbable) 210.dp else 170.dp)
            .pointerInput(pointCount, scrubbable) {
                if (!scrubbable) return@pointerInput
                detectTapGestures { offset ->
                    selected = scrubIndex(offset.x, size.width.toFloat(), pointCount)
                }
            }
            .pointerInput(pointCount, scrubbable) {
                if (!scrubbable) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        selected = scrubIndex(offset.x, size.width.toFloat(), pointCount)
                    },
                ) { change, _ ->
                    change.consume()
                    selected = scrubIndex(change.position.x, size.width.toFloat(), pointCount)
                }
            },
    ) {
        val topPad = if (scrubbable) 60f else 22f
        val bottomPad = if (scrubbable) 44f else 18f
        val plotW = size.width - LEFT_PAD - RIGHT_PAD
        val plotH = size.height - topPad - bottomPad
        val span = (yMax - yMin).coerceAtLeast(1f)
        val stepX = if (pointCount > 1) plotW / (pointCount - 1) else 0f
        fun px(i: Int) = LEFT_PAD + i * stepX
        fun py(v: Float) = topPad + (1 - ((v - yMin) / span).coerceIn(0f, 1f)) * plotH

        // Gridlines + y-axis numbers at 0, 50, 100% of the range. Skip a number when it
        // rounds to the same text as one already drawn (tight ranges made e.g. "63" twice).
        val drawnAxisTexts = HashSet<String>()
        listOf(0f, 0.5f, 1f).forEach { f ->
            val y = topPad + (1 - f) * plotH
            drawLine(ChartColors.grid, Offset(LEFT_PAD, y), Offset(size.width - RIGHT_PAD, y), strokeWidth = 2f)
            val text = valueFormat(yMin + f * span)
            if (drawnAxisTexts.add(text)) {
                label(text, LEFT_PAD - 10f, y + 10f, ChartColors.axisText, 11, Paint.Align.RIGHT)
            }
        }

        // X-axis time/date labels.
        if (scrubbable) {
            axisLabelIndices(pointCount).forEach { i ->
                xLabels.getOrNull(i)?.let { text ->
                    label(text, px(i), size.height - 8f, ChartColors.axisText, 10, Paint.Align.CENTER.forEdge(i, pointCount - 1))
                }
            }
        }

        // Note markers: a faint vertical line at each tagged day.
        if (markedIndices.isNotEmpty() && pointCount > 1) {
            markedIndices.forEach { idx ->
                if (idx in 0 until pointCount) {
                    val x = px(idx)
                    drawLine(ChartColors.note, Offset(x, topPad), Offset(x, topPad + plotH), strokeWidth = 3f)
                    label("◆", x, topPad - 4f, ChartColors.note, 11, Paint.Align.CENTER)
                }
            }
        }

        // Scrub guide under the data so the lines stay readable.
        selected?.let { i ->
            drawLine(ChartColors.scrub, Offset(px(i), topPad), Offset(px(i), topPad + plotH), strokeWidth = 2.5f)
        }

        series.forEach { s ->
            if (s.values.isEmpty()) return@forEach
            val showPointLabels = labelEveryPoint && s.values.size <= 8

            if (s.values.size > 1) {
                val path = Path()
                s.values.forEachIndexed { i, v -> if (i == 0) path.moveTo(px(i), py(v)) else path.lineTo(px(i), py(v)) }
                drawPath(path, s.color, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
            }
            s.values.forEachIndexed { i, v ->
                drawCircle(s.color, radius = 4f, center = Offset(px(i), py(v)))
                if (showPointLabels) {
                    label(valueFormat(v), px(i), py(v) - 14f, s.color, 11, Paint.Align.CENTER)
                }
            }
            // Label the latest value to the right of the line — unless every point is
            // already labeled, which used to double-print the last value (e.g. "63" twice).
            if (!showPointLabels) {
                val lastV = s.values.last()
                label(valueFormat(lastV), size.width - RIGHT_PAD + 6f, py(lastV) + 5f, s.color, 12, Paint.Align.LEFT)
            }
            // Ring the selected point.
            selected?.let { i ->
                s.values.getOrNull(i)?.let { v ->
                    drawCircle(s.color, radius = 9f, center = Offset(px(i), py(v)), style = Stroke(width = 3f))
                }
            }
        }

        // Read-out for the pinned point: "<time> · <value>" (all series when several).
        selected?.let { i ->
            val time = xLabels.getOrNull(i) ?: return@let
            val values = series.mapNotNull { s ->
                s.values.getOrNull(i)?.let { v ->
                    if (series.size == 1) "${valueFormat(v)}$readoutSuffix" else "${s.name} ${valueFormat(v)}$readoutSuffix"
                }
            }
            label("$time · ${values.joinToString(" · ")}", LEFT_PAD, 34f, ChartColors.scrub, 12, Paint.Align.LEFT)
        }
    }
}

/**
 * Bar chart with rounded bars; value labels above each bar when there aren't too many.
 * With [xLabels] the bars get date labels underneath and tap/drag scrubbing like [LineChart].
 */
@Composable
fun BarChart(
    values: List<Int>,
    color: Color,
    xLabels: List<String> = emptyList(),
    readoutSuffix: String = "",
    modifier: Modifier = Modifier,
) {
    val scrubbable = xLabels.isNotEmpty() && values.size > 1
    var selected by remember(values.size, xLabels) { mutableStateOf<Int?>(null) }

    fun barAt(x: Float, widthPx: Float): Int? {
        if (values.isEmpty()) return null
        return (x / (widthPx / values.size)).toInt().coerceIn(0, values.size - 1)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(if (scrubbable) 190.dp else 150.dp)
            .pointerInput(values.size, scrubbable) {
                if (!scrubbable) return@pointerInput
                detectTapGestures { offset -> selected = barAt(offset.x, size.width.toFloat()) }
            }
            .pointerInput(values.size, scrubbable) {
                if (!scrubbable) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> selected = barAt(offset.x, size.width.toFloat()) },
                ) { change, _ ->
                    change.consume()
                    selected = barAt(change.position.x, size.width.toFloat())
                }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val topPad = if (scrubbable) 58f else 26f
        val bottomPad = if (scrubbable) 40f else 6f
        val plotH = size.height - topPad - bottomPad
        val max = (values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val slot = size.width / values.size
        val barW = slot * 0.6f
        val showLabels = values.size <= 8
        values.forEachIndexed { i, v ->
            val bh = (v / max) * plotH
            val left = i * slot + (slot - barW) / 2
            val top = topPad + (plotH - bh)
            drawRoundRect(
                if (selected == i) ChartColors.scrub else color,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barW, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
            if (showLabels) {
                label(v.toString(), left + barW / 2, top - 8f, ChartColors.axisText, 11, Paint.Align.CENTER)
            }
        }
        if (scrubbable) {
            axisLabelIndices(values.size).forEach { i ->
                xLabels.getOrNull(i)?.let { text ->
                    val align = if (values.size <= 8) Paint.Align.CENTER else Paint.Align.CENTER.forEdge(i, values.size - 1)
                    label(text, i * slot + slot / 2, size.height - 8f, ChartColors.axisText, 10, align)
                }
            }
        }
        selected?.let { i ->
            val time = xLabels.getOrNull(i) ?: return@let
            label("$time · %,d$readoutSuffix".format(values[i]), 8f, 30f, ChartColors.scrub, 12, Paint.Align.LEFT)
        }
    }
}

/** Horizontal stacked bar of one night's sleep-stage minutes, with a legend. */
@Composable
fun StageCompositionBar(deep: Int, light: Int, rem: Int, awake: Int) {
    val total = (deep + light + rem + awake).coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth().height(30.dp).background(ChartColors.grid, RoundedCornerShape(8.dp))) {
            Segment(deep, total, ChartColors.deep)
            Segment(rem, total, ChartColors.rem)
            Segment(light, total, ChartColors.light)
            Segment(awake, total, ChartColors.awake)
        }
        Spacer(Modifier.height(10.dp))
        // Two rows of two so labels never get squeezed into mid-word wraps ("Aw ake").
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { Swatch(ChartColors.deep, "Deep ${deep}m") }
            Box(Modifier.weight(1f)) { Swatch(ChartColors.rem, "REM ${rem}m") }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { Swatch(ChartColors.light, "Light ${light}m") }
            Box(Modifier.weight(1f)) { Swatch(ChartColors.awake, "Awake ${awake}m") }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(value: Int, total: Int, color: Color) {
    if (value <= 0) return
    Box(Modifier.fillMaxHeight().weight(value.toFloat() / total).background(color))
}

@Composable
fun ChartLegend(series: List<LineSeries>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        series.forEach { s ->
            val latest = s.values.lastOrNull()?.toInt()?.toString() ?: "–"
            Swatch(s.color, "${s.name} $latest")
        }
    }
}

@Composable
private fun Swatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
