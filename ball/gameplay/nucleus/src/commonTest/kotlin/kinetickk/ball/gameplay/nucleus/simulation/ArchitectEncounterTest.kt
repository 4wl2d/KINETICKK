// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.impactTaken
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.model.weaponTaken
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Architect's phases, shifts, caps, escorts and attacks. */
class ArchitectEncounterTest {
    @Test
    fun gatesAtTwoThirdsAndOneThirdStopTheHitAndStartTheNextPhase() {
        val state = buildTestState()
        // 3,000 integrity: gates at 2,000 and 1,000, one instance takes at most 45.
        val boss = state.architect()
        assertEquals(45f, state.damageEnemy(boss, 10_000f).amount, 0.01f)
        assertEquals(2_955f, boss.hp, 0.01f)
        assertEquals(0, boss.phase)

        boss.hp = 2_030f
        assertEquals(30f, state.damageEnemy(boss, 80f).amount, 0.01f)
        assertEquals(2_000f, boss.hp, 0.01f)
        assertEquals(1, boss.phase)
        assertTrue(state.isShifting(boss))

        state.elapsed = boss.shiftUntil
        assertFalse(state.isShifting(boss))
        boss.hp = 1_040f
        assertEquals(40f, state.damageEnemy(boss, 80f).amount, 0.01f)
        assertEquals(1_000f, boss.hp, 0.01f)
        assertEquals(2, boss.phase)

        // The final phase has no gate; only now can the Architect fall.
        state.elapsed = boss.shiftUntil
        boss.hp = 40f
        state.damageEnemy(boss, 80f)
        assertTrue(boss.hp <= 0f)
        assertEquals(2, boss.phase)

        // Integrity already below a gate is never raised to it, and the next phase still starts.
        val other = buildTestState()
        val below = other.architect().apply { hp = 1_500f }
        assertEquals(30f, other.damageEnemy(below, 30f).amount, 0.01f)
        assertEquals(1_470f, below.hp, 0.01f)
        assertEquals(1, below.phase)
    }

    @Test
    fun aShiftingArchitectTakesNothingAndSpendsNothing() {
        val state = buildTestState(content = contentWithClauses("Calibrated Coil",
            EffectClause(EffectTrigger.WEAPON_HIT, actions = immutableListOf(EffectAction.Strike(DamageBasis.VICTIM_MAX_HP, 0.1f)))))
        state.own("Calibrated Coil")
        val boss = state.architect()
        boss.hp = 2_010f
        state.damageEnemy(boss, 50f)
        assertTrue(state.isShifting(boss))

        state.critChance = 0.5f
        state.critMissStreak = 2
        boss.flash = 0f
        boss.status[StatusCell.BRITTLE] = 1f
        boss.status[StatusCell.STACKS + StatusElement.THERMAL.ordinal] = 7f
        val integrity = boss.hp
        val random = state.gameplayRandom.snapshot()
        state.takeVisualFxCues()

        for (source in listOf(HitSource.WEAPON, HitSource.IMPACT, HitSource.DASH, HitSource.ECHO)) {
            assertEquals(0f, state.dealHit(boss, 500f, source).amount, "$source")
        }
        assertEquals(0f, state.dealProcDamage(boss, 500f).amount)
        assertEquals(0f, state.dealStatusDamage(boss, 500f, StatusElement.ION).amount)
        assertEquals(0f, state.dealStatusDamage(boss, 500f, StatusElement.ION, ignoresAffinity = true).amount)
        assertEquals(0f, state.dealCharacterDamage(boss, 500f).amount)
        assertEquals(0f, state.damageEnemy(boss, 500f).amount)
        state.applyStatus(boss, StatusElement.THERMAL, 3, generation = 0)
        state.fireClauses(EffectTrigger.WEAPON_HIT, boss, hit = 500f, source = HitSource.WEAPON)
        state.forceBreaks(boss, 0.5f)

        assertEquals(integrity, boss.hp)
        assertEquals(0f, boss.flash)
        assertTrue(state.takeVisualFxCues().none { it is VisualFxCue.DamageNumberAdded })
        assertEquals(random, state.gameplayRandom.snapshot(), "No critical roll is drawn")
        assertEquals(2, state.critMissStreak)
        assertEquals(1f, boss.status[StatusCell.BRITTLE], "Brittle waits for a hit that lands")
        assertEquals(7, boss.stacksOf(StatusElement.THERMAL), "No stacks land and no Ignite is spent")
        assertEquals(0f, boss.status[StatusCell.REARM_AT + StatusElement.THERMAL.ordinal])
        assertEquals(0f, boss.status[StatusCell.MAX_HP_PROC_READY_AT], "Its %max-integrity cooldown is not spent")
    }

    @Test
    fun theHitCapCoversEveryDealerIncludingEchoesAndCharacterAbilities() {
        val state = buildTestState()
        val boss = state.architect().apply { phase = 2 }
        val dealt = state.damageDealt
        state.takeVisualFxCues()

        assertEquals(45f, state.dealHit(boss, 10_000f, HitSource.ECHO).amount, 0.01f)
        assertEquals(45f, state.dealCharacterDamage(boss, 10_000f).amount, 0.01f)
        assertEquals(45f, state.dealHit(boss, 10_000f, HitSource.WEAPON, canCrit = false).amount, 0.01f)
        assertEquals(45f, state.dealStatusDamage(boss, 10_000f, StatusElement.THERMAL).amount, 0.01f)
        assertEquals(45f, state.dealProcDamage(boss, 10_000f, rescale = false).amount, 0.01f)

        assertEquals(3_000f - 5 * 45f, boss.hp, 0.01f)
        assertEquals(225.0, state.damageDealt - dealt, 0.01)
        val numbers = state.takeVisualFxCues().filterIsInstance<VisualFxCue.DamageNumberAdded>()
        assertEquals(List(5) { 45L }, numbers.map { it.amount }, "Numbers show what landed")
    }

    @Test
    fun theShiftCallsEscortsARingAndRepairsOnceFromTheEncounterStepEvenWhileFrozen() {
        val state = buildTestState()
        val boss = state.architect()
        boss.hp = 2_010f
        state.damageEnemy(boss, 50f)
        assertEquals(1, boss.phase)
        assertEquals(listOf(boss), state.enemies, "Nothing spawns inside the damage pipeline")
        assertTrue(state.projectiles.isEmpty())
        assertTrue(state.pickups.isEmpty())

        boss.status[StatusCell.FROZEN_UNTIL] = state.elapsed + 5f
        state.simulateStep(MutableGameState.FIXED_STEP)
        assertTrue(state.isFrozen(boss))
        // With nothing to harden against, the Architect calls four Drifters.
        assertEquals(List(4) { EnemyType.DRIFTER }, state.enemies.filterNot { it.isBoss }.map { it.type })
        val ring = state.projectiles.count { it.hostile }
        assertEquals(16, ring)
        assertEquals(4, state.pickups.count { it.type == PickupType.REPAIR })
        assertEquals(0, boss.escortsPending)

        state.simulateStep(MutableGameState.FIXED_STEP)
        assertEquals(4, state.enemies.count { !it.isBoss })
        assertEquals(ring, state.projectiles.count { it.hostile })
        assertEquals(4, state.pickups.count { it.type == PickupType.REPAIR })
    }

    @Test
    fun theArchitectHardensAgainstTheBuildFromItsFirstAffinityTierAndIsNeverImmune() {
        fun shifts(vararg items: String): Pair<MutableGameState, List<Int>> {
            val state = buildTestState().apply { items.forEach { own(it) } }
            val boss = state.architect()
            assertEquals(EnemyAffinity.NONE, EnemyAffinity.aspect(state.affinityBits(boss)), "Phase one has no aspect")
            val aspects = mutableListOf<Int>()
            for (gate in listOf(2_000f, 1_000f)) {
                state.elapsed = boss.shiftUntil
                boss.status[StatusCell.RESIST_CARRY] = 0.5f
                boss.hp = gate + 10f
                state.damageEnemy(boss, 50f)
                val bits = state.affinityBits(boss)
                assertFalse(EnemyAffinity.immune(bits))
                assertEquals(0f, boss.status[StatusCell.RESIST_CARRY], "A new aspect restarts its carry")
                aspects += EnemyAffinity.aspect(bits)
            }
            return state to aspects
        }
        val none = EnemyAffinity.NONE
        val thermal = StatusElement.THERMAL.ordinal
        val cryo = StatusElement.CRYO.ordinal
        assertEquals(listOf(none, none), shifts("Igniter").second, "One source is below the first tier")
        // A Thermal tier and a lone Cryo source: the second shift falls back to the dominant element.
        val (thermalBuild, thermalAspects) = shifts("Igniter", "Incendiary Rounds", "Frost Plating")
        assertEquals(listOf(thermal, thermal), thermalAspects)
        // Both at their first tier: the dominant (the newest gain on a tie), then the secondary.
        assertEquals(listOf(cryo, thermal), shifts("Igniter", "Incendiary Rounds", "Frost Plating", "Rime Rounds").second)

        // Escorts follow the aspect: a Thermal Architect calls two Chargers and two Interceptors.
        thermalBuild.updateBossEncounter()
        assertEquals(
            listOf(EnemyType.CHARGER, EnemyType.CHARGER, EnemyType.INTERCEPTOR, EnemyType.INTERCEPTOR),
            thermalBuild.enemies.filterNot { it.isBoss }.map { it.type },
        )
    }

    @Test
    fun theKineticFrameHardensByPhaseWhateverTheAspect() {
        val state = buildTestState()
        val boss = state.addEnemyForTesting(400f, 0f, hp = 1_000_000f, radius = 74f, type = EnemyType.ARCHITECT)
        for ((phase, taken) in listOf(0 to 0.8f, 1 to 0.75f, 2 to 0.7f)) {
            boss.phase = phase
            for (aspect in listOf(-1, StatusElement.THERMAL.ordinal)) {
                assignAspect(boss, aspect, immune = false)
                boss.hp = boss.maxHp
                val impact = state.dealHit(boss, 100f, HitSource.IMPACT, canCrit = false)
                assertEquals(100f * taken, impact.amount, 0.01f, "phase $phase impact")
                assertEquals(100f, impact.unmitigated, 0.01f)
                assertEquals(100f * taken, state.dealHit(boss, 100f, HitSource.WEAPON, canCrit = false).amount, 0.01f,
                    "phase $phase weapon")
            }
        }
        // The first phase reads the profile table's frame.
        boss.phase = 0
        assertEquals(EnemyType.ARCHITECT.profile.impactTaken, boss.impactTaken)
        assertEquals(EnemyType.ARCHITECT.profile.weaponTaken, boss.weaponTaken)
    }

    @Test
    fun ordinaryAddsArriveThinnerAndOnlyWhileALiveArchitectExists() {
        val state = buildTestState().apply {
            bossSpawned = true
            spawnClock = 0f
            nextEliteAt = 0f
        }
        state.spawnWave(MutableGameState.FIXED_STEP)
        assertTrue(state.enemies.isEmpty(), "No adds without a live Architect")

        val boss = state.architect()
        state.damageEnemy(boss, 1f)
        state.spawnWave(MutableGameState.FIXED_STEP)
        assertEquals(2, state.enemies.size)
        assertTrue(state.enemies.none { it.isElite }, "No Elites during the fight")
        val interval = state.rebirthProfile.spawnInterval(state.content.tempo.spawnIntervalSeconds(state.elapsed))
        assertEquals(1.4f * interval, state.spawnClock, 0.0001f)

        val ordinaryCap = min(
            state.content.rebirth.maxActiveEnemies,
            state.rebirthProfile.enemyCap(state.content.tempo.ordinaryEnemyCap(state.elapsed)),
        )
        val fightCap = (ordinaryCap * ARCHITECT_ADD_CAP_SHARE).toInt()
        assertTrue(fightCap in 2 until ordinaryCap)
        while (state.enemies.size < fightCap) state.dummy(x = 400f * state.enemies.size)
        state.spawnClock = 0f
        state.spawnWave(MutableGameState.FIXED_STEP)
        assertEquals(fightCap, state.enemies.size, "The fight keeps a smaller cap")

        boss.hp = 0f
        state.enemies.retainAll { it === boss }
        state.spawnClock = 0f
        state.spawnWave(MutableGameState.FIXED_STEP)
        assertEquals(listOf(boss), state.enemies, "A fallen Architect ends the adds")
    }

    @Test
    fun addsArriveOnlyWhileTheArchitectShiftsOrWasHitRecently() {
        val state = buildTestState().apply { elapsed = 100f }
        val boss = state.architect()
        fun adds(): Int {
            state.enemies.retainAll { it === boss }
            state.spawnClock = 0f
            state.spawnWave(MutableGameState.FIXED_STEP)
            return state.enemies.size - 1
        }
        assertEquals(0, adds(), "An Architect nobody has hit calls no adds")

        state.damageEnemy(boss, 10f)
        assertEquals(1, adds())
        state.elapsed += ARCHITECT_ADD_ENGAGEMENT_SECONDS - 0.1f
        assertEquals(1, adds(), "The fight stays engaged inside the window")

        // A stalled fight: a minute without a hit calls nothing, however long the spawn clock runs.
        state.elapsed += 0.2f
        state.enemies.retainAll { it === boss }
        state.spawnClock = 0f
        repeat(600) {
            state.elapsed += 0.1f
            state.spawnWave(0.1f)
        }
        assertEquals(listOf(boss), state.enemies)

        // A shift takes no damage but keeps the adds coming.
        boss.shiftUntil = state.elapsed + 1f
        assertEquals(1, adds())
        state.elapsed = boss.shiftUntil
        assertEquals(0, adds())
    }

    @Test
    fun aShiftingArchitectIsNeverTargetedAndShotsPassThroughIt() {
        val state = buildTestState()
        val boss = state.architect().apply {
            x = state.coreX + 200f
            y = state.coreY
            previousX = x
            previousY = y
        }
        val drifter = state.dummy(x = state.coreX - 350f, y = state.coreY)
        assertEquals(boss, state.nearestEnemy(state.coreX, state.coreY, 820f), "Out of its shift the nearer Architect is the target")
        boss.hp = 2_010f
        state.damageEnemy(boss, 50f)
        assertTrue(state.isShifting(boss))

        assertEquals(drifter, state.nearestEnemy(state.coreX, state.coreY, 820f))
        assertEquals(drifter, state.nearestOtherEnemy(state.coreX, state.coreY, -1, 820f))
        val links = mutableListOf<Enemy>()
        state.chain(state.coreX, state.coreY, -1, 2, 820f) { links += it }
        assertEquals(listOf(drifter), links)

        state.equipWeaponForTesting(WeaponId.ARC_COIL)
        state.takeVisualFxCues()
        state.fireArcCoil(10f)
        val arcs = state.takeVisualFxCues().filterIsInstance<VisualFxCue.WeaponArcAdded>()
        assertTrue(arcs.isNotEmpty() && arcs.none { it.toX == boss.x }, "Arc Coil skips the shifting Architect")
        state.firePrismRelay(1f)
        val relay = state.projectiles.filter { !it.hostile }
        assertTrue(relay.isNotEmpty() && relay.all { it.vx < 0f }, "Prism Relay aims at the Drifter")

        // A shot crossing the shield keeps its pierce and records no hit.
        state.projectiles.clear()
        val shot = Projectile(boss.x, boss.y, 0f, 0f, radius = 5f, life = 1f, hostile = false, damage = 10f)
        state.projectiles += shot
        state.resolveProjectileHits()
        assertEquals(listOf(shot), state.projectiles)
        assertEquals(0, shot.pierce)
        assertFalse(shot.hasRecordedEnemyHit(boss.id))
        state.elapsed = boss.shiftUntil
        state.resolveProjectileHits()
        assertTrue(state.projectiles.isEmpty(), "Out of its shift the shot lands and is spent")
    }

    @Test
    fun anArchitectFallingInTheStepThatOpensALevelChoiceStillWins() {
        fun step(withArchitect: Boolean): Pair<MutableGameState, Enemy?> {
            val state = buildTestState()
            val boss = if (withArchitect) state.finalPhaseArchitectForTesting(state.coreX + 300f, state.coreY) else null
            // A shot already on the Architect finishes it in this step's projectile pass.
            if (boss != null) state.projectiles += Projectile(boss.x, boss.y, 0f, 0f, radius = 5f, life = 1f, hostile = false,
                damage = 10_000f)
            // A Data pickup on the Core completes a level in the same step.
            state.data = state.nextLevelData - 1
            state.pickups += Pickup(PickupType.DATA, state.coreX, state.coreY)
            state.simulateStep(MutableGameState.FIXED_STEP)
            return state to boss
        }
        assertEquals(GamePhase.CHOICE, step(withArchitect = false).first.phase, "The pickup opens a level choice this step")

        val (state, boss) = step(withArchitect = true)
        assertEquals(GamePhase.VICTORY, state.phase)
        assertTrue(checkNotNull(boss).dead)
        assertTrue(boss !in state.enemies)
        assertEquals(state.rebirthLevel, state.pendingClearedRebirthLevel)
    }

    @Test
    fun anArchitectWhoseKillRewardCompletesALevelStillWins() {
        // Data Siphon pays Data for an Elite or Architect kill; one Data short of a level, that reward levels up.
        fun kill(architect: Boolean): MutableGameState {
            val state = buildTestState().apply { own("Data Siphon") }
            val target = if (architect) state.finalPhaseArchitectForTesting(400f, 0f)
            else state.dummy(hp = 1_000f, type = EnemyType.ELITE).apply { hp = 1f }
            state.data = state.nextLevelData - 1
            state.dealHit(target, 1_000f, HitSource.WEAPON, canCrit = false)
            state.sweepKills()
            return state
        }
        assertEquals(GamePhase.CHOICE, kill(architect = false).phase, "The kill reward opens a level choice")

        val state = kill(architect = true)
        assertEquals(GamePhase.VICTORY, state.phase)
        assertEquals(state.rebirthLevel, state.pendingClearedRebirthLevel)
    }

    @Test
    fun volleysWaitWhileTheProjectileBudgetIsFull() {
        val state = buildTestState()
        val boss = state.architect().apply { actionTimer = 0f }
        assertTrue(450 < MutableGameState.MAX_PROJECTILES, "Player shots keep their share")
        repeat(450) { state.projectiles += Projectile(state.coreX - 2_000f, state.coreY, 0f, 0f, 1f, 10f, hostile = false) }

        state.updateEnemies(MutableGameState.FIXED_STEP)
        assertEquals(450, state.projectiles.size, "A full budget skips the volley")
        assertTrue(boss.actionTimer > 0f, "The skipped volley still waits a full cadence")

        state.projectiles.removeAt(state.projectiles.lastIndex)
        boss.actionTimer = 0f
        state.updateEnemies(MutableGameState.FIXED_STEP)
        assertTrue(state.projectiles.size > 450, "Below the budget the volley fires")
    }

    @Test
    fun executionersLineAgainstTheArchitectReadsTheCurrentThird() {
        val state = buildTestState(RelicId.EXECUTIONERS_PRISM)
        val boss = state.architect()
        // 35 % of a 1,000 third.
        boss.hp = 2_300f
        assertTrue(belowExecutionLine(boss, 0.35f))
        boss.hp = 2_400f
        assertFalse(belowExecutionLine(boss, 0.35f))
        boss.phase = 2
        boss.hp = 900f
        assertFalse(belowExecutionLine(boss, 0.35f), "The final third is not a free auto-crit")
        assertFalse(state.dealHit(boss, 10f, HitSource.WEAPON).critical)
        boss.hp = 300f
        assertTrue(belowExecutionLine(boss, 0.35f))
        assertTrue(state.dealHit(boss, 10f, HitSource.WEAPON).critical)

        val ordinary = state.dummy(hp = 1_000f).apply { hp = 340f }
        assertTrue(belowExecutionLine(ordinary, 0.35f))
        ordinary.hp = 360f
        assertFalse(belowExecutionLine(ordinary, 0.35f))
    }

    private fun MutableGameState.architect(maxHp: Float = 3_000f): Enemy {
        bossSpawned = true
        return addEnemyForTesting(400f, 0f, hp = maxHp, radius = 74f, type = EnemyType.ARCHITECT)
    }
}
