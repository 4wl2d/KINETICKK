// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import kinetickk.ball.gameplay.api.GameplayInteractionLimits
import kinetickk.ball.gameplay.api.GameplayInteractionPulse

/** Public API factories and Interaction diagnostics share one fixed representation policy. */
object InteractionIngressLimits {
    const val MIN_FRAME_DELTA_SECONDS = GameplayInteractionLimits.MIN_FRAME_DELTA_SECONDS
    const val MAX_FRAME_DELTA_SECONDS = GameplayInteractionLimits.MAX_FRAME_DELTA_SECONDS
    const val MIN_VIEWPORT_DIMENSION_PX = GameplayInteractionLimits.MIN_VIEWPORT_DIMENSION_PX
    const val MAX_VIEWPORT_DIMENSION_PX = GameplayInteractionLimits.MAX_VIEWPORT_DIMENSION_PX
    const val MIN_DENSITY = GameplayInteractionLimits.MIN_DENSITY
    const val MAX_DENSITY = GameplayInteractionLimits.MAX_DENSITY
    const val MIN_CHOICE_INDEX = GameplayInteractionLimits.MIN_CHOICE_INDEX
    const val MAX_CHOICE_INDEX = GameplayInteractionLimits.MAX_CHOICE_INDEX
}

enum class InteractionInputField {
    FRAME_DELTA_SECONDS,
    VIEWPORT_WIDTH_PX,
    VIEWPORT_HEIGHT_PX,
    DENSITY,
    POINTER_X_PX,
    POINTER_Y_PX,
    CHOICE_INDEX,
}

sealed interface ValidationFailure {
    val code: String

    data class NonFinite(
        val field: InteractionInputField,
    ) : ValidationFailure {
        override val code: String = "non-finite-${field.name.lowercase()}"
    }

    data class OutOfRange(
        val field: InteractionInputField,
        val acceptedMinimum: Float,
        val acceptedMaximum: Float,
    ) : ValidationFailure {
        override val code: String = "out-of-range-${field.name.lowercase()}"
    }

    data class IndexOutOfRange(
        val field: InteractionInputField,
        val acceptedMinimum: Int,
        val acceptedMaximum: Int,
    ) : ValidationFailure {
        override val code: String = "out-of-range-${field.name.lowercase()}"
    }
}

sealed interface InteractionValidationResult<out Intent : GameplayInteractionPulse> {
    data class Valid<out Intent : GameplayInteractionPulse>(
        val intent: Intent,
    ) : InteractionValidationResult<Intent>

    data class Invalid(
        val failure: ValidationFailure,
    ) : InteractionValidationResult<Nothing>
}

/** Raw platform numbers are quarantined here before a trusted Intent can be constructed. */
class GameInteractionValidator {
    fun frameElapsed(
        rawDeltaSeconds: Float,
    ): InteractionValidationResult<GameplayInteractionPulse.FrameElapsed> =
        validateBounded(
            value = rawDeltaSeconds,
            field = InteractionInputField.FRAME_DELTA_SECONDS,
            minimum = InteractionIngressLimits.MIN_FRAME_DELTA_SECONDS,
            maximum = InteractionIngressLimits.MAX_FRAME_DELTA_SECONDS,
        ) {
            GameplayInteractionPulse.FrameElapsed.fromValidated(rawDeltaSeconds)
        }

    fun viewportChanged(
        rawWidthPx: Float,
        rawHeightPx: Float,
        rawDensity: Float,
    ): InteractionValidationResult<GameplayInteractionPulse.ViewportChanged> {
        val failure = boundedFailure(
            value = rawWidthPx,
            field = InteractionInputField.VIEWPORT_WIDTH_PX,
            minimum = InteractionIngressLimits.MIN_VIEWPORT_DIMENSION_PX,
            maximum = InteractionIngressLimits.MAX_VIEWPORT_DIMENSION_PX,
        ) ?: boundedFailure(
            value = rawHeightPx,
            field = InteractionInputField.VIEWPORT_HEIGHT_PX,
            minimum = InteractionIngressLimits.MIN_VIEWPORT_DIMENSION_PX,
            maximum = InteractionIngressLimits.MAX_VIEWPORT_DIMENSION_PX,
        ) ?: boundedFailure(
            value = rawDensity,
            field = InteractionInputField.DENSITY,
            minimum = InteractionIngressLimits.MIN_DENSITY,
            maximum = InteractionIngressLimits.MAX_DENSITY,
        )
        return if (failure == null) {
            InteractionValidationResult.Valid(
                GameplayInteractionPulse.ViewportChanged.fromValidated(
                    rawWidthPx,
                    rawHeightPx,
                    rawDensity,
                ),
            )
        } else {
            InteractionValidationResult.Invalid(failure)
        }
    }

    /** Only finite representation is fixed; viewport membership remains Nucleus business policy. */
    fun pointerMoved(
        rawXpx: Float,
        rawYpx: Float,
        active: Boolean = true,
    ): InteractionValidationResult<GameplayInteractionPulse.PointerMoved> {
        val failure = finiteFailure(rawXpx, InteractionInputField.POINTER_X_PX)
            ?: finiteFailure(rawYpx, InteractionInputField.POINTER_Y_PX)
        return if (failure == null) {
            InteractionValidationResult.Valid(
                GameplayInteractionPulse.PointerMoved.fromValidated(rawXpx, rawYpx, active),
            )
        } else {
            InteractionValidationResult.Invalid(failure)
        }
    }

    fun choiceSelected(
        rawIndex: Int,
    ): InteractionValidationResult<GameplayInteractionPulse.ChoiceSelected> =
        if (rawIndex in InteractionIngressLimits.MIN_CHOICE_INDEX..
            InteractionIngressLimits.MAX_CHOICE_INDEX
        ) {
            InteractionValidationResult.Valid(
                GameplayInteractionPulse.ChoiceSelected.fromValidated(rawIndex),
            )
        } else {
            InteractionValidationResult.Invalid(
                ValidationFailure.IndexOutOfRange(
                    field = InteractionInputField.CHOICE_INDEX,
                    acceptedMinimum = InteractionIngressLimits.MIN_CHOICE_INDEX,
                    acceptedMaximum = InteractionIngressLimits.MAX_CHOICE_INDEX,
                ),
            )
        }

    private inline fun <Intent : GameplayInteractionPulse> validateBounded(
        value: Float,
        field: InteractionInputField,
        minimum: Float,
        maximum: Float,
        createIntent: () -> Intent,
    ): InteractionValidationResult<Intent> {
        val failure = boundedFailure(value, field, minimum, maximum)
        return if (failure == null) {
            InteractionValidationResult.Valid(createIntent())
        } else {
            InteractionValidationResult.Invalid(failure)
        }
    }

    private fun finiteFailure(
        value: Float,
        field: InteractionInputField,
    ): ValidationFailure? = if (value.isFinite()) null else ValidationFailure.NonFinite(field)

    private fun boundedFailure(
        value: Float,
        field: InteractionInputField,
        minimum: Float,
        maximum: Float,
    ): ValidationFailure? = when {
        !value.isFinite() -> ValidationFailure.NonFinite(field)
        value < minimum || value > maximum -> ValidationFailure.OutOfRange(
            field = field,
            acceptedMinimum = minimum,
            acceptedMaximum = maximum,
        )
        else -> null
    }
}
