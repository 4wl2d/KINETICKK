// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ChallengePolicy
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.SitePoint
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Course seeds of slaloms: `worldHash(runSeed, offerId, side, SLALOM_COURSE_SALT)` (registered SITES salt). */
internal const val SLALOM_COURSE_SALT: Long = 0x517E_C0A5_0000_0002L

/** A running challenge is lost once the Core is farther than this from its centre (a slalom: from its next pole). */
internal const val CHALLENGE_LEASH: Float = 2_400f

/** Untouched volley shots fly at most this long. */
private const val UNTOUCHED_SHOT_MAX_LIFE: Float = 2f

/** Untouched volley shots fly this far past the aim point (within [UNTOUCHED_SHOT_MAX_LIFE]). */
private const val UNTOUCHED_SHOT_OVERSHOOT: Float = 400f

/** Untouched volley lanes sit this far either side of the middle lane. */
private const val UNTOUCHED_LANE_SPACING: Float = 60f

/** Fx colour of a passed pole's burst (bone). */
private const val POLE_BURST_COLOR: Int = 1

/**
 * The running challenge the Core starts by entering [site], or null when it refuses to start (a Ram rite without room
 * for its marked foes), leaving the offer in place. Every challenge counts down its definition's duration; the brakeless
 * ring and the untouched and velocity zones are centred on the site, the Ram rite spawns its foes as an encounter the
 * site owns, the untouched trial remembers the damage taken so far and the slalom lays its poles.
 */
internal fun MutableGameState.startedChallenge(site: SiteState): SiteState? {
    val policy = content.sites.challenges
    val started = site.copy(active = true, remaining = content.sites.definition(site.kind).duration, clock = 0f)
    return when (site.kind) {
        SiteKind.RAM_TRIAL -> spawnRamFoes(site, policy)?.let { foes -> started.copy(foeIds = foes) }
        SiteKind.UNTOUCHED_TRIAL -> started.copy(
            hurtBaseline = damageTaken + damageAbsorbed,
            volleyClock = policy.untouchedVolleyInterval,
            warningRemaining = 0f,
        )
        SiteKind.SLALOM_TRIAL -> laidSlalom(started, policy)
        SiteKind.BRAKELESS_TRIAL, SiteKind.VELOCITY_TRIAL -> started
        else -> error("${site.kind} is not a challenge")
    }
}

/**
 * Spawns the Ram rite's marked Drifters on a ring around [site] as an encounter it owns (no stream draw), or returns
 * null without spawning anything when the hard enemy cap lacks room for all of them (no ordinary enemy is evicted).
 */
private fun MutableGameState.spawnRamFoes(site: SiteState, policy: ChallengePolicy): ImmutableList<Int>? {
    val tag = siteEncounterTag(site)
    val spawned = spawnEncounter(
        EncounterRequest(
            tag = tag,
            centerX = site.x,
            centerY = site.y,
            areaRadius = policy.ramClamp,
            members = List(policy.ramFoes) { EnemyType.DRIFTER },
            placement = EncounterPlacement.RING,
            ringRadius = policy.ramRingRadius,
            integrityShare = policy.ramFoeHealthShare,
            abandonDistance = CHALLENGE_LEASH,
            evictOrdinary = false,
            announce = false,
        ),
    )
    if (!spawned) return null
    val ids = ArrayList<Int>(policy.ramFoes)
    for (index in enemies.indices) if (enemies[index].ownerTag == tag) ids += enemies[index].id
    return ids.toImmutableList()
}

/** The encounter tag of a window site's own foes (its window id, C03). */
internal fun siteEncounterTag(site: SiteState): Long = encounterTag(EncounterOwner.SITE, site.id)

/**
 * The slalom's poles: the first pole side comes from a hash of the run seed and the window side (alternating after it),
 * and the course runs along the flight's heading, or 0.35 rad to either side when that keeps every pole clear of
 * obstacles (the heading again when none does). Pole k lies `first + spacing·k` along the axis and the pole offset to
 * its side; its bit in [SiteState.sides] is set when it is passed on its left.
 */
private fun MutableGameState.laidSlalom(started: SiteState, policy: ChallengePolicy): SiteState {
    val random = HashRandom(worldHash(runSeed, started.offerId, started.side, SLALOM_COURSE_SALT))
    val firstSide = if (random.nextFloat() < 0.5f) 1f else -1f
    val heading = if (speed >= 80f) atan2(velocityY, velocityX) else atan2(lastAimDirectionY, lastAimDirectionX)
    var axis = heading
    for (candidate in 0 until 3) {
        val turn = when (candidate) {
            0 -> 0f
            1 -> policy.slalomAxisTurn
            else -> -policy.slalomAxisTurn
        }
        if (slalomClear(started, heading + turn, firstSide, policy)) {
            axis = heading + turn
            break
        }
    }
    val unitX = cos(axis)
    val unitY = sin(axis)
    var sides = 0
    val course = ArrayList<SitePoint>(policy.slalomPoles)
    for (pole in 0 until policy.slalomPoles) {
        val side = slalomSide(firstSide, pole)
        if (side > 0f) sides = sides or (1 shl pole)
        course += slalomPole(unitX, unitY, pole, side, policy)
    }
    return started.copy(step = 0, course = course.toImmutableList(), sides = sides, axis = axis)
}

/** True when every pole of a slalom along [axis] from [site] is clear of obstacles. */
private fun MutableGameState.slalomClear(site: SiteState, axis: Float, firstSide: Float, policy: ChallengePolicy): Boolean {
    val unitX = cos(axis)
    val unitY = sin(axis)
    for (pole in 0 until policy.slalomPoles) {
        val mark = slalomPole(unitX, unitY, pole, slalomSide(firstSide, pole), policy)
        if (isSpawnPointBlocked(site.x + mark.dx, site.y + mark.dy, policy.slalomPoleClearance)) return false
    }
    return true
}

/** +1 (left of the axis) or −1: the first pole's side, alternating. */
private fun slalomSide(firstSide: Float, pole: Int): Float = if (pole % 2 == 0) firstSide else -firstSide

/** Distance along the axis of slalom pole [pole]. */
private fun slalomAlong(pole: Int, policy: ChallengePolicy): Float = policy.slalomFirstAlong + policy.slalomSpacing * pole

private fun slalomPole(unitX: Float, unitY: Float, pole: Int, side: Float, policy: ChallengePolicy): SitePoint {
    val along = slalomAlong(pole, policy)
    val lateral = side * policy.slalomPoleOffset
    // The axis normal (−unitY, unitX) is the left of the course.
    return SitePoint(unitX * along - unitY * lateral, unitY * along + unitX * lateral)
}

/** Radius of the brakeless ring [clock] seconds into the trial: it shrinks from its start to its end radius. */
internal fun ChallengePolicy.brakelessRing(clock: Float): Float =
    brakelessStartRadius + (brakelessEndRadius - brakelessStartRadius) * min(1f, clock / brakelessDuration)

/** How a challenge step ends. */
private enum class ChallengeOutcome { ONGOING, COMPLETED, FAILED }

/**
 * One step of the running challenge [site]: lost on time out or once the Core strays [CHALLENGE_LEASH] from its centre
 * (a slalom: from its next pole); otherwise its kind's rule earns, counts or breaks. A completed challenge grants its
 * definition's reward and discovers the trials; a lost or failed one grants nothing. Either way the Ram rite's surviving
 * foes stay on as ordinary enemies.
 */
internal fun MutableGameState.advanceChallengeSite(site: SiteState, delta: Float) {
    val trial = site.copy(clock = site.clock + delta, remaining = max(0f, site.remaining - delta))
    if (trial.remaining <= 0f || challengeLeashBroken(trial)) {
        endChallenge(trial, completed = false)
        return
    }
    val policy = content.sites.challenges
    val distance = length(coreX - trial.x, coreY - trial.y)
    var next = trial
    val outcome = when (trial.kind) {
        SiteKind.BRAKELESS_TRIAL -> {
            if (braking && trial.clock > policy.brakelessGrace) {
                ChallengeOutcome.FAILED
            } else {
                val earning = distance <= policy.brakelessRing(trial.clock) && speed >= policy.brakelessMinSpeed
                next = trial.earned(earning, delta, policy.brakelessRequired)
                if (next.meter >= policy.brakelessRequired) ChallengeOutcome.COMPLETED else ChallengeOutcome.ONGOING
            }
        }
        SiteKind.RAM_TRIAL -> when {
            trial.count >= policy.ramRequired -> ChallengeOutcome.COMPLETED
            // A foe killed any other way is gone without counting: the rite fails once too few foes remain.
            trial.count + liveFoes(trial) < policy.ramRequired -> ChallengeOutcome.FAILED
            else -> ChallengeOutcome.ONGOING
        }
        SiteKind.UNTOUCHED_TRIAL -> {
            if (damageTaken + damageAbsorbed > trial.hurtBaseline) {
                ChallengeOutcome.FAILED
            } else {
                next = untouchedVolley(trial.earned(distance <= policy.untouchedRadius, delta, policy.untouchedRequired), delta, policy)
                if (next.meter >= policy.untouchedRequired) ChallengeOutcome.COMPLETED else ChallengeOutcome.ONGOING
            }
        }
        SiteKind.VELOCITY_TRIAL -> {
            val earning = speed >= policy.velocityMinSpeed && distance <= policy.velocityZoneRadius
            next = trial.earned(earning, delta, policy.velocityRequired)
            if (next.meter >= policy.velocityRequired) ChallengeOutcome.COMPLETED else ChallengeOutcome.ONGOING
        }
        SiteKind.SLALOM_TRIAL -> when (slalomCrossing(trial, policy)) {
            0 -> ChallengeOutcome.ONGOING
            1 -> {
                next = trial.copy(step = trial.step + 1)
                val pole = trial.course[trial.step]
                emitSound(GameplayAudioCue.PICKUP)
                burst(trial.x + pole.dx, trial.y + pole.dy, 10, POLE_BURST_COLOR)
                if (next.step >= next.course.size) ChallengeOutcome.COMPLETED else ChallengeOutcome.ONGOING
            }
            else -> ChallengeOutcome.FAILED
        }
        else -> error("${trial.kind} is not a challenge")
    }
    when (outcome) {
        ChallengeOutcome.ONGOING -> sites = listOf(next)
        ChallengeOutcome.COMPLETED -> endChallenge(next, completed = true)
        ChallengeOutcome.FAILED -> endChallenge(next, completed = false)
    }
}

/** This trial with [delta] more seconds earned when [earning], never past [required]. */
private fun SiteState.earned(earning: Boolean, delta: Float, required: Float): SiteState =
    if (earning) copy(meter = min(required, meter + delta)) else this

private fun MutableGameState.challengeLeashBroken(trial: SiteState): Boolean {
    var markX = trial.x
    var markY = trial.y
    if (trial.kind == SiteKind.SLALOM_TRIAL && trial.step < trial.course.size) {
        markX += trial.course[trial.step].dx
        markY += trial.course[trial.step].dy
    }
    return distanceSquared(coreX, coreY, markX, markY) > square(CHALLENGE_LEASH)
}

/** Marked foes of [trial] still alive. */
private fun MutableGameState.liveFoes(trial: SiteState): Int {
    var live = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.alive && trial.isFoe(enemy.id)) live++
    }
    return live
}

/** Index scan without boxing the id. */
private fun SiteState.isFoe(id: Int): Boolean {
    for (index in foeIds.indices) if (foeIds[index] == id) return true
    return false
}

/**
 * The untouched volley: the clock runs down while no volley is telegraphed; then a warning aims at the Core, and when it
 * runs out three shots leave the centre along the aim, one middle lane and one either side.
 */
private fun MutableGameState.untouchedVolley(trial: SiteState, delta: Float, policy: ChallengePolicy): SiteState {
    if (trial.warningRemaining > 0f) {
        val warning = max(0f, trial.warningRemaining - delta)
        if (warning <= 0f) fireUntouchedVolley(trial, policy)
        return trial.copy(warningRemaining = warning)
    }
    val clock = trial.volleyClock - delta
    if (clock > 0f) return trial.copy(volleyClock = clock)
    return trial.copy(
        volleyClock = policy.untouchedVolleyInterval,
        warningRemaining = policy.untouchedWarning,
        volleyAngle = atan2(coreY - trial.y, coreX - trial.x),
        aimX = coreX,
        aimY = coreY,
    )
}

private fun MutableGameState.fireUntouchedVolley(trial: SiteState, policy: ChallengePolicy) {
    val dx = cos(trial.volleyAngle)
    val dy = sin(trial.volleyAngle)
    val speed = policy.untouchedShotSpeed
    val life = min(UNTOUCHED_SHOT_MAX_LIFE, (length(trial.aimX - trial.x, trial.aimY - trial.y) + UNTOUCHED_SHOT_OVERSHOOT) / speed)
    val damage = hostileDamage(null, policy.untouchedShotDamage)
    repeat(3) { lane ->
        val lateral = (lane - 1) * UNTOUCHED_LANE_SPACING
        addProjectile(
            Projectile(
                trial.x - dy * lateral, trial.y + dx * lateral, dx * speed, dy * speed,
                radius = 7f, life = life, hostile = true, damage = damage,
            ),
        )
    }
}

/**
 * The slalom's next pole as the Core crosses its line across the axis this step (moving forward along the axis): 0 no
 * crossing (or one outside the course's lateral reach), 1 passed on its flagged side with the Core clear of the pole,
 * −1 crossed on the wrong side.
 */
private fun MutableGameState.slalomCrossing(trial: SiteState, policy: ChallengePolicy): Int {
    val unitX = cos(trial.axis)
    val unitY = sin(trial.axis)
    val along = slalomAlong(trial.step, policy)
    val fromAlong = (previousCoreX - trial.x) * unitX + (previousCoreY - trial.y) * unitY
    val toAlong = (coreX - trial.x) * unitX + (coreY - trial.y) * unitY
    if (!(fromAlong < along && toAlong >= along)) return 0
    val fromLateral = -(previousCoreX - trial.x) * unitY + (previousCoreY - trial.y) * unitX
    val toLateral = -(coreX - trial.x) * unitY + (coreY - trial.y) * unitX
    val share = (along - fromAlong) / (toAlong - fromAlong)
    val lateral = fromLateral + (toLateral - fromLateral) * share
    if (abs(lateral) > policy.slalomMaxLateral) return 0
    val side = if (trial.sides and (1 shl trial.step) != 0) 1f else -1f
    return if (side * lateral >= policy.slalomPoleOffset + coreRadius) 1 else -1
}

/**
 * Ends the running challenge [trial]: completed, its definition's reward (at the centre, a slalom's at its last pole)
 * and the trials' discovery; otherwise nothing. The Ram rite's encounter ends either way, its surviving foes turning
 * into ordinary enemies.
 */
private fun MutableGameState.endChallenge(trial: SiteState, completed: Boolean) {
    if (trial.kind == SiteKind.RAM_TRIAL) endEncounter(siteEncounterTag(trial), despawn = false)
    sites = emptyList() // consume before any choice can open
    if (!completed) {
        message = SiteNotice.CHALLENGE_FAILED.english
        messageTime = 1.6f
        return
    }
    message = SiteNotice.CHALLENGE_COMPLETE.english
    messageTime = 2f
    discoverWorld(WorldDiscovery.TRIALS)
    val lastMark = trial.course.lastOrNull()
    // Granted after the notice, so a full directed queue's REWARD SALVAGED is the one shown.
    grantSiteReward(
        content.sites.definition(trial.kind).reward,
        trial.x + (lastMark?.dx ?: 0f),
        trial.y + (lastMark?.dy ?: 0f),
        worldHash(runSeed, trial.offerId, trial.side, REWARD_SCATTER_SALT),
    )
}

/**
 * True when [enemy]'s killing blow was a Dash: a Dash strike (a character's Dash strike included) or an impact during
 * the Dash phase.
 */
internal fun MutableGameState.isDashKill(enemy: Enemy): Boolean {
    val source = enemy.status[StatusCell.LAST_SOURCE].toInt() - 1
    return source == HitSource.DASH.ordinal || (source == HitSource.IMPACT.ordinal && dashPhaseTime > 0f)
}

/** The Ram rite's foes outlive a site cleared at the Architect's arrival: they turn into ordinary enemies. */
internal fun MutableGameState.releaseSiteFoes(site: SiteState) {
    if (site.active && site.kind == SiteKind.RAM_TRIAL) endEncounter(siteEncounterTag(site), despawn = false)
}
