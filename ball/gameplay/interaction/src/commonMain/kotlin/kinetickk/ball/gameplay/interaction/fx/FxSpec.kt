// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement

/**
 * The procedural pieces every reaction, skill and Apex look is built from. One painter draws each
 * from (centre, reach, layer progress, seed, direction, weight, colours); none stores particles.
 */
enum class FxPrimitive {
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
enum class FxColor { A, B, BONE, INK, AB, YOU, GOLD, GOLD_LIGHT }

/** Layer easing: linear, Out, In, Snap and Pull (`KkEase`). */
enum class FxEase { L, O, I, S, P }

/** How a burst's reach follows its cue. */
enum class FxReach {
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
object FxFlag {
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
val FxStrokeWidths: FloatArray = floatArrayOf(1.5f, 2f, 3f, 4.5f)

/**
 * One animated piece of a look: [primitive] over the burst progress [t0]..[t1] eased by [ease], in
 * [color], [count] elements, radius fraction [r0]→[r1] of the reach, stroke [width], [spin] degrees
 * over the layer, primitive parameters [param]/[param2], peak [alpha] and [FxFlag] bits.
 */
class FxLayer(
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
class ReactionFxSpec(
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

private fun layer(
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

private fun spec(life: Float, reach: FxReach, fixed: Float = 0f, vararg layers: FxLayer) = ReactionFxSpec(life, reach, fixed, arrayOf(*layers))

private const val SIG = FxFlag.SIG
private const val SEC = FxFlag.SEC
private const val CORE = FxFlag.CORE
private const val VERB = FxFlag.VERB
private const val NOT_VERB = FxFlag.NOT_VERB
private const val KEY = FxFlag.KEY
private const val BASE = FxFlag.BASE
private const val DIR = FxFlag.DIR

/**
 * The 21 reaction looks (hud-fx §4.3 with the keystone deltas of §4.4), indexed by reaction ordinal.
 * The exhaustive `when` makes a new reaction fail to compile until it has a look.
 */
object ReactionFxSpecs {
    private val specs: Array<ReactionFxSpec> = Array(ReactionId.entries.size) { build(ReactionId.entries[it]) }

    operator fun get(reaction: Int): ReactionFxSpec = specs[reaction.coerceIn(0, specs.lastIndex)]

    operator fun get(reaction: ReactionId): ReactionFxSpec = specs[reaction.ordinal]

    /** The longest reaction look (seconds). */
    val maxLife: Float = specs.maxOf { it.life }

    private fun build(reaction: ReactionId): ReactionFxSpec = when (reaction) {
        // Spall: a white blast, a split ring in both hues, cracks, steam and spall flakes (Flash Freeze: a frost hexagon).
        ReactionId.THERMAL_SHOCK -> spec(0.60f, FxReach.TARGET, 0f,
            layer(FxPrimitive.DISC, 0f, 0.18f, FxEase.S, FxColor.BONE, r0 = 0.30f, r1 = 0.70f, alpha = 0.35f, flags = SIG),
            layer(FxPrimitive.SPLIT_RING, 0f, 0.70f, FxEase.O, FxColor.AB, r0 = 0.35f, r1 = 1f, w = 3f, spin = 120f, param = 172f, alpha = 0.95f,
                flags = SIG),
            layer(FxPrimitive.CRACKS, 0.05f, 0.55f, FxEase.O, FxColor.BONE, n = 3, r0 = 0.10f, r1 = 0.65f, w = 2f, param = 0.6f),
            layer(FxPrimitive.DOTS, 0.10f, 0.90f, FxEase.O, FxColor.BONE, n = 6, r0 = 0.20f, r1 = 0.90f, param = 3f, alpha = 0.5f,
                flags = SEC or FxFlag.RISE),
            layer(FxPrimitive.SPOKES, 0f, 0.25f, FxEase.S, FxColor.A, n = 4, r0 = 0.90f, r1 = 1.25f, w = 2f, param = 0.4f, flags = SEC or BASE),
            layer(FxPrimitive.HEX, 0f, 0.35f, FxEase.O, FxColor.B, r0 = 0.50f, r1 = 0.80f, w = 2f, flags = KEY),
        )
        // Plasma Pool: a flash with four forks and embers settling into the pool (Arc Plasma: six forks, no embers).
        ReactionId.PLASMA_BURST -> spec(0.65f, FxReach.AREA, 0f,
            layer(FxPrimitive.DISC, 0f, 0.30f, FxEase.S, FxColor.A, r0 = 0.25f, r1 = 0.80f, alpha = 0.30f, flags = SIG),
            layer(FxPrimitive.FORKS, 0f, 0.40f, FxEase.O, FxColor.B, n = 4, r0 = 0.20f, r1 = 1f, w = 2f, param = 0.12f, flags = SIG or BASE),
            layer(FxPrimitive.FORKS, 0f, 0.40f, FxEase.O, FxColor.B, n = 6, r0 = 0.20f, r1 = 1f, w = 2f, param = 0.12f, flags = SIG or KEY),
            layer(FxPrimitive.RING, 0.10f, 0.65f, FxEase.O, FxColor.A, r0 = 0.40f, r1 = 1.05f, w = 3f),
            layer(FxPrimitive.DOTS, 0.25f, 1f, FxEase.L, FxColor.A, n = 8, r0 = 0.20f, r1 = 0.95f, param = 3f, alpha = 0.6f, flags = SEC or BASE),
        )
        // Accretion: a thick ring closes and a star forms; the field carries the rest (Dwarf Star: four short spokes).
        ReactionId.FUSION -> spec(0.55f, FxReach.AREA, 0f,
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.I, FxColor.B, r0 = 1f, r1 = 0.20f, w = 4.5f, flags = SIG),
            layer(FxPrimitive.DISC, 0.20f, 0.80f, FxEase.O, FxColor.A, r0 = 0.05f, r1 = 0.18f, alpha = 0.35f, flags = SIG),
            layer(FxPrimitive.SPOKES, 0.25f, 0.85f, FxEase.O, FxColor.A, n = 6, r0 = 0.12f, r1 = 0.38f, w = 2f, spin = 30f, flags = BASE),
            layer(FxPrimitive.SPOKES, 0.25f, 0.85f, FxEase.O, FxColor.A, n = 4, r0 = 0.12f, r1 = 0.28f, w = 2f, spin = 30f, flags = KEY),
            layer(FxPrimitive.STREAKS_IN, 0f, 0.45f, FxEase.I, FxColor.B, n = 8, r0 = 1.10f, r1 = 0.30f, w = 1.5f, param = 0.18f, flags = SEC),
        )
        // Ablaze: flame tongues, rotting embers; the Core catches fire (Firewalker: always).
        ReactionId.WILDFIRE -> spec(0.70f, FxReach.FIXED, 96f,
            layer(FxPrimitive.FLAMES, 0f, 0.60f, FxEase.O, FxColor.A, n = 6, r0 = 0.40f, r1 = 0.80f, param = 0.22f, flags = SIG),
            layer(FxPrimitive.DOTS, 0.10f, 0.70f, FxEase.O, FxColor.B, n = 6, r0 = 0.20f, r1 = 0.90f, param = 3f),
            layer(FxPrimitive.RING, 0f, 0.45f, FxEase.O, FxColor.A, r0 = 0.20f, r1 = 0.70f, w = 2f, flags = SEC),
            layer(FxPrimitive.FLAMES, 0f, 0.50f, FxEase.O, FxColor.A, n = 8, r0 = 6f, r1 = 12f, param = 10f,
                flags = CORE or VERB or FxFlag.KEY_ALWAYS or SIG),
        )
        // Frictionless: an ice crystal with cross ticks; drag stripped from the Core (Zero Resistance: no Core layers).
        ReactionId.SUPERCONDUCT -> spec(0.55f, FxReach.FIXED, 72f,
            layer(FxPrimitive.SPOKES, 0f, 0.40f, FxEase.O, FxColor.A, n = 6, r0 = 0.10f, r1 = 0.80f, w = 2f, flags = SIG),
            layer(FxPrimitive.TICKS, 0f, 0.40f, FxEase.O, FxColor.A, n = 6, r0 = 0.55f, r1 = 0.55f, w = 1.5f, param = 0.20f,
                flags = SIG or FxFlag.TANGENT),
            layer(FxPrimitive.FORKS, 0f, 0.30f, FxEase.S, FxColor.B, n = 3, r0 = 0.10f, r1 = 0.60f, w = 1.5f, param = 0.08f, flags = SEC),
            layer(FxPrimitive.RING, 0f, 0.45f, FxEase.O, FxColor.A, r0 = 8f, r1 = 40f, w = 2f, flags = CORE or VERB or BASE),
            layer(FxPrimitive.SPOKES, 0f, 0.30f, FxEase.O, FxColor.BONE, n = 6, r0 = 4f, r1 = 20f, w = 1.5f, flags = CORE or VERB or SEC or BASE),
        )
        // Ice Fan: five shards fanning away from the Core over a gravity crush at the origin.
        ReactionId.GLACIER_CRUSH -> spec(0.55f, FxReach.FIXED, 220f,
            layer(FxPrimitive.SHARDS, 0f, 0.45f, FxEase.O, FxColor.A, n = 5, r0 = 0.05f, r1 = 1f, param = 18f, param2 = 25f, flags = SIG or DIR),
            layer(FxPrimitive.RING, 0f, 0.30f, FxEase.I, FxColor.B, r0 = 0.30f, r1 = 0.08f, w = 3f, flags = SIG),
            layer(FxPrimitive.SPOKES, 0f, 0.25f, FxEase.S, FxColor.B, n = 5, r0 = 0.05f, r1 = 0.35f, w = 1.5f, param2 = 25f, flags = DIR or SEC),
            layer(FxPrimitive.DOTS, 0.20f, 0.70f, FxEase.L, FxColor.A, n = 10, r0 = 0.30f, r1 = 1f, param = 2f, param2 = 55f, flags = DIR or SEC),
        )
        // Glass Charge: a glass pane cracks and the Embrittled ring appears (Glass Burst: the ring solid in A).
        ReactionId.EMBRITTLE -> spec(0.60f, FxReach.TARGET, 0f,
            layer(FxPrimitive.PLATE, 0f, 0.35f, FxEase.P, FxColor.A, r0 = 1.10f, r1 = 0.95f, w = 3f, spin = -8f, flags = SIG),
            layer(FxPrimitive.CRACKS, 0.10f, 0.60f, FxEase.O, FxColor.B, n = 4, r0 = 0.15f, r1 = 0.85f, w = 1.5f, param = 0.7f, flags = SIG),
            layer(FxPrimitive.DOTS, 0.20f, 0.80f, FxEase.O, FxColor.B, n = 6, r0 = 0.30f, r1 = 0.90f, param = 2.5f),
            layer(FxPrimitive.RING, 0.25f, 0.60f, FxEase.O, FxColor.A, r0 = 0.90f, r1 = 1.10f, w = 1.6f, flags = FxFlag.DOTTED or SEC or BASE),
            layer(FxPrimitive.RING, 0.25f, 0.60f, FxEase.O, FxColor.A, r0 = 0.90f, r1 = 1.10f, w = 1.5f, flags = SEC or KEY),
        )
        // Attractor: the yank inward and field lines spinning up (Repulsor: the pair pushes outward).
        ReactionId.MAGNETIZE -> spec(0.60f, FxReach.AREA, 0f,
            layer(FxPrimitive.STREAKS_IN, 0f, 0.40f, FxEase.I, FxColor.A, n = 10, r0 = 1f, r1 = 0.15f, w = 2f, param = 0.22f, flags = SIG or BASE),
            layer(FxPrimitive.RING, 0f, 0.35f, FxEase.I, FxColor.B, r0 = 1f, r1 = 0.30f, w = 3f, flags = SIG or BASE),
            layer(FxPrimitive.STREAKS_OUT, 0f, 0.40f, FxEase.O, FxColor.A, n = 10, r0 = 0.15f, r1 = 1f, w = 2f, param = 0.22f, flags = SIG or KEY),
            layer(FxPrimitive.RING, 0f, 0.35f, FxEase.O, FxColor.B, r0 = 0.30f, r1 = 1f, w = 3f, flags = SIG or KEY),
            layer(FxPrimitive.ARCS, 0.30f, 0.90f, FxEase.O, FxColor.AB, n = 4, r0 = 0.30f, r1 = 0.30f, w = 2f, spin = 180f, param = 50f),
            layer(FxPrimitive.DOTS_IN, 0.05f, 0.45f, FxEase.I, FxColor.B, n = 10, r0 = 1f, r1 = 0.20f, param = 2.5f, flags = SEC),
        )
        // Overcurrent: jagged forks, decay pits; the weapon kicked (Short Circuit: the Core layers in bone).
        ReactionId.FEEDBACK -> spec(0.55f, FxReach.FIXED, 90f,
            layer(FxPrimitive.FORKS, 0f, 0.35f, FxEase.S, FxColor.A, n = 3, r0 = 0.15f, r1 = 0.90f, w = 2f, param = 0.22f, flags = SIG),
            layer(FxPrimitive.DOTS, 0.05f, 0.70f, FxEase.O, FxColor.B, n = 8, r0 = 0.20f, r1 = 0.85f, param = 3f, flags = SIG),
            layer(FxPrimitive.SPLIT_RING, 0f, 0.50f, FxEase.O, FxColor.AB, r0 = 0.30f, r1 = 0.80f, w = 1.5f, spin = -90f, param = 172f, flags = SEC),
            layer(FxPrimitive.RING, 0f, 0.30f, FxEase.O, FxColor.A, r0 = 4f, r1 = 28f, w = 2f, flags = CORE or VERB or BASE),
            layer(FxPrimitive.ARCS, 0f, 0.30f, FxEase.O, FxColor.A, n = 3, r0 = 18f, r1 = 18f, w = 2f, spin = 90f, param = 40f,
                flags = CORE or VERB or SEC or BASE),
            layer(FxPrimitive.RING, 0f, 0.30f, FxEase.O, FxColor.BONE, r0 = 4f, r1 = 28f, w = 2f, flags = CORE or VERB or KEY),
            layer(FxPrimitive.ARCS, 0f, 0.30f, FxEase.O, FxColor.BONE, n = 3, r0 = 18f, r1 = 18f, w = 2f, spin = 90f, param = 40f,
                flags = CORE or VERB or SEC or KEY),
        )
        // Compost: a spiral drawing in under a growing rim (Plague Pit: eight arms).
        ReactionId.ROT_WELL -> spec(0.55f, FxReach.AREA, 0f,
            layer(FxPrimitive.SPIRAL, 0f, 0.55f, FxEase.I, FxColor.B, n = 6, r0 = 1f, r1 = 0.25f, w = 2f, param = 50f, flags = SIG or BASE),
            layer(FxPrimitive.SPIRAL, 0f, 0.55f, FxEase.I, FxColor.B, n = 8, r0 = 1f, r1 = 0.25f, w = 2f, param = 50f, flags = SIG or KEY),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.O, FxColor.A, r0 = 0.60f, r1 = 1f, w = 3f, flags = SIG),
            layer(FxPrimitive.DOTS_IN, 0f, 0.55f, FxEase.I, FxColor.B, n = 12, r0 = 0.95f, r1 = 0.20f, param = 2.5f),
            layer(FxPrimitive.DISC, 0f, 0.50f, FxEase.O, FxColor.INK, r0 = 0.20f, r1 = 0.60f, alpha = 0.30f, flags = SEC),
        )
        // Blast Jump: a double sound ring and heat shimmer, handing over to the Core's ring (Rocket Clap: the Core launched).
        ReactionId.THUNDERCLAP -> spec(0.60f, FxReach.AREA, 0f,
            layer(FxPrimitive.RING, 0f, 0.45f, FxEase.O, FxColor.B, r0 = 0.15f, r1 = 1.10f, w = 4.5f, flags = SIG),
            layer(FxPrimitive.RING, 0.12f, 0.60f, FxEase.O, FxColor.B, r0 = 0.15f, r1 = 1.25f, w = 2f, flags = SIG),
            layer(FxPrimitive.DISC, 0f, 0.15f, FxEase.S, FxColor.A, r0 = 0.20f, r1 = 0.50f, alpha = 0.35f),
            layer(FxPrimitive.SPOKES, 0f, 0.30f, FxEase.O, FxColor.A, n = 12, r0 = 0.30f, r1 = 0.60f, w = 1.5f, param = 0.2f, flags = SEC),
            layer(FxPrimitive.RING, 0f, 0.15f, FxEase.O, FxColor.B, r0 = 30f, r1 = 14f, w = 2f, flags = CORE or VERB or BASE),
            layer(FxPrimitive.STREAKS_OUT, 0f, 0.30f, FxEase.O, FxColor.B, n = 6, r0 = 8f, r1 = 60f, w = 2f, param = 14f,
                flags = CORE or VERB or KEY),
        )
        // Shard Guard: radial shards and ringing rings far away; near the Core the shards fly to their orbit
        // (Crystal Choir: seven radial shards and the three rings always).
        ReactionId.SHATTERPOINT -> spec(0.60f, FxReach.FIXED, 240f,
            layer(FxPrimitive.SHARDS, 0f, 0.45f, FxEase.O, FxColor.A, n = 5, r0 = 0.05f, r1 = 1f, param = 16f, flags = DIR or NOT_VERB or SIG or BASE),
            layer(FxPrimitive.SHARDS, 0f, 0.45f, FxEase.O, FxColor.A, n = 7, r0 = 0.05f, r1 = 1f, param = 16f, flags = DIR or SIG or KEY),
            layer(FxPrimitive.RING, 0f, 0.35f, FxEase.O, FxColor.B, r0 = 0.10f, r1 = 0.35f, w = 3f,
                flags = NOT_VERB or FxFlag.KEY_ALWAYS or SIG),
            layer(FxPrimitive.RING, 0.10f, 0.45f, FxEase.O, FxColor.B, r0 = 0.10f, r1 = 0.45f, w = 2f, flags = NOT_VERB or FxFlag.KEY_ALWAYS),
            layer(FxPrimitive.RING, 0.20f, 0.55f, FxEase.O, FxColor.B, r0 = 0.10f, r1 = 0.55f, w = 1.5f,
                flags = NOT_VERB or FxFlag.KEY_ALWAYS or SEC),
            layer(FxPrimitive.SHARDS, 0f, 0.35f, FxEase.O, FxColor.A, n = 5, r0 = 0f, r1 = 1f, param = 16f, flags = FxFlag.CORE_TARGET or VERB or SIG),
            layer(FxPrimitive.RING, 0f, 0.35f, FxEase.O, FxColor.B, r0 = 0.10f, r1 = 0.40f, w = 2f, flags = VERB),
        )
        // Arc Weave: three arc stacks, a split ring and short forks; the weave comes online (Standing Wave: always).
        ReactionId.RESONANT_ARC -> spec(0.55f, FxReach.FIXED, 96f,
            layer(FxPrimitive.WAVES, 0f, 0.50f, FxEase.O, FxColor.B, n = 3, r0 = 0.30f, r1 = 0.90f, w = 2f, param = 70f, param2 = 0.15f,
                flags = SIG or FxFlag.TRI),
            layer(FxPrimitive.SPLIT_RING, 0f, 0.45f, FxEase.O, FxColor.AB, r0 = 0.20f, r1 = 0.60f, w = 2f, spin = 140f, param = 172f),
            layer(FxPrimitive.FORKS, 0f, 0.25f, FxEase.S, FxColor.A, n = 4, r0 = 0.10f, r1 = 0.50f, w = 1.5f, param = 0.1f, flags = SEC),
            layer(FxPrimitive.ARCS, 0f, 0.40f, FxEase.O, FxColor.B, n = 3, r0 = 18f, r1 = 18f, w = 2f, spin = 120f, param = 30f,
                flags = CORE or VERB or FxFlag.KEY_ALWAYS),
        )
        // Rock Echo: two quake rings and long ground cracks (the echoes are jagged shockwaves).
        ReactionId.SEISMIC -> spec(0.70f, FxReach.AREA, 0f,
            layer(FxPrimitive.QUAKE, 0f, 0.50f, FxEase.O, FxColor.A, r0 = 0.15f, r1 = 1f, w = 3f, flags = SIG),
            layer(FxPrimitive.QUAKE, 0.15f, 0.70f, FxEase.O, FxColor.A, r0 = 0.15f, r1 = 0.80f, w = 2f),
            layer(FxPrimitive.CRACKS, 0f, 0.45f, FxEase.O, FxColor.B, n = 6, r0 = 0.10f, r1 = 0.70f, w = 2f, param = 0.5f, flags = SIG),
            layer(FxPrimitive.DISC, 0f, 0.30f, FxEase.O, FxColor.INK, r0 = 0.20f, r1 = 0.60f, alpha = 0.35f, flags = SEC),
        )
        // Breakpoint: long cracks, decay dots and ticks on the rim (Crevasse: no ticks).
        ReactionId.FRACTURE -> spec(0.55f, FxReach.AREA, 0f,
            layer(FxPrimitive.CRACKS, 0f, 0.45f, FxEase.O, FxColor.A, n = 6, r0 = 0.10f, r1 = 1f, w = 2f, param = 0.45f, flags = SIG),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.O, FxColor.B, r0 = 0.30f, r1 = 0.90f, w = 1.5f),
            layer(FxPrimitive.DOTS, 0.10f, 0.70f, FxEase.O, FxColor.A, n = 10, r0 = 0.20f, r1 = 1f, param = 2.5f, flags = SEC),
            layer(FxPrimitive.TICKS, 0.05f, 0.45f, FxEase.O, FxColor.B, n = 6, r0 = 0.95f, r1 = 0.95f, w = 2f, param = 0.12f, flags = SEC or BASE),
        )
        // Hush: bars collapse onto a punched-out centre (Dead Air: the punch at the Core).
        ReactionId.SILENCE -> spec(0.65f, FxReach.AREA, 0f,
            layer(FxPrimitive.BARS, 0f, 0.45f, FxEase.I, FxColor.A, n = 6, r0 = 0.80f, r1 = 0.10f, param = 0.18f, flags = SIG),
            layer(FxPrimitive.PUNCH, 0.20f, 0.65f, FxEase.O, FxColor.B, r0 = 0.05f, r1 = 0.25f, w = 2f, flags = SIG or BASE),
            layer(FxPrimitive.PUNCH, 0.20f, 0.65f, FxEase.O, FxColor.B, r0 = 6f, r1 = 30f, w = 2f, flags = CORE or SIG or KEY),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.I, FxColor.B, r0 = 1f, r1 = 0.40f, w = 2f),
            layer(FxPrimitive.DOTS, 0.30f, 0.90f, FxEase.L, FxColor.B, n = 6, r0 = 0.40f, r1 = 0.95f, param = 2f, alpha = 0.5f, flags = SEC),
        )
        // Flame Leap: ink flames rimmed in Void around a punch (Hellfire: ten longer flames).
        ReactionId.BLACKFIRE -> spec(0.70f, FxReach.TARGET, 0f,
            layer(FxPrimitive.FLAMES, 0f, 0.70f, FxEase.O, FxColor.B, n = 8, r0 = 0.45f, r1 = 0.85f, param = 0.30f,
                flags = FxFlag.INK_CORE or SIG or BASE),
            layer(FxPrimitive.FLAMES, 0f, 0.70f, FxEase.O, FxColor.B, n = 10, r0 = 0.45f, r1 = 0.85f, param = 0.36f,
                flags = FxFlag.INK_CORE or SIG or KEY),
            layer(FxPrimitive.PUNCH, 0f, 0.30f, FxEase.O, FxColor.B, r0 = 0.10f, r1 = 0.35f, w = 2f, flags = SIG),
            layer(FxPrimitive.DOTS, 0.10f, 0.70f, FxEase.O, FxColor.A, n = 6, r0 = 0.50f, r1 = 1f, param = 2.5f),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.O, FxColor.A, r0 = 0.40f, r1 = 0.95f, w = 1.5f, flags = SEC),
        )
        // Time Bubble: clock ticks freeze and a clock hand stops.
        ReactionId.STASIS -> spec(0.65f, FxReach.AREA, 0f,
            layer(FxPrimitive.TICKS, 0f, 0.65f, FxEase.O, FxColor.A, n = 12, r0 = 0.70f, r1 = 0.70f, w = 2f, spin = 120f, param = 0.14f,
                param2 = 0.30f, flags = SIG),
            layer(FxPrimitive.RING, 0f, 0.30f, FxEase.I, FxColor.B, r0 = 1f, r1 = 0.70f, w = 3f, flags = SIG),
            layer(FxPrimitive.DISC, 0f, 0.20f, FxEase.S, FxColor.BONE, r0 = 0.20f, r1 = 0.40f, alpha = 0.30f),
            layer(FxPrimitive.SPOKES, 0.05f, 0.35f, FxEase.O, FxColor.B, n = 1, r0 = 0f, r1 = 0.60f, w = 2f, spin = 360f, flags = SEC),
        )
        // Void Anchor: a punch that grows and closes late (Event Strike: anchor rings in A and bone).
        ReactionId.ANNIHILATE -> spec(0.60f, FxReach.TARGET, 0f,
            layer(FxPrimitive.PUNCH, 0f, 0.45f, FxEase.O, FxColor.B, r0 = 0.15f, r1 = 0.55f, w = 3f, flags = SIG),
            layer(FxPrimitive.RING, 0.30f, 0.60f, FxEase.I, FxColor.B, r0 = 0.60f, r1 = 0.05f, w = 3f, flags = SIG),
            layer(FxPrimitive.FORKS, 0f, 0.25f, FxEase.S, FxColor.A, n = 2, r0 = 0.10f, r1 = 0.70f, w = 2f, param = 0.1f),
            layer(FxPrimitive.DOTS, 0.10f, 0.60f, FxEase.O, FxColor.A, n = 5, r0 = 0.60f, r1 = 1f, param = 2f, flags = SEC),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.O, FxColor.BONE, r0 = 0.30f, r1 = 0.60f, w = 1.5f, flags = VERB or SEC),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.O, FxColor.A, r0 = 0.40f, r1 = 0.75f, w = 1.5f, flags = VERB or SEC or KEY),
        )
        // Maw: a punched void swallowing dots (Hungry Void: no inward arcs).
        ReactionId.NULL_WELL -> spec(0.55f, FxReach.AREA, 0f,
            layer(FxPrimitive.PUNCH, 0f, 0.55f, FxEase.O, FxColor.B, r0 = 0.05f, r1 = 0.40f, w = 3f, flags = SIG),
            layer(FxPrimitive.DOTS_IN, 0f, 0.55f, FxEase.I, FxColor.A, n = 11, r0 = 1f, r1 = 0.10f, param = 3f, flags = SIG),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.I, FxColor.B, r0 = 1f, r1 = 0.55f, w = 2f),
            layer(FxPrimitive.ARCS, 0.10f, 0.55f, FxEase.O, FxColor.A, n = 6, r0 = 0.70f, r1 = 0.70f, w = 2f, spin = -120f, param = 30f,
                flags = SEC or BASE),
        )
        // Erasure: a thin ring closes to a point, then the erasure slab (or a heavy burst without the execute).
        ReactionId.OBLIVION -> spec(0.65f, FxReach.TARGET, 0f,
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.I, FxColor.B, r0 = 1.10f, r1 = 0.02f, w = 3f, flags = SIG),
            layer(FxPrimitive.DOTS_IN, 0f, 0.40f, FxEase.I, FxColor.A, n = 8, r0 = 1f, r1 = 0.05f, param = 2.5f, flags = SIG),
            layer(FxPrimitive.DISC, 0.38f, 0.55f, FxEase.S, FxColor.BONE, r0 = 0.05f, r1 = 0.45f, alpha = 0.35f, flags = VERB),
            layer(FxPrimitive.WIPE, 0.40f, 0.75f, FxEase.S, FxColor.BONE, param = 0.35f, flags = VERB or SIG),
            layer(FxPrimitive.RING, 0.40f, 0.75f, FxEase.O, FxColor.A, r0 = 0.20f, r1 = 0.80f, w = 2f, flags = NOT_VERB),
        )
    }
}

/**
 * Skill cast and delayed-impact looks (hud-fx §4.7), by skill and phase. Reach is the cue's radius
 * (the effect's own radius), or [ReactionFxSpec.fixedReach] where the look has a fixed one.
 */
object SkillFxSpecs {
    const val PHASES: Int = 2
    private val specs: Array<ReactionFxSpec?> = Array(SkillId.entries.size * PHASES) { index ->
        build(SkillId.entries[index / PHASES], index % PHASES)
    }

    /** The look of [skill] at [phase], or null when that skill has no such phase. */
    fun of(skill: Int, phase: Int): ReactionFxSpec? {
        if (skill !in SkillId.entries.indices || phase !in 0 until PHASES) return null
        return specs[skill * PHASES + phase]
    }

    fun of(skill: SkillId, phase: Int): ReactionFxSpec? = of(skill.ordinal, phase)

    /** The longest skill look (seconds). */
    val maxLife: Float = specs.maxOf { it?.life ?: 0f }

    private fun build(skill: SkillId, phase: Int): ReactionFxSpec? = when (skill) {
        SkillId.SOLAR_FLARE -> if (phase != 0) null else spec(0.60f, FxReach.CUE, 0f,
            layer(FxPrimitive.DISC, 0f, 0.25f, FxEase.S, FxColor.A, r0 = 0.20f, r1 = 0.70f, alpha = 0.30f, flags = SIG),
            layer(FxPrimitive.FLAMES, 0f, 0.80f, FxEase.O, FxColor.A, n = 10, r0 = 0.30f, r1 = 1f, param = 0.12f, flags = SIG or FxFlag.PER_RANK),
            layer(FxPrimitive.SPOKES, 0f, 0.30f, FxEase.O, FxColor.BONE, n = 8, r0 = 4f, r1 = 24f, w = 1.5f, flags = CORE),
            layer(FxPrimitive.RING, 0.10f, 0.60f, FxEase.O, FxColor.A, r0 = 0.50f, r1 = 1f, w = 3f),
        )
        SkillId.METEOR -> when (phase) {
            0 -> spec(0.25f, FxReach.CUE, 0f,
                layer(FxPrimitive.DISC, 0f, 1f, FxEase.S, FxColor.BONE, r0 = 6f, r1 = 14f, alpha = 0.30f, flags = CORE or SIG),
            )
            else -> spec(0.70f, FxReach.CUE, 0f,
                layer(FxPrimitive.DISC, 0f, 0.30f, FxEase.S, FxColor.A, r0 = 0.20f, r1 = 0.80f, alpha = 0.35f, flags = SIG),
                layer(FxPrimitive.RING, 0f, 0.60f, FxEase.O, FxColor.A, r0 = 0.30f, r1 = 1.15f, w = 4.5f, flags = SIG),
                layer(FxPrimitive.SHARDS, 0f, 0.60f, FxEase.O, FxColor.BONE, n = 8, r0 = 0.20f, r1 = 1f, param = 12f),
                layer(FxPrimitive.CRACKS, 0f, 0.50f, FxEase.O, FxColor.A, n = 5, r0 = 0.10f, r1 = 0.70f, w = 2f, param = 0.5f),
                layer(FxPrimitive.RING, 0f, 0.30f, FxEase.O, FxColor.YOU, r0 = 6f, r1 = 60f, w = 2f, flags = CORE or FxFlag.CORE_INSIDE),
            )
        }
        SkillId.FROST_LINE -> if (phase != 0) null else spec(0.35f, FxReach.CUE, 0f,
            layer(FxPrimitive.HEX, 0f, 1f, FxEase.O, FxColor.A, r0 = 0.05f, r1 = 0.12f, w = 3f, flags = SIG),
        )
        SkillId.FROST_SNAP -> if (phase != 0) null else spec(0.55f, FxReach.CUE, 0f,
            layer(FxPrimitive.HEX, 0f, 0.35f, FxEase.O, FxColor.A, r0 = 0.10f, r1 = 1f, w = 3f, flags = SIG),
            layer(FxPrimitive.SPOKES, 0f, 0.50f, FxEase.O, FxColor.A, n = 6, r0 = 0.10f, r1 = 0.90f, w = 2f, flags = SIG),
            layer(FxPrimitive.TICKS, 0f, 0.60f, FxEase.O, FxColor.A, n = 6, r0 = 0.60f, r1 = 0.60f, w = 1.5f, param = 0.12f, flags = FxFlag.TANGENT),
            layer(FxPrimitive.DISC, 0f, 0.12f, FxEase.S, FxColor.BONE, r0 = 0.20f, r1 = 0.40f, alpha = 0.30f),
        )
        SkillId.ARC_HOOK -> if (phase != 0) null else spec(0.40f, FxReach.CUE, 0f,
            layer(FxPrimitive.RING, 0f, 1f, FxEase.O, FxColor.A, r0 = 0f, r1 = 30f, w = 2f, flags = CORE or SIG),
        )
        SkillId.STORM_COIL -> if (phase != 0) null else spec(0.50f, FxReach.CUE, 0f,
            layer(FxPrimitive.FORKS, 0f, 0.50f, FxEase.S, FxColor.A, n = 4, r0 = 0.05f, r1 = 0.45f, w = 2f, param = 0.12f, flags = SIG),
            layer(FxPrimitive.RING, 0f, 0.70f, FxEase.O, FxColor.A, r0 = 0.20f, r1 = 1f, w = 1.5f),
        )
        SkillId.GRAVITY_WELL -> when (phase) {
            0 -> spec(0.50f, FxReach.CUE, 0f,
                layer(FxPrimitive.RING, 0f, 0.60f, FxEase.I, FxColor.A, r0 = 1.20f, r1 = 0.30f, w = 4.5f, flags = SIG),
                layer(FxPrimitive.STREAKS_IN, 0f, 0.60f, FxEase.I, FxColor.A, n = 8, r0 = 1f, r1 = 0.30f, w = 1.5f, param = 0.15f),
            )
            else -> spec(0.30f, FxReach.CUE, 0f,
                layer(FxPrimitive.RING, 0f, 1f, FxEase.I, FxColor.A, r0 = 1f, r1 = 0.05f, w = 4.5f, flags = SIG),
            )
        }
        SkillId.MASS_DRIVER -> if (phase != 0) null else spec(0.50f, FxReach.CUE, 0f,
            layer(FxPrimitive.RING, 0f, 0.60f, FxEase.I, FxColor.A, r0 = 60f, r1 = 8f, w = 4.5f, flags = CORE or SIG),
            layer(FxPrimitive.TICKS, 0f, 0.70f, FxEase.O, FxColor.A, n = 4, r0 = 14f, r1 = 14f, w = 3f, param = 8f, flags = CORE),
        )
        SkillId.DECAY_SPORES -> if (phase != 0) null else spec(0.70f, FxReach.CUE, 0f,
            layer(FxPrimitive.DOTS, 0f, 0.80f, FxEase.O, FxColor.A, n = 16, r0 = 0f, r1 = 1f, param = 3f, flags = SIG),
            layer(FxPrimitive.DISC, 0f, 0.60f, FxEase.O, FxColor.A, r0 = 0.20f, r1 = 0.80f, alpha = 0.12f),
            layer(FxPrimitive.RING, 0.10f, 0.70f, FxEase.O, FxColor.A, r0 = 0.60f, r1 = 1f, w = 1.6f, flags = FxFlag.DOTTED),
        )
        SkillId.ENTROPIC_TIDE -> if (phase != 0) null else spec(0.70f, FxReach.CUE, 0f,
            layer(FxPrimitive.DOTS_IN, 0f, 0.60f, FxEase.I, FxColor.A, n = 20, r0 = 1f, r1 = 0.05f, param = 2.5f, flags = SIG),
            layer(FxPrimitive.RING, 0f, 0.60f, FxEase.I, FxColor.A, r0 = 1f, r1 = 0.20f, w = 2f),
            layer(FxPrimitive.RING, 0.55f, 0.75f, FxEase.O, FxColor.BONE, r0 = 2f, r1 = 30f, w = 3f, flags = CORE or SIG),
        )
        SkillId.SONIC_BOOM -> if (phase != 0) null else spec(0.50f, FxReach.CUE, 0f,
            layer(FxPrimitive.WAVES, 0f, 0.70f, FxEase.O, FxColor.A, n = 3, r0 = 0.10f, r1 = 1f, w = 3f, param = 70f, param2 = 0.12f,
                flags = DIR or SIG),
            layer(FxPrimitive.LANES, 0f, 0.40f, FxEase.O, FxColor.BONE, n = 3, r0 = 0f, r1 = 30f, w = 1.5f, param2 = 8f,
                flags = CORE or DIR or FxFlag.BACK),
            layer(FxPrimitive.DOTS, 0.10f, 0.80f, FxEase.O, FxColor.A, n = 6, r0 = 0.30f, r1 = 1f, param = 2.5f, param2 = 35f, flags = DIR),
        )
        SkillId.RESONATOR -> if (phase != 0) null else spec(0.50f, FxReach.CUE, 0f,
            layer(FxPrimitive.RING, 0f, 0.70f, FxEase.O, FxColor.A, r0 = 0.20f, r1 = 1f, w = 2f, flags = SIG),
            layer(FxPrimitive.PLATE, 0f, 0.70f, FxEase.P, FxColor.A, r0 = 0.03f, r1 = 0.04f, w = 2f),
        )
        SkillId.VOID_STEP -> when (phase) {
            0 -> spec(0.45f, FxReach.CUE, 0f,
                layer(FxPrimitive.LANES, 0f, 0.60f, FxEase.O, FxColor.A, n = 1, r0 = 1f, r1 = 1f, w = 1.5f,
                    flags = DIR or FxFlag.INK_CORE or SIG),
                layer(FxPrimitive.PUNCH, 0f, 0.60f, FxEase.O, FxColor.A, r0 = 2f, r1 = 10f, w = 1.5f, flags = FxFlag.WORLD),
            )
            else -> spec(0.50f, FxReach.FIXED, 90f,
                layer(FxPrimitive.PUNCH, 0f, 0.60f, FxEase.O, FxColor.A, r0 = 0.10f, r1 = 0.90f, w = 3f, flags = SIG),
                layer(FxPrimitive.RING, 0f, 0.60f, FxEase.O, FxColor.A, r0 = 0.30f, r1 = 1.10f, w = 3f),
                layer(FxPrimitive.DOTS, 0.10f, 0.80f, FxEase.O, FxColor.A, n = 6, r0 = 0.30f, r1 = 1f, param = 2.5f),
            )
        }
        SkillId.NULL_SHROUD -> when (phase) {
            0 -> spec(0.40f, FxReach.CUE, 0f,
                layer(FxPrimitive.RING, 0f, 1f, FxEase.I, FxColor.A, r0 = 80f, r1 = 44f, w = 3f, flags = CORE or SIG),
            )
            else -> spec(0.40f, FxReach.CUE, 0f,
                layer(FxPrimitive.RING, 0f, 1f, FxEase.O, FxColor.A, r0 = 44f, r1 = 120f, w = 2f, flags = CORE or SIG),
            )
        }
        SkillId.SLINGSHOT -> if (phase != 0) null else spec(0.45f, FxReach.CUE, 0f,
            layer(FxPrimitive.ARCS, 0f, 0.70f, FxEase.O, FxColor.A, n = 1, r0 = 24f, r1 = 24f, w = 3f, param = 120f, flags = CORE or DIR or SIG),
            layer(FxPrimitive.LANES, 0f, 0.50f, FxEase.O, FxColor.A, n = 3, r0 = 0f, r1 = 30f, w = 1.5f, param2 = 8f, flags = CORE or DIR),
        )
        SkillId.FULL_STOP -> if (phase != 0) null else spec(0.50f, FxReach.CUE, 0f,
            layer(FxPrimitive.RING, 0f, 0.70f, FxEase.O, FxColor.A, r0 = 0.10f, r1 = 1f, w = 4.5f, flags = SIG),
            layer(FxPrimitive.RING, 0.10f, 0.60f, FxEase.O, FxColor.BONE, r0 = 0.10f, r1 = 0.80f, w = 1.5f),
            layer(FxPrimitive.LANES, 0f, 0.40f, FxEase.O, FxColor.BONE, n = 2, r0 = 40f, r1 = 40f, w = 2f, param2 = 10f,
                flags = CORE or DIR or FxFlag.BACK),
            layer(FxPrimitive.TICKS, 0f, 0.70f, FxEase.O, FxColor.BONE, n = 8, r0 = 0.50f, r1 = 0.50f, w = 2f, param = 0.10f, flags = FxFlag.RANK3),
        )
        SkillId.CATALYZE -> if (phase != 0) null else spec(0.55f, FxReach.CUE, 0f,
            layer(FxPrimitive.SPLIT_RING, 0f, 0.70f, FxEase.O, FxColor.AB, r0 = 0.20f, r1 = 1f, w = 2f, spin = 90f, param = 172f, flags = SIG),
        )
        SkillId.DUAL_INFUSION -> if (phase != 0) null else spec(0.50f, FxReach.CUE, 0f,
            layer(FxPrimitive.ARCS, 0f, 1f, FxEase.O, FxColor.AB, n = 2, r0 = 14f, r1 = 14f, w = 3f, spin = 180f, param = 170f, flags = CORE or SIG),
        )
    }
}

/**
 * The Apex activation (hud-fx §4.8): gold rings out to 280, gold spokes and a bone flash at the
 * Core's position; the character's silhouette outline growing to three Core radii is drawn by the
 * renderer over the first half.
 */
object ApexFxSpec {
    const val REACH: Float = 280f
    const val SILHOUETTE_END: Float = 0.5f

    val activation: ReactionFxSpec = spec(1f, FxReach.FIXED, REACH,
        layer(FxPrimitive.RING, 0f, 0.60f, FxEase.O, FxColor.GOLD, r0 = 0.093f, r1 = 1f, w = 4.5f, flags = SIG),
        layer(FxPrimitive.RING, 0.15f, 0.75f, FxEase.O, FxColor.GOLD, r0 = 0.093f, r1 = 0.714f, w = 2f),
        layer(FxPrimitive.SPOKES, 0f, 0.40f, FxEase.O, FxColor.GOLD_LIGHT, n = 12, r0 = 0.15f, r1 = 0.45f, w = 2f),
        layer(FxPrimitive.DISC, 0f, 0.15f, FxEase.S, FxColor.BONE, r0 = 0.107f, r1 = 0.286f, alpha = 0.30f),
    )
}

/** How a styled link is drawn (hud-fx §4.5). */
enum class LinkKind {
    /** The weapon's Arc Coil zigzag. */
    WEAPON,

    /** Dots hopping along a bulging arc (Wildfire, Flame Leap). */
    EMBER,

    /** Straight jittered lightning with a glow. */
    LIGHTNING,

    /** A frost trail brightening toward its end. */
    FROST,

    /** An ink beam with colour rims and travelling sparks (Annihilate). */
    BEAM,

    /** A taut line with a bone core (Arc Hook, Catalyze, Apex). */
    TAUT,

    /** Small sound arcs along the link (Resonator). */
    SOUND,
}

/** Which colours a link look takes: the style's element pair, its skill, element or the gold. */
enum class LinkTone { REACTION, SKILL, ELEMENT, GOLD, BONE }

/**
 * One link look: [kind], its presentation [life] (a styled link lives at least this long),
 * [segments] and [jitter] for lightning, and the end and middle marks it carries.
 */
class LinkLook(
    val kind: LinkKind,
    val tone: LinkTone,
    val life: Float,
    val segments: Int = 5,
    val jitter: Float = 8f,
    /** Colour A on the dots (Wildfire) or ink dots rimmed B with an A head (Flame Leap). */
    val inkDots: Boolean = false,
    /** Lightning: B pits at ¼, ½, ¾ (Feedback). */
    val pits: Boolean = false,
    /** Lightning: perpendicular B bars at ⅓ and ⅔ (Resonant Arc). */
    val bars: Boolean = false,
    /** A hexagon of radius 5 at the end (Superconduct). */
    val endHex: Boolean = false,
    /** An end shard along the ray (frost trails). */
    val endShard: Boolean = false,
    /** Two B rings at the end (Shatterpoint). */
    val endRings: Boolean = false,
    /** An end ring of this radius (0 none). */
    val endRing: Float = 0f,
    /** A bone core under the colour for lightning (Storm Coil). */
    val boneCore: Boolean = false,
)

/** Link looks by `LinkStyleCode` (0 weapon, 1 + reaction, 32 + skill, 64 + element Break, 96 + character Apex). */
object LinkLooks {
    const val REACTION_BASE: Int = 1
    const val SKILL_BASE: Int = 32
    const val BREAK_BASE: Int = 64
    const val APEX_BASE: Int = 96
    const val CODES: Int = 128

    /** The longest styled link presentation (seconds). */
    const val MAX_LIFE: Float = 0.35f

    private val weapon = LinkLook(LinkKind.WEAPON, LinkTone.REACTION, 0f)
    private val looks: Array<LinkLook> = Array(CODES) { build(it) }

    operator fun get(style: Int): LinkLook = if (style in 0 until CODES) looks[style] else weapon

    /** Whether [style] names a reaction, skill, element or character ([style] 0 and unknown codes draw the weapon look). */
    fun isKnown(style: Int): Boolean = style == 0 || (style in 0 until CODES && looks[style] !== weapon)

    /** The reaction ordinal of a reaction style, else −1. */
    fun reaction(style: Int): Int = (style - REACTION_BASE).takeIf { style >= REACTION_BASE && it < ReactionId.entries.size } ?: -1

    /** The skill ordinal of a skill style, else −1. */
    fun skill(style: Int): Int = (style - SKILL_BASE).takeIf { style >= SKILL_BASE && it < SkillId.entries.size } ?: -1

    /** The element ordinal of a Break style, else −1. */
    fun element(style: Int): Int = (style - BREAK_BASE).takeIf { style >= BREAK_BASE && it < StatusElement.entries.size } ?: -1

    private fun build(style: Int): LinkLook {
        if (style == 0) return weapon
        val reaction = ReactionId.entries.getOrNull(style - REACTION_BASE)?.takeIf { style < SKILL_BASE }
        if (reaction != null) return when (reaction) {
            ReactionId.WILDFIRE -> LinkLook(LinkKind.EMBER, LinkTone.REACTION, 0.30f)
            ReactionId.SUPERCONDUCT -> LinkLook(LinkKind.LIGHTNING, LinkTone.REACTION, 0.22f, segments = 4, jitter = 4f, endHex = true)
            ReactionId.GLACIER_CRUSH -> LinkLook(LinkKind.FROST, LinkTone.REACTION, 0.25f, endShard = true)
            ReactionId.FEEDBACK -> LinkLook(LinkKind.LIGHTNING, LinkTone.REACTION, 0.22f, segments = 6, jitter = 12f, pits = true)
            ReactionId.SHATTERPOINT -> LinkLook(LinkKind.FROST, LinkTone.REACTION, 0.25f, endRings = true)
            ReactionId.RESONANT_ARC -> LinkLook(LinkKind.LIGHTNING, LinkTone.REACTION, 0.22f, segments = 5, jitter = 8f, bars = true)
            ReactionId.BLACKFIRE -> LinkLook(LinkKind.EMBER, LinkTone.REACTION, 0.35f, inkDots = true)
            ReactionId.ANNIHILATE -> LinkLook(LinkKind.BEAM, LinkTone.REACTION, 0.30f)
            else -> LinkLook(LinkKind.LIGHTNING, LinkTone.REACTION, 0.22f, segments = 5, jitter = 8f)
        }
        val skill = SkillId.entries.getOrNull(style - SKILL_BASE)?.takeIf { style >= SKILL_BASE && style < BREAK_BASE }
        if (skill != null) return when (skill) {
            SkillId.ARC_HOOK -> LinkLook(LinkKind.TAUT, LinkTone.SKILL, 0.25f, endRing = 9f)
            SkillId.STORM_COIL -> LinkLook(LinkKind.LIGHTNING, LinkTone.SKILL, 0.18f, segments = 4, jitter = 10f, boneCore = true)
            SkillId.RESONATOR -> LinkLook(LinkKind.SOUND, LinkTone.SKILL, 0.30f)
            SkillId.CATALYZE -> LinkLook(LinkKind.TAUT, LinkTone.BONE, 0.25f, endRing = 6f)
            else -> LinkLook(LinkKind.LIGHTNING, LinkTone.SKILL, 0.22f, segments = 5, jitter = 8f)
        }
        val element = StatusElement.entries.getOrNull(style - BREAK_BASE)?.takeIf { style >= BREAK_BASE && style < APEX_BASE }
        if (element != null) {
            return if (element == StatusElement.ION) LinkLook(LinkKind.LIGHTNING, LinkTone.ELEMENT, 0.20f, segments = 6, jitter = 10f)
            else LinkLook(LinkKind.LIGHTNING, LinkTone.ELEMENT, 0.20f, segments = 5, jitter = 8f)
        }
        if (style >= APEX_BASE && style - APEX_BASE < kinetickk.ball.content.api.CoreShape.entries.size) {
            return LinkLook(LinkKind.TAUT, LinkTone.GOLD, 0.25f)
        }
        return weapon
    }
}
