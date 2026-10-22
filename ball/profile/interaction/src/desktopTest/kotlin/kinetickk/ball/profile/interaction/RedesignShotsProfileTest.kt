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
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.profile.api.MatrixProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.armory.impl.ArmoryContent
import kinetickk.ball.profile.interaction.armory.impl.ArmoryLayoutHolder
import kinetickk.ball.profile.interaction.armory.impl.ArmoryNotice
import kinetickk.ball.profile.interaction.armory.impl.ArmoryRefusal
import kinetickk.ball.profile.interaction.armory.impl.ArmoryViewState
import kinetickk.ball.profile.interaction.hangar.impl.HangarShotAccess
import kinetickk.ball.profile.interaction.hangar.impl.hangarProjection
import kinetickk.ball.profile.interaction.matrix.impl.MatrixContent
import kinetickk.ball.profile.interaction.matrix.impl.MatrixPurchaseFlash
import kinetickk.ball.profile.interaction.matrix.impl.MatrixState
import kinetickk.ball.profile.interaction.matrix.impl.toRenderModel
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel
import kinetickk.ball.profile.interaction.rebirth.impl.RebirthContent
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopSegment
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * Local visual QA harness: renders every Workshop tab and Rebirth state for board comparison.
 *
 * With `KK_SHOTS=1` every state renders at each of its sizes in both languages into build/redesign-shots/profile.
 * Otherwise the run is a smoke: each state renders once (its first size, English) and nothing is written, so a
 * state that fails to compose, lay out or draw still fails.
 */
@OptIn(ExperimentalTestApi::class)
class RedesignShotsProfileTest {
    private val fullShots = System.getenv("KK_SHOTS") == "1"
    private val sizes = listOf(1440 to 810, 844 to 390, 390 to 844)
    private val languages = listOf(AppLanguage.English to "en", AppLanguage.Russian to "ru")
    private val out by lazy { File("build/redesign-shots/profile").apply { mkdirs() } }

    private fun shoot(
        screen: String,
        state: String,
        advanceMs: Long = 400,
        at: List<Pair<Int, Int>> = sizes,
        focus: String? = null,
        content: @Composable () -> Unit,
    ) {
        for ((w, h) in if (fullShots) at else at.take(1)) for ((language, code) in if (fullShots) languages else languages.take(1)) {
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
                if (!fullShots) return@runSkikoComposeUiTest
                val file = File(out, "$screen-$state-${w}x$h-$code.png")
                Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData()!!.use { file.writeBytes(it.bytes) }
            }
        }
    }

    /** The game's default text size (125 %) and the largest (175 %). */
    private val scales = listOf(1.25f to "", 1.75f to "-text175")

    /** Refit levels of the shots' roster: Circle at [orb], Square at [prism], Star at [star]. */
    private fun characterLevels(orb: Int = 0, prism: Int = 0, star: Int = 0) = PermanentUpgrades(
        CoreShape.entries.map { when (it) { CoreShape.ORB -> orb; CoreShape.PRISM -> prism; CoreShape.STAR -> star; else -> 0 } },
        WeaponId.entries.map { 0 },
    )

    /** Honing levels of the shots' arsenal: Flux Wake at [flux], Morningstar at [morningstar]. */
    private fun weaponLevels(flux: Int = 0, morningstar: Int = 0) = PermanentUpgrades(
        CoreShape.entries.map { 0 },
        WeaponId.entries.map { when (it) { WeaponId.FLUX_WAKE -> flux; WeaponId.MORNINGSTAR -> morningstar; else -> 0 } },
    )

    private val roster = setOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.STAR)

    private fun characters(matter: Long = 1_284L, upgrades: PermanentUpgrades = characterLevels(prism = 3, star = 10)) =
        with(HangarShotAccess) { hangarProjection(unlocked = roster).model(matter, upgrades) }

    /**
     * The Characters tab: the Refit block at level 0 (affordable), level 5 (short of Matter) and level 10 (the
     * maximum), a locked character's unlock progress, the Traits segment and a run in progress ("In a run").
     */
    @Test
    fun workshopCharacters() {
        for ((state, scene) in listOf(
            "level0-affordable" to Triple(characters(), CoreShape.ORB, WorkshopSegment.UPGRADE),
            "level5-short" to Triple(characters(upgrades = characterLevels(orb = 5)), CoreShape.ORB, WorkshopSegment.UPGRADE),
            "level10-max" to Triple(characters(), CoreShape.STAR, WorkshopSegment.UPGRADE),
            "locked" to Triple(characters(), CoreShape.HEXAGON, WorkshopSegment.UPGRADE),
            "traits" to Triple(characters(), CoreShape.STAR, WorkshopSegment.TRAITS),
        )) for ((scale, suffix) in scales) {
            val (model, shape, segment) = scene
            shoot("workshop-characters", state + suffix) { HangarShotAccess.Content(model, shape, scale, segment = segment) }
        }
        shoot("workshop-characters", "in-run") {
            HangarShotAccess.Content(characters(), CoreShape.PRISM, 1.25f, selectionAvailable = false)
        }
        // Ascendant: the header reads Ascendant and the characters that cleared tier 10 wear the crown (the selected
        // tile and its detail preview among them).
        val ascendant = hangarProjection(unlocked = roster)
            .copy(ascendant = true, ascendedCharacters = immutableSetOf(CoreShape.ORB, CoreShape.STAR))
            .let { projection -> with(HangarShotAccess) { projection.model(1_284L, characterLevels(orb = 2)) } }
        for ((scale, suffix) in scales) {
            shoot("workshop-characters", "ascendant$suffix") { HangarShotAccess.Content(ascendant, CoreShape.ORB, scale) }
        }
    }

    /**
     * The Weapons tab: the Honing block at level 0 (affordable), level 5 (short) and level 10, a weapon that is not
     * owned (affordable and short unlocks, a Rebirth gate), the Mastery and Details segments and the run's weapon.
     */
    @Test
    fun workshopWeapons() {
        fun model(matter: Long = 1_284L, upgrades: PermanentUpgrades = weaponLevels(morningstar = 10)) = ArmoryRenderModel(
            totalMatter = matter,
            selectedWeapon = WeaponId.FLUX_WAKE,
            unlockedWeapons = Weapons.take(7).map { it.id }.toImmutableSet(),
            activeRunWeapon = null,
            rebirthRank = 0,
            upgrades = upgrades,
        )
        for ((state, scene) in listOf(
            "level0-affordable" to Triple(model(), WeaponId.FLUX_WAKE, WorkshopSegment.UPGRADE),
            "level5-short" to Triple(model(upgrades = weaponLevels(flux = 5)), WeaponId.FLUX_WAKE, WorkshopSegment.UPGRADE),
            "level10-max" to Triple(model(), WeaponId.MORNINGSTAR, WorkshopSegment.UPGRADE),
            "unowned-affordable" to Triple(model(), WeaponId.ARC_COIL, WorkshopSegment.UPGRADE),
            "unowned-short" to Triple(model(), WeaponId.PRISM_RELAY, WorkshopSegment.UPGRADE),
            "mastery" to Triple(model(), WeaponId.MORNINGSTAR, WorkshopSegment.MASTERY),
        )) for ((scale, suffix) in scales) {
            val (armory, id, segment) = scene
            shoot("workshop-weapons", state + suffix) {
                ArmoryContent(armory, Weapons, WeaponMastery.entries, ArmoryViewState(id), scale,
                    remember { ScrollState(0) }, remember { ArmoryLayoutHolder() }, segment = segment) {}
            }
        }
        // Ion Swarm in the run, the Hexagon (its signature character) selected, an heirloom carrying its Charge key.
        val active = ArmoryRenderModel(1_284L, WeaponId.FLUX_WAKE, Weapons.take(7).map { it.id }.toImmutableSet(), WeaponId.ION_SWARM,
            rebirthRank = 0, character = CoreShape.HEXAGON,
            heirloomRelics = kinetickk.foundation.collections.ImmutableList.copyOf(CatalogHeirlooms), upgrades = weaponLevels(flux = 3))
        for ((scale, suffix) in scales) {
            shoot("workshop-weapons", "active$suffix") {
                ArmoryContent(active, Weapons, WeaponMastery.entries, ArmoryViewState(WeaponId.ION_SWARM), scale,
                    remember { ScrollState(0) }, remember { ArmoryLayoutHolder() },
                    kinetickk.ball.content.api.defaultCoreShapeDefinitions(), CatalogRebirthRelics) {}
            }
            // The Details segment scrolled to the weapon sheet (synergy and facts).
            shoot("workshop-weapons", "details$suffix") {
                ArmoryContent(active, Weapons, WeaponMastery.entries, ArmoryViewState(WeaponId.ION_SWARM), scale,
                    remember { ScrollState(0) }, remember { ArmoryLayoutHolder() },
                    kinetickk.ball.content.api.defaultCoreShapeDefinitions(), CatalogRebirthRelics, detailScrollStart = 10_000,
                    segment = WorkshopSegment.DETAILS) {}
            }
            // A Rebirth-gated weapon, refused once: the tile names the rank and the panel the reason.
            shoot("workshop-weapons", "reborn$suffix") {
                ArmoryContent(active, Weapons, WeaponMastery.entries,
                    ArmoryViewState(WeaponId.GLACIER_BELL, ArmoryNotice(WeaponId.GLACIER_BELL, ArmoryRefusal.REBIRTH_LOCKED)), scale,
                    remember { ScrollState(0) }, remember { ArmoryLayoutHolder() },
                    kinetickk.ball.content.api.defaultCoreShapeDefinitions(), CatalogRebirthRelics) {}
            }
        }
        // Between the default and the largest size (the phone ladder stacks here).
        shoot("workshop-weapons", "mastery-text150") {
            ArmoryContent(model(), Weapons, WeaponMastery.entries, ArmoryViewState(WeaponId.MORNINGSTAR), 1.5f,
                remember { ScrollState(0) }, remember { ArmoryLayoutHolder() }, segment = WorkshopSegment.MASTERY) {}
        }
        // The same at 100 %.
        shoot("workshop-weapons", "level5-short-text100") {
            ArmoryContent(model(upgrades = weaponLevels(flux = 5)), Weapons, WeaponMastery.entries, ArmoryViewState(WeaponId.FLUX_WAKE), 1f,
                remember { ScrollState(0) }, remember { ArmoryLayoutHolder() }) {}
        }
    }

    /** The Core Matrix tab (its body is unchanged; the Reset sits in the header's extra slot). */
    @Test
    fun workshopMatrix() {
        val ranks = MatrixNodeId.entries.map { id ->
            when (id) {
                MatrixNodeId.IMPACT_DRIVER, MatrixNodeId.REINFORCED_HULL, MatrixNodeId.MAGNET_ARRAY -> 3
                MatrixNodeId.CRITICAL_LATTICE, MatrixNodeId.SALVAGE_PROTOCOL, MatrixNodeId.CRYO_LOOP -> 1
                else -> 0
            }
        }
        val model = MatrixProfileSnapshot(PlayerEconomy(matter = 1_284L), MatrixProgress(ranks), rebirthRank = 1)
            .toRenderModel(CatalogMatrixNodes)
        val screen = "workshop-matrix"
        for ((scale, suffix) in scales) {
            shoot(screen, "affordable$suffix") { MatrixContent(MatrixState(model, MatrixNodeId.WEAPON_CALIBRATION), scale, remember { ScrollState(0) }) {} }
            shoot(screen, "maxed$suffix") { MatrixContent(MatrixState(model, MatrixNodeId.IMPACT_DRIVER), scale, remember { ScrollState(0) }) {} }
            shoot(screen, "reborn-locked$suffix") { MatrixContent(MatrixState(model, MatrixNodeId.GLASS_ENGINE), scale, remember { ScrollState(0) }) {} }
            shoot(screen, "prerequisite$suffix") { MatrixContent(MatrixState(model, MatrixNodeId.WIDE_OFFER), scale, remember { ScrollState(0) }) {} }
        }
        shoot(screen, "short") {
            val poor = MatrixProfileSnapshot(PlayerEconomy(matter = 40L), MatrixProgress(ranks), rebirthRank = 1).toRenderModel(CatalogMatrixNodes)
            MatrixContent(MatrixState(poor, MatrixNodeId.NANOFORGE), 1.25f, remember { ScrollState(0) }) {}
        }
        shoot(screen, "reset-armed") {
            MatrixContent(MatrixState(model, MatrixNodeId.IMPACT_DRIVER, resetArmed = true), 1.25f, remember { ScrollState(0) }) {}
        }
        shoot(screen, "flash-invert", advanceMs = 16) {
            MatrixContent(MatrixState(model, MatrixNodeId.CRYO_LOOP, flash = MatrixPurchaseFlash(MatrixNodeId.CRYO_LOOP, 1)), 1.25f,
                remember { ScrollState(0) }) {}
        }
        shoot(screen, "text100") { MatrixContent(MatrixState(model, MatrixNodeId.PERPETUAL_DRIFT), 1f, remember { ScrollState(0) }) {} }
        val phones = listOf(844 to 390, 390 to 844)
        shoot(screen, "focused-last", at = phones, focus = "profile-matrix-node-${MatrixNodeId.PROSPECTOR}") {
            MatrixContent(MatrixState(model, MatrixNodeId.PROSPECTOR), 1.25f, remember { ScrollState(0) }) {}
        }
    }

    private fun rebirth(target: Int, canAdvance: Boolean = true) = RebirthRenderModel(
        current = rebirthProfile((target - 1).coerceAtLeast(0)),
        focus = rebirthProfile(target.coerceAtMost(10)),
        highestCleared = target - 1,
        eligible = canAdvance,
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
            val max = RebirthRenderModel(rebirthProfile(10), rebirthProfile(10), 10, true, 1_924L, 0, 10, catalogRebirthBuild(10, 10))
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
private fun rebirthProfile(tier: Int): RebirthProfile = kinetickk.ball.content.api.defaultRebirthProfile(tier)
