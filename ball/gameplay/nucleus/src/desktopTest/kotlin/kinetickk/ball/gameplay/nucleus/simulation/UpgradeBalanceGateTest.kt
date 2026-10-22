// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Refit and Honing cannot buy past the Rebirth gates (economy §4.6). J1-J4 replay RebirthBalanceGateTest's
 * setups and thresholds with maxed upgrades on the same harness and seeds; J5 shows the upgrades are
 * perceptible in a scripted encounter with equal resources.
 *
 * - J1 (D at max): tier 10, strong build + Pentagon Refit 10 + Honing 10 on every owned weapon: at most 3/5
 *   wins and a median survival of at least 480 s.
 * - J2 (G at max): tier 10, full Matrix Pentagon/Arc Coil + the same upgrades: fewer than 5/5 wins.
 * - J3 (E's ceiling): tier 8, half Matrix + Orb Refit 5 + Honing 5 on every owned weapon: at least 4/5 losses.
 * - J4 (upgrades are not progression): tier 6, no Matrix, Orb Refit 10 + Honing 10 on every owned weapon: no win.
 * - J5 (perceptible): Orb/Flux Wake and Pentagon/Arc Coil, maxed against 0, in a 25 s crowd and a 180 s Architect
 *   fight on four fixed seeds: weapon-side damage per engaged second +12 %, character-ability damage per engaged
 *   second +8 %, and no more integrity lost (as a share of maximum integrity).
 *
 * Knob order if a gate fails (economy §4.6): weaponPowerPerLevel 0.03 → 0.025, characterPowerPerLevel
 * 0.02 → 0.015, characterIntegrityPerLevel 2 → 1.5, then the signature step that breaks a band.
 */
class UpgradeBalanceGateTest {
    @Test fun maxedUpgradesKeepTierTenSurvivableButRareForTheStrongBuild() {
        val outcomes = EconomyGateSeeds.map { seed ->
            play(seed, maxed(GateMatrix.FULL, tier = 10, CoreShape.PENTAGON, WeaponId.ARC_COIL, level = 10), StrongRelics)
        }
        report("UPGRADE_GATE J1 tier=10 strong+", outcomes)
        val median = outcomes.map { it.seconds }.gateMedian()
        assertTrue(median >= 480f, "tier 10 must stay survivable for a maxed strong build: median $median s, ${outcomes.summary()}")
        assertTrue(outcomes.count { it.won } <= 3, "maxed upgrades must not make tier 10 routine: ${outcomes.summary()}")
    }

    @Test fun maxedUpgradesDoNotMakeTierTenAFormalityForAFullMatrix() {
        val outcomes = EconomyGateSeeds.map { seed ->
            play(seed, maxed(GateMatrix.FULL, tier = 10, CoreShape.PENTAGON, WeaponId.ARC_COIL, level = 10))
        }
        report("UPGRADE_GATE J2 tier=10 signature+", outcomes)
        assertTrue(outcomes.count { it.won } < outcomes.size, "tier 10 must not be a formality with maxed upgrades: ${outcomes.summary()}")
    }

    @Test fun halfUpgradesDoNotCarryHalfAMatrixThroughTierEight() {
        val outcomes = EconomyGateSeeds.map { seed ->
            play(seed, maxed(GateMatrix.HALF, tier = 8, CoreShape.ORB, WeaponId.FLUX_WAKE, level = 5))
        }
        report("UPGRADE_GATE J3 tier=8 half+", outcomes)
        assertTrue(outcomes.count { !it.won } >= 4, "tier 8 must outgrow half the Matrix with half the upgrades: ${outcomes.summary()}")
    }

    @Test fun maxedUpgradesCannotReplaceTheMatrixAtTierSix() {
        val outcomes = EconomyGateSeeds.map { seed ->
            play(seed, maxed(GateMatrix.NONE, tier = 6, CoreShape.ORB, WeaponId.FLUX_WAKE, level = 10))
        }
        report("UPGRADE_GATE J4 tier=6 fresh+maxed", outcomes)
        assertTrue(outcomes.none { it.won }, "tier 6 must still demand the Matrix: ${outcomes.summary()}")
    }

    @Test fun maxedUpgradesArePerceptibleInEqualEncounters() {
        val failures = mutableListOf<String>()
        for ((shape, weapon) in listOf(CoreShape.ORB to WeaponId.FLUX_WAKE, CoreShape.PENTAGON to WeaponId.ARC_COIL)) {
            val plain = Totals()
            val upgraded = Totals()
            for (seed in PerceptibleSeeds) for (boss in listOf(false, true)) {
                val base = encounter(shape, weapon, level = 0, boss = boss, seed = seed)
                val maxed = encounter(shape, weapon, level = canonicalGameplayContent.upgrades.maxLevel, boss = boss, seed = seed)
                println("UPGRADE_J5 $shape/$weapon seed=$seed ${if (boss) "BOSS" else "CROWD"} base=$base maxed=$maxed")
                plain += base
                upgraded += maxed
            }
            val weaponGain = upgraded.weaponRate / plain.weaponRate
            val abilityGain = upgraded.abilityRate / plain.abilityRate
            println("UPGRADE_GATE J5 $shape/$weapon weapon=${weaponGain}x ability=${abilityGain}x " +
                "integrityLost=${upgraded.integrityLost}/${plain.integrityLost}")
            if (weaponGain < 1.12f) failures += "$shape/$weapon weapon-side damage only ${weaponGain}x"
            if (abilityGain < 1.08f) failures += "$shape/$weapon ability damage only ${abilityGain}x"
            if (upgraded.integrityLost > plain.integrityLost) {
                failures += "$shape/$weapon lost more integrity maxed (${upgraded.integrityLost}) than at 0 (${plain.integrityLost})"
            }
        }
        assertTrue(failures.isEmpty(), "maxed upgrades must be perceptible: $failures")
    }

    private data class Outcome(val seed: Int, val won: Boolean, val seconds: Float, val reason: String) {
        override fun toString(): String = "$seed:${if (won) "WIN" else "LOSS"}@${seconds.toInt()}s" + if (won) "" else "($reason)"
    }

    private fun List<Outcome>.summary(): String = joinToString(" ")

    private fun report(label: String, outcomes: List<Outcome>) = println("$label ${outcomes.summary()}")

    /** A RebirthBalanceGateTest setup with Refit [level] for [shape] and Honing [level] on every owned weapon. */
    private fun maxed(matrix: GateMatrix, tier: Int, shape: CoreShape, weapon: WeaponId, level: Int): GameplayProfileSnapshot =
        gateProfile(matrix, tier, shape, weapon, upgrades = gateUpgrades(shape, level, gateOwnedWeapons(weapon), level))

    private fun play(seed: Int, profile: GameplayProfileSnapshot, relics: List<RelicId> = emptyList()): Outcome {
        val state = playGateRun(seed, profile, NoPoiBot(), relics)
        return Outcome(seed, state.phase == GamePhase.VICTORY, state.elapsed, state.message)
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
     */
    private fun encounter(shape: CoreShape, weapon: WeaponId, level: Int, boss: Boolean, seed: Int): EncounterMeasure {
        val game = MutableGameState(canonicalGameplayContent, seed = seed).apply {
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
            nextPointOfferIndex = ((content.pointsOfInterest.lastOfferAt - content.pointsOfInterest.firstOfferAt) /
                content.pointsOfInterest.offerInterval).toInt() + 1
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
            val radius = if (boss) 0f else 170f
            game.spawnedEnemy(if (boss) EnemyType.ARCHITECT else EnemyType.DRIFTER, cos(angle) * radius, sin(angle) * radius)
                .also { game.enemies += it }
        }
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
            val desiredHeading = atan2(accelerationY, accelerationX)
            heading += atan2(sin(desiredHeading - heading), cos(desiredHeading - heading)).coerceIn(-0.08f, 0.08f)
            val distance = ((length(accelerationX, accelerationY) - 92f) / game.magnetStrength).coerceIn(65f, 480f)
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
            game.update(1f / 60f)
            game.takeVisualFxCues()
            game.takeSoundCues()
            integrityLost += max(0f, previousIntegrity - game.hp) / game.maxHp
            previousIntegrity = game.hp
            if (clearedAt < 0f && targets.all { it.dead || it.hp <= 0f }) clearedAt = game.elapsed - startedAt
        }
        val engaged = if (clearedAt > 0f) clearedAt else max(1f / 60f, game.elapsed - startedAt)
        val ability = (game.characterRuntime.abilityDamage - abilityBefore).toFloat()
        val weaponSide = (game.damageDealt - damageBefore).toFloat() - ability
        return EncounterMeasure(weaponSide / engaged, ability / engaged, integrityLost)
    }

    private companion object {
        /** RebirthBalanceGateTest's strong build Relics. */
        val StrongRelics: List<RelicId> =
            listOf(RelicId.STORMHEART_CAPACITOR, RelicId.PANDEMIC_CODEX, RelicId.THUNDER_CHOIR, RelicId.CINDER_CROWN)

        /** The comparative scenario's canonical seed and its three coverage seeds, fixed by definition. */
        val PerceptibleSeeds: List<Int> = listOf(81_006) + List(3) { 73_199 + it * 7_919 }
    }
}
