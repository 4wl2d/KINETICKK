// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.ParticleDensity
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.interaction.canvas.look.*

/** Flux Wake's burning half-width and the fade after a trail point's life (mirrors of the simulation's). */
private const val FLUX_WAKE_WIDTH = 20f
private const val TRAIL_FADE_SECONDS = 0.45f

/** Dash afterimages: rings the size of the Core in the you color (`HUD.dc.html` dash ghosts). */
internal fun DrawScope.drawMotionEchoes(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    for (index in visualFx.motionEchoes.indices) {
        val echo = visualFx.motionEchoes[index]
        val center = world(engine, echo.x, echo.y, shakeX, shakeY)
        if (!isOnScreen(center, 60f)) continue
        val life = (echo.life / echo.maxLife).coerceIn(0f, 1f)
        drawCircle(roles.you.copy(alpha = life * life * echo.intensity * 0.7f), 18f, center, style = kkStroke(KkStrokeWidth.Icon))
    }
}

/**
 * Expanding rings for kills (bone), dash (you), ram impacts (you, 3 px), damage taken (threat),
 * Breaks and reactions (element rings), Apex strikes (gold) and quakes (a jagged element ring).
 */
internal fun DrawScope.drawShockwaves(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    for (index in visualFx.shockwaves.indices) {
        val wave = visualFx.shockwaves[index]
        val center = world(engine, wave.x, wave.y, shakeX, shakeY)
        if (!isOnScreen(center, wave.maxRadius)) continue
        val life = (wave.life / wave.maxLife).coerceIn(0f, 1f)
        val radius = max(2f, wave.maxRadius * KkEase.Out.transform(1f - life))
        val color = fxColor(wave.colorIndex, roles)
        drawCircle(color.copy(alpha = life * 0.06f), radius * 0.72f, center)
        if (isQuakeColor(wave.colorIndex)) {
            // The quake's vertices come from its spot, so each one keeps its own jagged outline.
            drawQuakeRing(center, radius, (wave.x.toRawBits() * 31) xor wave.y.toRawBits(), color, life * 0.8f, 3f)
        } else {
            drawCircle(color.copy(alpha = life * 0.8f), radius, center, style = kkStroke(if (wave.colorIndex == 3) 3f else 2f))
        }
    }
}

/**
 * Impact accents drawn over the enemies: a bone flash where an ordinary enemy died and, for ram
 * impacts and elite kills (the you-color rings), a bone slash across the target (−27.5°).
 */
internal fun DrawScope.drawImpactMarks(engine: GameplayRenderModel, visualFx: VisualFxProjection, shakeX: Float, shakeY: Float) {
    for (index in visualFx.shockwaves.indices) {
        val wave = visualFx.shockwaves[index]
        if (wave.colorIndex != 1 && wave.colorIndex != 3) continue
        val progress = 1f - (wave.life / wave.maxLife).coerceIn(0f, 1f)
        if (progress >= 0.45f) continue
        val center = world(engine, wave.x, wave.y, shakeX, shakeY)
        if (!isOnScreen(center, wave.maxRadius)) continue
        if (wave.colorIndex == 1 && progress < 0.22f) {
            drawCircle(Kk.Bone.copy(alpha = (1f - progress / 0.22f) * 0.85f), wave.maxRadius * 0.34f * (1f + progress), center)
        }
        if (wave.colorIndex == 3) {
            val length = wave.maxRadius * 1.3f * (1.1f + progress * 0.2f)
            rotate(KkShape.Slash, center) {
                drawRect(Kk.Bone.copy(alpha = 1f - progress / 0.45f), Offset(center.x - length * 0.5f, center.y - 2.5f), Size(length, 5f))
            }
        }
    }
}

/**
 * The Core's path history in the you color (a wider band where Flux Wake cuts). The ribbon that
 * trails the Core is [drawCoreRibbon]'s.
 */
internal fun DrawScope.drawTrail(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val you = roles.you
    val wake = engine.weapon == WeaponId.FLUX_WAKE
    // The Flux Wake band is as wide as the strip it burns: twice as wide from the first form, wider with Agony.
    val band = 2f * (FLUX_WAKE_WIDTH * (if (engine.weaponLevel >= 3) 2f else 1f) + 4f * engine.relicRank(RelicId.AGONY_SCEPTER))
    val lowParticles = engine.settings.particleDensity == ParticleDensity.LOW
    var hasPrevious = false
    var previous = Offset.Zero
    var previousLife = 0f
    for (index in engine.trail.indices) {
        val point = engine.trail[index]
        val location = world(engine, point.x, point.y, shakeX, shakeY)
        // A point burns for its life, then fades out over TRAIL_FADE_SECONDS.
        val life = (1f - point.age / (point.life + TRAIL_FADE_SECONDS)).coerceIn(0f, 1f)
        if (hasPrevious) {
            val segmentLife = min(previousLife, life)
            if (segmentLife > 0f && (isOnScreen(previous, 50f) || isOnScreen(location, 50f))) {
                if (wake) {
                    drawLine(you.copy(alpha = segmentLife * 0.12f), previous, location, band * point.scale)
                } else if (!lowParticles) {
                    drawLine(you.copy(alpha = segmentLife * 0.05f), previous, location, 10f)
                }
                drawLine(you.copy(alpha = segmentLife * if (wake) 0.6f else 0.32f), previous, location, 2f)
            }
        }
        hasPrevious = true
        previous = location
        previousLife = life
    }
}
