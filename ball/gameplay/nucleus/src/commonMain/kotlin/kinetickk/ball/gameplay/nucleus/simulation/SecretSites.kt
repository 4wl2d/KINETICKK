// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EnemyNotices
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.RunSecretFlag
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * Easter eggs: the Lost Cursor and the Quiet Signal are found by flying through them, the Golden Drifter is released by
 * coming near its hidden trigger, Spin Cycle is tracked around the singularity in every run, and the structures report
 * the Architect's Doodle. Each is found at most once per run, pays a small reward and records its world discovery.
 * The Cartographer's sigil appears ahead of a Core that flew far enough early enough; touching it summons the
 * Cartographer for a duel the sigil pays for when the boss falls. Nothing here draws from a run stream.
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

/** Site and encounter id of the far sigil (the sigils' base plus the kind's ordinal); its tag meets no other site's. */
internal const val SIGIL_ID_BASE: Long = 0x00F0_0000_0000_0010L
internal val FAR_SIGIL_ID: Long = SIGIL_ID_BASE + SiteKind.FAR_SIGIL.ordinal

/** The secret boss the far sigil summons. */
internal val SIGIL_BOSS: EnemyType = EnemyType.CARTOGRAPHER

/** The sigil's boss arrives this far above it; the sigil is placed clear of obstacles by this radius. */
internal const val SIGIL_BOSS_OFFSET: Float = 260f
private const val SIGIL_CLEARANCE: Float = 140f
private const val SIGIL_APPEARS_NOTICE_SECONDS: Float = 2.5f
private const val SIGIL_FADES_NOTICE_SECONDS: Float = 1.6f
private const val SIGIL_BROKEN_NOTICE_SECONDS: Float = 2f

/** The secret trackers after the sigil: far travel, Spin Cycle, then the Golden Drifter's escape. */
internal fun MutableGameState.updateSecretTrackers(delta: Float) {
    updateFarTravel()
    updateSpinCycle(delta)
    updateGoldenDrifter()
}

/**
 * Far travel, the Cartographer's condition: the first step, before the policy's time, on which the Core is at least the
 * far-travel distance from the run's spawn in absolute space sets the flag once and places the sigil ahead of the Core.
 * Only where the layer has sites; after the flag or the time nothing is measured.
 */
private fun MutableGameState.updateFarTravel() {
    val policy = content.sites.secrets
    if (hasSecret(RunSecretFlag.FAR_TRAVEL) || elapsed >= policy.farTravelBefore || !layerFeatures().sites) return
    val reach = policy.farTravelDistance.toDouble()
    if (absoluteDistanceSquared(coreX, coreY) < reach * reach) return
    setSecret(RunSecretFlag.FAR_TRAVEL)
    val heading = travelHeading()
    spawnSecretSigil(coreX + cos(heading) * policy.sigilAhead, coreY + sin(heading) * policy.sigilAhead)
}

/** Sigils neither appear nor summon from this second, and an untouched one fades then. */
internal fun MutableGameState.sigilCutoffAt(): Float = content.tempo.bossAtSeconds - content.sites.secrets.sigilCutoff

/** True while [type] may still be summoned this run (a secret boss arrives at most once). */
private fun MutableGameState.secretBossAvailable(type: EnemyType): Boolean = secretBossMask and (1L shl type.ordinal) == 0L

/**
 * Places the far sigil at ([x], [y]), moved clear of obstacles and structures, unless a sigil already lies in the world,
 * the sigil cutoff has come or the Cartographer cannot arrive any more. It waits there until the cutoff.
 */
internal fun MutableGameState.spawnSecretSigil(x: Float, y: Float) {
    val cutoff = sigilCutoffAt()
    if (secretSigil != null || elapsed >= cutoff || !secretBossAvailable(SIGIL_BOSS)) return
    val (sigilX, sigilY) = nudgedClearOfObstacles(x, y, SIGIL_CLEARANCE)
    secretSigil = SiteState(FAR_SIGIL_ID, SiteKind.FAR_SIGIL, sigilX, sigilY, expiresAt = cutoff)
    setSecret(RunSecretFlag.FAR_SIGIL_SPAWNED)
    message = SiteNotice.SIGIL_APPEARS.english
    messageTime = SIGIL_APPEARS_NOTICE_SECONDS
}

/**
 * The sigil's step before the secret trackers. Untouched, it fades at its cutoff, and the Core's swept step through its
 * circle summons the Cartographer unless another engagement runs or the encounter is refused (it stays to be touched
 * again). Summoned, the duel is the engagement: the boss's fall breaks the sigil at the next step, wherever the Core
 * is; the Core farther than the duel leash from the sigil, or a boss gone without a kill, fades it. A sigil waiting in
 * realspace is neither touched nor faded during a visit to another layer.
 */
internal fun MutableGameState.updateSecretSigil() {
    if (!inBaseLayer()) return
    val sigil = secretSigil ?: return
    val policy = content.sites.secrets
    if (!sigil.active) {
        if (elapsed >= sigil.expiresAt) {
            secretSigil = null
            message = SiteNotice.SIGIL_FADES.english
            messageTime = SIGIL_FADES_NOTICE_SECONDS
            return
        }
        if (segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, sigil.x, sigil.y, policy.sigilRadius) &&
            !engagementActive()
        ) {
            summonSigilBoss(sigil)
        }
        return
    }
    when {
        sigil.count >= 1 -> breakSigil(sigil)
        distanceSquared(coreX, coreY, sigil.x, sigil.y) > policy.duelLeash * policy.duelLeash || !sigilBossAlive(sigil) ->
            fadeSigilDuel(sigil)
    }
}

/**
 * The Cartographer arrives above the sigil as an encounter the sigil owns: kept within the duel leash of it,
 * leash-exempt, with its own profile aspect, evicting the oldest ordinary enemy at the hard cap, announced by its
 * secret signal and drawn from no run stream. A refused summoning changes nothing.
 */
private fun MutableGameState.summonSigilBoss(sigil: SiteState) {
    val policy = content.sites.secrets
    val tag = siteEncounterTag(sigil)
    val summoned = spawnEncounter(
        EncounterRequest(
            tag = tag,
            centerX = sigil.x,
            centerY = sigil.y,
            areaRadius = policy.duelLeash,
            members = listOf(SIGIL_BOSS),
            integrityShare = policy.secretBossIntegrityShare,
            abandonDistance = policy.duelLeash,
            evictOrdinary = true,
            positions = floatArrayOf(sigil.x, sigil.y - SIGIL_BOSS_OFFSET),
        ),
    )
    if (!summoned) return
    val boss = encounter(tag)?.bossId ?: -1
    check(boss >= 0) { "A sigil's encounter holds its secret boss" }
    secretSigil = sigil.copy(active = true, foeIds = immutableListOf(boss))
    setSecret(RunSecretFlag.CARTOGRAPHER_SUMMONED)
}

/** True while the duel's boss is alive. */
private fun MutableGameState.sigilBossAlive(sigil: SiteState): Boolean {
    val boss = sigil.foeIds.firstOrNull() ?: return false
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.id == boss) return enemy.alive
    }
    return false
}

/** Kill hook ([recordSiteKill]): the duel's boss fell where it stood; the sigil breaks at its next step. */
internal fun MutableGameState.recordSigilKill(enemy: Enemy): Boolean {
    val sigil = secretSigil?.takeIf { it.active && enemy.id in it.foeIds } ?: return false
    secretSigil = sigil.copy(count = 1, aimX = enemy.x, aimY = enemy.y)
    return true
}

/**
 * The Cartographer fell: its own kill already paid the secret-boss reward and its discovery; the sigil adds its reward
 * where the boss fell and the run's defeat flag. Its notice never replaces the boss's own defeat notice while that shows.
 */
private fun MutableGameState.breakSigil(sigil: SiteState) {
    endEncounter(siteEncounterTag(sigil), despawn = false)
    secretSigil = null // consume before any choice can open
    setSecret(RunSecretFlag.CARTOGRAPHER_DEFEATED)
    discoverWorld(WorldDiscovery.CARTOGRAPHER)
    if (messageTime <= 0f || !message.startsWith(EnemyNotices.SECRET_BOSS_DEFEATED)) {
        message = SiteNotice.SIGIL_BROKEN.english
        messageTime = SIGIL_BROKEN_NOTICE_SECONDS
    }
    // Granted after the notice, so a salvaged reward's notice is the one shown.
    grantSiteReward(content.sites.secrets.secretReward, sigil.aimX, sigil.aimY, runHash(runSeed, REWARD_SCATTER_SALT, sigil.id))
}

/** The duel ended without the boss's fall: the boss leaves without a kill and nothing is granted. */
private fun MutableGameState.fadeSigilDuel(sigil: SiteState) {
    endEncounter(siteEncounterTag(sigil), despawn = true)
    secretSigil = null
    message = SiteNotice.SIGIL_FADES.english
    messageTime = SIGIL_FADES_NOTICE_SECONDS
}

/** The Architect's arrival: a sigil leaves the world, and a summoned boss leaves without a kill. */
internal fun MutableGameState.releaseSecretSigil() {
    val sigil = secretSigil ?: return
    if (sigil.active) endEncounter(siteEncounterTag(sigil), despawn = true)
    secretSigil = null
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
 * seconds over the rift's own notice. During an engagement it stays unheard, so that fight cannot consume the rift's
 * short entry window.
 */
internal fun MutableGameState.hearQuietSignal(site: SiteState) {
    if (engagementActive()) return
    consumeAmbientSite(site)
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
