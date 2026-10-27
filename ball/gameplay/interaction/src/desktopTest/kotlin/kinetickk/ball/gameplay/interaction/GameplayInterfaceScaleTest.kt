// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.canvas.HudBlock
import kinetickk.ball.gameplay.interaction.canvas.HudLayoutProbe
import kinetickk.ball.gameplay.interaction.canvas.hudTestSnapshot
import kinetickk.ball.gameplay.interaction.canvas.with
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.input.hudScale
import kinetickk.ball.gameplay.interaction.input.resolveGameplayPress
import kinetickk.ball.gameplay.interaction.input.runningControlTargetAt
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.runningControlBounds
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkInterfaceScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The applied interface scale (meta-ui §3.6) reaches the run: the HUD, its running controls and their hit
 * tests grow together while the world keeps the raw viewport density.
 */
@OptIn(ExperimentalTestApi::class)
class GameplayInterfaceScaleTest {
    private class TestRun(width: Float, height: Float) : GameplayInteractionPort {
        val snapshot = hudTestSnapshot(width, height)
        val pulses = ArrayList<GameplayInteractionPulse>()
        override val instanceId get() = snapshot.instanceId
        override fun renderSnapshot() = snapshot
        override fun visualFxSnapshot() = VisualFxProjection.EMPTY
        override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance {
            pulses += pulse
            return GameplayAcceptance.Accepted(snapshot.instanceId, snapshot.revision)
        }
    }

    /** What the run showed at one interface scale: control nodes, the drawn clock, the run's pulses. */
    private class Shown(val pause: Rect, val dash: Rect, val clock: Rect, val pulses: List<GameplayInteractionPulse>)

    private fun show(scale: Float, pressAt: ((Rect) -> androidx.compose.ui.geometry.Offset)? = null): Shown {
        var shown: Shown? = null
        runComposeUiTest {
            mainClock.autoAdvance = false
            val run = TestRun(WIDTH.toFloat(), HEIGHT.toFloat())
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalAppLanguage provides AppLanguage.English,
                    LocalKkInterfaceScale provides scale,
                ) {
                    Box(Modifier.requiredSize(WIDTH.dp, HEIGHT.dp)) { GameplayContent(run, inputEnabled = true) {} }
                }
            }
            repeat(2) { mainClock.advanceTimeByFrame() }
            waitForIdle()
            val pause = onNodeWithTag("kinetickk.gameplay.pause").fetchSemanticsNode().boundsInRoot
            val dash = onNodeWithTag("kinetickk.gameplay.dash").fetchSemanticsNode().boundsInRoot
            val clock = assertNotNull(HudLayoutProbe.rect(HudBlock.CLOCK), "no clock drawn")
            if (pressAt != null) {
                run.pulses.clear()
                val point = pressAt(pause)
                onRoot().performTouchInput { click(point) }
                mainClock.advanceTimeByFrame()
            }
            shown = Shown(pause, dash, clock, run.pulses.toList())
        }
        return checkNotNull(shown)
    }

    @Test
    fun theRunsControlsAndReadoutsGrowWithTheInterfaceScale() {
        val normal = show(1f)
        val large = show(LARGE)
        // Pause is a 48 dp square: exactly the scale; Dash and the clock follow the HUD unit (≥ 1).
        assertEquals(normal.pause.width * LARGE, large.pause.width, 0.5f)
        assertEquals(normal.pause.height * LARGE, large.pause.height, 0.5f)
        assertTrue(large.dash.width > normal.dash.width * 1.1f, "Dash ${normal.dash} -> ${large.dash}")
        assertTrue(large.clock.height > normal.clock.height * 1.1f, "clock ${normal.clock} -> ${large.clock}")
        // The world keeps the raw density: the viewport the run receives is the same at both scales.
        fun viewports(shown: Shown) = shown.pulses.filterIsInstance<GameplayInteractionPulse.ViewportChanged>().map { it.density }.toSet()
        assertEquals(setOf(1f), viewports(normal))
        assertEquals(setOf(1f), viewports(large))
    }

    @Test
    fun aTapLandsOnTheScaledControlWhereItIsDrawn() {
        // Just inside the scaled pause button's left edge, outside the 100 % one: it pauses.
        val large = show(LARGE) { pause -> androidx.compose.ui.geometry.Offset(pause.left + 2f, pause.center.y) }
        assertTrue(GameplayInteractionPulse.PauseToggled in large.pulses, "${large.pulses}")
        val model = requireNotNull(hudTestSnapshot(WIDTH.toFloat(), HEIGHT.toFloat()).renderModel)
        val scaledPause = runningControlBounds(model.screenWidth, model.screenHeight, model.hudScale(LARGE), RunningControlMask.NONE)
            .single { it.target == RunningControlTarget.PAUSE }.bounds
        val normalPause = runningControlBounds(model.screenWidth, model.screenHeight, model.hudScale(1f), RunningControlMask.NONE)
            .single { it.target == RunningControlTarget.PAUSE }.bounds
        assertEquals(large.pause.left, scaledPause.left, 0.5f)
        assertTrue(scaledPause.left + 2f < normalPause.left, "the probe point lies outside the 100 % button")
        assertEquals(RunningControlTarget.PAUSE, model.runningControlTargetAt(scaledPause.left + 2f, scaledPause.center.y, LARGE))
        assertEquals(null, model.runningControlTargetAt(scaledPause.left + 2f, scaledPause.center.y))
    }

    @Test
    fun thePausedMenuIsHitTestedAtTheScaleItIsLaidOutAt() {
        val model = requireNotNull(hudTestSnapshot(WIDTH.toFloat(), HEIGHT.toFloat()).renderModel).with("phase" to GamePhase.PAUSED)
        val scaled = pauseLayoutGeometry(model.screenWidth, model.screenHeight, model.hudScale(LARGE))
        val exit = scaled.actions.last().bounds
        assertEquals(GameplayInput.ExitToHome, model.resolveGameplayPress(exit.center.x, exit.center.y, LARGE))
        assertEquals(1f, model.hudScale(1f))
        assertEquals(LARGE, model.hudScale(LARGE))
    }

    private companion object {
        const val WIDTH = 1_000
        const val HEIGHT = 700
        const val LARGE = 1.3f
    }
}
