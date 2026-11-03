// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.obstacleSeedUnit
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.hud.*
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.design.KkLayerHues
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStrokeWidth
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.positiveModulo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Dimensions in the world. Realspace keeps its own backdrop untouched; a dimension paints its atmosphere
 * (DimensionAtmosphere.kt and the layers' files) and lays stateless motes over its ground (rising light specks in the
 * Zenith, rising heat squares in the Nadir, blinking hollow squares on the Archive's local clock). Here the world layer
 * composes a visit's marks (DimensionMarks.kt: halo rings, embers) and rifts (DimensionRifts.kt) with the layer's
 * weather, and the rifts and the ring to fly through next get edge markers and minimap marks. Every cache is
 * object-local and drawn under a translation, so the world rebase never invalidates one.
 */

/** Side of a mote cell in world units: each cell holds at most one mote, placed by a stateless hash of the cell. */
internal const val DIMENSION_MOTE_CELL = 160f

/** Motes reach this far past their position; cells this far outside the view still draw into it. */
private const val MOTE_MARGIN = 4f

private const val TWO_PI = CanvasMath.TAU

/** Mote cells this dense hold a mote; none at the low particle density. */
internal fun dimensionMoteDensity(density: ParticleDensity): Float = when (density) {
    ParticleDensity.LOW -> 0f
    ParticleDensity.NORMAL -> 0.45f
    ParticleDensity.HIGH -> 0.6f
}

/**
 * Every mote of [layer] whose cell overlaps the world rectangle [left]..[right] × [top]..[bottom] at [time], as world
 * positions with the cell's hash: a cell holds one when its hash falls under [density]; a [crowded] screen keeps every
 * other cell. A mote rises inside its own cell (the Zenith 8–18, the Nadir 12–24 units per second; the Archive's stay),
 * so the pattern belongs to the world and repeats exactly one cell over.
 */
internal inline fun forEachDimensionMote(
    layer: WorldLayer,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    time: Float,
    density: Float,
    crowded: Boolean,
    action: (x: Float, y: Float, hash: Int) -> Unit,
) {
    if (density <= 0f) return
    val riseBase = when (layer) {
        WorldLayer.ZENITH -> 8f
        WorldLayer.NADIR -> 12f
        WorldLayer.ARCHIVE, WorldLayer.BASE -> 0f
    }
    val riseSpread = when (layer) {
        WorldLayer.ZENITH -> 10f
        WorldLayer.NADIR -> 12f
        WorldLayer.ARCHIVE, WorldLayer.BASE -> 0f
    }
    val firstX = floor(left / DIMENSION_MOTE_CELL).toInt()
    val lastX = floor(right / DIMENSION_MOTE_CELL).toInt()
    val firstY = floor(top / DIMENSION_MOTE_CELL).toInt()
    val lastY = floor(bottom / DIMENSION_MOTE_CELL).toInt()
    for (cy in firstY..lastY) for (cx in firstX..lastX) {
        if (crowded && (cx + cy) and 1 != 0) continue
        val hash = dimensionCellHash(cx, cy, layer.ordinal)
        if (obstacleSeedUnit(hash, 0) >= density) continue
        val rise = riseBase + riseSpread * obstacleSeedUnit(hash, 3)
        val x = (cx + obstacleSeedUnit(hash, 1)) * DIMENSION_MOTE_CELL
        val y = cy * DIMENSION_MOTE_CELL + positiveModulo(obstacleSeedUnit(hash, 2) * DIMENSION_MOTE_CELL - rise * time, DIMENSION_MOTE_CELL)
        action(x, y, hash)
    }
}

/** One draw per mote, in the theme: light specks, rising embers, or blinking hollow squares on the local clock. */
internal fun DrawScope.drawDimensionMotes(engine: GameplayRenderModel, layer: WorldLayer, theme: DimensionTheme, shakeX: Float, shakeY: Float) {
    val density = dimensionMoteDensity(engine.settings.particleDensity)
    if (density <= 0f) return
    val crowded = onScreenEnemyCount(engine, shakeX, shakeY, STATUS_PATTERN_ENEMY_LIMIT + 1) > STATUS_PATTERN_ENEMY_LIMIT
    val originX = size.width * 0.5f - engine.renderCameraX + shakeX
    val originY = size.height * 0.5f - engine.renderCameraY + shakeY
    // The Archive's motes keep its local clock, so they freeze when the Core stops.
    val time = if (layer == WorldLayer.ARCHIVE) engine.dimension.localClock else engine.renderElapsed
    forEachDimensionMote(
        layer, -originX - MOTE_MARGIN, -originY - MOTE_MARGIN, size.width - originX + MOTE_MARGIN, size.height - originY + MOTE_MARGIN,
        time, density, crowded,
    ) { x, y, hash ->
        val at = Offset(x + originX, y + originY)
        // At least 24 s per twinkle.
        val twinkle = sin(time * 0.26f + TWO_PI * obstacleSeedUnit(hash, 4))
        val alt = obstacleSeedUnit(hash, 5) < 0.4f
        val color = if (alt) theme.moteAlt else theme.mote
        when (layer) {
            WorldLayer.ZENITH ->
                drawCircle(color.copy(alpha = (color.alpha + 0.15f * twinkle).coerceIn(0f, 1f)), 1.6f + obstacleSeedUnit(hash, 6), at)
            WorldLayer.NADIR -> {
                val side = 2f + obstacleSeedUnit(hash, 6) * 1.2f
                drawRect(color.copy(alpha = (color.alpha + 0.1f * twinkle).coerceIn(0f, 1f)), Offset(at.x - side * 0.5f, at.y - side * 0.5f),
                    Size(side, side))
            }
            WorldLayer.ARCHIVE -> {
                // Each square blinks on its own phase of the local clock.
                if (positiveModulo(time * 0.4f + obstacleSeedUnit(hash, 6), 1f) < 0.55f) {
                    drawRect(color.copy(alpha = (color.alpha + 0.1f * twinkle).coerceIn(0f, 1f)), Offset(at.x - 2f, at.y - 2f), Size(4f, 4f),
                        style = kkStroke(KkStrokeWidth.Line))
                }
            }
            WorldLayer.BASE -> Unit
        }
    }
}

/**
 * The current layer's world marks, after the sites: the glitch rift (realspace shows only it), then in a visit the
 * layer's weather, its halo rings, its embers and the exit rift.
 */
internal fun DrawScope.drawDimensionWorld(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val dimension = engine.dimension
    dimension.glitchPortal?.let { drawDimensionPortal(engine, it, shakeX, shakeY, roles) }
    if (dimension.layer == WorldLayer.BASE) return
    drawDimensionWeather(engine, shakeX, shakeY, roles)
    drawHaloRings(engine, dimension, shakeX, shakeY, roles)
    drawHarvestEmbers(engine, dimension, shakeX, shakeY, roles)
    dimension.exitPortal?.let { drawDimensionPortal(engine, it, shakeX, shakeY, roles) }
}

/** Edge markers of the exit and glitch rifts and the next halo, after the event signal. */
internal fun DrawScope.collectDimensionEdgeMarkers(engine: GameplayRenderModel, batch: EdgeMarkerBatch) {
    val dimension = engine.dimension
    dimension.glitchPortal?.let { collectDimensionEdgeMarker(engine, it.x, it.y, EdgeMarkerIcon.PORTAL, batch) }
    // The exit rift and the course belong to a visit; realspace never shows them.
    if (dimension.layer == WorldLayer.BASE) return
    dimension.exitPortal?.let { collectDimensionEdgeMarker(engine, it.x, it.y, EdgeMarkerIcon.PORTAL, batch) }
    if (dimension.nextHaloId < 0) return
    val halos = dimension.halos
    for (index in halos.indices) {
        val halo = halos[index]
        if (halo.id == dimension.nextHaloId) collectDimensionEdgeMarker(engine, halo.x, halo.y, EdgeMarkerIcon.HALO, batch)
    }
}

/**
 * A rift or ring whose centre lies off screen in the unshaken view gets its marker; like a site's ring it reaches well
 * past its centre, so a ring still partly in view shares the edge with its marker.
 */
private fun DrawScope.collectDimensionEdgeMarker(engine: GameplayRenderModel, x: Float, y: Float, icon: EdgeMarkerIcon, batch: EdgeMarkerBatch) {
    val target = world(engine, x, y, 0f, 0f)
    if (markShown(target, 0f, WorldOverlayScratch.keepOut, engine.worldScale)) return
    val dx = x - engine.coreX
    val dy = y - engine.coreY
    batch.add(target.x * engine.worldScale, target.y * engine.worldScale, sqrt(dx * dx + dy * dy), icon)
}

/** The next halo's edge glyph: a ring with four blunt ticks outside it (no arrow). */
internal fun DrawScope.drawHaloEdgeGlyph(center: Offset, size: Float, color: Color) {
    val radius = size * 0.3f
    val width = max(1f, size * 0.09f)
    drawCircle(color, radius, center, style = kkStroke(width))
    for (index in 0 until 4) {
        val dx = EdgeTickDirections[index * 2]
        val dy = EdgeTickDirections[index * 2 + 1]
        drawLine(color, Offset(center.x + dx * (radius + size * 0.07f), center.y + dy * (radius + size * 0.07f)),
            Offset(center.x + dx * (radius + size * 0.19f), center.y + dy * (radius + size * 0.19f)), width)
    }
}

/** Halos, embers and rifts on the minimap, inside its clip, after the structures. */
internal fun DrawScope.drawDimensionMinimapMarks(
    engine: GameplayRenderModel,
    centerX: Float,
    centerY: Float,
    half: Float,
    scale: Float,
    dot: Float,
    roles: KkRolePalette,
) {
    val dimension = engine.dimension
    val coreX = engine.renderCoreX
    val coreY = engine.renderCoreY
    // A rift is an outline square in its target's accent, pinned inside the rim when beyond the range (as window rifts are).
    val rift = dot * 2.2f
    dimension.glitchPortal?.let {
        drawPinnedMark(centerX, centerY, half, (it.x - coreX) * scale, (it.y - coreY) * scale, rift, riftAccent(it.target, roles), filled = false)
    }
    // Rings, embers and the exit rift belong to a visit; realspace never shows them.
    if (dimension.layer == WorldLayer.BASE) return
    // Halos in their gold, the ring to fly through next larger in the player's colour; embers as bright magma dots.
    val halos = dimension.halos
    for (index in halos.indices) {
        val halo = halos[index]
        val dx = (halo.x - coreX) * scale
        val dy = (halo.y - coreY) * scale
        if (abs(dx) > half || abs(dy) > half) continue
        val next = halo.id == dimension.nextHaloId
        drawCircle(if (next) roles.you else roles.gold.base, dot * (if (next) 2.6f else 1.9f), Offset(centerX + dx, centerY + dy),
            style = kkStroke(if (next) KkStrokeWidth.Icon else KkStrokeWidth.Line))
    }
    val embers = dimension.embers
    for (index in embers.indices) {
        val dx = (embers[index].x - coreX) * scale
        val dy = (embers[index].y - coreY) * scale
        if (abs(dx) > half || abs(dy) > half) continue
        drawCircle(KkLayerHues.MagmaCore, dot * 1.1f, Offset(centerX + dx, centerY + dy))
    }
    dimension.exitPortal?.let {
        drawPinnedMark(centerX, centerY, half, (it.x - coreX) * scale, (it.y - coreY) * scale, rift, riftAccent(it.target, roles), filled = false)
    }
}

/** A stateless 32-bit hash of a mote cell of [layer] (any ordinal). */
internal fun dimensionCellHash(cellX: Int, cellY: Int, layer: Int): Int {
    var mixed = cellX * -0x61c88647 + cellY * 0x27d4eb2d + layer * 0x165667b1
    mixed = (mixed xor (mixed ushr 16)) * -0x7a143595
    mixed = (mixed xor (mixed ushr 13)) * -0x3d4d51cb
    return mixed xor (mixed ushr 16)
}

/** The edge glyph's four ticks, on the diagonals. */
private val EdgeTickDirections = FloatArray(8) { index ->
    val angle = (index / 2) * TWO_PI / 4 + TWO_PI / 8
    if (index % 2 == 0) cos(angle) else sin(angle)
}

/** The world's dimension looks never depend on the current world, viewport or preferences: their caches build once. */
internal fun prepareDimensionVisualResources() {
    MarkStrokes.ring
    MarkStrokes.hatchedRing
    MarkStrokes.catchRing
    AtmosphereStrokes.crackBloom
    AtmosphereStrokes.crackGlow
    AtmosphereStrokes.crackBody
    AtmosphereStrokes.crackCore
    AtmosphereStrokes.shimmer
    prepareZenithResources()
    prepareNadirResources()
    prepareArchiveResources()
    prepareRiftResources()
}
