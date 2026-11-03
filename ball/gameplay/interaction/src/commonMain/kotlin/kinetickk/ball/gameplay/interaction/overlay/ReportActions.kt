// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.overlay

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.KkButtonSize
import kinetickk.foundation.design.KkButtonVariant
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.rememberKkCanvasMeasurer

/** One button of the report's action row. */
private class ReportAction(
    val label: String,
    val input: GameplayInput,
    val tag: String,
    val variant: KkButtonVariant,
    val size: KkButtonSize,
    /** Board font size, before the text-size factor. */
    val font: Float,
    /** Share of the row's spare width; 0 keeps the natural width plus [slackDp]. */
    val weight: Float,
    val height: Dp,
    val enterDelay: Float,
    val slackDp: Float = 0f,
    val contentDescription: String? = null,
)

/**
 * Victory: Rebirth (primary) + Re-enter; defeat, and a Baby mode victory (its clear counted nothing, so it opens no next
 * cycle): Re-enter. The game has no Lab action here. On a
 * phone in landscape Menu shares the row ([withMenu]). The row is sized as a whole
 * ([overlayRowWidths]): every label keeps its board size while the row has room, and when it has
 * not, all labels shrink by one factor, so the primary label stays the largest and a larger text
 * size never leaves a label smaller than the default did.
 */
@Composable
internal fun ReportActions(
    scene: ReportScene,
    frame: OverlayFrame,
    modifier: Modifier,
    primaryHeight: Float,
    primaryFont: Float,
    ghostFont: Float,
    withMenu: Boolean = false,
) {
    val language = LocalAppLanguage.current
    val height = (primaryHeight * (if (primaryHeight >= 64f) frame.scale else 1f)).coerceAtLeast(48f).dp
    val big = primaryHeight >= 64f
    val fontScale = if (big) frame.scale else 1f
    val primarySize = if (big) KkButtonSize.LG else KkButtonSize.MD
    val rebirthNext = scene.presentation.victory && !scene.presentation.easy
    val actions = remember(language, rebirthNext, withMenu, height, primarySize, fontScale, primaryFont, ghostFont) {
        buildList {
            if (rebirthNext) {
                add(ReportAction(language.text(GameplayText.RebirthNext), GameplayInput.OpenRebirth, "kinetickk.gameplay.rebirth",
                    KkButtonVariant.PRIMARY, primarySize, primaryFont * fontScale, 1f, height, 0.6f))
                add(ReportAction(language.text(GameplayText.Reenter), GameplayInput.RestartRun, "kinetickk.gameplay.restart",
                    KkButtonVariant.GHOST, KkButtonSize.MD, ghostFont * fontScale, 0.62f, height, 0.6f))
            } else {
                add(ReportAction(language.text(GameplayText.Reenter), GameplayInput.RestartRun, "kinetickk.gameplay.restart",
                    KkButtonVariant.PRIMARY, primarySize, primaryFont * fontScale, 1f, height, 0.6f))
            }
            if (withMenu) {
                add(ReportAction(language.text(OverlayRedesignText.Menu), GameplayInput.ExitToHome, "kinetickk.gameplay.exit",
                    KkButtonVariant.GHOST, KkButtonSize.XS, ReportMenuFont, 0f, 48.dp, 0.7f, ReportMenuSlackDp,
                    language.text(GameplayText.ReturnHome)))
            }
        }
    }
    val measurer = rememberKkCanvasMeasurer(scene.textScale)
    val reference = rememberKkCanvasMeasurer(1f)
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth.toFloat()
        val (widths, factor) = remember(actions, measurer, reference, density, available) {
            val gaps = (0 until actions.size - 1).sumOf { index -> reportActionGap(actions, index).toDouble() }.toFloat() * density
            // One px more than the padding guards against rounding when a shrunk label is measured again.
            val pads = FloatArray(actions.size) { index -> (actions[index].size.paddingDp + actions[index].slackDp) * density + 1f }
            val weights = FloatArray(actions.size) { index -> actions[index].weight }
            fun labels(with: CanvasTextMeasurer) = FloatArray(actions.size) { index -> reportActionLabelWidth(with, actions[index].label, actions[index].font) }
            val row = overlayRowWidths(labels(measurer), pads, weights, available - gaps)
            if (measurer.scale <= 1f || row.second >= 1f) return@remember row
            // Text widths are not exactly linear in the font size: above the default the labels
            // never end below the size the default text size gives them.
            val floor = overlayRowWidths(labels(reference), pads, weights, available - gaps).second / measurer.scale
            if (row.second >= floor) row else labels(measurer).let { grown -> FloatArray(actions.size) { grown[it] * floor + pads[it] } to floor }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions.forEachIndexed { index, action ->
                if (index > 0) Spacer(Modifier.width(reportActionGap(actions, index - 1).dp))
                OverlayButton(
                    action.label, { scene.onInput(action.input) },
                    Modifier.enter(scene, action.enterDelay).size((widths[index] / density).dp, action.height).testTag(action.tag),
                    action.variant, action.size, scene.actionsEnabled,
                    textScale = scene.textScale, fontSize = action.font * factor, contentDescription = action.contentDescription,
                )
            }
        }
    }
}

/** Gap after action [index]: 12 dp between actions, 10 dp before Menu. */
private fun reportActionGap(actions: List<ReportAction>, index: Int): Float =
    if (actions[index + 1].weight <= 0f) 10f else 12f

/** Width in px of an action label at board size [font] (the [OverlayButton] label style). */
private fun reportActionLabelWidth(measurer: CanvasTextMeasurer, label: String, font: Float): Float =
    measureKkText(measurer, label, measurer.typography.condStyle(font, trackingEm = 0.02f, lineHeightEm = 1f), uppercase = true).size.width.toFloat()

private const val ReportMenuFont = 14f
private const val ReportMenuSlackDp = 12f

@Composable
internal fun ReportMenu(scene: ReportScene, frame: OverlayFrame, modifier: Modifier, touch: Boolean = false) {
    val language = LocalAppLanguage.current
    val measurer = rememberKkCanvasMeasurer(scene.textScale)
    val density = LocalDensity.current.density
    val label = language.text(OverlayRedesignText.Menu)
    val width = remember(measurer, label, density) {
        kinetickk.ball.gameplay.interaction.overlay.overlayButtonWidth(measurer, label, KkButtonSize.XS, density, null) / density
    }
    OverlayButton(
        label, { scene.onInput(GameplayInput.ExitToHome) },
        modifier.enter(scene, 0.7f).size(width.dp + 8.dp, if (touch) 48.dp else 30.dp).testTag("kinetickk.gameplay.exit"),
        KkButtonVariant.GHOST, KkButtonSize.XS, scene.actionsEnabled, textScale = scene.textScale, fontSize = 14f,
        contentDescription = language.text(GameplayText.ReturnHome),
    )
}
