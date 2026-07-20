// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.home.impl

import kinetickk.core.content.CoreShape
import kinetickk.core.content.WeaponId
import kinetickk.core.profile.api.CollectionCapability
import kinetickk.core.profile.api.LoadoutCapability
import kinetickk.core.profile.api.LoadoutProfileSnapshot
import kinetickk.core.profile.api.PlayerCollection
import kinetickk.core.profile.api.PlayerEconomy
import kinetickk.core.profile.api.PlayerLoadout
import kinetickk.core.profile.api.ProfileMutationResult
import kinetickk.core.profile.api.ProfilePersistResult
import kinetickk.core.profile.api.RebirthCapability
import kinetickk.core.profile.api.RebirthProfileSnapshot
import kinetickk.core.profile.api.RebirthProgress
import kinetickk.feature.home.api.HomeOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeReducerTest {
    @Test
    fun modelCombinesOnlyNarrowProfileSnapshots() {
        val profile = FakeHomeProfile(
            economy = PlayerEconomy(matter = 81, lifetimeMatter = 120),
            loadout = PlayerLoadout(
                coreShape = CoreShape.PRISM,
                selectedWeapon = WeaponId.FLUX_WAKE,
                unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR),
            ),
            collection = PlayerCollection(setOf(0, 399)),
            rebirth = RebirthProgress(level = 2, highestCleared = 1),
        )

        val model = HomeReducer(profile, profile, profile).uiModel()

        assertEquals(CoreShape.PRISM, model.coreShape)
        assertEquals(81, model.totalMatter)
        assertEquals(2, model.discoveredItemCount)
        assertEquals(2, model.unlockedWeaponCount)
        assertFalse(model.canRebirth)
        assertTrue(model.isCoreShapeUnlocked(CoreShape.SHARD))
    }

    @Test
    fun actionsSelectLoadoutOrEmitShellOutputs() {
        val profile = FakeHomeProfile()
        val reducer = HomeReducer(profile, profile, profile)

        assertNull(reducer.reduce(HomeAction.SelectCoreShape(CoreShape.PRISM)))
        assertEquals(CoreShape.PRISM, profile.loadout.coreShape)
        assertIs<HomeOutput.StartRun>(reducer.reduce(HomeAction.StartRun))
        assertIs<HomeOutput.OpenSettings>(reducer.reduce(HomeAction.OpenSettings))
    }

    @Test
    fun pointerGeometryPreservesCoreStartAndRouteHitboxes() {
        val viewport = HomeViewport(1_280f, 720f, 1f)

        assertEquals(
            HomeAction.SelectCoreShape(CoreShape.PRISM),
            resolveHomePress(viewport, 640f, 720f * 0.62f),
        )
        assertEquals(HomeAction.StartRun, resolveHomePress(viewport, 640f, 720f * 0.78f))
        assertEquals(HomeAction.OpenRebirth, resolveHomePress(viewport, 640f, 720f * 0.9f))
    }
}

private class FakeHomeProfile(
    var economy: PlayerEconomy = PlayerEconomy(lifetimeMatter = 100),
    var loadout: PlayerLoadout = PlayerLoadout(),
    var collection: PlayerCollection = PlayerCollection(),
    var rebirth: RebirthProgress = RebirthProgress(),
) : LoadoutCapability, CollectionCapability, RebirthCapability {
    override fun loadoutSnapshot(): LoadoutProfileSnapshot = LoadoutProfileSnapshot(economy, loadout)

    override fun selectCoreShape(shape: CoreShape): ProfileMutationResult {
        loadout = loadout.copy(coreShape = shape)
        return applied()
    }

    override fun purchaseOrEquipWeapon(id: WeaponId): ProfileMutationResult = applied()

    override fun collectionSnapshot(): PlayerCollection = collection

    override fun rebirthSnapshot(): RebirthProfileSnapshot = RebirthProfileSnapshot(rebirth)

    override fun advanceRebirth(): ProfileMutationResult = applied()

    private fun applied(): ProfileMutationResult.Applied = ProfileMutationResult.Applied(
        persistence = ProfilePersistResult.Persisted,
    )
}
