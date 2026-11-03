// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.sites

import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The running bounty the Core starts by entering [site], or null when it refuses to start (its encounter has no room),
 * leaving the offer in place. The policy's mini-boss waits its spawn offset beyond the centre, on the line from the Core
 * through the site, as an encounter the site owns: kept inside the arena, leash-exempt, with its own profile aspect,
 * freeing hard-cap room by evicting the oldest ordinary enemy, drawn from no run stream. The bounty runs for the policy's
 * time limit; its clock counts up.
 */
internal fun MutableGameState.startedBounty(site: SiteState): SiteState? {
    val policy = content.sites.bounty
    val tag = siteEncounterTag(site)
    val awayX = site.x - coreX
    val awayY = site.y - coreY
    val away = length(awayX, awayY)
    val unitX: Float
    val unitY: Float
    if (away > 0.001f) {
        unitX = awayX / away
        unitY = awayY / away
    } else {
        // A Core right on the centre has no line through it: the boss waits ahead along the flight.
        val heading = travelHeading()
        unitX = cos(heading)
        unitY = sin(heading)
    }
    val spawned = spawnEncounter(
        EncounterRequest(
            tag = tag,
            centerX = site.x,
            centerY = site.y,
            areaRadius = policy.arenaRadius,
            members = listOf(policy.bossType),
            integrityShare = policy.bossIntegrityShare,
            abandonDistance = bountyExitRadius(),
            evictOrdinary = true,
            // The trial's instruction is the run message; the HUD's boss block names the target.
            announce = false,
            positions = floatArrayOf(site.x + unitX * policy.bossSpawnOffset, site.y + unitY * policy.bossSpawnOffset),
        ),
    )
    if (!spawned) return null
    val boss = encounter(tag)?.bossId ?: -1
    check(boss >= 0) { "A bounty encounter holds its mini-boss" }
    return site.copy(
        active = true,
        remaining = policy.timeLimit,
        clock = 0f,
        count = 0,
        foeIds = immutableListOf(boss),
    )
}

/** Past this distance from its centre the Core has left the bounty's arena (the arena plus its exit margin). */
internal fun MutableGameState.bountyExitRadius(): Float = content.sites.bounty.arenaRadius + content.sites.bounty.exitMargin

/**
 * One step of the running bounty [site]: the clock counts up and the time limit down; once the kill hook counted the
 * boss the bounty is claimed (the boss fell while the Core was inside, at the previous step's position); otherwise
 * leaving the arena past [bountyExitRadius], or a boss gone without a kill, abandons it, and a limit run out with the
 * boss still standing lets it escape.
 */
internal fun MutableGameState.advanceBountySite(site: SiteState, delta: Float) {
    val trial = site.copy(clock = site.clock + delta, remaining = max(0f, site.remaining - delta))
    // A boss brought to zero integrity before the site step stays until the step's kill sweep counts it, so the next
    // step claims it: it is neither gone nor escaping meanwhile.
    val boss = enemyById(trial.foeIds.firstOrNull() ?: -1)
    when {
        trial.count >= 1 -> claimBounty(trial)
        boss == null || boss.dead || distanceSquared(coreX, coreY, trial.x, trial.y) > square(bountyExitRadius()) ->
            abandonBounty(trial)
        trial.remaining <= 0f && boss.alive -> endBounty(trial, SiteNotice.BOUNTY_ESCAPED)
        else -> sites = listOf(trial)
    }
}

/**
 * True while a duel's boss (the site's first foe) is alive or awaits its kill: one brought to zero integrity before
 * the site step stays until the step's kill sweep counts it, so the next step claims it.
 */
internal fun MutableGameState.siteBossAlive(site: SiteState): Boolean =
    enemyById(site.foeIds.firstOrNull() ?: -1)?.dead == false

/**
 * The boss fell: its own kill already paid its Key, Relic and Elite kill; the bounty adds its definition's reward and the
 * bounty's discovery. The encounter ends, and guards the boss called in stay on as ordinary enemies.
 */
private fun MutableGameState.claimBounty(trial: SiteState) {
    endEncounter(siteEncounterTag(trial), despawn = false)
    completeWindowSite(
        trial, SiteNotice.BOUNTY_CLAIMED, content.sites.definition(trial.kind).reward, trial.x, trial.y,
        WorldDiscovery.BOUNTY,
    )
}

/** The Core left: the boss and its guards leave without a kill, and nothing is granted. */
private fun MutableGameState.abandonBounty(trial: SiteState) = endBounty(trial, SiteNotice.BOUNTY_ABANDONED)

/** The bounty ends unclaimed with [notice]: the boss and its guards leave without a kill, and nothing is granted. */
private fun MutableGameState.endBounty(trial: SiteState, notice: SiteNotice) {
    endEncounter(siteEncounterTag(trial), despawn = true)
    loseWindowSite(notice)
}
