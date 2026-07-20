// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.settings.api

import androidx.compose.runtime.Composable
import kinetickk.core.audio.api.AudioCue
import kinetickk.core.profile.api.PlayerPreferences

/** Small immutable payload rendered by the Settings feature. */
data class SettingsRenderModel(
    val preferences: PlayerPreferences,
)

sealed interface SettingsOutput {
    data object Back : SettingsOutput
    data class Cue(val cue: AudioCue) : SettingsOutput
}

interface SettingsFeature {
    @Composable
    fun Content(
        routeToken: Int,
        onOutput: (SettingsOutput) -> Unit,
    )
}
