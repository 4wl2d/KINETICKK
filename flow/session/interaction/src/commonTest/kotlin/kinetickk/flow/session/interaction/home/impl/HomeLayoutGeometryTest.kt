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
