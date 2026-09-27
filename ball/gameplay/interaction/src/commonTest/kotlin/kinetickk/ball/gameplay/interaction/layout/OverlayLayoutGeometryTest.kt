// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

                val choice = choiceLayoutGeometry(
                    width = width,
                    height = height,
                    scale = device.density,
                    choiceCount = 4,
                    canReroll = true,
                )
                assertEquals(4, choice.cards.size)
                assertTouchTargets(device, width, height, choice.cards + listOfNotNull(choice.reroll))
                assertNoOverlap(
                    device,
                    choice.cards.mapIndexed { index, rect -> "choice-${index + 1}" to rect } +
                        listOf("reroll" to assertNotNull(choice.reroll)),
                )

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
}
