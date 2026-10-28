// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.DimensionRewardKind
import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.DimensionVisit
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.render.TrialClock
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.ball.gameplay.nucleus.render.TrialReward
import kinetickk.ball.gameplay.nucleus.render.TrialSource
import kinetickk.ball.gameplay.nucleus.render.TrialValue
import kotlin.math.min

/*
 * Dimension visits as an engagement and their hooks into the shared queues: the opt-in marks, the kill hook and the
 * trial panel's facts here, the reward queue in DimensionRewards.kt.
 */

/** True while the Core is inside a dimension visit (one engagement at a time). */
internal fun MutableGameState.dimensionVisitActive(): Boolean = dimensionRun.visit != null

/** True once the run entered any dimension (an opted-in run). */
internal fun MutableGameState.dimensionTouched(): Boolean = dimensionRun.visitedMask != 0

/**
 * Appends the optional-site mark of the open glitch rift (its centre and the radius that enters it) after [count]
 * marks and returns the new count. Window rifts are sites and already marked; an exit rift exists only inside a visit,
 * which a pilot that must not opt in never reaches. Writes nothing once [MAX_OPTIONAL_SITE_MARKS] are written.
 */
internal fun MutableGameState.appendDimensionMarks(out: FloatArray, count: Int): Int {
    val portal = dimensionRun.glitchPortal ?: return count
    if (count >= MAX_OPTIONAL_SITE_MARKS) return count
    val base = count * 3
    out[base] = portal.x
    out[base + 1] = portal.y
    out[base + 2] = content.dimensions.portalRadius
    return count + 1
}

/** The trial panel's meter shows at most this many cells (the course and the Harvest stay well inside it). */
private const val MAX_DIMENSION_TRIAL_SEGMENTS = 24

/**
 * The trial panel of the visit in progress, or null outside one: the objective's name and its instruction behind the
 * (!), no clock (the Rebirth strip's plate carries the visit timer), one cell per course ring or ember counted and the
 * count as its value, or a boss objective's single cell filling with the integrity its boss has lost (no value), and
 * the layer's reward. A completed objective stays full until the visit ends. A dimension is no site: its family is a
 * placeholder the panel never reads (it names, draws and explains a dimension by its source).
 */
internal fun MutableGameState.dimensionTrialProjection(): TrialProjection? {
    val visit = dimensionRun.visit ?: return null
    val objective = visit.objective
    val boss = objective.boss != null
    val goal = visit.goal.coerceAtLeast(1)
    return TrialProjection(
        family = SiteFamily.PORTAL,
        kind = null,
        title = objective.label,
        rules = objective.instruction,
        clock = TrialClock.NONE,
        seconds = 0f,
        segments = if (boss) 1 else min(goal, MAX_DIMENSION_TRIAL_SEGMENTS),
        progress = if (boss) objectiveBossIntegrityLost(visit) else min(1f, visit.progress.toFloat() / goal),
        value = if (boss) TrialValue.NONE else TrialValue.COUNT,
        done = if (boss) 0f else visit.progress.toFloat(),
        required = if (boss) 0f else goal.toFloat(),
        reward = when (content.dimensions.definition(visit.layer).reward.kind) {
            DimensionRewardKind.BLESSING_OFFER -> TrialReward.BLESSING
            DimensionRewardKind.PACT_OFFER -> TrialReward.PACT
            DimensionRewardKind.ARCHIVE_TROVE -> TrialReward.TROVE
            DimensionRewardKind.NONE -> TrialReward.UNKNOWN
        },
        source = TrialSource.DIMENSION,
        dimension = visit.layer,
    )
}

/** The share of its integrity the objective boss has lost: 0 before it arrives, 1 once the objective is complete. */
private fun MutableGameState.objectiveBossIntegrityLost(visit: DimensionVisit): Float {
    if (visit.completed) return 1f
    if (visit.bossId < 0) return 0f
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.id != visit.bossId) continue
        return if (enemy.maxHp > 0f) (1f - enemy.hp / enemy.maxHp).coerceIn(0f, 1f) else 0f
    }
    return 0f
}

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
