// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.max

/**
 * The gameplay surface in device pixels. The simulation viewport is dp; the HUD, the touch
 * controls and the Compose overlays are laid out in device pixels with [hudScale].
 */
internal val GameplayRenderModel.hudWidth: Float get() = screenWidth * viewportDensity
internal val GameplayRenderModel.hudHeight: Float get() = screenHeight * viewportDensity

/**
 * px per dp of the HUD, the running controls and the run's overlays: [GameplayRenderModel.uiScale] at the
 * applied interface scale [interfaceScale] (1 = 100 %). The world keeps the raw viewport density; drawing
 * and hit tests use this one value, so they always agree.
 */
internal fun GameplayRenderModel.hudScale(interfaceScale: Float): Float = hudScale(uiScale, viewportDensity, interfaceScale)

/** [GameplayRenderModel.hudScale] from its parts: the model's [uiScale] at 100 %, else the scaled viewport density. */
internal fun hudScale(uiScale: Float, viewportDensity: Float, interfaceScale: Float): Float =
    if (interfaceScale == 1f) uiScale else max(1f, viewportDensity * interfaceScale)

/** The text-size setting at which the HUD's UI text renders at the boards' reference size. */
internal const val HUD_REFERENCE_TEXT_SCALE = 1.25f

/** Growth of the HUD's UI text relative to the boards: 0.8 at 100 %, 1 at the default, 1.4 at 175 %. */
internal fun hudUiScale(textScale: Float): Float = textScale.coerceAtLeast(0.5f) / HUD_REFERENCE_TEXT_SCALE
