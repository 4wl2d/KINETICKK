// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Long scripted character scenarios at ability power 1. The balance script is one fixed 90 s fight
 * for every character: the pointer traces a figure eight, a Dash fires every 1.5 s (two Pentagon
 * beats) into a pack of three Drifters that appears along its aim (two 60-integrity flankers and a
 * 240-integrity middle one), at most twelve enemies stay, and every 3.75 s a heavy head-on bolt
 * arrives with a short brake timed to meet it. Only the mechanic's own output is measured.
 */
class CharacterBalanceTest {
    /** Ability power stays 1: the run clock never advances progress, no level-ups, no Overdrive. */
    private val content = canonicalGameplayContent.copy(tempo = canonicalGameplayContent.tempo.copy(bossAtSeconds = 1_000_000f))

    private class Outcome(val dealt: Double, val prevented: Double, val hpLost: Float, val kills: Int, val dashes: Int,
        val overheated: Boolean)

    /**
     * Runs [script] for [seconds]. With [keepAlive] the Core's integrity is raised to [KEPT_INTEGRITY]
     * and refilled every step, so no heavy bolt ends the run; without it the run may end.
     */
    private fun scripted(
        shape: CoreShape,
        seconds: Float,
        keepAlive: Boolean = true,
        script: (state: MutableGameState, frame: Int) -> Unit = ::balanceScript,
    ): Outcome {
        val state = MutableGameState(content, seed = 4_242)
        state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
        state.resize(1_280f, 720f)
        state.coreShape = shape
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
            state.pointsOfInterest = emptyList()
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

    @Test
    fun everyCharactersAbilityOutputSitsWithinFortyPercentOfTheMedian() {
        val outputs = CoreShape.entries.associateWith { shape ->
            val outcome = scripted(shape, 90f)
            // Defensive mechanics are measured by what they prevent (1 integrity = 1 damage).
            if (shape == CoreShape.PRISM || shape == CoreShape.DIAMOND) outcome.prevented else outcome.dealt
        }
        val sorted = outputs.values.sorted()
        val median = (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        println("CHARACTER-BALANCE median=${"%.1f".format(median)} " +
            outputs.entries.joinToString { (shape, value) -> "$shape=${"%.1f".format(value)} (${"%.2f".format(value / median)})" })
        outputs.forEach { (shape, value) ->
            assertTrue(value in median * 0.6..median * 1.4,
                "$shape ability output $value is outside ±40 % of the median $median (${outputs.entries.joinToString()})")
        }
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
            if (frame % 60 == 30) aimShot(state, 150f, 10f, frame)
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

    private companion object {
        const val KEPT_INTEGRITY = 1_000f
        const val DASH_FRAMES = 90
        const val SHOT_FRAMES = 225
        const val SHOT_DAMAGE = 150f
        const val SHOT_SPEED = 400f

        /** A hostile bolt fired head-on from [distance] ahead of the Core along its heading. */
        fun aimShot(state: MutableGameState, distance: Float, damage: Float, frame: Int) {
            val speed = hypot(state.velocityX, state.velocityY)
            val heading = if (speed > 1f) atan2(state.velocityY, state.velocityX) else frame * 0.37f
            val x = state.coreX + cos(heading) * distance
            val y = state.coreY + sin(heading) * distance
            state.projectiles += Projectile(x = x, y = y, vx = -cos(heading) * SHOT_SPEED, vy = -sin(heading) * SHOT_SPEED,
                radius = 6f, life = 2f, hostile = true, damage = damage).apply { previousX = x; previousY = y }
        }

        fun balanceScript(state: MutableGameState, frame: Int) {
            val angle = frame / 60f * 1.4f
            state.updatePointer(state.screenWidth * 0.5f + cos(angle) * 300f, state.screenHeight * 0.5f + sin(angle * 2f) * 220f)
            if (frame % DASH_FRAMES == 0 && frame > 0) {
                state.requestDash()
                val aim = atan2(state.cameraY + state.pointerY - state.screenHeight * 0.5f - state.coreY,
                    state.cameraX + state.pointerX - state.screenWidth * 0.5f - state.coreX)
                repeat(3) { index ->
                    val direction = aim + (index - 1) * 0.35f
                    state.addEnemyForTesting(state.coreX + cos(direction) * 140f, state.coreY + sin(direction) * 140f,
                        hp = if (index == 1) 240f else 60f)
                }
                // Old stragglers leave so the field stays bounded.
                while (state.enemies.size > 12) state.enemies.removeAt(0)
            }
            val shotFrame = frame % SHOT_FRAMES
            if (shotFrame == SHOT_FRAMES / 2) aimShot(state, 150f, SHOT_DAMAGE, frame)
            // The bolt closes at its speed plus the Core's (about 0.14 s): brake from 0.08 s and hold 0.15 s.
            state.setBrake(shotFrame in (SHOT_FRAMES / 2 + 5)..(SHOT_FRAMES / 2 + 14))
        }
    }
}
