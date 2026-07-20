// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.simulation

internal fun MutableGameState.d(value: Float): Float = value * uiScale
internal fun MutableGameState.square(value: Float): Float = value * value
internal fun MutableGameState.powFast(base: Float, exponent: Int): Float {
    var result = 1f
    repeat(exponent) { result *= base }
    return result
}
