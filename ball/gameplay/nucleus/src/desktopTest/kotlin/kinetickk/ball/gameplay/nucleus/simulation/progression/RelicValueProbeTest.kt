// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerLoadout
import java.io.File
import kotlin.test.Test

/**
 * The Relic value probe (a calibration window, by default the combined confirmation window i = 2,501..2,800; never the
 * gate population). It measures each Relic's marginal value: one Relic bound at the run start with the rest of the
 * run's Relic budget spent on nothing (no further offer, no Meld), against the same run with no Relic at all, on the
 * same seeds with the reference bot (NoPoiBot). Per setup and Relic it prints the win share and its paired difference
 * to no Relic, the median survival, the median Architect clear (arrival to Victory, wins) and the offense read before
 * win share saturates: damage dealt and kills by 360 s (runs alive then), as a paired median ratio to no Relic. Paired
 * bootstrap standard errors follow policy.md (20,000 resamples, RNG seed 20261027).
 *
 * `KK_RELIC_PROBE=2501..2800` names the window; `KK_RELIC_SETUPS` the setups as
 * `arm:tier[:weapon[:shape[:element[:tag[:brake]]]]]` with balanceSweep's arm names (default `half:7,full:10`), e.g.
 * `half:5:FLUX_WAKE:ORB:THERMAL:RAM` flies the half profile on the Flux Wake with a Thermal Ram bot (`-` keeps a part; a
 * trailing `brake` makes the bot brake for Stasis Anchor); `KK_RELIC_ARMS` the arms (default every Relic alone; `A+B`
 * binds several); `KK_RELIC_RANK` their rank (default 1); `KK_RELIC_OUT` an optional file that receives one
 * tab-separated line per run as each setup ends. `--rerun` makes Gradle run it again with new variables; the summary
 * lands in the suite's JUnit XML. The work notes (relics.md) hold the measured table.
 */
class RelicValueProbeTest {
    @Test
    fun probeEachRelicsMarginalValue() {
        val window = System.getenv("KK_RELIC_PROBE")?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val seeds = windowSeeds(probeWindow(window))
        val setups = (System.getenv("KK_RELIC_SETUPS")?.takeIf { it.isNotBlank() } ?: "half:7,full:10").split(',').map { ProbeSetup.parse(it.trim()) }
        val arms = System.getenv("KK_RELIC_ARMS")?.takeIf { it.isNotBlank() }?.split(',')?.map { ProbeArm.parse(it.trim()) }
            ?: RelicId.entries.map { ProbeArm(it.name, listOf(it)) }
        val rank = System.getenv("KK_RELIC_RANK")?.trim()?.toInt() ?: 1
        val out = System.getenv("KK_RELIC_OUT")?.takeIf { it.isNotBlank() }?.let(::File)
        val content = canonicalGameplayContent
        for (setup in setups) {
            val flown = listOf(ProbeArm("NONE", emptyList())) + arms.filter { arm -> arm.relics.all { content.relic(it).minimumRebirth <= setup.tier } }
            val runs = populationOf(flown.flatMap { arm -> seeds.map { arm to it } }) { (arm, seed) -> fly(seed, setup, arm.relics, rank, content) }
            val byArm = flown.indices.associate { flown[it] to runs.subList(it * seeds.size, (it + 1) * seeds.size) }
            out?.appendText(buildString {
                byArm.forEach { (arm, armRuns) -> armRuns.forEach { appendLine("${setup.label}\t${arm.label}\t$rank\t${it.line()}") } }
            })
            report("window=$window setup=${setup.label} rank=$rank", byArm)
        }
    }

    /** An arm: the Relics bound at the start (none for NONE); `A+B` binds several. */
    private class ProbeArm(val label: String, val relics: List<RelicId>) {
        companion object {
            fun parse(text: String): ProbeArm =
                ProbeArm(text, text.split('+').map { it.trim().uppercase() }.filter { it != "NONE" && it.isNotEmpty() }.map(RelicId::valueOf))
        }
    }

    /** A setup: a balanceSweep profile at a tier, optionally on another weapon or character, with the bot's build taste. */
    private class ProbeSetup(
        val label: String,
        val arm: SweepArm,
        val tier: Int,
        val weapon: WeaponId?,
        val shape: CoreShape?,
        val element: StatusElement?,
        val tag: BuildTag,
        val brakeForStasis: Boolean,
    ) {
        fun profile(content: GameplayContentSnapshot): GameplayProfileSnapshot {
            val base = arm.profile(tier, content)
            if (weapon == null && shape == null) return base
            val nextWeapon = weapon ?: base.loadout.selectedWeapon
            val nextShape = shape ?: base.loadout.coreShape
            return base.copy(
                loadout = PlayerLoadout(nextShape, nextWeapon, gateOwnedWeapons(nextWeapon)),
                upgrades = gateUpgrades(nextShape, base.upgrades.characterLevels.max(), gateOwnedWeapons(nextWeapon), base.upgrades.weaponLevels.max()),
            )
        }

        fun bot(): NoPoiBot = NoPoiBot(archetypeTag = tag, element = element, brakeForStasis = brakeForStasis)

        companion object {
            fun parse(text: String): ProbeSetup {
                val parts = text.split(':').map { it.trim() }
                val arm = SweepArm.entries.single { it.label == parts[0] }
                fun part(index: Int): String? = parts.getOrNull(index)?.takeIf { it.isNotEmpty() && it != "-" }
                return ProbeSetup(
                    label = text,
                    arm = arm,
                    tier = parts[1].toInt(),
                    weapon = part(2)?.let(WeaponId::valueOf),
                    shape = part(3)?.let(CoreShape::valueOf),
                    element = part(4)?.let { if (it == "NONE") null else StatusElement.valueOf(it) } ?: StatusElement.ENTROPY,
                    tag = part(5)?.let(BuildTag::valueOf) ?: BuildTag.RAM,
                    brakeForStasis = part(6) == "brake",
                )
            }
        }
    }

    /** One run: its end, the Architect clear (null unless won) and the offense marks at 360 s (null when dead by then). */
    private class ValueRun(
        val seed: Int,
        val won: Boolean,
        val seconds: Float,
        val clear: Float?,
        val damageAt360: Double?,
        val killsAt360: Int?,
        val takenAt360: Double?,
        val level: Int,
        val cause: String,
    ) {
        fun line(): String = listOf(seed, won, seconds, clear ?: "", damageAt360 ?: "", killsAt360 ?: "", takenAt360 ?: "", level, cause)
            .joinToString("\t")
    }

    private fun fly(seed: Int, setup: ProbeSetup, relics: List<RelicId>, rank: Int, content: GameplayContentSnapshot): ValueRun {
        val state = startGateRun(seed, setup.profile(content), content = content)
        relics.forEach { relic -> repeat(rank) { state.claimRelicForTesting(relic) } }
        // The rest of the budget is spent on nothing: no offer opens, no Meld, no other Relic.
        state.relicRun.relicClaims = content.relicPolicy.acquisitionsPerRun
        val bot = setup.bot()
        var architectAt: Float? = null
        var damage: Double? = null
        var kills: Int? = null
        var taken: Double? = null
        while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
            if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                bot.control(state)
                state.update(0.02f)
            }
            if (damage == null && state.elapsed >= 360f && state.phase != GamePhase.GAME_OVER) {
                damage = state.runStats.damageDealt
                kills = state.runStats.kills
                taken = state.runStats.damageTaken
            }
            if (architectAt == null && state.enemies.any { it.type == EnemyType.ARCHITECT }) architectAt = state.elapsed
            state.takeSoundCues()
            state.takeVisualFxCues()
        }
        val won = state.phase == GamePhase.VICTORY
        val cause = when {
            won -> "WIN"
            state.phase == GamePhase.GAME_OVER -> state.message.replace(' ', '_')
            else -> "TIMEOUT"
        }
        return ValueRun(seed, won, state.elapsed, architectAt?.takeIf { won }?.let { state.elapsed - it }, damage, kills, taken,
            state.level, cause)
    }

    private fun report(label: String, runs: Map<ProbeArm, List<ValueRun>>) {
        val none = runs.values.first()
        runs.forEach { (arm, armRuns) ->
            val paired = none.zip(armRuns)
            val alive = paired.filter { (n, a) -> n.damageAt360 != null && a.damageAt360 != null }
            println("RELIC VALUE $label arm=${arm.label} win=${"%.3f".format(armRuns.shareOf { it.won })} " +
                bootstrapLine("dWin", paired) { pairs -> pairs.map { (n, a) -> (if (a.won) 1.0 else 0.0) - (if (n.won) 1.0 else 0.0) }.average() } +
                " survival=${armRuns.map { it.seconds }.gateMedian()} clear=${armRuns.mapNotNull { it.clear }.finiteMedian()} " +
                "alive360=${alive.size} " +
                bootstrapLine("damage360", alive, "%.3f") { pairs ->
                    pairs.map { it.second.damageAt360!!.toFloat() }.finiteMedian() / pairs.map { it.first.damageAt360!!.toFloat() }.finiteMedian()
                } + " " +
                bootstrapLine("kills360", alive, "%.3f") { pairs ->
                    pairs.map { it.second.killsAt360!!.toFloat() }.finiteMedian() / pairs.map { it.first.killsAt360!!.toFloat() }.finiteMedian()
                } + " " +
                bootstrapLine("taken360", alive, "%.3f") { pairs ->
                    pairs.map { it.second.takenAt360!!.toFloat() }.finiteMedian() / pairs.map { it.first.takenAt360!!.toFloat() }.finiteMedian()
                } + " ends=" + armRuns.groupingBy { it.cause }.eachCount().entries.sortedByDescending { it.value }.joinToString(",") { "${it.key}:${it.value}" })
        }
    }
}
