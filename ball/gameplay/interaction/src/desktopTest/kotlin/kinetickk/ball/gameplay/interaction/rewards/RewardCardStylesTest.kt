// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.interaction.canvas.elementFill
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.KK_CARD_END_LABEL_START
import kinetickk.foundation.design.KK_CARD_LABEL_CONTRAST
import kinetickk.foundation.design.KK_CARD_START_LABEL_END
import kinetickk.foundation.design.KkCardMaterial
import kinetickk.foundation.design.rarityColors
import kinetickk.foundation.design.KkCardOrnament
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkVisionMode
import kinetickk.foundation.design.bandBackgroundsAt
import kinetickk.foundation.design.bandLabelColors
import kinetickk.foundation.design.kkContrast
import kinetickk.foundation.design.kkMix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The shipped catalog (content implementation), for checks over every real card. */
internal val shippedRewardContent: GameplayContentSnapshot by lazy { createContentCatalog().gameplayContent() }

/**
 * Every card has its own look: the shipped items differ in their style signature, the epic and
 * legendary items (and only they) carry bespoke art, and every band label reads at 4.5:1 or better
 * in every colour-vision mode.
 */
class RewardCardStylesTest {
    private val content = shippedRewardContent

    private fun itemCards(language: AppLanguage = AppLanguage.English): List<RewardCardPresentation> {
        val choices = content.items.map { ChoiceOption(ChoiceType.ITEM, it.name, it.description, it.rarity.displayLabel.uppercase(), itemId = it.id) }
        val model = rewardFixtureModel(content = content, choices = choices, language = language)
        return choices.mapIndexed { index, choice -> model.rewardCardPresentation(choice, index, language) }
    }

    private fun otherCards(): List<RewardCardPresentation> {
        fun card(type: ChoiceType, choice: ChoiceOption) =
            rewardFixtureModel(content = content, choiceType = type, choices = listOf(choice)).rewardCardPresentation(choice, 0)
        val relics = content.relics.map { relic ->
            card(ChoiceType.RELIC, ChoiceOption(ChoiceType.RELIC, relic.name, relic.upside, relic.aspect.displayLabel, relicId = relic.id,
                relicAction = RelicChoiceAction.ACQUIRE))
        }
        val heirloom = content.relics.first().let { relic ->
            card(ChoiceType.RELIC, ChoiceOption(ChoiceType.RELIC, relic.name, relic.upside, relic.aspect.displayLabel, relicId = relic.id,
                relicAction = RelicChoiceAction.ACQUIRE, heirloom = true))
        }
        val weapons = content.weapons.map { weapon ->
            card(ChoiceType.WEAPON, ChoiceOption(ChoiceType.WEAPON, weapon.name, weapon.description, weapon.tags.first(), weaponId = weapon.id))
        }
        val systems = listOf(
            card(ChoiceType.TOTEM, ChoiceOption(ChoiceType.TOTEM, "Amplify", "", "AMPLIFY", weaponId = content.weapons.first().id,
                totemAction = TotemAction.AMPLIFY_CURRENT)),
            card(ChoiceType.TOTEM, ChoiceOption(ChoiceType.TOTEM, "Change weapon", "", "RECALIBRATE", totemAction = TotemAction.CHANGE_WEAPON)),
            card(ChoiceType.RELIC, ChoiceOption(ChoiceType.RELIC, "Leave hollow", "", "DECLINE", relicAction = RelicChoiceAction.DECLINE)),
            card(ChoiceType.RELIC_BIND, ChoiceOption(ChoiceType.RELIC_BIND, "Meld", "", "MELD", relicId = RelicId.GLASS_COMET,
                relicAction = RelicChoiceAction.MELD)),
        )
        val focus = RewardFocus.entries.map { focus ->
            val type = when (focus) {
                RewardFocus.MOTION, RewardFocus.CONTROL, RewardFocus.STRIKE -> ChoiceType.TOTEM
                RewardFocus.OFFENSE, RewardFocus.DEFENSE, RewardFocus.ECONOMY -> ChoiceType.ITEM
                else -> ChoiceType.RELIC
            }
            card(type, ChoiceOption(type, focus.label, "", "FOCUS", rewardFocus = focus))
        }
        return relics + heirloom + weapons + systems + focus
    }

    @Test
    fun everyShippedItemHasItsOwnStyleSignature() {
        val cards = itemCards()
        assertEquals(content.items.size, cards.size)
        // The signature leaves the seed out: two items that share material, hues, ornament and glyph fail
        // however differently their seeds turn the watermark.
        val duplicates = cards.groupBy { it.signature() }.filterValues { it.size > 1 }
        assertTrue(duplicates.isEmpty(), "Items sharing a style signature: " + duplicates.values.joinToString { group -> group.joinToString(" = ") { it.title } })
        // A faint watermark reads as its outline: items that share everything else carry glyphs of
        // different outlines (never two hexagons, two diamonds or two rings side by side).
        val families = cards.filter { rewardCardArt(it.item) == null }.groupBy { card ->
            val style = rewardCardLook(card, KkVisionMode.DEFAULT.palette).style
            listOf(style.material, card.elements.getOrNull(0), card.elements.getOrNull(1), style.ornament)
        }.values.filter { it.size > 1 }
        assertTrue(families.isNotEmpty(), "The catalog has items sharing material, hues and ornament")
        for (family in families) {
            val outlines = family.groupBy { glyphOutline(rewardCardLook(it, KkVisionMode.DEFAULT.palette).style.watermark!!) }
                .filterValues { it.size > 1 }
            assertTrue(outlines.isEmpty(), "Look-alike watermarks: " + outlines.entries.joinToString { (outline, group) ->
                "$outline: " + group.joinToString(" = ") { "${it.title} (${rewardCardLook(it, KkVisionMode.DEFAULT.palette).style.watermark})" }
            })
        }
        // Nothing is a blank slab: every item has a pattern, a frame ornament and either its own
        // watermark or (epic, legendary) its bespoke art, which no generic glyph competes with.
        cards.forEach { card ->
            val look = rewardCardLook(card, KkVisionMode.DEFAULT.palette)
            val style = look.style
            assertNotNull(style.pattern, "${card.title} has a pattern")
            if (look.art == null) assertNotNull(style.watermark, "${card.title} has a watermark")
            else assertNull(style.watermark, "${card.title} lets its art carry its identity")
            assertTrue(style.ornament != KkCardOrnament.NONE, "${card.title} has an ornament")
            assertEquals(KkCardMaterial.ofRank(card.rank), style.material, "${card.title} takes the material of its rarity")
        }
    }

    @Test
    fun cardsSharingRarityTagAndElementStillLookDifferent() {
        // Items of one rarity, primary tag and element share material, hue, pattern and ornament: their
        // own watermark glyph and the seeded placement must still set the faces visibly apart.
        val cards = itemCards().filter { rewardCardArt(it.item) == null }
        val families = cards.groupBy { card -> Triple(card.rank, card.item!!.tags.firstOrNull(), card.item.elements.firstOrNull()) }
            .values.filter { it.size > 1 }
        assertTrue(families.isNotEmpty(), "The catalog has items sharing rarity, tag and element")
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
            AppLanguage.English, roles = KkRolePalette.Default)
        var pairs = 0
        for (family in families) {
            val faces = family.map { card -> face(measurer, card) }
            for (i in faces.indices) for (j in i + 1 until faces.size) {
                val share = differingShare(faces[i], faces[j])
                assertTrue(share >= 0.02f, "${family[i].title} and ${family[j].title} differ over only ${"%.3f".format(share)} of the face")
                pairs++
            }
        }
        assertTrue(pairs >= 7, "Compared every same-family pair ($pairs)")
    }

    /**
     * What a watermark at 5–8 % reads as: its outer outline. Glyphs drawn round one outline (a hexagon,
     * a diamond, a ring) look alike that faint; every other glyph is its own.
     */
    private fun glyphOutline(icon: KkIcon): String = when (icon) {
        KkIcon.SYSTEM_INTEGRITY, KkIcon.SYSTEM_SHIELD, KkIcon.SYSTEM_MATTER, KkIcon.SYSTEM_ELITE -> "hexagon"
        KkIcon.FORMS_DIAMOND, KkIcon.SYSTEM_DATA, KkIcon.SYSTEM_DISMANTLED, KkIcon.SYSTEM_ANOMALY, KkIcon.WEAPONS_PHASE_LATTICE -> "diamond"
        KkIcon.FORMS_CIRCLE, KkIcon.FORMS_RING, KkIcon.WEAPONS_GRAVITY_MINES, KkIcon.WEAPONS_ENTROPY_FIELD, KkIcon.SYSTEM_CYCLE_CLOCK -> "ring"
        else -> icon.name
    }

    /** The bare face of [card] on the 300 x 450 board (frame, material, ornament and watermark; no text). */
    private fun face(measurer: CanvasTextMeasurer, card: RewardCardPresentation): IntArray {
        val bitmap = ImageBitmap(300, 450)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(300f, 450f)) {
            drawRect(Kk.Ink)
            drawRewardCardFace(measurer, card, card.accent, 0f, 0f, -1f, KkPathCache(4), false, rewardCardLook(card, KkRolePalette.Default), RewardArtCache())
        }
        val pixels = bitmap.toPixelMap()
        return IntArray(300 * 450) { pixels[it % 300, it / 300].toArgb() }
    }

    /** The largest channel step between two colours (antialiased edges blend in a level or two of what lies under them). */
    private fun channelStep(p: Int, q: Int): Int {
        var max = 0
        for (shift in intArrayOf(0, 8, 16)) max = maxOf(max, kotlin.math.abs(((p shr shift) and 0xFF) - ((q shr shift) and 0xFF)))
        return max
    }

    /** Share of the face below the band (inside the frame) whose colour differs between [a] and [b] by more than a faint step. */
    private fun differingShare(a: IntArray, b: IntArray): Float {
        var differing = 0
        var total = 0
        for (y in 60 until 440) for (x in 30 until 270) {
            total++
            val p = a[y * 300 + x]
            val q = b[y * 300 + x]
            var max = 0
            for (shift in intArrayOf(0, 8, 16)) max = maxOf(max, kotlin.math.abs(((p shr shift) and 0xFF) - ((q shr shift) and 0xFF)))
            if (max > 6) differing++
        }
        return differing.toFloat() / total
    }

    @Test
    fun epicAndLegendaryItemsAndOnlyThemHaveArt() {
        content.items.forEach { item ->
            val art = rewardCardArt(item)
            if (item.rarity >= ItemRarity.EPIC) {
                assertNotNull(art, "${item.name} (${item.rarity}) has bespoke art")
                assertEquals(item.rarity == ItemRarity.LEGENDARY, art.animated, "${item.name}: legendary art is animated, epic art static")
            } else {
                assertEquals(null, art, "${item.name} (${item.rarity}) has no bespoke art")
            }
        }
        val names = content.items.map { it.name }.toSet()
        RewardCardArt.entries.forEach { art -> assertTrue(art.itemName in names, "${art.itemName} names a shipped item") }
        assertEquals(RewardCardArt.entries.size, content.items.count { it.rarity >= ItemRarity.EPIC }, "One art per epic and legendary item")
    }

    @Test
    fun kineticCardsTakeThePresetVoltAndTheMaterialOfTheirRarity() {
        // Kinetic (no element) is a type like the elements: every Kinetic item, relic, meld and direction
        // takes the preset's own `you` hue on its face and keeps the slash pattern no element uses (Mono's
        // white Kinetic beside its white Cryo); its frame is its rarity's material like any other card's.
        val items = itemCards()
        val kineticItems = items.filter { it.item!!.elements.isEmpty() }
        assertTrue(kineticItems.size >= 40, "The catalog's Kinetic items (${kineticItems.size})")
        val others = otherCards()
        val kineticOthers = others.filter { card ->
            card.relic?.aspect == RelicAspect.KINETIC || card.choice.relicAction == RelicChoiceAction.MELD ||
                (card.choice.rewardFocus != null && card.choice.type != ChoiceType.TOTEM &&
                    RelicAspect.entries.none { it.name == card.choice.rewardFocus!!.name && it.element != null })
        }
        assertTrue(kineticOthers.size >= 9, "Kinetic relics, the meld and the elementless directions (${kineticOthers.size})")
        for (mode in KkVisionMode.entries) {
            val volt = mode.palette.you
            for (card in kineticItems + kineticOthers) {
                val look = buildRewardCardLook(card, mode.palette)
                val style = look.style
                assertEquals(volt, style.hue, "${card.title} in $mode takes the preset's volt")
                assertEquals(volt, look.hue, "${card.title} in $mode: its art and ornament hue")
                assertEquals(KineticFill, style.pattern, "${card.title} in $mode keeps the Kinetic pattern")
                assertTrue(StatusElement.entries.none { elementFill(it.ordinal) == style.pattern }, "the Kinetic pattern is no element's")
                if (card.item != null) {
                    assertEquals(volt, card.iconColor(mode.palette), "${card.title} in $mode: icon")
                    assertEquals(KkCardMaterial.ofRank(card.rank), style.material, "${card.title} takes its rarity's material")
                }
            }
        }
        // Mono paints Kinetic and Cryo near-white: their patterns tell them apart.
        val cryo = items.first { it.item!!.elements.firstOrNull() == StatusElement.CRYO }
        assertTrue(buildRewardCardLook(cryo, KkRolePalette.Mono).style.pattern != KineticFill)
    }

    @Test
    fun rarityAndTypeAreSeparateChannels() {
        // The style model: a card's rarity colours are its material's frame and nothing else, the same for
        // every card of that material whatever its type; its type hue paints the face and is never one of
        // them. Mono is seen as luminance alone and its metals and type tones are the same greys and
        // whites (KkCardFrameVisionTest exempts it): there every card that has a type carries its pattern.
        // How far frames keep from type tones as each preset is seen is KkCardFrameVisionTest's.
        val cards = itemCards() + otherCards()
        for (mode in KkVisionMode.entries) {
            val byMaterial = HashMap<KkCardMaterial, MutableSet<List<Color>>>()
            val types = (0..StatusElement.entries.size).map(mode.palette::tone) + mode.palette.you
            for (card in cards) {
                val look = buildRewardCardLook(card, mode.palette)
                val style = look.style
                val rarity = style.rarityColors()
                byMaterial.getOrPut(style.material) { HashSet() } += rarity
                if (mode == KkVisionMode.MONO) {
                    if (!card.decline) assertNotNull(style.pattern, "${card.title} in Mono tells its type by its pattern")
                } else {
                    assertTrue(look.hue !in rarity && look.secondHue !in rarity, "${card.title} in $mode: its type hue paints the frame")
                    assertTrue(types.none { it in rarity }, "${card.title} in $mode: a type tone paints the frame")
                }
                if (card.relic != null) {
                    // A relic has no rarity: one material whatever its aspect (a Sovereign's gold stays on its face).
                    assertEquals(RelicMaterial, style.material, "${card.title} in $mode wears the relic material")
                    assertTrue(card.rank < 5, "${card.title}: no relic takes the legendary name")
                }
                if (card.item != null) {
                    // The face takes the type: the element (Kinetic is the preset's volt), never the rarity.
                    val expected = card.item.elements.firstOrNull()?.let { mode.palette.element(it.ordinal) } ?: mode.palette.kinetic
                    assertEquals(expected, style.hue, "${card.title} in $mode: its face hue")
                    assertEquals(expected, card.iconColor(mode.palette), "${card.title} in $mode: its icon")
                }
            }
            for ((material, frames) in byMaterial) assertEquals(1, frames.size, "$mode $material: one frame for every type")
        }
        // Items of every rarity are spread over the types, so a rarity is never told by a hue.
        val items = itemCards()
        for (rarity in ItemRarity.entries.filter { it < ItemRarity.LEGENDARY }) {
            val types = items.filter { it.item!!.rarity == rarity }.map { it.item!!.elements.firstOrNull() }.toSet()
            assertTrue(types.size >= 3, "$rarity items come in several types: $types")
        }
    }

    @Test
    fun ruleLinesTakeTheCardsOwnTypeHue() {
        // Trigger bars and numbers take the element their line names, else the card's own type: an
        // element card never shows Kinetic's volt, and a Kinetic card shows nothing else.
        val cards = itemCards()
        var elementLines = 0
        var fallbackLines = 0
        for (mode in KkVisionMode.entries) {
            val roles = mode.palette
            for (card in cards) {
                val item = card.item!!
                val typeHues = item.elements.map { roles.element(it.ordinal) }.ifEmpty { listOf(roles.kinetic) }
                for (line in card.rules.filter { it.kind != RuleLineKind.COST }) {
                    val hue = line.hue(roles, card.iconColor(roles))
                    assertTrue(hue in typeHues, "${card.title} in $mode: '${line.text}' takes $hue, not its type ${item.elements.ifEmpty { "Kinetic" }}")
                    if (item.elements.isNotEmpty()) {
                        elementLines++
                        if (line.element == null) fallbackLines++
                        if (roles.kinetic !in typeHues) assertTrue(hue != roles.kinetic, "${card.title} in $mode: '${line.text}' in Kinetic's volt")
                    }
                }
            }
        }
        assertTrue(fallbackLines >= KkVisionMode.entries.size * 20, "Element cards' lines that name no element ($fallbackLines of $elementLines)")
    }

    @Test
    fun protanAndDeutanCardsTakeTheDeepGold() {
        // Their volt (every Kinetic card's type hue) is a yellow those deficiencies see as the bright gold:
        // the legendary frame, its name foil and the Rebirth marks take the deep gold there.
        val legendary = itemCards().filter { it.rank == 5 }
        assertTrue(legendary.isNotEmpty())
        for (mode in KkVisionMode.entries) {
            val deep = mode == KkVisionMode.PROTAN || mode == KkVisionMode.DEUTAN
            for (card in legendary + otherCards()) {
                val style = buildRewardCardLook(card, mode.palette).style
                assertTrue(style.gold === mode.palette.gold, "${card.title} in $mode paints the preset's gold")
                if (style.material == KkCardMaterial.GOLD) {
                    assertTrue((mode.palette.gold.base in style.rarityColors()) && (deep != (Kk.RLegend in style.rarityColors())),
                        "${card.title} in $mode: its frame's gold")
                }
            }
        }
    }

    @Test
    fun cardsOfOneRarityShareTheirFrameWhateverTheirType() {
        // On screen: the border and the band's leading half of two cards of one rarity but different types
        // (an element and Kinetic) are the same pixels; their faces are not.
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
            AppLanguage.English, roles = KkRolePalette.Default)
        val items = itemCards()
        var compared = 0
        for (rarity in ItemRarity.entries) {
            val ofRarity = items.filter { it.item!!.rarity == rarity }
            val kinetic = ofRarity.firstOrNull { it.item!!.elements.isEmpty() } ?: continue
            val element = ofRarity.firstOrNull { it.item!!.elements.isNotEmpty() } ?: continue
            val a = face(measurer, kinetic)
            val b = face(measurer, element)
            val width = KkCardMaterial.ofRank(rarity.rank).frame.borderDp
            var frameDiffers = 0
            var framePixels = 0
            val where = ArrayList<String>()
            for (y in 60 until 440) {
                // The pixel under the middle of the border on both slanted sides (wholly inside the border).
                val inset = width * 0.5f * (1f - 22f / 450f)
                val left = 22f * (1f - y / 450f) + inset
                val right = 300f - 22f * (y / 450f) - inset
                for (x in intArrayOf(kotlin.math.floor(left).toInt(), kotlin.math.floor(right).toInt())) {
                    framePixels++
                    if (channelStep(a[y * 300 + x], b[y * 300 + x]) > 3) { frameDiffers++; where += "($x,$y)" }
                }
            }
            // The band from its leading edge to the middle (its rarity label, before the type pips).
            for (y in 6 until 36) for (x in 40 until 150) {
                framePixels++
                if (channelStep(a[y * 300 + x], b[y * 300 + x]) > 3) { frameDiffers++; where += "($x,$y)" }
            }
            assertTrue(framePixels > 500, "$rarity: sampled the frame ($framePixels px)")
            assertEquals(0, frameDiffers, "$rarity: ${kinetic.title} and ${element.title} differ in their frame at ${where.take(8)}")
            assertTrue(differingShare(a, b) > 0.02f, "$rarity: ${kinetic.title} and ${element.title} share a face")
            compared++
        }
        assertTrue(compared >= 4, "Compared the rarities ($compared)")
    }

    @Test
    fun itemsSharingATriggerCarryDifferentGlyphs() {
        // The Kill, Impact and Weapon-hit families are large: what each clause does sets its glyph apart.
        val families = content.items.filter { rewardCardArt(it) == null && it.clauses.isNotEmpty() && it.stats.isEmpty() }
            .groupBy { it.clauses.first().trigger }.filterValues { it.size >= 4 }
        assertTrue(families.isNotEmpty(), "The catalog has large trigger families")
        for ((trigger, items) in families) {
            val glyphs = items.map { it.watermarkIcon() }.toSet()
            assertTrue(glyphs.size >= 2, "$trigger items share one glyph: ${items.joinToString { it.name }}")
        }
        val kinetic = content.items.filter { rewardCardArt(it) == null && it.elements.isEmpty() && it.clauses.isNotEmpty() }
            .groupBy { it.clauses.first().trigger }
        for ((trigger, items) in kinetic) {
            val glyphs = items.groupBy { it.watermarkIcon() }.filterValues { it.size > 2 }
            assertTrue(glyphs.isEmpty(), "Kinetic $trigger items share a glyph three times or more: $glyphs")
        }
    }

    @Test
    fun elementNumbersReadOnTheCardFace() {
        // The numbers of a card's lines take the element hue, lifted until they read at 4.5:1 on the face.
        // Kinetic (tone 0) is the preset's volt.
        for (mode in KkVisionMode.entries) for (index in 0..StatusElement.entries.size) {
            val tone = rewardTextTone(mode.palette.tone(index))
            for (face in listOf(Kk.Ink2, Kk.Ink3)) {
                val contrast = kkContrast(tone, face)
                assertTrue(contrast >= KK_CARD_LABEL_CONTRAST, "$mode tone $index: $tone reads at ${"%.2f".format(contrast)}:1 on $face")
            }
        }
        assertEquals(Kk.Bone, rewardTextTone(Kk.Bone), "a readable colour is kept as it is")
    }

    @Test
    fun bandLabelsReadInEveryVisionMode() {
        val cards = itemCards() + otherCards()
        var checked = 0
        for (mode in KkVisionMode.entries) for (card in cards) {
            val style = buildRewardCardLook(card, mode.palette).style
            val (start, end) = style.bandLabelColors()
            // The prism label is a gradient: its stops and the colours between them.
            val painted = start.indices.flatMap { index ->
                val next = start[(index + 1) % start.size]
                listOf(start[index], kkMix(start[index], next, 0.25f), kkMix(start[index], next, 0.5f), kkMix(start[index], next, 0.75f))
            }
            for (step in 0..12) {
                val leading = KK_CARD_START_LABEL_END * step / 12f
                val trailing = KK_CARD_END_LABEL_START + (0.98f - KK_CARD_END_LABEL_START) * step / 12f
                for (background in style.bandBackgroundsAt(leading)) for (color in painted) {
                    assertReadable(color, background, "${card.title} ${style.material} leading label in $mode")
                }
                for (background in style.bandBackgroundsAt(trailing)) {
                    assertReadable(end, background, "${card.title} ${style.material} trailing label in $mode")
                }
            }
            checked++
        }
        assertTrue(checked >= KkVisionMode.entries.size * 100, "Checked every card in every mode ($checked)")
    }

    private fun assertReadable(text: Color, background: Color, scene: String) {
        val contrast = kkContrast(text, background)
        assertTrue(contrast >= KK_CARD_LABEL_CONTRAST, "$scene: $text on $background is only ${"%.2f".format(contrast)}:1")
    }
}
