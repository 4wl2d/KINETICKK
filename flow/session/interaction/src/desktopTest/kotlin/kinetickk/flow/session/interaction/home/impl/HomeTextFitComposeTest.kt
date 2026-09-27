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
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.LocalAppLanguage
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
                val size = homeTileLabelSize(name, tile, 1f) { value, candidate ->
                    measureKkText(text, value, text.typography.labelStyle(candidate), uppercase = true).size.width
                }
                if (tile.height >= 60f) assertTrue(size >= 7f, "$name has no label at ${width}x$height ${language.code}")
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

    private fun assertWhole(layout: TextLayoutResult, what: String) {
        assertFalse(layout.lineCount > 1 || layout.isLineEllipsized(layout.lineCount - 1) || layout.hasVisualOverflow, "truncated: $what")
    }

    private fun forMeasurers(check: (CanvasTextMeasurer, AppLanguage, Float) -> Unit) {
        for (language in AppLanguage.entries) for (scale in listOf(1f, 1.25f)) runComposeUiTest {
            var measurer: CanvasTextMeasurer? = null
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalAppLanguage provides language) {
                    measurer = rememberKkCanvasMeasurer(scale)
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
