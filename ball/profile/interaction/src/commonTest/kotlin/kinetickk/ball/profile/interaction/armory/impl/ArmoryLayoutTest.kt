// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.TestWeapons
import kinetickk.ball.profile.interaction.profileFrame
import kinetickk.ball.profile.interaction.profileTextScale
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopSegment
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArmoryLayoutTest {
    private fun layout(width: Float, height: Float, density: Float = 1f, textScale: Float = 1f) =
        armoryLayout(profileFrame(width, height, density), BoardWeapons.size, textScale, backWidth = 80f * density, actionWidth = 260f * density)

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

        // The shipped 24 keep the board's tile geometry and add three rows under the fold: the grid
        // scrolls by exactly those rows instead of shrinking the tiles.
        val shipped = armoryLayout(profileFrame(1440f, 810f, 1f), TestWeapons.size, 1f, backWidth = 80f, actionWidth = 260f)
        assertEquals(24, shipped.tiles.size)
        assertEquals(layout.tiles.first(), shipped.tiles.first())
        assertEquals(layout.tiles[4], shipped.tiles[4])
        assertEquals(layout.gridViewport, shipped.gridViewport)
        assertEquals(layout.gridContentHeight + 3f * shipped.rowPitch, shipped.gridContentHeight, 0.01f)
        assertEquals(shipped.gridContentHeight - shipped.gridViewport.height, shipped.gridScrollMax, 0.01f)
        assertTrue(shipped.gridScrollMax > 0f)
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
        val reversed = BoardWeapons.reversed().toImmutableList()
        val firstCenter = layout.tiles.first().center
        val x = grid.left + firstCenter.x
        val y = grid.top + firstCenter.y

        assertEquals(WeaponId.FLUX_WAKE, assertIs<ArmoryAction.Activate>(
            resolveArmoryPress(layout, BoardWeapons, WeaponId.FLUX_WAKE, 0f, x, y)).id)
        assertEquals(reversed.first().id, assertIs<ArmoryAction.Activate>(
            resolveArmoryPress(layout, reversed, WeaponId.FLUX_WAKE, 0f, x, y)).id)

        // Scrolling the grid by (up to) one row maps the same point to the tile one row below.
        assertTrue(layout.gridScrollMax >= layout.rowPitch)
        val scroll = layout.rowPitch
        assertEquals(BoardWeapons[layout.columns].id, assertIs<ArmoryAction.Activate>(
            resolveArmoryPress(layout, BoardWeapons, WeaponId.FLUX_WAKE, scroll, x, y)).id)

        // The primary action does not scroll: it maps at its screen position to the inspected weapon.
        val action = layout.action.center
        assertEquals(WeaponId.ARC_COIL, assertIs<ArmoryAction.Apply>(
            resolveArmoryPress(layout, BoardWeapons, WeaponId.ARC_COIL, 0f, action.x, action.y)).id)
        assertIs<ArmoryAction.Back>(resolveArmoryPress(layout, BoardWeapons, WeaponId.FLUX_WAKE, 0f,
            layout.back.center.x, layout.back.center.y))
        // The gap between two tiles and the empty panel area below the action do not act.
        val gapX = grid.left + (layout.tiles[0].right + layout.tiles[1].left) * 0.5f
        assertNull(resolveArmoryPress(layout, BoardWeapons, WeaponId.FLUX_WAKE, 0f, gapX, y))
    }

    @Test
    fun primaryActionStaysOnScreenAtEveryTextSize() {
        for ((width, height) in listOf(1440f to 810f, 1000f to 700f, 844f to 390f, 720f to 360f, 390f to 844f)) {
            for ((textScale, segment) in listOf(1f, 1.75f).flatMap { scale -> WorkshopSegment.entries.filter {
                it != WorkshopSegment.TRAITS }.map { scale to it } }) {
                val layout = armoryLayout(profileFrame(width, height, 1f), BoardWeapons.size, textScale, backWidth = 80f,
                    actionWidth = 260f, segment = segment)
                val action = layout.action
                assertTrue(action.top >= layout.frame.headerHeight && action.bottom <= height, "$width x $height @$textScale $action")
                if (layout.actionPinned) {
                    // Pinned under the scrolling details, never beneath them.
                    assertTrue(layout.detailViewport.bottom <= action.top, "$width x $height @$textScale")
                    assertTrue(layout.detailScrollMax > 0f, "$width x $height @$textScale")
                } else {
                    // The details fit, so nothing scrolls and the action follows the ladder.
                    assertEquals(0f, layout.detailScrollMax, "$width x $height @$textScale")
                    assertTrue(maxOf(layout.ladder.bottom, layout.forms.bottom, layout.facts.bottom, layout.upgrade.bottom) <= action.top,
                        "$width x $height @$textScale $segment")
                }
            }
        }
        // Each segment's blocks follow the switch in order and the others take no room; the content ends
        // at the shown body (the Mastery sheet on the board frame still runs past the fold: the action pins).
        fun board(segment: WorkshopSegment) = armoryLayout(profileFrame(1440f, 810f, 1f), BoardWeapons.size, 1f, 80f, 260f, segment = segment)
        val mastery = board(WorkshopSegment.MASTERY)
        assertTrue(mastery.segments.top >= mastery.tags.bottom && mastery.mastery.top >= mastery.segments.bottom &&
            mastery.ladder.top >= mastery.mastery.bottom && mastery.forms.top >= mastery.ladder.bottom, "${mastery.ladder} ${mastery.forms}")
        assertEquals(0f, mastery.synergy.height + mastery.facts.height + mastery.upgrade.height)
        assertEquals(mastery.forms.bottom + 12f, mastery.detailViewport.top + mastery.detailContentHeight, 0.01f)
        val details = board(WorkshopSegment.DETAILS)
        assertTrue(details.synergy.top >= details.segments.bottom && details.facts.top >= details.synergy.bottom)
        assertEquals(0f, details.mastery.height + details.ladder.height + details.forms.height + details.upgrade.height)
        assertEquals(details.facts.bottom + 12f, details.detailViewport.top + details.detailContentHeight, 0.01f)
        val upgrade = board(WorkshopSegment.UPGRADE)
        assertEquals(168f, upgrade.upgrade.height, 0.01f)
        assertEquals(upgrade.upgrade.bottom + 12f, upgrade.detailViewport.top + upgrade.detailContentHeight, 0.01f)
        // The board frame's Upgrade segment fits: the action sits in its natural place under the block.
        assertFalse(upgrade.actionPinned)
        assertEquals(upgrade.upgrade.bottom + 26f, upgrade.action.top, 0.01f)
        assertTrue(armoryLayout(profileFrame(390f, 844f, 1f), BoardWeapons.size, 1.75f, 80f, 260f, segment = WorkshopSegment.MASTERY).actionPinned)
        // A short sheet (measured heights) leaves the action in its natural place under the facts.
        val short = armoryLayout(profileFrame(1440f, 810f, 1f), BoardWeapons.size, 1f, 80f, 260f, segment = WorkshopSegment.DETAILS) { _, _ ->
            ArmoryDetailMetrics(40f, 50f, 22f, formsHeight = 10f, synergyHeight = 10f, factsHeight = 10f)
        }
        assertFalse(short.actionPinned)
        assertEquals(short.facts.bottom + 26f, short.action.top, 0.01f)
    }

    @Test
    fun theTwentyFourWeaponGridScrollsAndKeepsTouchTargets() {
        for ((width, height) in listOf(1440f to 810f, 1000f to 700f, 844f to 390f, 720f to 360f, 390f to 844f, 360f to 640f)) {
            for (setting in listOf(1f, 1.25f, 1.75f)) {
                // Density 2: the sizes are dp, the geometry px.
                val layout = armoryLayout(profileFrame(width * 2f, height * 2f, 2f), TestWeapons.size, profileTextScale(setting),
                    backWidth = 160f, actionWidth = 520f)
                val context = "${width}x$height @$setting"
                assertEquals(TestWeapons.size, layout.tiles.size, context)
                // Every tile is a press target of at least 48 dp in both directions.
                layout.tiles.forEach { tile ->
                    assertTrue(tile.width >= 48f * 2f && tile.height >= 48f * 2f, "$context tile ${tile.width}x${tile.height}")
                }
                // Rows follow each other a pitch apart and the last row ends inside the scrolling content.
                assertEquals(layout.tiles[layout.columns].top - layout.tiles[0].top, layout.rowPitch, 0.01f, context)
                assertTrue(layout.tiles.last().bottom <= layout.gridContentHeight + 0.01f, context)
                // Six rows of four (or eight of three) never fit one screen: the grid scrolls, and the
                // last tile can be scrolled fully into the viewport.
                assertTrue(layout.gridScrollMax > 0f, "$context does not scroll")
                val last = layout.tiles.last()
                val reveal = armoryRevealScroll(layout, TestWeapons.lastIndex, 0f)
                assertTrue(last.top >= reveal && last.bottom <= reveal + layout.gridViewport.height + 0.01f, "$context last tile at $reveal")
            }
        }
    }

    @Test
    fun revealingATileScrollsAsLittleAsPossible() {
        val layout = layout(390f, 844f)
        val viewport = layout.gridViewport.height
        val margin = layout.tiles.first().top
        // A tile already in view leaves the scroll where it is.
        assertEquals(0f, armoryRevealScroll(layout, 0, 0f))
        assertEquals(0f, armoryRevealScroll(layout, 1, 0f))
        // A tile below the fold brings its bottom (and the grid padding) to the viewport's bottom ...
        val below = layout.tiles.lastIndex
        val down = armoryRevealScroll(layout, below, 0f)
        assertEquals((layout.tiles[below].bottom + margin - viewport).coerceIn(0f, layout.gridScrollMax), down, 0.01f)
        // ... and one above it brings its top back to the viewport's top.
        assertEquals((layout.tiles[0].top - margin).coerceAtLeast(0f), armoryRevealScroll(layout, 0, down), 0.01f)
        // Unknown indices only clamp the current scroll.
        assertEquals(layout.gridScrollMax, armoryRevealScroll(layout, 99, 10_000f))
    }

    @Test
    fun lockedWeaponsKeepRoomForTheShortfall() {
        val frame = profileFrame(844f, 390f, 1f)
        val wide = armoryLayout(frame, BoardWeapons.size, 1f, 80f, actionWidth = 10_000f, needRoom = true)
        assertTrue(wide.need.width >= (wide.detailViewport.width - 24f) * 0.3f)
        val owned = armoryLayout(frame, BoardWeapons.size, 1f, 80f, actionWidth = 10_000f, needRoom = false)
        assertEquals(owned.detailViewport.right - 12f, owned.action.right, 0.01f)
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

    @Test
    fun tileIconsKeepOneSizeBetweenTheTallestTextsAndTheTilesGrowRatherThanLoseThem() {
        val frame = profileFrame(844f, 390f, 1f)
        val insets = armoryTileInsets(frame)
        val icon = frame.d(armoryType(frame.mode).tileIcon)
        fun layout(status: Float, nameBlock: Float) = armoryLayout(frame, BoardWeapons.size, 1f, 80f, 260f,
            tileText = { ArmoryTileMetrics(status, nameBlock) })
        val plain = layout(status = 0f, nameBlock = 0f)
        // Room to spare: the board's icon, centred between the texts.
        val roomy = layout(status = 14f, nameBlock = 18f)
        assertEquals(plain.tiles, roomy.tiles)
        assertEquals(icon, roomy.tileIcon, 0.01f)
        val bandTop = insets.top + 14f + frame.d(ARMORY_TILE_ICON_GAP)
        val bandBottom = roomy.tiles.first().height - insets.bottom - 18f - frame.d(ARMORY_TILE_ICON_GAP)
        assertEquals((bandTop + bandBottom) * 0.5f, roomy.tileIconY, 0.01f)
        // Two-line names (phone, default text): every tile's icon shrinks to what the tallest name
        // leaves, the grid keeps its fitted rows.
        val wrapped = layout(status = 14f, nameBlock = 37f)
        assertEquals(plain.tiles, wrapped.tiles)
        assertTrue(wrapped.tileIcon < icon && wrapped.tileIcon >= icon * ARMORY_TILE_ICON_MIN, "${wrapped.tileIcon}")
        // Large text: the icon keeps its smallest size and the tiles grow (the grid scrolls).
        val large = layout(status = 18f, nameBlock = 52f)
        assertEquals(icon * ARMORY_TILE_ICON_MIN, large.tileIcon, 0.01f)
        val tile = large.tiles.first()
        assertTrue(tile.height > plain.tiles.first().height, "${tile.height}")
        assertEquals(insets.top + 18f + frame.d(ARMORY_TILE_ICON_GAP) * 2f + large.tileIcon + 52f + insets.bottom, tile.height, 0.01f)
        assertTrue(large.gridScrollMax > 0f)
        assertEquals(large.tiles[large.columns].top - tile.top, large.rowPitch, 0.01f)
    }

    @Test
    fun sideBySideMilestonesNeedTheirBonusGroupsClearlyApart() {
        val frame = profileFrame(844f, 390f, 1f)
        val levels = listOf(1, 3, 6, 10)
        // A 320 px ladder: Lvl 3 starts at 64.8, Lvl 6 at 162, and the last group ends at the right edge.
        val gap = 7f
        assertTrue(armoryMasteryFitsSideBySide(frame, levels, listOf(28f, 58f, 60f, 60f), gap, 320f))
        // The Lvl 6 group running within 3 gaps of the right-aligned last group does not fit.
        assertFalse(armoryMasteryFitsSideBySide(frame, levels, listOf(28f, 58f, 90f, 60f), gap, 320f))
        // Nor does a group wider than its slot before the next milestone.
        assertFalse(armoryMasteryFitsSideBySide(frame, levels, listOf(28f, 120f, 60f, 60f), gap, 320f))
        assertTrue(armoryMasteryFitsSideBySide(frame, emptyList(), emptyList(), gap, 320f))
    }

    @Test
    fun smallestTextSizeKeepsTheBoardTilesAndDock() {
        // At 100 % text the labels shrink; the tiles (the press targets), the grid and the
        // portrait detail dock keep their size at the board's text size (125 %).
        for ((width, height) in listOf(1440f to 810f, 844f to 390f, 390f to 844f)) {
            val small = layout(width, height, textScale = profileTextScale(1f))
            val default = layout(width, height, textScale = profileTextScale(1.25f))
            val context = "$width x $height"
            assertEquals(default.tiles, small.tiles, context)
            assertEquals(default.gridViewport, small.gridViewport, context)
            val floor = small.frame.d(if (small.frame.regular) 172f else 84f)
            small.tiles.forEach { assertTrue(it.height >= floor - 0.01f, "$context tile ${it.height} < $floor") }
        }
    }
}

/** The reference board lays out the original twelve weapons on one unscrolled four-column grid. */
private val BoardWeapons = TestWeapons.take(12).toImmutableList()
