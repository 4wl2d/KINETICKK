// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.click
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.GameplayContent
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.interaction.canvas.hudTestSnapshot
import kinetickk.ball.gameplay.interaction.canvas.withModel
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GameplayRewardPreparationTest {
    @Test
    fun framesWaitForPreparationAndOnlyTheRealChoiceCanBeSelected() = runDesktopComposeUiTest(1440, 900) {
        val run = FrozenRun(model(GamePhase.CHOICE))
        mainClock.autoAdvance = false
        setContent { Host(run) }
        waitForIdle()
        assertEquals(0, run.frames, "Preparation must precede the first gameplay frame")
        onAllNodesWithTag("kinetickk.gameplay.choice.1", useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("kinetickk.gameplay").performKeyInput { pressKey(Key.Two) }
        assertEquals(0, run.selections, "A shortcut cannot accept a choice before its cards become visible")
        mainClock.advanceTimeByFrame()
        waitForIdle()
        assertEquals(0, run.frames, "The preparation callback must not advance simulation")
        mainClock.advanceTimeBy(2500)
        waitForIdle()
        assertTrue(run.frames > 0, "The real gameplay loop must start after preparation")
        assertEquals(0, run.selections, "Preparing sample cards must not accept a reward")
        for (index in 1..3) onAllNodesWithTag("kinetickk.gameplay.choice.$index", useUnmergedTree = true).assertCountEquals(1)
        onAllNodesWithTag("kinetickk.gameplay.choice.4", useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("kinetickk.gameplay.choice.2").performClick()
        assertEquals(1, run.selections)
        assertEquals(1, run.pulses.filterIsInstance<GameplayInteractionPulse.ChoiceSelected>().single().index)
    }

    @Test
    fun replacingAndRemovingAnAwaitingRunCannotReadyItsSuccessorOrKeepItsLoopAlive() = runDesktopComposeUiTest(1440, 900) {
        val first = FrozenRun(model(GamePhase.CHOICE))
        val second = FrozenRun(model(GamePhase.CHOICE))
        val third = FrozenRun(model(GamePhase.CHOICE))
        val owner = mutableStateOf(first)
        val shown = mutableStateOf(true)
        mainClock.autoAdvance = false
        setContent { if (shown.value) Host(owner.value) }
        waitForIdle()
        assertEquals(0, first.frames)
        runOnIdle { owner.value = second }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        assertEquals(0, second.frames, "A replacement must perform its own preparation")
        onAllNodesWithTag("kinetickk.gameplay.choice.1", useUnmergedTree = true).assertCountEquals(0)
        runOnIdle { shown.value = false }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        val firstFrames = first.frames
        val secondFrames = second.frames
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        assertEquals(firstFrames, first.frames, "The removed first owner must stay stopped")
        assertEquals(secondFrames, second.frames, "The removed second owner must stay stopped")
        onAllNodesWithTag("kinetickk.gameplay.choice.1", useUnmergedTree = true).assertCountEquals(0)
        runOnIdle { owner.value = third; shown.value = true }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        assertEquals(0, third.frames)
        onAllNodesWithTag("kinetickk.gameplay.choice.1", useUnmergedTree = true).assertCountEquals(0)
        mainClock.advanceTimeBy(2500)
        waitForIdle()
        assertTrue(third.frames > 0)
        assertEquals(firstFrames, first.frames)
        assertEquals(secondFrames, second.frames)
        val currentChoice = onNodeWithTag("kinetickk.gameplay.choice.1").assertIsDisplayed().assertIsEnabled()
        val currentBounds = currentChoice.fetchSemanticsNode().boundsInRoot
        currentChoice.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(0, first.selections)
        assertEquals(0, second.selections)
        assertEquals(1, third.selections, "The current owner must receive semantic activation at $currentBounds")
        // Desktop hover previews and lifts an unselected card before the press; wait for that layout.
        currentChoice.performMouseInput { moveTo(center) }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        currentChoice.performMouseInput { click(center) }
        assertEquals(0, first.selections)
        assertEquals(0, second.selections)
        assertEquals(2, third.selections, "The current owner must also receive the physical desktop click")
    }

    @Test
    fun emptyAndNonEpicCatalogsBecomePlayableWithoutLeavingSampleChoiceNodes() {
        val catalogs = listOf(
            rewardFixtureContent.copy(items = immutableListOf()),
            rewardFixtureContent.copy(items = rewardFixtureContent.items.map { it.copy(rarity = ItemRarity.COMMON) }.toImmutableList()),
        )
        for (content in catalogs) runDesktopComposeUiTest(1440, 900) {
            val run = FrozenRun(model(GamePhase.RUNNING, content))
            mainClock.autoAdvance = false
            setContent { Host(run) }
            waitForIdle()
            assertEquals(0, run.frames)
            mainClock.advanceTimeBy(2500)
            waitForIdle()
            assertTrue(run.frames > 0, "Preparation must finish with ${content.items.size} non-epic items")
            for (index in 1..4) onAllNodesWithTag("kinetickk.gameplay.choice.$index", useUnmergedTree = true).assertCountEquals(0)
            assertEquals(0, run.selections)
            onNodeWithTag("kinetickk.gameplay.dash").performClick()
            assertEquals(1, run.pulses.count { it == GameplayInteractionPulse.DashRequested }, "Controls must work after preparation")
        }
    }

    @Composable
    private fun Host(run: FrozenRun) {
        CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.English) {
            Box(Modifier.requiredSize(1440.dp, 900.dp)) { GameplayContent(run, true, {}) }
        }
    }

    private fun model(phase: GamePhase, content: GameplayContentSnapshot = shippedRewardContent): GameplayRenderModel =
        rewardFixtureModel(phase = phase, content = content, width = 1440f, height = 900f,
            choices = if (phase == GamePhase.CHOICE) content.items.take(3).map {
                ChoiceOption(ChoiceType.ITEM, it.name, it.description, it.rarity.displayLabel, itemId = it.id)
            } else emptyList())

    private class FrozenRun(model: GameplayRenderModel) : GameplayInteractionPort {
        val pulses = mutableListOf<GameplayInteractionPulse>()
        val frames get() = pulses.count { it is GameplayInteractionPulse.FrameElapsed }
        val selections get() = pulses.count { it is GameplayInteractionPulse.ChoiceSelected }
        private val snapshot = hudTestSnapshot(1440f, 900f).withModel(model)
        override val instanceId get() = snapshot.instanceId
        override fun renderSnapshot() = snapshot
        override fun visualFxSnapshot() = VisualFxProjection.EMPTY
        override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance {
            pulses += pulse
            return GameplayAcceptance.Accepted(snapshot.instanceId, snapshot.revision)
        }
    }
}
