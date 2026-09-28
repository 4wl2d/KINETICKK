// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.fitKkText
import kinetickk.ball.profile.interaction.ProfilePanel
import kinetickk.ball.profile.interaction.ProfileSlabButton
import kinetickk.ball.profile.interaction.dp
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.profileHeaderBackWidth
import kinetickk.ball.profile.interaction.profileScrollCue
import kinetickk.ball.profile.interaction.ProfileTextProbe
import kinetickk.ball.profile.interaction.drawProfileText
import kinetickk.ball.profile.interaction.profileTextScale
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Latest geometry for the backdrop pass (written during composition). */
internal class RebirthLayoutHolder {
    var layout: RebirthLayout? = null
}

/**
 * The Rebirth screen themed by its target tier. [timeSeconds] pins the ambient clock (tests and
 * captures); null runs it from the frame clock. [advanceProgress] draws the advance sequence over
 * the screen (0 = none).
 */
@Composable
internal fun RebirthContent(
    model: RebirthRenderModel,
    confirmationArmed: Boolean,
    textScale: Float,
    onAction: (RebirthAction) -> Unit,
    timeSeconds: Float? = null,
    advanceProgress: Float = 0f,
    holder: RebirthLayoutHolder = remember { RebirthLayoutHolder() },
) {
    // [textScale] is the player's text-size setting; 1.25 renders the boards' size.
    val scale = profileTextScale(textScale)
    val language = LocalAppLanguage.current
    val baseRoles = LocalKkRolePalette.current
    val theme = remember(model.targetTier, baseRoles) { RebirthTheme(model.targetTier, baseRoles) }
    val clock = remember { mutableFloatStateOf(timeSeconds ?: 0f) }
    LaunchedEffect(timeSeconds) {
        if (timeSeconds != null) {
            clock.floatValue = timeSeconds
            return@LaunchedEffect
        }
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0L) start = now
                clock.floatValue = rebirthAmbientTime((now - start) / 1_000_000_000f)
            }
        }
    }
    val state = model.actionState(confirmationArmed)
    CompositionLocalProvider(LocalKkRolePalette provides theme.roles) {
      Box(Modifier.fillMaxSize()) {
        ProfilePanel(
            title = language.text(ProfileText.RebirthTitle),
            count = null,
            info = null,
            matter = model.matter,
            scale = scale,
            tag = "profile-rebirth",
            onBack = { onAction(RebirthAction.Back) },
            background = { frame ->
                drawRebirthBackdrop(frame, theme, holder.layout, clock.floatValue, state == RebirthActionState.ARMED)
            },
        ) { frame ->
            val measurer = rememberKkCanvasMeasurer(scale)
            // The big numeral fills a fixed block, so it ignores the text-size setting.
            val numeralMeasurer = rememberKkCanvasMeasurer(1f)
            val type = rebirthType(frame.mode)
            val backWidth = profileHeaderBackWidth(measurer, frame, language.text(ProfileScreensRedesignText.Back))
            val layout = rebirthLayout(frame, model.minimumTier, model.maximumTier, model.targetTier, scale, backWidth)
            SideEffect { holder.layout = layout }
            val viewport = layout.viewport
            val scroll = rememberScrollState()
            Box(
                Modifier.offset { IntOffset(viewport.left.roundToInt(), viewport.top.roundToInt()) }
                    .size(frame.dp(viewport.width), frame.dp(viewport.height))
                    .profileScrollCue(scroll, theme.background, frame.d(28f))
                    .verticalScroll(scroll)
                    .testTag("profile-rebirth-scroll"),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(frame.dp(layout.contentHeight))
                        .drawBehind {
                            translate(-viewport.left, -viewport.top) {
                                drawRebirthSection(measurer, numeralMeasurer, frame, type, layout, model, theme, language)
                                drawRebirthTable(measurer, frame, type, layout, model, theme, language)
                            }
                        },
                ) {
                    val ladder = layout.ladder
                    Box(
                        Modifier.offset { IntOffset((ladder.left - viewport.left).roundToInt(), (ladder.top - viewport.top).roundToInt()) }
                            .size(frame.dp(ladder.width), frame.dp(ladder.height))
                            .testTag("profile-rebirth-ladder")
                            .semantics {
                                contentDescription = language.text(ProfileScreensRedesignText.TierLadder, model.minimumTier,
                                    model.maximumTier, model.current.tier)
                            },
                    )
                    if (!layout.actionPinned) {
                        RebirthActionRow(frame, type, layout, model, state, scale, language, Offset(viewport.left, viewport.top), onAction)
                    }
                }
            }
            if (layout.actionPinned) {
                // Pinned Advance: a band in the theme's ground under the scrolling content.
                val band = layout.viewport.bottom
                Box(Modifier.offset { IntOffset(0, band.roundToInt()) }
                    .size(frame.dp(frame.width), frame.dp(frame.height - band))
                    .drawBehind {
                        drawRect(theme.background)
                        drawLine(Kk.Line2, Offset.Zero, Offset(size.width, 0f), frame.density)
                    })
                RebirthActionRow(frame, type, layout, model, state, scale, language, Offset.Zero, onAction)
            }
        }
        if (advanceProgress > 0f && advanceProgress < 1f) {
            val tier = kkIntString(model.targetTier)
            val direction = (if (model.isMaximumTier) model.current else model.next).directive.displayName.localizedContent(language)
            RebirthAdvanceOverlay(advanceProgress, theme, tier, direction)
        }
      }
    }
}

/** Advance (or Confirm/Locked/Max) with its (!) info; [origin] is the parent's screen position. */
@Composable
private fun RebirthActionRow(
    frame: ProfileFrame,
    type: RebirthType,
    layout: RebirthLayout,
    model: RebirthRenderModel,
    state: RebirthActionState,
    scale: Float,
    language: AppLanguage,
    origin: Offset,
    onAction: (RebirthAction) -> Unit,
) {
    val action = layout.action
    val label = when (state) {
        RebirthActionState.READY -> language.text(ProfileScreensRedesignText.Advance)
        RebirthActionState.ARMED -> language.text(ProfileScreensRedesignText.Confirm)
        RebirthActionState.LOCKED -> language.text(ProfileScreensRedesignText.Locked)
        RebirthActionState.MAXIMUM -> language.text(ProfileScreensRedesignText.MaxTier)
    }
    ProfileSlabButton(
        label = label,
        onClick = { onAction(RebirthAction.AdvanceRequested) },
        frame = frame,
        tag = "profile-rebirth-advance",
        size = if (frame.regular) KkButtonSize.LG else KkButtonSize.MD,
        fontSize = type.action,
        enabled = state == RebirthActionState.READY || state == RebirthActionState.ARMED,
        locked = state == RebirthActionState.LOCKED,
        armed = state == RebirthActionState.ARMED,
        textScale = scale,
        contentDescription = if (state == RebirthActionState.READY || state == RebirthActionState.ARMED) {
            "$label ${language.text(ProfileScreensRedesignText.TierTag, model.next.tier)}"
        } else {
            label
        },
        modifier = Modifier.offset { IntOffset((action.left - origin.x).roundToInt(), (action.top - origin.y).roundToInt()) }
            .size(frame.dp(action.width), frame.dp(action.height)),
    )
    val info = layout.info
    Box(Modifier.offset { IntOffset((info.left - origin.x).roundToInt(), (info.top - origin.y).roundToInt()) }) {
        KkInfoButton(rebirthInfoText(model, state, language), Modifier.testTag("profile-rebirth-info"),
            placement = KkTooltipPlacement.ABOVE_END, textScale = scale)
    }
}

/** The advance sequence over everything (header included) at [progress] 0..1 of 1.6 s. */
@Composable
internal fun RebirthAdvanceOverlay(progress: Float, theme: RebirthTheme, tier: String, direction: String) {
    val language = LocalAppLanguage.current
    val label = language.text(ProfileScreensRedesignText.AdvanceLabel)
    val measurer = rememberKkCanvasMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val frame = remember(constraints.maxWidth, constraints.maxHeight, density) {
            kinetickk.ball.profile.interaction.profileFrame(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), density)
        }
        Box(Modifier.fillMaxSize().drawBehind { drawRebirthAdvance(measurer, frame, progress, theme, tier, direction, label) })
    }
}

/** Strike-through thickness for the "from" numeral: the board's 8 px on a 52 px (58 × 0.9) box. */
internal fun rebirthStrikeThickness(numeralBoxHeight: Float): Float = numeralBoxHeight * (8f / 52.2f)

/** The (!) text: what the game keeps, preceded by why Advance is unavailable. */
internal fun rebirthInfoText(model: RebirthRenderModel, state: RebirthActionState, language: AppLanguage): String {
    val keeps = language.text(ProfileScreensRedesignText.RebirthKeeps)
    return when (state) {
        RebirthActionState.LOCKED -> language.text(ProfileScreensRedesignText.RebirthLocked, model.current.tier) + " " + keeps
        RebirthActionState.MAXIMUM -> language.text(ProfileScreensRedesignText.RebirthMaximum) + " " + keeps
        else -> keeps
    }
}

private fun DrawScope.drawRebirthSection(
    measurer: CanvasTextMeasurer,
    numeralMeasurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: RebirthType,
    layout: RebirthLayout,
    model: RebirthRenderModel,
    theme: RebirthTheme,
    language: AppLanguage,
) {
    val k = frame.k
    val accent = theme.accent
    if (!model.isMaximumTier) {
        val fromLayout = measureKkText(measurer, kkIntString(model.current.tier),
            measurer.typography.wideStyle(type.fromNumeral * k, tabular = true))
        val from = layout.from
        drawProfileText(fromLayout, from.left, from.top, Kk.Mute, "rebirth.from.numeral")
        // The strike keeps the board's proportion (8 px on a 58 px numeral) so small numerals
        // stay readable on phones.
        val barCenter = Offset(from.left + fromLayout.size.width * 0.5f, from.top + fromLayout.kkBoxHeight * 0.46f)
        val barHeight = rebirthStrikeThickness(fromLayout.kkBoxHeight)
        rotate(-12f, barCenter) {
            drawRect(accent, Offset(from.left - barHeight, barCenter.y - barHeight * 0.5f),
                Size(fromLayout.size.width + barHeight * 2f, barHeight))
        }
        val fromNameLeft = from.left + fromLayout.size.width + frame.d(14f)
        val fromName = fitKkText(measurer, model.current.directive.displayName.localizedContent(language), type.fromName * k,
            from.right - fromNameLeft, minFactor = 0.5f) { measurer.typography.condStyle(it) }
        drawProfileText(fromName, fromNameLeft, from.top + fromLayout.kkBoxHeight * 0.5f, Kk.Mute, "rebirth.from.name",
            valign = KkVAlign.CENTER)
    }
    // The numeral in two halves split along a slash, the lower half offset (12, 8).
    val numeral = layout.numeral
    val size = if (theme.eventHorizon) type.numeralMax else type.numeral
    val numeralLayout = measureKkText(numeralMeasurer, kkIntString(model.targetTier),
        numeralMeasurer.typography.wideStyle(size * k, tabular = true, lineHeightEm = numeral.height / (size * frame.unit)))
    val width = numeralLayout.size.width.toFloat()
    val height = numeral.height
    val left = numeral.left
    val top = numeral.top
    clipPath(NumeralPaths.upper(left, top, width, height)) {
        drawKkText(numeralLayout, left, top, accent)
    }
    val dx = frame.d(12f)
    val dy = frame.d(8f)
    clipPath(NumeralPaths.lower(left + dx, top + dy, width, height)) {
        drawKkText(numeralLayout, left + dx, top + dy, accent)
    }
    val tipX = left + width * 0.34f
    val tipY = top + height * 0.545f
    drawPath(NumeralPaths.wedge(0f, tipY + frame.d(3f), tipX, tipY, frame.d(6f)), Kk.Bone)

    val target = if (model.isMaximumTier) model.current else model.next
    // The direction name clears the measured numeral (board: 250 px for one digit, 400 for two).
    val nameLeft = maxOf(layout.name.left, left + width + dx + frame.d(8f))
    val nameLayout = rebirthDirectiveLayout(measurer, frame, type, target.directive.displayName.localizedContent(language),
        (layout.name.right - nameLeft).coerceAtLeast(frame.d(40f)))
    drawProfileText(nameLayout, nameLeft, layout.name.top, Kk.Bone, "rebirth.directive")
    val tierTag = drawKkTag(measurer, language.text(ProfileScreensRedesignText.TierTag, model.targetTier),
        Offset(nameLeft, layout.name.top + nameLayout.kkBoxHeight + frame.d(12f)), heightDp = 22f * k,
        fontSize = type.tag * k, background = accent, foreground = Kk.Ink)
    ProfileTextProbe.record("rebirth.tier.tag", null, null, tierTag)

    layout.cells.forEachIndexed { index, cell ->
        val tier = model.minimumTier + index
        val tierColor = theme.tierColor(tier)
        // The board's ladder cells render upright (their entrance animation holds `transform: none`).
        val state = model.tierCell(tier)
        val striped = tier >= KkRebirthTiers.MaxTier
        var fg: Color
        when {
            striped -> {
                drawKkStripes(cell, Color.White, Color.Black, kind = KkFillKind.BADGE_STRIPES)
                fg = Color.Black
            }
            state == RebirthTierCell.CLEARED -> { drawRect(tierColor, cell.topLeft, cell.size); fg = Kk.Ink }
            state == RebirthTierCell.CURRENT -> { drawRect(Kk.Bone, cell.topLeft, cell.size); fg = Kk.Ink }
            state == RebirthTierCell.NEXT -> { drawRect(Kk.Ink2, cell.topLeft, cell.size); fg = tierColor }
            else -> { drawRect(Kk.Ink2, cell.topLeft, cell.size); fg = Kk.Mute2 }
        }
        val ring = when {
            state == RebirthTierCell.NEXT -> tierColor
            state == RebirthTierCell.CURRENT && striped -> Kk.Bone
            state == RebirthTierCell.LATER && !striped -> tierColor.copy(alpha = 0.33f)
            else -> Color.Unspecified
        }
        val ringWidth = if (ring == Color.Unspecified) 0f else frame.d(if (state == RebirthTierCell.LATER) 1.5f else 3f)
        if (ring != Color.Unspecified) {
            val inner = cell.deflate(ringWidth * 0.5f)
            drawRect(ring, inner.topLeft, inner.size, style = kkStroke(ringWidth))
        }
        // The numeral fits inside the cell (and its ring) at every text size.
        val chip = striped && rebirthLadderChip(frame, cell)
        val inset = ringWidth + if (chip) 0f else frame.d(1f)
        val pad = if (chip) frame.d(2f) else 0f
        val number = rebirthLadderNumberLayout(measurer, frame, type, tier, (cell.width - (inset + pad) * 2f).coerceAtLeast(1f))
        if (chip) {
            // Narrow cells carry the striped goal tier's numeral on a solid bone band across the
            // cell: the 8 dp stripes would otherwise dissolve a numeral of about their size.
            val halfHeight = minOf(number.kkBoxHeight * 0.5f + pad, cell.height * 0.5f - inset)
            val chipRect = Rect(cell.left + inset, cell.center.y - halfHeight, cell.right - inset, cell.center.y + halfHeight)
            drawRect(Kk.Bone, chipRect.topLeft, chipRect.size)
            ProfileTextProbe.record("rebirth.ladder.chip", tier, null, chipRect, Kk.Bone)
            fg = Kk.Ink
        }
        drawProfileText(number, cell.center.x, cell.center.y, fg, "rebirth.ladder.number", tier, align = KkAlign.CENTER,
            valign = KkVAlign.CENTER)
    }
}

/** The striped goal cell puts its numeral on a chip when the cell is narrower than 56 dp (phones, small windows). */
internal fun rebirthLadderChip(frame: ProfileFrame, cell: Rect): Boolean = cell.width < frame.density * 56f

/** A ladder cell's tier numeral, shrunk to fit [width]. */
internal fun rebirthLadderNumberLayout(measurer: CanvasTextMeasurer, frame: ProfileFrame, type: RebirthType, tier: Int, width: Float) =
    fitKkText(measurer, kkIntString(tier), type.ladderNumber * frame.k, width, uppercase = false, minFactor = 0.4f) {
        measurer.typography.wideStyle(it, tabular = true)
    }

/** The target tier's directive name beside the big numeral, shrunk to fit [width] (never cut). */
internal fun rebirthDirectiveLayout(measurer: CanvasTextMeasurer, frame: ProfileFrame, type: RebirthType, name: String, width: Float) =
    fitKkText(measurer, name, type.name * frame.k, width, minFactor = 0.4f) { measurer.typography.condStyle(it) }

/** A comparison table's section title, shrunk to fit [width] (never cut). */
internal fun rebirthSectionLayout(measurer: CanvasTextMeasurer, frame: ProfileFrame, type: RebirthType, title: String, width: Float) =
    fitKkText(measurer, title, type.tableLabel * frame.k, width, minFactor = 0.5f) { measurer.typography.labelStyle(it) }

/** Width in px of a comparison table's value column. */
internal fun rebirthValueColumn(frame: ProfileFrame, scale: Float): Float =
    // Value columns (70 px on the board) widen with the text-size setting.
    frame.d(70f) * (if (frame.regular) 1f else 0.8f) * scale.coerceAtLeast(1f)

private fun DrawScope.drawRebirthTable(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: RebirthType,
    layout: RebirthLayout,
    model: RebirthRenderModel,
    theme: RebirthTheme,
    language: AppLanguage,
) {
    val k = frame.k
    val header = layout.tableHeader
    val column = rebirthValueColumn(frame, measurer.scale)
    val headStyle = measurer.typography.wideStyle(type.tableHead * k, tabular = true)
    val bottomPad = frame.d(8f)
    val hostileTitle = rebirthSectionLayout(measurer, frame, type, language.text(ProfileText.HostileEscalation), header.width - column * 2f)
    drawProfileText(hostileTitle, header.left, header.bottom - bottomPad, Kk.Bone, "rebirth.section", valign = KkVAlign.BASELINE)
    val currentHead = measureKkText(measurer, kkIntString(model.current.tier), headStyle)
    val nextHead = measureKkText(measurer, kkIntString(model.targetTier), headStyle)
    drawProfileText(currentHead, header.right - column, header.bottom - bottomPad, Kk.Mute, "rebirth.head", align = KkAlign.END,
        valign = KkVAlign.BASELINE)
    drawProfileText(nextHead, header.right, header.bottom - bottomPad, theme.accent, "rebirth.head", align = KkAlign.END,
        valign = KkVAlign.BASELINE)
    drawLine(Kk.Line2, Offset(header.left, header.bottom), Offset(header.right, header.bottom), frame.density)

    val current = model.current
    val next = if (model.isMaximumTier) model.current else model.next
    val hostile = rebirthHostileRows(current, next, language)
    hostile.forEachIndexed { index, row ->
        drawRebirthRow(measurer, frame, type, layout.hostileRows[index], column, row, if (row.change == RebirthChange.UP) theme.threat else Kk.Mute)
    }
    val label = layout.compensationLabel
    val compensationTitle = rebirthSectionLayout(measurer, frame, type, language.text(ProfileText.CycleCompensation), label.width)
    drawProfileText(compensationTitle, label.left, label.bottom - bottomPad, Kk.Bone, "rebirth.section", valign = KkVAlign.BASELINE)
    drawLine(Kk.Line2, Offset(label.left, label.bottom), Offset(label.right, label.bottom), frame.density)
    rebirthCompensationRows(current, next, language).forEachIndexed { index, row ->
        drawRebirthRow(measurer, frame, type, layout.compensationRows[index], column, row,
            if (row.change == RebirthChange.UP) theme.accent else Kk.Mute)
    }
}

private fun DrawScope.drawRebirthRow(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: RebirthType,
    rect: Rect,
    column: Float,
    row: RebirthRow,
    nextColor: Color,
) {
    val k = frame.k
    val cy = rect.center.y
    val label = fitKkText(measurer, row.label, type.body * k, rect.width - column * 2f - frame.d(8f), uppercase = false,
        minFactor = 0.55f) { measurer.typography.bodyStyle(it) }
    drawProfileText(label, rect.left, cy, Kk.Bone, "rebirth.row.label", valign = KkVAlign.CENTER)
    val current = fitKkText(measurer, row.current, type.mono * k, column - frame.d(6f), minFactor = 0.6f) {
        measurer.typography.monoStyle(it)
    }
    drawProfileText(current, rect.right - column, cy, Kk.Mute, "rebirth.row.value", align = KkAlign.END, valign = KkVAlign.CENTER)
    val next = fitKkText(measurer, row.next, type.mono * k, column - frame.d(6f), minFactor = 0.6f) {
        measurer.typography.monoStyle(it, weight = FontWeight.Bold)
    }
    drawProfileText(next, rect.right, cy, nextColor, "rebirth.row.value", align = KkAlign.END, valign = KkVAlign.CENTER)
    drawLine(Kk.Line, Offset(rect.left, rect.bottom), Offset(rect.right, rect.bottom), frame.density)
}

/** One comparison row: label, current and next values and the direction of the change. */
internal class RebirthRow(val label: String, val current: String, val next: String, val change: RebirthChange)

/** Hostile modifiers for the current and next tier, from the game's tier profiles. */
internal fun rebirthHostileRows(current: RebirthProfile, next: RebirthProfile, language: AppLanguage): List<RebirthRow> = listOf(
    RebirthRow(language.text(ProfileText.OpeningHostiles), current.openingEnemyCount.toString(), next.openingEnemyCount.toString(),
        rebirthChange(current.openingEnemyCount.toFloat(), next.openingEnemyCount.toFloat())),
    multiplierRow(language.text(ProfileText.EnemyCap), current.enemyCapMultiplier, next.enemyCapMultiplier, language),
    multiplierRow(language.text(ProfileText.SpawnRate), current.spawnRateMultiplier, next.spawnRateMultiplier, language),
    multiplierRow(language.text(ProfileText.EnemyIntegrity), current.enemyHealthMultiplier, next.enemyHealthMultiplier, language),
    multiplierRow(language.text(ProfileText.EnemySpeed), current.enemySpeedMultiplier, next.enemySpeedMultiplier, language),
    multiplierRow(language.text(ProfileText.IncomingDamage), current.incomingDamageMultiplier, next.incomingDamageMultiplier, language),
    RebirthRow(language.text(ProfileText.ThreatAdvance),
        language.text(ProfileText.Seconds, current.threatTimeOffsetSeconds.roundToInt()),
        language.text(ProfileText.Seconds, next.threatTimeOffsetSeconds.roundToInt()),
        rebirthChange(current.threatTimeOffsetSeconds, next.threatTimeOffsetSeconds)),
)

/** Compensation for the player for the current and next tier. */
internal fun rebirthCompensationRows(current: RebirthProfile, next: RebirthProfile, language: AppLanguage): List<RebirthRow> = listOf(
    multiplierRow(language.text(ProfileText.PlayerPower), current.playerPowerMultiplier, next.playerPowerMultiplier, language),
    RebirthRow(language.text(ProfileText.CoreIntegrity), "+${current.playerIntegrityBonus.roundToInt()}",
        "+${next.playerIntegrityBonus.roundToInt()}", rebirthChange(current.playerIntegrityBonus, next.playerIntegrityBonus)),
    multiplierRow(language.text(ProfileText.KineticMatter), current.matterGainMultiplier, next.matterGainMultiplier, language),
    RebirthRow(language.text(ProfileText.BonusRerolls), "+${current.bonusRerolls}", "+${next.bonusRerolls}",
        rebirthChange(current.bonusRerolls.toFloat(), next.bonusRerolls.toFloat())),
)

private fun multiplierRow(label: String, current: Float, next: Float, language: AppLanguage): RebirthRow =
    RebirthRow(label, rebirthMultiplier(current, language), rebirthMultiplier(next, language), rebirthChange(current, next))

/** `×1.10`: two decimals with the language's decimal separator. */
internal fun rebirthMultiplier(value: Float, language: AppLanguage): String {
    val hundredths = (value * 100f).roundToLong()
    val separator = if (language == AppLanguage.Russian) ',' else '.'
    return "×${hundredths / 100L}$separator${(100L + hundredths % 100L).toString().substring(1)}"
}


/** Clip paths for the split numeral and the bone wedge, rebuilt only when the geometry moves. */
private object NumeralPaths {
    private val upper = Path()
    private val lower = Path()
    private val wedge = Path()
    private val upperKey = FloatArray(5) { Float.NaN }
    private val lowerKey = FloatArray(5) { Float.NaN }
    private val wedgeKey = FloatArray(5) { Float.NaN }

    fun upper(left: Float, top: Float, width: Float, height: Float): Path {
        if (!same(upperKey, left, top, width, height, 0f)) {
            upper.rewind()
            upper.moveTo(left - width, top - height)
            upper.lineTo(left + width * 2f, top - height)
            upper.lineTo(left + width * 2f, top + height * 0.44f - height * 0.16f)
            upper.lineTo(left + width, top + height * 0.44f)
            upper.lineTo(left, top + height * 0.6f)
            upper.lineTo(left - width, top + height * 0.6f + height * 0.16f)
            upper.close()
        }
        return upper
    }

    fun lower(left: Float, top: Float, width: Float, height: Float): Path {
        if (!same(lowerKey, left, top, width, height, 0f)) {
            lower.rewind()
            lower.moveTo(left - width, top + height * 0.6f + height * 0.16f)
            lower.lineTo(left, top + height * 0.6f)
            lower.lineTo(left + width, top + height * 0.44f)
            lower.lineTo(left + width * 2f, top + height * 0.44f - height * 0.16f)
            lower.lineTo(left + width * 2f, top + height * 2f)
            lower.lineTo(left - width, top + height * 2f)
            lower.close()
        }
        return lower
    }

    fun wedge(fromX: Float, fromY: Float, tipX: Float, tipY: Float, thickness: Float): Path {
        if (!same(wedgeKey, fromX, fromY, tipX, tipY, thickness)) {
            wedge.rewind()
            wedge.moveTo(fromX, fromY - thickness * 0.5f)
            wedge.lineTo(tipX, tipY)
            wedge.lineTo(fromX, fromY + thickness * 0.5f)
            wedge.close()
        }
        return wedge
    }

    private fun same(key: FloatArray, a: Float, b: Float, c: Float, d: Float, e: Float): Boolean {
        val hit = key[0] == a && key[1] == b && key[2] == c && key[3] == d && key[4] == e
        key[0] = a; key[1] = b; key[2] = c; key[3] = d; key[4] = e
        return hit
    }
}
