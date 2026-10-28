// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.render.TrialProjection

/*
 * Dimension visits as an engagement and their hooks into the shared queues: the kill hook here, the reward queue in
 * DimensionRewards.kt. The optional-site marks and the trial panel stay inert until the presentation lands.
 */

/** True while the Core is inside a dimension visit (one engagement at a time). */
internal fun MutableGameState.dimensionVisitActive(): Boolean = dimensionRun.visit != null

/** True once the run entered any dimension (an opted-in run). */
internal fun MutableGameState.dimensionTouched(): Boolean = dimensionRun.visitedMask != 0

/** Appends optional-site marks of non-window portals; returns the new mark count. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.appendDimensionMarks(out: FloatArray, count: Int): Int = count

/** The trial panel of an active dimension objective, or null. */
internal fun MutableGameState.dimensionTrialProjection(): TrialProjection? = null

/**
 * Kill hook of onEnemyKilled during a visit whose objective is still open: the objective boss's fall completes it and
 * ends its encounter (its adds stay as ordinary enemies; a secret boss keeps its defeat notice); a burning enemy (a
 * Nadir native or one carrying Thermal) leaves an ember during a Harvest. Draws nothing.
 */
internal fun MutableGameState.onDimensionEnemyKilled(enemy: Enemy) {
    val visit = dimensionRun.visit ?: return
    if (visit.completed) return
    if (visit.bossId >= 0 && enemy.id == visit.bossId) {
        completeObjective(announce = !enemy.isSecretBoss)
        endEncounter(dimensionEncounterTag(visit), despawn = false)
        return
    }
    if (visit.objective == DimensionObjective.HARVEST && burning(enemy)) dropEmber(enemy.x, enemy.y)
}

/** True for a Nadir native or an enemy carrying Thermal stacks. */
private fun MutableGameState.burning(enemy: Enemy): Boolean =
    content.enemy(enemy.type).habitats and EnemyHabitat.NADIR.bit != 0 || stacks(enemy, StatusElement.THERMAL) > 0

/** True while the open choice is a dimension reward (it cannot be rerolled). */
internal fun MutableGameState.dimensionRewardOpen(): Boolean = dimensionRewardLayer != null
