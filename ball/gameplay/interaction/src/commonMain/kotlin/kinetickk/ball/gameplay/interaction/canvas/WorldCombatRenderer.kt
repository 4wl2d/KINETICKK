// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.damageNumberTier
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.profile.api.ParticleDensity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val RADIANS_TO_DEGREES = 57.29578f

/** Flux Wake's burning half-width and the fade after a trail point's life (mirrors of the simulation's). */
private const val FLUX_WAKE_WIDTH = 20f
private const val TRAIL_FADE_SECONDS = 0.45f

/** How far a damage number drifts away from the Core over its life (world units). */
private const val DAMAGE_NUMBER_DRIFT = 52f

/** A layout-affecting difference that gives the ink shadow its own paragraph (see drawDamageNumbers). */
private const val SHADOW_LINE_HEIGHT = 1.001f

/**
 * Standard damage number size before the tier scale (×1, ×1.2, ×1.45, ×1.8), the size setting and
 * the text size: at the default text size the four tiers render at about 25/30/36/45 px.
 */
private const val DAMAGE_NUMBER_BASE_SP = 20f

/** The crit stamp next to a damage number: 12 px on the Feedback board at the default text size. */
private val CRIT_STAMP_SP = worldLabelSp(12f)

/**
 * The world layer in world units ([withWorldUnits]: entities, combat, POI marks, the singularity),
 * then its screen-space overlay in device pixels (damage numbers, reaction stamps, edge markers).
 * [shakeX]/[shakeY] are device pixels.
 */
internal fun DrawScope.drawWorld(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    textMeasurer: TextMeasurer,
    hudDensity: Float = density,
) {
    WorldDrawProbe.begin()
    // The HUD keep-out is screen space, at the HUD's (interface-scaled) density: update it before the
    // world layer changes the scope's units.
    worldHudKeepOut(engine, textMeasurer, hudDensity)
    val markers = WorldOverlayScratch.markers.clear()
    val scale = engine.worldScale
    withWorldUnits(scale) {
        drawWorldLayer(engine, visualFx, shakeX / scale, shakeY / scale, textMeasurer, markers)
    }
    drawDamageNumbers(engine, visualFx, shakeX, shakeY, textMeasurer)
    drawReactionStamps(engine, visualFx, shakeX, shakeY, textMeasurer)
    // Off-screen targets are placed together, so their markers never overlap each other.
    drawEdgeMarkers(markers, textMeasurer)
}

private fun DrawScope.drawWorldLayer(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    textMeasurer: TextMeasurer,
    markers: EdgeMarkerBatch,
) {
    val roles = textMeasurer.roles
    val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    // The pointer is current input, never interpolated: the singularity sits exactly under it.
    val pointer = Offset(engine.pointerX + shakeX * 0.18f, engine.pointerY + shakeY * 0.18f)

    // The crowd and the reaction effects setting pick every world look's level of detail this frame.
    val crowd = onScreenEnemyCount(engine, shakeX, shakeY, FxFrame.CROWD_SIG_ONLY + 1)
    FxFrame.begin(crowd, engine.settings.reactionEffects)
    // Structure floors and walls, then obstacles, lie on the backdrop, under everything that moves.
    drawStructureFloors(engine, shakeX, shakeY, roles)
    drawStructureWalls(engine, shakeX, shakeY, roles)
    drawObstacles(engine, shakeX, shakeY, roles)
    drawSites(engine, shakeX, shakeY, textMeasurer)
    drawAmbientSites(engine, shakeX, shakeY, textMeasurer)
    drawDimensionWorld(engine, shakeX, shakeY, roles)
    drawWorldEventAmbient(engine, shakeX, shakeY, roles)
    drawEventSignals(engine, shakeX, shakeY, textMeasurer)
    // Lingering fields and skill zones are ground decals.
    drawCombatFields(engine, shakeX, shakeY, roles)
    drawSkillMarkers(engine, shakeX, shakeY, roles)
    drawCharacterField(engine, shakeX, shakeY, roles)
    drawTotem(engine, shakeX, shakeY, textMeasurer)
    drawShockwaves(engine, visualFx, shakeX, shakeY, roles)
    drawMotionEchoes(engine, visualFx, shakeX, shakeY, roles)
    drawTrail(engine, shakeX, shakeY, roles)
    drawCoreRibbon(engine, visualFx.coreRibbon, shakeX, shakeY, roles)
    drawWeaponNodes(engine, shakeX, shakeY, roles)
    drawWeaponEntities(engine, shakeX, shakeY, roles, airborne = false)
    drawPickups(engine, shakeX, shakeY, roles)
    drawVolatileBursts(engine, shakeX, shakeY, roles)
    drawWorldEventStrikes(engine, shakeX, shakeY, roles)
    drawWeapon(engine, core, shakeX, shakeY, roles)
    drawWeaponEntities(engine, shakeX, shakeY, roles, airborne = true)
    drawWeaponArcs(engine, visualFx, shakeX, shakeY, roles)
    drawPersistentLinks(engine, shakeX, shakeY, roles)
    drawParticles(engine, visualFx, shakeX, shakeY, roles)
    // Telegraphs and threats remain readable over cosmetic trails and secondary effects. A crowded
    // screen drops the status patterns of ordinary enemies for a flat tint.
    val statusDetail = crowd <= STATUS_PATTERN_ENEMY_LIMIT
    for (index in engine.enemies.indices) {
        drawEnemy(engine, engine.enemies[index], shakeX, shakeY, roles, statusDetail)
    }
    drawElementBursts(engine, visualFx, shakeX, shakeY, roles)
    drawSkillBursts(engine, visualFx, shakeX, shakeY, roles)
    drawApexBursts(engine, visualFx, shakeX, shakeY, roles)
    drawCharacterActors(engine, shakeX, shakeY, roles)
    drawProjectiles(engine, shakeX, shakeY, roles)
    drawImpactMarks(engine, visualFx, shakeX, shakeY)
    // The tether and the Core/cursor halos are HUD (screen-space) elements drawn by HudRenderer.
    if (engine.phase != GamePhase.GAME_OVER) {
        drawCore(engine, core, roles)
        drawCoreStates(engine, core, roles)
        drawApexActive(engine, core, shakeX, shakeY, roles)
        drawSecretCompanions(engine, core, shakeX, shakeY, roles)
    }
    drawStructureScrim(engine, shakeX, shakeY)
    drawSingularity(pointer, engine.renderElapsed, engine.tetherDistance < 75f, roles, engine.easyMode)
    // Decided in world units, like the marks they stand in for; the collection order is the priority order (the
    // batch keeps the first markers it receives).
    collectSiteEdgeMarkers(engine, textMeasurer, markers)
    collectTotemEdgeMarker(engine, markers)
    collectSignalEdgeMarker(engine, markers)
    collectDimensionEdgeMarkers(engine, markers)
    collectStructureEdgeMarkers(engine, markers)
    collectAmbientSiteEdgeMarkers(engine, markers)
}

/**
 * Damage numbers (cond 900 italic, ink offset shadow): the hue of their element, size by tier, pop to 1.25× in 90 ms,
 * settle in 180 ms and drift away from the Core over 600 ms (Out), then shrink out (In).
 * Critical hits get a stamp. Text layouts that differ only in color share one paragraph, and
 * repainting it in another color or alpha rebuilds it, so the shadow gets its own layout (a
 * hair-different line height) and the exit is a scale, not a fade.
 */
private fun DrawScope.drawDamageNumbers(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    textMeasurer: TextMeasurer,
) {
    val settings = engine.settings
    if (!settings.damageNumbers) return
    val roles = textMeasurer.roles
    val typography = textMeasurer.typography
    val numbers = visualFx.damageNumbers
    DamageNumberImages.beginFrame()
    for (index in numbers.indices) {
        val number = numbers[index]
        val elapsedMs = (InteractionFxLimits.DAMAGE_NUMBER_LIFE_SECONDS - number.life) * 1_000f
        val progress = (elapsedMs / KkTime.Drift).coerceIn(0f, 1f)
        val pop = damageNumberPop(elapsedMs, progress)
        if (pop <= 0.02f) continue
        val drift = KkEase.Out.transform(progress) * DAMAGE_NUMBER_DRIFT
        val location = worldOnScreen(engine, number.x + number.driftX * drift, number.y + number.driftY * drift, shakeX, shakeY)
        if (!isOnScreen(location, 80f)) continue
        val tier = damageNumberTier(number.amount, settings.damageNumberTierThreshold, number.critical)
        val fontSize = DAMAGE_NUMBER_BASE_SP * settings.damageNumberSize.scale * damageNumberScale(tier)
        val color = damageNumberColor(number.tone, roles)
        val text = number.formattedAmount(settings.damageNumberFormat, textMeasurer.language)
        val face = measureKkText(textMeasurer, text, typography.condStyle(fontSize, tabular = true, lineHeightEm = 1f, color = color))
        val shadow = measureKkText(textMeasurer, text, typography.condStyle(fontSize, tabular = true, lineHeightEm = SHADOW_LINE_HEIGHT, color = Kk.Ink))
        val image = DamageNumberImages.image(face, shadow, color)
        val tilt = ((number.amount % 9L).toInt() - 4) * 1.1f
        // Keep the whole number (and its crit stamp) inside the screen, e.g. long Russian amounts
        // at phone edges.
        val half = face.size.width * 0.5f * pop + 2f
        val halfHeight = face.kkBoxHeight * 0.5f * pop + 2f
        var right = half
        var up = halfHeight
        if (number.critical) {
            val stamp = kkStampSize(textMeasurer, WorldStrings.crit(textMeasurer.language), density, CRIT_STAMP_SP)
            right = half + (stamp.width + 3f) * pop
            up = max(halfHeight, (face.kkBoxHeight * 0.5f + 6f + stamp.height) * pop)
        }
        val edge = d(6f)
        val x = keepInside(location.x, half, right, size.width, edge)
        val y = keepInside(location.y, up, halfHeight, size.height, edge)
        withTransform({
            translate(x, y)
            rotate(tilt, Offset.Zero)
            scale(pop, pop, Offset.Zero)
        }) {
            if (image != null) image.draw(this, pop) else drawDamageNumberInk(face, shadow, color)
            if (number.critical) {
                drawKkStamp(
                    textMeasurer,
                    WorldStrings.crit(textMeasurer.language),
                    Offset(face.size.width * 0.5f + 3f, -face.kkBoxHeight * 0.5f - 6f),
                    KkStampVariant.THREAT,
                    fontSize = CRIT_STAMP_SP,
                )
            }
        }
    }
}

/**
 * Moves a label centered at [center] with extents [before]/[after] along one axis so it lies
 * within [edge]..[length] − [edge]; centered when it cannot fit.
 */
internal fun keepInside(center: Float, before: Float, after: Float, length: Float, edge: Float): Float {
    val low = edge + before
    val high = length - edge - after
    return if (low > high) (low + high) * 0.5f else center.coerceIn(low, high)
}

/**
 * Pop 0.4 to 1.25 in 90 ms and settle to 1 by 270 ms (`kk-demo-float`), hold, then shrink out
 * over the last quarter of the drift (In).
 */
internal fun damageNumberPop(elapsedMs: Float, progress: Float): Float = when {
    elapsedMs < 90f -> kkLerp(0.4f, 1.25f, KkEase.Out.transform((elapsedMs / 90f).coerceIn(0f, 1f)))
    elapsedMs < 270f -> kkLerp(1.25f, 1f, KkEase.Out.transform((elapsedMs - 90f) / 180f))
    progress > 0.75f -> 1f - KkEase.In.transform(((progress - 0.75f) / 0.25f).coerceIn(0f, 1f))
    else -> 1f
}

/** Dash afterimages: rings the size of the Core in the you color (`HUD.dc.html` dash ghosts). */
internal fun DrawScope.drawMotionEchoes(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    for (index in visualFx.motionEchoes.indices) {
        val echo = visualFx.motionEchoes[index]
        val center = world(engine, echo.x, echo.y, shakeX, shakeY)
        if (!isOnScreen(center, 60f)) continue
        val life = clamp(echo.life / echo.maxLife, 0f, 1f)
        drawCircle(roles.you.copy(alpha = life * life * echo.intensity * 0.7f), 18f, center, style = kkStroke(2f))
    }
}

/**
 * Expanding rings for kills (bone), dash (you), ram impacts (you, 3 px), damage taken (threat),
 * Breaks and reactions (element rings), Apex strikes (gold) and quakes (a jagged element ring).
 */
internal fun DrawScope.drawShockwaves(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    for (index in visualFx.shockwaves.indices) {
        val wave = visualFx.shockwaves[index]
        val center = world(engine, wave.x, wave.y, shakeX, shakeY)
        if (!isOnScreen(center, wave.maxRadius)) continue
        val life = clamp(wave.life / wave.maxLife, 0f, 1f)
        val radius = max(2f, wave.maxRadius * KkEase.Out.transform(1f - life))
        val color = fxColor(wave.colorIndex, roles)
        drawCircle(color.copy(alpha = life * 0.06f), radius * 0.72f, center)
        if (isQuakeColor(wave.colorIndex)) {
            // The quake's vertices come from its spot, so each one keeps its own jagged outline.
            drawQuakeRing(center, radius, (wave.x.toRawBits() * 31) xor wave.y.toRawBits(), color, life * 0.8f, 3f)
        } else {
            drawCircle(color.copy(alpha = life * 0.8f), radius, center, style = kkStroke(if (wave.colorIndex == 3) 3f else 2f))
        }
    }
}

/**
 * Impact accents drawn over the enemies: a bone flash where an ordinary enemy died and, for ram
 * impacts and elite kills (the you-color rings), a bone slash across the target (−27.5°).
 */
private fun DrawScope.drawImpactMarks(engine: GameplayRenderModel, visualFx: VisualFxProjection, shakeX: Float, shakeY: Float) {
    for (index in visualFx.shockwaves.indices) {
        val wave = visualFx.shockwaves[index]
        if (wave.colorIndex != 1 && wave.colorIndex != 3) continue
        val progress = 1f - clamp(wave.life / wave.maxLife, 0f, 1f)
        if (progress >= 0.45f) continue
        val center = world(engine, wave.x, wave.y, shakeX, shakeY)
        if (!isOnScreen(center, wave.maxRadius)) continue
        if (wave.colorIndex == 1 && progress < 0.22f) {
            drawCircle(Kk.Bone.copy(alpha = (1f - progress / 0.22f) * 0.85f), wave.maxRadius * 0.34f * (1f + progress), center)
        }
        if (wave.colorIndex == 3) {
            val length = wave.maxRadius * 1.3f * (1.1f + progress * 0.2f)
            rotate(KkShape.Slash, center) {
                drawRect(Kk.Bone.copy(alpha = 1f - progress / 0.45f), Offset(center.x - length * 0.5f, center.y - 2.5f), Size(length, 5f))
            }
        }
    }
}

/**
 * The Core's path history in the you color (a wider band where Flux Wake cuts). The ribbon that
 * trails the Core is [drawCoreRibbon]'s.
 */
internal fun DrawScope.drawTrail(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val you = roles.you
    val wake = engine.weapon == WeaponId.FLUX_WAKE
    // The Flux Wake band is as wide as the strip it burns: twice as wide from the first form, wider with Agony.
    val band = 2f * (FLUX_WAKE_WIDTH * (if (engine.weaponLevel >= 3) 2f else 1f) + 4f * engine.relicRank(RelicId.AGONY_SCEPTER))
    val lowParticles = engine.settings.particleDensity == ParticleDensity.LOW
    var hasPrevious = false
    var previous = Offset.Zero
    var previousLife = 0f
    for (index in engine.trail.indices) {
        val point = engine.trail[index]
        val location = world(engine, point.x, point.y, shakeX, shakeY)
        // A point burns for its life, then fades out over TRAIL_FADE_SECONDS.
        val life = clamp(1f - point.age / (point.life + TRAIL_FADE_SECONDS), 0f, 1f)
        if (hasPrevious) {
            val segmentLife = min(previousLife, life)
            if (segmentLife > 0f && (isOnScreen(previous, 50f) || isOnScreen(location, 50f))) {
                if (wake) {
                    drawLine(you.copy(alpha = segmentLife * 0.12f), previous, location, band * point.scale)
                } else if (!lowParticles) {
                    drawLine(you.copy(alpha = segmentLife * 0.05f), previous, location, 10f)
                }
                drawLine(you.copy(alpha = segmentLife * if (wake) 0.6f else 0.32f), previous, location, 2f)
            }
        }
        hasPrevious = true
        previous = location
        previousLife = life
    }
}

/**
 * World-impact screen effects only (HUD state effects are HudRenderer's): a threat glow on every
 * edge while damage is fresh (the render model has no source direction). Ram impacts read through
 * their slash, ring and screen shake; they never flash the whole screen.
 */
internal fun DrawScope.drawScreenFx(
    engine: GameplayRenderModel,
    @Suppress("UNUSED_PARAMETER") renderTime: Float,
    roles: KkRolePalette,
) {
    val flash = engine.damageFlash
    if (flash > 0f) {
        val depth = d(60f)
        val glow = WorldBrushes.edgeGlow(roles.threat.copy(alpha = 0.9f), size.width, size.height, depth)
        val alpha = flash * 0.4f
        drawRect(glow.left, Offset.Zero, Size(depth, size.height), alpha)
        drawRect(glow.right, Offset(size.width - depth, 0f), Size(depth, size.height), alpha)
        drawRect(glow.top, Offset.Zero, Size(size.width, depth), alpha)
        drawRect(glow.bottom, Offset(0f, size.height - depth), Size(size.width, depth), alpha)
    }
}

/** Weapons in the world read as the player's: bone bodies with you-color accents. */
internal fun DrawScope.drawWeapon(engine: GameplayRenderModel, core: Offset, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val you = roles.you
    val motionAngle = if (engine.speed > 1f) atan2(engine.velocityY, engine.velocityX) else 0f
    when (engine.weapon) {
        WeaponId.FLUX_WAKE -> Unit
        WeaponId.MORNINGSTAR -> {
            val ballX = engine.interpolated(engine.previousMorningstarX, engine.morningstarX)
            val ballY = engine.interpolated(engine.previousMorningstarY, engine.morningstarY)
            val ball = world(engine, ballX, ballY, shakeX, shakeY)
            drawLine(you.copy(alpha = 0.16f), core, ball, 6f)
            drawLine(Kk.Bone.copy(alpha = 0.6f), core, ball, 1.5f, pathEffect = WorldStrokes.dashEffect)
            drawMorningstarBall(ball, 20f, engine.morningstarAngle, you)
            drawSecondMorningstar(engine, core, ball, roles)
            val agonyRank = engine.relicRank(RelicId.AGONY_SCEPTER)
            if (agonyRank > 0) {
                val agony = world(engine, engine.renderCoreX * 2f - ballX, engine.renderCoreY * 2f - ballY, shakeX, shakeY)
                drawLine(Kk.ASovereign.copy(alpha = 0.16f), core, agony, 6f)
                drawLine(Kk.Bone.copy(alpha = 0.6f), core, agony, 1.5f, pathEffect = WorldStrokes.dashEffect)
                drawMorningstarBall(agony, 17f + agonyRank, -engine.morningstarAngle, Kk.ASovereign)
            }
        }
        WeaponId.PHASE_LATTICE -> drawPhaseLattice(engine, core, roles)
        WeaponId.NULL_LANCE -> {
            // A lance with a square block end aligned to its shaft (a turned square would read as a head).
            val tip = polar(core, 48f, motionAngle)
            drawLine(you.copy(alpha = 0.2f), core, polar(core, 115f, motionAngle), 7f)
            drawLine(Kk.Bone, core, tip, 2f)
            rotate(motionAngle * RADIANS_TO_DEGREES, tip) {
                drawRect(you, Offset(tip.x - 4f, tip.y - 4f), Size(8f, 8f))
            }
        }
        WeaponId.GRAVITY_MINES -> drawCircle(you.copy(alpha = 0.3f), 29f, core, style = WorldStrokes.dashedThin)
        WeaponId.ION_SWARM -> for (index in engine.weaponOrbitals.indices) {
            val orbital = engine.weaponOrbitals[index]
            val point = world(engine, engine.interpolated(orbital.previousX, orbital.x), engine.interpolated(orbital.previousY, orbital.y), shakeX, shakeY)
            drawCircle(you.copy(alpha = 0.14f), 12f, point)
            rotate(engine.renderElapsed * 115f + orbital.index * 30f, point) {
                drawRect(you, Offset(point.x - 5f, point.y - 5f), Size(10f, 10f))
            }
        }
        WeaponId.RIFT_BLADES -> for (index in engine.weaponOrbitals.indices) {
            val orbital = engine.weaponOrbitals[index]
            val point = world(engine, engine.interpolated(orbital.previousX, orbital.x), engine.interpolated(orbital.previousY, orbital.y), shakeX, shakeY)
            drawLine(Kk.Bone.copy(alpha = 0.2f), core, point, 1f, pathEffect = WorldStrokes.dashEffect)
            rotate(engine.renderElapsed * 240f + orbital.index * 45f, point) {
                drawRect(Kk.Bone, Offset(point.x - 11f, point.y - 11f), Size(22f, 22f))
                drawRect(you, Offset(point.x - 11f, point.y - 11f), Size(22f, 22f), style = kkStroke(2f))
            }
        }
        WeaponId.ARC_COIL -> {
            val pulse = (sin(engine.renderElapsed * 9f) + 1f) * 0.5f
            drawCircle(you.copy(alpha = 0.3f), 31f + pulse * 7f, core, style = kkStroke(2f))
            drawCircle(Kk.Bone.copy(alpha = 0.4f), 22f - pulse * 4f, core, style = WorldStrokes.dashedHair)
        }
        WeaponId.QUASAR_CANNON -> {
            val rear = polar(core, 19f, motionAngle + kotlin.math.PI.toFloat())
            val tip = polar(core, 55f, motionAngle)
            drawLine(you.copy(alpha = 0.26f), rear, tip, 14f)
            drawLine(Kk.Bone, core, tip, 3f)
            drawCircle(you, 7f, tip)
        }
        WeaponId.ENTROPY_FIELD -> drawEntropyField(engine, core, roles)
        WeaponId.SINGULARITY_SPEAR -> {
            drawCircle(Kk.Bone.copy(alpha = 0.25f), 36f, core, style = kkStroke(2f))
            if (engine.weaponBeamTime > 0f) {
                val start = world(engine, engine.weaponBeamStartX, engine.weaponBeamStartY, shakeX, shakeY)
                val end = world(engine, engine.weaponBeamEndX, engine.weaponBeamEndY, shakeX, shakeY)
                val alpha = clamp(engine.weaponBeamTime / 0.18f, 0f, 1f)
                drawLine(you.copy(alpha = alpha * 0.22f), start, end, 19f)
                drawLine(Kk.Bone.copy(alpha = alpha), start, end, 4f)
                drawLine(you.copy(alpha = alpha), start, end, 1.3f)
            }
        }
        WeaponId.PRISM_RELAY -> {
            val pulse = (sin(engine.renderElapsed * 7f) + 1f) * 0.5f
            val outer = 19f + pulse * 3f
            rotate(engine.renderElapsed * 46f, core) {
                drawRect(you, Offset(core.x - outer, core.y - outer), Size(outer * 2f, outer * 2f), style = kkStroke(2f))
            }
            rotate(-engine.renderElapsed * 69f, core) {
                drawRect(Kk.Bone.copy(alpha = 0.8f), Offset(core.x - 10f, core.y - 10f), Size(20f, 20f), style = kkStroke(1.5f))
            }
        }
        // The 0.4.0 additions (WorldWeaponRenderer).
        WeaponId.GYRE_DISC, WeaponId.SCATTER_BLOOM, WeaponId.TETHER_ARC, WeaponId.SENTRY_GRID,
        WeaponId.HUNTER_SALVO, WeaponId.CONCUSSION_ENGINE, WeaponId.GLACIER_BELL, WeaponId.EVENT_LENS,
        WeaponId.SOLAR_SWEEP, WeaponId.AEGIS_SHARDS, WeaponId.HOLLOW_RIFT, WeaponId.RESONANCE_DRUM ->
            drawAddedWeapon(engine, core, motionAngle, shakeX, shakeY, roles)
    }
}

/**
 * The Morningstar head: a bone disc with a you-color rim and eight short blunt studs (square
 * ends, never pointed spikes) that turn with the flail, and an ink hub.
 */
private fun DrawScope.drawMorningstarBall(center: Offset, radius: Float, angle: Float, accent: Color) {
    drawCircle(accent.copy(alpha = 0.12f), radius + 12f, center)
    val studWidth = radius * 0.28f
    val studLength = radius * 0.3f
    withTransform({
        translate(center.x, center.y)
        rotate(angle * RADIANS_TO_DEGREES, Offset.Zero)
    }) {
        for (stud in 0 until MORNINGSTAR_STUDS) {
            rotate(stud * (360f / MORNINGSTAR_STUDS), Offset.Zero) {
                drawRect(accent, Offset(-studWidth * 0.5f, -radius - studLength + 1f), Size(studWidth, studLength))
            }
        }
    }
    drawCircle(Kk.Bone, radius, center)
    drawCircle(accent, radius - 1.25f, center, style = kkStroke(2.5f))
    drawCircle(Kk.Ink, radius * 0.22f, center)
}

private const val MORNINGSTAR_STUDS = 8

/** Gravity mines: a you-color core with a dashed ring that tightens as the fuse runs down. */
internal fun DrawScope.drawWeaponNodes(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    for (index in engine.weaponNodes.indices) {
        val node = engine.weaponNodes[index]
        if (node.type != WeaponNodeType.GRAVITY_MINE) continue
        val point = world(engine, node.x, node.y, shakeX, shakeY)
        if (!isOnScreen(point, node.radius)) continue
        val ratio = clamp(node.life / node.maxLife, 0f, 1f)
        val fuse = 1f - ratio
        drawCircle(roles.you.copy(alpha = fuse * fuse * 0.1f), node.radius, point)
        drawCircle(roles.you.copy(alpha = 0.5f), max(12f, node.radius * ratio), point, style = WorldStrokes.dashedThin)
        drawCircle(roles.you.copy(alpha = 0.3f), 13f, point)
        drawCircle(roles.you, 8f, point)
    }
}

/**
 * The Core: a bone form silhouette ([KkFormGeometry]) with a hard ink rim and a soft glow; you-color
 * in overdrive and heat-tinted while overheated (`HUD.dc.html`). Integrity/heat/shield arcs are
 * the HUD's Core halo. In Baby mode the form wears a diaper ([drawDiaper]).
 */
internal fun DrawScope.drawCore(engine: GameplayRenderModel, center: Offset, roles: KkRolePalette) {
    val overdrive = engine.overdriveTime > 0f
    val fill = when {
        overdrive -> roles.you
        engine.overheated -> kkMix(Kk.Bone, roles.heat, 0.35f)
        else -> Kk.Bone
    }
    val glow = when {
        overdrive -> roles.you
        engine.overheated -> roles.heat
        else -> Kk.Bone
    }
    val glowAlpha = if (overdrive || engine.overheated) 0.26f else 0.12f
    drawCircle(glow.copy(alpha = glowAlpha), 24f, center)
    drawCircle(glow.copy(alpha = glowAlpha * 0.5f), 32f, center)
    if (overdrive) drawCircle(glow.copy(alpha = 0.1f), 44f, center)
    if (engine.braking) {
        val compression = (sin(engine.renderElapsed * 15f) + 1f) * 0.5f
        drawCircle(Kk.Bone.copy(alpha = 0.6f), 30f - compression * 5f, center, style = WorldStrokes.dashedMedium)
    }
    // Phased by Void Step: the Core shows at half strength.
    drawCoreForm(engine, center, if (engine.coreState.phased) fill.copy(alpha = 0.5f) else fill, roles)
    if (engine.easyMode) drawDiaper(engine, center, roles, if (engine.coreState.phased) 0.5f else 1f)
    val ability = engine.characterAbility
    if (ability.charge > 0f && engine.coreShape != CoreShape.RING && engine.coreShape != CoreShape.PRISM) {
        drawArc(roles.you, -90f, 360f * ability.charge.coerceIn(0f, 1f), false,
            Offset(center.x - 25f, center.y - 25f), Size(50f, 50f), style = kkStroke(2f))
    }
    // Parry marks stay inside the HUD's Core halo (radius 38).
    if (ability.parryWindow > 0f) {
        rotate(45f, center) {
            drawRect(Kk.Bone, Offset(center.x - 24f, center.y - 24f), Size(48f, 48f), style = kkStroke(2f))
        }
    }
    if (engine.dashPhaseTime > 0f) drawCircle(roles.you.copy(alpha = 0.8f), 28f + engine.dashPhaseTime * 50f, center, style = kkStroke(2f))
}

private val TesseractVertices = FloatArray(32)

/** A 4D rotating wireframe projected to Canvas and scaled to [radius]; the fixed center dot stays visible. */
internal fun DrawScope.drawTesseractCore(
    center: Offset,
    elapsed: Float,
    color: Color,
    radius: Float = GameplayRenderModel.CORE_RADIUS,
) {
    drawCircle(color.copy(alpha = 0.22f), radius, center, style = kkStroke(1f))
    val scale = radius / GameplayRenderModel.CORE_RADIUS
    val angle = elapsed * 0.65f
    val c = cos(angle)
    val s = sin(angle)
    val c2 = cos(angle * 0.73f)
    val s2 = sin(angle * 0.73f)
    val vertices = TesseractVertices
    for (index in 0 until 16) {
        val x = if (index and 1 == 0) -1f else 1f
        val y = if (index and 2 == 0) -1f else 1f
        val z = if (index and 4 == 0) -1f else 1f
        val w = if (index and 8 == 0) -1f else 1f
        val rotatedX = x * c - w * s
        val rotatedW = x * s + w * c
        val rotatedY = y * c2 - z * s2
        val rotatedZ = y * s2 + z * c2
        val perspective = 24f * scale / (2.8f - rotatedW * 0.55f)
        vertices[index * 2] = center.x + (rotatedX + rotatedZ * 0.35f) * perspective
        vertices[index * 2 + 1] = center.y + (rotatedY + rotatedZ * 0.25f) * perspective
    }
    for (index in 0 until 16) {
        for (axis in 0 until 4) {
            val other = index xor (1 shl axis)
            if (index < other) {
                drawLine(
                    color.copy(alpha = if (axis == 3) 0.5f else 1f),
                    Offset(vertices[index * 2], vertices[index * 2 + 1]),
                    Offset(vertices[other * 2], vertices[other * 2 + 1]),
                    2f,
                )
            }
        }
    }
    drawCircle(Kk.Ink, 6f * scale, center)
    drawCircle(Kk.Bone, 3.5f * scale, center)
}
