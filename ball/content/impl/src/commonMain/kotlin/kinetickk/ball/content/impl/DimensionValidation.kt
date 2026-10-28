// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds

/** Cross-checks of the dimension content and its boons (each definition validates itself on construction). */
internal fun validateDimensions(data: ContentBootstrapData) {
    require(data.boons.size <= ContentBounds.MAX_BOON_DEFINITIONS) {
        "boons size ${data.boons.size} exceeds bootstrap bound ${ContentBounds.MAX_BOON_DEFINITIONS}"
    }
    require(data.boons.map { it.id }.toSet().size == data.boons.size) { "Boon ids must be unique" }
}
