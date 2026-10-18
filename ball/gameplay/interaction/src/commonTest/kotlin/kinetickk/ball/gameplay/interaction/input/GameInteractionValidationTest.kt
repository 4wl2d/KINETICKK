// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.MAX_GAMEPLAY_PRESENTATION_DELTA_SECONDS
import kinetickk.ball.gameplay.interaction.selectGameplayPresentationDelta
import kinetickk.ball.gameplay.interaction.input.GameInteractionValidator
import kinetickk.ball.gameplay.interaction.input.InteractionIngressLimits
import kinetickk.ball.gameplay.interaction.input.InteractionInputField
import kinetickk.ball.gameplay.interaction.input.InteractionValidationResult
import kinetickk.ball.gameplay.interaction.input.ValidationFailure
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.Test

class GameInteractionValidationTest {
    @Test
    fun presentationDeltaAcceptsPointOneAndClampsFirstNPlusOne() {
        assertEquals(
            MAX_GAMEPLAY_PRESENTATION_DELTA_SECONDS,
            selectGameplayPresentationDelta(MAX_GAMEPLAY_PRESENTATION_DELTA_SECONDS),
        )
        assertEquals(
            MAX_GAMEPLAY_PRESENTATION_DELTA_SECONDS,
            selectGameplayPresentationDelta(
                Float.fromBits(MAX_GAMEPLAY_PRESENTATION_DELTA_SECONDS.toBits() + 1),
            ),
        )
    }

    @Test
    fun viewportAndPointerReachTheNucleusInDp() {
        val validator = GameInteractionValidator()
        val viewport = valid<GameplayInteractionPulse.ViewportChanged>(validator.viewportChanged(2_560f, 1_600f, 2f))
        assertEquals(1_280f, viewport.width)
        assertEquals(800f, viewport.height)
        assertEquals(2f, viewport.density)
        val pointer = valid<GameplayInteractionPulse.PointerMoved>(validator.pointerMoved(1_000f, 600f, density = 2f))
        assertEquals(500f, pointer.x)
        assertEquals(300f, pointer.y)
        // A tiny viewport at a high density stays a representable dp viewport.
        val tiny = valid<GameplayInteractionPulse.ViewportChanged>(
            validator.viewportChanged(1f, 1f, InteractionIngressLimits.MAX_DENSITY),
        )
        assertEquals(InteractionIngressLimits.MIN_VIEWPORT_DIMENSION_PX, tiny.width)
        assertOutOfRange(validator.pointerMoved(1f, 1f, density = 0f), InteractionInputField.DENSITY)
        assertNonFinite(validator.pointerMoved(1f, 1f, density = Float.NaN), InteractionInputField.DENSITY)
        // The pointer density shares the viewport bounds: both ends accepted, the next values rejected.
        val minimum = InteractionIngressLimits.MIN_DENSITY
        val maximum = InteractionIngressLimits.MAX_DENSITY
        assertEquals(4f / minimum, valid<GameplayInteractionPulse.PointerMoved>(validator.pointerMoved(4f, 4f, density = minimum)).x)
        assertEquals(4f / maximum, valid<GameplayInteractionPulse.PointerMoved>(validator.pointerMoved(4f, 4f, density = maximum)).x)
        assertOutOfRange(
            validator.pointerMoved(4f, 4f, density = Float.fromBits(minimum.toBits() - 1)),
            InteractionInputField.DENSITY,
        )
        assertOutOfRange(
            validator.pointerMoved(4f, 4f, density = Float.fromBits(maximum.toBits() + 1)),
            InteractionInputField.DENSITY,
        )
    }

    @Test
    fun frameDeltaAcceptsExactBoundsAndRejectsTheNextRepresentableValues() {
        val validator = GameInteractionValidator()

        assertEquals(
            InteractionIngressLimits.MIN_FRAME_DELTA_SECONDS,
            valid<GameplayInteractionPulse.FrameElapsed>(
                validator.frameElapsed(InteractionIngressLimits.MIN_FRAME_DELTA_SECONDS),
            ).realDeltaSeconds,
        )
        assertEquals(
            InteractionIngressLimits.MAX_FRAME_DELTA_SECONDS,
            valid<GameplayInteractionPulse.FrameElapsed>(
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
        val accepted = valid<GameplayInteractionPulse.ViewportChanged>(
            validator.viewportChanged(
                rawWidthPx = maximum,
                rawHeightPx = maximum,
                rawDensity = InteractionIngressLimits.MAX_DENSITY,
            ),
        )
        // The Nucleus simulates the dp viewport: device pixels are divided by the density here.
        assertEquals(maximum / InteractionIngressLimits.MAX_DENSITY, accepted.width)
        assertEquals(maximum / InteractionIngressLimits.MAX_DENSITY, accepted.height)
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

        valid<GameplayInteractionPulse.ViewportChanged>(
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
    fun pointerValidationAcceptsFiniteCoordinatesAndRejectsNonfiniteXAndY() {
        val validator = GameInteractionValidator()

        val origin = valid<GameplayInteractionPulse.PointerMoved>(
            validator.pointerMoved(0f, 0f, active = false),
        )
        assertFalse(origin.active)
        val edge = valid<GameplayInteractionPulse.PointerMoved>(
            validator.pointerMoved(100f, 50f),
        )
        assertEquals(100f, edge.x)
        assertEquals(50f, edge.y)

        assertEquals(-1f, valid<GameplayInteractionPulse.PointerMoved>(
            validator.pointerMoved(-1f, 51f),
        ).x)
        assertNonFinite(
            validator.pointerMoved(Float.POSITIVE_INFINITY, 0f),
            InteractionInputField.POINTER_X_PX,
        )
        assertNonFinite(
            validator.pointerMoved(0f, Float.NaN),
            InteractionInputField.POINTER_Y_PX,
        )
    }

    @Test
    fun choiceIndexValidationAcceptsZeroThroughThreeAndRejectsBothAdjacentValues() {
        val validator = GameInteractionValidator()

        assertEquals(
            0,
            valid<GameplayInteractionPulse.ChoiceSelected>(validator.choiceSelected(0)).index,
        )
        assertEquals(
            3,
            valid<GameplayInteractionPulse.ChoiceSelected>(validator.choiceSelected(3)).index,
        )
        listOf(-1, 4).forEach { rejectedIndex ->
            val failure = assertIs<ValidationFailure.IndexOutOfRange>(
                invalid(validator.choiceSelected(rejectedIndex)),
            )
            assertEquals(InteractionInputField.CHOICE_INDEX, failure.field)
            assertEquals(0, failure.acceptedMinimum)
            assertEquals(3, failure.acceptedMaximum)
        }
    }

    private inline fun <reified Intent : GameplayInteractionPulse> valid(
        result: InteractionValidationResult<GameplayInteractionPulse>,
    ): Intent {
        val valid = assertIs<InteractionValidationResult.Valid<*>>(result)
        return assertIs<Intent>(valid.intent)
    }

    private fun invalid(
        result: InteractionValidationResult<GameplayInteractionPulse>,
    ): ValidationFailure = assertIs<InteractionValidationResult.Invalid>(result).failure

    private fun assertOutOfRange(
        result: InteractionValidationResult<GameplayInteractionPulse>,
        expectedField: InteractionInputField,
    ) {
        val failure = assertIs<ValidationFailure.OutOfRange>(invalid(result))
        assertEquals(expectedField, failure.field)
        assertTrue(failure.acceptedMinimum.isFinite())
        assertTrue(failure.acceptedMaximum.isFinite())
    }

    private fun assertNonFinite(
        result: InteractionValidationResult<GameplayInteractionPulse>,
        expectedField: InteractionInputField,
    ) {
        val failure = assertIs<ValidationFailure.NonFinite>(invalid(result))
        assertEquals(expectedField, failure.field)
    }
}
