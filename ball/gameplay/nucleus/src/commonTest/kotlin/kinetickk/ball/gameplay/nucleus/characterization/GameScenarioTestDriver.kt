// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.characterization

import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot

/**
 * Keeps the behavior-characterization suite readable while exercising mutable simulation state
 * directly. This test-only alias is not part of the application API.
 */
internal typealias GameScenario = MutableGameState

internal fun gameScenario(
    seed: Int = 731_991,
    initialMatter: Int? = null,
    initialRebirthLevel: Int = 0,
    bootstrapProgress: GameplayProfileSnapshot? = null,
): GameScenario = MutableGameState(
    content = canonicalGameplayContent,
    seed = seed,
    initialMatter = initialMatter,
    initialRebirthLevel = initialRebirthLevel,
    bootstrapProgress = bootstrapProgress,
)
