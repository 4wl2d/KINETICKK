// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.*
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RewardContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun firstDiscoveryBadgeIsVisibleAndDoesNotCreateASecondSelectionTarget() {
        val fresh = mutableStateOf(true)
        var selections = 0
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian, LocalDensity provides Density(1f)) {
                RewardCard(card().copy(isNewDiscovery = fresh.value), 0, 1.25f, 0f, true,
                    Modifier.requiredSize(200.dp, 300.dp), onSelect = { selections++ })
            }
        }
        compose.onNodeWithTag("kinetickk.gameplay.choice.1.new", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performClick()
        compose.runOnIdle { assertEquals(1, selections); fresh.value = false }
        compose.onNodeWithTag("kinetickk.gameplay.choice.1.new", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun statChangesShowBothValuesWithoutArrowsAndTakeActsOnTheHoveredOrFocusedCard() {
        val selections = mutableListOf<Int>()
        val cards = mutableStateOf(conciseCards())
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    RewardContent(
                        RewardPresentation("Улучшение", "", cards.value, Color.White, Color.Magenta, 2),
                        choiceLayoutGeometry(1000f, 700f, 1f, 3, true), 1000f, 1f, 1f, 0f, true,
                        onSelect = { selections += it }, onReroll = { cards.value = listOf(card(), card(), card()) },
                    )
                }
            }
        }
        // Before value muted and after value in the accent: two separate values, never "A → B".
        compose.onNodeWithText("7,2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1,5%", useUnmergedTree = true).assertIsDisplayed()
        compose.assertNoForbiddenGlyphs()
        capture("concise-rewards-russian")
        // The middle card is lifted first; its interactions are listed without selecting it.
        compose.onNodeWithTag("kinetickk.gameplay.reward-connections", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Эхо массы", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performMouseInput { moveTo(center) }
        compose.onNodeWithText("Эхо массы", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { assertTrue(selections.isEmpty()) }
        compose.onNodeWithTag("kinetickk.gameplay.choice.3").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Сбор данных", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { assertTrue(selections.isEmpty()) }
        capture("concise-rewards-focus")
        compose.onNodeWithTag("kinetickk.gameplay.take").performClick()
        compose.runOnIdle { assertEquals(listOf(2), selections) }
        compose.onNodeWithTag("kinetickk.gameplay.reroll").performClick()
        compose.onNodeWithTag("kinetickk.gameplay.reward-connections").assertDoesNotExist()
    }

    @Test
    fun conciseCardsKeepEffectsVisibleAtNormalSizeAndReadableInCompactLayouts() {
        val scenario = mutableStateOf(Scenario(900f, 600f, 3, 1f))
        compose.setContent {
            val value = scenario.value
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(value.width.dp, value.height.dp)) {
                    RewardContent(
                        RewardPresentation("Улучшение", "", conciseCards(), Color.White, Color.Magenta, 2),
                        choiceLayoutGeometry(value.width, value.height, 1f, 3, true), value.width, 1f, value.scale, 0f, true,
                        onSelect = {}, onReroll = {},
                    )
                }
            }
        }
        compose.onNodeWithText("2,84 с", useUnmergedTree = true).assertIsDisplayed()
        listOf(360f to 720f, 780f to 360f).forEach { (width, height) ->
            listOf(1f, 1.75f).forEach { scale ->
                compose.runOnIdle { scenario.value = Scenario(width, height, 3, scale) }
                // Dealt cards are rotated; scroll the card body by gestures (the test helper
                // performScrollTo assumes an axis-aligned viewport) until the value shows.
                revealInCard("2,84 с", "kinetickk.gameplay.choice.3.text")
                compose.onNodeWithTag("kinetickk.gameplay.take").assertIsDisplayed()
                compose.onNodeWithTag("kinetickk.gameplay.reward-build").assertIsDisplayed()
                capture("concise-rewards-${width.toInt()}x${height.toInt()}-$scale")
            }
        }
    }

    @Test
    fun changingLanguageUpdatesTheFooterWithoutRecreatingTheCards() {
        val language = mutableStateOf(AppLanguage.Russian)
        val selections = mutableListOf<Int>()
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides language.value, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    RewardContent(
                        RewardPresentation("Upgrade", "", listOf(card(), card(), card()), Color.White, Color.Magenta, 2),
                        choiceLayoutGeometry(1000f, 700f, 1f, 3, false), 1000f, 1f, 1f, 0f, true,
                        onSelect = { selections += it }, onReroll = {},
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Взять").assertIsDisplayed()
        compose.onNodeWithContentDescription("Сборка").assertIsDisplayed()
        compose.runOnIdle { language.value = AppLanguage.English }
        compose.onNodeWithContentDescription("Take").assertIsDisplayed()
        compose.onNodeWithContentDescription("Build").assertIsDisplayed()
        compose.onNodeWithTag("kinetickk.gameplay.reroll").assertDoesNotExist()
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(0), selections) }
    }

    @Test
    fun buildOpensTheCodexAndNeverTakesAChoice() {
        var builds = 0
        val selections = mutableListOf<Int>()
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    RewardContent(
                        RewardPresentation("Upgrade", "", listOf(card(), card()), Color.White, Color.Magenta, 1),
                        choiceLayoutGeometry(1000f, 700f, 1f, 2, true), 1000f, 1f, 1f, 0f, true,
                        onSelect = { selections += it }, onReroll = {}, onBuild = { builds++ },
                    )
                }
            }
        }
        compose.onNodeWithTag("kinetickk.gameplay.reward-build").performClick()
        compose.runOnIdle { assertEquals(1, builds); assertTrue(selections.isEmpty()) }
    }

    @Test
    fun clickSelectsOnceAndOnlyVisibleCardHasActivation() {
        var selections = 0
        setEnglishContent { RewardCard(card(), 0, 1f, 0f, true, Modifier.requiredSize(250.dp, 270.dp)) { selections++ } }

        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, selections) }
        compose.onNodeWithTag("kinetickk.gameplay.choice.1.text", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun draggingAndWheelScrollFullTextWithoutSelectingAndTheHeaderStaysPinned() {
        var selections = 0
        setEnglishContent { RewardCard(card(), 0, 1.75f, 0f, true, Modifier.requiredSize(178.dp, 220.dp)) { selections++ } }
        val header = compose.onNodeWithTag("kinetickk.gameplay.choice.1.compact", useUnmergedTree = true)
        val headerTop = header.fetchSemanticsNode().boundsInRoot.top
        val text = compose.onNodeWithTag("kinetickk.gameplay.choice.1.text", useUnmergedTree = true)
        text.performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, selections) }
        text.performMouseInput { moveTo(center); scroll(100f) }
        compose.runOnIdle { assertEquals(0, selections) }
        compose.onNodeWithText("Description paragraph 11 includes all modifiers and the original catalog text without clipping.",
            useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals(headerTop, header.fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("kinetickk.gameplay.choice.1.scroll", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun actualCardBoundsChooseCompactHeaderAtEveryTextScale() {
        val dimensions = mutableStateOf(Triple(180f, 240f, 1f))
        setEnglishContent {
            val (width, height, scale) = dimensions.value
            RewardCard(card(), 0, scale, 0f, true, Modifier.requiredSize(width.dp, height.dp)) {}
        }
        listOf(1f, 1.25f, 1.75f).forEach { scale ->
            listOf(180f to 240f, 179f to 240f, 180f to 239f, 220f to 256f, 300f to 450f).forEach { (width, height) ->
                compose.runOnIdle { dimensions.value = Triple(width, height, scale) }
                // Phone-shaped cards (Mobile-LevelUp 220 × 256) use the compact header too.
                val compact = rewardCardIsCompact(width, height) || height < width * 1.3f
                val header = if (compact) "compact" else "expanded"
                compose.onNodeWithTag("kinetickk.gameplay.choice.1.$header", useUnmergedTree = true).assertIsDisplayed()
                val body = compose.onNodeWithTag("kinetickk.gameplay.choice.1.text", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(body.height > 0f, "Scrollable body remains reachable at $width × $height / $scale")
            }
        }
    }

    @Test
    fun draggingVisualHeaderDoesNotActivateReward() {
        var selections = 0
        setEnglishContent { RewardCard(card(), 0, 1f, 0f, true, Modifier.requiredSize(250.dp, 270.dp)) { selections++ } }
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performTouchInput {
            swipe(Offset(30f, 30f), Offset(200f, 30f), durationMillis = 300)
        }
        compose.runOnIdle { assertEquals(0, selections) }
    }

    @Test
    fun headerVerticalDragAndWheelScrollTheTextWithNoSelection() {
        var selections = 0
        setEnglishContent { RewardCard(card(), 0, 1f, 0f, true, Modifier.requiredSize(250.dp, 270.dp)) { selections++ } }
        val text = compose.onNodeWithTag("kinetickk.gameplay.choice.1.text", useUnmergedTree = true)
        fun position() = text.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(0f, position())
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performTouchInput {
            swipe(Offset(30f, 70f), Offset(30f, 10f), durationMillis = 300)
        }
        val afterDrag = position()
        assertTrue(afterDrag > 0f)
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performMouseInput {
            moveTo(Offset(40f, 30f))
            scroll(100f)
        }
        assertTrue(position() > afterDrag)
        compose.runOnIdle { assertEquals(0, selections) }
    }

    @Test
    fun mouseDragsScrollCardTextWithoutActivatingIt() {
        var selections = 0
        setEnglishContent { RewardCard(card(), 0, 1f, 0f, true, Modifier.requiredSize(250.dp, 270.dp)) { selections++ } }
        val card = compose.onNodeWithTag("kinetickk.gameplay.choice.1")
        val text = compose.onNodeWithTag("kinetickk.gameplay.choice.1.text", useUnmergedTree = true)
        fun position() = text.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        card.performMouseInput {
            moveTo(Offset(30f, 30f))
            press()
            moveTo(Offset(200f, 30f))
            release()
        }
        compose.runOnIdle { assertEquals(0, selections) }
        assertEquals(0f, position())
        card.performMouseInput {
            moveTo(Offset(30f, 70f))
            press()
            moveTo(Offset(30f, 10f))
            release()
        }
        val afterHeader = position()
        assertTrue(afterHeader > 0f)
        text.performMouseInput {
            moveTo(Offset(30f, 100f))
            press()
            moveTo(Offset(30f, 30f))
            release()
        }
        assertTrue(position() > afterHeader)
        compose.runOnIdle { assertEquals(0, selections) }
    }

    @Test
    fun twoToFourChoicesAreDealtAtTheSharedRectanglesWithTheirFooterAcrossLayoutsAndScales() {
        val scenario = mutableStateOf(Scenario(900f, 600f, 3, 1f))
        setEnglishContent {
            val value = scenario.value
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(value.width.dp, value.height.dp)) {
                    RewardContent(
                        presentation = RewardPresentation("Upgrade", "", List(value.count) { card() }, Color.White, Color(0xFFA96CFF), 3),
                        layout = choiceLayoutGeometry(value.width, value.height, 1f, value.count, true),
                        screenWidth = value.width,
                        uiScale = 1f,
                        textScale = value.scale,
                        renderTime = 0f,
                        enabled = true,
                        onSelect = {},
                        onReroll = {},
                    )
                }
            }
        }
        listOf(900f to 600f, 1440f to 810f, 360f to 720f, 780f to 360f).forEach { (width, height) ->
            listOf(2, 3, 4).forEach { count ->
                listOf(1f, 1.25f, 1.75f).forEach { scale ->
                    compose.runOnIdle { scenario.value = Scenario(width, height, count, scale) }
                    // Every card plus Reroll, Take and Build.
                    compose.onAllNodes(hasClickAction()).assertCountEquals(count + 3)
                    val origin = compose.onNodeWithTag("kinetickk.gameplay.rewards").fetchSemanticsNode().boundsInRoot.topLeft
                    val layout = choiceLayoutGeometry(width, height, 1f, count, true)
                    val lifted = (count - 1) / 2
                    layout.cards.forEachIndexed { index, bounds ->
                        // Cards rest rotated (−4° … +3°) around their centers; the lifted card rises 22 dp.
                        val card = compose.onNodeWithTag("kinetickk.gameplay.choice.${index + 1}").fetchSemanticsNode().boundsInRoot
                        assertEquals(bounds.center.x, card.center.x - origin.x, 1.5f)
                        assertEquals(bounds.center.y - if (index == lifted) 22f else 0f, card.center.y - origin.y, 1.5f)
                        assertTrue(card.width >= bounds.width - 1f && card.height >= bounds.height - 1f)
                    }
                    listOf("reroll", "take", "reward-build").forEach { tag ->
                        compose.onNodeWithTag("kinetickk.gameplay.$tag").assertIsDisplayed()
                    }
                    capture("rewards-${width.toInt()}x${height.toInt()}-$count-$scale")
                }
            }
        }
    }

    @Test
    fun relicReplacementArmsNothingUntilASlotIsLiftedAndThenTakesThatSlot() {
        val selections = mutableListOf<Int>()
        val relics = listOf(EquippedRelic(RelicId.KINETIC_FLYWHEEL, 2), EquippedRelic(RelicId.GHOST_VECTOR, 1),
            EquippedRelic(RelicId.ORBITAL_NAIL, 1), EquippedRelic(RelicId.VOLTAIC_FILAMENT, 1))
        val model = rewardFixtureModel(
            choiceType = ChoiceType.RELIC_BIND, relics = relics, rerolls = 0,
            choices = relics.mapIndexed { index, equipped ->
                ChoiceOption(ChoiceType.RELIC_BIND, "Replace ${equipped.id}", "Break slot ${index + 1}.", "REPLACE",
                    relicId = RelicId.PERIAPSIS_HOOK, relicAction = RelicChoiceAction.REPLACE, relicSlot = index)
            },
        )
        showModel(model, 1000f, 700f) { selections += it }
        compose.onNodeWithTag("kinetickk.gameplay.take").assertIsNotEnabled()
        compose.onNodeWithTag("kinetickk.gameplay.rewards.incoming").assertIsDisplayed()
        compose.onNodeWithTag("kinetickk.gameplay.choice.3").performMouseInput { moveTo(center) }
        compose.onNodeWithTag("kinetickk.gameplay.take").assertIsEnabled()
        compose.runOnIdle { assertTrue(selections.isEmpty()) }
        compose.onNodeWithTag("kinetickk.gameplay.take").performClick()
        compose.runOnIdle { assertEquals(listOf(2), selections) }
        // Clicking a slot still takes it immediately, as the host accepts choices today.
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performClick()
        compose.runOnIdle { assertEquals(listOf(2, 0), selections) }
        compose.assertNoForbiddenGlyphs()
    }

    @Test
    fun totemOfferingsAreRowsWithMasteryRulesBehindTheInfoButtonAndNoReroll() {
        val selections = mutableListOf<Int>()
        val model = rewardFixtureModel(
            choiceType = ChoiceType.TOTEM, weaponLevel = 5,
            choices = listOf(
                ChoiceOption(ChoiceType.TOTEM, "Amplify Flux Wake", "Advance.", "RESONANT", weaponId = WeaponId.FLUX_WAKE,
                    totemAction = TotemAction.AMPLIFY_CURRENT),
                ChoiceOption(ChoiceType.TOTEM, "Change weapon", "Recalibrate.", "RECALIBRATE", totemAction = TotemAction.CHANGE_WEAPON),
            ),
        )
        showModel(model, 1000f, 700f) { selections += it }
        compose.onNodeWithTag("kinetickk.gameplay.reroll").assertDoesNotExist()
        compose.onNodeWithContentDescription("Weapon level stays for the whole run, even when you change weapon. Mastery milestones: Lvl 3, Lvl 6, Lvl 10.")
            .assertIsDisplayed()
        compose.onNodeWithText("Flux Wake", substring = true, ignoreCase = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("kinetickk.gameplay.choice.2").performClick()
        compose.runOnIdle { assertEquals(listOf(1), selections) }
        // Hovering a row lifts it; Take then accepts that row through the same choice path.
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performMouseInput { moveTo(center) }
        compose.onNodeWithTag("kinetickk.gameplay.take").performClick()
        compose.runOnIdle { assertEquals(listOf(1, 0), selections) }
        compose.assertNoForbiddenGlyphs()
    }

    @Test
    fun totemAndRelicChoicesKeepPhoneTouchTargetsInsideTheViewport() {
        val scenario = mutableStateOf<GameplayRenderModel?>(null)
        val size = mutableStateOf(390f to 844f)
        compose.setContent {
            val model = scenario.value ?: return@setContent
            val (width, height) = size.value
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    RewardContent(model, choiceLayoutGeometry(width, height, 1f, model.choices.size, model.choicesCanReroll), 0f, true, {}, {})
                }
            }
        }
        val relics = listOf(EquippedRelic(RelicId.KINETIC_FLYWHEEL, 2), EquippedRelic(RelicId.GHOST_VECTOR, 1), EquippedRelic(RelicId.ORBITAL_NAIL, 1))
        val models = listOf(
            rewardFixtureModel(choiceType = ChoiceType.TOTEM, choices = listOf(
                ChoiceOption(ChoiceType.TOTEM, "Amplify Flux Wake", "Advance.", "", weaponId = WeaponId.FLUX_WAKE, totemAction = TotemAction.AMPLIFY_CURRENT),
                ChoiceOption(ChoiceType.WEAPON, "Arc Coil", "Chains.", "CHANGE", weaponId = WeaponId.ARC_COIL),
                ChoiceOption(ChoiceType.WEAPON, "Gravity Mines", "Pulls.", "CHANGE", weaponId = WeaponId.GRAVITY_MINES),
            ), language = AppLanguage.Russian),
            rewardFixtureModel(choiceType = ChoiceType.RELIC_BIND, relics = relics, language = AppLanguage.Russian,
                choices = relics.mapIndexed { index, equipped ->
                    ChoiceOption(ChoiceType.RELIC_BIND, "Meld ${equipped.id}", "Meld.", "", relicId = equipped.id,
                        relicAction = RelicChoiceAction.MELD_TARGET, relicSlot = index)
                }),
        )
        listOf(390f to 844f, 844f to 390f, 1440f to 810f).forEach { dimensions ->
            models.forEach { model ->
                compose.runOnIdle { size.value = dimensions; scenario.value = model }
                val (width, height) = dimensions
                val targets = model.choices.indices.map { "kinetickk.gameplay.choice.${it + 1}" } + "kinetickk.gameplay.take"
                targets.forEach { tag ->
                    val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.left >= -1f && bounds.top >= -1f && bounds.right <= width + 1f && bounds.bottom <= height + 1f,
                        "$tag inside ${width}x$height: $bounds")
                    // Phones need 48 dp targets; the desktop board keeps its 38 dp small buttons.
                    val minimum = if (width >= 1000f) 38f else 48f
                    assertTrue(bounds.width >= minimum && bounds.height >= minimum, "$tag touch target at ${width}x$height: $bounds")
                }
            }
        }
    }

    private fun revealInCard(text: String, bodyTag: String) {
        val body = compose.onNodeWithTag(bodyTag, useUnmergedTree = true)
        val target = compose.onNodeWithText(text, useUnmergedTree = true)
        repeat(8) {
            val viewport = body.fetchSemanticsNode().boundsInRoot
            val bounds = target.fetchSemanticsNode().boundsInRoot
            if (bounds.top >= viewport.top - 2f && bounds.bottom <= viewport.bottom + 2f) return
            body.performTouchInput { swipeUp() }
        }
        target.assertIsDisplayed()
        val viewport = body.fetchSemanticsNode().boundsInRoot
        val bounds = target.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.bottom <= viewport.bottom + 2f, "$text is reachable by scrolling the card body")
    }

    private fun showModel(model: GameplayRenderModel, width: Float, height: Float, onSelect: (Int) -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    RewardContent(model, choiceLayoutGeometry(width, height, 1f, model.choices.size, model.choicesCanReroll), 0f, true, onSelect, {})
                }
            }
        }
    }

    private fun setEnglishContent(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English, content = content)
        }
    }

    private fun capture(name: String) {
        val directory = System.getenv("KINETICKK_RENDER_CAPTURE_DIR") ?: return
        val bitmap = compose.onNodeWithTag("kinetickk.gameplay.rewards").captureToImage()
        val pixels = bitmap.toPixelMap()
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, pixels[x, y].toArgb())
        File(directory).mkdirs()
        ImageIO.write(image, "png", File(directory, "$name.png"))
    }

    private data class Scenario(val width: Float, val height: Float, val count: Int, val scale: Float)

    private fun conciseCards(): List<RewardCardPresentation> {
        val names = listOf("Эгида «Память»", "Демпфер «Гравитация»", "Сборщик «Эхо»")
        val effects = listOf(ItemEffect.SHIELD_CAPACITY to ItemEffect.DATA_GAIN, ItemEffect.DAMAGE_REDUCTION to ItemEffect.MASS, ItemEffect.PICKUP_RADIUS to ItemEffect.COMBO_WINDOW)
        val changes = listOf(
            listOf(RewardStatPresentation("Щит", "0", "7,2", true), RewardStatPresentation("Получение данных", "1×", "1,02×", true)),
            listOf(RewardStatPresentation("Снижение урона", "0", "1,5%", true), RewardStatPresentation("Масса", "1", "1,02", true)),
            listOf(RewardStatPresentation("Радиус сбора", "150", "163,96", true), RewardStatPresentation("Окно комбо", "2,8 с", "2,84 с", true)),
        )
        return names.mapIndexed { index, name ->
            val item = ItemDefinition(320 + index, name, "Catalog flavor must stay outside reward cards", ItemRarity.COMMON,
                ItemModifier(effects[index].first, 1f), ItemModifier(effects[index].second, 1f), 8, 1, "Family")
            RewardCardPresentation(
                ChoiceOption(ChoiceType.ITEM, name, item.description, "ОБЫЧНЫЙ", itemId = item.id),
                Color(0xFF94A0BC), "ОБЫЧНЫЙ", emptyList(), "", item = item, itemStack = 1,
                relicPolicy = RelicPolicy(4, 5), changes = changes[index], bandEnd = "Копии 1/8", connections = when (index) {
                    1 -> listOf(RewardConnection("Ядро", "Усиливает", core = CoreShape.entries.first()), RewardConnection("Эхо массы", "Усиливает",
                        relic = RelicDefinition(RelicId.MASS_ECHO, "Mass Echo", RelicAspect.GRAVITIC, "Mass interaction", "Damage from mass")))
                    2 -> listOf(RewardConnection("Сбор данных", "Усиливает", core = CoreShape.entries.first()))
                    else -> listOf(RewardConnection("Ядро", "Усиливает", core = CoreShape.entries.first()))
                },
            )
        }
    }

    private fun card() = RewardCardPresentation(
        choice = ChoiceOption(ChoiceType.ITEM, "A very long artifact name with every word preserved", "Full description", "LEGENDARY"),
        accent = Color(0xFFFFD45B),
        tag = "LEGENDARY",
        descriptions = List(12) { "Description paragraph $it includes all modifiers and the original catalog text without clipping." },
        operation = "",
        rank = 5,
    )
}

/** SPEC §2: no separator dots, arrows or section marks in any rendered text. */
internal fun SemanticsNodeInteractionsProvider.assertNoForbiddenGlyphs() {
    val forbidden = charArrayOf('·', '→', '←', '↑', '↓', '↵', '›', '‹', '▶', '◀', '◇', '§')
    onAllNodes(isRoot()).fetchSemanticsNodes().forEach { root ->
        fun visit(node: androidx.compose.ui.semantics.SemanticsNode) {
            val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                listOfNotNull(node.config.getOrNull(SemanticsProperties.StateDescription))
            texts.forEach { text -> assertTrue(text.none { it in forbidden }, "Forbidden glyph in \"$text\"") }
            node.children.forEach(::visit)
        }
        visit(root)
    }
}
