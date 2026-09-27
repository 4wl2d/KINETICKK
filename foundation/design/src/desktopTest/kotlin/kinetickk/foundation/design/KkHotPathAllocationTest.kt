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
        val hudFrame: DrawScope.(Float) -> Unit = { time ->
            drawKkMeter(meter, 0.62f, measurer.roles.threat, measurer.roles, segments = 10, ghost = 0.1f, threat = true)
            drawKkMeter(meter, 0.8f, measurer.roles.you, measurer.roles, stripes = KkMeterStripes.YOU, time = time)
            drawKkTickLadder(meter, measurer.roles, 30, 22, 18, 28)
            drawKkPips(Offset(20f, 40f), 5, 3, measurer.roles.shield, nextOutlined = true, emptyOutlined = true)
            drawKkRelicSlot(Offset(340f, 50f), Kk.AIon, KkIcon.ASPECT_ION, ring = measurer.roles.you)
            drawKkSynergyLink(Offset(362f, 50f), Offset(376f, 50f), measurer.roles.you)
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
            drawKkToast(measurer, Offset(260f, 120f), "Build updated", "Echo Reactor", gem = true)
            drawKkHatch(grid)
            drawKkHalftone(grid, measurer.roles.you)
            drawKkGrid(grid)
            drawKkStripes(grid, measurer.roles.threat, time = time)
            drawKkSeparator(300f, 20f, 11f, Kk.Bone)
            drawKkSynergyBracket(measurer, 320f, 400f, 100f, measurer.roles.you, tag = "Synergy")
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
}
