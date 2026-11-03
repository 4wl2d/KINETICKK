// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.canvas.look.overlayColor
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkStampVariant
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkHalftone
import kinetickk.foundation.design.drawKkRadialFade
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.foundation.design.wideStyle
import kotlin.math.max
import kinetickk.ball.gameplay.interaction.overlay.*

/** The smallest share of its size the incoming relic panel's copy shrinks to. */
internal const val RelicPanelMinFit = 0.6f

/** Board placement of the relic bind layout for one layout mode (board px). */
internal class RelicSpec(
    val boardWidth: Float,
    val boardHeight: Float,
    val stampX: Float,
    val stampY: Float,
    val stampSize: Float,
    val countRight: Float,
    val panel: Rect,
    val diamond: Float,
    val nameSize: Float,
    val bodySize: Float,
    val bodyLines: Int,
    val panelInline: Boolean,
    val matrix: Rect,
    val slotSize: Float,
    val slotRowTop: Float,
    val slotNameSize: Float,
    val bracketStep: Float,
    val side: Rect,
    val rowTitle: Float,
    val rowBody: Float,
    val buttonHeight: Float,
    val buttonFont: Float,
)

private fun relicSpec(mode: GameplayLayoutMode): RelicSpec = when (mode) {
    // Relic board at 1440 x 810.
    GameplayLayoutMode.REGULAR -> RelicSpec(
        1440f, 810f, 56f, 38f, 22f, 1384f,
        Rect(56f, 116f, 436f, 700f), 180f, 36f, 18f, 5, false,
        Rect(452f, 150f, 992f, 670f), 100f, 240f, 20f, 44f,
        Rect(1010f, 116f, 1392f, 740f), 22f, 14f, 72f, 32f,
    )
    GameplayLayoutMode.COMPACT_LANDSCAPE -> RelicSpec(
        844f, 390f, 16f, 10f, 15f, 828f,
        Rect(16f, 52f, 246f, 380f), 76f, 18f, 13f, 3, false,
        Rect(254f, 48f, 590f, 380f), 54f, 150f, 14f, 34f,
        Rect(600f, 52f, 828f, 382f), 16f, 12f, 48f, 22f,
    )
    GameplayLayoutMode.COMPACT_PORTRAIT -> RelicSpec(
        390f, 844f, 16f, 14f, 16f, 374f,
        Rect(16f, 60f, 374f, 290f), 96f, 20f, 14f, 3, true,
        Rect(16f, 288f, 374f, 522f), 58f, 108f, 14f, 32f,
        Rect(16f, 530f, 374f, 832f), 18f, 13f, 56f, 26f,
    )
}

/**
 * The incoming relic panel of the bind screen: the lifted slot's panel reads the incoming keys
 * against the matrix that choice would leave (a replaced opposing relic no longer underlines
 * them); before a slot is lifted, against every bound relic.
 */
internal fun RewardPresentation.bindPanel(preview: RewardRelicPreview?): RewardRelicPanel? =
    preview?.panel ?: relicPanel ?: cards.firstOrNull()?.relicPreview?.panel

/**
 * Relic binding (Relic board): incoming relic panel, the straight row of matrix slots (each slot
 * is a choice), synergy brackets and the signed preview of the lifted slot with the primary
 * action (Replace in the threat color).
 */
@Composable
internal fun RewardRelicBind(state: RewardOverlayState) {
    val presentation = state.presentation
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = rememberInterfaceTypography()
    val spec = relicSpec(state.mode)
    val matrix = presentation.relicMatrix ?: return
    val selectedCard = state.selected?.let(presentation.cards::getOrNull)
    val preview = selectedCard?.relicPreview
    val replace = presentation.cards.firstOrNull()?.choice?.relicAction == RelicChoiceAction.REPLACE
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frame = overlayFrame(maxWidth.value, maxHeight.value, spec.boardWidth, spec.boardHeight, state.textScale)
        val panel = presentation.bindPanel(preview)
        val accent = panel?.aspect?.overlayColor(roles) ?: roles.you
        // Halo behind the matrix in the incoming aspect.
        Canvas(Modifier.fillMaxSize()) {
            val cx = (frame.x(spec.matrix.center.x).toPx())
            val cy = (frame.y(spec.matrix.center.y).toPx())
            val r = frame.dp(spec.matrix.width * 0.62f).toPx()
            val halo = Rect(cx - r, cy - r, cx + r, cy + r)
            drawKkRadialFade(halo) { drawKkHalftone(halo, accent.copy(alpha = 0.12f)) }
        }
        OverlayStamp(
            presentation.heading,
            Modifier.offset(frame.x(spec.stampX), frame.y(spec.stampY)).testTag("kinetickk.gameplay.rewards.heading"),
            KkStampVariant.YOU, color = accent, fontSize = frame.sp(spec.stampSize, 13f) / state.textScale, textScale = state.textScale,
            slam = { ((state.entrance() - 60f) / 500f).coerceIn(0f, 1f) },
        )
        Row(
            Modifier.offset(frame.x(spec.countRight - 200f), frame.y(spec.stampY + 2f)).width(frame.dp(200f)),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.Bottom,
        ) {
            OverlayText(language.text(OverlayRedesignText.Relics), typography.labelStyle(frame.sp(15f, 11f), color = Kk.Mute),
                Modifier.padding(bottom = 2.dp), uppercase = true)
            OverlayText("${matrix.equipped}/${matrix.maxSlots}", typography.wideStyle(frame.sp(22f, 15f), tabular = true, color = Kk.Bone))
        }
        if (panel != null) {
            RelicPanel(panel, spec, frame, typography, state,
                Modifier.offset(frame.x(spec.panel.left), frame.y(spec.panel.top)).width(frame.dp(spec.panel.width)))
        }
        val slotText = rememberRelicSlotText(matrix, spec, frame, typography)
        val placement = relicRowPlacement(spec, frame, slotText, LocalDensity.current.density)
        RelicMatrixRow(matrix, preview, replace, spec, placement, frame, state, roles, slotText)
        RelicPreviewSide(preview, selectedCard, replace, spec, placement, frame, state, typography, roles)
    }
}

/**
 * The relic row's slot labels, shared by every slot: the [name] style and the height (px) of the
 * box every name sits in, so each [rank] label below sits on one baseline; [nameWidth] (dp)
 * leaves [RelicSlotNameGapDp] between neighbouring names.
 */
internal class RelicSlotText(val name: TextStyle, val nameHeight: Float, val nameWidth: Float, val rank: TextStyle, val rankHeight: Float)

/**
 * Names share the largest size (to [RelicSlotNameMinScale]) at which each fits [widthPx] in two
 * lines without breaking a word; their box is as tall as the tallest name. Rank labels share
 * one size that fits the same width on one line.
 */
internal fun relicSlotText(
    measurer: TextMeasurer,
    names: List<String>,
    ranks: List<String>,
    name: TextStyle,
    rank: TextStyle,
    widthPx: Float,
    widthDp: Float,
): RelicSlotText {
    val shown = names.filter(String::isNotBlank).map(String::uppercase)
    val nameStyle = sharedFitStyle(measurer, shown.map { OverlayTextBox(it, widthPx) }, name, 2, RelicSlotNameMinScale)
    val constraints = Constraints(maxWidth = widthPx.toInt().coerceAtLeast(1))
    val nameHeight = shown.maxOfOrNull { measurer.measure(it, nameStyle, constraints = constraints).size.height }?.toFloat() ?: 0f
    val rankShown = ranks.filter(String::isNotBlank).map(String::uppercase)
    val rankStyle = sharedFitStyle(measurer, rankShown.map { OverlayTextBox(it, widthPx) }, rank, 1, 0.7f)
    val rankHeight = rankShown.maxOfOrNull { measurer.measure(it, rankStyle, softWrap = false, maxLines = 1).size.height }?.toFloat() ?: 0f
    return RelicSlotText(nameStyle, nameHeight, widthDp, rankStyle, rankHeight)
}

@Composable
private fun rememberRelicSlotText(matrix: RewardRelicMatrix, spec: RelicSpec, frame: OverlayFrame, typography: InterfaceTypography): RelicSlotText {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val density = LocalDensity.current.density
    val column = spec.matrix.width / matrix.slots.size.coerceAtLeast(1)
    val widthDp = column * frame.scale - RelicSlotNameGapDp
    val name = typography.condStyle(frame.sp(spec.slotNameSize, 12f))
    val rank = typography.monoStyle(frame.sp(11f, 9f))
    return remember(matrix.names, matrix.ranks, name, rank, widthDp, density) {
        // One px under the laid-out width absorbs dp to px rounding.
        relicSlotText(measurer, matrix.names, matrix.ranks, name, rank, widthDp * density - 1f, widthDp)
    }
}

/** Clear space (dp) every slot name keeps to its neighbours. */
internal const val RelicSlotNameGapDp = 8f

/** The smallest share of its size a slot name shrinks to before it takes a third line. */
private const val RelicSlotNameMinScale = 0.5f

/** Board px between a slot's diamond and its name (and the slot button's vertical padding). */
internal const val RelicSlotGap = 14f

/** Board px the rank labels keep above the matrix bottom (the side column keeps its own gap below it). */
private const val RelicRankClearance = 8f

/**
 * Where the relic row ends and the preview column starts (board px): the matrix grows to hold
 * the slot row with its labels at larger text, and a preview column below it (portrait) moves
 * down with it so the rank labels never run into its heading. [sideShift] is how far it moved.
 */
internal class RelicRowPlacement(val matrixHeight: Float, val sideTop: Float, val sideShift: Float)

private fun relicRowPlacement(spec: RelicSpec, frame: OverlayFrame, text: RelicSlotText, density: Float): RelicRowPlacement {
    val labels = (text.nameHeight + 4f * density + text.rankHeight) / (frame.scale * density)
    val rowBottom = spec.slotRowTop + spec.slotSize + RelicSlotGap + labels
    val matrixHeight = max(spec.matrix.height, rowBottom + RelicRankClearance)
    val sideBelow = spec.side.top >= spec.matrix.bottom
    val sideTop = if (sideBelow) max(spec.side.top, spec.matrix.top + matrixHeight + spec.side.top - spec.matrix.bottom) else spec.side.top
    return RelicRowPlacement(matrixHeight, sideTop, sideTop - spec.side.top)
}

/** Board px of the extra gap above the preview column's button; a pushed-down column gives it up first. */
internal const val RelicButtonGap = 12f
