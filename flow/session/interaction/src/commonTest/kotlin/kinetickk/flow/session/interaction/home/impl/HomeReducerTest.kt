// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.flow.session.interaction.audio.SessionAudioCue
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.interaction.TestCoreShapes
import kinetickk.flow.session.interaction.TestRebirthPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HomeReducerTest {
    @Test
    fun modelMapsOnlyTheAuthoritativeHomeProjection() {
        val projection = homeProjection(
            economy = PlayerEconomy(matter = 81, lifetimeMatter = 120),
            loadout = PlayerLoadout(
                coreShape = CoreShape.PRISM,
                selectedWeapon = WeaponId.FLUX_WAKE,
                unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR),
            ),
            collection = PlayerCollection(setOf(0, 399)),
            rebirth = RebirthProgress(level = 2, highestCleared = 1),
            canAdvanceRebirth = false,
        )

        val model = homeReducer().uiModel(projection)

        assertEquals(CoreShape.PRISM, model.coreShape)
        assertEquals(81, model.totalMatter)
        assertEquals(2, model.discoveredItemCount)
        assertEquals(2, model.unlockedWeaponCount)
        assertFalse(model.canRebirth)
        assertTrue(model.isCoreShapeUnlocked(CoreShape.SHARD))
    }

    @Test
    fun actionsEmitTypedProfileIntentOrOrderedShellEffects() {
        val reducer = homeReducer()

        val selection = reducer.reduce(HomeAction.SelectCoreShape(CoreShape.PRISM)).effects
        val emittedSelection = assertIs<HomeOutput.SelectCoreShape>(
            assertIs<HomeEffect.Emit>(selection[0]).output,
        )
        assertEquals(CoreShape.PRISM, emittedSelection.shape)
        assertEquals(SessionAudioCue.UI_CLICK, assertIs<HomeEffect.PlayAudio>(selection[1]).cue)

        val start = reducer.reduce(HomeAction.StartRun).effects
        assertEquals(SessionAudioCue.UI_CLICK, assertIs<HomeEffect.PlayAudio>(start[0]).cue)
        assertIs<HomeOutput.StartRun>(assertIs<HomeEffect.Emit>(start[1]).output)

        val settings = reducer.reduce(HomeAction.OpenSettings).effects
        assertIs<HomeOutput.OpenSettings>(assertIs<HomeEffect.Emit>(settings[1]).output)
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

private fun homeReducer(): HomeReducer = HomeReducer(
    coreShapes = TestCoreShapes,
    itemCount = 400,
    weaponCount = 12,
    rebirthPolicy = TestRebirthPolicy,
)

private fun homeProjection(
    economy: PlayerEconomy = PlayerEconomy(lifetimeMatter = 100),
    loadout: PlayerLoadout = PlayerLoadout(),
    collection: PlayerCollection = PlayerCollection(),
    rebirth: RebirthProgress = RebirthProgress(),
    canAdvanceRebirth: Boolean = false,
): HomeProgressProjection = HomeProgressProjection(
    instanceId = LOCAL_PROFILE_INSTANCE_ID,
    revision = ProfileRevision.ZERO,
    economy = economy,
    loadout = loadout,
    collection = collection,
    rebirthProgress = rebirth,
    canAdvanceRebirth = canAdvanceRebirth,
)
