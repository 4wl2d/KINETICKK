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
import kinetickk.ball.gameplay.interaction.performance.GameplayPerformanceSnapshot
import kinetickk.ball.gameplay.interaction.performance.PerformanceDurationStats
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.formatCompact
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.monoStyle
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
    val frame = HudScratch.frame.update(size.width, size.height, density)
    val trial = PerformanceTrialLayout.update(size.width, size.height, density, textMeasurer.scale)
    val left = frame.margin
    val top = trial.top
    if (frame.regular) {
        drawRegularPerformanceHud(projection, textMeasurer, frame, left, top)
    } else {
        drawCompactPerformanceHud(projection, textMeasurer, frame, left, top)
    }
}

private val PerformanceTrialLayout = HudTrialPanelLayout()

/** Top-left diagnostics panel: ink-2 chamfered slip, `you` rule, mono readouts. */
private fun DrawScope.drawPerformancePanel(frame: HudFrame, left: Float, top: Float, width: Float, height: Float, roles: KkRolePalette) {
    val cut = frame.u(10f)
    drawPath(HudDrawCache.paths.chamfer(HudPath.PERF_PANEL.ordinal, HudDrawCache.rect(HudRect.PERF_PANEL, left, top, left + width, top + height), cut),
        Kk.Ink2.copy(alpha = 0.92f))
    drawRect(roles.you, Offset(left, top), Size(width - cut, frame.u(2f)))
}

private fun DrawScope.drawCompactPerformanceHud(
    projection: PerformanceHudProjection,
    textMeasurer: TextMeasurer,
    frame: HudFrame,
    left: Float,
    top: Float,
) {
    val roles = textMeasurer.roles
    val width = min(frame.u(360f), frame.width - frame.margin * 2f)
    val fontSize = 10f
    val lineStep = fontSize * 1.35f * textMeasurer.scale * density + frame.u(2f)
    val padding = frame.u(10f)
    val height = padding * 2f + lineStep * (1 + projection.compactLines.size)
    drawPerformancePanel(frame, left, top, width, height, roles)
    val textLeft = left + padding
    val maxTextWidth = width - padding * 2f
    drawPerformanceLine(textMeasurer, HudText.PERF_TITLE, textMeasurer.language.text(GameplayText.PerformanceCompactTitle),
        textLeft, top + padding, fontSize, roles.you, maxTextWidth, label = true)
    val lines = projection.compactLines
    for (index in lines.indices) {
        drawPerformanceLine(textMeasurer, PerformanceSlots[index.coerceAtMost(PerformanceSlots.size - 1)], lines[index], textLeft,
            top + padding + lineStep * (index + 1), fontSize, compactPerformanceLineColor(index, projection.hasSlowFrames, roles), maxTextWidth)
    }
}

private fun DrawScope.drawRegularPerformanceHud(
    projection: PerformanceHudProjection,
    textMeasurer: TextMeasurer,
    frame: HudFrame,
    left: Float,
    top: Float,
) {
    val roles = textMeasurer.roles
    val width = min(frame.u(640f), frame.width - frame.margin * 2f).coerceAtLeast(frame.u(180f))
    val fontSize = 11f * frame.textFactor
    val lineStep = fontSize * 1.35f * textMeasurer.scale * density + frame.u(3f)
    val padding = frame.u(12f)
    val height = padding * 2f + lineStep * 6f
    drawPerformancePanel(frame, left, top, width, height, roles)
    val textLeft = left + padding
    val maxTextWidth = width - padding * 2f
    drawPerformanceLine(textMeasurer, HudText.PERF_TITLE, textMeasurer.language.text(GameplayText.PerformanceTitle),
        textLeft, top + padding, fontSize, roles.you, maxTextWidth, label = true)
    drawPerformanceLine(textMeasurer, HudText.PERF_0, projection.frameLine, textLeft, top + padding + lineStep, fontSize, roles.you, maxTextWidth)
    drawPerformanceLine(textMeasurer, HudText.PERF_1, projection.dispatchLine, textLeft, top + padding + lineStep * 2f, fontSize,
        roles.you.copy(alpha = 0.7f), maxTextWidth)
    drawPerformanceLine(textMeasurer, HudText.PERF_2, projection.canvasLine, textLeft, top + padding + lineStep * 3f, fontSize,
        roles.you.copy(alpha = 0.7f), maxTextWidth)
    drawPerformanceLine(textMeasurer, HudText.PERF_3, projection.rateLine, textLeft, top + padding + lineStep * 4f, fontSize,
        if (projection.hasSlowFrames) roles.heat else roles.you, maxTextWidth)
    drawPerformanceLine(textMeasurer, HudText.PERF_4, projection.entitiesLine, textLeft, top + padding + lineStep * 5f, fontSize,
        roles.you, maxTextWidth)
}

private val PerformanceSlots = arrayOf(HudText.PERF_0, HudText.PERF_1, HudText.PERF_2, HudText.PERF_3, HudText.PERF_4)

private fun compactPerformanceLineColor(index: Int, hasSlowFrames: Boolean, roles: KkRolePalette): Color = when (index) {
    0 -> roles.you
    1 -> roles.you.copy(alpha = 0.7f)
    2, 3 -> if (hasSlowFrames) roles.heat else roles.you
    else -> roles.you
}

private fun DrawScope.drawPerformanceLine(
    textMeasurer: TextMeasurer,
    slot: HudText,
    text: String,
    x: Float,
    y: Float,
    fontSize: Float,
    color: Color,
    maxWidth: Float,
    label: Boolean = false,
) {
    val style = if (label) textMeasurer.typography.labelStyle(fontSize + 1f) else textMeasurer.typography.monoStyle(fontSize)
    val layout = HudDrawCache.layout(slot, textMeasurer, text, style, uppercase = label, maxWidth = maxWidth)
    drawKkText(layout, x, y, color)
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
