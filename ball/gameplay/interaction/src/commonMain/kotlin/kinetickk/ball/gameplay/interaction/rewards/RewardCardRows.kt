// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.gameplay.interaction.canvas.look.drawRelicIcon
import kinetickk.ball.gameplay.interaction.canvas.look.elementFill
import kinetickk.ball.gameplay.interaction.canvas.look.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.look.drawActiveItemMark
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.drawKkHatch
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkPattern
import kinetickk.foundation.design.drawKkSheared
import kinetickk.ball.gameplay.interaction.canvas.look.drawItemIcon
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.KK_CARD_LABEL_CONTRAST
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkSpectrum
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.kkLiftContrast
import kinetickk.foundation.design.drawKkIconPlate
import kinetickk.foundation.design.drawKkRadialFade
import kinetickk.foundation.design.kkLoop
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.wideStyle
import kotlin.math.max
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.overlay.*

/** A relic's keys as mono words: shared with a bound relic in bone, opposed by one underlined in the threat role. */
@Composable
internal fun RewardKeyWords(keys: List<RewardRelicKey>, typography: InterfaceTypography, fontSize: Float, roles: KkRolePalette) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        keys.forEach { key ->
            val color = when (key.state) {
                RewardKeyState.OPPOSED -> roles.threat
                RewardKeyState.SHARED -> Kk.Bone
                RewardKeyState.NEUTRAL -> Kk.Mute
            }
            val style = typography.monoStyle(fontSize, weight = FontWeight.Bold, color = color).let {
                if (key.state == RewardKeyState.OPPOSED) it.copy(textDecoration = TextDecoration.Underline) else it
            }
            OverlayFitText(key.label, style, uppercase = true, maxLines = 1, minScale = 0.6f)
        }
    }
}

/**
 * An item's mechanics: each TRIGGER line (mono caps behind a bar in its hue, [RewardRuleLine.hue])
 * heads its EFFECT line (body, numbers in that hue); rules read like effects. The COST lines close
 * the list in one block behind a threat stripe, under [costLabel] ("Cost") when there is room. A
 * suppressed line is struck through in the threat role.
 */
@Composable
internal fun RewardRuleRows(
    lines: List<RewardRuleLine>,
    typeHue: Color,
    bodySize: Float,
    triggerSize: Float,
    lineHeight: Float,
    typography: InterfaceTypography,
    roles: KkRolePalette,
    plates: RewardFacePlates?,
    innerGap: Dp,
    costLabel: String?,
    costLabelStyle: TextStyle,
) {
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val next = lines.getOrNull(index + 1)
        if (line.kind == RuleLineKind.COST) {
            index++
            continue
        }
        if (line.kind == RuleLineKind.TRIGGER && next != null && next.kind == RuleLineKind.EFFECT) {
            Column(Modifier.fillMaxWidth().facePlate(plates, "rule$index"), verticalArrangement = Arrangement.spacedBy(innerGap)) {
                RewardRuleLineText(line, typeHue, bodySize, triggerSize, lineHeight, typography, roles)
                RewardRuleLineText(next, typeHue, bodySize, triggerSize, lineHeight, typography, roles)
            }
            index += 2
        } else {
            Box(Modifier.fillMaxWidth().facePlate(plates, "rule$index")) {
                RewardRuleLineText(line, typeHue, bodySize, triggerSize, lineHeight, typography, roles)
            }
            index++
        }
    }
    val costs = lines.filter { it.kind == RuleLineKind.COST }
    if (costs.isNotEmpty()) {
        Column(
            Modifier.fillMaxWidth().facePlate(plates, "cost").testTag(RewardCostTag)
                .drawBehind { drawRect(roles.threat, size = Size(3.dp.toPx(), size.height)) }.padding(start = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            costLabel?.let { OverlayText(it, costLabelStyle, uppercase = true) }
            costs.forEach { line -> RewardRuleLineText(line, typeHue, bodySize, triggerSize, lineHeight, typography, roles) }
        }
    }
}

/**
 * The hue of a rule line's trigger bar and numbers on a card of type hue [typeHue]: the element the
 * line names, else the card's own type. A line naming no element on an element card stays in that
 * element's hue: Kinetic's volt appears only on a Kinetic card.
 */
internal fun RewardRuleLine.hue(roles: KkRolePalette, typeHue: Color): Color = element?.overlayColor(roles) ?: typeHue

@Composable
private fun RewardRuleLineText(
    line: RewardRuleLine,
    typeHue: Color,
    bodySize: Float,
    triggerSize: Float,
    lineHeight: Float,
    typography: InterfaceTypography,
    roles: KkRolePalette,
) {
    val hue = line.hue(roles, typeHue)
    val struck = line.suppressed
    when (line.kind) {
        RuleLineKind.TRIGGER -> {
            val color = if (struck) roles.threat.copy(alpha = 0.8f) else Kk.Bone2
            val style = typography.monoStyle(triggerSize, weight = FontWeight.Bold, color = color)
                .let { if (struck) it.copy(textDecoration = TextDecoration.LineThrough) else it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size((triggerSize * 0.55f).dp, (triggerSize * 1.25f).dp)) {
                    // A sheared bar in the clause's element hue heads the trigger.
                    drawKkSheared(Rect(Offset.Zero, size), if (struck) roles.threat else hue)
                }
                Spacer(Modifier.width(7.dp))
                OverlayFitText(line.text, style, Modifier.weight(1f), uppercase = true, maxLines = 3, minScale = 0.8f)
            }
        }
        RuleLineKind.COST -> {
            // The cost block around it carries the stripe and the label.
            val style = typography.bodyStyle(bodySize * 0.95f, lineHeightEm = lineHeight, color = roles.threat)
                .let { if (struck) it.copy(textDecoration = TextDecoration.LineThrough) else it }
            OverlayRichText(rewardRichText(line.text, roles.threat), style, Modifier.fillMaxWidth())
        }
        else -> {
            if (struck) {
                val style = typography.bodyStyle(bodySize, lineHeightEm = lineHeight, color = roles.threat.copy(alpha = 0.8f))
                    .copy(textDecoration = TextDecoration.LineThrough)
                OverlayRichText(AnnotatedString(rewardKeepUnits(line.text)), style)
            } else {
                OverlayRichText(rewardRichText(line.text, rewardTextTone(hue)), typography.bodyStyle(bodySize, lineHeightEm = lineHeight, color = Kk.Bone2))
            }
        }
    }
}

/**
 * [color] as card text: an element hue (the numbers of a card's lines) lifted toward bone until it
 * reads at 4.5:1 on the card face, selected or not (a dark Mono or Ion hue otherwise sinks into
 * the ink). Marks and patterns keep the element hue itself.
 */
internal fun rewardTextTone(color: Color): Color = kkLiftContrast(color, Kk.Ink3, KK_CARD_LABEL_CONTRAST)

/**
 * A card's headline value hue: the card's [accent], or bone when the accent is a grey dimmer than bone (Mono's shield
 * and heat). A grey carries no type, only emphasis, and the headline never reads dimmer than the bone lines under it.
 */
internal fun rewardValueTone(accent: Color): Color {
    val grey = maxOf(accent.red, accent.green, accent.blue) - minOf(accent.red, accent.green, accent.blue) < 0.03f
    return if (grey && accent.luminance() < Kk.Bone.luminance()) Kk.Bone else accent
}

/** Test tag of a card's cost block (its threat stripe is drawn on purpose beside the text). */
internal const val RewardCostTag = "kinetickk.gameplay.reward-cost"

/** A mandatory cost: a threat stripe on the left edge, the [label] ("Cost") and the text. */
@Composable
internal fun RewardCostBlock(text: String, label: String?, style: TextStyle, labelStyle: TextStyle, threat: Color, modifier: Modifier) {
    Column(
        modifier.testTag(RewardCostTag).drawBehind { drawRect(threat, size = Size(3.dp.toPx(), size.height)) }.padding(start = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        label?.let { OverlayText(it, labelStyle, uppercase = true) }
        OverlayRichText(rewardRichText(text, threat), style)
    }
}

/**
 * The icon plate, the type channel's anchor: rimmed in the type hue (an item's second element takes
 * the rim's right and bottom edges, [secondHue]) over the element's pattern (the Kinetic slash for an
 * item or relic without one), the icon in the type hue. A specified [tint] (a type hue that tells
 * little apart by colour, [RewardCardLook.plateTint]) tints the plate in the hue and strengthens its
 * pattern, so the type still reads off the metal frame.
 */
internal fun DrawScope.drawRewardIcon(
    presentation: RewardCardPresentation,
    accent: Color,
    iconColor: Color,
    secondHue: Color,
    tint: Color,
    compact: Boolean,
    time: Float,
) {
    val bounds = Rect(Offset.Zero, size)
    val item = presentation.item
    val relic = presentation.relic
    val policy = presentation.relicPolicy
    val cut = if (compact) 8f else 12f
    val cue = tint.isSpecified
    val plate = if (cue) tint else Kk.Ink3
    val pattern = plateCuePattern(cue)
    when {
        item != null -> {
            drawKkIconPlate(bounds, null, iconColor, cut, rim = iconColor, rimSecond = if (secondHue != iconColor) secondHue else Color.Unspecified,
                fill = plate)
            // The plate carries the type's pattern: identity without hue (color vision).
            drawRadialPattern(bounds, item.elements.firstOrNull()?.let { elementFill(it.ordinal) } ?: KineticFill, iconColor, pattern)
            val effect = item.active
            if (effect != null) drawActiveItemMark(effect, Offset(bounds.center.x, bounds.top + bounds.minDimension * 0.5f), bounds.minDimension,
                iconColor, presentation.itemStack ?: 0, item.maxStacks, plate)
            else drawItemIcon(item, bounds.center, size.minDimension * 0.34f, iconColor, presentation.itemStack)
        }
        relic != null && policy != null -> {
            drawKkIconPlate(bounds, null, accent, cut, rim = accent, fill = plate)
            val element = relic.aspect.element
            when {
                element != null -> drawRadialPattern(bounds, elementFill(element.ordinal), accent, pattern)
                relic.aspect == RelicAspect.KINETIC -> drawRadialPattern(bounds, KineticFill, accent, pattern)
            }
            // The band names the rank; the rune keeps the plate's hue under the color-vision preset.
            drawRelicIcon(relic, policy, bounds.center, size.minDimension * 0.34f, time = time, accent = accent)
        }
        presentation.decline -> {
            drawKkIconPlate(bounds, null, accent, cut)
            drawKkHatch(bounds.deflate(4f * density))
            drawKkIcon(presentation.icon ?: KkIcon.SYSTEM_MATTER, bounds.center, size.minDimension * 0.5f, Kk.Mute)
        }
        else -> drawKkIconPlate(bounds, presentation.icon, accent, cut, size.minDimension / density * 0.55f, rim = accent)
    }
}

/** [kind] in [color] at [alpha] over the plate, fading out toward its edges. */
private fun DrawScope.drawRadialPattern(bounds: Rect, kind: KkFillKind, color: Color, alpha: Float) {
    drawKkRadialFade(bounds) { drawKkPattern(bounds, kind, color, alpha) }
}
/**
 * Card name: cond 900 italic in bone; epic adds a prismatic glow (the rarity's spectrum, never the
 * type hue); legendary the moving gold foil.
 */
@Composable
internal fun RewardCardName(
    text: String,
    rank: Int,
    typography: InterfaceTypography,
    fontSize: Float,
    time: State<Float>,
    color: Color = Kk.Bone,
) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val shown = text.uppercase()
        val base = typography.condStyle(fontSize, color = color)
        val width = constraints.maxWidth.toFloat()
        val style = remember(shown, base, width) { fitTextStyle(measurer, shown, base, width, 2, 0.75f) }
        RewardCardNameText(text, rank, style, time)
    }
}

@Composable
private fun RewardCardNameText(text: String, rank: Int, style: TextStyle, time: State<Float>) {
    when (rank.coerceIn(1, 5)) {
        5 -> {
            // The legendary foil in the preset's gold (the deep gold where its volt reads as the bright one).
            val gold = LocalKkRolePalette.current.gold
            BasicText(
                text.uppercase(),
                Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithCache {
                    val tile = max(size.width * 2.6f, 1f)
                    val foil = Brush.linearGradient(
                        0.2f to gold.base, 0.42f to gold.light, 0.64f to gold.base,
                        start = Offset.Zero, end = Offset(tile, 0f), tileMode = TileMode.Repeated,
                    )
                    onDrawWithContent {
                        drawContent()
                        val shift = -kkLoop(time.value, 2.4f) * tile
                        translate(shift, 0f) { drawRect(foil, Offset(-shift, 0f), size, blendMode = BlendMode.SrcIn) }
                    }
                },
                style = style,
            )
        }
        4 -> {
            // The prismatic glow: two rings of faint copies in the spectrum round the name (text-shadow
            // 0 0 18px @55 %), their reach scaled with the type, so a phone-sized name glows instead of ghosting.
            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
            val reach = with(LocalDensity.current) { style.fontSize.toPx() } * RewardNameGlowReach
            val glow = LocalKkRolePalette.current.spectrum
            BasicText(
                text.uppercase(),
                Modifier.drawBehind {
                    val laid = layout ?: return@drawBehind
                    val spectrum = RewardNameSpectrum.brush(laid.size.width.toFloat(), glow)
                    for (ring in 1..2) for (index in 0 until 8) {
                        val angle = index * 0.7853982f
                        drawText(laid, spectrum, Offset(kotlin.math.cos(angle) * reach * ring, kotlin.math.sin(angle) * reach * ring),
                            alpha = if (ring == 1) 0.13f else 0.065f)
                    }
                },
                style = style,
                onTextLayout = { layout = it },
            )
        }
        else -> BasicText(text.uppercase(), style = style)
    }
}

/**
 * A stat line: name (and condition) left; before value muted, after value in the accent. When
 * the name does not fit beside the values it takes its own line and the values sit under it.
 */
@Composable
internal fun RewardStatLine(
    change: RewardStatPresentation,
    first: Boolean,
    accent: Color,
    compact: Boolean,
    font: Float,
    typography: InterfaceTypography,
    roles: KkRolePalette,
    modifier: Modifier,
) {
    val nameSize = (if (compact) 14f else if (first) 16f else 15f) * font
    val valueSize = (if (compact) (if (first) 16f else 12f) else if (first) 22f else 16f) * font
    val afterColor = when {
        !change.improved -> roles.threat
        change.before.isNotEmpty() || first -> rewardValueTone(accent)
        else -> Kk.Bone
    }
    val nameStyle = typography.bodyStyle(nameSize, color = if (first) Kk.Bone else Kk.Mute)
    val beforeStyle = typography.wideStyle(valueSize * 0.62f, tabular = true, color = Kk.Mute2)
    val afterStyle = typography.wideStyle(valueSize, tabular = true, color = afterColor)
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 8f * density
        val stacked = remember(change, nameStyle, afterStyle, constraints.maxWidth) {
            val values = measurer.measure(change.after, afterStyle).size.width +
                (if (change.before.isNotEmpty()) measurer.measure(change.before, beforeStyle).size.width + gap else 0f)
            val name = measurer.measure(change.name, nameStyle, softWrap = false).size.width
            name + gap + values > constraints.maxWidth
        }
        val label: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                change.source?.let { OverlayFitText(it, typography.monoStyle(9f * font, color = Kk.Mute2), uppercase = true, maxLines = 1) }
                OverlayFitText(change.name, nameStyle, maxLines = if (stacked) 2 else 1)
                change.condition?.let { OverlayFitText(it, typography.monoStyle(9f * font, color = Kk.Mute), uppercase = true, maxLines = 2) }
            }
        }
        val values: @Composable () -> Unit = {
            if (change.before.isNotEmpty()) {
                OverlayText(change.before, beforeStyle, Modifier.padding(bottom = 2.dp))
                Spacer(Modifier.width(8.dp))
            }
            OverlayText(change.after, afterStyle)
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth()) {
                label(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) { values() }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                label(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                values()
            }
        }
    }
}

/** Reach of each ring of the epic name glow as a share of the name's font size. */
private const val RewardNameGlowReach = 0.085f

/** The preset's spectrum across an epic name (one brush per name width and spectrum, a few at once). */
private object RewardNameSpectrum {
    private val widths = FloatArray(4) { -1f }
    private val spectra = arrayOfNulls<KkSpectrum>(4)
    private val brushes = arrayOfNulls<Brush>(4)
    private var next = 0

    fun brush(width: Float, spectrum: KkSpectrum): Brush {
        for (index in 0 until 4) if (widths[index] == width && spectra[index] === spectrum) return brushes[index]!!
        val created = Brush.horizontalGradient(spectrum.stops, startX = 0f, endX = max(width, 1f))
        widths[next] = width
        spectra[next] = spectrum
        brushes[next] = created
        next = (next + 1) % 4
        return created
    }
}
