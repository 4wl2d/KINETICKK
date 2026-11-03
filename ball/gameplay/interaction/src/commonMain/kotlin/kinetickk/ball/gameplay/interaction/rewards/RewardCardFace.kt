// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.gameplay.interaction.canvas.look.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.look.overlayIcon
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.drawKkCardBack
import kinetickk.foundation.design.drawKkCardFrame
import kinetickk.foundation.design.drawKkCardFront
import kinetickk.foundation.design.monoStyle
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.rewards.art.*

/** Card width (dp) below which the band's trailing value moves into the body. */
internal fun rewardCardIsNarrow(widthDp: Float): Boolean = widthDp < 200f

/**
 * The reward card at the canvas size, in its two channels: the rarity frame (the material's border,
 * band, echo, glow and effects) and the type face every card text sits on (the hue's pattern,
 * ornament, watermark, type pips and art). [look] (memoized per presentation and palette) carries the
 * style and the art; [artCache] keeps the art's geometry per card. Allocation-free for a steady card.
 */
internal fun DrawScope.drawRewardCardFace(
    measurer: CanvasTextMeasurer,
    presentation: RewardCardPresentation,
    @Suppress("UNUSED_PARAMETER") accent: Color,
    time: Float,
    selection: Float,
    burst: Float,
    paths: KkPathCache = RewardFacePaths,
    phoneDeal: Boolean = false,
    look: RewardCardLook = rewardCardLook(presentation, measurer.roles),
    artCache: RewardArtCache = RewardFaceArtCache,
    /** The bottom of the card's text in px (the face plates measure it); NaN for the default layout. */
    textBottom: Float = Float.NaN,
    /** The top of the card's footer in px, NaN when it has none (or for the default layout). */
    footerTop: Float = Float.NaN,
    /** Draw the rarity frame too; a caller that lays plates over the face draws it after them ([drawKkCardFrame]). */
    frame: Boolean = true,
) {
    val spec = RewardFaceSpecs.of(size.width / density, size.height / density, phoneDeal)
    val bandHeight = spec.bandHeight * spec.u
    val bandEnd = if (rewardCardIsNarrow(size.width / density)) null else presentation.bandEnd
    val bounds = RewardFaceSpecs.bounds(size.width, size.height)
    val style = look.style
    drawKkCardBack(bounds, time, selection, style, textBottom)
    look.art?.let { art -> drawRewardCardArt(art, rewardArtFrame(artCache, look, measurer.roles, spec, textBottom, footerTop), time) }
    // The text column's left edge and the text's bottom: ornaments and facets keep clear of the text.
    drawKkCardFront(measurer, bounds, time, selection, presentation.bandStart, bandEnd, bandHeight, burst, style,
        textLeft = spec.start * spec.u * density, textBottom = textBottom)
    if (presentation.elements.isNotEmpty() || presentation.item != null) drawBandElementPips(measurer, presentation, bandEnd, bandHeight, spec.compact)
    when {
        presentation.reborn || presentation.heirloom -> drawGoldRim(paths, bounds, presentation.rebirthTier, spec.compact, measurer.roles.gold.base)
        presentation.decline -> {
            // A calm card: no effects, a dashed inner outline in the line color ([rewardDeclineOutlineLeftDp]).
            val inset = RewardDeclineInsetDp * density
            val outline = paths.slab(1, inset, bandHeight * density + inset, size.width - inset, size.height - inset, RewardDeclineCutDp * density)
            drawPath(outline, Kk.Line2, style = RewardCardStrokes.dashed(1.5f * density, density))
        }
    }
    if (frame) drawKkCardFrame(bounds, time, style, burst)
}

/**
 * The art frame of [look] at this card's size and text, rebuilt only when the size, look, palette or
 * the text's bottom ([textBottom], whole px) changes. The clear region starts below the text (never
 * above the default share of the face) and keeps at least [RewardArtMinClear] board dp; a card whose
 * text leaves less than that is crowded, and its art moves to the room that is left.
 */
private fun DrawScope.rewardArtFrame(
    cache: RewardArtCache,
    look: RewardCardLook,
    roles: KkRolePalette,
    spec: RewardCardSpec,
    textBottom: Float,
    footerTop: Float,
): RewardArtFrame {
    val text = if (textBottom.isNaN()) Float.NaN else kotlin.math.round(textBottom)
    val footer = if (footerTop.isNaN()) Float.NaN else kotlin.math.round(footerTop)
    cache.current(look, roles, size.width, size.height, density, spec.compact, text, footer)?.let { return it }
    val unit = spec.u * density
    val w = size.width
    val h = size.height
    // Above the footer when the card has one; a measured card without one lends the art its footer room.
    val bottom = when {
        !footer.isNaN() -> footer - 4f * unit
        !text.isNaN() -> h - (spec.bodyBottom + 4f) * unit
        else -> h - (spec.bodyBottom + spec.footer) * unit
    }
    val defaultTop = h * (if (spec.compact) 0.62f else 0.56f)
    val floor = if (text.isNaN()) defaultTop else text + RewardArtTextGap * unit
    val minClear = RewardArtMinClear * unit * (if (spec.compact) 0.7f else 1f)
    val clearTop = min(max(defaultTop, floor), bottom - minClear)
    return cache.store(
        RewardArtFrame(
            bounds = Rect(0f, 0f, w, h),
            face = Rect(0f, spec.bandHeight * unit, w, h),
            clear = Rect(spec.start * unit, clearTop, w - spec.end * unit, max(bottom, clearTop + 24f * unit)),
            textFloor = if (text.isNaN()) clearTop else floor,
            crowded = floor > bottom - minClear,
            textBottom = text,
            footerTop = footer,
            icon = Rect(spec.plateX * unit, spec.plateY * unit, (spec.plateX + spec.plateSize) * unit, (spec.plateY + spec.plateSize) * unit),
            hue = look.hue,
            secondHue = look.secondHue,
            roles = roles,
            density = density,
            seed = look.style.seed,
            compact = spec.compact,
            unit = unit,
            cache = cache,
            look = look,
        ),
    )
}

/** The gap (board dp) the art keeps below the last text row. */
private const val RewardArtTextGap = 8f

/** The least height (board dp) of the art's clear region. */
private const val RewardArtMinClear = 72f

/** Card specs and face bounds of the face canvases drawn this frame, so a steady face allocates none. */
internal object RewardFaceSpecs {
    private const val CAPACITY = 8
    private val keys = FloatArray(CAPACITY * 3) { Float.NaN }
    private val specs = arrayOfNulls<RewardCardSpec>(CAPACITY)
    private var next = 0
    private val boundKeys = FloatArray(CAPACITY * 2) { Float.NaN }
    private val rects = arrayOfNulls<Rect>(CAPACITY)
    private var nextRect = 0

    fun of(widthDp: Float, heightDp: Float, phoneDeal: Boolean): RewardCardSpec {
        val deal = if (phoneDeal) 1f else 0f
        for (index in 0 until CAPACITY) {
            val base = index * 3
            if (keys[base] == widthDp && keys[base + 1] == heightDp && keys[base + 2] == deal) return specs[index]!!
        }
        val spec = rewardCardSpec(widthDp, heightDp, 1f, phoneDeal)
        val base = next * 3
        keys[base] = widthDp
        keys[base + 1] = heightDp
        keys[base + 2] = deal
        specs[next] = spec
        next = (next + 1) % CAPACITY
        return spec
    }

    fun bounds(width: Float, height: Float): Rect {
        for (index in 0 until CAPACITY) {
            if (boundKeys[index * 2] == width && boundKeys[index * 2 + 1] == height) return rects[index]!!
        }
        val rect = Rect(0f, 0f, width, height)
        boundKeys[nextRect * 2] = width
        boundKeys[nextRect * 2 + 1] = height
        rects[nextRect] = rect
        nextRect = (nextRect + 1) % CAPACITY
        return rect
    }
}

/** Art cache for faces drawn outside a card composable (tests, previews). Draw-thread confined. */
private val RewardFaceArtCache = RewardArtCache()

/**
 * Type pips on the band's trailing end, before the stack count: a diamond per element with its
 * glyph, or one volt Kinetic pip for an item without an element (Kinetic is a type like the others).
 */
private fun DrawScope.drawBandElementPips(
    measurer: CanvasTextMeasurer,
    presentation: RewardCardPresentation,
    bandEnd: String?,
    bandHeight: Float,
    compact: Boolean,
) {
    val band = bandHeight * density
    val endWidth = bandEnd?.let { measureKkText(measurer, it, measurer.typography.monoStyle(9f, weight = FontWeight.Bold), uppercase = true).size.width.toFloat() } ?: 0f
    val half = (if (compact) 7f else 9f) * density
    val pitch = half * 1.7f
    var cx = size.width - 22f * density - endWidth - (if (bandEnd != null) 12f * density else 0f) - half
    val cy = band * 0.5f
    val count = max(presentation.elements.size, 1)
    for (index in count - 1 downTo 0) {
        val element = presentation.elements.getOrNull(index)
        val hue = element?.overlayColor(measurer.roles) ?: measurer.roles.kinetic
        rotate(45f, Offset(cx, cy)) {
            val side = half * 1.414f
            drawRect(Kk.Ink, Offset(cx - side * 0.5f - density, cy - side * 0.5f - density), Size(side + 2f * density, side + 2f * density))
            drawRect(hue, Offset(cx - side * 0.5f, cy - side * 0.5f), Size(side, side))
        }
        drawKkIcon(element?.overlayIcon() ?: KkIcon.ASPECT_KINETIC, Offset(cx, cy), half * 1.15f, Kk.Ink, strokeWidth = 2.6f)
        cx -= pitch
    }
}

/**
 * Gold rim of a Reborn or Heirloom card, inside the rarity frame (which keeps the card's rarity),
 * with one ring mark per Rebirth tier along the bottom edge.
 */
private fun DrawScope.drawGoldRim(paths: KkPathCache, bounds: Rect, tier: Int, compact: Boolean, gold: Color) {
    val cut = RewardCardCutDp * density
    val k = if (compact) 0.75f else 1f
    val first = 6.5f * k * density
    val outer = paths.slab(2, bounds.left + first, bounds.top + first, bounds.right - first, bounds.bottom - first, cut - first * 0.1f)
    drawPath(outer, gold, style = kkStroke(2f * density))
    val inset = first + 4f * k * density
    val inner = paths.slab(3, bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset, cut - inset * 0.1f)
    drawPath(inner, gold, alpha = 0.35f, style = kkStroke(density))
    val marks = tier.coerceIn(0, 10)
    if (marks == 0) return
    val radius = (if (compact) 2.4f else 3f) * density
    val pitch = radius * 3.2f
    val y = bounds.bottom - first - 2f * k * density
    var x = bounds.center.x - (marks - 1) * pitch * 0.5f
    repeat(marks) {
        drawCircle(Kk.Ink2, radius + 1.5f * density, Offset(x, y))
        drawCircle(gold, radius, Offset(x, y), style = kkStroke(1.3f * density))
        x += pitch
    }
}

/** Shared face paths for faces drawn outside a card composable (tests, previews). Draw-thread confined. */
private val RewardFacePaths = KkPathCache(4)

private object RewardCardStrokes {
    private var width = -1f
    private var stroke: Stroke? = null

    fun dashed(strokeWidth: Float, density: Float): Stroke {
        stroke?.takeIf { width == strokeWidth }?.let { return it }
        width = strokeWidth
        return Stroke(strokeWidth, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * density, 5f * density)))
            .also { stroke = it }
    }
}

/** The card face cut (`.card` clip-path, 22 dp). */
internal const val RewardCardCutDp = 22f

/** Air (dp) between the body's lowest line and the card frame's inner edge. */
internal const val RewardBodyFrameClearanceDp = 2f

/**
 * Width (dp) over which the face plates fade out around the text: about three times the largest
 * card spark (7 dp), so a spark dims as a whole diamond and no spark, halftone dot or sheen band
 * ever ends at an edge.
 */
internal const val RewardPlateFadeDp = 20f

/** How much of the card effects the face plates hide under the text (the rest shows through). */
internal const val RewardPlateOpacity = 0.75f

/** Composites the plates as one layer, so overlapping rings never add up to a darker band. */
private val RewardPlateLayer by lazy { Paint().apply { alpha = RewardPlateOpacity } }

/**
 * The text rows of one card: the face canvas draws a soft plate under each. The plates' geometry and
 * edge brushes are built once per layout ([prepare]) and only drawn per frame, so an animating card
 * allocates nothing for them.
 */
internal class RewardFacePlates {
    var card: LayoutCoordinates? = null
    var viewport: LayoutCoordinates? = null
    val rows = LinkedHashMap<String, LayoutCoordinates>()
    private val positions = HashMap<String, Offset>()

    /** Bumped when a row moves (layout, scroll), so the face canvas redraws its plates. */
    var version by mutableIntStateOf(0)

    /** The bottom (card px) of the card's text, as if unscrolled and cut at the viewport; NaN until measured. */
    var textBottom = Float.NaN
        private set

    /** The top (card px) of the card's footer, NaN when it has none. */
    var footerTop = Float.NaN
        private set

    private var builtVersion = -1
    private var builtFace = Color.Unspecified
    private var builtWidth = -1f
    private var builtHeight = -1f
    private var count = 0
    private var cores = FloatArray(4 * 8)
    private var edges = arrayOfNulls<Brush>(8 * 8)
    private var built = arrayOfNulls<LayoutCoordinates>(8)

    fun place(key: String, coordinates: LayoutCoordinates) {
        rows[key] = coordinates
        val position = coordinates.positionInRoot()
        if (positions.put(key, position) != position) version++
    }

    /**
     * Measures the rows against the card and the text viewport and builds the plates in [face], when
     * a row moved, the canvas size or the face colour changed, or a row left the layout. [scroll] is
     * the body's scroll offset in px (the text bottom ignores it, so scrolling never moves the art).
     */
    fun prepare(scope: DrawScope, face: Color, scroll: Float) {
        val stamp = version
        val width = scope.size.width
        val height = scope.size.height
        if (stamp == builtVersion && face == builtFace && width == builtWidth && height == builtHeight && allAttached()) return
        val card = card?.takeIf { it.isAttached } ?: return
        val viewport = viewport?.takeIf { it.isAttached }?.let { card.localBoundingBoxOf(it, clipBounds = false) } ?: return
        val measured = ArrayList<Triple<String, Rect, LayoutCoordinates?>>(rows.size)
        val iterator = rows.entries.iterator()
        while (iterator.hasNext()) {
            val (key, row) = iterator.next()
            if (!row.isAttached) {
                iterator.remove()
                positions.remove(key)
                continue
            }
            measured += Triple(key, card.localBoundingBoxOf(row, clipBounds = false), row)
        }
        build(scope, face, scroll, viewport, measured, stamp)
    }

    /**
     * Builds the plates from the rows' bounds in card px (key, bounds, the row's coordinates), cut
     * at [viewport]; a row that did not move keeps its brushes. [stamp] is the [version] they belong to.
     */
    internal fun build(scope: DrawScope, face: Color, scroll: Float, viewport: Rect, measured: List<Triple<String, Rect, LayoutCoordinates?>>, stamp: Int = version) {
        val fade = with(scope) { RewardPlateFadeDp.dp.toPx() }
        val padX = with(scope) { 2.dp.toPx() }
        val padY = with(scope) { 1.dp.toPx() }
        val previous = cores.copyOf(count * 4)
        val sameFace = face == builtFace
        var next = 0
        var bottom = Float.NaN
        var footer = Float.NaN
        for ((key, bounds, row) in measured) {
            if (key == "footer") footer = bounds.top
            if (key != "chips" && key != "footer") {
                val unscrolled = min(bounds.bottom + scroll, viewport.bottom)
                bottom = if (bottom.isNaN()) unscrolled else max(bottom, unscrolled)
            }
            val core = Rect(bounds.left - padX, bounds.top - padY, bounds.right + padX, bounds.bottom + padY).intersect(viewport)
            if (core.width <= 0f || core.height <= 0f) continue
            ensure(next + 1)
            val base = next * 4
            cores[base] = core.left
            cores[base + 1] = core.top
            cores[base + 2] = core.right
            cores[base + 3] = core.bottom
            built[next] = row
            // A row that did not move keeps its brushes.
            val kept = sameFace && base + 3 < previous.size && previous[base] == core.left && previous[base + 1] == core.top &&
                previous[base + 2] == core.right && previous[base + 3] == core.bottom && edges[next * 8] != null
            if (!kept) softEdges(core, face, fade, next * 8)
            next++
        }
        for (index in next until built.size) built[index] = null
        count = next
        textBottom = bottom
        footerTop = footer
        builtVersion = stamp
        builtFace = face
        builtWidth = scope.size.width
        builtHeight = scope.size.height
    }

    private fun allAttached(): Boolean {
        for (index in 0 until count) {
            val row = built[index]
            if (row != null && !row.isAttached) return false
        }
        return true
    }

    private fun ensure(rows: Int) {
        if (rows <= built.size) return
        val capacity = max(rows, built.size * 2)
        cores = cores.copyOf(capacity * 4)
        edges = edges.copyOf(capacity * 8)
        built = built.copyOf(capacity)
    }

    /** The four edge and four corner fades of [core] from [color] to clear over [fade] px. */
    private fun softEdges(core: Rect, color: Color, fade: Float, at: Int) {
        val stops = arrayOf(0f to color, 1f to color.copy(alpha = 0f))
        edges[at] = Brush.verticalGradient(*stops, startY = core.top, endY = core.top - fade)
        edges[at + 1] = Brush.verticalGradient(*stops, startY = core.bottom, endY = core.bottom + fade)
        edges[at + 2] = Brush.horizontalGradient(*stops, startX = core.left, endX = core.left - fade)
        edges[at + 3] = Brush.horizontalGradient(*stops, startX = core.right, endX = core.right + fade)
        edges[at + 4] = Brush.radialGradient(*stops, center = core.topLeft, radius = fade)
        edges[at + 5] = Brush.radialGradient(*stops, center = core.topRight, radius = fade)
        edges[at + 6] = Brush.radialGradient(*stops, center = core.bottomLeft, radius = fade)
        edges[at + 7] = Brush.radialGradient(*stops, center = core.bottomRight, radius = fade)
    }

    /** Draws the prepared plates (allocation-free). */
    fun draw(scope: DrawScope) = with(scope) {
        val fade = RewardPlateFadeDp.dp.toPx()
        for (index in 0 until count) {
            val base = index * 4
            val left = cores[base]
            val top = cores[base + 1]
            val right = cores[base + 2]
            val bottom = cores[base + 3]
            val width = right - left
            val height = bottom - top
            val e = index * 8
            drawRect(builtFace, Offset(left, top), Size(width, height))
            drawRect(edges[e]!!, Offset(left, top - fade), Size(width, fade))
            drawRect(edges[e + 1]!!, Offset(left, bottom), Size(width, fade))
            drawRect(edges[e + 2]!!, Offset(left - fade, top), Size(fade, height))
            drawRect(edges[e + 3]!!, Offset(right, top), Size(fade, height))
            drawRect(edges[e + 4]!!, Offset(left - fade, top - fade), Size(fade, fade))
            drawRect(edges[e + 5]!!, Offset(right, top - fade), Size(fade, fade))
            drawRect(edges[e + 6]!!, Offset(left - fade, bottom), Size(fade, fade))
            drawRect(edges[e + 7]!!, Offset(right, bottom), Size(fade, fade))
        }
    }
}

/** Registers this text row with the card's soft face plates. */
internal fun Modifier.facePlate(plates: RewardFacePlates?, key: String): Modifier =
    if (plates == null) this else onPlaced { plates.place(key, it) }

/**
 * Soft plates of the card face color under every registered text row (built by
 * [RewardFacePlates.prepare]), clipped to the card face ([facePath]) and to the text viewport: solid
 * under the row (plus 2 dp), fading to nothing over [RewardPlateFadeDp] around it, composited as one
 * layer at [RewardPlateOpacity].
 */
internal fun DrawScope.drawRewardFacePlates(plates: RewardFacePlates, facePath: Path) {
    plates.version
    clipPath(facePath) {
        drawContext.canvas.saveLayer(RewardFaceSpecs.bounds(size.width, size.height), RewardPlateLayer)
        plates.draw(this)
        drawContext.canvas.restore()
    }
}
