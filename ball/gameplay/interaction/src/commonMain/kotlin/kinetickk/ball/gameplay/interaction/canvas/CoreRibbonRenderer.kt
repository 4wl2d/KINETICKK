// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkRolePalette

/** The Core's path ribbon, right after the nucleus trail. The ribbon samples do not exist yet, so it draws nothing. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawCoreRibbon(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) = Unit
