// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.api

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.RebirthProfile

/** Where a tier stands on the ladder: cleared, earned (selectable, not yet cleared) or locked. */
enum class RebirthTierStatus { CLEARED, EARNED, LOCKED }

/**
 * The Rebirth ladder's payload. [current] is the selected tier (the next run's), [focus] the tier the
 * screen details (mutators, scaling against [current], unlocks, status, select action).
 * [highestCleared] is the profile's record (-1 before the first victory); [eligible] is the shell's
 * permission to start a run now (Home, or a Victory).
 */
data class RebirthRenderModel(
    val current: RebirthProfile,
    val focus: RebirthProfile,
    val highestCleared: Int,
    val eligible: Boolean,
    /** Banked Matter for the header chip. */
    val matter: Long = 0L,
    /** The policy's tier range for the tier ladder. */
    val minimumTier: Int = 0,
    val maximumTier: Int = focus.tier,
    /** Build-system changes of the tiers; null hides the build sections. */
    val build: RebirthBuildModel? = null,
) {
    /** The highest selectable tier: one above the highest clear, within the ladder. */
    val rank: Int get() = (highestCleared + 1).coerceIn(minimumTier, maximumTier)

    /** Every tier cleared: the player is Ascendant. */
    val ascendant: Boolean get() = highestCleared >= ContentBounds.MAX_REBIRTH_LEVEL

    /** The tier this screen shows. */
    val targetTier: Int get() = focus.tier

    fun status(tier: Int): RebirthTierStatus = when {
        tier <= highestCleared -> RebirthTierStatus.CLEARED
        tier <= rank -> RebirthTierStatus.EARNED
        else -> RebirthTierStatus.LOCKED
    }

    val focusStatus: RebirthTierStatus get() = status(focus.tier)

    /** A tier above the highest clear takes the two-press confirmation. */
    val needsConfirmation: Boolean get() = focus.tier > highestCleared

    /** The focused tier can start a run now. */
    val canSelect: Boolean get() = eligible && focus.tier <= rank

    /** Gated content the focused tier opens. */
    val focusUnlocks: List<RebirthUnlock> = build?.unlocksAt(focus.tier) ?: emptyList()
}

sealed interface RebirthOutput {
    data object Back : RebirthOutput

    /** First press on an uncleared [tier]: Session arms the two-press confirmation. */
    data class ArmRequested(val tier: Int) : RebirthOutput

    /** Starts a run at [tier]: the second press on an uncleared tier, or one press on a cleared one. */
    data class ConfirmRequested(val tier: Int) : RebirthOutput
}

interface RebirthFeature {
    /** Plays the Profile-owned feedback after Session observes an accepted Rebirth command. */
    fun playAcceptedFeedback()

    /**
     * The tier animation that follows [playAcceptedFeedback] (1.6 s, then nothing). Session leaves the
     * Rebirth screen as soon as the run starts, so a host draws this above whatever comes next; the
     * default draws nothing.
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
