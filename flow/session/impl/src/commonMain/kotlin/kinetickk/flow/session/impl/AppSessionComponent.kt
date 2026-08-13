// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.gameplay.api.GameplayModuleResultDelivery
import kinetickk.ball.gameplay.interaction.GameplaySessionHost
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileModuleResultDelivery
import kinetickk.ball.profile.api.SessionProfileRoute
import kinetickk.flow.session.api.AppSessionPort

/** Assembly-facing singleton Session owner plus its two statically bound result sinks. */
interface AppSessionComponent : AppSessionPort {
    fun receiveProfileModuleResult(delivery: ProfileModuleResultDelivery)

    fun receiveGameplayModuleResult(delivery: GameplayModuleResultDelivery)
}

fun createAppSessionComponent(
    profileRoute: SessionProfileRoute,
    gameplaySessionHost: GameplaySessionHost,
    updateAudioPreferences: (PlayerPreferences) -> Unit,
    playMuteFeedback: () -> Unit,
    playRebirthAcceptedFeedback: () -> Unit,
): AppSessionComponent = DefaultAppSessionComponent.create(
    profileRoute = profileRoute,
    gameplaySessionHost = gameplaySessionHost,
    updateAudioPreferences = updateAudioPreferences,
    playMuteFeedback = playMuteFeedback,
    playRebirthAcceptedFeedback = playRebirthAcceptedFeedback,
)
