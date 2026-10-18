// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.foundation.design.kkLoop
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Legendary scene (animated, looping on the card time, behind the text).

/**
 * Glacial Heart: hexagonal frost dendrites grow in from three corners; at the height of the growth
 * the frozen heart in the lower body takes a hit, a fracture web races out sideways and down across
 * the face (never up into the text) and a frost front closes it again behind it, then the grown
 * frost thaws back to its seed while fine snow drifts down. Cryo hue, with gold on the heart and
 * its shock ring.
 */
internal fun DrawScope.drawGlacialHeartArt(frame: RewardArtFrame, time: Float) {
    val p = kkLoop(time, GlacialHeartLoopSeconds) * GlacialHeartLoopSeconds
    val meta = frame.cache.floats(SLOT_META, META_SIZE) { buildMeta(frame, it) }
    val frost = frame.cache.floats(SLOT_FROST, 1 + FROST_MAX * SEG) { buildFrost(frame, meta, it) }
    val snow = frame.cache.floats(SLOT_SNOW, 1 + SNOW_MAX * FLAKE) { buildSnow(frame, it) }
    val glints = frame.cache.floats(SLOT_GLINTS, 1 + GLINT_MAX * GLINT) { buildGlints(frame, frost, it) }
    // Built right after the meta it completes (the crack reach), whatever the time.
    val cracks = frame.cache.path(PATH_CRACKS) { crackPath(frame, meta) }
    val growth = frostGrowth(p)
    val grown = frostGrown(p)
    val shock = bump(p, IMPACT + 0.3f, 0.55f)
    // The hit jolts the frozen field for a moment (a damped shudder of a pixel or two).
    val since = p - IMPACT
    val jolt = if (since >= 0f && since < JOLT_TIME) (1f - since / JOLT_TIME) * (1f - since / JOLT_TIME) * frame.u(1.8f) else 0f
    translate(jolt * sin(since * 70f), jolt * 0.6f * sin(since * 53f + 1f)) {
        drawCornerGlaze(frame, meta, grown, shock)
        drawFrost(frame, frost, p, growth, shock)
        drawGlints(frame, frost, glints, p, growth)
    }
    drawCracks(frame, meta, cracks, p)
    drawHeart(frame, meta, p)
    drawSnow(frame, snow, p)
}

private const val JOLT_TIME = 0.32f

/** One loop of the scene in seconds: growth, impact, cracks, healing, thaw. */
internal const val GlacialHeartLoopSeconds = 7f

// Timeline (seconds into the loop). The growth wraps: it starts before the loop seam and peaks at the impact.
private const val GROW_START = 6.35f
private const val GROW_TIME = 3.55f
private const val IMPACT = 2.9f
private const val RACE_TIME = 0.42f
private const val HEAL_START = 3.5f
private const val HEAL_TIME = 1.45f
private const val SCAR_END = 5.9f
private const val THAW_START = 4.55f
private const val THAW_END = 6.2f
private const val THAW_SEGMENT = 0.6f

/** Share of the growth that never thaws: the seed frost every card keeps in its corners. */
private const val SEED = 0.3f

/** Share of a main stem's length that is seed frost. */
private const val SEED_LENGTH = 0.44f

private const val SLOT_META = 0
private const val SLOT_FROST = 1
private const val SLOT_SNOW = 2
private const val SLOT_GLINTS = 3

private const val PATH_CRACKS = 0
private const val PATH_CIRCLE = 1
private const val PATH_GLAZE = 2 // three corners: 2, 3, 4
private const val PATH_FACETS = 5 // three corners: 5, 6, 7
private const val PATH_HEART = 8
private const val PATH_HEART_LIT = 9

private const val BRUSH_HAZE = 0 // three corners: 0, 1, 2
private const val BRUSH_BLOOM = 3
private const val BRUSH_CRACK_GLOW = 4
private const val BRUSH_CRACK_CORE = 5
private const val BRUSH_CRACK_HOT = 6
private const val BRUSH_AURA = 7

/** Floats per frost segment: x0, y0, x1, y1, growth start, growth end, width, kind. */
private const val SEG = 8
private const val FROST_MAX = 128
private const val KIND_MAIN = 0f
private const val KIND_STEM = 1f
private const val KIND_BRANCH = 2f
private const val KIND_TWIG = 3f

/** Floats per glint: frost segment index, twinkles per loop, phase. */
private const val GLINT = 3
private const val GLINT_MAX = 6

/** Floats per snowflake: x, phase, wraps per loop, sway, sway cycles, sway phase, radius, alpha, crystal, spin. */
private const val FLAKE = 10
private const val SNOW_MAX = 24

/** Meta: impact x, y, crack reach; per corner x, y, glaze radius; the crack ceiling (no crack rises past it). */
private const val META_CORNERS = 3
private const val META_CEILING = META_CORNERS + 3 * 3
private const val META_SIZE = META_CEILING + 1

// --- Timeline -------------------------------------------------------------------------------

/** Frost growth 0..1 at [p]: [SEED] at rest, rising to 1 by the impact, held there through the thaw. */
private fun frostGrowth(p: Float): Float {
    val t = wrap(p - GROW_START)
    return if (t < GROW_TIME) SEED + (1f - SEED) * smooth(t / GROW_TIME) else 1f
}

/** How much of the grown (non-seed) frost stands at [p]: 0 at rest, 1 at the impact, back to 0 after the thaw. */
private fun frostGrown(p: Float): Float {
    val t = wrap(p - GROW_START)
    if (t < GROW_TIME) return smooth(t / GROW_TIME)
    return when {
        p < THAW_START -> 1f
        p < THAW_END -> 1f - smooth((p - THAW_START) / (THAW_END - THAW_START))
        else -> 0f
    }
}

/** Opacity of a grown segment that finished growing at [end]: the tips thaw first, the roots last. */
private fun thaw(p: Float, end: Float): Float {
    if (end <= SEED + 1e-4f || p < THAW_START || p > GROW_START) return 1f
    val start = THAW_START + (1f - (end - SEED) / (1f - SEED)) * (THAW_END - THAW_START - THAW_SEGMENT)
    return 1f - smooth((p - start) / THAW_SEGMENT)
}

private fun wrap(value: Float): Float {
    val r = value % GlacialHeartLoopSeconds
    return if (r < 0f) r + GlacialHeartLoopSeconds else r
}

private fun smooth(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private fun easeOut(t: Float): Float {
    val c = 1f - t.coerceIn(0f, 1f)
    return 1f - c * c * c
}

/** A tent 0..1..0 of half-width [half] centred on [at]. */
private fun bump(p: Float, at: Float, half: Float): Float = max(0f, 1f - abs(p - at) / half)

// --- Drawing --------------------------------------------------------------------------------

/** The corner hazes and the small hexagonal ice plates that spread with the frost. */
private fun DrawScope.drawCornerGlaze(frame: RewardArtFrame, meta: FloatArray, grown: Float, shock: Float) {
    val edge = frame.cache.stroke(frame.u(1f), join = StrokeJoin.Miter)
    val reach = 0.72f + 0.28f * grown
    for (corner in 0 until 3) {
        val base = META_CORNERS + corner * 3
        val center = Offset(meta[base], meta[base + 1])
        val radius = meta[base + 2]
        val haze = frame.cache.brush(BRUSH_HAZE + corner) {
            Brush.radialGradient(listOf(frame.hue.copy(alpha = 0.6f), frame.hue.copy(alpha = 0.18f), Color.Transparent),
                center = center, radius = radius)
        }
        drawCircle(haze, radius, center, alpha = (0.26f + 0.2f * grown + 0.14f * shock).coerceAtMost(1f))
        val plates = frame.cache.path(PATH_GLAZE + corner) { hexPlates(frame, corner, center, radius, facets = false) }
        val facets = frame.cache.path(PATH_FACETS + corner) { hexPlates(frame, corner, center, radius, facets = true) }
        scale(reach, center) {
            drawPath(plates, frame.hue, alpha = 0.1f + 0.06f * shock)
            drawPath(facets, frame.hue, alpha = 0.16f + 0.12f * shock, style = edge)
            drawPath(plates, frame.hue, alpha = 0.3f + 0.2f * shock, style = edge)
        }
    }
}

/** Every frost stem, branch and twig revealed up to [growth], thawing tip first. */
private fun DrawScope.drawFrost(frame: RewardArtFrame, frost: FloatArray, p: Float, growth: Float, shock: Float) {
    val count = frost[0].toInt()
    val tip = mix(frame.hue, Color.White, 0.5f)
    val lift = 1f + 0.6f * shock
    for (index in 0 until count) {
        val b = 1 + index * SEG
        val s0 = frost[b + 4]
        val s1 = frost[b + 5]
        val shown = ((growth - s0) / (s1 - s0)).coerceIn(0f, 1f)
        if (shown <= 0f) continue
        val opacity = thaw(p, s1)
        if (opacity <= 0f) continue
        val kind = frost[b + 7]
        val alpha = when (kind) {
            KIND_MAIN -> 0.62f
            KIND_STEM -> 0.52f
            KIND_BRANCH -> 0.46f
            else -> 0.36f
        } * opacity * lift
        val start = Offset(frost[b], frost[b + 1])
        val end = Offset(frost[b] + (frost[b + 2] - frost[b]) * shown, frost[b + 1] + (frost[b + 3] - frost[b + 1]) * shown)
        // The growing end runs brighter while it grows; round caps keep the ends soft, never a point.
        drawLine(if (shown < 1f) tip else frame.hue, start, end, frost[b + 6], StrokeCap.Round, alpha = alpha.coerceAtMost(1f))
    }
}

/** Light catching the ice: a few branch tips twinkle (whole twinkles per loop, so the loop closes). */
private fun DrawScope.drawGlints(frame: RewardArtFrame, frost: FloatArray, glints: FloatArray, p: Float, growth: Float) {
    val count = glints[0].toInt()
    val loop = p / GlacialHeartLoopSeconds
    val halo = mix(frame.hue, Color.White, 0.3f)
    for (index in 0 until count) {
        val g = 1 + index * GLINT
        val b = 1 + glints[g].toInt() * SEG
        val end = frost[b + 5]
        if (growth < end) continue
        val visible = thaw(p, end)
        if (visible <= 0f) continue
        val wave = sin(2f * PI.toFloat() * (glints[g + 1] * loop + glints[g + 2]))
        if (wave <= 0f) continue
        val twinkle = wave * wave * wave * wave * visible
        val center = Offset(frost[b + 2], frost[b + 3])
        drawCircle(halo, frame.u(3.6f), center, alpha = 0.18f * twinkle)
        drawCircle(Color.White, max(frame.dp(0.8f), frame.u(1.3f)), center, alpha = 0.85f * twinkle)
    }
}

/** The impact's fracture web: racing out from the heart, closed again by the frost front behind it. */
private fun DrawScope.drawCracks(frame: RewardArtFrame, meta: FloatArray, cracks: Path, p: Float) {
    if (p < IMPACT || p > SCAR_END) return
    val center = Offset(meta[0], meta[1])
    val reach = meta[2]
    val circle = frame.cache.path(PATH_CIRCLE) { addOval(Rect(center, reach)) }
    // The cracks fade out as they climb toward the name, so the title never sits on a bright line.
    val fadeTop = frame.bounds.height * 0.3f
    val fadeBottom = frame.clear.top + frame.u(8f)
    val white = mix(frame.hue, Color.White, 0.6f)
    val glowBrush = frame.cache.brush(BRUSH_CRACK_GLOW) { fadeUp(frame.hue, fadeTop, fadeBottom) }
    val coreBrush = frame.cache.brush(BRUSH_CRACK_CORE) { fadeUp(white, fadeTop, fadeBottom) }
    val hotBrush = frame.cache.brush(BRUSH_CRACK_HOT) { fadeUp(Color.White, fadeTop, fadeBottom) }
    val race = reach * easeOut((p - IMPACT) / RACE_TIME)
    // The cracks never rise past the ceiling (the text's floor); the flash ring stays below it too.
    val healing = ((p - HEAL_START) / HEAL_TIME).coerceIn(0f, 1f)
    // The frost front leaves the heart quickly and slows as it seals the long outer cracks.
    val heal = (reach + frame.u(HEAL_BAND)) * (1f - (1f - healing) * (1f - healing))
    val glow = frame.cache.stroke(frame.u(3.6f), join = StrokeJoin.Miter)
    val core = frame.cache.stroke(max(frame.dp(0.8f), frame.u(1.15f)), join = StrokeJoin.Miter)
    val bright = frame.cache.stroke(frame.u(2.2f), join = StrokeJoin.Miter)
    // Healed seams: a faint frosted scar that fades after the front has passed.
    val scar = 0.3f * (1f - ((p - HEAL_START) / (SCAR_END - HEAL_START)).coerceIn(0f, 1f))
    if (heal > 0f && scar > 0f) {
        withCircle(circle, center, heal, reach, ClipOp.Intersect) { drawPath(cracks, glowBrush, alpha = scar, style = core) }
    }
    // Open cracks between the frost front and the racing front.
    withCircle(circle, center, race, reach, ClipOp.Intersect) {
        withCircle(circle, center, heal, reach, ClipOp.Difference) {
            drawPath(cracks, glowBrush, alpha = 0.26f, style = glow)
            drawPath(cracks, coreBrush, alpha = 0.82f, style = core)
        }
        // The racing tips run white-hot.
        val racing = 1f - ((p - IMPACT) / RACE_TIME).coerceIn(0f, 1f)
        if (racing > 0f) {
            withCircle(circle, center, race - frame.u(RACE_BAND), reach, ClipOp.Difference) {
                drawPath(cracks, hotBrush, alpha = 0.9f * (0.4f + 0.6f * racing), style = bright)
            }
        }
    }
    // The frost front glints along the cracks it closes.
    if (healing > 0f && healing < 1f) {
        withCircle(circle, center, heal, reach, ClipOp.Intersect) {
            withCircle(circle, center, heal - frame.u(HEAL_BAND), reach, ClipOp.Difference) {
                drawPath(cracks, coreBrush, alpha = 0.7f * sin(PI.toFloat() * healing), style = bright)
            }
        }
    }
}

private fun fadeUp(color: Color, top: Float, bottom: Float): Brush =
    Brush.verticalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 0.35f), color), startY = top, endY = bottom)

private const val RACE_BAND = 16f
private const val HEAL_BAND = 18f

/** The frozen heart at the impact point: an upright ice crystal that gathers light, flares on the hit and settles. */
private fun DrawScope.drawHeart(frame: RewardArtFrame, meta: FloatArray, p: Float) {
    val center = Offset(meta[0], meta[1])
    val since = p - IMPACT
    val flash = when {
        since < 0f -> 0f
        since < 0.06f -> since / 0.06f
        else -> {
            val fall = (1f - (since - 0.06f) / 0.9f).coerceIn(0f, 1f)
            fall * fall
        }
    }
    // Before the hit the heart gathers light as the frost closes in; it settles again after the flash.
    val glow = if (since < 0f) smooth((p - (IMPACT - 1.4f)) / 1.4f) else 1f - smooth(since / 1.6f)
    if (flash > 0f) {
        val bloom = frame.cache.brush(BRUSH_BLOOM) {
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), frame.hue.copy(alpha = 0.5f), frame.hue.copy(alpha = 0f)),
                center = center, radius = frame.u(BLOOM))
        }
        drawRect(frame.hue, frame.face.topLeft, frame.face.size, alpha = 0.07f * flash)
        // The bloom and the shock rings reach no higher than the crack ceiling (the text stays clear).
        val room = (center.y - meta[META_CEILING]).coerceAtLeast(frame.u(28f))
        val bloomScale = min(1f, room / (frame.u(BLOOM) * 1.2f))
        scale((0.55f + 0.65f * (1f - flash)) * bloomScale, center) { drawCircle(bloom, frame.u(BLOOM), center, alpha = flash) }
        val ring = easeOut(since / 0.9f)
        val ringReach = min(frame.u(96f), room - frame.u(14f)).coerceAtLeast(frame.u(12f))
        drawCircle(frame.hue, frame.u(14f) + ringReach * ring, center, alpha = 0.5f * (1f - ring),
            style = frame.cache.stroke(frame.u(1.4f)))
        drawHexRing(center, frame.u(12f) + min(frame.u(44f), ringReach) * ring, 0.26f * ring, frame.gold, 0.85f * (1f - ring), frame.u(1.4f))
    }
    val heart = frame.cache.path(PATH_HEART) { crystal(center, frame.u(HEART_HALF_WIDTH), frame.u(HEART_HALF_HEIGHT)) }
    val lit = frame.cache.path(PATH_HEART_LIT) { crystalLitFacet(center, frame.u(HEART_HALF_WIDTH), frame.u(HEART_HALF_HEIGHT)) }
    val outline = frame.cache.stroke(max(frame.dp(0.8f), frame.u(1f)), join = StrokeJoin.Miter)
    val light = max(0.4f * glow, flash)
    // A cold aura round the heart, so it reads as a glowing core rather than a loose gem.
    val aura = frame.cache.brush(BRUSH_AURA) {
        Brush.radialGradient(listOf(frame.hue.copy(alpha = 0.5f), frame.hue.copy(alpha = 0.12f), Color.Transparent),
            center = center, radius = frame.u(AURA))
    }
    drawCircle(aura, frame.u(AURA), center, alpha = 0.45f + 0.5f * light)
    drawPath(heart, frame.hue, alpha = 0.14f + 0.3f * light)
    drawPath(lit, Color.White, alpha = 0.12f + 0.45f * light)
    drawPath(heart, frame.gold, alpha = 0.45f + 0.5f * light, style = outline)
}

private const val HEART_HALF_WIDTH = 6.5f
private const val HEART_HALF_HEIGHT = 11f
private const val BLOOM = 64f
private const val AURA = 28f

/** A hexagon ring of [radius] turned by [turn] radians, six butt-ended lines (no per-frame path). */
private fun DrawScope.drawHexRing(center: Offset, radius: Float, turn: Float, color: Color, alpha: Float, width: Float) {
    if (alpha <= 0f) return
    for (side in 0 until 6) {
        val a0 = turn + side * (PI.toFloat() / 3f) - PI.toFloat() / 2f
        val a1 = a0 + PI.toFloat() / 3f
        drawLine(color, Offset(center.x + cos(a0) * radius, center.y + sin(a0) * radius),
            Offset(center.x + cos(a1) * radius, center.y + sin(a1) * radius), width, alpha = alpha)
    }
}

/** Fine snow: dots and a few six-armed crystals that turn a sixth of a turn per loop, so the loop closes. */
private fun DrawScope.drawSnow(frame: RewardArtFrame, snow: FloatArray, p: Float) {
    val count = snow[0].toInt()
    val loop = p / GlacialHeartLoopSeconds
    val top = frame.face.top - frame.u(12f)
    val range = frame.bounds.bottom + frame.u(12f) - top
    val tone = mix(frame.hue, frame.bone, 0.4f)
    val arm = max(frame.dp(0.8f), frame.u(0.9f))
    for (index in 0 until count) {
        val b = 1 + index * FLAKE
        val fall = snow[b + 1] + snow[b + 2] * loop
        val y = top + (fall - fall.toInt()) * range
        val x = snow[b] + snow[b + 3] * sin(2f * PI.toFloat() * (snow[b + 4] * loop + snow[b + 5]))
        val alpha = snow[b + 7]
        val radius = snow[b + 6]
        if (snow[b + 8] > 0f) {
            val turn = snow[b + 9] * (PI.toFloat() / 3f) * loop + snow[b + 5] * PI.toFloat()
            for (spoke in 0 until 3) {
                val a = turn + spoke * (PI.toFloat() / 3f)
                val dx = cos(a) * radius
                val dy = sin(a) * radius
                drawLine(tone, Offset(x - dx, y - dy), Offset(x + dx, y + dy), arm, alpha = alpha)
            }
        } else {
            drawCircle(tone, radius, Offset(x, y), alpha = alpha)
        }
    }
}

/** Runs [block] clipped to (or outside of) the circle of [radius] around [center], from a cached circle of [reach]. */
private inline fun DrawScope.withCircle(
    circle: Path,
    center: Offset,
    radius: Float,
    reach: Float,
    op: ClipOp,
    block: DrawScope.() -> Unit,
) {
    if (radius <= 0.5f) {
        if (op == ClipOp.Difference) block()
        return
    }
    val s = radius / reach
    scale(s, center) {
        clipPath(circle, op) {
            scale(1f / s, center) { block() }
        }
    }
}

// --- Geometry (built once per card size, look and palette) ----------------------------------

/** The impact point, the crack reach (set by the crack path) and the three frost corners. */
private fun buildMeta(frame: RewardArtFrame, out: FloatArray) {
    val b = frame.bounds
    val clear = frame.clear
    out[0] = clear.left + clear.width * (if (frame.compact) 0.58f else 0.55f)
    // Low in the clear body, so longer copy (Russian runs a line further) keeps its distance.
    out[1] = clear.top + clear.height * (if (frame.compact) 0.6f else 0.62f)
    out[2] = 1f
    // No crack rises past the text's floor, nor closer than a little above the heart.
    out[META_CEILING] = max(frame.face.top, min(frame.textFloor, out[1] - frame.u(36f)))
    val cut = frame.dp(9f)
    val corners = floatArrayOf(
        b.left + cut, b.bottom - cut, frame.u(120f),
        b.right - frame.dp(2f), b.bottom - frame.dp(2f), frame.u(120f),
        b.right - frame.dp(2f), frame.face.top + frame.dp(1f), frame.u(88f),
    )
    corners.copyInto(out, META_CORNERS)
}

/**
 * Stems: corner (0 bottom left, 1 bottom right, 2 top right), angle (degrees, y down), length (board
 * dp), growth start, growth end, branches, twigs per branch, main (1: its root is seed frost).
 */
private val BoardStems = floatArrayOf(
    0f, -44f, 128f, 0f, 0.84f, 6f, 2f, 1f,
    0f, -13f, 86f, 0.3f, 0.86f, 4f, 1f, 0f,
    0f, -76f, 96f, 0.34f, 0.92f, 4f, 1f, 0f,
    1f, -136f, 126f, 0f, 0.86f, 6f, 2f, 1f,
    1f, -166f, 82f, 0.32f, 0.88f, 4f, 1f, 0f,
    1f, -104f, 96f, 0.3f, 0.94f, 4f, 1f, 0f,
    2f, 132f, 92f, 0f, 0.8f, 6f, 1f, 1f,
    2f, 104f, 64f, 0.34f, 0.9f, 3f, 0f, 0f,
)

private val PhoneStems = floatArrayOf(
    0f, -45f, 120f, 0f, 0.84f, 4f, 1f, 1f,
    0f, -15f, 78f, 0.32f, 0.88f, 3f, 0f, 0f,
    1f, -135f, 118f, 0f, 0.86f, 4f, 1f, 1f,
    1f, -106f, 86f, 0.32f, 0.92f, 3f, 0f, 0f,
    2f, 118f, 56f, 0f, 0.82f, 4f, 0f, 1f,
)

private const val STEM = 8

private fun buildFrost(frame: RewardArtFrame, meta: FloatArray, out: FloatArray) {
    out[0] = 0f
    val stems = if (frame.compact) PhoneStems else BoardStems
    for (stem in 0 until stems.size / STEM) {
        val s = stem * STEM
        val corner = stems[s].toInt()
        val main = stems[s + 7] > 0f
        val angle = radians(stems[s + 1])
        val length = frame.u(stems[s + 2])
        val ox = meta[META_CORNERS + corner * 3]
        val oy = meta[META_CORNERS + corner * 3 + 1]
        out.addFern(frame, ox, oy, angle, length, stems[s + 3], stems[s + 4], stems[s + 5].toInt(), stems[s + 6].toInt(), main, stem * 97)
    }
}

/**
 * A frost dendrite (the hexagonal habit of a growing ice crystal): a straight stem with pairs of side
 * branches at ±60°, shortening toward the tip; on a main stem the longer inner branches carry a pair
 * of twigs at ±60° of their own. Everything grows at the stem's speed from the moment the stem's
 * front passes its root.
 */
private fun FloatArray.addFern(
    frame: RewardArtFrame,
    ox: Float,
    oy: Float,
    angle: Float,
    length: Float,
    t0: Float,
    t1: Float,
    branches: Int,
    twigs: Int,
    main: Boolean,
    salt: Int,
) {
    val width = max(frame.dp(0.9f), frame.u(if (main) 1.9f else 1.5f))
    val dx = cos(angle)
    val dy = sin(angle)
    // Growth (0..1) at which the stem's front reaches [f] of its length.
    fun front(f: Float): Float = if (main) {
        if (f <= SEED_LENGTH) SEED * f / SEED_LENGTH else SEED + (t1 - SEED) * (f - SEED_LENGTH) / (1f - SEED_LENGTH)
    } else {
        t0 + (t1 - t0) * f
    }
    // Growth per px once past the seed, so branches and twigs grow as fast as their stem.
    val perPx = if (main) (t1 - SEED) / ((1f - SEED_LENGTH) * length) else (t1 - t0) / length
    if (main) {
        val sx = ox + dx * length * SEED_LENGTH
        val sy = oy + dy * length * SEED_LENGTH
        addSegment(ox, oy, sx, sy, 0f, SEED, width, KIND_MAIN)
        addSegment(sx, sy, ox + dx * length, oy + dy * length, SEED, t1, width * 0.8f, KIND_MAIN)
    } else {
        addSegment(ox, oy, ox + dx * length, oy + dy * length, max(SEED, t0), t1, width, KIND_STEM)
    }
    val sixty = radians(60f)
    for (k in 0 until branches) {
        val f = 0.16f + 0.72f * k / max(1, branches - 1) + (frame.random(salt + 30 + k) - 0.5f) * 0.03f
        val px = ox + dx * length * f
        val py = oy + dy * length * f
        // Symmetric pairs, tapering steadily toward the tip.
        val branchLength = length * (if (main) 0.3f else 0.26f) * (1f - f).pow(0.85f) * (0.94f + 0.12f * frame.random(salt + 50 + k))
        val bs0 = front(f)
        val bs1 = min(1f, bs0 + branchLength * perPx)
        val withTwigs = main && twigs > 0 && k < (branches + 1) / 2
        for (side in 0..1) {
            val branchAngle = angle + (if (side == 0) sixty else -sixty)
            val bx = cos(branchAngle)
            val by = sin(branchAngle)
            addSegment(px, py, px + bx * branchLength, py + by * branchLength, bs0, bs1, width * 0.6f, KIND_BRANCH)
            if (!withTwigs) continue
            // A pair of twigs at the branch's middle, at ±60° of the branch.
            val tx = px + bx * branchLength * 0.5f
            val ty = py + by * branchLength * 0.5f
            val twig = branchLength * 0.34f
            val ts0 = bs0 + (bs1 - bs0) * 0.5f
            for (turn in 0..1) {
                val twigAngle = branchAngle + (if (turn == 0) sixty else -sixty)
                addSegment(tx, ty, tx + cos(twigAngle) * twig, ty + sin(twigAngle) * twig, ts0, min(1f, ts0 + twig * perPx), width * 0.42f, KIND_TWIG)
            }
        }
    }
}

private fun FloatArray.addSegment(x0: Float, y0: Float, x1: Float, y1: Float, s0: Float, s1: Float, width: Float, kind: Float) {
    val count = this[0].toInt()
    if (count >= FROST_MAX) return
    val b = 1 + count * SEG
    this[b] = x0
    this[b + 1] = y0
    this[b + 2] = x1
    this[b + 3] = y1
    // A segment that starts in the seed is seed frost through and through: it is whole at rest.
    this[b + 4] = s0
    this[b + 5] = if (s0 < SEED) min(max(s1, s0 + 0.01f), SEED) else max(s1, s0 + 0.01f)
    this[b + 6] = width
    this[b + 7] = kind
    this[0] = (count + 1).toFloat()
}

/** The angular range (degrees, y down) of [corner]'s quarter of the card. */
private fun cornerRange(corner: Int): Pair<Float, Float> = when (corner) {
    0 -> -90f to 0f
    1 -> -180f to -90f
    else -> 90f to 180f
}

/**
 * Three small hexagonal ice plates out from [corner] along its diagonal, shrinking outward: their
 * outlines ([facets] false, filled and stroked) or the three facet lines across each ([facets] true).
 */
private fun Path.hexPlates(frame: RewardArtFrame, corner: Int, center: Offset, radius: Float, facets: Boolean) {
    val (from, to) = cornerRange(corner)
    for (i in 0 until 3) {
        val heading = radians((from + to) * 0.5f + (frame.random(400 + corner * 20 + i) - 0.5f) * 40f)
        val distance = radius * (0.1f + 0.17f * i) * (0.9f + 0.2f * frame.random(420 + corner * 20 + i))
        val cx = center.x + cos(heading) * distance
        val cy = center.y + sin(heading) * distance
        val r = radius * (0.12f - 0.025f * i)
        // Turned like the dendrites (their branches run at 60° steps).
        val turn = radians(30f) + radians(frame.random(440 + corner * 20 + i) * 60f)
        if (facets) {
            for (spoke in 0 until 3) {
                val a = turn + spoke * radians(120f)
                moveTo(cx, cy)
                lineTo(cx + cos(a) * r, cy + sin(a) * r)
            }
        } else {
            for (vertex in 0..5) {
                val a = turn + vertex * radians(60f)
                if (vertex == 0) moveTo(cx + cos(a) * r, cy + sin(a) * r) else lineTo(cx + cos(a) * r, cy + sin(a) * r)
            }
            close()
        }
    }
}

/**
 * The fracture web from the impact point: straight-rooted radial cracks that kink as they run out
 * (a few fork), tied together near the centre by a broken ring of chords. Sets the reach.
 */
private fun Path.crackPath(frame: RewardArtFrame, meta: FloatArray) {
    val ix = meta[0]
    val iy = meta[1]
    val b = frame.bounds
    val count = if (frame.compact) 6 else 8
    // A fan running sideways and down (y down: 0° right, 90° down), never steeply up into the text.
    val angles = FloatArray(count) { radians(CRACK_FAN_FROM + CRACK_FAN * (it + 0.5f) / count + (frame.random(610 + it) - 0.5f) * 12f) }
    val ceiling = meta[META_CEILING]
    val room = Rect(b.left, ceiling, b.right, b.bottom)
    val inner = frame.u(if (frame.compact) 16f else 20f)
    val outer = frame.u(if (frame.compact) 34f else 44f)
    var reach = 0f
    for (k in 0 until count) {
        var dir = angles[k]
        // Straight out past the chords, so every chord meets its cracks.
        val first = min(outer * 1.08f, edgeDistance(ix, iy, dir, room))
        var x = ix + cos(dir) * first
        var y = iy + sin(dir) * first
        moveTo(ix, iy)
        lineTo(x, y)
        reach = max(reach, first)
        val limit = (0.7f + 0.3f * frame.random(620 + k)) * edgeDistance(ix, iy, dir, room)
        var travelled = first
        var step = 0
        while (travelled < limit && step < 12) {
            val length = frame.u(20f + 20f * frame.random(630 + k * 16 + step))
            val radial = atan2(y - iy, x - ix)
            val turn = radians((frame.random(700 + k * 16 + step) - 0.5f) * 40f)
            dir = clampAngle(dir + turn, radial, radians(26f))
            // A crack that would climb past the ceiling stops short of it.
            if (y + sin(dir) * length < ceiling) break
            x += cos(dir) * length
            y += sin(dir) * length
            travelled += length
            lineTo(x, y)
            reach = max(reach, hypot(x - ix, y - iy))
            // A short fork off some joints, never near the crack's end (a fork there would read as a tail).
            if ((step == 1 || step == 3) && (k + step) % 2 == 0 && limit - travelled > frame.u(56f) && y - frame.u(40f) > ceiling) {
                val side = if (frame.random(800 + k * 4 + step) < 0.5f) -1f else 1f
                val fd = dir + side * radians(30f + 12f * frame.random(810 + k * 4 + step))
                val forkLength = frame.u(16f + 16f * frame.random(820 + k * 4 + step))
                val fx = x + cos(fd) * forkLength
                val fy = y + sin(fd) * forkLength
                lineTo(fx, fy)
                val gd = fd - side * radians(20f)
                val gx = fx + cos(gd) * forkLength * 0.7f
                val gy = fy + sin(gd) * forkLength * 0.7f
                lineTo(gx, gy)
                reach = max(reach, hypot(gx - ix, gy - iy))
                moveTo(x, y)
            }
            step++
        }
    }
    // Straight concentric chords between neighbouring cracks, each at its own radius, a few missing:
    // the broken ring of an impact fracture.
    for (k in 0 until count - 1) {
        if (frame.random(860 + k) < 0.34f) continue
        val a0 = angles[k]
        val a1 = angles[(k + 1) % count]
        val r0 = inner + (outer - inner) * frame.random(880 + k)
        val r1 = r0 * (0.8f + 0.4f * frame.random(900 + k))
        moveTo(ix + cos(a0) * r0, iy + sin(a0) * r0)
        lineTo(ix + cos(a1) * r1, iy + sin(a1) * r1)
    }
    meta[2] = reach + frame.u(4f)
}

/** The crack fan (degrees, y down): from a little above the right horizontal round through down to a little above the left. */
private const val CRACK_FAN_FROM = -24f
private const val CRACK_FAN = 228f

/** Distance from (x, y) along [angle] to the edge of [bounds]. */
private fun edgeDistance(x: Float, y: Float, angle: Float, bounds: Rect): Float {
    val dx = cos(angle)
    val dy = sin(angle)
    val tx = if (dx > 1e-4f) (bounds.right - x) / dx else if (dx < -1e-4f) (bounds.left - x) / dx else Float.MAX_VALUE
    val ty = if (dy > 1e-4f) (bounds.bottom - y) / dy else if (dy < -1e-4f) (bounds.top - y) / dy else Float.MAX_VALUE
    return min(tx, ty)
}

/** [angle] kept within [limit] of [reference] (radians), so a crack keeps running outward. */
private fun clampAngle(angle: Float, reference: Float, limit: Float): Float {
    var delta = angle - reference
    while (delta > PI) delta -= 2f * PI.toFloat()
    while (delta < -PI) delta += 2f * PI.toFloat()
    return reference + delta.coerceIn(-limit, limit)
}

/** An ice crystal standing upright: a tall hexagon, its shoulders a third of the way down. */
private fun Path.crystal(center: Offset, halfWidth: Float, halfHeight: Float) {
    moveTo(center.x, center.y - halfHeight)
    lineTo(center.x + halfWidth, center.y - halfHeight * 0.36f)
    lineTo(center.x + halfWidth, center.y + halfHeight * 0.36f)
    lineTo(center.x, center.y + halfHeight)
    lineTo(center.x - halfWidth, center.y + halfHeight * 0.36f)
    lineTo(center.x - halfWidth, center.y - halfHeight * 0.36f)
    close()
}

/** The crystal's lit left face: from the top point down the centre ridge to the bottom point. */
private fun Path.crystalLitFacet(center: Offset, halfWidth: Float, halfHeight: Float) {
    moveTo(center.x, center.y - halfHeight)
    lineTo(center.x, center.y + halfHeight)
    lineTo(center.x - halfWidth, center.y + halfHeight * 0.36f)
    lineTo(center.x - halfWidth, center.y - halfHeight * 0.36f)
    close()
}

/** Picks a few spread-out branch tips of [frost] to twinkle. */
private fun buildGlints(frame: RewardArtFrame, frost: FloatArray, out: FloatArray) {
    out[0] = 0f
    val wanted = if (frame.compact) 3 else GLINT_MAX
    var branches = 0
    for (index in 0 until frost[0].toInt()) if (frost[1 + index * SEG + 7] == KIND_BRANCH) branches++
    if (branches == 0) return
    val stride = max(1, branches / wanted)
    var seen = 0
    var count = 0
    for (index in 0 until frost[0].toInt()) {
        if (frost[1 + index * SEG + 7] != KIND_BRANCH) continue
        if (seen % stride == stride / 2 && count < wanted) {
            val g = 1 + count * GLINT
            out[g] = index.toFloat()
            out[g + 1] = if (frame.random(1400 + count) < 0.5f) 1f else 2f
            out[g + 2] = frame.random(1440 + count)
            count++
        }
        seen++
    }
    out[0] = count.toFloat()
}

private fun buildSnow(frame: RewardArtFrame, out: FloatArray) {
    val count = if (frame.compact) 11 else 16
    val crystals = 3
    out[0] = count.toFloat()
    val width = frame.bounds.width
    for (i in 0 until count) {
        val b = 1 + i * FLAKE
        val crystal = i < crystals
        // Stratified across the width so the snow never clumps (crystals interleaved with the dots).
        val column = (i * 7) % count
        out[b] = width * ((column + 0.2f + 0.6f * frame.random(1000 + i)) / count)
        out[b + 1] = frame.random(1040 + i)
        out[b + 2] = if (crystal || frame.random(1080 + i) < 0.6f) 1f else 2f
        out[b + 3] = frame.u(3f + 6f * frame.random(1120 + i))
        out[b + 4] = 1f + (frame.random(1160 + i) * 2.99f).toInt()
        out[b + 5] = frame.random(1200 + i)
        out[b + 6] = if (crystal) frame.u(3f + 1.4f * frame.random(1240 + i)) else max(frame.dp(0.7f), frame.u(0.9f + 0.9f * frame.random(1240 + i)))
        out[b + 7] = if (crystal) 0.4f + 0.16f * frame.random(1280 + i) else 0.26f + 0.32f * frame.random(1280 + i)
        out[b + 8] = if (crystal) 1f else 0f
        out[b + 9] = if (frame.random(1320 + i) < 0.5f) -1f else 1f
    }
}

private fun radians(degrees: Float): Float = degrees * (PI.toFloat() / 180f)

/** A plain sRGB channel mix of [a] toward [b] (no colour-space conversion, so nothing allocates per frame). */
private fun mix(a: Color, b: Color, t: Float): Color =
    Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t)
