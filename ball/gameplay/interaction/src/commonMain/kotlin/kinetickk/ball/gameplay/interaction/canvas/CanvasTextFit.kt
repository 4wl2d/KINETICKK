// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.text.TextLayoutResult

/** Whether every character shows, without an ellipsis and without a line break inside a word. */
internal fun TextLayoutResult.fitsWithoutCuts(): Boolean {
    if (didOverflowWidth || didOverflowHeight || isLineEllipsized(lineCount - 1)) return false
    val shown = layoutInput.text.text
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end <= 0 || end >= shown.length) continue
        if (!shown[end - 1].isWhitespace() && !shown[end].isWhitespace() && shown[end - 1] != '-') return false
    }
    return true
}
