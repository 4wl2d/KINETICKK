// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.armory.impl.ArmoryContent
import kinetickk.ball.profile.interaction.armory.impl.ArmoryLayoutHolder
import kinetickk.ball.profile.interaction.armory.impl.ArmoryViewState
import kinetickk.ball.profile.interaction.armory.impl.armoryLadderCellsTop
import kinetickk.ball.profile.interaction.lab.impl.LabContent
import kinetickk.ball.profile.interaction.lab.impl.LabLayoutHolder
import kinetickk.ball.profile.interaction.lab.impl.LabState
import kinetickk.ball.profile.interaction.lab.impl.toRenderModel
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel
import kinetickk.ball.profile.interaction.rebirth.impl.RebirthContent
import kinetickk.ball.profile.interaction.rebirth.impl.RebirthLayoutHolder
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.kkBoxHeight
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Renders the Armory, Lab and Rebirth screens with the bundled fonts and the game's copy at the
 * three reference frames, in English and Russian, at text sizes 100 %, 125 % (default) and
 * 175 %, and checks every text they draw: nothing is clipped, ellipsized or split inside a word,
 * and the reviewed layouts keep their text whole and in place.
 */
@OptIn(ExperimentalTestApi::class)
class ProfileTextFitTest {
    private val sizes = listOf(1440 to 810, 844 to 390, 390 to 844)
    private val languages = listOf(AppLanguage.English, AppLanguage.Russian)
    private val settings = listOf(1f, 1.25f, 1.75f)

    private class Scene<T>(val language: AppLanguage, val setting: Float, val item: T) {
        override fun toString() = "$language @${setting} $item"
    }

    private class Frame(val texts: List<ProfileDrawnText>, val image: ImageBitmap) {
        fun of(role: String) = texts.filter { it.role == role }
    }

    private fun <T> scenes(items: List<T>) = languages.flatMap { language -> settings.flatMap { setting -> items.map { Scene(language, setting, it) } } }

    /** Renders each scene at [width]×[height] (density 1) and hands its drawn texts and pixels to [check]. */
    private fun <T> render(
        width: Int,
        height: Int,
        scenes: List<Scene<T>>,
        content: @Composable (Scene<T>) -> Unit,
        check: (Scene<T>, Frame) -> Unit,
    ) = runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), Density(1f)) {
        mainClock.autoAdvance = false
        val current = mutableIntStateOf(-1)
        setContent {
            val index = current.intValue
            val scene = scenes.getOrNull(index)
            if (scene != null) key(index) {
                CompositionLocalProvider(LocalAppLanguage provides scene.language) {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag("profile-root")) { content(scene) }
                }
            }
        }
        val drawn = ArrayList<ProfileDrawnText>()
        ProfileTextProbe.sink = { drawn += it }
        try {
            scenes.forEachIndexed { index, scene ->
                drawn.clear()
                runOnIdle { current.intValue = index }
                mainClock.advanceTimeBy(64)
                waitForIdle()
                val image = onNodeWithTag("profile-root").captureToImage()
                check(scene, Frame(drawn.toList(), image))
            }
        } finally {
            ProfileTextProbe.sink = null
        }
    }

    /** No drawn text is clipped, ellipsized or split inside a word. */
    private fun assertWhole(frame: Frame, context: String) {
        assertTrue(frame.texts.any { it.layout != null }, "$context drew no text")
        for (text in frame.texts) {
            val layout = text.layout ?: continue
            val shown = layout.layoutInput.text.text
            assertFalse(layout.hasVisualOverflow, "$context ${text.role} '$shown' overflows its box: ${layout.size} in " +
                "${layout.layoutInput.constraints} w=${layout.multiParagraph.width} h=${layout.multiParagraph.height}")
            assertTrue((0 until layout.lineCount).none { layout.isLineEllipsized(it) }, "$context ${text.role} '$shown' is ellipsized")
            assertFalse(layout.breaksWord(), "$context ${text.role} '$shown' splits a word across lines")
        }
    }

    private fun fontSize(text: ProfileDrawnText): Float = assertNotNull(text.layout).layoutInput.style.fontSize.value

    @Test
    fun armoryTextsStayWholeAndInsideTheDetailPanel() {
        val model = ArmoryRenderModel(1_284L, WeaponId.FLUX_WAKE, CatalogWeapons.take(7).map { it.id }.toImmutableSet(), WeaponId.ION_SWARM)
        for ((width, height) in sizes) {
            val holders = HashMap<Scene<WeaponId>, ArmoryLayoutHolder>()
            val tileNames = HashMap<Triple<AppLanguage, Float, Any?>, Float>()
            render(width, height, scenes(CatalogWeapons.map { it.id }), content = { scene ->
                ArmoryContent(model, CatalogWeapons, WeaponMastery.entries, ArmoryViewState(scene.item), scene.setting,
                    remember { ScrollState(0) }, holders.getOrPut(scene) { ArmoryLayoutHolder() }) {}
            }) { scene, frame ->
                val context = "${width}x$height $scene"
                assertWhole(frame, context)
                assertTrue(frame.of("header.title").isNotEmpty(), "$context header title")
                val layout = assertNotNull(holders[scene]?.layout, context)
                val weapon = CatalogWeapons.first { it.id == scene.item }

                // The detail name block (two lines at large text) stays inside the scrolling panel
                // above the description, and no glyph row touches the panel's clipped top edge.
                val name = frame.of("armory.detail.name").single()
                val viewport = layout.detailViewport
                assertTrue(name.box.top >= viewport.top + 4f, "$context name top ${name.box.top} vs panel ${viewport.top}")
                assertTrue(name.box.bottom <= layout.description.top, "$context name ${name.box} runs into the description ${layout.description}")
                assertNoInkRows(frame.image, name.box.left, name.box.right, viewport.top + 1f, 2, context)

                // Every tag, and the in-run tag, is drawn inside the tag rows.
                val expected = weapon.tags.map { it.localizedContent(scene.language) } +
                    listOfNotNull(if (weapon.id == model.activeRunWeapon) scene.language.text(ProfileScreensRedesignText.ActiveRun) else null)
                val tags = frame.of("armory.detail.tag")
                assertEquals(expected.toSet(), tags.map { it.owner }.toSet(), context)
                tags.forEach {
                    assertTrue(it.box.left >= layout.tags.left - 0.5f && it.box.right <= layout.tags.right + 0.5f &&
                        it.box.bottom <= layout.tags.bottom + 0.5f, "$context tag ${it.owner} ${it.box} outside ${layout.tags}")
                }
                assertTrue(layout.description.bottom <= layout.tags.top, context)
                // The description slot is the wrapped text (or the board's shorter slot): no blank
                // lines are reserved above the tags and ladder.
                val description = frame.of("armory.detail.description").single()
                val boardSlot = layout.frame.d(if (width == 1440) 18f else 14f) * 1.4f * (if (width == 1440) 3f else 2f)
                assertEquals(max(boardSlot, assertNotNull(description.layout).kkBoxHeight), layout.description.height, 0.5f, context)

                // At 175 % on the board frame all milestones are visible at rest; on phones the
                // first ladder line (the level cells) starts above the pinned action.
                if (scene.setting == 1.75f) {
                    if (width == 1440) {
                        val milestones = frame.of("armory.ladder.name")
                        assertEquals(WeaponMastery.entries.size, milestones.size, context)
                        milestones.forEach { assertTrue(it.box.bottom <= viewport.bottom, "$context milestone ${it.box} below ${viewport.bottom}") }
                    }
                    if (width == 390) {
                        val cells = layout.ladder.top + armoryLadderCellsTop(layout.frame, profileTextScale(scene.setting))
                        assertTrue(cells + layout.frame.d(10f) <= viewport.bottom, "$context ladder cells $cells below ${viewport.bottom}")
                    }
                }
                frame.of("armory.tile.name").forEach { tileNames[Triple(scene.language, scene.setting, it.owner)] = fontSize(it) }
                // The default text size (125 %) draws UI text at the board's size (tile status: 11 px on the 1440 board).
                if (width == 1440 && scene.setting == 1.25f && scene.language == AppLanguage.English) {
                    frame.of("armory.tile.status").forEach { assertEquals(11f, fontSize(it), 0.01f, "$context ${it.owner}") }
                }
            }
            // Large text never makes a tile name smaller than it is at 100 % text.
            assertLargeTextNotSmaller(tileNames, "armory tiles ${width}x$height")
            if (width != 1440) {
                // "Гравитационные мины" keeps both words whole in the phone tile at 175 %.
                assertNotNull(tileNames[Triple(AppLanguage.Russian, 1.75f, "Гравитационные мины")], "${width}x$height")
            }
        }
    }

    @Test
    fun labTextsStayWholeAndTheMaxStampClearsTheValues() {
        val model = LabProfileSnapshot(PlayerEconomy(matter = 1_284L), LabProgress(listOf(4, 5, 8, 2, 1, 2, 0, 1)))
            .toRenderModel(CatalogMetaUpgrades)
        for ((width, height) in sizes) {
            val holders = HashMap<Scene<MetaUpgradeId>, LabLayoutHolder>()
            val rowNames = HashMap<Triple<AppLanguage, Float, Any?>, Float>()
            render(width, height, scenes(CatalogMetaUpgrades.map { it.id }), content = { scene ->
                LabContent(LabState(model, scene.item), scene.setting, remember { ScrollState(0) }, holders.getOrPut(scene) { LabLayoutHolder() }) {}
            }) { scene, frame ->
                val context = "${width}x$height $scene"
                assertWhole(frame, context)
                assertTrue(frame.of("header.title").isNotEmpty(), "$context header title")
                val layout = assertNotNull(holders[scene]?.layout, context)
                val columns = layout.columns
                val stamps = frame.of("lab.row.stamp")
                assertEquals(1, stamps.map { it.owner }.toSet().size, "$context one maxed upgrade")
                for (stamp in stamps) {
                    // The stamp fits the cost column and, rotated −6°, stays clear of the value.
                    assertTrue(stamp.box.width <= columns.costRight - columns.costLeft + 0.5f, "$context stamp ${stamp.box.width}")
                    val stampBounds = rotatedBounds(stamp.box, -6f)
                    frame.of("lab.row.value").filter { it.owner == stamp.owner }.forEach {
                        assertFalse(stampBounds.overlaps(it.box), "$context stamp $stampBounds covers the value ${it.box}")
                    }
                }
                // The description slot is the wrapped text: no blank lines push the panels down.
                val description = frame.of("lab.detail.description").single()
                assertEquals(assertNotNull(description.layout).kkBoxHeight, layout.description.height, 0.5f, context)
                // At rest at 175 % on the landscape phone the Now and Next values are visible.
                if (width == 844 && scene.setting == 1.75f) {
                    val values = frame.of("lab.panel.value")
                    assertEquals(2, values.size, context)
                    values.forEach { assertTrue(it.box.bottom <= layout.detailViewport.bottom, "$context value ${it.box} under the pinned action") }
                }
                frame.of("lab.row.name").forEach { rowNames[Triple(scene.language, scene.setting, it.owner)] = fontSize(it) }
            }
            assertLargeTextNotSmaller(rowNames, "lab rows ${width}x$height")
        }
    }

    @Test
    fun rebirthTextsStayWholeAndTheGoalTierReads() {
        val targets = (1..10).toList() + 11
        for ((width, height) in sizes) {
            val holders = HashMap<Scene<Int>, RebirthLayoutHolder>()
            fun model(target: Int) = if (target > 10) {
                RebirthRenderModel(catalogRebirthProfile(10), catalogRebirthProfile(10), false, 1_924L, 0, 10)
            } else {
                RebirthRenderModel(catalogRebirthProfile(target - 1), catalogRebirthProfile(target), true, 1_924L, 0, 10)
            }
            render(width, height, scenes(targets), content = { scene ->
                RebirthContent(model(scene.item), false, scene.setting, {}, timeSeconds = 7.3f,
                    holder = holders.getOrPut(scene) { RebirthLayoutHolder() })
            }) { scene, frame ->
                val context = "${width}x$height $scene"
                assertWhole(frame, context)
                // Both section titles and the target's directive (Swarm, Fortified, Overclocked) are
                // drawn whole: fitted within their room, with their text unchanged.
                val language = scene.language
                assertEquals(listOf(ProfileText.HostileEscalation, ProfileText.CycleCompensation).map { language.text(it).uppercase() },
                    frame.of("rebirth.section").map { assertNotNull(it.layout).layoutInput.text.text }.distinct(), context)
                val target = model(scene.item).let { if (it.isMaximumTier) it.current else it.next }
                assertEquals(listOf(target.directive.displayName.localizedContent(language).uppercase()),
                    frame.of("rebirth.directive").map { assertNotNull(it.layout).layoutInput.text.text }.distinct(), context)
                val layout = assertNotNull(holders[scene]?.layout, context)
                // Every ladder numeral lies inside its cell.
                val numbers = frame.of("rebirth.ladder.number")
                assertEquals(layout.cells.size, numbers.size, context)
                numbers.forEach {
                    val cell = layout.cells[it.owner as Int]
                    assertTrue(it.box.left >= cell.left && it.box.right <= cell.right, "$context tier ${it.owner} ${it.box} outside $cell")
                }
                // Narrow cells put the striped goal tier on a solid chip with ≥ 3:1 contrast.
                val goal = numbers.single { it.owner == 10 }
                val chip = frame.of("rebirth.ladder.chip").singleOrNull()
                if (width != 1440) {
                    assertNotNull(chip, context)
                    val cell = layout.cells[10]
                    assertTrue(chip.box.left >= cell.left && chip.box.right <= cell.right && chip.box.top >= cell.top &&
                        chip.box.bottom <= cell.bottom, "$context chip ${chip.box} outside $cell")
                    assertTrue(goal.box.left >= chip.box.left && goal.box.right <= chip.box.right, "$context numeral ${goal.box} off the chip ${chip.box}")
                    assertTrue(contrast(goal.color, chip.color) >= 3f, "$context contrast ${contrast(goal.color, chip.color)}")
                }
            }
        }
    }

    private fun assertLargeTextNotSmaller(sizes: Map<Triple<AppLanguage, Float, Any?>, Float>, context: String) {
        val small = sizes.filterKeys { it.second == 1f }
        assertTrue(small.isNotEmpty(), context)
        for ((key, size) in small) {
            val large = assertNotNull(sizes[Triple(key.first, 1.75f, key.third)], "$context ${key.third}")
            assertTrue(large >= size - 0.01f, "$context '${key.third}' ${key.first}: $large px at 175 % < $size px at 100 %")
        }
    }

    /** No bright (text) pixel in [rows] rows from [top] between [left] and [right]. */
    private fun assertNoInkRows(image: ImageBitmap, left: Float, right: Float, top: Float, rows: Int, context: String) {
        val pixels = image.toPixelMap()
        val y0 = top.toInt().coerceIn(0, image.height - 1)
        for (y in y0 until (y0 + rows).coerceAtMost(image.height)) {
            for (x in left.toInt().coerceAtLeast(0) until right.toInt().coerceAtMost(image.width)) {
                val color = pixels[x, y]
                assertTrue(max(color.red, max(color.green, color.blue)) < 0.5f, "$context ink at $x,$y (clipped text): $color")
            }
        }
    }

    private fun rotatedBounds(rect: Rect, degrees: Float): Rect {
        val radians = Math.toRadians(degrees.toDouble())
        val w = (rect.width * abs(cos(radians)) + rect.height * abs(sin(radians))).toFloat()
        val h = (rect.width * abs(sin(radians)) + rect.height * abs(cos(radians))).toFloat()
        return Rect(rect.center.x - w / 2f, rect.center.y - h / 2f, rect.center.x + w / 2f, rect.center.y + h / 2f)
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }
}
