// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kinetickk.ball.gameplay.interaction.canvas.look.drawRelicIcon
import kinetickk.ball.gameplay.interaction.canvas.look.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.look.overlayIcon
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkTagVariant
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkRelicSlot
import kinetickk.foundation.design.kkPulse
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kinetickk.foundation.design.wideStyle
import kotlin.math.max
import kinetickk.ball.gameplay.interaction.overlay.*

@Composable
internal fun RelicPanel(
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
internal fun RelicMatrixRow(
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
    relic: kinetickk.ball.gameplay.interaction.canvas.look.OverlayRelicSlot?,
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
    val interactive = cardIndex >= 0 && state.enabled
    val grow = if (selected) 1.1f else 1f
    val paths = remember { KkPathCache(1) }
    val card = state.presentation.cards.getOrNull(cardIndex)
    val base = modifier.graphicsLayer {
        val p = ((state.entrance() - 100f - slot * 60f) / 450f).coerceIn(0f, 1f)
        alpha = (p / 0.5f).coerceIn(0f, 1f)
        translationY = (1f - KkEase.Pull.transform(p)) * 46f * density
    }
    val clickable = if (cardIndex >= 0) {
        base.testTag("kinetickk.gameplay.choice.${cardIndex + 1}")
            .rewardPreviewInput(interactive) { state.onSelected(cardIndex) }
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
