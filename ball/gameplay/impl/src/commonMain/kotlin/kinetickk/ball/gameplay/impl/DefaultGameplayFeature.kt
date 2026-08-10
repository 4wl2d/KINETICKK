// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.GameplayContent
import kinetickk.ball.gameplay.interaction.GameplayFeature
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.foundation.design.SpaceBlack
import kinetickk.resource.audio.api.AudioService

class DefaultGameplayFeature(
    private val profilePort: ProfilePort,
    audioService: AudioService,
) : GameplayFeature {
    private val audioExecutor = ResourceGameplayAudioExecutor(audioService)
    private var componentValue by mutableStateOf<GameComponent?>(null)

    override fun createRun(
        runId: RunId,
        commandResultSink: (GameplayCommandResult.Accepted) -> Unit,
    ): GameplayPort {
        ensureReplacementAllowed(runId)
        return GameComponent.create(
            runId = runId,
            profilePort = profilePort,
            audioExecutor = audioExecutor,
            commandResultSink = commandResultSink,
        ).also { componentValue = it }
    }

    override fun activeRun(): GameplayPort? = componentValue

    override fun receiveProfileCommandResult(result: ProfileCommandResult.Accepted) {
        checkNotNull(componentValue) {
            "Cannot deliver a Profile command result before creating a GameplayRun"
        }.receiveProfileCommandResult(result)
    }

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
        check(!status.profileCommandPending) {
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
