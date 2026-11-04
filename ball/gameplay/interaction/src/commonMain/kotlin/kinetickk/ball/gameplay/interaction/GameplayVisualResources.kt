// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.interaction.canvas.look.prepareSiteGlyphs
import kinetickk.ball.gameplay.interaction.canvas.prepareWorldVisualResources
import kinetickk.ball.gameplay.interaction.canvas.world.places.prepareDimensionVisualResources
import kinetickk.ball.gameplay.interaction.overlay.prepareTerminalVisualResources
import kinetickk.foundation.design.prepareInterfaceIcons
import kotlinx.coroutines.yield

/**
 * Builds the retained, size-independent visual resources before the menu opens. Call on the UI
 * dispatcher: these are the same draw-thread-confined caches used by the live renderer. Each group
 * is bounded and yields to let the loading screen render and cancellation take effect.
 *
 * Viewport-dependent layouts and procedural world instances keep their existing bounded caches;
 * guessing their keys here would only allocate entries that the first real frame immediately evicts.
 */
suspend fun prepareGameplayVisualResources() {
    yield()
    prepareInterfaceIcons()
    yield()
    prepareSiteGlyphs()
    yield()
    prepareWorldVisualResources()
    yield()
    prepareDimensionVisualResources()
    yield()
    prepareTerminalVisualResources()
}
