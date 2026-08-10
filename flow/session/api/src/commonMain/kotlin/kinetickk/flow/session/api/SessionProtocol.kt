// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayCommandRef
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayExitProfileOutcome
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileCommandRef
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileResourceFailure
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileV4Rejection

data class SessionConfiguration(
    val gameplayContent: GameplayContentSnapshot,
)

sealed interface SessionPulse

sealed interface SessionInteractionPulse : SessionPulse {
    data object StartRunRequested : SessionInteractionPulse
    data object RestartRunRequested : SessionInteractionPulse
    data object ExitRunRequested : SessionInteractionPulse
    data class OpenOverlay(val destination: AppDestination) : SessionInteractionPulse
    data object CloseOverlay : SessionInteractionPulse
    data class ShortcutObserved(val shortcut: SessionShortcut) : SessionInteractionPulse
    data object ToggleMuteRequested : SessionInteractionPulse
    data class SelectCoreShapeRequested(val shape: CoreShape) : SessionInteractionPulse

    /** The first accepted request arms confirmation; the second submits the Profile command. */
    data object RebirthRequested : SessionInteractionPulse

    data object ResetCancelled : SessionInteractionPulse
    data object ResetConfirmed : SessionInteractionPulse
    data object ResetRetryRequested : SessionInteractionPulse
}

/** Impl-only participant returns retained by the bounded Session completion path. */
sealed interface SessionControlPulse : SessionPulse {
    data class ProfileCommandCompleted(
        val result: ProfileCommandResult.Accepted,
    ) : SessionControlPulse

    data class ProfileCommandRejectedBeforeAcceptance(
        val commandRef: ProfileCommandRef,
        val rejection: ProfileAcceptance.Rejected,
    ) : SessionControlPulse

    data class GameplayCommandCompleted(
        val result: GameplayCommandResult.Accepted,
    ) : SessionControlPulse

    data class GameplayCommandRejectedBeforeAcceptance(
        val commandRef: GameplayCommandRef,
        val rejection: GameplayAcceptance.Rejected,
    ) : SessionControlPulse
}

enum class SessionContextField {
    RUN_BOOTSTRAP,
    PREFERENCES,
    REBIRTH_PROGRESS,
    PERSISTENCE_STATUS,
    GAMEPLAY_STATUS,
}

enum class SessionContextReason {
    MISSING,
    WRONG_INSTANCE,
    WRONG_RUN,
}

enum class SessionParticipantResultRejection {
    NO_COMMAND_PENDING,
    PARTICIPANT_MISMATCH,
    COMMAND_REF_MISMATCH,
    TARGET_INSTANCE_MISMATCH,
    OUTCOME_MISMATCH,
}

sealed interface SessionRejection {
    data object RevisionExhausted : SessionRejection
    data object RunIdExhausted : SessionRejection
    data object ProfileCommandOrdinalExhausted : SessionRejection
    data object GameplayCommandOrdinalExhausted : SessionRejection
    data object ParticipantCommandPending : SessionRejection
    data object ResetBlocksInput : SessionRejection
    data object StartUnavailable : SessionRejection
    data object RestartUnavailable : SessionRejection
    data object ExitUnavailable : SessionRejection
    data object CloseUnavailable : SessionRejection
    data object ShortcutUnavailable : SessionRejection
    data object CoreShapeSelectionUnavailable : SessionRejection
    data object RebirthUnavailable : SessionRejection
    data object ResetActionUnavailable : SessionRejection

    data class OverlayUnavailable(
        val destination: AppDestination,
    ) : SessionRejection

    data class InvalidContext(
        val field: SessionContextField,
        val reason: SessionContextReason,
    ) : SessionRejection

    data class UnexpectedParticipantResult(
        val reason: SessionParticipantResultRejection,
    ) : SessionRejection
}

sealed interface SessionAcceptance {
    val instanceId: AppSessionInstanceId

    data class Accepted(
        override val instanceId: AppSessionInstanceId,
        val revision: SessionRevision,
    ) : SessionAcceptance

    data class Rejected(
        override val instanceId: AppSessionInstanceId,
        val observedRevision: SessionRevision,
        val reason: SessionRejection,
    ) : SessionAcceptance
}

sealed interface SessionWorkflowFailure {
    data class ProfileCommandRejected(
        val commandRef: ProfileCommandRef,
        val observedRevision: ProfileRevision,
        val reason: ProfileRejection,
    ) : SessionWorkflowFailure

    data class GameplayCommandRejected(
        val commandRef: GameplayCommandRef,
        val rejection: GameplayAcceptance.Rejected,
    ) : SessionWorkflowFailure

    data class ExitProgressRejected(
        val outcome: GameplayExitProfileOutcome.ProgressRejected,
    ) : SessionWorkflowFailure

    data class ResetWriteRejected(
        val reason: ProfileV4Rejection,
    ) : SessionWorkflowFailure

    data class ResetWriteOutcomeUnknown(
        val reason: ProfileResourceFailure,
    ) : SessionWorkflowFailure

    data class ResetNeedsAttention(
        val status: ProfileResetStatus.NeedsAttention,
    ) : SessionWorkflowFailure
}

/** Exhaustive Session-owned Profile result inventory used by workflow correlation. */
fun ProfileCommandOutcome.isSessionOwnedOutcome(): Boolean = when (this) {
    is ProfileCommandOutcome.CoreShapeSelected,
    is ProfileCommandOutcome.PreferencesChanged,
    is ProfileCommandOutcome.RebirthAdvanced,
    ProfileCommandOutcome.ResetCompleted,
    is ProfileCommandOutcome.ResetWriteRejected,
    is ProfileCommandOutcome.ResetWriteOutcomeUnknown,
    is ProfileCommandOutcome.ResetNeedsAttention,
    -> true
    ProfileCommandOutcome.GameplayProgressApplied -> false
}
