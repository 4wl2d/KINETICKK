// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kotlin.math.floor
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*

/** Data needed to go from run level [level] to the next: the content table scaled by the run's Rebirth tier. */
internal fun MutableGameState.dataRequiredForLevel(level: Int): Int =
    content.tempo.dataRequiredForLevel(level, rebirthProfile)

internal fun MutableGameState.gainData(rawAmount: Float) {
    if (creditData(rawAmount) <= 0) return
    val levelBefore = level
    val maximumLevel = content.tempo.maximumLevel
    while (level < maximumLevel && data >= nextLevelData) {
        data -= nextLevelData
        level++
        pendingLevelChoices++
        onLevelUp()
        nextLevelData = dataRequiredForLevel(level)
        if (content.tempo.grantsWeaponAmplification(level) &&
            weaponLevel < content.tempo.maximumAutomaticWeaponLevel + matrix.startingWeaponLevels + signatureLevelBonus()
        ) {
            val before = captureBuildChange()
            amplifyCurrentWeapon()
            emitBuildChange(before, currentWeaponDefinition.name + " mastery advanced")
        }
        if (level == maximumLevel) {
            // The capped level holds no Data: the leftover would only charge an Apex that unlocks ready.
            data = 0
            unlockApex()
        }
    }
    if (level > levelBefore) vacuumOnScreenData()
    openNextPendingChoice()
}

/**
 * Adds Data without levelling and returns the whole points added; the fraction carries over. A LEVEL_UP
 * clause's Data arrives this way, so the level loop in [gainData] that fired it takes the Data up against the
 * settled requirement and never re-enters itself. At the run's maximum level every whole Data still counts as
 * collected but charges the Apex instead and adds nothing (0 is returned, so [gainData] never loops).
 */
internal fun MutableGameState.creditData(rawAmount: Float): Int {
    dataFraction += rawAmount * dataGainLiability()
    val whole = floor(dataFraction).toInt()
    if (whole <= 0) return 0
    dataFraction -= whole
    dataCollected += whole.toLong()
    if (level >= content.tempo.maximumLevel) {
        chargeApexWithData(whole)
        return 0
    }
    data += whole
    return whole
}
