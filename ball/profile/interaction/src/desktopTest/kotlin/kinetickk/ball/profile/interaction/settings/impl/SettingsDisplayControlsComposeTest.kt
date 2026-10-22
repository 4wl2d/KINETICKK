// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.profile.api.FRAME_RATE_CAP_OPTIONS
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplayPort
import kinetickk.ball.profile.interaction.settings.api.SettingsLiveWindow
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Display and Controls rows through the real feature: canvas presses, keys and accessibility nodes. */
@OptIn(ExperimentalTestApi::class)
class SettingsDisplayControlsComposeTest {
    private val waiting = "Waiting for a key"

    @Test
    fun aKeyCellBindsTheNextKeyRefusesUnbindableKeysSwapsAndResets() = settings(SettingsDisplayPort.None) { port, outputs, _ ->
        click("kinetickk.settings.group.controls")
        val dash = onNodeWithTag("kinetickk.settings.bind.dash.key")
        dash.assertContentDescriptionEquals("Dash: Space")
        // Escape cancels a capture: no change, and the shell never sees it (Settings stays open).
        click("kinetickk.settings.bind.dash.key")
        assertEquals(waiting, dash.state())
        key(Key.Escape)
        assertNull(dash.state())
        assertTrue(port.adjustments.isEmpty())
        assertTrue(SettingsOutput.Back !in outputs)
        // Tab can never be bound: refused, announced, and the cell keeps waiting.
        click("kinetickk.settings.bind.dash.key")
        key(Key.Tab)
        assertEquals(waiting, dash.state())
        assertEquals("Key not available", announcement())
        assertTrue(port.adjustments.isEmpty())
        // Z belongs to Skill 1: Dash takes it and Skill 1 takes Space.
        key(Key.Z)
        assertNull(dash.state())
        assertEquals(BindableKey.Z, port.preferences.keyBindings.key(GameAction.DASH))
        assertEquals(BindableKey.SPACE, port.preferences.keyBindings.key(GameAction.SKILL_1))
        dash.assertContentDescriptionEquals("Dash: Z")
        onNodeWithTag("kinetickk.settings.bind.skill_1.key").assertContentDescriptionEquals("Skill 1: Space")
        assertEquals("Dash set to Z. Skill 1 moved to Space", announcement())
        assertEquals(listOf<ProfilePreferenceAdjustment>(ProfilePreferenceAdjustment.SetKeyBinding(GameAction.DASH, BindableKey.Z)), port.adjustments)
        // A press anywhere else ends the capture and does nothing more: the tab stays.
        click("kinetickk.settings.bind.dash.key")
        click("kinetickk.settings.group.game")
        assertNull(dash.state())
        onNodeWithTag("kinetickk.settings.group.controls").assertIsSelected()
        // Space on the focused cell starts a capture; Space's own repeats while held choose nothing.
        dash.performSemanticsAction(SemanticsActions.RequestFocus)
        onRoot().performKeyInput { keyDown(Key.Spacebar) }
        settle()
        // Held past the repeat delay: the injected repeats reach Settings while it waits.
        onRoot().performKeyInput { advanceEventTime(700); keyUp(Key.Spacebar) }
        settle()
        assertEquals(waiting, dash.state())
        assertEquals(1, port.adjustments.size)
        assertEquals("Dash set to Z. Skill 1 moved to Space", announcement())
        key(Key.F)
        assertEquals(BindableKey.F, port.preferences.keyBindings.key(GameAction.DASH))
        // Reset keys: the first press arms Confirm, the second restores every default.
        val reset = onNodeWithTag("kinetickk.settings.resetbindings.action")
        reset.assertContentDescriptionEquals("Reset").assertIsEnabled()
        click("kinetickk.settings.resetbindings.action")
        reset.assertContentDescriptionEquals("Confirm")
        assertEquals(KeyBindings.DEFAULT.with(GameAction.DASH, BindableKey.F).with(GameAction.SKILL_1, BindableKey.SPACE), port.preferences.keyBindings)
        click("kinetickk.settings.resetbindings.action")
        assertEquals(KeyBindings.DEFAULT, port.preferences.keyBindings)
        assertEquals("Keys reset", announcement())
        reset.assertContentDescriptionEquals("Reset").assertIsNotEnabled()
        // Every accepted change was reported to the shell, which re-reads preferences on it.
        assertEquals(3, outputs.count { it is SettingsOutput.LanguageChanged })
    }

    @Test
    fun desktopDisplayRowsWriteTypedChoicesAndReapplyOverAWindowTheSystemChanged() {
        val support = DESKTOP_SUPPORT
        val display = SettingsTestDisplayPort(support)
        settings(display) { port, _, _ ->
            click("kinetickk.settings.group.display")
            // The refresh rate is a readout: no control, its (!) carries the value.
            assertTrue(onAllNodesWithTag("kinetickk.settings.refreshrate.auto").fetchSemanticsNodes().isEmpty())
            assertEquals("144 Hz", onNodeWithTag("kinetickk.settings.refreshrate.info").state())
            // Window size steps through the offered presets, only in windowed mode.
            click("kinetickk.settings.windowsize.increase")
            assertEquals(WindowSizePreset.W1366X768, port.preferences.windowSize)
            click("kinetickk.settings.windowmode.borderless")
            assertEquals(WindowMode.BORDERLESS, port.preferences.windowMode)
            onNodeWithTag("kinetickk.settings.windowsize.increase").assertIsNotEnabled()
            click("kinetickk.settings.windowsize.increase")
            assertEquals(WindowSizePreset.W1366X768, port.preferences.windowSize)
            click("kinetickk.settings.windowmode.windowed")
            onNodeWithTag("kinetickk.settings.windowsize.increase").assertIsEnabled()
            // The OS took the window to fullscreen: pressing the stored Windowed applies it again, no write.
            display.live = SettingsLiveWindow(WindowMode.FULLSCREEN, 1920, 1080)
            val writes = port.adjustments.size
            click("kinetickk.settings.group.game")
            click("kinetickk.settings.group.display")
            click("kinetickk.settings.windowmode.windowed")
            assertEquals(1, display.reapplied)
            assertEquals(writes, port.adjustments.size)
            display.live = null
            settle()
            // Frame limit: Unlimited is the top; − steps to 240.
            onNodeWithTag("kinetickk.settings.framecap.increase").assertIsEnabled()
            click("kinetickk.settings.framecap.decrease")
            assertEquals(FRAME_RATE_CAP_OPTIONS.last(), port.preferences.frameRateCap)
            click("kinetickk.settings.vsync.toggle")
            assertEquals(false, port.preferences.vsync)
            click("kinetickk.settings.showfps.toggle")
            assertEquals(true, port.preferences.showFps)
            click("kinetickk.settings.interfacescale.130")
            assertEquals(130, port.preferences.interfaceScalePercent)
        }
    }

    @Test
    fun androidOffersRefreshChoicesAndImmersiveAndHidesControlsWithoutAKeyboard() {
        val display = SettingsTestDisplayPort(ANDROID_SUPPORT)
        settings(display, width = 390, height = 844) { port, _, _ ->
            assertTrue(onAllNodesWithTag("kinetickk.settings.group.controls").fetchSemanticsNodes().isEmpty())
            click("kinetickk.settings.group.display")
            assertTrue(onAllNodesWithTag("kinetickk.settings.windowmode.windowed").fetchSemanticsNodes().isEmpty())
            onNodeWithTag("kinetickk.settings.refreshrate.auto").assertIsSelected()
            click("kinetickk.settings.refreshrate.90")
            assertEquals(90, port.preferences.refreshRateHz)
            click("kinetickk.settings.immersive.toggle")
            assertEquals(true, port.preferences.immersive)
            // Touch never offers 85 %.
            assertTrue(onAllNodesWithTag("kinetickk.settings.interfacescale.85").fetchSemanticsNodes().isEmpty())
            click("kinetickk.settings.group.game")
            click("kinetickk.settings.pauseonfocusloss.toggle")
            assertEquals(false, port.preferences.pauseOnFocusLoss)
            click("kinetickk.settings.group.graphics")
            click("kinetickk.settings.reactioneffects.minimal")
            assertEquals(kinetickk.ball.profile.api.ReactionEffectsLevel.MINIMAL, port.preferences.reactionEffects)
        }
    }

    @Test
    fun theWebFullscreenToggleAsksTheBrowserAndNeverWritesProfile() {
        val display = SettingsTestDisplayPort(WEB_SUPPORT)
        settings(display) { port, _, _ ->
            click("kinetickk.settings.group.display")
            click("kinetickk.settings.fullscreen.toggle")
            assertTrue(display.browserFullscreen)
            assertEquals(true, onNodeWithTag("kinetickk.settings.fullscreen.toggle").fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ToggleableState)?.let { it == androidx.compose.ui.state.ToggleableState.On })
            click("kinetickk.settings.fullscreen.toggle")
            assertTrue(!display.browserFullscreen)
            assertTrue(port.adjustments.isEmpty())
        }
    }

    private fun settings(
        display: SettingsDisplayPort,
        width: Int = 1440,
        height: Int = 810,
        preferences: PlayerPreferences = PlayerPreferences(language = AppLanguage.English),
        body: ComposeUiTest.(SettingsTestProfilePort, MutableList<SettingsOutput>, SettingsDisplayPort) -> Unit,
    ): Unit = runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
        mainClock.autoAdvance = false
        val port = SettingsTestProfilePort(preferences)
        val outputs = mutableListOf<SettingsOutput>()
        val feature = DefaultSettingsFeature(port, SettingsTestSilentAudio, display)
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides preferences.language) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    feature.Content(routeToken = 1L) { outputs += it }
                }
            }
        }
        settle()
        body(port, outputs, display)
    }

    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(120)
        waitForIdle()
    }

    private fun ComposeUiTest.click(tag: String) {
        onNodeWithTag(tag).performClick()
        settle()
    }

    private fun ComposeUiTest.key(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun ComposeUiTest.announcement(): String? =
        onNodeWithTag("kinetickk.settings.announce").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.single()

    private fun SemanticsNodeInteraction.state(): String? =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
}
