// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.api

/** Closed inventory of the seven application routes. */
sealed interface AppDestination {
    data object Home : AppDestination
    data object Gameplay : AppDestination
    data object Settings : AppDestination
    data object Lab : AppDestination
    data object Armory : AppDestination
    data object Rebirth : AppDestination
    data object Codex : AppDestination
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
}

enum class SessionResetLifecycle {
    READY,
    CONFIRMATION_REQUIRED,
    RESET_IN_PROGRESS,
    PURGE_NEEDS_ATTENTION,
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
    CONFIRMING_RESET,
    RETRYING_PURGE,
}
