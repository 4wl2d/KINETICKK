// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.FieldSource
import kinetickk.ball.gameplay.nucleus.model.MarkKind
import kinetickk.ball.gameplay.nucleus.model.SeveredRelic
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.PointOfInterestState
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.Totem
import kinetickk.ball.gameplay.nucleus.model.TrailPoint
import kinetickk.ball.gameplay.nucleus.model.WeaponNode
import kinetickk.ball.gameplay.nucleus.model.WeaponOrbital
import kinetickk.ball.gameplay.nucleus.model.WorldPoint
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameplayRenderModelMapperTest {
    @Test
    fun reusedProjectionMatchesIndependentFreshMappingAndRetainedSnapshotsSurviveLaterInputs() {
        val frame = GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f)
        val source = fullyPopulatedState().apply {
            coreShape = kinetickk.ball.content.api.CoreShape.TESSERACT
            characterRuntime = CharacterRuntime().apply {
                charge = 0.4f
                haloRadius = 61f
                parryWindow = 0.2f
                latticeCount = 2
                latticeNodes[0] = -1f
                latticeNodes[1] = 2f
                latticeNodes[2] = 3f
                latticeNodes[3] = -4f
                latticeTime = 5f
            }
            pointsOfInterest = listOf(
                PointOfInterestState(
                    offerId = 1,
                    kind = PointOfInterestKind.COLLAPSING_ORBIT,
                    x = 300f,
                    y = 400f,
                    expiresAt = 90f,
                ),
            )
        }
        val choiceSource = populatedState().apply { openItemChoice() }
        val cases = listOf(
            source to listOf(
                GameplayInteractionPulse.PointerMoved.fromValidated(901f, 361f),
                GameplayInteractionPulse.ViewportChanged.fromValidated(1_024f, 768f, 2f),
                GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, true),
                GameplayInteractionPulse.DashRequested,
                GameplayInteractionPulse.SkillRequested.fromValidated(0),
                GameplayInteractionPulse.ApexRequested,
                frame,
                GameplayInteractionPulse.PauseToggled,
                frame,
                GameplayInteractionPulse.PauseToggled,
                GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, false),
                frame,
            ),
            choiceSource to listOf(
                GameplayInteractionPulse.ChoicesRerolled,
                GameplayInteractionPulse.ChoiceSelected.fromValidated(0),
                frame,
            ),
        )

        cases.forEachIndexed { caseIndex, (initial, inputs) ->
            var state = EngineState(initial)
            var projection = initial.toRenderModel()
            val retained = mutableListOf<Pair<GameplayRenderModel, List<Pair<String, Any?>>>>()
            val reducer = GameReducer()
            inputs.forEachIndexed { inputIndex, input ->
                retained += projection to projection.exactRenderFacts()
                val accepted = assertIs<GameReductionResult.Accepted>(reducer.reduce(state, input))
                val optimized = accepted.state.model.toRenderModel(
                    reusableCollections = projection,
                    identitySource = state.model,
                )
                // No source clone, retained projection, or identity hint participates in this read.
                val fresh = accepted.state.model.toRenderModel()
                assertEquals(
                    fresh.exactRenderFacts(),
                    optimized.exactRenderFacts(),
                    "case $caseIndex input $inputIndex: $input",
                )
                state = accepted.state
                projection = optimized
            }
            retained.forEachIndexed { index, (snapshot, expected) ->
                assertEquals(expected, snapshot.exactRenderFacts(), "case $caseIndex retained $index")
            }
        }
    }

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
        assertSame(retained.combatFields, projected.combatFields)
        assertSame(retained.coreState, projected.coreState)
        assertTrue(retained.combatFields.isNotEmpty())
        assertTrue(retained.enemies.single().controlBits != 0)
        assertTrue(candidate.itemStacks.sharesStorageWith(source.itemStacks))
        assertTrue(candidate.discoveredItemIds.sharesStorageWith(source.discoveredItemIds))
        assertTrue(candidate.relicRanks.sharesStorageWith(source.relicRanks))
    }

    @Test
    fun buildProjectionIsReusedForAnUnchangedBuildAndRebuiltWhenARelicGaugeMoves() {
        val source = buildTestState(
            RelicId.STORMHEART_CAPACITOR,
            RelicId.FURNACE_COVENANT,
            RelicId.OVERDRIVE_CASCADE,
        )
        val retained = source.toRenderModel()
        assertTrue(retained.isPairingDiscovered(RelicPairingId.MELTDOWN))
        assertTrue(retained.build.links.single { it.pairing == RelicPairingId.MELTDOWN }.discovered)
        assertEquals(source.build.affinity(StatusElement.THERMAL), retained.build.affinity(StatusElement.THERMAL))

        val scalar = source.copyForScalarInputReduction()
        assertSame(retained.build, scalar.toRenderModel(reusableCollections = retained, identitySource = source).build)

        val charged = source.copyForReduction().apply { stormCharge = 0.5f }
        val recharged = charged.toRenderModel(reusableCollections = retained, identitySource = source)
        assertNotSame(retained.build, recharged.build)
        assertEquals(0.5f, recharged.build.stormCharge)
        assertEquals(0f, retained.build.stormCharge)
        assertSame(retained.build.tagPoints, recharged.build.tagPoints)
        assertSame(retained.build.tagTiers, recharged.build.tagTiers)
        assertSame(retained.build.affinity, recharged.build.affinity)
        assertSame(retained.build.affinityTiers, recharged.build.affinityTiers)
        assertSame(retained.build.links, recharged.build.links)
        assertSame(retained.build.relicUpsides, recharged.build.relicUpsides)
        assertSame(retained.build.breakThresholds, recharged.build.breakThresholds)
    }

    @Test
    fun buildViewIsRebuiltWhenAnyOneOfItsLiveInputsChanges() {
        fun source(setup: MutableGameState.() -> Unit = {}) =
            buildTestState(RelicId.STASIS_ANCHOR, RelicId.OUROBOROS_SEAL).apply { elapsed = 10f }.apply(setup)
        val cases: List<Triple<String, MutableGameState.() -> Unit, MutableGameState.() -> Unit>> = listOf(
            Triple("storm charge", {}, { stormCharge = 0.25f }),
            Triple("stasis meter", {}, { stasisMeter = 1f }),
            Triple("stasis bank", {}, { stasisBank = 4 }),
            Triple("ouroboros spent", {}, { ouroborosSpent = true }),
            Triple("invulnerability ends with time alone", { invulnerableUntil = 10.1f }, { elapsed = 10.2f }),
            Triple("burnout ends with time alone", { overdriveBurnoutUntil = 10.1f }, { elapsed = 10.2f }),
            Triple("a severed relic", {}, { severedRelics = listOf(SeveredRelic(RelicId.GLASS_COMET, until = 50f)) }),
            Triple("a discovered pairing", {}, {
                discoveredPairingMask = discoveredPairingMask or (1 shl RelicPairingId.MELTDOWN.ordinal)
            }),
            Triple("a recompiled build", {}, { acquireItem(content.items.first().id) }),
            Triple("owned item storage", {}, { itemStacks[0] = itemStacks[0] + 1 }),
            Triple("reborn item storage", {}, { rebornItems[0] = 1 }),
        )
        cases.forEach { (name, setup, change) ->
            val committed = source(setup)
            val retained = committed.toRenderModel()
            assertSame(retained.build, committed.copyForReduction().toRenderModel(retained, identitySource = committed).build, name)
            val candidate = committed.copyForReduction().apply(change)
            val projected = candidate.toRenderModel(retained, identitySource = committed)
            assertNotSame(retained.build, projected.build, name)
            assertEquals(candidate.buildProjection(), projected.build, name)
            assertEquals(committed.buildProjection(), retained.build, "$name: the retained view stays immutable")
        }
    }

    @Test
    fun severedRelicCountdownUpdatesWithoutRebuildingStaticBuildCollections() {
        val source = buildTestState().apply {
            elapsed = 10f
            severedRelics = listOf(SeveredRelic(RelicId.GLASS_COMET, until = 50f))
            recompileBuild()
        }
        val retained = source.toRenderModel()
        val candidate = source.copyForReduction().apply { elapsed = 11f }
        val projected = candidate.toRenderModel(retained, identitySource = source)
        assertEquals(candidate.buildProjection(), projected.build)
        assertEquals(39f, projected.build.severed.single().remaining)
        assertEquals(40f, retained.build.severed.single().remaining)
        assertSame(retained.build.tagPoints, projected.build.tagPoints)
        assertSame(retained.build.relicUpsides, projected.build.relicUpsides)
        assertSame(retained.build.suppressedItemIds, projected.build.suppressedItemIds)
    }

    @Test
    fun enemyViewIsRebuiltWhenOnlyItsStatusChangesWithTime() {
        val source = buildTestState().apply { elapsed = 5f }
        val enemy = source.dummy(x = 300f)
        // A Frozen enemy keeps its position, velocity and integrity while it thaws.
        enemy.status[StatusCell.FROZEN_UNTIL] = 5.05f
        enemy.status[StatusCell.EMBRITTLED_UNTIL] = 5.05f
        val retained = source.toRenderModel()
        assertTrue(retained.enemies.single().statusBits and STATUS_FROZEN_BIT != 0)

        val unchanged = source.copyForReduction().toRenderModel(retained, identitySource = source)
        assertSame(retained.enemies, unchanged.enemies)

        val thawed = source.copyForReduction().apply { elapsed = 5.1f }
        val projected = thawed.toRenderModel(retained, identitySource = source)
        assertNotSame(retained.enemies, projected.enemies)
        assertEquals(0, projected.enemies.single().statusBits and (STATUS_FROZEN_BIT or STATUS_EMBRITTLED_BIT))
        assertTrue(retained.enemies.single().statusBits and STATUS_FROZEN_BIT != 0, "The retained view stays immutable")

        val stacked = source.copyForReduction().apply { enemies.single().status[StatusCell.STACKS + StatusElement.ION.ordinal] = 2f }
        val restacked = stacked.toRenderModel(retained, identitySource = source)
        assertNotSame(retained.enemies, restacked.enemies)
        assertEquals(2, (restacked.enemies.single().statusBits shr (StatusElement.ION.ordinal * 4)) and 0xF)
    }

    @Test
    fun enemyViewIsRebuiltWhenOnlyItsAffinityChanges() {
        val source = buildTestState().apply { elapsed = 5f }
        // A shifting Architect keeps its position, velocity, integrity and statuses while the shift runs out.
        source.addEnemyForTesting(x = 300f, y = 0f, type = EnemyType.ARCHITECT).apply {
            phase = 1
            shiftUntil = 5.05f
        }
        val retained = source.toRenderModel()
        assertTrue(EnemyAffinity.shifting(retained.enemies.single().affinityBits))
        assertEquals(1, EnemyAffinity.phase(retained.enemies.single().affinityBits))

        val unchanged = source.copyForReduction().toRenderModel(retained, identitySource = source)
        assertSame(retained.enemies, unchanged.enemies, "Equal affinity reuses the projection")

        val settled = source.copyForReduction().apply { elapsed = 5.1f }
        val projected = settled.toRenderModel(retained, identitySource = source)
        assertNotSame(retained.enemies, projected.enemies)
        assertEquals(retained.enemies.single().statusBits, projected.enemies.single().statusBits)
        assertFalse(EnemyAffinity.shifting(projected.enemies.single().affinityBits))
        assertEquals(1, EnemyAffinity.phase(projected.enemies.single().affinityBits))
        assertTrue(EnemyAffinity.shifting(retained.enemies.single().affinityBits), "The retained view stays immutable")

        val hardened = source.copyForReduction().apply {
            assignAspect(enemies.single(), StatusElement.CRYO.ordinal, immune = false)
        }
        val reprojected = hardened.toRenderModel(retained, identitySource = source)
        assertNotSame(retained.enemies, reprojected.enemies)
        assertEquals(StatusElement.CRYO.ordinal, EnemyAffinity.aspect(reprojected.enemies.single().affinityBits))
        assertEquals(StatusElement.THERMAL.ordinal, EnemyAffinity.weakness(reprojected.enemies.single().affinityBits))
        assertEquals(EnemyAffinity.NONE, EnemyAffinity.aspect(retained.enemies.single().affinityBits))
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
            equippedRelics = listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, rank = 2))
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
        assertEquals(1, retained.relicRank(RelicId.PERPETUAL_VECTOR))
        assertEquals(2, projected.equippedRelics.single().rank)
        assertEquals("Changed", projected.choices.single().title)
        assertEquals(3, projected.itemStack(0))
        assertEquals(2, projected.discoveredItemCount)
        assertEquals(2, projected.relicRank(RelicId.PERPETUAL_VECTOR))
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
        equippedRelics = listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, rank = 1))
        choices = listOf(choice(title = "Initial"))
        itemStacks[0] = 2
        discoveredItemIds += 101
        relicRanks[0] = 1
    }

    private fun fullyPopulatedState(): MutableGameState = populatedState().apply {
        totem = Totem(x = 10f, y = 20f, pulse = 0.5f)
        addEnemyForTesting(x = 10f, y = 20f).apply {
            silencedUntil = 0.5f
            setMark(this, MarkKind.SPALL, 0.5f)
        }
        addField(CombatFieldKind.ROT_WELL, FieldSource.REACTION, 9, x = 30f, y = 40f, radius = 50f, seconds = 2f,
            damagePerSecond = 0f)
        ablazeUntil = 0.4f
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

    /** Detached observable values; raw Float bits preserve NaN payloads and signed zero. */
    private fun GameplayRenderModel.exactRenderFacts(): List<Pair<String, Any?>> = listOf(
        "content" to content,
        "phase" to phase,
        "runStatistics" to runStatistics,
        "settings" to listOf(
            settings.soundEnabled, settings.musicEnabled, settings.masterVolume.toRawBits(),
            settings.simulationSpeed.toRawBits(), settings.textScale.toRawBits(), settings.screenShake,
            settings.particleDensity, settings.damageNumbers, settings.damageNumberSize,
            settings.damageNumberFormat, settings.damageNumberTierThreshold, settings.language, settings.runStatisticsOnLeft,
            settings.minimapEnabled,
        ),
        "scalars" to listOf(
            rebirthLevel, screenWidth.toRawBits(), screenHeight.toRawBits(), uiScale.toRawBits(),
            coreX.toRawBits(), coreY.toRawBits(), velocityX.toRawBits(), velocityY.toRawBits(),
            cameraX.toRawBits(), cameraY.toRawBits(), pointerX.toRawBits(), pointerY.toRawBits(),
            pointerActive, braking, elapsed.toRawBits(), heat.toRawBits(), overheated,
            dashPhaseTime.toRawBits(), hp.toRawBits(), maxHp.toRawBits(), shield.toRawBits(),
            maxShield.toRawBits(), level, data, nextLevelData, keys, kills, combo,
            comboTime.toRawBits(), runMatter, totalMatter, lastImpact.toRawBits(),
            lastImpactTime.toRawBits(), damageFlash.toRawBits(), runGrace.toRawBits(),
            screenShake.toRawBits(), message, messageTime.toRawBits(), mass.toRawBits(),
            damageMultiplier.toRawBits(), weaponPower.toRawBits(), effectiveWeaponPower.toRawBits(),
            coolingRate.toRawBits(), magnetStrength.toRawBits(), dashImpulse.toRawBits(),
            dashHeatCost.toRawBits(), regenPerSecond.toRawBits(), critChance.toRawBits(),
            critMultiplier.toRawBits(), pickupRadius.toRawBits(), luck.toRawBits(),
            dataGain.toRawBits(), matterGain.toRawBits(), attackSpeed.toRawBits(),
            damageReduction.toRawBits(), comboWindow.toRawBits(), overdriveGain.toRawBits(),
            dragCoefficient.toRawBits(), polarityStability.toRawBits(), weapon, weaponLevel,
            overdriveCharge.toRawBits(), overdriveTime.toRawBits(), rerollsRemaining,
            acquiredItemCount, recentItem, morningstarAngle.toRawBits(), morningstarX.toRawBits(),
            morningstarY.toRawBits(), weaponBeamTime.toRawBits(), weaponBeamStartX.toRawBits(),
            weaponBeamStartY.toRawBits(), weaponBeamEndX.toRawBits(), weaponBeamEndY.toRawBits(),
            coreShape, choiceType, pendingRelicChoiceCount, directedChoice,
        ),
        "equippedRelics" to equippedRelics.toList(),
        "totem" to totem?.let { listOf(it.x.toRawBits(), it.y.toRawBits(), it.pulse.toRawBits()) },
        "enemies" to enemies.map {
            listOf(
                it.id, it.type, it.x.toRawBits(), it.y.toRawBits(), it.vx.toRawBits(), it.vy.toRawBits(),
                it.hp.toRawBits(), it.maxHp.toRawBits(), it.radius.toRawBits(), it.actionTimer.toRawBits(),
                it.flash.toRawBits(), it.contactCooldown.toRawBits(), it.weaponCooldown.toRawBits(),
                it.previousX.toRawBits(), it.previousY.toRawBits(), it.dead, it.statusBits, it.affinityBits,
                it.armored, it.controlBits,
            )
        },
        "projectiles" to projectiles.map {
            listOf(
                it.x.toRawBits(), it.y.toRawBits(), it.vx.toRawBits(), it.vy.toRawBits(),
                it.radius.toRawBits(), it.life.toRawBits(), it.hostile, it.damage.toRawBits(),
                it.pierce, it.colorIndex, it.sourceWeapon, it.previousX.toRawBits(), it.previousY.toRawBits(),
            )
        },
        "pickups" to pickups.map {
            listOf(
                it.type, it.x.toRawBits(), it.y.toRawBits(), it.vx.toRawBits(), it.vy.toRawBits(),
                it.life.toRawBits(), it.previousX.toRawBits(), it.previousY.toRawBits(),
            )
        },
        "trail" to trail.map { listOf(it.x.toRawBits(), it.y.toRawBits(), it.age.toRawBits()) },
        "weaponNodes" to weaponNodes.map {
            listOf(
                it.type, it.x.toRawBits(), it.y.toRawBits(), it.life.toRawBits(),
                it.maxLife.toRawBits(), it.radius.toRawBits(),
            )
        },
        "weaponOrbitals" to weaponOrbitals.map {
            listOf(it.index, it.x.toRawBits(), it.y.toRawBits(), it.radius.toRawBits())
        },
        "choices" to choices.toList(),
        "rewardPreviews" to rewardPreviews.toList(),
        "characterAbility" to listOf(
            characterAbility.toString(),
        ),
        "pointsOfInterest" to pointsOfInterest.map {
            listOf(
                it.kind, it.name, it.x.toRawBits(), it.y.toRawBits(), it.active,
                it.remaining.toRawBits(), it.nextBeacon, it.progress.toRawBits(), it.defenderIds.toList(),
                it.warningRemaining.toRawBits(), it.volleyAngle.toRawBits(),
            )
        },
        "itemStacks" to itemStacks.toList(),
        "discoveredItemIds" to discoveredItemIds.toList(),
        "relicRanks" to relicRanks.toList(),
        "build" to build,
        "discoveredReactionMask" to discoveredReactionMask,
        "discoveredPairingMask" to discoveredPairingMask,
        "coreRadius" to coreRadius.toRawBits(),
        "abilityPower" to abilityPower.toRawBits(),
        "obstacles" to obstacles.toList(),
        "minimapRange" to minimapRange.toRawBits(),
        "rebirthRank" to rebirthRank,
        "volatileBursts" to volatileBursts.toList(),
        "maxLevel" to maxLevel,
        "skillBar" to skillBar,
        "apex" to apex,
        "combatFields" to combatFields.toList(),
        "coreState" to coreState,
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
