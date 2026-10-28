// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reference pilot opts into nothing (decisions, gates policy): a Slingshot swings the Core's momentum onto the aim,
 * so, like its Blink and Arc Hook, the bot never swings along a line that runs into a skipped optional site. Without
 * this a fresh run's swing threw the Core at a window rift 270 u ahead at 945 u/s, faster than the bot's site berth
 * could turn it, and the No omen base of the omen gates entered a Nadir visit.
 */
class NoPoiSlingshotGuardTest {
    @Test fun theBotSwingsItsMomentumOntoItsAimInOpenSpace() {
        val state = cruisingBackFromItsAim()
        NoPoiBot().castSkills(state)
        assertTrue(state.skillBufferMask != 0, "the bot did not cast a ready Slingshot against its momentum")
        state.update(INPUT_FRAME)
        assertTrue(state.velocityX >= SWING_FLOOR, "the swing did not throw the Core along its aim: ${state.velocityX}")
    }

    @Test fun theBotHoldsASwingThatWouldThrowTheCoreIntoASkippedRift() {
        val state = cruisingBackFromItsAim()
        state.sites = listOf(SiteState(0L, SiteKind.NADIR_RIFT, state.coreX + SITE_DISTANCE, state.coreY, state.elapsed + 20f))
        NoPoiBot().castSkills(state)
        assertEquals(0, state.skillBufferMask, "the bot swung its Core at a skipped rift")
        state.update(INPUT_FRAME)
        assertTrue(state.velocityX < 0f, "the Core turned toward the rift: ${state.velocityX}")
        assertFalse(state.optionalSitesTouched(), "the Core opted into the rift")
    }

    @Test fun aSkippedRiftBehindTheSwingDoesNotHoldIt() {
        val state = cruisingBackFromItsAim()
        state.sites = listOf(SiteState(0L, SiteKind.NADIR_RIFT, state.coreX - SITE_DISTANCE, state.coreY, state.elapsed + 20f))
        NoPoiBot().castSkills(state)
        assertTrue(state.skillBufferMask != 0, "a rift off the swing's line held the cast")
    }

    /**
     * A quiet arena whose Core holds a ready Slingshot and cruises along -x at [CRUISE] u/s with its singularity
     * 200 dp along +x: the swing the bot casts against its momentum would throw it along +x.
     */
    private fun cruisingBackFromItsAim(): MutableGameState = MutableGameState(canonicalGameplayContent, seed = SEED).apply {
        unlockWeaponsForTesting(ReferenceArmory)
        applyPreferences(settings.copy(simulationSpeed = 1f))
        startRun()
        val opener = NoPoiBot()
        while (phase == GamePhase.CHOICE) opener.choose(this)
        enemies.clear()
        projectiles.clear()
        pickups.clear()
        spawnClock = Float.MAX_VALUE
        nextEliteAt = Float.MAX_VALUE
        quietArenaForTesting()
        clearObstaclesForTesting()
        acquireActive(SkillId.SLINGSHOT)
        check(phase == GamePhase.RUNNING)
        setVelocityForTesting(-CRUISE, 0f)
        updatePointer(screenWidth * 0.5f + coreX - cameraX + 200f, screenHeight * 0.5f + coreY - cameraY)
    }

    private companion object {
        const val SEED = 2026
        const val INPUT_FRAME = 0.02f
        const val CRUISE = 700f
        /** The rift's centre from the Core: well inside the swing's half-second throw at the slowest swing (700 u/s). */
        const val SITE_DISTANCE = 270f
        /** A swing throws the Core at its minimum swing speed or more; one frame of steering takes little of it. */
        const val SWING_FLOOR = 600f
    }
}
