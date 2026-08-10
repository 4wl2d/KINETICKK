// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.reset.impl

import kinetickk.flow.session.interaction.reset.api.ResetModalMode
import kinetickk.flow.session.interaction.reset.api.ResetModalOutput
import kinetickk.flow.session.interaction.reset.api.ResetModalRenderModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResetModalStateTest {
    private val viewport = ResetModalViewport(width = 1_280f, height = 720f, density = 1f)

    @Test
    fun confirmationControlsEmitCancelAndConfirmDelete() {
        val model = ResetModalRenderModel(ResetModalMode.CONFIRMATION_REQUIRED)

        assertEquals(ResetModalOutput.Cancel, resolveResetModalPress(model, viewport, 400f, 520f))
        assertEquals(ResetModalOutput.ConfirmDelete, resolveResetModalPress(model, viewport, 850f, 520f))
    }

    @Test
    fun needsAttentionPrimaryControlEmitsRetryPurge() {
        val model = ResetModalRenderModel(ResetModalMode.PURGE_NEEDS_ATTENTION)

        assertEquals(ResetModalOutput.RetryPurge, resolveResetModalPress(model, viewport, 850f, 520f))
    }

    @Test
    fun inProgressModalBlocksInputWithoutEmittingAControl() {
        val model = ResetModalRenderModel(ResetModalMode.RESET_IN_PROGRESS)

        assertNull(resolveResetModalPress(model, viewport, 400f, 520f))
        assertNull(resolveResetModalPress(model, viewport, 850f, 520f))
    }

    @Test
    fun unavailableBootstrapCannotEmitDestructiveControls() {
        val model = ResetModalRenderModel(ResetModalMode.BOOTSTRAP_UNAVAILABLE)

        val outputs = listOfNotNull(
            resolveResetModalPress(model, viewport, 400f, 520f),
            resolveResetModalPress(model, viewport, 850f, 520f),
        ).toSet()

        assertEquals(setOf(ResetModalOutput.Cancel), outputs)
    }
}
