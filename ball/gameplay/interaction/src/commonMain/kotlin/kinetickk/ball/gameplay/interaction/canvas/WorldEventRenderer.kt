// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkRolePalette

// Ambient world events. None is planned yet, so every entry point draws nothing.

/** The event's ground look (storm motes, flare sweep), after the dimension marks. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawWorldEventAmbient(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) = Unit

/** Telegraphed meteor strikes, after the Volatile Remains bursts. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawWorldEventStrikes(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) = Unit

/** The screen edge an event tints while it runs. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawWorldEventEdge(engine: GameplayRenderModel, roles: KkRolePalette, renderTime: Float) = Unit
