// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.impl.ProfileComponent
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.ball.profile.impl.createProfileComponent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineAcceptance
import kinetickk.foundation.dispatch.InlineReply
import kinetickk.foundation.dispatch.call
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Refit and Honing across the Ball boundary (Profile buys and stores them, Gameplay applies them): levels bought
 * with Matter reach the next run's bootstrap snapshot, and the run starts with the Refit integrity and reports both
 * levels in its Build summary.
 */
class PermanentUpgradeRunBootstrapTest {
    @Test
    fun aRefitAndAHoningBoughtWithMatterReachTheNextRun() {
        val catalog = createContentCatalog()
        val profile = createProfileComponent(UpgradePersistence(), catalog.profilePolicy())
        bank(profile, 10_000L)
        repeat(2) { assertIs<ProfileAcceptance.Accepted>(profile.accept(ProfilePulse.PurchaseCharacterUpgrade(CoreShape.ORB))) }
        repeat(3) { assertIs<ProfileAcceptance.Accepted>(profile.accept(ProfilePulse.PurchaseWeaponUpgrade(WeaponId.FLUX_WAKE))) }

        val snapshot = assertIs<ProfileRunBootstrapResult.Ready>(profile.query(ProfileQuery.GetRunBootstrap).result).snapshot
        assertEquals(CoreShape.ORB, snapshot.loadout.coreShape)
        assertEquals(2, snapshot.upgrades.characterLevel(CoreShape.ORB))
        assertEquals(3, snapshot.upgrades.weaponLevel(WeaponId.FLUX_WAKE))

        val content = catalog.gameplayContent()
        val upgraded = start(content, snapshot)
        val plain = start(content, snapshot.copy(upgrades = PermanentUpgrades()))
        val upgradedModel = requireNotNull(GameplayNucleus.renderSnapshot(upgraded).renderModel)
        val plainModel = requireNotNull(GameplayNucleus.renderSnapshot(plain).renderModel)
        assertEquals(plainModel.maxHp + 2 * content.upgrades.characterIntegrityPerLevel, upgradedModel.maxHp, 1e-4f)
        val summary = GameplayNucleus.query(upgraded, GameplayQuery.GetBuildSummary)
        assertEquals(2, summary.characterUpgradeLevel)
        assertEquals(3, summary.weaponUpgradeLevel)
        assertEquals("Refit 2 · Honing 3", summary.upgrades)
        assertTrue(GameplayNucleus.query(plain, GameplayQuery.GetBuildSummary).weaponUpgradeLevel == 0)
    }

    private fun start(content: kinetickk.ball.content.api.GameplayContentSnapshot, profile: GameplayProfileSnapshot): GameplayState =
        assertIs<GameplayDecision.Accepted>(GameplayNucleus.decide(
            GameplayState.initial(RunId(1), content),
            GameplayNucleusPulse.StartRun,
            GameplayContext(start = GameplayStartContext.Ready(GameplayStartInputs(content, profile, seed = 11))),
        )).frame.nextState

    /** Banks [matter] through the Profile's accepted gameplay-progress path (as a finished run does). */
    private fun bank(profile: ProfileComponent, matter: Long) {
        val call = InlineAcceptance(BoundedCompletionDeque<Boolean>(1))
        var applied = false
        call.dispatch {
            call.acceptAndDrain(rootItem = false, rootFrame = immutableListOf(Unit),
                outputs = { it }, acceptFrame = { _, _ -> },
                decideCompletion = { accepted -> applied = accepted; immutableListOf<Unit>() },
                execute = { _, _ -> call.call(
                    invoke = { reply: InlineReply<ProfileProgressApplied, ProfileRefusal> ->
                        profile.applyGameplayProgress(GameplayProgressUpdate(bankedMatter = matter), reply)
                    },
                    acceptedInput = { true }, refusedInput = { false },
                ) })
        }
        assertTrue(applied, "Profile banked the Matter")
        assertEquals(matter, profile.query(ProfileQuery.GetHomeProgress).economy.matter)
    }
}

private class UpgradePersistence : ProfilePersistenceCapability {
    private var payload: String? = null
    override fun readSnapshot() = ProfilePersistenceReadResult.Observed(payload)
    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        this.payload = payload
        return ProfilePersistenceMutationResult.COMPLETED
    }
}
