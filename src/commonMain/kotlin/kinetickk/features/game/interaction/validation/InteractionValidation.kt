// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.interaction.validation

import kinetickk.features.game.nucleus.protocol.GameIntent

/** Finite limits enforced before raw platform numbers can become a [GameIntent]. */
object InteractionIngressLimits {
    const val MIN_FRAME_DELTA_SECONDS = 0f
    const val MAX_FRAME_DELTA_SECONDS = 1f

    const val MIN_VIEWPORT_DIMENSION_PX = 1f
    const val MAX_VIEWPORT_DIMENSION_PX = 32_768f

    const val MIN_DENSITY = 0.5f
    const val MAX_DENSITY = 8f
}

enum class InteractionInputField {
    FRAME_DELTA_SECONDS,
    VIEWPORT_WIDTH_PX,
    VIEWPORT_HEIGHT_PX,
    DENSITY,
    POINTER_X_PX,
    POINTER_Y_PX,
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

    data object MissingValidatedViewport : ValidationFailure {
        override val code: String = "missing-validated-viewport"
    }
}

sealed interface InteractionValidationResult<out Intent : GameIntent> {
    data class Valid<out Intent : GameIntent>(
        val intent: Intent,
    ) : InteractionValidationResult<Intent>

    data class Invalid(
        val failure: ValidationFailure,
    ) : InteractionValidationResult<Nothing>
}

/**
 * Stateful Interaction-side quarantine for the raw numeric ingress owned by the Compose host.
 *
 * A pointer is admitted only after a viewport has been validated. Pointer coordinates use a
 * strict inclusive `[0, dimension]` policy: they are never silently clamped. An invalid viewport
 * observation does not replace the last accepted viewport.
 */
class GameInteractionValidator {
    private var validatedViewport: ValidatedViewport? = null

    fun frameElapsed(
        rawDeltaSeconds: Float,
    ): InteractionValidationResult<GameIntent.FrameElapsed> =
        validateBounded(
            value = rawDeltaSeconds,
            field = InteractionInputField.FRAME_DELTA_SECONDS,
            minimum = InteractionIngressLimits.MIN_FRAME_DELTA_SECONDS,
            maximum = InteractionIngressLimits.MAX_FRAME_DELTA_SECONDS,
        ) {
            GameIntent.FrameElapsed(realDeltaSeconds = rawDeltaSeconds)
        }

    fun viewportChanged(
        rawWidthPx: Float,
        rawHeightPx: Float,
        rawDensity: Float,
    ): InteractionValidationResult<GameIntent.ViewportChanged> {
        val failure =
            boundedFailure(
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

        if (failure != null) return InteractionValidationResult.Invalid(failure)

        val intent = GameIntent.ViewportChanged(
            width = rawWidthPx,
            height = rawHeightPx,
            density = rawDensity,
        )
        validatedViewport = ValidatedViewport(width = rawWidthPx, height = rawHeightPx)
        return InteractionValidationResult.Valid(intent)
    }

    fun pointerMoved(
        rawXpx: Float,
        rawYpx: Float,
        active: Boolean = true,
    ): InteractionValidationResult<GameIntent.PointerMoved> =
        validatePointer(rawXpx, rawYpx) { validatedX, validatedY ->
            GameIntent.PointerMoved(x = validatedX, y = validatedY, active = active)
        }

    fun pointerPressed(
        rawXpx: Float,
        rawYpx: Float,
    ): InteractionValidationResult<GameIntent.PointerPressed> =
        validatePointer(rawXpx, rawYpx) { validatedX, validatedY ->
            GameIntent.PointerPressed(x = validatedX, y = validatedY)
        }

    private fun <Intent : GameIntent> validatePointer(
        rawXpx: Float,
        rawYpx: Float,
        createIntent: (Float, Float) -> Intent,
    ): InteractionValidationResult<Intent> {
        val viewport = validatedViewport
            ?: return InteractionValidationResult.Invalid(
                ValidationFailure.MissingValidatedViewport,
            )
        val failure =
            boundedFailure(
                value = rawXpx,
                field = InteractionInputField.POINTER_X_PX,
                minimum = 0f,
                maximum = viewport.width,
            ) ?: boundedFailure(
                value = rawYpx,
                field = InteractionInputField.POINTER_Y_PX,
                minimum = 0f,
                maximum = viewport.height,
            )
        return if (failure == null) {
            InteractionValidationResult.Valid(createIntent(rawXpx, rawYpx))
        } else {
            InteractionValidationResult.Invalid(failure)
        }
    }

    private inline fun <Intent : GameIntent> validateBounded(
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

    private data class ValidatedViewport(
        val width: Float,
        val height: Float,
    )
}
