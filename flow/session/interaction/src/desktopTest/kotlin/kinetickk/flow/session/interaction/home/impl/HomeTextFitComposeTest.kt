// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RebirthDirective
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
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.drawKkMenuItem
import kinetickk.foundation.design.kkBoxHeight
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Home text measured with the bundled fonts: facts, tile labels and the legal line fit whole. */
@OptIn(ExperimentalTestApi::class)
class HomeTextFitComposeTest {
    @Test
    fun factsCardShowsEveryLabelAndValueWholeForRealNamesInBothLanguages() = forMeasurers { text, language, scale ->
        for ((width, height) in listOf(1_440f to 810f, 1_280f to 720f, 1_000f to 700f)) {
            val layout = homeLayoutGeometry(width, height, 1f)
            val rect = requireNotNull(layout.scene.facts)
            val infoLeft = requireNotNull(layout.info(HomeInfoTarget.FACTS)).bounds.left
            for (weapon in WeaponNames.keys) for (directive in RebirthDirective.entries) for (target in HomeMenuTargets) {
                val facts = homeFacts(model(weapon, directive), target, language, scale)
                val card = homeFactsCardLayout(text, rect, facts, layout.scene.unit, infoLeft, 1f)
                assertEquals(facts.facts.size, card.rows.size)
                card.rows.forEachIndexed { index, (key, value) ->
                    val where = "$target $weapon $directive ${language.code} ${width}x$height @$scale"
                    assertWhole(key, "key ${facts.facts[index].first} $where")
                    assertWhole(value, "value ${facts.facts[index].second} $where")
                    assertTrue(key.size.width + value.size.width <= card.columnRight - card.columnLeft + 2f, "row overlaps $where")
                }
                assertWhole(card.big, "big ${facts.big} $target ${language.code}")
            }
        }
    }

    @Test
    fun formTileLabelsKeepPaddingInsideTheSlantedFace() = forMeasurers { text, language, _ ->
        val names = defaultCoreShapeDefinitions().map { it.displayName.localizedContent(language) }
        for ((width, height) in listOf(1_440f to 810f, 1_280f to 720f, 1_000f to 700f, 844f to 390f, 390f to 844f)) {
            val tile = homeLayoutGeometry(width, height, 1f).bounds(HomeLayoutTarget.CORE_ORB)
            names.forEach { name ->
                val size = homeTileLabelSize(name, tile, 1f, text.scale) { value, candidate ->
                    measureKkText(text, value, text.typography.labelStyle(candidate), uppercase = true).size.width
                }
                if (tile.height >= 60f) assertTrue(size * text.scale >= HOME_TILE_LABEL_MIN_PX - 0.01f, "$name has no label at ${width}x$height ${language.code}")
                if (size > 0f) {
                    val label = measureKkText(text, name, text.typography.labelStyle(size), uppercase = true).size.width
                    val face = tile.width - homeTileCut(tile, 1f)
                    assertTrue(label + 2f * HOME_TILE_LABEL_PADDING_DP <= face, "$name ${language.code} ${width}x$height: $label in $face")
                }
            }
        }
    }

    @Test
    fun legalLineKeepsItsNoticesWholeBetweenItsBounds() = forMeasurers { text, language, _ ->
        for ((width, height) in listOf(1_440f to 810f, 1_000f to 700f, 844f to 390f, 600f to 390f, 390f to 844f, 390f to 600f)) {
            val layout = homeLayoutGeometry(width, height, 1f)
            val legal = homeLegalLayout(text, layout, 1f)
            val expected = if (layout.mode == HomeLayoutMode.REGULAR) 5 else 3
            assertEquals(expected, legal.parts.size, "desktop: copyright, license, no warranty, source, version")
            legal.parts.forEach { assertWhole(it, "legal ${language.code} ${width}x$height") }
            assertTrue(legal.width <= layout.scene.legalRight - layout.scene.legalLeft + 1f,
                "legal ${legal.width} > ${layout.scene.legalRight - layout.scene.legalLeft} at ${width}x$height ${language.code}")
            val texts = legal.parts.map { it.layoutInput.text.text }
            assertTrue(texts.any { "2026" in it } && texts.any { "GPL" in it } && texts.any { "github.com" in it })
            assertFalse(texts.any { '·' in it || "//" in it })
        }
    }

    @Test
    fun menuItemsStayInsideTheirRowWithTheirTrailInsideTheSelectedExtent() {
        for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.25f, 1.75f)) runComposeUiTest {
            var measurer: CanvasTextMeasurer? = null
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalAppLanguage provides language) {
                    measurer = rememberKkCanvasMeasurer(homeMenuMeasurerScale(homeUiScale(textScale)))
                }
            }
            waitForIdle()
            runOnIdle {
                val menu = requireNotNull(measurer)
                // Stamps on Armory ("Unlockable") and Rebirth ("Ready"), sub values on the others.
                val model = model(WeaponId.SINGULARITY_SPEAR, RebirthDirective.entries.first()).copy(weaponUnlockAffordable = true, canRebirth = true)
                for ((width, height) in listOf(1_440f to 810f, 1_000f to 700f, 844f to 390f, 800f to 360f, 600f to 390f, 390f to 844f, 360f to 800f, 412f to 915f)) {
                    val layout = homeLayoutGeometry(width, height, 1f)
                    if (layout.scene.menuColumns != 1) continue
                    val fontSize = layout.scene.menuFontSize / menu.scale
                    // The item's gaps, slab and speed lines follow its label as drawn (the layout's menu font).
                    val u = layout.scene.menuFontSize / 64f
                    val stampSize = homeMenuStampSize(menu, layout, 1f)
                    // Stamps grow with the text size or keep the size they have at the default one.
                    assertTrue(stampSize * menu.scale >= 14f * u.coerceAtLeast(0.8f) - 0.01f,
                        "stamp ${stampSize * menu.scale} px ${language.code} ${width}x$height @$textScale")
                    HomeMenuTargets.forEachIndexed { index, target ->
                        val where = "$target ${language.code} ${width}x$height @$textScale"
                        val bounds = layout.bounds(target)
                        val label = language.text(HomeMenuLabels[index])
                        val facts = homeFacts(model, target, language, textScale)
                        val placement = homeMenuPlacement(menu, bounds, label, facts.sub, facts.stamp, layout.scene.menuFontSize, stampSize, 1f)
                        // The foundation item's own layout, selected (moved 26 px left, sub shown) and at rest.
                        val selected = drawnMenuItem(menu, width, height, placement.left, bounds.center.y, label, fontSize, 1f,
                            facts.stamp, facts.sub.takeIf { placement.showSub }, stampSize)
                        val resting = drawnMenuItem(menu, width, height, placement.left, bounds.center.y, label, fontSize, 0f, facts.stamp, null, stampSize)
                        assertTrue(selected.right - 44f * u - 26f * u <= bounds.right + 0.5f, "selected $where ends at ${selected.right - 70f * u}")
                        assertTrue(resting.right - 44f * u <= bounds.right + 0.5f, "$where ends at ${resting.right - 44f * u}")
                        // Its speed lines never reach past the extent the layout keeps clear.
                        val extent = homeSelectedMenuExtent(bounds, layout.scene.menuFontSize)
                        assertTrue(placement.left - HOME_MENU_REACH * u >= extent.left - 0.5f, "trail of $where passes ${extent.left}")
                        // At the default text size on the reference screens every sub value keeps its place
                        // (narrower phones may leave the optional sub value out).
                        if (textScale == 1.25f && facts.sub != null && width.toInt() in setOf(1_440, 844, 390)) {
                            assertTrue(placement.showSub, "sub of $where dropped")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun theFormNameFitsWholeBesideItsInfoAtEveryTextSize() = forMeasurers { text, language, _ ->
        val names = defaultCoreShapeDefinitions().map { it.displayName.localizedContent(language) } +
            language.text(kinetickk.flow.session.interaction.localization.SessionText.UNKNOWN_CORE)
        for ((width, height) in listOf(1_440f to 810f, 1_000f to 700f, 844f to 390f, 800f to 360f, 600f to 390f, 390f to 844f, 390f to 600f)) {
            val layout = homeLayoutGeometry(width, height, 1f)
            val room = homeFormNameRight(layout, 1f) - layout.scene.formNameLeft
            names.forEach { name ->
                val layoutText = homeFormNameLayout(text, name, layout.scene.formNameSize, room, 1f)
                assertWhole(layoutText, "form name $name ${language.code} ${width}x$height")
                assertTrue(layoutText.size.width <= room, "$name is ${layoutText.size.width} in $room at ${width}x$height ${language.code}")
            }
        }
    }

    @Test
    fun theFormDescriptionIsShownWholeBetweenTheNameAndTheTiles() = forMeasurers { text, language, scale ->
        val descriptions = defaultCoreShapeDefinitions().map { it.mechanicDescription.localizedContent(language) }
        for ((width, height) in listOf(1_440f to 810f, 1_280f to 720f, 1_000f to 700f, 900f to 560f)) {
            val layout = homeLayoutGeometry(width, height, 1f)
            val rect = requireNotNull(layout.scene.formDescription)
            val (top, bottom) = homeFormDescriptionSpan(layout, 1f)
            // The name's wide type has a 0.9 em line box; tiles lift 8 px when selected.
            assertTrue(top >= layout.scene.formNameCenterY + layout.scene.formNameSize * 0.45f - 0.01f)
            assertTrue(bottom <= HomeCoreTargets.minOf { layout.bounds(it).top } - 8f + 0.01f)
            descriptions.forEach { description ->
                val where = "${description.take(24)} ${language.code} ${width}x$height @$scale"
                val laid = homeFormDescriptionLayout(text, description, rect.width, bottom - top)
                if (laid == null) {
                    // Only when even two small lines cannot hold it; the form's (!) carries the text.
                    assertTrue(width < 1_440f || scale > 1.25f, "description dropped at the reference size: $where")
                    assertTrue(layout.info(HomeInfoTarget.FORM) != null)
                } else {
                    assertFalse(laid.isCut() || laid.didOverflowHeight, "cut: $where")
                    assertTrue(laid.size.width <= rect.width + 0.5f && laid.kkBoxHeight <= bottom - top + 0.01f, "too large: $where")
                }
            }
        }
    }

    @Test
    fun selectedMenuItemsDrawNoPixelOverTheLegalNotices() {
        // Phone landscape and portrait (360-px-tall landscape is the 1080 × 2400 targets turned sideways) and desktop.
        val viewports = listOf(
            Triple(800, 360, 1f), Triple(844, 390, 1f), Triple(600, 390, 1f), Triple(873, 393, 1f), Triple(914, 411, 1f),
            Triple(390, 844, 1f), Triple(360, 800, 1f), Triple(412, 915, 1f), Triple(390, 600, 1f), Triple(1_440, 810, 1f),
            Triple(2_412, 1_080, 3f), Triple(2_400, 1_080, 3f), Triple(1_080, 2_400, 3f),
        )
        for (density in listOf(1f, 3f)) for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.25f, 1.75f)) runComposeUiTest {
            var home: CanvasTextMeasurer? = null
            var menuMeasurer: CanvasTextMeasurer? = null
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density, 1f), LocalAppLanguage provides language) {
                    home = rememberKkCanvasMeasurer(homeUiScale(textScale))
                    menuMeasurer = rememberKkCanvasMeasurer(homeMenuMeasurerScale(homeUiScale(textScale)))
                }
            }
            waitForIdle()
            runOnIdle {
                val text = requireNotNull(home)
                val menu = requireNotNull(menuMeasurer)
                // Stamps on Armory and Rebirth, sub values on the others: the widest rows.
                val model = model(WeaponId.SINGULARITY_SPEAR, RebirthDirective.entries.first()).copy(weaponUnlockAffordable = true, canRebirth = true)
                viewports.filter { it.third == density }.forEach { (width, height, _) ->
                    val layout = homeLayoutGeometry(width.toFloat(), height.toFloat(), density)
                    val scene = layout.scene
                    val legal = rendered(width, height, density) { drawLegal(text, layout) }
                    val ink = requireNotNull(inkBounds(legal, (scene.legalBaseline - 3f * scene.legalSize).toInt(), height)) { "no legal line" }
                    val where = "${language.code} ${width}x$height @$textScale"
                    // The phone layouts' allowance for the notices' tallest glyphs holds for the bundled font
                    // (desktop adds "Без гарантий", whose Й rises higher, far below its menu).
                    if (layout.mode != HomeLayoutMode.REGULAR) {
                        assertTrue(ink.top >= kotlin.math.floor(scene.legalBaseline - HOME_LEGAL_ASCENT_EM * scene.legalSize),
                            "legal glyphs rise to row ${ink.top}, baseline ${scene.legalBaseline} $where")
                    }
                    val stampSize = homeMenuStampSize(menu, layout, density)
                    HomeMenuTargets.forEachIndexed { index, target ->
                        val bounds = layout.bounds(target)
                        val label = language.text(HomeMenuLabels[index])
                        val facts = homeFacts(model, target, language, textScale)
                        val placement = if (scene.menuColumns == 1) {
                            homeMenuPlacement(menu, bounds, label, facts.sub, facts.stamp, scene.menuFontSize, stampSize, density)
                        } else {
                            HomeMenuPlacement(bounds.left, false)
                        }
                        val item = rendered(width, height, density) {
                            drawHomeMenuItem(menu, layout, index, label, facts, placement.left, placement.showSub, stampSize, 1f, HOME_TRAIL_REST_SECONDS)
                        }
                        val over = inkBounds(item, ink.top, ink.bottom, ink.left, ink.right)
                        assertEquals(null, over, "Selected $target (slab, echo or speed line) covers the legal glyphs $ink at $over $where")
                    }
                }
            }
        }
    }

    private fun rendered(width: Int, height: Int, density: Float, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit):
        androidx.compose.ui.graphics.ImageBitmap {
        val image = androidx.compose.ui.graphics.ImageBitmap(width, height)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(Density(density), androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(image), androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat()), draw)
        return image
    }

    /** Pixel rows [top, bottom) × columns [left, right) of [image] with any ink, as their bounding box (null when blank). */
    private fun inkBounds(image: androidx.compose.ui.graphics.ImageBitmap, top: Int, bottom: Int, left: Int = 0, right: Int = image.width):
        androidx.compose.ui.unit.IntRect? {
        val y0 = top.coerceIn(0, image.height)
        val y1 = bottom.coerceIn(y0, image.height)
        val x0 = left.coerceIn(0, image.width)
        val x1 = right.coerceIn(x0, image.width)
        if (y1 <= y0 || x1 <= x0) return null
        val pixels = IntArray((x1 - x0) * (y1 - y0))
        image.readPixels(pixels, x0, y0, x1 - x0, y1 - y0)
        var found: androidx.compose.ui.unit.IntRect? = null
        for (y in y0 until y1) for (x in x0 until x1) {
            if (pixels[(y - y0) * (x1 - x0) + (x - x0)] ushr 24 == 0) continue
            val box = found
            found = if (box == null) {
                androidx.compose.ui.unit.IntRect(x, y, x + 1, y + 1)
            } else {
                androidx.compose.ui.unit.IntRect(minOf(box.left, x), minOf(box.top, y), maxOf(box.right, x + 1), maxOf(box.bottom, y + 1))
            }
        }
        return found
    }

    private fun drawnMenuItem(menu: CanvasTextMeasurer, width: Float, height: Float, left: Float, centerY: Float, label: String,
        fontSize: Float, selection: Float, stamp: String?, sub: String?, stampSize: Float): androidx.compose.ui.geometry.Rect {
        var drawn = androidx.compose.ui.geometry.Rect.Zero
        val image = androidx.compose.ui.graphics.ImageBitmap(width.toInt(), height.toInt())
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(image), androidx.compose.ui.geometry.Size(width, height)) {
            drawn = drawKkMenuItem(menu, left, centerY, label, selection = selection, time = HOME_TRAIL_REST_SECONDS,
                fontSize = fontSize, stamp = stamp, sub = sub, stampFontSize = stampSize)
        }
        return drawn
    }

    private fun assertWhole(layout: TextLayoutResult, what: String) {
        assertFalse(layout.lineCount > 1 || layout.isCut() || layout.hasVisualOverflow, "truncated: $what")
    }

    private fun forMeasurers(check: (CanvasTextMeasurer, AppLanguage, Float) -> Unit) {
        for (language in AppLanguage.entries) for (scale in listOf(1f, 1.25f, 1.75f)) runComposeUiTest {
            var measurer: CanvasTextMeasurer? = null
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalAppLanguage provides language) {
                    // Home measures UI text with the text size over the default (see homeUiScale).
                    measurer = rememberKkCanvasMeasurer(homeUiScale(scale))
                }
            }
            waitForIdle()
            runOnIdle { check(requireNotNull(measurer), language, scale) }
        }
    }

    private fun model(weapon: WeaponId, directive: RebirthDirective): HomeUiModel {
        val weapons = WeaponNames.map { (id, name) -> WeaponDefinition(id, name, "Description", listOf("tag"), 100) }.toImmutableList()
        val policy = TestRebirthPolicy.copy(profiles = TestRebirthPolicy.profiles.map { it.copy(directive = directive) }.toImmutableList())
        return HomeReducer(defaultCoreShapeDefinitions(), 400, 12, policy, weapons, 40).uiModel(
            HomeProgressProjection(
                LOCAL_PROFILE_INSTANCE_ID, ProfileRevision.ZERO,
                PlayerEconomy(matter = 1_284_567, lifetimeMatter = 9_999_999),
                PlayerLoadout(CoreShape.TESSERACT, weapon, WeaponId.entries.toSet()),
                PlayerCollection((0 until 400).toSet()),
                RebirthProgress(level = 10, highestCleared = 9), canAdvanceRebirth = true,
                unlockedCoreShapes = immutableSetOf(*CoreShape.entries.toTypedArray()),
            ),
        )
    }

    private companion object {
        val WeaponNames = mapOf(
            WeaponId.FLUX_WAKE to "Flux Wake", WeaponId.MORNINGSTAR to "Morningstar", WeaponId.PHASE_LATTICE to "Phase Lattice",
            WeaponId.NULL_LANCE to "Null Lance", WeaponId.GRAVITY_MINES to "Gravity Mines", WeaponId.ION_SWARM to "Ion Swarm",
            WeaponId.RIFT_BLADES to "Rift Blades", WeaponId.ARC_COIL to "Arc Coil", WeaponId.QUASAR_CANNON to "Quasar Cannon",
            WeaponId.ENTROPY_FIELD to "Entropy Field", WeaponId.SINGULARITY_SPEAR to "Singularity Spear", WeaponId.PRISM_RELAY to "Prism Relay",
        )
    }
}

/** Cut text: lines past its limit, or one line narrower than its natural width (Skia never reports the ellipsis itself). */
private fun TextLayoutResult.isCut(): Boolean =
    multiParagraph.didExceedMaxLines || (lineCount == 1 && multiParagraph.intrinsics.maxIntrinsicWidth > size.width + 0.5f)
