// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.rebirth.nucleus.state

import kinetickk.flows.rebirth.nucleus.protocol.RebirthStatus

internal data class RebirthFlowState(
    val acceptedRevision: ULong = 0uL,
    val status: RebirthStatus = RebirthStatus.Idle,
    val transitionSteps: Int = 0,
)
