// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteReward
import kinetickk.ball.gameplay.nucleus.model.CircuitBeacons
import kinetickk.ball.gameplay.nucleus.model.SitePoint
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.render.SiteMarkProjection
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.TrialClock
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.ball.gameplay.nucleus.render.TrialReward
import kinetickk.ball.gameplay.nucleus.render.TrialValue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableListOfSize
import kinetickk.foundation.collections.mapToImmutableList
import kotlin.math.ceil
import kotlin.math.min

/** Defenders a sealed anomaly's vault holds (SiteSystem completes it at this count). */
private const val VAULT_DEFENDERS = 3

/** The trial panel's meter has 1..24 cells. */
private const val MAX_TRIAL_SEGMENTS = 24

/** A bounty or sigil duel shows its foe's integrity as three cells. */
private const val DUEL_SEGMENTS = 3

/**
 * The window sites, then the secret sigil if one lies in the world. Sites change a few times per second at most: the
 * same sites at the same time, or projections that already carry every derived value, keep the previous immutable
 * list; a course whose marks did not move keeps its previous list too.
 */
internal fun MutableGameState.siteProjections(
    reusable: ImmutableList<SiteProjection>?,
    identitySource: MutableGameState?,
): ImmutableList<SiteProjection> {
    val sigil = secretSigil
    val count = sites.size + if (sigil != null) 1 else 0
    if (reusable != null) {
        // A duel's progress is its foe's integrity, which the site state does not hold.
        if (sites === identitySource?.sites && sigil === identitySource.secretSigil &&
            elapsed.sameBitsAs(identitySource.elapsed) && !readsFoeIntegrity()
        ) return reusable
        if (reusable.size == count) {
            var same = true
            for (index in 0 until count) {
                if (!siteProjectionMatches(reusable[index], projectedSite(index))) {
                    same = false
                    break
                }
            }
            if (same) return reusable
        }
    }
    return immutableListOfSize(count) { index ->
        siteProjection(projectedSite(index), reusable?.getOrNull(index)?.course)
    }
}

/**
 * The trial panel's facts: the active window site or summoned sigil, else the first other engagement that reports one
 * (a dungeon, then a dimension objective); an equal projection keeps the previous instance.
 */
internal fun MutableGameState.trialProjection(previous: TrialProjection?): TrialProjection? {
    val trial = (activeWindowSite() ?: secretSigil?.takeIf { it.active })?.let { siteTrialProjection(it) }
        ?: structureTrialProjection() ?: dimensionTrialProjection() ?: return null
    return if (trial == previous) previous else trial
}

/**
 * The trial panel of an active site, or null for kinds that never run one (caches, eggs, rifts). Anomalies keep the
 * 0.5.0 panel: the circuit's three beacons, the vault's defenders and the orbit's seconds, with their directed reward.
 */
internal fun MutableGameState.siteTrialProjection(site: SiteState): TrialProjection? {
    val policy = content.sites
    val challenges = policy.challenges
    val reward = trialReward(policy.definition(site.kind).reward)
    return when (site.kind) {
        SiteKind.RESONANT_CIRCUIT -> siteTrial(site, TrialClock.COUNTDOWN, CircuitBeacons.size, TrialValue.COUNT,
            site.step.toFloat(), CircuitBeacons.size.toFloat(), reward)
        SiteKind.SEALED_ANOMALY -> siteTrial(site, TrialClock.COUNTDOWN, site.foeIds.size, TrialValue.COUNT,
            site.count.toFloat(), VAULT_DEFENDERS.toFloat(), reward)
        SiteKind.COLLAPSING_ORBIT -> secondsTrial(site, site.meter, policy.windows.orbitRequiredSeconds, reward)
        SiteKind.GATE_RACE -> siteTrial(site, TrialClock.ELAPSED_TENTHS, site.course.size, TrialValue.COUNT,
            site.step.toFloat(), site.course.size.toFloat(), TrialReward.MEDAL, raceMedal(site))
        SiteKind.BRAKELESS_TRIAL -> secondsTrial(site, site.meter, challenges.brakelessRequired, reward)
        SiteKind.UNTOUCHED_TRIAL -> secondsTrial(site, site.meter, challenges.untouchedRequired, reward)
        SiteKind.VELOCITY_TRIAL -> secondsTrial(site, site.meter, challenges.velocityRequired, reward)
        SiteKind.RAM_TRIAL -> siteTrial(site, TrialClock.COUNTDOWN, challenges.ramRequired, TrialValue.COUNT,
            site.count.toFloat(), challenges.ramRequired.toFloat(), reward)
        SiteKind.SLALOM_TRIAL -> siteTrial(site, TrialClock.COUNTDOWN, challenges.slalomPoles, TrialValue.COUNT,
            site.step.toFloat(), challenges.slalomPoles.toFloat(), reward)
        // The bounty's own payout is its mini-boss's Key and Relic drops; the site adds Matter.
        SiteKind.BOUNTY_ARENA -> siteTrial(site, TrialClock.NONE, DUEL_SEGMENTS, TrialValue.NONE, 0f, 0f, TrialReward.KEY_AND_RELIC)
        SiteKind.FAR_SIGIL -> siteTrial(site, TrialClock.NONE, DUEL_SEGMENTS, TrialValue.NONE, 0f, 0f, TrialReward.UNKNOWN)
        SiteKind.DRIFT_CACHE, SiteKind.LOST_CURSOR, SiteKind.GOLDEN_DRIFTER, SiteKind.QUIET_SIGNAL, SiteKind.ZENITH_RIFT,
        SiteKind.NADIR_RIFT,
        -> null
    }
}

private fun MutableGameState.secondsTrial(site: SiteState, earned: Float, required: Float, reward: TrialReward): TrialProjection =
    siteTrial(site, TrialClock.COUNTDOWN, ceil(required).toInt(), TrialValue.SECONDS, earned, required, reward)

private fun MutableGameState.siteTrial(
    site: SiteState,
    clock: TrialClock,
    segments: Int,
    value: TrialValue,
    done: Float,
    required: Float,
    reward: TrialReward,
    medal: Int = 0,
): TrialProjection {
    val definition = content.sites.definition(site.kind)
    return TrialProjection(
        family = site.kind.family,
        kind = site.kind,
        title = definition.name,
        rules = definition.instruction,
        clock = clock,
        seconds = when (clock) {
            TrialClock.COUNTDOWN -> siteRemaining(site)
            TrialClock.ELAPSED_TENTHS -> site.clock
            TrialClock.NONE -> 0f
        },
        segments = segments.coerceIn(1, MAX_TRIAL_SEGMENTS),
        progress = siteProgress(site),
        value = value,
        done = done,
        required = required,
        reward = reward,
        medal = medal,
    )
}

/** The panel's reward label: the directed reward when there is one, else the bundle's leading part. */
private fun trialReward(reward: SiteReward): TrialReward = when (reward.directed) {
    DirectedReward.WEAPON -> TrialReward.WEAPON
    DirectedReward.RELIC -> TrialReward.RELIC
    DirectedReward.ITEM_AND_REPAIR -> TrialReward.ITEM_AND_REPAIR
    null -> when {
        reward.itemOffers > 0 || reward.itemOfferFloor != null -> TrialReward.ITEM
        reward.keys > 0 || reward.relics > 0 -> TrialReward.KEY_AND_RELIC
        reward.matter > 0 -> TrialReward.MATTER
        else -> TrialReward.UNKNOWN
    }
}

/** Window sites first, then the sigil (only asked for an index below the projected count). */
private fun MutableGameState.projectedSite(index: Int): SiteState =
    if (index < sites.size) sites[index] else checkNotNull(secretSigil)

private fun MutableGameState.siteProjection(site: SiteState, previousCourse: ImmutableList<SiteMarkProjection>?): SiteProjection {
    val definition = content.sites.definition(site.kind)
    return SiteProjection(
        kind = site.kind,
        name = definition.name,
        x = site.x,
        y = site.y,
        active = site.active,
        remaining = siteRemaining(site),
        step = site.step,
        progress = siteProgress(site),
        foeIds = site.foeIds,
        warningRemaining = site.warningRemaining,
        volleyAngle = site.volleyAngle,
        course = courseProjection(site.course, previousCourse),
        radius = siteRadius(site),
        sides = site.sides,
        clock = site.clock,
        medal = siteMedal(site),
        activationRadius = definition.activationRadius,
        aimDx = siteAimDx(site),
        aimDy = siteAimDy(site),
    )
}

private fun courseProjection(
    course: ImmutableList<SitePoint>,
    previous: ImmutableList<SiteMarkProjection>?,
): ImmutableList<SiteMarkProjection> {
    if (course.isEmpty()) return immutableListOf()
    if (previous != null && courseMatches(previous, course)) return previous
    return course.mapToImmutableList { mark -> SiteMarkProjection(mark.dx, mark.dy) }
}

private fun courseMatches(projection: ImmutableList<SiteMarkProjection>, course: ImmutableList<SitePoint>): Boolean {
    if (projection.size != course.size) return false
    for (index in course.indices) {
        val mark = projection[index]
        val point = course[index]
        if (!mark.dx.sameBitsAs(point.dx) || !mark.dy.sameBitsAs(point.dy)) return false
    }
    return true
}

/** An offer counts down to its close; a timed trial its seconds left; an untimed duel shows none. */
private fun MutableGameState.siteRemaining(site: SiteState): Float = when {
    !site.active -> (site.expiresAt - elapsed).coerceAtLeast(0f)
    site.kind == SiteKind.GATE_RACE || content.sites.definition(site.kind).duration > 0f -> site.remaining
    else -> 0f
}

private fun MutableGameState.siteProgress(site: SiteState): Float {
    val challenges = content.sites.challenges
    return when (site.kind) {
        SiteKind.RESONANT_CIRCUIT -> site.step / 3f
        SiteKind.SEALED_ANOMALY -> site.count / 3f
        SiteKind.COLLAPSING_ORBIT -> site.meter / content.sites.windows.orbitRequiredSeconds
        SiteKind.GATE_RACE -> share(site.step.toFloat(), site.course.size.toFloat())
        SiteKind.BRAKELESS_TRIAL -> share(site.meter, challenges.brakelessRequired)
        SiteKind.UNTOUCHED_TRIAL -> share(site.meter, challenges.untouchedRequired)
        SiteKind.VELOCITY_TRIAL -> share(site.meter, challenges.velocityRequired)
        SiteKind.RAM_TRIAL -> share(site.count.toFloat(), challenges.ramRequired.toFloat())
        SiteKind.SLALOM_TRIAL -> share(site.step.toFloat(), challenges.slalomPoles.toFloat())
        SiteKind.BOUNTY_ARENA, SiteKind.FAR_SIGIL -> if (site.active) foeIntegrityLost(site) else 0f
        SiteKind.DRIFT_CACHE, SiteKind.LOST_CURSOR, SiteKind.GOLDEN_DRIFTER, SiteKind.QUIET_SIGNAL, SiteKind.ZENITH_RIFT,
        SiteKind.NADIR_RIFT,
        -> 0f
    }
}

/** [done] of [required] in 0..1; a goal of nothing is already met. */
private fun share(done: Float, required: Float): Float = if (required > 0f) min(1f, done / required).coerceAtLeast(0f) else 1f

/** Share of the duel foe's integrity already taken (1 once the kill hook counted it). */
private fun MutableGameState.foeIntegrityLost(site: SiteState): Float {
    if (site.count >= 1) return 1f
    val id = site.foeIds.firstOrNull() ?: return 0f
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.id != id) continue
        return if (enemy.maxHp > 0f) (1f - enemy.hp / enemy.maxHp).coerceIn(0f, 1f) else 0f
    }
    return 0f
}

/** True while an active duel's progress reads an enemy's integrity. */
private fun MutableGameState.readsFoeIntegrity(): Boolean {
    for (index in sites.indices) if (sites[index].active && sites[index].kind == SiteKind.BOUNTY_ARENA) return true
    return secretSigil?.active == true
}

/** Ring or arena radius around an active challenge or bounty (the brakeless ring shrinks with its clock). */
private fun MutableGameState.siteRadius(site: SiteState): Float {
    if (!site.active) return 0f
    val challenges = content.sites.challenges
    return when (site.kind) {
        SiteKind.BRAKELESS_TRIAL -> challenges.brakelessStartRadius +
            (challenges.brakelessEndRadius - challenges.brakelessStartRadius) * min(1f, site.clock / challenges.brakelessDuration)
        SiteKind.RAM_TRIAL -> challenges.ramClamp
        SiteKind.UNTOUCHED_TRIAL -> challenges.untouchedRadius
        SiteKind.VELOCITY_TRIAL -> challenges.velocityZoneRadius
        SiteKind.BOUNTY_ARENA -> content.sites.bounty.arenaRadius
        SiteKind.RESONANT_CIRCUIT, SiteKind.SEALED_ANOMALY, SiteKind.COLLAPSING_ORBIT, SiteKind.GATE_RACE,
        SiteKind.SLALOM_TRIAL, SiteKind.DRIFT_CACHE, SiteKind.LOST_CURSOR, SiteKind.GOLDEN_DRIFTER, SiteKind.QUIET_SIGNAL,
        SiteKind.FAR_SIGIL, SiteKind.ZENITH_RIFT, SiteKind.NADIR_RIFT,
        -> 0f
    }
}

private fun MutableGameState.siteMedal(site: SiteState): Int =
    if (site.active && site.kind == SiteKind.GATE_RACE) raceMedal(site) else 0

/** A running untouched trial's volley aim point from the centre; no other site aims. */
private fun siteAimDx(site: SiteState): Float = if (aims(site)) site.aimX - site.x else 0f

private fun siteAimDy(site: SiteState): Float = if (aims(site)) site.aimY - site.y else 0f

private fun aims(site: SiteState): Boolean = site.active && site.kind == SiteKind.UNTOUCHED_TRIAL

private fun MutableGameState.siteProjectionMatches(projection: SiteProjection, site: SiteState): Boolean {
    val definition = content.sites.definition(site.kind)
    return projection.kind == site.kind &&
        projection.name == definition.name &&
        projection.x.sameBitsAs(site.x) &&
        projection.y.sameBitsAs(site.y) &&
        projection.active == site.active &&
        projection.remaining.sameBitsAs(siteRemaining(site)) &&
        projection.step == site.step &&
        projection.progress.sameBitsAs(siteProgress(site)) &&
        (projection.foeIds === site.foeIds || projection.foeIds == site.foeIds) &&
        projection.warningRemaining.sameBitsAs(site.warningRemaining) &&
        projection.volleyAngle.sameBitsAs(site.volleyAngle) &&
        courseMatches(projection.course, site.course) &&
        projection.radius.sameBitsAs(siteRadius(site)) &&
        projection.sides == site.sides &&
        projection.clock.sameBitsAs(site.clock) &&
        projection.medal == siteMedal(site) &&
        projection.activationRadius.sameBitsAs(definition.activationRadius) &&
        projection.reveal.sameBitsAs(Float.POSITIVE_INFINITY) &&
        projection.aimDx.sameBitsAs(siteAimDx(site)) &&
        projection.aimDy.sameBitsAs(siteAimDy(site))
}
