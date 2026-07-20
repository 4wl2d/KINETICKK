// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.collections

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.Test

class ImmutableCollectionsTest {
    @Test
    fun factoriesCopySourceStorage() {
        val listSource = mutableListOf(3, 1, 3)
        val setSource = mutableListOf(3, 1, 3, 2)

        val immutableList = listSource.toImmutableList()
        val immutableSet = setSource.toImmutableSet()
        listSource[0] = 99
        listSource += 4
        setSource.clear()

        assertEquals(listOf(3, 1, 3), immutableList)
        assertEquals(listOf(3, 1, 2), immutableSet.toList())
    }

    @Test
    fun containersAndIteratorsDoNotProvideMutableCasts() {
        val immutableList: List<Int> = immutableListOf(1, 2, 3)
        val immutableSet: Set<Int> = immutableSetOf(1, 2, 3)
        val listIterator: Iterator<Int> = immutableList.iterator()
        val setIterator: Iterator<Int> = immutableSet.iterator()
        val bidirectionalIterator: ListIterator<Int> = immutableList.listIterator()

        assertFalse((immutableList as Any) is MutableList<*>)
        assertFalse((immutableSet as Any) is MutableSet<*>)
        assertFalse((listIterator as Any) is MutableIterator<*>)
        assertFalse((setIterator as Any) is MutableIterator<*>)
        assertFalse((bidirectionalIterator as Any) is MutableListIterator<*>)

        assertFailsWith<ClassCastException> { immutableList as MutableList<Int> }
        assertFailsWith<ClassCastException> { immutableSet as MutableSet<Int> }
        assertFailsWith<ClassCastException> { listIterator as MutableIterator<Int> }
        assertFailsWith<ClassCastException> { setIterator as MutableIterator<Int> }
        assertFailsWith<ClassCastException> {
            bidirectionalIterator as MutableListIterator<Int>
        }
    }

    @Test
    fun listOrderAndStructuralEqualityAreStable() {
        val first = immutableListOf(3, 1, 3, 2)
        val second = listOf(3, 1, 3, 2).toImmutableList()

        assertEquals(listOf(3, 1, 3, 2), first.toList())
        assertEquals(first, second)
        assertEquals(second, first)
        assertEquals(listOf(3, 1, 3, 2), first)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.hashCode(), listOf(3, 1, 3, 2).hashCode())
        assertEquals(immutableListOf(1, 3), first.subList(1, 3))
    }

    @Test
    fun setIterationOrderAndSetEqualityAreStable() {
        val first = immutableSetOf(3, 1, 3, 2, 1)
        val second = listOf(2, 3, 1).toImmutableSet()
        val ordinary = setOf(1, 2, 3)

        assertEquals(listOf(3, 1, 2), first.toList())
        assertEquals(listOf(2, 3, 1), second.toList())
        assertEquals(first, second)
        assertEquals(second, ordinary)
        assertEquals(ordinary, first)
        assertEquals(ordinary.hashCode(), first.hashCode())
    }
}
