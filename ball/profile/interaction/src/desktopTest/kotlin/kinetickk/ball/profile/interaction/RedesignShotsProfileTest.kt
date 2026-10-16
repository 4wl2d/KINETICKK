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
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.armory.impl.ArmoryContent
import kinetickk.ball.profile.interaction.armory.impl.ArmoryLayoutHolder
import kinetickk.ball.profile.interaction.armory.impl.ArmoryViewState
import kinetickk.ball.profile.interaction.lab.impl.LabContent
import kinetickk.ball.profile.interaction.lab.impl.LabPurchaseFlash
import kinetickk.ball.profile.interaction.lab.impl.LabState
import kinetickk.ball.profile.interaction.lab.impl.toRenderModel
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
    fun lab() {
        val ranks = listOf(4, 5, 8, 2, 1, 2, 0, 1)
        val model = LabProfileSnapshot(PlayerEconomy(matter = 1_284L), LabProgress(ranks)).toRenderModel(MetaUpgrades)
        for ((scale, suffix) in scales) {
            shoot("lab", "affordable$suffix") { LabContent(LabState(model, MetaUpgradeId.CORE_INTEGRITY), scale, remember { ScrollState(0) }) {} }
            shoot("lab", "maxed$suffix") { LabContent(LabState(model, MetaUpgradeId.MAGNETIC_RESONANCE), scale, remember { ScrollState(0) }) {} }
            shoot("lab", "selected-last$suffix") { LabContent(LabState(model, MetaUpgradeId.ARMORY_LICENSE), scale, remember { ScrollState(0) }) {} }
        }
        shoot("lab", "short") {
            val poor = LabProfileSnapshot(PlayerEconomy(matter = 40L), LabProgress(ranks)).toRenderModel(MetaUpgrades)
            LabContent(LabState(poor, MetaUpgradeId.ARMORY_LICENSE), 1.25f, remember { ScrollState(0) }) {}
        }
        shoot("lab", "flash-invert", advanceMs = 16) {
            LabContent(LabState(model, MetaUpgradeId.CORE_INTEGRITY, LabPurchaseFlash(MetaUpgradeId.CORE_INTEGRITY, 1)), 1.25f,
                remember { ScrollState(0) }) {}
        }
        shoot("lab", "flash-nudge", advanceMs = 150) {
            LabContent(LabState(model, MetaUpgradeId.CORE_INTEGRITY, LabPurchaseFlash(MetaUpgradeId.CORE_INTEGRITY, 1)), 1.25f,
                remember { ScrollState(0) }) {}
        }
        shoot("lab", "text100") { LabContent(LabState(model, MetaUpgradeId.KINETIC_AMPLIFIER), 1f, remember { ScrollState(0) }) {} }
        shoot("lab", "text175") { LabContent(LabState(model, MetaUpgradeId.KINETIC_AMPLIFIER), 1.75f, remember { ScrollState(0) }) {} }
        shoot("lab", "text150") { LabContent(LabState(model, MetaUpgradeId.KINETIC_AMPLIFIER), 1.5f, remember { ScrollState(0) }) {} }
        // Keyboard focus on a row of an overflowing list (ring beside the scroll bar), at the list's
        // start and end, on desktop and phones at 175 % and on phones at the default 125 %.
        val phones = listOf(844 to 390, 390 to 844, 360 to 640)
        for ((id, name) in listOf(MetaUpgradeId.CORE_INTEGRITY to "first", MetaUpgradeId.ARMORY_LICENSE to "last")) {
            shoot("lab", "focused-$name-text175", at = listOf(1440 to 810) + phones, focus = "profile-lab-buy-$id") {
                LabContent(LabState(model, id), 1.75f, remember { ScrollState(0) }) {}
            }
            shoot("lab", "focused-$name", at = phones, focus = "profile-lab-buy-$id") {
                LabContent(LabState(model, id), 1.25f, remember { ScrollState(0) }) {}
            }
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

private val Weapons = listOf(
    WeaponDefinition(WeaponId.FLUX_WAKE, "Flux Wake", "High-speed movement leaves a damaging trail that lingers behind the Core.", listOf("MOMENTUM", "TRAIL", "AREA"), 0),
    WeaponDefinition(WeaponId.MORNINGSTAR, "Morningstar", "A heavy orbital mass converts velocity and mass into crushing contact damage.", listOf("ORBITAL", "IMPACT", "MASS"), 25),
    WeaponDefinition(WeaponId.PHASE_LATTICE, "Phase Lattice", "A pulsing gravity ring damages enemies held near its unstable perimeter.", listOf("AURA", "CONTROL", "PHASE"), 55),
    WeaponDefinition(WeaponId.NULL_LANCE, "Null Lance", "Momentum periodically projects a piercing lance along the Core's travel vector.", listOf("PIERCE", "DIRECTIONAL", "MOMENTUM"), 95),
    WeaponDefinition(WeaponId.GRAVITY_MINES, "Gravity Mines", "Braking plants implosive mines that pull enemies inward before detonating.", listOf("MINE", "PULL", "BRAKE"), 145),
    WeaponDefinition(WeaponId.ION_SWARM, "Ion Swarm", "Autonomous ion motes orbit the Core, seek targets, and accelerate with attack speed.", listOf("DRONE", "SEEKING", "RAPID"), 215),
    WeaponDefinition(WeaponId.RIFT_BLADES, "Rift Blades", "Paired blades tear outward through enemies and return across their original paths.", listOf("BLADE", "RETURNING", "CRITICAL"), 305),
    WeaponDefinition(WeaponId.ARC_COIL, "Arc Coil", "Stored kinetic charge erupts as lightning that chains between nearby enemies.", listOf("CHAIN", "LIGHTNING", "CHARGE"), 430),
    WeaponDefinition(WeaponId.QUASAR_CANNON, "Quasar Cannon", "A slow-charging cannon compresses momentum into a colossal piercing projectile.", listOf("HEAVY", "CHARGE", "PIERCE"), 610),
    WeaponDefinition(WeaponId.ENTROPY_FIELD, "Entropy Field", "A widening decay field slows hostile motion and deals escalating damage over time.", listOf("AURA", "DECAY", "SLOW"), 860),
    WeaponDefinition(WeaponId.SINGULARITY_SPEAR, "Singularity Spear", "Every overdrive cycle forges a boss-piercing spear from condensed kinetic matter.", listOf("ULTIMATE", "OVERDRIVE", "BOSS"), 1_200),
    WeaponDefinition(WeaponId.PRISM_RELAY, "Prism Relay", "Launches a seeking light shard that refracts between targets; mastery adds ricochets and twin relays.", listOf("SEEKING", "RICOCHET", "REFRACTION"), 1_650),
).toImmutableList()

private val MetaUpgrades = listOf(
    MetaUpgradeDefinition(MetaUpgradeId.CORE_INTEGRITY, "Core Integrity", "+10 maximum integrity at the start of every run.", 10, 18, ItemModifier(ItemEffect.MAX_INTEGRITY, 10f)),
    MetaUpgradeDefinition(MetaUpgradeId.KINETIC_AMPLIFIER, "Kinetic Amplifier", "+5% collision damage at the start of every run.", 10, 22, ItemModifier(ItemEffect.IMPACT_DAMAGE, 0.05f)),
    MetaUpgradeDefinition(MetaUpgradeId.MAGNETIC_RESONANCE, "Magnetic Resonance", "+4% magnetic pull strength at the start of every run.", 8, 24, ItemModifier(ItemEffect.MAGNETISM, 0.04f)),
    MetaUpgradeDefinition(MetaUpgradeId.CRYO_VENTS, "Cryo Vents", "+5% heat dissipation at the start of every run.", 8, 26, ItemModifier(ItemEffect.COOLING, 0.05f)),
    MetaUpgradeDefinition(MetaUpgradeId.DASH_CAPACITOR, "Dash Capacitor", "+5% dash impulse at the start of every run.", 8, 30, ItemModifier(ItemEffect.DASH_POWER, 0.05f)),
    MetaUpgradeDefinition(MetaUpgradeId.SALVAGE_PROTOCOL, "Salvage Protocol", "+5% Kinetic Matter gained during runs.", 10, 34, ItemModifier(ItemEffect.MATTER_GAIN, 0.05f)),
    MetaUpgradeDefinition(MetaUpgradeId.DATA_ARCHIVE, "Data Archive", "+5% Data gained during runs.", 10, 38, ItemModifier(ItemEffect.DATA_GAIN, 0.05f)),
    MetaUpgradeDefinition(MetaUpgradeId.ARMORY_LICENSE, "Armory License", "+4% weapon power per rank.", 12, 45, ItemModifier(ItemEffect.WEAPON_POWER, 0.04f)),
).toImmutableList()

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
