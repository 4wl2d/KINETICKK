// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.canvas.isCut
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.RewardPreview
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.KkTextRole
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kinetickk.ball.gameplay.interaction.canvas.fitOverlayText
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Russian and English reward overlays at the three reference frames: no text is cut off, no line
 * breaks inside a word, card bodies fit without clipping, pinned tags stay on their cards and the
 * signed relic preview never runs under its action.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayTextFitTest {
    private val frames = listOf(1440 to 810, 844 to 390, 390 to 844)
    private val relics3 = listOf(EquippedRelic(RelicId.KINETIC_FLYWHEEL, 2), EquippedRelic(RelicId.GHOST_VECTOR, 1), EquippedRelic(RelicId.ORBITAL_NAIL, 1))
    private val relics4 = relics3 + EquippedRelic(RelicId.VOLTAIC_FILAMENT, 1)

    @Test
    fun levelUpAndRelicCardsFitTheirTextWholeInBothLanguages() {
        forEachFrame(::levelUp, ::relicOffers) { width, height, model ->
            assertTextFitsWithoutBreakingWords("${model.choiceType} ${width}x$height")
            model.choices.indices.forEach { index ->
                val body = onNode(hasTestTag("kinetickk.gameplay.choice.${index + 1}.text"), useUnmergedTree = true).fetchSemanticsNode()
                val range = body.config[SemanticsProperties.VerticalScrollAxisRange]
                assertEquals(0f, range.maxValue(), "${model.choiceType} card ${index + 1} at ${width}x$height fits without scrolling")
            }
            if (model.choiceType == ChoiceType.RELIC) {
                model.choices.indices.forEach { index ->
                    val card = onNodeWithTag("kinetickk.gameplay.choice.${index + 1}").fetchSemanticsNode().boundsInRoot
                    val footer = onNode(hasTestTag("kinetickk.gameplay.choice.${index + 1}.footer"), useUnmergedTree = true)
                        .fetchSemanticsNode().boundsInRoot
                    assertTrue(card.inflate(2f).contains(footer.topLeft) && card.inflate(2f).contains(footer.bottomRight),
                        "Relic action tag ${index + 1} stays on its card at ${width}x$height: $footer in $card")
                }
            }
        }
    }

    @Test
    fun relicBindingAndTotemRowsFitAndThePreviewNeverRunsUnderTheAction() {
        forEachFrame(::replacing, ::melding, ::totem) { width, height, model ->
            assertTextFitsWithoutBreakingWords("${model.choiceType} ${width}x$height")
            if (model.choiceType == ChoiceType.RELIC_BIND) {
                val take = onNodeWithTag("kinetickk.gameplay.take").fetchSemanticsNode().boundsInRoot
                val rows = onAllNodes(hasTestTag("kinetickk.gameplay.rewards.preview-row"), useUnmergedTree = true).fetchSemanticsNodes()
                assertTrue(rows.isNotEmpty(), "The signed preview shows at ${width}x$height")
                rows.forEach { row ->
                    val bounds = row.boundsInRoot
                    assertTrue(bounds.bottom <= take.top + 1f && bounds.top >= 0f && bounds.right <= width + 1f,
                        "Preview row $bounds clear of $take at ${width}x$height")
                }
            }
        }
    }

    @Test
    fun pauseCanvasTextsShrinkToTheirBoxesInsteadOfBeingCut() {
        runDesktopComposeUiTest(400, 200) {
            val fitted = mutableListOf<Pair<Float, TextLayoutResult>>()
            setContent {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian, LocalDensity provides Density(1f)) {
                    val measurer = rememberKkCanvasMeasurer(1f)
                    SideEffect {
                        fitted.clear()
                        // Phone stat label beside its value, run panel label, synergy description on two lines.
                        fitted += 150f to fitOverlayText(measurer, "Максимальная прочность", KkTextRole.BODY, 14f, 150f)
                        fitted += 76f to fitOverlayText(measurer, "Лучшая серия", KkTextRole.MONO, 8f, 76f, uppercase = true, minScale = 0.65f)
                        fitted += 361f to fitOverlayText(measurer, "Повороты заряжают следующее основное попадание и снижают нагрев от рывка.",
                            KkTextRole.MONO, 9f, 361f, maxLines = 2, uppercase = true, minScale = 0.85f)
                    }
                }
            }
            waitForIdle()
            assertEquals(3, fitted.size)
            fitted.forEach { (width, layout) ->
                assertTrue(layout.size.width <= width + 0.5f, "${layout.layoutInput.text} fits $width")
                assertTrue(!layout.isCut(), "${layout.layoutInput.text} is not cut")
            }
            assertTrue(fitted.last().second.lineCount <= 2)
        }
    }

    @Test
    fun totemDescriptionsShareOneSizeWithinTheirLineBudget() {
        var checked = 0
        for ((width, height) in frames) for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.25f, 1.75f)) {
            runDesktopComposeUiTest(width, height) {
                val model = totem(language, width.toFloat(), height.toFloat(), textScale)
                setContent {
                    CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f)) {
                        Box(Modifier.requiredSize(width.dp, height.dp)) {
                            RewardContent(model, choiceLayoutGeometry(width.toFloat(), height.toFloat(), 1f, model.choices.size, model.choicesCanReroll),
                                0f, true, {}, {})
                        }
                    }
                }
                mainClock.advanceTimeBy(2_000)
                waitForIdle()
                val scene = "totem ${width}x$height $language ${textScale}x"
                val layouts = model.choices.indices.mapNotNull { index ->
                    val tag = hasTestTag("kinetickk.gameplay.choice.${index + 1}.description")
                    val nodes = onAllNodes((tag or hasAnyAncestor(tag)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                        useUnmergedTree = true).fetchSemanticsNodes()
                    nodes.singleOrNull()?.let { node ->
                        val results = mutableListOf<TextLayoutResult>()
                        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                        results.single()
                    }
                }
                if (layouts.isEmpty()) return@runDesktopComposeUiTest
                checked++
                assertEquals(model.choices.size, layouts.size, "$scene: every row shows its description")
                assertEquals(1, layouts.map { it.layoutInput.style.fontSize }.distinct().size, "$scene: one description size for the list")
                layouts.forEachIndexed { index, layout ->
                    assertTrue(layout.lineCount <= 2, "$scene: \"${layout.layoutInput.text}\" takes ${layout.lineCount} lines")
                    assertTrue(!layout.didOverflowHeight, "$scene: \"${layout.layoutInput.text}\" runs past the height its row leaves it")
                    val row = onNodeWithTag("kinetickk.gameplay.choice.${index + 1}").fetchSemanticsNode().boundsInRoot
                    val text = onAllNodes((hasTestTag("kinetickk.gameplay.choice.${index + 1}.description")), useUnmergedTree = true)
                        .fetchSemanticsNodes().single().boundsInRoot
                    assertTrue(text.bottom <= row.bottom + 0.5f && text.top >= row.top, "$scene: description ${index + 1} inside its row: $text in $row")
                }
                assertTextFitsWithoutBreakingWords(scene)
            }
        }
        assertTrue(checked >= 12, "Desktop and portrait totems show descriptions ($checked scenes)")
    }

    private fun forEachFrame(
        vararg scenes: (AppLanguage, Float, Float) -> GameplayRenderModel,
        check: SemanticsNodeInteractionsProvider.(Int, Int, GameplayRenderModel) -> Unit,
    ) {
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
            scenes.forEach { scene ->
                val model = scene(language, width.toFloat(), height.toFloat())
                current.value = model
                mainClock.advanceTimeBy(2_000)
                waitForIdle()
                if (model.choices.firstOrNull()?.relicAction == RelicChoiceAction.REPLACE) {
                    // Replacing arms nothing until a slot is lifted; lift one to show its preview.
                    onNodeWithTag("kinetickk.gameplay.choice.2").performMouseInput { moveTo(center) }
                    waitForIdle()
                }
                check(width, height, requireNotNull(current.value))
            }
        }
    }

    private fun levelUp(language: AppLanguage, width: Float, height: Float) = rewardFixtureModel(
        choices = listOf(itemChoice(0), itemChoice(1), itemChoice(2)), itemStacks = listOf(2, 0, 0, 0, 0),
        language = language, width = width, height = height,
        previews = listOf(
            RewardPreview(immutableListOf(RewardStatChange("Dash impulse", 873f, 1083f, ""), RewardStatChange("Cooling", 23.2f, 24.8f, "/s"))),
            RewardPreview(immutableListOf(RewardStatChange("Critical chance", 8f, 27f, "%"), RewardStatChange("Critical power", 1.5f, 1.64f, "×"))),
            RewardPreview(immutableListOf(RewardStatChange("Overdrive gain", 1.2f, 1.51f, "×"), RewardStatChange("Combo window", 2.8f, 2.98f, "s"))),
        ),
    )

    private fun relicOffers(language: AppLanguage, width: Float, height: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC, relics = relics3, language = language, width = width, height = height,
        choices = listOf(RelicId.VOLTAIC_FILAMENT, RelicId.PERIAPSIS_HOOK, RelicId.GHOST_VECTOR).map { id ->
            val relic = rewardFixtureContent.relic(id)
            ChoiceOption(ChoiceType.RELIC, relic.name, relic.description, relic.aspect.displayLabel.uppercase(), relicId = id, relicAction = RelicChoiceAction.ACQUIRE)
        },
        previews = listOf(
            // Real nucleus preview labels (RelicEffectPreview): stat and condition joined by ": ".
            RewardPreview(immutableListOf(RewardStatChange("Arc: hit, every 0.28s", 0f, 16f, "%", sourceRelic = RelicId.VOLTAIC_FILAMENT))),
            RewardPreview(immutableListOf(RewardStatChange("Damage: distance >300", 0f, 8f, "%", sourceRelic = RelicId.PERIAPSIS_HOOK)),
                addedSynergies = immutableListOf("Gravitic grouping")),
            RewardPreview(immutableListOf(RewardStatChange("Damage: dash", 24f, 48f, "", sourceRelic = RelicId.GHOST_VECTOR),
                RewardStatChange("Radius: dash", 86f, 100f, "", sourceRelic = RelicId.GHOST_VECTOR))),
        ),
    )

    private fun replacing(language: AppLanguage, width: Float, height: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC_BIND, relics = relics4, language = language, width = width, height = height,
        choices = relics4.mapIndexed { index, equipped ->
            val current = rewardFixtureContent.relic(equipped.id)
            ChoiceOption(ChoiceType.RELIC_BIND, "Replace ${current.name}", "", "", relicId = RelicId.PERIAPSIS_HOOK,
                relicAction = RelicChoiceAction.REPLACE, relicSlot = index)
        },
        previews = relics4.map {
            RewardPreview(immutableListOf(RewardStatChange("Damage: distance >300", 0f, 8f, "%", sourceRelic = RelicId.PERIAPSIS_HOOK)),
                addedSynergies = immutableListOf("Gravitic grouping"), removedSynergies = immutableListOf("Vector maneuver"))
        },
    )

    private fun melding(language: AppLanguage, width: Float, height: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC_BIND, relics = relics3, language = language, width = width, height = height,
        choices = relics3.mapIndexed { index, equipped ->
            ChoiceOption(ChoiceType.RELIC_BIND, "Meld ${rewardFixtureContent.relic(equipped.id).name}", "", "", relicId = equipped.id,
                relicAction = RelicChoiceAction.MELD_TARGET, relicSlot = index)
        },
        previews = listOf(RewardPreview(immutableListOf(RewardStatChange("Damage: speed ≥500", 10f, 15f, "%", sourceRelic = RelicId.KINETIC_FLYWHEEL),
            RewardStatChange("Damage: speed ≥1600", 20f, 30f, "%", sourceRelic = RelicId.KINETIC_FLYWHEEL)))),
    )

    private fun totem(language: AppLanguage, width: Float, height: Float, textScale: Float = 1f) = rewardFixtureModel(
        choiceType = ChoiceType.TOTEM, weaponLevel = 5, directedChoice = true, language = language, width = width, height = height, textScale = textScale,
        choices = listOf(ChoiceOption(ChoiceType.TOTEM, "Amplify Flux Wake", "", "", weaponId = WeaponId.FLUX_WAKE,
            totemAction = TotemAction.AMPLIFY_CURRENT)) + listOf(WeaponId.ARC_COIL, WeaponId.GRAVITY_MINES).map { id ->
            val weapon = rewardFixtureContent.weapon(id)
            ChoiceOption(ChoiceType.WEAPON, weapon.name, weapon.description, "CHANGE", weaponId = id)
        },
    )
}

/** No text node is ellipsized and no line ends inside a word. */
internal fun SemanticsNodeInteractionsProvider.assertTextFitsWithoutBreakingWords(scene: String) {
    val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue(nodes.isNotEmpty(), "$scene has text")
    nodes.forEach { node ->
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.firstOrNull() ?: return@forEach
        val text = layout.layoutInput.text.text
        for (line in 0 until layout.lineCount) {
            assertTrue(!layout.isLineEllipsized(line) && !layout.isCut(), "$scene: \"$text\" is cut off")
            if (line == layout.lineCount - 1) continue
            val end = layout.getLineEnd(line)
            if (end <= 0 || end >= text.length) continue
            val boundary = text[end - 1].isWhitespace() || text[end].isWhitespace() || text[end - 1] in "-–—/"
            assertTrue(boundary, "$scene: \"$text\" breaks inside a word at $end")
        }
    }
}
