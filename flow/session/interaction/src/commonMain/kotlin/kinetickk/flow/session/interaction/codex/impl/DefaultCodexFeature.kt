// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.profile.api.ProfileCollectionVisits
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.flow.session.interaction.audio.SessionAudioCue
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.codex.api.*
import kinetickk.flow.session.interaction.home.impl.formIcon
import kinetickk.foundation.design.*
import kinetickk.resource.audio.api.AudioService

class DefaultCodexFeature(
    private val profilePort: ProfileReadPort,
    private val collectionVisits: ProfileCollectionVisits,
    private val uiCatalog: UiCatalogSnapshot,
    audioService: AudioService,
) : CodexFeature {
    private val audioExecutor = SessionAudioExecutor(audioService)
    private val reducer = CodexReducer(uiCatalog.items)

    @Composable
    override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) {
        var collectionValue by remember(profilePort) { mutableStateOf(profilePort.query(ProfileQuery.GetCollection)) }
        CodexContent(
            catalog = uiCatalog,
            model = reducer.renderModel(collectionValue, runStacks),
            progress = profilePort.query(ProfileQuery.GetHomeProgress),
            scale = profilePort.query(ProfileQuery.GetPreferences).preferences.textScale,
            onEntryViewed = { entry ->
                collectionVisits.markViewed(entry)
                collectionValue = profilePort.query(ProfileQuery.GetCollection)
            },
            onClose = {
                audioExecutor.play(SessionAudioCue.UI_CLICK)
                onOutput(CodexOutput.Back)
            },
        )
    }
}
