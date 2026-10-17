// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.foundation.dispatch.InlineReply
import kinetickk.ball.content.api.CoreShape

/** Settings capability implemented by the existing Profile owner. */
interface ProfileSettings {
    fun toggleMute(reply: InlineReply<ProfileSettingsChanged, ProfileRefusal>)
}

data class ProfileSettingsChanged(
    val revision: ProfileRevision,
    val preferences: PlayerPreferences,
)

/** Loadout selection capability; availability and unlock policy remain Profile decisions. */
interface ProfileLoadout {
    fun selectCoreShape(shape: CoreShape, reply: InlineReply<ProfileCoreShapeSelected, ProfileRefusal>)
}

data class ProfileCoreShapeSelected(val revision: ProfileRevision, val shape: CoreShape)

/** Rebirth progression capability; eligibility and captured tier policy remain Profile decisions. */
interface ProfileRebirth {
    /**
     * Selects Rebirth [tier] for the next run: any tier from the policy minimum up to the rank (one
     * above the highest clear). Selecting the current tier again is accepted and confirms it.
     */
    fun selectRebirthTier(tier: Int, reply: InlineReply<ProfileRebirthTierSelected, ProfileRefusal>)
}

data class ProfileRebirthTierSelected(val revision: ProfileRevision, val progress: RebirthProgress)

/** Applies a captured gameplay update under the Profile owner's progression policy. */
interface ProfileProgress {
    fun applyGameplayProgress(update: GameplayProgressUpdate, reply: InlineReply<ProfileProgressApplied, ProfileRefusal>)
}

data class ProfileProgressApplied(val revision: ProfileRevision)

sealed interface ProfileRefusal {
    data object Busy : ProfileRefusal
    data object RevisionCapacityExhausted : ProfileRefusal
    data class DecisionRejected(val reason: ProfileRejection) : ProfileRefusal
}
