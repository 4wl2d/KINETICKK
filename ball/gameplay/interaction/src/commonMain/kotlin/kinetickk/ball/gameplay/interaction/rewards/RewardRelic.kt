// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.canvas.overlayColor
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
import kinetickk.foundation.design.drawKkSynergyLink
import kinetickk.foundation.design.kkPulse
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kinetickk.foundation.design.wideStyle
import kinetickk.ball.gameplay.interaction.canvas.OverlaySynergyLink

/** One synergy bracket above the relic row: stacked [level], dashed when it is a [preview]. */
internal class RelicBracket(
    val link: OverlaySynergyLink,
    val name: String,
    val level: Int,
    val preview: Boolean,
    val faded: Boolean,
)

/**
 * Brackets for the current synergies (solid; [faded] when the selected choice breaks them) and
 * the synergies the selected choice would form (dashed, stacked above). Links whose slot spans
 * overlap take separate levels.
 */
internal fun relicBrackets(matrix: RewardRelicMatrix, preview: RewardRelicPreview?): List<RelicBracket> {
    val placed = mutableListOf<RelicBracket>()
    fun levelFor(link: OverlaySynergyLink, from: Int): Int {
        var level = from
        while (placed.any { it.level == level && it.link.first <= link.last && link.first <= it.link.last }) level++
        return level
    }
    matrix.links.forEachIndexed { index, link ->
        val faded = preview?.removedSynergies?.contains(link.definition.id) == true
        placed += RelicBracket(link, matrix.linkNames[index], levelFor(link, 0), preview = false, faded = faded)
    }
    val base = (placed.maxOfOrNull { it.level } ?: -1) + 1
    preview?.addedLinks?.forEachIndexed { index, link ->
        placed += RelicBracket(link, preview.addedNames.getOrElse(index) { link.definition.name }, levelFor(link, base), preview = true, faded = false)
    }
    return placed
}

/** Draws [brackets] over slots centered at [centers], legs reaching down to [legBottom]. */
internal fun DrawScope.drawRelicBrackets(
    measurer: CanvasTextMeasurer,
    brackets: List<RelicBracket>,
    centers: FloatArray,
    firstY: Float,
    levelStep: Float,
    legBottom: Float,
    dashed: Stroke,
) {
    brackets.forEach { bracket ->
        val slots = bracket.link.slots
        if (slots.isEmpty() || slots.last() >= centers.size) return@forEach
        val y = firstY - bracket.level * levelStep
        val color = bracket.link.definition.overlayColor().let { if (bracket.faded) it.copy(alpha = 0.2f) else it }
        val left = centers[slots.first()]
        val right = centers[slots.last()]
        drawKkSynergyBracket(measurer, left, right, y, color, bracket.preview, bracket.name.ifEmpty { null })
        val stroke = if (bracket.preview) dashed else kkStroke(2f * density)
        slots.forEach { slot ->
            val x = centers[slot]
            drawLine(color, Offset(x, y), Offset(x, legBottom), stroke.width, StrokeCap.Butt, stroke.pathEffect)
        }
    }
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
                val faded = preview?.removedSynergies?.contains(link.definition.id) == true
                val color = link.definition.overlayColor().let { if (faded) it.copy(alpha = 0.2f) else it }
                link.slots.zipWithNext().forEach { (a, b) ->
                    drawKkSynergyLink(Offset(centers[a] + slot * 0.5f, cy), Offset(centers[b] - slot * 0.5f, cy), color)
                }
            }
            if (preview != null && preview.addedLinks.isNotEmpty()) {
                val brackets = preview.addedLinks.mapIndexed { index, link ->
                    RelicBracket(link, if (regular) preview.addedNames.getOrElse(index) { "" } else "", index, preview = true, faded = false)
                }
                val first = cy - slot * 0.5f - (if (regular) 12f else 8f) * density
                drawRelicBrackets(measurer, brackets, centers, first, (if (regular) 30f else 6f) * density, cy - slot * 0.5f - 3f * density, dashed)
            }
            matrix.slots.forEachIndexed { index, relic ->
                val target = preview != null && preview.targetSlot == index
                val ring = when {
                    preview == null || !target -> Color.Unspecified
                    preview.replace -> roles.threat
                    else -> roles.you
                }
                if (relic != null) {
                    drawKkRelicSlot(Offset(centers[index], cy), relic.aspect.overlayColor(), relic.aspect.overlayIcon(), sizeDp, ring = ring)
                } else {
                    val incoming = preview?.incomingAspect?.takeIf { target }
                    if (incoming != null) {
                        drawKkRelicSlot(Offset(centers[index], cy), incoming.overlayColor(), incoming.overlayIcon(), sizeDp, ring = ring, alpha = 0.6f)
                    } else {
                        drawKkRelicSlot(Offset(centers[index], cy), Color.Unspecified, sizeDp = sizeDp, ring = ring)
                    }
                }
            }
        }
    }
}

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
        Rect(16f, 296f, 374f, 566f), 58f, 118f, 14f, 34f,
        Rect(16f, 580f, 374f, 832f), 18f, 13f, 56f, 26f,
    )
}

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
        val panel = presentation.relicPanel ?: preview?.panel ?: presentation.cards.firstOrNull()?.relicPreview?.panel
        val accent = panel?.aspect?.overlayColor() ?: roles.you
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
        RelicMatrixRow(matrix, preview, replace, spec, frame, state, typography, roles)
        RelicPreviewSide(preview, selectedCard, replace, spec, frame, state, typography, roles)
    }
}

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
    val color = panel.aspect?.overlayColor() ?: roles.you
    val diamond: @Composable () -> Unit = {
        Canvas(Modifier.size(frame.dp(spec.diamond)).semantics { contentDescription = panel.aspectLabel.orEmpty() }) {
            val c = center
            val half = size.minDimension * 0.5f
            rotate(state.time.value * 18f, c) {
                drawCircle(color.copy(alpha = 0.5f), half + 18f * frame.scale * density, c, style = dashed)
            }
            drawPath(paths.diamond(0, c.x, c.y, half, half), color)
            val inner = half - 5f * frame.scale * density * 1.414f
            drawPath(paths.diamond(1, c.x, c.y, inner, inner), Kk.Ink2)
            val icon = panel.aspect?.overlayIcon() ?: KkIcon.UI_PLUS
            drawKkIcon(icon, c, half * 0.78f, color)
        }
    }
    val tags: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            panel.aspectLabel?.let { OverlayTag(it, background = color, foreground = Kk.Ink, textScale = state.textScale) }
            panel.rank?.let { OverlayTag(it, variant = KkTagVariant.LINE, textScale = state.textScale) }
            if (panel.isNew) OverlayTag(language.text(OverlayRedesignText.NewKind), variant = KkTagVariant.LINE, textScale = state.textScale)
        }
    }
    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(frame.dp(12f))) {
            OverlayText(panel.name, typography.wideStyle(frame.sp(spec.nameSize, 16f), lineHeightEm = 1f, color = Kk.Bone), uppercase = true, maxLines = 3)
            OverlayText(panel.description, typography.bodyStyle(frame.sp(spec.bodySize, 12f), color = Kk.Bone), maxLines = spec.bodyLines)
        }
    }
    val effect: @Composable () -> Unit = {
        panel.effect?.let { effect ->
            Box(Modifier.fillMaxWidth().drawBehind { drawRect(Kk.Ink2) }.padding(horizontal = frame.dp(14f).coerceAtLeast(8.dp), vertical = frame.dp(12f).coerceAtLeast(6.dp))) {
                OverlayText(effect, typography.monoStyle(frame.sp(11f, 9f), color = roles.you), uppercase = true, maxLines = spec.bodyLines)
            }
        }
    }
    Column(
        modifier.graphicsLayer {
            val p = ((state.entrance() - 50f) / 520f).coerceIn(0f, 1f)
            alpha = (p / 0.6f).coerceIn(0f, 1f)
            translationX = -(1f - KkEase.Pull.transform(p)) * 90f * density
        }.testTag("kinetickk.gameplay.rewards.incoming"),
        verticalArrangement = Arrangement.spacedBy(frame.dp(if (spec.panelInline) 12f else 14f)),
    ) {
        if (spec.panelInline) {
            Row(horizontalArrangement = Arrangement.spacedBy(frame.dp(26f)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(frame.dp(18f))) { diamond() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(frame.dp(10f))) { tags(); text() }
            }
        } else {
            Box(Modifier.padding(start = frame.dp(18f), top = frame.dp(18f), bottom = frame.dp(8f))) { diamond() }
            tags()
            text()
        }
        effect()
    }
}

@Composable
private fun RelicMatrixRow(
    matrix: RewardRelicMatrix,
    preview: RewardRelicPreview?,
    replace: Boolean,
    spec: RelicSpec,
    frame: OverlayFrame,
    state: RewardOverlayState,
    typography: InterfaceTypography,
    roles: KkRolePalette,
) {
    val measurer = rememberKkCanvasMeasurer(state.textScale)
    val dashed = rememberDashedStroke()
    val cards = state.presentation.cards
    val n = matrix.slots.size.coerceAtLeast(1)
    val column = spec.matrix.width / n
    val brackets = remember(matrix, preview) { relicBrackets(matrix, preview) }
    Box(
        Modifier.offset(frame.x(spec.matrix.left), frame.y(spec.matrix.top))
            .size(frame.dp(spec.matrix.width), frame.dp(spec.matrix.height))
            .testTag("kinetickk.gameplay.rewards.matrix"),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = frame.scale * density
            val centers = FloatArray(n) { (it + 0.5f) * column * s }
            val legBottom = (spec.slotRowTop - spec.bracketStep) * s
            drawRelicBrackets(measurer, brackets, centers, legBottom - 16f * s, spec.bracketStep * 1.2f * s, legBottom, dashed)
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
                    typography = typography,
                    roles = roles,
                    modifier = Modifier.offset(frame.dp(column * slot), frame.dp(spec.slotRowTop - 14f))
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
    typography: InterfaceTypography,
    roles: KkRolePalette,
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
    Column(clickable.padding(vertical = frame.dp(14f)), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(frame.dp(14f))) {
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
            if (relic != null) {
                drawKkRelicSlot(c, relic.aspect.overlayColor(), relic.aspect.overlayIcon(), sizeDp, sizeDp * 0.36f)
            } else {
                drawKkRelicSlot(c, Color.Unspecified, sizeDp = sizeDp)
                drawKkIcon(KkIcon.UI_PLUS, c, sizeDp * 0.3f * density, Kk.Mute)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OverlayText(name, typography.condStyle(frame.sp(spec.slotNameSize, 12f), color = Kk.Bone), uppercase = true,
                maxLines = 2, align = TextAlign.Center)
            if (meta.isNotEmpty()) OverlayText(meta, typography.monoStyle(frame.sp(11f, 9f), color = Kk.Mute), uppercase = true)
        }
    }
}

@Composable
private fun RelicPreviewSide(
    preview: RewardRelicPreview?,
    selectedCard: RewardCardPresentation?,
    replace: Boolean,
    spec: RelicSpec,
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
        Modifier.offset(frame.x(spec.side.left), frame.y(spec.side.top)).width(frame.dp(spec.side.width))
            .heightIn(max = frame.dp(spec.side.height))
            .graphicsLayer {
                val p = ((state.entrance() - 100f) / 520f).coerceIn(0f, 1f)
                alpha = (p / 0.6f).coerceIn(0f, 1f)
                translationX = (1f - KkEase.Pull.transform(p)) * 110f * density
            },
        verticalArrangement = Arrangement.spacedBy(frame.dp(12f)),
    ) {
        OverlayText(preview?.kicker ?: presentation.heading, typography.labelStyle(frame.sp(15f, 11f), color = Kk.Mute),
            Modifier.testTag("kinetickk.gameplay.rewards.kicker"), uppercase = true, maxLines = 1)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(frame.dp(8f)),
        ) {
            preview?.rows?.forEach { row -> RelicPreviewRow(row, frame, spec, typography, roles) }
            selectedCard?.changes?.take(4)?.let { changes ->
                if (changes.isNotEmpty()) {
                    Column(Modifier.padding(top = frame.dp(6f)), verticalArrangement = Arrangement.spacedBy(frame.dp(6f))) {
                        changes.forEach { change -> RelicStatLine(change, frame, typography, roles) }
                    }
                }
            }
        }
        Spacer(Modifier.height(frame.dp(12f)))
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

@Composable
private fun RelicPreviewRow(row: RewardPreviewRow, frame: OverlayFrame, spec: RelicSpec, typography: InterfaceTypography, roles: KkRolePalette) {
    val color = row.tone.color(roles, row.color)
    val edge = if (row.sign == "=") Color.Transparent else color.copy(alpha = 0.6f)
    Row(
        Modifier.fillMaxWidth()
            .drawBehind {
                drawRect(Kk.Ink2)
                if (edge.alpha > 0f) drawRect(edge, style = kkStroke(1.5f * density))
            }
            .padding(horizontal = frame.dp(14f).coerceAtLeast(8.dp), vertical = frame.dp(12f).coerceAtLeast(6.dp))
            .testTag("kinetickk.gameplay.rewards.preview-row"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OverlayText(row.sign, typography.wideStyle(frame.sp(18f, 13f), color = color), Modifier.width(frame.dp(22f).coerceAtLeast(14.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OverlayText(row.title, typography.condStyle(frame.sp(spec.rowTitle, 15f), color = color), uppercase = true, maxLines = 2)
            row.detail?.let { OverlayText(it, typography.bodyStyle(frame.sp(spec.rowBody, 11f), color = Kk.Mute), maxLines = 3) }
        }
    }
}

@Composable
private fun RelicStatLine(change: RewardStatPresentation, frame: OverlayFrame, typography: InterfaceTypography, roles: KkRolePalette) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            OverlayText(change.name, typography.bodyStyle(frame.sp(14f, 11f), color = Kk.Bone))
            change.condition?.let { OverlayText(it, typography.monoStyle(frame.sp(9f, 8f), color = Kk.Mute), uppercase = true) }
        }
        if (change.before.isNotEmpty()) {
            OverlayText(change.before, typography.wideStyle(frame.sp(11f, 9f), tabular = true, color = Kk.Mute2), Modifier.padding(end = 8.dp, bottom = 1.dp))
        }
        OverlayText(change.after, typography.wideStyle(frame.sp(15f, 12f), tabular = true, color = if (change.improved) roles.you else roles.threat))
    }
}
