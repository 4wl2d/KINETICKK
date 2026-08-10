// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.RebirthProgress
import kotlin.math.roundToInt

/** Manual v2/v3 wire codec. The storage key and version remain independent on purpose. */
internal object ProfileCodec {
    private const val VERSION = 3
    private const val LEGACY_VERSION = 2

    fun encode(profile: PlayerProfile, policy: ProfilePolicySnapshot): String {
        val preferences = profile.preferences.normalized()
        val matter = profile.economy.matter.coerceAtLeast(0L)
        val rebirthLevel = profile.rebirthProgress.level.coerceIn(
            policy.rebirth.minimumLevel,
            policy.rebirth.maximumLevel,
        )
        val highestCleared = profile.rebirthProgress.highestCleared.coerceIn(-1, rebirthLevel)
        val weaponMask = profile.loadout.unlockedWeapons
            .map(WeaponId::ordinal)
            .filter { it in 0..30 }
            .fold(0) { mask, index -> mask or (1 shl index) }
        val preferencesValue = listOf(
            preferences.soundEnabled.asInt(),
            preferences.musicEnabled.asInt(),
            (preferences.masterVolume * 100f).roundToInt(),
            (preferences.simulationSpeed * 100f).roundToInt(),
            preferences.screenShake.asInt(),
            preferences.particleDensity.ordinal,
            preferences.damageNumbers.asInt(),
            (preferences.textScale * 100f).roundToInt(),
            preferences.damageNumberSize.ordinal,
            preferences.damageNumberFormat.ordinal,
            preferences.damageNumberTierThreshold,
        ).joinToString(",")
        return listOf(
            VERSION,
            matter,
            profile.economy.lifetimeMatter.coerceAtLeast(matter),
            profile.loadout.coreShape.ordinal,
            profile.loadout.selectedWeapon.ordinal,
            weaponMask,
            profile.labProgress.ranks.joinToString(",") { it.coerceAtLeast(0).toString() },
            profile.collection.discoveredItemIds.filter { it >= 0 }.sorted().joinToString(","),
            preferencesValue,
            rebirthLevel,
            highestCleared,
        ).joinToString("|")
    }

    fun decode(value: String?, policy: ProfilePolicySnapshot): PlayerProfile? {
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
            val unlockedWeapons = (0..30)
                .filter { index -> weaponMask and (1 shl index) != 0 }
                .mapNotNullTo(mutableSetOf()) { index -> WeaponId.entries.getOrNull(index) }
                .apply { if (isEmpty()) add(policy.weapons.first().id) }
            val metaLevels = parts[6].split(',')
                .mapNotNull(String::toIntOrNull)
                .map { it.coerceAtLeast(0) }
                .let { levels ->
                    List(policy.metaUpgrades.size) { index -> levels.getOrElse(index) { 0 } }
                }
            val discoveries = parts[7].split(',')
                .mapNotNull(String::toIntOrNull)
                .filterTo(mutableSetOf()) { it >= 0 }
            val settingParts = parts[8].split(',')
            val preferences = PlayerPreferences(
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
                parts[9].toIntOrNull()?.coerceIn(
                    policy.rebirth.minimumLevel,
                    policy.rebirth.maximumLevel,
                ) ?: policy.rebirth.minimumLevel
            } else {
                policy.rebirth.minimumLevel
            }
            val highestCleared = if (version >= VERSION) {
                parts[10].toIntOrNull()?.coerceIn(-1, rebirthLevel) ?: -1
            } else {
                -1
            }

            PlayerProfile(
                preferences = preferences,
                economy = PlayerEconomy(matter, lifetimeMatter),
                loadout = PlayerLoadout(
                    coreShape = CoreShape.entries[coreShapeIndex.coerceAtMost(CoreShape.entries.lastIndex)],
                    selectedWeapon = WeaponId.entries[
                        selectedWeaponIndex.coerceAtMost(WeaponId.entries.lastIndex)
                    ],
                    unlockedWeapons = unlockedWeapons,
                ),
                labProgress = LabProgress(metaLevels),
                collection = PlayerCollection(discoveries),
                rebirthProgress = RebirthProgress(rebirthLevel, highestCleared),
            )
        }.getOrNull()
    }

    private fun Boolean.asInt(): Int = if (this) 1 else 0

    private fun List<String>.intAt(index: Int, fallback: Int): Int =
        getOrNull(index)?.toIntOrNull() ?: fallback
}
