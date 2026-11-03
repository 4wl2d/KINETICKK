// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation


/*
 * Legacy forwarders; new code uses the holder; never add a forwarder for a new field.
 *
 * Holders of the root simulation package: the frame state and the cue outbox. Each property forwards one field that
 * moved from [MutableGameState] into a state holder, so the call sites, tests, fingerprints and benchmark adapters
 * written against the flat state read it unchanged. This list may only shrink: a call site moved to
 * `state.<holder>.<field>` lets its forwarder go.
 */

// FrameState

internal var MutableGameState.uiScale: Float
    get() = frameState.uiScale
    set(value) { frameState.uiScale = value }

internal var MutableGameState.viewportDensity: Float
    get() = frameState.viewportDensity
    set(value) { frameState.viewportDensity = value }

internal var MutableGameState.accumulator: Float
    get() = frameState.accumulator
    set(value) { frameState.accumulator = value }

internal var MutableGameState.lastTransitionSteps: Int
    get() = frameState.lastTransitionSteps
    set(value) { frameState.lastTransitionSteps = value }

internal var MutableGameState.previousCameraX: Float
    get() = frameState.previousCameraX
    set(value) { frameState.previousCameraX = value }

internal var MutableGameState.previousCameraY: Float
    get() = frameState.previousCameraY
    set(value) { frameState.previousCameraY = value }

internal var MutableGameState.interpolationPrimed: Boolean
    get() = frameState.interpolationPrimed
    set(value) { frameState.interpolationPrimed = value }

internal var MutableGameState.lastImpact: Float
    get() = frameState.lastImpact
    set(value) { frameState.lastImpact = value }

internal var MutableGameState.lastImpactTime: Float
    get() = frameState.lastImpactTime
    set(value) { frameState.lastImpactTime = value }

internal var MutableGameState.damageFlash: Float
    get() = frameState.damageFlash
    set(value) { frameState.damageFlash = value }

internal var MutableGameState.screenShake: Float
    get() = frameState.screenShake
    set(value) { frameState.screenShake = value }

internal var MutableGameState.message: String
    get() = frameState.message
    set(value) { frameState.message = value }

internal var MutableGameState.messageTime: Float
    get() = frameState.messageTime
    set(value) { frameState.messageTime = value }
