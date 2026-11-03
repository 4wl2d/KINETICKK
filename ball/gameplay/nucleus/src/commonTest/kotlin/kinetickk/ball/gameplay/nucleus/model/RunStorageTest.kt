// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.foundation.collections.toImmutableSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunStorageTest {
    @Test
    fun intArrayCopiesAreIndependentInEveryDirection() {
        val source = RunIntArray(intArrayOf(1, 2, 3))
        val first = source.copy()
        val second = source.copy()
        val third = first.copy()

        first[0] = 10
        source[1] = 20
        third[2] = 30

        assertEquals(listOf(1, 20, 3), source.toList())
        assertEquals(listOf(10, 2, 3), first.toList())
        assertEquals(listOf(1, 2, 3), second.toList())
        assertEquals(listOf(1, 2, 30), third.toList())
    }

    @Test
    fun intFillWritesEveryValueOfOnlyItsOwnArray() {
        val source = RunIntArray(intArrayOf(4, 4, 4))
        val copy = source.copy()

        copy[1] = 4
        copy.fill(4)
        assertEquals(listOf(4, 4, 4), copy.toList())

        copy.fill(5)
        assertEquals(listOf(4, 4, 4), source.toList())
        assertEquals(listOf(5, 5, 5), copy.toList())
        assertEquals(5, copy.getOrElse(2) { -1 })
        assertEquals(-1, copy.getOrElse(3) { -1 })
    }

    @Test
    fun floatArrayKeepsRawBitsForSignedZeroAndNanPayloads() {
        val nanPayloadA = Float.fromBits(0x7fc00001)
        val nanPayloadB = Float.fromBits(0x7fc00002)
        val source = RunFloatArray(floatArrayOf(0f, nanPayloadA))
        val copy = source.copy()

        copy[0] = -0f
        copy[1] = nanPayloadB
        assertEquals(0f.toRawBits(), source[0].toRawBits())
        assertEquals((-0f).toRawBits(), copy[0].toRawBits())
        assertEquals(nanPayloadA.toRawBits(), source[1].toRawBits())
        assertEquals(nanPayloadB.toRawBits(), copy[1].toRawBits())

        val fillSource = RunFloatArray(floatArrayOf(-0f, -0f))
        val fillCopy = fillSource.copy()
        fillCopy.fill(0f)
        assertEquals(listOf(0f.toRawBits(), 0f.toRawBits()), fillCopy.toList().map(Float::toRawBits))
        assertEquals(listOf((-0f).toRawBits(), (-0f).toRawBits()), fillSource.toList().map(Float::toRawBits))
    }

    @Test
    fun setMutatorsReportMembershipChangesAndCopiesAreIndependent() {
        val source = RunMutableSet(linkedSetOf(1, 2, 3))
        val copy = source.copy()

        assertFalse(copy.add(2))
        assertFalse(copy.addAll(listOf(1, 2)))
        assertFalse(copy.remove(99))
        assertFalse(copy.removeAll(listOf(98, 99)))
        assertFalse(copy.retainAll(listOf(1, 2, 3, 4)))
        assertEquals(setOf(1, 2, 3), copy)

        val iterator = source.iterator()
        assertEquals(1, iterator.next())
        iterator.remove()
        assertEquals(setOf(2, 3), source)
        assertEquals(setOf(1, 2, 3), copy)

        assertTrue(copy.add(4))
        assertTrue(copy.retainAll(listOf(1, 3, 4)))
        assertEquals(listOf(1, 3, 4), copy.toList())
        assertEquals(listOf(2, 3), source.toList())

        val empty = RunMutableSet(mutableSetOf<Int>())
        val emptyCopy = empty.copy()
        emptyCopy.add(7)
        assertTrue(empty.isEmpty())
        assertEquals(setOf(7), emptyCopy)
    }

    @Test
    fun setCopyKeepsTheSourceIterationOrder() {
        val source = RunMutableSet(linkedSetOf(3, 1, 2))
        source.remove(3)
        source.add(3)

        assertEquals(listOf(1, 2, 3), source.copy().toList())
    }

    @Test
    fun immutableSetReuseRequiresTheSameStableIterationOrder() {
        val values = RunMutableSet(linkedSetOf(1, 2, 3))
        val originalProjection = values.toImmutableSet()

        assertTrue(values.reuseIfContentEqual(originalProjection) === originalProjection)
        assertTrue(values.remove(1))
        assertTrue(values.add(1))

        assertEquals(listOf(2, 3, 1), values.toList())
        assertEquals(null, values.reuseIfContentEqual(originalProjection))
        assertEquals(listOf(2, 3, 1), values.toImmutableSet().toList())
    }

    @Test
    fun mutableIteratorRejectsRemoveBeforeNextAndRepeatedRemove() {
        val values = RunMutableSet(linkedSetOf(1, 2))
        val iterator = values.iterator()
        assertFailsWith<IllegalStateException> { iterator.remove() }
        assertEquals(1, iterator.next())
        iterator.remove()
        assertFailsWith<IllegalStateException> { iterator.remove() }
        assertEquals(listOf(2), values.toList())
    }

    @Test
    fun pagedFloatArrayCopiesAreIndependentInEveryDirection() {
        val source = RunPagedFloatArray(size = 10, pageSize = 4)
        assertEquals(3, source.pageCount)
        for (index in 0 until 10) source[index] = index.toFloat()
        val first = source.copy()
        val second = source.copy()
        val nested = first.copy()

        first[5] = 50f
        assertEquals(5f, source[5])
        assertEquals(5f, second[5])
        assertEquals(5f, nested[5])
        first[6] = 60f

        source[9] = 90f
        assertEquals(9f, first[9])
        assertEquals(9f, second[9])
        assertEquals(9f, nested[9])
        nested[0] = -1f
        assertEquals(0f, first[0])
        assertEquals(listOf(-1f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f), nested.toList())
        assertEquals(listOf(0f, 1f, 2f, 3f, 4f, 50f, 60f, 7f, 8f, 9f), first.toList())
        assertEquals(listOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 90f), source.toList())
        assertEquals((0 until 10).map { it.toFloat() }, second.toList())
    }

    @Test
    fun pagedFloatArrayKeepsRawBitsAndBounds() {
        val source = RunPagedFloatArray(size = 3, pageSize = 2)
        val copy = source.copy()
        copy[0] = -0f
        assertEquals((-0f).toRawBits(), copy[0].toRawBits())
        assertEquals(0f.toRawBits(), source[0].toRawBits())
        assertEquals(3, copy.toList().size)
        assertFailsWith<IndexOutOfBoundsException> { source[3] }
        assertFailsWith<IndexOutOfBoundsException> { source[-1] = 1f }
    }
}
