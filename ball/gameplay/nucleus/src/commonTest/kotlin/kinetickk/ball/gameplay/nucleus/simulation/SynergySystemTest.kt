// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.CombatField
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.EchoHit
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Build-level synergy: tag resonance, element affinity and relic-to-relic interactions. */
class SynergySystemTest {
    @Test
    fun tagPointsCountRebornTwiceMasteredOnceAndTheCharacterLean() {
        val state = engine()
        assertEquals(1, state.build.tagPoints(BuildTag.DRIVE), "ORB leans toward Drive")
        val ballast = state.item("Ballast Core")
        state.acquireItem(ballast.id)
        assertEquals(1, state.build.tagPoints(BuildTag.RAM))
        repeat(ballast.maxStacks - 1) { state.acquireItem(ballast.id) }
        // Mastered (max stacks) without an element adds one point to the first tag.
        assertEquals(2, state.build.tagPoints(BuildTag.RAM))

        val reborn = engine()
        reborn.acquireItem(reborn.item("Shock Plate").id, reborn = true)
        assertEquals(2, reborn.build.tagPoints(BuildTag.RAM))
        assertEquals(2, reborn.build.tagPoints(BuildTag.ANCHOR))

        val ring = engine(shape = CoreShape.RING)
        assertEquals(1, ring.build.tagPoints(BuildTag.BULWARK))
        assertEquals(0, ring.build.tagPoints(BuildTag.DRIVE))
    }

    @Test
    fun resonanceTiersUnlockAtThreeFiveAndSevenPoints() {
        val state = engine(shape = CoreShape.SHARD)
        // Multi-stack items, so a single copy is never Mastered.
        val ram = state.content.items.filter { BuildTag.RAM in it.tags && it.elements.isEmpty() && it.maxStacks > 1 }
        val tiers = mutableListOf<Int>()
        ram.take(7).forEach { item ->
            state.acquireItem(item.id)
            tiers += state.tagTier(BuildTag.RAM)
        }
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3), tiers)
        assertEquals(3, state.content.build.policy.tier(7, state.content.build.policy.resonanceThresholds))
    }

    @Test
    fun elementAffinityCountsItemsMasteryAndElementalRelicAspect() {
        val state = engine()
        val igniter = state.item("Igniter")
        repeat(igniter.maxStacks) { state.acquireItem(igniter.id) }
        // One owned Thermal item plus its Mastered point.
        assertEquals(2, state.build.affinity(StatusElement.THERMAL))
        assertEquals(1, state.affinityTier(StatusElement.THERMAL))
        assertEquals(1.25f, state.build.potency(StatusElement.THERMAL), 0.0001f)

        state.acquireRelic(RelicId.CINDER_CROWN)
        assertEquals(3, state.build.affinity(StatusElement.THERMAL))
        repeat(2) { state.acquireRelic(RelicId.CINDER_CROWN) }
        assertEquals(3, state.relicRank(RelicId.CINDER_CROWN))
        // A rank-three elemental relic adds two.
        assertEquals(4, state.build.affinity(StatusElement.THERMAL))
        assertEquals(2, state.affinityTier(StatusElement.THERMAL))
        assertEquals(1, state.build.thresholdCut(StatusElement.THERMAL))
        assertEquals(1.4f, state.build.breakPower(StatusElement.THERMAL), 0.0001f)
        assertEquals(StatusElement.THERMAL, state.build.dominant)
    }

    @Test
    fun dominantElementTieGoesToTheMostRecentGain() {
        val state = engine()
        state.acquireItem(state.item("Igniter").id)
        state.acquireItem(state.item("Frost Plating").id)
        assertEquals(StatusElement.CRYO, state.build.dominant)
        assertEquals(StatusElement.THERMAL, state.build.secondary)

        val reversed = engine()
        reversed.acquireItem(reversed.item("Frost Plating").id)
        reversed.acquireItem(reversed.item("Igniter").id)
        assertEquals(StatusElement.THERMAL, reversed.build.dominant)
        assertEquals(StatusElement.CRYO, reversed.build.secondary)

        // A strictly higher affinity wins regardless of recency.
        reversed.acquireItem(reversed.item("Rime Rounds").id)
        assertEquals(StatusElement.CRYO, reversed.build.dominant)
    }

    @Test
    fun anElementalRelicBindOrRankThreeIsTheMostRecentGainInATie() {
        // A Thermal item, then a Cryo relic: the bind is newer, so Cryo leads the tie.
        val bound = engine()
        bound.acquireItem(bound.item("Igniter").id)
        bound.acquireRelic(RelicId.ABSOLUTE_ZERO)
        assertEquals(1, bound.build.affinity(StatusElement.THERMAL))
        assertEquals(1, bound.build.affinity(StatusElement.CRYO))
        assertEquals(StatusElement.CRYO, bound.build.dominant)
        assertEquals(StatusElement.THERMAL, bound.build.secondary)

        // The relic first, then the item: the item is newer.
        val itemLast = engine(RelicId.ABSOLUTE_ZERO)
        itemLast.acquireItem(itemLast.item("Igniter").id)
        assertEquals(StatusElement.THERMAL, itemLast.build.dominant)

        // Rank three adds the relic's second point; reaching a tie that way is again the newest gain.
        itemLast.acquireItem(itemLast.item("Incendiary Rounds").id)
        assertEquals(2, itemLast.build.affinity(StatusElement.THERMAL))
        itemLast.acquireRelic(RelicId.ABSOLUTE_ZERO)
        assertEquals(StatusElement.THERMAL, itemLast.build.dominant, "Rank two adds no affinity")
        itemLast.acquireRelic(RelicId.ABSOLUTE_ZERO)
        assertEquals(3, itemLast.relicRank(RelicId.ABSOLUTE_ZERO))
        assertEquals(2, itemLast.build.affinity(StatusElement.CRYO))
        assertEquals(StatusElement.CRYO, itemLast.build.dominant)

        // A replacement bind counts from the moment of the swap.
        val swapped = engine(RelicId.GYRE_ENGINE, RelicId.BLINK_DRIVE, RelicId.GLASS_COMET, RelicId.PERPETUAL_VECTOR)
        swapped.acquireItem(swapped.item("Igniter").id)
        assertEquals(StatusElement.THERMAL, swapped.build.dominant)
        swapped.replaceRelic(0, RelicId.ABSOLUTE_ZERO)
        assertEquals(1, swapped.build.affinity(StatusElement.CRYO))
        assertEquals(StatusElement.CRYO, swapped.build.dominant)
    }

    @Test
    fun harmonyAndDissonanceScaleUpsidesWhileHollowCrownWeakensTheRest() {
        val harmony = engine(RelicId.GYRE_ENGINE, RelicId.STORMHEART_CAPACITOR, RelicId.BLINK_DRIVE)
        // Gyre shares Motion with both partners, and with the run's Flux Wake (weapon key harmony).
        assertEquals(1.21f * HARMONY_MAGNITUDE, harmony.build.relicUpside(RelicId.GYRE_ENGINE), 0.0001f)
        assertTrue(harmony.build.links.all { it.kind == RelicLinkKind.HARMONY })

        val dissonance = engine(RelicId.GYRE_ENGINE, RelicId.STASIS_ANCHOR)
        assertEquals(0.85f * HARMONY_MAGNITUDE, dissonance.build.relicUpside(RelicId.GYRE_ENGINE), 0.0001f)
        assertEquals(0.85f, dissonance.build.relicUpside(RelicId.STASIS_ANCHOR), 0.0001f)
        assertEquals(RelicLinkKind.DISSONANCE, dissonance.build.links.single().kind)

        // A named pairing replaces the generic key rule for its pair.
        val named = engine(RelicId.PERPETUAL_VECTOR, RelicId.GYRE_ENGINE)
        assertEquals(HARMONY_MAGNITUDE, named.build.relicUpside(RelicId.GYRE_ENGINE), 0.0001f)
        assertEquals(RelicLinkKind.CONCORD, named.build.links.single().kind)
        assertEquals(RelicPairingId.ENDLESS_GYRE, named.build.links.single().pairing)

        val crowned = engine(RelicId.HOLLOW_CROWN, RelicId.ECHO_CHAMBER, rebirth = 3)
        assertEquals(0.75f, crowned.build.relicUpside(RelicId.ECHO_CHAMBER), 0.0001f)
        assertEquals(1f, crowned.build.relicUpside(RelicId.HOLLOW_CROWN), 0.0001f)
    }

    @Test
    fun namedPairingsAreDetectedAndDiscoveredOnceWithASignedCue() {
        val state = engine()
        state.takeVisualFxCues()
        state.acquireRelic(RelicId.FURNACE_COVENANT)
        state.acquireRelic(RelicId.ABSOLUTE_ZERO)
        assertTrue(state.build.pairing(RelicPairingId.THERMAL_PARADOX))
        val discord = state.takeVisualFxCues().filterIsInstance<VisualFxCue.BuildChanged>()
            .single { it.title == "PAIRING DISCOVERED" }
        assertEquals(listOf("− Thermal Paradox"), discord.details.toList())

        state.acquireRelic(RelicId.OVERDRIVE_CASCADE)
        assertTrue(state.build.pairing(RelicPairingId.MELTDOWN))
        val concord = state.takeVisualFxCues().filterIsInstance<VisualFxCue.BuildChanged>()
            .single { it.title == "PAIRING DISCOVERED" }
        assertEquals(listOf("+ Meltdown"), concord.details.toList())

        val update = state.takeProgressUpdate()
        assertEquals(setOf(RelicPairingId.THERMAL_PARADOX, RelicPairingId.MELTDOWN), update?.discoveredPairingIds?.toSet())

        // Binding the same pair again in a later run announces nothing new.
        state.startRun()
        state.takeVisualFxCues()
        state.acquireRelic(RelicId.FURNACE_COVENANT)
        state.acquireRelic(RelicId.OVERDRIVE_CASCADE)
        assertTrue(state.takeVisualFxCues().none { it is VisualFxCue.BuildChanged && it.title == "PAIRING DISCOVERED" })
        assertTrue(state.takeProgressUpdate()?.discoveredPairingIds.isNullOrEmpty())
    }

    @Test
    fun concordPairingsChangeTheirRelicsRules() {
        // Meltdown: Overdrive starts at full heat.
        val meltdown = engine(RelicId.FURNACE_COVENANT, RelicId.OVERDRIVE_CASCADE)
        meltdown.overdriveCharge = 100f
        meltdown.updateOverdrive(0f)
        assertEquals(MutableGameState.MAX_HEAT, meltdown.heat)

        // Fold Vector: Blinks keep 55 % of speed but lose the heat discount.
        val fold = engine(RelicId.BLINK_DRIVE, RelicId.PERPETUAL_VECTOR)
        assertEquals(fold.dashHeatCost, fold.effectiveDashHeatCost(), 0.0001f)
        val blinkOnly = engine(RelicId.BLINK_DRIVE)
        assertEquals(blinkOnly.dashHeatCost * 0.65f, blinkOnly.effectiveDashHeatCost(), 0.0001f)
    }

    @Test
    fun combatFieldBoundAndRelicRemovalPreserveIsolatedSnapshots() {
        val state = engine(RelicId.ECHO_CHAMBER, RelicId.CINDER_CROWN)
        val bound = state.content.build.policy.maximumFields
        repeat(bound + 1) { index ->
            state.addField(CombatField(CombatFieldKind.ROT_WELL, remaining = 1f, x = index.toFloat(), y = 0f,
                radius = 50f, damagePerSecond = 1f))
        }
        assertEquals(bound, state.combatFields.size)
        assertEquals((bound - 1).toFloat(), state.combatFields.last().x)
        val enemy = state.addEnemyForTesting(x = 400f, y = 400f)
        enemy.status[kinetickk.ball.gameplay.nucleus.model.StatusCell.CINDER_BANK] = 12f
        state.echoHits += EchoHit(enemy.id, delay = 0.3f, damage = 4f, appliesDominant = false)
        val sourceFields = state.combatFields
        val sourceEchoes = state.echoHits.toList()

        // Replacing a relic removes the runtime it owned, only in the candidate.
        val copy = state.copyForReduction()
        copy.replaceRelic(0, RelicId.GYRE_ENGINE)
        copy.replaceRelic(1, RelicId.BLINK_DRIVE)
        assertTrue(copy.echoHits.isEmpty())
        assertEquals(0f, copy.enemies.single().status[kinetickk.ball.gameplay.nucleus.model.StatusCell.CINDER_BANK])
        copy.updateCombatFields(2f)
        assertTrue(copy.combatFields.isEmpty())
        assertNotSame(sourceFields, copy.combatFields)

        assertEquals(sourceEchoes, state.echoHits.toList())
        assertEquals(12f, state.enemies.single().status[kinetickk.ball.gameplay.nucleus.model.StatusCell.CINDER_BANK])
        assertEquals(sourceFields, state.combatFields)
        assertEquals(bound, state.combatFields.size)
        copy.startRun()
        assertTrue(copy.combatFields.isEmpty())
        assertTrue(copy.echoHits.isEmpty())
        assertNull(copy.severedRelics.firstOrNull())
        assertFalse(state.combatFields.isEmpty())
    }

    private fun engine(
        vararg relics: RelicId,
        shape: CoreShape = CoreShape.ORB,
        rebirth: Int = 0,
    ): MutableGameState = MutableGameState(canonicalGameplayContent, seed = 4_242, initialRebirthLevel = rebirth).apply {
        coreShape = shape
        startRun()
        enemies.clear()
        relics.forEach(::acquireRelic)
    }

    private fun MutableGameState.item(name: String) = content.items.single { it.name == name }
}
