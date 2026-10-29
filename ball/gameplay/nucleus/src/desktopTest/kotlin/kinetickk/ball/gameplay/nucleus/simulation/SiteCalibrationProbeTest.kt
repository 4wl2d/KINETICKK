// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test

/**
 * The SITES calibration probe (plan §8.2-§8.3, spec §14.3): runs only when `KK_SITE_PROBE` names a window of probe
 * indices (`first..last` of `i * 7_919 + 13`; SITES calibrates on 2,201..2,300 and never on gate seeds) and prints each
 * arm's measures with bootstrap standard errors (20,000 resamples of whole runs, RNG seed 20261027). `KK_SITE_ARMS`
 * picks arms (RACE, RACE_PHONE, BRAKELESS, RAM, UNTOUCHED, VELOCITY, SLALOM, BOUNTY, BOUNTY_T6, ANOMALY, MIXED, NOPOI,
 * SIGIL; all by default), `KK_SITE_KNOBS` overrides the site knobs (`name=value`, comma-separated: goldFactor,
 * silverFactor, bronzeFactor, parReach, limitFactor, turnAllowanceSeconds, brakelessRequired, ramRequired, ramFoes,
 * ramFoeHealthShare, ramRingRadius, ramDuration, untouchedRequired, velocityRequired, velocityMinSpeed, slalomDuration,
 * bossIntegrityShare) and `KK_SITE_PILOT` the pilot's gains by their [SitePilotTuning] names; several sets separated by
 * `;` run one after the other.
 */
class SiteCalibrationProbeTest {
    @Test
    fun printsTheSiteArmsOnTheNamedWindow() {
        val window = System.getenv("KK_SITE_PROBE")?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val (first, last) = window.split("..").map { it.trim().toInt() }.let { it[0] to (it.getOrNull(1) ?: (it[0] + 99)) }
        require(last < 1_001 || first > 1_300) { "the probe never runs on gate seeds (1,001..1,300)" }
        val seeds = siteWindowSeeds(first, last)
        val arms = System.getenv("KK_SITE_ARMS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: listOf("RACE", "RACE_PHONE", "BRAKELESS", "RAM", "UNTOUCHED", "VELOCITY", "SLALOM", "BOUNTY", "BOUNTY_T6", "ANOMALY", "MIXED", "NOPOI", "SIGIL")
        // Several knob or gain sets run one after the other, separated by ';'.
        for (knobs in System.getenv("KK_SITE_KNOBS").orEmpty().split(';')) for (gains in System.getenv("KK_SITE_PILOT").orEmpty().split(';')) {
            val base = knobbed(canonicalGameplayContent, knobs)
            val tuning = pilot(gains)
            println("SITE-PROBE window=$first..$last knobs='$knobs' pilot='$gains'")
            for (name in arms) {
                val started = System.nanoTime()
                val runs = populationOf(seeds) { seed -> probeRun(name, seed, base, tuning) }
                val wall = (System.nanoTime() - started) / 1e9
                println("SITE-PROBE $name ${summary(name, runs)} wall=${"%.1f".format(wall)}s")
            }
        }
    }

    private fun probeRun(name: String, seed: Int, base: GameplayContentSnapshot, tuning: SitePilotTuning): SiteRun = when (name) {
        "RACE_PHONE" -> flySiteRun(seed, SiteArm.RACE, SiteRunSetup(412f, 915f, base = base, stopAfterWindows = true), tuning)
        "BOUNTY_T6" -> flySiteRun(seed, SiteArm.BOUNTY, SiteRunSetup(profile = gateProfile(GateMatrix.FULL, tier = 6), base = base), tuning)
        "NOPOI" -> flyNoPoiRun(seed, base)
        "SIGIL" -> flySiteRun(seed, SiteArm.SIGIL, SiteRunSetup(base = base, stopAfterWindows = true), tuning)
        else -> flySiteRun(seed, SiteArm.valueOf(name), SiteRunSetup(base = base), tuning)
    }

    private fun summary(name: String, runs: List<SiteRun>): String {
        val engagements = runs.flatMap { it.engagements }
        val outcomes = engagements.groupingBy { it.outcome }.eachCount().entries.sortedBy { it.key.ordinal }
            .joinToString(",") { "${it.key}:${it.value}" }
        val kinds = engagements.map { it.kind }.toSet()
        val parts = mutableListOf(
            "runs=${runs.size}",
            "engagements=${engagements.size}",
            "offers=${runs.sumOf { it.offers }}",
            "outcomes=[$outcomes]",
            bootstrapLine("winShare", runs) { sample -> sample.count { it.won }.toDouble() / sample.size },
            bootstrapLine("noPoiWinShare", runs) { sample -> sample.count { it.noPoiWin }.toDouble() / sample.size },
        )
        if (name == "NOPOI" || engagements.isEmpty()) return parts.joinToString(" ")
        parts += bootstrapLine("success", runs) { sample -> sample.judged(kinds).shareWhere { it.outcome.success } }
        parts += bootstrapLine("value", runs) { sample -> sample.judged(kinds).meanValue() }
        if (SiteKind.GATE_RACE in kinds) {
            parts += bootstrapLine("bronzeUp", runs) { sample -> sample.judged(kinds).shareWhere { it.outcome.medal >= 1 } }
            parts += bootstrapLine("silverUp", runs) { sample -> sample.judged(kinds).shareWhere { it.outcome.medal >= 2 } }
            parts += bootstrapLine("gold", runs) { sample -> sample.judged(kinds).shareWhere { it.outcome.medal >= 3 } }
        }
        if (SiteKind.BOUNTY_ARENA in kinds || SiteKind.FAR_SIGIL in kinds) {
            parts += bootstrapLine("medianFight", runs) { sample ->
                sample.judged(kinds).filter { it.outcome.success }.map { it.seconds }.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2].toDouble() }
            }
        }
        if (SiteKind.FAR_SIGIL in kinds || name == "SIGIL") parts += "summoned=${runs.count { run -> run.engagements.isNotEmpty() }}"
        engagements.filter { it.outcome == SiteOutcome.DIED }.takeIf { it.isNotEmpty() }?.let { died ->
            parts += "died=${died.groupingBy { it.notice }.eachCount()}"
        }
        engagements.filter { !it.read }.takeIf { it.isNotEmpty() }?.let { unread ->
            parts += "unread=${unread.map { "${it.kind}:${it.outcome}:'${it.notice}'" }}"
        }
        if (kinds.size > 1) for (kind in kinds.sortedBy { it.ordinal }) {
            parts += "$kind:n=${runs.judged(setOf(kind)).size}:" + bootstrapLine("success", runs) { sample -> sample.judged(setOf(kind)).shareWhere { it.outcome.success } }
        }
        if (name == "MIXED") for (family in engagements.map { it.kind.family }.distinct()) {
            val familyKinds = engagements.filter { it.kind.family == family }.map { it.kind }.toSet()
            parts += "$family:n=${runs.judged(familyKinds).size}:" + bootstrapLine("value", runs) { sample -> sample.judged(familyKinds).meanValue() }
        }
        return parts.joinToString(" ")
    }

    private fun knobbed(base: GameplayContentSnapshot, text: String): GameplayContentSnapshot {
        var sites = base.sites
        for ((name, value) in overrides(text)) {
            sites = when (name) {
                "goldFactor" -> sites.copy(race = sites.race.copy(goldFactor = value))
                "silverFactor" -> sites.copy(race = sites.race.copy(silverFactor = value))
                "bronzeFactor" -> sites.copy(race = sites.race.copy(bronzeFactor = value))
                "parReach" -> sites.copy(race = sites.race.copy(parReach = value))
                "limitFactor" -> sites.copy(race = sites.race.copy(limitFactor = value))
                "turnAllowanceSeconds" -> sites.copy(race = sites.race.copy(turnAllowanceSeconds = value))
                "brakelessRequired" -> sites.copy(challenges = sites.challenges.copy(brakelessRequired = value))
                "ramRequired" -> sites.copy(challenges = sites.challenges.copy(ramRequired = value.toInt()))
                "ramFoes" -> sites.copy(challenges = sites.challenges.copy(ramFoes = value.toInt()))
                "ramFoeHealthShare" -> sites.copy(challenges = sites.challenges.copy(ramFoeHealthShare = value))
                "ramRingRadius" -> sites.copy(challenges = sites.challenges.copy(ramRingRadius = value))
                "ramDuration" -> sites.copy(challenges = sites.challenges.copy(ramDuration = value))
                "untouchedRequired" -> sites.copy(challenges = sites.challenges.copy(untouchedRequired = value))
                "velocityRequired" -> sites.copy(challenges = sites.challenges.copy(velocityRequired = value))
                "velocityMinSpeed" -> sites.copy(challenges = sites.challenges.copy(velocityMinSpeed = value))
                "slalomDuration" -> sites.copy(
                    challenges = sites.challenges.copy(slalomDuration = value),
                    definitions = sites.definitions.map {
                        if (it.kind == SiteKind.SLALOM_TRIAL) it.copy(duration = value) else it
                    }.toImmutableList(),
                )
                "bossIntegrityShare" -> sites.copy(bounty = sites.bounty.copy(bossIntegrityShare = value))
                else -> error("unknown site knob $name")
            }
        }
        return base.copy(sites = sites)
    }

    private fun pilot(text: String): SitePilotTuning {
        var tuning = SitePilotTuning()
        for ((name, value) in overrides(text)) {
            tuning = when (name) {
                "approachReach" -> tuning.copy(approachReach = value)
                "raceReach" -> tuning.copy(raceReach = value)
                "raceBlendReach" -> tuning.copy(raceBlendReach = value)
                "raceBlend" -> tuning.copy(raceBlend = value)
                "raceDashReach" -> tuning.copy(raceDashReach = value)
                "brakelessShare" -> tuning.copy(brakelessShare = value)
                "brakelessSpeed" -> tuning.copy(brakelessSpeed = value)
                "ramSpeed" -> tuning.copy(ramSpeed = value)
                "ramDashReach" -> tuning.copy(ramDashReach = value)
                "untouchedShare" -> tuning.copy(untouchedShare = value)
                "untouchedSpeed" -> tuning.copy(untouchedSpeed = value)
                "velocityRadius" -> tuning.copy(velocityRadius = value)
                "velocitySpeed" -> tuning.copy(velocitySpeed = value)
                "slalomMargin" -> tuning.copy(slalomMargin = value)
                "slalomSpeed" -> tuning.copy(slalomSpeed = value)
                "slalomLead" -> tuning.copy(slalomLead = value)
                "bountyReturn" -> tuning.copy(bountyReturn = value)
                "beaconSpeed" -> tuning.copy(beaconSpeed = value)
                "orbitBand" -> tuning.copy(orbitBand = value)
                "orbitSpeed" -> tuning.copy(orbitSpeed = value)
                "farFlightAt" -> tuning.copy(farFlightAt = value)
                "farFlightReach" -> tuning.copy(farFlightReach = value)
                else -> error("unknown pilot gain $name")
            }
        }
        return tuning
    }

    private fun overrides(text: String): List<Pair<String, Float>> =
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { entry ->
            val (name, value) = entry.split('=').map { it.trim() }
            name to value.toFloat()
        }
}
