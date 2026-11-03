// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kinetickk.ball.gameplay.interaction.canvas.look.drawRelicRune
import kinetickk.ball.gameplay.interaction.canvas.look.elementIcon
import kinetickk.ball.gameplay.interaction.canvas.look.relicHue
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.interaction.canvas.look.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.look.overlayIcon
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkInfoButton
import kinetickk.foundation.design.KkSlam
import kinetickk.foundation.design.KkStampVariant
import kinetickk.foundation.design.KkTagVariant
import kinetickk.foundation.design.KkTooltipPlacement
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkGem
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkPips
import kinetickk.foundation.design.drawKkRelicSlot
import kinetickk.foundation.design.drawKkTag
import kinetickk.foundation.design.kkSlab
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.kkTagSize
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kinetickk.foundation.design.wideStyle
import kotlin.math.max

/** Header tags, the slammed two-line title, cause stamp, matter banked, bank and build rows. */
@Composable
internal fun ColumnScope.ReportSummary(
    scene: ReportScene,
    frame: OverlayFrame,
    titleSize: Float,
    matterSize: Float,
    gemSize: Float,
    stampSize: Float,
    slotSize: Float,
    relicSize: Float,
    compact: Boolean = false,
    inlineShatter: Boolean = false,
    gapScale: Float = 1f,
    recap: Boolean = true,
    /** Draw the Victory's Rebirth lines under the stamp (landscape phones show them beside the statistics). */
    rebirthLines: Boolean = true,
) {
    val presentation = scene.presentation
    val language = LocalAppLanguage.current
    val typography = rememberInterfaceTypography()
    val roles = scene.roles
    val text = scene.textScale
    val k = if (compact) 1f else frame.scale
    fun sp(value: Float, min: Float = 11f) = max(value * k, min) * text
    // The title and the matter figure are display type: the text-size setting does not apply.
    fun display(value: Float, min: Float) = max(value * k, min)
    fun gap(value: Float): Dp = (value * k * gapScale).dp
    fun box(value: Float): Dp = (value * k).dp
    // The header, title and stamp sit above the inline shatter, whose halo and debris reach past
    // its own box (portrait).
    ReportHeader(scene, sp(15f), Modifier.zIndex(1f).enter(scene, 0f, left = true))
    Spacer(Modifier.height(gap(18f)))
    val titleTop = language.text(if (presentation.victory) OverlayRedesignText.VictoryTitleTop else OverlayRedesignText.DefeatTitleTop)
    val titleBottom = language.text(if (presentation.victory) OverlayRedesignText.VictoryTitleBottom else OverlayRedesignText.DefeatTitleBottom)
    Column(
        Modifier.zIndex(1f).fillMaxWidth().semantics(mergeDescendants = true) { heading() }.graphicsLayer {
            val p = ((scene.t - 0.1f) / 0.5f).coerceIn(0f, 1f)
            val s = KkSlam.scale(p)
            scaleX = s
            scaleY = s
            rotationZ = KkSlam.rotation(p, -2f)
            alpha = KkSlam.alpha(p)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
        },
    ) {
        FitText(titleTop, typography.wideStyle(display(titleSize, 24f), lineHeightEm = 0.92f, color = scene.accent))
        FitText(titleBottom, typography.wideStyle(display(titleSize, 24f), lineHeightEm = 0.92f, color = Kk.Bone))
    }
    Spacer(Modifier.height(gap(22f)))
    val stamp = if (presentation.victory) language.text(GameplayText.ArchitectFallen) else presentation.reason
    Box(Modifier.zIndex(1f).enter(scene, 0.3f)) {
        OverlayStamp(stamp, Modifier.testTag("kinetickk.gameplay.results.cause"),
            if (presentation.victory) KkStampVariant.YOU else KkStampVariant.THREAT, fontSize = stampSize * k, textScale = text)
    }
    if (presentation.easy) {
        // Baby mode: the soft stamp says the run counted nothing; its (!) says what that means.
        Spacer(Modifier.height(gap(10f)))
        Row(Modifier.zIndex(1f).enter(scene, 0.33f), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OverlayStamp(language.text(OverlayRedesignText.NotCounted), Modifier.testTag("kinetickk.gameplay.results.not-counted"),
                KkStampVariant.YOU, color = roles.soft, fontSize = stampSize * k * 0.8f, textScale = text)
            KkInfoButton(language.text(OverlayRedesignText.NotCountedInfo), Modifier.testTag("kinetickk.gameplay.results.not-counted-info"),
                KkTooltipPlacement.ABOVE_START, text)
        }
    }
    presentation.rebirthEarned?.takeIf { rebirthLines }?.let { earned ->
        Spacer(Modifier.height(gap(14f)))
        ReportRebirthEarned(earned, sp(15f), sp(12f), Modifier.zIndex(1f).enter(scene, 0.35f))
    }
    if (inlineShatter) {
        Canvas(Modifier.fillMaxWidth().height(230.dp).clearAndSetSemantics { }) {
            drawReportShatter(scene, center, size.height / 420f * 0.95f)
        }
    }
    Spacer(Modifier.height(gap(if (compact) 18f else 44f)))
    Column(Modifier.enter(scene, 0.4f)) {
        OverlayText(language.text(if (presentation.easy) OverlayRedesignText.MatterNotBanked else OverlayRedesignText.MatterBanked),
            typography.labelStyle(sp(15f), color = Kk.Mute), uppercase = true)
        Row(Modifier.padding(top = gap(8f)), horizontalArrangement = Arrangement.spacedBy(gap(14f)), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(box(gemSize))) { drawKkGem(center, roles.you, size.minDimension / density) }
            OverlayText("+" + presentation.matter, typography.wideStyle(display(matterSize, 22f), tabular = true, lineHeightEm = 0.9f, color = roles.you),
                Modifier.testTag("kinetickk.gameplay.results.matter"))
        }
    }
    presentation.bank?.let { bank ->
        Spacer(Modifier.height(gap(12f)))
        Row(Modifier.enter(scene, 0.45f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OverlayText(language.text(OverlayRedesignText.Bank), typography.labelStyle(sp(15f), color = Kk.Mute), uppercase = true)
            OverlayText(bank, typography.monoStyle(sp(11f), color = Kk.Bone), uppercase = true)
            KkInfoButton(language.text(OverlayRedesignText.BankInfo), Modifier.testTag("kinetickk.gameplay.results.bank-info"),
                KkTooltipPlacement.ABOVE_START, text)
        }
    }
    val icon = presentation.weaponIcon
    if (icon != null) {
        Spacer(Modifier.height(gap(if (compact) 18f else 34f)))
        Row(Modifier.enter(scene, 0.5f).testTag("kinetickk.gameplay.results.build"), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OverlayText(language.text(OverlayRedesignText.Build), typography.labelStyle(sp(15f), color = Kk.Mute),
                Modifier.padding(end = 8.dp), uppercase = true)
            val measurer = rememberKkCanvasMeasurer(1f)
            Box(
                Modifier.size(box(slotSize)).testTag("kinetickk.gameplay.results.weapon")
                    .semantics { this.text = AnnotatedString(presentation.weapon + " " + presentation.weaponLevel.orEmpty()) }
                    .drawWithCache {
                        // The level label keeps inside the sheared face and the icon clears it at every slot size.
                        val bounds = Rect(Offset.Zero, size)
                        val placement = weaponSlotPlacement(measurer, bounds, icon, presentation.weaponLevel)
                        onDrawBehind {
                            drawPlacedWeaponSlot(measurer, bounds, icon, presentation.weaponLevel, presentation.weaponMaxLevel, placement = placement)
                        }
                    },
            )
            if (presentation.relics.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Canvas(Modifier.size(box(relicSize * presentation.relics.size + 8f * (presentation.relics.size - 1)), box(relicSize))
                    .semantics { contentDescription = language.text(OverlayRedesignText.Relics) }) {
                    val slot = size.height
                    presentation.relics.forEachIndexed { index, relic ->
                        val center = Offset(slot * 0.5f + index * (slot + 8f * k * density), size.height * 0.5f)
                        val definition = presentation.relicDefinitions.getOrNull(index)
                        when {
                            definition != null -> {
                                val hue = relicHue(definition, roles)
                                drawKkRelicSlot(center, hue, sizeDp = slot / density)
                                drawRelicRune(definition, center, slot * 0.31f, hue)
                            }
                            relic != null -> drawKkRelicSlot(center, relic.aspect.overlayColor(roles), relic.aspect.overlayIcon(), slot / density)
                            else -> drawKkRelicSlot(center, Color.Unspecified, sizeDp = slot / density)
                        }
                    }
                }
            }
        }
    }
    if (recap) ReportBuildRecap(scene, frame, compact)
}

/** The build recap at the report's sizes ([ReportRecap]). */
@Composable
internal fun ReportBuildRecap(scene: ReportScene, frame: OverlayFrame, compact: Boolean) {
    val k = if (compact) 1f else frame.scale
    val text = scene.textScale
    ReportRecap(scene, Modifier.enter(scene, 0.55f), max(15f * k, 11f) * text, max(13f * k, 10f) * text, (24f * k).dp,
        (if (compact) 10f else 18f * k).dp)
}

/**
 * The build recap under the build row: the run's leading archetypes (glyph, name, lit tier pips)
 * and its dominant element, then the reactions it set off, those first met in this run marked
 * with a small diamond in the you color. Rows wrap; nothing shows for a run without tags or
 * reactions.
 */
@Composable
private fun ReportRecap(scene: ReportScene, modifier: Modifier, labelSize: Float, chipSize: Float, chipHeight: Dp, gap: Dp) {
    val presentation = scene.presentation
    if (presentation.recapTags.isEmpty() && presentation.dominant == null && presentation.reactions.isEmpty()) return
    val language = LocalAppLanguage.current
    val typography = rememberInterfaceTypography()
    val roles = scene.roles
    Column(modifier.testTag("kinetickk.gameplay.results.recap")) {
        if (presentation.recapTags.isNotEmpty() || presentation.dominant != null) {
            Spacer(Modifier.height(gap))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OverlayText(language.text(HudRedesignText.TopTags), typography.labelStyle(labelSize, color = Kk.Mute),
                    Modifier.align(Alignment.CenterVertically).padding(end = 4.dp), uppercase = true)
                presentation.recapTags.forEach { tag ->
                    ReportRecapChip(tag.label, chipSize, chipHeight, Kk.Bone, tier = tag.tier) { size ->
                        drawKkIcon(KkIcon.Tags[tag.tag.ordinal], center, size, if (tag.tier > 0) Kk.Bone else Kk.Mute)
                    }
                }
                presentation.dominant?.let { dominant ->
                    val hue = roles.element(dominant.element)
                    ReportRecapChip(language.text(HudRedesignText.DominantElement, dominant.label), chipSize, chipHeight, hue) { size ->
                        drawKkIcon(elementIcon(dominant.element), center, size, hue)
                        drawCircle(hue, size * 0.62f, center, style = kkStroke(1.5f * density))
                    }
                }
            }
        }
        if (presentation.reactions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OverlayText(language.text(HudRedesignText.ReactionsThisRun), typography.labelStyle(labelSize, color = Kk.Mute),
                    Modifier.align(Alignment.CenterVertically).padding(end = 4.dp), uppercase = true)
                presentation.reactions.forEach { reaction ->
                    val first = roles.element(reaction.id.first.ordinal)
                    val second = roles.element(reaction.id.second.ordinal)
                    ReportRecapChip(reaction.name, chipSize, chipHeight, Kk.Bone, new = if (reaction.discovered) roles.you else null,
                        wide = true) { size ->
                        drawKkIcon(elementIcon(reaction.id.first.ordinal), Offset(center.x - size * 0.55f, center.y), size, first)
                        drawKkIcon(elementIcon(reaction.id.second.ordinal), Offset(center.x + size * 0.55f, center.y), size, second)
                    }
                }
            }
        }
    }
}

/** One recap chip: an ink plate with a glyph, the name, lit tier pips and the mark of a new find in [new]'s color. */
@Composable
private fun ReportRecapChip(
    label: String,
    textSize: Float,
    height: Dp,
    color: Color,
    tier: Int = 0,
    new: Color? = null,
    wide: Boolean = false,
    glyph: DrawScope.(Float) -> Unit,
) {
    val typography = rememberInterfaceTypography()
    Row(
        Modifier.height(height).drawWithCache {
            val plate = Path().kkSlab(0f, 0f, size.width, size.height, 6f * density)
            onDrawBehind { drawPath(plate, Kk.Ink2) }
        }.padding(horizontal = 8.dp).semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(height * (if (wide) 0.9f else 0.6f), height * 0.6f)) { glyph(size.height) }
        OverlayText(label, typography.condStyle(textSize, color = color), uppercase = true, maxLines = 1)
        if (tier > 0) {
            Canvas(Modifier.size(height * 0.95f, height * 0.25f)) {
                val pip = (size.width - 4f * density) / 3f / density
                drawKkPips(Offset.Zero, 3, tier.coerceIn(0, 3), Kk.Bone, sizeDp = size.height / density, widthDp = pip, gapDp = 2f, emptyColor = Kk.Ink4)
            }
        }
        if (new != null) {
            Spacer(Modifier.size(height * 0.34f).testTag("kinetickk.gameplay.results.recap.new").drawWithCache {
                val half = size.minDimension * 0.5f
                val x = size.width * 0.5f
                val y = size.height * 0.5f
                val mark = Path().apply {
                    moveTo(x, y - half)
                    lineTo(x + half, y)
                    lineTo(x, y + half)
                    lineTo(x - half, y)
                    close()
                }
                onDrawBehind { drawPath(mark, new) }
            })
        }
    }
}

/**
 * The header row: the "Run report" label, then the rebirth and form tags on one line, as on the
 * board. When the three outgrow the column (Russian at a large text size on a phone) they shrink
 * together by one factor, so the last tag keeps its whole plate instead of being clamped to the
 * width left over, with its name running past the outline.
 */
@Composable
private fun ReportHeader(scene: ReportScene, labelSize: Float, modifier: Modifier) {
    val presentation = scene.presentation
    val label = LocalAppLanguage.current.text(GameplayText.RunRecord)
    val typography = rememberInterfaceTypography()
    val measurer = rememberKkCanvasMeasurer(scene.textScale)
    val labels = rememberTextMeasurer(cacheSize = 4)
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth.toFloat()
        val rebirth = presentation.rebirth
        val form = presentation.form
        val formIcon = presentation.formIcon
        val fit = remember(label, labelSize, rebirth, form, formIcon, typography, measurer, density, available) {
            val gaps = ReportHeaderGapDp * density * listOfNotNull(rebirth, form).size
            reportSharedFit(available) { factor ->
                val scaled = CanvasTextMeasurer(measurer.delegate, measurer.scale * factor, measurer.language, measurer.typography, measurer.roles)
                val shown = labels.measure(label.uppercase(), typography.labelStyle(labelSize * factor), softWrap = false, maxLines = 1)
                // One px per plate guards against rounding it to whole pixels.
                gaps + shown.size.width +
                    (rebirth?.let { kkTagSize(scaled, it, density).width + 1f } ?: 0f) +
                    (form?.let { reportFormTagSize(scaled, it, formIcon, density).width + 1f } ?: 0f)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ReportHeaderGapDp.dp), verticalAlignment = Alignment.CenterVertically) {
            OverlayText(label, typography.labelStyle(labelSize * fit, color = Kk.Mute), uppercase = true)
            rebirth?.let {
                if (presentation.rebirthLevel > 0) {
                    OverlayTag(it, background = Kk.Gold, foreground = Kk.Ink, textScale = scene.textScale * fit)
                } else {
                    OverlayTag(it, variant = KkTagVariant.LINE, textScale = scene.textScale * fit)
                }
            }
            form?.let { ReportFormTag(it, formIcon, scene.textScale * fit) }
        }
    }
}

private const val ReportHeaderGapDp = 10f

/**
 * The largest factor, at most 1, at which a row that must share one line fits [available]:
 * [width] gives the row's width at a factor. Found by bisection, down to 0.4.
 */
private fun reportSharedFit(available: Float, width: (Float) -> Float): Float {
    if (width(1f) <= available) return 1f
    var fits = 0.4f
    var over = 1f
    repeat(8) {
        val middle = (fits + over) * 0.5f
        if (width(middle) <= available) fits = middle else over = middle
    }
    return fits
}

/** Size in px of [ReportFormTag]: the line tag's plate, widened by the form icon and its gap. */
private fun reportFormTagSize(measurer: CanvasTextMeasurer, text: String, icon: KkIcon?, density: Float): Size {
    val plate = kkTagSize(measurer, text, density)
    return if (icon == null) plate else Size(plate.width + formTagLead(measurer.scale, density), plate.height)
}

/** Icon size in px of the form tag at [textScale]: it follows the label's size. */
private fun formTagIconPx(textScale: Float, density: Float): Float = FormTagFontSp * FormTagIconEm * textScale * density

/** Width in px the form icon and its gap add before the tag's text plate. */
private fun formTagLead(textScale: Float, density: Float): Float = formTagIconPx(textScale, density) + FormTagIconGapDp * density

/**
 * The Core form's line tag with the form icon before its name (Report board "■ Ram"; the pause
 * chip draws the same icon). The icon follows the label's size, 6 dp before the text as on the
 * board; the plate is the line tag's, widened by the icon.
 */
@Composable
private fun ReportFormTag(text: String, icon: KkIcon?, textScale: Float) {
    if (icon == null) {
        OverlayTag(text, variant = KkTagVariant.LINE, textScale = textScale)
        return
    }
    val measurer = rememberKkCanvasMeasurer(textScale)
    val density = LocalDensity.current.density
    val tag = remember(measurer, text, icon, density) { reportFormTagSize(measurer, text, icon, density) }
    val iconPx = formTagIconPx(textScale, density)
    val lead = formTagLead(textScale, density)
    Box(
        Modifier.size((tag.width / density).dp, (tag.height / density).dp).testTag("kinetickk.gameplay.results.form")
            .semantics { this.text = AnnotatedString(text) }
            .drawWithCache {
                val inset = 0.75f * density
                val outline = Path().kkSlab(inset, inset, size.width - inset, size.height - inset, 6f * density)
                onDrawBehind {
                    drawPath(outline, Kk.Line2, style = kkStroke(1.5f * density))
                    drawKkIcon(icon, Offset(FormTagPaddingDp * density + iconPx * 0.5f, size.height * 0.5f), iconPx, Kk.Bone)
                    // The text plate after the icon keeps the tag's own padding; the outline above is the whole tag's.
                    drawKkTag(measurer, text, Offset(lead, 0f), KkTagVariant.LINE, background = Color.Transparent)
                }
            },
    )
}

/** Tag label size (`.tag`, 13 px), icon size relative to it, and the board's glyph gap. */
private const val FormTagFontSp = 13f
private const val FormTagIconEm = 0.84f
private const val FormTagIconGapDp = 6f
private const val FormTagPaddingDp = 10f

/** A single line that shrinks to fit its width (huge wide titles, long Russian words). */
@Composable
private fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxPx = constraints.maxWidth.toFloat()
        val shown = text.uppercase()
        val fitted = remember(shown, style, maxPx) {
            val width = measurer.measure(shown, style, softWrap = false, maxLines = 1).size.width.toFloat()
            if (width <= maxPx || width <= 0f) style else style.copy(fontSize = style.fontSize * (maxPx / width) * 0.98f)
        }
        BasicText(shown, style = fitted, maxLines = 1, softWrap = false)
    }
}

/** Combat (with the (!) on how damage is counted) and Collection, revealed top to bottom. */
@Composable
internal fun ReportStatistics(scene: ReportScene, frame: OverlayFrame, rowHeight: Float, labelSize: Float, valueSize: Float) {
    val language = LocalAppLanguage.current
    val typography = rememberInterfaceTypography()
    val t = scene.t
    Row(
        Modifier.reveal(terminalRevealProgress(t, 0.08f, 0.38f)),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayText(language.text(GameplayText.CombatStatistics), typography.labelStyle(labelSize * scene.textScale, color = Kk.Bone),
            Modifier.semantics { heading() }, uppercase = true)
        KkInfoButton(language.text(GameplayText.DamageAccountingHint), Modifier.testTag("kinetickk.gameplay.results.damage-info"),
            KkTooltipPlacement.BELOW, scene.textScale)
    }
    Spacer(Modifier.height(6.dp))
    ReportRows(scene, scene.presentation.combat, 0, rowHeight, labelSize, valueSize, typography)
    Spacer(Modifier.height(18.dp))
    OverlayText(language.text(GameplayText.LootStatistics), typography.labelStyle(labelSize * scene.textScale, color = Kk.Bone),
        Modifier.reveal(terminalRevealProgress(t, 0.12f + scene.presentation.combat.size * 0.065f)).semantics { heading() }, uppercase = true)
    Spacer(Modifier.height(6.dp))
    ReportRows(scene, scene.presentation.collection, scene.presentation.combat.size, rowHeight, labelSize, valueSize, typography)
}

@Composable
private fun ReportRows(
    scene: ReportScene,
    rows: List<TerminalStatistic>,
    firstIndex: Int,
    rowHeight: Float,
    labelSize: Float,
    valueSize: Float,
    typography: InterfaceTypography,
) {
    val language = LocalAppLanguage.current
    rows.forEachIndexed { index, statistic ->
        val progress = terminalRevealProgress(scene.t, 0.18f + (firstIndex + index) * 0.065f)
        // Reserve layout height while keeping unrevealed text out of accessibility traversal.
        Column(Modifier.fillMaxWidth().reveal(progress)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = rowHeight.dp).testTag("kinetickk.gameplay.stat.${statistic.label.name}"),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayFitText(language.text(statistic.label), typography.bodyStyle(labelSize * scene.textScale, color = Kk.Bone), Modifier.weight(1f),
                    maxLines = 2)
                OverlayText(statistic.value, typography.condStyle(valueSize * scene.textScale, tabular = true, color = when (statistic.tone) {
                    TerminalTone.BONE -> Kk.Bone
                    TerminalTone.YOU -> scene.roles.you
                    TerminalTone.THREAT -> scene.roles.threat
                }))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).drawBehind { drawRect(Kk.Line) })
        }
    }
}
