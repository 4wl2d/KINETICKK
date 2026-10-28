// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Baby mode (RunMode.EASY) rules of one run, read from the immutable [MutableGameState.easyMode] and the content's
 * EasyModeTuning. Shared systems call these through one-line hooks: the singularity contact in updateCore, the damage
 * factor and obstacle floor in PlayerDamageSystem, the Heirloom offer at the run start and the Profile outbox guards
 * in ProgressionSystem. A STANDARD run reads none of the tuning and multiplies by exactly 1.
 */

/** Shockwave and spark colour of Baby mode's soft bounce (Interaction maps it to the soft role, SOFT_RING_COLOR). */
internal const val EASY_SOFT_COLOR_INDEX: Int = 23

/** Feed message of a Baby mode bounce; localized through the content phrase table. */
internal const val EASY_BOOP_MESSAGE: String = "BOOP"

/** Seconds the BOOP message shows when no other message holds the feed. */
private const val EASY_BOOP_MESSAGE_SECONDS: Float = 0.9f

/** Incoming hostile and obstacle damage factor of this run: the Baby mode multiplier, 1 otherwise. */
internal val MutableGameState.incomingDamageFactor: Float
    get() = if (easyMode) content.easyMode.incomingDamageMultiplier else 1f

/** Obstacle damage after shields in Baby mode: never below the integrity floor. */
internal fun MutableGameState.capEasyObstacleDamage(amount: Float): Float =
    if (!easyMode) amount else min(amount, max(0f, hp - content.easyMode.obstacleIntegrityFloor))

/**
 * Baby mode: the singularity at ([targetX], [targetY]) bounces the Core off instead of ending the run. The Core is put
 * `boopClearance` from the cursor along the line from the cursor to the Core (back along its velocity, or against the
 * last aim, at dead centre); the inward speed returns outward at `boopRestitution`, at least `boopMinimumOutwardSpeed`
 * leaves outward, and the bounce never leaves more speed than the Core had (or that minimum; the sideways part is cut
 * first), so it cannot be used as a launcher. The previous Core position stays, so the drawn Core glides to the bounce point; the next swept contact test
 * starts outside the kill circle. A soft shockwave, sparks, a click and, when the feed is free, the BOOP message show it.
 */
internal fun MutableGameState.boopOffSingularity(targetX: Float, targetY: Float) {
    val tuning = content.easyMode
    val speedBefore = speed
    var nx = coreX - targetX
    var ny = coreY - targetY
    val distance = length(nx, ny)
    if (distance < 0.001f) {
        if (speedBefore > 1f) {
            nx = -velocityX / speedBefore
            ny = -velocityY / speedBefore
        } else {
            nx = -lastAimDirectionX
            ny = -lastAimDirectionY
        }
    } else {
        nx /= distance
        ny /= distance
    }
    coreX = targetX + nx * tuning.boopClearance
    coreY = targetY + ny * tuning.boopClearance
    val inward = velocityX * nx + velocityY * ny
    if (inward < 0f) {
        velocityX -= (1f + tuning.boopRestitution) * inward * nx
        velocityY -= (1f + tuning.boopRestitution) * inward * ny
    }
    val outward = velocityX * nx + velocityY * ny
    if (outward < tuning.boopMinimumOutwardSpeed) {
        velocityX += (tuning.boopMinimumOutwardSpeed - outward) * nx
        velocityY += (tuning.boopMinimumOutwardSpeed - outward) * ny
    }
    // Never more speed than before (or the minimum): the sideways part gives way first, so the outward minimum holds.
    // The outward part is at most that limit (restitution < 1 of the old speed, or the minimum itself).
    val limit = max(speedBefore, tuning.boopMinimumOutwardSpeed)
    if (speed > limit) {
        val outwardNow = velocityX * nx + velocityY * ny
        val sidewaysX = velocityX - outwardNow * nx
        val sidewaysY = velocityY - outwardNow * ny
        val sideways = length(sidewaysX, sidewaysY)
        val keep = if (sideways > 0f) sqrt(max(0f, limit * limit - outwardNow * outwardNow)) / sideways else 0f
        velocityX = outwardNow * nx + sidewaysX * keep
        velocityY = outwardNow * ny + sidewaysY * keep
    }
    shockwave(coreX, coreY, 0.35f, 70f, EASY_SOFT_COLOR_INDEX)
    burst(coreX, coreY, 6, EASY_SOFT_COLOR_INDEX)
    emitSound(GameplayAudioCue.UI_CLICK)
    if (messageTime <= 0f) {
        message = EASY_BOOP_MESSAGE
        messageTime = EASY_BOOP_MESSAGE_SECONDS
    }
}
