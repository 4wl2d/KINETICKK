// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.VolatileBurstProjection
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.min

// Rebirth mutator behaviors owned by the Rebirth rework: Volatile Remains (R4), Entropy Tide (R7),
// Armored Vanguard (R8), Blackout's Data expiry (R9) and the Singularity Architect (R10). Every rule
// reads the run's tier profile, so a tier without the mutator behaves exactly as before.

/** Delay between an ordinary enemy's death and its Volatile Remains burst. */
internal const val VOLATILE_BURST_DELAY_SECONDS: Float = 0.35f
/** Radius of a Volatile Remains burst (and its telegraph ring). */
internal const val VOLATILE_BURST_RADIUS: Float = 70f
/** Base burst damage; it grows with the run like every hostile attack. */
internal const val VOLATILE_BURST_DAMAGE: Float = 6f
/** A Core at least this fast slips out of a burst. */
internal const val VOLATILE_ESCAPE_SPEED: Float = 400f

/** Entropy Tide keeps this share of every heal. */
internal const val ENTROPY_TIDE_HEALING: Float = 0.6f
/** Entropy Tide keeps this share of shield regeneration. */
internal const val ENTROPY_TIDE_SHIELD_REGEN: Float = 0.7f

/** Share of ordinary spawns that arrive armored under Armored Vanguard. */
internal const val ARMORED_VANGUARD_CHANCE: Float = 0.2f
/** Weapon damage an intact armor plate lets through. */
internal const val ARMOR_WEAPON_TAKEN: Float = 0.6f

/** Blackout: a Data pickup the magnet has not caught fades after this long. */
internal const val BLACKOUT_DATA_LIFE_SECONDS: Float = 10f

/** The Singularity Architect fires its enraged volleys this much more often. */
internal const val ARCHITECT_ENRAGE_ATTACK_RATE: Float = 1.45f

/** Ordinary enemies are neither Elites nor boss class (the Architect, mini-bosses such as the Herald, secret bosses). */
internal val Enemy.isOrdinary: Boolean get() = !type.profile.eliteClass

/** True while an Armored Vanguard plate still guards [this] against weapons. */
internal val Enemy.armorIntact: Boolean get() = armored && !armorCracked

/** R4: an ordinary enemy's death arms a burst [VOLATILE_BURST_DELAY_SECONDS] later inside a telegraph ring. */
internal fun MutableGameState.queueVolatileRemains(enemy: Enemy) {
    if (!runMutatorActive(RebirthMutator.VOLATILE_REMAINS) || !enemy.isOrdinary) return
    if (volatileBursts.size >= MutableGameState.MAX_VOLATILE_BURSTS) return
    volatileBursts = (volatileBursts + VolatileBurstProjection(
        x = enemy.x,
        y = enemy.y,
        radius = VOLATILE_BURST_RADIUS,
        armedAt = elapsed,
        detonatesAt = elapsed + VOLATILE_BURST_DELAY_SECONDS,
    )).toImmutableList()
}

/**
 * R4: detonates every due burst. A burst hurts a Core inside its ring that moves slower than
 * [VOLATILE_ESCAPE_SPEED] and is not in a Dash phase; the hit window keeps simultaneous bursts to one hit.
 * A burst is a hostile blast, so a Diamond's open parry (and Mirror Stance while braking) takes it, as it takes
 * any other blast ([hurtByHostileBlast]).
 */
internal fun MutableGameState.updateVolatileRemains() {
    if (volatileBursts.isEmpty() || phase != GamePhase.RUNNING) return
    var due = 0
    for (index in volatileBursts.indices) if (volatileBursts[index].detonatesAt <= elapsed) due++
    if (due == 0) return
    val bursts = volatileBursts
    volatileBursts = bursts.filter { it.detonatesAt > elapsed }.toImmutableList()
    for (index in bursts.indices) {
        val burst = bursts[index]
        if (burst.detonatesAt > elapsed) continue
        burst(burst.x, burst.y, 8, 2)
        val reach = burst.radius + coreRadius
        if (phase == GamePhase.RUNNING && dashPhaseTime <= 0f && speed < VOLATILE_ESCAPE_SPEED &&
            distanceSquared(burst.x, burst.y, coreX, coreY) <= reach * reach
        ) {
            val damage = hostileDamage(null, VOLATILE_BURST_DAMAGE)
            if (!tryCharacterParry(burst.x, burst.y, coreX - burst.x, coreY - burst.y, damage = damage)) takeDamage(damage)
        }
    }
}

/** R7: the share of a heal Entropy Tide lets through. */
internal fun MutableGameState.entropyTideHealing(): Float =
    if (runMutatorActive(RebirthMutator.ENTROPY_TIDE)) ENTROPY_TIDE_HEALING else 1f

/** R7: the share of shield regeneration Entropy Tide lets through. */
internal fun MutableGameState.entropyTideShieldRegen(): Float =
    if (runMutatorActive(RebirthMutator.ENTROPY_TIDE)) ENTROPY_TIDE_SHIELD_REGEN else 1f

/**
 * R8: one ordinary spawn in five arrives armored. The roll is a stateless SplitMix64 hash of the run
 * seed and the enemy id, so it draws nothing from the spawn stream and never shifts later spawns.
 */
internal fun MutableGameState.rollVanguardArmor(enemy: Enemy) {
    if (!runMutatorActive(RebirthMutator.ARMORED_VANGUARD) || !enemy.isOrdinary) return
    val hash = splitMix64(splitMix64(runSeed.toLong() xor ARMOR_SEED_SALT) xor enemy.id.toLong())
    enemy.armored = (hash ushr 40).toFloat() / (1 shl 24) < ARMORED_VANGUARD_CHANCE
}

/** Salt of the Armored Vanguard hash, distinct from the obstacle field's. */
private const val ARMOR_SEED_SALT: Long = 0x0A2D_0BE5_5EED_0008L

/** R8: a Core impact (or Dash contact) that lands cracks an intact plate for good. */
internal fun MutableGameState.crackArmor(enemy: Enemy, source: HitSource?) {
    if (!enemy.armorIntact || (source != HitSource.IMPACT && source != HitSource.DASH)) return
    enemy.armorCracked = true
    burst(enemy.x, enemy.y, 10, 0)
    emitSound(GameplayAudioCue.IMPACT)
}

/** R8: weapon damage an enemy takes through its plate (1 without an intact plate). */
internal fun armorWeaponFactor(enemy: Enemy, source: HitSource): Float =
    if (source == HitSource.WEAPON && enemy.armorIntact) ARMOR_WEAPON_TAKEN else 1f

/** R9: a fresh Data pickup fades after [BLACKOUT_DATA_LIFE_SECONDS] unless the magnet latches it first. */
internal fun MutableGameState.blackoutDataLife(life: Float): Float =
    if (runMutatorActive(RebirthMutator.BLACKOUT)) min(life, BLACKOUT_DATA_LIFE_SECONDS) else life

/** R10: the Architect's last phase index: 3 (a fourth, enraged phase) under Singularity, else 2. */
internal fun MutableGameState.architectFinalPhase(): Int = bossFinalPhase(architectSpec())

/** R10: true while the Singularity Architect is in its enraged fourth phase. */
internal fun MutableGameState.architectEnraged(enemy: Enemy): Boolean =
    enemy.isBoss && bossEnraged(enemy, architectSpec())

/** R10: the Singularity Architect arrives with a Herald escort, the run's third and last Herald. */
internal fun MutableGameState.spawnSingularityEscort() {
    if (!runMutatorActive(RebirthMutator.SINGULARITY)) return
    if (content.enemy(EnemyType.HERALD).minimumRebirth > rebirthLevel) return
    heraldsSpawned++
    if (enemies.size >= content.rebirth.maxActiveEnemies) evictOldestOrdinaryEnemy()
    spawnScheduled(EnemyType.HERALD)
}
