// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.StructurePolicy
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WorldLayer
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/*
 * Structure placement. A stateless hash of the run seed, the world layer and an absolute cell of
 * StructurePolicy.cellSize decides whether the cell holds a structure, which template, its quarter rotation and its
 * offset inside the cell margins. No chance below StructurePolicy.minimumDistance from the absolute origin, measured
 * on the nearest possible footprint point (|centre| − circumradius), ramping to StructurePolicy.maximumChance at
 * StructurePolicy.fullChanceDistance. Nothing draws from a run stream, so a placement never shifts offers, spawns or
 * obstacle layouts; obstacle candidates inside a footprint are dropped after yielding, so no other obstacle moves.
 */

/** Salt of the placement hash (registered in the structures lane notes). */
internal const val STRUCTURE_SALT: Long = 0x57C0_C7E5_5EED_0002L

/** A structure the hash places in one cell: absolute centre and half extents after the quarter [rotation]. */
internal data class StructurePlacement(
    val id: Long,
    val template: StructureTemplateId,
    val rotation: Int,
    val absoluteX: Double,
    val absoluteY: Double,
    val halfWidth: Float,
    val halfHeight: Float,
) {
    /** Squared distance from the absolute point to the footprint inflated by [inflate] (0 inside it). */
    fun gapSquared(absoluteX: Double, absoluteY: Double, inflate: Float): Double {
        val dx = max(0.0, abs(absoluteX - this.absoluteX) - (halfWidth + inflate))
        val dy = max(0.0, abs(absoluteY - this.absoluteY) - (halfHeight + inflate))
        return dx * dx + dy * dy
    }
}

/** Id of the structure in an absolute cell of [layer]: bit 63, the layer in bits 60-62, 26 bits per cell axis, low byte 0. */
internal fun structureId(layer: WorldLayer, cellX: Int, cellY: Int): Long =
    Long.MIN_VALUE or ((layer.ordinal.toLong() and 0x7L) shl 60) or ((cellX.toLong() and STRUCTURE_CELL_MASK) shl 34) or
        ((cellY.toLong() and STRUCTURE_CELL_MASK) shl 8)

/**
 * The solid-impact surface of a structure, from its id or the id of any of its walls: the low byte set, so every wall
 * of one structure shares one hit cooldown. Disjoint from every wall id (index ≤ 47) and from [NO_SURFACE_ID], even for
 * the realspace structure at cell (0, 0) whose id is [Long.MIN_VALUE].
 */
internal fun structureSurfaceId(id: Long): Long = id or STRUCTURE_SURFACE_BITS

private const val STRUCTURE_SURFACE_BITS: Long = 0xFFL

/** The absolute cell column of a structure id (sign-extended from its 26 bits). */
internal fun structureCellX(id: Long): Int = ((id shl 4) shr 38).toInt()

/** The absolute cell row of a structure id (sign-extended from its 26 bits). */
internal fun structureCellY(id: Long): Int = ((id shl 30) shr 38).toInt()

private const val STRUCTURE_CELL_MASK: Long = 0x3FF_FFFFL

/**
 * The structure [policy] places in the absolute cell ([cellX], [cellY]) of [layer] for [runSeed], or null. Pure: the
 * five hash draws (roll, template pick, rotation, offset x, offset y) are taken in this order whatever the outcome.
 */
internal fun placeStructure(policy: StructurePolicy, runSeed: Int, layer: WorldLayer, cellX: Int, cellY: Int): StructurePlacement? {
    if (!policy.enabled) return null
    val layerScale = policy.layerChance[layer.ordinal]
    if (layerScale <= 0f) return null
    val random = HashRandom(worldHash(runSeed, layer, cellX, cellY, STRUCTURE_SALT))
    val roll = random.nextFloat()
    val pick = random.nextFloat()
    val rotation = random.nextInt() ushr 30
    val offsetX = random.nextFloat()
    val offsetY = random.nextFloat()
    val size = policy.cellSize.toDouble()
    val cellDistance = hypot((cellX + 0.5) * size, (cellY + 0.5) * size)
    val template = weightedTemplate(policy, cellDistance, pick) ?: return null
    val turned = rotation and 1 != 0
    val halfWidth = if (turned) template.halfHeight else template.halfWidth
    val halfHeight = if (turned) template.halfWidth else template.halfHeight
    val margin = policy.cellMargin.toDouble()
    val x = cellX * size + margin + halfWidth + offsetX * (size - 2.0 * margin - 2.0 * halfWidth)
    val y = cellY * size + margin + halfHeight + offsetY * (size - 2.0 * margin - 2.0 * halfHeight)
    val nearest = hypot(x, y) - hypot(halfWidth.toDouble(), halfHeight.toDouble())
    val ramp = ((nearest - policy.minimumDistance) / (policy.fullChanceDistance - policy.minimumDistance)).coerceIn(0.0, 1.0)
    if (roll >= (policy.maximumChance * ramp * layerScale).toFloat()) return null
    return StructurePlacement(structureId(layer, cellX, cellY), template.id, rotation, x, y, halfWidth, halfHeight)
}

/**
 * A weighted pick over the templates eligible at [cellDistance] (weight > 0, minimum distance reached): walks them
 * in order subtracting weights from `pick × total` and takes the first that drops below zero (the last eligible one
 * on rounding); null when none is eligible.
 */
private fun weightedTemplate(policy: StructurePolicy, cellDistance: Double, pick: Float): StructureTemplate? {
    val templates = policy.templates
    var total = 0.0
    for (index in templates.indices) {
        val template = templates[index]
        if (template.eligibleAt(cellDistance)) total += template.weight
    }
    if (total <= 0.0) return null
    var remaining = pick * total
    var last: StructureTemplate? = null
    for (index in templates.indices) {
        val template = templates[index]
        if (!template.eligibleAt(cellDistance)) continue
        last = template
        remaining -= template.weight
        if (remaining < 0.0) return template
    }
    return last
}

private fun StructureTemplate.eligibleAt(cellDistance: Double): Boolean = weight > 0f && minimumDistance <= cellDistance

/** The structure the run's hash places in the absolute cell of [layer] (the current layer by default), or null. */
internal fun MutableGameState.structurePlacement(cellX: Int, cellY: Int, layer: WorldLayer = worldLayer): StructurePlacement? =
    placeStructure(content.structures, runSeed, layer, cellX, cellY)

/** Absolute structure cell index of an absolute coordinate. */
internal fun MutableGameState.structureCell(absolute: Double): Int = floor(absolute / content.structures.cellSize).toInt()

/**
 * True when the obstacle candidate of the absolute chunk ([chunkX], [chunkY]) at ([offsetX], [offsetY]) with [radius]
 * meets a structure footprint inflated by the policy's obstacle clearance. Stateless: spent and collapsed structures
 * keep suppressing, so their footprints stay clearings. Checks the at most four cells the inflated circle covers.
 */
internal fun MutableGameState.structureSuppresses(chunkX: Int, chunkY: Int, offsetX: Float, offsetY: Float, radius: Float): Boolean {
    val policy = content.structures
    if (!policy.enabled) return false
    val chunk = content.obstacles.chunkSize.toDouble()
    val x = chunkX * chunk + offsetX
    val y = chunkY * chunk + offsetY
    val clearance = policy.obstacleClearance
    val reach = (radius + clearance).toDouble()
    val limit = radius.toDouble() * radius
    for (cellY in structureCell(y - reach)..structureCell(y + reach)) {
        for (cellX in structureCell(x - reach)..structureCell(x + reach)) {
            val placement = structurePlacement(cellX, cellY) ?: continue
            if (placement.gapSquared(x, y, clearance) < limit) return true
        }
    }
    return false
}

/**
 * True when ([absoluteX], [absoluteY]) lies in a structure footprint of the current layer inflated by [margin]
 * (non-negative), whatever the structure's state. Checks the cells the inflated point covers: its own cell, and a
 * neighbour only when the margin reaches across that border. Sites use it to keep their hashed cells off structures.
 */
internal fun MutableGameState.structureFootprintContains(absoluteX: Double, absoluteY: Double, margin: Float): Boolean {
    if (!content.structures.enabled) return false
    val reach = margin.toDouble()
    for (cellY in structureCell(absoluteY - reach)..structureCell(absoluteY + reach)) {
        for (cellX in structureCell(absoluteX - reach)..structureCell(absoluteX + reach)) {
            val placement = structurePlacement(cellX, cellY) ?: continue
            if (abs(absoluteX - placement.absoluteX) <= placement.halfWidth + reach &&
                abs(absoluteY - placement.absoluteY) <= placement.halfHeight + reach
            ) {
                return true
            }
        }
    }
    return false
}
