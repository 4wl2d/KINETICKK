// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PortalProjection
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkRebirthTiers
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.drawKkHalftone
import kinetickk.foundation.design.drawKkRadialFade
import kinetickk.foundation.design.isOnScreen
import kinetickk.foundation.design.kkFillBrush
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.positiveModulo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Dimensions in the world. Realspace keeps its own backdrop untouched; a dimension paints its ground, grid and halftone
 * in its theme and lays stateless motes over them (rising light specks and two slow light wedges in the Zenith, rising
 * embers over cracked ground in the Nadir, blinking hollow squares and a scanline on the Archive's local clock). Rifts,
 * halo rings and embers are the world marks; rifts and the ring to fly through next also get edge markers and minimap
 * marks. Colours come from the role palette (hazard never grounds a layer, gold never appears); every cache is
 * object-local and drawn under a translation, so the world rebase never invalidates one.
 */

/** Side of a mote cell in world units: each cell holds at most one mote, placed by a stateless hash of the cell. */
internal const val DIMENSION_MOTE_CELL = 160f

/** A rift's ink disc, its three dashed rings and its bone rim (world units). */
private const val PORTAL_DISC_RADIUS = 58f
private const val PORTAL_RIM_RADIUS = 65f
private const val PORTAL_SIGIL_RADIUS = 14f
private val PortalRingRadii = floatArrayOf(30f, 44f, 58f)
/** Ring turns in degrees per second: no faster than one turn per 24 s. */
private val PortalRingTurns = floatArrayOf(15f, -12f, 9f)
/** How far a glitch rift's ghost rings stand off at most (world units). */
internal const val PORTAL_GLITCH_SHIFT = 2.5f
/** Reach of a rift's look past its centre, for culling. */
private const val PORTAL_EXTENT = PORTAL_RIM_RADIUS + 4f

/** A halo ring, its blunt ticks and the dashed ring around the ring to fly through next (world units). */
private const val HALO_RADIUS = 70f
private const val HALO_TICK_FROM = 74f
private const val HALO_TICK_TO = 80f
private const val HALO_NEXT_RADIUS = 84f
private const val HALO_EXTENT = HALO_NEXT_RADIUS + 6f

/** An ember's diamond side, its glow ring and the seconds over which it fades out at the end of its life. */
private const val EMBER_SIDE = 7f
private const val EMBER_GLOW_RADIUS = 12f
private const val EMBER_FADE_SECONDS = 1.5f

/** The halftone disc under the Core, as realspace draws it. */
private const val HALFTONE_DIAMETER = 700f
private val DimensionHalftoneRect = Rect(-HALFTONE_DIAMETER * 0.5f, -HALFTONE_DIAMETER * 0.5f, HALFTONE_DIAMETER * 0.5f, HALFTONE_DIAMETER * 0.5f)

/** Motes reach this far past their position; cells this far outside the view still draw into it. */
private const val MOTE_MARGIN = 4f

/** Share of Nadir cells with a crack in the ground. */
private const val CRACK_SHARE = 0.18f

/** The Archive's scanline band: its height and how fast the local clock moves it down the screen. */
private const val SCANLINE_HEIGHT = 24f
private const val SCANLINE_SPEED = 36f

private const val TWO_PI = (2.0 * PI).toFloat()

/** A dimension's backdrop colours for one rebirth tier and Color vision palette. */
internal class DimensionTheme(
    val ground: Color,
    val grid: Color,
    /** Every fourth grid line (the Archive's; the other layers draw every line alike). */
    val gridMajor: Color,
    val halftone: Color,
    val mote: Color,
    val moteAlt: Color,
    val ray: Color,
    val crack: Color,
    val crackRim: Color,
    val scanline: Color,
)

/**
 * Dimension themes by layer, tier and palette (identity): the tier's arena grid alpha raised a little, the layer's own
 * ground mixed into ink, and the Event Horizon tier in black and white. Built once per key, never per frame.
 */
internal object DimensionThemes {
    private const val CAPACITY = 8
    private val layers = IntArray(CAPACITY) { -1 }
    private val tiers = IntArray(CAPACITY)
    private val palettes = arrayOfNulls<KkRolePalette>(CAPACITY)
    private val themes = arrayOfNulls<DimensionTheme>(CAPACITY)
    private var next = 0

    fun of(layer: WorldLayer, rebirthLevel: Int, roles: KkRolePalette): DimensionTheme {
        val tier = rebirthLevel.coerceIn(0, KkRebirthTiers.MaxTier)
        for (index in 0 until CAPACITY) {
            if (layers[index] == layer.ordinal && tiers[index] == tier && palettes[index] === roles) return themes[index]!!
        }
        val theme = build(layer, tier, roles)
        layers[next] = layer.ordinal
        tiers[next] = tier
        palettes[next] = roles
        themes[next] = theme
        next = (next + 1) % CAPACITY
        return theme
    }

    private fun build(layer: WorldLayer, tier: Int, roles: KkRolePalette): DimensionTheme {
        val arena = arenaTheme(tier).grid.alpha
        val horizon = KkRebirthTiers.isEventHorizon(tier)
        fun hue(color: Color): Color = if (horizon) Color.White else color
        fun ground(color: Color, share: Float): Color = if (horizon) kkMix(Kk.Ink, Color.White, 0.04f) else kkMix(Kk.Ink, color, share)
        val bone = hue(Kk.Bone)
        val none = Color.Transparent
        return when (layer) {
            WorldLayer.ZENITH -> {
                val grid = hue(kkMix(roles.shield, Kk.Bone, 0.5f)).copy(alpha = arena + 0.035f)
                DimensionTheme(
                    ground = ground(roles.shield, 0.06f), grid = grid, gridMajor = grid, halftone = bone.copy(alpha = 0.07f),
                    mote = bone.copy(alpha = 0.30f), moteAlt = hue(roles.shield).copy(alpha = 0.22f), ray = bone.copy(alpha = 0.035f),
                    crack = none, crackRim = none, scanline = none,
                )
            }
            WorldLayer.NADIR -> {
                val heat = hue(roles.heat)
                val grid = heat.copy(alpha = arena + 0.03f)
                DimensionTheme(
                    ground = ground(roles.heat, 0.07f), grid = grid, gridMajor = grid, halftone = heat.copy(alpha = 0.06f),
                    mote = heat.copy(alpha = 0.35f), moteAlt = heat.copy(alpha = 0.35f), ray = none,
                    crack = (if (horizon) kkMix(Kk.Ink, Color.White, 0.1f) else Kk.Ink4).copy(alpha = 0.6f), crackRim = heat.copy(alpha = 0.1f),
                    scanline = none,
                )
            }
            WorldLayer.ARCHIVE -> {
                val void = hue(roles.element(VOID_ELEMENT))
                DimensionTheme(
                    ground = ground(roles.element(VOID_ELEMENT), 0.05f), grid = bone.copy(alpha = 0.055f),
                    gridMajor = bone.copy(alpha = 0.09f), halftone = void.copy(alpha = 0.06f), mote = bone.copy(alpha = 0.32f),
                    moteAlt = void.copy(alpha = 0.32f), ray = none, crack = none, crackRim = none, scanline = bone.copy(alpha = 0.03f),
                )
            }
            // Realspace never draws through a dimension theme; its own arena colours stand in.
            WorldLayer.BASE -> {
                val arenaTheme = arenaTheme(tier)
                DimensionTheme(
                    ground = Kk.Ink, grid = arenaTheme.grid, gridMajor = arenaTheme.grid, halftone = arenaTheme.halftone,
                    mote = none, moteAlt = none, ray = none, crack = none, crackRim = none, scanline = none,
                )
            }
        }
    }
}

/** Index of the void element in the palette's element colours (the Archive's hue). */
private const val VOID_ELEMENT = 6

/** A rift's accent by its target: the Zenith shield, the Nadir heat, the Archive void, realspace bone. */
internal fun riftAccent(target: WorldLayer, roles: KkRolePalette): Color = when (target) {
    WorldLayer.ZENITH -> roles.shield
    WorldLayer.NADIR -> roles.heat
    WorldLayer.ARCHIVE -> roles.element(VOID_ELEMENT)
    WorldLayer.BASE -> Kk.Bone
}

/** Whether rifts into [target] are locked in [engine] (the site renderer's window rifts ask here). */
internal fun riftLocked(engine: GameplayRenderModel, target: WorldLayer): Boolean = engine.dimension.lockedRifts and target.bit != 0

/** The ground of a dimension, drawn instead of the realspace grid while the Core is in one. */
internal fun DrawScope.drawDimensionBackdrop(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val layer = engine.dimension.layer
    val theme = DimensionThemes.of(layer, engine.rebirthLevel, roles)
    // Painted here rather than under the backdrop, so every caller of the backdrop shows the dimension's ground.
    drawRect(theme.ground)
    if (layer == WorldLayer.ZENITH) drawZenithRays(theme, engine.renderElapsed)
    drawDimensionGrid(engine, theme, shakeX, shakeY)
    if (layer == WorldLayer.NADIR) drawNadirCracks(engine, theme, shakeX, shakeY)
    val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    translate(core.x, core.y) {
        drawKkRadialFade(DimensionHalftoneRect) { drawKkHalftone(DimensionHalftoneRect, theme.halftone) }
    }
    drawDimensionMotes(engine, layer, theme, shakeX, shakeY)
    if (layer == WorldLayer.ARCHIVE) {
        val top = positiveModulo(engine.dimension.localClock * SCANLINE_SPEED, size.height + SCANLINE_HEIGHT) - SCANLINE_HEIGHT
        drawRect(theme.scanline, Offset(0f, top), Size(size.width, SCANLINE_HEIGHT))
    }
}

/** The world-anchored 40-unit grid in the theme; every fourth world line takes the major colour. */
private fun DrawScope.drawDimensionGrid(engine: GameplayRenderModel, theme: DimensionTheme, shakeX: Float, shakeY: Float) {
    val shiftX = size.width * 0.5f - engine.renderCameraX + shakeX
    val shiftY = size.height * 0.5f - engine.renderCameraY + shakeY
    var x = worldGridOffset(engine.renderCameraX, size.width, shakeX) - WORLD_GRID_SPACING
    var column = ((x - shiftX) / WORLD_GRID_SPACING).roundToInt()
    while (x <= size.width) {
        if (x >= 0f) drawLine(if (column and 3 == 0) theme.gridMajor else theme.grid, Offset(x, 0f), Offset(x, size.height), 1f)
        x += WORLD_GRID_SPACING
        column++
    }
    var y = worldGridOffset(engine.renderCameraY, size.height, shakeY) - WORLD_GRID_SPACING
    var row = ((y - shiftY) / WORLD_GRID_SPACING).roundToInt()
    while (y <= size.height) {
        if (y >= 0f) drawLine(if (row and 3 == 0) theme.gridMajor else theme.grid, Offset(0f, y), Offset(size.width, y), 1f)
        y += WORLD_GRID_SPACING
        row++
    }
}

/** The Zenith's two light wedges falling from above the screen, each swaying once per 48 s. */
private fun DrawScope.drawZenithRays(theme: DimensionTheme, time: Float) {
    val wedge = DimensionPaths.rayWedge(size.width, size.height)
    for (index in 0 until 2) {
        val sway = 18f * sin(time * TWO_PI / 48f + index * PI.toFloat()) + if (index == 0) -10f else 10f
        translate(size.width * (0.3f + 0.4f * index), -size.height * 0.25f) {
            rotate(sway, Offset.Zero) { drawPath(wedge, theme.ray) }
        }
    }
}

/** Cracks in 18 % of the Nadir's cells: a heat rim under an ink line, one of three cached shapes per cell. */
private fun DrawScope.drawNadirCracks(engine: GameplayRenderModel, theme: DimensionTheme, shakeX: Float, shakeY: Float) {
    val originX = size.width * 0.5f - engine.renderCameraX + shakeX
    val originY = size.height * 0.5f - engine.renderCameraY + shakeY
    val firstX = floor((-originX - CRACK_REACH) / DIMENSION_MOTE_CELL).toInt()
    val lastX = floor((size.width - originX + CRACK_REACH) / DIMENSION_MOTE_CELL).toInt()
    val firstY = floor((-originY - CRACK_REACH) / DIMENSION_MOTE_CELL).toInt()
    val lastY = floor((size.height - originY + CRACK_REACH) / DIMENSION_MOTE_CELL).toInt()
    for (cy in firstY..lastY) for (cx in firstX..lastX) {
        val hash = dimensionCellHash(cx, cy, WorldLayer.NADIR.ordinal)
        if (dimensionUnit(hash, CRACK_SALT) >= CRACK_SHARE) continue
        val crack = DimensionPaths.crack(min(2, (dimensionUnit(hash, CRACK_SALT + 1) * 3f).toInt()))
        val x = originX + (cx + 0.3f + 0.4f * dimensionUnit(hash, CRACK_SALT + 2)) * DIMENSION_MOTE_CELL
        val y = originY + (cy + 0.3f + 0.4f * dimensionUnit(hash, CRACK_SALT + 3)) * DIMENSION_MOTE_CELL
        translate(x, y) {
            drawPath(crack, theme.crackRim, style = kkStroke(3f))
            drawPath(crack, theme.crack, style = kkStroke(1.5f))
        }
    }
}

/** A crack reaches at most this far from its anchor. */
private const val CRACK_REACH = 50f
private const val CRACK_SALT = 6

/** Mote cells this dense hold a mote; none at the low particle density. */
internal fun dimensionMoteDensity(density: ParticleDensity): Float = when (density) {
    ParticleDensity.LOW -> 0f
    ParticleDensity.NORMAL -> 0.45f
    ParticleDensity.HIGH -> 0.6f
}

/**
 * Every mote of [layer] whose cell overlaps the world rectangle [left]..[right] × [top]..[bottom] at [time], as world
 * positions with the cell's hash: a cell holds one when its hash falls under [density]; a [crowded] screen keeps every
 * other cell. A mote rises inside its own cell (the Zenith 8–18, the Nadir 12–24 units per second; the Archive's stay),
 * so the pattern belongs to the world and repeats exactly one cell over.
 */
internal inline fun forEachDimensionMote(
    layer: WorldLayer,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    time: Float,
    density: Float,
    crowded: Boolean,
    action: (x: Float, y: Float, hash: Int) -> Unit,
) {
    if (density <= 0f) return
    val riseBase = when (layer) {
        WorldLayer.ZENITH -> 8f
        WorldLayer.NADIR -> 12f
        WorldLayer.ARCHIVE, WorldLayer.BASE -> 0f
    }
    val riseSpread = when (layer) {
        WorldLayer.ZENITH -> 10f
        WorldLayer.NADIR -> 12f
        WorldLayer.ARCHIVE, WorldLayer.BASE -> 0f
    }
    val firstX = floor(left / DIMENSION_MOTE_CELL).toInt()
    val lastX = floor(right / DIMENSION_MOTE_CELL).toInt()
    val firstY = floor(top / DIMENSION_MOTE_CELL).toInt()
    val lastY = floor(bottom / DIMENSION_MOTE_CELL).toInt()
    for (cy in firstY..lastY) for (cx in firstX..lastX) {
        if (crowded && (cx + cy) and 1 != 0) continue
        val hash = dimensionCellHash(cx, cy, layer.ordinal)
        if (dimensionUnit(hash, 0) >= density) continue
        val rise = riseBase + riseSpread * dimensionUnit(hash, 3)
        val x = (cx + dimensionUnit(hash, 1)) * DIMENSION_MOTE_CELL
        val y = cy * DIMENSION_MOTE_CELL + positiveModulo(dimensionUnit(hash, 2) * DIMENSION_MOTE_CELL - rise * time, DIMENSION_MOTE_CELL)
        action(x, y, hash)
    }
}

/** One draw per mote, in the theme: light specks, rising embers, or blinking hollow squares on the local clock. */
private fun DrawScope.drawDimensionMotes(engine: GameplayRenderModel, layer: WorldLayer, theme: DimensionTheme, shakeX: Float, shakeY: Float) {
    val density = dimensionMoteDensity(engine.settings.particleDensity)
    if (density <= 0f) return
    val crowded = onScreenEnemyCount(engine, shakeX, shakeY, STATUS_PATTERN_ENEMY_LIMIT + 1) > STATUS_PATTERN_ENEMY_LIMIT
    val originX = size.width * 0.5f - engine.renderCameraX + shakeX
    val originY = size.height * 0.5f - engine.renderCameraY + shakeY
    // The Archive's motes keep its local clock, so they freeze when the Core stops.
    val time = if (layer == WorldLayer.ARCHIVE) engine.dimension.localClock else engine.renderElapsed
    forEachDimensionMote(
        layer, -originX - MOTE_MARGIN, -originY - MOTE_MARGIN, size.width - originX + MOTE_MARGIN, size.height - originY + MOTE_MARGIN,
        time, density, crowded,
    ) { x, y, hash ->
        val at = Offset(x + originX, y + originY)
        // At least 24 s per twinkle.
        val twinkle = sin(time * 0.26f + TWO_PI * dimensionUnit(hash, 4))
        val alt = dimensionUnit(hash, 5) < 0.4f
        val color = if (alt) theme.moteAlt else theme.mote
        when (layer) {
            WorldLayer.ZENITH ->
                drawCircle(color.copy(alpha = (color.alpha + 0.15f * twinkle).coerceIn(0f, 1f)), 1.6f + dimensionUnit(hash, 6), at)
            WorldLayer.NADIR -> {
                val side = 2f + dimensionUnit(hash, 6) * 1.2f
                drawRect(color.copy(alpha = (color.alpha + 0.1f * twinkle).coerceIn(0f, 1f)), Offset(at.x - side * 0.5f, at.y - side * 0.5f),
                    Size(side, side))
            }
            WorldLayer.ARCHIVE -> {
                // Each square blinks on its own phase of the local clock.
                if (positiveModulo(time * 0.4f + dimensionUnit(hash, 6), 1f) < 0.55f) {
                    drawRect(color.copy(alpha = (color.alpha + 0.1f * twinkle).coerceIn(0f, 1f)), Offset(at.x - 2f, at.y - 2f), Size(4f, 4f),
                        style = kkStroke(1f))
                }
            }
            WorldLayer.BASE -> Unit
        }
    }
}

/** Rifts, halos and embers of the current layer, after the sites; realspace shows only an open glitch rift. */
internal fun DrawScope.drawDimensionWorld(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val dimension = engine.dimension
    val time = engine.renderElapsed
    dimension.glitchPortal?.let { drawDimensionPortal(engine, it, shakeX, shakeY, time, roles) }
    if (dimension.layer == WorldLayer.BASE) return
    val halos = dimension.halos
    val slow = engine.speed < dimension.pace
    for (index in halos.indices) {
        val halo = halos[index]
        val center = world(engine, halo.x, halo.y, shakeX, shakeY)
        if (!isOnScreen(center, HALO_EXTENT)) continue
        // The course ring after the next one waits in grey; every other ring shows its Grace.
        val following = !dimension.completed && halo.courseIndex >= 0 && halo.courseIndex == dimension.progress + 1
        if (following) drawCircle(Kk.Mute.copy(alpha = 0.7f), HALO_RADIUS, center, style = kkStroke(3f)) else drawHalo(center, roles)
        if (halo.id == dimension.nextHaloId) drawNextHaloRing(center, time, slow, roles)
    }
    val embers = dimension.embers
    for (index in embers.indices) {
        val ember = embers[index]
        val center = world(engine, ember.x, ember.y, shakeX, shakeY)
        if (!isOnScreen(center, EMBER_GLOW_RADIUS + 2f)) continue
        val alpha = min(1f, ember.remaining / EMBER_FADE_SECONDS).coerceAtLeast(0f)
        drawCircle(roles.heat.copy(alpha = 0.25f * alpha), EMBER_GLOW_RADIUS, center, style = kkStroke(2f))
        rotate(45f, center) {
            drawRect(roles.heat.copy(alpha = alpha), Offset(center.x - EMBER_SIDE * 0.5f, center.y - EMBER_SIDE * 0.5f), Size(EMBER_SIDE, EMBER_SIDE))
        }
    }
    dimension.exitPortal?.let { drawDimensionPortal(engine, it, shakeX, shakeY, time, roles) }
}

private fun DrawScope.drawDimensionPortal(
    engine: GameplayRenderModel,
    portal: PortalProjection,
    shakeX: Float,
    shakeY: Float,
    time: Float,
    roles: KkRolePalette,
) {
    val center = world(engine, portal.x, portal.y, shakeX, shakeY)
    if (isOnScreen(center, PORTAL_EXTENT)) drawPortal(center, portal.target, portal.locked, portal.glitch, time, roles)
}

/** A halo ring: a faint shield disc in a shield ring with eight blunt bone ticks outside it. */
private fun DrawScope.drawHalo(center: Offset, roles: KkRolePalette) {
    drawCircle(roles.shield.copy(alpha = 0.05f), HALO_RADIUS, center)
    drawCircle(roles.shield.copy(alpha = 0.9f), HALO_RADIUS, center, style = kkStroke(3f))
    val tick = Kk.Bone.copy(alpha = 0.5f)
    for (index in 0 until HALO_TICKS) {
        val dx = HaloTickDirections[index * 2]
        val dy = HaloTickDirections[index * 2 + 1]
        drawLine(tick, Offset(center.x + dx * HALO_TICK_FROM, center.y + dy * HALO_TICK_FROM),
            Offset(center.x + dx * HALO_TICK_TO, center.y + dy * HALO_TICK_TO), 2f)
    }
}

private const val HALO_TICKS = 8
private val HaloTickDirections = FloatArray(HALO_TICKS * 2) { index ->
    val angle = (index / 2) * TWO_PI / HALO_TICKS
    if (index % 2 == 0) cos(angle) else sin(angle)
}

/**
 * The ring to fly through next: a dashed ring in the player's colour turning at 15° per second, grey with hatched dashes
 * while the Core flies below the layer's pace (a slow pass would not count).
 */
private fun DrawScope.drawNextHaloRing(center: Offset, time: Float, slow: Boolean, roles: KkRolePalette) {
    rotate(time * 15f, center) {
        if (slow) {
            drawCircle(kkFillBrush(KkFillKind.HATCH, Kk.Mute), HALO_NEXT_RADIUS, center, style = DimensionStrokes.hatchedRing)
            drawCircle(Kk.Mute, HALO_NEXT_RADIUS, center, style = DimensionStrokes.ring)
        } else {
            drawCircle(roles.you, HALO_NEXT_RADIUS, center, style = DimensionStrokes.ring)
        }
    }
}

/**
 * One rift at [center] leading to [target]: an ink disc, three dashed rings in the target's accent turning at their own
 * slow rates, a bone rim and the target's sigil. A [locked] rift is hatched with grey rings; a [glitch] rift doubles its
 * rings in bone either side, standing off in 4 Hz steps. The site renderer's window rifts draw through it too.
 */
internal fun DrawScope.drawPortal(center: Offset, target: WorldLayer, locked: Boolean, glitch: Boolean, time: Float, roles: KkRolePalette) {
    val accent = riftAccent(target, roles)
    val rings = if (locked) Kk.Mute else accent
    drawCircle(Kk.Ink.copy(alpha = 0.92f), PORTAL_DISC_RADIUS, center)
    if (locked) drawCircle(kkFillBrush(KkFillKind.HATCH, Kk.Mute), PORTAL_DISC_RADIUS, center, alpha = 0.5f)
    if (glitch) {
        val shift = portalGlitchOffset(time)
        drawPortalRings(Offset(center.x - shift, center.y), Kk.Bone.copy(alpha = 0.5f), time)
        drawPortalRings(center, rings, time)
        drawPortalRings(Offset(center.x + shift, center.y), Kk.Bone.copy(alpha = 0.5f), time)
    } else {
        drawPortalRings(center, rings, time)
    }
    drawCircle(Kk.Bone.copy(alpha = 0.8f), PORTAL_RIM_RADIUS, center, style = kkStroke(2f))
    drawDimensionSigil(target, center, PORTAL_SIGIL_RADIUS, accent)
}

private fun DrawScope.drawPortalRings(center: Offset, color: Color, time: Float) {
    for (index in PortalRingRadii.indices) {
        rotate(time * PortalRingTurns[index], center) {
            drawCircle(color, PortalRingRadii[index], center, style = DimensionStrokes.ring)
        }
    }
}

/** How far a glitch rift's ghost rings stand off at [time]: a stateless step every quarter second, 40 % to all of the shift. */
internal fun portalGlitchOffset(time: Float): Float = PORTAL_GLITCH_SHIFT * (0.4f + 0.6f * dimensionUnit(floor(time * 4f).toInt(), GLITCH_SALT))

private const val GLITCH_SALT = 11

/** Edge markers of the exit and glitch rifts and the next halo, after the event signal. */
internal fun DrawScope.collectDimensionEdgeMarkers(engine: GameplayRenderModel, batch: EdgeMarkerBatch) {
    val dimension = engine.dimension
    dimension.glitchPortal?.let { collectDimensionEdgeMarker(engine, it.x, it.y, EdgeMarkerIcon.PORTAL, batch) }
    // The exit rift and the course belong to a visit; realspace never shows them.
    if (dimension.layer == WorldLayer.BASE) return
    dimension.exitPortal?.let { collectDimensionEdgeMarker(engine, it.x, it.y, EdgeMarkerIcon.PORTAL, batch) }
    if (dimension.nextHaloId < 0) return
    val halos = dimension.halos
    for (index in halos.indices) {
        val halo = halos[index]
        if (halo.id == dimension.nextHaloId) collectDimensionEdgeMarker(engine, halo.x, halo.y, EdgeMarkerIcon.HALO, batch)
    }
}

/**
 * A rift or ring whose centre lies off screen in the unshaken view gets its marker; like a site's ring it reaches well
 * past its centre, so a ring still partly in view shares the edge with its marker.
 */
private fun DrawScope.collectDimensionEdgeMarker(engine: GameplayRenderModel, x: Float, y: Float, icon: EdgeMarkerIcon, batch: EdgeMarkerBatch) {
    val target = world(engine, x, y, 0f, 0f)
    if (markShown(target, 0f, WorldOverlayScratch.keepOut, engine.worldScale)) return
    val dx = x - engine.coreX
    val dy = y - engine.coreY
    batch.add(target.x * engine.worldScale, target.y * engine.worldScale, sqrt(dx * dx + dy * dy), icon)
}

/** The next halo's edge glyph: a ring with four blunt ticks outside it (no arrow). */
internal fun DrawScope.drawHaloEdgeGlyph(center: Offset, size: Float, color: Color) {
    val radius = size * 0.3f
    val width = max(1f, size * 0.09f)
    drawCircle(color, radius, center, style = kkStroke(width))
    for (index in 0 until 4) {
        val dx = HaloTickDirections[(index * 2 + 1) * 2]
        val dy = HaloTickDirections[(index * 2 + 1) * 2 + 1]
        drawLine(color, Offset(center.x + dx * (radius + size * 0.07f), center.y + dy * (radius + size * 0.07f)),
            Offset(center.x + dx * (radius + size * 0.19f), center.y + dy * (radius + size * 0.19f)), width)
    }
}

/** Halos, embers and rifts on the minimap, inside its clip, after the structures. */
internal fun DrawScope.drawDimensionMinimapMarks(
    engine: GameplayRenderModel,
    centerX: Float,
    centerY: Float,
    half: Float,
    scale: Float,
    dot: Float,
    roles: KkRolePalette,
) {
    val dimension = engine.dimension
    val coreX = engine.renderCoreX
    val coreY = engine.renderCoreY
    dimension.glitchPortal?.let { drawMinimapRift(it, centerX, centerY, half, scale, dot, coreX, coreY, roles) }
    // Rings, embers and the exit rift belong to a visit; realspace never shows them.
    if (dimension.layer == WorldLayer.BASE) return
    val halos = dimension.halos
    for (index in halos.indices) {
        val dx = (halos[index].x - coreX) * scale
        val dy = (halos[index].y - coreY) * scale
        if (abs(dx) > half || abs(dy) > half) continue
        drawCircle(roles.shield, dot * 1.9f, Offset(centerX + dx, centerY + dy), style = kkStroke(1f))
    }
    val embers = dimension.embers
    for (index in embers.indices) {
        val dx = (embers[index].x - coreX) * scale
        val dy = (embers[index].y - coreY) * scale
        if (abs(dx) > half || abs(dy) > half) continue
        drawCircle(roles.heat, dot * 0.95f, Offset(centerX + dx, centerY + dy))
    }
    dimension.exitPortal?.let { drawMinimapRift(it, centerX, centerY, half, scale, dot, coreX, coreY, roles) }
}

/** A rift as an outline square in its target's accent, pinned inside the rim when beyond the range (as window rifts are). */
private fun DrawScope.drawMinimapRift(
    portal: PortalProjection,
    centerX: Float,
    centerY: Float,
    half: Float,
    scale: Float,
    dot: Float,
    coreX: Float,
    coreY: Float,
    roles: KkRolePalette,
) {
    val size = dot * 2.2f
    val at = pinned(centerX, centerY, half, (portal.x - coreX) * scale, (portal.y - coreY) * scale, size)
    drawRect(riftAccent(portal.target, roles), Offset(at.x - size * 0.5f, at.y - size * 0.5f), Size(size, size), style = kkStroke(1f))
}

/** A stateless 32-bit hash of a mote cell of [layer] (any ordinal). */
internal fun dimensionCellHash(cellX: Int, cellY: Int, layer: Int): Int {
    var mixed = cellX * -0x61c88647 + cellY * 0x27d4eb2d + layer * 0x165667b1
    mixed = (mixed xor (mixed ushr 16)) * -0x7a143595
    mixed = (mixed xor (mixed ushr 13)) * -0x3d4d51cb
    return mixed xor (mixed ushr 16)
}

/** A stateless hash of [hash] and [salt] mapped to [0, 1). */
internal fun dimensionUnit(hash: Int, salt: Int): Float {
    var mixed = hash * -0x61c88647 + salt * 0x27d4eb2d
    mixed = (mixed xor (mixed ushr 15)) * 0x2c1b3c6d
    mixed = (mixed xor (mixed ushr 12)) * 0x297a2d39
    mixed = mixed xor (mixed ushr 15)
    return (mixed ushr 8) / 16_777_216f
}

/** Dashed strokes of the rift rings and the next halo's ring (created once). */
private object DimensionStrokes {
    val ring: Stroke by lazy { Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))) }
    val hatchedRing: Stroke by lazy { Stroke(7f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))) }
}

/** Half the opening of a Zenith light wedge: narrow enough that ink keeps its share of the screen. */
private const val RAY_HALF_ANGLE_DEGREES = 3.5f

/** Cached object-local paths: the Zenith's light wedge (per screen size) and the Nadir's three crack shapes. */
private object DimensionPaths {
    private val wedge = Path()
    private var wedgeWidth = Float.NaN
    private var wedgeHeight = Float.NaN
    private val cracks = arrayOfNulls<Path>(3)

    /** A narrow wedge opening downward from the origin, long enough to cross the screen from above it. */
    fun rayWedge(width: Float, height: Float): Path {
        if (width == wedgeWidth && height == wedgeHeight) return wedge
        wedgeWidth = width
        wedgeHeight = height
        val length = max(width, height) * 1.6f
        val spread = sin(RAY_HALF_ANGLE_DEGREES * PI.toFloat() / 180f) * length
        wedge.rewind()
        wedge.moveTo(0f, 0f)
        wedge.lineTo(spread, length)
        wedge.lineTo(-spread, length)
        wedge.close()
        return wedge
    }

    /** Crack [variant] (0..2): open polylines turning only at obtuse angles, around the origin. */
    fun crack(variant: Int): Path = cracks[variant] ?: Path().also { path ->
        val lines = CrackLines[variant]
        for (line in lines) {
            path.moveTo(line[0], line[1])
            for (point in 2 until line.size step 2) path.lineTo(line[point], line[point + 1])
        }
        cracks[variant] = path
    }
}

/** The crack shapes' polylines (x, y pairs; a branch is its own line). */
internal val CrackLines: List<List<FloatArray>> = listOf(
    listOf(floatArrayOf(-40f, -10f, -12f, 4f, 14f, -2f, 42f, 12f), floatArrayOf(14f, -2f, 26f, -22f)),
    listOf(floatArrayOf(-36f, 18f, -8f, 6f, 6f, -16f, 34f, -26f), floatArrayOf(-8f, 6f, 10f, 20f)),
    listOf(floatArrayOf(-44f, 0f, -16f, -8f, 8f, 6f, 30f, 0f, 46f, 14f)),
)
