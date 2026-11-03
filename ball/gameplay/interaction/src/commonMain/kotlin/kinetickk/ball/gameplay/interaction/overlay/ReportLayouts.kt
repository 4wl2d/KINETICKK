// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkShape
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.drawKkGrid
import kinetickk.foundation.design.drawKkHalftone
import kinetickk.foundation.design.drawKkRadialFade
import kinetickk.foundation.design.drawKkRingBurst
import kinetickk.foundation.design.kkLerp
import kinetickk.foundation.design.kkStroke
import kotlin.math.max
import kinetickk.ball.gameplay.interaction.canvas.fx.*

@Composable
internal fun TerminalContent(
    engine: GameplayRenderModel,
    elapsed: Float,
    enabled: Boolean,
    reactionsThisRun: Int = 0,
    discoveredThisRun: Int = 0,
    onInput: (GameplayInput) -> Unit,
) {
    val language = LocalAppLanguage.current
    val presentation = remember(engine, language, reactionsThisRun, discoveredThisRun) {
        engine.terminalPresentation(language, reactionsThisRun, discoveredThisRun)
    }
    TerminalContent(presentation, engine.settings.textScale, engine.settings.runStatisticsOnLeft, elapsed, enabled, onInput)
}

/**
 * Run report (Report / Report-Defeat boards): two-line title, cause stamp, matter banked, bank
 * total, build row, the shatter illustration, Combat and Collection statistics on the skewed
 * panel (mirrored when statistics sit on the left) and the game's next actions. A Baby mode run
 * shows a "Not counted" stamp under the cause, its matter as not banked and no bank or Rebirth
 * lines. [textScale] is the text-size setting: UI text renders at the board size at the default
 * ([overlayUiTextScale]); the title and the matter figure are display type and ignore it.
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
    val shatter = reportShatterArt(presentation.victory)
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
internal class ReportScene(
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

internal fun prepareTerminalVisualResources() {
    reportShatterArt(false).paths
    reportShatterArt(true).paths
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
                compact = true, gapScale = 0.6f, rebirthLines = false)
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
                // Landscape phones keep the summary column for the title, matter and build: what the
                // Victory earns on the Rebirth ladder leads the statistics instead.
                scene.presentation.rebirthEarned?.let { earned ->
                    ReportRebirthEarned(earned, 13f * scene.textScale, 11f * scene.textScale, Modifier.padding(bottom = 10.dp))
                }
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
            // The recap follows the actions here, so the next run's buttons stay on the first screen.
            ReportSummary(scene, frame, titleSize = 44f, matterSize = 40f, gemSize = 22f, stampSize = 15f, slotSize = 50f, relicSize = 30f,
                compact = true, inlineShatter = true, recap = false, rebirthLines = false)
            Spacer(Modifier.height(20.dp))
            ReportActions(scene, frame, Modifier.fillMaxWidth(), primaryHeight = 56f, primaryFont = 24f, ghostFont = 20f)
            Spacer(Modifier.height(10.dp))
            ReportMenu(scene, frame, Modifier, touch = true)
            ReportBuildRecap(scene, frame, compact = true)
            // What the Victory earns on the Rebirth ladder follows the recap, so the actions stay on the first screen.
            scene.presentation.rebirthEarned?.let { earned ->
                Spacer(Modifier.height(18.dp))
                ReportRebirthEarned(earned, 14f * scene.textScale, 12f * scene.textScale)
            }
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
internal fun DrawScope.drawReportShatter(scene: ReportScene, center: Offset, scale: Float) {
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

/** Entrance: fade + rise (or slide from the left) after [delay] seconds of the report clock. */
internal fun Modifier.enter(scene: ReportScene, delay: Float, left: Boolean = false): Modifier = graphicsLayer {
    val p = ((scene.t - delay) / 0.5f).coerceIn(0f, 1f)
    val eased = (if (left) KkEase.Pull else KkEase.Out).transform(p)
    alpha = if (left) (p / 0.6f).coerceIn(0f, 1f) else p
    if (left) translationX = -(1f - eased) * 90f * density else translationY = (1f - eased) * 46f * density
}

internal fun Modifier.reveal(progress: Float): Modifier = (if (progress == 0f) clearAndSetSemantics { } else this).graphicsLayer {
    alpha = progress
    translationY = -14.dp.toPx() * (1f - progress)
}
