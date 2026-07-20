// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.characterization

import kinetickk.core.content.*
import kinetickk.core.profile.api.DamageNumberFormat

import kinetickk.feature.gameplay.domain.model.*
import kinetickk.feature.gameplay.domain.protocol.VisualFxCue
import kinetickk.feature.gameplay.domain.simulation.*
import kotlin.math.roundToLong
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class GameScenarioTest {
    @Test
    fun dashAddsVelocityAndHeat() {
        val engine = GameScenario(seed = 1, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.startRun()

        engine.requestDash()
        engine.update(1f / 60f)

        assertTrue(engine.speed > 500f)
        assertTrue(engine.heat > 30f)
        assertTrue(engine.dashPhaseTime > 0f)
    }

    @Test
    fun repeatedDashesTriggerOverheat() {
        val engine = GameScenario(seed = 2, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.startRun()

        repeat(3) {
            engine.requestDash()
            engine.update(1f / 60f)
        }

        assertTrue(engine.overheated)
        assertEquals(GameScenario.MAX_HEAT, engine.heat)
    }

    @Test
    fun resizingARunKeepsThePointerInsideTheArenaAtTheSameRelativeAim() {
        val engine = GameScenario(seed = 20, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.startRun()
        engine.updatePointer(1200f, 700f)

        engine.resize(720f, 540f)

        assertEquals(675f, engine.pointerX, 0.001f)
        assertEquals(525f, engine.pointerY, 0.001f)
        assertTrue(engine.pointerX in 0f..engine.screenWidth)
        assertTrue(engine.pointerY in 0f..engine.screenHeight)
    }

    @Test
    fun touchingSingularityEndsTheRunAfterGracePeriod() {
        val engine = GameScenario(seed = 3, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.startRun()
        engine.updatePointer(640f, 360f)

        repeat(40) { engine.update(1f / 24f) }

        assertEquals(GamePhase.GAME_OVER, engine.phase)
        assertEquals("SINGULARITY CONTACT", engine.message)
    }

    @Test
    fun runClockFormatsForHud() {
        assertEquals("00:00", formatRunTime(0f))
        assertEquals("03:07", formatRunTime(187.9f))
        assertEquals("20:00", formatRunTime(GameScenario.RUN_DURATION_SECONDS))
    }

    @Test
    fun exitingAMidRunSessionBanksMatterExactlyOnce() {
        val engine = GameScenario(seed = 41, initialMatter = 10)
        engine.startRun()
        engine.runMatter = 37L

        engine.exitRun()
        val firstUpdate = requireNotNull(engine.takeProgressUpdate())
        engine.exitRun()

        assertEquals(GamePhase.PAUSED, engine.phase)
        assertEquals(37L, firstUpdate.bankedMatter)
        assertEquals(47L, engine.totalMatter)
        assertEquals(null, engine.takeProgressUpdate())
    }

    @Test
    fun damageEventsEmitTypedVisualCuesAndRespectTheVisibilitySetting() {
        val enabledEngine = GameScenario(seed = 22, initialMatter = 0)
        val enabledTarget = enabledEngine.addEnemyForTesting(x = 20f, y = 30f, hp = 100_000f)

        val appliedDamage = enabledEngine.damageEnemyForTesting(enabledTarget, 14_000.6f)

        val number = enabledEngine.takeVisualFxCues()
            .filterIsInstance<VisualFxCue.DamageNumberAdded>()
            .single()
        assertEquals(appliedDamage.roundToLong(), number.amount)
        assertFalse(number.critical)
        assertEquals("14.4K", formatDamageNumber(number.amount, DamageNumberFormat.COMPACT))
        assertEquals(number.amount.toString(), formatDamageNumber(number.amount, DamageNumberFormat.FULL))

        val disabledEngine = GameScenario(seed = 23, initialMatter = 0)
        disabledEngine.resize(1280f, 720f)
        disabledEngine.applyPreferences(disabledEngine.settings.copy(damageNumbers = false))
        val disabledTarget = disabledEngine.addEnemyForTesting(x = 20f, y = 30f, hp = 100_000f)

        disabledEngine.damageEnemyForTesting(disabledTarget, 500f)

        assertTrue(
            disabledEngine.takeVisualFxCues()
                .filterIsInstance<VisualFxCue.DamageNumberAdded>()
                .isEmpty(),
        )
    }

    @Test
    fun openingEnemyCountIncreasesWithRebirthTier() {
        val baseline = GameScenario(seed = 8, initialMatter = 0, initialRebirthLevel = 0)
        val maximum = GameScenario(
            seed = 8,
            initialMatter = 0,
            initialRebirthLevel = RebirthProgression.MAX_LEVEL,
        )

        baseline.startRun()
        maximum.startRun()

        assertEquals(RebirthProgression.profile(0).openingEnemyCount, baseline.enemies.size)
        assertTrue(baseline.enemies.all { it.type == EnemyType.DRIFTER })
        assertEquals(RebirthProgression.profile(RebirthProgression.MAX_LEVEL).openingEnemyCount, maximum.enemies.size)
        assertTrue(maximum.enemies.size > baseline.enemies.size)
    }

}
