// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.UpgradePolicy
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileUpgradeTest {
    @Test
    fun refitChargesTheDoublingPriceAtTheExactBalanceAndRejectsOneMatterShort() {
        // Orb's Refit base is 50, so level 3 → 4 costs 50 × 2³.
        val price = 400L
        val exact = state(matter = price, lifetime = 9_000L, characters = mapOf(CoreShape.ORB to 3))

        val bought = refit(exact, CoreShape.ORB).acceptedProfile()
        assertEquals(4, bought.upgrades.characterLevel(CoreShape.ORB))
        assertEquals(PlayerEconomy(matter = 0L, lifetimeMatter = 9_000L), bought.economy)
        assertEquals(exact.profile.upgrades.weaponLevels, bought.upgrades.weaponLevels)

        val short = state(matter = price - 1L, characters = mapOf(CoreShape.ORB to 3))
        assertEquals(ProfileRejection.InsufficientMatter, refit(short, CoreShape.ORB).rejection())
    }

    @Test
    fun honingUsesTheWeaponBaseAndIsSoldOnlyForOwnedWeapons() {
        // Glacier Bell is gated at Rebirth 1, so its Honing base is round5(35 × 1.2) = 40.
        assertEquals(40, TestProfilePolicy.weapon(WeaponId.GLACIER_BELL).upgradeBaseCost)
        val owned = state(
            matter = 40L,
            unlocked = setOf(WeaponId.FLUX_WAKE, WeaponId.GLACIER_BELL),
        )
        val honed = hone(owned, WeaponId.GLACIER_BELL).acceptedProfile()
        assertEquals(1, honed.upgrades.weaponLevel(WeaponId.GLACIER_BELL))
        assertEquals(0L, honed.economy.matter)
        // Honing does not equip: the selected weapon stays as it was.
        assertEquals(WeaponId.FLUX_WAKE, honed.loadout.selectedWeapon)

        val rich = state(matter = 1_000_000L)
        assertEquals(ProfileRejection.WeaponNotOwned, hone(rich, WeaponId.GLACIER_BELL).rejection())
        assertEquals(35L, rich.profile.economy.matter - hone(rich, WeaponId.FLUX_WAKE).acceptedProfile().economy.matter)
    }

    @Test
    fun theTenthLevelIsAcceptedAndTheEleventhIsRejectedAtMaximumLevel() {
        val maxLevel = TestProfilePolicy.upgrades.maxLevel
        assertEquals(10, maxLevel)
        val nine = state(
            matter = 1_000_000L,
            characters = mapOf(CoreShape.ORB to maxLevel - 1),
            weapons = mapOf(WeaponId.FLUX_WAKE to maxLevel - 1),
        )

        val refitTen = refit(nine, CoreShape.ORB).acceptedProfile()
        assertEquals(maxLevel, refitTen.upgrades.characterLevel(CoreShape.ORB))
        assertEquals(1_000_000L - 50L * 512L, refitTen.economy.matter)
        val honedTen = hone(nine, WeaponId.FLUX_WAKE).acceptedProfile()
        assertEquals(maxLevel, honedTen.upgrades.weaponLevel(WeaponId.FLUX_WAKE))
        assertEquals(1_000_000L - 35L * 512L, honedTen.economy.matter)

        val ten = state(
            matter = 1_000_000L,
            characters = mapOf(CoreShape.ORB to maxLevel),
            weapons = mapOf(WeaponId.FLUX_WAKE to maxLevel),
        )
        assertEquals(ProfileRejection.UpgradeMaxLevel, refit(ten, CoreShape.ORB).rejection())
        assertEquals(ProfileRejection.UpgradeMaxLevel, hone(ten, WeaponId.FLUX_WAKE).rejection())
    }

    @Test
    fun ownershipIsCheckedBeforeTheMaximumAndTheMaximumBeforeMatter() {
        // Prism unlocks with elite kills; a fresh profile has none. Every later check would fail too.
        val broke = state(
            matter = 0L,
            characters = mapOf(CoreShape.PRISM to 10, CoreShape.ORB to 10),
            weapons = mapOf(WeaponId.MORNINGSTAR to 10, WeaponId.FLUX_WAKE to 10),
        )
        assertEquals(ProfileRejection.CoreShapeLocked, refit(broke, CoreShape.PRISM).rejection())
        assertEquals(ProfileRejection.WeaponNotOwned, hone(broke, WeaponId.MORNINGSTAR).rejection())
        assertEquals(ProfileRejection.UpgradeMaxLevel, refit(broke, CoreShape.ORB).rejection())
        assertEquals(ProfileRejection.UpgradeMaxLevel, hone(broke, WeaponId.FLUX_WAKE).rejection())

        // A character outside the captured policy is not unlocked for this Profile.
        val narrow = TestProfilePolicy.copy(
            coreShapes = TestProfilePolicy.coreShapes.filter { it.id == CoreShape.ORB }.toImmutableList(),
        )
        val narrowState = state(matter = 1_000L, policy = narrow)
        assertEquals(ProfileRejection.CoreShapeLocked, refit(narrowState, CoreShape.HEXAGON).rejection())
    }

    @Test
    fun anAcceptedUpgradePersistsOneSnapshotAndLeavesThePreviousStateUntouched() {
        val before = state(matter = 500L)
        val beforeProfile = before.profile

        val frame = refit(before, CoreShape.ORB).acceptedFrame()

        assertEquals(beforeProfile, before.profile)
        assertEquals(0, before.profile.upgrades.characterLevel(CoreShape.ORB))
        assertEquals(ProfileRevision(before.revision.value + 1L), frame.nextState.revision)
        val persist = assertIs<ProfileOutput.PersistSnapshot>(frame.outputs.single())
        assertEquals(frame.nextState.profile, persist.snapshot.profile)
        assertIs<ProfilePersistenceStatus.Pending>(frame.nextState.persistence)
        assertEquals(frame, refit(before, CoreShape.ORB).acceptedFrame())
    }

    @Test
    fun boughtLevelsReachTheLoadoutProjectionAndTheRunBootstrap() {
        val frame = hone(refit(state(matter = 1_000L), CoreShape.ORB).acceptedFrame().settled(), WeaponId.FLUX_WAKE)
            .acceptedFrame()
        val state = frame.settled()
        val expected = PermanentUpgrades(
            characterLevels = CoreShape.entries.map { if (it == CoreShape.ORB) 1 else 0 },
            weaponLevels = WeaponId.entries.map { if (it == WeaponId.FLUX_WAKE) 1 else 0 },
        )

        assertEquals(expected, ProfileNucleus.query(state, ProfileQuery.GetLoadout).snapshot.upgrades)
        val bootstrap = ProfileNucleus.query(state, ProfileQuery.GetRunBootstrap).result
        assertEquals(expected, assertIs<ProfileRunBootstrapResult.Ready>(bootstrap).snapshot.upgrades)
        assertEquals(1_000L - 50L - 35L, state.profile.economy.matter)
    }

    @Test
    fun loadingClampsLevelsAboveALoweredMaximumAndRefundsTheirPrice() {
        val lowered = TestProfilePolicy.copy(upgrades = UpgradePolicy(maxLevel = 4))
        val profile = defaultPlayerProfile(lowered).copy(
            economy = PlayerEconomy(matter = 100L, lifetimeMatter = 100L),
            upgrades = upgrades(
                characters = mapOf(CoreShape.ORB to 6, CoreShape.HEXAGON to 3),
                weapons = mapOf(WeaponId.FLUX_WAKE to 5),
            ),
        )

        val loaded = loaded(profile, lowered)

        assertEquals(4, loaded.profile.upgrades.characterLevel(CoreShape.ORB))
        assertEquals(3, loaded.profile.upgrades.characterLevel(CoreShape.HEXAGON))
        assertEquals(4, loaded.profile.upgrades.weaponLevel(WeaponId.FLUX_WAKE))
        // Orb levels 5 and 6 cost 50 × (16 + 32); Flux Wake level 5 cost 35 × 16.
        val refund = 50L * (16L + 32L) + 35L * 16L
        assertEquals(PlayerEconomy(100L + refund, 100L + refund), loaded.profile.economy)
        assertEquals(ProfileRevision(4L), loaded.revision)
        assertIs<ProfilePersistenceStatus.Persisted>(loaded.persistence)
    }

    @Test
    fun loadingKeepsLevelsOfLockedCharactersAndUnownedWeapons() {
        // Gates are purchase rules: a rebalance that relocks a character or a missing weapon never wipes levels.
        val profile = defaultPlayerProfile(TestProfilePolicy).copy(
            economy = PlayerEconomy(matter = 5L, lifetimeMatter = 9_000L),
            upgrades = upgrades(
                characters = mapOf(CoreShape.HEXAGON to 10),
                weapons = mapOf(WeaponId.RESONANCE_DRUM to 7),
            ),
        )

        val loaded = loaded(profile)

        assertEquals(profile, loaded.profile)
        assertEquals(ProfileRevision(4L), loaded.revision)
    }

    @Test
    fun malformedUpgradeListsFallBackToTheDefaultProfile() {
        val default = defaultPlayerProfile(TestProfilePolicy)
        val malformed = listOf(
            PermanentUpgrades(characterLevels = List(CoreShape.entries.size - 1) { 0 }, weaponLevels = List(WeaponId.entries.size) { 0 }),
            PermanentUpgrades(characterLevels = List(CoreShape.entries.size) { 0 }, weaponLevels = List(WeaponId.entries.size + 1) { 0 }),
            upgrades(characters = mapOf(CoreShape.ORB to -1)),
            upgrades(weapons = mapOf(WeaponId.FLUX_WAKE to -1)),
        )

        malformed.forEach { upgrades ->
            val loaded = loaded(default.copy(economy = PlayerEconomy(10L, 10L), upgrades = upgrades))
            assertEquals(default, loaded.profile, upgrades.toString())
            assertEquals(ProfileRevision(1L), loaded.revision)
        }
    }

    private fun upgrades(
        characters: Map<CoreShape, Int> = emptyMap(),
        weapons: Map<WeaponId, Int> = emptyMap(),
    ): PermanentUpgrades = PermanentUpgrades(
        characterLevels = CoreShape.entries.map { characters[it] ?: 0 },
        weaponLevels = WeaponId.entries.map { weapons[it] ?: 0 },
    )

    private fun state(
        matter: Long,
        lifetime: Long = matter,
        characters: Map<CoreShape, Int> = emptyMap(),
        weapons: Map<WeaponId, Int> = emptyMap(),
        unlocked: Set<WeaponId> = setOf(WeaponId.FLUX_WAKE),
        policy: ProfilePolicySnapshot = TestProfilePolicy,
    ): ProfileState {
        val initial = ProfileState.initial(policy, ProfileSnapshotReadResult.Observed(null))
        return initial.copy(
            profile = defaultPlayerProfile(policy).copy(
                economy = PlayerEconomy(matter = matter, lifetimeMatter = lifetime),
                loadout = PlayerLoadout(CoreShape.ORB, WeaponId.FLUX_WAKE, unlocked),
                upgrades = upgrades(characters, weapons),
            ),
        )
    }

    private fun loaded(profile: PlayerProfile, policy: ProfilePolicySnapshot = TestProfilePolicy): ProfileState =
        ProfileState.initial(policy, ProfileSnapshotReadResult.Observed(ProfileSnapshot(ProfileRevision(3L), profile)))

    private fun refit(state: ProfileState, shape: CoreShape): ProfileDecision =
        ProfileNucleus.decide(state, ProfileNucleusPulse.Intent(ProfilePulse.PurchaseCharacterUpgrade(shape)))

    private fun hone(state: ProfileState, id: WeaponId): ProfileDecision =
        ProfileNucleus.decide(state, ProfileNucleusPulse.Intent(ProfilePulse.PurchaseWeaponUpgrade(id)))

    private fun ProfileAcceptedFrame.settled(): ProfileState = nextState.copy(
        persistence = ProfilePersistenceStatus.Persisted(nextState.revision),
    )

    private fun ProfileDecision.acceptedFrame(): ProfileAcceptedFrame = assertIs<ProfileDecision.Accepted>(this).frame

    private fun ProfileDecision.acceptedProfile(): PlayerProfile = acceptedFrame().nextState.profile

    private fun ProfileDecision.rejection(): ProfileRejection = assertIs<ProfileDecision.Rejected>(this).reason
}
