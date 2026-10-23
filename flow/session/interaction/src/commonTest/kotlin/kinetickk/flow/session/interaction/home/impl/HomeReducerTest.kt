// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.defaultMatrixNodes
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.flow.session.interaction.audio.SessionAudioCue
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.interaction.TestCoreShapes
import kinetickk.flow.session.interaction.TestRebirthPolicy
import kinetickk.flow.session.interaction.testWeapon
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HomeReducerTest {
    @Test
    fun presentationClockAcceptsMaximumAndClampsNextRepresentableDelta() {
        val nextRepresentable = Float.fromBits(MAX_HOME_PRESENTATION_FRAME_DELTA_SECONDS.toBits() + 1)

        assertEquals(
            MAX_HOME_PRESENTATION_FRAME_DELTA_SECONDS,
            selectHomePresentationFrameDeltaSeconds(MAX_HOME_PRESENTATION_FRAME_DELTA_SECONDS),
        )
        assertEquals(
            MAX_HOME_PRESENTATION_FRAME_DELTA_SECONDS,
            selectHomePresentationFrameDeltaSeconds(nextRepresentable),
        )
    }

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
    fun theRebirthBadgeShowsTheRankTheSelectedTierAndTheAscendantTitle() {
        val lowered = homeReducer().uiModel(homeProjection(rebirth = RebirthProgress(level = 2, highestCleared = 4), canAdvanceRebirth = true))
        assertEquals(2, lowered.rebirthLevel)
        assertEquals(5, lowered.rebirthRank)
        assertFalse(lowered.ascendant)
        val facts = homeFacts(lowered, HomeLayoutTarget.REBIRTH, kinetickk.foundation.common.localization.AppLanguage.English, 1f)
        assertEquals("2", facts.big)
        assertEquals(listOf("Tier", "Rank", "Status"), facts.facts.map { it.first })
        assertEquals("5", facts.facts[1].second)
        assertEquals("New tier", facts.facts[2].second)

        val ascendant = homeReducer().uiModel(homeProjection(rebirth = RebirthProgress(level = 7, highestCleared = 10)))
        assertTrue(ascendant.ascendant)
        assertEquals(10, ascendant.rebirthRank)
        val crowned = homeFacts(ascendant, HomeLayoutTarget.REBIRTH, kinetickk.foundation.common.localization.AppLanguage.Russian, 1f)
        assertEquals("Вознесённый", crowned.facts[1].second)
        assertEquals("Выбрана", crowned.facts[2].second)
    }

    @Test
    fun theWorkshopStampIsTheProfilesAffordabilityRead() {
        // Home never repeats a purchase rule: the stamp is the Profile's own dry run of its purchases.
        val weapons = listOf(testWeapon(WeaponId.FLUX_WAKE, cost = 0), testWeapon(WeaponId.GLACIER_BELL, cost = 700, minimumRebirth = 1))
            .toImmutableList()
        val reducer = HomeReducer(TestCoreShapes, 80, weapons.size, TestRebirthPolicy, weapons = weapons)
        val rich = homeProjection(economy = PlayerEconomy(matter = 10_000, lifetimeMatter = 10_000))
        assertFalse(reducer.uiModel(rich).workshopAffordable)
        assertEquals(null, homeFacts(reducer.uiModel(rich), HomeLayoutTarget.WORKSHOP, AppLanguage.English, 1f).stamp)
        val affordable = reducer.uiModel(homeProjection().copy(workshopAffordable = true, upgradeLevels = 7))
        assertTrue(affordable.workshopAffordable)
        assertEquals(7, affordable.upgradeLevels)
        assertEquals("Affordable", homeFacts(affordable, HomeLayoutTarget.WORKSHOP, AppLanguage.English, 1f).stamp)
        assertEquals("Доступно", homeFacts(affordable, HomeLayoutTarget.WORKSHOP, AppLanguage.Russian, 1f).stamp)
        // The level cap is every level the Workshop sells: 10 per character and per weapon.
        assertEquals(10 * (TestCoreShapes.size + weapons.size), affordable.upgradeLevelCap)
    }

    @Test
    fun matrixTileShowsOwnedNodesOverAllNodesAndOwnedRanksOverAllRanks() {
        val nodes = defaultMatrixNodes()
        val owned = mapOf(
            MatrixNodeId.IMPACT_DRIVER to 3, MatrixNodeId.WEAPON_CALIBRATION to 1, MatrixNodeId.REINFORCED_HULL to 2,
            MatrixNodeId.HARDLIGHT_RESERVE to 1, MatrixNodeId.MAGNET_ARRAY to 1, MatrixNodeId.SALVAGE_PROTOCOL to 2,
            MatrixNodeId.DATA_ARCHIVE to 1,
        )
        val matrix = MatrixProgress(MatrixNodeId.entries.map { owned[it] ?: 0 })
        val reducer = HomeReducer(TestCoreShapes, 80, 12, TestRebirthPolicy, matrixNodes = nodes)
        val projection = homeProjection(economy = PlayerEconomy(matter = 640, lifetimeMatter = 900))

        val model = reducer.uiModel(projection, matrix)
        assertEquals(7, model.matrixOwnedNodes)
        assertEquals(32, model.matrixNodeCount)
        assertEquals(11, model.matrixRanks)
        assertEquals(nodes.sumOf { it.maxRanks }, model.matrixMaxRanks)

        // The Workshop facts: what is bought of everything it sells, in whole percent, then each part.
        val english = homeFacts(model, HomeLayoutTarget.WORKSHOP, AppLanguage.English, 1f)
        assertEquals("Workshop", english.label)
        val total = 12 + model.matrixMaxRanks + model.upgradeLevelCap
        assertEquals("${(model.unlockedWeaponCount + 11) * 100 / total}%", english.big)
        assertEquals(english.big, english.sub)
        assertEquals(listOf("Weapons", "Ranks", "Upgrades"), english.facts.map { it.first })
        assertTrue("Ranks" to "11/${model.matrixMaxRanks}" in english.facts, english.facts.toString())
        assertTrue("Upgrades" to "0/${model.upgradeLevelCap}" in english.facts, english.facts.toString())
        val russian = homeFacts(model, HomeLayoutTarget.WORKSHOP, AppLanguage.Russian, 1f)
        assertEquals("Мастерская", russian.label)
        assertEquals(listOf("Оружие", "Ранги", "Улучшения"), russian.facts.map { it.first })

        // Before the Matrix is read the facts claim no rank count.
        val unread = homeFacts(reducer.uiModel(projection), HomeLayoutTarget.WORKSHOP, AppLanguage.English, 1f)
        assertTrue(unread.facts.none { it.first == "Ranks" })
    }

    @Test
    fun workshopCompletionCountsWeaponsRanksAndLevelsRoundedDown() {
        val base = homeReducer().uiModel(homeProjection())
        assertEquals(0, homeWorkshopCompletion(base.copy(unlockedWeaponCount = 0, weaponCount = 0)))
        // 1 + 2 + 3 of 24 + 50 + 360 = 6/434 → 1 %; everything bought → 100 %.
        val some = base.copy(unlockedWeaponCount = 1, weaponCount = 24, matrixRanks = 2, matrixMaxRanks = 50, upgradeLevels = 3,
            upgradeLevelCap = 360)
        assertEquals(1, homeWorkshopCompletion(some))
        assertEquals(100, homeWorkshopCompletion(some.copy(unlockedWeaponCount = 24, matrixRanks = 50, upgradeLevels = 360)))
        assertEquals(99, homeWorkshopCompletion(some.copy(unlockedWeaponCount = 24, matrixRanks = 50, upgradeLevels = 359)))
    }

    @Test
    fun actionsEmitTypedProfileIntentOrOrderedShellEffects() {
        val reducer = homeReducer()

        // The Workshop row opens the Workshop on the tab shown last; characters are chosen there.
        val workshop = reducer.reduce(HomeAction.OpenWorkshop).effects
        assertEquals(SessionAudioCue.UI_CLICK, assertIs<HomeEffect.PlayAudio>(workshop[0]).cue)
        assertEquals(HomeOutput.OpenWorkshop, assertIs<HomeEffect.Emit>(workshop[1]).output)

        val start = reducer.reduce(HomeAction.StartRun).effects
        assertEquals(SessionAudioCue.UI_CLICK, assertIs<HomeEffect.PlayAudio>(start[0]).cue)
        assertIs<HomeOutput.StartRun>(assertIs<HomeEffect.Emit>(start[1]).output)

        val settings = reducer.reduce(HomeAction.OpenSettings).effects
        assertIs<HomeOutput.OpenSettings>(assertIs<HomeEffect.Emit>(settings[1]).output)
    }

    @Test
    fun pointerGeometryPreservesCoreStartAndRouteHitboxes() {
        val viewport = HomeViewport(1_280f, 720f, 1f)

        // 1280 × 720 is the 16:9 reference scaled by 8/9: the menu sits on the right half.
        assertEquals(HomeAction.StartRun, resolveHomePress(viewport, 1_000f, 140f))
        assertEquals(HomeAction.OpenRebirth, resolveHomePress(viewport, 1_000f, 340f))
        assertEquals(null, resolveHomePress(viewport, 300f, 275f), "The hero is decoration, not a target")
        // Where the Characters card stood, under the form name, nothing opens: characters change in the Workshop.
        assertEquals(null, resolveHomePress(viewport, 978f, 638f))
    }
}

private fun homeReducer(): HomeReducer = HomeReducer(
    coreShapes = TestCoreShapes,
    itemCount = 80,
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
    unlockedCoreShapes = kinetickk.foundation.collections.immutableSetOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.SHARD),
)
