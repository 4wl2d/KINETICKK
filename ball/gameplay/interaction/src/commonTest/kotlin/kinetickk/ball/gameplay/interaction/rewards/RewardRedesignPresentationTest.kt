// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.SynergyId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.canvas.overlaySynergyLinks
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.RewardPreview
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RewardRedesignPresentationTest {
    private val flywheel = EquippedRelic(RelicId.KINETIC_FLYWHEEL, 2)
    private val ghost = EquippedRelic(RelicId.GHOST_VECTOR, 1)
    private val nail = EquippedRelic(RelicId.ORBITAL_NAIL, 1)

    @Test
    fun theLevelBadgeOnlyAnnouncesALevelUpAndLayoutsFollowTheChoiceType() {
        val levelUp = rewardFixtureModel(choices = listOf(itemChoice(0), itemChoice(1), itemChoice(2))).rewardPresentation()
        assertEquals(15, levelUp.level)
        assertEquals(RewardLayoutKind.CARDS, levelUp.kind)
        assertEquals(1, levelUp.defaultSelection(), "The middle card is lifted first, as dealt on the board")
        val directed = rewardFixtureModel(choices = listOf(itemChoice(0)), directedChoice = true).rewardPresentation()
        assertNull(directed.level, "A point-of-interest reward is not a level-up")
        val weapon = rewardFixtureModel(choiceType = ChoiceType.WEAPON, choices = listOf(
            ChoiceOption(ChoiceType.WEAPON, "Arc Coil", "", "CHAIN", weaponId = WeaponId.ARC_COIL))).rewardPresentation()
        assertNull(weapon.level)
        val totem = rewardFixtureModel(choiceType = ChoiceType.TOTEM, choices = listOf(
            ChoiceOption(ChoiceType.TOTEM, "Amplify Flux Wake", "", "", weaponId = WeaponId.FLUX_WAKE, totemAction = TotemAction.AMPLIFY_CURRENT),
        )).rewardPresentation()
        assertEquals(RewardLayoutKind.TOTEM, totem.kind)
        assertEquals(0, totem.defaultSelection())
    }

    @Test
    fun itemCardsReadRarityStackAndPlainOrComparedValuesWithoutArrows() {
        val plain = rewardFixtureModel(choices = listOf(itemChoice(1)), itemStacks = listOf(0, 1, 0, 0, 0)).rewardCardPresentation(itemChoice(1), 0)
        assertEquals(5, plain.rank)
        assertEquals("Legendary", plain.bandStart)
        assertEquals("Stack 2/3", plain.bandEnd)
        assertEquals("Precision family", plain.family)
        assertEquals(listOf("+19%", "+14%"), plain.changes.map { it.after })
        assertTrue(plain.changes.all { it.before.isEmpty() })
        assertTrue(plain.isNewDiscovery)

        val compared = rewardFixtureModel(
            choices = listOf(itemChoice(0)), discoveredItems = setOf(0),
            previews = listOf(RewardPreview(immutableListOf(RewardStatChange("Dash power", 873f, 1083f, "")))),
        ).rewardCardPresentation(itemChoice(0), 0)
        assertEquals("873", compared.changes.single().before)
        assertEquals("1083", compared.changes.single().after)
        assertEquals(false, compared.isNewDiscovery)
        val text = listOf(plain, compared).flatMap { card ->
            card.changes.flatMap { listOf(it.name, it.before, it.after) } + card.descriptions + listOfNotNull(card.bandStart, card.bandEnd, card.family) + card.operation
        }
        assertTrue(text.none { it.contains('→') || it.contains('·') }, text.toString())
    }

    @Test
    fun nucleusConditionLabelsBecomeANameAndAConditionInBothLanguages() {
        val english = RewardStatChange("Damage: speed ≥500", 5f, 10f, "%").presentation(AppLanguage.English)
        assertEquals("Damage", english.name)
        assertEquals("speed ≥500", english.condition)
        val russian = RewardStatChange("Damage: speed ≥500", 5f, 10f, "%").presentation(AppLanguage.Russian)
        assertEquals("Урон", russian.name)
        assertEquals("скорость ≥500", russian.condition)
        val single = RewardStatChange("Pull: hit", 0f, 24f, "u/s").presentation(AppLanguage.Russian)
        assertEquals("Притяжение при попадании", single.name)
        assertNull(single.condition)
        assertNull(RewardStatChange("Dash heat", 36f, 33f, "").presentation(AppLanguage.English).condition)
    }

    @Test
    fun relicActionsNameTheGameOutcomeForEveryRelicChoiceAction() {
        val policy = RelicPolicy(4, 5)
        fun label(action: RelicChoiceAction, owned: Int = 0, slotRank: Int? = null, equipped: Int = 2) =
            rewardRelicAction(action, owned, slotRank, equipped, policy, AppLanguage.English)
        assertEquals(RewardAction("Bind", RewardTone.YOU), label(RelicChoiceAction.ACQUIRE))
        assertEquals(RewardAction("Meld", RewardTone.YOU), label(RelicChoiceAction.ACQUIRE, owned = 2))
        assertEquals(RewardAction("Salvage", RewardTone.MUTE), label(RelicChoiceAction.ACQUIRE, owned = 5))
        assertEquals(RewardAction("Replace", RewardTone.THREAT), label(RelicChoiceAction.ACQUIRE, equipped = 4))
        assertEquals(RewardAction("Meld", RewardTone.YOU), label(RelicChoiceAction.MELD))
        assertEquals(RewardAction("Replace", RewardTone.THREAT), label(RelicChoiceAction.REPLACE, slotRank = 3))
        assertEquals(RewardAction("Meld", RewardTone.YOU), label(RelicChoiceAction.MELD_TARGET, slotRank = 4))
        assertEquals(RewardAction("Salvage", RewardTone.MUTE), label(RelicChoiceAction.MELD_TARGET, slotRank = 5))
    }

    @Test
    fun matrixSynergiesFollowAspectPairsAndRelicPairs() {
        val content = rewardFixtureContent
        val vector = overlaySynergyLinks(listOf(RelicId.KINETIC_FLYWHEEL, RelicId.GHOST_VECTOR, RelicId.ORBITAL_NAIL, null), content)
        assertEquals(listOf(SynergyId.VECTOR_MANEUVER), vector.map { it.definition.id })
        assertEquals(listOf(0, 1), vector.single().slots)
        // Two copies of one relic are not two different relics of the aspect.
        assertTrue(overlaySynergyLinks(listOf(RelicId.KINETIC_FLYWHEEL, RelicId.KINETIC_FLYWHEEL, null, null), content).isEmpty())
        val pair = overlaySynergyLinks(listOf(RelicId.GHOST_VECTOR, RelicId.ORBITAL_NAIL, RelicId.MIRROR_CUT, null), content)
        assertEquals(listOf(SynergyId.GHOST_MIRROR), pair.map { it.definition.id })
        assertEquals(listOf(0, 2), pair.single().slots)
        // Sovereign relics never form an aspect synergy.
        assertTrue(overlaySynergyLinks(listOf(RelicId.CROWN_OF_FOUR_WINDS, RelicId.ENGINE_OF_PARADOX, null, null), content).isEmpty())
    }

    @Test
    fun relicPreviewsPlaceTheIncomingRelicAndSignWhatTheChoiceGainsKeepsAndBreaks() {
        val acquire = ChoiceOption(ChoiceType.RELIC, "Periapsis Hook", "", "GRAVITIC", relicId = RelicId.PERIAPSIS_HOOK, relicAction = RelicChoiceAction.ACQUIRE)
        val model = rewardFixtureModel(choiceType = ChoiceType.RELIC, relics = listOf(flywheel, ghost, nail), choices = listOf(acquire),
            previews = listOf(RewardPreview(addedSynergies = immutableListOf("Gravitic grouping"))))
        val card = model.rewardCardPresentation(acquire, 0)
        val preview = requireNotNull(card.relicPreview)
        assertEquals(3, preview.targetSlot, "A new relic binds to the free slot")
        assertEquals(false, preview.replace)
        assertEquals(listOf(SynergyId.GRAVITIC_GROUPING), preview.addedLinks.map { it.definition.id })
        assertEquals(listOf(2, 3), preview.addedLinks.single().slots)
        assertEquals(RewardAction("Bind", RewardTone.YOU), card.action)
        // The band names the resulting rank; the card body keeps its lines for the relic's effects.
        assertEquals("Rank 1", card.bandEnd)
        assertTrue(card.changes.none { it.name == "Rank" })
        val matrix = requireNotNull(model.rewardPresentation().relicMatrix)
        assertEquals(3, matrix.equipped)
        assertEquals(listOf("Kinetic Flywheel", "Ghost Vector", "Orbital Nail", "Free slot"), matrix.names)

        val replaceFlywheel = ChoiceOption(ChoiceType.RELIC_BIND, "Replace Kinetic Flywheel", "", "", relicId = RelicId.PERIAPSIS_HOOK,
            relicAction = RelicChoiceAction.REPLACE, relicSlot = 0)
        val full = rewardFixtureModel(choiceType = ChoiceType.RELIC_BIND, relics = listOf(flywheel, ghost, nail, EquippedRelic(RelicId.VOLTAIC_FILAMENT, 1)),
            choices = listOf(replaceFlywheel),
            previews = listOf(RewardPreview(addedSynergies = immutableListOf("Gravitic grouping"), removedSynergies = immutableListOf("Vector maneuver"))))
        val replace = requireNotNull(full.rewardCardPresentation(replaceFlywheel, 0).relicPreview)
        assertEquals(0, replace.targetSlot)
        assertTrue(replace.replace)
        assertEquals(setOf(SynergyId.VECTOR_MANEUVER), replace.removedSynergies)
        assertEquals(listOf("−", "−", "+"), replace.rows.map { it.sign }, "Lost relic, broken synergy, formed synergy")
        assertEquals("Replace", replace.primaryLabel)
        val presentation = full.rewardPresentation()
        assertEquals(RewardLayoutKind.RELIC_BIND, presentation.kind)
        assertNull(presentation.defaultSelection(), "Nothing is armed before the player lifts a slot")
        assertEquals("Periapsis Hook", presentation.relicPanel?.name)

        val meld = ChoiceOption(ChoiceType.RELIC_BIND, "Meld Kinetic Flywheel", "", "", relicId = RelicId.KINETIC_FLYWHEEL,
            relicAction = RelicChoiceAction.MELD_TARGET, relicSlot = 0)
        val melding = rewardFixtureModel(choiceType = ChoiceType.RELIC_BIND, relics = listOf(flywheel, ghost), choices = listOf(meld))
        val meldPreview = requireNotNull(melding.rewardCardPresentation(meld, 0).relicPreview)
        assertEquals(listOf("+", "="), meldPreview.rows.map { it.sign }, "Next rank, kept synergy")
        assertEquals("Rank 3", meldPreview.rows.first().title)
        assertEquals("Rank 3", meldPreview.panel?.rank)
        assertEquals(0, melding.rewardPresentation().defaultSelection())
    }

    @Test
    fun bracketsStackOverlappingSynergiesAndMarkTheOnesAChoiceBreaks() {
        val model = rewardFixtureModel(choiceType = ChoiceType.RELIC_BIND, relics = listOf(flywheel, ghost, nail, EquippedRelic(RelicId.MIRROR_CUT, 1)))
        val matrix = requireNotNull(model.rewardPresentation().relicMatrix)
        assertEquals(setOf(SynergyId.VECTOR_MANEUVER, SynergyId.GHOST_MIRROR), matrix.links.map { it.definition.id }.toSet())
        val brackets = relicBrackets(matrix, null)
        val maneuver = brackets.single { it.link.definition.id == SynergyId.VECTOR_MANEUVER }
        val mirror = brackets.single { it.link.definition.id == SynergyId.GHOST_MIRROR }
        assertNotEquals(maneuver.level, mirror.level, "Overlapping spans take separate levels")
        val breaking = RewardRelicPreview(0, true, null, emptyList(), emptyList(), setOf(SynergyId.VECTOR_MANEUVER), emptyList(), null, "", "")
        assertTrue(relicBrackets(matrix, breaking).single { it.link.definition.id == SynergyId.VECTOR_MANEUVER }.faded)
    }

    @Test
    fun cardsAreDealtWithTheBoardRotationsAndStagger() {
        assertEquals(listOf(-4f, 0f, 3f), (0..2).map { rewardCardRestRotation(it, 3) })
        assertEquals(0f, rewardCardRestRotation(0, 1))
        assertTrue((0..3).map { rewardCardRestRotation(it, 4) }.zipWithNext().all { (a, b) -> a < b })
        assertEquals(0f, rewardDealProgress(120f, 0, 1))
        assertEquals(0f, rewardDealProgress(210f, 1, 1), "90 ms stagger")
        assertEquals(1f, rewardDealProgress(720f, 0, 3), "Common to rare land in 600 ms")
        assertTrue(rewardDealProgress(720f, 0, 5) < 1f, "Epic and legendary take 750 ms")
    }

    @Test
    fun overlayCopyIsTranslatedKeepsArgumentsAndFollowsTheHardRules() {
        val arguments = Regex("\\{\\d+}")
        val forbidden = charArrayOf('·', '→', '←', '↑', '↓', '↵', '›', '‹', '▶', '◀', '◇', '§')
        OverlayRedesignText.entries.forEach { resource ->
            assertTrue(resource.english.isNotBlank() && resource.russian.isNotBlank(), "$resource")
            assertNotEquals(resource.english, resource.russian, "Untranslated $resource")
            assertEquals(arguments.findAll(resource.english).map { it.value }.toList().sorted(),
                arguments.findAll(resource.russian).map { it.value }.toList().sorted(), "$resource arguments")
            listOf(resource.english, resource.russian).forEach { text ->
                assertTrue(text.none { it in forbidden }, "$resource: $text")
                assertTrue(!Regex("\\bL\\d+\\b|LV |Lv\\.").containsMatchIn(text), "$resource level label: $text")
            }
        }
        assertEquals("Lvl", OverlayRedesignText.LevelLabel.english)
        assertEquals("Ур.", OverlayRedesignText.LevelLabel.russian)
    }

    @Test
    fun meldingUsesOneTermForTheHeadingAndItsAction() {
        val english = rewardHeading(ChoiceType.RELIC_BIND, RelicChoiceAction.MELD_TARGET, AppLanguage.English)
        val action = rewardRelicAction(RelicChoiceAction.MELD_TARGET, 0, 1, 3, RelicPolicy(4, 5), AppLanguage.English)
        assertEquals(OverlayRedesignText.Meld.english, english)
        assertEquals(english, action.label)
        // Russian pairs the noun and the verb of the same word (Слияние / Слить).
        val russian = rewardHeading(ChoiceType.RELIC_BIND, RelicChoiceAction.MELD_TARGET, AppLanguage.Russian)
        assertEquals(russian.take(3), OverlayRedesignText.Meld.russian.take(3))
    }

    @Test
    fun relicPreviewPlansShrinkFromTheFullPreviewToTheTersest() {
        val plans = relicPreviewPlans(rowCount = 3, statCount = 2)
        assertEquals(RelicPreviewPlan(3, 3, 2), plans.first(), "Everything first")
        assertEquals(RelicPreviewPlan(1, 0, 0), plans.last(), "At least one titled row always shows")
        // Stat lines go before any detail line, and details before whole rows.
        val firstWithoutStats = plans.indexOfFirst { it.stats == 0 }
        val firstWithoutDetail = plans.indexOfFirst { it.detailRows < it.rows }
        val firstWithoutRow = plans.indexOfFirst { it.rows < 3 }
        assertTrue(firstWithoutStats < firstWithoutDetail && firstWithoutDetail < firstWithoutRow)
        plans.zipWithNext().forEach { (fuller, terser) ->
            assertTrue(terser.rows <= fuller.rows && terser.detailRows <= fuller.detailRows && terser.stats <= fuller.stats)
            assertTrue(terser != fuller)
        }
        assertEquals(listOf(RelicPreviewPlan(0, 0, 0)), relicPreviewPlans(0, 0))
    }
}
