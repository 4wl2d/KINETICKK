// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
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
import kinetickk.foundation.design.LocalKkInterfaceScale
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
    fun keysOfACaptureNeverReachTheShellEvenWhileHeld() = settings(SettingsDisplayPort.None) { port, _, unconsumed ->
        click("kinetickk.settings.group.controls")
        // Outside a capture Settings leaves Escape and letters to the shell.
        key(Key.Escape)
        key(Key.S)
        assertEquals(listOf(Key.Escape, Key.S), unconsumed.toList())
        unconsumed.clear()
        // Escape held to cancel: its repeats do not also close Settings.
        click("kinetickk.settings.bind.pause.key")
        onRoot().performKeyInput { keyDown(Key.Escape); advanceEventTime(700); keyUp(Key.Escape) }
        settle()
        // S held to bind: its repeats do not reach the Settings shortcut.
        click("kinetickk.settings.bind.pause.key")
        onRoot().performKeyInput { keyDown(Key.S); advanceEventTime(700); keyUp(Key.S) }
        settle()
        assertEquals(BindableKey.S, port.preferences.keyBindings.key(GameAction.PAUSE))
        assertTrue(unconsumed.isEmpty(), "$unconsumed")
        // After the release, keys reach the shell again.
        key(Key.S)
        assertEquals(listOf(Key.S), unconsumed.toList())
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

    @Test
    fun aWindowOpenedAgainForADisplayChangeComesBackToTheSameTabAndPage() =
        runSkikoComposeUiTest(size = Size(844f, 390f), density = Density(1f)) {
            mainClock.autoAdvance = false
            val port = SettingsTestProfilePort(PlayerPreferences(language = AppLanguage.English))
            val feature = DefaultSettingsFeature(port, SettingsTestSilentAudio, SettingsTestDisplayPort(DESKTOP_SUPPORT))
            var window by mutableIntStateOf(0)
            var route by mutableLongStateOf(1L)
            setContent {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English) {
                    // A new window composes the feature from scratch: no rememberSaveable state survives.
                    key(window) {
                        Box(Modifier.requiredSize(844.dp, 390.dp)) { feature.Content(routeToken = route) {} }
                    }
                }
            }
            settle()
            // Graphics pages at 844×390 (8 rows).
            click("kinetickk.settings.group.graphics")
            click("kinetickk.settings.page.1")
            onNodeWithTag("kinetickk.settings.page.1").assertIsSelected()
            window++
            settle()
            onNodeWithTag("kinetickk.settings.group.graphics").assertIsSelected()
            onNodeWithTag("kinetickk.settings.page.1").assertIsSelected()
            // A new route (Settings opened again) starts on the first tab.
            route = 2L
            settle()
            onNodeWithTag("kinetickk.settings.group.game").assertIsSelected()
        }

    @Test
    fun underTheShellsScaledDensityTheScalesFollowTheWindowsUnscaledSize() =
        runSkikoComposeUiTest(size = Size(900f, 700f), density = Density(1f)) {
            mainClock.autoAdvance = false
            // 900×700 dp keeps 130 % (692×538 dp); judged on the scaled 692×538 it would not (532 dp long).
            val port = SettingsTestProfilePort(PlayerPreferences(language = AppLanguage.English, interfaceScalePercent = 130))
            val feature = DefaultSettingsFeature(port, SettingsTestSilentAudio, SettingsTestDisplayPort(DESKTOP_SUPPORT))
            setContent {
                CompositionLocalProvider(
                    LocalAppLanguage provides AppLanguage.English,
                    LocalDensity provides Density(1.3f),
                    LocalKkInterfaceScale provides 1.3f,
                ) {
                    Box(Modifier.fillMaxSize()) { feature.Content(routeToken = 1L) {} }
                }
            }
            settle()
            click("kinetickk.settings.group.display")
            var page = 0
            while (onAllNodesWithTag("kinetickk.settings.interfacescale.130").fetchSemanticsNodes().isEmpty()) {
                click("kinetickk.settings.page.${++page}")
            }
            onNodeWithTag("kinetickk.settings.interfacescale.130").assertIsSelected()
            assertTrue(port.adjustments.isEmpty())
        }

    private fun settings(
        display: SettingsDisplayPort,
        width: Int = 1440,
        height: Int = 810,
        preferences: PlayerPreferences = PlayerPreferences(language = AppLanguage.English),
        body: ComposeUiTest.(SettingsTestProfilePort, MutableList<SettingsOutput>, MutableList<Key>) -> Unit,
    ): Unit = runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
        mainClock.autoAdvance = false
        val port = SettingsTestProfilePort(preferences)
        val outputs = mutableListOf<SettingsOutput>()
        // Key downs Settings left unconsumed, as the shell's bubble handler would see them.
        val unconsumed = mutableListOf<Key>()
        val feature = DefaultSettingsFeature(port, SettingsTestSilentAudio, display)
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides preferences.language) {
                Box(Modifier.requiredSize(width.dp, height.dp).onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) unconsumed += event.key
                    false
                }) {
                    feature.Content(routeToken = 1L) { outputs += it }
                }
            }
        }
        settle()
        body(port, outputs, unconsumed)
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
