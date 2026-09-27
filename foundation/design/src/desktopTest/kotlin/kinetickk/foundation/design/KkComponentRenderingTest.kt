// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KkComponentRenderingTest {
    private val measurer = kkTestMeasurer()

    private fun image(width: Int = 240, height: Int = 120, draw: DrawScope.() -> Unit) = kkRender(width, height, draw = draw)

    private val face = Rect(40f, 34f, 180f, 86f)

    @Test
    fun buttonStatesAreVisuallyDistinctAndUseRoleColors() {
        fun button(
            hover: Float = 0f,
            focused: Boolean = false,
            pressed: Boolean = false,
            enabled: Boolean = true,
            locked: Boolean = false,
            armed: Boolean = false,
            roles: KkRolePalette = KkRolePalette.Default,
        ) = image {
            drawKkButton(kkTestMeasurer(roles), face, "Deploy", hover = hover, focused = focused, pressed = pressed,
                enabled = enabled, locked = locked, armed = armed)
        }
        val idle = button()
        val states = listOf(idle, button(hover = 1f), button(focused = true), button(pressed = true), button(enabled = false),
            button(locked = true), button(armed = true))
        assertEquals(states.size, states.map { it.argb().toList() }.toSet().size, "every state differs")
        // Idle face is `you`; the echo only appears on hover/focus, below-right of the face.
        assertEquals(Kk.Volt.toArgb(), idle.at(60, 44))
        assertEquals(0, idle.regionCountNear(Kk.Bone, 150, 87, 188, 94))
        assertTrue(button(hover = 1f).regionCountNear(Kk.Bone, 150, 87, 192, 94) > 40, "hover echo")
        // Pressed flashes bone; disabled and locked use ink-3; locked is hatched.
        assertEquals(Kk.Bone.toArgb(), button(pressed = true).at(64, 44))
        assertEquals(Kk.Ink3.toArgb(), button(enabled = false).at(60, 44))
        val lockedFace = button(locked = true).argb()
        assertTrue(lockedFace.countNear(Kk.Ink3, 2) in 500..8_000, "hatch interrupts the locked face")
        // Focus draws a 2 dp bone outline 4 dp outside the face.
        assertEquals(Kk.Bone.toArgb(), button(focused = true).at(110, 29))
        // Color vision: the primary face follows the palette, the armed face is the threat color.
        assertEquals(KkRolePalette.Protan.you.toArgb(), button(roles = KkRolePalette.Protan).at(60, 44))
        assertTrue(colorDistance(KkRolePalette.Default.threat.toArgb(), button(armed = true).at(60, 44)) < 70)
    }

    @Test
    fun menuItemSelectionGrowsTheSlabToTheEdgeAndTurnsTheLabelInk() {
        val idle = image(640, 160) { drawKkMenuItem(measurer, 200f, 80f, "Deploy", selection = 0f, edgeRight = 640f) }
        val selected = image(640, 160) { drawKkMenuItem(measurer, 200f, 80f, "Deploy", selection = 1f, time = 0.4f, edgeRight = 640f) }
        assertEquals(0, idle.regionCountNear(Kk.Bone, 600, 40, 640, 120), "no slab when idle")
        assertTrue(selected.regionCountNear(Kk.Bone, 600, 40, 640, 120) > 1_500, "slab reaches the screen edge")
        assertTrue(selected.regionCountNear(Kk.Volt, 0, 0, 640, 160) > 300, "volt echo and trail")
        assertTrue(selected.regionCountNear(Kk.Ink, 200, 50, 400, 110) > 1_000, "ink label on the bone slab")
        val bounds = kkMenuBounds()
        assertTrue(bounds.width > 150f && bounds.height == 66f, "hit bounds $bounds")
        val dim = image(640, 160) { drawKkMenuItem(measurer, 200f, 80f, "Quit", dim = true) }
        assertTrue(dim.countNear(Kk.Mute2, 4) > 200)
    }

    private fun kkMenuBounds(): Rect {
        var bounds = Rect.Zero
        image(640, 160) { bounds = drawKkMenuItem(measurer, 200f, 80f, "Deploy") }
        return bounds
    }

    @Test
    fun metersFillSegmentGhostAndStripe() {
        val bounds = Rect(20f, 20f, 220f, 34f)
        val half = image { drawKkMeter(bounds, 0.5f, Kk.Bone, KkRolePalette.Default) }
        assertEquals(Kk.Bone.toArgb(), half.at(60, 27))
        assertNotEquals(Kk.Bone.toArgb(), half.at(180, 27))
        val segmented = image { drawKkMeter(bounds, 1f, Kk.Bone, KkRolePalette.Default, segments = 10) }
        val row = (22 until 218).map { segmented.at(it, 27) }
        assertTrue(row.count { it == Kk.Ink.toArgb() } >= 18, "3 dp gaps between ten cells")
        val ghost = image { drawKkMeter(bounds, 0.5f, Kk.Bone, KkRolePalette.Default, ghost = 0.2f, ghostColor = Kk.Hazard) }
        assertEquals(Kk.Hazard.toArgb(), ghost.at(140, 27))
        val stripes = image { drawKkMeter(bounds, 1f, Kk.Hazard, KkRolePalette.Default, stripes = KkMeterStripes.THREAT, time = 0.1f) }
        assertTrue(stripes.countNear(Kk.Hazard, 6) > 300)
        assertTrue(stripes.countNear(kkMix(Kk.Hazard, Kk.Ink, 0.45f), 6) > 300)
        val moved = image { drawKkMeter(bounds, 1f, Kk.Hazard, KkRolePalette.Default, stripes = KkMeterStripes.THREAT, time = 0.3f) }
        assertNotEquals(stripes.argb().toList(), moved.argb().toList(), "stripes animate with time")
        // Lean: the top edge of the fill starts further right than the bottom edge.
        val top = (0 until 240).first { half.at(it, 21) == Kk.Bone.toArgb() }
        val bottom = (0 until 240).first { half.at(it, 33) == Kk.Bone.toArgb() }
        assertTrue(top > bottom, "sheared -12 deg ($top vs $bottom)")
    }

    @Test
    fun monoPaletteHatchesThreatsAndOtherPalettesDoNot() {
        val shape = Rect(20f, 20f, 200f, 100f)
        val plain = image { drawRect(Kk.Ink1, shape.topLeft, shape.size) }
        val defaultHatch = image { drawRect(Kk.Ink1, shape.topLeft, shape.size); drawKkThreatHatch(shape, KkRolePalette.Default) }
        val monoHatch = image { drawRect(Kk.Ink1, shape.topLeft, shape.size); drawKkThreatHatch(shape, KkRolePalette.Mono) }
        assertContentEquals(plain.argb(), defaultHatch.argb())
        assertTrue(monoHatch.countNear(Color.White, 40) > 1_500, "white hatch lines over the threat")
        val meter = image { drawKkMeter(Rect(20f, 40f, 220f, 60f), 1f, Color.White, KkRolePalette.Mono, threat = true) }
        assertTrue(meter.countNear(Kk.Ink, 4) > 1_000, "ink hatch over a white threat meter")
    }

    @Test
    fun levelBadgeTiersEscalate() {
        val tiers = listOf(7, 14, 24, 34, 45).map { level ->
            image(260, 200) { drawKkLevelBadge(measurer, Offset(90f, 80f), level, time = 1.5f) }
        }
        assertEquals(5, tiers.map { it.argb().toList() }.toSet().size)
        assertTrue(tiers[0].regionCountNear(Kk.Volt, 0, 0, 260, 200) == 0, "t1 has no volt")
        assertTrue(tiers[1].countNear(Kk.Volt) > 50, "t2 volt echo")
        assertTrue(tiers[2].countNear(Kk.Volt) > 800, "t3 volt face")
        assertTrue(tiers[3].countNear(Kk.Volt) > 200 && tiers[3].countNear(Kk.Ink2, 3) > 300, "t4 ink face, volt text")
        assertTrue(tiers[4].countNear(Kk.RLegend, 40) > 400, "t5 gold foil")
        // Rays extend far outside the t5 badge.
        assertTrue(tiers[4].regionCountNear(Kk.RLegend, 0, 0, 80, 200, 90) > 20 || tiers[4].regionCountNear(Kk.RLegend, 180, 0, 260, 200, 90) > 20)
    }

    @Test
    fun rarityReadsThroughEffects() {
        fun card(rank: Int, time: Float = 1.9f, selected: Float = 0f) = image(320, 360) {
            drawKkCard(measurer, Rect(40f, 50f, 260f, 300f), rank, time, selected, "Rarity", "New", 32f)
        }
        val cards = (1..5).map { card(it) }
        assertEquals(5, cards.map { it.argb().toList() }.toSet().size)
        // Echo offsets: rare 4, epic 6, legendary 8 (striped); common/uncommon none. Sample just
        // outside the face's slanted right edge (x = 260 - 22 * (y - 50) / 250).
        fun echoRows(image: androidx.compose.ui.graphics.ImageBitmap, color: Color, tolerance: Int) = (120 until 290).count { y ->
            val edge = 260f - 22f * (y - 50f) / 250f
            colorDistance(image.at((edge + 2.5f).toInt(), y), color.toArgb()) <= tolerance
        }
        assertEquals(0, echoRows(cards[0], Kk.RCommon, 12), "common has no echo")
        assertTrue(echoRows(cards[2], Kk.RRare, 12) > 120, "rare echo")
        assertTrue(echoRows(cards[4], Kk.RLegend, 40) + echoRows(cards[4], Color(0xFFFFE59A), 40) > 120, "legendary echo")
        // Legendary rays reach outside the card.
        fun warmOutside(image: androidx.compose.ui.graphics.ImageBitmap) = (0 until 360).sumOf { y ->
            (0 until 36).count { x -> image.at(x, y).let { ((it shr 16) and 0xFF) - (it and 0xFF) > 10 } }
        }
        assertTrue(warmOutside(cards[4]) > 30, "legendary rays behind the card")
        assertEquals(0, warmOutside(cards[3]), "no rays below legendary")
        // Epic sparks and halftone animate; common is flat and static.
        assertNotEquals(card(4, 0.4f).argb().toList(), card(4, 1.4f).argb().toList())
        assertContentEquals(card(1, 0.4f).argb(), card(1, 1.4f).argb())
    }

    @Test
    fun slotsShowAspectCooldownReadinessAndEmptiness() {
        val relic = image { drawKkRelicSlot(Offset(60f, 60f), Kk.ARift, KkIcon.ASPECT_RIFT) }
        assertTrue(relic.countNear(Kk.ARift) > 100)
        assertEquals(Kk.Ink2.toArgb(), relic.at(52, 60))
        val empty = image { drawKkRelicSlot(Offset(60f, 60f), Color.Unspecified) }
        assertEquals(0, empty.countNear(Kk.ARift))
        assertTrue(empty.countNear(Kk.Line2, 3) > 60)
        val slot = Rect(40f, 30f, 102f, 92f)
        val ready = image { drawKkWeaponSlot(measurer, slot, KkIcon.WEAPONS_ARC_COIL, "Lvl 3", ready = true) }
        val cooling = image { drawKkWeaponSlot(measurer, slot, KkIcon.WEAPONS_ARC_COIL, "Lvl 3", cooldown = 0.5f) }
        val max = image { drawKkWeaponSlot(measurer, slot, KkIcon.WEAPONS_ARC_COIL, "Lvl 10", ready = true, maxLevel = true) }
        val none = image { drawKkWeaponSlot(measurer, slot, null) }
        val glow = (20 until 100).count { y -> colorDistance(ready.at(36, y), Kk.Ink.toArgb()) > 8 }
        assertTrue(glow > 10, "ready glow outside the slot ($glow)")
        assertEquals(0, (20 until 100).count { y -> colorDistance(cooling.at(36, y), Kk.Ink.toArgb()) > 8 }, "no glow while cooling")
        assertTrue(cooling.countNear(Kk.Volt) < ready.countNear(Kk.Volt), "cooldown sweep darkens the icon")
        assertTrue(max.countNear(Kk.RLegend, 20) > 80)
        assertEquals(0, none.countNear(Kk.Volt))
        val plus = (0 until 16).sumOf { dy -> (0 until 16).count { dx -> colorDistance(none.at(63 + dx, 53 + dy), Kk.Ink.toArgb()) > 40 } }
        assertTrue(plus > 8, "plus mark ($plus)")
    }

    @Test
    fun tagsStampsChipsToastsAndTooltipsDrawTheirFaces() {
        val tag = image { drawKkTag(measurer, "Volt", Offset(20f, 20f), KkTagVariant.YOU) }
        assertTrue(tag.countNear(Kk.Volt) > 300)
        val stamp = image { drawKkStamp(measurer, "New!", Offset(40f, 40f)) }
        assertTrue(stamp.countNear(Kk.Volt) > 500)
        val outline = image { drawKkStamp(measurer, "Max", Offset(40f, 40f), KkStampVariant.OUTLINE) }
        assertTrue(outline.countNear(Kk.Volt) in 60..900)
        val chip = image { drawKkChip(measurer, Offset(20f, 20f), "1,284", "Matter") }
        assertTrue(chip.countNear(Kk.Ink2, 2) > 1_000 && chip.countNear(Kk.Volt) > 60)
        val warning = image(320) { drawKkToast(measurer, Offset(10f, 20f), "Singularity proximity", tone = KkToastTone.WARNING) }
        assertTrue(warning.countNear(Kk.Hazard) > 3_000)
        var tip = Rect.Zero
        val tooltip = image(400, 300) {
            tip = drawKkTooltip(measurer, Rect(188f, 200f, 212f, 224f), "Mastery climbs during the run and resets with the run build.")
        }
        assertTrue(tip.bottom <= 188f && tip.width == 270f, "above the anchor with a 12 dp gap: $tip")
        assertTrue(tooltip.countNear(Kk.Bone, 2) > 8_000)
        // No room above: flips below the anchor.
        var flipped = Rect.Zero
        image(400, 300) { flipped = drawKkTooltip(measurer, Rect(188f, 8f, 212f, 32f), "Short text") }
        assertTrue(flipped.top >= 44f, "flipped below: $flipped")
        val info = image(60, 60) { drawKkInfoButton(measurer, Rect(18f, 18f, 42f, 42f), active = true) }
        assertTrue(info.countNear(Kk.Volt, 30) > 40)
        assertEquals(0, image(60, 60) { drawKkInfoButton(measurer, Rect(18f, 18f, 42f, 42f)) }.countNear(Kk.Volt, 30))
    }

    @Test
    fun controlsReflectSelection() {
        val tab = image { drawKkTab(measurer, Rect(20f, 20f, 160f, 60f), "Interface", selected = true) }
        assertTrue(tab.countNear(Kk.Bone, 2) > 2_000 && tab.regionCountNear(Kk.Volt, 20, 62, 170, 70) > 100, "bone slab + volt underline")
        val segment = image { drawKkSegment(measurer, Rect(20f, 20f, 120f, 54f), "Normal", selected = true) }
        assertTrue(segment.countNear(Kk.Volt) > 1_500)
        val off = image { drawKkToggle(measurer, Rect(20f, 20f, 82f, 48f), 0f) }
        val on = image { drawKkToggle(measurer, Rect(20f, 20f, 82f, 48f), 1f) }
        assertEquals(0, off.countNear(Kk.Volt))
        assertTrue(on.regionCountNear(Kk.Volt, 50, 20, 84, 48) > 200, "thumb slid right in volt")
        val slider = image { drawKkSlider(Rect(20f, 20f, 220f, 48f), 0.7f, KkRolePalette.Default) }
        assertTrue(slider.regionCountNear(Kk.Volt, 20, 20, 150, 48) > 500)
        assertEquals(0, slider.regionCountNear(Kk.Volt, 170, 20, 240, 48))
        val tile = image { drawKkTile(measurer, Rect(40f, 20f, 150f, 104f), "Square", KkIcon.FORMS_SQUARE, selected = 1f) }
        assertTrue(tile.countNear(Kk.Bone, 2) > 5_000 && tile.countNear(Kk.Volt) > 300)
        val locked = image { drawKkTile(measurer, Rect(40f, 20f, 150f, 104f), "Locked", KkIcon.FORMS_TESSERACT, locked = true) }
        assertEquals(0, locked.countNear(Kk.Volt))
        val row = image { drawKkListRow(measurer, Rect(20f, 20f, 220f, 68f), "Flux Wake", "Trail", selected = 1f) }
        assertEquals(Kk.Bone.toArgb(), row.at(200, 30))
    }

    @Test
    fun fillsTileSeamlesslyAndFadeRadially() {
        val hatch = image { drawKkHatch(Rect(0f, 0f, 240f, 120f), Color.White) }
        val lit = hatch.argb().count { colorDistance(it, Kk.Ink.toArgb()) > 60 }
        assertTrue(lit in 3_000..14_000, "hatch coverage $lit")
        // Seamless: the diagonal pattern repeats every tile along a row.
        val tile = KkFillKind.HATCH.let { (it.periodDp * kotlin.math.sqrt(2f)).let(Math::round) }
        val row = (0 until 200).map { hatch.at(it, 60) }
        assertEquals(row.subList(0, 100), row.subList(tile, tile + 100))
        val halftone = image { drawKkHalftone(Rect(0f, 0f, 240f, 120f), Color.White) }
        val dots = halftone.countNear(Color.White, 60)
        assertTrue(dots in 200..5_000, "halftone dot pixels $dots")
        val faded = image { drawKkRadialFade(Rect(0f, 0f, 240f, 120f)) { drawRect(Color.White) } }
        assertEquals(Color.White.toArgb(), faded.at(120, 60))
        assertEquals(Kk.Ink.toArgb(), faded.at(2, 2))
        val grid = image { drawKkGrid(Rect(0f, 0f, 240f, 120f), Color.White) }
        assertTrue(maxOf(grid.at(39, 17), grid.at(40, 17)).let { colorDistance(it, Kk.Ink.toArgb()) > 100 }, "40 dp grid line")
        assertEquals(Kk.Ink.toArgb(), grid.at(20, 17))
    }

    @Test
    fun shutterCoversTheScreenAtTheSwapMomentOnly() {
        val covered = image(320, 180) { drawRect(Color.White); drawKkShutter(KkShutter.SWAP_MS.toFloat(), KkRolePalette.Default) }
        assertEquals(0, covered.countNear(Color.White, 2), "ink slab covers the whole screen")
        val before = image(320, 180) { drawRect(Color.White); drawKkShutter(0f, KkRolePalette.Default) }
        assertTrue(before.argb().all { it == Color.White.toArgb() })
        val after = image(320, 180) { drawRect(Color.White); drawKkShutter(KkShutter.TOTAL_MS.toFloat(), KkRolePalette.Default) }
        assertTrue(after.argb().all { it == Color.White.toArgb() })
    }

    @Test
    fun legacyArrowMarkIsAPointSymmetricBar() {
        val mark = image(120, 60) { drawKineticArrow(Offset(60f, 30f), 60f, Color.White) }
        val pixels = mark.argb()
        var mismatched = 0
        for (y in 0 until 60) for (x in 0 until 120) {
            val mirrored = pixels[(59 - y) * 120 + (119 - x)]
            if (colorDistance(pixels[y * 120 + x], mirrored) > 90) mismatched++
        }
        assertTrue(mismatched < 30, "no arrowhead: the mark is symmetric under 180 deg rotation ($mismatched)")
        assertTrue(pixels.count { it != Kk.Ink.toArgb() } > 200)
        assertFalse(NavigationText.entries.any { entry ->
            listOf(entry.english, entry.russian).any { text -> text.any { it in "·‹›→" } || "Esc" in text }
        })
    }
}
