// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.reset.api

import androidx.compose.runtime.Composable

enum class ResetModalMode {
    CONFIRMATION_REQUIRED,
    RESET_IN_PROGRESS,
    PURGE_NEEDS_ATTENTION,
    BOOTSTRAP_UNAVAILABLE,
}

data class ResetModalRenderModel(
    val mode: ResetModalMode,
)

sealed interface ResetModalOutput {
    /** Intentionally leaves the blocking modal unchanged. */
    data object Cancel : ResetModalOutput

    data object ConfirmDelete : ResetModalOutput
    data object RetryPurge : ResetModalOutput
}

interface ResetModalFeature {
    @Composable
    fun Content(
        model: ResetModalRenderModel,
        onOutput: (ResetModalOutput) -> Unit,
    )
}
