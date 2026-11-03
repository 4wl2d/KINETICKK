// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.world

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ObstacleKind
import kinetickk.ball.content.api.ObstacleKindTuning
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.obstacleSeedUnit
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Breakable kinds.

/**
 * Remembers that the obstacle with [id] broke for good, so regenerating its chunk skips it. The memory keeps the most
 * recent [ContentBounds.MAX_DESTROYED_OBSTACLES] ids.
 */
internal fun MutableGameState.recordDestroyedObstacle(id: Long) {
    val destroyed = destroyedObstacleIds + id
    destroyedObstacleIds = if (destroyed.size > ContentBounds.MAX_DESTROYED_OBSTACLES) {
        destroyed.subList(destroyed.size - ContentBounds.MAX_DESTROYED_OBSTACLES, destroyed.size).toList()
    } else destroyed
}

/**
 * The crystal at [index] shatters for good: it leaves the window and joins the destroyed memory, throws
 * [ObstacleKindTuning.crystalShards] Cryo shard lines through nearby enemies, drops [ObstacleKindTuning.crystalData]
 * Data and fires SHATTER with the crystal's kind from its centre, as hard as [impact]. A Core that broke it ([byCore])
 * keeps [ObstacleKindTuning.crystalSpeedRetention] of its speed and flies on; a crystal never hurts it.
 */
internal fun MutableGameState.shatterCrystal(index: Int, crystal: Obstacle, impact: Float, byCore: Boolean) {
    val kinds = content.obstacles.kinds
    obstacleField = obstacleField.removed(index)
    recordDestroyedObstacle(crystal.id)
    if (byCore) {
        velocityX *= kinds.crystalSpeedRetention
        velocityY *= kinds.crystalSpeedRetention
    }
    fireShards(crystal.x, crystal.y, kinds.crystalShards, kinds.crystalShardDamage * runScale(), StatusElement.CRYO, -1, 0, false)
    for (data in 0 until kinds.crystalData) {
        val angle = obstacleSeedUnit(crystal.shapeSeed, CRYSTAL_DATA_SALT + data) * TAU
        addPickup(Pickup(PickupType.DATA, crystal.x, crystal.y, cos(angle) * CRYSTAL_DATA_SPEED, sin(angle) * CRYSTAL_DATA_SPEED))
    }
    val cryo = FxColorCode.elementTone(StatusElement.CRYO)
    burst(crystal.x, crystal.y, CRYSTAL_BURST, cryo)
    shockwave(crystal.x, crystal.y, CRYSTAL_RING_SECONDS, crystal.radius * CRYSTAL_RING_SPAN, cryo)
    emitSound(GameplayAudioCue.IMPACT)
    screenShake = max(screenShake, CRYSTAL_SHAKE)
    if (phase != GamePhase.RUNNING) return
    fireClauses(
        EffectTrigger.SHATTER, null, impactPower(impact), obstacle = ObstacleKind.CRYSTAL,
        originX = crystal.x, originY = crystal.y,
    )
}

/**
 * An enemy's slam at [slamSpeed] broke the crystal with [id] this step (one break per step: further crystals wait for
 * their next contact). Nothing happens when it is no longer in the window.
 */
internal fun MutableGameState.shatterCrystalStruckByEnemy(id: Long, slamSpeed: Float) {
    val index = obstacleIndexOf(id)
    if (index >= 0) shatterCrystal(index, obstacleField.obstacles[index], slamSpeed, byCore = false)
}

/** Salts of the Data a crystal drops: one direction per pickup from its shape seed. */
private const val CRYSTAL_DATA_SALT: Int = 80
private const val CRYSTAL_DATA_SPEED: Float = 160f
private const val CRYSTAL_BURST: Int = 18
private const val CRYSTAL_RING_SECONDS: Float = 0.3f
private const val CRYSTAL_RING_SPAN: Float = 2.2f
private const val CRYSTAL_SHAKE: Float = 6f

/**
 * A dormant derelict mine arms once the Core comes within its arming reach (its body plus
 * [ObstacleKindTuning.mineArmMargin]): it blows [ObstacleKindTuning.mineFuseSeconds] later, telegraphed by a threat
 * ring that reaches the blast's edge as it goes off.
 */
internal fun MutableGameState.armMineNearCore(index: Int, mine: Obstacle, kinds: ObstacleKindTuning) {
    if (mine.fuseAt > 0f || phase != GamePhase.RUNNING) return
    val reach = mine.radius + kinds.mineArmMargin + coreRadius
    if (distanceSquared(coreX, coreY, mine.x, mine.y) >= reach * reach) return
    obstacleField = obstacleField.replaced(index, mine.copy(fuseAt = elapsed + kinds.mineFuseSeconds))
    shockwave(mine.x, mine.y, kinds.mineFuseSeconds, kinds.mineBlastRadius, THREAT_TONE)
}

/**
 * Sets the mine at [index] off on the quick fuse ([ObstacleKindTuning.mineQuickFuseSeconds] from now), arming it if it
 * was dormant; a fuse that already ends sooner stays.
 */
internal fun MutableGameState.quickFuseMine(index: Int) {
    val mine = obstacleField.obstacles[index]
    val quick = elapsed + content.obstacles.kinds.mineQuickFuseSeconds
    if (mine.fuseAt > 0f && mine.fuseAt <= quick) return
    obstacleField = obstacleField.replaced(index, mine.copy(fuseAt = quick))
}

/**
 * A player shot whose step from ([x0], [y0]) to ([x1], [y1]) runs into the body of the derelict mine at [index] sets
 * it off on the quick fuse; the shot flies on (a mine stops nothing). Other kinds are left alone.
 */
internal fun MutableGameState.fuseMineCrossedByShot(
    index: Int,
    obstacle: Obstacle,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    shotRadius: Float,
) {
    if (obstacle.kind != ObstacleKind.DERELICT_MINE) return
    if (obstacleStepEntry(x0, y0, x1, y1, obstacle.x, obstacle.y, obstacle.radius, shotRadius) < 0f) return
    quickFuseMine(index)
}

/**
 * Detonates the mines whose fuse ran out, at most [ObstacleKindTuning.maximumMineDetonationsPerStep] per step (the
 * rest wait for the next step). The window's field obstacles (mines among them) are walked from the last down, so a
 * removal only shifts indices already visited.
 */
internal fun MutableGameState.detonateDueMines() {
    val kinds = content.obstacles.kinds
    var detonations = 0
    var field = obstacleField
    var n = field.fieldCount - 1
    while (n >= 0) {
        if (detonations >= kinds.maximumMineDetonationsPerStep || phase != GamePhase.RUNNING) return
        val index = field.fieldIndex(n)
        val obstacle = field.obstacles[index]
        if (obstacle.kind == ObstacleKind.DERELICT_MINE && obstacle.fuseAt > 0f && obstacle.fuseAt <= elapsed) {
            detonateMine(index, obstacle, kinds)
            detonations++
            // The blast changed the window (and may have fused the mines it reached): go on below [index] in it.
            field = obstacleField
            n = field.fieldCount
            while (n > 0 && field.fieldIndex(n - 1) >= index) n--
        }
        n--
    }
}

/**
 * The mine at [index] goes off for good: it leaves the window and joins the destroyed memory, deals
 * [ObstacleKindTuning.mineBlastEnemyDamage] (with run growth) to every enemy within [ObstacleKindTuning.mineBlastRadius],
 * sets off the mines its blast reaches on the quick fuse, costs a Core inside the blast
 * [ObstacleKindTuning.mineBlastShare] of its maximum integrity as obstacle damage (a lethal blast ends the run as an
 * obstacle collision), and fires SHATTER with the mine's kind from its centre.
 */
private fun MutableGameState.detonateMine(index: Int, mine: Obstacle, kinds: ObstacleKindTuning) {
    obstacleField = obstacleField.removed(index)
    recordDestroyedObstacle(mine.id)
    val enemyDamage = kinds.mineBlastEnemyDamage * runScale()
    areaDamage(mine.x, mine.y, kinds.mineBlastRadius, enemyDamage)
    forEachObstacleNear(mine.x, mine.y, kinds.mineBlastRadius) { neighbourIndex, neighbour ->
        if (neighbour.kind != ObstacleKind.DERELICT_MINE) return@forEachObstacleNear
        val reach = kinds.mineBlastRadius + neighbour.radius
        if (distanceSquared(mine.x, mine.y, neighbour.x, neighbour.y) <= reach * reach) quickFuseMine(neighbourIndex)
    }
    val distance = length(coreX - mine.x, coreY - mine.y)
    if (distance <= kinds.mineBlastRadius + coreRadius) {
        takeDamage(
            maxHp * kinds.mineBlastShare * build.obstacleDamageScale,
            kind = IncomingDamage.OBSTACLE,
            obstacle = ObstacleKind.DERELICT_MINE,
        )
    }
    burst(mine.x, mine.y, MINE_BURST, THREAT_TONE)
    shockwave(mine.x, mine.y, MINE_RING_SECONDS, kinds.mineBlastRadius, THREAT_TONE)
    emitSound(GameplayAudioCue.IMPACT)
    screenShake = max(screenShake, MINE_SHAKE * (1f - distance / MINE_SHAKE_RANGE).coerceIn(0f, 1f))
    if (phase != GamePhase.RUNNING) return
    fireClauses(
        EffectTrigger.SHATTER, null, enemyDamage, obstacle = ObstacleKind.DERELICT_MINE,
        originX = mine.x, originY = mine.y,
    )
}

/** The threat effect tone: a mine is a danger. */
private const val THREAT_TONE: Int = 4
private const val MINE_BURST: Int = 22
private const val MINE_RING_SECONDS: Float = 0.35f
private const val MINE_SHAKE: Float = 10f

/** A blast this far from the Core or farther shakes nothing. */
private const val MINE_SHAKE_RANGE: Float = 900f
