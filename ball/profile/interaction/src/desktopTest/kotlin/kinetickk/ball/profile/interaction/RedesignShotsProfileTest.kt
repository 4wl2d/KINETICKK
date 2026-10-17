// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.profile.api.MatrixProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.armory.impl.ArmoryContent
import kinetickk.ball.profile.interaction.armory.impl.ArmoryLayoutHolder
import kinetickk.ball.profile.interaction.armory.impl.ArmoryViewState
import kinetickk.ball.profile.interaction.matrix.impl.MatrixContent
import kinetickk.ball.profile.interaction.matrix.impl.MatrixPurchaseFlash
import kinetickk.ball.profile.interaction.matrix.impl.MatrixState
import kinetickk.ball.profile.interaction.matrix.impl.toRenderModel
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel
import kinetickk.ball.profile.interaction.rebirth.impl.RebirthContent
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/** Local visual QA harness (not committed): renders every D2 screen state for board comparison. */
@OptIn(ExperimentalTestApi::class)
class RedesignShotsProfileTest {
    private val sizes = listOf(1440 to 810, 844 to 390, 390 to 844)
    private val languages = listOf(AppLanguage.English to "en", AppLanguage.Russian to "ru")
    private val out = File("build/redesign-shots/profile").apply { mkdirs() }

    private fun shoot(
        screen: String,
        state: String,
        advanceMs: Long = 400,
        at: List<Pair<Int, Int>> = sizes,
        focus: String? = null,
        content: @Composable () -> Unit,
    ) {
        for ((w, h) in at) for ((language, code) in languages) {
            runSkikoComposeUiTest(Size(w.toFloat(), h.toFloat()), Density(1f)) {
                mainClock.autoAdvance = false
                setContent {
                    CompositionLocalProvider(LocalAppLanguage provides language) {
                        Box(Modifier.requiredSize(w.dp, h.dp).testTag("shot")) { content() }
                    }
                }
                mainClock.advanceTimeBy(advanceMs)
                if (focus != null) {
                    onNodeWithTag(focus).requestFocus()
                    mainClock.advanceTimeBy(64)
                }
                waitForIdle()
                val image = onNodeWithTag("shot").captureToImage()
                val file = File(out, "$screen-$state-${w}x$h-$code.png")
                Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData()!!.use { file.writeBytes(it.bytes) }
            }
        }
    }

    /** The game's default text size (125 %) and the largest (175 %). */
    private val scales = listOf(1.25f to "", 1.75f to "-text175")

    @Test
    fun hangar() {
        val model = kinetickk.ball.profile.interaction.hangar.impl.hangarProjection(
            unlocked = setOf(kinetickk.ball.content.api.CoreShape.ORB, kinetickk.ball.content.api.CoreShape.PRISM,
                kinetickk.ball.content.api.CoreShape.STAR),
        ).let { projection ->
            with(kinetickk.ball.profile.interaction.hangar.impl.HangarShotAccess) { projection.model(1_284L) }
        }
        for ((state, shape) in listOf(
            "selected" to kinetickk.ball.content.api.CoreShape.ORB,
            "unlocked" to kinetickk.ball.content.api.CoreShape.STAR,
            "locked" to kinetickk.ball.content.api.CoreShape.HEXAGON,
        )) for ((scale, suffix) in scales) {
            shoot("hangar", state + suffix) {
                kinetickk.ball.profile.interaction.hangar.impl.HangarShotAccess.Content(model, shape, scale)
            }
        }
    }

    @Test
    fun armory() {
        val model = ArmoryRenderModel(
            totalMatter = 1_284L,
            selectedWeapon = WeaponId.FLUX_WAKE,
            unlockedWeapons = Weapons.take(7).map { it.id }.toImmutableSet(),
            activeRunWeapon = null,
        )
        for ((state, id) in listOf(
            "starter" to WeaponId.FLUX_WAKE,
            "owned" to WeaponId.MORNINGSTAR,
            "affordable" to WeaponId.ARC_COIL,
            "short" to WeaponId.PRISM_RELAY,
        )) for ((scale, suffix) in scales) {
            if (suffix.isNotEmpty() && state == "owned") continue
            shoot("armory", state + suffix) {
                ArmoryContent(model, Weapons, WeaponMastery.entries, ArmoryViewState(id), scale,
                    remember { ScrollState(0) }, remember { ArmoryLayoutHolder() }) {}
            }
        }
        val active = ArmoryRenderModel(1_284L, WeaponId.FLUX_WAKE, Weapons.take(7).map { it.id }.toImmutableSet(), WeaponId.ION_SWARM)
        for ((scale, suffix) in scales) {
            shoot("armory", "active$suffix") {
                ArmoryContent(active, Weapons, WeaponMastery.entries, ArmoryViewState(WeaponId.ION_SWARM), scale,
                    remember { ScrollState(0) }, remember { ArmoryLayoutHolder() }) {}
            }
        }
        // Between the default and the largest size (the phone ladder stacks here).
        shoot("armory", "owned-text150") {
            ArmoryContent(model, Weapons, WeaponMastery.entries, ArmoryViewState(WeaponId.MORNINGSTAR), 1.5f,
                remember { ScrollState(0) }, remember { ArmoryLayoutHolder() }) {}
        }
        // The same at 100 %.
        shoot("armory", "short-text100") {
            ArmoryContent(model, Weapons, WeaponMastery.entries, ArmoryViewState(WeaponId.PRISM_RELAY), 1f,
                remember { ScrollState(0) }, remember { ArmoryLayoutHolder() }) {}
        }
    }

    @Test
    fun matrix() {
        val ranks = MatrixNodeId.entries.map { id ->
            when (id) {
                MatrixNodeId.IMPACT_DRIVER, MatrixNodeId.REINFORCED_HULL, MatrixNodeId.MAGNET_ARRAY -> 3
                MatrixNodeId.CRITICAL_LATTICE, MatrixNodeId.SALVAGE_PROTOCOL, MatrixNodeId.CRYO_LOOP -> 1
                else -> 0
            }
        }
        val model = MatrixProfileSnapshot(PlayerEconomy(matter = 1_284L), MatrixProgress(ranks), rebirthRank = 1)
            .toRenderModel(CatalogMatrixNodes)
        for ((scale, suffix) in scales) {
            shoot("matrix", "affordable$suffix") { MatrixContent(MatrixState(model, MatrixNodeId.WEAPON_CALIBRATION), scale, remember { ScrollState(0) }) {} }
            shoot("matrix", "maxed$suffix") { MatrixContent(MatrixState(model, MatrixNodeId.IMPACT_DRIVER), scale, remember { ScrollState(0) }) {} }
            shoot("matrix", "reborn-locked$suffix") { MatrixContent(MatrixState(model, MatrixNodeId.GLASS_ENGINE), scale, remember { ScrollState(0) }) {} }
            shoot("matrix", "prerequisite$suffix") { MatrixContent(MatrixState(model, MatrixNodeId.WIDE_OFFER), scale, remember { ScrollState(0) }) {} }
        }
        shoot("matrix", "short") {
            val poor = MatrixProfileSnapshot(PlayerEconomy(matter = 40L), MatrixProgress(ranks), rebirthRank = 1).toRenderModel(CatalogMatrixNodes)
            MatrixContent(MatrixState(poor, MatrixNodeId.NANOFORGE), 1.25f, remember { ScrollState(0) }) {}
        }
        shoot("matrix", "reset-armed") {
            MatrixContent(MatrixState(model, MatrixNodeId.IMPACT_DRIVER, resetArmed = true), 1.25f, remember { ScrollState(0) }) {}
        }
        shoot("matrix", "flash-invert", advanceMs = 16) {
            MatrixContent(MatrixState(model, MatrixNodeId.CRYO_LOOP, flash = MatrixPurchaseFlash(MatrixNodeId.CRYO_LOOP, 1)), 1.25f,
                remember { ScrollState(0) }) {}
        }
        shoot("matrix", "text100") { MatrixContent(MatrixState(model, MatrixNodeId.PERPETUAL_DRIFT), 1f, remember { ScrollState(0) }) {} }
        val phones = listOf(844 to 390, 390 to 844)
        shoot("matrix", "focused-last", at = phones, focus = "profile-matrix-node-${MatrixNodeId.PROSPECTOR}") {
            MatrixContent(MatrixState(model, MatrixNodeId.PROSPECTOR), 1.25f, remember { ScrollState(0) }) {}
        }
    }

    private fun rebirth(target: Int, canAdvance: Boolean = true) = RebirthRenderModel(
        current = rebirthProfile((target - 1).coerceAtLeast(0)),
        next = rebirthProfile(target.coerceAtMost(10)),
        canAdvance = canAdvance,
        matter = 1_924L,
        minimumTier = 0,
        maximumTier = 10,
        build = catalogRebirthBuild((target - 1).coerceAtLeast(0), target.coerceAtMost(10)),
    )

    @Test
    fun rebirthTiers() {
        for (tier in 1..10) {
            shoot("rebirth", "tier" + (if (tier < 10) "0$tier" else "$tier")) {
                RebirthContent(rebirth(tier), false, 1.25f, {}, timeSeconds = 7.3f)
            }
        }
        shoot("rebirth", "max") {
            val max = RebirthRenderModel(rebirthProfile(10), rebirthProfile(10), false, 1_924L, 0, 10, catalogRebirthBuild(10, 10))
            RebirthContent(max, false, 1.25f, {}, timeSeconds = 7.3f)
        }
        for (tier in listOf(3, 4, 5, 8, 9, 10)) {
            shoot("rebirth", "tier" + (if (tier < 10) "0$tier" else "$tier") + "-text175") {
                RebirthContent(rebirth(tier), false, 1.75f, {}, timeSeconds = 7.3f)
            }
        }
        shoot("rebirth", "tier04-text100") { RebirthContent(rebirth(4), false, 1f, {}, timeSeconds = 7.3f) }
    }

    @Test
    fun rebirthStates() {
        shoot("rebirth", "tier04-locked") { RebirthContent(rebirth(4, canAdvance = false), false, 1.25f, {}, timeSeconds = 7.3f) }
        shoot("rebirth", "tier04-armed") { RebirthContent(rebirth(4), true, 1.25f, {}, timeSeconds = 7.3f) }
        // Armed with Advance pinned (large text; a small phone).
        shoot("rebirth", "tier04-armed-text175") { RebirthContent(rebirth(4), true, 1.75f, {}, timeSeconds = 7.3f) }
        shoot("rebirth", "tier04-armed", at = listOf(360 to 640)) { RebirthContent(rebirth(4), true, 1.25f, {}, timeSeconds = 7.3f) }
        shoot("rebirth", "tier04-armed-text175", at = listOf(360 to 640)) { RebirthContent(rebirth(4), true, 1.75f, {}, timeSeconds = 7.3f) }
        for (progress in listOf(0.2f, 0.34f, 0.45f, 0.8f)) {
            shoot("rebirth", "tier04-advance${(progress * 100).toInt()}") {
                RebirthContent(rebirth(4), true, 1.25f, {}, timeSeconds = 7.3f, advanceProgress = progress)
            }
        }
    }
}

private val Weapons = CatalogWeapons

/** Mirrors the game's default Rebirth data (content impl) for realistic captures. */
private fun rebirthProfile(tier: Int): RebirthProfile {
    val swarmRanks = (tier + 2) / 3
    val fortifiedRanks = (tier + 1) / 3
    val overclockedRanks = tier / 3
    val directive = when {
        tier == 0 -> RebirthDirective.BASELINE
        (tier - 1) % 3 == 0 -> RebirthDirective.SWARM
        (tier - 1) % 3 == 1 -> RebirthDirective.FORTIFIED
        else -> RebirthDirective.OVERCLOCKED
    }
    return RebirthProfile(
        tier = tier, directive = directive, openingEnemyCount = 5 + (tier + 1) / 2,
        enemyCapMultiplier = 1f + tier * 0.08f + swarmRanks * 0.01f,
        spawnRateMultiplier = 1f + tier * 0.06f + swarmRanks * 0.01f,
        enemyHealthMultiplier = 1f + tier * 0.18f + tier * tier * 0.012f + fortifiedRanks * 0.02f,
        enemySpeedMultiplier = 1f + tier * 0.025f + overclockedRanks * 0.005f,
        incomingDamageMultiplier = 1f + tier * 0.08f + overclockedRanks * 0.005f,
        eliteRateMultiplier = 1f, threatTimeOffsetSeconds = tier * 8f, playerPowerMultiplier = 1f + tier * 0.05f,
        playerIntegrityBonus = tier * 3f, matterGainMultiplier = 1f + tier * 0.12f + fortifiedRanks * 0.01f,
        bonusRerolls = tier / 5, maximumActiveEnemies = 120, minimumSpawnIntervalSeconds = 0.09f, minimumEliteIntervalSeconds = 24f,
    )
}
