// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The FX reducer's structure disintegrations: one look per cue, aged by the effect steps and gone after 1.6 s, at most
 * one window's worth (the oldest leaves), moved by a rebase, ended by a clear, and published only after a change.
 */
class StructureDisintegrationFxTest {
    private fun cue(x: Float, template: StructureTemplateId = StructureTemplateId.VAULT) =
        VisualFxCue.StructureDisintegrated(template, rotation = 1, x = x, y = -20f)

    @Test
    fun aDisintegrationAgesExpiresAtItsLifeAndKeepsOneWindowAtMost() {
        val reducer = InteractionFxReducer(seed = 11)
        reducer.apply(listOf(cue(100f)))
        val started = reducer.snapshot().disintegrations.single()
        assertEquals(StructureTemplateId.VAULT, started.template)
        assertEquals(1, started.rotation)
        assertEquals(100f to -20f, started.x to started.y)
        assertEquals(InteractionFxLimits.DISINTEGRATION_SECONDS, started.life)
        assertEquals(1.6f, started.maxLife)

        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(1f)))
        assertEquals(0.6f, reducer.snapshot().disintegrations.single().life, 1e-5f)
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(0.6f)))
        assertTrue(reducer.snapshot().disintegrations.isEmpty(), "gone once its life runs out")

        repeat(ContentBounds.MAX_STRUCTURES_IN_WINDOW + 2) { reducer.apply(listOf(cue(it.toFloat()))) }
        val window = reducer.snapshot().disintegrations
        assertEquals(InteractionFxLimits.MAX_DISINTEGRATIONS, window.size)
        assertEquals((2..ContentBounds.MAX_STRUCTURES_IN_WINDOW + 1).map { it.toFloat() }, window.map { it.x }, "the oldest leave first")
        assertEquals(window.size, window.map { it.seed }.toSet().size, "each shatters its own way")
    }

    @Test
    fun aRebaseMovesTheShardsAndAClearEndsThem() {
        val reducer = InteractionFxReducer(seed = 12)
        reducer.apply(listOf(cue(500f)))
        reducer.apply(listOf(VisualFxCue.WorldRebased(200f, -40f)))
        val moved = reducer.snapshot().disintegrations.single()
        assertEquals(300f to 20f, moved.x to moved.y)
        reducer.apply(listOf(VisualFxCue.ClearAll))
        assertTrue(reducer.snapshot().disintegrations.isEmpty())
    }

    @Test
    fun theSnapshotReusesTheListWhileNothingIsAlive() {
        val reducer = InteractionFxReducer(seed = 13)
        val quiet = reducer.snapshot()
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(1f / 120f), VisualFxCue.WorldRebased(10f, 10f)))
        assertSame(quiet, reducer.snapshot(), "nothing alive, nothing published")
        reducer.apply(listOf(cue(0f)))
        val alive = reducer.snapshot()
        assertNotSame(quiet.disintegrations, alive.disintegrations)
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(2f)))
        val ended = reducer.snapshot()
        assertTrue(ended.disintegrations.isEmpty())
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(1f / 120f)))
        assertSame(ended, reducer.snapshot(), "an ended look leaves the projection alone")
        assertSame(ended.disintegrations, reducer.snapshot().disintegrations)
    }
}
