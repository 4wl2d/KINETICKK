// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_CHIP_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_CHIP_ROW_HEIGHT_DP
import kinetickk.ball.gameplay.interaction.localization.DimensionHudText
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.ball.gameplay.nucleus.render.TrialReward
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.TextResource
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkShape
import kinetickk.foundation.design.KkTime
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.d
import kinetickk.foundation.design.drawKkPips
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.kkBoxHeight
import kinetickk.foundation.design.kkPulse
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.monoStyle
import kotlin.math.max
import kotlin.math.min

/*
 * Dimensions on the HUD: the plate in the Rebirth strip's slot (the layer's sigil, the visit timer over its remaining
 * meter and the objective's pips), the slash shutter of a layer change, the Nadir's heat edge, and the dimension
 * objective's lines on the trial panel. Cached layouts, paths and brushes: a steady frame allocates nothing.
 */

/**
 * The visit's plate in the Rebirth strip's slot (same anchors and right limit as the strip): the layer's sigil on an ink
 * disc ringed in the layer's accent, the visit timer (m:ss, tabular mono in bone, pulsing over its last ten seconds,
 * never in threat) over a meter of the time left in the accent, then a pip per course ring or ember (filled as counted;
 * boss objectives show their boss bar instead). Where the timer does not fit only the sigil is drawn, and below a
 * legible plate nothing. Realspace never draws it and returns false; during a visit it returns true, so the strip yields
 * its whole slot (C20: the plate before the Baby mode pacifier and the tier plate).
 */
internal fun DrawScope.drawDimensionPlate(engine: GameplayRenderModel, hostMeasurer: TextMeasurer, frame: HudFrame): Boolean {
    val dimension = engine.dimension
    val layer = dimension.layer
    if (layer == WorldLayer.BASE) return false
    val centerY: Float
    val height: Float
    val left: Float
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            centerY = HudScratch.badgeTop + (HudScratch.badgeBottom - HudScratch.badgeTop) * 0.5f
            height = min(frame.u(34f), HudScratch.badgeBottom - HudScratch.badgeTop)
            left = HudScratch.badgeRight + frame.u(18f)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            centerY = HudScratch.badgeTop + (HudScratch.badgeBottom - HudScratch.badgeTop) * 0.5f
            height = min(frame.u(28f), HudScratch.badgeBottom - HudScratch.badgeTop)
            left = HudScratch.economyRight + frame.u(12f)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            height = frame.u(PORTRAIT_CHIP_ROW_HEIGHT_DP)
            centerY = frame.top + frame.u(PORTRAIT_CHIP_ROW_DP) + height * 0.5f
            left = HudScratch.economyRight + frame.u(10f)
        }
    }
    val right = if (frame.mode == GameplayLayoutMode.COMPACT_PORTRAIT) {
        min(frame.width - frame.margin - frame.u(PLATE_PORTRAIT_CHAIN_RESERVE_DP), HudScratch.chainLeft - frame.u(10f))
    } else {
        val clock = HudDrawCache.peekLayout(HudText.TIMER)?.size?.width?.toFloat() ?: frame.u(120f)
        frame.width * 0.5f - clock * 0.5f - frame.u(14f)
    }
    val plate = min(height, right - left)
    if (plate < frame.u(MIN_PLATE_DP)) return true
    val roles = hostMeasurer.roles
    val accent = riftAccent(layer, roles)
    val top = centerY - height * 0.5f
    val radius = plate * 0.5f
    val center = Offset(left + radius, centerY)
    drawCircle(Kk.Ink2, radius, center)
    drawCircle(accent, radius - frame.u(0.75f), center, style = kkStroke(frame.u(1.5f)))
    drawDimensionSigil(layer, center, radius * SIGIL_SHARE, accent)
    var drawnRight = left + plate

    val measurer = HudMeasurers.display(hostMeasurer)
    val timer = HudDrawCache.layout(HudText.DIMENSION_TIMER, measurer, WorldStrings.timer(dimension.remaining),
        hostMeasurer.typography.monoStyle(height * TIMER_SIZE_SHARE / frame.density, weight = FontWeight.Bold, lineHeightEm = 1f))
    val timerLeft = drawnRight + frame.u(6f)
    val timerWidth = timer.size.width.toFloat()
    if (timerLeft + timerWidth <= right) {
        val meterGap = frame.u(3f)
        val meterHeight = frame.u(2f)
        val textHeight = timer.kkBoxHeight
        val columnTop = centerY - (textHeight + meterGap + meterHeight) * 0.5f
        val alpha = dimensionTimerAlpha(dimension.remaining, engine.renderElapsed)
        // The pulse fades the text through a layer: a text drawn in a new colour would reshape its paragraph each frame.
        if (alpha < 1f) drawIntoCanvas {
            it.saveLayer(DimensionHudScratch.layerBounds(timerLeft, columnTop, timerLeft + timerWidth, columnTop + textHeight),
                DimensionHudScratch.fade.apply { this.alpha = alpha })
        }
        drawKkText(timer, timerLeft, columnTop, Kk.Bone, valign = KkVAlign.TOP)
        if (alpha < 1f) drawIntoCanvas { it.restore() }
        val meterTop = columnTop + textHeight + meterGap
        val share = if (dimension.duration > 0f) (dimension.remaining / dimension.duration).coerceIn(0f, 1f) else 0f
        drawRect(Kk.Line2, Offset(timerLeft, meterTop), Size(timerWidth, meterHeight))
        if (share > 0f) drawRect(accent, Offset(timerLeft, meterTop), Size(timerWidth * share, meterHeight))
        drawnRight = timerLeft + timerWidth
        val objective = dimension.objective
        if (objective == DimensionObjective.ASCENT || objective == DimensionObjective.HARVEST) {
            val pipsLeft = drawnRight + frame.u(8f)
            val pips = drawObjectivePips(dimension.goal, dimension.progress, pipsLeft, right, centerY, height, accent, frame)
            if (pips > 0f) drawnRight = pipsLeft + pips
        }
    }
    HudLayoutProbe.record(HudBlock.REBIRTH, left, top, drawnRight, top + height)
    return true
}

/**
 * The objective's pips from [left] up to [right] in one row, or in two when that lets them grow (a narrow slot), never
 * taller than the plate's [height]; nothing below a legible size. Returns the drawn width (0 when nothing was drawn).
 */
private fun DrawScope.drawObjectivePips(
    goal: Int,
    progress: Int,
    left: Float,
    right: Float,
    centerY: Float,
    height: Float,
    accent: Color,
    frame: HudFrame,
): Float {
    if (goal <= 0) return 0f
    val gap = frame.u(2f)
    var size = 0f
    var rows = 1
    for (candidate in 1..2) {
        val columns = (goal + candidate - 1) / candidate
        val fit = min(min(frame.u(MAX_PIP_DP), (height - (candidate - 1) * gap) / candidate), (right - left - (columns - 1) * gap) / columns)
        if (fit > size + 0.01f) {
            size = fit
            rows = candidate
        }
    }
    if (size < frame.u(MIN_PIP_DP)) return 0f
    val columns = (goal + rows - 1) / rows
    val filled = progress.coerceIn(0, goal)
    val blockTop = centerY - (rows * size + (rows - 1) * gap) * 0.5f
    var width = 0f
    for (row in 0 until rows) {
        val first = row * columns
        val count = min(columns, goal - first)
        width = max(width, drawKkPips(Offset(left, blockTop + row * (size + gap)), count, (filled - first).coerceIn(0, count), accent,
            sizeDp = size / density, gapDp = gap / density, emptyColor = Kk.Line2))
    }
    return width
}

/** The timer's alpha: 1, then pulsing between 0.55 and 1 once a second over the visit's last ten seconds. */
internal fun dimensionTimerAlpha(remaining: Float, time: Float): Float =
    if (remaining > TIMER_PULSE_SECONDS) 1f else TIMER_PULSE_LOW + (1f - TIMER_PULSE_LOW) * (1f - kkPulse(time, 1f, KkEase.Out))

/**
 * The slash shutter of a layer change, over the screen states and under the HUD modules: [KkTime.Shutter] ms after the
 * layer changed ([HudPresentationMemory] records the edge; never on a host's first frame or a new run's), three ink
 * slabs sheared at the slash angle start together over the whole screen and slide right at their own speeds (the right
 * one fastest), each edged in 3 dp of the new layer's accent, wiping the new layer into view from the left. One
 * cached slab and edge path per screen size, drawn under translations: zero allocation. The edge leads the wipe: it
 * runs along each slab's left side, where the new layer comes into view.
 */
internal fun DrawScope.drawDimensionShutter(
    engine: GameplayRenderModel,
    roles: KkRolePalette,
    renderTime: Float,
    memory: HudPresentationMemory? = null,
) {
    val shutter = memory?.dimensionShutter ?: return
    val progress = shutter.progress(renderTime)
    if (progress < 0f || progress >= 1f) return
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val geometry = dimensionShutterGeometry(w, h, d(SHUTTER_OVERLAP_DP))
    val accent = riftAccent(engine.dimension.layer, roles)
    val body = HudDrawCache.paths.sheared(HudPath.SHUTTER_0.ordinal, 0f, 0f, geometry.width, h, KkShape.Slash)
    val edge = HudDrawCache.paths.sheared(HudPath.SHUTTER_1.ordinal, 0f, 0f, d(SHUTTER_EDGE_DP), h, KkShape.Slash)
    for (index in 0 until SHUTTER_SLABS) {
        translate(geometry.slabLeft(index, progress), 0f) {
            drawPath(body, Kk.Ink)
            drawPath(edge, accent)
        }
    }
}

/**
 * Where the shutter's slabs go on a [width] × [height] screen: each [width] wide (0.42 of the screen, or wider when the
 * slash's lean needs it to cover the screen at the start) overlapping by [overlap], the first starting where its top
 * corner meets the screen's left edge; slab i slides right by `p × (1 + 0.12 i) × travel`, the travel long enough
 * (1.6 screens, or the screen and the lean) for even the slowest slab to leave the screen by the end.
 */
internal fun dimensionShutterGeometry(width: Float, height: Float, overlap: Float): DimensionShutterGeometry =
    DimensionHudScratch.geometry.update(width, height, overlap)

/** The shutter's geometry for one screen size (draw-thread scratch, rebuilt only when the size changes). */
internal class DimensionShutterGeometry {
    private var keyWidth = Float.NaN
    private var keyHeight = Float.NaN
    private var keyOverlap = Float.NaN

    /** One slab's width at its vertical middle. */
    var width = 0f
        private set
    private var lean = 0f
    private var step = 0f
    private var travel = 0f

    fun update(screenWidth: Float, screenHeight: Float, overlap: Float): DimensionShutterGeometry {
        if (screenWidth == keyWidth && screenHeight == keyHeight && overlap == keyOverlap) return this
        keyWidth = screenWidth
        keyHeight = screenHeight
        keyOverlap = overlap
        lean = screenHeight * KkShape.SlashRatio
        width = max(screenWidth * SHUTTER_SLAB_SHARE, (screenWidth + lean) / SHUTTER_SLABS + overlap)
        step = width - overlap
        travel = max(screenWidth * SHUTTER_TRAVEL_SCREENS, screenWidth + lean + overlap)
        return this
    }

    /** The left edge (at the vertical middle) of slab [index] at [progress] 0..1. */
    fun slabLeft(index: Int, progress: Float): Float = -lean * 0.5f + index * step + progress * (1f + SHUTTER_SPEED_STEP * index) * travel
}

/**
 * The Nadir's heat edge while the Core is too slow: a radial band hugging the screen's edges in the heat role (never
 * threat), as strong as the warning ([kinetickk.ball.gameplay.nucleus.render.DimensionProjection.scorching]: 0.30 at
 * full) and pulsing at 1.2 Hz once the scorch ticks. One cached gradient per size and palette.
 */
internal fun DrawScope.drawScorchEdge(engine: GameplayRenderModel, roles: KkRolePalette, renderTime: Float) {
    val alpha = scorchEdgeAlpha(engine.dimension.scorching, renderTime)
    if (alpha <= 0f) return
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val heat = roles.heat
    val radius = w * 0.5f * SQRT_2
    val brush = HudDrawCache.brush(HudBrush.SCORCH_EDGE, w, h, 0f, 0f, heat) {
        Brush.radialGradient(
            0f to Color.Transparent, SCORCH_CLEAR_STOP to Color.Transparent, 1f to heat,
            center = Offset(w * 0.5f, h * 0.5f), radius = radius,
        )
    }
    // The circle of the farthest corner squashed into the screen's ellipse, like the critical vignette.
    scale(1f, h / w, Offset(w * 0.5f, h * 0.5f)) {
        drawRect(brush, Offset(0f, h * 0.5f - w * 0.5f), Size(w, w), alpha)
    }
}

/** The heat edge's alpha for a [scorching] warning at [time]: 0.30 × the warning, pulsing at 1.2 Hz at full. */
internal fun scorchEdgeAlpha(scorching: Float, time: Float): Float {
    if (scorching <= 0f) return 0f
    val strength = SCORCH_EDGE_ALPHA * min(1f, scorching)
    if (scorching < 1f) return strength
    return strength * (SCORCH_PULSE_LOW + (1f - SCORCH_PULSE_LOW) * kkPulse(time, 1f / SCORCH_PULSE_HZ, KkEase.Out))
}

/**
 * The layer edge the shutter plays on, kept by [HudPresentationMemory]: [observe] records the frame's layer and, on a
 * change between frames of one run ([armed]), the moment it changed. A host's first frame and a new run's only record.
 */
internal class DimensionShutterMemory {
    private var lastLayer: WorldLayer? = null
    private var changedAt = Float.NEGATIVE_INFINITY

    fun observe(layer: WorldLayer, renderTime: Float, armed: Boolean) {
        val last = lastLayer
        if (armed && last != null && layer != last) changedAt = renderTime
        lastLayer = layer
    }

    /** A new run drops a shutter the last one was playing. */
    fun forget() {
        changedAt = Float.NEGATIVE_INFINITY
    }

    /** The shutter's progress over [KkTime.Shutter] at [renderTime]: below 0 or from 1 on, none plays. */
    fun progress(renderTime: Float): Float =
        if (changedAt == Float.NEGATIVE_INFINITY) -1f else (renderTime - changedAt) * 1000f / KkTime.Shutter
}

/** The trial panel's label for a dimension objective. */
internal fun dimensionTrialLabel(): TextResource = DimensionHudText.Objective

/** The trial panel's first-row mark for a dimension objective: its layer's sigil, [size] px across. */
internal fun DrawScope.drawDimensionTrialIcon(trial: TrialProjection, center: Offset, size: Float, color: Color) =
    drawDimensionSigil(trial.dimension ?: WorldLayer.BASE, center, size * 0.5f, color)

/** The trial panel's reward for a dimension objective (Blessing, Pact, Legendary item), cached per reward and language. */
internal fun dimensionTrialReward(trial: TrialProjection, language: AppLanguage): String {
    val resource = dimensionTrialRewardText(trial.reward)
    return DimensionHudScratch.reward.of(resource, language.ordinal.toLong()) { language.text(resource) }
}

/** The reward label of a dimension objective's [reward]. */
internal fun dimensionTrialRewardText(reward: TrialReward): TextResource = when (reward) {
    TrialReward.BLESSING -> DimensionHudText.RewardBlessing
    TrialReward.PACT -> DimensionHudText.RewardPact
    TrialReward.TROVE -> DimensionHudText.RewardLegendaryItem
    else -> HudRedesignText.RewardUnknown
}

/** Draw-thread scratch of the dimension HUD. */
private object DimensionHudScratch {
    val geometry = DimensionShutterGeometry()
    val fade = Paint()
    val reward = HudKeyedText()
    private var bounds = Rect.Zero

    /** The timer's fade layer bounds; a new rect only when they moved. */
    fun layerBounds(left: Float, top: Float, right: Float, bottom: Float): Rect {
        val current = bounds
        if (current.left == left && current.top == top && current.right == right && current.bottom == bottom) return current
        return Rect(left, top, right, bottom).also { bounds = it }
    }
}

/** The plate's smallest legible size (reference dp); the sigil fills this share of its disc. */
private const val MIN_PLATE_DP = 14f
private const val SIGIL_SHARE = 0.72f

/** The timer's text size as a share of the plate's height. */
private const val TIMER_SIZE_SHARE = 0.44f

/** The timer pulses over the visit's last seconds, down to this alpha. */
private const val TIMER_PULSE_SECONDS = 10f
private const val TIMER_PULSE_LOW = 0.55f

/** The objective's pips: their largest and smallest size (reference dp). */
private const val MAX_PIP_DP = 7f
private const val MIN_PIP_DP = 3f

/** Chip-row width (reference dp) the portrait plate leaves for the chain at the row's end (the strip's reserve). */
private const val PLATE_PORTRAIT_CHAIN_RESERVE_DP = 72f

/** The shutter: three slabs of 0.42 screens, overlapping by 2 dp, led by a 3 dp edge, each 12 % faster than the last. */
private const val SHUTTER_SLABS = 3
private const val SHUTTER_SLAB_SHARE = 0.42f
private const val SHUTTER_OVERLAP_DP = 2f
private const val SHUTTER_EDGE_DP = 3f
private const val SHUTTER_SPEED_STEP = 0.12f
private const val SHUTTER_TRAVEL_SCREENS = 1.6f

/** The heat edge: 0.30 at full, clear inside half the corner radius, pulsing at 1.2 Hz down to 0.55 of itself. */
private const val SCORCH_EDGE_ALPHA = 0.30f
private const val SCORCH_CLEAR_STOP = 0.5f
private const val SCORCH_PULSE_HZ = 1.2f
private const val SCORCH_PULSE_LOW = 0.55f
private const val SQRT_2 = 1.4142135f
