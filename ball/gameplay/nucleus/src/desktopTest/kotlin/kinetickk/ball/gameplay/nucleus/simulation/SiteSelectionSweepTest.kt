// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Window kinds over 5,000 run seeds (site spec §14.1): for every window the first draw's kind and family shares sit
 * within 2 pp of weight / total over that window's eligible kinds, the second draw's within 2 pp of the same-family
 * down-weighted expectation, and no pair repeats a kind. Runs on the shipped weights and on the design weights.
 */
class SiteSelectionSweepTest {
    @Test
    fun windowFamiliesFollowTheirWeightsAndPairsNeverRepeatAKind() {
        val started = System.nanoTime()
        sweep("shipped", canonicalGameplayContent)
        sweep("design", canonicalGameplayContent.withWindowWeights(DesignWindowWeights))
        println("SITE-SELECTION-SWEEP seeds=$SEEDS ms=${(System.nanoTime() - started) / 1_000_000}")
    }

    private fun sweep(label: String, content: GameplayContentSnapshot) {
        val game = MutableGameState(content).apply { startRun(); enemies.clear() }
        val windows = content.sites.windows
        val windowCount = ((windows.lastOfferAt - windows.firstOfferAt) / windows.offerInterval).toInt() + 1
        val kinds = SiteKind.entries.size
        val firstCounts = Array(windowCount) { IntArray(kinds) }
        val secondCounts = Array(windowCount) { IntArray(kinds) }
        for (seed in 0 until SEEDS) {
            // The run seed reaches the kinds only through the site stream.
            game.siteRandom = CloneableXorWowRandom(seed xor MutableGameState.SITE_SEED_SALT)
            for (index in 0 until windowCount) {
                val pair = game.offerWindowForTesting(index)
                assertEquals(2, pair.size)
                assertNotEquals(pair[0].kind, pair[1].kind, "$label seed $seed window $index")
                firstCounts[index][pair[0].kind.ordinal]++
                secondCounts[index][pair[1].kind.ordinal]++
            }
        }
        var largestGap = 0.0
        for (index in 0 until windowCount) {
            val scheduledAt = windows.firstOfferAt + index * windows.offerInterval
            val weights = FloatArray(kinds) { ordinal ->
                val kind = SiteKind.entries[ordinal]
                val definition = content.sites.definition(kind)
                val eligible = kind.family != SiteFamily.PORTAL && scheduledAt >= definition.minimumOfferAt &&
                    definition.minimumRebirth <= 0 &&
                    scheduledAt + windows.offerLifetime + definition.reserveSeconds < content.tempo.bossAtSeconds
                if (eligible) definition.windowWeight else 0f
            }
            val (expectedFirst, expectedSecond) = expectedKindShares(weights, content.sites.sameFamilyWeight)
            val line = StringBuilder("SITE-SELECTION $label window=$index at=${scheduledAt.toInt()}")
            fun check(draw: String, observed: IntArray, expected: DoubleArray, groups: List<List<SiteKind>>) {
                for (group in groups) {
                    val share = group.sumOf { observed[it.ordinal] }.toDouble() / SEEDS
                    val want = group.sumOf { expected[it.ordinal] }
                    largestGap = maxOf(largestGap, abs(share - want))
                    assertTrue(abs(share - want) <= TOLERANCE, "$label window $index $draw $group: $share against $want")
                }
            }
            val familyGroups = SiteFamily.entries.map { family -> SiteKind.entries.filter { it.family == family } }
            val kindGroups = SiteKind.entries.map { listOf(it) }
            check("first", firstCounts[index], expectedFirst, familyGroups + kindGroups)
            check("second", secondCounts[index], expectedSecond, familyGroups + kindGroups)
            for (family in SiteFamily.entries) {
                val share = SiteKind.entries.filter { it.family == family }.sumOf { firstCounts[index][it.ordinal] }.toDouble() / SEEDS
                val want = SiteKind.entries.filter { it.family == family }.sumOf { expectedFirst[it.ordinal] }
                if (want > 0.0 || share > 0.0) line.append(" ${family.name.lowercase()}=${pct(share)}/${pct(want)}")
            }
            println(line)
        }
        println("SITE-SELECTION $label largest-gap-pp=${pct(largestGap)}")
    }

    /** First-draw shares by weight; second-draw shares summed over every first kind with its family down-weighted. */
    private fun expectedKindShares(weights: FloatArray, sameFamilyWeight: Float): Pair<DoubleArray, DoubleArray> {
        val first = DoubleArray(weights.size)
        val second = DoubleArray(weights.size)
        val total = weights.sum().toDouble()
        for (firstOrdinal in weights.indices) {
            val firstWeight = weights[firstOrdinal]
            if (firstWeight <= 0f) continue
            val firstShare = firstWeight / total
            val firstFamily = SiteKind.entries[firstOrdinal].family
            first[firstOrdinal] += firstShare
            val rest = DoubleArray(weights.size) { ordinal ->
                if (ordinal == firstOrdinal) 0.0
                else weights[ordinal] * (if (SiteKind.entries[ordinal].family == firstFamily) sameFamilyWeight.toDouble() else 1.0)
            }
            val restTotal = rest.sum()
            for (ordinal in rest.indices) second[ordinal] += firstShare * rest[ordinal] / restTotal
        }
        return first to second
    }

    private fun pct(share: Double): String = "%.1f".format(share * 100.0)

    private companion object {
        const val SEEDS = 5_000
        const val TOLERANCE = 0.02
    }
}
