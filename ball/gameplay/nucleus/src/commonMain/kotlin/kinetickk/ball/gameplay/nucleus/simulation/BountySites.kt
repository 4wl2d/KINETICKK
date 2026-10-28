// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.cos
import kotlin.math.sin

/**
 * The running bounty the Core starts by entering [site], or null when it refuses to start (its encounter has no room),
 * leaving the offer in place. The policy's mini-boss waits its spawn offset beyond the centre, on the line from the Core
 * through the site, as an encounter the site owns: kept inside the arena, leash-exempt, with its own profile aspect,
 * freeing hard-cap room by evicting the oldest ordinary enemy, drawn from no run stream. The bounty is untimed; its clock
 * counts up.
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
        remaining = content.sites.definition(site.kind).duration,
        clock = 0f,
        count = 0,
        foeIds = immutableListOf(boss),
    )
}

/** Past this distance from its centre the Core has left the bounty's arena (the arena plus its exit margin). */
internal fun MutableGameState.bountyExitRadius(): Float = content.sites.bounty.arenaRadius + content.sites.bounty.exitMargin

/**
 * One step of the running bounty [site]: the clock counts up; once the kill hook counted the boss the bounty is claimed
 * (the boss fell while the Core was inside, at the previous step's position); otherwise leaving the arena past
 * [bountyExitRadius], or a boss gone without a kill, abandons it.
 */
internal fun MutableGameState.advanceBountySite(site: SiteState, delta: Float) {
    val trial = site.copy(clock = site.clock + delta)
    when {
        trial.count >= 1 -> claimBounty(trial)
        distanceSquared(coreX, coreY, trial.x, trial.y) > square(bountyExitRadius()) || !bossAlive(trial) -> abandonBounty(trial)
        else -> sites = listOf(trial)
    }
}

/** True while the bounty's boss is alive. */
private fun MutableGameState.bossAlive(trial: SiteState): Boolean {
    val boss = trial.foeIds.firstOrNull() ?: return false
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.id == boss) return enemy.alive
    }
    return false
}

/**
 * The boss fell: its own kill already paid its Key, Relic and Elite kill; the bounty adds its definition's reward and the
 * bounty's discovery. The encounter ends, and guards the boss called in stay on as ordinary enemies.
 */
private fun MutableGameState.claimBounty(trial: SiteState) {
    endEncounter(siteEncounterTag(trial), despawn = false)
    sites = emptyList() // consume before any choice can open
    message = SiteNotice.BOUNTY_CLAIMED.english
    messageTime = 2f
    discoverWorld(WorldDiscovery.BOUNTY)
    // Granted after the notice, so a salvaged reward's notice is the one shown.
    grantSiteReward(
        content.sites.definition(trial.kind).reward, trial.x, trial.y,
        worldHash(runSeed, trial.offerId, trial.side, REWARD_SCATTER_SALT),
    )
}

/** The Core left: the boss and its guards leave without a kill, and nothing is granted. */
private fun MutableGameState.abandonBounty(trial: SiteState) {
    endEncounter(siteEncounterTag(trial), despawn = true)
    sites = emptyList()
    message = SiteNotice.BOUNTY_ABANDONED.english
    messageTime = 1.6f
}
