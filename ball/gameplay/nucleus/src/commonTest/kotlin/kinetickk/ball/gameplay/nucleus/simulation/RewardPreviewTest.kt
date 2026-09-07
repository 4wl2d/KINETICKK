// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.*

class RewardPreviewTest {
    @Test
    fun everyArtifactForecastMatchesSelectionIncludingCapsAndThirdFamilyBonus() {
        for (item in canonicalGameplayContent.items) {
            val state = engine().apply {
                acquireItem(item.id)
                acquireItem(item.id)
                critChance = 0.749f
                damageReduction = 0.649f
                dashHeatCost = 12.01f
                overdriveTime = 2f
                offer(ChoiceOption(ChoiceType.ITEM, item.name, item.description, "", itemId = item.id))
            }
            val before = state.buildStats()
            val preview = state.rewardPreviews().single()
            val selected = state.copyForReduction().apply { choose(0) }
            val after = selected.buildStats()
            before.zip(after).forEach { (old, new) ->
                val forecast = preview.changes.firstOrNull { it.name == old.name }
                if (kotlin.math.abs(old.value - new.value) >= 0.00001f) {
                    assertNotNull(forecast, "${item.name}: ${old.name}")
                    assertEquals(old.value, forecast.before)
                    assertEquals(new.value, forecast.after)
                } else assertNull(forecast)
            }
            assertTrue(preview.changes.any { it.name == "Weapon power" }, "Third family copy must include resonance")
            assertEquals(before, state.buildStats(), "Read must preserve accepted stats")
            assertEquals(2, state.itemStack(item.id))
        }
    }

    @Test
    fun repeatedReadsPreserveRandomnessDiscoveryAndOutputs() {
        val state = engine().apply { openItemChoice() }
        val expected = state.copyForReduction()
        repeat(5) { state.rewardPreviews() }
        assertEquals(expected.gameplayRandom.nextInt(), state.gameplayRandom.nextInt())
        assertEquals(expected.takeProgressUpdate(), state.takeProgressUpdate())
        assertEquals(expected.takeSoundCues(), state.takeSoundCues())
        assertEquals(expected.takeVisualFxCues(), state.takeVisualFxCues())
        assertEquals(expected.choices, state.choices)
        assertEquals(expected.buildStats(), state.buildStats())
    }

    @Test
    fun amplificationCrossesMasteryWithTheRealDamageAndActivationChanges() {
        val state = engine().apply {
            weaponLevel = 2
            offer(ChoiceOption(ChoiceType.TOTEM, "Amplify", "", "", weaponId = weapon, totemAction = TotemAction.AMPLIFY_CURRENT))
        }
        val preview = state.rewardPreviews().single()
        val selected = state.copyForReduction().apply { choose(0) }
        for (name in listOf("Weapon power", "Activation speed")) {
            val change = preview.changes.single { it.name == name }
            assertEquals(selected.buildStats().single { it.name == name }.value, change.after)
            assertTrue(change.after > change.before)
        }
        assertEquals(2, state.weaponLevel)
    }

    @Test
    fun weaponCountPreviewsMatchSpawnedDronesAndPrismProjectilesAtUpgradeThresholds() {
        val drones = engine().apply {
            weapon = WeaponId.ION_SWARM
            weaponLevel = 3
            offer(ChoiceOption(ChoiceType.TOTEM, "Amplify", "", "", weaponId = weapon, totemAction = TotemAction.AMPLIFY_CURRENT))
        }
        val droneCount = drones.rewardPreviews().single().changes.single { it.name == "Drones" }
        assertEquals(2f, droneCount.before)
        drones.choose(0)
        drones.updateWeapons(0f)
        assertEquals(drones.weaponOrbitals.size.toFloat(), droneCount.after)

        val prism = engine().apply {
            weapon = WeaponId.PRISM_RELAY
            weaponLevel = 5
            offer(ChoiceOption(ChoiceType.TOTEM, "Amplify", "", "", weaponId = weapon, totemAction = TotemAction.AMPLIFY_CURRENT))
        }
        val preview = prism.rewardPreviews().single()
        prism.choose(0)
        prism.addEnemyForTesting(prism.coreX + 100f, prism.coreY)
        prism.firePrismRelay(1f)
        assertEquals(prism.projectiles.size.toFloat(), preview.changes.single { it.name == "Projectiles" }.after)
        assertEquals(prism.projectiles.first().pierce.toFloat(), preview.changes.single { it.name == "Ricochets" }.after)
    }

    @Test
    fun fullMatrixWaitsForSlotAndReplacementShowsLostSynergyAndEffects() {
        val state = engine().apply {
            listOf(RelicId.KINETIC_FLYWHEEL, RelicId.GHOST_VECTOR, RelicId.ORBITAL_NAIL, RelicId.VOLTAIC_FILAMENT).forEach(::acquireRelic)
            offer(ChoiceOption(ChoiceType.RELIC, "New relic", "", "", relicId = RelicId.MASS_ECHO, relicAction = RelicChoiceAction.ACQUIRE))
        }
        val pending = state.rewardPreviews().single()
        assertTrue(pending.requiresSlot)
        assertTrue(pending.changes.isEmpty())
        state.offer(ChoiceOption(ChoiceType.RELIC_BIND, "Replace", "", "", relicId = RelicId.MASS_ECHO,
            relicSlot = 1, relicAction = RelicChoiceAction.REPLACE))
        val replacement = state.rewardPreviews().single()
        assertFalse(replacement.requiresSlot)
        assertTrue(replacement.removedSynergies.contains("Vector maneuver"))
        assertTrue(replacement.addedSynergies.contains("Gravitic grouping"))
        assertEquals(0f, replacement.changes.single { it.name == "Damage · dash" }.after)
        assertTrue(replacement.changes.single { it.name == "Damage · current mass" }.after > 0f)
        assertEquals(RelicId.GHOST_VECTOR, state.equippedRelics[1].id)
    }

    @Test
    fun relicRankForecastMatchesDashDamageAndMassArtifactUpdatesItsDependentRelic() {
        val state = engine().apply {
            acquireRelic(RelicId.GHOST_VECTOR)
            offer(ChoiceOption(ChoiceType.RELIC, "Ghost", "", "", relicId = RelicId.GHOST_VECTOR, relicAction = RelicChoiceAction.ACQUIRE))
        }
        val ghost = state.rewardPreviews().single()
        assertEquals(24f, ghost.changes.single { it.name == "Damage · dash" }.before)
        assertEquals(48f, ghost.changes.single { it.name == "Damage · dash" }.after)
        assertEquals(100f, ghost.changes.single { it.name == "Radius · dash" }.after)
        state.acquireRelic(RelicId.MASS_ECHO)
        val item = state.content.items.first { it.primary.effect == ItemEffect.MASS }
        state.offer(ChoiceOption(ChoiceType.ITEM, item.name, "", "", itemId = item.id))
        val mass = state.rewardPreviews().single().changes.single { it.name == "Damage · current mass" }
        assertTrue(mass.after > mass.before)
    }

    @Test
    fun scalarPointerProjectionReusesPreviewAndRerollReplacesIt() {
        val state = engine().apply { openItemChoice() }
        val first = state.toRenderModel()
        val pointer = state.copyForScalarInputReduction().apply { pointerX += 20f }
        val second = pointer.toRenderModel(first, state)
        assertSame(first.rewardPreviews, second.rewardPreviews)
        val rerolled = pointer.copyForReduction().apply { buildItemChoices() }
        val third = rerolled.toRenderModel(second, pointer)
        assertEquals(rerolled.rewardPreviews(), third.rewardPreviews)
        assertNotSame(second.rewardPreviews, third.rewardPreviews)
    }

    private fun engine() = MutableGameState(canonicalGameplayContent).apply { startRun() }
    private fun MutableGameState.offer(choice: ChoiceOption) {
        choices = listOf(choice)
        activeChoiceType = choice.type
        phase = GamePhase.CHOICE
    }
}
