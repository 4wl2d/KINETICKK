// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameplayLayoutGeometryTest {
    @Test
    fun fourTargetDeviceClassesKeepGameplayControlsInsideBothOrientations() {
        TargetDeviceProfiles.forEach { device ->
            listOf(
                device.widthPx to device.heightPx,
                device.heightPx to device.widthPx,
            ).forEach { (width, height) ->
                assertTrue(gameplayLayoutMode(width, height, device.density) != GameplayLayoutMode.REGULAR)

                val running = runningControlBounds(width, height, device.density)
                assertEquals(RunningControlTarget.entries.toSet(), running.map { it.target }.toSet())
                assertTouchTargets(device, width, height, running.map { it.bounds })
                assertNoOverlap(device, running.map { it.target.name to it.bounds })

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

    @Test
    fun regularDesktopGeometryPreservesExistingControlCenters() {
        val controls = runningControlBounds(width = 1_280f, height = 720f, scale = 1f)
            .associateBy { it.target }

        assertEquals(1_198f, controls.getValue(RunningControlTarget.DASH).bounds.center.x)
        assertEquals(632f, controls.getValue(RunningControlTarget.DASH).bounds.center.y)
        assertEquals(1_090f, controls.getValue(RunningControlTarget.BRAKE).bounds.center.x)
        assertEquals(653f, controls.getValue(RunningControlTarget.BRAKE).bounds.center.y)
    }
}

private fun assertTouchTargets(
    device: TargetDeviceProfile,
    viewportWidth: Float,
    viewportHeight: Float,
    targets: List<Rect>,
) {
    targets.forEach { bounds ->
        assertTrue(bounds.left >= 0f, "${device.name} target starts left of the viewport")
        assertTrue(bounds.top >= 0f, "${device.name} target starts above the viewport")
        assertTrue(bounds.right <= viewportWidth, "${device.name} target ends right of the viewport")
        assertTrue(bounds.bottom <= viewportHeight, "${device.name} target ends below the viewport")
        assertTrue(bounds.width / device.density >= 48f, "${device.name} target is too narrow")
        assertTrue(bounds.height / device.density >= 48f, "${device.name} target is too short")
    }
}

private fun assertNoOverlap(device: TargetDeviceProfile, targets: List<Pair<String, Rect>>) {
    targets.forEachIndexed { index, first ->
        targets.drop(index + 1).forEach { second ->
            assertFalse(
                first.second.left < second.second.right &&
                    first.second.right > second.second.left &&
                    first.second.top < second.second.bottom &&
                    first.second.bottom > second.second.top,
                "${device.name} overlaps ${first.first} and ${second.first}",
            )
        }
    }
}

private data class TargetDeviceProfile(
    val name: String,
    val widthPx: Float,
    val heightPx: Float,
    val density: Float,
)

private val TargetDeviceProfiles = listOf(
    TargetDeviceProfile("CPH2411", widthPx = 1_080f, heightPx = 2_412f, density = 3f),
    TargetDeviceProfile("RMX2002", widthPx = 1_080f, heightPx = 2_400f, density = 3f),
    TargetDeviceProfile("SM-A325F", widthPx = 1_080f, heightPx = 2_400f, density = 2.625f),
    TargetDeviceProfile("Redmi Note 9 Pro", widthPx = 1_080f, heightPx = 2_400f, density = 2.75f),
)
