// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.impl

import kinetickk.core.collections.toImmutableList
import kinetickk.core.collections.toImmutableSet
import kinetickk.core.content.MetaUpgradeCatalog
import kinetickk.core.content.WeaponId
import kinetickk.core.profile.api.GameplayProgressCapability
import kinetickk.core.profile.api.GameplayProgressUpdate
import kinetickk.core.profile.api.ProfileMutationResult
import kinetickk.feature.gameplay.api.GameplayOutput
import kinetickk.feature.gameplay.api.RunConfiguration
import kinetickk.feature.gameplay.domain.engine.GameDispatchResult
import kinetickk.feature.gameplay.domain.model.GamePhase
import kinetickk.feature.gameplay.domain.protocol.GameplayAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameComponentTest {
    @Test
    fun componentExecutesAudioVisualAndGestureEffectsAfterCommit() {
        val progress = RecordingGameplayProgressCapability()
        val observedOutputRevisions = mutableListOf<ULong>()
        lateinit var component: GameComponent
        val outputs = RecordingGameplayOutputs {
            observedOutputRevisions += component.snapshot().revision
        }
        component = GameComponent.create(
            configuration = RunConfiguration(),
            progressCapability = progress,
            seed = 2,
            onOutput = outputs,
        )

        val frame = assertIs<GameDispatchResult.Committed>(
            component.dispatch(GameplayAction.FrameElapsed(0.1f)),
        )
        assertEquals(frame.snapshot, component.snapshot())
        assertEquals(listOf(frame.snapshot.revision), observedOutputRevisions)
        assertEquals(1, outputs.audioFrames.size)

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
        assertEquals(1, outputs.gestureCount)
        assertTrue(progress.updates.isEmpty())
    }

    @Test
    fun progressResourceFailureDoesNotRollBackChoiceCommit() {
        val progress = RecordingGameplayProgressCapability()
        val component = GameComponent.create(
            configuration = resilientRunConfiguration(),
            progressCapability = progress,
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
    fun outputResourceFailuresDoNotRollBackCommittedState() {
        val outputs = RecordingGameplayOutputs(throwOnEveryOutput = true)
        val component = GameComponent.create(
            configuration = RunConfiguration(),
            progressCapability = RecordingGameplayProgressCapability(),
            seed = 3,
            onOutput = outputs,
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
        assertEquals(1, outputs.audioFrames.size)
        assertEquals(1, outputs.gestureCount)
    }
}

private fun resilientRunConfiguration(): RunConfiguration = RunConfiguration(
    unlockedWeapons = WeaponId.entries.toImmutableSet(),
    metaRanks = MetaUpgradeCatalog.all.map { it.maxRanks }.toImmutableList(),
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

private class RecordingGameplayOutputs(
    private val throwOnEveryOutput: Boolean = false,
    private val onOutputObserved: () -> Unit = {},
) : (GameplayOutput) -> Unit {
    val audioFrames = mutableListOf<GameplayOutput.AudioFrame>()
    var gestureCount = 0

    override fun invoke(output: GameplayOutput) {
        onOutputObserved()
        when (output) {
            is GameplayOutput.AudioFrame -> audioFrames += output
            GameplayOutput.UserGestureObserved -> gestureCount++
            else -> Unit
        }
        if (throwOnEveryOutput) error("output resource unavailable")
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
