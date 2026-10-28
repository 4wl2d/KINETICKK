// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.StructureField
import kinetickk.ball.gameplay.nucleus.model.Wall

/*
 * Structure walls in the shared world passes (the structures lane replaces this file). Inert until the lane lands:
 * the structure field stays empty, so no wall blocks, hits or ejects anything.
 */

/**
 * Earliest wall a circle of [radius] swept from ([x0], [y0]) to ([x1], [y1]) enters strictly before the fraction
 * [before]: the wall index in the high 32 bits and the entry fraction's float bits in the low 32 bits, or −1.
 */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.earliestWallEntry(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float, before: Float): Long = -1L

/**
 * The Core, resting at ([contactX], [contactY]) where it touched wall [index], answers the contact: it moves the Core
 * to its rest point off the wall and runs the solid-impact response.
 */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.respondToWall(index: Int, wall: Wall, contactX: Float, contactY: Float) = Unit

/** Last guard after the Core's bounces: the Core never rests inside a wall. */
internal fun MutableGameState.pushCoreOutOfWalls() = Unit

/** Enemies are pushed out of walls, slide along them and slam into them when thrown. */
internal fun MutableGameState.resolveEnemyWalls() = Unit

/** True when a circle of [radius] at ([x], [y]) would overlap a solid or sealed wall. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.wallBlocksPoint(x: Float, y: Float, radius: Float): Boolean = false

/** True when a circle of [radius] swept from ([x0], [y0]) to ([x1], [y1]) would touch a solid or sealed wall. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.wallBlocksSegment(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float): Boolean = false

/** Rebuilds the structure window around the absolute chunk when it is not current. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.refreshStructureField(chunkX: Int, chunkY: Int) = Unit

/** True when a structure footprint suppresses an obstacle candidate of the chunk (after yielding). */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.structureSuppresses(chunkX: Int, chunkY: Int, offsetX: Float, offsetY: Float, radius: Float): Boolean =
    false

/** A circle of [radius] at ([x], [y]) moved out of any structure footprint it would land in. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.structureEjected(x: Float, y: Float, radius: Float): SteeringVector = steeringVector(x, y)

/** Shots whose step crosses a solid or sealed wall are spent. */
internal fun MutableGameState.blockProjectilesAtWalls() = Unit

/** Index of the first solid or sealed wall a circle of [radius] meets on its way, or −1. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.wallOnPath(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float): Int = -1

/** True when ([x], [y]) keeps at least [clearance] from every solid or sealed wall. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.wallsClearOf(x: Float, y: Float, clearance: Float): Boolean = true

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.updateStructures(delta: Float) = Unit

/** A new run: no structure window, no dungeon, nothing spent. */
internal fun MutableGameState.resetStructures() {
    structureField = StructureField.EMPTY
    dungeon = null
    spentStructureIds = emptyList()
    lastAnnouncedStructureId = 0L
}

/** Positions follow a rebase; ids and absolute cells stay. */
internal fun MutableGameState.rebaseStructures(dx: Float, dy: Float) {
    structureField = structureField.shifted(dx, dy)
    dungeon = dungeon?.shifted(dx, dy)
}
