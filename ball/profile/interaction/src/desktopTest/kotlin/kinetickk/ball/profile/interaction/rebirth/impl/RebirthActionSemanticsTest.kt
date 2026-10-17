// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.profile.interaction.catalogRebirthProfile
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The select button acts only when the tier can be selected: a closed shell (a lost or running
 * run) and a tier above the rank show an inert "Locked" button that ignores presses.
 */
@OptIn(ExperimentalTestApi::class)
class RebirthActionSemanticsTest {
    private fun model(eligible: Boolean, focus: Int) = RebirthRenderModel(
        current = catalogRebirthProfile(2),
        focus = catalogRebirthProfile(focus),
        highestCleared = 2,
        eligible = eligible,
        matter = 1_924L,
        minimumTier = 0,
        maximumTier = 10,
    )

    @Test
    fun onlyASelectableTierHasALiveSelectButton() = runSkikoComposeUiTest(Size(1440f, 810f), Density(1f)) {
        val scene = mutableStateOf(model(eligible = false, focus = 3))
        val actions = ArrayList<RebirthAction>()
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(1440.dp, 810.dp)) {
                    RebirthContent(scene.value, confirmationArmed = false, textScale = 1.25f, onAction = { actions += it }, timeSeconds = 7.3f)
                }
            }
        }
        // Closed shell: the rank allows tier 3, but the shell is not eligible.
        assertEquals(RebirthActionState.UNAVAILABLE, scene.value.actionState(confirmationArmed = false))
        onNodeWithTag("profile-rebirth-advance").assertIsNotEnabled().performClick()
        // Above the rank.
        runOnIdle { scene.value = model(eligible = true, focus = 4) }
        onNodeWithTag("profile-rebirth-advance").assertIsNotEnabled().performClick()
        assertEquals(emptyList(), actions)
        // A selectable tier acts.
        runOnIdle { scene.value = model(eligible = true, focus = 3) }
        onNodeWithTag("profile-rebirth-advance").assertIsEnabled().performClick()
        assertEquals(listOf<RebirthAction>(RebirthAction.SelectRequested), actions)
    }
}
