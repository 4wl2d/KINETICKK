// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionText
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.profile.api.rank
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min

internal enum class CodexGridContentType {
    NOTICE, HEADING, SLOT, STAT, ROW, MATRIX, CHIPS, BLOCK, ITEM_HEAD, ITEM_LABEL, ITEM_RULE, ITEM_SPACER, ITEM_CELL,
}

/** Lazy content type of a part of the Items matrix. */
internal val CodexGridItem.contentType: CodexGridContentType
    get() = when (this) {
        is CodexGridItem.Head -> CodexGridContentType.ITEM_HEAD
        is CodexGridItem.Label -> CodexGridContentType.ITEM_LABEL
        is CodexGridItem.Rule -> CodexGridContentType.ITEM_RULE
        is CodexGridItem.Spacer -> CodexGridContentType.ITEM_SPACER
        is CodexGridItem.Cell -> CodexGridContentType.ITEM_CELL
    }

/** The banded Items matrix needs a grid at least this tall (dp): below it its header would take a third of the grid and scroll away. */
internal const val CODEX_ITEM_BANDED_MIN_GRID_DP = 300f

/**
 * A keyboard move in the Items matrix still to be brought into view: the cell keyed [target] at lazy index
 * [index] (scrolled to first when it is not composed, [scroll]) and its row's reveal ([rowStart], [lines], [gapPx]).
 * Compared by identity, so every key press is a new move even when it repeats the last one.
 */
internal class CodexMatrixMove(
    val target: String,
    val index: Int,
    val scroll: Boolean,
    val rowStart: Int,
    val lines: Int,
    val gapPx: Float,
)

/**
 * After a keyboard move to the cell keyed [targetKey], brings its row's first part (lazy index
 * [rowStart], [lines] grid lines above the cell's) into view where the row's lines down to the cell fit
 * the grid together ([gapPx] apart): the row's name then stays in sight beside a wrapped line.
 */
internal suspend fun LazyGridState.revealCodexRow(rowStart: Int, targetKey: String, lines: Int, gapPx: Float) {
    if (lines <= 0) return
    withFrameNanos { }
    val info = layoutInfo
    val start = info.visibleItemsInfo.firstOrNull { it.index == rowStart }
    if (start != null && start.offset.y >= info.viewportStartOffset) return
    val target = info.visibleItemsInfo.firstOrNull { it.key == targetKey } ?: return
    val needed = lines * (target.size.height + gapPx) + target.size.height
    if (needed <= info.viewportEndOffset - info.viewportStartOffset) scrollToItem(rowStart)
}

/** The Items matrix move a key asks for: arrows, Home / End and Page Up / Page Down; null for any other key. */
internal fun codexItemMove(key: Key): CodexItemMove? = when (key) {
    Key.DirectionLeft -> CodexItemMove.LEFT
    Key.DirectionRight -> CodexItemMove.RIGHT
    Key.DirectionUp -> CodexItemMove.UP
    Key.DirectionDown -> CodexItemMove.DOWN
    Key.MoveHome -> CodexItemMove.HOME
    Key.MoveEnd -> CodexItemMove.END
    Key.PageUp -> CodexItemMove.PAGE_UP
    Key.PageDown -> CodexItemMove.PAGE_DOWN
    else -> null
}

/**
 * Grid cell size and gap (the board's matrix cells, sized up to carry each entry's glyph), the
 * same in the collection and the Build tab. A cell holds a band at its top for a NEW stamp (left)
 * and a count badge (right) beside each other, and below it a glyph as large as a 64 dp cell's
 * centered glyph, so neither mark ever covers the glyph or makes it smaller.
 */
internal val CodexCell = 82.dp
internal val CodexCellGap = 6.dp

/**
 * Span (grid cells) of row [index] in a line of rows at least [minCells] wide: the line is filled,
 * and the rows of one line differ by at most one cell.
 */
internal fun codexRowSpan(maxLineSpan: Int, minCells: Int, index: Int): Int {
    val perLine = (maxLineSpan / minCells.coerceAtLeast(1)).coerceIn(1, maxLineSpan.coerceAtLeast(1))
    val base = maxLineSpan / perLine
    return base + if (index % perLine < maxLineSpan - base * perLine) 1 else 0
}

@Composable
internal fun CodexSlot(
    entry: CodexEntry,
    catalog: UiCatalogSnapshot,
    ui: Float,
    selection: CodexSelection,
    focus: MutableMap<String, FocusRequester>,
    band: Boolean,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
) {
    val roles = LocalKkRolePalette.current
    val measurer = codexMeasurer()
    val interaction = remember(entry.key) { MutableInteractionSource() }
    val focusedValue by interaction.collectIsFocusedAsState()
    val hoveredValue by interaction.collectIsHoveredAsState()
    val pinned = selection.pinnedKey == entry.key
    val previewed = selection.previewKey == entry.key
    val badge = entry.cellBadge()
    val localDensity = LocalDensity.current
    val density = localDensity.density
    val newLabel = LocalAppLanguage.current.text(SessionText.NEW_DISCOVERY)
    val stampFont = codexNewStampFont(measurer)
    // Every cell keeps the mark band, so all glyphs of the grid share one size and centre.
    val stamp = remember(measurer, newLabel, density) { kkStampSize(measurer, newLabel, density, stampFont) }
    val badgeFont = codexBadgeFont(ui)
    val markBand = codexMarkBand(stamp, stampFont, with(localDensity) { badgeFont.sp.toPx() }, density)
    Box(slotModifier(entry, selection, focus, interaction, onSelection).aspectRatio(1f).drawBehind {
        val gap = CodexCellGap.toPx()
        if (band) drawRect(roles.you.copy(alpha = 0.10f), Offset(-gap * 0.5f, -gap * 0.5f), Size(size.width + gap, size.height + gap))
        drawCodexCell(entry, catalog, measurer, band, hoveredValue || focusedValue, markBand)
        drawCodexSelection(pinned, previewed || focusedValue, roles)
    }) {
        // The discovery stamp and the count badge share the mark band above the glyph.
        if (entry.isNew) Box(Modifier.align(Alignment.TopStart).offset(CodexMarkInset, CodexMarkInset)
            .size((stamp.width / density).dp, (stamp.height / density).dp).testTag("codex-new-${entry.key}")
            .drawBehind { drawKkStamp(measurer, newLabel, Offset.Zero, fontSize = stampFont) })
        if (badge != null) BasicText(badge, Modifier.align(Alignment.TopEnd).padding(CodexMarkInset).testTag("codex-badge-${entry.key}")
            .background(Kk.Ink).padding(horizontal = 3.dp),
            style = measurer.typography.monoStyle(badgeFont, color = Kk.Bone), softWrap = false, maxLines = 1)
    }
}

/** Amount shown on a cell: stacks or rank in the current run, or the equipped weapon's level. */
private fun CodexEntry.cellBadge(): String? = when (val icon = icon) {
    is CodexIcon.Item -> quantity.takeIf { icon.stack > 0 }
    is CodexIcon.Relic -> quantity.takeIf { icon.rank != null }
    is CodexIcon.Weapon -> quantity.takeIf { it != CODEX_NONE }
    // An enemy's threat out of five; a Rebirth-locked one shows its padlock in that corner instead (the threat is in its facts).
    is CodexIcon.Enemy -> quantity.takeUnless { icon.locked }
    else -> null
}

/** Inset of the NEW stamp (top-left) and of the count badge (top-right) from the cell's corners. */
private val CodexMarkInset = 3.dp

/** NEW stamp font: 10 px on screen whatever the text size (cells grow with the text instead). */
internal fun codexNewStampFont(measurer: CanvasTextMeasurer): Float = 10f / measurer.scale.coerceAtLeast(0.1f)

/** Count badge font (sp) for the UI text factor [ui] (see [codexUiScale]): 9 at the default text size. */
internal fun codexBadgeFont(ui: Float): Float = CODEX_BADGE_FONT * ui

private const val CODEX_BADGE_FONT = 9f

/** Mono line height of the count badge's one-line box. */
private const val CODEX_BADGE_LINE_HEIGHT_EM = 1.35f

/** sin 6°: a stamp's rotation lowers one end of it by this much of its half width. */
private const val CODEX_STAMP_TILT = 0.1045f

/**
 * Height (px) of the mark band at the top of every cell: a NEW stamp of [stamp] size (drawn at
 * [stampFont], see [codexNewStampFont]) with its inset, its lowest rotated corner and its hard
 * shadow, or a count badge with a [badgeFontPx] mono label, whichever reaches lower, and a 2 dp gap.
 */
internal fun codexMarkBand(stamp: Size, stampFont: Float, badgeFontPx: Float, density: Float): Float {
    val inset = CodexMarkInset.value * density
    val stampBottom = inset + stamp.height + stamp.width * 0.5f * CODEX_STAMP_TILT + 3f * density * stampFont / 17f
    val badgeBottom = inset + badgeFontPx * CODEX_BADGE_LINE_HEIGHT_EM
    return max(stampBottom, badgeBottom) + 2f * density
}

/** The glyph's stack ring, frame and faint halo reach this far out, as a multiple of its radius. */
internal const val CODEX_GLYPH_REACH = 1.15f

/** A cell glyph (px from the cell's top-left): its center and radius. */
internal data class CodexGlyph(val centerY: Float, val radius: Float, val centerX: Float)

/**
 * The glyph circle of every cell of a grid of [cellPx] cells: one size and centre for all of them,
 * whether a cell carries a NEW stamp, a count badge, both or neither. It fills the cell below the
 * [bandPx] mark band (see [codexMarkBand]) down to a 3 dp margin, centered across, and is never
 * larger than a centered glyph (0.36 of the cell).
 */
internal fun codexCellGlyph(cellPx: Float, bandPx: Float, density: Float): CodexGlyph {
    val margin = 3f * density
    val reach = min(cellPx * 0.36f * CODEX_GLYPH_REACH, min(cellPx - margin - bandPx, cellPx - 2f * margin) * 0.5f).coerceAtLeast(0f)
    return CodexGlyph((bandPx + cellPx - margin) * 0.5f, reach / CODEX_GLYPH_REACH, cellPx * 0.5f)
}

@Composable
internal fun slotModifier(
    entry: CodexEntry,
    selection: CodexSelection,
    focus: MutableMap<String, FocusRequester>,
    interaction: MutableInteractionSource,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
    description: String = entry.summary,
): Modifier {
    val inputEnabled = LocalCodexInputEnabled.current
    val requester = remember(entry.key) { FocusRequester() }
    val hoveredValue by interaction.collectIsHoveredAsState()
    DisposableEffect(entry.key) {
        focus[entry.key] = requester
        onDispose {
            focus.remove(entry.key)
            onSelection { current -> current.copy(
                hoverKey = current.hoverKey?.takeUnless { it == entry.key },
                focusKey = current.focusKey?.takeUnless { it == entry.key },
            ) }
        }
    }
    LaunchedEffect(hoveredValue) {
        onSelection { current ->
            if (hoveredValue) current.copy(hoverKey = entry.key)
            else if (current.hoverKey == entry.key) current.copy(hoverKey = null) else current
        }
    }
    return Modifier.testTag("codex-slot-${entry.key}").semantics { contentDescription = description; selected = selection.pinnedKey == entry.key }
        .focusRequester(requester).focusProperties { canFocus = inputEnabled }.onFocusChanged { state ->
            onSelection { current ->
                if (state.isFocused) current.copy(focusKey = entry.key, hoverKey = null)
                else if (current.focusKey == entry.key) current.copy(focusKey = null) else current
            }
        }.hoverable(interaction, enabled = inputEnabled).clickable(enabled = inputEnabled, interactionSource = interaction, indication = null, role = Role.Button) { onSelection { it.activate(entry.key) } }
}
