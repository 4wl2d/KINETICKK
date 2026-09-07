// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.gameplay.interaction.GameplayRunHost
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileSettings
import kinetickk.ball.profile.api.ProfileRebirth
import kinetickk.ball.profile.api.ProfileLoadout
import kinetickk.flow.session.api.AppSessionPort

fun createAppSessionComponent(
    profilePort: ProfileReadPort,
    profileSettings: ProfileSettings,
    profileLoadout: ProfileLoadout,
    profileRebirth: ProfileRebirth,
    gameplayRunHost: GameplayRunHost,
    updateAudioPreferences: (PlayerPreferences) -> Unit,
    playMuteFeedback: () -> Unit,
    playRebirthAcceptedFeedback: () -> Unit,
): AppSessionPort = DefaultAppSessionComponent.create(
    profilePort = profilePort,
    profileSettings = profileSettings,
    profileLoadout = profileLoadout,
    profileRebirth = profileRebirth,
    gameplayRunHost = gameplayRunHost,
    updateAudioPreferences = updateAudioPreferences,
    playMuteFeedback = playMuteFeedback,
    playRebirthAcceptedFeedback = playRebirthAcceptedFeedback,
)
