// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

/**
 * Stroke widths in px (`docs/design/redesign/tokens.json` `shape.stroke`): the hairlines [Line] and [Line2] and the
 * icon stroke [Icon]. [Heavy] is the heaviest outline: SPEC section 7.2 draws enemy outlines 1.5/2/3 px by frame.
 */
object KkStrokeWidth {
    const val Line: Float = 1f
    const val Line2: Float = 1.5f
    const val Icon: Float = 2f
    const val Heavy: Float = 3f
}
