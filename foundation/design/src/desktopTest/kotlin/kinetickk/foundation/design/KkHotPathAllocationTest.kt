// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The HUD/world performance contract: helpers documented as allocation-free allocate no JVM heap
 * per frame once their caches are warm (paths, strokes, tiles, styles and layouts).
 */
class KkHotPathAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun hudHelpersDoNotAllocatePerFrame() {
        val measurer = kkTestMeasurer(roles = KkRolePalette.Mono)
        val meter = Rect(20f, 20f, 220f, 34f)
        val slot = Rect(240f, 20f, 302f, 82f)
        val badgeAt = Offset(20f, 60f)
        val paths = KkPathCache(4)
        val grid = Rect(400f, 100f, 540f, 170f)
        var frameIndex = 0
        val hudFrame: DrawScope.(Float) -> Unit = { time ->
            // The tone and element lookups change every frame, as damage numbers and status rims do.
            val tone = frameIndex++ % 6
            drawKkMeter(meter, 0.62f, measurer.roles.threat, measurer.roles, segments = 10, ghost = 0.1f, threat = true)
            drawKkMeter(meter, 0.8f, measurer.roles.you, measurer.roles, stripes = KkMeterStripes.YOU, time = time)
            drawKkTickLadder(meter, measurer.roles, 30, 22, 18, 28)
            drawKkPips(Offset(20f, 40f), 5, 3, measurer.roles.shield, nextOutlined = true, emptyOutlined = true)
            drawKkRelicSlot(Offset(340f, 50f), Kk.AIon, KkIcon.ASPECT_ION, ring = measurer.roles.you)
            drawKkSynergyLink(Offset(362f, 50f), Offset(376f, 50f), measurer.roles.you)
            // Status and damage tones and the element/tag glyphs are per-frame lookups too.
            for (index in 0..5) drawKkIcon(KkIcon.Aspects[index], Offset(20f + index * 18f, 100f), 16f, measurer.roles.tone(index))
            for (index in 0 until KkIcon.Tags.size) drawKkIcon(KkIcon.Tags[index], Offset(140f + index * 18f, 100f), 16f, Kk.Bone)
            drawKkRelicSlot(Offset(390f, 50f), measurer.roles.element(tone % 5), KkIcon.Elements[tone % 5])
            drawKkPips(Offset(20f, 112f), 4, 2, Kk.tone(tone), sizeDp = 6f)
            drawKkIcon(KkIcon.Aspects[tone], Offset(128f, 112f), 12f, measurer.roles.tone(tone))
            drawKkWeaponSlot(measurer, slot, KkIcon.WEAPONS_ARC_COIL, "Lvl 3", cooldown = 0.4f, ready = true)
            drawKkIcon(KkIcon.WEAPONS_GRAVITY_MINES, Offset(420f, 50f), 32f, measurer.roles.you)
            drawKkLevelBadge(measurer, badgeAt, 24, time = time)
            drawKkChip(measurer, Offset(20f, 120f), "1,284", "Matter")
            drawKkThreatHatch(paths.slab(0, 440f, 20f, 520f, 60f, 10f), measurer.roles)
            drawPath(paths.sheared(1, 440f, 70f, 520f, 90f), measurer.roles.heat)
            drawKkRingBurst(Offset(480f, 120f), 30f, 0.5f, measurer.roles.you)
            drawKkShutter(200f, measurer.roles)
            drawKkTag(measurer, "Elite", Offset(20f, 150f), KkTagVariant.THREAT)
            drawKkStamp(measurer, "Overheat", Offset(120f, 150f), KkStampVariant.THREAT)
            drawKkToast(measurer, Offset(260f, 120f), "Reaction discovered", "+ Superconduct", gem = true)
            drawKkHatch(grid)
            drawKkHalftone(grid, measurer.roles.you)
            drawKkGrid(grid)
            drawKkStripes(grid, measurer.roles.threat, time = time)
            drawKkSeparator(300f, 20f, 11f, Kk.Bone)
            drawKkSynergyBracket(measurer, 320f, 400f, 100f, measurer.roles.you, tag = "Cold Verdict")
            drawKkText(measurer, "07:42", measurer.typography.wideStyle(44f, tabular = true), 280f, 10f, Kk.Bone)
        }
        val bitmap = ImageBitmap(560, 180)
        val scope = CanvasDrawScope()
        val canvas = Canvas(bitmap)
        val thread = Thread.currentThread().id
        fun bytesPerFrame(block: DrawScope.() -> Unit): Long {
            fun frame() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(560f, 180f), block)
            repeat(300) { frame() } // warm caches and JIT
            val before = threads.getThreadAllocatedBytes(thread)
            repeat(500) { frame() }
            return (threads.getThreadAllocatedBytes(thread) - before) / 500
        }
        val baseline = bytesPerFrame { }
        val hud = bytesPerFrame { hudFrame(0.25f) }
        // CanvasDrawScope.draw itself allocates a little per call (the baseline); the helpers must
        // not add paths, strokes, brushes, styles, rects, strings or text layouts on top of it.
        assertTrue(hud - baseline < 64, "HUD helpers allocate ${hud - baseline} bytes per frame above the $baseline byte baseline")
    }

    @Test
    fun levelBadgeTiersApexStatesAndTheirMetersDoNotAllocatePerFrame() {
        // Protan: the deep gold everywhere the badge and its meters read the preset's gold.
        val measurer = kkTestMeasurer(roles = KkRolePalette.Protan)
        val bar = Rect(20f, 120f, 140f, 124f)
        val line = Rect(0f, 0f, 640f, 4f)
        val bitmap = ImageBitmap(640, 180)
        val scope = CanvasDrawScope()
        val canvas = Canvas(bitmap)
        val thread = Thread.currentThread().id
        var frameIndex = 0
        fun frame(draw: Boolean) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(640f, 180f)) {
            // Inside one animation cycle: rays turn, foil and stripes scroll, the ready glow pulses.
            val time = 1f + (frameIndex++ % 500) * 0.002f
            if (!draw) return@draw
            drawKkLevelBadge(measurer, Offset(20f, 40f), 41, time = time, maxLevel = 42)
            drawKkLevelBadge(measurer, Offset(170f, 40f), 42, time = time, maxLevel = 42, apex = KkApexState.COOLING)
            drawKkLevelBadge(measurer, Offset(320f, 40f), 42, time = time, maxLevel = 42, apex = KkApexState.READY)
            drawKkLevelBadge(measurer, Offset(470f, 40f), 42, time = time, maxLevel = 42, apex = KkApexState.ACTIVE, apexActive = 0.5f)
            drawKkMeter(bar, 0.7f, measurer.roles.gold.base, measurer.roles, stripes = KkMeterStripes.GOLD, time = time)
            drawKkMeter(bar, 1f, measurer.roles.gold.base, measurer.roles, fillBrush = kkFoilBrush(bar.width * 2.6f, measurer.roles.gold),
                fillAlpha = 0.75f + 0.25f * kkPulse(time, 1.2f), edge = true, edgeColor = measurer.roles.you)
            drawKkFoil(line, time, gold = measurer.roles.gold)
            drawKkMeterStripes(0f, 8f, 320f, 12f, KkMeterStripes.GOLD, measurer.roles, time)
        }
        repeat(300) { frame(true) } // warm caches and JIT
        val before = threads.getThreadAllocatedBytes(thread)
        repeat(500) { frame(false) }
        val baseline = (threads.getThreadAllocatedBytes(thread) - before) / 500
        val start = threads.getThreadAllocatedBytes(thread)
        repeat(500) { frame(true) }
        val perFrame = (threads.getThreadAllocatedBytes(thread) - start) / 500
        assertTrue(perFrame - baseline < 64, "T5/T6 badges and their meters allocate ${perFrame - baseline} bytes per frame")
    }

    @Test
    fun skillSlotsInEveryStateDoNotAllocatePerFrame() {
        val measurer = kkTestMeasurer()
        val key = measureKkKeyCap(measurer, "Space", 10f)
        val slots = listOf(Rect(10f, 10f, 62f, 62f), Rect(80f, 10f, 132f, 62f), Rect(150f, 10f, 202f, 62f), Rect(220f, 10f, 272f, 62f),
            Rect(290f, 10f, 342f, 62f))
        val bitmap = ImageBitmap(400, 120)
        val scope = CanvasDrawScope()
        val canvas = Canvas(bitmap)
        val thread = Thread.currentThread().id
        var frameIndex = 0
        fun frame(draw: Boolean) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(400f, 120f)) {
            val t = (frameIndex++ % 200) / 200f
            if (!draw) return@draw
            val ion = measurer.roles.element(2)
            drawKkSkillSlot(measurer, slots[0], KkIcon.WEAPONS_ARC_COIL, ion, cooldown = 1f - t, ready = false, rank = 2,
                refundFlash = 1f - t, refundColor = ion, keyLayout = key, rankFill = t)
            drawKkSkillSlot(measurer, slots[1], KkIcon.WEAPONS_MORNINGSTAR, ion, cooldown = 0f, ready = true, rank = 3,
                charges = 1, maxCharges = 2, active = t, activeColor = ion, pending = 1f - t, pressed = 1f, readyFlash = t, keyLayout = key)
            drawKkSkillSlot(measurer, slots[2], KkIcon.WEAPONS_GYRE_DISC, Kk.Bone, cooldown = 0.4f, ready = false, rank = 1,
                shakeX = 3f * (t - 0.5f), keyLayout = key)
            drawKkSkillSlot(measurer, slots[3], null, Kk.Mute2, cooldown = 0f, ready = false, rank = 0, locked = true)
            drawKkSkillSlot(measurer, slots[4], null, Kk.Mute2, cooldown = 0f, ready = false, rank = 0, pressed = if (t < 0.5f) 1f else 0f,
                empty = true)
        }
        repeat(300) { frame(true) } // warm caches and JIT
        val before = threads.getThreadAllocatedBytes(thread)
        repeat(500) { frame(false) }
        val baseline = (threads.getThreadAllocatedBytes(thread) - before) / 500
        val start = threads.getThreadAllocatedBytes(thread)
        repeat(500) { frame(true) }
        val perFrame = (threads.getThreadAllocatedBytes(thread) - start) / 500
        assertTrue(perFrame - baseline < 64, "skill slots allocate ${perFrame - baseline} bytes per frame")
    }

    @Test
    fun numbersThatChangeEveryFrameDoNotMeasureText() {
        val measurer = kkTestMeasurer()
        val style = measurer.typography.wideStyle(44f, tabular = true)
        val bitmap = ImageBitmap(400, 80)
        val scope = CanvasDrawScope()
        val canvas = Canvas(bitmap)
        val thread = Thread.currentThread().id
        var value = 0L
        fun frame(draw: Boolean) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(400f, 80f)) {
            if (draw) {
                drawKkTabularNumber(measurer, value, style, 200f, 10f, Kk.Bone, KkAlign.CENTER)
                drawKkTabularNumber(measurer, value % 100L, style, 390f, 10f, Kk.Bone, KkAlign.END, suffix = "%")
            }
            value += 7L
        }
        repeat(300) { frame(true) } // measures every digit once and warms the JIT
        val before = threads.getThreadAllocatedBytes(thread)
        repeat(500) { frame(false) }
        val baseline = (threads.getThreadAllocatedBytes(thread) - before) / 500
        val start = threads.getThreadAllocatedBytes(thread)
        repeat(500) { frame(true) }
        val perFrame = (threads.getThreadAllocatedBytes(thread) - start) / 500
        assertTrue(perFrame - baseline < 64, "changing numbers allocate ${perFrame - baseline} bytes per frame")
    }

    @Test
    fun numbersSharingAStyleInDifferentColorsDoNotRepaintSharedDigits() {
        // A chain count and a draining percentage in one style but two colors, sharing digits on
        // every frame: each color keeps its own digit layouts instead of repainting shared ones.
        val measurer = kkTestMeasurer()
        val style = measurer.typography.condStyle(26f, tabular = true)
        val bitmap = ImageBitmap(400, 80)
        val scope = CanvasDrawScope()
        val canvas = Canvas(bitmap)
        val thread = Thread.currentThread().id
        var frameIndex = 0
        fun frame(draw: Boolean) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(400f, 80f)) {
            if (draw) {
                val percent = 21L - frameIndex % 12L
                drawKkTabularNumber(measurer, percent, style, 60f, 10f, measurer.roles.threat, suffix = "%")
                drawKkTabularNumber(measurer, 12L, style, 390f, 10f, Kk.Bone, KkAlign.END, prefix = "×")
            }
            frameIndex++
        }
        repeat(300) { frame(true) }
        val measured = KkTextStats.layoutsMeasured
        val before = threads.getThreadAllocatedBytes(thread)
        repeat(480) { frame(false) }
        val baseline = (threads.getThreadAllocatedBytes(thread) - before) / 480
        val start = threads.getThreadAllocatedBytes(thread)
        repeat(480) { frame(true) }
        val perFrame = (threads.getThreadAllocatedBytes(thread) - start) / 480
        assertEquals(measured, KkTextStats.layoutsMeasured, "no digit is measured again")
        assertTrue(perFrame - baseline < 64, "two colors on one style allocate ${perFrame - baseline} bytes per frame")
    }
}
