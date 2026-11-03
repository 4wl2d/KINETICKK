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

// RelicMechanicState

internal var MutableGameState.glassInvulnerabilityReadyAt: Float
    get() = relicMechanics.glassInvulnerabilityReadyAt
    set(value) { relicMechanics.glassInvulnerabilityReadyAt = value }

internal var MutableGameState.bulwarkInvulnerabilityReadyAt: Float
    get() = relicMechanics.bulwarkInvulnerabilityReadyAt
    set(value) { relicMechanics.bulwarkInvulnerabilityReadyAt = value }

internal var MutableGameState.stormCharge: Float
    get() = relicMechanics.stormCharge
    set(value) { relicMechanics.stormCharge = value }

internal var MutableGameState.stasisMeter: Float
    get() = relicMechanics.stasisMeter
    set(value) { relicMechanics.stasisMeter = value }

internal var MutableGameState.stasisBank: Int
    get() = relicMechanics.stasisBank
    set(value) { relicMechanics.stasisBank = value }

internal var MutableGameState.stasisLockedUntil: Float
    get() = relicMechanics.stasisLockedUntil
    set(value) { relicMechanics.stasisLockedUntil = value }

internal var MutableGameState.sinceTurn: Float
    get() = relicMechanics.sinceTurn
    set(value) { relicMechanics.sinceTurn = value }

internal var MutableGameState.gyreReadyAt: Float
    get() = relicMechanics.gyreReadyAt
    set(value) { relicMechanics.gyreReadyAt = value }

internal var MutableGameState.gyreEchoAt: Float
    get() = relicMechanics.gyreEchoAt
    set(value) { relicMechanics.gyreEchoAt = value }

internal var MutableGameState.gyreEchoDamage: Float
    get() = relicMechanics.gyreEchoDamage
    set(value) { relicMechanics.gyreEchoDamage = value }

internal var MutableGameState.overdriveExtension: Float
    get() = relicMechanics.overdriveExtension
    set(value) { relicMechanics.overdriveExtension = value }

internal var MutableGameState.overdriveKills: Int
    get() = relicMechanics.overdriveKills
    set(value) { relicMechanics.overdriveKills = value }

internal var MutableGameState.aegisAbsorbed: Float
    get() = relicMechanics.aegisAbsorbed
    set(value) { relicMechanics.aegisAbsorbed = value }

internal var MutableGameState.aegisNovaReadyAt: Float
    get() = relicMechanics.aegisNovaReadyAt
    set(value) { relicMechanics.aegisNovaReadyAt = value }

internal var MutableGameState.shieldBreakArmed: Boolean
    get() = relicMechanics.shieldBreakArmed
    set(value) { relicMechanics.shieldBreakArmed = value }

internal var MutableGameState.shieldBreakReadyAt: Float
    get() = relicMechanics.shieldBreakReadyAt
    set(value) { relicMechanics.shieldBreakReadyAt = value }

internal var MutableGameState.glassCharge: Float
    get() = relicMechanics.glassCharge
    set(value) { relicMechanics.glassCharge = value }

internal var MutableGameState.integrityLiabilityCut: Float
    get() = relicMechanics.integrityLiabilityCut
    set(value) { relicMechanics.integrityLiabilityCut = value }

internal var MutableGameState.cinderRecoilReadyAt: Float
    get() = relicMechanics.cinderRecoilReadyAt
    set(value) { relicMechanics.cinderRecoilReadyAt = value }

internal var MutableGameState.secondWindSpent: Boolean
    get() = relicMechanics.secondWindSpent
    set(value) { relicMechanics.secondWindSpent = value }

internal var MutableGameState.matrixFreeDashReadyAt: Float
    get() = relicMechanics.matrixFreeDashReadyAt
    set(value) { relicMechanics.matrixFreeDashReadyAt = value }

internal var MutableGameState.necroticClock: Float
    get() = relicMechanics.necroticClock
    set(value) { relicMechanics.necroticClock = value }
