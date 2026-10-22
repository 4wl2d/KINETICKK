// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameplayLayoutGeometryTest {
    @Test
    fun fourTargetDeviceClassesKeepGameplayControlsInsideBothOrientations() {
        TargetDeviceProfiles.forEach { device ->
            listOf(
                device.widthPx to device.heightPx,
                device.heightPx to device.widthPx,
            ).forEach { (width, height) ->
                val mode = gameplayLayoutMode(width, height, device.density)
                assertTrue(mode != GameplayLayoutMode.REGULAR)

                val running = runningControlBounds(width, height, device.density)
                // Portrait phones keep performance in the pause menu: their touch row holds the skills.
                val expected = RunningControlTarget.entries.toSet() -
                    if (mode == GameplayLayoutMode.COMPACT_PORTRAIT) setOf(RunningControlTarget.PERFORMANCE) else emptySet()
                assertEquals(expected, running.map { it.target }.toSet())
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
        // Mobile-Portrait: Brake bottom-left, the larger Dash bottom-right on one baseline; the skill
        // squares between them narrow both (Brake 80, Dash 92 at the board width).
        assertEquals(Rect(16f, 738f, 96f, 808f), portrait.getValue(RunningControlTarget.BRAKE))
        assertEquals(Rect(282f, 728f, 374f, 808f), portrait.getValue(RunningControlTarget.DASH))
        val pause = portrait.getValue(RunningControlTarget.PAUSE)
        assertEquals(78f, pause.center.y)
        assertTrue(pause.right <= 390f - 16f + 2f)
    }

    @Test
    fun desktopControlsShareOneBaselineAboveTheLoadoutAndPauseSitsTopRight() {
        val width = 1_280f
        val height = 720f
        val controls = runningControlBounds(width, height, scale = 1f, mask = RunningControlMask.NONE).associateBy { it.target }
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

    /**
     * Every reference window: no mask places no skill or Apex target and every mask places only its
     * own; with all of them every target (and Build) is at least 48 dp, on screen and apart from the
     * others; the skill squares keep the Core's zone clear on landscape (w ≥ 550 dp) and portrait
     * phones, and portrait Brake and Dash keep ≥ 6 dp from the skill squares.
     */
    @Test
    fun runningControlsAreAtLeastFortyEightDpInEveryMode() {
        val skills = setOf(RunningControlTarget.SKILL_1, RunningControlTarget.SKILL_2, RunningControlTarget.SKILL_3)
        for ((w, h) in ReferenceWindows) for (scale in listOf(1f, 2f)) {
            val width = w * scale
            val height = h * scale
            val where = "$w x $h @$scale"
            val none = runningControlBounds(width, height, scale, RunningControlMask.NONE).map { it.target }.toSet()
            assertTrue(none.none { it in skills || it == RunningControlTarget.APEX }, "$where: mask 0 placed $none")
            val onlySkills = runningControlBounds(width, height, scale, RunningControlMask.SKILLS).map { it.target }.toSet()
            assertEquals(none + skills, onlySkills, where)
            val onlyApex = runningControlBounds(width, height, scale, RunningControlMask.APEX).map { it.target }.toSet()
            assertEquals(none + RunningControlTarget.APEX, onlyApex, where)

            val all = runningControlBounds(width, height, scale, RunningControlMask.ALL)
            assertEquals(none + skills + RunningControlTarget.APEX, all.map { it.target }.toSet(), where)
            val build = runningBuildButtonBounds(width, height, scale)
            val targets = all.map { it.target.name to it.bounds } + ("BUILD" to build)
            targets.forEach { (name, bounds) ->
                assertTrue(bounds.width >= 48f * scale - 0.01f && bounds.height >= 48f * scale - 0.01f, "$where: $name $bounds under 48 dp")
                assertTrue(bounds.left >= -0.01f && bounds.top >= -0.01f && bounds.right <= width + 0.01f && bounds.bottom <= height + 0.01f,
                    "$where: $name $bounds off screen")
            }
            targets.forEachIndexed { index, (first, a) ->
                targets.drop(index + 1).forEach { (second, b) -> assertFalse(a.overlaps(b), "$where: $first $a meets $second $b") }
            }
            val mode = gameplayLayoutMode(width, height, scale)
            val byTarget = all.associate { it.target to it.bounds }
            val clearance = CORE_ZONE_DP * scale
            val core = Rect(width * 0.5f - clearance, height * 0.5f - clearance, width * 0.5f + clearance, height * 0.5f + clearance)
            if (mode == GameplayLayoutMode.COMPACT_PORTRAIT || (mode == GameplayLayoutMode.COMPACT_LANDSCAPE && w >= 550f)) {
                skills.forEach { skill -> assertFalse(byTarget.getValue(skill).overlaps(core), "$where: $skill on the Core's zone") }
            }
            if (mode == GameplayLayoutMode.COMPACT_PORTRAIT) {
                val brake = byTarget.getValue(RunningControlTarget.BRAKE)
                val dash = byTarget.getValue(RunningControlTarget.DASH)
                assertTrue(byTarget.getValue(RunningControlTarget.SKILL_1).left - brake.right >= 6f * scale - 0.01f, "$where: Brake gap")
                assertTrue(dash.left - byTarget.getValue(RunningControlTarget.SKILL_3).right >= 6f * scale - 0.01f, "$where: Dash gap")
                assertFalse(RunningControlTarget.PERFORMANCE in byTarget, "$where: performance on portrait")
            }
        }
    }

    /** The skill squares, the Apex box and the portrait Build button at the design's reference sizes (±0.5 px). */
    @Test
    fun skillRowApexAndBuildMatchTheReferenceRects() {
        fun at(w: Float, h: Float) = runningControlBounds(w, h, 1f).associate { it.target to it.bounds }
        fun row(w: Float, h: Float, z: Float, x: Float, c: Float, top: Float, bottom: Float, side: Float) {
            val controls = at(w, h)
            assertRect(Rect(z, top, z + side, bottom), controls.getValue(RunningControlTarget.SKILL_1), "$w x $h Z")
            assertRect(Rect(x, top, x + side, bottom), controls.getValue(RunningControlTarget.SKILL_2), "$w x $h X")
            assertRect(Rect(c, top, c + side, bottom), controls.getValue(RunningControlTarget.SKILL_3), "$w x $h C")
        }
        // REGULAR: the Dash row extended to the left (52 u squares, never under 48 dp).
        row(1_440f, 810f, 968f, 1_028f, 1_088f, 595f, 647f, 52f)
        row(1_280f, 720f, 808f, 868f, 928f, 505f, 557f, 52f)
        row(1_000f, 700f, 588.2f, 643.0f, 697.8f, 513.9f, 561.9f, 48f)
        row(481f, 800f, 109.5f, 163.2f, 217.0f, 634.6f, 682.6f, 48f)
        assertRect(Rect(1_152f, 595f, 1_276f, 647f), at(1_440f, 810f).getValue(RunningControlTarget.BRAKE), "1440 Brake")
        // Landscape: Z and X left of Dash, C below it at the margin.
        listOf(
            Triple(844f to 390f, floatArrayOf(586f, 640f, 222f), Rect(752f, 312f, 800f, 360f)),
            Triple(740f to 360f, floatArrayOf(500.2f, 554.2f, 192f), Rect(653.4f, 282f, 701.4f, 330f)),
            Triple(640f to 360f, floatArrayOf(417.8f, 471.8f, 192f), Rect(558.6f, 282f, 606.6f, 330f)),
            Triple(568f to 320f, floatArrayOf(347f, 401f, 152f), Rect(487f, 242f, 535f, 290f)),
        ).forEach { (size, zx, c) ->
            val controls = at(size.first, size.second)
            assertRect(Rect(zx[0], zx[2], zx[0] + 48f, zx[2] + 48f), controls.getValue(RunningControlTarget.SKILL_1), "$size Z")
            assertRect(Rect(zx[1], zx[2], zx[1] + 48f, zx[2] + 48f), controls.getValue(RunningControlTarget.SKILL_2), "$size X")
            assertRect(c, controls.getValue(RunningControlTarget.SKILL_3), "$size C")
        }
        // Portrait: Brake, Z, X, C, Dash on one baseline; Build in the top row left of pause.
        listOf(
            Triple(390f to 844f, floatArrayOf(96f, 282f, 111f, 760f), Rect(276f, 54f, 324f, 102f)),
            Triple(375f to 667f, floatArrayOf(92.6f, 270.9f, 103.8f, 583f), Rect(261f, 54f, 309f, 102f)),
            Triple(360f to 640f, floatArrayOf(89.3f, 259.7f, 96.5f, 556f), Rect(246f, 54f, 294f, 102f)),
            Triple(320f to 568f, floatArrayOf(71.8f, 239.8f, 77.8f, 484f), Rect(206f, 54f, 254f, 102f)),
        ).forEach { (size, values, build) ->
            val (w, h) = size
            val controls = at(w, h)
            assertEquals(values[0], controls.getValue(RunningControlTarget.BRAKE).right, 0.5f, "$size Brake right")
            assertEquals(values[1], controls.getValue(RunningControlTarget.DASH).left, 0.5f, "$size Dash left")
            listOf(RunningControlTarget.SKILL_1, RunningControlTarget.SKILL_2, RunningControlTarget.SKILL_3).forEachIndexed { index, skill ->
                val left = values[2] + index * 54f
                assertRect(Rect(left, values[3], left + 48f, values[3] + 48f), controls.getValue(skill), "$size $skill")
            }
            assertRect(build, runningBuildButtonBounds(w, h, 1f), "$size Build")
        }
        // The Apex: the badge with its Data bar (REGULAR), the badge row on phones.
        assertRect(Rect(26f, 18f, 160f, 84f), at(1_440f, 810f).getValue(RunningControlTarget.APEX), "1440 Apex")
        assertRect(Rect(38f, 0f, 128f, 48f), at(844f, 390f).getValue(RunningControlTarget.APEX), "844 Apex")
        assertRect(Rect(10f, 54f, 100f, 102f), at(390f, 844f).getValue(RunningControlTarget.APEX), "390 Apex")
    }

    private fun assertRect(expected: Rect, actual: Rect, where: String) {
        assertTrue(
            abs(expected.left - actual.left) <= 0.5f && abs(expected.top - actual.top) <= 0.5f &&
                abs(expected.right - actual.right) <= 0.5f && abs(expected.bottom - actual.bottom) <= 0.5f,
            "$where: expected $expected, was $actual",
        )
    }

    private companion object {
        /** REGULAR windows, landscape phones and portrait phones the design was measured at (dp). */
        val ReferenceWindows = listOf(
            1_440f to 810f, 1_280f to 720f, 1_000f to 700f, 481f to 800f,
            844f to 390f, 740f to 360f, 640f to 360f, 568f to 320f,
            390f to 844f, 375f to 667f, 360f to 640f, 320f to 568f,
        )

        /** The Core's zone around the screen centre the camera holds it in (the feed's clearance). */
        const val CORE_ZONE_DP = 46f
    }
}
