// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.api

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.profile.api.RebirthProgress

/** Small immutable payload rendered by the Rebirth feature. */
data class RebirthRenderModel(
    val current: RebirthProfile,
    val next: RebirthProfile,
    val canAdvance: Boolean,
) {
    val isMaximumTier: Boolean
        get() = next.tier <= current.tier
}

sealed interface RebirthOutput {
    data object Back : RebirthOutput
    data class CycleAdvanced(val progress: RebirthProgress) : RebirthOutput
}

interface RebirthFeature {
    @Composable
    fun Content(
        routeToken: Int,
        eligible: Boolean,
        onOutput: (RebirthOutput) -> Unit,
    )
}
