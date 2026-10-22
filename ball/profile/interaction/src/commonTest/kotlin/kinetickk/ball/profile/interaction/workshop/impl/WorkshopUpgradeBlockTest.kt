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
        val track = characterUpgradeTrack(policy, orb, level = 0, matter = 50L, unlocked = true)
        assertEquals(50L, track.nextPrice)
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
        assertEquals("50", texts.cost)
        assertNull(texts.need)
        assertEquals("Lvl 0 of 10", upgradeLevelText(track, english))
    }

    @Test
    fun aShortBalanceNamesTheMissingMatterAndOneMoreMatterBuys() {
        // Level 5 of a base-50 track costs 50 × 2^5 = 1,600.
        val short = characterUpgradeTrack(policy, orb, level = 5, matter = 1_599L, unlocked = true)
        assertEquals(1_600L, short.nextPrice)
        assertFalse(short.affordable || short.canBuy)
        assertEquals(1L, short.shortfall)
        assertEquals("Need 1 more matter", workshopUpgradeTexts(short, english).need)
        assertEquals("+10", UpgradeStat.INTEGRITY.value(short.effects[0].now, english))
        assertEquals("+12", UpgradeStat.INTEGRITY.value(short.effects[0].next!!, english))
        assertTrue(characterUpgradeTrack(policy, orb, level = 5, matter = 1_600L, unlocked = true).canBuy)
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
