// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kinetickk.ball.gameplay.interaction.canvas.hud.*
import kinetickk.ball.gameplay.interaction.canvas.world.*

/**
 * Core Stasis on screen: the frost over the Core, the screen-edge frost and the warning stamp draw without allocating
 * on steady frames, keep to the role palette (cryo while freezing, threat only while fracturing, hatched in Mono) and
 * fit a portrait phone at the largest text size in Russian.
 */
class CoreStasisRenderingTest {
    @Test
    fun coreFrostDrawsWithoutAllocatingForEveryCoreShape() {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
            AppLanguage.English)
        for (shape in CoreShape.entries) for ((stasis, fracturing) in listOf(0.6f to false, 1f to true)) {
            val base = hudTestModel(WORLD_WIDTH.toFloat(), WORLD_HEIGHT.toFloat()).with("coreShape" to shape)
            fun frames(frost: Boolean) = List(FRAMES) { frame ->
                base.with("elapsed" to 20f + frame * 0.013f, "coreStasis" to if (frost) stasis else 0f,
                    "coreStasisFracturing" to (frost && fracturing))
            }
            val plain = frames(frost = false)
            val frosted = frames(frost = true)
            val bytes = { models: List<GameplayRenderModel> ->
                steadyFrameBytes(WORLD_WIDTH, WORLD_HEIGHT, cycles = 3) { frame ->
                    drawWorld(models[frame % FRAMES], VisualFxProjection.EMPTY, 0f, 0f, measurer)
                }
            }
            val extra = bytes(frosted) - bytes(plain)
            assertTrue(extra <= 16, "$shape frost at $stasis (fracturing $fracturing) allocates $extra bytes per frame")
        }
    }

    @Test
    fun theStampAndTheEdgeFrostAllocateNothingOnSteadyFrames() {
        for ((width, height) in listOf(1_440 to 810, 844 to 390, 390 to 844)) for (language in listOf(AppLanguage.English, AppLanguage.Russian)) {
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f, language)
            for ((stasis, fracturing) in listOf(0.3f to false, 1f to false, 1f to true)) {
                val model = hudTestModel(width.toFloat(), height.toFloat()).with("coreStasis" to stasis, "coreStasisFracturing" to fracturing)
                val memory = HudPresentationMemory()
                val perFrame = steadyFrameBytes(width, height) { frame ->
                    drawHud(model, measurer, 10f + frame * 0.0021f, 0f, 0f, memory)
                }
                val where = "$language $stasis fracturing=$fracturing at $width x $height"
                assertNotNull(HudLayoutProbe.rect(HudBlock.CORE_STASIS), "$where: no stamp")
                assertTrue(perFrame <= 64, "Core Stasis HUD ($where) allocates $perFrame bytes above an empty frame")
            }
        }
    }

    /**
     * The frost and the stamp are cryo while the Core freezes (Easy mode never gets further) and turn threat only while
     * it fractures, in every preset with a threat hue of its own; Mono hatches the fracturing plate. The edge frost
     * never takes the threat colour.
     */
    @Test
    fun theFrostStaysCryoUntilTheCoreFracturesAndMonoHatchesTheFracture() {
        val (w, h) = 1_440 to 810
        val base = hudTestModel(w.toFloat(), h.toFloat())
        val freezing = base.with("coreStasis" to 1f)
        val easy = base.with("coreStasis" to 1f, "easyMode" to true)
        val fracturing = base.with("coreStasis" to 1f, "coreStasisFracturing" to true)
        val edge = Rect(0f, h * 0.4f, 24f, h * 0.6f)
        for (roles in listOf(KkRolePalette.Default, KkRolePalette.Protan, KkRolePalette.Deutan, KkRolePalette.Tritan)) {
            val thawed = render(base, w, h, roles)
            val frozen = render(freezing, w, h, roles)
            val stamp = interior(assertNotNull(HudLayoutProbe.rect(HudBlock.CORE_STASIS)))
            val cryo = roles.element(StatusElement.CRYO.ordinal)
            assertEquals(0, near(frozen, stamp, roles.threat), "${roles.threat}: a freezing stamp shows threat")
            assertTrue(near(frozen, stamp, cryo) > 50, "${roles.threat}: the freezing stamp is cryo")
            assertEquals(0, near(render(easy, w, h, roles), stamp, roles.threat), "${roles.threat}: Easy shows threat")
            val broken = render(fracturing, w, h, roles)
            assertTrue(near(broken, interior(assertNotNull(HudLayoutProbe.rect(HudBlock.CORE_STASIS))), roles.threat) > 50,
                "${roles.threat}: the fracturing stamp is threat")
            assertTrue(differing(thawed, frozen, edge) > 50, "${roles.threat}: frost creeps in at the edges")
            assertEquals(0, near(frozen, edge, roles.threat), "${roles.threat}: the edge frost shows threat")
            assertEquals(0, near(broken, edge, roles.threat), "${roles.threat}: the fracturing edge frost shows threat")
        }
        val hatched = render(fracturing, w, h, KkRolePalette.Mono)
        val plate = interior(assertNotNull(HudLayoutProbe.rect(HudBlock.CORE_STASIS)))
        val plain = render(fracturing, w, h, KkRolePalette.Mono.copy(hatchThreats = false))
        assertTrue(differing(hatched, plain, plate) >= 6, "Mono hatches the fracturing plate")
        assertEquals(0, differing(render(freezing, w, h, KkRolePalette.Mono), render(freezing, w, h, KkRolePalette.Mono.copy(hatchThreats = false)),
            plate), "Mono leaves the freezing plate plain")
    }

    @Test
    fun theRussianStampFitsAPortraitPhoneAtTheLargestTextScale() {
        val (w, h) = 390 to 844
        val model = hudTestModel(w.toFloat(), h.toFloat()).with("coreStasis" to 1f, "coreStasisFracturing" to true)
        for (language in listOf(AppLanguage.Russian, AppLanguage.English)) {
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.75f,
                language, HudTestFonts.typography)
            HudDrawCache.forgetLayouts()
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(w, h)), Size(w.toFloat(), h.toFloat())) {
                drawRect(Kk.Ink)
                drawHud(model, measurer, 0f)
            }
            val stamp = assertNotNull(HudLayoutProbe.rect(HudBlock.CORE_STASIS))
            val layout = assertNotNull(HudDrawCache.peekLayout(HudText.CORE_STASIS))
            assertFalse(layout.isCut(), "$language: the stamp's text is cut")
            assertTrue(stamp.left >= 8f && stamp.right <= w - 8f, "$language: the stamp $stamp leaves the $w px screen")
            assertTrue(layout.size.width < stamp.width, "$language: the text overflows its plate")
        }
    }

    private fun render(model: GameplayRenderModel, width: Int, height: Int, roles: KkRolePalette): PixelMap {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
            AppLanguage.English, HudTestFonts.typography, roles)
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            // Render time 0: every pulse sits at its full strength.
            drawHud(model, measurer, 0f)
        }
        return bitmap.toPixelMap()
    }

    /** The plate inside its tilt and shadow. */
    private fun interior(rect: Rect): Rect = Rect(rect.left + 6f, rect.top + 4f, rect.right - 6f, rect.bottom - 4f)

    private fun near(map: PixelMap, rect: Rect, color: Color): Int {
        var count = 0
        for (y in rect.top.toInt().coerceAtLeast(0) until rect.bottom.toInt().coerceAtMost(map.height)) {
            for (x in rect.left.toInt().coerceAtLeast(0) until rect.right.toInt().coerceAtMost(map.width)) {
                val p = map[x, y]
                if (abs(p.red - color.red) + abs(p.green - color.green) + abs(p.blue - color.blue) < 0.12f) count++
            }
        }
        return count
    }

    private fun differing(a: PixelMap, b: PixelMap, rect: Rect): Int {
        var count = 0
        for (y in rect.top.toInt().coerceAtLeast(0) until rect.bottom.toInt().coerceAtMost(a.height)) {
            for (x in rect.left.toInt().coerceAtLeast(0) until rect.right.toInt().coerceAtMost(a.width)) {
                val p = a[x, y]
                val q = b[x, y]
                if (abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue) > 0.02f) count++
            }
        }
        return count
    }

    /**
     * Bytes a steady [draw] frame allocates above an empty frame: [FRAMES] frames warm every cache, then [FRAMES]
     * frames are measured against as many empty ones; with [cycles] above 1 the quietest of that many full cycles counts
     * (a one-off JIT burst shows in one cycle only). One density and one draw block per call.
     */
    private inline fun steadyFrameBytes(width: Int, height: Int, cycles: Int = 1, crossinline draw: DrawScope.(frame: Int) -> Unit): Long {
        val canvas = Canvas(ImageBitmap(width, height))
        val scope = CanvasDrawScope()
        val size = Size(width.toFloat(), height.toFloat())
        val density = Density(1f)
        val clear: DrawScope.() -> Unit = { drawRect(Kk.Ink) }
        var current = 0
        val frame: DrawScope.() -> Unit = { draw(current) }
        repeat(FRAMES) {
            scope.draw(density, LayoutDirection.Ltr, canvas, size, clear)
            current = it
            scope.draw(density, LayoutDirection.Ltr, canvas, size, frame)
        }
        val emptyBytes = allocated { repeat(FRAMES) { scope.draw(density, LayoutDirection.Ltr, canvas, size, clear) } }
        val drawnBytes = List(cycles) {
            allocated {
                repeat(FRAMES) {
                    current = it
                    scope.draw(density, LayoutDirection.Ltr, canvas, size, frame)
                }
            }
        }.min()
        return (drawnBytes - emptyBytes) / FRAMES
    }

    private inline fun allocated(block: () -> Unit): Long {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(id)
        block()
        return threads.getThreadAllocatedBytes(id) - before
    }

    private companion object {
        const val FRAMES = 200
        const val WORLD_WIDTH = 960
        const val WORLD_HEIGHT = 600
    }
}
