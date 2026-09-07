// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.api

enum class GameplayRunPhase {
    CREATED,
    RUNNING,
    PAUSED,
    CHOICE,
    GAME_OVER,
    VICTORY,
    EXITED,
}

object GameplayInteractionLimits {
    const val MIN_FRAME_DELTA_SECONDS: Float = 0f
    const val MAX_FRAME_DELTA_SECONDS: Float = 1f
    const val MIN_VIEWPORT_DIMENSION_PX: Float = 1f
    const val MAX_VIEWPORT_DIMENSION_PX: Float = 32_768f
    const val MIN_DENSITY: Float = 0.5f
    const val MAX_DENSITY: Float = 8f
    const val MIN_CHOICE_INDEX: Int = 0
    const val MAX_CHOICE_INDEX: Int = 3
}

/** Closed, already-validated Interaction intent inventory. */
sealed interface GameplayInteractionPulse {
    class FrameElapsed private constructor(
        val realDeltaSeconds: Float,
    ) : GameplayInteractionPulse {
        companion object {
            fun fromValidated(realDeltaSeconds: Float): FrameElapsed {
                require(
                    realDeltaSeconds.isFinite() &&
                        realDeltaSeconds in
                        GameplayInteractionLimits.MIN_FRAME_DELTA_SECONDS..
                        GameplayInteractionLimits.MAX_FRAME_DELTA_SECONDS,
                )
                return FrameElapsed(realDeltaSeconds)
            }
        }
    }

    class ViewportChanged private constructor(
        val width: Float,
        val height: Float,
        val density: Float,
    ) : GameplayInteractionPulse {
        companion object {
            fun fromValidated(width: Float, height: Float, density: Float): ViewportChanged {
                require(
                    width.isFinite() &&
                        width in
                        GameplayInteractionLimits.MIN_VIEWPORT_DIMENSION_PX..
                        GameplayInteractionLimits.MAX_VIEWPORT_DIMENSION_PX,
                )
                require(
                    height.isFinite() &&
                        height in
                        GameplayInteractionLimits.MIN_VIEWPORT_DIMENSION_PX..
                        GameplayInteractionLimits.MAX_VIEWPORT_DIMENSION_PX,
                )
                require(
                    density.isFinite() &&
                        density in GameplayInteractionLimits.MIN_DENSITY..
                        GameplayInteractionLimits.MAX_DENSITY,
                )
                return ViewportChanged(width, height, density)
            }
        }
    }

    class PointerMoved private constructor(
        val x: Float,
        val y: Float,
        val active: Boolean = true,
    ) : GameplayInteractionPulse {
        companion object {
            fun fromValidated(x: Float, y: Float, active: Boolean = true): PointerMoved {
                require(x.isFinite() && y.isFinite())
                return PointerMoved(x, y, active)
            }
        }
    }

    data class BrakeChanged(val source: BrakeSource, val active: Boolean) : GameplayInteractionPulse
    data object DashRequested : GameplayInteractionPulse
    data object PauseToggled : GameplayInteractionPulse
    class ChoiceSelected private constructor(
        val index: Int,
    ) : GameplayInteractionPulse {
        companion object {
            fun fromValidated(index: Int): ChoiceSelected {
                require(
                    index in GameplayInteractionLimits.MIN_CHOICE_INDEX..
                        GameplayInteractionLimits.MAX_CHOICE_INDEX,
                )
                return ChoiceSelected(index)
            }
        }
    }
    data object ChoicesRerolled : GameplayInteractionPulse
    data object UserGestureObserved : GameplayInteractionPulse
}

enum class BrakeSource { KEYBOARD, SECONDARY_POINTER, TOUCH_CONTROL }

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
    data object NotStarted : GameplayRejection
    data object AlreadyStarted : GameplayRejection
    data object RunExited : GameplayRejection
    data object PauseUnavailable : GameplayRejection
    data object ProgressPending : GameplayRejection
    data object ProfileBootstrapUnavailable : GameplayRejection
    data object InvalidPreferencesProjection : GameplayRejection

    data class InvalidStartConfiguration(
        val reason: GameplayConfigurationRejection,
    ) : GameplayRejection

    data class PointerOutsideViewport(
        val axis: GameplayPointerAxis,
    ) : GameplayRejection
}

enum class GameplayPointerAxis { HORIZONTAL, VERTICAL }

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
