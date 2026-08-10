// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.interaction.GameplayFeature
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.flow.session.api.AppSessionPort
import kinetickk.flow.session.api.SessionConfiguration

/** Assembly-facing singleton Session owner plus its two statically bound result sinks. */
interface AppSessionComponent : AppSessionPort {
    fun receiveProfileCommandResult(result: ProfileCommandResult.Accepted)

    fun receiveGameplayCommandResult(result: GameplayCommandResult.Accepted)
}

fun createAppSessionComponent(
    gameplayContent: GameplayContentSnapshot,
    profilePort: ProfilePort,
    gameplayFeature: GameplayFeature,
    updateAudioPreferences: (PlayerPreferences) -> Unit,
    playMuteFeedback: () -> Unit,
    playRebirthAcceptedFeedback: () -> Unit,
): AppSessionComponent = DefaultAppSessionComponent.create(
    configuration = SessionConfiguration(gameplayContent),
    profilePort = profilePort,
    gameplayFeature = gameplayFeature,
    updateAudioPreferences = updateAudioPreferences,
    playMuteFeedback = playMuteFeedback,
    playRebirthAcceptedFeedback = playRebirthAcceptedFeedback,
)
