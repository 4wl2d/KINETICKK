// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl.storage

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
internal data class ProfileSnapshotDto(
    val revision: String,
    val profile: PlayerProfileDto,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class PlayerProfileDto(
    val preferences: PlayerPreferencesDto,
    val economy: PlayerEconomyDto,
    val loadout: PlayerLoadoutDto,
    // The retired Lab: only saves written before the Core Matrix carry it. Decoding refunds its ranks
    // (see RetiredLabRefund); encoding never writes it.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val labProgress: LabProgressDto? = null,
    // Owned Core Matrix ranks; absent when no node is owned, so a fresh profile keeps its bytes (and an
    // explicit empty list is not canonical).
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val matrix: MatrixProgressDto = MatrixProgressDto(emptyList()),
    val collection: PlayerCollectionDto,
    val rebirthProgress: RebirthProgressDto,
    val characterAchievements: CharacterAchievementProgressDto,
    // Bought Refit and Honing levels; absent while nothing is bought, so older saves keep their bytes
    // (and an explicit empty object is not canonical).
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val upgrades: PermanentUpgradesDto = PermanentUpgradesDto(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class PlayerPreferencesDto(
    val soundEnabled: Boolean,
    val musicEnabled: Boolean,
    val masterVolumePercent: Int,
    val simulationSpeedPercent: Int,
    val textScalePercent: Int,
    val screenShake: Boolean,
    val particleDensityId: String,
    val damageNumbers: Boolean,
    val damageNumberSizeId: String,
    val damageNumberFormatId: String,
    val damageNumberTierThreshold: Int,
    // Absence is accepted only for saves created before language selection was introduced.
    // Omitting this sentinel during canonical re-encoding preserves validation of those saves.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val languageCode: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val runStatisticsOnLeft: Boolean? = null,
    // Written only for a non-default choice: absence means ColorVision.DEFAULT, so every save that
    // predates the setting (and every default profile) keeps its exact canonical bytes.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val colorVisionId: String? = null,
    // Written only when the minimap is off: absence means on, so older saves keep their canonical bytes.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val minimapEnabled: Boolean? = null,
    // Display and controls follow the same rule: each field is written only for a non-default value
    // (booleans only as their non-default value), so older saves keep their bytes, and an explicit
    // default is refused as a value this codec never produces.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val windowModeId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val windowSizeId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val vsyncEnabled: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val frameRateCap: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val refreshRateHz: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val immersiveEnabled: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val interfaceScalePercent: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val showFps: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pauseOnFocusLoss: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val reactionEffectsId: String? = null,
    // Rebound actions only, strictly sorted by action wire id; absent while every key is the default.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val keyBindings: List<KeyBindingDto> = emptyList(),
)

@Serializable
internal data class KeyBindingDto(
    val action: String,
    val key: String,
)

@Serializable
internal data class PlayerEconomyDto(
    val matter: String,
    val lifetimeMatter: String,
)

@Serializable
internal data class PlayerLoadoutDto(
    val coreShapeId: String,
    val selectedWeaponId: String,
    val unlockedWeaponIds: List<String>,
)

@Serializable
internal data class LabProgressDto(
    val ranks: List<MetaUpgradeRankDto>,
)

@Serializable
internal data class MetaUpgradeRankDto(
    val id: String,
    val rank: Int,
)

@Serializable
internal data class MatrixProgressDto(
    val ranks: List<MatrixNodeRankDto>,
)

@Serializable
internal data class MatrixNodeRankDto(
    val id: String,
    val rank: Int,
)

/**
 * Bought levels as positional base-36 digits, one per track: [c] holds the Refit levels in
 * [CHARACTER_SLOTS] order and [w] the Honing levels in [WEAPON_UPGRADE_SLOTS] order. Trailing
 * zero levels are dropped, so a track with nothing bought is absent and appending a character or a
 * weapon keeps every older save canonical. Positional digits keep the worst case (36 tracks at the
 * maximum) within the desktop Preferences value budget, which `{id, level}` records would exceed.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class PermanentUpgradesDto(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val c: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val w: String = "",
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class PlayerCollectionDto(
    // Generation of the item and Relic catalog the ids below refer to. Every encode writes
    // ContentBounds.COLLECTION_GENERATION; absence marks a save of the retired catalog, which keeps
    // its exact canonical bytes and whose discoveries are discarded when mapped to the domain.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val itemCatalogEpoch: Int? = null,
    val discoveredItemIds: List<Int>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val newItemIds: List<Int> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val discoveredRelicIds: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val newRelicIds: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val discoveredReactionIds: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val discoveredPairingIds: List<String> = emptyList(),
    // Slot order at the Architect victory, so unique but deliberately not sorted.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val heirloomRelicIds: List<String> = emptyList(),
    // Recent first acquisitions, oldest first with repeats, so deliberately neither sorted nor unique;
    // written only when non-empty, so older saves keep their bytes.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val recentItemIds: List<Int> = emptyList(),
    // World discoveries as bits of WorldDiscovery.ordinal; written only when non-zero, so older saves keep their bytes.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val worldDiscoveryMask: Int = 0,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class RebirthProgressDto(
    val level: Int,
    val highestCleared: Int,
    // Characters that cleared the final tier, sorted by wire id; written only once one has, so older
    // saves keep their exact bytes.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val ascendedCharacterIds: List<String> = emptyList(),
    // Each character's highest clear as one positional base-36 digit in CHARACTER_SLOTS order: the
    // tier plus one, so 0 is none. Trailing zeros are dropped, so it is absent until a character
    // records a clear and older saves keep their exact bytes.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val characterClears: String = "",
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class CharacterAchievementProgressDto(
    val eliteKills: String,
    val dashHits: String,
    val completedOrbits: String,
    val architectVictories: String,
    val victoriousCharacterIds: List<String>,
    // Written only when non-zero: absence means 0, so saves from before these counters keep their bytes
    // (and an explicit "0" is not canonical).
    @EncodeDefault(EncodeDefault.Mode.NEVER) val totalKills: String = "0",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val ricochets: String = "0",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dataPickups: String = "0",
)
