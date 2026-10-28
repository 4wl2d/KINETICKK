// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Keynote's long sweeps (variety.md §8.6): its marginals over 9,000 seeds and the Elite aspect bias over 4,000
 * Elites. Too long for the browser runner's per-test limit; KeynoteTest holds the Keynote's rules.
 */
class KeynoteSweepTest {
    @Test
    fun everyTagAndElementIsTheKeynoteOfItsShareOfNineThousandSeeds() {
        val tags = IntArray(BuildTag.entries.size)
        val elements = IntArray(StatusElement.entries.size)
        for (seed in 1..9_000) {
            val state = buildTestState(seed = seed)
            tags[requireNotNull(state.keynoteTag).ordinal]++
            elements[requireNotNull(state.keynoteElement).ordinal]++
        }
        println("KEYNOTE-MARGINALS tags ${tags.toList()} elements ${elements.toList()}")
        BuildTag.entries.forEach { tag ->
            val share = tags[tag.ordinal] / 9_000.0
            assertTrue(abs(share - 1.0 / BuildTag.entries.size) <= 0.03, "$tag is the Keynote of $share of the runs")
        }
        StatusElement.entries.forEach { element ->
            val share = elements[element.ordinal] / 9_000.0
            assertTrue(abs(share - 1.0 / StatusElement.entries.size) <= 0.03, "$element is the Keynote of $share of the runs")
        }
    }

    @Test
    fun aboutAThirdOfElitesTakeTheAspectWeakToTheKeynoteOverFourThousandElites() {
        val withoutKeynote = canonicalGameplayContent.withoutKeynote()
        var featured = 0
        var naturallyFeatured = 0
        var elites = 0
        for (seed in 1..400) {
            val state = buildTestState(seed = seed)
            val plain = buildTestState(seed = seed, content = withoutKeynote)
            repeat(10) {
                // Both runs draw the same aspect from the spawn stream; only the Keynote's bias tells them apart.
                val rolled = plain.spawnedEnemy(EnemyType.ELITE, 0f, 0f).aspect
                val aspect = state.spawnedEnemy(EnemyType.ELITE, 0f, 0f).aspect
                elites++
                if (aspect == state.featuredAspect) featured++
                if (rolled == state.featuredAspect) naturallyFeatured++
                assertTrue(aspect == rolled || aspect == state.featuredAspect, "Seed $seed: an Elite keeps its aspect or takes the featured one")
            }
        }
        // The bias acts on the Elites that did not draw the featured aspect already (about one in seven did).
        val natural = naturallyFeatured.toDouble() / elites
        val bias = (featured - naturallyFeatured).toDouble() / (elites - naturallyFeatured)
        println("KEYNOTE-ELITES $elites Elites, featured ${featured.toDouble() / elites}, drawn naturally $natural, bias $bias")
        assertTrue(abs(natural - 1.0 / StatusElement.entries.size) <= 0.03, "Drawn naturally by $natural of Elites")
        assertTrue(bias in 0.30..0.40, "The Keynote turns $bias of the other Elites")
    }
}
