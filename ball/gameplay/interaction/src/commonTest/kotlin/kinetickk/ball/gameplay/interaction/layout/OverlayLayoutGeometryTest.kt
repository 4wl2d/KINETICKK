// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OverlayLayoutGeometryTest {
    @Test
    fun fourTargetDeviceClassesKeepOverlayTargetsInsideBothOrientations() {
        TargetDeviceProfiles.forEach { device ->
            listOf(
                device.widthPx to device.heightPx,
                device.heightPx to device.widthPx,
            ).forEach { (width, height) ->
                val pause = pauseLayoutGeometry(width, height, device.density)
                assertEquals(PauseTarget.entries.toSet(), pause.actions.map { it.target }.toSet())
                assertTouchTargets(device, width, height, pause.actions.map { it.bounds })
                assertNoOverlap(device, pause.actions.map { it.target.name to it.bounds })
                assertTrue(pause.actions.none { it.bounds.overlaps(pause.build) }, "${device.name} menu stays off the build overview")

                listOf(2, 3, 4).forEach { count ->
                    val choice = choiceLayoutGeometry(
                        width = width,
                        height = height,
                        scale = device.density,
                        choiceCount = count,
                        canReroll = true,
                    )
                    assertEquals(count, choice.cards.size)
                    assertTouchTargets(device, width, height, choice.cards + listOfNotNull(choice.reroll) + choice.take + choice.build)
                    assertNoOverlap(
                        device,
                        choice.cards.mapIndexed { index, rect -> "choice-${index + 1}" to rect } +
                            listOf("reroll" to assertNotNull(choice.reroll), "take" to choice.take, "build" to choice.build),
                    )
                }

                val terminal = terminalLayoutGeometry(width, height, device.density, victory = true)
                val terminalTargets = listOf(terminal.restart, assertNotNull(terminal.rebirth), terminal.exit)
                assertTouchTargets(device, width, height, terminalTargets)
                assertNoOverlap(
                    device,
                    listOf(
                        "restart" to terminal.restart,
                        "rebirth" to assertNotNull(terminal.rebirth),
                        "exit" to terminal.exit,
                    ),
                )
            }
        }
    }

    @Test
    fun desktopPauseMenuStepsDownTheLeftPanelBesideTheBuildOverview() {
        listOf(1440f to 810f, 1280f to 720f, 1000f to 720f, 1920f to 1080f).forEach { (width, height) ->
            val pause = pauseLayoutGeometry(width, height, 1f)
            assertEquals(GameplayLayoutMode.REGULAR, pause.mode)
            // The game's desktop pause targets, in menu order; no invented entries.
            assertEquals(listOf(PauseTarget.RESUME, PauseTarget.SETTINGS, PauseTarget.CODEX, PauseTarget.EXIT), pause.actions.map { it.target })
            pause.actions.zipWithNext().forEach { (upper, lower) ->
                assertTrue(upper.bounds.bottom <= lower.bounds.top, "$width x $height items do not overlap")
                assertTrue(upper.bounds.left < lower.bounds.left, "$width x $height items step right (Pause board)")
            }
            pause.actions.forEach { action ->
                assertTrue(action.bounds.height >= 48f, "$width x $height ${action.target} hit height")
                assertTrue(action.bounds.right <= pause.navClip.right, "$width x $height ${action.target} inside the panel")
                assertTrue(Rect(0f, 0f, width, height).contains(action.bounds.bottomRight), "$width x $height ${action.target} on screen")
                assertTrue(action.bounds.top > pause.titleY, "$width x $height ${action.target} below the title")
            }
            assertTrue(pause.build.left > pause.navClip.right, "$width x $height build overview right of the menu")
            assertTrue(pause.build.right <= width && pause.build.bottom <= height)
        }
    }

    @Test
    fun regularRewardFooterCentersRerollTakeAndBuildUnderTheBoardCards() {
        val choice = choiceLayoutGeometry(1440f, 810f, 1f, 3, canReroll = true)
        // LevelUp board: 300 x 450 cards from y 170, 34 apart; footer at y 724.
        choice.cards.forEach { card ->
            assertEquals(300f, card.width)
            assertEquals(450f, card.height)
            assertEquals(170f, card.top, 0.5f)
        }
        assertEquals(34f, choice.cards[1].left - choice.cards[0].right)
        val reroll = assertNotNull(choice.reroll)
        assertEquals(724f, choice.take.top)
        assertTrue(reroll.right < choice.take.left && choice.take.right < choice.build.left)
        assertEquals(720f, (reroll.left + choice.build.right) / 2f, 0.5f)
        val withoutReroll = choiceLayoutGeometry(1440f, 810f, 1f, 3, canReroll = false)
        assertEquals(null, withoutReroll.reroll)
        assertEquals(720f, (withoutReroll.take.left + withoutReroll.build.right) / 2f, 0.5f)
    }
}
