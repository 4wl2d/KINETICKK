// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableListOfSize
import kinetickk.foundation.collections.toImmutableSet

/**
 * A reducer-local view over an [IntArray]. Forks share storage until a value actually changes.
 *
 * Gameplay reduction is synchronous, so the shared marker deliberately has no atomics. It is
 * monotonic for each backing store: an abandoned fork can only make a later write copy
 * unnecessarily; it can never expose a mutation or overflow an ownership counter.
 */
internal class CopyOnWriteIntArray private constructor(
    private var storage: IntStorage,
) : Iterable<Int> {
    constructor(values: IntArray) : this(IntStorage(values))

    val size: Int
        get() = storage.values.size

    val indices: IntRange
        get() = storage.values.indices

    operator fun get(index: Int): Int = storage.values[index]

    operator fun set(index: Int, value: Int) {
        if (storage.values[index] == value) return
        writableValues()[index] = value
    }

    fun fill(value: Int) {
        val current = storage.values
        var index = 0
        while (index < current.size && current[index] == value) index++
        if (index == current.size) return
        writableValues().fill(value)
    }

    fun getOrElse(index: Int, defaultValue: (Int) -> Int): Int =
        if (index in indices) storage.values[index] else defaultValue(index)

    fun toList(): List<Int> = storage.values.toList()

    fun toImmutableList(): ImmutableList<Int> = immutableListOfSize(size) { index ->
        storage.values[index]
    }

    fun fork(): CopyOnWriteIntArray {
        storage.shared = true
        return CopyOnWriteIntArray(storage)
    }

    fun sharesStorageWith(other: CopyOnWriteIntArray): Boolean = storage === other.storage

    fun reuseIfContentEqual(previous: ImmutableList<Int>?): ImmutableList<Int>? {
        val current = storage.values
        if (previous == null || current.size != previous.size) return null
        var index = 0
        while (index < current.size) {
            if (current[index] != previous[index]) return null
            index++
        }
        return previous
    }

    override fun iterator(): IntIterator = storage.values.iterator()

    private fun writableValues(): IntArray {
        val current = storage
        if (!current.shared) return current.values
        return current.values.copyOf().also { copied ->
            storage = IntStorage(copied)
        }
    }

    private class IntStorage(
        val values: IntArray,
        var shared: Boolean = false,
    )
}

/** Float counterpart of [CopyOnWriteIntArray], retaining raw IEEE-754 bit semantics. */
internal class CopyOnWriteFloatArray private constructor(
    private var storage: FloatStorage,
) : Iterable<Float> {
    constructor(values: FloatArray) : this(FloatStorage(values))

    val size: Int
        get() = storage.values.size

    val indices: IntRange
        get() = storage.values.indices

    operator fun get(index: Int): Float = storage.values[index]

    operator fun set(index: Int, value: Float) {
        if (storage.values[index].toRawBits() == value.toRawBits()) return
        writableValues()[index] = value
    }

    fun fill(value: Float) {
        val valueBits = value.toRawBits()
        val current = storage.values
        var index = 0
        while (index < current.size && current[index].toRawBits() == valueBits) index++
        if (index == current.size) return
        writableValues().fill(value)
    }

    fun toList(): List<Float> = storage.values.toList()

    fun fork(): CopyOnWriteFloatArray {
        storage.shared = true
        return CopyOnWriteFloatArray(storage)
    }

    fun sharesStorageWith(other: CopyOnWriteFloatArray): Boolean = storage === other.storage

    override fun iterator(): FloatIterator = storage.values.iterator()

    private fun writableValues(): FloatArray {
        val current = storage
        if (!current.shared) return current.values
        return current.values.copyOf().also { copied ->
            storage = FloatStorage(copied)
        }
    }

    private class FloatStorage(
        val values: FloatArray,
        var shared: Boolean = false,
    )
}

/** A mutable-set facade whose reduction forks copy only when membership changes. */
internal class CopyOnWriteMutableSet<Element> private constructor(
    private var storage: SetStorage<Element>,
) : MutableSet<Element> {
    constructor(values: MutableSet<Element>) : this(SetStorage(values))

    fun fork(): CopyOnWriteMutableSet<Element> {
        storage.shared = true
        return CopyOnWriteMutableSet(storage)
    }

    fun sharesStorageWith(other: CopyOnWriteMutableSet<Element>): Boolean = storage === other.storage

    fun toImmutableSet(): ImmutableSet<Element> = storage.values.toImmutableSet()

    fun reuseIfContentEqual(previous: ImmutableSet<Element>?): ImmutableSet<Element>? {
        if (previous == null || previous.size != storage.values.size) return null
        val current = storage.values.iterator()
        val retained = previous.iterator()
        while (current.hasNext()) {
            if (current.next() != retained.next()) return null
        }
        return previous
    }

    override val size: Int
        get() = storage.values.size

    override fun contains(element: Element): Boolean = element in storage.values

    override fun containsAll(elements: Collection<Element>): Boolean = storage.values.containsAll(elements)

    override fun isEmpty(): Boolean = storage.values.isEmpty()

    override fun add(element: Element): Boolean {
        if (element in storage.values) return false
        return writableValues().add(element)
    }

    override fun addAll(elements: Collection<Element>): Boolean {
        if (elements.none { it !in storage.values }) return false
        return writableValues().addAll(elements)
    }

    override fun clear() {
        if (storage.values.isEmpty()) return
        writableValues().clear()
    }

    override fun remove(element: Element): Boolean {
        if (element !in storage.values) return false
        return writableValues().remove(element)
    }

    override fun removeAll(elements: Collection<Element>): Boolean {
        if (elements.none { it in storage.values }) return false
        return writableValues().removeAll(elements.toSet())
    }

    override fun retainAll(elements: Collection<Element>): Boolean {
        if (storage.values.all { it in elements }) return false
        return writableValues().retainAll(elements.toSet())
    }

    override fun iterator(): MutableIterator<Element> = ForkSafeIterator(storage)

    override fun equals(other: Any?): Boolean = storage.values == other

    override fun hashCode(): Int = storage.values.hashCode()

    override fun toString(): String = storage.values.toString()

    private fun writableValues(): MutableSet<Element> {
        val current = storage
        if (!current.shared) return current.values
        return current.values.toMutableSet().also { copied ->
            storage = SetStorage(copied)
        }
    }

    private inner class ForkSafeIterator(
        private val iteratedStorage: SetStorage<Element>,
    ) : MutableIterator<Element> {
        private val delegate = iteratedStorage.values.iterator()
        private var lastValue: Any? = NoValue

        override fun hasNext(): Boolean = delegate.hasNext()

        override fun next(): Element = delegate.next().also { lastValue = it }

        override fun remove() {
            check(lastValue !== NoValue) { "next() must be called before remove()" }
            if (storage === iteratedStorage && !iteratedStorage.shared) {
                delegate.remove()
            } else {
                @Suppress("UNCHECKED_CAST")
                this@CopyOnWriteMutableSet.remove(lastValue as Element)
            }
            lastValue = NoValue
        }
    }

    private class SetStorage<Element>(
        val values: MutableSet<Element>,
        var shared: Boolean = false,
    )

    private object NoValue
}
