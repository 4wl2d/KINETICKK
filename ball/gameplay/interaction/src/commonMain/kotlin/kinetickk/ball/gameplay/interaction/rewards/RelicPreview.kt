// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kinetickk.foundation.design.drawKkThreatHatch
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkButtonSize
import kinetickk.foundation.design.KkButtonVariant
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.wideStyle
import kotlin.math.max
import kinetickk.ball.gameplay.interaction.overlay.*

@Composable
internal fun RelicPreviewSide(
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
