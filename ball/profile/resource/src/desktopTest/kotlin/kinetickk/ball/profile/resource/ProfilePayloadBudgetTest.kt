// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.CharacterAchievementProgress
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.FRAME_RATE_CAP_OPTIONS
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.INTERFACE_SCALE_OPTIONS
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.REFRESH_RATE_RANGE
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.common.localization.AppLanguage
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The desktop broker stores the whole profile as one `java.util.prefs` value and refuses a payload
 * longer than [Preferences.MAX_VALUE_LENGTH] UTF-16 units before writing, so a profile that grew past
 * it would silently stop saving. Every bounded fact at its longest encoding must fit.
 */
class ProfilePayloadBudgetTest {
    @Test
    fun aWorstCaseProfileFitsOneDesktopPreferenceValue() {
        val snapshot = worstCaseSnapshot()
        val payload = assertIs<ProfileEncodeResult.Encoded>(ProfileCodec.encode(snapshot)).payload
        assertEquals(snapshot, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(payload)).snapshot)
        println("worst-case profile payload: ${payload.length} UTF-16 units at MAX_ITEMS ${ContentBounds.MAX_ITEMS}")
        assertTrue(payload.length <= Preferences.MAX_VALUE_LENGTH, "worst case ${payload.length} > ${Preferences.MAX_VALUE_LENGTH}")
    }

    @Test
    fun theWorstCaseUsesTheLongestEncodingOfEveryBoundedFact() {
        val profile = worstCaseSnapshot().profile
        // The 0.5.0 item bound (173 → 287 items, `ContentBounds.MAX_ITEMS` 192 → 320), every id owned.
        assertEquals(320, ContentBounds.MAX_ITEMS)
        assertEquals(ContentBounds.MAX_ITEMS, profile.collection.discoveredItemIds.size)
        assertEquals(profile.collection.discoveredItemIds, profile.collection.newItemIds)
        assertEquals(MAX_RECENT_ITEM_IDS, profile.collection.recentItemIds.size)
        assertEquals(MAX_HEIRLOOM_RELICS, profile.collection.heirloomRelicIds.size)
        assertEquals(RelicId.entries.size, profile.collection.discoveredRelicIds.size)
        assertEquals(MatrixNodeId.entries.size, profile.matrix.ownedNodeCount)
        assertTrue((profile.upgrades.characterLevels + profile.upgrades.weaponLevels).all { it == ContentBounds.MAX_UPGRADE_LEVEL })
        assertTrue(GameAction.entries.none { profile.preferences.keyBindings.key(it) == KeyBindings.DEFAULT.key(it) })
        assertEquals(CoreShape.entries.toSet(), profile.rebirthProgress.ascendedCharacters)
        assertTrue(profile.rebirthProgress.characterClears.all { it == ContentBounds.MAX_REBIRTH_LEVEL })
        val payload = assertIs<ProfileEncodeResult.Encoded>(ProfileCodec.encode(worstCaseSnapshot())).payload
        // Every optional preference is written.
        listOf(
            "colorVisionId", "minimapEnabled", "windowModeId", "windowSizeId", "vsyncEnabled", "frameRateCap",
            "refreshRateHz", "immersiveEnabled", "interfaceScalePercent", "showFps", "pauseOnFocusLoss",
            "reactionEffectsId", "keyBindings", "recentItemIds", "ascendedCharacterIds", "characterClears", "upgrades",
        ).forEach { field -> assertTrue(payload.contains("\"$field\":"), field) }
    }
}

private fun <T : Enum<T>> Iterable<T>.longestName(): T = maxBy { it.name.length }

/**
 * Every bounded fact at its largest size and longest wire form: Long counters at Long.MAX_VALUE, the
 * longest enum ids, three-digit percentages, and every optional field written.
 */
private fun worstCaseSnapshot(): ProfileSnapshot {
    val defaults = KeyBindings.DEFAULT
    val longestKeys = BindableKey.entries.filter { it !in defaults.keys }.sortedByDescending { it.name.length }
    return ProfileSnapshot(
        revision = ProfileRevision(Long.MAX_VALUE),
        profile = PlayerProfile(
            preferences = PlayerPreferences(
                soundEnabled = false,
                musicEnabled = false,
                masterVolume = 1f,
                simulationSpeed = SIMULATION_SPEED_OPTIONS.last(),
                textScale = 1.75f,
                screenShake = false,
                particleDensity = ParticleDensity.entries.longestName(),
                damageNumbers = false,
                damageNumberSize = DamageNumberSize.entries.longestName(),
                damageNumberFormat = DamageNumberFormat.entries.longestName(),
                damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
                language = AppLanguage.English,
                runStatisticsOnLeft = false,
                colorVision = ColorVision.entries.filter { it != ColorVision.DEFAULT }.longestName(),
                minimapEnabled = false,
                windowMode = WindowMode.entries.filter { it != WindowMode.WINDOWED }.longestName(),
                windowSize = WindowSizePreset.entries.filter { it != WindowSizePreset.W1280X800 }.longestName(),
                vsync = false,
                frameRateCap = FRAME_RATE_CAP_OPTIONS.last(),
                refreshRateHz = REFRESH_RATE_RANGE.last,
                immersive = true,
                interfaceScalePercent = INTERFACE_SCALE_OPTIONS.last(),
                showFps = true,
                pauseOnFocusLoss = false,
                reactionEffects = ReactionEffectsLevel.entries.filter { it != ReactionEffectsLevel.FULL }.longestName(),
                keyBindings = KeyBindings.of(longestKeys.take(GameAction.entries.size)),
            ),
            economy = PlayerEconomy(Long.MAX_VALUE, Long.MAX_VALUE),
            loadout = PlayerLoadout(
                coreShape = CoreShape.entries.longestName(),
                selectedWeapon = WeaponId.entries.longestName(),
                unlockedWeapons = WeaponId.entries.toSet(),
            ),
            matrix = MatrixProgress(List(MatrixNodeId.entries.size) { ContentBounds.MAX_MATRIX_NODE_RANKS }),
            collection = PlayerCollection(
                discoveredItemIds = (0 until ContentBounds.MAX_ITEMS).toSet(),
                newItemIds = (0 until ContentBounds.MAX_ITEMS).toSet(),
                discoveredRelicIds = RelicId.entries.toSet(),
                newRelicIds = RelicId.entries.toSet(),
                discoveredReactionIds = ReactionId.entries.toSet(),
                discoveredPairingIds = RelicPairingId.entries.toSet(),
                heirloomRelicIds = RelicId.entries.sortedByDescending { it.name.length }.take(MAX_HEIRLOOM_RELICS),
                recentItemIds = List(MAX_RECENT_ITEM_IDS) { ContentBounds.MAX_ITEMS - 1 },
            ),
            rebirthProgress = RebirthProgress(
                level = ContentBounds.MAX_REBIRTH_LEVEL,
                highestCleared = ContentBounds.MAX_REBIRTH_LEVEL,
                ascendedCharacters = CoreShape.entries.toImmutableSet(),
                characterClears = List(CoreShape.entries.size) { ContentBounds.MAX_REBIRTH_LEVEL }.toImmutableList(),
            ),
            characterAchievements = CharacterAchievementProgress(
                eliteKills = Long.MAX_VALUE,
                dashHits = Long.MAX_VALUE,
                completedOrbits = Long.MAX_VALUE,
                architectVictories = Long.MAX_VALUE,
                victoriousCharacters = CoreShape.entries.toImmutableSet(),
                totalKills = Long.MAX_VALUE,
                ricochets = Long.MAX_VALUE,
                dataPickups = Long.MAX_VALUE,
            ),
            upgrades = PermanentUpgrades(
                characterLevels = List(CoreShape.entries.size) { ContentBounds.MAX_UPGRADE_LEVEL },
                weaponLevels = List(WeaponId.entries.size) { ContentBounds.MAX_UPGRADE_LEVEL },
            ),
        ),
    )
}
