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
    fun theArmoryIsUnlockableOnlyForAWeaponTheRebirthRankAllows() {
        // Every ungated weapon owned; only Glacier Bell (Reborn 1) is left to buy.
        val weapons = listOf(
            testWeapon(WeaponId.FLUX_WAKE, cost = 0),
            testWeapon(WeaponId.MORNINGSTAR, cost = 120),
            testWeapon(WeaponId.GLACIER_BELL, cost = 700, minimumRebirth = 1),
        ).toImmutableList()
        val reducer = HomeReducer(TestCoreShapes, 80, weapons.size, TestRebirthPolicy, weapons = weapons)
        val loadout = PlayerLoadout(
            coreShape = CoreShape.ORB,
            selectedWeapon = WeaponId.FLUX_WAKE,
            unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR),
        )
        val rich = PlayerEconomy(matter = 10_000, lifetimeMatter = 10_000)

        assertFalse(reducer.uiModel(homeProjection(economy = rich, loadout = loadout)).weaponUnlockAffordable)
        // Rank 1 (tier 0 cleared) opens the gate.
        val reborn = homeProjection(economy = rich, loadout = loadout, rebirth = RebirthProgress(level = 0, highestCleared = 0))
        assertTrue(reducer.uiModel(reborn).weaponUnlockAffordable)
        assertFalse(reducer.uiModel(reborn.copy(economy = PlayerEconomy(matter = 699, lifetimeMatter = 10_000))).weaponUnlockAffordable)
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

        val english = homeFacts(model, HomeLayoutTarget.LAB, AppLanguage.English, 1f)
        assertEquals("Matrix", english.label)
        assertEquals("7/32", english.big)
        assertEquals("7/32", english.sub)
        assertTrue("Ranks" to "11/${model.matrixMaxRanks}" in english.facts, english.facts.toString())
        val russian = homeFacts(model, HomeLayoutTarget.LAB, AppLanguage.Russian, 1f)
        assertEquals("Матрица", russian.label)
        assertEquals("7/32", russian.big)

        // Before the Matrix is read the tile falls back to the Matter balance and claims no count.
        val unread = homeFacts(reducer.uiModel(projection), HomeLayoutTarget.LAB, AppLanguage.English, 1f)
        assertEquals(null, unread.sub)
        assertTrue(unread.facts.none { it.first == "Ranks" })
    }

    @Test
    fun actionsEmitTypedProfileIntentOrOrderedShellEffects() {
        val reducer = homeReducer()

        val hangar = reducer.reduce(HomeAction.OpenHangar).effects
        assertEquals(SessionAudioCue.UI_CLICK, assertIs<HomeEffect.PlayAudio>(hangar[0]).cue)
        assertIs<HomeOutput.OpenHangar>(assertIs<HomeEffect.Emit>(hangar[1]).output)

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
            HomeAction.OpenHangar,
            homeLayoutGeometry(1_280f, 720f, 1f).bounds(HomeLayoutTarget.HANGAR).center.let {
                resolveHomePress(viewport, it.x, it.y)
            },
        )
        // 1280 × 720 is the 16:9 reference scaled by 8/9: the menu sits on the right half.
        assertEquals(HomeAction.StartRun, resolveHomePress(viewport, 1_000f, 140f))
        assertEquals(HomeAction.OpenRebirth, resolveHomePress(viewport, 1_000f, 340f))
        assertEquals(null, resolveHomePress(viewport, 300f, 275f), "The hero is decoration, not a target")
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
