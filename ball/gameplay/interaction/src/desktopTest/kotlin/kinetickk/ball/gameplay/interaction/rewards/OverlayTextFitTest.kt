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
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.SignalOption
import kinetickk.ball.gameplay.interaction.canvas.with
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.BuildForecast
import kinetickk.ball.gameplay.nucleus.render.ForecastKind
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
    private val relics3 = listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 2), EquippedRelic(RelicId.GYRE_ENGINE, 1), EquippedRelic(RelicId.FURNACE_COVENANT, 1))
    private val relics4 = relics3 + EquippedRelic(RelicId.AEGIS_INVERSION, 1)

    @Test
    fun levelUpAndRelicCardsFitTheirTextWholeInBothLanguages() {
        forEachFrame(::levelUp, ::relicOffers) { width, height, model ->
            assertTextFitsWithoutBreakingWords("${model.choiceType} ${width}x$height")
            model.choices.indices.forEach { index ->
                val body = onNode(hasTestTag("kinetickk.gameplay.choice.${index + 1}.text"), useUnmergedTree = true).fetchSemanticsNode()
                val range = body.config[SemanticsProperties.VerticalScrollAxisRange]
                assertEquals(0f, range.maxValue(), "${model.choiceType} card ${index + 1} at ${width}x$height fits without scrolling")
            }
            model.choices.indices.forEach { index ->
                val card = onNodeWithTag("kinetickk.gameplay.choice.${index + 1}").fetchSemanticsNode().boundsInRoot
                // The footer (action tag and forecast chips) and the chips beside the icon stay on their card.
                onAllNodes(hasTestTag("kinetickk.gameplay.choice.${index + 1}.footer"), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                    val footer = node.boundsInRoot
                    assertTrue(card.inflate(2f).contains(footer.topLeft) && card.inflate(2f).contains(footer.bottomRight),
                        "${model.choiceType} footer ${index + 1} stays on its card at ${width}x$height: $footer in $card")
                }
                onAllNodes(hasTestTag("kinetickk.gameplay.choice.${index + 1}.chips"), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                    val chips = node.boundsInRoot
                    assertTrue(card.inflate(2f).contains(chips.topLeft) && card.inflate(2f).contains(chips.bottomRight),
                        "${model.choiceType} chips ${index + 1} stay on their card at ${width}x$height: $chips in $card")
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

    /**
     * The omen offer (the longest Russian omen and a mutator chip, the run conditions under the heading) and a signal
     * prompt (a Shrine with its stat previews, Ignore): no text is cut or broken inside a word, every card body fits
     * without scrolling and the chips stay on their cards.
     */
    @Test
    fun omenAndSignalCardsFitTheirTextWholeInBothLanguages() {
        forEachFrame(::omenOffer, ::shrinePrompt, ::merchantPrompt) { width, height, model ->
            val scene = "${model.choiceType} ${width}x$height"
            assertTextFitsWithoutBreakingWords(scene)
            model.choices.indices.forEach { index ->
                val body = onNode(hasTestTag("kinetickk.gameplay.choice.${index + 1}.text"), useUnmergedTree = true).fetchSemanticsNode()
                assertEquals(0f, body.config[SemanticsProperties.VerticalScrollAxisRange].maxValue(), "$scene card ${index + 1} fits without scrolling")
                val card = onNodeWithTag("kinetickk.gameplay.choice.${index + 1}").fetchSemanticsNode().boundsInRoot
                onAllNodes(hasTestTag("kinetickk.gameplay.choice.${index + 1}.chips"), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                    val chips = node.boundsInRoot
                    assertTrue(card.inflate(2f).contains(chips.topLeft) && card.inflate(2f).contains(chips.bottomRight),
                        "$scene chips ${index + 1} stay on their card: $chips in $card")
                }
            }
        }
    }

    private fun omenOffer(language: AppLanguage, width: Float, height: Float): GameplayRenderModel {
        val omens = rewardFixtureContent.variety.omens.definitions
        // Crowned Quarry (the longest upside and a mutator liability), Quickened Host (the longest liability), Amplified Keynote.
        return rewardFixtureModel(choiceType = ChoiceType.OMEN, language = language, width = width, height = height,
            choices = listOf(OmenId.CROWNED_QUARRY, OmenId.QUICKENED_HOST, OmenId.AMPLIFIED_KEYNOTE).map { id ->
                ChoiceOption(ChoiceType.OMEN, omens[id.ordinal].name, omens[id.ordinal].upside, "OMEN", omenId = id)
            } + ChoiceOption(ChoiceType.OMEN, "No omen", "Start the run as it is.", "DECLINE"),
        ).with("directorPersonality" to DirectorPersonalityId.ELEMENTAL_STORM, "keynoteTag" to BuildTag.PRECISION,
            "keynoteElement" to StatusElement.GRAVITIC)
    }

    private fun shrinePrompt(language: AppLanguage, width: Float, height: Float) = rewardFixtureModel(
        choiceType = ChoiceType.SIGNAL, language = language, width = width, height = height,
        choices = listOf(SignalOption.SHRINE_BLOOD to 54, SignalOption.SHRINE_WARD to 20, SignalOption.SHRINE_RELIC to 72, SignalOption.IGNORE to 0)
            .map { (option, cost) -> signal(option, SignalKind.SHRINE, cost, if (option == SignalOption.SHRINE_WARD) 25 else 0) },
        previews = listOf(
            RewardPreview(immutableListOf(RewardStatChange("Impact power", 1.64f, 1.74f, "×"), RewardStatChange("Weapon power", 1.36f, 1.46f, "×"))),
            RewardPreview(immutableListOf(RewardStatChange("Shield", 0f, 25f, ""), RewardStatChange("Damage reduction", 0f, 4f, "%"))),
        ),
    )

    /** The cursed cache's curses and the gamble's stakes: the longest cost lines. */
    private fun merchantPrompt(language: AppLanguage, width: Float, height: Float) = rewardFixtureModel(
        choiceType = ChoiceType.SIGNAL, language = language, width = width, height = height,
        choices = listOf(SignalOption.CACHE_CRATE, SignalOption.CACHE_RELIQUARY, SignalOption.GAMBLE_MATTER, SignalOption.IGNORE)
            .map { option -> signal(option, option.kind ?: SignalKind.CURSED_CACHE, cost = 1_250, payout = 2_750, locked = option == SignalOption.GAMBLE_MATTER) },
    )

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
        // A Reborn card and forecast rows: the footer chips take their room from the text.
        choices = listOf(itemChoice(0), itemChoice(1), itemChoice(2, reborn = true)), itemStacks = listOf(1, 0, 0, 0, 0, 0),
        language = language, width = width, height = height,
        previews = listOf(
            RewardPreview(immutableListOf(RewardStatChange("Dash impulse", 873f, 1083f, ""), RewardStatChange("Cooling", 23.2f, 24.8f, "/s")),
                immutableListOf(
                    BuildForecast(ForecastKind.REACTION, reaction = ReactionId.GLACIER_CRUSH, discovered = false),
                    BuildForecast(ForecastKind.TAG_TIER, tag = BuildTag.ANCHOR, before = 0, after = 1),
                    BuildForecast(ForecastKind.AFFINITY, element = StatusElement.CRYO, before = 1, after = 2),
                )),
            RewardPreview(immutableListOf(RewardStatChange("Critical chance", 8f, 27f, "%"), RewardStatChange("Critical power", 1.5f, 1.64f, "×")),
                immutableListOf(BuildForecast(ForecastKind.REACTION, reaction = ReactionId.PLASMA_BURST, discovered = true),
                    BuildForecast(ForecastKind.AFFINITY, element = StatusElement.THERMAL, before = 3, after = 4))),
            RewardPreview(immutableListOf(RewardStatChange("Overdrive gain", 1.2f, 1.51f, "×"), RewardStatChange("Combo window", 2.8f, 2.98f, "s")),
                immutableListOf(BuildForecast(ForecastKind.TAG_TIER, tag = BuildTag.OVERCLOCK, before = 1, after = 2))),
        ),
    )

    private fun relicOffers(language: AppLanguage, width: Float, height: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC, relics = relics3, language = language, width = width, height = height,
        choices = listOf(RelicId.AEGIS_INVERSION, RelicId.EVENT_HORIZON, RelicId.GYRE_ENGINE).map { id ->
            val relic = rewardFixtureContent.relic(id)
            ChoiceOption(ChoiceType.RELIC, relic.name, relic.upside, relic.aspect.displayLabel.uppercase(), relicId = id, relicAction = RelicChoiceAction.ACQUIRE)
        },
        previews = listOf(
            // Real nucleus preview labels (RelicEffectPreview): stat and condition joined by ": ".
            RewardPreview(immutableListOf(RewardStatChange("Arc: hit, every 0.28s", 0f, 16f, "%", sourceRelic = RelicId.AEGIS_INVERSION)),
                immutableListOf(BuildForecast(ForecastKind.CONCORD, pairing = RelicPairingId.FARADAY_CAGE, discovered = false),
                    BuildForecast(ForecastKind.HARMONY), BuildForecast(ForecastKind.AFFINITY, element = StatusElement.ION, before = 1, after = 2))),
            RewardPreview(immutableListOf(RewardStatChange("Damage: distance >300", 0f, 8f, "%", sourceRelic = RelicId.EVENT_HORIZON))),
            RewardPreview(immutableListOf(RewardStatChange("Damage: dash", 24f, 48f, "", sourceRelic = RelicId.GYRE_ENGINE),
                RewardStatChange("Radius: dash", 86f, 100f, "", sourceRelic = RelicId.GYRE_ENGINE))),
        ),
    )

    private fun replacing(language: AppLanguage, width: Float, height: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC_BIND, relics = relics4, language = language, width = width, height = height,
        choices = relics4.mapIndexed { index, equipped ->
            val current = rewardFixtureContent.relic(equipped.id)
            ChoiceOption(ChoiceType.RELIC_BIND, "Replace ${current.name}", "", "", relicId = RelicId.EVENT_HORIZON,
                relicAction = RelicChoiceAction.REPLACE, relicSlot = index)
        },
        previews = relics4.map {
            RewardPreview(immutableListOf(RewardStatChange("Damage: distance >300", 0f, 8f, "%", sourceRelic = RelicId.EVENT_HORIZON)))
        },
    )

    private fun melding(language: AppLanguage, width: Float, height: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC_BIND, relics = relics3, language = language, width = width, height = height,
        choices = relics3.mapIndexed { index, equipped ->
            ChoiceOption(ChoiceType.RELIC_BIND, "Meld ${rewardFixtureContent.relic(equipped.id).name}", "", "", relicId = equipped.id,
                relicAction = RelicChoiceAction.MELD_TARGET, relicSlot = index)
        },
        previews = listOf(RewardPreview(immutableListOf(RewardStatChange("Damage: speed ≥500", 10f, 15f, "%", sourceRelic = RelicId.PERPETUAL_VECTOR),
            RewardStatChange("Damage: speed ≥1600", 20f, 30f, "%", sourceRelic = RelicId.PERPETUAL_VECTOR)))),
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
            // A number keeps its unit on its line, and a unit's slash never splits it.
            val head = text.substring(0, end).trimEnd()
            val next = text.substring(end).trimStart().takeWhile { !it.isWhitespace() }
            val unitSlash = head.length >= 2 && head.endsWith("/") && !head[head.length - 2].isWhitespace()
            val unit = head.lastOrNull()?.isDigit() == true && next.trimEnd('.', ',', ';').lowercase() in UnitTokens
            assertTrue(!unitSlash && !unit && !next.startsWith("%"), "$scene: \"$text\" splits a number from its unit at $end")
        }
    }
}

/** Unit marks that must stay on their number's line. */
private val UnitTokens = setOf("%", "s", "с", "u/s", "ед/с", "ед", "u")
