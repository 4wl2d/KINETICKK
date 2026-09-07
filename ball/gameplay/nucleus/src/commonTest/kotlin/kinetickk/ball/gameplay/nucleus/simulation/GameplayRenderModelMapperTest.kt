// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime
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
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameplayRenderModelMapperTest {
    @Test
    fun reusedProjectionMatchesIndependentFreshMappingAndRetainedSnapshotsSurviveLaterInputs() {
        val frame = GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f)
        val source = fullyPopulatedState().apply {
            characterRuntime = CharacterRuntime(
                charge = 0.4f,
                barrier = 2f,
                ringRadius = 61f,
                parryWindow = 0.2f,
                lattice = listOf(WorldPoint(-1f, 2f), WorldPoint(3f, -4f)),
            )
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
                it.previousX.toRawBits(), it.previousY.toRawBits(), it.dead,
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
            characterAbility.charge.toRawBits(), characterAbility.barrier.toRawBits(),
            characterAbility.ringRadius.toRawBits(), characterAbility.parryWindow.toRawBits(),
            characterAbility.lattice.map { listOf(it.x.toRawBits(), it.y.toRawBits()) },
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
