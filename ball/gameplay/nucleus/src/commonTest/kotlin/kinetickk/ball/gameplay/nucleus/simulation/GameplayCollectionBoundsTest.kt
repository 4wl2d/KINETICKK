// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.api.ChoiceOption
import kinetickk.ball.gameplay.api.ChoiceType
import kinetickk.ball.gameplay.api.GamePhase
import kinetickk.ball.gameplay.api.GameplayInputField
import kinetickk.ball.gameplay.api.GameplayInputReason
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.WeaponNodeType
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.WeaponNode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameplayCollectionBoundsTest {
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
        repeat(MutableGameState.MAX_WEAPON_NODES) { index ->
            assertTrue(state.tryAddWeaponNode(weaponNode(index)))
        }

        assertFalse(state.tryAddWeaponNode(weaponNode(MutableGameState.MAX_WEAPON_NODES)))

        assertEquals(MutableGameState.MAX_WEAPON_NODES, state.weaponNodes.size)
        assertEquals(0f, state.weaponNodes.first().x)
        assertEquals((MutableGameState.MAX_WEAPON_NODES - 1).toFloat(), state.weaponNodes.last().x)
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
            reducer.reduce(EngineState(state), GameplayInteractionPulse.ChoiceSelected(3)),
        )

        val rejected = assertIs<GameReductionResult.Rejected>(
            reducer.reduce(EngineState(state), GameplayInteractionPulse.ChoiceSelected(4)),
        )
        assertEquals(
            GameplayRejection.InvalidInput(
                GameplayInputField.CHOICE_INDEX,
                GameplayInputReason.ABOVE_MAXIMUM,
            ),
            rejected.reason,
        )
    }

    @Test
    fun authoritativeIngressAcceptsExactBoundsAndRejectsNextRepresentableValues() {
        val state = newState(seed = 908)
        val reducer = GameReducer()

        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(EngineState(state), GameplayInteractionPulse.FrameElapsed(0f)),
        )
        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(EngineState(state), GameplayInteractionPulse.FrameElapsed(1f)),
        )
        assertInvalidInput(
            reducer,
            state,
            GameplayInteractionPulse.FrameElapsed(-Float.MIN_VALUE),
            GameplayInputField.FRAME_DELTA_SECONDS,
            GameplayInputReason.BELOW_MINIMUM,
        )
        assertInvalidInput(
            reducer,
            state,
            GameplayInteractionPulse.FrameElapsed(Float.fromBits(1f.toBits() + 1)),
            GameplayInputField.FRAME_DELTA_SECONDS,
            GameplayInputReason.ABOVE_MAXIMUM,
        )

        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                EngineState(state),
                GameplayInteractionPulse.ViewportChanged(1f, 1f, 0.5f),
            ),
        )
        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                EngineState(state),
                GameplayInteractionPulse.ViewportChanged(32_768f, 32_768f, 8f),
            ),
        )
        assertInvalidInput(
            reducer,
            state,
            GameplayInteractionPulse.ViewportChanged(
                Float.fromBits(1f.toBits() - 1),
                1f,
                0.5f,
            ),
            GameplayInputField.VIEWPORT_WIDTH,
            GameplayInputReason.BELOW_MINIMUM,
        )
        assertInvalidInput(
            reducer,
            state,
            GameplayInteractionPulse.ViewportChanged(
                1f,
                Float.fromBits(1f.toBits() - 1),
                0.5f,
            ),
            GameplayInputField.VIEWPORT_HEIGHT,
            GameplayInputReason.BELOW_MINIMUM,
        )
        assertInvalidInput(
            reducer,
            state,
            GameplayInteractionPulse.ViewportChanged(1f, 1f, Float.fromBits(0.5f.toBits() - 1)),
            GameplayInputField.DENSITY,
            GameplayInputReason.BELOW_MINIMUM,
        )
        assertInvalidInput(
            reducer,
            state,
            GameplayInteractionPulse.ViewportChanged(
                Float.fromBits(32_768f.toBits() + 1),
                32_768f,
                8f,
            ),
            GameplayInputField.VIEWPORT_WIDTH,
            GameplayInputReason.ABOVE_MAXIMUM,
        )
        assertInvalidInput(
            reducer,
            state,
            GameplayInteractionPulse.ViewportChanged(
                32_768f,
                Float.fromBits(32_768f.toBits() + 1),
                8f,
            ),
            GameplayInputField.VIEWPORT_HEIGHT,
            GameplayInputReason.ABOVE_MAXIMUM,
        )
        assertInvalidInput(
            reducer,
            state,
            GameplayInteractionPulse.ViewportChanged(32_768f, 32_768f, Float.fromBits(8f.toBits() + 1)),
            GameplayInputField.DENSITY,
            GameplayInputReason.ABOVE_MAXIMUM,
        )

        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                EngineState(state),
                GameplayInteractionPulse.PointerMoved(0f, 0f),
            ),
        )
        assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                EngineState(state),
                GameplayInteractionPulse.PointerMoved(state.screenWidth, state.screenHeight),
            ),
        )
        assertInvalidInput(
            reducer,
            state,
            GameplayInteractionPulse.PointerMoved(-Float.MIN_VALUE, 0f),
            GameplayInputField.POINTER_X,
            GameplayInputReason.BELOW_MINIMUM,
        )
        assertInvalidInput(
            reducer,
            state,
            GameplayInteractionPulse.PointerMoved(0f, -Float.MIN_VALUE),
            GameplayInputField.POINTER_Y,
            GameplayInputReason.BELOW_MINIMUM,
        )
        assertInvalidInput(
            reducer,
            state,
            GameplayInteractionPulse.PointerMoved(
                Float.fromBits(state.screenWidth.toBits() + 1),
                state.screenHeight,
            ),
            GameplayInputField.POINTER_X,
            GameplayInputReason.ABOVE_MAXIMUM,
        )
        assertInvalidInput(
            reducer,
            state,
            GameplayInteractionPulse.PointerMoved(
                state.screenWidth,
                Float.fromBits(state.screenHeight.toBits() + 1),
            ),
            GameplayInputField.POINTER_Y,
            GameplayInputReason.ABOVE_MAXIMUM,
        )
    }

    @Test
    fun arcCoilTargetsSixNearestAndLeavesSeventhUntouched() {
        val state = newState(seed = 909).apply {
            weaponLevel = 9
        }
        val candidates = List(MAX_ARC_COIL_TARGETS + 1) { index ->
            state.addEnemyForTesting(x = (index + 1) * 50f, y = 0f)
        }

        state.fireArcCoil(baseDamage = 25f)

        candidates.take(MAX_ARC_COIL_TARGETS).forEach { enemy ->
            assertTrue(enemy.hp < enemy.maxHp)
        }
        assertEquals(candidates.last().maxHp, candidates.last().hp)
        assertEquals(
            MAX_ARC_COIL_TARGETS,
            state.takeVisualFxCues().count { cue -> cue is VisualFxCue.WeaponArcAdded },
        )
    }

    @Test
    fun rewardChoiceGeneratorsSelectThreeFromLargerCandidatePools() {
        val itemState = newState(seed = 910).apply { lifetimeMatter = 4_000L }
        assertTrue(itemState.content.items.size > MAX_GENERATED_REWARD_CHOICES)
        itemState.buildItemChoices()
        assertEquals(MAX_GENERATED_REWARD_CHOICES, itemState.choices.size)

        val weaponState = newState(seed = 911)
        assertTrue(
            weaponState.content.weapons.count { definition -> definition.id != weaponState.weapon } >
                MAX_GENERATED_REWARD_CHOICES,
        )
        weaponState.buildWeaponChoices()
        assertEquals(MAX_GENERATED_REWARD_CHOICES, weaponState.choices.size)

        val relicState = newState(seed = 912)
        assertTrue(relicState.content.relics.size > MAX_GENERATED_REWARD_CHOICES)
        relicState.buildRelicChoices()
        assertEquals(MAX_GENERATED_REWARD_CHOICES, relicState.choices.size)
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
        repeat(Projectile.MAX_HIT_ENEMY_IDS) { index ->
            assertTrue(projectile.tryRecordEnemyHit(index + 1))
        }

        val firstOverflowId = Projectile.MAX_HIT_ENEMY_IDS + 1
        assertFalse(projectile.tryRecordEnemyHit(firstOverflowId))
        assertEquals(
            Projectile.MAX_HIT_ENEMY_IDS,
            (1..firstOverflowId).count(projectile::hasRecordedEnemyHit),
        )

        projectile.retainLiveEnemyHits((2..firstOverflowId).toSet())

        assertTrue(projectile.tryRecordEnemyHit(firstOverflowId))
        assertEquals(
            Projectile.MAX_HIT_ENEMY_IDS,
            (1..firstOverflowId).count(projectile::hasRecordedEnemyHit),
        )
        assertFalse(projectile.hasRecordedEnemyHit(1))
        assertTrue(projectile.hasRecordedEnemyHit(firstOverflowId))
    }

    private fun newState(seed: Int) = MutableGameState(
        content = canonicalGameplayContent,
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

    private fun choice(index: Int) = ChoiceOption(
        type = ChoiceType.ITEM,
        title = "Choice $index",
        description = "Bounded choice $index",
        tag = "TEST",
        itemId = canonicalGameplayContent.items.first().id,
    )

    private fun assertInvalidInput(
        reducer: GameReducer,
        state: MutableGameState,
        pulse: GameplayInteractionPulse,
        field: GameplayInputField,
        reason: GameplayInputReason,
    ) {
        val rejected = assertIs<GameReductionResult.Rejected>(
            reducer.reduce(EngineState(state), pulse),
        )
        assertEquals(GameplayRejection.InvalidInput(field, reason), rejected.reason)
    }

    private companion object {
        const val TRAIL_SAMPLE_DISTANCE = 22f
    }
}
