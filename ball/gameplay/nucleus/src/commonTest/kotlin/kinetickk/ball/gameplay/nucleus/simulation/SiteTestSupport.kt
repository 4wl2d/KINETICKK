// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList

/** Content whose window weights are exactly [weights] (every other kind 0); the rest of the site policy is shipped. */
internal fun GameplayContentSnapshot.withWindowWeights(weights: Map<SiteKind, Float>): GameplayContentSnapshot =
    copy(
        sites = sites.copy(
            definitions = sites.definitions.map { it.copy(windowWeight = weights[it.kind] ?: 0f) }.toImmutableList(),
        ),
    )

/** Only the three anomalies take window weight, so every drawn kind is one the window can start. */
internal fun GameplayContentSnapshot.withAnomalyWindowsOnly(): GameplayContentSnapshot =
    withWindowWeights(SiteKind.entries.filter { it.family == SiteFamily.ANOMALY }.associateWith { 1f })

/** Fixed placement population for lifecycle tests; rarity and seed eligibility are covered by ExplorationRarityTest. */
internal val ambientMechanicsContent: GameplayContentSnapshot = canonicalGameplayContent.let { content ->
    content.copy(sites = content.sites.copy(ambient = content.sites.ambient.copy(
        rules = content.sites.ambient.rules.map { rule ->
            rule.copy(runChance = 1f, maxChance = when (rule.kind) {
                SiteKind.DRIFT_CACHE -> 0.18f
                SiteKind.LOST_CURSOR -> 0.10f
                SiteKind.GOLDEN_DRIFTER -> 0.08f
                SiteKind.QUIET_SIGNAL -> 0.30f
                else -> error("Not an ambient test kind")
            })
        }.toImmutableList(),
    )))
}

/** The design window weights of the site spec (§2.1): anomalies 1, gate race 1.4, each challenge 0.36, bounty 1. */
internal val DesignWindowWeights: Map<SiteKind, Float> = SiteKind.entries.mapNotNull { kind ->
    when (kind.family) {
        SiteFamily.ANOMALY, SiteFamily.BOUNTY -> kind to 1f
        SiteFamily.RACE -> kind to 1.4f
        SiteFamily.CHALLENGE -> kind to 0.36f
        SiteFamily.CACHE, SiteFamily.SECRET, SiteFamily.PORTAL, SiteFamily.SIGNAL -> null
    }
}.toMap()

/**
 * Offers window [index] at its scheduled second from the Core's current pose (nothing offered before, no swept
 * movement, so nothing activates) and returns the offered pair.
 */
internal fun MutableGameState.offerWindowForTesting(index: Int): List<SiteState> {
    val windows = content.sites.windows
    sites = emptyList()
    nextSiteWindowIndex = index
    elapsed = windows.firstOfferAt + index * windows.offerInterval
    previousCoreX = coreX
    previousCoreY = coreY
    updateSites(0f)
    return sites
}
