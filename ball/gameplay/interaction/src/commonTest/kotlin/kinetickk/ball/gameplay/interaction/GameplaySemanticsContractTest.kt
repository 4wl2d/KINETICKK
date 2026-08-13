// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameplaySemanticsContractTest {
    @Test
    fun brakeUsesStableAccessibleDescriptionAndPressState() {
        assertEquals("Brake", GAMEPLAY_BRAKE_DESCRIPTION)
        assertEquals("Toggle brake", GAMEPLAY_BRAKE_SEMANTIC_ACTION_LABEL)
        assertEquals("released", gameplayBrakeStateDescription(active = false))
        assertEquals("pressed", gameplayBrakeStateDescription(active = true))
    }

    @Test
    fun semanticBrakeActivationLatchesAcrossFramesUntilNextExplicitAction() {
        var braking = false

        braking = gameplayBrakeSemanticToggleState(braking)
        assertTrue(braking)

        // Frame publication observes the current render state; it must not synthesize a
        // release like the old semantic click did in the same callback.
        repeat(3) {
            selectGameplayPresentationDelta(1f / 60f)
            assertTrue(braking)
        }

        braking = gameplayBrakeSemanticToggleState(braking)
        assertFalse(braking)
    }
}
