// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.MatrixNodeDefinition
import kotlin.math.max
import kotlin.math.min

/**
 * Run-constant totals of the owned Core Matrix effects, folded from content and the profile ranks
 * (see [MatrixEffect] for how ranks and nodes combine). Neutral values mean "not owned".
 * Obstacle values ([obstacleDamageMultiplier], [obstacleLethalSpeedBonus], [ricochetRig], [nearMiss],
 * [ricochetPhaseSeconds]) are only stored here; the obstacle systems read them.
 */
internal data class MatrixModifiers(
    /** Summed stat deltas by [ItemEffect.ordinal]: per-rank amount × owned ranks. */
    val statTotals: List<Float> = List(ItemEffect.entries.size) { 0f },
    val damageMultiplier: Float = 1f,
    val reactionPowerBonus: Float = 0f,
    val statusPotencyBonus: Float = 0f,
    val dashHeatMultiplier: Float = 1f,
    val dragMultiplier: Float = 1f,
    val startingWeaponLevels: Int = 0,
    val startingOverdrive: Float = 0f,
    val secondWind: MatrixEffect.SecondWind? = null,
    val obstacleDamageMultiplier: Float = 1f,
    val obstacleLethalSpeedBonus: Float = 0f,
    val ricochetRig: MatrixEffect.RicochetRig? = null,
    val nearMiss: MatrixEffect.NearMiss? = null,
    /** Every n-th item offer shows one more card; 0 = never. */
    val offerCardsEvery: Int = 0,
    /** Offers before a guaranteed Rare or better card; 0 = the default pity. */
    val rarityPityOffers: Int = 0,
    val pickupValueBonus: Float = 0f,
    val eliteBounty: Boolean = false,
    val extraHeirloomOffers: Int = 0,
    val rebornChanceBonus: Float = 0f,
    val enemyIntegrityMultiplier: Float = 1f,
    val contactDamageMultiplier: Float = 1f,
    /** Seconds between heat-free Dashes; 0 = none. */
    val freeDashInterval: Float = 0f,
    val rerolls: Int = 0,
    /** Rank-0 Data-economy items the draft deck deals beyond the deck policy's Data floor. */
    val dataStratumItems: Int = 0,
    /** Offer weight of Data-economy items in ordinary item offers; 1 = not owned. */
    val dataOfferWeight: Float = 1f,
    val weaponRunGrowthBonus: Float = 0f,
    val shieldRegenDelayMultiplier: Float = 1f,
    val ricochetPhaseSeconds: Float = 0f,
) {
    fun statTotal(effect: ItemEffect): Float = statTotals[effect.ordinal]
}

/** Folds every owned effect of [nodes]; [rank] gives the owned ranks of the node at a content index. */
internal fun matrixModifiers(nodes: List<MatrixNodeDefinition>, rank: (index: Int) -> Int): MatrixModifiers {
    val stats = FloatArray(ItemEffect.entries.size)
    var result = MatrixModifiers()
    nodes.forEachIndexed { index, node ->
        val ranks = rank(index).coerceIn(0, node.maxRanks)
        if (ranks == 0) return@forEachIndexed
        node.effects.forEach { effect ->
            result = result.with(effect, ranks, stats)
        }
    }
    return result.copy(statTotals = stats.toList())
}

private fun MatrixModifiers.with(effect: MatrixEffect, ranks: Int, stats: FloatArray): MatrixModifiers {
    fun multiplier(current: Float, perRank: Float): Float = max(MIN_MATRIX_MULTIPLIER, current + (perRank - 1f) * ranks)
    return when (effect) {
        is MatrixEffect.Stat -> {
            stats[effect.delta.effect.ordinal] += effect.delta.amount * ranks
            this
        }
        is MatrixEffect.DamageMultiplier -> copy(damageMultiplier = damageMultiplier + effect.fraction * ranks)
        is MatrixEffect.ReactionPower -> copy(reactionPowerBonus = reactionPowerBonus + effect.fraction * ranks)
        is MatrixEffect.StatusPotency -> copy(statusPotencyBonus = statusPotencyBonus + effect.fraction * ranks)
        is MatrixEffect.DashHeat -> copy(dashHeatMultiplier = multiplier(dashHeatMultiplier, effect.multiplier))
        is MatrixEffect.Drag -> copy(dragMultiplier = multiplier(dragMultiplier, effect.multiplier))
        is MatrixEffect.StartingWeaponLevel -> copy(startingWeaponLevels = startingWeaponLevels + effect.levels * ranks)
        is MatrixEffect.StartingOverdrive -> copy(startingOverdrive = min(100f, startingOverdrive + effect.charge * ranks))
        is MatrixEffect.SecondWind -> copy(
            secondWind = secondWind?.takeIf { it.integrityFraction >= effect.integrityFraction } ?: effect,
        )
        is MatrixEffect.ObstacleGuard -> copy(
            obstacleDamageMultiplier = max(0f, obstacleDamageMultiplier - (1f - effect.damageMultiplier) * ranks),
            obstacleLethalSpeedBonus = obstacleLethalSpeedBonus + effect.lethalSpeedBonus * ranks,
        )
        is MatrixEffect.RicochetRig -> copy(
            ricochetRig = ricochetRig?.let {
                MatrixEffect.RicochetRig(max(it.restitution, effect.restitution), max(it.burstRadius, effect.burstRadius))
            } ?: effect,
        )
        is MatrixEffect.NearMiss -> copy(nearMiss = nearMiss?.takeIf { it.overdrive >= effect.overdrive } ?: effect)
        is MatrixEffect.OfferCards -> copy(
            offerCardsEvery = if (offerCardsEvery == 0) effect.everyNthOffer else min(offerCardsEvery, effect.everyNthOffer),
        )
        is MatrixEffect.RarityPity -> copy(
            rarityPityOffers = if (rarityPityOffers == 0) effect.offers else min(rarityPityOffers, effect.offers),
        )
        is MatrixEffect.PickupValue -> copy(pickupValueBonus = pickupValueBonus + effect.fraction * ranks)
        MatrixEffect.EliteBounty -> copy(eliteBounty = true)
        is MatrixEffect.HeirloomOffers -> copy(extraHeirloomOffers = extraHeirloomOffers + effect.extra * ranks)
        is MatrixEffect.RebornChance -> copy(rebornChanceBonus = rebornChanceBonus + effect.bonus * ranks)
        is MatrixEffect.EnemyIntegrity -> copy(enemyIntegrityMultiplier = multiplier(enemyIntegrityMultiplier, effect.multiplier))
        is MatrixEffect.ContactDamage -> copy(contactDamageMultiplier = multiplier(contactDamageMultiplier, effect.multiplier))
        is MatrixEffect.FreeDashInterval -> copy(
            freeDashInterval = if (freeDashInterval == 0f) effect.seconds else min(freeDashInterval, effect.seconds),
        )
        is MatrixEffect.Rerolls -> copy(rerolls = rerolls + effect.count * ranks)
        is MatrixEffect.DataStratum -> copy(dataStratumItems = dataStratumItems + effect.items * ranks)
        is MatrixEffect.DataOfferWeight -> copy(dataOfferWeight = dataOfferWeight + effect.fraction * ranks)
        is MatrixEffect.WeaponRunGrowth -> copy(weaponRunGrowthBonus = weaponRunGrowthBonus + effect.bonusPerProgress * ranks)
        is MatrixEffect.ShieldRegenDelay -> copy(
            shieldRegenDelayMultiplier = multiplier(shieldRegenDelayMultiplier, effect.multiplier),
        )
        is MatrixEffect.RicochetPhase -> copy(ricochetPhaseSeconds = max(ricochetPhaseSeconds, effect.seconds))
    }
}

private const val MIN_MATRIX_MULTIPLIER: Float = 0.1f

/** Folds the owned Core Matrix ranks of this run's profile. */
internal fun MutableGameState.foldMatrix(): MatrixModifiers =
    matrixModifiers(content.matrixNodes) { index -> matrixRanks[index] }

/**
 * Applies the folded Matrix stat totals to the run-start stats, once per stat, with the same rules
 * as an item stat ([applyStatDelta]).
 */
internal fun MutableGameState.applyMatrixStats() {
    ItemEffect.entries.forEach { effect ->
        val total = matrix.statTotal(effect)
        if (total != 0f) applyStatDelta(effect, total)
    }
}

/**
 * [base] after one application of the Matrix total for [effect], mirroring [applyStatDelta] for a
 * single scalar; the build summary reports the difference as the Matrix contribution.
 */
internal fun MatrixModifiers.statValue(effect: ItemEffect, base: Float, pickupRadiusFloor: Float): Float {
    val amount = statTotal(effect)
    if (amount == 0f) return base
    return when (effect) {
        ItemEffect.MAGNETISM, ItemEffect.COOLING, ItemEffect.DASH_POWER -> base * max(0.2f, 1f + amount)
        ItemEffect.DASH_EFFICIENCY -> max(12f, base * (1f - amount.coerceAtMost(0.25f)))
        ItemEffect.CRIT_CHANCE -> (base + amount * (1f - base / 0.75f)).coerceIn(0f, 0.75f)
        ItemEffect.DAMAGE_REDUCTION -> (base + amount * (1f - base / 0.65f)).coerceIn(0f, 0.65f)
        ItemEffect.CRIT_DAMAGE -> max(1f, base + amount)
        ItemEffect.PICKUP_RADIUS -> max(pickupRadiusFloor, base + amount)
        ItemEffect.MAX_INTEGRITY -> max(10f, base + amount)
        else -> base + amount
    }
}

/** True while the next Dash costs no heat (Perpetual Drift). */
internal fun MutableGameState.matrixFreeDashReady(): Boolean =
    matrix.freeDashInterval > 0f && matrixFreeDashReadyAt <= elapsed

/** Spends the heat-free Dash when one is ready; returns true when it was spent. */
internal fun MutableGameState.spendMatrixFreeDash(): Boolean {
    if (!matrixFreeDashReady()) return false
    matrixFreeDashReadyAt = elapsed + matrix.freeDashInterval
    return true
}

/** Offers before the Rare-or-better pity card: the Matrix may shorten [PITY_OFFERS]. */
internal fun MutableGameState.pityOffers(): Int =
    if (matrix.rarityPityOffers > 0) min(PITY_OFFERS, matrix.rarityPityOffers) else PITY_OFFERS

/** True when the current item offer is one the Matrix widens by a card. */
internal fun MutableGameState.matrixWidensItemOffer(): Boolean =
    matrix.offerCardsEvery > 0 && itemOffersOpened > 0 && itemOffersOpened % matrix.offerCardsEvery == 0

/**
 * Second Wind: once per run, lethal integrity damage leaves the owned share of maximum integrity and
 * a short invulnerability instead. Returns true when it fired.
 */
internal fun MutableGameState.trySecondWind(): Boolean {
    val secondWind = matrix.secondWind ?: return false
    if (secondWindSpent || hp > 0f) return false
    secondWindSpent = true
    hp = max(1f, maxHp * secondWind.integrityFraction)
    invulnerableUntil = max(invulnerableUntil, elapsed + secondWind.invulnerableSeconds)
    runGrace = max(runGrace, secondWind.invulnerableSeconds)
    message = "SECOND WIND"
    messageTime = 2f
    return true
}
