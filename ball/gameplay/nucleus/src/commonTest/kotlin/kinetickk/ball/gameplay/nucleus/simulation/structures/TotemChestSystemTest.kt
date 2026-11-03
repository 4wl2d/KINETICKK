// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.StructureNotice
import kinetickk.ball.content.api.StructurePolicy
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.TotemChestPolicy
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.projection.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Totem chests: the hashed roll a structure carries from its placement, the chest its cleared final room materialises,
 * the claim (one Key and one charge per TOTEM choice, re-armed after a moment of play, shattered with its last charge,
 * locked without a Key), and every lifecycle edge: dimensions, pause, the Architect, rebase, a new run, a full list,
 * previews, reduction siblings, revisits and directed weapon rewards.
 */
class TotemChestSystemTest {
    private val step = MutableGameState.FIXED_STEP

    // -----------------------------------------------------------------------------------------
    // The roll and the materialised chest.

    @Test
    fun aClearedFinalRoomMaterialisesItsRolledChestAtTheRoomCentre() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION, configure = forcedChest())
        val run = dungeon.run
        val rolled = totemChestCharges(run.content.structures, dungeon.template, run.runSeed, WorldLayer.BASE, 12, -9)
        assertTrue(rolled in 1..ContentBounds.MAX_TOTEM_CHEST_CHARGES)
        assertEquals(rolled, dungeon.structure.chestCharges, "the window carries the roll")
        dungeon.clear(0)
        assertEquals(rolled, run.dungeon!!.chestCharges, "the dungeon carries it from entry")
        assertEquals(rolled, run.toRenderModel().structures.dungeon?.chestCharges, "the panel shows it")
        listOf(1, 3, 2).forEach(dungeon::clear)

        val chest = run.structures.totemChests.single()
        val (centreX, centreY) = dungeon.roomCentre(dungeon.template.finalRoom)
        assertEquals(dungeon.placement.id, chest.structureId)
        assertEquals(rolled, chest.charges)
        assertEquals(centreX, chest.x, 0.01f)
        assertEquals(centreY, chest.y, 0.01f)
        assertEquals(run.elapsed, chest.bornAt)
        assertEquals(chest.bornAt + 0.6f, chest.armedAt)
    }

    @Test
    fun aChancelessOrSecretTemplatePaysNoChest() {
        val chanceless = DungeonFixture(StructureTemplateId.DERELICT_STATION, configure = forcedChest(chance = 0f))
        assertEquals(0, chanceless.structure.chestCharges)
        listOf(0, 1, 3, 2).forEach(chanceless::clear)
        assertTrue(chanceless.run.structures.totemChests.isEmpty())

        val doodle = DungeonFixture(StructureTemplateId.ARCHITECTS_DOODLE)
        assertEquals(0, doodle.structure.chestCharges)
        doodle.moveCore(doodle.roomCentre(0))
        doodle.run.updateStructures(step)
        doodle.run.updateStructures(step)
        assertTrue(doodle.run.isSpent(doodle.placement.id))
        assertTrue(doodle.run.structures.totemChests.isEmpty())
    }

    @Test
    fun theChestRollIsAPureHashAndDrawsFromNoRunStream() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION, configure = forcedChest())
        val run = dungeon.run
        val policy = run.content.structures
        val template = dungeon.template
        val rolled = dungeon.structure.chestCharges
        val streams = run.streams()
        run.teleportCoreForTesting(dungeon.placement.absoluteX + 30_000.0, dungeon.placement.absoluteY)
        run.teleportCoreForTesting(dungeon.placement.absoluteX, dungeon.placement.absoluteY)
        assertEquals(rolled, dungeon.structure.chestCharges, "a window round trip keeps the roll")
        run.coreX = 300_000f
        run.rebaseWorldIfNeeded()
        run.teleportCoreForTesting(dungeon.placement.absoluteX, dungeon.placement.absoluteY)
        assertEquals(rolled, dungeon.structure.chestCharges, "a rebase keeps the roll")
        assertEquals(streams, run.streams(), "window builds draw from no stream")
        val fresh = DungeonFixture(StructureTemplateId.DERELICT_STATION, configure = forcedChest())
        assertEquals(rolled, fresh.structure.chestCharges, "the same seed rolls the same chest")

        val sample = (0 until 64).map { totemChestCharges(policy, template, 77, WorldLayer.BASE, it, -it) }
        assertNotEquals(1, sample.toSet().size, "cells roll differently")
        val seeds = (0 until 64).map { totemChestCharges(policy, template, it, WorldLayer.BASE, 5, 5) }
        assertNotEquals(1, seeds.toSet().size, "seeds roll differently")
        assertEquals(sample, (0 until 64).map { totemChestCharges(policy, template, 77, WorldLayer.BASE, it, -it) })
    }

    @Test
    fun chargeWeightsAndMinimumsShapeTheRoll() {
        val policy = canonicalGameplayContent.structures
        val sure = policy.template(StructureTemplateId.DERELICT_STATION).copy(totemChestChance = 1f)
        val counts = IntArray(4)
        for (index in 0 until 4_000) counts[totemChestCharges(policy, sure, 20_261_103, WorldLayer.BASE, index % 80, index / 80)]++
        assertEquals(0, counts[0], "a sure chance always pays a chest")
        // Recorded on this fixed sample: 1 = 2,189, 2 = 1,312, 3 = 499 of 4,000.
        assertEquals(listOf(2_189, 1_312, 499), counts.drop(1))
        assertEquals(0.55, counts[1] / 4_000.0, 0.03)
        assertEquals(0.33, counts[2] / 4_000.0, 0.03)
        assertEquals(0.12, counts[3] / 4_000.0, 0.03)

        for (id in listOf(StructureTemplateId.VAULT, StructureTemplateId.RELIQUARY)) {
            val template = policy.template(id)
            val rolls = (0 until 400).map { totemChestCharges(policy, template, 9, WorldLayer.BASE, it, 3) }
            assertTrue(rolls.none { it == 1 }, "$id never rolls a single charge")
            if (id == StructureTemplateId.RELIQUARY) assertTrue(rolls.all { it >= 2 }, "the Reliquary always pays")
        }
        val off = policy.copy(totemChest = TotemChestPolicy(enabled = false))
        assertTrue((0 until 400).all { totemChestCharges(off, sure, 9, WorldLayer.BASE, it, 3) == 0 })
    }

    // -----------------------------------------------------------------------------------------
    // The claim.

    @Test
    fun contactWithAKeyOpensOneChargeAndTheTotemChoice() {
        val run = chestRun()
        run.keys = 2
        run.placeTotemChestForTesting(charges = 3)
        run.simulateStep(step)
        assertEquals(1, run.keys)
        assertEquals(2, run.structures.totemChests.single().charges)
        assertEquals(GamePhase.CHOICE, run.phase)
        assertEquals(ChoiceType.TOTEM, run.choiceType)
        assertTrue(GameplayAudioCue.PICKUP in run.takeSoundCues())
    }

    @Test
    fun withoutAKeyTheChestWaitsLockedForTheRun() {
        val run = chestRun()
        val chest = run.placeTotemChestForTesting(charges = 2)
        repeat(1_200) {
            restOn(run, chest.x, chest.y)
            run.simulateStep(step)
        }
        assertEquals(GamePhase.RUNNING, run.phase)
        assertEquals(0, run.keys)
        assertEquals(listOf(chest), run.structures.totemChests)
    }

    @Test
    fun aClosedChoiceReArmsTheChestAfterAThirdOfASecondOfPlay() {
        val run = chestRun()
        run.keys = 2
        run.placeTotemChestForTesting(charges = 2)
        run.simulateStep(step)
        val openedAt = run.elapsed
        val armedAt = run.structures.totemChests.single().armedAt
        assertEquals(openedAt + run.content.structures.totemChest.rearmSeconds, armedAt)
        repeat(10) { run.update(step) }
        assertEquals(openedAt, run.elapsed, "no run time passes while the choice is open")
        run.choose(run.choices.indexOfFirst { it.totemAction == TotemAction.AMPLIFY_CURRENT })
        assertEquals(GamePhase.RUNNING, run.phase)
        while (run.elapsed + step < armedAt) {
            restOn(run, run.structures.totemChests.single().x, run.structures.totemChests.single().y)
            run.simulateStep(step)
            assertEquals(GamePhase.RUNNING, run.phase, "still re-arming at ${run.elapsed}")
        }
        run.simulateStep(step)
        assertTrue(run.elapsed >= armedAt)
        assertEquals(GamePhase.CHOICE, run.phase)
        assertEquals(0, run.keys)
    }

    @Test
    fun restingOnTheChestSpendsOneKeyPerCharge() {
        val run = chestRun()
        run.keys = 5
        val chest = run.placeTotemChestForTesting(charges = 3)
        var choices = 0
        var shattered = false
        repeat(600) {
            run.takeVisualFxCues()
            restOn(run, chest.x, chest.y)
            run.simulateStep(step)
            if (run.phase == GamePhase.CHOICE) {
                choices++
                if (run.structures.totemChests.isEmpty()) {
                    shattered = GameplayAudioCue.IMPACT in run.takeSoundCues()
                }
                run.choose(run.choices.indexOfFirst { it.totemAction == TotemAction.AMPLIFY_CURRENT })
            }
        }
        assertEquals(3, choices)
        assertEquals(2, run.keys)
        assertTrue(run.structures.totemChests.isEmpty())
        assertTrue(shattered, "the last charge shatters with an impact")
    }

    @Test
    fun theLastChargeShattersAndRemovesTheChest() {
        val run = chestRun()
        run.keys = 1
        val chest = run.placeTotemChestForTesting(charges = 1)
        val other = run.placeTotemChestForTesting(x = run.coreX + 5_000f, charges = 2)
        run.takeVisualFxCues()
        run.simulateStep(step)
        assertEquals(listOf(other), run.structures.totemChests)
        val cues = run.takeVisualFxCues()
        assertTrue(cues.any { it is VisualFxCue.ShockwaveAdded && it.x == chest.x && it.y == chest.y && it.maxRadius == 220f })
        assertTrue(cues.any { it is VisualFxCue.Burst && it.x == chest.x && it.requestedCount == 32 })
        val sounds = run.takeSoundCues()
        assertTrue(GameplayAudioCue.IMPACT in sounds && GameplayAudioCue.PICKUP in sounds)
        assertEquals(ChoiceType.TOTEM, run.choiceType)
    }

    @Test
    fun chestsOpenOnlyInRealspaceWhileRunning() {
        val run = chestRun()
        run.keys = 1
        val chest = run.placeTotemChestForTesting(charges = 2)
        switchLayerForTesting(run, WorldLayer.ZENITH)
        run.updateTotemChests()
        assertEquals(GamePhase.RUNNING, run.phase)
        assertEquals(listOf(chest), run.structures.totemChests)
        assertTrue(run.toRenderModel().totemChests.isEmpty(), "a dimension publishes no chest")
        switchLayerForTesting(run, WorldLayer.BASE)
        assertEquals(1, run.toRenderModel().totemChests.size)

        for (phase in listOf(GamePhase.PAUSED, GamePhase.GAME_OVER, GamePhase.VICTORY)) {
            run.phase = phase
            run.updateTotemChests()
            assertEquals(1, run.keys, "$phase opens nothing")
        }
        run.phase = GamePhase.RUNNING
        run.updateTotemChests()
        assertEquals(0, run.keys, "back in realspace and running it opens")
        assertEquals(ChoiceType.TOTEM, run.choiceType)
    }

    // -----------------------------------------------------------------------------------------
    // Lifecycle.

    @Test
    fun chestsSurviveTheArchitectCollapseAndStayClaimable() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION, configure = forcedChest())
        val run = dungeon.run
        listOf(0, 1, 3, 2).forEach(dungeon::clear)
        val chests = run.structures.totemChests
        assertEquals(1, chests.size)
        run.bossSpawned = true
        run.updateStructures(step)
        assertTrue(run.structureField.bossCollapsed)
        assertSame(chests, run.structures.totemChests, "the collapse leaves chests alone")
        run.keys = 1
        dungeon.moveCore(chests[0].x to chests[0].y)
        run.elapsed = chests[0].armedAt
        run.updateTotemChests()
        assertEquals(0, run.keys)
        assertEquals(ChoiceType.TOTEM, run.choiceType)
    }

    @Test
    fun chestsFollowARebaseAndANewRunClearsThem() {
        val run = chestRun()
        val chest = run.placeTotemChestForTesting(x = run.coreX + 300_000f, charges = 2)
        run.coreX += 300_000f
        val originX = run.worldOriginX
        run.rebaseWorldIfNeeded()
        val shift = (run.worldOriginX - originX).toFloat()
        assertNotEquals(0f, shift)
        val moved = run.structures.totemChests.single()
        assertEquals(chest.x - shift, moved.x)
        assertEquals(chest.y, moved.y)
        assertEquals(chest.bornAt, moved.bornAt, "run times stay")
        assertEquals(chest.armedAt, moved.armedAt)
        run.startRun()
        assertTrue(run.structures.totemChests.isEmpty(), "a new run starts without chests")
        val none = run.structures.totemChests
        run.rebaseTotemChests(10f, 10f)
        assertSame(none, run.structures.totemChests, "an empty list keeps its instance")
    }

    @Test
    fun aFullChestListSalvagesANewChestsChargesAsMatter() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION, configure = forcedChest())
        val run = dungeon.run
        repeat(ContentBounds.MAX_TOTEM_CHESTS) { run.placeTotemChestForTesting(x = 9_000f + 100f * it, charges = 1) }
        val full = run.structures.totemChests
        val charges = dungeon.structure.chestCharges
        dungeon.enterRoom(2)
        run.killGarrisonForTesting()
        val before = run.matter()
        val twin = run.deepCopy()
        twin.grantMatter(charges * 40f)
        run.updateStructures(step)
        assertSame(full, run.structures.totemChests, "the list stays at its bound")
        assertEquals(twin.matter(), run.matter(), 1e-6)
        assertTrue(run.matter() > before)
        assertEquals(StructureNotice.TOTEM_CHEST_SALVAGED.key, run.message)
    }

    @Test
    fun rewardPreviewsOfAChestChoiceNeverSpendKeysOrCharges() {
        val run = chestRun()
        run.keys = 3
        run.placeTotemChestForTesting(charges = 3)
        run.simulateStep(step)
        assertEquals(ChoiceType.TOTEM, run.choiceType)
        val keys = run.keys
        val chests = run.structures.totemChests
        run.rewardPreviews()
        run.toRenderModel()
        assertEquals(keys, run.keys)
        assertSame(chests, run.structures.totemChests)
    }

    @Test
    fun reductionSiblingsShareNoChestState() {
        val source = chestRun()
        source.keys = 2
        source.placeTotemChestForTesting(charges = 2)
        val chests = source.structures.totemChests
        val sibling = source.deepCopy()
        sibling.simulateStep(step)
        assertEquals(1, sibling.keys)
        assertEquals(1, sibling.structures.totemChests.single().charges)
        assertEquals(2, source.keys)
        assertSame(chests, source.structures.totemChests)
        assertEquals(2, chests.single().charges)
    }

    @Test
    fun aStructurePaysOneChestOnly() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION, configure = forcedChest())
        val run = dungeon.run
        listOf(0, 1, 3, 2).forEach(dungeon::clear)
        assertEquals(1, run.structures.totemChests.size)
        dungeon.enterRoom(2)
        dungeon.enterRoom(1)
        assertEquals(1, run.structures.totemChests.size, "re-entry pays nothing")
        run.teleportCoreForTesting(dungeon.placement.absoluteX + 30_000.0, dungeon.placement.absoluteY)
        run.teleportCoreForTesting(dungeon.placement.absoluteX, dungeon.placement.absoluteY)
        dungeon.enterRoom(2)
        switchLayerForTesting(run, WorldLayer.ZENITH)
        switchLayerForTesting(run, WorldLayer.BASE)
        dungeon.enterRoom(2)
        assertEquals(1, run.structures.totemChests.size, "window and layer round trips pay nothing")
        assertEquals(dungeon.placement.id, run.structures.totemChests.single().structureId)
    }

    @Test
    fun aDirectedWeaponSiteTotemChoiceNeverTouchesChests() {
        val run = chestRun()
        run.unlockWeaponsForTesting(WeaponId.entries)
        run.keys = 1
        val chest = run.placeTotemChestForTesting(x = run.coreX + 4_000f, charges = 2)
        run.openDirectedReward(DirectedReward.WEAPON)
        assertEquals(ChoiceType.TOTEM, run.choiceType)
        run.choose(0)
        run.choose(run.choices.indexOfFirst { it.totemAction == TotemAction.AMPLIFY_CURRENT }.coerceAtLeast(0))
        assertEquals(1, run.keys)
        assertEquals(listOf(chest), run.structures.totemChests)
    }

    // -----------------------------------------------------------------------------------------
    // Fixtures.

    /** Forces every template's chest: [chance] and at least [minimum] charges. */
    private fun forcedChest(chance: Float = 1f, minimum: Int = 1): (StructurePolicy) -> StructurePolicy = { policy ->
        policy.copy(templates = policy.templates.map {
            if (it.secret) it else it.copy(totemChestChance = chance, totemChestMinCharges = minimum)
        }.toImmutableList())
    }

    /** A quiet started run in a world without structures, the Core at rest. */
    private fun chestRun(): MutableGameState = buildTestState(content = structuresDisabledContent, seed = 61).apply {
        quietArenaForTesting()
        setVelocityForTesting(0f, 0f)
        previousCoreX = coreX
        previousCoreY = coreY
    }

    /** Holds the Core still at ([x], [y]), so no field or knot drifts it off the chest. */
    private fun restOn(run: MutableGameState, x: Float, y: Float) {
        run.coreX = x
        run.coreY = y
        run.previousCoreX = x
        run.previousCoreY = y
        run.setVelocityForTesting(0f, 0f)
    }

    private fun switchLayerForTesting(run: MutableGameState, target: WorldLayer) {
        run.worldLayer = target
        run.worldKeepOut = null
        run.switchWorldWindow()
        run.onWorldLayerChanged()
    }
}
