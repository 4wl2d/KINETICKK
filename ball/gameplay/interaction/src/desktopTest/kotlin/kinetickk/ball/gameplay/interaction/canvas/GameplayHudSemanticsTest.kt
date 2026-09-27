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
import androidx.compose.ui.test.performSemanticsAction
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
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GameplayHudSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun trialRulesSitBehindAFocusableInfoNodeAndDesktopPauseAndBuildAreHeaderButtons() {
        val snapshot = hudTestSnapshot()
        val orbit = PointOfInterestProjection(PointOfInterestKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f, true, 12f, 0, 0.4f,
            immutableListOf(), 0f, 0f)
        val model = requireNotNull(snapshot.renderModel).with("pointsOfInterest" to listOf(orbit).toImmutableList())
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
        val infoBounds = info.getUnclippedBoundsInRoot()
        fun bonePixelsBelowInfo(): Int {
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val bone = Kk.Bone.toArgb()
            var count = 0
            val top = infoBounds.bottom.value.toInt()
            for (y in top until (top + 60).coerceAtMost(pixels.height)) {
                for (x in (infoBounds.left.value.toInt() - 120).coerceAtLeast(0) until (infoBounds.right.value.toInt() + 120).coerceAtMost(pixels.width)) {
                    if (pixels[x, y].toArgb() == bone) count++
                }
            }
            return count
        }
        compose.mainClock.advanceTimeByFrame()
        val before = bonePixelsBelowInfo()
        info.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        info.assertIsFocused()
        compose.mainClock.advanceTimeByFrame()
        // Focus opens the bone tooltip slip with the rules below the (!).
        assertTrue(bonePixelsBelowInfo() > before + 2_000, "the focused (!) shows no tooltip")

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
