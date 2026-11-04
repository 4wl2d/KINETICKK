// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** `docs/design/redesign/tokens.json` is the source of truth for [KkStrokeWidth] and [KkTypeScale]. */
class KkTokenTableTest {
    private val json: JsonObject by lazy {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "docs/design/redesign/tokens.json") }
            .first { it.isFile }
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    @Test
    fun strokeWidthsMatchTokensJson() {
        // "hairlines 1-1.5px line/line-2; icons 2px"
        val stroke = json.getValue("shape").jsonObject.getValue("stroke").jsonPrimitive.content
        val hairlines = assertNotNull(Regex("""hairlines ([0-9.]+)-([0-9.]+)px""").find(stroke), stroke)
        assertEquals(hairlines.groupValues[1].toFloat(), KkStrokeWidth.Line, "line")
        assertEquals(hairlines.groupValues[2].toFloat(), KkStrokeWidth.Line2, "line-2")
        val icons = assertNotNull(Regex("""icons ([0-9.]+)px""").find(stroke), stroke)
        assertEquals(icons.groupValues[1].toFloat(), KkStrokeWidth.Icon, "icons")
        assertTrue(KkStrokeWidth.Heavy > KkStrokeWidth.Icon, "the heavy outline is the widest stroke")
    }

    @Test
    fun typeScaleMatchesTokensJsonRoles() {
        val typography = json.getValue("typography").jsonObject
        val families = typography.getValue("families").jsonObject
        val roles = typography.getValue("roles").jsonObject
        val table = mapOf(
            "t-wide" to KkTypeScale.Wide,
            "t-cond" to KkTypeScale.Cond,
            "t-label" to KkTypeScale.Label,
            "t-body" to KkTypeScale.Body,
            "t-mono" to KkTypeScale.Mono,
        )
        assertEquals(roles.filterValues { it is JsonObject }.keys, table.keys, "one token per tokens.json role")
        table.forEach { (key, role) ->
            assertEquals(roleOf(roles.getValue(key).jsonObject), role, key)
            assertTrue(role.family in families, "$key family ${role.family}")
        }
    }

    private fun roleOf(source: JsonObject) = KkTypeRole(
        family = source.getValue("family").jsonPrimitive.content,
        weight = source.getValue("weight").jsonPrimitive.int,
        sizeSp = source["size"]?.jsonPrimitive?.float,
        trackingEm = source["tracking"]?.jsonPrimitive?.let(::em) ?: 0f,
        lineHeight = source.getValue("lineHeight").jsonPrimitive.float,
        uppercase = source["case"]?.jsonPrimitive?.content == "upper",
        italic = source["italic"]?.jsonPrimitive?.boolean ?: false,
    )

    /** Tracking is a bare number or an em string such as `"0.09em"`. */
    private fun em(value: JsonPrimitive): Float = if (value.isString) value.content.removeSuffix("em").toFloat() else value.float
}
