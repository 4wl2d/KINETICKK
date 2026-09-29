// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.interaction.GAMEPLAY_TRIAL_INFO_TAG
import kinetickk.ball.gameplay.interaction.GameplayContent
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.runningBuildButtonBounds
import kinetickk.ball.gameplay.interaction.layout.runningControlBounds
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.LocalAppLanguage
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GameplayHudSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var trialModel: kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel

    /** Bone pixels in the band just below the trial panel, where its rules slip opens. */
    private fun tooltipPixels(): Int {
        val host = compose.onNodeWithTag(HOST_TAG).getUnclippedBoundsInRoot()
        val panel = HudTrialPanelLayout().update(1_440f, 810f, 1f, trialModel.settings.textScale)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val bone = Kk.Bone.toArgb()
        var count = 0
        val top = (host.top.value + panel.bottom).toInt() + 14
        for (y in top until (top + 40).coerceAtMost(pixels.height)) {
            for (x in (host.left.value + panel.left).toInt() until (host.left.value + panel.right).toInt().coerceAtMost(pixels.width)) {
                if (pixels[x, y].toArgb() == bone) count++
            }
        }
        return count
    }

    private fun startTrial(pulses: MutableList<GameplayInteractionPulse>) {
        val snapshot = hudTestSnapshot()
        val orbit = PointOfInterestProjection(PointOfInterestKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f, true, 12f, 0, 0.4f,
            immutableListOf(), 0f, 0f)
        trialModel = requireNotNull(snapshot.renderModel).with("pointsOfInterest" to listOf(orbit).toImmutableList())
        val running = snapshot.withModel(trialModel)
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
                Box(Modifier.requiredSize(1_440.dp, 810.dp).testTag(HOST_TAG)) { GameplayContent(port, true) {} }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun tappingTheTrialInfoOpensTheRulesWithoutSteeringAndTappingElsewhereClosesThemAndSteers() {
        val pulses = mutableListOf<GameplayInteractionPulse>()
        startTrial(pulses)
        val info = HudTrialPanelLayout().update(1_440f, 810f, 1f, trialModel.settings.textScale).infoTarget(1f).center
        val closed = tooltipPixels()
        fun steered() = pulses.any { it is GameplayInteractionPulse.PointerMoved }

        pulses.clear()
        compose.onNodeWithTag(HOST_TAG).performTouchInput { click(info) }
        compose.mainClock.advanceTimeByFrame()
        assertFalse(steered(), "a tap on the (!) steered the singularity")
        assertTrue(tooltipPixels() > closed + 2_000, "a tap on the (!) did not open the rules")

        pulses.clear()
        compose.onNodeWithTag(HOST_TAG).performTouchInput { click(Offset(700f, 500f)) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(steered(), "a tap elsewhere did not steer")
        assertTrue(tooltipPixels() < closed + 500, "a tap elsewhere left the rules open")

        // A second tap on the (!) toggles: open, then closed again.
        compose.onNodeWithTag(HOST_TAG).performTouchInput { click(info) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(tooltipPixels() > closed + 2_000)
        compose.onNodeWithTag(HOST_TAG).performTouchInput { click(info) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(tooltipPixels() < closed + 500, "a second tap did not close the rules")
    }

    @Test
    fun aHoveringMouseShowsTheTrialRulesAndDoesNotSteerOverTheInfo() {
        val pulses = mutableListOf<GameplayInteractionPulse>()
        startTrial(pulses)
        val info = HudTrialPanelLayout().update(1_440f, 810f, 1f, trialModel.settings.textScale).infoTarget(1f).center
        val closed = tooltipPixels()
        pulses.clear()
        compose.onNodeWithTag(HOST_TAG).performMouseInput { moveTo(info) }
        compose.mainClock.advanceTimeByFrame()
        assertFalse(pulses.any { it is GameplayInteractionPulse.PointerMoved }, "hovering the (!) steered")
        assertTrue(tooltipPixels() > closed + 2_000, "hovering the (!) did not show the rules")
        compose.onNodeWithTag(HOST_TAG).performMouseInput { moveTo(Offset(700f, 500f)) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(pulses.any { it is GameplayInteractionPulse.PointerMoved })
        assertTrue(tooltipPixels() < closed + 500, "the rules stayed open after the mouse left")
    }

    @Test
    fun keyboardFocusReturnsToTheGameWhenTheTrialEndsWhileItsInfoIsFocused() {
        val snapshot = hudTestSnapshot()
        val orbit = PointOfInterestProjection(PointOfInterestKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f, true, 12f, 0, 0.4f,
            immutableListOf(), 0f, 0f)
        val base = requireNotNull(snapshot.renderModel)
        val withTrial = snapshot.withModel(base.with("pointsOfInterest" to listOf(orbit).toImmutableList()))
        val afterTrial = snapshot.withModel(base.with("pointsOfInterest" to immutableListOf<PointOfInterestProjection>()))
        var current = withTrial
        var trialEnds = false
        val pulses = mutableListOf<GameplayInteractionPulse>()
        val port = object : GameplayInteractionPort {
            override val instanceId get() = current.instanceId
            override fun renderSnapshot() = current
            override fun visualFxSnapshot() = VisualFxProjection.EMPTY
            override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance {
                pulses += pulse
                // The next frame completes the trial: the render model no longer has an active trial.
                if (trialEnds && pulse is GameplayInteractionPulse.FrameElapsed) {
                    current = afterTrial
                    return GameplayAcceptance.Accepted(current.instanceId, current.revision)
                }
                return GameplayAcceptance.Rejected(current.instanceId, current.revision, GameplayRejection.RunExited)
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(1_440.dp, 810.dp).testTag(HOST_TAG)) { GameplayContent(port, true) {} }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val info = compose.onNodeWithTag(GAMEPLAY_TRIAL_INFO_TAG)
        info.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        info.assertIsFocused()

        trialEnds = true
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag(GAMEPLAY_TRIAL_INFO_TAG).assertDoesNotExist()
        pulses.clear()
        compose.onNodeWithTag(GAMEPLAY_ROOT_TAG).performKeyInput { pressKey(Key.P) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(GameplayInteractionPulse.PauseToggled in pulses, "P did nothing after the trial ended: $pulses")
        pulses.clear()
        compose.onNodeWithTag(GAMEPLAY_ROOT_TAG).performKeyInput { pressKey(Key.Spacebar) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(GameplayInteractionPulse.DashRequested in pulses, "Space did nothing after the trial ended: $pulses")
    }

    @Test
    fun trialRulesSitBehindAFocusableInfoNodeAndDesktopPauseAndBuildAreHeaderButtons() {
        val snapshot = hudTestSnapshot()
        val orbit = PointOfInterestProjection(PointOfInterestKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f, true, 12f, 0, 0.4f,
            immutableListOf(), 0f, 0f)
        val model = requireNotNull(snapshot.renderModel).with("pointsOfInterest" to listOf(orbit).toImmutableList())
        trialModel = model
        val running = snapshot.withModel(model)
        val pulses = mutableListOf<GameplayInteractionPulse>()
        val port = object : GameplayInteractionPort {
            override val instanceId get() = running.instanceId
            override fun renderSnapshot() = running
            override fun visualFxSnapshot() = VisualFxProjection.EMPTY
            override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance {
                pulses += pulse
                return GameplayAcceptance.Rejected(running.instanceId, running.revision, GameplayRejection.RunExited)
            }
        }
        // The gameplay frame loop never idles; drive the clock by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(1_440.dp, 810.dp).testTag(HOST_TAG)) { GameplayContent(port, true) {} }
            }
        }
        val rules = model.content.pointsOfInterest.definition(PointOfInterestKind.COLLAPSING_ORBIT).instruction
        val info = compose.onNodeWithTag(GAMEPLAY_TRIAL_INFO_TAG)
        info.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(rules)))
        compose.mainClock.advanceTimeByFrame()
        val before = tooltipPixels()
        info.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        info.assertIsFocused()
        compose.mainClock.advanceTimeByFrame()
        // Focus opens the bone tooltip slip with the rules below the panel.
        assertTrue(tooltipPixels() > before + 2_000, "the focused (!) shows no tooltip")

        compose.onNodeWithTag("kinetickk.gameplay.pause").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(GameplayInteractionPulse.PauseToggled in pulses)
        // Bounds relative to the 1440 × 810 host (the test window may be smaller and center it).
        val host = compose.onNodeWithTag(HOST_TAG).getUnclippedBoundsInRoot()
        fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let {
            Rect(it.left.value - host.left.value, it.top.value - host.top.value, it.right.value - host.left.value, it.bottom.value - host.top.value)
        }
        val controls = runningControlBounds(1_440f, 810f, 1f).associate { it.target to it.bounds }
        val pause = bounds("kinetickk.gameplay.pause")
        assertRect(controls.getValue(RunningControlTarget.PAUSE), pause)
        assertTrue(pause.top < 60f && pause.right > 1_380f, "pause is not top-right: $pause")
        assertRect(controls.getValue(RunningControlTarget.DASH), bounds("kinetickk.gameplay.dash"))
        assertRect(controls.getValue(RunningControlTarget.BRAKE), bounds("kinetickk.gameplay.brake"))
        val build = bounds("kinetickk.gameplay.build")
        assertRect(runningBuildButtonBounds(1_440f, 810f, 1f), build)
        assertTrue(build.right <= pause.left + 0.5f)
        assertRect(HudTrialPanelLayout().update(1_440f, 810f, 1f, model.settings.textScale).infoTarget(1f), bounds(GAMEPLAY_TRIAL_INFO_TAG))
    }
}

private const val HOST_TAG = "hud-host"
private const val GAMEPLAY_ROOT_TAG = "kinetickk.gameplay"

private fun assertRect(expected: Rect, actual: Rect) {
    assertEquals(expected.left, actual.left, 0.5f, "left of $actual")
    assertEquals(expected.top, actual.top, 0.5f, "top of $actual")
    assertEquals(expected.right, actual.right, 0.5f, "right of $actual")
    assertEquals(expected.bottom, actual.bottom, 0.5f, "bottom of $actual")
}

private fun GameplayRenderSnapshot.withModel(model: kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel): GameplayRenderSnapshot {
    val constructor = GameplayRenderSnapshot::class.java.declaredConstructors.first { it.parameterCount == 4 }
    constructor.isAccessible = true
    val identity = GameplayRenderSnapshot::class.java.declaredFields.first { it.name == "projectionSourceIdentity" }
        .also { it.isAccessible = true }.get(this)
    return constructor.newInstance(instanceId, revision, model, identity) as GameplayRenderSnapshot
}
