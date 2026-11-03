// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.overlay

import androidx.compose.ui.semantics.text
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.look.OverlayRelicSlot
import kinetickk.ball.gameplay.interaction.canvas.look.overlayCompact
import kinetickk.ball.gameplay.interaction.canvas.look.overlayGrouped
import kinetickk.ball.gameplay.interaction.canvas.look.overlayIcon
import kinetickk.ball.gameplay.interaction.canvas.look.overlayLevel
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.formatRunTime
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.KkIcon
import kotlin.math.roundToLong

internal fun terminalRevealProgress(elapsed: Float, delay: Float, duration: Float = 0.38f): Float {
    val fraction = ((elapsed - delay) / duration).coerceIn(0f, 1f)
    return 1f - (1f - fraction) * (1f - fraction) * (1f - fraction)
}

internal fun terminalRevealDelay(victory: Boolean): Float = if (victory) 0.18f else 0.68f
internal fun terminalActionsReady(elapsed: Float, victory: Boolean): Boolean =
    elapsed >= terminalRevealDelay(victory) + 0.32f

/** Color role of a report value (elites and lost integrity read as threat, streaks as you). */
internal enum class TerminalTone { BONE, YOU, THREAT }

internal data class TerminalStatistic(val label: GameplayText, val value: String, val tone: TerminalTone = TerminalTone.BONE)

internal data class TerminalPresentation(
    val victory: Boolean,
    val reason: String,
    val time: String,
    val kills: String,
    val matter: String,
    val weapon: String,
    val combat: List<TerminalStatistic>,
    val collection: List<TerminalStatistic>,
    val rebirth: String? = null,
    /** The Rebirth tier: from the first Rebirth on, its tag is gold (gold means Rebirth). */
    val rebirthLevel: Int = 0,
    val form: String? = null,
    val formIcon: KkIcon? = null,
    val bank: String? = null,
    val weaponIcon: KkIcon? = null,
    val weaponLevel: String? = null,
    val weaponMaxLevel: Boolean = false,
    val relics: List<OverlayRelicSlot?> = emptyList(),
    /** The bound relics' definitions in slot order, for their own runes. */
    val relicDefinitions: List<RelicDefinition?> = emptyList(),
    /** Build recap: the run's leading archetype tags, its dominant element and its reactions. */
    val recapTags: List<TerminalTag> = emptyList(),
    val dominant: TerminalElement? = null,
    val reactions: List<TerminalReaction> = emptyList(),
    /** What a Victory earns on the Rebirth ladder (null for a defeat or a replayed cleared tier). */
    val rebirthEarned: ReportRebirthLines? = null,
    /** A Baby mode run: nothing is counted (no bank line, no Rebirth lines, a "Not counted" stamp). */
    val easy: Boolean = false,
)

/** A leading archetype of the run: the tag, its name and the resonance tier it reached. */
internal data class TerminalTag(val tag: BuildTag, val label: String, val tier: Int)

/** The run's dominant element (status element ordinal) and its name. */
internal data class TerminalElement(val element: Int, val label: String)

/** A reaction set off in the run; [discovered] marks one met for the first time in it. */
internal data class TerminalReaction(val id: ReactionId, val name: String, val discovered: Boolean)

/** Archetypes the recap names. */
private const val RECAP_TAGS = 3

/**
 * The report of this frame. [reactionsThisRun] and [discoveredThisRun] are bit sets by reaction
 * ordinal (the interaction's visual record of the run's reactions).
 */
internal fun GameplayRenderModel.terminalPresentation(
    language: AppLanguage,
    reactionsThisRun: Int = 0,
    discoveredThisRun: Int = 0,
): TerminalPresentation {
    val stats = runStatistics
    val victory = phase == GamePhase.VICTORY
    fun stat(label: GameplayText, value: String, tone: TerminalTone = TerminalTone.BONE) = TerminalStatistic(label, value, tone)
    return TerminalPresentation(
        victory = victory,
        reason = message.localizedContent(language),
        time = formatRunTime(elapsed),
        kills = overlayGrouped(kills.toLong(), language),
        matter = overlayGrouped(runMatter, language),
        weapon = currentWeaponDefinition.name.localizedContent(language),
        combat = listOf(
            stat(GameplayText.EnemiesDestroyed, overlayGrouped(kills.toLong(), language)),
            stat(GameplayText.ElitesDestroyed, overlayGrouped(stats.eliteKills.toLong(), language), TerminalTone.THREAT),
            stat(GameplayText.DamageDealt, overlayCompact(stats.damageDealt.roundToLong(), language)),
            stat(GameplayText.DamageTaken, overlayGrouped(stats.damageTaken.roundToLong(), language), if (victory) TerminalTone.BONE else TerminalTone.THREAT),
            stat(GameplayText.DamageAbsorbed, overlayGrouped(stats.damageAbsorbed.roundToLong(), language)),
            stat(GameplayText.BestCombo, "×" + stats.bestCombo, TerminalTone.YOU),
            stat(GameplayText.RunDuration, formatRunTime(elapsed)),
        ),
        collection = listOf(
            stat(GameplayText.MatterEarned, overlayGrouped(runMatter, language), TerminalTone.YOU),
            stat(GameplayText.DataCollected, overlayGrouped(stats.dataCollected, language)),
            stat(GameplayText.PickupsCollected, overlayGrouped(stats.pickupsCollected, language)),
            stat(GameplayText.KeysCollected, overlayGrouped(stats.keysCollected, language)),
            stat(GameplayText.ArtifactsAcquired, overlayGrouped(acquiredItemCount.toLong(), language)),
            stat(GameplayText.LevelReached, overlayLevel(level, language)),
        ),
        rebirth = language.text(OverlayRedesignText.RebirthTag, rebirthLevel),
        rebirthLevel = rebirthLevel,
        form = content.coreShape(coreShape).displayName.localizedContent(language),
        formIcon = coreShape.overlayIcon(),
        bank = if (easyMode) null else overlayGrouped(totalMatter, language),
        weaponIcon = weapon.overlayIcon(),
        weaponLevel = overlayLevel(weaponLevel, language),
        weaponMaxLevel = nextWeaponMastery == null,
        relics = List(content.relicPolicy.maxSlots) { index ->
            equippedRelics.getOrNull(index)?.let { OverlayRelicSlot(it.id, content.relic(it.id).aspect, it.rank) }
        },
        relicDefinitions = List(content.relicPolicy.maxSlots) { index -> equippedRelics.getOrNull(index)?.let { content.relic(it.id) } },
        recapTags = BuildTag.entries.filter { build.tagPoints(it) > 0 }
            .sortedWith(compareByDescending<BuildTag> { build.tagPoints(it) }.thenBy { it.ordinal })
            .take(RECAP_TAGS)
            .map { TerminalTag(it, it.displayLabel.localizedContent(language), build.tagTier(it)) },
        dominant = build.dominant?.let { TerminalElement(it.ordinal, it.displayLabel.localizedContent(language)) },
        reactions = ReactionId.entries.filter { reactionsThisRun and (1 shl it.ordinal) != 0 }.map {
            TerminalReaction(it, content.build.reaction(it).name.localizedContent(language), discoveredThisRun and (1 shl it.ordinal) != 0)
        },
        rebirthEarned = if (easyMode) null else reportRebirthLines(language),
        easy = easyMode,
    )
}
