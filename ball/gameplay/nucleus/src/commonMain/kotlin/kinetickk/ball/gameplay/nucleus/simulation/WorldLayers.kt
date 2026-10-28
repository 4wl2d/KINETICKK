// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.LayerFeatures
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.Enemy

/** A circle in absolute coordinates where a layer's generation suppresses content (after yielding). */
internal data class WorldKeepOut(val layer: WorldLayer, val x: Double, val y: Double, val radius: Float)

/** True when the active keep-out of the current layer covers a body of [reach] at the absolute point. */
internal fun MutableGameState.keepOutSuppresses(absoluteX: Double, absoluteY: Double, reach: Float): Boolean {
    val keepOut = worldKeepOut ?: return false
    if (keepOut.layer != worldLayer) return false
    val dx = absoluteX - keepOut.x
    val dy = absoluteY - keepOut.y
    val limit = (keepOut.radius + reach).toDouble()
    return dx * dx + dy * dy < limit * limit
}

/** What realspace-only systems may run in the current layer (other lanes read these, never the layer itself). */
internal fun MutableGameState.layerFeatures(): LayerFeatures = content.dimensions.definition(worldLayer).features

internal fun MutableGameState.inBaseLayer(): Boolean = worldLayer == WorldLayer.BASE

/** Called by a layer switch after the layer field changed; one line per lane. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.onWorldLayerChanged(from: WorldLayer, to: WorldLayer) {
    clearSitesForLayerChange()
    onStructureLayerChanged()
    endWorldEventForLayerChange()
}

/** The current layer's change to the Core's drag. */
internal fun MutableGameState.layerDrag(drag: Float): Float = drag

/** The current layer's scale of the director's spawn rate. */
internal fun MutableGameState.layerSpawnRateMultiplier(): Float = 1f

/** The current layer's scale of the ordinary enemy cap. */
internal fun MutableGameState.layerOrdinaryCapMultiplier(): Float = 1f

/** World time scale for [enemy] (null: hostile projectiles) in the current layer. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.layerTimeScale(enemy: Enemy?): Float = 1f

/** The current layer's scale of Matter earned. */
internal fun MutableGameState.layerMatterMultiplier(): Float = 1f
