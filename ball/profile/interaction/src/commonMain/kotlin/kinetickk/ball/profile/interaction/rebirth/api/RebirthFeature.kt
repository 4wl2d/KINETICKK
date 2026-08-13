// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.api

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.RebirthProfile

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
    data object ArmRequested : RebirthOutput
    data object ConfirmRequested : RebirthOutput
}

interface RebirthFeature {
    /** Plays the Profile-owned feedback after Session observes an accepted Rebirth command. */
    fun playAcceptedFeedback()

    @Composable
    fun Content(
        routeToken: Long,
        eligible: Boolean,
        confirmationArmed: Boolean,
        onOutput: (RebirthOutput) -> Unit,
    )
}
