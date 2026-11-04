// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.foundation.common.localization.AppLanguage
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
        // Six tiers under the content cap 42: 41 is the last T5, 42 the APEX (ready).
        val tiers = listOf(7, 14, 24, 34, 41, 42).map { level ->
            image(260, 200) {
                drawKkLevelBadge(measurer, Offset(90f, 80f), level, time = 1.5f, maxLevel = CAP, apex = KkApexState.READY)
            }
        }
        assertEquals(6, tiers.map { it.argb().toList() }.toSet().size)
        assertTrue(tiers[0].regionCountNear(Kk.Volt, 0, 0, 260, 200) == 0, "t1 has no volt")
        assertTrue(tiers[1].countNear(Kk.Volt) > 50, "t2 volt echo")
        assertTrue(tiers[2].countNear(Kk.Volt) > 800, "t3 volt face")
        assertTrue(tiers[3].countNear(Kk.Volt) > 200 && tiers[3].countNear(Kk.Ink2, 3) > 300, "t4 ink face, volt text")
        assertTrue(tiers[4].countNear(Kk.RLegend, 40) > 400, "t5 gold foil")
        // Rays extend far outside the t5 badge.
        assertTrue(tiers[4].regionCountNear(Kk.RLegend, 0, 0, 80, 200, 90) > 20 || tiers[4].regionCountNear(Kk.RLegend, 180, 0, 260, 200, 90) > 20)
        // The APEX: an ink face inside a gold frame, the volt echo, gold rays and foil text.
        val face = badgeFace(42)
        val apex = tiers[5]
        assertTrue(apex.regionCountNear(Kk.Ink, face.left.toInt() + 14, face.top.toInt() + 5, face.right.toInt() - 14, face.bottom.toInt() - 5, 6) > 200,
            "t6 ink face")
        assertTrue(apex.countNear(Kk.RLegend, 40) > 200, "t6 gold frame and foil text")
        assertTrue(apex.countNear(Kk.Volt, 20) > 300, "t6 volt echo")
        // The cap itself, not the decade, makes the APEX: 42 without a cap is a plain T5.
        val uncapped = image(260, 200) { drawKkLevelBadge(measurer, Offset(90f, 80f), 42, time = 1.5f) }
        assertTrue(uncapped.countNear(Kk.Volt, 20) < 50, "42 without a cap draws no APEX echo")
    }

    @Test
    fun levelBadgeApexStatesDiffer() {
        val states = listOf(KkApexState.COOLING, KkApexState.READY, KkApexState.ACTIVE).map { state ->
            state to image(260, 200) {
                drawKkLevelBadge(measurer, Offset(90f, 80f), CAP, time = 1.5f, maxLevel = CAP, apex = state, apexActive = 0.6f)
            }.argb()
        }
        states.forEachIndexed { index, (a, first) ->
            states.drop(index + 1).forEach { (b, second) ->
                val differing = first.indices.count { first[it] != second[it] }
                assertTrue(differing >= 200, "$a vs $b differ in $differing px")
            }
        }
        // NONE (no Apex runtime yet) shows the recharging look.
        val none = image(260, 200) { drawKkLevelBadge(measurer, Offset(90f, 80f), CAP, time = 1.5f, maxLevel = CAP) }.argb()
        assertContentEquals(states[0].second, none)
    }

    @Test
    fun levelBadgeT6DiffersFromT4OutsideTheFace() {
        fun outside(level: Int): Int {
            val face = badgeFace(level)
            val image = image(260, 200) {
                drawKkLevelBadge(measurer, Offset(90f, 80f), level, time = 1.5f, maxLevel = CAP, apex = KkApexState.READY)
            }.argb()
            // Outside the face and outside the T4 echo (the face moved 5 dp down and right), with 1 px of anti-aliasing.
            var count = 0
            for (y in 0 until 200) for (x in 0 until 260) {
                val inFace = x >= face.left - 1f && x <= face.right + 1f && y >= face.top - 1f && y <= face.bottom + 1f
                val inEcho = x >= face.left + 4f && x <= face.right + 6f && y >= face.top + 4f && y <= face.bottom + 6f
                if (!inFace && !inEcho && colorDistance(image[y * 260 + x], Kk.Ink.toArgb()) > 24) count++
            }
            return count
        }
        assertEquals(0, outside(34), "t4 paints nothing beyond its echo")
        val apex = outside(42)
        assertTrue(apex >= 300, "t6 rays, echoes and glow reach past the face ($apex px)")
    }

    @Test
    fun levelBadgeFaceMetricsMatchAcrossTiers() {
        assertEquals(kkLevelBadgeSize(measurer, 41, 1f), kkLevelBadgeSize(measurer, 42, 1f))
        val t5 = badgeFace(41)
        listOf(KkApexState.NONE, KkApexState.COOLING, KkApexState.READY, KkApexState.ACTIVE).forEach { state ->
            assertEquals(t5.size, badgeFace(42, state).size, "$state")
        }
    }

    @Test
    fun levelBadgeApexFoilTextScrollsUnlessCooling() {
        // Inside the ink face only the foil text changes over time (the rays and echoes sit under it).
        fun inner(state: KkApexState, time: Float): List<Int> {
            val face = badgeFace(CAP, state)
            val image = image(260, 200) {
                drawKkLevelBadge(measurer, Offset(90f, 80f), CAP, time = time, maxLevel = CAP, apex = state, apexActive = 0.6f)
            }
            val pixels = mutableListOf<Int>()
            for (y in face.top.toInt() + 4 until face.bottom.toInt() - 4) for (x in face.left.toInt() + 14 until face.right.toInt() - 14) {
                pixels += image.at(x, y)
            }
            return pixels
        }
        fun moved(state: KkApexState): Int {
            val a = inner(state, 0.1f)
            val b = inner(state, 1.3f)
            return a.indices.count { a[it] != b[it] }
        }
        assertEquals(0, moved(KkApexState.COOLING), "cooling foil text stands still")
        assertTrue(moved(KkApexState.ACTIVE) > 40, "active foil text scrolls (${moved(KkApexState.ACTIVE)} px)")
    }

    @Test
    fun levelBadgeGoldFollowsTheColorVisionPreset() {
        val protan = kkTestMeasurer(KkRolePalette.Protan)
        listOf(41, 42).forEach { level ->
            val image = image(260, 200) {
                drawKkLevelBadge(protan, Offset(90f, 80f), level, time = 1.5f, maxLevel = CAP, apex = KkApexState.READY)
            }
            assertTrue(image.countNear(KkGold.Deep.base, 24) > 100, "level $level: Protan draws the deep gold")
            assertEquals(0, image.countNear(Kk.RLegend, 12), "level $level: no bright gold under Protan")
        }
    }

    private fun badgeFace(level: Int, state: KkApexState = KkApexState.READY): Rect {
        var face = Rect.Zero
        image(260, 200) { face = drawKkLevelBadge(measurer, Offset(90f, 80f), level, time = 1.5f, maxLevel = CAP, apex = state) }
        return face
    }

    @Test
    fun rarityReadsThroughEffects() {
        fun card(rank: Int, time: Float = 1.9f, selected: Float = 0f) = image(320, 360) {
            drawKkCard(measurer, Rect(40f, 50f, 260f, 300f), rank, time, selected, "Rarity", "New", 32f)
        }
        val cards = (1..5).map { card(it) }
        assertEquals(5, cards.map { it.argb().toList() }.toSet().size)
        // Echo offsets: rare 4 (chrome), epic 6 (spectrum), legendary 8 (striped gold); common/uncommon
        // none. Sample just outside the face's slanted right edge (x = 260 - 22 * (y - 50) / 250).
        fun echoRows(image: androidx.compose.ui.graphics.ImageBitmap, matches: (Color) -> Boolean) = (120 until 290).count { y ->
            val edge = 260f - 22f * (y - 50f) / 250f
            matches(Color(image.at((edge + 2.5f).toInt(), y)))
        }
        val lit = { color: Color -> colorDistance(color.toArgb(), Kk.Ink.toArgb()) > 12 }
        val chrome = { color: Color -> kkLuminance(color) > 0.2f && maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue) < 0.1f }
        assertEquals(0, echoRows(cards[0], lit), "common has no echo")
        assertEquals(0, echoRows(cards[1], lit), "uncommon has no echo")
        assertTrue(echoRows(cards[2], chrome) > 120, "rare chrome echo")
        assertTrue(echoRows(cards[4]) { colorDistance(it.toArgb(), Kk.RLegend.toArgb()) <= 40 || colorDistance(it.toArgb(), 0xFFFFE59A.toInt()) <= 40 } > 120,
            "legendary echo")
        // Legendary rays reach far outside the card, past the halos and echoes: below legendary that far
        // field stays ink, and a legendary without its rays differs from one with them there.
        fun far(image: androidx.compose.ui.graphics.ImageBitmap): List<Int> {
            val argb = image.argb()
            return (0 until 360).flatMap { y -> (0 until 320).filter { x -> x < 26 || x > 276 || y < 36 || y > 316 }.map { x -> argb[y * 320 + x] } }
        }
        val ink = Kk.Ink.toArgb()
        for (rank in 1..4) assertTrue(far(cards[rank - 1]).all { colorDistance(it, ink) <= 4 }, "no rays below legendary (rank $rank)")
        val rayless = image(320, 360) {
            drawKkCard(measurer, Rect(40f, 50f, 260f, 300f), 5, 1.9f, 0f, "Rarity", "New", 32f,
                style = KkCardStyle(KkCardMaterial.GOLD, Kk.RLegend, raysAlpha = 0f))
        }
        val rays = far(cards[4]).zip(far(rayless)).count { (a, b) -> colorDistance(a, b) > 3 }
        assertTrue(rays > 300, "legendary rays behind the card ($rays px)")
        // Epic sparks and halftone animate; common is flat and static.
        assertNotEquals(card(4, 0.4f).argb().toList(), card(4, 1.4f).argb().toList())
        assertContentEquals(card(1, 0.4f).argb(), card(1, 1.4f).argb())
    }

    @Test
    fun slotsShowAspectCooldownReadinessAndEmptiness() {
        val relic = image { drawKkRelicSlot(Offset(60f, 60f), Kk.AGravitic, KkIcon.ASPECT_GRAVITIC) }
        assertTrue(relic.countNear(Kk.AGravitic) > 100)
        assertEquals(Kk.Ink2.toArgb(), relic.at(52, 60))
        val empty = image { drawKkRelicSlot(Offset(60f, 60f), Color.Unspecified) }
        assertEquals(0, empty.countNear(Kk.AGravitic))
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
    fun skillSlotsShowLockCooldownReadinessRanksChargesAndKeyCaps() {
        val slot = Rect(60f, 30f, 112f, 82f)
        val ion = measurer.roles.element(2)
        fun draw(
            cooldown: Float = 0f, ready: Boolean = false, rank: Int = 1, charges: Int = -1, maxCharges: Int = 1,
            active: Float = 0f, pending: Float = 0f, locked: Boolean = false, pressed: Float = 0f, refund: Float = 0f,
            shake: Float = 0f, key: androidx.compose.ui.text.TextLayoutResult? = null, rankFill: Float = 1f, empty: Boolean = false,
        ) = image(200, 120) {
            drawKkSkillSlot(measurer, slot, KkIcon.WEAPONS_ARC_COIL, ion, cooldown, ready, rank, charges = charges, maxCharges = maxCharges,
                active = active, activeColor = ion, pending = pending, locked = locked, pressed = pressed, refundFlash = refund,
                refundColor = Kk.Bone, shakeX = shake, keyLayout = key, rankFill = rankFill, empty = empty)
        }
        val ready = draw(ready = true)
        val cooling = draw(cooldown = 0.6f)
        // Ready: the `you` glow outside the face; cooling: none, and the sweep darkens the icon.
        val glow = (20 until 100).count { y -> colorDistance(ready.at(56, y), Kk.Ink.toArgb()) > 8 }
        assertTrue(glow > 10, "ready glow outside the slot ($glow)")
        assertEquals(0, (20 until 100).count { y -> colorDistance(cooling.at(56, y), Kk.Ink.toArgb()) > 8 }, "glow while cooling")
        assertTrue(cooling.countNear(ion, 20) < ready.countNear(ion, 20) - 20, "the sweep does not cover the icon")
        // Locked: the lock in Mute2, no element icon, no pips.
        val locked = draw(locked = true)
        assertEquals(0, locked.countNear(ion, 20))
        assertTrue(locked.countNear(Kk.Mute2, 20) > 20, "lock glyph")
        assertEquals(0, locked.countNear(Kk.Volt, 20))
        // Empty (an unbound slot, usable): a recessed Ink1 face, a dashed Line2 edge and three unlit pips; no lock, no
        // hatch, no glyph, sweep, charges, bar or key cap whatever else is passed.
        val empty = draw(empty = true, ready = true, rank = 2, cooldown = 0.5f, charges = 2, maxCharges = 2, active = 1f,
            key = measureKkKeyCap(measurer, "Z", 10f))
        assertEquals(0, empty.countNear(ion, 20), "an empty slot shows a glyph or a bar")
        assertEquals(0, empty.countNear(Kk.Mute2, 20), "an empty slot shows a lock")
        assertEquals(0, empty.countNear(Kk.Volt, 20), "an empty slot glows, ranks or charges")
        assertEquals(0, empty.regionCountNear(Kk.Bone, 60, 82, 112, 100, 40), "an empty slot shows a key cap")
        assertTrue(empty.regionCountNear(Kk.Ink1, 72, 42, 100, 70, 2) >= 27 * 27, "no recessed face")
        // Along the top edge the Line2 dashes alternate with gaps.
        val edgeRow = (27..32).map { y -> (72 until 108).map { x -> colorDistance(empty.at(x, y), Kk.Line2.toArgb()) <= 24 } }
            .maxBy { row -> row.count { it } }
        val breaks = edgeRow.zipWithNext().count { (a, b) -> a != b }
        assertTrue(edgeRow.count { it } >= 8 && breaks >= 4, "the edge is not dashed: $edgeRow")
        assertTrue(empty.regionCountNear(Kk.Ink4, 60, 30, 100, 40, 4) >= 30, "no unlit pips")
        // A press still answers on an empty slot, fainter than on a held one.
        val pressedEmpty = draw(empty = true, pressed = 1f).regionCountNear(Kk.Ink1, 72, 42, 100, 70, 2)
        assertTrue(pressedEmpty < 50, "the press does not light the empty face")
        // Rank pips: one volt pip at rank I, three at rank II... gold at rank III (no volt pips left).
        val pips = { image: androidx.compose.ui.graphics.ImageBitmap, color: Color -> image.regionCountNear(color, 60, 30, 100, 40, 20) }
        val rankOne = pips(draw(rank = 1), Kk.Volt)
        val rankTwo = pips(draw(rank = 2), Kk.Volt)
        assertTrue(rankOne > 6 && rankTwo > rankOne + 6, "pips $rankOne → $rankTwo")
        val top = draw(rank = 3)
        assertEquals(0, pips(top, Kk.Volt))
        assertTrue(pips(top, measurer.roles.gold.base) > rankTwo, "rank III pips are gold")
        // The newest pip fills: half filled shows fewer volt pixels than a full one.
        assertTrue(pips(draw(rank = 2, rankFill = 0.5f), Kk.Volt) in (rankOne + 1) until rankTwo)
        // Charges: two squares top-right only when the slot holds two charges.
        val chargeRegion = { image: androidx.compose.ui.graphics.ImageBitmap -> image.regionCountNear(Kk.Volt, 92, 30, 112, 40, 20) }
        assertEquals(0, chargeRegion(draw(rank = 0)))
        val twoCharges = chargeRegion(draw(rank = 0, charges = 2, maxCharges = 2))
        val oneCharge = chargeRegion(draw(rank = 0, charges = 1, maxCharges = 2))
        assertTrue(twoCharges > oneCharge && oneCharge > 4, "charges $oneCharge / $twoCharges")
        // Active share in the element colour, pending in Bone, near the bottom.
        val bar = { image: androidx.compose.ui.graphics.ImageBitmap, color: Color -> image.regionCountNear(color, 60, 70, 112, 78, 20) }
        assertTrue(bar(draw(active = 1f, rank = 0), ion) > bar(draw(rank = 0), ion) + 20)
        assertTrue(bar(draw(pending = 1f, rank = 0), Kk.Bone) > 20)
        // Press flash: a Bone face; refund edge: Bone pixels inside a cooling face.
        assertTrue(draw(pressed = 1f).countNear(Kk.Bone, 10) > 1_500)
        assertTrue(draw(cooldown = 0.5f, refund = 1f).regionCountNear(Kk.Bone, 60, 30, 112, 82, 30) > 20)
        // Shake moves the whole slot.
        assertTrue(draw(shake = 3f).argb().zip(draw().argb()).count { (a, b) -> a != b } > 100)
        // Key cap: an Ink3 plate below the slot with the label.
        val key = measureKkKeyCap(measurer, "Z", 10f)
        val capped = draw(key = key)
        assertTrue(capped.regionCountNear(Kk.Ink3, 70, 85, 102, 97, 4) > 30, "no key plate")
        assertTrue(capped.regionCountNear(Kk.Bone, 70, 85, 102, 97, 40) > 4, "no key label")
        assertEquals(0, draw().regionCountNear(Kk.Bone, 70, 85, 102, 97, 40))
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
        // Over a bone slab (Home's selected row) the slip keeps an ink edge, so the two never read as one shape.
        var over = Rect.Zero
        val onBone = kkRender(400, 300, background = Kk.Bone) { over = drawKkTooltip(measurer, Rect(188f, 8f, 212f, 32f), "Short text") }
        val left = over.left.toInt()
        val rows = (over.bottom - over.top).toInt() - 8
        assertTrue(onBone.regionCountNear(Kk.Ink, left - 1, over.top.toInt() + 4, left + 1, over.bottom.toInt() - 4, 40) >= rows,
            "the slip's left edge is ink over bone: $over")
        assertTrue(onBone.regionCountNear(Kk.Ink, left + 2, over.top.toInt() + 4, left + 6, over.top.toInt() + 8, 40) == 0,
            "the edge stays a thin line: $over")
        val info = image(60, 60) { drawKkInfoButton(measurer, Rect(18f, 18f, 42f, 42f), active = true) }
        assertTrue(info.countNear(Kk.Volt, 30) > 40)
        assertEquals(0, image(60, 60) { drawKkInfoButton(measurer, Rect(18f, 18f, 42f, 42f)) }.countNear(Kk.Volt, 30))
    }

    /**
     * A narrow card's band has no trailing label: its leading label runs whole to where that label would end (the
     * Russian Derelict kind on a 195 dp phone card), and a label too long even for that shrinks rather than being cut.
     */
    @Test
    fun aBandLabelWithoutATrailingLabelUsesTheBandsRoomBeforeShrinking() {
        val russian = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f, AppLanguage.Russian,
            KkTestFonts.typography)
        val band = 195f - 18f
        lateinit var derelict: TextLayoutResult
        lateinit var beside: TextLayoutResult
        lateinit var long: TextLayoutResult
        image(240, 60) {
            derelict = kkCardBandStartLabel(russian, "Заброшенный остов", Kk.Bone, band, hasEnd = false)
            beside = kkCardBandStartLabel(russian, "Заброшенный остов", Kk.Bone, band, hasEnd = true)
            long = kkCardBandStartLabel(russian, "Заброшенный остов далеко за краем", Kk.Bone, band, hasEnd = false)
        }
        fun whole(layout: TextLayoutResult) = !layout.didOverflowWidth && layout.getLineEnd(0, visibleEnd = true) == layout.layoutInput.text.length &&
            !layout.layoutInput.text.text.contains('…')
        assertTrue(whole(derelict) && derelict.size.width <= band - 36f, "the Derelict kind fits whole: ${derelict.size.width} px")
        assertEquals(12f, derelict.layoutInput.style.fontSize.value, 0.01f, "at its own size")
        assertTrue(beside.size.width <= band * 0.6f + 0.5f, "beside a trailing label it keeps to 0.6 of the band")
        assertTrue(long.layoutInput.style.fontSize.value < 12f && long.size.width <= band - 36f + 0.5f, "a longer label shrinks to the room")
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

    private companion object {
        /** The content's run level cap. */
        const val CAP = 42
    }
}
