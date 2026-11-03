// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.TotemChestPolicy
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.DungeonPilotGateTest.Arm
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.max
import kotlin.test.Test

/**
 * The Totem chest economy probe (structures window i = 2,301..2,400, combined window 2,501..2,800; never the gate
 * population): Elite resonance gives every Elite-class kill a weapon level and drops a Key, and a Key opens a Totem
 * chest charge whose Amplify adds another, so a run that clears dungeons stacks both on the automatic amplification.
 * [DungeonCampaignPilot] seeks and fights dungeons over the whole run and spends its Keys at the chests within the
 * structure signal range (6,000 u); unbounded, the bot's own chest law flew it back across the map to every chest,
 * past the Elites it would otherwise fight. Each seed flies twice, with the shipped chests and with
 * `TotemChestPolicy(enabled = false)`, paired by seed. The arms load out as
 * [DungeonPilotGateTest]'s: FRESH (gate A's fresh tier-0 Orb, Arc Coil on even seeds and Flux Wake on odd ones) and
 * STRONG (gate B's full-Matrix tier-3 Pentagon on its Arc Coil). A run plays until it ends or 900 s.
 *
 * It prints every run and, per arm and window and pooled: Keys dropped and collected against chest charges found,
 * materialised, salvaged and opened; the weapon level at 360 s and 720 s (the last level for a run that ended earlier);
 * win, survival and clear deltas with paired bootstrap standard errors (policy.md: 20,000 resamples, RNG seed
 * 20261027, seeds resampled with both arms); and the share of the weapon levels gained by 720 s that came from
 * resonance, chests (and directed sites) and the automatic amplification.
 *
 * Target, stated before the first reading: over the pooled windows' seeds whose first dungeon attempt clears (the arms
 * play identically until the first chest appears, so the set is the same in both), the mean weapon level at 720 s with
 * chests is at most 1.25 times the mean without them in each arm, and the dungeon gate keeps its bands. A reading
 * above it retunes only chest content (a template's `totemChestChance`, the charge weights) on these windows.
 *
 * `KK_CHEST_PROBE=2301..2400,2501..2800` names the windows (comma-separated `first..last`); `KK_CHEST_ARMS=FRESH,STRONG`
 * picks arms; `KK_CHEST_KNOBS=chance.VAULT=0.6,minCharges.RELIQUARY=1,weights=55/33/12` overrides chest content (`;`
 * separates sets flown one after another); `KK_CHEST_REACH=Infinity` flies the bot's own chest law (any other value
 * bounds the chest trips in u). `--rerun` makes Gradle run it again with new variables; the lines land in the suite's
 * JUnit XML.
 */
class TotemChestEconomyProbeTest {
    @Test
    fun probeTheTotemChestEconomyOfDungeonClearingRuns() {
        val text = System.getenv("KK_CHEST_PROBE")?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val windows = text.split(',').map { probeWindow(it.trim()) }
        val arms = System.getenv("KK_CHEST_ARMS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.map { Arm.valueOf(it.uppercase()) } ?: Arm.entries
        val reach = System.getenv("KK_CHEST_REACH")?.trim()?.takeIf { it.isNotEmpty() }?.toFloat()
            ?: canonicalGameplayContent.structures.signalRange
        for (knobs in System.getenv("KK_CHEST_KNOBS").orEmpty().split(';')) {
            val chests = chestContent(knobs)
            val none = chests.copy(structures = chests.structures.copy(totemChest = TotemChestPolicy(enabled = false)))
            println("CHEST PROBE windows=$windows knobs='$knobs' reach=$reach ${chestContentLine(chests)}")
            for (arm in arms) {
                val byWindow = windows.map { window ->
                    populationOf(windowSeeds(window)) { seed -> ChestPair(flyChestCampaign(seed, arm, chests, reach), flyChestCampaign(seed, arm, none, reach)) }
                }
                byWindow.flatten().forEach { println("CHEST PROBE $arm ON ${it.on.line()}\nCHEST PROBE $arm OFF ${it.off.line()}") }
                windows.forEachIndexed { index, window -> printSummary("$arm window=$window", byWindow[index]) }
                if (windows.size > 1) printSummary("$arm pooled", byWindow.flatten())
            }
        }
    }
}

/** The weapon level and its sources so far: Elite resonance, chest Amplify, directed-site Amplify, automatic. */
internal data class LevelMark(val level: Int, val resonance: Int, val chest: Int, val site: Int, val automatic: Int) {
    fun line(): String = "$level(r$resonance/c$chest/s$site/a$automatic)"
}

/** One campaign run: how it ended, its weapon levels, its dungeon attempts and its Key and chest economy. */
internal class ChestRun(
    val seed: Int,
    val phase: GamePhase,
    val elapsed: Float,
    val weapon: WeaponId,
    val startLevel: Int,
    val at360: LevelMark,
    val at720: LevelMark,
    val final: LevelMark,
    val attempts: List<DungeonAttempt>,
    /** Elite-class kills: each paid resonance and dropped a Key. */
    val keyDrops: Int,
    val keysCollected: Long,
    val chargesFound: Int,
    val chargesMaterialised: Int,
    val chargesOpened: Int,
    val recalibrations: Int,
    val keysLeft: Int,
    val chargesWaiting: Int,
    /** Frames whose weapon level rose less than resonance and Amplify explain (0 unless the ledger is wrong). */
    val anomalies: Int,
) {
    val won: Boolean get() = phase == GamePhase.VICTORY
    val aliveAt720: Boolean get() = phase != GamePhase.GAME_OVER || elapsed >= 720f
    val survival: Float get() = if (phase == GamePhase.GAME_OVER) elapsed else max(elapsed, CHEST_RUN_CAP)
    val clears: Int get() = attempts.count { it.outcome == DungeonOutcome.CLEARED }
    val chargesSalvaged: Int get() = chargesFound - chargesMaterialised

    fun line(): String = "seed=$seed stop=${"%.2f".format(elapsed)} phase=$phase weapon=$weapon start=$startLevel " +
        "wl360=${at360.line()} wl720=${at720.line()} final=${final.line()} attempts=" +
        attempts.joinToString(",") { "${it.template}:${it.outcome}@${"%.0f".format(it.enteredAt)}" }.ifEmpty { "none" } +
        " keyDrops=$keyDrops keysCollected=$keysCollected found=$chargesFound materialised=$chargesMaterialised " +
        "opened=$chargesOpened recalibrated=$recalibrations keysLeft=$keysLeft waiting=$chargesWaiting anomalies=$anomalies"
}

/** One seed flown with the chests ([on]) and without them ([off]). */
internal class ChestPair(val on: ChestRun, val off: ChestRun) {
    /** The first attempt cleared (identical in both arms: they differ only once a chest appears). */
    val clearsFirst: Boolean get() = off.attempts.firstOrNull()?.outcome == DungeonOutcome.CLEARED

    /** True when the arms' first attempts differ, which the probe's pairing rules out. */
    val firstAttemptsDiffer: Boolean get() = on.attempts.firstOrNull()?.line() != off.attempts.firstOrNull()?.line()
}

/** The run cap of the population harness ([playGateRun]). */
private const val CHEST_RUN_CAP = 900f

/** One campaign run of [arm] on [seed] with [content], loaded out as [DungeonPilotGateTest.fly] loads out the arm. */
internal fun flyChestCampaign(seed: Int, arm: Arm, content: GameplayContentSnapshot, chestReach: Float = Float.POSITIVE_INFINITY): ChestRun {
    val profile = when (arm) {
        Arm.FRESH -> gateProfile(GateMatrix.NONE, tier = 0, weapon = if (seed % 2 == 0) WeaponId.ARC_COIL else WeaponId.FLUX_WAKE, content = content)
        Arm.STRONG -> gateProfile(GateMatrix.FULL, tier = 3, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, content = content)
    }
    val bot = when (arm) {
        Arm.FRESH -> NoPoiBot()
        Arm.STRONG -> NoPoiBot(avoidWeaponHarmony = true)
    }
    val state = startGateRun(seed, profile, content = content)
    val campaign = DungeonCampaignPilot(bot, chestReach)
    val ledger = ChestLedger(state)
    var at360: LevelMark? = null
    var at720: LevelMark? = null
    while (state.elapsed < CHEST_RUN_CAP && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
        ledger.before(state)
        if (state.phase == GamePhase.CHOICE) campaign.choose(state) else {
            campaign.control(state)
            state.update(0.02f)
        }
        ledger.after(state)
        campaign.observe(state)
        state.takeSoundCues()
        state.takeVisualFxCues()
        if (at360 == null && state.elapsed >= 360f) at360 = ledger.mark(state)
        if (at720 == null && state.elapsed >= 720f) at720 = ledger.mark(state)
    }
    campaign.observe(state)
    val final = ledger.mark(state)
    return ChestRun(
        seed, state.phase, state.elapsed, state.weapon, ledger.startLevel, at360 ?: final, at720 ?: final, final,
        campaign.attempts.toList(), state.eliteKills, state.keysCollected, campaign.chargesFound, ledger.materialised,
        ledger.opened, ledger.recalibrations, state.keys, waitingCharges(state), ledger.anomalies,
    )
}

/** Charges of every waiting Totem chest. */
private fun waitingCharges(s: MutableGameState): Int = s.structures.totemChests.sumOf { it.charges }

/**
 * Attributes every weapon level a frame adds: resonance from the frame's Elite-class kills, an Amplify from a Totem
 * choice (a chest's when a Key opened it, else a directed site's), the rest automatic. Only a chest spends a Key, so
 * Keys spent are charges opened.
 */
private class ChestLedger(start: MutableGameState) {
    val startLevel = start.weaponLevel
    var resonance = 0
    var chest = 0
    var site = 0
    var automatic = 0
    var materialised = 0
    var opened = 0
    var recalibrations = 0
    var anomalies = 0
    private var chestChoice = false
    private var level = 0
    private var elites = 0
    private var keys = 0
    private var waiting = 0
    private var choice: ChoiceType? = null

    fun before(s: MutableGameState) {
        level = s.weaponLevel
        elites = s.eliteKills
        keys = s.keys
        waiting = waitingCharges(s)
        choice = if (s.phase == GamePhase.CHOICE) s.activeChoiceType else null
    }

    fun after(s: MutableGameState) {
        val spent = max(0, keys - s.keys)
        opened += spent
        materialised += max(0, waitingCharges(s) - waiting + spent)
        if (spent > 0) chestChoice = true
        val resonant = (s.eliteKills - elites) * s.content.tempo.eliteResonanceLevels
        val amplify = if (choice == ChoiceType.TOTEM && s.weaponLevel > level) 1 else 0
        if (amplify > 0) if (chestChoice) chest++ else site++
        val weaponChoiceOpen = s.phase == GamePhase.CHOICE && s.activeChoiceType == ChoiceType.WEAPON
        if (choice == ChoiceType.TOTEM && chestChoice && weaponChoiceOpen) recalibrations++
        if ((choice == ChoiceType.TOTEM || choice == ChoiceType.WEAPON) && !weaponChoiceOpen) chestChoice = false
        val rest = s.weaponLevel - level - resonant - amplify
        if (rest < 0) anomalies++ else automatic += rest
        resonance += resonant
    }

    fun mark(s: MutableGameState) = LevelMark(s.weaponLevel, resonance, chest, site, automatic)
}

/** The shipped content with the chest knobs of [knobs] (`chance.TEMPLATE=…`, `minCharges.TEMPLATE=…`, `weights=a/b/c`). */
private fun chestContent(knobs: String): GameplayContentSnapshot {
    var structures = canonicalGameplayContent.structures
    knobs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
        val (name, value) = entry.split('=').map { it.trim() }
        val key = name.substringBefore('.')
        structures = if (key == "weights") {
            structures.copy(totemChest = structures.totemChest.copy(chargeWeights = value.split('/').map { it.toFloat() }.toImmutableList()))
        } else {
            val id = StructureTemplateId.valueOf(name.substringAfter('.').uppercase())
            structures.copy(templates = structures.templates.map { template ->
                when {
                    template.id != id -> template
                    key == "chance" -> template.copy(totemChestChance = value.toFloat())
                    key == "minCharges" -> template.copy(totemChestMinCharges = value.toInt())
                    else -> error("unknown chest knob $name")
                }
            }.toImmutableList())
        }
    }
    return canonicalGameplayContent.copy(structures = structures)
}

private fun chestContentLine(content: GameplayContentSnapshot): String =
    "weights=${content.structures.totemChest.chargeWeights.joinToString("/")} chances=" +
        content.structures.templates.joinToString(",") { "${it.id}:${it.totemChestChance}/min${it.totemChestMinCharges}" }

private fun List<ChestPair>.meanOf(value: (ChestPair) -> Double): Double = if (isEmpty()) Double.NaN else sumOf(value) / size

private fun printSummary(label: String, pairs: List<ChestPair>) {
    val clearing = pairs.filter { it.clearsFirst }
    println("CHEST PROBE SUMMARY $label n=${pairs.size} clearingFirst=${clearing.size} " +
        "firstAttemptsDiffer=${pairs.count { it.firstAttemptsDiffer }} anomalies=${pairs.sumOf { it.on.anomalies + it.off.anomalies }}")
    println("CHEST PROBE LEVELS $label clearing ${levelLine(clearing)}")
    println("CHEST PROBE LEVELS $label all ${levelLine(pairs)}")
    println("CHEST PROBE OUTCOMES $label ${outcomeLine(pairs)}")
    println("CHEST PROBE ECONOMY $label all ${economyLine(pairs)}")
    println("CHEST PROBE ECONOMY $label clearing ${economyLine(clearing)}")
    println("CHEST PROBE SOURCES $label clearing on ${sourceLine(clearing.map { it.on })} off ${sourceLine(clearing.map { it.off })}")
    println("CHEST PROBE SOURCES $label all on ${sourceLine(pairs.map { it.on })} off ${sourceLine(pairs.map { it.off })}")
}

/** Mean weapon levels at 360 and 720 s with and without chests, their paired ratios and the ratio of levels gained. */
private fun levelLine(pairs: List<ChestPair>): String {
    if (pairs.isEmpty()) return "n=0"
    val parts = ArrayList<String>()
    for ((name, mark) in listOf<Pair<String, (ChestRun) -> LevelMark>>("360" to { it.at360 }, "720" to { it.at720 })) {
        parts += "mean$name=${"%.3f".format(pairs.meanOf { mark(it.on).level.toDouble() })}/${"%.3f".format(pairs.meanOf { mark(it.off).level.toDouble() })}"
        parts += bootstrapLine("ratio$name", pairs) { sample ->
            sample.meanOf { mark(it.on).level.toDouble() } / sample.meanOf { mark(it.off).level.toDouble() }
        }
        parts += bootstrapLine("gainRatio$name", pairs) { sample ->
            sample.meanOf { (mark(it.on).level - it.on.startLevel).toDouble() } / sample.meanOf { (mark(it.off).level - it.off.startLevel).toDouble() }
        }
    }
    parts += "median720=${pairs.map { it.on.at720.level.toFloat() }.finiteMedian()}/${pairs.map { it.off.at720.level.toFloat() }.finiteMedian()}"
    return "n=${pairs.size} " + parts.joinToString(" ")
}

/** Win, alive-at-720 s, survival and dungeon clear readings with chests and without, and their paired deltas. */
private fun outcomeLine(pairs: List<ChestPair>): String {
    val metrics = listOf<Pair<String, (ChestRun) -> Double>>(
        "win" to { if (it.won) 1.0 else 0.0 },
        "alive720" to { if (it.aliveAt720) 1.0 else 0.0 },
        "survival" to { it.survival.toDouble() },
        "attempts" to { it.attempts.size.toDouble() },
        "clears" to { it.clears.toDouble() },
    )
    val parts = metrics.map { (name, value) ->
        "$name=${"%.3f".format(pairs.meanOf { value(it.on) })}/${"%.3f".format(pairs.meanOf { value(it.off) })} " +
            bootstrapLine("d$name", pairs) { sample -> sample.meanOf { value(it.on) } - sample.meanOf { value(it.off) } }
    }
    fun clearShare(sample: List<ChestPair>, run: (ChestPair) -> ChestRun): Double =
        sample.sumOf { run(it).clears }.toDouble() / max(1, sample.sumOf { run(it).attempts.size })
    val share = "clearShare=${"%.3f".format(clearShare(pairs) { it.on })}/${"%.3f".format(clearShare(pairs) { it.off })} " +
        bootstrapLine("dclearShare", pairs) { sample -> clearShare(sample) { it.on } - clearShare(sample) { it.off } }
    return "n=${pairs.size} " + (parts + share).joinToString(" ")
}

/** Mean Keys and chest charges per run with chests, and the no-chest arm's Keys. */
private fun economyLine(pairs: List<ChestPair>): String {
    if (pairs.isEmpty()) return "n=0"
    fun mean(value: (ChestRun) -> Number) = "%.3f".format(pairs.meanOf { value(it.on).toDouble() })
    val found = pairs.sumOf { it.on.chargesFound }
    val opened = pairs.sumOf { it.on.chargesOpened }
    val collected = pairs.sumOf { it.on.keysCollected }
    return "n=${pairs.size} keyDrops=${mean { it.keyDrops }} keysCollected=${mean { it.keysCollected }} " +
        "found=${mean { it.chargesFound }} materialised=${mean { it.chargesMaterialised }} salvaged=${mean { it.chargesSalvaged }} " +
        "opened=${mean { it.chargesOpened }} recalibrated=${mean { it.recalibrations }} keysLeft=${mean { it.keysLeft }} " +
        "waiting=${mean { it.chargesWaiting }} openedOfFound=$opened/$found openedOfCollected=$opened/$collected " +
        "runsOpening=${pairs.count { it.on.chargesOpened > 0 }} " +
        "off.keysCollected=${"%.3f".format(pairs.meanOf { it.off.keysCollected.toDouble() })} " +
        "off.keysLeft=${"%.3f".format(pairs.meanOf { it.off.keysLeft.toDouble() })}"
}

/** Shares of the weapon levels gained by 720 s from resonance, chests, directed sites and the automatic amplification. */
private fun sourceLine(runs: List<ChestRun>): String {
    val resonance = runs.sumOf { it.at720.resonance }
    val chest = runs.sumOf { it.at720.chest }
    val site = runs.sumOf { it.at720.site }
    val automatic = runs.sumOf { it.at720.automatic }
    val total = max(1, resonance + chest + site + automatic)
    fun share(value: Int) = "%.3f".format(value.toDouble() / total)
    return "gained=$total resonance=${share(resonance)} chest=${share(chest)} site=${share(site)} automatic=${share(automatic)} " +
        "perRun=${"%.2f".format(resonance.toDouble() / max(1, runs.size))}/${"%.2f".format(chest.toDouble() / max(1, runs.size))}/" +
        "${"%.2f".format(site.toDouble() / max(1, runs.size))}/${"%.2f".format(automatic.toDouble() / max(1, runs.size))}"
}
