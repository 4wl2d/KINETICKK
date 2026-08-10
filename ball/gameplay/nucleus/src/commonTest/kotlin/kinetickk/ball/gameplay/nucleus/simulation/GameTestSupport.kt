// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.*

import kinetickk.ball.gameplay.nucleus.model.DelayedRelicHit
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.api.EnemyType
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.api.PickupType
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.model.Totem
import kinetickk.ball.gameplay.nucleus.model.TrailPoint
import kinetickk.ball.gameplay.nucleus.model.WeaponHitCadence
import kinetickk.ball.content.api.WeaponId
import kotlin.math.max

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
        relicCounters = IntArray(content.relics.size),
        relicTimers = FloatArray(content.relics.size),
        relicValues = FloatArray(content.relics.size),
    )
    enemies += enemy
    return enemy
}

internal fun MutableGameState.activateTotemForTesting(x: Float = coreX, y: Float = coreY) {
    keys++
    totem = Totem(x, y)
}

internal fun MutableGameState.equipWeaponForTesting(id: WeaponId) = equipRunWeapon(id)

internal fun MutableGameState.acquireItemForTesting(id: Int) = acquireItem(id)

internal fun MutableGameState.acquireRelicForTesting(id: RelicId) = acquireRelic(id)

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

internal fun MutableGameState.addDelayedRelicHitForTesting() {
    delayedRelicHits += DelayedRelicHit(RelicId.ECHO_CHAMBER, 0, 1f, 1f)
}

internal fun MutableGameState.killEnemyForTesting(
    type: EnemyType,
    x: Float = coreX,
    y: Float = coreY,
) {
    val enemy = addEnemyForTesting(x, y, hp = 1f, type = type)
    damageEnemy(enemy, 2f, relicKillProcsEligible = true)
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

internal fun MutableGameState.relicProcCountForTesting(id: RelicId): Int = relicProcCounts[id.ordinal]

internal fun MutableGameState.agonyMutationCountForTesting(id: WeaponId): Int = agonyMutationCounts[id.ordinal]

internal fun MutableGameState.agonyMutationCountsForTesting(): List<Int> = agonyMutationCounts.toList()

internal fun MutableGameState.delayedRelicHitCountForTesting(): Int = delayedRelicHits.size

internal fun MutableGameState.grantDataForTesting(amount: Float) = gainData(amount)

internal fun MutableGameState.amplifyWeaponForTesting(levels: Int = 1) {
    repeat(max(0, levels)) { amplifyCurrentWeapon() }
}
