// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.GameplayProgressCapability
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.ProfileMutationResult
import kinetickk.ball.gameplay.api.RunConfiguration
import kinetickk.ball.gameplay.nucleus.engine.GameDispatchResult
import kinetickk.ball.gameplay.nucleus.model.GamePhase
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val testContent = SyntheticGameplayContent

class GameComponentTest {
    @Test
    fun componentExecutesAudioVisualAndGestureEffectsAfterCommit() {
        val progress = RecordingGameplayProgressCapability()
        val observedOutputRevisions = mutableListOf<ULong>()
        lateinit var component: GameComponent
        val audio = RecordingGameplayAudioExecutor {
            observedOutputRevisions += component.snapshot().revision
        }
        component = GameComponent.create(
            configuration = RunConfiguration(content = testContent),
            progressCapability = progress,
            audioExecutor = audio,
            seed = 2,
        )

        val frame = assertIs<GameDispatchResult.Committed>(
            component.dispatch(GameplayAction.FrameElapsed(0.1f)),
        )
        assertEquals(frame.snapshot, component.snapshot())
        assertSame(testContent, frame.snapshot.renderModel.content)
        assertEquals(listOf(frame.snapshot.revision), observedOutputRevisions)
        assertEquals(1, audio.frames.size)

        component.dispatch(GameplayAction.DashRequested)
        val dashFrame = assertIs<GameDispatchResult.Committed>(
            component.dispatch(GameplayAction.FrameElapsed(0.1f)),
        )
        assertEquals(dashFrame.snapshot, component.snapshot())
        assertTrue(component.visualFxSnapshot().shockwaves.isNotEmpty())

        val gesture = assertIs<GameDispatchResult.Committed>(
            component.dispatch(GameplayAction.UserGestureObserved),
        )
        assertEquals(gesture.snapshot, component.snapshot())
        assertEquals(gesture.snapshot.revision, observedOutputRevisions.last())
        assertEquals(1, audio.unlockCount)
        assertTrue(progress.updates.isEmpty())
    }

    @Test
    fun progressResourceFailureDoesNotRollBackChoiceCommit() {
        val progress = RecordingGameplayProgressCapability()
        val component = GameComponent.create(
            configuration = resilientRunConfiguration(),
            progressCapability = progress,
            audioExecutor = RecordingGameplayAudioExecutor(),
            seed = 11,
        )
        component.advanceToFirstItemChoice()
        val before = component.snapshot()
        val chosenItemId = requireNotNull(before.renderModel.choices.first().itemId)

        val selected = assertIs<GameDispatchResult.Committed>(
            component.dispatch(GameplayAction.ChoiceSelected(index = 0)),
        )

        assertEquals(before.revision + 1uL, selected.snapshot.revision)
        assertEquals(selected.snapshot, component.snapshot())
        assertEquals(1, selected.snapshot.renderModel.itemStack(chosenItemId))
        assertEquals(
            setOf(chosenItemId),
            progress.updates.single().discoveredItemIds.toSet(),
        )
    }

    @Test
    fun audioResourceFailuresDoNotRollBackCommittedState() {
        val audio = RecordingGameplayAudioExecutor(throwOnEveryCall = true)
        val component = GameComponent.create(
            configuration = RunConfiguration(content = testContent),
            progressCapability = RecordingGameplayProgressCapability(),
            audioExecutor = audio,
            seed = 3,
        )

        val frame = assertIs<GameDispatchResult.Committed>(
            component.dispatch(GameplayAction.FrameElapsed(0.1f)),
        )
        assertEquals(frame.snapshot, component.snapshot())
        assertEquals(1uL, frame.snapshot.revision)

        val gesture = assertIs<GameDispatchResult.Committed>(
            component.dispatch(GameplayAction.UserGestureObserved),
        )
        assertEquals(gesture.snapshot, component.snapshot())
        assertEquals(2uL, gesture.snapshot.revision)
        assertEquals(1, audio.frames.size)
        assertEquals(1, audio.unlockCount)
    }
}

private fun resilientRunConfiguration(): RunConfiguration = RunConfiguration(
    content = testContent,
    unlockedWeapons = WeaponId.entries.toImmutableSet(),
    metaRanks = testContent.metaUpgrades.map { it.maxRanks }.toImmutableList(),
)

private fun GameComponent.advanceToFirstItemChoice() {
    repeat(1_200) { frameIndex ->
        val phase = snapshot().renderModel.phase
        if (phase == GamePhase.CHOICE) return
        check(phase == GamePhase.RUNNING) {
            "Run ended before the first item choice at frame $frameIndex: $phase"
        }
        assertIs<GameDispatchResult.Committed>(dispatch(GameplayAction.FrameElapsed(0.1f)))
    }
    error("First item choice was not reached within the deterministic frame budget")
}

private class RecordingGameplayAudioExecutor(
    private val throwOnEveryCall: Boolean = false,
    private val onCallObserved: () -> Unit = {},
) : GameplayAudioExecutor {
    val frames = mutableListOf<Pair<Float, List<GameplayAudioCue>>>()
    var unlockCount = 0

    override fun advance(realDeltaSeconds: Float, cues: ImmutableList<GameplayAudioCue>) {
        onCallObserved()
        frames += realDeltaSeconds to cues.toList()
        if (throwOnEveryCall) error("audio resource unavailable")
    }

    override fun ensureUnlocked() {
        onCallObserved()
        unlockCount++
        if (throwOnEveryCall) error("audio resource unavailable")
    }
}

/** Deliberately narrow fake: configuration is supplied to the component separately. */
private class RecordingGameplayProgressCapability : GameplayProgressCapability {
    val updates = mutableListOf<GameplayProgressUpdate>()

    override fun applyGameplayProgress(update: GameplayProgressUpdate): ProfileMutationResult {
        updates += update
        error("profile persistence unavailable")
    }
}
