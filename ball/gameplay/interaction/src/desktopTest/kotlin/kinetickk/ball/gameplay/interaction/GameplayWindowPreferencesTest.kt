// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.canvas.hudTestSnapshot
import kinetickk.ball.gameplay.interaction.canvas.with
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalCrashDiagnostics
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kinetickk.foundation.diagnostics.PerformanceDiagnostics
import kinetickk.foundation.diagnostics.DiagnosticPhase
import kinetickk.foundation.diagnostics.DIAGNOSTICS_ENABLED
import kinetickk.foundation.design.LocalAppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Show FPS and Pause when unfocused through the real gameplay host. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeApi::class)
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

    private class TestDiagnostics : PerformanceDiagnostics {
        override var capturesPerformance: Boolean by mutableStateOf(false)
        override val overlayVisible: Boolean get() = capturesPerformance
        var frames = 0
        override fun toggleOverlay() { capturesPerformance = !capturesPerformance }
        override fun frame(frameTimeNanos: Long, budgetNanos: Long) { frames++ }
        override fun phase(phase: DiagnosticPhase, durationNanos: Long) = Unit
        override fun workload(enemies: Int, projectiles: Int, pickups: Int, effects: Int) = Unit
        override fun context(key: String, describe: () -> String) = Unit
        override fun event(category: String, message: String, highFrequency: Boolean) = Unit
    }

    private val english = PlayerPreferences(language = AppLanguage.English)

    @Test
    fun onALandscapePhoneShowFpsMeasuresFramesAndOnlyDebugCanOpenThePanel() = exerciseCounter(844, 390)

    @Test
    fun onDesktopShowFpsMeasuresFramesAndOnlyDebugCanOpenThePanel() = exerciseCounter(1_440, 810)

    @Test
    fun onA360DpPortraitWindowShowFpsRemainsVisibleAndCanBeToggled() = exerciseCounter(360, 720)

    @Test
    fun onA320DpPortraitWindowShowFpsRemainsVisibleAndCanBeToggled() = exerciseCounter(320, 640)

    /** Show FPS measures the gameplay loop independently; only Debug exposes its diagnostic action. */
    private fun exerciseCounter(width: Int, height: Int) = runComposeUiTest {
        mainClock.autoAdvance = false
        val run = TestRun(width.toFloat(), height.toFloat(), english.copy(showFps = true))
        val diagnostics = TestDiagnostics()
        setContent { Host(run, TestWindow(focused = true), width, height, diagnostics) }
        mainClock.advanceTimeBy(640)
        waitForIdle()
        assertMeasuredFps()
        assertEquals(0, diagnostics.frames, "Showing FPS must not start performance recording")
        if (DIAGNOSTICS_ENABLED && width > height) {
            assertEquals("off", performanceState(), "$width x $height: Show FPS shows the counter, not the panel")
            onNodeWithTag("kinetickk.gameplay.performance").performSemanticsAction(SemanticsActions.OnClick)
            repeat(2) { mainClock.advanceTimeByFrame() }
            waitForIdle()
            assertEquals("on", performanceState(), "$width x $height: a tap on the counter opens the panel")
        } else {
            val node = onNodeWithTag("kinetickk.gameplay.performance").fetchSemanticsNode()
            assertNull(node.config.getOrNull(SemanticsActions.OnClick), "Release and portrait have a read-only counter")
            assertNull(node.config.getOrNull(SemanticsProperties.Role), "A readout must not announce a diagnostic button")
        }
        run.publish(english.copy(showFps = false))
        // A frame takes the new model, the next recomposes with it.
        repeat(6) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        assertNull(counterText(), "$width x $height: the counter leaves with Show FPS off")
        run.publish(english.copy(showFps = true))
        repeat(6) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        assertEquals("– FPS", counterText(), "Showing the counter again starts a fresh measurement window")
        mainClock.advanceTimeBy(640)
        waitForIdle()
        assertMeasuredFps()
    }

    @Test
    fun showFpsMeasuresFramesWithoutAnyDiagnosticService() = runComposeUiTest {
        mainClock.autoAdvance = false
        val run = TestRun(1440f, 810f, english.copy(showFps = true))
        setContent { Host(run, TestWindow(focused = true), 1440, 810, CrashDiagnostics.None) }
        mainClock.advanceTimeBy(640)
        waitForIdle()
        assertMeasuredFps()
    }

    @Test
    fun fpsRemainsVisibleAndReadOnlyAboveRewardAndPauseOverlays() {
        for ((width, height) in listOf(320 to 640, 360 to 720, 1440 to 810)) runDesktopComposeUiTest(width, height) {
            mainClock.autoAdvance = false
            val run = TestRun(width.toFloat(), height.toFloat(), english.copy(showFps = true))
            setContent { Host(run, TestWindow(focused = true), width, height) }
            mainClock.advanceTimeBy(640)
            for (phase in listOf(GamePhase.CHOICE, GamePhase.PAUSED)) {
                runOnIdle { run.publish(english.copy(showFps = true), phase) }
                mainClock.advanceTimeBy(2400)
                waitForIdle()
                assertMeasuredFps()
                val counter = onNodeWithTag("kinetickk.gameplay.performance")
                val node = counter.fetchSemanticsNode()
                assertNull(node.config.getOrNull(SemanticsActions.OnClick), "$phase counter is read-only in every build")
                val viewport = onNodeWithTag("kinetickk.gameplay").fetchSemanticsNode().boundsInRoot
                assertTrue(viewport.contains(node.boundsInRoot.topLeft) && viewport.contains(node.boundsInRoot.bottomRight),
                    "$phase counter ${node.boundsInRoot} must fit the gameplay viewport $viewport")
                val image = counter.captureToImage().toPixelMap()
                var brightPixels = 0
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    val color = image[x, y]
                    if (color.red > 0.7f && color.green > 0.7f && color.blue > 0.6f) brightPixels++
                }
                assertTrue(brightPixels > 5, "$phase overlay must not cover the numeric FPS text")
                runOnIdle { run.publish(english.copy(showFps = false), phase) }
                repeat(6) { mainClock.advanceTimeByFrame() }
                waitForIdle()
                assertNull(counterText(), "$phase must respect Show FPS off")
                runOnIdle { run.publish(english.copy(showFps = true), phase) }
                mainClock.advanceTimeBy(640)
                waitForIdle()
                assertMeasuredFps()
            }
        }
    }

    @Test
    fun publishingTheFirstFpsValueDoesNotRecomposeGameplay() = runComposeUiTest {
        mainClock.autoAdvance = false
        val run = TestRun(1440f, 810f, english.copy(showFps = true))
        setContent { Host(run, TestWindow(focused = true), 1440, 810, CrashDiagnostics.None) }
        mainClock.advanceTimeBy(240)
        waitForIdle()
        assertEquals("– FPS", counterText())
        val recomposers = Recomposer.runningRecomposers.value.toList()
        assertTrue(recomposers.isNotEmpty())
        val changes = recomposers.sumOf { it.changeCount }
        mainClock.advanceTimeBy(400)
        waitForIdle()
        assertMeasuredFps()
        assertEquals(changes, recomposers.sumOf { it.changeCount }, "The numeric FPS readout updates semantics and drawing only")
    }

    @Test
    fun withoutShowFpsTheRunHasNoCounter() = runComposeUiTest {
        mainClock.autoAdvance = false
        val run = TestRun(844f, 390f, english)
        setContent { Host(run, TestWindow(focused = true), 844, 390) }
        repeat(3) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        assertNull(counterText())
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
    private fun Host(run: TestRun, window: WindowInfo, width: Int, height: Int, diagnostics: CrashDiagnostics = CrashDiagnostics.None) {
        CompositionLocalProvider(
            LocalDensity provides Density(1f),
            LocalAppLanguage provides AppLanguage.English,
            LocalWindowInfo provides window,
            LocalCrashDiagnostics provides diagnostics,
        ) {
            Box(Modifier.requiredSize(width.dp, height.dp)) { GameplayContent(run, inputEnabled = true) {} }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.performanceState(): String? =
        onAllNodesWithTag("kinetickk.gameplay.performance").fetchSemanticsNodes()
            .firstOrNull()?.config?.getOrNull(SemanticsProperties.StateDescription)

    private fun androidx.compose.ui.test.ComposeUiTest.counterText(): String? =
        onAllNodesWithTag("kinetickk.gameplay.performance").fetchSemanticsNodes()
            .firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.single()?.text

    private fun androidx.compose.ui.test.ComposeUiTest.assertMeasuredFps() {
        val text = assertNotNull(counterText(), "Show FPS must expose a counter in every build")
        val measured = assertNotNull(text.removeSuffix(" FPS").toIntOrNull(), "A half-second window must display numeric FPS: $text")
        assertTrue(measured in 60..63, "The test frame clock advances every 16 ms: $text")
    }
}

private fun GameplayRenderSnapshot.withModel(model: GameplayRenderModel): GameplayRenderSnapshot {
    val constructor = GameplayRenderSnapshot::class.java.declaredConstructors.first { it.parameterCount == 4 }
    constructor.isAccessible = true
    val identity = GameplayRenderSnapshot::class.java.declaredFields.first { it.name == "projectionSourceIdentity" }
        .also { it.isAccessible = true }.get(this)
    return constructor.newInstance(instanceId, revision, model, identity) as GameplayRenderSnapshot
}
