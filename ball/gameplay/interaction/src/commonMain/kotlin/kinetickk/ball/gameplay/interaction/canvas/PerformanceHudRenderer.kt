// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.interaction.performance.GameplayPerformanceSnapshot
import kinetickk.ball.gameplay.interaction.performance.PerformanceDurationStats
import kinetickk.foundation.design.Acid
import kinetickk.foundation.design.Cyan
import kinetickk.foundation.design.Muted
import kinetickk.foundation.design.Orange
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.White
import kinetickk.foundation.design.d
import kinetickk.foundation.design.drawLabel
import kinetickk.foundation.design.formatCompact
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

internal class PerformanceHudProjection(
    val frameLine: String,
    val dispatchLine: String,
    val canvasLine: String,
    val rateLine: String,
    val entitiesLine: String,
    val compactLines: List<String>,
    val hasSlowFrames: Boolean,
)

internal fun GameplayPerformanceSnapshot.toPerformanceHudProjection() = PerformanceHudProjection(
    frameLine = frameInterval.hudLine("FRAME"),
    dispatchLine = dispatchPipeline.hudLine("DISPATCH"),
    canvasLine = canvasDraw.hudLine("CANVAS"),
    rateLine = "FPS ${framesPerSecond.tenths()}  1% LOW ${onePercentLowFramesPerSecond.tenths()}" +
        "  >16.67 ${framesOver16MillisFraction.percent()}  >33.33 ${framesOver33MillisFraction.percent()}",
    entitiesLine = "ENTITY CUR/PEAK" +
        "  E $currentEnemies/$peakEnemies" +
        "  P $currentProjectiles/$peakProjectiles" +
        "  PICK $currentPickups/$peakPickups" +
        "  TRAIL $currentTrailPoints/$peakTrailPoints",
    compactLines = listOf(
        "FRM P50 ${frameInterval.p50Millis.tenths()}" +
            " P95 ${frameInterval.p95Millis.tenths()}" +
            " P99 ${frameInterval.p99Millis.tenths()}" +
            " MAX ${frameInterval.maxMillis.tenths()}",
        "N ${frameInterval.totalSampleCount.compact()} W ${frameInterval.sampleCount.compact()}" +
            " | PIPE P50 ${dispatchPipeline.p50Millis.tenths()} P95 ${dispatchPipeline.p95Millis.tenths()}",
        "DRAW P50 ${canvasDraw.p50Millis.tenths()} P95 ${canvasDraw.p95Millis.tenths()}" +
            " | FPS ${framesPerSecond.tenths()} LOW ${onePercentLowFramesPerSecond.tenths()}",
        ">16 ${framesOver16MillisFraction.percent()} >33 ${framesOver33MillisFraction.percent()}" +
            " | E ${currentEnemies.compact()}/${peakEnemies.compact()}" +
            " PRJ ${currentProjectiles.compact()}/${peakProjectiles.compact()}",
        "PICK ${currentPickups.compact()}/${peakPickups.compact()}" +
            " TRAIL ${currentTrailPoints.compact()}/${peakTrailPoints.compact()}",
    ),
    hasSlowFrames = framesOver16MillisFraction > 0.05,
)

internal fun DrawScope.drawPerformanceHud(
    projection: PerformanceHudProjection,
    textMeasurer: TextMeasurer,
) {
    val left = d(8f)
    val top = d(8f)
    val compact = gameplayLayoutMode(size.width, size.height, density) != GameplayLayoutMode.REGULAR
    if (compact) {
        drawCompactPerformanceHud(projection, textMeasurer, left, top)
        return
    }

    drawRegularPerformanceHud(projection, textMeasurer, left, top)
}

private fun DrawScope.drawCompactPerformanceHud(
    projection: PerformanceHudProjection,
    textMeasurer: TextMeasurer,
    left: Float,
    top: Float,
) {
    val controlReserve = d(136f)
    val width = min(d(220f), size.width - d(16f) - controlReserve).coerceAtLeast(d(180f))
    val height = d(100f)
    val fontSize = 6f
    val textLeft = left + d(8f)
    val maxTextWidth = width - d(16f)

    drawRect(Color(0xE9050610), Offset(left, top), Size(width, height))
    drawRect(Cyan.copy(alpha = 0.82f), Offset(left, top), Size(width, height), style = Stroke(d(1f)))
    drawLabel(
        textMeasurer = textMeasurer,
        text = COMPACT_PERFORMANCE_TITLE,
        x = textLeft,
        y = top + d(7f),
        fontSize = fontSize + 0.5f,
        color = Acid,
        maxWidth = maxTextWidth,
    )
    projection.compactLines.forEachIndexed { index, line ->
        drawPerformanceLine(
            textMeasurer = textMeasurer,
            text = line,
            x = textLeft,
            y = top + d(22f + index * 14f),
            fontSize = fontSize,
            color = compactPerformanceLineColor(index, projection.hasSlowFrames),
            maxWidth = maxTextWidth,
        )
    }
}

private fun DrawScope.drawRegularPerformanceHud(
    projection: PerformanceHudProjection,
    textMeasurer: TextMeasurer,
    left: Float,
    top: Float,
) {
    val width = min(d(610f), size.width - d(16f)).coerceAtLeast(d(180f))
    val height = d(116f)
    val fontSize = if (size.width / density < 760f) 5.5f else 7f
    val textLeft = left + d(8f)
    val maxTextWidth = width - d(16f)

    drawRect(Color(0xE9050610), Offset(left, top), Size(width, height))
    drawRect(Cyan.copy(alpha = 0.82f), Offset(left, top), Size(width, height), style = Stroke(d(1f)))
    drawLabel(
        textMeasurer = textMeasurer,
        text = "PERFORMANCE // F3 TOGGLE + RESET // ROLLING WINDOW",
        x = textLeft,
        y = top + d(7f),
        fontSize = fontSize + 0.5f,
        color = Acid,
        maxWidth = maxTextWidth,
    )
    drawPerformanceLine(textMeasurer, projection.frameLine, textLeft, top + d(25f), fontSize, White, maxTextWidth)
    drawPerformanceLine(textMeasurer, projection.dispatchLine, textLeft, top + d(42f), fontSize, Muted, maxTextWidth)
    drawPerformanceLine(textMeasurer, projection.canvasLine, textLeft, top + d(59f), fontSize, Muted, maxTextWidth)
    drawPerformanceLine(
        textMeasurer,
        projection.rateLine,
        textLeft,
        top + d(76f),
        fontSize,
        if (projection.hasSlowFrames) Orange else Cyan,
        maxTextWidth,
    )
    drawPerformanceLine(textMeasurer, projection.entitiesLine, textLeft, top + d(93f), fontSize, White, maxTextWidth)
}

private fun compactPerformanceLineColor(index: Int, hasSlowFrames: Boolean): Color = when (index) {
    0 -> White
    1 -> Muted
    2, 3 -> if (hasSlowFrames) Orange else Cyan
    else -> White
}

private fun DrawScope.drawPerformanceLine(
    textMeasurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    fontSize: Float,
    color: Color,
    maxWidth: Float,
) {
    drawLabel(
        textMeasurer = textMeasurer,
        text = text,
        x = x,
        y = y,
        fontSize = fontSize,
        color = color,
        maxWidth = maxWidth,
    )
}

private fun PerformanceDurationStats.hudLine(label: String): String =
    "$label ms" +
        "  P50 ${p50Millis.tenths()}" +
        "  P95 ${p95Millis.tenths()}" +
        "  P99 ${p99Millis.tenths()}" +
        "  MAX ${maxMillis.tenths()}" +
        "  N $totalSampleCount  WIN $sampleCount"

private fun Number.compact(): String = formatCompact(toLong())

private fun Double.percent(): String = "${(this * 100.0).tenths()}%"

private fun Double.tenths(): String {
    if (!isFinite()) return "--"
    val scaled = (this * 10.0).roundToInt()
    return "${scaled / 10}.${abs(scaled % 10)}"
}

internal const val COMPACT_PERFORMANCE_TITLE = "PERFORMANCE // TAP PERF // ROLLING"
