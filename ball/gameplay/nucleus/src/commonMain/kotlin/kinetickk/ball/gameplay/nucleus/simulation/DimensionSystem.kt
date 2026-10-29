// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DimensionDefinition
import kinetickk.ball.content.api.DimensionNotices
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.DimensionPortal
import kinetickk.ball.gameplay.nucleus.model.DimensionRewardGrant
import kinetickk.ball.gameplay.nucleus.model.DimensionRunState
import kinetickk.ball.gameplay.nucleus.model.DimensionVisit
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableListAppending
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Dimension visits: entry through a rift, the visit clock and the way back. Coordinates never jump; a switch changes
 * the layer, clears the arrival point of obstacles, removes every hostile without a kill and draws the pickups in.
 * Nothing here draws from a run stream: objectives and rifts come from stateless hashes.
 */

/** Salts of the dimension hashes (registered in the lane notes). */
internal const val PORTAL_WINDOW_SALT: Long = 0x0D13_E5A1_0000_0001L
internal const val DIMENSION_OBJECTIVE_SALT: Long = 0x0D13_E5A1_0000_0002L

/** A displaced Elite returns, and any due Elite arrives, this long after the Core is back in realspace. */
internal const val EXIT_ELITE_DELAY_SECONDS: Float = 8f
/** The director arrives in a dimension building for this long, with at least this budget banked. */
private const val ARRIVAL_BUILD_SECONDS: Float = 10f
private const val ARRIVAL_SPAWN_BUDGET: Float = 5f
/** Back in realspace the director breathes for this long and keeps at most this budget. */
private const val RETURN_BREATHER_SECONDS: Float = 6f
private const val RETURN_SPAWN_BUDGET: Float = 3f
/** Room kept between a placed rift and any obstacle, beyond the rift's own radius. */
private const val PORTAL_CLEARANCE: Float = 30f
/** Room kept between an arriving objective boss and any obstacle, beyond the boss's own radius. */
private const val OBJECTIVE_BOSS_CLEARANCE: Float = 40f
/** An objective boss fights inside the widest encounter area around its arrival point. */
private const val OBJECTIVE_BOSS_AREA_RADIUS: Float = 2_400f
/** The widest encounter abandon distance: the visit's end, not distance, ends an objective boss's encounter. */
private const val OBJECTIVE_BOSS_ABANDON_DISTANCE: Float = 6_000f

/** How a visit ended: through its exit rift, by its timer, or forced before the Architect. */
internal enum class DimensionExit { PORTAL, TIMER, FORCED }

/**
 * True when a rift into [target] may be entered now: rifts are enabled, the run is running in realspace before the
 * Architect, [target] is a dimension not entered this run, at least the minimum visit fits before the exit margin,
 * and no boss-class enemy lives (entering would delete it and its reward).
 */
internal fun MutableGameState.canEnterDimension(target: WorldLayer): Boolean =
    phase == GamePhase.RUNNING && dimensionEntryOpen(target)

/**
 * The entry rules of [canEnterDimension] apart from the phase, so a rift's locked look holds while the run is paused
 * or a choice is open.
 */
internal fun MutableGameState.dimensionEntryOpen(target: WorldLayer): Boolean {
    val policy = content.dimensions
    if (!policy.enabled || worldLayer != WorldLayer.BASE || bossSpawned) return false
    if (target == WorldLayer.BASE || dimensionRun.visitedMask and target.bit != 0) return false
    if (content.tempo.bossAtSeconds - policy.exitMarginSeconds - elapsed < policy.minimumVisitSeconds) return false
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.alive && enemy.isBossClass) return false
    }
    return true
}

/** Enters [target] at the Core; false (nothing changes) when [canEnterDimension] refuses. */
internal fun MutableGameState.enterDimension(target: WorldLayer): Boolean {
    if (!canEnterDimension(target)) return false
    val policy = content.dimensions
    val definition = policy.definition(target)
    val serial = dimensionRun.visitedMask.countOneBits()
    val objective = rollObjective(definition, serial)
    val endsAt = min(elapsed + definition.visitSeconds, content.tempo.bossAtSeconds - policy.exitMarginSeconds)
    val from = worldLayer
    switchLayer(target)
    onWorldLayerChanged(from, target)
    // A displaced Elite is due at once: the layer holds it back, and the return reschedules it.
    clearHostilesForLayerChange(eliteRescheduleAt = elapsed)
    drawPickupsIn()
    resetDirectorForLayer(entering = true)
    dimensionSlowSeconds = 0f
    dimensionScorchClock = 0f
    dimensionLocalClock = 0f
    dimensionRun = dimensionRun.copy(
        visitedMask = dimensionRun.visitedMask or target.bit,
        glitchPortal = null,
        visit = newVisit(target, objective, serial, endsAt),
    )
    discoverWorld(target.discovery())
    message = DimensionNotices.entry(target, objective)
    messageTime = 2.6f
    emitVisualFx(VisualFxCue.ClearAll)
    emitSound(GameplayAudioCue.APEX)
    shockwave(coreX, coreY, 0.8f, 300f, 4)
    return true
}

/**
 * Ends the visit and returns the Core to realspace where it is, queueing the visit's reward (the smaller one when its
 * objective was not completed). The dimension's enemies stay behind. Completing both window dimensions early enough
 * opens the glitch rift into the Archive ahead of the Core.
 */
internal fun MutableGameState.exitDimension(reason: DimensionExit) {
    val visit = dimensionRun.visit ?: return
    // Refused before anything changes: each layer is entered once, so a fourth reward cannot exist.
    check(dimensionRun.pendingRewards.size < ContentBounds.MAX_PENDING_DIMENSION_REWARDS) {
        "At most ${ContentBounds.MAX_PENDING_DIMENSION_REWARDS} dimension rewards wait"
    }
    val from = worldLayer
    val policy = content.dimensions
    switchLayer(WorldLayer.BASE)
    onWorldLayerChanged(from, WorldLayer.BASE)
    clearHostilesForLayerChange(eliteRescheduleAt = Float.POSITIVE_INFINITY)
    drawPickupsIn()
    resetDirectorForLayer(entering = false)
    nextEliteAt = max(nextEliteAt, elapsed + EXIT_ELITE_DELAY_SECONDS)
    dimensionSlowSeconds = 0f
    dimensionScorchClock = 0f
    var next = dimensionRun.copy(
        visit = null,
        pendingRewards = dimensionRun.pendingRewards.toImmutableListAppending(DimensionRewardGrant(from, visit.completed)),
        completedMask = if (visit.completed) dimensionRun.completedMask or from.bit else dimensionRun.completedMask,
    )
    // Granted in realspace, so the Archive's Matter is never scaled by a layer.
    if (from == WorldLayer.ARCHIVE) {
        val reward = policy.definition(WorldLayer.ARCHIVE).reward
        grantMatter(if (visit.completed) reward.matterOnSuccess else reward.matterOnFailure)
    }
    val glitch = archiveUnlocked(next)
    if (glitch) next = next.copy(glitchPortal = glitchPortalAhead(next.nextPortalId), nextPortalId = next.nextPortalId + 1)
    dimensionRun = next
    // A forced return happens as the Architect arrives: its announcement stands.
    if (reason != DimensionExit.FORCED) {
        message = when {
            glitch -> DimensionNotices.ARCHIVE_STIRS
            reason == DimensionExit.TIMER && !visit.completed -> DimensionNotices.PULLED_BACK
            else -> DimensionNotices.REALSPACE
        }
        messageTime = 2.2f
    }
    emitVisualFx(VisualFxCue.ClearAll)
    emitSound(GameplayAudioCue.RECOVERED)
}

/** The layer switch itself: the new layer keeps the arrival point clear and regenerates the obstacle window. */
private fun MutableGameState.switchLayer(target: WorldLayer) {
    worldLayer = target
    worldKeepOut = WorldKeepOut(target, worldOriginX + coreX, worldOriginY + coreY, content.dimensions.arrivalClearRadius)
    switchWorldWindow()
}

/**
 * Removes every hostile of the layer being left without a kill (no Data, Matter, Keys or kill effects): encounters
 * end first and despawn their members, then the remaining enemies, hostile shots and mines, pending blasts and the
 * Totem go (the Keys stay and place a new Totem in realspace). A removed Elite is rescheduled to [eliteRescheduleAt]
 * at the latest.
 */
internal fun MutableGameState.clearHostilesForLayerChange(eliteRescheduleAt: Float) {
    endAllEncounters(despawn = true)
    var displacedElite = false
    for (index in enemies.indices) if (enemies[index].isElite) displacedElite = true
    enemies.clear()
    if (displacedElite) nextEliteAt = min(nextEliteAt, eliteRescheduleAt)
    projectiles.removeMatchingStable { projectile -> projectile.hostile }
    if (volatileBursts.isNotEmpty()) volatileBursts = immutableListOf()
    totem = null
}

/** Moves every pickup onto the Core, so the normal collection pass collects it with all its effects; none is lost. */
internal fun MutableGameState.drawPickupsIn() {
    for (index in pickups.indices) {
        val pickup = pickups[index]
        pickup.x = coreX
        pickup.y = coreY
        pickup.previousX = coreX
        pickup.previousY = coreY
        pickup.vx = 0f
        pickup.vy = 0f
        pickup.attracted = true
    }
}

/**
 * Sets the director's phase for the arrival (a short build with a small banked budget) or the return (a short
 * breather with little budget) and forgets the planned event and the surge's theme, as a phase started by the director
 * would ([onDirectorPhaseStarted]). Fixed durations: no spawn-stream draw.
 */
internal fun MutableGameState.resetDirectorForLayer(entering: Boolean) {
    if (entering) {
        directorPhase = SpawnDirectorPhase.BUILD.ordinal
        directorPhaseUntil = elapsed + ARRIVAL_BUILD_SECONDS
        spawnBudget = min(MAX_SPAWN_BUDGET, max(spawnBudget, ARRIVAL_SPAWN_BUDGET))
    } else {
        directorPhase = SpawnDirectorPhase.BREATHER.ordinal
        directorPhaseUntil = elapsed + RETURN_BREATHER_SECONDS
        spawnBudget = min(spawnBudget, RETURN_SPAWN_BUDGET)
    }
    surgeThemeType = -1
    pendingEventKind = -1
    pendingEventSize = 0
    pendingEventType = -1
    recentEventTypes = 0L
    previousEventTypes = 0L
    ambushSpeedSeconds = 0f
}

/** The visit's objective: a weighted pick among the layer's objectives from a stateless hash of the run and visit. */
private fun MutableGameState.rollObjective(definition: DimensionDefinition, serial: Int): DimensionObjective {
    val objectives = definition.objectives
    var total = 0f
    for (index in objectives.indices) total += objectives[index].weight
    var remaining = hashUnit(worldHash(runSeed, definition.layer, serial, 0, DIMENSION_OBJECTIVE_SALT)) * total
    for (index in objectives.indices) {
        val entry = objectives[index]
        if (remaining < entry.weight) return entry.objective
        remaining -= entry.weight
    }
    return objectives[objectives.size - 1].objective
}

/**
 * A visit into [layer] entered now with [objective]: its goal, its boss's arrival and the course anchored at the Core.
 * An Ascent places its first two course rings at once.
 */
internal fun MutableGameState.newVisit(layer: WorldLayer, objective: DimensionObjective, serial: Int, endsAt: Float): DimensionVisit {
    val tuning = content.dimensions.objectives
    val goal = when (objective) {
        DimensionObjective.ASCENT -> tuning.ascentRings
        DimensionObjective.HARVEST -> tuning.harvestEmbers
        DimensionObjective.JUDGEMENT, DimensionObjective.PYRE, DimensionObjective.ANTECEDENT -> 1
    }
    val bossSpawnAt = when (objective) {
        DimensionObjective.ASCENT, DimensionObjective.HARVEST -> Float.POSITIVE_INFINITY
        DimensionObjective.JUDGEMENT, DimensionObjective.PYRE -> elapsed + tuning.bossDelaySeconds
        DimensionObjective.ANTECEDENT -> elapsed + tuning.antecedentDelaySeconds
    }
    var visit = DimensionVisit(
        layer = layer, objective = objective, serial = serial, enteredAt = elapsed, endsAt = endsAt, goal = goal,
        bossSpawnAt = bossSpawnAt, courseX = coreX, courseY = coreY, courseHeading = travelHeading(),
    )
    if (objective == DimensionObjective.ASCENT) {
        for (index in 0 until min(ASCENT_RINGS_AHEAD, goal)) visit = withCourseRing(visit, index)
    }
    return visit
}

/** The encounter tag of the visit's objective boss: the dimension owner keyed by the layer and the visit's serial. */
internal fun dimensionEncounterTag(visit: DimensionVisit): Long =
    encounterTag(EncounterOwner.DIMENSION, (visit.layer.ordinal.toLong() shl 8) or visit.serial.toLong())

/**
 * The objective boss arrives once its delay ran out: [DimensionObjectiveTuning.bossDistance] ahead of the Core,
 * moved clear of obstacles, as a one-member encounter that may evict ordinary enemies for room and is ended by the
 * visit rather than by distance, with its layer's [DimensionDefinition.bossIntegrityShare] of its integrity. A refused
 * spawn (no room yet) is tried again on the next step. Draws nothing.
 */
private fun MutableGameState.spawnObjectiveBossIfDue() {
    val visit = dimensionRun.visit ?: return
    if (visit.completed || visit.bossId >= 0 || elapsed < visit.bossSpawnAt) return
    val type = visit.objective.boss ?: return
    val heading = travelHeading()
    val distance = content.dimensions.objectives.bossDistance
    val (x, y) = nudgedClearOfObstacles(
        coreX + cos(heading) * distance, coreY + sin(heading) * distance, type.profile.radius + OBJECTIVE_BOSS_CLEARANCE,
    )
    val tag = dimensionEncounterTag(visit)
    val spawned = spawnEncounter(
        EncounterRequest(
            tag = tag,
            centerX = x,
            centerY = y,
            areaRadius = OBJECTIVE_BOSS_AREA_RADIUS,
            members = listOf(type),
            integrityShare = content.dimensions.definition(visit.layer).bossIntegrityShare,
            abandonDistance = OBJECTIVE_BOSS_ABANDON_DISTANCE,
            evictOrdinary = true,
            positions = floatArrayOf(x, y),
        ),
    )
    if (!spawned) return
    val bossId = encounter(tag)?.bossId ?: return
    dimensionRun = dimensionRun.copy(visit = visit.copy(bossId = bossId))
}

/**
 * The integrity share [enemy] arrived with as the visit's objective boss (its layer's bossIntegrityShare), else 1. The
 * boss hit cap is divided by it, so the share shortens the fight in capped hits as well as in integrity: the cap is a
 * share of the boss's own integrity, which alone would leave a tier-10 boss as many capped hits at any share.
 */
internal fun MutableGameState.objectiveBossIntegrityShare(enemy: Enemy): Float {
    val visit = dimensionRun.visit ?: return 1f
    return if (enemy.id == visit.bossId) content.dimensions.definition(visit.layer).bossIntegrityShare else 1f
}

/** The share of its damage [enemy] deals as the visit's objective boss (its layer's bossDamageShare), else 1. */
internal fun MutableGameState.objectiveBossDamageShare(enemy: Enemy?): Float {
    val visit = dimensionRun.visit ?: return 1f
    return if (enemy != null && enemy.id == visit.bossId) content.dimensions.definition(visit.layer).bossDamageShare else 1f
}

/**
 * Completes the visit's objective: the exit rift opens [DimensionPolicy.exitPortalDistance] ahead of the Core (moved
 * clear of obstacles) until the visit's end, with the completion banner unless [announce] is false (a secret boss's
 * fall keeps its own defeat notice). A completed visit pays the full reward whether the Core leaves through the rift
 * or the timer pulls it back.
 */
internal fun MutableGameState.completeObjective(announce: Boolean = true) {
    val visit = dimensionRun.visit ?: return
    if (visit.completed) return
    val policy = content.dimensions
    val heading = travelHeading()
    val (x, y) = nudgedClearOfObstacles(
        coreX + cos(heading) * policy.exitPortalDistance, coreY + sin(heading) * policy.exitPortalDistance,
        policy.portalRadius + PORTAL_CLEARANCE,
    )
    dimensionRun = dimensionRun.copy(
        visit = visit.copy(
            progress = visit.goal,
            completed = true,
            splitEndsAt = Float.POSITIVE_INFINITY,
            splitSeconds = 0f,
            exitPortal = DimensionPortal(dimensionRun.nextPortalId, x, y, WorldLayer.BASE, visit.endsAt),
        ),
        nextPortalId = dimensionRun.nextPortalId + 1,
    )
    if (announce) {
        message = DimensionNotices.completed(visit.objective)
        messageTime = 2f
    }
    emitSound(GameplayAudioCue.LEVEL_UP)
}

/** The way the Core is going: its velocity when it moves, else its last aim (the window sites' heading). */
internal fun MutableGameState.travelHeading(): Float =
    if (speed >= 80f) atan2(velocityY, velocityX) else atan2(lastAimDirectionY, lastAimDirectionX)

private fun WorldLayer.discovery(): WorldDiscovery = when (this) {
    WorldLayer.ZENITH -> WorldDiscovery.ZENITH
    WorldLayer.NADIR -> WorldDiscovery.NADIR
    WorldLayer.ARCHIVE -> WorldDiscovery.ARCHIVE
    WorldLayer.BASE -> error("Realspace is no discovery")
}

/** The Archive may still open: entered by no rift yet, and its glitch rift's visit would end before the exit margin. */
private fun MutableGameState.archiveReachable(state: DimensionRunState): Boolean =
    content.dimensions.enabled && state.visitedMask and WorldLayer.ARCHIVE.bit == 0 && state.glitchPortal == null &&
        elapsed <= content.dimensions.latestGlitchRiftAt(content.tempo.bossAtSeconds)

/** Both window dimensions completed this run and the Archive still reachable. */
private fun MutableGameState.archiveUnlocked(state: DimensionRunState): Boolean {
    val both = WorldLayer.ZENITH.bit or WorldLayer.NADIR.bit
    return state.completedMask and both == both && archiveReachable(state)
}

/** The glitch rift [glitchPortalDistance] ahead of the Core, moved clear of obstacles. */
private fun MutableGameState.glitchPortalAhead(id: Int): DimensionPortal {
    val policy = content.dimensions
    val heading = travelHeading()
    return glitchPortalAt(id, coreX + cos(heading) * policy.glitchPortalDistance, coreY + sin(heading) * policy.glitchPortalDistance)
}

private fun MutableGameState.glitchPortalAt(id: Int, x: Float, y: Float): DimensionPortal {
    val policy = content.dimensions
    val (portalX, portalY) = nudgedClearOfObstacles(x, y, policy.portalRadius + PORTAL_CLEARANCE)
    return DimensionPortal(id, portalX, portalY, WorldLayer.ARCHIVE, elapsed + policy.glitchPortalLifetimeSeconds)
}

/**
 * Opens the glitch rift into the Archive at ([x], [y]) (moved clear of obstacles) without the two completions: the
 * Quiet Signal's way in. The same layer, time and once-per-run rules apply, and one glitch rift is open at a time;
 * false when they refuse.
 */
internal fun MutableGameState.openArchiveRiftAt(x: Float, y: Float): Boolean {
    if (phase != GamePhase.RUNNING || worldLayer != WorldLayer.BASE || bossSpawned || !archiveReachable(dimensionRun)) {
        return false
    }
    dimensionRun = dimensionRun.copy(glitchPortal = glitchPortalAt(dimensionRun.nextPortalId, x, y), nextPortalId = dimensionRun.nextPortalId + 1)
    message = DimensionNotices.ARCHIVE_STIRS
    messageTime = 2.2f
    return true
}

/**
 * Realspace step of the glitch rift: it closes at its expiry; flying through it enters the Archive unless another
 * engagement runs (the window sites' rule: one engagement at a time) or entry is refused, and then it stays open.
 */
private fun MutableGameState.updateGlitchPortal() {
    val portal = dimensionRun.glitchPortal ?: return
    if (elapsed >= portal.expiresAt) {
        dimensionRun = dimensionRun.copy(glitchPortal = null)
        return
    }
    val touched = segmentCircleIntersects(
        previousCoreX, previousCoreY, coreX, coreY, portal.x, portal.y, content.dimensions.portalRadius,
    )
    if (touched && !engagementActive()) enterDimension(WorldLayer.ARCHIVE)
}

/**
 * One step of the dimension system (after the sites): the glitch rift in realspace; in a dimension the visit clock,
 * the timer's pull back, the layer's physics and objective marks, the objective boss's arrival and the exit rift.
 */
internal fun MutableGameState.updateDimension(delta: Float) {
    if (phase != GamePhase.RUNNING) return
    if (worldLayer == WorldLayer.BASE) {
        updateGlitchPortal()
        return
    }
    val visit = dimensionRun.visit ?: return
    dimensionLocalClock += delta * layerTimeScale(null)
    if (elapsed >= visit.endsAt) {
        exitDimension(DimensionExit.TIMER)
        return
    }
    updateLayerMechanics(delta)
    // A lethal scorch tick ends the run.
    if (phase != GamePhase.RUNNING) return
    spawnObjectiveBossIfDue()
    val exit = dimensionRun.visit?.exitPortal ?: return
    if (segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, exit.x, exit.y, content.dimensions.portalRadius)) {
        exitDimension(DimensionExit.PORTAL)
    }
}

/** Returns the Core to realspace before the Architect arrives (a visit always ends earlier; this is the backstop). */
internal fun MutableGameState.forceRealspaceBeforeArchitect() {
    if (dimensionRun.visit != null) exitDimension(DimensionExit.FORCED)
}

/** A new run starts in realspace with no rift offered, no visit, no pending reward and no boons. */
internal fun MutableGameState.resetDimensions() {
    runBoons = immutableListOf()
    dimensionRun = DimensionRunState.EMPTY
    dimensionSlowSeconds = 0f
    dimensionScorchClock = 0f
    dimensionLocalClock = 0f
    dimensionRewardLayer = null
}

/** The world rebase moved every run-local position by ([dx], [dy]); rifts, rings, embers and the course follow. */
internal fun MutableGameState.rebaseDimensions(dx: Float, dy: Float) {
    dimensionRun = dimensionRun.shifted(dx, dy)
}
