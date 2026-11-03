// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.ActiveBuff
import kinetickk.ball.gameplay.nucleus.model.RunFloatArray
import kinetickk.ball.gameplay.nucleus.model.RunIntArray

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
