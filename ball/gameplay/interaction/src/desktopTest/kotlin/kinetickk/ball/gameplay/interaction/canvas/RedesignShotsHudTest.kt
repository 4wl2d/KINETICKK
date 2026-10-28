// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.interaction.fixtureWeapon
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.render.ApexProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.SkillBarProjection
import kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureRelics
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.localeList
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test

/**
 * Local visual QA harness for the in-run HUD (SPEC §12). With `KK_SHOTS=1` every state draws at every size, language,
 * text size and colour-vision palette into build/redesign-shots/hud. Otherwise the run is a smoke: each state draws
 * once (1440 × 810, Russian, text size 125 %, Default palette) and nothing is written, so a state that fails to draw
 * still fails.
 */
class RedesignShotsHudTest {
    private val fullShots = System.getenv("KK_SHOTS") == "1"

    /** The whole list with `KK_SHOTS=1`, else its first entry. */
    private fun <T> List<T>.matrix(): List<T> = if (fullShots) this else take(1)

    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
    private val output = File(root, "ball/gameplay/interaction/build/redesign-shots/hud")

    @Test
    fun renderHudStates() {
        if (fullShots) output.mkdirs()
        // KK_SHOTS_SIZES ("1000x700,640x360") replaces the three reference sizes.
        val sizes = (System.getenv("KK_SHOTS_SIZES")?.split(',')?.map { it.substringBefore('x').toInt() to it.substringAfter('x').toInt() }
            ?: listOf(1440 to 810, 844 to 390, 390 to 844)).matrix()
        val only = System.getenv("KK_SHOTS_ONLY")
        // The game's default text size (1.25) and the setting's maximum (1.75, suffix -175).
        val scales = (System.getenv("KK_SHOTS_SCALES") ?: "1.25,1.75").split(',').map { it.toFloat() }.matrix()
        // Color vision palettes for the threat-meter states (suffix -mono / -protan), at the default size.
        val palettes = listOf("" to KkRolePalette.Default, "-mono" to KkRolePalette.Mono, "-protan" to KkRolePalette.Protan).matrix()
        val paletteStates = setOf(Shot.CRITICAL, Shot.OVERHEAT, Shot.ELITE, Shot.ARCHITECT, Shot.POLARITY, Shot.DASH)
        for ((width, height) in sizes) {
            val base = baseModel(width, height)
            for (language in AppLanguage.entries.matrix()) for (textScale in scales) for ((suffix, roles) in palettes) {
                if (roles != KkRolePalette.Default && textScale != 1.25f) continue
                val measurer = CanvasTextMeasurer(
                    TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                    textScale, language, typography.copy(localeList = language.localeList()), roles,
                )
                for (state in Shot.entries) {
                    if (only != null && state.name.lowercase() !in only.split(',')) continue
                    if (roles != KkRolePalette.Default && state !in paletteStates) continue
                    val scene = scene(state, base, width, height)
                    val bitmap = ImageBitmap(width, height)
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                        drawGameplay(scene.model, scene.fx, measurer, scene.time, null, hudMemory = scene.memory,
                            trialInfoOpen = scene.focused, controls = scene.controls)
                        // The Compose Build button sits beside pause; outline its bounds for review.
                        val build = kinetickk.ball.gameplay.interaction.layout.runningBuildButtonBounds(size.width, size.height, 1f)
                        drawRect(kinetickk.foundation.design.Kk.Ink3, build.topLeft + androidx.compose.ui.geometry.Offset(4f, 6f),
                            Size(build.width - 8f, build.height - 12f))
                    }
                    val lang = if (language == AppLanguage.English) "en" else "ru"
                    val scaleTag = if (textScale == 1.25f) "" else "-" + (textScale * 100).toInt()
                    write(bitmap, File(output, "hud-${state.name.lowercase()}-${width}x$height-$lang$scaleTag$suffix.png"))
                }
            }
        }
    }

    private class Scene(
        val model: GameplayRenderModel,
        val fx: VisualFxProjection,
        val memory: HudPresentationMemory,
        val time: Float,
        val focused: Boolean,
        val controls: kinetickk.ball.gameplay.interaction.input.RunningControlPressLog? = null,
    )

    private enum class Shot {
        CRUISE, DASH, OVERHEAT, POLARITY, OVERDRIVE, CRITICAL, ELITE, ARCHITECT, TRIAL, TRIAL_INFO, FEED, FEED_ANOMALY,
        // Review states: interleaved synergies, off-screen markers with a boss alive, an orbit trial near the boss row.
        SYNERGY, ELITE_MARKERS, ARCHITECT_TRIAL,
        // The Rebirth strip: the tier plate with five and with every mutator sigil.
        REBIRTH_5, REBIRTH_10,
        // The level badge's upper tiers and the APEX at the cap in each Apex state; a tier change 300 ms and the
        // Apex unlock 400 ms into their one-shots.
        LEVEL_T4, LEVEL_T5, APEX_COOLING, APEX_READY, APEX_ACTIVE, TIER_UP, APEX_UNLOCK,
        // The active row with key caps (keyboard in use) at the cap: Z ready at rank III, X cooling 60 % at rank II
        // with a refund flash, C empty; then every slot bound with two charges, a running and an in-flight active; then
        // the element-less Catalyze ready at rank II in Z and Dual Infusion cooling at rank I in X (the kinetic role)
        // beside an empty C.
        SKILLS_MIXED, SKILLS_FULL, SKILLS_KINETIC,
        // The trial panel of the other families: a gate race on its clock (silver still in reach), the brakeless
        // challenge, the bounty duel and the summoned sigil; each with its edge markers, the race's next gate off screen.
        TRIAL_RACE, TRIAL_CHALLENGE, TRIAL_BOUNTY, TRIAL_SIGIL,
        // Baby mode: the soft tether and cursor arc with the pacifier plate alone at tier 0, then before the tier plate
        // and its sigils at tier 3.
        EASY, EASY_TIER,
        // A mini-boss's narrow block (the Throne in its second phase, three parts) and a secret boss's wide one (the
        // Antecedent in its third phase, four parts, hardened against Cryo so its weakness fills the icon slot).
        MINIBOSS, SECRET_BOSS,
        // The dungeon panel: sealed in a Derelict Station's bay with its garrison, two of four rooms clear, the bay's cell
        // at its pulse's peak, under the scrim and the vignette.
        DUNGEON,
        // A dimension visit: the plate in the Rebirth slot and the objective's panel; the Zenith's course at pace with its
        // next ring's arc half into the split, the Nadir's harvest in its last seconds with the Core scorching (the heat
        // edge) and an ember just thrown from its kill, the Archive's duel with the Antecedent; then the shutter 150 ms
        // into the Zenith.
        DIMENSION_ZENITH, DIMENSION_NADIR, DIMENSION_ARCHIVE, DIMENSION_SHUTTER,
    }

    private fun level(shot: Shot, values: Values): Int = when (shot) {
        Shot.LEVEL_T4 -> 34
        Shot.LEVEL_T5 -> 41
        Shot.APEX_COOLING, Shot.APEX_READY, Shot.APEX_ACTIVE, Shot.APEX_UNLOCK, Shot.SKILLS_MIXED, Shot.SKILLS_FULL,
        Shot.SKILLS_KINETIC -> LEVEL_CAP
        Shot.TIER_UP -> 20
        else -> values.level
    }

    private fun apex(shot: Shot): ApexProjection = when (shot) {
        // Charged 40 %.
        Shot.APEX_COOLING -> ApexProjection(unlocked = true, cooldown01 = 0.6f)
        Shot.APEX_READY, Shot.APEX_UNLOCK, Shot.SKILLS_FULL -> ApexProjection(unlocked = true, ready = true)
        Shot.SKILLS_MIXED, Shot.SKILLS_KINETIC -> ApexProjection(unlocked = true, cooldown01 = 0.3f)
        Shot.APEX_ACTIVE -> ApexProjection(unlocked = true, active01 = 0.6f)
        else -> ApexProjection.LOCKED
    }

    private data class Values(
        val core: Pair<Float, Float>, val sing: Pair<Float, Float>, val speed: Float, val heat: Float, val polarity: Float,
        val overdrive: Float, val integrity: Float, val shield: Float, val chain: Int, val chainWindow: Float, val time: Float,
        val keys: Int, val level: Int,
    )

    private fun values(shot: Shot): Values = when (shot) {
        Shot.DASH -> Values(700f to 430f, 1010f to 262f, 886f, 71f, 0.74f, 64f, 142f, 40f, 14, 0.9f, 464f, 1, 14)
        Shot.OVERHEAT -> Values(560f to 500f, 820f to 330f, 640f, 100f, 0.8f, 40f, 131f, 20f, 7, 0.4f, 483f, 1, 15)
        Shot.POLARITY -> Values(1040f to 560f, 1404f to 470f, 380f, 22f, 0.18f, 51f, 142f, 40f, 5, 0.3f, 500f, 1, 15)
        Shot.OVERDRIVE -> Values(720f to 420f, 1020f to 250f, 790f, 34f, 0.9f, 72f, 150f, 60f, 31, 0.96f, 552f, 2, 17)
        Shot.CRITICAL -> Values(660f to 440f, 712f to 398f, 300f, 55f, 0.64f, 22f, 18f, 0f, 0, 0f, 631f, 2, 19)
        Shot.ELITE -> Values(520f to 520f, 760f to 380f, 560f, 40f, 0.84f, 47f, 126f, 40f, 9, 0.55f, 310f, 0, 11)
        Shot.ARCHITECT -> Values(560f to 610f, 800f to 520f, 700f, 46f, 0.78f, 66f, 97f, 20f, 4, 0.35f, 762f, 2, 24)
        else -> Values(640f to 470f, 905f to 300f, 412f, 28f, 0.86f, 58f, 142f, 40f, 12, 0.62f, 462f, 1, 14)
    }

    private fun scene(shot: Shot, base: GameplayRenderModel, width: Int, height: Int): Scene {
        val v = values(shot)
        val sx = width / 1440f
        val sy = height / 810f
        val coreScreenX = v.core.first * sx
        val coreScreenY = v.core.second * sy
        val pointerX = (v.sing.first * sx).coerceIn(4f, width - 4f)
        val pointerY = (v.sing.second * sy).coerceIn(4f, height - 4f)
        val coreX = coreScreenX - width * 0.5f
        val coreY = coreScreenY - height * 0.5f
        val angle = atan2(pointerY - coreScreenY, pointerX - coreScreenX)
        val enemies = when (shot) {
            Shot.ELITE, Shot.ELITE_MARKERS -> listOf(enemy(1, EnemyType.ELITE, 975f * sx - width * 0.5f, 325f * sy - height * 0.5f, 0.64f, 38f))
            Shot.ARCHITECT, Shot.ARCHITECT_TRIAL -> listOf(enemy(2, EnemyType.ARCHITECT, 710f * sx - width * 0.5f, 290f * sy - height * 0.5f, 0.53f, 74f))
            Shot.MINIBOSS, Shot.SECRET_BOSS -> listOf(rankBoss(shot, 975f * sx - width * 0.5f, 325f * sy - height * 0.5f, if (shot == Shot.MINIBOSS) 0.48f else 0.41f))
            else -> emptyList()
        }
        val trial = shot == Shot.TRIAL || shot == Shot.TRIAL_INFO
        val points = when {
            trial -> listOf(
                SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", coreX, coreY, true, 14.2f, -1, 0.65f,
                    immutableListOf(), 0f, 0f),
            )
            // Offers far above, above-left and above-right of the view: their edge markers.
            shot == Shot.ELITE_MARKERS -> listOf(
                SiteProjection(SiteKind.SEALED_ANOMALY, "Sealed anomaly", 0f, -3_000f, false, 20f, -1, 0f,
                    immutableListOf(), 0f, 0f),
                SiteProjection(SiteKind.RESONANT_CIRCUIT, "Resonant circuit", -1_400f, -2_600f, false, 20f, -1, 0f,
                    immutableListOf(), 0f, 0f),
                SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", 1_400f, -2_600f, false, 20f, -1, 0f,
                    immutableListOf(), 0f, 0f),
            )
            shot == Shot.TRIAL_RACE || shot == Shot.TRIAL_CHALLENGE || shot == Shot.TRIAL_BOUNTY || shot == Shot.TRIAL_SIGIL -> {
                val kind = when (shot) {
                    Shot.TRIAL_RACE -> SiteKind.GATE_RACE
                    Shot.TRIAL_CHALLENGE -> SiteKind.BRAKELESS_TRIAL
                    Shot.TRIAL_BOUNTY -> SiteKind.BOUNTY_ARENA
                    else -> SiteKind.FAR_SIGIL
                }
                val running = trialSites(coreX, coreY).single { it.kind == kind }
                listOf(running, SiteProjection(SiteKind.UNTOUCHED_TRIAL, "Untouched", 1_400f, -2_600f, false, 20f, -1, 0f,
                    immutableListOf(), 0f, 0f))
            }
            // An orbit whose ring center sits just under the top band: its timer must avoid the boss block.
            shot == Shot.ARCHITECT_TRIAL -> listOf(
                SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", coreX, coreY - 120f, true, 14.2f, -1, 0.4f,
                    immutableListOf(), 0f, 0f),
            )
            else -> emptyList()
        }
        val model = base.with(
            "coreX" to coreX, "coreY" to coreY, "cameraX" to 0f, "cameraY" to 0f,
            "pointerX" to pointerX, "pointerY" to pointerY,
            "velocityX" to cos(angle) * v.speed, "velocityY" to sin(angle) * v.speed,
            "heat" to v.heat, "overheated" to (v.heat >= 100f),
            "dashPhaseTime" to if (shot == Shot.DASH) 0.16f else 0f,
            "hp" to v.integrity, "maxHp" to 180f, "shield" to v.shield, "maxShield" to 60f,
            "level" to level(shot, v), "maxLevel" to LEVEL_CAP, "apex" to apex(shot), "data" to 77, "nextLevelData" to 100, "keys" to v.keys,
            "combo" to v.chain, "comboTime" to v.chainWindow * 2.8f, "comboWindow" to 2.8f,
            "runMatter" to if (shot == Shot.ARCHITECT) 1_234_567L else 486L, "elapsed" to v.time, "polarityStability" to v.polarity,
            "overdriveCharge" to v.overdrive, "overdriveTime" to if (shot == Shot.OVERDRIVE) 5f else 0f,
            "weapon" to WeaponId.FLUX_WAKE, "weaponLevel" to 7,
            // The synergy review interleaves two links: Gyre Engine and Stormheart harmonize (0, 2) while
            // Neutron Heart and Event Horizon form Black Star (1, 3).
            "equippedRelics" to (if (shot == Shot.SYNERGY) listOf(EquippedRelic(RelicId.GYRE_ENGINE, 1),
                EquippedRelic(RelicId.NEUTRON_HEART, 1), EquippedRelic(RelicId.STORMHEART_CAPACITOR, 1), EquippedRelic(RelicId.EVENT_HORIZON, 1))
            else listOf(EquippedRelic(RelicId.entries[0], 2), EquippedRelic(RelicId.entries[7], 1),
                EquippedRelic(RelicId.entries[1], 1))).toImmutableList(),
            "enemies" to enemies.toImmutableList(),
            "sites" to points.toImmutableList(),
            "trial" to points.firstOrNull { it.active }?.let { siteTrial(it) },
            "message" to when (shot) {
                Shot.FEED -> "ELITE SIGNAL"
                Shot.FEED_ANOMALY -> "TWO ANOMALIES DETECTED // CHOOSE A COURSE"
                else -> ""
            },
            "messageTime" to if (shot == Shot.FEED || shot == Shot.FEED_ANOMALY) 1.5f else 0f,
            "screenShake" to 0f,
            "skillBar" to when (shot) {
                Shot.SKILLS_MIXED -> MIXED_SKILL_BAR.let { bar ->
                    SkillBarProjection(immutableListOf(bar.slot(0), bar.slot(1).copy(refunded = 1f), bar.slot(2)))
                }
                Shot.SKILLS_FULL -> FULL_SKILL_BAR
                Shot.SKILLS_KINETIC -> KINETIC_SKILL_BAR
                else -> SkillBarProjection.EMPTY
            },
            "rebirthLevel" to when (shot) {
                Shot.REBIRTH_5 -> 5
                Shot.REBIRTH_10 -> 10
                Shot.EASY_TIER -> 3
                else -> 0
            },
            "easyMode" to (shot == Shot.EASY || shot == Shot.EASY_TIER),
        ).let { if (shot == Shot.DUNGEON) dungeonShot(it) else it }.let { dimensionShot(shot, it) ?: it }
        val time = 3.37f
        val memory = HudPresentationMemory()
        val boss = BossTarget()
        val pre = when (shot) {
            Shot.POLARITY -> model.with("polarityStability" to v.polarity + 0.02f, "elapsed" to v.time - 0.2f)
            Shot.OVERDRIVE -> model.with("overdriveTime" to 7f)
            Shot.CRITICAL -> model.with("hp" to 30f)
            Shot.CRUISE -> model.with("hp" to 151f)
            Shot.ELITE, Shot.ELITE_MARKERS -> model.with("enemies" to listOf(enemy(1, EnemyType.ELITE, 0f, 0f, 0.7f, 38f)).toImmutableList())
            Shot.ARCHITECT, Shot.ARCHITECT_TRIAL -> model.with("enemies" to listOf(enemy(2, EnemyType.ARCHITECT, 0f, 0f, 0.62f, 74f)).toImmutableList())
            Shot.MINIBOSS, Shot.SECRET_BOSS -> model.with("enemies" to listOf(rankBoss(shot, 0f, 0f, 0.55f)).toImmutableList())
            // Before the drawn frame X had no refund: the frame plays its refund flash.
            Shot.SKILLS_MIXED -> model.with("skillBar" to MIXED_SKILL_BAR)
            else -> model
        }
        when (shot) {
            // The one-shot started 300 / 400 ms before the drawn frame: the frame before it shows the lower tier.
            Shot.TIER_UP, Shot.APEX_UNLOCK -> {
                val lower = model.with("level" to level(shot, v) - 1, "apex" to ApexProjection.LOCKED)
                val lead = if (shot == Shot.TIER_UP) 0.3f else 0.4f
                memory.observe(lower, time - lead - 0.1f, boss.select(lower))
                memory.observe(model, time - lead, boss.select(model))
            }
            // The layer changed 150 ms before the drawn frame: the shutter is a third of the way across.
            Shot.DIMENSION_SHUTTER -> {
                val realspace = model.with("dimension" to kinetickk.ball.gameplay.nucleus.render.DimensionProjection.REALSPACE, "trial" to null)
                memory.observe(realspace, time - 0.3f, boss.select(realspace))
                memory.observe(model, time - 0.15f, boss.select(model))
            }
            else -> memory.observe(pre, time - 0.2f, boss.select(pre))
        }
        val fx = if (shot == Shot.FEED || shot == Shot.FEED_ANOMALY) VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(
            BuildNotificationProjection("Neon Ram", immutableListOf("Impact damage +0.05", "Weapon power +0.04", "+ Vector maneuver"), 5f),
            BuildNotificationProjection("Ghost Vector", immutableListOf("Dash power +12"), 3.2f),
        )) else VisualFxProjection.EMPTY
        val controls = if (shot == Shot.SKILLS_MIXED || shot == Shot.SKILLS_FULL || shot == Shot.SKILLS_KINETIC) keyboardControls() else null
        return Scene(model, fx, memory, time, shot == Shot.TRIAL_INFO, controls)
    }

    private companion object {
        /** The content's run level cap. */
        const val LEVEL_CAP = 42

        /** The element-less actives in Z and X, which draw in the kinetic role as their cards do, beside an empty C. */
        val KINETIC_SKILL_BAR = SkillBarProjection(immutableListOf(
            SkillSlotProjection(skill = SkillId.CATALYZE.ordinal, rank = 2, ready = true),
            SkillSlotProjection(skill = SkillId.DUAL_INFUSION.ordinal, rank = 1, cooldown01 = 0.4f),
            SkillSlotProjection.EMPTY,
        ))
    }

    private fun enemy(id: Int, type: EnemyType, x: Float, y: Float, integrity: Float, radius: Float) =
        EnemyProjection(id, type, x, y, 0f, 0f, 1000f * integrity, 1000f, radius, 0f, 0f, 0f, 0f, x, y, false)

    /** [Shot.MINIBOSS]'s Throne (second phase, two arcs) or [Shot.SECRET_BOSS]'s Antecedent (third phase, two arcs). */
    private fun rankBoss(shot: Shot, x: Float, y: Float, integrity: Float): EnemyProjection {
        val mini = shot == Shot.MINIBOSS
        val aspect = if (mini) kinetickk.ball.content.api.StatusElement.VOID else kinetickk.ball.content.api.StatusElement.CRYO
        val weakness = if (mini) kinetickk.ball.content.api.StatusElement.SONIC else kinetickk.ball.content.api.StatusElement.THERMAL
        val affinity = kinetickk.ball.gameplay.nucleus.render.EnemyAffinity.encode(aspect.ordinal, mini, weakness.ordinal, armored = true,
            fragile = false, shifting = false, phase = if (mini) 1 else 2)
        return enemy(3, if (mini) EnemyType.THRONE else EnemyType.ANTECEDENT, x, y, integrity, if (mini) 48f else 58f)
            .copy(affinityBits = affinity, charges = 2, facing = 0.5f)
    }

    /** The dimension shots' models: [model] inside the shot's visit with the layer's natives (null for every other shot). */
    private fun dimensionShot(shot: Shot, model: GameplayRenderModel): GameplayRenderModel? {
        val layer = when (shot) {
            Shot.DIMENSION_ZENITH, Shot.DIMENSION_SHUTTER -> kinetickk.ball.content.api.WorldLayer.ZENITH
            Shot.DIMENSION_NADIR -> kinetickk.ball.content.api.WorldLayer.NADIR
            Shot.DIMENSION_ARCHIVE -> kinetickk.ball.content.api.WorldLayer.ARCHIVE
            else -> return null
        }
        val x = model.coreX
        val y = model.coreY
        fun native(id: Int, type: EnemyType, dx: Float, dy: Float, radius: Float) = enemy(id, type, x + dx, y + dy, 1f, radius)
        val visit = when (layer) {
            kinetickk.ball.content.api.WorldLayer.ZENITH -> dimensionVisit(layer, x, y, course = listOf(420f to -150f, 900f to 120f),
                ambient = listOf(-380f to 260f), progress = 3, pace = 900f, splitRemaining = 1.3f, splitDuration = 2.6f)
            kinetickk.ball.content.api.WorldLayer.NADIR -> dimensionVisit(layer, x, y, embers = listOf(160f to -90f, -220f to 140f, 300f to 210f),
                progress = 7, scorching = 1f, thrownFrom = listOf(-60f to 120f), emberAge = 0.1f).copy(remaining = 8.2f)
            else -> dimensionVisit(layer, x, y).copy(remaining = 71f)
        }
        val natives = when (layer) {
            kinetickk.ball.content.api.WorldLayer.ZENITH -> listOf(native(41, EnemyType.SERAPH, 330f, 220f, 22f), native(42, EnemyType.CHOIR, -300f, -200f, 24f))
            kinetickk.ball.content.api.WorldLayer.NADIR -> listOf(native(43, EnemyType.IMP, 260f, -200f, 18f), native(44, EnemyType.HOUND, -260f, 60f, 16f))
            else -> listOf(rankBoss(Shot.SECRET_BOSS, x + 360f, y - 160f, 0.62f), native(45, EnemyType.CIPHER, -300f, 180f, 18f))
        }
        val speed = if (layer == kinetickk.ball.content.api.WorldLayer.NADIR) 520f else 1_150f
        return model.inVisit(visit, bossShare = 0.38f).with(
            "enemies" to natives.toImmutableList(),
            "velocityX" to speed, "velocityY" to 0f,
            "rebirthLevel" to 4,
        )
    }

    /** [Shot.DUNGEON]'s model: [model] with the Core sealed in a Derelict Station's bay among its garrison, depth full. */
    private fun dungeonShot(model: GameplayRenderModel): GameplayRenderModel {
        val station = StructureFixture(model.content.structures.template(StructureTemplateId.DERELICT_STATION),
            model.coreX - 750f, model.coreY - 400f, clearedRooms = 0b0011, engagedRoom = 3)
        fun member(id: Int, type: EnemyType, dx: Float, dy: Float, radius: Float) =
            enemy(id, type, model.coreX + dx, model.coreY + dy, 1f, radius)
        return model.withDungeon(station).with(
            "previousCoreX" to model.coreX, "previousCoreY" to model.coreY,
            // The brackets' and the cell's pulse at its peak.
            "elapsed" to ((kotlin.math.PI / 2.0 + 2.0 * kotlin.math.PI * 441.0) / 6.0).toFloat(),
            "enemies" to listOf(
                member(31, EnemyType.CRAWLER, -290f, 250f, 18f), member(32, EnemyType.CRAWLER, 290f, -240f, 18f),
                member(33, EnemyType.CRAWLER, 300f, 220f, 18f), member(34, EnemyType.DRIFTER, 120f, 110f, 16f),
                member(35, EnemyType.SHOOTER, -160f, -120f, 18f), member(36, EnemyType.SENTRY, 40f, -260f, 20f),
            ).toImmutableList(),
        )
    }

    private fun GameplayRenderModel.with(vararg overrides: Pair<String, Any?>): GameplayRenderModel {
        val map = overrides.toMap()
        val type = GameplayRenderModel::class.java
        val constructor = type.declaredConstructors.filter { !it.isSynthetic }.maxBy { it.parameterCount }
        val fields = type.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        check(fields.size == constructor.parameterCount)
        val args = fields.map { field ->
            field.isAccessible = true
            if (field.name in map) map[field.name] else field.get(this)
        }.toTypedArray()
        constructor.isAccessible = true
        return constructor.newInstance(*args) as GameplayRenderModel
    }

    private fun baseModel(width: Int, height: Int): GameplayRenderModel {
        val content = shotContent()
        val profile = PlayerProfile(preferences = PlayerPreferences(language = AppLanguage.English))
        val initial = GameplayState.initial(RunId(1), content)
        val started = (GameplayNucleus.decide(
            initial,
            GameplayNucleusPulse.StartRun,
            GameplayContext(start = GameplayStartContext.Ready(GameplayStartInputs(
                content,
                GameplayProfileSnapshot(profile.preferences, profile.economy, profile.loadout, profile.matrix, profile.collection, profile.rebirthProgress),
                seed = 731_991,
            ))),
        ) as GameplayDecision.Accepted).frame.nextState
        val resized = (GameplayNucleus.decide(
            started,
            GameplayNucleusPulse.Intent(GameplayInteractionPulse.ViewportChanged.fromValidated(width.toFloat(), height.toFloat(), 1f)),
        ) as GameplayDecision.Accepted).frame.nextState
        return requireNotNull(GameplayNucleus.renderSnapshot(resized).renderModel)
    }

    private val typography: InterfaceTypography by lazy {
        val directory = File(root, "foundation/design/src/commonMain/composeResources/font")
        fun font(name: String, weight: FontWeight, style: FontStyle = FontStyle.Normal) = Font(File(directory, "$name.ttf"), weight, style)
        val cond = FontFamily(
            font("kk_cond_black_italic", FontWeight.Black, FontStyle.Italic),
            font("kk_cond_black", FontWeight.Black),
            font("kk_cond_extrabold", FontWeight.ExtraBold),
        )
        InterfaceTypography(
            body = FontFamily(font("kk_body_regular", FontWeight.Normal), font("kk_body_medium", FontWeight.Medium), font("kk_body_bold", FontWeight.Bold)),
            display = FontFamily(font("kk_cond_black_italic", FontWeight.Bold), font("kk_cond_extrabold", FontWeight.Normal)),
            wide = FontFamily(font("kk_wide_black", FontWeight.Black), font("kk_wide_bold", FontWeight.Bold)),
            cond = cond,
            label = cond,
            mono = FontFamily(font("kk_mono_medium", FontWeight.Medium), font("kk_mono_bold", FontWeight.Bold)),
        )
    }

    private fun write(bitmap: ImageBitmap, file: File) {
        if (!fullShots) return
        val pixels = bitmap.toPixelMap()
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, pixels[x, y].toArgb())
        ImageIO.write(image, "png", file)
    }

    @Suppress("unused")
    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>) = hypot(a.first - b.first, a.second - b.second)
}

private fun shotContent() = GameplayContentSnapshot(
    version = KINETICKK_CONTENT_VERSION,
    items = listOf("Cinder", "Neon", "Gravitic").mapIndexed { index, component ->
        val name = "$component Ram"
        ItemDefinition(
            index, name,
            "$name binds the Impact family to a $component component: +5% Impact damage and +4% Weapon power per stack (max 8).",
            if (index == 1) ItemRarity.UNCOMMON else ItemRarity.COMMON,
            immutableListOf(BuildTag.RAM),
            stats = immutableListOf(StatDelta(ItemEffect.IMPACT_DAMAGE, 0.05f), StatDelta(ItemEffect.WEAPON_POWER, 0.04f)),
            maxStacks = 3, unlockLevel = 1,
        )
    }.toImmutableList(),
    weapons = WeaponId.entries.map { fixtureWeapon(it) }.toImmutableList(),
    weaponMasteries = WeaponMastery.entries.toImmutableList(),
    relics = rewardFixtureRelics.toImmutableList(),
    // Every tier's profile, so the Rebirth strip states show the catalog's mutators.
    rebirth = RebirthPolicySnapshot(
        minimumLevel = 0, maximumLevel = 10,
        profiles = (0..10).map { kinetickk.ball.content.api.defaultRebirthProfile(it) }.toImmutableList(),
        maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
    ),
    relicPolicy = RelicPolicy(4, 5),
)
