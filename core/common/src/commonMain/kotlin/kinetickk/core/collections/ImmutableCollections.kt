// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.collections

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
        /** Copies [elements] into storage owned exclusively by the returned list. */
        fun <Element> copyOf(elements: Iterable<Element>): ImmutableList<Element> =
            ImmutableList(elements.toList())
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

    companion object {
        /** Copies distinct values from [elements], retaining their first-occurrence order. */
        fun <Element> copyOf(elements: Iterable<Element>): ImmutableSet<Element> {
            val distinctElements = mutableListOf<Element>()
            elements.forEach { element ->
                if (element !in distinctElements) {
                    distinctElements += element
                }
            }
            return ImmutableSet(ImmutableList.copyOf(distinctElements))
        }
    }
}

fun <Element> immutableListOf(vararg elements: Element): ImmutableList<Element> =
    ImmutableList.copyOf(elements.asList())

fun <Element> Iterable<Element>.toImmutableList(): ImmutableList<Element> =
    ImmutableList.copyOf(this)

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
