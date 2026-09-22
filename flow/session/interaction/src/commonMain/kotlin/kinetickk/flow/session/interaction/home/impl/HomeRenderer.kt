// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.localizedContent
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min

/** Bounded draw-only interpolation; semantic selection remains in HomeReducer. */
internal class HomeMenuMotion {
    var position = 0f
    var cursor = Offset.Zero
    var accent = KineticAccent
    val sceneWeights = FloatArray(6) { if (it == 0) 1f else 0f }
    fun advance(target: HomeLayoutTarget, pointer: Offset, viewport: HomeViewport, delta: Float) {
        val amount = 1f - exp(-delta.coerceIn(0f, 0.1f) * 12f)
        val index = menuTargets.indexOf(target).coerceAtLeast(0)
        position += (index - position) * amount
        val normalized = if (viewport.width > 0f && viewport.height > 0f && pointer != Offset.Zero)
            Offset((pointer.x / viewport.width - 0.5f).coerceIn(-0.5f, 0.5f),
                (pointer.y / viewport.height - 0.5f).coerceIn(-0.5f, 0.5f)) else Offset.Zero
        cursor += (normalized - cursor) * amount
        accent = lerp(accent, menuAccent(target), amount)
        sceneWeights.indices.forEach { scene ->
            sceneWeights[scene] += ((if (scene == index) 1f else 0f) - sceneWeights[scene]) * amount
        }
    }
}

private val menuTargets = listOf(HomeLayoutTarget.START, HomeLayoutTarget.LAB, HomeLayoutTarget.ARMORY,
    HomeLayoutTarget.REBIRTH, HomeLayoutTarget.CODEX, HomeLayoutTarget.SETTINGS)
private val menuLabels = listOf(SessionText.START_RUN, SessionText.LAB, SessionText.ARMORY,
    SessionText.REBIRTH, SessionText.CODEX, SessionText.SETTINGS)
private val menuKeys = listOf("↵", "L", "A", "B", "C", "S")
private fun menuAccent(target: HomeLayoutTarget): Color = when (target) {
    HomeLayoutTarget.LAB -> Cyan
    HomeLayoutTarget.ARMORY -> Orange
    HomeLayoutTarget.REBIRTH -> Magenta
    HomeLayoutTarget.CODEX -> Gold
    HomeLayoutTarget.SETTINGS -> Violet
    else -> KineticAccent
}

internal fun DrawScope.drawHome(
    model: HomeUiModel,
    text: TextMeasurer,
    time: Float,
    layout: HomeLayoutGeometry,
    previewShape: CoreShape?,
    activeTarget: HomeLayoutTarget,
    motion: HomeMenuMotion,
) {
    val regular = layout.mode == HomeLayoutMode.REGULAR
    val portrait = layout.mode == HomeLayoutMode.COMPACT_PORTRAIT
    val margin = d(if (regular) 36f else 16f)
    val titleWidth = if (regular) size.width * 0.48f else if (portrait) size.width - margin * 2f else size.width * 0.43f
    val titleY = if (regular) size.height * 0.055f else d(8f)
    var titleSize = min(if (regular) 92f else if (portrait) 58f else 29f / text.scale, titleWidth / density / 4.65f / text.scale)
    if (regular) {
        val measured = text.delegate.measure("KINETICKK", textStyle(titleSize * text.scale, White, FontWeight.Bold, text.typography.display))
        val availableHeight = size.height * 0.27f - titleY - d(16f)
        titleSize *= min(1f, availableHeight / measured.size.height.coerceAtLeast(1))
    }
    drawLabel(text, "KINETICKK", margin, titleY, titleSize, White, display = true, maxWidth = titleWidth)
    // The cut continues through the wordmark and into the orbital composition.
    val cutY = titleY + d(titleSize * text.scale * 0.9f)
    drawLine(SpaceBlack, Offset(margin, cutY), Offset(margin + titleWidth, cutY - d(titleSize * 0.25f)), d(2.5f))
    if (regular) drawLabel(text, text.language.text(SessionText.HOME_INSTRUCTIONS), margin,
        size.height * 0.27f, 11f, Muted, maxWidth = titleWidth, maxLines = 2)

    val center = when {
        regular -> Offset(size.width * 0.77f, size.height * 0.43f)
        portrait -> Offset(size.width * 0.5f, size.height * 0.255f)
        else -> Offset(size.width * 0.76f, size.height * 0.33f)
    } + Offset(motion.cursor.x * d(30f), motion.cursor.y * d(22f))
    val radius = when {
        regular -> min(size.width * 0.12f, size.height * 0.19f)
        portrait -> min(size.width * 0.18f, size.height * if (size.height / density < 660f) 0.055f else 0.085f)
        else -> min(size.width * 0.09f, size.height * 0.16f)
    }
    drawKineticOrbits(center, radius, time, motion.accent, -28f + motion.cursor.x * 12f + motion.position * 3f)
    if (previewShape != null) drawCoreShape(previewShape, center, radius, White, time * 0.06f)
    else motion.sceneWeights.forEachIndexed { index, opacity ->
        if (opacity > 0.01f) {
            val color = White.copy(alpha = opacity)
            if (index == 0) drawCoreShape(model.coreShape, center, radius, color, time * 0.06f)
            else {
                val layered = opacity < 0.99f
                if (layered) drawContext.canvas.saveLayer(
                    Rect(center - Offset(radius * 1.5f, radius * 1.5f), center + Offset(radius * 1.5f, radius * 1.5f)),
                    Paint().apply { alpha = opacity },
                )
                drawSystemGlyph(when (index) {
                    1 -> SystemGlyphStyle.CONCENTRIC_RING
                    2 -> SystemGlyphStyle.SPEAR_LINE
                    3 -> SystemGlyphStyle.HEPTAGON_ORBIT
                    4 -> SystemGlyphStyle.TWIN_DIAMONDS
                    else -> SystemGlyphStyle.TRIANGLE_NETWORK
                }, center, radius, time * 0.20f, White)
                if (layered) drawContext.canvas.restore()
            }
        }
    }
    rotate(-28f + motion.cursor.x * 12f, center) {
        drawLine(SpaceBlack, center - Offset(radius * 1.18f, 0f), center + Offset(radius * 1.18f, 0f), radius * 0.045f)
        drawLine(motion.accent, center - Offset(radius * 1.7f, -radius * 0.05f),
            center + Offset(radius * 1.9f, radius * 0.04f), d(2.5f))
    }
    if (regular || portrait) {
        drawInterfaceGlyph(InterfaceGlyph.DIAMOND, Offset(size.width - margin - d(35f), d(27f)), d(8f), White)
        drawLabel(text, formatCompact(model.totalMatter, text.language), size.width - margin, d(13f), 17f,
            White, alignRight = true, display = true)
    }

    if (!portrait) {
        val first = layout.bounds(HomeLayoutTarget.START)
        val second = layout.bounds(HomeLayoutTarget.LAB)
        val top = first.top + (second.top - first.top) * motion.position
        drawKineticRibbon(Rect(first.left, top, first.right, top + first.height), motion.accent)
    }
    menuTargets.forEachIndexed { index, target ->
        val bounds = layout.bounds(target)
        val active = activeTarget == target
        if (portrait && active) drawKineticRibbon(bounds, motion.accent, d(8f))
        val ink = if (portrait) { if (active) SpaceBlack else White } else
            lerp(White, SpaceBlack, (1f - abs(index - motion.position) * 2.5f).coerceIn(0f, 1f))
        val numberWidth = d(if (regular) 32f else if (portrait) 28f else 25f)
        val fontSize = (if (regular) 31f else if (portrait) 23f else 22f) / text.scale.coerceAtLeast(1.25f) * 1.25f
        val y = bounds.center.y - d(fontSize * text.scale * 0.66f)
        drawLabel(text, "0${index + 1}", bounds.left + d(9f), bounds.center.y - d(7f * text.scale),
            if (regular) 10f else 8f, if (active) ink else Muted)
        drawLabel(text, text.language.text(menuLabels[index]).uppercase(), bounds.left + numberWidth + d(8f), y,
            fontSize, ink, display = true, maxWidth = bounds.width - numberWidth - d(if (portrait) 20f else 64f), fitWidth = true)
        if (!portrait) {
            if (active) drawKineticArrow(Offset(bounds.right - d(28f), bounds.center.y), d(22f), SpaceBlack)
            else drawLabel(text, menuKeys[index], bounds.right - d(22f), bounds.center.y - d(7f), 10f, Muted, centered = true)
        }
        if (!active) drawLine(DarkLine, bounds.bottomLeft + Offset(numberWidth + d(8f), 0f), bounds.bottomRight - Offset(d(14f), 0f), d(0.7f))
    }
    layout.actions.forEach { action ->
        action.target.coreShapeOrNull()?.let { shape -> drawCoreChoice(model, text, shape, action.bounds, previewShape == shape) }
    }
    if (regular) {
        val firstCore = layout.bounds(HomeLayoutTarget.CORE_ORB)
        val shape = previewShape ?: model.coreShape
        val definition = model.coreShape(shape)
        drawLabel(text, definition.displayName.localizedContent(text.language).uppercase(), firstCore.left,
            firstCore.top - d(30f * text.scale), 18f, motion.accent, display = true)
        drawLabel(text, (if (model.isCoreShapeUnlocked(shape)) definition.mechanicDescription else definition.unlockDescription).localizedContent(text.language),
            firstCore.left, firstCore.bottom + d(16f), 11f, Muted, maxWidth = size.width - firstCore.left - margin, maxLines = 2)
    }
    if (portrait) {
        val shape = previewShape ?: model.coreShape
        val definition = model.coreShape(shape)
        drawLabel(text, definition.displayName.localizedContent(text.language).uppercase(), margin,
            layout.bounds(HomeLayoutTarget.CORE_ORB).top - d(24f * text.scale),
            11f, motion.accent, display = true, maxWidth = size.width - margin * 2f)
        drawLabel(text, (if (model.isCoreShapeUnlocked(shape)) definition.mechanicDescription else definition.unlockDescription).localizedContent(text.language),
            margin, layout.bounds(HomeLayoutTarget.CORE_ORB).bottom + d(2f), 10f, Muted,
            maxWidth = size.width - margin * 2f, maxLines = 2)
    }
    drawLabel(text, text.language.text(if (regular) SessionText.COPYRIGHT else SessionText.COMPACT_LICENSE), if (!regular && !portrait) size.width * 0.54f else margin,
        size.height - d(22f), if (regular) 8f else 7f, Muted, maxWidth = if (!regular && !portrait) size.width * 0.44f else size.width - margin * 2f)
}

private fun DrawScope.drawCoreChoice(model: HomeUiModel, text: TextMeasurer, shape: CoreShape, bounds: Rect, preview: Boolean) {
    val selected = model.coreShape == shape
    val unlocked = model.isCoreShapeUnlocked(shape)
    val accent = if (selected) KineticAccent else if (unlocked || preview) White else Muted.copy(alpha = 0.45f)
    if (selected || preview) {
        drawRect(accent.copy(alpha = 0.07f), bounds.topLeft, bounds.size)
        val corner = d(7f)
        listOf(bounds.topLeft, bounds.topRight, bounds.bottomLeft, bounds.bottomRight).forEachIndexed { index, p ->
            drawLine(accent, p, p + Offset(if (index % 2 == 0) corner else -corner, 0f), d(1.5f))
            drawLine(accent, p, p + Offset(0f, if (index < 2) corner else -corner), d(1.5f))
        }
    }
    drawCoreShape(shape, Offset(bounds.center.x, bounds.top + bounds.height * 0.35f), d(13f), accent, 0f)
    if (!unlocked) drawInterfaceGlyph(InterfaceGlyph.LOCK, Offset(bounds.right - d(8f), bounds.top + d(8f)), d(4f), Muted)
    drawLabel(text, model.coreShape(shape).displayName.localizedContent(text.language).uppercase(), bounds.center.x,
        bounds.top + bounds.height * 0.66f, 10f / text.scale.coerceAtLeast(1f), if (selected) White else Muted,
        centered = true, display = true, maxWidth = bounds.width - d(5f), fitWidth = true)
}

private fun DrawScope.drawCoreShape(shape: CoreShape, center: Offset, radius: Float, color: Color, rotation: Float) {
    when (shape) {
        CoreShape.ORB -> drawCircle(color, radius, center)
        CoreShape.PRISM -> drawPolygon(center, radius, 4, (PI / 4).toFloat() + rotation, color, Fill)
        CoreShape.SHARD -> drawPolygon(center, radius, 3, -(PI / 2).toFloat() + rotation, color, Fill)
        CoreShape.RING -> drawCircle(color, radius, center, style = Stroke(radius * 0.17f))
        CoreShape.DIAMOND -> drawPolygon(center, radius, 4, rotation, color, Fill)
        CoreShape.TESSERACT -> {
            drawPolygon(center, radius, 4, (PI / 4).toFloat() + rotation, color, Stroke(radius * 0.045f))
            drawPolygon(center, radius * 0.55f, 4, (PI / 4).toFloat() - rotation, color, Stroke(radius * 0.045f))
        }
    }
}
