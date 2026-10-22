// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sqrt

const val TAU: Float = (PI * 2.0).toFloat()

fun distanceSquared(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    return dx * dx + dy * dy
}

fun pointToSegmentDistanceSquared(
    px: Float,
    py: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
): Float = pointToSegmentDistanceSquaredDouble(px, py, ax, ay, bx, by).toFloat()

/** True when segment (ax, ay)-(bx, by) and segment (cx, cy)-(dx, dy) cross (touching ends count). */
fun segmentsCross(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
    val d1 = orientation(cx, cy, dx, dy, ax, ay)
    val d2 = orientation(cx, cy, dx, dy, bx, by)
    val d3 = orientation(ax, ay, bx, by, cx, cy)
    val d4 = orientation(ax, ay, bx, by, dx, dy)
    return d1 * d2 <= 0.0 && d3 * d4 <= 0.0 && !(d1 == 0.0 && d2 == 0.0 && d3 == 0.0 && d4 == 0.0)
}

/** Twice the signed area of (ax, ay), (bx, by), (cx, cy), in Double so near-parallel segments stay exact enough. */
private fun orientation(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Double =
    (bx.toDouble() - ax) * (cy.toDouble() - ay) - (by.toDouble() - ay) * (cx.toDouble() - ax)

fun segmentCircleIntersects(
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    cx: Float,
    cy: Float,
    radius: Float,
): Boolean {
    if (radius < 0f) return false
    val radiusDouble = radius.toDouble()
    return pointToSegmentDistanceSquaredDouble(cx, cy, ax, ay, bx, by) <= radiusDouble * radiusDouble
}

private fun pointToSegmentDistanceSquaredDouble(
    px: Float,
    py: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
): Double {
    val segmentX = bx.toDouble() - ax.toDouble()
    val segmentY = by.toDouble() - ay.toDouble()
    val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY
    if (segmentLengthSquared == 0.0) {
        val pointX = px.toDouble() - ax.toDouble()
        val pointY = py.toDouble() - ay.toDouble()
        return pointX * pointX + pointY * pointY
    }

    val projection = (
        (px.toDouble() - ax.toDouble()) * segmentX +
            (py.toDouble() - ay.toDouble()) * segmentY
        ) / segmentLengthSquared
    val amount = projection.coerceIn(0.0, 1.0)
    val closestX = ax.toDouble() + segmentX * amount
    val closestY = ay.toDouble() + segmentY * amount
    val distanceX = px.toDouble() - closestX
    val distanceY = py.toDouble() - closestY
    return distanceX * distanceX + distanceY * distanceY
}

fun length(x: Float, y: Float): Float = sqrt(x * x + y * y)

fun clamp(value: Float, minimum: Float, maximum: Float): Float =
    value.coerceIn(minimum, maximum)

fun lerp(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

fun softVelocity(value: Float, knee: Float = 720f, scale: Float = 480f): Float {
    require(knee.isFinite() && knee >= 0f) { "knee must be finite and non-negative" }
    require(scale.isFinite() && scale > 0f) { "scale must be finite and positive" }
    if (value <= knee) return value

    val excess = value.toDouble() - knee.toDouble()
    return (knee.toDouble() + scale.toDouble() * ln(1.0 + excess / scale.toDouble())).toFloat()
}

private val NUMBER_SUFFIXES = arrayOf("", "K", "M", "B", "T", "Qa", "Qi")

fun abbreviateNumber(value: Long): String {
    if (value in -999L..999L) return value.toString()

    var scaled = value.toDouble()
    var suffixIndex = 0
    while (abs(scaled) >= 1_000.0 && suffixIndex < NUMBER_SUFFIXES.lastIndex) {
        scaled /= 1_000.0
        suffixIndex++
    }

    var roundedTenths = (scaled * 10.0).roundToLong()
    if (abs(roundedTenths) >= 10_000L && suffixIndex < NUMBER_SUFFIXES.lastIndex) {
        scaled /= 1_000.0
        suffixIndex++
        roundedTenths = (scaled * 10.0).roundToLong()
    }

    val whole = roundedTenths / 10L
    val fraction = abs(roundedTenths % 10L)
    val number = if (fraction == 0L) whole.toString() else "$whole.$fraction"
    return number + NUMBER_SUFFIXES[suffixIndex]
}
