// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.flow.session.interaction.TestRebirthPolicy
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** Home's selected menu item and form showcase, checked on the pixels they draw with the bundled fonts. */
@OptIn(ExperimentalTestApi::class)
class HomeMenuPixelComposeTest {
    @Test
    fun theSelectedSlabKeepsItsSizeAndHoldsItsLabelAtEveryTextSize() {
        val probes = HashMap<String, SlabProbe>()
        for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.25f, 1.75f)) withMenu(language, textScale) { menu ->
            for ((width, height) in OneColumnViewports) {
                val layout = homeLayoutGeometry(width.toFloat(), height.toFloat(), 1f)
                val stampSize = homeMenuStampSize(menu, layout, 1f)
                val k = layout.scene.menuFontSize / 64f
                HomeMenuTargets.forEachIndexed { index, target ->
                    val label = language.text(HomeMenuLabels[index])
                    val facts = homeFacts(StampModel, target, language, textScale)
                    val placement = homeMenuPlacement(menu, layout.bounds(target), label, facts.sub, facts.stamp, layout.scene.menuFontSize, stampSize, 1f)
                    val image = rendered(width, height) {
                        drawHomeMenuItem(menu, layout, index, label, facts, placement.left, placement.showSub, stampSize, 1f, HOME_TRAIL_REST_SECONDS)
                    }
                    // The label's columns once the selected item has moved left.
                    val labelLeft = placement.left + (22f - HOME_MENU_SLIDE) * k
                    val labelWidth = measureKkText(menu, label, menu.typography.condStyle(layout.scene.menuFontSize / menu.scale, lineHeightEm = 1f),
                        uppercase = true).size.width
                    probes["$target ${language.code} ${width}x$height @$textScale"] =
                        slabProbe(image, (labelLeft + 2f).toInt(), (labelLeft + labelWidth - 2f).toInt(), "$target ${language.code} ${width}x$height @$textScale")
                }
            }
        }
        probes.filterKeys { it.endsWith("@1.25") }.forEach { (key, reference) ->
            for (scale in listOf("1.0", "1.75")) {
                val probe = requireNotNull(probes[key.removeSuffix("1.25") + scale])
                val where = "${key.removeSuffix("@1.25")} at $scale vs 1.25: $probe vs $reference"
                assertTrue(abs(probe.height - reference.height) <= 1, "slab height changed: $where")
                assertTrue(probe.top > 0 && probe.bottom > 0, "label ink leaves the slab: $where")
                assertTrue(probe.top >= reference.top - 1 && probe.bottom >= reference.bottom - 1, "label clearance shrank: $where")
            }
        }
    }

    @Test
    fun aSelectedItemKeepsItsStampRightAfterTheLabelAndAddsItsCountAfterIt() {
        for (language in AppLanguage.entries) withMenu(language, 1.25f) { menu ->
            for ((width, height) in listOf(1_440 to 810, 844 to 390, 390 to 844)) {
                val layout = homeLayoutGeometry(width.toFloat(), height.toFloat(), 1f)
                val stampSize = homeMenuStampSize(menu, layout, 1f)
                val k = layout.scene.menuFontSize / 64f
                val index = HomeMenuTargets.indexOf(HomeLayoutTarget.ARMORY)
                val label = language.text(HomeMenuLabels[index])
                val facts = homeFacts(StampModel, HomeLayoutTarget.ARMORY, language, 1.25f)
                val bounds = layout.bounds(HomeLayoutTarget.ARMORY)
                val placement = homeMenuPlacement(menu, bounds, label, facts.sub, facts.stamp, layout.scene.menuFontSize, stampSize, 1f)
                val where = "${language.code} ${width}x$height"
                assertTrue(facts.stamp != null && facts.sub != null && placement.showSub, "armory needs its stamp and count: $where")
                fun image(selection: Float, sub: Boolean) = rendered(width, height) {
                    drawHomeMenuItem(menu, layout, index, label, facts, placement.left, sub, stampSize, selection, HOME_TRAIL_REST_SECONDS)
                }
                val labelWidth = measureKkText(menu, label, menu.typography.condStyle(layout.scene.menuFontSize / menu.scale, lineHeightEm = 1f),
                    uppercase = true).size.width
                val band = (bounds.center.y - 16f * k).toInt()..(bounds.center.y + 16f * k).toInt()
                val you = menu.roles.you.toArgb()
                fun stampColumns(image: Frame, labelEnd: Float): IntRange {
                    val columns = (labelEnd.toInt() until image.width).filter { x -> band.any { y -> near(image.pixel(x, y), you) } }
                    if (columns.isEmpty()) fail("no stamp drawn: $where")
                    return columns.first()..columns.last()
                }
                val selectedEnd = placement.left + (22f - HOME_MENU_SLIDE) * k + labelWidth + 4f * k
                val withCount = image(1f, true)
                val stamp = stampColumns(withCount, selectedEnd)
                val alone = stampColumns(image(1f, false), selectedEnd)
                val resting = stampColumns(image(0f, false), placement.left + 26f * k + labelWidth)
                assertTrue(abs(stamp.first - alone.first) <= 1, "the count moved the stamp from ${alone.first} to ${stamp.first}: $where")
                assertTrue(abs(resting.first - stamp.first - HOME_MENU_SLIDE * k) <= 2f,
                    "selecting moved the stamp from ${resting.first} to ${stamp.first}, not by the slide: $where")
                // The count (ink on the slab) follows the stamp and its shadow; nothing sits between the label and the stamp.
                val dark = (0 until withCount.width).filter { x -> band.any { y -> isInk(withCount.pixel(x, y)) } }
                assertTrue(dark.any { it > stamp.last + 6 }, "no count after the stamp: $where")
                assertTrue(dark.none { it > selectedEnd && it < stamp.first }, "ink between the label and the stamp: $where")
            }
        }
    }

    @Test
    fun theTwoColumnSelectionKeepsItsSlabCutAndLeadBeforeTheLabel() {
        for (language in AppLanguage.entries) for (textScale in listOf(1.25f, 1.75f)) withMenu(language, textScale) { menu ->
            for ((width, height) in TwoColumnViewports) {
                val layout = homeLayoutGeometry(width.toFloat(), height.toFloat(), 1f)
                assertTrue(layout.scene.menuColumns == 2, "two columns at ${width}x$height")
                val k = layout.scene.menuFontSize / 64f
                fun item(index: Int, selection: Float) = rendered(width, height) {
                    val target = HomeMenuTargets[index]
                    drawHomeMenuItem(menu, layout, index, language.text(HomeMenuLabels[index]), homeFacts(StampModel, target, language, textScale),
                        layout.bounds(target).left, false, Float.NaN, selection, HOME_TRAIL_REST_SECONDS)
                }
                HomeMenuTargets.forEachIndexed { index, target ->
                    val where = "$target ${language.code} ${width}x$height @$textScale"
                    val bounds = layout.bounds(target)
                    val band = (bounds.top.toInt() - 2)..(bounds.bottom.toInt() + 2)
                    // At rest the label alone is drawn, whole inside its column (the clip would cut a longer one).
                    val label = paintedColumns(item(index, 0f), band) ?: fail("no label: $where")
                    assertTrue(label.last < bounds.right, "label runs to ${label.last} past its column's ${bounds.right}: $where")
                    // Selected, the item keeps 4 px clear of the other column's label in its row, on either side.
                    val image = item(index, 1f)
                    val selected = paintedColumns(image, band) ?: fail("nothing selected: $where")
                    val neighbour = paintedColumns(item(index xor 1, 0f), band) ?: fail("no neighbour label: $where")
                    val clear = if (index % 2 == 1) selected.first - neighbour.last - 1 else neighbour.first - selected.last - 1
                    assertTrue(clear >= 4, "selected ${selected.first}..${selected.last}, neighbour label ${neighbour.first}..${neighbour.last}: $where")
                    val rows = (bounds.top.toInt()..bounds.bottom.toInt()).filter { y -> (0 until width).any { isBone(image.pixel(it, y)) } }
                    if (rows.isEmpty()) fail("no slab: $where")
                    fun slabLeft(y: Int) = (0 until width).first { isBone(image.pixel(it, y)) }
                    val slabLeft = rows.minOf { slabLeft(it) }
                    val labelLeft = (0 until width).first { x -> rows.any { y -> isInk(image.pixel(x, y)) } }
                    // The item's lead (22 px at font 64), less the two antialiased edges.
                    assertTrue(labelLeft - slabLeft >= 22f * k - 2.5f, "label at $labelLeft, slab from $slabLeft: $where")
                    // The skewed cut: the slab's left edge runs right going up by about the cut.
                    val skew = slabLeft(rows.first() + 2) - slabLeft(rows.last() - 2)
                    assertTrue(skew >= 11f * k, "slab's left edge leans $skew px: $where")
                }
            }
        }
    }

    @Test
    fun theFormNameClearsItsDescriptionByFourPixels() {
        for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.25f, 1.75f)) withMeasurer(language, homeUiScale(textScale)) { text ->
            for ((width, height) in listOf(1_000 to 700, 1_440 to 810, 1_920 to 1_080)) {
                val layout = homeLayoutGeometry(width.toFloat(), height.toFloat(), 1f)
                val scene = layout.scene
                val nameRight = homeFormNameRight(layout, 1f)
                val (top, bottom) = homeFormDescriptionSpan(layout, 1f)
                defaultCoreShapeDefinitions().forEach { form ->
                    val name = form.displayName.localizedContent(language)
                    val where = "$name ${language.code} ${width}x$height @$textScale"
                    val nameInk = inkRows(rendered(width, height) { drawHomeFormName(text, scene, name, nameRight, Kk.Bone) }) ?: fail("no name: $where")
                    val description = rendered(width, height) {
                        drawHomeFormDescription(text, scene, form.mechanicDescription.localizedContent(language), top, bottom)
                    }
                    // Two lines still fit beside the name's descenders; only the smaller desktop may leave the text to the (!) at 175 %.
                    val descriptionInk = inkRows(description)
                        ?: if (width < 1_440 && textScale > 1.25f) return@forEach else fail("description dropped: $where")
                    assertTrue(descriptionInk.first - nameInk.last - 1 >= 4,
                        "name ink ends on row ${nameInk.last}, description starts on ${descriptionInk.first}: $where")
                }
            }
        }
    }

    /** Selected slab at the label's columns: its height and the label ink's least clearance to its top and bottom edges. */
    private data class SlabProbe(val height: Int, val top: Int, val bottom: Int)

    private fun slabProbe(image: Frame, from: Int, to: Int, where: String): SlabProbe {
        val heights = ArrayList<Int>()
        var top = Int.MAX_VALUE
        var bottom = Int.MAX_VALUE
        for (x in from..to) {
            val column = (0 until image.height).map { image.pixel(x, it) }
            val first = column.indexOfFirst { isBone(it) }
            val last = column.indexOfLast { isBone(it) }
            if (first < 0) fail("no slab at column $x: $where")
            heights += last - first + 1
            column.forEachIndexed { y, pixel ->
                if (isInk(pixel)) {
                    top = minOf(top, y - first)
                    bottom = minOf(bottom, last - y)
                }
            }
        }
        if (top == Int.MAX_VALUE) fail("no label ink: $where")
        return SlabProbe(heights.sorted()[heights.size / 2], top, bottom)
    }

    /** First and last pixel columns painted (at least a quarter opaque) on [rows] of [image], or null when none is. */
    private fun paintedColumns(image: Frame, rows: IntRange): IntRange? {
        val columns = (0 until image.width).filter { x -> rows.any { y -> y in 0 until image.height && image.pixel(x, y) ushr 24 >= 64 } }
        return if (columns.isEmpty()) null else columns.first()..columns.last()
    }

    /** First and last pixel rows with any ink, or null when [image] is blank. */
    private fun inkRows(image: Frame): IntRange? {
        val rows = (0 until image.height).filter { y -> (0 until image.width).any { x -> image.pixel(x, y) ushr 24 != 0 } }
        return if (rows.isEmpty()) null else rows.first()..rows.last()
    }

    /** A rendered frame's ARGB pixels. */
    private class Frame(val width: Int, val height: Int, private val pixels: IntArray) {
        fun pixel(x: Int, y: Int): Int = pixels[y * width + x]
    }

    private fun rendered(width: Int, height: Int, draw: DrawScope.() -> Unit): Frame {
        val image = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat()), draw)
        val pixels = IntArray(width * height)
        image.readPixels(pixels)
        return Frame(width, height, pixels)
    }

    private fun channels(argb: Int) = intArrayOf(argb ushr 24, (argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    private fun isBone(argb: Int): Boolean = channels(argb).let { (a, r, g, b) -> a >= 250 && r >= 228 && g >= 226 && b >= 218 }

    private fun isInk(argb: Int): Boolean = channels(argb).let { (a, r, g, b) -> a >= 128 && r < 90 && g < 90 && b < 90 }

    private fun near(argb: Int, color: Int): Boolean {
        val (a, r, g, b) = channels(argb)
        val (_, cr, cg, cb) = channels(color)
        return a >= 250 && abs(r - cr) <= 12 && abs(g - cg) <= 12 && abs(b - cb) <= 12
    }

    private fun withMenu(language: AppLanguage, textScale: Float, check: (CanvasTextMeasurer) -> Unit) =
        withMeasurer(language, homeMenuMeasurerScale(homeUiScale(textScale)), check)

    private fun withMeasurer(language: AppLanguage, scale: Float, check: (CanvasTextMeasurer) -> Unit) = runComposeUiTest {
        var measurer: CanvasTextMeasurer? = null
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalAppLanguage provides language) {
                measurer = rememberKkCanvasMeasurer(scale)
            }
        }
        waitForIdle()
        runOnIdle { check(requireNotNull(measurer)) }
    }

    private companion object {
        /** One-column Home menus: the reference screens and the smaller phones. */
        val OneColumnViewports = listOf(1_440 to 810, 844 to 390, 390 to 844, 800 to 360, 600 to 390, 360 to 800)

        /** Two-column Home menus (portrait under 660 px tall), down to the narrowest 320 px phones. */
        val TwoColumnViewports = listOf(320 to 568, 328 to 568, 332 to 568, 340 to 600, 360 to 640, 390 to 600, 412 to 650)

        /** Stamps on Armory and Rebirth, counts on the others. */
        val StampModel: HomeUiModel by lazy {
            val weapons = listOf(WeaponId.FLUX_WAKE to "Flux Wake", WeaponId.SINGULARITY_SPEAR to "Singularity Spear")
                .map { (id, name) -> WeaponDefinition(id, name, "Description", listOf("tag"), 100) }.toImmutableList()
            HomeReducer(defaultCoreShapeDefinitions(), 400, 12, TestRebirthPolicy, weapons, 40).uiModel(
                HomeProgressProjection(
                    LOCAL_PROFILE_INSTANCE_ID, ProfileRevision.ZERO,
                    PlayerEconomy(matter = 1_284_567, lifetimeMatter = 9_999_999),
                    PlayerLoadout(CoreShape.TESSERACT, WeaponId.FLUX_WAKE, setOf(WeaponId.FLUX_WAKE)),
                    PlayerCollection((0 until 400).toSet()),
                    RebirthProgress(level = 10, highestCleared = 9), canAdvanceRebirth = true,
                    unlockedCoreShapes = immutableSetOf(*CoreShape.entries.toTypedArray()),
                ),
            ).copy(weaponUnlockAffordable = true, canRebirth = true)
        }
    }
}
