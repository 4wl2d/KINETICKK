// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.StructurePolicy
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.model.StructureField
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList

/** Canonical content with structures turned on and every shipped placement number. */
internal val structuresEnabledContent: GameplayContentSnapshot =
    canonicalGameplayContent.copy(structures = canonicalGameplayContent.structures.copy(enabled = true))

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
