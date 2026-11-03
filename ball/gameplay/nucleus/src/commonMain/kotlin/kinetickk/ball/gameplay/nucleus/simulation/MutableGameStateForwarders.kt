// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction

// StepScratch

internal var MutableGameState.statusCuesThisStep: Int
    get() = scratch.statusCuesThisStep
    set(value) { scratch.statusCuesThisStep = value }

internal val MutableGameState.chainScratch: IntArray
    get() = scratch.chainScratch

internal var MutableGameState.chainDepth: Int
    get() = scratch.chainDepth
    set(value) { scratch.chainDepth = value }

internal var MutableGameState.shatteringEnemyId: Int
    get() = scratch.shatteringEnemyId
    set(value) { scratch.shatteringEnemyId = value }

// PendingChoiceState

internal var MutableGameState.pendingLevelChoices: Int
    get() = pendingChoice.pendingLevelChoices
    set(value) { pendingChoice.pendingLevelChoices = value }

internal var MutableGameState.pendingRelicChoices: Int
    get() = pendingChoice.pendingRelicChoices
    set(value) { pendingChoice.pendingRelicChoices = value }

internal var MutableGameState.pendingBindingRelic: RelicId?
    get() = pendingChoice.pendingBindingRelic
    set(value) { pendingChoice.pendingBindingRelic = value }

internal var MutableGameState.pendingRelicBindAction: RelicChoiceAction?
    get() = pendingChoice.pendingRelicBindAction
    set(value) { pendingChoice.pendingRelicBindAction = value }

internal var MutableGameState.pendingActiveItemId: Int?
    get() = pendingChoice.pendingActiveItemId
    set(value) { pendingChoice.pendingActiveItemId = value }

internal var MutableGameState.pendingActiveReborn: Boolean
    get() = pendingChoice.pendingActiveReborn
    set(value) { pendingChoice.pendingActiveReborn = value }
