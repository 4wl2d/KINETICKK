// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DimensionNotices
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.*

/** A new run: no sites, no queued rewards, no secrets; the site stream and the discovery masks are kept. */
internal fun MutableGameState.resetSites() {
    sites = emptyList()
    nextSiteWindowIndex = 0
    pendingDirectedRewards = emptyList()
    directedReward = null
    selectedRewardFocus = null
    ambientSites = AmbientSiteWindow.EMPTY
    secretSigil = null
    openedSiteIds = emptyList()
    runSecretFlags = 0
    pendingFloorOffers = emptyList()
    goldenDrifterId = -1
    goldenDrifterUntil = 0f
    spinBuckets = emptyList()
    spinCurrent = 0f
    spinClock = 0f
    spinLastAngle = 0f
    spinPrimed = false
}

/** A world layer switch: every placed site of the old layer is gone (the Golden Drifter with them). */
internal fun MutableGameState.clearSitesForLayerChange() {
    sites = emptyList()
    ambientSites = AmbientSiteWindow.EMPTY
    secretSigil = null
    goldenDrifterId = -1
}

internal fun MutableGameState.updateSites(delta: Float) {
    if (phase != GamePhase.RUNNING) return
    if (!content.sites.enabled) {
        sites = emptyList()
        ambientSites = AmbientSiteWindow.EMPTY
        secretSigil = null
        return
    }
    if (bossSpawned || elapsed >= content.tempo.bossAtSeconds) {
        for (index in sites.indices) releaseSiteFoes(sites[index])
        sites = emptyList()
        // Nothing opens once the Architect arrives, so nothing ambient stays in view.
        ambientSites = AmbientSiteWindow.EMPTY
        return
    }
    offerDueWindow()
    advanceWindowSites(delta)
    updateAmbientSites()
}

/**
 * The window sites' step: with none running, closed offers leave and the first offer the Core flew through starts
 * (refused while another engagement runs); a running site advances by its family's rules.
 */
private fun MutableGameState.advanceWindowSites(delta: Float) {
    val active = activeWindowSite()
    if (active == null) {
        if (sites.anyExpired(elapsed)) sites = sites.filter { elapsed < it.expiresAt }
        val selected = sites.firstOrNull {
            segmentCircleIntersects(
                previousCoreX, previousCoreY, coreX, coreY, it.x, it.y, content.sites.definition(it.kind).activationRadius,
            )
        } ?: return
        // One engagement at a time: a refused activation leaves the pair offered.
        if (!engagementActive()) activateWindowSite(selected)
        return
    }
    when (active.kind.family) {
        SiteFamily.RACE -> advanceRaceSite(active, delta)
        SiteFamily.CHALLENGE -> advanceChallengeSite(active, delta)
        // The bounty never starts until its family lands, and an accepted rift leaves the window sites to the
        // dimension visit; the anomaly countdown is the step of everything else.
        SiteFamily.ANOMALY, SiteFamily.BOUNTY, SiteFamily.PORTAL, SiteFamily.CACHE, SiteFamily.SECRET, SiteFamily.SIGNAL,
        -> advanceAnomalySite(active, delta)
    }
}

/**
 * One step of a running anomaly: lost on time out or 2,400 u from its centre; resolved by the circuit's three
 * beacons, the vault's three defenders or eight seconds in the orbit's ring.
 */
private fun MutableGameState.advanceAnomalySite(active: SiteState, delta: Float) {
    val policy = content.sites.windows
    var trial = active.copy(remaining = max(0f, active.remaining - delta))
    if (trial.remaining <= 0f || distanceSquared(coreX, coreY, trial.x, trial.y) > square(2_400f)) {
        sites = emptyList()
        message = SiteNotice.ANOMALY_LOST.english
        messageTime = 1.6f
        return
    }
    var completed = false
    when (trial.kind) {
        SiteKind.RESONANT_CIRCUIT -> {
            val beacon = CircuitBeacons[trial.step]
            if (segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, trial.x + beacon.dx, trial.y + beacon.dy, 55f)) {
                trial = trial.copy(step = trial.step + 1)
                completed = trial.step == CircuitBeacons.size
            }
        }
        SiteKind.SEALED_ANOMALY -> {
            completed = trial.count == 3
            // Defenders remain around their vault, rather than following a fleeing player forever.
            for (enemy in enemies) if (enemy.id in trial.foeIds && !enemy.dead) {
                val dx = enemy.x - trial.x
                val dy = enemy.y - trial.y
                val distance = length(dx, dy)
                if (distance > 290f) {
                    enemy.x = trial.x + dx / distance * 290f
                    enemy.y = trial.y + dy / distance * 290f
                    enemy.previousX = enemy.x
                    enemy.previousY = enemy.y
                }
            }
        }
        SiteKind.COLLAPSING_ORBIT -> {
            val distance = length(coreX - trial.x, coreY - trial.y)
            val moved = length(coreX - previousCoreX, coreY - previousCoreY)
            val earned = if (distance in 120f..190f && moved >= 80f * delta) delta else 0f
            trial = trial.copy(meter = min(policy.orbitRequiredSeconds, trial.meter + earned))
            completed = trial.meter >= policy.orbitRequiredSeconds
            if (trial.warningRemaining > 0f) {
                val warning = max(0f, trial.warningRemaining - delta)
                trial = trial.copy(warningRemaining = warning)
                if (warning <= 0f) fireOrbitVolley(trial)
            } else {
                trial = trial.copy(volleyClock = trial.volleyClock - delta)
                if (trial.volleyClock <= 0f) {
                    trial = trial.copy(volleyClock = 2.2f, warningRemaining = 0.9f,
                        volleyAngle = atan2(coreY - trial.y, coreX - trial.x))
                }
            }
        }
        // Only the anomalies are offered until the site lane draws kinds by weight.
        else -> Unit
    }
    if (completed) {
        if (trial.kind == SiteKind.COLLAPSING_ORBIT) pendingCompletedOrbits++
        sites = emptyList() // consume before any choice can open
        message = SiteNotice.ANOMALY_RESOLVED.english
        messageTime = 2f
        // Granted after the notice, so a full directed queue's REWARD SALVAGED is the one shown.
        grantSiteReward(
            content.sites.definition(trial.kind).reward, trial.x, trial.y,
            worldHash(runSeed, trial.offerId, trial.side, REWARD_SCATTER_SALT),
        )
    } else sites = listOf(trial)
}

/**
 * Offers the due window, if any: a missed window is consumed and leaving a site never rolls another offer. The kinds
 * always take exactly two site-stream draws, then a rift may claim one side, so the stream never depends on rifts.
 */
private fun MutableGameState.offerDueWindow() {
    val policy = content.sites.windows
    val scheduledAt = policy.firstOfferAt + nextSiteWindowIndex * policy.offerInterval
    if (scheduledAt > policy.lastOfferAt || elapsed < scheduledAt) return
    val offerId = nextSiteWindowIndex++
    if (!layerFeatures().sites || sites.isNotEmpty() || elapsed >= scheduledAt + policy.offerLifetime ||
        scheduledAt + policy.offerLifetime + policy.trialDuration >= content.tempo.bossAtSeconds
    ) return
    val (first, second) = rollWindowKinds(scheduledAt)
    offerWindowPair(offerId, scheduledAt, first, second, claimPortalForWindow(offerId, scheduledAt))
}

/**
 * Places a window's pair at the 0.5.0 positions (heading ∓ 1 rad at 560 u, moved clear of obstacles); a [claim] turns
 * its side into the rift of its target and names the rift in the notice.
 */
internal fun MutableGameState.offerWindowPair(
    offerId: Int,
    scheduledAt: Float,
    first: SiteKind,
    second: SiteKind,
    claim: PortalClaim?,
) {
    val rift = claim?.let { riftKind(it.target) }
    val riftSide = claim?.side ?: -1
    check(claim == null || riftSide == 0 || riftSide == 1) { "A rift claims side 0 or 1 of a window" }
    val heading = if (speed >= 80f) atan2(velocityY, velocityX) else atan2(lastAimDirectionY, lastAimDirectionX)
    sites = List(2) { side ->
        val angle = heading + if (side == 0) -1.0f else 1.0f
        // Window sites move out from under obstacles, so their rings and beacons stay reachable.
        val (pointX, pointY) = clearPointOfInterestSite(coreX + cos(angle) * 560f, coreY + sin(angle) * 560f)
        val kind = if (rift != null && side == riftSide) rift else if (side == 0) first else second
        SiteState(windowSiteId(offerId, side), kind, pointX, pointY, scheduledAt + content.sites.windows.offerLifetime)
    }
    message = when {
        rift != null -> DimensionNotices.RIFT_DETECTED
        first.family == SiteFamily.ANOMALY && second.family == SiteFamily.ANOMALY -> SiteNotice.TWO_ANOMALIES.english
        else -> SiteNotice.TWO_SITES.english
    }
    messageTime = 2.5f
}

/** The window site kind of a rift's target; realspace and the Archive's glitch rift are never window sites. */
private fun riftKind(target: WorldLayer): SiteKind? = when (target) {
    WorldLayer.ZENITH -> SiteKind.ZENITH_RIFT
    WorldLayer.NADIR -> SiteKind.NADIR_RIFT
    WorldLayer.BASE, WorldLayer.ARCHIVE -> null
}

/**
 * The two kinds of a window, from exactly two site-stream draws: the first by window weight among the eligible kinds,
 * the second among the rest with the first kind's family down-weighted by the policy's same-family weight. Site
 * policy validation guarantees both draws a positive weight.
 */
internal fun MutableGameState.rollWindowKinds(scheduledAt: Float): Pair<SiteKind, SiteKind> {
    val weights = FloatArray(SiteKind.entries.size) // one small array per offered window (at most six per run)
    var total = 0f
    for (kind in SiteKind.entries) {
        val weight = if (windowEligible(kind, scheduledAt)) content.sites.definition(kind).windowWeight else 0f
        weights[kind.ordinal] = weight
        total += weight
    }
    val first = weightedSite(weights, total, siteRandom.nextFloat())
    weights[first.ordinal] = 0f
    total = 0f
    for (kind in SiteKind.entries) {
        if (kind.family == first.family) weights[kind.ordinal] *= content.sites.sameFamilyWeight
        total += weights[kind.ordinal]
    }
    return first to weightedSite(weights, total, siteRandom.nextFloat())
}

/**
 * True when a window scheduled at [scheduledAt] may draw [kind]: it has window weight, its earliest window and Rebirth
 * tier are reached, and its reserve still ends before the Architect. Rifts are never drawn; a rift claims its side.
 */
internal fun MutableGameState.windowEligible(kind: SiteKind, scheduledAt: Float): Boolean {
    val definition = content.sites.definition(kind)
    return kind.family != SiteFamily.PORTAL && definition.windowWeight > 0f && scheduledAt >= definition.minimumOfferAt &&
        rebirthLevel >= definition.minimumRebirth &&
        scheduledAt + content.sites.windows.offerLifetime + definition.reserveSeconds < content.tempo.bossAtSeconds
}

/** Linear scan of [weights] at `roll × total`; float rounding falls back to the last positive weight. */
private fun weightedSite(weights: FloatArray, total: Float, roll: Float): SiteKind {
    check(total > 0f) { "A window draw needs a positive weight" }
    var remaining = roll * total
    var last = -1
    for (index in weights.indices) {
        val weight = weights[index]
        if (weight <= 0f) continue
        last = index
        if (remaining < weight) return SiteKind.entries[index]
        remaining -= weight
    }
    return SiteKind.entries[last]
}

/** True when an offer closed; the pair is rebuilt only then. */
private fun List<SiteState>.anyExpired(elapsed: Float): Boolean {
    for (index in indices) if (elapsed >= this[index].expiresAt) return true
    return false
}

/** The Core entered an offered window site: its family's rules start it. */
internal fun MutableGameState.activateWindowSite(point: SiteState) {
    when (point.kind.family) {
        SiteFamily.ANOMALY -> activateAnomalySite(point)
        SiteFamily.RACE -> startWindowTrial(startedRace(point))
        // A Ram rite without room for its foes refuses to start and stays offered.
        SiteFamily.CHALLENGE -> startedChallenge(point)?.let { startWindowTrial(it) }
        SiteFamily.PORTAL -> activatePortalSite(point)
        // The bounty is not offered until its family lands; caches and secrets are never window sites.
        SiteFamily.BOUNTY, SiteFamily.CACHE, SiteFamily.SECRET, SiteFamily.SIGNAL -> Unit
    }
}

/** A started window trial replaces the offered pair, and its instruction becomes the run message. */
private fun MutableGameState.startWindowTrial(started: SiteState) {
    sites = listOf(started)
    message = content.sites.definition(started.kind).instruction
    messageTime = 4f
}

private fun MutableGameState.activateAnomalySite(point: SiteState) {
    if (point.kind == SiteKind.SEALED_ANOMALY && enemies.size + 3 > content.rebirth.maxActiveEnemies) return
    val ids = mutableListOf<Int>()
    if (point.kind == SiteKind.SEALED_ANOMALY) repeat(3) { index ->
        val angle = TAU * index / 3f
        val integrity = spawnIntegrity(EnemyType.WARDEN) * POINT_DEFENDER_HEALTH_SHARE
        val enemy = Enemy(
            id = nextEntityId++, type = EnemyType.WARDEN,
            x = point.x + cos(angle) * 130f, y = point.y + sin(angle) * 130f,
            hp = integrity, maxHp = integrity, radius = 26f, actionTimer = 0.8f + index * 0.4f,
        )
        enemies += enemy
        ids += enemy.id
    }
    startWindowTrial(point.copy(active = true, remaining = content.sites.windows.trialDuration, foeIds = ids.toImmutableList()))
}

/**
 * Kill hook of [onEnemyKilled]: a defeated foe of the active window site counts toward its trial (a Ram rite's foe
 * only when a Dash killed it; any other kill shows as one foe fewer).
 */
internal fun MutableGameState.recordSiteKill(enemy: Enemy) {
    val trial = sites.singleOrNull()?.takeIf { it.active && enemy.id in it.foeIds } ?: return
    // Called once by onEnemyKilled, whose dead guard also protects this counter.
    val count = when (trial.kind) {
        SiteKind.RAM_TRIAL -> if (isDashKill(enemy)) trial.count + 1 else return
        else -> min(3, trial.count + 1)
    }
    sites = listOf(trial.copy(count = count))
}

private fun MutableGameState.fireOrbitVolley(point: SiteState) {
    val dx = cos(point.volleyAngle)
    val dy = sin(point.volleyAngle)
    val damage = hostileDamage(null, 12f)
    repeat(3) { lane ->
        val lateral = (lane - 1) * 60f
        addProjectile(Projectile(point.x - dx * 320f - dy * lateral,
            point.y - dy * 320f + dx * lateral, dx * 460f, dy * 460f,
            radius = 7f, life = 1.5f, hostile = true, damage = damage))
    }
}
