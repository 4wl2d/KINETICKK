// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.abs
import kotlin.math.sqrt

// World-anchored HUD (halos, tether, dash afterimages, overheat stamp) and screen-space state
// effects (critical vignette, polarity edge glow, overdrive border and streaks). World-anchored
// geometry uses world units like the Core it hugs; UI text and screen effects use dp.

private const val CORE_HALO_RATIO = 2.2f
private const val HALO_ARC_DEGREES = 110f
private const val CURSOR_HALO_RADIUS = 32f
private const val CURSOR_ARC_DEGREES = 300f

/** Tether, Core halo, cursor halo and the Core-anchored state marks. */
internal fun DrawScope.drawHudWorldAnchors(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    renderTime: Float,
    shakeX: Float,
    shakeY: Float,
    memory: HudPresentationMemory?,
    critical: Boolean,
) {
    if (engine.phase == GamePhase.GAME_OVER) return
    val roles = measurer.roles
    val core = world(engine, engine.coreX, engine.coreY, shakeX, shakeY)
    val cursor = Offset(engine.pointerX + shakeX * 0.18f, engine.pointerY + shakeY * 0.18f)
    val draining = memory != null && memory.polarityDraining(renderTime)
    val haloRadius = GameplayRenderModel.CORE_RADIUS * CORE_HALO_RATIO

    drawTether(engine, roles, core, cursor, renderTime, critical, draining)
    if (engine.overdriveTime > 0f) drawOverdriveCore(roles, core, renderTime)
    if (engine.dashPhaseTime > 0f) drawDashAfterimages(engine, roles, core)
    drawCoreHalo(engine, roles, core, haloRadius)
    if (critical) drawCriticalRing(roles, core, renderTime)
    drawCursorHalo(engine, measurer, cursor, renderTime, draining)
    if (engine.overheated) drawOverheatStamp(measurer, core, haloRadius, renderTime)
}

/** Dashed Core-to-cursor line: heavier with more pull, solid threat when contact is close. */
private fun DrawScope.drawTether(
    engine: GameplayRenderModel,
    roles: KkRolePalette,
    core: Offset,
    cursor: Offset,
    renderTime: Float,
    critical: Boolean,
    draining: Boolean,
) {
    val dx = cursor.x - core.x
    val dy = cursor.y - core.y
    val distance = sqrt(dx * dx + dy * dy)
    val startGap = GameplayRenderModel.CORE_RADIUS + 4f
    val endGap = 16f
    if (distance <= startGap + endGap + 2f) return
    val ux = dx / distance
    val uy = dy / distance
    val start = Offset(core.x + ux * startGap, core.y + uy * startGap)
    val end = Offset(cursor.x - ux * endGap, cursor.y - uy * endGap)
    val close = engine.tetherDistance < CLOSE_CONTACT_DISTANCE
    val weight = (distance / 120f * engine.tetherAuthority.coerceIn(0.35f, 1f)).coerceIn(1f, 3f)
    val pulse = 0.55f + 0.45f * kkPulse(renderTime, 1.2f, KkEase.Out)
    when {
        close -> drawLine(roles.threat.copy(alpha = 0.9f), start, end, weight + 1f, StrokeCap.Butt)
        else -> {
            val color = when {
                critical -> roles.threat.copy(alpha = 0.9f * pulse)
                draining -> roles.pol.copy(alpha = 0.55f * pulse)
                else -> roles.threat.copy(alpha = 0.45f * pulse)
            }
            drawLine(color, start, end, weight, StrokeCap.Butt, HudDrawCache.lineDash(6f, 5f))
        }
    }
}

/** Matches the game's existing "singularity close" presentation threshold (world units). */
private const val CLOSE_CONTACT_DISTANCE = 75f

/** Core halo: left arc integrity, right arc heat, dashed shield ring. */
private fun DrawScope.drawCoreHalo(engine: GameplayRenderModel, roles: KkRolePalette, core: Offset, radius: Float) {
    val stroke = kkStroke(4f)
    val topLeft = Offset(core.x - radius, core.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    val track = Kk.Bone.copy(alpha = 0.16f)
    val integrity = (engine.hp / engine.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val heat = (engine.heat / GameplayRenderModel.MAX_HEAT).coerceIn(0f, 1f)
    drawArc(track, 125f, HALO_ARC_DEGREES, false, topLeft, arcSize, style = stroke)
    if (integrity > 0f) {
        drawArc(if (isIntegrityCritical(engine.hp, engine.maxHp)) roles.threat else Kk.Bone, 125f,
            HALO_ARC_DEGREES * integrity, false, topLeft, arcSize, style = stroke)
    }
    drawArc(track, 55f, -HALO_ARC_DEGREES, false, topLeft, arcSize, style = stroke)
    if (heat > 0f) {
        drawArc(if (engine.overheated) roles.threat else roles.heat, 55f, -HALO_ARC_DEGREES * heat, false,
            topLeft, arcSize, style = stroke)
    }
    if (engine.maxShield > 0f) {
        val alpha = if (engine.shield > 0f) 0.8f else 0.25f
        drawCircle(roles.shield.copy(alpha = alpha), radius * 45f / 38f, core,
            style = HudDrawCache.dashed(HudDash.SHIELD_RING, 1.5f, 18f, 6f))
    }
}

/** Cursor halo: polarity arc, threat under 25 %, pulsing while polarity drains. */
private fun DrawScope.drawCursorHalo(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    cursor: Offset,
    renderTime: Float,
    draining: Boolean,
) {
    val roles = measurer.roles
    val stability = engine.polarityStability.coerceIn(0f, 1f)
    val low = stability <= 0.25f
    val alpha = if (draining) 0.35f + 0.65f * (1f - kkPulse(renderTime, 1.1f, KkEase.Out)) else 1f
    val radius = CURSOR_HALO_RADIUS
    val topLeft = Offset(cursor.x - radius, cursor.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    val stroke = kkStroke(3f)
    drawArc(roles.pol.copy(alpha = 0.18f), 120f, CURSOR_ARC_DEGREES, false, topLeft, arcSize, style = stroke)
    if (stability > 0f) {
        drawArc(if (low) roles.threat else roles.pol, 120f, CURSOR_ARC_DEGREES * stability, false, topLeft, arcSize,
            alpha = alpha, style = stroke)
    }
    if (draining && low) {
        // Changes every frame while draining: cached digit layouts, placed clear of the controls.
        // UI text (it follows the text size), in a style of its own: see [polarityLabelStyle].
        val text = HudMeasurers.ui(measurer)
        val percent = (stability * 100f).toLong()
        val style = polarityLabelStyle(text)
        val labelWidth = kkTabularNumberWidth(text, percent, style, suffix = PERCENT)
        val labelHeight = measureKkText(text, "0", style).kkBoxHeight
        val box = PolarityLabelBox
        placePolarityLabel(cursor.x, cursor.y, radius + d(14f), labelWidth, labelHeight, size.width, size.height, density, box)
        // Solid text: animating a text's alpha repaints its paragraph every frame; the halo pulses.
        drawKkTabularNumber(text, percent, style, box[0], box[1], roles.threat, suffix = PERCENT)
        HudLayoutProbe.record(HudBlock.POLARITY_LABEL, box[0], box[1], box[2], box[3])
    }
}

/**
 * The polarity % (threat) shares the screen with the chain count (bone / you), both drawn digit by
 * digit. Layouts whose styles have equal layout attributes share one paragraph (the style memo and
 * the platform's layout cache both match them), and painting a shared digit in two colors reshapes
 * it every frame, so the % keeps its own line height, a layout attribute no chain style uses.
 */
internal fun polarityLabelStyle(measurer: TextMeasurer) =
    measurer.typography.condStyle(26f, tabular = true, lineHeightEm = POLARITY_LINE_HEIGHT_EM)

/** Line height of the polarity % (the chain uses [COND_LINE_HEIGHT_EM]). */
internal const val POLARITY_LINE_HEIGHT_EM = 0.87f

private const val PERCENT = "%"
private val PolarityLabelBox = FloatArray(4)

/**
 * Places the polarity value beside the cursor halo ([gap] from the cursor): left, right, above or
 * below (then up to five label sizes further out), each slid along the screen edge to stay on
 * screen, taking the first that stays clear of the running controls (Dash sits where the cursor
 * strains on phones). Writes left, top, right,
 * bottom into [out]; returns the choice 0..3.
 */
internal fun placePolarityLabel(
    cursorX: Float,
    cursorY: Float,
    gap: Float,
    labelWidth: Float,
    labelHeight: Float,
    width: Float,
    height: Float,
    density: Float,
    out: FloatArray,
): Int {
    val margin = 8f * density
    val maxLeft = (width - margin - labelWidth).coerceAtLeast(margin)
    val maxTop = (height - margin - labelHeight).coerceAtLeast(margin)
    for (attempt in 0 until 24) {
        // Left, right, above, below; then the same directions up to five label sizes further out.
        val choice = attempt % 4
        val reach = gap + (attempt / 4) * (if (choice < 2) labelWidth else labelHeight)
        val left = when (choice) {
            0 -> cursorX - reach - labelWidth
            1 -> cursorX + reach
            else -> (cursorX - labelWidth * 0.5f).coerceIn(margin, maxLeft)
        }
        val top = when (choice) {
            2 -> cursorY - reach - labelHeight
            3 -> cursorY + reach
            else -> (cursorY - labelHeight * 0.5f).coerceIn(margin, maxTop)
        }
        val right = left + labelWidth
        val bottom = top + labelHeight
        // Sideways placements may not slide over the cursor; vertical ones may slide along the edge.
        if (left < margin || top < margin || right > width - margin || bottom > height - margin) continue
        var blocked = false
        forEachRunningControlBounds(width, height, density) { _, cl, ct, cr, cb ->
            if (left < cr && right > cl && top < cb && bottom > ct) blocked = true
        }
        if (!blocked) {
            out[0] = left; out[1] = top; out[2] = right; out[3] = bottom
            return choice
        }
    }
    // Nothing is clear (cannot happen with the current layouts): keep it left of the cursor, on screen.
    val left = (cursorX - gap - labelWidth).coerceIn(margin, maxLeft)
    val top = (cursorY - labelHeight * 0.5f).coerceIn(margin, maxTop)
    out[0] = left; out[1] = top; out[2] = left + labelWidth; out[3] = top + labelHeight
    return 0
}

/** Three fading rings behind the Core along its travel while a dash is in progress. */
private fun DrawScope.drawDashAfterimages(engine: GameplayRenderModel, roles: KkRolePalette, core: Offset) {
    val speed = engine.speed
    if (speed < 1f) return
    val bx = -engine.velocityX / speed
    val by = -engine.velocityY / speed
    val phase = (engine.dashPhaseTime / 0.24f).coerceIn(0f, 1f)
    val stroke = kkStroke(2f)
    for (index in 1..3) {
        val f = index * 0.2f
        val distance = 60f * (1f + f * 2f)
        drawCircle(roles.you.copy(alpha = (0.7f - f) * phase), 18f, Offset(core.x + bx * distance, core.y + by * distance), style = stroke)
    }
}

/** Overdrive at the Core: a soft `you` glow and a ring pulsing out every 1.6 s. */
private fun DrawScope.drawOverdriveCore(roles: KkRolePalette, core: Offset, renderTime: Float) {
    val glowRadius = 90f
    val brush = HudDrawCache.brush(HudBrush.CORE_GLOW, glowRadius, 0f, 0f, 0f, roles.you) {
        Brush.radialGradient(0f to roles.you.copy(alpha = 0.28f), 1f to roles.you.copy(alpha = 0f), center = Offset.Zero, radius = glowRadius)
    }
    translate(core.x, core.y) { drawCircle(brush, glowRadius, Offset.Zero) }
    drawKkRingBurst(core, 140f, kkLoop(renderTime, 1.6f), roles.you, 3f)
}

/** Critical integrity: a threat ring contracting onto the Core at heart rate. */
private fun DrawScope.drawCriticalRing(roles: KkRolePalette, core: Offset, renderTime: Float) {
    val t = KkEase.In.transform(kkLoop(renderTime, 0.9f))
    val scale = kkLerp(1.5f, 0.15f, t)
    drawCircle(roles.threat.copy(alpha = t), 70f * scale, core, style = kkStroke(2f))
}

/** "Overheat" stamp beside the Core while the dash is offline (pulsing). */
private fun DrawScope.drawOverheatStamp(measurer: TextMeasurer, core: Offset, haloRadius: Float, renderTime: Float) {
    val language = measurer.language
    val text = HudScratch.overheat.of(language, 0L) { language.text(HudRedesignText.Overheat) }
    // UI text: follows the text size (the plate grows with it).
    val layout = HudDrawCache.layout(HudText.OVERHEAT, HudMeasurers.ui(measurer), text,
        measurer.typography.condStyle(14f, trackingEm = 0.06f, lineHeightEm = 1f), uppercase = true)
    val alpha = 0.35f + 0.65f * (1f - kkPulse(renderTime, 1.1f, KkEase.Out))
    val k = 14f / 17f
    val width = layout.size.width + d(22f) * k
    val height = layout.kkBoxHeight + d(9f) * k
    val left = core.x + haloRadius * 0.9f
    val top = core.y + haloRadius * 0.55f
    val roles = measurer.roles
    rotate(-6f, Offset(left + width * 0.5f, top + height * 0.5f)) {
        drawRect(Kk.Ink, Offset(left + d(3f) * k, top + d(3f) * k), Size(width, height), alpha)
        drawRect(roles.threat, Offset(left, top), Size(width, height), alpha)
        // The plate pulses; the ink label stays solid (a text alpha change repaints its paragraph).
        drawKkText(layout, left + width * 0.5f, top + height * 0.5f + d(0.5f), Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER)
    }
}

/** Screen-space state effects: critical vignette, polarity edge glow, overdrive border and streaks. */
internal fun DrawScope.drawHudScreenStates(
    engine: GameplayRenderModel,
    roles: KkRolePalette,
    renderTime: Float,
    memory: HudPresentationMemory?,
    critical: Boolean,
) {
    if (critical) drawCriticalVignette(roles, heartbeat(renderTime))
    if (memory != null && memory.polarityDraining(renderTime)) drawPolarityEdge(engine, roles, renderTime)
    if (engine.overdriveTime > 0f) drawOverdriveBorder(roles, renderTime)
}

private fun DrawScope.drawCriticalVignette(roles: KkRolePalette, alpha: Float) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    // `radial-gradient(ellipse at center, transparent 42%, threat 42% alpha at 100%)`: a circle of
    // the farthest-corner radius squashed vertically into the screen's ellipse.
    val radius = w * 0.5f * 1.4142f
    val brush = HudDrawCache.brush(HudBrush.VIGNETTE, w, h, 0f, 0f, roles.threat) {
        Brush.radialGradient(
            0f to Color.Transparent, 0.42f to Color.Transparent, 1f to roles.threat.copy(alpha = 0.42f),
            center = Offset(w * 0.5f, h * 0.5f), radius = radius,
        )
    }
    scale(1f, h / w, Offset(w * 0.5f, h * 0.5f)) {
        drawRect(brush, Offset(0f, h * 0.5f - w * 0.5f), Size(w, w), alpha)
    }
}

/** Threat edge glow on the side the cursor strains toward, pulsing (`edge-warn`, 0.9 s). */
private fun DrawScope.drawPolarityEdge(engine: GameplayRenderModel, roles: KkRolePalette, renderTime: Float) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val reachX = engine.pointerX / w * 2f - 1f
    val reachY = engine.pointerY / h * 2f - 1f
    val band = d(150f)
    val alpha = 0.35f + 0.65f * (1f - kkPulse(renderTime, 0.9f, KkEase.Out))
    val threat = roles.threat.copy(alpha = 0.42f)
    val pol = roles.pol.copy(alpha = 0.12f)
    if (abs(reachX) >= abs(reachY)) {
        val right = reachX > 0f
        val slot = if (right) HudBrush.EDGE_RIGHT else HudBrush.EDGE_LEFT
        val left = if (right) w - band else 0f
        val brush = HudDrawCache.brush(slot, left, band, 0f, 0f, roles.threat) {
            val stops = if (right) arrayOf(0f to Color.Transparent, 0.45f to pol, 1f to threat)
            else arrayOf(0f to threat, 0.55f to pol, 1f to Color.Transparent)
            Brush.horizontalGradient(*stops, startX = left, endX = left + band)
        }
        drawRect(brush, Offset(left, 0f), Size(band, h), alpha)
    } else {
        val bottom = reachY > 0f
        val slot = if (bottom) HudBrush.EDGE_BOTTOM else HudBrush.EDGE_TOP
        val top = if (bottom) h - band else 0f
        val brush = HudDrawCache.brush(slot, top, band, 0f, 0f, roles.threat) {
            val stops = if (bottom) arrayOf(0f to Color.Transparent, 0.45f to pol, 1f to threat)
            else arrayOf(0f to threat, 0.55f to pol, 1f to Color.Transparent)
            Brush.verticalGradient(*stops, startY = top, endY = top + band)
        }
        drawRect(brush, Offset(0f, top), Size(w, band), alpha)
    }
}

private val OverdriveGlowWidths = floatArrayOf(10f, 24f, 44f, 70f)
private val OverdriveGlowAlphas = floatArrayOf(0.07f, 0.05f, 0.035f, 0.025f)

/** Pulsing `you` screen border (inset 3 dp + soft inner glow as stacked strokes) and speed streaks. */
private fun DrawScope.drawOverdriveBorder(roles: KkRolePalette, renderTime: Float) {
    val w = size.width
    val h = size.height
    val alpha = 0.35f + 0.65f * (1f - kkPulse(renderTime, 1.4f, KkEase.Out))
    for (index in OverdriveGlowWidths.indices) {
        val width = d(OverdriveGlowWidths[index])
        drawRect(roles.you, Offset(width * 0.5f, width * 0.5f), Size(w - width, h - width),
            alpha * OverdriveGlowAlphas[index], kkStroke(width))
    }
    val border = d(3f)
    drawRect(roles.you, Offset(border * 0.5f, border * 0.5f), Size(w - border, h - border), alpha, kkStroke(border))
    drawSpeedStreak(roles, h * 180f / 810f, d(240f), kkLoop(renderTime, 0.9f))
    drawSpeedStreak(roles, h * 590f / 810f, d(280f), kkLoop(renderTime, 1.1f, 0.45f))
}

/** `kk-streak`: a 2 dp line entering from the right edge and crossing 900 dp to the left. */
private fun DrawScope.drawSpeedStreak(roles: KkRolePalette, y: Float, length: Float, progress: Float) {
    val alpha = if (progress < 0.15f) progress / 0.15f else 1f - (progress - 0.15f) / 0.85f
    val x = size.width + d(60f) - d(900f) * progress
    drawRect(roles.you, Offset(x, y - d(1f)), Size(length, d(2f)), alpha.coerceIn(0f, 1f))
}
