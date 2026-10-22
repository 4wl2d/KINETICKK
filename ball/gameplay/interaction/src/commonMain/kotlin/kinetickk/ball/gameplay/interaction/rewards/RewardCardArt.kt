// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.clipKkCardFace
import kinetickk.foundation.design.kkSeedFraction

/**
 * The bespoke art of the shipped epic and legendary items, keyed by canonical English item name.
 * This enum is the single name-to-scene table: [rewardCardArt] looks items up here (and later epics and
 * legendaries borrow a family scene from it) and [drawRewardCardArt] dispatches on it. Epic art is a static
 * signature illustration; legendary art ([animated]) is a looping scene that replaces the generic legendary
 * rays and sparks.
 */
internal enum class RewardCardArt(val itemName: String, val animated: Boolean = false) {
    TUNGSTEN_HEART("Tungsten Heart"),
    REDLINE_GOVERNOR("Redline Governor"),
    ARREST_ENGINE("Arrest Engine"),
    OVERCHARGED_CAPACITOR("Overcharged Capacitor"),
    GLASS_EDGE("Glass Edge"),
    LAST_STAND("Last Stand"),
    REDLINE_REACTOR("Redline Reactor"),
    DOMINO_PROTOCOL("Domino Protocol"),
    GAMBLERS_LEDGER("Gambler's Ledger"),
    PHOENIX_COIL("Phoenix Coil"),
    SHATTERGLASS("Shatterglass"),
    STORM_LATTICE("Storm Lattice"),
    DARK_STAR("Dark Star"),
    NECROTIC_EXCHANGE("Necrotic Exchange"),
    DEADEYE_RETICLE("Deadeye Reticle"),
    PRISMATIC_CORE("Prismatic Core"),
    VOLATILE_COMPOUND("Volatile Compound"),
    SOLAR_CHOIR("Solar Choir", animated = true),
    GLACIAL_HEART("Glacial Heart", animated = true),
    RESONANCE_FORK("Resonance Fork", animated = true),
}

private val ArtByName: Map<String, RewardCardArt> = RewardCardArt.entries.associateBy { it.itemName }

/**
 * Epic and legendary items without a scene of their own borrow the scene of the shipped item closest in
 * theme, keyed by canonical English item name. The face still differs: the art is drawn in the item's own
 * hues, under its own ornament and frame, so no two cards share a look.
 */
private val FamilyArtByName: Map<String, RewardCardArt> = mapOf(
    "Pile-Up Protocol" to RewardCardArt.DOMINO_PROTOCOL,
    "Gravity Breech" to RewardCardArt.DARK_STAR,
    "Frozen Moment" to RewardCardArt.SHATTERGLASS,
    "Annihilator Coil" to RewardCardArt.STORM_LATTICE,
    "Singularity Seed" to RewardCardArt.DARK_STAR,
    "Stillness Sight" to RewardCardArt.SHATTERGLASS,
    "Avalanche Protocol" to RewardCardArt.DARK_STAR,
    "Comet Drive" to RewardCardArt.TUNGSTEN_HEART,
    "Siege Ram" to RewardCardArt.ARREST_ENGINE,
    "Nitro Injector" to RewardCardArt.REDLINE_REACTOR,
    "Hard Stop" to RewardCardArt.REDLINE_GOVERNOR,
    "Usurer's Contract" to RewardCardArt.LAST_STAND,
    "Feeding Frenzy" to RewardCardArt.NECROTIC_EXCHANGE,
    "Cathedral Bell" to RewardCardArt.RESONANCE_FORK,
    "Nothing Left" to RewardCardArt.RESONANCE_FORK,
    "Perfect Carom" to RewardCardArt.GLACIAL_HEART,
    "Pyre Magazine" to RewardCardArt.SOLAR_CHOIR,
    "Last Word" to RewardCardArt.RESONANCE_FORK,
    "Cornucopia Core" to RewardCardArt.SOLAR_CHOIR,
    "Twin Chamber" to RewardCardArt.STORM_LATTICE,
    "Alchemical Core" to RewardCardArt.PRISMATIC_CORE,
    "Overture" to RewardCardArt.OVERCHARGED_CAPACITOR,
    "Encore Protocol" to RewardCardArt.VOLATILE_COMPOUND,
    "Quench Seal" to RewardCardArt.SHATTERGLASS,
    "Arc Furnace" to RewardCardArt.STORM_LATTICE,
    "Degenerate Matter" to RewardCardArt.DARK_STAR,
    "Ember Treads" to RewardCardArt.TUNGSTEN_HEART,
    "Cryogenic Bus" to RewardCardArt.STORM_LATTICE,
    "Glacier Scope" to RewardCardArt.SHATTERGLASS,
    "Glassblower's Ram" to RewardCardArt.TUNGSTEN_HEART,
    "Like Poles" to RewardCardArt.DARK_STAR,
    "Shunt Resistor" to RewardCardArt.OVERCHARGED_CAPACITOR,
    "Grave Mulch" to RewardCardArt.NECROTIC_EXCHANGE,
    "Blast Rider" to RewardCardArt.REDLINE_GOVERNOR,
    "Choir Prism" to RewardCardArt.SHATTERGLASS,
    "Wavefront Rudder" to RewardCardArt.REDLINE_GOVERNOR,
    "Fault Hammer" to RewardCardArt.ARREST_ENGINE,
    "Sapper's Wedge" to RewardCardArt.GLASS_EDGE,
    "Anechoic Shell" to RewardCardArt.LAST_STAND,
    "Brazier of Ash" to RewardCardArt.PHOENIX_COIL,
    "Hourglass Relay" to RewardCardArt.GLACIAL_HEART,
    "Horizon Knife" to RewardCardArt.RESONANCE_FORK,
    "Starving Lantern" to RewardCardArt.DARK_STAR,
    "Requiem Engine" to RewardCardArt.RESONANCE_FORK,
    "Gravity Assist" to RewardCardArt.ARREST_ENGINE,
)

/**
 * The art of [item]: its bespoke scene, else its family's (epic and legendary items only), else null. An
 * epic or legendary item missing from both tables still gets a scene of its kind (a looping legendary scene
 * or a static epic signature, chosen by its primary tag), so a card never lacks one.
 */
internal fun rewardCardArt(item: ItemDefinition?): RewardCardArt? {
    if (item == null) return null
    ArtByName[item.name]?.let { return it }
    if (item.rarity.rank < ItemRarity.EPIC.rank) return null
    FamilyArtByName[item.name]?.let { return it }
    return if (item.rarity == ItemRarity.LEGENDARY) RewardCardArt.RESONANCE_FORK else when (item.tags.firstOrNull()) {
        BuildTag.RAM -> RewardCardArt.TUNGSTEN_HEART
        BuildTag.DRIVE -> RewardCardArt.REDLINE_GOVERNOR
        BuildTag.ANCHOR -> RewardCardArt.ARREST_ENGINE
        BuildTag.ARSENAL -> RewardCardArt.OVERCHARGED_CAPACITOR
        BuildTag.PRECISION -> RewardCardArt.GLASS_EDGE
        BuildTag.BULWARK -> RewardCardArt.LAST_STAND
        BuildTag.OVERCLOCK -> RewardCardArt.REDLINE_REACTOR
        BuildTag.CHAIN -> RewardCardArt.DOMINO_PROTOCOL
        BuildTag.FORTUNE -> RewardCardArt.GAMBLERS_LEDGER
        null -> RewardCardArt.PRISMATIC_CORE
    }
}

/** Whether [item] draws a scene borrowed from its family rather than one of its own. */
internal fun usesFamilyArt(item: ItemDefinition): Boolean = rewardCardArt(item) != null && item.name !in ArtByName

/**
 * Draws [art] into the card face of [frame] at [time] seconds, clipped to the face slab. The card
 * draws it after its back half (face, pattern, watermark, body texture) and before its front half
 * (glow, ornament, band); the text plates and the text follow. Allocation-free once the art's
 * cached geometry exists.
 */
internal fun DrawScope.drawRewardCardArt(art: RewardCardArt, frame: RewardArtFrame, time: Float) {
    frame.cache.bind(frame)
    clipKkCardFace(frame.bounds) {
        when (art) {
            RewardCardArt.TUNGSTEN_HEART -> drawTungstenHeartArt(frame, time)
            RewardCardArt.REDLINE_GOVERNOR -> drawRedlineGovernorArt(frame, time)
            RewardCardArt.ARREST_ENGINE -> drawArrestEngineArt(frame, time)
            RewardCardArt.OVERCHARGED_CAPACITOR -> drawOverchargedCapacitorArt(frame, time)
            RewardCardArt.GLASS_EDGE -> drawGlassEdgeArt(frame, time)
            RewardCardArt.LAST_STAND -> drawLastStandArt(frame, time)
            RewardCardArt.REDLINE_REACTOR -> drawRedlineReactorArt(frame, time)
            RewardCardArt.DOMINO_PROTOCOL -> drawDominoProtocolArt(frame, time)
            RewardCardArt.GAMBLERS_LEDGER -> drawGamblersLedgerArt(frame, time)
            RewardCardArt.PHOENIX_COIL -> drawPhoenixCoilArt(frame, time)
            RewardCardArt.SHATTERGLASS -> drawShatterglassArt(frame, time)
            RewardCardArt.STORM_LATTICE -> drawStormLatticeArt(frame, time)
            RewardCardArt.DARK_STAR -> drawDarkStarArt(frame, time)
            RewardCardArt.NECROTIC_EXCHANGE -> drawNecroticExchangeArt(frame, time)
            RewardCardArt.DEADEYE_RETICLE -> drawDeadeyeReticleArt(frame, time)
            RewardCardArt.PRISMATIC_CORE -> drawPrismaticCoreArt(frame, time)
            RewardCardArt.VOLATILE_COMPOUND -> drawVolatileCompoundArt(frame, time)
            RewardCardArt.SOLAR_CHOIR -> drawSolarChoirArt(frame, time)
            RewardCardArt.GLACIAL_HEART -> drawGlacialHeartArt(frame, time)
            RewardCardArt.RESONANCE_FORK -> drawResonanceForkArt(frame, time)
        }
    }
}

/**
 * Everything one card's art needs, fixed for a card size, look and palette (the per-frame time is a
 * separate argument, so the frame never changes while the card animates). All rects are in the
 * card canvas's px, (0, 0) at the card's top left. Its [cache] is mutable draw-thread state.
 */
internal class RewardArtFrame(
    /** The whole card canvas. */
    val bounds: Rect,
    /** The card face below the band (the art is clipped to the slanted face outline as well). */
    val face: Rect,
    /**
     * Where art can sit clear of the text: the lower body between the text column's edges, above the
     * footer, starting below the card's measured text (never above the default share of the face) and
     * at least a minimum height tall. Text above it is covered by the soft face plates either way.
     */
    val clear: Rect,
    /** The icon plate (opaque): art can frame it, a corona can sit behind its edges. */
    val icon: Rect,
    /** The type hue: the element's (the preset's volt for Kinetic) under the current colour-vision preset. */
    val hue: Color,
    /** The second element hue, or [hue] for single-element items. */
    val secondHue: Color,
    /** The current palette: `roles.elements` for art that cycles through every element hue. */
    val roles: KkRolePalette,
    /** Px per dp. */
    val density: Float,
    /** The card's stable seed (item id based): use [random] rather than reading it directly. */
    val seed: Int,
    /** Phone layout (small icon, tighter text): keep detail coarse. */
    val compact: Boolean,
    /** Px per board dp: 1 dp at the 300 × 450 board card, scaled with the card. */
    val unit: Float,
    /** The per-card cache for paths, brushes, strokes and precomputed floats. */
    val cache: RewardArtCache,
    /** The card look this frame was built for (the cache key). */
    val look: RewardCardLook,
    /** The y just below the card's last text row (px): art kept clear of the text stays below it. */
    val textFloor: Float = clear.top,
    /**
     * The text leaves less than the clear region's minimum height below it: the clear region then
     * overlaps the text, and a scene moves its key element to the room that is left.
     */
    val crowded: Boolean = false,
    /** The measured text bottom (whole px) this frame was built for, NaN for the default layout (the cache key). */
    val textBottom: Float = Float.NaN,
    /** The measured footer top (whole px) this frame was built for, NaN without one (the cache key). */
    val footerTop: Float = Float.NaN,
) {
    /** Bone (highlights). */
    val bone: Color get() = Kk.Bone

    /** Gold: legendary accents only, in the preset's gold (the deep gold where its volt reads as the bright one). */
    val gold: Color get() = roles.gold.base

    /** The gold's light: the glints of a legendary scene. */
    val goldLight: Color get() = roles.gold.light

    /** [value] board dp in px at this card's size (use for every length, so art scales with the card). */
    fun u(value: Float): Float = value * unit

    /** [value] dp in px, unscaled (hairlines that must stay crisp). */
    fun dp(value: Float): Float = value * density

    /** A stable pseudo-random 0..1 for [salt] (the same card always draws the same art). */
    fun random(salt: Int): Float = kkSeedFraction(seed, 100 + salt)
}

/** Opacity bands of the art (the spec's 10–22 %): low for large fills, high for thin key lines. */
internal object RewardArtAlpha {
    const val LOW = 0.10f
    const val MID = 0.16f
    const val HIGH = 0.22f
}

/**
 * Per-card art cache: reusable paths, brushes, strokes and float arrays, rebuilt only when the card's
 * [RewardArtFrame] changes (another size, look or palette). Draw thread only.
 *
 * ```
 * val disc = frame.cache.path(0) { addOval(frame.clear) }        // built once per frame geometry
 * val glow = frame.cache.brush(0) { Brush.radialGradient(...) }   // created once per frame geometry
 * drawPath(disc, glow, alpha = RewardArtAlpha.MID)
 * ```
 */
internal class RewardArtCache {
    @PublishedApi internal val paths = arrayOfNulls<Path>(SLOTS)
    @PublishedApi internal val built = BooleanArray(SLOTS)
    @PublishedApi internal val brushes = arrayOfNulls<Brush>(SLOTS)
    @PublishedApi internal val floats = arrayOfNulls<FloatArray>(SLOTS)
    @PublishedApi internal val floatsBuilt = BooleanArray(SLOTS)
    @PublishedApi internal val rects = arrayOfNulls<Rect>(RECTS)
    private val strokeKeys = FloatArray(STROKES * 3) { Float.NaN }
    private val strokes = arrayOfNulls<Stroke>(STROKES)
    private var strokeNext = 0
    private var bound: RewardArtFrame? = null
    private var frame: RewardArtFrame? = null

    /** Binds the cache to [frame]: geometry built for another frame is rebuilt on its next use. */
    fun bind(frame: RewardArtFrame) {
        if (bound === frame) return
        bound = frame
        built.fill(false)
        floatsBuilt.fill(false)
        brushes.fill(null)
        rects.fill(null)
    }

    /** The reusable path of [slot] (0 until [SLOTS]), built by [build] once per frame geometry. */
    inline fun path(slot: Int, build: Path.() -> Unit): Path {
        val path = paths[slot] ?: Path().also { paths[slot] = it }
        if (!built[slot]) {
            path.rewind()
            path.build()
            built[slot] = true
        }
        return path
    }

    /** The brush of [slot], created by [create] once per frame geometry. */
    inline fun brush(slot: Int, create: () -> Brush): Brush = brushes[slot] ?: create().also { brushes[slot] = it }

    /** The rect of [slot] (0 until [RECTS]), created by [create] once per frame geometry. */
    inline fun rect(slot: Int, create: () -> Rect): Rect = rects[slot] ?: create().also { rects[slot] = it }

    /** A float array of [size] for [slot], filled by [fill] once per frame geometry (positions, angles). */
    inline fun floats(slot: Int, size: Int, fill: (FloatArray) -> Unit): FloatArray {
        val existing = floats[slot]
        val array = if (existing != null && existing.size == size) existing else FloatArray(size).also { floats[slot] = it }
        if (!floatsBuilt[slot]) {
            fill(array)
            floatsBuilt[slot] = true
        }
        return array
    }

    /** A cached stroke of [width] px with [cap] and [join] (a few at once without allocating). */
    fun stroke(width: Float, cap: StrokeCap = StrokeCap.Butt, join: StrokeJoin = StrokeJoin.Miter): Stroke {
        val c = cap.hashCode().toFloat()
        val j = join.hashCode().toFloat()
        for (index in 0 until STROKES) {
            val base = index * 3
            if (strokeKeys[base] == width && strokeKeys[base + 1] == c && strokeKeys[base + 2] == j) return strokes[index]!!
        }
        val created = Stroke(width, cap = cap, join = join)
        val base = strokeNext * 3
        strokeKeys[base] = width
        strokeKeys[base + 1] = c
        strokeKeys[base + 2] = j
        strokes[strokeNext] = created
        strokeNext = (strokeNext + 1) % STROKES
        return created
    }

    /** The frame for [look] at this size and text, reused while nothing changed; null when it must be rebuilt. */
    fun current(look: RewardCardLook, roles: KkRolePalette, width: Float, height: Float, density: Float, compact: Boolean, textBottom: Float = Float.NaN, footerTop: Float = Float.NaN): RewardArtFrame? {
        val existing = frame ?: return null
        val sameText = (existing.textBottom == textBottom || (existing.textBottom.isNaN() && textBottom.isNaN())) &&
            (existing.footerTop == footerTop || (existing.footerTop.isNaN() && footerTop.isNaN()))
        val same = existing.look === look && existing.roles === roles && existing.bounds.width == width &&
            existing.bounds.height == height && existing.density == density && existing.compact == compact && sameText
        return if (same) existing else null
    }

    /** Keeps [created] as the current frame. */
    fun store(created: RewardArtFrame): RewardArtFrame {
        frame = created
        return created
    }

    companion object {
        /** Path, brush and float slots per card. */
        const val SLOTS = 24

        /** Rect slots per card. */
        const val RECTS = 4
        private const val STROKES = 8
    }
}
