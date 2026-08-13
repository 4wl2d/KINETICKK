// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsReducerTest {
    @Test
    fun rowsMapToClosedProfileAdjustmentsWithoutOptimisticStateChanges() {
        val initial = SettingsState(PlayerPreferences().toRenderModel(), page = 0)
        val increasingAdjustments = listOf(
            SettingsRow.SFX to ProfilePreferenceAdjustment.ToggleSoundEffects,
            SettingsRow.MUSIC to ProfilePreferenceAdjustment.ToggleMusic,
            SettingsRow.MASTER_VOLUME to ProfilePreferenceAdjustment.StepMasterVolume(
                PreferenceAdjustmentDirection.INCREASE,
            ),
            SettingsRow.SIMULATION_SPEED to ProfilePreferenceAdjustment.StepSimulationSpeed(
                PreferenceAdjustmentDirection.INCREASE,
            ),
            SettingsRow.TEXT_SIZE to ProfilePreferenceAdjustment.StepTextScale(
                PreferenceAdjustmentDirection.INCREASE,
            ),
            SettingsRow.SCREEN_SHAKE to ProfilePreferenceAdjustment.ToggleScreenShake,
            SettingsRow.PARTICLES to ProfilePreferenceAdjustment.StepParticleDensity(
                PreferenceAdjustmentDirection.INCREASE,
            ),
            SettingsRow.DAMAGE_NUMBERS to ProfilePreferenceAdjustment.ToggleDamageNumbers,
            SettingsRow.DAMAGE_NUMBER_SIZE to ProfilePreferenceAdjustment.StepDamageNumberSize(
                PreferenceAdjustmentDirection.INCREASE,
            ),
            SettingsRow.DAMAGE_NUMBER_FORMAT to ProfilePreferenceAdjustment.StepDamageNumberFormat(
                PreferenceAdjustmentDirection.INCREASE,
            ),
            SettingsRow.DAMAGE_COLOR_THRESHOLDS to
                ProfilePreferenceAdjustment.StepDamageNumberTierThreshold(
                    PreferenceAdjustmentDirection.INCREASE,
                ),
        )

        increasingAdjustments.forEach { (row, expected) ->
            val reduction = SettingsReducer.reduce(
                initial,
                SettingsAction.Adjust(row, direction = 1),
            )
            assertEquals(initial, reduction.state)
            assertEquals(
                expected,
                assertIs<SettingsEffect.AdjustPreference>(reduction.effects.first()).adjustment,
            )
            assertEquals(
                ProfileAudioCue.UI_CLICK,
                assertIs<SettingsEffect.PlayAudio>(reduction.effects.last()).cue,
            )
        }

        val decrease = SettingsReducer.reduce(
            initial,
            SettingsAction.Adjust(SettingsRow.MASTER_VOLUME, direction = -1),
        )
        assertEquals(
            ProfilePreferenceAdjustment.StepMasterVolume(PreferenceAdjustmentDirection.DECREASE),
            assertIs<SettingsEffect.AdjustPreference>(decrease.effects.first()).adjustment,
        )
    }

    @Test
    fun pageAndBackAreUiOnlyStateTransitions() {
        val initial = SettingsState(PlayerPreferences().toRenderModel(), page = 0)
        val paged = SettingsReducer.reduce(initial, SettingsAction.PageSelected(1))
        assertEquals(1, paged.state.page)
        assertTrue(paged.effects.none { it is SettingsEffect.AdjustPreference })

        val back = SettingsReducer.reduce(paged.state, SettingsAction.Back)
        assertEquals(ProfileAudioCue.UI_CLICK, assertIs<SettingsEffect.PlayAudio>(back.effects[0]).cue)
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
