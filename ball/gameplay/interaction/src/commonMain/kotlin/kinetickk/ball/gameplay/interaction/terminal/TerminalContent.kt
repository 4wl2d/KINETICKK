// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.OverlayRelicSlot
import kinetickk.ball.gameplay.interaction.canvas.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.overlayCompact
import kinetickk.ball.gameplay.interaction.canvas.overlayGrouped
import kinetickk.ball.gameplay.interaction.canvas.overlayIcon
import kinetickk.ball.gameplay.interaction.canvas.overlayLevel
import kinetickk.ball.gameplay.interaction.canvas.overlayRowWidths
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.interaction.rewards.OverlayButton
import kinetickk.ball.gameplay.interaction.rewards.OverlayFrame
import kinetickk.ball.gameplay.interaction.rewards.OverlayStamp
import kinetickk.ball.gameplay.interaction.rewards.OverlayTag
import kinetickk.ball.gameplay.interaction.rewards.OverlayFitText
import kinetickk.ball.gameplay.interaction.rewards.OverlayText
import kinetickk.ball.gameplay.interaction.rewards.RewardScrollIndicator
import kinetickk.ball.gameplay.interaction.rewards.overlayUiTextScale
import kinetickk.ball.gameplay.interaction.rewards.overlayFrame
import kinetickk.ball.gameplay.nucleus.model.formatRunTime
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkButtonSize
import kinetickk.foundation.design.KkButtonVariant
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkInfoButton
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkShape
import kinetickk.foundation.design.KkSlam
import kinetickk.foundation.design.KkStampVariant
import kinetickk.foundation.design.KkTagVariant
import kinetickk.foundation.design.KkTooltipPlacement
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkGem
import kinetickk.foundation.design.drawKkGrid
import kinetickk.foundation.design.drawKkHalftone
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkRadialFade
import kinetickk.foundation.design.drawKkRelicSlot
import kinetickk.foundation.design.drawKkRingBurst
import kinetickk.foundation.design.drawKkTag
import kinetickk.foundation.design.kkLerp
import kinetickk.foundation.design.kkSlab
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.kkTagSize
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kinetickk.foundation.design.wideStyle
import kotlin.math.max
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
    val form: String? = null,
    val formIcon: KkIcon? = null,
    val bank: String? = null,
    val weaponIcon: KkIcon? = null,
    val weaponLevel: String? = null,
    val weaponMaxLevel: Boolean = false,
    val relics: List<OverlayRelicSlot?> = emptyList(),
)

internal fun GameplayRenderModel.terminalPresentation(language: AppLanguage): TerminalPresentation {
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
        form = content.coreShape(coreShape).displayName.localizedContent(language),
        formIcon = coreShape.overlayIcon(),
        bank = overlayGrouped(totalMatter, language),
        weaponIcon = weapon.overlayIcon(),
        weaponLevel = overlayLevel(weaponLevel, language),
        weaponMaxLevel = nextWeaponMastery == null,
        relics = List(content.relicPolicy.maxSlots) { index ->
            equippedRelics.getOrNull(index)?.let { OverlayRelicSlot(it.id, content.relic(it.id).aspect, it.rank) }
        },
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

/**
 * Run report (Report / Report-Defeat boards): two-line title, cause stamp, matter banked, bank
 * total, build row, the shatter illustration, Combat and Collection statistics on the skewed
 * panel (mirrored when statistics sit on the left) and the game's next actions. [textScale] is
 * the text-size setting: UI text renders at the board size at the default ([overlayUiTextScale]);
 * the title and the matter figure are display type and ignore it.
 */
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
    if (elapsed < delay) return
    val reveal = terminalRevealProgress(elapsed, delay)
    val actionsEnabled = enabled && terminalActionsReady(elapsed, presentation.victory)
    val roles = LocalKkRolePalette.current
    val accent = if (presentation.victory) roles.you else roles.threat
    val shatter = remember(presentation.victory) { ReportShatterArt(presentation.victory) }
    val t = elapsed - delay
    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("kinetickk.gameplay.results")
            .drawBehind { drawRect(Kk.Ink, alpha = reveal) },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val scene = ReportScene(presentation, overlayUiTextScale(textScale), t, reveal, accent, roles, actionsEnabled, onInput, shatter)
        when {
            width >= 820f && height >= 480f -> RegularReport(scene, statisticsOnLeft, width, height)
            width > height -> CompactReport(scene, statisticsOnLeft)
            else -> PortraitReport(scene)
        }
    }
}

/** What every report layout draws from: data, clock, accent and the host callback. */
private class ReportScene(
    val presentation: TerminalPresentation,
    /** UI text factor ([overlayUiTextScale] of the setting): 1 renders board sizes. */
    val textScale: Float,
    val t: Float,
    val reveal: Float,
    val accent: Color,
    val roles: KkRolePalette,
    val actionsEnabled: Boolean,
    val onInput: (GameplayInput) -> Unit,
    val shatter: ReportShatterArt,
)

/** Seeded shatter shape and its paths, built once per outcome. */
internal class ReportShatterArt(victory: Boolean) {
    val victory: Boolean = victory
    val shape: ReportShatterShape = reportShatterShape(victory)
    val paths: List<Path> by lazy { shape.shards.map { it.path() } }
}

@Composable
private fun RegularReport(scene: ReportScene, mirrored: Boolean, widthDp: Float, heightDp: Float) {
    val frame = overlayFrame(widthDp, heightDp, 1440f, 810f, scene.textScale, 0.5f, 1.3f)
    fun bx(x: Float, w: Float) = if (mirrored) 1440f - x - w else x
    val summaryLeft = if (mirrored) 540f else 56f
    val shatterX = if (mirrored) 1216f else 750f
    val density = LocalDensity.current.density
    ReportBackdrop(scene, frame, mirrored, Offset(290f, 330f), 450f, 1440f,
        frame.x(if (mirrored) 580f else 860f).value * density + (if (mirrored) -1f else 1f) * KkShape.ShearRatio * (frame.y(810f).value * density - heightDp * density))
    ReportShatterCanvas(scene, frame, Offset(shatterX, 472f), 0.8f)
    ReportScrollColumn(
        Modifier.offset(frame.x(summaryLeft), frame.y(44f)).width(frame.dp(780f)).heightIn(max = frame.dp(746f)),
        "kinetickk.gameplay.results.summary", Kk.Ink,
    ) {
        ReportSummary(scene, frame, titleSize = 94f, matterSize = 76f, gemSize = 34f, stampSize = 20f, slotSize = 50f, relicSize = 36f)
    }
    ReportScrollColumn(
        Modifier.offset(frame.x(bx(930f, 462f)), frame.y(44f)).width(frame.dp(462f)).heightIn(max = frame.dp(626f)),
        "kinetickk.gameplay.results.statistics", Kk.Ink1,
    ) {
        ReportStatistics(scene, frame, rowHeight = 30f, labelSize = 15f, valueSize = 22f)
    }
    ReportActions(
        scene, frame,
        Modifier.offset(frame.x(bx(930f, 462f)), frame.y(690f)).width(frame.dp(462f)),
        primaryHeight = 72f, primaryFont = 30f, ghostFont = 24f,
    )
    ReportMenu(scene, frame, Modifier.offset(frame.x(bx(930f, 120f)), frame.y(770f)))
}

@Composable
private fun CompactReport(scene: ReportScene, mirrored: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frame = overlayFrame(maxWidth.value, maxHeight.value, 844f, 390f, scene.textScale, 0.7f, 1.4f)
        val split = maxWidth * 0.54f
        val statsWidth = maxWidth - split
        val density = LocalDensity.current.density
        val edge = (if (mirrored) (maxWidth - split).value + 12f else split.value + 22f) * density
        ReportBackdrop(scene, frame, mirrored, Offset(160f, 140f), 260f, 844f, edge)
        val summaryOffset = if (mirrored) statsWidth else 0.dp
        ReportShatterCanvas(scene, frame, Offset(if (mirrored) 740f else 360f, 250f), 0.34f)
        // The summary (title, matter, bank, build) keeps the whole left column; the next actions
        // are pinned under the statistics, as on the desktop board, so they never cover it.
        ReportScrollColumn(
            Modifier.offset(x = summaryOffset).width(split).fillMaxHeight(),
            "kinetickk.gameplay.results.summary", Kk.Ink,
            PaddingValues(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        ) {
            ReportSummary(scene, frame, titleSize = 38f, matterSize = 34f, gemSize = 20f, stampSize = 15f, slotSize = 50f, relicSize = 28f,
                compact = true, gapScale = 0.6f)
        }
        Column(
            Modifier.offset(x = if (mirrored) 0.dp else split).width(statsWidth).fillMaxHeight()
                .padding(start = if (mirrored) 16.dp else 40.dp, end = if (mirrored) 40.dp else 16.dp, top = 14.dp, bottom = 10.dp),
        ) {
            // Rows below the pinned actions are announced by a scroll bar and a fade (13 rows
            // outgrow a phone's height).
            ReportScrollColumn(
                Modifier.weight(1f).fillMaxWidth(),
                "kinetickk.gameplay.results.statistics", Kk.Ink1,
            ) {
                ReportStatistics(scene, frame, rowHeight = 26f, labelSize = 13f, valueSize = 17f)
            }
            Spacer(Modifier.height(10.dp))
            // Menu shares the row, so the row sizes all three buttons together.
            ReportActions(scene, frame, Modifier.fillMaxWidth(), primaryHeight = 48f, primaryFont = 22f, ghostFont = 18f, withMenu = true)
        }
    }
}

@Composable
private fun PortraitReport(scene: ReportScene) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frame = overlayFrame(maxWidth.value, maxHeight.value, 390f, 844f, scene.textScale, 0.7f, 1.4f)
        ReportBackdrop(scene, frame, false, Offset(120f, 200f), 260f, 390f, null)
        ReportScrollColumn(
            Modifier.fillMaxSize(), "kinetickk.gameplay.results.summary", Kk.Ink,
            PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        ) {
            ReportSummary(scene, frame, titleSize = 44f, matterSize = 40f, gemSize = 22f, stampSize = 15f, slotSize = 50f, relicSize = 30f,
                compact = true, inlineShatter = true)
            Spacer(Modifier.height(20.dp))
            ReportActions(scene, frame, Modifier.fillMaxWidth(), primaryHeight = 56f, primaryFont = 24f, ghostFont = 20f)
            Spacer(Modifier.height(10.dp))
            ReportMenu(scene, frame, Modifier, touch = true)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().testTag("kinetickk.gameplay.results.statistics")) {
                ReportStatistics(scene, frame, rowHeight = 28f, labelSize = 14f, valueSize = 18f)
            }
        }
    }
}

/**
 * A vertically scrolling report column ([tag] names its viewport). Whenever its content runs past
 * the viewport, a scroll bar at the end edge and a fade into [background] at the cut edge show
 * that more follows.
 */
@Composable
private fun ReportScrollColumn(
    modifier: Modifier,
    tag: String,
    background: Color,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().testTag(tag).verticalScroll(scroll).padding(padding).padding(end = ReportScrollGutter),
            content = content,
        )
        if (scroll.maxValue > 0) {
            val language = LocalAppLanguage.current
            if (scroll.canScrollBackward) ReportFade(background, top = true, Modifier.align(Alignment.TopCenter))
            if (scroll.canScrollForward) ReportFade(background, top = false, Modifier.align(Alignment.BottomCenter).testTag("$tag.fade"))
            RewardScrollIndicator(
                scroll, Kk.Bone,
                Modifier.matchParentSize().wrapContentWidth(Alignment.End).width(3.dp).testTag("$tag.scroll"),
                language.text(OverlayRedesignText.ScrollableReport),
            )
        }
    }
}

/** Room at the end of a scrolling report column for its scroll bar. */
private val ReportScrollGutter = 10.dp

@Composable
private fun ReportFade(background: Color, top: Boolean, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().height(if (top) 18.dp else 36.dp).drawWithCache {
            val clear = background.copy(alpha = 0f)
            val brush = if (top) Brush.verticalGradient(0f to background, 1f to clear) else Brush.verticalGradient(0f to clear, 1f to background)
            onDrawBehind { drawRect(brush) }
        },
    )
}

/**
 * Ink ground: 48 px grid, a halftone halo in the outcome color and the skewed stats panel whose
 * edge meets the bottom of the screen at [panelEdgePx] (null = no panel), leaning −12°.
 */
@Composable
private fun ReportBackdrop(
    scene: ReportScene,
    frame: OverlayFrame,
    mirrored: Boolean,
    haloCenter: Offset,
    haloRadius: Float,
    boardWidth: Float,
    panelEdgePx: Float?,
) {
    Canvas(Modifier.fillMaxSize()) {
        val alpha = scene.reveal
        drawKkGrid(Rect(Offset.Zero, size), Kk.Bone.copy(alpha = 0.045f * alpha), spacingDp = 48f * frame.scale)
        val cx = frame.x(if (mirrored) boardWidth - haloCenter.x else haloCenter.x).toPx()
        val cy = frame.y(haloCenter.y).toPx()
        val r = frame.dp(haloRadius).toPx()
        val halo = Rect(cx - r, cy - r, cx + r, cy + r)
        drawKkRadialFade(halo) { drawKkHalftone(halo, scene.accent.copy(alpha = 0.14f * alpha)) }
        if (panelEdgePx != null) {
            val lean = KkShape.ShearRatio * size.height * (if (mirrored) -1f else 1f)
            val path = ReportPanelPath.of(mirrored, panelEdgePx + lean, panelEdgePx, size.width, size.height)
            drawPath(path, Kk.Ink1, alpha)
            drawLine(Kk.Line, Offset(panelEdgePx + lean, 0f), Offset(panelEdgePx, size.height), density, alpha = alpha)
        }
    }
}

private object ReportPanelPath {
    private val path = Path()
    private val key = FloatArray(5) { Float.NaN }

    fun of(mirrored: Boolean, top: Float, bottom: Float, width: Float, height: Float): Path {
        val m = if (mirrored) 1f else 0f
        if (key[0] != m || key[1] != top || key[2] != bottom || key[3] != width || key[4] != height) {
            key[0] = m; key[1] = top; key[2] = bottom; key[3] = width; key[4] = height
            path.reset()
            val outer = if (mirrored) 0f else width
            path.moveTo(top, 0f)
            path.lineTo(outer, 0f)
            path.lineTo(outer, height)
            path.lineTo(bottom, height)
            path.close()
        }
        return path
    }
}

/** The shatter at board point [center] scaled by [boardScale] (0.8 on the Report board). */
@Composable
private fun ReportShatterCanvas(scene: ReportScene, frame: OverlayFrame, center: Offset, boardScale: Float) {
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val c = Offset(frame.x(center.x).toPx(), frame.y(center.y).toPx())
        drawReportShatter(scene, c, boardScale * frame.scale * density)
    }
}

/** Shatter drawn around [center] with board units scaled by [scale] px. */
private fun DrawScope.drawReportShatter(scene: ReportScene, center: Offset, scale: Float) {
    val art = scene.shatter
    val t = scene.t
    val accent = scene.accent
    val alpha = scene.reveal
    val halo = Rect(center.x - 250f * scale, center.y - 250f * scale, center.x + 250f * scale, center.y + 250f * scale)
    drawKkRadialFade(halo) { drawKkHalftone(halo, accent.copy(alpha = (if (art.victory) 0.14f else 0.13f) * alpha)) }
    drawKkRingBurst(center, 120f * scale, t - 0.3f, accent, 3f)
    drawShatterShards(art.shape, art.paths, center, scale, t, accent, alpha = alpha)
    drawShatterDebris(art.shape, center, scale, t, accent, alpha = alpha)
    val pop = ((t - (if (art.victory) 0.9f else 0.6f)) / 0.5f).coerceIn(0f, 1f)
    if (pop <= 0f) return
    val s = if (pop < 0.6f) kkLerp(0f, 1.15f, KkEase.Pull.transform(pop / 0.6f)) else kkLerp(1.15f, 1f, (pop - 0.6f) / 0.4f)
    val a = (pop / 0.6f).coerceIn(0f, 1f) * alpha
    if (art.victory) {
        // Surviving bone Core: ink gap ring, you ring and a soft glow (stacked discs, no blur).
        val r = 26f * scale * s
        for (index in CoreGlow.indices) drawCircle(accent.copy(alpha = CoreGlowAlpha[index] * a), r + 9f * scale * s + CoreGlow[index] * scale, center)
        drawCircle(accent.copy(alpha = a), r + 9f * scale * s, center)
        drawCircle(Kk.Ink.copy(alpha = a), r + 6f * scale * s, center)
        drawCircle(Kk.Bone.copy(alpha = a), r, center)
    } else {
        // Black singularity ringed in threat.
        val r = 40f * scale * s
        for (index in CoreGlow.indices) drawCircle(accent.copy(alpha = CoreGlowAlpha[index] * a * 1.2f), r + CoreGlow[index] * scale, center)
        drawCircle(Color.Black.copy(alpha = a), r, center)
        drawCircle(accent.copy(alpha = a), r + 1.5f * scale, center, style = kkStroke(3f * scale))
    }
}

private val CoreGlow = floatArrayOf(6f, 14f, 24f, 36f)
private val CoreGlowAlpha = floatArrayOf(0.2f, 0.11f, 0.06f, 0.03f)

/** Header tags, the slammed two-line title, cause stamp, matter banked, bank and build rows. */
@Composable
private fun ColumnScope.ReportSummary(
    scene: ReportScene,
    frame: OverlayFrame,
    titleSize: Float,
    matterSize: Float,
    gemSize: Float,
    stampSize: Float,
    slotSize: Float,
    relicSize: Float,
    compact: Boolean = false,
    inlineShatter: Boolean = false,
    gapScale: Float = 1f,
) {
    val presentation = scene.presentation
    val language = LocalAppLanguage.current
    val typography = rememberInterfaceTypography()
    val roles = scene.roles
    val text = scene.textScale
    val k = if (compact) 1f else frame.scale
    fun sp(value: Float, min: Float = 11f) = max(value * k, min) * text
    // The title and the matter figure are display type: the text-size setting does not apply.
    fun display(value: Float, min: Float) = max(value * k, min)
    fun gap(value: Float): Dp = (value * k * gapScale).dp
    fun box(value: Float): Dp = (value * k).dp
    // The header, title and stamp sit above the inline shatter, whose halo and debris reach past
    // its own box (portrait).
    Row(
        Modifier.zIndex(1f).enter(scene, 0f, left = true),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayText(language.text(GameplayText.RunRecord), typography.labelStyle(sp(15f), color = Kk.Mute), uppercase = true)
        presentation.rebirth?.let { OverlayTag(it, variant = KkTagVariant.LINE, textScale = text) }
        presentation.form?.let { ReportFormTag(it, presentation.formIcon, text) }
    }
    Spacer(Modifier.height(gap(18f)))
    val titleTop = language.text(if (presentation.victory) OverlayRedesignText.VictoryTitleTop else OverlayRedesignText.DefeatTitleTop)
    val titleBottom = language.text(if (presentation.victory) OverlayRedesignText.VictoryTitleBottom else OverlayRedesignText.DefeatTitleBottom)
    Column(
        Modifier.zIndex(1f).fillMaxWidth().semantics(mergeDescendants = true) { heading() }.graphicsLayer {
            val p = ((scene.t - 0.1f) / 0.5f).coerceIn(0f, 1f)
            val s = KkSlam.scale(p)
            scaleX = s
            scaleY = s
            rotationZ = KkSlam.rotation(p, -2f)
            alpha = KkSlam.alpha(p)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
        },
    ) {
        FitText(titleTop, typography.wideStyle(display(titleSize, 24f), lineHeightEm = 0.92f, color = scene.accent))
        FitText(titleBottom, typography.wideStyle(display(titleSize, 24f), lineHeightEm = 0.92f, color = Kk.Bone))
    }
    Spacer(Modifier.height(gap(22f)))
    val stamp = if (presentation.victory) language.text(GameplayText.ArchitectFallen) else presentation.reason
    Box(Modifier.zIndex(1f).enter(scene, 0.3f)) {
        OverlayStamp(stamp, Modifier.testTag("kinetickk.gameplay.results.cause"),
            if (presentation.victory) KkStampVariant.YOU else KkStampVariant.THREAT, fontSize = stampSize * k, textScale = text)
    }
    if (inlineShatter) {
        Canvas(Modifier.fillMaxWidth().height(230.dp).clearAndSetSemantics { }) {
            drawReportShatter(scene, center, size.height / 420f * 0.95f)
        }
    }
    Spacer(Modifier.height(gap(if (compact) 18f else 44f)))
    Column(Modifier.enter(scene, 0.4f)) {
        OverlayText(language.text(OverlayRedesignText.MatterBanked), typography.labelStyle(sp(15f), color = Kk.Mute), uppercase = true)
        Row(Modifier.padding(top = gap(8f)), horizontalArrangement = Arrangement.spacedBy(gap(14f)), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(box(gemSize))) { drawKkGem(center, roles.you, size.minDimension / density) }
            OverlayText("+" + presentation.matter, typography.wideStyle(display(matterSize, 22f), tabular = true, lineHeightEm = 0.9f, color = roles.you),
                Modifier.testTag("kinetickk.gameplay.results.matter"))
        }
    }
    presentation.bank?.let { bank ->
        Spacer(Modifier.height(gap(12f)))
        Row(Modifier.enter(scene, 0.45f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OverlayText(language.text(OverlayRedesignText.Bank), typography.labelStyle(sp(15f), color = Kk.Mute), uppercase = true)
            OverlayText(bank, typography.monoStyle(sp(11f), color = Kk.Bone), uppercase = true)
            KkInfoButton(language.text(OverlayRedesignText.BankInfo), Modifier.testTag("kinetickk.gameplay.results.bank-info"),
                KkTooltipPlacement.ABOVE_START, text)
        }
    }
    val icon = presentation.weaponIcon
    if (icon != null) {
        Spacer(Modifier.height(gap(if (compact) 18f else 34f)))
        Row(Modifier.enter(scene, 0.5f).testTag("kinetickk.gameplay.results.build"), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OverlayText(language.text(OverlayRedesignText.Build), typography.labelStyle(sp(15f), color = Kk.Mute),
                Modifier.padding(end = 8.dp), uppercase = true)
            val measurer = rememberKkCanvasMeasurer(1f)
            Box(
                Modifier.size(box(slotSize)).testTag("kinetickk.gameplay.results.weapon")
                    .semantics { this.text = AnnotatedString(presentation.weapon + " " + presentation.weaponLevel.orEmpty()) }
                    .drawWithCache {
                        // The level label keeps inside the sheared face and the icon clears it at every slot size.
                        val bounds = Rect(Offset.Zero, size)
                        val placement = weaponSlotPlacement(measurer, bounds, icon, presentation.weaponLevel)
                        onDrawBehind {
                            drawPlacedWeaponSlot(measurer, bounds, icon, presentation.weaponLevel, presentation.weaponMaxLevel, placement = placement)
                        }
                    },
            )
            if (presentation.relics.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Canvas(Modifier.size(box(relicSize * presentation.relics.size + 8f * (presentation.relics.size - 1)), box(relicSize))
                    .semantics { contentDescription = language.text(OverlayRedesignText.Relics) }) {
                    val slot = size.height
                    presentation.relics.forEachIndexed { index, relic ->
                        val center = Offset(slot * 0.5f + index * (slot + 8f * k * density), size.height * 0.5f)
                        if (relic != null) drawKkRelicSlot(center, relic.aspect.overlayColor(), relic.aspect.overlayIcon(), slot / density)
                        else drawKkRelicSlot(center, Color.Unspecified, sizeDp = slot / density)
                    }
                }
            }
        }
    }
}

/**
 * The Core form's line tag with the form icon before its name (Report board "■ Ram"; the pause
 * chip draws the same icon). The icon follows the label's size, 6 dp before the text as on the
 * board; the plate is the line tag's, widened by the icon.
 */
@Composable
private fun ReportFormTag(text: String, icon: KkIcon?, textScale: Float) {
    if (icon == null) {
        OverlayTag(text, variant = KkTagVariant.LINE, textScale = textScale)
        return
    }
    val measurer = rememberKkCanvasMeasurer(textScale)
    val density = LocalDensity.current.density
    val plate = remember(measurer, text, density) { kkTagSize(measurer, text, density) }
    val iconPx = FormTagFontSp * FormTagIconEm * textScale * density
    val lead = iconPx + FormTagIconGapDp * density
    Box(
        Modifier.size(((plate.width + lead) / density).dp, (plate.height / density).dp).testTag("kinetickk.gameplay.results.form")
            .semantics { this.text = AnnotatedString(text) }
            .drawWithCache {
                val inset = 0.75f * density
                val outline = Path().kkSlab(inset, inset, size.width - inset, size.height - inset, 6f * density)
                onDrawBehind {
                    drawPath(outline, Kk.Line2, style = kkStroke(1.5f * density))
                    drawKkIcon(icon, Offset(FormTagPaddingDp * density + iconPx * 0.5f, size.height * 0.5f), iconPx, Kk.Bone)
                    // The text plate after the icon keeps the tag's own padding; the outline above is the whole tag's.
                    drawKkTag(measurer, text, Offset(lead, 0f), KkTagVariant.LINE, background = Color.Transparent)
                }
            },
    )
}

/** Tag label size (`.tag`, 13 px), icon size relative to it, and the board's glyph gap. */
private const val FormTagFontSp = 13f
private const val FormTagIconEm = 0.84f
private const val FormTagIconGapDp = 6f
private const val FormTagPaddingDp = 10f

/** A single line that shrinks to fit its width (huge wide titles, long Russian words). */
@Composable
private fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxPx = constraints.maxWidth.toFloat()
        val shown = text.uppercase()
        val fitted = remember(shown, style, maxPx) {
            val width = measurer.measure(shown, style, softWrap = false, maxLines = 1).size.width.toFloat()
            if (width <= maxPx || width <= 0f) style else style.copy(fontSize = style.fontSize * (maxPx / width) * 0.98f)
        }
        BasicText(shown, style = fitted, maxLines = 1, softWrap = false)
    }
}

/** Combat (with the (!) on how damage is counted) and Collection, revealed top to bottom. */
@Composable
private fun ReportStatistics(scene: ReportScene, frame: OverlayFrame, rowHeight: Float, labelSize: Float, valueSize: Float) {
    val language = LocalAppLanguage.current
    val typography = rememberInterfaceTypography()
    val t = scene.t
    Row(
        Modifier.reveal(terminalRevealProgress(t, 0.08f, 0.38f)),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayText(language.text(GameplayText.CombatStatistics), typography.labelStyle(labelSize * scene.textScale, color = Kk.Bone),
            Modifier.semantics { heading() }, uppercase = true)
        KkInfoButton(language.text(GameplayText.DamageAccountingHint), Modifier.testTag("kinetickk.gameplay.results.damage-info"),
            KkTooltipPlacement.BELOW, scene.textScale)
    }
    Spacer(Modifier.height(6.dp))
    ReportRows(scene, scene.presentation.combat, 0, rowHeight, labelSize, valueSize, typography)
    Spacer(Modifier.height(18.dp))
    OverlayText(language.text(GameplayText.LootStatistics), typography.labelStyle(labelSize * scene.textScale, color = Kk.Bone),
        Modifier.reveal(terminalRevealProgress(t, 0.12f + scene.presentation.combat.size * 0.065f)).semantics { heading() }, uppercase = true)
    Spacer(Modifier.height(6.dp))
    ReportRows(scene, scene.presentation.collection, scene.presentation.combat.size, rowHeight, labelSize, valueSize, typography)
}

@Composable
private fun ReportRows(
    scene: ReportScene,
    rows: List<TerminalStatistic>,
    firstIndex: Int,
    rowHeight: Float,
    labelSize: Float,
    valueSize: Float,
    typography: InterfaceTypography,
) {
    val language = LocalAppLanguage.current
    rows.forEachIndexed { index, statistic ->
        val progress = terminalRevealProgress(scene.t, 0.18f + (firstIndex + index) * 0.065f)
        // Reserve layout height while keeping unrevealed text out of accessibility traversal.
        Column(Modifier.fillMaxWidth().reveal(progress)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = rowHeight.dp).testTag("kinetickk.gameplay.stat.${statistic.label.name}"),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayFitText(language.text(statistic.label), typography.bodyStyle(labelSize * scene.textScale, color = Kk.Bone), Modifier.weight(1f),
                    maxLines = 2)
                OverlayText(statistic.value, typography.condStyle(valueSize * scene.textScale, tabular = true, color = when (statistic.tone) {
                    TerminalTone.BONE -> Kk.Bone
                    TerminalTone.YOU -> scene.roles.you
                    TerminalTone.THREAT -> scene.roles.threat
                }))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).drawBehind { drawRect(Kk.Line) })
        }
    }
}

/** One button of the report's action row. */
private class ReportAction(
    val label: String,
    val input: GameplayInput,
    val tag: String,
    val variant: KkButtonVariant,
    val size: KkButtonSize,
    /** Board font size, before the text-size factor. */
    val font: Float,
    /** Share of the row's spare width; 0 keeps the natural width plus [slackDp]. */
    val weight: Float,
    val height: Dp,
    val enterDelay: Float,
    val slackDp: Float = 0f,
    val contentDescription: String? = null,
)

/**
 * Victory: Rebirth (primary) + Re-enter; defeat: Re-enter. The game has no Lab action here. On a
 * phone in landscape Menu shares the row ([withMenu]). The row is sized as a whole
 * ([overlayRowWidths]): every label keeps its board size while the row has room, and when it has
 * not, all labels shrink by one factor, so the primary label stays the largest and a larger text
 * size never leaves a label smaller than the default did.
 */
@Composable
private fun ReportActions(
    scene: ReportScene,
    frame: OverlayFrame,
    modifier: Modifier,
    primaryHeight: Float,
    primaryFont: Float,
    ghostFont: Float,
    withMenu: Boolean = false,
) {
    val language = LocalAppLanguage.current
    val height = (primaryHeight * (if (primaryHeight >= 64f) frame.scale else 1f)).coerceAtLeast(48f).dp
    val big = primaryHeight >= 64f
    val fontScale = if (big) frame.scale else 1f
    val primarySize = if (big) KkButtonSize.LG else KkButtonSize.MD
    val actions = remember(language, scene.presentation.victory, withMenu, height, primarySize, fontScale, primaryFont, ghostFont) {
        buildList {
            if (scene.presentation.victory) {
                add(ReportAction(language.text(GameplayText.RebirthNext), GameplayInput.OpenRebirth, "kinetickk.gameplay.rebirth",
                    KkButtonVariant.PRIMARY, primarySize, primaryFont * fontScale, 1f, height, 0.6f))
                add(ReportAction(language.text(GameplayText.Reenter), GameplayInput.RestartRun, "kinetickk.gameplay.restart",
                    KkButtonVariant.GHOST, KkButtonSize.MD, ghostFont * fontScale, 0.62f, height, 0.6f))
            } else {
                add(ReportAction(language.text(GameplayText.Reenter), GameplayInput.RestartRun, "kinetickk.gameplay.restart",
                    KkButtonVariant.PRIMARY, primarySize, primaryFont * fontScale, 1f, height, 0.6f))
            }
            if (withMenu) {
                add(ReportAction(language.text(OverlayRedesignText.Menu), GameplayInput.ExitToHome, "kinetickk.gameplay.exit",
                    KkButtonVariant.GHOST, KkButtonSize.XS, ReportMenuFont, 0f, 48.dp, 0.7f, ReportMenuSlackDp,
                    language.text(GameplayText.ReturnHome)))
            }
        }
    }
    val measurer = rememberKkCanvasMeasurer(scene.textScale)
    val reference = rememberKkCanvasMeasurer(1f)
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth.toFloat()
        val (widths, factor) = remember(actions, measurer, reference, density, available) {
            val gaps = (0 until actions.size - 1).sumOf { index -> reportActionGap(actions, index).toDouble() }.toFloat() * density
            // One px more than the padding guards against rounding when a shrunk label is measured again.
            val pads = FloatArray(actions.size) { index -> (actions[index].size.paddingDp + actions[index].slackDp) * density + 1f }
            val weights = FloatArray(actions.size) { index -> actions[index].weight }
            fun labels(with: CanvasTextMeasurer) = FloatArray(actions.size) { index -> reportActionLabelWidth(with, actions[index].label, actions[index].font) }
            val row = overlayRowWidths(labels(measurer), pads, weights, available - gaps)
            if (measurer.scale <= 1f || row.second >= 1f) return@remember row
            // Text widths are not exactly linear in the font size: above the default the labels
            // never end below the size the default text size gives them.
            val floor = overlayRowWidths(labels(reference), pads, weights, available - gaps).second / measurer.scale
            if (row.second >= floor) row else labels(measurer).let { grown -> FloatArray(actions.size) { grown[it] * floor + pads[it] } to floor }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions.forEachIndexed { index, action ->
                if (index > 0) Spacer(Modifier.width(reportActionGap(actions, index - 1).dp))
                OverlayButton(
                    action.label, { scene.onInput(action.input) },
                    Modifier.enter(scene, action.enterDelay).size((widths[index] / density).dp, action.height).testTag(action.tag),
                    action.variant, action.size, scene.actionsEnabled,
                    textScale = scene.textScale, fontSize = action.font * factor, contentDescription = action.contentDescription,
                )
            }
        }
    }
}

/** Gap after action [index]: 12 dp between actions, 10 dp before Menu. */
private fun reportActionGap(actions: List<ReportAction>, index: Int): Float =
    if (actions[index + 1].weight <= 0f) 10f else 12f

/** Width in px of an action label at board size [font] (the [OverlayButton] label style). */
private fun reportActionLabelWidth(measurer: CanvasTextMeasurer, label: String, font: Float): Float =
    measureKkText(measurer, label, measurer.typography.condStyle(font, trackingEm = 0.02f, lineHeightEm = 1f), uppercase = true).size.width.toFloat()

private const val ReportMenuFont = 14f
private const val ReportMenuSlackDp = 12f

@Composable
private fun ReportMenu(scene: ReportScene, frame: OverlayFrame, modifier: Modifier, touch: Boolean = false) {
    val language = LocalAppLanguage.current
    val measurer = rememberKkCanvasMeasurer(scene.textScale)
    val density = LocalDensity.current.density
    val label = language.text(OverlayRedesignText.Menu)
    val width = remember(measurer, label, density) {
        kinetickk.ball.gameplay.interaction.rewards.overlayButtonWidth(measurer, label, KkButtonSize.XS, density, null) / density
    }
    OverlayButton(
        label, { scene.onInput(GameplayInput.ExitToHome) },
        modifier.enter(scene, 0.7f).size(width.dp + 8.dp, if (touch) 48.dp else 30.dp).testTag("kinetickk.gameplay.exit"),
        KkButtonVariant.GHOST, KkButtonSize.XS, scene.actionsEnabled, textScale = scene.textScale, fontSize = 14f,
        contentDescription = language.text(GameplayText.ReturnHome),
    )
}

/** Entrance: fade + rise (or slide from the left) after [delay] seconds of the report clock. */
private fun Modifier.enter(scene: ReportScene, delay: Float, left: Boolean = false): Modifier = graphicsLayer {
    val p = ((scene.t - delay) / 0.5f).coerceIn(0f, 1f)
    val eased = (if (left) KkEase.Pull else KkEase.Out).transform(p)
    alpha = if (left) (p / 0.6f).coerceIn(0f, 1f) else p
    if (left) translationX = -(1f - eased) * 90f * density else translationY = (1f - eased) * 46f * density
}

private fun Modifier.reveal(progress: Float): Modifier = (if (progress == 0f) clearAndSetSemantics { } else this).graphicsLayer {
    alpha = progress
    translationY = -14.dp.toPx() * (1f - progress)
}

