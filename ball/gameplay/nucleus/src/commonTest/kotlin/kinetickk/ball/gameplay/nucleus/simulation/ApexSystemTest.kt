// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.model.ApexCell
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.WeaponRuntime
import kinetickk.ball.gameplay.nucleus.model.weaponTaken
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.ApexProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyControlBits
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The Apex system (combat-final §5.1, §5.2): unlock, cast, Data charge, strikes, bolt budget, shots and projection. */
class ApexSystemTest {
    @Test
    fun theApexUnlocksReadyWhenTheRunReachesItsLevelCapAndDataThenChargesIt() {
        val state = arena()
        val cap = state.content.tempo.maximumLevel
        state.level = cap - 1
        state.nextLevelData = 10
        assertFalse(state.apexUnlocked)
        state.gainData(25f)

        assertEquals(cap, state.level)
        assertEquals(0, state.data, "the capped level holds no Data")
        assertTrue(state.apexUnlocked)
        assertTrue(state.apexReady(), "ready on unlock")
        assertEquals(state.elapsed, state.skillRuntime.apex(ApexCell.READY_AT))
        assertEquals(APEX_ONLINE_MESSAGE, state.message)
        assertTrue(GameplayAudioCue.APEX in state.takeSoundCues())

        // Past the cap Data still counts as collected but never levels or fills the bar.
        val collected = state.dataCollected
        val pending = state.pendingLevelChoices
        state.gainData(5_001f)
        assertEquals(cap, state.level)
        assertEquals(0, state.data)
        assertEquals(pending, state.pendingLevelChoices)
        assertEquals(collected + 5_001L, state.dataCollected)
        assertEquals(0, state.creditData(3f), "credit at the cap adds nothing to level with")
    }

    @Test
    fun dataAtTheCapTakesThreeHundredthsOfASecondPerPointAndNeverBringsTheApexBelowThirtySeconds() {
        val state = arena()
        state.level = state.content.tempo.maximumLevel
        // Locked: Data at the cap charges nothing.
        state.gainData(100f)
        assertEquals(0f, state.skillRuntime.apex(ApexCell.REFUNDED))
        state.unlockApex()
        // Ready: nothing to recharge.
        state.gainData(100f)
        assertEquals(0f, state.skillRuntime.apex(ApexCell.REFUNDED))

        state.castNow()
        val cast = state.elapsed
        assertEquals(cast + 60f, state.skillRuntime.apex(ApexCell.READY_AT))
        state.gainData(100f)
        assertEquals(3f, state.skillRuntime.apex(ApexCell.REFUNDED), 1e-4f)
        assertEquals(cast + 57f, state.skillRuntime.apex(ApexCell.READY_AT), 1e-4f)
        // The bank holds the floor: at most cooldown − minimum (30 s) per cycle, never before cast + 30 s.
        state.gainData(10_000f)
        assertEquals(cast + 30f, state.skillRuntime.apex(ApexCell.READY_AT), 1e-4f)
        assertEquals(30f, state.skillRuntime.apex(ApexCell.REFUNDED), 1e-3f)
        assertEquals(0f, state.refundApex(5f))
        // A new cycle refunds again from zero.
        state.elapsed = cast + 30f
        assertTrue(state.apexReady())
        state.castNow()
        assertEquals(0f, state.skillRuntime.apex(ApexCell.REFUNDED))
        assertEquals(0.03f * 7f, state.refundApex(0.03f * 7f), 1e-5f)
    }

    @Test
    fun aCastRunsForItsDurationRechargesForSixtySecondsAndFillsTheBudgets() {
        for (shape in CoreShape.entries) {
            val state = arena(shape)
            state.unlockNow()
            val definition = state.apexDefinition
            state.requestApex()
            assertEquals(MutableGameState.SKILL_INPUT_BUFFER_SECONDS, state.apexBufferTime)
            state.updateApex(MutableGameState.FIXED_STEP)

            val runtime = state.skillRuntime
            assertEquals(0f, state.apexBufferTime, "$shape spent the buffer")
            assertEquals(state.elapsed + definition.durationSeconds, runtime.apex(ApexCell.ACTIVE_UNTIL), "$shape duration")
            assertEquals(state.elapsed + 60f, runtime.apex(ApexCell.READY_AT), "$shape cooldown")
            assertEquals(definition.bossStrikes.toFloat(), runtime.apex(ApexCell.STRIKES_LEFT), "$shape strikes")
            assertEquals(definition.boltBudget.toFloat(), runtime.apex(ApexCell.BOLTS_LEFT))
            assertEquals(1f, runtime.apex(ApexCell.CAST_COUNT))
            assertTrue(state.apexActive())
            val cue = state.takeVisualFxCues().filterIsInstance<VisualFxCue.ApexActivated>().single()
            assertEquals(shape, cue.shape)
            assertEquals(state.coreX, cue.x)
            assertTrue(GameplayAudioCue.APEX in state.takeSoundCues(), "$shape sound")

            // A request while cooling waits out its buffer and casts nothing.
            state.requestApex()
            repeat(30) { state.updateApex(MutableGameState.FIXED_STEP) }
            assertEquals(0f, state.apexBufferTime)
            assertEquals(1f, runtime.apex(ApexCell.CAST_COUNT))

            // The activation ends on time, its form ends with it.
            state.elapsed = runtime.apex(ApexCell.ACTIVE_UNTIL)
            state.updateApex(MutableGameState.FIXED_STEP)
            assertFalse(state.apexActive())
            assertEquals(0f, runtime.apex(ApexCell.ACTIVE_UNTIL))
            assertFalse(state.apexReady())
        }
    }

    @Test
    fun requestsBufferOnlyForAnUnlockedApexWhileRunningAndRepeatsChangeNothing() {
        val reducer = GameReducer()
        val request = GameplayInteractionPulse.ApexRequested
        val locked = EngineState(arena())
        assertSame(locked, assertIs<GameReductionResult.Accepted>(reducer.reduce(locked, request)).state, "a locked Apex")

        val model = arena().apply { unlockNow() }
        val state = EngineState(model)
        val buffered = assertIs<GameReductionResult.Accepted>(reducer.reduce(state, request)).state
        assertNotSame(state, buffered)
        assertEquals(MutableGameState.SKILL_INPUT_BUFFER_SECONDS, buffered.model.apexBufferTime)
        assertEquals(0f, model.apexBufferTime, "the committed state is untouched")
        assertSame(model.skillRuntime, buffered.model.skillRuntime, "a request shares the runtime")
        assertSame(buffered, assertIs<GameReductionResult.Accepted>(reducer.reduce(buffered, request)).state, "a repeat")

        val paused = EngineState(arena().apply { unlockNow(); togglePause(); takeVisualFxCues(); takeSoundCues() })
        assertSame(paused, assertIs<GameReductionResult.Accepted>(reducer.reduce(paused, request)).state, "a paused run")

        // Pause, overlays and exits drop a buffered request.
        model.requestApex()
        model.togglePause()
        assertEquals(0f, model.apexBufferTime)
    }

    @Test
    fun apexStrikesDealTheFullCapStopAtTheBudgetAndNeverPassAPhaseGate() {
        val state = arena()
        val boss = state.testArchitect()
        val ordinary = state.dummy(x = state.coreX - 300f)
        state.unlockNow()
        state.castNow()
        val cap = state.architectHitCap(boss)
        val budget = state.apexDefinition.bossStrikes

        // A strike removes the boss's whole hit cap, whatever the channel's own number, and spends one strike.
        assertEquals(cap, state.apexStrike(boss), 1e-3f)
        assertEquals(budget - 1, state.apexStrikesLeft)
        // Ordinary enemies and a shifting boss take nothing and spend nothing.
        assertEquals(0f, state.apexStrike(ordinary))
        boss.shiftUntil = state.elapsed + 1f
        val hp = boss.hp
        assertEquals(0f, state.apexStrike(boss))
        assertEquals(hp, boss.hp)
        assertEquals(budget - 1, state.apexStrikesLeft)
        boss.shiftUntil = 0f
        // Never past the phase gate: half a cap above the first gate, the strike removes that half and the shift starts.
        val gate = boss.maxHp * 2f / 3f
        boss.hp = gate + cap * 0.5f
        assertEquals(cap * 0.5f, state.apexStrike(boss), 1e-2f)
        assertEquals(gate, boss.hp, 1e-2f)
        assertTrue(state.isShifting(boss))
        assertEquals(0f, state.apexStrike(boss), "nothing lands while it shifts")
        assertEquals(budget - 2, state.apexStrikesLeft)
        boss.shiftUntil = 0f
        repeat(budget - 2) { assertEquals(cap, state.apexStrike(boss), 1e-3f) }
        assertEquals(0, state.apexStrikesLeft)
        val spent = boss.hp
        assertEquals(0f, state.apexStrike(boss), "the budget is spent")
        assertEquals(spent, boss.hp)
        assertEquals((gate + cap * 0.5f - spent + cap).toDouble(), state.skillRuntime.apexBossDamage, 1e-2)
        assertEquals(budget, state.takeVisualFxCues().filterIsInstance<VisualFxCue.ShockwaveAdded>().count { it.colorIndex == FxColorCode.APEX_GOLD })
    }

    @Test
    fun apexErasuresSpendTheBoltBudgetAndStopWhenItIsEmpty() {
        val state = arena()
        val before = state.hostileBolt(state.coreX + 100f, state.coreY)
        assertFalse(state.eraseApexShot(before, before.x, before.y), "no activation, no budget")
        state.unlockNow()
        state.castNow()
        val budget = state.apexDefinition.boltBudget
        val bolts = List(budget + 3) { state.hostileBolt(state.coreX + 50f + it, state.coreY) }
        state.takeVisualFxCues()
        assertEquals(budget, bolts.count { state.eraseApexShot(it, it.x, it.y) })
        assertEquals(3, bolts.count { !it.spent })
        assertEquals(0f, state.skillRuntime.apex(ApexCell.BOLTS_LEFT))
        val bursts = state.takeVisualFxCues().filterIsInstance<VisualFxCue.Burst>()
        assertEquals(MAX_SHOT_ERASURES_SHOWN_PER_STEP, bursts.size)
        assertTrue(bursts.all { it.colorIndex == FxColorCode.APEX_GOLD })
        // Other causes keep their own rules.
        assertTrue(state.eraseHostileShot(bolts.last(), EraseCause.CHARACTER, 0f, 0f))
    }

    @Test
    fun apexShotsStrikeBossesHitOrdinaryEnemiesAndPassThroughBossesOnceStrikesRunOut() {
        val state = arena()
        val boss = state.testArchitect()
        state.unlockNow()
        state.castNow()
        val cap = state.architectHitCap(boss)

        state.apexShot(boss, 10_000f)
        state.resolveProjectileHits()
        assertEquals(3_000f - cap, boss.hp, 1e-2f)
        val left = state.apexDefinition.bossStrikes - 1
        assertEquals(left, state.apexStrikesLeft)
        assertTrue(state.projectiles.isEmpty(), "the shot spent itself")

        val ordinary = state.dummy(x = state.coreX - 300f, hp = 1_000f)
        state.apexShot(ordinary, 40f)
        state.resolveProjectileHits()
        assertEquals(1_000f - 40f * state.matrix.damageMultiplier * ordinary.weaponTaken, ordinary.hp, 1e-3f)
        assertEquals(left, state.apexStrikesLeft)

        state.skillRuntime.setApex(ApexCell.STRIKES_LEFT, 0f)
        val hp = boss.hp
        val shot = state.apexShot(boss, 10_000f)
        state.resolveProjectileHits()
        assertEquals(hp, boss.hp)
        assertTrue(shot in state.projectiles && !shot.spent, "the shot passes through")
        // Apex shots never qualify for on-hit effects.
        assertFalse(state.weaponRuntime.canQualify(WeaponRuntime.PROC_APEX, state.elapsed))
    }

    @Test
    fun castingDrawsNoRandomness() {
        for (shape in CoreShape.entries) {
            val state = arena(shape)
            repeat(4) { index -> state.dummy(x = state.coreX + 80f + index * 50f, y = state.coreY + (index % 2) * 40f) }
            state.testArchitect()
            repeat(12) { state.hostileBolt(state.coreX + 30f * it - 150f, state.coreY + 60f) }
            state.unlockNow()
            state.characterRuntime.charge = 1f
            val gameplay = state.gameplayRandom.snapshot()
            val reward = state.rewardRandom.snapshot()
            val spawn = state.spawnRandom.snapshot()
            state.castNow()
            repeat(240) {
                state.elapsed += MutableGameState.FIXED_STEP
                state.updateApex(MutableGameState.FIXED_STEP)
                state.updateCharacterRuntime(MutableGameState.FIXED_STEP)
            }
            assertEquals(gameplay, state.gameplayRandom.snapshot(), "$shape drew gameplay randomness")
            assertEquals(reward, state.rewardRandom.snapshot(), "$shape drew reward randomness")
            assertEquals(spawn, state.spawnRandom.snapshot(), "$shape drew spawn randomness")
        }
    }

    @Test
    fun aForkIsolatesTheApexAndANewRunLocksItAgain() {
        val state = arena()
        state.unlockNow()
        val committed = state.skillRuntime.toString()
        val candidate = state.copyForReduction()
        candidate.castNow()
        assertTrue(candidate.apexActive())
        assertFalse(state.apexActive())
        assertEquals(committed, state.skillRuntime.toString(), "the committed runtime is untouched")
        candidate.startRun()
        assertFalse(candidate.apexUnlocked)
        assertTrue(state.apexUnlocked)
    }

    @Test
    fun theRenderModelProjectsTheApexAndMarksTheStrikeTarget() {
        val state = arena()
        val boss = state.testArchitect()
        val ordinary = state.dummy(x = state.coreX - 300f)
        assertSame(ApexProjection.LOCKED, state.toRenderModel().apex)

        state.unlockNow()
        val ready = state.toRenderModel()
        assertEquals(ApexProjection(unlocked = true, ready = true), ready.apex)
        assertSame(ready.apex, state.toRenderModel(reusableCollections = ready).apex, "an unchanged Apex keeps its projection")

        state.castNow()
        val active = state.toRenderModel()
        assertEquals(ApexProjection(unlocked = true, ready = false, cooldown01 = 1f, active01 = 1f, strikesLeft = state.apexDefinition.bossStrikes, boltsLeft = 24),
            active.apex)
        val bits = active.enemies.associate { it.id to it.controlBits }
        assertEquals(EnemyControlBits.APEX_STRIKE_TARGET, bits.getValue(boss.id) and EnemyControlBits.APEX_STRIKE_TARGET)
        assertEquals(0, bits.getValue(ordinary.id) and EnemyControlBits.APEX_STRIKE_TARGET)

        state.elapsed += 5f
        state.refundApex(6f)
        val half = state.toRenderModel().apex
        assertEquals(0.5f, half.active01, 1e-4f)
        assertEquals(6f, half.refunded, 1e-4f)
        assertEquals(0.2f, half.dataCharge01, 1e-4f)
        assertEquals((60f - 5f - 6f) / 60f, half.cooldown01, 1e-4f)

        state.elapsed += 6f
        state.updateApex(MutableGameState.FIXED_STEP)
        val cooling = state.toRenderModel()
        assertEquals(0f, cooling.apex.active01)
        assertEquals(0, cooling.apex.strikesLeft)
        assertEquals(0, cooling.enemies.single { it.id == boss.id }.controlBits and EnemyControlBits.APEX_STRIKE_TARGET)
    }

    private fun arena(shape: CoreShape = CoreShape.ORB): MutableGameState = buildTestState(shape = shape).apply {
        obstacleField = ObstacleField.EMPTY
        aimAt(coreX + 300f, coreY)
        assertEquals(GamePhase.RUNNING, phase)
    }
}

/** Unlocks the Apex as the level cap would, draining the banner's sound. */
internal fun MutableGameState.unlockNow() {
    level = content.tempo.maximumLevel
    unlockApex()
    takeSoundCues()
}

/** Casts the Apex now through its buffered request. */
internal fun MutableGameState.castNow() {
    requestApex()
    updateApex(MutableGameState.FIXED_STEP)
    check(apexActive()) { "the Apex did not cast" }
}

/** An Architect of 3,000 integrity, 400 to the Core's right. */
internal fun MutableGameState.testArchitect(x: Float = coreX + 400f, y: Float = coreY): Enemy {
    bossSpawned = true
    return addEnemyForTesting(x, y, hp = 3_000f, radius = 74f, type = EnemyType.ARCHITECT)
}

/** An Apex shot of [damage] about to cross [target] this step. */
internal fun MutableGameState.apexShot(target: Enemy, damage: Float): Projectile =
    Projectile(
        x = target.x, y = target.y, vx = 0f, vy = 0f, radius = 5f, life = 2f, hostile = false, damage = damage,
        colorIndex = FxColorCode.APEX_GOLD, previousX = target.x - 40f, previousY = target.y, procKey = WeaponRuntime.PROC_APEX,
    ).also { projectiles += it }
