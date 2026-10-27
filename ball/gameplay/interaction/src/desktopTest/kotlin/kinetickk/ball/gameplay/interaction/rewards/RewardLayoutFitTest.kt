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
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
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
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.BuildForecast
import kinetickk.ball.gameplay.nucleus.render.ForecastKind
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.rankLines
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.foundation.common.localization.text
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
    private val relics3 = listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 2), EquippedRelic(RelicId.GYRE_ENGINE, 1), EquippedRelic(RelicId.FURNACE_COVENANT, 1))
    private val relics4 = relics3 + EquippedRelic(RelicId.AEGIS_INVERSION, 1)

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
     * A totem row's meta (equipped, the weapon's family and tags) beside the kind and the Signature badge never
     * shrinks below 70 % of its 9 sp phone size: it shows readable or not at all (its row's state description keeps it).
     */
    @Test
    fun totemMetaBesideTheSignatureBadgeStaysReadableOrStepsAside() {
        forEachScene(::directedTotem, frames = listOf(844 to 390, 390 to 844)) { scene, model ->
            val floor = 0.7f * 9f * overlayUiTextScale(model.settings.textScale)
            model.choices.indices.forEach { index ->
                textsUnder("kinetickk.gameplay.choice.${index + 1}.meta").forEach { (_, layout) ->
                    val size = layout.layoutInput.style.fontSize.value
                    assertTrue(size >= floor - 0.01f, "$scene: row ${index + 1} meta \"${layout.layoutInput.text}\" at $size sp under $floor")
                    assertTrue(!layout.isCut() && layout.lineCount == 1, "$scene: row ${index + 1} meta \"${layout.layoutInput.text}\" is cut")
                }
            }
            // The equipped weapon's row (no badge beside its kind) keeps its meta.
            assertTrue(textsUnder("kinetickk.gameplay.choice.1.meta").isNotEmpty(), "$scene: the Amplify row shows its Equipped meta")
        }
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
     * At the default text size the meld preview shows every signed row with its whole detail
     * line, also on the phone where the relic row's labels push the preview column down.
     */
    @Test
    fun meldPreviewKeepsEveryRowDetailAtTheDefaultTextSize() {
        forEachScene(::melding, settings = listOf(1.25f)) { scene, model ->
            val language = if ("Russian" in scene) AppLanguage.Russian else AppLanguage.English
            val presentation = model.rewardPresentation(language)
            val rows = requireNotNull(presentation.cards[requireNotNull(presentation.defaultSelection())].relicPreview).rows
            // The next-rank row shows the values the meld raises; the link rows keep their detail.
            assertTrue(rows.size >= 2 && rows.all { it.detail != null || it.after != null }, "$scene: the meld preview has detailed rows: $rows")
            // The preview measures the plans it drops too; only the placed one shows.
            val shown = textsUnder("kinetickk.gameplay.rewards.preview-row").filter { (node, _) ->
                generateSequence(node.layoutInfo) { it.parentInfo }.all { it.isPlaced }
            }
            val texts = shown.map { it.second.layoutInput.text.text }
            rows.forEach { row ->
                assertTrue(row.title.uppercase() in texts, "$scene: the preview shows the \"${row.title}\" row: $texts")
                row.detail?.let { detail ->
                    assertTrue(rewardKeepUnits(detail) in texts, "$scene: the \"${row.title}\" row shows its detail \"$detail\": $texts")
                }
                row.after?.let { after -> assertTrue(after in texts, "$scene: the \"${row.title}\" row shows its value \"$after\": $texts") }
            }
            shown.forEach { (_, layout) ->
                assertTrue(!layout.isCut() && !layout.didOverflowHeight, "$scene: \"${layout.layoutInput.text}\" shows whole")
            }
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
                // Rendered with their units held to their numbers.
                val shown = descriptions.map(::rewardKeepUnits)
                textsUnder("kinetickk.gameplay.choice.${index + 1}.text").filter { (_, layout) -> layout.layoutInput.text.text in shown }
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
     * Upright cards at the board, phone, narrow phone and portrait sizes: the chips beside the icon
     * end above the text, the footer (action tag and forecast chips) starts below it, and the whole
     * text shows without scrolling at the default text size, in both languages.
     */
    @Test
    fun cardChipsAndFootersKeepClearOfTheText() {
        val sizes = listOf(300 to 450, 220 to 254, 195 to 254, 178 to 305)
        val bound = listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 2), EquippedRelic(RelicId.GYRE_ENGINE, 1))
        var checked = 0
        var elementChips = 0
        for ((width, height) in sizes) for (language in AppLanguage.entries) {
            val items = rewardFixtureModel(
                choices = listOf(itemChoice(0), itemChoice(2, reborn = true), itemChoice(5)), relics = listOf(EquippedRelic(RelicId.GLASS_COMET, 1)),
                previews = listOf(
                    RewardPreview(immutableListOf(RewardStatChange("Cooling", 23.2f, 24.8f, "/s")), immutableListOf(
                        BuildForecast(ForecastKind.REACTION, reaction = ReactionId.THERMAL_SHOCK, discovered = false),
                        BuildForecast(ForecastKind.TAG_TIER, tag = BuildTag.ANCHOR, before = 0, after = 1),
                        BuildForecast(ForecastKind.AFFINITY, element = StatusElement.CRYO, before = 1, after = 2))),
                    RewardPreview(immutableListOf(RewardStatChange("Weapon power", 1.36f, 1.84f, "×")), immutableListOf(
                        BuildForecast(ForecastKind.TAG_TIER, tag = BuildTag.OVERCLOCK, before = 0, after = 1))),
                    RewardPreview(forecasts = immutableListOf(BuildForecast(ForecastKind.SUPPRESSED),
                        BuildForecast(ForecastKind.AFFINITY, element = StatusElement.ION, before = 0, after = 1))),
                ),
            )
            val relics = rewardFixtureModel(choiceType = ChoiceType.RELIC, relics = bound,
                choices = listOf(RelicId.STASIS_ANCHOR, RelicId.STORMHEART_CAPACITOR).map { id ->
                    val relic = rewardFixtureContent.relic(id)
                    ChoiceOption(ChoiceType.RELIC, relic.name, relic.upside, relic.aspect.displayLabel.uppercase(), relicId = id,
                        relicAction = RelicChoiceAction.ACQUIRE)
                } + ChoiceOption(ChoiceType.RELIC, "Leave hollow", "", "DECLINE", relicAction = RelicChoiceAction.DECLINE))
            val weapons = weapons(language, 1440f, 810f, 1.25f)
            // Active items: a free slot in the run character's lane, a full loadout, an owned one reaching rank III.
            val base = rewardFixtureActiveBase
            val actives = rewardFixtureModel(content = rewardActiveFixtureContent, coreShape = laneOf(SkillId.SOLAR_FLARE),
                itemStacks = List(rewardActiveFixtureContent.items.size) { if (it == base + 2) 2 else 0 },
                choices = listOf(rewardActiveChoice(base, slot = 0), rewardActiveChoice(base + 1, slot = null, replaces = true),
                    rewardActiveChoice(base + 2, slot = 2, stacks = 2)))
            val cards = listOf(items, relics, weapons, actives).flatMap { model ->
                model.choices.mapIndexed { index, choice -> model.rewardCardPresentation(choice, index, language) }
            }
            cards.forEach { card ->
                runDesktopComposeUiTest(width + 20, height + 20, effectContext = object : MotionDurationScale {
                    override val scaleFactor = 0f
                }) {
                    setContent {
                        CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f)) {
                            // The landscape phone deal sizes come from a phone layout.
                            RewardCard(card, 0, overlayUiTextScale(1.25f), 0f, true, Modifier.requiredSize(width.dp, height.dp),
                                phoneDeal = height == 254) {}
                        }
                    }
                    waitForIdle()
                    val scene = "${card.title} ${width}x$height $language"
                    val body = onNode(hasTestTag("kinetickk.gameplay.choice.1.text"), useUnmergedTree = true).fetchSemanticsNode()
                    assertEquals(0f, body.config[SemanticsProperties.VerticalScrollAxisRange].maxValue(), "$scene shows its whole text")
                    val text = body.boundsInRoot
                    val shown = textsUnder("kinetickk.gameplay.choice.1.text").map { it.first.boundsInRoot }.filter { it.height > 0f }
                    val textTop = shown.minOfOrNull { it.top } ?: text.top
                    onAllNodes(hasTestTag("kinetickk.gameplay.choice.1.chips"), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                        val chips = node.children.map { it.boundsInRoot }.filter { it.height > 0f }
                        chips.forEach { chip -> assertTrue(chip.bottom <= textTop + 0.5f && chip.right <= width + 0.5f, "$scene: chip $chip clear of the text at $textTop") }
                        // A weapon's element chip is read once: its label already names the element.
                        card.chips.filter { it.kind == RewardChipKind.ELEMENT }.forEach { element ->
                            node.children.filter { child -> child.config.getOrElseNullable(SemanticsProperties.Text) { null }
                                ?.joinToString(" ") == element.label }.forEach { child ->
                                val spoken = child.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.joinToString(" ")
                                assertTrue(spoken == null || spoken == element.label, "$scene: element chip reads '$spoken'")
                                elementChips++
                            }
                        }
                    }
                    onAllNodes(hasTestTag("kinetickk.gameplay.choice.1.footer"), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                        val footer = node.boundsInRoot
                        assertTrue(footer.top >= text.bottom - 0.5f && footer.bottom <= height + 0.5f && footer.right <= width + 0.5f,
                            "$scene: footer $footer below the text $text")
                    }
                    assertTextFitsWithoutBreakingWords(scene)
                    checked++
                }
            }
        }
        assertTrue(checked >= 4 * 2 * 12, "Checked every card at every size ($checked)")
        assertTrue(elementChips > 0, "Weapon element chips were read")
    }

    /**
     * Every prompt's heading stamp stays on the screen at every frame and text size in both languages (the active slot
     * prompt's in Russian at 175 % on a portrait phone included).
     */
    @Test
    fun everyPromptHeadingStaysOnTheScreen() {
        var checked = 0
        forEachScene(::directedTotem, ::replacing, ::melding, ::weapons, ::relicOffers, ::activeSlotPrompt) { scene, model ->
            val heading = onNodeWithTag("kinetickk.gameplay.rewards.heading", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(heading.left >= -0.5f && heading.right <= model.screenWidth + 0.5f, "$scene: heading $heading inside the screen")
            checked++
        }
        assertTrue(checked == frames.size * 2 * settings.size * 6, "Checked every heading ($checked)")
    }

    /**
     * An active item card always shows its slot chip (the key it binds to, or that it replaces a slot) inside its chip
     * column, at every frame and text size in both languages, and its rank's numbers and what rank III adds never wrap a
     * number away from the word before it, nor the prompt's "rank 1" its number or the Russian "с" its noun, nor the keep
     * card's "+4 matter" its amount from its unit.
     */
    @Test
    fun anActiveCardKeepsItsSlotChipAndItsNumbersWithTheirLabels() {
        var chips = 0
        var rows = 0
        forEachScene(::activeOffers, ::activeSlotPrompt) { scene, model ->
            val language = model.settings.language
            val content = rewardActiveFixtureContent
            model.choices.forEachIndexed { index, choice ->
                val card = model.rewardCardPresentation(choice, index, language)
                val tag = "kinetickk.gameplay.choice.${index + 1}"
                card.chips.firstOrNull()?.let { first ->
                    val column = onNodeWithTag("$tag.chips", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val shown = onAllNodes(hasAnyAncestor(hasTestTag("$tag.chips")) and SemanticsMatcher.expectValue(SemanticsProperties.Text,
                        listOf(androidx.compose.ui.text.AnnotatedString(first.spoken))), useUnmergedTree = true).fetchSemanticsNodes()
                        .map { it.boundsInRoot }.filter { it.width > 0f && it.height > 0f }
                    assertTrue(shown.size == 1 && shown.single().left >= column.left - 0.5f && shown.single().right <= column.right + 0.5f,
                        "$scene card ${index + 1}: '${first.spoken}' shows inside its chip column $column ($shown)")
                    chips++
                }
                val active = card.item?.active?.let(content::skill)?.let { skill ->
                    listOfNotNull(skill.rankLines(requireNotNull(card.itemStack), language).drop(1).joinToString(", "),
                        skill.rankThree.localizedContent(language).takeIf { card.itemStack == ContentBounds.MAX_SKILL_RANK })
                }.orEmpty()
                val incoming = choice.incomingItemId?.takeIf { card.item != null }?.let { id ->
                    language.text(OverlayRedesignText.ActiveIncoming,
                        requireNotNull(content.item(id)).name.localizedContent(language)).normalized()
                }
                val kept = card.descriptions.takeIf { choice.keepsActiveLoadout }.orEmpty()
                textsUnder("$tag.text").forEach { (node, layout) ->
                    val text = node.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString("") { it.text }?.normalized()
                    if (text == null || (text !in active && text != incoming && text !in kept)) return@forEach
                    val shown = layout.layoutInput.text.text
                    (0 until layout.lineCount).forEach { line ->
                        val words = shown.substring(layout.getLineStart(line), layout.getLineEnd(line, visibleEnd = true))
                        val where = "$scene card ${index + 1}: line ${line + 1} of '$text'"
                        if (line > 0) assertTrue(!words.first().isDigit(), "$where starts with a number")
                        if (line == layout.lineCount - 1) return@forEach
                        if (text in kept) assertTrue(!words.last().isDigit(), "$where ends with an amount without its unit")
                        if (text == incoming) assertTrue(words.substringAfterLast(' ').length > 1, "$where ends with a one-letter word")
                    }
                    rows++
                }
            }
        }
        assertTrue(chips >= frames.size * 2 * settings.size * 6, "Checked every active card's slot chip ($chips)")
        assertTrue(rows >= frames.size * 2 * settings.size * 6, "Checked every active card's number rows ($rows)")
    }

    /** [this] with its no-break spaces and word joiners read as the plain text they hold together. */
    private fun String.normalized(): String = replace('\u00A0', ' ').replace("\u2060", "")

    /**
     * A relic's cost always shows whole, at every frame and text size in both languages (the compact
     * landscape deal at 175 % in Russian included): never cut, below the scrolling text (so the scroll
     * fade never reaches it), above the action footer and inside the card.
     */
    @Test
    fun relicCostsShowWholeAtEveryTextSize() {
        var checked = 0
        forEachScene(::relicOffers) { scene, model ->
            model.choices.indices.forEach { index ->
                val tag = "kinetickk.gameplay.choice.${index + 1}"
                // Compared in each block's own coordinates: dealt cards lean, so their boxes in the root overlap.
                val card = onNodeWithTag(tag).fetchSemanticsNode().layoutInfo.coordinates
                val textNode = onNode(hasTestTag("$tag.text"), useUnmergedTree = true).fetchSemanticsNode()
                // The text's coordinates are its scrolled content's: the viewport is the content less its scroll range.
                val text = textNode.layoutInfo.coordinates
                val range = textNode.config[SemanticsProperties.VerticalScrollAxisRange]
                val viewport = text.size.height - range.maxValue()
                val footer = onAllNodes(hasTestTag("$tag.footer"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.layoutInfo?.coordinates
                val costs = textsUnder("$tag.cost").filter { (node, _) -> generateSequence(node.layoutInfo) { it.parentInfo }.all { it.isPlaced } }
                assertTrue(costs.isNotEmpty(), "$scene: card ${index + 1} shows its cost")
                costs.forEach { (node, layout) ->
                    val line = "$scene card ${index + 1}: cost \"${layout.layoutInput.text}\""
                    val cost = node.layoutInfo.coordinates
                    val top = Offset.Zero
                    val bottom = Offset(0f, cost.size.height.toFloat())
                    assertTrue(!layout.isCut() && !layout.didOverflowHeight && cost.size.height > 0, "$line is cut (${layout.size})")
                    val belowText = text.localPositionOf(cost, top).y + range.value() - viewport
                    assertTrue(belowText >= -0.5f, "$line starts ${-belowText} px inside the scrolling text's viewport")
                    val inCard = card.size.height - card.localPositionOf(cost, bottom).y
                    assertTrue(inCard >= -0.5f, "$line ends ${-inCard} px below the card")
                    footer?.let {
                        val aboveFooter = -it.localPositionOf(cost, bottom).y
                        assertTrue(aboveFooter >= -0.5f, "$line runs ${-aboveFooter} px into the footer")
                    }
                    checked++
                }
            }
        }
        assertTrue(checked >= 3 * 2 * 2 * 3, "Checked every relic card's cost ($checked)")
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

    /** A character whose active lane holds [effect]. */
    private fun laneOf(effect: SkillId): CoreShape =
        CoreShape.entries.first { effect in rewardActiveFixtureContent.coreShape(it).activeLane }

    private fun forEachScene(
        vararg scenes: (AppLanguage, Float, Float, Float) -> GameplayRenderModel,
        frames: List<Pair<Int, Int>> = this.frames,
        settings: List<Float> = this.settings,
        check: SemanticsNodeInteractionsProvider.(String, GameplayRenderModel) -> Unit,
    ) {
        for ((width, height) in frames) for (language in AppLanguage.entries) for (setting in settings) for (scene in scenes) {
            // These assertions inspect settled geometry, not the intermediate entrance frames.
            runDesktopComposeUiTest(width, height, effectContext = object : MotionDurationScale {
                override val scaleFactor = 0f
            }) {
                val model = scene(language, width.toFloat(), height.toFloat(), setting)
                setContent {
                    CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f)) {
                        Box(Modifier.requiredSize(width.dp, height.dp)) {
                            RewardContent(model, choiceLayoutGeometry(width.toFloat(), height.toFloat(), 1f, model.choices.size, model.choicesCanReroll),
                                0f, true, {}, {})
                        }
                    }
                }
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

    // The Pentagon runs: the Arc Coil row carries the Signature badge beside its kind.
    private fun directedTotem(language: AppLanguage, width: Float, height: Float, setting: Float) = rewardFixtureModel(
        choiceType = ChoiceType.TOTEM, weaponLevel = 5, directedChoice = true, language = language, width = width, height = height, textScale = setting,
        coreShape = CoreShape.PENTAGON,
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
            ChoiceOption(ChoiceType.RELIC_BIND, "Replace ${current.name}", "", "", relicId = RelicId.EVENT_HORIZON,
                relicAction = RelicChoiceAction.REPLACE, relicSlot = index)
        },
        previews = relics4.map {
            RewardPreview(immutableListOf(RewardStatChange("Damage: distance >300", 0f, 8f, "%", sourceRelic = RelicId.EVENT_HORIZON)))
        },
    )

    private fun melding(language: AppLanguage, width: Float, height: Float, setting: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC_BIND, relics = relics3, language = language, width = width, height = height, textScale = setting,
        choices = relics3.mapIndexed { index, equipped ->
            ChoiceOption(ChoiceType.RELIC_BIND, "Meld ${rewardFixtureContent.relic(equipped.id).name}", "", "", relicId = equipped.id,
                relicAction = RelicChoiceAction.MELD_TARGET, relicSlot = index)
        },
        previews = listOf(RewardPreview(immutableListOf(RewardStatChange("Damage: speed ≥500", 10f, 15f, "%", sourceRelic = RelicId.PERPETUAL_VECTOR)))),
    )

    // The Pentagon runs: Arc Coil, its signature weapon, carries the Signature chip.
    private fun weapons(language: AppLanguage, width: Float, height: Float, setting: Float) = rewardFixtureModel(
        choiceType = ChoiceType.WEAPON, language = language, width = width, height = height, textScale = setting, coreShape = CoreShape.PENTAGON,
        choices = listOf(WeaponId.ARC_COIL, WeaponId.GRAVITY_MINES, WeaponId.NULL_LANCE).map { id ->
            val weapon = rewardFixtureContent.weapon(id)
            ChoiceOption(ChoiceType.WEAPON, weapon.name, weapon.description, weapon.tags.first().displayLabel.uppercase(), weaponId = id)
        },
    )

    private fun relicOffers(language: AppLanguage, width: Float, height: Float, setting: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC, relics = relics3, language = language, width = width, height = height, textScale = setting,
        choices = listOf(RelicId.AEGIS_INVERSION, RelicId.EVENT_HORIZON, RelicId.GYRE_ENGINE).map { id ->
            val relic = rewardFixtureContent.relic(id)
            ChoiceOption(ChoiceType.RELIC, relic.name, relic.upside, relic.aspect.displayLabel.uppercase(), relicId = id, relicAction = RelicChoiceAction.ACQUIRE)
        },
        previews = listOf(
            RewardPreview(immutableListOf(RewardStatChange("Arc: hit, every 0.28s", 0f, 16f, "%", sourceRelic = RelicId.AEGIS_INVERSION))),
            RewardPreview(immutableListOf(RewardStatChange("Damage: distance >300", 0f, 8f, "%", sourceRelic = RelicId.EVENT_HORIZON))),
            // Ranking up a bound relic changes no build stat; its card carries the upside and the liability.
            RewardPreview(),
        ),
    )

    // Active items: Solar Flare into a free Z slot in the run character's lane, Meteor while every slot is full and
    // Catalyze, owned twice, ranking C to rank III.
    private fun activeOffers(language: AppLanguage, width: Float, height: Float, setting: Float): GameplayRenderModel {
        val base = rewardFixtureActiveBase
        return rewardFixtureModel(content = rewardActiveFixtureContent, coreShape = laneOf(SkillId.SOLAR_FLARE), language = language,
            width = width, height = height, textScale = setting,
            itemStacks = List(rewardActiveFixtureContent.items.size) { if (it == base + 2) 2 else 0 },
            choices = listOf(rewardActiveChoice(base, slot = 0), rewardActiveChoice(base + 1, slot = null, replaces = true),
                rewardActiveChoice(base + 2, slot = 2, stacks = 2)))
    }

    // The active slot prompt: Solar Flare (rank II), Meteor and Catalyze held while Frost Line comes in, and the keep card.
    private fun activeSlotPrompt(language: AppLanguage, width: Float, height: Float, setting: Float): GameplayRenderModel {
        val content = rewardActiveFixtureContent
        val base = rewardFixtureActiveBase
        val held = listOf(base to 2, base + 1 to 1, base + 2 to 1)
        val incoming = base + 3
        return rewardFixtureModel(content = content, choiceType = ChoiceType.ACTIVE_SLOT, coreShape = laneOf(SkillId.SOLAR_FLARE),
            language = language, width = width, height = height, textScale = setting,
            itemStacks = List(content.items.size) { id -> held.firstOrNull { it.first == id }?.second ?: 0 },
            choices = held.mapIndexed { slot, (id, rank) ->
                val item = requireNotNull(content.item(id))
                ChoiceOption(ChoiceType.ACTIVE_SLOT, item.name, "", "", itemId = id, skillId = item.active, skillRank = rank, activeSlot = slot,
                    incomingItemId = incoming)
            } + ChoiceOption(ChoiceType.ACTIVE_SLOT, requireNotNull(content.item(incoming)).name, "", "", incomingItemId = incoming,
                // An Uncommon item's salvage as the nucleus names it.
                salvageMatter = 4))
    }

    private fun relicOffersWithMeld(language: AppLanguage, width: Float, height: Float, setting: Float) = rewardFixtureModel(
        choiceType = ChoiceType.RELIC, relics = relics4, language = language, width = width, height = height, textScale = setting,
        choices = listOf(RelicId.EVENT_HORIZON, RelicId.ECHO_CHAMBER).map { id ->
            val relic = rewardFixtureContent.relic(id)
            ChoiceOption(ChoiceType.RELIC, relic.name, relic.upside, relic.aspect.displayLabel.uppercase(), relicId = id, relicAction = RelicChoiceAction.ACQUIRE)
        } + ChoiceOption(ChoiceType.RELIC, "Meld resonance", "Collapse this offering into one of the four bound Relics and raise its rank.", "MELD",
            relicAction = RelicChoiceAction.MELD),
    )
}
