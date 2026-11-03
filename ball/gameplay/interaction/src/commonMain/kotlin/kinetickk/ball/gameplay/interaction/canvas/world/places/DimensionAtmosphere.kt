// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkLayerHues
import kinetickk.foundation.design.KkRebirthTiers
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.drawKkRadialHalftone
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.positiveModulo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/*
 * The dimensions' atmosphere: each layer paints its own sky over a dark ground instead of the realspace grid (the
 * Zenith's heaven, the Nadir's hell, the Archive's corrupted signal; ZenithAtmosphere.kt, NadirAtmosphere.kt and
 * ArchiveAtmosphere.kt), then its weather drifts through the world layer under every mark that moves (feathers, ember
 * rain, glyph shards and datamosh), budgeted by [FxPool.ATMOSPHERE] so a crowded frame thins it first. Colours come
 * from the layer hues of `:foundation:design` (decorative, never a role) and the role palette for marks; every glow is
 * an object-local disc scaled into place and every path is cached, so a frame allocates nothing.
 */

/** A dimension's backdrop colours for one rebirth tier and Color vision palette. */
internal class DimensionTheme(
    val ground: Color,
    /** The layer's light: the Zenith's radiance, the Nadir's crimson, the Archive's signal glow. */
    val glow: Color,
    /** Its second light: the Zenith's haze, the Nadir's magma, the Archive's glitch. */
    val glowAlt: Color,
    val grid: Color,
    /** Every fourth grid line (the Archive's; the other layers draw every line alike). */
    val gridMajor: Color,
    val halftone: Color,
    val mote: Color,
    val moteAlt: Color,
    /** The Zenith's light shafts. */
    val ray: Color,
    /** The Nadir's cracks: their magma core, its glow and the soot drifting over them. */
    val crack: Color,
    val crackGlow: Color,
    val smoke: Color,
    /** The Archive's scanlines and its falling data. */
    val scanline: Color,
    val rain: Color,
    /** The brightest touch of the ground: the Zenith's gold floor glow, the Archive's data heads. */
    val highlight: Color,
    /** The sky's wide light: the Zenith's dawn above the screen. */
    val sky: Color,
)

/**
 * Dimension themes by layer, tier and palette (identity): the layer's hues over its dark ground, the tier's arena grid
 * alpha raised a little, and the Event Horizon tier in black and white. Built once per key, never per frame.
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
        fun hue(color: Color): Color = if (horizon) color.luminanceShare().let { Color(it, it, it, color.alpha) } else color
        fun ground(color: Color): Color = if (horizon) kkMix(Kk.Ink, Color.White, 0.03f) else color
        val none = Color.Transparent
        return when (layer) {
            WorldLayer.ZENITH -> {
                val radiance = hue(KkLayerHues.Radiance)
                val gold = hue(KkLayerHues.HaloGold)
                val grid = kkMix(gold, radiance, 0.5f).copy(alpha = arena)
                DimensionTheme(
                    ground = ground(KkLayerHues.ZenithGround), glow = radiance, glowAlt = hue(KkLayerHues.Haze),
                    grid = grid, gridMajor = grid, halftone = gold.copy(alpha = 0.08f), mote = radiance.copy(alpha = 0.42f),
                    moteAlt = gold.copy(alpha = 0.34f), ray = radiance, crack = none, crackGlow = none, smoke = none,
                    scanline = none, rain = none, highlight = gold, sky = hue(KkLayerHues.Dawn),
                )
            }
            WorldLayer.NADIR -> {
                val magma = hue(KkLayerHues.Magma)
                val grid = hue(KkLayerHues.Crimson).copy(alpha = arena + 0.05f)
                DimensionTheme(
                    ground = ground(KkLayerHues.NadirGround), glow = hue(KkLayerHues.Crimson), glowAlt = magma, grid = grid,
                    gridMajor = grid, halftone = magma.copy(alpha = 0.05f), mote = magma.copy(alpha = 0.55f),
                    moteAlt = hue(KkLayerHues.MagmaCore).copy(alpha = 0.5f), ray = none, crack = hue(KkLayerHues.MagmaCore),
                    crackGlow = magma, smoke = if (horizon) Color.Black else KkLayerHues.Soot, scanline = none, rain = none,
                    highlight = hue(KkLayerHues.MagmaCore), sky = none,
                )
            }
            WorldLayer.ARCHIVE -> {
                val signal = hue(KkLayerHues.Signal)
                DimensionTheme(
                    ground = ground(KkLayerHues.ArchiveGround), glow = signal, glowAlt = hue(KkLayerHues.Glitch),
                    grid = signal.copy(alpha = arena + 0.02f), gridMajor = signal.copy(alpha = arena + 0.07f),
                    halftone = signal.copy(alpha = 0.05f), mote = hue(Kk.Bone).copy(alpha = 0.32f), moteAlt = signal.copy(alpha = 0.4f),
                    ray = none, crack = none, crackGlow = none, smoke = none, scanline = signal, rain = signal, highlight = hue(Kk.Bone),
                    sky = none,
                )
            }
            // Realspace never draws through a dimension theme; its own arena colours stand in.
            WorldLayer.BASE -> {
                val arenaTheme = arenaTheme(tier)
                DimensionTheme(
                    ground = Kk.Ink, glow = none, glowAlt = none, grid = arenaTheme.grid, gridMajor = arenaTheme.grid,
                    halftone = arenaTheme.halftone, mote = none, moteAlt = none, ray = none, crack = none, crackGlow = none,
                    smoke = none, scanline = none, rain = none, highlight = none, sky = none,
                )
            }
        }
    }

    /** Rough lightness of a colour (0..1), so the Event Horizon's greys keep each hue's weight. */
    private fun Color.luminanceShare(): Float = (0.3f * red + 0.59f * green + 0.11f * blue).coerceIn(0f, 1f)
}

/** The halftone disc under the Core, as realspace draws it. */
private const val HALFTONE_DIAMETER = 700f
private val DimensionHalftoneRect = Rect(-HALFTONE_DIAMETER * 0.5f, -HALFTONE_DIAMETER * 0.5f, HALFTONE_DIAMETER * 0.5f, HALFTONE_DIAMETER * 0.5f)

/**
 * The ground of a dimension, drawn instead of the realspace grid while the Core is in one: the layer's ground, its sky
 * (light, clouds, cracks, smoke or falling data), its grid, the halftone disc at the Core and the stateless motes.
 */
internal fun DrawScope.drawDimensionBackdrop(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val layer = engine.dimension.layer
    val theme = DimensionThemes.of(layer, engine.rebirthLevel, roles)
    // Painted here rather than under the backdrop, so every caller of the backdrop shows the dimension's ground.
    drawRect(theme.ground)
    when (layer) {
        WorldLayer.ZENITH -> drawZenithSky(engine, theme, shakeX, shakeY)
        WorldLayer.NADIR -> drawNadirDepths(engine, theme, shakeX, shakeY)
        WorldLayer.ARCHIVE -> drawArchiveSignal(engine, theme, shakeX, shakeY)
        WorldLayer.BASE -> Unit
    }
    drawDimensionGrid(engine, theme, shakeX, shakeY)
    val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    translate(core.x, core.y) {
        drawKkRadialHalftone(DimensionHalftoneRect, theme.halftone)
    }
    drawDimensionMotes(engine, layer, theme, shakeX, shakeY)
    if (layer == WorldLayer.ARCHIVE) drawArchiveScanlines(engine, theme)
}

/**
 * The layer's weather in the world layer, after the sites and under every mark that moves: the Zenith's feathers, the
 * Nadir's ember rain, the Archive's glyph shards and datamosh. Each kind takes its draws from [FxPool.ATMOSPHERE] and
 * is skipped whole when the frame cannot afford it; the low particle density draws none.
 */
internal fun DrawScope.drawDimensionWeather(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val layer = engine.dimension.layer
    if (layer == WorldLayer.BASE) return
    val density = dimensionWeatherDensity(engine.settings.particleDensity)
    if (density <= 0f) return
    val theme = DimensionThemes.of(layer, engine.rebirthLevel, roles)
    // A crowded screen keeps every other weather cell.
    val crowded = FxFrame.crowd > FxFrame.CROWD_SECONDARY_OFF
    when (layer) {
        WorldLayer.ZENITH -> drawZenithFeathers(engine, theme, shakeX, shakeY, density, crowded)
        WorldLayer.NADIR -> drawNadirEmberRain(engine, theme, shakeX, shakeY, density, crowded)
        WorldLayer.ARCHIVE -> drawArchiveShards(engine, theme, shakeX, shakeY, density, crowded)
        WorldLayer.BASE -> Unit
    }
}

/** Weather cells this dense hold a flake, a spark or a shard; none at the low particle density. */
internal fun dimensionWeatherDensity(density: ParticleDensity): Float = when (density) {
    ParticleDensity.LOW -> 0f
    ParticleDensity.NORMAL -> 0.7f
    ParticleDensity.HIGH -> 1f
}

/**
 * Takes [ops] draws of the atmosphere's budget for one weather element, or false when the frame cannot afford it (the
 * element is skipped, never a signature).
 */
internal fun atmosphereAffords(ops: Int): Boolean = FxDrawBudget.take(FxPool.ATMOSPHERE, ops, signature = false)

/**
 * Soft discs of unit radius by colour and falloff, object-local (centred on the origin), so one brush serves every
 * glow, cloud and smoke bank of that colour scaled and moved into place ([drawSoftDisc]). Built once per key.
 */
internal object DimensionGlows {
    private const val CAPACITY = 32
    private val colors = LongArray(CAPACITY)
    private val cores = FloatArray(CAPACITY)
    private val brushes = arrayOfNulls<Brush>(CAPACITY)
    private var next = 0

    /** A disc of [color] at its centre, still [core] of its strength at that share of the radius, clear at the edge. */
    fun of(color: Color, core: Float = 0.25f): Brush {
        val key = color.value.toLong()
        for (index in 0 until CAPACITY) {
            if (brushes[index] != null && colors[index] == key && cores[index] == core) return brushes[index]!!
        }
        val brush = Brush.radialGradient(
            0f to color, core to color.copy(alpha = color.alpha * 0.55f), 1f to color.copy(alpha = 0f),
            center = Offset.Zero, radius = 1f,
        )
        return remember(key, core, brush)
    }

    /** A vignette of unit radius: clear inside [clear] of the radius, [color] at the radius and beyond. */
    fun vignette(color: Color, clear: Float): Brush {
        val key = color.value.toLong()
        // A vignette keys its falloff negated, apart from the discs.
        for (index in 0 until CAPACITY) {
            if (brushes[index] != null && colors[index] == key && cores[index] == -clear) return brushes[index]!!
        }
        val brush = Brush.radialGradient(
            0f to color.copy(alpha = 0f), clear to color.copy(alpha = 0f), 1f to color, center = Offset.Zero, radius = 1f,
        )
        return remember(key, -clear, brush)
    }

    private fun remember(key: Long, core: Float, brush: Brush): Brush {
        colors[next] = key
        cores[next] = core
        brushes[next] = brush
        next = (next + 1) % CAPACITY
        return brush
    }
}

/** [brush] (a [DimensionGlows.vignette]) over the whole screen, its unit circle stretched to [radiusX] by [radiusY] about the centre. */
internal fun DrawScope.drawVignette(brush: Brush, radiusX: Float, radiusY: Float, alpha: Float = 1f) {
    val halfWidth = size.width * 0.5f
    val halfHeight = size.height * 0.5f
    translate(halfWidth, halfHeight) {
        scale(radiusX, radiusY, Offset.Zero) {
            drawRect(brush, Offset(-halfWidth / radiusX, -halfHeight / radiusY), Size(2f * halfWidth / radiusX, 2f * halfHeight / radiusY), alpha)
        }
    }
}

/** A soft disc of [brush] ([DimensionGlows]) centred at ([x], [y]), [radiusX] by [radiusY] wide, at [alpha]. */
internal fun DrawScope.drawSoftDisc(brush: Brush, x: Float, y: Float, radiusX: Float, radiusY: Float = radiusX, alpha: Float = 1f) {
    translate(x, y) {
        scale(radiusX, radiusY, Offset.Zero) { drawCircle(brush, 1f, Offset.Zero, alpha = alpha) }
    }
}

/** Round-capped strokes of the atmosphere and the dimension marks (created once). */
internal object AtmosphereStrokes {
    val crackBloom: Stroke by lazy { Stroke(30f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val crackGlow: Stroke by lazy { Stroke(12f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val crackBody: Stroke by lazy { Stroke(4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val crackCore: Stroke by lazy { Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val shimmer: Stroke by lazy { Stroke(36f, cap = StrokeCap.Round) }
    val feather: Stroke by lazy { Stroke(1.2f, cap = StrokeCap.Round) }
    val tear: Stroke by lazy { Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val tearCore: Stroke by lazy { Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val tearGlow: Stroke by lazy { Stroke(12f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
}

/** The world-anchored 40-unit grid in the theme; every fourth world line takes the major colour. */
private fun DrawScope.drawDimensionGrid(engine: GameplayRenderModel, theme: DimensionTheme, shakeX: Float, shakeY: Float) {
    val firstX = worldGridOffset(engine.renderCameraX, size.width, shakeX)
    val firstY = worldGridOffset(engine.renderCameraY, size.height, shakeY)
    // One colour (the Zenith and the Nadir) batches like the realspace grid; only the Archive alternates major lines.
    if (theme.gridMajor == theme.grid) {
        drawWorldGrid(Offset(firstX, firstY), theme.grid)
        return
    }
    val shiftX = size.width * 0.5f - engine.renderCameraX + shakeX
    val shiftY = size.height * 0.5f - engine.renderCameraY + shakeY
    var x = firstX - WORLD_GRID_SPACING
    var column = kotlin.math.round((x - shiftX) / WORLD_GRID_SPACING).toInt()
    while (x <= size.width) {
        if (x >= 0f) drawLine(if (column and 3 == 0) theme.gridMajor else theme.grid, Offset(x, 0f), Offset(x, size.height), 1f)
        x += WORLD_GRID_SPACING
        column++
    }
    var y = firstY - WORLD_GRID_SPACING
    var row = kotlin.math.round((y - shiftY) / WORLD_GRID_SPACING).toInt()
    while (y <= size.height) {
        if (y >= 0f) drawLine(if (row and 3 == 0) theme.gridMajor else theme.grid, Offset(0f, y), Offset(size.width, y), 1f)
        y += WORLD_GRID_SPACING
        row++
    }
}

/** The world origin on screen: world point (x, y) draws at (x + originX, y + originY). */
internal fun DrawScope.worldOriginX(engine: GameplayRenderModel, shakeX: Float): Float = size.width * 0.5f - engine.renderCameraX + shakeX

internal fun DrawScope.worldOriginY(engine: GameplayRenderModel, shakeY: Float): Float = size.height * 0.5f - engine.renderCameraY + shakeY

/** A stateless unit of [hash] for [salt] in [0, 1) (the obstacle seed's mixer, shared by every dimension look). */
internal fun cellUnit(hash: Int, salt: Int): Float = kinetickk.ball.content.api.obstacleSeedUnit(hash, salt)

/** [value] wrapped into 0 until [period]. */
internal fun wrapped(value: Float, period: Float): Float = positiveModulo(value, period)

/** A screen [size] grown by [margin] each way, for culling against a reach. */
internal fun DrawScope.inView(x: Float, y: Float, margin: Float): Boolean =
    x >= -margin && y >= -margin && x <= size.width + margin && y <= size.height + margin
