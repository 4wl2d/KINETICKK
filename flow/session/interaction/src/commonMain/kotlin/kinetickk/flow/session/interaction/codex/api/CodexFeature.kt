// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.api

import androidx.compose.runtime.Composable
import kinetickk.resource.audio.api.AudioCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableListOf

data class CodexRunStacks(
    val itemStacks: ImmutableList<Int> = immutableListOf(),
)

data class CodexRenderModel(
    val discoveredItemIds: ImmutableSet<Int>,
    val runStacks: CodexRunStacks,
) {
    fun isDiscovered(itemId: Int): Boolean = itemId in discoveredItemIds
    fun itemStack(itemId: Int): Int = runStacks.itemStacks.getOrElse(itemId) { 0 }
}

sealed interface CodexOutput {
    data object Back : CodexOutput
    data class Cue(val cue: AudioCue) : CodexOutput
}

interface CodexFeature {
    @Composable
    fun Content(
        runStacks: CodexRunStacks,
        onOutput: (CodexOutput) -> Unit,
    )
}
