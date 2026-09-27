// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import kotlin.test.Test
import kotlin.test.assertEquals
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

            }
        }
    }

    @Test
    fun desktopControlsShareOneBaselineAndKeepComfortableTargets() {
        val controls = runningControlBounds(width = 1_280f, height = 720f, scale = 1f)
            .associateBy { it.target }

        val dash = controls.getValue(RunningControlTarget.DASH).bounds
        val brake = controls.getValue(RunningControlTarget.BRAKE).bounds
        assertEquals(1_180f, dash.center.x)
        assertEquals(1_008f, brake.center.x)
        assertEquals(668f, dash.center.y)
        assertEquals(dash.center.y, brake.center.y)
        assertTrue(brake.width >= 48f && dash.width >= 48f)
        assertTrue(brake.right < dash.left)
    }
}
