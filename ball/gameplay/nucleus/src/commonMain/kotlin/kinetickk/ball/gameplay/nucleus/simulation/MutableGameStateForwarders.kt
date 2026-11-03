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

// SkillCastState

internal var MutableGameState.skillBufferMask: Int
    get() = skillCast.skillBufferMask
    set(value) { skillCast.skillBufferMask = value }

internal var MutableGameState.skillBufferTime: Float
    get() = skillCast.skillBufferTime
    set(value) { skillCast.skillBufferTime = value }

internal var MutableGameState.apexBufferTime: Float
    get() = skillCast.apexBufferTime
    set(value) { skillCast.apexBufferTime = value }

internal val MutableGameState.skillScratch: IntArray
    get() = skillCast.skillScratch

internal var MutableGameState.castSlot: Int
    get() = skillCast.castSlot
    set(value) { skillCast.castSlot = value }

internal var MutableGameState.castHits: Int
    get() = skillCast.castHits
    set(value) { skillCast.castHits = value }

internal var MutableGameState.castAttunedStacks: Int
    get() = skillCast.castAttunedStacks
    set(value) { skillCast.castAttunedStacks = value }

internal var MutableGameState.castPrimaryId: Int
    get() = skillCast.castPrimaryId
    set(value) { skillCast.castPrimaryId = value }

internal var MutableGameState.castBossHitFirst: Int
    get() = skillCast.castBossHitFirst
    set(value) { skillCast.castBossHitFirst = value }

internal var MutableGameState.castBossHitSecond: Int
    get() = skillCast.castBossHitSecond
    set(value) { skillCast.castBossHitSecond = value }
