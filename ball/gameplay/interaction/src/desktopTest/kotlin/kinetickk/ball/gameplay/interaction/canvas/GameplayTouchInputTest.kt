// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.interaction.GameplayContent
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.runningControlBounds
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The running HUD's touch model end to end: one thumb steers while others tap Dash and a skill and hold
 * Brake; control pointers never steer; Brake releases only when its last pointer lifts; when the steering
 * thumb lifts, a waiting pointer takes over from where it is.
 */
@OptIn(ExperimentalTestApi::class)
class GameplayTouchInputTest {
    @get:Rule
    val compose = createComposeRule()

    private val pulses = mutableListOf<GameplayInteractionPulse>()

    private fun start() {
        val snapshot = hudTestSnapshot()
        val model = requireNotNull(snapshot.renderModel).with(*levelOverrides(), "skillBar" to FULL_SKILL_BAR)
        val running = snapshot.withTouchModel(model)
        val port = object : GameplayInteractionPort {
            override val instanceId get() = running.instanceId
            override fun renderSnapshot() = running
            override fun visualFxSnapshot() = VisualFxProjection.EMPTY
            override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance {
                pulses += pulse
                return GameplayAcceptance.Rejected(running.instanceId, running.revision, GameplayRejection.RunExited)
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(1_440.dp, 810.dp).testTag(HOST)) { GameplayContent(port, true) {} }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun actions(): List<GameplayInteractionPulse> = pulses.filter {
        it !is GameplayInteractionPulse.FrameElapsed && it !is GameplayInteractionPulse.UserGestureObserved &&
            it !is GameplayInteractionPulse.ViewportChanged && it !is GameplayInteractionPulse.PointerMoved
    }

    private fun steeredTo(): List<Offset> =
        pulses.filterIsInstance<GameplayInteractionPulse.PointerMoved>().map { Offset(it.x, it.y) }

    private fun touch(block: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
        pulses.clear()
        compose.onNodeWithTag(HOST).performTouchInput(block)
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun aSteeringThumbKeepsSteeringWhileOtherFingersTapDashASkillAndHoldBrake() {
        start()
        val controls = runningControlBounds(1_440f, 810f, 1f).associate { it.target to it.bounds }
        val dash = controls.getValue(RunningControlTarget.DASH).center
        val brake = controls.getValue(RunningControlTarget.BRAKE).center
        val z = controls.getValue(RunningControlTarget.SKILL_1).center

        touch { down(0, Offset(700f, 500f)) }
        assertEquals(listOf(Offset(700f, 500f)), steeredTo())

        touch { down(1, dash) }
        assertEquals(listOf<GameplayInteractionPulse>(GameplayInteractionPulse.DashRequested), actions())
        assertTrue(steeredTo().isEmpty(), "the Dash tap steered")

        touch { down(2, brake) }
        assertEquals(listOf<GameplayInteractionPulse>(GameplayInteractionPulse.BrakeChanged(BrakeSource.TOUCH_CONTROL, true)), actions())
        touch { down(3, z) }
        assertEquals(listOf<GameplayInteractionPulse>(GameplayInteractionPulse.SkillRequested.fromValidated(0)), actions())

        // The control fingers slide into the arena: they never steer; the steering thumb still does.
        touch { moveTo(1, Offset(600f, 400f)); moveTo(3, Offset(500f, 300f)) }
        assertTrue(steeredTo().isEmpty(), "a control finger steered: ${steeredTo()}")
        touch { moveTo(0, Offset(720f, 520f)) }
        assertEquals(listOf(Offset(720f, 520f)), steeredTo())

        // Lifting the skill and Dash fingers changes nothing; Brake releases with its own finger.
        touch { up(3); up(1) }
        assertTrue(actions().isEmpty(), "lifting Dash or a skill dispatched ${actions()}")
        touch { up(2) }
        assertEquals(listOf<GameplayInteractionPulse>(GameplayInteractionPulse.BrakeChanged(BrakeSource.TOUCH_CONTROL, false)), actions())
        touch { up(0) }
        assertTrue(actions().isEmpty())
    }

    @Test
    fun aWaitingFingerTakesOverTheSteeringWhenTheSteeringThumbLifts() {
        start()
        touch { down(0, Offset(700f, 500f)) }
        // A second finger off the controls waits: its moves do not steer.
        touch { down(1, Offset(400f, 300f)) }
        assertTrue(steeredTo().isEmpty(), "the waiting finger steered")
        touch { moveTo(1, Offset(420f, 310f)) }
        assertTrue(steeredTo().isEmpty())
        touch { up(0) }
        assertEquals(listOf(Offset(420f, 310f)), steeredTo(), "the waiting finger did not take over from where it is")
        touch { moveTo(1, Offset(430f, 320f)) }
        assertEquals(listOf(Offset(430f, 320f)), steeredTo())
        touch { up(1) }
    }

    @Test
    fun twoFingersOnBrakeKeepBrakingUntilTheLastLifts() {
        start()
        val brake = runningControlBounds(1_440f, 810f, 1f).first { it.target == RunningControlTarget.BRAKE }.bounds
        touch { down(0, brake.center) }
        touch { down(1, brake.center + Offset(20f, 0f)) }
        touch { up(0) }
        assertTrue(actions().none { it is GameplayInteractionPulse.BrakeChanged }, "the first Brake finger released Brake")
        touch { up(1) }
        assertEquals(listOf<GameplayInteractionPulse>(GameplayInteractionPulse.BrakeChanged(BrakeSource.TOUCH_CONTROL, false)), actions())
    }
}

private const val HOST = "touch-host"

private fun GameplayRenderSnapshot.withTouchModel(model: kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel): GameplayRenderSnapshot {
    val constructor = GameplayRenderSnapshot::class.java.declaredConstructors.first { it.parameterCount == 4 }
    constructor.isAccessible = true
    val identity = GameplayRenderSnapshot::class.java.declaredFields.first { it.name == "projectionSourceIdentity" }
        .also { it.isAccessible = true }.get(this)
    return constructor.newInstance(instanceId, revision, model, identity) as GameplayRenderSnapshot
}
