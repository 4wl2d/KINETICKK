// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.projection.*
import kinetickk.ball.gameplay.nucleus.render.TotemChestProjection
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The render contract of Totem chests: waiting chests publish with their stable facts and keep their instance while
 * nothing changes (by identity on the pure path, by raw float bits otherwise); a dimension publishes none; the rolled
 * charges join the structure and dungeon projections and their reuse.
 */
class TotemChestProjectionTest {
    @Test
    fun chestProjectionsReuseByIdentityAndContentAndHideOutsideRealspace() {
        val run = buildTestState(content = structuresDisabledContent, seed = 61)
        val empty = run.toRenderModel()
        assertTrue(empty.totemChests.isEmpty())
        val chest = run.placeTotemChestForTesting(x = 120f, y = -40f, charges = 2)
        val first = run.toRenderModel(reusableCollections = empty)
        assertEquals(listOf(TotemChestProjection(chest.structureId, 120f, -40f, 2, chest.bornAt, chest.armedAt)), first.totemChests)

        // The pure path: the same list as the identity source keeps the projection.
        val candidate = run.deepCopy()
        assertSame(first.totemChests, candidate.toRenderModel(reusableCollections = first, identitySource = run).totemChests)
        // The tick path: an equal list (a fresh instance) keeps it by content.
        run.structures.totemChests = run.structures.totemChests.toList()
        assertSame(first.totemChests, run.toRenderModel(reusableCollections = first).totemChests)
        // An open replaces it.
        run.structures.totemChests = listOf(chest.copy(charges = 1, armedAt = chest.armedAt + 0.35f))
        val opened = run.toRenderModel(reusableCollections = first).totemChests
        assertNotSame(first.totemChests, opened)
        assertEquals(1, opened.single().charges)
        // A rebase moves it; -0 and +0 differ by their bits.
        run.structures.totemChests = listOf(chest.copy(x = -0f))
        val negative = run.toRenderModel()
        run.structures.totemChests = listOf(chest.copy(x = 0f))
        assertNotSame(negative.totemChests, run.toRenderModel(reusableCollections = negative).totemChests)

        // Outside realspace the shared empty list is published.
        run.worldLayer = WorldLayer.NADIR
        val hidden = run.toRenderModel(reusableCollections = first).totemChests
        assertTrue(hidden.isEmpty())
        assertSame(hidden, run.toRenderModel().totemChests)
        assertSame(empty.totemChests, hidden, "every empty publication shares one instance")
    }

    @Test
    fun chestChargesJoinTheStructureAndDungeonReuse() {
        val site = DungeonFixture(StructureTemplateId.VAULT, configure = { policy ->
            policy.copy(templates = policy.templates.map { if (it.secret) it else it.copy(totemChestChance = 1f) }.toImmutableList())
        })
        val run = site.run
        val charges = site.structure.chestCharges
        assertTrue(charges >= 2, "a Vault chest rolls at least two charges")
        val first = run.toRenderModel()
        val index = run.structureField.indexOf(site.placement.id)
        assertEquals(charges, first.structures.structures[index].chestCharges)
        assertSame(first.structures.structures, run.toRenderModel(reusableCollections = first).structures.structures)

        site.enterRoom(0)
        val held = run.toRenderModel().structures
        assertEquals(charges, held.dungeon?.chestCharges)
        val retained = run.toRenderModel()
        assertSame(retained.structures.dungeon, run.toRenderModel(reusableCollections = retained).structures.dungeon)

        // Different charges never reuse the previous projection.
        val structure = run.structureField.structures[index]
        val changed = run.structureField.withStructure(index, structure.copy(chestCharges = charges + 1), run.structureField.walls.subList(structure.wallStart, structure.wallEnd))
        run.structureField = changed
        run.dungeon = run.dungeon!!.copy(chestCharges = charges + 1)
        val moved = run.toRenderModel(reusableCollections = retained).structures
        assertNotSame(retained.structures.structures, moved.structures)
        assertEquals(charges + 1, moved.structures[index].chestCharges)
        assertNotSame(retained.structures.dungeon, moved.dungeon)
        assertEquals(charges + 1, moved.dungeon?.chestCharges)
    }
}
