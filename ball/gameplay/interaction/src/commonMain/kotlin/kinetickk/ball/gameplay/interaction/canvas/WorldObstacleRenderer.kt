// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.isOnScreen
import kinetickk.foundation.design.kkStroke
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * World obstacles, drawn over the backdrop and under everything else: an asteroid is a jagged ink
 * polygon (8 to 12 corners from its shape seed) with a bone rim, an unstable asteroid adds heat
 * cracks and a pulsing core, and a bumper is a glowing polarity ring. Outlines are cached per shape
 * and radius and drawn translated; obstacles off the view are skipped. Allocation-free per frame.
 */
internal fun DrawScope.drawObstacles(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val obstacles = engine.obstacles
    if (obstacles.isEmpty()) return
    val time = engine.renderElapsed
    for (index in obstacles.indices) {
        val obstacle = obstacles[index]
        val center = world(engine, obstacle.x, obstacle.y, shakeX, shakeY)
        if (!isOnScreen(center, obstacle.radius + 8f)) continue
        when (obstacle.kind) {
            ObstacleKind.BUMPER -> drawBumper(center, obstacle.radius, time, roles)
            ObstacleKind.ASTEROID, ObstacleKind.UNSTABLE -> drawAsteroid(obstacle, center, time, roles)
        }
    }
}

private fun DrawScope.drawAsteroid(obstacle: ObstacleProjection, center: Offset, time: Float, roles: KkRolePalette) {
    val outline = ObstaclePaths.outline(obstacle.shapeSeed, obstacle.radius)
    val unstable = obstacle.kind == ObstacleKind.UNSTABLE
    translate(center.x, center.y) {
        drawPath(outline, Kk.Ink3)
        drawPath(outline, Kk.Ink4, alpha = 0.6f, style = kkStroke(rimWidth(obstacle.radius)))
        drawPath(outline, (if (unstable) roles.heat else Kk.Bone).copy(alpha = if (unstable) 0.7f else 0.32f), style = kkStroke(2f))
        if (unstable) drawPath(ObstaclePaths.cracks(obstacle.shapeSeed, obstacle.radius), roles.heat.copy(alpha = 0.75f), style = kkStroke(1.5f))
    }
    if (unstable) {
        val pulse = (sin(time * 5.5f + obstacle.shapeSeed % 7) + 1f) * 0.5f
        drawCircle(roles.heat.copy(alpha = 0.18f + 0.22f * pulse), obstacle.radius * (0.28f + 0.06f * pulse), center)
        drawCircle(roles.heat.copy(alpha = 0.6f + 0.3f * pulse), obstacle.radius * 0.12f, center)
    }
}

/**
 * Width of an asteroid's inner rim: 18 % of its radius rounded to whole 2-unit steps. Radii are continuous,
 * so unrounded widths would give every rock its own stroke and crowd the shared stroke cache.
 */
internal fun rimWidth(radius: Float): Float = (radius * 0.09f).roundToInt().coerceAtLeast(1) * 2f

private fun DrawScope.drawBumper(center: Offset, radius: Float, time: Float, roles: KkRolePalette) {
    val glow = (sin(time * 3.2f) + 1f) * 0.5f
    drawCircle(roles.pol.copy(alpha = 0.08f + 0.06f * glow), radius * 1.45f, center)
    drawCircle(Kk.Ink2, radius, center)
    drawCircle(roles.pol.copy(alpha = 0.85f), radius, center, style = kkStroke(4f))
    drawCircle(roles.pol.copy(alpha = 0.35f + 0.25f * glow), radius * 0.62f, center, style = kkStroke(2f))
}

/**
 * Obstacle outlines centred at the origin, keyed by shape seed and radius: built once, then drawn
 * under translate. Bounded round robin (the window holds at most 128 obstacles, a view far fewer);
 * draw-thread confined.
 */
internal object ObstaclePaths {
    private const val CAPACITY = 96
    private const val OUTLINE = 0
    private const val CRACKS = 1
    private val seeds = IntArray(CAPACITY)
    private val radii = FloatArray(CAPACITY) { -1f }
    private val kinds = IntArray(CAPACITY) { -1 }
    private val paths = arrayOfNulls<Path>(CAPACITY)
    private var next = 0

    /** The jagged rim of an asteroid with [seed] and [radius]. */
    fun outline(seed: Int, radius: Float): Path = get(OUTLINE, seed, radius)

    /** Three cracks from the centre of an unstable asteroid toward its rim. */
    fun cracks(seed: Int, radius: Float): Path = get(CRACKS, seed, radius)

    /** Corner count of an outline: 8..12 from the seed. */
    fun corners(seed: Int): Int = 8 + ((seed % 5) + 5) % 5

    private fun get(kind: Int, seed: Int, radius: Float): Path {
        for (index in 0 until CAPACITY) {
            if (kinds[index] == kind && seeds[index] == seed && radii[index] == radius) return paths[index]!!
        }
        val path = paths[next]?.also { it.rewind() } ?: Path()
        if (kind == OUTLINE) buildOutline(path, seed, radius) else buildCracks(path, seed, radius)
        kinds[next] = kind
        seeds[next] = seed
        radii[next] = radius
        paths[next] = path
        next = (next + 1) % CAPACITY
        return path
    }

    private fun buildOutline(path: Path, seed: Int, radius: Float) {
        val corners = corners(seed)
        val step = TWO_PI / corners
        for (corner in 0 until corners) {
            val angle = corner * step + (unit(seed, corner * 2) - 0.5f) * step * 0.6f
            val reach = radius * (0.84f + 0.16f * unit(seed, corner * 2 + 1))
            val x = cos(angle) * reach
            val y = sin(angle) * reach
            if (corner == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun buildCracks(path: Path, seed: Int, radius: Float) {
        for (crack in 0 until 3) {
            val angle = crack * TWO_PI / 3f + unit(seed, 40 + crack) * 1.2f
            val bend = angle + (unit(seed, 50 + crack) - 0.5f) * 0.8f
            path.moveTo(cos(angle) * radius * 0.14f, sin(angle) * radius * 0.14f)
            path.lineTo(cos(bend) * radius * 0.5f, sin(bend) * radius * 0.5f)
            path.lineTo(cos(angle) * radius * 0.78f, sin(angle) * radius * 0.78f)
        }
    }

    /** A stateless hash of the seed and [salt] mapped to [0, 1). */
    private fun unit(seed: Int, salt: Int): Float {
        var mixed = seed * -0x61c88647 + salt * 0x27d4eb2d
        mixed = (mixed xor (mixed ushr 15)) * 0x2c1b3c6d
        mixed = (mixed xor (mixed ushr 12)) * 0x297a2d39
        mixed = mixed xor (mixed ushr 15)
        return (mixed ushr 8) / 16_777_216f
    }

    private const val TWO_PI: Float = 6.2831855f
}
