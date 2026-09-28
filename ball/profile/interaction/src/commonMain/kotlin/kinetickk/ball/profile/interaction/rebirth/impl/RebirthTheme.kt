// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.drawProfileGrid
import kinetickk.foundation.design.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * One Rebirth tier theme (`tokens.json rebirthTiers`, index = target tier): accent, background
 * mix(accent 8 %, ink 92 %), halftone density, orbit rings 1 + tier / 2, a threat band of stripes
 * from tier 5 and streaks. Tier 10 is the event horizon: pure black, grayscale, black hole.
 */
internal class RebirthTheme(val tier: Int, roles: KkRolePalette) {
    val eventHorizon: Boolean = KkRebirthTiers.isEventHorizon(tier)
    val accent: Color = tone(KkRebirthTiers.color(tier))
    val background: Color = KkRebirthTiers.background(tier)
    val soft: Color = accent.copy(alpha = 0.35f)
    val halftone: Color = accent.copy(alpha = if (eventHorizon) 0.16f else 0.09f + tier.coerceIn(0, 10) * 0.008f)
    val halftoneSpacing: Float = max(6f, 12f - (tier.coerceIn(0, 10) / 2).toFloat())
    val rings: Int = KkRebirthTiers.ringCount(tier)
    val band: Boolean = KkRebirthTiers.hasHazardBand(tier) && !eventHorizon
    val streaks: Int = minOf(8, 1 + tier.coerceIn(0, 10))

    /** Increases of hostile modifiers read in the threat role (grayscale at the event horizon). */
    val threat: Color = tone(roles.threat)

    /** The role palette for this screen: `you` becomes the tier accent (`--volt: acc`). */
    val roles: KkRolePalette = roles.copy(you = accent, threat = threat)

    /** Ladder color of [tier] under this theme's grayscale rule. */
    fun tierColor(tier: Int): Color = tone(KkRebirthTiers.color(tier))

    /** `filter: grayscale(1) contrast(1.15)` at the event horizon; identity otherwise. */
    fun tone(color: Color): Color = if (eventHorizon) rebirthGray(color) else color
}

internal fun rebirthGray(color: Color): Color {
    val luma = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    val value = ((luma - 0.5f) * 1.15f + 0.5f).coerceIn(0f, 1f)
    return Color(value, value, value, color.alpha)
}

/** Where the backdrop sits for a layout: the orbit center and the size factor of the board. */
private fun backdropOrigin(frame: ProfileFrame, layout: RebirthLayout?): Pair<Offset, Float> = when {
    frame.mode == ProfileLayoutMode.REGULAR || layout == null -> Offset(frame.x(300f), frame.d(430f)) to frame.unit
    frame.mode == ProfileLayoutMode.COMPACT_LANDSCAPE ->
        Offset(layout.numeral.left + frame.d(110f), layout.numeral.center.y + frame.d(30f)) to frame.unit * 0.5f
    else -> Offset(frame.width * 0.5f, layout.numeral.center.y + frame.d(20f)) to frame.unit * 0.55f
}

/**
 * The tier 5+ band of stripes. On the regular board it crosses the backdrop under the ladder
 * (rotated −4°); phones stack content there, so it runs straight along the header's lower edge.
 */
internal fun rebirthBandRect(frame: ProfileFrame, center: Offset, u: Float, width: Float): Rect =
    if (frame.mode == ProfileLayoutMode.REGULAR) {
        val top = center.y + 260f * u
        Rect(-100f * u, top, width + 100f * u, top + 16f * u)
    } else {
        Rect(0f, frame.headerHeight - frame.d(3f), width, frame.headerHeight + frame.d(4f))
    }

/** The full-bleed Rebirth backdrop for [theme] at [time] seconds. */
internal fun DrawScope.drawRebirthBackdrop(
    frame: ProfileFrame,
    theme: RebirthTheme,
    layout: RebirthLayout?,
    time: Float,
) {
    drawRect(theme.background)
    drawProfileGrid(frame)
    val (center, u) = backdropOrigin(frame, layout)
    val tier = theme.tier.coerceIn(0, 10)
    // Halftone disc (left −60, top 40, 900 × 900 on the board), radially faded.
    val halftoneRect = Rect(center.x + (-360f) * u, center.y + (-390f) * u, center.x + 540f * u, center.y + 510f * u)
    drawKkRadialFade(halftoneRect) {
        drawRect(RebirthHalftone.brush(theme.halftoneSpacing * u, 1.7f * u, theme.halftone), halftoneRect.topLeft, halftoneRect.size)
    }
    for (index in 0 until theme.rings) {
        val radius = (180f + index * 60f) * u
        val period = max(24f, 40f - index * 4f - tier)
        val turn = (time / period) * 360f * if (index % 2 == 0) 1f else -1f
        val dashed = index % 2 == 0
        val stroke = if (dashed) RebirthStrokes.dashed(1.5f * u, 4f * u) else kkStroke(1f * u)
        rotate(turn, center) {
            drawCircle(theme.soft, radius, center, style = stroke)
            drawCircle(theme.accent, 5f * u, Offset(center.x, center.y - radius))
        }
    }
    val streakTravel = 900f * u
    for (index in 0 until theme.streaks) {
        val duration = 1.8f - tier * 0.08f + (index % 3) * 0.3f
        val delay = index * 0.37f
        val progress = kkLoop(time, duration, delay)
        val alpha = if (progress < 0.15f) progress / 0.15f else 1f - (progress - 0.15f) / 0.85f
        val y = size.height * (120f + ((index * 137) % 600)) / 810f
        val x = size.width + 60f * u - streakTravel * progress
        drawRect(theme.soft, Offset(x, y), Size((120f + (index % 3) * 90f) * u, 2f * u), alpha = alpha.coerceIn(0f, 1f))
    }
    if (theme.band) {
        val band = rebirthBandRect(frame, center, u, size.width)
        if (frame.mode == ProfileLayoutMode.REGULAR) {
            rotate(-4f, Offset(size.width * 0.5f, band.top)) {
                drawKkStripes(band, theme.accent.copy(alpha = 0.35f), Kk.Ink.copy(alpha = 0.35f), time)
            }
        } else {
            drawKkStripes(band, theme.accent.copy(alpha = 0.35f), Kk.Ink.copy(alpha = 0.35f), time)
        }
    }
    if (theme.eventHorizon) {
        drawBlackHole(Offset(center.x + 290f * u, center.y), 720f * u, u, time)
    }
}

/**
 * The armed screen's hazard stripes along the top and bottom edges (`Rebirth--tier04-armed`), in
 * the tier accent and ink, moving with [time]. Drawn above the scrolling content and the pinned
 * Advance band, so neither the scroll fade nor the band covers the bottom one.
 */
internal fun DrawScope.drawRebirthArmedStripes(frame: ProfileFrame, theme: RebirthTheme, time: Float) {
    val stripe = rebirthArmedStripe(frame)
    drawKkStripes(Rect(0f, 0f, size.width, stripe), theme.accent, Kk.Ink, time)
    drawKkStripes(Rect(0f, size.height - stripe, size.width, size.height), theme.accent, Kk.Ink, time)
}

/** Height (px) of the armed screen's hazard stripes along the top and bottom edges (8 px on the board). */
internal fun rebirthArmedStripe(frame: ProfileFrame): Float = frame.d(8f)

/**
 * Tier 10 black hole (`.bh`): lensing halo, tilted accretion disk turning once per 24 s, the
 * bright upper arc, the photon ring with a stacked-stroke glow, the event horizon with its
 * shadow, and sparks spiralling in. No blur; brushes are cached per geometry.
 */
internal fun DrawScope.drawBlackHole(center: Offset, size: Float, u: Float, time: Float) {
    val half = size * 0.5f
    drawCircle(BlackHoleBrushes.lens(center, half * 1.36f), half * 1.36f, center)
    withTransform({
        rotate(-14f, center)
        scale(1f, 0.3f, center)
    }) {
        rotate(time * 15f, center) {
            val disk = BlackHoleBrushes.disk(center)
            drawCircle(disk, half * 0.47f, center, style = kkStroke(half * 0.22f))
            drawCircle(disk, half * 0.33f, center, alpha = 0.4f, style = kkStroke(half * 0.06f))
            drawCircle(disk, half * 0.68f, center, alpha = 0.35f, style = kkStroke(half * 0.2f))
        }
    }
    val arcRadius = size * 0.29f
    drawArc(Color.White.copy(alpha = 0.9f), 210f - 14f, 120f, false, Offset(center.x - arcRadius, center.y - arcRadius),
        Size(arcRadius * 2f, arcRadius * 2f), style = Stroke(5f * u, cap = StrokeCap.Round))
    val ring = size * 0.19f
    drawCircle(Color.White, ring, center, alpha = 0.07f, style = kkStroke(28f * u))
    drawCircle(Color.White, ring, center, alpha = 0.16f, style = kkStroke(16f * u))
    drawCircle(Color.White, ring, center, alpha = 0.3f, style = kkStroke(8f * u))
    drawCircle(Color.White, ring, center, style = kkStroke(2f * u))
    val hole = size * 0.175f
    drawCircle(BlackHoleBrushes.shadow(center, hole, hole + 86f * u), hole + 86f * u, center)
    drawCircle(Color.Black, hole, center)
    val scaleS = size / 720f
    for (index in 0 until 14) {
        val duration = 2.6f + (index % 3) * 0.5f
        val progress = kkLoop(time, duration, -index * 0.23f)
        val eased = KkEase.In.transform(progress)
        val alpha = if (progress < 0.15f) progress / 0.15f else 1f - (progress - 0.15f) / 0.85f
        val angle = ((index * 26f) + 300f * eased) * (PI.toFloat() / 180f)
        val start = kkLerp((300f + (index % 4) * 30f) * scaleS, 18f * scaleS, eased)
        val length = (14f + (index % 3) * 10f) * scaleS
        val dx = cos(angle)
        val dy = sin(angle)
        drawLine(Color.White, Offset(center.x + dx * start, center.y + dy * start),
            Offset(center.x + dx * (start + length), center.y + dy * (start + length)), 3f * scaleS,
            cap = StrokeCap.Round, alpha = alpha.coerceIn(0f, 1f))
    }
}

/** Advance sequence length (`kk-adv-*`), in milliseconds. */
internal const val REBIRTH_ADVANCE_MS = 1600

private val ShakeStops = floatArrayOf(0f, 0.30f, 0.33f, 0.37f, 0.42f, 0.48f, 0.55f, 0.62f, 1f)
private val ShakeX = floatArrayOf(0f, 0f, -9f, 8f, -5f, 3f, -2f, 0f, 0f)
private val ShakeY = floatArrayOf(0f, 0f, 4f, -6f, -3f, 3f, 1f, 0f, 0f)

/** `kk-adv-shake` offset at [progress] (short shake between 30 % and 62 %), in board px. */
internal fun rebirthAdvanceShake(progress: Float): Offset {
    if (progress <= ShakeStops[1] || progress >= ShakeStops[7]) return Offset.Zero
    for (index in 1 until ShakeStops.size) {
        if (progress <= ShakeStops[index]) {
            val t = (progress - ShakeStops[index - 1]) / (ShakeStops[index] - ShakeStops[index - 1])
            return Offset(kkLerp(ShakeX[index - 1], ShakeX[index], t), kkLerp(ShakeY[index - 1], ShakeY[index], t))
        }
    }
    return Offset.Zero
}

/** `kk-adv-dim`: the screen underneath dims from 30 % to 40 % and stays dimmed. */
internal fun rebirthAdvanceDim(progress: Float): Float = when {
    progress <= 0.3f -> 0f
    progress >= 0.4f -> 1f
    else -> KkEase.Out.transform((progress - 0.3f) / 0.1f)
}

/** `kk-adv-flash`: one stepped inversion between 31 % and 36 %. */
internal fun rebirthAdvanceFlash(progress: Float): Boolean = progress >= 0.31f && progress < 0.36f

/** `kk-adv-num`: numeral scale, rotation and alpha at [progress] (slam from 2.6 / −10° after 28 %). */
internal fun rebirthAdvanceNumeral(progress: Float): Triple<Float, Float, Float> = when {
    progress < 0.28f -> Triple(2.6f, -10f, 0f)
    progress < 0.40f -> {
        val t = KkEase.Pull.transform((progress - 0.28f) / 0.12f)
        Triple(kkLerp(2.6f, 0.92f, t), kkLerp(-10f, -3f, t), t.coerceIn(0f, 1f))
    }
    progress < 0.48f -> Triple(kkLerp(0.92f, 1f, KkEase.Pull.transform((progress - 0.4f) / 0.08f)), -3f, 1f)
    else -> Triple(1f, -3f, 1f)
}

/**
 * The advance animation (`Rebirth-Advance`, 1.6 s): shutter slabs at −27° sweep across in 620 ms
 * (Snap, staggered 60 ms), a stepped threat-free inversion flash, the dim from 30 %, a ring
 * burst, and the new numeral slamming in from 2.6× / −10° after 28 % (Pull), all under a short
 * shake between 30 % and 62 %. Draws nothing outside 0 < [progress] < 1.
 */
internal fun DrawScope.drawRebirthAdvance(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    progress: Float,
    theme: RebirthTheme,
    tier: String,
    direction: String,
    label: String,
) {
    if (progress <= 0f || progress >= 1f) return
    val u = frame.unit
    val regular = frame.regular
    val time = progress * REBIRTH_ADVANCE_MS / 1000f
    val shake = rebirthAdvanceShake(progress)
    translate(shake.x * u, shake.y * u) {
        val dim = rebirthAdvanceDim(progress)
        if (dim > 0f) drawRect(Kk.Ink, alpha = 0.86f * dim)
        // Shutter: three slabs (accent, bone, ink with an accent edge) travel from −1500 to +1700 px in 620 ms.
        val slabLeft = floatArrayOf(300f, 560f, 760f)
        val slabWidth = floatArrayOf(280f, 220f, 320f)
        val lean = size.height * 0.5f * tan(27f * PI.toFloat() / 180f)
        val span = size.width / (1440f * u)
        for (index in 0..2) {
            val local = (time - index * 0.06f) / 0.62f
            if (local <= 0f || local >= 1f) continue
            val travel = kkLerp(-1500f, 1700f, KkEase.Snap.transform(local)) * span
            val left = (slabLeft[index] * span + travel) * u
            val width = slabWidth[index] * span * u
            val path = AdvancePaths.slab(index, left, width, lean, size.height)
            val color = when (index) {
                0 -> theme.accent
                1 -> Kk.Bone
                else -> Kk.Ink
            }
            drawPath(path, color)
            if (index == 2) drawPath(path, theme.accent, style = kkStroke(3f * u))
        }
        if (rebirthAdvanceFlash(progress)) drawRect(Kk.Bone, blendMode = BlendMode.Difference)
        val ringCenter = Offset(size.width * 0.5f, size.height * 400f / 810f)
        drawKkRingBurst(ringCenter, 250f * u, (time - 0.5f) / 1.1f, theme.accent, strokeDp = 6f * u / density)
        val (numeralScale, rotation, alpha) = rebirthAdvanceNumeral(progress)
        if (alpha > 0f) {
            val numeralSize = if (regular) 340f else 190f
            val labelLayout = measureKkText(measurer, label, measurer.typography.monoStyle((if (regular) 11f else 10f) * frame.k), uppercase = true)
            val numeralLayout = measureKkText(measurer, tier, measurer.typography.wideStyle(numeralSize * frame.k, tabular = true,
                lineHeightEm = 0.88f))
            val nameLayout = kinetickk.ball.profile.interaction.fitKkText(measurer, direction, (if (regular) 60f else 36f) * frame.k,
                size.width - frame.d(32f), minFactor = 0.4f) { measurer.typography.condStyle(it) }
            val blockHeight = labelLayout.kkBoxHeight + numeralLayout.kkBoxHeight + nameLayout.kkBoxHeight
            val top = (size.height - blockHeight) * 0.5f - frame.d(20f)
            val pivot = Offset(size.width * 0.5f, top + blockHeight * 0.5f)
            withTransform({
                rotate(rotation, pivot)
                scale(numeralScale, numeralScale, pivot)
            }) {
                drawKkText(labelLayout, size.width * 0.5f, top, theme.accent, align = KkAlign.CENTER, alpha = alpha)
                drawKkText(numeralLayout, size.width * 0.5f, top + labelLayout.kkBoxHeight, theme.accent, align = KkAlign.CENTER, alpha = alpha)
                drawKkText(nameLayout, size.width * 0.5f, top + labelLayout.kkBoxHeight + numeralLayout.kkBoxHeight, Kk.Bone,
                    align = KkAlign.CENTER, alpha = alpha)
            }
        }
    }
}

/** Halftone tile per spacing/dot/color (the tier sets the density); rebuilt only on change. */
private object RebirthHalftone {
    private var spacing = -1f
    private var radius = -1f
    private var color = Color.Unspecified
    private var brush: Brush? = null

    fun brush(spacing: Float, radius: Float, color: Color): Brush {
        val cached = brush
        if (cached != null && spacing == this.spacing && radius == this.radius && color == this.color) return cached
        val tile = max(2, spacing.roundToInt())
        val bitmap = ImageBitmap(tile, tile)
        Canvas(bitmap).drawCircle(Offset(tile * 0.5f, tile * 0.5f), radius, Paint().apply {
            this.color = color
            isAntiAlias = true
        })
        return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)).also {
            brush = it
            this.spacing = spacing
            this.radius = radius
            this.color = color
        }
    }
}

private object RebirthStrokes {
    private var width = -1f
    private var dash = -1f
    private var stroke: Stroke? = null

    fun dashed(width: Float, dash: Float): Stroke {
        val cached = stroke
        if (cached != null && width == this.width && dash == this.dash) return cached
        return Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))).also {
            stroke = it
            this.width = width
            this.dash = dash
        }
    }
}

private object BlackHoleBrushes {
    private var lensKey = Offset.Unspecified
    private var lensRadius = -1f
    private var lens: Brush? = null
    private var diskKey = Offset.Unspecified
    private var disk: Brush? = null
    private var shadowKey = Offset.Unspecified
    private var shadowInner = -1f
    private var shadow: Brush? = null

    fun lens(center: Offset, radius: Float): Brush {
        val cached = lens
        if (cached != null && center == lensKey && radius == lensRadius) return cached
        return Brush.radialGradient(
            0f to Color.Transparent, 0.34f to Color.Transparent, 0.46f to Color.White.copy(alpha = 0.1f), 0.64f to Color.Transparent,
            center = center, radius = radius,
        ).also { lens = it; lensKey = center; lensRadius = radius }
    }

    fun disk(center: Offset): Brush {
        val cached = disk
        if (cached != null && center == diskKey) return cached
        return Brush.sweepGradient(
            0f to Color.White.copy(alpha = 0.05f), 0.12f to Color.White, 0.26f to Color.White.copy(alpha = 0.15f),
            0.48f to Color.White.copy(alpha = 0.85f), 0.62f to Color.White.copy(alpha = 0.08f), 0.8f to Color.White,
            1f to Color.White.copy(alpha = 0.05f),
            center = center,
        ).also { disk = it; diskKey = center }
    }

    fun shadow(center: Offset, inner: Float, outer: Float): Brush {
        val cached = shadow
        if (cached != null && center == shadowKey && inner == shadowInner) return cached
        return Brush.radialGradient(
            0f to Color.Black, (inner / outer) to Color.Black, 1f to Color.Transparent,
            center = center, radius = outer,
        ).also { shadow = it; shadowKey = center; shadowInner = inner }
    }
}

/** Reused shutter slab paths (−27° lean) for the advance sequence. */
private object AdvancePaths {
    private val paths = Array(3) { Path() }

    fun slab(index: Int, left: Float, width: Float, lean: Float, height: Float): Path = paths[index].apply {
        rewind()
        moveTo(left + lean, 0f)
        lineTo(left + width + lean, 0f)
        lineTo(left + width - lean, height)
        lineTo(left - lean, height)
        close()
    }
}

/** Seconds of the Rebirth ambient clock wrapped to a long period (keeps float precision). */
internal fun rebirthAmbientTime(seconds: Float): Float = seconds - floor(seconds / 1200f) * 1200f
