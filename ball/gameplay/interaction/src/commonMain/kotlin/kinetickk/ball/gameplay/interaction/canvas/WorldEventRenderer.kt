// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.WorldEventProjection
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.design.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

// Ambient world events: the storm's motes and the flare's sweep on the ground, then the telegraphed strikes. Colours come
// from the role palette only: the meteor's strike rings are danger (threat), the ion storm's rings take the Ion element's
// hue with a threat ring where the Core is hurt, the data storm is bone, the gravity storm polarity and the flare heat.

/** Side of an ambient cell (world units); a large screen grows it so no frame draws more than [MAX_EVENT_CELLS]. */
internal const val EVENT_CELL = 160f

/** Ambient cells one frame may draw (one line or circle each). */
internal const val MAX_EVENT_CELLS = 48

/** Growth of the cell side while a screen would need more than [MAX_EVENT_CELLS]. */
private const val EVENT_CELL_GROWTH = 1.25f

/** Gravity streaks drift down the wind at this speed (world units per second); the wind's strength scales their length. */
private const val GRAVITY_DRIFT = 150f
private const val GRAVITY_STREAK_MIN = 14f
private const val GRAVITY_STREAK_SPREAD = 20f

/** Data motes rise at this speed (world units per second) and sway sideways. */
private const val DATA_RISE = 38f
private const val DATA_SWAY = 6f

/** Share of data-storm cells that hold a brighter glint instead of a mote. */
private const val DATA_GLINT_SHARE = 0.2f

/** The flare's band crosses the screen in this long after a sweep starts; its width in world units. */
internal const val SOLAR_SWEEP_CROSS_SECONDS = 1.2f
private const val SOLAR_SWEEP_BAND = 260f

/** How long the ground look and the edge band take to fade out at the event's end (seconds). */
internal const val WORLD_EVENT_FADE_SECONDS = 3f

/** Ground looks ramp to this share of their strength while the event is only telegraphed. */
private const val TELEGRAPH_AMBIENT_SHARE = 0.5f

/**
 * The event's ground look after the dimension marks: the gravity storm's polarity streaks drifting down its wind, the data
 * storm's bone motes rising, the solar flare's heat band crossing the screen on each sweep. The motes sit in world-anchored
 * cells (one stateless hash of the cell and the kind each, no state, at most [MAX_EVENT_CELLS] a frame); a Low particle
 * density draws half of them and a crowd above the status-pattern limit half, above the signature limit none. The sweep
 * band is one cached gradient, translated and turned; it always draws, since the sweep heats enemies.
 */
internal fun DrawScope.drawWorldEventAmbient(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val event = engine.worldEvent ?: return
    val now = engine.renderElapsed
    val share = worldEventAmbientShare(event, now)
    if (share <= 0f) return
    when (event.kind) {
        WorldEventKind.GRAVITY_STORM, WorldEventKind.DATA_STORM -> drawEventMotes(engine, event, now, share, shakeX, shakeY, roles)
        WorldEventKind.SOLAR_FLARE -> drawSolarSweep(event, now, share, shakeX, shakeY, roles.heat)
        // Meteors and ion zones show only as their strikes.
        WorldEventKind.METEOR_SHOWER, WorldEventKind.ION_STORM -> Unit
    }
}

/**
 * Telegraphed strikes after the Volatile Remains bursts, each filling toward its landing: a meteor's dashed threat ring
 * over a growing shadow disc, an ion zone's dashed ring in the Ion hue with a threat ring where it hurts the Core.
 * At most three draws per strike.
 */
internal fun DrawScope.drawWorldEventStrikes(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val event = engine.worldEvent ?: return
    val strikes = event.strikes
    if (strikes.isEmpty()) return
    val now = engine.renderElapsed
    val threat = roles.threat
    val coreReach = engine.content.variety.worldEvents.ion.coreRadius
    for (index in strikes.indices) {
        val strike = strikes[index]
        val center = world(engine, strike.x, strike.y, shakeX, shakeY)
        if (!isOnScreen(center, strike.radius + 8f)) continue
        val span = (strike.strikesAt - strike.armedAt).coerceAtLeast(0.01f)
        val progress = ((now - strike.armedAt) / span).coerceIn(0f, 1f)
        when (event.kind) {
            WorldEventKind.METEOR_SHOWER -> {
                // The meteor's shadow grows as it falls; the ring is where it lands.
                drawCircle(Kk.Ink.copy(alpha = 0.25f + 0.4f * progress), strike.radius * (0.2f + 0.6f * progress), center)
                drawCircle(threat.copy(alpha = 0.06f + 0.16f * progress), strike.radius * progress, center)
                drawCircle(threat.copy(alpha = 0.5f + 0.4f * progress), strike.radius, center, style = WorldStrokes.dashedMedium)
            }
            WorldEventKind.ION_STORM -> {
                val ion = roles.element(StatusElement.ION.ordinal)
                drawCircle(ion.copy(alpha = 0.05f + 0.15f * progress), strike.radius * progress, center)
                drawCircle(ion.copy(alpha = 0.5f + 0.4f * progress), strike.radius, center, style = WorldStrokes.dashedMedium)
                drawCircle(threat.copy(alpha = 0.3f + 0.5f * progress), coreReach, center, style = kkStroke(1.5f))
            }
            WorldEventKind.DATA_STORM, WorldEventKind.GRAVITY_STORM, WorldEventKind.SOLAR_FLARE -> Unit
        }
    }
}

/**
 * Share (0..1) of an event's ground look at [now]: up to [TELEGRAPH_AMBIENT_SHARE] over the telegraph, whole while it
 * runs, fading to nothing over its last [WORLD_EVENT_FADE_SECONDS].
 */
internal fun worldEventAmbientShare(event: WorldEventProjection, now: Float): Float {
    if (now < event.telegraphAt || now >= event.endsAt) return 0f
    if (now < event.startsAt) {
        val telegraph = (event.startsAt - event.telegraphAt).coerceAtLeast(0.01f)
        return TELEGRAPH_AMBIENT_SHARE * ((now - event.telegraphAt) / telegraph).coerceIn(0f, 1f)
    }
    return ((event.endsAt - now) / WORLD_EVENT_FADE_SECONDS).coerceIn(0f, 1f)
}

/** The ambient cell side for a [width] × [height] view (world units): [EVENT_CELL], grown until the view fits the cap. */
internal fun eventCellSize(width: Float, height: Float): Float {
    var cell = EVENT_CELL
    while (eventCellCount(width, height, cell) > MAX_EVENT_CELLS) cell *= EVENT_CELL_GROWTH
    return cell
}

/** The most world-anchored cells of side [cell] a [width] × [height] view can touch. */
internal fun eventCellCount(width: Float, height: Float, cell: Float): Int =
    ((width / cell).toInt() + 2) * ((height / cell).toInt() + 2)

private fun DrawScope.drawEventMotes(
    engine: GameplayRenderModel,
    event: WorldEventProjection,
    now: Float,
    share: Float,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val crowd = FxFrame.crowd
    if (crowd > FxFrame.CROWD_SIG_ONLY) return
    // A low density or a crowd keeps every other cell (by the cell's own hash, so the kept ones never change).
    val halved = engine.settings.particleDensity == ParticleDensity.LOW || crowd > FxFrame.CROWD_SECONDARY_OFF
    val cell = eventCellSize(size.width, size.height)
    val left = engine.renderCameraX - size.width * 0.5f
    val top = engine.renderCameraY - size.height * 0.5f
    val firstX = floor(left / cell).toInt()
    val firstY = floor(top / cell).toInt()
    val lastX = floor((left + size.width) / cell).toInt()
    val lastY = floor((top + size.height) / cell).toInt()
    val kind = event.kind.ordinal
    val gravity = event.kind == WorldEventKind.GRAVITY_STORM
    val windX = cos(event.windAngle)
    val windY = sin(event.windAngle)
    val strength = 0.4f + 0.6f * event.windStrength.coerceIn(0f, 1f)
    val pol = roles.pol
    for (cellY in firstY..lastY) for (cellX in firstX..lastX) {
        val hash = eventCellHash(cellX, cellY, kind)
        if (halved && (hash and 1) == 0) continue
        val baseX = (cellX + eventUnit(hash, 3)) * cell
        val baseY = (cellY + eventUnit(hash, 11)) * cell
        val phase = eventUnit(hash, 19)
        if (gravity) {
            val travel = fraction(now * GRAVITY_DRIFT / cell + phase)
            val along = (travel - 0.5f) * cell
            val length = (GRAVITY_STREAK_MIN + GRAVITY_STREAK_SPREAD * eventUnit(hash, 7)) * strength
            val head = world(engine, baseX + windX * along, baseY + windY * along, shakeX, shakeY)
            val tail = Offset(head.x - windX * length, head.y - windY * length)
            val alpha = 0.5f * share * sin(PI.toFloat() * travel)
            drawLine(pol.copy(alpha = alpha), tail, head, 1.5f)
        } else {
            val travel = fraction(now * DATA_RISE / cell + phase)
            val sway = sin(now * 1.3f + phase * 6.2832f) * DATA_SWAY
            val center = world(engine, baseX + sway, baseY + (0.5f - travel) * cell, shakeX, shakeY)
            val fade = sin(PI.toFloat() * travel)
            if (eventUnit(hash, 27) < DATA_GLINT_SHARE) {
                val glint = 0.5f + 0.5f * sin(now * 5f + phase * 6.2832f)
                drawCircle(Kk.Bone.copy(alpha = (0.35f + 0.45f * glint) * share * fade), 2.6f, center)
            } else {
                drawCircle(Kk.Bone.copy(alpha = 0.3f * share * fade), 1.4f + 1.2f * eventUnit(hash, 7), center)
            }
        }
    }
}

/**
 * The flare's sweep: a heat band crossing the screen through its centre along the sweep's angle for
 * [SOLAR_SWEEP_CROSS_SECONDS] after it starts.
 */
private fun DrawScope.drawSolarSweep(event: WorldEventProjection, now: Float, share: Float, shakeX: Float, shakeY: Float, heat: Color) {
    val since = now - event.sweepStartedAt
    if (!(since >= 0f && since < SOLAR_SWEEP_CROSS_SECONDS)) return
    val progress = since / SOLAR_SWEEP_CROSS_SECONDS
    val reach = sqrt(size.width * size.width + size.height * size.height) * 0.5f + SOLAR_SWEEP_BAND
    val offset = -reach + 2f * reach * progress
    val normalX = cos(event.sweepAngle)
    val normalY = sin(event.sweepAngle)
    val brush = EventBrushes.sweep(heat)
    translate(size.width * 0.5f + normalX * offset + shakeX, size.height * 0.5f + normalY * offset + shakeY) {
        rotate(event.sweepAngle * RADIANS_TO_DEGREES, Offset.Zero) {
            drawRect(brush, Offset(-SOLAR_SWEEP_BAND * 0.5f, -reach), Size(SOLAR_SWEEP_BAND, reach * 2f), alpha = 0.75f * share)
        }
    }
}

private const val RADIANS_TO_DEGREES = 57.29578f

/** The flare's band gradient across its width, built once per colour (draw-thread confined). */
private object EventBrushes {
    private var sweepColor: Color = Color.Unspecified
    private var sweepBrush: Brush? = null

    fun sweep(color: Color): Brush {
        val cached = sweepBrush
        if (cached != null && sweepColor == color) return cached
        val half = SOLAR_SWEEP_BAND * 0.5f
        return Brush.horizontalGradient(
            0f to Color.Transparent, 0.5f to color.copy(alpha = 0.32f), 1f to Color.Transparent, startX = -half, endX = half,
        ).also {
            sweepColor = color
            sweepBrush = it
        }
    }
}

private fun fraction(value: Float): Float = value - floor(value)

/** A stateless hash of an ambient cell and the event kind (murmur3's finalizer over the mixed coordinates). */
internal fun eventCellHash(cellX: Int, cellY: Int, kind: Int): Int {
    var h = cellX * -0x61c88647 xor (cellY * 0x27d4eb2f) xor (kind * 0x165667b1)
    h = h xor (h ushr 16)
    h *= -0x7a143595
    h = h xor (h ushr 13)
    h *= -0x3d4d51cb
    return h xor (h ushr 16)
}

/** A 0..1 value from [hash] (its bits rotated by [salt]). */
private fun eventUnit(hash: Int, salt: Int): Float = ((hash.rotateLeft(salt) ushr 8) and 0xFFFF) / 65_536f
