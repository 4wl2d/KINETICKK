// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

/** Cross-checks of the run-variety content against the rest of the catalog (each policy validates itself on construction). */
internal fun validateRunVariety(data: ContentBootstrapData) {
    data.variety.director.personalities.forEach { personality ->
        personality.typeBias.forEach { bias ->
            val enemy = data.enemies.firstOrNull { it.type == bias.type }
            require(enemy != null && !enemy.isScheduled) {
                "Personality ${personality.id} may bias only ordinary kinds, not ${bias.type}"
            }
        }
    }
}
