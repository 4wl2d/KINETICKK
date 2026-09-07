// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.lab.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.*
import kinetickk.ball.profile.interaction.lab.api.LabRenderModel
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*

@Composable
internal fun LabContent(model: LabRenderModel, scale: Float, onAction: (LabAction) -> Unit) {
    val language = LocalAppLanguage.current
    ProfilePanel(language.text(ProfileText.LabTitle), language.text(ProfileText.LabSummary, formatCompact(model.matter, language)),
        scale, "profile-lab", onBack = { onAction(LabAction.Back) }) { wide ->
        model.upgrades.chunked(if (wide) 2 else 1).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                row.forEach { upgrade ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProfileLabel(upgrade.name.localizedContent(language), scale, bold = true, modifier = Modifier.weight(1f))
                            ProfileLabel("${upgrade.rank}/${upgrade.maxRanks}", scale, Muted, size = 10f)
                        }
                        ProfileLabel(upgrade.description.localizedContent(language), scale, Muted, size = 11f)
                        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                            drawBar(0f, 0f, size.width, size.height, upgrade.rank.toFloat() / upgrade.maxRanks.coerceAtLeast(1), Cyan, DarkLine)
                        }
                        ProfileButton(if (upgrade.isMaxed) language.text(ProfileText.MaximumSynchrony)
                            else language.text(ProfileText.BuyMatter, formatCompact(upgrade.nextCost, language)), scale,
                            "profile-lab-buy-${upgrade.id}", Modifier.fillMaxWidth(), enabled = upgrade.isAffordable, accent = Cyan) {
                            onAction(LabAction.PurchaseRequested(upgrade.id))
                        }
                        ProfileDivider()
                    }
                }
                if (wide && row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
