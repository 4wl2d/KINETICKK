// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.render.DimensionProjection
import kinetickk.ball.gameplay.nucleus.render.EmberProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.HaloProjection
import kinetickk.ball.gameplay.nucleus.render.PortalProjection
import kinetickk.ball.gameplay.nucleus.render.TrialClock
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.ball.gameplay.nucleus.render.TrialReward
import kinetickk.ball.gameplay.nucleus.render.TrialSource
import kinetickk.ball.gameplay.nucleus.render.TrialValue
import kinetickk.foundation.collections.toImmutableList

/** The objective each layer's fixtures show: the Ascent's course, the Harvest's embers, the Archive's boss. */
internal fun fixtureObjective(layer: WorldLayer): DimensionObjective = when (layer) {
    WorldLayer.ZENITH -> DimensionObjective.ASCENT
    WorldLayer.NADIR -> DimensionObjective.HARVEST
    WorldLayer.ARCHIVE, WorldLayer.BASE -> DimensionObjective.ANTECEDENT
}

/**
 * A visit of [layer] around the Core at ([coreX], [coreY]), as Gameplay publishes it: course rings at [course] offsets
 * (their places in order, the first the ring to fly through next unless [completed]) with [splitRemaining] of a
 * [splitDuration]-second split to it, ambient rings at [ambient], embers at [embers] with [emberLife] seconds left (the
 * first ones thrown [emberAge] seconds ago from the kills at [thrownFrom]), and the exit rift at [exit] once the
 * objective is [completed].
 */
internal fun dimensionVisit(
    layer: WorldLayer,
    coreX: Float,
    coreY: Float,
    course: List<Pair<Float, Float>> = emptyList(),
    ambient: List<Pair<Float, Float>> = emptyList(),
    embers: List<Pair<Float, Float>> = emptyList(),
    emberLife: Float = 5f,
    exit: Pair<Float, Float>? = null,
    completed: Boolean = exit != null,
    progress: Int = 0,
    pace: Float = 900f,
    localClock: Float = 3f,
    scorching: Float = 0f,
    splitRemaining: Float = 0f,
    splitDuration: Float = 0f,
    thrownFrom: List<Pair<Float, Float>> = emptyList(),
    emberAge: Float = Float.POSITIVE_INFINITY,
): DimensionProjection {
    val courseRings = course.mapIndexed { index, (dx, dy) -> HaloProjection(index, coreX + dx, coreY + dy, progress + index) }
    val ambientRings = ambient.mapIndexed { index, (dx, dy) -> HaloProjection(100 + index, coreX + dx, coreY + dy, -1) }
    return DimensionProjection(
        layer = layer,
        objective = fixtureObjective(layer),
        remaining = 52f,
        duration = 75f,
        progress = progress,
        goal = if (layer == WorldLayer.ARCHIVE) 1 else 10,
        completed = completed,
        pace = pace,
        scorching = scorching,
        localClock = localClock,
        nextHaloId = if (completed || courseRings.isEmpty()) -1 else courseRings.first().id,
        exitPortal = exit?.let { (dx, dy) -> PortalProjection(1, coreX + dx, coreY + dy, WorldLayer.BASE, locked = false, glitch = false, remaining = 40f) },
        halos = (courseRings + ambientRings).toImmutableList(),
        embers = embers.mapIndexed { index, (dx, dy) ->
            val (fromX, fromY) = thrownFrom.getOrNull(index) ?: (dx to dy)
            val age = if (index < thrownFrom.size) emberAge else Float.POSITIVE_INFINITY
            EmberProjection(coreX + dx, coreY + dy, emberLife - index * 0.05f, coreX + fromX, coreY + fromY, age)
        }.toImmutableList(),
        splitRemaining = splitRemaining,
        splitDuration = splitDuration,
    )
}

/** Realspace with the glitch rift open at ([dx], [dy]) from the Core ([locked] while the Core could not enter it). */
internal fun glitchRift(coreX: Float, coreY: Float, dx: Float, dy: Float, locked: Boolean = false): DimensionProjection =
    DimensionProjection(
        glitchPortal = PortalProjection(2, coreX + dx, coreY + dy, WorldLayer.ARCHIVE, locked = locked, glitch = true, remaining = 30f),
        lockedRifts = if (locked) WorldLayer.ARCHIVE.bit else 0,
    )

/** [this] model with [dimension] published. */
internal fun GameplayRenderModel.withDimension(dimension: DimensionProjection): GameplayRenderModel = with("dimension" to dimension)

/**
 * The trial panel Gameplay publishes during [visit]: the objective's name and instruction, no clock, a counted
 * objective's cells and count, or a boss objective's one cell at [bossShare] of its integrity lost, and the layer's
 * reward.
 */
internal fun dimensionTrial(visit: DimensionProjection, bossShare: Float = 0.4f): TrialProjection {
    val objective = checkNotNull(visit.objective)
    val boss = objective.boss != null
    return TrialProjection(
        family = SiteFamily.PORTAL,
        kind = null,
        title = objective.label,
        rules = objective.instruction,
        clock = TrialClock.NONE,
        seconds = 0f,
        segments = if (boss) 1 else visit.goal,
        progress = if (boss) bossShare else visit.progress.toFloat() / visit.goal,
        value = if (boss) TrialValue.NONE else TrialValue.COUNT,
        done = if (boss) 0f else visit.progress.toFloat(),
        required = if (boss) 0f else visit.goal.toFloat(),
        reward = when (visit.layer) {
            WorldLayer.ZENITH -> TrialReward.BLESSING
            WorldLayer.NADIR -> TrialReward.PACT
            WorldLayer.ARCHIVE, WorldLayer.BASE -> TrialReward.TROVE
        },
        source = TrialSource.DIMENSION,
        dimension = visit.layer,
    )
}

/** [this] model inside [visit] with its trial panel published. */
internal fun GameplayRenderModel.inVisit(visit: DimensionProjection, bossShare: Float = 0.4f): GameplayRenderModel =
    with("dimension" to visit, "trial" to dimensionTrial(visit, bossShare))
