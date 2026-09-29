// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import kinetickk.ball.profile.interaction.catalogRebirthProfile
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel
import kinetickk.ball.profile.interaction.renderProbed
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The armed Rebirth screen (Confirm) runs hazard stripes along its top and bottom edges, also
 * when Advance is pinned on its band at the bottom (large text, small phones).
 */
class RebirthArmedStripeTest {
    private val model = RebirthRenderModel(catalogRebirthProfile(3), catalogRebirthProfile(4), true, 1_924L, 0, 10)
    private val accent = RebirthTheme(4, KkRolePalette.Default).accent

    @Test
    fun anArmedScreenKeepsBothHazardStripesWhenAdvanceIsPinned() {
        var pinned = 0
        for ((width, height) in listOf(390 to 844, 360 to 640, 844 to 390, 1440 to 810)) {
            val settings = listOf(1.25f, 1.75f)
            val holders = HashMap<Float, RebirthLayoutHolder>()
            renderProbed(width, height, settings, language = { AppLanguage.English }, content = { setting ->
                RebirthContent(model, confirmationArmed = true, textScale = setting, onAction = {}, timeSeconds = 7.3f,
                    holder = holders.getOrPut(setting) { RebirthLayoutHolder() })
            }) { setting, frame ->
                val context = "${width}x$height @$setting"
                val layout = assertNotNull(holders[setting]?.layout, context)
                if (layout.actionPinned) pinned++
                val stripe = rebirthArmedStripe(layout.frame)
                val pixels = frame.image.toPixelMap()
                // A row inside each 8 px edge band alternates the tier accent with ink along the width.
                for (y in listOf((stripe * 0.5f).toInt(), (height - stripe * 0.5f).toInt())) {
                    val lit = (0 until width).count { x -> near(pixels[x, y], accent) }
                    assertTrue(lit >= width * 0.25f, "$context row $y: $lit of $width px in the stripe accent (pinned ${layout.actionPinned})")
                }
            }
        }
        assertTrue(pinned >= 2, "$pinned armed screens with a pinned Advance")
    }

    private fun near(a: Color, b: Color): Boolean =
        abs(a.red - b.red) < 0.1f && abs(a.green - b.green) < 0.1f && abs(a.blue - b.blue) < 0.1f
}
