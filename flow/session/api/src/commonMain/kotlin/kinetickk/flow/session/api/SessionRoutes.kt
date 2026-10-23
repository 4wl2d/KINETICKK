// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.api

/** Closed inventory of the six application routes. */
sealed interface AppDestination {
    data object Home : AppDestination
    data object Gameplay : AppDestination
    data object Settings : AppDestination
    /**
     * Characters, weapons and the Core Matrix behind one tabbed overlay. It opens over Home and
     * over a paused (or pausing) run; character selection stays a Home-only Session decision.
     */
    data object Workshop : AppDestination
    data object Rebirth : AppDestination
    data object Codex : AppDestination
}

fun AppDestination.isBaseDestination(): Boolean = when (this) {
    AppDestination.Home,
    AppDestination.Gameplay,
    -> true
    AppDestination.Settings,
    AppDestination.Workshop,
    AppDestination.Rebirth,
    AppDestination.Codex,
    -> false
}

fun AppDestination.isOverlayDestination(): Boolean = !isBaseDestination()

enum class SessionShortcut {
    SETTINGS,
    /** W: toggles the Workshop (the tab aliases L, A and H are presentation keys mapped by Interaction). */
    WORKSHOP,
    REBIRTH,
    CODEX,
    MUTE,
    BACK,
    ENTER,
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
    SELECTING_REBIRTH_TIER,
    STARTING_REBIRTH_RUN,
    EXITING_RUN,
}
