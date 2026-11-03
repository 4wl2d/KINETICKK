// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DUNGEON_ASPECT_SALVAGE_MATTER
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.hud.*
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.interaction.localization.StructureHudText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text

/*
 * The dungeon trial panel's texts: the rules behind its (!) and its reward row, built once per template, aspect
 * fallback, Totem chest charges (the rules) and language, so a steady panel builds no string. The chest charges come
 * from the dungeon projection (the roll the final room pays); the panel draws the chest itself as a mark beside the
 * rewards, so the reward row keeps its length.
 */

/** Draw-thread scratch of the dungeon panel's texts. */
private object StructurePanelText {
    val rewards = HudKeyedText()
    val rules = HudKeyedText()
}

/** The rules behind the dungeon panel's (!) in [language], with the waiting Totem chest's rule when one is rolled. */
internal fun structureTrialRules(engine: GameplayRenderModel, trial: TrialProjection, language: AppLanguage): String {
    val id = trial.structure ?: return language.text(StructureHudText.Rules)
    val template = engine.content.structures.template(id)
    val fallback = engine.structureAspectFallbackMask(template)
    val charges = engine.structures.dungeon?.chestCharges ?: 0
    val key = (charges.toLong() shl 24) or (fallback.toLong() shl 16) or (id.ordinal.toLong() shl 8) or language.ordinal.toLong()
    return StructurePanelText.rules.of(engine.content, key) {
        buildList {
            if (template.trialDescription.isNotBlank()) add(template.trialDescription.localizedContent(language))
            // The chest's sentence joins the rules' paragraph: a phone tooltip has no line to spare.
            val chest = if (charges > 0) " " + language.text(StructureHudText.TotemChestRule, charges) else ""
            add(language.text(StructureHudText.Rules) + chest)
            for (index in template.rewards.indices) {
                val reward = template.rewards[index]
                if (fallback and (1 shl index) != 0) {
                    add(language.text(StructureHudText.AspectFallback, DUNGEON_ASPECT_SALVAGE_MATTER))
                    continue
                }
                val boon = reward.boonId?.let(engine.content::boon) ?: continue
                add(boon.description.localizedContent(language))
            }
        }.joinToString("\n\n")
    }
}

/**
 * The dungeon panel's reward row: its template's reward labels in [language] joined with " + " ("Relic + Rare item"),
 * built once per template and language so a steady panel builds no string.
 */
internal fun structureTrialRewards(engine: GameplayRenderModel, trial: TrialProjection, language: AppLanguage): String {
    val id = trial.structure ?: return language.text(HudRedesignText.RewardUnknown)
    val template = engine.content.structures.template(id)
    val fallback = engine.structureAspectFallbackMask(template)
    val key = (fallback.toLong() shl 16) or (id.ordinal.toLong() shl 8) or language.ordinal.toLong()
    return StructurePanelText.rewards.of(engine.content, key) {
        template.rewards.mapIndexed { index, reward ->
            (if (fallback and (1 shl index) != 0) "Matter" else reward.label).localizedContent(language)
        }.joinToString(" + ")
    }
}

/** The same ownership, capacity and content availability that decide the final-room aspect payout. */
private fun GameplayRenderModel.structureAspectFallbackMask(template: StructureTemplate): Int {
    var mask = 0
    for (index in template.rewards.indices) {
        val id = template.rewards[index].boonId ?: continue
        if (id in runBoons || runBoons.size >= ContentBounds.MAX_RUN_BOONS || content.boon(id) == null) {
            mask = mask or (1 shl index)
        }
    }
    return mask
}
