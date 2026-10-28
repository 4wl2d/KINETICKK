// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.RunModifierEffect
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test

/**
 * The VARIETY calibration probe (plan §8.2-§8.3, spec §11): runs only when `KK_VARIETY_PROBE` names a window of probe
 * indices (`first..last` of `i * 7_919 + 13`; VARIETY calibrates on 1,601..1,900 and never on gate seeds) and prints each
 * arm's measures with bootstrap standard errors (20,000 resamples of whole runs, paired by seed against the base arm,
 * RNG seed 20261027). `KK_VARIETY_ARMS` picks arms (BASE, OFF, each omen by its [OmenId] name, OMENS for all twelve,
 * SEEKER, DIRECTOR (the budget harness on the window's first 60 seeds; `KK_DIRECTOR_LIFE` sets its enemy life); BASE and
 * OMENS by default); `KK_VARIETY_KNOBS` overrides magnitudes (`name=value`, comma-separated):
 * `<OMEN>.gain<N>` / `<OMEN>.cost<N>` set the Nth effect's value (a stat amount, a Matter scale, a reroll count) and
 * `<OMEN>.cost<N>.<health|speed|incomingDamage|spawnRate|eliteRate>` one hostile scale, `<OMEN>.addGain.<ItemEffect>` appends a
 * stat gain; `budget.<PERSONALITY>`;
 * `affix.baseChance`, `affix.chancePerTier`, `champion.baseChance`, `champion.integrityScale`; `signal.<SignalTuning
 * field>` for the prices and odds; `meteor.coreDamage`, `ion.coreDamage`, `meteor.enemyDamageScale`,
 * `ion.enemyDamageScale`, `gravity.coreAcceleration`, `solar.coolingPenalty`, `solar.weaponBonus`. Several sets
 * separated by `;` run one after the other.
 */
class RunVarietyCalibrationProbeTest {
    @Test
    fun printsTheVarietyArmsOnTheNamedWindow() {
        val window = System.getenv("KK_VARIETY_PROBE")?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val (first, last) = window.split("..").map { it.trim().toInt() }.let { it[0] to (it.getOrNull(1) ?: (it[0] + 299)) }
        val seeds = varietyWindowSeeds(first, last)
        val arms = (System.getenv("KK_VARIETY_ARMS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("BASE", "OMENS"))
            .flatMap { if (it == "OMENS") OmenId.entries.map { omen -> omen.name } else listOf(it) }
        // Knob sets that change only omens share one base (No omen never reads them).
        val bases = HashMap<GameplayContentSnapshot, List<VarietyRun>>()
        for (knobs in System.getenv("KK_VARIETY_KNOBS").orEmpty().split(';')) {
            val content = varietyKnobbed(canonicalGameplayContent, knobs)
            println("VARIETY-PROBE window=$first..$last knobs='$knobs'")
            val base = bases.getOrPut(content.copy(variety = content.variety.copy(omens = canonicalGameplayContent.variety.omens))) {
                timed("BASE") { populationOf(seeds) { flyVarietyRun(it, content) } }
            }
            println("VARIETY-PROBE BASE ${armLine(base)}")
            for (arm in arms) when (arm) {
                "BASE" -> Unit
                "OFF" -> {
                    val off = timed(arm) { populationOf(seeds) { flyVarietyRun(it, content.withoutRunVariety()) } }
                    println("VARIETY-PROBE OFF ${armLine(off)} ${pairedLine(base, off)}")
                }
                "SEEKER" -> {
                    val seeker = timed(arm) { populationOf(seeds) { flyVarietyRun(it, content, seek = true) } }
                    println("VARIETY-PROBE SEEKER ${armLine(seeker)} ${pairedLine(base, seeker)} ${signalLine(seeker)}")
                }
                "DIRECTOR" -> directorLines(seeds.take(60), content)
                else -> {
                    val omen = OmenId.valueOf(arm)
                    val runs = timed(arm) { populationOf(seeds) { flyVarietyRun(it, content, omen) } }
                    println("VARIETY-PROBE $arm ${armLine(runs)} ${pairedLine(base, runs)}")
                }
            }
        }
    }

    private fun <T> timed(name: String, work: () -> T): T {
        val started = System.nanoTime()
        return work().also { println("VARIETY-PROBE $name wall=${"%.1f".format((System.nanoTime() - started) / 1e9)}s") }
    }

    private fun directorLines(seeds: List<Int>, content: GameplayContentSnapshot) {
        val life = System.getenv("KK_DIRECTOR_LIFE")?.toFloat() ?: 6f
        val identity = populationOf(seeds) { flyDirector(it, null, content, life) }
        println("VARIETY-PROBE DIRECTOR life=$life identity count=${identity.sumOf { it.capUnits } / seeds.size} " +
            "integrity=${identity.sumOf { it.integrity } / seeds.size} meanEvents=${identity.sumOf { it.events } / seeds.size.toDouble()} " +
            "minEvents=${identity.minOf { it.events }} minKinds=${identity.minOf { it.eventKinds.size }} held=${identity.sumOf { it.heldSteps }}")
        for (id in DirectorPersonalityId.entries) {
            val runs = populationOf(seeds) { flyDirector(it, id, content, life) }
            val pairs = identity.zip(runs)
            println("VARIETY-PROBE DIRECTOR $id " +
                bootstrapLine("count", pairs) { p -> p.sumOf { it.second.capUnits } / p.sumOf { it.first.capUnits } } + " " +
                bootstrapLine("integrity", pairs) { p -> p.sumOf { it.second.integrity } / p.sumOf { it.first.integrity } } +
                " meanEvents=${runs.sumOf { it.events } / seeds.size.toDouble()} minEvents=${runs.minOf { it.events }} minKinds=${runs.minOf { it.eventKinds.size }} held=${runs.sumOf { it.heldSteps }}")
        }
    }
}

/** One arm's own measures: W, S, M and L with bootstrap SEs. */
internal fun armLine(runs: List<VarietyRun>): String =
    "n=${runs.size} " + bootstrapLine("W", runs) { it.winShare() } + " " + bootstrapLine("S", runs) { it.medianSeconds() } + " " +
        bootstrapLine("M", runs) { it.medianMatter() } + " " + bootstrapLine("L", runs) { it.meanLevelAt600() }

/** An arm against the base on the same seeds: the win-share shift, Matter ratio and level shift, paired by seed. */
internal fun pairedLine(base: List<VarietyRun>, arm: List<VarietyRun>): String {
    val pairs = base.zip(arm)
    return bootstrapLine("dW", pairs) { p -> p.map { it.second }.winShare() - p.map { it.first }.winShare() } + " " +
        bootstrapLine("M/Mb", pairs) { p -> p.map { it.second }.medianMatter() / p.map { it.first }.medianMatter() } + " " +
        bootstrapLine("dL", pairs) { p -> p.map { it.second }.meanLevelAt600() - p.map { it.first }.meanLevelAt600() }
}

/** The seeker's signal measures: reach share, signals spawned and engaged, the cards it took and other sites touched. */
internal fun signalLine(runs: List<VarietyRun>): String =
    bootstrapLine("reach", runs) { it.reachShare() } + " spawned=${runs.sumOf { it.signalsSpawned }} " +
        "engaged=${runs.sumOf { it.signalsEngaged }} picks=${runs.flatMap { it.picks }.groupingBy { it }.eachCount()} " +
        "otherSites=${runs.count { it.touchedSite }}"

/** [content] with the probe's magnitude overrides (see [RunVarietyCalibrationProbeTest]). */
internal fun varietyKnobbed(content: GameplayContentSnapshot, knobs: String): GameplayContentSnapshot {
    var variety = content.variety
    for (entry in knobs.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
        val (name, raw) = entry.split('=').map { it.trim() }
        val value = raw.toFloat()
        val parts = name.split('.')
        variety = when {
            parts[0] == "budget" -> variety.copy(director = variety.director.copy(personalities = variety.director.personalities.map {
                if (it.id.name == parts[1]) it.copy(budgetScale = value) else it
            }.toImmutableList()))
            name == "affix.baseChance" -> variety.copy(affixes = variety.affixes.copy(baseChance = value))
            name == "affix.chancePerTier" -> variety.copy(affixes = variety.affixes.copy(chancePerTier = value))
            name == "champion.baseChance" -> variety.copy(champions = variety.champions.copy(baseChance = value))
            name == "champion.integrityScale" -> variety.copy(champions = variety.champions.copy(integrityScale = value))
            parts[0] == "signal" -> variety.copy(signals = variety.signals.copy(tuning = signalKnob(variety.signals.tuning, parts[1], value)))
            parts[0] in setOf("meteor", "ion", "gravity", "solar") -> variety.copy(worldEvents = eventKnob(variety.worldEvents, name, value))
            else -> {
                val omen = OmenId.valueOf(parts[0])
                val definitions = variety.omens.definitions
                val definition = definitions[omen.ordinal]
                if (parts[1] == "addGain") {
                    val added = definition.copy(gains = (definition.gains + RunModifierEffect.Stat(ItemEffect.valueOf(parts[2]), value)).toImmutableList())
                    variety = variety.copy(omens = variety.omens.copy(definitions = definitions.map { if (it.id == omen) added else it }.toImmutableList()))
                    continue
                }
                val gain = parts[1].startsWith("gain")
                val index = parts[1].removePrefix(if (gain) "gain" else "cost").toInt()
                val effects = if (gain) definition.gains else definition.costs
                val changed = effects.mapIndexed { at, effect -> if (at == index) effectKnob(effect, parts.getOrNull(2), value) else effect }
                    .toImmutableList()
                val updated = if (gain) definition.copy(gains = changed) else definition.copy(costs = changed)
                variety.copy(omens = variety.omens.copy(definitions = definitions.map { if (it.id == omen) updated else it }.toImmutableList()))
            }
        }
    }
    return content.copy(variety = variety)
}

private fun effectKnob(effect: RunModifierEffect, field: String?, value: Float): RunModifierEffect = when (effect) {
    is RunModifierEffect.Stat -> effect.copy(amount = value)
    is RunModifierEffect.MatterGainScale -> effect.copy(factor = value)
    is RunModifierEffect.Rerolls -> effect.copy(delta = value.toInt())
    is RunModifierEffect.KeynoteFloor -> effect.copy(share = value)
    is RunModifierEffect.HostileScale -> when (field) {
        "health" -> effect.copy(health = value)
        "speed" -> effect.copy(speed = value)
        "incomingDamage" -> effect.copy(incomingDamage = value)
        "spawnRate" -> effect.copy(spawnRate = value)
        "eliteRate" -> effect.copy(eliteRate = value)
        else -> error("a hostile scale needs its field: $field")
    }
    else -> error("no magnitude knob for $effect")
}

private fun signalKnob(tuning: kinetickk.ball.content.api.SignalTuning, field: String, value: Float) = when (field) {
    "merchantRareCost" -> tuning.copy(merchantRareCost = value)
    "merchantEpicCost" -> tuning.copy(merchantEpicCost = value)
    "merchantRerollCost" -> tuning.copy(merchantRerollCost = value)
    "shrineBloodShare" -> tuning.copy(shrineBloodShare = value)
    "shrineBloodImpact" -> tuning.copy(shrineBloodImpact = value)
    "shrineBloodWeapon" -> tuning.copy(shrineBloodWeapon = value)
    "gambleRerollChance" -> tuning.copy(gambleRerollChance = value)
    "gambleRerollWin" -> tuning.copy(gambleRerollWin = value.toInt())
    "crateCurse" -> tuning.copy(crateCurse = value)
    "crateCurseSeconds" -> tuning.copy(crateCurseSeconds = value)
    "derelictRepairShare" -> tuning.copy(derelictRepairShare = value)
    "derelictCargoMatter" -> tuning.copy(derelictCargoMatter = value)
    "priceBase" -> tuning.copy(priceBase = value)
    "priceRampSeconds" -> tuning.copy(priceRampSeconds = value)
    else -> error("unknown signal knob $field")
}

private fun eventKnob(events: kinetickk.ball.content.api.WorldEventPolicy, name: String, value: Float) = when (name) {
    "meteor.coreDamage" -> events.copy(meteor = events.meteor.copy(coreDamage = value))
    "meteor.enemyDamageScale" -> events.copy(meteor = events.meteor.copy(enemyDamageScale = value))
    "ion.coreDamage" -> events.copy(ion = events.ion.copy(coreDamage = value))
    "ion.enemyDamageScale" -> events.copy(ion = events.ion.copy(enemyDamageScale = value))
    "gravity.coreAcceleration" -> events.copy(gravity = events.gravity.copy(coreAcceleration = value))
    "solar.coolingPenalty" -> events.copy(solar = events.solar.copy(coolingPenalty = value))
    "solar.weaponBonus" -> events.copy(solar = events.solar.copy(weaponBonus = value))
    else -> error("unknown event knob $name")
}
