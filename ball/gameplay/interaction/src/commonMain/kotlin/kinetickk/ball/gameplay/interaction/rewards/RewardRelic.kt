// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.gameplay.interaction.canvas.drawRelicIcon
import kinetickk.ball.gameplay.interaction.canvas.overlayColor
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.foundation.design.drawKkThreatHatch
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.measureKkText
import kinetickk.ball.gameplay.interaction.canvas.overlayIcon
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkButtonSize
import kinetickk.foundation.design.KkButtonVariant
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStampVariant
import kinetickk.foundation.design.KkTagVariant
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkHalftone
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkRadialFade
import kinetickk.foundation.design.drawKkRelicSlot
import kinetickk.foundation.design.drawKkSynergyBracket
import kinetickk.foundation.design.drawKkTag
import kinetickk.foundation.design.kkTagSize
import kinetickk.foundation.design.drawKkSynergyLink
import kinetickk.foundation.design.kkPulse
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kinetickk.foundation.design.wideStyle
import kinetickk.ball.gameplay.interaction.canvas.OverlayRelicLink
import kotlin.math.max
import kotlin.math.min

/** One relic link bracket above the relic row: stacked [level], dashed when it is a [preview]. */
internal class RelicBracket(
    val link: OverlayRelicLink,
    val name: String,
    val level: Int,
    val preview: Boolean,
    val faded: Boolean,
)

/**
 * Brackets for the current links (solid; [faded] when the selected choice breaks them) and the
 * links the selected choice would form (dashed, stacked above). Only named pairings and
 * dissonance take a bracket: harmony reads as its multiplier alone. Links whose slot spans
 * overlap take separate levels.
 */
internal fun relicBrackets(matrix: RewardRelicMatrix, preview: RewardRelicPreview?): List<RelicBracket> {
    val placed = mutableListOf<RelicBracket>()
    fun levelFor(link: OverlayRelicLink, from: Int): Int {
        var level = from
        while (placed.any { it.level == level && it.link.first <= link.last && link.first <= it.link.last }) level++
        return level
    }
    matrix.links.forEachIndexed { index, link ->
        if (!link.bracketed) return@forEachIndexed
        val faded = preview?.removedLinks?.any(link::joinsSame) == true
        placed += RelicBracket(link, link.bracketLabel(matrix.linkNames[index]), levelFor(link, 0), preview = false, faded = faded)
    }
    val base = (placed.maxOfOrNull { it.level } ?: -1) + 1
    preview?.addedLinks?.forEachIndexed { index, link ->
        if (!link.bracketed) return@forEachIndexed
        placed += RelicBracket(link, link.bracketLabel(preview.addedNames.getOrElse(index) { "" }), levelFor(link, base), preview = true, faded = false)
    }
    return placed
}

/** Named pairings and dissonance draw a bracket; harmony shows only as a multiplier. */
internal val OverlayRelicLink.bracketed: Boolean get() = pairing != null || kind == RelicLinkKind.DISSONANCE

/** A named pairing's bracket tag carries its sign ("+ Name", "− ?"); dissonance keeps its word. */
private fun OverlayRelicLink.bracketLabel(name: String): String = when {
    name.isEmpty() || pairing == null -> name
    kind == RelicLinkKind.DISCORD -> "− $name"
    else -> "+ $name"
}

/** Link tag metrics (dp, board sp): an unmet pairing's "+ ?" takes a larger mark so it never reads as a speck. */
private const val LinkTagHeightDp = 20f
private const val LinkTagFont = 12f
private const val UnmetLinkTagFont = 15f

private fun linkTagFont(unmet: Boolean): Float = if (unmet) UnmetLinkTagFont else LinkTagFont

/**
 * A link's tag at [topLeft]: a met pairing on a plate in its [color]; an unmet one ("+ ?", "− ?")
 * in its color on a dark plate inside a dashed outline in the same color. [paths] slot 0 holds the outline.
 */
private fun DrawScope.drawRelicLinkTag(
    measurer: CanvasTextMeasurer,
    label: String,
    topLeft: Offset,
    color: Color,
    unmet: Boolean,
    dashed: Stroke,
    paths: KkPathCache,
) {
    val rect = drawKkTag(measurer, label, topLeft, heightDp = LinkTagHeightDp, fontSize = linkTagFont(unmet),
        background = if (unmet) Kk.Ink2 else color, foreground = if (unmet) color else Kk.Ink)
    if (unmet) {
        val inset = 0.75f * density
        drawPath(paths.slab(0, rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, 6f * density), color,
            style = Stroke(1.5f * density, cap = StrokeCap.Butt, pathEffect = dashed.pathEffect))
    }
}

/**
 * Draws [brackets] over slots centered at [centers], legs reaching down to [legBottom]. [ticks]
 * marks interference with hatch ticks along its line (left out where brackets stack too closely).
 */
internal fun DrawScope.drawRelicBrackets(
    measurer: CanvasTextMeasurer,
    brackets: List<RelicBracket>,
    centers: FloatArray,
    firstY: Float,
    levelStep: Float,
    legBottom: Float,
    dashed: Stroke,
    paths: KkPathCache? = null,
    ticks: Boolean = true,
) {
    brackets.forEach { bracket ->
        val slots = bracket.link.slots
        if (slots.isEmpty() || slots.last() >= centers.size) return@forEach
        val y = firstY - bracket.level * levelStep
        val color = bracket.link.overlayColor(measurer.roles).let { if (bracket.faded) it.copy(alpha = 0.2f) else it }
        val left = centers[slots.first()]
        val right = centers[slots.last()]
        // An unmet pairing and every preview are dashed; interference carries hatch ticks along its line.
        val unmet = !bracket.link.discovered
        drawKkSynergyBracket(measurer, left, right, y, color, bracket.preview || unmet, null)
        if (ticks && bracket.link.interferes) {
            val tick = 5f * density
            var x = left + 8f * density
            while (x < right - 6f * density) {
                drawLine(color, Offset(x, y + tick * 0.5f), Offset(x + tick * 0.6f, y - tick * 0.5f), 1.5f * density)
                x += 9f * density
            }
        }
        if (bracket.name.isNotEmpty() && paths != null) {
            // Every met link tag sits on a plate in its color (the preview one too: its bracket is
            // dashed, its label stays a solid tag); an unmet one is outlined dashed. Kept inside the canvas.
            val tag = kkTagSize(measurer, bracket.name, density, LinkTagHeightDp, linkTagFont(unmet))
            val x = ((left + right) * 0.5f - tag.width * 0.5f).coerceIn(0f, (size.width - tag.width).coerceAtLeast(0f))
            drawRelicLinkTag(measurer, bracket.name, Offset(x, y - 6f * density - tag.height), color, unmet, dashed, paths)
        }
        val stroke = if (bracket.preview || unmet) dashed else kkStroke(2f * density)
        slots.forEach { slot ->
            val x = centers[slot]
            drawLine(color, Offset(x, y), Offset(x, legBottom), stroke.width, StrokeCap.Butt, stroke.pathEffect)
        }
    }
}

/** A link's hue under the color-vision preset: named concord in bone, interference in the threat role. */
internal fun OverlayRelicLink.overlayColor(roles: KkRolePalette): Color = when (kind) {
    RelicLinkKind.CONCORD -> Kk.Bone
    RelicLinkKind.HARMONY -> Kk.Mute
    RelicLinkKind.DISCORD -> roles.threat
    RelicLinkKind.DISSONANCE -> kkMix(roles.threat, Kk.Ink, 0.25f)
}

/**
 * A bound relic in a matrix slot: its rim and its own rune in the aspect hue under the color-vision
 * preset. The rank reads as text beside the slot, never as markers on the small rune.
 */
internal fun DrawScope.drawRelicSlotRune(
    center: Offset,
    relic: RelicDefinition,
    policy: RelicPolicy,
    sizeDp: Float,
    roles: KkRolePalette,
    time: Float,
    ring: Color = Color.Unspecified,
    alpha: Float = 1f,
) {
    val hue = relic.aspect.overlayColor(roles)
    drawKkRelicSlot(center, hue, null, sizeDp, ring = ring, alpha = alpha)
    drawRelicIcon(relic, policy, center, sizeDp * density * 0.25f, time = time, alpha = alpha, accent = hue)
}

@Composable
private fun rememberDashedStroke(): Stroke {
    val density = LocalDensity.current.density
    return remember(density) {
        Stroke(2f * density, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * density, 4f * density)))
    }
}

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

/** The smallest share of its size the incoming relic panel's copy shrinks to. */
private const val RelicPanelMinFit = 0.6f

/** Board placement of the relic bind layout for one layout mode (board px). */
private class RelicSpec(
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
 * action (Replace in the threat color, Meld, Salvage).
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
private const val RelicSlotGap = 14f

/** Board px the rank labels keep above the matrix bottom (the side column keeps its own gap below it). */
private const val RelicRankClearance = 8f

/**
 * Where the relic row ends and the preview column starts (board px): the matrix grows to hold
 * the slot row with its labels at larger text, and a preview column below it (portrait) moves
 * down with it so the rank labels never run into its heading. [sideShift] is how far it moved.
 */
private class RelicRowPlacement(val matrixHeight: Float, val sideTop: Float, val sideShift: Float)

private fun relicRowPlacement(spec: RelicSpec, frame: OverlayFrame, text: RelicSlotText, density: Float): RelicRowPlacement {
    val labels = (text.nameHeight + 4f * density + text.rankHeight) / (frame.scale * density)
    val rowBottom = spec.slotRowTop + spec.slotSize + RelicSlotGap + labels
    val matrixHeight = max(spec.matrix.height, rowBottom + RelicRankClearance)
    val sideBelow = spec.side.top >= spec.matrix.bottom
    val sideTop = if (sideBelow) max(spec.side.top, spec.matrix.top + matrixHeight + spec.side.top - spec.matrix.bottom) else spec.side.top
    return RelicRowPlacement(matrixHeight, sideTop, sideTop - spec.side.top)
}

/** Board px of the extra gap above the preview column's button; a pushed-down column gives it up first. */
private const val RelicButtonGap = 12f

@Composable
private fun RelicPanel(
    panel: RewardRelicPanel,
    spec: RelicSpec,
    frame: OverlayFrame,
    typography: InterfaceTypography,
    state: RewardOverlayState,
    modifier: Modifier,
) {
    val roles = LocalKkRolePalette.current
    val language = LocalAppLanguage.current
    val dashed = rememberDashedStroke()
    val paths = remember { KkPathCache(2) }
    val color = panel.aspect?.overlayColor(roles) ?: roles.you
    val policy = state.presentation.relicMatrix?.policy
    // The panel keeps to its board rect: at large text sizes its copy shrinks (to 60 %) until the
    // panel fits above the relic row.
    val scroll = rememberScrollState()
    var fit by remember(panel, frame.scale, state.textScale) { mutableFloatStateOf(1f) }
    LaunchedEffect(scroll.maxValue, fit) {
        if (scroll.maxValue > 0 && fit > RelicPanelMinFit) fit = max(RelicPanelMinFit, fit - 0.05f)
    }
    // Everything in the panel shrinks with its copy: the rune diamond and the tags too, so the
    // upside never ends up smaller than the tags above it.
    val diamond: @Composable () -> Unit = {
        Canvas(Modifier.size(frame.dp(spec.diamond) * fit).semantics { contentDescription = panel.aspectLabel.orEmpty() }) {
            val c = center
            val half = size.minDimension * 0.5f
            rotate(state.time.value * 18f, c) {
                drawCircle((if (panel.heirloom) roles.gold.base else color).copy(alpha = 0.5f), half + 18f * frame.scale * density, c, style = dashed)
            }
            drawPath(paths.diamond(0, c.x, c.y, half, half), color)
            val inner = half - 5f * frame.scale * density * 1.414f
            drawPath(paths.diamond(1, c.x, c.y, inner, inner), Kk.Ink2)
            val relic = panel.relic
            if (relic != null && policy != null) {
                // The relic's own rune (the rank tag names its rank): four relics of one aspect never look alike.
                drawRelicIcon(relic, policy, c, half * 0.46f, time = state.time.value, accent = color)
            } else {
                drawKkIcon(panel.aspect?.overlayIcon() ?: KkIcon.UI_PLUS, c, half * 0.78f, color)
            }
        }
    }
    val tags: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val tagScale = state.textScale * fit
            if (panel.heirloom) OverlayTag(language.text(OverlayRedesignText.Heirloom), background = roles.gold.base, foreground = Kk.Ink, textScale = tagScale)
            panel.aspectLabel?.let { OverlayTag(it, background = color, foreground = Kk.Ink, textScale = tagScale) }
            panel.rank?.let { OverlayTag(it, variant = KkTagVariant.LINE, textScale = tagScale) }
            if (panel.isNew) OverlayTag(language.text(OverlayRedesignText.NewKind), variant = KkTagVariant.LINE, textScale = tagScale)
        }
    }
    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(frame.dp(12f))) {
            OverlayFitText(panel.name, typography.wideStyle(frame.sp(spec.nameSize, 16f) * fit, lineHeightEm = 1f,
                color = if (panel.heirloom) roles.gold.base else Kk.Bone), uppercase = true, maxLines = 2, minScale = 0.6f)
            OverlayRichText(rewardRichText(panel.description, Kk.Bone, panel.values, color),
                typography.bodyStyle(frame.sp(spec.bodySize, 12f) * fit, color = Kk.Bone2))
        }
    }
    val cost: @Composable () -> Unit = {
        // The mandatory cost of the relic: a threat-striped block under its label.
        panel.liability?.let { liability ->
            Column(
                Modifier.fillMaxWidth().drawBehind {
                    drawRect(Kk.Ink2)
                    drawRect(roles.threat, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                }.padding(start = frame.dp(16f).coerceAtLeast(11.dp), end = frame.dp(14f).coerceAtLeast(8.dp),
                    top = frame.dp(10f).coerceAtLeast(5.dp), bottom = frame.dp(12f).coerceAtLeast(6.dp)),
                verticalArrangement = Arrangement.spacedBy(frame.dp(4f)),
            ) {
                OverlayText(language.text(OverlayRedesignText.Cost),
                    typography.monoStyle(frame.sp(10f, 8.5f) * fit, weight = FontWeight.Bold, color = roles.threat), uppercase = true)
                OverlayRichText(rewardRichText(liability, roles.threat), typography.bodyStyle(frame.sp(spec.bodySize - 2f, 11f) * fit, color = roles.threat))
            }
        }
    }
    val build: @Composable () -> Unit = {
        // Keys (shared ones harmonize, opposed ones interfere) and the tags the relic attunes.
        if (panel.keys.isNotEmpty() || (panel.attuned.isNotEmpty() && state.mode == GameplayLayoutMode.REGULAR)) {
            Column(verticalArrangement = Arrangement.spacedBy(frame.dp(8f))) {
                if (panel.keys.isNotEmpty()) RelicKeyRow(panel.keys, typography, frame.sp(11f, 9f) * fit, roles)
                // Phones keep the keys; the attuned tags read on the board (and in the Codex).
                if (panel.attuned.isNotEmpty() && state.mode == GameplayLayoutMode.REGULAR) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OverlayText(language.text(OverlayRedesignText.Attuned), typography.monoStyle(frame.sp(10f, 8.5f) * fit, color = Kk.Mute),
                            uppercase = true)
                        RewardChipFlow(panel.attuned, if (frame.scale >= 0.9f) RewardChipRegular else RewardChipCompact, state.textScale * fit,
                            Modifier.weight(1f), maxRows = 2)
                    }
                }
            }
        }
    }
    Column(
        modifier.heightIn(max = frame.dp(spec.panel.height)).graphicsLayer {
            val p = ((state.entrance() - 50f) / 520f).coerceIn(0f, 1f)
            alpha = (p / 0.6f).coerceIn(0f, 1f)
            translationX = -(1f - KkEase.Pull.transform(p)) * 90f * density
        }.testTag("kinetickk.gameplay.rewards.incoming").verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(frame.dp(if (spec.panelInline) 12f else 14f)),
    ) {
        if (spec.panelInline) {
            Row(horizontalArrangement = Arrangement.spacedBy(frame.dp(26f)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(frame.dp(18f) * fit)) { diamond() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(frame.dp(10f))) { tags(); text() }
            }
        } else {
            Box(Modifier.padding(start = frame.dp(18f) * fit, top = frame.dp(18f) * fit, bottom = frame.dp(8f))) { diamond() }
            tags()
            text()
        }
        cost()
        build()
    }
}

/** A relic's keys as mono words: shared ones in bone, opposed ones underlined in the threat role. */
@Composable
private fun RelicKeyRow(keys: List<RewardRelicKey>, typography: InterfaceTypography, fontSize: Float, roles: KkRolePalette) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { key ->
            val color = when (key.state) {
                RewardKeyState.OPPOSED -> roles.threat
                RewardKeyState.SHARED -> Kk.Bone
                RewardKeyState.NEUTRAL -> Kk.Mute
            }
            val style = typography.monoStyle(fontSize, weight = FontWeight.Bold, color = color)
            OverlayText(key.label, if (key.state == RewardKeyState.OPPOSED) style.copy(textDecoration = TextDecoration.Underline) else style,
                uppercase = true)
        }
    }
}

@Composable
private fun RelicMatrixRow(
    matrix: RewardRelicMatrix,
    preview: RewardRelicPreview?,
    replace: Boolean,
    spec: RelicSpec,
    placement: RelicRowPlacement,
    frame: OverlayFrame,
    state: RewardOverlayState,
    roles: KkRolePalette,
    slotText: RelicSlotText,
) {
    val measurer = rememberKkCanvasMeasurer(state.textScale)
    val dashed = rememberDashedStroke()
    val cards = state.presentation.cards
    val n = matrix.slots.size.coerceAtLeast(1)
    val column = spec.matrix.width / n
    val brackets = remember(matrix, preview) { relicBrackets(matrix, preview) }
    val tagPaths = remember { KkPathCache(1) }
    Box(
        Modifier.offset(frame.x(spec.matrix.left), frame.y(spec.matrix.top))
            .size(frame.dp(spec.matrix.width), frame.dp(placement.matrixHeight))
            .testTag("kinetickk.gameplay.rewards.matrix"),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = frame.scale * density
            val centers = FloatArray(n) { (it + 0.5f) * column * s }
            val legBottom = (spec.slotRowTop - spec.bracketStep) * s
            drawRelicBrackets(measurer, brackets, centers, legBottom - 16f * s, spec.bracketStep * 1.2f * s, legBottom, dashed, tagPaths)
        }
        matrix.slots.forEachIndexed { slot, relic ->
            val cardIndex = cards.indexOfFirst { it.choice.relicSlot == slot }
            key(slot, cardIndex) {
                RelicSlotButton(
                    slot = slot,
                    relic = relic,
                    name = matrix.names[slot],
                    meta = matrix.ranks[slot],
                    cardIndex = cardIndex,
                    selected = cardIndex >= 0 && state.selected == cardIndex,
                    replace = replace,
                    spec = spec,
                    frame = frame,
                    state = state,
                    roles = roles,
                    slotText = slotText,
                    modifier = Modifier.offset(frame.dp(column * slot), frame.dp(spec.slotRowTop - RelicSlotGap))
                        .width(frame.dp(column)),
                )
            }
        }
    }
}

@Composable
private fun RelicSlotButton(
    slot: Int,
    relic: kinetickk.ball.gameplay.interaction.canvas.OverlayRelicSlot?,
    name: String,
    meta: String,
    cardIndex: Int,
    selected: Boolean,
    replace: Boolean,
    spec: RelicSpec,
    frame: OverlayFrame,
    state: RewardOverlayState,
    roles: KkRolePalette,
    slotText: RelicSlotText,
    modifier: Modifier,
) {
    val language = LocalAppLanguage.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val interactive = cardIndex >= 0 && state.enabled
    LaunchedEffect(hovered, focused) { if (interactive && (hovered || focused)) state.onSelected(cardIndex) }
    val grow by animateFloatAsState(if (selected) 1.1f else 1f, tween(200, easing = KkEase.Pull), label = "slotGrow")
    val paths = remember { KkPathCache(1) }
    val card = state.presentation.cards.getOrNull(cardIndex)
    val base = modifier.graphicsLayer {
        val p = ((state.entrance() - 100f - slot * 60f) / 450f).coerceIn(0f, 1f)
        alpha = (p / 0.5f).coerceIn(0f, 1f)
        translationY = (1f - KkEase.Pull.transform(p)) * 46f * density
    }
    val clickable = if (cardIndex >= 0) {
        base.testTag("kinetickk.gameplay.choice.${cardIndex + 1}")
            .semantics { stateDescription = listOf(name, meta).filter(String::isNotEmpty).joinToString(", ") }
            .clickable(
                interactionSource = source, indication = null, enabled = interactive, role = Role.Button,
                onClickLabel = language.text(GameplayText.SelectChoice, cardIndex + 1, card?.title.orEmpty()),
                onClick = { state.onSelect(cardIndex) },
            )
    } else base.semantics { contentDescription = name }
    Column(clickable.padding(vertical = frame.dp(RelicSlotGap)), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(frame.dp(RelicSlotGap))) {
        Canvas(Modifier.size(frame.dp(spec.slotSize))) {
            val c = center
            val half = size.minDimension * 0.5f * grow
            if (selected) {
                val ring = if (replace) roles.threat else roles.you
                val pulse = 0.65f + 0.35f * kkPulse(state.time.value, 1.1f)
                val r = half + 10f * frame.scale * density
                drawPath(paths.diamond(0, c.x, c.y, r, r), ring, alpha = pulse)
            } else if (hovered && interactive) {
                val r = half + 6f * frame.scale * density
                drawPath(paths.diamond(0, c.x, c.y, r, r), Kk.Bone, alpha = 0.35f)
            }
            val sizeDp = half * 2f / density
            val definition = state.presentation.relicMatrix?.definitions?.getOrNull(slot)
            if (relic != null && definition != null) {
                drawRelicSlotRune(c, definition, state.presentation.relicMatrix.policy, sizeDp, roles, state.time.value)
            } else if (relic != null) {
                drawKkRelicSlot(c, relic.aspect.overlayColor(roles), relic.aspect.overlayIcon(), sizeDp, sizeDp * 0.36f)
            } else {
                drawKkRelicSlot(c, Color.Unspecified, sizeDp = sizeDp)
                drawKkIcon(KkIcon.UI_PLUS, c, sizeDp * 0.3f * density, Kk.Mute)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Every name takes the same box (one size, the tallest name's height), so the rank
            // labels of the row sit on one baseline and names keep clear of their neighbours.
            val density = LocalDensity.current.density
            Box(Modifier.width(slotText.nameWidth.dp).height((slotText.nameHeight / density).dp), contentAlignment = Alignment.TopCenter) {
                OverlayFitText(name, slotText.name.copy(color = Kk.Bone), Modifier.testTag("kinetickk.gameplay.rewards.slot-name"),
                    uppercase = true, maxLines = 2, align = TextAlign.Center)
            }
            if (meta.isNotEmpty()) {
                OverlayText(meta, slotText.rank.copy(color = Kk.Mute), Modifier.testTag("kinetickk.gameplay.rewards.slot-rank"), uppercase = true)
            }
        }
    }
}

@Composable
private fun RelicPreviewSide(
    preview: RewardRelicPreview?,
    selectedCard: RewardCardPresentation?,
    replace: Boolean,
    spec: RelicSpec,
    placement: RelicRowPlacement,
    frame: OverlayFrame,
    state: RewardOverlayState,
    typography: InterfaceTypography,
    roles: KkRolePalette,
) {
    val presentation = state.presentation
    val fallback = presentation.cards.firstOrNull()?.relicPreview
    val primaryLabel = preview?.primaryLabel ?: fallback?.primaryLabel.orEmpty()
    val threat = replace && (selectedCard?.action?.tone ?: RewardTone.THREAT) == RewardTone.THREAT
    Column(
        Modifier.offset(frame.x(spec.side.left), frame.y(placement.sideTop)).width(frame.dp(spec.side.width))
            .heightIn(max = frame.dp(spec.side.bottom - placement.sideTop))
            .graphicsLayer {
                val p = ((state.entrance() - 100f) / 520f).coerceIn(0f, 1f)
                alpha = (p / 0.6f).coerceIn(0f, 1f)
                translationX = (1f - KkEase.Pull.transform(p)) * 110f * density
            },
        verticalArrangement = Arrangement.spacedBy(frame.dp(12f)),
    ) {
        OverlayFitText(preview?.kicker ?: presentation.heading, typography.labelStyle(frame.sp(15f, 11f), color = Kk.Mute),
            Modifier.fillMaxWidth().testTag("kinetickk.gameplay.rewards.kicker"), uppercase = true, maxLines = 1)
        RelicPreviewList(
            rows = preview?.rows.orEmpty(),
            changes = selectedCard?.changes.orEmpty().take(4),
            frame = frame,
            spec = spec,
            typography = typography,
            roles = roles,
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
        )
        // The column keeps its bottom; when the relic row pushes its top down, the gap above the
        // button shrinks first so the preview rows keep the height they have at the spec placement.
        Spacer(Modifier.height(frame.dp((RelicButtonGap - placement.sideShift).coerceAtLeast(0f))))
        val selected = state.selected
        OverlayButton(
            primaryLabel,
            { selected?.let(state.onSelect) },
            Modifier.fillMaxWidth().height(frame.dp(spec.buttonHeight).coerceAtLeast(48.dp)).testTag("kinetickk.gameplay.take"),
            if (threat) KkButtonVariant.HAZARD else KkButtonVariant.PRIMARY,
            if (spec.buttonHeight >= 64f) KkButtonSize.LG else KkButtonSize.MD,
            state.enabled && selected != null,
            textScale = state.textScale,
            stateDescription = selectedCard?.title,
            fontSize = frame.sp(spec.buttonFont, 20f) / state.textScale,
        )
    }
}

/** Which parts of the signed preview show: rows, how many keep their detail line, stat lines. */
internal data class RelicPreviewPlan(val rows: Int, val detailRows: Int, val stats: Int)

/**
 * Plans from the fullest to the tersest. The preview drops stat lines first, then the detail
 * lines from the last row up, then trailing rows, so it always fits its column whole.
 */
internal fun relicPreviewPlans(rowCount: Int, statCount: Int): List<RelicPreviewPlan> = buildList {
    add(RelicPreviewPlan(rowCount, rowCount, statCount))
    for (stats in statCount - 1 downTo 0) add(RelicPreviewPlan(rowCount, rowCount, stats))
    for (details in rowCount - 1 downTo 0) add(RelicPreviewPlan(rowCount, details, 0))
    for (rows in rowCount - 1 downTo 1) add(RelicPreviewPlan(rows, 0, 0))
}

/** The signed rows and stat lines, trimmed by [relicPreviewPlans] to the height it is given. */
@Composable
private fun RelicPreviewList(
    rows: List<RewardPreviewRow>,
    changes: List<RewardStatPresentation>,
    frame: OverlayFrame,
    spec: RelicSpec,
    typography: InterfaceTypography,
    roles: KkRolePalette,
    modifier: Modifier,
) {
    val plans = remember(rows.size, changes.size) { relicPreviewPlans(rows.size, changes.size) }
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        var chosen: List<androidx.compose.ui.layout.Placeable> = emptyList()
        var height = 0
        for (index in plans.indices) {
            val plan = plans[index]
            val measured = subcompose(index) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(frame.dp(8f))) {
                    for (row in 0 until plan.rows) RelicPreviewRow(rows[row], row < plan.detailRows, frame, spec, typography, roles)
                    if (plan.stats > 0) {
                        Column(Modifier.padding(top = frame.dp(6f)), verticalArrangement = Arrangement.spacedBy(frame.dp(6f))) {
                            for (stat in 0 until plan.stats) RelicStatLine(changes[stat], frame, typography, roles)
                        }
                    }
                }
            }.map { it.measure(loose) }
            height = measured.maxOfOrNull { it.height } ?: 0
            chosen = measured
            if (height <= constraints.maxHeight) break
        }
        layout(constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: (chosen.maxOfOrNull { it.width } ?: 0),
            height.coerceAtMost(constraints.maxHeight)) {
            chosen.forEach { it.place(0, 0) }
        }
    }
}

@Composable
private fun RelicPreviewRow(
    row: RewardPreviewRow,
    detail: Boolean,
    frame: OverlayFrame,
    spec: RelicSpec,
    typography: InterfaceTypography,
    roles: KkRolePalette,
) {
    val color = row.tone.color(roles, row.color)
    val kept = row.sign == "="
    val bar = if (kept && row.tone != RewardTone.THREAT) Kk.Line2 else color
    val interferes = row.tone == RewardTone.THREAT
    val dashed = rememberDashedStroke()
    Row(
        Modifier.fillMaxWidth()
            .drawBehind {
                drawRect(Kk.Ink2)
                // An unmet pairing: hatched when it interferes, outlined dashed when it amplifies.
                if (row.unknown) {
                    if (interferes) drawKkThreatHatch(androidx.compose.ui.geometry.Rect(Offset.Zero, size), roles)
                    else drawRect(color.copy(alpha = 0.7f), style = dashed)
                }
                drawRect(bar, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
            }
            .padding(start = frame.dp(16f).coerceAtLeast(10.dp), end = frame.dp(14f).coerceAtLeast(8.dp),
                top = frame.dp(10f).coerceAtLeast(5.dp), bottom = frame.dp(11f).coerceAtLeast(6.dp))
            .testTag("kinetickk.gameplay.rewards.preview-row"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OverlayText(row.sign, typography.wideStyle(frame.sp(18f, 13f), color = if (kept) Kk.Mute else color), Modifier.width(frame.dp(22f).coerceAtLeast(14.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            row.kicker?.let { OverlayFitText(it, typography.monoStyle(frame.sp(10f, 8.5f), color = Kk.Mute), uppercase = true, maxLines = 1) }
            OverlayFitText(rewardKeepUnits(row.title), typography.condStyle(frame.sp(spec.rowTitle, 15f), color = color), uppercase = true, maxLines = 2)
            if (detail) row.detail?.let { OverlayFitText(rewardKeepUnits(it), typography.bodyStyle(frame.sp(spec.rowBody, 11f), color = Kk.Mute), maxLines = 3) }
        }
        if (row.after != null) {
            // The value now (muted) and after the choice (in the row's color): never an arrow.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.before?.takeIf { it != row.after }?.let {
                    OverlayText(it, typography.wideStyle(frame.sp(11f, 9f), tabular = true, color = Kk.Mute2), Modifier.padding(bottom = 1.dp))
                }
                OverlayText(row.after, typography.wideStyle(frame.sp(15f, 12f), tabular = true, color = if (kept) Kk.Bone else color))
            }
        }
    }
}

@Composable
private fun RelicStatLine(change: RewardStatPresentation, frame: OverlayFrame, typography: InterfaceTypography, roles: KkRolePalette) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            OverlayFitText(change.name, typography.bodyStyle(frame.sp(14f, 11f), color = Kk.Bone), maxLines = 2)
            change.condition?.let { OverlayFitText(it, typography.monoStyle(frame.sp(9f, 8f), color = Kk.Mute), uppercase = true, maxLines = 2) }
        }
        if (change.before.isNotEmpty()) {
            OverlayText(change.before, typography.wideStyle(frame.sp(11f, 9f), tabular = true, color = Kk.Mute2), Modifier.padding(end = 8.dp, bottom = 1.dp))
        }
        OverlayText(change.after, typography.wideStyle(frame.sp(15f, 12f), tabular = true, color = if (change.improved) roles.you else roles.threat))
    }
}
