// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.kkLoop
import kinetickk.foundation.design.kkMix
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Legendary scene (animated, looping on the card time, behind the text).

/**
 * Resonance Fork: a gold tuning fork stands in the lower face. Once a loop it is struck: its tines
 * shiver (after-images in an element pair), concentric rings ring out from behind the icon plate
 * (the reaction itself) across the whole face and keep echoing long after the strike, and two
 * standing waves, anchored between gold clamps, cross into a moiré band. The rings and waves cycle
 * through every element hue (a reaction is an element pair). Every layer is periodic in
 * [ResonanceForkLoopSeconds], so the loop has no seam.
 */
internal fun DrawScope.drawResonanceForkArt(frame: RewardArtFrame, time: Float) {
    val g = frame.cache.floats(SlotGeometry, G_COUNT) { forkGeometry(frame, it) }
    val phase = kkLoop(time, ResonanceForkLoopSeconds) * ResonanceForkLoopSeconds
    val ring = forkEnvelope(phase)
    val elements = frame.roles.elements
    val hueA = cycleHue(elements, phase / ResonanceForkLoopSeconds * elements.size)
    val hueB = cycleHue(elements, phase / ResonanceForkLoopSeconds * elements.size + PartnerOffset)
    val center = Offset(g[G_CX], g[G_RING_Y])

    // The strike: a soft gold bloom at the tines that settles to a faint hum.
    val flash = forkFlash(phase)
    val bloom = frame.cache.brush(BrushBloom) {
        Brush.radialGradient(
            0f to frame.gold.copy(alpha = 0.5f), 0.3f to frame.gold.copy(alpha = 0.24f), 0.65f to frame.gold.copy(alpha = 0.06f),
            1f to Color.Transparent,
            center = center, radius = g[G_BLOOM],
        )
    }
    drawCircle(bloom, g[G_BLOOM], center, alpha = 0.10f + 0.16f * ring + 0.34f * flash)

    // The echo rings spread from behind the icon plate into the open face beside and below it.
    drawForkRings(frame, g, phase, Offset(g[G_ECHO_X], g[G_ECHO_Y]))
    drawForkWaves(frame, g, phase, ring, hueA, hueB)
    drawForkBody(frame, g, phase, ring, flash, hueA, hueB)
}

/** The echoing rings: one per emission, alternating the element pair of its moment, easing outward and fading slowly. */
private fun DrawScope.drawForkRings(frame: RewardArtFrame, g: FloatArray, phase: Float, center: Offset) {
    val elements = frame.roles.elements
    val r0 = g[G_R0]
    val reach = g[G_R_MAX] - r0
    val thin = frame.cache.stroke(g[G_HAIR])
    val glow = frame.cache.stroke(g[G_GLOW])
    for (k in 0 until RingEmissions) {
        val emitted = k * RingInterval + RingOffset
        var age = phase - emitted
        if (age < 0f) age += ResonanceForkLoopSeconds
        if (age >= RingLife) continue
        val life = age / RingLife
        val radius = r0 + reach * (1f - (1f - life) * (1f - life))
        val distance = 1f - smooth((radius - r0 - reach * 0.25f) / (reach * 0.75f))
        // A ring carries the ringing level it leaves the tines with, swells in over its first moment and fades slowly.
        val alpha = forkEnvelope(emitted + StrikeAttack) * smooth(life / RingSwell) * (1f - life).pow(1.35f) * distance
        if (alpha < 0.004f) continue
        val position = emitted / ResonanceForkLoopSeconds * elements.size + if (k % 2 == 0) 0f else PartnerOffset
        val hue = cycleHue(elements, position)
        drawCircle(hue, radius, center, alpha = 0.13f * alpha, style = glow)
        drawCircle(hue, radius, center, alpha = 0.55f * alpha, style = thin)
    }
}

/**
 * Two standing waves pinned between gold clamps, each drawn as the classic long exposure of a
 * vibrating string: a family of snapshots sweeping through the lobes between fixed nodes. The waves
 * have two and three lobes, so their families cross at shifting angles and beat into moiré fringes.
 */
private fun DrawScope.drawForkWaves(frame: RewardArtFrame, g: FloatArray, phase: Float, ring: Float, hueA: Color, hueB: Color) {
    val swell = 0.6f + 0.4f * ring
    val curves = g[G_LINES].toInt()
    val hair = frame.cache.stroke(g[G_HAIR])
    val turn = 2f * PI.toFloat()
    drawWaveFamily(frame, g, SlotWaveA, HumpsA, swell, curves, turn * phase / WavePeriodA, hueA, hair)
    drawWaveFamily(frame, g, SlotWaveB, HumpsB, swell * 0.8f, curves, turn * phase / WavePeriodB + 0.7f, hueB, hair)
    // Gold clamps hold the ends; gold pins mark the three-lobe wave's inner nodes.
    val clamps = frame.cache.path(SlotClamps) { clampsPath(g, this) }
    drawPath(clamps, frame.gold, alpha = 0.6f)
}

/**
 * One wave's [curves] snapshots at [share] of the full amplitude, their phases spread evenly from
 * [phase]. A snapshot is the cached curve of the nearest amplitude level, scaled to its exact
 * amplitude (by at most about a fifth, so its hairline keeps its weight): changing a path every
 * frame would allocate, scaling a cached one does not.
 */
private fun DrawScope.drawWaveFamily(
    frame: RewardArtFrame,
    g: FloatArray,
    slot: Int,
    humps: Int,
    share: Float,
    curves: Int,
    phase: Float,
    hue: Color,
    stroke: Stroke,
) {
    val pivot = Offset(0f, g[G_BAND_Y])
    for (c in 0 until curves) {
        val a = share * cos(phase + 2f * PI.toFloat() * c / curves)
        val level = waveLevel(abs(a))
        val curve = frame.cache.path(slot + level) { wavePath(g, this, humps, level) }
        if (level == WaveLevels - 1) {
            drawPath(curve, hue, alpha = WaveAlpha, style = stroke)
        } else {
            scale(1f, a / WaveStep.pow(level), pivot) { drawPath(curve, hue, alpha = WaveAlpha, style = stroke) }
        }
    }
}

/** The cached amplitude level nearest [share] (levels fall by [WaveStep]; the last one is the flat string). */
private fun waveLevel(share: Float): Int {
    if (share <= 0f) return WaveLevels - 1
    val level = (ln(1f / share) / ln(1f / WaveStep) + 0.5f).toInt()
    return min(max(level, 0), WaveLevels - 1)
}

/** A standing wave of [humps] half-waves across the band at amplitude [level] (one cubic per hump). */
private fun wavePath(g: FloatArray, path: Path, humps: Int, level: Int) {
    val left = g[G_BAND_LEFT]
    val right = g[G_BAND_RIGHT]
    val y = g[G_BAND_Y]
    path.moveTo(left, y)
    if (level == WaveLevels - 1) {
        path.lineTo(right, y)
        return
    }
    val w = (right - left) / humps
    val reach = w * HumpControl
    val a = g[G_AMP] * WaveStep.pow(level) * (4f / 3f)
    for (h in 0 until humps) {
        val x = left + h * w
        val dy = if (h % 2 == 0) -a else a
        path.cubicTo(x + reach, y + dy, x + w - reach, y + dy, x + w, y)
    }
}

/** The fork itself: after-images of the shivering tines, a knockout from the waves, a gold body and a bone highlight. */
private fun DrawScope.drawForkBody(frame: RewardArtFrame, g: FloatArray, phase: Float, ring: Float, flash: Float, hueA: Color, hueB: Color) {
    val fork = frame.cache.path(SlotFork) { forkPath(g, this) }
    val tineL = frame.cache.path(SlotTineL) { tinePath(g, this, left = true) }
    val tineR = frame.cache.path(SlotTineR) { tinePath(g, this, left = false) }
    val hair = frame.cache.stroke(g[G_HAIR])
    // The tines swing out and in once per ring; the after-images sit at the swing's ends.
    val shiver = cos(2f * PI.toFloat() * phase / RingInterval)
    val swing = GhostDegrees * (0.4f + 0.6f * ring) * (0.8f + 0.2f * shiver)
    val ghost = 0.2f + 0.4f * ring
    val pivotY = g[G_Y_INNER]
    for (level in 0 until GhostLevels) {
        val degrees = swing * GhostSwing[level]
        val fade = ghost * GhostFade[level]
        rotate(-degrees, Offset(g[G_PIVOT_L], pivotY)) { drawPath(tineL, hueA, alpha = fade, style = hair) }
        rotate(degrees, Offset(g[G_PIVOT_R], pivotY)) { drawPath(tineR, hueB, alpha = fade, style = hair) }
    }
    drawPath(fork, Kk.Ink2, alpha = 0.92f)
    val body = frame.cache.brush(BrushBody) {
        Brush.verticalGradient(
            0f to frame.gold.copy(alpha = 0.34f), 0.55f to frame.gold.copy(alpha = 0.14f), 1f to frame.gold.copy(alpha = 0.26f),
            startY = g[G_Y_TOP], endY = g[G_Y_KNOB],
        )
    }
    drawPath(fork, body)
    // The rim brightens with the strike and glints faintly on every swing.
    val rim = 0.56f + 0.06f * ring * (0.5f + 0.5f * shiver) + 0.25f * flash
    drawPath(fork, frame.gold, alpha = rim, style = frame.cache.stroke(g[G_OUTLINE], StrokeCap.Butt, StrokeJoin.Miter))
    val shine = frame.cache.path(SlotShine) { shinePath(g, this) }
    drawPath(shine, frame.bone, alpha = 0.30f + 0.35f * flash, style = hair)
}

/** Fills [g] with the scene geometry for [frame] (px): the fork in the clear region, the band through its tines. */
private fun forkGeometry(frame: RewardArtFrame, g: FloatArray) {
    val clear = frame.clear
    val top = clear.top + clear.height * (if (frame.compact) 0.2f else 0.27f)
    val bottom = min(frame.bounds.bottom - frame.u(24f), clear.bottom + frame.u(18f))
    val h = max(bottom - top, frame.u(40f))
    val cx = clear.center.x
    val tine = h * 0.5f
    g[G_CX] = cx
    g[G_Y_TOP] = top
    g[G_Y_INNER] = top + tine
    g[G_Y_BASE] = top + tine + h * 0.09f
    g[G_Y_STEM] = top + h * 0.89f
    g[G_Y_KNOB] = top + h
    g[G_TINE] = h * 0.075f
    g[G_GAP] = h * 0.105f
    g[G_STEM] = h * 0.045f
    g[G_KNOB_W] = h * 0.12f
    g[G_PIVOT_L] = cx - g[G_GAP] * 0.5f - g[G_TINE] * 0.5f
    g[G_PIVOT_R] = cx + g[G_GAP] * 0.5f + g[G_TINE] * 0.5f
    g[G_RING_Y] = top + tine * 0.18f
    val icon = frame.icon
    g[G_ECHO_X] = icon.center.x
    g[G_ECHO_Y] = icon.center.y
    g[G_R0] = max(icon.width, icon.height) * 0.56f
    // Out to the far corner of the face, so the last echoes fade across the whole card.
    val far = kotlin.math.hypot(frame.bounds.right - icon.center.x, frame.bounds.bottom - icon.center.y)
    g[G_R_MAX] = far * 0.92f
    g[G_BLOOM] = h * 0.42f
    g[G_BAND_Y] = top + tine * 0.62f
    g[G_BAND_LEFT] = clear.left
    g[G_BAND_RIGHT] = clear.right
    g[G_LINES] = (if (frame.compact) 4 else 6).toFloat()
    g[G_AMP] = h * 0.16f
    g[G_HAIR] = max(frame.dp(1f), frame.u(1.15f))
    g[G_GLOW] = frame.u(4f)
    g[G_OUTLINE] = max(frame.dp(1f), frame.u(1.5f))
    g[G_CLAMP_H] = g[G_AMP] * 0.75f
}

/** The fork silhouette: two square tines on a chamfered U, a stem and a chamfered knob. */
private fun forkPath(g: FloatArray, path: Path) {
    val cx = g[G_CX]
    val t = g[G_TINE]
    val gap = g[G_GAP] * 0.5f
    val top = g[G_Y_TOP]
    val inner = g[G_Y_INNER]
    val base = g[G_Y_BASE]
    val stemTop = g[G_Y_STEM]
    val knob = g[G_Y_KNOB]
    val chamfer = t * 0.9f
    val innerChamfer = gap * 0.45f
    val stem = g[G_STEM] * 0.5f
    val kw = g[G_KNOB_W] * 0.5f
    val kc = kw * 0.4f
    path.moveTo(cx - gap - t, top)
    path.lineTo(cx - gap, top)
    path.lineTo(cx - gap, inner - innerChamfer)
    path.lineTo(cx - gap + innerChamfer, inner)
    path.lineTo(cx + gap - innerChamfer, inner)
    path.lineTo(cx + gap, inner - innerChamfer)
    path.lineTo(cx + gap, top)
    path.lineTo(cx + gap + t, top)
    path.lineTo(cx + gap + t, base - chamfer)
    path.lineTo(cx + gap + t - chamfer, base)
    path.lineTo(cx + stem, base)
    path.lineTo(cx + stem, stemTop)
    path.lineTo(cx + kw - kc, stemTop)
    path.lineTo(cx + kw, stemTop + kc)
    path.lineTo(cx + kw, knob - kc)
    path.lineTo(cx + kw - kc, knob)
    path.lineTo(cx - kw + kc, knob)
    path.lineTo(cx - kw, knob - kc)
    path.lineTo(cx - kw, stemTop + kc)
    path.lineTo(cx - kw + kc, stemTop)
    path.lineTo(cx - stem, stemTop)
    path.lineTo(cx - stem, base)
    path.lineTo(cx - gap - t + chamfer, base)
    path.lineTo(cx - gap - t, base - chamfer)
    path.close()
}

/** The upper part of one tine's outline (it swings the most), for the after-images. */
private fun tinePath(g: FloatArray, path: Path, left: Boolean) {
    val s = if (left) -1f else 1f
    val cx = g[G_CX]
    val inner = cx + s * g[G_GAP] * 0.5f
    val outer = inner + s * g[G_TINE]
    val from = g[G_Y_TOP] + (g[G_Y_INNER] - g[G_Y_TOP]) * 0.55f
    path.moveTo(outer, from)
    path.lineTo(outer, g[G_Y_TOP])
    path.lineTo(inner, g[G_Y_TOP])
    path.lineTo(inner, from)
}

/** The bone highlight: down the left tine's lit face, with a shorter glint on the right tine. */
private fun shinePath(g: FloatArray, path: Path) {
    val cx = g[G_CX]
    val x = cx - g[G_GAP] * 0.5f - g[G_TINE] * 0.62f
    path.moveTo(x, g[G_Y_TOP] + g[G_TINE] * 0.6f)
    path.lineTo(x, g[G_Y_INNER] - g[G_TINE] * 0.4f)
    val xr = cx + g[G_GAP] * 0.5f + g[G_TINE] * 0.38f
    path.moveTo(xr, g[G_Y_TOP] + g[G_TINE] * 0.6f)
    path.lineTo(xr, g[G_Y_TOP] + (g[G_Y_INNER] - g[G_Y_TOP]) * 0.45f)
}

/** Two slim parallelogram clamps holding the band's ends, and a small diamond pin on each inner node of the three-lobe wave. */
private fun clampsPath(g: FloatArray, path: Path) {
    val half = g[G_CLAMP_H]
    val w = g[G_TINE] * 0.42f
    val lean = half * 0.2f
    val y = g[G_BAND_Y]
    val left = g[G_BAND_LEFT]
    val right = g[G_BAND_RIGHT]
    for (x in floatArrayOf(left - w, right)) {
        path.moveTo(x + lean, y - half)
        path.lineTo(x + lean + w, y - half)
        path.lineTo(x - lean + w, y + half)
        path.lineTo(x - lean, y + half)
        path.close()
    }
    val pin = g[G_TINE] * 0.34f
    for (n in 1 until HumpsB) {
        val x = left + (right - left) * n / HumpsB
        path.moveTo(x, y - pin)
        path.lineTo(x + pin, y)
        path.lineTo(x, y + pin)
        path.lineTo(x - pin, y)
        path.close()
    }
}

/** The ringing at [phase] seconds into the loop: a quick strike, a long decay, a hum that never stops. */
private fun forkEnvelope(phase: Float): Float {
    val attack = smooth(phase / StrikeAttack)
    val decay = (exp(-phase / RingDecay) - DecayTail) / (1f - DecayTail)
    return HumFloor + (1f - HumFloor) * attack * decay
}

/** The strike's flash: up with the attack, gone within half a second. */
private fun forkFlash(phase: Float): Float = smooth(phase / StrikeAttack) * exp(-phase / 0.32f)

/**
 * The element hue at [position] (0 until the element count, wrapping): each hue holds, then fades
 * out to bone and the next fades in from it. Two hues are never mixed, so a blend never passes
 * through an in-between colour (magenta and orange would make the threat's pink) or a muddy grey.
 */
private fun cycleHue(elements: List<Color>, position: Float): Color {
    val count = elements.size
    var p = position % count
    if (p < 0f) p += count
    val index = min(p.toInt(), count - 1)
    val blend = smooth((p - index - HueHold) / (1f - HueHold))
    return if (blend < 0.5f) {
        kkMix(elements[index], Kk.Bone, blend * 2f)
    } else {
        kkMix(elements[(index + 1) % count], Kk.Bone, (1f - blend) * 2f)
    }
}

private fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The scene's loop (s): the strike, its rings and the hue cycle all repeat on it. */
internal const val ResonanceForkLoopSeconds = 7.5f
private const val RingInterval = 0.375f
private const val RingEmissions = 20
private const val RingOffset = 0.05f
private const val RingLife = 4.2f
/** The share of a ring's life it takes to swell in at the tines. */
private const val RingSwell = 0.05f
private const val RingDecay = 1.9f
private const val HumFloor = 0.22f
private const val StrikeAttack = 0.18f
private val DecayTail = exp(-ResonanceForkLoopSeconds / RingDecay)
private const val WavePeriodA = ResonanceForkLoopSeconds / 3f
private const val WavePeriodB = ResonanceForkLoopSeconds / 4f
private const val HumpsA = 2
private const val HumpsB = 3
/** Cached amplitude levels of a wave (the last is flat), each [WaveStep] of the one before. */
private const val WaveLevels = 9
private const val WaveStep = 0.7f
private const val WaveAlpha = 0.4f
/** Cubic control reach of a sine hump (4/3 ÷ π of its width). */
private const val HumpControl = 0.4244f
private const val PartnerOffset = 2f
private const val HueHold = 0.7f
private const val GhostDegrees = 6f
private const val GhostLevels = 3
/** After-images: two outward (the swing's end and halfway) and one inward, fading with the swing. */
private val GhostSwing = floatArrayOf(1f, 0.5f, -0.55f)
private val GhostFade = floatArrayOf(1f, 0.6f, 0.5f)

private const val SlotGeometry = 0
private const val SlotFork = 0
private const val SlotTineL = 1
private const val SlotTineR = 2
private const val SlotShine = 3
private const val SlotClamps = 4
/** Wave A's amplitude levels take slots 5 until 14, wave B's 14 until 23. */
private const val SlotWaveA = 5
private const val SlotWaveB = SlotWaveA + WaveLevels
private const val BrushBloom = 0
private const val BrushBody = 1

private const val G_CX = 0
private const val G_Y_TOP = 1
private const val G_Y_INNER = 2
private const val G_Y_BASE = 3
private const val G_Y_STEM = 4
private const val G_Y_KNOB = 5
private const val G_TINE = 6
private const val G_GAP = 7
private const val G_STEM = 8
private const val G_KNOB_W = 9
private const val G_PIVOT_L = 10
private const val G_PIVOT_R = 11
private const val G_RING_Y = 12
private const val G_R0 = 13
private const val G_R_MAX = 14
private const val G_BLOOM = 15
private const val G_BAND_Y = 16
private const val G_BAND_LEFT = 17
private const val G_BAND_RIGHT = 18
private const val G_LINES = 19
private const val G_AMP = 20
private const val G_HAIR = 21
private const val G_GLOW = 22
private const val G_OUTLINE = 23
private const val G_CLAMP_H = 24
private const val G_ECHO_X = 25
private const val G_ECHO_Y = 26
private const val G_COUNT = 27
