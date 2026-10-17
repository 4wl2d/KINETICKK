// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponScaling
import kinetickk.ball.gameplay.nucleus.model.WeaponRuntime
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.abs
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The weapon synergy rules (SPEC 10.2) read from owned State: tags, signature, harmony, infusion and scaling. */
class WeaponSynergyTest {
    @Test
    fun tagResonanceGivesFivePercentPerPointInTheWeaponsTagsUpToHalf() {
        val state = buildTestState(weapon = WeaponId.MORNINGSTAR)
        val tags = state.currentWeaponDefinition.tags
        val fitting = state.content.items.filter { item -> item.tags.any { it in tags } && item.clauses.isEmpty() }
        assertEquals(0f, state.weaponTagBonus())
        var index = 0
        while (state.weaponTagPoints() <= 12) {
            state.acquireItem(fitting[index % fitting.size].id)
            index++
            val expected = min(TAG_RESONANCE_CAP, TAG_RESONANCE_PER_POINT * state.weaponTagPoints())
            assertClose(expected, state.weaponTagBonus(), "after ${state.weaponTagPoints()} points")
            assertTrue(index < 200, "no item reached twelve tag points")
        }
        assertClose(TAG_RESONANCE_CAP, state.weaponTagBonus(), "the cap")
        // The Orb is not the Morningstar's signature and nothing bound carries its key: only resonance counts.
        assertClose(1f + TAG_RESONANCE_CAP, state.permanentWeaponSynergy(), "resonance alone")
    }

    @Test
    fun theSignatureCharacterStartsALevelUpAndRaisesTheAutomaticCeiling() {
        val weapon = WeaponId.MORNINGSTAR
        val signature = canonicalGameplayContent.weapon(weapon).signature
        val other = CoreShape.entries.first { it != signature }
        val starring = buildTestState(shape = signature, weapon = weapon)
        val plain = buildTestState(shape = other, weapon = weapon)
        assertEquals(1 + SIGNATURE_START_LEVELS, starring.weaponLevel)
        assertEquals(1, plain.weaponLevel)
        // Each character brings its own tag point, so the comparison takes tag resonance out of both.
        assertClose(
            SIGNATURE_POWER,
            starring.permanentWeaponSynergy() / (1f + starring.weaponTagBonus()) / (plain.permanentWeaponSynergy() / (1f + plain.weaponTagBonus())),
            "signature power",
        )

        val ceiling = canonicalGameplayContent.tempo.maximumAutomaticWeaponLevel
        assertEquals(ceiling, levelUpAt(plain, ceiling), "an ordinary run stops at the ceiling")
        assertEquals(ceiling + 1, levelUpAt(starring, ceiling), "the signature run passes it by one")
        assertEquals(ceiling + 1, levelUpAt(starring, ceiling + 1), "and stops there")
    }

    @Test
    fun aBoundRelicCarryingTheWeaponsKeyHarmonizesBothWays() {
        val weapon = WeaponId.MORNINGSTAR
        val key = canonicalGameplayContent.weapon(weapon).key
        val relic = canonicalGameplayContent.relics.first { key in it.keys && it.minimumRebirth == 0 }.id
        val foreign = WeaponId.entries.first { canonicalGameplayContent.weapon(it).key !in canonicalGameplayContent.relic(relic).keys }
        val harmonized = buildTestState(relic, weapon = weapon)
        val unrelated = buildTestState(relic, weapon = foreign)

        assertEquals(relic, harmonized.harmonizingRelic())
        assertNull(unrelated.harmonizingRelic())
        assertClose(HARMONY_POWER, harmonized.permanentWeaponSynergy() / (1f + harmonized.weaponTagBonus()), "harmony power")
        assertClose(HARMONY_MAGNITUDE, harmonized.build.relicUpside(relic) / unrelated.build.relicUpside(relic), "harmony magnitude")
        assertEquals(relic, harmonized.weaponSynergyProjection(null).harmonizingRelic)
    }

    @Test
    fun aKineticWeaponAdoptsTheDominantElementOnEveryThirdQualifyingHitAtTierTwo() {
        val state = buildTestState(weapon = WeaponId.MORNINGSTAR)
        assertNull(state.currentWeaponDefinition.element)
        // Items of one element that never apply a status themselves; a mastered (fully stacked) one adds more affinity.
        fun quiet(element: StatusElement) = state.content.items.filter { item ->
            item.elements == listOf(element) && item.clauses.all { clause -> clause.actions.none { it is EffectAction.ApplyStatus } }
        }
        val element = StatusElement.entries.first { quiet(it).size >= 3 }
        val affine = quiet(element)
        var index = 0
        while (state.affinityTier(element) < KINETIC_INFUSION_TIER) {
            assertNull(state.weaponInfusionElement(), "below tier $KINETIC_INFUSION_TIER the weapon stays kinetic")
            assertTrue(index < affine.size, "no item set reached affinity tier $KINETIC_INFUSION_TIER")
            repeat(affine[index].maxStacks) { state.acquireItem(affine[index].id) }
            index++
        }
        assertEquals(element, state.build.dominant)
        assertEquals(element, state.weaponInfusionElement())

        val target = state.dummy()
        repeat(KINETIC_INFUSION_EVERY - 1) { state.dealWeaponDamage(target, 1f) }
        assertEquals(0, target.stacksOf(element), "the first hits stay kinetic")
        state.dealWeaponDamage(target, 1f)
        assertEquals(1, target.stacksOf(element), "every third qualifying hit carries one stack")
        // A hit that does not qualify (its activation already spent) never counts toward the infusion.
        repeat(KINETIC_INFUSION_EVERY) { state.dealWeaponDamage(target, 1f, procKey = WeaponRuntime.PROC_NEVER) }
        assertEquals(1, target.stacksOf(element))
    }

    @Test
    fun scalingMultipliersStayFiniteWithinOneAndTheCap() {
        assertEquals(1f, clampScaling(Float.NaN))
        assertEquals(1f, clampScaling(-3f))
        assertEquals(1f, clampScaling(0.5f))
        assertEquals(1.3f, clampScaling(1.3f))
        assertEquals(SCALING_MAX, clampScaling(9f))
        assertEquals(SCALING_MAX, clampScaling(Float.POSITIVE_INFINITY))

        for (weapon in WeaponId.entries) {
            val state = buildTestState(weapon = weapon)
            assertInRange(state.weaponScalingMultiplier(), "$weapon at rest")
            state.velocityX = 9_000f
            state.heat = 1_000f
            state.combo = 900
            state.mass = 40f
            state.luck = 40f
            state.attackSpeed = 9f
            state.shield = 1_000f
            state.overdriveTime = 5f
            assertInRange(state.weaponScalingMultiplier(), "$weapon at extremes")
        }
    }

    @Test
    fun shieldScalingWithoutCapacityIsOneAndAFullShieldAddsHalf() {
        val state = buildTestState(weapon = WeaponId.PHASE_LATTICE)
        assertEquals(WeaponScaling.SHIELD, state.currentWeaponDefinition.scaling)
        state.maxShield = 0f
        state.shield = 0f
        assertEquals(0f, state.shieldCapacity())
        assertEquals(1f, state.weaponScalingMultiplier())
        state.maxShield = 40f
        state.shield = state.shieldCapacity()
        assertClose(1.5f, state.weaponScalingMultiplier(), "full shield")
    }

    @Test
    fun thePentagonsOnBeatArcCoilActivationChainsTwoMoreTargets() {
        val state = buildTestState(shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL)
        assertTrue(state.signatureActive())
        state.weaponLevel = 1
        state.elapsed = BEAT_PERIOD * 8 + BEAT_PERIOD / 2f
        assertEquals(2, state.arcCoilTargetCount(), "off the beat")
        state.elapsed = BEAT_PERIOD * 8
        assertTrue(state.isOnBeat())
        assertEquals(4, state.arcCoilTargetCount(), "on the beat")
    }

    /** Levels [state] up across the next weapon amplification from weapon level [from]; returns the weapon level after. */
    private fun levelUpAt(state: MutableGameState, from: Int): Int {
        state.weaponLevel = from
        val tempo = state.content.tempo
        var next = state.level + 1
        while (!tempo.grantsWeaponAmplification(next)) next++
        state.level = next - 1
        state.data = 0
        state.nextLevelData = tempo.dataRequiredForLevel(state.level)
        state.gainData(state.nextLevelData.toFloat())
        assertEquals(next, state.level)
        return state.weaponLevel
    }

    private fun assertClose(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) < 1e-4f, "$message: expected $expected, was $actual")

    private fun assertInRange(value: Float, message: String) {
        assertNotNull(value)
        assertTrue(value.isFinite() && value >= 1f && value <= SCALING_MAX, "$message: $value")
    }
}
