// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kinetickk.ball.content.api.CoreShape
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkFormGeometry
import kinetickk.foundation.design.KkFormPaths
import kinetickk.foundation.design.KkTooltipPlacement
import kinetickk.foundation.design.drawKkGrid
import kinetickk.foundation.design.drawKkHalftone
import kinetickk.foundation.design.drawKkRadialFade
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.drawKkThreatHatch
import kinetickk.foundation.design.drawKkTooltip
import kinetickk.foundation.design.kkBoxHeight
import kinetickk.foundation.design.kkLerp
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.kkTooltipRect
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.measureKkTooltip
import kinetickk.foundation.design.wideStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal fun DrawScope.drawHome(
    model: HomeUiModel,
    text: CanvasTextMeasurer,
    time: Float,
    layout: HomeLayoutGeometry,
    state: HomeDrawState,
    motion: HomeMenuMotion,
) {
    val scene = layout.scene
    drawRect(Kk.Ink)
    // The layout follows the measured viewport; skip frames drawn before it is known.
    if (scene.heroRadius <= 1f || size.width <= 1f || size.height <= 1f) return
    val accent = motion.accent
    val tinted = motion.tinted(text)
    val language = text.language
    val facts = motion.facts(model, state.activeTarget, language, state.textScale)
    drawRect(kkMix(Kk.Ink, accent, 0.07f))
    drawKkGrid(Rect(Offset.Zero, size), Kk.Bone.copy(alpha = 0.045f), scene.gridSpacing / density)
    drawBackgroundWord(text, facts.label, scene, accent)
    drawHero(model, text, time, scene, state, motion)
    if (state.easy) drawHomeBabyStamp(text, scene)
    drawHeader(model, text, layout, state, accent)
    drawMenu(model, tinted, layout, motion, state.activeTarget, state.textScale)
    scene.facts?.let { drawFactsCard(tinted, it, facts, layout, state, motion, accent) }
    drawFormPanel(model, tinted, layout, state, motion)
    layout.quit?.let { drawQuit(tinted, it.bounds, layout.scene, state.quitActive) }
    drawVersion(text, layout)
    val open = state.openInfo ?: state.activeInfo
    val info = open?.let(layout::info)
    if (open != null && info != null) {
        val content = when (open) {
            HomeInfoTarget.FACTS -> facts.info
            HomeInfoTarget.FORM -> homeFormInfo(model, language)
        }
        val placement = if (layout.mode == HomeLayoutMode.COMPACT_PORTRAIT) KkTooltipPlacement.ABOVE else KkTooltipPlacement.ABOVE_END
        drawKkTooltip(text, info.bounds, content, placement)
    } else if (homeShowsRunModeTip(layout, state.openInfo, state.activeInfo, state.runModeActive, state.runModeTip)) {
        drawKkTooltip(text, layout.runModeToggle.bounds, language.text(SessionRedesignText.BABY_MODE_INFO), KkTooltipPlacement.BELOW)
    }
}

/** Baby mode's tooltip shows while its toggle is hovered, focused or was just tapped, unless a (!) tooltip is drawn. */
internal fun homeShowsRunModeTip(
    layout: HomeLayoutGeometry,
    openInfo: HomeInfoTarget?,
    activeInfo: HomeInfoTarget?,
    runModeActive: Boolean,
    runModeTip: Boolean,
): Boolean = (runModeActive || runModeTip) && (openInfo ?: activeInfo)?.let(layout::info) == null

/** The rect [drawHome] draws Baby mode's tooltip slip in: below the toggle, clamped into the [viewport]. */
internal fun homeRunModeTipRect(text: CanvasTextMeasurer, layout: HomeLayoutGeometry, viewport: HomeViewport): Rect {
    val body = measureKkTooltip(text, text.language.text(SessionRedesignText.BABY_MODE_INFO), viewport.density)
    val within = Rect(0f, 0f, viewport.width, viewport.height)
    return kkTooltipRect(layout.runModeToggle.bounds, body.kkBoxHeight, viewport.density, within, KkTooltipPlacement.BELOW)
}

internal fun DrawScope.displaySize(text: CanvasTextMeasurer, px: Float): Float = max(1f, px / density / text.scale)

private fun DrawScope.drawBackgroundWord(text: CanvasTextMeasurer, label: String, scene: HomeScene, accent: Color) {
    val layout = measureKkText(text, label, text.typography.wideStyle(displaySize(text, scene.wordSize), lineHeightEm = 1f), uppercase = true)
    drawKkText(layout, scene.wordLeft, scene.wordTop, accent.copy(alpha = 0.07f))
}

private fun DrawScope.drawHero(
    model: HomeUiModel,
    text: CanvasTextMeasurer,
    time: Float,
    scene: HomeScene,
    state: HomeDrawState,
    motion: HomeMenuMotion,
) {
    val accent = motion.accent
    val roles = text.roles
    val center = scene.heroCenter
    val k = scene.heroRadius / 260f
    motion.strokes(scene.unit, density)
    // Halftone disc (accent @12 %, a pastel soft in Baby mode), radially faded.
    val halftoneRadius = 310f * k
    val halftone = Rect(center.x - halftoneRadius, center.y - halftoneRadius, center.x + halftoneRadius, center.y + halftoneRadius)
    val halftoneTint = if (state.easy) kkMix(roles.soft, Kk.Bone, 0.45f).copy(alpha = 0.12f) else accent.copy(alpha = 0.12f)
    drawKkRadialFade(halftone) { drawKkHalftone(halftone, halftoneTint) }
    // Accretion rays: dashes crawling inward along 14 spokes.
    val rayScale = max(scene.unit, density)
    for (index in 0 until 14) {
        val angle = index / 14f * 2f * PI.toFloat() + 0.2f
        val outer = (300f - (index % 3) * 30f) * k
        val inner = 70f * k
        val period = 48f * k
        val loop = 0.9f + (index % 4) * 0.25f
        val phase = ((time / loop) % 1f) * period
        val dx = cos(angle)
        val dy = sin(angle)
        val color = accent.copy(alpha = if (index % 2 == 1) 0.35f else 0.6f)
        val width = (if (index % 2 == 1) 1.5f else 2.5f) * rayScale
        val length = outer - inner
        var start = phase - period
        while (start < length) {
            val a = max(0f, start)
            val b = min(length, start + 16f * k)
            if (b > a) {
                drawLine(
                    color,
                    Offset(center.x + dx * (outer - a), center.y + dy * (outer - a)),
                    Offset(center.x + dx * (outer - b), center.y + dy * (outer - b)),
                    width,
                )
            }
            start += period
        }
    }
    // Orbit plane rings (dashed outer, faint middle, accent inner).
    drawOval(Kk.Bone.copy(alpha = 0.18f), Offset(center.x - 300f * k, center.y - 300f * k * TILT), Size(600f * k, 600f * k * TILT), style = motion.dashedThin)
    drawOval(Kk.Bone.copy(alpha = 0.08f), Offset(center.x - 260f * k, center.y - 260f * k * TILT), Size(520f * k, 520f * k * TILT), style = kkStroke(max(k, 1f)))
    drawOval(accent.copy(alpha = 0.4f), Offset(center.x - 150f * k, center.y - 150f * k * TILT), Size(300f * k, 300f * k * TILT), style = motion.dashedThin)

    val spin = (time / HOME_ORBIT_SECONDS * 360f) % 360f
    val coreAngle = spin - 20f
    // Conic trail behind the Core, in the tilted plane.
    val trailRadius = 259.7f * k
    withTransform({
        scale(1f, TILT, center)
        rotate(coreAngle, center)
    }) {
        drawArc(
            motion.trailBrush(center), 100f, 167f, false,
            Offset(center.x - trailRadius, center.y - trailRadius), Size(trailRadius * 2f, trailRadius * 2f),
            style = kkStroke(28f * k),
        )
    }
    val coreRadians = coreAngle * PI.toFloat() / 180f
    val planeY = -cos(coreRadians)
    val core = Offset(center.x + 260f * k * sin(coreRadians), center.y + 260f * k * planeY * TILT)
    val coreFar = planeY < 0f
    val perspective = 1500f / (1500f - 260f * planeY * SIN_TILT)
    // Baby mode's singularity is the harmless soft cursor, and so is its end of the tether.
    val cursorTone = if (state.easy) roles.soft else roles.threat
    // Far half first: enemies and Core behind the singularity.
    drawEnemies(text, time, center, k, spin, accent, far = true, motion)
    if (coreFar) {
        drawTether(core, center, k, accent, cursorTone)
        drawCore(model, core, k, perspective, time, motion)
    }
    drawSingularity(center, k, time, cursorTone, motion)
    drawEnemies(text, time, center, k, spin, accent, far = false, motion)
    if (!coreFar) {
        drawTether(core, center, k, accent, cursorTone)
        drawCore(model, core, k, perspective, time, motion)
    }
}

private fun DrawScope.drawTether(core: Offset, center: Offset, k: Float, accent: Color, cursor: Color) {
    val width = max(3f * k, density * 1.5f)
    for (segment in 0 until 8) {
        val a = segment / 8f
        val b = (segment + 1) / 8f
        drawLine(
            lerp(accent, cursor.copy(alpha = 0.9f), (a + b) * 0.5f),
            Offset(kkLerp(core.x, center.x, a), kkLerp(core.y, center.y, a)),
            Offset(kkLerp(core.x, center.x, b), kkLerp(core.y, center.y, b)),
            width,
        )
    }
}

private fun DrawScope.drawSingularity(center: Offset, k: Float, time: Float, tone: Color, motion: HomeMenuMotion) {
    val turn = (time / HOME_ORBIT_SECONDS * 360f) % 360f
    rotate(turn, center) {
        drawCircle(tone.copy(alpha = 0.5f), 70f * k, center, style = motion.dashedRing)
    }
    rotate(-turn, center) {
        drawCircle(tone.copy(alpha = 0.7f), 44f * k, center, style = motion.dashedRing)
    }
    val ring = 24f * k
    val width = max(4f * k, density * 2f)
    drawCircle(tone.copy(alpha = 0.08f), ring, center, style = kkStroke(width * 4.5f))
    drawCircle(tone.copy(alpha = 0.16f), ring, center, style = kkStroke(width * 2.5f))
    drawCircle(tone, ring, center, style = kkStroke(width))
    drawCircle(Kk.Bone, max(6f * k, density * 3f), center)
}

/** The current character's form ([KkFormPaths]) on the hero orbit, with its accent glow. */
private fun DrawScope.drawCore(
    model: HomeUiModel,
    center: Offset,
    k: Float,
    perspective: Float,
    time: Float,
    motion: HomeMenuMotion,
) {
    val shape = model.coreShape
    val radius = max(32f * k, density * 18f) * perspective
    val accent = motion.accent
    // Glow (box-shadow 46 px accent @85 %) as stacked translucent rings, never a real-time blur.
    for (index in 0 until 5) {
        drawCircle(accent.copy(alpha = 0.85f * (0.13f - index * 0.025f)), radius + (6f + index * 8f) * k, center)
    }
    val spin = when (shape) {
        CoreShape.PRISM, CoreShape.HEXAGON -> time / 60f * 360f
        CoreShape.TESSERACT, CoreShape.STAR -> time / 40f * 360f
        CoreShape.SPIRAL -> -time / 30f * 360f
        else -> 0f
    }
    val index = shape.formIndex()
    val path = KkFormPaths.of(index, radius)
    rotate(spin % 360f, center) {
        translate(center.x, center.y) {
            val outline = max(10f * k, density * 5f)
            if (KkFormGeometry.isStroked(index)) {
                val width = KkFormGeometry.strokeWidth(index, radius)
                drawPath(path, Kk.Ink, style = kkStroke(width + outline))
                drawPath(path, Kk.Bone, style = kkStroke(width))
            } else {
                drawPath(path, Kk.Ink, style = kkStroke(outline))
                drawPath(path, Kk.Bone)
            }
        }
    }
    if (shape == CoreShape.RING) {
        drawCircle(Kk.Ink, radius * 0.24f + max(5f * k, density * 2.5f), center)
        drawCircle(Kk.Bone, radius * 0.24f, center)
    }
}

/** The form index of a character: [KkFormGeometry] and [KkIcon.Forms] follow the character order. */
internal fun CoreShape.formIndex(): Int = ordinal

private fun DrawScope.drawEnemies(
    text: CanvasTextMeasurer,
    time: Float,
    center: Offset,
    k: Float,
    spin: Float,
    accent: Color,
    far: Boolean,
    motion: HomeMenuMotion,
) {
    val roles = text.roles
    val size = max(34f * k, density * 20f)
    val gridScale = size / 24f
    for (index in EnemyAngles.indices) {
        val angle = (EnemyAngles[index] - 20f) * PI.toFloat() / 180f
        val planeY = -cos(angle)
        if ((planeY < 0f) != far) continue
        val position = Offset(center.x + 260f * k * sin(angle), center.y + 260f * k * planeY * TILT)
        // Seconds since the Core last passed this enemy.
        val since = ((spin - EnemyAngles[index] + 360f) % 360f) / 360f * HOME_ORBIT_SECONDS
        val respawn = HOME_ORBIT_SECONDS * 0.35f
        val (scale, alpha, flash) = when {
            since < HIT_FLASH -> Triple(kkLerp(1.35f, 0.3f, since / HIT_FLASH), 1f - since / HIT_FLASH, true)
            since < respawn -> Triple(0f, 0f, false)
            since < respawn + 0.45f -> {
                val p = KkEase.Out.transform((since - respawn) / 0.45f)
                Triple(kkLerp(0.4f, 1f, p), p, false)
            }
            else -> Triple(1f, 1f, false)
        }
        if (alpha > 0.01f) {
            val path = motion.enemies[index]
            withTransform({
                translate(position.x - size * 0.5f * scale, position.y - size * 0.5f * scale)
                scale(gridScale * scale, gridScale * scale, Offset.Zero)
            }) {
                drawPath(path, if (flash) Kk.Bone else Kk.Ink1, alpha)
                drawPath(path, if (flash) Kk.Bone else roles.threat, alpha, motion.enemyStroke)
                if (!flash) drawKkThreatHatch(path, roles)
            }
        }
        if (since < 0.8f) {
            val p = KkEase.Out.transform(since / 0.8f)
            drawCircle(accent.copy(alpha = 1f - p), 40f * k * kkLerp(0.2f, 1.9f, p), position, style = kkStroke(max(3f * k, density * 1.5f)))
        }
        if (since < 1.2f) {
            val p = KkEase.Out.transform(since / 1.2f)
            drawShard(motion.shardPath, position, 0, p, k, roles.threat)
            drawShard(motion.shardPath, position, 1, p, k, Kk.Bone)
            drawShard(motion.shardPath, position, 2, p, k, accent)
        }
    }
}

private const val HIT_FLASH = 0.22f
private val ShardTravel = floatArrayOf(46f, -40f, 220f, -38f, 30f, -160f, 20f, 44f, 90f)
private val ShardSizes = floatArrayOf(12f, 10f, 9f)

private fun DrawScope.drawShard(path: Path, origin: Offset, index: Int, progress: Float, k: Float, color: Color) {
    val base = index * 3
    val size = ShardSizes[index] * max(k, 0.6f)
    val x = origin.x + ShardTravel[base] * k * progress
    val y = origin.y + ShardTravel[base + 1] * k * progress
    path.rewind()
    val h = size * 0.5f
    when (index) {
        0 -> { path.moveTo(x, y - h); path.lineTo(x + h, y + h); path.lineTo(x - h, y + h) }
        1 -> { path.moveTo(x - h, y - h); path.lineTo(x + h, y - h * 0.4f); path.lineTo(x - h * 0.6f, y + h) }
        else -> { path.moveTo(x, y - h); path.lineTo(x + h, y); path.lineTo(x, y + h); path.lineTo(x - h, y) }
    }
    path.close()
    rotate(ShardTravel[base + 2] * progress, Offset(x, y)) {
        drawPath(path, color, alpha = 1f - progress)
    }
}
