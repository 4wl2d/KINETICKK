// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

/*
 * The per-step structure hook. The window itself lives in StructureFieldSystem.kt, placement and obstacle suppression
 * in StructurePlacement.kt, the capsule geometry in StructureQueries.kt and the wall collisions in
 * StructureCollisions.kt.
 */

/** The step after the Architect arrives, the structure window collapses (its key follows the boss flag). */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.updateStructures(delta: Float) {
    if (bossSpawned && !structureField.bossCollapsed) refreshStructureField(obstacleField.centerChunkX, obstacleField.centerChunkY)
}
