// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.characterization

import kinetickk.feature.game.domain.model.*
import kinetickk.feature.game.domain.simulation.*

/**
 * Keeps the behavior-characterization suite readable while exercising mutable simulation state
 * directly. This test-only alias is not part of the application API.
 */
internal typealias GameScenario = MutableGameState
