// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction

/**
 * Choices waiting to open: queued level and relic offers, the relic awaiting its bind and the active item awaiting
 * a slot. Written by the progression domain.
 */
internal class PendingChoiceState(source: PendingChoiceState?) {
    internal var pendingLevelChoices: Int = source?.pendingLevelChoices ?: 0
    internal var pendingRelicChoices: Int = source?.pendingRelicChoices ?: 0
    internal var pendingBindingRelic: RelicId? = source?.pendingBindingRelic
    internal var pendingRelicBindAction: RelicChoiceAction? = source?.pendingRelicBindAction
    /**
     * The unowned active item taken while every slot held an active: it waits, with its Reborn roll, for the
     * [ChoiceType.ACTIVE_SLOT] prompt to replace a slot or salvage it; null otherwise.
     */
    internal var pendingActiveItemId: Int? = source?.pendingActiveItemId
    internal var pendingActiveReborn: Boolean = source?.pendingActiveReborn ?: false

    /** A new run has no choice waiting. */
    fun reset() {
        pendingLevelChoices = 0
        pendingRelicChoices = 0
        pendingBindingRelic = null
        pendingRelicBindAction = null
        pendingActiveItemId = null
        pendingActiveReborn = false
    }
}
