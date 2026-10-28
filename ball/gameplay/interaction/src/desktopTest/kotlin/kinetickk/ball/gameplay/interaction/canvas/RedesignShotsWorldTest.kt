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
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.ANOMALY_KIND_COUNT
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.EliteAffix
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.interaction.fx.CoreRibbonProjection
import kinetickk.ball.gameplay.interaction.fx.DamageNumberProjection
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.MotionEchoProjection
import kinetickk.ball.gameplay.interaction.fx.ParticleProjection
import kinetickk.ball.gameplay.interaction.fx.ShockwaveProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.fx.WeaponArcProjection
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EventSignalProjection
import kinetickk.ball.gameplay.nucleus.render.EventStrikeProjection
import kinetickk.ball.gameplay.nucleus.render.WorldEventProjection
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.gameplay.nucleus.render.PickupProjection
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.SiteMarkProjection
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.ProjectileProjection
import kinetickk.ball.gameplay.nucleus.render.TotemProjection
import kinetickk.ball.gameplay.nucleus.render.TrailPointProjection
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeProjection
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.gameplay.nucleus.render.WeaponOrbitalProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureRelics
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test

/**
 * Local visual QA harness (SPEC §12). With `KK_SHOTS=1` it draws every scene and strip at every size in both
 * languages and writes build/redesign-shots/world/. Otherwise the run is a smoke: each scene draws once (the
 * first size, English), as do the marker strips, one flight and one shake strip, and nothing is written, so a
 * scene that fails to draw still fails.
 */
class RedesignShotsWorldTest {
    private val fullShots = System.getenv("KK_SHOTS") == "1"

    /** The whole list with `KK_SHOTS=1`, else its first entry. */
    private fun <T> List<T>.matrix(): List<T> = if (fullShots) this else take(1)

    @Test
    fun rendersWorldStates() {
        val sizes = listOf(1440 to 810, 844 to 390, 390 to 844)
        val languages = listOf(AppLanguage.English to "en", AppLanguage.Russian to "ru")
        val scenes = scenes()
        for ((width, height) in sizes.matrix()) for ((language, code) in languages.matrix()) for (scene in scenes) {
            val roles = scene.roles
            val measurer = CanvasTextMeasurer(
                ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
                scene.textScale, language, Fonts.typography, roles,
            )
            val model = scene.model(width.toFloat(), height.toFloat())
            val fx = scene.fx(model)
            val bitmap = ImageBitmap(width, height)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                drawRect(Kk.Ink)
                drawBackdrop(model, 0f, 0f, scene.time, roles)
                drawWorld(model, fx, 0f, 0f, measurer)
                drawScreenFx(model, scene.time, roles)
                if (scene.hud) drawHud(model, measurer, scene.time)
            }
            write(bitmap, File("build/redesign-shots/world/world-${scene.name}-${width}x$height-$code.png"))
        }
    }

    /**
     * Fix round 3, second pass (marker flicker): a crowded run (two offers and the totem 4 degrees
     * apart) drawn in consecutive frames, stacked top to bottom: turning in quarter-degree steps
     * (where the old batch placement flipped back and forth), and shaken 3 px either way at a
     * fixed direction after settling. Every frame must show the same arrangement, shifted at most a
     * sample while turning.
     */
    @Test
    fun rendersMarkerStrips() {
        val sizes = listOf(Triple(1440, 810, 272f), Triple(844, 390, 336f), Triple(390, 844, 302.5f))
        val languages = listOf(AppLanguage.English to "en", AppLanguage.Russian to "ru")
        for ((width, height, from) in sizes.matrix()) for ((language, code) in languages.matrix()) {
            val measurer = CanvasTextMeasurer(
                ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
                1.25f, language, Fonts.typography, KkRolePalette.Default,
            )
            fun frame(angle: Float, shakeX: Float, shakeY: Float): ImageBitmap {
                val targets = listOf(angle - 4f, angle, angle + 4f).map { degrees ->
                    val radians = Math.toRadians(degrees.toDouble())
                    (1_000f + 3_000f * cos(radians).toFloat()) to (1_000f + 3_000f * sin(radians).toFloat())
                }
                val model = model(width.toFloat(), height.toFloat(), points = listOf(
                    SiteProjection(SiteKind.SEALED_ANOMALY, "Sealed anomaly", targets[0].first, targets[0].second, false, 31f, 0, 0f, immutableListOf(), 0f, 0f),
                    SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", targets[1].first, targets[1].second, false, 31f, 0, 0f, immutableListOf(), 0f, 0f)),
                    totem = TotemProjection(targets[2].first, targets[2].second, 1f))
                val bitmap = ImageBitmap(width, height)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                    drawRect(Kk.Ink)
                    drawBackdrop(model, shakeX, shakeY, 3f, KkRolePalette.Default)
                    drawWorld(model, VisualFxProjection.EMPTY, shakeX, shakeY, measurer)
                    drawHud(model, measurer, 3f)
                }
                return bitmap
            }
            // Turning: settle a quarter degree before the strip, then five quarter-degree steps.
            repeat(6) { frame(from - 0.25f, 0f, 0f) }
            val turning = (0 until 5).map { frame(from + it * 0.25f, 0f, 0f) }
            writeStrip(turning, File("build/redesign-shots/world/world-fix3-markers-turn-${width}x$height-$code.png"))
            // Shaking straight up: settle, then six shaken frames.
            repeat(6) { frame(273.5f, 0f, 0f) }
            val shaken = (0 until 6).map { frame(273.5f, if (it % 2 == 0) 3f else -3f, if (it % 3 == 0) 2f else -2f) }
            writeStrip(shaken, File("build/redesign-shots/world/world-fix3-markers-shake-${width}x$height-$code.png"))
        }
    }

    /**
     * Fix round 3, third pass (WORLD-NEW-1): consecutive frames of the verifier's cases, each
     * cropped to the band where the markers sit and stacked top to bottom. Flights: offers spawned
     * as the game spawns them (560 px out, one radian either side of +x), the Core (camera on it)
     * flying 6 px per frame on course 75 and 85 at 1440x810 RU; a fly-by past a cluster at
     * 844x390 EN (3 px per frame). Shake: the game's strongest shake (6 dp along sin/cos of the
     * run time) with the targets 150 px past the edge. Every strip must show the markers keeping
     * their arrangement (no swap and swap back, no dither).
     */
    @Test
    fun rendersMarkerFlightStrips() {
        val offers = listOf(-1f, 1f).map { side -> 560f * cos(side) to 560f * sin(side) }
        fun point(kind: SiteKind, at: Pair<Float, Float>) =
            SiteProjection(kind, kind.name, at.first, at.second, false, 31f, 0, 0f, immutableListOf(), 0f, 0f)
        fun measurer(language: AppLanguage) = CanvasTextMeasurer(
            ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
            1.25f, language, Fonts.typography, KkRolePalette.Default,
        )
        /** Draws [frames] frames of [scene] (camera and shake per frame); keeps [kept] cropped to [crop]. */
        fun strip(
            width: Int, height: Int, language: AppLanguage, frames: Int, kept: IntRange, crop: IntArray, file: String,
            scene: (Int) -> Triple<GameplayRenderModel, Float, Float>,
        ) {
            val textMeasurer = measurer(language)
            val strip = ArrayList<ImageBitmap>()
            for (frame in 0 until frames) {
                val (model, shakeX, shakeY) = scene(frame)
                val keep = frame in kept
                val bitmap = ImageBitmap(if (keep) width else 1, if (keep) height else 1)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                    drawRect(Kk.Ink)
                    drawBackdrop(model, shakeX, shakeY, 3f, KkRolePalette.Default)
                    drawWorld(model, VisualFxProjection.EMPTY, shakeX, shakeY, textMeasurer)
                    drawHud(model, textMeasurer, 3f)
                }
                if (keep) strip += bitmap
            }
            writeStrip(strip, File("build/redesign-shots/world/$file"), crop)
        }
        if (fullShots) for (course in listOf(75, 85)) {
            val radians = Math.toRadians(course.toDouble()).toFloat()
            val kept = if (course == 75) 188..200 else 482..499
            strip(1440, 810, AppLanguage.Russian, kept.last + 1, kept, intArrayOf(420, 20, 1020, 180),
                "world-fix3-flight-$course-1440x810-ru.png") { frame ->
                val core = 6f * frame
                val model = model(1440f, 810f, points = listOf(point(SiteKind.entries[0], offers[0]), point(SiteKind.entries[1], offers[1])))
                    .with("coreX" to core * cos(radians), "coreY" to core * sin(radians), "cameraX" to core * cos(radians), "cameraY" to core * sin(radians))
                Triple(model, 0f, 0f)
            }
        }
        if (fullShots) strip(844, 390, AppLanguage.English, 2_484, 2_474..2_483, intArrayOf(0, 0, 844, 140), "world-fix3-flyby-844x390-en.png") { frame ->
            val x = -4_000f + 3f * frame
            val model = model(844f, 390f, points = listOf(point(SiteKind.SEALED_ANOMALY, -150f to -1_400f),
                point(SiteKind.COLLAPSING_ORBIT, 0f to -1_450f)), totem = TotemProjection(150f, -1_400f, 1f))
                .with("coreX" to x, "coreY" to 0f, "cameraX" to x, "cameraY" to 0f)
            Triple(model, 0f, 0f)
        }
        // Fourth pass: the verifier's flights on f0d9800 (a marker stranded 30-59 degrees off, a totem
        // pushed down and back before a crossing, a circuit pushed aside by an arriving orbit).
        data class Flight(val w: Int, val h: Int, val language: AppLanguage, val totem: Boolean, val heading: Float, val course: Int,
            val speed: Float, val kept: IntRange, val crop: IntArray, val name: String)
        for (flight in listOf(
            Flight(844, 390, AppLanguage.English, true, 5.016365f, 19, 6f, 94..106, intArrayOf(160, 20, 700, 130), "stranded-844x390-en"),
            Flight(390, 844, AppLanguage.Russian, true, 4.498208f, 26, 6f, 133..142, intArrayOf(150, 100, 390, 360), "crossing-390x844-ru"),
            Flight(390, 844, AppLanguage.English, true, 0.18163367f, 29, 4.5f, 155..164, intArrayOf(0, 520, 390, 640), "arrival-390x844-en"),
            // Fifth pass: 9d051c5 flashed a totem right after a stranding switch (510 -> 582 -> 492,
            // 366 -> 228 -> 366 fourteen frames later).
            Flight(844, 390, AppLanguage.English, true, 2.495924f, 1, 4.5f, 201..212, intArrayOf(300, 20, 700, 130), "switch-844x390-en"),
            Flight(390, 844, AppLanguage.Russian, true, 5.6731505f, 11, 4.5f, 414..434, intArrayOf(0, 100, 390, 260), "switch-390x844-ru"),
        ).matrix()) {
            val points = List(2) { side ->
                val angle = flight.heading + if (side == 0) -1f else 1f
                point(SiteKind.entries[(flight.course + side) % ANOMALY_KIND_COUNT], 560f * cos(angle) to 560f * sin(angle))
            }
            val course = flight.heading + Math.toRadians(flight.course * 10.0 + 5.0).toFloat()
            strip(flight.w, flight.h, flight.language, flight.kept.last + 1, flight.kept, flight.crop, "world-fix3-${flight.name}.png") { frame ->
                val core = flight.speed * frame
                val model = model(flight.w.toFloat(), flight.h.toFloat(), points = points,
                    totem = if (flight.totem) TotemProjection(900f, -2_600f, 1f) else null)
                    .with("coreX" to core * cos(course), "coreY" to core * sin(course), "cameraX" to core * cos(course), "cameraY" to core * sin(course))
                Triple(model, 0f, 0f)
            }
        }
        // Fifth pass, the curved flight (844x390 RU, near totem, R=300 turning left, start 7): 9d051c5
        // flashed the totem to the right edge for one frame at f282.
        if (fullShots) run {
            val heading = 7 * 45f * Math.PI.toFloat() / 180f + 0.2f
            val points = List(2) { side ->
                val angle = heading + if (side == 0) -1f else 1f
                point(SiteKind.entries[(7 + side) % ANOMALY_KIND_COUNT], 560f * cos(angle) to 560f * sin(angle))
            }
            val side = heading - Math.PI.toFloat() / 2f
            val centerX = 300f * cos(side)
            val centerY = 300f * sin(side)
            val startAngle = kotlin.math.atan2(-centerY, -centerX)
            strip(844, 390, AppLanguage.Russian, 293, 276..292, intArrayOf(300, 160, 844, 390), "world-fix3-switch-curved-844x390-ru.png") { frame ->
                val angle = startAngle - 4.5f * frame / 300f
                val coreX = centerX + 300f * cos(angle)
                val coreY = centerY + 300f * sin(angle)
                val model = model(844f, 390f, points = points, totem = TotemProjection(1_500f * cos(heading + 1.03f), 1_500f * sin(heading + 1.03f), 1f))
                    .with("coreX" to coreX, "coreY" to coreY, "cameraX" to coreX, "cameraY" to coreY)
                Triple(model, 0f, 0f)
            }
        }
        // The game's shake at its strongest, targets 150 px past the edge 12 degrees apart.
        for ((size, language, direction) in listOf(Triple(1440 to 810, AppLanguage.English, 150f), Triple(844 to 390, AppLanguage.English, 348f),
            Triple(390 to 844, AppLanguage.Russian, 300f)).matrix()) {
            val (width, height) = size
            val targets = listOf(direction - 12f, direction, direction + 12f).map { degrees ->
                val radians = Math.toRadians(degrees.toDouble()).toFloat()
                val alongX = kotlin.math.abs(cos(radians))
                val alongY = kotlin.math.abs(sin(radians))
                val reach = minOf(if (alongX > 1e-4f) (width * 0.5f + 150f) / alongX else Float.MAX_VALUE,
                    if (alongY > 1e-4f) (height * 0.5f + 150f) / alongY else Float.MAX_VALUE)
                (1_000f + reach * cos(radians)) to (1_000f + reach * sin(radians))
            }
            val crop = when (width) {
                1440 -> intArrayOf(0, 540, 720, 810)
                844 -> intArrayOf(422, 0, 844, 220)
                else -> intArrayOf(0, 0, 390, 300)
            }
            strip(width, height, language, 18, 6..17, crop, "world-fix3-gameshake-${width}x$height-${if (language == AppLanguage.English) "en" else "ru"}.png") { frame ->
                val model = model(width.toFloat(), height.toFloat(), points = listOf(point(SiteKind.SEALED_ANOMALY, targets[0]),
                    point(SiteKind.COLLAPSING_ORBIT, targets[1])), totem = TotemProjection(targets[2].first, targets[2].second, 1f))
                val time = 3f + frame / 60f
                if (frame < 6) Triple(model, 0f, 0f) else Triple(model, sin(time * 91f) * 6f, cos(time * 77f) * 6f)
            }
        }
    }

    /** [frames] cropped to [crop] (left, top, right, bottom) and stacked top to bottom with a 4 px mute rule between them. */
    private fun writeStrip(frames: List<ImageBitmap>, file: File, crop: IntArray) {
        if (!fullShots) return
        val width = crop[2] - crop[0]
        val height = crop[3] - crop[1]
        val image = BufferedImage(width, frames.size * (height + 4) - 4, BufferedImage.TYPE_INT_ARGB)
        val rule = Kk.Mute.toArgb()
        for (y in 0 until image.height) for (x in 0 until width) image.setRGB(x, y, rule)
        frames.forEachIndexed { index, bitmap ->
            val map = bitmap.toPixelMap()
            val top = index * (height + 4)
            for (y in 0 until height) for (x in 0 until width) image.setRGB(x, top + y, map[x + crop[0], y + crop[1]].toArgb())
        }
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    /** [frames] stacked top to bottom with a 4 px mute rule between them. */
    private fun writeStrip(frames: List<ImageBitmap>, file: File) {
        if (!fullShots) return
        val width = frames.first().width
        val height = frames.first().height
        val image = BufferedImage(width, frames.size * (height + 4) - 4, BufferedImage.TYPE_INT_ARGB)
        val rule = Kk.Mute.toArgb()
        for (y in 0 until image.height) for (x in 0 until width) image.setRGB(x, y, rule)
        frames.forEachIndexed { index, bitmap ->
            val map = bitmap.toPixelMap()
            val top = index * (height + 4)
            for (y in 0 until height) for (x in 0 until width) image.setRGB(x, top + y, map[x, y].toArgb())
        }
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    private class Scene(
        val name: String,
        val roles: KkRolePalette = KkRolePalette.Default,
        val time: Float = 3f,
        val model: (Float, Float) -> GameplayRenderModel,
        val fx: (GameplayRenderModel) -> VisualFxProjection = { VisualFxProjection.EMPTY },
        val hud: Boolean = false,
        val textScale: Float = 1.25f,
    )

    private fun scenes(): List<Scene> = buildList {
        add(Scene("cruise", model = { w, h -> cruise(w, h) }, fx = ::cruiseFx))
        add(Scene("mono", roles = KkRolePalette.Mono, model = { w, h -> cruise(w, h) }, fx = ::cruiseFx))
        add(Scene("protan", roles = KkRolePalette.Protan, model = { w, h -> cruise(w, h) }, fx = ::cruiseFx))
        add(Scene("elite", model = { w, h -> elite(w, h) }, fx = ::killFx))
        add(Scene("architect", model = { w, h -> architect(w, h) }))
        add(Scene("architect-hud", hud = true, model = { w, h -> architect(w, h) }))
        add(Scene("architect-shift-hud", hud = true, model = { w, h -> architect(w, h, shifting = true) }))
        // Every enemy type with its run affinity (aspect contour, frame weight, weakness tab), in each palette.
        add(Scene("roster", model = { w, h -> roster(w, h) }))
        add(Scene("roster-mono", roles = KkRolePalette.Mono, model = { w, h -> roster(w, h) }))
        add(Scene("roster-protan", roles = KkRolePalette.Protan, model = { w, h -> roster(w, h) }))
        add(Scene("impact", model = { w, h -> impact(w, h) }, fx = ::impactFx))
        add(Scene("impact-after", model = { w, h -> impact(w, h, lastImpactTime = 0.66f) }, fx = ::impactFx))
        add(Scene("damage", model = { w, h -> model(w, h, damageFlash = 1f) }, fx = ::damageFx))
        add(Scene("overdrive", model = { w, h -> model(w, h, overdriveTime = 3f, weapon = WeaponId.ION_SWARM, orbitals = true) }))
        add(Scene("overheat", model = { w, h -> model(w, h, overheated = true, weapon = WeaponId.MORNINGSTAR) }))
        add(Scene("poi-offer", model = { w, h -> model(w, h, points = offers(), totem = TotemProjection(-900f, 120f, 1f)) }))
        add(Scene("poi-orbit", model = { w, h -> model(w, h, points = listOf(active(SiteKind.COLLAPSING_ORBIT, 60f, 30f, 0.65f, warning = 0.5f))) }))
        add(Scene("poi-sealed", model = { w, h -> sealed(w, h) }))
        add(Scene("poi-circuit", model = { w, h -> model(w, h, points = listOf(active(SiteKind.RESONANT_CIRCUIT, -60f, -170f, 0.33f, step = 1))) }))
        // Review-fix states: the HUD drawn over the world to check labels and edge markers clear it.
        add(Scene("poi-offer-hud", hud = true, model = { w, h -> model(w, h, points = offers(), totem = TotemProjection(-900f, 120f, 1f)) }))
        add(Scene("poi-orbit-hud", hud = true, model = { w, h -> model(w, h, points = listOf(active(SiteKind.COLLAPSING_ORBIT, 60f, 30f, 0.65f, warning = 0.5f))) }))
        add(Scene("poi-orbit-high-hud", hud = true, model = { w, h -> model(w, h, points = listOf(active(SiteKind.COLLAPSING_ORBIT, 40f, -(h * 0.5f + 150f), 0.4f))) }))
        add(Scene("poi-orbit-low-hud", hud = true, model = { w, h -> model(w, h, points = listOf(active(SiteKind.COLLAPSING_ORBIT, -60f, h * 0.5f - 120f, 0.8f))) }))
        add(Scene("poi-sealed-hud", hud = true, model = { w, h -> sealed(w, h) }))
        add(Scene("poi-circuit-hud", hud = true, model = { w, h -> model(w, h, points = listOf(active(SiteKind.RESONANT_CIRCUIT, -60f, -170f, 0.33f, step = 1))) }))
        add(Scene("poi-circuit-far-hud", hud = true, model = { w, h -> model(w, h, points = listOf(active(SiteKind.RESONANT_CIRCUIT, -900f, -700f, 0.66f, step = 2))) }))
        add(Scene("numbers-edge", model = { w, h -> model(w, h) }, fx = ::edgeNumbersFx))
        // Second review-fix states (world): timers and marks near the HUD, markers above/below.
        val fixScenes = listOf<Pair<String, (Float, Float) -> GameplayRenderModel>>(
            // Target beacon just above the integrity cluster (its timer used to sit on the number).
            "poi-beacon-integrity-hud" to { w, h -> model(w, h, points = listOf(beaconAt(w, h, 0.15f * w, 0.67f * h))) },
            // Target beacon cut by the left edge: only its edge marker draws.
            "poi-beacon-edge-hud" to { w, h -> model(w, h, points = listOf(beaconAt(w, h, 30f, 0.5f * h))) },
            // Active vault under the relic row (phone) or the chain (desktop).
            "poi-vault-relic-hud" to { w, h -> model(w, h, points = listOf(activeAt(SiteKind.SEALED_ANOMALY, w, h, 0.74f * w, 0.23f * h))) },
            // An offer low on the left, next to the integrity cluster.
            "poi-offer-low-hud" to { w, h -> model(w, h, points = listOf(offerAt(w, h, 0.2f * w, 0.62f * h))) },
            // Orbit far above and far below the view.
            "poi-orbit-high-hud" to { w, h -> model(w, h, points = listOf(active(SiteKind.COLLAPSING_ORBIT, 40f, -(h * 0.5f + 700f), 0.4f))) },
            "poi-orbit-below-hud" to { w, h -> model(w, h, points = listOf(active(SiteKind.COLLAPSING_ORBIT, 40f, h * 0.5f + 700f, 0.4f))) },
            // An elite alive and the totem overhead (portrait boss row + marker).
            "poi-boss-marker-hud" to { w, h -> model(w, h, enemies = listOf(enemy(1, EnemyType.ELITE, 150f, 120f, 38f)), totem = TotemProjection(1_040f, 1_000f - h * 0.5f - 900f, 1f)) },
            // Third pass: a boss and a trial at once, the orbit far up-left (markers past the stacked top bands).
            "poi-boss-trial-marker-hud" to { w, h -> model(w, h, enemies = listOf(enemy(1, EnemyType.ELITE, 150f, 120f, 38f)),
                points = listOf(active(SiteKind.COLLAPSING_ORBIT, -0.41f * (h * 0.5f + 900f), -(h * 0.5f + 900f), 0.4f)),
                totem = TotemProjection(1_000f + 0.3f * (h * 0.5f + 900f), 1_000f + h * 0.5f + 900f, 1f)) },
            // Target beacon low in a HUD-free bottom gap: whole, place pips included, on screen.
            "poi-beacon-bottom-hud" to { w, h -> model(w, h, points = listOf(beaconAt(w, h, 0.31f * w, h - 67f))) },
        )
        fixScenes.forEach { (name, build) ->
            add(Scene("fix2-$name", hud = true, model = build))
            add(Scene("fix2-$name-t175", hud = true, textScale = 1.75f, model = build))
        }
        // Third fix round (integrated QA): enemies later in the run (the interceptor stays a
        // diamond, shooters stay squares), offer names, crowded edge markers, offers at the edge.
        listOf(11.7f, 27.3f, 64.9f).forEach { t ->
            add(Scene("fix3-cruise-t${t.toInt()}", model = { w, h -> cruise(w, h, elapsed = t) }, fx = ::cruiseFx))
        }
        val fix3Scenes = listOf<Pair<String, (Float, Float) -> GameplayRenderModel>>(
            // The game's pair of offers (560 from the Core at +-1 rad, about 940 apart): a circuit
            // up-left and an orbit down-right of the Core (the second leaves narrow screens).
            "poi-offers-hud" to { w, h -> model(w, h, points = listOf(
                offerAt(w, h, 0.5f * w - 300f, 0.5f * h - 100f, SiteKind.RESONANT_CIRCUIT),
                offerAt(w, h, 0.5f * w + 620f, 0.5f * h + 110f, SiteKind.COLLAPSING_ORBIT))) },
            // An offer hard against the left edge: its name and timer stay on screen.
            "poi-offer-edge-hud" to { w, h -> model(w, h, points = listOf(offerAt(w, h, 50f, 0.45f * h))) },
            // Two offers and the totem off screen in nearly one direction (above right).
            "poi-markers-crowded-hud" to { w, h -> model(w, h, points = listOf(
                SiteProjection(SiteKind.SEALED_ANOMALY, "Sealed anomaly", 1_000f + 900f, 1_000f - 2_600f, false, 31f, 0, 0f, immutableListOf(), 0f, 0f),
                SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", 1_000f + 1_150f, 1_000f - 2_500f, false, 31f, 0, 0f, immutableListOf(), 0f, 0f)),
                totem = TotemProjection(1_000f + 1_300f, 1_000f - 2_300f, 1f)) },
        )
        fix3Scenes.forEach { (name, build) ->
            add(Scene("fix3-$name", hud = true, model = build))
            add(Scene("fix3-$name-t175", hud = true, textScale = 1.75f, model = build))
        }
        CoreShape.entries.forEach { shape ->
            add(Scene("form-${shape.name.lowercase()}", model = { w, h -> model(w, h, coreShape = shape, weapon = WeaponId.PHASE_LATTICE) }))
        }
        listOf(0, 5, 10).forEach { tier ->
            add(Scene("tier$tier", model = { w, h -> cruise(w, h, rebirthLevel = tier) }, fx = ::cruiseFx))
        }
        // The Core ribbon follows the recorded path: a carved curve, a Dash with its ghosts, Overdrive, a blink's break.
        add(Scene("ribbon-curve", model = { w, h -> ribbonRun(w, h, 1_400f, 520f) }, fx = { ribbonFx(it, 520f) }))
        add(Scene("ribbon-dash", model = { w, h -> ribbonRun(w, h, 1_800f, 900f, dash = 0.18f) }, fx = { ribbonFx(it, 900f, dashNewest = 10) }))
        add(Scene("ribbon-overdrive", model = { w, h -> ribbonRun(w, h, 2_400f, 700f, overdrive = 3f) }, fx = { ribbonFx(it, 700f) }))
        add(Scene("ribbon-break", model = { w, h -> ribbonRun(w, h, 1_200f, 600f) }, fx = ::ribbonBreakFx))
        // A running gate race: two gates passed, the next in volt, later gates mute, the doubled finish; then the same
        // course with the next gate far off screen (its edge marker) under the HUD's trial panel.
        add(Scene("site-race", model = { w, h -> model(w, h, points = listOf(race(step = 2))) }))
        add(Scene("site-race-hud", hud = true, model = { w, h -> model(w, h, points = listOf(race(step = 2))) }))
        add(Scene("site-race-far-hud", hud = true, model = { w, h -> model(w, h, points = listOf(race(step = 5, far = true))) }))
        // The five challenges: a slalom with one pole passed and the next in volt (then under the HUD), the brakeless
        // run's shrinking ring, the Ram rite's ring with its bracketed drifters (two already destroyed), the untouched
        // trial's ring with a telegraphed volley, and the velocity hold's wide zone with its progress at the center.
        add(Scene("site-slalom", model = { w, h -> model(w, h, points = listOf(slalom())) }))
        add(Scene("site-slalom-hud", hud = true, model = { w, h -> model(w, h, points = listOf(slalom())) }))
        add(Scene("site-brakeless", hud = true, model = { w, h -> model(w, h, points = listOf(ringChallenge(SiteKind.BRAKELESS_TRIAL, -200f, 150f, 765f, 0.45f))) }))
        add(Scene("site-ram", hud = true, model = { w, h -> ram(w, h) }))
        add(Scene("site-untouched", hud = true, model = { w, h ->
            model(w, h, points = listOf(ringChallenge(SiteKind.UNTOUCHED_TRIAL, -150f, 100f, 650f, 0.3f, warning = 0.5f)))
        }))
        add(Scene("site-velocity", hud = true, model = { w, h -> model(w, h, points = listOf(ringChallenge(SiteKind.VELOCITY_TRIAL, 300f, 200f, 1_800f, 0.6f))) }))
        // Baby mode: the soft cursor with its pacifier wings, the diapered Core, the pastel halftone and ribbon; then under
        // the HUD (the soft tether and the pacifier plate before the tier plate at tier 3).
        add(Scene("easy-cruise", model = { w, h -> ribbonRun(w, h, 1_400f, 520f).with("easyMode" to true) }, fx = { ribbonFx(it, 520f) }))
        add(Scene("easy-cruise-hud", hud = true, model = { w, h -> ribbonRun(w, h, 1_400f, 520f).with("easyMode" to true, "rebirthLevel" to 3) },
            fx = { ribbonFx(it, 520f) }))
        // The 0.6.0 roster: the ten new ordinary kinds, the four mini-bosses and the two secret bosses; a judging Throne and a
        // hardened Antecedent with their bars; a Comet marking its lane and one streaking; Pylons docked on a rock.
        add(Scene("roster-worlds", model = { w, h -> rosterWorlds(w, h) }))
        add(Scene("roster-worlds-mono", roles = KkRolePalette.Mono, model = { w, h -> rosterWorlds(w, h) }))
        add(Scene("roster-worlds-protan", roles = KkRolePalette.Protan, model = { w, h -> rosterWorlds(w, h) }))
        add(Scene("miniboss-throne", hud = true, model = { w, h -> minibossThrone(w, h) }))
        add(Scene("secret-antecedent", hud = true, model = { w, h -> secretAntecedent(w, h) }))
        add(Scene("comet-lane", model = { w, h -> cometLane(w, h) }))
        add(Scene("pylon-dock", model = { w, h -> pylonDock(w, h) }))
        // Run variety: each world event in progress under the HUD (its edge band; the meteor's and the ion storm's strikes
        // filling, the flare's band crossing, the storms' motes), the gravity storm in its telegraph, a Merchant signal on
        // screen and past the edge, an Elite with each affix and champions beside plain kinds; the meteor, the affixes and
        // the champions also on the mono and protan palettes.
        for (kind in WorldEventKind.entries) {
            add(Scene("event-${eventShotName(kind)}", hud = true, model = { w, h -> worldEvent(w, h, kind) }, fx = ::cruiseFx))
        }
        add(Scene("event-gravity-telegraph", hud = true, model = { w, h -> worldEvent(w, h, WorldEventKind.GRAVITY_STORM, elapsed = 1.8f) }))
        add(Scene("event-meteor-mono", roles = KkRolePalette.Mono, hud = true, model = { w, h -> worldEvent(w, h, WorldEventKind.METEOR_SHOWER) }))
        add(Scene("event-meteor-protan", roles = KkRolePalette.Protan, hud = true, model = { w, h -> worldEvent(w, h, WorldEventKind.METEOR_SHOWER) }))
        add(Scene("signal-merchant", hud = true, model = { w, h -> signal(w, h, onScreen = true) }))
        add(Scene("signal-far", hud = true, model = { w, h -> signal(w, h, onScreen = false) }))
        for ((suffix, roles) in listOf("" to KkRolePalette.Default, "-mono" to KkRolePalette.Mono, "-protan" to KkRolePalette.Protan)) {
            add(Scene("elite-affixes$suffix", roles = roles, model = { w, h -> eliteAffixes(w, h) }))
            add(Scene("champions$suffix", roles = roles, model = { w, h -> champions(w, h) }))
        }
    }

    /**
     * The ten new ordinary kinds at their run radii in a row (the Pylon and the Sentry mounted, the Seraph and the Choir
     * with their rings), the four mini-bosses below (three egg sacs, three arcs) and the two secret bosses (the
     * Antecedent's two arcs of its third phase).
     */
    private fun rosterWorlds(w: Float, h: Float): GameplayRenderModel {
        val ordinary = listOf(EnemyType.COMET to 14f, EnemyType.PYLON to 22f, EnemyType.SENTRY to 20f, EnemyType.CRAWLER to 12f,
            EnemyType.SERAPH to 21f, EnemyType.CHOIR to 25f, EnemyType.IMP to 15f, EnemyType.HOUND to 16f, EnemyType.CIPHER to 19f,
            EnemyType.MIRROR to 20f)
        val minis = listOf(EnemyType.WARDEN_PRIME to 44f, EnemyType.BROOD_QUEEN to 46f, EnemyType.THRONE to 48f, EnemyType.PYRE_HERALD to 50f)
        val secrets = listOf(EnemyType.CARTOGRAPHER to 54f, EnemyType.ANTECEDENT to 58f)
        val enemies = ordinary.mapIndexed { index, (type, radius) ->
            val mounted = type == EnemyType.PYLON || type == EnemyType.SENTRY
            enemy(index + 1, type, -405f + index * 90f, -270f, radius).copy(
                behavior = if (mounted) kinetickk.ball.gameplay.nucleus.render.EnemyBehavior.ANCHORED else 0,
                facing = (PI / 2).toFloat(),
            )
        } + minis.mapIndexed { index, (type, radius) ->
            enemy(20 + index, type, -330f + index * 220f, 70f, radius).copy(charges = if (type == EnemyType.WARDEN_PRIME || type == EnemyType.PYRE_HERALD) 0 else 3)
        } + secrets.mapIndexed { index, (type, radius) ->
            enemy(30 + index, type, -160f + index * 320f, 250f, radius,
                affinity = if (type == EnemyType.ANTECEDENT) phaseAffinity(StatusElement.CRYO, phase = 2) else runAffinity(type))
                .copy(charges = if (type == EnemyType.ANTECEDENT) 2 else 0, facing = 0.4f)
        }
        return model(w, h, enemies = enemies)
    }

    /** A Throne in its final phase passing judgement (its four lanes firming) behind its last arc, a third of it left. */
    private fun minibossThrone(w: Float, h: Float) = model(w, h, enemies = listOf(
        enemy(1, EnemyType.THRONE, 160f, -220f, 48f, hp = 30f, affinity = phaseAffinity(StatusElement.VOID, phase = 2, immune = true)).copy(
            behavior = kinetickk.ball.gameplay.nucleus.render.EnemyBehavior.TELEGRAPH, behaviorRemaining = 0.35f, facing = 0.6f, charges = 1),
        enemy(2, EnemyType.SERAPH, -260f, -150f, 21f),
        enemy(3, EnemyType.CHOIR, 330f, 60f, 25f),
    ), projectiles = (0 until 8).map { index -> projectile(160f + cos(index * 0.785f) * 120f, -220f + sin(index * 0.785f) * 120f, true) })

    /**
     * The Antecedent in its third phase behind its two turning arcs, hardened against Cryo (so weak to Thermal), with two
     * Imps of its second phase still about and an aimed fan in flight.
     */
    private fun secretAntecedent(w: Float, h: Float) = model(w, h, enemies = listOf(
        enemy(1, EnemyType.ANTECEDENT, 140f, -230f, 58f, hp = 45f, affinity = phaseAffinity(StatusElement.CRYO, phase = 2)).copy(
            charges = 2, facing = 1.1f),
        enemy(2, EnemyType.IMP, -220f, -90f, 15f),
        enemy(3, EnemyType.IMP, 300f, 110f, 15f),
    ), projectiles = (0 until 5).map { index -> projectile(140f - 60f - index * 4f, -230f + 70f + (index - 2) * 22f, true) })

    /** A Comet marking its lane toward the Core and another streaking along its own. */
    private fun cometLane(w: Float, h: Float) = model(w, h, enemies = listOf(
        enemy(1, EnemyType.COMET, -320f, -210f, 14f).copy(behavior = kinetickk.ball.gameplay.nucleus.render.EnemyBehavior.TELEGRAPH,
            behaviorRemaining = 0.45f, facing = kotlin.math.atan2(210f, 320f)),
        enemy(2, EnemyType.COMET, 260f, 140f, 14f).copy(behavior = kinetickk.ball.gameplay.nucleus.render.EnemyBehavior.STRIKE,
            facing = (PI * 0.92).toFloat(), vx = -1_100f, vy = 140f),
    ))

    /** Two Pylons docked on a rock's rim facing the Core: one idle on its mount, one aiming (its sight locked). */
    private fun pylonDock(w: Float, h: Float): GameplayRenderModel {
        val rockX = 230f
        val rockY = -170f
        val rock = 90f
        fun docked(id: Int, angle: Float, behavior: Int, remaining: Float): EnemyProjection {
            val x = rockX + cos(angle) * (rock + 22f)
            val y = rockY + sin(angle) * (rock + 22f)
            return enemy(id, EnemyType.PYLON, x, y, 22f).copy(behavior = behavior, behaviorRemaining = remaining,
                facing = kotlin.math.atan2(-y, -x))
        }
        return model(w, h, enemies = listOf(
            docked(1, (PI * 0.82).toFloat(), kinetickk.ball.gameplay.nucleus.render.EnemyBehavior.ANCHORED, 0f),
            docked(2, (PI * 0.55).toFloat(), kinetickk.ball.gameplay.nucleus.render.EnemyBehavior.TELEGRAPH, 0.15f),
        )).with("obstacles" to immutableListOf(ObstacleProjection(1L, ObstacleKind.ASTEROID, 1_000f + rockX, 1_000f + rockY, rock, 7)))
    }

    /** A boss's affinity: [aspect] (weak to its counter) in [phase], as the nucleus publishes a boss's phase. */
    private fun phaseAffinity(aspect: StatusElement, phase: Int, immune: Boolean = false) = kinetickk.ball.gameplay.nucleus.render.EnemyAffinity.encode(
        aspect.ordinal, immune, counterOf(aspect).ordinal, armored = true, fragile = false, shifting = false, phase = phase)

    private fun eventShotName(kind: WorldEventKind): String = when (kind) {
        WorldEventKind.METEOR_SHOWER -> "meteor"
        WorldEventKind.DATA_STORM -> "data"
        WorldEventKind.GRAVITY_STORM -> "gravity"
        WorldEventKind.ION_STORM -> "ion"
        WorldEventKind.SOLAR_FLARE -> "solar"
    }

    /**
     * The cruise with [kind] at [elapsed] (telegraphed from 0 s, running from 3 s to 33 s): three strikes at different
     * fills for the meteor (95 u) and the ion storm (120 u), the flare's band half way across, the wind at 30 degrees.
     */
    private fun worldEvent(w: Float, h: Float, kind: WorldEventKind, elapsed: Float = 12f): GameplayRenderModel {
        val ion = kind == WorldEventKind.ION_STORM
        val radius = if (ion) 120f else 95f
        val span = if (ion) 1f else 1.4f
        val strikes = listOf(Triple(-0.22f, -0.14f, 0.2f), Triple(0.18f, 0.16f, 0.6f), Triple(-0.05f, 0.3f, 0.95f)).map { (fx, fy, fill) ->
            EventStrikeProjection(1_000f + fx * w, 1_000f + fy * h, radius, elapsed - span * fill, elapsed - span * fill + span)
        }.toImmutableList()
        return cruise(w, h, elapsed = elapsed).with("worldEvent" to WorldEventProjection(kind, telegraphAt = 0f, startsAt = 3f, endsAt = 33f,
            windAngle = 0.52f, windStrength = if (elapsed < 3f) 0f else 1f, sweepStartedAt = elapsed - 0.6f, sweepAngle = 0.35f,
            strikes = if (kind == WorldEventKind.METEOR_SHOWER || ion) strikes else immutableListOf()))
    }

    /** A Merchant signal 18 s from its end, on screen beside the heading or far past the edge (its marker). */
    private fun signal(w: Float, h: Float, onScreen: Boolean): GameplayRenderModel {
        val dx = if (onScreen) w * 0.22f else 2_600f
        val dy = if (onScreen) -h * 0.12f else -500f
        return model(w, h).with("eventSignals" to immutableListOf(EventSignalProjection(5, SignalKind.MERCHANT, 1_000f + dx, 1_000f + dy, 21f)))
    }

    /** One Elite per affix in two rows of three (a Gravitic aspect, as the Keynote bias often deals). */
    private fun eliteAffixes(w: Float, h: Float) = model(w, h, enemies = EliteAffix.entries.mapIndexed { index, affix ->
        val spacing = minOf(170f, w * 0.28f)
        enemy(index + 1, EnemyType.ELITE, (index % 3 - 1) * spacing, (index / 3 - 0.5f) * 190f, 30f,
            affinity = runAffinity(EnemyType.ELITE, eliteAspect = StatusElement.GRAVITIC) or
                EnemyAffinity.encode(EnemyAffinity.NONE, false, EnemyAffinity.NONE, false, false, false, 0, affix = affix.ordinal))
    })

    /** Champions (top row) over the same kinds plain (bottom row). */
    private fun champions(w: Float, h: Float): GameplayRenderModel {
        val kinds = listOf(EnemyType.DRIFTER to 17f, EnemyType.CHARGER to 23f, EnemyType.SHOOTER to 20f)
        val spacing = minOf(170f, w * 0.28f)
        val championBit = EnemyAffinity.encode(EnemyAffinity.NONE, false, EnemyAffinity.NONE, false, false, false, 0, champion = true)
        return model(w, h, enemies = kinds.flatMapIndexed { index, (type, radius) ->
            listOf(
                enemy(index + 1, type, (index - 1) * spacing, -95f, radius, affinity = runAffinity(type) or championBit),
                enemy(index + 11, type, (index - 1) * spacing, 95f, radius),
            )
        })
    }

    /**
     * A cruise moving +x at [speed] whose path bends up behind the Core on a circle of [radius] (centre straight above it),
     * the nucleus trail on the same circle.
     */
    private fun ribbonRun(w: Float, h: Float, speed: Float, radius: Float, dash: Float = 0f, overdrive: Float = 0f): GameplayRenderModel {
        val step = speed / 120f / radius
        val trail = (0 until 24).map { index ->
            val angle = (PI / 2).toFloat() + (index * 6 + 1) * step
            TrailPointProjection(1_000f + cos(angle) * radius, 1_000f - radius + sin(angle) * radius, index * 0.05f)
        }
        return cruise(w, h).with("velocityX" to speed, "velocityY" to 0f, "dashPhaseTime" to dash, "overdriveTime" to overdrive,
            "trail" to trail.toImmutableList())
    }

    /** One sample per fixed step on [ribbonRun]'s circle for the model's speed, newest one step behind the Core. */
    private fun ribbonFx(model: GameplayRenderModel, radius: Float, dashNewest: Int = 0): VisualFxProjection {
        val step = model.speed / 120f / radius
        return cruiseFx(model).copy(coreRibbon = curvedRibbon(model.coreX, model.coreY - radius, radius,
            (PI / 2).toFloat() + step, 47 * step, 48, dashNewest, oldestAge = 0.39f))
    }

    /** A blink 300 u back: six samples since it, then the older path, which no segment joins. */
    private fun ribbonBreakFx(model: GameplayRenderModel): VisualFxProjection {
        val step = model.speed / 120f
        val since = (1..6).map { model.coreX - it * step to model.coreY }
        val before = (0 until 30).map { model.coreX - 300f - it * step * cos(it * 0.03f) to model.coreY - 170f - it * step * sin(it * 0.03f) }
        val points = since + before
        val ribbon = CoreRibbonProjection.of(
            FloatArray(points.size * 2) { if (it % 2 == 0) points[it / 2].first else points[it / 2].second },
            FloatArray(points.size) { it / 120f },
            IntArray(points.size) {
                if (it == since.lastIndex || it == points.lastIndex) CoreRibbonProjection.START else 0
            },
        )
        return cruiseFx(model).copy(coreRibbon = ribbon)
    }

    /** A six-pole slalom from just behind the Core, its axis turned 0.2 rad, pole 0 passed and pole 1 next. */
    private fun slalom(): SiteProjection {
        val axis = 0.2f
        val course = List(6) { index ->
            val along = 360f + 340f * index
            val lateral = if (index % 2 == 0) 150f else -150f
            SiteMarkProjection(cos(axis) * along - sin(axis) * lateral, sin(axis) * along + cos(axis) * lateral)
        }.toImmutableList()
        return SiteProjection(SiteKind.SLALOM_TRIAL, "Slalom", 1_000f - 300f, 1_000f - 40f, true, 12.4f, 1, 1f / 6f,
            immutableListOf(), 0f, 0f, course = course, sides = 0b010101)
    }

    /** A running ring challenge centred ([dx], [dy]) from the Core; the untouched trial aims its volley at the Core. */
    private fun ringChallenge(kind: SiteKind, dx: Float, dy: Float, radius: Float, progress: Float, warning: Float = 0f) =
        SiteProjection(kind, kind.name, 1_000f + dx, 1_000f + dy, true, 13.2f, 0, progress, immutableListOf(), warning,
            kotlin.math.atan2(-dy, -dx), radius = radius, aimDx = -dx, aimDy = -dy)

    /** The Ram rite with its eight drifters on their ring, two of them already destroyed (gone) and counted. */
    private fun ram(w: Float, h: Float): GameplayRenderModel {
        val centerX = 150f
        val centerY = -80f
        val foes = (2 until 8).map { index ->
            val angle = index * 2f * kotlin.math.PI.toFloat() / 8f
            enemy(index + 1, EnemyType.DRIFTER, centerX + cos(angle) * 320f, centerY + sin(angle) * 320f, 17f)
        }
        val rite = SiteProjection(SiteKind.RAM_TRIAL, "Ram rite", 1_000f + centerX, 1_000f + centerY, true, 14.6f, 0, 2f / 6f,
            (1..8).toList().toImmutableList(), 0f, 0f, radius = 650f)
        return model(w, h, enemies = foes, points = listOf(rite))
    }

    /** A seven-gate race around the Core: a loop of 330 (the far course 1,300, past every edge). */
    private fun race(step: Int, far: Boolean = false): SiteProjection {
        val radius = if (far) 1_300f else 330f
        val course = List(7) { index ->
            val angle = -2.4f + index * 0.76f
            SiteMarkProjection(cos(angle) * radius, sin(angle) * radius)
        }.toImmutableList()
        return SiteProjection(SiteKind.GATE_RACE, "Gate race", 1_000f + 40f, 1_000f + 20f, true, 9.3f, step, step / 7f,
            immutableListOf(), 0f, 0f, course = course, clock = 5.7f, medal = 2)
    }

    // Board HUD.png positions (1440 x 810) are converted to world offsets from the Core so every
    // size keeps the Core at the screen center.
    private fun cruise(w: Float, h: Float, rebirthLevel: Int = 0, elapsed: Float = 3f): GameplayRenderModel {
        val enemies = listOf(
            enemy(1, EnemyType.DRIFTER, -340f, -170f, 17f),
            enemy(2, EnemyType.DRIFTER, -250f, -256f, 15f),
            enemy(3, EnemyType.CHARGER, -170f, 180f, 18f),
            enemy(4, EnemyType.SHOOTER, -390f, 90f, 17f, actionTimer = 0.3f),
            enemy(5, EnemyType.SHOOTER, 480f, -70f, 17f),
            enemy(6, EnemyType.WEAVER, 196f, 104f, 19f, flash = 1f),
            enemy(7, EnemyType.INTERCEPTOR, -490f, -50f, 15f),
            enemy(8, EnemyType.DRIFTER, 490f, 110f, 15f),
            enemy(9, EnemyType.CHARGER, 590f, 30f, 18f),
            enemy(10, EnemyType.DRIFTER, 240f, 190f, 16f),
            enemy(11, EnemyType.DRIFTER, 360f, -300f, 14f),
            enemy(12, EnemyType.WARDEN, -520f, 330f, 29f),
            enemy(13, EnemyType.SPLITTER, 640f, 260f, 27f),
        )
        return model(
            w, h, enemies = enemies, rebirthLevel = rebirthLevel, elapsed = elapsed,
            projectiles = listOf(-310f to 78f, -260f to 60f, -210f to 42f, 440f to -44f, 396f to -38f, 352f to -32f, 120f to -75f, -30f to 50f)
                .map { (x, y) -> projectile(x, y, hostile = true) } + projectile(90f, -30f, hostile = false),
            pickups = listOf(
                PickupType.DATA to (-120f to -90f), PickupType.DATA to (-100f to -70f), PickupType.DATA to (-135f to -65f),
                PickupType.DATA to (360f to 0f), PickupType.KEY to (-340f to -90f), PickupType.REPAIR to (120f to 140f),
                PickupType.RELIC to (-420f to -250f),
            ),
            nodes = true,
        )
    }

    private fun cruiseFx(model: GameplayRenderModel): VisualFxProjection {
        val numbers = listOf(
            Triple(210f, 50f, 742L), Triple(-360f, -208f, 184L), Triple(470f, 50f, 96L), Triple(310f, 142f, 1_240L), Triple(-60f, 220f, 4_800L),
        ).mapIndexed { index, (x, y, amount) ->
            DamageNumberProjection(model.coreX + x, model.coreY + y, amount, critical = index == 4,
                life = InteractionFxLimits.DAMAGE_NUMBER_LIFE_SECONDS - 0.12f - index * 0.05f,
                driftX = x / kotlin.math.hypot(x, y), driftY = y / kotlin.math.hypot(x, y))
        }
        val arc = WeaponArcProjection(model.coreX + 200f, model.coreY + 150f, model.coreX + 400f, model.coreY + 60f, 0.12f)
        return VisualFxProjection(
            particles = shards(model.coreX + 240f, model.coreY - 180f, 1).toImmutableList(),
            motionEchoes = immutableListOf(),
            shockwaves = immutableListOf(ShockwaveProjection(model.coreX + 240f, model.coreY - 180f, 0.24f, 0.3f, 50f, 1)),
            damageNumbers = numbers.toImmutableList(),
            weaponArcs = immutableListOf(arc),
        )
    }

    /** Big numbers at every screen edge (long Russian compact amounts clip first). */
    private fun edgeNumbersFx(model: GameplayRenderModel): VisualFxProjection {
        val halfW = model.screenWidth * 0.5f
        val halfH = model.screenHeight * 0.5f
        val spots = listOf(halfW - 8f to 0f, -halfW + 8f to -60f, 0f to -halfH + 6f, 60f to halfH - 6f, halfW - 30f to halfH - 30f)
        return VisualFxProjection.EMPTY.copy(damageNumbers = spots.mapIndexed { index, (dx, dy) ->
            DamageNumberProjection(model.coreX + dx, model.coreY + dy, listOf(48_200L, 3_900L, 1_250L, 912_000L, 6_600L)[index],
                critical = index % 2 == 0, life = 0.3f, driftX = 0f, driftY = -1f)
        }.toImmutableList())
    }

    private fun shards(x: Float, y: Float, colorIndex: Int, directionX: Float? = null, directionY: Float = 0f): List<ParticleProjection> =
        (0 until 10).map { index ->
            // Spread like the reducer's directional burst (±0.58 rad) with varied speeds.
            val angle = if (directionX != null) kotlin.math.atan2(directionY, directionX) + ((index * 7) % 10 - 4.5f) * 0.12f else index * 2.4f
            val distance = if (index < 4) 30f + ((index * 5) % 4) * 16f else 20f + index * 6f
            val speed = if (index < 4) 170f else 120f
            ParticleProjection(x + cos(angle) * distance, y + sin(angle) * distance, cos(angle) * speed, sin(angle) * speed,
                0.35f, 0.6f, colorIndex, if (index < 4) 5.5f + index * 0.6f else 2f)
        }

    private fun killFx(model: GameplayRenderModel): VisualFxProjection = VisualFxProjection(
        particles = shards(model.coreX - 260f, model.coreY + 120f, 1).toImmutableList(),
        motionEchoes = immutableListOf(),
        shockwaves = immutableListOf(ShockwaveProjection(model.coreX - 260f, model.coreY + 120f, 0.26f, 0.3f, 46f, 1)),
        damageNumbers = immutableListOf(DamageNumberProjection(model.coreX + 150f, model.coreY - 200f, 1_140L, false, 0.3f, driftX = 0.6f, driftY = -0.8f)),
        weaponArcs = immutableListOf(),
    )

    private fun impactFx(model: GameplayRenderModel): VisualFxProjection = VisualFxProjection(
        particles = shards(model.coreX + 150f, model.coreY - 20f, 3, 1f, -0.2f).toImmutableList(),
        motionEchoes = listOf(0.2f, 0.45f, 0.7f).map { f ->
            MotionEchoProjection(model.coreX - 60f * (1f + f * 2f), model.coreY + 14f * (1f + f * 2f), 0.3f * (1f - f), 0.3f, 1f)
        }.toImmutableList(),
        shockwaves = immutableListOf(ShockwaveProjection(model.coreX + 150f, model.coreY - 20f, 0.23f, 0.26f, 110f, 3)),
        damageNumbers = immutableListOf(DamageNumberProjection(model.coreX + 160f, model.coreY - 60f, 1_640L, false, 0.45f, driftX = 0.9f, driftY = -0.4f)),
        weaponArcs = immutableListOf(),
    )

    private fun damageFx(model: GameplayRenderModel): VisualFxProjection = VisualFxProjection(
        particles = shards(model.coreX, model.coreY, 4).toImmutableList(),
        motionEchoes = immutableListOf(),
        shockwaves = immutableListOf(ShockwaveProjection(model.coreX, model.coreY, 0.2f, 0.3f, 90f, 4)),
        damageNumbers = immutableListOf(),
        weaponArcs = immutableListOf(),
    )

    private fun elite(w: Float, h: Float) = model(w, h, enemies = listOf(
        enemy(1, EnemyType.ELITE, 250f, -170f, 38f),
        enemy(2, EnemyType.DRIFTER, -200f, -180f, 16f),
        enemy(3, EnemyType.SHOOTER, 380f, 120f, 17f),
    ), pickups = listOf(PickupType.KEY to (-240f to 110f), PickupType.RELIC to (-220f to 130f), PickupType.DATA to (-280f to 100f)))

    /**
     * The Architect in its second phase, hardened against Thermal (weak to Cryo): [shifting] into it at
     * the two-thirds gate, or a little way into the phase.
     */
    private fun architect(w: Float, h: Float, shifting: Boolean = false) = model(w, h, enemies = listOf(
        enemy(1, EnemyType.ARCHITECT, 150f, -250f, 74f, hp = if (shifting) 200f / 3f else 55f,
            affinity = runAffinity(EnemyType.ARCHITECT, architectAspect = StatusElement.THERMAL, architectPhase = 1, shifting = shifting)),
    ), projectiles = (0 until 12).map { index -> projectile(150f + cos(index * 0.52f) * 150f, -250f + sin(index * 0.52f) * 150f, true) })

    /** One of each ordinary type at its run radius in a row, a Gravitic elite and a Thermal-stacked Shooter below. */
    private fun roster(w: Float, h: Float) = model(w, h, enemies = listOf(
        enemy(1, EnemyType.DRIFTER, -330f, -120f, 17f),
        enemy(2, EnemyType.SHOOTER, -240f, -120f, 20f),
        enemy(3, EnemyType.INTERCEPTOR, -150f, -120f, 18f),
        enemy(4, EnemyType.CHARGER, -60f, -120f, 23f),
        enemy(5, EnemyType.WEAVER, 30f, -120f, 21f),
        enemy(6, EnemyType.SPLITTER, 125f, -120f, 27f),
        enemy(7, EnemyType.WARDEN, 230f, -120f, 29f),
        enemy(8, EnemyType.ELITE, -150f, 130f, 38f, affinity = runAffinity(EnemyType.ELITE, eliteAspect = StatusElement.GRAVITIC)),
        enemy(9, EnemyType.SHOOTER, 60f, 130f, 20f, statusBits = 5 or (2 shl 4)),
    ))

    private fun impact(w: Float, h: Float, lastImpactTime: Float = 0.715f) = model(w, h, lastImpactTime = lastImpactTime, velocityX = 780f, velocityY = -160f,
        enemies = listOf(enemy(1, EnemyType.INTERCEPTOR, 175f, -26f, 18f, flash = 1f)))

    private fun sealed(w: Float, h: Float): GameplayRenderModel {
        val point = active(SiteKind.SEALED_ANOMALY, 200f, -120f, 1f / 3f, defenders = listOf(21, 22, 23))
        val enemies = listOf(
            enemy(21, EnemyType.WARDEN, 330f, -120f, 26f),
            enemy(22, EnemyType.WARDEN, 135f, -7f, 26f),
            enemy(24, EnemyType.DRIFTER, 60f, -233f, 16f),
        )
        return model(w, h, points = listOf(point), enemies = enemies)
    }

    /** Screen position ([x], [y]) at [w] x [h] as a world position (the Core sits at 1000, 1000 at the screen center). */
    private fun worldX(w: Float, x: Float) = 1_000f + x - w * 0.5f
    private fun worldY(h: Float, y: Float) = 1_000f + y - h * 0.5f

    /** An active circuit whose target beacon (order 2: 230 right, 200 below the center) sits at screen ([x], [y]). */
    private fun beaconAt(w: Float, h: Float, x: Float, y: Float) = SiteProjection(SiteKind.RESONANT_CIRCUIT, "Resonant circuit",
        worldX(w, x) - 230f, worldY(h, y) - 200f, true, 14f, 0, 0.33f, immutableListOf(), 0f, 0f)

    private fun activeAt(kind: SiteKind, w: Float, h: Float, x: Float, y: Float) = SiteProjection(kind, "Sealed anomaly",
        worldX(w, x), worldY(h, y), true, 14f, 0, 0.33f, immutableListOf(), 0f, 0f)

    private fun offerAt(w: Float, h: Float, x: Float, y: Float, kind: SiteKind = SiteKind.SEALED_ANOMALY) =
        SiteProjection(kind, kind.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
            worldX(w, x), worldY(h, y), false, 31f, 0, 0f, immutableListOf(), 0f, 0f)

    private fun offers() = listOf(
        SiteProjection(SiteKind.SEALED_ANOMALY, "Sealed anomaly", 1_000f + 330f, 1_000f - 150f, false, 31f, 0, 0f, immutableListOf(), 0f, 0f),
        SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", 1_000f + 1_300f, 1_000f + 500f, false, 31f, 0, 0f, immutableListOf(), 0f, 0f),
    )

    private fun active(kind: SiteKind, dx: Float, dy: Float, progress: Float, step: Int = 0, warning: Float = 0f, defenders: List<Int> = emptyList()) =
        SiteProjection(kind, kind.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, 1_000f + dx, 1_000f + dy, true, 14f, step, progress,
            defenders.toImmutableList(), warning, -0.3f)

    private fun enemy(
        id: Int,
        type: EnemyType,
        dx: Float,
        dy: Float,
        radius: Float,
        flash: Float = 0f,
        actionTimer: Float = 1f,
        dead: Boolean = false,
        statusBits: Int = 0,
        affinity: Int = runAffinity(type),
        hp: Float = 100f,
    ) = EnemyProjection(id, type, 1_000f + dx, 1_000f + dy, 0f, 0f, hp, 100f, radius, actionTimer, flash, 0f, 0f, 1_000f + dx, 1_000f + dy, dead,
        statusBits = statusBits, affinityBits = affinity)

    private fun projectile(dx: Float, dy: Float, hostile: Boolean) = ProjectileProjection(
        1_000f + dx, 1_000f + dy, 0f, 0f, if (hostile) 4.5f else 4f, 1f, hostile, 1f, 0, 0, null, 1_000f + dx - 6f, 1_000f + dy + 3f,
    )

    private fun model(
        w: Float,
        h: Float,
        enemies: List<EnemyProjection> = emptyList(),
        projectiles: List<ProjectileProjection> = emptyList(),
        pickups: List<Pair<PickupType, Pair<Float, Float>>> = emptyList(),
        points: List<SiteProjection> = emptyList(),
        totem: TotemProjection? = null,
        coreShape: CoreShape = CoreShape.ORB,
        weapon: WeaponId = WeaponId.ARC_COIL,
        rebirthLevel: Int = 0,
        damageFlash: Float = 0f,
        lastImpactTime: Float = 0f,
        overdriveTime: Float = 0f,
        overheated: Boolean = false,
        velocityX: Float = 360f,
        velocityY: Float = -190f,
        nodes: Boolean = false,
        orbitals: Boolean = false,
        elapsed: Float = 3f,
    ): GameplayRenderModel {
        val coreX = 1_000f
        val coreY = 1_000f
        return GameplayRenderModel(
            content = Content, phase = GamePhase.RUNNING, settings = PlayerPreferences(textScale = 1.25f), rebirthLevel = rebirthLevel,
            screenWidth = w, screenHeight = h, uiScale = 1f, coreX = coreX, coreY = coreY, velocityX = velocityX, velocityY = velocityY,
            cameraX = coreX, cameraY = coreY, pointerX = w * 0.5f + 265f, pointerY = h * 0.5f - 170f, pointerActive = true,
            braking = false, elapsed = elapsed, heat = 28f, overheated = overheated, dashPhaseTime = 0f, hp = 142f, maxHp = 180f,
            shield = 2f, maxShield = 3f, level = 14, data = 30, nextLevelData = 40, keys = 1, kills = 40, combo = 12,
            comboTime = 1f, runMatter = 486L, totalMatter = 1_000L, lastImpact = 20f, lastImpactTime = lastImpactTime,
            damageFlash = damageFlash, runGrace = 0f, screenShake = 0f, message = "", messageTime = 0f, mass = 1f,
            damageMultiplier = 1f, weaponPower = 1f, coolingRate = 1f, magnetStrength = 1f, dashImpulse = 1f, dashHeatCost = 20f,
            regenPerSecond = 0f, critChance = 0f, critMultiplier = 2f, pickupRadius = 150f, luck = 0f, dataGain = 1f, matterGain = 1f,
            attackSpeed = 1f, damageReduction = 0f, comboWindow = 2f, overdriveGain = 1f, dragCoefficient = 1f, polarityStability = 0.86f,
            weapon = weapon, weaponLevel = 5, overdriveCharge = 58f, overdriveTime = overdriveTime, rerollsRemaining = 1,
            acquiredItemCount = 0, recentItem = null, equippedRelics = immutableListOf(), morningstarAngle = 0.4f,
            morningstarX = coreX + 70f, morningstarY = coreY + 40f, weaponBeamTime = 0f, weaponBeamStartX = 0f,
            weaponBeamStartY = 0f, weaponBeamEndX = 0f, weaponBeamEndY = 0f, totem = totem, coreShape = coreShape,
            enemies = enemies.toImmutableList(), projectiles = projectiles.toImmutableList(),
            pickups = pickups.map { (type, offset) ->
                PickupProjection(type, coreX + offset.first, coreY + offset.second, 0f, 0f, 3f, coreX + offset.first + 8f, coreY + offset.second + 5f)
            }.toImmutableList(),
            trail = (0 until 24).map { index -> TrailPointProjection(coreX - index * 18f * 0.88f, coreY + index * 18f * 0.47f, index * 0.08f) }.toImmutableList(),
            weaponNodes = if (nodes) immutableListOf(WeaponNodeProjection(WeaponNodeType.GRAVITY_MINE, coreX - 345f, coreY - 285f, 2f, 3f, 55f)) else immutableListOf(),
            weaponOrbitals = if (orbitals) (0 until 4).map { index -> WeaponOrbitalProjection(index, coreX + cos(index * 1.57f) * 70f, coreY + sin(index * 1.57f) * 70f, 8f) }.toImmutableList() else immutableListOf(),
            choices = immutableListOf(), choiceType = ChoiceType.ITEM, pendingRelicChoiceCount = 0,
            itemStacks = immutableListOf(), discoveredItemIds = immutableSetOf(), relicRanks = immutableListOf(),
            sites = points.toImmutableList(),
            // A running site's panel, as Gameplay publishes it with the site.
            trial = points.firstOrNull { it.active }?.let { siteTrial(it) },
        )
    }

    private object Fonts {
        private val directory: File by lazy {
            generateSequence(File("").absoluteFile) { it.parentFile }
                .map { File(it, "foundation/design/src/commonMain/composeResources/font") }
                .first { File(it, "kk_wide_black.ttf").isFile }
        }

        private fun font(name: String, weight: FontWeight, style: FontStyle = FontStyle.Normal) = Font(File(directory, "$name.ttf"), weight, style)

        val typography: InterfaceTypography by lazy {
            val cond = FontFamily(font("kk_cond_black_italic", FontWeight.Black, FontStyle.Italic), font("kk_cond_black", FontWeight.Black), font("kk_cond_extrabold", FontWeight.ExtraBold))
            InterfaceTypography(
                body = FontFamily(font("kk_body_regular", FontWeight.Normal), font("kk_body_medium", FontWeight.Medium), font("kk_body_bold", FontWeight.Bold)),
                display = FontFamily(font("kk_cond_black_italic", FontWeight.Bold), font("kk_cond_extrabold", FontWeight.Normal)),
                wide = FontFamily(font("kk_wide_black", FontWeight.Black), font("kk_wide_bold", FontWeight.Bold)),
                cond = cond, label = cond,
                mono = FontFamily(font("kk_mono_medium", FontWeight.Medium), font("kk_mono_bold", FontWeight.Bold)),
            )
        }
    }

    private fun write(bitmap: ImageBitmap, file: File) {
        if (!fullShots) return
        val map = bitmap.toPixelMap()
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, map[x, y].toArgb())
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    private companion object {
        val Content = GameplayContentSnapshot(
            version = KINETICKK_CONTENT_VERSION,
            items = listOf(ItemDefinition(0, "Ram", "Ram", ItemRarity.COMMON, immutableListOf(BuildTag.RAM),
                stats = immutableListOf(StatDelta(ItemEffect.IMPACT_DAMAGE, 0.05f), StatDelta(ItemEffect.WEAPON_POWER, 0.04f)),
                maxStacks = 3, unlockLevel = 1)).toImmutableList(),
            weapons = WeaponId.entries.map { fixtureWeapon(it, description = "Fixture") }.toImmutableList(),
            weaponMasteries = WeaponMastery.entries.toImmutableList(),
            relics = rewardFixtureRelics.toImmutableList(),
            rebirth = RebirthPolicySnapshot(
                minimumLevel = 0, maximumLevel = 0,
                profiles = immutableListOf(RebirthProfile(0, "Baseline", 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0, 120, 0.09f, 24f)),
                maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
            ),
            relicPolicy = RelicPolicy(4, 5),
        )
    }
}
