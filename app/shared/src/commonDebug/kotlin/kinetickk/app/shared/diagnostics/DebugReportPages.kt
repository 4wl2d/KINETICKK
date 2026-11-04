// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared.diagnostics

/** A text field only sees one small page. Large TSV exports otherwise cause seconds of text layout. */
internal class DebugReportPages(private val text: String) {
    private val boundaries: IntArray = buildList {
        add(0)
        var start = 0
        while (start < text.length) {
            var end = minOf(start + PAGE_CHAR_LIMIT, text.length)
            if (end < text.length) {
                val newline = text.lastIndexOf('\n', end - 1)
                if (newline >= start) end = newline + 1
                else if (text[end - 1] in '\uD800'..'\uDBFF' && text[end] in '\uDC00'..'\uDFFF') end--
            }
            add(end)
            start = end
        }
        if (size == 1) add(0)
    }.toIntArray()

    val count: Int get() = boundaries.size - 1

    fun page(index: Int): String {
        require(index in 0 until count)
        return text.substring(boundaries[index], boundaries[index + 1])
    }

    companion object {
        const val PAGE_CHAR_LIMIT: Int = 8_192
    }
}
