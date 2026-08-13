// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.Totem
import kinetickk.ball.gameplay.nucleus.model.TrailPoint
import kinetickk.ball.gameplay.nucleus.model.WeaponNode
import kinetickk.ball.gameplay.nucleus.model.WeaponOrbital
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameplayRenderModelMapperTest {
    @Test
    fun identityMatchedScalarSourceReusesEveryProjectionWithoutReadingStableLists() {
        val source = fullyPopulatedState()
        val guardedRelics = ReadGuardList(source.equippedRelics)
        val guardedChoices = ReadGuardList(source.choices)
        source.equippedRelics = guardedRelics
        source.choices = guardedChoices
        val retained = source.toRenderModel()
        val candidate = source.copyForScalarInputReduction()
        guardedRelics.rejectReads = true
        guardedChoices.rejectReads = true

        val projected = candidate.toRenderModel(
            reusableCollections = retained,
            identitySource = source,
        )

        assertSame(retained.equippedRelics, projected.equippedRelics)
        assertSame(retained.totem, projected.totem)
        assertSame(retained.enemies, projected.enemies)
        assertSame(retained.projectiles, projected.projectiles)
        assertSame(retained.pickups, projected.pickups)
        assertSame(retained.trail, projected.trail)
        assertSame(retained.weaponNodes, projected.weaponNodes)
        assertSame(retained.weaponOrbitals, projected.weaponOrbitals)
        assertSame(retained.choices, projected.choices)
        assertSame(retained.itemStacks, projected.itemStacks)
        assertSame(retained.discoveredItemIds, projected.discoveredItemIds)
        assertSame(retained.relicRanks, projected.relicRanks)
        assertTrue(candidate.itemStacks.sharesStorageWith(source.itemStacks))
        assertTrue(candidate.discoveredItemIds.sharesStorageWith(source.discoveredItemIds))
        assertTrue(candidate.relicRanks.sharesStorageWith(source.relicRanks))
    }

    @Test
    fun equalStableCollectionsAndEmptyDynamicProjectionsAreStructurallyShared() {
        val source = populatedState()
        val retained = source.toRenderModel()
        val unchangedCandidate = source.copyForReduction()

        val projected = unchangedCandidate.toRenderModel(retained)

        assertSame(retained.equippedRelics, projected.equippedRelics)
        assertSame(retained.choices, projected.choices)
        assertSame(retained.itemStacks, projected.itemStacks)
        assertSame(retained.discoveredItemIds, projected.discoveredItemIds)
        assertSame(retained.relicRanks, projected.relicRanks)
        assertSame(retained.enemies, projected.enemies)
        assertSame(retained.projectiles, projected.projectiles)
        assertSame(retained.pickups, projected.pickups)
        assertSame(retained.trail, projected.trail)
        assertSame(retained.weaponNodes, projected.weaponNodes)
        assertSame(retained.weaponOrbitals, projected.weaponOrbitals)
    }

    @Test
    fun changedStableCollectionsAreRebuiltAndRetainedProjectionStaysImmutable() {
        val source = populatedState()
        val retained = source.toRenderModel()
        val changedCandidate = source.copyForReduction().apply {
            equippedRelics = listOf(EquippedRelic(RelicId.KINETIC_FLYWHEEL, rank = 2))
            choices = listOf(choice(title = "Changed"))
            itemStacks[0] = 3
            discoveredItemIds += 202
            relicRanks[0] = 2
        }

        val projected = changedCandidate.toRenderModel(retained)

        assertNotSame(retained.equippedRelics, projected.equippedRelics)
        assertNotSame(retained.choices, projected.choices)
        assertNotSame(retained.itemStacks, projected.itemStacks)
        assertNotSame(retained.discoveredItemIds, projected.discoveredItemIds)
        assertNotSame(retained.relicRanks, projected.relicRanks)
        assertEquals(1, retained.equippedRelics.single().rank)
        assertEquals("Initial", retained.choices.single().title)
        assertEquals(2, retained.itemStack(0))
        assertEquals(1, retained.discoveredItemCount)
        assertEquals(1, retained.relicRank(RelicId.KINETIC_FLYWHEEL))
        assertEquals(2, projected.equippedRelics.single().rank)
        assertEquals("Changed", projected.choices.single().title)
        assertEquals(3, projected.itemStack(0))
        assertEquals(2, projected.discoveredItemCount)
        assertEquals(2, projected.relicRank(RelicId.KINETIC_FLYWHEEL))
    }

    @Test
    fun equalNonEmptyEntitiesAreSharedButMutationsReprojectWithoutChangingRetainedSnapshot() {
        val source = populatedState()
        source.addEnemyForTesting(x = 10f, y = 20f)
        source.projectiles += Projectile(11f, 21f, 0f, 0f, radius = 2f, life = 1f)
        source.pickups += Pickup(PickupType.DATA, 12f, 22f)
        source.trail += TrailPoint(13f, 23f)
        source.weaponNodes += WeaponNode(
            type = WeaponNodeType.GRAVITY_MINE,
            x = 14f,
            y = 24f,
            life = 1f,
            maxLife = 2f,
            radius = 3f,
        )
        source.weaponOrbitals += WeaponOrbital(index = 0, x = 15f, y = 25f, radius = 4f)
        val retained = source.toRenderModel()
        val candidate = source.copyForReduction()

        val unchangedProjection = candidate.toRenderModel(retained)
        candidate.enemies.single().x = 30f
        candidate.projectiles.single().x = 31f
        candidate.pickups.single().x = 32f
        candidate.trail.single().x = 33f
        candidate.weaponNodes.single().x = 34f
        candidate.weaponOrbitals.single().x = 35f
        val changedProjection = candidate.toRenderModel(unchangedProjection)

        assertSame(retained.enemies, unchangedProjection.enemies)
        assertSame(retained.projectiles, unchangedProjection.projectiles)
        assertSame(retained.pickups, unchangedProjection.pickups)
        assertSame(retained.trail, unchangedProjection.trail)
        assertSame(retained.weaponNodes, unchangedProjection.weaponNodes)
        assertSame(retained.weaponOrbitals, unchangedProjection.weaponOrbitals)
        assertNotSame(unchangedProjection.enemies, changedProjection.enemies)
        assertNotSame(unchangedProjection.projectiles, changedProjection.projectiles)
        assertNotSame(unchangedProjection.pickups, changedProjection.pickups)
        assertNotSame(unchangedProjection.trail, changedProjection.trail)
        assertNotSame(unchangedProjection.weaponNodes, changedProjection.weaponNodes)
        assertNotSame(unchangedProjection.weaponOrbitals, changedProjection.weaponOrbitals)
        assertEquals(10f, retained.enemies.single().x)
        assertEquals(11f, retained.projectiles.single().x)
        assertEquals(12f, retained.pickups.single().x)
        assertEquals(13f, retained.trail.single().x)
        assertEquals(14f, retained.weaponNodes.single().x)
        assertEquals(15f, retained.weaponOrbitals.single().x)
        assertEquals(10f, unchangedProjection.enemies.single().x)
        assertEquals(30f, changedProjection.enemies.single().x)
        assertEquals(31f, changedProjection.projectiles.single().x)
        assertEquals(32f, changedProjection.pickups.single().x)
        assertEquals(33f, changedProjection.trail.single().x)
        assertEquals(34f, changedProjection.weaponNodes.single().x)
        assertEquals(35f, changedProjection.weaponOrbitals.single().x)
    }

    @Test
    fun fullReductionRebuildsOnlyTheChangedProjectionFamily() {
        val source = fullyPopulatedState()
        val retained = source.toRenderModel()
        val candidate = source.copyForReduction().apply {
            enemies.single().x = 44f
        }

        val projected = candidate.toRenderModel(
            reusableCollections = retained,
            identitySource = source,
        )

        assertNotSame(retained.enemies, projected.enemies)
        assertSame(retained.equippedRelics, projected.equippedRelics)
        assertSame(retained.totem, projected.totem)
        assertSame(retained.projectiles, projected.projectiles)
        assertSame(retained.pickups, projected.pickups)
        assertSame(retained.trail, projected.trail)
        assertSame(retained.weaponNodes, projected.weaponNodes)
        assertSame(retained.weaponOrbitals, projected.weaponOrbitals)
        assertSame(retained.choices, projected.choices)
        assertSame(retained.itemStacks, projected.itemStacks)
        assertSame(retained.discoveredItemIds, projected.discoveredItemIds)
        assertSame(retained.relicRanks, projected.relicRanks)
        assertEquals(10f, retained.enemies.single().x)
        assertEquals(44f, projected.enemies.single().x)
    }

    @Test
    fun mismatchedIdentitySourceUsesExactFallbackAndCannotReuseChangedStorage() {
        val source = fullyPopulatedState()
        val retained = source.toRenderModel()
        val candidate = source.copyForReduction().apply {
            choices = listOf(choice(title = "Changed"))
            itemStacks[0] = 7
            discoveredItemIds += 303
            relicRanks[0] = 3
            totem!!.x = 91f
            enemies.single().x = 92f
        }
        val unrelatedSource = fullyPopulatedState()

        val projected = candidate.toRenderModel(
            reusableCollections = retained,
            identitySource = unrelatedSource,
        )

        assertNotSame(retained.choices, projected.choices)
        assertNotSame(retained.itemStacks, projected.itemStacks)
        assertNotSame(retained.discoveredItemIds, projected.discoveredItemIds)
        assertNotSame(retained.relicRanks, projected.relicRanks)
        assertNotSame(retained.totem, projected.totem)
        assertNotSame(retained.enemies, projected.enemies)
        assertEquals("Initial", retained.choices.single().title)
        assertEquals(2, retained.itemStack(0))
        assertEquals(10f, retained.totem!!.x)
        assertEquals(10f, retained.enemies.single().x)
        assertEquals("Changed", projected.choices.single().title)
        assertEquals(7, projected.itemStack(0))
        assertEquals(91f, projected.totem!!.x)
        assertEquals(92f, projected.enemies.single().x)
    }

    @Test
    fun identityMismatchFallsBackToRawFloatBitsRatherThanNumericEquality() {
        val source = populatedState().apply {
            totem = Totem(x = -0.0f, y = 1f, pulse = 2f)
        }
        val retained = source.toRenderModel()
        val candidate = source.copyForReduction()
        val unrelatedSource = populatedState()

        val bitExact = candidate.toRenderModel(
            reusableCollections = retained,
            identitySource = unrelatedSource,
        )
        candidate.totem!!.x = 0.0f
        val differentBits = candidate.toRenderModel(
            reusableCollections = retained,
            identitySource = unrelatedSource,
        )

        assertSame(retained.totem, bitExact.totem)
        assertNotSame(retained.totem, differentBits.totem)
        assertEquals((-0.0f).toRawBits(), retained.totem!!.x.toRawBits())
        assertEquals(0.0f.toRawBits(), differentBits.totem!!.x.toRawBits())
    }

    private fun populatedState(): MutableGameState = MutableGameState(
        content = canonicalGameplayContent,
        seed = 712,
        initialMatter = 0,
    ).apply {
        equippedRelics = listOf(EquippedRelic(RelicId.KINETIC_FLYWHEEL, rank = 1))
        choices = listOf(choice(title = "Initial"))
        itemStacks[0] = 2
        discoveredItemIds += 101
        relicRanks[0] = 1
    }

    private fun fullyPopulatedState(): MutableGameState = populatedState().apply {
        totem = Totem(x = 10f, y = 20f, pulse = 0.5f)
        addEnemyForTesting(x = 10f, y = 20f)
        projectiles += Projectile(11f, 21f, 0f, 0f, radius = 2f, life = 1f)
        pickups += Pickup(PickupType.DATA, 12f, 22f)
        trail += TrailPoint(13f, 23f)
        weaponNodes += WeaponNode(
            type = WeaponNodeType.GRAVITY_MINE,
            x = 14f,
            y = 24f,
            life = 1f,
            maxLife = 2f,
            radius = 3f,
        )
        weaponOrbitals += WeaponOrbital(index = 0, x = 15f, y = 25f, radius = 4f)
    }

    private fun choice(title: String): ChoiceOption = ChoiceOption(
        type = ChoiceType.ITEM,
        title = title,
        description = "$title choice",
        tag = "TEST",
        itemId = canonicalGameplayContent.items.first().id,
    )

    private class ReadGuardList<Element>(
        private val values: List<Element>,
    ) : AbstractList<Element>() {
        var rejectReads: Boolean = false

        override val size: Int
            get() = values.size

        override fun get(index: Int): Element {
            check(!rejectReads) { "Identity reuse unexpectedly scanned shared stable storage" }
            return values[index]
        }
    }
}
