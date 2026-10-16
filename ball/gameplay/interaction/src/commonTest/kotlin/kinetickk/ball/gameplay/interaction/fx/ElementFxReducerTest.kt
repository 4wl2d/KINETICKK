// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Break and reaction flashes, first-discovery stamps, the run's reaction record and toast priority. */
class ElementFxReducerTest {
    @Test
    fun flashesOfOneKindMergeWithinEightyUnitsAndOthersStaySeparate() {
        val reducer = InteractionFxReducer(seed = 3)
        reducer.apply(listOf(
            VisualFxCue.StatusBroken(0f, 0f, StatusElement.THERMAL.ordinal),
            VisualFxCue.StatusBroken(79f, 0f, StatusElement.THERMAL.ordinal),
            VisualFxCue.StatusBroken(0f, 0f, StatusElement.CRYO.ordinal),
            VisualFxCue.ReactionTriggered(0f, 0f, ReactionId.THERMAL_SHOCK.ordinal, 60f, false),
            VisualFxCue.ReactionTriggered(300f, 0f, ReactionId.THERMAL_SHOCK.ordinal, 60f, false),
        ))
        val bursts = reducer.snapshot().elementBursts
        assertEquals(4, bursts.size, "two Ignites 79 u apart read as one flash; Freeze and the far reaction stay apart")
        val ignite = bursts.single { it.primary == StatusElement.THERMAL.ordinal && it.secondary < 0 }
        assertEquals(2, ignite.weight)
        val shock = bursts.filter { it.reaction == ReactionId.THERMAL_SHOCK.ordinal }
        assertEquals(2, shock.size)
        shock.forEach {
            assertEquals(StatusElement.THERMAL.ordinal, it.primary, "a reaction flash carries both elements")
            assertEquals(StatusElement.CRYO.ordinal, it.secondary)
        }
        // 81 u apart they stay two flashes.
        val apart = InteractionFxReducer(seed = 3)
        apart.apply(listOf(VisualFxCue.StatusBroken(0f, 0f, 2), VisualFxCue.StatusBroken(81f, 0f, 2)))
        assertEquals(2, apart.snapshot().elementBursts.size)
    }

    @Test
    fun flashesAcceptTwentyFourAndTrimTheOldestOnTheTwentyFifth() {
        val reducer = InteractionFxReducer(seed = 4)
        reducer.apply((0 until InteractionFxLimits.MAX_ELEMENT_BURSTS).map { VisualFxCue.StatusBroken(it * 200f, 0f, it % 5) })
        val accepted = reducer.snapshot().elementBursts
        assertEquals(InteractionFxLimits.MAX_ELEMENT_BURSTS, accepted.size)
        reducer.apply(listOf(VisualFxCue.StatusBroken(-5_000f, 0f, 1)))
        val trimmed = reducer.snapshot().elementBursts
        assertEquals(InteractionFxLimits.MAX_ELEMENT_BURSTS, trimmed.size)
        assertEquals(accepted.drop(1).map { it.x }, trimmed.dropLast(1).map { it.x })
        assertEquals(-5_000f, trimmed.last().x)
        // Flashes end on their own time.
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(InteractionFxLimits.REACTION_BURST_SECONDS + 0.01f)))
        assertTrue(reducer.snapshot().elementBursts.isEmpty())
    }

    @Test
    fun onlyFirstDiscoveriesStampTheirNameAndTheRunRemembersEveryReaction() {
        val reducer = InteractionFxReducer(seed = 5)
        reducer.apply(listOf(
            VisualFxCue.ReactionTriggered(10f, 20f, ReactionId.FUSION.ordinal, 80f, firstDiscovery = true),
            VisualFxCue.ReactionTriggered(10f, 20f, ReactionId.FEEDBACK.ordinal, 80f, firstDiscovery = false),
        ))
        val snapshot = reducer.snapshot()
        assertEquals(listOf(ReactionId.FUSION.ordinal), snapshot.reactionStamps.map { it.reaction })
        assertEquals(InteractionFxLimits.REACTION_STAMP_SECONDS, snapshot.reactionStamps.single().maxLife)
        assertEquals((1 shl ReactionId.FUSION.ordinal) or (1 shl ReactionId.FEEDBACK.ordinal), snapshot.runReactionMask)
        assertEquals(1 shl ReactionId.FUSION.ordinal, snapshot.runDiscoveryMask)
        // The stamp is world text: it moves with a rebase, and leaves after 900 ms.
        reducer.apply(listOf(VisualFxCue.WorldRebased(10f, 0f)))
        assertEquals(0f, reducer.snapshot().reactionStamps.single().x)
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(0.91f)))
        assertTrue(reducer.snapshot().reactionStamps.isEmpty())
        assertEquals(1 shl ReactionId.FUSION.ordinal, reducer.snapshot().runDiscoveryMask, "the run keeps its record")
        // A new run starts a new record.
        reducer.apply(listOf(VisualFxCue.ClearAll))
        assertEquals(0, reducer.snapshot().runReactionMask)
        assertEquals(0, reducer.snapshot().runDiscoveryMask)
    }

    @Test
    fun discoveriesOutrankBreakNotesWhichOutrankAcquisitions() {
        val reducer = InteractionFxReducer(seed = 6)
        reducer.apply(listOf(
            VisualFxCue.BuildChanged(BuildNoticeTitles.REACTION_DISCOVERED, immutableListOf("+ Thermal Shock")),
            VisualFxCue.BuildChanged("Rime Rounds", immutableListOf("Weapon power +0.04")),
            VisualFxCue.BuildChanged("Igniter", immutableListOf("Impact damage +0.05")),
        ))
        // A first Break evicts the oldest acquisition, never the discovery.
        reducer.apply(listOf(VisualFxCue.StatusBroken(0f, 0f, StatusElement.THERMAL.ordinal)))
        val full = reducer.snapshot().buildNotifications
        assertEquals(listOf("Igniter", BuildNoticeTitles.BREAK, BuildNoticeTitles.REACTION_DISCOVERED), full.map { it.title },
            "toasts keep their rank: acquisitions, then the Break note, then the discovery")
        assertEquals(BuildNoticeKind.DISCOVERY, full.last().kind)
        // A fourth acquisition evicts the oldest acquisition, never the Break note or the discovery.
        reducer.apply(listOf(VisualFxCue.BuildChanged("Cinder Ram", immutableListOf())))
        assertEquals(listOf("Cinder Ram", BuildNoticeTitles.BREAK, BuildNoticeTitles.REACTION_DISCOVERED),
            reducer.snapshot().buildNotifications.map { it.title })
        // Break notes live 3.5 s; discoveries and acquisitions 6 s.
        val breaks = InteractionFxReducer(seed = 7)
        breaks.apply(listOf(VisualFxCue.StatusBroken(0f, 0f, StatusElement.ION.ordinal)))
        val note = breaks.snapshot().buildNotifications.single()
        assertEquals(BuildNoticeKind.BREAK, note.kind)
        assertEquals(3.5f, note.maxLife)
        assertEquals(listOf("+ Overload"), note.details)
        // Only the first Break of an element in a run leaves a note.
        breaks.apply(listOf(VisualFxCue.EffectsAdvanced(2f), VisualFxCue.StatusBroken(0f, 0f, StatusElement.ION.ordinal)))
        assertEquals(1.5f, breaks.snapshot().buildNotifications.single().life, 0.0001f)
    }

    @Test
    fun aFirstBreakTheFullFeedDropsLeavesItsNoteOnTheNextBreak() {
        val reducer = InteractionFxReducer(seed = 11)
        reducer.apply(listOf(
            VisualFxCue.BuildChanged(BuildNoticeTitles.REACTION_DISCOVERED, immutableListOf("+ Fusion")),
            VisualFxCue.BuildChanged(BuildNoticeTitles.PAIRING_DISCOVERED, immutableListOf("+ Black Star")),
            VisualFxCue.BuildChanged(BuildNoticeTitles.INCOMPATIBILITY_DISCOVERED, immutableListOf("\u2212 Shock Sling")),
            VisualFxCue.StatusBroken(0f, 0f, StatusElement.ION.ordinal),
        ))
        assertTrue(reducer.snapshot().buildNotifications.none { it.kind == BuildNoticeKind.BREAK }, "a full feed of discoveries keeps them all")
        // The discoveries leave; the element's next Break is still its first shown one.
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(6.1f), VisualFxCue.StatusBroken(0f, 0f, StatusElement.ION.ordinal)))
        assertEquals(listOf("+ Overload"), reducer.snapshot().buildNotifications.single().details)
    }

    @Test
    fun discoveriesOfOneKindMergeWithinOneAndAHalfSecondsUpToThreeLines() {
        val reducer = InteractionFxReducer(seed = 8)
        fun reaction(name: String) = VisualFxCue.BuildChanged(BuildNoticeTitles.REACTION_DISCOVERED, immutableListOf("+ $name"))
        reducer.apply(listOf(reaction("Fusion"), VisualFxCue.EffectsAdvanced(1.4f), reaction("Wildfire")))
        val merged = reducer.snapshot().buildNotifications.single()
        assertEquals(listOf("+ Fusion", "+ Wildfire"), merged.details)
        assertEquals(merged.maxLife, merged.life, "a merged toast starts its life again")
        assertEquals(1.4f, merged.age, 0.0001f, "but keeps its age")
        // A full toast hands the next find a toast of its own: no name is lost.
        reducer.apply(listOf(reaction("Feedback"), reaction("Rot Well")))
        assertEquals(listOf(listOf("+ Fusion", "+ Wildfire", "+ Feedback"), listOf("+ Rot Well")),
            reducer.snapshot().buildNotifications.map { it.details })
        // The same name twice adds nothing.
        reducer.apply(listOf(reaction("Rot Well")))
        assertEquals(listOf("+ Rot Well"), reducer.snapshot().buildNotifications.last().details)
        // Pairings merge only with pairings.
        reducer.apply(listOf(VisualFxCue.BuildChanged(BuildNoticeTitles.PAIRING_DISCOVERED, immutableListOf("+ Black Star"))))
        assertEquals(3, reducer.snapshot().buildNotifications.size)
        // The window runs from the toast's opening: a chain of finds 1 s apart opens a new toast past 1.5 s.
        val chain = InteractionFxReducer(seed = 12)
        chain.apply(listOf(reaction("Fusion"), VisualFxCue.EffectsAdvanced(1f), reaction("Wildfire"), VisualFxCue.EffectsAdvanced(0.6f),
            reaction("Magnetize")))
        assertEquals(listOf(listOf("+ Fusion", "+ Wildfire"), listOf("+ Magnetize")), chain.snapshot().buildNotifications.map { it.details })
        // First Breaks of several elements close together share one note.
        val breaks = InteractionFxReducer(seed = 9)
        breaks.apply(listOf(VisualFxCue.StatusBroken(0f, 0f, 0), VisualFxCue.StatusBroken(500f, 0f, 1)))
        assertEquals(listOf("+ Ignite", "+ Freeze"), breaks.snapshot().buildNotifications.single().details)
    }

    @Test
    fun flashesMoveWithAWorldRebase() {
        val reducer = InteractionFxReducer(seed = 13)
        reducer.apply(listOf(
            VisualFxCue.StatusBroken(100f, 50f, StatusElement.CRYO.ordinal),
            VisualFxCue.ReactionTriggered(300f, 50f, ReactionId.FUSION.ordinal, 80f, firstDiscovery = false),
            VisualFxCue.WorldRebased(40f, 20f),
        ))
        assertEquals(listOf(60f to 30f, 260f to 30f), reducer.snapshot().elementBursts.map { it.x to it.y })
    }

    @Test
    fun damageNumbersKeepTheirElementTone() {
        val reducer = InteractionFxReducer(seed = 10)
        reducer.apply(listOf(
            VisualFxCue.DamageNumberAdded(0f, 0f, 40, false, tone = 3),
            VisualFxCue.DamageNumberAdded(0f, 0f, 40, true),
        ))
        assertEquals(listOf(3, 0), reducer.snapshot().damageNumbers.map { it.tone })
    }
}
