// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.profileTextScale
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopPanel
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopSegment
import kinetickk.ball.profile.interaction.workshop.impl.label
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.rememberKkCanvasMeasurer

/**
 * The Weapons tab as the Workshop hosts it (its panel, header extra and background) for a fixed
 * [model] and view [state], with the detail on [segment]: the screen tests and shots drive the body
 * without a Profile port.
 */
@Composable
internal fun ArmoryContent(
    model: ArmoryRenderModel,
    weapons: List<WeaponDefinition>,
    masteries: List<WeaponMastery>,
    state: ArmoryViewState,
    textScale: Float,
    gridScroll: ScrollState,
    holder: ArmoryLayoutHolder,
    coreShapes: List<CoreShapeDefinition> = emptyList(),
    relics: List<RelicDefinition> = emptyList(),
    detailScrollStart: Int = 0,
    segment: WorkshopSegment = WorkshopSegment.UPGRADE,
    onAction: (ArmoryAction) -> Unit,
) {
    val language = LocalAppLanguage.current
    val scale = profileTextScale(textScale)
    val measurer = rememberKkCanvasMeasurer(scale)
    val title = WorkshopTab.WEAPONS.label(language)
    val pager: @Composable RowScope.(ProfileFrame) -> Unit = { frame -> ArmoryPager(frame, gridScroll, holder, scale, onAction) }
    WorkshopPanel(
        tab = WorkshopTab.WEAPONS,
        count = language.text(ProfileScreensRedesignText.CountOf, weapons.count { it.id in model.unlockedWeapons }, weapons.size),
        info = language.text(ProfileScreensRedesignText.WorkshopWeaponsInfo),
        matter = model.totalMatter,
        scale = scale,
        onBack = { onAction(ArmoryAction.Back) },
        onAction = {},
        background = { frame -> drawArmoryBackground(frame, holder.layout, measurer, title) },
        headerExtra = pager,
        headerExtraWidth = { frame -> armoryPagerWidth(frame, gridScroll, scale) },
    ) { frame, header ->
        ArmoryBody(frame, model, weapons, masteries, state, segment, textScale, gridScroll, holder, coreShapes, relics, detailScrollStart,
            gridExtra = if (header.extraInBody) ({ pager(frame) }) else null, onAction = onAction)
    }
}
