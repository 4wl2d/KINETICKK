// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.skills

import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState.Companion.MAX_SKILL_SCRATCH

/**
 * Skill and Apex input buffers and the scratch of the cast under way. Written by the skills domain (the Apex request
 * buffers here too); the cast scratch is never read across casts, so a deep copy starts it fresh.
 */
internal class SkillCastState(source: SkillCastState?) {
    // Skill and Apex input: buffered slot requests and their age, the buffered Apex request.
    internal var skillBufferMask: Int = source?.skillBufferMask ?: 0
    internal var skillBufferTime: Float = source?.skillBufferTime ?: 0f
    internal var apexBufferTime: Float = source?.apexBufferTime ?: 0f
    /** Candidate-private scratch of one skill cast (picked targets); never read across casts. */
    internal val skillScratch: IntArray = IntArray(MAX_SKILL_SCRATCH)
    /** The slot casting right now, -1 between casts; the cast's damaged-enemy count and the bosses it struck. */
    internal var castSlot: Int = -1
    internal var castHits: Int = 0
    /** Enemies the running element-less cast attuned (SkillAttunement). */
    internal var castAttunedStacks: Int = 0
    internal var castPrimaryId: Int = -1
    internal var castBossHitFirst: Int = -1
    internal var castBossHitSecond: Int = -1
}
