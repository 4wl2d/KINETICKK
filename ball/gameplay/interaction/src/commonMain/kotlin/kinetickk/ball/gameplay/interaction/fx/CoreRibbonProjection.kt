// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kotlin.math.max
import kotlin.math.min

/**
 * The Core's recent path for the ribbon behind it, newest sample first: run-local world positions at the start of past
 * fixed steps, spaced by distance, with their age in simulation seconds. Immutable: each published projection owns
 * fresh arrays copied from the reducer's ring.
 */
class CoreRibbonProjection private constructor(
    /** x0, y0, x1, y1, ... (newest first). */
    private val positions: FloatArray,
    private val ages: FloatArray,
    private val flags: IntArray,
) {
    val size: Int get() = ages.size

    fun x(index: Int): Float = positions[index * 2]

    fun y(index: Int): Float = positions[index * 2 + 1]

    fun age(index: Int): Float = ages[index]

    /** The sample begins a chain: no segment joins it to the older sample after it (run start or a teleport). */
    fun startsChain(index: Int): Boolean = flags[index] and START != 0

    /** Recorded while a Dash phase ran. */
    fun dash(index: Int): Boolean = flags[index] and DASH != 0

    override fun equals(other: Any?): Boolean = other is CoreRibbonProjection && positions.contentEquals(other.positions) &&
        ages.contentEquals(other.ages) && flags.contentEquals(other.flags)

    override fun hashCode(): Int = (positions.contentHashCode() * 31 + ages.contentHashCode()) * 31 + flags.contentHashCode()

    companion object {
        const val START = 1
        const val DASH = 2

        val EMPTY = CoreRibbonProjection(FloatArray(0), FloatArray(0), IntArray(0))

        /**
         * Copies the arrays (newest first): [positions] holds two floats per sample, [ages] and [flags] one each; at most
         * [InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES] samples. No samples give [EMPTY].
         */
        internal fun of(positions: FloatArray, ages: FloatArray, flags: IntArray): CoreRibbonProjection {
            val size = ages.size
            require(positions.size == size * 2 && flags.size == size) {
                "a Core ribbon of $size samples needs ${size * 2} coordinates and $size flags, not ${positions.size} and ${flags.size}"
            }
            return of(positions, ages, flags, size)
        }

        /**
         * Copies the first [size] samples (newest first) of arrays that may be longer, such as a reducer's reused staging
         * arrays; at most [InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES]. No samples give [EMPTY].
         */
        internal fun of(positions: FloatArray, ages: FloatArray, flags: IntArray, size: Int): CoreRibbonProjection {
            require(size <= InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES) {
                "a Core ribbon holds at most ${InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES} samples, not $size"
            }
            require(size >= 0 && positions.size >= size * 2 && ages.size >= size && flags.size >= size) {
                "$size Core ribbon samples need ${size * 2} coordinates and $size ages and flags, not ${positions.size}, " +
                    "${ages.size} and ${flags.size}"
            }
            if (size == 0) return EMPTY
            return CoreRibbonProjection(positions.copyOf(size * 2), ages.copyOf(size), flags.copyOf(size))
        }
    }
}

/** Arc length (world units) the ribbon draws at [speed]: today's wedge length up to 920 u/s, growing beyond it. */
internal fun ribbonLength(speed: Float): Float = min(RIBBON_MAX_LENGTH, RIBBON_BASE_LENGTH + speed * RIBBON_LENGTH_PER_SPEED)

/** Distance between stored samples at [speed]: the drawn length in 48 parts, at least 4 units. */
internal fun ribbonSpacing(speed: Float): Float = max(RIBBON_MIN_SPACING, ribbonLength(speed) / RIBBON_SPACING_DIVISOR)

internal const val RIBBON_BASE_LENGTH = 80f
internal const val RIBBON_LENGTH_PER_SPEED = 260f / 920f
internal const val RIBBON_MAX_LENGTH = 860f
internal const val RIBBON_SPACING_DIVISOR = 48f
internal const val RIBBON_MIN_SPACING = 4f

/** Samples older than this (simulation seconds) are dropped: a stopped Core's ribbon fades out. */
internal const val RIBBON_MAX_AGE = 0.45f

/** Below this speed (u/s) no sample is stored and nothing is drawn, as with today's tail (outside a Dash). */
internal const val RIBBON_MIN_SPEED = 25f

/** A step whose travel exceeds this many times the speed's reach (plus [RIBBON_TELEPORT_SLACK] units) is a teleport. */
internal const val RIBBON_TELEPORT_FACTOR = 2f
internal const val RIBBON_TELEPORT_SLACK = 48f
