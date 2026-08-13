// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.lab.impl

import kinetickk.ball.profile.interaction.lab.api.LabRenderModel
import kotlin.math.floor
import kotlin.math.min

internal fun resolveLabPress(
    model: LabRenderModel,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    x: Float,
    y: Float,
): LabAction? {
    val scale = density.coerceAtLeast(1f)
    fun d(value: Float): Float = value * scale

    val width = min(d(900f), screenWidth - d(30f))
    val height = min(d(650f), screenHeight - d(30f))
    val left = (screenWidth - width) * 0.5f
    val top = (screenHeight - height) * 0.5f
    val right = left + width
    val bottom = top + height
    if (y > bottom - d(55f)) return LabAction.Back
    val contentTop = top + d(88f)
    val contentWidth = right - left - d(50f)
    val columnWidth = contentWidth * 0.5f
    val rowHeight = d(105f)
    if (
        x !in left + d(25f)..right - d(25f) ||
        y !in contentTop..contentTop + rowHeight * ((model.upgrades.size + 1) / 2)
    ) {
        return null
    }
    val column = if (x < left + d(25f) + columnWidth) 0 else 1
    val row = floor((y - contentTop) / rowHeight).toInt()
    val upgrade = model.upgrades.getOrNull(row * 2 + column) ?: return null
    return LabAction.PurchaseRequested(upgrade.id)
}
