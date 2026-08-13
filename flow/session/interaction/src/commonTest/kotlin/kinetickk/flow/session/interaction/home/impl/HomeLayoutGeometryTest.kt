// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeLayoutGeometryTest {
    @Test
    fun fourTargetDeviceClassesKeepEveryActionVisibleAndTouchableInBothOrientations() {
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
