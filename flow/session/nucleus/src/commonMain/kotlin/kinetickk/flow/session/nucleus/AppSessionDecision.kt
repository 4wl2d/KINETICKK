// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayCommandBoundaryResponse
import kinetickk.ball.gameplay.api.GameplayCommandSourceToken
import kinetickk.ball.gameplay.api.GameplayEffectiveProtocolIdentity
import kinetickk.ball.gameplay.api.GameplayModuleCommandRequest
import kinetickk.ball.gameplay.api.GameplayModuleResult
import kinetickk.ball.gameplay.api.GameplayResultIssuerProvenance
import kinetickk.ball.gameplay.api.GameplayResultSourceToken
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplayTargetBoundaryProvenance
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileCommandBoundaryResponse
import kinetickk.ball.profile.api.ProfileCommandSourceToken
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileResultIssuerProvenance
import kinetickk.ball.profile.api.ProfileResultSourceToken
import kinetickk.ball.profile.api.ProfileTargetBoundaryProvenance
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionRejection
import kinetickk.foundation.collections.ImmutableList

const val MAX_SESSION_OUTPUTS_PER_DECISION: Int = 3

/** Sparse exact reads. Impl validates identity/run before constructing a trusted Nucleus input. */
data class AppSessionContext(
    val runBootstrap: RunBootstrapProjection? = null,
    val preferences: PreferencesProjection? = null,
    val rebirthProgress: RebirthProgressProjection? = null,
    val gameplayStatus: GameplayRunStatusProjection? = null,
) {
    companion object {
        val Empty: AppSessionContext = AppSessionContext()
    }
}

sealed interface AppSessionNucleusPulse {
    data class Intent(
        val intent: SessionInteractionPulse,
    ) : AppSessionNucleusPulse

    sealed interface ModuleResultPulse : AppSessionNucleusPulse

    sealed interface ControlPulse : AppSessionNucleusPulse
}

/** Trusted Profile accepted-frame result constructed only after Impl boundary validation. */
internal data class ProfileModuleResultPulse(
    val commandSource: ProfileCommandSourceToken,
    val resultSource: ProfileResultSourceToken,
    val effectiveProtocolIdentity: ProfileEffectiveProtocolIdentity,
    val result: ProfileModuleResult,
    val issuerProvenance: ProfileResultIssuerProvenance,
) : AppSessionNucleusPulse.ModuleResultPulse

/** Trusted Gameplay accepted-frame result constructed only after Impl boundary validation. */
internal data class GameplayModuleResultPulse(
    val commandSource: GameplayCommandSourceToken,
    val resultSource: GameplayResultSourceToken,
    val effectiveProtocolIdentity: GameplayEffectiveProtocolIdentity,
    val result: GameplayModuleResult,
    val issuerProvenance: GameplayResultIssuerProvenance,
) : AppSessionNucleusPulse.ModuleResultPulse

/** Source-owned carrier for a Profile refusal before target acceptance. */
internal data class ProfileCommandRejectedBeforeAcceptance(
    val commandSource: ProfileCommandSourceToken,
    val effectiveProtocolIdentity: ProfileEffectiveProtocolIdentity,
    val boundaryResponse: ProfileCommandBoundaryResponse,
    val targetBoundaryProvenance: ProfileTargetBoundaryProvenance,
) : AppSessionNucleusPulse.ControlPulse

/** Source-owned carrier for a Gameplay refusal before target acceptance. */
internal data class GameplayCommandRejectedBeforeAcceptance(
    val commandSource: GameplayCommandSourceToken,
    val effectiveProtocolIdentity: GameplayEffectiveProtocolIdentity,
    val boundaryResponse: GameplayCommandBoundaryResponse,
    val targetBoundaryProvenance: GameplayTargetBoundaryProvenance,
) : AppSessionNucleusPulse.ControlPulse

fun profileModuleResultPulse(
    commandSource: ProfileCommandSourceToken,
    resultSource: ProfileResultSourceToken,
    effectiveProtocolIdentity: ProfileEffectiveProtocolIdentity,
    result: ProfileModuleResult,
    issuerProvenance: ProfileResultIssuerProvenance,
): AppSessionNucleusPulse.ModuleResultPulse = ProfileModuleResultPulse(
    commandSource,
    resultSource,
    effectiveProtocolIdentity,
    result,
    issuerProvenance,
)

fun gameplayModuleResultPulse(
    commandSource: GameplayCommandSourceToken,
    resultSource: GameplayResultSourceToken,
    effectiveProtocolIdentity: GameplayEffectiveProtocolIdentity,
    result: GameplayModuleResult,
    issuerProvenance: GameplayResultIssuerProvenance,
): AppSessionNucleusPulse.ModuleResultPulse = GameplayModuleResultPulse(
    commandSource,
    resultSource,
    effectiveProtocolIdentity,
    result,
    issuerProvenance,
)

fun profileCommandRejectedBeforeAcceptance(
    commandSource: ProfileCommandSourceToken,
    effectiveProtocolIdentity: ProfileEffectiveProtocolIdentity,
    boundaryResponse: ProfileCommandBoundaryResponse,
    targetBoundaryProvenance: ProfileTargetBoundaryProvenance,
): AppSessionNucleusPulse.ControlPulse = ProfileCommandRejectedBeforeAcceptance(
    commandSource,
    effectiveProtocolIdentity,
    boundaryResponse,
    targetBoundaryProvenance,
)

fun gameplayCommandRejectedBeforeAcceptance(
    commandSource: GameplayCommandSourceToken,
    effectiveProtocolIdentity: GameplayEffectiveProtocolIdentity,
    boundaryResponse: GameplayCommandBoundaryResponse,
    targetBoundaryProvenance: GameplayTargetBoundaryProvenance,
): AppSessionNucleusPulse.ControlPulse = GameplayCommandRejectedBeforeAcceptance(
    commandSource,
    effectiveProtocolIdentity,
    boundaryResponse,
    targetBoundaryProvenance,
)

sealed interface AppSessionDecision {
    data class Accepted(val frame: AppSessionAcceptedFrame) : AppSessionDecision
    data class Rejected(val reason: SessionRejection) : AppSessionDecision
}

/** Canonical accepted frame; shell UI is derived separately through the Session Query surface. */
public data class AppSessionAcceptedFrame(
    val nextState: AppSessionState,
    val outputs: ImmutableList<AppSessionOutput>,
) {
    init {
        require(outputs.size <= MAX_SESSION_OUTPUTS_PER_DECISION) {
            "Session semantic output bound exceeded: ${outputs.size}"
        }
        outputs.zipWithNext().forEach { (before, after) ->
            require(before.orderRank <= after.orderRank) {
                "Session outputs must retain ensure -> participant -> feedback order"
            }
        }
        require(outputs.count(AppSessionOutput::isParticipantCommand) <= 1) {
            "A Session decision may issue at most one participant command"
        }
        outputs.forEachIndexed { index, output ->
            when (output) {
                is AppSessionOutput.SendProfileCommand -> require(
                    output.request.sourceOrdinal == index &&
                        output.request.semanticHandle.sourceOrdinal == index &&
                        output.request.semanticHandle.sourceRevision == nextState.revision.value,
                ) { "Profile command identity must equal its accepted Session output position" }
                is AppSessionOutput.SendGameplayCommand -> require(
                    output.request.sourceOrdinal == index &&
                        output.request.semanticHandle.sourceOrdinal == index &&
                        output.request.semanticHandle.sourceRevision == nextState.revision.value,
                ) { "Gameplay command identity must equal its accepted Session output position" }
                else -> Unit
            }
        }
        val ensures = outputs.filterIsInstance<AppSessionOutput.EnsureGameplayRun>()
        require(ensures.size <= 1) { "A Session decision may ensure at most one GameplayRun" }
        ensures.singleOrNull()?.let { ensure ->
            val gameplay = outputs.filterIsInstance<AppSessionOutput.SendGameplayCommand>().singleOrNull()
            require(gameplay != null && gameplay.request.targetInstance.runId == ensure.runId) {
                "Ensured GameplayRun must be the target of the same accepted frame"
            }
        }
    }
}

sealed interface AppSessionOutput {
    data class EnsureGameplayRun(val runId: RunId) : AppSessionOutput
    data class SendProfileCommand(val request: ProfileModuleCommandRequest) : AppSessionOutput
    data class SendGameplayCommand(val request: GameplayModuleCommandRequest) : AppSessionOutput
    data class SynchronizeAudioPreferences(val preferences: PlayerPreferences) : AppSessionOutput
    data object PlayMuteFeedback : AppSessionOutput
    data object PlayRebirthAcceptedFeedback : AppSessionOutput
}

private val AppSessionOutput.orderRank: Int
    get() = when (this) {
        is AppSessionOutput.EnsureGameplayRun -> 0
        is AppSessionOutput.SendProfileCommand,
        is AppSessionOutput.SendGameplayCommand,
        -> 1
        is AppSessionOutput.SynchronizeAudioPreferences,
        AppSessionOutput.PlayMuteFeedback,
        AppSessionOutput.PlayRebirthAcceptedFeedback,
        -> 2
    }

private fun AppSessionOutput.isParticipantCommand(): Boolean =
    this is AppSessionOutput.SendProfileCommand || this is AppSessionOutput.SendGameplayCommand
