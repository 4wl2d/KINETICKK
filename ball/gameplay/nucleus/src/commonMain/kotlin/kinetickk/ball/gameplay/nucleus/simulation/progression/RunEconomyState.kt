// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.profile.api.GameplayProfileSnapshot

/**
 * The run's level and Data, Keys, run Matter and the Profile's Matter totals. Written by the progression domain.
 */
internal class RunEconomyState(
    content: GameplayContentSnapshot,
    source: RunEconomyState?,
    activeRebirthProfile: RebirthProfile,
    initialMatter: Int?,
    bootstrapProgress: GameplayProfileSnapshot?,
) {
    internal var level: Int = source?.level ?: 1
    internal var data: Int = source?.data ?: 0
    internal var dataFraction: Float = source?.dataFraction ?: 0f
    internal var nextLevelData: Int = source?.nextLevelData
        ?: content.tempo.dataRequiredForLevel(1, activeRebirthProfile)
    internal var keys: Int = source?.keys ?: 0
    internal var runMatter: Long = source?.runMatter ?: 0L
    internal var matterFraction: Float = source?.matterFraction ?: 0f
    internal var totalMatter: Long = source?.totalMatter ?: initialMatter?.toLong()
        ?: bootstrapProgress?.economy?.matter ?: 0L
    internal var lifetimeMatter: Long = source?.lifetimeMatter ?: initialMatter?.toLong()
        ?: bootstrapProgress?.economy?.lifetimeMatter ?: totalMatter

    /** A new run starts at level 1 needing [firstLevelData], with no Data, Keys or run Matter; the Profile totals stay. */
    fun reset(firstLevelData: Int) {
        level = 1
        data = 0
        dataFraction = 0f
        nextLevelData = firstLevelData
        keys = 0
        runMatter = 0L
        matterFraction = 0f
    }
}
