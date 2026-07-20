// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.simulation

import kinetickk.feature.game.domain.model.*
import kinetickk.feature.game.domain.protocol.SoundCue
import kotlin.math.roundToInt

internal fun MutableGameState.adjustSetting(row: SettingsRow, direction: Int) {
    settings = when (row) {
        SettingsRow.SFX -> settings.copy(soundEnabled = !settings.soundEnabled)
        SettingsRow.MUSIC -> settings.copy(musicEnabled = !settings.musicEnabled)
        SettingsRow.MASTER_VOLUME -> settings.copy(
            masterVolume = stepPercentage(settings.masterVolume, direction, 0f, 1f),
        )
        SettingsRow.SIMULATION_SPEED -> {
            val current = MutableGameState.SIMULATION_SPEEDS.indices.minByOrNull { kotlin.math.abs(MutableGameState.SIMULATION_SPEEDS[it] - settings.simulationSpeed) } ?: 2
            settings.copy(simulationSpeed = MutableGameState.SIMULATION_SPEEDS[(current + direction).coerceIn(MutableGameState.SIMULATION_SPEEDS.indices)])
        }
        SettingsRow.TEXT_SIZE -> settings.copy(
            textScale = stepPercentage(settings.textScale, direction, 1f, 1.75f),
        )
        SettingsRow.SCREEN_SHAKE -> settings.copy(screenShake = !settings.screenShake)
        SettingsRow.PARTICLES -> {
            val next = (settings.particleDensity.ordinal + direction).coerceIn(ParticleDensity.entries.indices)
            settings.copy(particleDensity = ParticleDensity.entries[next])
        }
        SettingsRow.DAMAGE_NUMBERS -> settings.copy(damageNumbers = !settings.damageNumbers)
        SettingsRow.DAMAGE_NUMBER_SIZE -> {
            val next = (settings.damageNumberSize.ordinal + direction).coerceIn(DamageNumberSize.entries.indices)
            settings.copy(damageNumberSize = DamageNumberSize.entries[next])
        }
        SettingsRow.DAMAGE_NUMBER_FORMAT -> {
            val next = (settings.damageNumberFormat.ordinal + direction).coerceIn(DamageNumberFormat.entries.indices)
            settings.copy(damageNumberFormat = DamageNumberFormat.entries[next])
        }
        SettingsRow.DAMAGE_COLOR_THRESHOLDS -> {
            val current = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices.minByOrNull { index ->
                kotlin.math.abs(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[index] - settings.damageNumberTierThreshold)
            } ?: 2
            val next = (current + direction).coerceIn(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices)
            settings.copy(damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[next])
        }
    }.normalized()
    persist()
    emitSound(SoundCue.UI_CLICK)
}

internal fun MutableGameState.stepPercentage(value: Float, direction: Int, minimum: Float, maximum: Float): Float {
    val nextPercent = (value * 100f).roundToInt() + direction.coerceIn(-1, 1)
    return (nextPercent / 100f).coerceIn(minimum, maximum)
}


internal fun MutableGameState.selectSettingsPage(page: Int) {
    settingsPage = page
    emitSound(SoundCue.UI_CLICK)
}

internal fun MutableGameState.selectArmoryPage(page: Int) {
    armoryPage = page.coerceIn(0, maxArmoryPage)
    emitSound(SoundCue.UI_CLICK)
}

internal fun MutableGameState.selectCodexPage(page: Int) {
    codexPage = page.coerceIn(0, maxCodexPage)
    emitSound(SoundCue.UI_CLICK)
}
