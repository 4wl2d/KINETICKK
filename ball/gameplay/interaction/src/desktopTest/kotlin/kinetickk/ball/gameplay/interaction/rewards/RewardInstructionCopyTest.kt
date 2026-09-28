// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The game's choice descriptions for the weapon totem, the directed-reward focus step and the
 * relic meld are step-by-step instructions ("Choose…, then…"). No reward layout shows them, in
 * the presentation or on screen, in English or Russian, at any reference frame.
 */
@OptIn(ExperimentalTestApi::class)
class RewardInstructionCopyTest {
    private val frames = listOf(1440 to 810, 844 to 390, 390 to 844)
    private val relics4 = listOf(
        EquippedRelic(RelicId.KINETIC_FLYWHEEL, 2), EquippedRelic(RelicId.GHOST_VECTOR, 1),
        EquippedRelic(RelicId.ORBITAL_NAIL, 1), EquippedRelic(RelicId.VOLTAIC_FILAMENT, 5),
    )

    @Test
    fun offersNeverRepeatTheGamesInstructionSentences() {
        for (language in AppLanguage.entries) scenes(language, 1440f, 810f).forEach { (name, model) ->
            val instructions = instructionTexts(model, language)
            val presentation = model.rewardPresentation(language)
            presentation.cards.forEach { card ->
                card.descriptions.forEach { line -> assertTrue(line !in instructions, "$name $language card line \"$line\"") }
                card.totemRow?.let { row -> assertTrue(row.description !in instructions, "$name $language totem row \"${row.description}\"") }
                card.relicPreview?.rows?.forEach { row -> assertTrue(row.detail !in instructions, "$name $language preview row \"${row.detail}\"") }
            }
        }
    }

    @Test
    fun focusChangeAndMeldOffersShowTheirEffectInstead() {
        for (language in AppLanguage.entries) {
            val scenes = scenes(language, 1440f, 810f).toMap()
            val items = scenes.getValue("focus-items").rewardPresentation(language)
            items.cards.forEach { assertEquals(listOf(language.text(OverlayRedesignText.FocusArtifacts)), it.descriptions) }
            val weapons = scenes.getValue("focus-weapons").rewardPresentation(language)
            assertEquals(RewardLayoutKind.TOTEM, weapons.kind)
            weapons.cards.forEach { card -> assertTrue(card.totemRow!!.description.isNotBlank()) }
            val totem = scenes.getValue("totem").rewardPresentation(language)
            assertEquals(language.text(OverlayRedesignText.ChangeWeaponEffect),
                totem.cards.single { it.choice.totemAction == TotemAction.CHANGE_WEAPON }.totemRow!!.description)
            val meld = scenes.getValue("relic-meld-offer").rewardPresentation(language)
            assertEquals(listOf(language.text(OverlayRedesignText.MeldEffect)),
                meld.cards.single { it.choice.relicAction == RelicChoiceAction.MELD }.descriptions)
        }
    }

    @Test
    fun noRewardLayoutRendersAnInstructionSentence() {
        for ((width, height) in frames) for (language in AppLanguage.entries) runDesktopComposeUiTest(width, height) {
            val current = mutableStateOf<GameplayRenderModel?>(null)
            setContent {
                val model = current.value ?: return@setContent
                CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f)) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        RewardContent(model, choiceLayoutGeometry(width.toFloat(), height.toFloat(), 1f, model.choices.size, model.choicesCanReroll),
                            0f, true, {}, {})
                    }
                }
            }
            scenes(language, width.toFloat(), height.toFloat()).forEach { (name, model) ->
                current.value = model
                mainClock.advanceTimeBy(2_000)
                waitForIdle()
                val instructions = instructionTexts(model, language).map { it.lowercase() }
                val shown = onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                    .fetchSemanticsNodes().flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text.lowercase() } }
                assertTrue(shown.isNotEmpty(), "$name ${width}x$height $language renders text")
                shown.forEach { text ->
                    assertTrue(instructions.none { text.contains(it) }, "$name ${width}x$height $language shows the instruction \"$text\"")
                }
            }
        }
    }

    /**
     * The step and rule descriptions of [model]'s focus, totem and relic-slot choices (source and
     * localized): the lines no layout may show. Item, weapon and relic offers carry catalog text.
     */
    private fun instructionTexts(model: GameplayRenderModel, language: AppLanguage): Set<String> = model.choices
        .filter { it.rewardFocus != null || it.totemAction != null || it.relicAction in StepActions }
        .flatMap { listOf(it.description, it.description.localizedContent(language)) }.filter(String::isNotBlank).toSet()

    /** The game's own choices, built with the nucleus strings (ProgressionSystem, PointOfInterestSystem). */
    private fun scenes(language: AppLanguage, width: Float, height: Float): List<Pair<String, GameplayRenderModel>> {
        fun focus(type: ChoiceType, vararg focuses: RewardFocus) = focuses.map { focus ->
            ChoiceOption(type, focus.label, FocusDescription, "FOCUS", rewardFocus = focus)
        }
        return listOf(
            "totem" to rewardFixtureModel(
                choiceType = ChoiceType.TOTEM, weaponLevel = 5, language = language, width = width, height = height,
                choices = listOf(
                    ChoiceOption(ChoiceType.TOTEM, "Amplify Flux Wake", "Advance the current system from level 5 to 6 immediately.", "RESONANT",
                        weaponId = WeaponId.FLUX_WAKE, totemAction = TotemAction.AMPLIFY_CURRENT),
                    ChoiceOption(ChoiceType.TOTEM, "Change weapon", "Recalibrate the Totem, then choose one of three different weapon systems.",
                        "RECALIBRATE", totemAction = TotemAction.CHANGE_WEAPON),
                ),
            ),
            "focus-items" to rewardFixtureModel(
                choiceType = ChoiceType.ITEM, directedChoice = true, language = language, width = width, height = height,
                choices = focus(ChoiceType.ITEM, RewardFocus.OFFENSE, RewardFocus.DEFENSE, RewardFocus.ECONOMY),
            ),
            "focus-relics" to rewardFixtureModel(
                choiceType = ChoiceType.RELIC, directedChoice = true, relics = relics4.take(3), language = language, width = width, height = height,
                choices = focus(ChoiceType.RELIC, RewardFocus.VECTOR, RewardFocus.RIFT, RewardFocus.ENTROPY),
            ),
            "focus-weapons" to rewardFixtureModel(
                choiceType = ChoiceType.TOTEM, directedChoice = true, language = language, width = width, height = height,
                choices = focus(ChoiceType.TOTEM, RewardFocus.MOTION, RewardFocus.CONTROL, RewardFocus.STRIKE),
            ),
            "relic-meld-offer" to rewardFixtureModel(
                choiceType = ChoiceType.RELIC, relics = relics4, language = language, width = width, height = height,
                choices = listOf(RelicId.PERIAPSIS_HOOK, RelicId.ECHO_CHAMBER).map { id ->
                    val relic = rewardFixtureContent.relic(id)
                    ChoiceOption(ChoiceType.RELIC, relic.name, relic.description, relic.aspect.displayLabel.uppercase(),
                        relicId = id, relicAction = RelicChoiceAction.ACQUIRE)
                } + ChoiceOption(ChoiceType.RELIC, "Meld resonance",
                    "Collapse this offering into one of the four bound Relics and raise its rank.", "MELD", relicAction = RelicChoiceAction.MELD),
            ),
            "relic-meld-target" to rewardFixtureModel(
                choiceType = ChoiceType.RELIC_BIND, relics = relics4, language = language, width = width, height = height,
                choices = relics4.mapIndexed { index, equipped ->
                    ChoiceOption(ChoiceType.RELIC_BIND, "Meld ${rewardFixtureContent.relic(equipped.id).name}",
                        if (equipped.rank < 5) "Collapse the offering into slot ${index + 1} and advance rank ${equipped.rank} to ${equipped.rank + 1}."
                        else "Slot ${index + 1} is already rank 5; salvage the excess resonance.",
                        "SLOT ${index + 1} // RANK ${equipped.rank}", relicId = equipped.id, relicAction = RelicChoiceAction.MELD_TARGET, relicSlot = index)
                },
            ),
        )
    }
}

private val StepActions = setOf(RelicChoiceAction.MELD, RelicChoiceAction.MELD_TARGET, RelicChoiceAction.REPLACE)

/** The nucleus focus-step description (PointOfInterestSystem.openDirectedReward). */
private const val FocusDescription = "Choose a direction, then one of three matching offers."
