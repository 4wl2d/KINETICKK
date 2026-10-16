// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkSlabKind
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A dealt hand animates every frame: once warm, drawing the faces of an epic and a legendary card
 * (material, ornament, watermark, art and the legendary scene) over a whole loop allocates nothing
 * beyond an empty frame, in a hue palette and in Mono, on the board and on a phone.
 */
class RewardCardFaceAllocationTest {
    @Test
    fun epicAndLegendaryFacesAllocateNothingPerFrame() {
        val content = shippedRewardContent
        val epics = content.items.filter { it.rarity == ItemRarity.EPIC }
        val legendaries = content.items.filter { it.rarity == ItemRarity.LEGENDARY }
        for (palette in listOf(KkRolePalette.Default, KkRolePalette.Mono)) for ((width, height, phone) in listOf(Triple(300, 450, false), Triple(195, 254, true))) {
            // Every epic and legendary once, side by side in rows, as their own cards (own caches).
            val items = epics + legendaries
            val choices = items.map { ChoiceOption(ChoiceType.ITEM, it.name, it.description, it.rarity.displayLabel, itemId = it.id) }
            val model = rewardFixtureModel(content = content, choices = choices)
            val cards = choices.mapIndexed { index, choice -> model.rewardCardPresentation(choice, index) }
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
                AppLanguage.English, roles = palette)
            val looks = cards.map { rewardCardLook(it, palette) }
            val caches = cards.map { RewardArtCache() }
            val paths = cards.map { KkPathCache(4) }
            val columns = 5
            val canvasWidth = columns * width
            val canvasHeight = ((cards.size + columns - 1) / columns) * height
            val bitmap = ImageBitmap(canvasWidth, canvasHeight)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            val size = Size(canvasWidth.toFloat(), canvasHeight.toFloat())
            val cardSize = Size(width.toFloat(), height.toFloat())
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            fun hand(frame: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                val time = frame / 60f
                drawRect(Kk.Ink)
                for (index in cards.indices) {
                    translate((index % columns) * cardSize.width, (index / columns) * cardSize.height) {
                        drawContext.size = cardSize
                        drawRewardCardFace(measurer, cards[index], cards[index].accent, time, 0f, -1f, paths[index], phone, looks[index], caches[index])
                        drawContext.size = size
                    }
                }
            }
            // Warm every cache over one whole loop (every legendary scene loops within 8 s), then measure the same frames.
            repeat(FRAMES) { empty(); hand(it) }
            val emptyBytes = allocated { repeat(FRAMES) { empty() } }
            val handBytes = allocated { repeat(FRAMES) { hand(it) } }
            val perFrame = (handBytes - emptyBytes) / FRAMES
            assertTrue(perFrame <= 64, "${cards.size} epic and legendary faces at $width x $height allocate $perFrame bytes per frame")
        }
    }

    @Test
    fun theTextPlatesAllocateNothingPerFrame() {
        // Every card draws soft plates under its text rows. Once built for a layout, drawing them (and
        // the per-frame check that the layout still stands) allocates nothing, frame after frame.
        val size = Size(300f, 450f)
        val bitmap = ImageBitmap(300, 450)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        val plates = RewardFacePlates()
        val rows = listOf(
            Triple("chips", Rect(138f, 56f, 266f, 150f), null),
            Triple("stat0", Rect(34f, 220f, 266f, 246f), null),
            Triple("stat1", Rect(34f, 252f, 266f, 272f), null),
            Triple("rule0", Rect(34f, 280f, 266f, 322f), null),
            Triple("rule2", Rect(34f, 328f, 266f, 352f), null),
            Triple("cost", Rect(34f, 358f, 266f, 392f), null),
            Triple("description0", Rect(34f, 398f, 266f, 414f), null),
            Triple("footer", Rect(30f, 404f, 260f, 432f), null),
        )
        val face = Kk.Ink2
        val facePath = KkPathCache(1)
        val faceRect = Rect(Offset.Zero, size)
        scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { plates.build(this, face, 0f, Rect(0f, 162f, 300f, 432f), rows) }
        assertTrue(plates.textBottom in 413f..415f, "The text ends at its lowest row above the footer (${plates.textBottom})")
        fun frame() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
            drawRect(Kk.Ink)
            plates.prepare(this, face, 0f)
            drawRewardFacePlates(plates, facePath.slab(0, faceRect, 22f, KkSlabKind.SLAB))
        }
        fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
        repeat(FRAMES) { frame(); empty() }
        val emptyBytes = allocated { repeat(FRAMES) { empty() } }
        val frameBytes = allocated { repeat(FRAMES) { frame() } }
        val perFrame = (frameBytes - emptyBytes) / FRAMES
        assertTrue(perFrame <= 64, "${rows.size} text plates allocate $perFrame bytes per frame")
    }

    private inline fun allocated(block: () -> Unit): Long {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(id)
        block()
        return threads.getThreadAllocatedBytes(id) - before
    }

    private companion object {
        /** Eight seconds at 60 fps: a whole loop of every animated layer. */
        const val FRAMES = 480
    }
}
