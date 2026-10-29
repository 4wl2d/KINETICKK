// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The run-skeleton variety metric (variety.md §11.4). A run's skeleton has ten components: its director personality,
 * Keynote tag and element, world-event count, event kinds in order, event starts in 60 s buckets, signal kinds in order,
 * signal slots, the first five onslaught themes and the first four Elite affixes (the last two from a 400 s run flown by
 * the NoPoi bot). Over [Seeds] (`i * 7_919 + 101`, i = 1..200), V is the mean normalized Hamming distance over every
 * pair of seeds. Design: V at least [MIN_VARIETY], at most [MAX_DUPLICATE_SHARE] of the runs share a skeleton, each
 * personality rolls a sixth of the runs within [PERSONALITY_TOLERANCE], each world-event kind runs in at least
 * [MIN_EVENT_KIND_SHARE] of the runs, each signal kind in [MIN_SIGNAL_KIND_SHARE] and each tag is the Keynote of at
 * least [MIN_TAG_SHARE]. The control arm with every run-variety system switched off reads one skeleton on every seed
 * (V = 0) and leaves the run as it is without the systems: the opening packs and the gameplay, spawn and reward streams
 * after the start are the shipped run's, the deal is the one without a Keynote (the only system that touches it) and the
 * director's first four minutes are the plain director's. Measured with the lane tree: V 0.880 (bootstrap SE 0.0024,
 * 20,000 resamples of seeds, RNG seed 20261027), no duplicate skeleton, the rarest personality 0.125 of the runs, event
 * kind 0.44, signal kind 0.585 and Keynote tag 0.085.
 */
class RunSkeletonVarietyTest {
    @Test
    fun runSkeletonsDifferFromSeedToSeed() {
        val v = variety(shipped)
        val duplicates = shipped.size - shipped.toSet().size
        println("SKELETON-GATE V=${"%.4f".format(v)} " + bootstrapLine("V", shipped, ::variety) + " duplicates=$duplicates of ${shipped.size} " +
            "components=" + (0 until COMPONENTS).joinToString(",") { index -> "%.3f".format(componentDistance(shipped, index)) })
        assertTrue(v >= MIN_VARIETY, "run skeletons vary only ${"%.4f".format(v)}; expected at least $MIN_VARIETY")
        assertTrue(duplicates.toDouble() / shipped.size <= MAX_DUPLICATE_SHARE, "$duplicates of ${shipped.size} runs repeat a skeleton")
    }

    @Test
    fun everySystemDealsEachOfItsKindsAcrossTheRuns() {
        val failures = ArrayList<String>()
        val personalities = DirectorPersonalityId.entries.associateWith { id -> shipped.shareOf { it.parts[0] == id } }
        val events = WorldEventKind.entries.associateWith { kind -> shipped.shareOf { kind in (it.parts[4] as List<*>) } }
        val signals = SignalKind.entries.associateWith { kind -> shipped.shareOf { kind in (it.parts[6] as List<*>) } }
        val tags = BuildTag.entries.associateWith { tag -> shipped.shareOf { it.parts[1] == tag } }
        println("SKELETON-MARGINALS personalities=${shares(personalities)} events=${shares(events)} signals=${shares(signals)} tags=${shares(tags)}")
        personalities.forEach { (id, share) ->
            if (abs(share - 1.0 / personalities.size) > PERSONALITY_TOLERANCE) failures += "$id rolls ${"%.3f".format(share)} of the runs"
        }
        events.forEach { (kind, share) -> if (share < MIN_EVENT_KIND_SHARE) failures += "$kind runs in ${"%.3f".format(share)} of the runs" }
        signals.forEach { (kind, share) -> if (share < MIN_SIGNAL_KIND_SHARE) failures += "$kind signals in ${"%.3f".format(share)} of the runs" }
        tags.forEach { (tag, share) -> if (share < MIN_TAG_SHARE) failures += "$tag is the Keynote of ${"%.3f".format(share)} of the runs" }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test
    fun withoutRunVarietyEveryRunHasOneSkeletonAndKeepsTheRunItHadWithoutTheSystems() {
        val off = canonicalGameplayContent.withoutRunVariety()
        val skeletons = populationOf(Seeds) { skeletonOf(it, off) }
        println("SKELETON-GATE OFF V=${"%.4f".format(variety(skeletons))} distinct=${skeletons.toSet().size} skeleton=${skeletons.first().parts}")
        assertEquals(0.0, variety(skeletons), "the control arm reads one skeleton")
        assertEquals(Skeleton(listOf(null, null, null, 0, emptyList<Any>(), emptyList<Any>(), emptyList<Any>(), emptyList<Any>(),
            List(THEMES) { -1 }, List(AFFIXES) { null })), skeletons.first(), "the control arm reads no system")

        val withoutKeynote = canonicalGameplayContent.withoutKeynote()
        val plainDirector = canonicalGameplayContent.withoutDirectorVariety()
        val mismatches = populationOf(Seeds) { seed ->
            val disabled = startedRun(seed, off)
            buildList {
                if (startFacts(disabled) != startFacts(startedRun(seed, canonicalGameplayContent))) add("$seed opening or streams")
                if (disabled.itemDeck.dealtIds() != startedRun(seed, withoutKeynote).itemDeck.dealtIds()) add("$seed deal")
                if (timeline(seed, off) != timeline(seed, plainDirector)) add("$seed director")
            }
        }.flatten()
        assertTrue(mismatches.isEmpty(), "the control arm moved ${mismatches.size} facts: ${mismatches.take(12)}")
    }

    /** One run's ten skeleton components, compared by equality. */
    private data class Skeleton(val parts: List<Any?>)

    private companion object {
        val Seeds: List<Int> = varietyMetricSeeds(200)
        const val COMPONENTS = 10
        const val THEMES = 5
        const val AFFIXES = 4
        const val SKELETON_SECONDS = 400f

        const val MIN_VARIETY = 0.75
        const val MAX_DUPLICATE_SHARE = 0.01
        const val PERSONALITY_TOLERANCE = 0.05
        const val MIN_EVENT_KIND_SHARE = 0.30
        // One of five signal kinds per run: every kind should still appear across the population.
        const val MIN_SIGNAL_KIND_SHARE = 0.10
        const val MIN_TAG_SHARE = 0.05

        val shipped: List<Skeleton> by lazy { populationOf(Seeds) { skeletonOf(it, canonicalGameplayContent) } }

        /** The skeleton of [seed]'s run on [content]: the planned components at the start, then a 400 s NoPoi run. */
        fun skeletonOf(seed: Int, content: GameplayContentSnapshot): Skeleton {
            var start: List<Any?>? = null
            val themes = ArrayList<Int>()
            val affixes = ArrayList<Any?>()
            val elites = HashSet<Int>()
            var surges = 0
            flyVarietyRun(seed, content, until = SKELETON_SECONDS) { state ->
                if (start == null) {
                    val plans = state.worldEventPlans
                    val signals = content.variety.signals
                    start = listOf(
                        state.directorPersonality, state.keynoteTag, state.keynoteElement, plans.size, plans.map { it.kind },
                        plans.map { (it.startsAt / 60f).toInt() }, plannedSignalKinds(state.runSeed, signals),
                        plannedSignalSlots(state.runSeed, signals),
                    )
                }
                if (state.surgeIndex != surges) {
                    surges = state.surgeIndex
                    if (themes.size < THEMES) themes += state.surgeThemeType
                }
                for (enemy in state.enemies) {
                    if (enemy.type == EnemyType.ELITE && elites.add(enemy.id) && affixes.size < AFFIXES) affixes += enemy.affix
                }
            }
            while (themes.size < THEMES) themes += -1
            while (affixes.size < AFFIXES) affixes += null
            return Skeleton(checkNotNull(start) + listOf(themes.toList(), affixes.toList()))
        }

        /**
         * The mean normalized Hamming distance over every pair of [skeletons]: the mean over components of the share of
         * pairs that differ there, counted from each component's value frequencies.
         */
        fun variety(skeletons: List<Skeleton>): Double = (0 until COMPONENTS).sumOf { componentDistance(skeletons, it) } / COMPONENTS

        /** The share of pairs of [skeletons] whose component [index] differs (0 for fewer than two). */
        fun componentDistance(skeletons: List<Skeleton>, index: Int): Double {
            if (skeletons.size < 2) return 0.0
            val counts = HashMap<Any?, Int>()
            for (skeleton in skeletons) counts.merge(skeleton.parts[index], 1, Int::plus)
            val pairs = skeletons.size.toDouble() * (skeletons.size - 1) / 2.0
            return 1.0 - counts.values.sumOf { it.toDouble() * (it - 1) / 2.0 } / pairs
        }

        fun <K> shares(map: Map<K, Double>): String = map.entries.joinToString(",", "{", "}") { "${it.key}=${"%.3f".format(it.value)}" }

        fun startedRun(seed: Int, content: GameplayContentSnapshot): MutableGameState = MutableGameState(content, seed = seed).apply {
            unlockWeaponsForTesting(ReferenceArmory)
            applyPreferences(settings.copy(simulationSpeed = 1f))
            startRun()
        }

        /** The opening packs and the positions of the gameplay, spawn and reward streams after the start. */
        fun startFacts(state: MutableGameState): List<Any> = listOf(
            state.enemies.map { "${it.type}@${it.x},${it.y}" }, state.gameplayRandom.snapshot(), state.spawnRandom.snapshot(),
            state.rewardRandom.snapshot(),
        )

        /** Every enemy of the director's first four minutes in arrival order (the budget gate's director flight). */
        fun timeline(seed: Int, content: GameplayContentSnapshot): List<String> =
            ArrayList<String>().also { flyDirector(seed, null, content, timeline = it) }
    }
}
