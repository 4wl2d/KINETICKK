// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus

import kinetickk.ball.profile.api.FRAME_RATE_CAP_OPTIONS
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.INTERFACE_SCALE_OPTIONS
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.REFRESH_RATE_RANGE
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.foundation.common.input.BindableKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferencesProjectionValidationTest {
    @Test
    fun displayAndControlPreferencesAreValidOnlyWithinTheirOptionSets() {
        assertTrue(GameplayNucleus.isValidPreferencesProjection(PlayerPreferences()))
        val listed = PlayerPreferences(
            windowMode = WindowMode.FULLSCREEN,
            windowSize = WindowSizePreset.W2560X1440,
            vsync = false,
            frameRateCap = FRAME_RATE_CAP_OPTIONS.last(),
            refreshRateHz = REFRESH_RATE_RANGE.first,
            immersive = true,
            interfaceScalePercent = INTERFACE_SCALE_OPTIONS.first(),
            showFps = true,
            pauseOnFocusLoss = false,
            reactionEffects = ReactionEffectsLevel.MINIMAL,
            keyBindings = KeyBindings.DEFAULT.with(GameAction.SKILL_1, BindableKey.SPACE),
        )
        assertTrue(GameplayNucleus.isValidPreferencesProjection(listed))
        assertTrue(GameplayNucleus.isValidPreferencesProjection(listed.copy(refreshRateHz = REFRESH_RATE_RANGE.last)))

        listOf(
            listed.copy(frameRateCap = 59),
            listed.copy(frameRateCap = FRAME_RATE_CAP_OPTIONS.last() + 1),
            listed.copy(refreshRateHz = REFRESH_RATE_RANGE.first - 1),
            listed.copy(refreshRateHz = REFRESH_RATE_RANGE.last + 1),
            listed.copy(interfaceScalePercent = INTERFACE_SCALE_OPTIONS.first() - 1),
            listed.copy(interfaceScalePercent = 101),
        ).forEach { invalid ->
            assertFalse(GameplayNucleus.isValidPreferencesProjection(invalid), invalid.toString())
        }
    }
}
