// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.rebirth.impl

import kotlin.math.min

internal fun resolveRebirthPress(
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    x: Float,
    y: Float,
): RebirthAction? {
    val scale = density.coerceAtLeast(1f)
    fun d(value: Float): Float = value * scale

    val width = min(d(900f), screenWidth - d(30f))
    val height = min(d(650f), screenHeight - d(30f))
    val left = (screenWidth - width) * 0.5f
    val top = (screenHeight - height) * 0.5f
    val right = left + width
    val bottom = top + height
    return when {
        x in left + d(24f)..right - d(24f) &&
            y in bottom - d(118f)..bottom - d(68f) -> RebirthAction.AdvanceRequested
        x in left + d(20f)..right - d(20f) &&
            y in bottom - d(55f)..bottom - d(14f) -> RebirthAction.Back
        else -> null
    }
}
