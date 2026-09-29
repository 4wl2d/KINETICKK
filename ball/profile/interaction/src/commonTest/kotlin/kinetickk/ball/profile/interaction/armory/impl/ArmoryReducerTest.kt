// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.interaction.TestWeapons
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.foundation.collections.immutableSetOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArmoryReducerTest {
    private val reducer = ArmoryReducer(TestWeapons)

    // TestWeapons cost index * 100: FLUX_WAKE 0, MORNINGSTAR 100, PHASE_LATTICE 200, NULL_LANCE 300.
    private val model = ArmoryRenderModel(
        totalMatter = 200L,
        selectedWeapon = WeaponId.FLUX_WAKE,
        unlockedWeapons = immutableSetOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR),
        activeRunWeapon = null,
    )

    @Test
    fun statusFollowsTheLoadoutProjectionAndMatterBoundary() {
        fun status(id: WeaponId) = model.status(TestWeapons.first { it.id == id })

        assertEquals(ArmoryWeaponStatus.STARTER, status(WeaponId.FLUX_WAKE))
        assertEquals(ArmoryWeaponStatus.OWNED, status(WeaponId.MORNINGSTAR))
        // Exactly enough matter is affordable; one unit short is not.
        assertEquals(ArmoryWeaponStatus.AFFORDABLE, status(WeaponId.PHASE_LATTICE))
        assertEquals(ArmoryWeaponStatus.SHORT, status(WeaponId.NULL_LANCE))
        assertEquals(ArmoryWeaponStatus.SHORT, model.copy(totalMatter = 199L).status(TestWeapons[2]))
        assertFalse(ArmoryWeaponStatus.STARTER.actionEnabled)
        assertTrue(ArmoryWeaponStatus.OWNED.actionEnabled)
        assertTrue(ArmoryWeaponStatus.AFFORDABLE.actionEnabled)
        assertFalse(ArmoryWeaponStatus.SHORT.actionEnabled)
    }

    @Test
    fun firstActivationInspectsAndActivatingTheInspectedTileConfirms() {
        val start = ArmoryViewState(WeaponId.FLUX_WAKE)

        val inspect = reducer.reduce(start, model, ArmoryAction.Activate(WeaponId.PHASE_LATTICE))
        assertEquals(WeaponId.PHASE_LATTICE, inspect.state.inspected)
        assertTrue(inspect.effects.none { it is ArmoryEffect.PurchaseOrEquipWeapon })

        val confirm = reducer.reduce(inspect.state, model, ArmoryAction.Activate(WeaponId.PHASE_LATTICE))
        assertEquals(
            WeaponId.PHASE_LATTICE,
            assertIs<ArmoryEffect.PurchaseOrEquipWeapon>(confirm.effects.single()).id,
        )
    }

    @Test
    fun primaryActionEmitsOnlyTheTypedProfileIntentWhenAvailable() {
        val start = ArmoryViewState(WeaponId.FLUX_WAKE)

        val equip = reducer.reduce(start, model, ArmoryAction.Apply(WeaponId.MORNINGSTAR))
        assertEquals(WeaponId.MORNINGSTAR, assertIs<ArmoryEffect.PurchaseOrEquipWeapon>(equip.effects.single()).id)
        assertTrue(reducer.reduce(start, model, ArmoryAction.Apply(WeaponId.NULL_LANCE)).effects.isEmpty())
        assertTrue(reducer.reduce(start, model, ArmoryAction.Apply(WeaponId.FLUX_WAKE)).effects.isEmpty())
    }

    @Test
    fun inspectingIsLocalAndIgnoresRepeatsAndUnknownWeapons() {
        val start = ArmoryViewState(WeaponId.FLUX_WAKE)
        val single = ArmoryReducer(TestWeapons.take(1).let { kinetickk.foundation.collections.ImmutableList.copyOf(it) })

        val inspected = reducer.reduce(start, model, ArmoryAction.Inspect(WeaponId.ARC_COIL))
        assertEquals(WeaponId.ARC_COIL, inspected.state.inspected)
        assertEquals(ProfileAudioCue.UI_CLICK, assertIs<ArmoryEffect.PlayAudio>(inspected.effects.single()).cue)
        assertTrue(reducer.reduce(inspected.state, model, ArmoryAction.Inspect(WeaponId.ARC_COIL)).effects.isEmpty())
        assertEquals(start, single.reduce(start, model, ArmoryAction.Inspect(WeaponId.ARC_COIL)).state)
    }

    @Test
    fun pageStepsAndBackAreLocalPresentationEffects() {
        val start = ArmoryViewState(WeaponId.FLUX_WAKE)

        val next = reducer.reduce(start, model, ArmoryAction.NextPage).effects
        assertEquals(ProfileAudioCue.UI_CLICK, assertIs<ArmoryEffect.PlayAudio>(next[0]).cue)
        assertTrue(assertIs<ArmoryEffect.ScrollGrid>(next[1]).forward)
        assertFalse(assertIs<ArmoryEffect.ScrollGrid>(reducer.reduce(start, model, ArmoryAction.PreviousPage).effects[1]).forward)
        val back = reducer.reduce(start, model, ArmoryAction.Back).effects
        assertEquals(ProfileAudioCue.UI_CLICK, assertIs<ArmoryEffect.PlayAudio>(back[0]).cue)
        assertEquals(ArmoryOutput.Back, assertIs<ArmoryEffect.Emit>(back[1]).output)
    }

    @Test
    fun renderModelUsesOnlyTheAuthoritativeProjectionSnapshot() {
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
}
