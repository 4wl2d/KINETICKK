// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.TestWeapons
import kinetickk.ball.profile.interaction.profileFrame
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArmoryLayoutTest {
    private fun layout(width: Float, height: Float, density: Float = 1f, textScale: Float = 1f) =
        armoryLayout(profileFrame(width, height, density), TestWeapons.size, textScale, backWidth = 80f * density, actionWidth = 260f * density)

    @Test
    fun regularGridMatchesTheBoardFourColumnGrid() {
        val layout = layout(1440f, 810f)
        assertEquals(4, layout.columns)
        val first = layout.tiles.first().translate(layout.gridViewport.left, layout.gridViewport.top)
        assertEquals(56f, first.left, 0.01f)
        assertEquals(108f, first.top, 0.01f)
        assertEquals((830f - 36f) / 4f, first.width, 0.01f)
        assertEquals(172f, first.height, 0.01f)
        val fifth = layout.tiles[4].translate(layout.gridViewport.left, layout.gridViewport.top)
        assertEquals(108f + 172f + 12f, fifth.top, 0.01f)
        // Every weapon fits without scrolling on the reference frame; the detail panel starts at 950.
        assertEquals(0f, layout.gridScrollMax)
        assertEquals(950f, layout.plate.left, 0.01f)
        assertTrue(layout.tiles.all { it.right + layout.gridViewport.left < layout.detailViewport.left })
    }

    @Test
    fun everyModeKeepsTilesInsideTheGridAndClearOfTheDetailPanel() {
        for ((size, mode) in listOf(
            (1000f to 700f) to ProfileLayoutMode.REGULAR,
            (844f to 390f) to ProfileLayoutMode.COMPACT_LANDSCAPE,
            (720f to 360f) to ProfileLayoutMode.COMPACT_LANDSCAPE,
            (390f to 844f) to ProfileLayoutMode.COMPACT_PORTRAIT,
        )) {
            val layout = layout(size.first, size.second)
            assertEquals(mode, layout.frame.mode, "$size")
            val grid = layout.gridViewport
            layout.tiles.forEach { tile ->
                assertTrue(tile.left >= 0f && tile.right <= grid.width + 0.01f, "$size $tile")
                val screen = tile.translate(grid.left, grid.top)
                if (mode != ProfileLayoutMode.COMPACT_PORTRAIT) {
                    assertTrue(screen.right <= layout.detailViewport.left, "$size $screen")
                }
            }
            assertTrue(layout.action.left >= layout.detailViewport.left && layout.action.right <= layout.detailViewport.right, "$size")
            assertTrue(layout.back.bottom <= layout.frame.headerHeight, "$size")
        }
        assertEquals(3, layout(390f, 844f).columns)
    }

    @Test
    fun pressMappingUsesInjectedOrderScrollAndTheInspectedWeapon() {
        // The large text size makes the portrait grid scroll.
        val layout = layout(390f, 844f, textScale = 1.75f)
        val grid = layout.gridViewport
        val reversed = TestWeapons.reversed().toImmutableList()
        val firstCenter = layout.tiles.first().center
        val x = grid.left + firstCenter.x
        val y = grid.top + firstCenter.y

        assertEquals(WeaponId.FLUX_WAKE, assertIs<ArmoryAction.Activate>(
            resolveArmoryPress(layout, TestWeapons, WeaponId.FLUX_WAKE, 0f, 0f, x, y)).id)
        assertEquals(reversed.first().id, assertIs<ArmoryAction.Activate>(
            resolveArmoryPress(layout, reversed, WeaponId.FLUX_WAKE, 0f, 0f, x, y)).id)

        // Scrolling the grid by (up to) one row maps the same point to the tile one row below.
        assertTrue(layout.gridScrollMax >= layout.rowPitch)
        val scroll = layout.rowPitch
        assertEquals(TestWeapons[layout.columns].id, assertIs<ArmoryAction.Activate>(
            resolveArmoryPress(layout, TestWeapons, WeaponId.FLUX_WAKE, scroll, 0f, x, y)).id)

        // The detail panel scrolls too (large text): at its end the action maps to the inspected weapon.
        val action = layout.action.center
        val detailScroll = layout.detailScrollMax
        assertTrue(layout.action.bottom - detailScroll <= layout.detailViewport.bottom + 0.01f)
        assertEquals(WeaponId.ARC_COIL, assertIs<ArmoryAction.Apply>(
            resolveArmoryPress(layout, TestWeapons, WeaponId.ARC_COIL, 0f, detailScroll, action.x, action.y - detailScroll)).id)
        assertIs<ArmoryAction.Back>(resolveArmoryPress(layout, TestWeapons, WeaponId.FLUX_WAKE, 0f, 0f,
            layout.back.center.x, layout.back.center.y))
        // The gap between two tiles and the empty panel area below the action do not act.
        val gapX = grid.left + (layout.tiles[0].right + layout.tiles[1].left) * 0.5f
        assertNull(resolveArmoryPress(layout, TestWeapons, WeaponId.FLUX_WAKE, 0f, 0f, gapX, y))
    }

    @Test
    fun gridPageStepsMoveByWholeVisibleRowsAndClamp() {
        assertEquals(200f, armoryGridScrollTarget(0f, 500f, viewportHeight = 250f, rowPitch = 100f, forward = true))
        assertEquals(500f, armoryGridScrollTarget(400f, 500f, 250f, 100f, forward = true))
        assertEquals(0f, armoryGridScrollTarget(100f, 500f, 250f, 100f, forward = false))
        // A viewport shorter than a row still steps one row.
        assertEquals(100f, armoryGridScrollTarget(0f, 500f, 50f, 100f, forward = true))
        assertEquals(0f, armoryGridScrollTarget(0f, 0f, 250f, 100f, forward = true))
        assertEquals(1 to 1, armoryGridPages(0f, 0f, 250f, 100f))
        assertEquals(4 to 1, armoryGridPages(0f, 500f, 250f, 100f))
        assertEquals(4 to 4, armoryGridPages(500f, 500f, 250f, 100f))
    }
}
