// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.profile.nucleus.state

import kinetickk.features.profile.nucleus.protocol.RunSettlementId

internal data class AppliedRunSettlement(
    val settlementId: RunSettlementId,
    val matterEarned: Long,
    val clearedRebirthLevel: Int?,
)

internal data class ProfileState(
    val values: PlayerProfileValues,
    val lastRunSettlement: AppliedRunSettlement? = null,
    val transitionSteps: Int = 0,
)
