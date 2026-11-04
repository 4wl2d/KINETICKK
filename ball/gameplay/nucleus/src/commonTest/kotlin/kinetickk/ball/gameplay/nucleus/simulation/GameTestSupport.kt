// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.*

import kinetickk.ball.gameplay.nucleus.model.EchoHit
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.model.TotemChest
import kinetickk.ball.gameplay.nucleus.model.TrailPoint
import kinetickk.ball.gameplay.nucleus.model.WeaponHitCadence
import kinetickk.ball.content.api.WeaponId
import kotlin.math.max
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*

internal fun MutableGameState.setVelocityForTesting(x: Float, y: Float) {
    velocityX = x
    velocityY = y
}

internal fun MutableGameState.addEnemyForTesting(
    x: Float,
    y: Float,
    hp: Float = 1_000f,
    radius: Float = 17f,
    type: EnemyType = EnemyType.DRIFTER,
): Enemy {
    val enemy = Enemy(
        id = nextEntityId++,
        type = type,
        x = x,
        y = y,
        hp = hp,
        maxHp = hp,
        radius = radius,
    )
    enemies += enemy
    return enemy
}

/**
 * The Architect in its final phase and out of its shift, with [hp] below one capped hit: past every gate,
 * so a single damage instance may still finish it.
 */
internal fun MutableGameState.finalPhaseArchitectForTesting(
    x: Float,
    y: Float,
    maxHp: Float = 5_400f,
    hp: Float = 40f,
): Enemy {
    return addEnemyForTesting(x, y, hp = maxHp, type = EnemyType.ARCHITECT).also { architect ->
        require(hp > 0f && hp < architectHitCap(architect)) { "The Architect must sit below one capped hit" }
        architect.phase = ARCHITECT_FINAL_PHASE
        architect.shiftUntil = 0f
        architect.hp = hp
    }
}

/**
 * Puts a Totem chest with [charges] at ([x], [y]) (by default on the Core), armed now or after the materialise delay,
 * as the only waiting chest besides those already there; its id is a free structure id.
 */
internal fun MutableGameState.placeTotemChestForTesting(
    x: Float = coreX,
    y: Float = coreY,
    charges: Int = 1,
    armed: Boolean = true,
    id: Long = (900L + structures.totemChests.size) shl 8,
): TotemChest {
    val chest = TotemChest(id, x, y, charges, bornAt = elapsed, armedAt = if (armed) elapsed else elapsed + 0.6f)
    structures.totemChests = structures.totemChests + chest
    return chest
}

internal fun MutableGameState.equipWeaponForTesting(id: WeaponId) = equipRunWeapon(id)

/** Owns [ids] as a profile purchase before the run would (Totem and point-of-interest offers read this set). */
internal fun MutableGameState.unlockWeaponsForTesting(ids: Iterable<WeaponId>) {
    unlockedWeaponSet.addAll(ids)
    unlockedWeaponView = unlockedWeaponSet.toSet()
}

internal fun MutableGameState.acquireItemForTesting(id: Int) = acquireItem(id)

internal fun MutableGameState.acquireRelicForTesting(id: RelicId) = acquireRelic(id)

/**
 * Binds [id] as a run gets it through a Relic offer: the offer claims one of the run's Relic opportunities, then its
 * card binds or ranks the Relic. Gate setups bind their starting Relics with it, so they spend the Relic budget.
 */
internal fun MutableGameState.claimRelicForTesting(id: RelicId) {
    relicRun.relicClaims++
    acquireRelic(id)
}

internal fun MutableGameState.openRelicChoiceForTesting() {
    pendingRelicChoices++
    openNextPendingChoice()
}

internal fun MutableGameState.dropRelicForTesting(x: Float = coreX, y: Float = coreY) {
    pickups += Pickup(PickupType.RELIC, x, y)
}

internal fun MutableGameState.addProjectileForTesting() {
    projectiles += Projectile(coreX, coreY, 0f, 0f, 1f, 1f)
}

internal fun MutableGameState.addTrailPointForTesting() {
    trail += TrailPoint(coreX, coreY, 0f)
}

internal fun MutableGameState.addEchoHitForTesting() {
    echoHits += EchoHit(enemyId = 0, delay = 1f, damage = 1f, appliesDominant = false)
}

internal fun MutableGameState.killEnemyForTesting(
    type: EnemyType,
    x: Float = coreX,
    y: Float = coreY,
) {
    val enemy = addEnemyForTesting(x, y, hp = 1f, type = type)
    damageEnemy(enemy, 2f, killEligible = true)
    onEnemyKilled(enemy)
    enemies.remove(enemy)
}

internal fun MutableGameState.triggerWeaponContactForTesting(target: Enemy, continuous: Boolean = false): Float =
    dealWeaponDamage(
        target,
        baseAmount = 20f,
        cadence = if (continuous) WeaponHitCadence.CONTINUOUS else WeaponHitCadence.DISCRETE,
    ).amount

internal fun MutableGameState.damageEnemyForTesting(target: Enemy, amount: Float): Float =
    damageEnemy(target, amount).amount

internal fun MutableGameState.agonyMutationCountForTesting(id: WeaponId): Int = agonyMutationCounts[id.ordinal]

internal fun MutableGameState.agonyMutationCountsForTesting(): List<Int> = agonyMutationCounts.toList()

internal fun MutableGameState.echoHitCountForTesting(): Int = echoHits.size

internal fun MutableGameState.grantDataForTesting(amount: Float) = gainData(amount)

internal fun MutableGameState.amplifyWeaponForTesting(levels: Int = 1) {
    repeat(max(0, levels)) { amplifyCurrentWeapon() }
}
