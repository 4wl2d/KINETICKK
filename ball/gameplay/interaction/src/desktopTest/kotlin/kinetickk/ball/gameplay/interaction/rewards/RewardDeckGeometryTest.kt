// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.content.api.OmenId
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where reward cards sit: a decline card's plate and text start clear of its slanted dashed outline at every card size,
 * a mutator chip too wide for its row breaks into two lines, and no card of a deal, resting or lifted, rides over a card
 * of another row.
 */
@OptIn(ExperimentalTestApi::class)
class RewardDeckGeometryTest {
    @Test
    fun aDeclineCardsPlateAndTextStartClearOfItsOutline() {
        // The portrait 2 x 2 deck's card, the phone gallery's and landscape deal's cards, and the board card.
        val sizes = listOf(Triple(178, 305, false), Triple(195, 254, true), Triple(220, 256, true), Triple(300, 450, false))
        for (language in AppLanguage.entries) for ((width, height, phoneDeal) in sizes) {
            val decline = rewardFixtureModel(choiceType = ChoiceType.OMEN, choices = listOf(noOmen), language = language)
                .rewardPresentation(language).cards.single()
            assertTrue(decline.decline)
            runDesktopComposeUiTest(width + 40, height + 40) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                        Box(Modifier.requiredSize((width + 40).dp, (height + 40).dp)) {
                            RewardCard(decline, 0, overlayUiTextScale(1.25f), 0f, true,
                                Modifier.requiredSize(width.dp, height.dp), phoneDeal = phoneDeal) {}
                        }
                    }
                }
                waitForIdle()
                val card = onNodeWithTag("kinetickk.gameplay.choice.1").fetchSemanticsNode().boundsInRoot
                val where = "${width}x$height ${language.code}"
                val band = if (rewardCardUsesCompactLayout(width.toFloat(), height.toFloat(), phoneDeal)) 30f else 42f
                val u = rewardCardUnit(width.toFloat(), height.toFloat(), phoneDeal)
                fun clear(box: Rect, what: String) {
                    val outline = rewardDeclineOutlineLeftDp(box.top - card.top, height.toFloat(), band * u)
                    assertTrue(box.left - card.left >= outline + 3f,
                        "$what starts ${box.left - card.left} dp in, on the outline at $outline dp ($where)")
                }
                val plateTag = "kinetickk.gameplay.choice.1.${if (rewardCardUsesCompactLayout(width.toFloat(), height.toFloat(), phoneDeal)) "compact" else "expanded"}"
                clear(onNodeWithTag(plateTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot, "the plate")
                val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
                    .filter { node -> node.boundsInRoot.top > card.top + band * u }
                assertTrue(texts.size >= 2, "the decline's title and line are shown ($where)")
                texts.forEach { node -> clear(node.boundsInRoot, "'${node.config[SemanticsProperties.Text].joinToString()}'") }
            }
        }
    }

    /**
     * A phone omen card's mutator chip too wide for the column beside the plate breaks its name into two lines over the
     * column's two rows instead of shrinking to an unreadable size, at the default and the largest text size.
     */
    @Test
    fun aMutatorChipTooWideForItsRowBreaksIntoTwoLinesInsteadOfShrinking() {
        val language = AppLanguage.Russian
        val omen = rewardFixtureContent.variety.omens.definitions[OmenId.FAULT_LINE.ordinal]
        val card = rewardFixtureModel(choiceType = ChoiceType.OMEN,
            choices = listOf(ChoiceOption(ChoiceType.OMEN, omen.name, omen.upside, "OMEN", omenId = OmenId.FAULT_LINE)), language = language)
            .rewardPresentation(language).cards.single()
        val chip = card.chips.first { it.kind == RewardChipKind.MUTATOR }
        assertEquals("Расколотое пространство", chip.label)
        val style = RewardChipCompact
        val gap = 4f
        // The phone gallery card's column beside its plate: 105 dp wide, the plate's 46 u + 6 dp tall.
        val column = 105
        val height = (style.heightDp * 2f + gap + 6f).toInt()
        for (textScale in listOf(1.25f, 1.75f)) runDesktopComposeUiTest(200, 100) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                    Box(Modifier.requiredSize(column.dp, height.dp)) {
                        RewardChipFlow(listOf(chip), style, overlayUiTextScale(textScale), Modifier.fillMaxWidth(), maxRows = 2, gapDp = gap)
                    }
                }
            }
            waitForIdle()
            val drawn = onNode(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(chip.spoken))), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            // Two rows tall at a scale the name still reads at; one row scaled down to the column would be far smaller.
            val scale = drawn.height / (style.heightDp * 2f + gap)
            assertTrue(scale >= 0.7f, "the chip spans two rows at $scale of its size (x$textScale)")
            assertTrue(drawn.width <= column + 0.5f, "the chip stays in its column (x$textScale)")
        }
    }

    /** In a deal of more than one row (the portrait 2 x 2 deck) no card, resting or lifted, rides over a card of another row. */
    @Test
    fun noDealtCardRestingOrLiftedOverlapsACardOfAnotherRow() {
        val layouts = listOf(1_440f to 810f, 844f to 390f, 390f to 844f)
        for ((width, height) in layouts) for (count in 2..4) {
            val cards = choiceLayoutGeometry(width, height, 1f, count, canReroll = true).cards
            for (selected in -1 until count) {
                val shapes = cards.indices.map { index ->
                    val lifted = index == selected
                    val rise = if (lifted && rewardCardRises(index, cards)) RewardCardLiftDp else 0f
                    val degrees = rewardCardRestRotation(index, count) - (if (lifted) 1.5f else 0f)
                    corners(cards[index], degrees, rise)
                }
                for (a in shapes.indices) for (b in a + 1 until shapes.size) {
                    if (cards[a].bottom > cards[b].top && cards[b].bottom > cards[a].top) continue // one row
                    assertTrue(!overlap(shapes[a], shapes[b]),
                        "cards $a and $b of $count overlap at ${width.toInt()}x${height.toInt()} with card $selected lifted")
                }
            }
        }
    }

    /** The four corners of [rect] (shrunk by half a pixel) turned [degrees] about its centre and raised by [rise]. */
    private fun corners(rect: Rect, degrees: Float, rise: Float): List<Offset> {
        val radians = degrees * PI.toFloat() / 180f
        val c = rect.center
        return listOf(Offset(rect.left + 0.5f, rect.top + 0.5f), Offset(rect.right - 0.5f, rect.top + 0.5f),
            Offset(rect.right - 0.5f, rect.bottom - 0.5f), Offset(rect.left + 0.5f, rect.bottom - 0.5f)).map { p ->
            val dx = p.x - c.x
            val dy = p.y - c.y
            Offset(c.x + dx * cos(radians) - dy * sin(radians), c.y + dx * sin(radians) + dy * cos(radians) - rise)
        }
    }

    /** Whether two convex quads overlap (separating axis test over both quads' edges). */
    private fun overlap(a: List<Offset>, b: List<Offset>): Boolean {
        for (shape in listOf(a, b)) for (index in shape.indices) {
            val p = shape[index]
            val q = shape[(index + 1) % shape.size]
            val axis = Offset(q.y - p.y, p.x - q.x)
            val (minA, maxA) = a.map { it.x * axis.x + it.y * axis.y }.let { it.min() to it.max() }
            val (minB, maxB) = b.map { it.x * axis.x + it.y * axis.y }.let { it.min() to it.max() }
            if (maxA < minB || maxB < minA) return false
        }
        return true
    }

    private val noOmen = ChoiceOption(ChoiceType.OMEN, "No omen", "Start the run as it is.", "DECLINE")
}
