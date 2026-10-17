// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.rebirthRankUnlocks
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.interaction.rewards.OverlayText
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkMutatorSigil
import kinetickk.foundation.design.KkMutatorSigilPaths
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.rememberInterfaceTypography

/**
 * The Victory report's Rebirth lines: [headline] names the rank this clear earns ("Reborn rank N
 * earned") or the Ascendant clear of the final tier; [unlocks] lists what the new rank opens (weapons,
 * characters and Relics by name, then item and Matrix node counts and the new enemy types), null when
 * it opens nothing. [ascendant] marks the final tier's clear (the report draws the crown).
 */
internal data class ReportRebirthLines(val headline: String, val unlocks: String?, val ascendant: Boolean)

/**
 * The Rebirth lines of a Victory, or null: a replayed cleared tier earns nothing new. The run started
 * at rank [GameplayRenderModel.rebirthRank]; clearing the tier at that rank earns the next one.
 */
internal fun GameplayRenderModel.reportRebirthLines(language: AppLanguage): ReportRebirthLines? {
    if (phase != GamePhase.VICTORY) return null
    val maximum = content.rebirth.maximumLevel
    if (rebirthLevel >= maximum) return ReportRebirthLines(language.text(OverlayRedesignText.AscendantCleared), null, ascendant = true)
    if (rebirthLevel < rebirthRank) return null
    val earned = rebirthLevel + 1
    val unlocks = rebirthRankUnlocks(earned, content.weapons, content.coreShapes, content.relics, content.items,
        content.matrixNodes, content.enemies)
    val parts = buildList {
        unlocks.weapons.forEach { add(it.name.localizedContent(language)) }
        unlocks.characters.forEach { add(it.displayName.localizedContent(language)) }
        unlocks.relics.forEach { add(it.name.localizedContent(language)) }
        if (unlocks.items.isNotEmpty()) add(language.text(OverlayRedesignText.UnlockedItems, unlocks.items.size))
        if (unlocks.matrixNodes.isNotEmpty()) add(language.text(OverlayRedesignText.UnlockedMatrixNodes, unlocks.matrixNodes.size))
        if (unlocks.enemies.isNotEmpty()) {
            add(language.text(OverlayRedesignText.UnlockedEnemies, unlocks.enemies.joinToString(", ") { it.name.localizedContent(language) }))
        }
    }
    return ReportRebirthLines(
        headline = language.text(OverlayRedesignText.RebornRankEarned, earned),
        unlocks = if (parts.isEmpty()) null else language.text(OverlayRedesignText.RebirthUnlocked, parts.joinToString(", ")),
        ascendant = false,
    )
}

/**
 * The Rebirth lines under the Victory stamp: the earned rank (or the Ascendant clear, after the gold
 * crown) in gold, then what the rank opens in bone.
 */
@Composable
internal fun ReportRebirthEarned(lines: ReportRebirthLines, headlineSize: Float, detailSize: Float, modifier: Modifier = Modifier) {
    val typography = rememberInterfaceTypography()
    Column(modifier.testTag("kinetickk.gameplay.results.rebirth"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (lines.ascendant) {
                Canvas(Modifier.size((headlineSize * 1.4f).dp).testTag("kinetickk.gameplay.results.ascendant")) {
                    val radius = size.minDimension * 0.5f
                    translate(size.width * 0.5f, size.height * 0.5f) {
                        drawPath(KkMutatorSigilPaths.of(KkMutatorSigil.ASCENDANT, radius), Kk.Gold)
                    }
                }
            }
            OverlayText(lines.headline, typography.labelStyle(headlineSize, color = Kk.Gold), uppercase = true)
        }
        lines.unlocks?.let { unlocks -> OverlayText(unlocks, typography.bodyStyle(detailSize, color = Kk.Bone)) }
    }
}
