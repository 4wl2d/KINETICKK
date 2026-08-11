// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.LocalPlayerId
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileV4Rejection
import kinetickk.ball.profile.api.ProfileV4Snapshot
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

internal const val PROFILE_SCHEMA_VERSION: Int = 4
internal const val MAX_PROFILE_PAYLOAD_BYTES: Int = 65_536
internal val LOCAL_PROFILE_ID: String = LocalPlayerId.LOCAL_PLAYER.stableValue

internal sealed interface ProfileV4EncodeResult {
    data class Encoded(val payload: String) : ProfileV4EncodeResult
    data class Rejected(val reason: ProfileV4Rejection) : ProfileV4EncodeResult
}

internal sealed interface ProfileV4DecodeResult {
    data class Decoded(val snapshot: ProfileV4Snapshot) : ProfileV4DecodeResult
    data class Rejected(val reason: ProfileV4Rejection) : ProfileV4DecodeResult
}

/** Strict, canonical save-v4 codec. It never imports or normalizes older formats. */
internal object ProfileV4Codec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        allowSpecialFloatingPointValues = false
        prettyPrint = false
    }

    fun encode(snapshot: ProfileV4Snapshot): ProfileV4EncodeResult {
        val dto = try {
            snapshot.toDto()
        } catch (rejection: ProfileCodecRejection) {
            return ProfileV4EncodeResult.Rejected(rejection.reason)
        }
        val payload = try {
            json.encodeToString(dto)
        } catch (_: SerializationException) {
            return ProfileV4EncodeResult.Rejected(ProfileV4Rejection.MALFORMED_JSON)
        } catch (_: IllegalArgumentException) {
            return ProfileV4EncodeResult.Rejected(ProfileV4Rejection.MALFORMED_JSON)
        }
        return when (payload.utf8Validation()) {
            Utf8Validation.Accepted -> ProfileV4EncodeResult.Encoded(payload)
            Utf8Validation.TooLarge -> ProfileV4EncodeResult.Rejected(ProfileV4Rejection.PAYLOAD_TOO_LARGE)
            Utf8Validation.Invalid -> ProfileV4EncodeResult.Rejected(ProfileV4Rejection.INVALID_UTF8)
        }
    }

    fun decode(payload: String): ProfileV4DecodeResult {
        when (payload.utf8Validation()) {
            Utf8Validation.TooLarge -> {
                return ProfileV4DecodeResult.Rejected(ProfileV4Rejection.PAYLOAD_TOO_LARGE)
            }
            Utf8Validation.Invalid -> {
                return ProfileV4DecodeResult.Rejected(ProfileV4Rejection.INVALID_UTF8)
            }
            Utf8Validation.Accepted -> Unit
        }

        val dto = try {
            json.decodeFromString<ProfileEnvelopeV4Dto>(payload)
        } catch (_: SerializationException) {
            return ProfileV4DecodeResult.Rejected(ProfileV4Rejection.MALFORMED_JSON)
        } catch (_: IllegalArgumentException) {
            return ProfileV4DecodeResult.Rejected(ProfileV4Rejection.MALFORMED_JSON)
        }

        val canonicalPayload = try {
            json.encodeToString(dto)
        } catch (_: SerializationException) {
            return ProfileV4DecodeResult.Rejected(ProfileV4Rejection.MALFORMED_JSON)
        } catch (_: IllegalArgumentException) {
            return ProfileV4DecodeResult.Rejected(ProfileV4Rejection.MALFORMED_JSON)
        }
        if (canonicalPayload != payload) {
            return ProfileV4DecodeResult.Rejected(ProfileV4Rejection.NON_CANONICAL_PAYLOAD)
        }

        return try {
            ProfileV4DecodeResult.Decoded(dto.toSnapshot())
        } catch (rejection: ProfileCodecRejection) {
            ProfileV4DecodeResult.Rejected(rejection.reason)
        }
    }
}

@Serializable
private data class ProfileEnvelopeV4Dto(
    val schemaVersion: Int,
    val profileId: String,
    val contentVersion: String,
    val revision: String,
    val legacyResetConfirmed: Boolean,
    val profile: PlayerProfileV4Dto,
)

@Serializable
private data class PlayerProfileV4Dto(
    val preferences: PlayerPreferencesV4Dto,
    val economy: PlayerEconomyV4Dto,
    val loadout: PlayerLoadoutV4Dto,
    val labProgress: LabProgressV4Dto,
    val collection: PlayerCollectionV4Dto,
    val rebirthProgress: RebirthProgressV4Dto,
)

@Serializable
private data class PlayerPreferencesV4Dto(
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
)

@Serializable
private data class PlayerEconomyV4Dto(
    val matter: String,
    val lifetimeMatter: String,
)

@Serializable
private data class PlayerLoadoutV4Dto(
    val coreShapeId: String,
    val selectedWeaponId: String,
    val unlockedWeaponIds: List<String>,
)

@Serializable
private data class LabProgressV4Dto(
    val ranks: List<MetaUpgradeRankV4Dto>,
)

@Serializable
private data class MetaUpgradeRankV4Dto(
    val id: String,
    val rank: Int,
)

@Serializable
private data class PlayerCollectionV4Dto(
    val discoveredItemIds: List<Int>,
)

@Serializable
private data class RebirthProgressV4Dto(
    val level: Int,
    val highestCleared: Int,
)

private fun ProfileV4Snapshot.toDto(): ProfileEnvelopeV4Dto {
    validateProfile(profile)
    rejectUnless(contentVersion.value.isNotBlank(), ProfileV4Rejection.VALUE_OUT_OF_RANGE)
    rejectUnless(revision.value >= 0L, ProfileV4Rejection.INVALID_DECIMAL)

    val preferences = profile.preferences
    val ranks = MetaUpgradeId.entries
        .map { id ->
            MetaUpgradeRankV4Dto(
                id = id.wireId(),
                rank = profile.labProgress.rank(id),
            )
        }
        .sortedBy(MetaUpgradeRankV4Dto::id)

    return ProfileEnvelopeV4Dto(
        schemaVersion = PROFILE_SCHEMA_VERSION,
        profileId = LOCAL_PROFILE_ID,
        contentVersion = contentVersion.value,
        revision = revision.value.toString(),
        legacyResetConfirmed = legacyResetConfirmed,
        profile = PlayerProfileV4Dto(
            preferences = PlayerPreferencesV4Dto(
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
            ),
            economy = PlayerEconomyV4Dto(
                matter = profile.economy.matter.toString(),
                lifetimeMatter = profile.economy.lifetimeMatter.toString(),
            ),
            loadout = PlayerLoadoutV4Dto(
                coreShapeId = profile.loadout.coreShape.wireId(),
                selectedWeaponId = profile.loadout.selectedWeapon.wireId(),
                unlockedWeaponIds = profile.loadout.unlockedWeapons
                    .map(WeaponId::wireId)
                    .sorted(),
            ),
            labProgress = LabProgressV4Dto(ranks),
            collection = PlayerCollectionV4Dto(
                profile.collection.discoveredItemIds.sorted(),
            ),
            rebirthProgress = RebirthProgressV4Dto(
                level = profile.rebirthProgress.level,
                highestCleared = profile.rebirthProgress.highestCleared,
            ),
        ),
    )
}

private fun ProfileEnvelopeV4Dto.toSnapshot(): ProfileV4Snapshot {
    rejectUnless(schemaVersion == PROFILE_SCHEMA_VERSION, ProfileV4Rejection.UNSUPPORTED_SCHEMA_VERSION)
    rejectUnless(profileId == LOCAL_PROFILE_ID, ProfileV4Rejection.PROFILE_ID_MISMATCH)
    rejectUnless(contentVersion.isNotBlank(), ProfileV4Rejection.VALUE_OUT_OF_RANGE)
    val revisionValue = revision.parseCanonicalNonNegativeLong()
    val contentVersionValue = try {
        ContentVersion(contentVersion)
    } catch (_: IllegalArgumentException) {
        reject(ProfileV4Rejection.VALUE_OUT_OF_RANGE)
    }

    val expectedRankIds = MetaUpgradeId.entries.map { it.wireId() }.sorted()
    val actualRankIds = profile.labProgress.ranks.map(MetaUpgradeRankV4Dto::id)
    rejectUnless(
        actualRankIds == actualRankIds.distinct().sorted() && actualRankIds == expectedRankIds,
        ProfileV4Rejection.INVALID_ORDER_OR_DUPLICATE,
    )
    rejectUnless(
        profile.loadout.unlockedWeaponIds == profile.loadout.unlockedWeaponIds.distinct().sorted(),
        ProfileV4Rejection.INVALID_ORDER_OR_DUPLICATE,
    )
    rejectUnless(
        profile.collection.discoveredItemIds ==
            profile.collection.discoveredItemIds.distinct().sorted(),
        ProfileV4Rejection.INVALID_ORDER_OR_DUPLICATE,
    )

    val metaRanks = MutableList(MetaUpgradeId.entries.size) { 0 }
    profile.labProgress.ranks.forEach { record ->
        val id = record.id.metaUpgradeId()
        metaRanks[id.ordinal] = record.rank
    }

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
        ),
        economy = PlayerEconomy(
            matter = profile.economy.matter.parseCanonicalNonNegativeLong(),
            lifetimeMatter = profile.economy.lifetimeMatter.parseCanonicalNonNegativeLong(),
        ),
        loadout = PlayerLoadout(
            coreShape = profile.loadout.coreShapeId.coreShape(),
            selectedWeapon = profile.loadout.selectedWeaponId.weaponId(),
            unlockedWeapons = profile.loadout.unlockedWeaponIds.mapTo(mutableSetOf()) { it.weaponId() },
        ),
        labProgress = LabProgress(metaRanks),
        collection = PlayerCollection(profile.collection.discoveredItemIds.toSet()),
        rebirthProgress = RebirthProgress(
            level = profile.rebirthProgress.level,
            highestCleared = profile.rebirthProgress.highestCleared,
        ),
    )
    validateProfile(decoded)
    profile.labProgress.ranks.forEach { record ->
        rejectUnless(record.rank >= 0, ProfileV4Rejection.VALUE_OUT_OF_RANGE)
    }

    return ProfileV4Snapshot(
        contentVersion = contentVersionValue,
        revision = ProfileRevision(revisionValue),
        legacyResetConfirmed = legacyResetConfirmed,
        profile = decoded,
    )
}

private fun validateProfile(profile: PlayerProfile) {
    val preferences = profile.preferences
    rejectUnless(
        preferences.masterVolume.isFinite() && preferences.masterVolume in 0f..1f &&
            preferences.simulationSpeed.isFinite() && preferences.simulationSpeed in 0.75f..2f &&
            preferences.simulationSpeed in SIMULATION_SPEED_OPTIONS &&
            preferences.textScale.isFinite() && preferences.textScale in 1f..1.75f &&
            preferences.damageNumberTierThreshold in DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS,
        ProfileV4Rejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.economy.matter >= 0L &&
            profile.economy.lifetimeMatter >= profile.economy.matter,
        ProfileV4Rejection.INCONSISTENT_PROFILE,
    )

    rejectUnless(
        profile.loadout.unlockedWeapons.isNotEmpty() &&
            profile.loadout.unlockedWeapons.size <= ContentBounds.MAX_WEAPONS,
        ProfileV4Rejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.loadout.selectedWeapon in profile.loadout.unlockedWeapons,
        ProfileV4Rejection.INCONSISTENT_PROFILE,
    )

    rejectUnless(
        profile.labProgress.ranks.size == MetaUpgradeId.entries.size &&
            profile.labProgress.ranks.size == ContentBounds.MAX_META_UPGRADES,
        ProfileV4Rejection.INCONSISTENT_PROFILE,
    )
    rejectUnless(profile.labProgress.ranks.all { it >= 0 }, ProfileV4Rejection.VALUE_OUT_OF_RANGE)

    rejectUnless(
        profile.collection.discoveredItemIds.size <= ContentBounds.MAX_ITEMS &&
            profile.collection.discoveredItemIds.all { it >= 0 },
        ProfileV4Rejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.rebirthProgress.level in ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL &&
            profile.rebirthProgress.highestCleared in -1..profile.rebirthProgress.level,
        ProfileV4Rejection.VALUE_OUT_OF_RANGE,
    )
}

private fun Float.toPercent(): Int {
    rejectUnless(isFinite(), ProfileV4Rejection.VALUE_OUT_OF_RANGE)
    return (this * 100f).roundToInt()
}

private fun String.parseCanonicalNonNegativeLong(): Long {
    if (isEmpty() || this != "0" && (first() == '0' || any { it !in '0'..'9' })) {
        reject(ProfileV4Rejection.INVALID_DECIMAL)
    }
    if (this == "0") return 0L
    if (any { it !in '0'..'9' }) reject(ProfileV4Rejection.INVALID_DECIMAL)
    val value = toLongOrNull() ?: reject(ProfileV4Rejection.INVALID_DECIMAL)
    rejectUnless(value >= 0L && value.toString() == this, ProfileV4Rejection.INVALID_DECIMAL)
    return value
}

private fun CoreShape.wireId(): String = when (this) {
    CoreShape.ORB -> "ORB"
    CoreShape.PRISM -> "PRISM"
    CoreShape.SHARD -> "SHARD"
}

private fun String.coreShape(): CoreShape = when (this) {
    "ORB" -> CoreShape.ORB
    "PRISM" -> CoreShape.PRISM
    "SHARD" -> CoreShape.SHARD
    else -> reject(ProfileV4Rejection.INVALID_STABLE_ID)
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
    else -> reject(ProfileV4Rejection.INVALID_STABLE_ID)
}

private fun MetaUpgradeId.wireId(): String = when (this) {
    MetaUpgradeId.CORE_INTEGRITY -> "CORE_INTEGRITY"
    MetaUpgradeId.KINETIC_AMPLIFIER -> "KINETIC_AMPLIFIER"
    MetaUpgradeId.MAGNETIC_RESONANCE -> "MAGNETIC_RESONANCE"
    MetaUpgradeId.CRYO_VENTS -> "CRYO_VENTS"
    MetaUpgradeId.DASH_CAPACITOR -> "DASH_CAPACITOR"
    MetaUpgradeId.SALVAGE_PROTOCOL -> "SALVAGE_PROTOCOL"
    MetaUpgradeId.DATA_ARCHIVE -> "DATA_ARCHIVE"
    MetaUpgradeId.ARMORY_LICENSE -> "ARMORY_LICENSE"
}

private fun String.metaUpgradeId(): MetaUpgradeId = when (this) {
    "CORE_INTEGRITY" -> MetaUpgradeId.CORE_INTEGRITY
    "KINETIC_AMPLIFIER" -> MetaUpgradeId.KINETIC_AMPLIFIER
    "MAGNETIC_RESONANCE" -> MetaUpgradeId.MAGNETIC_RESONANCE
    "CRYO_VENTS" -> MetaUpgradeId.CRYO_VENTS
    "DASH_CAPACITOR" -> MetaUpgradeId.DASH_CAPACITOR
    "SALVAGE_PROTOCOL" -> MetaUpgradeId.SALVAGE_PROTOCOL
    "DATA_ARCHIVE" -> MetaUpgradeId.DATA_ARCHIVE
    "ARMORY_LICENSE" -> MetaUpgradeId.ARMORY_LICENSE
    else -> reject(ProfileV4Rejection.INVALID_STABLE_ID)
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
    else -> reject(ProfileV4Rejection.INVALID_STABLE_ID)
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
    else -> reject(ProfileV4Rejection.INVALID_STABLE_ID)
}

private fun DamageNumberFormat.wireId(): String = when (this) {
    DamageNumberFormat.COMPACT -> "COMPACT"
    DamageNumberFormat.FULL -> "FULL"
}

private fun String.damageNumberFormat(): DamageNumberFormat = when (this) {
    "COMPACT" -> DamageNumberFormat.COMPACT
    "FULL" -> DamageNumberFormat.FULL
    else -> reject(ProfileV4Rejection.INVALID_STABLE_ID)
}

private class ProfileCodecRejection(
    val reason: ProfileV4Rejection,
) : RuntimeException()

private fun reject(reason: ProfileV4Rejection): Nothing = throw ProfileCodecRejection(reason)

private fun rejectUnless(condition: Boolean, reason: ProfileV4Rejection) {
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
