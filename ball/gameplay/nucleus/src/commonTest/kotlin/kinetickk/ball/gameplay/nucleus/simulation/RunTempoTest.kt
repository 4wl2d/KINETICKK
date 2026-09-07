// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.RunTempoProfile
import kinetickk.ball.content.api.FatigueTuning
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.enemyTypeForElapsed
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunTempoTest {
    @Test
    fun edgeFatigueTuningRejectsMissingRecoveryAndDegenerateEdgeBands() {
        for (invalid in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { FatigueTuning(normalRecoveryPerSecond = invalid) }
            assertFailsWith<IllegalArgumentException> { FatigueTuning(edgeDrainPerSecond = invalid) }
            assertFailsWith<IllegalArgumentException> { FatigueTuning(edgeStrainStart = invalid) }
        }
        assertFailsWith<IllegalArgumentException> { FatigueTuning(edgeStrainStart = 1f) }
        assertFailsWith<IllegalArgumentException> { FatigueTuning(edgeStrainStart = 1.01f) }
        assertEquals(0.01f, FatigueTuning(edgeStrainStart = 0.01f).edgeStrainStart)
        assertEquals(0.99f, FatigueTuning(edgeStrainStart = 0.99f).edgeStrainStart)
    }

    @Test
    fun customTempoDrivesBossAndRenderThroughSameSnapshot() {
        val engine = MutableGameState(canonicalGameplayContent.copy(tempo = RunTempoProfile(bossAtSeconds = 400f)))
        engine.startRun()
        engine.runGrace = 1_000f
        engine.elapsed = 399f
        engine.simulateStep(0.01f)
        assertFalse(engine.bossSpawned)
        assertEquals(engine.runProgress, engine.toRenderModel().runProgress)
        engine.elapsed = 400f
        engine.simulateStep(0.01f)
        assertTrue(engine.bossSpawned)
        assertEquals(1, engine.enemies.count { it.type == EnemyType.ARCHITECT })
        assertEquals(GamePhase.RUNNING, engine.phase, "The boss clock must never end the run")
        engine.simulateStep(0.01f)
        assertEquals(1, engine.enemies.count { it.type == EnemyType.ARCHITECT })
    }

    @Test
    fun baseTempoArrivesAtTwelveMinutesAndOffersFourElitesBeforeSix() {
        val tempo = canonicalGameplayContent.tempo
        assertEquals(720f, tempo.bossAtSeconds)
        var nextElite = tempo.firstEliteAtSeconds
        repeat(3) { nextElite += tempo.eliteIntervalSeconds(nextElite) }
        assertTrue(nextElite < 360f, "Four relic opportunities must be available before minute six")
        assertTrue(tempo.spawnIntervalSeconds(600f) < tempo.spawnIntervalSeconds(100f))
        assertTrue(tempo.attackRateMultiplier(600f) > tempo.attackRateMultiplier(100f))
        assertTrue(tempo.ordinaryEnemyCap(600f) > tempo.ordinaryEnemyCap(100f))
    }

    @Test
    fun changingBossTempoScalesEncounterPhasesAndPressureTogether() {
        val base = RunTempoProfile()
        val faster = base.copy(bossAtSeconds = 360f)
        for (seconds in listOf(0f, 38f, 90f, 150f, 240f, 360f, 600f)) {
            assertEquals(base.encounterStage(seconds), faster.encounterStage(seconds * 0.5f))
            assertEquals(enemyTypeForElapsed(seconds, 0.99f, base), enemyTypeForElapsed(seconds * 0.5f, 0.99f, faster))
            assertEquals(base.attackRateMultiplier(seconds), faster.attackRateMultiplier(seconds * 0.5f))
            assertEquals(base.enemyHealthMultiplier(seconds), faster.enemyHealthMultiplier(seconds * 0.5f))
        }
    }

    @Test
    fun earnedExperienceDevelopsWeaponWithoutPointsOfInterest() {
        val engine = MutableGameState(canonicalGameplayContent)
        engine.startRun()
        repeat(17) {
            engine.phase = GamePhase.RUNNING
            engine.gainData(engine.nextLevelData.toFloat())
        }
        assertEquals(18, engine.level)
        assertEquals(10, engine.weaponLevel)
        assertTrue(engine.equippedRelics.isEmpty())
        assertEquals(0, engine.keys)
        repeat(8) {
            engine.phase = GamePhase.RUNNING
            engine.gainData(engine.nextLevelData.toFloat())
        }
        assertEquals(10, engine.weaponLevel, "Automatic amplification stops at the profile bound")
    }

    @Test
    fun tempoMultipliersReachEarnedDataAndMatter() {
        fun withTempo(tempo: RunTempoProfile) = MutableGameState(canonicalGameplayContent.copy(tempo = tempo)).apply {
            startRun()
            enemies.clear()
            pickups += Pickup(PickupType.DATA, coreX, coreY)
            resolvePickupCollection()
            killEnemyForTesting(EnemyType.DRIFTER)
        }
        val normal = withTempo(RunTempoProfile(dataPickupMultiplier = 1f, matterRewardMultiplier = 1f))
        val boosted = withTempo(RunTempoProfile(dataPickupMultiplier = 2f, matterRewardMultiplier = 2f))
        assertEquals(normal.data * 2, boosted.data)
        assertEquals(normal.runMatter * 2, boosted.runMatter)
    }
}
