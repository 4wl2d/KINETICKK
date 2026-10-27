// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Long scripted character scenarios at ability power 1. The balance script is one fixed 90 s fight
 * for every character: the pointer traces a figure eight and a Dash fires every 1.5 s (two Pentagon
 * beats) while a pack of three Drifters (two 60-integrity flankers and a 240-integrity middle one)
 * appears at a random bearing within a radian of the aim, 140 to 280 ahead, so the Dash meets a pack
 * only part of the time; at most twelve enemies stay. Every 3.75 s a Sniper bolt with the content's
 * damage arrives head-on and a short brake answers it; Drifters that touch a slow Core deal their
 * content contact damage. The jittered variant moves every Dash and every brake by 0.05 to 0.15 s.
 * Each variant averages 24 seeded fights. Only the mechanic's own output is measured.
 *
 * Accepted rule for the defensive characters: the Prism and the Diamond prevent damage instead of dealing
 * it, so SPEC 8.2's ±40 % ability-output band covers only the ten offensive characters. Content damage caps
 * what a defensive mechanic can prevent (their prevented totals stay near a fifth of the offensive median:
 * see the report), so the two are judged by the defensive criterion instead: in the same fights the Prism
 * loses at most 60 % and the Diamond at most 75 % of the integrity the Orb loses
 * ([prismAndDiamondKeepOffMuchOfTheContentDamageTheOrbTakesInTheSameFight]).
 */
class CharacterBalanceTest {
    private class Outcome(val dealt: Double, val prevented: Double, val hpLost: Float, val kills: Int, val dashes: Int,
        val overheated: Boolean)

    @Test
    fun everyOffensiveCharactersAbilityOutputSitsWithinFortyPercentOfTheMedianWithExactAndJitteredTiming() {
        val outside = mutableListOf<String>()
        for ((jittered, outcomes) in balanceRuns) {
            val outputs = OFFENSIVE.associateWith { shape -> outcomes.getValue(shape).dealt }
            val median = median(outputs.values)
            // The Prism and the Diamond prevent damage instead: reported here, accepted by the defense test.
            val defensive = DEFENSIVE.joinToString { shape ->
                val prevented = outcomes.getValue(shape).prevented
                "$shape prevented=${"%.1f".format(prevented)} (${"%.2f".format(prevented / median)})"
            }
            println("CHARACTER-BALANCE jittered=$jittered median=${"%.1f".format(median)} " +
                outputs.entries.joinToString { (shape, value) -> "$shape=${"%.1f".format(value)} (${"%.2f".format(value / median)})" } +
                "; $defensive")
            outputs.forEach { (shape, value) ->
                if (value !in median * 0.6..median * 1.4) outside += "$shape=$value (median $median, jittered=$jittered)"
            }
        }
        assertTrue(outside.isEmpty(), "ability output outside ±40 % of the median: $outside")
    }

    /** The defensive characters' acceptance: each keeps off its share of the integrity the Orb loses. */
    @Test
    fun prismAndDiamondKeepOffMuchOfTheContentDamageTheOrbTakesInTheSameFight() {
        for ((jittered, outcomes) in balanceRuns) {
            val orb = outcomes.getValue(CoreShape.ORB)
            val prism = outcomes.getValue(CoreShape.PRISM)
            val diamond = outcomes.getValue(CoreShape.DIAMOND)
            println("CHARACTER-DEFENSE jittered=$jittered ORB lost=${orb.hpLost} " +
                "PRISM lost=${prism.hpLost} prevented=${prism.prevented} DIAMOND lost=${diamond.hpLost} prevented=${diamond.prevented}")
            assertTrue(orb.hpLost > 0f, "the fight hurts a Core without a defensive mechanic")
            assertTrue(prism.hpLost <= orb.hpLost * PRISM_LOSS_SHARE,
                "the Prism lost ${prism.hpLost} against the Orb's ${orb.hpLost} (jittered=$jittered)")
            assertTrue(diamond.hpLost <= orb.hpLost * DIAMOND_LOSS_SHARE,
                "the Diamond lost ${diamond.hpLost} against the Orb's ${orb.hpLost} (jittered=$jittered)")
            assertTrue(prism.prevented > 0.0 && diamond.prevented > 0.0)
        }
    }

    /**
     * Refit 10 for every character (economy §4.6): the same fights keep the offensive characters within ±40 % of
     * their median and the Prism and the Diamond within their share of the Orb's integrity loss.
     */
    @Test
    fun everyCharacterAtRefitTenStaysInBand() {
        val outside = mutableListOf<String>()
        for ((jittered, outcomes) in refitRuns) {
            val outputs = OFFENSIVE.associateWith { shape -> outcomes.getValue(shape).dealt }
            val median = median(outputs.values)
            val orb = outcomes.getValue(CoreShape.ORB)
            val prism = outcomes.getValue(CoreShape.PRISM)
            val diamond = outcomes.getValue(CoreShape.DIAMOND)
            println("CHARACTER-BALANCE refit=10 jittered=$jittered median=${"%.1f".format(median)} " +
                outputs.entries.joinToString { (shape, value) -> "$shape=${"%.1f".format(value)} (${"%.2f".format(value / median)})" } +
                "; ORB lost=${orb.hpLost} PRISM lost=${prism.hpLost} DIAMOND lost=${diamond.hpLost}")
            outputs.forEach { (shape, value) ->
                if (value !in median * 0.6..median * 1.4) outside += "$shape=$value (median $median, jittered=$jittered)"
            }
            if (prism.hpLost > orb.hpLost * PRISM_LOSS_SHARE) outside += "PRISM lost ${prism.hpLost} of the Orb's ${orb.hpLost} (jittered=$jittered)"
            if (diamond.hpLost > orb.hpLost * DIAMOND_LOSS_SHARE) outside += "DIAMOND lost ${diamond.hpLost} of the Orb's ${orb.hpLost} (jittered=$jittered)"
        }
        assertTrue(outside.isEmpty(), "Refit 10 characters outside their band: $outside")
    }

    @Test
    fun aPrismRammingPacksWhileShotAtLosesIntegrityWithinAMinute() {
        // Swarmling packs to ram every second and one aimed bolt per second: facets cannot keep up.
        val outcome = scripted(CoreShape.PRISM, 60f, keepAlive = false) { state, frame ->
            val time = frame / 60f
            state.updatePointer(state.screenWidth * 0.5f + cos(time * 1.4f) * 300f, state.screenHeight * 0.5f + sin(time * 1.4f) * 220f)
            if (frame % 60 == 0) {
                val heading = atan2(state.velocityY, state.velocityX)
                repeat(4) { index ->
                    val angle = heading + (index - 1.5f) * 0.25f
                    state.addEnemyForTesting(state.coreX + cos(angle) * 160f, state.coreY + sin(angle) * 160f, hp = 9f,
                        radius = 10f, type = EnemyType.SWARMLING)
                }
            }
            if (frame % 60 == 30) aimShot(state, 150f, boltDamage(state, EnemyType.SHOOTER), frame)
        }
        assertTrue(outcome.hpLost > 0f, "the Prism kept full integrity for a minute (prevented ${outcome.prevented})")
        assertTrue(outcome.kills > 0, "the Prism rammed packs")
    }

    @Test
    fun aPentagonDashingOnEveryBeatOverheatsOrSlowsBelowOneDashPerOnePointTwoSeconds() {
        val outcome = scripted(CoreShape.PENTAGON, 30f) { state, frame ->
            val time = frame / 60f
            state.updatePointer(state.screenWidth * 0.5f + cos(time * 1.4f) * 280f, state.screenHeight * 0.5f + sin(time * 1.4f) * 200f)
            // A Dash press on every beat (0.75 s), when the Dash is ready.
            if (state.isOnBeat() && state.beatOffset() < 0.02f && state.buildDashReady()) state.requestDash()
        }
        val rate = outcome.dashes / 30f
        assertTrue(outcome.overheated || rate <= 1f / 1.2f, "dashed ${outcome.dashes} times in 30 s without overheating")
    }

    /**
     * The balance script. Packs appear at a bearing within [PACK_BEARING_SPREAD] of the aim; with
     * [jittered] every Dash and every brake moves 3 to 9 frames (0.05 to 0.15 s) early or late.
     */
    private class BalanceScript(private val jittered: Boolean, seed: Int) : (MutableGameState, Int) -> Unit {
        private val random = Random(seed)
        private var nextDash = DASH_FRAMES + offset()
        private var nextShot = SHOT_FRAMES / 2
        private var brakeFrom = Int.MIN_VALUE
        private var brakeUntil = Int.MIN_VALUE

        private fun offset(): Int = if (!jittered) 0 else random.nextInt(3, 10) * if (random.nextBoolean()) 1 else -1

        override fun invoke(state: MutableGameState, frame: Int) {
            val angle = frame / 60f * 1.4f
            state.updatePointer(state.screenWidth * 0.5f + cos(angle) * 300f, state.screenHeight * 0.5f + sin(angle * 2f) * 220f)
            if (frame == nextDash) {
                nextDash += DASH_FRAMES + offset()
                state.requestDash()
                val aim = atan2(state.cameraY + state.pointerY - state.screenHeight * 0.5f - state.coreY,
                    state.cameraX + state.pointerX - state.screenWidth * 0.5f - state.coreX)
                val bearing = aim + (random.nextFloat() * 2f - 1f) * PACK_BEARING_SPREAD
                val distance = PACK_NEAR + random.nextFloat() * (PACK_FAR - PACK_NEAR)
                repeat(3) { index ->
                    val direction = bearing + (index - 1) * 0.35f
                    state.addEnemyForTesting(state.coreX + cos(direction) * distance, state.coreY + sin(direction) * distance,
                        hp = if (index == 1) 240f else 60f)
                }
                // Old stragglers leave so the field stays bounded.
                while (state.enemies.size > 12) state.enemies.removeAt(0)
            }
            if (frame == nextShot) {
                nextShot += SHOT_FRAMES
                aimShot(state, 150f, boltDamage(state, EnemyType.SNIPER), frame)
                // The bolt closes at its speed plus the Core's (about 0.14 s): brake from 0.08 s and hold 0.15 s.
                brakeFrom = frame + 5 + offset()
                brakeUntil = brakeFrom + 9
            }
            state.setBrake(frame in brakeFrom..brakeUntil)
        }
    }

    private companion object {
        val content = canonicalGameplayContent.copy(tempo = canonicalGameplayContent.tempo.copy(bossAtSeconds = 1_000_000f))
        /** The defensive characters: outside the ability-output band, accepted by their loss share of the Orb's. */
        val DEFENSIVE = listOf(CoreShape.PRISM, CoreShape.DIAMOND)
        val OFFENSIVE = CoreShape.entries - DEFENSIVE.toSet()

        /**
         * Both balance variants (exact timing, then jittered timing) for every character, each the mean of
         * [BALANCE_SEEDS] fights with their own pack bearings and timing, run once per test JVM.
         */
        val balanceRuns: Map<Boolean, Map<CoreShape, Outcome>> by lazy { balance(refit = 0) }

        /** [balanceRuns] with every character at Refit 10. */
        val refitRuns: Map<Boolean, Map<CoreShape, Outcome>> by lazy { balance(refit = content.upgrades.maxLevel) }

        fun balance(refit: Int): Map<Boolean, Map<CoreShape, Outcome>> =
            listOf(false, true).associateWith { jittered ->
                CoreShape.entries.associateWith { shape ->
                    val fights = BALANCE_SEEDS.map { seed ->
                        scripted(shape, 90f, seed = seed, refit = refit, script = BalanceScript(jittered, seed))
                    }
                    Outcome(fights.sumOf { it.dealt } / fights.size, fights.sumOf { it.prevented } / fights.size,
                        fights.sumOf { it.hpLost.toDouble() }.toFloat() / fights.size, fights.sumOf { it.kills } / fights.size,
                        fights.sumOf { it.dashes } / fights.size, fights.any { it.overheated })
                }
            }

        const val KEPT_INTEGRITY = 1_000f
        val BALANCE_SEEDS = 4_241..4_264
        const val DASH_FRAMES = 90
        const val SHOT_FRAMES = 225
        const val SHOT_SPEED = 400f
        const val PACK_BEARING_SPREAD = 1f
        const val PACK_NEAR = 140f
        const val PACK_FAR = 280f
        /** The most of the Orb's integrity loss the Prism and the Diamond may lose in the same fights. */
        const val PRISM_LOSS_SHARE = 0.6f
        const val DIAMOND_LOSS_SHARE = 0.75f

        fun median(values: Collection<Double>): Double {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
        }

        /**
         * Runs [script] for [seconds]. With [keepAlive] the Core's integrity is raised to [KEPT_INTEGRITY]
         * and refilled every step, so the fight never ends early; without it the run may end.
         */
        fun scripted(
            shape: CoreShape,
            seconds: Float,
            keepAlive: Boolean = true,
            seed: Int = 4_242,
            refit: Int = 0,
            script: (state: MutableGameState, frame: Int) -> Unit,
        ): Outcome {
            val state = MutableGameState(content, seed = seed)
            state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
            state.resize(1_280f, 720f)
            state.coreShape = shape
            state.characterUpgradeLevels[shape.ordinal] = refit
            state.startRun()
            state.enemies.clear()
            state.nextEliteAt = Float.MAX_VALUE
            state.nextLevelData = Int.MAX_VALUE
            if (keepAlive) {
                state.maxHp = KEPT_INTEGRITY
                state.hp = state.maxHp
            }
            var hpLost = 0f
            var dashes = 0
            var overheated = false
            var frame = 0
            val startKills = state.kills
            while (state.elapsed < seconds && state.phase == GamePhase.RUNNING) {
                state.spawnBudget = 0f
                state.overdriveCharge = 0f
                state.overdriveTime = 0f
                state.weaponLevel = 1
                state.runGrace = 1f
                // No trials: their rewards would open choices and shift the script.
                state.quietArenaForTesting()
                val dashSequence = state.characterRuntime.dashSequence
                script(state, frame)
                val before = state.hp
                state.update(1f / 60f)
                hpLost += (before - state.hp).coerceAtLeast(0f)
                dashes += state.characterRuntime.dashSequence - dashSequence
                overheated = overheated || state.overheated
                if (keepAlive) state.hp = state.maxHp
                state.takeSoundCues()
                state.takeVisualFxCues()
                state.takeProgressUpdate()
                frame++
            }
            if (keepAlive) assertEquals(GamePhase.RUNNING, state.phase, "$shape left the run: ${state.message}")
            assertEquals(1f, state.abilityPower(), 1e-3f, "the scenario keeps ability power 1")
            return Outcome(state.characterRuntime.abilityDamage, state.characterRuntime.damagePrevented, hpLost,
                state.kills - startKills, dashes, overheated)
        }

        /** What a bolt of an [type] enemy fired now deals (content shot damage through the run growth). */
        fun boltDamage(state: MutableGameState, type: EnemyType): Float = state.hostileDamage(null, type.profile.shotDamage)

        /** A hostile bolt fired head-on from [distance] ahead of the Core along its heading. */
        fun aimShot(state: MutableGameState, distance: Float, damage: Float, frame: Int) {
            val speed = hypot(state.velocityX, state.velocityY)
            val heading = if (speed > 1f) atan2(state.velocityY, state.velocityX) else frame * 0.37f
            val x = state.coreX + cos(heading) * distance
            val y = state.coreY + sin(heading) * distance
            state.projectiles += Projectile(x = x, y = y, vx = -cos(heading) * SHOT_SPEED, vy = -sin(heading) * SHOT_SPEED,
                radius = 6f, life = 2f, hostile = true, damage = damage).apply { previousX = x; previousY = y }
        }
    }
}
