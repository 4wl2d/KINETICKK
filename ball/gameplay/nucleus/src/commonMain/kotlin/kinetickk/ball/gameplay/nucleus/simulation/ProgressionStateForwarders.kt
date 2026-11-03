// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.EchoHit
import kinetickk.ball.gameplay.nucleus.model.ItemDeck
import kinetickk.ball.gameplay.nucleus.model.ItemHistoryWeights
import kinetickk.ball.gameplay.nucleus.model.RunFloatArray
import kinetickk.ball.gameplay.nucleus.model.RunIntArray
import kinetickk.ball.gameplay.nucleus.model.RunMutableSet
import kinetickk.ball.gameplay.nucleus.model.SeveredRelic
import kinetickk.ball.gameplay.nucleus.render.ChoiceType

/*
 * Legacy forwarders; new code uses the holder; never add a forwarder for a new field.
 *
 * Holders of the progression domain. Each property forwards one field that moved from [MutableGameState] into a
 * state holder, so the call sites, tests, fingerprints and benchmark adapters written against the flat state read
 * it unchanged. This list may only shrink: a call site moved to `state.<holder>.<field>` lets its forwarder go.
 */

// RunProfileCapture

internal var MutableGameState.activeRebirthProfile: RebirthProfile
    get() = profileCapture.activeRebirthProfile
    set(value) { profileCapture.activeRebirthProfile = value }

internal val MutableGameState.unlockedWeaponSet: RunMutableSet<WeaponId>
    get() = profileCapture.unlockedWeaponSet

internal var MutableGameState.unlockedWeaponView: Set<WeaponId>
    get() = profileCapture.unlockedWeaponView
    set(value) { profileCapture.unlockedWeaponView = value }

internal val MutableGameState.matrixRanks: RunIntArray
    get() = profileCapture.matrixRanks

internal val MutableGameState.characterUpgradeLevels: RunIntArray
    get() = profileCapture.characterUpgradeLevels

internal val MutableGameState.weaponUpgradeLevels: RunIntArray
    get() = profileCapture.weaponUpgradeLevels

internal val MutableGameState.deckSeed: Int
    get() = profileCapture.deckSeed

internal val MutableGameState.offersRunOmen: Boolean
    get() = profileCapture.offersRunOmen

internal var MutableGameState.itemDeck: ItemDeck
    get() = profileCapture.itemDeck
    set(value) { profileCapture.itemDeck = value }

internal val MutableGameState.itemHistory: ItemHistoryWeights
    get() = profileCapture.itemHistory

// DiscoveryState

internal val MutableGameState.discoveredItemIds: RunMutableSet<Int>
    get() = discoveryState.discoveredItemIds

internal var MutableGameState.discoveredRelicMask: Long
    get() = discoveryState.discoveredRelicMask
    set(value) { discoveryState.discoveredRelicMask = value }

internal var MutableGameState.pendingDiscoveredRelicMask: Long
    get() = discoveryState.pendingDiscoveredRelicMask
    set(value) { discoveryState.pendingDiscoveredRelicMask = value }

internal var MutableGameState.pendingDiscoveredItemIdStorage: RunMutableSet<Int>?
    get() = discoveryState.pendingDiscoveredItemIdStorage
    set(value) { discoveryState.pendingDiscoveredItemIdStorage = value }

internal var MutableGameState.discoveredReactionMask: Int
    get() = discoveryState.discoveredReactionMask
    set(value) { discoveryState.discoveredReactionMask = value }

internal var MutableGameState.pendingDiscoveredReactionMask: Int
    get() = discoveryState.pendingDiscoveredReactionMask
    set(value) { discoveryState.pendingDiscoveredReactionMask = value }

internal var MutableGameState.discoveredPairingMask: Int
    get() = discoveryState.discoveredPairingMask
    set(value) { discoveryState.discoveredPairingMask = value }

internal var MutableGameState.pendingDiscoveredPairingMask: Int
    get() = discoveryState.pendingDiscoveredPairingMask
    set(value) { discoveryState.pendingDiscoveredPairingMask = value }

internal var MutableGameState.worldDiscoveryMask: Int
    get() = discoveryState.worldDiscoveryMask
    set(value) { discoveryState.worldDiscoveryMask = value }

internal var MutableGameState.pendingWorldDiscoveryMask: Int
    get() = discoveryState.pendingWorldDiscoveryMask
    set(value) { discoveryState.pendingWorldDiscoveryMask = value }

// ProgressOutbox

internal var MutableGameState.pendingBankedMatter: Long
    get() = progressOutbox.pendingBankedMatter
    set(value) { progressOutbox.pendingBankedMatter = value }

internal var MutableGameState.pendingClearedRebirthLevel: Int?
    get() = progressOutbox.pendingClearedRebirthLevel
    set(value) { progressOutbox.pendingClearedRebirthLevel = value }

internal var MutableGameState.pendingEliteKills: Int
    get() = progressOutbox.pendingEliteKills
    set(value) { progressOutbox.pendingEliteKills = value }

internal var MutableGameState.pendingDashHits: Int
    get() = progressOutbox.pendingDashHits
    set(value) { progressOutbox.pendingDashHits = value }

internal var MutableGameState.pendingCompletedOrbits: Int
    get() = progressOutbox.pendingCompletedOrbits
    set(value) { progressOutbox.pendingCompletedOrbits = value }

internal var MutableGameState.pendingArchitectDefeatedWith: CoreShape?
    get() = progressOutbox.pendingArchitectDefeatedWith
    set(value) { progressOutbox.pendingArchitectDefeatedWith = value }

internal var MutableGameState.pendingTotalKills: Int
    get() = progressOutbox.pendingTotalKills
    set(value) { progressOutbox.pendingTotalKills = value }

internal var MutableGameState.pendingRicochets: Int
    get() = progressOutbox.pendingRicochets
    set(value) { progressOutbox.pendingRicochets = value }

internal var MutableGameState.pendingDataPickups: Int
    get() = progressOutbox.pendingDataPickups
    set(value) { progressOutbox.pendingDataPickups = value }

internal var MutableGameState.achievementFlushDue: Boolean
    get() = progressOutbox.achievementFlushDue
    set(value) { progressOutbox.achievementFlushDue = value }

internal var MutableGameState.pendingAcquiredItemIds: List<Int>
    get() = progressOutbox.pendingAcquiredItemIds
    set(value) { progressOutbox.pendingAcquiredItemIds = value }

internal var MutableGameState.bankedRunMatter: Long
    get() = progressOutbox.bankedRunMatter
    set(value) { progressOutbox.bankedRunMatter = value }

// RelicRunState

internal val MutableGameState.relicRanks: RunIntArray
    get() = relicRun.relicRanks

internal val MutableGameState.relicCooldowns: RunFloatArray
    get() = relicRun.relicCooldowns

internal val MutableGameState.relicCounters: RunIntArray
    get() = relicRun.relicCounters

internal val MutableGameState.echoHits: MutableList<EchoHit>
    get() = relicRun.echoHits

internal var MutableGameState.severedRelics: List<SeveredRelic>
    get() = relicRun.severedRelics
    set(value) { relicRun.severedRelics = value }

internal var MutableGameState.ouroborosSpent: Boolean
    get() = relicRun.ouroborosSpent
    set(value) { relicRun.ouroborosSpent = value }

internal var MutableGameState.ouroborosLockedUntil: Float
    get() = relicRun.ouroborosLockedUntil
    set(value) { relicRun.ouroborosLockedUntil = value }

internal var MutableGameState.heirloomCandidates: List<RelicId>
    get() = relicRun.heirloomCandidates
    set(value) { relicRun.heirloomCandidates = value }

internal var MutableGameState.heirloomPending: Boolean
    get() = relicRun.heirloomPending
    set(value) { relicRun.heirloomPending = value }

internal var MutableGameState.heirloomOfferActive: Boolean
    get() = relicRun.heirloomOfferActive
    set(value) { relicRun.heirloomOfferActive = value }

internal var MutableGameState.pendingHeirloomResolved: Boolean
    get() = relicRun.pendingHeirloomResolved
    set(value) { relicRun.pendingHeirloomResolved = value }

internal var MutableGameState.pendingVictoryRelics: List<RelicId>?
    get() = relicRun.pendingVictoryRelics
    set(value) { relicRun.pendingVictoryRelics = value }

// OfferRunState

internal var MutableGameState.activeChoiceType: ChoiceType
    get() = offerRun.activeChoiceType
    set(value) { offerRun.activeChoiceType = value }

internal var MutableGameState.rerollsRemaining: Int
    get() = offerRun.rerollsRemaining
    set(value) { offerRun.rerollsRemaining = value }

internal var MutableGameState.offerPity: Int
    get() = offerRun.offerPity
    set(value) { offerRun.offerPity = value }

internal var MutableGameState.itemOffersOpened: Int
    get() = offerRun.itemOffersOpened
    set(value) { offerRun.itemOffersOpened = value }

internal var MutableGameState.offerRarityFloor: ItemRarity?
    get() = offerRun.offerRarityFloor
    set(value) { offerRun.offerRarityFloor = value }

internal var MutableGameState.acquiredItemCount: Int
    get() = offerRun.acquiredItemCount
    set(value) { offerRun.acquiredItemCount = value }

internal var MutableGameState.recentItem: ItemDefinition?
    get() = offerRun.recentItem
    set(value) { offerRun.recentItem = value }

// RunStatsState

internal var MutableGameState.kills: Int
    get() = runStats.kills
    set(value) { runStats.kills = value }

internal var MutableGameState.damageDealt: Double
    get() = runStats.damageDealt
    set(value) { runStats.damageDealt = value }

internal var MutableGameState.damageTaken: Double
    get() = runStats.damageTaken
    set(value) { runStats.damageTaken = value }

internal var MutableGameState.damageAbsorbed: Double
    get() = runStats.damageAbsorbed
    set(value) { runStats.damageAbsorbed = value }

internal var MutableGameState.dataCollected: Long
    get() = runStats.dataCollected
    set(value) { runStats.dataCollected = value }

internal var MutableGameState.pickupsCollected: Long
    get() = runStats.pickupsCollected
    set(value) { runStats.pickupsCollected = value }

internal var MutableGameState.keysCollected: Long
    get() = runStats.keysCollected
    set(value) { runStats.keysCollected = value }

internal var MutableGameState.eliteKills: Int
    get() = runStats.eliteKills
    set(value) { runStats.eliteKills = value }

internal var MutableGameState.bestCombo: Int
    get() = runStats.bestCombo
    set(value) { runStats.bestCombo = value }
