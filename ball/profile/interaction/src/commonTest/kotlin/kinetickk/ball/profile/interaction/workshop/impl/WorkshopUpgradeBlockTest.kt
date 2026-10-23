// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.UpgradePolicy
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.interaction.TestWeapons
import kinetickk.ball.profile.interaction.profileFrame
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The upgrade block's model and geometry: prices follow the content rule the Profile charges, the
 * level is clamped to the policy, and the block's states (affordable, short, maximum, not sold) read
 * as the Workshop spec lists them.
 */
class WorkshopUpgradeBlockTest {
    private val policy = UpgradePolicy()
    private val orb = defaultCoreShapeDefinitions().first { it.id == CoreShape.ORB }
    private val english = AppLanguage.English

    @Test
    fun levelZeroAffordableOffersTheBasePriceAndPreviewsTheFirstLevel() {
        val track = characterUpgradeTrack(policy, orb, level = 0, matter = 55L, unlocked = true)
        assertEquals(55L, track.nextPrice)
        assertTrue(track.affordable && track.canBuy)
        assertEquals(0L, track.shortfall)
        assertEquals(listOf(UpgradeEffectLine(UpgradeStat.INTEGRITY, 0f, 2f), UpgradeEffectLine(UpgradeStat.CHARACTER_DAMAGE, 0f, 2f)),
            track.effects)
        // Nothing yet: Now shows the dash; Next the first level.
        assertEquals("—", UpgradeStat.INTEGRITY.value(track.effects[0].now, english))
        assertEquals("+2", UpgradeStat.INTEGRITY.value(track.effects[0].next!!, english))
        assertEquals("+2%", UpgradeStat.CHARACTER_DAMAGE.value(track.effects[1].next!!, english))
        val texts = workshopUpgradeTexts(track, english)
        assertEquals("Upgrade", texts.label)
        assertEquals("55", texts.cost)
        assertNull(texts.need)
        assertEquals("Lvl 0 of 10", upgradeLevelText(track, english))
    }

    @Test
    fun aShortBalanceNamesTheMissingMatterAndOneMoreMatterBuys() {
        // Level 5 of a base-55 track costs 55 × 2^5 = 1,760.
        val short = characterUpgradeTrack(policy, orb, level = 5, matter = 1_759L, unlocked = true)
        assertEquals(1_760L, short.nextPrice)
        assertFalse(short.affordable || short.canBuy)
        assertEquals(1L, short.shortfall)
        assertEquals("Need 1 more matter", workshopUpgradeTexts(short, english).need)
        assertEquals("+10", UpgradeStat.INTEGRITY.value(short.effects[0].now, english))
        assertEquals("+12", UpgradeStat.INTEGRITY.value(short.effects[0].next!!, english))
        assertTrue(characterUpgradeTrack(policy, orb, level = 5, matter = 1_760L, unlocked = true).canBuy)
    }

    @Test
    fun theMaximumHasNoPriceNoNextValueAndClampsLevelsAboveIt() {
        val top = characterUpgradeTrack(policy, orb, level = policy.maxLevel, matter = Long.MAX_VALUE, unlocked = true)
        assertTrue(top.maxed)
        assertFalse(top.canBuy)
        assertNull(top.nextPrice)
        assertTrue(top.effects.all { it.next == null })
        assertEquals("+20%", UpgradeStat.CHARACTER_DAMAGE.value(top.effects[1].now, english))
        val texts = workshopUpgradeTexts(top, english)
        assertEquals("Maximum", texts.label)
        assertNull(texts.cost)
        assertNull(texts.need)
        // A level above a lowered maximum reads as the maximum (the Profile clamps it on load).
        assertEquals(top, characterUpgradeTrack(policy, orb, level = policy.maxLevel + 1, matter = Long.MAX_VALUE, unlocked = true))
        assertEquals(3, characterUpgradeTrack(UpgradePolicy(maxLevel = 3), orb, level = 7, matter = 0L, unlocked = true).level)
    }

    @Test
    fun aTrackThatIsNotSoldShowsLockedWithTheFirstLevelPreview() {
        val weapon = TestWeapons.first()
        val track = weaponUpgradeTrack(policy, weapon, level = 0, matter = Long.MAX_VALUE, owned = false)
        assertFalse(track.owned || track.affordable || track.canBuy)
        assertEquals(listOf(UpgradeEffectLine(UpgradeStat.WEAPON_DAMAGE, 0f, 3f)), track.effects)
        val texts = workshopUpgradeTexts(track, english)
        assertEquals("Locked", texts.label)
        assertNull(texts.cost)
        assertNull(texts.need)
        assertEquals("Сначала откройте это оружие", upgradeRefusalText(ProfileRejection.WeaponNotOwned, track, AppLanguage.Russian))
    }

    @Test
    fun theLastLevelOfTheDearestBaseFitsTheCompactPriceAndRefusalsNameTheirReason() {
        // The tenth level of the content's largest base costs 10,000 × 2^9 = 5,120,000 (no overflow, compact text).
        val dearest = orb.copy(upgradeBaseCost = ContentBounds.MAX_UPGRADE_BASE_COST)
        val track = characterUpgradeTrack(policy, dearest, level = 9, matter = 0L, unlocked = true)
        assertEquals(5_120_000L, track.nextPrice)
        assertEquals(5_120_000L, track.shortfall)
        val cost = workshopUpgradeTexts(track, english).cost!!
        assertTrue(cost.length <= 6, cost)
        assertEquals("Already at level 10", upgradeRefusalText(ProfileRejection.UpgradeMaxLevel, track, english))
        assertEquals("Unlock this character first", upgradeRefusalText(ProfileRejection.CoreShapeLocked, track, english))
        assertEquals("Not applied. Try again", upgradeRefusalText(ProfileRejection.NoChange, track, english))
        // Honing values keep their tenths (3 % per level on a 2.5 % policy shows one decimal).
        val half = weaponUpgradeTrack(UpgradePolicy(weaponPowerPerLevel = 0.025f), TestWeapons.first(), 1, 0L, owned = true)
        assertEquals("+2.5%", UpgradeStat.WEAPON_DAMAGE.value(half.effects.single().now, english))
    }

    /** Every character's Refit signature sentence (economy §7) in both languages, its numbers from the content steps. */
    @Test
    fun everyRefitNamesItsSignatureParameterPerLevel() {
        val expected = mapOf(
            CoreShape.ORB to ("Orbit charges on a 2% shorter arc per level" to "Орбита заряжается на дуге короче на 2% за уровень"),
            CoreShape.PRISM to ("Facets regrow 3% faster per level" to "Грани отрастают на 3% быстрее за уровень"),
            CoreShape.SHARD to ("Punctures vent 0.8 more heat per level" to "Прокол сбрасывает на 0,8 жара больше за уровень"),
            CoreShape.RING to ("The halo reaches 3 farther per level" to "Ореол шире на 3 за уровень"),
            CoreShape.DIAMOND to ("The parry window grows 4 ms per level" to "Окно парирования длиннее на 4 мс за уровень"),
            CoreShape.TESSERACT to ("Lattice nodes last 0.2 s longer per level" to "Узлы решётки живут на 0,2 с дольше за уровень"),
            CoreShape.STAR to ("Supernova radius +4 per level" to "Радиус сверхновой +4 за уровень"),
            CoreShape.SPIRAL to ("The echo swallows shots 0.03 s sooner per level" to "Эхо поглощает снаряды на 0,03 с чаще за уровень"),
            CoreShape.DIPOLE to ("Attract reach +4 and Repel reach +3 per level" to "Притяжение +4 и отталкивание +3 к радиусу за уровень"),
            CoreShape.PENTAGON to ("On-beat bursts +3 radius per level" to "Радиус всплеска в такт +3 за уровень"),
            CoreShape.CRESCENT to ("Blink recharges 0.04 s faster per level" to "Скачок перезаряжается на 0,04 с быстрее за уровень"),
            CoreShape.HEXAGON to ("Drones last 0.4 s longer per level" to "Дроны живут на 0,4 с дольше за уровень"),
        )
        assertEquals(CoreShape.entries.toSet(), expected.keys)
        for (definition in defaultCoreShapeDefinitions()) {
            val track = characterUpgradeTrack(policy, definition, level = 3, matter = 0L, unlocked = true)
            val (en, ru) = expected.getValue(definition.id)
            assertEquals(en, workshopUpgradeTexts(track, english).signature, "${definition.id}")
            assertEquals(ru, workshopUpgradeTexts(track, AppLanguage.Russian).signature, "${definition.id}")
        }
        // The sentence follows the content step: a retuned step changes the number it shows.
        val retuned = orb.copy(upgradeSignature = kinetickk.ball.content.api.CharacterUpgradeSignature(-0.025f))
        assertEquals("Orbit charges on a 2.5% shorter arc per level",
            workshopUpgradeTexts(characterUpgradeTrack(policy, retuned, 0, 0L, unlocked = true), english).signature)
        // Honing has no signature.
        assertNull(weaponUpgradeTrack(policy, TestWeapons.first(), 0, 0L, owned = true).signature)
        assertEquals("0,03", signatureNumber(0.03f, AppLanguage.Russian))
        assertEquals("12", signatureNumber(12f, english))
    }

    @Test
    fun theSignatureSentenceSitsBetweenTheEffectLinesAndTheAction() {
        val regular = profileFrame(1440f, 810f, 1f)
        val plain = workshopUpgradeLayout(regular, 0f, 0f, width = 440f, textScale = 1f, effectCount = 2, buyWidth = 120f, needWidth = null)
        assertNull(plain.signature)
        val signed = workshopUpgradeLayout(regular, 0f, 0f, width = 440f, textScale = 1f, effectCount = 2, buyWidth = 120f, needWidth = null,
            signatureHeight = 34f)
        val signature = signed.signature!!
        assertTrue(signature.top >= signed.effects.last().bottom)
        assertEquals(34f, signature.height)
        assertTrue(signed.buy.top >= signature.bottom)
        // The block grows by the sentence and the gap above it.
        assertEquals(plain.height + 34f + 6f, signed.height, 0.01f)
    }

    @Test
    fun theBlockStacksHeaderPipsLinesAndTheActionWithTheNeedBesideOrUnderIt() {
        val regular = profileFrame(1440f, 810f, 1f)
        val layout = workshopUpgradeLayout(regular, left = 100f, top = 200f, width = 440f, textScale = 1f, effectCount = 2,
            buyWidth = 120f, needWidth = 150f)
        assertEquals(200f, layout.header.top)
        assertEquals(222f, layout.header.bottom)
        // Ten 26 dp pips 4 dp apart.
        assertEquals(296f, layout.pips.width)
        assertEquals(14f, layout.pips.height)
        assertEquals(2, layout.effects.size)
        assertTrue(layout.effects.first().top >= layout.captions.bottom)
        // The action keeps its 220 board px minimum and 52 board px height; the need sits beside it.
        assertEquals(220f, layout.buy.width)
        assertEquals(52f, layout.buy.height)
        assertTrue(layout.buy.top >= layout.effects.last().bottom)
        val need = layout.need!!
        assertEquals(layout.buy.right + 16f, need.left)
        assertEquals(layout.buy.top, need.top)
        assertEquals(168f, layout.height)
        // Less than 96 px beside the action: the need stacks under it and the block grows by it.
        val narrow = workshopUpgradeLayout(regular, 0f, 0f, width = 300f, textScale = 1f, effectCount = 1, buyWidth = 220f,
            needWidth = 150f, needHeight = 18f)
        assertEquals(narrow.buy.bottom + 4f, narrow.need!!.top)
        assertEquals(narrow.need!!.bottom, narrow.height)
        // Phones use the smaller pips, lines and action.
        val phone = profileFrame(390f, 844f, 1f)
        val compact = workshopUpgradeLayout(phone, 0f, 0f, width = 358f, textScale = 1f, effectCount = 2, buyWidth = 100f, needWidth = null)
        assertEquals(207f, compact.pips.width, 0.01f)
        assertEquals(44f, compact.buy.height, 0.01f)
        assertEquals(170f, compact.buy.width, 0.01f)
        assertNull(compact.need)
        assertTrue(compact.height in 120f..136f, "${compact.height}")
        // Larger text grows the text rows, never the pips or the action.
        val large = workshopUpgradeLayout(regular, 0f, 0f, width = 440f, textScale = 1.4f, effectCount = 2, buyWidth = 120f, needWidth = null)
        assertTrue(large.header.height > layout.header.height)
        assertEquals(layout.pips.height, large.pips.height)
        assertEquals(layout.buy.height, large.buy.height)
    }
}
