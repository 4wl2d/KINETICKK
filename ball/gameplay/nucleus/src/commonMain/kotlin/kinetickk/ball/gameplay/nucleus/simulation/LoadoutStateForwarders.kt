// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.ActiveBuff
import kinetickk.ball.gameplay.nucleus.model.CombatFieldTable
import kinetickk.ball.gameplay.nucleus.model.RunFloatArray
import kinetickk.ball.gameplay.nucleus.model.RunIntArray
import kinetickk.ball.gameplay.nucleus.model.WeaponRuntime

/*
 * Legacy forwarders; new code uses the holder; never add a forwarder for a new field.
 *
 * Holders of the builds, elements, weapons and characters domains. Each property forwards one field that moved from
 * [MutableGameState] into a state holder, so the call sites, tests, fingerprints and benchmark adapters written
 * against the flat state read it unchanged. This list may only shrink: a call site moved to
 * `state.<holder>.<field>` lets its forwarder go.
 */

// BuildRunState

internal val MutableGameState.itemStacks: RunIntArray
    get() = buildRun.itemStacks

internal val MutableGameState.rebornItems: RunIntArray
    get() = buildRun.rebornItems

internal var MutableGameState.acquisitionOrder: List<Int>
    get() = buildRun.acquisitionOrder
    set(value) { buildRun.acquisitionOrder = value }

internal val MutableGameState.clauseReadyAt: RunFloatArray
    get() = buildRun.clauseReadyAt

internal val MutableGameState.clauseCounters: RunIntArray
    get() = buildRun.clauseCounters

internal val MutableGameState.statusAccumulators: RunFloatArray
    get() = buildRun.statusAccumulators

internal val MutableGameState.itemStatusReadyAt: RunFloatArray
    get() = buildRun.itemStatusReadyAt

internal var MutableGameState.buffs: List<ActiveBuff>
    get() = buildRun.buffs
    set(value) { buildRun.buffs = value }

internal var MutableGameState.clauseDepth: Int
    get() = buildRun.clauseDepth
    set(value) { buildRun.clauseDepth = value }

internal var MutableGameState.comboKillCounter: Int
    get() = buildRun.comboKillCounter
    set(value) { buildRun.comboKillCounter = value }

internal var MutableGameState.arsenalCounter: Int
    get() = buildRun.arsenalCounter
    set(value) { buildRun.arsenalCounter = value }

// ElementRunState

internal val MutableGameState.combatFieldTable: CombatFieldTable
    get() = elementRun.combatFieldTable

internal var MutableGameState.coreFieldMask: Int
    get() = elementRun.coreFieldMask
    set(value) { elementRun.coreFieldMask = value }

internal var MutableGameState.fieldEnterReadyAt: Float
    get() = elementRun.fieldEnterReadyAt
    set(value) { elementRun.fieldEnterReadyAt = value }

internal var MutableGameState.reactionTokens: Float
    get() = elementRun.reactionTokens
    set(value) { elementRun.reactionTokens = value }

internal var MutableGameState.breakTokens: Float
    get() = elementRun.breakTokens
    set(value) { elementRun.breakTokens = value }

// WeaponRunState

internal var MutableGameState.weaponLevel: Int
    get() = weaponRun.weaponLevel
    set(value) { weaponRun.weaponLevel = value }

internal var MutableGameState.weaponRuntime: WeaponRuntime
    get() = weaponRun.weaponRuntime
    set(value) { weaponRun.weaponRuntime = value }

internal var MutableGameState.weaponClock: Float
    get() = weaponRun.weaponClock
    set(value) { weaponRun.weaponClock = value }

internal var MutableGameState.weaponSecondaryClock: Float
    get() = weaponRun.weaponSecondaryClock
    set(value) { weaponRun.weaponSecondaryClock = value }

internal val MutableGameState.agonyMutationCounts: RunIntArray
    get() = weaponRun.agonyMutationCounts

internal var MutableGameState.trailLastX: Float
    get() = weaponRun.trailLastX
    set(value) { weaponRun.trailLastX = value }

internal var MutableGameState.trailLastY: Float
    get() = weaponRun.trailLastY
    set(value) { weaponRun.trailLastY = value }

internal var MutableGameState.trailDistanceCarry: Float
    get() = weaponRun.trailDistanceCarry
    set(value) { weaponRun.trailDistanceCarry = value }

internal var MutableGameState.morningstarAngle: Float
    get() = weaponRun.morningstarAngle
    set(value) { weaponRun.morningstarAngle = value }

internal var MutableGameState.morningstarX: Float
    get() = weaponRun.morningstarX
    set(value) { weaponRun.morningstarX = value }

internal var MutableGameState.morningstarY: Float
    get() = weaponRun.morningstarY
    set(value) { weaponRun.morningstarY = value }

internal var MutableGameState.previousMorningstarX: Float
    get() = weaponRun.previousMorningstarX
    set(value) { weaponRun.previousMorningstarX = value }

internal var MutableGameState.previousMorningstarY: Float
    get() = weaponRun.previousMorningstarY
    set(value) { weaponRun.previousMorningstarY = value }

internal var MutableGameState.weaponBeamTime: Float
    get() = weaponRun.weaponBeamTime
    set(value) { weaponRun.weaponBeamTime = value }

internal var MutableGameState.weaponBeamStartX: Float
    get() = weaponRun.weaponBeamStartX
    set(value) { weaponRun.weaponBeamStartX = value }

internal var MutableGameState.weaponBeamStartY: Float
    get() = weaponRun.weaponBeamStartY
    set(value) { weaponRun.weaponBeamStartY = value }

internal var MutableGameState.weaponBeamEndX: Float
    get() = weaponRun.weaponBeamEndX
    set(value) { weaponRun.weaponBeamEndX = value }

internal var MutableGameState.weaponBeamEndY: Float
    get() = weaponRun.weaponBeamEndY
    set(value) { weaponRun.weaponBeamEndY = value }
