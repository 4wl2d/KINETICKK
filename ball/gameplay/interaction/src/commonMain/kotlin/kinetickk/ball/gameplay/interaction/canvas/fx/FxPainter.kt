// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.fx

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.fx.FxColor
import kinetickk.ball.gameplay.interaction.fx.FxFlag
import kinetickk.ball.gameplay.interaction.fx.FxLayer
import kinetickk.ball.gameplay.interaction.fx.FxPrimitive
import kinetickk.ball.gameplay.interaction.fx.ReactionFxSpec
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kotlin.math.max
import kotlin.math.roundToInt
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.world.*

private const val TAU = CanvasMath.TAU

/** Golden-ratio increment of the per-element hash (`0x9E3779B9`). */
private const val GOLDEN = -0x61c88647

/** The fixed angle the hash takes for element 0, used as a look's default heading. */
private const val HEADING = 0

/**
 * A stable pseudo-random value in [0, 1) for element [index] of a look with [seed]: the 32-bit
 * finalizer of MurmurHash3 over `seed xor (index · 0x9E3779B9)`. Pure, allocation-free.
 */
internal fun fxHash(seed: Int, index: Int): Float {
    var h = seed xor (index * GOLDEN)
    h = h xor (h ushr 16)
    h *= -0x7a143595
    h = h xor (h ushr 13)
    h *= -0x3d4d51cb
    h = h xor (h ushr 16)
    return (h ushr 8) * (1f / 16_777_216f)
}

/** Draw-operation pools of the world FX ([FxDrawBudget]). */
internal enum class FxPool(val ops: Int) {
    TRANSIENT(900),
    LINKS(600),
    FIELDS(220),
    GLYPHS(480),
    CORE_STATES(100),
    /** The shards of shattering structures (one line each; floors and the slabs' flash draw regardless). */
    RUBBLE(360),
    /** Dungeon motes, telegraphs and moments (motes are secondary; telegraphs, the guardian and moments are signature). */
    DUNGEON(320),
}

/**
 * Per-frame draw-operation budget of the world FX (hud-fx §4.11). Every primitive reports its
 * operations before drawing: a non-signature layer that does not fit its pool is skipped, a
 * signature layer always draws and is recorded. Reset by [FxFrame.begin]; draw-thread confined.
 */
internal object FxDrawBudget {
    private val used = IntArray(FxPool.entries.size)

    /** Signature operations drawn past a pool's end, per pool (tests read them). */
    private val overflow = IntArray(FxPool.entries.size)

    /** Signature layers drawn and non-signature layers skipped, per pool. */
    private val signatures = IntArray(FxPool.entries.size)
    private val skipped = IntArray(FxPool.entries.size)

    fun reset() {
        used.fill(0)
        overflow.fill(0)
        signatures.fill(0)
        skipped.fill(0)
    }

    fun used(pool: FxPool): Int = used[pool.ordinal]

    fun overflow(pool: FxPool): Int = overflow[pool.ordinal]

    fun signatures(pool: FxPool): Int = signatures[pool.ordinal]

    fun skipped(pool: FxPool): Int = skipped[pool.ordinal]

    fun remaining(pool: FxPool): Int = pool.ops - used[pool.ordinal]

    /** Takes [ops] from [pool] when they fit; a [signature] always takes them (recording any overflow). */
    fun take(pool: FxPool, ops: Int, signature: Boolean): Boolean {
        val index = pool.ordinal
        val left = pool.ops - used[index]
        if (signature) {
            signatures[index]++
            overflow[index] += max(0, ops - max(0, left))
            used[index] += ops
            return true
        }
        if (ops > left) {
            skipped[index]++
            return false
        }
        used[index] += ops
        return true
    }
}

/**
 * World FX state of the frame being drawn: how many enemies are on screen (crowd level of detail,
 * capped just past [CROWD_SIG_ONLY]) and the reaction effects setting. [begin] runs once per world
 * frame before anything draws; draw-thread confined.
 */
internal object FxFrame {
    /** Above this many on-screen enemies secondary layers go (the status patterns' limit). */
    const val CROWD_SECONDARY_OFF = STATUS_PATTERN_ENEMY_LIMIT

    /** Above this many only signature layers draw. */
    const val CROWD_SIG_ONLY = 90

    var crowd: Int = 0
        private set
    var level: ReactionEffectsLevel = ReactionEffectsLevel.FULL
        private set

    fun begin(crowd: Int, level: ReactionEffectsLevel) {
        this.crowd = crowd
        this.level = level
        FxDrawBudget.reset()
    }

    /** Peak alpha of reaction, Break, skill and Apex looks for the setting. */
    val peakAlpha: Float
        get() = when (level) {
            ReactionEffectsLevel.FULL -> 1f
            ReactionEffectsLevel.REDUCED -> 0.85f
            ReactionEffectsLevel.MINIMAL -> 0.70f
        }

    /** Element counts for the setting (at least one element survives). */
    val countScale: Float get() = if (level == ReactionEffectsLevel.FULL) 1f else 0.6f

    /** Field fills for the setting (rims never change). */
    val fillScale: Float
        get() = when (level) {
            ReactionEffectsLevel.FULL -> 1f
            ReactionEffectsLevel.REDUCED -> 0.75f
            ReactionEffectsLevel.MINIMAL -> 0.5f
        }

    /** Styled links draw as one line at the minimal setting. */
    val plainLinks: Boolean get() = level == ReactionEffectsLevel.MINIMAL
}

/**
 * Scratch for drawing one look: its centre and the Core in layer units, the reach, the burst's
 * progress and age, the seed, heading and colours, and which layers it shows. One instance,
 * refilled per burst; nothing allocates.
 */
internal object FxContext {
    var x = 0f
    var y = 0f
    var coreX = 0f
    var coreY = 0f
    var coreRadius = 16f
    var reach = 1f
    var progress = 0f
    var age = 0f
    var elapsed = 0f
    var seed = 0
    var theta = 0f
    var a: Color = Color.White
    var b: Color = Color.White
    var you: Color = Color.White
    var gold: Color = Color.White
    var goldLight: Color = Color.White
    var alpha = 1f
    var rank = 1
    var verb = false
    var keystone = false
    var coreInside = false
    var signatureOnly = false
    var secondaryOff = false

    /** The heading: [direction] when it is a number, else one hashed from the seed. */
    fun heading(direction: Float, seed: Int): Float = if (direction.isNaN()) fxHash(seed, HEADING) * TAU else direction

    fun color(color: FxColor, index: Int): Color = when (color) {
        FxColor.A -> a
        FxColor.B -> b
        FxColor.BONE -> Kk.Bone
        FxColor.INK -> Kk.Ink
        FxColor.AB -> if (index % 2 == 0) a else b
        FxColor.YOU -> you
        FxColor.GOLD -> gold
        FxColor.GOLD_LIGHT -> goldLight
    }

    /** Whether [layer] belongs to this burst's form and level of detail (before the budget). */
    fun shows(layer: FxLayer): Boolean {
        val flags = layer.flags
        if (flags and FxFlag.KEY != 0 && !keystone) return false
        if (flags and FxFlag.BASE != 0 && keystone) return false
        val forced = keystone && flags and FxFlag.KEY_ALWAYS != 0
        if (flags and FxFlag.VERB != 0 && !verb && !forced) return false
        if (flags and FxFlag.NOT_VERB != 0 && verb && !forced) return false
        if (flags and FxFlag.RANK3 != 0 && rank < 3) return false
        if (flags and FxFlag.CORE_INSIDE != 0 && !coreInside) return false
        val signature = flags and FxFlag.SIG != 0
        if (signatureOnly && !signature) return false
        if (secondaryOff && flags and FxFlag.SEC != 0) return false
        return true
    }
}

/** Draws every layer of [spec] the context shows, within the [pool] budget. */
internal fun DrawScope.drawFxSpec(spec: ReactionFxSpec, pool: FxPool) {
    val layers = spec.layers
    val scale = FxFrame.countScale
    for (index in layers.indices) {
        val layer = layers[index]
        if (!FxContext.shows(layer)) continue
        val progress = FxContext.progress
        if (progress < layer.t0 || progress > layer.t1) continue
        val elements = elementsOf(layer, scale)
        if (!FxDrawBudget.take(pool, layer.ops(elements), layer.has(FxFlag.SIG))) continue
        drawFxLayer(layer, elements)
    }
}

private fun elementsOf(layer: FxLayer, scale: Float): Int {
    val count = layer.elements(FxContext.rank)
    return if (scale >= 1f) count else max(1, (count * scale).roundToInt())
}

/** Vertices of a [FxPrimitive.QUAKE] ring. */
internal const val QUAKE_VERTICES = 12

/** A jagged gravitic ring (the quake shockwaves, colour indices 16..22) with [seed]'s vertices. */
internal fun DrawScope.drawQuakeRing(center: Offset, radius: Float, seed: Int, color: Color, alpha: Float, width: Float) {
    var previous = Offset.Zero
    var first = Offset.Zero
    for (i in 0..QUAKE_VERTICES) {
        val vertex = i % QUAKE_VERTICES
        val distance = radius * (1f + 0.24f * (fxHash(seed, 60 + vertex) - 0.5f))
        val point = polar(center, distance, vertex * TAU / QUAKE_VERTICES)
        if (i == 0) first = point else drawLine(color, previous, if (vertex == 0) first else point, width, alpha = alpha)
        previous = point
    }
}
