// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreStasisTuning
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.projection.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fully progressed profiles at tier 10 and the perceptibility of the permanent upgrades (docs/balance/progression.md,
 * P4, P5 and P7), on the gate harness ([sweepRun], the arms of [SweepArm]) over the first [TOP_RUNS] [PopulationSeeds]:
 * - P4: the full Matrix, the Pentagon with its signature Arc Coil, Refit and Honing at the maximum and the strong Relics
 *   bound from the start ([SweepArm.STRONG]) win tier 10 in at least [P4_MIN_WIN] of the runs: comfortable;
 * - P5: the same profile with the bot's own Relics ([SweepArm.FULL]) wins tier 10 in [P5_WIN]: contested;
 * - P7 in play: Orb/Flux Wake and Pentagon/Arc Coil, maxed against 0, in a 25 s crowd and a 180 s Architect fight on four
 *   fixed seeds: character-ability damage per engaged second at least [P7_MIN_ABILITY_GAIN] times, weapon-side damage at
 *   least [P7_MIN_WEAPON_GAIN] times (it includes impacts and procs that Honing never touches) and no more integrity
 *   lost (as a share of maximum integrity; in the Architect fight a Core below half its integrity is repaired, the loss
 *   counted, so both flights meet the whole fight). The exact P7 bounds on the hits (Honing ×1.8, Refit ×1.6, a full
 *   Matrix ×1.8 offense and effective integrity) are PermanentPowerBudgetTest's.
 * These replace J1-J4 (the owner superseded "tier 10 at most 3 of 5" and the "upgrades cannot buy a tier" bounds: Refit
 * and Honing are now half of the permanent power by design). Thresholds are the design's; the population rule of
 * policy.md restates them on the integrated base.
 */
class UpgradeBalanceGateTest {
    @Test fun p4TheFullyProgressedStrongBuildClearsTierTenComfortably() {
        // The setup is what it claims: the Pentagon's signature and Stormheart Capacitor's Charge-key harmony are live.
        val start = startGateRun(PopulationSeeds.first(), SweepArm.STRONG.profile(10, canonicalGameplayContent), SweepArm.STRONG.relics)
        val synergy = start.toRenderModel().weaponSynergy
        assertTrue(synergy.signatureActive, "the signature bonus is active")
        assertEquals(RelicId.STORMHEART_CAPACITOR, synergy.harmonizingRelic, "the key-harmony Relic")
        val runs = top(SweepArm.STRONG)
        val share = runs.shareOf { it.won }
        println("UPGRADE_GATE P4 tier=10 strong+maxed win share ${"%.3f".format(share)} of ${runs.size} " +
            "ends ${runs.groupingBy { it.cause }.eachCount()}")
        assertTrue(share >= P4_MIN_WIN, "a fully progressed strong build must clear tier 10 comfortably: ${"%.3f".format(share)}")
    }

    @Test fun p5AFullyProgressedOrdinaryBuildContestsTierTen() {
        val runs = top(SweepArm.FULL)
        val share = runs.shareOf { it.won }
        println("UPGRADE_GATE P5 tier=10 full+maxed win share ${"%.3f".format(share)} of ${runs.size} " +
            "ends ${runs.groupingBy { it.cause }.eachCount()}")
        assertTrue(share in P5_WIN, "a fully progressed ordinary build must contest tier 10: ${"%.3f".format(share)}")
    }

    @Test fun p7MaxedUpgradesArePerceptibleInEqualEncounters() {
        val failures = mutableListOf<String>()
        for ((shape, weapon) in listOf(CoreShape.ORB to WeaponId.FLUX_WAKE, CoreShape.PENTAGON to WeaponId.ARC_COIL)) {
            val plain = Totals()
            val upgraded = Totals()
            for (seed in PerceptibleSeeds) for (boss in listOf(false, true)) {
                val base = encounter(shape, weapon, level = 0, boss = boss, seed = seed)
                val maxed = encounter(shape, weapon, level = canonicalGameplayContent.upgrades.maxLevel, boss = boss, seed = seed)
                println("UPGRADE_P7 $shape/$weapon seed=$seed ${if (boss) "BOSS" else "CROWD"} base=$base maxed=$maxed")
                plain += base
                upgraded += maxed
            }
            val weaponGain = upgraded.weaponRate / plain.weaponRate
            val abilityGain = upgraded.abilityRate / plain.abilityRate
            println("UPGRADE_GATE P7 $shape/$weapon weapon=${weaponGain}x ability=${abilityGain}x " +
                "integrityLost=${upgraded.integrityLost}/${plain.integrityLost}")
            if (weaponGain < P7_MIN_WEAPON_GAIN) failures += "$shape/$weapon weapon-side damage only ${weaponGain}x"
            if (abilityGain < P7_MIN_ABILITY_GAIN) failures += "$shape/$weapon ability damage only ${abilityGain}x"
            if (upgraded.integrityLost > plain.integrityLost) {
                failures += "$shape/$weapon lost more integrity maxed (${upgraded.integrityLost}) than at 0 (${plain.integrityLost})"
            }
        }
        assertTrue(failures.isEmpty(), "maxed upgrades must be perceptible: $failures")
    }

    /** The arm's tier-10 runs over the first [TOP_RUNS] [PopulationSeeds], played once per test JVM. */
    private fun top(arm: SweepArm): List<SweepRun> = synchronized(TopRuns) {
        TopRuns.getOrPut(arm) {
            populationOf(PopulationSeeds.take(TOP_RUNS)) { seed ->
                sweepRun(seed, arm.profile(10, canonicalGameplayContent), arm.relics, canonicalGameplayContent)
            }
        }
    }

    /** One encounter's output per engaged second (until every target is down, else the whole encounter). */
    private data class EncounterMeasure(val weaponRate: Float, val abilityRate: Float, val integrityLost: Float) {
        override fun toString(): String =
            "weapon=${weaponRate.toInt()}/s ability=${abilityRate.toInt()}/s lost=${(integrityLost * 100f).toInt()}%"
    }

    private class Totals {
        var weaponRate = 0f
        var abilityRate = 0f
        var integrityLost = 0f

        operator fun plusAssign(measure: EncounterMeasure) {
            weaponRate += measure.weaponRate
            abilityRate += measure.abilityRate
            integrityLost += measure.integrityLost
        }
    }

    /**
     * ComparativeBuildScenarioTest's encounter shape with equal resources: level 18, weapon level 10, the eight
     * shared survival stacks, no Relics, spawning held; twelve Drifters at 540 s for 25 s or the Architect at its
     * arrival for 180 s. The Core orbits the targets close enough for impacts and dodges shots like that harness.
     * The crowd's Drifters (WeaponBalanceTest's arena targets) hold [CROWD_INTEGRITY] and are topped up below a quarter,
     * so no kill ends or quantizes the measurement and both flights meet the same crowd for all 25 s; the
     * Architect fight is measured until the Victory or 180 s, and a Core that falls below half its integrity there is
     * repaired to full (its loss counted), so no fall ends it early.
     */
    private fun encounter(shape: CoreShape, weapon: WeaponId, level: Int, boss: Boolean, seed: Int): EncounterMeasure {
        // The scripted orbit measures upgrades, not player behaviour; Core Stasis is off.
        val game = MutableGameState(canonicalGameplayContent.copy(coreStasis = CoreStasisTuning.DISABLED), seed = seed).apply {
            coreShape = shape
            startingWeapon = weapon
            characterUpgradeLevels[shape.ordinal] = level
            weaponUpgradeLevels[weapon.ordinal] = level
            startRun()
            settings = settings.copy(simulationSpeed = 1f)
            if (boss) {
                elapsed = content.tempo.bossAtSeconds
                runGrace = 0f
            } else {
                elapsed = 540f
            }
            screenWidth = 1920f
            screenHeight = 1080f
            this.level = 18
            nextLevelData = dataRequiredForLevel(this.level)
            weaponLevel = 10
            listOf("Nanite Mesh" to 2, "Shield Cell" to 2, "Heat Sink" to 2, "Hardlight Weave" to 2).forEach { (name, stacks) ->
                val item = content.items.single { it.name == name }
                repeat(stacks) { acquireItem(item.id) }
            }
            enemies.clear()
            spawnClock = Float.MAX_VALUE
            nextEliteAt = Float.MAX_VALUE
            bossSpawned = true
            quietArenaForTesting()
            coreX = 220f
            previousCoreX = coreX
            velocityY = 420f
            cameraX = coreX
            cameraY = coreY
            pointerX = screenWidth / 2f + 360f
            pointerY = screenHeight / 2f
            previousSingularityX = coreX + 360f
            previousSingularityY = coreY
        }
        val count = if (boss) 1 else 12
        val targets = (0 until count).map { index ->
            val angle = index * TAU / count
            val radius = 170f
            if (boss) {
                game.spawnedEnemy(EnemyType.ARCHITECT, 0f, 0f).also { game.enemies += it }
            } else {
                game.addEnemyForTesting(cos(angle) * radius, sin(angle) * radius, hp = CROWD_INTEGRITY)
            }
        }
        var crowdDamage = 0.0
        val startedAt = game.elapsed
        val seconds = if (boss) 180 else 25
        var heading = 0f
        var clearedAt = -1f
        var integrityLost = 0f
        var previousIntegrity = game.hp
        val damageBefore = game.damageDealt
        val abilityBefore = game.characterRuntime.abilityDamage
        repeat(seconds * 60) { frame ->
            if (game.phase != GamePhase.RUNNING) {
                if (game.phase == GamePhase.CHOICE && game.choices.isNotEmpty()) game.choose(0) else return@repeat
            }
            if (!boss) for (target in targets) {
                if (target !in game.enemies) {
                    target.dead = false
                    game.enemies += target
                }
                if (target.hp < target.maxHp * 0.25f) target.hp = target.maxHp
            }
            val t = frame / 60f
            val live = targets.filter { !it.dead && it.hp > 0f }
            val centerX = if (live.isEmpty()) 0f else live.sumOf { it.x.toDouble() }.toFloat() / live.size
            val centerY = if (live.isEmpty()) 0f else live.sumOf { it.y.toDouble() }.toFloat() / live.size
            val orbit = 140f
            val angular = if (shape == CoreShape.ORB) 1.8f else 1.3f
            val desiredX = centerX + cos(t * angular) * orbit
            val desiredY = centerY + sin(t * angular) * orbit
            var accelerationX = (desiredX - game.coreX) * 5f + (-sin(t * angular) * orbit * angular - game.velocityX) * 3f
            var accelerationY = (desiredY - game.coreY) * 5f + (cos(t * angular) * orbit * angular - game.velocityY) * 3f
            var urgent = false
            for (shot in game.projectiles) if (shot.hostile) {
                val relativeVx = shot.vx - game.velocityX
                val relativeVy = shot.vy - game.velocityY
                val dx = shot.x - game.coreX
                val dy = shot.y - game.coreY
                val time = (-(dx * relativeVx + dy * relativeVy) / max(1f, relativeVx * relativeVx + relativeVy * relativeVy)).coerceIn(0f, 0.4f)
                val closestX = dx + relativeVx * time
                val closestY = dy + relativeVy * time
                val closest = length(closestX, closestY)
                if (dx * relativeVx + dy * relativeVy < 0f && closest < 60f) {
                    val speed = max(1f, length(relativeVx, relativeVy))
                    val side = if (-relativeVy * game.velocityX + relativeVx * game.velocityY >= 0f) 1f else -1f
                    val push = (60f - closest) / 60f * 1_200f
                    accelerationX += -relativeVy / speed * side * push
                    accelerationY += relativeVx / speed * side * push
                    if (time < 0.13f && closest < 28f) urgent = true
                }
            }
            val aim = game.steeringAimFor(accelerationX, accelerationY)
            val desiredHeading = aim.heading
            // Braking now needs the pointer behind the screen centre, so the heading must be able to swing back quickly:
            // at 0.15 rad a frame the orbit keeps the tracking error it had under the 0.5.0 law.
            heading += atan2(sin(desiredHeading - heading), cos(desiredHeading - heading)).coerceIn(-0.15f, 0.15f)
            val distance = aim.distance.coerceIn(65f, 480f)
            val edgeMargin = (1f - game.content.tempo.fatigue.edgeStrainStart) * 0.5f
            game.updatePointer(
                (game.coreX + cos(heading) * distance - game.cameraX + game.screenWidth / 2f)
                    .coerceIn(game.screenWidth * edgeMargin, game.screenWidth * (1f - edgeMargin)),
                (game.coreY + sin(heading) * distance - game.cameraY + game.screenHeight / 2f)
                    .coerceIn(game.screenHeight * edgeMargin, game.screenHeight * (1f - edgeMargin)),
            )
            val brake = game.speed > 850f
            game.setBrake(brake)
            if (urgent && !brake && game.heat < 45f) game.requestDash()
            val before = targets.sumOf { it.hp.toDouble() }
            // A fall never ends the Architect fight: below half its integrity the Core is repaired to full (the loss stays
            // counted), so both flights meet the fight until the Victory or 180 s. The scripted orbit falls to the Architect
            // under either flight law and any pilot slew, and a fall caps the loss at one integrity plus what was healed, so
            // the flight that lives longer would read as the one that lost more.
            if (boss && game.hp < game.maxHp * REPAIR_BELOW) {
                game.hp = game.maxHp
                previousIntegrity = game.hp
            }
            game.update(1f / 60f)
            crowdDamage += before - targets.sumOf { it.hp.toDouble() }
            game.takeVisualFxCues()
            game.takeSoundCues()
            integrityLost += max(0f, previousIntegrity - game.hp) / game.maxHp
            previousIntegrity = game.hp
            if (clearedAt < 0f && targets.all { it.dead || it.hp <= 0f }) clearedAt = game.elapsed - startedAt
        }
        val engaged = if (boss && clearedAt > 0f) clearedAt else max(1f / 60f, game.elapsed - startedAt)
        val ability = (game.characterRuntime.abilityDamage - abilityBefore).toFloat()
        val dealt = if (boss) (game.damageDealt - damageBefore).toFloat() else crowdDamage.toFloat()
        val weaponSide = dealt - ability
        return EncounterMeasure(weaponSide / engaged, ability / engaged, integrityLost)
    }

    private companion object {
        /** The tier-10 populations: the first 150 [PopulationSeeds] (binomial SE about 0.03 near P4's bound). */
        const val TOP_RUNS = 150

        /** P4: the least tier-10 win share of the fully progressed strong build. */
        const val P4_MIN_WIN = 0.80

        /** P5: the tier-10 win share band of the fully progressed ordinary build. */
        val P5_WIN = 0.30..0.85

        /** P7 in play: the least gain of character-ability damage per engaged second, maxed against 0 (Refit ×1.97). */
        const val P7_MIN_ABILITY_GAIN = 1.6f

        /**
         * P7 in play: the least gain of weapon-side damage per engaged second, maxed against 0 (Honing ×2.26 on the weapon's
         * own hits, diluted by impacts, procs and the Architect's hit cap).
         */
        const val P7_MIN_WEAPON_GAIN = 1.4f

        /** In the Architect fight a Core below this share of its integrity is repaired to full before the next step. */
        const val REPAIR_BELOW = 0.5f

        /** The crowd Drifters' integrity pool (WeaponBalanceTest's crowd arena). */
        const val CROWD_INTEGRITY = 900f

        /** The comparative scenario's canonical seed and its three coverage seeds, fixed by definition. */
        val PerceptibleSeeds: List<Int> = listOf(81_006) + List(3) { 73_199 + it * 7_919 }

        val TopRuns = HashMap<SweepArm, List<SweepRun>>()
    }
}
