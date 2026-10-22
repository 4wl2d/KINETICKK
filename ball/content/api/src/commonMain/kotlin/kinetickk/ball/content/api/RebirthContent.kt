// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A named rule a Rebirth tier adds to the run. Tier n activates entry n - 1 and every entry before
 * it; the order is the activation order, so entries are append-only.
 */
enum class RebirthMutator(
    val displayName: String,
    val description: String,
) {
    PACK_HUNTERS("Pack Hunters", "Enemies arrive in larger packs."),
    HARDENED_ELITES("Hardened Elites", "Elites carry more integrity and arrive with escorts."),
    HUNTER_PROTOCOL("Hunter Protocol", "Ranged hunters arrive earlier in the run."),
    VOLATILE_REMAINS("Volatile Remains", "Ordinary enemies burst shortly after they die."),
    FRACTURED_SPACE("Fractured Space", "Space holds more obstacles, and some asteroids are unstable."),
    OVERCLOCKED_SWARM("Overclocked Swarm", "Enemies attack more often."),
    ENTROPY_TIDE("Entropy Tide", "Healing and shield regeneration are weaker."),
    ARMORED_VANGUARD("Armored Vanguard", "Some enemies wear armor until an impact cracks it."),
    BLACKOUT("Blackout", "Data fades sooner and the minimap sees less."),
    SINGULARITY("Singularity", "The Architect gains an enraged fourth phase."),
}

/** Immutable, render-ready tuning for one Rebirth cycle. */
data class RebirthProfile(
    val tier: Int,
    /** The tier's player-facing name (English content key; localized by the content tables). */
    val displayName: String,
    val openingEnemyCount: Int,
    val enemyCapMultiplier: Float,
    val spawnRateMultiplier: Float,
    val enemyHealthMultiplier: Float,
    val enemySpeedMultiplier: Float,
    val incomingDamageMultiplier: Float,
    val eliteRateMultiplier: Float,
    val threatTimeOffsetSeconds: Float,
    val playerPowerMultiplier: Float,
    val playerIntegrityBonus: Float,
    val matterGainMultiplier: Float,
    val bonusRerolls: Int,
    val maximumActiveEnemies: Int,
    val minimumSpawnIntervalSeconds: Float,
    val minimumEliteIntervalSeconds: Float,
    /** Active mutators in activation order; see [RebirthMutator]. */
    val mutators: ImmutableList<RebirthMutator> = immutableListOf(),
    /** Extra enemies added to every spawned pack. */
    val packSizeBonus: Int = 0,
    /** Scales the mean number of world obstacles per chunk. */
    val obstacleDensityMultiplier: Float = 1f,
    /** Scales every run level's Data requirement at this tier (rounded per level, [dataRequirement]); 1 keeps the base curve. */
    val dataRequirementScale: Float = 1f,
) {
    init {
        require(tier >= 0) { "Rebirth tier must be non-negative" }
        require(displayName.isNotBlank()) { "Rebirth tier needs a display name" }
        require(maximumActiveEnemies > 0) { "Rebirth enemy cap must be positive" }
        require(minimumSpawnIntervalSeconds.isFinite() && minimumSpawnIntervalSeconds > 0f) {
            "Minimum spawn interval must be finite and positive"
        }
        require(minimumEliteIntervalSeconds.isFinite() && minimumEliteIntervalSeconds > 0f) {
            "Minimum elite interval must be finite and positive"
        }
        require(mutators.toSet().size == mutators.size) { "Rebirth mutators must be distinct" }
        require(packSizeBonus >= 0) { "Rebirth pack size bonus must not be negative" }
        require(obstacleDensityMultiplier.isFinite() && obstacleDensityMultiplier > 0f) {
            "Obstacle density multiplier must be finite and positive"
        }
        require(dataRequirementScale.isFinite() && dataRequirementScale >= 1f) {
            "Data requirement scale must be finite and at least 1"
        }
    }

    private val mutatorMask: Int = mutators.fold(0) { mask, mutator -> mask or (1 shl mutator.ordinal) }

    /** The mutator this tier adds on top of the previous one (null at tier 0). */
    val newestMutator: RebirthMutator? get() = mutators.lastOrNull()

    /** True when this cycle activates [mutator]; allocation-free for per-step reads. */
    fun has(mutator: RebirthMutator): Boolean = mutatorMask and (1 shl mutator.ordinal) != 0

    fun enemyCap(base: Int): Int = min(
        maximumActiveEnemies,
        max(base, floor(base.coerceAtLeast(0) * enemyCapMultiplier).toInt()),
    )

    fun spawnInterval(baseSeconds: Float): Float =
        max(minimumSpawnIntervalSeconds, baseSeconds / spawnRateMultiplier)

    fun eliteInterval(baseSeconds: Float): Float =
        max(minimumEliteIntervalSeconds, baseSeconds / eliteRateMultiplier)

    fun enemyHealth(base: Float): Float = base * enemyHealthMultiplier

    /**
     * A run level's Data requirement at this tier: `floor(base × dataRequirementScale + 0.5)`, never below [base]
     * and saturating at [Int.MAX_VALUE]. Monotone in [base], so a non-decreasing table stays non-decreasing.
     */
    fun dataRequirement(base: Int): Int {
        if (dataRequirementScale == 1f) return base
        val scaled = floor(base.toDouble() * dataRequirementScale.toDouble() + 0.5)
        return if (scaled >= Int.MAX_VALUE.toDouble()) Int.MAX_VALUE else max(base, scaled.toInt())
    }
}

/** Data requirement growth per Rebirth tier: tier t needs `1 + 0.14t` times the base table (Data supply grows alike). */
const val REBIRTH_DATA_REQUIREMENT_SCALE_PER_TIER: Float = 0.14f

/** Name of the tier without mutators. */
const val BASELINE_REBIRTH_NAME: String = "Baseline"

/** Short names of tiers 0..10; each later tier is named for the mutator it adds. */
val RebirthTierNames: List<String> = listOf(
    BASELINE_REBIRTH_NAME, "Pack", "Hardened", "Hunt", "Fuse", "Rift", "Overclocked", "Tide", "Armor", "Blackout", "Singularity",
)

/**
 * The shipped tuning of Rebirth tier [tier] (selected tier t). Hostile scaling grows faster than the
 * player's compensation, so each tier is a real step up: enemy integrity `1 + 0.32t + 0.05t²`,
 * incoming damage `1 + 0.14t + 0.008t²`, enemy speed `1 + 0.035t`, spawn rate `1 + 0.09t`, enemy cap
 * `1 + 0.10t`, Elite rate `1 + 0.07t`, threat `12t` s ahead, Matter `1 + 0.2t`, player power
 * `1 + 0.03t`, `t / 4` bonus rerolls, `5 + t` opening enemies, obstacle density `1 + 0.05t` and run-level Data
 * requirements `1 + 0.14t` ([REBIRTH_DATA_REQUIREMENT_SCALE_PER_TIER]). Tier
 * n also activates the first n [RebirthMutator]s and carries its short name ([RebirthTierNames]). Lives in the
 * API (like the Matrix and enemy rosters) so fixtures can mirror the catalog exactly.
 */
fun defaultRebirthProfile(
    tier: Int,
    maximumActiveEnemies: Int = 120,
    minimumSpawnIntervalSeconds: Float = 0.09f,
    minimumEliteIntervalSeconds: Float = 24f,
): RebirthProfile {
    val t = tier.toFloat()
    val mutators = RebirthMutator.entries.take(tier).toImmutableList()
    return RebirthProfile(
        tier = tier,
        displayName = RebirthTierNames.getOrElse(tier) { mutators.lastOrNull()?.displayName ?: BASELINE_REBIRTH_NAME },
        openingEnemyCount = 5 + tier,
        enemyCapMultiplier = 1f + t * 0.10f,
        spawnRateMultiplier = 1f + t * 0.09f,
        enemyHealthMultiplier = 1f + t * 0.32f + t * t * 0.05f,
        enemySpeedMultiplier = 1f + t * 0.035f,
        incomingDamageMultiplier = 1f + t * 0.14f + t * t * 0.008f,
        eliteRateMultiplier = 1f + t * 0.07f,
        threatTimeOffsetSeconds = t * 12f,
        playerPowerMultiplier = 1f + t * 0.03f,
        playerIntegrityBonus = 0f,
        matterGainMultiplier = 1f + t * 0.2f,
        bonusRerolls = tier / 4,
        maximumActiveEnemies = maximumActiveEnemies,
        minimumSpawnIntervalSeconds = minimumSpawnIntervalSeconds,
        minimumEliteIntervalSeconds = minimumEliteIntervalSeconds,
        mutators = mutators,
        packSizeBonus = if (RebirthMutator.PACK_HUNTERS in mutators) 1 else 0,
        obstacleDensityMultiplier = 1f + t * 0.05f,
        dataRequirementScale = 1f + t * REBIRTH_DATA_REQUIREMENT_SCALE_PER_TIER,
    )
}

/**
 * Content a Rebirth rank opens, derived from the content gates: weapons, characters (a Reborn-rank
 * unlock target), Relics, items and Core Matrix nodes become available at [rank], and the enemy types
 * gated at that tier join the roster when it is selected. Each list keeps catalog order.
 */
data class RebirthRankUnlocks(
    val rank: Int,
    val weapons: List<WeaponDefinition>,
    val characters: List<CoreShapeDefinition>,
    val relics: List<RelicDefinition>,
    val items: List<ItemDefinition>,
    val matrixNodes: List<MatrixNodeDefinition>,
    val enemies: List<EnemyDefinition>,
) {
    val isEmpty: Boolean
        get() = weapons.isEmpty() && characters.isEmpty() && relics.isEmpty() && items.isEmpty() &&
            matrixNodes.isEmpty() && enemies.isEmpty()
}

/** What reaching [rank] opens in these catalogs; rank 0 opens nothing (it is the start). */
fun rebirthRankUnlocks(
    rank: Int,
    weapons: List<WeaponDefinition>,
    coreShapes: List<CoreShapeDefinition>,
    relics: List<RelicDefinition>,
    items: List<ItemDefinition>,
    matrixNodes: List<MatrixNodeDefinition>,
    enemies: List<EnemyDefinition>,
): RebirthRankUnlocks {
    if (rank <= 0) return RebirthRankUnlocks(rank, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    return RebirthRankUnlocks(
        rank = rank,
        weapons = weapons.filter { it.minimumRebirth == rank },
        characters = coreShapes.filter {
            it.unlockRequirement == CharacterUnlockRequirement.REBIRTH_RANK && it.unlockTarget == rank
        },
        relics = relics.filter { it.minimumRebirth == rank },
        items = items.filter { it.minimumRebirth == rank },
        matrixNodes = matrixNodes.filter { it.minimumRebirth == rank },
        enemies = enemies.filter { it.minimumRebirth == rank },
    )
}
