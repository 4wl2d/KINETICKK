// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.overlayIcon
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.interaction.rewards.OverlayButtonProbe
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureContent
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureModel
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import org.junit.Test
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integrated visual QA fixes of the run report, checked on rendered pixels with the bundled fonts:
 * the weapon slot's level label and icon, the shatter under the cause stamp, the form tag's icon
 * and the sizes of the action labels.
 */
@OptIn(ExperimentalTestApi::class)
class ReportLayoutFixesTest {
    private val frames = listOf(1440 to 810, 844 to 390, 390 to 844)
    private val relics = listOf(
        EquippedRelic(RelicId.KINETIC_FLYWHEEL, 2), EquippedRelic(RelicId.GHOST_VECTOR, 1),
        EquippedRelic(RelicId.ORBITAL_NAIL, 1), EquippedRelic(RelicId.VOLTAIC_FILAMENT, 1),
    )

    @Test
    fun reportWeaponSlotKeepsItsLevelInsideTheFaceAndClearOfTheIcon() {
        for ((width, height) in frames) for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.25f, 1.75f))
            for (level in listOf(10, 8)) {
                val scene = "report ${width}x$height $language ${textScale}x level $level"
                val model = rewardFixtureModel(phase = GamePhase.VICTORY, relics = relics, language = language, width = width.toFloat(),
                    height = height.toFloat(), textScale = textScale, level = 22, weaponLevel = level)
                // Not at the mastery cap, so the icon (you) and the label (bone) differ in color.
                val presentation = model.terminalPresentation(language).copy(weaponMaxLevel = false)
                runDesktopComposeUiTest(width, height) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                            Box(Modifier.requiredSize(width.dp, height.dp)) { TerminalContent(presentation, textScale, false, 3f, true) {} }
                        }
                    }
                    val slot = onNode(hasText(presentation.weapon + " " + presentation.weaponLevel), useUnmergedTree = true)
                    slot.performScrollTo()
                    assertSlotClear(slot.captureToImage().toPixelMap(), scene)
                }
            }
    }

    @Test
    fun everyWeaponIconClearsItsLevelAtEverySlotSize() {
        val icons = KkIcon.entries.filter { it.key.startsWith("weapons.") }
        val levels = listOf("Lvl 1", "Lvl 10", "Lvl 12", "Lvl 100", "Ур. 7", "Ур. 10")
        // Report slots on phones and desktop, and the pause slot (26 px icon in 50 units).
        val slots = listOf(44f to 0.48f, 46f to 0.48f, 50f to 0.48f, 56f to 0.48f, 65f to 0.48f, 50f to 0.52f, 56f to 0.52f)
        val cell = 90
        val columns = icons.size
        val rows = levels.size * slots.size
        runDesktopComposeUiTest(columns * cell, rows * cell) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    val measurer = rememberKkCanvasMeasurer(1f)
                    Canvas(Modifier.requiredSize((columns * cell).dp, (rows * cell).dp).testTag("slots")) {
                        drawRect(Kk.Ink)
                        for (row in 0 until rows) for (column in icons.indices) {
                            val (size, share) = slots[row % slots.size]
                            val bounds = Rect(column * cell + 10f, row * cell + 10f, column * cell + 10f + size, row * cell + 10f + size)
                            val text = levels[row / slots.size]
                            drawPlacedWeaponSlot(measurer, bounds, icons[column], text, maxLevel = false, ready = share > 0.5f,
                                placement = weaponSlotPlacement(measurer, bounds, icons[column], text, share))
                        }
                    }
                }
            }
            val pixels = onNodeWithTag("slots").captureToImage().toPixelMap()
            for (row in 0 until rows) for (column in icons.indices) {
                val (size, share) = slots[row % slots.size]
                val scene = "${icons[column].key} \"${levels[row / slots.size]}\" in a ${size.toInt()} px slot, icon share $share"
                assertSlotClear(pixels, scene, column * cell + 10, row * cell + 10, size.toInt())
            }
        }
    }

    @Test
    fun portraitShatterStaysUnderTheCauseStamp() {
        for (language in AppLanguage.entries) for (textScale in listOf(1.25f, 1.75f)) for (victory in listOf(false, true)) {
            val scene = "portrait report $language ${textScale}x victory $victory"
            // The run the QA shots showed debris on the stamp for.
            val model = rewardFixtureModel(phase = if (victory) GamePhase.VICTORY else GamePhase.GAME_OVER, relics = relics, language = language,
                width = 390f, height = 844f, textScale = textScale, message = if (victory) "ARCHITECT DISMANTLED" else "SINGULARITY CONTACT",
                runMatter = 640L, totalMatter = 1_284L, kills = 901, elapsed = 554f, level = 16, weaponLevel = 8)
            runDesktopComposeUiTest(390, 844) {
                var plate = Color.Unspecified
                setContent {
                    plate = if (victory) LocalKkRolePalette.current.you else LocalKkRolePalette.current.threat
                    CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                        Box(Modifier.requiredSize(390.dp, 844.dp)) { TerminalContent(model, 4f, true) {} }
                    }
                }
                val pixels = onNodeWithTag("kinetickk.gameplay.results.cause").captureToImage().toPixelMap()
                var checked = 0
                for (y in 0 until pixels.height) {
                    val row = (0 until pixels.width).filter { x -> distance(pixels[x, y], plate) < 20f }
                    if (row.size < 4) continue
                    // Inside the rotated plate every pixel is plate, ink lettering or a blend of the two.
                    for (x in row.first()..row.last()) {
                        checked++
                        val off = distanceToSegment(pixels[x, y], Kk.Ink, plate)
                        assertTrue(off < 30f, "$scene: pixel ($x, $y) ${pixels[x, y]} on the stamp is neither plate nor its lettering (off $off)")
                    }
                }
                assertTrue(checked > 500, "$scene: the stamp plate was found")
            }
        }
    }

    @Test
    fun formTagDrawsTheFormIconBeforeItsName() {
        for ((width, height) in frames) for (language in AppLanguage.entries) for (textScale in listOf(1.25f, 1.75f)) {
            val scene = "report ${width}x$height $language ${textScale}x"
            val presentation = rewardFixtureModel(phase = GamePhase.GAME_OVER, relics = relics, language = language).terminalPresentation(language)
            val form = requireNotNull(presentation.form)
            runDesktopComposeUiTest(width, height) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                        Box(Modifier.requiredSize(width.dp, height.dp)) { TerminalContent(presentation, textScale, false, 3f, true) {} }
                    }
                }
                val pixels = onNodeWithText(form, useUnmergedTree = true).captureToImage().toPixelMap()
                // Columns holding bone ink (the icon and the letters; the line plate is darker).
                val inked = (0 until pixels.width).map { x -> (0 until pixels.height).any { y -> isBone(pixels[x, y]) } }
                val first = inked.indexOf(true)
                assertTrue(first >= 0, "$scene: the tag is drawn")
                val firstEnd = (first until pixels.width).first { !inked[it] }
                val next = (firstEnd until pixels.width).firstOrNull { inked[it] } ?: pixels.width
                assertTrue(firstEnd - first >= 7, "$scene: a glyph at least 7 px wide leads the tag (${firstEnd - first} px)")
                assertTrue(next - firstEnd >= 4, "$scene: the glyph stands apart from the name, as the icon does (${next - firstEnd} px gap)")
            }
        }
    }

    @Test
    fun headerTagsKeepTheirWholePlatesInsideTheColumn() {
        for ((width, height) in frames) for (language in AppLanguage.entries) for (longest in listOf(false, true)) {
            // The fixture's Circle at rebirth 3, and the longest form name at a two-digit rebirth.
            val base = rewardFixtureModel(phase = GamePhase.GAME_OVER, relics = relics, language = language, rebirthLevel = if (longest) 12 else 3)
                .terminalPresentation(language)
            val shape = if (longest) CoreShape.entries.maxBy { rewardFixtureContent.coreShape(it).displayName.localizedContent(language).length } else null
            val presentation = if (shape == null) base
                else base.copy(form = rewardFixtureContent.coreShape(shape).displayName.localizedContent(language), formIcon = shape.overlayIcon())
            val rebirthText = requireNotNull(presentation.rebirth)
            val rebirthWidths = listOf(1f, 1.25f, 1.75f).map { textScale ->
                val scene = "report ${width}x$height $language ${textScale}x \"$rebirthText\" \"${presentation.form}\""
                var rebirthWidth = 0f
                runDesktopComposeUiTest(width, height) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                            Box(Modifier.requiredSize(width.dp, height.dp)) { TerminalContent(presentation, textScale, false, 3f, true) {} }
                        }
                    }
                    val column = onNodeWithTag("kinetickk.gameplay.results.summary", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val rebirth = onNodeWithText(rebirthText, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val form = onNodeWithTag("kinetickk.gameplay.results.form", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    rebirthWidth = rebirth.width
                    assertTrue(rebirth.right <= form.left, "$scene: the rebirth tag ($rebirth) comes before the form tag ($form)")
                    assertTrue(form.right <= column.right, "$scene: the form tag ($form) stays inside the column ($column)")
                    // Every bone pixel after the rebirth tag (the form icon and name) lies inside the
                    // form tag's sheared outline (6 px shear); the column's scroll bar is left out.
                    val pixels = onRoot().captureToImage().toPixelMap()
                    var ink = 0
                    for (y in form.top.toInt() until form.bottom.toInt()) for (x in rebirth.right.toInt() + 1 until column.right.toInt() - 4) {
                        if (!isBone(pixels[x, y])) continue
                        ink++
                        val down = (y + 0.5f - form.top) / form.height
                        val left = form.left + 6f * (1f - down)
                        val right = form.right - 6f * down
                        assertTrue(x + 0.5f >= left - 1f && x + 0.5f <= right + 1f,
                            "$scene: the form tag's ink at ($x, $y) lies outside its outline ${left}..$right (tag $form)")
                    }
                    assertTrue(ink > 20, "$scene: the form tag's icon and name are drawn ($ink px)")
                }
                rebirthWidth
            }
            assertTrue(rebirthWidths[0] <= rebirthWidths[1] && rebirthWidths[1] <= rebirthWidths[2],
                "report ${width}x$height $language \"$rebirthText\": the header tags grow with the text size ($rebirthWidths)")
        }
    }

    @Test
    fun actionLabelsKeepTheirSizeAsTextGrowsAndThePrimaryLeads() {
        val labels = mutableListOf<Pair<TextLayoutResult, Float>>()
        OverlayButtonProbe.records = labels
        try {
            for ((width, height) in frames) for (language in AppLanguage.entries) for (victory in listOf(true, false)) {
                val sizes = listOf(1f, 1.25f, 1.75f).map { textScale ->
                    labels.clear()
                    runDesktopComposeUiTest(width, height) {
                        setContent {
                            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                                Box(Modifier.requiredSize(width.dp, height.dp)) {
                                    TerminalContent(rewardFixtureModel(phase = if (victory) GamePhase.VICTORY else GamePhase.GAME_OVER, language = language)
                                        .terminalPresentation(language), textScale, false, 3f, true) {}
                                }
                            }
                        }
                        waitForIdle()
                    }
                    labels.associate { (layout, _) -> layout.layoutInput.text.text to layout.layoutInput.style.fontSize.value }
                }
                val primary = language.text(if (victory) GameplayText.RebirthNext else GameplayText.Reenter).uppercase()
                val ghost = language.text(GameplayText.Reenter).uppercase()
                val menu = language.text(OverlayRedesignText.Menu).uppercase()
                listOf(1f, 1.25f, 1.75f).forEachIndexed { index, textScale ->
                    val scene = "report ${width}x$height $language victory $victory ${textScale}x"
                    val shown = sizes[index]
                    val lead = requireNotNull(shown[primary]) { "$scene draws $primary: $shown" }
                    if (victory) assertTrue(lead >= requireNotNull(shown[ghost]) - 0.01f, "$scene: $primary ($lead) is not smaller than $ghost: $shown")
                    // On a phone in landscape Menu shares the action row.
                    if (width == 844) assertTrue(lead >= requireNotNull(shown[menu]) - 0.01f, "$scene: $primary ($lead) is not smaller than $menu: $shown")
                }
                if (width == 844) for (label in sizes[1].keys) {
                    val scene = "report ${width}x$height $language victory $victory"
                    assertTrue(requireNotNull(sizes[2][label]) >= requireNotNull(sizes[1][label]) - 0.01f,
                        "$scene: \"$label\" is not smaller at 175 % (${sizes[2][label]}) than at 125 % (${sizes[1][label]})")
                }
            }
        } finally {
            OverlayButtonProbe.records = null
        }
    }
}

/**
 * The weapon slot at ([left], [top]) of [size] px in [pixels] (whole image when [size] is 0):
 * the level label (bone) lies inside the sheared face (its left edge runs from x + 9 at the top
 * to x at the bottom, its right edge from the right at the top to right − 9 at the bottom) and
 * at least 2 px below the icon's lowest stroke (you, inside the face; a ready slot's glow is you
 * too but lies outside it).
 */
private fun assertSlotClear(pixels: PixelMap, scene: String, left: Int = 0, top: Int = 0, size: Int = 0) {
    val side = if (size > 0) size else pixels.width
    val height = if (size > 0) size else pixels.height
    assertEquals(side, height, "$scene: the slot is square")
    var labelTop = Int.MAX_VALUE
    var iconBottom = Int.MIN_VALUE
    var labelPixels = 0
    for (y in 0 until height) for (x in 0 until side) {
        val color = pixels[left + x, top + y]
        val cy = y + 0.5f
        val faceLeft = 9f * (height - cy) / height
        val faceRight = side - 9f * cy / height
        when {
            isBone(color) -> {
                labelPixels++
                labelTop = minOf(labelTop, y)
                assertTrue(x + 0.5f >= faceLeft - 0.5f && x + 0.5f <= faceRight + 0.5f,
                    "$scene: the level label leaves the sheared face at ($x, $y); face spans $faceLeft..$faceRight")
            }
            isVolt(color) && x + 0.5f > faceLeft + 1.5f && x + 0.5f < faceRight - 1.5f && y > 0 && y < height - 1 ->
                iconBottom = maxOf(iconBottom, y)
        }
    }
    assertTrue(labelPixels > 10, "$scene: the level label is drawn")
    assertTrue(iconBottom >= 0, "$scene: the icon is drawn")
    assertTrue(labelTop - iconBottom - 1 >= 2, "$scene: ${labelTop - iconBottom - 1} px between the icon (bottom $iconBottom) and the label (top $labelTop)")
}

private fun channels(color: Color) = floatArrayOf(color.red * 255f, color.green * 255f, color.blue * 255f)

/** Bone ink (neutral and bright): the level label, tag text and icon. */
private fun isBone(color: Color): Boolean {
    val (r, g, b) = channels(color)
    val top = max(r, max(g, b))
    return top > 110f && b >= 0.75f * max(r, g)
}

/** You ink (yellow-green): the weapon icon. */
private fun isVolt(color: Color): Boolean {
    val (r, g, b) = channels(color)
    return max(r, g) > 110f && b <= 0.5f * max(r, g) && g >= 0.8f * r
}

private fun distance(a: Color, b: Color): Float {
    val x = channels(a)
    val y = channels(b)
    return sqrt((x[0] - y[0]) * (x[0] - y[0]) + (x[1] - y[1]) * (x[1] - y[1]) + (x[2] - y[2]) * (x[2] - y[2]))
}

/** RGB distance from [color] to the blends of [from] and [to]. */
private fun distanceToSegment(color: Color, from: Color, to: Color): Float {
    val p = channels(color)
    val a = channels(from)
    val b = channels(to)
    val d = FloatArray(3) { b[it] - a[it] }
    val t = ((0 until 3).sumOf { ((p[it] - a[it]) * d[it]).toDouble() } / (0 until 3).sumOf { (d[it] * d[it]).toDouble() }).toFloat().coerceIn(0f, 1f)
    return sqrt((0 until 3).sumOf { ((p[it] - (a[it] + t * d[it])) * (p[it] - (a[it] + t * d[it]))).toDouble() }).toFloat()
}
