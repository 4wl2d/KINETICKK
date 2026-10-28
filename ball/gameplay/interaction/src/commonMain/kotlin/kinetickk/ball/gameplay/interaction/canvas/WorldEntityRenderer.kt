// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

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
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val RAD_TO_DEG = 57.29578f

/** Degrees per second for a full turn every [seconds]. */
private fun spin(seconds: Float): Float = 360f / seconds

/**
 * The singularity (cursor): a threat ring with a soft halo, a slowly turning dashed outer ring, a
 * bone dot and four crosshair ticks (`kk.css .sing`). [danger] (contact is close) brightens the halo.
 * In Baby mode ([easy]) the cursor is harmless: the soft role instead of threat, no danger pulse, and
 * the pacifier guard's wings left and right of the ring.
 */
internal fun DrawScope.drawSingularity(center: Offset, time: Float, danger: Boolean, roles: KkRolePalette, easy: Boolean = false) {
    val tone = if (easy) roles.soft else roles.threat
    val pulse = if (danger && !easy) (sin(time * 10f) + 1f) * 0.5f else 0f
    drawCircle(tone.copy(alpha = 0.10f + pulse * 0.10f), 24f, center, style = kkStroke(8f))
    drawCircle(tone.copy(alpha = 0.05f + pulse * 0.06f), 28f, center, style = kkStroke(10f))
    rotate(time * spin(3.2f), center) {
        drawCircle(tone.copy(alpha = 0.7f), 27.25f, center, style = WorldStrokes.dashedThin)
    }
    if (easy) drawPacifierWings(center, tone)
    // `.sing .r1` is a border with no background: whatever the cursor sits on stays visible inside it.
    drawCircle(tone, 13.5f, center, style = kkStroke(3f))
    drawCircle(Kk.Bone, 4f, center)
    drawRect(tone, Offset(center.x - 1f, center.y - 40f), Size(2f, 8f))
    drawRect(tone, Offset(center.x - 1f, center.y + 32f), Size(2f, 8f))
    drawRect(tone, Offset(center.x - 40f, center.y - 1f), Size(8f, 2f))
    drawRect(tone, Offset(center.x + 32f, center.y - 1f), Size(8f, 2f))
}

/**
 * One enemy: telegraph, silhouette, its statuses ([drawEnemyStatus]) and its affinity
 * ([EnemyProjection.affinityBits]). The outline stays threat for every enemy and its weight shows
 * the frame; an aspect tints the body and adds an inner contour in its element (a second one when
 * immune), and the weakness shows as a tab at the lower left ([drawWeaknessTab]). [statusDetail]
 * false (a crowded screen) drops ordinary enemies' status patterns for a flat tint and their
 * weakness tab for a dot; elites keep them.
 */
internal fun DrawScope.drawEnemy(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
    statusDetail: Boolean = true,
) {
    val center = world(engine, engine.interpolated(enemy.previousX, enemy.x), engine.interpolated(enemy.previousY, enemy.y), shakeX, shakeY)
    val renderMargin = enemyRenderMargin(enemy)
    if (center.x < -renderMargin || center.y < -renderMargin || center.x > size.width + renderMargin || center.y > size.height + renderMargin) return
    val threat = roles.threat
    drawEnemyTelegraph(engine, enemy, center, shakeX, shakeY, threat)
    val phasedOut = enemy.type == EnemyType.PHANTOM || enemy.type == EnemyType.CARTOGRAPHER
    if (phasedOut && enemy.behavior == EnemyBehavior.PHASED && EnemyAffinity.shifting(enemy.affinityBits)) {
        // Out of phase (and untargetable): only a faint, turning outline marks where it went (a Cartographer between rocks).
        rotate(engine.renderElapsed * spin(2f), center) {
            drawCircle(threat.copy(alpha = 0.32f), enemy.radius * 1.2f, center, style = WorldStrokes.dashedThin)
        }
        return
    }
    if (enemy.type == EnemyType.CIPHER && enemy.behavior == EnemyBehavior.PHASED && EnemyAffinity.shifting(enemy.affinityBits)) {
        // Glitched out (and untargetable): its outline doubled sideways, faint and dashed, with no body.
        drawCipherGlitch(engine, enemy, center, threat)
        return
    }
    val affinity = enemy.affinityBits
    val aspect = EnemyAffinity.aspect(affinity)
    val aspectHue = if (aspect >= 0) roles.element(aspect) else threat
    val body = if (aspect >= 0) kkMix(Kk.Ink1, aspectHue, ASPECT_BODY_TINT) else Kk.Ink1
    // Hit flash: the fill turns bone for the impact frame, then a short bone afterglow.
    val fill = when {
        enemy.flash > 0.9f -> Kk.Bone
        enemy.flash > 0f -> kkMix(body, Kk.Bone, enemy.flash * 0.35f)
        else -> body
    }
    val bits = enemy.statusBits
    val dominant = if (bits and EnemyStatusBits.STACK_BITS == 0) -1 else EnemyStatusBits.dominant(bits)
    // Stacks over this enemy's own threshold (Embrittle brings Cryo's Break closer).
    val statusFill = if (dominant < 0) 0f else
        (EnemyStatusBits.stacks(bits, dominant).toFloat() / engine.enemyBreakThresholdOf(dominant, bits)).coerceIn(0f, 1f)
    if (enemy.type == EnemyType.ARCHITECT) {
        drawArchitect(engine, enemy, center, fill, roles, dominant, statusFill)
        val ring = enemy.radius * ARCHITECT_RING
        if (bits != 0) drawEnemyStatus(engine, center, ring, enemy.radius, bits, dominant, statusFill, roles, true)
        drawWeaknessTab(center, ring + STATUS_RING_GAP + WEAKNESS_TAB_GAP, EnemyAffinity.weakness(affinity), detailed = true, roles)
        if (enemy.controlBits != 0) drawEnemyControl(engine, enemy, center, ring, roles)
        return
    }
    val silhouette = enemySilhouette(enemy.type)
    val path = WorldPaths.silhouette(silhouette, enemy.radius)
    // A Frozen enemy stops turning under its ice plate and eases back into its turn when it thaws.
    val degrees = if (facesAim(enemy.type)) aimDegrees(enemy.facing) else FrozenPoses.rotation(
        enemy.id, bits and EnemyStatusBits.FROZEN != 0, engine.renderElapsed, enemyRotation(enemy.type, engine.renderElapsed, enemy.id),
    )
    // Elites and the boss ranks (the Herald and the other mini-bosses, the secret bosses) keep their detail and a halo.
    val rank = engine.content.enemy(enemy.type).rank
    val haloed = rank == EnemyRank.ELITE || rank == EnemyRank.MINI_BOSS || rank == EnemyRank.SECRET_BOSS
    val patterned = statusDetail || haloed
    if (haloed) {
        val pulse = (sin(engine.renderElapsed * 3.2f) + 1f) * 0.5f
        drawCircle(threat.copy(alpha = 0.10f + pulse * 0.06f), enemy.radius * 1.45f, center)
        drawCircle(threat.copy(alpha = 0.05f + pulse * 0.04f), enemy.radius * 1.9f, center)
    }
    withTransform({
        translate(center.x, center.y)
        rotate(degrees, Offset.Zero)
    }) {
        drawPath(path, fill)
        if (dominant >= 0) drawStatusFill(path, dominant, statusFill, patterned, roles)
        drawKkThreatHatch(path, roles)
        drawPath(path, threat, style = kkStroke(outlineWidth(affinity)))
        if (aspect >= 0) drawPath(WorldPaths.silhouette(silhouette, enemy.radius * ASPECT_CONTOUR), aspectHue, style = kkStroke(1.5f))
        if (enemy.type == EnemyType.ELITE) {
            // The elite's own inner contour carries its aspect rather than a third one.
            drawPath(WorldPaths.silhouette(silhouette, enemy.radius * 0.52f), aspectHue.copy(alpha = 0.6f), style = kkStroke(1.5f))
        } else if (aspect >= 0 && EnemyAffinity.immune(affinity)) {
            drawPath(WorldPaths.silhouette(silhouette, enemy.radius * IMMUNE_CONTOUR), aspectHue, style = kkStroke(1.5f))
        }
    }
    when (enemy.type) {
        EnemyType.SHOOTER -> drawCircle(threat, max(2.5f, enemy.radius * 0.2f), center)
        EnemyType.WARDEN -> drawCircle(threat, enemy.radius * 0.24f, center, style = kkStroke(2f))
        EnemyType.ELITE -> drawKkIcon(KkIcon.SYSTEM_ELITE, center, enemy.radius * 1.05f, threat)
        EnemyType.HERALD -> {
            drawKkIcon(KkIcon.SYSTEM_ELITE, center, enemy.radius * 0.8f, threat)
            if (enemy.shielded) {
                val pulse = (sin(engine.renderElapsed * SHIELD_PULSE) + 1f) * 0.5f
                val shield = enemy.radius * (1.55f + 0.04f * pulse)
                drawCircle(Kk.Bone, shield, center, alpha = 0.05f + 0.05f * pulse)
                drawCircle(Kk.Bone, shield, center, alpha = 0.45f + 0.4f * pulse, style = kkStroke(3f))
            }
        }
        EnemyType.REFLECTOR -> {
            // The front shield: a bone arc across the 120 degree face it turns toward the Core.
            val arc = enemy.radius + 7f
            val start = enemy.facing * RAD_TO_DEG - REFLECTOR_ARC_DEGREES * 0.5f
            drawArc(Kk.Bone, start, REFLECTOR_ARC_DEGREES, false, Offset(center.x - arc, center.y - arc), Size(arc * 2f, arc * 2f),
                style = kkStroke(3f))
        }
        EnemyType.AEGIS -> drawCircle(Kk.Bone, enemy.radius * 0.18f, center)
        EnemyType.BROODMOTHER -> drawCircle(threat, enemy.radius * 0.3f, center, style = kkStroke(2f))
        EnemyType.PYLON, EnemyType.SENTRY -> if (enemy.behavior == EnemyBehavior.ANCHORED || enemy.behavior == EnemyBehavior.TELEGRAPH) {
            // The mount: a bone arc behind it, opposite its aim.
            val arc = enemy.radius + MOUNT_ARC_GAP
            val start = (enemy.facing + PI.toFloat()) * RAD_TO_DEG - MOUNT_ARC_DEGREES * 0.5f
            drawArc(Kk.Bone, start, MOUNT_ARC_DEGREES, false, Offset(center.x - arc, center.y - arc), Size(arc * 2f, arc * 2f),
                style = kkStroke(3f))
        }
        // Secondary accents of ordinary kinds follow the crowd's level of detail.
        EnemyType.SERAPH -> if (statusDetail) drawCircle(Kk.Bone, enemy.radius * SERAPH_RING, center, style = kkStroke(1.5f))
        EnemyType.CHOIR -> if (statusDetail) drawCircle(Kk.Bone, enemy.radius * CHOIR_RING, center, style = kkStroke(1.5f))
        // Mini-bosses carry the Herald's glyph; the Brood Queen's egg sacs orbit it, the Throne's shield arcs ring it.
        EnemyType.WARDEN_PRIME, EnemyType.PYRE_HERALD -> drawKkIcon(KkIcon.SYSTEM_ELITE, center, enemy.radius * 0.8f, threat)
        EnemyType.BROOD_QUEEN -> {
            drawKkIcon(KkIcon.SYSTEM_ELITE, center, enemy.radius * 0.8f, threat)
            repeat(enemy.charges) { sac ->
                val angle = engine.renderElapsed * QUEEN_SAC_ORBIT_RATE + sac * QUEEN_SAC_SPACING
                drawCircle(Kk.Bone, QUEEN_SAC_RADIUS, polar(center, enemy.radius + QUEEN_SAC_GAP, angle))
            }
        }
        EnemyType.THRONE -> {
            drawKkIcon(KkIcon.SYSTEM_ELITE, center, enemy.radius * 0.8f, threat)
            drawShieldArcs(enemy, center)
        }
        // Secret bosses carry the anomaly glyph inside a slowly turning bone ring; the Antecedent's arcs ring it too.
        EnemyType.CARTOGRAPHER -> drawSecretBossMark(engine, enemy, center, threat)
        EnemyType.ANTECEDENT -> {
            drawSecretBossMark(engine, enemy, center, threat)
            drawShieldArcs(enemy, center)
        }
        else -> Unit
    }
    if (enemy.shielded && enemy.type != EnemyType.HERALD) drawImpactShield(engine, enemy, center)
    drawArmorPlates(enemy, center)
    if (bits != 0) drawEnemyStatus(engine, center, enemy.radius + STATUS_RING_GAP, enemy.radius, bits, dominant, statusFill, roles, patterned)
    drawWeaknessTab(center, enemy.radius + STATUS_RING_GAP + WEAKNESS_TAB_GAP, EnemyAffinity.weakness(affinity), patterned, roles)
    // Control and mark glyphs (reactions, skills, the Apex) sit outside the status ring.
    if (enemy.controlBits != 0) drawEnemyControl(engine, enemy, center, enemy.radius + STATUS_RING_GAP, roles)
}

/** How far off screen an enemy still draws: its telegraphs and tethers reach farther than its body. */
private fun enemyRenderMargin(enemy: EnemyProjection): Float = when (enemy.type) {
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
private const val MINE_TRIGGER_REACH = 60f
private const val MINE_ARMED_LIFE = 7.2f
private const val REFLECTOR_ARC_DEGREES = 120f

/** Presentation copies of the 0.6.0 lanes, sight lines and their timings (Comet, Pylon, Sentry), and the mount arc. */
private const val COMET_LANE_REACH = 1_500f
private const val COMET_MARK_SECONDS = 0.6f
private const val PYLON_SIGHT_REACH = 900f
private const val PYLON_AIM_SECONDS = 0.8f
private const val PYLON_LOCK_SECONDS = 0.2f
private const val SENTRY_LINE_REACH = 760f
private const val SENTRY_AIM_SECONDS = 0.45f
private const val MOUNT_ARC_DEGREES = 120f
private const val MOUNT_ARC_GAP = 5f

/** Presentation copies of the dimension kinds' reaches and timings (Seraph, Choir, Imp, Hound, Cipher), and accents. */
private const val SERAPH_MEND_REACH = 260f
private const val SERAPH_MEND_LEAD = 0.4f
private const val SERAPH_RING = 0.5f
private const val CHOIR_WAVE_REACH = 300f
private const val CHOIR_SING_SECONDS = 0.6f
private const val CHOIR_RING = 0.32f
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
private const val QUEEN_BROOD_LEAD = 0.6f
private const val QUEEN_SAC_RADIUS = 4.5f
private const val QUEEN_SAC_GAP = 10f
private const val QUEEN_SAC_ORBIT_RATE = 0.7f
private const val QUEEN_SAC_SPACING = (2.0 * PI / 3.0).toFloat()
private const val THRONE_ARC_DEGREES = 80f
private const val THRONE_ARC_SPACING = (2.0 * PI / 3.0).toFloat()
private const val THRONE_ARC_GAP = 9f
private const val THRONE_LANE_REACH = 900f
private const val THRONE_JUDGEMENT_SECONDS = 1f
private const val HERALD_VOLLEY_LEAD = 0.3f

/**
 * Presentation copies of the secret bosses' marks and reaches: the anomaly glyph and its turning bone ring (24 s per
 * turn), and the Cartographer's three Survey lanes, their spacing and how long they are marked.
 */
private const val SECRET_GLYPH = 0.75f
private const val SECRET_RING = 1.7f
private const val SECRET_RING_ALPHA = 0.55f
private const val SECRET_RING_DEGREES_PER_SECOND = 15f
private const val CARTO_LANE_REACH = 1_400f
private const val CARTO_LANE_OFFSET = 60f
private const val CARTO_MARK_SECONDS = 1f

/** Distance of the status ring outside an enemy's silhouette (world units). */
private const val STATUS_RING_GAP = 4f

/** The Architect's status ring, as a multiple of its radius (outside its turning frames). */
private const val ARCHITECT_RING = 2.42f

/** Share of the aspect's element in an aspected enemy's ink body. */
private const val ASPECT_BODY_TINT = 0.28f

/** The aspect's inner contour and an immune aspect's second one, as silhouette scales. */
private const val ASPECT_CONTOUR = 0.82f
private const val IMMUNE_CONTOUR = 0.62f

/** Outline weight by frame: armored (impact taken below 1) 3 px, fragile (above 1) 1.5 px, else 2 px. */
private fun outlineWidth(affinity: Int): Float = when {
    EnemyAffinity.armored(affinity) -> 3f
    EnemyAffinity.fragile(affinity) -> 1.5f
    else -> 2f
}

/**
 * The weakness tab [distance] from [center] at +135° (lower left, opposite the rim pip), in screen
 * space so it never turns: the weak element's glyph in its hue on an ink disc, or a small dot in that
 * hue when not [detailed] (a crowded screen). Nothing for [EnemyAffinity.NONE].
 */
private fun DrawScope.drawWeaknessTab(center: Offset, distance: Float, weakness: Int, detailed: Boolean, roles: KkRolePalette) {
    if (weakness < 0) return
    val hue = roles.element(weakness)
    val tab = polar(center, distance, WEAKNESS_TAB_ANGLE)
    if (!detailed) {
        drawCircle(hue, WEAKNESS_DOT_RADIUS, tab)
        return
    }
    drawCircle(Kk.Ink, WEAKNESS_TAB_RADIUS, tab)
    drawKkIcon(elementIcon(weakness), tab, WEAKNESS_GLYPH, hue)
}

/** The weakness tab sits this far outside the status ring, at +135° (radians). */
private const val WEAKNESS_TAB_GAP = 9f
private const val WEAKNESS_TAB_ANGLE = (3.0 * PI / 4.0).toFloat()

/** The tab's ink disc and glyph (the glyph's default stroke stays above a pixel), and the crowd dot. */
private const val WEAKNESS_TAB_RADIUS = 7f
private const val WEAKNESS_GLYPH = 12f
private const val WEAKNESS_DOT_RADIUS = 3f

/** Above this many enemies on screen, ordinary enemies show their status as a flat tint. */
internal const val STATUS_PATTERN_ENEMY_LIMIT = 60

/** Whether more than [STATUS_PATTERN_ENEMY_LIMIT] enemies are on screen (status level of detail). */
internal fun DrawScope.crowdedForStatusPatterns(engine: GameplayRenderModel, shakeX: Float, shakeY: Float): Boolean =
    onScreenEnemyCount(engine, shakeX, shakeY, STATUS_PATTERN_ENEMY_LIMIT + 1) > STATUS_PATTERN_ENEMY_LIMIT

/** Enemies on screen, counted up to [limit] (the world FX crowd level of detail stops counting there). */
internal fun DrawScope.onScreenEnemyCount(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, limit: Int): Int {
    var shown = 0
    for (index in engine.enemies.indices) {
        val enemy = engine.enemies[index]
        val center = world(engine, engine.interpolated(enemy.previousX, enemy.x), engine.interpolated(enemy.previousY, enemy.y), shakeX, shakeY)
        if (!isOnScreen(center, enemy.radius)) continue
        shown++
        if (shown >= limit) return shown
    }
    return shown
}

/**
 * The dominant status inside the silhouette [path] (drawn in the enemy's own transform, so the
 * pattern turns with it): the element's pattern at 25 % plus half of the way to its Break, or a
 * flat 35 % tint when [patterned] is false.
 */
private fun DrawScope.drawStatusFill(path: androidx.compose.ui.graphics.Path, element: Int, fill: Float, patterned: Boolean, roles: KkRolePalette) {
    val color = roles.element(element)
    if (patterned) drawKkPattern(path, elementFill(element), color, 0.25f + 0.5f * fill)
    else drawPath(path, color, alpha = 0.35f)
}

/**
 * Status marks around an enemy at [center] (screen space, never turned): the Frozen ice plate and
 * Brittle cracks over the body, an arc on the ring at [ring] whose sweep is the dominant element's
 * stacks over its Break threshold on this enemy, a rim pip in the element of a second status ready
 * to react, and the dotted ring of Embrittle. The element one stack short of its Break (the primed
 * flag) pulses at 4 Hz: the arc thickens when it is the dominant one, the pip swells when it is
 * the second. No numbers.
 */
private fun DrawScope.drawEnemyStatus(
    engine: GameplayRenderModel,
    center: Offset,
    ring: Float,
    radius: Float,
    bits: Int,
    dominant: Int,
    fill: Float,
    roles: KkRolePalette,
    patterned: Boolean,
) {
    if (bits and EnemyStatusBits.FROZEN != 0) {
        val ice = roles.element(CRYO_ELEMENT)
        val plate = WorldPaths.silhouette(EnemySilhouette.HEXAGON, radius * 1.16f)
        translate(center.x, center.y) {
            if (patterned) drawKkPattern(plate, KkFillKind.LATTICE, ice, 0.6f) else drawPath(plate, ice, alpha = 0.3f)
            drawPath(plate, ice.copy(alpha = 0.18f))
            drawPath(plate, ice, alpha = 0.9f, style = kkStroke(1.5f))
        }
    }
    if (bits and EnemyStatusBits.BRITTLE != 0) {
        val cracks = BrittleCracks
        var index = 0
        while (index < cracks.size) {
            val from = Offset(center.x + cracks[index] * radius, center.y + cracks[index + 1] * radius)
            val to = Offset(center.x + cracks[index + 2] * radius, center.y + cracks[index + 3] * radius)
            drawLine(Kk.Ink, from, to, 3f)
            drawLine(Kk.Bone, from, to, 1.3f)
            index += 4
        }
    }
    if (dominant >= 0) {
        val color = roles.element(dominant)
        val primed = engine.primedElement(bits, dominant)
        val wave = (sin(engine.renderElapsed * PRIMED_PULSE) + 1f) * 0.5f
        val topLeft = Offset(center.x - ring, center.y - ring)
        val box = Size(ring * 2f, ring * 2f)
        drawCircle(color, ring, center, alpha = 0.16f, style = kkStroke(1f))
        val arcPrimed = primed == dominant
        drawArc(color, -90f, 360f * fill.coerceAtLeast(0.04f), false, topLeft, box, alpha = if (arcPrimed) 0.55f + 0.45f * wave else 0.95f,
            style = if (arcPrimed) kkStroke(3.5f) else kkStroke(2f))
        // Anchor III breaks a status on brake release once it is 60 % of the way.
        if (engine.build.tagTier(kinetickk.ball.content.api.BuildTag.ANCHOR) >= 3) {
            val angle = (-90f + 0.6f * 360f) / RAD_TO_DEG
            drawLine(Kk.Bone, polar(center, ring - 3f, angle), polar(center, ring + 3f, angle), 1.5f)
        }
        // The second element: the primed one when the dominant is not, else the one with most stacks (two or more).
        val partner = if (primed >= 0 && primed != dominant) primed else EnemyStatusBits.reactionPartner(bits, dominant)
        if (partner >= 0) {
            val pip = polar(center, ring, PIP_ANGLE)
            val swell = if (partner == primed) 1f + 0.35f * wave else 1f
            drawCircle(Kk.Ink, PIP_RADIUS * swell + PIP_INK, pip)
            drawCircle(roles.element(partner), PIP_RADIUS * swell, pip)
        }
    }
    if (bits and EnemyStatusBits.EMBRITTLED != 0) {
        drawCircle(Kk.Bone2, ring + 5f, center, alpha = 0.8f, style = WorldStrokes.dotted)
    }
}

private const val CRYO_ELEMENT = 1

/** Primed marks pulse four times a second (radians per second of run time). */
private const val PRIMED_PULSE = (2.0 * PI * 4.0).toFloat()

/** The rim pip sits up and to the right of the ring (−45°). */
private const val PIP_ANGLE = (-PI / 4.0).toFloat()

/** The rim pip: a 4 u element dot (radius) in a 1.6 u ink ring that parts it from the body and the ring. */
private const val PIP_RADIUS = 4f
private const val PIP_INK = 1.6f

/**
 * Rotation of enemies while Frozen: the angle each held when the ice took it, by id, so it stops
 * turning; when it thaws it eases back into its turn over [THAW_SECONDS]. Open-addressed by id
 * (no allocation); a pose not refreshed for a moment is forgotten, and an enemy whose slot another
 * frozen enemy takes keeps turning. Draw-thread confined.
 */
internal object FrozenPoses {
    private const val SLOTS = 256
    private const val FORGET_SECONDS = 0.25f
    private const val THAW_SECONDS = 0.35f
    private val ids = IntArray(SLOTS) { Int.MIN_VALUE }

    /** The held angle while frozen, the offset left to ease out while thawing. */
    private val angles = FloatArray(SLOTS)

    /** The last frozen frame's run time, or the thaw's start. */
    private val marks = FloatArray(SLOTS)
    private val thawing = BooleanArray(SLOTS)

    fun rotation(id: Int, frozen: Boolean, elapsed: Float, turning: Float): Float {
        val slot = id and (SLOTS - 1)
        val since = elapsed - marks[slot]
        val known = ids[slot] == id && since >= 0f && since <= (if (thawing[slot]) THAW_SECONDS else FORGET_SECONDS)
        if (frozen) {
            if (known && !thawing[slot]) {
                marks[slot] = elapsed
                return angles[slot]
            }
            val held = if (known) turning + angles[slot] * (1f - KkEase.Out.transform(since / THAW_SECONDS)) else turning
            ids[slot] = id
            angles[slot] = held
            marks[slot] = elapsed
            thawing[slot] = false
            return held
        }
        if (!known) return turning
        if (!thawing[slot]) {
            var offset = (angles[slot] - turning) % 360f
            if (offset > 180f) offset -= 360f
            if (offset < -180f) offset += 360f
            angles[slot] = offset
            marks[slot] = elapsed
            thawing[slot] = true
            return turning + offset
        }
        return turning + angles[slot] * (1f - KkEase.Out.transform(since / THAW_SECONDS))
    }
}

/** Gameplay telegraphs keep their timing; only their look changed (threat lines and rings). */
private fun DrawScope.drawEnemyTelegraph(
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
        EnemyType.CHARGER -> if (enemy.actionTimer < 0f) {
            val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
            val charge = clamp(-enemy.actionTimer / 0.45f, 0f, 1f)
            drawLine(threat.copy(alpha = 0.2f + charge * 0.62f), center, core, 2f + charge * 2f)
            drawCircle(threat.copy(alpha = 0.72f), enemy.radius + 28f * (1f - charge), center, style = WorldStrokes.dashedMedium)
        }
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
        EnemyType.HERALD -> if (enemy.actionTimer in 0f..0.3f) {
            drawCircle(threat.copy(alpha = 0.5f), enemy.radius + 22f + enemy.actionTimer * 40f, center, style = kkStroke(2f))
        }
        EnemyType.BOMBER -> if (enemy.behavior == EnemyBehavior.PRIMED) {
            // The fuse blinks faster as it burns; the ring shows the blast's reach.
            val remaining = enemy.behaviorRemaining
            val blink = if (sin(engine.renderElapsed * (18f + (0.6f - remaining).coerceAtLeast(0f) * 60f)) > 0f) 1f else 0.35f
            drawCircle(threat.copy(alpha = 0.10f * blink), BOMBER_BLAST_REACH, center)
            drawCircle(threat.copy(alpha = 0.75f * blink), BOMBER_BLAST_REACH, center, style = WorldStrokes.dashedMedium)
            drawCircle(Kk.Bone.copy(alpha = 0.8f * blink), enemy.radius + 5f, center, style = kkStroke(2f))
        }
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
            val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
            val reach = sqrt((core.x - center.x) * (core.x - center.x) + (core.y - center.y) * (core.y - center.y)) + 120f
            val locked = enemy.behaviorRemaining <= 0.22f
            val charge = (1f - enemy.behaviorRemaining / 1.1f).coerceIn(0f, 1f)
            val end = polar(center, reach, enemy.facing)
            if (locked) drawLine(threat.copy(alpha = 0.95f), center, end, 2.5f)
            else drawLine(threat.copy(alpha = 0.25f + charge * 0.45f), center, end, 1.2f, pathEffect = WorldStrokes.dashEffect)
            drawCircle(threat.copy(alpha = 0.6f), enemy.radius + 10f * (1f - charge), center, style = kkStroke(1.5f))
        }
        EnemyType.PHANTOM -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) {
            val charge = (1f - enemy.behaviorRemaining / 0.35f).coerceIn(0f, 1f)
            val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
            drawLine(threat.copy(alpha = 0.25f + charge * 0.5f), center, core, 1.5f + charge * 2f)
            drawCircle(threat.copy(alpha = 0.7f), enemy.radius + 24f * (1f - charge), center, style = WorldStrokes.dashedMedium)
        }
        EnemyType.BROODMOTHER -> if (enemy.actionTimer in 0f..0.6f) {
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
                val back = enemy.facing + PI.toFloat()
                drawLine(Kk.Bone.copy(alpha = 0.55f), center, polar(center, enemy.radius * 7f, back), enemy.radius * 0.9f)
                drawLine(threat, center, polar(center, enemy.radius * 4f, back), 1f)
            }
            else -> Unit
        }
        // The Sniper's sight line: dashed while it tracks the Core, solid once locked, with a closing muzzle ring.
        EnemyType.PYLON -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) {
            val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
            val reach = sqrt((core.x - center.x) * (core.x - center.x) + (core.y - center.y) * (core.y - center.y)) + 120f
            val locked = enemy.behaviorRemaining <= PYLON_LOCK_SECONDS
            val charge = (1f - enemy.behaviorRemaining / PYLON_AIM_SECONDS).coerceIn(0f, 1f)
            val end = polar(center, reach, enemy.facing)
            if (locked) drawLine(threat.copy(alpha = 0.95f), center, end, 2.5f)
            else drawLine(threat.copy(alpha = 0.25f + charge * 0.45f), center, end, 1.2f, pathEffect = WorldStrokes.dashEffect)
            drawCircle(threat.copy(alpha = 0.6f), enemy.radius + 10f * (1f - charge), center, style = kkStroke(1.5f))
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
        EnemyType.IMP -> if (enemy.behavior == EnemyBehavior.PRIMED) {
            val remaining = enemy.behaviorRemaining
            val blink = if (sin(engine.renderElapsed * (18f + (IMP_FUSE_SECONDS - remaining).coerceAtLeast(0f) * 60f)) > 0f) 1f else 0.35f
            drawCircle(threat.copy(alpha = 0.10f * blink), IMP_BLAST_REACH, center)
            drawCircle(threat.copy(alpha = 0.75f * blink), IMP_BLAST_REACH, center, style = WorldStrokes.dashedMedium)
            drawCircle(Kk.Bone.copy(alpha = 0.8f * blink), enemy.radius + 5f, center, style = kkStroke(2f))
        }
        // The Charger's wind-up look over the Hound's shorter wind-up.
        EnemyType.HOUND -> if (enemy.actionTimer < 0f) {
            val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
            val charge = clamp(-enemy.actionTimer / HOUND_WINDUP_SECONDS, 0f, 1f)
            drawLine(threat.copy(alpha = 0.2f + charge * 0.62f), center, core, 2f + charge * 2f)
            drawCircle(threat.copy(alpha = 0.72f), enemy.radius + 28f * (1f - charge), center, style = WorldStrokes.dashedMedium)
        }
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
        // The Broodmother's look before a brood.
        EnemyType.BROOD_QUEEN -> if (enemy.actionTimer in 0f..QUEEN_BROOD_LEAD) {
            drawCircle(threat.copy(alpha = 0.45f), enemy.radius + 10f + enemy.actionTimer * 30f, center, style = WorldStrokes.dashedThin)
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
        // The Herald's volley ring.
        EnemyType.PYRE_HERALD, EnemyType.ANTECEDENT -> if (enemy.actionTimer in 0f..HERALD_VOLLEY_LEAD) {
            drawCircle(threat.copy(alpha = 0.5f), enemy.radius + 22f + enemy.actionTimer * 40f, center, style = kkStroke(2f))
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

/** The shield arcs of a Throne or the Antecedent: [EnemyProjection.charges] bone arcs of 80° spaced 120° from its facing. */
private fun DrawScope.drawShieldArcs(enemy: EnemyProjection, center: Offset) {
    val arc = enemy.radius + THRONE_ARC_GAP
    repeat(enemy.charges) { index ->
        val start = (enemy.facing + index * THRONE_ARC_SPACING) * RAD_TO_DEG - THRONE_ARC_DEGREES * 0.5f
        drawArc(Kk.Bone, start, THRONE_ARC_DEGREES, false, Offset(center.x - arc, center.y - arc), Size(arc * 2f, arc * 2f),
            style = kkStroke(3f))
    }
}

/** A secret boss's identity: the anomaly glyph at its centre and a dashed bone ring turning 15° a second around it. */
private fun DrawScope.drawSecretBossMark(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    threat: androidx.compose.ui.graphics.Color,
) {
    drawKkIcon(KkIcon.SYSTEM_ANOMALY, center, enemy.radius * SECRET_GLYPH, threat)
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
 * A shield that only impacts break on anything but the Herald (which draws its own): boss ranks keep the Herald's
 * ring; ordinary and Elite enemies (a Seraph's mend) a smaller, lighter one with the same pulse.
 */
private fun DrawScope.drawImpactShield(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset) {
    val pulse = (sin(engine.renderElapsed * SHIELD_PULSE) + 1f) * 0.5f
    when (engine.content.enemy(enemy.type).rank) {
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
private fun DrawScope.drawCipherGlitch(
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
private fun DrawScope.drawArchitect(
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

internal fun DrawScope.drawProjectiles(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    for (index in engine.projectiles.indices) {
        val projectile = engine.projectiles[index]
        // Drawn between its last two simulated positions, with a one-step streak behind it.
        val x = engine.interpolated(projectile.previousX, projectile.x)
        val y = engine.interpolated(projectile.previousY, projectile.y)
        val center = world(engine, x, y, shakeX, shakeY)
        if (!isOnScreen(center, projectile.radius + 28f)) continue
        val previous = world(engine, x - (projectile.x - projectile.previousX), y - (projectile.y - projectile.previousY), shakeX, shakeY)
        if (projectile.hostile && projectile.kind == ProjectileKind.MINE) {
            drawMine(center, projectile.radius, projectile.life, engine.renderElapsed, roles)
        } else if (projectile.hostile) {
            val threat = roles.threat
            drawLine(threat.copy(alpha = 0.22f), previous, center, projectile.radius * 1.1f)
            drawCircle(threat.copy(alpha = 0.1f), projectile.radius + 6f, center)
            drawCircle(threat.copy(alpha = 0.22f), projectile.radius + 2.5f, center)
            drawCircle(threat, projectile.radius, center)
            if (roles.hatchThreats) {
                translate(center.x, center.y) {
                    drawKkThreatHatch(WorldPaths.silhouette(EnemySilhouette.OCTAGON, projectile.radius), roles, Kk.Ink)
                }
            }
        } else {
            // Apex shots (colour index 15) fly gold; every other shot of the player in its colour.
            val accent = if (projectile.colorIndex == APEX_GOLD_COLOR) roles.gold.base else roles.you
            drawLine(accent.copy(alpha = 0.3f), previous, center, max(1.5f, projectile.radius * 1.2f))
            drawCircle(accent.copy(alpha = 0.18f), projectile.radius + 4f, center)
            drawCircle(if (projectile.colorIndex == APEX_GOLD_COLOR) roles.gold.light else Kk.Bone, max(2f, projectile.radius * 0.8f), center)
        }
    }
}

/** A mine: a threat diamond with a blinking core; once armed a faint ring shows its trigger reach. */
private fun DrawScope.drawMine(center: Offset, radius: Float, life: Float, elapsed: Float, roles: KkRolePalette) {
    val threat = roles.threat
    val armed = life <= MINE_ARMED_LIFE
    rotate(45f, center) {
        drawRect(Kk.Ink1, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f))
        drawRect(threat, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f), style = kkStroke(2f))
    }
    val blink = if (armed) (if (cos(elapsed * 9f) > 0f) 1f else 0.3f) else 0.25f
    drawCircle(threat.copy(alpha = blink), radius * 0.4f, center)
    if (armed) drawCircle(threat.copy(alpha = 0.22f), MINE_TRIGGER_REACH, center, style = WorldStrokes.dashedHair)
}

internal fun DrawScope.drawPickups(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    for (index in engine.pickups.indices) {
        val pickup = engine.pickups[index]
        val x = engine.interpolated(pickup.previousX, pickup.x)
        val y = engine.interpolated(pickup.previousY, pickup.y)
        val center = world(engine, x, y, shakeX, shakeY)
        if (!isOnScreen(center, 28f)) continue
        val previous = world(engine, x - (pickup.x - pickup.previousX), y - (pickup.y - pickup.previousY), shakeX, shakeY)
        when (pickup.type) {
            PickupType.DATA -> {
                drawLine(Kk.Bone.copy(alpha = 0.22f), previous, center, 2f)
                // Bone diamond; sizes vary a little per pickup like the boards.
                val half = 3.2f + (index % 3) * 0.6f
                rotate(45f, center) { drawRect(Kk.Bone, Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f)) }
            }
            PickupType.KEY -> {
                drawLine(roles.you.copy(alpha = 0.25f), previous, center, 2f)
                drawPickupPlate(center, roles.you)
                drawKkIcon(KkIcon.SYSTEM_KEY, center, 15f, roles.you)
            }
            PickupType.REPAIR -> {
                drawLine(Kk.Bone.copy(alpha = 0.22f), previous, center, 2f)
                drawPickupPlate(center, Kk.Bone)
                drawKkIcon(KkIcon.SYSTEM_INTEGRITY, center, 14f, Kk.Bone)
                drawRect(Kk.Bone, Offset(center.x - 3f, center.y - 0.75f), Size(6f, 1.5f))
                drawRect(Kk.Bone, Offset(center.x - 0.75f, center.y - 3f), Size(1.5f, 6f))
            }
            PickupType.RELIC -> {
                drawLine(Kk.RLegend.copy(alpha = 0.3f), previous, center, 2.5f)
                drawUnresolvedRelicIcon(center, 11f, engine.renderElapsed)
            }
        }
    }
}

/** A small ink-2 sheared plate with an outline, the chip look for world pickups. */
private fun DrawScope.drawPickupPlate(center: Offset, outline: androidx.compose.ui.graphics.Color) {
    val plate = WorldPaths.shearedPlate(28f)
    translate(center.x, center.y) {
        drawPath(plate, Kk.Ink2)
        drawPath(plate, outline.copy(alpha = 0.7f), style = kkStroke(1f))
    }
}

/**
 * Particles: the first shards of a burst fly as tumbling sheared slivers (never pointed); the rest
 * are short sparks along their velocity.
 */
internal fun DrawScope.drawParticles(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    for (index in visualFx.particles.indices) {
        val particle = visualFx.particles[index]
        val center = world(engine, particle.x, particle.y, shakeX, shakeY)
        if (!isOnScreen(center, 24f)) continue
        val alpha = clamp(particle.life / particle.maxLife, 0f, 1f)
        val color = fxColor(particle.colorIndex, roles)
        val speed = sqrt(particle.vx * particle.vx + particle.vy * particle.vy)
        if (particle.size >= InteractionFxLimits.SHARD_MIN_SIZE) {
            val heading = if (speed > 0.001f) kotlin.math.atan2(particle.vy, particle.vx) else 0f
            val length = particle.size * 2.6f
            withTransform({
                translate(center.x, center.y)
                rotate(shardTumble(heading, index, alpha), Offset.Zero)
                scale(length, length, Offset.Zero)
            }) {
                drawPath(WorldUnitShapes.shard, color, alpha = min(1f, alpha * 1.4f))
            }
        } else if (speed > 1f) {
            val stretch = min(0.05f, 14f / speed)
            val tailX = center.x - particle.vx * stretch
            val tailY = center.y - particle.vy * stretch
            drawLine(color.copy(alpha = alpha * 0.8f), Offset(tailX, tailY), center, max(1f, particle.size * 0.7f))
        } else {
            drawCircle(color.copy(alpha = alpha), max(0.8f, particle.size * 0.6f), center)
        }
    }
}

/** Shards tumble: a per-shard phase plus a turn over their life, never aligned to their travel. */
internal fun shardTumble(heading: Float, index: Int, life: Float): Float =
    heading * RAD_TO_DEG + 55f + (index % 4) * 17f + (1f - life) * 220f

/**
 * The weapon totem: stacked sheared ink plates with you-color edge lines around a you-color key
 * block (`Totem.dc.html`) inside a slowly turning dashed ring. Off-screen, [collectTotemEdgeMarker]
 * marks it at the screen edge with the distance.
 */
internal fun DrawScope.drawTotem(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    val totem = engine.totem ?: return
    val roles = textMeasurer.roles
    val location = world(engine, totem.x, totem.y, shakeX, shakeY)
    if (!isOnScreen(steady(location, shakeX, shakeY), 60f)) return // decided unshaken, as its edge marker
    val pulse = (sin(totem.pulse) + 1f) * 0.5f
    rotate(engine.renderElapsed * spin(24f), location) {
        drawCircle(roles.you.copy(alpha = 0.45f), 45f, location, style = WorldStrokes.dashedThin)
    }
    drawCircle(roles.you.copy(alpha = 0.05f + pulse * 0.04f), 45f, location)
    drawTotemPlates(location, 1f, roles.you, keyBlock = true, keyIcon = true)
}

/**
 * Adds the off-screen totem to [batch]; [drawEdgeMarkers] draws it with the frame's other markers.
 * Whether it is off screen, and where its marker points, is decided in the unshaken view, as
 * [drawTotem] decides whether to draw it (both in world units; [batch] receives device pixels).
 */
internal fun DrawScope.collectTotemEdgeMarker(engine: GameplayRenderModel, batch: EdgeMarkerBatch) {
    val totem = engine.totem ?: return
    val location = world(engine, totem.x, totem.y, 0f, 0f)
    if (isOnScreen(location, 60f)) return
    val dx = totem.x - engine.coreX
    val dy = totem.y - engine.coreY
    val scale = engine.worldScale
    batch.add(location.x * scale, location.y * scale, sqrt(dx * dx + dy * dy), EdgeMarkerIcon.TOTEM)
}

/**
 * Five stacked plates (60/80/110/80/60 on the board) around a key block, scaled to the world:
 * [scale] 1 is 30 units wide. [keyBlock] fills the middle plate in [accent] ([keyIcon] adds the
 * key); otherwise the middle plate is outlined.
 */
internal fun DrawScope.drawTotemPlates(
    center: Offset,
    scale: Float,
    accent: androidx.compose.ui.graphics.Color,
    keyBlock: Boolean,
    keyIcon: Boolean,
) {
    val width = 30f * scale
    val gap = 2.2f * scale
    val heights = TotemPlateHeights
    var total = 0f
    for (height in heights) total += height * scale
    total += gap * (heights.size - 1)
    var top = center.y - total * 0.5f
    for (index in heights.indices) {
        val height = heights[index] * scale
        val middle = index == heights.size / 2
        val plateTop = top
        withKkShear(plateTop + height * 0.5f) {
            val face = if (middle && keyBlock) accent else Kk.Ink3
            drawRect(face, Offset(center.x - width * 0.5f, plateTop), Size(width, height))
            if (!middle) {
                val edgeY = if (index < heights.size / 2) plateTop + height - 1f * scale else plateTop
                drawRect(accent, Offset(center.x - width * 0.5f, edgeY), Size(width, max(1f, scale)))
            } else if (!keyBlock) {
                drawRect(accent, Offset(center.x - width * 0.5f, plateTop), Size(width, height), style = kkStroke(1.5f))
            }
        }
        if (middle && keyBlock && keyIcon) drawKkIcon(KkIcon.SYSTEM_KEY, Offset(center.x, plateTop + height * 0.5f), 16f * scale, Kk.Ink)
        top += height + gap
    }
}

private val TotemPlateHeights = floatArrayOf(10f, 13f, 18f, 13f, 10f)

/** The stacked-plates mark used by edge markers (the HUD-Elite totem marker). */
internal fun DrawScope.drawTotemIcon(center: Offset, size: Float, color: androidx.compose.ui.graphics.Color) {
    val unit = size / 24f
    drawRect(color, Offset(center.x - 3f * unit, center.y - 9f * unit), Size(6f * unit, 4f * unit), style = kkStroke(max(1f, 1.6f * unit)))
    drawRect(color, Offset(center.x - 4f * unit, center.y - 3f * unit), Size(8f * unit, 5f * unit), style = kkStroke(max(1f, 1.6f * unit)))
    drawRect(color, Offset(center.x - 3f * unit, center.y + 4f * unit), Size(6f * unit, 5f * unit), style = kkStroke(max(1f, 1.6f * unit)))
}

/**
 * A Break's own figure in its element at [progress] of its life, at [strength] of its brightness
 * (the reaction effects setting's peak alpha). Merged Breaks ([weight]) burn brighter.
 */
internal fun DrawScope.drawBreakBurst(
    element: Int,
    center: Offset,
    reach: Float,
    progress: Float,
    color: androidx.compose.ui.graphics.Color,
    weight: Int,
    strength: Float = 1f,
) {
    val out = KkEase.Out.transform(progress)
    val fade = (1f - progress) * strength
    val bright = (0.7f + 0.1f * weight).coerceAtMost(1f) * strength
    if (progress < 0.22f) drawCircle(color, reach * (0.28f + out * 0.3f), center, alpha = (1f - progress / 0.22f) * 0.4f * strength)
    when (element) {
        // Ignite: the stacks explode outward; flame ticks ride the ring.
        0 -> {
            val radius = reach * out
            drawCircle(color, radius, center, alpha = fade * bright, style = kkStroke(3f))
            for (tick in 0 until 10) {
                val angle = tick * TAU / 10f + progress * 0.6f
                drawLine(color.copy(alpha = fade * bright), polar(center, radius * 0.72f, angle), polar(center, radius * 1.08f, angle), 2f)
            }
        }
        // Freeze: a hexagon locks around the target and six shards fly off its corners.
        1 -> {
            val radius = reach * (0.55f + 0.45f * out)
            for (side in 0 until 6) {
                val a = side * TAU / 6f + 0.52f
                drawLine(color.copy(alpha = fade * bright), polar(center, radius, a), polar(center, radius, a + TAU / 6f), 2.5f)
                drawLine(Kk.Bone.copy(alpha = fade * 0.8f), polar(center, radius * 1.05f, a), polar(center, radius * (1.15f + out * 0.4f), a), 1.5f)
            }
        }
        // Overload: lightning forks out from the target.
        2 -> {
            drawCircle(color, reach * out * 0.6f, center, alpha = fade * 0.6f, style = WorldStrokes.dashedThin)
            for (bolt in 0 until 6) {
                val a = bolt * TAU / 6f + 0.3f
                val length = reach * (0.35f + 0.65f * out)
                var previous = polar(center, length * 0.2f, a)
                for (segment in 1..3) {
                    val t = segment / 3f
                    val wobble = if (segment == 3) 0f else (if (segment % 2 == 0) 0.22f else -0.22f)
                    val next = polar(center, length * (0.2f + 0.8f * t), a + wobble)
                    drawLine(color.copy(alpha = fade * bright), previous, next, 2f)
                    previous = next
                }
            }
        }
        // Collapse: everything is pulled in; two rings close on the target to a third of the reach,
        // streaks spiral in behind them, and a dense core swells where they meet.
        3 -> {
            val closing = reach * (1f - 0.68f * out)
            drawCircle(color, closing, center, alpha = fade * bright, style = kkStroke(3.5f))
            drawCircle(color, closing * 0.62f, center, alpha = fade * 0.8f, style = kkStroke(2f))
            for (streak in 0 until 8) {
                val a = streak * TAU / 8f + 0.2f - progress * 0.9f
                drawLine(color.copy(alpha = fade * bright), polar(center, closing * 1.06f, a), polar(center, closing * (1.34f - 0.14f * out), a + 0.14f),
                    2f)
            }
            drawCircle(color, reach * (0.12f + 0.12f * out), center, alpha = fade * 0.22f)
            drawCircle(color, reach * (0.04f + 0.07f * out), center, alpha = fade * 0.95f)
        }
        // Shatter: three sound rings race outward one after another, split by the gaps of a
        // shockwave, and short throw strokes fly off the outermost.
        5 -> {
            for (ring in 0 until 3) {
                val lag = ring * 0.16f
                val t = ((progress - lag) / (1f - lag)).coerceIn(0f, 1f)
                if (t <= 0f) continue
                val radius = reach * KkEase.Out.transform(t)
                val alpha = (1f - t) * bright
                for (arc in 0 until 4) {
                    drawArc(color, arc * 90f + 12f + ring * 30f, 66f, false, Offset(center.x - radius, center.y - radius),
                        Size(radius * 2f, radius * 2f), alpha = alpha, style = kkStroke(3f - ring * 0.6f))
                }
            }
            val outer = reach * out
            for (stroke in 0 until 8) {
                val a = stroke * TAU / 8f + 0.2f
                drawLine(color.copy(alpha = fade * bright), polar(center, outer * 0.86f, a), polar(center, outer * 1.12f, a), 2f)
            }
        }
        // Unmake: a dashed hollow ring closes on the target while its core goes dark, then the
        // remembered damage punches back out as a thin bright ring.
        6 -> {
            val closing = reach * (1f - 0.7f * out)
            drawCircle(color, closing, center, alpha = fade * bright, style = WorldStrokes.dashedThin)
            drawCircle(Kk.Ink, reach * 0.22f * out, center, alpha = fade * 0.7f)
            drawCircle(color, reach * 0.22f * out, center, alpha = fade * bright, style = kkStroke(2f))
            if (progress > 0.45f) {
                val t = (progress - 0.45f) / 0.55f
                drawCircle(color, reach * (0.3f + 0.7f * KkEase.Out.transform(t)), center, alpha = (1f - t) * bright, style = kkStroke(1.5f))
            }
        }
        // Rupture: a broken ring tears open and pits scatter.
        else -> {
            val radius = reach * out
            for (arc in 0 until 5) {
                val start = arc * 72f + progress * 50f
                drawArc(color, start, 44f, false, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f),
                    alpha = fade * bright, style = kkStroke(3f))
            }
            for (pit in 0 until 7) {
                val a = pit * TAU / 7f + 0.4f
                drawCircle(color, 2f, polar(center, radius * (0.4f + 0.11f * pit), a), alpha = fade)
            }
        }
    }
}

private const val TAU = (2.0 * PI).toFloat()

/** Reaction name stamps: cond 30 at the world label scale, big enough to read mid-fight. */
private val REACTION_STAMP_SP = worldLabelSp(30f)

/**
 * The first time a profile meets a reaction, its name stamps at the enemy: condensed, tilted −6°,
 * slammed in and fading out over 900 ms, printed in the first element's hue over an offset in the
 * second's and an ink shadow. At most ten times in a profile's life.
 */
internal fun DrawScope.drawReactionStamps(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    textMeasurer: TextMeasurer,
) {
    val stamps = visualFx.reactionStamps
    if (stamps.isEmpty()) return
    val roles = textMeasurer.roles
    val reactions = engine.content.build.reactions
    for (index in stamps.indices) {
        val stamp = stamps[index]
        val definition = reactions.getOrNull(stamp.reaction) ?: continue
        val center = worldOnScreen(engine, stamp.x, stamp.y, shakeX, shakeY)
        if (!isOnScreen(center, 160f)) continue
        val elapsed = stamp.maxLife - stamp.life
        val slam = (elapsed * 1000f / KkSlam.DURATION_MS).coerceIn(0f, 1f)
        val fade = if (stamp.life < 0.25f) (stamp.life / 0.25f).coerceIn(0f, 1f) else 1f
        val alpha = KkSlam.alpha(slam) * fade
        if (alpha <= 0f) continue
        val first = roles.element(definition.id.first.ordinal)
        val second = roles.element(definition.id.second.ordinal)
        val name = WorldStrings.reactionName(definition.name, stamp.reaction, textMeasurer.language)
        val style = textMeasurer.typography.condStyle(REACTION_STAMP_SP, lineHeightEm = 1f, color = first)
        val face = measureKkText(textMeasurer, name, style, uppercase = true)
        val echo = measureKkText(textMeasurer, name, textMeasurer.typography.condStyle(REACTION_STAMP_SP, lineHeightEm = 1.001f, color = second),
            uppercase = true)
        val shadow = measureKkText(textMeasurer, name, textMeasurer.typography.condStyle(REACTION_STAMP_SP, lineHeightEm = 1.002f, color = Kk.Ink),
            uppercase = true)
        val half = face.size.width * 0.5f + 8f
        val x = keepInside(center.x, half, half, size.width, d(8f))
        val y = keepInside(center.y - 46f, face.kkBoxHeight, face.kkBoxHeight, size.height, d(8f))
        withTransform({
            translate(x, y)
            rotate(KkSlam.rotation(slam, -6f), Offset.Zero)
            val s = KkSlam.scale(slam)
            scale(s, s, Offset.Zero)
        }) {
            drawKkText(shadow, 4f, 4f, Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER, alpha)
            drawKkText(echo, 3f, 2.5f, second, KkAlign.CENTER, KkVAlign.CENTER, alpha)
            drawKkText(face, 0f, 0f, first, KkAlign.CENTER, KkVAlign.CENTER, alpha)
        }
    }
}
