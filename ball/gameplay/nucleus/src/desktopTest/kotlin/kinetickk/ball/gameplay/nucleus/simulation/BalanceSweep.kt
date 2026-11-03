// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.foundation.collections.ImmutableList
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.sqrt

/*
 * The many-run balance sweep (docs/balance/progression.md): `./gradlew :ball:gameplay:nucleus:balanceSweep`. It plays the
 * gate harness (the input-only NoPoi bot on [playGateRun]) for every tier × arm cell over a calibration window and prints
 * one table row per cell; `-Psweep.mode=campaign` instead plays whole profiles from a fresh start (see
 * BalanceCampaign.kt). It is a tool, never a gate: it runs outside `desktopTest`, so no shard, CI job or `check` sees it.
 * Inputs are system properties (the Gradle task forwards `-Psweep.*`):
 * - `sweep.tiers`: `0..10` or a list `0,2,4` (default `0..10`);
 * - `sweep.arms`: arm names of [SweepArm] (default all);
 * - `sweep.window`: probe indices `first..last` (policy.md's progression window, default `3401..3500`; never the gate
 *   population);
 * - `sweep.threads`: worker threads (default every core);
 * - `sweep.knobs`: content overrides, `name=value` pairs separated by commas ([sweepContent]);
 * - `sweep.out`: an optional file for one tab-separated line per run.
 */

/** The progression window of policy.md: Rebirth curves, permanent progression and this sweep. */
internal val ProgressionWindow: IntRange = 3_401..4_000

/** The profiles the sweep plays: the design targets' arms of docs/balance/progression.md. */
internal enum class SweepArm(val label: String) {
    /** No Matrix, no upgrades, the Orb and the bot's loadout (P1, P2). */
    FRESH("fresh"),

    /** Half the Matrix ([halfMatrixRanks]) and Refit/Honing at half the maximum level, the Orb (P3). */
    HALF("half"),

    /** The full Matrix, maxed Refit and Honing, the Pentagon with its signature Arc Coil, the bot's own Relics (P5). */
    FULL("full"),

    /** [FULL] with RebirthBalanceGateTest's strong Relics bound from the start (P4). */
    STRONG("strong");

    fun profile(tier: Int, content: GameplayContentSnapshot): GameplayProfileSnapshot {
        val maxLevel = content.upgrades.maxLevel
        return when (this) {
            FRESH -> gateProfile(GateMatrix.NONE, tier, content = content)
            HALF -> {
                val weapon = botLoadout(BotWeaponOrder, ReferenceArmory)
                gateProfile(GateMatrix.HALF, tier, CoreShape.ORB, weapon, content = content,
                    upgrades = gateUpgrades(CoreShape.ORB, maxLevel / 2, gateOwnedWeapons(weapon), maxLevel / 2))
            }
            FULL, STRONG -> gateProfile(GateMatrix.FULL, tier, CoreShape.PENTAGON, WeaponId.ARC_COIL, content = content,
                upgrades = gateUpgrades(CoreShape.PENTAGON, maxLevel, gateOwnedWeapons(WeaponId.ARC_COIL), maxLevel))
        }
    }

    val relics: List<RelicId> get() = if (this == STRONG) SweepStrongRelics else emptyList()
}

/** RebirthBalanceGateTest's strong build Relics. */
internal val SweepStrongRelics: List<RelicId> =
    listOf(RelicId.STORMHEART_CAPACITOR, RelicId.PANDEMIC_CODEX, RelicId.THUNDER_CHOIR, RelicId.CINDER_CROWN)

/** What one sweep run ended with. [clear] is the seconds from the Architect's arrival to the Victory (null otherwise). */
internal data class SweepRun(
    val seed: Int,
    val won: Boolean,
    val seconds: Float,
    val clear: Float?,
    val level: Int,
    val weaponLevel: Int,
    val legendaries: Int,
    val epics: Int,
    val relics: Int,
    val matter: Long,
    /** "WIN", "TIMEOUT" (alive at the harness's 900 s), or the run end message. */
    val cause: String,
)

/** Plays one gate run of [profile] on [seed] and reads what the sweep reports. */
internal fun sweepRun(seed: Int, profile: GameplayProfileSnapshot, relics: List<RelicId>, content: GameplayContentSnapshot): SweepRun {
    var architectAt: Float? = null
    val state = playGateRun(seed, profile, NoPoiBot(), relics, content) { s ->
        if (architectAt == null && s.enemies.any { it.type == EnemyType.ARCHITECT }) architectAt = s.elapsed
    }
    val won = state.phase == GamePhase.VICTORY
    var legendaries = 0
    var epics = 0
    for (item in content.items) {
        val stacks = state.itemStacks[item.id]
        if (item.rarity == ItemRarity.LEGENDARY) legendaries += stacks
        if (item.rarity == ItemRarity.EPIC) epics += stacks
    }
    val cause = when {
        won -> "WIN"
        state.phase == GamePhase.GAME_OVER -> state.message
        else -> "TIMEOUT"
    }
    return SweepRun(seed, won, state.elapsed, architectAt?.takeIf { won }?.let { state.elapsed - it }, state.level, state.weaponLevel,
        legendaries, epics, state.equippedRelics.size, state.runMatter, cause)
}

/**
 * The content a sweep plays: the canonical catalog with [knobs] applied. Knobs are the calibration constants of
 * docs/balance/progression.md: `health`, `damage`, `matter` (the per-tier bases of H, D and the Matter reward),
 * `honing`, `refit` (per-level damage growth), `refitIntegrity` (integrity per Refit level), `weaponCap` and
 * `bossCapShrink` (the boss hit cap's shrink per tier).
 */
internal fun sweepContent(knobs: Map<String, String>): GameplayContentSnapshot {
    var content = canonicalGameplayContent
    knobs.forEach { (name, raw) ->
        val value = raw.toFloat()
        content = when (name) {
            "health", "damage", "matter" -> content.copy(rebirth = content.rebirth.copy(profiles = content.rebirth.profiles.map { profile ->
                val scale = Math.pow(value.toDouble(), profile.tier.toDouble()).toFloat()
                when (name) {
                    "health" -> profile.copy(enemyHealthMultiplier = scale)
                    "damage" -> profile.copy(incomingDamageMultiplier = scale)
                    else -> profile.copy(matterGainMultiplier = scale)
                }
            }.let { ImmutableList.copyOf(it) }))
            else -> sweepUpgradeKnob(content, name, value)
        }
    }
    return content
}

/** Parses `0..10` or `0,2,4`. */
internal fun sweepTiers(text: String): List<Int> =
    if (".." in text) text.split("..").let { (first, last) -> (first.trim().toInt()..last.trim().toInt()).toList() }
    else text.split(",").map { it.trim().toInt() }

internal fun sweepKnobs(text: String?): Map<String, String> =
    text.orEmpty().split(",").filter { it.isNotBlank() }.associate { pair ->
        val (name, value) = pair.split("=").map { it.trim() }
        name to value
    }

/** Runs [work] on every one of [items] on [threads] workers; results keep the order of [items]. */
internal fun <I, T> sweepPool(items: List<I>, threads: Int, work: (I) -> T): List<T> {
    val pool = Executors.newFixedThreadPool(threads) { task -> Thread(task, "balance-sweep").apply { isDaemon = true } }
    try {
        return items.map { item -> pool.submit(Callable { work(item) }) }.map { it.get() }
    } finally {
        pool.shutdown()
    }
}

private fun List<Float>.median(): Float = if (isEmpty()) Float.NaN else sorted()[size / 2]

private fun share(count: Int, of: Int): String {
    if (of == 0) return "-"
    val p = count.toDouble() / of
    return "%.2f±%.2f".format(p, sqrt(p * (1 - p) / of))
}

/** One table row of [runs]: win share (binomial SE), medians, means and the end causes. */
internal fun sweepRow(tier: Int, arm: SweepArm, runs: List<SweepRun>): String {
    val wins = runs.filter { it.won }
    val causes = runs.groupingBy { it.cause }.eachCount().entries.sortedByDescending { it.value }
        .joinToString(" ") { (cause, count) -> "${cause.replace(' ', '_')}:$count" }
    fun mean(value: (SweepRun) -> Int): String = "%.2f".format(runs.map(value).average())
    return listOf(
        tier.toString(), arm.label, runs.size.toString(), share(wins.size, runs.size),
        "%.0f".format(runs.map { it.seconds }.median()),
        wins.mapNotNull { it.clear }.median().let { if (it.isNaN()) "-" else "%.0f".format(it) },
        runs.map { it.level.toFloat() }.median().toInt().toString(),
        runs.map { it.weaponLevel.toFloat() }.median().toInt().toString(),
        mean { it.legendaries }, mean { it.epics }, mean { it.relics },
        runs.map { it.matter.toFloat() }.median().toLong().toString(),
        causes,
    ).joinToString("\t")
}

internal const val SWEEP_HEADER: String =
    "tier\tarm\truns\twin(SE)\tmedSurvival\tmedClear\tlevel\tweaponLvl\tlegendary\tepic\trelics\tmedMatter\tends"

fun main() {
    val mode = System.getProperty("kinetickk.sweep.mode") ?: "cells"
    val threads = System.getProperty("kinetickk.sweep.threads")?.toInt() ?: Runtime.getRuntime().availableProcessors()
    val window = probeWindow(System.getProperty("kinetickk.sweep.window") ?: "3401..3500")
    val out = System.getProperty("kinetickk.sweep.out")?.takeIf { it.isNotBlank() }?.let(::File)
    val knobSets = System.getProperty("kinetickk.sweep.knobs").orEmpty().split(";").map(::sweepKnobs)
    val cells = sweepCells()
    out?.printWriter()?.use { it.println("knobs\ttier\tarm\tseed\twon\tseconds\tclear\tlevel\tweaponLevel\tlegendaries\tepics\trelics\tmatter\tcause") }
    for (knobs in knobSets) {
        val content = sweepContent(knobs)
        println("balanceSweep mode=$mode window=$window threads=$threads knobs=$knobs")
        if (mode == "campaign") {
            runCampaigns(content, window, threads, System.getProperty("kinetickk.sweep.campaigns")?.toInt() ?: 12)
            continue
        }
        sweepCellsOnce(cells, windowSeeds(window), threads, content, knobs, out)
    }
}

/** The cells to play: `sweep.cells` (`arm:tier` pairs, e.g. `fresh:2,half:5`) or every `sweep.tiers` × `sweep.arms`. */
private fun sweepCells(): List<Pair<Int, SweepArm>> {
    fun arm(name: String): SweepArm = SweepArm.entries.single { it.label == name.trim() }
    System.getProperty("kinetickk.sweep.cells")?.takeIf { it.isNotBlank() }?.let { text ->
        return text.split(",").map { pair -> pair.split(":").let { (name, tier) -> tier.trim().toInt() to arm(name) } }
    }
    val tiers = sweepTiers(System.getProperty("kinetickk.sweep.tiers") ?: "0..10")
    val arms = System.getProperty("kinetickk.sweep.arms")?.split(",")?.map(::arm) ?: SweepArm.entries
    return tiers.flatMap { tier -> arms.map { tier to it } }
}

private fun sweepCellsOnce(
    cells: List<Pair<Int, SweepArm>>,
    seeds: List<Int>,
    threads: Int,
    content: GameplayContentSnapshot,
    knobs: Map<String, String>,
    out: File?,
) {
    val started = System.nanoTime()
    val runs = sweepPool(cells.flatMap { cell -> seeds.map { seed -> cell to seed } }, threads) { (cell, seed) ->
        val (tier, arm) = cell
        sweepRun(seed, arm.profile(tier, content), arm.relics, content)
    }
    val label = knobs.entries.joinToString(",") { (name, value) -> "$name=$value" }
    out?.let { file ->
        file.appendText(buildString {
            cells.forEachIndexed { index, (tier, arm) ->
                runs.subList(index * seeds.size, (index + 1) * seeds.size).forEach { run ->
                    appendLine(listOf(label, tier, arm.label, run.seed, run.won, run.seconds, run.clear ?: "", run.level,
                        run.weaponLevel, run.legendaries, run.epics, run.relics, run.matter, run.cause).joinToString("\t"))
                }
            }
        })
    }
    println(SWEEP_HEADER)
    cells.forEachIndexed { index, (tier, arm) -> println(sweepRow(tier, arm, runs.subList(index * seeds.size, (index + 1) * seeds.size))) }
    println("balanceSweep ${runs.size} runs in ${(System.nanoTime() - started) / 1_000_000_000} s")
}
