// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.DraftDeckPolicy
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RunModifierEffect
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.usesSkills
import kinetickk.ball.gameplay.nucleus.model.ItemDeck
import kinetickk.ball.gameplay.nucleus.model.weaknessElement
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The run's Keynote (variety.md §8): drawn from the deck stream after the deal, its deck floors, the aspect it features
 * for Elites, its projection and its banner. The long sweeps are in KeynoteSweepTest and DraftDeckSweepTest.
 */
class KeynoteTest {
    private val keynote = canonicalGameplayContent.variety.keynote
    private val withoutKeynote = canonicalGameplayContent.withoutKeynote()

    @Test
    fun theKeynoteFollowsTheDealSoTheDealtOrderAndEveryOtherStreamStayPut() {
        for (seed in 1..12) {
            val rebirth = seed % 3 * 5
            val on = buildTestState(seed = seed, rebirth = rebirth)
            val off = buildTestState(seed = seed, rebirth = rebirth, content = withoutKeynote)
            val tag = assertNotNull(on.keynoteTag)
            val element = assertNotNull(on.keynoteElement)
            assertNull(off.keynoteTag)
            assertNull(off.keynoteElement)
            assertEquals(off.rewardRandom.snapshot(), on.rewardRandom.snapshot(), "Seed $seed reward stream")
            assertEquals(off.gameplayRandom.snapshot(), on.gameplayRandom.snapshot(), "Seed $seed gameplay stream")
            assertEquals(off.spawnRandom.snapshot(), on.spawnRandom.snapshot(), "Seed $seed spawn stream")
            // The same deal order: the items neither deck dealt keep the same priority.
            assertEquals(off.itemDeck.reserve().filter { it !in on.itemDeck }, on.itemDeck.reserve().filter { it !in off.itemDeck })
            // The Keynote only adds its passives; the wager and skill floors after it may then need fewer of their own.
            val added = (on.itemDeck.dealtIds() - off.itemDeck.dealtIds().toSet()).map { on.content.items[it] }
            val dropped = (off.itemDeck.dealtIds() - on.itemDeck.dealtIds().toSet()).map { on.content.items[it] }
            added.forEach { item ->
                assertTrue(item.featuresTag(tag) || item.featuresElement(element) || item.risk || item.usesSkills, "Seed $seed added ${item.name}")
            }
            dropped.forEach { item -> assertTrue(item.risk || item.usesSkills, "Seed $seed dropped ${item.name}") }
        }
    }

    @Test
    fun aKeynoteDeckHoldsItsTagAndElementShares() {
        for (seed in 1..12) {
            val state = buildTestState(seed = seed, rebirth = seed % 3 * 5)
            val tag = assertNotNull(state.keynoteTag)
            val element = assertNotNull(state.keynoteElement)
            val universe = state.content.items.filter { it.minimumRebirth <= state.rebirthRank }
            assertShareHolds(state.itemDeck, universe.filter { it.featuresTag(tag) }, keynote.tagShare, "Seed $seed $tag")
            assertShareHolds(state.itemDeck, universe.filter { it.featuresElement(element) }, keynote.elementShare, "Seed $seed $element")
        }
    }

    @Test
    fun theFullPolicyStillDrawsTheKeynoteFromAFreshDeckStream() {
        val full = canonicalGameplayContent.copy(build = canonicalGameplayContent.build.copy(
            policy = canonicalGameplayContent.build.policy.copy(draftDeck = DraftDeckPolicy.FULL),
        ))
        repeat(10) { seed ->
            val state = buildTestState(seed = seed, content = full)
            val off = buildTestState(seed = seed, content = full.withoutKeynote())
            assertTrue(state.itemDeck.dealsEverything)
            val stream = CloneableXorWowRandom(state.deckSeed)
            assertEquals(BuildTag.entries[stream.nextInt(BuildTag.entries.size)], state.keynoteTag, "Seed $seed tag")
            assertEquals(StatusElement.entries[stream.nextInt(StatusElement.entries.size)], state.keynoteElement, "Seed $seed element")
            assertEquals(off.rewardRandom.snapshot(), state.rewardRandom.snapshot(), "Seed $seed reward stream")
            assertEquals(off.spawnRandom.snapshot(), state.spawnRandom.snapshot(), "Seed $seed spawn stream")
            assertNull(off.keynoteTag)
        }
    }

    @Test
    fun theKeynoteIsTheSameForASeedAndVariesAcrossSeeds() {
        val first = buildTestState(seed = 88)
        val again = buildTestState(seed = 88)
        assertEquals(first.keynoteTag, again.keynoteTag)
        assertEquals(first.keynoteElement, again.keynoteElement)
        assertEquals(first.itemDeck.dealtIds(), again.itemDeck.dealtIds())
        val keynotes = (1..30).map { seed -> buildTestState(seed = seed).let { it.keynoteTag to it.keynoteElement } }
        assertTrue(keynotes.map { it.first }.toSet().size >= 6, "Tags over 30 seeds: ${keynotes.map { it.first }.toSet()}")
        assertTrue(keynotes.map { it.second }.toSet().size >= 5, "Elements over 30 seeds: ${keynotes.map { it.second }.toSet()}")
    }

    @Test
    fun aKeynoteFloorEffectDealsReservePassivesInPriorityWithoutADraw() {
        val state = buildTestState(seed = 23)
        val tag = assertNotNull(state.keynoteTag)
        val element = assertNotNull(state.keynoteElement)
        val before = state.itemDeck
        val dealtBefore = before.dealtIds()
        val reward = state.rewardRandom.snapshot()
        val spawn = state.spawnRandom.snapshot()
        val universe = state.content.items.filter { it.minimumRebirth <= state.rebirthRank }
        val tagItems = universe.filter { it.featuresTag(tag) }
        val elementItems = universe.filter { it.featuresElement(element) }
        val share = 0.95f
        fun wanted(items: List<ItemDefinition>) = items.count { it.id in before }.let { dealt ->
            (0..items.size).first { it.toFloat() >= share * items.size } - dealt
        }.coerceAtLeast(0)

        state.applyRunModifier(RunModifierEffect.KeynoteFloor(share))

        assertShareHolds(state.itemDeck, tagItems, share, "$tag")
        assertShareHolds(state.itemDeck, elementItems, share, "$element")
        val added = state.itemDeck.dealtIds() - dealtBefore.toSet()
        assertTrue(added.isNotEmpty(), "The 0.95 share deals more than the 0.8 deal")
        assertTrue(added.size <= wanted(tagItems) + wanted(elementItems), "Only what the shares need: $added")
        added.forEach { id -> assertTrue(state.content.items[id].let { it.featuresTag(tag) || it.featuresElement(element) }) }
        // Deal priority: the tag floor takes the reserve's first tag passives, and the reserve keeps its order.
        assertTrue(before.reserve().filter { id -> tagItems.any { it.id == id } }.take(wanted(tagItems)).all { it in state.itemDeck })
        assertEquals(before.reserve().filter { it !in added }, state.itemDeck.reserve())
        assertEquals(reward, state.rewardRandom.snapshot(), "No reward draw")
        assertEquals(spawn, state.spawnRandom.snapshot(), "No spawn draw")
        assertEquals(dealtBefore, before.dealtIds(), "The earlier deck value is unchanged")
        // Once the shares hold there is nothing left to deal.
        val settled = state.itemDeck
        state.promoteKeynoteFloors(share)
        assertSame(settled, state.itemDeck)

        // A deck that deals everything, and a run without a Keynote, are left as they are.
        val whole = buildTestState(seed = 23).dealWholeCatalog()
        whole.promoteKeynoteFloors(1f)
        assertSame(ItemDeck.EVERYTHING, whole.itemDeck)
        val plain = buildTestState(seed = 23, content = withoutKeynote)
        val plainDeck = plain.itemDeck
        plain.promoteKeynoteFloors(1f)
        assertSame(plainDeck, plain.itemDeck)
    }

    @Test
    fun theFeaturedAspectIsTheOneWeakToTheKeynoteElement() {
        val expected = mapOf(
            StatusElement.THERMAL to StatusElement.CRYO,
            StatusElement.CRYO to StatusElement.THERMAL,
            StatusElement.ENTROPY to StatusElement.ION,
            StatusElement.GRAVITIC to StatusElement.ENTROPY,
            StatusElement.ION to StatusElement.GRAVITIC,
            StatusElement.VOID to StatusElement.SONIC,
            StatusElement.SONIC to StatusElement.VOID,
        )
        assertEquals(StatusElement.entries.toSet(), expected.keys)
        val state = buildTestState(seed = 3)
        expected.forEach { (element, aspect) ->
            assertEquals(aspect.ordinal, weakAspectFor(element), "$element")
            // An Elite of that aspect is weak to the element.
            val elite = state.spawnedEnemy(EnemyType.ELITE, 0f, 0f)
            assignAspect(elite, weakAspectFor(element), immune = true)
            assertEquals(element.ordinal, elite.weaknessElement)
        }
    }

    @Test
    fun aStartedRunFeaturesTheAspectWeakToItsKeynoteAndProjectsTheKeynote() {
        for (seed in 1..6) {
            val state = buildTestState(seed = seed)
            val element = assertNotNull(state.keynoteElement)
            assertEquals(weakAspectFor(element), state.featuredAspect)
            val model = state.toRenderModel()
            assertEquals(state.keynoteTag, model.keynoteTag)
            assertEquals(element, model.keynoteElement)
            assertEquals(state.featuredAspect, model.featuredAspect)
        }
        for (content in listOf(withoutKeynote, canonicalGameplayContent.withoutRunVariety())) {
            val model = buildTestState(seed = 4, content = content).toRenderModel()
            assertNull(model.keynoteTag)
            assertNull(model.keynoteElement)
            assertEquals(-1, model.featuredAspect)
        }
    }

    @Test
    fun elitesTakeTheFeaturedAspectAtTheBiasWithoutAStreamDraw() {
        // The plain director, so no Elemental Storm swaps the bias or the aspect (its own case follows).
        val plainDirector = canonicalGameplayContent.withoutDirectorVariety()
        val always = plainDirector.withKeynote(keynote.copy(eliteAspectBias = 1f))
        val never = plainDirector.withKeynote(keynote.copy(eliteAspectBias = 0f))
        for (seed in 1..8) {
            val biased = buildTestState(seed = seed, content = always)
            val plain = buildTestState(seed = seed, content = never)
            val off = buildTestState(seed = seed, content = plainDirector.withoutKeynote())
            val element = assertNotNull(biased.keynoteElement)
            repeat(6) {
                val elite = biased.spawnedEnemy(EnemyType.ELITE, 0f, 0f)
                val rolled = off.spawnedEnemy(EnemyType.ELITE, 0f, 0f).aspect
                assertEquals(biased.featuredAspect, elite.aspect, "Seed $seed bias 1")
                assertEquals(element.ordinal, elite.weaknessElement, "Seed $seed weak to the Keynote")
                assertEquals(rolled, plain.spawnedEnemy(EnemyType.ELITE, 0f, 0f).aspect, "Seed $seed bias 0")
            }
            // Heralds and ordinary enemies keep their own aspects.
            assertEquals(off.spawnedEnemy(EnemyType.HERALD, 0f, 0f).aspect, biased.spawnedEnemy(EnemyType.HERALD, 0f, 0f).aspect)
            assertEquals(off.spawnedEnemy(EnemyType.SHOOTER, 0f, 0f).aspect, biased.spawnedEnemy(EnemyType.SHOOTER, 0f, 0f).aspect)
            // The roll is a stateless hash: every stream is where the run without a Keynote left it.
            assertEquals(off.spawnRandom.snapshot(), biased.spawnRandom.snapshot(), "Seed $seed spawn stream")
            assertEquals(off.gameplayRandom.snapshot(), biased.gameplayRandom.snapshot(), "Seed $seed gameplay stream")
        }

        // The Elemental Storm uses its own bias.
        val storm = canonicalGameplayContent.withKeynote(keynote.copy(eliteAspectBias = 0f, stormEliteAspectBias = 1f))
        val stormRun = buildTestState(seed = 9, content = storm).apply {
            forcePersonalityForTesting(DirectorPersonalityId.ELEMENTAL_STORM)
        }
        val calmRun = buildTestState(seed = 9, content = storm.withoutDirectorVariety())
        val offRun = buildTestState(seed = 9, content = withoutKeynote)
        repeat(4) {
            val rolled = offRun.spawnedEnemy(EnemyType.ELITE, 0f, 0f).aspect
            assertEquals(stormRun.featuredAspect, stormRun.spawnedEnemy(EnemyType.ELITE, 0f, 0f).aspect)
            assertEquals(rolled, calmRun.spawnedEnemy(EnemyType.ELITE, 0f, 0f).aspect)
        }
    }

    @Test
    fun theKeynoteBannerNamesTheTagAndElementOnceAtItsTime() {
        val at = canonicalGameplayContent.variety.director.keynoteAnnounceAtSeconds
        // The hostile pattern, the first stage, was announced at its own earlier time.
        val state = buildTestState(seed = 31).apply {
            elapsed = at - 0.05f
            varietyAnnouncement = HOSTILE_PATTERN_ANNOUNCEMENT
            message = ""
        }
        val tag = assertNotNull(state.keynoteTag)
        val element = assertNotNull(state.keynoteElement)
        repeat(5) { state.simulateStep(MutableGameState.FIXED_STEP) }
        assertTrue(state.elapsed < at)
        assertEquals("", state.message)
        assertEquals(HOSTILE_PATTERN_ANNOUNCEMENT, state.varietyAnnouncement)
        while (state.varietyAnnouncement == HOSTILE_PATTERN_ANNOUNCEMENT) state.simulateStep(MutableGameState.FIXED_STEP)
        assertTrue(state.elapsed >= at && state.elapsed - MutableGameState.FIXED_STEP < at, "At the first step past ${state.elapsed}")
        assertEquals("KEYNOTE // ${tag.displayLabel.uppercase()} // ${element.displayLabel.uppercase()}", state.message)
        assertEquals(KEYNOTE_BANNER_SECONDS, state.messageTime)
        assertEquals(KEYNOTE_ANNOUNCEMENT, state.varietyAnnouncement)
        // Once per run.
        state.message = "LATER"
        repeat(60) { state.simulateStep(MutableGameState.FIXED_STEP) }
        assertEquals("LATER", state.message)

        // Without a Keynote the stage passes silently.
        val plain = buildTestState(seed = 31, content = withoutKeynote).apply {
            elapsed = at - 0.01f
            varietyAnnouncement = HOSTILE_PATTERN_ANNOUNCEMENT
            message = ""
        }
        repeat(3) { plain.simulateStep(MutableGameState.FIXED_STEP) }
        assertEquals(KEYNOTE_ANNOUNCEMENT, plain.varietyAnnouncement)
        assertEquals("", plain.message)
    }

    private fun assertShareHolds(deck: ItemDeck, items: List<ItemDefinition>, share: Float, label: String) {
        val dealt = items.count { it.id in deck }
        assertTrue(dealt.toFloat() >= share * items.size, "$label: $dealt of ${items.size} dealt, share $share")
    }
}

/** A passive item at most Rare with the Keynote's [tag], the kind its tag floor deals. */
internal fun ItemDefinition.featuresTag(tag: BuildTag): Boolean =
    !isActive && rarity.rank <= ItemRarity.RARE.rank && tag in tags

/** A passive item at most Rare with the Keynote's [element], the kind its element floor deals. */
internal fun ItemDefinition.featuresElement(element: StatusElement): Boolean =
    !isActive && rarity.rank <= ItemRarity.RARE.rank && element in elements
