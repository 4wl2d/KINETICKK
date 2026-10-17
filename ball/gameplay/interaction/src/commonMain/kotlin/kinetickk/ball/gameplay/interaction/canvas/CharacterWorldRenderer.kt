// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The form index of a character: [KkFormGeometry] and [KkIcon.Forms] follow the character order. */
internal fun CoreShape.formIndex(): Int = ordinal

/** Element hue indices (status element order) the character marks borrow. */
private const val SONIC_HUE = 5
private const val VOID_HUE = 6
private const val GRAVITIC_HUE = 3

/**
 * Character fields under the action (world units): the Ring's halo band, the Dipole's polarity ring,
 * the Pentagon's beat pulse, the Crescent's void scars and the Tesseract's lattice.
 */
internal fun DrawScope.drawCharacterField(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val ability = engine.characterAbility
    val you = roles.you
    when (engine.coreShape) {
        CoreShape.RING -> {
            val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
            val active = engine.speed >= 40f
            val radius = ability.ringRadius
            drawCircle(you.copy(alpha = if (active) 0.07f else 0.02f), radius, core, style = kkStroke(36f))
            drawCircle(you.copy(alpha = if (active) 0.45f else 0.18f), max(1f, radius - 18f), core, style = kkStroke(1f))
            drawCircle(you.copy(alpha = if (active) 0.6f else 0.2f), radius + 18f, core, style = kkStroke(1.5f))
            val burst = ability.haloBurstFlash
            if (burst > 0f) drawCircle(Kk.Bone.copy(alpha = burst * 0.7f), radius + 24f * (1f - burst), core, style = kkStroke(4f * burst + 1f))
        }
        CoreShape.DIPOLE -> {
            val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
            val repel = ability.polarity == 1
            val hue = if (repel) roles.heat else roles.element(GRAVITIC_HUE)
            val radius = if (repel) 220f else 300f
            drawCircle(hue.copy(alpha = 0.05f), radius, core)
            // Dashes drift outward while repelling and inward while attracting.
            val drift = ((engine.renderElapsed * 0.9f) % 1f).let { if (repel) it else 1f - it }
            drawCircle(hue.copy(alpha = 0.4f), radius, core, style = WorldStrokes.dashedMedium)
            drawCircle(hue.copy(alpha = 0.22f * (1f - drift)), radius * (0.35f + 0.65f * drift), core, style = kkStroke(1.5f))
            val flash = ability.polarityFlash
            if (flash > 0f) drawCircle(hue.copy(alpha = flash * 0.6f), radius * (1.1f - 0.1f * flash), core, style = kkStroke(3f))
        }
        CoreShape.PENTAGON -> {
            val period = ability.beatPeriod
            if (period > 0f) {
                val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
                val elapsed = engine.renderElapsed
                val phase = (elapsed % period) / period
                val hue = roles.element(SONIC_HUE)
                // Each beat sends out one visible pulse; the window around it is lit.
                drawCircle(hue.copy(alpha = 0.55f * (1f - phase)), 30f + 70f * phase, core, style = kkStroke(2.5f))
                val offset = min(elapsed % period, period - elapsed % period)
                if (offset <= ability.beatWindow) drawCircle(hue.copy(alpha = 0.22f), 30f, core, style = kkStroke(8f))
            }
        }
        CoreShape.CRESCENT -> {
            val scars = ability.scars
            val void = roles.element(VOID_HUE)
            for (index in scars.indices) {
                val scar = scars[index]
                val start = world(engine, scar.x0, scar.y0, shakeX, shakeY)
                val end = world(engine, scar.x1, scar.y1, shakeX, shakeY)
                if (scar.fuse < 1f) {
                    drawLine(void.copy(alpha = 0.25f + 0.45f * scar.fuse), start, end, 6f + 10f * scar.fuse)
                    drawLine(Kk.Ink.copy(alpha = 0.7f), start, end, 2f)
                } else {
                    val fade = (1f - scar.fade / 0.3f).coerceIn(0f, 1f)
                    drawLine(void.copy(alpha = 0.6f * fade), start, end, 22f * fade + 2f)
                    drawLine(Kk.Bone.copy(alpha = fade), start, end, 3f)
                }
            }
        }
        CoreShape.TESSERACT -> {
            val lattice = ability.lattice
            for (index in lattice.indices) {
                val point = lattice[index]
                val position = world(engine, point.x, point.y, shakeX, shakeY)
                if (index > 0) {
                    val previous = lattice[index - 1]
                    drawLine(you.copy(alpha = 0.5f), world(engine, previous.x, previous.y, shakeX, shakeY), position, 1.5f)
                }
                rotate(45f, position) {
                    drawRect(you, Offset(position.x - 7f, position.y - 7f), Size(14f, 14f), style = kkStroke(1.5f))
                }
            }
            if (lattice.size >= 2) {
                // The pending closing edge back to the first node.
                val first = lattice[0]
                val last = lattice[lattice.size - 1]
                drawLine(you.copy(alpha = 0.18f), world(engine, last.x, last.y, shakeX, shakeY),
                    world(engine, first.x, first.y, shakeX, shakeY), 1f)
            }
        }
        else -> Unit
    }
}

/** Character actors over the enemies: the Spiral's echo ghost and the Hive's drones. */
internal fun DrawScope.drawCharacterActors(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val ability = engine.characterAbility
    if (ability.ghostActive) {
        val x = engine.interpolated(ability.previousGhostX, ability.ghostX)
        val y = engine.interpolated(ability.previousGhostY, ability.ghostY)
        val center = world(engine, x, y, shakeX, shakeY)
        val radius = engine.coreRadius
        drawCircle(roles.you.copy(alpha = 0.12f), radius * 1.9f, center)
        val path = KkFormPaths.of(KkFormGeometry.SPIRAL, radius)
        val stroke = kkStroke(KkFormGeometry.strokeWidth(KkFormGeometry.SPIRAL, radius))
        rotate(-engine.renderElapsed * 140f, center) {
            translate(center.x, center.y) { drawPath(path, Kk.Bone.copy(alpha = 0.5f), style = stroke) }
        }
    }
    val drones = ability.drones
    for (index in drones.indices) {
        val drone = drones[index]
        val center = world(engine, engine.interpolated(drone.previousX, drone.x), engine.interpolated(drone.previousY, drone.y),
            shakeX, shakeY)
        val path = KkFormPaths.of(KkFormGeometry.HEXAGON, if (drone.darting) 7f else 5.5f)
        translate(center.x, center.y) {
            drawPath(path, Kk.Ink, style = kkStroke(3f))
            drawPath(path, if (drone.darting) roles.you else Kk.Bone)
        }
    }
}

/**
 * The Core silhouette from [KkFormGeometry] with its ink rim, then the character's marks around it
 * (inside the HUD's Core halo, radius 38): facets, Edge, Stellar Mass glow, Blink charges and the streak.
 */
internal fun DrawScope.drawCoreForm(engine: GameplayRenderModel, center: Offset, fill: Color, roles: KkRolePalette) {
    val radius = engine.coreRadius
    val shape = engine.coreShape
    val ability = engine.characterAbility
    if (shape == CoreShape.STAR && ability.stellarMass > 0) {
        val glow = ability.stellarMass / 24f
        drawCircle(roles.heat.copy(alpha = 0.08f + 0.18f * glow), radius * (1.6f + glow), center)
    }
    if (ability.novaFlash > 0f) {
        val flash = ability.novaFlash
        drawCircle(roles.heat.copy(alpha = flash * 0.5f), radius + (1f - flash) * 140f, center, style = kkStroke(6f * flash + 1f))
    }
    val index = shape.formIndex()
    val spin = when (shape) {
        CoreShape.STAR -> engine.renderElapsed * 40f
        CoreShape.SPIRAL -> -engine.renderElapsed * 120f
        CoreShape.HEXAGON -> engine.renderElapsed * 18f
        else -> 0f
    }
    val path = KkFormPaths.of(index, radius)
    rotate(spin % 360f, center) {
        translate(center.x, center.y) {
            if (KkFormGeometry.isStroked(index)) {
                val width = KkFormGeometry.strokeWidth(index, radius)
                drawPath(path, Kk.Ink, style = kkStroke(width + 6f))
                drawPath(path, if (shape == CoreShape.TESSERACT) fill.copy(alpha = 0.3f) else fill, style = kkStroke(width))
            } else {
                drawPath(path, Kk.Ink, style = kkStroke(6f))
                drawPath(path, fill)
            }
        }
    }
    if (shape == CoreShape.RING) {
        drawCircle(Kk.Ink, radius * 0.24f + 2.5f, center)
        drawCircle(fill, radius * 0.24f, center)
    }
    if (shape == CoreShape.TESSERACT) drawTesseractCore(center, engine.renderElapsed, fill, radius)
    when (shape) {
        CoreShape.PRISM -> drawFacetRing(ability.facets, ability.facetProgress, center, engine.renderElapsed, roles)
        CoreShape.DIAMOND -> drawPips(ability.edge, 5, center, 30f, Kk.Bone, roles.you, diamond = true)
        CoreShape.CRESCENT -> drawPips(ability.blinkCharges, 2, center, 30f, roles.element(VOID_HUE), Kk.Mute2, diamond = false)
        CoreShape.PENTAGON -> drawPips(ability.beatStreak, 5, center, 32f, roles.element(SONIC_HUE), Kk.Mute2, diamond = false)
        else -> Unit
    }
}

/** Four facets orbiting the Core: intact facets filled, broken ones outlined, the next one filling in. */
private fun DrawScope.drawFacetRing(facets: Int, progress: Float, center: Offset, elapsed: Float, roles: KkRolePalette) {
    val base = elapsed * 0.6f
    for (index in 0 until 4) {
        val angle = base + index * 1.5707964f
        val point = Offset(center.x + cos(angle) * 27f, center.y + sin(angle) * 27f)
        val intact = index < facets
        rotate(45f + angle * 57.29578f, point) {
            val half = if (intact) 4.5f else 3.5f
            drawRect(Kk.Ink, Offset(point.x - half - 1.5f, point.y - half - 1.5f), Size(half * 2f + 3f, half * 2f + 3f))
            if (intact) {
                drawRect(roles.shield, Offset(point.x - half, point.y - half), Size(half * 2f, half * 2f))
            } else {
                drawRect(roles.shield.copy(alpha = 0.25f + 0.6f * if (index == facets) progress else 0f),
                    Offset(point.x - half, point.y - half), Size(half * 2f, half * 2f), style = kkStroke(1.5f))
            }
        }
    }
}

/** [count] of [capacity] pips on an arc above the Core. */
private fun DrawScope.drawPips(
    count: Int,
    capacity: Int,
    center: Offset,
    reach: Float,
    lit: Color,
    unlit: Color,
    diamond: Boolean,
) {
    val spread = 0.42f
    val start = -1.5707964f - spread * (capacity - 1) * 0.5f
    for (index in 0 until capacity) {
        val angle = start + index * spread
        val point = Offset(center.x + cos(angle) * reach, center.y + sin(angle) * reach)
        val color = if (index < count) lit else unlit
        if (diamond) {
            rotate(45f, point) {
                drawRect(Kk.Ink, Offset(point.x - 4f, point.y - 4f), Size(8f, 8f))
                drawRect(color, Offset(point.x - 2.6f, point.y - 2.6f), Size(5.2f, 5.2f))
            }
        } else {
            drawCircle(Kk.Ink, 4f, point)
            drawCircle(color, 2.6f, point)
        }
    }
}
