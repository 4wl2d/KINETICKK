// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingOutputBufferIsolationTest {
    @Test
    fun relicDiscoveryBelongsToTheAcceptedAcquisitionAndSurvivesReplacement() {
        val source = MutableGameState(canonicalGameplayContent, seed = 9001)
        val retained = source.toRenderModel()
        val candidate = source.copyForReduction()
        val first = kinetickk.ball.content.api.RelicId.KINETIC_FLYWHEEL
        val second = kinetickk.ball.content.api.RelicId.GHOST_VECTOR
        candidate.acquireRelic(first)
        assertTrue(candidate.toRenderModel().isRelicDiscovered(first))
        assertFalse(retained.isRelicDiscovered(first))
        assertFalse(source.toRenderModel().isRelicDiscovered(first))
        assertNull(source.takeProgressUpdate())
        assertEquals(setOf(first), candidate.takeProgressUpdate()?.discoveredRelicIds?.toSet())
        assertNull(candidate.takeProgressUpdate())
        candidate.acquireRelic(first)
        assertNull(candidate.takeProgressUpdate())
        candidate.replaceRelic(0, second)
        assertEquals(setOf(second), candidate.takeProgressUpdate()?.discoveredRelicIds?.toSet())
        assertTrue(candidate.toRenderModel().isRelicDiscovered(first))
        assertTrue(candidate.toRenderModel().isRelicDiscovered(second))
    }

    @Test
    fun emptyReductionCopyKeepsEveryOutputStorageUnmaterialized() {
        val source = MutableGameState(canonicalGameplayContent, seed = 10_101)
        val candidate = source.copyForReduction()

        assertNull(source.pendingDiscoveredItemIdStorage)
        assertNull(source.soundCueStorage)
        assertNull(source.visualFxCueStorage)
        assertNull(candidate.pendingDiscoveredItemIdStorage)
        assertNull(candidate.soundCueStorage)
        assertNull(candidate.visualFxCueStorage)

        // Read-only facade operations must not defeat laziness.
        assertTrue(candidate.pendingDiscoveredItemIds.isEmpty())
        assertTrue(candidate.soundCues.isEmpty())
        assertTrue(candidate.visualFxCues.isEmpty())
        assertNull(candidate.pendingDiscoveredItemIdStorage)
        assertNull(candidate.soundCueStorage)
        assertNull(candidate.visualFxCueStorage)
    }

    @Test
    fun drainingOneForkAndRecordingLaterCannotMutateSourceOrSiblingOutputs() {
        val source = MutableGameState(canonicalGameplayContent, seed = 10_102).apply {
            pendingBankedMatter = 7L
            pendingClearedRebirthLevel = 1
            pendingDiscoveredItemIds += 0
            emitSound(GameplayAudioCue.UI_CLICK)
            emitVisualFx(VisualFxCue.EffectsAdvanced(0.125f))
        }
        val drainedBranch = source.copyForReduction()
        val retainedSibling = source.copyForReduction()

        assertEquals(
            Triple(7L, setOf(0), 1),
            drainedBranch.takeProgressUpdate()?.let { update ->
                Triple(update.bankedMatter, update.discoveredItemIds.toSet(), update.clearedRebirthLevel)
            },
        )
        assertEquals(listOf(GameplayAudioCue.UI_CLICK), drainedBranch.takeSoundCues().toList())
        assertEquals(
            listOf(VisualFxCue.EffectsAdvanced(0.125f)),
            drainedBranch.takeVisualFxCues().toList(),
        )
        assertFalse(drainedBranch.pendingDiscoveredItemIds.isNotEmpty())
        assertFalse(drainedBranch.soundCues.isNotEmpty())
        assertTrue(drainedBranch.visualFxCues.isEmpty())

        // Reuse the drained branch and mutate the retained source after both forks already exist.
        drainedBranch.pendingBankedMatter = 11L
        drainedBranch.pendingDiscoveredItemIds += 1
        drainedBranch.emitSound(GameplayAudioCue.DASH)
        drainedBranch.emitVisualFx(VisualFxCue.ClearAll)
        source.pendingBankedMatter = 13L
        source.pendingDiscoveredItemIds += 2
        source.emitSound(GameplayAudioCue.IMPACT)
        source.emitVisualFx(VisualFxCue.EffectsAdvanced(0.25f))

        assertEquals(
            Triple(7L, setOf(0), 1),
            retainedSibling.takeProgressUpdate()?.let { update ->
                Triple(update.bankedMatter, update.discoveredItemIds.toSet(), update.clearedRebirthLevel)
            },
        )
        assertEquals(listOf(GameplayAudioCue.UI_CLICK), retainedSibling.takeSoundCues().toList())
        assertEquals(
            listOf(VisualFxCue.EffectsAdvanced(0.125f)),
            retainedSibling.takeVisualFxCues().toList(),
        )

        assertEquals(
            Triple(11L, setOf(1), null),
            drainedBranch.takeProgressUpdate()?.let { update ->
                Triple(update.bankedMatter, update.discoveredItemIds.toSet(), update.clearedRebirthLevel)
            },
        )
        assertEquals(listOf(GameplayAudioCue.DASH), drainedBranch.takeSoundCues().toList())
        assertEquals(listOf(VisualFxCue.ClearAll), drainedBranch.takeVisualFxCues().toList())

        assertEquals(
            Triple(13L, setOf(0, 2), 1),
            source.takeProgressUpdate()?.let { update ->
                Triple(update.bankedMatter, update.discoveredItemIds.toSet(), update.clearedRebirthLevel)
            },
        )
        assertEquals(
            listOf(GameplayAudioCue.UI_CLICK, GameplayAudioCue.IMPACT),
            source.takeSoundCues().toList(),
        )
        assertEquals(
            listOf(
                VisualFxCue.EffectsAdvanced(0.125f),
                VisualFxCue.EffectsAdvanced(0.25f),
            ),
            source.takeVisualFxCues().toList(),
        )
    }
}
