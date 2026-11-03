// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.REGULAR_HUD_BOTTOM_DP
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.COMPACT_NUMBER_THRESHOLD
import kinetickk.foundation.common.localization.compactNumberRounded
import kinetickk.foundation.common.localization.formatCompactNumber
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.interaction.layout.*

/** Bottom-left: integrity number, segmented integrity bar, shield cells, dash pips, heat, ability. */
internal fun DrawScope.drawCoreStatus(
    engine: GameplayRenderModel,
    hostMeasurer: TextMeasurer,
    frame: HudFrame,
    renderTime: Float,
    memory: HudPresentationMemory?,
    critical: Boolean,
) {
    // The integrity number is display type: the same size at every text-size setting.
    val measurer = HudMeasurers.display(hostMeasurer)
    val roles = measurer.roles
    val left = frame.margin
    val width: Float
    val bottom: Float
    val numberSize: Float
    val barHeight: Float
    val pipWidth: Float
    val pipHeight: Float
    val heatHeight: Float
    val gapRow: Float
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            width = min(frame.u(340f), (frame.width - frame.margin * 2f) * 0.3f)
            bottom = frame.height - frame.u(REGULAR_HUD_BOTTOM_DP)
            numberSize = frame.t(44f)
            barHeight = frame.u(12f)
            pipWidth = 16f * frame.textFactor
            pipHeight = 10f * frame.textFactor
            heatHeight = frame.u(6f)
            gapRow = frame.u(10f)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            width = frame.u(200f) * frame.factor
            bottom = frame.height - frame.u(14f)
            numberSize = 28f
            barHeight = frame.u(9f)
            pipWidth = 12f
            pipHeight = 8f
            heatHeight = frame.u(5f)
            gapRow = frame.u(7f)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            width = frame.u(PORTRAIT_INTEGRITY_WIDTH_DP) * frame.factor
            bottom = frame.portraitClustersBottom
            numberSize = 28f
            barHeight = frame.u(9f)
            pipWidth = 12f
            pipHeight = 8f
            heatHeight = frame.u(4f)
            gapRow = frame.u(5f)
        }
    }
    // Dash pips + heat bar row.
    val capacity = dashChargeCapacity(engine.dashHeatCost)
    val ready = dashChargesReady(engine.heat, engine.dashHeatCost, engine.overheated)
    val rowHeight = max(d(pipHeight), heatHeight)
    val rowTop = bottom - rowHeight
    val pipsWidth = drawKkPips(Offset(left, rowTop + (rowHeight - d(pipHeight)) * 0.5f), capacity, ready,
        if (engine.overheated) Kk.Ink4 else roles.you, sizeDp = pipHeight, widthDp = pipWidth, gapDp = 3f, emptyColor = Kk.Ink4)
    if (engine.dashPhaseTime > 0f && ready < capacity) {
        // The pip a dash just spent flashes out.
        val spend = (engine.dashPhaseTime / DASH_PHASE_SECONDS).coerceIn(0f, 1f)
        val x = left + ready * (d(pipWidth) + d(3f))
        drawPath(HudDrawCache.paths.sheared(HudPath.DASH_SPEND.ordinal, x, rowTop + (rowHeight - d(pipHeight)) * 0.5f,
            x + d(pipWidth), rowTop + (rowHeight + d(pipHeight)) * 0.5f), roles.you, alpha = spend)
    }
    val heatLeft = left + pipsWidth + frame.u(10f)
    val heat = HudDrawCache.rect(HudRect.HEAT, heatLeft, rowTop + (rowHeight - heatHeight) * 0.5f, left + width,
        rowTop + (rowHeight + heatHeight) * 0.5f)
    drawKkMeter(heat, engine.heat / GameplayRenderModel.MAX_HEAT, roles.heat, roles,
        stripes = if (engine.overheated) KkMeterStripes.THREAT else KkMeterStripes.NONE, time = renderTime,
        threat = engine.overheated)

    // Segmented integrity bar with hit ghost; pulses at heart rate when critical.
    val barBottom = rowTop - gapRow
    val barTop = barBottom - barHeight
    val integrity = (engine.hp / engine.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val ghost = if (memory != null) memory.integrityGhost(renderTime) else 0f
    val pulse = if (critical) heartbeat(renderTime) else 1f
    val barColor = if (critical) roles.threat.copy(alpha = 0.45f + 0.55f * pulse) else Kk.Bone
    val bar = HudDrawCache.rect(HudRect.INTEGRITY, left, barTop, left + width, barBottom)
    drawKkMeter(bar, integrity, barColor, roles, segments = integritySegments(engine.maxHp), ghost = ghost,
        ghostColor = roles.threat, threat = critical)
    if (engine.build.invulnerable) {
        // A bone frame flickers around integrity while nothing can hurt it.
        val pad = frame.u(3f)
        drawPath(HudDrawCache.paths.sheared(HudPath.INVULNERABLE_BAR.ordinal, bar.left - pad, bar.top - pad, bar.right + pad, bar.bottom + pad),
            Kk.Bone, alpha = 0.5f + 0.5f * kotlin.math.sin(renderTime * 20f).let { it * it }, style = kkStroke(frame.u(1.5f)))
    }

    drawNumberRow(engine, measurer, frame, roles, left, left + width, barTop - frame.u(if (frame.regular) 8f else 5f), bottom, numberSize,
        ghost, critical)
}

/**
 * The number row on [numberBottom]: shield cells and the ability right-aligned at [right], the integrity number (split
 * channels while a hit ghost drains) in the room left of them.
 */
private fun DrawScope.drawNumberRow(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    frame: HudFrame,
    roles: KkRolePalette,
    left: Float,
    right: Float,
    numberBottom: Float,
    bottom: Float,
    numberSize: Float,
    ghost: Float,
    critical: Boolean,
) {
    var anchorBottom = numberBottom - frame.u(if (frame.regular) 6f else 4f)
    var blockLeft = right
    if (engine.maxShield > 0f) {
        val cells = shieldCells(engine.maxShield)
        val filled = filledShieldCells(engine.shield, engine.maxShield, cells)
        val cellWidth = if (frame.regular) 24f * frame.textFactor else 16f
        val cellHeight = if (frame.regular) 9f * frame.textFactor else 7f
        val gap = if (frame.regular) 3f else 2f
        val total = cells * d(cellWidth) + (cells - 1) * d(gap)
        drawKkPips(Offset(right - total, anchorBottom - d(cellHeight)), cells, filled, roles.shield, sizeDp = cellHeight,
            widthDp = cellWidth, gapDp = gap, emptyColor = roles.shield, emptyOutlined = true)
        blockLeft = right - total
        anchorBottom -= d(cellHeight) + frame.u(7f)
    }
    blockLeft = min(blockLeft, drawAbilityMeter(engine, frame, roles, right, anchorBottom))
    val room = blockLeft - frame.u(6f) - left
    val value = (engine.hp.coerceAtLeast(0f).toDouble() * engine.numberScale).toLong()
    val style = measurer.typography.wideStyle(numberSize, tabular = true, trackingEm = colorTracking(if (critical) 3 else 0))
    if (ghost > 0f) {
        // Split channels while the hit ghost drains, each copy with its own layouts.
        val split = d(2f) * (ghost * 4f).coerceAtMost(1f)
        drawIntegrityNumber(measurer, value, measurer.typography.wideStyle(numberSize, tabular = true, trackingEm = colorTracking(1)),
            left - split, numberBottom, room, roles.threat, alpha = 0.7f)
        drawIntegrityNumber(measurer, value, measurer.typography.wideStyle(numberSize, tabular = true, trackingEm = colorTracking(2)),
            left + split, numberBottom, room, roles.shield, alpha = 0.55f)
    }
    val numberWidth = drawIntegrityNumber(measurer, value, style, left, numberBottom, room, if (critical) roles.threat else Kk.Bone)
    HudLayoutProbe.record(HudBlock.INTEGRITY, left, numberBottom - digitBoxHeight(measurer, style), max(right, left + numberWidth), bottom)
}

/**
 * The displayed integrity [value] (integrity × the run's number scale) on [bottom]: below 1,000 from cached digit
 * layouts, which stay allocation-free while it changes; above, its compact text ("1.08M"), built again only when the
 * shown text changes (keyed by [compactNumberRounded]). A number wider than [room] shrinks about its bottom-left corner
 * to fit beside the shield cells. Returns the drawn width.
 */
private fun DrawScope.drawIntegrityNumber(
    measurer: TextMeasurer,
    value: Long,
    style: androidx.compose.ui.text.TextStyle,
    x: Float,
    bottom: Float,
    room: Float,
    color: Color,
    alpha: Float = 1f,
): Float {
    val top = bottom - digitBoxHeight(measurer, style)
    val layout = if (value < COMPACT_NUMBER_THRESHOLD) null else {
        val language = measurer.language
        measureKkText(measurer, HudScratch.integrity.of(language, compactNumberRounded(value)) { formatCompactNumber(value, language) }, style)
    }
    val natural = if (layout != null) layout.size.width.toFloat() else kkTabularNumberWidth(measurer, value, style)
    val fit = if (natural > room && room > 0f) room / natural else 1f
    withTransform({ scale(fit, fit, Offset(x, bottom)) }) {
        if (layout == null) drawKkTabularNumber(measurer, value, style, x, top, color, alpha = alpha) else drawKkText(layout, x, top, color, alpha = alpha)
    }
    return natural * fit
}

internal const val DASH_PHASE_SECONDS = 0.24f

/** `kk-heartbeat` opacity over one second: 0.45, 1, 0.55, 0.95, back to 0.45. */
internal fun heartbeat(time: Float): Float {
    val t = kkLoop(time, 1f)
    return when {
        t < 0.12f -> kkLerp(0.45f, 1f, t / 0.12f)
        t < 0.24f -> kkLerp(1f, 0.55f, (t - 0.12f) / 0.12f)
        t < 0.36f -> kkLerp(0.55f, 0.95f, (t - 0.24f) / 0.12f)
        else -> kkLerp(0.95f, 0.45f, (t - 0.36f) / 0.64f)
    }
}

/**
 * Character ability: the form's glyph ([KkFormGeometry]) and a small sheared meter of the mechanic's
 * readiness, segmented by what it counts (facets, nodes, charges, drones, streak). Returns its left edge.
 */
private fun DrawScope.drawAbilityMeter(engine: GameplayRenderModel, frame: HudFrame, roles: KkRolePalette, right: Float, bottom: Float): Float {
    val ability = engine.characterAbility
    val width = frame.u(if (frame.regular) 64f else 44f)
    val height = frame.u(if (frame.regular) 5f else 4f)
    val armed = ability.charge >= 1f || ability.parryWindow > 0f
    val rect = HudDrawCache.rect(HudRect.ABILITY, right - width, bottom - height, right, bottom)
    val segments = when (engine.coreShape) {
        CoreShape.PRISM, CoreShape.TESSERACT, CoreShape.HEXAGON -> 4
        CoreShape.CRESCENT -> 2
        CoreShape.PENTAGON -> 5
        else -> 0
    }
    drawKkMeter(rect, ability.charge, if (armed) roles.you else Kk.Bone2, roles, segments = segments)
    val glyph = height * 1.6f
    val center = Offset(right - width - glyph - frame.u(4f), bottom - height * 0.5f)
    val index = engine.coreShape.formIndex()
    val path = KkFormPaths.of(index, glyph)
    val color = if (armed) roles.you else Kk.Bone2
    translate(center.x, center.y) {
        if (KkFormGeometry.isStroked(index)) {
            drawPath(path, color, style = kkStroke(max(1f, KkFormGeometry.strokeWidth(index, glyph))))
        } else {
            drawPath(path, color)
        }
    }
    return center.x - glyph
}

/** Bottom-center: speed number stretched by velocity tier, velocity ladder, overdrive bar. */
internal fun DrawScope.drawKineticGauge(
    engine: GameplayRenderModel,
    hostMeasurer: TextMeasurer,
    frame: HudFrame,
    renderTime: Float,
    memory: HudPresentationMemory?,
) {
    // The speed number is display type: the same size at every text-size setting.
    val measurer = HudMeasurers.display(hostMeasurer)
    val roles = measurer.roles
    val tier = engine.velocityTier
    val ticks = if (frame.regular) 30 else 20
    val lit = velocityLadderLit(engine.speed, ticks)
    val hotFrom = ticks * 2 / 3
    val maxFrom = if (tier >= 4) (lit - ticks / 10).coerceAtLeast(hotFrom) else ticks
    val speedRole = when {
        tier >= 4 -> 2
        lit >= hotFrom -> 1
        else -> 0
    }
    val speedColor = when (speedRole) {
        2 -> roles.threat
        1 -> roles.you
        else -> Kk.Bone
    }
    val overdriveActive = engine.overdriveTime > 0f
    val drain = if (overdriveActive && memory != null) memory.overdriveDrain(engine) else 0f
    val overdrive = if (drain > 0f) drain else engine.overdriveCharge / 100f
    val stripes = if (overdriveActive) KkMeterStripes.YOU else KkMeterStripes.NONE
    val value = engine.speed.toLong()
    val stretch = speedStretch(tier)
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            val width = min(frame.u(360f), (frame.width - frame.margin * 2f) * 0.3f)
            val left = frame.width * 0.5f - width * 0.5f
            val bottom = frame.height - frame.u(24f)
            val od = HudDrawCache.rect(HudRect.OVERDRIVE, left, bottom - frame.u(5f), left + width, bottom)
            drawKkMeter(od, overdrive, roles.you, roles, stripes = stripes, time = renderTime)
            drawBurnout(engine, od, roles)
            val ladderBottom = od.top - frame.u(8f)
            val ladder = HudDrawCache.rect(HudRect.LADDER, left, ladderBottom - frame.u(16f), left + width, ladderBottom)
            drawKkTickLadder(ladder, roles, ticks, lit, hotFrom, maxFrom)
            drawSpeedNumber(measurer, value, measurer.typography.wideStyle(frame.t(56f), tabular = true, trackingEm = colorTracking(speedRole)), frame.width * 0.5f,
                ladder.top - frame.u(10f), KkAlign.CENTER, stretch, speedColor, od)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            val width = frame.u(230f) * frame.factor
            val left = frame.margin + frame.u(248f) * frame.factor
            val bottom = frame.height - frame.u(12f)
            val od = HudDrawCache.rect(HudRect.OVERDRIVE, left, bottom - frame.u(4f), left + width, bottom)
            drawKkMeter(od, overdrive, roles.you, roles, stripes = stripes, time = renderTime)
            drawBurnout(engine, od, roles)
            val ladderBottom = od.top - frame.u(6f)
            val ladder = HudDrawCache.rect(HudRect.LADDER, left, ladderBottom - frame.u(12f), left + width, ladderBottom)
            drawKkTickLadder(ladder, roles, ticks, lit, hotFrom, maxFrom)
            drawSpeedNumber(measurer, value, measurer.typography.wideStyle(30f, tabular = true, trackingEm = colorTracking(speedRole)), left, ladder.top - frame.u(5f),
                KkAlign.START, stretch, speedColor, od)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val right = frame.width - frame.margin
            val bottom = frame.portraitClustersBottom
            // Right of the minimap when it shows: the meter starts after it and the number fits the room beside it.
            val minimapRight = HudMinimapGeometry.portraitRight(engine, frame)
            val gap = frame.u(8f)
            var left = right - frame.u(160f) * frame.factor
            if (!minimapRight.isNaN()) left = max(left, minimapRight + gap)
            val od = HudDrawCache.rect(HudRect.OVERDRIVE, left, bottom - frame.u(4f), right, bottom)
            drawKkMeter(od, overdrive, roles.you, roles, stripes = stripes, time = renderTime)
            drawBurnout(engine, od, roles)
            drawSpeedNumber(measurer, value, measurer.typography.wideStyle(30f, tabular = true, trackingEm = colorTracking(speedRole)), right, od.top - frame.u(7f),
                KkAlign.END, stretch, speedColor, od, maxWidth = if (minimapRight.isNaN()) Float.POSITIVE_INFINITY else right - minimapRight - gap)
        }
    }
}

/**
 * Overdrive Cascade's Burnout (no charge during Overdrive and for 3 s after): the overdrive meter
 * is hatched in the threat hue while charge cannot build.
 */
private fun DrawScope.drawBurnout(engine: GameplayRenderModel, meter: androidx.compose.ui.geometry.Rect, roles: KkRolePalette) {
    if (!engine.build.overdriveBurnout) return
    drawKkHatch(meter, roles.threat.copy(alpha = BURNOUT_HATCH_ALPHA))
}

private const val BURNOUT_HATCH_ALPHA = 0.55f

/**
 * Speed number anchored at its bottom edge, stretched horizontally and leaning −8°. It changes
 * every frame, so it is drawn from cached digit layouts.
 */
private fun DrawScope.drawSpeedNumber(
    measurer: TextMeasurer,
    value: Long,
    style: androidx.compose.ui.text.TextStyle,
    x: Float,
    bottom: Float,
    align: KkAlign,
    tierStretch: Float,
    color: Color,
    gauge: androidx.compose.ui.geometry.Rect,
    maxWidth: Float = Float.POSITIVE_INFINITY,
) {
    val top = bottom - digitBoxHeight(measurer, style)
    // Within [maxWidth] with its lean, the number gives up stretch (the tier cue) before it would run past it.
    val natural = kkTabularNumberWidth(measurer, value, style)
    val stretch = min(tierStretch, (maxWidth - (bottom - top) * SPEED_LEAN) / natural)
    withTransform({
        scale(stretch, 1f, Offset(x, bottom))
        kkShear(bottom, -8f)
    }) {
        drawKkTabularNumber(measurer, value, style, x, top, color, align)
    }
    // The stretched number with its lean (the top leans right by tan 8° of its height) and the gauge under it (the
    // ladder and the overdrive meter).
    val width = natural * stretch
    val lean = (bottom - top) * SPEED_LEAN
    val left = when (align) {
        KkAlign.START -> x
        KkAlign.CENTER -> x - width * 0.5f
        KkAlign.END -> x - width
    }
    HudLayoutProbe.record(HudBlock.SPEED, min(left - lean, gauge.left), top, max(left + width + lean, gauge.right), gauge.bottom)
}

/** tan(8°): the speed number's lean. */
private const val SPEED_LEAN = 0.1405f
