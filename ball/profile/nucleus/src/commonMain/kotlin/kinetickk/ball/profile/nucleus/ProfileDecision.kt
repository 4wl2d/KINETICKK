// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileCoreShapeSelected
import kinetickk.ball.profile.api.ProfileRebirthAdvanced
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.foundation.collections.ImmutableList

const val MAX_PROFILE_OUTPUTS_PER_DECISION: Int = 2

sealed interface ProfileNucleusPulse {
    data class Intent(val intent: ProfilePulse.Business) : ProfileNucleusPulse
    data object ToggleMute : ProfileNucleusPulse
    data class SelectCoreShape(val shape: CoreShape) : ProfileNucleusPulse
    data object AdvanceRebirth : ProfileNucleusPulse
    data class ApplyGameplayProgress(val update: GameplayProgressUpdate) : ProfileNucleusPulse

    sealed interface Fact : ProfileNucleusPulse

    data class WriteCompleted(
        val effectRef: ProfileEffectRef,
        val result: ProfileWriteResult,
    ) : Fact
}

sealed interface ProfileDecision {
    data class Accepted(
        val frame: ProfileAcceptedFrame,
    ) : ProfileDecision

    data class Rejected(
        val reason: ProfileRejection,
    ) : ProfileDecision
}

/** Flattened snapshot frame: the full next State plus an ordered semantic output batch. */
public data class ProfileAcceptedFrame(
    val nextState: ProfileState,
    val outputs: ImmutableList<ProfileOutput>,
) {
    init {
        require(outputs.size <= MAX_PROFILE_OUTPUTS_PER_DECISION) {
            "Profile semantic output limit exceeded"
        }
    }
}

sealed interface ProfileOutput {
    data class PersistSnapshot(
        val effectRef: ProfileEffectRef,
        val snapshot: ProfileSnapshot,
    ) : ProfileOutput

    data class SettingsChanged(val result: ProfileSettingsChanged) : ProfileOutput
    data class CoreShapeSelected(val result: ProfileCoreShapeSelected) : ProfileOutput
    data class RebirthAdvanced(val result: ProfileRebirthAdvanced) : ProfileOutput
    data class ProgressApplied(val result: ProfileProgressApplied) : ProfileOutput
}
