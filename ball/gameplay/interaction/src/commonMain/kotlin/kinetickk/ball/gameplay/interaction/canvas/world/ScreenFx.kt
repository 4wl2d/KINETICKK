// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.interaction.canvas.*

/**
 * World-impact screen effects only (HUD state effects are HudRenderer's): a threat glow on every
 * edge while damage is fresh (the render model has no source direction). Ram impacts read through
 * their slash, ring and screen shake; they never flash the whole screen.
 */
internal fun DrawScope.drawScreenFx(
    engine: GameplayRenderModel,
    @Suppress("UNUSED_PARAMETER") renderTime: Float,
    roles: KkRolePalette,
) {
    val flash = engine.damageFlash
    if (flash > 0f) {
        val depth = d(60f)
        val glow = edgeGlow(roles.threat.copy(alpha = 0.9f), size.width, size.height, depth)
        val alpha = flash * 0.4f
        drawRect(glow.left, Offset.Zero, Size(depth, size.height), alpha)
        drawRect(glow.right, Offset(size.width - depth, 0f), Size(depth, size.height), alpha)
        drawRect(glow.top, Offset.Zero, Size(size.width, depth), alpha)
        drawRect(glow.bottom, Offset(0f, size.height - depth), Size(size.width, depth), alpha)
    }
}
