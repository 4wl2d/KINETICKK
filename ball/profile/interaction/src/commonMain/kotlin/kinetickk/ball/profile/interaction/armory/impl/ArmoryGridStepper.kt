// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Page steps for the weapon grid. Each step starts its own animation to its target: the effect
 * is keyed on the step count, so a step whose target equals an earlier one (whose animation a
 * drag or wheel scroll cancelled) still moves the grid.
 */
internal class ArmoryGridStepper {
    var steps by mutableIntStateOf(0)
        private set
    var target = 0
        private set

    fun step(target: Int) {
        this.target = target
        steps += 1
    }
}

@Composable
internal fun rememberArmoryGridStepper(gridScroll: ScrollState): ArmoryGridStepper {
    val stepper = remember { ArmoryGridStepper() }
    val steps = stepper.steps
    LaunchedEffect(steps) {
        if (steps > 0) gridScroll.animateScrollTo(stepper.target)
    }
    return stepper
}
