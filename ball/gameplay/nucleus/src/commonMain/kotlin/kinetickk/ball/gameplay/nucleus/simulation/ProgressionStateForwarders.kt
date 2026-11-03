// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.ItemDeck
import kinetickk.ball.gameplay.nucleus.model.ItemHistoryWeights
import kinetickk.ball.gameplay.nucleus.model.RunIntArray
import kinetickk.ball.gameplay.nucleus.model.RunMutableSet

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
