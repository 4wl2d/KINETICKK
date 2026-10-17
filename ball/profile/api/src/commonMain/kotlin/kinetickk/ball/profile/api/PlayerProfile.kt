// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.common.localization.AppLanguage

/** Persistent ordinal order; append-only changes require an explicit save-format decision. */
enum class ParticleDensity { LOW, NORMAL, HIGH }

/** Persistent ordinal order; append-only changes require an explicit save-format decision. */
enum class DamageNumberSize(val scale: Float) {
    SMALL(0.8f),
    NORMAL(1f),
    LARGE(1.25f),
    HUGE(1.55f),
}

/** Persistent ordinal order; append-only changes require an explicit save-format decision. */
enum class DamageNumberFormat { COMPACT, FULL }

/**
 * Role-color remapping for color vision (you, threat, heat, shield, polarity). Persisted by stable
 * wire ids; DEFAULT is the absence of a stored value, so saves predating the setting stay canonical.
 */
enum class ColorVision { DEFAULT, PROTAN, DEUTAN, TRITAN, MONO }

const val DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD: Int = 50

val SIMULATION_SPEED_OPTIONS: ImmutableList<Float> = immutableListOf(0.75f, 1f, 1.15f, 1.35f, 1.6f, 2f)

val DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS: ImmutableList<Int> = immutableListOf(
    10,
    25,
    50,
    100,
    250,
    500,
    1_000,
    2_500,
    5_000,
    10_000,
    25_000,
    50_000,
    100_000,
    250_000,
    500_000,
    1_000_000,
    2_500_000,
    5_000_000,
    10_000_000,
    25_000_000,
    50_000_000,
    100_000_000,
)

data class PlayerPreferences(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val masterVolume: Float = 0.65f,
    val simulationSpeed: Float = 1f,
    val textScale: Float = 1.25f,
    val screenShake: Boolean = true,
    val particleDensity: ParticleDensity = ParticleDensity.NORMAL,
    val damageNumbers: Boolean = true,
    val damageNumberSize: DamageNumberSize = DamageNumberSize.NORMAL,
    val damageNumberFormat: DamageNumberFormat = DamageNumberFormat.COMPACT,
    val damageNumberTierThreshold: Int = DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
    val language: AppLanguage = AppLanguage.Russian,
    val runStatisticsOnLeft: Boolean = false,
    val colorVision: ColorVision = ColorVision.DEFAULT,
    /** Shows the run minimap; absent from saves while it is on. */
    val minimapEnabled: Boolean = true,
) {
    fun normalized(): PlayerPreferences = copy(
        masterVolume = masterVolume.coerceIn(0f, 1f),
        simulationSpeed = simulationSpeed.coerceIn(0.75f, 2f),
        textScale = textScale.coerceIn(1f, 1.75f),
        damageNumberTierThreshold = damageNumberTierThreshold.coerceIn(
            DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.first(),
            DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
        ),
    )
}

data class PlayerEconomy(
    val matter: Long = 0L,
    val lifetimeMatter: Long = matter,
)

data class PlayerLoadout(
    val coreShape: CoreShape = CoreShape.ORB,
    val selectedWeapon: WeaponId = WeaponId.FLUX_WAKE,
    val unlockedWeapons: ImmutableSet<WeaponId> = immutableSetOf(WeaponId.FLUX_WAKE),
) {
    constructor(
        coreShape: CoreShape = CoreShape.ORB,
        selectedWeapon: WeaponId = WeaponId.FLUX_WAKE,
        unlockedWeapons: Set<WeaponId>,
    ) : this(coreShape, selectedWeapon, unlockedWeapons.toImmutableSet())
}

/** Owned Core Matrix ranks indexed by [MatrixNodeId.ordinal]; a node at 0 is not owned. */
data class MatrixProgress(
    val ranks: ImmutableList<Int> = ImmutableList.copyOf(List(MatrixNodeId.entries.size) { 0 }),
) {
    constructor(ranks: List<Int>) : this(ranks.toImmutableList())

    fun rank(id: MatrixNodeId): Int = ranks.getOrElse(id.ordinal) { 0 }

    fun owns(id: MatrixNodeId): Boolean = rank(id) > 0

    /** Nodes with at least one rank. */
    val ownedNodeCount: Int get() = ranks.count { it > 0 }
}

/** Relics remembered as Heirlooms: at most one per Relic slot bound at the Architect victory. */
const val MAX_HEIRLOOM_RELICS: Int = 4

data class PlayerCollection(
    val discoveredItemIds: ImmutableSet<Int> = immutableSetOf(),
    val newItemIds: ImmutableSet<Int> = immutableSetOf(),
    val discoveredRelicIds: ImmutableSet<RelicId> = immutableSetOf(),
    val newRelicIds: ImmutableSet<RelicId> = immutableSetOf(),
    /** Element reactions the player has triggered at least once; the Codex reveals only these. */
    val discoveredReactionIds: ImmutableSet<ReactionId> = immutableSetOf(),
    /** Named Relic pairings the player has bound together at least once. */
    val discoveredPairingIds: ImmutableSet<RelicPairingId> = immutableSetOf(),
    /**
     * Relics bound at the last Architect victory, in slot order, at most [MAX_HEIRLOOM_RELICS] and
     * distinct. The next run offers them once; resolving that offer clears the list.
     */
    val heirloomRelicIds: ImmutableList<RelicId> = immutableListOf(),
) {
    constructor(
        discoveredItemIds: Set<Int>,
        newItemIds: Set<Int> = emptySet(),
        discoveredRelicIds: Set<RelicId> = emptySet(),
        newRelicIds: Set<RelicId> = emptySet(),
        discoveredReactionIds: Set<ReactionId> = emptySet(),
        discoveredPairingIds: Set<RelicPairingId> = emptySet(),
        heirloomRelicIds: List<RelicId> = emptyList(),
    ) : this(
        discoveredItemIds.toImmutableSet(), newItemIds.toImmutableSet(),
        discoveredRelicIds.toImmutableSet(), newRelicIds.toImmutableSet(),
        discoveredReactionIds.toImmutableSet(), discoveredPairingIds.toImmutableSet(),
        heirloomRelicIds.toImmutableList(),
    )
}

data class RebirthProgress(
    val level: Int = 0,
    val highestCleared: Int = -1,
)

/** Highest Rebirth tier this profile may select: one above the highest cleared tier, within content bounds. */
val RebirthProgress.rank: Int
    get() = (highestCleared + 1).coerceIn(ContentBounds.MIN_REBIRTH_LEVEL, ContentBounds.MAX_REBIRTH_LEVEL)

/** True once the final Rebirth tier has been cleared. */
val RebirthProgress.isAscendant: Boolean
    get() = highestCleared >= ContentBounds.MAX_REBIRTH_LEVEL

/** Cumulative Profile-owned achievements shared by every run and Matrix loadout. */
data class CharacterAchievementProgress(
    val eliteKills: Long = 0L,
    val dashHits: Long = 0L,
    val completedOrbits: Long = 0L,
    val architectVictories: Long = 0L,
    val victoriousCharacters: ImmutableSet<CoreShape> = immutableSetOf(),
    /** Enemies destroyed across every run. */
    val totalKills: Long = 0L,
    /** Hard obstacle bounces across every run. */
    val ricochets: Long = 0L,
    /** Data pickups collected across every run. */
    val dataPickups: Long = 0L,
)

data class PlayerProfile(
    val preferences: PlayerPreferences = PlayerPreferences(),
    val economy: PlayerEconomy = PlayerEconomy(),
    val loadout: PlayerLoadout = PlayerLoadout(),
    val matrix: MatrixProgress = MatrixProgress(),
    val collection: PlayerCollection = PlayerCollection(),
    val rebirthProgress: RebirthProgress = RebirthProgress(),
    val characterAchievements: CharacterAchievementProgress = CharacterAchievementProgress(),
)
