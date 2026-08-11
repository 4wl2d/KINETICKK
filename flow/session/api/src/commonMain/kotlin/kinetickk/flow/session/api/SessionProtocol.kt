// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.api

import kinetickk.ball.content.api.CoreShape

/** Closed Interaction intent inventory of the singleton AppSession Flow. */
sealed interface SessionInteractionPulse {
    data object StartRunRequested : SessionInteractionPulse
    data object RestartRunRequested : SessionInteractionPulse
    data object ExitRunRequested : SessionInteractionPulse
    data class OpenOverlay(val destination: AppDestination) : SessionInteractionPulse
    data object CloseOverlay : SessionInteractionPulse
    data class ShortcutObserved(val shortcut: SessionShortcut) : SessionInteractionPulse
    data object ToggleMuteRequested : SessionInteractionPulse
    data class SelectCoreShapeRequested(val shape: CoreShape) : SessionInteractionPulse

    /** The first accepted request arms confirmation; the second starts the participant workflow. */
    data object RebirthRequested : SessionInteractionPulse

    data object ResetCancelled : SessionInteractionPulse
    data object ResetConfirmed : SessionInteractionPulse
    data object ResetRetryRequested : SessionInteractionPulse
}

sealed interface SessionRejection {
    data object RunIdExhausted : SessionRejection
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

/** Presentation-safe failure family; participant protocol payloads remain Session-internal. */
enum class SessionWorkflowFailureCode {
    PROFILE_COMMAND_REFUSED,
    GAMEPLAY_COMMAND_REFUSED,
    EXIT_PROGRESS_NOT_APPLIED,
    RESET_WRITE_REJECTED,
    RESET_WRITE_RESOURCE_FAILURE,
    RESET_WRITE_OUTCOME_UNKNOWN,
    RESET_NEEDS_ATTENTION,
}
