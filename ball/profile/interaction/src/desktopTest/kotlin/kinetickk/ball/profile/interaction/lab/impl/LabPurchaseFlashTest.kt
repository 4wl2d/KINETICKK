// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.lab.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.interaction.CatalogMetaUpgrades
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LabPurchaseFlashTest {
    @Test
    fun theInvertedFrameKeepsTheRowsShearedCornersAtTheBackground() {
        val model = LabProfileSnapshot(PlayerEconomy(matter = 1_284L), LabProgress(listOf(4, 5, 8, 2, 1, 2, 0, 1)))
            .toRenderModel(CatalogMetaUpgrades)
        for ((width, height) in listOf(1440 to 810, 844 to 390, 390 to 844)) {
            runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), Density(1f)) {
                mainClock.autoAdvance = false
                val holder = LabLayoutHolder()
                // The first row (not the selected one) just bought a rank: its bone slab inverts.
                setContent {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag("lab")) {
                        LabContent(LabState(model, MetaUpgradeId.KINETIC_AMPLIFIER, LabPurchaseFlash(MetaUpgradeId.CORE_INTEGRITY, 1)), 1.25f,
                            remember { ScrollState(0) }, holder) {}
                    }
                }
                mainClock.advanceTimeBy(16)
                val pixels = onNodeWithTag("lab").captureToImage().toPixelMap()
                val list = holder.layout!!.listViewport
                val row = holder.layout!!.rows.first().translate(list.left, list.top)
                fun brightness(x: Float, y: Float): Float = pixels[x.toInt(), y.toInt()].let { max(it.red, max(it.green, it.blue)) }
                val context = "${width}x$height $row"
                // Inside the slab the inverted frame is bone…
                assertTrue(brightness(row.center.x, row.top + 3f) > 0.8f, "$context slab ${pixels[row.center.x.toInt(), (row.top + 3f).toInt()]}")
                // …while the corners the −12° shear leaves out stay the dark list background.
                for ((x, y) in listOf(row.left + 1f to row.top + 1f, row.right - 2f to row.bottom - 2f)) {
                    assertTrue(brightness(x, y) < 0.2f, "$context corner $x,$y ${pixels[x.toInt(), y.toInt()]}")
                }
            }
        }
    }
}
