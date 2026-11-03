// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.canvas.look.overlayColor
import kinetickk.foundation.design.measureKkText
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.drawKkRelicSlot
import kinetickk.foundation.design.kkTagSize
import kinetickk.foundation.design.drawKkSynergyLink
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kinetickk.foundation.design.wideStyle
import kinetickk.ball.gameplay.interaction.canvas.look.OverlayRelicLink
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.overlay.*

/**
 * Compact relic matrix for relic offers (top right of the deal): count, diamonds with synergy
 * links, and for the lifted offer its target slot ring and the synergy it would form (dashed).
 */
@Composable
internal fun RewardRelicStrip(matrix: RewardRelicMatrix, preview: RewardRelicPreview?, state: RewardOverlayState, modifier: Modifier) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = rememberInterfaceTypography()
    val measurer = rememberKkCanvasMeasurer(state.textScale)
    val regular = state.mode == GameplayLayoutMode.REGULAR
    val dashed = rememberDashedStroke()
    val tagPaths = remember { KkPathCache(1) }
    val countLabel = language.text(OverlayRedesignText.Relics)
    Box(modifier.testTag("kinetickk.gameplay.rewards.relics")) {
        // Phones keep the strip to the header line: count left of the diamonds, no bracket tags.
        Row(
            Modifier.align(if (regular) Alignment.TopEnd else Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (regular) {
                OverlayText(language.text(OverlayRedesignText.Relics), typography.labelStyle(14f * state.textScale, color = Kk.Mute),
                    Modifier.padding(bottom = 2.dp), uppercase = true)
            }
            OverlayText("${matrix.equipped}/${matrix.maxSlots}", typography.wideStyle((if (regular) 22f else 15f) * state.textScale, tabular = true, color = Kk.Bone))
        }
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = matrix.names.joinToString(", ") }) {
            val slot = (if (regular) 34f else 22f) * density
            val pitch = slot + (if (regular) 14f else 8f) * density
            val n = matrix.slots.size
            val cy = size.height - slot * 0.5f - (if (regular) 6f else 4f) * density
            val centers = FloatArray(n) { index -> size.width - slot * 0.5f - 6f * density - (n - 1 - index) * pitch }
            val sizeDp = slot / density
            matrix.links.forEach { link ->
                val faded = preview?.removedLinks?.any(link::joinsSame) == true
                val color = link.overlayColor(roles).let { if (faded) it.copy(alpha = 0.2f) else it }
                link.slots.zipWithNext().forEach { (a, b) ->
                    drawKkSynergyLink(Offset(centers[a] + slot * 0.5f, cy), Offset(centers[b] - slot * 0.5f, cy), color)
                }
            }
            if (preview != null && preview.addedLinks.any { it.bracketed }) {
                // Dissonance brackets stay low and unlabelled; named pairings stack above with their tag.
                val legBottom = cy - slot * 0.5f - 3f * density
                val first = cy - slot * 0.5f - (if (regular) 12f else 8f) * density
                val dissonant = preview.addedLinks.filter { it.bracketed && it.pairing == null }
                    .mapIndexed { level, link -> RelicBracket(link, "", level, preview = true, faded = false) }
                val step = (if (regular) 7f else 4f) * density
                // The phone strip stacks its brackets a few dp apart: color alone marks interference there.
                drawRelicBrackets(measurer, dissonant, centers, first, step, legBottom, dashed, ticks = regular)
                // Named pairings stack their bracket lines closely above the dissonance; their tags share
                // one row above the lines (the board only), so two pairings never climb into the count.
                val namedLinks = preview.addedLinks.withIndex().filter { it.value.pairing != null }
                val named = namedLinks.mapIndexed { level, (_, link) -> RelicBracket(link, "", level, preview = true, faded = false) }
                val namedFirst = first - dissonant.size * step
                val namedStep = (if (regular) 7f else 6f) * density
                drawRelicBrackets(measurer, named, centers, namedFirst, namedStep, legBottom, dashed, ticks = regular)
                if (regular && named.isNotEmpty()) {
                    val labels = namedLinks.map { (index, link) -> link to link.bracketLabel(preview.addedNames.getOrElse(index) { "" }) }
                    val countSize = measureKkText(measurer, "${matrix.equipped}/${matrix.maxSlots}", measurer.typography.wideStyle(22f, tabular = true)).size
                    val countWidth = countSize.width + 10f * density +
                        measureKkText(measurer, countLabel, measurer.typography.labelStyle(14f), uppercase = true).size.width
                    drawRelicStripTags(measurer, labels, centers, namedFirst - (named.size - 1) * namedStep - 6f * density,
                        countSize.height + 4f * density, size.width - countWidth - 10f * density, dashed, tagPaths)
                }
            }
            val time = state.time.value
            matrix.slots.forEachIndexed { index, relic ->
                val target = preview != null && preview.targetSlot == index
                val ring = when {
                    preview == null || !target -> Color.Unspecified
                    preview.replace -> roles.threat
                    else -> roles.you
                }
                val center = Offset(centers[index], cy)
                val definition = matrix.definitions.getOrNull(index)
                if (relic != null && definition != null) {
                    drawRelicSlotRune(center, definition, matrix.policy, sizeDp, roles, time, ring)
                } else {
                    val incoming = preview?.incoming?.takeIf { target }
                    if (incoming != null) {
                        drawRelicSlotRune(center, incoming, matrix.policy, sizeDp, roles, time, ring, alpha = 0.6f)
                    } else {
                        drawKkRelicSlot(center, Color.Unspecified, sizeDp = sizeDp, ring = ring)
                    }
                }
            }
        }
    }
}

/**
 * The tags of the named pairings a lifted relic offer forms, in one row whose bottom is
 * [rowBottom]: each as near the middle of its bracket as the row allows, packed left to right.
 * When the row reaches up beside the relic count ([countBottom]) it stays left of [countLeft].
 */
private fun DrawScope.drawRelicStripTags(
    measurer: CanvasTextMeasurer,
    labels: List<Pair<OverlayRelicLink, String>>,
    centers: FloatArray,
    rowBottom: Float,
    countBottom: Float,
    countLeft: Float,
    dashed: Stroke,
    paths: KkPathCache,
) {
    val shown = labels.filter { (link, label) -> label.isNotEmpty() && link.slots.isNotEmpty() && link.slots.last() < centers.size }
        .sortedBy { (link, _) -> centers[link.first] + centers[link.last] }
    if (shown.isEmpty()) return
    val top = rowBottom - LinkTagHeightDp * density
    val widths = FloatArray(shown.size) { kkTagSize(measurer, shown[it].second, density, LinkTagHeightDp, linkTagFont(!shown[it].first.discovered)).width }
    val middles = FloatArray(shown.size) { (centers[shown[it].first.first] + centers[shown[it].first.last]) * 0.5f }
    val lefts = relicStripTagLefts(widths, middles, 6f * density, if (top < countBottom) countLeft else size.width)
    shown.forEachIndexed { index, (link, label) ->
        drawRelicLinkTag(measurer, label, Offset(lefts[index], top), link.overlayColor(measurer.roles), !link.discovered, dashed, paths)
    }
}

/**
 * Left edges of a row of tags of [widths], each centered on its [middles] entry (ascending) where
 * it can be: packed left to right [gap] apart, then shifted back inside [right], never past 0.
 */
internal fun relicStripTagLefts(widths: FloatArray, middles: FloatArray, gap: Float, right: Float): FloatArray {
    val lefts = FloatArray(widths.size)
    var cursor = 0f
    for (index in widths.indices) {
        lefts[index] = max(cursor, middles[index] - widths[index] * 0.5f)
        cursor = lefts[index] + widths[index] + gap
    }
    if (lefts.isEmpty()) return lefts
    val shift = min((lefts.last() + widths.last() - right).coerceAtLeast(0f), lefts.first().coerceAtLeast(0f))
    for (index in lefts.indices) lefts[index] -= shift
    return lefts
}
