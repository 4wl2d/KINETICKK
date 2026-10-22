// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.canvas.hudTestSnapshot
import kinetickk.ball.gameplay.interaction.canvas.with
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Show FPS and Pause when unfocused through the real gameplay host. */
@OptIn(ExperimentalTestApi::class)
class GameplayWindowPreferencesTest {
    private class TestWindow(focused: Boolean) : WindowInfo {
        override var isWindowFocused: Boolean by mutableStateOf(focused)
    }

    /** A run whose published model the test replaces, as a run does when Settings applies preferences. */
    private class TestRun(width: Float, height: Float, preferences: PlayerPreferences) : GameplayInteractionPort {
        private val base = hudTestSnapshot(width, height)
        val pulses = ArrayList<GameplayInteractionPulse>()
        var snapshot: GameplayRenderSnapshot = base
            private set

        init {
            publish(preferences)
        }

        fun publish(preferences: PlayerPreferences, phase: GamePhase = GamePhase.RUNNING) {
            snapshot = base.withModel(requireNotNull(base.renderModel).with("settings" to preferences, "phase" to phase))
        }

        override val instanceId get() = snapshot.instanceId
        override fun renderSnapshot() = snapshot
        override fun visualFxSnapshot() = VisualFxProjection.EMPTY
        override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance {
            pulses += pulse
            return GameplayAcceptance.Accepted(snapshot.instanceId, snapshot.revision)
        }
    }

    private val english = PlayerPreferences(language = AppLanguage.English)

    @Test
    fun onALandscapePhoneShowFpsPlacesTheCounterWhoseTapTogglesThePanel() = exerciseCounter(844, 390)

    @Test
    fun onDesktopShowFpsPlacesTheCounterWhoseTapTogglesThePanel() = exerciseCounter(1_440, 810)

    /** The counter is the PERFORMANCE control: Show FPS places it with the panel closed, a tap opens the panel. */
    private fun exerciseCounter(width: Int, height: Int) = runComposeUiTest {
        mainClock.autoAdvance = false
        val run = TestRun(width.toFloat(), height.toFloat(), english.copy(showFps = true))
        setContent { Host(run, TestWindow(focused = true), width, height) }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        assertEquals("off", performanceState(), "$width x $height: Show FPS shows the counter, not the panel")
        onNodeWithTag("kinetickk.gameplay.performance").performSemanticsAction(SemanticsActions.OnClick)
        repeat(2) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        assertEquals("on", performanceState(), "$width x $height: a tap on the counter opens the panel")
        run.publish(english.copy(showFps = false))
        // A frame takes the new model, the next recomposes with it.
        repeat(6) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        assertNull(performanceState(), "$width x $height: the counter leaves with Show FPS off")
    }

    @Test
    fun withoutShowFpsTheRunHasNoCounter() = runComposeUiTest {
        mainClock.autoAdvance = false
        val run = TestRun(844f, 390f, english)
        setContent { Host(run, TestWindow(focused = true), 844, 390) }
        repeat(3) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        assertNull(performanceState())
    }

    @Test
    fun onlyPortraitPhonesOpenThePanelForShowFps() {
        // Portrait phones have no Performance slot for the counter (Build holds it), so the panel shows instead.
        assertTrue(showFpsOpensPanel(true, GameplayLayoutMode.COMPACT_PORTRAIT))
        assertFalse(showFpsOpensPanel(false, GameplayLayoutMode.COMPACT_PORTRAIT))
        assertFalse(showFpsOpensPanel(true, GameplayLayoutMode.COMPACT_LANDSCAPE))
        assertFalse(showFpsOpensPanel(true, GameplayLayoutMode.REGULAR))
    }

    @Test
    fun losingWindowFocusPausesOnlyARunningRunAndOnlyWithThePreferenceOn() = runComposeUiTest {
        mainClock.autoAdvance = false
        val run = TestRun(1_440f, 810f, english)
        val window = TestWindow(focused = true)
        setContent { Host(run, window, 1_440, 810) }
        fun pauses() = run.pulses.count { it == GameplayInteractionPulse.PauseToggled }
        fun focus(focused: Boolean) {
            window.isWindowFocused = focused
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        assertEquals(0, pauses())
        focus(false)
        assertEquals(1, pauses())
        // Gaining focus never resumes; staying unfocused never pauses again.
        focus(true)
        mainClock.advanceTimeByFrame()
        assertEquals(1, pauses())

        run.publish(english.copy(pauseOnFocusLoss = false))
        mainClock.advanceTimeByFrame()
        focus(false)
        focus(true)
        assertEquals(1, pauses())

        run.publish(english, phase = GamePhase.PAUSED)
        mainClock.advanceTimeByFrame()
        focus(false)
        assertEquals(1, pauses())
    }

    @Test
    fun aWindowThatNeverHadFocusDoesNotPause() = runComposeUiTest {
        mainClock.autoAdvance = false
        val run = TestRun(1_440f, 810f, english)
        val window = TestWindow(focused = false)
        setContent { Host(run, window, 1_440, 810) }
        repeat(3) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        assertFalse(GameplayInteractionPulse.PauseToggled in run.pulses)
    }

    @Test
    fun theFocusEdgeRuleNeedsAFocusedRunningRunWithInput() {
        assertTrue(pausesOnFocusLoss(true, false, GamePhase.RUNNING, pauseOnFocusLoss = true, inputEnabled = true))
        assertFalse(pausesOnFocusLoss(false, false, GamePhase.RUNNING, pauseOnFocusLoss = true, inputEnabled = true))
        assertFalse(pausesOnFocusLoss(true, true, GamePhase.RUNNING, pauseOnFocusLoss = true, inputEnabled = true))
        assertFalse(pausesOnFocusLoss(true, false, GamePhase.RUNNING, pauseOnFocusLoss = false, inputEnabled = true))
        assertFalse(pausesOnFocusLoss(true, false, GamePhase.RUNNING, pauseOnFocusLoss = true, inputEnabled = false))
        GamePhase.entries.filter { it != GamePhase.RUNNING }.forEach { phase ->
            assertFalse(pausesOnFocusLoss(true, false, phase, pauseOnFocusLoss = true, inputEnabled = true), "$phase")
        }
    }

    @androidx.compose.runtime.Composable
    private fun Host(run: TestRun, window: WindowInfo, width: Int, height: Int) {
        CompositionLocalProvider(
            LocalDensity provides Density(1f),
            LocalAppLanguage provides AppLanguage.English,
            LocalWindowInfo provides window,
        ) {
            Box(Modifier.requiredSize(width.dp, height.dp)) { GameplayContent(run, inputEnabled = true) {} }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.performanceState(): String? =
        onAllNodesWithTag("kinetickk.gameplay.performance").fetchSemanticsNodes()
            .firstOrNull()?.config?.getOrNull(SemanticsProperties.StateDescription)
}

private fun GameplayRenderSnapshot.withModel(model: GameplayRenderModel): GameplayRenderSnapshot {
    val constructor = GameplayRenderSnapshot::class.java.declaredConstructors.first { it.parameterCount == 4 }
    constructor.isAccessible = true
    val identity = GameplayRenderSnapshot::class.java.declaredFields.first { it.name == "projectionSourceIdentity" }
        .also { it.isAccessible = true }.get(this)
    return constructor.newInstance(instanceId, revision, model, identity) as GameplayRenderSnapshot
}
