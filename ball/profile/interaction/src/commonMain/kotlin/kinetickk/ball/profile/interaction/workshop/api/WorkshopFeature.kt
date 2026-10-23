// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.api

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId

/** The Workshop's tabs; the ordinal is the strip order (and the 1/2/3 key). */
enum class WorkshopTab {
    CHARACTERS,
    WEAPONS,
    MATRIX,
}

/**
 * A request from the shell to show [tab] (a Home row, the L/A/H aliases, the Hangar card). [serial]
 * makes a repeated request apply again; a request whose serial was already applied changes nothing.
 */
data class WorkshopTabRequest(val tab: WorkshopTab, val serial: Int)

sealed interface WorkshopOutput {
    data object Back : WorkshopOutput

    /** Character selection goes through Session, the authority on "between runs". */
    data class SelectCharacter(val shape: CoreShape) : WorkshopOutput
}

/**
 * Characters, weapons and the Core Matrix behind one header with tabs. The shown tab is
 * Interaction-ephemeral: it survives closing and reopening within the app session, and a
 * [WorkshopTabRequest] with a new serial replaces it.
 */
interface WorkshopFeature {
    @Composable
    fun Content(
        routeToken: Long,
        /** Null keeps the tab shown last in this app session. */
        tabRequest: WorkshopTabRequest?,
        /** Session's `AppShellProjection.characterSelectionAvailable`: Select renders disabled without it. */
        characterSelectionAvailable: Boolean,
        /** The weapon of the run in progress, marked "In this run". */
        activeRunWeapon: WeaponId?,
        onOutput: (WorkshopOutput) -> Unit,
    )
}
