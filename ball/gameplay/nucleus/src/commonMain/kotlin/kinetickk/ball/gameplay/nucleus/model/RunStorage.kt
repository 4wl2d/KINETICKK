// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableListOfSize
import kinetickk.foundation.collections.toImmutableSet

/**
 * Run-owned [IntArray] storage. The production tick writes it in place; [copy] is the eager copy a deep copy of the
 * run state takes.
 */
internal class RunIntArray(
    private val values: IntArray,
) : Iterable<Int> {
    val size: Int
        get() = values.size

    val indices: IntRange
        get() = values.indices

    operator fun get(index: Int): Int = values[index]

    operator fun set(index: Int, value: Int) {
        values[index] = value
    }

    fun fill(value: Int) {
        values.fill(value)
    }

    fun getOrElse(index: Int, defaultValue: (Int) -> Int): Int =
        if (index in indices) values[index] else defaultValue(index)

    fun toList(): List<Int> = values.toList()

    fun toImmutableList(): ImmutableList<Int> = immutableListOfSize(size) { index ->
        values[index]
    }

    /** An independent copy: later writes to either array never reach the other. */
    fun copy(): RunIntArray = RunIntArray(values.copyOf())

    fun reuseIfContentEqual(previous: ImmutableList<Int>?): ImmutableList<Int>? {
        val current = values
        if (previous == null || current.size != previous.size) return null
        var index = 0
        while (index < current.size) {
            if (current[index] != previous[index]) return null
            index++
        }
        return previous
    }

    override fun iterator(): IntIterator = values.iterator()
}

/** Float counterpart of [RunIntArray], retaining raw IEEE-754 bit semantics. */
internal class RunFloatArray(
    private val values: FloatArray,
) : Iterable<Float> {
    val size: Int
        get() = values.size

    val indices: IntRange
        get() = values.indices

    operator fun get(index: Int): Float = values[index]

    operator fun set(index: Int, value: Float) {
        values[index] = value
    }

    fun fill(value: Float) {
        values.fill(value)
    }

    fun toList(): List<Float> = values.toList()

    /** An independent copy: later writes to either array never reach the other. */
    fun copy(): RunFloatArray = RunFloatArray(values.copyOf())

    override fun iterator(): FloatIterator = values.iterator()
}

/**
 * A fixed-size float array kept in pages of [pageSize] values. The production tick writes it in place; [copy] copies
 * every page eagerly.
 */
internal class RunPagedFloatArray private constructor(
    val size: Int,
    private val pageSize: Int,
    private val pages: Array<FloatArray>,
) : Iterable<Float> {
    constructor(size: Int, pageSize: Int) : this(size, pageSize, allocatePages(size, pageSize))

    val pageCount: Int
        get() = pages.size

    operator fun get(index: Int): Float {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("index $index, size $size")
        return pages[index / pageSize][index % pageSize]
    }

    operator fun set(index: Int, value: Float) {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("index $index, size $size")
        pages[index / pageSize][index % pageSize] = value
    }

    fun toList(): List<Float> = List(size) { index -> get(index) }

    /** An independent copy of every page: later writes to either array never reach the other. */
    fun copy(): RunPagedFloatArray =
        RunPagedFloatArray(size, pageSize, Array(pages.size) { page -> pages[page].copyOf() })

    override fun iterator(): FloatIterator = object : FloatIterator() {
        private var index = 0
        override fun hasNext(): Boolean = index < size
        override fun nextFloat(): Float = get(index++)
    }

    private companion object {
        fun allocatePages(size: Int, pageSize: Int): Array<FloatArray> {
            require(size >= 0 && pageSize > 0) { "size $size, page size $pageSize" }
            val count = (size + pageSize - 1) / pageSize
            return Array(count) { FloatArray(pageSize) }
        }
    }
}

/** A run-owned mutable set that keeps its insertion order; [copy] is the eager copy a deep copy of the run state takes. */
internal class RunMutableSet<Element>(
    private val values: MutableSet<Element>,
) : MutableSet<Element> {
    /** An independent copy in the same iteration order: later writes to either set never reach the other. */
    fun copy(): RunMutableSet<Element> = RunMutableSet(values.toMutableSet())

    fun toImmutableSet(): ImmutableSet<Element> = values.toImmutableSet()

    fun reuseIfContentEqual(previous: ImmutableSet<Element>?): ImmutableSet<Element>? {
        if (previous == null || previous.size != values.size) return null
        val current = values.iterator()
        val retained = previous.iterator()
        while (current.hasNext()) {
            if (current.next() != retained.next()) return null
        }
        return previous
    }

    override val size: Int
        get() = values.size

    override fun contains(element: Element): Boolean = element in values

    override fun containsAll(elements: Collection<Element>): Boolean = values.containsAll(elements)

    override fun isEmpty(): Boolean = values.isEmpty()

    override fun add(element: Element): Boolean = values.add(element)

    override fun addAll(elements: Collection<Element>): Boolean = values.addAll(elements)

    override fun clear() {
        values.clear()
    }

    override fun remove(element: Element): Boolean = values.remove(element)

    override fun removeAll(elements: Collection<Element>): Boolean = values.removeAll(elements.toSet())

    override fun retainAll(elements: Collection<Element>): Boolean = values.retainAll(elements.toSet())

    override fun iterator(): MutableIterator<Element> = values.iterator()

    override fun equals(other: Any?): Boolean = values == other

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = values.toString()
}
