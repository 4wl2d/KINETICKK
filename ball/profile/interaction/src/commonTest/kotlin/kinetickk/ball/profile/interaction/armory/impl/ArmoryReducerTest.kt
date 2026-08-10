// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import kinetickk.resource.audio.api.AudioCue
import kinetickk.ball.content.api.CoreShape
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.ball.content.api.WeaponCatalog
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.LoadoutCapability
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.ProfileMutationResult
import kinetickk.ball.profile.api.ProfileMutationRejection
import kinetickk.ball.profile.api.ProfilePersistResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArmoryReducerTest {
    @Test
    fun paginationIsLocalAndClamped() {
        val reducer = ArmoryReducer(FakeLoadout())

        assertEquals(1, reducer.reduce(0, ArmoryAction.NextPage).page)
        assertEquals(reducer.maxPage, reducer.reduce(Int.MAX_VALUE, ArmoryAction.NextPage).page)
        assertEquals(0, reducer.reduce(0, ArmoryAction.PreviousPage).page)
        assertTrue(reducer.reduce(2, ArmoryAction.Back).close)
    }

    @Test
    fun selectingWeaponUsesOnlyLoadoutCapability() {
        val capability = FakeLoadout()
        val reducer = ArmoryReducer(capability)

        val reduction = reducer.reduce(0, ArmoryAction.SelectWeapon(WeaponId.MORNINGSTAR))

        assertTrue(reduction.profileChanged)
        assertEquals(AudioCue.PURCHASE, reduction.feedbackCue)
        assertEquals(WeaponId.MORNINGSTAR, capability.loadout.selectedWeapon)
        assertFalse(reduction.close)
    }

    @Test
    fun rejectedSelectionDoesNotReportAProfileChangeOrPurchaseCue() {
        val capability = FakeLoadout(rejectSelection = true)
        val reducer = ArmoryReducer(capability)

        val reduction = reducer.reduce(0, ArmoryAction.SelectWeapon(WeaponId.MORNINGSTAR))

        assertFalse(reduction.profileChanged)
        assertEquals(null, reduction.feedbackCue)
        assertEquals(WeaponId.FLUX_WAKE, capability.loadout.selectedWeapon)
    }

    @Test
    fun pointerMappingKeepsThreeCardAndFooterGeometry() {
        val viewport = ArmoryViewport(1_280f, 720f, 1f)
        val firstCardCenter = (1_280f - (245f * 3f + 16f * 2f)) * 0.5f + 122f

        assertIs<ArmoryAction.SelectWeapon>(resolveArmoryPress(viewport, 0, firstCardCenter, 300f))
        assertIs<ArmoryAction.Back>(resolveArmoryPress(viewport, 0, 250f, 690f))
        assertIs<ArmoryAction.NextPage>(resolveArmoryPress(viewport, 0, 1_050f, 690f))
        assertEquals((WeaponCatalog.all.size - 1) / ARMORY_PAGE_SIZE, ArmoryReducer(FakeLoadout()).maxPage)
    }
}

private class FakeLoadout(
    private val rejectSelection: Boolean = false,
) : LoadoutCapability {
    var economy = PlayerEconomy(matter = 100_000, lifetimeMatter = 100_000)
    var loadout = PlayerLoadout(
        coreShape = CoreShape.ORB,
        selectedWeapon = WeaponId.FLUX_WAKE,
        unlockedWeapons = setOf(WeaponId.FLUX_WAKE),
    )

    override fun loadoutSnapshot(): LoadoutProfileSnapshot = LoadoutProfileSnapshot(economy, loadout)

    override fun selectCoreShape(shape: CoreShape): ProfileMutationResult = applied()

    override fun purchaseOrEquipWeapon(id: WeaponId): ProfileMutationResult {
        if (rejectSelection) {
            return ProfileMutationResult.Rejected(ProfileMutationRejection.INSUFFICIENT_MATTER)
        }
        loadout = loadout.copy(
            selectedWeapon = id,
            unlockedWeapons = (loadout.unlockedWeapons + id).toImmutableSet(),
        )
        return applied()
    }

    private fun applied() = ProfileMutationResult.Applied(
        ProfilePersistResult.Persisted,
    )
}
