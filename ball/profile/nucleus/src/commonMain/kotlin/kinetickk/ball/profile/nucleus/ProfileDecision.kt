// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfileModuleCommandPulse
import kinetickk.ball.profile.api.ProfileModuleResultOutput
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileV4Snapshot
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kinetickk.foundation.collections.ImmutableList

const val MAX_PROFILE_OUTPUTS_PER_DECISION: Int = 2

/** Profile currently has no per-Pulse semantic context. */
data object ProfileContext

sealed interface ProfileNucleusPulse {
    data class Intent(val intent: ProfilePulse.Business) : ProfileNucleusPulse
    data class ModuleCommand(val pulse: ProfileModuleCommandPulse) : ProfileNucleusPulse

    sealed interface Fact : ProfileNucleusPulse

    data class V4WriteCompleted(
        val effectRef: ProfileEffectRef,
        val result: ProfileV4WriteResult,
    ) : Fact

    data class LegacyPurgeCompleted(
        val effectRef: ProfileEffectRef,
        val result: ProfileLegacyPurgeResult,
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
data class ProfileAcceptedFrame(
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
    data class PersistV4Snapshot(
        val effectRef: ProfileEffectRef,
        val snapshot: ProfileV4Snapshot,
    ) : ProfileOutput

    data class PurgeLegacy(
        val effectRef: ProfileEffectRef,
    ) : ProfileOutput

    data class CompleteCommand(
        val result: ProfileModuleResultOutput,
    ) : ProfileOutput
}
