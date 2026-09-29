// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Shared phone profiles and touch-target assertions for every gameplay layout owner. */
internal fun assertTouchTargets(
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

internal fun assertNoOverlap(device: TargetDeviceProfile, targets: List<Pair<String, Rect>>) {
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

internal data class TargetDeviceProfile(
    val name: String,
    val widthPx: Float,
    val heightPx: Float,
    val density: Float,
)

internal val TargetDeviceProfiles = listOf(
    TargetDeviceProfile("CPH2411", widthPx = 1_080f, heightPx = 2_412f, density = 3f),
    TargetDeviceProfile("RMX2002", widthPx = 1_080f, heightPx = 2_400f, density = 3f),
    TargetDeviceProfile("SM-A325F", widthPx = 1_080f, heightPx = 2_400f, density = 2.625f),
    TargetDeviceProfile("Redmi Note 9 Pro", widthPx = 1_080f, heightPx = 2_400f, density = 2.75f),
)
