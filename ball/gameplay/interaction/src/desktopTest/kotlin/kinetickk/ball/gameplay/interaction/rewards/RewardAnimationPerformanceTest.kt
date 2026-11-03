// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.GameplayContent
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.interaction.canvas.hudTestSnapshot
import kinetickk.ball.gameplay.interaction.canvas.with
import kinetickk.ball.gameplay.interaction.canvas.withModel
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real gameplay host and reward UI; a frozen run removes simulation and persistence from the check. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeApi::class)
class RewardAnimationPerformanceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anIdleDealtHandAnimatesWithoutRecomposingItsContent() = exerciseDealtHand(showFps = false)

    @Test
    fun showingFpsKeepsRewardAnimationOutOfComposition() = exerciseDealtHand(showFps = true)

    private fun exerciseDealtHand(showFps: Boolean) {
        val content = shippedRewardContent
        val choices = content.items.filter { it.rarity == ItemRarity.EPIC }.take(4).map {
            ChoiceOption(ChoiceType.ITEM, it.name, it.description, it.rarity.displayLabel, itemId = it.id)
        }
        assertEquals(4, choices.size)
        val fixture = rewardFixtureModel(content = content, choices = choices, width = 1440f, height = 900f, textScale = 1.25f)
        val model = fixture.with("settings" to fixture.settings.copy(showFps = showFps))
        val run = FrozenChoiceRun(model)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(1440.dp, 900.dp)) { GameplayContent(run, true, {}) }
            }
        }
        // Initial text fit, font loading and the finite deal entrance have finished before sampling.
        compose.mainClock.advanceTimeBy(2400)
        compose.waitForIdle()
        val beforePixels = pixels()
        val recomposers = Recomposer.runningRecomposers.value.toList()
        assertTrue(recomposers.isNotEmpty(), "The real host must have a running recomposer")
        val changesBefore = recomposers.sumOf { it.changeCount }
        val framesBefore = run.frames
        val benchmark = !showFps && System.getenv("KK_REWARD_ANIMATION_BENCH") == "1"
        val frames = if (benchmark) 120 else 12
        val threads = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        val ids = threads?.allThreadIds ?: longArrayOf()
        val allocatedBefore = threads?.getThreadAllocatedBytes(ids)
        val started = System.nanoTime()
        repeat(frames) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        val nanos = System.nanoTime() - started
        val allocatedAfter = threads?.getThreadAllocatedBytes(ids)
        val bytes = if (allocatedBefore == null || allocatedAfter == null) -1L else ids.indices.sumOf {
            if (allocatedBefore[it] >= 0 && allocatedAfter[it] >= 0) allocatedAfter[it] - allocatedBefore[it] else 0L
        }
        val changes = recomposers.sumOf { it.changeCount } - changesBefore
        val elapsedFrames = run.frames - framesBefore
        val afterPixels = pixels()
        val changedPixels = beforePixels.indices.count { beforePixels[it] != afterPixels[it] }
        val record = "{\"label\":\"${System.getenv("KK_REWARD_ANIMATION_LABEL") ?: "test"}\",\"frames\":$frames," +
            "\"host_frames\":$elapsedFrames,\"show_fps\":$showFps,\"composition_changes\":$changes,\"changed_pixels\":$changedPixels," +
            "\"test_loop_ns\":$nanos,\"existing_threads_allocated_bytes\":$bytes}"
        println("REWARD_ANIMATION $record")
        if (benchmark) System.getenv("KK_REWARD_ANIMATION_OUT")?.let { path ->
            File(path).also { it.parentFile?.mkdirs() }.appendText("$record\n")
        }
        assertTrue(elapsedFrames >= frames - 1, "The production gameplay frame loop must keep running")
        assertTrue(changedPixels > 0, "The card art must keep animating while the run is frozen")
        assertEquals(0L, changes, "Animation ticks must invalidate drawing, without rebuilding the reward composition")
    }

    private fun pixels(): IntArray {
        val image = compose.onNodeWithTag("kinetickk.gameplay.rewards").captureToImage()
        val pixels = image.toPixelMap()
        return IntArray(image.width * image.height) { pixels[it % image.width, it / image.width].toArgb() }
    }

    private class FrozenChoiceRun(model: GameplayRenderModel) : GameplayInteractionPort {
        private val snapshot = hudTestSnapshot(1440f, 900f).withModel(model)
        var frames = 0
            private set
        override val instanceId get() = snapshot.instanceId
        override fun renderSnapshot() = snapshot
        override fun visualFxSnapshot() = VisualFxProjection.EMPTY
        override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance {
            if (pulse is GameplayInteractionPulse.FrameElapsed) frames++
            return GameplayAcceptance.Accepted(snapshot.instanceId, snapshot.revision)
        }
    }
}
