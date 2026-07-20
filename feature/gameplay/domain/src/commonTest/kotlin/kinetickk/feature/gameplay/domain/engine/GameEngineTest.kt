// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.engine

import kinetickk.core.content.*

import kinetickk.core.collections.ImmutableList
import kinetickk.core.profile.api.GameplayProfileSnapshot
import kinetickk.core.profile.api.PlayerEconomy
import kinetickk.core.profile.api.PlayerProfile
import kinetickk.feature.gameplay.domain.model.GamePhase
import kinetickk.feature.gameplay.domain.renderModel.EnemyProjection
import kinetickk.feature.gameplay.domain.renderModel.GameplayRenderModel
import kinetickk.feature.gameplay.domain.protocol.BrakeSource
import kinetickk.feature.gameplay.domain.protocol.GameEffect
import kinetickk.feature.gameplay.domain.protocol.GameplayAction
import kinetickk.feature.gameplay.domain.protocol.GameRejection
import kinetickk.feature.gameplay.domain.protocol.VisualFxCue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

class GameEngineTest {
    @Test
    fun sameSeedAndIntentTraceProduceTheSameProjection() {
        val trace = listOf(
            GameplayAction.ViewportChanged(width = 1_280f, height = 720f, density = 1.5f),
            GameplayAction.PointerMoved(x = 1_100f, y = 240f),
            GameplayAction.FrameElapsed(0.1f),
            GameplayAction.BrakeChanged(BrakeSource.KEYBOARD, active = true),
            GameplayAction.DashRequested,
            GameplayAction.FrameElapsed(0.1f),
            GameplayAction.BrakeChanged(BrakeSource.KEYBOARD, active = false),
        )
        val first = engine(seed = 91_337)
        val second = engine(seed = 91_337)

        trace.forEach { intent ->
            assertIs<GameDispatchResult.Committed>(first.dispatch(intent))
            assertIs<GameDispatchResult.Committed>(second.dispatch(intent))
        }

        assertEquals(first.snapshot().testSnapshot(), second.snapshot().testSnapshot())
    }

    @Test
    fun acceptedIntentCommitsOnceAndReturnsExplicitEffects() {
        val engine = engine()
        assertEquals(0uL, engine.snapshot().revision)
        assertSame(engine.snapshot(), engine.snapshot())

        val frame = assertIs<GameDispatchResult.Committed>(
            engine.dispatch(GameplayAction.FrameElapsed(0.1f)),
        )
        assertEquals(1uL, frame.snapshot.revision)
        assertEquals(GamePhase.RUNNING, frame.snapshot.renderModel.phase)
        assertTrue(frame.effects.any { it is GameEffect.EmitVisualFx })
        assertTrue(frame.effects.any { it is GameEffect.AdvanceAudio })

        val gesture = assertIs<GameDispatchResult.Committed>(
            engine.dispatch(GameplayAction.UserGestureObserved),
        )
        assertEquals(2uL, gesture.snapshot.revision)
        assertEquals(1, gesture.effects.size)
        assertSame(GameEffect.EnsureAudioUnlocked, gesture.effects.single())
    }

    @Test
    fun invalidIntentsAreRejectedWithoutMutationOrEffects() {
        val engine = engine()
        val before = engine.snapshot()

        listOf(
            GameplayAction.FrameElapsed(Float.NaN),
            GameplayAction.FrameElapsed(-0.001f),
            GameplayAction.FrameElapsed(1.001f),
            GameplayAction.ViewportChanged(Float.POSITIVE_INFINITY, 720f, 1f),
            GameplayAction.ViewportChanged(32_769f, 720f, 1f),
            GameplayAction.ViewportChanged(1_280f, 720f, 0.49f),
            GameplayAction.PointerMoved(-1f, 20f),
            GameplayAction.PointerMoved(1_281f, 20f),
            GameplayAction.ChoiceSelected(4),
        ).forEach { invalid ->
            val rejected = assertIs<GameDispatchResult.Rejected>(engine.dispatch(invalid))
            assertIs<GameRejection.InvalidInput>(rejected.reason)
            assertSame(before, engine.snapshot())
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun committedRenderModelOwnsImmutableCollectionsAndDoesNotChangeLater() {
        val engine = engine(seed = 17)
        val retained = assertIs<GameDispatchResult.Committed>(
            engine.dispatch(GameplayAction.ViewportChanged(1_280f, 720f, 1f)),
        ).snapshot
        val retainedRenderModel = retained.renderModel
        val retainedSnapshot = retained.testSnapshot()

        assertIs<ImmutableList<EnemyProjection>>(retainedRenderModel.enemies)
        assertFalse((retainedRenderModel.enemies as Any) is MutableList<*>)
        assertFailsWith<ClassCastException> {
            (retainedRenderModel.enemies as Any) as MutableList<EnemyProjection>
        }

        repeat(8) {
            assertIs<GameDispatchResult.Committed>(engine.dispatch(GameplayAction.FrameElapsed(0.1f)))
        }

        assertEquals(retainedSnapshot, retained.testSnapshot())
        assertNotEquals(retained.revision, engine.snapshot().revision)
    }

    @Test
    fun frameDeltaIsClampedByTheSimulationFixedStepBudget() {
        val engine = engine()

        val frame = assertIs<GameDispatchResult.Committed>(
            engine.dispatch(GameplayAction.FrameElapsed(1f)),
        )

        assertTrue(frame.snapshot.renderModel.elapsed <= 48 * GameplayRenderModel.FIXED_STEP)
        val audio = assertIs<GameEffect.AdvanceAudio>(
            frame.effects.single { it is GameEffect.AdvanceAudio },
        )
        assertEquals(1f, audio.realDeltaSeconds)
    }

    @Test
    fun visualConsequencesAreExplicitAndNotStoredInTheRenderModel() {
        val engine = engine()

        val committed = assertIs<GameDispatchResult.Committed>(
            engine.dispatch(GameplayAction.FrameElapsed(0.1f)),
        )

        val visualFx = assertIs<GameEffect.EmitVisualFx>(
            committed.effects.first { it is GameEffect.EmitVisualFx },
        )
        assertIs<ImmutableList<VisualFxCue>>(visualFx.cues)
        assertTrue(visualFx.cues.any { it is VisualFxCue.MotionSample })
    }

    @Test
    fun bootstrapProgressIsVisibleAtRevisionZero() {
        val engine = GameEngine.create(
            bootstrapProgress = PlayerProfile(
                economy = PlayerEconomy(matter = 42, lifetimeMatter = 84),
            ).toGameplaySnapshot(),
            seed = 7,
        )

        assertEquals(0uL, engine.snapshot().revision)
        assertEquals(42L, engine.snapshot().renderModel.totalMatter)
    }

    private fun engine(seed: Int = 731_991): GameEngine = GameEngine.create(
        bootstrapProgress = null,
        seed = seed,
        initialMatter = 0,
    )
}

private data class RenderSnapshot(
    val revision: ULong,
    val phase: GamePhase,
    val settings: Any,
    val coreX: Float,
    val coreY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val pointerX: Float,
    val pointerY: Float,
    val elapsed: Float,
    val hp: Float,
    val shield: Float,
    val level: Int,
    val data: Int,
    val keys: Int,
    val kills: Int,
    val combo: Int,
    val runMatter: Long,
    val totalMatter: Long,
    val message: String,
    val enemies: List<Any>,
    val projectiles: List<Any>,
    val pickups: List<Any>,
    val trail: List<Any>,
    val weaponNodes: List<Any>,
    val weaponOrbitals: List<Any>,
)

private fun GameSnapshot.testSnapshot(): RenderSnapshot = renderModel.let { model ->
    RenderSnapshot(
        revision = revision,
        phase = model.phase,
        settings = model.settings,
        coreX = model.coreX,
        coreY = model.coreY,
        velocityX = model.velocityX,
        velocityY = model.velocityY,
        pointerX = model.pointerX,
        pointerY = model.pointerY,
        elapsed = model.elapsed,
        hp = model.hp,
        shield = model.shield,
        level = model.level,
        data = model.data,
        keys = model.keys,
        kills = model.kills,
        combo = model.combo,
        runMatter = model.runMatter,
        totalMatter = model.totalMatter,
        message = model.message,
        enemies = model.enemies.toList(),
        projectiles = model.projectiles.toList(),
        pickups = model.pickups.toList(),
        trail = model.trail.toList(),
        weaponNodes = model.weaponNodes.toList(),
        weaponOrbitals = model.weaponOrbitals.toList(),
    )
}

private fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
    preferences = preferences,
    economy = economy,
    loadout = loadout,
    labProgress = labProgress,
    collection = collection,
    rebirthProgress = rebirthProgress,
)
