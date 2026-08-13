// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.interaction.GameplayPresentation
import kinetickk.ball.gameplay.interaction.GameplaySessionHost
import kinetickk.ball.profile.api.ProfileModuleResultDelivery

/**
 * Assembly-only composite for the one active GameplayRun host.
 *
 * Application composition is the sole production holder. Downstream Session and presentation
 * roles receive only the narrow Interaction views, while the result router receives this
 * implementation-owned delivery method.
 */
interface GameplayCompositionComponent : GameplaySessionHost, GameplayPresentation {
    fun receiveProfileModuleResult(delivery: ProfileModuleResultDelivery)
}
