// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.SkillDefinition
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.interaction.input.RunningControlPressLog
import kinetickk.ball.gameplay.interaction.layout.KEY_CAP_MIN_WIDTH_DP
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.SKILL_KEY_CAP_GAP_DP
import kinetickk.ball.gameplay.interaction.layout.SKILL_KEY_CAP_HEIGHT_DP
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.layout.skillSlot
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection
import kinetickk.ball.profile.api.GameAction
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkSlam
import kinetickk.foundation.design.KkTime
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.d
import kinetickk.foundation.design.drawKkRingBurst
import kinetickk.foundation.design.drawKkSkillSlot
import kinetickk.foundation.design.kkKeyCapStyle
import kinetickk.foundation.design.label
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Z/X/C skill row at the SKILL targets of [forEachRunningControlBounds] (live mask): each slot's
 * look ([drawKkSkillSlot]) with its feedback — the 90 ms press flash ([RunningControlPressLog]), the
 * refusal shake and Bone sweep edge, the ready flash and ring, the refund edge, the draft slam and
 * ring, the rank-up pip fill and ring, and the whole row sliding in with the first draft — and, while
 * the keyboard is in use on REGULAR windows at least [KEY_CAP_MIN_WIDTH_DP] wide, the bound key under
 * each drafted slot. Slots before their draft show locked. Allocation-free on steady frames.
 */
internal fun DrawScope.drawSkillBar(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    frame: HudFrame,
    renderTime: Float,
    memory: HudPresentationMemory?,
    press: RunningControlPressLog?,
) {
    val mask = RunningControlMask.of(engine)
    if (mask and RunningControlMask.SKILLS == 0) return
    // The first draft slides the row in from the right while it fades in (Pull).
    val row = memory?.skillRow(renderTime) ?: 1f
    val entrance = KkEase.Pull.transform(row)
    if (row < 1f) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(HudDrawCache.rect(HudRect.SKILL_ROW, 0f, 0f, size.width, size.height),
                HudScratch.skillRowPaint.apply { alpha = entrance })
        }
    }
    translate(frame.u(SKILL_ROW_ENTRANCE_DP) * (1f - entrance), 0f) {
        var unionLeft = Float.POSITIVE_INFINITY
        var unionTop = Float.POSITIVE_INFINITY
        var unionRight = Float.NEGATIVE_INFINITY
        var unionBottom = Float.NEGATIVE_INFINITY
        var keysDrawn = false
        var keysLeft = Float.POSITIVE_INFINITY
        var keysRight = Float.NEGATIVE_INFINITY
        var keysBottom = Float.NEGATIVE_INFINITY
        var keysTop = Float.POSITIVE_INFINITY
        forEachRunningControlBounds(size.width, size.height, density, mask) { target, left, top, right, bottom ->
            val slot = target.skillSlot
            if (slot < 0) return@forEachRunningControlBounds
            val bounds = HudDrawCache.rect(SkillRects[slot], left, top, right, bottom)
            val keyWidth = drawSkillSlotAt(engine, measurer, frame, target, slot, bounds, renderTime, memory, press)
            unionLeft = min(unionLeft, left)
            unionTop = min(unionTop, top)
            unionRight = max(unionRight, right)
            unionBottom = max(unionBottom, bottom)
            if (keyWidth > 0f) {
                keysDrawn = true
                val capTop = bottom + frame.u(SKILL_KEY_CAP_GAP_DP)
                keysLeft = min(keysLeft, bounds.center.x - keyWidth * 0.5f)
                keysRight = max(keysRight, bounds.center.x + keyWidth * 0.5f)
                keysTop = min(keysTop, capTop)
                keysBottom = max(keysBottom, capTop + frame.u(SKILL_KEY_CAP_HEIGHT_DP))
            }
        }
        if (unionLeft.isFinite()) HudLayoutProbe.record(HudBlock.SKILLS, unionLeft, unionTop, unionRight, unionBottom)
        if (keysDrawn) HudLayoutProbe.record(HudBlock.SKILL_KEYS, keysLeft, keysTop, keysRight, keysBottom)
    }
    if (row < 1f) drawIntoCanvas { it.restore() }
}

/** Draws one slot with its one-shots; returns the width of its key cap (0 without one). */
private fun DrawScope.drawSkillSlotAt(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    frame: HudFrame,
    target: RunningControlTarget,
    slot: Int,
    bounds: androidx.compose.ui.geometry.Rect,
    renderTime: Float,
    memory: HudPresentationMemory?,
    press: RunningControlPressLog?,
): Float {
    val roles = measurer.roles
    val projection = engine.skillBar.slots[slot]
    val id = projection.skillId
    val center = bounds.center
    val side = bounds.width
    if (id == null) {
        drawKkSkillSlot(measurer, bounds, icon = null, iconColor = Kk.Mute2, cooldown = 0f, ready = false, rank = 0, locked = true)
        return 0f
    }
    val definition = engine.content.skill(id)
    val color = skillColor(definition, roles)
    // Draft: the slot slams in (220 ms) under a `you` ring (420 ms).
    val acquired = memory?.skillAcquired(slot, renderTime) ?: 1f
    val acquiredMs = acquired * HudPresentationMemory.SKILL_ACQUIRE_MS
    val slam = if (acquired < 1f) KkSlam.scale((acquiredMs / KkSlam.DURATION_MS).coerceIn(0f, 1f)) else 1f
    // Rank-up: the new pip fills over 90 ms under a ring (gold at rank III), 600 ms.
    val ranked = memory?.skillRanked(slot, renderTime) ?: 1f
    val rankFill = ((ranked * HudPresentationMemory.SKILL_RANK_MS) / KkTime.Snap).coerceIn(0f, 1f)
    // Ready: a Bone face flash (90 ms) under a `you` ring (420 ms).
    val readied = memory?.skillReady(slot, renderTime) ?: 1f
    val readyFlash = if (readied < 1f && readied * HudPresentationMemory.SKILL_READY_MS < KkTime.Snap) 1f else 0f
    // Refund: the sweep's leading edge flashes in the skill's colour (220 ms).
    val refund = memory?.skillRefund(slot, renderTime) ?: 1f
    var edge = if (refund < 1f) 1f - refund else 0f
    var edgeColor = color
    // Refusal (cooling or no target): a 240 ms shake and a Bone edge (220 ms).
    var shake = 0f
    val refusedFor = refusalAge(engine, projection)
    if (refusedFor >= 0f) {
        val p = refusedFor / REFUSAL_SHAKE_SECONDS
        if (p < 1f) shake = d(REFUSAL_SHAKE_DP) * sin(p * 6f * PI.toFloat()) * (1f - p)
        val bone = 1f - refusedFor / REFUSAL_EDGE_SECONDS
        if (bone > edge) {
            edge = bone
            edgeColor = Kk.Bone
        }
    }
    val pressed = press?.pressFlash(target, renderTime) ?: 0f
    val keyLayout = skillKeyLayout(engine, measurer, frame, slot, side, press)
    withTransform({ scale(slam, slam, center) }) {
        drawKkSkillSlot(
            measurer, bounds, skillIcon(id, engine), color,
            cooldown = projection.cooldown01, ready = projection.ready, rank = projection.rank,
            charges = projection.charges, maxCharges = projection.maxCharges,
            active = projection.active01, activeColor = color, pending = projection.pending01,
            pressed = pressed, readyFlash = readyFlash, refundFlash = edge, refundColor = edgeColor,
            shakeX = shake, keyLayout = keyLayout,
            iconSizeDp = if (frame.regular) REGULAR_SKILL_ICON_DP * frame.textFactor else PHONE_SKILL_ICON_DP,
            rankFill = rankFill, unit = if (frame.regular) frame.unit else 0f,
        )
    }
    if (memory != null) {
        drawKkRingBurst(center, frame.u(SKILL_RING_DP), acquired, roles.you)
        val rankColor = if (projection.rank >= RANK_THREE) roles.gold.base else roles.you
        drawKkRingBurst(center, frame.u(SKILL_RING_DP), ranked, rankColor)
        drawKkRingBurst(center, side * READY_RING_SCALE, readied, roles.you, 2f)
    }
    return keyLayout?.let { it.size.width + frame.u(KEY_CAP_PADDING_DP) * 2f } ?: 0f
}

/**
 * Seconds since [projection]'s refusal while it is recent (≤ [REFUSAL_WINDOW_SECONDS] of the run
 * clock), else −1.
 */
internal fun refusalAge(engine: GameplayRenderModel, projection: SkillSlotProjection): Float {
    if (projection.refusal == SkillSlotProjection.REFUSAL_NONE || projection.refusedAt < 0f) return -1f
    val age = engine.renderElapsed - projection.refusedAt
    return if (age in 0f..REFUSAL_WINDOW_SECONDS) age else -1f
}

/** The key cap under a drafted REGULAR slot while the keyboard is in use, or null. */
private fun DrawScope.skillKeyLayout(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    frame: HudFrame,
    slot: Int,
    side: Float,
    press: RunningControlPressLog?,
): androidx.compose.ui.text.TextLayoutResult? {
    if (press == null || !press.keyHints || !keyCapsShown(frame)) return null
    val action = when (slot) {
        0 -> GameAction.SKILL_1
        1 -> GameAction.SKILL_2
        else -> GameAction.SKILL_3
    }
    val label = engine.settings.keyBindings.key(action).label(measurer.language)
    val room = side - frame.u(KEY_CAP_PADDING_DP) * 2f
    val display = HudMeasurers.display(measurer)
    return HudDrawCache.fitted(SkillKeyTexts[slot], display, label, frame.t(KEY_CAP_TEXT_SP), room, uppercase = true,
        minSize = KEY_CAP_MIN_SP) { size -> kkKeyCapStyle(display, size) }
}

/** Key caps show on REGULAR windows at least [KEY_CAP_MIN_WIDTH_DP] dp wide. */
internal fun keyCapsShown(frame: HudFrame): Boolean = frame.regular && frame.width >= KEY_CAP_MIN_WIDTH_DP * frame.density

/** The skill's colour: its element; kinetic skills `kinetic`; Catalyze and Dual Infusion Bone. */
internal fun skillColor(definition: SkillDefinition, roles: KkRolePalette): Color {
    val element = definition.element
    return when {
        element != null -> roles.element(element.ordinal)
        definition.id == SkillId.CATALYZE || definition.id == SkillId.DUAL_INFUSION -> Kk.Bone
        else -> roles.kinetic
    }
}

/**
 * The skill's icon (an existing weapon glyph); when it equals the equipped weapon's, the skill shows
 * its element's aspect instead (kinetic for kinetic skills, chain for Catalyze, arsenal for Dual
 * Infusion), so the slot never repeats the weapon slot.
 */
internal fun skillIcon(id: SkillId, engine: GameplayRenderModel): KkIcon {
    val icon = skillGlyph(id)
    if (icon != weaponIcon(engine.weapon)) return icon
    val element = engine.content.skill(id).element
    return when {
        element != null -> elementIcon(element.ordinal)
        id == SkillId.CATALYZE -> KkIcon.TAG_CHAIN
        id == SkillId.DUAL_INFUSION -> KkIcon.TAG_ARSENAL
        else -> KkIcon.ASPECT_KINETIC
    }
}

/** Each skill's glyph (all 18 distinct). */
internal fun skillGlyph(id: SkillId): KkIcon = when (id) {
    SkillId.SOLAR_FLARE -> KkIcon.WEAPONS_SOLAR_SWEEP
    SkillId.METEOR -> KkIcon.WEAPONS_MORNINGSTAR
    SkillId.FROST_LINE -> KkIcon.WEAPONS_PHASE_LATTICE
    SkillId.FROST_SNAP -> KkIcon.WEAPONS_GLACIER_BELL
    SkillId.ARC_HOOK -> KkIcon.WEAPONS_TETHER_ARC
    SkillId.STORM_COIL -> KkIcon.WEAPONS_ARC_COIL
    SkillId.GRAVITY_WELL -> KkIcon.WEAPONS_GRAVITY_MINES
    SkillId.MASS_DRIVER -> KkIcon.WEAPONS_SINGULARITY_SPEAR
    SkillId.DECAY_SPORES -> KkIcon.WEAPONS_SCATTER_BLOOM
    SkillId.ENTROPIC_TIDE -> KkIcon.WEAPONS_ENTROPY_FIELD
    SkillId.SONIC_BOOM -> KkIcon.WEAPONS_RESONANCE_DRUM
    SkillId.RESONATOR -> KkIcon.WEAPONS_SENTRY_GRID
    SkillId.VOID_STEP -> KkIcon.WEAPONS_HOLLOW_RIFT
    SkillId.NULL_SHROUD -> KkIcon.WEAPONS_EVENT_LENS
    SkillId.SLINGSHOT -> KkIcon.WEAPONS_GYRE_DISC
    SkillId.FULL_STOP -> KkIcon.WEAPONS_CONCUSSION_ENGINE
    SkillId.CATALYZE -> KkIcon.WEAPONS_PRISM_RELAY
    SkillId.DUAL_INFUSION -> KkIcon.WEAPONS_FLUX_WAKE
}

/**
 * The skill's reach around the Core after a no-target refusal (Bone dashed ring, fading over
 * [REFUSAL_WINDOW_SECONDS]), in world units (call inside the world-unit layer at the drawn Core).
 */
internal fun DrawScope.drawSkillReachRings(engine: GameplayRenderModel, core: Offset) {
    val slots = engine.skillBar.slots
    for (index in 0 until slots.size) {
        val projection = slots[index]
        if (projection.refusal != SkillSlotProjection.REFUSAL_NO_TARGET) continue
        val id = projection.skillId ?: continue
        val age = refusalAge(engine, projection)
        if (age < 0f) continue
        val reach = engine.content.skill(id).reach
        if (reach <= 0f) continue
        drawCircle(Kk.Bone, reach, core, alpha = 0.5f * (1f - age / REFUSAL_WINDOW_SECONDS), style = WorldStrokes.dashedThin)
    }
}

/**
 * Apex strikes left while the Apex runs and a boss bar shows: sheared pips right-aligned on the
 * bar's right end, their top [APEX_STRIKE_RISE_DP] above it (gold left, Ink4 spent).
 */
internal fun DrawScope.drawApexStrikes(engine: GameplayRenderModel, frame: HudFrame, roles: KkRolePalette, barRight: Float, barTop: Float) {
    val apex = engine.apex
    if (!apex.unlocked || apex.active01 <= 0f) return
    val total = engine.content.coreShape(engine.coreShape).apex.bossStrikes
    if (total <= 0) return
    val width = if (frame.regular) frame.u(8f) else d(6f)
    val height = if (frame.regular) frame.u(5f) else d(4f)
    val gap = frame.u(3f)
    val top = barTop - frame.u(APEX_STRIKE_RISE_DP)
    var right = barRight
    for (index in total - 1 downTo 0) {
        val path = HudDrawCache.paths.sheared(StrikePaths[index], right - width, top, right, top + height)
        drawPath(path, if (index < apex.strikesLeft) roles.gold.base else Kk.Ink4)
        right -= width + gap
    }
}

private val SkillRects = arrayOf(HudRect.SKILL_0, HudRect.SKILL_1, HudRect.SKILL_2)
private val SkillKeyTexts = arrayOf(HudText.SKILL_KEY_0, HudText.SKILL_KEY_1, HudText.SKILL_KEY_2)
private val StrikePaths = IntArray(kinetickk.ball.content.api.ContentBounds.MAX_APEX_STRIKES) { HudPath.STRIKE_0.ordinal + it }

/** The first draft slides the row in from this far right (reference dp). */
private const val SKILL_ROW_ENTRANCE_DP = 24f

/** Draft and rank-up ring bursts (reference dp); the ready ring is this share of the square. */
private const val SKILL_RING_DP = 40f
private const val READY_RING_SCALE = 0.8f

/** Skill icon sizes: REGULAR 26 × text factor, phones 22 dp. */
private const val REGULAR_SKILL_ICON_DP = 26f
private const val PHONE_SKILL_ICON_DP = 22f

private const val RANK_THREE = 3

/** Refusal feedback: the shake (3 dp, 240 ms), the Bone edge (220 ms), the reach ring window (420 ms). */
private const val REFUSAL_SHAKE_DP = 3f
private const val REFUSAL_SHAKE_SECONDS = 0.24f
private const val REFUSAL_EDGE_SECONDS = 0.22f
internal const val REFUSAL_WINDOW_SECONDS = 0.42f

/** Key caps: mono 700 at 10 sp (never below 8 sp) with 3 u padding; labels shrink to fit their plate. */
internal const val KEY_CAP_TEXT_SP = 10f
internal const val KEY_CAP_MIN_SP = 8f
private const val KEY_CAP_PADDING_DP = 3f

/** The Apex strike pips rise this far above the boss bar (reference dp). */
private const val APEX_STRIKE_RISE_DP = 6f
