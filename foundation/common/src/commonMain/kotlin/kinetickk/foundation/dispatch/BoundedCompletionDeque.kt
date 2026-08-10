// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.dispatch

/** A same-stack completion buffer with an explicit, non-truncating capacity. */
class BoundedCompletionDeque<T>(val capacity: Int) {
    init {
        require(capacity > 0) { "Completion capacity must be positive" }
    }

    private val values: ArrayDeque<T> = ArrayDeque(capacity)

    val size: Int
        get() = values.size

    val remainingCapacity: Int
        get() = capacity - values.size

    val isEmpty: Boolean
        get() = values.isEmpty()

    fun tryAddLast(value: T): Boolean {
        if (values.size == capacity) return false
        values.addLast(value)
        return true
    }

    fun removeFirstOrNull(): T? = if (values.isEmpty()) null else values.removeFirst()
}
