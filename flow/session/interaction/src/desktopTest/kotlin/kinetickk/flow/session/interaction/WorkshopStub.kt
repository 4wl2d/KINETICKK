// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.interaction.workshop.api.WorkshopFeature
import kinetickk.ball.profile.interaction.workshop.api.WorkshopOutput
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTabRequest

/** A Workshop that draws nothing, for shell tests that never open it. */
internal val NoWorkshopFeature: WorkshopFeature = object : WorkshopFeature {
    @Composable
    override fun Content(
        routeToken: Long,
        tabRequest: WorkshopTabRequest?,
        characterSelectionAvailable: Boolean,
        activeRunWeapon: WeaponId?,
        onOutput: (WorkshopOutput) -> Unit,
    ) = Unit
}
