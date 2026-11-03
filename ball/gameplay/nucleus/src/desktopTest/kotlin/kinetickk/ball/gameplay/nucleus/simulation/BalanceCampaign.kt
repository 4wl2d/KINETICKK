// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.rank
import kinetickk.foundation.collections.ImmutableList
import kotlin.math.roundToInt

/*
 * The sweep's campaign mode (`-Psweep.mode=campaign`): whole profiles played from a fresh start, the economy target of
 * docs/balance/progression.md ("a full Matrix, one maxed character and one maxed weapon take about 40-70 runs of normal
 * play"). Each campaign is the Orb with the bot's loadout and the reference armory: after every run it banks the run's
 * Matter, then buys the cheapest purchase it can afford (a Matrix rank it may buy, the Orb's next Refit level or the
 * loadout weapon's next Honing level) until none is affordable. It pushes its Rebirth rank (one tier above its highest
 * clear); after a lost push it farms the tier below once, then pushes again. Campaign c plays the window's seeds in order from position c × (window / campaigns),
 * wrapping, so campaigns meet different seeds at the same stage.
 */

/** Runs after which a campaign that has not finished stops counting. */
private const val CAMPAIGN_MAX_RUNS: Int = 160

private class Campaign(val content: GameplayContentSnapshot) {
    val weapon: WeaponId = botLoadout(BotWeaponOrder, ReferenceArmory)
    val ranks = IntArray(content.matrixNodes.size)
    var refit = 0
    var honing = 0
    var matter = 0L
    var lifetime = 0L
    var highestCleared = -1
    val firstClearRun = IntArray(ContentBounds.MAX_REBIRTH_LEVEL + 1) { -1 }
    val matterByTier = Array(ContentBounds.MAX_REBIRTH_LEVEL + 1) { mutableListOf<Long>() }
    var finishedAt = -1
    private var farmNext = false

    val rank: Int get() = RebirthProgress(highestCleared = highestCleared).rank

    /** The tier of the next run: the rank, or the tier below it right after a lost push. */
    val tier: Int get() = if (farmNext) (rank - 1).coerceAtLeast(0) else rank

    val finished: Boolean
        get() = refit >= content.upgrades.maxLevel && honing >= content.upgrades.maxLevel &&
            content.matrixNodes.indices.all { ranks[it] >= content.matrixNodes[it].maxRanks }

    fun profile(): GameplayProfileSnapshot = GameplayProfileSnapshot(
        preferences = PlayerPreferences(simulationSpeed = 1f),
        economy = PlayerEconomy(matter, lifetime),
        loadout = PlayerLoadout(CoreShape.ORB, weapon, gateOwnedWeapons(weapon)),
        matrix = MatrixProgress(ranks.toList()),
        collection = PlayerCollection(),
        rebirthProgress = RebirthProgress(level = tier, highestCleared = highestCleared),
        upgrades = gateUpgrades(CoreShape.ORB, refit, setOf(weapon), honing),
    )

    /** The cheapest purchase the profile may make now, as its price and the action that makes it; null for none. */
    private fun cheapest(): Pair<Long, () -> Unit>? {
        val options = mutableListOf<Pair<Long, () -> Unit>>()
        val owned = content.matrixNodes.map { node -> ranks[node.id.ordinal] > 0 }
        content.matrixNodes.forEachIndexed { index, node ->
            val open = node.prerequisites.isEmpty() || node.prerequisites.any { owned[it.ordinal] }
            if (ranks[index] < node.maxRanks && open && rank >= node.minimumRebirth) {
                options += node.cost(ranks[index]).toLong() to { ranks[index]++ }
            }
        }
        val policy = content.upgrades
        if (refit < policy.maxLevel) options += policy.nextCost(content.coreShape(CoreShape.ORB).upgradeBaseCost, refit) to { refit++ }
        if (honing < policy.maxLevel) options += policy.nextCost(content.weapon(weapon).upgradeBaseCost, honing) to { honing++ }
        return options.minByOrNull { it.first }
    }

    fun spend() {
        while (true) {
            val (price, buy) = cheapest() ?: return
            if (price > matter) return
            matter -= price
            buy()
        }
    }

    fun bank(run: Int, played: Int, outcome: SweepRun) {
        farmNext = played == rank && !outcome.won && played > 0
        matter += outcome.matter
        lifetime += outcome.matter
        matterByTier[played] += outcome.matter
        if (outcome.won && played > highestCleared) {
            highestCleared = played
            if (firstClearRun[played] < 0) firstClearRun[played] = run
        }
    }
}

/** Plays [count] campaigns over [window] on [threads] workers and prints their progression. */
internal fun runCampaigns(content: GameplayContentSnapshot, window: IntRange, threads: Int, count: Int) {
    val seeds = windowSeeds(window)
    val stride = seeds.size / count
    val started = System.nanoTime()
    val campaigns = sweepPool((0 until count).toList(), threads) { index ->
        val campaign = Campaign(content)
        for (run in 1..CAMPAIGN_MAX_RUNS) {
            val seed = seeds[(index * stride + run - 1) % seeds.size]
            val tier = campaign.tier
            campaign.bank(run, tier, sweepRun(seed, campaign.profile(), emptyList(), content))
            campaign.spend()
            if (campaign.finished) {
                campaign.finishedAt = run
                break
            }
        }
        println("campaign $index finished=${campaign.finishedAt} highestCleared=${campaign.highestCleared} " +
            "firstClears=${campaign.firstClearRun.joinToString(",")} matrix=${campaign.ranks.sum()} refit=${campaign.refit} " +
            "honing=${campaign.honing} lifetime=${campaign.lifetime}")
        campaign
    }
    val finished = campaigns.map { if (it.finishedAt > 0) it.finishedAt else Int.MAX_VALUE }.sorted()
    fun quantile(q: Double): String = finished[((finished.size - 1) * q).toInt()].let { if (it == Int.MAX_VALUE) ">$CAMPAIGN_MAX_RUNS" else "$it" }
    println("CAMPAIGN runs to full Matrix + Orb Refit ${content.upgrades.maxLevel} + Honing ${content.upgrades.maxLevel}: " +
        "p25=${quantile(0.25)} median=${quantile(0.5)} p75=${quantile(0.75)} (of ${campaigns.size})")
    println("tier\tmedianFirstClearRun\tcampaignsCleared\tmedianMatterPerRun\trunsPlayed")
    for (tier in 0..ContentBounds.MAX_REBIRTH_LEVEL) {
        val clears = campaigns.map { it.firstClearRun[tier] }.filter { it > 0 }.sorted()
        val matter = campaigns.flatMap { it.matterByTier[tier] }.sorted()
        println("$tier\t${clears.getOrNull(clears.size / 2) ?: "-"}\t${clears.size}\t${matter.getOrNull(matter.size / 2) ?: "-"}\t${matter.size}")
    }
    println("balanceSweep campaigns in ${(System.nanoTime() - started) / 1_000_000_000} s")
}

/** The Refit, Honing, weapon-level and boss-cap knobs of [sweepContent]. */
internal fun sweepUpgradeKnob(content: GameplayContentSnapshot, name: String, value: Float): GameplayContentSnapshot = when (name) {
    "honing" -> content.copy(upgrades = content.upgrades.copy(weaponPowerGrowth = value))
    "refit" -> content.copy(upgrades = content.upgrades.copy(characterPowerGrowth = value))
    "refitIntegrity" -> content.copy(upgrades = content.upgrades.copy(characterIntegrityPerLevel = value))
    "weaponCap" -> content.copy(tempo = content.tempo.copy(maximumWeaponLevel = value.toInt()))
    "costScale" -> content.copy(
        matrixNodes = ImmutableList.copyOf(content.matrixNodes.map { node ->
            node.copy(costs = ImmutableList.copyOf(node.costs.map { cost -> (cost * value).roundToInt() }))
        }),
        coreShapes = ImmutableList.copyOf(content.coreShapes.map { it.copy(upgradeBaseCost = (it.upgradeBaseCost * value).roundToInt()) }),
        weapons = ImmutableList.copyOf(content.weapons.map { it.withUpgradeBaseCost((it.upgradeBaseCost * value).roundToInt()) }),
    )
    "bossCapShrink" -> content.copy(enemies = ImmutableList.copyOf(content.enemies.map { enemy ->
        enemy.encounter?.let { enemy.copy(encounter = it.copy(hitCapTierShrink = value)) } ?: enemy
    }))
    else -> error("unknown sweep knob $name (health, damage, matter, honing, refit, refitIntegrity, weaponCap, costScale, bossCapShrink)")
}

private fun WeaponDefinition.withUpgradeBaseCost(base: Int): WeaponDefinition = WeaponDefinition(
    id = id, name = name, description = description, family = family, element = element, tags = tags, key = key,
    signature = signature, scaling = scaling, cadence = cadence, permanentUnlockCost = permanentUnlockCost,
    minimumRebirth = minimumRebirth, masteryForms = masteryForms, signatureText = signatureText, upgradeBaseCost = base,
)
