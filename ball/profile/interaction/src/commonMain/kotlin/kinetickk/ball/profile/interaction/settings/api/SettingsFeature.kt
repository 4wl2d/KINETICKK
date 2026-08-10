// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.api

import androidx.compose.runtime.Composable
import kinetickk.ball.profile.api.PlayerPreferences

/** Small immutable payload rendered by the Settings feature. */
data class SettingsRenderModel(
    val preferences: PlayerPreferences,
)

sealed interface SettingsOutput {
    data object Back : SettingsOutput
}

interface SettingsFeature {
    @Composable
    fun Content(
        routeToken: Int,
        onOutput: (SettingsOutput) -> Unit,
    )
}
