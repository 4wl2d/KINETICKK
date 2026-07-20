// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.settings.impl

import kinetickk.core.audio.api.AudioCue
import kinetickk.core.profile.api.DamageNumberFormat
import kinetickk.core.profile.api.DamageNumberSize
import kinetickk.core.profile.api.PlayerPreferences
import kinetickk.feature.settings.api.SettingsOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsReducerTest {
    @Test
    fun percentageRowsAdvanceInExactOnePercentStepsAndRequestPersistence() {
        val initial = SettingsState(PlayerPreferences().toRenderModel(), page = 0)

        val volume = SettingsReducer.reduce(
            initial,
            SettingsAction.Adjust(SettingsRow.MASTER_VOLUME, direction = 1),
        )
        assertEquals(0.66f, volume.state.model.preferences.masterVolume)
        assertEquals(
            volume.state.model.preferences,
            assertIs<SettingsEffect.UpdatePreferences>(volume.effects.first()).preferences,
        )
        assertEquals(
            SettingsOutput.Cue(AudioCue.UI_CLICK),
            assertIs<SettingsEffect.Emit>(volume.effects.last()).output,
        )

        val text = SettingsReducer.reduce(
            volume.state,
            SettingsAction.Adjust(SettingsRow.TEXT_SIZE, direction = 1),
        )
        assertEquals(1.26f, text.state.model.preferences.textScale)
    }

    @Test
    fun enumRowsCycleAndClampAtPersistentIdentityBoundaries() {
        val initial = SettingsState(PlayerPreferences().toRenderModel(), page = 0)
        val size = SettingsReducer.reduce(
            initial,
            SettingsAction.Adjust(SettingsRow.DAMAGE_NUMBER_SIZE, direction = 1),
        )
        assertEquals(DamageNumberSize.LARGE, size.state.model.preferences.damageNumberSize)

        val format = SettingsReducer.reduce(
            size.state,
            SettingsAction.Adjust(SettingsRow.DAMAGE_NUMBER_FORMAT, direction = 1),
        )
        assertEquals(DamageNumberFormat.FULL, format.state.model.preferences.damageNumberFormat)

        val threshold = SettingsReducer.reduce(
            format.state,
            SettingsAction.Adjust(SettingsRow.DAMAGE_COLOR_THRESHOLDS, direction = 1),
        )
        assertEquals(100, threshold.state.model.preferences.damageNumberTierThreshold)
    }

    @Test
    fun pageAndBackAreUiOnlyStateTransitions() {
        val initial = SettingsState(PlayerPreferences().toRenderModel(), page = 0)
        val paged = SettingsReducer.reduce(initial, SettingsAction.PageSelected(1))
        assertEquals(1, paged.state.page)
        assertTrue(paged.effects.none { it is SettingsEffect.UpdatePreferences })

        val back = SettingsReducer.reduce(paged.state, SettingsAction.Back)
        assertEquals(SettingsOutput.Cue(AudioCue.UI_CLICK), assertIs<SettingsEffect.Emit>(back.effects[0]).output)
        assertEquals(SettingsOutput.Back, assertIs<SettingsEffect.Emit>(back.effects[1]).output)
    }

    @Test
    fun renderModelNormalizesUntrustedPreferenceNumbers() {
        val model = PlayerPreferences(
            masterVolume = -4f,
            simulationSpeed = 9f,
            textScale = 0.1f,
        ).toRenderModel()

        assertEquals(0f, model.preferences.masterVolume)
        assertEquals(2f, model.preferences.simulationSpeed)
        assertEquals(1f, model.preferences.textScale)
        assertEquals("65%", settingValue(PlayerPreferences(), SettingsRow.MASTER_VOLUME))
        assertEquals("50/200/1K", settingValue(PlayerPreferences(), SettingsRow.DAMAGE_COLOR_THRESHOLDS))
    }
}
