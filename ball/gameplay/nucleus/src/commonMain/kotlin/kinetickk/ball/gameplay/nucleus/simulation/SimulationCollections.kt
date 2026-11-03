// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

/** Stable one-pass compaction without iterator or stdlib predicate-wrapper allocation. */
internal inline fun <Element> MutableList<Element>.removeMatchingStable(
    remove: (Element) -> Boolean,
) {
    var retainedCount = 0
    for (index in indices) {
        val value = this[index]
        if (!remove(value)) {
            if (retainedCount != index) this[retainedCount] = value
            retainedCount++
        }
    }
    var index = lastIndex
    while (index >= retainedCount) {
        removeAt(index)
        index--
    }
}
