// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.nucleus.model.formatRunTime
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Cyan
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.Muted
import kinetickk.foundation.design.SpaceBlack
import kinetickk.foundation.design.White
import kotlin.math.roundToLong

internal fun terminalRevealProgress(elapsed: Float, delay: Float, duration: Float = 0.38f): Float {
    val fraction = ((elapsed - delay) / duration).coerceIn(0f, 1f)
    return 1f - (1f - fraction) * (1f - fraction) * (1f - fraction)
}

internal fun terminalRevealDelay(victory: Boolean): Float = if (victory) 0.18f else 0.68f
internal fun terminalActionsReady(elapsed: Float, victory: Boolean): Boolean =
    elapsed >= terminalRevealDelay(victory) + 0.32f

internal data class TerminalStatistic(val label: GameplayText, val value: String)

internal data class TerminalPresentation(
    val victory: Boolean,
    val reason: String,
    val time: String,
    val kills: String,
    val matter: String,
    val weapon: String,
    val combat: List<TerminalStatistic>,
    val collection: List<TerminalStatistic>,
)

internal fun GameplayRenderModel.terminalPresentation(language: AppLanguage): TerminalPresentation {
    val stats = runStatistics
    fun stat(label: GameplayText, value: Number) = TerminalStatistic(label, value.toString())
    return TerminalPresentation(
        victory = phase == GamePhase.VICTORY,
        reason = message.localizedContent(language),
        time = formatRunTime(elapsed),
        kills = kills.toString(),
        matter = runMatter.toString(),
        weapon = language.text(GameplayText.WeaponLevel, currentWeaponDefinition.name.localizedContent(language), weaponLevel),
        combat = listOf(
            stat(GameplayText.EnemiesDestroyed, kills),
            stat(GameplayText.ElitesDestroyed, stats.eliteKills),
            stat(GameplayText.DamageDealt, stats.damageDealt.roundToLong()),
            stat(GameplayText.DamageTaken, stats.damageTaken.roundToLong()),
            stat(GameplayText.DamageAbsorbed, stats.damageAbsorbed.roundToLong()),
            stat(GameplayText.BestCombo, stats.bestCombo),
        ),
        collection = listOf(
            stat(GameplayText.MatterEarned, runMatter),
            stat(GameplayText.DataCollected, stats.dataCollected),
            stat(GameplayText.PickupsCollected, stats.pickupsCollected),
            stat(GameplayText.KeysCollected, stats.keysCollected),
            stat(GameplayText.ArtifactsAcquired, acquiredItemCount),
            stat(GameplayText.LevelReached, level),
        ),
    )
}

@Composable
internal fun TerminalContent(
    engine: GameplayRenderModel,
    elapsed: Float,
    enabled: Boolean,
    onInput: (GameplayInput) -> Unit,
) {
    val language = LocalAppLanguage.current
    val presentation = remember(engine, language) { engine.terminalPresentation(language) }
    TerminalContent(presentation, engine.settings.textScale, engine.settings.runStatisticsOnLeft, elapsed, enabled, onInput)
}

@Composable
internal fun TerminalContent(
    presentation: TerminalPresentation,
    textScale: Float,
    statisticsOnLeft: Boolean,
    elapsed: Float,
    enabled: Boolean,
    onInput: (GameplayInput) -> Unit,
) {
    val delay = terminalRevealDelay(presentation.victory)
    val reveal = terminalRevealProgress(elapsed, delay)
    if (elapsed < delay) return
    val actionsEnabled = enabled && terminalActionsReady(elapsed, presentation.victory)
    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("kinetickk.gameplay.results")
            .background(Color(0xFF090C10).copy(alpha = 0.96f * reveal)),
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth >= (820f * (textScale / 1.25f).coerceAtLeast(1f)).dp
        val padding = if (maxWidth < 500.dp) 20.dp else 40.dp
        if (wide) {
            Row(
                Modifier.widthIn(max = 1280.dp).fillMaxSize().padding(padding),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val summary: @Composable RowScope.() -> Unit = {
                    SummaryPanel(presentation, textScale, actionsEnabled, onInput,
                        Modifier.weight(0.95f).fillMaxHeight().verticalScroll(rememberScrollState()).reveal(reveal))
                }
                val statistics: @Composable RowScope.() -> Unit = {
                    StatisticsPanel(presentation, textScale, elapsed - delay,
                        Modifier.weight(1.05f).fillMaxHeight().verticalScroll(rememberScrollState()))
                }
                if (statisticsOnLeft) statistics() else summary()
                Box(Modifier.width(1.dp).fillMaxHeight().background(White.copy(alpha = 0.08f * reveal)))
                if (statisticsOnLeft) summary() else statistics()
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                // Keep actions first on narrow screens so a long report never buries navigation.
                SummaryPanel(presentation, textScale, actionsEnabled, onInput, Modifier.fillMaxWidth().reveal(reveal))
                StatisticsPanel(presentation, textScale, elapsed - delay, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SummaryPanel(
    presentation: TerminalPresentation,
    scale: Float,
    enabled: Boolean,
    onInput: (GameplayInput) -> Unit,
    modifier: Modifier,
) {
    val language = LocalAppLanguage.current
    Column(modifier.testTag("kinetickk.gameplay.results.summary"), verticalArrangement = Arrangement.Center) {
        Box(Modifier.padding(top = 12.dp, bottom = 24.dp).width(42.dp).height(3.dp).background(Cyan))
        Label(language.text(GameplayText.RunRecord), 10f * scale, Cyan, weight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        Label(language.text(if (presentation.victory) GameplayText.RunConquered else GameplayText.SingularityRemembers),
            34f * scale, White, Modifier.semantics { heading() }, FontWeight.Medium)
        Spacer(Modifier.height(14.dp))
        Label(presentation.reason, 10f * scale, Muted)
        Spacer(Modifier.height(36.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < (310f * scale).dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompactSummaryMetric(GameplayText.RunDuration, presentation.time, scale, White)
                    CompactSummaryMetric(GameplayText.EnemiesDestroyed, presentation.kills, scale, White)
                    CompactSummaryMetric(GameplayText.MatterEarned, presentation.matter, scale, Cyan)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryMetric(GameplayText.RunDuration, presentation.time, scale, White, Modifier.weight(1f))
                    SummaryMetric(GameplayText.EnemiesDestroyed, presentation.kills, scale, White, Modifier.weight(1f))
                    SummaryMetric(GameplayText.MatterEarned, presentation.matter, scale, Cyan, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Label(presentation.weapon, 11f * scale, Muted)
        Spacer(Modifier.height(40.dp))
        TerminalButton(language.text(GameplayText.Reenter), "restart", scale, true, enabled) { onInput(GameplayInput.RestartRun) }
        if (presentation.victory) {
            Spacer(Modifier.height(10.dp))
            TerminalButton(language.text(GameplayText.RebirthNext), "rebirth", scale, false, enabled) { onInput(GameplayInput.OpenRebirth) }
        }
        Spacer(Modifier.height(10.dp))
        TerminalButton(language.text(GameplayText.ReturnHome), "exit", scale, false, enabled) { onInput(GameplayInput.ExitToHome) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CompactSummaryMetric(label: GameplayText, value: String, scale: Float, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(LocalAppLanguage.current.text(label), 10f * scale, Muted, Modifier.weight(1f))
        Label(value, 20f * scale, color, weight = FontWeight.Medium)
    }
}

@Composable
private fun SummaryMetric(label: GameplayText, value: String, scale: Float, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(value, 23f * scale, color, weight = FontWeight.Medium)
        Label(LocalAppLanguage.current.text(label), 9f * scale, Muted)
    }
}

@Composable
private fun StatisticsPanel(presentation: TerminalPresentation, scale: Float, elapsed: Float, modifier: Modifier) {
    val language = LocalAppLanguage.current
    Column(modifier.testTag("kinetickk.gameplay.results.statistics"), verticalArrangement = Arrangement.Center) {
        Spacer(Modifier.height(12.dp))
        Label(language.text(GameplayText.DetailedStatistics), 20f * scale, White,
            Modifier.reveal(terminalRevealProgress(elapsed, 0.08f)).semantics { heading() }, FontWeight.Medium)
        Spacer(Modifier.height(24.dp))
        StatisticSection(GameplayText.CombatStatistics, presentation.combat, scale, elapsed, 0)
        Spacer(Modifier.height(22.dp))
        StatisticSection(GameplayText.LootStatistics, presentation.collection, scale, elapsed, presentation.combat.size)
        Spacer(Modifier.height(18.dp))
        Label(language.text(GameplayText.DamageAccountingHint), 9f * scale, Muted,
            Modifier.reveal(terminalRevealProgress(elapsed, 1.0f)))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatisticSection(title: GameplayText, rows: List<TerminalStatistic>, scale: Float, elapsed: Float, firstIndex: Int) {
    Label(LocalAppLanguage.current.text(title), 9f * scale, Cyan,
        Modifier.reveal(terminalRevealProgress(elapsed, 0.12f + firstIndex * 0.065f)), FontWeight.Medium)
    Spacer(Modifier.height(10.dp))
    rows.forEachIndexed { index, statistic ->
        val progress = terminalRevealProgress(elapsed, 0.18f + (firstIndex + index) * 0.065f)
        // Reserve layout height while keeping unrevealed text out of accessibility traversal.
        Column(Modifier.fillMaxWidth().reveal(progress)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).testTag("kinetickk.gameplay.stat.${statistic.label.name}"),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Label(LocalAppLanguage.current.text(statistic.label), 10f * scale, Muted, Modifier.weight(1f))
                Label(statistic.value, 12f * scale, White, weight = FontWeight.Medium)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(White.copy(alpha = 0.07f)))
        }
    }
}

@Composable
private fun TerminalButton(label: String, tag: String, scale: Float, prominent: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val focusedValue by interactions.collectIsFocusedAsState()
    val hoveredValue by interactions.collectIsHoveredAsState()
    val active = focusedValue || hoveredValue
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .background(if (prominent) Cyan else White.copy(alpha = if (active) 0.10f else 0.045f))
            .border(1.dp, if (active) White else Color.Transparent)
            .testTag("kinetickk.gameplay.$tag")
            .hoverable(interactions, enabled)
            .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(label, 12f * scale, if (prominent) SpaceBlack else White, weight = FontWeight.Medium)
    }
}

private fun Modifier.reveal(progress: Float): Modifier = (if (progress == 0f) clearAndSetSemantics { } else this).graphicsLayer {
    alpha = progress
    translationY = -14.dp.toPx() * (1f - progress)
}

@Composable
private fun Label(text: String, size: Float, color: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal) {
    BasicText(text, modifier, TextStyle(color = color, fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.3f).sp))
}
