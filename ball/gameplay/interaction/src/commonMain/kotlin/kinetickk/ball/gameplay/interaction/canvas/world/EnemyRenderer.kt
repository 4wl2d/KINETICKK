// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*

internal const val RAD_TO_DEG = CanvasMath.RAD_TO_DEG

/** Degrees per second for a full turn every [seconds]. */
internal fun spin(seconds: Float): Float = 360f / seconds

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
    drawCircle(tone, 13.5f, center, style = kkStroke(KkStrokeWidth.Heavy))
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
    drawEliteVariety(engine, enemy, center, patterned)
    withTransform({
        translate(center.x, center.y)
        rotate(degrees, Offset.Zero)
    }) {
        drawPath(path, fill)
        if (dominant >= 0) drawStatusFill(path, dominant, statusFill, patterned, roles)
        drawKkThreatHatch(path, roles)
        drawPath(path, threat, style = kkStroke(outlineWidth(affinity) + championOutlineBonus(affinity)))
        if (aspect >= 0) drawPath(WorldPaths.silhouette(silhouette, enemy.radius * ASPECT_CONTOUR), aspectHue, style = kkStroke(KkStrokeWidth.Line2))
        if (enemy.type == EnemyType.ELITE) {
            // The elite's own inner contour carries its aspect rather than a third one.
            drawPath(WorldPaths.silhouette(silhouette, enemy.radius * 0.52f), aspectHue.copy(alpha = 0.6f), style = kkStroke(KkStrokeWidth.Line2))
        } else if (aspect >= 0 && EnemyAffinity.immune(affinity)) {
            drawPath(WorldPaths.silhouette(silhouette, enemy.radius * IMMUNE_CONTOUR), aspectHue, style = kkStroke(KkStrokeWidth.Line2))
        }
    }
    // Mini-bosses carry the Herald's glyph; the Brood Queen's egg sacs orbit it, the Throne's shield arcs ring it.
    if (rank == EnemyRank.MINI_BOSS) drawKkIcon(KkIcon.SYSTEM_ELITE, center, enemy.radius * 0.8f, threat)
    when (enemy.type) {
        EnemyType.SHOOTER -> drawCircle(threat, max(2.5f, enemy.radius * 0.2f), center)
        EnemyType.WARDEN -> drawCircle(threat, enemy.radius * 0.24f, center, style = kkStroke(KkStrokeWidth.Icon))
        EnemyType.ELITE -> drawKkIcon(KkIcon.SYSTEM_ELITE, center, enemy.radius * 1.05f, threat)
        // The front shield: a bone arc across the 120 degree face it turns toward the Core.
        EnemyType.REFLECTOR ->
            drawBoneArc(center, enemy.radius + 7f, enemy.facing * RAD_TO_DEG - REFLECTOR_ARC_DEGREES * 0.5f, REFLECTOR_ARC_DEGREES)
        EnemyType.AEGIS -> drawCircle(Kk.Bone, enemy.radius * 0.18f, center)
        EnemyType.BROODMOTHER -> drawCircle(threat, enemy.radius * 0.3f, center, style = kkStroke(KkStrokeWidth.Icon))
        EnemyType.PYLON, EnemyType.SENTRY -> if (enemy.behavior == EnemyBehavior.ANCHORED || enemy.behavior == EnemyBehavior.TELEGRAPH) {
            // The mount: a bone arc behind it, opposite its aim.
            val start = (enemy.facing + PI.toFloat()) * RAD_TO_DEG - MOUNT_ARC_DEGREES * 0.5f
            drawBoneArc(center, enemy.radius + MOUNT_ARC_GAP, start, MOUNT_ARC_DEGREES)
        }
        // Secondary accents of ordinary kinds follow the crowd's level of detail.
        EnemyType.SERAPH -> if (statusDetail) drawCircle(Kk.Bone, enemy.radius * SERAPH_RING, center, style = kkStroke(KkStrokeWidth.Line2))
        EnemyType.CHOIR -> if (statusDetail) drawCircle(Kk.Bone, enemy.radius * CHOIR_RING, center, style = kkStroke(KkStrokeWidth.Line2))
        EnemyType.BROOD_QUEEN -> repeat(enemy.charges) { sac ->
            val angle = engine.renderElapsed * QUEEN_SAC_ORBIT_RATE + sac * QUEEN_SAC_SPACING
            drawCircle(Kk.Bone, QUEEN_SAC_RADIUS, polar(center, enemy.radius + QUEEN_SAC_GAP, angle))
        }
        EnemyType.THRONE -> drawShieldArcs(enemy, center)
        // Secret bosses carry the anomaly glyph inside a slowly turning bone ring; the Antecedent's arcs ring it too.
        EnemyType.CARTOGRAPHER -> drawSecretBossMark(engine, enemy, center, threat, roles)
        EnemyType.ANTECEDENT -> {
            drawSecretBossMark(engine, enemy, center, threat, roles)
            drawShieldArcs(enemy, center)
        }
        else -> Unit
    }
    if (enemy.shielded) drawImpactShield(engine, enemy, center, rank)
    drawArmorPlates(enemy, center)
    if (bits != 0) drawEnemyStatus(engine, center, enemy.radius + STATUS_RING_GAP, enemy.radius, bits, dominant, statusFill, roles, patterned)
    drawWeaknessTab(center, enemy.radius + STATUS_RING_GAP + WEAKNESS_TAB_GAP, EnemyAffinity.weakness(affinity), patterned, roles)
    // Control and mark glyphs (reactions, skills, the Apex) sit outside the status ring.
    if (enemy.controlBits != 0) drawEnemyControl(engine, enemy, center, enemy.radius + STATUS_RING_GAP, roles)
}

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

/** How far past an enemy's radius its weakness tab's disc reaches. */
internal const val WEAKNESS_TAB_REACH = STATUS_RING_GAP + WEAKNESS_TAB_GAP + WEAKNESS_TAB_RADIUS
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
internal fun DrawScope.drawStatusFill(path: androidx.compose.ui.graphics.Path, element: Int, fill: Float, patterned: Boolean, roles: KkRolePalette) {
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
            drawPath(plate, ice, alpha = 0.9f, style = kkStroke(KkStrokeWidth.Line2))
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
        drawCircle(color, ring, center, alpha = 0.16f, style = kkStroke(KkStrokeWidth.Line))
        val arcPrimed = primed == dominant
        drawArc(color, -90f, 360f * fill.coerceAtLeast(0.04f), false, topLeft, box, alpha = if (arcPrimed) 0.55f + 0.45f * wave else 0.95f,
            style = if (arcPrimed) kkStroke(3.5f) else kkStroke(KkStrokeWidth.Icon))
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
