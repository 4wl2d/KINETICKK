// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.variety

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.SignalPrompt
import kinetickk.ball.gameplay.nucleus.model.WorldEventPlan
import kinetickk.ball.gameplay.nucleus.render.EventSignalProjection
import kinetickk.ball.gameplay.nucleus.render.VolatileBurstProjection
import kinetickk.ball.gameplay.nucleus.render.WorldEventProjection
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState.Companion.MAX_VOLATILE_BURSTS
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/**
 * Run variety: the omen, run mutators, the director personality and featured aspect, surges, the Keynote, world events,
 * event signals and their prompts, and the Volatile Remains bursts. Written by the variety domain.
 */
internal class VarietyRunState(source: VarietyRunState?) {
    internal var omenPending: Boolean = source?.omenPending ?: false
    internal var runOmen: OmenId? = source?.runOmen
    /** Rebirth mutators a run modifier added (bit per ordinal), on top of the tier's own. */
    internal var runMutatorMask: Int = source?.runMutatorMask ?: 0
    internal var directorPersonality: DirectorPersonalityId? = source?.directorPersonality
    internal var featuredAspect: Int = source?.featuredAspect ?: -1
    internal var surgeIndex: Int = source?.surgeIndex ?: 0
    internal var surgeThemeType: Int = source?.surgeThemeType ?: -1
    internal var varietyAnnouncement: Int = source?.varietyAnnouncement ?: 0
    internal var keynoteTag: BuildTag? = source?.keynoteTag
    internal var keynoteElement: StatusElement? = source?.keynoteElement
    /** At most [ContentBounds.MAX_WORLD_EVENTS_PER_RUN] plans. */
    internal var worldEventPlans: ImmutableList<WorldEventPlan> = source?.worldEventPlans ?: immutableListOf()
    internal var nextWorldEvent: Int = source?.nextWorldEvent ?: 0
    internal var worldEvent: WorldEventProjection? = source?.worldEvent
    internal var worldEventPulse: Int = source?.worldEventPulse ?: 0
    internal var worldEventNextPulseAt: Float = source?.worldEventNextPulseAt ?: 0f
    internal var eventSignals: ImmutableList<EventSignalProjection> = source?.eventSignals ?: immutableListOf()
    internal var nextSignalSlot: Int = source?.nextSignalSlot ?: 0
    internal var signalsSpawned: Int = source?.signalsSpawned ?: 0
    internal var signalsEngaged: Int = source?.signalsEngaged ?: 0
    /** At most [ContentBounds.MAX_PENDING_SIGNAL_PROMPTS] prompts. */
    internal var pendingSignalPrompts: ImmutableList<SignalPrompt> = source?.pendingSignalPrompts ?: immutableListOf()
    /** Volatile Remains bursts waiting to detonate (Rebirth R4), at most [MAX_VOLATILE_BURSTS]. */
    internal var volatileBursts: ImmutableList<VolatileBurstProjection> = source?.volatileBursts ?: immutableListOf()
}
