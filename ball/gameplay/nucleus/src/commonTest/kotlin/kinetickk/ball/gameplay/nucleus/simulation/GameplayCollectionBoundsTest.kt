// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayPointerAxis
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.nucleus.model.EchoHit
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.WeaponEntity
import kinetickk.ball.gameplay.nucleus.model.WeaponNode
import kinetickk.ball.gameplay.nucleus.model.WeaponRuntime
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.SimulationOutput
import kinetickk.ball.gameplay.nucleus.protocol.SimulationOutputs
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameplayCollectionBoundsTest {
    @Test
    fun fixedStepStableCompactionRetainsOrderAcrossConsecutiveRemovals() {
        val state = newState(seed = 900)
        val retainedEnemy = state.addEnemyForTesting(x = 0f, y = 0f)
        // Enemies that left without a kill (a detonated Bomber) are compacted out in order.
        val firstRemovedEnemy = state.addEnemyForTesting(x = 100f, y = 0f).apply { dead = true }
        val secondRemovedEnemy = state.addEnemyForTesting(x = 110f, y = 0f).apply { dead = true }
        val lastRetainedEnemy = state.addEnemyForTesting(x = 20f, y = 0f)
        state.updateEnemies(0f)
        assertEquals(
            listOf(retainedEnemy.id, lastRetainedEnemy.id),
            state.enemies.map { enemy -> enemy.id },
        )
        assertFalse(state.enemies.any { enemy ->
            enemy.id == firstRemovedEnemy.id || enemy.id == secondRemovedEnemy.id
        })

        val firstRetainedProjectile = Projectile(0f, 0f, 0f, 0f, 1f, 1f)
        val firstRemovedProjectile = Projectile(0f, 0f, 0f, 0f, 1f, 0f)
        val secondRemovedProjectile = Projectile(0f, 0f, 0f, 0f, 1f, -1f)
        val lastRetainedProjectile = Projectile(0f, 0f, 0f, 0f, 1f, 2f)
        state.projectiles += listOf(
            firstRetainedProjectile,
            firstRemovedProjectile,
            secondRemovedProjectile,
            lastRetainedProjectile,
        )
        state.updateProjectiles(0f)
        assertEquals(
            listOf(firstRetainedProjectile, lastRetainedProjectile),
            state.projectiles,
        )

        val firstRetainedPickup = Pickup(PickupType.DATA, 0f, 0f, life = 1f)
        val firstRemovedPickup = Pickup(PickupType.DATA, 0f, 0f, life = 0f)
        val secondRemovedPickup = Pickup(PickupType.DATA, 0f, 0f, life = -1f)
        val lastRetainedPickup = Pickup(PickupType.DATA, 0f, 0f, life = 2f)
        state.pickups += listOf(
            firstRetainedPickup,
            firstRemovedPickup,
            secondRemovedPickup,
            lastRetainedPickup,
        )
        state.updatePickups(0f)
        assertEquals(listOf(firstRetainedPickup, lastRetainedPickup), state.pickups)

        val firstRetainedHit = EchoHit(enemyId = -1, delay = 1f, damage = 1f, appliesDominant = false)
        val firstRemovedHit = EchoHit(enemyId = -1, delay = 0f, damage = 1f, appliesDominant = false)
        val secondRemovedHit = EchoHit(enemyId = -1, delay = -1f, damage = 1f, appliesDominant = false)
        val lastRetainedHit = EchoHit(enemyId = -1, delay = 2f, damage = 1f, appliesDominant = false)
        state.echoHits += listOf(
            firstRetainedHit,
            firstRemovedHit,
            secondRemovedHit,
            lastRetainedHit,
        )
        state.updateRelicRuntime(0f)
        assertEquals(listOf(firstRetainedHit, lastRetainedHit), state.echoHits)
    }

    @Test
    fun collisionCompactionRetainsOrderAcrossConsecutiveProjectileAndPickupHits() {
        val state = newState(seed = 899).apply {
            dashPhaseTime = 1f
        }
        val retainedFriendly = Projectile(500f, 500f, 0f, 0f, 1f, 1f, hostile = false)
        val firstHostileHit = Projectile(0f, 0f, 0f, 0f, 1f, 1f, hostile = true)
        val secondHostileHit = Projectile(0f, 0f, 0f, 0f, 1f, 1f, hostile = true)
        val retainedHostile = Projectile(500f, 500f, 0f, 0f, 1f, 1f, hostile = true)
        state.projectiles += listOf(
            retainedFriendly,
            firstHostileHit,
            secondHostileHit,
            retainedHostile,
        )

        state.resolveProjectileHits()

        assertEquals(listOf(retainedFriendly, retainedHostile), state.projectiles)

        val retainedPickup = Pickup(PickupType.REPAIR, 500f, 500f)
        val firstCollected = Pickup(PickupType.REPAIR, 0f, 0f)
        val secondCollected = Pickup(PickupType.REPAIR, 0f, 0f)
        val lastRetainedPickup = Pickup(PickupType.REPAIR, -500f, -500f)
        state.pickups += listOf(
            retainedPickup,
            firstCollected,
            secondCollected,
            lastRetainedPickup,
        )

        state.resolvePickupCollection()

        assertEquals(listOf(retainedPickup, lastRetainedPickup), state.pickups)
    }

    @Test
    fun fixedSimulationOutputBatchPreservesCanonicalOrderAndOwnedPayloads() {
        val visualFxCues = immutableListOf<VisualFxCue>(VisualFxCue.ClearAll)
        val progressUpdate = GameplayProgressUpdate(bankedMatter = 7L)
        val audioCues = immutableListOf(GameplayAudioCue.DASH)

        val outputs = SimulationOutputs.create(
            visualFxCues = visualFxCues,
            progressUpdate = progressUpdate,
            advanceAudio = true,
            audioRealDeltaSeconds = 0.25f,
            audioCues = audioCues,
        )

        assertSame(visualFxCues, outputs.visualFxCuesOrNull)
        assertSame(progressUpdate, outputs.progressUpdate)
        assertSame(audioCues, outputs.audioCuesOrNull)
        assertEquals(3, outputs.size)
        assertSame(visualFxCues, assertIs<SimulationOutput.EmitVisualFx>(outputs[0]).cues)
        assertSame(progressUpdate, assertIs<SimulationOutput.PublishProgress>(outputs[1]).update)
        assertIs<SimulationOutput.AdvanceAudio>(outputs[2]).let { audio ->
            assertEquals(0.25f, audio.realDeltaSeconds)
            assertSame(audioCues, audio.cues)
        }
        assertSame(SimulationOutputs.Empty, SimulationOutputs.create())
        assertFailsWith<IllegalArgumentException> {
            SimulationOutputs.create(audioCues = audioCues)
        }
        assertFailsWith<IllegalArgumentException> {
            SimulationOutputs.create(audioRealDeltaSeconds = 0.25f)
        }
        assertEquals(
            listOf(SimulationOutput.EnsureAudioUnlocked),
            SimulationOutputs.EnsureAudioUnlocked.toList(),
        )
    }

    @Test
    fun soundCuesAcceptThirtyTwoAndRejectThirtyThird() {
        val state = newState(seed = 901)
        repeat(MutableGameState.MAX_GAMEPLAY_SOUND_CUES) {
            state.emitSound(GameplayAudioCue.DASH)
        }
        state.emitSound(GameplayAudioCue.VICTORY)

        val cues = state.takeSoundCues()

        assertEquals(MutableGameState.MAX_GAMEPLAY_SOUND_CUES, cues.size)
        assertTrue(cues.all { cue -> cue == GameplayAudioCue.DASH })
    }

    @Test
    fun weaponNodesAcceptEightAndRejectNinth() {
        val state = newState(seed = 902)
        assertTrue(state.weaponLevel < 6)
        repeat(BASE_MINE_LIMIT) { index ->
            assertTrue(state.tryAddWeaponNode(weaponNode(index)))
        }

        assertFalse(state.tryAddWeaponNode(weaponNode(BASE_MINE_LIMIT)))

        assertEquals(BASE_MINE_LIMIT, state.weaponNodes.size)
        assertEquals(0f, state.weaponNodes.first().x)
        assertEquals((BASE_MINE_LIMIT - 1).toFloat(), state.weaponNodes.last().x)

        // Gravity Mines' second form (level 6) holds twelve, never a thirteenth.
        state.weaponLevel = 6
        repeat(MutableGameState.MAX_WEAPON_NODES - BASE_MINE_LIMIT) { index ->
            assertTrue(state.tryAddWeaponNode(weaponNode(BASE_MINE_LIMIT + index)))
        }
        assertFalse(state.tryAddWeaponNode(weaponNode(MutableGameState.MAX_WEAPON_NODES)))
        assertEquals(MutableGameState.MAX_WEAPON_NODES, state.weaponNodes.size)
    }

    @Test
    fun weaponOrbitalsAcceptEightAndRejectNinthRequested() {
        val state = newState(seed = 903)
        state.ensureOrbitals(
            count = MutableGameState.MAX_WEAPON_ORBITALS,
            orbitRadius = 100f,
            hitRadius = 8f,
            delta = 0f,
            angularSpeed = 1f,
        )

        assertEquals(MutableGameState.MAX_WEAPON_ORBITALS, state.weaponOrbitals.size)
        assertEquals((0 until MutableGameState.MAX_WEAPON_ORBITALS).toList(), state.weaponOrbitals.map { it.index })

        assertFailsWith<IllegalArgumentException> {
            state.ensureOrbitals(
                count = MutableGameState.MAX_WEAPON_ORBITALS + 1,
                orbitRadius = 100f,
                hitRadius = 8f,
                delta = 0f,
                angularSpeed = 1f,
            )
        }
        assertEquals(MutableGameState.MAX_WEAPON_ORBITALS, state.weaponOrbitals.size)
    }

    @Test
    fun weaponEntitiesAcceptThirtyTwoAndMakeRoomForTheThirtyThirdWithoutATetherOrBeam() {
        val state = newState(seed = 910)
        val entities = state.weaponRuntime.entities
        state.addWeaponEntity(weaponEntity(WeaponEntityKind.TETHER, 0))
        state.addWeaponEntity(weaponEntity(WeaponEntityKind.BEAM, 1))
        repeat(WeaponRuntime.MAX_WEAPON_ENTITIES - 2) { index -> state.addWeaponEntity(weaponEntity(WeaponEntityKind.RING, index + 2)) }
        assertEquals(WeaponRuntime.MAX_WEAPON_ENTITIES, entities.size)

        // The thirty-third takes the place of the oldest entity other than the tether and the beam.
        state.addWeaponEntity(weaponEntity(WeaponEntityKind.RING, 100))
        assertEquals(WeaponRuntime.MAX_WEAPON_ENTITIES, entities.size)
        assertEquals(listOf(0f, 1f, 3f), entities.take(3).map { it.x })
        assertEquals(100f, entities.last().x)

        // A finished entity gives way before any live one.
        entities.single { it.x == 20f }.life = 0f
        state.addWeaponEntity(weaponEntity(WeaponEntityKind.RING, 101))
        assertEquals(WeaponRuntime.MAX_WEAPON_ENTITIES, entities.size)
        assertTrue(entities.none { it.x == 20f })
        assertTrue(entities.any { it.x == 3f })

        // A list of tethers and beams only drops the newcomer.
        entities.clear()
        repeat(WeaponRuntime.MAX_WEAPON_ENTITIES) { index ->
            state.addWeaponEntity(weaponEntity(if (index % 2 == 0) WeaponEntityKind.TETHER else WeaponEntityKind.BEAM, index))
        }
        state.addWeaponEntity(weaponEntity(WeaponEntityKind.RING, 200))
        assertEquals(WeaponRuntime.MAX_WEAPON_ENTITIES, entities.size)
        assertTrue(entities.none { it.kind == WeaponEntityKind.RING })
    }

    @Test
    fun anEntityCreatedInsideAnEntityPassShiftsNoIndexAndKeepsTheBound() {
        val state = buildTestState(seed = 911, weapon = WeaponId.HUNTER_SALVO)
        // The third form splits a missile that kills into two.
        state.weaponLevel = 10
        val entities = state.weaponRuntime.entities
        entities.clear()
        val doomed = state.addEnemyForTesting(x = state.coreX + 300f, y = state.coreY, hp = 1f)
        val sturdy = state.addEnemyForTesting(x = state.coreX - 300f, y = state.coreY, hp = 1_000_000f)
        repeat(2) { index -> entities += weaponEntity(WeaponEntityKind.RING, index) }
        entities += missileAt(doomed.x, doomed.y)
        entities += missileAt(sturdy.x, sturdy.y)
        repeat(WeaponRuntime.MAX_WEAPON_ENTITIES - entities.size) { index -> entities += weaponEntity(WeaponEntityKind.RING, index + 10) }
        assertEquals(WeaponRuntime.MAX_WEAPON_ENTITIES, entities.size)

        state.resolveWeaponEntityHits(MutableGameState.FIXED_STEP)

        assertFalse(doomed.alive)
        // The split made room by finishing the two oldest rings, so the second missile kept its turn in the pass.
        assertTrue(sturdy.hp < sturdy.maxHp, "the missile after the split still struck")
        assertTrue(entities.none { it.kind == WeaponEntityKind.RING && it.x < 2f })
        assertEquals(2, entities.count { it.kind == WeaponEntityKind.MISSILE })
        assertTrue(entities.all { it.life > 0f })
        assertTrue(entities.size <= WeaponRuntime.MAX_WEAPON_ENTITIES)
    }

    @Test
    fun choiceInventoryAcceptsFourAndRejectsFifthAtomically() {
        val state = newState(seed = 904)
        val accepted = List(MutableGameState.MAX_CHOICES) { index -> choice(index) }
        state.choices = accepted

        assertEquals(MutableGameState.MAX_CHOICES, state.choices.size)

        assertFailsWith<IllegalArgumentException> {
            state.choices = accepted + choice(MutableGameState.MAX_CHOICES)
        }
        assertSame(accepted, state.choices)
    }

    @Test
    fun choiceIndexThreeIsAcceptedAndFourIsRejected() {
        val state = newState(seed = 905).apply {
            phase = GamePhase.CHOICE
            choices = List(MutableGameState.MAX_CHOICES) { index -> choice(index) }
        }
        val reducer = GameReducer()

        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                EngineState(state),
                GameplayInteractionPulse.ChoiceSelected.fromValidated(3),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            GameplayInteractionPulse.ChoiceSelected.fromValidated(4)
        }
    }

    @Test
    fun validatedFactoriesOwnFixedBoundsAndReducerOwnsPointerMembership() {
        val state = newState(seed = 908)
        val reducer = GameReducer()

        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                EngineState(state),
                GameplayInteractionPulse.FrameElapsed.fromValidated(0f),
            ),
        )
        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                EngineState(state),
                GameplayInteractionPulse.FrameElapsed.fromValidated(1f),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            GameplayInteractionPulse.FrameElapsed.fromValidated(-Float.MIN_VALUE)
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayInteractionPulse.FrameElapsed.fromValidated(Float.fromBits(1f.toBits() + 1))
        }

        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                EngineState(state),
                GameplayInteractionPulse.ViewportChanged.fromValidated(1f, 1f, 0.5f),
            ),
        )
        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                EngineState(state),
                GameplayInteractionPulse.ViewportChanged.fromValidated(32_768f, 32_768f, 8f),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            GameplayInteractionPulse.ViewportChanged.fromValidated(
                Float.fromBits(1f.toBits() - 1),
                1f,
                0.5f,
            )
        }

        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                EngineState(state),
                GameplayInteractionPulse.PointerMoved.fromValidated(0f, 0f),
            ),
        )
        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                EngineState(state),
                GameplayInteractionPulse.PointerMoved.fromValidated(
                    state.screenWidth,
                    state.screenHeight,
                ),
            ),
        )
        val rejected = assertIs<GameReductionResult.Rejected>(
            reducer.reduce(
                EngineState(state),
                GameplayInteractionPulse.PointerMoved.fromValidated(-Float.MIN_VALUE, 0f),
            ),
        )
        assertEquals(
            GameplayRejection.PointerOutsideViewport(GameplayPointerAxis.HORIZONTAL),
            rejected.reason,
        )
        assertFailsWith<IllegalArgumentException> {
            GameplayInteractionPulse.PointerMoved.fromValidated(Float.NaN, 0f)
        }
    }

    @Test
    fun repeatedEffectiveScalarInputsReuseTheExactEngineWithoutOutputs() {
        val state = newState(seed = 940).apply {
            dashBufferTime = MutableGameState.DASH_INPUT_BUFFER_SECONDS
        }
        val engine = EngineState(state)
        val reducer = GameReducer()
        val unchangedInputs = listOf(
            GameplayInteractionPulse.ViewportChanged.fromValidated(1_280f, 720f, 1f),
            GameplayInteractionPulse.PointerMoved.fromValidated(900f, 360f, active = true),
            GameplayInteractionPulse.BrakeChanged(
                BrakeSource.KEYBOARD,
                false,
            ),
            GameplayInteractionPulse.BrakeChanged(
                BrakeSource.SECONDARY_POINTER,
                false,
            ),
            GameplayInteractionPulse.BrakeChanged(
                BrakeSource.TOUCH_CONTROL,
                false,
            ),
            GameplayInteractionPulse.DashRequested,
        )

        unchangedInputs.forEach { input ->
            val reduction = assertIs<GameReductionResult.Accepted>(
                reducer.reduce(engine, input),
            )

            assertSame(engine, reduction.state)
            assertTrue(reduction.outputs.isEmpty())
        }

        val pausedState = newState(seed = 941).apply {
            phase = GamePhase.PAUSED
            dashBufferTime = 0f
        }
        val pausedEngine = EngineState(pausedState)
        val pausedDash = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(pausedEngine, GameplayInteractionPulse.DashRequested),
        )
        assertSame(pausedEngine, pausedDash.state)
        assertTrue(pausedDash.outputs.isEmpty())
    }

    @Test
    fun scalarIdentityFastPathRetainsDerivedPointerAndPendingOutputSemantics() {
        val reducer = GameReducer()
        val staleAimState = newState(seed = 942).apply {
            lastAimDirectionX = 0f
            lastAimDirectionY = 1f
        }
        val staleAimEngine = EngineState(staleAimState)

        val correctedAim = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                staleAimEngine,
                GameplayInteractionPulse.PointerMoved.fromValidated(900f, 360f),
            ),
        )

        assertNotSame(staleAimEngine, correctedAim.state)
        assertEquals(1f, correctedAim.state.model.lastAimDirectionX)
        assertEquals(0f, correctedAim.state.model.lastAimDirectionY)

        val pendingOutputState = newState(seed = 943).apply {
            dashBufferTime = MutableGameState.DASH_INPUT_BUFFER_SECONDS
            pendingBankedMatter = 7L
            emitVisualFx(VisualFxCue.ClearAll)
            emitSound(GameplayAudioCue.DASH)
        }
        val pendingOutputEngine = EngineState(pendingOutputState)
        val drained = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(pendingOutputEngine, GameplayInteractionPulse.DashRequested),
        )

        assertNotSame(pendingOutputEngine, drained.state)
        assertIs<SimulationOutput.EmitVisualFx>(drained.outputs[0])
        assertEquals(7L, assertIs<SimulationOutput.PublishProgress>(drained.outputs[1]).update.bankedMatter)
        assertEquals(
            listOf(GameplayAudioCue.DASH),
            assertIs<SimulationOutput.AdvanceAudio>(drained.outputs[2]).cues,
        )
    }

    @Test
    fun pausedFrameFastPathDrainsPendingOutputsInCanonicalOrder() {
        val state = newState(seed = 944).apply {
            phase = GamePhase.PAUSED
            accumulator = 0f
            lastTransitionSteps = 0
            pendingBankedMatter = 9L
            emitVisualFx(VisualFxCue.ClearAll)
            emitSound(GameplayAudioCue.DASH)
        }
        val engine = EngineState(state)
        val frameDelta = 1f / 60f

        val reduction = assertIs<GameReductionResult.Accepted>(
            GameReducer().reduce(
                engine,
                GameplayInteractionPulse.FrameElapsed.fromValidated(frameDelta),
            ),
        )

        assertNotSame(engine, reduction.state)
        assertIs<SimulationOutput.EmitVisualFx>(reduction.outputs[0])
        assertEquals(
            9L,
            assertIs<SimulationOutput.PublishProgress>(reduction.outputs[1]).update.bankedMatter,
        )
        val audio = assertIs<SimulationOutput.AdvanceAudio>(reduction.outputs[2])
        assertEquals(frameDelta, audio.realDeltaSeconds)
        assertEquals(listOf(GameplayAudioCue.DASH), audio.cues)
    }

    @Test
    fun arcCoilChainsAtMostFourTargetsAndLeavesFifthUntouched() {
        // Level 18 opens all three forms: four targets and the wider hop.
        val state = newState(seed = 909).apply {
            weaponLevel = 18
        }
        state.equipWeaponForTesting(WeaponId.ARC_COIL)
        // Ion-immune Elites take the coil's (elementless) damage but no Charge, so no Overload chains on.
        val candidates = List(7) { index ->
            state.addEnemyForTesting(x = (index + 1) * 50f, y = 0f, type = EnemyType.ELITE)
                .also { assignAspect(it, StatusElement.ION.ordinal, immune = true) }
        }
        state.takeVisualFxCues()

        state.fireArcCoil(baseDamage = 25f)

        assertEquals(4, state.arcCoilTargetCount())
        candidates.take(4).forEach { enemy ->
            assertTrue(enemy.hp < enemy.maxHp)
        }
        candidates.drop(4).forEach { enemy -> assertEquals(enemy.maxHp, enemy.hp) }
        assertEquals(
            4,
            state.takeVisualFxCues().count { cue -> cue is VisualFxCue.WeaponArcAdded },
        )
    }

    @Test
    fun rewardChoiceGeneratorsAcceptThreeCandidatesAndDeferFirstNPlusOne() {
        val itemCards = 3
        listOf(itemCards, itemCards + 1).forEachIndexed { index, candidateCount ->
            val itemContent = canonicalGameplayContent.copy(
                items = canonicalGameplayContent.items.take(candidateCount).toImmutableList(),
            )
            val itemState = newState(seed = 910 + index, content = itemContent).apply {
                lifetimeMatter = 4_000L
            }
            assertEquals(itemCards, itemState.itemOfferSize())
            itemState.buildItemChoices()
            val selectedItemIds = itemState.choices.mapNotNull { choice -> choice.itemId }.toSet()
            assertEquals(itemCards, itemState.choices.size)
            assertEquals(itemCards, selectedItemIds.size)
            assertEquals(
                candidateCount - itemCards,
                itemContent.items.count { item -> item.id !in selectedItemIds },
            )

            val relicContent = canonicalGameplayContent.copy(
                relics = canonicalGameplayContent.relics.take(candidateCount).toImmutableList(),
            )
            val relicState = newState(seed = 914 + index, content = relicContent)
            relicState.buildRelicChoices()
            val selectedRelicIds = relicState.choices.mapNotNull { choice -> choice.relicId }.toSet()
            assertEquals(itemCards, selectedRelicIds.size)
            assertEquals(
                candidateCount - itemCards,
                relicContent.relics.count { relic -> relic.id !in selectedRelicIds },
            )
            // The fourth card declines into an empty slot; it never consumes a relic candidate.
            assertEquals(RelicChoiceAction.DECLINE, relicState.choices.last().relicAction)
            assertEquals(itemCards + 1, relicState.choices.size)
        }

        // A Totem recalibration offers three weapons, never the Ledger-widened item bound.
        listOf(WEAPON_OFFER_CHOICES, WEAPON_OFFER_CHOICES + 1).forEachIndexed { index, candidateCount ->
            val currentWeapon = canonicalGameplayContent.weapon(WeaponId.FLUX_WAKE)
            val weaponCandidates = canonicalGameplayContent.weapons
                .filter { definition -> definition.id != currentWeapon.id }
                .take(candidateCount)
            val weaponContent = canonicalGameplayContent.copy(
                weapons = (listOf(currentWeapon) + weaponCandidates).toImmutableList(),
            )
            val weaponState = newState(seed = 912 + index, content = weaponContent)
            weaponState.unlockWeaponsForTesting(weaponCandidates.map { it.id })
            weaponState.buildWeaponChoices()
            val selectedWeaponIds = weaponState.choices.mapNotNull { choice -> choice.weaponId }.toSet()
            assertEquals(WEAPON_OFFER_CHOICES, selectedWeaponIds.size)
            assertEquals(
                candidateCount - WEAPON_OFFER_CHOICES,
                weaponCandidates.count { weapon -> weapon.id !in selectedWeaponIds },
            )
        }

        // Gambler's Ledger adds exactly one card, filling but never exceeding the choice bound.
        val ledger = newState(seed = 919).apply {
            lifetimeMatter = 4_000L
            acquireItemForTesting(content.items.single { it.name == "Gambler's Ledger" }.id)
        }
        assertEquals(MAX_GENERATED_REWARD_CHOICES, ledger.itemOfferSize())
        ledger.buildItemChoices()
        assertEquals(MAX_GENERATED_REWARD_CHOICES, ledger.choices.mapNotNull { it.itemId }.toSet().size)
    }

    /** The generic chain bound shared by items, relics, reactions and Breaks: [MAX_CHAIN_TARGETS] links. */
    @Test
    fun chainWorkAcceptsSixTargetsAndRejectsTheSeventh() {
        fun chainOver(seed: Int, requested: Int): Pair<List<kinetickk.ball.gameplay.nucleus.model.Enemy>, Int> {
            val state = newState(seed = seed)
            val origin = state.addEnemyForTesting(x = 0f, y = 0f)
            val targets = List(MAX_CHAIN_TARGETS + 2) { index ->
                state.addEnemyForTesting(x = (index + 1) * 10f, y = 0f, hp = 100f)
            }
            state.chain(origin.x, origin.y, origin.id, requested, range = 100f) { target ->
                state.dealProcDamage(target, 10f, rescale = false)
            }
            return targets to state.takeVisualFxCues().count { cue -> cue is VisualFxCue.WeaponArcAdded }
        }

        val (exactTargets, exactArcs) = chainOver(seed = 916, requested = MAX_CHAIN_TARGETS)
        assertTrue(exactTargets.take(MAX_CHAIN_TARGETS).all { target -> target.hp < target.maxHp })
        assertTrue(exactTargets.drop(MAX_CHAIN_TARGETS).all { target -> target.hp == target.maxHp })
        assertEquals(MAX_CHAIN_TARGETS, exactArcs)

        // A larger request never performs the first link past the bound.
        val (overflowTargets, overflowArcs) = chainOver(seed = 917, requested = MAX_CHAIN_TARGETS + 1)
        assertTrue(overflowTargets.take(MAX_CHAIN_TARGETS).all { target -> target.hp < target.maxHp })
        assertEquals(overflowTargets[MAX_CHAIN_TARGETS].maxHp, overflowTargets[MAX_CHAIN_TARGETS].hp)
        assertEquals(MAX_CHAIN_TARGETS, overflowArcs)
    }

    @Test
    fun nearestEnemyScansPreserveFirstTieRangeAndExclusionSemantics() {
        val state = newState(seed = 918)
        val first = state.addEnemyForTesting(x = 3f, y = 4f, hp = 100f)
        val second = state.addEnemyForTesting(x = -3f, y = 4f, hp = 100f)

        assertSame(first, state.nearestEnemy(x = 0f, y = 0f, range = 5f))
        assertSame(
            second,
            state.nearestOtherEnemy(
                x = 0f,
                y = 0f,
                excludedId = first.id,
                range = 5f,
            ),
        )
        assertNull(state.nearestEnemy(x = 0f, y = 0f, range = 4.99f))
        assertSame(second, state.nearestOtherEnemy(first.x, first.y, first.id, range = 10f))

        second.dead = true
        assertNull(state.nearestOtherEnemy(first.x, first.y, first.id, range = 10f))
    }

    @Test
    fun trailSamplerProcessesThirtyTwoAndDropsThirtyThirdSample() {
        val exact = newState(seed = 906).apply {
            velocityX = 100f
            coreX = TRAIL_SAMPLE_DISTANCE * MutableGameState.MAX_TRAIL_SAMPLES_PER_UPDATE
        }
        exact.sampleFluxTrail()

        assertEquals(MutableGameState.MAX_TRAIL_SAMPLES_PER_UPDATE, exact.trail.size)
        assertEquals(exact.coreX, exact.trail.last().x)

        val overflow = newState(seed = 907).apply {
            velocityX = 100f
            coreX = TRAIL_SAMPLE_DISTANCE * (MutableGameState.MAX_TRAIL_SAMPLES_PER_UPDATE + 1)
        }
        overflow.sampleFluxTrail()

        assertEquals(MutableGameState.MAX_TRAIL_SAMPLES_PER_UPDATE, overflow.trail.size)
        assertEquals(
            TRAIL_SAMPLE_DISTANCE * MutableGameState.MAX_TRAIL_SAMPLES_PER_UPDATE,
            overflow.trail.last().x,
        )
    }

    @Test
    fun projectileHitHistoryAcceptsOneHundredTwentyRejectsNextThenReclaimsDeadEntry() {
        val projectile = Projectile(0f, 0f, 0f, 0f, 1f, 1f)
        for (enemyId in Projectile.MAX_HIT_ENEMY_IDS downTo 1) {
            assertTrue(projectile.tryRecordEnemyHit(enemyId))
        }

        val firstOverflowId = Projectile.MAX_HIT_ENEMY_IDS + 1
        assertFalse(projectile.tryRecordEnemyHit(firstOverflowId))
        assertEquals(
            Projectile.MAX_HIT_ENEMY_IDS,
            (1..firstOverflowId).count(projectile::hasRecordedEnemyHit),
        )

        val liveEnemyIds = IntArray(Projectile.MAX_HIT_ENEMY_IDS) { index -> index + 2 }
        projectile.retainLiveEnemyHits(liveEnemyIds, liveEnemyIds.size)

        assertTrue(projectile.tryRecordEnemyHit(firstOverflowId))
        assertEquals(
            Projectile.MAX_HIT_ENEMY_IDS,
            (1..firstOverflowId).count(projectile::hasRecordedEnemyHit),
        )
        assertFalse(projectile.hasRecordedEnemyHit(1))
        assertTrue(projectile.hasRecordedEnemyHit(firstOverflowId))
    }

    @Test
    fun projectileCopyOwnsAnIndependentCompactHitHistory() {
        val source = Projectile(0f, 0f, 0f, 0f, 1f, 1f)
        val recordedEnemyIds = listOf(17, 3, 41, 7, 29, 11, 23, 5, 37, 13, 31, 19)
        recordedEnemyIds.forEach { enemyId ->
            assertTrue(source.tryRecordEnemyHit(enemyId))
        }
        val isolated = source.isolatedCopy()

        source.retainLiveEnemyHits(IntArray(0), 0)

        recordedEnemyIds.forEach { enemyId ->
            assertFalse(source.hasRecordedEnemyHit(enemyId))
            assertTrue(isolated.hasRecordedEnemyHit(enemyId))
        }
        assertTrue(isolated.tryRecordEnemyHit(43))
        assertFalse(source.hasRecordedEnemyHit(43))
    }

    @Test
    fun projectileHitHistoryMembershipIsCorrectAtLinearAndBinarySearchBoundaries() {
        listOf(0, 4, 12, Projectile.MAX_HIT_ENEMY_IDS).forEach { historySize ->
            val projectile = Projectile(0f, 0f, 0f, 0f, 1f, 1f)
            val enemyIds = List(historySize) { index -> index * 2 + 1 }
            enemyIds.asReversed().forEach { enemyId ->
                assertTrue(projectile.tryRecordEnemyHit(enemyId))
            }

            enemyIds.forEach { enemyId ->
                assertTrue(
                    projectile.hasRecordedEnemyHit(enemyId),
                    "missing recorded id $enemyId at size $historySize",
                )
                assertTrue(
                    projectile.tryRecordEnemyHit(enemyId),
                    "duplicate id $enemyId must remain accepted",
                )
            }
            repeat(historySize + 1) { index ->
                val absentEnemyId = index * 2
                assertFalse(
                    projectile.hasRecordedEnemyHit(absentEnemyId),
                    "unexpected id $absentEnemyId at size $historySize",
                )
            }
        }
    }

    @Test
    fun projectileHitHistoryRetentionMergesSortedLiveIdsAndHonorsTheirLogicalCount() {
        val projectile = Projectile(0f, 0f, 0f, 0f, 1f, 1f)
        listOf(Int.MAX_VALUE, 12, 1, 120, 4, 8, Int.MIN_VALUE, 16).forEach { enemyId ->
            assertTrue(projectile.tryRecordEnemyHit(enemyId))
        }
        val liveEnemyIds = intArrayOf(Int.MIN_VALUE, 1, 8, 16, 120, Int.MAX_VALUE, 12)

        projectile.retainLiveEnemyHits(liveEnemyIds, liveEnemyIds.size - 1)

        listOf(Int.MIN_VALUE, 1, 8, 16, 120, Int.MAX_VALUE).forEach { enemyId ->
            assertTrue(projectile.hasRecordedEnemyHit(enemyId))
        }
        listOf(4, 12).forEach { enemyId ->
            assertFalse(projectile.hasRecordedEnemyHit(enemyId))
        }
    }

    private fun newState(
        seed: Int,
        content: GameplayContentSnapshot = canonicalGameplayContent,
    ) = MutableGameState(
        content = content,
        seed = seed,
        initialMatter = 0,
    )

    private fun weaponNode(index: Int) = WeaponNode(
        type = WeaponNodeType.GRAVITY_MINE,
        x = index.toFloat(),
        y = 0f,
        life = 1f,
        maxLife = 1f,
        radius = 1f,
    )

    /** A live weapon entity told apart by its x. */
    private fun weaponEntity(kind: WeaponEntityKind, index: Int) = WeaponEntity(kind, x = index.toFloat(), y = 0f, radius = 1f, life = 1f, maxLife = 1f)

    /** A Hunter missile standing on (x, y): it strikes whatever stands there in the hit pass. */
    private fun missileAt(x: Float, y: Float) =
        WeaponEntity(WeaponEntityKind.MISSILE, x, y, HUNTER_MISSILE_RADIUS, HUNTER_MISSILE_LIFE, HUNTER_MISSILE_LIFE, vx = 1f, damage = 1_000f)

    private fun choice(index: Int) = ChoiceOption(
        type = ChoiceType.ITEM,
        title = "Choice $index",
        description = "Bounded choice $index",
        tag = "TEST",
        itemId = canonicalGameplayContent.items.first().id,
    )

    private companion object {
        const val TRAIL_SAMPLE_DISTANCE = 22f
    }
}
