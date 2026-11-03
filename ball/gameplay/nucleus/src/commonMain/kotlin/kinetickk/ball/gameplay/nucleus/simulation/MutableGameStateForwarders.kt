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

// CoreReactionState

internal var MutableGameState.ablazeUntil: Float
    get() = coreReactions.ablazeUntil
    set(value) { coreReactions.ablazeUntil = value }

internal var MutableGameState.frictionlessUntil: Float
    get() = coreReactions.frictionlessUntil
    set(value) { coreReactions.frictionlessUntil = value }

internal var MutableGameState.blastJumpUntil: Float
    get() = coreReactions.blastJumpUntil
    set(value) { coreReactions.blastJumpUntil = value }

internal var MutableGameState.weaveUntil: Float
    get() = coreReactions.weaveUntil
    set(value) { coreReactions.weaveUntil = value }

internal var MutableGameState.weaveDamage: Float
    get() = coreReactions.weaveDamage
    set(value) { coreReactions.weaveDamage = value }

internal var MutableGameState.weaveRange: Float
    get() = coreReactions.weaveRange
    set(value) { coreReactions.weaveRange = value }

internal var MutableGameState.guardShards: Int
    get() = coreReactions.guardShards
    set(value) { coreReactions.guardShards = value }

internal var MutableGameState.guardUntil: Float
    get() = coreReactions.guardUntil
    set(value) { coreReactions.guardUntil = value }

internal var MutableGameState.guardDamage: Float
    get() = coreReactions.guardDamage
    set(value) { coreReactions.guardDamage = value }

internal var MutableGameState.voidAnchorId: Int
    get() = coreReactions.voidAnchorId
    set(value) { coreReactions.voidAnchorId = value }

internal var MutableGameState.voidAnchorUntil: Float
    get() = coreReactions.voidAnchorUntil
    set(value) { coreReactions.voidAnchorUntil = value }

internal var MutableGameState.overcurrentReadyAt: Float
    get() = coreReactions.overcurrentReadyAt
    set(value) { coreReactions.overcurrentReadyAt = value }

internal var MutableGameState.flameRefundReadyAt: Float
    get() = coreReactions.flameRefundReadyAt
    set(value) { coreReactions.flameRefundReadyAt = value }

internal var MutableGameState.erasureLatchId: Int
    get() = coreReactions.erasureLatchId
    set(value) { coreReactions.erasureLatchId = value }

internal var MutableGameState.coreImpulseReadyAt: Float
    get() = coreReactions.coreImpulseReadyAt
    set(value) { coreReactions.coreImpulseReadyAt = value }

internal var MutableGameState.primedDashUntil: Float
    get() = coreReactions.primedDashUntil
    set(value) { coreReactions.primedDashUntil = value }

internal var MutableGameState.dashHeatBank: Float
    get() = coreReactions.dashHeatBank
    set(value) { coreReactions.dashHeatBank = value }

internal var MutableGameState.phaseUntil: Float
    get() = coreReactions.phaseUntil
    set(value) { coreReactions.phaseUntil = value }

internal var MutableGameState.aftershockAt0: Float
    get() = coreReactions.aftershockAt0
    set(value) { coreReactions.aftershockAt0 = value }

internal var MutableGameState.aftershockX0: Float
    get() = coreReactions.aftershockX0
    set(value) { coreReactions.aftershockX0 = value }

internal var MutableGameState.aftershockY0: Float
    get() = coreReactions.aftershockY0
    set(value) { coreReactions.aftershockY0 = value }

internal var MutableGameState.aftershockDamage0: Float
    get() = coreReactions.aftershockDamage0
    set(value) { coreReactions.aftershockDamage0 = value }

internal var MutableGameState.aftershockAt1: Float
    get() = coreReactions.aftershockAt1
    set(value) { coreReactions.aftershockAt1 = value }

internal var MutableGameState.aftershockX1: Float
    get() = coreReactions.aftershockX1
    set(value) { coreReactions.aftershockX1 = value }

internal var MutableGameState.aftershockY1: Float
    get() = coreReactions.aftershockY1
    set(value) { coreReactions.aftershockY1 = value }

internal var MutableGameState.aftershockDamage1: Float
    get() = coreReactions.aftershockDamage1
    set(value) { coreReactions.aftershockDamage1 = value }

internal var MutableGameState.shotErasedDispatches: Int
    get() = coreReactions.shotErasedDispatches
    set(value) { coreReactions.shotErasedDispatches = value }

internal var MutableGameState.markDispatches: Int
    get() = coreReactions.markDispatches
    set(value) { coreReactions.markDispatches = value }

internal var MutableGameState.shotsErased: Long
    get() = coreReactions.shotsErased
    set(value) { coreReactions.shotsErased = value }
