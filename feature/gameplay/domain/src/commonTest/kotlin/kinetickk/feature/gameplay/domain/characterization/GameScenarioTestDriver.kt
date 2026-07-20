// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.characterization

import kinetickk.feature.gameplay.domain.model.*
import kinetickk.feature.gameplay.domain.simulation.*

/**
 * Keeps the behavior-characterization suite readable while exercising mutable simulation state
 * directly. This test-only alias is not part of the application API.
 */
internal typealias GameScenario = MutableGameState
