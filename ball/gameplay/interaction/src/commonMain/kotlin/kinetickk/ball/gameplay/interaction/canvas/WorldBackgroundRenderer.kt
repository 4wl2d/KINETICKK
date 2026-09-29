// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel

/** World grid pitch in world units (`bg-grid`; the world spec asks for 40 px). */
internal const val WORLD_GRID_SPACING = 40f

/** Diameter of the halftone disc that sits under the Core (`HUD.dc.html`: 700 px). */
private const val CORE_HALFTONE_DIAMETER = 700f

private val CoreHalftoneRect = Rect(
    -CORE_HALFTONE_DIAMETER * 0.5f,
    -CORE_HALFTONE_DIAMETER * 0.5f,
    CORE_HALFTONE_DIAMETER * 0.5f,
    CORE_HALFTONE_DIAMETER * 0.5f,
)

/**
 * Ink ground with a world-anchored 40 px grid and a faint halftone disc around the Core. The
 * run's rebirth tier tints the grid and halftone only. [roles] is the Color vision palette.
 */
internal fun DrawScope.drawBackdrop(
    engine: GameplayRenderModel,
    shakeX: Float,
    shakeY: Float,
    @Suppress("UNUSED_PARAMETER") renderTime: Float,
    @Suppress("UNUSED_PARAMETER") roles: KkRolePalette,
) {
    val theme = arenaTheme(engine.rebirthLevel)
    val origin = Offset(
        worldGridOffset(engine.cameraX, size.width, shakeX),
        worldGridOffset(engine.cameraY, size.height, shakeY),
    )
    var x = origin.x - WORLD_GRID_SPACING
    while (x <= size.width) {
        if (x >= 0f) drawLine(theme.grid, Offset(x, 0f), Offset(x, size.height), 1f)
        x += WORLD_GRID_SPACING
    }
    var y = origin.y - WORLD_GRID_SPACING
    while (y <= size.height) {
        if (y >= 0f) drawLine(theme.grid, Offset(0f, y), Offset(size.width, y), 1f)
        y += WORLD_GRID_SPACING
    }
    val core = world(engine, engine.coreX, engine.coreY, shakeX, shakeY)
    // Dots are anchored to the Core, like the board's halftone element, and fade out radially.
    translate(core.x, core.y) {
        drawKkRadialFade(CoreHalftoneRect) {
            drawKkHalftone(CoreHalftoneRect, theme.halftone)
        }
    }
}

/** Screen offset (0 until the spacing) of the first grid line for a camera coordinate. */
internal fun worldGridOffset(camera: Float, viewport: Float, shake: Float): Float =
    positiveModulo(viewport * 0.5f - camera + shake, WORLD_GRID_SPACING)
