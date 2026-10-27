// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.FieldAspect
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ImpulseDirection
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.MarkPick
import kinetickk.ball.content.api.ReactionAspect
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.ZoneKind
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.interaction.canvas.activeItemEmptyPipColor
import kinetickk.ball.gameplay.interaction.canvas.elementFill
import kinetickk.ball.gameplay.interaction.canvas.skillGlyph
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
import kinetickk.foundation.collections.toImmutableList
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

    /**
     * An active item's unlit rank pips read on every plate its mark is drawn on, in every colour-vision mode: the card's
     * icon plate (plain, or tinted in a pale hue as Cryo and Void are by default, Ion in Protan and every hue in Mono)
     * and the pause grid's Ink2 plate keep them at 1.35:1 or more (Line2 pips sat near 1:1 on the tinted plates).
     */
    @Test
    fun anActiveItemsUnlitRankPipsReadOnEveryPlate() {
        val actives = itemCards().filter { it.item?.active != null }
        assertEquals(SkillId.entries.size, actives.size, "every active effect has its item")
        for (mode in KkVisionMode.entries) for (card in actives) {
            val look = rewardCardLook(card, mode.palette)
            for (plate in listOf(look.plateTint.takeOrElse { Kk.Ink3 }, Kk.Ink2)) {
                val contrast = kkContrast(activeItemEmptyPipColor(plate), plate)
                assertTrue(contrast >= 1.35f, "$mode ${card.title}: unlit pips at $contrast:1 on ${plate.toArgb().toUInt().toString(16)}")
            }
        }
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
            card(ChoiceType.WEAPON, ChoiceOption(ChoiceType.WEAPON, weapon.name, weapon.description, weapon.tags.first().displayLabel.uppercase(), weaponId = weapon.id))
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

    @Test
    fun everyShippedWeaponOfferHasItsOwnPatternOrnamentAndGlyph() {
        val looks = content.weapons.map { weapon ->
            val choice = ChoiceOption(ChoiceType.WEAPON, weapon.name, weapon.description, weapon.tags.first().displayLabel.uppercase(),
                weaponId = weapon.id)
            val card = rewardFixtureModel(content = content, choiceType = ChoiceType.WEAPON, choices = listOf(choice)).rewardCardPresentation(choice, 0)
            weapon.name to rewardCardLook(card, KkVisionMode.DEFAULT.palette).style
        }
        assertEquals(KkIcon.Weapons.size, looks.size)
        // Every weapon offer is the player's own system in a steel frame and the `you` hue; what the weapon
        // is (orbital, beam, trap, pulse ...) sets its pattern and ornament pair apart, and its icon its glyph.
        looks.forEach { (name, style) ->
            assertEquals(KkCardMaterial.STEEL, style.material, name)
            assertEquals(KkVisionMode.DEFAULT.palette.you, style.hue, name)
            assertTrue(style.ornament != KkCardOrnament.NONE, "$name has an ornament")
        }
        val pairs = looks.groupBy { (_, style) -> style.pattern to style.ornament }.filterValues { it.size > 1 }
        assertTrue(pairs.isEmpty(), "Weapons sharing a pattern and ornament: " + pairs.values.joinToString { group -> group.joinToString(" = ") { it.first } })
        assertEquals(looks.size, looks.mapNotNull { it.second.watermark }.toSet().size, "every weapon carries its own glyph")
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
                assertNotNull(art, "${item.name} (${item.rarity}) has art")
                assertEquals(item.rarity == ItemRarity.LEGENDARY, art.animated, "${item.name}: legendary art is animated, epic art static")
            } else {
                assertEquals(null, art, "${item.name} (${item.rarity}) has no art")
            }
        }
        val names = content.items.map { it.name }.toSet()
        RewardCardArt.entries.forEach { art -> assertTrue(art.itemName in names, "${art.itemName} names a shipped item") }
        // Every scene belongs to the item it was drawn for; later epics and legendaries borrow one from their family.
        RewardCardArt.entries.forEach { art -> assertEquals(art, rewardCardArt(content.items.single { it.name == art.itemName })) }
        val borrowing = content.items.filter { it.rarity >= ItemRarity.EPIC && usesFamilyArt(it) }
        assertEquals(content.items.count { it.rarity >= ItemRarity.EPIC } - RewardCardArt.entries.size, borrowing.size)
        assertTrue(borrowing.isNotEmpty(), "The expansion's epics and legendaries borrow family scenes")
        // A borrowed scene never repeats its owner's look: the hue or the ornament tells them apart.
        val owners = content.items.filter { it.rarity >= ItemRarity.EPIC && !usesFamilyArt(it) }.associateBy { rewardCardArt(it) }
        borrowing.forEach { item ->
            val owner = owners.getValue(rewardCardArt(item))
            assertTrue(owner.elements != item.elements || owner.tags.firstOrNull() != item.tags.firstOrNull(),
                "${item.name} looks like ${owner.name}")
        }
    }

    @Test
    fun everyActiveItemWearsItsEffectsGlyph() {
        // The one glyph table the HUD slots, the card plates and the other screens share: every effect its own mark.
        assertEquals(SkillId.entries.size, SkillId.entries.map(::skillGlyph).toSet().size, "18 effects, 18 glyphs")
        // The per-active watermark table: each effect's own glyph, except where its faint outline would repeat a card
        // of the same rarity, element and ornament (the outline rule above).
        val watermarks = mapOf(
            SkillId.SOLAR_FLARE to KkIcon.WEAPONS_SOLAR_SWEEP, SkillId.METEOR to KkIcon.WEAPONS_MORNINGSTAR,
            SkillId.FROST_LINE to KkIcon.WEAPONS_PHASE_LATTICE, SkillId.FROST_SNAP to KkIcon.WEAPONS_GLACIER_BELL,
            SkillId.ARC_HOOK to KkIcon.WEAPONS_TETHER_ARC, SkillId.STORM_COIL to KkIcon.WEAPONS_ARC_COIL,
            SkillId.GRAVITY_WELL to KkIcon.FORMS_SPIRAL, SkillId.MASS_DRIVER to KkIcon.WEAPONS_SINGULARITY_SPEAR,
            SkillId.DECAY_SPORES to KkIcon.WEAPONS_SCATTER_BLOOM, SkillId.ENTROPIC_TIDE to KkIcon.WEAPONS_ENTROPY_FIELD,
            SkillId.SONIC_BOOM to KkIcon.WEAPONS_RESONANCE_DRUM, SkillId.RESONATOR to KkIcon.WEAPONS_SENTRY_GRID,
            SkillId.VOID_STEP to KkIcon.WEAPONS_HOLLOW_RIFT, SkillId.NULL_SHROUD to KkIcon.WEAPONS_EVENT_LENS,
            SkillId.SLINGSHOT to KkIcon.WEAPONS_GYRE_DISC, SkillId.FULL_STOP to KkIcon.WEAPONS_CONCUSSION_ENGINE,
            SkillId.CATALYZE to KkIcon.WEAPONS_PRISM_RELAY, SkillId.DUAL_INFUSION to KkIcon.WEAPONS_FLUX_WAKE,
        )
        assertEquals(SkillId.entries.toSet(), watermarks.keys)
        SkillId.entries.forEach { assertEquals(watermarks.getValue(it), activeWatermark(it), "$it watermark") }
        assertEquals(listOf(SkillId.GRAVITY_WELL), SkillId.entries.filter { activeWatermark(it) != skillGlyph(it) },
            "only an outline clash moves a watermark off its effect's glyph")
        val cards = itemCards().filter { it.item!!.isActive }
        assertEquals(SkillId.entries.size, cards.size, "every effect is carried by one shipped item")
        for (card in cards) {
            val item = card.item!!
            val look = rewardCardLook(card, KkVisionMode.DEFAULT.palette)
            if (look.art == null) {
                assertEquals(activeWatermark(item.active!!), look.style.watermark, "${item.name} wears its effect's watermark")
            } else {
                // An epic active borrows a scene of its own family, never one drawn for an item that looks like it.
                assertEquals(ItemRarity.EPIC, item.rarity, item.name)
                assertTrue(usesFamilyArt(item), "${item.name} has a family scene of its own")
            }
            assertEquals(item.rarity.rank, card.rank, "${item.name} takes its rarity's frame")
        }
    }

    @Test
    fun anEpicOrLegendaryMissingFromTheArtTablesStillGetsASceneOfItsKind() {
        val template = content.items.first { it.rarity == ItemRarity.EPIC }
        ItemRarity.entries.forEach { rarity ->
            BuildTag.entries.map { listOf(it) }.plus(listOf(emptyList())).forEach { tags ->
                val unknown = template.copy(name = "Unlisted ${rarity.name} ${tags.firstOrNull()}", rarity = rarity,
                    tags = tags.toImmutableList())
                val art = rewardCardArt(unknown)
                if (rarity >= ItemRarity.EPIC) assertEquals(rarity == ItemRarity.LEGENDARY, assertNotNull(art).animated)
                else assertNull(art)
            }
        }
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
    fun theSkillApexFieldAndMarkVocabularyCarriesItsOwnGlyphs() {
        fun clauseItem(trigger: EffectTrigger, action: EffectAction) = vocabularyItem(
            clauses = listOf(EffectClause(trigger, actions = kinetickk.foundation.collections.immutableListOf(action), cooldownSeconds = 2f)),
        )
        fun ruleItem(rule: ItemRule) = vocabularyItem(rules = listOf(rule))
        // A clause's glyph is its first action's mark; an action without one falls to its trigger's.
        val strike = EffectAction.Strike(DamageBasis.HIT, 0.5f)
        assertEquals(
            listOf(
                KkIcon.WEAPONS_SOLAR_SWEEP, KkIcon.WEAPONS_HUNTER_SALVO, KkIcon.SYSTEM_ELITE, KkIcon.WEAPONS_GRAVITY_MINES,
                KkIcon.WEAPONS_AEGIS_SHARDS, KkIcon.WEAPONS_NULL_LANCE,
            ),
            listOf(EffectTrigger.SKILL_CAST, EffectTrigger.SKILL_HIT, EffectTrigger.APEX, EffectTrigger.FIELD_ENTER,
                EffectTrigger.SHOT_ERASED, EffectTrigger.MARK_CONSUMED).map { clauseItem(it, strike).watermarkIcon() },
        )
        val actionGlyphs = listOf(
            EffectAction.RefundSkills(0.5f) to KkIcon.WEAPONS_RESONANCE_DRUM,
            EffectAction.ChargeApex(1f) to KkIcon.FORMS_STAR,
            EffectAction.ForceReaction(100f, 2) to KkIcon.WEAPONS_SCATTER_BLOOM,
            EffectAction.Zone(ZoneKind.HUSH, 120f, 2f) to KkIcon.WEAPONS_EVENT_LENS,
            EffectAction.EraseShots(200f, 3) to KkIcon.WEAPONS_AEGIS_SHARDS,
            EffectAction.CoreImpulse(300f, ImpulseDirection.FORWARD) to KkIcon.FORMS_CRESCENT,
            EffectAction.ApplyMark(MarkPick.CRACKED, 2f) to KkIcon.WEAPONS_HOLLOW_RIFT,
            EffectAction.Throw(400f) to KkIcon.WEAPONS_CONCUSSION_ENGINE,
            EffectAction.PrimeDash to KkIcon.FORMS_SPIRAL,
        )
        for ((action, glyph) in actionGlyphs) {
            // On a kill (its own glyph the Entropy Field) every new action shows its own mark, never the trigger's.
            assertEquals(glyph, clauseItem(EffectTrigger.SLAM, action).watermarkIcon(), "$action")
            assertEquals(glyph.name, glyphOutline(glyph), "$glyph reads as its own outline")
        }
        assertEquals(actionGlyphs.size, actionGlyphs.map { it.second }.toSet().size, "nine actions, nine glyphs")

        val whileGlyphs = listOf(
            EffectCondition.CoreInField to KkIcon.WEAPONS_GRAVITY_MINES,
            EffectCondition.ApexActive to KkIcon.SYSTEM_ELITE,
            EffectCondition.SkillsCharging(2) to KkIcon.SYSTEM_CYCLE_CLOCK,
            EffectCondition.WeaponFamilyIs(WeaponFamily.MOMENTUM) to KkIcon.WEAPONS_GYRE_DISC,
            EffectCondition.WeaponFamilyIs(WeaponFamily.ORBITAL) to KkIcon.FORMS_DIPOLE,
            EffectCondition.WeaponFamilyIs(WeaponFamily.AURA) to KkIcon.WEAPONS_GLACIER_BELL,
            EffectCondition.WeaponFamilyIs(WeaponFamily.PROJECTILE) to KkIcon.WEAPONS_SCATTER_BLOOM,
            EffectCondition.WeaponFamilyIs(WeaponFamily.TRAP) to KkIcon.WEAPONS_SENTRY_GRID,
            EffectCondition.WeaponFamilyIs(WeaponFamily.CHAIN) to KkIcon.FORMS_PENTAGON,
            EffectCondition.WeaponFamilyIs(WeaponFamily.BEAM) to KkIcon.WEAPONS_SINGULARITY_SPEAR,
            EffectCondition.WeaponFamilyIs(WeaponFamily.PULSE) to KkIcon.WEAPONS_CONCUSSION_ENGINE,
        )
        for ((condition, glyph) in whileGlyphs) {
            assertEquals(glyph, ruleItem(ItemRule.WhileModifier(condition, BuffStat.DAMAGE, 0.1f)).watermarkIcon(), "$condition")
        }
        val ruleGlyphs = listOf(
            ItemRule.ReactionKeystone(ReactionId.FUSION) to KkIcon.WEAPONS_TETHER_ARC,
            ItemRule.ReactionTuning(null, ReactionAspect.RADIUS, 0.2f) to KkIcon.WEAPONS_PRISM_RELAY,
            ItemRule.FieldTuning(FieldAspect.RADIUS, 0.2f) to KkIcon.WEAPONS_GRAVITY_MINES,
            ItemRule.SkillCooldown(null, 0.1f) to KkIcon.SYSTEM_CYCLE_CLOCK,
            ItemRule.SkillPower(null, 0.2f) to KkIcon.WEAPONS_SOLAR_SWEEP,
            ItemRule.SkillCharges to KkIcon.SYSTEM_CYCLE_CLOCK,
            ItemRule.SkillAttunement(ElementPick.DOMINANT) to KkIcon.WEAPONS_SOLAR_SWEEP,
            ItemRule.CatalysisRate(0.5f) to KkIcon.WEAPONS_ARC_COIL,
            ItemRule.ApexCooldown(0.1f) to KkIcon.SYSTEM_ELITE,
            ItemRule.CoreLinkRadius(100f) to KkIcon.SYSTEM_POLARITY,
        )
        for ((rule, glyph) in ruleGlyphs) assertEquals(glyph, ruleItem(rule).watermarkIcon(), "$rule")
    }

    private fun vocabularyItem(clauses: List<EffectClause> = emptyList(), rules: List<ItemRule> = emptyList()) =
        kinetickk.ball.content.api.ItemDefinition(
            id = 0, name = "Glyph Probe", description = "Shows one glyph.", rarity = ItemRarity.EPIC,
            tags = kinetickk.foundation.collections.immutableListOf(BuildTag.CHAIN), clauses = clauses.toImmutableList(),
            rules = rules.toImmutableList(), maxStacks = 1, unlockLevel = 1,
        )

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
