// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileGameUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeActionsStayInsideTheSafeViewportAndDoNotOverlap() {
        freezeContinuousFrameClock()
        composeRule.awaitTag(HOME_SCREEN_TAG)
        val screenBounds = composeRule.onNodeWithTag(HOME_SCREEN_TAG).visibleBounds()
        val actionTags = listOf(
            HOME_START_TAG,
            HOME_LAB_TAG,
            HOME_ARMORY_TAG,
            HOME_REBIRTH_TAG,
            HOME_CODEX_TAG,
            HOME_SETTINGS_TAG,
        )

        val actionBounds = actionTags.map { tag ->
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertMinimumTouchTarget()
                .visibleBounds()
                .also { bounds -> assertInside(screenBounds, bounds, tag) }
        }
        assertNoOverlaps(actionTags, actionBounds)
    }

    @Test
    fun gameplayTouchControlsSupportRunPauseResumeAndExit() {
        freezeContinuousFrameClock()
        composeRule.awaitTag(HOME_START_TAG)
        composeRule.onNodeWithTag(HOME_START_TAG)
            .assertHasClickAction()
            .performTouchInput {
                down(center)
                up()
            }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.awaitTag(GAMEPLAY_SCREEN_TAG)
        // The first gameplay layout publishes the measured Android viewport back through the
        // gameplay port. Advance once more so controls use that density-aware projection instead
        // of the deterministic desktop bootstrap viewport.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.awaitTag(GAMEPLAY_PAUSE_TAG)

        val gameplayBounds = composeRule.onNodeWithTag(GAMEPLAY_SCREEN_TAG)
            .assertIsDisplayed()
            .visibleBounds()
        val runningTags = listOf(
            GAMEPLAY_BRAKE_TAG,
            GAMEPLAY_DASH_TAG,
            GAMEPLAY_PERFORMANCE_TAG,
            GAMEPLAY_PAUSE_TAG,
        )
        val runningBounds = runningTags.map { tag ->
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertMinimumTouchTarget()
                .visibleBounds()
                .also { bounds -> assertInside(gameplayBounds, bounds, tag) }
        }
        assertNoOverlaps(runningTags, runningBounds)
        assertTrue(
            "Brake must expose focus semantics for keyboard and accessibility navigation",
            composeRule.onNodeWithTag(GAMEPLAY_BRAKE_TAG)
                .fetchSemanticsNode()
                .config
                .contains(SemanticsProperties.Focused),
        )

        composeRule.onNodeWithTag(GAMEPLAY_BRAKE_TAG).performTouchInput {
            down(center)
            advanceEventTime(120)
            up()
        }
        val brakeControl = composeRule.onNodeWithTag(GAMEPLAY_BRAKE_TAG).requestFocus()
        brakeControl.performKeyInput { pressKey(Key.Spacebar) }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(
            "pressed",
            composeRule.onNodeWithTag(GAMEPLAY_BRAKE_TAG)
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription],
        )
        composeRule.onNodeWithTag(GAMEPLAY_BRAKE_TAG).performKeyInput { pressKey(Key.Spacebar) }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(
            "released",
            composeRule.onNodeWithTag(GAMEPLAY_BRAKE_TAG)
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription],
        )
        composeRule.onNodeWithTag(GAMEPLAY_DASH_TAG)
            .assertHasClickAction()
            .performTouchInput {
                down(center)
                up()
            }
        composeRule.onNodeWithTag(GAMEPLAY_PERFORMANCE_TAG).performClick()
        composeRule.onNodeWithTag(GAMEPLAY_PAUSE_TAG).performClick()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.awaitTag(GAMEPLAY_RESUME_TAG)
        listOf(GAMEPLAY_BRAKE_TAG, GAMEPLAY_DASH_TAG, GAMEPLAY_PAUSE_TAG).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
        val pausedTags = listOf(
            GAMEPLAY_RESUME_TAG,
            GAMEPLAY_SETTINGS_TAG,
            GAMEPLAY_PERFORMANCE_TAG,
            GAMEPLAY_EXIT_TAG,
        )
        val pausedBounds = pausedTags.map { tag ->
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertMinimumTouchTarget()
                .visibleBounds()
                .also { bounds -> assertInside(gameplayBounds, bounds, tag) }
        }
        assertNoOverlaps(pausedTags, pausedBounds)
        composeRule.onNodeWithTag(GAMEPLAY_RESUME_TAG)
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.awaitTag(GAMEPLAY_PAUSE_TAG)
        composeRule.onNodeWithTag(GAMEPLAY_PAUSE_TAG).performClick()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.awaitTag(GAMEPLAY_EXIT_TAG)
        composeRule.onNodeWithTag(GAMEPLAY_EXIT_TAG)
            .assertIsDisplayed()
            .assertMinimumTouchTarget()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.awaitTag(HOME_SCREEN_TAG)
    }

    private fun SemanticsNodeInteraction.assertMinimumTouchTarget(): SemanticsNodeInteraction =
        assertWidthIsAtLeast(MINIMUM_TOUCH_TARGET_DP.dp)
            .assertHeightIsAtLeast(MINIMUM_TOUCH_TARGET_DP.dp)

    private fun freezeContinuousFrameClock() {
        // Home and gameplay intentionally publish a state on every frame. Compose's default
        // test clock advances frame awaiters while waiting for idleness, so a real app host can
        // never become idle. Keep the clock manual; interaction helpers still drain the UI queue.
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun SemanticsNodeInteraction.visibleBounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun assertInside(container: Rect, child: Rect, tag: String) {
        assertTrue("$tag left edge $child is outside $container", child.left >= container.left)
        assertTrue("$tag top edge $child is outside $container", child.top >= container.top)
        assertTrue("$tag right edge $child is outside $container", child.right <= container.right)
        assertTrue("$tag bottom edge $child is outside $container", child.bottom <= container.bottom)
    }

    private fun assertNoOverlaps(tags: List<String>, bounds: List<Rect>) {
        bounds.indices.forEach { leftIndex ->
            (leftIndex + 1 until bounds.size).forEach { rightIndex ->
                assertFalse(
                    "${tags[leftIndex]} overlaps ${tags[rightIndex]}: " +
                        "${bounds[leftIndex]} vs ${bounds[rightIndex]}",
                    bounds[leftIndex].overlaps(bounds[rightIndex]),
                )
            }
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.awaitTag(tag: String) {
        waitUntil(timeoutMillis = UI_TIMEOUT.inWholeMilliseconds) {
            // With the clock frozen, a slower device can launch the Activity after the single
            // bootstrap frame. Drive one deterministic frame per poll until Compose publishes
            // its first hierarchy; keep every subsequent tag assertion exact.
            mainClock.advanceTimeByFrame()
            try {
                onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            } catch (failure: IllegalStateException) {
                if (failure.message?.startsWith(NO_COMPOSE_HIERARCHIES_PREFIX) != true) {
                    throw failure
                }
                false
            }
        }
        onNode(hasTestTag(tag), useUnmergedTree = true).assertIsDisplayed()
    }

    private companion object {
        const val HOME_SCREEN_TAG = "kinetickk.home"
        const val HOME_START_TAG = "kinetickk.home.start"
        const val HOME_LAB_TAG = "kinetickk.home.lab"
        const val HOME_ARMORY_TAG = "kinetickk.home.armory"
        const val HOME_REBIRTH_TAG = "kinetickk.home.rebirth"
        const val HOME_CODEX_TAG = "kinetickk.home.codex"
        const val HOME_SETTINGS_TAG = "kinetickk.home.settings"
        const val GAMEPLAY_SCREEN_TAG = "kinetickk.gameplay"
        const val GAMEPLAY_PAUSE_TAG = "kinetickk.gameplay.pause"
        const val GAMEPLAY_PERFORMANCE_TAG = "kinetickk.gameplay.performance"
        const val GAMEPLAY_DASH_TAG = "kinetickk.gameplay.dash"
        const val GAMEPLAY_BRAKE_TAG = "kinetickk.gameplay.brake"
        const val GAMEPLAY_RESUME_TAG = "kinetickk.gameplay.resume"
        const val GAMEPLAY_SETTINGS_TAG = "kinetickk.gameplay.settings"
        const val GAMEPLAY_EXIT_TAG = "kinetickk.gameplay.exit"
        const val MINIMUM_TOUCH_TARGET_DP = 48
        const val NO_COMPOSE_HIERARCHIES_PREFIX = "No compose hierarchies found in the app"
        val UI_TIMEOUT = 15.seconds
    }
}
