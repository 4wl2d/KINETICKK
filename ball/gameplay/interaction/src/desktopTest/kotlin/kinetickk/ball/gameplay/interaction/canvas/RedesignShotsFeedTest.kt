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
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.StatDelta
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
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
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
 * Local visual QA harness for the in-run feed (SPEC §12). With `KK_SHOTS=1` every state draws at every size, in both
 * languages and at the default and the largest text size into build/redesign-shots/feed. Otherwise the run is a
 * smoke: each state draws once (the first size, Russian, text size 125 %) and nothing is written, so a state that
 * fails to draw still fails.
 */
class RedesignShotsFeedTest {
    private val fullShots = System.getenv("KK_SHOTS") == "1"

    /** The whole list with `KK_SHOTS=1`, else its first entry. */
    private fun <T> List<T>.matrix(): List<T> = if (fullShots) this else take(1)

    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
    private val output = File(root, "ball/gameplay/interaction/build/redesign-shots/feed")

    @Test
    fun renderHudStates() {
        if (fullShots) output.mkdirs()
        val sizes = (System.getenv("KK_SHOTS_SIZES")?.split(',')?.map { it.substringBefore('x').toInt() to it.substringAfter('x').toInt() }
            ?: listOf(1440 to 810, 844 to 390, 390 to 844)).matrix()
        val only = System.getenv("KK_SHOTS_ONLY")
        for ((width, height) in sizes) {
            val base = baseModel(width, height)
            val scales = (System.getenv("KK_SHOTS_SCALES")?.split(',')?.map { it.toFloat() } ?: listOf(1.25f, 1.75f)).matrix()
            for (language in AppLanguage.entries.matrix()) for (textScale in scales) {
                val measurer = CanvasTextMeasurer(
                    TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                    textScale, language, typography.copy(localeList = language.localeList()), KkRolePalette.Default,
                )
                for (state in Shot.entries) {
                    if (only != null && state.name.lowercase() !in only.split(',')) continue
                    val scene = scene(state, base, width, height)
                    val bitmap = ImageBitmap(width, height)
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                        drawGameplay(scene.model, scene.fx, measurer, scene.time, null, hudMemory = scene.memory,
                            trialInfoOpen = scene.focused)
                        // The Compose Build button sits beside pause; outline its bounds for review.
                        val build = kinetickk.ball.gameplay.interaction.layout.runningBuildButtonBounds(size.width, size.height, 1f)
                        drawRect(kinetickk.foundation.design.Kk.Ink3, build.topLeft + androidx.compose.ui.geometry.Offset(4f, 6f),
                            Size(build.width - 8f, build.height - 12f))
                    }
                    val lang = if (language == AppLanguage.English) "en" else "ru"
                    val scale = "t" + Math.round(textScale * 100f)
                    write(bitmap, File(output, "feed-${state.name.lowercase()}-$scale-${width}x$height-$lang.png"))
                }
            }
        }
    }

    private class Scene(val model: GameplayRenderModel, val fx: VisualFxProjection, val memory: HudPresentationMemory, val time: Float, val focused: Boolean)

    private enum class Shot { FEED, FEED_ANOMALY, FEED_LEVEL, FEED_REPLACE, FEED_FADE, FEED_TRIAL, FEED_SYNERGY4_TRIAL, FEED_SYNERGY4_WIDE, FEED_SYNERGY4_BOSS, FEED_SYNERGY4_ARCHITECT }

    private data class Values(
        val core: Pair<Float, Float>, val sing: Pair<Float, Float>, val speed: Float, val heat: Float, val polarity: Float,
        val overdrive: Float, val integrity: Float, val shield: Float, val chain: Int, val chainWindow: Float, val time: Float,
        val keys: Int, val level: Int,
    )

    // The Core at the screen center, where the camera keeps it.
    @Suppress("UNUSED_PARAMETER")
    private fun values(shot: Shot): Values = Values(720f to 405f, 985f to 250f, 412f, 28f, 0.86f, 58f, 142f, 40f, 12, 0.62f, 462f, 1, 14)

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
        // An elite boss (its portrait row pushes the feed down) off to the Core's upper left.
        val enemies = when (shot) {
            Shot.FEED_SYNERGY4_BOSS -> listOf(enemy(4, EnemyType.ELITE, coreX - 90f, coreY - 150f, 0.5f, 24f))
            // The Architect's bar is taller: the landscape feed docks under it too.
            Shot.FEED_SYNERGY4_ARCHITECT -> listOf(enemy(5, EnemyType.ARCHITECT, coreX - 90f, coreY - 170f, 0.6f, 40f))
            else -> emptyList()
        }
        val trial = shot == Shot.FEED_TRIAL || shot == Shot.FEED_SYNERGY4_TRIAL
        val points = if (trial) listOf(
            SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", coreX, coreY, true, 14.2f, -1, 0.65f,
                immutableListOf(), 0f, 0f),
        ) else emptyList()
        val model = base.with(
            "coreX" to coreX, "coreY" to coreY, "cameraX" to 0f, "cameraY" to 0f,
            "pointerX" to pointerX, "pointerY" to pointerY,
            "velocityX" to cos(angle) * v.speed, "velocityY" to sin(angle) * v.speed,
            "heat" to v.heat, "overheated" to (v.heat >= 100f),
            "dashPhaseTime" to 0f,
            "hp" to v.integrity, "maxHp" to 180f, "shield" to v.shield, "maxShield" to 60f,
            "level" to v.level, "data" to 77, "nextLevelData" to 100, "keys" to v.keys,
            "combo" to v.chain, "comboTime" to v.chainWindow * 2.8f, "comboWindow" to 2.8f,
            "runMatter" to 486L, "elapsed" to v.time, "polarityStability" to v.polarity,
            "overdriveCharge" to v.overdrive, "overdriveTime" to 0f,
            "weapon" to WeaponId.FLUX_WAKE, "weaponLevel" to 7,
            // Vector, Gravitic, Vector: the two Vector relics link across the middle slot.
            "equippedRelics" to listOf(EquippedRelic(RelicId.entries[0], 2), EquippedRelic(RelicId.entries[7], 1),
                EquippedRelic(RelicId.entries[1], 1)).toImmutableList(),
            "enemies" to enemies.toImmutableList(),
            "sites" to points.toImmutableList(),
            "message" to when (shot) {
                Shot.FEED, Shot.FEED_FADE -> "ELITE SIGNAL"
                Shot.FEED_ANOMALY -> "TWO ANOMALIES DETECTED // CHOOSE A COURSE"
                Shot.FEED_LEVEL -> "FLUX WAKE // LEVEL 7"
                Shot.FEED_REPLACE -> "GHOST VECTOR // SLOT 2 BOUND"
                Shot.FEED_TRIAL -> "SINGULARITY SPEAR SYNCHRONIZED"
                Shot.FEED_SYNERGY4_TRIAL, Shot.FEED_SYNERGY4_BOSS, Shot.FEED_SYNERGY4_ARCHITECT -> "ELITE SIGNAL"
                Shot.FEED_SYNERGY4_WIDE -> ""
            },
            "messageTime" to when (shot) {
                Shot.FEED_FADE -> 0.22f
                Shot.FEED_SYNERGY4_WIDE -> 0f
                else -> 1.5f
            },
            "screenShake" to 0f,
        )
        val time = 3.37f
        val memory = HudPresentationMemory()
        val boss = BossTarget()
        val pre = model
        memory.observe(pre, time - 0.2f, boss.select(pre))
        val neonRam = BuildNotificationProjection("Neon Ram", immutableListOf("Impact damage +0.05", "Weapon power +0.04", "+ Vector maneuver"), 5f)
        val fx = VisualFxProjection.EMPTY.copy(buildNotifications = when (shot) {
            Shot.FEED, Shot.FEED_ANOMALY -> immutableListOf(neonRam, BuildNotificationProjection("Ghost Vector", immutableListOf("Dash power +12"), 3.2f))
            Shot.FEED_LEVEL -> immutableListOf(BuildNotificationProjection("Amplify Flux Wake", immutableListOf("Weapon power +0.04"), 5.6f))
            Shot.FEED_REPLACE -> immutableListOf(BuildNotificationProjection("Replace Ghost Vector",
                immutableListOf("Impact damage +0.05", "Weapon power +0.04", "Dash power +12", "\u2212 Vector maneuver", "+ Gravitic grouping"), 5.6f))
            // Fading out: toasts at 50 % and 20 % of their exit, the banner at half its fade.
            Shot.FEED_FADE -> immutableListOf(neonRam.copy(life = 0.3f), BuildNotificationProjection("Ghost Vector", immutableListOf("Dash power +12"), 0.12f))
            Shot.FEED_TRIAL -> immutableListOf(BuildNotificationProjection("Singularity Spear mastery advanced",
                immutableListOf("Critical chance +2.5%", "Damage reduction +3%", "+ Gravitic grouping"), 5f), neonRam)
            // A relic replace that ends two synergies and starts two.
            Shot.FEED_SYNERGY4_TRIAL, Shot.FEED_SYNERGY4_BOSS, Shot.FEED_SYNERGY4_ARCHITECT -> immutableListOf(BuildNotificationProjection("Replace Ghost Vector",
                immutableListOf("Impact damage +0.05", "+ Gravitic grouping", "+ Brake compression", "\u2212 Vector maneuver", "\u2212 Ghost mirror"), 5.6f))
            Shot.FEED_SYNERGY4_WIDE -> immutableListOf(BuildNotificationProjection("Replace Chroma Feedback",
                immutableListOf("Critical chance +2.5%", "Damage reduction +3%", "+ Gravitic grouping", "+ Brake compression",
                    "\u2212 Vector maneuver", "\u2212 Ghost mirror"), 5.6f))
        })
        return Scene(model, fx, memory, time, false)
    }

    private fun enemy(id: Int, type: EnemyType, x: Float, y: Float, integrity: Float, radius: Float) =
        EnemyProjection(id, type, x, y, 0f, 0f, 1000f * integrity, 1000f, radius, 0f, 0f, 0f, 0f, x, y, false)

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
    rebirth = RebirthPolicySnapshot(
        minimumLevel = 0, maximumLevel = 0,
        profiles = immutableListOf(RebirthProfile(
            0, "Baseline", 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0,
            120, 0.09f, 24f,
        )),
        maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
    ),
    relicPolicy = RelicPolicy(4, 5),
)
