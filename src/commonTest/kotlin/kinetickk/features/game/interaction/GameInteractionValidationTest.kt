// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.interaction

import kinetickk.features.game.interaction.validation.GameInteractionValidator
import kinetickk.features.game.interaction.validation.InteractionIngressLimits
import kinetickk.features.game.interaction.validation.InteractionInputField
import kinetickk.features.game.interaction.validation.InteractionValidationResult
import kinetickk.features.game.interaction.validation.ValidationFailure
import kinetickk.features.game.nucleus.protocol.GameIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameInteractionValidationTest {
    @Test
    fun frameDeltaAcceptsExactBoundsAndRejectsTheNextRepresentableValues() {
        val validator = GameInteractionValidator()

        assertEquals(
            InteractionIngressLimits.MIN_FRAME_DELTA_SECONDS,
            valid<GameIntent.FrameElapsed>(
                validator.frameElapsed(InteractionIngressLimits.MIN_FRAME_DELTA_SECONDS),
            ).realDeltaSeconds,
        )
        assertEquals(
            InteractionIngressLimits.MAX_FRAME_DELTA_SECONDS,
            valid<GameIntent.FrameElapsed>(
                validator.frameElapsed(InteractionIngressLimits.MAX_FRAME_DELTA_SECONDS),
            ).realDeltaSeconds,
        )

        val belowMinimum = -Float.MIN_VALUE
        val aboveMaximum = Float.fromBits(
            InteractionIngressLimits.MAX_FRAME_DELTA_SECONDS.toBits() + 1,
        )
        assertOutOfRange(
            validator.frameElapsed(belowMinimum),
            InteractionInputField.FRAME_DELTA_SECONDS,
        )
        assertOutOfRange(
            validator.frameElapsed(aboveMaximum),
            InteractionInputField.FRAME_DELTA_SECONDS,
        )
        assertNonFinite(
            validator.frameElapsed(Float.NaN),
            InteractionInputField.FRAME_DELTA_SECONDS,
        )
    }

    @Test
    fun viewportAcceptsNAndRejectsNPlusOneForEveryBoundedField() {
        val validator = GameInteractionValidator()
        val maximum = InteractionIngressLimits.MAX_VIEWPORT_DIMENSION_PX
        val accepted = valid<GameIntent.ViewportChanged>(
            validator.viewportChanged(
                rawWidthPx = maximum,
                rawHeightPx = maximum,
                rawDensity = InteractionIngressLimits.MAX_DENSITY,
            ),
        )
        assertEquals(maximum, accepted.width)
        assertEquals(maximum, accepted.height)
        assertEquals(InteractionIngressLimits.MAX_DENSITY, accepted.density)

        assertOutOfRange(
            validator.viewportChanged(
                rawWidthPx = maximum + 1f,
                rawHeightPx = maximum,
                rawDensity = 1f,
            ),
            InteractionInputField.VIEWPORT_WIDTH_PX,
        )
        assertOutOfRange(
            validator.viewportChanged(
                rawWidthPx = maximum,
                rawHeightPx = maximum + 1f,
                rawDensity = 1f,
            ),
            InteractionInputField.VIEWPORT_HEIGHT_PX,
        )
        assertOutOfRange(
            validator.viewportChanged(
                rawWidthPx = maximum,
                rawHeightPx = maximum,
                rawDensity = InteractionIngressLimits.MAX_DENSITY + 1f,
            ),
            InteractionInputField.DENSITY,
        )

        valid<GameIntent.ViewportChanged>(
            validator.viewportChanged(
                rawWidthPx = InteractionIngressLimits.MIN_VIEWPORT_DIMENSION_PX,
                rawHeightPx = InteractionIngressLimits.MIN_VIEWPORT_DIMENSION_PX,
                rawDensity = InteractionIngressLimits.MIN_DENSITY,
            ),
        )
        assertOutOfRange(
            validator.viewportChanged(0f, 1f, 1f),
            InteractionInputField.VIEWPORT_WIDTH_PX,
        )
        assertOutOfRange(
            validator.viewportChanged(1f, 0f, 1f),
            InteractionInputField.VIEWPORT_HEIGHT_PX,
        )
        assertOutOfRange(
            validator.viewportChanged(1f, 1f, 0.49f),
            InteractionInputField.DENSITY,
        )
    }

    @Test
    fun pointerFactoriesRequireAViewportAndUseStrictInclusiveBounds() {
        val validator = GameInteractionValidator()
        assertIs<ValidationFailure.MissingValidatedViewport>(
            invalid(validator.pointerMoved(0f, 0f)),
        )
        valid<GameIntent.ViewportChanged>(validator.viewportChanged(100f, 50f, 1f))

        val origin = valid<GameIntent.PointerMoved>(validator.pointerMoved(0f, 0f, active = false))
        assertFalse(origin.active)
        val edge = valid<GameIntent.PointerPressed>(validator.pointerPressed(100f, 50f))
        assertEquals(100f, edge.x)
        assertEquals(50f, edge.y)

        assertOutOfRange(
            validator.pointerMoved(101f, 50f),
            InteractionInputField.POINTER_X_PX,
        )
        assertOutOfRange(
            validator.pointerPressed(100f, 51f),
            InteractionInputField.POINTER_Y_PX,
        )
        assertOutOfRange(
            validator.pointerMoved(-1f, 0f),
            InteractionInputField.POINTER_X_PX,
        )
        assertNonFinite(
            validator.pointerPressed(Float.POSITIVE_INFINITY, 0f),
            InteractionInputField.POINTER_X_PX,
        )
    }

    @Test
    fun invalidViewportDoesNotReplaceTheLastValidatedPointerBoundary() {
        val validator = GameInteractionValidator()
        valid<GameIntent.ViewportChanged>(validator.viewportChanged(100f, 50f, 1f))
        assertNonFinite(
            validator.viewportChanged(200f, 100f, Float.NaN),
            InteractionInputField.DENSITY,
        )

        valid<GameIntent.PointerMoved>(validator.pointerMoved(100f, 50f))
        assertOutOfRange(
            validator.pointerMoved(101f, 50f),
            InteractionInputField.POINTER_X_PX,
        )
    }

    private inline fun <reified Intent : GameIntent> valid(
        result: InteractionValidationResult<GameIntent>,
    ): Intent {
        val valid = assertIs<InteractionValidationResult.Valid<*>>(result)
        return assertIs<Intent>(valid.intent)
    }

    private fun invalid(
        result: InteractionValidationResult<GameIntent>,
    ): ValidationFailure = assertIs<InteractionValidationResult.Invalid>(result).failure

    private fun assertOutOfRange(
        result: InteractionValidationResult<GameIntent>,
        expectedField: InteractionInputField,
    ) {
        val failure = assertIs<ValidationFailure.OutOfRange>(invalid(result))
        assertEquals(expectedField, failure.field)
        assertTrue(failure.acceptedMinimum.isFinite())
        assertTrue(failure.acceptedMaximum.isFinite())
    }

    private fun assertNonFinite(
        result: InteractionValidationResult<GameIntent>,
        expectedField: InteractionInputField,
    ) {
        val failure = assertIs<ValidationFailure.NonFinite>(invalid(result))
        assertEquals(expectedField, failure.field)
    }
}
