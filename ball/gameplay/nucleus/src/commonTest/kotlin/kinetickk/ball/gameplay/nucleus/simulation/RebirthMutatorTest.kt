// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rank gates versus tier scaling, and the Rebirth mutators the rework owns (R4, R7, R8, R9, R10). */
class RebirthMutatorTest {
    @Test
    fun droppingToTierZeroAfterRankFiveKeepsRankUnlocksButTheTierZeroRosterAndScaling() {
        val lowered = rankedState(level = 0, highestCleared = 4)
        assertEquals(0, lowered.rebirthLevel)
        assertEquals(5, lowered.rebirthRank)
        assertEquals(0, lowered.rebirthProfile.tier)
        assertTrue(lowered.rebirthProfile.mutators.isEmpty())

        val rankFiveItems = canonicalGameplayContent.items.filter { it.minimumRebirth == 5 }
        assertTrue(rankFiveItems.isNotEmpty())
        val offerable = lowered.offerableItems().map { it.id }.toSet()
        rankFiveItems.filter { lowered.hasUsefulItemEffect(it) }.forEach { assertTrue(it.id in offerable, it.name) }
        val beyond = canonicalGameplayContent.items.filter { it.minimumRebirth > 5 }.map { it.id }
        assertTrue(beyond.none { it in offerable })
        assertTrue(lowered.offerableRelics().any { it.id == RelicId.OUROBOROS_SEAL })

        // The roster follows the selected tier: no Rebirth-gated enemy type at tier 0, whatever the rank.
        lowered.elapsed = 900f
        canonicalGameplayContent.enemies.filter { it.minimumRebirth >= 1 && !it.isScheduled }.forEach { definition ->
            assertEquals(0f, lowered.typeWeight(definition, weakOnly = false, recency = false), definition.type.name)
        }
        val tierTwo = rankedState(level = 2, highestCleared = 4).apply { elapsed = 900f }
        assertTrue(tierTwo.typeWeight(canonicalGameplayContent.enemy(EnemyType.PHANTOM), weakOnly = false, recency = false) > 0f)

        // A fresh profile sees none of it.
        val fresh = rankedState(level = 0, highestCleared = -1)
        assertEquals(0, fresh.rebirthRank)
        assertTrue(fresh.offerableItems().none { it.minimumRebirth > 0 })
        assertTrue(fresh.offerableRelics().none { it.minimumRebirth > 0 })
    }

    @Test
    fun volatileRemainsBurstAfterTheDelayUnlessTheCoreIsFastOrDashing() {
        val state = buildTestState(rebirth = 4)
        assertTrue(state.rebirthProfile.has(RebirthMutator.VOLATILE_REMAINS))
        state.setVelocityForTesting(0f, 0f)
        state.killEnemyForTesting(EnemyType.DRIFTER, x = state.coreX + 40f, y = state.coreY)
        val burst = state.volatileBursts.single()
        assertEquals(VOLATILE_BURST_RADIUS, burst.radius)
        assertEquals(state.elapsed + VOLATILE_BURST_DELAY_SECONDS, burst.detonatesAt, 0.0001f)
        assertEquals(burst, state.toRenderModel().volatileBursts.single())

        val before = state.hp
        state.elapsed += VOLATILE_BURST_DELAY_SECONDS * 0.5f
        state.updateVolatileRemains()
        assertEquals(before, state.hp)
        state.elapsed += VOLATILE_BURST_DELAY_SECONDS
        state.updateVolatileRemains()
        assertTrue(state.hp < before, "a slow Core inside the ring takes the burst")
        assertTrue(state.volatileBursts.isEmpty())

        // Fast or dashing Cores and Cores outside the ring escape; Elites leave no remains.
        fun escapes(setup: MutableGameState.() -> Unit, distance: Float = 40f): Boolean {
            val other = buildTestState(rebirth = 4)
            other.setVelocityForTesting(0f, 0f)
            other.killEnemyForTesting(EnemyType.SWARMLING, x = other.coreX + distance, y = other.coreY)
            other.setup()
            val hp = other.hp
            other.elapsed += VOLATILE_BURST_DELAY_SECONDS + 0.01f
            other.updateVolatileRemains()
            return other.hp == hp && other.volatileBursts.isEmpty()
        }
        assertTrue(escapes({ setVelocityForTesting(VOLATILE_ESCAPE_SPEED, 0f) }))
        assertFalse(escapes({ setVelocityForTesting(VOLATILE_ESCAPE_SPEED - 5f, 0f) }))
        assertTrue(escapes({ dashPhaseTime = 0.2f }))
        assertTrue(escapes({}, distance = VOLATILE_BURST_RADIUS + 60f))
        val elite = buildTestState(rebirth = 4)
        elite.killEnemyForTesting(EnemyType.ELITE)
        assertTrue(elite.volatileBursts.isEmpty())
        val belowTier = buildTestState(rebirth = 3)
        belowTier.killEnemyForTesting(EnemyType.DRIFTER)
        assertTrue(belowTier.volatileBursts.isEmpty())
    }

    @Test
    fun volatileBurstsAreBoundedAndMoveWithARebase() {
        val state = buildTestState(rebirth = 4)
        repeat(MutableGameState.MAX_VOLATILE_BURSTS + 5) { state.killEnemyForTesting(EnemyType.DRIFTER, x = 600f, y = 0f) }
        assertEquals(MutableGameState.MAX_VOLATILE_BURSTS, state.volatileBursts.size)
        state.coreX = 300_000f
        state.rebaseWorldIfNeeded()
        assertEquals(600f - 300_000f, state.volatileBursts.first().x)
    }

    @Test
    fun entropyTideWeakensHealingAndShieldRegeneration() {
        fun healed(tier: Int): Float {
            val state = buildTestState(rebirth = tier)
            state.hp = 50f
            state.heal(10f, HealSource.REPAIR)
            return state.hp - 50f
        }
        assertEquals(10f, healed(6), 0.0001f)
        assertEquals(10f * ENTROPY_TIDE_HEALING, healed(7), 0.0001f)

        fun regenerated(tier: Int): Float {
            val state = buildTestState(rebirth = tier)
            state.maxShield = 100f
            state.shield = 0f
            state.shieldRechargeDelay = 0f
            state.updateSurvival(1f)
            return state.shield
        }
        assertEquals(regenerated(6) * ENTROPY_TIDE_SHIELD_REGEN, regenerated(7), 0.0001f)
    }

    @Test
    fun armoredVanguardPlatesBluntWeaponsUntilAnImpactCracksThem() {
        val state = buildTestState(rebirth = 8)
        var armoredCount = 0
        repeat(400) { index -> if (state.spawnedEnemy(EnemyType.DRIFTER, 900f + index, 0f).armored) armoredCount++ }
        assertTrue(armoredCount in 55..105, "about one ordinary spawn in five is armored: $armoredCount")
        repeat(40) { assertFalse(state.spawnedEnemy(EnemyType.ELITE, 900f, 0f).armored) }
        val unarmored = buildTestState(rebirth = 7)
        repeat(100) { assertFalse(unarmored.spawnedEnemy(EnemyType.DRIFTER, 900f, 0f).armored) }
        // The roll is a hash of the run seed and the id: the same run plates the same enemies.
        val replay = buildTestState(rebirth = 8)
        val first = List(40) { replay.spawnedEnemy(EnemyType.DRIFTER, 900f, 0f).armored }
        val again = buildTestState(rebirth = 8)
        assertEquals(first, List(40) { again.spawnedEnemy(EnemyType.DRIFTER, 900f, 0f).armored })

        val plain = state.dummy(x = 300f)
        val plated = state.dummy(x = -300f).apply { armored = true }
        val plainDamage = state.triggerWeaponContactForTesting(plain)
        val platedDamage = state.triggerWeaponContactForTesting(plated)
        assertEquals(plainDamage * ARMOR_WEAPON_TAKEN, platedDamage, plainDamage * 0.001f)
        assertTrue(state.toRenderModel().enemies.single { it.id == plated.id }.armored)

        // Proc damage ignores the plate; an impact cracks it for good.
        assertEquals(1f, armorWeaponFactor(plated, HitSource.PROC))
        state.dealHit(plated, 10f, HitSource.IMPACT, canCrit = false)
        assertTrue(plated.armorCracked)
        assertFalse(state.toRenderModel().enemies.single { it.id == plated.id }.armored)
        assertEquals(plainDamage, state.triggerWeaponContactForTesting(plated), plainDamage * 0.001f)
    }

    @Test
    fun theArmoredVanguardRollDrawsNothingFromTheSpawnStream() {
        val state = buildTestState(rebirth = 8)
        val before = state.spawnRandom.snapshot()
        repeat(50) { state.spawnedEnemy(EnemyType.DRIFTER, 900f + it, 0f) }
        assertEquals(before, state.spawnRandom.snapshot(), "ordinary spawns at tier 8 leave the spawn stream untouched")
    }

    @Test
    fun blackoutDataFadesSoonerUnlessTheMagnetCaughtIt() {
        val dark = buildTestState(rebirth = 9)
        dark.addPickup(Pickup(PickupType.DATA, 5_000f, 0f))
        dark.addPickup(Pickup(PickupType.REPAIR, 5_000f, 0f))
        assertEquals(BLACKOUT_DATA_LIFE_SECONDS, dark.pickups.first { it.type == PickupType.DATA }.life)
        assertEquals(20f, dark.pickups.first { it.type == PickupType.REPAIR }.life)
        repeat(1_250) { dark.updatePickups(MutableGameState.FIXED_STEP) }
        assertTrue(dark.pickups.none { it.type == PickupType.DATA }, "the far Data faded after 10 s")
        // A latched pickup never fades on its way in.
        val caught = buildTestState(rebirth = 9)
        caught.addPickup(Pickup(PickupType.DATA, 50_000f, 0f, attracted = true))
        repeat(240) { caught.updatePickups(MutableGameState.FIXED_STEP) }
        assertEquals(BLACKOUT_DATA_LIFE_SECONDS, caught.pickups.single().life)
        val light = buildTestState(rebirth = 8)
        light.addPickup(Pickup(PickupType.DATA, 5_000f, 0f))
        assertEquals(20f, light.pickups.single().life)
    }

    @Test
    fun theSingularityArchitectHasAnEnragedFourthPhaseAndAHeraldEscort() {
        val state = buildTestState(rebirth = 10)
        assertEquals(ARCHITECT_FINAL_PHASE + 1, state.architectFinalPhase())
        state.bossSpawned = true
        val boss = state.addEnemyForTesting(400f, 0f, hp = 4_000f, radius = 74f, type = EnemyType.ARCHITECT)
        // Quarters: gates at 3,000, 2,000 and 1,000; each hit lands within the tier-10 cap.
        val near = state.architectHitCap(boss) * 0.5f
        boss.hp = 3_000f + near
        state.damageEnemy(boss, 80f)
        assertEquals(3_000f, boss.hp, 0.01f)
        assertEquals(1, boss.phase)
        state.elapsed = boss.shiftUntil
        boss.hp = 2_000f + near
        state.damageEnemy(boss, 80f)
        assertEquals(2, boss.phase)
        assertFalse(state.architectEnraged(boss))
        state.elapsed = boss.shiftUntil
        boss.hp = 1_000f + near
        state.damageEnemy(boss, 80f)
        assertEquals(1_000f, boss.hp, 0.01f)
        assertEquals(3, boss.phase)
        assertTrue(state.architectEnraged(boss))
        assertEquals("THE ARCHITECT ENRAGES", state.message)
        // The phase still fits the published two-bit field.
        assertEquals(3, kinetickk.ball.gameplay.nucleus.render.EnemyAffinity.phase(
            state.toRenderModel().enemies.single { it.id == boss.id }.affinityBits))
        state.elapsed = boss.shiftUntil
        boss.hp = near
        state.damageEnemy(boss, 80f)
        assertTrue(boss.hp <= 0f)

        // Below tier 10 the Architect keeps its three phases.
        assertEquals(ARCHITECT_FINAL_PHASE, buildTestState(rebirth = 9).architectFinalPhase())

        // It arrives with a Herald.
        val arrival = buildTestState(rebirth = 10).apply { heraldsSpawned = 2 }
        arrival.elapsed = canonicalGameplayContent.tempo.bossAtSeconds
        arrival.simulateStep(MutableGameState.FIXED_STEP)
        assertTrue(arrival.enemies.any { it.type == EnemyType.ARCHITECT })
        assertTrue(arrival.enemies.any { it.type == EnemyType.HERALD })
        val ordinary = buildTestState(rebirth = 9).apply { heraldsSpawned = 2 }
        ordinary.elapsed = canonicalGameplayContent.tempo.bossAtSeconds
        ordinary.simulateStep(MutableGameState.FIXED_STEP)
        assertTrue(ordinary.enemies.none { it.type == EnemyType.HERALD })
    }

    private fun rankedState(level: Int, highestCleared: Int): MutableGameState = MutableGameState(
        canonicalGameplayContent,
        seed = 4_040,
        bootstrapProgress = GameplayProfileSnapshot(
            preferences = PlayerPreferences(),
            economy = PlayerEconomy(),
            loadout = PlayerLoadout(),
            matrix = MatrixProgress(),
            collection = PlayerCollection(),
            rebirthProgress = RebirthProgress(level = level, highestCleared = highestCleared),
        ),
    ).apply {
        resize(1_280f, 720f)
        startRun()
    }
}
