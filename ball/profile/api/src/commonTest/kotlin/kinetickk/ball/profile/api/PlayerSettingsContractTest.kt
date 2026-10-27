// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.input.BindableKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlayerSettingsContractTest {
    @Test
    fun defaultBindingsReproduceTheShippedKeysPlusSkillAndApexKeys() {
        val defaults = KeyBindings.DEFAULT

        assertEquals(14, GameAction.entries.size)
        assertEquals(
            listOf(
                BindableKey.SPACE, BindableKey.SHIFT, BindableKey.Z, BindableKey.X, BindableKey.C, BindableKey.V,
                BindableKey.P, BindableKey.Q, BindableKey.R, BindableKey.DIGIT_1, BindableKey.DIGIT_2,
                BindableKey.DIGIT_3, BindableKey.DIGIT_4, BindableKey.F3,
            ),
            GameAction.entries.map(defaults::key),
        )
        assertTrue(defaults.isDefault)
        GameAction.entries.forEach { action -> assertEquals(action, defaults.action(defaults.key(action))) }
        assertNull(defaults.action(BindableKey.F))
        assertEquals(KeyBindings.DEFAULT, PlayerPreferences().keyBindings)
    }

    @Test
    fun rebindingAnOwnedKeySwapsWithItsHolderAndRebindingBackRestoresTheDefault() {
        val defaults = KeyBindings.DEFAULT

        val swapped = defaults.with(GameAction.DASH, BindableKey.Z)
        assertEquals(BindableKey.Z, swapped.key(GameAction.DASH))
        assertEquals(BindableKey.SPACE, swapped.key(GameAction.SKILL_1))
        assertEquals(GameAction.DASH, swapped.action(BindableKey.Z))
        assertEquals(GameAction.SKILL_1, swapped.action(BindableKey.SPACE))
        assertFalse(swapped.isDefault)
        assertEquals(defaults, swapped.with(GameAction.DASH, BindableKey.SPACE))
        assertTrue(swapped.with(GameAction.DASH, BindableKey.SPACE).isDefault)

        val free = defaults.with(GameAction.APEX, BindableKey.F)
        assertEquals(BindableKey.F, free.key(GameAction.APEX))
        assertNull(free.action(BindableKey.V))
        assertEquals(GameAction.entries.size, free.keys.toSet().size)

        assertSame(defaults, defaults.with(GameAction.PAUSE, BindableKey.P))
        assertEquals(BindableKey.SPACE, defaults.key(GameAction.DASH), "the source map is never changed")
    }

    @Test
    fun bindingsRequireOneDistinctKeyPerAction() {
        val keys = KeyBindings.DEFAULT.keys

        assertEquals(KeyBindings.DEFAULT, KeyBindings.of(keys))
        assertFailsWith<IllegalArgumentException> { KeyBindings.of(keys.dropLast(1)) }
        assertFailsWith<IllegalArgumentException> { KeyBindings.of(keys + BindableKey.F) }
        assertFailsWith<IllegalArgumentException> { KeyBindings.of(keys.dropLast(1) + keys.first()) }
        assertFalse(KeyBindings.isValid(keys.dropLast(1) + keys.first()))
        assertTrue(KeyBindings.isValid(keys))
    }

    @Test
    fun displayPreferencesDefaultAndNormalizeToTheirOptionSets() {
        val defaults = PlayerPreferences()
        assertEquals(WindowMode.WINDOWED, defaults.windowMode)
        assertEquals(WindowSizePreset.W1280X800, defaults.windowSize)
        assertEquals(1280 to 800, defaults.windowSize.widthDp to defaults.windowSize.heightDp)
        assertTrue(defaults.vsync)
        assertEquals(0, defaults.frameRateCap)
        assertEquals(REFRESH_RATE_AUTO, defaults.refreshRateHz)
        assertFalse(defaults.immersive)
        assertEquals(100, defaults.interfaceScalePercent)
        assertFalse(defaults.showFps)
        assertTrue(defaults.pauseOnFocusLoss)
        assertEquals(ReactionEffectsLevel.FULL, defaults.reactionEffects)
        assertEquals(defaults, defaults.normalized())

        assertEquals(listOf(0, 30, 60, 90, 120, 144, 165, 240), FRAME_RATE_CAP_OPTIONS)
        assertEquals(listOf(85, 100, 115, 130), INTERFACE_SCALE_OPTIONS)
        assertEquals(7, WindowSizePreset.entries.size)

        assertEquals(240, defaults.copy(frameRateCap = 240).normalized().frameRateCap)
        assertEquals(0, defaults.copy(frameRateCap = 241).normalized().frameRateCap)
        assertEquals(0, defaults.copy(frameRateCap = 75).normalized().frameRateCap)
        assertEquals(24, defaults.copy(refreshRateHz = 24).normalized().refreshRateHz)
        assertEquals(360, defaults.copy(refreshRateHz = 360).normalized().refreshRateHz)
        assertEquals(REFRESH_RATE_AUTO, defaults.copy(refreshRateHz = 23).normalized().refreshRateHz)
        assertEquals(REFRESH_RATE_AUTO, defaults.copy(refreshRateHz = 361).normalized().refreshRateHz)
        assertEquals(130, defaults.copy(interfaceScalePercent = 130).normalized().interfaceScalePercent)
        assertEquals(100, defaults.copy(interfaceScalePercent = 131).normalized().interfaceScalePercent)
        assertEquals(85, defaults.copy(interfaceScalePercent = 85).normalized().interfaceScalePercent)
        assertEquals(100, defaults.copy(interfaceScalePercent = 84).normalized().interfaceScalePercent)
    }

    @Test
    fun displayAdjustmentsAdmitOnlyListedOptionsAndTheRefreshRange() {
        FRAME_RATE_CAP_OPTIONS.forEach { fps -> assertEquals(fps, ProfilePreferenceAdjustment.SetFrameRateCap(fps).fps) }
        for (unlisted in listOf(-1, 1, 29, 59, 75, 239, 241, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { ProfilePreferenceAdjustment.SetFrameRateCap(unlisted) }
        }
        for (hz in listOf(REFRESH_RATE_AUTO, REFRESH_RATE_RANGE.first, 60, REFRESH_RATE_RANGE.last)) {
            assertEquals(hz, ProfilePreferenceAdjustment.SetRefreshRate(hz).hz)
        }
        for (outside in listOf(-1, 1, REFRESH_RATE_RANGE.first - 1, REFRESH_RATE_RANGE.last + 1, Int.MIN_VALUE)) {
            assertFailsWith<IllegalArgumentException> { ProfilePreferenceAdjustment.SetRefreshRate(outside) }
        }
        INTERFACE_SCALE_OPTIONS.forEach { percent ->
            assertEquals(percent, ProfilePreferenceAdjustment.SetInterfaceScale(percent).percent)
        }
        for (unlisted in listOf(0, 84, 86, 99, 101, 129, 131)) {
            assertFailsWith<IllegalArgumentException> { ProfilePreferenceAdjustment.SetInterfaceScale(unlisted) }
        }
        assertEquals(
            ProfilePreferenceAdjustment.SetKeyBinding(GameAction.APEX, BindableKey.F),
            ProfilePreferenceAdjustment.SetKeyBinding(GameAction.APEX, BindableKey.F),
        )
    }

    @Test
    fun permanentUpgradesStartAtZeroForEveryCharacterAndWeapon() {
        val upgrades = PermanentUpgrades()

        assertEquals(CoreShape.entries.size, upgrades.characterLevels.size)
        assertEquals(WeaponId.entries.size, upgrades.weaponLevels.size)
        assertTrue(upgrades.isDefault)
        assertEquals(0, upgrades.characterLevel(CoreShape.HEXAGON))
        assertEquals(0, upgrades.weaponLevel(WeaponId.entries.last()))
        assertEquals(upgrades, PlayerProfile().upgrades)

        val raised = PermanentUpgrades(
            characterLevels = List(CoreShape.entries.size) { if (it == CoreShape.STAR.ordinal) 3 else 0 },
            weaponLevels = List(WeaponId.entries.size) { if (it == 0) 10 else 0 },
        )
        assertEquals(3, raised.characterLevel(CoreShape.STAR))
        assertEquals(10, raised.weaponLevel(WeaponId.entries.first()))
        assertFalse(raised.isDefault)
        assertEquals(0, PermanentUpgrades(emptyList(), emptyList()).characterLevel(CoreShape.ORB))
    }

    @Test
    fun recentItemsAndAcquiredItemsDefaultToEmptyAndCountAsProgress() {
        assertTrue(PlayerCollection().recentItemIds.isEmpty())
        assertEquals(listOf(57, 12, 57), PlayerCollection(discoveredItemIds = emptySet(), recentItemIds = listOf(57, 12, 57)).recentItemIds)
        assertEquals(64, MAX_RECENT_ITEM_IDS)

        assertTrue(GameplayProgressUpdate().isEmpty)
        assertTrue(GameplayProgressUpdate().acquiredItemIds.isEmpty())
        assertFalse(GameplayProgressUpdate(discoveredItemIds = emptySet(), acquiredItemIds = listOf(3)).isEmpty)

        val collection = PlayerCollection(recentItemIds = immutableListOf(4, 9))
        val snapshot = GameplayProfileSnapshot(
            preferences = PlayerPreferences(),
            economy = PlayerEconomy(),
            loadout = PlayerLoadout(),
            matrix = MatrixProgress(),
            collection = collection,
            rebirthProgress = RebirthProgress(),
        )
        assertEquals(listOf(4, 9), snapshot.recentItemIds)
        assertEquals(PermanentUpgrades(), snapshot.upgrades)
        assertEquals(PermanentUpgrades(), LoadoutProfileSnapshot(PlayerEconomy(), PlayerLoadout()).upgrades)
    }
}
