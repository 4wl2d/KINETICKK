// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.lab.api

import androidx.compose.runtime.Composable
import kinetickk.core.audio.api.AudioCue
import kinetickk.core.collections.ImmutableList
import kinetickk.core.content.MetaUpgradeId

data class LabUpgradeRenderModel(
    val id: MetaUpgradeId,
    val name: String,
    val description: String,
    val rank: Int,
    val maxRanks: Int,
    val nextCost: Long,
    val isMaxed: Boolean,
    val isAffordable: Boolean,
)

/** Small immutable payload rendered by the Lab feature. */
data class LabRenderModel(
    val matter: Long,
    val upgrades: ImmutableList<LabUpgradeRenderModel>,
)

sealed interface LabOutput {
    data object Back : LabOutput
    data class Cue(val cue: AudioCue) : LabOutput
}

interface LabFeature {
    @Composable
    fun Content(
        routeToken: Int,
        onOutput: (LabOutput) -> Unit,
    )
}
