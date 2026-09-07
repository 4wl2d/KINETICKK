// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.api.GameplayPresentationPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunPort
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.GameplayContent
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileProgress
import kinetickk.foundation.design.SpaceBlack
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kinetickk.resource.audio.api.AudioService

class DefaultGameplayFeature(
    private val gameplayContent: GameplayContentSnapshot,
    private val profilePort: ProfileReadPort,
    private val profileProgress: ProfileProgress,
    audioService: AudioService,
    private val diagnostics: CrashDiagnostics = CrashDiagnostics.None,
) : GameplayCompositionComponent {
    private val audioExecutor = ResourceGameplayAudioExecutor(audioService)
    private var componentValue by mutableStateOf<GameComponent?>(null)

    override fun createRun(
        runId: RunId,
    ): GameplayRunPort {
        ensureReplacementAllowed(runId)
        diagnostics.context("gameplay.start") { "runId=$runId seed=$DEFAULT_GAMEPLAY_SEED" }
        diagnostics.event("gameplay.start", "runId=$runId seed=$DEFAULT_GAMEPLAY_SEED")
        return GameComponent.create(
            runId = runId,
            content = gameplayContent,
            profilePort = profilePort,
            audioExecutor = audioExecutor,
            profileProgress = profileProgress,
            seed = DEFAULT_GAMEPLAY_SEED,
        ).also { componentValue = it }
    }

    override fun activeRun(): GameplayRunPort? = componentValue

    override fun activePresentation(): GameplayPresentationPort? = componentValue

    @Composable
    override fun Content(
        inputEnabled: Boolean,
        onOutput: (GameplayInteractionOutput) -> Unit,
    ) {
        val component = componentValue
        if (component == null) {
            Canvas(Modifier.fillMaxSize()) { drawRect(SpaceBlack) }
        } else {
            GameplayContent(
                component = component,
                inputEnabled = inputEnabled,
                onOutput = onOutput,
            )
        }
    }

    private fun ensureReplacementAllowed(runId: RunId) {
        val active = componentValue ?: return
        val status = active.query(GameplayQuery.GetRunStatus)
        check(!status.progressPending) {
            "Cannot replace a GameplayRun with a pending Profile command"
        }
        when (status.phase) {
            GameplayRunPhase.CREATED,
            GameplayRunPhase.RUNNING,
            GameplayRunPhase.PAUSED,
            GameplayRunPhase.CHOICE,
            -> error("Cannot replace a non-terminal GameplayRun")

            GameplayRunPhase.GAME_OVER,
            GameplayRunPhase.VICTORY,
            GameplayRunPhase.EXITED,
            -> Unit
        }
        require(runId.value > active.instanceId.runId.value) {
            "Gameplay RunId must increase monotonically"
        }
    }
}

/** Frozen target-owned construction policy; Session never supplies the simulation seed. */
internal const val DEFAULT_GAMEPLAY_SEED: Int = 731_991
