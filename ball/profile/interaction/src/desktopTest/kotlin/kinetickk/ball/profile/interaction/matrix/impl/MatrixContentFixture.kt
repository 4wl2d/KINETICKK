// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.matrix.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.profileTextScale
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopPanel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.rememberKkCanvasMeasurer

/**
 * The Core Matrix tab as the Workshop hosts it (its panel, Reset and background) for a fixed
 * [state]: the screen tests and shots drive the body without a Profile port.
 */
@Composable
internal fun MatrixContent(
    state: MatrixState,
    textScale: Float,
    gridScroll: ScrollState,
    holder: MatrixLayoutHolder = remember { MatrixLayoutHolder() },
    onAction: (MatrixAction) -> Unit,
) {
    val language = LocalAppLanguage.current
    val scale = profileTextScale(textScale)
    val measurer = rememberKkCanvasMeasurer(scale)
    val (owned, total) = state.model.nodeTotals()
    val reset: @Composable RowScope.(ProfileFrame) -> Unit = { frame -> MatrixResetButton(frame, state, measurer, scale, language, onAction) }
    WorkshopPanel(
        tab = WorkshopTab.MATRIX,
        count = language.text(ProfileScreensRedesignText.CountOf, owned, total),
        info = language.text(ProfileScreensRedesignText.WorkshopMatrixInfo),
        matter = state.model.matter,
        scale = scale,
        onBack = { onAction(MatrixAction.Back) },
        onAction = {},
        background = { frame -> drawMatrixBackground(frame, holder.layout) },
        headerExtra = reset,
        headerExtraWidth = { frame -> matrixResetWidth(measurer, frame, language) },
    ) { frame, header ->
        MatrixBody(frame, state, textScale, gridScroll, holder, gridExtra = if (header.extraInBody) ({ reset(frame) }) else null,
            onAction = onAction)
    }
}
