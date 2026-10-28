// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.RunSecretFlag
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * Easter eggs: the Lost Cursor and the Quiet Signal are found by flying through them, the Golden Drifter is released by
 * coming near its hidden trigger, Spin Cycle is tracked around the singularity in every run, and the structures report
 * the Architect's Doodle. Each is found at most once per run, pays a small reward and records its world discovery.
 * Nothing here draws from a run stream.
 */

/** The Golden Drifter's encounter id (one alive at a time); its tag never meets a window, bounty or sigil tag. */
internal const val GOLDEN_DRIFTER_ENCOUNTER_ID: Long = 0x00F0_0000_0000_0001L

/** The Golden Drifter's encounter tag. */
internal val GOLDEN_DRIFTER_TAG: Long = encounterTag(EncounterOwner.SITE, GOLDEN_DRIFTER_ENCOUNTER_ID)

/**
 * The Golden Drifter's encounter keeps it within the widest encounter area around the spot it was sighted; its own
 * escape rule (time, or its distance from the Core) ends the chase long before the encounter would be abandoned.
 */
internal const val GOLDEN_AREA_RADIUS: Float = 2_400f
private const val GOLDEN_ABANDON_DISTANCE: Float = 6_000f

/** The Quiet Signal's words stay in the feed this long. */
internal const val QUIET_NOTICE_SECONDS: Float = 6f
private const val EGG_NOTICE_SECONDS: Float = 2.5f
private const val GOLDEN_ESCAPED_NOTICE_SECONDS: Float = 2f

/** Spin Cycle's flourish: a burst of sparks in the Core's own colour (fx colour 0). */
private const val SPIN_BURST_COUNT: Int = 24
private const val CORE_FX_COLOR: Int = 0
private const val LOST_CURSOR_BURST_COUNT: Int = 8
private const val BONE_FX_COLOR: Int = 1

/** The secret trackers after the ambient sites: Spin Cycle, then the Golden Drifter's escape. */
internal fun MutableGameState.updateSecretTrackers(delta: Float) {
    updateSpinCycle(delta)
    updateGoldenDrifter()
}

/**
 * Spin Cycle: the Core's net signed rotation around the singularity, counted only while it keeps within the policy's
 * band of it, over the completed one-second buckets the policy keeps plus the running one. Reaching the policy's turns
 * in that window finds it once per run; nothing is tracked after that.
 */
internal fun MutableGameState.updateSpinCycle(delta: Float) {
    val policy = content.sites.secrets
    if (!policy.spinEnabled || hasSecret(RunSecretFlag.SPIN_CYCLE)) return
    val dx = coreX - (cameraX + pointerX - screenWidth * 0.5f)
    val dy = coreY - (cameraY + pointerY - screenHeight * 0.5f)
    val gapSquared = dx * dx + dy * dy
    if (gapSquared >= policy.spinMinDistance * policy.spinMinDistance &&
        gapSquared <= policy.spinMaxDistance * policy.spinMaxDistance
    ) {
        val angle = atan2(dy, dx)
        if (spinPrimed) spinCurrent += wrappedTurn(angle - spinLastAngle)
        spinLastAngle = angle
        spinPrimed = true
    } else {
        // Outside the band no rotation counts, and the next angle inside it starts a fresh delta.
        spinPrimed = false
    }
    spinClock += delta
    var net = spinCurrent
    for (index in spinBuckets.indices) net += spinBuckets[index]
    // The window holds up to the kept buckets plus the running one, checked before the running bucket closes.
    if (abs(net) >= policy.spinTurns * TAU) {
        completeSpinCycle()
        return
    }
    if (spinClock >= 1f) {
        spinBuckets = (spinBuckets + spinCurrent).takeLast(policy.spinBuckets)
        spinCurrent = 0f
        spinClock -= 1f
    }
}

/** [radians] wrapped into (−π, π]. */
private fun wrappedTurn(radians: Float): Float {
    var turn = radians
    while (turn > PI.toFloat()) turn -= TAU
    while (turn <= -PI.toFloat()) turn += TAU
    return turn
}

/** Spin Cycle found: its notice, the discovery, a flourish and its small Matter (nothing that acts in the run). */
private fun MutableGameState.completeSpinCycle() {
    setSecret(RunSecretFlag.SPIN_CYCLE)
    message = SiteNotice.SPIN_CYCLE.english
    messageTime = EGG_NOTICE_SECONDS
    discoverWorld(WorldDiscovery.SPIN_CYCLE)
    burst(coreX, coreY, SPIN_BURST_COUNT, CORE_FX_COLOR)
    grantSiteReward(
        content.sites.secrets.spinReward, coreX, coreY,
        runHash(runSeed, REWARD_SCATTER_SALT, RunSecretFlag.SPIN_CYCLE.ordinal.toLong()),
    )
}

/**
 * The Core flew through the Lost Cursor: found once per run (every other Lost Cursor leaves the window), its reward
 * granted where it drifted; from now on it follows the Core.
 */
internal fun MutableGameState.findLostCursor(site: SiteState) {
    setSecret(RunSecretFlag.LOST_CURSOR_FOUND)
    removeAmbientSitesOf(SiteKind.LOST_CURSOR)
    message = SiteNotice.LOST_CURSOR_FOUND.english
    messageTime = EGG_NOTICE_SECONDS
    discoverWorld(WorldDiscovery.LOST_CURSOR)
    emitSound(GameplayAudioCue.PICKUP)
    burst(site.x, site.y, LOST_CURSOR_BURST_COUNT, BONE_FX_COLOR)
    grantSiteReward(content.sites.secrets.lostCursorReward, site.x, site.y, ambientScatterSeed(site))
}

/**
 * The Core flew through the Quiet Signal: heard once per run (every other Quiet Signal leaves the window), its reward
 * granted, and the Archive's glitch rift asked to open where DIMENSIONS opens its own, ahead of the Core, so the pass
 * through the signal never carries the Core into the Archive by itself. The signal's words stay in the feed for six
 * seconds over the rift's own notice.
 */
internal fun MutableGameState.hearQuietSignal(site: SiteState) {
    setSecret(RunSecretFlag.QUIET_SIGNAL_FOUND)
    removeAmbientSitesOf(SiteKind.QUIET_SIGNAL)
    discoverWorld(WorldDiscovery.QUIET_SIGNAL)
    grantSiteReward(content.sites.secrets.quietReward, site.x, site.y, ambientScatterSeed(site))
    val heading = travelHeading()
    val ahead = content.dimensions.glitchPortalDistance
    openArchiveRiftAt(coreX + cos(heading) * ahead, coreY + sin(heading) * ahead)
    message = SiteNotice.QUIET_SIGNAL.english
    messageTime = QUIET_NOTICE_SECONDS
}

/** Data and repair pickups of an ambient egg scatter from its cell's hash. */
private fun MutableGameState.ambientScatterSeed(site: SiteState): Long =
    worldHash(runSeed, ambientCellX(site.id), ambientCellY(site.id), REWARD_SCATTER_SALT)

/**
 * The Core came within the trigger radius of a Golden Drifter site: an ordinary Drifter is released there as the
 * site's own encounter (leash-exempt, never evicted, outside the ordinary cap), unless one already flies, one was
 * caught this run, or the encounter is refused (then the trigger stays and tries again). The trigger is consumed.
 */
internal fun MutableGameState.releaseGoldenDrifter(site: SiteState) {
    if (goldenDrifterId >= 0 || hasSecret(RunSecretFlag.GOLDEN_DRIFTER_CAUGHT)) return
    val released = spawnEncounter(
        EncounterRequest(
            tag = GOLDEN_DRIFTER_TAG,
            centerX = site.x,
            centerY = site.y,
            areaRadius = GOLDEN_AREA_RADIUS,
            members = listOf(EnemyType.DRIFTER),
            ringRadius = 0f,
            abandonDistance = GOLDEN_ABANDON_DISTANCE,
            announce = false,
            positions = floatArrayOf(site.x, site.y),
        ),
    )
    if (!released) return
    goldenDrifterId = enemies.last { it.ownerTag == GOLDEN_DRIFTER_TAG }.id
    goldenDrifterUntil = elapsed + content.sites.secrets.goldenLifetime
    setSecret(RunSecretFlag.GOLDEN_DRIFTER_SPAWNED)
    consumeAmbientSite(site)
    message = SiteNotice.GOLDEN_SIGHTED.english
    messageTime = EGG_NOTICE_SECONDS
}

/**
 * The Golden Drifter's step before the enemies move: it gets away (removed without a kill) once its time is up or it
 * is farther from the Core than the escape distance; a drifter gone some other way just ends the chase, and a caught
 * one's encounter closes.
 */
private fun MutableGameState.updateGoldenDrifter() {
    if (goldenDrifterId < 0) {
        if (encounters.isNotEmpty() && encounter(GOLDEN_DRIFTER_TAG) != null && liveEncounterMembers(GOLDEN_DRIFTER_TAG) == 0) {
            endEncounter(GOLDEN_DRIFTER_TAG, despawn = false)
        }
        return
    }
    val drifter = goldenDrifter()
    if (drifter == null) {
        goldenDrifterId = -1
        endEncounter(GOLDEN_DRIFTER_TAG, despawn = false)
        return
    }
    val escape = content.sites.secrets.goldenEscapeDistance
    if (elapsed >= goldenDrifterUntil || distanceSquared(coreX, coreY, drifter.x, drifter.y) > escape * escape) {
        releaseGoldenDrifterWithoutKill()
        message = SiteNotice.GOLDEN_ESCAPED.english
        messageTime = GOLDEN_ESCAPED_NOTICE_SECONDS
    }
}

/** The flying Golden Drifter, or null. */
internal fun MutableGameState.goldenDrifter(): Enemy? {
    if (goldenDrifterId < 0) return null
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.id == goldenDrifterId && enemy.alive) return enemy
    }
    return null
}

/** Removes a flying Golden Drifter without a kill or reward (it got away, or the Architect arrived). */
internal fun MutableGameState.releaseGoldenDrifterWithoutKill() {
    if (goldenDrifterId < 0) return
    endEncounter(GOLDEN_DRIFTER_TAG, despawn = true)
    goldenDrifterId = -1
}

/** Kill hook ([recordSiteKill]): the Golden Drifter caught pays its reward and discovery once per run. */
internal fun MutableGameState.catchGoldenDrifter(enemy: Enemy) {
    goldenDrifterId = -1
    setSecret(RunSecretFlag.GOLDEN_DRIFTER_CAUGHT)
    message = SiteNotice.GOLDEN_CAUGHT.english
    messageTime = EGG_NOTICE_SECONDS
    discoverWorld(WorldDiscovery.GOLDEN_DRIFTER)
    grantSiteReward(
        content.sites.secrets.goldenReward, enemy.x, enemy.y,
        runHash(runSeed, REWARD_SCATTER_SALT, GOLDEN_DRIFTER_ENCOUNTER_ID xor enemy.id.toLong()),
    )
}

/**
 * The structures' hook: the Core reached the Architect's Doodle. Found once per run: its notice, the discovery and its
 * reward at the Core; later calls change nothing.
 */
internal fun MutableGameState.discoverDoodle() {
    if (hasSecret(RunSecretFlag.DOODLE_FOUND)) return
    setSecret(RunSecretFlag.DOODLE_FOUND)
    message = SiteNotice.DOODLE.english
    messageTime = EGG_NOTICE_SECONDS
    discoverWorld(WorldDiscovery.ARCHITECTS_DOODLE)
    grantSiteReward(
        content.sites.secrets.doodleReward, coreX, coreY,
        runHash(runSeed, REWARD_SCATTER_SALT, RunSecretFlag.DOODLE_FOUND.ordinal.toLong()),
    )
}
