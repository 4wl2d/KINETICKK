// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.localization.DimensionHudText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PortalProjection
import kinetickk.ball.gameplay.nucleus.render.RiftLock
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkLayerHues
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.d
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.isOnScreen
import kinetickk.foundation.design.kkFillBrush
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.positiveModulo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * Rifts are tears in space: a jagged lens of void opened in the world, its edges burning in the accent of where it
 * leads (the Zenith's gold, the Nadir's heat, the Archive's void, realspace's bone for the way home), a vortex turning
 * inside it and sparks drawn into it. A locked rift is grey, hatched and barred and names why; the Archive's glitch rift
 * splits into cyan and magenta copies that jitter on a stateless step. Every shape is a cached unit path scaled to the
 * rift's projected radius.
 */

/** A rift's accent by its target: the Zenith's gold, the Nadir's heat, the Archive's void, realspace's bone. */
internal fun riftAccent(target: WorldLayer, roles: KkRolePalette): Color = when (target) {
    WorldLayer.ZENITH -> roles.gold.base
    WorldLayer.NADIR -> roles.heat
    WorldLayer.ARCHIVE -> roles.element(StatusElement.VOID.ordinal)
    WorldLayer.BASE -> Kk.Bone
}

/** Whether rifts into [target] are locked in [engine] (the site renderer's window rifts ask here). */
internal fun riftLocked(engine: GameplayRenderModel, target: WorldLayer): Boolean = engine.dimension.lockedRifts and target.bit != 0

/** Why the window rift of [kind] is locked in [engine]; [RiftLock.NONE] for an open rift or another kind of site. */
internal fun riftLockOf(engine: GameplayRenderModel, kind: SiteKind): RiftLock {
    val target = when (kind) {
        SiteKind.ZENITH_RIFT -> WorldLayer.ZENITH
        SiteKind.NADIR_RIFT -> WorldLayer.NADIR
        else -> return RiftLock.NONE
    }
    return if (riftLocked(engine, target)) engine.dimension.riftLock else RiftLock.NONE
}

/** The tear's half height and half width as shares of its catch radius (inside the ring); its glow reaches past it. */
private const val TEAR_HEIGHT = 0.98f
private const val TEAR_WIDTH = 1.15f
private const val TEAR_GLOW = 1.7f
/** Sparks drawn into an open rift, and the seconds one takes from the glow's edge to the void. */
private const val RIFT_SPARKS = 10
private const val RIFT_SPARK_SECONDS = 1.6f
/** How far a glitch rift's copies stand off at most (world units). */
internal const val PORTAL_GLITCH_SHIFT = 6f

/**
 * One rift at [center] leading to [target], [radius] its catch radius: an outer glow, the tear's void with a vortex
 * turning inside it, its burning edge, the target's sigil and sparks drawn in. A [locked] rift is grey, hatched and
 * barred; a [glitch] rift doubles its edge in cyan and magenta either side, standing off in 4 Hz steps. The site
 * renderer's window rifts draw through it too.
 */
internal fun DrawScope.drawPortal(
    center: Offset,
    target: WorldLayer,
    locked: Boolean,
    glitch: Boolean,
    time: Float,
    roles: KkRolePalette,
    radius: Float,
    variant: Int = 0,
) {
    val accent = if (locked) Kk.Mute else riftAccent(target, roles)
    val tear = RiftPaths.tear(variant)
    val scaleX = radius * TEAR_WIDTH
    val scaleY = radius * TEAR_HEIGHT
    // The breath of an open tear: its width swells and settles once per four seconds.
    val breath = if (locked) 1f else 1f + 0.06f * sin(time * TWO_PI / 4f + variant)
    drawSoftDisc(DimensionGlows.of(accent.copy(alpha = if (locked) 0.12f else 0.34f), 0.3f), center.x, center.y,
        radius * TEAR_GLOW, radius * TEAR_GLOW * 1.15f)
    translate(center.x, center.y) {
        scale(scaleX * breath, scaleY, Offset.Zero) {
            drawPath(tear, Kk.Ink)
            clipPath(tear) {
                // A glimpse of where it leads: the accent's light deep inside, brightest at the heart.
                drawSoftDisc(DimensionGlows.of(accent.copy(alpha = if (locked) 0.1f else 0.9f), 0.55f), 0f, 0.1f, 0.7f, 1f)
                if (!locked) drawSoftDisc(DimensionGlows.of(Kk.Bone.copy(alpha = 0.75f), 0.3f), 0f, 0.1f, 0.32f, 0.5f)
                if (locked) {
                    // The hatch keeps its screen pitch: drawn back in screen units inside the unit clip.
                    val sx = scaleX * breath
                    scale(1f / sx, 1f / scaleY, Offset.Zero) {
                        drawRect(kkFillBrush(KkFillKind.HATCH, Kk.Mute), Offset(-sx, -scaleY), Size(2f * sx, 2f * scaleY), alpha = 0.5f)
                    }
                } else {
                    drawVortex(accent, time)
                }
            }
            if (glitch) {
                val shift = portalGlitchOffset(time) / scaleX
                translate(-shift, 0f) { drawPath(tear, KkLayerHues.Glitch, alpha = 0.9f, style = RiftPaths.edge) }
                translate(shift, 0f) { drawPath(tear, KkLayerHues.Signal, alpha = 0.9f, style = RiftPaths.edge) }
            }
            drawPath(tear, accent, alpha = if (locked) 0.25f else 0.45f, style = RiftPaths.glow)
            drawPath(tear, accent, style = RiftPaths.edge)
            drawPath(tear, Kk.Bone, alpha = if (locked) 0.4f else 0.9f, style = RiftPaths.core)
        }
    }
    if (locked) {
        // Two bars across the tear: closed.
        val bar = radius * 0.95f
        drawLine(Kk.Mute, Offset(center.x - bar, center.y - bar * 0.35f), Offset(center.x + bar, center.y + bar * 0.35f), 4f)
        drawLine(Kk.Mute, Offset(center.x - bar, center.y + bar * 0.35f), Offset(center.x + bar, center.y - bar * 0.35f), 4f)
    } else {
        drawRiftSparks(center, radius, accent, time)
    }
    drawDimensionSigil(target, center, radius * 0.22f, if (locked) Kk.Mute else Kk.Ink)
}

/** The vortex inside an open tear (unit space): three faint bone arcs turning at their own slow rates. */
private fun DrawScope.drawVortex(accent: Color, time: Float) {
    for (index in VortexRadii.indices) {
        val radius = VortexRadii[index]
        drawArc(Kk.Bone.copy(alpha = 0.32f - 0.08f * index), time * VortexTurns[index] + index * 120f, 120f, useCenter = false,
            topLeft = Offset(-radius, -radius), size = Size(radius * 2f, radius * 2f), style = RiftPaths.vortex)
    }
}

private val VortexRadii = floatArrayOf(0.3f, 0.55f, 0.8f)
/** Turns in degrees per second: no faster than one turn per 24 s. */
private val VortexTurns = floatArrayOf(15f, -12f, 9f)

/** Sparks drawn in from the glow's edge to the tear, each on its own phase, brightening as they near the void. */
private fun DrawScope.drawRiftSparks(center: Offset, radius: Float, accent: Color, time: Float) {
    for (index in 0 until RIFT_SPARKS) {
        val phase = positiveModulo(time / RIFT_SPARK_SECONDS + index * 0.37f, 1f)
        val angle = index * TWO_PI / RIFT_SPARKS + time * 0.25f
        val reach = radius * (TEAR_GLOW - (TEAR_GLOW - 0.35f) * phase)
        drawCircle(accent.copy(alpha = 0.25f + 0.7f * phase), 1.6f + 1.4f * phase,
            Offset(center.x + cos(angle) * reach, center.y + sin(angle) * reach * 1.1f))
    }
}

/** How far a glitch rift's copies stand off at [time]: a stateless step every quarter second, 40 % to all of the shift. */
internal fun portalGlitchOffset(time: Float): Float = PORTAL_GLITCH_SHIFT * (0.4f + 0.6f * cellUnit(floor(time * 4f).toInt(), GLITCH_SALT))

private const val GLITCH_SALT = 11

/**
 * A rift of the dimension system (the glitch rift in realspace, the exit rift in a visit) at its projected place: the
 * dashed ring a passing Core must cross and the tear.
 */
internal fun DrawScope.drawDimensionPortal(engine: GameplayRenderModel, portal: PortalProjection, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val center = world(engine, portal.x, portal.y, shakeX, shakeY)
    if (!isOnScreen(center, portal.radius * TEAR_GLOW)) return
    drawCircle(Kk.Bone.copy(alpha = 0.28f), portal.radius, center, style = MarkStrokes.ring)
    drawPortal(center, portal.target, portal.locked, portal.glitch, engine.renderElapsed, roles, portal.radius, portal.id)
}

/** Under a locked glitch rift, why it is locked (window rifts name it in their offer labels). */
internal fun DrawScope.drawDimensionLabels(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    val portal = engine.dimension.glitchPortal ?: return
    if (!portal.locked) return
    val center = world(engine, portal.x, portal.y, shakeX, shakeY)
    if (!isOnScreen(center, portal.radius * TEAR_GLOW)) return
    val scale = engine.worldScale
    inScreenUnits(scale) {
        drawRiftLockLabel(engine.dimension.riftLock, center.x * scale, (center.y + portal.radius * TEAR_HEIGHT + 10f) * scale, textMeasurer)
    }
}

/** Lock labels: mono 11 px on the boards, like the offers' timers. */
private val LOCK_LABEL_SP = worldLabelSp(11f)

/** Why a rift is locked, centred under it at ([x], [top]) in mute: nothing for [RiftLock.NONE]. */
internal fun DrawScope.drawRiftLockLabel(lock: RiftLock, x: Float, top: Float, textMeasurer: TextMeasurer) {
    if (lock == RiftLock.NONE) return
    val layout = measureKkText(textMeasurer, riftLockText(lock, textMeasurer.language),
        textMeasurer.typography.labelStyle(LOCK_LABEL_SP, color = Kk.Mute), uppercase = true)
    val edge = d(8f)
    val half = layout.size.width * 0.5f
    drawKkText(layout, x.coerceIn(edge + half, size.width - edge - half), top, Kk.Mute, KkAlign.CENTER, KkVAlign.TOP)
}

/** The words of a rift's [lock] in [language] (resolved once per lock and language). */
internal fun riftLockText(lock: RiftLock, language: AppLanguage): String {
    val slot = lock.ordinal * AppLanguage.entries.size + language.ordinal
    return RiftLockTexts[slot] ?: language.text(
        when (lock) {
            RiftLock.ENGAGEMENT -> DimensionHudText.LockedEngagement
            RiftLock.BOSS -> DimensionHudText.LockedBoss
            RiftLock.ARCHITECT, RiftLock.NONE -> DimensionHudText.LockedArchitect
        },
    ).also { RiftLockTexts[slot] = it }
}

private val RiftLockTexts = arrayOfNulls<String>(RiftLock.entries.size * AppLanguage.entries.size)

private const val TWO_PI = (2.0 * PI).toFloat()

/** Cached unit shapes and strokes of the rifts: the tear's variants and its edge, glow and vortex lines. */
private object RiftPaths {
    private const val VARIANTS = 3
    private val tears = arrayOfNulls<Path>(VARIANTS)

    /** Unit strokes: the tear is drawn scaled by about 70 u, so 0.05 is about 3.5 px. */
    val edge: Stroke by lazy { Stroke(0.05f) }
    val core: Stroke by lazy { Stroke(0.02f) }
    val glow: Stroke by lazy { Stroke(0.16f) }
    val vortex: Stroke by lazy { Stroke(0.035f) }

    /**
     * Tear [variant] (any id; three shapes): a lens from (0, -1) to (0, 1), widest a little below the middle, its two
     * edges jagged at alternating heights by the variant's offsets.
     */
    fun tear(variant: Int): Path {
        val index = ((variant % VARIANTS) + VARIANTS) % VARIANTS
        return tears[index] ?: Path().also { path ->
            val jag = TearJags[index]
            path.moveTo(0f, -1f)
            for (step in 1 until TEAR_STEPS) {
                val y = -1f + 2f * step / TEAR_STEPS
                path.lineTo(tearWidth(y) * (1f + jag[step % jag.size]), y)
            }
            path.lineTo(0f, 1f)
            for (step in TEAR_STEPS - 1 downTo 1) {
                val y = -1f + 2f * step / TEAR_STEPS
                path.lineTo(-tearWidth(y) * (1f + jag[(step + 3) % jag.size]), y + 0.04f)
            }
            path.close()
            tears[index] = path
        }
    }

    /** The lens's half width at height [y] (-1..1): zero at the tips, 0.5 just below the middle. */
    private fun tearWidth(y: Float): Float {
        val t = (y + 1f) * 0.5f
        return 2.1f * t * (1f - t) * (1f - 0.25f * (t - 0.55f))
    }

    private const val TEAR_STEPS = 9
}

/** Jag offsets of the three tears' edges (shares of the half width, cycled along an edge). */
private val TearJags: List<FloatArray> = listOf(
    floatArrayOf(0.18f, -0.22f, 0.3f, -0.12f, 0.24f, -0.28f, 0.1f),
    floatArrayOf(-0.2f, 0.26f, -0.1f, 0.32f, -0.26f, 0.14f, -0.18f),
    floatArrayOf(0.28f, -0.16f, 0.12f, -0.3f, 0.2f, -0.1f, 0.26f),
)

/** The rifts' cached shapes never depend on the world, the viewport or the preferences. */
internal fun prepareRiftResources() {
    repeat(3) { RiftPaths.tear(it) }
    RiftPaths.edge
    RiftPaths.core
    RiftPaths.glow
    RiftPaths.vortex
}
