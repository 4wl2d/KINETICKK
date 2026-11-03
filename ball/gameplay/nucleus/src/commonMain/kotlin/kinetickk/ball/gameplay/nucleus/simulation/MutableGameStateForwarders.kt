// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.simulation.enemies.EncounterState
import kinetickk.foundation.collections.ImmutableList

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

// SpawnDirectorState

internal var MutableGameState.spawnClock: Float
    get() = spawnDirector.spawnClock
    set(value) { spawnDirector.spawnClock = value }

internal var MutableGameState.nextEliteAt: Float
    get() = spawnDirector.nextEliteAt
    set(value) { spawnDirector.nextEliteAt = value }

internal var MutableGameState.elitesSpawned: Int
    get() = spawnDirector.elitesSpawned
    set(value) { spawnDirector.elitesSpawned = value }

internal var MutableGameState.heraldsSpawned: Int
    get() = spawnDirector.heraldsSpawned
    set(value) { spawnDirector.heraldsSpawned = value }

internal var MutableGameState.directorPhase: Int
    get() = spawnDirector.directorPhase
    set(value) { spawnDirector.directorPhase = value }

internal var MutableGameState.directorPhaseUntil: Float
    get() = spawnDirector.directorPhaseUntil
    set(value) { spawnDirector.directorPhaseUntil = value }

internal var MutableGameState.spawnBudget: Float
    get() = spawnDirector.spawnBudget
    set(value) { spawnDirector.spawnBudget = value }

internal var MutableGameState.pendingEventKind: Int
    get() = spawnDirector.pendingEventKind
    set(value) { spawnDirector.pendingEventKind = value }

internal var MutableGameState.pendingEventSize: Int
    get() = spawnDirector.pendingEventSize
    set(value) { spawnDirector.pendingEventSize = value }

internal var MutableGameState.pendingEventType: Int
    get() = spawnDirector.pendingEventType
    set(value) { spawnDirector.pendingEventType = value }

internal var MutableGameState.recentEventTypes: Long
    get() = spawnDirector.recentEventTypes
    set(value) { spawnDirector.recentEventTypes = value }

internal var MutableGameState.previousEventTypes: Long
    get() = spawnDirector.previousEventTypes
    set(value) { spawnDirector.previousEventTypes = value }

internal var MutableGameState.secretBossMask: Long
    get() = spawnDirector.secretBossMask
    set(value) { spawnDirector.secretBossMask = value }

internal var MutableGameState.encounters: ImmutableList<EncounterState>
    get() = spawnDirector.encounters
    set(value) { spawnDirector.encounters = value }

internal var MutableGameState.ambushReadyAt: Float
    get() = spawnDirector.ambushReadyAt
    set(value) { spawnDirector.ambushReadyAt = value }

internal var MutableGameState.encircleReadyAt: Float
    get() = spawnDirector.encircleReadyAt
    set(value) { spawnDirector.encircleReadyAt = value }

internal var MutableGameState.wallReadyAt: Float
    get() = spawnDirector.wallReadyAt
    set(value) { spawnDirector.wallReadyAt = value }

internal var MutableGameState.capOverflowReadyAt: Float
    get() = spawnDirector.capOverflowReadyAt
    set(value) { spawnDirector.capOverflowReadyAt = value }

internal var MutableGameState.ambushSpeedSeconds: Float
    get() = spawnDirector.ambushSpeedSeconds
    set(value) { spawnDirector.ambushSpeedSeconds = value }

internal var MutableGameState.spawnEventCount: Int
    get() = spawnDirector.spawnEventCount
    set(value) { spawnDirector.spawnEventCount = value }

internal var MutableGameState.lastSpawnEventKind: Int
    get() = spawnDirector.lastSpawnEventKind
    set(value) { spawnDirector.lastSpawnEventKind = value }

internal var MutableGameState.lastSpawnEventPlanned: Int
    get() = spawnDirector.lastSpawnEventPlanned
    set(value) { spawnDirector.lastSpawnEventPlanned = value }

internal var MutableGameState.lastSpawnEventSpawned: Int
    get() = spawnDirector.lastSpawnEventSpawned
    set(value) { spawnDirector.lastSpawnEventSpawned = value }
