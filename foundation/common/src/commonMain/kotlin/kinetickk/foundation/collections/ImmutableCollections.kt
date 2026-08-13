// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.collections

/**
 * A structurally read-only list with privately owned copied storage.
 *
 * The type implements [List] only. Its storage and storage iterators are never exposed, so a
 * caller cannot recover mutation authority through a mutable collection or iterator cast.
 */
class ImmutableList<out Element> private constructor(
    private val elements: List<Element>,
) : AbstractList<Element>() {
    override val size: Int
        get() = elements.size

    override fun get(index: Int): Element = elements[index]

    override fun iterator(): Iterator<Element> = ImmutableListIterator(this, startIndex = 0)

    override fun listIterator(): ListIterator<Element> =
        ImmutableListIterator(this, startIndex = 0)

    override fun listIterator(index: Int): ListIterator<Element> =
        ImmutableListIterator(this, startIndex = index)

    override fun subList(fromIndex: Int, toIndex: Int): ImmutableList<Element> {
        checkSubListRange(fromIndex, toIndex, size)
        return copyOf(elements.subList(fromIndex, toIndex))
    }

    companion object {
        private val EMPTY: ImmutableList<Nothing> = ImmutableList(emptyList())

        @Suppress("UNCHECKED_CAST")
        @PublishedApi
        internal fun <Element> empty(): ImmutableList<Element> =
            EMPTY as ImmutableList<Element>

        /** Copies [elements] into storage owned exclusively by the returned list. */
        fun <Element> copyOf(elements: Iterable<Element>): ImmutableList<Element> {
            @Suppress("UNCHECKED_CAST")
            if (elements is ImmutableList<*>) return elements as ImmutableList<Element>
            if (elements is Collection<*> && elements.isEmpty()) return empty()
            return ImmutableList(elements.toList())
        }

        /** Accepts storage that was allocated locally and was never exposed to a caller. */
        @PublishedApi
        internal fun <Element> takeOwnership(elements: List<Element>): ImmutableList<Element> =
            ImmutableList(elements)
    }
}

/**
 * A structurally read-only set with stable first-occurrence iteration order.
 *
 * Equality and hash codes retain ordinary [Set] semantics; iteration retains the order in which
 * distinct elements were first observed while copying the source.
 */
class ImmutableSet<out Element> private constructor(
    private val elements: ImmutableList<Element>,
) : AbstractSet<Element>() {
    override val size: Int
        get() = elements.size

    override fun contains(element: @UnsafeVariance Element): Boolean = elements.contains(element)

    override fun iterator(): Iterator<Element> = ImmutableListIterator(elements, startIndex = 0)

    /** Compares set contents through indexed owned storage without creating an iterator. */
    fun hasSameElementsAs(other: Set<Any?>): Boolean {
        if (size != other.size) return false
        var index = 0
        while (index < elements.size) {
            if (elements[index] !in other) return false
            index++
        }
        return true
    }

    companion object {
        /** Copies distinct values from [elements], retaining their first-occurrence order. */
        fun <Element> copyOf(elements: Iterable<Element>): ImmutableSet<Element> {
            @Suppress("UNCHECKED_CAST")
            if (elements is ImmutableSet<*>) return elements as ImmutableSet<Element>
            if (elements is Set<*>) {
                return ImmutableSet(ImmutableList.copyOf(elements as Iterable<Element>))
            }
            val seen = mutableSetOf<Element>()
            val distinctElements = ArrayList<Element>()
            elements.forEach { element ->
                if (seen.add(element)) {
                    distinctElements += element
                }
            }
            return ImmutableSet(ImmutableList.takeOwnership(distinctElements))
        }
    }
}

fun <Element> immutableListOf(): ImmutableList<Element> = ImmutableList.empty()

fun <Element> immutableListOf(element: Element): ImmutableList<Element> =
    ImmutableList.takeOwnership(listOf(element))

fun <Element> immutableListOf(first: Element, second: Element): ImmutableList<Element> =
    ImmutableList.takeOwnership(listOf(first, second))

fun <Element> immutableListOf(
    first: Element,
    second: Element,
    third: Element,
): ImmutableList<Element> = ImmutableList.takeOwnership(listOf(first, second, third))

fun <Element> immutableListOf(vararg elements: Element): ImmutableList<Element> =
    ImmutableList.copyOf(elements.asList())

/** Creates a compact immutable list from up to three nullable values without builder storage. */
fun <Element : Any> immutableListOfNotNull(
    first: Element?,
    second: Element?,
    third: Element?,
): ImmutableList<Element> = when {
    first != null && second != null && third != null -> immutableListOf(first, second, third)
    first != null && second != null -> immutableListOf(first, second)
    first != null && third != null -> immutableListOf(first, third)
    second != null && third != null -> immutableListOf(second, third)
    first != null -> immutableListOf(first)
    second != null -> immutableListOf(second)
    third != null -> immutableListOf(third)
    else -> immutableListOf()
}

/** Initializes exactly-sized privately owned immutable storage without a mutable builder. */
inline fun <Element> immutableListOfSize(
    size: Int,
    initializer: (index: Int) -> Element,
): ImmutableList<Element> {
    require(size >= 0) { "size must be non-negative" }
    if (size == 0) return ImmutableList.empty()
    if (size == 1) return immutableListOf(initializer(0))
    val destination = ArrayList<Element>(size)
    for (index in 0 until size) destination += initializer(index)
    return ImmutableList.takeOwnership(destination)
}

fun <Element> Iterable<Element>.toImmutableList(): ImmutableList<Element> =
    ImmutableList.copyOf(this)

/** Maps directly into privately owned immutable storage without an intermediate list copy. */
inline fun <Element, Result> Iterable<Element>.mapToImmutableList(
    transform: (Element) -> Result,
): ImmutableList<Result> {
    if (this is Collection<*> && isEmpty()) return ImmutableList.empty()
    val destination = if (this is Collection<*>) {
        ArrayList<Result>(size)
    } else {
        ArrayList()
    }
    if (this is List<*>) {
        @Suppress("UNCHECKED_CAST")
        val source = this as List<Element>
        for (index in source.indices) destination += transform(source[index])
    } else {
        for (element in this) destination += transform(element)
    }
    return ImmutableList.takeOwnership(destination)
}

/** Maps a list by index directly into exactly-sized privately owned immutable storage. */
inline fun <Element, Result> List<Element>.mapIndexedToImmutableList(
    transform: (index: Int, Element) -> Result,
): ImmutableList<Result> {
    if (isEmpty()) return ImmutableList.empty()
    val destination = ArrayList<Result>(size)
    for (index in indices) destination += transform(index, this[index])
    return ImmutableList.takeOwnership(destination)
}

/** Copies this iterable once and appends [element] to the newly owned immutable storage. */
fun <Element> Iterable<Element>.toImmutableListAppending(
    element: Element,
): ImmutableList<Element> {
    val sourceSize = (this as? Collection<*>)?.size ?: 0
    val destination = ArrayList<Element>(sourceSize + 1)
    if (this is List<*>) {
        @Suppress("UNCHECKED_CAST")
        val source = this as List<Element>
        for (index in source.indices) destination += source[index]
    } else {
        for (sourceElement in this) destination += sourceElement
    }
    destination += element
    return ImmutableList.takeOwnership(destination)
}

/** Boxes primitive values once into exactly-sized privately owned immutable storage. */
fun IntArray.toImmutableList(): ImmutableList<Int> {
    if (isEmpty()) return ImmutableList.empty()
    val destination = ArrayList<Int>(size)
    for (element in this) destination += element
    return ImmutableList.takeOwnership(destination)
}

fun <Element> immutableSetOf(vararg elements: Element): ImmutableSet<Element> =
    ImmutableSet.copyOf(elements.asList())

fun <Element> Iterable<Element>.toImmutableSet(): ImmutableSet<Element> =
    ImmutableSet.copyOf(this)

private class ImmutableListIterator<out Element>(
    private val elements: List<Element>,
    startIndex: Int,
) : ListIterator<Element> {
    private var index: Int = startIndex

    init {
        if (startIndex !in 0..elements.size) {
            throw IndexOutOfBoundsException(
                "startIndex ($startIndex) must be between 0 and ${elements.size}",
            )
        }
    }

    override fun hasNext(): Boolean = index < elements.size

    override fun next(): Element {
        if (!hasNext()) throw NoSuchElementException()
        return elements[index++]
    }

    override fun nextIndex(): Int = index

    override fun hasPrevious(): Boolean = index > 0

    override fun previous(): Element {
        if (!hasPrevious()) throw NoSuchElementException()
        return elements[--index]
    }

    override fun previousIndex(): Int = index - 1
}

private fun checkSubListRange(fromIndex: Int, toIndex: Int, size: Int) {
    if (fromIndex < 0 || toIndex > size) {
        throw IndexOutOfBoundsException(
            "subList range [$fromIndex, $toIndex) is outside list size $size",
        )
    }
    if (fromIndex > toIndex) {
        throw IllegalArgumentException("fromIndex ($fromIndex) must not exceed toIndex ($toIndex)")
    }
}
