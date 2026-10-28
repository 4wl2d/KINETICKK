// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/*
 * Portable neighbours of a finite float for boundary tests: kotlin.math's Float.nextUp and Float.nextDown exist on
 * the JVM and Native but not on wasmJs, so these step the IEEE bit pattern by one with the same results.
 */

/** The next representable float above this finite value (zero steps to [Float.MIN_VALUE]). */
internal fun Float.nextAbove(): Float = when {
    this == 0f -> Float.MIN_VALUE
    this > 0f -> Float.fromBits(toRawBits() + 1)
    else -> Float.fromBits(toRawBits() - 1)
}

/** The next representable float below this finite value (zero steps to -[Float.MIN_VALUE]). */
internal fun Float.nextBelow(): Float = -(-this).nextAbove()
