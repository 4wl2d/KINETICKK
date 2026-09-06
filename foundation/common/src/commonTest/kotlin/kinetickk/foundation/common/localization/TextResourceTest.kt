// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.common.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class TextResourceTest {
    @Test fun switchesLanguagesAndKeepsInsertedTextLiteral() {
        val resource = object : TextResource {
            override val english = "Found {0}: {1}"
            override val russian = "{1}: найдено {0}"
        }
        assertEquals("Found 3: {0}", AppLanguage.English.text(resource, 3, "{0}"))
        assertEquals("{0}: найдено 3", AppLanguage.Russian.text(resource, 3, "{0}"))
    }
}
