// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityProjection
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The weapons added in 0.4.0 and every weapon entity (discs, sentries, lenses and wells, rifts, shards,
 * missiles, rings, tethers and solar beams). Same language as the original roster: bone bodies with
 * you-color accents; elemental effects take their element's hue. Moving entities are drawn between their
 * last two fixed-step states; spins and pulses follow renderElapsed. Nothing here allocates per frame.
 */

private const val RADIANS_TO_DEGREES = 57.29578f
private const val TAU = (PI * 2.0).toFloat()

/** Mirrors of the simulation's geometry the world draws (the nucleus keeps the authoritative values). */
private const val PHASE_LATTICE_CENTER = 110f
private const val PHASE_LATTICE_HALF_WIDTH = 42f
private const val PHASE_LATTICE_OUTER_OFFSET = 100f
private const val MORNINGSTAR_SECOND_ORBIT = 0.72f
private const val MORNINGSTAR_SECOND_SIZE = 0.45f

/** Ring variants (the entity's phase): a bell toll, a drum pulse, a concussion shockwave, a passive bell pulse. */
private const val RING_TOLL = 0
private const val RING_DRUM = 1
private const val RING_CONCUSSION = 2

/** Rift phases: laid along a running Dash, armed, burst once (a second burst follows), a spear's scar, spent. */
private const val RIFT_GROWING = 0
private const val RIFT_SPENT = 4

/** A Gyre disc on its way back. */
private const val DISC_RETURNING = 1

private fun KkRolePalette.element(element: StatusElement): Color = elements.getOrElse(element.ordinal) { you }

/** Phase Lattice: the ring band (on the Halo for the Ring), wider at the first form, an outer ring at the second. */
internal fun DrawScope.drawPhaseLattice(engine: GameplayRenderModel, core: Offset, roles: KkRolePalette) {
    val center = if (engine.coreShape == CoreShape.RING) engine.characterAbility.ringRadius else PHASE_LATTICE_CENTER
    val half = PHASE_LATTICE_HALF_WIDTH * if (engine.weaponLevel >= 3) 1.35f else 1f
    val pulse = (sin(engine.renderElapsed * 4f) + 1f) * 0.5f
    val cryo = roles.element(StatusElement.CRYO)
    drawCircle(cryo.copy(alpha = 0.05f), center, core, style = kkStroke(half * 2f))
    drawCircle(roles.you.copy(alpha = 0.4f), center - half * 0.5f + pulse * half, core, style = kkStroke(1.5f))
    drawCircle(Kk.Bone.copy(alpha = 0.22f), center + half, core, style = WorldStrokes.dashedHair)
    if (engine.weaponLevel >= 6) {
        drawCircle(cryo.copy(alpha = 0.28f), center + PHASE_LATTICE_OUTER_OFFSET, core, style = WorldStrokes.dashedThin)
    }
}

/** Entropy Field: radius (170 + 8 per level) × 1.35 from the first form, +18 per Agony rank. */
internal fun DrawScope.drawEntropyField(engine: GameplayRenderModel, core: Offset, roles: KkRolePalette) {
    val radius = (170f + 8f * engine.weaponLevel) * (if (engine.weaponLevel >= 3) 1.35f else 1f) +
        18f * engine.relicRank(RelicId.AGONY_SCEPTER)
    val entropy = roles.element(StatusElement.ENTROPY)
    drawCircle(entropy.copy(alpha = 0.035f), radius, core)
    drawCircle(roles.you.copy(alpha = 0.3f), radius, core, style = WorldStrokes.dashedThin)
}

/** Morningstar's second form: a smaller star opposite the first on a shorter chain. */
internal fun DrawScope.drawSecondMorningstar(engine: GameplayRenderModel, core: Offset, ball: Offset, roles: KkRolePalette) {
    if (engine.weaponLevel < 6) return
    val second = Offset(core.x - (ball.x - core.x) * MORNINGSTAR_SECOND_ORBIT, core.y - (ball.y - core.y) * MORNINGSTAR_SECOND_ORBIT)
    drawLine(roles.you.copy(alpha = 0.12f), core, second, 4f)
    drawLine(Kk.Bone.copy(alpha = 0.5f), core, second, 1f, pathEffect = WorldStrokes.dashEffect)
    val radius = 20f * MORNINGSTAR_SECOND_SIZE + 3f
    drawCircle(roles.you.copy(alpha = 0.12f), radius + 8f, second)
    drawCircle(Kk.Bone, radius, second)
    drawCircle(roles.you, radius - 1f, second, style = kkStroke(2f))
}

/** The Core-side marks of the twelve 0.4.0 weapons (their entities are drawn by [drawWeaponEntities]). */
internal fun DrawScope.drawAddedWeapon(
    engine: GameplayRenderModel,
    core: Offset,
    motionAngle: Float,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val you = roles.you
    val time = engine.renderElapsed
    when (engine.weapon) {
        WeaponId.GYRE_DISC -> {
            // The launcher rail: a short bone slot along the aim.
            val back = polar(core, 30f, motionAngle + PI.toFloat())
            val front = polar(core, 30f, motionAngle)
            drawLine(you.copy(alpha = 0.18f), back, front, 8f)
            drawLine(Kk.Bone.copy(alpha = 0.7f), back, front, 1.5f, pathEffect = WorldStrokes.dashEffect)
        }
        WeaponId.SCATTER_BLOOM -> {
            // The 60° cone a Dash fires, as two short you-color edges ahead of the Core.
            drawLine(you.copy(alpha = 0.45f), polar(core, 30f, motionAngle - 0.52f), polar(core, 58f, motionAngle - 0.52f), 2f)
            drawLine(you.copy(alpha = 0.45f), polar(core, 30f, motionAngle + 0.52f), polar(core, 58f, motionAngle + 0.52f), 2f)
        }
        WeaponId.TETHER_ARC -> drawCircle(roles.element(StatusElement.ION).copy(alpha = 0.45f), 30f, core, style = WorldStrokes.dashedThin)
        WeaponId.SENTRY_GRID -> rotate(time * 30f, core) {
            drawRect(you.copy(alpha = 0.35f), Offset(core.x - 30f, core.y - 30f), Size(60f, 60f), style = WorldStrokes.dashedHair)
        }
        WeaponId.HUNTER_SALVO -> for (pod in 0 until 3) {
            // Three missile pods turning slowly around the Core.
            val point = polar(core, 32f, time * 1.3f + pod * TAU / 3f)
            drawCircle(roles.element(StatusElement.ENTROPY).copy(alpha = 0.25f), 7f, point)
            drawCircle(Kk.Bone, 3.5f, point)
        }
        WeaponId.CONCUSSION_ENGINE -> {
            // The ram plate: a heavy arc on the Core's front.
            val degrees = motionAngle * RADIANS_TO_DEGREES
            drawArc(you.copy(alpha = 0.7f), degrees - 50f, 100f, false, Offset(core.x - 32f, core.y - 32f), Size(64f, 64f), style = kkStroke(4f))
        }
        WeaponId.GLACIER_BELL -> {
            // The bell gathers while braking.
            val cryo = roles.element(StatusElement.CRYO)
            drawCircle(cryo.copy(alpha = if (engine.braking) 0.6f else 0.3f), 34f, core, style = WorldStrokes.dashedMedium)
            if (engine.braking) drawCircle(cryo.copy(alpha = 0.08f), 34f, core)
        }
        WeaponId.EVENT_LENS -> {
            val gravitic = roles.element(StatusElement.GRAVITIC)
            drawCircle(gravitic.copy(alpha = 0.35f), 26f, core, style = WorldStrokes.dashedHair)
            drawCircle(gravitic, 3f, polar(core, 26f, time * 2.4f))
        }
        WeaponId.SOLAR_SWEEP -> drawCircle(roles.element(StatusElement.THERMAL).copy(alpha = 0.3f), 30f, core, style = kkStroke(1.5f))
        WeaponId.AEGIS_SHARDS -> for (index in engine.weaponOrbitals.indices) {
            val orbital = engine.weaponOrbitals[index]
            val point = world(engine, engine.interpolated(orbital.previousX, orbital.x), engine.interpolated(orbital.previousY, orbital.y), shakeX, shakeY)
            drawShard(point, orbital.radius, time * 90f + orbital.index * 40f, roles.shield)
        }
        WeaponId.HOLLOW_RIFT -> {
            val void = roles.element(StatusElement.VOID)
            drawArc(void.copy(alpha = 0.6f), motionAngle * RADIANS_TO_DEGREES + 120f, 120f, false,
                Offset(core.x - 30f, core.y - 30f), Size(60f, 60f), style = kkStroke(2f))
        }
        WeaponId.RESONANCE_DRUM -> {
            val beat = (sin(time * 8.4f) + 1f) * 0.5f
            val sonic = roles.element(StatusElement.SONIC)
            drawCircle(sonic.copy(alpha = 0.4f), 26f + beat * 4f, core, style = kkStroke(1.5f))
            drawCircle(sonic.copy(alpha = 0.25f), 34f + beat * 6f, core, style = WorldStrokes.dashedHair)
        }
        else -> Unit
    }
}

/**
 * Weapon entities. [airborne] false draws what lies on the ground under pickups and enemies (wells, rifts,
 * rings); true draws what flies over them (discs, sentries, lenses, shards, missiles, tethers, beams).
 */
internal fun DrawScope.drawWeaponEntities(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette, airborne: Boolean) {
    val entities = engine.weaponEntities
    for (index in entities.indices) {
        val entity = entities[index]
        val ground = entity.kind == WeaponEntityKind.WELL || entity.kind == WeaponEntityKind.RIFT || entity.kind == WeaponEntityKind.RING
        if (ground == airborne) continue
        val center = world(engine, engine.interpolated(entity.previousX, entity.x), engine.interpolated(entity.previousY, entity.y), shakeX, shakeY)
        when (entity.kind) {
            WeaponEntityKind.DISC -> if (isOnScreen(center, entity.radius + 20f)) drawDisc(engine, entity, center, roles)
            WeaponEntityKind.SENTRY -> if (isOnScreen(center, 40f)) drawSentry(engine, entity, center, roles)
            WeaponEntityKind.LENS -> if (isOnScreen(center, 40f)) drawLens(engine, entity, center, roles)
            WeaponEntityKind.WELL -> if (isOnScreen(center, entity.radius)) drawWell(engine, entity, center, roles)
            WeaponEntityKind.RIFT -> drawRift(engine, entity, center, shakeX, shakeY, roles)
            WeaponEntityKind.SHARD -> if (isOnScreen(center, 30f)) {
                val heading = atan2(entity.y - entity.previousY, entity.x - entity.previousX)
                drawShard(center, entity.radius, heading * RADIANS_TO_DEGREES + 45f, roles.shield)
            }
            WeaponEntityKind.MISSILE -> if (isOnScreen(center, 40f)) drawMissile(entity, center, roles)
            WeaponEntityKind.RING -> if (isOnScreen(center, entity.radius)) drawWeaponRing(entity, center, roles)
            WeaponEntityKind.TETHER -> drawTether(engine, entity, center, shakeX, shakeY, roles)
            WeaponEntityKind.BEAM -> drawSolarBeam(engine, entity, shakeX, shakeY, roles)
        }
    }
}

/** A Gyre disc: a bone disc with a you rim and a notch that spins; brighter on the way back. */
private fun DrawScope.drawDisc(engine: GameplayRenderModel, disc: WeaponEntityProjection, center: Offset, roles: KkRolePalette) {
    val radius = disc.radius
    val returning = disc.phase == DISC_RETURNING
    drawCircle(roles.you.copy(alpha = if (returning) 0.22f else 0.12f), radius + 8f, center)
    drawCircle(Kk.Bone, radius, center)
    drawCircle(roles.you, radius - 1.25f, center, style = kkStroke(2.5f))
    rotate(engine.renderElapsed * 720f, center) {
        drawRect(Kk.Ink, Offset(center.x - radius * 0.15f, center.y - radius + 2f), Size(radius * 0.3f, radius * 0.6f))
    }
}

/** A sentry: a bone square on its corner with a you rim and a turning sight; it blinks out in its last second. */
private fun DrawScope.drawSentry(engine: GameplayRenderModel, sentry: WeaponEntityProjection, center: Offset, roles: KkRolePalette) {
    val fading = sentry.life < 1f && sin(engine.renderElapsed * 30f) < 0f
    val alpha = if (fading) 0.45f else 1f
    val half = sentry.radius
    rotate(45f, center) {
        drawRect(Kk.Bone.copy(alpha = alpha), Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f))
        drawRect(roles.you.copy(alpha = alpha), Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f), style = kkStroke(2f))
    }
    drawLine(roles.you.copy(alpha = 0.5f * alpha), center, polar(center, half + 10f, engine.renderElapsed * 3f), 1.5f)
    drawCircle(Kk.Ink, half * 0.3f, center)
}

/** An Event Lens in flight: a small gravitic lens with a turning inner mark. */
private fun DrawScope.drawLens(engine: GameplayRenderModel, lens: WeaponEntityProjection, center: Offset, roles: KkRolePalette) {
    val gravitic = roles.element(StatusElement.GRAVITIC)
    drawCircle(gravitic.copy(alpha = 0.18f), lens.radius + 6f, center)
    drawCircle(Kk.Bone, lens.radius, center)
    drawCircle(gravitic, lens.radius - 1f, center, style = kkStroke(2f))
    drawCircle(gravitic, 3f, polar(center, lens.radius * 0.5f, engine.renderElapsed * 6f))
}

/** A gravity well (a collapsed lens, a mine's last form): dashed rings turning inward, fading with its life. */
private fun DrawScope.drawWell(engine: GameplayRenderModel, well: WeaponEntityProjection, center: Offset, roles: KkRolePalette) {
    val gravitic = roles.element(StatusElement.GRAVITIC)
    val life = clamp(well.life / max(0.01f, well.maxLife), 0f, 1f)
    val alpha = min(1f, life * 2.5f)
    val inward = (engine.renderElapsed * 0.9f) % 1f
    drawCircle(gravitic.copy(alpha = 0.07f * alpha), well.radius, center)
    drawCircle(gravitic.copy(alpha = 0.5f * alpha), well.radius, center, style = WorldStrokes.dashedThin)
    drawCircle(gravitic.copy(alpha = 0.35f * alpha), well.radius * (1f - inward * 0.8f), center, style = WorldStrokes.dashedHair)
    drawCircle(Kk.Ink, 6f, center)
    drawCircle(gravitic.copy(alpha = alpha), 4f, center)
}

/** A rift along its segment: a dashed void seam while it waits, a bright cut when it bursts, then gone. */
private fun DrawScope.drawRift(
    engine: GameplayRenderModel,
    rift: WeaponEntityProjection,
    start: Offset,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val end = world(engine, engine.interpolated(rift.previousEndX, rift.endX), engine.interpolated(rift.previousEndY, rift.endY), shakeX, shakeY)
    if (!isOnScreen(start, rift.radius + 40f) && !isOnScreen(end, rift.radius + 40f)) return
    val void = roles.element(StatusElement.VOID)
    if (rift.phase == RIFT_SPENT) {
        val flash = clamp(rift.life / 0.25f, 0f, 1f)
        drawLine(void.copy(alpha = flash * 0.35f), start, end, rift.radius * 2f)
        drawLine(Kk.Bone.copy(alpha = flash), start, end, 3f)
        return
    }
    val pulse = (sin(engine.renderElapsed * if (rift.phase == RIFT_GROWING) 6f else 14f) + 1f) * 0.5f
    drawLine(void.copy(alpha = 0.08f + pulse * 0.06f), start, end, rift.radius * 2f)
    drawLine(void.copy(alpha = 0.7f), start, end, 2f, pathEffect = WorldStrokes.dashEffect)
}

/** An Aegis shard: a small shield-colored diamond with a bone core. */
private fun DrawScope.drawShard(center: Offset, radius: Float, degrees: Float, color: Color) {
    rotate(degrees, center) {
        drawRect(color.copy(alpha = 0.25f), Offset(center.x - radius - 3f, center.y - radius - 3f), Size(radius * 2f + 6f, radius * 2f + 6f))
        drawRect(Kk.Bone, Offset(center.x - radius * 0.7f, center.y - radius * 0.7f), Size(radius * 1.4f, radius * 1.4f))
        drawRect(color, Offset(center.x - radius * 0.7f, center.y - radius * 0.7f), Size(radius * 1.4f, radius * 1.4f), style = kkStroke(1.5f))
    }
}

/** A Hunter missile: a bone dart along its heading with an entropy exhaust. */
private fun DrawScope.drawMissile(missile: WeaponEntityProjection, center: Offset, roles: KkRolePalette) {
    val heading = atan2(missile.y - missile.previousY, missile.x - missile.previousX)
    val tail = polar(center, 16f, heading + PI.toFloat())
    drawLine(roles.element(StatusElement.ENTROPY).copy(alpha = 0.5f), tail, center, 4f)
    rotate(heading * RADIANS_TO_DEGREES, center) {
        drawRect(Kk.Bone, Offset(center.x - missile.radius, center.y - missile.radius * 0.45f), Size(missile.radius * 2f, missile.radius * 0.9f))
    }
    drawCircle(roles.you, 2.5f, polar(center, missile.radius, heading))
}

/** An expanding weapon ring: a bell toll (cryo), a drum pulse or concussion wave (sonic), a passive bell pulse. */
private fun DrawScope.drawWeaponRing(ring: WeaponEntityProjection, center: Offset, roles: KkRolePalette) {
    val life = clamp(ring.life / max(0.01f, ring.maxLife), 0f, 1f)
    val radius = max(4f, ring.radius * KkEase.Out.transform(1f - life))
    val color = when (ring.phase) {
        RING_DRUM, RING_CONCUSSION -> roles.element(StatusElement.SONIC)
        else -> roles.element(StatusElement.CRYO)
    }
    val strong = ring.phase == RING_TOLL || ring.phase == RING_DRUM || ring.phase == RING_CONCUSSION
    drawCircle(color.copy(alpha = life * 0.08f), radius, center)
    drawCircle(color.copy(alpha = life * if (strong) 0.85f else 0.45f), radius, center, style = kkStroke(if (strong) 3f else 1.5f))
}

/** A Tether Arc beam from the Core to its held target: a soft ion band under a jittering bone line. */
private fun DrawScope.drawTether(
    engine: GameplayRenderModel,
    tether: WeaponEntityProjection,
    target: Offset,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val core = world(engine, engine.interpolated(tether.previousEndX, tether.endX), engine.interpolated(tether.previousEndY, tether.endY), shakeX, shakeY)
    if (!isOnScreen(core, 40f) && !isOnScreen(target, 40f)) return
    val ion = roles.element(StatusElement.ION)
    val second = tether.phase == 1
    drawLine(ion.copy(alpha = if (second) 0.12f else 0.2f), core, target, if (second) 5f else 8f)
    val dx = target.x - core.x
    val dy = target.y - core.y
    val length = max(1f, kotlin.math.sqrt(dx * dx + dy * dy))
    val nx = -dy / length
    val ny = dx / length
    var previous = core
    for (segment in 0 until 5) {
        val t = (segment + 1f) / 5f
        val jitter = if (segment == 4) 0f else sin(segment * 3.7f + engine.renderElapsed * 33f) * 5f
        val next = Offset(core.x + dx * t + nx * jitter, core.y + dy * t + ny * jitter)
        drawLine(Kk.Bone.copy(alpha = if (second) 0.6f else 0.9f), previous, next, 1.5f)
        previous = next
    }
    drawCircle(ion, 4f, target)
}

/** A Solar Sweep beam from its inner radius out along its turning angle: a thermal band over a bone core line. */
private fun DrawScope.drawSolarBeam(engine: GameplayRenderModel, beam: WeaponEntityProjection, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val center = world(engine, engine.interpolated(beam.previousX, beam.x), engine.interpolated(beam.previousY, beam.y), shakeX, shakeY)
    if (!isOnScreen(center, beam.radius + beam.extent)) return
    // The shorter way between the step's two headings.
    val turn = atan2(sin(beam.angle - beam.previousAngle), kotlin.math.cos(beam.angle - beam.previousAngle))
    val angle = engine.interpolated(beam.angle - turn, beam.angle)
    val start = polar(center, beam.radius, angle)
    val end = polar(center, beam.radius + beam.extent, angle)
    val thermal = roles.element(StatusElement.THERMAL)
    drawLine(thermal.copy(alpha = 0.22f), start, end, 16f)
    drawLine(thermal.copy(alpha = 0.8f), start, end, 4f)
    drawLine(Kk.Bone, start, end, 1.5f)
}
