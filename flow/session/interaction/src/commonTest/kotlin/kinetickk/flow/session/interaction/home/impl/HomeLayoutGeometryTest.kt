// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeLayoutGeometryTest {
    @Test
    fun targetViewportsKeepEveryActionVisibleAndTouchableInBothOrientations() {
        TargetDeviceProfiles.forEach { device ->
            listOf(
                device.widthPx to device.heightPx,
                device.heightPx to device.widthPx,
            ).forEach { (width, height) ->
                val layout = homeLayoutGeometry(width, height, device.density)

                assertEquals(HomeLayoutTarget.entries.toSet(), layout.actions.map { it.target }.toSet())
                assertTrue(layout.mode != HomeLayoutMode.REGULAR, "${device.name} must use a compact layout")
                layout.actions.forEach { action ->
                    val bounds = action.bounds
                    assertTrue(bounds.left >= 0f, "${device.name} ${action.target} starts outside the viewport")
                    assertTrue(bounds.top >= 0f, "${device.name} ${action.target} starts outside the viewport")
                    assertTrue(bounds.right <= width, "${device.name} ${action.target} ends outside the viewport")
                    assertTrue(bounds.bottom <= height, "${device.name} ${action.target} ends outside the viewport")
                    assertTrue(bounds.width / device.density >= 48f, "${device.name} ${action.target} is too narrow")
                    assertTrue(bounds.height / device.density >= 48f, "${device.name} ${action.target} is too short")
                }
                layout.actions.forEachIndexed { index, first ->
                    layout.actions.drop(index + 1).forEach { second ->
                        assertFalse(
                            first.bounds.left < second.bounds.right &&
                                first.bounds.right > second.bounds.left &&
                                first.bounds.top < second.bounds.bottom &&
                                first.bounds.bottom > second.bounds.top,
                            "${device.name} overlaps ${first.target} and ${second.target}",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun hitTestingUsesTheSameAdaptiveBoundsAsRendering() {
        TargetDeviceProfiles.forEach { device ->
            val viewport = HomeViewport(device.heightPx, device.widthPx, device.density)
            val layout = homeLayoutGeometry(viewport.width, viewport.height, viewport.density)

            layout.actions.forEach { action ->
                val resolved = resolveHomePress(
                    viewport = viewport,
                    x = action.bounds.center.x,
                    y = action.bounds.center.y,
                )
                assertEquals(action.target.toHomeAction(), resolved)
            }
        }
    }
}

class HomeInfoGeometryTest {
    @Test
    fun infoButtonsAreTouchableInsideTheViewportAndNeverCoverAnAction() {
        val viewports = TargetDeviceProfiles.flatMap { device ->
            listOf(Triple(device.widthPx, device.heightPx, device.density), Triple(device.heightPx, device.widthPx, device.density))
        } + RegularViewports
        viewports.forEach { (width, height, density) ->
            val layout = homeLayoutGeometry(width, height, density)
            val viewport = HomeViewport(width, height, density)
            val expected = if (layout.mode == HomeLayoutMode.REGULAR) {
                setOf(HomeInfoTarget.FORM, HomeInfoTarget.FACTS)
            } else {
                setOf(HomeInfoTarget.FORM)
            }
            assertEquals(expected, layout.infos.map { it.target }.toSet(), "${width}x$height")
            layout.infos.forEach { info ->
                val touch = info.touch
                assertTrue(touch.width / density >= 44f && touch.height / density >= 44f, "${info.target} touch area")
                assertTrue(touch.left >= 0f && touch.top >= 0f && touch.right <= width && touch.bottom <= height,
                    "${info.target} leaves ${width}x$height")
                assertTrue(info.bounds.width / density in 23.9f..24.1f, "The drawn (!) is 24 dp")
                layout.actions.forEach { action ->
                    assertFalse(
                        touch.left < action.bounds.right && touch.right > action.bounds.left &&
                            touch.top < action.bounds.bottom && touch.bottom > action.bounds.top,
                        "${info.target} covers ${action.target} at ${width}x$height",
                    )
                }
                assertEquals(info.target, resolveHomeInfoPress(viewport, info.bounds.center.x, info.bounds.center.y))
                assertEquals(null, resolveHomePress(viewport, info.bounds.center.x, info.bounds.center.y))
            }
        }
    }

    @Test
    fun regularLayoutFollowsTheReferenceFrameInTheGameOrder() {
        val layout = homeLayoutGeometry(1_440f, 810f, 1f)
        assertEquals(HomeLayoutMode.REGULAR, layout.mode)
        val menu = HomeMenuTargets.map(layout::bounds)
        menu.forEachIndexed { index, bounds ->
            // Home board: items at x = 880 - 12 i, 74 px apart, the first centered at y = 161.
            assertEquals(880f - index * 12f, bounds.left, 0.01f)
            assertEquals(161f + index * 74f, bounds.center.y, 0.01f)
        }
        val tiles = HomeCoreTargets.map(layout::bounds)
        assertEquals(tiles.sortedBy { it.left }, tiles, "Form tiles keep the game's CoreShape order")
        tiles.zipWithNext().forEach { (left, right) -> assertTrue(left.right < right.left) }
        assertTrue(tiles.first().top > menu.last().bottom, "The form row sits under the menu")
    }

    @Test
    fun regularActionsStayTouchSizedDownToTheSmallestRegularViewport() {
        RegularViewports.forEach { (width, height, density) ->
            val layout = homeLayoutGeometry(width, height, density)
            assertEquals(HomeLayoutMode.REGULAR, layout.mode)
            layout.actions.forEach { action ->
                assertTrue(action.bounds.width / density >= 44f && action.bounds.height / density >= 44f,
                    "${action.target} at ${width}x$height")
                assertTrue(action.bounds.right <= width && action.bounds.bottom <= height)
            }
        }
    }
}

class HomeMenuClearanceTest {
    @Test
    fun theFormInfoStaysClearOfEverySelectedMenuSlabAndTrail() {
        val viewports = listOf(844f to 390f, 800f to 360f, 600f to 390f, 873f to 393f, 1_440f to 810f, 1_000f to 700f, 390f to 844f, 390f to 600f)
        viewports.forEach { (width, height) ->
            val layout = homeLayoutGeometry(width, height, 1f)
            val info = requireNotNull(layout.info(HomeInfoTarget.FORM)).touch
            HomeMenuTargets.forEach { target ->
                val extent = homeSelectedMenuExtent(layout.bounds(target), layout.scene.menuFontSize)
                assertFalse(
                    info.left < extent.right && info.right > extent.left && info.top < extent.bottom && info.bottom > extent.top,
                    "Selected $target crowds the form (!) at ${width}x$height",
                )
            }
        }
    }

    @Test
    fun theFormNameKeepsARowOfItsOwnBesideItsInfo() {
        listOf(1_440f to 810f, 1_000f to 700f, 844f to 390f, 600f to 390f, 390f to 844f, 390f to 600f).forEach { (width, height) ->
            val layout = homeLayoutGeometry(width, height, 1f)
            val nameRight = homeFormNameRight(layout, 1f)
            assertTrue(nameRight - layout.scene.formNameLeft >= 160f, "form name gets ${nameRight - layout.scene.formNameLeft} px at ${width}x$height")
            val info = requireNotNull(layout.info(HomeInfoTarget.FORM)).bounds
            assertTrue(info.right <= layout.scene.formNameLeft || info.left >= nameRight, "the (!) sits outside the name at ${width}x$height")
        }
    }

    @Test
    fun selectedMenuItemsInLandscapeStayClearOfTheFormTiles() {
        val viewports = listOf(Triple(844f, 390f, 1f), Triple(800f, 360f, 1f), Triple(600f, 390f, 1f), Triple(873f, 393f, 1f),
            Triple(914f, 411f, 1f), Triple(2_532f, 1_170f, 3f)) +
            TargetDeviceProfiles.map { Triple(it.heightPx, it.widthPx, it.density) }
        viewports.forEach { (width, height, density) ->
            val layout = homeLayoutGeometry(width, height, density)
            assertEquals(HomeLayoutMode.COMPACT_LANDSCAPE, layout.mode, "${width}x$height")
            HomeMenuTargets.forEach { target ->
                val extent = homeSelectedMenuExtent(layout.bounds(target), layout.scene.menuFontSize)
                HomeCoreTargets.forEach { tile ->
                    val bounds = layout.bounds(tile)
                    if (extent.top < bounds.bottom && extent.bottom > bounds.top) {
                        // Slab, echo and all three speed lines end at least 8 px right of the tile.
                        assertTrue(extent.left - bounds.right >= 8f * density - 0.01f,
                            "Selected $target reaches ${extent.left} over $tile ending ${bounds.right} at ${width}x$height")
                    }
                }
            }
            HomeCoreTargets.forEach { tile -> assertTrue(layout.bounds(tile).width / density >= 48f, "$tile at ${width}x$height") }
        }
    }

    @Test
    fun selectedMenuItemsInLandscapeEndAboveTheLegalNotices() {
        val viewports = listOf(Triple(844f, 390f, 1f), Triple(800f, 360f, 1f), Triple(600f, 390f, 1f), Triple(873f, 393f, 1f),
            Triple(914f, 411f, 1f), Triple(1_000f, 360f, 1f), Triple(2_532f, 1_170f, 3f)) +
            TargetDeviceProfiles.map { Triple(it.heightPx, it.widthPx, it.density) }
        viewports.forEach { (width, height, density) ->
            val layout = homeLayoutGeometry(width, height, density)
            val scene = layout.scene
            assertEquals(HomeLayoutMode.COMPACT_LANDSCAPE, layout.mode, "${width}x$height")
            // The notices may start anywhere right of legalLeft; their tallest glyphs rise 0.85 em.
            val legalTop = scene.legalBaseline - HOME_LEGAL_ASCENT_EM * scene.legalSize
            HomeMenuTargets.forEach { target ->
                // Slab with echo, and speed lines, each dropped by the turn at their left end.
                homeSelectedMenuFootprint(layout.bounds(target), scene.menuFontSize).forEach { part ->
                    if (part.right > scene.legalLeft && part.left < scene.legalRight) {
                        assertTrue(part.bottom <= legalTop - 2f * density + 0.01f,
                            "Selected $target reaches ${part.bottom} over the legal line (glyphs from $legalTop) at ${width}x$height")
                    }
                }
            }
            // Rows stay touch sized; the design's 36 px menu gives way only on 360-px-tall screens.
            HomeMenuTargets.forEach { target -> assertTrue(layout.bounds(target).height / density >= 48f - 0.01f) }
            assertTrue(scene.menuFontSize / density >= if (height / density >= 390f) 32f else 30f,
                "menu font ${scene.menuFontSize / density} at ${width}x$height")
        }
    }

    @Test
    fun portraitSpeedLinesOfEverySelectedItemEndInsideTheSideMargin() {
        val viewports = listOf(Triple(390f, 844f, 1f), Triple(360f, 800f, 1f), Triple(412f, 915f, 1f), Triple(1_170f, 2_532f, 3f)) +
            TargetDeviceProfiles.map { Triple(it.widthPx, it.heightPx, it.density) }
        viewports.forEach { (width, height, density) ->
            val layout = homeLayoutGeometry(width, height, density)
            if (layout.mode != HomeLayoutMode.COMPACT_PORTRAIT || layout.scene.menuColumns != 1) return@forEach
            HomeMenuTargets.forEach { target ->
                val extent = homeSelectedMenuExtent(layout.bounds(target), layout.scene.menuFontSize)
                assertTrue(extent.left >= 16f * density - 0.01f, "Selected $target trail starts at ${extent.left} at ${width}x$height")
                assertTrue(layout.bounds(target).right <= width - 16f * density + 0.01f)
            }
            // The menu still steps left going down.
            HomeMenuTargets.map(layout::bounds).zipWithNext().forEach { (upper, lower) -> assertTrue(lower.left < upper.left) }
        }
    }

    @Test
    fun theFormNameClearsEverySelectedMenuSlabAndSpeedLine() {
        listOf(1_440f to 810f, 1_000f to 700f, 844f to 390f, 800f to 360f, 600f to 390f, 873f to 393f, 390f to 844f, 390f to 600f).forEach { (width, height) ->
            val layout = homeLayoutGeometry(width, height, 1f)
            val scene = layout.scene
            val name = androidx.compose.ui.geometry.Rect(scene.formNameLeft, scene.formNameCenterY - scene.formNameSize * 0.65f,
                homeFormNameRight(layout, 1f), scene.formNameCenterY + scene.formNameSize * 0.65f)
            HomeMenuTargets.forEach { target ->
                homeSelectedMenuFootprint(layout.bounds(target), scene.menuFontSize).forEach { part ->
                    assertFalse(name.overlaps(part), "Selected $target covers the form name $name with $part at ${width}x$height")
                }
            }
            HomeCoreTargets.forEach { tile -> assertFalse(name.overlaps(layout.bounds(tile)), "the name runs into $tile at ${width}x$height") }
        }
    }

    @Test
    fun theSpeedLinesComeToRestWithAllThreeFullyDrawn() {
        // kk-trail: a 0.9 s cycle per line, delayed 0 / 0.15 / 0.3 s, opaque from 18 % to 72 %.
        val rest = homeTrailTime(10f)
        assertEquals(HOME_TRAIL_REST_SECONDS, rest)
        listOf(0f, 0.15f, 0.3f).forEach { delay ->
            val phase = kinetickk.foundation.design.kkLoop(rest, 0.9f, delay)
            assertTrue(phase in 0.18f..0.72f, "line delayed $delay is at $phase")
        }
        assertEquals(0f, homeTrailTime(0f))
        assertEquals(0.2f, homeTrailTime(0.2f))
    }
}

private val RegularViewports = listOf(
    Triple(900f, 560f, 1f),
    Triple(1_000f, 700f, 1f),
    Triple(1_280f, 720f, 1f),
    Triple(1_440f, 810f, 1f),
    Triple(2_560f, 1_080f, 1f),
    Triple(2_880f, 1_620f, 2f),
)

private data class TargetDeviceProfile(
    val name: String,
    val widthPx: Float,
    val heightPx: Float,
    val density: Float,
)

private val TargetDeviceProfiles = listOf(
    TargetDeviceProfile("Compact browser", widthPx = 390f, heightPx = 600f, density = 1f),
    TargetDeviceProfile("CPH2411", widthPx = 1_080f, heightPx = 2_412f, density = 3f),
    TargetDeviceProfile("RMX2002", widthPx = 1_080f, heightPx = 2_400f, density = 3f),
    TargetDeviceProfile("SM-A325F", widthPx = 1_080f, heightPx = 2_400f, density = 2.625f),
    TargetDeviceProfile("Redmi Note 9 Pro", widthPx = 1_080f, heightPx = 2_400f, density = 2.75f),
)
