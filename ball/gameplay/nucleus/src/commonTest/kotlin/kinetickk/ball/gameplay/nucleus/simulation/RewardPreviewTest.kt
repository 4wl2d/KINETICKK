// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.*

class RewardPreviewTest {
    @Test
    fun everyItemForecastMatchesSelectionIncludingCapsAndStackMagnitude() {
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
                } else assertNull(forecast, "${item.name}: ${old.name}")
            }
            assertEquals(before, state.buildStats(), "Read must preserve accepted stats")
            assertEquals(minOf(2, item.maxStacks), state.itemStack(item.id))
        }
    }

    @Test
    fun forecastsNameSuppressionFirstThenReactionsTiersAndAffinity() {
        val state = engine()
        val igniter = state.content.items.single { it.name == "Igniter" }
        val rime = state.content.items.single { it.name == "Rime Rounds" }
        val nanite = state.content.items.single { it.name == "Nanite Mesh" }
        state.acquireItem(igniter.id)
        state.offer(ChoiceOption(ChoiceType.ITEM, rime.name, rime.description, "", itemId = rime.id))
        val reaction = state.rewardPreviews().single().forecasts
        assertEquals(ForecastKind.REACTION, reaction.first().kind)
        assertEquals(ReactionId.THERMAL_SHOCK, reaction.first().reaction)
        assertFalse(reaction.first().discovered, "An unseen reaction stays a question for the card")
        assertTrue(reaction.any { it.kind == ForecastKind.AFFINITY && it.element == StatusElement.CRYO && it.before == 0 && it.after == 1 })

        state.discoveredReactionMask = 1 shl ReactionId.THERMAL_SHOCK.ordinal
        assertTrue(state.rewardPreviews().single().forecasts.first().discovered)

        val ram = state.content.items.filter { BuildTag.RAM in it.tags }.take(3)
        state.acquireItem(ram[0].id)
        state.acquireItem(ram[1].id)
        state.offer(ChoiceOption(ChoiceType.ITEM, ram[2].name, "", "", itemId = ram[2].id))
        val tier = state.rewardPreviews().single().forecasts.single { it.kind == ForecastKind.TAG_TIER && it.tag == BuildTag.RAM }
        assertEquals(0, tier.before)
        assertEquals(1, tier.after)

        state.acquireRelic(RelicId.VERDANT_ROT)
        state.offer(ChoiceOption(ChoiceType.ITEM, nanite.name, "", "", itemId = nanite.id))
        assertEquals(ForecastKind.SUPPRESSED, state.rewardPreviews().single().forecasts.first().kind)
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
    fun cardsThatChangeNothingPreviewNothingAndEveryCardReadsAsOnACopy() {
        val rime = canonicalGameplayContent.items.single { it.name == "Rime Rounds" }
        val state = engine().apply {
            acquireItem(canonicalGameplayContent.items.single { it.name == "Igniter" }.id)
            overdriveTime = 2f
            choices = listOf(
                ChoiceOption(ChoiceType.OMEN, NO_OMEN_TITLE, NO_OMEN_TEXT, "DECLINE"),
                ChoiceOption(ChoiceType.TOTEM, "Change", "", "", totemAction = TotemAction.CHANGE_WEAPON),
                ChoiceOption(ChoiceType.RELIC_BIND, "Keep", "", "", relicSlot = 0, relicAction = RelicChoiceAction.DECLINE),
                ChoiceOption(ChoiceType.ITEM, rime.name, rime.description, "", itemId = rime.id),
            )
            activeChoiceType = ChoiceType.ITEM
            phase = GamePhase.CHOICE
        }
        val previews = state.rewardPreviews()
        previews.take(3).forEachIndexed { index, preview -> assertEquals(RewardPreview(), preview, state.choices[index].title) }
        assertEquals(ForecastKind.REACTION, previews[3].forecasts.first().kind, "the item card still previews its build change")
        // Every preview is what an isolated copy of the state previews, including the cards that copy nothing.
        assertEquals(previews, state.copyForReduction().rewardPreviews())
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
            // Amplifying to level 3 opens Ion Swarm's first form: one more orbital.
            weaponLevel = 2
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
        // The second form twins the relay without another bounce, so the preview lists no ricochet change.
        assertTrue(preview.changes.none { it.name == "Ricochets" })

        // Amplifying to level 3 opens the first form: one more relay bounce.
        val amplified = engine().apply {
            weapon = WeaponId.PRISM_RELAY
            weaponLevel = 2
            offer(ChoiceOption(ChoiceType.TOTEM, "Amplify", "", "", weaponId = weapon, totemAction = TotemAction.AMPLIFY_CURRENT))
        }
        val ricochets = amplified.rewardPreviews().single().changes.single { it.name == "Ricochets" }
        amplified.choose(0)
        amplified.addEnemyForTesting(amplified.coreX + 100f, amplified.coreY)
        amplified.firePrismRelay(1f)
        assertEquals(amplified.projectiles.first().pierce.toFloat(), ricochets.after)
        assertTrue(ricochets.after > ricochets.before)

        // The third form makes the bounces burst without another ricochet.
        val ascended = engine().apply {
            weapon = WeaponId.PRISM_RELAY
            weaponLevel = 9
            offer(ChoiceOption(ChoiceType.TOTEM, "Amplify", "", "", weaponId = weapon, totemAction = TotemAction.AMPLIFY_CURRENT))
        }
        assertTrue(ascended.rewardPreviews().single().changes.none { it.name == "Ricochets" })
        ascended.choose(0)
        ascended.addEnemyForTesting(ascended.coreX + 100f, ascended.coreY)
        ascended.firePrismRelay(1f)
        assertEquals(ricochets.after, ascended.projectiles.first().pierce.toFloat())
    }

    @Test
    fun fullMatrixWaitsForSlotAndReplacementShowsLostSynergyAndEffects() {
        val state = engine().apply {
            listOf(RelicId.BLINK_DRIVE, RelicId.GYRE_ENGINE, RelicId.GLASS_COMET, RelicId.ECHO_CHAMBER).forEach(::acquireRelic)
            offer(ChoiceOption(ChoiceType.RELIC, "New relic", "", "", relicId = RelicId.STORMHEART_CAPACITOR, relicAction = RelicChoiceAction.ACQUIRE))
        }
        val pending = state.rewardPreviews().single()
        assertTrue(pending.requiresSlot)
        assertTrue(pending.changes.isEmpty())
        // The links it would form are shown per bound relic before a slot is chosen.
        assertEquals(
            listOf(ForecastKind.HARMONY to RelicId.BLINK_DRIVE, ForecastKind.HARMONY to RelicId.GYRE_ENGINE),
            pending.forecasts.map { it.kind to it.relic },
        )
        state.offer(ChoiceOption(ChoiceType.RELIC_BIND, "Replace", "", "", relicId = RelicId.STORMHEART_CAPACITOR,
            relicSlot = 0, relicAction = RelicChoiceAction.REPLACE))
        val replacement = state.rewardPreviews().single()
        assertFalse(replacement.requiresSlot)
        // Losing Blink Drive loses its heat discount; the new pair harmonizes.
        val dashHeat = replacement.changes.single { it.name == "Dash heat" }
        assertTrue(dashHeat.lowerIsBetter)
        assertTrue(dashHeat.after > dashHeat.before)
        assertTrue(replacement.forecasts.any { it.kind == ForecastKind.HARMONY && it.relic == RelicId.GYRE_ENGINE })
        assertEquals(RelicId.BLINK_DRIVE, state.equippedRelics[0].id)
    }

    @Test
    fun relicRankForecastMatchesTheRankedRelicStats() {
        val state = engine().apply {
            acquireRelic(RelicId.NEUTRON_HEART)
            offer(ChoiceOption(ChoiceType.RELIC, "Neutron", "", "", relicId = RelicId.NEUTRON_HEART, relicAction = RelicChoiceAction.ACQUIRE))
        }
        val preview = state.rewardPreviews().single()
        val mass = preview.changes.single { it.name == "Mass" }
        assertEquals(1.8f, mass.before, 0.0001f)
        assertEquals(2.1f, mass.after, 0.0001f)
        val selected = state.copyForReduction().apply { choose(0) }
        assertEquals(2, selected.relicRank(RelicId.NEUTRON_HEART))
        assertEquals(selected.buildStats().single { it.name == "Mass" }.value, mass.after)
        assertEquals(1, state.relicRank(RelicId.NEUTRON_HEART))
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

    @Test
    fun aSteeringStatItemPreviewsItsGripAndSlingRows() {
        // No shipped item carries the steering stats yet: a plain stat item is given them.
        val plain = canonicalGameplayContent.items.first {
            !it.isActive && it.stats.size == 1 && it.clauses.isEmpty() && it.rules.isEmpty() && it.maxStacks > 1
        }
        val steering = plain.copy(stats = immutableListOf(StatDelta(ItemEffect.GRIP, 0.3f), StatDelta(ItemEffect.SLING, -0.2f)))
        val content = canonicalGameplayContent.copy(
            items = canonicalGameplayContent.items.map { if (it.id == plain.id) steering else it }.toImmutableList(),
        )
        val state = MutableGameState(content).apply {
            startRun()
            offer(ChoiceOption(ChoiceType.ITEM, steering.name, steering.description, "", itemId = steering.id))
        }
        val changes = state.rewardPreviews().single().changes
        val selected = state.copyForReduction().apply { choose(0) }
        for ((name, after) in listOf("Grip" to 1.3f, "Sling" to 0.8f)) {
            val change = changes.single { it.name == name }
            assertEquals(1f, change.before, name)
            assertEquals(after, change.after, 1e-6f, name)
            assertEquals("×", change.unit, name)
            assertEquals(selected.buildStats().single { it.name == name }.value, change.after, name)
        }
    }

    private fun engine() = MutableGameState(canonicalGameplayContent).apply { startRun() }
    private fun MutableGameState.offer(choice: ChoiceOption) {
        choices = listOf(choice)
        activeChoiceType = choice.type
        phase = GamePhase.CHOICE
    }
}
