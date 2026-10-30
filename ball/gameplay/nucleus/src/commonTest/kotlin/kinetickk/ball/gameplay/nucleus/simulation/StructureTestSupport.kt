// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.StructurePolicy
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.model.PlacedStructure
import kinetickk.ball.gameplay.nucleus.model.StructureField
import kinetickk.ball.gameplay.nucleus.model.Wall
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs

/** Canonical content with structures turned on and every shipped placement number. */
internal val structuresEnabledContent: GameplayContentSnapshot =
    canonicalGameplayContent.copy(structures = canonicalGameplayContent.structures.copy(enabled = true))

/** Canonical content in a world without structures; walls may then be placed by hand ([placeWallsForTesting]). */
internal val structuresDisabledContent: GameplayContentSnapshot =
    canonicalGameplayContent.copy(structures = canonicalGameplayContent.structures.copy(enabled = false))

/**
 * Canonical content in which every cell away from the origin holds one of [templates] by weight: structures on, no
 * distance gate, full chance. Each template's own minimum distance is cleared so the policy accepts any of them.
 */
internal fun denseStructureContent(
    templates: List<StructureTemplate> = canonicalGameplayContent.structures.templates,
    configure: (StructurePolicy) -> StructurePolicy = { it },
): GameplayContentSnapshot {
    val shipped = canonicalGameplayContent.structures
    val policy = shipped.copy(
        enabled = true,
        minimumDistance = 0f,
        fullChanceDistance = 1f,
        maximumChance = 1f,
        templatesPerRun = StructureTemplateId.entries.size,
        secretTemplateChance = 1f,
        templates = templates.map { if (it.minimumDistance == 0f) it else it.copy(minimumDistance = 0f) }.toImmutableList(),
    )
    return canonicalGameplayContent.copy(structures = configure(policy))
}

/** [denseStructureContent] where only [template] carries weight, so every cell away from the origin holds it. */
internal fun forcedStructureContent(template: StructureTemplateId): GameplayContentSnapshot =
    denseStructureContent(canonicalGameplayContent.structures.templates.map { if (it.id == template) it else it.copy(weight = 0f) })

/** The structure the run's hash places in the absolute cell; fails when the cell is empty. */
internal fun MutableGameState.structureAtForTesting(cellX: Int, cellY: Int): StructurePlacement =
    requireNotNull(structurePlacement(cellX, cellY)) { "No structure in cell $cellX, $cellY" }

/**
 * Moves the run's origin to the absolute point with the Core resting at its run-local (0, 0), drops both windows and
 * regenerates them there, as if the Core had flown in.
 */
internal fun MutableGameState.teleportCoreForTesting(absoluteX: Double, absoluteY: Double) {
    worldOriginX = absoluteX
    worldOriginY = absoluteY
    coreX = 0f
    coreY = 0f
    previousCoreX = 0f
    previousCoreY = 0f
    obstacleField = ObstacleField.EMPTY
    structureField = StructureField.EMPTY
    refreshObstacleWindow()
}

/**
 * A started run of [seed] with a Vault in the absolute cell (12, −9) and the Core resting where an offer laid over the
 * vault's +y side is ejected to (as a window places it), flying into the vault at 600 u/s: a course laid from there
 * along the flight runs into the vault, whose final room opens only once both galleries are cleared.
 */
internal fun vaultSideRunForTesting(seed: Int): MutableGameState {
    val run = buildTestState(content = forcedStructureContent(StructureTemplateId.VAULT), seed = seed)
    val placement = run.structureAtForTesting(12, -9)
    run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
    val vault = run.structureField.structures[run.structureField.indexOf(placement.id)]
    val (startX, startY) = run.clearPointOfInterestSite(vault.x, vault.y + vault.halfHeight - 200f)
    run.coreX = startX
    run.coreY = startY
    run.previousCoreX = startX
    run.previousCoreY = startY
    val distance = length(vault.x - startX, vault.y - startY)
    run.setVelocityForTesting((vault.x - startX) / distance * 600f, (vault.y - startY) / distance * 600f)
    return run
}

/** True when ([x], [y]) lies outside every structure footprint of the window inflated by [inflate]. */
internal fun MutableGameState.outsideFootprintsForTesting(x: Float, y: Float, inflate: Float): Boolean =
    structureField.structures.all { s -> abs(x - s.x) >= s.halfWidth + inflate || abs(y - s.y) >= s.halfHeight + inflate }

/** A wall for [placeWallsForTesting] (run-local); its id comes from the structure it is placed in. */
internal fun testWall(
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    halfThickness: Float = 24f,
    kind: WallKind = WallKind.SOLID,
): Wall = Wall(id = 0L, kind = kind, ax = ax, ay = ay, bx = bx, by = by, halfThickness = halfThickness, door = if (kind == WallKind.SOLID) -1 else 0)

/**
 * Replaces the structure window with one structure per list of walls, each framed by the bounds of its walls' centre
 * lines, with ids from realspace cells (40 + i, 40) and wall ids `structureId or index`. Only for content with
 * structures disabled, whose refresh never replaces the window; returns the placed structures.
 */
internal fun MutableGameState.placeWallsForTesting(vararg structures: List<Wall>): List<PlacedStructure> {
    require(!content.structures.enabled) { "Test walls replace the window only while structures are disabled" }
    val placed = ArrayList<PlacedStructure>()
    val walls = ArrayList<Wall>()
    structures.forEachIndexed { structureIndex, list ->
        val id = structureId(WorldLayer.BASE, 40 + structureIndex, 40)
        val minX = list.minOf { minOf(it.ax, it.bx) }
        val maxX = list.maxOf { maxOf(it.ax, it.bx) }
        val minY = list.minOf { minOf(it.ay, it.by) }
        val maxY = list.maxOf { maxOf(it.ay, it.by) }
        val start = walls.size
        list.forEachIndexed { index, wall -> walls += wall.copy(id = id or index.toLong()) }
        placed += PlacedStructure(
            id = id,
            template = StructureTemplateId.DERELICT_STATION,
            rotation = 0,
            x = (minX + maxX) / 2f,
            y = (minY + maxY) / 2f,
            halfWidth = (maxX - minX) / 2f,
            halfHeight = (maxY - minY) / 2f,
            wallStart = start,
            wallEnd = walls.size,
            spent = false,
        )
    }
    structureField = StructureField.of(obstacleField.centerChunkX, obstacleField.centerChunkY, worldLayer.ordinal, false, placed, walls, emptyList())
    return placed
}

/** Sweeps the Core from ([fromX], [fromY]) to ([toX], [toY]) at velocity ([vx], [vy]) through the obstacle pass. */
internal fun MutableGameState.sweepCoreForTesting(fromX: Float, fromY: Float, toX: Float, toY: Float, vx: Float, vy: Float) {
    previousCoreX = fromX
    previousCoreY = fromY
    coreX = toX
    coreY = toY
    setVelocityForTesting(vx, vy)
    updateObstacles()
}

/** Distance from ([x], [y]) to the surface of [wall] (negative inside it). */
internal fun wallGapForTesting(x: Float, y: Float, wall: Wall): Float {
    val along = segmentNearest(x, y, wall.ax, wall.ay, wall.bx, wall.by)
    val dx = x - (wall.ax + (wall.bx - wall.ax) * along)
    val dy = y - (wall.ay + (wall.by - wall.ay) * along)
    return kotlin.math.sqrt(dx * dx + dy * dy) - wall.halfThickness
}

/**
 * Kills every live enemy the dungeon's sealed room owns, as if the Core had destroyed them (kills, rewards and clauses
 * run); the next dungeon step clears the room.
 */
internal fun MutableGameState.killGarrisonForTesting() {
    val dungeon = requireNotNull(dungeon) { "No dungeon" }
    require(dungeon.engagedRoom >= 0) { "No sealed room" }
    val tag = roomEncounterTag(dungeon.structureId, dungeon.engagedRoom)
    enemies.filter { it.ownerTag == tag && it.alive }.forEach { enemy ->
        onEnemyKilled(enemy)
        enemies.remove(enemy)
    }
}
