// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real floor pixels identify the dungeon with an elemental wash and glyph, including without colour. */
class DungeonIdentityRenderingTest {
    private val base = hudTestModel(440f, 440f)
    private val templates = base.content.structures.templates.filter { !it.secret }

    @Test
    fun eachDungeonHasADarkDistinctElementalFloorAndReadableGlyphUnderEveryPalette() {
        for (roles in listOf(KkRolePalette.Default, KkRolePalette.Protan, KkRolePalette.Deutan, KkRolePalette.Tritan, KkRolePalette.Mono)) {
            val fingerprints = mutableSetOf<Int>()
            for (template in templates) {
                val room = template.rooms[template.entranceRoom]
                val x = (room.minX + room.maxX) * 0.5f
                val y = (room.minY + room.maxY) * 0.5f
                val fixture = StructureFixture(template, 0f, 0f)
                val model = base.with("structures" to structureProjection(fixture),
                    "cameraX" to x, "cameraY" to y, "previousCameraX" to x, "previousCameraY" to y)
                val bitmap = ImageBitmap(440, 440)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(440f, 440f)) {
                    drawRect(Kk.Ink)
                    drawStructureFloors(model, 0f, 0f, roles)
                }
                val pixels = bitmap.toPixelMap()
                val background = pixels[20, 20]
                val backgroundBrightness = background.red + background.green + background.blue
                assertTrue(backgroundBrightness < 0.5f, "${template.name}: floor leaves combat readable")
                var glyphPixels = 0
                var fingerprint = 1
                for (py in 160 until 280) for (px in 160 until 280) {
                    val pixel = pixels[px, py]
                    if (pixel.red + pixel.green + pixel.blue > backgroundBrightness + 0.04f) glyphPixels++
                    fingerprint = fingerprint * 31 + pixel.toArgb()
                }
                assertTrue(glyphPixels > 100, "${template.name}: the element has a visible shape ($glyphPixels pixels)")
                fingerprints += fingerprint
            }
            assertEquals(templates.size, fingerprints.size, "dungeon identities remain distinct under $roles")
        }
        writeGalleryIfRequested()
    }

    /** The same world floors and wall renderer, zoomed out only for an optional inspection sheet. */
    private fun writeGalleryIfRequested() {
        val folder = System.getenv("KK_SHOTS_OUT")?.let(::File) ?: return
        val side = 440
        val header = 46
        val output = BufferedImage(side * 3, (side + header) * 2, BufferedImage.TYPE_INT_ARGB)
        val graphics = output.createGraphics()
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
        for ((index, template) in templates.withIndex()) {
            val fixture = StructureFixture(template, 0f, 0f)
            val model = base.with("structures" to structureProjection(fixture),
                "cameraX" to 0f, "cameraY" to 0f, "previousCameraX" to 0f, "previousCameraY" to 0f)
            val bitmap = ImageBitmap(side, side)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
                drawRect(Kk.Ink)
                withWorldUnits(0.16f) {
                    drawStructureFloors(model, 0f, 0f, KkRolePalette.Default)
                    drawStructureWalls(model, 0f, 0f, KkRolePalette.Default)
                }
            }
            val left = index % 3 * side
            val top = index / 3 * (side + header)
            graphics.color = java.awt.Color(Kk.Ink.toArgb(), true)
            graphics.fillRect(left, top, side, header)
            graphics.color = java.awt.Color(Kk.Bone.toArgb(), true)
            graphics.drawString("${template.name} / ${template.element}", left + 14, top + 29)
            val pixels = bitmap.toPixelMap()
            for (y in 0 until side) for (x in 0 until side) output.setRGB(left + x, top + header + y, pixels[x, y].toArgb())
        }
        graphics.dispose()
        folder.mkdirs()
        ImageIO.write(output, "png", File(folder, "dungeon-identities.png"))
    }
}
