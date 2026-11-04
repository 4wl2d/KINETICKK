// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.protocol.DungeonMomentKind
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The FX reducer's dungeon moments: one look per cue living its kind's time, aged by the effect steps, at most
 * [InteractionFxLimits.MAX_DUNGEON_MOMENTS] (the oldest leaves), moved with its focus by a rebase, ended by a clear,
 * and published only after a change; the shattering structures beside them keep their own lives.
 */
class DungeonMomentFxTest {
    private fun cue(kind: DungeonMomentKind, x: Float = 100f) =
        VisualFxCue.DungeonMoment(kind, StructureTemplateId.FOUNDRY, x, -20f, 250f, 400f, focusX = x + 30f, focusY = 10f)

    @Test
    fun aMomentLivesItsKindsTimeAndKeepsTheBoundAtMost() {
        val reducer = InteractionFxReducer(seed = 21)
        reducer.apply(listOf(cue(DungeonMomentKind.GUARDIAN)))
        val started = reducer.snapshot().dungeonMoments.single()
        assertEquals(DungeonMomentKind.GUARDIAN, started.kind)
        assertEquals(StructureTemplateId.FOUNDRY, started.template)
        assertEquals(listOf(100f, -20f, 250f, 400f, 130f, 10f),
            listOf(started.x, started.y, started.halfWidth, started.halfHeight, started.focusX, started.focusY))
        assertEquals(dungeonMomentSeconds(DungeonMomentKind.GUARDIAN), started.maxLife)
        assertEquals(started.maxLife, started.life)

        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(1f)))
        assertEquals(started.maxLife - 1f, reducer.snapshot().dungeonMoments.single().life, 1e-5f)
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(started.maxLife)))
        assertTrue(reducer.snapshot().dungeonMoments.isEmpty(), "gone once its life runs out")

        repeat(InteractionFxLimits.MAX_DUNGEON_MOMENTS + 2) { reducer.apply(listOf(cue(DungeonMomentKind.WAVE, it.toFloat()))) }
        val held = reducer.snapshot().dungeonMoments
        assertEquals(InteractionFxLimits.MAX_DUNGEON_MOMENTS, held.size)
        assertEquals((2..InteractionFxLimits.MAX_DUNGEON_MOMENTS + 1).map { it.toFloat() }, held.map { it.x }, "the oldest leave first")
        assertTrue(DungeonMomentKind.entries.all { dungeonMomentSeconds(it) in 0.5f..2.5f })
    }

    @Test
    fun aRebaseMovesTheMomentAndItsFocusAndAClearEndsMomentsAndShardsTogether() {
        val reducer = InteractionFxReducer(seed = 22)
        reducer.apply(listOf(cue(DungeonMomentKind.ROOM_CLEARED, 500f), VisualFxCue.StructureDisintegrated(StructureTemplateId.VAULT, 0, 0f, 0f)))
        reducer.apply(listOf(VisualFxCue.WorldRebased(200f, -40f)))
        val moved = reducer.snapshot().dungeonMoments.single()
        assertEquals(listOf(300f, 20f, 330f, 50f), listOf(moved.x, moved.y, moved.focusX, moved.focusY))
        reducer.apply(listOf(VisualFxCue.ClearAll))
        val cleared = reducer.snapshot()
        assertTrue(cleared.dungeonMoments.isEmpty() && cleared.disintegrations.isEmpty())
    }

    @Test
    fun aQuietFrameReusesThePublishedMoments() {
        val reducer = InteractionFxReducer(seed = 23)
        reducer.apply(listOf(cue(DungeonMomentKind.SEALED)))
        val first = reducer.snapshot().dungeonMoments
        assertSame(first, reducer.snapshot().dungeonMoments)
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(5f)))
        val ended = reducer.snapshot()
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(1f / 120f)))
        assertSame(ended, reducer.snapshot(), "an ended moment leaves the projection alone")
    }
}
