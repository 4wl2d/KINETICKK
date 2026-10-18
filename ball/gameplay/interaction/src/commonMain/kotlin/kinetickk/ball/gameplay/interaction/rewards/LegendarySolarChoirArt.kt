// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.foundation.design.kkLoop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// Legendary scene (animated, looping on the card time, behind the text).

/** Seconds of one Solar Choir loop: each of the six embers ignites once, one second after the last. */
internal const val SolarChoirLoopSeconds = 6f

/**
 * Solar Choir: a slowly turning corona behind the icon plate, and a ring of six embers on a tilted
 * plane in the lower face, leaning with the card. The embers ignite one after another; each fires
 * an arc of fire to the next, which answers, so the light walks round the ring once per loop.
 * Embers drift up from the ring. Gold plus the Thermal hue; every motion is periodic in
 * [SolarChoirLoopSeconds], so the loop has no seam.
 */
internal fun DrawScope.drawSolarChoirArt(frame: RewardArtFrame, time: Float) {
    val geometry = frame.cache.floats(GeometrySlot, GeometrySize) { solarGeometry(frame, it) }
    val nodes = frame.cache.floats(NodesSlot, Nodes * NodeStride) { choirNodes(geometry, it) }
    drawCorona(frame, geometry, time)
    drawChoir(frame, geometry, nodes, time)
    drawRisingEmbers(frame, nodes, time)
}

private const val Nodes = 6
private const val NodeStride = 3
private const val StepSeconds = SolarChoirLoopSeconds / Nodes
private const val RaysPerGroup = 8
private const val Ticks = 48
private const val Segments = 12
private const val RingPoints = 72
private const val EmberStride = 7
private const val TAU = (PI * 2.0).toFloat()

/** The lean of the card face slab (its 22 dp cut over the card height, see `clipKkCardFace`). */
private const val CardCutDp = 22f

// Cache slots (one card draws one art, so the slots are this scene's own).
private const val GeometrySlot = 0
private const val NodesSlot = 1
private const val ArcsSlot = 2
private const val EmbersSlot = 3
private const val ControlsSlot = 4

private const val RaysLongPath = 0
private const val RaysShortPath = 1
private const val TicksPath = 2
private const val RingBackPath = 3
private const val DiamondPath = 4
private const val RingFrontPath = 5
private const val LoopPathBase = 6 // .. 11, one per arc
private const val FilamentsPath = 12
private const val SegmentPathBase = 13 // .. 18, the ring between ember k and k + 1

private const val CoronaBrush = 0
private const val RaysBrush = 1
private const val GlowBrush = 2
private const val PillarBrush = 3
private const val HearthBrush = 4

// Geometry floats.
private const val CX = 0
private const val CY = 1
private const val HALF = 2
private const val EX = 3
private const val EY = 4
private const val RX = 5
private const val RY = 6
private const val GLOW = 7
private const val BULGE = 8
private const val LEAN = 9
private const val PILLAR = 10
private const val GeometrySize = 11

/** An ember's light between ignitions: never fully out, so the ring always reads. */
private const val Smoulder = 0.26f

private fun solarGeometry(frame: RewardArtFrame, g: FloatArray) {
    val icon = frame.icon
    val clear = frame.clear
    val bounds = frame.bounds
    val lean = frame.dp(CardCutDp) / bounds.height
    g[CX] = icon.center.x
    g[CY] = icon.center.y
    g[HALF] = icon.width * 0.5f
    if (frame.crowded) {
        // The text leaves no room below it: the choir rings the sun itself, round the icon plate,
        // kept inside the leaning left edge.
        val ey = icon.center.y + icon.height * 0.04f
        val rx = icon.width * (if (frame.compact) 0.86f else 0.74f)
        val edge = bounds.left + lean * (bounds.bottom - ey) + frame.u(10f)
        g[EX] = max(icon.center.x, edge + rx)
        g[EY] = ey
        g[RX] = rx
        g[RY] = icon.height * 0.56f
    } else {
        val ey = clear.top + clear.height * 0.55f
        // Centred between the leaning face edges at the ring's height, clear of both.
        val left = bounds.left + lean * (bounds.bottom - ey)
        val right = bounds.right - lean * (ey - bounds.top)
        g[EX] = (left + right) * 0.5f
        g[EY] = ey
        g[RX] = (right - left) * 0.5f - frame.u(if (frame.compact) 16f else 20f)
        g[RY] = clear.height * 0.36f
    }
    g[GLOW] = frame.u(if (frame.compact) 15f else 20f)
    // Round the icon the arcs and the ignition pillars stay low, under the band.
    g[BULGE] = frame.u(if (frame.crowded) (if (frame.compact) 10f else 16f) else if (frame.compact) 20f else 34f)
    g[LEAN] = lean
    g[PILLAR] = frame.u(if (frame.crowded) (if (frame.compact) 14f else 22f) else if (frame.compact) 22f else 38f)
}

/** The ring point at [angle] (radians, 0 = right, clockwise on screen), sheared with the card's lean. */
private fun ringX(g: FloatArray, angle: Float): Float = g[EX] + cos(angle) * g[RX] - sin(angle) * g[RY] * g[LEAN]

private fun ringY(g: FloatArray, angle: Float): Float = g[EY] + sin(angle) * g[RY]

/** Ember k at 180° + k × 60° (left, then clockwise on screen), with its depth scale on the tilted ring. */
private fun choirNodes(g: FloatArray, n: FloatArray) {
    for (k in 0 until Nodes) {
        val angle = nodeAngle(k)
        n[k * NodeStride] = ringX(g, angle)
        n[k * NodeStride + 1] = ringY(g, angle)
        n[k * NodeStride + 2] = 0.74f + 0.36f * (sin(angle) + 1f) * 0.5f
    }
}

private fun nodeAngle(k: Int): Float = PI.toFloat() + k * (PI.toFloat() / 3f)

// Corona.

private fun DrawScope.drawCorona(frame: RewardArtFrame, g: FloatArray, time: Float) {
    val half = g[HALF]
    val glowRadius = half * 2.8f
    val rayOuter = half * 2.75f
    val glow = frame.cache.brush(CoronaBrush) {
        Brush.radialGradient(
            0f to frame.gold.copy(alpha = 0.6f),
            0.36f to frame.hue.copy(alpha = 0.26f),
            1f to Color.Transparent,
            center = Offset.Zero,
            radius = glowRadius,
        )
    }
    val rayBrush = frame.cache.brush(RaysBrush) {
        Brush.radialGradient(
            0.36f to frame.gold,
            0.6f to frame.hue.copy(alpha = 0.7f),
            1f to Color.Transparent,
            center = Offset.Zero,
            radius = rayOuter,
        )
    }
    val longRays = frame.cache.path(RaysLongPath) { addRays(0f, half * 1.06f, rayOuter, frame.u(3f), frame.u(10f)) }
    val shortRays = frame.cache.path(RaysShortPath) {
        addRays(180f / RaysPerGroup, half * 1.06f, half * 1.95f, frame.u(2.5f), frame.u(7f))
    }
    val ticks = frame.cache.path(TicksPath) { addTicks(half * 1.5f, half * 1.6f, half * 1.68f) }
    val filaments = frame.cache.path(FilamentsPath) { addFilaments(half * 1.1f, rayOuter * 0.92f) }
    val loop = kkLoop(time, SolarChoirLoopSeconds)
    // The choir's pulse: the corona swells a little at every ignition.
    val pulse = ignitionPulse(time)
    // The two ray groups breathe against each other twice per loop.
    val breath = sin(TAU * kkLoop(time, SolarChoirLoopSeconds / 2f))
    translate(g[CX], g[CY]) {
        drawCircle(glow, glowRadius, Offset.Zero, alpha = 0.5f + 0.16f * pulse)
        // One ray pitch per loop (45°, a whole turn in 48 s): the ray pattern repeats at the seam.
        rotate(360f / RaysPerGroup * loop, Offset.Zero) {
            scale(1f + 0.07f * breath, Offset.Zero) {
                drawPath(longRays, rayBrush, alpha = 0.32f + 0.08f * pulse)
                // A hot filament down the middle of each long ray.
                drawPath(filaments, rayBrush, alpha = 0.55f + 0.2f * pulse, style = frame.cache.stroke(frame.u(1.1f)))
            }
            scale(1f - 0.07f * breath, Offset.Zero) { drawPath(shortRays, rayBrush, alpha = 0.26f + 0.06f * pulse) }
        }
        drawCircle(frame.gold, half * 1.4f, Offset.Zero, alpha = 0.34f, style = frame.cache.stroke(frame.u(1.1f)))
        // The tick ring turns the other way, two ticks per loop.
        rotate(-2f * 360f / Ticks * loop, Offset.Zero) {
            drawPath(ticks, frame.gold, alpha = 0.26f, style = frame.cache.stroke(frame.u(1.1f)))
        }
    }
}

/** [RaysPerGroup] flat-cut rays from [inner] to [outer], [innerWidth] widening to [outerWidth], starting at [startDeg]. */
private fun Path.addRays(startDeg: Float, inner: Float, outer: Float, innerWidth: Float, outerWidth: Float) {
    for (index in 0 until RaysPerGroup) {
        val a = (startDeg + index * 360f / RaysPerGroup) * PI.toFloat() / 180f
        val dx = cos(a)
        val dy = sin(a)
        val nx = -dy
        val ny = dx
        val wi = innerWidth * 0.5f
        val wo = outerWidth * 0.5f
        moveTo(dx * inner + nx * wi, dy * inner + ny * wi)
        lineTo(dx * outer + nx * wo, dy * outer + ny * wo)
        lineTo(dx * outer - nx * wo, dy * outer - ny * wo)
        lineTo(dx * inner - nx * wi, dy * inner - ny * wi)
        close()
    }
}

/** A radial line from [inner] to [outer] along each long ray. */
private fun Path.addFilaments(inner: Float, outer: Float) {
    for (index in 0 until RaysPerGroup) {
        val a = index * TAU / RaysPerGroup
        moveTo(cos(a) * inner, sin(a) * inner)
        lineTo(cos(a) * outer, sin(a) * outer)
    }
}

/** [Ticks] radial ticks from [inner]: every fourth reaches [outerLong], the rest [outer]. */
private fun Path.addTicks(inner: Float, outer: Float, outerLong: Float) {
    for (index in 0 until Ticks) {
        val a = index * TAU / Ticks
        val r = if (index % 4 == 0) outerLong else outer
        moveTo(cos(a) * inner, sin(a) * inner)
        lineTo(cos(a) * r, sin(a) * r)
    }
}

/** 0..1 swell after each ignition (one per [StepSeconds]), zero again before the next. */
private fun ignitionPulse(time: Float): Float {
    val since = kkLoop(time, StepSeconds) * StepSeconds
    val attack = (since / 0.08f).coerceAtMost(1f)
    val left = 1f - since / StepSeconds
    return attack * left * left * left
}

// The choir ring.

private fun DrawScope.drawChoir(frame: RewardArtFrame, g: FloatArray, n: FloatArray, time: Float) {
    val controls = frame.cache.floats(ControlsSlot, Nodes * 2) { arcControls(g, n, it) }
    val arcs = frame.cache.floats(ArcsSlot, Nodes * (Segments + 1) * 2) { arcPoints(n, controls, it) }
    val back = frame.cache.path(RingBackPath) { addHalfRing(g, PI.toFloat()) }
    val front = frame.cache.path(RingFrontPath) { addHalfRing(g, 0f) }
    val hearthRadius = g[RX]
    val hearth = frame.cache.brush(HearthBrush) {
        Brush.radialGradient(0f to frame.hue.copy(alpha = 0.5f), 0.45f to frame.hue.copy(alpha = 0.18f), 1f to Color.Transparent,
            center = Offset.Zero, radius = hearthRadius)
    }
    val glowRadius = g[GLOW]
    val glow = frame.cache.brush(GlowBrush) {
        Brush.radialGradient(
            0f to frame.gold,
            0.22f to frame.hue,
            0.55f to frame.hue.copy(alpha = 0.3f),
            1f to Color.Transparent,
            center = Offset.Zero,
            radius = glowRadius,
        )
    }
    val pillarHeight = g[PILLAR]
    val pillarRadius = pillarHeight * 0.5f
    val pillar = frame.cache.brush(PillarBrush) {
        Brush.radialGradient(0f to frame.hue, 0.4f to frame.hue.copy(alpha = 0.4f), 1f to Color.Transparent, center = Offset.Zero, radius = pillarRadius)
    }
    val diamond = frame.cache.path(DiamondPath) { addDiamond() }
    val hairline = frame.cache.stroke(frame.u(1.1f))
    val bridgeStroke = frame.cache.stroke(frame.u(1.6f), StrokeCap.Round)
    val trailWide = frame.u(6f)
    val trailCore = frame.u(2f)

    // The hearth: warm haze on the ring's plane, swelling at every ignition.
    translate(g[EX], g[EY]) {
        scale(1f, g[RY] / g[RX], Offset.Zero) {
            drawCircle(hearth, hearthRadius, Offset.Zero, alpha = 0.26f + 0.1f * ignitionPulse(time))
        }
    }
    // The ring at rest, so a still frame shows the whole choir: its far half fine, its near half
    // brighter on a soft hue band (the plane tilts toward the viewer).
    drawPath(back, frame.gold, alpha = 0.16f, style = hairline)
    drawPath(front, frame.hue, alpha = 0.09f, style = frame.cache.stroke(frame.u(6f)))
    drawPath(front, frame.gold, alpha = 0.3f, style = frame.cache.stroke(frame.u(1.4f)))

    // The fire spreads along the ring: the stretch between a lit pair burns while they are bridged.
    for (k in 0 until Nodes) {
        val burn = bridgeLight(sinceIgnition(time, k))
        if (burn > 0f) {
            val segment = frame.cache.path(SegmentPathBase + k) { addRingSegment(g, k) }
            drawPath(segment, frame.hue, alpha = 0.26f * burn, style = frame.cache.stroke(frame.u(5f), StrokeCap.Round))
            drawPath(segment, frame.gold, alpha = 0.5f * burn, style = bridgeStroke)
        }
    }

    // Glows and rising light under everything else on the ring.
    for (k in 0 until Nodes) {
        val light = emberLight(sinceIgnition(time, k), time, k)
        val base = k * NodeStride
        val depth = n[base + 2]
        translate(n[base], n[base + 1]) {
            scale(depth * (0.7f + 0.5f * light), Offset.Zero) {
                drawCircle(glow, glowRadius, Offset.Zero, alpha = (0.2f + 0.45f * light).coerceAtMost(1f))
            }
            // The voice: a soft column of light above a lit ember.
            val lift = ((light - Smoulder) / (1f - Smoulder)).coerceIn(0f, 1.2f)
            if (lift > 0.01f) {
                // A radial glow squeezed into a tall soft column standing on the ember.
                val tall = depth * (0.6f + 0.4f * lift)
                translate(0f, -pillarRadius * tall) {
                    scale(depth * 0.2f, tall, Offset.Zero) { drawCircle(pillar, pillarRadius, Offset.Zero, alpha = 0.4f * lift.coerceAtMost(1f)) }
                }
                // A level glint along the ring's plane, longest in the ignition flash.
                val reach = frame.u(10f) * depth * lift * (1f + 0.6f * flash(sinceIgnition(time, k)))
                drawLine(frame.goldLight, Offset(-reach, 0f), Offset(reach, 0f), frame.u(1f), StrokeCap.Round, alpha = 0.4f * lift.coerceAtMost(1f))
            }
        }
    }

    // Each lit pair stays bridged a while after its arc lands, then cools.
    for (k in 0 until Nodes) {
        val bridge = bridgeLight(sinceIgnition(time, k))
        if (bridge > 0f) {
            val loop = frame.cache.path(LoopPathBase + k) { addLoop(n, controls, k) }
            drawPath(loop, frame.hue, alpha = 0.12f * bridge, style = frame.cache.stroke(frame.u(4f), StrokeCap.Round))
            drawPath(loop, frame.hue, alpha = 0.5f * bridge, style = bridgeStroke)
        }
    }

    // The arcs in flight: a comet from ember k leaping to ember k + 1, landing as it ignites.
    for (k in 0 until Nodes) {
        val since = sinceIgnition(time, k)
        if (since < ArcDepart || since > ArcDepart + ArcTravel + ArcLag) continue
        val head = smooth((since - ArcDepart) / ArcTravel)
        val tail = smooth((since - ArcDepart - ArcLag) / ArcTravel)
        if (head - tail < 0.002f) continue
        val base = k * (Segments + 1) * 2
        for (segment in 0 until Segments) {
            val from = segment.toFloat() / Segments
            val to = (segment + 1).toFloat() / Segments
            val a = maxOf(from, tail)
            val b = minOf(to, head)
            if (b <= a) continue
            val rel = ((a + b) * 0.5f - tail) / (head - tail)
            val start = arcAt(arcs, base, a)
            val end = arcAt(arcs, base, b)
            drawLine(frame.hue, start, end, trailWide, StrokeCap.Round, alpha = 0.22f * rel)
            drawLine(frame.goldLight, start, end, trailCore, StrokeCap.Round, alpha = 0.85f * rel * rel)
        }
        // The comet's head melts into the ember it lights instead of vanishing on landing.
        val tipLight = 1f - smooth((since - (ArcDepart + ArcTravel - 0.12f)) / 0.12f)
        if (tipLight > 0f) {
            val tip = arcAt(arcs, base, head)
            translate(tip.x, tip.y) {
                scale(0.55f, Offset.Zero) { drawCircle(glow, glowRadius, Offset.Zero, alpha = 0.9f * tipLight) }
            }
        }
    }

    // Ignition ripples (the ember's note), flat on the ring's tilted plane.
    for (k in 0 until Nodes) {
        val since = sinceIgnition(time, k)
        if (since >= RippleSeconds) continue
        val p = since / RippleSeconds
        val out = 1f - (1f - p) * (1f - p)
        val base = k * NodeStride
        val r = (frame.u(4f) + frame.u(24f) * out) * n[base + 2]
        val fade = (1f - p) * (1f - p)
        drawOval(frame.gold, Offset(n[base] - r, n[base + 1] - r * 0.42f), Size(r * 2f, r * 0.84f), alpha = 0.6f * fade, style = hairline)
    }

    // Ember cores: a hot diamond in a hue diamond.
    for (k in 0 until Nodes) {
        val light = emberLight(sinceIgnition(time, k), time, k)
        val base = k * NodeStride
        val size = frame.u(3.6f) * n[base + 2]
        translate(n[base], n[base + 1]) {
            scale(size * (0.85f + 0.3f * light), Offset.Zero) {
                drawPath(diamond, frame.hue, alpha = (0.45f + 0.5f * light).coerceAtMost(1f))
            }
            scale(size * (0.35f + 0.3f * light), Offset.Zero) {
                drawPath(diamond, frame.goldLight, alpha = (0.25f + 0.7f * light).coerceAtMost(1f))
            }
        }
    }
}

private const val ArcDepart = 0.12f
private const val ArcTravel = StepSeconds - ArcDepart
private const val ArcLag = 0.34f
private const val RippleSeconds = 1.2f

/** Seconds since ember [k] last ignited (it ignites at k × [StepSeconds] in every loop). */
private fun sinceIgnition(time: Float, k: Int): Float = kkLoop(time, SolarChoirLoopSeconds, k * StepSeconds) * SolarChoirLoopSeconds

/** An ember's light [since] its ignition: a flash, a sung hold, a slow cool to [Smoulder]; flickering while lit. */
private fun emberLight(since: Float, time: Float, k: Int): Float {
    val light = when {
        since < 0.1f -> Smoulder + (1.3f - Smoulder) * smooth(since / 0.1f)
        since < 0.6f -> 1.3f - 0.3f * smooth((since - 0.1f) / 0.5f)
        since < 3.0f -> 1f
        since < 5.2f -> 1f - (1f - Smoulder) * smooth((since - 3.0f) / 2.2f)
        else -> Smoulder
    }
    // Whole cycles per loop, so the flicker meets itself at the seam.
    val fast = kkLoop(time, SolarChoirLoopSeconds / 17f)
    val faster = kkLoop(time, SolarChoirLoopSeconds / 29f)
    val flicker = 1f + 0.07f * sin(TAU * (fast + k * 0.37f)) + 0.04f * sin(TAU * (faster + k * 0.61f))
    return light * flicker
}

/** 0..1 of the ignition flash [since] ignition (the light's overshoot above its sung hold). */
private fun flash(since: Float): Float = when {
    since < 0.1f -> smooth(since / 0.1f)
    since < 0.6f -> 1f - smooth((since - 0.1f) / 0.5f)
    else -> 0f
}

/** How brightly the loop from ember k to k + 1 glows [since] ember k ignited: lit on landing, cooled by 4 s. */
private fun bridgeLight(since: Float): Float {
    val landed = ArcDepart + ArcTravel
    return when {
        since < landed - 0.2f -> 0f
        since < landed -> smooth((since - landed + 0.2f) / 0.2f)
        since < 4f -> 1f - smooth((since - landed) / (4f - landed))
        else -> 0f
    }
}

/**
 * The quadratic control of the leap from ember k to k + 1: an arch standing up off the ring's
 * plane (straight up on screen), lower toward the far side, so the arcs read as fire leaping
 * between the embers rather than as another ring.
 */
private fun arcControls(g: FloatArray, n: FloatArray, c: FloatArray) {
    for (k in 0 until Nodes) {
        val next = (k + 1) % Nodes
        val depth = (n[k * NodeStride + 2] + n[next * NodeStride + 2]) * 0.5f
        val height = g[BULGE] * depth * depth
        // The curve's midpoint sits [height] above the chord's; the control lies twice as far.
        c[k * 2] = (n[k * NodeStride] + n[next * NodeStride]) * 0.5f
        c[k * 2 + 1] = (n[k * NodeStride + 1] + n[next * NodeStride + 1]) * 0.5f - 2f * height
    }
}

private fun arcPoints(n: FloatArray, c: FloatArray, out: FloatArray) {
    for (k in 0 until Nodes) {
        val next = (k + 1) % Nodes
        val x0 = n[k * NodeStride]
        val y0 = n[k * NodeStride + 1]
        val x2 = n[next * NodeStride]
        val y2 = n[next * NodeStride + 1]
        for (i in 0..Segments) {
            val t = i.toFloat() / Segments
            val u = 1f - t
            val base = (k * (Segments + 1) + i) * 2
            out[base] = u * u * x0 + 2f * u * t * c[k * 2] + t * t * x2
            out[base + 1] = u * u * y0 + 2f * u * t * c[k * 2 + 1] + t * t * y2
        }
    }
}

/** The ring from ember [k] to ember k + 1. */
private fun Path.addRingSegment(g: FloatArray, k: Int) {
    val from = nodeAngle(k)
    val steps = RingPoints / Nodes
    for (i in 0..steps) {
        val a = from + i * (PI.toFloat() / 3f) / steps
        if (i == 0) moveTo(ringX(g, a), ringY(g, a)) else lineTo(ringX(g, a), ringY(g, a))
    }
}

/** Half the ring from [from] (0 = the near half below the centre, π = the far half above it). */
private fun Path.addHalfRing(g: FloatArray, from: Float) {
    for (i in 0..RingPoints / 2) {
        val a = from + i * TAU / RingPoints
        if (i == 0) moveTo(ringX(g, a), ringY(g, a)) else lineTo(ringX(g, a), ringY(g, a))
    }
}

private fun Path.addLoop(n: FloatArray, c: FloatArray, k: Int) {
    val next = (k + 1) % Nodes
    moveTo(n[k * NodeStride], n[k * NodeStride + 1])
    quadraticTo(c[k * 2], c[k * 2 + 1], n[next * NodeStride], n[next * NodeStride + 1])
}

/** The point of the arc starting at [base] at [t] 0..1 along it (linear between the precomputed samples). */
private fun arcAt(arcs: FloatArray, base: Int, t: Float): Offset {
    val f = (t * Segments).coerceIn(0f, Segments.toFloat())
    val i = f.toInt().coerceAtMost(Segments - 1)
    val r = f - i
    val p = base + i * 2
    return Offset(arcs[p] + (arcs[p + 2] - arcs[p]) * r, arcs[p + 1] + (arcs[p + 3] - arcs[p + 1]) * r)
}

/** A unit diamond, a little taller than wide (scaled per use). */
private fun Path.addDiamond() {
    moveTo(0f, -1.3f)
    lineTo(1f, 0f)
    lineTo(0f, 1.3f)
    lineTo(-1f, 0f)
    close()
}

// Rising embers.

private fun DrawScope.drawRisingEmbers(frame: RewardArtFrame, n: FloatArray, time: Float) {
    val count = if (frame.compact) 10 else 18
    val embers = frame.cache.floats(EmbersSlot, count * EmberStride) { fillEmbers(frame, n, count, it) }
    val diamond = frame.cache.path(DiamondPath) { addDiamond() }
    for (j in 0 until count) {
        val b = j * EmberStride
        val period = SolarChoirLoopSeconds / embers[b + 6]
        val p = kkLoop(time + embers[b + 5] * period, period)
        val x = embers[b] + embers[b + 3] * sin(TAU * (p + embers[b + 5]))
        val y = embers[b + 1] - embers[b + 2] * p * (1.25f - 0.25f * p)
        val fade = sin(PI.toFloat() * p)
        val size = embers[b + 4] * (1f - 0.45f * p)
        val color = when (j % 3) {
            0 -> frame.goldLight
            1 -> frame.gold
            else -> frame.hue
        }
        translate(x, y) {
            scale(size, Offset.Zero) { drawPath(diamond, color, alpha = 0.75f * fade) }
        }
    }
}

private fun fillEmbers(frame: RewardArtFrame, n: FloatArray, count: Int, e: FloatArray) {
    for (j in 0 until count) {
        val b = j * EmberStride
        val k = (j * 5) % Nodes
        e[b] = n[k * NodeStride] + (frame.random(j * 7 + 1) - 0.5f) * frame.u(26f)
        e[b + 1] = n[k * NodeStride + 1] - frame.u(2f) - frame.random(j * 7 + 2) * frame.u(8f)
        // Embers rise about as far as the ring is tall (less when the choir rings the icon, under the band).
        val rise = if (frame.crowded) frame.icon.height * 0.7f else frame.clear.height
        e[b + 2] = rise * (0.4f + 0.45f * frame.random(j * 7 + 3))
        e[b + 3] = frame.u(2.5f + 5f * frame.random(j * 7 + 4))
        e[b + 4] = frame.u(1.6f + 1.4f * frame.random(j * 7 + 5))
        e[b + 5] = frame.random(j * 7 + 6)
        // Two or three rises per loop: whole cycles, so every ember meets itself at the seam.
        e[b + 6] = if (frame.random(j * 7 + 7) < 0.5f) 2f else 3f
    }
}

private fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
