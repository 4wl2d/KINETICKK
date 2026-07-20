// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.simulation

import kinetickk.feature.game.domain.model.*
import kinetickk.feature.game.domain.protocol.SoundCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin


internal fun MutableGameState.onEnemyKilled(enemy: Enemy) {
    if (enemy.dead) return
    enemy.dead = true
    kills++
    combo++
    comboTime = comboWindow
    if (enemy.relicKillProcsEligible) triggerRelicKillEffects(enemy)
    val baseMatter = when (enemy.type) {
        EnemyType.ELITE -> 4f
        EnemyType.ARCHITECT -> 30f
        else -> 1f
    }
    val comboMultiplier = 1f + min(combo, 50) * 0.01f + velocityTier * 0.15f
    grantMatter(baseMatter * comboMultiplier)
    val dataCount = when (enemy.type) {
        EnemyType.DRIFTER -> 1
        EnemyType.SHOOTER -> 2
        EnemyType.CHARGER -> 3
        EnemyType.INTERCEPTOR -> 3
        EnemyType.WEAVER -> 3
        EnemyType.WARDEN -> 5
        EnemyType.SPLITTER -> 4
        EnemyType.ELITE -> 8
        EnemyType.ARCHITECT -> 20
    }
    overdriveCharge = min(125f, overdriveCharge + dataCount * 1.8f * overdriveGain)
    repeat(dataCount) {
        val angle = gameplayRandom.nextFloat() * TAU
        val pickupSpeed = 28f + gameplayRandom.nextFloat() * 70f
        addPickup(Pickup(PickupType.DATA, enemy.x, enemy.y, cos(angle) * pickupSpeed, sin(angle) * pickupSpeed))
    }
    if (enemy.type == EnemyType.ELITE) {
        addPickup(Pickup(PickupType.KEY, enemy.x, enemy.y))
        addPickup(Pickup(PickupType.RELIC, enemy.x + 15f, enemy.y - 8f))
    }
    if (enemy.type != EnemyType.ARCHITECT && gameplayRandom.nextFloat() < 0.035f + luck * 0.02f) {
        addPickup(Pickup(PickupType.REPAIR, enemy.x, enemy.y))
    }
    if (enemy.type == EnemyType.SPLITTER) spawnSplitterFragments(enemy)
    shockwave(
        enemy.x,
        enemy.y,
        if (enemy.type == EnemyType.ARCHITECT) 0.8f else 0.3f,
        enemy.radius * if (enemy.type == EnemyType.ARCHITECT) 4.5f else 2.7f,
        if (enemy.type == EnemyType.ELITE) 3 else 1,
    )
    burst(enemy.x, enemy.y, if (enemy.type == EnemyType.ARCHITECT) 60 else 12, if (enemy.type == EnemyType.ELITE) 3 else 1)
    emitSound(SoundCue.ENEMY_DESTROYED)
    if (enemy.type == EnemyType.ARCHITECT) {
        phase = GamePhase.VICTORY
        message = "ARCHITECT DISMANTLED"
        messageTime = 10f
        highestClearedRebirth = max(highestClearedRebirth, rebirthLevel)
        bankRunMatter()
        persist()
        emitSound(SoundCue.VICTORY)
    }
}

internal fun MutableGameState.triggerRelicKillEffects(enemy: Enemy) {
    val slipstreamRank = relicRank(RelicId.SLIPSTREAM_RELAY)
    if (slipstreamRank > 0 && speed >= 500f) {
        slipstreamRelayTime = 3f
        relicProcCounts[RelicId.SLIPSTREAM_RELAY.ordinal]++
    }

    val eventideRank = relicRank(RelicId.EVENTIDE_ANCHOR)
    if (eventideRank > 0) {
        val radius = 72f + 18f * eventideRank
        enemies.forEach { target ->
            if (!target.dead && target.hp > 0f && target.id != enemy.id) {
                val dx = enemy.x - target.x
                val dy = enemy.y - target.y
                val distance = max(1f, length(dx, dy))
                if (distance <= radius + target.radius) {
                    damageEnemy(target, 18f * eventideRank)
                    target.vx += dx / distance * 55f * eventideRank
                    target.vy += dy / distance * 55f * eventideRank
                }
            }
        }
        shockwave(enemy.x, enemy.y, 0.28f, radius, 2)
        relicProcCounts[RelicId.EVENTIDE_ANCHOR.ordinal]++
    }

    val secondHandRank = relicRank(RelicId.SECOND_HAND)
    if (secondHandRank > 0) {
        weaponClock -= 0.08f * secondHandRank
        weaponSecondaryClock -= 0.08f * secondHandRank
        relicProcCounts[RelicId.SECOND_HAND.ordinal]++
    }

    val splitRank = relicRank(RelicId.SPLIT_HORIZON)
    if (splitRank > 0) {
        nearestOtherEnemy(enemy.x, enemy.y, enemy.id, 760f)?.let { target ->
            fireRelicProjectile(enemy.x, enemy.y, target, 18f * splitRank)
            relicProcCounts[RelicId.SPLIT_HORIZON.ordinal]++
        }
    }

    val quietusRank = relicRank(RelicId.QUIETUS_BLOOM)
    if (quietusRank > 0) {
        val radius = 68f + 16f * quietusRank
        val slow = (1f - 0.10f * quietusRank).coerceAtLeast(0.35f)
        enemies.forEach { target ->
            if (!target.dead && target.hp > 0f && target.id != enemy.id &&
                distanceSquared(enemy.x, enemy.y, target.x, target.y) <= square(radius + target.radius)
            ) {
                damageEnemy(target, 10f * quietusRank)
                target.vx *= slow
                target.vy *= slow
            }
        }
        shockwave(enemy.x, enemy.y, 0.34f, radius, 4)
        relicProcCounts[RelicId.QUIETUS_BLOOM.ordinal]++
    }

    val tollRank = relicRank(RelicId.DEVOURERS_TOLL)
    if (tollRank > 0) {
        val index = RelicId.DEVOURERS_TOLL.ordinal
        relicCounters[index]++
        if (relicCounters[index] % 3 == 0) {
            hp = min(maxHp, hp + 1.5f * tollRank)
            shield = min(maxShield, shield + 1.5f * tollRank)
            relicProcCounts[index]++
        }
    }
}

internal fun MutableGameState.fireRelicProjectile(x: Float, y: Float, target: Enemy, damage: Float) {
    val angle = atan2(target.y - y, target.x - x)
    addProjectile(Projectile(
        x = x,
        y = y,
        vx = cos(angle) * 720f,
        vy = sin(angle) * 720f,
        radius = 5f,
        life = 2f,
        hostile = false,
        damage = damage,
        pierce = 0,
        colorIndex = 2,
        sourceWeapon = null,
    ))
}

internal fun MutableGameState.spawnSplitterFragments(enemy: Enemy) {
    val difficulty = 1f + elapsed / 470f
    val fragmentLimit = rebirthProfile.enemyCap(90)
    val fragmentCount = min(2, max(0, fragmentLimit - enemies.count { !it.dead }))
    repeat(fragmentCount) { index ->
        if (enemies.size >= MutableGameState.MAX_ENEMIES) return@repeat
        val angle = gameplayRandom.nextFloat() * TAU + index * TAU * 0.5f
        val fragmentHp = rebirthProfile.enemyHealth(24f * difficulty)
        val fragmentSpeed = 175f * rebirthProfile.enemySpeedMultiplier
        enemies += Enemy(
            id = nextEntityId++,
            type = EnemyType.DRIFTER,
            x = enemy.x + cos(angle) * 13f,
            y = enemy.y + sin(angle) * 13f,
            vx = cos(angle) * fragmentSpeed,
            vy = sin(angle) * fragmentSpeed,
            hp = fragmentHp,
            maxHp = fragmentHp,
            radius = 12f,
            actionTimer = 0.2f,
        )
    }
}
