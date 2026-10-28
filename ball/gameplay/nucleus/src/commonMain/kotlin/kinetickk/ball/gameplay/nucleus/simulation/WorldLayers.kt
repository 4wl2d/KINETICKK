// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.LayerFeatures
import kinetickk.ball.content.api.ObstacleKind
import kinetickk.ball.content.api.ObstacleMix
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kotlin.math.max

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

/**
 * True when the active keep-out of the current layer covers an obstacle candidate of the chunk. Generation asks after
 * the candidate yielded to its neighbours and passed the destroyed-id check, so suppressing it moves nothing else.
 * A candidate reaches its own circle, or the widest field reach of the obstacle kinds while it is (or the layer's mix
 * may still turn it into) a field kind, so no pull, plume or arming ring reaches the arrival point.
 */
internal fun MutableGameState.keepOutCovers(
    chunkX: Int,
    chunkY: Int,
    offsetX: Float,
    offsetY: Float,
    kind: ObstacleKind,
    radius: Float,
): Boolean {
    val keepOut = worldKeepOut ?: return false
    if (keepOut.layer != worldLayer) return false
    val chunk = content.obstacles.chunkSize
    val absoluteX = chunkX.toDouble() * chunk + offsetX
    val absoluteY = chunkY.toDouble() * chunk + offsetY
    return keepOutSuppresses(absoluteX, absoluteY, keepOutReach(kind, radius))
}

/** How far from its centre a candidate of [kind] and [radius] may act once placed in the current layer. */
private fun MutableGameState.keepOutReach(kind: ObstacleKind, radius: Float): Float {
    // Only asteroid candidates turn into the newer kinds (bumpers and unstables never do).
    val mayCarryField = kind.field || kind == ObstacleKind.ASTEROID && currentObstacleMix().fieldShare() > 0f
    return if (mayCarryField) max(radius, content.obstacles.kinds.maximumInfluence) else radius
}

private fun ObstacleMix.fieldShare(): Float {
    val kinds = ObstacleKind.entries
    var total = 0f
    for (index in kinds.indices) if (kinds[index].field) total += share(kinds[index])
    return total
}

/**
 * Regenerates the whole obstacle window for the current layer and keep-out. A layer switch calls it after changing
 * [MutableGameState.worldLayer] and [MutableGameState.worldKeepOut]: the empty field fails the window's span check, so
 * every chunk is drawn again from the layer's own hashes. Destroyed ids stay remembered (they carry the layer tag).
 */
internal fun MutableGameState.switchWorldWindow() {
    obstacleField = ObstacleField.EMPTY
    refreshObstacleWindow()
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
