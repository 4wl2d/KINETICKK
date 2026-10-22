// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.INTERFACE_SCALE_OPTIONS
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.settings.api.SettingsLiveWindow
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        assertEquals("Вкл", settingValue(russian, SettingsRow.MINIMAP))
        assertEquals("Off", settingValue(english.copy(minimapEnabled = false), SettingsRow.MINIMAP))
        assertEquals("Миникарта", SettingsRow.MINIMAP.label(AppLanguage.Russian))
        assertEquals("Minimap", SettingsRow.MINIMAP.label(AppLanguage.English))
        assertTrue(SettingsRow.MINIMAP in SettingsGroup.INTERFACE.rows)
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
            SettingsRow.MINIMAP to ProfilePreferenceAdjustment.ToggleMinimap,
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
        // Every segmented row is offered: desktop window modes plus Android refresh rates.
        val platform = SettingsPlatform(DESKTOP_SUPPORT.copy(refreshRatesHz = listOf(120, 60)))
        val initial = SettingsState(preferences.toRenderModel(), page = 0, group = SettingsGroup.GRAPHICS, platform = platform)
        val expected: Map<SettingsRow, (Int) -> ProfilePreferenceAdjustment> = mapOf(
            SettingsRow.SIMULATION_SPEED to { ProfilePreferenceAdjustment.SetSimulationSpeed(SIMULATION_SPEED_OPTIONS[it]) },
            SettingsRow.PARTICLES to { ProfilePreferenceAdjustment.SetParticleDensity(ParticleDensity.entries[it]) },
            SettingsRow.DAMAGE_NUMBER_SIZE to { ProfilePreferenceAdjustment.SetDamageNumberSize(DamageNumberSize.entries[it]) },
            SettingsRow.DAMAGE_NUMBER_FORMAT to { ProfilePreferenceAdjustment.SetDamageNumberFormat(DamageNumberFormat.entries[it]) },
            SettingsRow.COLOR_VISION to { ProfilePreferenceAdjustment.SetColorVision(ColorVision.entries[it]) },
            SettingsRow.RUN_STATISTICS_SIDE to { ProfilePreferenceAdjustment.ToggleRunStatisticsSide },
            SettingsRow.LANGUAGE to { ProfilePreferenceAdjustment.SetLanguage(AppLanguage.entries[it]) },
            SettingsRow.WINDOW_MODE to { ProfilePreferenceAdjustment.SetWindowMode(WindowMode.entries[it]) },
            SettingsRow.REFRESH_RATE to { ProfilePreferenceAdjustment.SetRefreshRate(listOf(0, 120, 60)[it]) },
            SettingsRow.INTERFACE_SCALE to { ProfilePreferenceAdjustment.SetInterfaceScale(INTERFACE_SCALE_OPTIONS[it]) },
            SettingsRow.REACTION_EFFECTS to { ProfilePreferenceAdjustment.SetReactionEffects(ReactionEffectsLevel.entries[it]) },
        )
        assertEquals(SettingsRow.entries.filter { it.control == SettingsControl.SEGMENTED }.toSet(), expected.keys)
        for ((row, adjustment) in expected) {
            val selected = row.selectedOption(preferences, platform)
            for (option in 0 until row.optionCount(platform)) {
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
            for (invalid in listOf(-1, row.optionCount(platform), Int.MAX_VALUE)) {
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
    fun aPressOnTheOpenSlipClosesItWithoutTouchingAPreference() {
        val initial = SettingsState(PlayerPreferences().toRenderModel(), page = 0, group = SettingsGroup.GRAPHICS)
        val opened = SettingsReducer.reduce(initial, SettingsAction.ToggleInfo(SettingsRow.COLOR_VISION)).state
        val closed = SettingsReducer.reduce(opened, SettingsAction.CloseInfo)
        assertEquals(opened.copy(info = null), closed.state)
        assertTrue(closed.effects.isEmpty(), "no preference change, sound or output")
        // Already closed (the slip was shown by hover or focus): still inert.
        assertEquals(SettingsReduction(initial), SettingsReducer.reduce(initial, SettingsAction.CloseInfo))
    }

    @Test
    fun aPinnedOrFocusedSlipTakesPressesButAHoveredOneLetsThemThrough() {
        val colorVision = SettingsTarget.Info(SettingsRow.COLOR_VISION)
        val shake = SettingsTarget.Info(SettingsRow.SCREEN_SHAKE)
        // Pinned by a tap (touch: nothing hovers).
        assertTrue(settingsSlipTakesPresses(focus = null, hover = null, info = SettingsRow.COLOR_VISION))
        assertTrue(settingsSlipTakesPresses(focus = null, hover = colorVision, info = SettingsRow.COLOR_VISION))
        // Held by keyboard focus.
        assertTrue(settingsSlipTakesPresses(focus = colorVision, hover = null, info = null))
        // Shown only while the pointer rests on its (!), or another row's hover replaces the pinned one.
        assertFalse(settingsSlipTakesPresses(focus = null, hover = colorVision, info = null))
        assertFalse(settingsSlipTakesPresses(focus = null, hover = shake, info = SettingsRow.COLOR_VISION))
        assertFalse(settingsSlipTakesPresses(focus = null, hover = SettingsTarget.Back, info = null))
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

    @Test
    fun displayRowsRequestTypedAdjustmentsAndBrowserFullscreenStaysLive() {
        val platform = SettingsPlatform(DESKTOP_SUPPORT.copy(immersive = true, browserFullscreen = true), browserFullscreen = true)
        val initial = SettingsState(PlayerPreferences().toRenderModel(), page = 0, group = SettingsGroup.DISPLAY, platform = platform)
        val toggles = mapOf(
            SettingsRow.PAUSE_ON_FOCUS_LOSS to ProfilePreferenceAdjustment.TogglePauseOnFocusLoss,
            SettingsRow.VSYNC to ProfilePreferenceAdjustment.ToggleVsync,
            SettingsRow.IMMERSIVE to ProfilePreferenceAdjustment.ToggleImmersive,
            SettingsRow.SHOW_FPS to ProfilePreferenceAdjustment.ToggleShowFps,
        )
        for ((row, adjustment) in toggles) {
            assertEquals(
                listOf(SettingsEffect.AdjustPreference(adjustment), SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
                SettingsReducer.reduce(initial, SettingsAction.Adjust(row, 1)).effects, "$row",
            )
        }
        // Fullscreen is the browser's live state: the press asks the host for the opposite and writes no preference.
        assertEquals(
            listOf(SettingsEffect.SetBrowserFullscreen(false), SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
            SettingsReducer.reduce(initial, SettingsAction.Adjust(SettingsRow.FULLSCREEN, 1)).effects,
        )
        assertEquals(
            SettingsEffect.SetBrowserFullscreen(true),
            SettingsReducer.reduce(initial.copy(platform = platform.copy(browserFullscreen = false)), SettingsAction.Adjust(SettingsRow.FULLSCREEN, 1)).effects.first(),
        )
        assertTrue(SettingsRow.FULLSCREEN.isOn(PlayerPreferences(), platform))
        // Rows this platform does not offer are inert.
        val bare = initial.copy(platform = SettingsPlatform.DEFAULT)
        for (row in listOf(SettingsRow.VSYNC, SettingsRow.IMMERSIVE, SettingsRow.FULLSCREEN, SettingsRow.WINDOW_SIZE)) {
            assertEquals(SettingsReduction(bare), SettingsReducer.reduce(bare, SettingsAction.Adjust(row, 1)), "$row")
        }
        assertEquals(SettingsReduction(bare), SettingsReducer.reduce(bare, SettingsAction.Select(SettingsRow.WINDOW_MODE, 2)))
        assertEquals(SettingsReduction(bare), SettingsReducer.reduce(bare, SettingsAction.Select(SettingsRow.REFRESH_RATE, 0)))
    }

    @Test
    fun frameRateLimitStepsThroughTheListedLimitsUpToUnlimited() {
        assertEquals(listOf(30, 60, 90, 120, 144, 165, 240, 0), FRAME_RATE_CAP_STEPS)
        fun step(cap: Int, direction: Int): List<SettingsEffect> = SettingsReducer.reduce(
            SettingsState(PlayerPreferences(frameRateCap = cap).toRenderModel(), page = 0, group = SettingsGroup.DISPLAY),
            SettingsAction.Adjust(SettingsRow.FRAME_RATE_CAP, direction),
        ).effects
        assertEquals(SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetFrameRateCap(60)), step(30, 1).first())
        assertEquals(SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetFrameRateCap(0)), step(240, 1).first())
        assertEquals(SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetFrameRateCap(240)), step(0, -1).first())
        // Ends: Unlimited is the top, 30 the bottom; a press past either end does nothing (no click).
        assertTrue(step(0, 1).isEmpty())
        assertTrue(step(30, -1).isEmpty())
        val preferences = PlayerPreferences(frameRateCap = 0)
        assertEquals(1f, SettingsRow.FRAME_RATE_CAP.sliderValue(preferences))
        assertFalse(SettingsRow.FRAME_RATE_CAP.canStep(preferences, 1))
        assertTrue(SettingsRow.FRAME_RATE_CAP.canStep(preferences, -1))
        assertEquals("Unlimited", SettingsRow.FRAME_RATE_CAP.sliderOutput(preferences, AppLanguage.English))
        assertEquals("144 к/с", SettingsRow.FRAME_RATE_CAP.sliderOutput(PlayerPreferences(frameRateCap = 144), AppLanguage.Russian))
    }

    @Test
    fun windowSizeStepsThroughOfferedPresetsOnlyInWindowedMode() {
        val offered = listOf(WindowSizePreset.W1280X720, WindowSizePreset.W1280X800, WindowSizePreset.W1440X900, WindowSizePreset.W1600X900)
        val platform = SettingsPlatform(DESKTOP_SUPPORT.copy(windowSizes = offered))
        fun reduce(preferences: PlayerPreferences, direction: Int, live: SettingsLiveWindow? = null) = SettingsReducer.reduce(
            SettingsState(preferences.toRenderModel(), page = 0, group = SettingsGroup.DISPLAY, platform = platform.copy(live = live)),
            SettingsAction.Adjust(SettingsRow.WINDOW_SIZE, direction),
        ).effects
        val windowed = PlayerPreferences()
        // 1366×768 is not offered here, so + skips to 1440×900; the largest offered preset is the end.
        assertEquals(SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetWindowSize(WindowSizePreset.W1440X900)), reduce(windowed, 1).first())
        assertEquals(SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetWindowSize(WindowSizePreset.W1280X720)), reduce(windowed, -1).first())
        assertTrue(reduce(windowed.copy(windowSize = WindowSizePreset.W1600X900), 1).isEmpty())
        assertTrue(reduce(windowed.copy(windowSize = WindowSizePreset.W1280X720), -1).isEmpty())
        // Borderless and fullscreen disable the row.
        for (mode in listOf(WindowMode.BORDERLESS, WindowMode.FULLSCREEN)) {
            assertTrue(reduce(windowed.copy(windowMode = mode), 1).isEmpty(), "$mode")
            assertFalse(SettingsRow.WINDOW_SIZE.isEnabled(windowed.copy(windowMode = mode)))
        }
        // A drag-resized window steps from its live size to the nearest offered preset in that direction.
        val resized = SettingsLiveWindow(WindowMode.WINDOWED, 1500, 950)
        assertEquals(SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetWindowSize(WindowSizePreset.W1440X900)), reduce(windowed, -1, resized).first())
        assertEquals(SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetWindowSize(WindowSizePreset.W1600X900)), reduce(windowed, 1, resized).first())
        assertEquals("1500×950", SettingsRow.WINDOW_SIZE.sliderOutput(windowed, AppLanguage.English, platform.copy(live = resized)))
        // When that nearest preset is the stored one, the press applies it again instead of a no-op write.
        assertEquals(SettingsEffect.ReapplyDisplay, reduce(windowed, -1, SettingsLiveWindow(WindowMode.WINDOWED, 1300, 820)).first())
        // Within 1 dp the live size counts as the stored preset.
        assertNull(platform.copy(live = SettingsLiveWindow(WindowMode.WINDOWED, 1281, 799)).liveSizeOverride(windowed))
        assertNotNull(platform.copy(live = SettingsLiveWindow(WindowMode.WINDOWED, 1282, 800)).liveSizeOverride(windowed))
    }

    @Test
    fun pressingTheStoredWindowModeReappliesItOnlyWhileTheSystemShowsAnother() {
        val platform = SettingsPlatform(DESKTOP_SUPPORT)
        val state = SettingsState(PlayerPreferences().toRenderModel(), page = 0, group = SettingsGroup.DISPLAY, platform = platform)
        val windowed = WindowMode.WINDOWED.ordinal
        assertEquals(SettingsReduction(state), SettingsReducer.reduce(state, SettingsAction.Select(SettingsRow.WINDOW_MODE, windowed)))
        val leftFullscreen = state.copy(platform = platform.copy(live = SettingsLiveWindow(WindowMode.FULLSCREEN, 1920, 1080)))
        assertEquals(
            listOf(SettingsEffect.ReapplyDisplay, SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
            SettingsReducer.reduce(leftFullscreen, SettingsAction.Select(SettingsRow.WINDOW_MODE, windowed)).effects,
        )
        assertEquals(WindowMode.FULLSCREEN, leftFullscreen.platform.liveModeOverride(PlayerPreferences()))
    }

    @Test
    fun interfaceScalesAreOfferedWhileTheScaledWindowKeepsItsMinimumSides() {
        assertEquals(listOf(85, 100, 115, 130), settingsInterfaceScales(1280f, 800f, touchPrimary = false))
        // 720 × 540 at 130 % is 554 dp long: below 568.
        assertEquals(listOf(85, 100, 115), settingsInterfaceScales(720f, 540f, touchPrimary = false))
        // Touch never offers 85 %; 390 dp at 130 % is 300 dp short: below 320.
        assertEquals(listOf(100, 115), settingsInterfaceScales(390f, 844f, touchPrimary = true))
        // Boundaries: 115 % needs a 368 dp short side (320 × 1.15) and a 653.2 dp long one.
        assertTrue(115 in settingsInterfaceScales(368f, 653.2f, touchPrimary = false))
        assertFalse(115 in settingsInterfaceScales(367.9f, 900f, touchPrimary = false))
        assertFalse(115 in settingsInterfaceScales(400f, 653.1f, touchPrimary = false))
        // A window too small for every scale still offers 100 %.
        assertEquals(listOf(100), settingsInterfaceScales(300f, 400f, touchPrimary = true))
        // The applied scale is the stored one clamped to what is offered; the stored value is untouched.
        val platform = SettingsPlatform(interfaceScales = listOf(85, 100, 115))
        val preferences = PlayerPreferences(interfaceScalePercent = 130)
        assertEquals(115, platform.appliedInterfaceScale(130))
        assertEquals(2, SettingsRow.INTERFACE_SCALE.selectedOption(preferences, platform))
        assertEquals(listOf("85", "100", "115"), (0 until SettingsRow.INTERFACE_SCALE.optionCount(platform)).map { SettingsRow.INTERFACE_SCALE.optionId(it, platform) })
        assertEquals(100, SettingsPlatform(interfaceScales = listOf(100, 115)).appliedInterfaceScale(85))
    }

    @Test
    fun refreshRateChoosesAutoOrAnOfferedRateAndShowsTheMonitorRateOnDesktop() {
        val android = SettingsPlatform(ANDROID_SUPPORT)
        assertEquals(listOf("auto", "120", "90", "60"), (0 until SettingsRow.REFRESH_RATE.optionCount(android)).map { SettingsRow.REFRESH_RATE.optionId(it, android) })
        assertEquals(listOf("Авто", "120 Гц", "90 Гц", "60 Гц"), (0 until 4).map { SettingsRow.REFRESH_RATE.optionLabel(it, AppLanguage.Russian, android) })
        assertEquals(2, SettingsRow.REFRESH_RATE.selectedOption(PlayerPreferences(refreshRateHz = 90), android))
        // A stored rate this display does not offer is applied (and shown) as Auto.
        assertEquals(0, SettingsRow.REFRESH_RATE.selectedOption(PlayerPreferences(refreshRateHz = 144), android))
        val desktop = SettingsPlatform(DESKTOP_SUPPORT)
        assertEquals(0, SettingsRow.REFRESH_RATE.optionCount(desktop))
        assertEquals("144 Hz", settingValue(PlayerPreferences(), SettingsRow.REFRESH_RATE, AppLanguage.English, desktop))
        assertEquals("—", settingValue(PlayerPreferences(), SettingsRow.REFRESH_RATE, AppLanguage.English, SettingsPlatform(DESKTOP_SUPPORT.copy(monitorRefreshHz = 0))))
        assertTrue(SettingsRow.REFRESH_RATE.about(AppLanguage.English, desktop).startsWith("Your monitor"))
        assertTrue(SettingsRow.REFRESH_RATE.about(AppLanguage.English, android).startsWith("How often"))
    }

    @Test
    fun aKeyCellCapturesTheNextBindableKeyAndSwapsWithItsHolder() {
        val idle = SettingsState(PlayerPreferences().toRenderModel(), page = 0, group = SettingsGroup.CONTROLS)
        val begun = SettingsReducer.reduce(idle, SettingsAction.BeginCapture(GameAction.DASH))
        assertEquals(GameAction.DASH, begun.state.capture)
        assertEquals(listOf(SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)), begun.effects)
        // A free key: one typed adjustment, the confirm sound, the announcement; capture ends.
        val bound = SettingsReducer.reduce(begun.state, SettingsAction.CaptureKey(BindableKey.F, at = 2f))
        assertEquals(null, bound.state.capture)
        assertEquals(null, bound.state.swapped)
        assertEquals(begun.state.model, bound.state.model, "no optimistic change: Profile decides")
        assertEquals(
            listOf(
                SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetKeyBinding(GameAction.DASH, BindableKey.F)),
                SettingsEffect.PlayAudio(ProfileAudioCue.PURCHASE),
                SettingsEffect.Announce(SettingsAnnouncement.KeyBound(GameAction.DASH, BindableKey.F)),
            ),
            bound.effects,
        )
        // A key another action holds: that action takes Dash's old key, and its row is marked.
        val swap = SettingsReducer.reduce(begun.state, SettingsAction.CaptureKey(BindableKey.Z, at = 3.5f))
        assertEquals(GameAction.SKILL_1, swap.state.swapped)
        assertEquals(3.5f, swap.state.swappedAt)
        assertEquals(
            SettingsEffect.Announce(SettingsAnnouncement.KeyBound(GameAction.DASH, BindableKey.Z, GameAction.SKILL_1, BindableKey.SPACE)),
            swap.effects.last(),
        )
        assertEquals(
            "Dash set to Z. Skill 1 moved to Space",
            settingsAnnouncementText(SettingsAnnouncement.KeyBound(GameAction.DASH, BindableKey.Z, GameAction.SKILL_1, BindableKey.SPACE), AppLanguage.English),
        )
        assertEquals(
            "Рывок: Z. Навык 1: перенесено на Пробел",
            settingsAnnouncementText(SettingsAnnouncement.KeyBound(GameAction.DASH, BindableKey.Z, GameAction.SKILL_1, BindableKey.SPACE), AppLanguage.Russian),
        )
        // The action's own key ends the capture with nothing to change.
        assertEquals(SettingsReduction(begun.state.copy(capture = null)), SettingsReducer.reduce(begun.state, SettingsAction.CaptureKey(BindableKey.SPACE, at = 1f)))
        // A key that can never be bound: refused sound, flash time, capture continues.
        val refused = SettingsReducer.reduce(begun.state, SettingsAction.CaptureRejected(at = 4f))
        assertEquals(GameAction.DASH, refused.state.capture)
        assertEquals(4f, refused.state.rejectAt)
        assertEquals(
            listOf(SettingsEffect.PlayAudio(ProfileAudioCue.REFUSED), SettingsEffect.Announce(SettingsAnnouncement.KeyUnavailable)),
            refused.effects,
        )
        // Escape cancels: nothing changes.
        assertEquals(SettingsReduction(begun.state.copy(capture = null)), SettingsReducer.reduce(begun.state, SettingsAction.CancelCapture))
    }

    @Test
    fun whileCapturingAnyOtherPressOnlyEndsTheCapture() {
        val capturing = SettingsState(
            PlayerPreferences().toRenderModel(), page = 0, group = SettingsGroup.CONTROLS, capture = GameAction.APEX,
        )
        val consumed = listOf(
            SettingsAction.SelectGroup(SettingsGroup.GAME), SettingsAction.PageSelected(1), SettingsAction.Back,
            SettingsAction.BeginCapture(GameAction.DASH), SettingsAction.ToggleInfo(SettingsRow.RESET_BINDINGS),
            SettingsAction.ArmReset, SettingsAction.Adjust(SettingsRow.SHOW_FPS, 1), SettingsAction.CloseInfo,
        )
        for (action in consumed) {
            assertEquals(SettingsReduction(capturing.copy(capture = null)), SettingsReducer.reduce(capturing, action), "$action")
        }
        // A press on the capturing cell keeps waiting.
        assertEquals(SettingsReduction(capturing), SettingsReducer.reduce(capturing, SettingsAction.BeginCapture(GameAction.APEX)))
        // Capture actions that arrive after the capture ended are stale and inert.
        val idle = capturing.copy(capture = null)
        for (action in listOf(SettingsAction.CaptureKey(BindableKey.K, 1f), SettingsAction.CaptureRejected(1f), SettingsAction.CancelCapture)) {
            assertEquals(SettingsReduction(idle), SettingsReducer.reduce(idle, action), "$action")
        }
        // Without a keyboard there are no key rows to capture for.
        val touch = idle.copy(platform = SettingsPlatform(ANDROID_SUPPORT))
        assertEquals(SettingsReduction(touch), SettingsReducer.reduce(touch, SettingsAction.BeginCapture(GameAction.DASH)))
        assertEquals(SettingsReduction(touch), SettingsReducer.reduce(touch, SettingsAction.SelectGroup(SettingsGroup.CONTROLS)))
    }

    @Test
    fun resetKeysNeedsTwoPressesAndAnyOtherActionDisarmsIt() {
        val defaults = SettingsState(PlayerPreferences().toRenderModel(), page = 0, group = SettingsGroup.CONTROLS)
        // Every key is its default: the button is disabled.
        assertEquals(SettingsReduction(defaults), SettingsReducer.reduce(defaults, SettingsAction.ArmReset))
        assertFalse(defaults.resetAvailable)
        val rebound = defaults.copy(
            model = PlayerPreferences(keyBindings = KeyBindings.DEFAULT.with(GameAction.DASH, BindableKey.F)).toRenderModel(),
        )
        val armed = SettingsReducer.reduce(rebound, SettingsAction.ArmReset)
        assertTrue(armed.state.resetArmed)
        assertEquals(listOf(SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)), armed.effects)
        // The second press of the same button confirms.
        assertEquals(SettingsAction.ConfirmReset, SettingsTarget.Reset.toAction(resetArmed = true))
        assertEquals(SettingsAction.ArmReset, SettingsTarget.Reset.toAction(resetArmed = false))
        val confirmed = SettingsReducer.reduce(armed.state, SettingsAction.ConfirmReset)
        assertFalse(confirmed.state.resetArmed)
        assertEquals(
            listOf(
                SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.ResetKeyBindings),
                SettingsEffect.PlayAudio(ProfileAudioCue.PURCHASE),
                SettingsEffect.Announce(SettingsAnnouncement.KeysReset),
            ),
            confirmed.effects,
        )
        // Confirm without arming does nothing.
        assertEquals(SettingsReduction(rebound), SettingsReducer.reduce(rebound, SettingsAction.ConfirmReset))
        // Any other action disarms: a tab, a page, an explanation, a toggle, a key cell.
        for (action in listOf(
            SettingsAction.SelectGroup(SettingsGroup.GAME), SettingsAction.PageSelected(1), SettingsAction.ToggleInfo(SettingsRow.RESET_BINDINGS),
            SettingsAction.Adjust(SettingsRow.SHOW_FPS, 1), SettingsAction.BeginCapture(GameAction.PAUSE),
        )) {
            assertFalse(SettingsReducer.reduce(armed.state, action).state.resetArmed, "$action")
        }
    }

    @Test
    fun newRowsHaveTheirSpecifiedTagsAndBindableKeyLabels() {
        assertEquals(
            listOf("pauseonfocusloss", "windowmode", "windowsize", "fullscreen", "vsync", "framecap", "refreshrate", "immersive",
                "interfacescale", "showfps", "reactioneffects"),
            listOf(SettingsRow.PAUSE_ON_FOCUS_LOSS, SettingsRow.WINDOW_MODE, SettingsRow.WINDOW_SIZE, SettingsRow.FULLSCREEN,
                SettingsRow.VSYNC, SettingsRow.FRAME_RATE_CAP, SettingsRow.REFRESH_RATE, SettingsRow.IMMERSIVE,
                SettingsRow.INTERFACE_SCALE, SettingsRow.SHOW_FPS, SettingsRow.REACTION_EFFECTS).map { it.tagId },
        )
        assertEquals("bind.dash", SettingsRow.BIND_DASH.tagId)
        assertEquals("bind.skill_1", SettingsRow.BIND_SKILL_1.tagId)
        assertEquals("bind.performance", SettingsRow.BIND_PERFORMANCE.tagId)
        assertEquals("resetbindings", SettingsRow.RESET_BINDINGS.tagId)
        // One key row per rebindable action, in the action order, then Reset keys.
        assertEquals(GameAction.entries.map { it.bindRow }.toSet(), SettingsGroup.CONTROLS.rows.dropLast(1).toSet())
        assertEquals(SettingsRow.RESET_BINDINGS, SettingsGroup.CONTROLS.rows.last())
        GameAction.entries.forEach { assertEquals(it, it.bindRow.boundAction) }
        assertEquals("Space", settingValue(PlayerPreferences(language = AppLanguage.English), SettingsRow.BIND_DASH))
        assertEquals("Пробел", settingValue(PlayerPreferences(), SettingsRow.BIND_DASH))
        assertEquals("Навык 2", SettingsRow.BIND_SKILL_2.label(AppLanguage.Russian))
        assertEquals("Choice 4", SettingsRow.BIND_CHOICE_4.label(AppLanguage.English))
        assertEquals("Takes card 4 of the offer.", SettingsRow.BIND_CHOICE_4.about(AppLanguage.English))
        assertEquals(listOf("full", "reduced", "minimal"), (0 until 3).map { SettingsRow.REACTION_EFFECTS.optionId(it) })
        assertEquals(listOf("windowed", "borderless", "fullscreen"), (0 until 3).map { SettingsRow.WINDOW_MODE.optionId(it) })
    }
}
