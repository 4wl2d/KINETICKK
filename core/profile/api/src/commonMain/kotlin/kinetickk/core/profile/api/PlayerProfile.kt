// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.profile.api

import kinetickk.core.collections.ImmutableList
import kinetickk.core.collections.ImmutableSet
import kinetickk.core.collections.immutableListOf
import kinetickk.core.collections.immutableSetOf
import kinetickk.core.collections.toImmutableList
import kinetickk.core.collections.toImmutableSet
import kinetickk.core.content.CoreShape
import kinetickk.core.content.MetaUpgradeId
import kinetickk.core.content.WeaponId

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

const val DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD: Int = 50

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
    val simulationSpeed: Float = 1.15f,
    val textScale: Float = 1.25f,
    val screenShake: Boolean = true,
    val particleDensity: ParticleDensity = ParticleDensity.NORMAL,
    val damageNumbers: Boolean = true,
    val damageNumberSize: DamageNumberSize = DamageNumberSize.NORMAL,
    val damageNumberFormat: DamageNumberFormat = DamageNumberFormat.COMPACT,
    val damageNumberTierThreshold: Int = DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
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

data class LabProgress(
    val ranks: ImmutableList<Int> = ImmutableList.copyOf(List(MetaUpgradeId.entries.size) { 0 }),
) {
    constructor(ranks: List<Int>) : this(ranks.toImmutableList())

    fun rank(id: MetaUpgradeId): Int = ranks.getOrElse(id.ordinal) { 0 }
}

data class PlayerCollection(
    val discoveredItemIds: ImmutableSet<Int> = immutableSetOf(),
) {
    constructor(discoveredItemIds: Set<Int>) : this(discoveredItemIds.toImmutableSet())
}

data class RebirthProgress(
    val level: Int = 0,
    val highestCleared: Int = -1,
)

data class PlayerProfile(
    val preferences: PlayerPreferences = PlayerPreferences(),
    val economy: PlayerEconomy = PlayerEconomy(),
    val loadout: PlayerLoadout = PlayerLoadout(),
    val labProgress: LabProgress = LabProgress(),
    val collection: PlayerCollection = PlayerCollection(),
    val rebirthProgress: RebirthProgress = RebirthProgress(),
)
