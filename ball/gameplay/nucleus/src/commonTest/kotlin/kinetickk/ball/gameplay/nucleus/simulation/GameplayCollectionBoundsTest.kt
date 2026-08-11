// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayPointerAxis
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.WeaponNode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
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
    fun rewardChoiceGeneratorsAcceptThreeCandidatesAndDeferFirstNPlusOne() {
        listOf(MAX_GENERATED_REWARD_CHOICES, MAX_GENERATED_REWARD_CHOICES + 1)
            .forEachIndexed { index, candidateCount ->
                val itemContent = canonicalGameplayContent.copy(
                    items = canonicalGameplayContent.items.take(candidateCount).toImmutableList(),
                )
                val itemState = newState(seed = 910 + index, content = itemContent).apply {
                    lifetimeMatter = 4_000L
                }
                itemState.buildItemChoices()
                val selectedItemIds = itemState.choices.mapNotNull { choice -> choice.itemId }.toSet()
                assertEquals(MAX_GENERATED_REWARD_CHOICES, selectedItemIds.size)
                assertEquals(
                    candidateCount - MAX_GENERATED_REWARD_CHOICES,
                    itemContent.items.count { item -> item.id !in selectedItemIds },
                )

                val currentWeapon = canonicalGameplayContent.weapon(WeaponId.FLUX_WAKE)
                val weaponCandidates = canonicalGameplayContent.weapons
                    .filter { definition -> definition.id != currentWeapon.id }
                    .take(candidateCount)
                val weaponContent = canonicalGameplayContent.copy(
                    weapons = (listOf(currentWeapon) + weaponCandidates).toImmutableList(),
                )
                val weaponState = newState(seed = 912 + index, content = weaponContent)
                weaponState.buildWeaponChoices()
                val selectedWeaponIds = weaponState.choices.mapNotNull { choice -> choice.weaponId }.toSet()
                assertEquals(MAX_GENERATED_REWARD_CHOICES, selectedWeaponIds.size)
                assertEquals(
                    candidateCount - MAX_GENERATED_REWARD_CHOICES,
                    weaponCandidates.count { weapon -> weapon.id !in selectedWeaponIds },
                )

                val relicContent = canonicalGameplayContent.copy(
                    relics = canonicalGameplayContent.relics.take(candidateCount).toImmutableList(),
                )
                val relicState = newState(seed = 914 + index, content = relicContent)
                relicState.buildRelicChoices()
                val selectedRelicIds = relicState.choices.mapNotNull { choice -> choice.relicId }.toSet()
                assertEquals(MAX_GENERATED_REWARD_CHOICES, selectedRelicIds.size)
                assertEquals(
                    candidateCount - MAX_GENERATED_REWARD_CHOICES,
                    relicContent.relics.count { relic -> relic.id !in selectedRelicIds },
                )
            }
    }

    @Test
    fun relicChainWorkAcceptsFiveAndRejectsSixthIteration() {
        val exact = newState(seed = 916)
        val exactOrigin = exact.addEnemyForTesting(x = 0f, y = 0f)
        val exactTargets = List(exact.content.relicPolicy.maxRank + 1) { index ->
            exact.addEnemyForTesting(x = (index + 1) * 10f, y = 0f, hp = 100f)
        }

        exact.chainRelicDamage(
            origin = exactOrigin,
            count = exact.content.relicPolicy.maxRank,
            range = 100f,
            damage = 10f,
        )

        assertTrue(exactTargets.take(exact.content.relicPolicy.maxRank).all { target -> target.hp < target.maxHp })
        assertEquals(exactTargets.last().maxHp, exactTargets.last().hp)
        assertEquals(
            exact.content.relicPolicy.maxRank,
            exact.takeVisualFxCues().count { cue -> cue is VisualFxCue.WeaponArcAdded },
        )

        val overflow = newState(seed = 917)
        val overflowOrigin = overflow.addEnemyForTesting(x = 0f, y = 0f)
        val overflowTargets = List(overflow.content.relicPolicy.maxRank + 1) { index ->
            overflow.addEnemyForTesting(x = (index + 1) * 10f, y = 0f, hp = 100f)
        }
        val before = overflowTargets.map { target -> target.hp }

        assertFailsWith<IllegalArgumentException> {
            overflow.chainRelicDamage(
                origin = overflowOrigin,
                count = overflow.content.relicPolicy.maxRank + 1,
                range = 100f,
                damage = 10f,
            )
        }
        assertEquals(before, overflowTargets.map { target -> target.hp })
        assertTrue(overflow.takeVisualFxCues().isEmpty())
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
