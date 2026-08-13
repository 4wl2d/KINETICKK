// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.foundation.collections.toImmutableSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CopyOnWriteStorageTest {
    @Test
    fun intArrayMultiForkWritesDetachInBothDirectionsWithoutCrossBranchMutation() {
        val source = CopyOnWriteIntArray(intArrayOf(1, 2, 3))
        val first = source.fork()
        val second = source.fork()
        val third = first.fork()

        assertTrue(first.sharesStorageWith(source))
        assertTrue(second.sharesStorageWith(source))
        assertTrue(third.sharesStorageWith(source))

        first[0] = 10
        source[1] = 20
        third[2] = 30

        assertEquals(listOf(1, 20, 3), source.toList())
        assertEquals(listOf(10, 2, 3), first.toList())
        assertEquals(listOf(1, 2, 3), second.toList())
        assertEquals(listOf(1, 2, 30), third.toList())
        assertFalse(first.sharesStorageWith(source))
        assertFalse(second.sharesStorageWith(source))
        assertFalse(third.sharesStorageWith(source))
    }

    @Test
    fun sameIntValueAndFillRemainSharedUntilContentActuallyChanges() {
        val source = CopyOnWriteIntArray(intArrayOf(4, 4, 4))
        val branch = source.fork()

        branch[1] = 4
        branch.fill(4)
        source[2] = 4
        assertTrue(branch.sharesStorageWith(source))

        branch.fill(5)
        assertFalse(branch.sharesStorageWith(source))
        assertEquals(listOf(4, 4, 4), source.toList())
        assertEquals(listOf(5, 5, 5), branch.toList())
    }

    @Test
    fun floatArrayUsesRawBitsForSignedZeroAndNanPayloadDetachDecisions() {
        val nanPayloadA = Float.fromBits(0x7fc00001)
        val nanPayloadB = Float.fromBits(0x7fc00002)
        val source = CopyOnWriteFloatArray(floatArrayOf(0f, nanPayloadA))
        val signedZeroBranch = source.fork()
        val nanBranch = source.fork()

        signedZeroBranch[0] = 0f
        nanBranch[1] = nanPayloadA
        assertTrue(signedZeroBranch.sharesStorageWith(source))
        assertTrue(nanBranch.sharesStorageWith(source))

        signedZeroBranch[0] = -0f
        nanBranch[1] = nanPayloadB
        assertFalse(signedZeroBranch.sharesStorageWith(source))
        assertFalse(nanBranch.sharesStorageWith(source))
        assertEquals(0f.toRawBits(), source[0].toRawBits())
        assertEquals((-0f).toRawBits(), signedZeroBranch[0].toRawBits())
        assertEquals(nanPayloadA.toRawBits(), source[1].toRawBits())
        assertEquals(nanPayloadB.toRawBits(), nanBranch[1].toRawBits())

        val fillSource = CopyOnWriteFloatArray(floatArrayOf(-0f, -0f))
        val fillBranch = fillSource.fork()
        fillBranch.fill(-0f)
        assertTrue(fillBranch.sharesStorageWith(fillSource))
        fillBranch.fill(0f)
        assertFalse(fillBranch.sharesStorageWith(fillSource))
    }

    @Test
    fun setNoOpMutatorsStaySharedAndIteratorRemoveIsForkSafe() {
        val source = CopyOnWriteMutableSet(linkedSetOf(1, 2, 3))
        val iteratorCreatedBeforeFork = source.iterator()
        assertEquals(1, iteratorCreatedBeforeFork.next())
        val retainedFork = source.fork()

        assertFalse(retainedFork.add(2))
        assertFalse(retainedFork.addAll(listOf(1, 2)))
        assertFalse(retainedFork.remove(99))
        assertFalse(retainedFork.removeAll(listOf(98, 99)))
        assertFalse(retainedFork.retainAll(listOf(1, 2, 3, 4)))
        assertTrue(retainedFork.sharesStorageWith(source))

        iteratorCreatedBeforeFork.remove()
        assertEquals(setOf(2, 3), source)
        assertEquals(setOf(1, 2, 3), retainedFork)
        assertFalse(retainedFork.sharesStorageWith(source))

        assertEquals(2, iteratorCreatedBeforeFork.next())
        iteratorCreatedBeforeFork.remove()
        assertEquals(setOf(3), source)
        assertEquals(setOf(1, 2, 3), retainedFork)

        val sibling = retainedFork.fork()
        val branchIterator = retainedFork.iterator()
        assertEquals(1, branchIterator.next())
        branchIterator.remove()
        assertEquals(setOf(2, 3), retainedFork)
        assertEquals(setOf(1, 2, 3), sibling)
        assertFalse(retainedFork.sharesStorageWith(sibling))

        val empty = CopyOnWriteMutableSet(mutableSetOf<Int>())
        val emptyFork = empty.fork()
        emptyFork.clear()
        assertTrue(emptyFork.sharesStorageWith(empty))
    }

    @Test
    fun immutableSetReuseRequiresTheSameStableIterationOrder() {
        val values = CopyOnWriteMutableSet(linkedSetOf(1, 2, 3))
        val originalProjection = values.toImmutableSet()

        assertTrue(values.reuseIfContentEqual(originalProjection) === originalProjection)
        assertTrue(values.remove(1))
        assertTrue(values.add(1))

        assertEquals(listOf(2, 3, 1), values.toList())
        assertEquals(null, values.reuseIfContentEqual(originalProjection))
        assertEquals(listOf(2, 3, 1), values.toImmutableSet().toList())
    }

    @Test
    fun mutableIteratorRejectsRemoveBeforeNextAndRepeatedRemoveOnEveryOwnershipPath() {
        val unshared = CopyOnWriteMutableSet(linkedSetOf(1, 2))
        val unsharedIterator = unshared.iterator()
        assertFailsWith<IllegalStateException> { unsharedIterator.remove() }
        assertEquals(1, unsharedIterator.next())
        unsharedIterator.remove()
        assertFailsWith<IllegalStateException> { unsharedIterator.remove() }
        assertEquals(listOf(2), unshared.toList())

        val shared = CopyOnWriteMutableSet(linkedSetOf(1, 2))
        val sharedIterator = shared.iterator()
        val sibling = shared.fork()
        assertFailsWith<IllegalStateException> { sharedIterator.remove() }
        assertEquals(1, sharedIterator.next())
        sharedIterator.remove()
        assertFailsWith<IllegalStateException> { sharedIterator.remove() }
        assertEquals(listOf(2), shared.toList())
        assertEquals(listOf(1, 2), sibling.toList())
    }
}
