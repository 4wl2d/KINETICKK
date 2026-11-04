// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*
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
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.d
import kinetickk.foundation.design.drawKkPips
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.kkBoxHeight
import kinetickk.foundation.design.kkPulse
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.monoStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.foundation.design.KkLayerHues
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

/*
 * Dimensions on the HUD: the plate in the Rebirth strip's slot (the layer's sigil, the visit timer over its remaining
 * meter, the objective's pips and the layer's gauge: Grace, Heat or Desync, or a broken course), the portal of a layer
 * change, the Nadir's burning heat edge, and the dimension objective's lines on the trial panel. Cached layouts, paths
 * and brushes: a steady frame allocates nothing.
 */

/**
 * The visit's plate in the Rebirth strip's slot (from [left] up to [right], [height] tall about [centerY]): the layer's
 * sigil on an ink disc ringed in the layer's accent, the visit timer (m:ss, tabular mono in bone, pulsing over its last
 * ten seconds, never in threat) over a meter of the time left in the accent, then a pip per course ring or ember (filled
 * as counted; boss objectives show their boss bar instead). Where the timer does not fit only the sigil is drawn, and
 * below a legible plate nothing. Realspace never draws it and returns false; during a visit it returns true, so the strip
 * yields its whole slot (C20: the plate before the Baby mode pacifier and the tier plate).
 */
internal fun DrawScope.drawDimensionPlate(
    engine: GameplayRenderModel,
    hostMeasurer: TextMeasurer,
    frame: HudFrame,
    left: Float,
    right: Float,
    centerY: Float,
    height: Float,
): Boolean {
    val dimension = engine.dimension
    val layer = dimension.layer
    if (layer == WorldLayer.BASE) return false
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
            // A broken course greys its pips and strikes them through.
            val pips = drawObjectivePips(dimension.goal, dimension.progress, pipsLeft, right, centerY, height,
                if (dimension.failed) Kk.Mute else accent, frame)
            if (pips > 0f) {
                drawnRight = pipsLeft + pips
                HudLayoutProbe.record(HudBlock.DIMENSION_PIPS, pipsLeft, top, drawnRight, top + height)
                if (dimension.failed) {
                    drawLine(roles.threat, Offset(pipsLeft - frame.u(2f), centerY + height * 0.28f), Offset(drawnRight + frame.u(2f),
                        centerY - height * 0.28f), frame.u(2f))
                }
            }
        }
        val gaugeLeft = drawnRight + frame.u(10f)
        val gauge = drawLayerGauge(engine, hostMeasurer, frame, gaugeLeft, right, centerY, height)
        if (gauge > 0f) {
            drawnRight = gaugeLeft + gauge
            HudLayoutProbe.record(HudBlock.DIMENSION_GAUGE, gaugeLeft, top, drawnRight, top + height)
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

/**
 * The layer's gauge right of the plate's marks (from [left] up to [right]): what the Core's speed does here, named
 * over a thin bar. The Zenith's Grace (the speed's share of the pace, lit gold at pace, where halos mend), the Nadir's
 * Heat (the scorch warning, in threat once it ticks) and the Archive's Desync (hostile time and its factor; the tick
 * marks real time). A broken Ascent names its broken course instead, in threat. Returns the drawn width, 0 when it
 * does not fit.
 */
private fun DrawScope.drawLayerGauge(
    engine: GameplayRenderModel,
    hostMeasurer: TextMeasurer,
    frame: HudFrame,
    left: Float,
    right: Float,
    centerY: Float,
    height: Float,
): Float {
    val dimension = engine.dimension
    val roles = hostMeasurer.roles
    val language = hostMeasurer.language
    val resource: DimensionHudText
    val color: Color
    var share = 0f
    var tick = -1f
    when {
        dimension.failed -> {
            resource = DimensionHudText.CourseBroken
            color = roles.threat
        }
        dimension.layer == WorldLayer.ZENITH -> {
            resource = DimensionHudText.Grace
            val pace = dimension.pace.coerceAtLeast(1f)
            share = (engine.speed / pace / GRACE_GAUGE_SPAN).coerceIn(0f, 1f)
            tick = 1f / GRACE_GAUGE_SPAN
            color = if (engine.speed >= pace) roles.gold.base else Kk.Mute
        }
        dimension.layer == WorldLayer.NADIR -> {
            resource = DimensionHudText.Heat
            share = dimension.scorching.coerceIn(0f, 1f)
            color = when {
                dimension.scorching >= 1f -> roles.threat
                dimension.scorching > 0f -> roles.heat
                else -> Kk.Mute
            }
        }
        else -> {
            resource = DimensionHudText.Desync
            share = (dimension.timeScale / DESYNC_GAUGE_SPAN).coerceIn(0f, 1f)
            tick = 1f / DESYNC_GAUGE_SPAN
            color = KkLayerHues.Signal
        }
    }
    // Desync carries its factor in steps of 0.05 (21 strings per language, each built once), so a Core speeding up
    // reads a handful of cached labels instead of a new one every frame.
    val label = if (resource == DimensionHudText.Desync) desyncLabel(dimension.timeScale, language) else language.cachedText(resource)
    val layout = measureKkText(HudMeasurers.display(hostMeasurer), label,
        hostMeasurer.typography.monoStyle(height * GAUGE_SIZE_SHARE / frame.density, weight = FontWeight.Bold, lineHeightEm = 1f), uppercase = true)
    val width = max(layout.size.width.toFloat(), frame.u(GAUGE_MIN_DP))
    if (left + width > right) return 0f
    val barHeight = frame.u(2f)
    val textHeight = layout.kkBoxHeight
    val top = centerY - (textHeight + frame.u(3f) + barHeight) * 0.5f
    drawKkText(layout, left, top, color, valign = KkVAlign.TOP)
    val barTop = top + textHeight + frame.u(3f)
    drawRect(Kk.Line2, Offset(left, barTop), Size(width, barHeight))
    if (share > 0f) drawRect(color, Offset(left, barTop), Size(width * share, barHeight))
    if (tick > 0f) {
        drawRect(Kk.Bone, Offset(left + width * tick - frame.u(0.75f), barTop - frame.u(1.5f)), Size(frame.u(1.5f), barHeight + frame.u(3f)))
    }
    return width
}

/** "Desync ×0.45": the gauge's Desync label at [scale] rounded to its 0.05 step, in [language] (the locale's separator). */
internal fun desyncLabel(scale: Float, language: AppLanguage): String {
    val step = (scale / DESYNC_STEP).roundToInt().coerceIn(0, DESYNC_STEPS - 1)
    val slot = language.ordinal * DESYNC_STEPS + step
    return DesyncLabels[slot] ?: run {
        val hundredths = step * 5
        "${language.text(DimensionHudText.Desync)} ×${hundredths / 100}${WorldStrings.decimalSeparator(language)}" +
            (hundredths % 100).toString().padStart(2, '0')
    }.also { DesyncLabels[slot] = it }
}

/** A gauge label resolved once per language. */
private fun AppLanguage.cachedText(resource: DimensionHudText): String {
    val slot = resource.ordinal * AppLanguage.entries.size + ordinal
    return GaugeTexts[slot] ?: text(resource).also { GaugeTexts[slot] = it }
}

private const val DESYNC_STEP = 0.05f
/** Factors 0.00..1.50 in steps of 0.05 (the Archive spans 0.25..1.25). */
private const val DESYNC_STEPS = 31
private val DesyncLabels = arrayOfNulls<String>(DESYNC_STEPS * AppLanguage.entries.size)
private val GaugeTexts = arrayOfNulls<String>(DimensionHudText.entries.size * AppLanguage.entries.size)

/** Grace's bar spans the speed up to this many paces; Desync's the factor up to this. */
private const val GRACE_GAUGE_SPAN = 1.5f
private const val DESYNC_GAUGE_SPAN = 1.25f

/** The gauge's text size as a share of the plate's height, and its narrowest bar (reference dp). */
private const val GAUGE_SIZE_SHARE = 0.3f
private const val GAUGE_MIN_DP = 44f

/** The timer's alpha: 1, then pulsing between 0.55 and 1 once a second over the visit's last ten seconds. */
internal fun dimensionTimerAlpha(remaining: Float, time: Float): Float =
    if (remaining > TIMER_PULSE_SECONDS) 1f else TIMER_PULSE_LOW + (1f - TIMER_PULSE_LOW) * (1f - kkPulse(time, 1f, KkEase.Out))

/**
 * The layer change as a portal, over the screen states and under the HUD modules, [DIMENSION_TRANSITION_MS] after the
 * layer changed ([HudPresentationMemory] records the edge; never on a host's first frame or a new run's): first the old
 * layer implodes, an iris of ink closing on the Core with a jagged rim burning in the old layer's accent and streaks
 * pulled in after it; then the new layer explodes out of the Core, the iris reopening with a rim in the new accent
 * behind a flash and shards thrown outward. One cached ink stroke per screen size and the rim's stateless jags: zero
 * allocation.
 */
internal fun DrawScope.drawDimensionTransition(
    engine: GameplayRenderModel,
    roles: KkRolePalette,
    renderTime: Float,
    memory: HudPresentationMemory? = null,
) {
    val transition = memory?.dimensionTransition ?: return
    val progress = transition.progress(renderTime)
    if (progress < 0f || progress >= 1f) return
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val core = worldOnScreen(engine, engine.renderCoreX, engine.renderCoreY, 0f, 0f)
    // The iris must reach the farthest corner from the Core.
    val farX = max(core.x, w - core.x)
    val farY = max(core.y, h - core.y)
    val reach = sqrt(farX * farX + farY * farY) + d(24f)
    val ink = DimensionHudScratch.inkStroke(max(w, h) * 3f)
    val implode = progress < IMPLODE_SHARE
    val t = if (implode) KkEase.In.transform(progress / IMPLODE_SHARE) else KkEase.Out.transform((progress - IMPLODE_SHARE) / (1f - IMPLODE_SHARE))
    val radius = if (implode) reach * (1f - t) else reach * t
    val accent = riftAccent(if (implode) transition.from else engine.dimension.layer, roles)
    drawCircle(Kk.Ink, radius + ink.width * 0.5f, core, style = ink)
    if (!implode) {
        val flash = HudDrawCache.brush(HudBrush.DIMENSION_FLASH, 1f, 1f, 0f, 0f, accent) {
            Brush.radialGradient(0f to Kk.Bone, 0.25f to accent, 1f to accent.copy(alpha = 0f), center = Offset.Zero, radius = 1f)
        }
        val flashRadius = d(60f) + radius * 0.45f
        translate(core.x, core.y) {
            scale(flashRadius, flashRadius, Offset.Zero) { drawCircle(flash, 1f, Offset.Zero, alpha = (1f - t) * 0.85f) }
        }
    }
    drawIrisRim(core, radius, accent, renderTime, if (implode) 1f else 1f - 0.6f * t)
    for (index in 0 until IRIS_SEGMENTS) {
        val dx = IrisDirections[index * 2]
        val dy = IrisDirections[index * 2 + 1]
        if (implode) {
            // Streaks pulled in after the closing rim.
            val from = radius + d(40f) + d(90f) * (1f - t)
            drawLine(accent.copy(alpha = 0.25f + 0.6f * t), Offset(core.x + dx * from, core.y + dy * from),
                Offset(core.x + dx * (radius + d(6f)), core.y + dy * (radius + d(6f))), d(2f))
        } else {
            // Shards thrown out behind the opening rim.
            val at = radius * (0.7f + 0.25f * IrisJags[index % IrisJags.size])
            drawLine(Kk.Bone.copy(alpha = 0.8f * (1f - t)), Offset(core.x + dx * at, core.y + dy * at),
                Offset(core.x + dx * (at + d(18f)), core.y + dy * (at + d(18f))), d(3f))
        }
    }
}

/** The iris's rim at [radius] about [center]: a glow and a jagged burning edge in [accent], at [alpha], its jags stepping at 12 Hz. */
private fun DrawScope.drawIrisRim(center: Offset, radius: Float, accent: Color, time: Float, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(accent.copy(alpha = 0.3f * alpha), radius, center, style = kkStroke(d(16f)))
    val step = (time * 12f).toInt()
    val edge = accent.copy(alpha = alpha)
    for (index in 0 until IRIS_SEGMENTS) {
        val next = (index + 1) % IRIS_SEGMENTS
        val jag = radius + d(10f) * IrisJags[(index + step) % IrisJags.size]
        val nextJag = radius + d(10f) * IrisJags[(next + step) % IrisJags.size]
        drawLine(edge, Offset(center.x + IrisDirections[index * 2] * jag, center.y + IrisDirections[index * 2 + 1] * jag),
            Offset(center.x + IrisDirections[next * 2] * nextJag, center.y + IrisDirections[next * 2 + 1] * nextJag), d(3f))
    }
}

/** The portal's length (longer than a shutter, so the tear reads), the share its implosion takes and its rim's segments. */
internal const val DIMENSION_TRANSITION_MS = 900
private const val IMPLODE_SHARE = 0.38f
private const val IRIS_SEGMENTS = 24
private val IrisDirections = FloatArray(IRIS_SEGMENTS * 2) { index ->
    val angle = (index / 2) * 2f * PI.toFloat() / IRIS_SEGMENTS
    if (index % 2 == 0) cos(angle) else sin(angle)
}
/** The rim's jags as shares of its 10 dp reach, cycled round it. */
private val IrisJags = floatArrayOf(0.2f, 1f, 0.45f, 0.85f, 0f, 0.65f, 0.3f, 0.95f, 0.1f, 0.55f, 0.75f)

/**
 * The Nadir's heat edge while the Core is too slow, with flame tongues along the top and bottom: a radial band hugging
 * the screen's edges in the heat role (never
 * threat), as strong as the warning ([kinetickk.ball.gameplay.nucleus.render.DimensionProjection.scorching]: 0.30 at
 * full) and pulsing at 1.2 Hz once the scorch ticks. One cached gradient per size and palette.
 */
internal fun DrawScope.drawScorchEdge(engine: GameplayRenderModel, roles: KkRolePalette, renderTime: Float) {
    val alpha = scorchEdgeAlpha(engine.dimension.scorching, renderTime)
    if (alpha <= 0f) return
    drawScreenVignette(HudBrush.SCORCH_EDGE, SCORCH_CLEAR_STOP, roles.heat, alpha)
    // Flame tongues licking up from the bottom edge and down from the top, flickering on their own beat.
    val tongues = DimensionHudScratch.flames(size.width, d(FLAME_DP))
    val flicker = 0.75f + 0.25f * sin(renderTime * 11f)
    translate(0f, size.height) {
        scale(1f, -flicker, Offset.Zero) { drawPath(tongues, roles.heat, alpha = (alpha * 1.6f).coerceAtMost(1f)) }
    }
    scale(1f, 0.6f + 0.4f * flicker, Offset.Zero) { drawPath(tongues, roles.heat, alpha = (alpha * 1.1f).coerceAtMost(1f)) }
}

/** The flame tongues' tallest reach (reference dp). */
private const val FLAME_DP = 46f

/** The heat edge's alpha for a [scorching] warning at [time]: 0.30 × the warning, pulsing at 1.2 Hz at full. */
internal fun scorchEdgeAlpha(scorching: Float, time: Float): Float {
    if (scorching <= 0f) return 0f
    val strength = SCORCH_EDGE_ALPHA * min(1f, scorching)
    if (scorching < 1f) return strength
    return strength * (SCORCH_PULSE_LOW + (1f - SCORCH_PULSE_LOW) * kkPulse(time, 1f / SCORCH_PULSE_HZ, KkEase.Out))
}

/**
 * The layer edge the portal plays on, kept by [HudPresentationMemory]: [observe] records the frame's layer and, on a
 * change between frames of one run ([armed]), the layer left and the moment it changed. A host's first frame and a new
 * run's only record.
 */
internal class DimensionTransitionMemory {
    private var lastLayer: WorldLayer? = null
    private var changedAt = Float.NEGATIVE_INFINITY

    /** The layer the last change left (its implosion's accent). */
    var from: WorldLayer = WorldLayer.BASE
        private set

    fun observe(layer: WorldLayer, renderTime: Float, armed: Boolean) {
        val last = lastLayer
        if (armed && last != null && layer != last) {
            changedAt = renderTime
            from = last
        }
        lastLayer = layer
    }

    /** A new run drops a portal the last one was playing. */
    fun forget() {
        changedAt = Float.NEGATIVE_INFINITY
    }

    /** The portal's progress over [DIMENSION_TRANSITION_MS] at [renderTime]: below 0 or from 1 on, none plays. */
    fun progress(renderTime: Float): Float =
        if (changedAt == Float.NEGATIVE_INFINITY) -1f else (renderTime - changedAt) * 1000f / DIMENSION_TRANSITION_MS
}

/** The trial panel's first-row mark for a dimension objective: its layer's sigil, [size] px across. */
internal fun DrawScope.drawDimensionTrialIcon(trial: TrialProjection, center: Offset, size: Float, color: Color) =
    drawDimensionSigil(trial.dimension ?: WorldLayer.BASE, center, size * 0.5f, color)

/**
 * The trial panel's reward line for a dimension objective (Blessing, Pact, Legendary item), or its broken course once
 * it [failed]; cached per line and language.
 */
internal fun dimensionTrialReward(trial: TrialProjection, failed: Boolean, language: AppLanguage): String {
    val resource = if (failed) DimensionHudText.CourseBroken else dimensionTrialRewardText(trial.reward)
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
    val fade = Paint()
    val reward = HudKeyedText()
    private var bounds = Rect.Zero
    private var inkWidth = Float.NaN
    private var ink = Stroke(1f)
    private val tongues = Path()
    private var tonguesWidth = Float.NaN
    private var tonguesHeight = Float.NaN

    /** The portal's ink ring of [width] (three screens: it covers everything outside the iris), rebuilt only on a resize. */
    fun inkStroke(width: Float): Stroke {
        if (width != inkWidth) {
            inkWidth = width
            ink = Stroke(width)
        }
        return ink
    }

    /** A row of flame tongues hanging from the top edge, [height] deep at most, across [width]; rebuilt only on a resize. */
    fun flames(width: Float, height: Float): Path {
        if (width == tonguesWidth && height == tonguesHeight) return tongues
        tonguesWidth = width
        tonguesHeight = height
        tongues.rewind()
        tongues.moveTo(0f, 0f)
        var x = 0f
        var index = 0
        while (x < width) {
            val reach = height * FlameReach[index % FlameReach.size]
            val span = height * (0.55f + 0.25f * FlameReach[(index + 3) % FlameReach.size])
            tongues.quadraticTo(x + span * 0.2f, reach * 0.9f, x + span * 0.5f, reach)
            tongues.quadraticTo(x + span * 0.6f, reach * 0.4f, x + span, 0f)
            x += span
            index++
        }
        tongues.lineTo(width, 0f)
        tongues.close()
        return tongues
    }

    /** The timer's fade layer bounds; a new rect only when they moved. */
    fun layerBounds(left: Float, top: Float, right: Float, bottom: Float): Rect {
        val current = bounds
        if (current.left == left && current.top == top && current.right == right && current.bottom == bottom) return current
        return Rect(left, top, right, bottom).also { bounds = it }
    }
}

/** The sigil fills this share of the plate's disc. */
private const val SIGIL_SHARE = 0.72f

/** The timer's text size as a share of the plate's height. */
private const val TIMER_SIZE_SHARE = 0.44f

/** The timer pulses over the visit's last seconds, down to this alpha. */
private const val TIMER_PULSE_SECONDS = 10f
private const val TIMER_PULSE_LOW = 0.55f

/** The objective's pips: their largest and smallest size (reference dp). */
private const val MAX_PIP_DP = 7f
private const val MIN_PIP_DP = 3f

/** The flame tongues' reaches as shares of the tallest, cycled along the edge. */
private val FlameReach = floatArrayOf(0.55f, 1f, 0.7f, 0.4f, 0.9f, 0.6f, 0.8f)

/** The heat edge: 0.30 at full, clear inside half the corner radius, pulsing at 1.2 Hz down to 0.55 of itself. */
private const val SCORCH_EDGE_ALPHA = 0.30f
private const val SCORCH_CLEAR_STOP = 0.5f
private const val SCORCH_PULSE_HZ = 1.2f
private const val SCORCH_PULSE_LOW = 0.55f
