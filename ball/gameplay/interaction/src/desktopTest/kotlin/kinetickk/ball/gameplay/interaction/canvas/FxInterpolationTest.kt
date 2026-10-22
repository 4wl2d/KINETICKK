// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.test.Test
import kotlin.test.assertEquals

/** Transient looks age at the interpolated time (hud-fx §4.10), so they keep step with the interpolated world. */
class FxInterpolationTest {
    @Test
    fun fxAgeLagsOneStepAtAlphaZeroAndIsExactAtAlphaOne() {
        val base = hudTestModel()
        val step = GameplayRenderModel.FIXED_STEP
        assertEquals(0.3f, base.with("interpolationAlpha" to 1f).fxAge(life = 0.4f, maxLife = 0.7f), 1e-6f)
        assertEquals(0.3f - step, base.with("interpolationAlpha" to 0f).fxAge(life = 0.4f, maxLife = 0.7f), 1e-6f)
        assertEquals(0.3f - step * 0.5f, base.with("interpolationAlpha" to 0.5f).fxAge(life = 0.4f, maxLife = 0.7f), 1e-6f)
        // A look that has just started never ages backwards.
        assertEquals(0f, base.with("interpolationAlpha" to 0f).fxAge(life = 0.7f, maxLife = 0.7f))
        assertEquals(0f, base.with("interpolationAlpha" to 0.2f).fxAge(life = 0.699f, maxLife = 0.7f))
    }
}
