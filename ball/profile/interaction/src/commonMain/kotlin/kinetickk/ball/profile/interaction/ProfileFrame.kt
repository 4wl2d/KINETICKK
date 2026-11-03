// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min

/** Layout modes shared by the Armory, Lab and Rebirth screens (same thresholds as Home). */
internal enum class ProfileLayoutMode {
    REGULAR,
    COMPACT_LANDSCAPE,
    COMPACT_PORTRAIT,
}

/**
 * Screen frame in px. Board numbers are design px at the mode's reference frame (1440×810,
 * 844×390 or 390×844) and map to px through [d]; [x] maps a board x of the regular 1440 board
 * (the regular composition is centered when the window is wider than the scaled board).
 */
internal class ProfileFrame(
    val width: Float,
    val height: Float,
    val density: Float,
    val mode: ProfileLayoutMode,
    val k: Float,
    val originX: Float,
    val left: Float,
    val right: Float,
    val headerHeight: Float,
) {
    val unit: Float get() = density * k

    fun d(value: Float): Float = value * density * k

    fun x(value: Float): Float = originX + d(value)

    val regular: Boolean get() = mode == ProfileLayoutMode.REGULAR
    val portrait: Boolean get() = mode == ProfileLayoutMode.COMPACT_PORTRAIT
}

internal fun profileLayoutMode(width: Float, height: Float, density: Float): ProfileLayoutMode {
    val scale = density.coerceAtLeast(0.5f)
    val logicalWidth = width / scale
    val logicalHeight = height / scale
    return when {
        logicalWidth >= 900f && logicalHeight >= 560f -> ProfileLayoutMode.REGULAR
        logicalWidth <= logicalHeight -> ProfileLayoutMode.COMPACT_PORTRAIT
        else -> ProfileLayoutMode.COMPACT_LANDSCAPE
    }
}

internal fun profileFrame(width: Float, height: Float, density: Float): ProfileFrame {
    val scale = density.coerceAtLeast(0.5f)
    val logicalWidth = width / scale
    val logicalHeight = height / scale
    return when (profileLayoutMode(width, height, density)) {
        ProfileLayoutMode.REGULAR -> {
            val k = min(logicalWidth / 1440f, logicalHeight / 810f).coerceIn(0.6f, 1.5f)
            val originX = max(0f, (width - 1440f * k * scale) * 0.5f)
            ProfileFrame(width, height, scale, ProfileLayoutMode.REGULAR, k, originX,
                left = originX + 56f * k * scale, right = min(width - 24f * k * scale, originX + 1392f * k * scale),
                headerHeight = 84f * k * scale)
        }
        ProfileLayoutMode.COMPACT_LANDSCAPE -> {
            val k = min(logicalWidth / 844f, logicalHeight / 390f).coerceIn(0.75f, 1.3f)
            ProfileFrame(width, height, scale, ProfileLayoutMode.COMPACT_LANDSCAPE, k, 0f,
                left = 20f * k * scale, right = width - 20f * k * scale, headerHeight = 56f * k * scale)
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> {
            val k = min(logicalWidth / 390f, logicalHeight / 844f).coerceIn(0.75f, 1.3f)
            ProfileFrame(width, height, scale, ProfileLayoutMode.COMPACT_PORTRAIT, k, 0f,
                left = 16f * k * scale, right = width - 16f * k * scale, headerHeight = 108f * k * scale)
        }
    }
}

/** Converts px to dp for Compose sizes. */
internal fun ProfileFrame.dp(px: Float) = (px / density).dp
