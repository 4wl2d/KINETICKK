// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Visual QA gallery of the reward card styles (writes PNGs only when `KK_SHOTS_OUT` names a folder):
 *
 * - every shipped item's real [RewardCard] in sheets of eight, board (300 × 450) and phone (195 × 254),
 *   in the Default palette, and on the board in Protan, Deutan and Mono;
 * - one Russian sheet, all relics, and a sheet of weapon, totem, direction, meld and decline cards;
 * - a six-frame strip (t = 0..5 s) of each legendary.
 *
 * `KK_GALLERY_ONLY` (comma-separated canonical item names) renders only those items: their sheets
 * in every palette, the Russian sheet and their legendary strips.
 */
@OptIn(ExperimentalTestApi::class)
class RewardCardGalleryShotsTest {
    private val output = System.getenv("KK_SHOTS_OUT")?.takeIf { it.isNotBlank() }?.let(::File)
    private val only = System.getenv("KK_GALLERY_ONLY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
    private val content: GameplayContentSnapshot = shippedRewardContent

    @Test
    fun rendersTheCardGallery() {
        val folder = output ?: return
        folder.mkdirs()
        val items = content.items.filter { only == null || it.name in only }
        require(items.isNotEmpty()) { "KK_GALLERY_ONLY matched no shipped item: $only" }
        items.chunked(8).forEachIndexed { sheet, chunk ->
            val number = "%02d".format(sheet + 1)
            val english = itemCards(chunk, AppLanguage.English)
            render(File(folder, "items-board-default-$number.png"), english, Board, KkRolePalette.Default)
            render(File(folder, "items-phone-default-$number.png"), english, Phone, KkRolePalette.Default)
            render(File(folder, "items-board-protan-$number.png"), english, Board, KkRolePalette.Protan)
            render(File(folder, "items-board-deutan-$number.png"), english, Board, KkRolePalette.Deutan)
            render(File(folder, "items-board-mono-$number.png"), english, Board, KkRolePalette.Mono)
        }
        val russian = if (only != null) items.take(8) else representativeItems()
        render(File(folder, "items-board-default-ru.png"), itemCards(russian, AppLanguage.Russian), Board, KkRolePalette.Default, AppLanguage.Russian)
        if (only == null) {
            relicCards().chunked(8).forEachIndexed { sheet, chunk ->
                render(File(folder, "relics-board-default-${"%02d".format(sheet + 1)}.png"), chunk, Board, KkRolePalette.Default)
            }
            systemCards().chunked(8).forEachIndexed { sheet, chunk ->
                render(File(folder, "systems-board-default-${"%02d".format(sheet + 1)}.png"), chunk, Board, KkRolePalette.Default)
            }
            render(File(folder, "systems-phone-default.png"), systemCards().take(8), Phone, KkRolePalette.Default)
        }
        items.filter { it.rarity == ItemRarity.LEGENDARY }.forEach { item ->
            val card = itemCards(listOf(item), AppLanguage.English).single()
            val slug = item.name.lowercase().replace(Regex("[^a-z]+"), "-").trim('-')
            render(File(folder, "legendary-$slug-strip.png"), List(6) { card }, Board, KkRolePalette.Default, times = List(6) { it.toFloat() })
            render(File(folder, "legendary-$slug-strip-phone.png"), List(6) { card }, Phone, KkRolePalette.Default, times = List(6) { it.toFloat() })
        }
    }

    private class CardSize(val width: Int, val height: Int, val phone: Boolean)

    private fun itemCards(items: List<ItemDefinition>, language: AppLanguage): List<RewardCardPresentation> {
        val choices = items.map { ChoiceOption(ChoiceType.ITEM, it.name, it.description, it.rarity.displayLabel.uppercase(), itemId = it.id) }
        val model = rewardFixtureModel(content = content, choices = choices, discoveredItems = content.items.map { it.id }.toSet(), language = language)
        return choices.mapIndexed { index, choice -> model.rewardCardPresentation(choice, index, language) }
    }

    /** One of each rarity, the two-element item, two epics and the legendaries. */
    private fun representativeItems(): List<ItemDefinition> {
        val byRarity = ItemRarity.entries.take(3).mapNotNull { rarity -> content.items.firstOrNull { it.rarity == rarity } }
        val split = content.items.filter { it.elements.size > 1 }.take(1)
        val epics = content.items.filter { it.rarity == ItemRarity.EPIC }.let { listOf(it.first(), it.last()) }
        val legendaries = content.items.filter { it.rarity == ItemRarity.LEGENDARY }
        return (byRarity + split + epics + legendaries).distinct().take(8)
    }

    private fun relicCards(): List<RewardCardPresentation> {
        val choices = content.relics.map { relic ->
            ChoiceOption(ChoiceType.RELIC, relic.name, relic.upside, relic.aspect.displayLabel.uppercase(), relicId = relic.id,
                relicAction = RelicChoiceAction.ACQUIRE)
        }
        val model = rewardFixtureModel(content = content, choiceType = ChoiceType.RELIC, choices = choices,
            discoveredRelics = RelicId.entries.toSet())
        return choices.mapIndexed { index, choice -> model.rewardCardPresentation(choice, index) }
    }

    private fun systemCards(): List<RewardCardPresentation> {
        fun card(type: ChoiceType, choice: ChoiceOption) = rewardFixtureModel(content = content, choiceType = type, choices = listOf(choice),
            discoveredRelics = RelicId.entries.toSet()).rewardCardPresentation(choice, 0)
        val weapons = content.weapons.map { weapon ->
            card(ChoiceType.WEAPON, ChoiceOption(ChoiceType.WEAPON, weapon.name, weapon.description, weapon.tags.first().displayLabel.uppercase(), weaponId = weapon.id))
        }
        val totems = listOf(
            card(ChoiceType.TOTEM, ChoiceOption(ChoiceType.TOTEM, "Amplify Flux Wake", "Raise current weapon mastery by one level.", "AMPLIFY",
                weaponId = content.weapons.first().id, totemAction = TotemAction.AMPLIFY_CURRENT)),
            card(ChoiceType.TOTEM, ChoiceOption(ChoiceType.TOTEM, "Change weapon", "Recalibrate the Totem.", "RECALIBRATE",
                totemAction = TotemAction.CHANGE_WEAPON)),
        )
        val focus = listOf(RewardFocus.OFFENSE to ChoiceType.ITEM, RewardFocus.GRAVITIC to ChoiceType.RELIC, RewardFocus.MOTION to ChoiceType.TOTEM)
            .map { (focus, type) -> card(type, ChoiceOption(type, focus.label, "Choose a direction.", "FOCUS", rewardFocus = focus)) }
        val declines = listOf(
            card(ChoiceType.RELIC, ChoiceOption(ChoiceType.RELIC, "Leave hollow", "", "DECLINE", relicAction = RelicChoiceAction.DECLINE)),
            card(ChoiceType.RELIC, ChoiceOption(ChoiceType.RELIC, "Leave heirloom", "", "DECLINE", relicAction = RelicChoiceAction.DECLINE, heirloom = true)),
        )
        return totems + focus + declines + weapons
    }

    private fun render(
        file: File,
        cards: List<RewardCardPresentation>,
        size: CardSize,
        palette: KkRolePalette,
        language: AppLanguage = AppLanguage.English,
        times: List<Float> = List(cards.size) { SheetTime },
    ) {
        val columns = if (cards.size <= 6 && times.any { it != SheetTime }) cards.size else 4
        val rows = (cards.size + columns - 1) / columns
        val cellWidth = size.width + Pad * 2
        val cellHeight = size.height + Pad * 2
        val width = columns * cellWidth
        val height = rows * cellHeight
        runDesktopComposeUiTest(width, height) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language, LocalKkRolePalette provides palette) {
                    Box(Modifier.requiredSize(width.dp, height.dp).background(Kk.Ink)) {
                        cards.forEachIndexed { index, card ->
                            val x = (index % columns) * cellWidth
                            val y = (index / columns) * cellHeight
                            // Clipped per cell: legendary rays reach far past a card.
                            Box(Modifier.offset(x.dp, y.dp).requiredSize(cellWidth.dp, cellHeight.dp).clipToBounds()) {
                                RewardCard(card, index, overlayUiTextScale(1.25f), 0f, true,
                                    Modifier.offset(Pad.dp, Pad.dp).requiredSize(size.width.dp, size.height.dp),
                                    time = remember { mutableFloatStateOf(times[index]) }, phoneDeal = size.phone) {}
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val bitmap = onRoot().captureToImage()
            val pixels = bitmap.toPixelMap()
            val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, pixels[x, y].toArgb())
            ImageIO.write(image, "png", file)
        }
    }

    private companion object {
        val Board = CardSize(300, 450, phone = false)
        val Phone = CardSize(195, 254, phone = true)
        const val Pad = 18

        /** Mid-loop: the rare sheen and the epic glint are on their way across the card. */
        const val SheetTime = 2.3f
    }
}
