// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*

/** How far off screen an enemy still draws: its telegraphs and tethers reach farther than its body. */
internal fun enemyRenderMargin(enemy: EnemyProjection): Float = when (enemy.type) {
    EnemyType.WARDEN -> 460f
    EnemyType.ARCHITECT -> 220f
    EnemyType.HERALD -> 200f
    EnemyType.BOMBER -> BOMBER_BLAST_REACH + 20f
    EnemyType.LANCER, EnemyType.SNIPER ->
        if (enemy.behavior == EnemyBehavior.TELEGRAPH || enemy.behavior == EnemyBehavior.STRIKE) LANCE_LANE_LENGTH + 20f else 120f
    EnemyType.COMET, EnemyType.PYLON, EnemyType.SENTRY ->
        max(worldsTelegraphReach(enemy), if (enemy.linkedTo >= 0) AEGIS_TETHER_REACH else 120f)
    EnemyType.SERAPH ->
        if (enemy.actionTimer in 0f..SERAPH_MEND_LEAD) SERAPH_MEND_REACH + 20f else if (enemy.linkedTo >= 0) AEGIS_TETHER_REACH else 120f
    EnemyType.CHOIR -> CHOIR_WAVE_REACH + 20f
    EnemyType.WARDEN_PRIME -> PRIME_WELL_REACH + 20f
    EnemyType.THRONE -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) THRONE_LANE_REACH + 20f else MINI_BOSS_MARGIN
    EnemyType.BROOD_QUEEN, EnemyType.PYRE_HERALD, EnemyType.ANTECEDENT -> MINI_BOSS_MARGIN
    EnemyType.CARTOGRAPHER -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) CARTO_LANE_REACH + 20f else MINI_BOSS_MARGIN
    else -> if (enemy.linkedTo >= 0) AEGIS_TETHER_REACH else 120f
}

/** How far a Comet's lane and streak, a Pylon's sight line or a Sentry's bolt line reach while they show; 0 otherwise. */
private fun worldsTelegraphReach(enemy: EnemyProjection): Float = when (enemy.type) {
    EnemyType.COMET ->
        if (enemy.behavior == EnemyBehavior.TELEGRAPH || enemy.behavior == EnemyBehavior.STRIKE) COMET_LANE_REACH + 20f else 0f
    EnemyType.PYLON -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) PYLON_SIGHT_REACH + 20f else 0f
    EnemyType.SENTRY -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) SENTRY_LINE_REACH + 20f else 0f
    else -> 0f
}

/** Presentation copies of gameplay reaches: the Bomber's blast, the Lancer's lane, an Aegis tether, a mine's trigger. */
private const val BOMBER_BLAST_REACH = 120f
private const val LANCE_LANE_LENGTH = 900f
private const val AEGIS_TETHER_REACH = 240f
internal const val MINE_TRIGGER_REACH = 60f
internal const val MINE_ARMED_LIFE = 7.2f
internal const val REFLECTOR_ARC_DEGREES = 120f

/** Presentation copies of the 0.6.0 lanes, sight lines and their timings (Comet, Pylon, Sentry), and the mount arc. */
private const val COMET_LANE_REACH = 1_500f
private const val COMET_MARK_SECONDS = 0.6f
private const val PYLON_SIGHT_REACH = 900f
private const val PYLON_AIM_SECONDS = 0.8f
private const val PYLON_LOCK_SECONDS = 0.2f
private const val SENTRY_LINE_REACH = 760f
private const val SENTRY_AIM_SECONDS = 0.45f
internal const val MOUNT_ARC_DEGREES = 120f
internal const val MOUNT_ARC_GAP = 5f

/** Presentation copies of the dimension kinds' reaches and timings (Seraph, Choir, Imp, Hound, Cipher), and accents. */
private const val SERAPH_MEND_REACH = 260f
private const val SERAPH_MEND_LEAD = 0.4f
internal const val SERAPH_RING = 0.5f
private const val CHOIR_WAVE_REACH = 300f
private const val CHOIR_SING_SECONDS = 0.6f
internal const val CHOIR_RING = 0.32f
private const val IMP_BLAST_REACH = 95f
private const val IMP_FUSE_SECONDS = 0.5f
private const val HOUND_WINDUP_SECONDS = 0.35f
private const val CIPHER_AIM_SECONDS = 0.3f
private const val CIPHER_GLITCH_OFFSET = 4f
private const val CIPHER_GLITCH_ALPHA = 0.28f
/** An impact-only shield on an ordinary or Elite enemy (a Seraph's mend): smaller and lighter than a boss's. */
private const val IMPACT_SHIELD_RING = 1.35f

/**
 * Presentation copies of the mini-bosses' reaches and timings (Warden Prime's well and its double volley cycle, the
 * Throne's arcs and judgement lanes), the Brood Queen's sac pips and a mini-boss's draw margin.
 */
private const val MINI_BOSS_MARGIN = 220f
private const val PRIME_WELL_REACH = 560f
/** Warden Prime's second phase fires a ring at the half of its double cycle: the half is one volley period. */
private const val PRIME_PAIR_HALF = 2f
private const val PRIME_VOLLEY_LEAD = 0.35f
internal const val QUEEN_SAC_RADIUS = 4.5f
internal const val QUEEN_SAC_GAP = 10f
internal const val QUEEN_SAC_ORBIT_RATE = 0.7f
internal const val QUEEN_SAC_SPACING = (2.0 * PI / 3.0).toFloat()
private const val THRONE_ARC_DEGREES = 80f
private const val THRONE_ARC_SPACING = (2.0 * PI / 3.0).toFloat()
private const val THRONE_ARC_GAP = 9f
private const val THRONE_LANE_REACH = 900f
private const val THRONE_JUDGEMENT_SECONDS = 1f

/**
 * Presentation copies of the secret bosses' marks and reaches: the anomaly glyph and its turning bone ring (24 s per
 * turn), and the Cartographer's three Survey lanes, their spacing and how long they are marked.
 */
private const val SECRET_GLYPH = 0.75f

/** A streaking Comet's tail: its length in radii, the full streak's alpha and its brighter core's (together 0.55 at the body). */
private const val COMET_TAIL_LENGTH = 7f
private const val COMET_TAIL_ALPHA = 0.3f
private const val COMET_TAIL_CORE_ALPHA = 0.36f

/** Half side of the Mono ink plate under the secret boss glyph, in glyph sizes: its diamond's 10/24 half diagonal. */
private const val SECRET_GLYPH_PLATE = (10.0 / 24.0 / 1.4142135623730951).toFloat()
private const val SECRET_RING = 1.7f
private const val SECRET_RING_ALPHA = 0.55f
private const val SECRET_RING_DEGREES_PER_SECOND = 15f
private const val CARTO_LANE_REACH = 1_400f
private const val CARTO_LANE_OFFSET = 60f
private const val CARTO_MARK_SECONDS = 1f

/** Gameplay telegraphs keep their timing; only their look changed (threat lines and rings). */
internal fun DrawScope.drawEnemyTelegraph(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    shakeX: Float,
    shakeY: Float,
    threat: androidx.compose.ui.graphics.Color,
) {
    when (enemy.type) {
        EnemyType.SHOOTER -> if (enemy.actionTimer in 0f..0.42f) {
            val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
            val charge = 1f - enemy.actionTimer / 0.42f
            drawLine(threat.copy(alpha = 0.12f + charge * 0.42f), center, core, 1f + charge * 1.2f, pathEffect = WorldStrokes.dashEffect)
            drawCircle(threat.copy(alpha = 0.5f), enemy.radius + 15f * (1f - charge), center, style = kkStroke(1.5f))
        }
        EnemyType.CHARGER -> if (enemy.actionTimer < 0f) drawWindup(engine, enemy, center, shakeX, shakeY, threat, 0.45f)
        EnemyType.INTERCEPTOR -> {
            val predicted = world(
                engine,
                engine.renderCoreX + engine.velocityX * 0.45f,
                engine.renderCoreY + engine.velocityY * 0.45f,
                shakeX,
                shakeY,
            )
            drawLine(threat.copy(alpha = 0.3f), center, predicted, 1.4f, pathEffect = WorldStrokes.dashEffect)
            drawCircle(threat.copy(alpha = 0.55f), 9f, predicted, style = kkStroke(1.5f))
            drawCircle(threat.copy(alpha = 0.55f), 2f, predicted)
        }
        EnemyType.WEAVER -> {
            repeat(2) { index ->
                val orbitAngle = engine.renderElapsed * 2.7f + enemy.id + index * PI.toFloat()
                drawCircle(threat.copy(alpha = 0.8f), 2.5f, polar(center, enemy.radius + 10f, orbitAngle))
            }
            if (enemy.actionTimer in 0f..0.34f) {
                val angle = engine.renderElapsed * 0.2f + enemy.id
                drawLine(threat.copy(alpha = 0.48f), polar(center, enemy.radius + 19f, angle),
                    polar(center, enemy.radius + 19f, angle + PI.toFloat()), 2f)
            }
        }
        EnemyType.WARDEN -> {
            // The gravity well's reach, kept to a hairline so several wardens never tint the arena.
            val gravityPulse = (sin(engine.renderElapsed * 2.2f + enemy.id) + 1f) * 0.5f
            drawCircle(threat.copy(alpha = 0.12f + gravityPulse * 0.08f), 440f, center, style = WorldStrokes.dashedHair)
            if (enemy.actionTimer in 0f..0.38f) {
                drawCircle(threat.copy(alpha = 0.52f), enemy.radius + 18f + enemy.actionTimer * 50f, center, style = kkStroke(2f))
            }
        }
        EnemyType.ELITE -> if (enemy.actionTimer in 0f..0.28f) {
            drawCircle(threat.copy(alpha = 0.48f), enemy.radius + 20f + enemy.actionTimer * 38f, center, style = kkStroke(1.5f))
        }
        EnemyType.HERALD, EnemyType.PYRE_HERALD, EnemyType.ANTECEDENT -> if (enemy.actionTimer in 0f..0.3f) {
            drawCircle(threat.copy(alpha = 0.5f), enemy.radius + 22f + enemy.actionTimer * 40f, center, style = kkStroke(2f))
        }
        EnemyType.BOMBER -> if (enemy.behavior == EnemyBehavior.PRIMED) drawFuse(engine, enemy, center, threat, BOMBER_BLAST_REACH, 0.6f)
        EnemyType.LANCER -> when (enemy.behavior) {
            EnemyBehavior.TELEGRAPH -> {
                val charge = (1f - enemy.behaviorRemaining / 0.7f).coerceIn(0f, 1f)
                val end = polar(center, LANCE_LANE_LENGTH, enemy.facing)
                drawLine(threat.copy(alpha = 0.18f + charge * 0.5f), center, end, 2f + charge * 5f)
                drawLine(threat.copy(alpha = 0.6f + charge * 0.4f), center, end, 1f, pathEffect = WorldStrokes.dashEffect)
            }
            EnemyBehavior.STRIKE -> {
                val tail = polar(center, enemy.radius * 3.5f, enemy.facing + PI.toFloat())
                drawLine(Kk.Bone.copy(alpha = 0.7f), center, tail, enemy.radius * 0.8f)
            }
            else -> Unit
        }
        EnemyType.SNIPER -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) {
            drawSightLine(engine, enemy, center, shakeX, shakeY, threat, 0.22f, 1.1f)
        }
        EnemyType.PHANTOM -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) {
            val charge = (1f - enemy.behaviorRemaining / 0.35f).coerceIn(0f, 1f)
            val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
            drawLine(threat.copy(alpha = 0.25f + charge * 0.5f), center, core, 1.5f + charge * 2f)
            drawCircle(threat.copy(alpha = 0.7f), enemy.radius + 24f * (1f - charge), center, style = WorldStrokes.dashedMedium)
        }
        EnemyType.BROODMOTHER, EnemyType.BROOD_QUEEN -> if (enemy.actionTimer in 0f..0.6f) {
            drawCircle(threat.copy(alpha = 0.45f), enemy.radius + 10f + enemy.actionTimer * 30f, center, style = WorldStrokes.dashedThin)
        }
        EnemyType.COMET -> when (enemy.behavior) {
            EnemyBehavior.TELEGRAPH -> {
                // The lane it is about to streak, firming as the mark runs out.
                val charge = (1f - enemy.behaviorRemaining / COMET_MARK_SECONDS).coerceIn(0f, 1f)
                drawLine(threat.copy(alpha = 0.25f + charge * 0.5f), center, polar(center, COMET_LANE_REACH, enemy.facing), 1.5f,
                    pathEffect = WorldStrokes.dashEffect)
            }
            EnemyBehavior.STRIKE -> {
                // The streak: 7 radii long and 0.9 radii wide at the body, tapering to a blunt tip, its bone fading
                // along it (a shorter, brighter core over the full-length streak).
                val back = enemy.facing + PI.toFloat()
                withTransform({
                    translate(center.x, center.y)
                    rotate(back * RAD_TO_DEG, Offset.Zero)
                    scale(enemy.radius * COMET_TAIL_LENGTH, enemy.radius, Offset.Zero)
                }) {
                    drawPath(WorldUnitShapes.cometTail, Kk.Bone.copy(alpha = COMET_TAIL_ALPHA))
                    drawPath(WorldUnitShapes.cometTailCore, Kk.Bone.copy(alpha = COMET_TAIL_CORE_ALPHA))
                }
                drawLine(threat, center, polar(center, enemy.radius * 4f, back), 1f)
            }
            else -> Unit
        }
        // The Sniper's sight line on its own timings.
        EnemyType.PYLON -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) {
            drawSightLine(engine, enemy, center, shakeX, shakeY, threat, PYLON_LOCK_SECONDS, PYLON_AIM_SECONDS)
        }
        // The corridor its bolt train will run down, thickening as the aim holds.
        EnemyType.SENTRY -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) {
            val charge = (1f - enemy.behaviorRemaining / SENTRY_AIM_SECONDS).coerceIn(0f, 1f)
            drawLine(threat.copy(alpha = 0.3f + charge * 0.5f), center, polar(center, SENTRY_LINE_REACH, enemy.facing), 1f + charge * 2f)
        }
        // The reach of its next mend, shown just before it pulses.
        EnemyType.SERAPH -> if (enemy.actionTimer in 0f..SERAPH_MEND_LEAD) {
            drawCircle(Kk.Bone.copy(alpha = 0.25f), SERAPH_MEND_REACH, center, style = WorldStrokes.dashedThin)
        }
        // Two rings contract onto it while it sings; the hairline is the reach of its shove.
        EnemyType.CHOIR -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) {
            val charge = (1f - enemy.behaviorRemaining / CHOIR_SING_SECONDS).coerceIn(0f, 1f)
            val ring = CHOIR_WAVE_REACH + (enemy.radius + 12f - CHOIR_WAVE_REACH) * charge
            drawCircle(threat.copy(alpha = 0.18f), CHOIR_WAVE_REACH, center, style = kkStroke(1f))
            drawCircle(threat.copy(alpha = 0.3f + charge * 0.5f), ring, center, style = WorldStrokes.dashedThin)
            drawCircle(threat.copy(alpha = 0.3f + charge * 0.5f), ring * 0.6f, center, style = WorldStrokes.dashedThin)
        }
        // The Bomber's fuse look at the Imp's smaller reach.
        EnemyType.IMP -> if (enemy.behavior == EnemyBehavior.PRIMED) drawFuse(engine, enemy, center, threat, IMP_BLAST_REACH, IMP_FUSE_SECONDS)
        // The Charger's wind-up look over the Hound's shorter wind-up.
        EnemyType.HOUND -> if (enemy.actionTimer < 0f) drawWindup(engine, enemy, center, shakeX, shakeY, threat, HOUND_WINDUP_SECONDS)
        EnemyType.CIPHER -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) {
            val charge = (1f - enemy.behaviorRemaining / CIPHER_AIM_SECONDS).coerceIn(0f, 1f)
            val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
            drawLine(threat.copy(alpha = 0.25f + charge * 0.5f), center, core, 1.2f, pathEffect = WorldStrokes.dashEffect)
        }
        // A hairline to the cursor it mirrors the Core through (the singularity's draw point).
        EnemyType.MIRROR -> {
            val pointer = Offset(engine.pointerX + shakeX * 0.18f, engine.pointerY + shakeY * 0.18f)
            drawLine(threat.copy(alpha = 0.18f), center, pointer, 1f, pathEffect = WorldStrokes.dashedHair.pathEffect)
        }
        // The well's reach: a hairline while it pulls, a firmer dashed ring while it pushes; the Warden's volley ring.
        EnemyType.WARDEN_PRIME -> {
            if (enemy.behavior == EnemyBehavior.PRIMED) {
                drawCircle(threat.copy(alpha = 0.35f), PRIME_WELL_REACH, center, style = WorldStrokes.dashedMedium)
            } else {
                val gravityPulse = (sin(engine.renderElapsed * 2.2f + enemy.id) + 1f) * 0.5f
                drawCircle(threat.copy(alpha = 0.12f + gravityPulse * 0.08f), PRIME_WELL_REACH, center, style = WorldStrokes.dashedHair)
            }
            // Its second phase fires at the half of a double cycle too.
            val doubled = EnemyAffinity.phase(enemy.affinityBits) == 1 && enemy.actionTimer > PRIME_PAIR_HALF
            val volley = if (doubled) enemy.actionTimer - PRIME_PAIR_HALF else enemy.actionTimer
            if (volley in 0f..PRIME_VOLLEY_LEAD) {
                drawCircle(threat.copy(alpha = 0.52f), enemy.radius + 18f + volley * 50f, center, style = kkStroke(2f))
            }
        }
        // Judgement: the four lanes its bolt trains will run down, firming while the arcs stand still.
        EnemyType.THRONE -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) {
            val charge = (1f - enemy.behaviorRemaining / THRONE_JUDGEMENT_SECONDS).coerceIn(0f, 1f)
            val lane = threat.copy(alpha = 0.2f + charge * 0.6f)
            repeat(4) { index ->
                val angle = enemy.facing + PI.toFloat() * 0.25f + index * PI.toFloat() * 0.5f
                drawLine(lane, center, polar(center, THRONE_LANE_REACH, angle), 1.5f, pathEffect = WorldStrokes.dashEffect)
            }
        }
        // Survey: the three parallel lanes its bolt trains will run down, firming while it holds the aim (its final
        // phase's short aim after a jump marks none).
        EnemyType.CARTOGRAPHER -> if (enemy.behavior == EnemyBehavior.TELEGRAPH && EnemyAffinity.phase(enemy.affinityBits) < 2) {
            val charge = (1f - enemy.behaviorRemaining / CARTO_MARK_SECONDS).coerceIn(0f, 1f)
            val lane = threat.copy(alpha = 0.2f + charge * 0.6f)
            val sideX = -sin(enemy.facing) * CARTO_LANE_OFFSET
            val sideY = cos(enemy.facing) * CARTO_LANE_OFFSET
            for (index in -1..1) {
                val start = Offset(center.x + sideX * index, center.y + sideY * index)
                drawLine(lane, start, polar(start, CARTO_LANE_REACH, enemy.facing), 1.5f, pathEffect = WorldStrokes.dashEffect)
            }
        }
        EnemyType.DRIFTER, EnemyType.SPLITTER, EnemyType.ARCHITECT, EnemyType.SWARMLING, EnemyType.AEGIS,
        EnemyType.MINELAYER, EnemyType.REFLECTOR,
        EnemyType.CRAWLER,
        -> Unit
    }
    if (enemy.linkedTo >= 0) drawAegisTether(engine, enemy, center, shakeX, shakeY)
}

/** A charge's wind-up: a firming line to the Core and a dashed ring closing onto the body over [windupSeconds]. */
private fun DrawScope.drawWindup(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    shakeX: Float,
    shakeY: Float,
    threat: androidx.compose.ui.graphics.Color,
    windupSeconds: Float,
) {
    val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    val charge = (-enemy.actionTimer / windupSeconds).coerceIn(0f, 1f)
    drawLine(threat.copy(alpha = 0.2f + charge * 0.62f), center, core, 2f + charge * 2f)
    drawCircle(threat.copy(alpha = 0.72f), enemy.radius + 28f * (1f - charge), center, style = WorldStrokes.dashedMedium)
}

/** A primed fuse: it blinks faster as it burns down from [fuseSeconds]; the ring shows the blast's [reach]. */
private fun DrawScope.drawFuse(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    threat: androidx.compose.ui.graphics.Color,
    reach: Float,
    fuseSeconds: Float,
) {
    val remaining = enemy.behaviorRemaining
    val blink = if (sin(engine.renderElapsed * (18f + (fuseSeconds - remaining).coerceAtLeast(0f) * 60f)) > 0f) 1f else 0.35f
    drawCircle(threat.copy(alpha = 0.10f * blink), reach, center)
    drawCircle(threat.copy(alpha = 0.75f * blink), reach, center, style = WorldStrokes.dashedMedium)
    drawCircle(Kk.Bone.copy(alpha = 0.8f * blink), enemy.radius + 5f, center, style = kkStroke(2f))
}

/**
 * The Sniper's sight line: dashed while it tracks the Core over [aimSeconds], solid once [lockSeconds] remain, with a
 * closing muzzle ring.
 */
private fun DrawScope.drawSightLine(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    shakeX: Float,
    shakeY: Float,
    threat: androidx.compose.ui.graphics.Color,
    lockSeconds: Float,
    aimSeconds: Float,
) {
    val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    val reach = sqrt((core.x - center.x) * (core.x - center.x) + (core.y - center.y) * (core.y - center.y)) + 120f
    val locked = enemy.behaviorRemaining <= lockSeconds
    val charge = (1f - enemy.behaviorRemaining / aimSeconds).coerceIn(0f, 1f)
    val end = polar(center, reach, enemy.facing)
    if (locked) drawLine(threat.copy(alpha = 0.95f), center, end, 2.5f)
    else drawLine(threat.copy(alpha = 0.25f + charge * 0.45f), center, end, 1.2f, pathEffect = WorldStrokes.dashEffect)
    drawCircle(threat.copy(alpha = 0.6f), enemy.radius + 10f * (1f - charge), center, style = kkStroke(1.5f))
}

/** The shield arcs of a Throne or the Antecedent: [EnemyProjection.charges] bone arcs of 80° spaced 120° from its facing. */
internal fun DrawScope.drawShieldArcs(enemy: EnemyProjection, center: Offset) {
    repeat(enemy.charges) { index ->
        val start = (enemy.facing + index * THRONE_ARC_SPACING) * RAD_TO_DEG - THRONE_ARC_DEGREES * 0.5f
        drawBoneArc(center, enemy.radius + THRONE_ARC_GAP, start, THRONE_ARC_DEGREES)
    }
}

/** A bone arc of [radius] around [center] (a front shield, a mount, a shield arc). */
internal fun DrawScope.drawBoneArc(center: Offset, radius: Float, startDegrees: Float, sweepDegrees: Float) {
    drawArc(Kk.Bone, startDegrees, sweepDegrees, false, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f),
        style = kkStroke(3f))
}

/**
 * A secret boss's identity: the anomaly glyph at its centre and a dashed bone ring turning 15° a second around it. Where
 * threats are hatched (Mono) the glyph sits on an ink plate of its own diamond, so its threat strokes never merge into the
 * body's threat hatch.
 */
internal fun DrawScope.drawSecretBossMark(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    threat: androidx.compose.ui.graphics.Color,
    roles: KkRolePalette,
) {
    val glyph = enemy.radius * SECRET_GLYPH
    if (roles.hatchThreats) {
        // The glyph's diamond (24-grid half extent 10) as a square turned 45°.
        val half = glyph * SECRET_GLYPH_PLATE
        rotate(45f, center) { drawRect(Kk.Ink, Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f)) }
    }
    drawKkIcon(KkIcon.SYSTEM_ANOMALY, center, glyph, threat)
    rotate(engine.renderElapsed * SECRET_RING_DEGREES_PER_SECOND, center) {
        drawCircle(Kk.Bone, enemy.radius * SECRET_RING, center, alpha = SECRET_RING_ALPHA, style = WorldStrokes.dashedThin)
    }
}

/** A tethered ally: a dashed bone line back to its Aegis and a small shield pip on the ally. */
private fun DrawScope.drawAegisTether(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset, shakeX: Float, shakeY: Float) {
    val enemies = engine.enemies
    for (index in enemies.indices) {
        val aegis = enemies[index]
        if (aegis.id != enemy.linkedTo || aegis.dead) continue
        val from = world(engine, engine.interpolated(aegis.previousX, aegis.x), engine.interpolated(aegis.previousY, aegis.y), shakeX, shakeY)
        drawLine(Kk.Bone.copy(alpha = 0.45f), from, center, 1.5f, pathEffect = WorldStrokes.dashEffect)
        drawCircle(Kk.Bone.copy(alpha = 0.7f), enemy.radius + 3f, center, style = kkStroke(1.5f))
        return
    }
}

/**
 * A shield that only impacts break: boss ranks (the Herald among them) a pulsing ring at 1.55 radii; ordinary and Elite
 * enemies (a Seraph's mend) a smaller, lighter one with the same pulse.
 */
internal fun DrawScope.drawImpactShield(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset, rank: EnemyRank) {
    val pulse = (sin(engine.renderElapsed * SHIELD_PULSE) + 1f) * 0.5f
    when (rank) {
        EnemyRank.MINI_BOSS, EnemyRank.BOSS, EnemyRank.SECRET_BOSS -> {
            val shield = enemy.radius * (1.55f + 0.04f * pulse)
            drawCircle(Kk.Bone, shield, center, alpha = 0.05f + 0.05f * pulse)
            drawCircle(Kk.Bone, shield, center, alpha = 0.45f + 0.4f * pulse, style = kkStroke(3f))
        }
        EnemyRank.ORDINARY, EnemyRank.ELITE ->
            drawCircle(Kk.Bone, enemy.radius * IMPACT_SHIELD_RING, center, alpha = 0.35f + 0.4f * pulse, style = kkStroke(2f))
    }
}

/** A glitched Cipher: its outline stroked twice, offset sideways, in faint dashed threat. */
internal fun DrawScope.drawCipherGlitch(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    threat: androidx.compose.ui.graphics.Color,
) {
    val path = WorldPaths.silhouette(enemySilhouette(enemy.type), enemy.radius)
    val degrees = enemyRotation(enemy.type, engine.renderElapsed, enemy.id)
    val ghost = threat.copy(alpha = CIPHER_GLITCH_ALPHA)
    repeat(2) { index ->
        withTransform({
            translate(center.x + (index * 2 - 1) * CIPHER_GLITCH_OFFSET, center.y)
            rotate(degrees, Offset.Zero)
        }) {
            drawPath(path, ghost, style = WorldStrokes.dashedThin)
        }
    }
}

/**
 * The Architect: a triangle with a bone inner triangle and threat eye inside two counter-rotating
 * frames, which take the hue of its aspect (threat while it has none). While it shifts between
 * phases a bone shield pulses around it.
 */
internal fun DrawScope.drawArchitect(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    fill: androidx.compose.ui.graphics.Color,
    roles: KkRolePalette,
    dominant: Int,
    statusFill: Float,
) {
    val threat = roles.threat
    val radius = enemy.radius
    val outer = radius * 1.62f
    val inner = radius * 1.22f
    val aspect = EnemyAffinity.aspect(enemy.affinityBits)
    val frame = if (aspect >= 0) roles.element(aspect) else threat
    rotate(-engine.renderElapsed * spin(40f), center) {
        drawCircle(threat.copy(alpha = 0.4f), radius * 2.57f, center, style = WorldStrokes.dashedHair)
    }
    rotate(engine.renderElapsed * spin(18f) + 12f, center) {
        drawRect(frame, Offset(center.x - outer, center.y - outer), Size(outer * 2f, outer * 2f), style = kkStroke(2f))
    }
    rotate(-engine.renderElapsed * spin(12f) - 6f, center) {
        drawRect(frame.copy(alpha = 0.6f), Offset(center.x - inner, center.y - inner), Size(inner * 2f, inner * 2f), style = kkStroke(2f))
    }
    val triangle = WorldPaths.architectTriangle(radius)
    // The triangle's visual center sits below its box center (`M50 4 96 84H4Z`), as on the board.
    translate(center.x, center.y) {
        drawPath(triangle, fill)
        if (dominant >= 0) drawStatusFill(triangle, dominant, statusFill, patterned = true, roles = roles)
        drawKkThreatHatch(triangle, roles)
        drawPath(triangle, threat, style = kkStroke(2.5f))
        drawPath(WorldPaths.architectInnerTriangle(radius), Kk.Bone, style = kkStroke(1.5f))
        drawCircle(threat, radius * 0.174f, Offset(0f, radius * 0.174f))
    }
    if (EnemyAffinity.shifting(enemy.affinityBits)) {
        val pulse = (sin(engine.renderElapsed * SHIELD_PULSE) + 1f) * 0.5f
        val shield = radius * (ARCHITECT_SHIELD + 0.04f * pulse)
        drawCircle(Kk.Bone, shield, center, alpha = 0.05f + 0.05f * pulse)
        drawCircle(Kk.Bone, shield, center, alpha = 0.45f + 0.4f * pulse, style = kkStroke(3f))
    }
}

/** The Architect's shift shield: a bone circle just inside its frames' corners, pulsing twice a second. */
private const val ARCHITECT_SHIELD = 1.9f
private const val SHIELD_PULSE = (2.0 * PI * 2.0).toFloat()
