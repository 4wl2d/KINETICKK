// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.api

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileCommandRef
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision

data class RunConfiguration(
    val content: GameplayContentSnapshot,
    val profile: GameplayProfileSnapshot,
    val seed: Int = 731_991,
)

enum class GameplayRunPhase {
    CREATED,
    RUNNING,
    PAUSED,
    CHOICE,
    GAME_OVER,
    VICTORY,
    EXITED,
}

sealed interface GameplayPulse

sealed interface GameplayInteractionPulse : GameplayPulse {
    data class FrameElapsed(val realDeltaSeconds: Float) : GameplayInteractionPulse
    data class ViewportChanged(val width: Float, val height: Float, val density: Float) : GameplayInteractionPulse
    data class PointerMoved(val x: Float, val y: Float, val active: Boolean = true) : GameplayInteractionPulse
    data class BrakeChanged(val source: BrakeSource, val active: Boolean) : GameplayInteractionPulse
    data object DashRequested : GameplayInteractionPulse
    data object PauseToggled : GameplayInteractionPulse
    data object PauseForOverlay : GameplayInteractionPulse
    data object ExitRunRequested : GameplayInteractionPulse
    data class PreferencesChanged(val preferences: PlayerPreferences) : GameplayInteractionPulse
    data class ChoiceSelected(val index: Int) : GameplayInteractionPulse
    data object ChoicesRerolled : GameplayInteractionPulse
    data object UserGestureObserved : GameplayInteractionPulse
}

enum class BrakeSource { KEYBOARD, SECONDARY_POINTER, TOUCH_CONTROL }

sealed interface GameplaySessionPulse : GameplayPulse {
    data class StartRun(val configuration: RunConfiguration) : GameplaySessionPulse
    data object PauseForOverlay : GameplaySessionPulse
    data class ApplyPreferences(val preferences: PlayerPreferences) : GameplaySessionPulse
    data object ExitRun : GameplaySessionPulse
}

/** Control returns accepted only through the statically reserved completion path. */
sealed interface GameplayControlPulse : GameplayPulse {
    data class ProfileCommandCompleted(
        val result: ProfileCommandResult.Accepted,
    ) : GameplayControlPulse

    data class ProfileCommandRejectedBeforeAcceptance(
        val commandRef: ProfileCommandRef,
        val rejection: ProfileAcceptance.Rejected,
    ) : GameplayControlPulse
}

data class GameplayCommand(
    val ref: GameplayCommandRef,
    val pulse: GameplaySessionPulse,
)

sealed interface GameplayCommandOutcome {
    data object RunStarted : GameplayCommandOutcome
    data object OverlayPaused : GameplayCommandOutcome
    data class PreferencesApplied(val preferences: PlayerPreferences) : GameplayCommandOutcome
    data class RunExited(val profile: GameplayExitProfileOutcome) : GameplayCommandOutcome
}

sealed interface GameplayExitProfileOutcome {
    data object NoProgress : GameplayExitProfileOutcome
    data object ProgressApplied : GameplayExitProfileOutcome
    data class ProgressRejected(
        val observedRevision: ProfileRevision,
        val reason: ProfileRejection,
    ) : GameplayExitProfileOutcome
}

sealed interface GameplayCommandResult {
    val commandRef: GameplayCommandRef

    data class Accepted(
        override val commandRef: GameplayCommandRef,
        val targetRevision: GameplayRevision,
        val outcome: GameplayCommandOutcome,
    ) : GameplayCommandResult
}

enum class GameplayInputField {
    FRAME_DELTA_SECONDS,
    VIEWPORT_WIDTH,
    VIEWPORT_HEIGHT,
    DENSITY,
    POINTER_X,
    POINTER_Y,
    CHOICE_INDEX,
}

enum class GameplayInputReason {
    NON_FINITE,
    BELOW_MINIMUM,
    ABOVE_MAXIMUM,
}

enum class GameplayCommandRefRejection {
    WRONG_TARGET,
    WRONG_SOURCE_KIND,
    ADMISSION_MISMATCH,
}

enum class GameplayProfileResultRejection {
    NO_COMMAND_PENDING,
    COMMAND_REF_MISMATCH,
    TARGET_INSTANCE_MISMATCH,
    OUTCOME_MISMATCH,
}

enum class GameplayConfigurationRejection {
    INVALID_PREFERENCES,
    STARTING_WEAPON_MISSING,
    STARTING_WEAPON_LOCKED,
    META_RANK_COUNT_MISMATCH,
    META_RANK_OUT_OF_RANGE,
    UNKNOWN_DISCOVERED_ITEM,
    REBIRTH_LEVEL_OUT_OF_RANGE,
    NEGATIVE_MATTER,
    LIFETIME_MATTER_BELOW_CURRENT,
}

sealed interface GameplayRejection {
    data object RevisionExhausted : GameplayRejection
    data object NotStarted : GameplayRejection
    data object AlreadyStarted : GameplayRejection
    data object RunExited : GameplayRejection
    data object PauseUnavailable : GameplayRejection
    data object ProfileCommandPending : GameplayRejection

    data class InvalidInput(
        val field: GameplayInputField,
        val reason: GameplayInputReason,
    ) : GameplayRejection

    data class InvalidConfiguration(
        val reason: GameplayConfigurationRejection,
    ) : GameplayRejection

    data class InvalidCommandRef(
        val reason: GameplayCommandRefRejection,
    ) : GameplayRejection

    data class UnexpectedProfileResult(
        val reason: GameplayProfileResultRejection,
    ) : GameplayRejection
}

sealed interface GameplayAcceptance {
    val instanceId: GameplayInstanceId

    data class Accepted(
        override val instanceId: GameplayInstanceId,
        val revision: GameplayRevision,
    ) : GameplayAcceptance

    data class Rejected(
        override val instanceId: GameplayInstanceId,
        val observedRevision: GameplayRevision,
        val reason: GameplayRejection,
    ) : GameplayAcceptance
}
