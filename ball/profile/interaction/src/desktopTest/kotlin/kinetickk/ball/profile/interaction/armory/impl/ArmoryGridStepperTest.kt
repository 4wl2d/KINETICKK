// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ArmoryGridStepperTest {
    @Test
    fun aPageStepAfterAUserScrollCancelledTheLastOneStillMovesTheGrid() = runSkikoComposeUiTest(Size(200f, 200f), Density(1f)) {
        mainClock.autoAdvance = false
        lateinit var grid: ScrollState
        lateinit var stepper: ArmoryGridStepper
        lateinit var scope: CoroutineScope
        setContent {
            grid = rememberScrollState()
            stepper = rememberArmoryGridStepper(grid)
            scope = rememberCoroutineScope()
            Box(Modifier.size(100.dp).verticalScroll(grid)) { Box(Modifier.size(100.dp, 260.dp)) }
        }
        mainClock.advanceTimeBy(32)
        // A 200 px viewport of 100 px rows: a forward step from the top reaches the end.
        fun forward() = armoryGridScrollTarget(grid.value.toFloat(), grid.maxValue.toFloat(), 200f, 100f, forward = true).toInt()
        val end = grid.maxValue
        assertEquals(end, runOnIdle { forward() })
        runOnIdle { stepper.step(forward()) }
        mainClock.advanceTimeBy(64)
        assertTrue(grid.value in 1 until end, "animating: ${grid.value}")

        // A wheel or drag scroll (user input) cancels the step's animation part way.
        runOnIdle { scope.launch { grid.scroll(MutatePriority.UserInput) { scrollBy(-10f) } } }
        mainClock.advanceTimeBy(600)
        assertTrue(grid.value < end, "cancelled at ${grid.value}")

        // The next forward step computes the same target as the cancelled one and still gets there.
        val again = runOnIdle { forward() }
        assertEquals(end, again)
        runOnIdle { stepper.step(again) }
        mainClock.advanceTimeBy(1_000)
        assertEquals(end, grid.value)
    }
}
