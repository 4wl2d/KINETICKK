// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.data.progress

import kinetickk.feature.game.domain.model.DamageNumberFormat
import kinetickk.feature.game.domain.model.DamageNumberSize
import kinetickk.feature.game.domain.model.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD
import kinetickk.feature.game.domain.model.GameSettings
import kinetickk.feature.game.domain.model.ParticleDensity
import kinetickk.feature.game.domain.model.RebirthProgression
import kinetickk.feature.game.domain.model.StoredProgress
import kotlin.math.roundToInt

/** Private Resource serialization; this is not a domain decision API. */
internal object ProgressCodec {
    private const val VERSION = 3
    private const val LEGACY_VERSION = 2

    fun encode(progress: StoredProgress): String {
        val normalizedSettings = progress.settings.normalized()
        val rebirthLevel = progress.rebirthLevel.coerceIn(0, RebirthProgression.MAX_LEVEL)
        val highestClearedRebirth = progress.highestClearedRebirth.coerceIn(-1, rebirthLevel)
        val weaponMask = progress.unlockedWeaponIndices
            .filter { it in 0..30 }
            .fold(0) { mask, index -> mask or (1 shl index) }
        val settingsValue = listOf(
            normalizedSettings.soundEnabled.asInt(),
            normalizedSettings.musicEnabled.asInt(),
            (normalizedSettings.masterVolume * 100f).roundToInt(),
            (normalizedSettings.simulationSpeed * 100f).roundToInt(),
            normalizedSettings.screenShake.asInt(),
            normalizedSettings.particleDensity.ordinal,
            normalizedSettings.damageNumbers.asInt(),
            (normalizedSettings.textScale * 100f).roundToInt(),
            normalizedSettings.damageNumberSize.ordinal,
            normalizedSettings.damageNumberFormat.ordinal,
            normalizedSettings.damageNumberTierThreshold,
        ).joinToString(",")
        return listOf(
            VERSION,
            progress.matter.coerceAtLeast(0L),
            progress.lifetimeMatter.coerceAtLeast(progress.matter.coerceAtLeast(0L)),
            progress.coreShapeIndex.coerceAtLeast(0),
            progress.selectedWeaponIndex.coerceAtLeast(0),
            weaponMask,
            progress.metaLevels.joinToString(",") { it.coerceAtLeast(0).toString() },
            progress.discoveredItemIds.filter { it >= 0 }.sorted().joinToString(","),
            settingsValue,
            rebirthLevel,
            highestClearedRebirth,
        ).joinToString("|")
    }

    fun decode(value: String?): StoredProgress? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val parts = value.split('|')
            val version = parts.firstOrNull()?.toIntOrNull() ?: return null
            if (version != LEGACY_VERSION && version != VERSION) return null
            if (parts.size < 9 || version == VERSION && parts.size < 11) return null
            val matter = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val lifetimeMatter = parts[2].toLongOrNull()?.coerceAtLeast(matter) ?: matter
            val coreShapeIndex = parts[3].toIntOrNull()?.coerceAtLeast(0) ?: 0
            val selectedWeaponIndex = parts[4].toIntOrNull()?.coerceAtLeast(0) ?: 0
            val weaponMask = parts[5].toIntOrNull() ?: 1
            val unlockedWeapons = (0..30).filterTo(mutableSetOf()) { index ->
                weaponMask and (1 shl index) != 0
            }.ifEmpty { mutableSetOf(0) }
            val metaLevels = parts[6].split(',')
                .mapNotNull(String::toIntOrNull)
                .map { it.coerceAtLeast(0) }
                .let { levels -> List(8) { index -> levels.getOrElse(index) { 0 } } }
            val discoveries = parts[7].split(',')
                .mapNotNull(String::toIntOrNull)
                .filterTo(mutableSetOf()) { it >= 0 }
            val settingParts = parts[8].split(',')
            val settings = GameSettings(
                soundEnabled = settingParts.intAt(0, 1) != 0,
                musicEnabled = settingParts.intAt(1, 1) != 0,
                masterVolume = settingParts.intAt(2, 65) / 100f,
                simulationSpeed = settingParts.intAt(3, 115) / 100f,
                textScale = settingParts.intAt(7, 125) / 100f,
                screenShake = settingParts.intAt(4, 1) != 0,
                particleDensity = ParticleDensity.entries.getOrElse(settingParts.intAt(5, 1)) {
                    ParticleDensity.NORMAL
                },
                damageNumbers = settingParts.intAt(6, 1) != 0,
                damageNumberSize = DamageNumberSize.entries.getOrElse(
                    settingParts.intAt(8, DamageNumberSize.NORMAL.ordinal),
                ) {
                    DamageNumberSize.NORMAL
                },
                damageNumberFormat = DamageNumberFormat.entries.getOrElse(
                    settingParts.intAt(9, DamageNumberFormat.COMPACT.ordinal),
                ) {
                    DamageNumberFormat.COMPACT
                },
                damageNumberTierThreshold = settingParts.intAt(
                    10,
                    DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
                ),
            ).normalized()
            val rebirthLevel = if (version >= VERSION) {
                parts[9].toIntOrNull()?.coerceIn(0, RebirthProgression.MAX_LEVEL) ?: 0
            } else {
                0
            }
            val highestClearedRebirth = if (version >= VERSION) {
                parts[10].toIntOrNull()?.coerceIn(-1, rebirthLevel) ?: -1
            } else {
                -1
            }
            StoredProgress(
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
        }.getOrNull()
    }

    private fun Boolean.asInt(): Int = if (this) 1 else 0

    private fun List<String>.intAt(index: Int, fallback: Int): Int =
        getOrNull(index)?.toIntOrNull() ?: fallback
}
