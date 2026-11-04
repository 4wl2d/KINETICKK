// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.DiscoveryBadge
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkSlabKind
import kinetickk.foundation.design.KkTagVariant
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.drawKkCardFrame
import kinetickk.foundation.design.kkCardFrameReachDp
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.overlay.*
import kinetickk.ball.gameplay.interaction.rewards.art.*

/** Card content dimensions derived from the card's own size (board 300×450 or phone 220×256). */
internal class RewardCardSpec(val compact: Boolean, val u: Float, val font: Float) {
    fun dp(value: Float): Dp = (value * u).dp
    fun sp(value: Float): Float = value * font

    /** Board dp of the card layout: icon plate, the chips beside it, body and footer. */
    val bandHeight: Float get() = if (compact) 30f else 42f
    val plateSize: Float get() = if (compact) 46f else 88f
    val plateX: Float get() = if (compact) 22f else 34f
    val plateY: Float get() = if (compact) 38f else 56f
    val chipsGap: Float get() = if (compact) 10f else 16f
    val bodyTop: Float get() = if (compact) 92f else 162f
    val bodyBottom: Float get() = if (compact) 10f else 18f
    val footer: Float get() = if (compact) 24f else 34f
    /** Body line height: phones set their paragraphs a little tighter. */
    val lineHeight: Float get() = if (compact) 1.3f else 1.4f
    val start: Float get() = if (compact) 22f else 34f
    val end: Float get() = if (compact) 24f else 34f
    /** Chips keep a readable size: small cards (phones, a scaled-down board card) take the compact chip. */
    val chips: RewardChipStyle get() = if (compact || u < 0.85f) RewardChipCompact else RewardChipRegular
}

/**
 * Whether a card takes the phone layout: small cards, every card of a phone deal ([phoneDeal], the
 * layout's compact card content: the four-card landscape deal is 195 × 254) and squat ones (up to
 * 1.3 times as tall as wide). A board card on a short window (300 × 419 at 1280 × 720) keeps the
 * board layout, scaled.
 */
internal fun rewardCardUsesCompactLayout(widthDp: Float, heightDp: Float, phoneDeal: Boolean = false): Boolean =
    rewardCardIsCompact(widthDp, heightDp) || phoneDeal || heightDp < widthDp * 1.3f

internal fun rewardCardSpec(widthDp: Float, heightDp: Float, textScale: Float, phoneDeal: Boolean): RewardCardSpec {
    val compact = rewardCardUsesCompactLayout(widthDp, heightDp, phoneDeal)
    val clamped = rewardCardUnit(widthDp, heightDp, phoneDeal)
    return RewardCardSpec(compact, clamped, clamped.coerceIn(0.8f, 1.2f) * textScale)
}

/** The card layout's unit: its size against the phone (220 × 256) or board (300 × 450) card it was drawn for. */
internal fun rewardCardUnit(widthDp: Float, heightDp: Float, phoneDeal: Boolean): Float {
    val compact = rewardCardUsesCompactLayout(widthDp, heightDp, phoneDeal)
    val u = if (compact) min(widthDp / 220f, heightDp / 256f) else min(widthDp / 300f, heightDp / 450f)
    return u.coerceIn(0.6f, 1.25f)
}

/** Forecast chips a card shows at most: three on the board, two on phones. */
internal fun rewardForecastLimit(compact: Boolean): Int = if (compact) 2 else 3

@Composable
internal fun RewardCard(
    presentation: RewardCardPresentation,
    index: Int,
    textScale: Float,
    renderTime: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPreview: () -> Unit = {},
    selected: Boolean = false,
    time: State<Float>? = null,
    burst: () -> Float = { -1f },
    bodyFit: Float = 1f,
    onBodyFit: (Float) -> Unit = {},
    /** The card is dealt by a phone layout (its compact card content), whatever its shape. */
    phoneDeal: Boolean = false,
    /** The card's text plates, when the caller keeps them (they measure where the text ends). */
    facePlates: RewardFacePlates? = null,
    onSelect: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hoveredValue by interactionSource.collectIsHoveredAsState()
    val focusedValue by interactionSource.collectIsFocusedAsState()
    val pressedValue by interactionSource.collectIsPressedAsState()
    val highlighted = enabled && (hoveredValue || focusedValue || pressedValue)
    // Keep touch previews behind clickable's press/drag arbitration; moving on raw down can cancel a tap.
    LaunchedEffect(pressedValue) { if (enabled && pressedValue) onPreview() }
    val clock = time ?: rememberUpdatedState(renderTime)
    val selection = if (selected || highlighted) 1f else 0f
    val accent = presentation.accentColor(roles)
    val iconColor = presentation.iconColor(roles)
    val typography = rememberInterfaceTypography()
    val scrollState = rememberScrollState()
    BoxWithConstraints(
        modifier
            .testTag("kinetickk.gameplay.choice.${index + 1}")
            .rewardLockedLook(presentation.locked)
            .semantics { stateDescription = listOfNotNull(presentation.bandStart, presentation.bandEnd).joinToString(", ") }
            .rewardDragGestures(enabled, scrollState)
            // The body consumes its own scroll first. Header drags and wheel input reach this
            // parent, moving the same text viewport once.
            .scrollable(scrollState, Orientation.Vertical, enabled = enabled, reverseDirection = true)
            .rewardPreviewInput(enabled && !presentation.locked, onPreview)
            // A single activation surface. The nested scrollable cancels this click on drag;
            // wheel events never synthesize activation.
            .clickable(
                enabled = enabled && !presentation.locked,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = language.text(GameplayText.SelectChoice, index + 1, presentation.title),
                onClick = onSelect,
            ),
    ) {
        val spec = rewardCardSpec(maxWidth.value, maxHeight.value, textScale, phoneDeal)
        // Narrow cards move the band's trailing value into the body so the two labels never collide.
        val narrow = rewardCardIsNarrow(maxWidth.value)
        // The card's own look: the frame by rarity (its material), the face by type (hue and pattern by
        // element, ornament by tag, watermark and seed, the bespoke art of an epic or legendary item).
        val look = remember(presentation, roles) { rewardCardLook(presentation, roles) }
        val artCache = remember { RewardArtCache() }
        // Everything drawn behind the text (pattern, watermark, art, sparks, scanlines, sheen) stays
        // behind it: the face canvas lays a soft-edged patch of the card face under each text row, so
        // effects fade out under the text instead of being cut at a hard edge (a cut spark would read
        // as an arrowhead).
        val ownPlates = remember { RewardFacePlates() }
        val plates = if (look.needsPlates) facePlates ?: ownPlates else null
        val paths = remember { KkPathCache(1) }
        // The rarity band is card chrome of a fixed height: its labels follow the text size up to
        // the default and no further, so the two labels never collide or get cut.
        val bandMeasurer = rememberKkCanvasMeasurer(min(textScale, 1f))
        val facePaths = remember { KkPathCache(4) }
        RewardCardFace(plates, selection, scrollState, burst, bandMeasurer, presentation, accent, clock, facePaths, phoneDeal, look,
            artCache, paths)
        // A decline card's plate and text start clear of its slanted dashed outline (a tall phone card's slant reaches them).
        val declineShift = if (presentation.decline) rewardDeclineShiftDp(spec, maxHeight.value).dp else 0.dp
        val plateSize = spec.dp(spec.plateSize)
        Box(
            Modifier.offset(spec.dp(spec.plateX) + declineShift, spec.dp(spec.plateY)).size(plateSize)
                .testTag("kinetickk.gameplay.choice.${index + 1}.${if (spec.compact) "compact" else "expanded"}")
                .drawBehind { drawRewardIcon(presentation, accent, iconColor, look.secondHue, look.plateTint, spec.compact, clock.value) },
        )
        RewardCardHeaderChips(presentation, spec, maxWidth, declineShift, plateSize, plates, index, typography, roles, textScale)
        val forecast = presentation.forecast.take(rewardForecastLimit(spec.compact))
        val footerTags = if (presentation.item == null && presentation.chips.isEmpty() && !spec.compact) presentation.tags else emptyList()
        val hasFooter = footerTags.isNotEmpty() || presentation.action != null || forecast.isNotEmpty()
        val footerHeight = if (hasFooter) max(spec.dp(spec.footer).value, spec.chips.heightDp + 2f).dp else 0.dp
        val bodyTop = spec.dp(spec.bodyTop)
        val bodyBottom = spec.dp(spec.bodyBottom)
        val bodyHeight = (maxHeight - bodyTop - bodyBottom - footerHeight).coerceAtLeast(24.dp)
        val start = spec.dp(spec.start) + declineShift
        // The body converges on a fitting size during the entrance; text longer than the last
        // step scrolls. Cards dealt together share the smallest step any of them needs ([bodyFit]).
        val end = spec.dp(spec.end)
        // The face's right edge slants in by RewardCardCutDp toward the bottom and the frame (its border, the epic
        // rim) reaches in from it: the rows under the name (and the costs) end clear of both at the lowest line they
        // show, [lowest] down from the body's top. The name and the labels above the rows sit high enough for [end].
        val frameReach = kkCardFrameReachDp(look.style.material, maxWidth.value) + RewardBodyFrameClearanceDp
        val rowsEnd = { lowest: Dp -> max(end.value, RewardCardCutDp * (bodyTop + lowest).value / maxHeight.value + frameReach).dp }
        val gap = spec.dp(if (spec.compact) 3f else 6f)
        // Phone cards keep a card's mechanics (rules, a relic's upside); its numbers return when it has none.
        val mechanics = presentation.rules.any { it.kind != RuleLineKind.COST } || presentation.relic != null
        val changes = if (spec.compact && mechanics) emptyList() else presentation.changes
        val descriptions = if (spec.compact && changes.isNotEmpty()) emptyList() else presentation.descriptions
        val body = rewardCardBody(presentation, spec, start, end, gap, narrow, changes, descriptions, rowsEnd, iconColor, accent, typography,
            roles, clock, language)
        // Costs are never hidden: a relic's liability in a threat-striped block, laid out under the body
        // as its tail. It fits with the body; when the body still overflows at its smallest size, the
        // cost keeps its whole height at the bottom and only the text above it scrolls.
        val costs = rewardCardCosts(presentation, spec, start, gap, rowsEnd, plates, index, typography, roles, language)
        val fitState = remember(presentation, maxWidth, maxHeight, textScale, phoneDeal, typography, LocalDensity.current) {
            RewardCardFitState()
        }
        // Int.MAX_VALUE until the body is first measured.
        val overflows = scrollState.maxValue in 1 until Int.MAX_VALUE
        Box(Modifier.offset(y = bodyTop).fillMaxWidth().height(bodyHeight).onPlaced { plates?.viewport = it }) {
            val steps = remember(textScale) { rewardCardFitSteps(textScale) }
            RewardCardFitLayout(steps, bodyFit, fitState, scrollState, bodyHeight, onBodyFit, Modifier.fillMaxSize(),
                tail = costs, tailGap = { fit -> (gap * fit).roundToPx() }) { fit, lowest ->
                body(
                    fit,
                    Modifier.fillMaxWidth().testTag("kinetickk.gameplay.choice.${index + 1}.text")
                        // Longer text fades out at the bottom of the text column itself: nothing is
                        // painted over the card, so its rarity glow, border and effects stay whole.
                        .then(if (overflows) Modifier.rewardScrollFade(scrollState, spec.dp(18f)) else Modifier)
                        .verticalScroll(scrollState, enabled = enabled),
                    plates,
                    lowest,
                )
            }
            RewardScrollIndicator(scrollState, iconColor, Modifier.align(Alignment.CenterEnd)
                .padding(end = spec.dp(if (spec.compact) 14f else 18f)).fillMaxHeight().width(3.dp)
                .testTag("kinetickk.gameplay.choice.${index + 1}.scroll"))
        }
        if (hasFooter) RewardCardFooter(presentation, spec, start, end, bodyBottom, footerHeight, plates, index, footerTags, forecast, textScale)
        if (presentation.isNewDiscovery) RewardCardNewStamp(presentation, spec, language, textScale, index)
    }
}

/** The card's body: the name, a narrow card's band value and the family, then the stat lines, rules or descriptions. */
@Composable
private fun rewardCardBody(
    presentation: RewardCardPresentation,
    spec: RewardCardSpec,
    start: Dp,
    end: Dp,
    gap: Dp,
    narrow: Boolean,
    changes: List<RewardStatPresentation>,
    descriptions: List<String>,
    rowsEnd: (Dp) -> Dp,
    iconColor: Color,
    accent: Color,
    typography: InterfaceTypography,
    roles: KkRolePalette,
    clock: State<Float>,
    language: AppLanguage,
): @Composable (Float, Modifier, RewardFacePlates?, Dp) -> Unit = { fit, bodyModifier, rowPlates, lowest ->
    val font = spec.font * fit
    Column(bodyModifier.padding(start = start, end = end), verticalArrangement = Arrangement.spacedBy(gap * fit)) {
        RewardCardName(presentation.title, presentation.rank, typography, (if (spec.compact) 26f else 38f) * font, clock,
            color = if (presentation.heirloom) roles.gold.base else Kk.Bone)
        if (narrow) presentation.bandEnd?.let { bandEnd ->
            OverlayText(bandEnd, typography.monoStyle(10f * font, color = Kk.Mute), uppercase = true)
        }
        if (!spec.compact) presentation.family?.let { family ->
            OverlayFitText(family, typography.monoStyle(11f * font, color = Kk.Mute), uppercase = true, maxLines = 2)
        }
        Spacer(Modifier.height(spec.dp(if (spec.compact) 2f else 8f) * fit))
        if (changes.isNotEmpty() || presentation.rules.isNotEmpty() || descriptions.isNotEmpty()) Column(
            Modifier.padding(end = rowsEnd(lowest) - end), verticalArrangement = Arrangement.spacedBy(gap * fit),
        ) {
            // An item's values take its element hue (the preset's volt for Kinetic): rarity is the card's material, not a value color.
            val valueColor = rewardTextTone(if (presentation.item != null) iconColor else accent)
            changes.forEachIndexed { line, change ->
                RewardStatLine(change, line == 0, valueColor, spec.compact, font, typography, roles, Modifier.facePlate(rowPlates, "stat$line"))
            }
            val bodySize = (if (spec.compact) 13f else 14.5f) * font
            if (presentation.rules.isNotEmpty()) {
                // Trigger bars and numbers take the line's element, else the card's own type hue.
                RewardRuleRows(presentation.rules, iconColor, bodySize, (if (spec.compact) 9f else 10.5f) * font, spec.lineHeight, typography,
                    roles, rowPlates, gap * fit * 0.4f,
                    costLabel = if (spec.compact) null else language.text(OverlayRedesignText.Cost),
                    costLabelStyle = typography.monoStyle((if (spec.compact) 8.5f else 9.5f) * font, weight = FontWeight.Bold, color = roles.threat))
            } else {
                descriptions.forEachIndexed { line, description ->
                    val color = if (presentation.decline) Kk.Mute else Kk.Bone2
                    val number = if (presentation.relic != null) Kk.Bone else color
                    OverlayRichText(
                        rewardRichText(description, number, if (line == 0) presentation.values else emptyList(), rewardTextTone(accent)),
                        typography.bodyStyle(bodySize, lineHeightEm = spec.lineHeight, color = color),
                        Modifier.fillMaxWidth().facePlate(rowPlates, "description$line"),
                    )
                }
            }
        }
    }
}

/** A relic's liabilities as threat-striped blocks under the body (none when the card has rules or no costs). */
@Composable
private fun rewardCardCosts(
    presentation: RewardCardPresentation,
    spec: RewardCardSpec,
    start: Dp,
    gap: Dp,
    rowsEnd: (Dp) -> Dp,
    plates: RewardFacePlates?,
    index: Int,
    typography: InterfaceTypography,
    roles: KkRolePalette,
    language: AppLanguage,
): (@Composable (Float, Dp) -> Unit)? = if (presentation.rules.isEmpty() && presentation.liabilities.isNotEmpty()) {
    { fit, lowest ->
        val font = spec.font * fit
        Column(Modifier.fillMaxWidth().padding(start = start, end = rowsEnd(lowest))
            .testTag("kinetickk.gameplay.choice.${index + 1}.cost"),
            verticalArrangement = Arrangement.spacedBy(gap * fit)) {
            presentation.liabilities.forEachIndexed { line, liability ->
                RewardCostBlock(
                    liability,
                    if (spec.compact) null else language.text(OverlayRedesignText.Cost),
                    typography.bodyStyle((if (spec.compact) 12.5f else 13.5f) * font, lineHeightEm = spec.lineHeight, color = roles.threat),
                    typography.monoStyle((if (spec.compact) 8.5f else 9.5f) * font, weight = FontWeight.Bold, color = roles.threat),
                    roles.threat,
                    Modifier.fillMaxWidth().facePlate(plates, "liability$line"),
                )
            }
        }
    }
} else {
    null
}

/** The card face, its text plates and the rarity frame, drawn behind the card's text. */
@Composable
@NonRestartableComposable
private fun RewardCardFace(
    plates: RewardFacePlates?,
    selection: Float,
    scrollState: ScrollState,
    burst: () -> Float,
    bandMeasurer: CanvasTextMeasurer,
    presentation: RewardCardPresentation,
    accent: Color,
    clock: State<Float>,
    facePaths: KkPathCache,
    phoneDeal: Boolean,
    look: RewardCardLook,
    artCache: RewardArtCache,
    paths: KkPathCache,
) {
    Canvas(Modifier.fillMaxSize().onPlaced { plates?.card = it }) {
        // The face and its plates use the current selection together.
        val face = if (selection > 0.5f) Kk.Ink3 else Kk.Ink2
        // The plates are measured first: the art lays itself out in the room the text leaves.
        plates?.prepare(this, face, scrollState.value.toFloat())
        val dealt = burst()
        drawRewardCardFace(bandMeasurer, presentation, accent, clock.value, selection, dealt, facePaths, phoneDeal, look, artCache,
            plates?.textBottom ?: Float.NaN, plates?.footerTop ?: Float.NaN, frame = false)
        if (plates != null) {
            drawRewardFacePlates(plates, paths.slab(0, RewardFaceSpecs.bounds(size.width, size.height), RewardCardCutDp.dp.toPx(), KkSlabKind.SLAB))
        }
        // The rarity frame last, over the plates, so it stays whole round the card.
        drawKkCardFrame(RewardFaceSpecs.bounds(size.width, size.height), clock.value, look.style, dealt)
    }
}

/** Identity beside the icon plate: a relic's keys, then the header chips. */
@Composable
@NonRestartableComposable
private fun RewardCardHeaderChips(
    presentation: RewardCardPresentation,
    spec: RewardCardSpec,
    maxWidth: Dp,
    declineShift: Dp,
    plateSize: Dp,
    plates: RewardFacePlates?,
    index: Int,
    typography: InterfaceTypography,
    roles: KkRolePalette,
    textScale: Float,
) {
    // Identity beside the icon: a relic's keys, then an active's slot and lane, Risk, Reborn, Heirloom and tag chips.
    val chipsLeft = spec.dp(spec.plateX + spec.plateSize + spec.chipsGap) + declineShift
    val chipsWidth = (maxWidth - chipsLeft - spec.dp(spec.end)).coerceAtLeast(0.dp)
    val keys = presentation.keys
    // Phone relic cards keep the keys and the Heirloom mark; the attuned tags read on the board (and in the Codex).
    val headerChips = if (spec.compact) presentation.chips.filter { it.kind != RewardChipKind.ATTUNED } else presentation.chips
    if (headerChips.isNotEmpty() || keys.isNotEmpty()) {
        Column(
            Modifier.offset(chipsLeft, spec.dp(spec.plateY)).width(chipsWidth).height(plateSize + spec.dp(6f))
                .facePlate(plates, "chips").testTag("kinetickk.gameplay.choice.${index + 1}.chips"),
            // Centered against the icon plate, so a card with few chips leaves no empty column under them.
            verticalArrangement = Arrangement.spacedBy(spec.dp(if (spec.compact) 4f else 6f), Alignment.CenterVertically),
        ) {
            if (keys.isNotEmpty()) RewardKeyWords(keys, typography, spec.sp(if (spec.compact) 9f else 10.5f), roles)
            val rows = if (keys.isEmpty()) (if (spec.compact) 2 else 3) else (if (spec.compact) 1 else 2)
            RewardChipFlow(headerChips, spec.chips, textScale, Modifier.fillMaxWidth(), maxRows = rows,
                gapDp = if (spec.compact) 4f else 5f)
        }
    }
}

/** The outcome tag, footer tags and forecast, pinned at the bottom of the card outside the scrolling body. */
@Composable
@NonRestartableComposable
private fun BoxScope.RewardCardFooter(
    presentation: RewardCardPresentation,
    spec: RewardCardSpec,
    start: Dp,
    end: Dp,
    bodyBottom: Dp,
    footerHeight: Dp,
    plates: RewardFacePlates?,
    index: Int,
    footerTags: List<String>,
    forecast: List<RewardChip>,
    textScale: Float,
) {
    // The outcome tag and the forecast stay pinned at the bottom of the card, outside the scrolling body.
    Row(
        Modifier.align(Alignment.BottomStart)
            .padding(start = start - spec.dp(4f), end = end - spec.dp(6f), bottom = bodyBottom)
            .height(footerHeight)
            .facePlate(plates, "footer")
            .testTag("kinetickk.gameplay.choice.${index + 1}.footer"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presentation.action?.let { action ->
            OverlayTag(
                action.label,
                variant = when (action.tone) {
                    RewardTone.THREAT -> KkTagVariant.THREAT
                    RewardTone.YOU -> KkTagVariant.YOU
                    else -> KkTagVariant.LINE
                },
                heightDp = spec.chips.heightDp,
                fontSize = if (spec.compact) 11.5f else 13f,
                textScale = textScale,
            )
        }
        footerTags.forEach { tag -> OverlayTag(tag, variant = KkTagVariant.LINE, textScale = textScale) }
        if (forecast.isNotEmpty()) {
            RewardChipFlow(forecast, spec.chips, textScale,
                Modifier.weight(1f).testTag("kinetickk.gameplay.choice.${index + 1}.forecast"), gapDp = if (spec.compact) 4f else 5f)
        }
    }
}

/** The New stamp of a first discovery: on the icon plate's corner on the board, on the top edge of a phone card. */
@Composable
@NonRestartableComposable
private fun BoxScope.RewardCardNewStamp(
    presentation: RewardCardPresentation,
    spec: RewardCardSpec,
    language: AppLanguage,
    textScale: Float,
    index: Int,
) {
    // On the board the stamp sits on the icon plate's lower corner, clear of the chips and the
    // name at any text size (its bottom edge is fixed; a larger stamp grows up over the icon).
    // An active item's plate keeps its lower band for the rank pips: there the stamp sits on the
    // plate's upper corner, below the band, and a larger stamp grows down over the glyph.
    // Phone cards have no room beside their small icon: the stamp rides on the top edge, its
    // bottom fixed just inside the frame and above the band's labels and pips, so a larger
    // stamp grows up and away from the card.
    if (spec.compact) {
        val overlap = spec.dp(RewardNewStampOverlapDp)
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = spec.dp(14f)).layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(placeable.width, 0) { placeable.place(0, overlap.roundToPx() - placeable.height) }
            },
        ) {
            DiscoveryBadge(
                language.text(GameplayText.NewDiscovery), min(textScale * 0.7f, RewardNewStampMaxScale),
                Modifier.testTag("kinetickk.gameplay.choice.${index + 1}.new"),
            )
        }
    } else {
        Box(
            Modifier.offset(spec.dp(spec.plateX - 8f), spec.dp(spec.plateY)).height(spec.dp(spec.plateSize + 6f)),
            contentAlignment = if (presentation.item?.active != null) Alignment.TopStart else Alignment.BottomStart,
        ) {
            DiscoveryBadge(
                language.text(GameplayText.NewDiscovery), textScale * 0.9f,
                Modifier.testTag("kinetickk.gameplay.choice.${index + 1}.new"),
            )
        }
    }
}

/** How far (board dp) a phone card's New stamp reaches into the card: the frame, never the band labels. */
internal const val RewardNewStampOverlapDp = 3f

/** The largest phone New stamp (DiscoveryBadge scale), so it stays a sticker on the edge at any text size. */
private const val RewardNewStampMaxScale = 0.85f

/** Native scrolling handles touch; mouse content dragging is adapted here because
 * desktop scrollable intentionally leaves mouse drags to its child. One clickable
 * remains responsible for activation, with every drag cancelling its release. */
internal fun Modifier.rewardDragGestures(enabled: Boolean, scrollState: ScrollState): Modifier = pointerInput(enabled, scrollState) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val press = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var dragged = false
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == press.id }
                ?: break
            if ((change.position - press.position).getDistance() > viewConfiguration.touchSlop) dragged = true
            if (dragged && change.pressed && change.type == PointerType.Mouse) {
                val delta = change.position.y - change.previousPosition.y
                if (delta != 0f) {
                    scrollState.dispatchRawDelta(-delta)
                    change.consume()
                }
            }
            if (!change.pressed) {
                if (dragged) change.consume()
                break
            }
        }
    }
}
