// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.roundToInt

/** Header: Back, title, the Codex tabs, then the tab's progress chip (Catalog: phones only) and rule (!). */
@Composable
internal fun CodexHeader(
    tab: CodexTab,
    model: CodexRenderModel,
    catalog: UiCatalogSnapshot,
    typography: InterfaceTypography,
    ui: Float,
    controlScale: Float,
    availableWidth: Dp,
    wide: Boolean,
    compactHeader: Boolean,
    compactTabs: Boolean,
    sheetVisible: Boolean,
    collectionDiscovered: Int,
    collectionTotal: Int,
    onClose: () -> Unit,
    selectTab: (CodexTab) -> Unit,
) {
    val language = LocalAppLanguage.current
    val policy = catalog.build.policy
    Row(Modifier.fillMaxWidth().heightIn(min = if (wide) 60.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
        KkButton(language.text(SessionRedesignText.BACK), onClick = onClose, modifier = Modifier.testTag("codex-close"),
            variant = KkButtonVariant.GHOST, size = KkButtonSize.SM, enabled = !sheetVisible, textScale = ui)
        Spacer(Modifier.width(14.dp))
        if (!compactHeader) {
            Box(Modifier.width(1.dp).height(28.dp).background(Kk.Line2))
            Spacer(Modifier.width(14.dp))
            val titleSize = if (wide) 44f else if (availableWidth < 600.dp) 26f else 32f
            if (wide) BasicText(language.text(SessionText.CODEX).uppercase(), style = typography.condStyle(titleSize, color = Kk.Bone), softWrap = false, maxLines = 1)
            // Phones: larger text grows the controls around the title, so it shrinks to fit whole.
            else Box(Modifier.weight(1f)) {
                CodexFitText(language.text(SessionText.CODEX), { size -> typography.condStyle(size, color = Kk.Bone) }, titleSize, 14f)
            }
            Spacer(Modifier.width(if (wide) 22.dp else 8.dp))
        }
        if (compactHeader || wide) CodexNavigationTabs(tab, controlScale, compactTabs, Modifier.weight(1f)) { selectTab(it) }
        when (tab) {
            // Wide screens list every section's count in the left nav; the header repeats it only on phones.
            CodexTab.CATALOG -> if (!wide) {
                CodexChip("$collectionDiscovered/$collectionTotal", language.text(SessionText.COLLECTION_PROGRESS, collectionDiscovered, collectionTotal), ui,
                    Modifier.padding(start = 8.dp).testTag("codex-collection-progress"))
            }
            CodexTab.REACTIONS -> {
                KkInfoButton(language.text(CodexText.REACTION_RULE), Modifier.padding(horizontal = 8.dp), placement = KkTooltipPlacement.BELOW, textScale = ui)
                val found = ReactionId.entries.count(model::isReactionDiscovered)
                CodexChip("$found/${ReactionId.entries.size}", language.text(CodexText.REACTIONS_PROGRESS, found, ReactionId.entries.size), ui,
                    Modifier.testTag("codex-reactions-progress"), label = if (wide) language.text(CodexText.REACTIONS_PROGRESS_LABEL) else null)
            }
            CodexTab.PAIRINGS -> {
                KkInfoButton(language.text(CodexText.PAIRING_RULE, ((policy.harmonyScale - 1f) * 100f).roundToInt(), ((1f - policy.dissonanceScale) * 100f).roundToInt()),
                    Modifier.padding(horizontal = 8.dp), placement = KkTooltipPlacement.BELOW, textScale = ui)
                val found = catalog.build.pairings.count { model.isPairingDiscovered(it.id) }
                CodexChip("$found/${catalog.build.pairings.size}", language.text(CodexText.PAIRINGS_PROGRESS, found, catalog.build.pairings.size), ui,
                    Modifier.testTag("codex-pairings-progress"), label = if (wide) language.text(CodexText.PAIRINGS_PROGRESS_LABEL) else null)
            }
            // Tags: each section carries its own rule.
            CodexTab.TAGS, CodexTab.BUILD -> Unit
        }
    }
}
