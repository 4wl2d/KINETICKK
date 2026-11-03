// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*

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
    drawStructureDisintegrations(engine, visualFx, shakeX, shakeY, roles)
    drawObstacles(engine, shakeX, shakeY, roles)
    drawSites(engine, shakeX, shakeY, textMeasurer)
    drawAmbientSites(engine, shakeX, shakeY)
    drawDimensionWorld(engine, shakeX, shakeY, roles)
    drawWorldEventAmbient(engine, shakeX, shakeY, roles)
    drawEventSignals(engine, shakeX, shakeY, textMeasurer)
    // Lingering fields and skill zones are ground decals.
    drawCombatFields(engine, shakeX, shakeY, roles)
    drawSkillMarkers(engine, shakeX, shakeY, roles)
    drawCharacterField(engine, shakeX, shakeY, roles)
    drawTotemChests(engine, shakeX, shakeY, roles)
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
        drawCoreFrost(engine, core, roles)
        drawApexActive(engine, core, shakeX, shakeY, roles)
        drawSecretCompanions(engine, core, shakeX, shakeY, roles)
    }
    drawStructureScrim(engine, shakeX, shakeY)
    drawSingularity(pointer, engine.renderElapsed, engine.tetherDistance < 75f, roles, engine.easyMode)
    // Decided in world units, like the marks they stand in for; the collection order is the priority order (the
    // batch keeps the first markers it receives).
    collectSiteEdgeMarkers(engine, textMeasurer, markers)
    collectTotemChestEdgeMarker(engine, markers)
    collectSignalEdgeMarker(engine, markers)
    collectDimensionEdgeMarkers(engine, markers)
    collectStructureEdgeMarkers(engine, markers)
    collectAmbientSiteEdgeMarkers(engine, markers)
}
