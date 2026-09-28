// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.lab.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toPixelMap
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.interaction.CatalogMetaUpgrades
import kinetickk.ball.profile.interaction.renderProbed
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A Lab list longer than its screen always shows that it scrolls, from either end: its scroll bar
 * is drawn beside the rows, visible, with the thumb at the end the list is at and the hidden rest
 * of the track on the other side. (A fold that falls between two rows left the fades on empty
 * rows' gap, so the list read as complete.)
 */
class LabListScrollBarTest {
    private class Scene(val language: AppLanguage, val setting: Float, val selected: MetaUpgradeId) {
        override fun toString() = "$language @$setting $selected"
    }

    private val model = LabProfileSnapshot(PlayerEconomy(matter = 1_284L), LabProgress(listOf(4, 5, 8, 2, 1, 2, 0, 1)))
        .toRenderModel(CatalogMetaUpgrades)

    @Test
    fun anOverflowingListShowsItsScrollBarAtBothEnds() {
        var overflowing = 0
        for ((width, height) in listOf(1440 to 810, 844 to 390, 390 to 844)) {
            // The first row selected opens the list at its start, the last one at its end.
            val scenes = listOf(AppLanguage.English, AppLanguage.Russian).flatMap { language ->
                listOf(1f, 1.25f, 1.75f).flatMap { setting ->
                    listOf(MetaUpgradeId.CORE_INTEGRITY, MetaUpgradeId.ARMORY_LICENSE).map { Scene(language, setting, it) }
                }
            }
            val holders = HashMap<Scene, LabLayoutHolder>()
            renderProbed(width, height, scenes, language = { it.language }, content = { scene ->
                LabContent(LabState(model, scene.selected), scene.setting, remember { ScrollState(0) }, holders.getOrPut(scene) { LabLayoutHolder() }) {}
            }) { scene, frame ->
                val context = "${width}x$height $scene"
                val layout = assertNotNull(holders[scene]?.layout, context)
                val list = layout.listViewport
                val thumb = frame.last("lab.list.thumb")
                if (layout.listScrollMax <= 0f) {
                    assertNull(thumb, "$context: a list that fits draws no scroll bar")
                    return@renderProbed
                }
                overflowing++
                assertNotNull(thumb, "$context: the list overflows by ${layout.listScrollMax} px but shows no scroll bar")
                val atEnd = scene.selected == MetaUpgradeId.ARMORY_LICENSE
                assertEquals(atEnd, (thumb.owner as Int) > 0, "$context scroll ${thumb.owner}")
                // The bar sits in the list's own gutter: right of every row, inside the viewport.
                val box = thumb.box.translate(list.left, list.top)
                val rowsRight = list.left + layout.rows.maxOf { it.right }
                assertTrue(box.left >= rowsRight && box.right <= list.right, "$context thumb $box beside rows ending at $rowsRight in $list")
                val track = layout.frame.d(LAB_LIST_PAD)
                assertTrue(box.top >= list.top + track - 0.5f && box.bottom <= list.bottom - track + 0.5f, "$context thumb $box")
                // The thumb is at the end the list shows; the track beyond it marks the hidden rows.
                if (atEnd) {
                    assertEquals(list.bottom - track, box.bottom, 0.5f, context)
                    assertTrue(box.top > list.top + track + 1f, "$context thumb $box fills the track")
                } else {
                    assertEquals(list.top + track, box.top, 0.5f, context)
                    assertTrue(box.bottom < list.bottom - track - 1f, "$context thumb $box fills the track")
                }
                // And it shows on screen: the thumb's middle column is bright over the dark list.
                val pixels = frame.image.toPixelMap()
                val x = box.center.x.toInt()
                val bright = (box.top.toInt() + 1 until box.bottom.toInt() - 1).count { y ->
                    pixels[x, y].let { max(it.red, max(it.green, it.blue)) } > 0.5f
                }
                assertTrue(bright >= (box.height - 2f) * 0.9f, "$context only $bright bright px in a ${box.height} px thumb")
            }
        }
        assertTrue(overflowing >= 20, "$overflowing overflowing lists")
    }
}
