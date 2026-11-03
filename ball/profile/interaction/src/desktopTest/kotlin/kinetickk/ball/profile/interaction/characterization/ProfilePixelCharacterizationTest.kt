// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.characterization

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.profile.api.MatrixProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.interaction.CatalogMatrixNodes
import kinetickk.ball.profile.interaction.CatalogWeapons
import kinetickk.ball.profile.interaction.RedesignShotsProfileTest
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.armory.impl.ArmoryContent
import kinetickk.ball.profile.interaction.armory.impl.ArmoryLayoutHolder
import kinetickk.ball.profile.interaction.armory.impl.ArmoryViewState
import kinetickk.ball.profile.interaction.hangar.impl.HangarShotAccess
import kinetickk.ball.profile.interaction.matrix.impl.MatrixContent
import kinetickk.ball.profile.interaction.matrix.impl.MatrixState
import kinetickk.ball.profile.interaction.matrix.impl.toRenderModel
import kinetickk.ball.profile.interaction.rebirth.impl.RebirthContent
import kinetickk.ball.profile.interaction.settings.impl.SettingsTestProfilePort
import kinetickk.ball.profile.interaction.settings.impl.SettingsTestSilentAudio
import kinetickk.ball.profile.interaction.settings.impl.DefaultSettingsFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplayPort
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopSegment
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.test.Test

/**
 * Characterizes rendered Profile screens through the profile and settings shot harness paths: the Workshop's
 * Characters, Weapons and Matrix tabs, the Rebirth screen and two Settings groups, at 1440×810 in English and Russian
 * on a fixed clock. Each scene renders in two fresh compositions and the SHA-256 of the second capture is printed.
 */
@OptIn(ExperimentalTestApi::class)
class ProfilePixelCharacterizationTest {
    @Test
    fun workshopAndRebirthScreensHashStably() {
        val harness = RedesignShotsProfileTest()
        val armory = ArmoryRenderModel(
            totalMatter = 1_284L,
            selectedWeapon = WeaponId.FLUX_WAKE,
            unlockedWeapons = CatalogWeapons.take(7).map { it.id }.toImmutableSet(),
            activeRunWeapon = null,
            rebirthRank = 0,
            upgrades = PermanentUpgrades(CoreShape.entries.map { 0 }, WeaponId.entries.map { if (it == WeaponId.MORNINGSTAR) 10 else 0 }),
        )
        val ranks = MatrixNodeId.entries.map { id ->
            when (id) {
                MatrixNodeId.IMPACT_DRIVER, MatrixNodeId.REINFORCED_HULL, MatrixNodeId.MAGNET_ARRAY -> 3
                MatrixNodeId.CRITICAL_LATTICE, MatrixNodeId.SALVAGE_PROTOCOL, MatrixNodeId.CRYO_LOOP -> 1
                else -> 0
            }
        }
        val matrix = MatrixProfileSnapshot(PlayerEconomy(matter = 1_284L), MatrixProgress(ranks), rebirthRank = 1)
            .toRenderModel(CatalogMatrixNodes)
        val characters = harness.characters()
        val rebirth = harness.rebirth(4)
        val scenes = listOf<Pair<String, @Composable () -> Unit>>(
            "workshop-characters-level0" to {
                HangarShotAccess.Content(characters, CoreShape.ORB, 1.25f, segment = WorkshopSegment.UPGRADE)
            },
            "workshop-characters-traits" to {
                HangarShotAccess.Content(characters, CoreShape.STAR, 1.25f, segment = WorkshopSegment.TRAITS)
            },
            "workshop-weapons-level10" to {
                ArmoryContent(armory, CatalogWeapons, WeaponMastery.entries, ArmoryViewState(WeaponId.MORNINGSTAR), 1.25f,
                    remember { ScrollState(0) }, remember { ArmoryLayoutHolder() }, segment = WorkshopSegment.UPGRADE) {}
            },
            "workshop-weapons-mastery" to {
                ArmoryContent(armory, CatalogWeapons, WeaponMastery.entries, ArmoryViewState(WeaponId.MORNINGSTAR), 1.25f,
                    remember { ScrollState(0) }, remember { ArmoryLayoutHolder() }, segment = WorkshopSegment.MASTERY) {}
            },
            "workshop-matrix" to {
                MatrixContent(MatrixState(matrix, MatrixNodeId.WEAPON_CALIBRATION), 1.25f, remember { ScrollState(0) }) {}
            },
            "rebirth-tier04" to {
                RebirthContent(rebirth, false, 1.25f, {}, timeSeconds = 7.3f)
            },
        )
        for ((name, content) in scenes) for ((language, code) in PROFILE_LANGUAGES) {
            fun capture(): ImageBitmap {
                var image: ImageBitmap? = null
                runSkikoComposeUiTest(Size(PROFILE_WIDTH.toFloat(), PROFILE_HEIGHT.toFloat()), Density(1f)) {
                    mainClock.autoAdvance = false
                    setContent {
                        CompositionLocalProvider(LocalAppLanguage provides language) {
                            Box(Modifier.requiredSize(PROFILE_WIDTH.dp, PROFILE_HEIGHT.dp).testTag("shot")) { content() }
                        }
                    }
                    mainClock.advanceTimeBy(400)
                    waitForIdle()
                    image = onNodeWithTag("shot").captureToImage()
                }
                return checkNotNull(image)
            }
            capture()
            printProfilePixelHash(name, code, capture())
        }
    }

    @Test
    fun settingsGroupsHashStably() {
        val groups = listOf("game" to "settings-game", "graphics" to "settings-graphics")
        for ((tab, state) in groups) for ((language, code) in PROFILE_LANGUAGES) {
            fun capture(): ImageBitmap {
                var image: ImageBitmap? = null
                runSkikoComposeUiTest(size = Size(PROFILE_WIDTH.toFloat(), PROFILE_HEIGHT.toFloat()), density = Density(1f)) {
                    mainClock.autoAdvance = false
                    val feature = DefaultSettingsFeature(
                        SettingsTestProfilePort(PlayerPreferences(language = language)), SettingsTestSilentAudio, SettingsDisplayPort.None,
                    )
                    setContent {
                        CompositionLocalProvider(LocalAppLanguage provides language) {
                            Box(Modifier.requiredSize(PROFILE_WIDTH.dp, PROFILE_HEIGHT.dp)) { feature.Content(routeToken = 1L) { } }
                        }
                    }
                    mainClock.advanceTimeBy(100)
                    if (tab != "game") {
                        onNodeWithTag("kinetickk.settings.group.$tab").performClick()
                        mainClock.advanceTimeBy(100)
                    }
                    mainClock.advanceTimeBy(1_100)
                    image = onRoot().captureToImage()
                }
                return checkNotNull(image)
            }
            capture()
            printProfilePixelHash(state, code, capture())
        }
    }
}

private const val PROFILE_WIDTH = 1440
private const val PROFILE_HEIGHT = 810

private val PROFILE_LANGUAGES = listOf(AppLanguage.English to "en", AppLanguage.Russian to "ru")

private fun printProfilePixelHash(scene: String, language: String, bitmap: ImageBitmap) {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(pixels)
    val bytes = ByteBuffer.allocate(pixels.size * 4)
    bytes.asIntBuffer().put(pixels)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
    println("scene=$scene lang=$language sha256=$digest")
}
