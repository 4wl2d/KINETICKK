// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList

/** The four Core Matrix branches; each is one column of the Matrix screen. */
enum class MatrixBranch(val displayLabel: String) {
    KINETIC("Kinetic"),
    BULWARK("Bulwark"),
    SYSTEMS("Systems"),
    FORTUNE("Fortune"),
}

/**
 * Stable Core Matrix node ids. Profiles persist them by name and index ranks by ordinal, so new
 * nodes are appended and existing ids are never renamed or removed.
 */
enum class MatrixNodeId {
    IMPACT_DRIVER,
    WEAPON_CALIBRATION,
    CRITICAL_LATTICE,
    ARMED_START,
    CATALYST_CORE,
    OVERDRIVE_COIL,
    REBORN_ARSENAL,
    GLASS_ENGINE,
    REINFORCED_HULL,
    HARDLIGHT_RESERVE,
    IMPACT_DAMPERS,
    SECOND_WIND,
    NANOFORGE,
    BASTION_PROTOCOL,
    AEGIS_LATTICE,
    JUGGERNAUT,
    MAGNET_ARRAY,
    CRYO_LOOP,
    VECTOR_THRUSTERS,
    NEAR_MISS,
    RICOCHET_RIG,
    OVERCLOCKED_REFLEXES,
    GRAVITY_SURFER,
    PERPETUAL_DRIFT,
    SALVAGE_PROTOCOL,
    DATA_ARCHIVE,
    REROLL_CACHE,
    WIDE_OFFER,
    LUCKY_CHARM,
    ELITE_BOUNTY,
    HEIRLOOM_VAULT,
    PROSPECTOR,
}

/**
 * What one owned rank of a node does. Gameplay folds every owned effect at run start:
 * - additive values ([Stat], fractions, levels, counts, charge, bonuses) apply once per rank and add
 *   across nodes;
 * - multipliers ([DashHeat], [Drag], [EnemyIntegrity], [ContactDamage], [ShieldRegenDelay] and the
 *   [ObstacleGuard] damage multiplier) add their difference from 1 once per rank, so two ranks of 0.9
 *   give 0.8;
 * - switches ([SecondWind], [RicochetRig], [NearMiss], [OfferCards], [RarityPity], [EliteBounty],
 *   [FreeDashInterval], [RicochetPhase]) apply once while the node has a rank; several owners keep
 *   the strongest value.
 */
sealed interface MatrixEffect {
    /** A passive stat change per rank, with the same rules as an item stat. */
    data class Stat(val delta: StatDelta) : MatrixEffect

    /** All damage the Core deals (hits, procs, statuses, reactions, abilities) × (1 + fraction). */
    data class DamageMultiplier(val fraction: Float) : MatrixEffect {
        init { requireFraction(fraction) }
    }

    data class ReactionPower(val fraction: Float) : MatrixEffect {
        init { requireFraction(fraction) }
    }

    data class StatusPotency(val fraction: Float) : MatrixEffect {
        init { requireFraction(fraction) }
    }

    /** Dash heat cost × multiplier. */
    data class DashHeat(val multiplier: Float) : MatrixEffect {
        init { requireMultiplier(multiplier) }
    }

    /** Drag coefficient × multiplier. */
    data class Drag(val multiplier: Float) : MatrixEffect {
        init { requireMultiplier(multiplier) }
    }

    /** Runs start this many weapon levels higher; the automatic weapon level cap rises as much. */
    data class StartingWeaponLevel(val levels: Int) : MatrixEffect {
        init { require(levels in 1..MAX_STARTING_WEAPON_LEVELS) { "Starting weapon levels must be 1..$MAX_STARTING_WEAPON_LEVELS" } }
    }

    data class StartingOverdrive(val charge: Float) : MatrixEffect {
        init { require(charge.isFinite() && charge > 0f && charge <= 100f) { "Starting Overdrive must be within 0..100" } }
    }

    /** Once per run, lethal integrity damage leaves [integrityFraction] of maximum integrity instead. */
    data class SecondWind(val integrityFraction: Float, val invulnerableSeconds: Float) : MatrixEffect {
        init {
            require(integrityFraction.isFinite() && integrityFraction > 0f && integrityFraction <= 1f) {
                "Second Wind integrity must be within 0..1"
            }
            require(invulnerableSeconds.isFinite() && invulnerableSeconds in 0f..10f) {
                "Second Wind invulnerability must be within 0..10 seconds"
            }
        }
    }

    /** Obstacle collision damage × [damageMultiplier]; the lethal collision speed rises by [lethalSpeedBonus]. */
    data class ObstacleGuard(val damageMultiplier: Float, val lethalSpeedBonus: Float) : MatrixEffect {
        init {
            require(damageMultiplier.isFinite() && damageMultiplier in 0f..1f) { "Obstacle damage multiplier must be within 0..1" }
            require(lethalSpeedBonus.isFinite() && lethalSpeedBonus >= 0f) { "Lethal speed bonus must be non-negative" }
        }
    }

    /** Asteroid bounces keep [restitution] of the normal speed (never above 1) and burst within [burstRadius]. */
    data class RicochetRig(val restitution: Float, val burstRadius: Float) : MatrixEffect {
        init {
            require(restitution.isFinite() && restitution > 0f && restitution <= 1f) { "Restitution must be within 0..1" }
            require(burstRadius.isFinite() && burstRadius in 0f..ContentBounds.MAX_EFFECT_RADIUS) { "Burst radius out of range" }
        }
    }

    /** Passing within [distance] of an asteroid at [minSpeed] or more grants [overdrive], at most once per [cooldown]. */
    data class NearMiss(val distance: Float, val minSpeed: Float, val overdrive: Float, val cooldown: Float) : MatrixEffect {
        init {
            require(distance.isFinite() && distance > 0f && distance <= ContentBounds.MAX_EFFECT_RADIUS) { "Near-miss distance out of range" }
            require(minSpeed.isFinite() && minSpeed > 0f) { "Near-miss speed must be positive" }
            require(overdrive.isFinite() && overdrive > 0f && overdrive <= 100f) { "Near-miss Overdrive out of range" }
            require(cooldown.isFinite() && cooldown > 0f) { "Near-miss cooldown must be positive" }
        }
    }

    /** Every [everyNthOffer]-th item offer of a run shows one extra card, within the choice bound. */
    data class OfferCards(val everyNthOffer: Int) : MatrixEffect {
        init { require(everyNthOffer >= 1) { "Offer interval must be positive" } }
    }

    /** A Rare or better card is guaranteed after [offers] offers without one. */
    data class RarityPity(val offers: Int) : MatrixEffect {
        init { require(offers >= 1) { "Pity offers must be positive" } }
    }

    /** Data from each Data pickup × (1 + fraction). */
    data class PickupValue(val fraction: Float) : MatrixEffect {
        init { requireFraction(fraction) }
    }

    /** Each Elite kill queues an extra item offer. */
    data object EliteBounty : MatrixEffect

    /**
     * Extra Heirloom cards per rank. An extra the offer cannot show (every carried Relic is already
     * offered, or the cards fill the choice bound beside the decline card) raises the taken Heirloom a
     * rank instead.
     */
    data class HeirloomOffers(val extra: Int) : MatrixEffect {
        init { require(extra in 1..MAX_EXTRA_HEIRLOOM_OFFERS) { "Extra Heirloom offers must be 1..$MAX_EXTRA_HEIRLOOM_OFFERS" } }
    }

    /** Added to the chance that an offered card is Reborn. */
    data class RebornChance(val bonus: Float) : MatrixEffect {
        init { require(bonus.isFinite() && bonus > 0f && bonus <= 1f) { "Reborn chance bonus must be within 0..1" } }
    }

    /** Spawn integrity of every enemy × multiplier. */
    data class EnemyIntegrity(val multiplier: Float) : MatrixEffect {
        init { requireMultiplier(multiplier) }
    }

    /** Contact damage the Core takes × multiplier. */
    data class ContactDamage(val multiplier: Float) : MatrixEffect {
        init { requireMultiplier(multiplier) }
    }

    /** One Dash every [seconds] costs no heat. */
    data class FreeDashInterval(val seconds: Float) : MatrixEffect {
        init { require(seconds.isFinite() && seconds > 0f) { "Free Dash interval must be positive" } }
    }

    data class Rerolls(val count: Int) : MatrixEffect {
        init { require(count in 1..MAX_MATRIX_REROLLS) { "Rerolls must be 1..$MAX_MATRIX_REROLLS" } }
    }

    /** Weapon run growth gains [bonusPerProgress] × run progress. */
    data class WeaponRunGrowth(val bonusPerProgress: Float) : MatrixEffect {
        init { requireFraction(bonusPerProgress) }
    }

    /** The delay before the shield starts recharging × multiplier. */
    data class ShieldRegenDelay(val multiplier: Float) : MatrixEffect {
        init { requireMultiplier(multiplier) }
    }

    /** Each ricochet grants [seconds] of Dash phase. */
    data class RicochetPhase(val seconds: Float) : MatrixEffect {
        init { require(seconds.isFinite() && seconds > 0f && seconds <= 2f) { "Ricochet phase must be within 0..2 seconds" } }
    }
}

private const val MAX_STARTING_WEAPON_LEVELS: Int = 3
private const val MAX_EXTRA_HEIRLOOM_OFFERS: Int = 2
private const val MAX_MATRIX_REROLLS: Int = 3

private fun requireFraction(value: Float) {
    require(value.isFinite() && value > 0f && value <= 2f) { "Matrix fraction must be within 0..2" }
}

private fun requireMultiplier(value: Float) {
    require(value.isFinite() && value > 0f && value <= 3f && value != 1f) { "Matrix multiplier must be within 0..3 and not 1" }
}

/**
 * One Core Matrix node. [row] is its prerequisite depth (0 = no prerequisite); [costs] lists the
 * Matter price of each rank; owning any one of [prerequisites] opens it; [minimumRebirth] is the
 * Rebirth rank it needs. Prerequisites and gates apply only at purchase.
 */
data class MatrixNodeDefinition(
    val id: MatrixNodeId,
    val name: String,
    val description: String,
    val branch: MatrixBranch,
    val row: Int,
    val maxRanks: Int,
    val costs: ImmutableList<Int>,
    val prerequisites: ImmutableList<MatrixNodeId>,
    val minimumRebirth: Int,
    val keystone: Boolean,
    val effects: ImmutableList<MatrixEffect>,
) {
    init {
        require(name.isNotBlank()) { "Matrix node name must not be blank" }
        require(description.isNotBlank()) { "Matrix node description must not be blank" }
        require(row in 0 until ContentBounds.MAX_MATRIX_ROWS) { "Matrix node row must be within the row bound" }
        require(maxRanks in 1..ContentBounds.MAX_MATRIX_NODE_RANKS) { "Matrix node ranks must be within the rank bound" }
        require(costs.size == maxRanks) { "Matrix node needs exactly one cost per rank" }
        require(costs.all { it > 0 }) { "Matrix node costs must be positive" }
        require(prerequisites.toSet().size == prerequisites.size && id !in prerequisites) {
            "Matrix node prerequisites must be distinct and exclude the node itself"
        }
        require((row == 0) == prerequisites.isEmpty()) { "Only first-row Matrix nodes have no prerequisite" }
        require(minimumRebirth in ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL) {
            "Matrix node Rebirth gate must be within the Rebirth bounds"
        }
        require(effects.size in 1..ContentBounds.MAX_MATRIX_NODE_EFFECTS) { "Matrix node needs 1..4 effects" }
    }

    /** Matter price of the next rank when [owned] ranks are owned. */
    fun cost(owned: Int): Int {
        require(owned in 0 until maxRanks) { "owned must be between 0 and ${maxRanks - 1}" }
        return costs[owned]
    }

    /**
     * Matter paid for [owned] ranks. Ranks above [maxRanks] (left by a rebalance that lowered the
     * maximum) are refunded at the last listed price.
     */
    fun spent(owned: Int): Long {
        if (owned <= 0) return 0L
        val listed = minOf(owned, maxRanks)
        var total = 0L
        for (rank in 0 until listed) total += costs[rank].toLong()
        return total + (owned - listed).toLong() * costs.last().toLong()
    }
}
