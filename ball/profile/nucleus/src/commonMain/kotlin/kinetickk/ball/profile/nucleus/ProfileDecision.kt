// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.ball.profile.api.ProfileCommandAdmission
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileV4Snapshot
import kinetickk.foundation.collections.ImmutableList

const val MAX_PROFILE_OUTPUTS_PER_DECISION: Int = 2

/** Sparse, immutable evidence available to the pure decision. */
data class ProfileContext(
    val command: ProfileCommand? = null,
    val admission: ProfileCommandAdmission? = null,
) {
    companion object {
        val Local: ProfileContext = ProfileContext()
    }
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
)

sealed interface ProfileOutput {
    data class PersistV4Snapshot(
        val effectRef: ProfileEffectRef,
        val snapshot: ProfileV4Snapshot,
    ) : ProfileOutput

    data class PurgeLegacy(
        val effectRef: ProfileEffectRef,
    ) : ProfileOutput

    data class CompleteCommand(
        val result: ProfileCommandResult.Accepted,
    ) : ProfileOutput
}
