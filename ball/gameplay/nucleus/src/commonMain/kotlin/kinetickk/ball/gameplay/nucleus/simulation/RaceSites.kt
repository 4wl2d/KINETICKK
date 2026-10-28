// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.SitePoint
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Course seeds of gate races: `worldHash(runSeed, offerId, side, RACE_COURSE_SALT)` (registered SITES salt). */
internal const val RACE_COURSE_SALT: Long = 0x517E_C0A5_0000_0001L

/** A running race is lost once the Core is farther than this from its next gate. */
internal const val RACE_LEASH: Float = 2_400f

/** Fx colour of a passed gate's burst (bone). */
private const val GATE_BURST_COLOR: Int = 1

/**
 * The running race the Core starts by entering [site]: 5..7 gates swept around the start in a loop that never closes
 * on it, each gate moved outward from the start until clear of obstacles. The course comes from a hash of the run seed
 * and the window side, so it draws from no stream; the par is the course's length at the steady speed of the policy's
 * cursor reach plus a turn allowance per gate after the first, and the limit is the par's share, at least the policy's
 * minimum and at most the window's trial.
 */
internal fun MutableGameState.startedRace(site: SiteState): SiteState {
    val race = content.sites.race
    val random = HashRandom(worldHash(runSeed, site.offerId, site.side, RACE_COURSE_SALT))
    val gates = race.minGates + random.nextIndex(race.maxGates - race.minGates + 1)
    val heading = if (speed >= 80f) atan2(velocityY, velocityX) else atan2(lastAimDirectionY, lastAimDirectionX)
    val turn = if (random.nextFloat() < 0.5f) 1f else -1f
    val sweep = TAU * race.loopShare / gates
    val course = ArrayList<SitePoint>(gates)
    var path = 0f
    var fromX = 0f
    var fromY = 0f
    for (index in 0 until gates) {
        val angle = heading + turn * sweep * index + (2f * random.nextFloat() - 1f) * race.angleJitter
        val radius = race.minRadius + random.nextFloat() * (race.maxRadius - race.minRadius)
        val gate = nudgedFrom(
            site.x, site.y, site.x + cos(angle) * radius, site.y + sin(angle) * radius,
            race.gateClearance, race.nudgeStep, race.nudgeAttempts,
        )
        val dx = gate.x - site.x
        val dy = gate.y - site.y
        path += length(dx - fromX, dy - fromY)
        course += SitePoint(dx, dy)
        fromX = dx
        fromY = dy
    }
    val par = path / steadyCursorSpeed(race.parReach) + race.turnAllowanceSeconds * (gates - 1)
    val limit = min(max(par * race.limitFactor, race.minimumLimit), content.sites.windows.trialDuration)
    return site.copy(
        active = true, remaining = limit, clock = 0f, step = 0, course = course.toImmutableList(), par = par, limit = limit,
    )
}

/**
 * Moves a point at ([x], [y]) away from ([centerX], [centerY]) in [step]s until a circle of [clearance] there is clear
 * of obstacles and walls (at most [attempts] moves), so a course mark stays within its bound of the centre.
 */
internal fun MutableGameState.nudgedFrom(
    centerX: Float,
    centerY: Float,
    x: Float,
    y: Float,
    clearance: Float,
    step: Float,
    attempts: Int,
): SteeringVector {
    var pointX = x
    var pointY = y
    var moves = 0
    while (moves < attempts && isSpawnPointBlocked(pointX, pointY, clearance)) {
        val dx = pointX - centerX
        val dy = pointY - centerY
        val distance = max(1f, length(dx, dy))
        pointX += dx / distance * step
        pointY += dy / distance * step
        moves++
    }
    return steeringVector(pointX, pointY)
}

/**
 * One step of the running race [site] (its clock and countdown already exclude this step): lost on time out or once
 * the Core strays from the next gate; a swept pass through the next gate (only the next one counts) advances it, and
 * passing the last finishes it.
 */
internal fun MutableGameState.advanceRaceSite(site: SiteState, delta: Float) {
    val trial = site.copy(clock = site.clock + delta, remaining = max(0f, site.remaining - delta))
    val gate = trial.course[trial.step]
    val gateX = trial.x + gate.dx
    val gateY = trial.y + gate.dy
    if (trial.remaining <= 0f || distanceSquared(coreX, coreY, gateX, gateY) > square(RACE_LEASH)) {
        sites = emptyList()
        message = SiteNotice.RACE_LOST.english
        messageTime = 1.6f
        return
    }
    if (!segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, gateX, gateY, content.sites.race.gateRadius)) {
        sites = listOf(trial)
        return
    }
    val passed = trial.copy(step = trial.step + 1)
    emitSound(GameplayAudioCue.PICKUP)
    burst(gateX, gateY, 10, GATE_BURST_COLOR)
    if (passed.step < passed.course.size) sites = listOf(passed) else finishRace(passed, gateX, gateY)
}

/**
 * The last gate was passed at ([gateX], [gateY]): the best medal the clock earned picks the reward (gold, silver,
 * bronze, or the finish reward past bronze), granted at the last gate, and the race counts as discovered.
 */
private fun MutableGameState.finishRace(race: SiteState, gateX: Float, gateY: Float) {
    val policy = content.sites.race
    val (reward, notice) = when (raceMedal(race)) {
        3 -> policy.gold to SiteNotice.RACE_GOLD
        2 -> policy.silver to SiteNotice.RACE_SILVER
        1 -> policy.bronze to SiteNotice.RACE_BRONZE
        else -> policy.finish to SiteNotice.RACE_FINISHED
    }
    sites = emptyList() // consume before any choice can open
    message = notice.english
    messageTime = 2f
    discoverWorld(WorldDiscovery.GATE_RACE)
    // Granted after the notice, so a full directed queue's REWARD SALVAGED is the one shown.
    grantSiteReward(reward, gateX, gateY, worldHash(runSeed, race.offerId, race.side, REWARD_SCATTER_SALT))
}

/**
 * The best medal a race's clock still allows (at the finish: the medal it earned): 3 gold, 2 silver, 1 bronze, 0 none
 * (also without a par).
 */
internal fun MutableGameState.raceMedal(site: SiteState): Int {
    val race = content.sites.race
    return when {
        site.par <= 0f -> 0
        site.clock <= site.par * race.goldFactor -> 3
        site.clock <= site.par * race.silverFactor -> 2
        site.clock <= site.par * race.bronzeFactor -> 1
        else -> 0
    }
}
