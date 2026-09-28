// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
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
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reward layouts at the game's default text size (125 %) and the largest (175 %), English and
 * Russian, at the three reference frames: labels of one kind share one size, nothing is cut by
 * the box it sits in, and neighbouring blocks keep clear of each other.
 */
@OptIn(ExperimentalTestApi::class)
class RewardLayoutFitTest {
    private val frames = listOf(1440 to 810, 844 to 390, 390 to 844)
    private val settings = listOf(1.25f, 1.75f)
    private val relics3 = listOf(EquippedRelic(RelicId.KINETIC_FLYWHEEL, 2), EquippedRelic(RelicId.GHOST_VECTOR, 1), EquippedRelic(RelicId.ORBITAL_NAIL, 1))
    private val relics4 = relics3 + EquippedRelic(RelicId.VOLTAIC_FILAMENT, 1)

    /**
     * Every landscape totem row shows "Lvl N", the ticks and the whole mastery tier name inside
     * its face: the level column's texts get their whole line height, at any text size.
     */
    @Test
    fun totemLevelColumnKeepsItsWholeHeightInEveryRow() {
        var columns = 0
        forEachScene(::directedTotem) { scene, model ->
            model.choices.indices.forEach { index ->
                val tag = "kinetickk.gameplay.choice.${index + 1}"
                val row = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                val level = textsUnder("$tag.level")
                if (level.isNotEmpty()) columns++
                level.forEach { (node, layout) ->
                    val text = layout.layoutInput.text.text
                    assertTrue(!layout.didOverflowHeight && !layout.isCut(), "$scene: \"$text\" (${layout.multiParagraph.height} px) is cut by its ${layout.size} box")
                    val bounds = node.boundsInRoot
                    assertTrue(bounds.top >= row.top - 0.5f && bounds.bottom <= row.bottom + 0.5f,
                        "$scene: \"$text\" $bounds stays inside row ${index + 1} $row")
                }
                // No other text of the row is cut off or leaves its face either.
                textsUnder(tag).forEach { (node, layout) ->
                    assertTrue(!layout.isCut() && node.boundsInRoot.bottom <= row.bottom + 0.5f, "$scene: \"${layout.layoutInput.text}\" fits row ${index + 1}")
                }
            }
        }
        assertTrue(columns >= 2 * 2 * 2 * 3, "Landscape totems show a level column in every row ($columns)")
    }

    /**
     * The relic row's names share one size, keep 8 dp clear of their neighbours and sit in one box
     * height, so every rank label shares one baseline; the ranks stay inside the matrix and 16 dp
     * above the preview column's heading when it sits below the row (portrait).
     */
    @Test
    fun relicSlotNamesShareOneSizeAndTheirRanksOneBaseline() {
        forEachScene(::replacing, ::melding) { scene, _ ->
            val names = textsUnder("kinetickk.gameplay.rewards.slot-name").sortedBy { it.first.boundsInRoot.left }
            assertTrue(names.size >= 3, "$scene: the slots show their names")
            assertEquals(1, names.map { it.second.layoutInput.style.fontSize }.distinct().size, "$scene: one name size for the row")
            names.forEach { (_, layout) ->
                assertTrue(layout.lineCount <= 2 && !layout.isCut(), "$scene: \"${layout.layoutInput.text}\" fits two lines")
            }
            names.zipWithNext().forEach { (left, right) ->
                val gap = inkLeft(right) - inkRight(left)
                assertTrue(gap >= RelicSlotNameGapDp - 0.5f,
                    "$scene: \"${left.second.layoutInput.text}\" and \"${right.second.layoutInput.text}\" are $gap dp apart")
            }
            val ranks = onAllNodes(hasTestTag("kinetickk.gameplay.rewards.slot-rank"), useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(ranks.size >= 3, "$scene: the bound slots show their ranks")
            val tops = ranks.map { it.boundsInRoot.top }
            assertTrue(tops.max() - tops.min() <= 0.5f, "$scene: rank labels share one baseline: $tops")
            val rankBottom = ranks.maxOf { it.boundsInRoot.bottom }
            val matrix = onNodeWithTag("kinetickk.gameplay.rewards.matrix").fetchSemanticsNode().boundsInRoot
            assertTrue(rankBottom <= matrix.bottom + 0.5f, "$scene: the rank labels ($rankBottom) stay inside the matrix $matrix")
            val kicker = onNodeWithTag("kinetickk.gameplay.rewards.kicker").fetchSemanticsNode().boundsInRoot
            if (kicker.top > tops.min()) {
                assertTrue(kicker.top - rankBottom >= 16f - 0.5f, "$scene: the rank labels end at $rankBottom, the preview heading starts at ${kicker.top}")
            }
            assertTextFitsWithoutBreakingWords(scene)
        }
    }

    /**
     * Cards dealt together on the desktop show their whole body without scrolling, all at one
     * size; a deal whose cards fit keeps the size the text setting asks for.
     */
    @Test
    fun cardsOfOneDealShareOneBodySizeAndShowTheirWholeText() {
        val sizes = mutableMapOf<String, Float>()
        forEachScene(::weapons, ::relicOffers, ::relicOffersWithMeld, frames = listOf(1440 to 810), settings = listOf(1f, 1.25f, 1.75f)) { scene, model ->
            model.choices.indices.forEach { index ->
                val body = onNode(hasTestTag("kinetickk.gameplay.choice.${index + 1}.text"), useUnmergedTree = true).fetchSemanticsNode()
                assertEquals(0f, body.config[SemanticsProperties.VerticalScrollAxisRange].maxValue(), "$scene: card ${index + 1} shows its whole body")
            }
            // The body paragraphs (descriptions) of every card.
            val language = if ("Russian" in scene) AppLanguage.Russian else AppLanguage.English
            val paragraphs = model.choices.indices.flatMap { index ->
                val descriptions = model.rewardCardPresentation(model.choices[index], index, language).descriptions
                textsUnder("kinetickk.gameplay.choice.${index + 1}.text").filter { (_, layout) -> layout.layoutInput.text.text in descriptions }
            }
            assertTrue(paragraphs.size >= model.choices.size, "$scene: every card shows its description")
            val size = paragraphs.map { it.second.layoutInput.style.fontSize.value }.distinct()
            assertEquals(1, size.size, "$scene: one body size for the deal: $size")
            sizes[scene] = size.single()
        }
        // Weapon cards fit whole at 100 % and 125 %: none shrinks, so the setting scales them by 1.25.
        for (language in AppLanguage.entries) {
            val smallest = requireNotNull(sizes["weapons 1440x810 $language 1.0"])
            val default = requireNotNull(sizes["weapons 1440x810 $language 1.25"])
            assertEquals(1.25f, default / smallest, 0.01f, "$language weapon bodies at 125 % against 100 %")
        }
    }

    /**
     * A card that still scrolls fades its text out at the bottom of the text column only: the
     * card's rarity glow beside the column stays exactly as the bare face draws it.
     */
    @Test
    fun scrollFadeLeavesTheCardGlowWhole() {
        val card = RewardCardPresentation(
            choice = ChoiceOption(ChoiceType.ITEM, "Rime Thruster", "Full description", "RARE"),
            accent = Color(0xFF5BC8FF),
            tag = "RARE",
            descriptions = List(8) { "Description paragraph $it keeps every word of the catalog text." },
            operation = "",
            rank = 3,
        )
        val width = 220
        val height = 256
        runDesktopComposeUiTest(width * 2 + 20, height) {
            setContent {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English, LocalDensity provides Density(1f)) {
                    val measurer = rememberKkCanvasMeasurer(1f)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(Modifier.requiredSize(width.dp, height.dp).clipToBounds().testTag("card")) {
                            RewardCard(card, 0, 1f, 0f, true, Modifier.requiredSize(width.dp, height.dp)) {}
                        }
                        Canvas(Modifier.requiredSize(width.dp, height.dp).clipToBounds().testTag("face")) {
                            drawRewardCardFace(measurer, card, card.accent, 0f, 0f, -1f)
                        }
                    }
                }
            }
            waitForIdle()
            val body = onNode(hasTestTag("kinetickk.gameplay.choice.1.text"), useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(body.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f, "The long card scrolls")
            val root = onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
            val viewport = body.boundsInRoot.translate(-root.left, -root.top)
            // Texts scrolled out of the viewport report empty bounds.
            val text = textsUnder("kinetickk.gameplay.choice.1.text").map { it.first.boundsInRoot }.filter { it.width > 0f }
                .minOf { it.left } - root.left
            val rendered = onNodeWithTag("card").captureToImage().toPixelMap()
            val face = onNodeWithTag("face").captureToImage().toPixelMap()
            var compared = 0
            // The fade band: the bottom 18 dp of the text viewport, left of the text column (the
            // slanted face outline excluded).
            for (y in (viewport.bottom - 18f).toInt() until viewport.bottom.toInt()) {
                val outline = 22f * (1f - y / height.toFloat()) + 3f
                for (x in outline.toInt() + 1 until (text - 3f).toInt()) {
                    val a = rendered[x, y].toArgb()
                    val b = face[x, y].toArgb()
                    for (shift in intArrayOf(16, 8, 0)) {
                        assertTrue(abs((a shr shift and 0xFF) - (b shr shift and 0xFF)) <= 2,
                            "The scroll fade paints over the card beside the text at ($x, $y)")
                    }
                    compared++
                }
            }
            assertTrue(compared > 100, "Compared the glow beside the fade ($compared px)")
        }
    }

    private fun forEachScene(
        vararg scenes: (AppLanguage, Float, Float, Float) -> GameplayRenderModel,
        frames: List<Pair<Int, Int>> = this.frames,
        settings: List<Float> = this.settings,
        check: SemanticsNodeInteractionsProvider.(String, GameplayRenderModel) -> Unit,
    ) {
        for ((width, height) in frames) for (language in AppLanguage.entries) for (setting in settings) for (scene in scenes) {
            runDesktopComposeUiTest(width, height) {
                val model = scene(language, width.toFloat(), height.toFloat(), setting)
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
                val name = (scene as kotlin.reflect.KFunction<*>).name
                check("$name ${width}x$height $language $setting", model)
            }
        }
    }

    private fun SemanticsNodeInteractionsProvider.textsUnder(tag: String): List<Pair<SemanticsNode, TextLayoutResult>> =
        onAllNodes((hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            results.firstOrNull()?.let { node to it }
        }

    private fun inkLeft(text: Pair<SemanticsNode, TextLayoutResult>): Float =
        text.first.boundsInRoot.left + (0 until text.second.lineCount).minOf { text.second.getLineLeft(it) }

    private fun inkRight(text: Pair<SemanticsNode, TextLayoutResult>): Float =
        text.first.boundsInRoot.left + (0 until text.second.lineCount).maxOf { text.second.getLineRight(it) }

    private fun directedTotem(language: AppLanguage, width: Float, height: Float, setting: Float) = rewardFixtureModel(
        choiceType = ChoiceType.TOTEM, weaponLevel = 5, directedChoice = true, language = language, width = width, height = height, textScale = setting,
        choices = listOf(ChoiceOption(ChoiceType.TOTEM, "Amplify Flux Wake", "Raise current weapon mastery by one level.", "AMPLIFY",
            weaponId = WeaponId.FLUX_WAKE, totemAction = TotemAction.AMPLIFY_CURRENT)) + listOf(WeaponId.ARC_COIL, WeaponId.GRAVITY_MINES).map { id ->
            val weapon = rewardFixtureContent.weapon(id)
            ChoiceOption(ChoiceType.WEAPON, weapon.name, weapon.description, "CHANGE", weaponId = id)
        },
    )

    private fun replacing(language: AppLanguage, width: Float, height: Float, setting: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC_BIND, relics = relics4, language = language, width = width, height = height, textScale = setting,
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

    private fun melding(language: AppLanguage, width: Float, height: Float, setting: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC_BIND, relics = relics3, language = language, width = width, height = height, textScale = setting,
        choices = relics3.mapIndexed { index, equipped ->
            ChoiceOption(ChoiceType.RELIC_BIND, "Meld ${rewardFixtureContent.relic(equipped.id).name}", "", "", relicId = equipped.id,
                relicAction = RelicChoiceAction.MELD_TARGET, relicSlot = index)
        },
        previews = listOf(RewardPreview(immutableListOf(RewardStatChange("Damage: speed ≥500", 10f, 15f, "%", sourceRelic = RelicId.KINETIC_FLYWHEEL)))),
    )

    private fun weapons(language: AppLanguage, width: Float, height: Float, setting: Float) = rewardFixtureModel(
        choiceType = ChoiceType.WEAPON, language = language, width = width, height = height, textScale = setting,
        choices = listOf(WeaponId.ARC_COIL, WeaponId.GRAVITY_MINES, WeaponId.NULL_LANCE).map { id ->
            val weapon = rewardFixtureContent.weapon(id)
            ChoiceOption(ChoiceType.WEAPON, weapon.name, weapon.description, weapon.tags.first(), weaponId = id)
        },
    )

    private fun relicOffers(language: AppLanguage, width: Float, height: Float, setting: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC, relics = relics3, language = language, width = width, height = height, textScale = setting,
        choices = listOf(RelicId.VOLTAIC_FILAMENT, RelicId.PERIAPSIS_HOOK, RelicId.GHOST_VECTOR).map { id ->
            val relic = rewardFixtureContent.relic(id)
            ChoiceOption(ChoiceType.RELIC, relic.name, relic.description, relic.aspect.displayLabel.uppercase(), relicId = id, relicAction = RelicChoiceAction.ACQUIRE)
        },
        previews = listOf(
            RewardPreview(immutableListOf(RewardStatChange("Arc: hit, every 0.28s", 0f, 16f, "%", sourceRelic = RelicId.VOLTAIC_FILAMENT))),
            RewardPreview(immutableListOf(RewardStatChange("Damage: distance >300", 0f, 8f, "%", sourceRelic = RelicId.PERIAPSIS_HOOK)),
                addedSynergies = immutableListOf("Gravitic grouping")),
            RewardPreview(immutableListOf(RewardStatChange("Damage: dash", 24f, 48f, "", sourceRelic = RelicId.GHOST_VECTOR))),
        ),
    )

    private fun relicOffersWithMeld(language: AppLanguage, width: Float, height: Float, setting: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC, relics = relics4, language = language, width = width, height = height, textScale = setting,
        choices = listOf(RelicId.PERIAPSIS_HOOK, RelicId.ECHO_CHAMBER).map { id ->
            val relic = rewardFixtureContent.relic(id)
            ChoiceOption(ChoiceType.RELIC, relic.name, relic.description, relic.aspect.displayLabel.uppercase(), relicId = id, relicAction = RelicChoiceAction.ACQUIRE)
        } + ChoiceOption(ChoiceType.RELIC, "Meld resonance", "Collapse this offering into one of the four bound Relics and raise its rank.", "MELD",
            relicAction = RelicChoiceAction.MELD),
    )
}
