// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** `docs/design/redesign/icons.json` is the source of truth for the generated [KkIcon] table. */
class KkIconTableTest {
    private val json: JsonObject by lazy {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "docs/design/redesign/icons.json") }
            .first { it.isFile }
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    @Test
    fun kotlinTableMatchesIconsJsonExactly() {
        assertEquals(24, json.getValue("grid").jsonPrimitive.content.toInt())
        assertEquals(2, json.getValue("strokeWidth").jsonPrimitive.content.toInt())
        val icons = json.getValue("icons").jsonObject
        assertEquals(icons.keys.toList(), KkIcon.entries.map { it.key }, "keys and order")
        KkIcon.entries.forEach { icon ->
            val source = icons.getValue(icon.key).jsonObject
            assertEquals(source.getValue("label").jsonPrimitive.content, icon.label, icon.key)
            val layers = source.getValue("layers").jsonArray.map { it.jsonObject }
            val design = layers.map { it.getValue("d").jsonPrimitive.content to it.getValue("style").jsonPrimitive.content }
            val drawn = icon.layers.map { it.d to it.style.jsonName }
            val override = NoArrowheadOverrides[icon.key]
            if (override != null) {
                // The design path stays in icons.json; the code draws a non-pointed replacement.
                val (reason, pointedHead) = override
                assertTrue(design.any { (d, _) -> pointedHead in d }, "${icon.key}: the exemption still matches icons.json")
                assertFalse(drawn.any { (d, _) -> pointedHead in d }, "${icon.key}: $reason")
            } else {
                assertEquals(design, drawn, icon.key)
            }
            assertEquals(icon.key.replace('.', '_').uppercase(), icon.name, "enum name follows the key")
        }
        val styles = json.getValue("styles").jsonObject.keys
        assertEquals(styles, KkIconStyle.entries.map { it.jsonName }.toSet())
    }

    @Test
    fun everyIconParsesInsideTheGridAndDrawsVisiblePixels() {
        KkIcon.entries.forEach { icon ->
            icon.layers.forEach { layer ->
                val bounds = PathParser().parsePathString(layer.d).toPath().getBounds()
                assertTrue(bounds.left >= -0.5f && bounds.top >= -0.5f && bounds.right <= 24.5f && bounds.bottom <= 24.5f,
                    "${icon.key} layer bounds $bounds")
            }
            val pixels = render { drawKkIcon(icon, Offset(24f, 24f), 48f, Color.White) }
            val lit = pixels.count { it != Black }
            assertTrue(lit > 40, "${icon.key} is visible ($lit px)")
            assertTrue(pixels.indices.filter { pixels[it] != Black }.all { index ->
                val x = index % 48
                val y = index / 48
                x in 0 until 48 && y in 0 until 48
            })
        }
        assertFalse(KkIcon.entries.any { "arrow" in it.key || "chevron" in it.key })
    }

    @Test
    fun dashedAndDottedLayersDifferFromSolidStrokes() {
        val dashed = render { drawKkIcon(KkIcon.WEAPONS_GRAVITY_MINES, Offset(24f, 24f), 48f, Color.White) }
        val solid = render { drawKkIcon(KkIcon.FORMS_RING, Offset(24f, 24f), 48f, Color.White) }
        assertNotEquals(dashed.toList(), solid.toList())
        assertEquals(listOf(KkIcon.ASPECT_VECTOR, KkIcon.ASPECT_GRAVITIC, KkIcon.ASPECT_ION, KkIcon.ASPECT_RIFT,
            KkIcon.ASPECT_PRISM, KkIcon.ASPECT_ENTROPY, KkIcon.ASPECT_SOVEREIGN), KkIcon.Aspects)
        assertEquals(6, KkIcon.Forms.size)
        assertEquals(KkIcon.WEAPONS_FLUX_WAKE, KkIcon.byKey("weapons.flux_wake"))
    }

    private fun render(draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): IntArray {
        val bitmap = ImageBitmap(48, 48)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(48f, 48f)) {
            drawRect(Color.Black)
            draw()
        }
        val map = bitmap.toPixelMap()
        return IntArray(48 * 48) { map[it % 48, it / 48].toArgb() }
    }

    private companion object {
        val Black = Color.Black.toArgb()

        /**
         * Keys whose icons.json path draws an arrowhead: SPEC hard rule 3 wins over the icon set
         * (owner decision), so KkIcons draws a documented replacement. Value: the reason and the
         * design sub-path that is replaced.
         */
        val NoArrowheadOverrides = mapOf(
            "weapons.null_lance" to ("hard rule 3: the pointed lance head reads as an arrowhead" to "M14 10l1-5 4 4-5 1Z"),
        )
    }
}
