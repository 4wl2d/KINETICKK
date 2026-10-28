// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ObstacleMix
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent

/**
 * Replaces the generated window with exactly [obstacles] (run-local positions), keeping it centred on the
 * Core's chunk so the next refresh leaves it alone while the Core stays in that chunk.
 */
internal fun MutableGameState.placeObstaclesForTesting(vararg obstacles: Obstacle) {
    val span = content.obstacles.windowChunks
    val centerX = absoluteChunk(worldOriginX + coreX)
    val centerY = absoluteChunk(worldOriginY + coreY)
    val cells = List(span * span) { mutableListOf<Obstacle>() }
    val half = span / 2
    obstacles.forEach { obstacle ->
        val column = absoluteChunk(worldOriginX + obstacle.x) - (centerX - half)
        val row = absoluteChunk(worldOriginY + obstacle.y) - (centerY - half)
        require(column in 0 until span && row in 0 until span) { "A test obstacle must sit inside the window" }
        cells[row * span + column] += obstacle
    }
    obstacleField = ObstacleField.of(centerX, centerY, span, cells)
}

internal fun testObstacle(
    x: Float,
    y: Float,
    radius: Float = 80f,
    kind: ObstacleKind = ObstacleKind.ASTEROID,
    id: Long = (x.toRawBits().toLong() shl 32) xor (y.toRawBits().toLong() and 0xFFFF_FFFFL),
    shapeSeed: Int = 7,
): Obstacle = Obstacle(id = id, kind = kind, x = x, y = y, radius = radius, shapeSeed = shapeSeed)

/** The canonical content with [mix] as the realspace obstacle kind mix. */
internal fun contentWithObstacleMix(mix: ObstacleMix): GameplayContentSnapshot {
    val obstacles = canonicalGameplayContent.obstacles
    return canonicalGameplayContent.copy(obstacles = obstacles.copy(kinds = obstacles.kinds.copy(space = mix)))
}

/** A quiet run with no generated obstacles at all (the window is empty but current). */
internal fun MutableGameState.clearObstaclesForTesting() = placeObstaclesForTesting()

/**
 * Moves the Core to ([x], [y]) at velocity ([vx], [vy]) as if the last step started at
 * (`x - vx·dt`, `y - vy·dt`), then resolves obstacles exactly as the fixed step does after the Core moves.
 */
internal fun MutableGameState.flyIntoObstaclesForTesting(x: Float, y: Float, vx: Float, vy: Float) {
    val delta = MutableGameState.FIXED_STEP
    previousCoreX = x - vx * delta
    previousCoreY = y - vy * delta
    coreX = x
    coreY = y
    setVelocityForTesting(vx, vy)
    updateObstacles()
}
