// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.projection

import kinetickk.foundation.collections.ImmutableList

internal fun <Source : Any, Projection> Source.reuseIfIdentical(
    previousSource: Source?,
    previousProjection: Projection?,
): Projection? = previousProjection?.takeIf { this === previousSource }

internal fun <Source, Projection> List<Source>.reuseEmptyProjection(
    previous: ImmutableList<Projection>?,
): ImmutableList<Projection>? =
    previous?.takeIf { isEmpty() && it.isEmpty() }

internal inline fun <Source, Projection> List<Source>.reuseProjectionIfContentEqual(
    previous: ImmutableList<Projection>?,
    sameProjection: (Source, Projection) -> Boolean,
): ImmutableList<Projection>? {
    if (previous == null || size != previous.size) return null
    var index = 0
    while (index < size) {
        if (!sameProjection(this[index], previous[index])) return null
        index++
    }
    return previous
}

internal fun Float.sameBitsAs(other: Float): Boolean = toRawBits() == other.toRawBits()

internal fun <Element> List<Element>.reuseIfContentEqual(
    previous: ImmutableList<Element>?,
): ImmutableList<Element>? {
    if (previous == null || size != previous.size) return null
    var index = 0
    while (index < size) {
        if (this[index] != previous[index]) return null
        index++
    }
    return previous
}
