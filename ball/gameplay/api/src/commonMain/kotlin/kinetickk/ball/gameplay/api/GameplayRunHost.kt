// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.api

/** Creates and exposes the one active GameplayRun. */
interface GameplayRunHost {
    fun createRun(
        runId: RunId,
    ): GameplayRunPort

    fun activeRun(): GameplayRunPort?
}
