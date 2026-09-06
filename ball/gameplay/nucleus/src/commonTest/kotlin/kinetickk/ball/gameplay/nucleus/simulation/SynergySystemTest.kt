// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SynergySystemTest {
    @Test
    fun resonanceRequiresTwoDifferentOrdinaryRelicsAndIgnoresRanksAndSovereign() {
        val engine = engine(RelicId.KINETIC_FLYWHEEL)
        repeat(4) { engine.acquireRelic(RelicId.KINETIC_FLYWHEEL) }
        engine.acquireRelic(RelicId.CROWN_OF_FOUR_WINDS)
        assertFalse(engine.hasSynergy(SynergyId.VECTOR_MANEUVER))
        engine.acquireRelic(RelicId.GHOST_VECTOR)
        assertTrue(engine.hasSynergy(SynergyId.VECTOR_MANEUVER))
        val summary = engine.buildSynergySummaries().first { it.id == SynergyId.VECTOR_MANEUVER.name }
        assertTrue(summary.active)
        assertTrue(summary.missingComponents.isEmpty())
        engine.replaceRelic(0, RelicId.ORBITAL_NAIL)
        assertFalse(engine.hasSynergy(SynergyId.VECTOR_MANEUVER))
        assertEquals(12, engine.buildSynergySummaries().size)
    }

    @Test
    fun allSixSystemResonancesHaveObservableCombatBehavior() {
        val vector = engine(RelicId.KINETIC_FLYWHEEL, RelicId.GHOST_VECTOR)
        vector.onSynergyTurn()
        assertTrue(vector.synergyManeuverCharge > 0f)
        val target = vector.addEnemyForTesting(100f, 0f)
        vector.dealWeaponDamage(target, 20f)
        assertEquals(0f, vector.synergyManeuverCharge)
        val grouping = engine(RelicId.ORBITAL_NAIL, RelicId.MASS_ECHO)
        val origin = grouping.addEnemyForTesting(100f, 0f)
        val neighbor = grouping.addEnemyForTesting(200f, 0f)
        grouping.onSynergyPrimaryHit(origin, DamageResult(20f, false), 0f)
        assertTrue(neighbor.vx < 0f)
        for ((first, second, synergy) in listOf(
            Triple(RelicId.VOLTAIC_FILAMENT, RelicId.ION_DEBT, SynergyId.ION_DISCHARGE),
            Triple(RelicId.GLASS_WITNESS, RelicId.MIRROR_CUT, SynergyId.PRISM_REFRACTION),
        )) {
            val state = engine(first, second)
            val hit = state.addEnemyForTesting(100f, 0f)
            val other = state.addEnemyForTesting(150f, 0f)
            state.onSynergyPrimaryHit(hit, DamageResult(20f, false), 0f)
            assertTrue(other.hp < 1_000f, synergy.name)
        }
        for ((first, second, synergy) in listOf(
            Triple(RelicId.ECHO_CHAMBER, RelicId.SECOND_HAND, SynergyId.RIFT_ECHO),
            Triple(RelicId.SCAR_TISSUE, RelicId.QUIETUS_BLOOM, SynergyId.ENTROPY_DECAY),
        )) {
            val state = engine(first, second)
            val hit = state.addEnemyForTesting(100f, 0f)
            state.onSynergyPrimaryHit(hit, DamageResult(20f, false), 0f)
            assertEquals(1_000f, hit.hp)
            state.updateSynergyRuntime(0.5f)
            assertTrue(hit.hp < 1_000f, synergy.name)
        }
    }

    @Test
    fun brakeCompressionRequiresActualDecelerationAndConsumesOneCharge() {
        val state = engine(RelicId.BRAKEPOINT_MEMORY, RelicId.MASS_ECHO).apply {
            braking = true
            magnetStrength = 0f
        }
        repeat(120) { state.updateCore(1f / 120f) }
        assertEquals(0f, state.brakepointCharge)
        state.velocityX = 800f
        state.updateCore(0.1f)
        assertTrue(state.brakepointCharge > 0f)
        val origin = state.addEnemyForTesting(state.coreX + 50f, state.coreY)
        val nearby = state.addEnemyForTesting(state.coreX + 90f, state.coreY)
        state.dealWeaponDamage(origin, 20f)
        assertTrue(nearby.hp < 1_000f)
        assertEquals(0f, state.brakepointCharge)
    }

    @Test
    fun linkedEchoUsesSavedTargetInsteadOfNewNearestTarget() {
        val state = engine(RelicId.VOLTAIC_FILAMENT, RelicId.ECHO_CHAMBER)
        val origin = state.addEnemyForTesting(100f, 0f)
        val linked = state.addEnemyForTesting(150f, 0f)
        state.onQualifiedWeaponHit(origin, DamageResult(20f, false), WeaponId.FLUX_WAKE)
        assertEquals(linked.id, state.delayedRelicHits.single().linkedEnemyId)
        val linkedHealth = linked.hp
        val newer = state.addEnemyForTesting(101f, 0f)
        state.updateRelicRuntime(0.5f)
        assertTrue(linked.hp < linkedHealth)
        assertEquals(1_000f, newer.hp)
        assertTrue(state.delayedRelicHits.isEmpty())
    }

    @Test
    fun markedDeathTransfersDecayOnceWithoutRecursiveKillProcs() {
        val state = engine(RelicId.GLASS_WITNESS, RelicId.SCAR_TISSUE)
        val origin = state.addEnemyForTesting(100f, 0f).apply {
            hp = 0f
            relicKillProcsEligible = true
            relicTimers[RelicId.GLASS_WITNESS.ordinal] = 3f
            relicTimers[RelicId.SCAR_TISSUE.ordinal] = 3f
            relicCounters[RelicId.SCAR_TISSUE.ordinal] = 5
        }
        val next = state.addEnemyForTesting(150f, 0f, hp = 2f)
        state.onEnemyKilled(origin)
        state.onEnemyKilled(origin)
        assertEquals(1, state.synergyEffects.size)
        state.updateSynergyRuntime(0.5f)
        assertTrue(next.hp <= 0f)
        assertFalse(next.relicKillProcsEligible)
        state.onEnemyKilled(next)
        assertEquals(1, state.synergyEffects.size, "Secondary decay must not transfer again")
    }

    @Test
    fun ghostMirrorNeedsRealDashDisplacementAndConsumesItsEdge() {
        val state = engine(RelicId.GHOST_VECTOR, RelicId.MIRROR_CUT)
        state.performDash()
        state.updateSynergyRuntime(0.01f)
        assertTrue(state.synergyEffects.isEmpty())
        state.velocityX = 1_400f
        state.updateCore(0.1f)
        state.updateSynergyRuntime(0.01f)
        assertEquals(SynergyEffectKind.GHOST_EDGE, state.synergyEffects.single().kind)
        val origin = state.addEnemyForTesting(state.coreX + 20f, 0f)
        val next = state.addEnemyForTesting(state.coreX + 60f, 0f)
        state.onSynergyPrimaryHit(origin, DamageResult(20f, false), 0f)
        assertTrue(next.hp < 1_000f)
        assertTrue(state.synergyEffects.none { it.kind == SynergyEffectKind.GHOST_EDGE })
    }

    @Test
    fun anchorPreservesChargeUntilCollapseAndRemovalCancelsIt() {
        val state = engine(RelicId.EVENTIDE_ANCHOR, RelicId.ION_DEBT)
        val origin = state.addEnemyForTesting(100f, 0f).apply {
            hp = 0f
            relicKillProcsEligible = true
            relicCounters[RelicId.ION_DEBT.ordinal] = 4
        }
        val next = state.addEnemyForTesting(150f, 0f)
        state.onEnemyKilled(origin)
        assertEquals(1_000f, next.hp)
        state.updateSynergyRuntime(0.2f)
        assertEquals(1_000f, next.hp)
        assertTrue(next.vx < 0f)
        val copy = state.copyForReduction()
        copy.replaceRelic(1, RelicId.KINETIC_FLYWHEEL)
        assertTrue(copy.synergyEffects.isEmpty())
        state.updateSynergyRuntime(0.5f)
        assertTrue(next.hp < 1_000f)
        assertTrue(state.synergyEffects.isEmpty())
    }

    @Test
    fun fracturedTargetLeavesSlowingTrailAndWorldRebasePreservesGeometry() {
        val state = engine(RelicId.FRACTURE_GATE, RelicId.QUIETUS_BLOOM)
        val origin = state.addEnemyForTesting(100f, 0f)
        repeat(6) { state.onQualifiedWeaponHit(origin, DamageResult(20f, false), WeaponId.FLUX_WAKE) }
        assertEquals(-100f, origin.x)
        val effect = state.synergyEffects.single()
        assertEquals(100f, effect.x)
        assertEquals(-100f, effect.endX)
        val neighbor = state.addEnemyForTesting(30f, 0f).apply { vx = 200f }
        state.updateSynergyRuntime(0.1f)
        assertTrue(neighbor.vx < 200f)
        assertTrue(neighbor.hp < 1_000f)
        state.rebaseSynergyEffects(40f, 20f)
        assertEquals(60f, state.synergyEffects.single().x)
        assertEquals(-140f, state.synergyEffects.single().endX)
    }

    @Test
    fun effectBoundAndDependencyRemovalPreserveIsolatedSnapshots() {
        val state = engine(RelicId.ECHO_CHAMBER, RelicId.SECOND_HAND)
        repeat(MAX_SYNERGY_EFFECTS + 1) { id ->
            state.addSynergyEffect(SynergyEffect(SynergyId.RIFT_ECHO, SynergyEffectKind.ECHO, 0.5f, 1f, id))
        }
        assertEquals(MAX_SYNERGY_EFFECTS, state.synergyEffects.size)
        val sourceEffects = state.synergyEffects
        val copy = state.copyForReduction()
        copy.replaceRelic(1, RelicId.KINETIC_FLYWHEEL)
        assertTrue(copy.synergyEffects.isEmpty())
        assertEquals(sourceEffects, state.synergyEffects)
        assertEquals(MAX_SYNERGY_EFFECTS, state.synergyEffects.size)
        copy.startRun()
        assertTrue(copy.synergyEffects.isEmpty())
    }

    private fun engine(vararg relics: RelicId) = MutableGameState(canonicalGameplayContent).apply {
        startRun()
        runGrace = 10_000f
        enemies.clear()
        critChance = 0f
        relics.forEach(::acquireRelic)
    }
}
