// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsReducerTest {
    @Test
    fun exactVolumeValidatesInputAndRequestsOneAcceptedChangeWithoutClickSpam() {
        val initial = SettingsState(PlayerPreferences().toRenderModel(), 0, SettingsGroup.SOUND)
        for (percent in listOf(0, 37, 100)) {
            val action = SettingsAction.SetMasterVolume(percent)
            val reduction = SettingsReducer.reduce(initial, action)
            assertEquals(initial, reduction.state)
            assertEquals(listOf(SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetMasterVolume(percent))), reduction.effects)
            assertEquals(reduction, SettingsReducer.reduce(initial, action))
        }
        for (percent in listOf(-1, 101, 65, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals(SettingsReduction(initial), SettingsReducer.reduce(initial, SettingsAction.SetMasterVolume(percent)))
        }
    }

    @Test
    fun switchingGroupsResetsThePageWithoutChangingPreferences() {
        val initial = SettingsState(PlayerPreferences().toRenderModel(), page = 1, group = SettingsGroup.GRAPHICS)
        val action = SettingsAction.SelectGroup(SettingsGroup.SOUND)
        val changed = SettingsReducer.reduce(initial, action)
        assertEquals(initial.model, changed.state.model)
        assertEquals(SettingsGroup.SOUND, changed.state.group)
        assertEquals(0, changed.state.page)
        assertTrue(changed.effects.none { it is SettingsEffect.AdjustPreference })
        assertEquals(changed, SettingsReducer.reduce(initial, action))
        assertEquals(SettingsReduction(initial), SettingsReducer.reduce(initial, SettingsAction.SelectGroup(SettingsGroup.GRAPHICS)))
    }

    @Test
    fun languageSelectionRequestsAcceptedProfileChangeAndSameLanguageIsInert() {
        val initial = SettingsState(PlayerPreferences(language = AppLanguage.Russian).toRenderModel(), 0)
        val changed = SettingsReducer.reduce(initial, SettingsAction.SelectLanguage(AppLanguage.English))
        assertEquals(initial, changed.state)
        assertEquals(ProfilePreferenceAdjustment.SetLanguage(AppLanguage.English),
            assertIs<SettingsEffect.AdjustPreference>(changed.effects.first()).adjustment)
        assertEquals(changed, SettingsReducer.reduce(initial, SettingsAction.SelectLanguage(AppLanguage.English)))
        assertTrue(SettingsReducer.reduce(initial, SettingsAction.SelectLanguage(AppLanguage.Russian)).effects.isEmpty())
        assertTrue(SettingsReducer.reduce(initial, SettingsAction.Adjust(SettingsRow.LANGUAGE, 1)).effects.isEmpty())
    }

    @Test
    fun textualSettingsUseTheSelectedLanguage() {
        val russian = PlayerPreferences(language = AppLanguage.Russian, soundEnabled = false)
        val english = russian.copy(language = AppLanguage.English)
        assertEquals("Выкл", settingValue(russian, SettingsRow.SFX))
        assertEquals("Off", settingValue(english, SettingsRow.SFX))
        assertEquals("Норма", settingValue(russian, SettingsRow.PARTICLES))
        assertEquals("Normal", settingValue(english, SettingsRow.PARTICLES))
        assertEquals("Кратко", settingValue(russian, SettingsRow.DAMAGE_NUMBER_FORMAT))
        assertEquals("Compact", settingValue(english, SettingsRow.DAMAGE_NUMBER_FORMAT))
        assertEquals("Справа", settingValue(russian, SettingsRow.RUN_STATISTICS_SIDE))
        assertEquals("Слева", settingValue(russian.copy(runStatisticsOnLeft = true), SettingsRow.RUN_STATISTICS_SIDE))
        assertEquals("Left", settingValue(english.copy(runStatisticsOnLeft = true), SettingsRow.RUN_STATISTICS_SIDE))
    }

    @Test
    fun rowsMapToClosedProfileAdjustmentsWithoutOptimisticStateChanges() {
        val initial = SettingsState(PlayerPreferences().toRenderModel(), page = 0)
        val increasingAdjustments = listOf(
            SettingsRow.RUN_STATISTICS_SIDE to ProfilePreferenceAdjustment.ToggleRunStatisticsSide,
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
    fun segmentedChoicesRequestDirectProfileChoicesAndTheCurrentChoiceIsInert() {
        val preferences = PlayerPreferences(colorVision = ColorVision.TRITAN, particleDensity = ParticleDensity.HIGH)
        val initial = SettingsState(preferences.toRenderModel(), page = 0, group = SettingsGroup.GRAPHICS)
        val expected: Map<SettingsRow, (Int) -> ProfilePreferenceAdjustment> = mapOf(
            SettingsRow.SIMULATION_SPEED to { ProfilePreferenceAdjustment.SetSimulationSpeed(SIMULATION_SPEED_OPTIONS[it]) },
            SettingsRow.PARTICLES to { ProfilePreferenceAdjustment.SetParticleDensity(ParticleDensity.entries[it]) },
            SettingsRow.DAMAGE_NUMBER_SIZE to { ProfilePreferenceAdjustment.SetDamageNumberSize(DamageNumberSize.entries[it]) },
            SettingsRow.DAMAGE_NUMBER_FORMAT to { ProfilePreferenceAdjustment.SetDamageNumberFormat(DamageNumberFormat.entries[it]) },
            SettingsRow.COLOR_VISION to { ProfilePreferenceAdjustment.SetColorVision(ColorVision.entries[it]) },
            SettingsRow.RUN_STATISTICS_SIDE to { ProfilePreferenceAdjustment.ToggleRunStatisticsSide },
            SettingsRow.LANGUAGE to { ProfilePreferenceAdjustment.SetLanguage(AppLanguage.entries[it]) },
        )
        assertEquals(SettingsRow.entries.filter { it.control == SettingsControl.SEGMENTED }.toSet(), expected.keys)
        for ((row, adjustment) in expected) {
            val selected = row.selectedOption(preferences)
            for (option in 0 until row.optionCount()) {
                val reduction = SettingsReducer.reduce(initial, SettingsAction.Select(row, option))
                assertEquals(initial, reduction.state, "$row $option")
                if (option == selected) {
                    assertTrue(reduction.effects.isEmpty(), "$row $option is already selected")
                } else {
                    assertEquals(
                        listOf(SettingsEffect.AdjustPreference(adjustment(option)), SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
                        reduction.effects, "$row $option",
                    )
                }
            }
            for (invalid in listOf(-1, row.optionCount(), Int.MAX_VALUE)) {
                assertEquals(SettingsReduction(initial), SettingsReducer.reduce(initial, SettingsAction.Select(row, invalid)))
            }
        }
        for (row in SettingsRow.entries.filter { it.control != SettingsControl.SEGMENTED }) {
            assertEquals(SettingsReduction(initial), SettingsReducer.reduce(initial, SettingsAction.Select(row, 0)))
        }
        assertTrue(SettingsReducer.reduce(initial, SettingsAction.Adjust(SettingsRow.COLOR_VISION, 1)).effects.isEmpty())
        assertEquals(ColorVision.TRITAN.ordinal, SettingsRow.COLOR_VISION.selectedOption(preferences))
    }

    @Test
    fun infoExplanationTogglesAndClosesWhenTheTabOrPageChanges() {
        val initial = SettingsState(PlayerPreferences().toRenderModel(), page = 0, group = SettingsGroup.GRAPHICS)
        val opened = SettingsReducer.reduce(initial, SettingsAction.ToggleInfo(SettingsRow.COLOR_VISION))
        assertEquals(SettingsRow.COLOR_VISION, opened.state.info)
        assertTrue(opened.effects.isEmpty())
        assertEquals(SettingsRow.PARTICLES, SettingsReducer.reduce(opened.state, SettingsAction.ToggleInfo(SettingsRow.PARTICLES)).state.info)
        assertEquals(null, SettingsReducer.reduce(opened.state, SettingsAction.ToggleInfo(SettingsRow.COLOR_VISION)).state.info)
        assertEquals(null, SettingsReducer.reduce(opened.state, SettingsAction.SelectGroup(SettingsGroup.SOUND)).state.info)
        assertEquals(null, SettingsReducer.reduce(opened.state, SettingsAction.PageSelected(1)).state.info)
        assertEquals(opened.state.model, SettingsReducer.reduce(opened.state, SettingsAction.ToggleInfo(SettingsRow.COLOR_VISION)).state.model)
    }

    @Test
    fun rowsHaveStableTagsAndDistinctOptionIds() {
        assertEquals("colorvision", SettingsRow.COLOR_VISION.tagId)
        assertEquals(listOf("default", "protan", "deutan", "tritan", "mono"),
            (0 until SettingsRow.COLOR_VISION.optionCount()).map { SettingsRow.COLOR_VISION.optionId(it) })
        assertEquals(listOf("ru", "en"), (0 until SettingsRow.LANGUAGE.optionCount()).map { SettingsRow.LANGUAGE.optionId(it) })
        assertEquals(listOf("75", "100", "115", "135", "160", "200"),
            (0 until SettingsRow.SIMULATION_SPEED.optionCount()).map { SettingsRow.SIMULATION_SPEED.optionId(it) })
        for (row in SettingsRow.entries.filter { it.control == SettingsControl.SEGMENTED }) {
            val ids = (0 until row.optionCount()).map { row.optionId(it) }
            assertEquals(ids.size, ids.toSet().size, "$row")
            for (language in AppLanguage.entries) {
                (0 until row.optionCount()).forEach { assertTrue(row.optionLabel(it, language).isNotBlank()) }
            }
        }
        assertEquals(SettingsRow.entries.toSet(), SettingsGroup.entries.flatMap { it.rows }.toSet(), "every row is on a tab")
        assertEquals(SettingsRow.entries.size, SettingsGroup.entries.sumOf { it.rows.size }, "no row is on two tabs")
    }

    @Test
    fun colorVisionReadsInBothLanguages() {
        assertEquals("Default", settingValue(PlayerPreferences(language = AppLanguage.English), SettingsRow.COLOR_VISION))
        assertEquals("Моно", settingValue(PlayerPreferences(colorVision = ColorVision.MONO), SettingsRow.COLOR_VISION))
        assertEquals("1.15×", SettingsRow.SIMULATION_SPEED.optionLabel(2, AppLanguage.English))
        assertEquals("0,75×", SettingsRow.SIMULATION_SPEED.optionLabel(0, AppLanguage.Russian))
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
        assertEquals("50/200/1 тыс.", settingValue(PlayerPreferences(), SettingsRow.DAMAGE_COLOR_THRESHOLDS))
        assertEquals("50/200/1K", settingValue(PlayerPreferences(language = AppLanguage.English), SettingsRow.DAMAGE_COLOR_THRESHOLDS))
    }
}
