// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.AmbientSiteWindow
import kinetickk.ball.gameplay.nucleus.model.SignalPrompt
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.WorldEventPlan
import kinetickk.ball.gameplay.nucleus.render.EventSignalProjection
import kinetickk.ball.gameplay.nucleus.render.VolatileBurstProjection
import kinetickk.ball.gameplay.nucleus.render.WorldEventProjection
import kinetickk.foundation.collections.ImmutableList

/*
 * Legacy forwarders; new code uses the holder; never add a forwarder for a new field.
 *
 * Holders of the sites, variety, world and dimensions domains. Each property forwards one field that moved from
 * [MutableGameState] into a state holder, so the call sites, tests, fingerprints and benchmark adapters written
 * against the flat state read it unchanged. This list may only shrink: a call site moved to
 * `state.<holder>.<field>` lets its forwarder go.
 */

// SiteRunState

internal var MutableGameState.sites: List<SiteState>
    get() = siteRun.sites
    set(value) { siteRun.sites = value }

internal var MutableGameState.nextSiteWindowIndex: Int
    get() = siteRun.nextSiteWindowIndex
    set(value) { siteRun.nextSiteWindowIndex = value }

internal var MutableGameState.pendingDirectedRewards: List<kinetickk.ball.content.api.DirectedReward>
    get() = siteRun.pendingDirectedRewards
    set(value) { siteRun.pendingDirectedRewards = value }

internal var MutableGameState.directedReward: kinetickk.ball.content.api.DirectedReward?
    get() = siteRun.directedReward
    set(value) { siteRun.directedReward = value }

internal var MutableGameState.selectedRewardFocus: kinetickk.ball.content.api.RewardFocus?
    get() = siteRun.selectedRewardFocus
    set(value) { siteRun.selectedRewardFocus = value }

internal var MutableGameState.ambientSites: AmbientSiteWindow
    get() = siteRun.ambientSites
    set(value) { siteRun.ambientSites = value }

internal var MutableGameState.secretSigil: SiteState?
    get() = siteRun.secretSigil
    set(value) { siteRun.secretSigil = value }

internal var MutableGameState.openedSiteIds: List<Long>
    get() = siteRun.openedSiteIds
    set(value) { siteRun.openedSiteIds = value }

internal var MutableGameState.runSecretFlags: Int
    get() = siteRun.runSecretFlags
    set(value) { siteRun.runSecretFlags = value }

internal var MutableGameState.pendingFloorOffers: List<ItemRarity>
    get() = siteRun.pendingFloorOffers
    set(value) { siteRun.pendingFloorOffers = value }

internal var MutableGameState.goldenDrifterId: Int
    get() = siteRun.goldenDrifterId
    set(value) { siteRun.goldenDrifterId = value }

internal var MutableGameState.goldenDrifterUntil: Float
    get() = siteRun.goldenDrifterUntil
    set(value) { siteRun.goldenDrifterUntil = value }

internal var MutableGameState.spinBuckets: List<Float>
    get() = siteRun.spinBuckets
    set(value) { siteRun.spinBuckets = value }

internal var MutableGameState.spinCurrent: Float
    get() = siteRun.spinCurrent
    set(value) { siteRun.spinCurrent = value }

internal var MutableGameState.spinClock: Float
    get() = siteRun.spinClock
    set(value) { siteRun.spinClock = value }

internal var MutableGameState.spinLastAngle: Float
    get() = siteRun.spinLastAngle
    set(value) { siteRun.spinLastAngle = value }

internal var MutableGameState.spinPrimed: Boolean
    get() = siteRun.spinPrimed
    set(value) { siteRun.spinPrimed = value }

// VarietyRunState

internal var MutableGameState.omenPending: Boolean
    get() = varietyRun.omenPending
    set(value) { varietyRun.omenPending = value }

internal var MutableGameState.runOmen: OmenId?
    get() = varietyRun.runOmen
    set(value) { varietyRun.runOmen = value }

internal var MutableGameState.runMutatorMask: Int
    get() = varietyRun.runMutatorMask
    set(value) { varietyRun.runMutatorMask = value }

internal var MutableGameState.directorPersonality: DirectorPersonalityId?
    get() = varietyRun.directorPersonality
    set(value) { varietyRun.directorPersonality = value }

internal var MutableGameState.featuredAspect: Int
    get() = varietyRun.featuredAspect
    set(value) { varietyRun.featuredAspect = value }

internal var MutableGameState.surgeIndex: Int
    get() = varietyRun.surgeIndex
    set(value) { varietyRun.surgeIndex = value }

internal var MutableGameState.surgeThemeType: Int
    get() = varietyRun.surgeThemeType
    set(value) { varietyRun.surgeThemeType = value }

internal var MutableGameState.varietyAnnouncement: Int
    get() = varietyRun.varietyAnnouncement
    set(value) { varietyRun.varietyAnnouncement = value }

internal var MutableGameState.keynoteTag: BuildTag?
    get() = varietyRun.keynoteTag
    set(value) { varietyRun.keynoteTag = value }

internal var MutableGameState.keynoteElement: StatusElement?
    get() = varietyRun.keynoteElement
    set(value) { varietyRun.keynoteElement = value }

internal var MutableGameState.worldEventPlans: ImmutableList<WorldEventPlan>
    get() = varietyRun.worldEventPlans
    set(value) { varietyRun.worldEventPlans = value }

internal var MutableGameState.nextWorldEvent: Int
    get() = varietyRun.nextWorldEvent
    set(value) { varietyRun.nextWorldEvent = value }

internal var MutableGameState.worldEvent: WorldEventProjection?
    get() = varietyRun.worldEvent
    set(value) { varietyRun.worldEvent = value }

internal var MutableGameState.worldEventPulse: Int
    get() = varietyRun.worldEventPulse
    set(value) { varietyRun.worldEventPulse = value }

internal var MutableGameState.worldEventNextPulseAt: Float
    get() = varietyRun.worldEventNextPulseAt
    set(value) { varietyRun.worldEventNextPulseAt = value }

internal var MutableGameState.eventSignals: ImmutableList<EventSignalProjection>
    get() = varietyRun.eventSignals
    set(value) { varietyRun.eventSignals = value }

internal var MutableGameState.nextSignalSlot: Int
    get() = varietyRun.nextSignalSlot
    set(value) { varietyRun.nextSignalSlot = value }

internal var MutableGameState.signalsSpawned: Int
    get() = varietyRun.signalsSpawned
    set(value) { varietyRun.signalsSpawned = value }

internal var MutableGameState.signalsEngaged: Int
    get() = varietyRun.signalsEngaged
    set(value) { varietyRun.signalsEngaged = value }

internal var MutableGameState.pendingSignalPrompts: ImmutableList<SignalPrompt>
    get() = varietyRun.pendingSignalPrompts
    set(value) { varietyRun.pendingSignalPrompts = value }

internal var MutableGameState.volatileBursts: ImmutableList<VolatileBurstProjection>
    get() = varietyRun.volatileBursts
    set(value) { varietyRun.volatileBursts = value }
