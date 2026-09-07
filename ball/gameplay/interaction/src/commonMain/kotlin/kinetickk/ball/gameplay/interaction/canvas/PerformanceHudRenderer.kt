// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text

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

internal fun GameplayPerformanceSnapshot.toPerformanceHudProjection(language: AppLanguage = AppLanguage.English) = PerformanceHudProjection(
    frameLine = frameInterval.hudLine(language.text(GameplayText.Frame), language),
    dispatchLine = dispatchPipeline.hudLine(language.text(GameplayText.Dispatch), language),
    canvasLine = canvasDraw.hudLine(language.text(GameplayText.Canvas), language),
    rateLine = language.text(GameplayText.Rate, framesPerSecond.tenths(language), onePercentLowFramesPerSecond.tenths(language)) +
        (if (language == AppLanguage.Russian) "  >16,67 " else "  >16.67 ") + framesOver16MillisFraction.percent(language) +
        (if (language == AppLanguage.Russian) "  >33,33 " else "  >33.33 ") + framesOver33MillisFraction.percent(language),
    entitiesLine = language.text(GameplayText.Entities) +
        language.text(GameplayText.EnemyCounts, currentEnemies, peakEnemies) +
        language.text(GameplayText.ProjectileCounts, currentProjectiles, peakProjectiles) +
        language.text(GameplayText.PickupCounts, currentPickups, peakPickups) +
        language.text(GameplayText.TrailCounts, currentTrailPoints, peakTrailPoints),
    compactLines = listOf(
        language.text(GameplayText.CompactFrame, frameInterval.p50Millis.tenths(language)) +
            " P95 ${frameInterval.p95Millis.tenths(language)}" +
            " P99 ${frameInterval.p99Millis.tenths(language)}" +
            language.text(GameplayText.Max, frameInterval.maxMillis.tenths(language)),
        language.text(GameplayText.CompactSamples, frameInterval.totalSampleCount.compact(language), frameInterval.sampleCount.compact(language)) +
            language.text(GameplayText.CompactPipeline, dispatchPipeline.p50Millis.tenths(language), dispatchPipeline.p95Millis.tenths(language)),
        language.text(GameplayText.CompactDraw, canvasDraw.p50Millis.tenths(language), canvasDraw.p95Millis.tenths(language)) +
            language.text(GameplayText.CompactRate, framesPerSecond.tenths(language), onePercentLowFramesPerSecond.tenths(language)),
        ">16 ${framesOver16MillisFraction.percent(language)} >33 ${framesOver33MillisFraction.percent(language)}" +
            language.text(GameplayText.CompactEnemies, currentEnemies.compact(language), peakEnemies.compact(language)) +
            language.text(GameplayText.CompactProjectiles, currentProjectiles.compact(language), peakProjectiles.compact(language)),
        language.text(GameplayText.CompactPickups, currentPickups.compact(language), peakPickups.compact(language)) +
            language.text(GameplayText.CompactTrail, currentTrailPoints.compact(language), peakTrailPoints.compact(language)),
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
        text = textMeasurer.language.text(GameplayText.PerformanceCompactTitle),
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
        text = textMeasurer.language.text(GameplayText.PerformanceTitle),
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

private fun PerformanceDurationStats.hudLine(label: String, language: AppLanguage): String =
    language.text(GameplayText.Milliseconds, label) +
        "  P50 ${p50Millis.tenths(language)}" +
        "  P95 ${p95Millis.tenths(language)}" +
        "  P99 ${p99Millis.tenths(language)}" +
        language.text(GameplayText.MaxRegular, maxMillis.tenths(language)) +
        language.text(GameplayText.Samples, totalSampleCount, sampleCount)

private fun Number.compact(language: AppLanguage): String = formatCompact(toLong(), language)

private fun Double.percent(language: AppLanguage): String = "${(this * 100.0).tenths(language)}%"

private fun Double.tenths(language: AppLanguage): String {
    if (!isFinite()) return "--"
    val scaled = (this * 10.0).roundToInt()
    val separator = if (language == AppLanguage.Russian) ',' else '.'
    return "${scaled / 10}$separator${abs(scaled % 10)}"
}

internal val COMPACT_PERFORMANCE_TITLE = GameplayText.PerformanceCompactTitle.english
