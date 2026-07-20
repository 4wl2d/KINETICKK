// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

internal sealed interface AppDestination {
    data object Home : AppDestination
    data object Gameplay : AppDestination
    data object Settings : AppDestination
    data object Lab : AppDestination
    data object Armory : AppDestination
    data object Rebirth : AppDestination
    data object Codex : AppDestination
}

internal enum class AppGameplayPhase {
    IDLE,
    RUNNING,
    PAUSED,
    CHOICE,
    GAME_OVER,
    VICTORY,
}

internal data class AppBackStack(
    val base: AppDestination = AppDestination.Home,
    val overlay: AppDestination? = null,
    val routeToken: Int = 0,
) {
    init {
        require(base == AppDestination.Home || base == AppDestination.Gameplay)
        require(overlay == null || overlay.isOverlay())
    }

    val active: AppDestination
        get() = overlay ?: base

    val entries: List<AppDestination>
        get() = if (overlay == null) listOf(base) else listOf(base, overlay)
}

internal data class AppNavigationTransition(
    val backStack: AppBackStack,
    val pauseGameplay: Boolean = false,
)

internal class AppNavigator(
    initialBackStack: AppBackStack = AppBackStack(),
) {
    var backStack: AppBackStack = initialBackStack
        private set

    fun showHome() {
        backStack = AppBackStack(
            base = AppDestination.Home,
            routeToken = backStack.routeToken + 1,
        )
    }

    fun showGameplay() {
        backStack = AppBackStack(
            base = AppDestination.Gameplay,
            routeToken = backStack.routeToken + 1,
        )
    }

    fun openOverlay(
        destination: AppDestination,
        gameplayPhase: AppGameplayPhase,
    ): AppNavigationTransition {
        require(destination.isOverlay()) { "$destination is not an overlay destination" }
        if (!canOpen(destination, gameplayPhase)) {
            return AppNavigationTransition(backStack)
        }
        val needsPause = backStack.base == AppDestination.Gameplay &&
            gameplayPhase == AppGameplayPhase.RUNNING
        backStack = backStack.copy(
            overlay = destination,
            routeToken = backStack.routeToken + 1,
        )
        return AppNavigationTransition(backStack, pauseGameplay = needsPause)
    }

    fun back(): AppNavigationTransition {
        if (backStack.overlay != null) {
            backStack = backStack.copy(
                overlay = null,
                routeToken = backStack.routeToken + 1,
            )
        }
        return AppNavigationTransition(backStack)
    }

    private fun canOpen(destination: AppDestination, gameplayPhase: AppGameplayPhase): Boolean {
        if (backStack.base != AppDestination.Gameplay) return true
        return when (gameplayPhase) {
            AppGameplayPhase.CHOICE -> false
            AppGameplayPhase.GAME_OVER,
            AppGameplayPhase.VICTORY,
            -> destination == AppDestination.Rebirth
            AppGameplayPhase.IDLE,
            AppGameplayPhase.RUNNING,
            AppGameplayPhase.PAUSED,
            -> true
        }
    }
}

private fun AppDestination.isOverlay(): Boolean = when (this) {
    AppDestination.Home,
    AppDestination.Gameplay,
    -> false
    AppDestination.Settings,
    AppDestination.Lab,
    AppDestination.Armory,
    AppDestination.Rebirth,
    AppDestination.Codex,
    -> true
}
