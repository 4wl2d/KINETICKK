// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.reset.impl

import kinetickk.flow.session.interaction.reset.api.ResetModalMode
import kinetickk.flow.session.interaction.reset.api.ResetModalOutput
import kinetickk.flow.session.interaction.reset.api.ResetModalRenderModel

internal data class ResetModalViewport(
    val width: Float,
    val height: Float,
    val density: Float,
)

internal fun resolveResetModalPress(
    model: ResetModalRenderModel,
    viewport: ResetModalViewport,
    x: Float,
    y: Float,
): ResetModalOutput? {
    if (model.mode == ResetModalMode.RESET_IN_PROGRESS) return null
    val d = viewport.density
    val modalWidth = minOf(720f * d, viewport.width - 30f * d)
    val modalHeight = minOf(420f * d, viewport.height - 30f * d)
    val left = (viewport.width - modalWidth) * 0.5f
    val top = (viewport.height - modalHeight) * 0.5f
    val buttonTop = top + modalHeight - 82f * d
    val buttonBottom = top + modalHeight - 30f * d
    if (y !in buttonTop..buttonBottom) return null

    val gap = 16f * d
    val buttonWidth = (modalWidth - 80f * d - gap) * 0.5f
    val cancelLeft = left + 40f * d
    val primaryLeft = cancelLeft + buttonWidth + gap
    return when {
        x in cancelLeft..cancelLeft + buttonWidth -> ResetModalOutput.Cancel
        model.mode == ResetModalMode.BOOTSTRAP_UNAVAILABLE -> null
        x in primaryLeft..primaryLeft + buttonWidth -> when (model.mode) {
            ResetModalMode.CONFIRMATION_REQUIRED -> ResetModalOutput.ConfirmDelete
            ResetModalMode.PURGE_NEEDS_ATTENTION -> ResetModalOutput.RetryPurge
            ResetModalMode.RESET_IN_PROGRESS -> null
            ResetModalMode.BOOTSTRAP_UNAVAILABLE -> null
        }
        else -> null
    }
}
