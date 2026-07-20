// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.presentation.canvas

import kinetickk.core.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.feature.gameplay.domain.renderModel.GameplayRenderModel
import kotlin.math.ln
import kotlin.math.max

internal fun speedVisualRatio(speed: Float): Float {
    val safeSpeed = max(0f, speed)
    return (ln(1f + safeSpeed / 300f) / ln(1f + 5_000f / 300f)).coerceIn(0f, 1f)
}

internal fun DrawScope.world(
    engine: GameplayRenderModel,
    x: Float,
    y: Float,
    shakeX: Float,
    shakeY: Float,
): Offset = Offset(
    size.width * 0.5f + x - engine.cameraX + shakeX,
    size.height * 0.5f + y - engine.cameraY + shakeY,
)
