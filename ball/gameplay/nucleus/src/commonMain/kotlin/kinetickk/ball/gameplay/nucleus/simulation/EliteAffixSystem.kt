// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EliteAffix
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.SPLITTER_FRAGMENT_HEALTH
import kinetickk.ball.gameplay.nucleus.model.SPLITTER_FRAGMENT_RADIUS
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.VolatileBurstProjection
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Elite affixes and champions (variety.md §4). Every roll is a stateless hash of the run seed and the enemy id, so no
 * stream gains a draw. Each affix reuses an existing behaviour: the Herald's shield cycle, Splitter fragments, the
 * Warden's well, the Aegis tether, the Volatile Remains burst and Overclocked cadence.
 */

/** Notice of an Elite without an affix. */
internal const val ELITE_SIGNAL: String = "ELITE SIGNAL"

/** Distance of a Splitting Elite's fragments from where it died, as a Splitter's. */
private const val AFFIX_DROP_OFFSET: Float = 13f

/** Angle step of a Splitting Elite's fragment ring per id residue (mod 8), about 45°. */
private const val SPLIT_ANGLE_STEP: Float = 0.785f

/** Offset and outward speed of a champion's bonus Data. */
private const val CHAMPION_DATA_OFFSET: Float = 14f
private const val CHAMPION_DATA_SPEED: Float = 40f

/**
 * Rolls a new enemy's run variety after its aspect and armor: the Keynote's aspect bias and an affix for an Elite, the
 * champion flag for an ordinary ambient enemy (its integrity was already scaled by [championIntegrityScale]). Each is
 * a stateless hash keyed by the enemy id; a Shielded Elite raises its first shield the policy's first-shield delay
 * after it arrives.
 */
internal fun MutableGameState.rollEliteVariety(enemy: Enemy) {
    biasEliteAspect(enemy)
    val affix = rolledEliteAffix(enemy.id, enemy.isElite)
    enemy.affix = affix
    if (affix == EliteAffix.SHIELDED) enemy.abilityAt = elapsed + content.variety.affixes.firstShieldSeconds
    enemy.champion = rollsChampion(enemy.type, enemy.id, enemy.ownerTag)
}

/**
 * The affix an Elite with [id] rolls now, or null: none before the affix threat time, otherwise with
 * [eliteAffixChance] by a stateless hash whose low bits pick one of the six affixes uniformly.
 */
internal fun MutableGameState.rolledEliteAffix(id: Int, elite: Boolean): EliteAffix? {
    val policy = content.variety.affixes
    if (!elite || !policy.enabled || threatElapsed < policy.fromThreatSeconds) return null
    val hash = runHash(runSeed, AFFIX_SALT, id.toLong())
    if (hashUnit(hash) >= eliteAffixChance()) return null
    return EliteAffix.entries[((hash ushr 8) and 0xFFFF).toInt() % EliteAffix.entries.size]
}

/** The chance an Elite rolls an affix at this run's Rebirth tier: `min(1, base + perTier × tier)`. */
internal fun MutableGameState.eliteAffixChance(): Float {
    val policy = content.variety.affixes
    return min(1f, policy.baseChance + policy.chancePerTier * rebirthLevel)
}

/**
 * True when an enemy of [type] with [id] arrives as a champion: an ordinary kind other than the Swarmling, spawned by
 * the ambient director ([ownerTag] 0, so an encounter's members, site defenders and fragments never are), from the
 * champion threat time on, with [championChance] by a stateless hash.
 */
internal fun MutableGameState.rollsChampion(type: EnemyType, id: Int, ownerTag: Long): Boolean {
    val policy = content.variety.champions
    if (!policy.enabled || ownerTag != 0L || type == EnemyType.SWARMLING || type.profile.rank != EnemyRank.ORDINARY) {
        return false
    }
    if (threatElapsed < policy.fromThreatSeconds) return false
    return hashUnit(runHash(runSeed, CHAMPION_SALT, id.toLong())) < championChance()
}

/** The chance an eligible spawn is a champion at this run's Rebirth tier: `base + perTier × tier`. */
internal fun MutableGameState.championChance(): Float {
    val policy = content.variety.champions
    return policy.baseChance + policy.chancePerTier * rebirthLevel
}

/** The integrity factor of the enemy [spawnedEnemy] is about to build with [id]: the champion scale, or 1. */
internal fun MutableGameState.championIntegrityScale(type: EnemyType, id: Int, ownerTag: Long): Float =
    if (rollsChampion(type, id, ownerTag)) content.variety.champions.integrityScale else 1f

/** Movement factor of [enemy]'s steering: a Hasted Elite moves faster. */
internal fun MutableGameState.affixSpeedFactor(enemy: Enemy): Float =
    if (enemy.affix == EliteAffix.HASTED) content.variety.affixes.hasteSpeed else 1f

/** Attack cadence factor of [enemy]: a Hasted Elite attacks more often, like an Overclocked swarm. */
internal fun MutableGameState.affixCadence(enemy: Enemy): Float =
    if (enemy.affix == EliteAffix.HASTED) content.variety.affixes.hasteCadence else 1f

/**
 * One step of an affixed Elite's own behaviour, from its kind's branch while it can act: a Shielded Elite runs the
 * Herald's shield cycle (no summons), a Magnetic Elite the Warden's well at its strength and a Linked Elite the Aegis
 * tether. Splitting and Volatile act on death, Hasted through the speed and cadence factors.
 */
internal fun MutableGameState.updateEliteAffix(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    val affix = enemy.affix ?: return
    val policy = content.variety.affixes
    when (affix) {
        EliteAffix.SHIELDED -> if (elapsed >= enemy.abilityAt && enemy.shieldUntil <= elapsed) {
            enemy.shieldUntil = elapsed + policy.shieldSeconds
            enemy.abilityAt = elapsed + policy.shieldIntervalSeconds
            shockwave(enemy.x, enemy.y, 0.45f, enemy.radius * 2.6f, 3)
        }
        EliteAffix.MAGNETIC -> applyGravityWell(enemy, dx, dy, distance, delta, policy.wellStrength)
        EliteAffix.LINKED -> tetherNearestAllies(enemy, policy.linkCount)
        EliteAffix.SPLITTING, EliteAffix.VOLATILE, EliteAffix.HASTED -> Unit
    }
}

/**
 * The run-variety part of a kill, after its ordinary rewards: a Splitting Elite breaks into fragments, a Volatile Elite
 * arms a burst, and a champion pays its bonus Data and Matter. Nothing here draws from a stream.
 */
internal fun MutableGameState.onAffixedEnemyKilled(enemy: Enemy) {
    when (enemy.affix) {
        EliteAffix.SPLITTING -> spawnAffixFragments(enemy)
        EliteAffix.VOLATILE -> queueAffixBurst(enemy)
        else -> Unit
    }
    if (enemy.champion) payChampion(enemy)
}

/**
 * A Splitting Elite's fragments: Splitter fragments (Drifters with the fragment integrity and radius), as many as the
 * affix allows within the Splitter's cap rule, on a ring whose angles follow the id instead of a gameplay draw.
 */
internal fun MutableGameState.spawnAffixFragments(enemy: Enemy) {
    val difficulty = content.tempo.enemyHealthMultiplier(elapsed)
    var live = 0
    for (index in enemies.indices) if (!enemies[index].dead) live++
    val count = min(content.variety.affixes.splitFragments, max(0, rebirthProfile.enemyCap(90) - live))
    val fragmentHp = rebirthProfile.enemyHealth(SPLITTER_FRAGMENT_HEALTH * difficulty) * matrix.enemyIntegrityMultiplier
    val fragmentSpeed = 175f * rebirthProfile.enemySpeedMultiplier
    val ring = (enemy.id % 8) * SPLIT_ANGLE_STEP
    for (index in 0 until count) {
        if (enemies.size >= content.rebirth.maxActiveEnemies) return
        val angle = index * TAU / count + ring
        val fragment = Enemy(
            id = nextEntityId++,
            type = EnemyType.DRIFTER,
            x = enemy.x + cos(angle) * AFFIX_DROP_OFFSET,
            y = enemy.y + sin(angle) * AFFIX_DROP_OFFSET,
            vx = cos(angle) * fragmentSpeed,
            vy = sin(angle) * fragmentSpeed,
            hp = fragmentHp,
            maxHp = fragmentHp,
            radius = SPLITTER_FRAGMENT_RADIUS,
            actionTimer = 0.2f,
        )
        fragment.ownerTag = enemy.ownerTag
        enemies += fragment
        recordEncounterSpawn(enemy.ownerTag)
    }
}

/**
 * A Volatile Elite's burst: a Volatile Remains burst at the affix's radius and delay, armed whether or not the run has
 * the mutator; it detonates under the same damage law and escape speed.
 */
internal fun MutableGameState.queueAffixBurst(enemy: Enemy) {
    if (volatileBursts.size >= MutableGameState.MAX_VOLATILE_BURSTS) return
    val policy = content.variety.affixes
    volatileBursts = (volatileBursts + VolatileBurstProjection(
        x = enemy.x,
        y = enemy.y,
        radius = policy.burstRadius,
        armedAt = elapsed,
        detonatesAt = elapsed + policy.burstDelaySeconds,
    )).toImmutableList()
}

/**
 * A champion's bonus: its Data spread evenly around where it died, flying outward, and its Matter reward again
 * ([matterScale] − 1 times) under the kill's combo multiplier.
 */
internal fun MutableGameState.payChampion(enemy: Enemy) {
    val policy = content.variety.champions
    val count = policy.bonusData
    for (index in 0 until count) {
        val angle = index * TAU / count
        val x = cos(angle)
        val y = sin(angle)
        addPickup(Pickup(
            PickupType.DATA,
            enemy.x + x * CHAMPION_DATA_OFFSET,
            enemy.y + y * CHAMPION_DATA_OFFSET,
            x * CHAMPION_DATA_SPEED,
            y * CHAMPION_DATA_SPEED,
        ))
    }
    val comboMultiplier = 1f + min(combo, 50) * 0.01f + velocityTier * 0.15f
    grantMatter(
        enemy.type.profile.matterReward * (policy.matterScale - 1f) * comboMultiplier * content.tempo.matterRewardMultiplier,
    )
}

/** The feed notice of the Elite the director just called in: its affix's signal, or [ELITE_SIGNAL]. */
internal fun MutableGameState.eliteSignalNotice(): String {
    val elite = enemies.lastOrNull() ?: return ELITE_SIGNAL
    return if (elite.isElite) elite.affix?.signalNotice ?: ELITE_SIGNAL else ELITE_SIGNAL
}
