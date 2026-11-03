// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.kkMix

/** Volatile Compound: a flask with rising bubbles and a fizzing meniscus. */
internal fun DrawScope.drawVolatileCompoundArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val spot = frame.spot(0.64f, 0.62f, 1.0f)
    val cx = spot[0]
    val cy = spot[1]
    val r = spot[2]
    val g = spot[3]
    val cache = frame.cache
    val f = cache.floats(0, FlaskFloats) { flaskGeometry(it, cx, cy, r) }
    val glass = cache.path(0) { flask(f, 0f) }
    val wall = cache.path(1) { flask(f, r * 0.045f) }
    val liquid = cache.path(2) { flaskLiquid(f) }
    val surface = cache.path(3) {
        moveTo(f[FlaskLiquidLeft], f[FlaskLevel] - f[FlaskMeniscus])
        quadraticTo(cx, f[FlaskLevel] + f[FlaskMeniscus], f[FlaskLiquidRight], f[FlaskLevel] - f[FlaskMeniscus])
    }
    val bubbles = cache.path(4) {
        for (index in FlaskBubbles.indices step 3) {
            val x = cx + FlaskBubbles[index] * r
            val y = f[FlaskBase] - FlaskBubbles[index + 1] * r
            val radius = FlaskBubbles[index + 2] * r
            addOval(Rect(x - radius, y - radius, x + radius, y + radius))
        }
    }
    val glints = cache.path(7) {
        for (index in FlaskBubbles.indices step 3) {
            val radius = FlaskBubbles[index + 2] * r
            if (radius < r * 0.03f) continue
            val x = cx + FlaskBubbles[index] * r - radius * 0.38f
            val y = f[FlaskBase] - FlaskBubbles[index + 1] * r - radius * 0.38f
            val dot = radius * 0.26f
            addOval(Rect(x - dot, y - dot, x + dot, y + dot))
        }
    }
    val fizz = cache.path(5) {
        // Fine spray just over the surface, densest in the middle.
        for (index in 0 until 12) {
            val t = (index + 0.5f) / 12f
            val x = f[FlaskLiquidLeft] + (f[FlaskLiquidRight] - f[FlaskLiquidLeft]) * (0.1f + 0.8f * t)
            val lift = 1f - kotlin.math.abs(t - 0.5f) * 1.4f
            val y = f[FlaskLevel] - r * (0.03f + 0.09f * lift * frame.random(110 + index))
            val radius = r * (0.008f + 0.009f * frame.random(120 + index))
            addOval(Rect(x - radius, y - radius, x + radius, y + radius))
        }
    }
    val ticks = cache.path(6) {
        for (step in 0 until 4) {
            val y = f[FlaskBase] - r * (0.2f + step * 0.16f)
            val x = flaskWallX(f, y, left = true) + r * 0.05f
            moveTo(x, y)
            lineTo(x + r * (if (step % 2 == 0) 0.14f else 0.08f), y)
        }
    }
    val bone = cache.brush(0) { fadeBrush(frame.hue, cx, cy - r * 0.2f, r * 1.5f, 0.45f) }
    // The liquid and the glass take a steel tinted toward the card hue, so the compound reads as its colour.
    val steel = cache.brush(1) { fadeBrush(kkMix(Kk.Mute, frame.hue, 0.5f), cx, cy, r * 1.5f, 0.45f) }
    drawPath(liquid, steel, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawPath(liquid, bone, alpha = alphaOf(RewardArtAlpha.LOW * 0.6f, g))
    drawPath(wall, steel, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1f), join = StrokeJoin.Miter))
    drawPath(glass, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(2f), join = StrokeJoin.Miter))
    drawPath(ticks, bone, alpha = alphaOf(RewardArtAlpha.MID, g), style = cache.stroke(frame.u(1f)))
    drawPath(surface, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1.6f), StrokeCap.Round))
    drawPath(bubbles, steel, alpha = alphaOf(RewardArtAlpha.LOW, g))
    drawPath(bubbles, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1.2f)))
    drawPath(glints, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawPath(fizz, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g))
}

// region Volatile Compound

private const val FlaskBase = 0
private const val FlaskTop = 1
private const val FlaskNeck = 2
private const val FlaskLevel = 3
private const val FlaskMeniscus = 4
private const val FlaskLiquidLeft = 5
private const val FlaskLiquidRight = 6
private const val FlaskCx = 7
private const val FlaskBodyHalf = 8
private const val FlaskNeckHalf = 9
private const val FlaskChamfer = 10
private const val FlaskLip = 11
private const val FlaskFloats = 12

/** Bubbles: x from the center and height above the base (spot radii), and radius. Inside the liquid, then rising out of the neck. */
private val FlaskBubbles = floatArrayOf(
    -0.32f, 0.11f, 0.05f, 0.2f, 0.15f, 0.035f, -0.08f, 0.24f, 0.06f, 0.38f, 0.08f, 0.03f,
    0.04f, 0.1f, 0.025f, -0.2f, 0.3f, 0.022f, 0.12f, 0.3f, 0.028f,
    0.03f, 0.56f, 0.03f, -0.04f, 0.74f, 0.04f, 0.05f, 0.94f, 0.05f, -0.02f, 1.16f, 0.04f,
    0.08f, 1.5f, 0.032f, -0.07f, 1.66f, 0.024f,
)

private fun flaskGeometry(out: FloatArray, cx: Float, cy: Float, r: Float) {
    val base = cy + r * 0.62f
    out[FlaskBase] = base
    out[FlaskTop] = base - r * 1.38f
    out[FlaskNeck] = base - r * 0.84f
    out[FlaskCx] = cx
    out[FlaskBodyHalf] = r * 0.64f
    out[FlaskNeckHalf] = r * 0.15f
    out[FlaskChamfer] = r * 0.07f
    out[FlaskLip] = r * 0.05f
    out[FlaskLevel] = base - r * 0.42f
    out[FlaskMeniscus] = r * 0.035f
    out[FlaskLiquidLeft] = flaskWallX(out, out[FlaskLevel] - out[FlaskMeniscus], left = true)
    out[FlaskLiquidRight] = flaskWallX(out, out[FlaskLevel] - out[FlaskMeniscus], left = false)
}

/** The inner face of the cone wall at height [y]. */
private fun flaskWallX(f: FloatArray, y: Float, left: Boolean): Float {
    val bottom = f[FlaskBase] - f[FlaskChamfer]
    val t = ((bottom - y) / (bottom - f[FlaskNeck])).coerceIn(0f, 1f)
    val half = f[FlaskBodyHalf] + (f[FlaskNeckHalf] - f[FlaskBodyHalf]) * t
    return if (left) f[FlaskCx] - half else f[FlaskCx] + half
}

/** The conical flask outline, [inset] inside the glass for its inner wall. */
private fun Path.flask(f: FloatArray, inset: Float) {
    val cx = f[FlaskCx]
    val base = f[FlaskBase] - inset
    val top = f[FlaskTop] + inset
    val neck = f[FlaskNeck]
    val body = f[FlaskBodyHalf] - inset * 1.4f
    val neckHalf = f[FlaskNeckHalf] - inset
    val chamfer = f[FlaskChamfer]
    val lip = if (inset == 0f) f[FlaskLip] else 0f
    val lipHeight = f[FlaskLip] * 0.9f
    if (lip > 0f) {
        moveTo(cx - neckHalf - lip, top)
        lineTo(cx + neckHalf + lip, top)
        lineTo(cx + neckHalf + lip, top + lipHeight)
        lineTo(cx + neckHalf, top + lipHeight)
    } else {
        // The inner wall stays open at the mouth.
        moveTo(cx + neckHalf, top + lipHeight)
    }
    lineTo(cx + neckHalf, neck)
    lineTo(cx + body, base - chamfer)
    lineTo(cx + body - chamfer, base)
    lineTo(cx - body + chamfer, base)
    lineTo(cx - body, base - chamfer)
    lineTo(cx - neckHalf, neck)
    if (lip > 0f) {
        lineTo(cx - neckHalf, top + lipHeight)
        lineTo(cx - neckHalf - lip, top + lipHeight)
        close()
    } else {
        lineTo(cx - neckHalf, top + lipHeight)
    }
}

/** The liquid below the meniscus, inside the glass. */
private fun Path.flaskLiquid(f: FloatArray) {
    val cx = f[FlaskCx]
    val base = f[FlaskBase] - f[FlaskChamfer] * 0.4f
    val level = f[FlaskLevel]
    val rise = f[FlaskMeniscus]
    val body = f[FlaskBodyHalf] - f[FlaskChamfer] * 0.6f
    moveTo(f[FlaskLiquidLeft], level - rise)
    quadraticTo(cx, level + rise, f[FlaskLiquidRight], level - rise)
    lineTo(cx + body, base - f[FlaskChamfer])
    lineTo(cx + body - f[FlaskChamfer], base)
    lineTo(cx - body + f[FlaskChamfer], base)
    lineTo(cx - body, base - f[FlaskChamfer])
    close()
}

// endregion
