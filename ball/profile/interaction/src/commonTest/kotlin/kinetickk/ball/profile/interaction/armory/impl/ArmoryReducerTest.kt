// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.interaction.TestWeapons
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
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
        rebirthRank = 0,
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
    fun aRebirthGatedWeaponStaysDisabledBelowItsRankWhateverTheMatter() {
        // Glacier Bell's shipped gate: Reborn 1, for 700 Matter.
        val weapons = TestWeapons.map { if (it.id == WeaponId.GLACIER_BELL) it.gatedAt(minimumRebirth = 1) else it }.toImmutableList()
        val gatedReducer = ArmoryReducer(weapons)
        val bell = weapons.first { it.id == WeaponId.GLACIER_BELL }
        val start = ArmoryViewState(WeaponId.GLACIER_BELL)
        val rich = model.copy(totalMatter = 10_000L)

        assertEquals(ArmoryWeaponStatus.REBIRTH_LOCKED, rich.status(bell))
        assertFalse(ArmoryWeaponStatus.REBIRTH_LOCKED.actionEnabled)
        // A press is never sent to the Profile: it names the Rebirth gate instead of doing nothing.
        for (action in listOf(ArmoryAction.Apply(WeaponId.GLACIER_BELL), ArmoryAction.Activate(WeaponId.GLACIER_BELL))) {
            val refused = gatedReducer.reduce(start, rich, action)
            assertTrue(refused.effects.none { it is ArmoryEffect.PurchaseOrEquipWeapon }, "$action")
            assertEquals(ProfileAudioCue.REFUSED, assertIs<ArmoryEffect.PlayAudio>(refused.effects.single()).cue)
            assertEquals(ArmoryNotice(WeaponId.GLACIER_BELL, ArmoryRefusal.REBIRTH_LOCKED), refused.state.inspectedNotice)
        }

        // At exactly its rank the weapon is an ordinary unlock again.
        val reborn = rich.copy(rebirthRank = 1)
        assertEquals(ArmoryWeaponStatus.AFFORDABLE, reborn.status(bell))
        assertEquals(
            WeaponId.GLACIER_BELL,
            assertIs<ArmoryEffect.PurchaseOrEquipWeapon>(gatedReducer.reduce(start, reborn, ArmoryAction.Apply(WeaponId.GLACIER_BELL)).effects.single()).id,
        )
        assertEquals(ArmoryWeaponStatus.SHORT, reborn.copy(totalMatter = 0L).status(bell))
        // An owned weapon is never gated: it can still be set as the starter.
        assertEquals(ArmoryWeaponStatus.OWNED, rich.copy(unlockedWeapons = immutableSetOf(WeaponId.FLUX_WAKE, WeaponId.GLACIER_BELL)).status(bell))
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
        assertEquals(null, equip.state.notice)
        // One Matter short: no intent, the shortfall is named.
        val short = reducer.reduce(start, model, ArmoryAction.Apply(WeaponId.NULL_LANCE))
        assertTrue(short.effects.none { it is ArmoryEffect.PurchaseOrEquipWeapon })
        assertEquals(ArmoryNotice(WeaponId.NULL_LANCE, ArmoryRefusal.INSUFFICIENT_MATTER), short.state.inspectedNotice)
        // The starter has nothing to do and nothing to explain.
        val starter = reducer.reduce(start, model, ArmoryAction.Apply(WeaponId.FLUX_WAKE))
        assertTrue(starter.effects.isEmpty())
        assertEquals(null, starter.state.notice)
    }

    @Test
    fun aRefusalStaysWithItsWeaponUntilAnotherIsInspectedOrTheProfileAccepts() {
        val refused = reducer.reduce(ArmoryViewState(WeaponId.FLUX_WAKE), model, ArmoryAction.Apply(WeaponId.NULL_LANCE)).state
        assertEquals(ArmoryRefusal.INSUFFICIENT_MATTER, refused.inspectedNotice?.refusal)
        // Inspecting another weapon drops it; inspecting the same one keeps it.
        assertEquals(refused, reducer.reduce(refused, model, ArmoryAction.Inspect(WeaponId.NULL_LANCE)).state)
        assertEquals(null, reducer.reduce(refused, model, ArmoryAction.Inspect(WeaponId.ARC_COIL)).state.notice)

        // The Profile's answers: its rejection reasons map onto the shown refusal; an acceptance clears it.
        val pending = ArmoryViewState(WeaponId.PHASE_LATTICE)
        assertEquals(ArmoryNotice(WeaponId.PHASE_LATTICE, ArmoryRefusal.INSUFFICIENT_MATTER),
            reducer.answered(pending, WeaponId.PHASE_LATTICE, ProfileRejection.InsufficientMatter).notice)
        assertEquals(ArmoryNotice(WeaponId.PHASE_LATTICE, ArmoryRefusal.REBIRTH_LOCKED),
            reducer.answered(pending, WeaponId.PHASE_LATTICE, ProfileRejection.WeaponRebirthLocked).notice)
        assertEquals(ArmoryNotice(WeaponId.PHASE_LATTICE, ArmoryRefusal.REFUSED),
            reducer.answered(pending, WeaponId.PHASE_LATTICE, ProfileRejection.BootstrapNotReady).notice)
        assertEquals(null, reducer.answered(pending, WeaponId.PHASE_LATTICE, ProfileRejection.NoChange).notice)
        assertEquals(null, reducer.answered(refused, WeaponId.NULL_LANCE, rejection = null).notice)
    }

    @Test
    fun synergyReadsTheSelectedCharacterAndTheHeirloomCandidatesCarryingTheKey() {
        val weapon = TestWeapons.first { it.id == WeaponId.MORNINGSTAR }
        val shapes = defaultCoreShapeDefinitions()
        val relics = listOf(
            keyedRelic(RelicId.PERPETUAL_VECTOR, RelicKey.MOTION, RelicKey.HEAT),
            keyedRelic(RelicId.ECHO_CHAMBER, RelicKey.ECHO, RelicKey.MOTION),
            keyedRelic(RelicId.CINDER_CROWN, RelicKey.HEAT, RelicKey.LIFE),
        )
        val withHeirlooms = model.copy(
            character = weapon.signature,
            heirloomRelics = immutableListOf(RelicId.CINDER_CROWN, RelicId.ECHO_CHAMBER, RelicId.PERPETUAL_VECTOR, RelicId.ECHO_CHAMBER),
        )
        val synergy = withHeirlooms.synergy(weapon, shapes, relics)
        assertEquals(weapon.signature, synergy.signature?.id)
        assertTrue(synergy.signatureActive)
        // Candidates carrying the weapon's key (Motion), in heirloom slot order and once each.
        assertEquals(listOf(RelicId.ECHO_CHAMBER, RelicId.PERPETUAL_VECTOR), synergy.harmonizing.map { it.id })

        val other = CoreShape.entries.first { it != weapon.signature }
        val plain = model.copy(character = other).synergy(weapon, shapes, relics)
        assertFalse(plain.signatureActive)
        assertTrue(plain.harmonizing.isEmpty())
        // An unknown candidate id (not in the catalog) is ignored rather than shown.
        assertTrue(model.copy(heirloomRelics = immutableListOf(RelicId.AGONY_SCEPTER)).synergy(weapon, shapes, relics).harmonizing.isEmpty())
    }

    @Test
    fun masteryFormsSitAtTheMilestonesAfterTheBase() {
        assertEquals(listOf(3, 6, 10), armoryFormLevels(WeaponMastery.entries))
        assertEquals(listOf(3, 6, 10), armoryFormLevels(emptyList()))
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
            rebirthRank = 3,
        )

        val model = reducer.renderModel(snapshot, activeRunWeapon = WeaponId.FLUX_WAKE)

        assertEquals(42L, model.totalMatter)
        assertEquals(WeaponId.MORNINGSTAR, model.selectedWeapon)
        assertEquals(snapshot.loadout.unlockedWeapons, model.unlockedWeapons)
        assertEquals(WeaponId.FLUX_WAKE, model.activeRunWeapon)
        assertEquals(3, model.rebirthRank)
        assertEquals(CoreShape.PRISM, model.character)
        assertTrue(model.heirloomRelics.isEmpty())
        val heirlooms = immutableListOf(RelicId.ECHO_CHAMBER)
        assertEquals(heirlooms, reducer.renderModel(snapshot, activeRunWeapon = null, heirloomRelics = heirlooms).heirloomRelics)
    }
}

private fun WeaponDefinition.gatedAt(minimumRebirth: Int): WeaponDefinition = WeaponDefinition(
    id = id,
    name = name,
    description = description,
    family = family,
    element = element,
    tags = tags,
    key = key,
    signature = signature,
    scaling = scaling,
    cadence = cadence,
    permanentUnlockCost = permanentUnlockCost,
    minimumRebirth = minimumRebirth,
    masteryForms = masteryForms,
    signatureText = signatureText,
)

private fun keyedRelic(id: RelicId, vararg keys: RelicKey): RelicDefinition = RelicDefinition(
    id = id,
    name = "Keyed ${id.name}",
    aspect = RelicAspect.KINETIC,
    keys = keys.toList().toImmutableList(),
    attunedTags = immutableListOf(),
    upside = "Keyed upside",
    liability = "Keyed liability",
)
