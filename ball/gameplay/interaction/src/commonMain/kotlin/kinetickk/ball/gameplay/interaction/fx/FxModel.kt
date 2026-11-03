// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

/**
 * The procedural pieces every reaction, skill and Apex look is built from. One painter draws each
 * from (centre, reach, layer progress, seed, direction, weight, colours); none stores particles.
 */
internal enum class FxPrimitive {
    /** A filled circle growing from r0 to r1. */
    DISC,

    /** A stroked circle. */
    RING,

    /** n arcs of `param`° evenly spaced, turning. */
    ARCS,

    /** Two arcs of `param`° in A and B, 180° apart, turning. */
    SPLIT_RING,

    /** n radial lines from R·r0 to r(p); `param` jitters their angles (radians). */
    SPOKES,

    /** n radial segments `param`·R long whose inner end moves inward along r(p). */
    STREAKS_IN,

    /** As [STREAKS_IN], moving outward. */
    STREAKS_OUT,

    /** n three-segment polylines spiralling inward from r(p), sweeping `param`°. */
    SPIRAL,

    /** n dots of radius `param` at hashed angles and radii between r0 and r1, appearing over the layer. */
    DOTS,

    /** n dots of radius `param` at radius r(p) (inward travel when r1 < r0). */
    DOTS_IN,

    /** n blunt shards `param` long travelling outward: a fan `param2`° apart, or radial when 0. */
    SHARDS,

    /** n three-segment lightning forks from the centre to r(p), jittered by `param`·R. */
    FORKS,

    /** A hexagon outline of radius r(p). */
    HEX,

    /** A sheared (−12°) square outline whose corners lie at r(p). */
    PLATE,

    /** n jagged three-segment cracks growing from R·r0 to r(p), ink under colour; `param` jitters the joints. */
    CRACKS,

    /** n blunt flame tongues on the ring r(p), `param`·R long. */
    FLAMES,

    /** n short ticks `param`·R long on the ring r(p); their spin settles by `param2`. */
    TICKS,

    /** n sheared bars r(p) wide, stacked `param`·R apart and collapsing to the centre. */
    BARS,

    /** A jagged ring through twelve hashed vertices. */
    QUAKE,

    /** n partial arcs of `param`° facing θ at radii r(p) + i·`param2`·R, each a little later. */
    WAVES,

    /** An ink disc of radius r(p) with a colour ring on it and a thin one at 0.6·r(p). */
    PUNCH,

    /** A sheared slab `param`·R wide crossing from −R to +R. */
    WIPE,

    /** n parallel straight lanes along θ (behind the centre with [FxFlag.BACK]), r(p)·R long, `param2` apart. */
    LANES,
}

/** Which colour a layer takes: the first or second element, bone, ink, the two alternating, the player's or the gold. */
internal enum class FxColor { A, B, BONE, INK, AB, YOU, GOLD, GOLD_LIGHT }

/** Layer easing: linear, Out, In, Snap and Pull (`KkEase`). */
internal enum class FxEase { L, O, I, S, P }

/** How a burst's reach follows its cue. */
internal enum class FxReach {
    /** The cue's radius within 36..240. */
    AREA,

    /** The cue's radius within 48..120 (the enemy's size or the reaction's own radius). */
    TARGET,

    /** A fixed reach ([ReactionFxSpec.fixedReach]). */
    FIXED,

    /** The cue's own effect radius (skills), within 24..480; 120 when the cue has none. */
    CUE,
}

/** Bits of [FxLayer.flags]. */
internal object FxFlag {
    /** A signature layer: always drawn, whatever the budget, crowd or setting. */
    const val SIG: Int = 1

    /** A secondary layer: the first to go. */
    const val SEC: Int = 1 shl 1

    /** Centred on the Core; radii are the Core radius plus world units and lengths are world units. */
    const val CORE: Int = 1 shl 2

    /** Drawn only when the reaction's Core or play-style branch fired. */
    const val VERB: Int = 1 shl 3

    /** Drawn only when that branch did not fire. */
    const val NOT_VERB: Int = 1 shl 4

    /** Drawn only in the keystone form. */
    const val KEY: Int = 1 shl 5

    /** Drawn only without the keystone. */
    const val BASE: Int = 1 shl 6

    /** Faces the cue's direction (else a seeded angle). */
    const val DIR: Int = 1 shl 7

    /** Flames with an ink core; lanes with an ink core under colour rims. */
    const val INK_CORE: Int = 1 shl 8

    /** Ticks lie along the ring instead of across it. */
    const val TANGENT: Int = 1 shl 9

    /** Shards fly to the Core's guard orbit slots. */
    const val CORE_TARGET: Int = 1 shl 10

    /** A dotted ring. */
    const val DOTTED: Int = 1 shl 11

    /** In the keystone form a [VERB] or [NOT_VERB] layer is drawn regardless of the branch. */
    const val KEY_ALWAYS: Int = 1 shl 12

    /** Dots drift up (steam). */
    const val RISE: Int = 1 shl 13

    /** Waves come as three stacks 120° apart. */
    const val TRI: Int = 1 shl 14

    /** Drawn only at rank III (skills). */
    const val RANK3: Int = 1 shl 15

    /** Drawn only when the Core stood inside the effect (skills). */
    const val CORE_INSIDE: Int = 1 shl 16

    /** Two more elements per rank (skills). */
    const val PER_RANK: Int = 1 shl 17

    /** Radii and lengths are world units around the burst's own centre. */
    const val WORLD: Int = 1 shl 18

    /** Lanes run behind the centre (opposite θ). */
    const val BACK: Int = 1 shl 19
}

/** The four stroke widths world FX use (world units); a dotted ring uses the dotted stroke (1.6). */
internal val FxStrokeWidths: FloatArray = floatArrayOf(1.5f, 2f, 3f, 4.5f)

/**
 * One animated piece of a look: [primitive] over the burst progress [t0]..[t1] eased by [ease], in
 * [color], [count] elements, radius fraction [r0]→[r1] of the reach, stroke [width], [spin] degrees
 * over the layer, primitive parameters [param]/[param2], peak [alpha] and [FxFlag] bits.
 */
internal class FxLayer(
    val primitive: FxPrimitive,
    val t0: Float,
    val t1: Float,
    val ease: FxEase,
    val color: FxColor,
    val count: Int,
    val r0: Float,
    val r1: Float,
    val width: Float,
    val spin: Float,
    val param: Float,
    val param2: Float,
    val alpha: Float,
    val flags: Int,
) {
    fun has(flag: Int): Boolean = flags and flag != 0

    /** Whether the primitive draws an outline (its width must come from [FxStrokeWidths]). */
    val stroked: Boolean
        get() = when (primitive) {
            FxPrimitive.DISC, FxPrimitive.DOTS, FxPrimitive.DOTS_IN, FxPrimitive.SHARDS, FxPrimitive.FLAMES, FxPrimitive.BARS,
            FxPrimitive.WIPE -> false
            else -> true
        }

    /**
     * Draw operations of [elements] elements (a ring, a dot, a polyline or a shape outline is one;
     * cracks count twice for their ink pass, a punch three, triple waves three per element).
     */
    fun ops(elements: Int): Int = when (primitive) {
        FxPrimitive.DISC, FxPrimitive.RING, FxPrimitive.HEX, FxPrimitive.PLATE, FxPrimitive.QUAKE, FxPrimitive.WIPE -> 1
        FxPrimitive.SPLIT_RING -> 2
        FxPrimitive.PUNCH -> 3
        FxPrimitive.CRACKS -> 2 * elements
        FxPrimitive.WAVES -> if (has(FxFlag.TRI)) 3 * elements else elements
        FxPrimitive.LANES -> if (has(FxFlag.INK_CORE)) 3 * elements else elements
        else -> elements
    }

    /** The element count at [rank] (skills add two per rank with [FxFlag.PER_RANK]). */
    fun elements(rank: Int): Int = if (has(FxFlag.PER_RANK)) count + 2 * rank.coerceIn(1, 3) else count
}

/** A transient look: its [life] in seconds, how its reach follows the cue and its [layers] in draw order. */
internal class ReactionFxSpec(
    val life: Float,
    val reach: FxReach,
    val fixedReach: Float,
    val layers: Array<FxLayer>,
) {
    /** The burst reach for a cue [radius]. */
    fun reachOf(radius: Float): Float = when (reach) {
        FxReach.AREA -> radius.coerceIn(36f, 240f)
        FxReach.TARGET -> radius.coerceIn(48f, 120f)
        FxReach.FIXED -> fixedReach
        FxReach.CUE -> if (radius > 0f) radius.coerceIn(24f, 480f) else 120f
    }
}

internal fun layer(
    primitive: FxPrimitive,
    t0: Float,
    t1: Float,
    ease: FxEase,
    color: FxColor,
    n: Int = 1,
    r0: Float = 0f,
    r1: Float = 1f,
    w: Float = 0f,
    spin: Float = 0f,
    param: Float = 0f,
    param2: Float = 0f,
    alpha: Float = 1f,
    flags: Int = 0,
) = FxLayer(primitive, t0, t1, ease, color, n, r0, r1, w, spin, param, param2, alpha, flags)

internal fun spec(life: Float, reach: FxReach, fixed: Float = 0f, vararg layers: FxLayer) = ReactionFxSpec(life, reach, fixed, arrayOf(*layers))

internal const val SIG = FxFlag.SIG
internal const val SEC = FxFlag.SEC
internal const val CORE = FxFlag.CORE
internal const val VERB = FxFlag.VERB
internal const val NOT_VERB = FxFlag.NOT_VERB
internal const val KEY = FxFlag.KEY
internal const val BASE = FxFlag.BASE
internal const val DIR = FxFlag.DIR
