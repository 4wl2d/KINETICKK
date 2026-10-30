// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.ObstacleMix
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.floor
import kotlin.math.roundToInt

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
    repels: Boolean = false,
    fuseAt: Float = 0f,
): Obstacle = Obstacle(id = id, kind = kind, x = x, y = y, radius = radius, shapeSeed = shapeSeed, fuseAt = fuseAt, repels = repels)

/**
 * An accelerator gate at ([x], [y]) whose normal points along [angle] radians: its shape seed carries the angle, so
 * `gateAngle(shapeSeed)` gives it back to within 2π / 65 536.
 */
internal fun testGate(x: Float, y: Float, angle: Float, radius: Float = 100f, id: Long = 61L): Obstacle {
    val turns = angle / 6.2831855f
    val seed = ((turns - floor(turns)) * 65_536f).roundToInt() and 0xFFFF
    return testObstacle(x, y, radius, ObstacleKind.ACCEL_GATE, id, shapeSeed = seed)
}

/** The canonical content with [mix] as the realspace obstacle kind mix. */
internal fun contentWithObstacleMix(mix: ObstacleMix): GameplayContentSnapshot {
    val obstacles = canonicalGameplayContent.obstacles
    return canonicalGameplayContent.copy(obstacles = obstacles.copy(kinds = obstacles.kinds.copy(space = mix)))
}

/**
 * This content (the canonical content by default) with every world layer's mix empty: the 0.5.0 field of asteroids and
 * bumpers on the same layout, each obstacle at its candidate's radius.
 */
internal fun GameplayContentSnapshot.withoutObstacleVariants(): GameplayContentSnapshot {
    val empty = ObstacleMix()
    return copy(obstacles = obstacles.copy(kinds = obstacles.kinds.copy(space = empty, zenith = empty, nadir = empty, archive = empty)))
}

internal fun withoutObstacleVariants(): GameplayContentSnapshot = canonicalGameplayContent.withoutObstacleVariants()

/**
 * A test item called [name] holding only [rules] and [clauses] (one CHAIN tag, one stack), for obstacle build hooks no
 * catalog item combines yet. Append it with [withProbes] and own it by name.
 */
internal fun obstacleProbe(name: String, vararg rules: ItemRule, clauses: List<EffectClause> = emptyList()): ItemDefinition =
    ItemDefinition(
        id = 0, name = name, description = "$name tests one obstacle build hook.", rarity = ItemRarity.RARE,
        tags = immutableListOf(BuildTag.CHAIN), clauses = clauses.toImmutableList(), rules = rules.toList().toImmutableList(),
        maxStacks = 1, unlockLevel = 1,
    )

/** This content with [probes] appended after its catalog (ids follow on). */
internal fun GameplayContentSnapshot.withProbes(vararg probes: ItemDefinition): GameplayContentSnapshot =
    copy(items = (items + probes.mapIndexed { index, probe -> probe.copy(id = items.size + index) }).toImmutableList())

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
