// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sin
import kinetickk.ball.gameplay.interaction.canvas.*

private const val RADIANS_TO_DEGREES = CanvasMath.RAD_TO_DEG

/** Weapons in the world read as the player's: bone bodies with you-color accents. */
internal fun DrawScope.drawWeapon(engine: GameplayRenderModel, core: Offset, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val you = roles.you
    val motionAngle = if (engine.speed > 1f) atan2(engine.velocityY, engine.velocityX) else 0f
    when (engine.weapon) {
        WeaponId.FLUX_WAKE -> Unit
        WeaponId.MORNINGSTAR -> {
            val ballX = engine.interpolated(engine.previousMorningstarX, engine.morningstarX)
            val ballY = engine.interpolated(engine.previousMorningstarY, engine.morningstarY)
            val ball = world(engine, ballX, ballY, shakeX, shakeY)
            drawLine(you.copy(alpha = 0.16f), core, ball, 6f)
            drawLine(Kk.Bone.copy(alpha = 0.6f), core, ball, 1.5f, pathEffect = WorldStrokes.dashEffect)
            drawMorningstarBall(ball, 20f, engine.morningstarAngle, you)
            drawSecondMorningstar(engine, core, ball, roles)
            val agonyRank = engine.relicRank(RelicId.AGONY_SCEPTER)
            if (agonyRank > 0) {
                val agony = world(engine, engine.renderCoreX * 2f - ballX, engine.renderCoreY * 2f - ballY, shakeX, shakeY)
                drawLine(Kk.ASovereign.copy(alpha = 0.16f), core, agony, 6f)
                drawLine(Kk.Bone.copy(alpha = 0.6f), core, agony, 1.5f, pathEffect = WorldStrokes.dashEffect)
                drawMorningstarBall(agony, 17f + agonyRank, -engine.morningstarAngle, Kk.ASovereign)
            }
        }
        WeaponId.PHASE_LATTICE -> drawPhaseLattice(engine, core, roles)
        WeaponId.NULL_LANCE -> {
            // A lance with a square block end aligned to its shaft (a turned square would read as a head).
            val tip = polar(core, 48f, motionAngle)
            drawLine(you.copy(alpha = 0.2f), core, polar(core, 115f, motionAngle), 7f)
            drawLine(Kk.Bone, core, tip, 2f)
            rotate(motionAngle * RADIANS_TO_DEGREES, tip) {
                drawRect(you, Offset(tip.x - 4f, tip.y - 4f), Size(8f, 8f))
            }
        }
        WeaponId.GRAVITY_MINES -> drawCircle(you.copy(alpha = 0.3f), 29f, core, style = WorldStrokes.dashedThin)
        WeaponId.ION_SWARM -> for (index in engine.weaponOrbitals.indices) {
            val orbital = engine.weaponOrbitals[index]
            val point = world(engine, engine.interpolated(orbital.previousX, orbital.x), engine.interpolated(orbital.previousY, orbital.y), shakeX, shakeY)
            drawCircle(you.copy(alpha = 0.14f), 12f, point)
            rotate(engine.renderElapsed * 115f + orbital.index * 30f, point) {
                drawRect(you, Offset(point.x - 5f, point.y - 5f), Size(10f, 10f))
            }
        }
        WeaponId.RIFT_BLADES -> for (index in engine.weaponOrbitals.indices) {
            val orbital = engine.weaponOrbitals[index]
            val point = world(engine, engine.interpolated(orbital.previousX, orbital.x), engine.interpolated(orbital.previousY, orbital.y), shakeX, shakeY)
            drawLine(Kk.Bone.copy(alpha = 0.2f), core, point, 1f, pathEffect = WorldStrokes.dashEffect)
            rotate(engine.renderElapsed * 240f + orbital.index * 45f, point) {
                drawRect(Kk.Bone, Offset(point.x - 11f, point.y - 11f), Size(22f, 22f))
                drawRect(you, Offset(point.x - 11f, point.y - 11f), Size(22f, 22f), style = kkStroke(2f))
            }
        }
        WeaponId.ARC_COIL -> {
            val pulse = (sin(engine.renderElapsed * 9f) + 1f) * 0.5f
            drawCircle(you.copy(alpha = 0.3f), 31f + pulse * 7f, core, style = kkStroke(2f))
            drawCircle(Kk.Bone.copy(alpha = 0.4f), 22f - pulse * 4f, core, style = WorldStrokes.dashedHair)
        }
        WeaponId.QUASAR_CANNON -> {
            val rear = polar(core, 19f, motionAngle + kotlin.math.PI.toFloat())
            val tip = polar(core, 55f, motionAngle)
            drawLine(you.copy(alpha = 0.26f), rear, tip, 14f)
            drawLine(Kk.Bone, core, tip, 3f)
            drawCircle(you, 7f, tip)
        }
        WeaponId.ENTROPY_FIELD -> drawEntropyField(engine, core, roles)
        WeaponId.SINGULARITY_SPEAR -> {
            drawCircle(Kk.Bone.copy(alpha = 0.25f), 36f, core, style = kkStroke(2f))
            if (engine.weaponBeamTime > 0f) {
                val start = world(engine, engine.weaponBeamStartX, engine.weaponBeamStartY, shakeX, shakeY)
                val end = world(engine, engine.weaponBeamEndX, engine.weaponBeamEndY, shakeX, shakeY)
                val alpha = (engine.weaponBeamTime / 0.18f).coerceIn(0f, 1f)
                drawLine(you.copy(alpha = alpha * 0.22f), start, end, 19f)
                drawLine(Kk.Bone.copy(alpha = alpha), start, end, 4f)
                drawLine(you.copy(alpha = alpha), start, end, 1.3f)
            }
        }
        WeaponId.PRISM_RELAY -> {
            val pulse = (sin(engine.renderElapsed * 7f) + 1f) * 0.5f
            val outer = 19f + pulse * 3f
            rotate(engine.renderElapsed * 46f, core) {
                drawRect(you, Offset(core.x - outer, core.y - outer), Size(outer * 2f, outer * 2f), style = kkStroke(2f))
            }
            rotate(-engine.renderElapsed * 69f, core) {
                drawRect(Kk.Bone.copy(alpha = 0.8f), Offset(core.x - 10f, core.y - 10f), Size(20f, 20f), style = kkStroke(1.5f))
            }
        }
        // The 0.4.0 additions (WorldWeaponRenderer).
        WeaponId.GYRE_DISC, WeaponId.SCATTER_BLOOM, WeaponId.TETHER_ARC, WeaponId.SENTRY_GRID,
        WeaponId.HUNTER_SALVO, WeaponId.CONCUSSION_ENGINE, WeaponId.GLACIER_BELL, WeaponId.EVENT_LENS,
        WeaponId.SOLAR_SWEEP, WeaponId.AEGIS_SHARDS, WeaponId.HOLLOW_RIFT, WeaponId.RESONANCE_DRUM ->
            drawAddedWeapon(engine, core, motionAngle, shakeX, shakeY, roles)
    }
}

/**
 * The Morningstar head: a bone disc with a you-color rim and eight short blunt studs (square
 * ends, never pointed spikes) that turn with the flail, and an ink hub.
 */
private fun DrawScope.drawMorningstarBall(center: Offset, radius: Float, angle: Float, accent: Color) {
    drawCircle(accent.copy(alpha = 0.12f), radius + 12f, center)
    val studWidth = radius * 0.28f
    val studLength = radius * 0.3f
    withTransform({
        translate(center.x, center.y)
        rotate(angle * RADIANS_TO_DEGREES, Offset.Zero)
    }) {
        for (stud in 0 until MORNINGSTAR_STUDS) {
            rotate(stud * (360f / MORNINGSTAR_STUDS), Offset.Zero) {
                drawRect(accent, Offset(-studWidth * 0.5f, -radius - studLength + 1f), Size(studWidth, studLength))
            }
        }
    }
    drawCircle(Kk.Bone, radius, center)
    drawCircle(accent, radius - 1.25f, center, style = kkStroke(2.5f))
    drawCircle(Kk.Ink, radius * 0.22f, center)
}

private const val MORNINGSTAR_STUDS = 8

/** Gravity mines: a you-color core with a dashed ring that tightens as the fuse runs down. */
internal fun DrawScope.drawWeaponNodes(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    for (index in engine.weaponNodes.indices) {
        val node = engine.weaponNodes[index]
        if (node.type != WeaponNodeType.GRAVITY_MINE) continue
        val point = world(engine, node.x, node.y, shakeX, shakeY)
        if (!isOnScreen(point, node.radius)) continue
        val ratio = (node.life / node.maxLife).coerceIn(0f, 1f)
        val fuse = 1f - ratio
        drawCircle(roles.you.copy(alpha = fuse * fuse * 0.1f), node.radius, point)
        drawCircle(roles.you.copy(alpha = 0.5f), max(12f, node.radius * ratio), point, style = WorldStrokes.dashedThin)
        drawCircle(roles.you.copy(alpha = 0.3f), 13f, point)
        drawCircle(roles.you, 8f, point)
    }
}
