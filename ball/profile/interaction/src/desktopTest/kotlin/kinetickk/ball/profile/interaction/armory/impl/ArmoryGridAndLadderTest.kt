// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.remember
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.CatalogWeapons
import kinetickk.ball.profile.interaction.ProbedFrame
import kinetickk.ball.profile.interaction.ProfileDrawnText
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.profileFrame
import kinetickk.ball.profile.interaction.renderProbed
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.kkBoxBottom
import kinetickk.foundation.design.kkBoxTop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Armory's grid and detail panel drawn with the bundled fonts and the game's copy, in English
 * and Russian, from the smallest to the largest text size: every tile keeps one icon size, the
 * mastery ladder keeps each milestone's bonus pair apart from its neighbours, and the outlined
 * background word stays inside the frame.
 */
class ArmoryGridAndLadderTest {
    private class Scene(val language: AppLanguage, val setting: Float, val inspected: WeaponId) {
        override fun toString() = "$language @$setting $inspected"
    }

    private val model = ArmoryRenderModel(1_284L, WeaponId.FLUX_WAKE, CatalogWeapons.take(7).map { it.id }.toImmutableSet(), WeaponId.ION_SWARM)
    private val languages = listOf(AppLanguage.English, AppLanguage.Russian)
    private val settings = listOf(1f, 1.25f, 1.5f, 1.75f)

    private fun render(width: Int, height: Int, scenes: List<Scene>, check: (Scene, ArmoryLayout, ProbedFrame) -> Unit) {
        val holders = HashMap<Scene, ArmoryLayoutHolder>()
        renderProbed(width, height, scenes, language = { it.language }, content = { scene ->
            ArmoryContent(model, CatalogWeapons, WeaponMastery.entries, ArmoryViewState(scene.inspected), scene.setting,
                remember { ScrollState(0) }, holders.getOrPut(scene) { ArmoryLayoutHolder() }) {}
        }) { scene, frame -> check(scene, assertNotNull(holders[scene]?.layout, "$scene"), frame) }
    }

    private fun scenes(inspected: WeaponId) = languages.flatMap { language -> settings.map { Scene(language, it, inspected) } }

    @Test
    fun everyTileDrawsItsIconAtOneSizeBetweenItsStatusAndName() {
        for ((width, height) in listOf(1440 to 810, 844 to 390, 390 to 844, 360 to 640)) {
            render(width, height, scenes(WeaponId.MORNINGSTAR)) { scene, layout, frame ->
                val context = "${width}x$height $scene"
                val icons = frame.latest("armory.tile.icon")
                val names = CatalogWeapons.map { it.name.localizedContent(scene.language) }
                // Every weapon's tile draws its icon, whatever the text size.
                assertEquals(names.toSet(), icons.map { it.owner }.toSet(), context)
                // One size for the whole grid, never below the smallest tile icon.
                val sizes = icons.map { it.box.width }
                assertTrue(sizes.max() - sizes.min() <= 1f, "$context icon sizes ${icons.map { "${it.owner}=${it.box.width}" }}")
                val board = layout.frame.d(armoryType(layout.frame.mode).tileIcon)
                assertTrue(sizes.min() >= board * ARMORY_TILE_ICON_MIN - 0.01f, "$context icons ${sizes.min()} < ${board * ARMORY_TILE_ICON_MIN}")
                for (icon in icons) {
                    // The icon lies inside its tile, below the status line and above the name.
                    val tile = layout.tiles[names.indexOf(icon.owner)]
                    assertTrue(icon.box.top >= 0f && icon.box.bottom <= tile.height && icon.box.left >= 0f && icon.box.right <= tile.width,
                        "$context ${icon.owner} icon ${icon.box} outside the ${tile.width}x${tile.height} tile")
                    val status = frame.latest("armory.tile.status").single { it.owner == icon.owner }
                    val name = frame.latest("armory.tile.name").single { it.owner == icon.owner }
                    assertTrue(icon.box.top >= lineBottom(status), "$context ${icon.owner} icon ${icon.box} over its status ${lineBottom(status)}")
                    assertTrue(icon.box.bottom <= lineTop(name), "$context ${icon.owner} icon ${icon.box} over its name ${lineTop(name)}")
                }
            }
        }
    }

    @Test
    fun sideBySideMilestonesKeepTheirBonusPairsClearlyApart() {
        var sideBySide = 0
        for ((width, height) in listOf(1440 to 810, 844 to 390, 390 to 844, 720 to 360)) {
            render(width, height, scenes(WeaponId.MORNINGSTAR)) { scene, layout, frame ->
                val context = "${width}x$height $scene"
                // Each bonus value as drawn: its milestone, line and horizontal extent.
                val values = frame.latest("armory.ladder.bonus").flatMap { values(it) }
                assertEquals(WeaponMastery.entries.size, values.map { it.milestone }.toSet().size, context)
                // Values whose line boxes overlap share a line.
                val lines = ArrayList<MutableList<Value>>()
                for (value in values.sortedBy { it.top }) {
                    val line = lines.lastOrNull()
                    if (line != null && value.top < line.maxOf { it.bottom }) line += value else lines += mutableListOf(value)
                }
                if (lines.size == 1) {
                    sideBySide++
                    assertFalse(layout.ladderStacked, context)
                    val groups = values.groupBy { it.milestone }.toSortedMap().values.map { group -> group.sortedBy { it.left } }
                    // The widest gap inside a pair ("+25%  +16%") …
                    val inner = groups.maxOf { group -> group.zipWithNext { a, b -> b.left - a.right }.maxOrNull() ?: 0f }
                    assertTrue(inner > 0f, context)
                    // … is at most half the gap between two milestones' groups.
                    groups.zipWithNext { a, b ->
                        val between = b.first().left - a.last().right
                        assertTrue(between >= inner * 2f, "$context milestones ${a.first().milestone}/${b.first().milestone}: " +
                            "$between px apart, pairs $inner px inside")
                    }
                } else {
                    // Stacked: one milestone per line.
                    assertTrue(layout.ladderStacked, context)
                    assertEquals(WeaponMastery.entries.size, lines.size, context)
                }
            }
        }
        assertTrue(sideBySide >= 8, "$sideBySide side-by-side ladders")
    }

    @Test
    fun theOutlinedBackgroundWordEndsInsideTheFrame() {
        for ((width, height) in listOf(1440 to 810, 1920 to 1080, 1280 to 720)) {
            val frame = profileFrame(width.toFloat(), height.toFloat(), 1f)
            render(width, height, languages.map { Scene(it, 1.25f, WeaponId.FLUX_WAKE) }) { scene, _, probed ->
                val context = "${width}x$height $scene"
                val word = assertNotNull(probed.last("armory.background.word"), context)
                val layout = assertNotNull(word.layout)
                assertFalse(layout.hasVisualOverflow, context)
                // Like the board's word: from x 40, ending at least 20 px inside the right edge.
                assertEquals(frame.x(40f), word.box.left, 0.5f, context)
                assertTrue(word.box.right <= frame.x(ARMORY_WORD_RIGHT) + 0.5f, "$context word ends at ${word.box.right}")
                // A title that fits keeps the board's 230 px (English "ARMORY").
                if (scene.language == AppLanguage.English) assertEquals(230f * frame.k, layout.layoutInput.style.fontSize.value, 0.01f, context)
            }
        }
    }

    private class Value(val milestone: Int, val top: Float, val bottom: Float, val left: Float, val right: Float)

    /** The drawn values of one ladder bonus text (split at spaces, as the old joined pair was drawn). */
    private fun values(text: ProfileDrawnText): List<Value> {
        val layout = assertNotNull(text.layout)
        val shown = layout.layoutInput.text.text
        val result = ArrayList<Value>()
        var start = -1
        for (index in 0..shown.length) {
            val space = index == shown.length || shown[index].isWhitespace()
            if (!space && start < 0) start = index
            if (space && start >= 0) {
                val left = layout.getBoundingBox(start).left
                val right = layout.getBoundingBox(index - 1).right
                result += Value(text.owner as Int, text.box.top + layout.kkBoxTop, text.box.top + layout.kkBoxBottom,
                    text.box.left + left, text.box.left + right)
                start = -1
            }
        }
        return result
    }

    /** Bottom of a drawn single line's CSS line box. */
    private fun lineBottom(text: ProfileDrawnText): Float = text.box.top + assertNotNull(text.layout).kkBoxBottom

    /** Top of a drawn text's CSS line box. */
    private fun lineTop(text: ProfileDrawnText): Float = text.box.top + assertNotNull(text.layout).kkBoxTop
}
