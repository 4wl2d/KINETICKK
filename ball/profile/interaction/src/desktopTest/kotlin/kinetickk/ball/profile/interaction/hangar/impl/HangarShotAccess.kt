// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.hangar.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.interaction.hangar.api.HangarRenderModel

/** Screenshot access to the Hangar's internal content for the profile shot suite. */
internal object HangarShotAccess {
    fun HangarProjection.model(matter: Long): HangarRenderModel = toRenderModel(defaultCoreShapeDefinitions(), matter)

    @Composable
    fun Content(model: HangarRenderModel, inspected: CoreShape, textScale: Float) {
        HangarContent(HangarState(model, inspected), textScale, remember { ScrollState(0) }) {}
    }
}
