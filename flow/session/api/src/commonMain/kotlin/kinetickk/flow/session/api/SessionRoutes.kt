// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.api

/** Closed inventory of the eight application routes. */
sealed interface AppDestination {
    data object Home : AppDestination
    data object Gameplay : AppDestination
    data object Settings : AppDestination
    data object Lab : AppDestination
    data object Armory : AppDestination
    data object Rebirth : AppDestination
    data object Codex : AppDestination
    /** Character selection; opens only over the Home base. */
    data object Hangar : AppDestination
}

fun AppDestination.isBaseDestination(): Boolean = when (this) {
    AppDestination.Home,
    AppDestination.Gameplay,
    -> true
    AppDestination.Settings,
    AppDestination.Lab,
    AppDestination.Armory,
    AppDestination.Rebirth,
    AppDestination.Codex,
    AppDestination.Hangar,
    -> false
}

fun AppDestination.isOverlayDestination(): Boolean = !isBaseDestination()

enum class SessionShortcut {
    SETTINGS,
    LAB,
    ARMORY,
    REBIRTH,
    CODEX,
    MUTE,
    BACK,
    ENTER,
    /** H: the Hangar, from the Home base only. */
    HANGAR,
}

enum class SessionLifecycle {
    READY,
    BOOTSTRAP_UNAVAILABLE,
}

/** Presentation-safe view of the single closed workflow currently awaiting a participant. */
enum class SessionWorkflowPhase {
    STARTING_RUN,
    RESTARTING_RUN,
    PAUSING_FOR_OVERLAY,
    APPLYING_SETTINGS,
    SELECTING_CORE_SHAPE,
    TOGGLING_MUTE,
    PROPAGATING_MUTE,
    ADVANCING_REBIRTH,
    STARTING_REBIRTH_RUN,
    EXITING_RUN,
}
