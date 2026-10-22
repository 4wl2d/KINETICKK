// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.ball.profile.api.CharacterAchievementProgress
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.isWithinRebirthBounds
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.foundation.common.localization.AppLanguage
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

internal const val MAX_PROFILE_PAYLOAD_BYTES: Int = 65_536

internal sealed interface ProfileEncodeResult {
    data class Encoded(val payload: String) : ProfileEncodeResult
    data class Rejected(val reason: ProfileSnapshotRejection) : ProfileEncodeResult
}

internal sealed interface ProfileDecodeResult {
    data class Decoded(val snapshot: ProfileSnapshot) : ProfileDecodeResult
    data class Rejected(val reason: ProfileSnapshotRejection) : ProfileDecodeResult
}

/** Strict, canonical codec; profiles predating language selection retain the Russian default. */
internal object ProfileCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        allowSpecialFloatingPointValues = false
        prettyPrint = false
    }

    fun encode(snapshot: ProfileSnapshot): ProfileEncodeResult {
        val dto = try {
            snapshot.toDto()
        } catch (rejection: ProfileCodecRejection) {
            return ProfileEncodeResult.Rejected(rejection.reason)
        }
        val payload = try {
            json.encodeToString(dto)
        } catch (_: SerializationException) {
            return ProfileEncodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        } catch (_: IllegalArgumentException) {
            return ProfileEncodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        }
        return when (payload.utf8Validation()) {
            Utf8Validation.Accepted -> ProfileEncodeResult.Encoded(payload)
            Utf8Validation.TooLarge -> ProfileEncodeResult.Rejected(ProfileSnapshotRejection.PAYLOAD_TOO_LARGE)
            Utf8Validation.Invalid -> ProfileEncodeResult.Rejected(ProfileSnapshotRejection.INVALID_UTF8)
        }
    }

    fun decode(payload: String): ProfileDecodeResult {
        when (payload.utf8Validation()) {
            Utf8Validation.TooLarge -> {
                return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.PAYLOAD_TOO_LARGE)
            }
            Utf8Validation.Invalid -> {
                return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.INVALID_UTF8)
            }
            Utf8Validation.Accepted -> Unit
        }

        val dto = try {
            json.decodeFromString<ProfileSnapshotDto>(payload)
        } catch (_: SerializationException) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        } catch (_: IllegalArgumentException) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        }

        val canonicalPayload = try {
            json.encodeToString(dto)
        } catch (_: SerializationException) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        } catch (_: IllegalArgumentException) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        }
        if (canonicalPayload != payload) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD)
        }

        return try {
            ProfileDecodeResult.Decoded(dto.toSnapshot())
        } catch (rejection: ProfileCodecRejection) {
            ProfileDecodeResult.Rejected(rejection.reason)
        }
    }
}

@Serializable
private data class ProfileSnapshotDto(
    val revision: String,
    val profile: PlayerProfileDto,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class PlayerProfileDto(
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
private data class PlayerPreferencesDto(
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
)

@Serializable
private data class PlayerEconomyDto(
    val matter: String,
    val lifetimeMatter: String,
)

@Serializable
private data class PlayerLoadoutDto(
    val coreShapeId: String,
    val selectedWeaponId: String,
    val unlockedWeaponIds: List<String>,
)

@Serializable
private data class LabProgressDto(
    val ranks: List<MetaUpgradeRankDto>,
)

@Serializable
private data class MetaUpgradeRankDto(
    val id: String,
    val rank: Int,
)

@Serializable
private data class MatrixProgressDto(
    val ranks: List<MatrixNodeRankDto>,
)

@Serializable
private data class MatrixNodeRankDto(
    val id: String,
    val rank: Int,
)

/** Non-zero levels only, each list strictly sorted by wire id and absent while empty. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class PermanentUpgradesDto(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val characters: List<UpgradeLevelDto> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val weapons: List<UpgradeLevelDto> = emptyList(),
)

@Serializable
private data class UpgradeLevelDto(
    val id: String,
    val level: Int,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class PlayerCollectionDto(
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
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class RebirthProgressDto(
    val level: Int,
    val highestCleared: Int,
    // Characters that cleared the final tier, sorted by wire id; written only once one has, so older
    // saves keep their exact bytes.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val ascendedCharacterIds: List<String> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class CharacterAchievementProgressDto(
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

private fun ProfileSnapshot.toDto(): ProfileSnapshotDto {
    validateProfile(profile)
    rejectUnless(revision.value >= 0L, ProfileSnapshotRejection.INVALID_DECIMAL)

    val preferences = profile.preferences
    val matrixRanks = MatrixNodeId.entries
        .filter { id -> profile.matrix.rank(id) > 0 }
        .map { id -> MatrixNodeRankDto(id = id.wireId(), rank = profile.matrix.rank(id)) }
        .sortedBy(MatrixNodeRankDto::id)

    return ProfileSnapshotDto(
        revision = revision.value.toString(),
        profile = PlayerProfileDto(
            preferences = PlayerPreferencesDto(
                soundEnabled = preferences.soundEnabled,
                musicEnabled = preferences.musicEnabled,
                masterVolumePercent = preferences.masterVolume.toPercent(),
                simulationSpeedPercent = preferences.simulationSpeed.toPercent(),
                textScalePercent = preferences.textScale.toPercent(),
                screenShake = preferences.screenShake,
                particleDensityId = preferences.particleDensity.wireId(),
                damageNumbers = preferences.damageNumbers,
                damageNumberSizeId = preferences.damageNumberSize.wireId(),
                damageNumberFormatId = preferences.damageNumberFormat.wireId(),
                damageNumberTierThreshold = preferences.damageNumberTierThreshold,
                languageCode = preferences.language.code,
                runStatisticsOnLeft = preferences.runStatisticsOnLeft,
                colorVisionId = preferences.colorVision.wireIdOrNull(),
                minimapEnabled = if (preferences.minimapEnabled) null else false,
            ),
            economy = PlayerEconomyDto(
                matter = profile.economy.matter.toString(),
                lifetimeMatter = profile.economy.lifetimeMatter.toString(),
            ),
            loadout = PlayerLoadoutDto(
                coreShapeId = profile.loadout.coreShape.wireId(),
                selectedWeaponId = profile.loadout.selectedWeapon.wireId(),
                unlockedWeaponIds = profile.loadout.unlockedWeapons
                    .map(WeaponId::wireId)
                    .sorted(),
            ),
            matrix = MatrixProgressDto(matrixRanks),
            collection = PlayerCollectionDto(
                itemCatalogEpoch = ContentBounds.COLLECTION_GENERATION,
                discoveredItemIds = profile.collection.discoveredItemIds.sorted(),
                newItemIds = profile.collection.newItemIds.sorted(),
                discoveredRelicIds = profile.collection.discoveredRelicIds.map(RelicId::wireId).sorted(),
                newRelicIds = profile.collection.newRelicIds.map(RelicId::wireId).sorted(),
                discoveredReactionIds = profile.collection.discoveredReactionIds.map(ReactionId::wireId).sorted(),
                discoveredPairingIds = profile.collection.discoveredPairingIds.map(RelicPairingId::wireId).sorted(),
                heirloomRelicIds = profile.collection.heirloomRelicIds.map(RelicId::wireId),
            ),
            characterAchievements = CharacterAchievementProgressDto(
                eliteKills = profile.characterAchievements.eliteKills.toString(),
                dashHits = profile.characterAchievements.dashHits.toString(),
                completedOrbits = profile.characterAchievements.completedOrbits.toString(),
                architectVictories = profile.characterAchievements.architectVictories.toString(),
                victoriousCharacterIds = profile.characterAchievements.victoriousCharacters.map(CoreShape::wireId).sorted(),
                totalKills = profile.characterAchievements.totalKills.toString(),
                ricochets = profile.characterAchievements.ricochets.toString(),
                dataPickups = profile.characterAchievements.dataPickups.toString(),
            ),
            rebirthProgress = RebirthProgressDto(
                level = profile.rebirthProgress.level,
                highestCleared = profile.rebirthProgress.highestCleared,
                ascendedCharacterIds = profile.rebirthProgress.ascendedCharacters.map(CoreShape::wireId).sorted(),
            ),
            upgrades = PermanentUpgradesDto(
                characters = CoreShape.entries
                    .filter { shape -> profile.upgrades.characterLevel(shape) > 0 }
                    .map { shape -> UpgradeLevelDto(id = shape.wireId(), level = profile.upgrades.characterLevel(shape)) }
                    .sortedBy(UpgradeLevelDto::id),
                weapons = WeaponId.entries
                    .filter { id -> profile.upgrades.weaponLevel(id) > 0 }
                    .map { id -> UpgradeLevelDto(id = id.wireId(), level = profile.upgrades.weaponLevel(id)) }
                    .sortedBy(UpgradeLevelDto::id),
            ),
        ),
    )
}

private fun ProfileSnapshotDto.toSnapshot(): ProfileSnapshot {
    val revisionValue = revision.parseCanonicalNonNegativeLong()
    val retiredCatalog = profile.collection.isFromRetiredCatalog()

    val legacyLab = profile.labProgress
    rejectUnless(legacyLab == null || profile.matrix.ranks.isEmpty(), ProfileSnapshotRejection.INCONSISTENT_PROFILE)
    // Upgrades were introduced long after the Lab was retired, so no save carries both.
    rejectUnless(legacyLab == null || profile.upgrades == PermanentUpgradesDto(), ProfileSnapshotRejection.INCONSISTENT_PROFILE)
    val labRefund = legacyLab?.let(RetiredLabRefund::refund) ?: 0L
    val matrixRanks = profile.matrix.toRanks()
    val upgrades = profile.upgrades.toUpgrades()
    rejectUnless(
        profile.loadout.unlockedWeaponIds.isStrictlySorted(),
        ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
    )
    rejectUnless(
        profile.collection.discoveredItemIds.isStrictlySorted(),
        ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
    )

    rejectUnless(
        profile.characterAchievements.victoriousCharacterIds.isStrictlySorted() &&
            profile.rebirthProgress.ascendedCharacterIds.isStrictlySorted(),
        ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
    )

    rejectUnless(
        profile.collection.newItemIds.isStrictlySorted() &&
            profile.collection.discoveredRelicIds.isStrictlySorted() &&
            profile.collection.newRelicIds.isStrictlySorted() &&
            profile.collection.discoveredReactionIds.isStrictlySorted() &&
            profile.collection.discoveredPairingIds.isStrictlySorted() &&
            profile.collection.heirloomRelicIds.toSet().size == profile.collection.heirloomRelicIds.size,
        ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
    )

    val storedMatter = profile.economy.matter.parseCanonicalNonNegativeLong()
    val storedLifetimeMatter = profile.economy.lifetimeMatter.parseCanonicalNonNegativeLong()
    rejectUnless(storedLifetimeMatter >= storedMatter, ProfileSnapshotRejection.INCONSISTENT_PROFILE)
    // A Lab refund returns the whole spend; lifetime Matter never falls below the balance it produced.
    val matter = saturatedAdd(storedMatter, labRefund)
    val lifetimeMatter = maxOf(storedLifetimeMatter, matter)

    val decoded = PlayerProfile(
        preferences = PlayerPreferences(
            soundEnabled = profile.preferences.soundEnabled,
            musicEnabled = profile.preferences.musicEnabled,
            masterVolume = profile.preferences.masterVolumePercent / 100f,
            simulationSpeed = profile.preferences.simulationSpeedPercent / 100f,
            textScale = profile.preferences.textScalePercent / 100f,
            screenShake = profile.preferences.screenShake,
            particleDensity = profile.preferences.particleDensityId.particleDensity(),
            damageNumbers = profile.preferences.damageNumbers,
            damageNumberSize = profile.preferences.damageNumberSizeId.damageNumberSize(),
            damageNumberFormat = profile.preferences.damageNumberFormatId.damageNumberFormat(),
            damageNumberTierThreshold = profile.preferences.damageNumberTierThreshold,
            language = profile.preferences.languageCode?.appLanguage() ?: AppLanguage.Russian,
            runStatisticsOnLeft = profile.preferences.runStatisticsOnLeft ?: false,
            colorVision = profile.preferences.colorVisionId?.colorVision() ?: ColorVision.DEFAULT,
            // Only "off" is ever written; an explicit default is not a value this codec produces.
            minimapEnabled = when (profile.preferences.minimapEnabled) {
                null -> true
                false -> false
                true -> reject(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
            },
        ),
        economy = PlayerEconomy(matter = matter, lifetimeMatter = lifetimeMatter),
        loadout = PlayerLoadout(
            coreShape = profile.loadout.coreShapeId.coreShape(),
            selectedWeapon = profile.loadout.selectedWeaponId.weaponId(),
            unlockedWeapons = profile.loadout.unlockedWeaponIds.mapTo(mutableSetOf()) { it.weaponId() },
        ),
        matrix = MatrixProgress(matrixRanks),
        collection = if (retiredCatalog) PlayerCollection() else profile.collection.toCollection(),
        characterAchievements = CharacterAchievementProgress(
            eliteKills = profile.characterAchievements.eliteKills.parseCanonicalNonNegativeLong(),
            dashHits = profile.characterAchievements.dashHits.parseCanonicalNonNegativeLong(),
            completedOrbits = profile.characterAchievements.completedOrbits.parseCanonicalNonNegativeLong(),
            architectVictories = profile.characterAchievements.architectVictories.parseCanonicalNonNegativeLong(),
            victoriousCharacters = profile.characterAchievements.victoriousCharacterIds.map { it.coreShape() }.toImmutableSet(),
            totalKills = profile.characterAchievements.totalKills.parseCanonicalNonNegativeLong(),
            ricochets = profile.characterAchievements.ricochets.parseCanonicalNonNegativeLong(),
            dataPickups = profile.characterAchievements.dataPickups.parseCanonicalNonNegativeLong(),
        ),
        rebirthProgress = RebirthProgress(
            level = profile.rebirthProgress.level,
            highestCleared = profile.rebirthProgress.highestCleared,
            ascendedCharacters = profile.rebirthProgress.ascendedCharacterIds.map { it.coreShape() }.toImmutableSet(),
        ),
        upgrades = upgrades,
    )
    validateProfile(decoded)

    return ProfileSnapshot(
        revision = ProfileRevision(revisionValue),
        profile = decoded,
    )
}

/**
 * Validates the epoch and the raw list bounds of either catalog generation before any id is mapped.
 * Returns true for a save of the retired catalog, whose discoveries are dropped unparsed: its Relic
 * names no longer exist and its item indices point into a different catalog.
 */
private fun PlayerCollectionDto.isFromRetiredCatalog(): Boolean {
    val epoch = itemCatalogEpoch
    rejectUnless(
        epoch == null || epoch in 0..ContentBounds.COLLECTION_GENERATION,
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    val retired = epoch == null || epoch < ContentBounds.COLLECTION_GENERATION
    val maxItems = if (retired) ContentBounds.LEGACY_MAX_ITEMS else ContentBounds.MAX_ITEMS
    val maxRelics = if (retired) ContentBounds.LEGACY_MAX_RELICS else ContentBounds.MAX_RELICS
    rejectUnless(
        discoveredItemIds.size <= maxItems && newItemIds.size <= maxItems &&
            discoveredRelicIds.size <= maxRelics && newRelicIds.size <= maxRelics &&
            discoveredReactionIds.size <= ReactionId.entries.size &&
            discoveredPairingIds.size <= RelicPairingId.entries.size &&
            heirloomRelicIds.size <= MAX_HEIRLOOM_RELICS,
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    // The retired codec never wrote these fields, so a save carrying them without an epoch was not
    // produced by any version.
    rejectUnless(
        !retired || discoveredReactionIds.isEmpty() && discoveredPairingIds.isEmpty() && heirloomRelicIds.isEmpty(),
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    return retired
}

private fun PlayerCollectionDto.toCollection(): PlayerCollection = PlayerCollection(
    discoveredItemIds = discoveredItemIds.toImmutableSet(),
    newItemIds = newItemIds.toImmutableSet(),
    discoveredRelicIds = discoveredRelicIds.map { it.relicId() }.toImmutableSet(),
    newRelicIds = newRelicIds.map { it.relicId() }.toImmutableSet(),
    discoveredReactionIds = discoveredReactionIds.map { it.reactionId() }.toImmutableSet(),
    discoveredPairingIds = discoveredPairingIds.map { it.relicPairingId() }.toImmutableSet(),
    heirloomRelicIds = heirloomRelicIds.map { it.relicId() }.toImmutableList(),
)

private fun validateProfile(profile: PlayerProfile) {
    val achievements = profile.characterAchievements
    rejectUnless(
        achievements.eliteKills >= 0L && achievements.dashHits >= 0L &&
            achievements.completedOrbits >= 0L && achievements.architectVictories >= 0L &&
            achievements.totalKills >= 0L && achievements.ricochets >= 0L && achievements.dataPickups >= 0L &&
            achievements.victoriousCharacters.size <= CoreShape.entries.size &&
            achievements.victoriousCharacters.size.toLong() <= achievements.architectVictories,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    val preferences = profile.preferences
    rejectUnless(
        preferences.masterVolume.isFinite() && preferences.masterVolume in 0f..1f &&
            preferences.simulationSpeed.isFinite() && preferences.simulationSpeed in 0.75f..2f &&
            preferences.simulationSpeed in SIMULATION_SPEED_OPTIONS &&
            preferences.textScale.isFinite() && preferences.textScale in 1f..1.75f &&
            preferences.damageNumberTierThreshold in DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS,
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.economy.matter >= 0L &&
            profile.economy.lifetimeMatter >= profile.economy.matter,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )

    rejectUnless(
        profile.loadout.unlockedWeapons.isNotEmpty() &&
            profile.loadout.unlockedWeapons.size <= ContentBounds.MAX_WEAPONS,
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.loadout.selectedWeapon in profile.loadout.unlockedWeapons,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )

    rejectUnless(
        profile.matrix.ranks.size == MatrixNodeId.entries.size &&
            profile.matrix.ranks.size <= ContentBounds.MAX_MATRIX_NODES,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    rejectUnless(
        profile.matrix.ranks.all { it in 0..ContentBounds.MAX_MATRIX_NODE_RANKS },
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )

    rejectUnless(
        profile.collection.discoveredItemIds.size <= ContentBounds.MAX_ITEMS &&
            profile.collection.discoveredItemIds.all { it >= 0 },
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.collection.discoveredItemIds.containsAll(profile.collection.newItemIds) &&
            profile.collection.discoveredRelicIds.containsAll(profile.collection.newRelicIds) &&
            profile.collection.discoveredRelicIds.size <= ContentBounds.MAX_RELICS,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    val heirlooms = profile.collection.heirloomRelicIds
    rejectUnless(heirlooms.size <= MAX_HEIRLOOM_RELICS, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    rejectUnless(heirlooms.toSet().size == heirlooms.size, ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE)
    rejectUnless(
        profile.rebirthProgress.isWithinRebirthBounds(ContentBounds.MIN_REBIRTH_LEVEL, ContentBounds.MAX_REBIRTH_LEVEL),
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    // An Ascendant clear is an Architect victory with that character.
    rejectUnless(
        achievements.victoriousCharacters.containsAll(profile.rebirthProgress.ascendedCharacters),
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    val upgrades = profile.upgrades
    rejectUnless(
        upgrades.characterLevels.size == CoreShape.entries.size && upgrades.weaponLevels.size == WeaponId.entries.size,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    rejectUnless(
        upgrades.characterLevels.all { it in 0..ContentBounds.MAX_UPGRADE_LEVEL } &&
            upgrades.weaponLevels.all { it in 0..ContentBounds.MAX_UPGRADE_LEVEL },
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
}

private fun <T : Comparable<T>> List<T>.isStrictlySorted(): Boolean {
    for (index in 1 until size) {
        if (this[index - 1] >= this[index]) return false
    }
    return true
}

private fun Float.toPercent(): Int {
    rejectUnless(isFinite(), ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    return (this * 100f).roundToInt()
}

private fun String.parseCanonicalNonNegativeLong(): Long {
    if (isEmpty() || this != "0" && (first() == '0' || any { it !in '0'..'9' })) {
        reject(ProfileSnapshotRejection.INVALID_DECIMAL)
    }
    if (this == "0") return 0L
    if (any { it !in '0'..'9' }) reject(ProfileSnapshotRejection.INVALID_DECIMAL)
    val value = toLongOrNull() ?: reject(ProfileSnapshotRejection.INVALID_DECIMAL)
    rejectUnless(value >= 0L && value.toString() == this, ProfileSnapshotRejection.INVALID_DECIMAL)
    return value
}

private fun CoreShape.wireId(): String = when (this) {
    CoreShape.ORB -> "ORB"
    CoreShape.PRISM -> "PRISM"
    CoreShape.SHARD -> "SHARD"
    CoreShape.RING -> "RING"
    CoreShape.DIAMOND -> "DIAMOND"
    CoreShape.TESSERACT -> "TESSERACT"
    CoreShape.STAR -> "STAR"
    CoreShape.SPIRAL -> "SPIRAL"
    CoreShape.DIPOLE -> "DIPOLE"
    CoreShape.PENTAGON -> "PENTAGON"
    CoreShape.CRESCENT -> "CRESCENT"
    CoreShape.HEXAGON -> "HEXAGON"
}

private fun String.coreShape(): CoreShape = when (this) {
    "ORB" -> CoreShape.ORB
    "PRISM" -> CoreShape.PRISM
    "SHARD" -> CoreShape.SHARD
    "RING" -> CoreShape.RING
    "DIAMOND" -> CoreShape.DIAMOND
    "TESSERACT" -> CoreShape.TESSERACT
    "STAR" -> CoreShape.STAR
    "SPIRAL" -> CoreShape.SPIRAL
    "DIPOLE" -> CoreShape.DIPOLE
    "PENTAGON" -> CoreShape.PENTAGON
    "CRESCENT" -> CoreShape.CRESCENT
    "HEXAGON" -> CoreShape.HEXAGON
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

private fun WeaponId.wireId(): String = when (this) {
    WeaponId.FLUX_WAKE -> "FLUX_WAKE"
    WeaponId.MORNINGSTAR -> "MORNINGSTAR"
    WeaponId.PHASE_LATTICE -> "PHASE_LATTICE"
    WeaponId.NULL_LANCE -> "NULL_LANCE"
    WeaponId.GRAVITY_MINES -> "GRAVITY_MINES"
    WeaponId.ION_SWARM -> "ION_SWARM"
    WeaponId.RIFT_BLADES -> "RIFT_BLADES"
    WeaponId.ARC_COIL -> "ARC_COIL"
    WeaponId.QUASAR_CANNON -> "QUASAR_CANNON"
    WeaponId.ENTROPY_FIELD -> "ENTROPY_FIELD"
    WeaponId.SINGULARITY_SPEAR -> "SINGULARITY_SPEAR"
    WeaponId.PRISM_RELAY -> "PRISM_RELAY"
    WeaponId.GYRE_DISC -> "GYRE_DISC"
    WeaponId.SCATTER_BLOOM -> "SCATTER_BLOOM"
    WeaponId.TETHER_ARC -> "TETHER_ARC"
    WeaponId.SENTRY_GRID -> "SENTRY_GRID"
    WeaponId.HUNTER_SALVO -> "HUNTER_SALVO"
    WeaponId.CONCUSSION_ENGINE -> "CONCUSSION_ENGINE"
    WeaponId.GLACIER_BELL -> "GLACIER_BELL"
    WeaponId.EVENT_LENS -> "EVENT_LENS"
    WeaponId.SOLAR_SWEEP -> "SOLAR_SWEEP"
    WeaponId.AEGIS_SHARDS -> "AEGIS_SHARDS"
    WeaponId.HOLLOW_RIFT -> "HOLLOW_RIFT"
    WeaponId.RESONANCE_DRUM -> "RESONANCE_DRUM"
}

private fun String.weaponId(): WeaponId = when (this) {
    "FLUX_WAKE" -> WeaponId.FLUX_WAKE
    "MORNINGSTAR" -> WeaponId.MORNINGSTAR
    "PHASE_LATTICE" -> WeaponId.PHASE_LATTICE
    "NULL_LANCE" -> WeaponId.NULL_LANCE
    "GRAVITY_MINES" -> WeaponId.GRAVITY_MINES
    "ION_SWARM" -> WeaponId.ION_SWARM
    "RIFT_BLADES" -> WeaponId.RIFT_BLADES
    "ARC_COIL" -> WeaponId.ARC_COIL
    "QUASAR_CANNON" -> WeaponId.QUASAR_CANNON
    "ENTROPY_FIELD" -> WeaponId.ENTROPY_FIELD
    "SINGULARITY_SPEAR" -> WeaponId.SINGULARITY_SPEAR
    "PRISM_RELAY" -> WeaponId.PRISM_RELAY
    "GYRE_DISC" -> WeaponId.GYRE_DISC
    "SCATTER_BLOOM" -> WeaponId.SCATTER_BLOOM
    "TETHER_ARC" -> WeaponId.TETHER_ARC
    "SENTRY_GRID" -> WeaponId.SENTRY_GRID
    "HUNTER_SALVO" -> WeaponId.HUNTER_SALVO
    "CONCUSSION_ENGINE" -> WeaponId.CONCUSSION_ENGINE
    "GLACIER_BELL" -> WeaponId.GLACIER_BELL
    "EVENT_LENS" -> WeaponId.EVENT_LENS
    "SOLAR_SWEEP" -> WeaponId.SOLAR_SWEEP
    "AEGIS_SHARDS" -> WeaponId.AEGIS_SHARDS
    "HOLLOW_RIFT" -> WeaponId.HOLLOW_RIFT
    "RESONANCE_DRUM" -> WeaponId.RESONANCE_DRUM
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

/** Core Matrix wire ids: stable names, never renamed or reused. */
private fun MatrixNodeId.wireId(): String = when (this) {
    MatrixNodeId.IMPACT_DRIVER -> "IMPACT_DRIVER"
    MatrixNodeId.WEAPON_CALIBRATION -> "WEAPON_CALIBRATION"
    MatrixNodeId.CRITICAL_LATTICE -> "CRITICAL_LATTICE"
    MatrixNodeId.ARMED_START -> "ARMED_START"
    MatrixNodeId.CATALYST_CORE -> "CATALYST_CORE"
    MatrixNodeId.OVERDRIVE_COIL -> "OVERDRIVE_COIL"
    MatrixNodeId.REBORN_ARSENAL -> "REBORN_ARSENAL"
    MatrixNodeId.GLASS_ENGINE -> "GLASS_ENGINE"
    MatrixNodeId.REINFORCED_HULL -> "REINFORCED_HULL"
    MatrixNodeId.HARDLIGHT_RESERVE -> "HARDLIGHT_RESERVE"
    MatrixNodeId.IMPACT_DAMPERS -> "IMPACT_DAMPERS"
    MatrixNodeId.SECOND_WIND -> "SECOND_WIND"
    MatrixNodeId.NANOFORGE -> "NANOFORGE"
    MatrixNodeId.BASTION_PROTOCOL -> "BASTION_PROTOCOL"
    MatrixNodeId.AEGIS_LATTICE -> "AEGIS_LATTICE"
    MatrixNodeId.JUGGERNAUT -> "JUGGERNAUT"
    MatrixNodeId.MAGNET_ARRAY -> "MAGNET_ARRAY"
    MatrixNodeId.CRYO_LOOP -> "CRYO_LOOP"
    MatrixNodeId.VECTOR_THRUSTERS -> "VECTOR_THRUSTERS"
    MatrixNodeId.NEAR_MISS -> "NEAR_MISS"
    MatrixNodeId.RICOCHET_RIG -> "RICOCHET_RIG"
    MatrixNodeId.OVERCLOCKED_REFLEXES -> "OVERCLOCKED_REFLEXES"
    MatrixNodeId.GRAVITY_SURFER -> "GRAVITY_SURFER"
    MatrixNodeId.PERPETUAL_DRIFT -> "PERPETUAL_DRIFT"
    MatrixNodeId.SALVAGE_PROTOCOL -> "SALVAGE_PROTOCOL"
    MatrixNodeId.DATA_ARCHIVE -> "DATA_ARCHIVE"
    MatrixNodeId.REROLL_CACHE -> "REROLL_CACHE"
    MatrixNodeId.WIDE_OFFER -> "WIDE_OFFER"
    MatrixNodeId.LUCKY_CHARM -> "LUCKY_CHARM"
    MatrixNodeId.ELITE_BOUNTY -> "ELITE_BOUNTY"
    MatrixNodeId.HEIRLOOM_VAULT -> "HEIRLOOM_VAULT"
    MatrixNodeId.PROSPECTOR -> "PROSPECTOR"
}

private fun String.matrixNodeId(): MatrixNodeId = when (this) {
    "IMPACT_DRIVER" -> MatrixNodeId.IMPACT_DRIVER
    "WEAPON_CALIBRATION" -> MatrixNodeId.WEAPON_CALIBRATION
    "CRITICAL_LATTICE" -> MatrixNodeId.CRITICAL_LATTICE
    "ARMED_START" -> MatrixNodeId.ARMED_START
    "CATALYST_CORE" -> MatrixNodeId.CATALYST_CORE
    "OVERDRIVE_COIL" -> MatrixNodeId.OVERDRIVE_COIL
    "REBORN_ARSENAL" -> MatrixNodeId.REBORN_ARSENAL
    "GLASS_ENGINE" -> MatrixNodeId.GLASS_ENGINE
    "REINFORCED_HULL" -> MatrixNodeId.REINFORCED_HULL
    "HARDLIGHT_RESERVE" -> MatrixNodeId.HARDLIGHT_RESERVE
    "IMPACT_DAMPERS" -> MatrixNodeId.IMPACT_DAMPERS
    "SECOND_WIND" -> MatrixNodeId.SECOND_WIND
    "NANOFORGE" -> MatrixNodeId.NANOFORGE
    "BASTION_PROTOCOL" -> MatrixNodeId.BASTION_PROTOCOL
    "AEGIS_LATTICE" -> MatrixNodeId.AEGIS_LATTICE
    "JUGGERNAUT" -> MatrixNodeId.JUGGERNAUT
    "MAGNET_ARRAY" -> MatrixNodeId.MAGNET_ARRAY
    "CRYO_LOOP" -> MatrixNodeId.CRYO_LOOP
    "VECTOR_THRUSTERS" -> MatrixNodeId.VECTOR_THRUSTERS
    "NEAR_MISS" -> MatrixNodeId.NEAR_MISS
    "RICOCHET_RIG" -> MatrixNodeId.RICOCHET_RIG
    "OVERCLOCKED_REFLEXES" -> MatrixNodeId.OVERCLOCKED_REFLEXES
    "GRAVITY_SURFER" -> MatrixNodeId.GRAVITY_SURFER
    "PERPETUAL_DRIFT" -> MatrixNodeId.PERPETUAL_DRIFT
    "SALVAGE_PROTOCOL" -> MatrixNodeId.SALVAGE_PROTOCOL
    "DATA_ARCHIVE" -> MatrixNodeId.DATA_ARCHIVE
    "REROLL_CACHE" -> MatrixNodeId.REROLL_CACHE
    "WIDE_OFFER" -> MatrixNodeId.WIDE_OFFER
    "LUCKY_CHARM" -> MatrixNodeId.LUCKY_CHARM
    "ELITE_BOUNTY" -> MatrixNodeId.ELITE_BOUNTY
    "HEIRLOOM_VAULT" -> MatrixNodeId.HEIRLOOM_VAULT
    "PROSPECTOR" -> MatrixNodeId.PROSPECTOR
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

/** Wire ids of the current Relic catalog; retired names are never mapped (see isFromRetiredCatalog). */
private fun RelicId.wireId(): String = when (this) {
    RelicId.PERPETUAL_VECTOR -> "PERPETUAL_VECTOR"
    RelicId.BLINK_DRIVE -> "BLINK_DRIVE"
    RelicId.GYRE_ENGINE -> "GYRE_ENGINE"
    RelicId.GLASS_COMET -> "GLASS_COMET"
    RelicId.EXECUTIONERS_PRISM -> "EXECUTIONERS_PRISM"
    RelicId.ECHO_CHAMBER -> "ECHO_CHAMBER"
    RelicId.FURNACE_COVENANT -> "FURNACE_COVENANT"
    RelicId.CINDER_CROWN -> "CINDER_CROWN"
    RelicId.OVERDRIVE_CASCADE -> "OVERDRIVE_CASCADE"
    RelicId.ABSOLUTE_ZERO -> "ABSOLUTE_ZERO"
    RelicId.STASIS_ANCHOR -> "STASIS_ANCHOR"
    RelicId.STORMHEART_CAPACITOR -> "STORMHEART_CAPACITOR"
    RelicId.AEGIS_INVERSION -> "AEGIS_INVERSION"
    RelicId.NEUTRON_HEART -> "NEUTRON_HEART"
    RelicId.EVENT_HORIZON -> "EVENT_HORIZON"
    RelicId.VERDANT_ROT -> "VERDANT_ROT"
    RelicId.PANDEMIC_CODEX -> "PANDEMIC_CODEX"
    RelicId.AGONY_SCEPTER -> "AGONY_SCEPTER"
    RelicId.HOLLOW_CROWN -> "HOLLOW_CROWN"
    RelicId.OUROBOROS_SEAL -> "OUROBOROS_SEAL"
    RelicId.THUNDER_CHOIR -> "THUNDER_CHOIR"
    RelicId.NULL_COVENANT -> "NULL_COVENANT"
}

private fun String.relicId(): RelicId = when (this) {
    "PERPETUAL_VECTOR" -> RelicId.PERPETUAL_VECTOR
    "BLINK_DRIVE" -> RelicId.BLINK_DRIVE
    "GYRE_ENGINE" -> RelicId.GYRE_ENGINE
    "GLASS_COMET" -> RelicId.GLASS_COMET
    "EXECUTIONERS_PRISM" -> RelicId.EXECUTIONERS_PRISM
    "ECHO_CHAMBER" -> RelicId.ECHO_CHAMBER
    "FURNACE_COVENANT" -> RelicId.FURNACE_COVENANT
    "CINDER_CROWN" -> RelicId.CINDER_CROWN
    "OVERDRIVE_CASCADE" -> RelicId.OVERDRIVE_CASCADE
    "ABSOLUTE_ZERO" -> RelicId.ABSOLUTE_ZERO
    "STASIS_ANCHOR" -> RelicId.STASIS_ANCHOR
    "STORMHEART_CAPACITOR" -> RelicId.STORMHEART_CAPACITOR
    "AEGIS_INVERSION" -> RelicId.AEGIS_INVERSION
    "NEUTRON_HEART" -> RelicId.NEUTRON_HEART
    "EVENT_HORIZON" -> RelicId.EVENT_HORIZON
    "VERDANT_ROT" -> RelicId.VERDANT_ROT
    "PANDEMIC_CODEX" -> RelicId.PANDEMIC_CODEX
    "AGONY_SCEPTER" -> RelicId.AGONY_SCEPTER
    "HOLLOW_CROWN" -> RelicId.HOLLOW_CROWN
    "OUROBOROS_SEAL" -> RelicId.OUROBOROS_SEAL
    "THUNDER_CHOIR" -> RelicId.THUNDER_CHOIR
    "NULL_COVENANT" -> RelicId.NULL_COVENANT
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

private fun ReactionId.wireId(): String = when (this) {
    ReactionId.THERMAL_SHOCK -> "THERMAL_SHOCK"
    ReactionId.PLASMA_BURST -> "PLASMA_BURST"
    ReactionId.FUSION -> "FUSION"
    ReactionId.WILDFIRE -> "WILDFIRE"
    ReactionId.SUPERCONDUCT -> "SUPERCONDUCT"
    ReactionId.GLACIER_CRUSH -> "GLACIER_CRUSH"
    ReactionId.EMBRITTLE -> "EMBRITTLE"
    ReactionId.MAGNETIZE -> "MAGNETIZE"
    ReactionId.FEEDBACK -> "FEEDBACK"
    ReactionId.ROT_WELL -> "ROT_WELL"
    ReactionId.THUNDERCLAP -> "THUNDERCLAP"
    ReactionId.SHATTERPOINT -> "SHATTERPOINT"
    ReactionId.RESONANT_ARC -> "RESONANT_ARC"
    ReactionId.SEISMIC -> "SEISMIC"
    ReactionId.FRACTURE -> "FRACTURE"
    ReactionId.SILENCE -> "SILENCE"
    ReactionId.BLACKFIRE -> "BLACKFIRE"
    ReactionId.STASIS -> "STASIS"
    ReactionId.ANNIHILATE -> "ANNIHILATE"
    ReactionId.NULL_WELL -> "NULL_WELL"
    ReactionId.OBLIVION -> "OBLIVION"
}

private fun String.reactionId(): ReactionId = when (this) {
    "THERMAL_SHOCK" -> ReactionId.THERMAL_SHOCK
    "PLASMA_BURST" -> ReactionId.PLASMA_BURST
    "FUSION" -> ReactionId.FUSION
    "WILDFIRE" -> ReactionId.WILDFIRE
    "SUPERCONDUCT" -> ReactionId.SUPERCONDUCT
    "GLACIER_CRUSH" -> ReactionId.GLACIER_CRUSH
    "EMBRITTLE" -> ReactionId.EMBRITTLE
    "MAGNETIZE" -> ReactionId.MAGNETIZE
    "FEEDBACK" -> ReactionId.FEEDBACK
    "ROT_WELL" -> ReactionId.ROT_WELL
    "THUNDERCLAP" -> ReactionId.THUNDERCLAP
    "SHATTERPOINT" -> ReactionId.SHATTERPOINT
    "RESONANT_ARC" -> ReactionId.RESONANT_ARC
    "SEISMIC" -> ReactionId.SEISMIC
    "FRACTURE" -> ReactionId.FRACTURE
    "SILENCE" -> ReactionId.SILENCE
    "BLACKFIRE" -> ReactionId.BLACKFIRE
    "STASIS" -> ReactionId.STASIS
    "ANNIHILATE" -> ReactionId.ANNIHILATE
    "NULL_WELL" -> ReactionId.NULL_WELL
    "OBLIVION" -> ReactionId.OBLIVION
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

private fun RelicPairingId.wireId(): String = when (this) {
    RelicPairingId.ENDLESS_GYRE -> "ENDLESS_GYRE"
    RelicPairingId.ASSASSINS_LINE -> "ASSASSINS_LINE"
    RelicPairingId.MELTDOWN -> "MELTDOWN"
    RelicPairingId.COLD_VERDICT -> "COLD_VERDICT"
    RelicPairingId.LIGHTNING_ROD -> "LIGHTNING_ROD"
    RelicPairingId.BLACK_STAR -> "BLACK_STAR"
    RelicPairingId.BLIGHT_GARDEN -> "BLIGHT_GARDEN"
    RelicPairingId.FIRESTORM -> "FIRESTORM"
    RelicPairingId.FORBIDDEN_ECHO -> "FORBIDDEN_ECHO"
    RelicPairingId.ROT_WARD -> "ROT_WARD"
    RelicPairingId.STILL_WATERS -> "STILL_WATERS"
    RelicPairingId.FOLD_VECTOR -> "FOLD_VECTOR"
    RelicPairingId.HOLLOW_VOW -> "HOLLOW_VOW"
    RelicPairingId.FARADAY_CAGE -> "FARADAY_CAGE"
    RelicPairingId.THERMAL_PARADOX -> "THERMAL_PARADOX"
    RelicPairingId.EXTINGUISHED -> "EXTINGUISHED"
    RelicPairingId.SHATTERED_AEGIS -> "SHATTERED_AEGIS"
    RelicPairingId.CONTRADICTION -> "CONTRADICTION"
    RelicPairingId.HEAVY_BLINK -> "HEAVY_BLINK"
    RelicPairingId.FRAGILE_REBIRTH -> "FRAGILE_REBIRTH"
}

private fun String.relicPairingId(): RelicPairingId = when (this) {
    "ENDLESS_GYRE" -> RelicPairingId.ENDLESS_GYRE
    "ASSASSINS_LINE" -> RelicPairingId.ASSASSINS_LINE
    "MELTDOWN" -> RelicPairingId.MELTDOWN
    "COLD_VERDICT" -> RelicPairingId.COLD_VERDICT
    "LIGHTNING_ROD" -> RelicPairingId.LIGHTNING_ROD
    "BLACK_STAR" -> RelicPairingId.BLACK_STAR
    "BLIGHT_GARDEN" -> RelicPairingId.BLIGHT_GARDEN
    "FIRESTORM" -> RelicPairingId.FIRESTORM
    "FORBIDDEN_ECHO" -> RelicPairingId.FORBIDDEN_ECHO
    "ROT_WARD" -> RelicPairingId.ROT_WARD
    "STILL_WATERS" -> RelicPairingId.STILL_WATERS
    "FOLD_VECTOR" -> RelicPairingId.FOLD_VECTOR
    "HOLLOW_VOW" -> RelicPairingId.HOLLOW_VOW
    "FARADAY_CAGE" -> RelicPairingId.FARADAY_CAGE
    "THERMAL_PARADOX" -> RelicPairingId.THERMAL_PARADOX
    "EXTINGUISHED" -> RelicPairingId.EXTINGUISHED
    "SHATTERED_AEGIS" -> RelicPairingId.SHATTERED_AEGIS
    "CONTRADICTION" -> RelicPairingId.CONTRADICTION
    "HEAVY_BLINK" -> RelicPairingId.HEAVY_BLINK
    "FRAGILE_REBIRTH" -> RelicPairingId.FRAGILE_REBIRTH
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

private fun ParticleDensity.wireId(): String = when (this) {
    ParticleDensity.LOW -> "LOW"
    ParticleDensity.NORMAL -> "NORMAL"
    ParticleDensity.HIGH -> "HIGH"
}

private fun String.particleDensity(): ParticleDensity = when (this) {
    "LOW" -> ParticleDensity.LOW
    "NORMAL" -> ParticleDensity.NORMAL
    "HIGH" -> ParticleDensity.HIGH
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

private fun DamageNumberSize.wireId(): String = when (this) {
    DamageNumberSize.SMALL -> "SMALL"
    DamageNumberSize.NORMAL -> "NORMAL"
    DamageNumberSize.LARGE -> "LARGE"
    DamageNumberSize.HUGE -> "HUGE"
}

private fun String.damageNumberSize(): DamageNumberSize = when (this) {
    "SMALL" -> DamageNumberSize.SMALL
    "NORMAL" -> DamageNumberSize.NORMAL
    "LARGE" -> DamageNumberSize.LARGE
    "HUGE" -> DamageNumberSize.HUGE
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

private fun DamageNumberFormat.wireId(): String = when (this) {
    DamageNumberFormat.COMPACT -> "COMPACT"
    DamageNumberFormat.FULL -> "FULL"
}

private fun String.damageNumberFormat(): DamageNumberFormat = when (this) {
    "COMPACT" -> DamageNumberFormat.COMPACT
    "FULL" -> DamageNumberFormat.FULL
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

/** DEFAULT has no wire id: it is represented only by the absent field. */
private fun ColorVision.wireIdOrNull(): String? = when (this) {
    ColorVision.DEFAULT -> null
    ColorVision.PROTAN -> "PROTAN"
    ColorVision.DEUTAN -> "DEUTAN"
    ColorVision.TRITAN -> "TRITAN"
    ColorVision.MONO -> "MONO"
}

private fun String.colorVision(): ColorVision = when (this) {
    "PROTAN" -> ColorVision.PROTAN
    "DEUTAN" -> ColorVision.DEUTAN
    "TRITAN" -> ColorVision.TRITAN
    "MONO" -> ColorVision.MONO
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

private fun String.appLanguage(): AppLanguage = when (this) {
    "ru" -> AppLanguage.Russian
    "en" -> AppLanguage.English
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

/**
 * Strict mapping of a Core Matrix record list: strictly sorted by wire id (no duplicates), every id
 * known and every rank within 1..MAX_MATRIX_NODE_RANKS (a zero rank is never written).
 */
private fun MatrixProgressDto.toRanks(): List<Int> {
    rejectUnless(ranks.map(MatrixNodeRankDto::id).isStrictlySorted(), ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE)
    rejectUnless(ranks.size <= MatrixNodeId.entries.size, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    val result = MutableList(MatrixNodeId.entries.size) { 0 }
    ranks.forEach { record ->
        val id = record.id.matrixNodeId()
        rejectUnless(record.rank in 1..ContentBounds.MAX_MATRIX_NODE_RANKS, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
        result[id.ordinal] = record.rank
    }
    return result
}

/**
 * Strict mapping of the upgrade record lists: each strictly sorted by wire id (no duplicates), every
 * id known and every level within 1..MAX_UPGRADE_LEVEL (a zero level is never written).
 */
private fun PermanentUpgradesDto.toUpgrades(): PermanentUpgrades {
    fun <Id> List<UpgradeLevelDto>.toLevels(size: Int, id: (String) -> Id, ordinal: (Id) -> Int): List<Int> {
        rejectUnless(map(UpgradeLevelDto::id).isStrictlySorted(), ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE)
        rejectUnless(this.size <= size, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
        val levels = MutableList(size) { 0 }
        forEach { record ->
            val index = ordinal(id(record.id))
            rejectUnless(record.level in 1..ContentBounds.MAX_UPGRADE_LEVEL, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
            levels[index] = record.level
        }
        return levels
    }
    return PermanentUpgrades(
        characterLevels = characters.toLevels(CoreShape.entries.size, { it.coreShape() }, CoreShape::ordinal),
        weaponLevels = weapons.toLevels(WeaponId.entries.size, { it.weaponId() }, WeaponId::ordinal),
    )
}

/**
 * The retired Lab, frozen as it shipped: eight upgrades priced `baseCost × rank²` for each rank.
 * A legacy save must list exactly these wire ids in sorted order with ranks in 0..maxRanks; its
 * refund is the full price of every owned rank, so veterans keep their whole spend.
 */
private object RetiredLabRefund {
    private class LegacyUpgrade(val wireId: String, val maxRanks: Int, val baseCost: Long)

    private val upgrades: List<LegacyUpgrade> = listOf(
        LegacyUpgrade("ARMORY_LICENSE", maxRanks = 12, baseCost = 45),
        LegacyUpgrade("CORE_INTEGRITY", maxRanks = 10, baseCost = 18),
        LegacyUpgrade("CRYO_VENTS", maxRanks = 8, baseCost = 26),
        LegacyUpgrade("DASH_CAPACITOR", maxRanks = 8, baseCost = 30),
        LegacyUpgrade("DATA_ARCHIVE", maxRanks = 10, baseCost = 38),
        LegacyUpgrade("KINETIC_AMPLIFIER", maxRanks = 10, baseCost = 22),
        LegacyUpgrade("MAGNETIC_RESONANCE", maxRanks = 8, baseCost = 24),
        LegacyUpgrade("SALVAGE_PROTOCOL", maxRanks = 10, baseCost = 34),
    )

    fun refund(lab: LabProgressDto): Long {
        rejectUnless(
            lab.ranks.map(MetaUpgradeRankDto::id) == upgrades.map(LegacyUpgrade::wireId),
            ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
        )
        var total = 0L
        lab.ranks.forEachIndexed { index, record ->
            val upgrade = upgrades[index]
            rejectUnless(record.rank in 0..upgrade.maxRanks, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
            for (rank in 1..record.rank) total += upgrade.baseCost * rank * rank
        }
        return total
    }
}

private fun saturatedAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

private class ProfileCodecRejection(
    val reason: ProfileSnapshotRejection,
) : RuntimeException()

private fun reject(reason: ProfileSnapshotRejection): Nothing = throw ProfileCodecRejection(reason)

private fun rejectUnless(condition: Boolean, reason: ProfileSnapshotRejection) {
    if (!condition) reject(reason)
}

private enum class Utf8Validation { Accepted, TooLarge, Invalid }

private fun String.utf8Validation(): Utf8Validation {
    var byteCount = 0
    var index = 0
    while (index < length) {
        val character = this[index]
        val encodedBytes = when {
            character.code <= 0x7F -> 1
            character.code <= 0x7FF -> 2
            character.isHighSurrogate() -> {
                if (index + 1 >= length || !this[index + 1].isLowSurrogate()) {
                    return Utf8Validation.Invalid
                }
                index += 1
                4
            }
            character.isLowSurrogate() -> return Utf8Validation.Invalid
            else -> 3
        }
        if (byteCount > MAX_PROFILE_PAYLOAD_BYTES - encodedBytes) return Utf8Validation.TooLarge
        byteCount += encodedBytes
        index += 1
    }
    return Utf8Validation.Accepted
}
