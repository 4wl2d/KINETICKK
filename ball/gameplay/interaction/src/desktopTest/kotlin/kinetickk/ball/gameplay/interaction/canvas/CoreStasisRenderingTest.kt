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
import kinetickk.ball.gameplay.interaction.drawGameplay
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.CoreStasisMotion
import kinetickk.ball.gameplay.nucleus.render.GamePhase
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
 * Core Freeze on screen: the frost over the Core, the ring meter, the screen-edge frost and the warning stamp with its
 * countdown draw without allocating on steady frames, keep to the role palette (cryo while freezing, threat over a
 * lethal freeze's last stretch and never in Easy mode, hatched in Mono; the edge frost never threat), fit a portrait
 * phone at the largest text size in Russian, and the death frame shows the Core encased and its ice bursting.
 */
class CoreStasisRenderingTest {
    @Test
    fun coreFrostDrawsWithoutAllocatingForEveryCoreShape() {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
            AppLanguage.English)
        for (shape in CoreShape.entries) for ((freeze, motion) in FROST_CASES) {
            val base = hudTestModel(WORLD_WIDTH.toFloat(), WORLD_HEIGHT.toFloat()).with("coreShape" to shape)
            fun frames(frost: Boolean) = List(FRAMES) { frame ->
                base.with("elapsed" to 20f + frame * 0.013f, "coreStasis" to if (frost) freeze else 0f,
                    "coreStasisMotion" to if (frost) motion else CoreStasisMotion.CLEAR)
            }
            val plain = frames(frost = false)
            val frosted = frames(frost = true)
            val bytes = { models: List<GameplayRenderModel> ->
                steadyFrameBytes(WORLD_WIDTH, WORLD_HEIGHT, cycles = 3) { frame ->
                    drawWorld(models[frame % FRAMES], VisualFxProjection.EMPTY, 0f, 0f, measurer)
                }
            }
            val extra = bytes(frosted) - bytes(plain)
            assertTrue(extra <= 16, "$shape frost at $freeze ($motion) allocates $extra bytes per frame")
        }
    }

    @Test
    fun theStampRingAndEdgeFrostAllocateNothingOnSteadyFrames() {
        for ((width, height) in listOf(1_440 to 810, 844 to 390, 390 to 844)) for (language in listOf(AppLanguage.English, AppLanguage.Russian)) {
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f, language)
            for ((freeze, motion) in HUD_CASES) {
                val model = hudTestModel(width.toFloat(), height.toFloat()).with("coreStasis" to freeze, "coreStasisMotion" to motion)
                val memory = HudPresentationMemory()
                val perFrame = steadyFrameBytes(width, height) { frame ->
                    drawHud(model, measurer, 10f + frame * 0.0021f, 0f, 0f, memory)
                }
                val where = "$language $freeze $motion at $width x $height"
                assertNotNull(HudLayoutProbe.rect(HudBlock.CORE_STASIS), "$where: no stamp")
                assertTrue(perFrame <= 64, "Core Freeze HUD ($where) allocates $perFrame bytes above an empty frame")
            }
        }
    }

    /**
     * The stamp is cryo while the Core freezes and turns threat over the last stretch of a freeze that can kill, in every
     * preset with a threat hue of its own; Easy mode never shows threat, a thawing Core shows bone, Mono hatches the
     * urgent plate, and the edge frost never takes the threat colour.
     */
    @Test
    fun theStampStaysCryoUntilTheLastStretchAndMonoHatchesIt() {
        val (w, h) = 1_440 to 810
        val base = hudTestModel(w.toFloat(), h.toFloat())
        val freezing = base.with("coreStasis" to 0.5f, "coreStasisMotion" to CoreStasisMotion.FREEZING)
        val urgent = base.with("coreStasis" to 0.9f, "coreStasisMotion" to CoreStasisMotion.FREEZING)
        val easy = urgent.with("easyMode" to true)
        val thawing = base.with("coreStasis" to 0.9f, "coreStasisMotion" to CoreStasisMotion.THAWING)
        val edge = Rect(0f, h * 0.4f, 24f, h * 0.6f)
        for (roles in listOf(KkRolePalette.Default, KkRolePalette.Protan, KkRolePalette.Deutan, KkRolePalette.Tritan)) {
            val thawed = render(base, w, h, roles)
            val cold = render(freezing, w, h, roles)
            val stamp = interior(assertNotNull(HudLayoutProbe.rect(HudBlock.CORE_STASIS)))
            val cryo = roles.element(StatusElement.CRYO.ordinal)
            assertEquals(0, near(cold, stamp, roles.threat), "${roles.threat}: a freezing stamp shows threat")
            assertTrue(near(cold, stamp, cryo) > 50, "${roles.threat}: the freezing stamp is cryo")
            val last = render(urgent, w, h, roles)
            assertTrue(near(last, interior(assertNotNull(HudLayoutProbe.rect(HudBlock.CORE_STASIS))), roles.threat) > 50,
                "${roles.threat}: the last stretch is threat")
            assertEquals(0, near(render(easy, w, h, roles), interior(assertNotNull(HudLayoutProbe.rect(HudBlock.CORE_STASIS))), roles.threat),
                "${roles.threat}: Easy shows threat")
            val melting = render(thawing, w, h, roles)
            val thawStamp = interior(assertNotNull(HudLayoutProbe.rect(HudBlock.CORE_STASIS)))
            assertEquals(0, near(melting, thawStamp, roles.threat), "${roles.threat}: a thawing stamp shows threat")
            assertTrue(near(melting, thawStamp, Kk.Bone) > 50, "${roles.threat}: the thawing stamp is bone")
            assertTrue(differing(thawed, cold, edge) > 50, "${roles.threat}: frost creeps in at the edges")
            assertEquals(0, near(last, edge, roles.threat), "${roles.threat}: the edge frost shows threat")
        }
        val hatched = render(urgent, w, h, KkRolePalette.Mono)
        val plate = interior(assertNotNull(HudLayoutProbe.rect(HudBlock.CORE_STASIS)))
        val plain = render(urgent, w, h, KkRolePalette.Mono.copy(hatchThreats = false))
        assertTrue(differing(hatched, plain, plate) >= 6, "Mono hatches the urgent plate")
        assertEquals(0, differing(render(freezing, w, h, KkRolePalette.Mono), render(freezing, w, h, KkRolePalette.Mono.copy(hatchThreats = false)),
            plate), "Mono leaves the freezing plate plain")
    }

    /** The ring meter fills clockwise from the top with the freeze: at a third only its right side is lit (Easy: all cryo). */
    @Test
    fun theRingMeterFillsWithTheFreeze() {
        val (w, h) = 1_440 to 810
        val roles = KkRolePalette.Default
        val cryo = roles.element(StatusElement.CRYO.ordinal)
        fun lit(freeze: Float, region: (Float, Float) -> Rect): Int {
            val model = hudTestModel(w.toFloat(), h.toFloat())
                .with("coreStasis" to freeze, "coreStasisMotion" to CoreStasisMotion.FREEZING, "easyMode" to true)
            val core = androidx.compose.ui.geometry.Offset(w * 0.5f + model.renderCoreX - model.cameraX, h * 0.5f + model.renderCoreY - model.cameraY)
            return near(render(model, w, h, roles), region(core.x, core.y), cryo)
        }
        // The ring sits at 1.36 halo radii (about 51 px for the Orb) around the Core.
        val right: (Float, Float) -> Rect = { x, y -> Rect(x + 44f, y - 12f, x + 60f, y + 12f) }
        val left: (Float, Float) -> Rect = { x, y -> Rect(x - 60f, y - 12f, x - 44f, y + 12f) }
        assertTrue(lit(0.3f, right) > 2 * lit(0.3f, left), "a third of the freeze lights the right side first")
        assertTrue(lit(0.9f, left) > lit(0.3f, left), "the left side lights later")
    }

    @Test
    fun theRussianStampFitsAPortraitPhoneAtTheLargestTextScale() {
        val (w, h) = 390 to 844
        val model = hudTestModel(w.toFloat(), h.toFloat()).with("coreStasis" to 0.9f, "coreStasisMotion" to CoreStasisMotion.FREEZING)
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

    /** The run's end of a Core frozen solid: the Core stays drawn, encased in ice, then the ice bursts into cryo shards. */
    @Test
    fun theDeathFrameShowsTheEncasedCoreAndItsIceBursting() {
        val (w, h) = 960 to 600
        val roles = KkRolePalette.Default
        val cryo = roles.element(StatusElement.CRYO.ordinal)
        val frozen = hudTestModel(w.toFloat(), h.toFloat())
            .with("phase" to GamePhase.GAME_OVER, "coreStasis" to 1f, "coreStasisMotion" to CoreStasisMotion.FROZEN)
        val shattered = frozen.with("coreStasisMotion" to CoreStasisMotion.CLEAR, "coreStasis" to 0f)
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
            AppLanguage.English, HudTestFonts.typography, roles)
        fun frame(model: GameplayRenderModel, elapsed: Float): PixelMap {
            val bitmap = ImageBitmap(w, h)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
                drawGameplay(model, VisualFxProjection.EMPTY, measurer, 0f, null, terminalElapsed = elapsed)
            }
            return bitmap.toPixelMap()
        }
        val core = Rect(w * 0.5f - 40f, h * 0.5f - 40f, w * 0.5f + 40f, h * 0.5f + 40f)
        val ring = Rect(w * 0.5f - 160f, h * 0.5f - 160f, w * 0.5f + 160f, h * 0.5f + 160f)
        assertTrue(near(frame(frozen, 0.2f), core, cryo) > 200, "the encased Core is drawn in ice")
        assertTrue(near(frame(frozen, 0.7f), ring, cryo) > near(frame(shattered, 0.7f), ring, cryo) + 200, "cryo shards burst")
        assertEquals(0, near(frame(frozen, 0.7f), ring, roles.threat), "a freeze death shows no threat shards")
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
        val FROST_CASES = listOf(0.3f to CoreStasisMotion.FREEZING, 0.95f to CoreStasisMotion.FREEZING, 0.6f to CoreStasisMotion.THAWING)
        val HUD_CASES = listOf(
            0.3f to CoreStasisMotion.FREEZING, 0.85f to CoreStasisMotion.FREEZING,
            0.6f to CoreStasisMotion.THAWING, 0.5f to CoreStasisMotion.HOLDING,
        )
    }
}
