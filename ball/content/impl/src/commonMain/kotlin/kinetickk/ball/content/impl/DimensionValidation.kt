// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.DimensionPolicy
import kinetickk.ball.content.api.DimensionRewardKind
import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.RunBoonDefinition
import kinetickk.ball.content.api.RunBoonKind
import kinetickk.ball.content.api.SiteWindowPolicy
import kinetickk.ball.content.api.WorldLayer

/**
 * Cross-checks of the dimension content against the roster, the POI windows, the run tempo and the boons (each
 * definition validates itself on construction). Runs after the roster check, so enemies follow the EnemyType order.
 */
internal fun validateDimensions(data: ContentBootstrapData) {
    require(data.boons.size <= ContentBounds.MAX_BOON_DEFINITIONS) {
        "boons size ${data.boons.size} exceeds bootstrap bound ${ContentBounds.MAX_BOON_DEFINITIONS}"
    }
    require(data.boons.map { it.id }.toSet().size == data.boons.size) { "Boon ids must be unique" }
    validateDimensionNatives(data.enemies)
    validateObjectiveBosses(data.dimensions, data.enemies)
    validateBoonHands(data.dimensions, data.boons)
    // A disabled policy opens no rift, so its schedule cannot fail a run.
    if (data.dimensions.enabled) validateRiftSchedule(data.dimensions, data.sites.windows, data.tempo.bossAtSeconds)
}

/**
 * Every dimension opens with one of its own kinds (living there and not in open space) from the first second at tier 0,
 * so a visit never meets only visitors from realspace. The roster check already opens every habitat with some kind.
 */
private fun validateDimensionNatives(enemies: List<EnemyDefinition>) {
    WorldLayer.entries.filter { it != WorldLayer.BASE }.forEach { layer ->
        require(enemies.any { enemy ->
            !enemy.isScheduled && !enemy.livesIn(EnemyHabitat.SPACE) && enemy.weightIn(layer.habitat) > 0f &&
                enemy.timeGateSeconds <= 0f && enemy.minimumRebirth == 0
        }) { "$layer must open with a native kind from the first second at tier 0" }
    }
}

/** A boss objective fights a mini-boss in Zenith and Nadir and a secret boss in the Archive, always one of that layer. */
private fun validateObjectiveBosses(policy: DimensionPolicy, enemies: List<EnemyDefinition>) {
    policy.definitions.flatMap { it.objectives }.map { it.objective }.forEach { objective ->
        val rank = objective.requiredBossRank()
        val bossType = objective.boss
        require((bossType == null) == (rank == null)) { "Objective $objective must name a boss exactly when it is a duel" }
        if (bossType == null) return@forEach
        val boss = enemies[bossType.ordinal]
        require(boss.rank == rank) { "Objective $objective boss $bossType must be a $rank" }
        require(boss.livesIn(objective.layer.habitat)) { "Objective $objective boss $bossType must live in ${objective.layer}" }
    }
}

private fun DimensionObjective.requiredBossRank(): EnemyRank? = when (this) {
    DimensionObjective.ASCENT, DimensionObjective.HARVEST -> null
    DimensionObjective.JUDGEMENT, DimensionObjective.PYRE -> EnemyRank.MINI_BOSS
    DimensionObjective.ANTECEDENT -> EnemyRank.SECRET_BOSS
}

/** A Blessing or Pact offer deals distinct cards, so the catalog holds at least as many boons of that kind as it deals. */
private fun validateBoonHands(policy: DimensionPolicy, boons: List<RunBoonDefinition>) {
    policy.definitions.forEach { definition ->
        val reward = definition.reward
        val kind = when (reward.kind) {
            DimensionRewardKind.BLESSING_OFFER -> RunBoonKind.BLESSING
            DimensionRewardKind.PACT_OFFER -> RunBoonKind.PACT
            DimensionRewardKind.NONE, DimensionRewardKind.ARCHIVE_TROVE -> return@forEach
        }
        val hand = maxOf(reward.cardsOnSuccess, reward.cardsOnFailure)
        require(boons.count { it.kind == kind } >= hand) { "${definition.layer} deals $hand ${kind.name.lowercase()} cards" }
    }
}

/**
 * Some POI window from [DimensionPolicy.firstPortalWindow] on can host a rift whose visit ends before the boss, and the
 * glitch rift into the Archive can still appear after the first window opens.
 */
private fun validateRiftSchedule(policy: DimensionPolicy, windows: SiteWindowPolicy, bossAtSeconds: Float) {
    val riftWindow = generateSequence(0) { it + 1 }
        .map { index -> index to windows.firstOfferAt + index * windows.offerInterval }
        .takeWhile { (_, scheduledAt) -> scheduledAt <= windows.lastOfferAt }
        .any { (index, scheduledAt) -> policy.windowMayHostRift(index, scheduledAt, windows.offerLifetime, bossAtSeconds) }
    require(riftWindow) { "No POI window can host a rift whose visit ends before the boss" }
    require(policy.latestGlitchRiftAt(bossAtSeconds) >= windows.firstOfferAt) {
        "The Archive's glitch rift could not appear before the boss"
    }
}
