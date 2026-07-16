// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.random

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CloneableXorWowRandomTest {
    @Test
    fun nextIntMatchesKotlinSeededRandom() {
        val goldenRandom = CloneableXorWowRandom(GOLDEN_SEED)
        assertContentEquals(
            KOTLIN_2_3_20_NEXT_INTS,
            IntArray(KOTLIN_2_3_20_NEXT_INTS.size) { goldenRandom.nextInt() },
        )

        COMPATIBILITY_SEEDS.forEach { seed ->
            val expected = Random(seed)
            val actual = CloneableXorWowRandom(seed)

            repeat(128) { index ->
                assertEquals(
                    expected = expected.nextInt(),
                    actual = actual.nextInt(),
                    message = "nextInt mismatch for seed=$seed at index=$index",
                )
            }
        }
    }

    @Test
    fun nextFloatMatchesKotlinSeededRandomBitForBit() {
        val goldenRandom = CloneableXorWowRandom(GOLDEN_SEED)
        assertContentEquals(
            KOTLIN_2_3_20_NEXT_FLOAT_BITS,
            IntArray(KOTLIN_2_3_20_NEXT_FLOAT_BITS.size) { goldenRandom.nextFloat().toBits() },
        )

        COMPATIBILITY_SEEDS.forEach { seed ->
            val expected = Random(seed)
            val actual = CloneableXorWowRandom(seed)

            repeat(128) { index ->
                assertEquals(
                    expected = expected.nextFloat().toBits(),
                    actual = actual.nextFloat().toBits(),
                    message = "nextFloat mismatch for seed=$seed at index=$index",
                )
            }
        }
    }

    @Test
    fun boundedNextIntMatchesKotlinSeededRandom() {
        val goldenRandom = CloneableXorWowRandom(GOLDEN_SEED)
        assertContentEquals(
            KOTLIN_2_3_20_BOUNDED_INTS,
            BOUNDS.map { goldenRandom.nextInt(it) }.toIntArray(),
        )

        COMPATIBILITY_SEEDS.forEach { seed ->
            val expected = Random(seed)
            val actual = CloneableXorWowRandom(seed)

            repeat(16) { pass ->
                BOUNDS.forEach { bound ->
                    assertEquals(
                        expected = expected.nextInt(bound),
                        actual = actual.nextInt(bound),
                        message = "nextInt($bound) mismatch for seed=$seed on pass=$pass",
                    )
                }
            }
        }
    }

    @Test
    fun snapshotAndCopyPreserveTheCursorWithoutAliasing() {
        val original = CloneableXorWowRandom(0x1357_9BDF)
        repeat(29) { index ->
            when (index % 3) {
                0 -> original.nextInt()
                1 -> original.nextFloat()
                else -> original.nextInt(97)
            }
        }

        val copied = original.copy()
        val restored = CloneableXorWowRandom.fromSnapshot(original.snapshot())

        repeat(128) { index ->
            val expected = original.nextInt()
            assertEquals(expected, copied.nextInt(), "copy mismatch at index=$index")
            assertEquals(expected, restored.nextInt(), "snapshot mismatch at index=$index")
        }

        copied.nextInt()
        assertEquals(original.nextInt(), restored.nextInt(), "advancing a copy changed another cursor")
    }

    @Test
    fun shuffledSequenceMatchesAfterPartialConsumptionAndCopy() {
        val expectedRandom = Random(SHUFFLE_SEED)
        val actualRandom = CloneableXorWowRandom(SHUFFLE_SEED)

        repeat(37) { index ->
            when (index % 3) {
                0 -> assertEquals(expectedRandom.nextInt(), actualRandom.nextInt())
                1 -> assertEquals(expectedRandom.nextFloat().toBits(), actualRandom.nextFloat().toBits())
                else -> assertEquals(expectedRandom.nextInt(1_003), actualRandom.nextInt(1_003))
            }
        }

        val copiedRandom = actualRandom.copy()
        val expected = (0 until 48).toMutableList().also { it.shuffle(expectedRandom) }
        val actual = (0 until 48).toMutableList().also { it.shuffle(actualRandom) }
        val copied = (0 until 48).toMutableList().also { it.shuffle(copiedRandom) }

        assertEquals(expected, actual)
        assertEquals(expected, copied)
        assertEquals(KOTLIN_2_3_20_SHUFFLE, actual)
        val nextExpected = expectedRandom.nextInt()
        assertEquals(KOTLIN_2_3_20_POST_SHUFFLE_NEXT_INT, nextExpected)
        assertEquals(nextExpected, actualRandom.nextInt())
        assertEquals(nextExpected, copiedRandom.nextInt())
    }

    private companion object {
        const val GOLDEN_SEED = 0x1234_5678
        val COMPATIBILITY_SEEDS = intArrayOf(
            Int.MIN_VALUE,
            -1,
            0,
            1,
            0x1234_5678,
            Int.MAX_VALUE,
        )

        val BOUNDS = intArrayOf(1, 2, 3, 7, 16, 31, 256, 1_003, 65_537, Int.MAX_VALUE)
        const val SHUFFLE_SEED = -0x2468_ACE
        const val KOTLIN_2_3_20_POST_SHUFFLE_NEXT_INT = 1_891_435_637

        val KOTLIN_2_3_20_NEXT_INTS = intArrayOf(
            -558_634_854,
            -507_122_293,
            -1_167_331_863,
            -7_802_200,
            -2_125_690_654,
            622_028_967,
            963_918_829,
            1_072_327_782,
            -1_468_634_230,
            -701_461_646,
            -836_740_540,
            -1_694_429_391,
        )

        val KOTLIN_2_3_20_NEXT_FLOAT_BITS = intArrayOf(
            1_063_171_048,
            1_063_372_269,
            1_060_793_325,
            1_065_322_738,
            1_057_049_736,
            1_041_517_984,
            1_046_860_012,
            1_048_553_904,
            1_059_616_363,
            1_062_613_131,
        )

        val KOTLIN_2_3_20_BOUNDED_INTS = intArrayOf(
            0,
            1,
            0,
            2,
            8,
            8,
            57,
            211,
            57_739,
            1_796_752_825,
        )

        val KOTLIN_2_3_20_SHUFFLE = listOf(
            44, 30, 17, 26, 33, 10, 34, 43, 38, 35, 7, 47,
            11, 39, 28, 1, 15, 40, 21, 24, 45, 19, 4, 41,
            46, 6, 31, 42, 37, 20, 25, 2, 5, 8, 16, 32,
            36, 3, 9, 0, 23, 14, 13, 22, 29, 18, 12, 27,
        )
    }
}
