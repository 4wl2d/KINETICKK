// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ANOMALY_KIND_COUNT
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
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
    val policy = content.sites.windows
    val bossAt = content.tempo.bossAtSeconds
    if (bossSpawned || elapsed >= bossAt) {
        sites = emptyList()
        return
    }
    // A missed scheduled window is consumed; leaving a point never rolls another offer.
    val scheduledAt = policy.firstOfferAt + nextSiteWindowIndex * policy.offerInterval
    if (scheduledAt <= policy.lastOfferAt && elapsed >= scheduledAt) {
        val offerId = nextSiteWindowIndex++
        if (sites.isEmpty() && elapsed < scheduledAt + policy.offerLifetime &&
            scheduledAt + policy.offerLifetime + policy.trialDuration < bossAt
        ) {
            val heading = if (speed >= 80f) atan2(velocityY, velocityX) else atan2(lastAimDirectionY, lastAimDirectionX)
            sites = List(2) { side ->
                val angle = heading + if (side == 0) -1.0f else 1.0f
                // Anomalies move out from under obstacles, so their ring and beacons stay reachable.
                val (pointX, pointY) = clearPointOfInterestSite(coreX + cos(angle) * 560f, coreY + sin(angle) * 560f)
                SiteState(
                    windowSiteId(offerId, side), SiteKind.entries[(offerId + side) % ANOMALY_KIND_COUNT],
                    pointX, pointY,
                    scheduledAt + policy.offerLifetime,
                )
            }
            message = SiteNotice.TWO_ANOMALIES.english
            messageTime = 2.5f
        }
    }
    val active = sites.firstOrNull { it.active }
    if (active == null) {
        sites = sites.filter { elapsed < it.expiresAt }
        val selected = sites.firstOrNull {
            segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, it.x, it.y, 65f)
        } ?: return
        activateWindowSite(selected)
        return
    }
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
        enqueueDirectedReward(
            checkNotNull(content.sites.definition(trial.kind).reward.directed) { "An anomaly grants a directed reward" },
        )
        if (trial.kind == SiteKind.COLLAPSING_ORBIT) pendingCompletedOrbits++
        sites = emptyList() // consume before any choice can open
        message = SiteNotice.ANOMALY_RESOLVED.english
        messageTime = 2f
    } else sites = listOf(trial)
}

/** The Core entered an offered window site: its family's rules start it. */
internal fun MutableGameState.activateWindowSite(point: SiteState) {
    when (point.kind.family) {
        SiteFamily.ANOMALY -> activateAnomalySite(point)
        SiteFamily.PORTAL -> activatePortalSite(point)
        // Only the anomalies are offered until the site families land.
        SiteFamily.RACE, SiteFamily.CHALLENGE, SiteFamily.BOUNTY, SiteFamily.CACHE, SiteFamily.SECRET,
        SiteFamily.SIGNAL,
        -> Unit
    }
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
    sites = listOf(point.copy(active = true, remaining = content.sites.windows.trialDuration,
        foeIds = ids.toImmutableList()))
    message = content.sites.definition(point.kind).instruction
    messageTime = 4f
}

/** Kill hook of [onEnemyKilled]: a defeated foe of the active window site counts toward its trial. */
internal fun MutableGameState.recordSiteKill(enemy: Enemy) {
    val trial = sites.singleOrNull()?.takeIf { it.active && enemy.id in it.foeIds } ?: return
    // Called once by onEnemyKilled, whose dead guard also protects this counter.
    sites = listOf(trial.copy(count = min(3, trial.count + 1)))
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
