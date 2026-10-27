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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
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
import kinetickk.ball.gameplay.interaction.rewards.rewardActiveChoice
import kinetickk.ball.gameplay.interaction.rewards.rewardActiveFixtureContent
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureActiveBase
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The running HUD's touch model end to end: one thumb steers while others tap Dash and a skill and hold
 * Brake; control pointers never steer; Brake releases only when its last pointer lifts; when the steering
 * thumb lifts, a waiting pointer takes over from where it is; a touch player's reward cards name a Z/X/C slot by its
 * number until a key is pressed.
 */
@OptIn(ExperimentalTestApi::class)
class GameplayTouchInputTest {
    @get:Rule
    val compose = createComposeRule()

    private val pulses = mutableListOf<GameplayInteractionPulse>()
    private lateinit var running: GameplayRenderSnapshot
    private lateinit var current: GameplayRenderSnapshot

    private fun start() {
        val snapshot = hudTestSnapshot()
        val model = requireNotNull(snapshot.renderModel).with(*levelOverrides(), "skillBar" to FULL_SKILL_BAR)
        running = snapshot.withTouchModel(model)
        current = running
        val port = object : GameplayInteractionPort {
            override val instanceId get() = running.instanceId
            override fun renderSnapshot() = current
            override fun visualFxSnapshot() = VisualFxProjection.EMPTY
            override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance {
                pulses += pulse
                // Accepted while the test moves the run between phases, so the host reads the new one.
                return if (current === running) {
                    GameplayAcceptance.Rejected(running.instanceId, running.revision, GameplayRejection.RunExited)
                } else {
                    GameplayAcceptance.Accepted(current.instanceId, current.revision)
                }
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

    /** The run moves to [phase] (RUNNING is the started run); the host reads it on the next frame. */
    private fun enter(phase: GamePhase) {
        val model = requireNotNull(running.renderModel)
        current = running.withTouchModel(model.with("phase" to phase))
        compose.mainClock.advanceTimeByFrame()
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
    fun aThumbHeldThroughAChoiceOrAPauseSteersAndBrakesAgainWhenTheRunReturns() {
        start()
        val brake = runningControlBounds(1_440f, 810f, 1f).first { it.target == RunningControlTarget.BRAKE }.bounds.center
        val braking = GameplayInteractionPulse.BrakeChanged(BrakeSource.TOUCH_CONTROL, true)
        val released = GameplayInteractionPulse.BrakeChanged(BrakeSource.TOUCH_CONTROL, false)
        touch { down(0, Offset(700f, 500f)) }
        touch { down(1, brake) }
        assertEquals(listOf<GameplayInteractionPulse>(braking), actions())

        for (away in listOf(GamePhase.CHOICE, GamePhase.PAUSED)) {
            enter(away)
            // Away from RUNNING the held Brake is released once and the thumb does not steer.
            touch { moveTo(0, Offset(710f, 505f)) }
            assertEquals(listOf<GameplayInteractionPulse>(released), actions(), "$away")
            assertTrue(steeredTo().isEmpty(), "$away: steered ${steeredTo()}")
            touch { moveTo(0, Offset(715f, 508f)) }
            assertTrue(actions().isEmpty(), "$away: ${actions()}")

            enter(GamePhase.RUNNING)
            touch { moveTo(0, Offset(720f, 520f)) }
            assertEquals(listOf<GameplayInteractionPulse>(braking), actions(), "after $away the held Brake was not engaged again")
            assertEquals(listOf(Offset(720f, 520f)), steeredTo(), "after $away the held thumb did not steer")
        }

        // A finger lifted while the run is away is forgotten: Brake stays released on return.
        enter(GamePhase.CHOICE)
        touch { up(1) }
        assertEquals(listOf<GameplayInteractionPulse>(released), actions())
        enter(GamePhase.RUNNING)
        touch { moveTo(0, Offset(730f, 530f)) }
        assertTrue(actions().isEmpty(), "${actions()}")
        assertEquals(listOf(Offset(730f, 530f)), steeredTo())
        touch { up(0) }
        // The steering slot was freed with its finger: a new finger steers.
        touch { down(2, Offset(400f, 300f)) }
        assertEquals(listOf(Offset(400f, 300f)), steeredTo())
    }

    @Test
    fun aTouchPlayerSeesAnActiveCardsSlotByNumberAndAKeyPressBringsBackItsKey() {
        start()
        // An offer dealing Solar Flare into the free X slot.
        val content = rewardActiveFixtureContent
        val offer = requireNotNull(running.renderModel).with("phase" to GamePhase.CHOICE, "content" to content,
            "choiceType" to ChoiceType.ITEM, "choices" to immutableListOf(rewardActiveChoice(rewardFixtureActiveBase, slot = 1)),
            "itemStacks" to List(content.items.size) { 0 }.toImmutableList())
        fun chips(): List<String> = compose.onAllNodes(hasAnyAncestor(hasTestTag("kinetickk.gameplay.choice.1.chips")) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        fun show(model: kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel?) {
            current = if (model == null) running else running.withTouchModel(model)
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
        }

        touch { down(0, Offset(700f, 500f)) }
        touch { up(0) }
        show(offer)
        assertTrue("Slot 2" in chips(), "a touch player reads the slot's number: ${chips()}")

        show(null)
        compose.onNodeWithTag("kinetickk.gameplay").performKeyInput { pressKey(Key.A) }
        compose.mainClock.advanceTimeByFrame()
        show(offer)
        assertTrue("Active X" in chips(), "a keyboard player reads the slot's key: ${chips()}")
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
