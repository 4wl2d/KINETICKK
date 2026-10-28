// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.WorldLayer

/*
 * Stateless hashes for everything placed in absolute space or rolled once per run. None of them draws from a run
 * stream, so a lane's placement never shifts offers, spawns or crits.
 *
 * Id namespaces (all non-zero bits disjoint by construction):
 * - obstacle ids: bits 0-3 slot, 4-31 chunkY, 32-59 chunkX, 60-62 world layer, bit 63 = 0;
 * - structureId = bit 63 | layer shl 60 | cellX (26 bits) shl 34 | cellY (26 bits) shl 8; a wall id is
 *   structureId or its index (0..47), structureSurfaceId = structureId or 0xFF;
 * - encounter tags: the owner byte in bits 56-63, the owner's id in the low 56 bits.
 * Each lane keeps its salts in its own files and registers them in its notes.
 */

/** 0 for BASE, so realspace hashes equal the pre-layer ones. */
internal fun layerSalt(layer: WorldLayer): Long = when (layer) {
    WorldLayer.BASE -> 0L
    WorldLayer.ZENITH -> 0x7E41_70A5_1A7E_0001L
    WorldLayer.NADIR -> 0x4AD1_2EA7_1A7E_0002L
    WorldLayer.ARCHIVE -> 0x0A2C_41BE_1A7E_0003L
}

/** Stateless SplitMix64 of the run seed, the layer, two coordinates ([a], [b]) and a salt; identical on every platform. */
internal fun worldHash(runSeed: Int, layer: WorldLayer, a: Int, b: Int, salt: Long): Long {
    var mixed = splitMix64(runSeed.toLong() xor salt xor layerSalt(layer))
    mixed = splitMix64(mixed xor a.toLong())
    return splitMix64(mixed xor (b.toLong() shl 1))
}

/** [worldHash] in realspace. */
internal fun worldHash(runSeed: Int, a: Int, b: Int, salt: Long): Long = worldHash(runSeed, WorldLayer.BASE, a, b, salt)

/** Stateless per-run hash of a salt and a key (a plan index, a visit serial). */
internal fun runHash(runSeed: Int, salt: Long, key: Long): Long = splitMix64(splitMix64(runSeed.toLong() xor salt) xor key)

/** Uniform [0, 1) from the top 24 bits (exact on every platform). */
internal fun hashUnit(hash: Long): Float = (hash ushr 40).toFloat() / (1 shl 24)

internal fun splitMix64(value: Long): Long {
    var z = value + -0x61c8864680b583ebL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
}

/** Candidate-private SplitMix64 stream seeded by a hash (obstacle chunks, variant rolls, placements). */
internal class HashRandom(private var state: Long) {
    private fun next(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform in [0, 1) with 24 exact bits, identical on every platform. */
    fun nextFloat(): Float = (next() ushr 40).toInt() / 16_777_216f

    fun nextInt(): Int = (next() ushr 32).toInt()

    /** Uniform in 0 until [bound] (positive). */
    fun nextIndex(bound: Int): Int = ((nextInt().toLong() and 0xFFFF_FFFFL) % bound).toInt()
}

/** Absolute coordinates of a run-local point. */
internal fun MutableGameState.absoluteX(x: Float): Double = worldOriginX + x
internal fun MutableGameState.absoluteY(y: Float): Double = worldOriginY + y

/** Absolute distance² of a run-local point from the run's spawn (the absolute origin), in Double. */
internal fun MutableGameState.absoluteDistanceSquared(x: Float, y: Float): Double {
    val absoluteX = absoluteX(x)
    val absoluteY = absoluteY(y)
    return absoluteX * absoluteX + absoluteY * absoluteY
}
