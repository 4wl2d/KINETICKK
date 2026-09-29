// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
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
                val build = runningBuildButtonBounds(width, height, device.density)
                assertTouchTargets(device, width, height, running.map { it.bounds } + build)
                assertNoOverlap(device, running.map { it.target.name to it.bounds } + ("BUILD" to build))
            }
        }
    }

    @Test
    fun phoneDashAndBrakeSitAtTheThumbSidesOfTheMobileBoards() {
        val landscape = runningControlBounds(width = 844f, height = 390f, scale = 1f).associate { it.target to it.bounds }
        val dash = landscape.getValue(RunningControlTarget.DASH)
        val brake = landscape.getValue(RunningControlTarget.BRAKE)
        // Mobile-HUD: Dash 104 × 84 above-right of Brake 84 × 64, both inside the right thumb box.
        assertEquals(Rect(696f, 222f, 800f, 306f), dash)
        assertEquals(Rect(610f, 308f, 694f, 372f), brake)
        assertTrue(dash.width > brake.width && dash.height > brake.height)

        val portrait = runningControlBounds(width = 390f, height = 844f, scale = 1f).associate { it.target to it.bounds }
        // Mobile-Portrait: Brake bottom-left, the larger Dash bottom-right on one baseline.
        assertEquals(Rect(16f, 738f, 120f, 808f), portrait.getValue(RunningControlTarget.BRAKE))
        assertEquals(Rect(254f, 728f, 374f, 808f), portrait.getValue(RunningControlTarget.DASH))
        val pause = portrait.getValue(RunningControlTarget.PAUSE)
        assertEquals(78f, pause.center.y)
        assertTrue(pause.right <= 390f - 16f + 2f)
    }

    @Test
    fun desktopControlsShareOneBaselineAboveTheLoadoutAndPauseSitsTopRight() {
        val width = 1_280f
        val height = 720f
        val controls = runningControlBounds(width, height, scale = 1f).associateBy { it.target }
        assertEquals(setOf(RunningControlTarget.DASH, RunningControlTarget.BRAKE, RunningControlTarget.PAUSE), controls.keys)

        val dash = controls.getValue(RunningControlTarget.DASH).bounds
        val brake = controls.getValue(RunningControlTarget.BRAKE).bounds
        val pause = controls.getValue(RunningControlTarget.PAUSE).bounds
        assertEquals(dash.center.y, brake.center.y)
        assertTrue(brake.right < dash.left)
        assertTrue(brake.width >= 48f && dash.width >= 48f && dash.height >= 48f)
        assertTrue(pause.width >= 48f && pause.height >= 48f)
        // Dash hugs the right margin; the loadout block (relics + weapon) stays free below the row.
        assertEquals(width - 32f, dash.right)
        val loadoutTop = height - (REGULAR_HUD_BOTTOM_DP + REGULAR_LOADOUT_HEIGHT_DP)
        assertTrue(dash.bottom < loadoutTop, "controls collide with the loadout cluster")
        // Pause is the top-right header button; Build sits beside it without overlapping.
        assertTrue(pause.top < 60f && pause.right <= width)
        val build = runningBuildButtonBounds(width, height, 1f)
        assertTrue(build.right <= pause.left)
        assertEquals(pause.center.y, build.center.y)
    }

    @Test
    fun narrowDesktopWindowsShrinkTheHudButKeepComfortableTargets() {
        val controls = runningControlBounds(width = 800f, height = 600f, scale = 1f)
        assertTrue(controls.all { it.bounds.width >= 48f && it.bounds.height >= 48f })
        assertEquals(0.72f, regularHudUnit(800f, 1f), 0.001f)
        assertEquals(1f, regularHudUnit(1_440f, 1f))
        assertEquals(2f, regularHudUnit(2_880f, 2f))
    }
}
