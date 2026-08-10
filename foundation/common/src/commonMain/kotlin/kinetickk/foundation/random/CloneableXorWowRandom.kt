// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.random

import kotlin.random.Random

/**
 * A snapshot-capable implementation of the seeded random generator used by Kotlin 2.3.20.
 *
 * The state transition, seed expansion, and 64-value warm-up match
 * `kotlin.random.Random(seed: Int)` in Kotlin 2.3.20. Like Kotlin's seeded generator, this
 * class is not thread-safe.
 *
 * The XorWow algorithm and compatibility behavior are based on Kotlin's Apache-2.0-licensed
 * `kotlin.random.XorWowRandom` implementation.
 */
class CloneableXorWowRandom private constructor(
    private var x: Int,
    private var y: Int,
    private var z: Int,
    private var w: Int,
    private var v: Int,
    private var addend: Int,
    warmUp: Boolean,
) : Random() {
    /** An immutable cursor that can recreate this generator's exact next value. */
    data class Snapshot(
        val x: Int,
        val y: Int,
        val z: Int,
        val w: Int,
        val v: Int,
        val addend: Int,
    )

    /** Creates a generator compatible with `Random(seed)` from Kotlin 2.3.20. */
    constructor(seed: Int) : this(
        x = seed,
        y = seed.shr(31),
        z = 0,
        w = 0,
        v = seed.inv(),
        addend = (seed shl 10) xor (seed.shr(31) ushr 4),
        warmUp = true,
    )

    init {
        require((x or y or z or w or v) != 0) {
            "XorWow state must contain at least one non-zero element."
        }

        if (warmUp) {
            repeat(WARM_UP_VALUES) { nextInt() }
        }
    }

    /** Returns an immutable representation of the current random cursor. */
    fun snapshot(): Snapshot = Snapshot(
        x = x,
        y = y,
        z = z,
        w = w,
        v = v,
        addend = addend,
    )

    /** Returns an independent generator positioned at the same cursor. */
    fun copy(): CloneableXorWowRandom = fromSnapshot(snapshot())

    override fun nextInt(): Int {
        var transition = x
        transition = transition xor (transition ushr 2)
        x = y
        y = z
        z = w
        val previousV = v
        w = previousV
        transition = (transition xor (transition shl 1)) xor previousV xor (previousV shl 4)
        v = transition
        addend += ADDEND_INCREMENT
        return transition + addend
    }

    override fun nextBits(bitCount: Int): Int =
        nextInt().ushr(Int.SIZE_BITS - bitCount) and (-bitCount).shr(Int.SIZE_BITS - 1)

    companion object {
        private const val ADDEND_INCREMENT = 362_437
        private const val WARM_UP_VALUES = 64

        /** Recreates a generator without re-running the seed warm-up. */
        fun fromSnapshot(snapshot: Snapshot): CloneableXorWowRandom = CloneableXorWowRandom(
            x = snapshot.x,
            y = snapshot.y,
            z = snapshot.z,
            w = snapshot.w,
            v = snapshot.v,
            addend = snapshot.addend,
            warmUp = false,
        )
    }
}
