// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.CoreShape
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkHomePalette
import kinetickk.foundation.design.KkTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Home accents name what each item opens; the Codex sweeps through the element hues. */
class HomeAccentTest {
    @Test
    fun everyMenuItemWearsItsSemanticPalette() {
        assertEquals(KkHomePalette.START.accent, menuAccent(HomeLayoutTarget.START))
        // The Workshop wears the deeper volt of permanent upgrades paid in Matter.
        assertEquals(KkHomePalette.LAB.accent, menuAccent(HomeLayoutTarget.WORKSHOP))
        assertTrue(KkHomePalette.LAB.accent != menuAccent(HomeLayoutTarget.START))
        assertEquals(KkHomePalette.SETTINGS.accent, menuAccent(HomeLayoutTarget.SETTINGS))
        // Gold means Rebirth; the Rebirth screen itself keeps the per-tier themes.
        assertEquals(Kk.Gold, menuAccent(HomeLayoutTarget.REBIRTH))
        // The Codex accent is never a fixed hue: it is the element sweep.
        assertTrue(HomeCodexSweep.toSet().containsAll(Kk.Elements))
    }

    @Test
    fun theCodexSweepHoldsEachElementHueThenEasesIntoTheNextAndWraps() {
        val hues = Kk.Elements
        hues.forEachIndexed { index, hue ->
            // The start of each share and its hold show the element's own hue.
            assertEquals(hue, homeCodexSweepColor(index.toFloat() / hues.size))
            assertEquals(hue, homeCodexSweepColor((index + 0.35f) / hues.size))
        }
        // The last share eases from Entropy back to Thermal.
        assertEquals(hues.first(), homeCodexSweepColor(0.9999f).let { if (it.distanceTo(hues.first()) < 0.02f) hues.first() else it })
        assertEquals(HOME_CODEX_SWEEP_STEPS, HomeCodexSweep.size)
        assertEquals(0, homeCodexSweepStep(0f))
        assertEquals(HOME_CODEX_SWEEP_STEPS - 1, homeCodexSweepStep(HOME_CODEX_SWEEP_SECONDS - 0.001f))
        assertEquals(0, homeCodexSweepStep(HOME_CODEX_SWEEP_SECONDS))
    }

    @Test
    fun theSettledCodexAccentFollowsTheSweepAndVisitsEveryElement() {
        val motion = HomeMenuMotion()
        // Select the Codex and let the palette shift settle.
        repeat(KkTime.PaletteShift / 16 + 2) { motion.advance(HomeLayoutTarget.CODEX, 1f / 60f) }
        val seen = mutableSetOf<Color>()
        repeat((HOME_CODEX_SWEEP_SECONDS * 60f).toInt()) {
            motion.advance(HomeLayoutTarget.CODEX, 1f / 60f)
            assertTrue(motion.accent in HomeCodexSweep, "a settled accent is one of the cached sweep steps")
            seen += motion.accent
        }
        Kk.Elements.forEach { hue -> assertTrue(hue in seen, "the sweep shows $hue") }
        // Leaving the Codex eases back into a fixed palette.
        repeat(30) { motion.advance(HomeLayoutTarget.SETTINGS, 1f / 60f) }
        assertEquals(KkHomePalette.SETTINGS.accent, motion.accent)
    }

    @Test
    fun theSweepGlidesWithoutVisibleJumpsBetweenSteps() {
        // At 30 steps a second no step moves any channel by more than a small fraction, even across
        // the widest hue change (Thermal to Cryo, blue 0.12 to 1).
        val steps = HomeCodexSweep + HomeCodexSweep.first()
        val largest = steps.zipWithNext { a, b -> a.distanceTo(b) }.max()
        assertTrue(largest <= 0.06f, "largest per-step change $largest")
        assertTrue(HOME_CODEX_SWEEP_STEPS / HOME_CODEX_SWEEP_SECONDS >= 30f, "at least 30 accents a second")
    }

    @Test
    fun aSecondSweepReusesTheTrailBrushOfEveryAccentItAlreadyBuilt() {
        val motion = HomeMenuMotion()
        val center = androidx.compose.ui.geometry.Offset(320f, 240f)
        repeat(KkTime.PaletteShift / 16 + 2) { motion.advance(HomeLayoutTarget.CODEX, 1f / 60f) }
        val frames = (HOME_CODEX_SWEEP_SECONDS * 60f).toInt()
        val built = mutableMapOf<Color, androidx.compose.ui.graphics.Brush>()
        repeat(frames) {
            motion.advance(HomeLayoutTarget.CODEX, 1f / 60f)
            val brush = motion.trailBrush(center)
            val known = built.getOrPut(motion.accent) { brush }
            assertTrue(known === brush, "one accent, one brush (the holds share theirs)")
        }
        assertEquals(homeCodexSweepSlots(HomeCodexSweep).distinct().size, built.size, "every distinct accent of the sweep was drawn once")
        // The next loop draws with the very brushes of the first: a steady sweep builds nothing new.
        repeat(frames) {
            motion.advance(HomeLayoutTarget.CODEX, 1f / 60f)
            assertTrue(built[motion.accent] === motion.trailBrush(center), "a steady sweep built a new brush for ${motion.accent}")
        }
    }

    private fun Color.distanceTo(other: Color): Float =
        maxOf(kotlin.math.abs(red - other.red), kotlin.math.abs(green - other.green), kotlin.math.abs(blue - other.blue))
}
