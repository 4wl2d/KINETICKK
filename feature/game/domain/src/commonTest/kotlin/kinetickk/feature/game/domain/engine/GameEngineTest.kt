// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.engine

import kinetickk.core.collections.ImmutableList
import kinetickk.core.collections.ImmutableSet
import kinetickk.feature.game.domain.model.GamePhase
import kinetickk.feature.game.domain.model.SettingsRow
import kinetickk.feature.game.domain.model.StoredProgress
import kinetickk.feature.game.domain.model.UiScreen
import kinetickk.feature.game.domain.model.WeaponId
import kinetickk.feature.game.domain.projection.EnemyProjection
import kinetickk.feature.game.domain.projection.GameProjection
import kinetickk.feature.game.domain.protocol.BrakeSource
import kinetickk.feature.game.domain.protocol.GameEffect
import kinetickk.feature.game.domain.protocol.GameIntent
import kinetickk.feature.game.domain.protocol.GameRejection
import kinetickk.feature.game.domain.protocol.VisualFxCue
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
            GameIntent.ViewportChanged(width = 1_280f, height = 720f, density = 1.5f),
            GameIntent.RunStartRequested,
            GameIntent.PointerMoved(x = 1_100f, y = 240f),
            GameIntent.FrameElapsed(0.1f),
            GameIntent.BrakeChanged(BrakeSource.KEYBOARD, active = true),
            GameIntent.DashRequested,
            GameIntent.FrameElapsed(0.1f),
            GameIntent.BrakeChanged(BrakeSource.KEYBOARD, active = false),
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

        val started = assertIs<GameDispatchResult.Committed>(
            engine.dispatch(GameIntent.RunStartRequested),
        )
        assertEquals(1uL, started.snapshot.revision)
        assertEquals(GamePhase.RUNNING, started.snapshot.projection.phase)
        assertTrue(started.effects.any { it is GameEffect.EmitVisualFx })
        assertTrue(started.effects.any { it is GameEffect.AdvanceAudio })

        val muted = assertIs<GameDispatchResult.Committed>(
            engine.dispatch(GameIntent.MuteToggled),
        )
        assertEquals(2uL, muted.snapshot.revision)
        assertTrue(muted.effects.any { it is GameEffect.PersistProgress })
        assertTrue(muted.effects.any { it is GameEffect.AdvanceAudio })

        val gesture = assertIs<GameDispatchResult.Committed>(
            engine.dispatch(GameIntent.UserGestureObserved),
        )
        assertEquals(3uL, gesture.snapshot.revision)
        assertEquals(1, gesture.effects.size)
        assertSame(GameEffect.EnsureAudioUnlocked, gesture.effects.single())
    }

    @Test
    fun invalidIntentsAreRejectedWithoutMutationOrEffects() {
        val engine = engine()
        val before = engine.snapshot()

        listOf(
            GameIntent.FrameElapsed(Float.NaN),
            GameIntent.FrameElapsed(-0.001f),
            GameIntent.FrameElapsed(1.001f),
            GameIntent.ViewportChanged(Float.POSITIVE_INFINITY, 720f, 1f),
            GameIntent.ViewportChanged(32_769f, 720f, 1f),
            GameIntent.ViewportChanged(1_280f, 720f, 0.49f),
            GameIntent.PointerMoved(-1f, 20f),
            GameIntent.PointerMoved(1_281f, 20f),
            GameIntent.ChoiceSelected(4),
            GameIntent.SettingAdjusted(SettingsRow.MASTER_VOLUME, direction = 0),
            GameIntent.SettingsPageSelected(-1),
            GameIntent.ArmoryPageSelected(Int.MAX_VALUE),
            GameIntent.CodexPageSelected(Int.MAX_VALUE),
        ).forEach { invalid ->
            val rejected = assertIs<GameDispatchResult.Rejected>(engine.dispatch(invalid))
            assertIs<GameRejection.InvalidInput>(rejected.reason)
            assertSame(before, engine.snapshot())
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun committedProjectionOwnsImmutableCollectionsAndDoesNotChangeLater() {
        val engine = engine(seed = 17)
        val retained = assertIs<GameDispatchResult.Committed>(
            engine.dispatch(GameIntent.RunStartRequested),
        ).snapshot
        val retainedProjection = retained.projection
        val retainedSnapshot = retained.testSnapshot()

        assertIs<ImmutableList<EnemyProjection>>(retainedProjection.enemies)
        assertIs<ImmutableSet<WeaponId>>(retainedProjection.unlockedWeapons)
        assertFalse((retainedProjection.enemies as Any) is MutableList<*>)
        assertFailsWith<ClassCastException> {
            (retainedProjection.enemies as Any) as MutableList<EnemyProjection>
        }

        repeat(8) {
            assertIs<GameDispatchResult.Committed>(engine.dispatch(GameIntent.FrameElapsed(0.1f)))
        }

        assertEquals(retainedSnapshot, retained.testSnapshot())
        assertNotEquals(retained.revision, engine.snapshot().revision)
    }

    @Test
    fun frameDeltaIsClampedByTheSimulationFixedStepBudget() {
        val engine = engine()
        assertIs<GameDispatchResult.Committed>(engine.dispatch(GameIntent.RunStartRequested))

        val frame = assertIs<GameDispatchResult.Committed>(
            engine.dispatch(GameIntent.FrameElapsed(1f)),
        )

        assertTrue(frame.snapshot.projection.elapsed <= 48 * GameProjection.FIXED_STEP)
        val audio = assertIs<GameEffect.AdvanceAudio>(
            frame.effects.single { it is GameEffect.AdvanceAudio },
        )
        assertEquals(1f, audio.realDeltaSeconds)
    }

    @Test
    fun visualConsequencesAreExplicitAndNotStoredInTheProjection() {
        val engine = engine()

        val committed = assertIs<GameDispatchResult.Committed>(
            engine.dispatch(GameIntent.RunStartRequested),
        )

        val visualFx = assertIs<GameEffect.EmitVisualFx>(
            committed.effects.first { it is GameEffect.EmitVisualFx },
        )
        assertIs<ImmutableList<VisualFxCue>>(visualFx.cues)
        assertTrue(visualFx.cues.any { it == VisualFxCue.ClearAll })
    }

    @Test
    fun bootstrapProgressIsVisibleAtRevisionZero() {
        val engine = GameEngine.create(
            bootstrapProgress = StoredProgress(matter = 42, lifetimeMatter = 84),
            seed = 7,
        )

        assertEquals(0uL, engine.snapshot().revision)
        assertEquals(42L, engine.snapshot().projection.totalMatter)
        assertEquals(84L, engine.snapshot().projection.lifetimeMatter)
    }

    private fun engine(seed: Int = 731_991): GameEngine = GameEngine.create(
        bootstrapProgress = null,
        seed = seed,
        initialMatter = 0,
    )
}

private data class ProjectionSnapshot(
    val revision: ULong,
    val phase: GamePhase,
    val screen: UiScreen,
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

private fun GameSnapshot.testSnapshot(): ProjectionSnapshot = projection.let { projection ->
    ProjectionSnapshot(
        revision = revision,
        phase = projection.phase,
        screen = projection.screen,
        settings = projection.settings,
        coreX = projection.coreX,
        coreY = projection.coreY,
        velocityX = projection.velocityX,
        velocityY = projection.velocityY,
        pointerX = projection.pointerX,
        pointerY = projection.pointerY,
        elapsed = projection.elapsed,
        hp = projection.hp,
        shield = projection.shield,
        level = projection.level,
        data = projection.data,
        keys = projection.keys,
        kills = projection.kills,
        combo = projection.combo,
        runMatter = projection.runMatter,
        totalMatter = projection.totalMatter,
        message = projection.message,
        enemies = projection.enemies.toList(),
        projectiles = projection.projectiles.toList(),
        pickups = projection.pickups.toList(),
        trail = projection.trail.toList(),
        weaponNodes = projection.weaponNodes.toList(),
        weaponOrbitals = projection.weaponOrbitals.toList(),
    )
}
