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
    /** Banked Matter for the header chip. */
    val matter: Long = 0L,
    /** The policy's tier range for the tier ladder. */
    val minimumTier: Int = 0,
    val maximumTier: Int = next.tier,
) {
    val isMaximumTier: Boolean
        get() = next.tier <= current.tier

    /** The tier this screen advances to (the current tier once the last one is reached). */
    val targetTier: Int
        get() = if (isMaximumTier) current.tier else next.tier
}

sealed interface RebirthOutput {
    data object Back : RebirthOutput
    data object ArmRequested : RebirthOutput
    data object ConfirmRequested : RebirthOutput
}

interface RebirthFeature {
    /** Plays the Profile-owned feedback after Session observes an accepted Rebirth command. */
    fun playAcceptedFeedback()

    /**
     * The advance animation that follows [playAcceptedFeedback] (1.6 s, then nothing). Session
     * leaves the Rebirth screen as soon as the run starts, so a host draws this above whatever
     * comes next; the default draws nothing.
     */
    @Composable
    fun AcceptedFeedback() = Unit

    @Composable
    fun Content(
        routeToken: Long,
        eligible: Boolean,
        confirmationArmed: Boolean,
        onOutput: (RebirthOutput) -> Unit,
    )
}
