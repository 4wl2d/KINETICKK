// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.interaction.TestWeapons
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ArmoryReducerTest {
    @Test
    fun pageSliceReturnsThreeForExactAndFirstNPlusOneInputs() {
        val exact = (0 until ARMORY_PAGE_SIZE).toList()
        val firstNPlusOne = exact + ARMORY_PAGE_SIZE

        assertEquals(exact, armoryPageSlice(exact, page = 0))
        assertEquals(exact, armoryPageSlice(firstNPlusOne, page = 0))
        assertEquals(listOf(ARMORY_PAGE_SIZE), armoryPageSlice(firstNPlusOne, page = 1))
    }

    @Test
    fun presentationClockAcceptsMaximumAndClampsNextRepresentableDelta() {
        val nextRepresentable = Float.fromBits(MAX_ARMORY_PRESENTATION_FRAME_DELTA_SECONDS.toBits() + 1)

        assertEquals(
            MAX_ARMORY_PRESENTATION_FRAME_DELTA_SECONDS,
            selectArmoryPresentationFrameDeltaSeconds(MAX_ARMORY_PRESENTATION_FRAME_DELTA_SECONDS),
        )
        assertEquals(
            MAX_ARMORY_PRESENTATION_FRAME_DELTA_SECONDS,
            selectArmoryPresentationFrameDeltaSeconds(nextRepresentable),
        )
    }

    @Test
    fun paginationIsLocalAndClamped() {
        val reducer = ArmoryReducer(TestWeapons)

        assertEquals(1, reducer.reduce(0, ArmoryAction.NextPage).page)
        assertEquals(reducer.maxPage, reducer.reduce(Int.MAX_VALUE, ArmoryAction.NextPage).page)
        assertEquals(0, reducer.reduce(0, ArmoryAction.PreviousPage).page)
        val backEffects = reducer.reduce(2, ArmoryAction.Back).effects
        assertEquals(ProfileAudioCue.UI_CLICK, assertIs<ArmoryEffect.PlayAudio>(backEffects[0]).cue)
        assertIs<ArmoryEffect.Emit>(backEffects[1])
    }

    @Test
    fun selectingWeaponEmitsOnlyTheTypedProfileIntent() {
        val reducer = ArmoryReducer(TestWeapons)

        val reduction = reducer.reduce(0, ArmoryAction.SelectWeapon(WeaponId.MORNINGSTAR))

        assertEquals(
            WeaponId.MORNINGSTAR,
            assertIs<ArmoryEffect.PurchaseOrEquipWeapon>(reduction.effects.single()).id,
        )
    }

    @Test
    fun renderModelUsesOnlyTheAuthoritativeProjectionSnapshot() {
        val reducer = ArmoryReducer(TestWeapons)
        val snapshot = LoadoutProfileSnapshot(
            economy = PlayerEconomy(matter = 42L, lifetimeMatter = 100L),
            loadout = PlayerLoadout(
                coreShape = CoreShape.PRISM,
                selectedWeapon = WeaponId.MORNINGSTAR,
                unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR),
            ),
        )

        val model = reducer.renderModel(snapshot, activeRunWeapon = WeaponId.FLUX_WAKE)

        assertEquals(42L, model.totalMatter)
        assertEquals(WeaponId.MORNINGSTAR, model.selectedWeapon)
        assertEquals(snapshot.loadout.unlockedWeapons, model.unlockedWeapons)
        assertEquals(WeaponId.FLUX_WAKE, model.activeRunWeapon)
    }

    @Test
    fun pointerMappingKeepsThreeCardAndFooterGeometry() {
        val viewport = ArmoryViewport(1_280f, 720f, 1f)
        val firstCardCenter = (1_280f - (245f * 3f + 16f * 2f)) * 0.5f + 122f

        val first = assertIs<ArmoryAction.SelectWeapon>(
            resolveArmoryPress(viewport, TestWeapons, 0, firstCardCenter, 300f),
        )
        assertEquals(TestWeapons.first().id, first.id)
        assertIs<ArmoryAction.Back>(resolveArmoryPress(viewport, TestWeapons, 0, 250f, 690f))
        assertIs<ArmoryAction.NextPage>(resolveArmoryPress(viewport, TestWeapons, 0, 1_050f, 690f))
        assertEquals((TestWeapons.size - 1) / ARMORY_PAGE_SIZE, ArmoryReducer(TestWeapons).maxPage)

        val reversed = TestWeapons.reversed().toImmutableList()
        val reorderedFirst = assertIs<ArmoryAction.SelectWeapon>(
            resolveArmoryPress(viewport, reversed, 0, firstCardCenter, 300f),
        )
        assertEquals(reversed.first().id, reorderedFirst.id)
    }
}
