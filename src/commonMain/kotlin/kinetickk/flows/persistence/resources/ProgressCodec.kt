// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.persistence.resources

import kinetickk.flows.persistence.ProgressPersistenceSchema
import kinetickk.flows.persistence.model.PersistedProgress
import kinetickk.flows.persistence.model.PersistedSettings
import kotlin.math.roundToInt

/** Private Resource serialization; this is not a Nucleus decision API. */
internal object ProgressCodec {
    fun encode(progress: PersistedProgress): String {
        val normalizedSettings = progress.settings.normalized()
        val rebirthLevel = progress.rebirthLevel.coerceIn(
            0,
            ProgressPersistenceSchema.MAX_REBIRTH_LEVEL,
        )
        val highestClearedRebirth = progress.highestClearedRebirth.coerceIn(-1, rebirthLevel)
        val weaponMask = progress.unlockedWeaponIndices
            .filter(ProgressPersistenceSchema::isSupportedWeaponCode)
            .fold(0) { mask, index -> mask or (1 shl index) }
        val settingsValue = listOf(
            normalizedSettings.soundEnabled.asInt(),
            normalizedSettings.musicEnabled.asInt(),
            (normalizedSettings.masterVolume * 100f).roundToInt(),
            (normalizedSettings.simulationSpeed * 100f).roundToInt(),
            normalizedSettings.screenShake.asInt(),
            normalizedSettings.particleDensityCode,
            normalizedSettings.damageNumbers.asInt(),
            (normalizedSettings.textScale * 100f).roundToInt(),
            normalizedSettings.damageNumberSizeCode,
            normalizedSettings.damageNumberFormatCode,
            normalizedSettings.damageNumberTierThreshold,
        ).joinToString(",")
        return listOf(
            ProgressPersistenceSchema.CURRENT_PAYLOAD_VERSION,
            progress.matter.coerceAtLeast(0L),
            progress.lifetimeMatter.coerceAtLeast(progress.matter.coerceAtLeast(0L)),
            progress.coreShapeIndex.coerceAtLeast(0),
            progress.selectedWeaponIndex.coerceAtLeast(0),
            weaponMask,
            List(ProgressPersistenceSchema.META_UPGRADE_CODE_COUNT) { index ->
                progress.metaLevels.getOrNull(index)?.coerceAtLeast(0) ?: 0
            }.joinToString(","),
            progress.discoveredItemIds.filter { it >= 0 }.sorted().joinToString(","),
            settingsValue,
            rebirthLevel,
            highestClearedRebirth,
        ).joinToString("|")
    }

    fun decode(value: String?): PersistedProgress? {
        if (value.isNullOrBlank()) return null
        return runCatching { decodeValidated(value) }.getOrNull()
    }

    private fun Boolean.asInt(): Int = if (this) 1 else 0

    private fun decodeValidated(value: String): PersistedProgress? {
        val parts = value.split('|')
        val version = parts.firstOrNull()?.toIntOrNull() ?: return null
        val expectedPartCount = when (version) {
            ProgressPersistenceSchema.LEGACY_PAYLOAD_VERSION ->
                ProgressPersistenceSchema.LEGACY_PAYLOAD_FIELD_COUNT
            ProgressPersistenceSchema.CURRENT_PAYLOAD_VERSION ->
                ProgressPersistenceSchema.CURRENT_PAYLOAD_FIELD_COUNT
            else -> return null
        }
        if (parts.size != expectedPartCount) return null

        val rawMatter = parts[1].toLongOrNull() ?: return null
        val matter = rawMatter.coerceAtLeast(0L)
        val lifetimeMatter = (parts[2].toLongOrNull() ?: return null).coerceAtLeast(matter)
        val coreShapeIndex = (parts[3].toIntOrNull() ?: return null).coerceAtLeast(0)
        val selectedWeaponIndex = (parts[4].toIntOrNull() ?: return null).coerceAtLeast(0)
        val weaponMask = (parts[5].toIntOrNull() ?: return null).takeIf { it >= 0 }
            ?: return null
        val unlockedWeapons = (0..30).filterTo(mutableSetOf()) { index ->
            weaponMask and (1 shl index) != 0
        }.ifEmpty { mutableSetOf(ProgressPersistenceSchema.BASELINE_WEAPON_CODE) }
        val metaLevels = parts[6].parseExactIntList(
            ProgressPersistenceSchema.META_UPGRADE_CODE_COUNT,
        )
            ?.map { it.coerceAtLeast(0) }
            ?: return null
        val discoveries = parts[7].parseIntSet()
            ?.filterTo(mutableSetOf()) { it >= 0 }
            ?: return null
        val settings = parseSettings(parts[8], version) ?: return null
        val rebirthLevel = if (version == ProgressPersistenceSchema.CURRENT_PAYLOAD_VERSION) {
            (parts[9].toIntOrNull() ?: return null).coerceIn(
                0,
                ProgressPersistenceSchema.MAX_REBIRTH_LEVEL,
            )
        } else {
            0
        }
        val highestClearedRebirth = if (
            version == ProgressPersistenceSchema.CURRENT_PAYLOAD_VERSION
        ) {
            (parts[10].toIntOrNull() ?: return null).coerceIn(-1, rebirthLevel)
        } else {
            -1
        }
        return PersistedProgress(
            matter = matter,
            lifetimeMatter = lifetimeMatter,
            coreShapeIndex = coreShapeIndex,
            selectedWeaponIndex = selectedWeaponIndex,
            unlockedWeaponIndices = unlockedWeapons,
            metaLevels = metaLevels,
            discoveredItemIds = discoveries,
            settings = settings,
            rebirthLevel = rebirthLevel,
            highestClearedRebirth = highestClearedRebirth,
        )
    }

    private fun parseSettings(value: String, version: Int): PersistedSettings? {
        val parts = value.split(',')
        val validPartCount = when (version) {
            ProgressPersistenceSchema.LEGACY_PAYLOAD_VERSION ->
                parts.size == ProgressPersistenceSchema.LEGACY_SETTINGS_FIELD_COUNT ||
                    parts.size ==
                    ProgressPersistenceSchema.LEGACY_SETTINGS_WITH_TEXT_SCALE_FIELD_COUNT
            ProgressPersistenceSchema.CURRENT_PAYLOAD_VERSION ->
                parts.size ==
                    ProgressPersistenceSchema.CURRENT_PAYLOAD_LEGACY_SETTINGS_FIELD_COUNT ||
                    parts.size == ProgressPersistenceSchema.CURRENT_SETTINGS_FIELD_COUNT
            else -> false
        }
        if (!validPartCount) return null

        val soundEnabled = parts.booleanAt(0) ?: return null
        val musicEnabled = parts.booleanAt(1) ?: return null
        val masterVolumePercent = parts[2].toIntOrNull() ?: return null
        val simulationSpeedPercent = parts[3].toIntOrNull() ?: return null
        val screenShake = parts.booleanAt(4) ?: return null
        val particleDensityCode = parts[5].toIntOrNull() ?: return null
        if (!ProgressPersistenceSchema.isSupportedParticleDensityCode(particleDensityCode)) {
            return null
        }
        val damageNumbers = parts.booleanAt(6) ?: return null
        val textScalePercent = if (
            parts.size >= ProgressPersistenceSchema.LEGACY_SETTINGS_WITH_TEXT_SCALE_FIELD_COUNT
        ) {
            parts[7].toIntOrNull() ?: return null
        } else {
            (ProgressPersistenceSchema.DEFAULT_TEXT_SCALE * 100f).roundToInt()
        }
        val damageNumberSizeCode = if (
            parts.size == ProgressPersistenceSchema.CURRENT_SETTINGS_FIELD_COUNT
        ) {
            val code = parts[8].toIntOrNull() ?: return null
            code.takeIf(ProgressPersistenceSchema::isSupportedDamageNumberSizeCode)
                ?: return null
        } else {
            ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_NORMAL_CODE
        }
        val damageNumberFormatCode = if (
            parts.size == ProgressPersistenceSchema.CURRENT_SETTINGS_FIELD_COUNT
        ) {
            val code = parts[9].toIntOrNull() ?: return null
            code.takeIf(ProgressPersistenceSchema::isSupportedDamageNumberFormatCode)
                ?: return null
        } else {
            ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_COMPACT_CODE
        }
        val damageNumberTierThreshold = if (
            parts.size == ProgressPersistenceSchema.CURRENT_SETTINGS_FIELD_COUNT
        ) {
            parts[10].toIntOrNull() ?: return null
        } else {
            ProgressPersistenceSchema.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD
        }
        return PersistedSettings(
            soundEnabled = soundEnabled,
            musicEnabled = musicEnabled,
            masterVolume = masterVolumePercent / 100f,
            simulationSpeed = simulationSpeedPercent / 100f,
            textScale = textScalePercent / 100f,
            screenShake = screenShake,
            particleDensityCode = particleDensityCode,
            damageNumbers = damageNumbers,
            damageNumberSizeCode = damageNumberSizeCode,
            damageNumberFormatCode = damageNumberFormatCode,
            damageNumberTierThreshold = damageNumberTierThreshold,
        ).normalized()
    }

    private fun List<String>.booleanAt(index: Int): Boolean? = when (getOrNull(index)) {
        "0" -> false
        "1" -> true
        else -> null
    }

    private fun String.parseExactIntList(expectedSize: Int): List<Int>? {
        val parts = split(',')
        if (parts.size != expectedSize) return null
        val result = ArrayList<Int>(expectedSize)
        for (part in parts) {
            result += part.toIntOrNull() ?: return null
        }
        return result
    }

    private fun String.parseIntSet(): Set<Int>? {
        if (isEmpty()) return emptySet()
        val result = mutableSetOf<Int>()
        for (part in split(',')) {
            result += part.toIntOrNull() ?: return null
        }
        return result
    }
}
