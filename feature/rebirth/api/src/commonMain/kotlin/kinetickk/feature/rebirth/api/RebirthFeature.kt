// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.rebirth.api

import androidx.compose.runtime.Composable
import kinetickk.core.audio.api.AudioCue
import kinetickk.core.content.RebirthProfile
import kinetickk.core.profile.api.RebirthProgress

/** Small immutable payload rendered by the Rebirth feature. */
data class RebirthRenderModel(
    val current: RebirthProfile,
    val next: RebirthProfile,
    val canAdvance: Boolean,
) {
    val isMaximumTier: Boolean
        get() = next.tier <= current.tier
}

sealed interface RebirthOutput {
    data object Back : RebirthOutput
    data class Cue(val cue: AudioCue) : RebirthOutput
    data class CycleAdvanced(val progress: RebirthProgress) : RebirthOutput
}

interface RebirthFeature {
    @Composable
    fun Content(
        routeToken: Int,
        eligible: Boolean,
        onOutput: (RebirthOutput) -> Unit,
    )
}
